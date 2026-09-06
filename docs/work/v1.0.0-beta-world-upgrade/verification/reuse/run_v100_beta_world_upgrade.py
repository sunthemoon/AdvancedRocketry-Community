#!/usr/bin/env python3
"""Capture an immutable real Beta world, then upgrade a copy and restart twice."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import socket
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from . import inspect_v100_world_upgrade as inspect
    from . import run_dedicated_server_smoke as server
    from . import run_v060_flight_server_smoke as flight
    from . import v100_beta_world_fixture as fixture
    from .run_v090_migration_server_smoke import _copy_server
    from .run_v100_flight_forced_stop import verify_local_session
else:
    import inspect_v100_world_upgrade as inspect
    import run_dedicated_server_smoke as server
    import run_v060_flight_server_smoke as flight
    import v100_beta_world_fixture as fixture
    from run_v090_migration_server_smoke import _copy_server
    from run_v100_flight_forced_stop import verify_local_session


BETA = "1.20.1-0.9.0-beta.1"
BETA_SHA256 = "fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad"


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")


def check_paths(source: Path, work: Path, evidence: Path) -> tuple[Path, Path, Path]:
    for path in (source, work, evidence):
        if any(server.is_link_or_junction(parent) for parent in (path, *path.parents)):
            raise server.SmokeError("World upgrade paths must not traverse links or junctions")
    roots = tuple(path.resolve() for path in (source, work, evidence))
    if any(a == b or a.is_relative_to(b) or b.is_relative_to(a)
           for index, a in enumerate(roots) for b in roots[index + 1:]):
        raise server.SmokeError("Source, disposable work and evidence must not overlap")
    if work.exists() or evidence.exists():
        raise server.SmokeError("Refusing to overwrite prior upgrade work/evidence")
    return roots


def require_stopped_port(port: int) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        try:
            probe.bind(("127.0.0.1", port))
        except OSError as error:
            raise server.SmokeError("Fixture port is already in use; do not copy or start a live server") from error


def validate_fixture(state: dict, setup: dict) -> dict:
    observed = state["observed"]
    inspect.validate_material_authority(observed)
    paused = inspect.machine_ledger(observed["paused"], running=False)
    running = inspect.machine_ledger(observed["running"], running=True)
    if observed.get("running_lit") != 1 or not 0 < running["progress"] < 100:
        raise server.SmokeError("Beta fixture must contain an actually running mid-recipe machine")
    if observed.get("vent_lit") != 1:
        raise server.SmokeError("Beta fixture must contain a supplied sealed room")
    vent = inspect.vent_ledger(observed["vent"])
    if vent["supply_ticks"] != vent["oxygen_accounted_ticks"]:
        raise server.SmokeError("Initial vent supply ledger is not conserved")
    rockets = inspect.indexed(observed["rockets"], "UUID")
    reports = setup["rockets"]
    if set(rockets) != {report["entity"] for report in reports.values()}:
        raise server.SmokeError("Captured Beta world does not contain exactly both assembled rockets")
    if (reports["earth"]["state"], reports["earth"]["fuel"], reports["moon"]["state"], reports["moon"]["fuel"]) != (
            "FUELED", 1000, "LANDED", 628):
        raise server.SmokeError("Fixture Earth/Moon rockets did not retain exact fuel and landing state")
    for report in reports.values():
        raw = rockets[report["entity"]]
        data = inspect.rocket_projection(raw)["RocketEntityData"]
        if data["snapshot"]["content_hash"] != report["snapshot"] or raw["observed_dimension"] != report["dimension"]:
            raise server.SmokeError("Native rocket NBT disagrees with the runtime report")
        blocks = data["snapshot"]["relative_blocks"]
        payloads = [block["block_entity"] for block in blocks if "block_entity" in block]
        if len(blocks) != 5 or payloads != [{"adapter": "advancedrocketrycommunity:vanilla_container_v1", "data": {"Items": [
                {"Slot": 0, "id": "minecraft:diamond", "Count": 17}, {"Slot": 26, "id": "minecraft:iron_ingot", "Count": 64}]}}]:
            raise server.SmokeError("Fixture rocket does not retain the complete expected inventory")
    stations = state["stations"]["stations"]
    if (len(stations) != 1 or inspect.nbt_uuid(stations[0]["owner_id"]) != fixture.SUCCESSOR
            or [inspect.nbt_uuid(member["id"]) for member in stations[0]["members"]] != [fixture.OWNER]):
        raise server.SmokeError("Beta fixture does not preserve the transferred station/member ACL")
    statuses = sorted(mission["status"] for mission in state["satellite_missions"]["missions"])
    if statuses != ["active", "claimed", "ready"]:
        raise server.SmokeError("Beta fixture must contain active, ready and claimed missions")
    if not state["satellite_missions"]["research_accounts"] or setup["claim"]["research"] <= 0:
        raise server.SmokeError("Beta fixture has no earned research")
    if state["rocket_transactions"]["transactions"]:
        raise server.SmokeError("Ordinary Beta upgrade fixture has pending block transactions")
    return {"paused": paused, "running": running, "vent": vent, "rocket_ids": sorted(rockets)}


def archive_world(world: Path, evidence: Path, inventory: list[dict]) -> dict:
    path = evidence / "original-beta-world.zip"
    with zipfile.ZipFile(path, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for record in inventory:
            archive.write(world / record["file"], "world/" + record["file"])
    if inspect.manifest(world) != inventory:
        raise server.SmokeError("Original Beta world changed during archival")
    return {"file": path.name, "bytes": path.stat().st_size, "sha256": server.digest_file(path)}


def retain_fixture_archive(summary_path: Path, retained: dict, evidence: Path) -> None:
    if retained.get("status") not in ("PASS", "CAPTURED") or retained.get("artifact_sha256") != BETA_SHA256:
        raise server.SmokeError("Reusable fixture must have a completed accepted-Beta capture")
    record = retained["original_world_archive"]
    if record.get("file") != "original-beta-world.zip":
        raise server.SmokeError("Reusable fixture archive name is not canonical")
    archive = summary_path.parent / record["file"]
    if (any(server.is_link_or_junction(path) for path in (archive, *archive.parents))
            or not archive.is_file() or not 0 < archive.stat().st_size <= inspect.MAX_WORLD_BYTES
            or archive.stat().st_size != record.get("bytes") or server.digest_file(archive) != record.get("sha256")):
        raise server.SmokeError("Reusable fixture archive differs from its size/hash contract")
    target = evidence / record["file"]
    if target.exists():
        raise server.SmokeError("Refusing to overwrite retained fixture archive")
    shutil.copy2(archive, target)
    if server.digest_file(target) != record["sha256"]:
        raise server.SmokeError("Retained archive copy changed")


def make_harness(args, session: Path, port: int, version: str):
    return fixture.WorldHarness(java=args.java, server=session, port=port, expected_version=version,
                                startup_timeout=args.startup_timeout)


def capture(args, source: Path, work: Path, evidence: Path, port: int, summary: dict) -> tuple[Path, dict]:
    session = work / "beta-fixture-server"
    _copy_server(source, session)
    fixture.install_capture_pack(session)
    harness = make_harness(args, session, port, BETA)
    process = harness.start("beta-capture")
    try:
        setup = fixture.setup_world(harness, process)
        summary["setup"] = setup
        fixture.observe_world(harness, process, setup)
        harness.capture_and_stop(process)
    except BaseException:
        process.abort()
        raise
    finally:
        summary["processes"].extend(harness.process_documents)
    state = inspect.read_world(session / "world")
    write_json(evidence / "beta-state.json", state)
    summary["initial_ledger"] = validate_fixture(state, setup)
    inventory = inspect.manifest(session / "world")
    summary["original_world_manifest"] = inventory
    summary["original_world_archive"] = archive_world(session / "world", evidence, inventory)
    print("[PASS] Actual Beta machine/room/rockets/member ACL/mission/research fixture captured", flush=True)
    return session, state


def upgrade(args, source: Path, work: Path, evidence: Path, port: int, summary: dict, before: dict) -> None:
    original = inspect.manifest(source / "world")
    if original != summary["original_world_manifest"]:
        raise server.SmokeError("Original fixture manifest changed before candidate copy")
    session = work / "candidate-server"
    _copy_server(source, session)
    beta_jar = session / "mods" / f"advancedrocketry-community-{BETA}.jar"
    if server.digest_file(beta_jar) != BETA_SHA256:
        raise server.SmokeError("Copied Beta artifact no longer matches accepted release")
    beta_jar.unlink()  # Only the verified file inside this newly owned server copy.
    installed = session / "mods" / args.artifact.name
    shutil.copy2(args.artifact, installed)
    if server.digest_file(installed) != summary["candidate_sha256"]:
        raise server.SmokeError("Candidate artifact copy changed")
    harness = make_harness(args, session, port, args.expected_version)
    try:
        previous = before
        for name in ("upgrade-first-open", "upgrade-restart-1", "upgrade-restart-2"):
            process = harness.start(name)
            try:
                if name == "upgrade-restart-2":
                    fixture.satellite._wait_until_ready(process, 2, 1)
                replay = fixture.satellite._claim(process, summary["setup"]["missions"][0], "ALREADY_CLAIMED")
                observation = fixture.observe_world(harness, process, summary["setup"])
                observation["claimed_mission_replay"] = replay
                harness.capture_and_stop(process)
            except BaseException:
                process.abort()
                raise
            state = inspect.read_world(session / "world")
            write_json(evidence / f"{name}-state.json", state)
            ledger = inspect.compare_worlds(previous, state)
            if inspect.manifest(source / "world") != original:
                raise server.SmokeError("Original fixture changed during candidate execution")
            summary["upgrades"].append({"name": name, "observation": observation, "ledger": ledger})
            previous = state
            print(f"[PASS] {name}: complete resources/identity/state comparison", flush=True)
    finally:
        summary["processes"].extend(harness.process_documents)
        summary["original_unchanged"] = inspect.manifest(source / "world") == original


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server_dir", type=Path)
    for name in ("baseline-summary", "work-dir", "evidence-dir", "artifact"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--base-commit", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.0.0-dev")
    parser.add_argument("--capture-only", action="store_true")
    parser.add_argument("--fixture-summary", type=Path)
    home = os.environ.get("JAVA_HOME")
    parser.add_argument("--java", default=str(Path(home) / "bin/java") if home else "java")
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    evidence = None
    summary = {"schema_version": 1, "version": "v1.0.0", "status": "IN_PROGRESS", "processes": [], "upgrades": [],
               "mod_version": BETA, "artifact": f"advancedrocketry-community-{BETA}.jar", "artifact_sha256": BETA_SHA256,
               "scope": "actual unmodified Beta world, first candidate open and two restarts; unmanned, not V1/V2 or final release",
               "base_commit": args.base_commit, "candidate_version": args.expected_version, "source_state": "development_worktree"}
    try:
        source, work, output = check_paths(args.source_server_dir, args.work_dir, args.evidence_dir)
        if re.fullmatch(r"[0-9a-f]{40}", args.base_commit) is None:
            raise server.SmokeError("Base commit must be a full lowercase SHA-1")
        baseline = flight._load_summary(args.baseline_summary)
        port, old_hash = flight._verify_inputs(source, baseline, BETA)
        properties = verify_local_session(source, port)
        if old_hash != BETA_SHA256 or properties.get("level-name", "world") != "world":
            raise server.SmokeError("Source must be the accepted Beta artifact with the default world")
        if sorted(path.name for path in (source / "mods").iterdir()) != [summary["artifact"]]:
            raise server.SmokeError("This fixture requires the accepted Beta alone, with no extra mods")
        require_stopped_port(port)
        if (server.is_link_or_junction(args.artifact) or not args.artifact.is_file()
                or not 0 < args.artifact.stat().st_size <= 32 * 1024**2
                or args.artifact.name != f"advancedrocketry-community-{args.expected_version}.jar"):
            raise server.SmokeError("Candidate JAR has an unsafe path, invalid filename or size")
        args.java, java_version = server.resolve_java(args.java)
        work.mkdir(parents=True, exist_ok=False)
        output.mkdir(parents=True, exist_ok=False)
        evidence = output
        summary.update(server_port=port, java=java_version, online_mode=properties.get("online-mode"),
                       candidate_sha256=server.digest_file(args.artifact),
                       harness_inputs={path.name: server.digest_file(path) for path in (
                           Path(__file__), Path(fixture.__file__), Path(inspect.__file__))})
        for module in (Path(__file__), Path(fixture.__file__), Path(inspect.__file__)):
            shutil.copy2(module, evidence / module.name)
        if args.fixture_summary:
            retained = flight._load_summary(args.fixture_summary)
            for key in ("setup", "original_world_manifest", "original_world_archive", "initial_ledger"):
                summary[key] = retained[key]
            if inspect.manifest(source / "world") != summary["original_world_manifest"]:
                raise server.SmokeError("Retained original fixture differs from its manifest")
            retain_fixture_archive(args.fixture_summary, retained, evidence)
            summary["source_fixture_summary_sha256"] = server.digest_file(args.fixture_summary)
            before = inspect.read_world(source / "world")
            write_json(evidence / "beta-state.json", before)
            validate_fixture(before, summary["setup"])
            fixture_server = source
        else:
            fixture_server, before = capture(args, source, work, evidence, port, summary)
        summary["fixture_server"] = str(fixture_server)
        if not args.capture_only:
            upgrade(args, fixture_server, work, evidence, port, summary, before)
        summary["status"] = "CAPTURED" if args.capture_only else "PASS"
        return 0
    except (OSError, ValueError, KeyError, TypeError, server.SmokeError) as error:
        summary.update(status="FAIL", error=str(error))
        print(f"[FAIL] {error}", file=sys.stderr, flush=True)
        return 1
    finally:
        if evidence:
            summary["completed_at"] = datetime.now(timezone.utc).isoformat()
            summary["logs"] = []
            for path in sorted(args.work_dir.glob("*/v100-upgrade-*-full.txt")):
                if server.is_link_or_junction(path) or path.stat().st_size > 16 * 1024**2:
                    raise server.SmokeError("Upgrade log is unsafe or exceeds 16 MiB")
                shutil.copy2(path, evidence / path.name)
                summary["logs"].append({"file": path.name, "sha256": server.digest_file(path)})
            write_json(evidence / "summary.json", summary)


if __name__ == "__main__":
    sys.exit(main())
