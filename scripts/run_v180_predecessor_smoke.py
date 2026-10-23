#!/usr/bin/env python3
"""Selected I7 native preproof/pre-move cuts, each with two independent server restarts.

Uses existing gated seams and real ordinary flight commands. This is not the full
B-PRE/B-REPLACE/B-HOLDS matrix: no cold-site, named-pad, same-slot, crew or V2 claim.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import time
import uuid

import run_dedicated_server_smoke as smoke
import run_v180_loader_cuts as common
from run_v060_flight_server_smoke import FlightHarness, EARTH, MOON, _server_command
from run_v180_transfer_concurrency import assemble, TEMP, nbt_uuid
from run_v180_f3_n2_cuts import read_disk, save
from run_v180_escrow_forced_stop import digest
from run_v180_escrow_pre_f2 import assert_pin

CUTS = {
    "STORE": "BARRIER_STORE_BEFORE",
    "FORCE": "BARRIER_FORCE_BEFORE",
    "TERMINAL": "ORDINARY_REMOVAL_BEFORE",
    "MOVE": "K1",
}


def require(value, message):
    if not value:
        raise smoke.SmokeError(message)


def retain_raw(world: Path, evidence: Path, restart: int):
    """No source/export/server copies: retain only the actual storage observations."""
    output = evidence / f"raw-{restart}"
    for path in world.rglob("*"):
        if path.is_file() and (path.suffix in (".mca", ".dat", ".arce-pending") or path.name == "level.dat_old"):
            target = output / path.relative_to(world)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
    hashes = {path.relative_to(output).as_posix(): digest(path) for path in output.rglob("*") if path.is_file()}
    (evidence / f"raw-{restart}-hashes.json").write_text(json.dumps(hashes, indent=2), encoding="utf-8")


def verify(disk: dict, flight: dict, expected_fuel: int, restart: int, cut: str):
    logical = flight["logical_rocket_id"]
    transfers = disk["journal"]["transfers"]
    require(len(transfers) == 1 and str(nbt_uuid(transfers[0]["logical_rocket_id"])) == logical
            and str(nbt_uuid(transfers[0]["transfer_id"])) == flight["transfer_id"]
            and transfers[0]["phase"] == "COMMITTED", "Old T was lost before replacement")
    records = [row["entity"] for row in disk["entities"]]
    require(all(str(nbt_uuid(e["RocketEntityData"]["assembly_transaction_id"])) == logical for e in records), "Foreign rocket")
    sources = [e for e in records if str(nbt_uuid(e["UUID"])) == flight["source_entity"]]
    destinations = [e for e in records if str(nbt_uuid(e["UUID"])) == flight["destination_entity"]]
    require(len(sources) <= 1 and len(destinations) <= 1 and len(records) == len(sources) + len(destinations), "Duplicate/changed physical identity")
    if restart or cut in ("TERMINAL", "MOVE"):
        require(not sources and len(destinations) == 1, "Completed proof/recovery did not exclude old S and retain D")
    if destinations:
        entity = destinations[0]; data = entity["RocketEntityData"]; state = data["flight_data"]
        require(state["state"] == ("FUELED" if cut == "MOVE" else "LANDED")
                and state["fuel"]["amount"] == expected_fuel, "Lost current fuel or advanced countdown")
        require(not state.get("active_transfer_id") and not state["passengers"]["assignments"] and not entity.get("Passengers"), "Changed crew/active transfer")
        require(data["snapshot"] == transfers[0]["destination_snapshot"], "Changed complete snapshot content")
        require(data["owner_id"] == transfers[0]["owner_id"], "Changed owner")
        require(state["fuel"]["committed_debits"] == transfers[0]["destination_flight"]["fuel"]["committed_debits"], "Changed debit history")
    if not restart and cut == "MOVE":
        staged = disk.get("pending", {}).get("transfers", [])
        require(len(staged) == 1 and staged[0]["phase"] == "PREPARED"
                and str(nbt_uuid(staged[0]["source_entity_id"])) == flight["destination_entity"]
                and staged[0]["source_flight"]["fuel"]["amount"] == expected_fuel, "Missing exact staged successor")


def run_case(args, cut, evidence):
    assert_pin(args.source_commit)
    session = TEMP.resolve() / ("W150-I7-" + cut + "-" + uuid.uuid4().hex)
    libraries = args.source_server.resolve() / "libraries"
    require(libraries.is_dir() and not any(smoke.is_link_or_junction(p) for p in libraries.rglob("*")), "Unsafe libraries")
    session.mkdir(); evidence.mkdir(); token = uuid.uuid4().hex
    marker = session / ".w150-owned"; marker.write_text(token, encoding="ascii")
    row = dict(case=cut, source=args.source_commit, jar_sha256=digest(args.jar), status="RUNNING", starts=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        properties = smoke.expected_server_properties(port, True)
        properties.update({"level-type": "minecraft:flat", "max-tick-time": "60000", "generator-settings": json.dumps({
            "layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
        for restart in range(3):
            assert_pin(args.source_commit)
            require(digest(args.jar) == row["jar_sha256"], "JAR changed")
            command = _server_command(args.java); command[2] = "-Xmx4G"
            if not restart:
                command.insert(1, "-Dadvancedrocketrycommunity.landedDisassemblyProbe=LANDED_DISASSEMBLY:K1" if cut == "MOVE"
                               else "-Dadvancedrocketrycommunity.ordinaryRetirementCut=" + CUTS[cut])
            entry = dict(restart=restart, command=command, disk_space=common.disk_space(), memory=common.physical_memory())
            row["starts"].append(entry)
            process = smoke.CapturedProcess(command, session, evidence / f"start-{restart}.log")
            process.wait_for(smoke.READY_MARKER, args.startup_timeout)
            if not restart:
                first = assemble(process, 0)
                save(process)  # Only initial pre-debit S; never flush the world at the actual cut.
                leg, report = FlightHarness.launch_leg(FlightHarness.__new__(FlightHarness), process,
                    sequence=1, trip=1, direction="OUT", source_dimension=EARTH, destination_dimension=MOON,
                    destination_name="moon", entity=first["entity"], expected_logical=first["logical"])
                row["flight"] = leg; row["expected_fuel"] = report["fuel"]
                if cut == "MOVE":
                    refuelled = FlightHarness.refuel(process, MOON, leg["destination_entity"])
                    row["expected_fuel"] = refuelled["amount"]
                start = len(process.lines)
                process.command(f"execute in {MOON} run arce rocket release-test launch {leg['destination_entity']} earth")
                boundary = "ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K1" if cut == "MOVE" else "ARCE_N3_RETIREMENT_PAUSED boundary=" + CUTS[cut]
                process.wait_for(re.compile(re.escape(boundary)), 120, start_at=start)
                process.process.kill(); entry["exit_code"] = process.finish(timeout=30)
            else:
                leg = row["flight"]
                process.wait_for(re.compile("ARCE_ROCKET_ENTITY_ACTIVE entity=" + leg["destination_entity"]), 120)
                # Let the ordinary recovery run; normal stop saves its result, never changes the unmodified cut copy.
                time.sleep(3)
                report = FlightHarness.report(process, MOON, leg["destination_entity"])
                require(report["fuel"] == row["expected_fuel"]
                        and report["state"] == ("FUELED" if cut == "MOVE" else "LANDED"), "Restart changed live authority")
                process.command("stop"); entry["exit_code"] = process.finish()
                require(entry["exit_code"] == 0, "Restart did not stop normally")
            entry["errors"] = [line for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
            require(not entry["errors"], "Unexpected native error")
            process = None
            world = session / "world"
            retain_raw(world, evidence, restart)
            disk = read_disk(world)
            (evidence / f"disk-{restart}.json").write_text(json.dumps(disk, indent=2), encoding="utf-8")
            verify(disk, row["flight"], row["expected_fuel"], restart, cut)
            entry["status"] = "PASS"
        row["status"] = "PASS"
    except Exception as error:
        row.update(status="FAIL", error=str(error))
    finally:
        if process is not None:
            process.abort()
        (evidence / "result.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        require(session.resolve().parent == TEMP.resolve() and not smoke.is_link_or_junction(session)
                and marker.read_text(encoding="ascii") == token, "Changed server cleanup ownership")
        shutil.rmtree(session)
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--case", action="append", choices=CUTS)
    parser.add_argument("--startup-timeout", type=float, default=240)
    args = parser.parse_args(); args.evidence_dir.mkdir(parents=True)
    rows = []
    for index, cut in enumerate(args.case or CUTS):
        if index:
            time.sleep(90)
        with common.heavy_lock(f"W150 pid={os.getpid()} {uuid.uuid4()} cut={cut}"):
            common.disk_space(); common.physical_memory()
            rows.append(run_case(args, cut, args.evidence_dir / cut))
        (args.evidence_dir / "summary.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
        print(cut + ": " + rows[-1]["status"], flush=True)
    return 0 if all(row["status"] == "PASS" for row in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
