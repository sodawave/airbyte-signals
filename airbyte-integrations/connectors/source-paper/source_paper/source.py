from typing import Any, List, Mapping, Tuple

from airbyte_cdk.logger import AirbyteLogger
from airbyte_cdk.sources import AbstractSource
from airbyte_cdk.sources.streams import Stream

from .streams import PaperFills


class SourcePaper(AbstractSource):
    def check_connection(self, logger: AirbyteLogger, config: Mapping[str, Any]) -> Tuple[bool, Any]:
        symbol_map = config.get("symbol_map")
        if not isinstance(symbol_map, dict) or not symbol_map:
            return False, "symbol_map must contain the entity's explicit symbol map"
        return True, None

    def streams(self, config: Mapping[str, Any]) -> List[Stream]:
        return [PaperFills(config)]
