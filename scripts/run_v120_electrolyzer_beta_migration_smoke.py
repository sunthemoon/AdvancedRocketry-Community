#!/usr/bin/env python3
"""Upgrade an immutable Beta world copy and check only its two Electrolyzers."""

from __future__ import annotations

import argparse
import io
import json
import re
import shutil
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

if __package__:
    from . import v120_electrolyzer_world_fixture as fixture
    from . import run_dedicated_server_smoke as server
    from .inspect_v100_world_upgrade import manifest as world_manifest
    from .run_v100_beta_world_upgrade import require_stopped_port
    from .v100_beta_world_fixture import WorldHarness, wait_condition
else:
    import v120_electrolyzer_world_fixture as fixture
    import run_dedicated_server_smoke as server
    from inspect_v100_world_upgrade import manifest as world_manifest
    from run_v100_beta_world_upgrade import require_stopped_port
    from v100_beta_world_fixture import WorldHarness, wait_condition


EXPECTED_VERSION = "1.20.1-1.1.1-dev"
MAX_WORLD_BYTES = 64 * 1024 * 1024
PROCESS_FIELDS = {
    "schema_version", "state", "resource_revision", "definition_id", "recipe_signature",
    "progress_ticks", "consumed_energy", "last_applied_transaction", "failure_code", "failure_subject",
}
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
SmokeError = server.SmokeError


def _safe(path: Path) -> Path:
    if any(server.is_link_or_junction(p) for p in (path, *path.parents)):
        raise SmokeError("Migration paths must not traverse links or junctions")
    return path.resolve()


def _archive_files(raw: bytes, records: list[dict]) -> dict[str, bytes]:
    """Validate every member before writing anything; no ZIP extract/extractall."""
    if not isinstance(records, list) or not 1 <= len(records) <= 256:
        raise SmokeError("World manifest has an invalid entry count")
    expected = {}
    total = 0
    for record in records:
        name, size, digest = record.get("file"), record.get("bytes"), record.get("sha256")
        if (not isinstance(name, str) or not name or "\\" in name or ":" in name
                or name.startswith("/") or any(part in ("", ".", "..") for part in name.split("/"))
                or str(PurePosixPath(name)) != name or name.casefold() in expected
                or type(size) is not int or not 0 <= size <= fixture.common.MAX_REGION_BYTES
                or not isinstance(digest, str) or fixture.common.SHA256.fullmatch(digest) is None):
            raise SmokeError("World manifest has an unsafe or invalid member")
        total += size
        expected[name.casefold()] = (name, size, digest)
    if total > MAX_WORLD_BYTES or "level.dat" not in expected:
        raise SmokeError("World archive exceeds its bound or lacks level.dat")
    result = {}
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        members = archive.infolist()
        if (len(members) != len(expected)
                or {entry.filename for entry in members} != {"world/" + x[0] for x in expected.values()}):
            raise SmokeError("World ZIP member set differs from its original manifest")
        for entry in members:
            name = entry.filename.removeprefix("world/")
            _, size, digest = expected[name.casefold()]
            if entry.is_dir() or entry.file_size != size or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise SmokeError("World ZIP member is linked or has an incorrect size")
            with archive.open(entry) as stream:
                data = stream.read(size + 1)
            if len(data) != size or fixture.common._sha256(data) != digest:
                raise SmokeError("World ZIP member differs from its original hash")
            result[name] = data
    return result


def _prepare(runtime: Path, session: Path, artifact: Path, port: int, files: dict[str, bytes]) -> None:
    """Copy only installed libraries/config, never the runtime source's world."""
    libraries = runtime / "libraries"
    if not libraries.is_dir():
        raise SmokeError("Source runtime has no installed libraries")
    count, total = 0, 0
    for path in libraries.rglob("*"):
        _safe(path)
        if path.is_file():
            count += 1
            total += path.stat().st_size
            if count > 10000 or total > 1024**3:
                raise SmokeError("Installed libraries exceed the bounded copy inventory")
    properties = fixture._read(runtime / "server.properties", 64 * 1024).decode("utf-8")
    eula = fixture._read(runtime / "eula.txt", 4096)
    if not re.search(rb"(?m)^eula=true\s*$", eula):
        raise SmokeError("Source runtime lacks an already accepted EULA")
    session.mkdir(parents=True, exist_ok=False)
    shutil.copytree(libraries, session / "libraries")
    settings = {"server-ip": "127.0.0.1", "server-port": str(port), "online-mode": "false",
                "level-name": "world", "function-permission-level": "4"}
    for key, value in settings.items():
        properties = re.sub(r"(?m)^" + re.escape(key) + r"=.*(?:\n|$)", "", properties)
        properties += f"\n{key}={value}\n"
    (session / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
    (session / "eula.txt").write_bytes(eula)
    (session / "mods").mkdir()
    shutil.copy2(artifact, session / "mods" / artifact.name)
    for name, data in files.items():
        target = session / "world" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)


