import json
import sqlite3
from pathlib import Path
from typing import Any, Iterable, Mapping, Optional

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
    reason: Optional[str] = None,
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
        error = validate_entity_config(config)
        if error:
            return AirbyteConnectionStatus(status=Status.FAILED, message=error)
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
                canonical = record.get("canonical_instrument")
                if canonical is not None and canonical != config["symbol_map"][symbol]:
                    yield acknowledgment(
                        fill_id,
                        destination_id,
                        "rejected",
                        {
                            "accepted": False,
                            "persisted": False,
                            "error": f"Canonical instrument mismatch for {symbol}: {canonical}",
                            "symbol": symbol,
                            "canonical_instrument": canonical,
                        },
                        "canonical_mismatch",
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


def validate_entity_config(config: Mapping[str, Any]) -> Optional[str]:
    if not isinstance(config.get("entity_id"), str) or not config["entity_id"].strip():
        return "entity_id must be a non-empty string"
    symbol_map = config.get("symbol_map")
    if (
        not isinstance(symbol_map, dict)
        or not symbol_map
        or any(not isinstance(key, str) or not key.strip() or not isinstance(value, str) or not value.strip() for key, value in symbol_map.items())
    ):
        return "symbol_map must contain non-empty native-to-canonical symbols"
    for field in ("supported_asset_classes", "tradable_symbols"):
        value = config.get(field)
        if not isinstance(value, list) or not value or any(not isinstance(item, str) or not item.strip() for item in value):
            return f"{field} must be a non-empty string list"
    if not set(config["tradable_symbols"]).issubset(symbol_map):
        return "Every tradable symbol must have a canonical symbol_map entry"
    return None
