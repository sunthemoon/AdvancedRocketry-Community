"""K17: the combined docs/17 reference workload world on C6 and its C4 schedule.

One world holds 16 supplied sealed rooms, ten station records, 100+ mission records,
three rocket pads (2,048, 64 and 64 blocks) and the schedule that exercises them.
Only Minecraft writes saves: the driver sends console commands and reads closed
worlds. No ``data merge``/``data modify`` or other NBT write is used (R638).

  plan      write the deterministic layout, commands, schedule and client steps
  build     one hooked server: build on a copy of K09's missions-101, then seal
  readback  one server without test properties: load a copy, compare readbacks
  soak      the schedule: samplers, saves, one restart, accounting before/after

Each server start is one WORKER-RULES heavy-JVM hold taken by the caller. Windows
figures are a qualification of the tooling, never REF evidence.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys
import time
import tomllib
from uuid import UUID

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_observe import raw_ticks
from scripts.campaign_observe.common import schedule as poll_schedule, utc_now
from scripts.campaign_prep import k03_chain as chain
from scripts.campaign_prep import k04_rockets as k04
from scripts.campaign_prep import k16_core as common

NS, OW, MOON, SPACE = common.NS, common.OW, common.MOON, common.SPACE
FIXTURES = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures"
OUTPUT = FIXTURES / "K17"
PARENT = FIXTURES / "K09/missions-101"
PARENTS = ("K09/missions-101", "K08/ten", "K04/2048", "K03/FI07", "K03/FI06")
HOOK_PROPERTY = "-Dadvancedrocketrycommunity.releaseTestHooks=true"
STATION_OWNER = "04520000-0000-4000-8000-000000000001"
ROOM_CHUNK, ROOM_Y, ROOM_SIZE = (64, 64), 200, 16
PADS = {"p2048": (2048, (4096, 100, 4096)), "pad-a": (64, (4160, 100, 4096)), "pad-b": (64, (4192, 100, 4096))}
FREE_REGIONS = ("region/r.8.8.mca", f"dimensions/{NS}/moon/region/r.2.2.mca")
CANISTERS, COAL_BLOCKS, FUEL_CELLS = 80, 64, 64
ATMOSPHERE = dict(maxVolumeCells=4096, maxInspectionsPerLevelTick=1024)
FAILURES = ("Unknown or incomplete command", "Incorrect argument", "Unknown block", "Unknown item",
            "Expected whitespace", "does not have property", "is not a container", "Could not set the block",
            "That position is not loaded", "That position is out of this world",
            "Station creation failed", "Rocket request rejected")
NBT_WRITES = ("data merge", "data modify", "data remove")


def pos(values):
    return " ".join(map(str, values))


def rooms():
    """Sixteen chunk-aligned 16x16x16 shells: 4,096 blocks of footprint, 2,744 interior cells each."""
    result = []
    for index in range(16):
        x0, z0 = (ROOM_CHUNK[0] + index % 4) * 16, (ROOM_CHUNK[1] + index // 4) * 16
        vx, vy, vz = x0 + 7, ROOM_Y + 2, z0 + 7
        result.append(dict(index=index, chunk=[x0 >> 4, z0 >> 4], box=[[x0, ROOM_Y, z0], [x0 + 15, ROOM_Y + 15, z0 + 15]],
            footprint_blocks=ROOM_SIZE ** 3, interior_cells=(ROOM_SIZE - 2) ** 3,
            vent=[vx, vy, vz], scan_seed=[vx, vy + 1, vz], empties_hopper=[vx, vy - 1, vz],
            canister_hopper=[vx - 1, vy, vz], generator=[vx + 1, vy, vz], fuel_hopper=[vx + 1, vy + 1, vz],
            boundary=[x0 + 3, ROOM_Y + 15, z0 + 3]))
    return result


def layout():
    pads = {}
    for name, (size, origin) in PADS.items():
        rocket = k04.rocket(size, origin)
        rocket.pop("blocks")
        x, y, z = origin
        pads[name] = dict(rocket, chest=None if size == 2048 else [x - 3, y, z],
                          loader=None if size == 2048 else rocket["loader"],
                          forceload=[x - 16, z - 16, x + 31, z + 31])
    return dict(schema_version=1, candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
        rooms=dict(dimension=MOON, shell="minecraft:iron_block", footprint_blocks=ROOM_SIZE ** 3,
                   footprint_definition="The whole 16x16x16 bounding box of a room, shell included; the 14x14x14 interior "
                   "holds the five devices, so the scanned volume is at most 2,744 cells.",
                   boundary_block="minecraft:oak_trapdoor[half=top,open=false] in the roof", items=rooms(),
                   forced_chunks=[r["chunk"] for r in rooms()]),
        pads=dict(dimension=OW, items=pads), stations=dict(total=10, owner_base=STATION_OWNER),
        missions=dict(minimum_records=100, batch=f"arce satellite release-test batch 100 32"),
        free_regions=list(FREE_REGIONS))


def room_commands(room):
    p = f"execute in {MOON} run "
    (x0, y0, z0), (x1, y1, z1) = room["box"]
    return [p + f"fill {x0} {y0} {z0} {x1} {y1} {z1} minecraft:iron_block hollow",
            p + f"setblock {pos(room['empties_hopper'])} minecraft:hopper[facing=down]",
            p + f"setblock {pos(room['vent'])} {NS}:oxygen_vent",
            p + f"setblock {pos(room['generator'])} {NS}:combustion_generator",
            p + f"setblock {pos(room['fuel_hopper'])} minecraft:hopper[facing=down]",
            p + f"setblock {pos(room['canister_hopper'])} minecraft:hopper[facing=east]",
            p + f"setblock {pos(room['boundary'])} minecraft:oak_trapdoor[half=top,open=false]",
            p + f"item replace block {pos(room['fuel_hopper'])} container.0 with minecraft:coal_block {COAL_BLOCKS}",
            *[p + f"item replace block {pos(room['canister_hopper'])} container.{slot} with {NS}:oxygen_canister 16"
              for slot in range(CANISTERS // 16)]]


def pad_commands(name):
    size, origin = PADS[name]
    rocket = k04.rocket(size, origin)
    commands = [c for c in k04.build_commands(dict(rockets=[rocket]))
                if not c.startswith(("gamerule", "time ", "weather"))]
    if size != 2048:
        x, y, z = origin
        commands += [f"setblock {pos(rocket['loader'])} {NS}:fuel_loader", f"setblock {x-3} {y} {z} minecraft:chest"]
        commands += [f"item replace block {x-3} {y} {z} container.{slot} with {NS}:rocket_fuel_cell 16"
                     for slot in range(FUEL_CELLS // 16)]
    return commands


def station_plan(existing):
    count = 10 - len(existing)
    chain.require(0 <= count <= 10, "Parent already holds more than ten stations")
    owners = [str(UUID(int=UUID(STATION_OWNER).int + 256 + i)) for i in range(count)]
    chain.require(not set(owners) & set(existing), "Station owner already used")
    return [dict(owner=owner, orbit=NS + ":earth", name=f"K17 station {i:02}") for i, owner in enumerate(owners)]


def build_commands():
    result = [f"execute in {MOON} run forceload add {ROOM_CHUNK[0]*16} {ROOM_CHUNK[1]*16} "
              f"{ROOM_CHUNK[0]*16+63} {ROOM_CHUNK[1]*16+63}"]
    for room in rooms():
        result += room_commands(room)
    for name in PADS:
        result += pad_commands(name)
    return result


def plan(args):
    """C4 schedule in sampled seconds. Restart downtime is never counted as sampled time."""
    total, save, restart, warmup = args.sample_seconds, args.save_interval, args.restart_at, args.warmup
    chain.require(all(math.isfinite(v) and v > 0 for v in (total, save, restart, warmup)), "Positive finite schedule required")
    chain.require(restart < total <= 86400, "The restart must fall inside the sampled time")
    reference = (total, save, restart, warmup) == (14400, 1800, 7200, 120)
    chain.require(reference or args.qualification, "A changed schedule needs --qualification")
    hooks = bool(args.hooks)
    actions = [dict(id="assemble-2048", at=total * .125, mode="console", command=f"arce rocket assemble {pos(layout()['pads']['items']['p2048']['assembler'])}"),
               dict(id="a11-open-restore", at=total * .25, mode="console", rooms=[0, 5]),
               dict(id="flights-a-b", at=total * .3, mode="hook" if hooks else "client", actors=["A", "B"]),
               dict(id="disassemble-2048", at=total * .4, mode="hook" if hooks else "client", actors=["A"]),
               dict(id="refill-missions", at=total * .45, mode="client", actors=["A", "B"])]
    return dict(schema_version=1, classification="QUALIFICATION_NOT_MEASUREMENT" if args.qualification else "CAMPAIGN_PROCEDURE_NO_GATE_VERDICT",
        source="docs/17 section 4; plan choices C4 and C10", sampled_seconds=total, warmup_seconds=warmup,
        flushed_save_interval_seconds=save, restart_at_sampled_seconds=restart,
        segments=[dict(index=1, sampled_seconds=restart), dict(index=2, sampled_seconds=total - restart)],
        vanilla_autosave="left on; the driver never sends save-off",
        test_properties=[HOOK_PROPERTY] if hooks else [],
        tick_source="K15 raw ticks (needs the release-test property)" if hooks else
        "forge tps 100-tick means only; K15's raw per-tick command does not exist without the release-test property",
        intervals=dict(tick=1 if hooks else 5, rss=1, heap=30, components=args.component_interval),
        actions=actions, client_steps=client_steps())


def client_steps():
    """Run-time actions that need a player. Written for accounts A and B (O7); none is executed here."""
    items = layout()
    a, b, big = (items["pads"]["items"][k] for k in ("pad-a", "pad-b", "p2048"))
    room = items["rooms"]["items"][0]
    def flight(actor, pad):
        return [f"{actor} stands at the pad's Fuel Loader {pad['loader']} in {OW}, uses it once with an empty hand (this claims it for {actor}), "
                f"takes rocket fuel cells from the chest at {pad['chest']} and uses them on the loader one at a time after the rocket exists.",
                f"{actor} opens the Rocket Assembler at {pad['assembler']} and assembles through its screen, so the 64-block rocket belongs to {actor}. "
                "A rocket assembled by the console belongs to the console owner and no player's loader can fuel it.",
                f"{actor} waits for FUELED (one flight to the Moon needs 1,642 of 16,000 units; each cell gives 500), boards the seat at {pad['seat_blocks'][0]}, "
                "opens the flight screen and launches to the Moon. This flight is also the dimension transfer.",
                f"On the Moon {actor} launches back to Earth with the remaining fuel (1,642 units) and leaves the rocket landed."]
    return dict(status="SCRIPTED_ONLY_NOT_EXECUTED", accounts="A and B are the two real connections of O7; names and UUIDs are parameters",
        connect=["A and B join before the warm-up and stay connected for the sampled time; both rejoin after the restart.",
                 f"B travels to the Moon rooms (chunk {ROOM_CHUNK} to {[ROOM_CHUNK[0]+3, ROOM_CHUNK[1]+3]}, y {ROOM_Y}) and runs /arce atmosphere status "
                 "at each component sample: it prints vents, providers, scans, pending, volumes, cells, dirty and inspections. The command needs a player."],
        flights=dict(when="at 0.3 of the sampled time and again in the second half, A and B launching within ten seconds of each other",
                     A=flight("A", a), B=flight("B", b)),
        assembly_2048=[f"A opens the Rocket Assembler at {big['assembler']} and assembles the 2,048-block structure through its screen (A10's normal path). "
                       "Without a player the driver sends the ordinary operator command arce rocket assemble instead.",
                       "A requests disassembly from the rocket and confirms with /arce rocket-disassembly confirm <id from the chat prompt>. "
                       "The owner, a creative player or an operator may do this; the console cannot."],
        missions=["Every mission lasts at most 72,000 ticks (one hour); data missions last 200 ticks and surveys 6,000. No mission stays ACTIVE for four hours.",
                  "At each half hour A and B each launch new missions from their own powered terminals, preferring asteroid and gas missions, "
                  "whose durations are the longest, and record each deadline from /arce satellite list.",
                  "The driver records active, ready and claimed counts from arce satellite admin evidence at every component sample."],
        a11=[f"The boundary of room 0 is the oak trapdoor at {room['boundary']} in {MOON}; every room's boundary cell is in the layout.",
             "A player opens it with a right click and closes it the same way. The driver can do the same with setblock [open=true] and [open=false], "
             "which changes a block state and no NBT."])


def forced_longs(values):
    """ChunkPos.toLong: low 32 bits x, high 32 bits z."""
    def signed(v):
        v &= 0xffffffff
        return v - (1 << 32) if v & 0x80000000 else v
    return sorted([signed(v), signed(v >> 32)] for v in values)


def forced_reply(lines):
    return sorted([int(x), int(z)] for x, z in re.findall(r"\[(-?\d+),\s*(-?\d+)\]", " ".join(lines)))


def parse_tps(lines):
    match = [re.search(r"Overall\s*:\s*Mean tick time: ([\d.,]+) ms\. Mean TPS: ([\d.,]+)", line) for line in lines]
    match = [m for m in match if m]
    chain.require(len(match) == 1, "Expected one overall forge tps line")
    return dict(mean_tick_ms=float(match[0][1].replace(",", ".")), mean_tps=float(match[0][2].replace(",", ".")))


def parse_fields(lines, marker):
    found = [line.split(marker, 1)[1] for line in lines if marker in line]
    chain.require(bool(found), "Missing readback " + marker)
    return {k: (int(v) if re.fullmatch(r"-?\d+", v) else v) for k, v in re.findall(r"(\w+)=([^\s]+)", found[0])}


def gc_summary(text):
    pauses = [float(v) for v in re.findall(r"\bPause [^\n]*? (\d+\.\d+)ms", text)]
    return dict(pause_count=len(pauses), pause_total_ms=sum(pauses), pause_max_ms=max(pauses, default=None),
                basis="-Xlog:gc* pause lines of one server lifetime")


def series_summary(samples):
    """Observations for the comparison across the restart; no verdict."""
    values = [float(v) for _, v in samples]
    chain.require(bool(values), "Empty series")
    slope = None
    if len(samples) > 1 and samples[-1][0] > samples[0][0]:
        mt, mv = statistics.fmean(t for t, _ in samples), statistics.fmean(values)
        slope = sum((t - mt) * (v - mv) for t, v in samples) / sum((t - mt) ** 2 for t, _ in samples)
    return dict(count=len(values), first=values[0], last=values[-1], minimum=min(values), maximum=max(values),
                median=statistics.median(values), slope_per_hour=None if slope is None else slope * 3600)


def items_of(entity):
    counts = {}
    for item in (entity or {}).get("Items", []):
        counts[item["id"]] = counts.get(item["id"], 0) + int(item["Count"])
    return counts


def accounting_from(entities, plan_layout):
    """Pure projection of closed block-entity roots, keyed by (dimension, x, y, z)."""
    def at(dimension, position):
        return entities.get((dimension, *position))
    canister, empty, cell = NS + ":oxygen_canister", NS + ":empty_canister", NS + ":rocket_fuel_cell"
    result = dict(rooms=[], pads={})
    for room in plan_layout["rooms"]["items"]:
        vent = (at(MOON, room["vent"]) or {}).get("arce_oxygen_vent")
        burn = (at(MOON, room["generator"]) or {}).get("arce_combustion_generator")
        chain.require(vent is not None and burn is not None, f"Room {room['index']} devices missing on disk")
        feed, out, fuel = (items_of(at(MOON, room[k])) for k in ("canister_hopper", "empties_hopper", "fuel_hopper"))
        full = feed.get(canister, 0) + vent["oxygen_canisters"]
        empties = out.get(empty, 0) + vent["empty_canisters"]
        slot = burn["fuel"]
        coal = fuel.get("minecraft:coal_block", 0) + (int(slot["Count"]) if slot.get("id") == "minecraft:coal_block" else 0)
        result["rooms"].append(dict(index=room["index"], full_canisters=full, empty_canisters=empties,
            canister_shells=full + empties, oxygen_units=vent["oxygen_units"], oxygen_potential=full * 1000 + vent["oxygen_units"],
            vent_energy=vent["energy"], coal_blocks=coal, generator_energy=burn["energy"], burn_remaining=burn["remaining"]))
    for name, pad in plan_layout["pads"]["items"].items():
        if pad["chest"]:
            loader = (at(OW, pad["loader"]) or {}).get("arce_fuel_loader")
            result["pads"][name] = dict(fuel_cells=items_of(at(OW, pad["chest"])).get(cell, 0), loader=loader)
    result["totals"] = {key: sum(r[key] for r in result["rooms"]) for key in
                        ("full_canisters", "empty_canisters", "canister_shells", "oxygen_units", "oxygen_potential",
                         "vent_energy", "coal_blocks", "generator_energy")}
    result["totals"]["fuel_cells"] = sum(p["fuel_cells"] for p in result["pads"].values())
    result["totals"]["fluids_mb"] = 0  # K17 places no fluid device; inherited roots are hashed in `identities`.
    return result


def reconcile(before, after, *, players=False):
    """Conservation between two closed worlds. Legitimate use only ever lowers a store."""
    findings = []
    for old, new in zip(before["rooms"], after["rooms"], strict=True):
        if old["canister_shells"] != new["canister_shells"]:
            findings.append(f"room {old['index']}: canister shells {old['canister_shells']} -> {new['canister_shells']}")
        for key in ("oxygen_potential", "coal_blocks"):
            if new[key] > old[key]:
                findings.append(f"room {old['index']}: {key} rose {old[key]} -> {new[key]}")
    for name, old in before["pads"].items():
        new = after["pads"][name]["fuel_cells"]
        if new > old["fuel_cells"] or (not players and new != old["fuel_cells"]):
            findings.append(f"{name}: fuel cells {old['fuel_cells']} -> {new}")
    debit = {key: before["totals"][key] - after["totals"][key] for key in ("oxygen_potential", "coal_blocks", "fuel_cells")}
    return dict(status="RECONCILED" if not findings else "UNEXPLAINED", findings=findings, debit=debit,
                rule="shells conserved per room; oxygen, coal and fuel cells never rise; fuel cells unchanged without players")


def region_chunks(path):
    """Occupied chunk slots of one closed region file as (cx, cz, compressed record)."""
    from scripts import v120_precision_world_fixture as region
    raw = region._read_regular(path, region.MAX_REGION_BYTES)
    rx, rz = map(int, path.stem.split(".")[1:])
    for slot in range(1024):
        if int.from_bytes(raw[slot*4:slot*4+3], "big"):
            cx, cz = rx * 32 + slot % 32, rz * 32 + slot // 32
            yield cx, cz, region._region_chunk(raw, cx, cz)


def dimension_of(world, path):
    parts = path.relative_to(world).parts
    return OW if parts[0] != "dimensions" else f"{parts[1]}:{parts[2]}"


def block_entities(world):
    from scripts import v120_precision_world_fixture as region
    result = {}
    for path in sorted(world.rglob("region/r.*.*.mca")):
        dimension = dimension_of(world, path)
        for cx, cz, record in region_chunks(path):
            for entity in region._decode_chunk(record, cx, cz).get("block_entities", []):
                result[(dimension, entity["x"], entity["y"], entity["z"])] = entity
    return result


def rocket_rows(world):
    """Rocket entities of a closed world. A 2,048-block snapshot has far more than the shared reader's 4,096 tags,
    so this read uses that reader's own maximum; a chunk still out of bounds is listed, never skipped silently."""
    import zlib
    from scripts import inspect_celestial_saved_data as nbt
    rockets, unreadable = [], []
    for path in sorted(world.rglob("entities/r.*.*.mca")):
        for cx, cz, record in region_chunks(path):
            name = f"{path.relative_to(world).as_posix()}#{cx},{cz}"
            try:
                stream = zlib.decompressobj()
                expanded = stream.decompress(record, nbt.MAX_DECOMPRESSED_BYTES + 1)
                chain.require(len(expanded) <= nbt.MAX_DECOMPRESSED_BYTES and stream.eof, "Entity chunk exceeds the read bound")
                root = nbt.NbtReader(expanded, max_tags=1_048_576).read_root()
            except ValueError as failure:
                unreadable.append(dict(chunk=name, reason=str(failure)))
                continue
            for entity in root.get("Entities", []):
                if entity.get("id") == NS + ":rocket":
                    flight = entity.get(k04.ROOT_KEY, {}).get("flight_data", {})
                    fuel = flight.get("fuel") or {}
                    rockets.append(dict(entity=next(iter(chain.saved_uuids(dict(v=entity["UUID"])))), chunk=name, state=flight.get("state"),
                                        fuel=dict(amount=fuel.get("amount", 0), capacity=fuel.get("capacity", 0))))
    return sorted(rockets, key=lambda r: r["entity"]), unreadable


