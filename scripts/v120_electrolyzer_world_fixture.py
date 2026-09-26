#!/usr/bin/env python3
"""Extract a bounded legacy Electrolyzer chunk from the immutable real Beta world."""

from __future__ import annotations

import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

if __package__:
    from . import v120_precision_world_fixture as common
    from .run_dedicated_server_smoke import is_link_or_junction
else:
    import v120_precision_world_fixture as common
    from run_dedicated_server_smoke import is_link_or_junction


SOURCE = common.ROOT / "docs/work/v1.0.0-beta-world-upgrade/verification/populated"
SUMMARY = SOURCE / "summary.json"
STATE = SOURCE / "beta-state.json"
ARCHIVE = SOURCE / "original-beta-world.zip"
FIXTURE = common.ROOT / "docs/work/v1.2.0-migration/fixtures/electrolyzer-beta"
REGION = "world/region/r.0.0.mca"
CHUNK = "c.0.0.nbt.zlib"
POSITIONS = {(0, 100, 0): "paused", (4, 100, 0): "running"}
MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
FixtureError = common.FixtureError


def _read(path: Path, limit: int) -> bytes:
    if any(is_link_or_junction(parent) for parent in (path, *path.parents)):
        raise FixtureError(f"Fixture path traverses a link or junction: {path}")
    return common._read_regular(path, limit)


def _json(path: Path, limit: int) -> tuple[dict, str]:
    raw = _read(path, limit)
    try:
        value = json.loads(raw)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise FixtureError("Fixture metadata is not valid JSON") from error
    if not isinstance(value, dict):
        raise FixtureError("Fixture metadata is not an object")
    return value, common._sha256(raw)


def _source(summary_path: Path, state_path: Path) -> tuple[dict, dict]:
    summary, summary_hash = _json(summary_path, 128 * 1024)
    state, state_hash = _json(state_path, 256 * 1024)
    archive = summary.get("original_world_archive")
    entries = summary.get("original_world_manifest")
    if (summary.get("original_unchanged") is not True
            or summary.get("mod_version") != "1.20.1-0.9.0-beta.1"
            or not isinstance(archive, dict) or not isinstance(entries, list)
            or len(entries) > 256):
        raise FixtureError("Source summary lacks bounded immutable Beta-world provenance")
    regions = [entry for entry in entries if isinstance(entry, dict)
               and entry.get("file") == "region/r.0.0.mca"]
    if len(regions) != 1:
        raise FixtureError("Source summary has no unique overworld region")
    region = regions[0]
    for value in (summary.get("artifact_sha256"), archive.get("sha256"), region.get("sha256")):
        if not isinstance(value, str) or common.SHA256.fullmatch(value) is None:
            raise FixtureError("Source provenance has an invalid SHA-256")
    for value, limit in ((archive.get("bytes"), MAX_ARCHIVE_BYTES),
                         (region.get("bytes"), common.MAX_REGION_BYTES)):
        if type(value) is not int or not 8192 <= value <= limit:
            raise FixtureError("Source archive/region has an invalid byte bound")
    observed = state.get("observed")
    if not isinstance(observed, dict) or observed.get("flush_success") != 1:
        raise FixtureError("Source state lacks its completed save receipt")
    expected = {name: observed.get(name) for name in POSITIONS.values()}
    for name, raw in expected.items():
        ledger = summary.get("initial_ledger", {}).get(name)
        if not isinstance(raw, dict) or not isinstance(ledger, dict):
            raise FixtureError("Source state has no legacy machine or initial ledger")
        inventory = raw.get("inventory")
        fluid = raw.get("fluid")
        if (raw.get("schema_version") != 1 or raw.get("progress") != ledger.get("progress")
                or raw.get("energy") != ledger.get("energy")
                or raw.get("active_recipe") != common.MOD + "electrolyzer_water"
                or not isinstance(fluid, dict) or fluid.get("FluidName") != "minecraft:water"
                or fluid.get("Amount") != ledger.get("water")
                or not isinstance(inventory, dict) or inventory.get("Size") != 4
                or inventory.get("Items") != [{"Slot": 0, "id": common.MOD + "empty_canister",
                                                "Count": ledger.get("items", {}).get("0")}]
                or set(raw) != {"schema_version", "inventory", "fluid", "energy", "progress", "active_recipe"}):
            raise FixtureError("Source legacy state differs from its initial material ledger")
    provenance = {
        "schema_version": 1,
        "source_version": summary["mod_version"],
        "source_summary_sha256": summary_hash,
        "source_state_sha256": state_hash,
        "source_archive_sha256": archive["sha256"],
        "source_archive_bytes": archive["bytes"],
        "source_region_sha256": region["sha256"],
        "source_region_bytes": region["bytes"],
        "source_artifact_sha256": summary["artifact_sha256"],
    }
    return provenance, expected


