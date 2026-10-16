#!/usr/bin/env python3
"""Force-stop ordinary native C0-C8 and check both subsequent restarts without fixture autosaves."""
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

CASES = ("CR_C0", "CR_L1_BEFORE", "CR_L1_REPLACED", "CR_L1_ACK",
         "CR_C2_SOURCE_BEFORE", "CR_C2_SOURCE_AFTER", "CR_C2_DESTINATION_BEFORE", "CR_C2_DESTINATION_AFTER",
         "CR_C3_FROZEN") + tuple(f"CR_{endpoint}_{stage}_{side}"
                                for endpoint in ("SOURCE", "DESTINATION")
                                for stage in ("STORE", "FORCE", "READBACK") for side in ("BEFORE", "AFTER")) + (
         "CR_C6_JOINT_READBACK", "CR_L2_BEFORE", "CR_L2_REPLACED", "CR_C7_ACKNOWLEDGED",
         "CR_C8_PRUNE_BEFORE", "CR_C8_PRUNE_AFTER", "CR_C8_RELEASED", "CR_LATER_WAIT", "CR_STALE_BIND", "CR_C2_MISMATCH",
         "CR_LATER_L2_REPLACED")
L2 = {"CR_L2_REPLACED", "CR_C7_ACKNOWLEDGED", "CR_C8_PRUNE_BEFORE", "CR_C8_PRUNE_AFTER", "CR_C8_RELEASED", "CR_STALE_BIND"}


def require_state(root: dict, sources: list[dict], rocket: dict, cut: str, restarted: bool) -> dict:
    before = cut in ("CR_C0", "CR_L1_BEFORE")
    repair = cut == "CR_C2_MISMATCH"
    chain = cut in ("CR_LATER_WAIT", "CR_STALE_BIND", "CR_LATER_L2_REPLACED")
    data = rocket["RocketEntityData"]
    flight, wrapper = data["flight_data"], data["resource_escrow"]
    fuel = flight["fuel"]["amount"]
    amounts = [source["arce_escrow_fixture"]["amount"] for source in sources]
    if (len(sources) != (2 if chain else 1) or root["floors"] or root["tombstones"] or root["armed_flights"]
            or root["rocket_authorities"] or root["issued_launch_serial"] or flight["fuel"]["committed_debits"]
            or flight["passengers"]["assignments"] or rocket.get("Passengers") or "active_transfer_id" in flight
            or data["assembly_transaction_id"] != flight["logical_rocket_id"]
            or rocket["id"] != "advancedrocketrycommunity:rocket" or rocket["Pos"] != [64.5, 100., 64.5]):
        raise smoke.SmokeError("Ordinary reconciliation changed native identity/flight authority")
    expected_watermark = 0 if before else 2 if cut in ("CR_LATER_WAIT", "CR_LATER_L2_REPLACED") or restarted and cut == "CR_STALE_BIND" else 1
    expected_records = 1 if repair or cut == "CR_LATER_L2_REPLACED" and not restarted else 0 if restarted or before or cut in L2 else expected_watermark
    if root["issued_through"] != expected_watermark or len(root["records"]) != expected_records:
        raise smoke.SmokeError("Wrong native C1/C7 ledger boundary")
    if cut == "CR_LATER_L2_REPLACED" and not restarted:
        markers = wrapper["markers"]
        if (len(markers) != 2 or [marker["seq"] for marker in markers] != [1, 2]
                or markers[1]["op_id"] != root["records"][0]["op_id"]):
            raise smoke.SmokeError("Partial L2 cut did not retain [A, B] against the surviving B record")
    if repair and any(op["phase"] != "REPAIR" or op["reason"] != "OTHER_STATE" for op in root["records"]):
        raise smoke.SmokeError("C2 mismatch lost sticky REPAIR")
    if before or repair:
        if amounts != [5000] or fuel != 0 or wrapper["resource_epoch"] != 0 or wrapper["markers"]:
            raise smoke.SmokeError("Unacknowledged or refused C2 changed resources")
    elif restarted or cut in L2 or cut == "CR_LATER_L2_REPLACED":
        applied = 2 if restarted and chain or cut == "CR_LATER_L2_REPLACED" else 1
        expected_amounts = [4000] + ([4000 if applied == 2 else 5000] if chain else [])
        if amounts != expected_amounts or fuel != applied * 1000 or wrapper["resource_epoch"] != applied:
            raise smoke.SmokeError("Retired native prefix changed conservation/epochs")
        if sum(amounts) + fuel != 5000 * len(sources):
            raise smoke.SmokeError("Native resource conservation failed")
        if restarted:
            if wrapper["markers"]:
                raise smoke.SmokeError("Restart retained retired rocket markers")
            for index, source in enumerate(sources):
                markers = source["resource_escrow"]["markers"]
                retained = index == 0 and (cut in L2 or cut == "CR_LATER_L2_REPLACED")
                if retained:
                    if (len(markers) != 1 or markers[0]["seq"] != 1 or markers[0]["role"] != "SOURCE"
                            or markers[0]["epoch_after"] != source["resource_escrow"]["resource_epoch"]):
                        raise smoke.SmokeError("Restart did not retain the untouched fixture source marker")
                elif markers:
                    raise smoke.SmokeError("The exact C8 callback did not prune its source marker")
    else:
        # Independent endpoint stores may contain different exact prefixes while durable L1 holds.
        # Their only legal values are the recorded BEFORE/AFTER; both restarts must converge conservatively.
        if any(amount not in (4000, 5000) for amount in amounts) or fuel not in range(0, expected_watermark * 1000 + 1, 1000):
            raise smoke.SmokeError("Unretired native endpoint is not a recorded prefix")
    if wrapper["resource_epoch"] != fuel // 1000 or (fuel and wrapper.get("ledger_id") != root["ledger_id"]):
        raise smoke.SmokeError("Rocket resource epoch or ledger changed")
    for source, amount in zip(sources, amounts):
        section = source["resource_escrow"]
        if section["ledger_id"] != root["ledger_id"] or section["resource_epoch"] != (5000 - amount) // 1000:
            raise smoke.SmokeError("Source resource epoch or ledger changed")
    return dict(ledger=root["ledger_id"], physical=rocket["UUID"], logical=data["assembly_transaction_id"],
                owner=data["owner_id"], snapshot=data["snapshot"], sources=[source["arce_escrow_fixture"]["identity"] for source in sources])


