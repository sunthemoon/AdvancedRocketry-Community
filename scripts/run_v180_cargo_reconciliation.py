#!/usr/bin/env python3
"""Fluid cargo stop-table cuts: native force-kill, two restarts and independent raw Anvil checks."""
from __future__ import annotations

import argparse
import ctypes
import json
import os
from pathlib import Path
import re
import shutil
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER
from run_v180_escrow_pre_f2 import assert_pin, journal
from run_v180_rocket_settlement_completion import require_lock

CUTS = ("PREPARING", "L1_BEFORE", "L1_REPLACED", "L1_ACK", "C2_SOURCE_AFTER", "C2_DESTINATION_AFTER",
        "FIRST_STORE", "C6_JOINT_READBACK", "L2_REPLACED", "C8_RELEASED", "SOURCE_ONLY_STORE", "DESTINATION_ONLY_STORE")
CASES = tuple("FC_" + direction + "_" + cut for direction in ("LOAD", "UNLOAD") for cut in CUTS)


def inspect(world: Path, case: str, restarted: bool) -> dict:
    root = ledger(world / "data" / LEDGER)
    entities = record(world / "entities/r.0.0.mca")["Entities"]
    rockets = [entry for entry in entities if "RocketEntityData" in entry]
    devices = [entry for entry in record(world / "region/r.0.0.mca")["block_entities"]
               if [entry.get(k) for k in ("x", "y", "z")] == [64, 100, 64]]
    if len(rockets) != 1 or len(devices) != 1 or journal(world)["transfers"]:
        raise smoke.SmokeError("Missing/duplicated native fixture or unexpected transfer record")
    entity, device = rockets[0], devices[0]["arce_rocket_fluid_cargo"]
    rocket = entity["RocketEntityData"]
    snapshot, section = rocket["snapshot"], rocket["resource_escrow"]
    blocks = [entry["block_entity"] for entry in snapshot["relative_blocks"] if "block_entity" in entry]
    if len(blocks) != 1 or blocks[0]["adapter"] != "advancedrocketrycommunity:pressurized_tank_v1":
        raise smoke.SmokeError("Native cargo fixture changed its adapter or payload count")
    cargo = blocks[0]["data"]["tank"]["fluid"].get("Amount", 0)
    amount = device["tank"].get("Amount", 0)
    load = case.startswith("FC_LOAD_")
    cut = case[len("FC_LOAD_") if load else len("FC_UNLOAD_"):]
    before = cut in ("PREPARING", "L1_BEFORE")
    if (device["owner"] != rocket["owner_id"] or device["binding"]["logical_rocket"] != rocket["assembly_transaction_id"]
            or device["binding"]["physical_rocket"] != entity["UUID"] or entity["Pos"] != [68.5, 100.0, 64.5]
            or rocket["flight_data"]["passengers"]["assignments"] or root["floors"] or root["tombstones"]):
        raise smoke.SmokeError("Native identity, placement, occupancy or ordinary authority changed")
    epochs = [section["resource_epoch"], device["resource_escrow"]["resource_epoch"]]
    if any(epoch not in (0, 1) for epoch in epochs):
        raise smoke.SmokeError("Resource epoch repeated")
    if cargo != (epochs[0] * 1000 if load else 5000 - epochs[0] * 1000) or amount != (5000 - epochs[1] * 1000 if load else epochs[1] * 1000):
        raise smoke.SmokeError("Native balance does not match its independently saved epoch")
    if restarted:
        epoch = 0 if before else 1
        if (root["records"] or root["issued_through"] != epoch or epochs != [epoch, epoch] or cargo + amount != 5000
                or section["markers"] or device["resource_escrow"]["markers"]):
            raise smoke.SmokeError("Restart did not conserve and retire exactly once")
    elif before:
        if root["records"] or root["issued_through"] or epochs != [0, 0]:
            raise smoke.SmokeError("Pre-L1 preparation became durable")
    elif cut in ("L2_REPLACED", "C8_RELEASED"):
        if root["records"] or root["issued_through"] != 1 or epochs != [1, 1]:
            raise smoke.SmokeError("L2 cut did not retire both native AFTER frontiers")
    else:
        if len(root["records"]) != 1 or root["records"][0]["phase"] != "PREPARED":
            raise smoke.SmokeError("Cut lost its PREPARED record")
        if cut == "FIRST_STORE" and sorted(epochs) != [0, 1]:
            raise smoke.SmokeError("First-store cut did not produce a genuinely mixed native state")
        if cut == "C6_JOINT_READBACK" and epochs != [1, 1]:
            raise smoke.SmokeError("Joint readback cut did not store both AFTER states")
        if cut in ("SOURCE_ONLY_STORE", "DESTINATION_ONLY_STORE"):
            source = cut == "SOURCE_ONLY_STORE"
            if epochs != ([0, 1] if load == source else [1, 0]) or cargo + amount != (4000 if source else 6000):
                raise smoke.SmokeError("Selected one-sided store has the wrong epochs or raw balance")
            op = root["records"][0]
            if root["issued_through"] != 1 or op["seq"] != 1:
                raise smoke.SmokeError("Selected store did not retain exactly one issued transfer")
            for state, role in ((section, "DESTINATION" if load else "SOURCE"),
                                (device["resource_escrow"], "SOURCE" if load else "DESTINATION")):
                markers = state["markers"]
                if state["resource_epoch"] == 0:
                    if markers: raise smoke.SmokeError("Unstored endpoint retained an applied marker")
                elif (len(markers) != 1 or markers[0]["op_id"] != op["op_id"] or markers[0]["seq"] != 1
                      or markers[0]["role"] != role or markers[0]["epoch_after"] != 1):
                    raise smoke.SmokeError("Stored endpoint lost its exact operation marker")
        if cut not in ("FIRST_STORE", "C6_JOINT_READBACK", "SOURCE_ONLY_STORE", "DESTINATION_ONLY_STORE") and epochs != [0, 0]:
            raise smoke.SmokeError("An unsaved apply cut unexpectedly changed native stores")
    persisted_side = ("SOURCE" if (epochs == [0, 1]) == load else "DESTINATION") if sorted(epochs) == [0, 1] else "BOTH" if epochs == [1, 1] else "NEITHER"
    return dict(ledger=root, device=device, rocket=rocket, raw_total=cargo + amount, epochs=epochs,
                persisted_side=persisted_side, amounts=dict(rocket=cargo, device=amount),
                identity=dict(physical=entity["UUID"], logical=rocket["assembly_transaction_id"], owner=rocket["owner_id"],
                              snapshot=snapshot["snapshot_id"], device=device["device"]),
                files={name: digest(world / name) for name in ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")})


def preflight() -> dict:
    if os.name == "nt":
        class MemoryStatus(ctypes.Structure):
            _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong),
                        *( (name, ctypes.c_ulonglong) for name in ("total", "available", "total_page", "available_page",
                                                                  "total_virtual", "available_virtual", "extended") )]
        status = MemoryStatus(); status.length = ctypes.sizeof(status)
        if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
            raise smoke.SmokeError("Cannot read free physical memory")
        memory = status.available
        disks = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
    else:
        memory = int(re.search(r"^MemAvailable:\s+(\d+)", Path("/proc/meminfo").read_text(), re.M)[1]) * 1024
        disks = {"/": shutil.disk_usage("/").free}
    if min(disks.values()) < 10 * 1024**3 or memory < 6 * 1024**3:
        raise smoke.SmokeError("Native start refused: less than 10 GiB disk or 6 GiB free memory")
    return dict(free_memory_bytes=memory, free_disk_bytes=disks)


