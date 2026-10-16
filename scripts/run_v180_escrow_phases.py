#!/usr/bin/env python3
"""Force-stop F5 journal/effect boundaries and recover each cut twice without completing ARMED."""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import re
import shutil
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER
from run_v180_escrow_pre_f2 import assert_pin, journal
from run_v180_escrow_destination import require_state as require_f4
from run_v180_rocket_settlement_completion import require_lock, slot_present, JOURNAL

CASES = ("FP_PASSENGERS_ACK", "FP_SOURCE_ACK", "FP_SOURCE_REMOVED", "FP_COMMITTED", "FP_ABSENT_SOURCE")
PHASES = dict(zip(CASES, ("PASSENGERS_TRANSFERRED", "SOURCE_REMOVED", "SOURCE_REMOVED", "COMMITTED", "COMMITTED")))


def require_state(root: dict, transfer: dict, source: dict | None, destination: dict | None, cut: str, restarted: bool) -> dict:
    expected = "COMMITTED" if restarted else PHASES[cut]
    if (transfer["phase"] != expected or transfer["schema_version"] != 3 or len(root["armed_flights"]) != 1
            or root["issued_launch_serial"] != 1 or root["records"] or root["floors"] or root["tombstones"] or root["rocket_authorities"]):
        raise smoke.SmokeError("F5 lost its phase or released its still-unproved arm")
    arm = root["armed_flights"][0]
    if (arm != transfer["native_transfer"]["armed"] or arm["outcome"] != "ARMED" or arm["serial"] != 1 or arm["epoch"] != 1
            or arm["src"] != transfer["source_entity_id"] or arm["d"] != transfer["destination_entity_id"] or arm["t"] != transfer["transfer_id"]):
        raise smoke.SmokeError("F5 changed fixed physical authority")
    if restarted or cut == "FP_ABSENT_SOURCE":
        if source is not None:
            raise smoke.SmokeError("F5 retained or reconstructed an authorized-removed source")
    elif source is None:
        raise smoke.SmokeError("F5 cut lost the durable source before its absence store")
    if not restarted and destination is not None:
        raise smoke.SmokeError("F5 cut unexpectedly saved its destination")
    if restarted and destination is None:
        raise smoke.SmokeError("F5 restart did not persist its fixed destination")
    if source is not None:
        # Before removal, all existing F4 identity/resource/manifest/progression checks still apply.
        publication = copy.deepcopy(transfer); publication["phase"] = "DESTINATION_SPAWNED"
        require_f4(root, publication, source, None, False)
    if destination is not None:
        data = destination["RocketEntityData"]
        if (destination["UUID"] != arm["d"] or data["assembly_transaction_id"] != arm["l"] or data["owner_id"] != transfer["owner_id"]
                or data["snapshot"] != transfer["destination_snapshot"] or data["flight_data"] != transfer["destination_flight"]
                or data["flight_data"]["fuel"]["amount"] != 500
                or data["flight_data"]["fuel"]["committed_debits"] != [dict(transaction_id=arm["t"])]
                or data["resource_escrow"] != dict(schema_version=1, ledger_id=root["ledger_id"], resource_epoch=0, markers=[])
                or destination["Pos"] != [80.5, 148.0, 64.5] or destination["Motion"] != [0.0, 0.0, 0.0] or destination.get("Passengers", [])):
            raise smoke.SmokeError("F5 changed destination credit, wrapper, manifest or native position")
    invariant = copy.deepcopy(transfer)
    invariant.pop("phase"); invariant.pop("checksum", None)
    return dict(ledger=root, transfer=invariant)


def inspect(world: Path, cut: str, restarted: bool) -> dict:
    root = ledger(world / "data" / LEDGER); transfers = journal(world)["transfers"]
    if len(transfers) != 1:
        raise smoke.SmokeError("F5 lost or duplicated its journal record")
    path = world / "entities/r.0.0.mca"
    sources = [entry for entry in record(path)["Entities"] if "RocketEntityData" in entry] if slot_present(path, 132) else []
    destinations = [entry for entry in record(path, 133)["Entities"] if "RocketEntityData" in entry] if slot_present(path, 133) else []
    if len(sources) > 1 or len(destinations) > 1:
        raise smoke.SmokeError("F5 duplicated a physical candidate")
    invariant = require_state(root, transfers[0], sources[0] if sources else None, destinations[0] if destinations else None, cut, restarted)
    return dict(invariant=invariant, sources=sources, destinations=destinations,
                files={name: digest(world / name) for name in ("data/" + LEDGER, "data/" + JOURNAL, "entities/r.0.0.mca")})


def run(args, *, cases=CASES, inspect_state=inspect,
        pause_marker="ARCE_RELEASE_PHASE_PAUSED cut={cut}",
        result_marker="ARCE_RELEASE_PHASE_RESULT cut={cut} phase=COMMITTED") -> dict:
    assert_pin(args.source_commit)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if (session.exists() or session.is_symlink() or evidence.exists() or session == evidence
            or session in evidence.parents or evidence in session.parents):
        raise smoke.SmokeError("Disposable session or evidence target exists or overlaps")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*")):
        raise smoke.SmokeError("Missing or unsafe Forge template libraries")
    require_lock(args.lock_owner); session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; (session / ".arce-phases-owned").write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar),
                  session=str(session), ownership_token=token, cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for cut in args.case or cases:
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
                    process.wait_for(re.compile(pause_marker.format(cut=re.escape(cut))), args.startup_timeout)
                    process.process.kill(); process.abort(); process = None
                    row["cut_disk"] = inspect_state(session / ("world-" + cut), cut, False)
                else:
                    process.wait_for(re.compile(result_marker.format(cut=re.escape(cut))), args.startup_timeout)
                    process.command("stop"); code = process.finish()
                    if code or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None; disk = inspect_state(session / ("world-" + cut), cut, True)
                    if disk["invariant"] != row["cut_disk"]["invariant"]:
                        raise smoke.SmokeError("F5 restart changed frozen authority, resources or assignment")
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
