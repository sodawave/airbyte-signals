from typing import Any, List, Mapping, Optional, Tuple

from airbyte_cdk.logger import AirbyteLogger
from airbyte_cdk.sources import AbstractSource
from airbyte_cdk.sources.streams import Stream

from .streams import PaperFills


class SourcePaper(AbstractSource):
    def check_connection(self, logger: AirbyteLogger, config: Mapping[str, Any]) -> Tuple[bool, Any]:
        error = validate_entity_config(config)
        if error:
            return False, error
        fixture_symbols = {record["symbol"] for record in PaperFills(config).read_records(None)}
        missing = fixture_symbols - set(config["tradable_symbols"])
        if missing:
            return False, f"Fixture symbols are not tradable: {sorted(missing)}"
        unmapped = fixture_symbols - set(config["symbol_map"])
        if unmapped:
            return False, f"Fixture symbols are not mapped: {sorted(unmapped)}"
        return True, None

    def streams(self, config: Mapping[str, Any]) -> List[Stream]:
        return [PaperFills(config)]


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
