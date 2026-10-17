#!/usr/bin/env python3
"""Pin native F1 replacement cuts and checked pre-F2 cancellation across two restarts."""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER
from run_v180_rocket_settlement_completion import require_lock, JOURNAL
from inspect_celestial_saved_data import NbtReader

CASES = ("PF_ARM_BEFORE", "PF_ARM_REPLACED", "PF_ARM_ACK", "PF_T_ACK", "PF_GOVERNED_T_ACK",
         "PF_LIVE_F2_ACK", "PF_LIVE_COUNTDOWN")


def journal(world: Path) -> dict:
    """These pre-landing cuts require W44's root 3 and its still-empty outbox."""
    with gzip.open(world / "data" / JOURNAL, "rb") as stream:
        payload = stream.read(16 * 1024 * 1024 + 1)
    if len(payload) > 16 * 1024 * 1024:
        raise smoke.SmokeError("Checked pre-F2 journal exceeds its bound")
    data = NbtReader(payload).read_root()["data"]
    if (data.get("schema_version") != 3 or not isinstance(data.get("transfers"), list)
            or data.get("classic_first_moon_landing") != {"schema_version": 1, "state": "EMPTY"}):
        raise smoke.SmokeError("Wrong checked pre-F2 journal root or nonempty landing outbox")
    return data


def has_transfer(cut: str) -> bool:
    return cut == "PF_T_ACK" or governed_transfer(cut)


def governed_transfer(cut: str) -> bool:
    return cut in ("PF_GOVERNED_T_ACK", "PF_LIVE_F2_ACK", "PF_LIVE_COUNTDOWN")


def require_governed_record(transfer: dict, arm: dict) -> None:
    native = transfer.get("native_transfer", {})
    if (transfer.get("schema_version") != 3 or native.get("schema_version") != 1
            or native.get("armed") != arm or native.get("signature_kind") != "FUEL_VECTOR_V1"
            or transfer.get("phase") != "PREPARED" or "destination_entity_id" in transfer
            or transfer.get("source_flight", {}).get("schema_version") != 2
            or transfer.get("destination_flight", {}).get("schema_version") != 2):
        raise smoke.SmokeError("Governed pre-F2 fixture changed its record versions or exact authority")


def crewed_cancellation(cut: str, crewed: bool) -> bool:
    return crewed and cut in ("PF_ARM_REPLACED", "PF_ARM_ACK")


def recovery_outcome(cut: str, crewed: bool) -> str:
    if crewed_cancellation(cut, crewed):
        return "CANCELLED_CLEANED"
    return "UNARMED" if cut == "PF_ARM_BEFORE" else "ARMED" if has_transfer(cut) else "CANCELLED"


def require_unchanged_source(disk: dict, before: dict) -> None:
    if disk["source"] != before["source"]:
        raise smoke.SmokeError("Restart changed checkpointed E")


def inspect(world: Path, cut: str, restarted: bool, crewed: bool = False) -> dict:
    root = ledger(world / "data" / LEDGER)
    transfers = journal(world)["transfers"]
    entities = record(world / "entities/r.0.0.mca")["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry]
    if len(rockets) != 1:
        raise smoke.SmokeError("Native F1 source missing or duplicated")
    source = rockets[0]
    data = source["RocketEntityData"]
    wrapper, flight = data["resource_escrow"], data["flight_data"]
    before, has_t = cut == "PF_ARM_BEFORE", has_transfer(cut)
    cleaned = restarted and crewed_cancellation(cut, crewed)
    expected = "CANCELLED" if restarted and not has_t else "ARMED"
    arms = root["armed_flights"]
    if (root["records"] or root["floors"] or root["tombstones"] or root["rocket_authorities"]
            or root["issued_launch_serial"] != int(not before) or len(arms) != int(not before and not cleaned)
            or len(transfers) != int(has_t)):
        raise smoke.SmokeError("Wrong F1 ledger or checked journal boundary")
    if arms and (arms[0]["outcome"] != expected or arms[0]["serial"] != 1 or arms[0]["src"] != source["UUID"]
                 or arms[0]["l"] != data["assembly_transaction_id"] or arms[0]["epoch"] != 1
                 or arms[0]["d"] == source["UUID"]):
        raise smoke.SmokeError("Wrong exact cancellation outcome or serial/identity")
    if (wrapper.get("ledger_id") != root["ledger_id"] or wrapper["resource_epoch"] != 1 or wrapper["markers"]
            or flight["fuel"]["amount"] != 1000 or flight["fuel"]["committed_debits"]
            or len(flight["passengers"]["assignments"]) != 1 or "active_transfer_id" in flight
            or source["Pos"] != [64.5, 100., 64.5]):
        raise smoke.SmokeError("F1 lost or changed its native source frontier")
    if cleaned and (flight.get("state") != "FUELED" or "plan" in flight):
        raise smoke.SmokeError("Crewed pre-F2 source is not idle after cleanup")
    if transfers and transfers[0]["transfer_id"] != arms[0]["t"]:
        raise smoke.SmokeError("Negative case lost T")
    if governed_transfer(cut):
        require_governed_record(transfers[0], arms[0])
    # Excludes unrelated vanilla timers; includes every persisted field of the fixed source adapter.
    projection = {key: source.get(key) for key in ("UUID", "id", "RocketEntityData", "Pos", "Motion",
                                                  "Rotation", "NoGravity", "OnGround", "Invulnerable")}
    return dict(ledger=root, journal=transfers, source=projection,
                files={name: digest(world / name) for name in ("data/" + LEDGER, "data/" + JOURNAL, "entities/r.0.0.mca")})


def assert_pin(source: str) -> None:
    repo = Path(__file__).resolve().parents[1]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    status = subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True)
    if head != source or status:
        raise smoke.SmokeError("Native run requires the unchanged committed worktree")


