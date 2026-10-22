"""Save a governed ASSEMBLED/LANDED-source countdown and resume it in two new native processes.

Call run_cut under the machine lock from a detached runner. No persisted world is edited.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path
import re
import shutil
import uuid

import launch_trace_observations as trace
import run_v180_retire_before_fill as retirement

common, base, smoke = retirement.common, retirement.base, retirement.smoke
CUTS = ("ASSEMBLED", "LANDED")
PREFIX = "LF_M_W133F_"


def check(disk: dict, cut: str) -> dict:
    data = disk["rocket"]["RocketEntityData"]
    device = disk["loader"]["arce_fluid_fuel_loader"]
    flight, root = data["flight_data"], disk["ledger"]
    base.require(len(disk["transfers"]) == 1, "Countdown lost/duplicated its journal")
    transfer = disk["transfers"][0]
    native = transfer["native_transfer"]
    frozen = copy.deepcopy(transfer["source_flight"])
    frozen.update(native["source"]["flight_changes"])
    for key in native["source"]["flight_absent"]:
        del frozen[key]
    base.require(transfer["phase"] == "PREPARED" and frozen["state"] == cut
                 and not frozen.get("plan") and not frozen.get("active_transfer_id"), "Frozen source changed")
    base.require(transfer["source_entity_id"] == disk["rocket"]["UUID"]
                 and transfer["logical_rocket_id"] == data["assembly_transaction_id"], "Wrong governed source identity")
    base.require(disk["rocket"]["Pos"] == [64.5, 100., 64.5] and flight["state"] == "COUNTDOWN"
                 and not flight.get("active_transfer_id") and not flight["passengers"]["assignments"], "Source moved before countdown save")
    base.require(flight["fuel"] == frozen["fuel"] and flight["fuel"]["propulsion"]["primary"]["amount"] == 1000
                 and flight["fuel"]["propulsion"]["oxidizer"]["amount"] == 0
                 and device["tank"] == {"FluidName": base.FUEL, "Amount": 4000}, "Countdown lost/duplicated fuel")
    base.require(device["owner"] == data["owner_id"] and device["binding"]["physical_rocket"] == disk["rocket"]["UUID"]
                 and device["binding"]["logical_rocket"] == data["assembly_transaction_id"], "Loader ownership/binding changed")
    base.require(root["issued_through"] == 1 and not any(root[k] for k in ("records", "floors", "tombstones"))
                 and len(root["armed_flights"]) == 1, "Unexpected fill/flight authority")
    base.require(root["armed_flights"][0] == native["armed"], "Journal and escrow launch authority differ")
    base.check_section(data["resource_escrow"], 1, 0, root, "DESTINATION", 1)
    base.check_section(device["resource_escrow"], 1, 0, root, "SOURCE", 1)
    base.require(not any(e.get("id") == base.ROCKET for e in disk["source"]), "Retired old origin contains a rocket")
    # Compare complete frozen/active flights, authority and endpoint identities on both restarts.
    return dict(physical=disk["rocket"]["UUID"], logical=data["assembly_transaction_id"], owner=data["owner_id"],
                device=device["device"], flight=flight, transfer=transfer, armed=root["armed_flights"])


def run_cut(args, cut: str, evidence: Path, owner: str) -> dict:
    retirement.assert_pin(args.source_commit); retirement.require_lock(owner)
    session = args.session_parent.resolve() / ("W133F-" + cut + "-" + uuid.uuid4().hex)
    token = uuid.uuid4().hex
    row = dict(cut=cut, source=args.source_commit, jar_sha256=retirement.digest(args.jar), starts=[], status="RUNNING")
    session.mkdir(); (session / ".w97-owned").write_text(token, encoding="ascii"); evidence.mkdir()
    process = None
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
            retirement.assert_pin(args.source_commit); retirement.require_lock(owner)
            start = dict(restart=restart, free_bytes=common.disk_space(), free_physical_memory=common.physical_memory())
            row["starts"].append(start)
            command = retirement._server_command(args.java); command[2] = "-Xmx4G"
            command[1:1] = ["-Dadvancedrocketrycommunity.escrowCase=" + PREFIX + cut,
                             "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"),
                             "-Dadvancedrocketrycommunity.escrowLaunchTrace=true"]
            start["command"] = command
            process = smoke.CapturedProcess(command, session, evidence / f"start-{restart}.log")
            process.wait_for(re.compile(r"ARCE_FILL_COUNTDOWN_PAUSED cut=" + PREFIX + cut), args.startup_timeout)
            process.process.kill(); process.abort()
            lines = process.lines
            start["observations"] = [line for line in lines if "ARCE_FILL_COUNTDOWN_" in line or "ARCE_LAUNCH_TRACE" in line]
            start["launches"] = trace.observations(lines)
            base.require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Native countdown logged ERROR/FATAL")
            saved = f"recovery={str(bool(restart)).lower()} state=COUNTDOWN frozen={cut} bound=true"
            base.require(any(saved in line for line in lines), "Missing bound countdown save/reload observation")
            if not restart:
                base.require(any(f"state={cut} transfer=NONE tank=4000 bank=1000" in line for line in lines), "Missing NONE fill checkpoint")
                base.require(sum(r["answer"]["check"] == "COUNTDOWN" for r in start["launches"]) == 1,
                             "First launch did not pass governed prepare and reach COUNTDOWN")
            else:
                base.require(not start["launches"], "Restart issued a replacement launch instead of resuming countdown")
            start["disk"] = retirement.inspect(session / "world")
            found = check(start["disk"], cut)
            base.require(identity is None or identity == found, "Restart changed the complete countdown or frozen authority")
            identity = found; start["status"] = "PASS"
            (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        row["status"] = "PASS"
    except Exception as failure:
        row.update(status="FAIL", error=str(failure))
    finally:
        if process is not None:
            process.abort()
        common.cleanup(session, token, args.session_parent)
        row["cleanup"] = not session.exists()
        (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
    return row
