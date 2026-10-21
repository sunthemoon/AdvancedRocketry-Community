"""R52 selected process-stop cuts, two restarts and isolated real-action follow-ups per checkpoint.

Action copies do not feed changes back into the main restart world. This is not power-loss testing.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import time
import uuid

import loader_cut_nbt as nbt
import loader_cut_oracle as base
import run_dedicated_server_smoke as smoke
import run_v180_loader_cuts as common
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import LEDGER, digest, ledger, record
from run_v180_escrow_pre_f2 import assert_pin, journal
from run_v180_rocket_settlement_completion import require_lock

CUTS = ("CAPTURE", "STORE", "REMOVE", "MOVED", "UNKNOWN", "ACK", "STALE", "TICKET", "HELD_COLUMN", "HELD_STORAGE", "K2", "K9")
PREFIX = "LF_M_R52_"


def source_entities(directory: Path) -> list:
    path = directory / "r.128.128.mca"
    # Vanilla can open a new, still zero-length region while querying an empty entity slot.
    if not path.exists() or path.stat().st_size == 0:
        return []
    base.require(path.stat().st_size >= 8192, "Truncated nonempty source entity region")
    with path.open("rb") as stream:
        location = stream.read(4)
    if location == b"\0\0\0\0":
        return []
    raw, _ = nbt.chunk(directory, 4096, 4096)
    return nbt.plain(raw).get("Entities", [])


def inspect(world: Path) -> dict:
    devices = [e for e in record(world / "region/r.0.0.mca")["block_entities"] if e.get("id") == base.LOADER]
    rockets = [e for e in record(world / "entities/r.0.0.mca")["Entities"] if e.get("id") == base.ROCKET]
    base.require(len(devices) == len(rockets) == 1, "Missing or duplicated destination endpoint")
    source_dir = world / "dimensions/advancedrocketrycommunity/moon/entities"
    path = world / "data" / LEDGER
    return dict(rocket=rockets[0], loader=devices[0], ledger=ledger(path) if path.exists() else None,
                transfers=journal(world)["transfers"], source=source_entities(source_dir))


def check(disk: dict, cut: str, restart: int) -> dict:
    data = disk["rocket"]["RocketEntityData"]; device = disk["loader"]["arce_fluid_fuel_loader"]
    root = disk["ledger"]; flight = data["flight_data"]
    held = cut.startswith("HELD_")
    completed = bool(restart) and not held or cut == "K9"
    after_removal = bool(restart) and not held or cut in ("MOVED", "UNKNOWN", "ACK", "K2", "K9")
    base.require(len(disk["transfers"]) == int(not after_removal), "Unexpected transfer authority")
    if disk["transfers"]:
        transfer = disk["transfers"][0]
        base.require(transfer["phase"] == "COMMITTED" and not transfer.get("native_transfer")
                     and transfer["logical_rocket_id"] == data["assembly_transaction_id"]
                     and transfer["destination_entity_id"] == disk["rocket"]["UUID"], "Retained T does not name the ordinary destination")
    if after_removal:
        base.require(not any(e.get("id") == base.ROCKET for e in disk["source"]), "Source origin still contains a rocket after removal")
    base.require(disk["rocket"]["Pos"] == [64.5, 100., 64.5] and flight["state"] == "LANDED"
                 and not flight.get("active_transfer_id") and not flight["passengers"]["assignments"], "Destination authority changed")
    base.require(flight["fuel"]["propulsion"]["primary"]["amount"] == 1000 * completed
                 and flight["fuel"]["propulsion"]["oxidizer"]["amount"] == 0
                 and device["tank"] == {"FluidName": base.FUEL, "Amount": 5000 - 1000 * completed}, "Fuel not conserved")
    base.require(device["owner"] == data["owner_id"] and device["binding"]["physical_rocket"] == disk["rocket"]["UUID"]
                 and device["binding"]["logical_rocket"] == data["assembly_transaction_id"], "Owner/binding changed")
    base.require(bool(root) == (bool(restart) and not held or cut in ("K2", "K9")), "Unexpected ledger creation")
    if root:
        base.require(root["issued_through"] == 1 and len(root["records"]) == int(not restart and cut == "K2")
                     and not any(root[k] for k in ("floors", "tombstones", "armed_flights", "rocket_authorities", "issued_launch_serial")),
                     "Fill retained unexpected records or flight/floor authority")
        for operation in root["records"]:
            base.require(not operation["transfer_guard"].get("transfer_id"), "First fill carried a named guard")
    markers = int(not restart and cut == "K9")
    base.check_section(data["resource_escrow"], int(completed), markers, root, "DESTINATION", 1)
    base.check_section(device["resource_escrow"], int(completed), markers, root, "SOURCE", 1)
    return dict(physical=disk["rocket"]["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"], device=device["device"])


def start(args, session: Path, evidence: Path, owner: str, cut: str, restart: int, action="CHECKPOINT") -> dict:
    assert_pin(args.source_commit); require_lock(owner)
    row = dict(restart=restart, action=action, free_bytes=common.disk_space(), free_physical_memory=common.physical_memory())
    command = _server_command(args.java); command[2] = "-Xmx4G"
    command[1:1] = ["-Dadvancedrocketrycommunity.escrowCase=" + PREFIX + cut,
                     "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                     "-Dadvancedrocketrycommunity.retirementFollowUp=" + action]
    row["command"] = command
    process = smoke.CapturedProcess(command, session, evidence)
    try:
        process.wait_for(re.compile(r"ARCE_R52_PAUSED cut=" + re.escape(PREFIX + cut) + r" boundary=\S+"), args.startup_timeout)
        process.process.kill(); process.abort()
        row["observations"] = [line for line in process.lines if "ARCE_R52_" in line]
        row["errors"] = [line for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
        expected = "ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority; dependent effects are blocked"
        injected = cut == "UNKNOWN" and restart == 0
        base.require((injected and len(row["errors"]) == 1 and row["errors"][0].rstrip().endswith(expected))
                     or (not injected and not row["errors"]), "Native process logged an unexpected ERROR/FATAL")
        text = "\n".join(row["observations"])
        base.require("tickets=0 pending=0" in text, "Missing first-tick RAM-ticket/pending observation")
        if cut.startswith("HELD_"):
            base.require("tickets=3 ordinary_mutation=true" in text, "Missing bounded held retirement observation")
            if action == "REFILL":
                base.require("code=TRANSFER_JOURNAL_UNPROVED conserved=true" in text, "Held retirement admitted fuel")
            if action == "LAUNCH":
                base.require("code=INVALID_STATE" in text, "Missing actual empty-rocket launch refusal")
        elif restart and action == "CHECKPOINT":
            base.require("tank=4000 bank=1000 floors=0 transfer=NONE" in text, "Missing conservation result")
        elif action == "REFILL":
            base.require("code=PREPARING conserved=true" in text, "Refuelling was not restored")
        elif action == "LAUNCH":
            base.require("state=COUNTDOWN governed=true" in text, "Governed launch did not reach countdown")
        row["status"] = "PASS"
    except Exception as failure:
        row.update(status="FAIL", error=str(failure), observations=[line for line in process.lines if "ARCE_R52_" in line])
    finally:
        process.abort()
    return row


def run_cut(args, cut, evidence, owner):
    session = args.session_parent.resolve() / ("W130-" + cut + "-" + uuid.uuid4().hex)
    token = uuid.uuid4().hex
    row = dict(cut=cut, status="RUNNING", source=args.source_commit, jar_sha256=digest(args.jar), starts=[], actions=[])
    session.mkdir(); (session / ".w97-owned").write_text(token, encoding="ascii"); evidence.mkdir()
    def remove_child(name):
        target = session / name
        base.require(target.resolve().parent == session.resolve() and not target.is_symlink()
                     and (session / ".w97-owned").read_text(encoding="ascii") == token, "Unsafe action-copy cleanup")
        shutil.rmtree(target)
    try:
        libraries = args.source_server / "libraries"
        base.require(libraries.is_dir() and not any(p.is_symlink() for p in libraries.rglob("*")), "Unsafe server template")
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        properties = smoke.expected_server_properties(port, True)
        properties.update({"level-name": "world", "level-type": "minecraft:flat", "max-tick-time": "60000",
                           "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        identity = None
        for restart in range(3):
            observed = start(args, session, evidence / f"start-{restart}.log", owner, cut, restart)
            row["starts"].append(observed)
            base.require(observed["status"] == "PASS", observed.get("error", "Start failed"))
            observed["disk"] = inspect(session / "world")
            found = check(observed["disk"], cut, restart)
            base.require(identity is None or found == identity, "Restart changed identities"); identity = found
            observed["resources_conserved"] = True
            if restart:
                checkpoint = session / "restart-checkpoint"
                shutil.copytree(session / "world", checkpoint)
                # Run actions on independent copies of this saved restart, never on the world continued by restart 2.
                try:
                    for action in ("REFILL", "LAUNCH"):
                        result = start(args, session, evidence / f"start-{restart}-{action.lower()}.log", owner, cut, restart, action)
                        row["actions"].append(result)
                        remove_child("world"); shutil.copytree(checkpoint, session / "world")
                finally:
                    remove_child("restart-checkpoint")
            (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        row["status"] = "PASS" if all(r["status"] == "PASS" for r in row["actions"]) else "FAIL"
    except Exception as failure:
        row.update(status="FAIL", error=str(failure))
    finally:
        common.cleanup(session, token, args.session_parent)
        row["cleanup"] = not session.exists()
        (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
    return row


def run(args):
    assert_pin(args.source_commit)
    args.evidence_dir.mkdir(parents=True, exist_ok=False)
    report = dict(source=args.source_commit, jar_sha256=digest(args.jar), cases=[], limitation="Process stops, not power loss; action forks are not restart continuations.")
    for cut in args.case or CUTS:
        owner = f"W130 {cut} pid={os.getpid()} {datetime.now(timezone.utc).isoformat()}"
        with common.heavy_lock(owner):
            report["cases"].append(run_cut(args, cut, args.evidence_dir / cut, owner))
        (args.evidence_dir / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(cut + ": " + report["cases"][-1]["status"], flush=True); time.sleep(2)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("source-server", "jar", "session-parent", "evidence-dir"):
        parser.add_argument("--" + key, required=True, type=Path)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CUTS); parser.add_argument("--startup-timeout", type=float, default=240)
    result = run(parser.parse_args())
    raise SystemExit(0 if all(row["status"] == "PASS" for row in result["cases"]) else 1)
