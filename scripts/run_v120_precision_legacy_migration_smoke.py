#!/usr/bin/env python3
"""Restart a disposable archived Precision world with the controller-owned Item build."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction, resolve_java
    from .run_v060_flight_server_smoke import FlightHarness
    from .run_v120_precision_restart_smoke import EXPECTED_VERSION, _assert_complete, _wait_report
    from . import v120_precision_world_fixture as fixture
else:
    from run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction, resolve_java
    from run_v060_flight_server_smoke import FlightHarness
    from run_v120_precision_restart_smoke import EXPECTED_VERSION, _assert_complete, _wait_report
    import v120_precision_world_fixture as fixture


ROOT = Path(__file__).resolve().parents[1]
SOURCE_SUMMARY = ROOT / "docs/work/v1.2.0-precision-assembler/packaged-restart/precision/summary.json"
REGION = Path("world/region/r.0.0.mca")
CONTROLLER_ROOT = "arce_precision_resources"
MARKER_ROOT = "arce_precision_port_migration"


def _machine_entities(compressed: dict[tuple[int, int], bytes]) -> dict[tuple[int, int, int], dict]:
    entities: dict[tuple[int, int, int], dict] = {}
    for (x, z), data in compressed.items():
        chunk = fixture._decode_chunk(data, x, z)
        block_entities = chunk.get("block_entities")
        if not isinstance(block_entities, list):
            raise SmokeError(f"Chunk {x},{z} has no BlockEntity list")
        for block_entity in block_entities:
            if not isinstance(block_entity, dict):
                raise SmokeError(f"Chunk {x},{z} has an invalid BlockEntity")
            if block_entity.get("id") not in (
                fixture.MOD + "precision_assembler", fixture.MOD + "precision_assembler_port"
            ):
                continue
            position = fixture._position(block_entity)
            if position in entities:
                raise SmokeError(f"Duplicate Precision BlockEntity at {position}")
            entities[position] = block_entity
    if set(entities) != {fixture.CONTROLLER} | fixture.PORTS:
        raise SmokeError("Archived world no longer contains the exact Precision structure")
    return entities


def _fixture_entities() -> dict[tuple[int, int, int], dict]:
    compressed = {
        (x, z): fixture._read_regular(
            fixture.FIXTURE / f"c.{x}.{z}.nbt.zlib", fixture.MAX_COMPRESSED_CHUNK
        ) for x, z in fixture.CHUNKS
    }
    return _machine_entities(compressed)


def _saved_entities(region_path: Path) -> dict[tuple[int, int, int], dict]:
    region = fixture._read_regular(region_path, fixture.MAX_REGION_BYTES)
    compressed = {(x, z): fixture._region_chunk(region, x, z) for x, z in fixture.CHUNKS}
    return _machine_entities(compressed)


def _assert_migrated(actual: dict, original: dict, machine_id: str) -> dict:
    controller = actual[fixture.CONTROLLER]
    root = controller.get(CONTROLLER_ROOT)
    if (not isinstance(root, dict) or root.get("schema_version") != 1
            or root.get("machine_id") != machine_id or root.get("phase") != "active"
            or not isinstance(root.get("items"), list) or len(root["items"]) != 7):
        raise SmokeError("Migrated controller Item root is missing or inactive")
    old_process = original[fixture.CONTROLLER].get("arce_process")
    if controller.get("arce_process") != old_process:
        raise SmokeError("Legacy process state changed during Item ownership migration")
    for index, position in enumerate(fixture.INPUTS + fixture.OUTPUTS):
        old_port = original[position]
        port = actual[position]
        old_resources = old_port.get("arce_precision_port")
        if port.get("arce_precision_port") != old_resources:
            raise SmokeError(f"Legacy Item shadow changed at {position}")
        if root["items"][index] != old_resources.get("item"):
            raise SmokeError(f"Controller Item slot differs from legacy port at {position}")
        expected_channel = f"item_input_{index}" if index < 5 else f"item_output_{index - 5}"
        if port.get(MARKER_ROOT) != {
            "schema_version": 1, "machine_id": machine_id, "channel": expected_channel
        }:
            raise SmokeError(f"Legacy port marker is missing or incorrect at {position}")
    if actual[fixture.ENERGY].get("arce_precision_port") != original[fixture.ENERGY].get(
            "arce_precision_port"):
        raise SmokeError("Physical Energy port changed during Item migration")
    return root


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    java_home = os.environ.get("JAVA_HOME")
    parser.add_argument("--java", default=str(Path(java_home) / "bin/java") if java_home else "java")
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    args = parser.parse_args()
    process = None
    try:
        manifest = fixture.verify()
        original = _fixture_entities()
        old_summary = json.loads(SOURCE_SUMMARY.read_text(encoding="utf-8"))
        server = args.server_dir.resolve()
        evidence = args.evidence_dir.resolve()
        if is_link_or_junction(server) or not server.is_dir() or evidence.exists():
            raise SmokeError("Disposable server or evidence path is unsafe or already exists")
        region = server / REGION
        if is_link_or_junction(region) or digest_file(region) != manifest["source_region_sha256"]:
            raise SmokeError("Disposable world is not an exact copy of the archived old save")
        artifact = args.artifact.resolve()
        installed = server / "mods" / f"advancedrocketry-community-{EXPECTED_VERSION}.jar"
        if (is_link_or_junction(artifact) or is_link_or_junction(installed)
                or not artifact.is_file() or not installed.is_file()
                or digest_file(artifact) != digest_file(installed)):
            raise SmokeError("Installed development artifact does not match the requested build")
        artifact_hash = digest_file(artifact)
        port = old_summary.get("port")
        if not isinstance(port, int) or not 1 <= port <= 65535:
            raise SmokeError("Archived server port is invalid")
        java, java_version = resolve_java(args.java)
        harness = FlightHarness(
            java=java, server=server, port=port,
            expected_version=EXPECTED_VERSION, startup_timeout=args.startup_timeout,
        )

        process = harness.start("v120-precision-legacy-migration")
        process.command("forceload add 142 143 144 146")
        time.sleep(2.0)
        first = _wait_report(process, lambda value: value["formation"] == "FORMED"
                             and value["outputs"] == tuple(old_summary["after_final_restart"]["outputs"]),
                             "legacy Precision migration")
        _assert_complete(first, old_summary["after_final_restart"]["last_applied"])
        harness.stop(process)
        process = None
        first_root = _assert_migrated(_saved_entities(region), original, manifest["machine_instance_id"])
        first_region_hash = digest_file(region)

        process = harness.start("v120-precision-legacy-restart")
        process.command("forceload add 142 143 144 146")
        second = _wait_report(process, lambda value: value["formation"] == "FORMED"
                              and value["outputs"] == first["outputs"], "migrated Precision restart")
        if second != first:
            raise SmokeError(f"Precision state changed after migrated-world restart: {first} -> {second}")
        harness.stop(process)
        process = None
        second_root = _assert_migrated(_saved_entities(region), original, manifest["machine_instance_id"])
        if second_root != first_root:
            raise SmokeError("Controller Item root changed after an idle migrated-world restart")

        evidence.mkdir(parents=True)
        summary = {
            "schema_version": 1, "version": "v1.2.0", "slice": "V120-PREC-04B",
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version, "artifact_sha256": artifact_hash,
            "source_fixture_manifest_sha256": digest_file(fixture.FIXTURE / "manifest.json"),
            "source_region_sha256": manifest["source_region_sha256"],
            "first_migrated_region_sha256": first_region_hash,
            "restart_region_sha256": digest_file(region),
            "machine_instance_id": manifest["machine_instance_id"],
            "all_seven_items_and_markers_persisted": True,
            "legacy_shadows_preserved": True,
            "energy_port_unchanged": True,
            "same_world_restarted": True,
            "first_report": first, "restart_report": second,
            "processes": harness.process_documents,
        }
        (evidence / "summary.json").write_text(
            json.dumps(summary, sort_keys=True, indent=2) + "\n", encoding="utf-8", newline="\n"
        )
        (evidence / "filtered-lifecycle.log").write_text(
            "\n".join(harness.filtered_lines) + "\n", encoding="utf-8", newline="\n"
        )
        print("[PASS] Archived physical Item roots migrated to controller ownership")
        print("[PASS] Same-world restart preserved seven inert shadows and exact controller resources")
        print(f"[PASS] Artifact SHA-256: {artifact_hash}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, KeyError, TypeError, fixture.FixtureError) as exc:
        if process is not None:
            process.abort()
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
