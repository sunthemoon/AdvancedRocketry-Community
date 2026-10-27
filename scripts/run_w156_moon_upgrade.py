#!/usr/bin/env python3
"""ADR-075 native upgrade restart case; called by the owner of the heavy-JVM lock.

The jar from before ADR-075 (the old rule) lands rocket L on the Moon at y 80, stops rocket P at PREPARED (TRANSIT)
and rocket D at DESTINATION_SPAWNED, both with destinations recorded at y 80. The W156 jar then restarts the same
world: recovery keeps P at its recorded source and lands D at its recorded y 80 origin, each with exact fuel and one
entity; L keeps its position; a new launch N lands on the ground, and its region overlaps neither L's region nor D's
recorded destination region. A second restart leaves all four unchanged.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import (
    ASSEMBLY_LOG,
    LAUNCH_LOG,
    PAUSE_LOG,
    PHASE_LOG,
    RECOVERY_LOG,
    FlightHarness,
    _in_dimension,
    _server_command,
)

EARTH = "minecraft:overworld"
MOON = "advancedrocketrycommunity:moon"
OLD_FLOOR = 80
Y = 100
BLOCKS = 4
# Earth sites of the four rockets; each has a rocket assembler at (x, Y, z) and the rocket above it.
SITES = {"L": (384, 384), "P": (416, 384), "D": (448, 384), "N": (480, 384)}
# The selector's first fixed Moon pads around (8, 80, 8), 64 blocks apart.
MOON_PADS = ((8, 8), (72, 8), (-56, 8), (8, 72))
LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")


def region(origin: list[int]) -> tuple[tuple[int, int, int], tuple[int, int, int]]:
    """The structure region of the 4-block rocket (tank west of the motor, seat and computer above it)."""
    x, y, z = origin
    return (x - 1, y, z), (x, y + 2, z)


def overlaps(first, second) -> bool:
    (a0, a1), (b0, b1) = first, second
    return all(a0[i] <= b1[i] and a1[i] >= b0[i] for i in range(3))


class Upgrade:
    def __init__(self, java: str, session: Path, port: int, evidence: Path):
        self.java, self.session, self.port, self.evidence = java, session, port, evidence
        self.stages: list[dict] = []

    def start(self, stage: str, jar: Path) -> smoke.CapturedProcess:
        for drive in (Path("C:/"), Path("D:/")):
            if shutil.disk_usage(drive).free < 10 * 1024**3:
                raise smoke.SmokeError("Native start requires 10 GiB free on C: and D:")
        mods = self.session / "mods"
        for old in mods.glob("*.jar"):
            old.unlink()
        installed = mods / jar.name
        shutil.copy2(jar, installed)
        command = _server_command(self.java)
        command[2] = "-Xmx4G"
        process = smoke.CapturedProcess(command, self.session, self.evidence / f"{stage}.log")
        process.wait_for(smoke.READY_MARKER, 300)
        smoke.validate_status_identity(smoke.wait_for_status(self.port), smoke.V180_STARTUP_VERSION)
        self.stages.append(dict(stage=stage, jar=jar.name, jar_sha256=smoke.digest_file(installed), command=command))
        for x, z in MOON_PADS:
            process.command(_in_dimension(MOON, f"forceload add {x} {z}"))
        return process

    def stop(self, process: smoke.CapturedProcess) -> None:
        start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(smoke.SAVE_MARKER, 60.0, start_at=start)
        process.command("stop")
        code = process.finish()
        findings = [line for line in smoke.scan_log(process.lines, expected_mod_version=smoke.V180_STARTUP_VERSION)
                    if "ARCE_TRANSFER_RECOVERY" not in line]
        self.stages[-1].update(exit=code, findings=findings)
        if code != 0 or findings:
            raise smoke.SmokeError(f"Stage {self.stages[-1]['stage']} exit {code}, findings {findings[:3]}")

    @staticmethod
    def assemble(process: smoke.CapturedProcess, name: str) -> dict:
        x, z = SITES[name]
        process.command(f"forceload add {x} {z}")
        process.command(f"fill {x - 2} {Y} {z - 1} {x + 2} {Y + 4} {z + 1} minecraft:air")
        process.command(f"setblock {x} {Y} {z} advancedrocketrycommunity:rocket_assembler")
        process.command(f"setblock {x - 1} {Y + 1} {z} advancedrocketrycommunity:rocket_fuel_tank")
        process.command(f"setblock {x} {Y + 1} {z} advancedrocketrycommunity:rocket_motor")
        process.command(f"setblock {x} {Y + 2} {z} advancedrocketrycommunity:rocket_seat")
        process.command(f"setblock {x} {Y + 3} {z} advancedrocketrycommunity:guidance_computer")
        start = len(process.lines)
        process.command(f"arce rocket assemble {x} {Y} {z}")
        index = process.wait_for(ASSEMBLY_LOG, 45.0, start_at=start)
        blocks, _snapshot, entity = ASSEMBLY_LOG.search(process.lines[index]).groups()
        if int(blocks) != BLOCKS:
            raise smoke.SmokeError(f"Rocket {name} has {blocks} blocks, expected {BLOCKS}")
        process.wait_for(re.compile(rf"ARCE_ROCKET_ENTITY_ACTIVE entity={re.escape(entity)} operational=true "),
                         30.0, start_at=index)
        report = FlightHarness.report(process, EARTH, entity)
        if report["state"] != "ASSEMBLED":
            raise smoke.SmokeError(f"Rocket {name} did not assemble")
        return report

    @staticmethod
    def launch(process: smoke.CapturedProcess, entity: str, checkpoint: str | None = None) -> dict:
        FlightHarness.refuel(process, EARTH, entity)
        start = len(process.lines)
        suffix = f" {checkpoint}" if checkpoint else ""
        process.command(_in_dimension(EARTH, f"arce rocket release-test launch {entity} moon{suffix}"))
        index = process.wait_for(LAUNCH_LOG, 30.0, start_at=start)
        transfer, _entity, logical, _source, destination, _checkpoint, code, required, fuel_before = \
            LAUNCH_LOG.search(process.lines[index]).groups()
        # v1.8 launches are asynchronous: the receipt answers JOURNAL_PENDING (as run_v180_predecessor_smoke.py expects).
        if code != "JOURNAL_PENDING" or destination != MOON:
            raise smoke.SmokeError(f"Launch of {entity} answered {code} to {destination}")
        flight = dict(transfer=transfer, logical=logical, required=int(required), fuel_before=int(fuel_before))
        if checkpoint:
            pattern = re.compile(rf"ARCE_RELEASE_TEST_FLIGHT_PAUSED checkpoint={re.escape(checkpoint)} "
                                 rf"transfer={re.escape(transfer)} ")
            paused = PAUSE_LOG.search(process.lines[process.wait_for(pattern, 60.0, start_at=index)]).groups()
            flight.update(phase=paused[2], state=paused[3], paused_entity=paused[4])
            return flight
        landed = re.compile(rf"ARCE_TRANSFER_PHASE transfer={re.escape(transfer)} .*event=landing_complete ")
        line = process.lines[process.wait_for(landed, 120.0, start_at=index)]
        flight["landed_entity"] = PHASE_LOG.search(line).group(5)
        flight["report"] = FlightHarness.report(process, MOON, flight["landed_entity"])
        return flight

    @staticmethod
    def recovery(process: smoke.CapturedProcess, transfer: str) -> dict:
        line = process.lines[process.wait_for(re.compile(rf"ARCE_TRANSFER_RECOVERY transfer={re.escape(transfer)} "), 90.0)]
        _transfer, phase, sources, destinations, action, status = RECOVERY_LOG.search(line).groups()
        return dict(phase=phase, source_count=int(sources), destination_count=int(destinations), action=action, status=status)

    @staticmethod
    def ground(process: smoke.CapturedProcess, origin: list[int]) -> int:
        """How many of the rocket's two lowest-layer blocks stand over a non-air block."""
        x, y, z = origin
        count = 0
        for column in (x - 1, x):
            marker = f"W156_GROUND_{uuid.uuid4().hex[:8]}"
            start = len(process.lines)
            process.command(_in_dimension(MOON, f"execute unless block {column} {y - 1} {z} minecraft:air run say {marker}"))
            process.command(_in_dimension(MOON, f"say {marker}_END"))
            end = process.wait_for(re.compile(re.escape(marker + "_END")), 30.0, start_at=start)
            count += any(marker in line and marker + "_END" not in line for line in process.lines[start:end + 1])
        return count


