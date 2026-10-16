#!/usr/bin/env python3
"""Force-stop native source phases with older Level metadata; resume each twice."""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER
from run_v180_escrow_pre_f2 import assert_pin, journal, require_governed_record
from run_v180_rocket_settlement_completion import require_lock, slot_present, JOURNAL
from inspect_celestial_saved_data import NbtReader

CASES = ("SC_COUNTDOWN", "SC_ASCENT", "SC_TRANSIT")


def require_source(root: dict, transfer: dict, source: dict, phase: str) -> dict:
    arms = root["armed_flights"]
    data = source["RocketEntityData"]
    flight, wrapper = data["flight_data"], data["resource_escrow"]
    if (len(arms) != 1 or root["issued_launch_serial"] != 1 or root["records"] or root["floors"]
            or root["tombstones"] or root["rocket_authorities"]):
        raise smoke.SmokeError("Source phase changed the ledger authority")
    arm = arms[0]
    require_governed_record(transfer, arm)
    if (arm["outcome"] != "ARMED" or arm["serial"] != 1 or arm["src"] != source["UUID"]
            or arm["l"] != data["assembly_transaction_id"] or arm["t"] != transfer["transfer_id"]
            or arm["d"] == source["UUID"] or arm["epoch"] != 1):
        raise smoke.SmokeError("Source identity, serial or phase authority changed")
    if (wrapper != {"schema_version": 1, "ledger_id": root["ledger_id"], "resource_epoch": 1, "markers": []}
            or flight["fuel"] != transfer["source_flight"]["fuel"] or flight["fuel"]["amount"] != 1000
            or flight["fuel"]["committed_debits"] or flight["passengers"] != transfer["source_flight"]["passengers"]
            or len(flight["passengers"]["assignments"]) != 1 or flight["state"] != phase
            or data["snapshot"] != transfer["source_snapshot"]):
        raise smoke.SmokeError("Source phase changed its frozen resources, wrapper, snapshot or manifest")
    if ((phase == "TRANSIT" and flight.get("active_transfer_id") != transfer["transfer_id"])
            or (phase != "TRANSIT" and "active_transfer_id" in flight)):
        raise smoke.SmokeError("Source phase has the wrong active transfer")
    x, y, z = source["Pos"]
    if (x != 64.5 or z != 64.5 or not 100 <= y <= 148
            or phase == "COUNTDOWN" and y != 100 or phase == "TRANSIT" and y != 148):
        raise smoke.SmokeError("Source left its permitted ascent column")
    return dict(physical=source["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"],
                snapshot=data["snapshot"], fuel=flight["fuel"], passengers=flight["passengers"], wrapper=wrapper)


def inspect(world: Path, cut: str, restarted: bool) -> dict:
    root = ledger(world / "data" / LEDGER)
    transfers = journal(world)["transfers"]
    if len(transfers) != 1:
        raise smoke.SmokeError("Source cut lost or duplicated its checked record")
    entities = record(world / "entities/r.0.0.mca")["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry]
    if len(rockets) != 1:
        raise smoke.SmokeError("Source cut lost or duplicated its native rocket")
    source = rockets[0]
    phase = "TRANSIT" if restarted else cut.removeprefix("SC_")
    invariant = require_source(root, transfers[0], source, phase)
    path = world / "entities/r.0.0.mca"
    if slot_present(path, 133) and any("RocketEntityData" in value for value in record(path, 133)["Entities"]):
        raise smoke.SmokeError("Source-only cut published a destination")
    with gzip.open(world / "level.dat", "rb") as stream:
        level_time = NbtReader(stream.read(16 * 1024 * 1024 + 1)).read_root()["Data"]["Time"]
    started = source["RocketEntityData"]["flight_data"]["state_started_game_time"]
    if not restarted and level_time >= started:
        raise smoke.SmokeError("Cut did not retain older Level metadata")
    return dict(invariant=invariant, source=source, ledger=root, journal=transfers, level_time=level_time,
                files={name: digest(world / name) for name in ("data/" + LEDGER, "data/" + JOURNAL,
                                                               "entities/r.0.0.mca", "level.dat")})


def run(args) -> dict:
    assert_pin(args.source_commit)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if (session.exists() or session.is_symlink() or evidence.exists() or session == evidence
            or session in evidence.parents or evidence in session.parents):
        raise smoke.SmokeError("Disposable session or evidence target exists or overlaps")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*")):
        raise smoke.SmokeError("Missing or unsafe Forge template libraries")
    require_lock(args.lock_owner)
    session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    (session / ".arce-source-clock-owned").write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar),
                  session=str(session), ownership_token=token, cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for cut in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + cut, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            row = dict(cut=cut, restarts=[]); report["in_progress"] = row
            for restart in range(3):
                assert_pin(args.source_commit); require_lock(args.lock_owner)
                free = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
                if min(free.values()) < 10 * 1024**3 or digest(args.jar) != report["jar_sha256"]:
                    raise smoke.SmokeError("Native start refused: low disk space or changed jar")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command.insert(1, "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"))
                command.insert(1, "-Dadvancedrocketrycommunity.escrowCase=" + cut)
                row.setdefault("starts", []).append(dict(restart=restart, command=command, free_bytes=free, lock_owner=args.lock_owner))
                process = smoke.CapturedProcess(command, session, evidence / f"{cut}-{restart}.log")
                if restart == 0:
                    process.wait_for(re.compile(r"ARCE_RELEASE_SOURCE_CLOCK_PAUSED cut=" + cut), args.startup_timeout)
                    process.process.kill(); process.abort(); process = None
                    row["cut_disk"] = inspect(session / ("world-" + cut), cut, False)
                else:
                    process.wait_for(re.compile(r"ARCE_RELEASE_SOURCE_CLOCK_RESULT cut=" + cut + r" state=DESTINATION_DUE"), args.startup_timeout)
                    process.command("stop"); code = process.finish()
                    if code or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None
                    disk = inspect(session / ("world-" + cut), cut, True)
                    if disk["invariant"] != row["cut_disk"]["invariant"]:
                        raise smoke.SmokeError("Restart changed frozen source resources or identity")
                    row["restarts"].append(dict(restart=restart, exit_code=code, disk=disk))
            row["status"] = "PASS"; report["cases"].append(row); report.pop("in_progress", None)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(f"{cut}: PASS (forced stop, two restarts)", flush=True)
        assert_pin(args.source_commit); report["status"] = "PASS"; return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None: process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True); parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try: run(args); return 0
    except (OSError, smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
