#!/usr/bin/env python3
"""Verify the immutable v0.9.0 SavedData migration fixture inventory."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FIXTURE_ROOT = Path("src/test/resources/migrations/v090")
MANIFEST_NAME = "manifest.json"
MAX_FIXTURE_BYTES = 1_048_576
MAX_MANIFEST_BYTES = 65_536
EXPECTED_FIXTURES = {
    "v030-celestial-v1.snbt": ("advancedrocketrycommunity_celestial", "v0.3.0"),
    "v050-rocket-transactions-v1.snbt": (
        "advancedrocketrycommunity_rocket_transactions",
        "v0.5.0",
    ),
    "v060-rocket-transfers-v1.snbt": (
        "advancedrocketrycommunity_rocket_transfers",
        "v0.6.0",
    ),
    "v070-stations-v1.snbt": ("advancedrocketrycommunity_stations", "v0.7.0"),
    "v080-satellite-missions-v1.snbt": (
        "advancedrocketrycommunity_satellite_missions",
        "v0.8.0",
    ),
}


def _canonical_json(value: object) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def render_manifest(repository_root: Path = ROOT) -> dict[str, object]:
    fixture_root = repository_root / FIXTURE_ROOT
    if not fixture_root.is_dir() or fixture_root.is_symlink():
        raise ValueError("migration fixture root is missing or symbolic")
    actual = {
        path.name
        for path in fixture_root.iterdir()
        if path.name != MANIFEST_NAME
    }
    if actual != set(EXPECTED_FIXTURES):
        raise ValueError(
            "migration fixture inventory mismatch: "
            f"expected={sorted(EXPECTED_FIXTURES)}, actual={sorted(actual)}"
        )

    fixtures: list[dict[str, object]] = []
    for name, (data_name, introduced_in) in sorted(EXPECTED_FIXTURES.items()):
        path = fixture_root / name
        if not path.is_file() or path.is_symlink():
            raise ValueError(f"migration fixture is not a regular file: {name}")
        payload = path.read_bytes()
        if not payload or len(payload) > MAX_FIXTURE_BYTES:
            raise ValueError(f"migration fixture has an invalid size: {name}")
        try:
            text = payload.decode("utf-8")
        except UnicodeDecodeError as error:
            raise ValueError(f"migration fixture is not UTF-8: {name}") from error
        if "\r" in text or not text.endswith("\n"):
            raise ValueError(f"migration fixture must use canonical LF text: {name}")
        if re.search(r"(?:^|[,{])\s*schema_version\s*:\s*1(?:\s*[,}])", text) is None:
            raise ValueError(f"migration fixture does not declare root schema 1: {name}")
        fixtures.append(
            {
                "data_name": data_name,
                "introduced_in": introduced_in,
                "path": (FIXTURE_ROOT / name).as_posix(),
                "schema_version": 1,
                "sha256": hashlib.sha256(payload).hexdigest(),
                "size": len(payload),
            }
        )
    return {
        "fixture_format": "arce-v090-saved-data-fixtures-v1",
        "fixtures": fixtures,
        "target_root_schema": 2,
    }


def write_manifest(
    repository_root: Path = ROOT,
    output: Path | None = None,
) -> dict[str, object]:
    manifest = render_manifest(repository_root)
    target = output or repository_root / FIXTURE_ROOT / MANIFEST_NAME
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".tmp")
    temporary.write_bytes(_canonical_json(manifest))
    temporary.replace(target)
    return manifest


def verify(
    repository_root: Path = ROOT,
    manifest_path: Path | None = None,
) -> list[str]:
    errors: list[str] = []
    try:
        expected = render_manifest(repository_root)
    except (OSError, ValueError) as error:
        return [str(error)]
    path = manifest_path or repository_root / FIXTURE_ROOT / MANIFEST_NAME
    try:
        if not path.is_file() or path.is_symlink():
            return ["migration fixture manifest is missing or symbolic"]
        payload = path.read_bytes()
        if len(payload) > MAX_MANIFEST_BYTES:
            return ["migration fixture manifest exceeds the byte limit"]
        actual = json.loads(payload.decode("utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        return [f"cannot read migration fixture manifest: {error}"]
    if actual != expected:
        errors.append("migration fixture manifest does not match exact fixture bytes")
    if payload != _canonical_json(actual):
        errors.append("migration fixture manifest is not canonical sorted JSON")
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", nargs="?", choices=("verify", "write"), default="verify")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--manifest", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    manifest = args.manifest.resolve() if args.manifest else None
    if args.command == "write":
        value = write_manifest(root, manifest)
        print(f"[PASS] Recorded {len(value['fixtures'])} immutable migration fixtures")
        return 0
    errors = verify(root, manifest)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(f"[PASS] v0.9.0 migration fixtures match {len(EXPECTED_FIXTURES)} hashes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