def require(condition: bool, message: str) -> None:
    if not condition:
        raise smoke.SmokeError(message)


def same(before: dict, after: dict) -> bool:
    return all(before[key] == after[key] for key in ("entity", "logical", "dimension", "state", "fuel", "origin", "blocks"))


def run(args) -> dict:
    repo = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if commit != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Requires the exact clean committed source")
    token = os.environ.get("W156_LOCK_TOKEN", "")
    if not token or not LOCK.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("The heavy-JVM lock is not owned by the caller")
    evidence = args.evidence_dir.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    session = TEMP / ("w156-upgrade-" + uuid.uuid4().hex)
    result = dict(status="IN_PROGRESS", source_commit=commit, old_commit=args.old_commit,
                  old_jar_sha256=smoke.digest_file(args.old_jar), new_jar_sha256=smoke.digest_file(args.jar))
    process = None
    try:
        session.mkdir()
        shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir()
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        props = smoke.expected_server_properties(port, True)
        props.update({"level-name": "world", "level-type": "minecraft:flat", "max-tick-time": "60000",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                          {"block": "minecraft:stone", "height": 3}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        upgrade = Upgrade(args.java, session, port, evidence)

        # The old rule: L lands at y 80; P and D record y 80 destinations and stop.
        process = upgrade.start("old", args.old_jar)
        rockets = {name: upgrade.assemble(process, name) for name in ("L", "P", "D")}
        flights = {"L": upgrade.launch(process, rockets["L"]["entity"]),
                   "P": upgrade.launch(process, rockets["P"]["entity"], "TRANSIT_PREPARED"),
                   "D": upgrade.launch(process, rockets["D"]["entity"], "DESTINATION_SPAWNED")}
        landed_l = flights["L"]["report"]
        require(landed_l["state"] == "LANDED" and landed_l["origin"][1] == OLD_FLOOR, "The old rule did not land L at y 80")
        require(flights["P"]["phase"] == "PREPARED" and flights["P"]["state"] == "TRANSIT", "P did not stop at PREPARED")
        spawned_d = FlightHarness.report(process, MOON, flights["D"]["paused_entity"])
        require(flights["D"]["phase"] == "DESTINATION_SPAWNED" and spawned_d["origin"][1] == OLD_FLOOR,
                "D's recorded destination is not at y 80")
        upgrade.stop(process)
        result.update(rockets=rockets, flights=flights, spawned_d=spawned_d)

        # The W156 jar: recovery keeps the recorded identities; a new launch lands on the ground.
        process = upgrade.start("recover1", args.jar)
        recovery_p = upgrade.recovery(process, flights["P"]["transfer"])
        recovery_d = upgrade.recovery(process, flights["D"]["transfer"])
        require(recovery_p["status"] == "RECOVERED" and recovery_p["action"] == "KEEP_SOURCE",
                f"P's PREPARED record recovered as {recovery_p}")
        require(recovery_d["status"] == "RECOVERED" and recovery_d["action"] == "REMOVE_SOURCE_KEEP_DESTINATION",
                f"D's DESTINATION_SPAWNED record recovered as {recovery_d}")
        landing = re.compile(rf"ARCE_TRANSFER_PHASE transfer={re.escape(flights['D']['transfer'])} .*event=landing_complete ")
        landed_d_entity = PHASE_LOG.search(process.lines[process.wait_for(landing, 120.0)]).group(5)
        landed_d = FlightHarness.report(process, MOON, landed_d_entity)
        source_p = FlightHarness.report(process, EARTH, rockets["P"]["entity"])
        after_l = FlightHarness.report(process, MOON, landed_l["entity"])
        require(landed_d["state"] == "LANDED" and landed_d["origin"] == spawned_d["origin"]
                and landed_d["fuel"] == flights["D"]["fuel_before"] - flights["D"]["required"]
                and landed_d["logical"] == flights["D"]["logical"], "D did not land at its recorded y 80 origin with exact fuel")
        require(source_p["state"] == "FUELED" and source_p["origin"] == rockets["P"]["origin"]
                and source_p["fuel"] == flights["P"]["fuel_before"] and source_p["logical"] == flights["P"]["logical"],
                "P was not kept at its recorded source with its fuel")
        require(same(landed_l, after_l), "The upgrade moved or changed the rocket landed at y 80")
        rockets["N"] = upgrade.assemble(process, "N")
        flights["N"] = upgrade.launch(process, rockets["N"]["entity"])
        landed_n = flights["N"]["report"]
        supported = upgrade.ground(process, landed_n["origin"])
        require(landed_n["state"] == "LANDED" and landed_n["origin"][1] < OLD_FLOOR and supported >= 1,
                f"The new launch did not land on the ground: {landed_n['origin']}, {supported} supported blocks")
        require(not overlaps(region(landed_n["origin"]), region(after_l["origin"]))
                and not overlaps(region(landed_n["origin"]), region(spawned_d["origin"])),
                "The new arrival's region overlaps the landed rocket or D's recorded destination")
        upgrade.stop(process)
        first = dict(L=after_l, D=landed_d, P=source_p, N=landed_n)
        result.update(recovery_p=recovery_p, recovery_d=recovery_d, after_first_restart=first, n_supported_blocks=supported)

        # A second restart leaves all four unchanged.
        process = upgrade.start("recover2", args.jar)
        second = {"L": FlightHarness.report(process, MOON, after_l["entity"]),
                  "D": FlightHarness.report(process, MOON, landed_d["entity"]),
                  "P": FlightHarness.report(process, EARTH, source_p["entity"]),
                  "N": FlightHarness.report(process, MOON, landed_n["entity"])}
        upgrade.stop(process)
        process = None
        result["after_second_restart"] = second
        require(all(same(first[name], second[name]) for name in first), "The second restart changed a rocket")
        result["stages"] = upgrade.stages
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=f"{type(failure).__name__}: {failure}")
        if process is not None and process.process.poll() is None:
            process.abort()
    finally:
        if session.is_dir() and not smoke.is_link_or_junction(session) and session.resolve().parent == TEMP.resolve():
            shutil.rmtree(session, ignore_errors=True)
        result["temporary_removed"] = not session.exists()
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source-server", "old-jar", "jar", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    for name in ("java", "source-commit", "old-commit"):
        parser.add_argument("--" + name, required=True)
    result = run(parser.parse_args())
    print(json.dumps({key: result.get(key) for key in ("status", "error", "temporary_removed")}, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
