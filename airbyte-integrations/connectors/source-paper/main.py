import sys

from airbyte_cdk.entrypoint import launch
from source_paper import SourcePaper

if __name__ == "__main__":
    launch(SourcePaper(), sys.argv[1:])
