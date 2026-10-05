#!/usr/bin/env python3
"""Check the actual Linux CI user and execution filesystems before heavy work."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import sys


MIN_FREE_BYTES = 10_000_000_000


def existing_parent(path: Path) -> Path:
    """Resolve a possibly not-yet-created cache/output directory without writing."""
    current = path.resolve()
    while not current.exists():
        parent = current.parent
        if parent == current:
            raise ValueError("No existing filesystem ancestor")
        current = parent
    if not current.is_dir():
        raise ValueError("Execution location is not a directory")
    return current


def inspect_host(root: Path) -> dict:
    uid = os.getuid() if hasattr(os, "getuid") else None
    result = {"schema": 1, "platform": sys.platform, "uid": uid,
              "minimum_free_bytes": MIN_FREE_BYTES, "filesystems": [], "errors": []}
    if sys.platform != "linux" or uid is None or uid <= 0:
        result["errors"].append("Linux execution must use a non-root account")
    locations = {
        "checkout_and_build": root,
        "temporary_and_evidence": Path(os.environ.get("RUNNER_TEMP", "/tmp")),
        "gradle_cache": Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))),
    }
    for label, path in locations.items():
        try:
            ancestor = existing_parent(path)
            usage = shutil.disk_usage(ancestor)
            result["filesystems"].append({"purpose": label, "location": str(path.resolve()),
                                           "sampled_at": str(ancestor), "free_bytes": usage.free})
            if usage.free < MIN_FREE_BYTES:
                result["errors"].append(f"{label} has less than 10 GB free")
        except (OSError, ValueError, RuntimeError) as error:
            result["errors"].append(f"Cannot inspect {label}: {type(error).__name__}")
    result["result"] = "FAIL" if result["errors"] else "PASS"
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    arguments = parser.parse_args(argv)
    result = inspect_host(arguments.root)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 1 if result["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
