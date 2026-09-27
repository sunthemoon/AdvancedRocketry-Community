#!/usr/bin/env python3
"""Four finite packaged cycles for declarative components and native rocket snapshots.

Uses normal assembly and opt-in operator disassembly/report commands, with zero
fuel and no connected players. This is not player flight, crash or load evidence.
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
import time
import uuid
from pathlib import Path

if __package__:
    from . import run_v130_adapter_recovery_smoke as recovery
    from . import run_v130_adapter_flight_smoke as flight
else:
    import run_v130_adapter_recovery_smoke as recovery
    import run_v130_adapter_flight_smoke as flight

server = recovery.server_smoke
SmokeError = recovery.SmokeError
write_json = recovery.write_json
require = flight.require
HOST, ROCKET = recovery.HOST, recovery.ROCKET
ORIGIN = (264, 101, 264)
ASSEMBLER = (264, 100, 264)
RELATIVE = {(0, 0, 0): "minecraft:diamond_block", (-1, 0, 0): "minecraft:emerald_block",
            (0, 1, 0): "minecraft:oak_planks", (0, 2, 0): "minecraft:gold_block", (1, 0, 0): "minecraft:quartz_block"}
POSITIONS = {tuple(a + b for a, b in zip(ORIGIN, offset)): name for offset, name in RELATIVE.items()}
PHASES = ("assemble", "skipped", "updated", "restart")
ASSEMBLY = re.compile(r"ARCE_ROCKET_TRANSACTION operation=assembly code=SUCCESS blocks=5 "
                      r"snapshot=([0-9a-f]{64}) entity=([0-9a-f-]{36})")


def expected_stats(updated: bool) -> dict:
    return {"block_count": 5, "mass": 294 if updated else 254, "thrust": 2800 if updated else 2400,
            "fuel_capacity": 2000 if updated else 1500, "engine_count": 1, "seat_count": 1,
            "guidance_count": 1, "block_entity_count": 0}


def validate_registration(lines: list[str], phase: str) -> None:
    require(phase in PHASES, "Unknown component phase")
    receipts = [line for line in lines if "Registered rocket components " in line or "Skipped rocket components " in line]
    variant = "updated" if phase in ("updated", "restart") else "standard"
    expected = "Skipped rocket components (event 1)" if phase == "skipped" else (
        f"Registered rocket components (variant {variant}, API 1.7, event 1)")
    require(len(receipts) == 1 and receipts[0].rstrip().endswith(expected), "Component registration selection differs")


def validate_disk(state: dict, phase: str, receipt: tuple, previous: dict | None) -> dict:
    require(phase in PHASES, "Unknown component phase")
    rockets = [entity for entity in state["entities"] if entity.get("id") == ROCKET]
    require(len(rockets) == 1 and not any(entity.get("id") == "minecraft:item" for entity in state["entities"]),
            "Native rocket or dropped-item authority differs")
    entity = rockets[0]
    require(recovery.uuid_from_nbt(entity["UUID"]) == receipt[1], "Native entity UUID differs from assembly receipt")
    data = entity["RocketEntityData"]
    snapshot, flight_data = data["snapshot"], data["flight_data"]
    require(data.get("schema_version") == 2 and snapshot.get("schema_version") == 1
            and snapshot.get("source_dimension") == "minecraft:overworld"
            and snapshot.get("source_origin") == list(ORIGIN)
            and snapshot.get("bounding_box") == [-1, 0, 0, 1, 2, 0]
            and snapshot.get("content_hash") == receipt[0], "Snapshot schema/location/identity differs")
    require(str(uuid.UUID(snapshot["snapshot_id"])) == snapshot["snapshot_id"], "Snapshot UUID is not canonical")
    require(recovery.uuid_from_nbt(data["owner_id"]) == flight.OWNER
            and data["assembly_transaction_id"] == flight_data.get("logical_rocket_id"), "Owner/logical identity differs")
    palette, blocks = snapshot["block_palette"], snapshot["relative_blocks"]
    require(len(palette) == len(blocks) == 5, "Component count changed")
    actual = {}
    for block in blocks:
        offset, index = tuple(block["position"]), block["palette"]
        require(offset not in actual and type(index) is int and 0 <= index < len(palette), "Bad position/palette")
        require("block_entity" not in block and palette[index].get("properties") == {}, "Unexpected component payload/state")
        actual[offset] = palette[index]["id"]
    require(actual == RELATIVE and snapshot.get("passenger_anchors") == [[0, 1, 0]], "Component blocks/anchors differ")
    expected = expected_stats(phase in ("updated", "restart"))
    require(snapshot.get("mass_inputs") == expected, "Captured component metrics differ")
    require(flight_data.get("schema_version") == 2 and flight_data.get("state") == "ASSEMBLED"
            and flight_data.get("fuel") == {"capacity": expected["fuel_capacity"], "amount": 0, "committed_debits": []}
            and flight_data.get("passengers") == {"seat_capacity": 1, "assignments": []}, "Flight fuel/seats differ")
    expected_blocks = {str(pos): {"Name": "minecraft:air"} for pos in POSITIONS}
    expected_blocks[str(ASSEMBLER)] = {"Name": HOST + ":rocket_assembler"}
    require(state["blocks"] == expected_blocks and not state["journal"]["transactions"], "Native world/journal authority differs")
    selected_block_entities = [entry for entry in state["block_entities"]
                              if tuple(entry.get(axis) for axis in ("x", "y", "z")) in (*POSITIONS, ASSEMBLER)]
    require(len(selected_block_entities) == 1 and selected_block_entities[0].get("id") == HOST + ":rocket_assembler"
            and tuple(selected_block_entities[0].get(axis) for axis in ("x", "y", "z")) == ASSEMBLER,
            "Unexpected BlockEntity remains at a captured component position")
    if phase in ("skipped", "restart"):
        require(previous is not None and data == previous, "Restart changed frozen RocketEntityData")
    elif phase == "updated":
        require(previous is not None and data["assembly_transaction_id"] != previous["assembly_transaction_id"]
                and snapshot["snapshot_id"] != previous["snapshot"]["snapshot_id"]
                and snapshot["content_hash"] != previous["snapshot"]["content_hash"], "Reassembly reused old authority")
    return data


def capture_disk(root: Path, output: Path) -> dict:
    files = {}
    for relative in ("world/level.dat", "world/region/r.0.0.mca", "world/entities/r.0.0.mca", "world/data/" + recovery.JOURNAL):
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(recovery.regular(root / relative, recovery.region_nbt.MAX_REGION_BYTES))
        files[relative] = {"sha256": server.digest_file(target), "bytes": target.stat().st_size}
    raw = recovery.regular(output / "world/region/r.0.0.mca", recovery.region_nbt.MAX_REGION_BYTES)
    chunk = recovery.region_nbt._decode_chunk(recovery.region_nbt._region_chunk(raw, 16, 16), 16, 16)
    raw = recovery.regular(output / "world/entities/r.0.0.mca", recovery.region_nbt.MAX_REGION_BYTES)
    entities = recovery.decode_entity_chunk(recovery.region_nbt._region_chunk(raw, 16, 16))["Entities"]
    return {"blocks": {str(pos): recovery.block_at(chunk, pos) for pos in (*POSITIONS, ASSEMBLER)},
            "entities": entities, "block_entities": chunk.get("block_entities", []),
            "journal": recovery.read_journal(output / "world/data" / recovery.JOURNAL), "files": files}


class ComponentRun(recovery.RecoveryRun):
    def live(self, process, updated: bool, label: str, assembled: bool = True) -> None:
        if assembled:
            self.query(process, f"arce rocket release-test report {self.receipt[1]}",
                       re.compile(r"Release-test flight state=ASSEMBLED fuel=0"))
        process.command("scoreboard players set #rockets arce_component 0")
        process.command(f"execute as @e[type={ROCKET}] run scoreboard players add #rockets arce_component 1")
        condition = f"if score #rockets arce_component matches {1 if assembled else 0} "
        if assembled:
            stats = expected_stats(updated)
            stats_nbt = ",".join(f"{key}:{value}{'L' if key in ('mass', 'thrust', 'fuel_capacity') else ''}"
                                 for key, value in stats.items())
            condition += (f'if data entity {self.receipt[1]} {{RocketEntityData:{{snapshot:{{content_hash:"{self.receipt[0]}",'
                          f'mass_inputs:{{{stats_nbt}}}}},flight_data:{{fuel:{{amount:0L,capacity:{stats["fuel_capacity"]}L}}}}}}}} ')
        for pos, name in POSITIONS.items():
            condition += f"if block {' '.join(map(str, pos))} {'minecraft:air' if assembled else name} "
        condition += "unless entity @e[type=minecraft:item,x=262,y=99,z=262,dx=5,dy=6,dz=5] "
        self.query(process, f"execute {condition}run say {label}", re.compile(rf"\[Server\] {re.escape(label)}\s*$"))

    def assemble(self, process) -> None:
        match = self.query(process, "arce rocket assemble 264 100 264", ASSEMBLY, 45)
        self.receipt = (match.group(1), str(uuid.UUID(match.group(2))))

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        if self.world_identity:
            server.complete_world_identity(self.server, self.world_identity)
        command = recovery.rocket_smoke._server_command(self.java)
        if phase == "skipped":
            command.insert(1, "-Darce_adapter_test.skipRocketComponents=true")
        if phase in ("updated", "restart"):
            command.insert(1, "-Darce_adapter_test.componentVariant=updated")
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.server), "mods": self.mods(True)})
        process = server.CapturedProcess(command, self.server, directory / "stdout.txt")
        original = process.command
        self.commands = []

        def recorded(value):
            self.commands.append(value)
            original(value)

        process.command = recorded
        try:
            process.wait_for(server.READY_MARKER, self.timeout)
            status = server.wait_for_status(self.port)
            document["status_mods"] = recovery.validate_status(status, True, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            validate_registration(process.lines, phase)
            process.command("forceload add 264 264")
            recovery.wait_condition(process, "execute if loaded 264 101 264", "V130_COMPONENT_LOADED")
            if phase == "assemble":
                process.command("gamerule doMobSpawning false")
                process.command("gamerule randomTickSpeed 0")
                process.command("scoreboard objectives add arce_component dummy")
                process.command("fill 262 100 262 266 105 266 minecraft:air")
                process.command(f"setblock 264 100 264 {HOST}:rocket_assembler")
                for pos, name in POSITIONS.items():
                    process.command(f"setblock {' '.join(map(str, pos))} {name}")
                self.assemble(process)
            if phase == "updated":
                document["previous_receipt"] = self.receipt
                self.live(process, False, "V130_COMPONENT_OLD_CAPTURE")
                self.query(process, f"arce rocket release-test disassemble {self.receipt[1]}",
                           re.compile(r"Release-test disassembly completed"))
                self.live(process, False, "V130_COMPONENT_RESTORED_BLOCKS", False)
                self.assemble(process)
            updated = phase in ("updated", "restart")
            self.live(process, updated, "V130_COMPONENT_FIRST_" + phase)
            before = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
            deadline = time.monotonic() + 10
            while True:
                time.sleep(0.3)
                after = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
                if after - before >= 20:
                    break
                require(time.monotonic() < deadline, "Finite component observation window did not advance")
            document["observation_ticks"] = after - before
            self.live(process, updated, "V130_COMPONENT_SECOND_" + phase)
            document["receipt"] = self.receipt
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop")
            require(process.finish() == 0, "Dedicated component process did not exit cleanly")
        except BaseException:
            process.abort()
            raise
        finally:
            document["exit_code"] = process.process.poll()
            write_json(directory / "commands.json", self.commands)
            for name in ("debug.log", "latest.log"):
                source = self.server / "logs" / name
                if source.exists():
                    (directory / name).write_bytes(recovery.regular(source, 32 * 1024**2))
        validate_registration(process.lines, phase)
        recovery.validate_registration(process.lines, "assemble")
        recovery.audit_log(process.lines, "assemble")
        document["log_counts"] = server.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server.verify_active_server_properties(
                recovery.regular(self.server / "server.properties", 65536), self.port)
        configs = {}
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            payload = recovery.regular(self.server / relative, 65536)
            target.write_bytes(payload)
            configs[relative] = flight.configuration_identity(relative, payload)
        require(self.config_hashes is None or self.config_hashes == configs, "Configuration changed across component cycles")
        self.config_hashes = configs
        self.mods(True)
        state = capture_disk(self.server, directory)
        write_json(directory / "disk-state.json", state)
        self.baseline = validate_disk(state, phase, self.receipt, self.baseline)
        document["disk_evidence"] = state["files"]
        return document


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--host-jar", type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.3.0-dev")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    args = parser.parse_args()
    output = None
    try:
        require(0 < args.startup_timeout <= 240, "Startup timeout must be within (0, 240] seconds")
        root, output_path, artifacts, args_file = recovery.validate_inputs(
                args.server_dir, args.evidence_dir, args.host_jar, args.fixture_jar, args.expected_version)
        java, java_version = server.resolve_java(args.java)
        output_path.mkdir(parents=True)
        output = output_path
        port = server.allocate_port()
        properties = server.write_server_configuration(root, port, True)
        (root / "mods").mkdir()
        for item in artifacts:
            shutil.copyfile(item["path"], root / "mods" / item["name"])
        run = ComponentRun(root, output, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "four clean processes; static components; zero fuel; native rocket snapshots",
                   "artifacts": artifacts, "java": java_version, "server": str(root), "port": port,
                   "forge_args_sha256": server.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(output / "summary.json", summary)
        for phase in PHASES:
            summary["cycles"].append(run.run_phase(phase))
            if phase == "assemble":
                run.world_identity = server.establish_world_identity(root, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
            summary["world"] = server.complete_world_identity(root, run.world_identity)
            write_json(output / "summary.json", summary)
        for item in artifacts:
            require(server.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed")
        summary["result"] = "PASS"
        write_json(output / "summary.json", summary)
        print(f"[PASS] Four finite component snapshot cycles; evidence: {output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError) as exc:
        if output is not None:
            write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if output is not None:
            paths = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(
                f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in paths), encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