def identities(world, entities):
    from scripts import v170_world_fixture as fixture
    from scripts.campaign_prep import k08_stations as stations
    roots = {path.name: fixture.read_nbt(path)["data"] for path in sorted((world / "data").glob(NS + "_*.dat"))}
    missions = roots[NS + "_satellite_missions.dat"]
    status = {}
    for mission in missions["missions"]:
        key = mission["kind"] + ":" + mission["status"]
        status[key] = status.get(key, 0) + 1
    rockets, unreadable = rocket_rows(world)
    mod = sorted(json.dumps([list(key), value], sort_keys=True) for key, value in entities.items()
                 if str(value.get("id", "")).startswith(NS + ":"))
    return dict(stations=sorted(stations.uuid_text(s["station_id"]) for s in roots[NS + "_stations.dat"]["stations"]),
        mission_ids=sorted(next(iter(chain.saved_uuids(m["mission_id"]))) for m in missions["missions"]),
        mission_status=status, satellites=len(missions["satellites"]), clock=missions["clock"],
        rockets=rockets, unreadable_entity_chunks=unreadable,
        rocket_fuel_units=sum((r["fuel"] or {}).get("amount", 0) for r in rockets),
        forced={path.relative_to(world).as_posix(): forced_longs(fixture.read_nbt(path)["data"]["Forced"])
                for path in sorted(world.rglob("chunks.dat"))},
        mod_block_entities=len(mod), mod_block_entity_roots_sha256=common.digest(common.json_bytes(mod)))


