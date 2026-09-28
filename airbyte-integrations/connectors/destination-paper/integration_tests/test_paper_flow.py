import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from airbyte_cdk.models import AirbyteMessage, AirbyteRecordMessage, SyncMode, Type
from destination_paper import DestinationPaper
from source_paper import SourcePaper


DESTINATION_ID = "ea75c03d-0d16-4857-93c7-7ad838647ed7"
POLICY = {
    "DAX": {"destination": "GER40", "factor": 1.0, "scale": 1.0},
    "DJ": {"destination": "WS30", "factor": 1.0, "scale": 1.0},
    "ES": {"exclusion": "future_not_supported"},
}


def test_native_paper_connectors_policy_journal_and_replay(tmp_path):
    source_config = {
        "entity_id": "paper-source",
        "symbol_map": {"DAX": "DE40", "DJ": "US30", "ES": "US500"},
        "supported_asset_classes": ["index"],
        "tradable_symbols": ["DAX", "DJ", "ES"],
    }
    destination_config = {
        "entity_id": DESTINATION_ID,
        "symbol_map": {"GER40": "DE40", "WS30": "US30"},
        "supported_asset_classes": ["index"],
        "tradable_symbols": ["GER40", "WS30"],
        "ledger_path": str(tmp_path / "paper.sqlite"),
    }
    source = SourcePaper()
    destination = DestinationPaper()
    fixtures = list(source.streams(source_config)[0].read_records(SyncMode.full_refresh))
    outbound = []
    journal = []

    for fixture in fixtures:
        row = POLICY[fixture["symbol"]]
        origin = datetime.fromisoformat(fixture["origen_en"])
        if "exclusion" in row:
            replica = datetime.now(timezone.utc)
            journal.append(
                {
                    "source_fill_id": fixture["source_fill_id"],
                    "status": "skipped",
                    "reason": row["exclusion"],
                    "origin": origin,
                    "replica": replica,
                    "desfase_ms": int((replica - origin).total_seconds() * 1000),
                }
            )
            continue
        data = {
            **fixture,
            "source_symbol": fixture["symbol"],
            "symbol": row["destination"],
            "quantity": fixture["quantity"] * row["factor"] * row["scale"],
            "_puente_destination_id": DESTINATION_ID,
        }
        outbound.append(
            AirbyteMessage(
                type=Type.RECORD,
                record=AirbyteRecordMessage(stream="fills", emitted_at=0, data=data),
            )
        )

    acknowledgments = list(destination.write(destination_config, None, outbound))
    for message in acknowledgments:
        acknowledgment = message.state.data["_puente_ack"]
        replica = datetime.now(timezone.utc)
        fixture = next(item for item in fixtures if item["source_fill_id"] == acknowledgment["source_fill_id"])
        origin = datetime.fromisoformat(fixture["origen_en"])
        journal.append(
            {
                "source_fill_id": acknowledgment["source_fill_id"],
                "status": acknowledgment["status"],
                "response": acknowledgment["response"],
                "origin": origin,
                "replica": replica,
                "desfase_ms": int((replica - origin).total_seconds() * 1000),
            }
        )

    assert [message.record.data["symbol"] for message in outbound] == ["GER40", "WS30"]
    assert all(message.record.data["source_symbol"] != "ES" for message in outbound)
    assert [entry["status"] for entry in journal] == ["skipped", "accepted", "accepted"]
    assert all(
        entry["desfase_ms"] == int((entry["replica"] - entry["origin"]).total_seconds() * 1000)
        for entry in journal
    )
    assert all(entry.get("response", {}).get("persisted", True) for entry in journal)

    accepted_ids = {entry["source_fill_id"] for entry in journal if entry["status"] == "accepted"}
    replay_outbound = [
        fixture for fixture in fixtures if fixture["source_fill_id"] not in accepted_ids and "exclusion" not in POLICY[fixture["symbol"]]
    ]
    assert replay_outbound == []
    with sqlite3.connect(destination_config["ledger_path"]) as database:
        assert database.execute("SELECT source_fill_id, destination_id FROM replicas ORDER BY source_fill_id").fetchall() == [
            ("paper-dax-1", DESTINATION_ID),
            ("paper-dj-1", DESTINATION_ID),
        ]


def test_symbol_maps_are_editable_native_connector_properties():
    connectors = Path(__file__).parents[2]
    specs = [
        connectors / "source-paper" / "source_paper" / "spec.json",
        connectors / "destination-paper" / "destination_paper" / "spec.json",
    ]
    for path in specs:
        specification = json.loads(path.read_text())["connectionSpecification"]
        symbol_map = specification["properties"]["symbol_map"]
        assert symbol_map["type"] == "object"
        assert symbol_map["additionalProperties"] == {"type": "string"}