def _machines(compressed: bytes) -> dict[str, dict]:
    chunk = fixture.common._decode_chunk(compressed, 0, 0)
    entities = chunk.get("block_entities")
    if chunk.get("DataVersion") != 3465 or not isinstance(entities, list) or len(entities) != 2:
        raise SmokeError("Migrated chunk identity or BlockEntity count changed")
    result = {}
    for entity in entities:
        position = fixture.common._position(entity)
        name = fixture.POSITIONS.get(position)
        if name is None or name in result or entity.get("id") != fixture.common.MOD + "electrolyzer":
            raise SmokeError("Migrated Electrolyzer position/identity changed")
        result[name] = entity
    return result


def _assert_migrated(machines: dict, original: dict) -> None:
    for name, entity in machines.items():
        process = entity.get("arce_process")
        if ("arce_process_journal" in entity or not isinstance(process, dict)
                or set(process) != PROCESS_FIELDS or process["schema_version"] != 1
                or type(process["resource_revision"]) is not int or process["resource_revision"] < 0):
            raise SmokeError("Migrated process root is missing, invalid or retains a journal")
    paused = machines["paused"]
    if paused.get("arce_machine") != original["paused"]:
        raise SmokeError("Energy-starved legacy machine lost resources or progress")
    state = paused["arce_process"]
    if (state["state"] != "waiting_energy" or state["definition_id"] != fixture.common.MOD + "electrolyzer_water"
            or state["progress_ticks"] != 40 or state["consumed_energy"] != 800
            or fixture.common.SHA256.fullmatch(state["recipe_signature"]) is None
            or state["recipe_signature"] == "0" * 64 or state["last_applied_transaction"] != ""
            or state["failure_code"] != "insufficient_energy" or state["resource_revision"] != 0
            or state["failure_subject"] != fixture.common.MOD + "electrolyzer_water"):
        raise SmokeError("Legacy paused process was not adopted with exact paid progress")
    expected = {
        "schema_version": 1, "progress": 0, "energy": 12000,
        "fluid": {"FluidName": "minecraft:empty", "Amount": 0},
        "inventory": {"Size": 4, "Items": [
            {"Slot": 2, "id": fixture.common.MOD + "hydrogen_canister", "Count": 4},
            {"Slot": 3, "id": fixture.common.MOD + "oxygen_canister", "Count": 4},
        ]},
    }
    if machines["running"].get("arce_machine") != expected:
        raise SmokeError("Legacy running machine failed the four-batch resource/energy ledger")
    state = machines["running"]["arce_process"]
    if (state["state"] != "idle" or state["progress_ticks"] != 0 or state["consumed_energy"] != 0
            or state["definition_id"] != "" or state["recipe_signature"] != ""
            or UUID.fullmatch(state["last_applied_transaction"]) is None
            or state["failure_code"] != "none" or state["resource_revision"] != 4
            or state["failure_subject"] != ""):
        raise SmokeError("Completed machine retains active state or lacks transaction identity")