def accounting(world):
    entities = block_entities(world)
    return dict(accounting_from(entities, layout()), identities=identities(world, entities))


class Console(chain.Console):
    def all(self, command, strict=True):
        start = len(self.process.lines)
        chain.require(not any(word in command for word in NBT_WRITES), "NBT-writing command refused: " + command)
        self.commands.append(command)
        self.process.command(command)
        self.query("say K17_END", r"\[Server\] K17_END")
        lines = [line.strip() for line in self.process.lines[start:] if "K17_END" not in line]
        chain.require(not strict or not any(m in line for line in lines for m in FAILURES), command + " -> " + str(lines[-3:]))
        return lines

    def until(self, command, marker, timeout=120, pump=None):
        deadline = time.monotonic() + timeout
        while True:
            lines = self.all(command, strict=False)
            if any(marker in line for line in lines):
                return lines
            chain.require(time.monotonic() < deadline, "Readback deadline: " + command)
            pump() if pump else time.sleep(.5)

    def await_line(self, marker, timeout, start_at, pump=None):
        """wait_for in one-second slices, so the samplers keep running during a long operation."""
        from scripts.run_dedicated_server_smoke import SmokeError
        deadline = time.monotonic() + timeout
        while True:
            try:
                return self.process.wait_for(marker, 1 if pump else timeout, start_at=start_at)
            except SmokeError as failure:
                if not pump or not str(failure).startswith("Timed out") or time.monotonic() >= deadline:
                    raise
                pump()


