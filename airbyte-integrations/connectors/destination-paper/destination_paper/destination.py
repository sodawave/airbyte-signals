import json
import sqlite3
from pathlib import Path
from typing import Any, Iterable, Mapping

from airbyte_cdk import AirbyteLogger
from airbyte_cdk.destinations import Destination
from airbyte_cdk.models import AirbyteConnectionStatus, AirbyteMessage, AirbyteStateMessage, ConfiguredAirbyteCatalog, Status, Type


ACK_FIELD = "_puente_ack"
DESTINATION_ID_FIELD = "_puente_destination_id"


def acknowledgment(
    fill_id: str,
    destination_id: str,
    status: str,
    response: Mapping[str, Any],
    reason: str | None = None,
) -> AirbyteMessage:
    payload = {
        "source_fill_id": fill_id,
        "destination_id": destination_id,
        "status": status,
        "response": dict(response),
    }
    if reason:
        payload["reason"] = reason
    return AirbyteMessage(type=Type.STATE, state=AirbyteStateMessage(data={ACK_FIELD: payload}))


class DestinationPaper(Destination):
    def check(self, logger: AirbyteLogger, config: Mapping[str, Any]) -> AirbyteConnectionStatus:
        if not config.get("entity_id"):
            return AirbyteConnectionStatus(status=Status.FAILED, message="entity_id is required")
        if not isinstance(config.get("symbol_map"), dict) or not config["symbol_map"]:
            return AirbyteConnectionStatus(status=Status.FAILED, message="symbol_map is required")
        return AirbyteConnectionStatus(status=Status.SUCCEEDED)

    def write(
        self,
        config: Mapping[str, Any],
        configured_catalog: ConfiguredAirbyteCatalog,
        input_messages: Iterable[AirbyteMessage],
    ) -> Iterable[AirbyteMessage]:
        ledger_path = Path(config.get("ledger_path", "/data/puente-paper.sqlite"))
        ledger_path.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(ledger_path) as database:
            database.execute(
                """
                CREATE TABLE IF NOT EXISTS replicas (
                  idempotency_key TEXT PRIMARY KEY,
                  source_fill_id TEXT NOT NULL,
                  destination_id TEXT NOT NULL,
                  request_json TEXT NOT NULL
                )
                """
            )
            for message in input_messages:
                if message.type == Type.STATE:
                    yield message
                    continue
                if message.type != Type.RECORD:
                    continue
                record = message.record.data
                symbol = record.get("symbol")
                fill_id = record.get("source_fill_id")
                destination_id = record.get(DESTINATION_ID_FIELD, config["entity_id"])
                if not fill_id:
                    yield acknowledgment(
                        "",
                        destination_id,
                        "rejected",
                        {"accepted": False, "persisted": False, "error": "source_fill_id is required"},
                        "missing_fill_id",
                    )
                    continue
                if symbol not in config["tradable_symbols"]:
                    yield acknowledgment(
                        fill_id,
                        destination_id,
                        "rejected",
                        {
                            "accepted": False,
                            "persisted": False,
                            "error": f"Unsupported paper destination symbol: {symbol}",
                            "symbol": symbol,
                        },
                        "unsupported_symbol",
                    )
                    continue
                key = f"{fill_id}:{destination_id}"
                cursor = database.execute(
                    """
                    INSERT INTO replicas(idempotency_key, source_fill_id, destination_id, request_json)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(idempotency_key) DO NOTHING
                    """,
                    (key, fill_id, destination_id, json.dumps(record, sort_keys=True)),
                )
                database.commit()
                inserted = cursor.rowcount == 1
                yield acknowledgment(
                    fill_id,
                    destination_id,
                    "accepted",
                    {
                        "accepted": True,
                        "persisted": True,
                        "inserted": inserted,
                        "idempotency_key": key,
                        "symbol": symbol,
                    },
                )
