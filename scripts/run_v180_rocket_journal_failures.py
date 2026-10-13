#!/usr/bin/env python3
"""Keep failed launch/phase journal writes fenced across a hard stop and two native restarts."""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import struct
import uuid

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import FlightHarness, EARTH, LAUNCH_LOG, _server_command
from run_v180_escrow_forced_stop import digest, record, LEDGER

CASES = ("ADMISSION", "ADVANCEMENT")
JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"
FAILURE = "ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority; dependent effects are blocked"


def validate_authority(rockets: list[dict], journal: dict, mode: str, baseline: dict | None) -> dict:
    if len(rockets) != 1:
        raise smoke.SmokeError("Failed journal write lost or duplicated the rocket")
    rocket = rockets[0]
    wrapper = rocket["RocketEntityData"]
    if wrapper["schema_version"] != 3 or wrapper["flight_data"]["fuel"]["amount"] != 1000:
        raise smoke.SmokeError("Failed journal write changed source fuel/schema")
    if wrapper["resource_escrow"] != {"schema_version": 1, "resource_epoch": 0, "markers": []}:
        raise smoke.SmokeError("Journal failure created escrow authority")
    entries = journal.get("transfers", [])
    if mode == "ADMISSION":
        if entries or wrapper["flight_data"]["state"] != "FUELED":
            raise smoke.SmokeError("Unacknowledged admission advanced authority")
    elif mode == "ADVANCEMENT":
        if len(entries) != 1 or entries[0]["phase"] != "PREPARED" or "destination_entity_id" in entries[0]:
            raise smoke.SmokeError("Failed advancement changed the durable journal phase")
        if entries[0]["source_entity_id"] != rocket["UUID"]:
            raise smoke.SmokeError("Retained journal changed source identity")
    else:
        raise smoke.SmokeError("Unknown native failure mode")
    authority = dict(identity=rocket["UUID"], snapshot=wrapper["snapshot"],
                     logical=wrapper["assembly_transaction_id"], owner=wrapper["owner_id"],
                     fuel=wrapper["flight_data"]["fuel"], escrow=wrapper["resource_escrow"])
    if baseline is not None and authority != baseline:
        raise smoke.SmokeError("Restart changed conserved source authority")
    return authority


def inspect(world: Path, mode: str, baseline: dict | None) -> dict:
    if (world / "data" / LEDGER).exists():
        raise smoke.SmokeError("Journal-only fixture created an escrow ledger")
    rockets = []
    files = {}
    for region in sorted(world.rglob("r.*.mca")):
        if region.parent.name != "entities":
            continue
        with region.open("rb") as stream:
            slots = [i for i, location in enumerate(struct.unpack(">1024I", stream.read(4096))) if location]
        for slot in slots:
            rockets.extend(entity for entity in record(region, slot).get("Entities", []) if "RocketEntityData" in entity)
        files[region.relative_to(world).as_posix()] = digest(region)
    journal_path = world / "data" / JOURNAL
    journal = {}
    if journal_path.exists():
        if journal_path.stat().st_size > 146_866_176:
            raise smoke.SmokeError("Oversized journal fixture")
        with gzip.open(journal_path, "rb") as stream:
            raw = stream.read(146_866_177)
        if len(raw) > 146_866_176:
            raise smoke.SmokeError("Oversized decoded journal fixture")
        journal = NbtReader(raw).read_root()["data"]
        files["data/" + JOURNAL] = digest(journal_path)
    authority = validate_authority(rockets, journal, mode, baseline)
    return dict(authority=authority, journal=journal, state=rockets[0]["RocketEntityData"]["flight_data"]["state"], files=files)


def check_log(lines: list[str]) -> None:
    errors = [line for line in lines if "/ERROR]" in line or "/FATAL]" in line]
    if len(errors) != 1 or FAILURE not in errors[0]:
        raise smoke.SmokeError(f"Expected one journal failure streak, observed {errors}")


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the disposable directory")
    for drive in (Path("C:/"), intended):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise smoke.SmokeError("Native journal cuts require 10 GiB on both drives")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or smoke.is_link_or_junction(libraries) or any(
            smoke.is_link_or_junction(p) for p in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe or missing Forge libraries")
    if not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file():
        raise smoke.SmokeError("Missing pinned Forge arguments")
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w11b2-journal-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  scope="Real legacy-fuel rocket; definite failed journal committers, no credited reconciliation or settlement")
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        harness = FlightHarness(java=args.java, server=session, port=port,
                                expected_version="1.20.1-1.8.0-dev", startup_timeout=args.startup_timeout)
        for mode in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + mode, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                                 "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
            row = dict(mode=mode, restarts=[]); report["in_progress"] = row
            baseline = None
            entity = None
            for restart in range(3):
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command.insert(1, "-Dadvancedrocketrycommunity.rocketJournalFailure=" + mode)
                process = smoke.CapturedProcess(command, session, evidence / f"{mode}-restart-{restart}.log")
                row.setdefault("commands", []).append(command)
                process.wait_for(smoke.READY_MARKER, args.startup_timeout)
                if restart == 0:
                    entity = harness.assemble(process)["entity"]
                    harness.refuel(process, EARTH, entity)
                if restart == 0 or mode == "ADMISSION":
                    start = len(process.lines)
                    process.command(f"arce rocket release-test launch {entity} moon")
                    index = process.wait_for(LAUNCH_LOG, 45, start_at=start)
                    launch = LAUNCH_LOG.search(process.lines[index])
                    if launch.group(7) != "JOURNAL_PENDING":
                        raise smoke.SmokeError("Native launch did not await its journal write")
                refused = re.compile(r"ARCE_RELEASE_JOURNAL_COMMIT_REFUSED mode=" + mode)
                index = 0
                for _ in range(3):
                    index = process.wait_for(refused, 90, start_at=index) + 1
                live = harness.report(process, EARTH, entity)
                if live["fuel"] != 1000 or live["passengers"] != 0:
                    raise smoke.SmokeError("Failed committer changed live source resources")
                if mode == "ADMISSION" and live["state"] != "FUELED":
                    raise smoke.SmokeError("Failed admission started countdown")
                save_start = len(process.lines); process.command("save-all flush")
                process.wait_for(smoke.SAVE_MARKER, 60, start_at=save_start)
                check_log(process.lines)
                if restart == 0:
                    process.process.kill(); process.abort(); code = None
                else:
                    process.command("stop"); code = process.finish()
                    if code != 0:
                        raise smoke.SmokeError(f"Native journal restart exited {code}")
                    check_log(process.lines)
                process = None
                disk = inspect(session / ("world-" + mode), mode, baseline)
                if restart and disk["journal"] != row["restarts"][0]["disk"]["journal"]:
                    raise smoke.SmokeError("Restart changed the retained checked journal snapshot")
                if baseline is None: baseline = disk["authority"]
                row["restarts"].append(dict(restart=restart, hard_stop=restart == 0, exit_code=code, live=live, disk=disk))
            row["status"] = "PASS"; report["cases"].append(row)
            print(f"{mode}: PASS (three failures per start, forced stop and two restarts)", flush=True)
        report.pop("in_progress", None); report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None: process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if (session.parent == intended and not smoke.is_link_or_junction(session) and marker.is_file()
                and not smoke.is_link_or_junction(marker) and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try:
        run(args); return 0
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