def check_lock(token):
    if token is None and sys.platform != "win32":
        return  # The reference node has no machine lock; this PC always has one.
    chain.require(token and token.startswith("W452 pid=") and (chain.LOCK / "owner.txt").read_text(
        encoding="utf-8-sig").startswith(token), "W452 heavy-JVM lock required")
    chain.require(min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) >= 10 * 1024**3, "10 GiB free required")


def start(args, runtime, log, hooks):
    from scripts import run_dedicated_server_smoke as server
    properties = runtime / "server.properties"
    text = properties.read_text(encoding="ascii")
    if "function-permission-level" not in text:
        # FI07's inherited capture function must parse; it is never executed. Two seats for accounts A and B.
        properties.write_text(text.replace("max-players=1", "max-players=2") + "function-permission-level=4\n", encoding="ascii")
    argfile = "win_args.txt" if sys.platform == "win32" else "unix_args.txt"
    command = [str(args.java), "-Xms512M", "-Xmx4G", "-Xlog:gc*:file=gc.log:time,uptime,level,tags"]
    command += [HOOK_PROPERTY] if hooks else []
    command += [f"@libraries/net/minecraftforge/forge/1.20.1-47.4.10/{argfile}", "nogui"]
    process = server.CapturedProcess(command, runtime, log)
    process.wait_for(server.READY_MARKER, 300)
    port = int(server.parse_java_properties(properties.read_bytes())["server-port"])
    status = chain.ready_status(server, port, "1.20.1-1.8.0-dev")
    return process, dict(java_command=command, properties=properties.read_text(encoding="ascii"), server_status=status,
                         test_properties=[HOOK_PROPERTY] if hooks else [], ready_utc=utc_now())


def stop(console, process, receipt):
    from scripts import run_dedicated_server_smoke as server
    console.query("save-all flush", server.SAVE_MARKER.pattern, 300)
    console.commands.append("stop")
    receipt["stop_sent_utc"] = utc_now()
    process.command("stop")
    receipt["exit_code"] = process.finish(300)
    receipt["stopped_utc"] = utc_now()
    chain.require(receipt["exit_code"] == 0, "Unclean native exit")
    receipt["errors"] = [s.strip() for s in process.lines if re.search(r"/(ERROR|FATAL)\]", s)]
    chain.require(not receipt["errors"], "Native ERROR/FATAL: " + str(receipt["errors"][:3]))


def config_readback(runtime):
    values = tomllib.loads((runtime / "config" / (NS + "-common.toml")).read_text(encoding="utf-8"))["atmosphere"]
    chain.require(all(values.get(k) == v for k, v in ATMOSPHERE.items()), "Atmosphere scan budgets are not the shipped defaults")
    return {k: values[k] for k in ATMOSPHERE}


def wait_loaded(console, dimension, box):
    x0, z0, x1, z1 = box
    checks = " ".join(f"if loaded {x} 100 {z}" for x in range(x0, x1 + 1, 16) for z in range(z0, z1 + 1, 16))
    console.until(f"execute in {dimension} {checks} run say K17_LOADED", "[Server] K17_LOADED", 120)


def lit_rooms(console, expected=None, timeout=0):
    """Vent block state lit=true is C6's own ACTIVE marker; reading it loads nothing and writes nothing."""
    deadline = time.monotonic() + timeout
    while True:
        lit = []
        for room in rooms():
            lines = console.all(f"execute in {MOON} if block {pos(room['vent'])} {NS}:oxygen_vent[lit=true] run say K17_LIT_{room['index']}", strict=False)
            if any(f"[Server] K17_LIT_{room['index']}" in line for line in lines):
                lit.append(room["index"])
        if expected is None or lit == expected or time.monotonic() >= deadline:
            return lit
        time.sleep(2)


def settled_rooms(console, timeout=300):
    """The supplied rooms once the scans after a load have settled.

    The saved block state still says lit=true before a vent's first tick, and each vent goes dark while its room
    is scanned again (four scans at a time at the shipped budget). Three full polls in a row are required.
    """
    started, streak = time.monotonic(), 0
    while True:
        lit = lit_rooms(console)
        streak = streak + 1 if lit == list(range(16)) else 0
        if streak >= 3 or time.monotonic() - started >= timeout:
            return dict(rooms_active=lit, settled=streak >= 3, wall_seconds=time.monotonic() - started)
        time.sleep(2)


def pad_status(console, name, validate=True):
    pad = layout()["pads"]["items"][name]
    at = pos(pad["assembler"])
    if validate:
        console.all(f"arce rocket validate {at}")
    lines = console.until(f"arce rocket status {at}", "Rocket assembler code=SUCCESS", 120)
    fields = parse_fields(lines, "Rocket assembler ")
    chain.require(fields["blocks"] == pad["expected_blocks"], f"{name}: C6 counts {fields['blocks']} blocks")
    return fields


def components(console):
    report = parse_fields(console.all("arce beta data-report"), "ARCE-BETA-1101 ")
    evidence = parse_fields(console.all("arce satellite admin evidence"), "ARCE_SATELLITE_EVIDENCE ")
    return dict(rooms_active=lit_rooms(console), stations=report["stations"], transfers=report["transfers"],
                transactions=report["transactions"], players=report["players"], atmosphere_volume=report["atmosphere_volume"],
                atmosphere_tick=report["atmosphere_tick"],
                missions={k: evidence[k] for k in ("satellites", "missions", "active", "ready", "claimed", "unfinished")})