def inspect(world: Path, cut: str, restarted: bool) -> dict:
    root = ledger(world / "data" / LEDGER)
    terrain = record(world / "region/r.0.0.mca")
    entities = record(world / "entities/r.0.0.mca")["Entities"]
    rockets = [entity for entity in entities if "RocketEntityData" in entity]
    if len(rockets) != 1 or journal(world)["transfers"]:
        raise smoke.SmokeError("Native rocket is missing/duplicated or an ordinary fill created a transfer")
    sources = []
    for x in (64, 68) if cut in ("CR_LATER_WAIT", "CR_STALE_BIND", "CR_LATER_L2_REPLACED") else (64,):
        chosen = [entry for entry in terrain["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == [x, 100, 64]]
        if len(chosen) != 1 or chosen[0]["id"] != "minecraft:chest":
            raise smoke.SmokeError("Fixture source missing/duplicated/retyped")
        sources.append(chosen[0]["ForgeData"])
    identity = require_state(root, sources, rockets[0], cut, restarted)
    return dict(ledger=root, sources=sources, rocket=rockets[0], identity=identity,
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
    (session / ".arce-reconciliation-owned").write_text(token, encoding="ascii")
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
                    process.wait_for(re.compile(r"ARCE_RELEASE_RECONCILIATION_PAUSED cut=" + cut), args.startup_timeout)
                    process.process.kill(); process.abort()
                    if any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError("Forced-stop start logged ERROR/FATAL")
                    process = None; row["cut_disk"] = inspect(session / ("world-" + cut), cut, False)
                else:
                    process.wait_for(re.compile(r"ARCE_RELEASE_RECONCILIATION_RESULT cut=" + cut), args.startup_timeout)
                    process.command("stop"); code = process.finish()
                    if code or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None
                    disk = inspect(session / ("world-" + cut), cut, True)
                    if disk["identity"] != row["cut_disk"]["identity"]:
                        raise smoke.SmokeError("Restart changed the independently observed native identities")
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