def _validate(compressed: bytes, expected: dict) -> None:
    root = common._decode_chunk(compressed, 0, 0)
    if root.get("DataVersion") != 3465:
        raise FixtureError("Legacy chunk is not the recorded Minecraft 1.20.1 data version")
    entities = root.get("block_entities")
    if not isinstance(entities, list) or len(entities) != 2:
        raise FixtureError("Legacy chunk must contain exactly two BlockEntities")
    seen = set()
    for entity in entities:
        position = common._position(entity)
        if (entity.get("id") != common.MOD + "electrolyzer" or position not in POSITIONS
                or position in seen):
            raise FixtureError("Legacy Electrolyzer identity/position is invalid or duplicated")
        seen.add(position)
        if "arce_process" in entity or "arce_process_journal" in entity:
            raise FixtureError("Legacy input already contains migrated process roots")
        if entity.get("arce_machine") != expected[POSITIONS[position]]:
            raise FixtureError("Legacy Electrolyzer resources/progress differ from the Beta capture")


def create(archive_path: Path = ARCHIVE, output: Path = FIXTURE,
           summary_path: Path = SUMMARY, state_path: Path = STATE) -> dict:
    if output.exists() or any(is_link_or_junction(p) for p in (output, *output.parents)):
        raise FixtureError("Refusing existing or linked fixture output")
    provenance, expected = _source(summary_path, state_path)
    archive = _read(archive_path, MAX_ARCHIVE_BYTES)
    if (len(archive) != provenance["source_archive_bytes"]
            or common._sha256(archive) != provenance["source_archive_sha256"]):
        raise FixtureError("Source archive does not match the immutable Beta-world hash")
    try:
        with zipfile.ZipFile(io.BytesIO(archive)) as source:
            if len(source.infolist()) > 256:
                raise FixtureError("Source archive exceeds its entry bound")
            regions = [entry for entry in source.infolist() if entry.filename == REGION]
            if len(regions) != 1 or regions[0].file_size != provenance["source_region_bytes"]:
                raise FixtureError("Source archive region is missing, duplicated or oversized")
            # Read only this fixed entry into memory; never extract ZIP paths.
            with source.open(regions[0]) as stream:
                region = stream.read(common.MAX_REGION_BYTES + 1)
    except (zipfile.BadZipFile, RuntimeError) as error:
        raise FixtureError("Source archive is invalid") from error
    if (len(region) != provenance["source_region_bytes"]
            or common._sha256(region) != provenance["source_region_sha256"]):
        raise FixtureError("Archived region differs from the original world manifest")
    compressed = common._region_chunk(region, 0, 0)
    _validate(compressed, expected)
    manifest = {**provenance, "chunks": {CHUNK: common._sha256(compressed)}}
    output.mkdir(parents=True)
    (output / CHUNK).write_bytes(compressed)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n",
                                         encoding="utf-8", newline="\n")
    return manifest


def verify(fixture: Path = FIXTURE, summary_path: Path = SUMMARY, state_path: Path = STATE) -> dict:
    provenance, expected = _source(summary_path, state_path)
    manifest, _ = _json(fixture / "manifest.json", 16 * 1024)
    if ({p.name for p in fixture.iterdir()} != {CHUNK, "manifest.json"}
            or set(manifest) != set(provenance) | {"chunks"}
            or any(manifest.get(key) != value for key, value in provenance.items())
            or not isinstance(manifest.get("chunks"), dict) or set(manifest["chunks"]) != {CHUNK}):
        raise FixtureError("Legacy fixture manifest/file set disagrees with its source")
    compressed = _read(fixture / CHUNK, common.MAX_COMPRESSED_CHUNK)
    if common._sha256(compressed) != manifest["chunks"][CHUNK]:
        raise FixtureError("Legacy fixture chunk hash mismatch")
    _validate(compressed, expected)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--create", action="store_true")
    action.add_argument("--verify", action="store_true")
    parser.add_argument("--fixture", type=Path, default=FIXTURE)
    parser.add_argument("--archive", type=Path, default=ARCHIVE)
    args = parser.parse_args()
    try:
        manifest = create(args.archive, args.fixture) if args.create else verify(args.fixture)
    except (FixtureError, OSError, KeyError, TypeError, ValueError) as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print("[PASS] Real Beta Electrolyzer fixture: one chunk, two legacy machines")
    print(f"[PASS] Source archive SHA-256: {manifest['source_archive_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