def live_readbacks(console, runtime):
    result = dict(config=config_readback(runtime), components=components(console),
                  stations=console.all("arce station admin dump"),
                  forced={dim: forced_reply(console.all(f"execute in {dim} run forceload query")) for dim in (OW, MOON, SPACE)},
                  vents={r["index"]: console.all(f"execute in {MOON} run data get block {pos(r['vent'])} arce_oxygen_vent") for r in rooms()})
    chain.require(result["components"]["atmosphere_volume"] == ATMOSPHERE["maxVolumeCells"]
                  and result["components"]["atmosphere_tick"] == ATMOSPHERE["maxInspectionsPerLevelTick"], "Effective scan budgets differ")
    return result


def parent_seals(fixtures):
    seals = {}
    for name in PARENTS:
        chain.verify_seal(fixtures / name)
        seals[name] = chain.digest(fixtures / name / "SHA256SUMS.txt")
    return seals


def receipt_base(args, mode):
    return dict(schema_version=1, status="FAIL", mode=mode, candidate_commit=common.CANDIDATE, jar_sha256=chain.digest(args.jar),
        forge="47.4.10", jei="absent", template=str(args.template), started_utc=utc_now(), client_started=False,
        driver_commit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(),
        driver_sha256=chain.digest(Path(__file__)), commands=[])


def finish(process, runtime, evidence, receipt, keep_runtime=False):
    if process is not None:
        process.abort()
        chain.require(process.process.poll() is not None, "Owned JVM alive; keep lock and runtime")
    for name in ("logs/debug.log", "gc.log"):
        if (runtime / name).exists() and not (evidence / Path(name).name).exists():
            shutil.copy2(runtime / name, evidence / Path(name).name)
    chain.write(evidence / "receipt.json", receipt)
    chain.require(runtime.resolve().is_relative_to((runtime.parents[1] / "_work").resolve()), "Unsafe cleanup")
    if runtime.exists() and not keep_runtime:
        shutil.rmtree(runtime)


def build(args):
    from scripts import v170_world_fixture as fixture
    from scripts.campaign_prep import k08_stations as stations
    check_lock(args.lock_owner)
    chain.require(chain.digest(args.jar) == common.JAR_HASH, "Pinned C6 JAR required")
    folder, source = args.output / "source", PARENT / "source-world"
    receipt = dict(receipt_base(args, "build"), parents=parent_seals(args.output.parent), parent=str(source),
        hooks=["arce satellite release-test batch 100 32 (the only hook used; it makes the 100 new mission records)"],
        choice="One world on K09's missions-101: its 113 satellite and mission records, the three logical asteroid instances and "
        "the v1.6 history cannot be made by C6's commands, while stations, rooms and rocket structures can.")
    for name in FREE_REGIONS:
        chain.require(not (source / name).exists(), "K17 site is not free in the parent: " + name)
    inventory = fixture.file_inventory(source)
    evidence, runtime = folder / "build", args.output / "_work" / "build"
    evidence.mkdir(parents=True, exist_ok=False)
    process = None
    try:
        fixture.prepare(args.template, runtime, args.jar, source)
        process, started = start(args, runtime, evidence / "console.log", hooks=True)
        receipt.update(started)
        console = Console(process, receipt["commands"])
        existing = [stations.uuid_text(s["station_id"]) for s in chain.snapshot(source)["roots"][NS + "_stations.dat"]["data"]["stations"]]
        receipt["inherited_stations"] = existing
        console.all(build_commands()[0])
        wait_loaded(console, MOON, (ROOM_CHUNK[0]*16, ROOM_CHUNK[1]*16, ROOM_CHUNK[0]*16+63, ROOM_CHUNK[1]*16+63))
        for pad in layout()["pads"]["items"].values():
            console.all(f"forceload add {pos(pad['forceload'])}")
            wait_loaded(console, OW, pad["forceload"])
        for command in build_commands()[1:]:
            console.all(command)
        receipt["stations_made"] = []
        for entry in station_plan(existing):
            line = console.query(f"arce station admin create {entry['owner']} {entry['orbit']} {entry['name']}", r"Created .* id=[a-f0-9-]{36}")
            receipt["stations_made"].append(dict(entry, id=re.search(r"id=([a-f0-9-]{36})", line)[1]))
        receipt["rooms_settled"] = settled_rooms(console)
        receipt["rooms_active"] = receipt["rooms_settled"]["rooms_active"]
        chain.require(receipt["rooms_settled"]["settled"], "Not every room is supplied: " + str(receipt["rooms_active"]))
        receipt["pads"] = {name: pad_status(console, name) for name in PADS}
        for pad in layout()["pads"]["items"].values():
            console.all(f"forceload remove {pos(pad['forceload'])}")
        console.query("arce satellite release-test batch 100 32", r"ARCE_RELEASE_TEST_SATELLITE_BATCH .*code=SUCCESS")
        receipt["readbacks"] = live_readbacks(console, runtime)
        chain.require(receipt["readbacks"]["components"]["stations"] == 10, "Ten station records required")
        chain.require(receipt["readbacks"]["components"]["missions"]["missions"] >= 100, "100 mission records required")
        stop(console, process, receipt)
        process = None
        receipt["accounting"] = accounting(runtime / "world")
        chain.require(receipt["accounting"]["totals"]["canister_shells"] == 16 * CANISTERS, "Canister shells differ from the grant")
        chain.require(inventory == fixture.file_inventory(source), "Parent world changed")
        shutil.copytree(runtime / "world", folder / "source-world")
        shutil.copy2(runtime / "config" / (NS + "-common.toml"), folder / "generated-common.toml")
        receipt["status"] = "BUILT"
        chain.write(folder / "layout.json", layout())
        chain.write(folder / "schedule.json", plan(argparse.Namespace(sample_seconds=14400, save_interval=1800, restart_at=7200,
                                                                     warmup=120, qualification=False, hooks=False, component_interval=300)))
        chain.write(folder / "manifest.json", dict(receipt, layout=layout(), build_commands=build_commands(),
            grants=["Console places every block, the 16 forced room chunks and the items in hoppers and chests (item replace block).",
                    f"Each room: {CANISTERS} oxygen canisters and {COAL_BLOCKS} coal blocks; each small pad: {FUEL_CELLS} rocket fuel cells.",
                    "Six stations are operator-created with distinct placeholder owners (C8).",
                    "Rockets are left unassembled and loaders unclaimed: ownership and fuelling need a player on C6."],
            source_inventory=fixture.file_inventory(folder / "source-world"), preparation_status="BUILT",
            campaign_acceptance=False, required_gates_approved=False))
    except BaseException as failure:
        receipt["error"] = f"{type(failure).__name__}: {failure}"
        raise
    finally:
        finish(process, runtime, evidence, receipt)
    chain.seal(folder)
    return receipt


