#!/usr/bin/env python3
"""Four finite packaged cycles: actual hopper fuels, frozen batches, legacy migration and changed definitions.

An initial schema-1 buffer is a migration fixture, not evidence of its earlier
consumption. All new fuel inputs use native hoppers. No forced crash/load/client
acceptance or cross-file atomicity is claimed.
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
    from . import run_v130_component_smoke as components
else:
    import run_v130_component_smoke as components

recovery, server, flight = components.recovery, components.server, components.flight
SmokeError, require, write_json = components.SmokeError, components.require, components.write_json
HOST, ROCKET = components.HOST, components.ROCKET
KEY = "arce_fuel_loader"
PHASES = ("buffered", "skipped", "updated", "restart")
LOADERS = ((268, 101, 264), (270, 101, 270), (268, 101, 270))
ORIGINS = (components.ORIGIN, (265, 101, 268))
OWNER = [0, 0, 0, 5]


def native_item(name: str, metadata: bool = False) -> dict:
    value = {"id": name, "Count": 1}
    if metadata:
        value["tag"] = {"fixture": "queued native data"}
    return value


def expected_roots(phase: str) -> list[dict]:
    require(phase in PHASES, "Unknown fuel phase")
    roots = [{"schema_version": 2, "slot_role": 0, "item": {}, "buffered_units": 0, "owner_id": OWNER.copy()}
             for _ in LOADERS]
    if phase == "buffered":
        for index, definition, total, remaining, item in (
                (0, "arce_adapter_test:large_fuel", 1573, 73, "minecraft:stick"),
                (2, HOST + ":rocket_fuel_cell", 500, 125, HOST + ":empty_canister")):
            roots[index].update(buffered_units=remaining, batch={"definition": definition, "total_units": total,
                                                               "remainder": native_item(item)})
    else:
        roots[0].update(slot_role=2, item=native_item("minecraft:stick"))
        roots[2].update(slot_role=2, item=native_item(HOST + ":empty_canister"))
    roots[1].update(slot_role=1 if phase in ("buffered", "skipped") else 2,
                    item=native_item("minecraft:charcoal", True) if phase in ("buffered", "skipped") else native_item("minecraft:bowl"))
    return roots


def validate_registration(lines: list[str], phase: str) -> None:
    require(phase in PHASES, "Unknown fuel phase")
    matches = [line for line in lines if "Registered rocket fuels " in line or "Skipped rocket fuels " in line]
    variant = "updated" if phase in ("updated", "restart") else "standard"
    expected = "Skipped rocket fuels (event 1)" if phase == "skipped" else f"Registered rocket fuels (variant {variant}, API 1.5, event 1)"
    require(len(matches) == 1 and matches[0].rstrip().endswith(expected), "Fuel registration selection differs")


def validate_disk(state: dict, phase: str, receipts: list, previous: dict | None) -> dict:
    require(phase in PHASES, "Unknown fuel phase")
    rockets = [entity for entity in state["entities"] if entity.get("id") == ROCKET]
    require(len(rockets) == len(receipts) == (1 if phase == "buffered" else 2), "Rocket count differs")
    require(not any(entity.get("id") == "minecraft:item" for entity in state["entities"]), "Unexpected loose item")
    require(state["journal"].get("transactions") == [], "Unfinished assembly transaction")
    expected = expected_roots(phase)
    block_entities = state["block_entities"]
    for pos, wanted in zip(LOADERS, expected):
        loaders = [be for be in block_entities if (be.get("x"), be.get("y"), be.get("z")) == pos]
        require(len(loaders) == 1 and loaders[0].get("id") == HOST + ":fuel_loader" and loaders[0].get(KEY) == wanted,
                f"Native loader differs at {pos}")
    hoppers = [be for be in block_entities if be.get("id") == "minecraft:hopper"]
    require(len(hoppers) == 2 and all(be.get("Items") == [] for be in hoppers), "Input hoppers retained or duplicated fuel")
    require(len(block_entities) == 5 + len(receipts), "Unexpected native block entity")
    captured = []
    for index, receipt in enumerate(receipts):
        found = [entity for entity in rockets if recovery.uuid_from_nbt(entity["UUID"]) == receipt[1]]
        require(len(found) == 1, "Stale entity receipt")
        data = found[0]["RocketEntityData"]
        snapshot, fuel = data["snapshot"], data["flight_data"]["fuel"]
        require(data.get("schema_version") == 2 and data.get("owner_id") == OWNER
                and snapshot.get("content_hash") == receipt[0] and snapshot.get("source_origin") == list(ORIGINS[index])
                and snapshot.get("mass_inputs") == components.expected_stats(False), "Captured rocket identity/stats differ")
        require(data["flight_data"].get("schema_version") == 2 and data["flight_data"].get("state") == "FUELED"
                and fuel.get("capacity") == 1500 and fuel.get("amount") == (1500 if index == 0 else 198 if phase == "skipped" else 499)
                and fuel.get("committed_debits") == [], "Actual fuel totals differ")
        for offset in components.RELATIVE:
            pos = tuple(a + b for a, b in zip(ORIGINS[index], offset))
            require(state["blocks"][str(pos)] == {"Name": "minecraft:air"}, "Assembled component remains in world")
        if previous:
            old = previous["rockets"]
            if index < len(old):
                require(snapshot == old[index]["snapshot"] and data["assembly_transaction_id"] == old[index]["assembly_transaction_id"]
                        and data["flight_data"]["logical_rocket_id"] == old[index]["flight_data"]["logical_rocket_id"], "Rocket identity changed")
            if index == 0 or phase == "restart":
                require(data == old[index], "Restart changed frozen rocket authority")
        captured.append(data)
    require(phase == "buffered" or previous is not None, "Missing previous cycle authority")
    return {"rockets": captured, "loaders": expected}


def capture_disk(root: Path, output: Path) -> dict:
    state = components.capture_disk(root, output)
    raw = recovery.regular(output / "world/region/r.0.0.mca", recovery.region_nbt.MAX_REGION_BYTES)
    chunk = recovery.region_nbt._decode_chunk(recovery.region_nbt._region_chunk(raw, 16, 16), 16, 16)
    for origin in ORIGINS:
        for offset in components.RELATIVE:
            pos = tuple(a + b for a, b in zip(origin, offset))
            state["blocks"][str(pos)] = recovery.block_at(chunk, pos)
    return state


class FuelRun(recovery.RecoveryRun):
    def assemble(self, process, origin: tuple) -> None:
        x, y, z = origin
        process.command(f"setblock {x} {y - 1} {z} {HOST}:rocket_assembler")
        for offset, name in components.RELATIVE.items():
            pos = tuple(a + b for a, b in zip(origin, offset))
            process.command(f"setblock {' '.join(map(str, pos))} {name}")
        match = self.query(process, f"arce rocket assemble {x} {y - 1} {z}", components.ASSEMBLY, 45)
        self.receipts.append((match.group(1), str(uuid.UUID(match.group(2)))))

    def observe(self, process, phase: str, suffix: str) -> None:
        conditions = []
        for pos, root in zip(LOADERS, expected_roots(phase)):
            query = f'{{{KEY}:{{schema_version:2,slot_role:{root["slot_role"]},buffered_units:{root["buffered_units"]}L}}}}'
            conditions.append(f"if data block {' '.join(map(str, pos))} {query}")
        for index, receipt in enumerate(self.receipts):
            fuel = 1500 if index == 0 else 198 if phase == "skipped" else 499
            conditions.append(f'if data entity {receipt[1]} {{RocketEntityData:{{flight_data:{{fuel:{{amount:{fuel}L}}}}}}}}')
        recovery.wait_condition(process, "execute " + " ".join(conditions), f"V130_FUEL_{phase}_{suffix}", timeout=30)

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        if self.world_identity:
            server.complete_world_identity(self.server, self.world_identity)
        command = recovery.rocket_smoke._server_command(self.java)
        if phase == "skipped": command.insert(1, "-Darce_adapter_test.skipRocketFuels=true")
        if phase in ("updated", "restart"): command.insert(1, "-Darce_adapter_test.fuelVariant=updated")
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
            recovery.wait_condition(process, "execute if loaded 264 101 264", "V130_FUEL_LOADED")
            if phase == "buffered":
                self.receipts = []
                process.command("gamerule doMobSpawning false")
                process.command("gamerule randomTickSpeed 0")
                process.command("fill 262 100 262 271 105 271 minecraft:air")
                self.assemble(process, ORIGINS[0])
                for index, (x, y, z) in enumerate(LOADERS):
                    units = 125 if index == 2 else 0
                    process.command(f'setblock {x} {y} {z} {HOST}:fuel_loader'
                                    f'{{{KEY}:{{schema_version:1,item_state:0,buffered_units:{units}L,owner_id:[I;0,0,0,5]}}}}')
                    if index < 2:
                        item = 'id:"minecraft:blaze_powder",Count:1b' if index == 0 else (
                            'id:"minecraft:charcoal",Count:1b,tag:{fixture:"queued native data"}')
                        process.command(f'setblock {x} {y + 1} {z} minecraft:hopper{{Items:[{{Slot:0b,{item}}}]}}')
            if phase == "skipped":
                self.observe(process, "buffered", "PRESERVED_WITHOUT_DEFINITIONS")
                self.assemble(process, ORIGINS[1])
            self.observe(process, phase, "FIRST")
            before = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
            deadline = time.monotonic() + 10
            while True:
                time.sleep(0.3)
                after = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
                if after - before >= 20: break
                require(time.monotonic() < deadline, "Finite fuel observation did not advance")
            document["observation_ticks"] = after - before
            self.observe(process, phase, "SECOND")
            document["receipts"] = self.receipts
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop")
            require(process.finish() == 0, "Dedicated fuel process did not exit cleanly")
        except BaseException:
            process.abort()
            raise
        finally:
            document["exit_code"] = process.process.poll()
            write_json(directory / "commands.json", self.commands)
            for name in ("debug.log", "latest.log"):
                source = self.server / "logs" / name
                if source.exists(): (directory / name).write_bytes(recovery.regular(source, 32 * 1024**2))
        validate_registration(process.lines, phase)
        recovery.validate_registration(process.lines, "assemble")
        recovery.audit_log(process.lines, "assemble")
        document["log_counts"] = server.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server.verify_active_server_properties(recovery.regular(self.server / "server.properties", 65536), self.port)
        configs = {}
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            payload = recovery.regular(self.server / relative, 65536)
            target.write_bytes(payload)
            configs[relative] = flight.configuration_identity(relative, payload)
        require(self.config_hashes is None or self.config_hashes == configs, "Configuration changed across fuel cycles")
        self.config_hashes = configs
        self.mods(True)
        state = capture_disk(self.server, directory)
        write_json(directory / "disk-state.json", state)
        self.baseline = validate_disk(state, phase, self.receipts, self.baseline)
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
    args, output = parser.parse_args(), None
    try:
        require(0 < args.startup_timeout <= 240, "Startup timeout must be within (0, 240]")
        root, output_path, artifacts, args_file = recovery.validate_inputs(args.server_dir, args.evidence_dir,
                args.host_jar, args.fixture_jar, args.expected_version)
        java, java_version = server.resolve_java(args.java)
        output_path.mkdir(parents=True)
        output = output_path
        port = server.allocate_port()
        properties = server.write_server_configuration(root, port, True)
        (root / "mods").mkdir()
        for item in artifacts: shutil.copyfile(item["path"], root / "mods" / item["name"])
        run = FuelRun(root, output, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "four clean processes; native hopper fuels; legacy buffer fixture; no crash atomicity",
                   "artifacts": artifacts, "java": java_version, "server": str(root), "port": port,
                   "forge_args_sha256": server.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(output / "summary.json", summary)
        for phase in PHASES:
            summary["cycles"].append(run.run_phase(phase))
            if phase == "buffered": run.world_identity = server.establish_world_identity(root, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
            summary["world"] = server.complete_world_identity(root, run.world_identity)
            write_json(output / "summary.json", summary)
        for item in artifacts: require(server.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed")
        summary["result"] = "PASS"
        write_json(output / "summary.json", summary)
        print(f"[PASS] Four finite item-fuel cycles; evidence: {output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError) as exc:
        if output is not None: write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if output is not None and output.is_dir():
            paths = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in paths),
                                              encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
