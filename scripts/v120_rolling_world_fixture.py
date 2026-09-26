#!/usr/bin/env python3
"""Extract and verify a bounded Rolling Machine chunk from a saved candidate world."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

if __package__:
    from . import v120_precision_world_fixture as common
    from .run_dedicated_server_smoke import is_link_or_junction
else:
    import v120_precision_world_fixture as common
    from run_dedicated_server_smoke import is_link_or_junction

ROOT = Path(__file__).resolve().parents[1]
SUMMARY = ROOT / "docs/work/v1.2.0-migration/rolling-restart/scenario/summary.json"
FIXTURE = ROOT / "docs/work/v1.2.0-migration/fixtures/rolling"
CHUNK = "c.8.8.nbt.zlib"
CONTROLLER = (132, 80, 132)
INPUT, FLUID, ENERGY, OUTPUT = ((130, 80, 132), (131, 80, 132),
                                (133, 80, 132), (134, 80, 132))
PORTS = {INPUT, FLUID, ENERGY, OUTPUT}
FixtureError = common.FixtureError


def _safe(path: Path) -> None:
    if any(is_link_or_junction(parent) for parent in (path, *path.parents)):
        raise FixtureError(f"Linked fixture path rejected: {path}")


def _read(path: Path, limit: int) -> bytes:
    _safe(path)
    return common._read_regular(path, limit)


def _json(path: Path, limit: int) -> tuple[dict, bytes]:
    source = _read(path, limit)
    try:
        value = json.loads(source)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise FixtureError("Fixture evidence is not valid JSON") from exc
    if not isinstance(value, dict):
        raise FixtureError("Fixture evidence is not an object")
    return value, source


def _source(path: Path) -> tuple[dict, dict]:
    summary, source = _json(path, 64 * 1024)
    final = summary.get("after_final_restart")
    if (summary.get("schema_version") != 1 or summary.get("slice") != "V120-ROLL-05"
            or summary.get("same_world_verified") is not True
            or summary.get("exact_once_output_verified") is not True
            or summary.get("durable_save_before_forced_stop") is not True
            or summary.get("progress_preserved_exactly") is not True
            or summary.get("before_forced_stop") != summary.get("after_forced_stop_restart")
            or not isinstance(final, dict) or summary.get("completed") != final
            or summary.get("stable_after_completion") != final):
        raise FixtureError("Rolling summary lacks same-world recovery evidence")
    expected = {"formation": "FORMED", "generation": 1, "state": "IDLE", "failure": "NONE",
                "progress": 0, "total": 0, "input_item": "minecraft:air", "input_count": 0,
                "water": 400, "energy": 2000, "output_item": "minecraft:iron_bars",
                "output_count": 8, "revision": 7, "journal": "none"}
    if set(final) != set(expected) | {"last_applied"} or any(
            final.get(key) != value for key, value in expected.items()):
        raise FixtureError("Rolling summary does not conserve the seeded batch")
    common._canonical_uuid(final["last_applied"])
    region = summary.get("region_after_final_restart")
    artifact = summary.get("artifact_sha256")
    base = summary.get("implementation_base_commit")
    dirty = summary.get("uncommitted_worktree")
    tested = summary.get("tested_implementation_commit")
    if (not isinstance(region, dict) or region.get("path") != "world/region/r.0.0.mca"
            or not isinstance(region.get("sha256"), str)
            or common.SHA256.fullmatch(region["sha256"]) is None
            or type(region.get("size")) is not int or not 8192 <= region["size"] <= common.MAX_REGION_BYTES
            or not isinstance(artifact, str) or common.SHA256.fullmatch(artifact) is None
            or not isinstance(base, str) or re.fullmatch(r"[0-9a-f]{40}", base) is None
            or type(dirty) is not bool or tested != (None if dirty else base)):
        raise FixtureError("Rolling summary has invalid source provenance")
    provenance = {
        "source_summary_sha256": common._sha256(source),
        "source_region_sha256": region["sha256"], "source_region_bytes": region["size"],
        "artifact_sha256": artifact, "source_tested_commit": tested,
        "implementation_base_commit": base, "uncommitted_worktree": dirty,
    }
    return summary, provenance


def _validate(compressed: bytes, summary: dict) -> str:
    root = common._decode_chunk(compressed, 8, 8)
    if root.get("DataVersion") != 3465:
        raise FixtureError("Rolling chunk has an unexpected DataVersion")
    encoded = root.get("block_entities")
    if not isinstance(encoded, list) or len(encoded) != 5:
        raise FixtureError("Rolling chunk must contain exactly five BlockEntities")
    entities = {}
    for entity in encoded:
        position = common._position(entity)
        if position in entities:
            raise FixtureError("Rolling BlockEntity identity is duplicated")
        entities[position] = entity
    if set(entities) != {CONTROLLER} | PORTS:
        raise FixtureError("Rolling controller/port coordinates differ from the scenario")
    controller = entities[CONTROLLER]
    lifecycle = controller.get("arce_multiblock")
    if (controller.get("id") != common.MOD + "rolling_machine"
            or not isinstance(lifecycle, dict) or lifecycle.get("schema_version") != 1
            or set(lifecycle) != {"schema_version", "generation", "rotation", "mirror_local_x",
                                  "parts", "machine_instance_id", "formation_state"}
            or lifecycle.get("rotation") != 0 or lifecycle.get("mirror_local_x") != 0
            or lifecycle.get("formation_state") != "FORMED" or lifecycle.get("generation") != 1
            or not isinstance(lifecycle.get("parts"), list) or len(lifecycle["parts"]) != 4
            or {common._position(part) for part in lifecycle["parts"]} != PORTS):
        raise FixtureError("Rolling controller identity or formation is invalid")
    machine_id = common._canonical_uuid(lifecycle.get("machine_instance_id"))
    expected_process = {
        "schema_version": 1, "state": "idle", "resource_revision": 7,
        "definition_id": "", "recipe_signature": "", "progress_ticks": 0,
        "consumed_energy": 0, "last_applied_transaction": summary["after_final_restart"]["last_applied"],
        "failure_code": "none", "failure_subject": "",
    }
    if controller.get("arce_process") != expected_process or "arce_process_journal" in controller:
        raise FixtureError("Rolling process differs from the final restart report")
    resources = {
        INPUT: {"port_type": "rolling_machine_item_input_port", "item": {}},
        OUTPUT: {"port_type": "rolling_machine_item_output_port",
                 "item": {"id": "minecraft:iron_bars", "Count": 8}},
        FLUID: {"port_type": "rolling_machine_fluid_input_port",
                "fluid": {"FluidName": "minecraft:water", "Amount": 400}},
        ENERGY: {"port_type": "rolling_machine_energy_input_port", "energy": 2000},
    }
    for position, expected in resources.items():
        port = entities[position]
        binding = port.get("arce_part_binding")
        if (port.get("id") != common.MOD + "rolling_machine_port"
                or port.get("arce_rolling_port") != {"schema_version": 1, **expected}
                or not isinstance(binding, dict) or binding.get("schema_version") != 1
                or set(binding) != {"schema_version", "generation", "controller",
                                    "controller_level", "machine_instance_id"}
                or binding.get("controller_level") != "minecraft:overworld"
                or common._position(binding.get("controller")) != CONTROLLER
                or binding.get("machine_instance_id") != machine_id or binding.get("generation") != 1):
            raise FixtureError(f"Rolling port {position} resources or binding differs from the report")
    return machine_id


def create(region_path: Path, output: Path = FIXTURE, summary_path: Path = SUMMARY) -> dict:
    _safe(output)
    if output.exists():
        raise FixtureError(f"Refusing to overwrite fixture directory: {output}")
    summary, provenance = _source(summary_path)
    region = _read(region_path, common.MAX_REGION_BYTES)
    if (len(region) != provenance["source_region_bytes"]
            or common._sha256(region) != provenance["source_region_sha256"]):
        raise FixtureError("Source region does not match the final restart evidence")
    compressed = common._region_chunk(region, 8, 8)
    machine_id = _validate(compressed, summary)
    manifest = {"schema_version": 1, **provenance, "machine_instance_id": machine_id,
                "chunks": {CHUNK: common._sha256(compressed)}}
    output.mkdir(parents=True)
    (output / CHUNK).write_bytes(compressed)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n",
                                         encoding="utf-8", newline="\n")
    return manifest


def verify(fixture: Path = FIXTURE, summary_path: Path = SUMMARY) -> dict:
    _safe(fixture)
    if not fixture.is_dir():
        raise FixtureError("Rolling fixture directory is missing")
    manifest, _ = _json(fixture / "manifest.json", 16 * 1024)
    summary, provenance = _source(summary_path)
    if (set(manifest) != set(provenance) | {"schema_version", "machine_instance_id", "chunks"}
            or manifest.get("schema_version") != 1
            or any(manifest.get(key) != value for key, value in provenance.items())
            or not isinstance(manifest.get("chunks"), dict) or set(manifest["chunks"]) != {CHUNK}
            or {path.name for path in fixture.iterdir()} != {CHUNK, "manifest.json"}):
        raise FixtureError("Rolling manifest or file set differs from source evidence")
    compressed = _read(fixture / CHUNK, common.MAX_COMPRESSED_CHUNK)
    if common._sha256(compressed) != manifest["chunks"][CHUNK]:
        raise FixtureError("Rolling fixture chunk hash mismatch")
    if _validate(compressed, summary) != manifest["machine_instance_id"]:
        raise FixtureError("Rolling manifest machine identity changed")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--create-from-region", type=Path)
    action.add_argument("--verify", action="store_true")
    parser.add_argument("--fixture", type=Path, default=FIXTURE)
    parser.add_argument("--summary", type=Path, default=SUMMARY)
    args = parser.parse_args()
    try:
        manifest = verify(args.fixture, args.summary) if args.verify else create(
            args.create_from_region, args.fixture, args.summary)
    except (FixtureError, OSError, KeyError, TypeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print("[PASS] Rolling saved-world fixture: one chunk, one controller, four ports")
    print(f"[PASS] Source region SHA-256: {manifest['source_region_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