def readback(args):
    """Reload a copy with no test property and compare what a restart must keep."""
    from scripts import v170_world_fixture as fixture
    check_lock(args.lock_owner)
    folder = args.output / "source"
    chain.verify_seal(folder)
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    chain.require(re.fullmatch(r"readback(-[a-z0-9]{1,16})?", args.tag) is not None, "Invalid readback tag")
    evidence, runtime = args.output / args.tag, args.output / "_work" / args.tag
    evidence.mkdir(parents=True, exist_ok=False)
    receipt = dict(receipt_base(args, "readback"), source_seal_sha256=chain.digest(folder / "SHA256SUMS.txt"))
    process = None
    try:
        fixture.prepare(args.template, runtime, args.jar, folder / "source-world")
        process, started = start(args, runtime, evidence / "console.log", hooks=False)
        receipt.update(started)
        console = Console(process, receipt["commands"])
        receipt["rooms_settled"] = settled_rooms(console)
        receipt["rooms_active"] = receipt["rooms_settled"]["rooms_active"]
        for pad in manifest["layout"]["pads"]["items"].values():
            console.all(f"forceload add {pos(pad['forceload'])}")
            wait_loaded(console, OW, pad["forceload"])
        receipt["pads"] = {name: pad_status(console, name) for name in PADS}
        for pad in manifest["layout"]["pads"]["items"].values():
            console.all(f"forceload remove {pos(pad['forceload'])}")
        receipt["readbacks"] = live_readbacks(console, runtime)
        stop(console, process, receipt)
        process = None
        receipt["accounting"] = accounting(runtime / "world")
        before, after = manifest["accounting"], receipt["accounting"]
        receipt["comparison"] = dict(
            rooms_active=receipt["rooms_settled"]["settled"] and manifest["rooms_active"] == list(range(16))
            and receipt["readbacks"]["components"]["rooms_active"] == list(range(16)),
            pads={k: v["blocks"] for k, v in receipt["pads"].items()} == {k: v["blocks"] for k, v in manifest["pads"].items()},
            atmosphere_config=receipt["readbacks"]["config"] == manifest["readbacks"]["config"],
            stations=after["identities"]["stations"] == before["identities"]["stations"] and len(after["identities"]["stations"]) == 10,
            mission_ids=after["identities"]["mission_ids"] == before["identities"]["mission_ids"],
            satellites=after["identities"]["satellites"] == before["identities"]["satellites"],
            forced=receipt["readbacks"]["forced"] == manifest["readbacks"]["forced"],
            resources=reconcile(before, after)["status"] == "RECONCILED")
        receipt["reconciliation"] = reconcile(before, after)
        receipt["mission_status"] = dict(source=before["identities"]["mission_status"], reloaded=after["identities"]["mission_status"])
        chain.require(all(receipt["comparison"].values()), "Reload readbacks differ: " + str(receipt["comparison"]))
        chain.verify_seal(folder)
        receipt["status"] = "MATCHED"
    except BaseException as failure:
        receipt["error"] = f"{type(failure).__name__}: {failure}"
        raise
    finally:
        finish(process, runtime, evidence, receipt)
    chain.seal(evidence)
    return receipt


def action_assemble(console, events, hold, pump=None):
    from scripts import run_v060_flight_server_smoke as flight
    pad = layout()["pads"]["items"]["p2048"]
    console.all(f"forceload add {pos(pad['forceload'])}")
    wait_loaded(console, OW, pad["forceload"])
    start_index, started = len(console.process.lines), time.monotonic()
    console.all(f"arce rocket assemble {pos(pad['assembler'])}")
    index = console.await_line(flight.ASSEMBLY_LOG, 120, start_index, pump)
    match = flight.ASSEMBLY_LOG.search(console.process.lines[index])
    chain.require(int(match[1]) == 2048, "C6 assembled a different count")
    events.append(dict(utc=utc_now(), event="assembled-2048", path="ordinary operator command arce rocket assemble",
                       blocks=2048, snapshot=match[2], entity=match[3], wall_seconds=time.monotonic() - started))
    if not hold:  # Without the hook nothing else is driven on this pad: leave no ticket behind.
        console.all(f"forceload remove {pos(pad['forceload'])}")
    return match[3]


def action_boundary(console, events, indexes, pump=None):
    for index in indexes:
        room = rooms()[index]
        at, vent = pos(room["boundary"]), f"execute in {MOON} if block {pos(room['vent'])} {NS}:oxygen_vent[lit=%s] run say K17_VENT"
        for state, lit in (("true", "false"), ("false", "true")):
            started = time.monotonic()
            console.all(f"execute in {MOON} run setblock {at} minecraft:oak_trapdoor[half=top,open={state}]")
            console.until(vent % lit, "[Server] K17_VENT", 120, pump)
            events.append(dict(utc=utc_now(), event="boundary-opened" if state == "true" else "boundary-restored", room=index,
                               cell=room["boundary"], vent_lit=lit == "true", wall_seconds=time.monotonic() - started))


def action_hook_flights(console, events, pump=None):
    """Release-test launch of both small rockets at once. Qualifies the pads; it is not A08's player flight."""
    from scripts import run_v060_flight_server_smoke as flight
    launched = []
    for name in ("pad-a", "pad-b"):
        pad = layout()["pads"]["items"][name]
        for dim in (OW, MOON):  # As K04: hold the pad column in both dimensions for the flight.
            console.all(f"execute in {dim} run forceload add {pos(pad['forceload'])}")
            wait_loaded(console, dim, pad["forceload"])
        start_index = len(console.process.lines)
        console.all(f"arce rocket assemble {pos(pad['assembler'])}")
        index = console.process.wait_for(flight.ASSEMBLY_LOG, 90, start_at=start_index)
        entity = flight.ASSEMBLY_LOG.search(console.process.lines[index])[3]
        console.process.wait_for(re.compile(r"ARCE_ROCKET_ENTITY_ACTIVE entity=" + entity + r" operational=true "), 45, start_at=index)
        console.query(f"arce rocket release-test refuel {entity}", r"ARCE_RELEASE_TEST_REFUEL entity=" + entity)
        launched.append(dict(pad=name, report=k04.parse_report(console.all(f"arce rocket release-test report {entity}"))))
    for row in launched:
        row["start_index"] = len(console.process.lines)
        lines = console.all(f"arce rocket release-test launch {row['report']['entity']} MOON")
        row["launch"] = k04.launch_receipt(lines, row["report"], OW, MOON)
        events.append(dict(utc=utc_now(), event="launched", pad=row["pad"], receipt=row["launch"].group(0)))
    for row in launched:
        marker = re.compile(r"ARCE_TRANSFER_PHASE transfer=" + re.escape(row["launch"].group(1)) + r" .*event=landing_complete ")
        row["landed_index"] = console.await_line(marker, 60, row["start_index"], pump)
        entity = flight.PHASE_LOG.search(console.process.lines[row["landed_index"]]).group(5)
        landed = k04.parse_report(console.all(f"execute in {MOON} run arce rocket release-test report {entity}"))
        chain.require(landed["dimension"] == MOON and landed["state"] == "LANDED" and landed["blocks"] == 64
                      and landed["fuel"] == int(row["launch"].group(9)) - int(row["launch"].group(8)), "Landing readback differs")
        events.append(dict(utc=utc_now(), event="landed", pad=row["pad"], dimension_transfer=[OW, MOON], report=landed))
    overlap = max(r["start_index"] for r in launched) < min(r["landed_index"] for r in launched)
    events.append(dict(utc=utc_now(), event="two-rockets-in-flight", overlap=overlap,
                       basis="both launch receipts precede either landing_complete line in the server log"))
    chain.require(overlap, "The two flights did not overlap")
    for name in ("pad-a", "pad-b"):
        for dim in (OW, MOON):
            console.all(f"execute in {dim} run forceload remove {pos(layout()['pads']['items'][name]['forceload'])}")


