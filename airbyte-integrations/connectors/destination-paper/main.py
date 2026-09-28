import sys

from airbyte_cdk.entrypoint import launch
from destination_paper import DestinationPaper

if __name__ == "__main__":
    launch(DestinationPaper(), sys.argv[1:])
