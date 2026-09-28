from airbyte_cdk.models import SyncMode

from source_paper import SourcePaper


CONFIG = {
    "entity_id": "paper-source",
    "symbol_map": {"DAX": "DE40", "DJ": "US30", "ES": "US500"},
    "supported_asset_classes": ["index"],
    "tradable_symbols": ["DAX", "DJ", "ES"],
    "fixture_origin": "2026-01-01T00:00:00+00:00",
}


def test_check_discover_and_read_deterministic_fills():
    source = SourcePaper()
    ok, error = source.check_connection(None, CONFIG)
    assert ok and error is None

    stream = source.streams(CONFIG)[0]
    assert stream.name == "fills"
    records = list(stream.read_records(SyncMode.full_refresh))
    assert [record["symbol"] for record in records] == ["DAX", "DJ", "ES"]
    assert [record["source_fill_id"] for record in records] == [
        "paper-dax-1",
        "paper-dj-1",
        "paper-es-1",
    ]


def test_check_rejects_incomplete_or_inconsistent_entity_configuration():
    source = SourcePaper()
    for patch in (
        {"entity_id": ""},
        {"symbol_map": {}},
        {"supported_asset_classes": []},
        {"tradable_symbols": ["UNKNOWN"]},
        {"symbol_map": {"DAX": ""}},
    ):
        config = {**CONFIG, **patch}
        ok, error = source.check_connection(None, config)
        assert not ok
        assert error