def action_hook_disassemble(console, events, entity, pump=None):
    """C6 answers the hook with "pending" and finishes through its sealed path, which logs its own two lines."""
    start_index, started = len(console.process.lines), time.monotonic()
    console.all(f"arce rocket release-test disassemble {entity}")
    sealed = re.compile(r"ARCE_ROCKET_SEALED_DISASSEMBLY entity=" + re.escape(entity) + r" logical=([0-9a-f-]{36}) result=([A-Z_]+)")
    index = console.await_line(sealed, 120, start_index, pump)
    receipt = sealed.search(console.process.lines[index])
    chain.require(receipt[2] == "SEALED", "Disassembly differs: " + receipt[0])
    done = console.await_line(re.compile(r"ARCE_DISASSEMBLY_DURABILITY_COMPLETE transaction=[0-9a-f-]{36} phase=DONE reason=SEALED "),
                              60, index, pump)
    seconds = time.monotonic() - started
    events.append(dict(utc=utc_now(), event="disassembled-2048", path="release-test hook, not the player confirmation",
                       receipt=receipt[0], durability=console.process.lines[done].strip().split("]: ", 1)[-1],
                       restored=pad_status(console, "p2048"), wall_seconds=seconds))
    console.all(f"forceload remove {pos(layout()['pads']['items']['p2048']['forceload'])}")


