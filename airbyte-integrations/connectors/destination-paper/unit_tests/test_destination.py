import sqlite3

from airbyte_cdk.models import AirbyteMessage, AirbyteRecordMessage, Status, Type

from destination_paper import DestinationPaper


def record(fill_id: str, symbol: str) -> AirbyteMessage:
    return AirbyteMessage(
        type=Type.RECORD,
        record=AirbyteRecordMessage(
            stream="fills",
            emitted_at=0,
            data={"source_fill_id": fill_id, "symbol": symbol, "quantity": 1},
        ),
    )


def test_write_is_idempotent_by_source_fill_and_destination(tmp_path):
    ledger = tmp_path / "paper.sqlite"
    config = {
        "entity_id": "destination-one",
        "symbol_map": {"GER40": "DE40", "WS30": "US30"},
        "supported_asset_classes": ["index"],
        "tradable_symbols": ["GER40", "WS30"],
        "ledger_path": str(ledger),
    }
    destination = DestinationPaper()
    acknowledgments = list(destination.write(config, None, [record("fill-1", "GER40"), record("fill-1", "GER40")]))

    with sqlite3.connect(ledger) as database:
        assert database.execute("SELECT COUNT(*) FROM replicas").fetchone()[0] == 1
    assert [message.state.data["_puente_ack"]["status"] for message in acknowledgments] == ["accepted", "accepted"]
    assert [message.state.data["_puente_ack"]["response"]["inserted"] for message in acknowledgments] == [True, False]


def test_write_acknowledges_unsupported_symbol_without_persisting(tmp_path):
    ledger = tmp_path / "paper.sqlite"
    config = {
        "entity_id": "destination-one",
        "symbol_map": {"GER40": "DE40"},
        "supported_asset_classes": ["index"],
        "tradable_symbols": ["GER40"],
        "ledger_path": str(ledger),
    }
    acknowledgments = list(DestinationPaper().write(config, None, [record("fill-es", "ES")]))

    acknowledgment = acknowledgments[0].state.data["_puente_ack"]
    assert acknowledgment["status"] == "rejected"
    assert acknowledgment["reason"] == "unsupported_symbol"
    assert acknowledgment["response"]["persisted"] is False
    with sqlite3.connect(ledger) as database:
        assert database.execute("SELECT COUNT(*) FROM replicas").fetchone()[0] == 0


def test_check_rejects_incomplete_or_inconsistent_entity_configuration():
    destination = DestinationPaper()
    valid = {
        "entity_id": "destination-one",
        "symbol_map": {"GER40": "DE40"},
        "supported_asset_classes": ["index"],
        "tradable_symbols": ["GER40"],
    }
    assert destination.check(None, valid).status == Status.SUCCEEDED
    for patch in (
        {"entity_id": ""},
        {"symbol_map": {}},
        {"supported_asset_classes": []},
        {"tradable_symbols": ["UNKNOWN"]},
        {"symbol_map": {"GER40": ""}},
    ):
        assert destination.check(None, {**valid, **patch}).status == Status.FAILED


def test_write_rejects_canonical_mismatch(tmp_path):
    config = {
        "entity_id": "destination-one",
        "symbol_map": {"GER40": "DE40"},
        "supported_asset_classes": ["index"],
        "tradable_symbols": ["GER40"],
        "ledger_path": str(tmp_path / "paper.sqlite"),
    }
    message = record("fill-mismatch", "GER40")
    message.record.data["canonical_instrument"] = "US30"

    acknowledgment = list(DestinationPaper().write(config, None, [message]))[0].state.data["_puente_ack"]

    assert acknowledgment["status"] == "rejected"
    assert acknowledgment["reason"] == "canonical_mismatch"
    assert acknowledgment["response"]["persisted"] is False