def run(args) -> None:
    assert_pin(args.source_commit); require_lock(args.lock_owner)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if session.exists() or evidence.exists() or session == evidence or session in evidence.parents or evidence in session.parents:
        raise smoke.SmokeError("Session/evidence already exists or overlaps")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*")):
        raise smoke.SmokeError("Missing or unsafe Forge template libraries")
    session.mkdir(parents=True); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex; sentinel = session / ".arce-cargo-owned"
    sentinel.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for case in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            row = dict(case=case, restarts=[]); report["in_progress"] = row
            for restart in range(3):
                assert_pin(args.source_commit); require_lock(args.lock_owner)
                row.setdefault("preflights", []).append(preflight())
                if digest(args.jar) != report["jar_sha256"]:
                    raise smoke.SmokeError("Native start refused: changed jar")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command[1:1] = ["-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                                "-Dadvancedrocketrycommunity.escrowCase=" + case]
                row.setdefault("commands", []).append(command)
                process = smoke.CapturedProcess(command, session, evidence / f"{case}-{restart}.log")
                process.wait_for(re.compile(("ARCE_RELEASE_CARGO_RESULT" if restart else "ARCE_RELEASE_CARGO_PAUSED") + " cut=" + case), args.startup_timeout)
                if restart:
                    process.command("stop"); code = process.finish()
                    if code: raise smoke.SmokeError(f"Native restart failed: {case}/{restart}, exit={code}")
                else:
                    process.process.kill(); process.abort()
                if any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                    raise smoke.SmokeError(f"Native run logged ERROR/FATAL: {case}/{restart}")
                process = None; disk = inspect(session / ("world-" + case), case, bool(restart))
                if restart:
                    if disk["identity"] != row["cut_disk"]["identity"]: raise smoke.SmokeError("Restart changed native identities")
                    row["restarts"].append(disk)
                else: row["cut_disk"] = disk
            row["status"] = "PASS"; report["cases"].append(row); report.pop("in_progress", None)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(f"{case}: PASS (force-kill, two restarts)", flush=True)
        report["status"] = "PASS"
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None: process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        # Only this newly created, token-owned disposable copy; never the template or source worlds.
        if session.resolve() == session and sentinel.is_file() and sentinel.read_text(encoding="ascii") == token:
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"): parser.add_argument("--" + key, type=Path, required=True)
    for key in ("source-commit", "java", "lock-owner"): parser.add_argument("--" + key, required=True)
    parser.add_argument("--case", action="append", choices=CASES); parser.add_argument("--startup-timeout", type=float, default=240)
    try: run(parser.parse_args()); return 0
    except (OSError, smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__": raise SystemExit(main())