def _saved(session: Path) -> tuple[bytes, dict]:
    region = fixture._read(session / fixture.REGION, fixture.common.MAX_REGION_BYTES)
    compressed = fixture.common._region_chunk(region, 0, 0)
    return compressed, _machines(compressed)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--session", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--tested-commit", required=True)
    parser.add_argument("--tested-worktree", action="store_true")
    parser.add_argument("--java", default="java")
    parser.add_argument("--port", type=int, default=56061)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    args = parser.parse_args()
    process = None
    try:
        runtime, session, artifact, evidence = map(_safe, (args.runtime, args.session, args.artifact, args.evidence_dir))
        if (session.exists() or evidence.exists() or not runtime.is_dir() or not artifact.is_file()
                or re.fullmatch(r"[0-9a-f]{40}", args.tested_commit) is None or not 1024 <= args.port <= 65535):
            raise SmokeError("Invalid inputs or existing session/evidence output")
        for output in (session, evidence):
            if any(output == source or output.is_relative_to(source) or source.is_relative_to(output)
                   for source in (runtime, artifact, fixture.SOURCE.resolve())):
                raise SmokeError("Migration output overlaps a read-only source")
        if session.is_relative_to(evidence) or evidence.is_relative_to(session):
            raise SmokeError("Migration work and evidence must not overlap")
        require_stopped_port(args.port)
        provenance = fixture.verify()
        _, original = fixture._source(fixture.SUMMARY, fixture.STATE)
        source_summary, _ = fixture._json(fixture.SUMMARY, 128 * 1024)
        raw = fixture._read(fixture.ARCHIVE, fixture.MAX_ARCHIVE_BYTES)
        if fixture.common._sha256(raw) != provenance["source_archive_sha256"]:
            raise SmokeError("Original Beta archive changed")
        files = _archive_files(raw, source_summary["original_world_manifest"])
        java, java_version = server.resolve_java(args.java)
        artifact_hash = server.digest_file(artifact)
        _prepare(runtime, session, artifact, args.port, files)
        if (world_manifest(session / "world") != source_summary["original_world_manifest"]
                or server.digest_file(session / "mods" / artifact.name) != artifact_hash):
            raise SmokeError("Disposable copy does not exactly match its sources")
        # WorldHarness aborts startup failures; inherited FlightHarness.stop records and scans logs.
        harness = WorldHarness(java=java, server=session, port=args.port,
                               expected_version=EXPECTED_VERSION, startup_timeout=args.startup_timeout)
        saved = []
        for name in ("electrolyzer-beta-upgrade", "electrolyzer-beta-restart"):
            process = harness.start(name)
            process.command("forceload add 0 0")
            wait_condition(process, 'execute if data block 4 100 0 {arce_machine:{energy:12000,progress:0,'
                           'inventory:{Items:[{Slot:2,id:"advancedrocketrycommunity:hydrogen_canister",Count:4b},'
                           '{Slot:3,id:"advancedrocketrycommunity:oxygen_canister",Count:4b}]}},'
                           'arce_process:{state:"idle"}}', "ARCE_V120_BETA_ELECTROLYZER_COMPLETE", timeout=40)
            harness.stop(process)
            process = None
            compressed, machines = _saved(session)
            _assert_migrated(machines, original)
            saved.append((compressed, machines, server.digest_file(session / fixture.REGION)))
        if saved[0][1] != saved[1][1]:
            raise SmokeError("Migrated machine roots changed across the same-world idle restart")
        if server.digest_file(fixture.ARCHIVE) != provenance["source_archive_sha256"]:
            raise SmokeError("Original Beta archive changed during execution")
        evidence.mkdir(parents=True)
        for record in harness.process_documents:
            log = session / record["full_log_file"]
            if server.digest_file(log) != record["full_log_sha256"]:
                raise SmokeError("Completed process log changed before archival")
            shutil.copy2(log, evidence / log.name)
        snapshots = []
        for name, (compressed, machines, region_hash) in zip(("upgrade", "restart"), saved):
            chunk_name = f"{name}-c.0.0.nbt.zlib"
            (evidence / chunk_name).write_bytes(compressed)
            snapshots.append({"phase": name, "chunk": chunk_name,
                              "chunk_sha256": fixture.common._sha256(compressed),
                              "region_sha256": region_hash, "machines": machines})
        summary = {
            "schema_version": 1, "status": "PASS", "slice": "V120-MIG-03B3",
            "completed_at": datetime.now(timezone.utc).isoformat(), "java": java_version,
            "artifact_version": EXPECTED_VERSION, "artifact_sha256": artifact_hash,
            "tested_implementation_commit": None if args.tested_worktree else args.tested_commit,
            "implementation_base_commit": args.tested_commit, "uncommitted_worktree": args.tested_worktree,
            "port": args.port, "source": provenance, "original_archive_unchanged": True,
            "source_world_files_verified": len(files), "same_world_restarted": True,
            "scope": "two_electrolyzers_only_not_whole_world_acceptance",
            "snapshots": snapshots, "processes": harness.process_documents,
        }
        (evidence / "summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        (evidence / "filtered-lifecycle.txt").write_text("\n".join(harness.filtered_lines) + "\n", encoding="utf-8")
        print("[PASS] Real Beta world copy: paused progress and four-batch resources conserved")
        print("[PASS] Same-world restart retained exact machine/process/transaction roots")
        print(f"[PASS] Artifact SHA-256: {artifact_hash}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, SmokeError, fixture.FixtureError,
            zipfile.BadZipFile) as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    finally:
        if process is not None:
            process.abort()


if __name__ == "__main__":
    raise SystemExit(main())