def run(args) -> dict:
    assert_pin(args.source_commit)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if session.exists() or session.is_symlink() or evidence.exists() or session == evidence or session in evidence.parents:
        raise smoke.SmokeError("Disposable session or evidence target already exists or overlaps")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*")):
        raise smoke.SmokeError("Missing or unsafe Forge template libraries")
    require_lock(args.lock_owner)
    session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    (session / ".arce-pre-f2-owned").write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar),
                  session=str(session), ownership_token=token, crewed=args.crewed, cases=[])
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
                if digest(args.jar) != report["jar_sha256"]:
                    raise smoke.SmokeError("Native jar changed")
                free = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
                if min(free.values()) < 10 * 1024**3:
                    raise smoke.SmokeError("Native start refused below 10 GiB free")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command.insert(1, "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"))
                command.insert(1, "-Dadvancedrocketrycommunity.escrowCase=" + cut)
                if args.crewed:
                    command.insert(1, "-Dadvancedrocketrycommunity.escrowCrewed=true")
                    if crewed_cancellation(cut, True):
                        command.insert(1, "-Dadvancedrocketrycommunity.escrowPreF2Restart=" + str(restart))
                row.setdefault("starts", []).append(dict(restart=restart, command=command, free_bytes=free, lock_owner=args.lock_owner))
                process = smoke.CapturedProcess(command, session, evidence / f"{cut}-{restart}.log")
                if restart == 0:
                    process.wait_for(re.compile(r"ARCE_RELEASE_PRE_F2_PAUSED cut=" + cut), args.startup_timeout)
                    if args.crewed and not any("ARCE_RELEASE_CREWED_F1 cut=" + cut in line and "mounted=true" in line for line in process.lines):
                        raise smoke.SmokeError("Native cut lacks its facility-owned mounted F1 passenger")
                    process.process.kill(); process.abort(); process = None
                    row["cut_disk"] = inspect(session / ("world-" + cut), cut, False, args.crewed)
                else:
                    outcome = recovery_outcome(cut, args.crewed)
                    process.wait_for(re.compile(r"ARCE_RELEASE_PRE_F2_RESULT cut=" + cut + " outcome=" + outcome + r"\s"), args.startup_timeout)
                    if crewed_cancellation(cut, args.crewed) and restart == 1:
                        cancellations = [line for line in process.lines if "ARCE_RELEASE_PRE_F2_CANCELLED cut=" + cut + " " in line]
                        if len(cancellations) != 1 or "serial=1 issued=1 outcome=CANCELLED digest=" not in cancellations[0]:
                            raise smoke.SmokeError("Crewed first recovery lacks its exact acknowledged CANCELLED outcome")
                    process.command("stop"); code = process.finish()
                    if code or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                        raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    process = None
                    disk = inspect(session / ("world-" + cut), cut, True, args.crewed)
                    require_unchanged_source(disk, row["cut_disk"])
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
        # The caller validates this exact owned directory and removes it with the task's plain rm -rf.


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--crewed", action="store_true", help="Mount a facility-owned assigned passenger before F1")
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args()
    try: run(args); return 0
    except (OSError, smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