def sample_segment(console, evidence, args, schedule_plan, segment, state, events):
    """One server lifetime of sampled time: ticks, RSS, heap, forced chunks, components, saves and actions."""
    from scripts import run_dedicated_server_smoke as server
    from scripts.campaign_observe import jdk
    from scripts.campaign_observe.process import parse_heap_info, read_process
    from scripts.campaign_observe.ticks import sample_forced_chunks
    hooks, pid = bool(args.hooks), console.process.process.pid
    target = jdk.CampaignJvm(console.process.process, "server")
    jcmd = args.java.with_name("jcmd.exe" if args.java.suffix == ".exe" else "jcmd")
    offset = sum(s["sampled_seconds"] for s in schedule_plan["segments"] if s["index"] < segment["index"])
    duration, intervals = segment["sampled_seconds"], schedule_plan["intervals"]
    time.sleep(schedule_plan["warmup_seconds"])  # C10: the warm-up after every start is discarded.
    anchor = raw_ticks.parse_raw_tick_reply(console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX)).tick if hooks else None
    events.append(dict(utc=utc_now(), event="sampling-start", segment=segment["index"], first_tick_exclusive=anchor))
    due = dict(tick=0.0, heap=0.0, components=0.0, save=state.setdefault("next_save", schedule_plan["flushed_save_interval_seconds"]))
    polls, rss, heap, tps = [], [], [], []
    started = time.monotonic()
    with (evidence / "samples.jsonl").open("x", encoding="utf-8") as stream:
        def emit(kind, **values):
            stream.write(json.dumps(dict(utc=utc_now(), sampled_s=round(offset + time.monotonic() - started, 3), kind=kind, **values)) + "\n")
            stream.flush()
        def tick():
            if hooks:
                line = console.query(raw_ticks.RAW_TICK_COMMAND, raw_ticks.PREFIX)
                poll = raw_ticks.parse_raw_tick_reply(line)
                polls.append(raw_ticks.RawTickPoll(poll.tick, tuple((t, n) for t, n in poll.samples if t > anchor)))
                emit("raw_ticks", reply=line.split(raw_ticks.PREFIX, 1)[1])
            else:
                tps.append((time.monotonic() - started, parse_tps(console.all("forge tps"))["mean_tick_ms"]))
                emit("forge_tps", mean_tick_ms=tps[-1][1])
        def pump():
            now = time.monotonic() - started
            memory = read_process(pid)
            rss.append((now, memory["rss_bytes"]))
            emit("process", **memory)
            if now >= due["tick"]:
                tick()
                due["tick"] = now + intervals["tick"]
            return now
        for _ in poll_schedule(duration, 1):
            now = pump()
            sampled = offset + now
            if now >= due["heap"]:
                heap.append((now, parse_heap_info(jdk.heap_info(target, jcmd=jcmd))["heap_used_bytes"]))
                emit("heap", heap_used_bytes=heap[-1][1])
                due["heap"] = now + intervals["heap"]
            if now >= due["components"]:
                forced = sample_forced_chunks(console.all, (OW, MOON, SPACE))
                found = {dim: forced_reply(lines) for dim, lines in forced["replies"].items()}
                emit("components", **components(console), forced=found,
                     unexpected_forced={dim: [c for c in found[dim] if c not in state["expected_forced"][dim] + state["temporary_forced"].get(dim, [])]
                                        for dim in found})
                due["components"] = now + intervals["components"]
            if sampled >= due["save"]:
                began = time.monotonic()
                console.query("save-all flush", server.SAVE_MARKER.pattern, 300)
                events.append(dict(utc=utc_now(), event="flushed-save", sampled_s=sampled, wall_seconds=time.monotonic() - began))
                state["next_save"] = due["save"] = due["save"] + schedule_plan["flushed_save_interval_seconds"]
            for action in schedule_plan["actions"]:
                if sampled < action["at"] or action["id"] in state["done"]:
                    continue
                state["done"].append(action["id"])
                if action["mode"] == "client":
                    events.append(dict(utc=utc_now(), event="client-step-due", id=action["id"], actors=action["actors"],
                                       status="NOT_EXECUTED: no client is started by this driver"))
                    continue
                events.append(dict(utc=utc_now(), event="action-start", id=action["id"], sampled_s=sampled))
                if action["id"] == "assemble-2048":
                    state["rocket_2048"] = action_assemble(console, events, hooks, pump)
                    box = layout()["pads"]["items"]["p2048"]["forceload"]
                    state["temporary_forced"] = {} if not hooks else {OW: [[x, z] for x in range(box[0] >> 4, (box[2] >> 4) + 1)
                                                                           for z in range(box[1] >> 4, (box[3] >> 4) + 1)]}
                elif action["id"] == "a11-open-restore":
                    action_boundary(console, events, action["rooms"], pump)
                elif action["id"] == "flights-a-b":
                    action_hook_flights(console, events, pump)
                elif action["id"] == "disassemble-2048":
                    action_hook_disassemble(console, events, state["rocket_2048"], pump)
                    state["temporary_forced"] = {}
        tick()
    summary = dict(segment=segment["index"], sampled_seconds=time.monotonic() - started,
                   rss_bytes=series_summary(rss), heap_used_bytes=series_summary(heap))
    if hooks:
        summary["ticks"] = raw_ticks.summarize_raw_ticks(polls, 500)
        if args.qualification:
            summary["ticks"].pop("reference_budgets")  # Not REF: no budget is compared on this PC.
        with (evidence / "ticks.csv").open("x", encoding="ascii", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(("server_tick", "total_nanos"))
            writer.writerows(raw_ticks.merge_polls(polls)["samples"])
    else:
        summary["ticks"] = dict(mode="aggregate_only", forge_tps_mean_tick_ms=series_summary(tps),
                                limit="100-tick means; no per-tick distribution, P95, P99 or spike length without the release-test property")
    return summary


def component_report(events, samples, hooks):
    """Present or missing over the run, from the recorded events and samples only."""
    names = {e["event"] for e in events}
    parts = [s for s in samples if s["kind"] == "components"]
    def seen(test):
        return bool(parts) and all(test(p) for p in parts)
    client = "CLIENT_STEP_NOT_EXECUTED"
    return {
        "two player connections": "MISSING: no client is started; O7 supplies A and B",
        "16 supplied sealed rooms": "PRESENT" if seen(lambda p: len(p["rooms_active"]) == 16 or "boundary-opened" in names) else "MISSING",
        "ten station records": "PRESENT" if seen(lambda p: p["stations"] == 10) else "MISSING",
        "100 mission records": "PRESENT" if seen(lambda p: p["missions"]["missions"] >= 100) else "MISSING",
        "missions ACTIVE at each sample": [p["missions"]["active"] for p in parts],
        "two rockets in flight": "PRESENT_BY_HOOK" if "two-rockets-in-flight" in names else client,
        "2,048-block assembly": "PRESENT" if "assembled-2048" in names else "MISSING",
        "2,048-block disassembly": "PRESENT_BY_HOOK" if "disassembled-2048" in names else client,
        "dimension transfer": "PRESENT_BY_HOOK" if "landed" in names else client,
        "flushed saves": sum(e["event"] == "flushed-save" for e in events),
        "orderly restart": "PRESENT" if "restart" in names else "MISSING",
        "room boundary opened and restored": "PRESENT" if {"boundary-opened", "boundary-restored"} <= names else "MISSING",
        "raw ticks": "PRESENT" if hooks else "MISSING: needs the release-test property",
        "unexpected forced chunks": sorted({json.dumps([d, c]) for p in parts for d, v in p["unexpected_forced"].items() for c in v}),
    }


def soak(args):
    """One segment per call on this PC (one server per lock hold); `--segment all` runs both where no lock exists."""
    from scripts import v170_world_fixture as fixture
    folder = args.output / "source"
    chain.verify_seal(folder)
    schedule_plan = plan(args)
    runtime, root = args.output / "_work" / args.tag, args.output / args.tag
    state_path = root / "state.json"
    for segment in schedule_plan["segments"]:
        if args.segment not in ("all", str(segment["index"])):
            continue
        check_lock(args.lock_owner)
        evidence = root / f"segment-{segment['index']}"
        evidence.mkdir(parents=True, exist_ok=False)
        receipt = dict(receipt_base(args, "soak"), tag=args.tag, segment=segment, schedule=schedule_plan,
                       source_seal_sha256=chain.digest(folder / "SHA256SUMS.txt"), host_scope="Windows qualification, not REF"
                       if sys.platform == "win32" else "host recorded by the campaign")
        process, events, last = None, [], segment["index"] == len(schedule_plan["segments"])
        try:
            if segment["index"] == 1:
                fixture.prepare(args.template, runtime, args.jar, folder / "source-world")
                before = accounting(runtime / "world")
                chain.write(root / "accounting-before.json", before)
                state = dict(done=[], temporary_forced={}, expected_forced={
                    OW: before["identities"]["forced"].get("data/chunks.dat", []),
                    MOON: before["identities"]["forced"].get(f"dimensions/{NS}/moon/data/chunks.dat", []),
                    SPACE: before["identities"]["forced"].get(f"dimensions/{NS}/space/data/chunks.dat", [])})
            else:
                state = json.loads(state_path.read_text(encoding="utf-8"))
                chain.require(state["segment_done"] == segment["index"] - 1 and runtime.is_dir(), "The previous segment did not end cleanly")
                events.append(dict(utc=utc_now(), event="restart", stopped_utc=state["stopped_utc"],
                                   note="downtime between stop and ready is not sampled time"))
            process, started = start(args, runtime, evidence / "console.log", bool(args.hooks))
            receipt.update(started)
            console = Console(process, receipt["commands"])
            settled = settled_rooms(console)
            events.append(dict(utc=utc_now(), event="rooms-settled", **settled))
            chain.require(settled["settled"], "The rooms did not settle after the start: " + str(settled["rooms_active"]))
            receipt["readbacks_start"] = live_readbacks(console, runtime)
            receipt["summary"] = sample_segment(console, evidence, args, schedule_plan, segment, state, events)
            receipt["readbacks_end"] = live_readbacks(console, runtime)
            stop(console, process, receipt)
            process = None
            shutil.copy2(runtime / "gc.log", evidence / "gc.log")
            receipt["gc"] = gc_summary((evidence / "gc.log").read_text(encoding="utf-8", errors="replace"))
            after = accounting(runtime / "world")
            chain.write(evidence / "accounting-after.json", after)
            before = json.loads((root / "accounting-before.json").read_text(encoding="utf-8"))
            receipt["reconciliation"] = reconcile(before, after)
            receipt["accounting_complete"] = not after["identities"]["unreadable_entity_chunks"]
            receipt["rocket_fuel_units"] = dict(before=before["identities"]["rocket_fuel_units"], after=after["identities"]["rocket_fuel_units"])
            receipt["identity_changes"] = {key: before["identities"][key] != after["identities"][key]
                                           for key in ("stations", "mission_ids", "satellites", "rockets", "forced", "mod_block_entity_roots_sha256")}
            state.update(segment_done=segment["index"], stopped_utc=receipt["stopped_utc"])
            chain.write(state_path, state)
            receipt["events"] = events
            if last:
                samples, every = [], []
                for index in range(1, segment["index"] + 1):
                    part = root / f"segment-{index}"
                    samples += [json.loads(line) for line in (part / "samples.jsonl").read_text(encoding="utf-8").splitlines()]
                    every += events if index == segment["index"] else json.loads((part / "receipt.json").read_text(encoding="utf-8"))["events"]
                receipt["components"] = component_report(every, samples, bool(args.hooks))
            receipt["status"] = "PASS"
        except BaseException as failure:
            receipt["error"] = f"{type(failure).__name__}: {failure}"
            receipt["events"] = events
            raise
        finally:
            finish(process, runtime, evidence, receipt, keep_runtime=receipt["status"] == "PASS" and not last)
        chain.seal(evidence)
    return schedule_plan


def parser():
    result = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = result.add_subparsers(dest="action", required=True)
    for name in ("plan", "build", "readback", "soak"):
        p = sub.add_parser(name)
        p.add_argument("--output", type=Path, default=OUTPUT)
        p.add_argument("--jar", type=Path, default=common.JAR)
        if name != "plan":
            p.add_argument("--template", type=Path, required=True)
            p.add_argument("--java", type=Path, required=True)
            p.add_argument("--lock-owner")
        if name in ("plan", "soak"):
            p.add_argument("--sample-seconds", type=float, default=14400)
            p.add_argument("--save-interval", type=float, default=1800)
            p.add_argument("--restart-at", type=float, default=7200)
            p.add_argument("--warmup", type=float, default=120)
            p.add_argument("--component-interval", type=float, default=300)
            p.add_argument("--qualification", action="store_true")
            p.add_argument("--hooks", action="store_true", help="start with the release-test property: raw ticks and hook-driven rocket actions")
        if name == "readback":
            p.add_argument("--tag", default="readback", help="a new evidence folder; an earlier readback is never replaced")
        if name == "soak":
            p.add_argument("--tag", required=True)
            p.add_argument("--segment", choices=("1", "2", "all"), required=True)
    return result


def main(argv=None):
    args = parser().parse_args(argv)
    if args.action == "plan":
        args.output.mkdir(parents=True, exist_ok=False)
        chain.write(args.output / "layout.json", layout())
        chain.write(args.output / "schedule.json", plan(args))
        (args.output / "commands.txt").write_text("\n".join(build_commands()) + "\n", encoding="ascii", newline="\n")
        return 0
    if args.action == "soak":
        chain.require(re.fullmatch(r"[a-z0-9][a-z0-9-]{0,31}", args.tag) is not None, "Invalid tag")
    result = dict(build=build, readback=readback, soak=soak)[args.action](args)
    print(json.dumps(result if args.action == "soak" else {k: result[k] for k in ("status", "mode")}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
