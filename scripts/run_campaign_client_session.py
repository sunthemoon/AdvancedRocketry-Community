#!/usr/bin/env python3
"""Run the tracked campaign client harness. See campaign_client/README.md."""
import sys
from pathlib import Path

# Support direct execution and imports through the repository's scripts namespace.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.campaign_client.cli import main

if __name__ == "__main__":
    raise SystemExit(main())
