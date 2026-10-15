#!/usr/bin/env python3
"""Create a registry-derived v1.7 world on an unmodified, packaged dedicated host.

Only existing console/release-test commands mutate the world. Forge REGISTRYDUMP
is the registry authority; native chunk NBT is the saved-content authority.
The caller holds the heavy-JVM lock and owns/cleans the disposable server.
"""
from __future__ import annotations

import gzip
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import v120_precision_world_fixture as region
    from .inspect_celestial_saved_data import NbtReader
else:
    import run_dedicated_server_smoke as server
    import v120_precision_world_fixture as region
    from inspect_celestial_saved_data import NbtReader

NS = "advancedrocketrycommunity"
OLD_COMMIT = "55da6a58842762c382edce0a5a842d06bb76ff6e"
OWNER = "00690000-0000-4000-8000-000000000001"
RIDER = "00690000-0000-4000-8000-000000000002"
FORGE = "1.20.1-47.4.10"
REGISTRIES = ("minecraft:block", "minecraft:item", "minecraft:block_entity_type")
MAX_BYTES = 16 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise server.SmokeError(message)


def write(path: Path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_nbt(path: Path):
    require(path.is_file() and not server.is_link_or_junction(path)
            and path.stat().st_size <= MAX_BYTES, f"Missing/unsafe/oversized NBT: {path}")
    with gzip.open(path, "rb") as stream:
        payload = stream.read(MAX_BYTES + 1)
    require(len(payload) <= MAX_BYTES, "Expanded NBT exceeds fixture bound")
    return NbtReader(payload, max_tags=200_000).read_root()


def registry_dump(text: str) -> dict:
    """Read Forge's frozen runtime tables, never infer IDs from Java source or assets."""
    result = {}
    for part in text.split("Registry Name: ")[1:]:
        name, _, body = part.partition("\n")
        name = name.strip()
        if name not in REGISTRIES:
            continue
        # A table row is '| numeric ID | resource key | value |'. Stop at the next log entry.
        body = re.split(r"\n\[", body, maxsplit=1)[0]
        keys = re.findall(r"\|\s*\d+\s*\|\s*(advancedrocketrycommunity:[a-z0-9_./-]+)\s*\|", body)
        require(len(keys) == len(set(keys)), f"Duplicate runtime registry key: {name}")
        keys = sorted(keys)
        if keys:
            if name in result:
                require(result[name] == keys, f"Runtime registry changed: {name}")
            result[name] = keys
    require(set(result) == set(REGISTRIES), "Incomplete Forge runtime registry dump")
    require(all(len(v) <= 1024 for v in result.values()), "Registry fixture exceeds 1024 entries")
    return result


def layout(registries: dict) -> dict:
    blocks = [{"pos": [128 + (i % 12) * 4, 100, 128 + (i // 12) * 4], "id": identity}
              for i, identity in enumerate(registries["minecraft:block"])]
    items = [{"pos": [128 + (i // 27) * 4, 100, 240], "slot": i % 27,
              "id": identity, "count": 1} for i, identity in enumerate(registries["minecraft:item"])]
    require(len(blocks) <= 300, "Block layout would overlap the container row")
    return {"registries": registries, "blocks": blocks, "items": items}


def prepare(template: Path, target: Path, jar: Path, world: Path | None = None):
    require(not target.exists(), "Server directory must be new")
    libraries = template / "libraries"
    require(libraries.is_dir(), "Missing Forge template libraries")
    require(not any(server.is_link_or_junction(p) for p in [libraries, *libraries.rglob("*")]),
            "Linked template libraries")
    target.mkdir(parents=True)
    shutil.copytree(libraries, target / "libraries")
    (target / "mods").mkdir()
    shutil.copy2(jar, target / "mods" / jar.name)
    require(server.digest_file(jar) == server.digest_file(target / "mods" / jar.name), "Installed jar differs")
    (target / "eula.txt").write_text("eula=true\n", encoding="ascii")
    (target / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={server.allocate_port()}\nonline-mode=false\n"
        "level-name=world\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}\n'
        "generate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-players=1\n"
        "spawn-protection=0\nsync-chunk-writes=true\nenable-rcon=false\ndifficulty=peaceful\n",
        encoding="ascii")
    if world:
        before = file_inventory(world)
        shutil.copytree(world, target / "world")
        require(before == file_inventory(world) == file_inventory(target / "world"), "World copy differs")


def file_inventory(directory: Path) -> list:
    result, total = [], 0
    for path in sorted(directory.rglob("*")):
        require(not server.is_link_or_junction(path), "Linked fixture input")
        if path.is_file():
            size = path.stat().st_size
            total += size
            require(total <= 512 * 1024**2, "World exceeds copy budget")
            result.append({"path": path.relative_to(directory).as_posix(), "bytes": size,
                           "sha256": server.digest_file(path)})
    require(result, "Empty fixture directory")
    return result


def query(process, command: str, marker: str, timeout=60) -> str:
    start = len(process.lines)
    process.w69_commands.append(command)
    process.command(command)
    index = process.wait_for(re.compile(marker), timeout, start_at=start)
    return process.lines[index].strip()


def launch(java: str, runtime: Path, evidence: Path, name: str, action=None) -> dict:
    evidence.mkdir(parents=True, exist_ok=True)
    args = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
    command = [java, "-Xms512M", "-Xmx2G", "-Dadvancedrocketrycommunity.releaseTestHooks=true",
               "-Dforge.logging.marker.registrydump=ACCEPT", "-Dforge.logging.console.level=info",
               f"@libraries/net/minecraftforge/forge/{FORGE}/{args}", "nogui"]
    receipt = {"command": command, "cwd": str(runtime), "result": "FAIL", "console_commands": []}
    process = None
    try:
        process = server.CapturedProcess(command, runtime, evidence / f"{name}.log")
        process.w69_commands = receipt["console_commands"]
        process.wait_for(server.READY_MARKER, 300)
        if action:
            action(process)
        receipt["operator_report"] = query(process, "arce beta data-report", r"ARCE-BETA-.*operational=")
        require("operational=true" in receipt["operator_report"], "Managed root refused at runtime")
        query(process, "save-all flush", server.SAVE_MARKER.pattern)
        process.w69_commands.append("stop")
        process.command("stop")
        receipt["exit_code"] = process.finish()
        require(receipt["exit_code"] == 0, "Server did not stop normally")
        errors = [line.strip() for line in process.lines if re.search(r"/(?:ERROR|FATAL)\]", line)]
        receipt["errors"] = errors
        require(not errors, "Native server emitted ERROR/FATAL: " + "; ".join(errors[:3]))
        receipt["result"] = "PASS"
        return receipt
    except Exception as error:
        receipt["failure"] = str(error)
        raise
    finally:
        if process:
            process.abort()
        debug = runtime / "logs/debug.log"
        if debug.exists():
            shutil.copy2(debug, evidence / f"{name}-debug.log")
        write(evidence / f"{name}-process.json", receipt)


def seed(process, plan: dict):
    query(process, "gamerule randomTickSpeed 0", "randomTickSpeed")
    query(process, "forceload add 128 128 191 255", "force load")
    for block in plan["blocks"]:
        x, y, z = block["pos"]
        # Glass cells contain fluid blocks and support falling blocks without touching other IDs.
        query(process, f"fill {x-1} {y-1} {z-1} {x+1} {y} {z+1} minecraft:glass", "filled")
        query(process, f"setblock {x} {y} {z} {block['id']}", "Changed the block")
    chests = sorted({tuple(item["pos"]) for item in plan["items"]})
    for x, y, z in chests:
        query(process, f"setblock {x} {y} {z} minecraft:chest", "Changed the block")
    for item in plan["items"]:
        pos = " ".join(map(str, item["pos"]))
        query(process, f"item replace block {pos} container.{item['slot']} with {item['id']} 1", "Replaced")
    query(process, "arce satellite release-test terminal 256 100 256 setup " + OWNER,
          r"ARCE_RELEASE_TEST setup .*status=READY")
    query(process, f"arce endgame release-test elevator build 288 100 288 {OWNER} {RIDER}",
          r"ARCE_RELEASE_TEST elevator built")
    query(process, "arce endgame release-test persist root", r"ARCE_RELEASE_TEST persist root")
    # A completed real flight materializes both rocket journals; no offline NBT injection.
    for (x, y, z), block in [((384,100,384), "rocket_assembler"), ((383,101,384), "rocket_fuel_tank"),
                              ((384,101,384), "rocket_motor"), ((384,102,384), "rocket_seat"),
                              ((384,103,384), "guidance_computer")]:
        if y == 100:
            query(process, "forceload add 368 368 400 400", "force load")
        query(process, f"setblock {x} {y} {z} {NS}:{block}", "Changed the block")
    line = query(process, "arce rocket assemble 384 100 384", r"ARCE_ROCKET_TRANSACTION operation=assembly code=SUCCESS")
    entity = re.search(r"entity=([a-f0-9-]{36})", line).group(1)
    query(process, f"arce rocket release-test refuel {entity}", r"Release-test rocket fuel=")
    landed = query(process, f"arce rocket release-test launch {entity} moon",
                   r"ARCE_TRANSFER_PHASE .*event=landing_complete", 60)
    destination = re.search(r"entity=([a-f0-9-]{36})", landed).group(1)
    query(process, f"execute in {NS}:moon run arce rocket release-test report {destination}", "ARCE_RELEASE_TEST_FLIGHT_REPORT")


def block_at(chunk: dict, pos: list) -> dict:
    x, y, z = pos
    sections = [entry for entry in chunk["sections"] if entry["Y"] == y >> 4]
    require(len(sections) == 1, f"Missing/duplicate section at {pos}")
    states = sections[0]["block_states"]
    palette = states["palette"]
    require(1 <= len(palette) <= 4096, "Invalid palette")
    index = 0
    if len(palette) > 1:
        bits = max(4, (len(palette) - 1).bit_length())
        per_word = 64 // bits
        require(len(states["data"]) == (4096 + per_word - 1) // per_word, "Malformed packed palette")
        cell = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        index = ((states["data"][cell // per_word] & ((1 << 64) - 1)) >> ((cell % per_word) * bits)) & ((1 << bits) - 1)
    require(index < len(palette), "Palette index out of range")
    return palette[index]


def capture(world: Path, plan: dict, target: Path) -> dict:
    target.mkdir()
    chunks = {}
    positions = [row["pos"] for key in ("blocks", "items") for row in plan[key]]
    for cx, cz in sorted({(p[0] >> 4, p[2] >> 4) for p in positions}):
        path = world / f"region/r.{cx >> 5}.{cz >> 5}.mca"
        raw = region._read_regular(path, region.MAX_REGION_BYTES)
        compressed = region._region_chunk(raw, cx, cz)
        chunks[cx, cz] = region._decode_chunk(compressed, cx, cz)
        (target / f"chunk-{cx}-{cz}.nbt.zlib").write_bytes(compressed)
    result = {"blocks": [], "items": [], "roots": {}, "registries": plan["registries"]}
    def entities(pos):
        return [row for row in chunks[pos[0] >> 4, pos[2] >> 4].get("block_entities", [])
                if [row.get(axis) for axis in ("x", "y", "z")] == pos]
    for row in plan["blocks"]:
        pos = row["pos"]
        be = entities(pos)
        require(len(be) <= 1, "Duplicate saved block entity")
        state = block_at(chunks[pos[0] >> 4, pos[2] >> 4], pos)
        result["blocks"].append({"pos": pos, "id": state["Name"], "state": state,
                                 "block_entity": be[0]["id"] if be else None})
    for pos in sorted({tuple(row["pos"]) for row in plan["items"]}):
        chests = entities(list(pos))
        require(len(chests) == 1 and chests[0]["id"] == "minecraft:chest", "Fixture container disappeared")
        for item in chests[0].get("Items", []):
            result["items"].append({"pos": list(pos), "slot": item["Slot"], "id": item["id"],
                                    "count": item["Count"], "tag": item.get("tag", {})})
    for file in sorted((world / "data").glob(NS + "_*.dat")):
        data = read_nbt(file)["data"]
        require(type(data.get("schema_version")) is int, "Unversioned managed root: " + file.name)
        shutil.copy2(file, target / file.name)
        result["roots"][file.stem.removeprefix(NS + "_")] = {"status": "loaded", "data": data}
    write(target / "inventory.json", result)
    return result


def create(java: str, runtime: Path, evidence: Path) -> tuple[dict, dict]:
    plan = {}
    def populate(process):
        plan.update(layout(registry_dump((runtime / "logs/debug.log").read_text(encoding="utf-8"))))
        write(evidence / "layout.json", plan)
        seed(process, plan)
    launch(java, runtime, evidence, "v17-create", populate)
    inventory = capture(runtime / "world", plan, evidence / "v17-saved")
    require([(row["pos"], row["id"]) for row in inventory["blocks"]]
            == [(row["pos"], row["id"]) for row in plan["blocks"]], "v1.7 did not retain every placed block")
    require([{k: row[k] for k in ("pos", "slot", "id", "count")} for row in inventory["items"]]
            == plan["items"], "v1.7 did not retain every item exactly once")
    require({row["block_entity"] for row in inventory["blocks"] if row["block_entity"]}
            == set(plan["registries"]["minecraft:block_entity_type"]), "Incomplete block entity coverage")
    require(set(inventory["roots"]) >= {"celestial", "rocket_transactions", "rocket_transfers", "stations",
                                       "satellite_missions", "endgame"}, "Missing persisted v1.7 root")
    require(inventory["roots"]["stations"]["data"]["stations"], "No station fixture")
    satellite = inventory["roots"]["satellite_missions"]["data"]
    require(all(satellite[key] for key in ("satellites", "missions", "research_accounts", "instances")),
            "Missing representative satellite/research state")
    require(inventory["roots"]["endgame"]["data"]["endpoints"], "No endgame fixture")
    return plan, inventory
