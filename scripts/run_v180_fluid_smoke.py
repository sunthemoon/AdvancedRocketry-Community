#!/usr/bin/env python3
"""Copy-only packaged-server gas canister upgrade and two same-world restarts."""

from __future__ import annotations

import argparse
import json
import re
import shutil
import time
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import run_v050_rocket_server_smoke as rocket
    from . import run_v180_combustion_smoke as combustion
    from . import v120_precision_world_fixture as regions
    from . import v140_migration_fixture as worlds
else:
    import run_dedicated_server_smoke as server
    import run_v050_rocket_server_smoke as rocket
    import run_v180_combustion_smoke as combustion
    import v120_precision_world_fixture as regions
    import v140_migration_fixture as worlds

NS = "advancedrocketrycommunity"
CANISTERS = ["empty_canister", "hydrogen_canister", "oxygen_canister", "nitrogen_canister"]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def capture(runtime: Path, evidence: Path, current: bool) -> dict:
    compressed = regions._region_chunk((runtime / "world/region/r.0.0.mca").read_bytes(), 8, 8)
    chunk = regions._decode_chunk(compressed, 8, 8)
    chests = [entity for entity in chunk.get("block_entities", [])
              if (entity.get("x"), entity.get("y"), entity.get("z")) == (132, 80, 132)]
    require(len(chests) == 1 and chests[0]["id"] == "minecraft:chest", "Fixture chest disappeared")
    items = {item["Slot"]: item for item in chests[0]["Items"]}
    require(len(items) == len(chests[0]["Items"]), "Duplicate saved inventory slots")
    require(len(items) == (6 if current else 3), "Unexpected fixture inventory")
    for slot, name in enumerate(CANISTERS[:4 if current else 3]):
        require(items[slot] == {"Slot": slot, "id": f"{NS}:{name}", "Count": 16,
                                "tag": {"marker": "legacy-kept" if slot < 3 else "new-nitrogen"}},
                "Canister ID, count or metadata changed on native save")
    if current:
        for slot, name in enumerate(("rocket_fuel_bucket", "enriched_lava_bucket"), 4):
            require(items[slot] == {"Slot": slot, "id": f"{NS}:{name}", "Count": 1}, "Liquid bucket changed")
    palette = {entry["Name"] for section in chunk.get("sections", [])
               for entry in section.get("block_states", {}).get("palette", [])}
    require("minecraft:lava" in palette, "Previously explored vanilla lava was changed")
    if current:
        require({f"{NS}:rocket_fuel", f"{NS}:enriched_lava"} <= palette, "Liquid world states did not persist")
    (evidence / "chunk-8-8.nbt.zlib").write_bytes(compressed)
    write(evidence / "saved-inventory.json", items)
    return items


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("runtime-template", "source-world", "v17-jar", "host-jar", "work-root"):
        parser.add_argument("--" + flag, type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    args.work_root = args.work_root.resolve()
    require(not args.work_root.exists(), "Use a new disposable evidence directory")
    for source in (args.runtime_template, args.source_world, args.v17_jar, args.host_jar):
        resolved = source.resolve()
        require(not args.work_root.is_relative_to(resolved) and not resolved.is_relative_to(args.work_root),
                "Disposable runtime must be disjoint from every input")
    old = combustion.artifact(args.v17_jar, "1.20.1-1.7.0-dev")
    current = combustion.artifact(args.host_jar, "1.20.1-1.8.0-dev")
    fixture = combustion.artifact(args.fixture_jar.resolve()) if args.fixture_jar else None
    work = args.work_root
    runtime = work / "server"
    runtime.mkdir(parents=True)
    original_world = worlds.copy_world(args.source_world, runtime / "world")
    shutil.copytree(args.runtime_template / "libraries", runtime / "libraries")
    mods = runtime / "mods"
    mods.mkdir()
    if fixture:
        shutil.copyfile(fixture["path"], mods / fixture["name"])
        require(server.digest_file(mods / fixture["name"]) == fixture["sha256"], "Installed fixture JAR changed")
    server.write_server_configuration(runtime, server.allocate_port(), True)
    command = rocket._server_command(args.java)
    command[2] = "-Xmx2G"
    write(work / "inputs.json", {"old": old, "current": current, "fixture": fixture, "source_world": str(args.source_world),
                               "runtime_template": str(args.runtime_template)})
    write(work / "source-world-files.json", original_world)
    receipts = []

    def phase(name: str, host: dict, seed: str | None = None) -> dict:
        directory = work / name
        directory.mkdir()
        for artifact in (old, current):
            installed = mods / artifact["name"]
            if installed.exists():
                installed.unlink()  # Only exact files installed by this newly created harness runtime.
        shutil.copyfile(host["path"], mods / host["name"])
        require(server.digest_file(mods / host["name"]) == host["sha256"], "Installed JAR changed")
        write(directory / "launch.json", {"command": command, "cwd": str(runtime), "host": host})
        process = None
        commands = []
        receipt = {"phase": name, "result": "IN_PROGRESS"}
        try:
            process = server.CapturedProcess(command, runtime, directory / "stdout.txt")
            process.wait_for(server.READY_MARKER, 300)

            def send(text: str):
                commands.append(text)
                process.command(text)

            def query(text: str, marker: str, timeout: int = 60):
                start = len(process.lines)
                send(text)
                return process.wait_for(re.compile(marker), timeout, start_at=start)

            query("forceload add 128 128", r"force loaded|No chunks were marked for force loading")
            query("forceload query 128 128", r"Chunk at \[8, ?8\] in minecraft:overworld is marked for force loading")
            time.sleep(1)
            if seed == "old":
                send("setblock 132 80 132 minecraft:chest")
                for slot, item in enumerate(CANISTERS[:3]):
                    send(f'item replace block 132 80 132 container.{slot} with {NS}:{item}{{marker:"legacy-kept"}} 16')
                send("fill 129 79 131 131 81 133 minecraft:stone")
                send("setblock 130 80 132 minecraft:lava")
                query("execute if block 130 80 132 minecraft:lava run say ARCE_FLUID_OLD_SEEDED", "ARCE_FLUID_OLD_SEEDED")
            elif seed == "current":
                send(f'item replace block 132 80 132 container.3 with {NS}:nitrogen_canister{{marker:"new-nitrogen"}} 16')
                for slot, liquid, x in ((4, "rocket_fuel", 136), (5, "enriched_lava", 140)):
                    send(f"item replace block 132 80 132 container.{slot} with {NS}:{liquid}_bucket")
                    send(f"fill {x-1} 79 131 {x+1} 81 133 minecraft:stone")
                    send(f"setblock {x} 80 132 {NS}:{liquid}")
            if host == current:
                query("execute if block 130 80 132 minecraft:lava[level=0] run say ARCE_FLUID_OLD_LAVA_UNCHANGED",
                      "ARCE_FLUID_OLD_LAVA_UNCHANGED")
                start = len(process.lines)
                query("arce fluids release-test report", r"\[minecraft/MinecraftServer\]: ARCE_FLUID_REPORT slot=3 ")
                reports = [line.strip() for line in process.lines[start:] if "ARCE_FLUID_REPORT" in line]
                for slot, gas in enumerate(("empty", "hydrogen", "oxygen", "nitrogen")):
                    require(any(f"slot={slot} item={NS}:{CANISTERS[slot]} count=16" in line
                                and f"fluid={'minecraft' if slot == 0 else NS}:{gas} amount={0 if slot == 0 else 1000}" in line
                                for line in reports), "Live canister capability differs after restart")
                for liquid, x in (("rocket_fuel", 136), ("enriched_lava", 140)):
                    query(f"execute if block {x} 80 132 {NS}:{liquid}[level=0] run say ARCE_FLUID_{liquid}_SOURCE",
                          f"ARCE_FLUID_{liquid}_SOURCE")
                receipt["reports"] = reports
            query("save-all flush", server.SAVE_MARKER.pattern, 300)
            commands.append("stop")
            process.command("stop")
            require(process.finish(300) == 0, "Packaged server did not stop cleanly")
            require(not server.scan_log(process.lines), "Unexpected packaged-server log findings")
            items = capture(runtime, directory, host == current)
            receipt.update(result="PASS", exit_code=0, log_counts=server.log_audit_counts(process.lines))
        except BaseException as error:
            receipt.update(result="FAIL", error=f"{type(error).__name__}: {error}")
            if process:
                process.abort()
            raise
        finally:
            write(directory / "receipt.json", receipt)
            write(directory / "commands.json", commands)
            for log in ("latest.log", "debug.log"):
                if (runtime / "logs" / log).exists():
                    shutil.copyfile(runtime / "logs" / log, directory / log)
        receipts.append(receipt)
        return items

    legacy = phase("v17-legacy-canisters", old, "old")
    upgraded = phase("v18-fluid-seed", current, "current")
    require(all(upgraded[slot] == item for slot, item in legacy.items()), "Upgrade changed legacy stacks")
    for name in ("restart-1", "restart-2"):
        require(phase(name, current) == upgraded, "Same-world restart rewrote canisters/buckets")
    require(worlds.inventory(args.source_world) == original_world, "Historical input world changed")
    require(server.digest_file(args.host_jar) == current["sha256"] and server.digest_file(args.v17_jar) == old["sha256"],
            "Input JAR changed during probe")
    if fixture:
        require(server.digest_file(args.fixture_jar) == fixture["sha256"], "Fixture JAR changed during probe")
    write(work / "summary.json", {"result": "PASS", "phases": receipts, "same_world_restarts": 2,
                                 "source_world_unchanged": True, "client_evidence": "NOT_PERFORMED",
                                 "required_gates_passed": False})
    print("PASS: legacy canister upgrade, liquid saved states, live capabilities and two same-world restarts")


if __name__ == "__main__":
    main()
