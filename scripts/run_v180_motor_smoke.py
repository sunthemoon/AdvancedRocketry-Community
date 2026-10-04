#!/usr/bin/env python3
"""Copy-only saved v1.7 casing upgrade and four motor identities over two restarts."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import run_v050_rocket_server_smoke as rocket
    from . import v120_precision_world_fixture as regions
    from . import v140_migration_fixture as worlds
else:
    import run_dedicated_server_smoke as server
    import run_v050_rocket_server_smoke as rocket
    import v120_precision_world_fixture as regions
    import v140_migration_fixture as worlds

NS = "advancedrocketrycommunity"
CHUNK = (8, 8)
CHEST = (132, 80, 132)
CASING = (133, 80, 132)
MOTORS = ("motor", "advanced_motor", "enhanced_motor", "elite_motor")
COUNTS = (1, 4, 8, 16)
FORCELOAD_RESULT = re.compile(r"force loaded|No chunks were marked for force loading")
FORCELOAD_QUERY = re.compile(r"Chunk at \[8, ?8\] in minecraft:overworld is marked for force loading")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def artifact(path: Path, version: str | None = None) -> dict:
    require(path.is_file() and not server.is_link_or_junction(path), "Artifact must be a regular file")
    path = path.resolve()
    with zipfile.ZipFile(path) as archive:
        metadata = archive.read("META-INF/mods.toml").decode("utf-8")
        if version:
            require(f'modId="{NS}"' in metadata and f'version="{version}"' in metadata,
                    "Artifact identity/version mismatch")
    return {"path": str(path), "name": path.name, "sha256": server.digest_file(path), "bytes": path.stat().st_size}


def block_at(chunk: dict, position: tuple[int, int, int]) -> dict:
    """Decode the exact 1.20.1 non-spanning packed palette cell, not palette presence."""
    x, y, z = position
    require((x >> 4, z >> 4) == CHUNK and (chunk.get("xPos"), chunk.get("zPos")) == CHUNK,
            "Fixture coordinate is outside the exact chunk")
    sections = [section for section in chunk.get("sections", []) if section.get("Y") == y >> 4]
    require(len(sections) == 1, "Fixture section is missing or duplicated")
    states = sections[0].get("block_states", {})
    palette = states.get("palette", [])
    require(isinstance(palette, list) and 1 <= len(palette) <= 4096, "Invalid block palette")
    if len(palette) == 1:
        require(not states.get("data"), "Single-valued palette has unexpected packed data")
        index = 0
    else:
        bits = max(4, (len(palette) - 1).bit_length())
        per_word = 64 // bits
        data = states.get("data", [])
        require(isinstance(data, list) and len(data) == (4096 + per_word - 1) // per_word,
                "Packed block array has the wrong size")
        cell = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        word = data[cell // per_word]
        require(type(word) is int and -(1 << 63) <= word < (1 << 63), "Invalid signed packed block word")
        index = ((word & ((1 << 64) - 1)) >> ((cell % per_word) * bits)) & ((1 << bits) - 1)
        require(index < len(palette), "Packed block cell is outside its palette")
    result = palette[index]
    require(isinstance(result, dict) and isinstance(result.get("Name"), str), "Invalid palette state")
    return result


def expected_items(current: bool) -> dict[int, dict]:
    expected = {0: {"Slot": 0, "id": f"{NS}:endgame_casing", "Count": 16,
                    "tag": {"marker": "legacy-casing-kept"}}}
    if current:
        for slot, (name, count) in enumerate(zip(MOTORS, COUNTS), 1):
            expected[slot] = {"Slot": slot, "id": f"{NS}:{name}", "Count": count,
                              "tag": {"marker": "C16a-07", "motor_tier": name}}
    return expected


def check_chunk(chunk: dict, current: bool) -> dict:
    require(block_at(chunk, CHEST) == {"Name": "minecraft:chest", "Properties": {
        "facing": "north", "type": "single", "waterlogged": "false"}}, "Fixture chest state changed")
    require(block_at(chunk, CASING) == {"Name": f"{NS}:endgame_casing"}, "Saved v1.7 casing block ID changed")
    chests = [entity for entity in chunk.get("block_entities", [])
              if (entity.get("x"), entity.get("y"), entity.get("z")) == CHEST]
    require(len(chests) == 1 and chests[0].get("id") == "minecraft:chest", "Fixture chest entity disappeared")
    stored = chests[0].get("Items", [])
    require(isinstance(stored, list) and all(isinstance(item, dict) and "Slot" in item for item in stored),
            "Invalid saved fixture inventory")
    items = {item["Slot"]: item for item in stored}
    require(len(items) == len(stored), "Duplicate saved inventory slots")
    require(items == expected_items(current), "Saved item ID, count or metadata changed")
    blocks = {"endgame_casing": block_at(chunk, CASING)}
    if current:
        for offset, name in enumerate(MOTORS, 2):
            position = (CHEST[0] + offset, CHEST[1], CHEST[2])
            state = block_at(chunk, position)
            require(state == {"Name": f"{NS}:{name}"}, "Motor block differs at its exact saved coordinate")
            blocks[name] = {"position": position, "state": state}
    return {"items": items, "blocks": blocks}


def capture(runtime: Path, evidence: Path, current: bool) -> dict:
    compressed = regions._region_chunk((runtime / "world/region/r.0.0.mca").read_bytes(), *CHUNK)
    chunk = regions._decode_chunk(compressed, *CHUNK)
    result = check_chunk(chunk, current)
    (evidence / "chunk-8-8.nbt.zlib").write_bytes(compressed)
    write(evidence / "saved-identities.json", result)
    write(evidence / "chunk-artifact.json", {"sha256": hashlib.sha256(compressed).hexdigest(), "bytes": len(compressed)})
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime-template", "source-world", "v17-jar", "host-jar", "work-root"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    work = args.work_root.resolve()
    require(not work.exists(), "Use a new disposable evidence directory")
    sources = [args.runtime_template, args.source_world, args.v17_jar, args.host_jar]
    if args.fixture_jar:
        sources.append(args.fixture_jar)
    for source in sources:
        resolved = source.resolve()
        require(not work.is_relative_to(resolved) and not resolved.is_relative_to(work),
                "Disposable runtime must be disjoint from every input")
    old = artifact(args.v17_jar, "1.20.1-1.7.0-dev")
    current = artifact(args.host_jar, "1.20.1-1.8.0-dev")
    fixture = artifact(args.fixture_jar) if args.fixture_jar else None
    runtime = work / "server"
    runtime.mkdir(parents=True)
    original_world = worlds.copy_world(args.source_world, runtime / "world")
    library_source = args.runtime_template / "libraries"
    require(library_source.is_dir() and not server.is_link_or_junction(library_source), "Unsafe runtime libraries")
    for child in library_source.rglob("*"):
        require(not server.is_link_or_junction(child), "Linked runtime library")
    shutil.copytree(library_source, runtime / "libraries")
    mods = runtime / "mods"
    mods.mkdir()
    if fixture:
        require(fixture["name"] not in {old["name"], current["name"]}, "Fixture name collides with host artifact")
        shutil.copyfile(fixture["path"], mods / fixture["name"])
        require(server.digest_file(mods / fixture["name"]) == fixture["sha256"], "Installed fixture JAR changed")
    properties = server.write_server_configuration(runtime, server.allocate_port(), True)
    command = rocket._server_command(args.java)
    command[2] = "-Xmx2G"
    write(work / "inputs.json", {"old": old, "current": current, "fixture": fixture,
                               "source_world": str(args.source_world.resolve()), "server_properties_sha256": properties})
    write(work / "source-world-files.json", original_world)
    receipts = []

    def phase(name: str, host: dict, seed: str | None = None) -> dict:
        directory = work / name
        directory.mkdir()
        for installed_host in (old, current):
            installed = mods / installed_host["name"]
            if installed.exists():
                installed.unlink()  # Only the exact host files installed in this new harness runtime.
        shutil.copyfile(host["path"], mods / host["name"])
        require(server.digest_file(mods / host["name"]) == host["sha256"], "Installed host JAR changed")
        write(directory / "launch.json", {"command": command, "cwd": str(runtime), "host": host})
        process = None
        commands = []
        receipt = {"phase": name, "result": "IN_PROGRESS", "host_sha256": host["sha256"]}
        try:
            process = server.CapturedProcess(command, runtime, directory / "stdout.txt")
            process.wait_for(server.READY_MARKER, 300)

            def query(text: str, marker: re.Pattern, timeout: int = 60):
                start = len(process.lines)
                commands.append(text)
                process.command(text)
                return process.wait_for(marker, timeout, start_at=start)

            query("forceload add 128 128", FORCELOAD_RESULT)
            query("forceload query 128 128", FORCELOAD_QUERY)
            query("execute if loaded 132 80 132 run say ARCE_MOTOR_FULL_LOADED", re.compile("ARCE_MOTOR_FULL_LOADED"))
            if seed == "legacy":
                query("setblock 132 80 132 minecraft:chest[facing=north,type=single,waterlogged=false] replace",
                      re.compile(r"Changed the block|Could not set the block"))
                query(f"setblock 133 80 132 {NS}:endgame_casing replace", re.compile(r"Changed the block|Could not set the block"))
                query('item replace block 132 80 132 container.0 with ' + NS + ':endgame_casing{marker:"legacy-casing-kept"} 16',
                      re.compile(r"Replaced a slot"))
            if seed == "current":
                for offset, (motor, count) in enumerate(zip(MOTORS, COUNTS), 2):
                    query(f"setblock {132 + offset} 80 132 {NS}:{motor} replace",
                          re.compile(r"Changed the block|Could not set the block"))
                    query('item replace block 132 80 132 container.' + str(offset - 1) + ' with ' + NS + ':' + motor
                          + '{marker:"C16a-07",motor_tier:"' + motor + '"} ' + str(count), re.compile(r"Replaced a slot"))
            for offset, block in [(1, "endgame_casing")] + (list(enumerate(MOTORS, 2)) if host == current else []):
                query(f"execute if block {132 + offset} 80 132 {NS}:{block} run say ARCE_MOTOR_EXACT_{block}",
                      re.compile("ARCE_MOTOR_EXACT_" + block))
            query("save-all flush", server.SAVE_MARKER, 300)
            commands.append("stop")
            process.command("stop")
            require(process.finish(300) == 0, "Packaged server did not stop cleanly")
            require(not server.scan_log(process.lines), "Unexpected packaged-server log findings")
            result = capture(runtime, directory, host == current)
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
        return result

    legacy = phase("v17-saved-casing", old, "legacy")
    upgraded = phase("v18-motor-seed", current, "current")
    require(upgraded["items"][0] == legacy["items"][0], "Upgrade changed the retained casing item")
    for name in ("restart-1", "restart-2"):
        require(phase(name, current) == upgraded, "Same-world restart changed saved motor/casing identities")
    require(worlds.inventory(args.source_world) == original_world, "Historical input world changed")
    for bound in (old, current, fixture):
        if bound:
            require(server.digest_file(Path(bound["path"])) == bound["sha256"], "Input JAR changed during probe")
    write(work / "summary.json", {"result": "PASS", "phases": receipts, "same_world_restarts": 2,
                                 "source_world_unchanged": True, "client_evidence": "NOT_PERFORMED",
                                 "classic_machine_formation": "NOT_TESTED", "required_gates_passed": False})
    print("PASS: saved v1.7 casing upgrade, four exact motor block/item identities and two same-world restarts")


if __name__ == "__main__":
    main()
