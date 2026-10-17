#!/usr/bin/env python3
"""W95 (W11b3 B3-E): a real loader's first fill through the gated switch, a forced stop and two restarts.

The fresh flat world starts NO_FILE. The gated loader's fair-queue visit creates the ledger; the server is killed at
the named cut after the durable L1. Each restart logs the START of its first tick (delegate state, records and
reservations, before anything can bind lazily) and lets production reconciliation finish exactly one transfer.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER
from run_v180_escrow_pre_f2 import assert_pin, journal
from run_v180_rocket_settlement_completion import require_lock

CASES = ("LF_C2_SOURCE_BEFORE", "LF_C2_SOURCE_AFTER")
LOADER = "advancedrocketrycommunity:fluid_fuel_loader"
FUEL = "advancedrocketrycommunity:rocket_fuel"
DEVICE = [66, 100, 64]
FIRST_TICK = re.compile(r"ARCE_RELEASE_LOADER_FIRST_TICK cut=(\S+) recovery=(true|false) tick=(\d+) delegate=(\w+) "
                        r"records=(\d+) pending=(\d+) generation=(-?\d+)")


def first_tick(lines: list[str], cut: str, restarted: bool, records: int) -> dict:
    found = [match for match in (FIRST_TICK.search(line) for line in lines) if match]
    if len(found) != 1:
        raise smoke.SmokeError("Missing or repeated first-tick inspection")
    match = found[0]
    expected = ("LOADED", str(records), str(records)) if restarted else ("NO_FILE", "0", "0")
    if match.group(1) != cut or match.group(2) != str(restarted).lower() or (match.group(4), match.group(5), match.group(6)) != expected:
        raise smoke.SmokeError(f"Wrong first-tick state: {match.group(0)}")
    return dict(tick=int(match.group(3)), delegate=match.group(4), records=int(match.group(5)),
                pending=int(match.group(6)), generation=int(match.group(7)))


def require_state(root: dict | None, device: dict, rocket: dict, restarted: bool) -> dict:
    loader = device["arce_fluid_fuel_loader"]
    tank, section = loader["tank"], loader["resource_escrow"]
    data = rocket["RocketEntityData"]
    flight, wrapper = data["flight_data"], data["resource_escrow"]
    primary = flight["fuel"]["propulsion"]["primary"]
    if (rocket["id"] != "advancedrocketrycommunity:rocket" or rocket["Pos"] != [64.5, 100.0, 64.5]
            or flight["passengers"]["assignments"] or rocket.get("Passengers") or "active_transfer_id" in flight
            or data["assembly_transaction_id"] != flight["logical_rocket_id"] or data["owner_id"] != loader["owner"]):
        raise smoke.SmokeError("The fill changed the rocket's identity, owner or flight authority")
    if loader["binding"]["logical_rocket"] != data["assembly_transaction_id"] or loader["binding"]["physical_rocket"] != rocket["UUID"]:
        raise smoke.SmokeError("The loader's binding changed")
    if root is None:
        raise smoke.SmokeError("No ledger after the first admitted fill")
    if root["floors"] or root["tombstones"] or root["armed_flights"] or root["rocket_authorities"] or root["issued_launch_serial"]:
        raise smoke.SmokeError("An ordinary fill created settlement or flight authority")
    if restarted:
        # Exactly one transfer: debited once, credited once, both markers pruned, both epochs advanced once.
        if (root["records"] or root["issued_through"] != 1 or tank != {} or primary["amount"] != 1000
                or section != {"schema_version": 1, "ledger_id": root["ledger_id"], "resource_epoch": 1, "markers": []}
                or wrapper.get("ledger_id") != root["ledger_id"] or wrapper["resource_epoch"] != 1 or wrapper["markers"]):
            raise smoke.SmokeError("Restart did not complete exactly one conserved transfer")
    else:
        # The forced stop follows the durable L1; no fixture save followed admission, so both stores hold BEFORE.
        if (len(root["records"]) != 1 or root["issued_through"] != 1 or root["writer_generation"] != 2
                or tank != {"FluidName": FUEL, "Amount": 1000} or primary["amount"] != 0
                or section != {"schema_version": 1, "resource_epoch": 0, "markers": []}
                or "ledger_id" in wrapper or wrapper["resource_epoch"] != 0 or wrapper["markers"]):
            raise smoke.SmokeError("The forced stop did not leave a durable L1 over both BEFORE stores")
        op = root["records"][0]
        if (op["source"]["identity"] != loader["device"] or op["source"]["type"] != LOADER
                or op["destination"]["identity"] != rocket["UUID"] or op["payload"] != {"FluidName": FUEL, "Amount": 1000}
                or op["native_binding"]["complete"] != 1 or op["native_binding"]["kind"] != "FUEL_VECTOR_V1"):
            raise smoke.SmokeError("The L1 record is not the loader's complete native fill")
    return dict(ledger=root["ledger_id"], device=loader["device"], owner=loader["owner"], physical=rocket["UUID"],
                logical=data["assembly_transaction_id"])


def inspect(world: Path, restarted: bool) -> dict:
    path = world / "data" / LEDGER
    root = ledger(path) if path.is_file() else None
    terrain = record(world / "region/r.0.0.mca")
    entities = record(world / "entities/r.0.0.mca")["Entities"]
    rockets = [entity for entity in entities if "RocketEntityData" in entity]
    if len(rockets) != 1 or journal(world)["transfers"]:
        raise smoke.SmokeError("The native rocket is missing or duplicated, or a fill created a transfer")
    devices = [entry for entry in terrain["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == DEVICE]
    if len(devices) != 1 or devices[0]["id"] != LOADER:
        raise smoke.SmokeError("The loader is missing, duplicated or retyped")
    identity = require_state(root, devices[0], rockets[0], restarted)
    return dict(ledger=root, loader=devices[0]["arce_fluid_fuel_loader"], rocket=rockets[0]["RocketEntityData"], identity=identity,
                files={name: digest(world / name) for name in ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def run(args) -> dict:
    assert_pin(args.source_commit)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if (session.exists() or session.is_symlink() or evidence.exists() or session == evidence
            or session in evidence.parents or evidence in session.parents):
        raise smoke.SmokeError("Disposable session/evidence already exists or overlaps")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*")):
        raise smoke.SmokeError("Missing or unsafe Forge template libraries")
    require_lock(args.lock_owner)
    session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    (session / ".arce-fluid-loader-owned").write_text(token, encoding="ascii")
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
                if restart == 0:
                    # The gated switch opens admission for the first start only; recovery needs no switch.
                    command.insert(1, "-Dadvancedrocketrycommunity.escrowLoaderAdmission=true")
                row.setdefault("starts", []).append(dict(restart=restart, command=command, free_bytes=free, lock_owner=args.lock_owner))
                process = smoke.CapturedProcess(command, session, evidence / f"{cut}-{restart}.log")
                if restart == 0:
                    process.wait_for(re.compile(r"ARCE_RELEASE_LOADER_PAUSED cut=" + cut), args.startup_timeout)
                    process.process.kill(); process.abort()
                    if any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError("Forced-stop start logged ERROR/FATAL")
                    row["first_tick"] = [first_tick(process.lines, cut, False, 0)]
                    process = None; row["cut_disk"] = inspect(session / ("world-" + cut), False)
                else:
                    process.wait_for(re.compile(r"ARCE_RELEASE_LOADER_RESULT cut=" + cut), args.startup_timeout)
                    process.command("stop"); code = process.finish()
                    if code or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    row["first_tick"].append(first_tick(process.lines, cut, True, 1 if restart == 1 else 0))
                    lines = process.lines; process = None
                    disk = inspect(session / ("world-" + cut), True)
                    if disk["identity"] != row["cut_disk"]["identity"]:
                        raise smoke.SmokeError("Restart changed the independently observed native identities")
                    row["restarts"].append(dict(restart=restart, exit_code=code, disk=disk,
                                                result=[line for line in lines if "ARCE_RELEASE_LOADER_RESULT" in line]))
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
