#!/usr/bin/env python3
"""Download the vanilla client JARs the derivation check compares against (ADR-061 §4.8).

The JARs come from Mojang's version manifest, are verified against the pinned
SHA-1 values Mojang publishes and the SHA-256 values this project records, and
are written to a directory outside the repository. They are read for
comparison only and never copied into the tree or a release.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
CLIENTS = {
    "1.12.2": {"sha1": "0f275bc1547d01fa5f56ba34bdc87d981ee12daf",
               "sha256": "8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f"},
    "1.20.1": {"sha1": "0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838",
               "sha256": "56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f"},
}
MAX_BYTES = 64 * 1024 * 1024


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=120) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise SystemExit(f"{url} is larger than {MAX_BYTES} bytes")
    return data


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--output", required=True, type=Path, help="directory outside the repository")
    arguments = parser.parse_args(argv)
    arguments.output.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(fetch(MANIFEST))
    for version, pins in CLIENTS.items():
        target = arguments.output / f"client-{version}.jar"
        if target.exists() and hashlib.sha1(target.read_bytes()).hexdigest() == pins["sha1"]:
            data = target.read_bytes()
        else:
            entry = next(item for item in manifest["versions"] if item["id"] == version)
            download = json.loads(fetch(entry["url"]))["downloads"]["client"]
            if download["sha1"] != pins["sha1"]:
                raise SystemExit(f"{version}: Mojang lists SHA-1 {download['sha1']}, pinned {pins['sha1']}")
            data = fetch(download["url"])
            if hashlib.sha1(data).hexdigest() != pins["sha1"]:
                raise SystemExit(f"{version}: downloaded bytes do not match the pinned SHA-1")
            target.write_bytes(data)
        digest = hashlib.sha256(data).hexdigest()
        if pins["sha256"] is not None and digest != pins["sha256"]:
            raise SystemExit(f"{version}: SHA-256 {digest} differs from the pinned value")
        print(f"{version}={target} sha256={digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
