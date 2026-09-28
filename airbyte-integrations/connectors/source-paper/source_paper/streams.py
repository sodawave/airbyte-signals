import json
from pathlib import Path
from typing import Any, Iterable, Mapping, Optional

from airbyte_cdk.models import SyncMode
from airbyte_cdk.sources.streams import Stream


class PaperFills(Stream):
    primary_key = "source_fill_id"

    def __init__(self, config: Mapping[str, Any]):
        self._config = config

    @property
    def name(self) -> str:
        return "fills"

    @property
    def supported_sync_modes(self):
        return [SyncMode.full_refresh]

    def get_json_schema(self) -> Mapping[str, Any]:
        return {
            "$schema": "http://json-schema.org/draft-07/schema#",
            "type": "object",
            "required": ["source_fill_id", "symbol", "side", "quantity", "price", "origen_en"],
            "properties": {
                "source_fill_id": {"type": "string"},
                "symbol": {"type": "string"},
                "side": {"type": "string", "enum": ["buy", "sell"]},
                "quantity": {"type": "number"},
                "price": {"type": "number"},
                "origen_en": {"type": "string", "format": "date-time"},
            },
        }

    def read_records(
        self,
        sync_mode: SyncMode,
        cursor_field: Optional[list] = None,
        stream_slice: Optional[Mapping[str, Any]] = None,
        stream_state: Optional[Mapping[str, Any]] = None,
    ) -> Iterable[Mapping[str, Any]]:
        fixture_path = Path(__file__).parent / "fixtures" / "fills.json"
        fixtures = json.loads(fixture_path.read_text())
        origin_override = self._config.get("fixture_origin")
        for fixture in fixtures:
            yield {**fixture, "origen_en": origin_override or fixture["origen_en"]}
