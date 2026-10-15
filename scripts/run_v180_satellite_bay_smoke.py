#!/usr/bin/env python3
"""W27b S1: native save and reload of a satellite bay root, and R-038's chunk and Level save-refusal scopes.

Two disposable flat worlds on one packaged dedicated server, each with a seed start and two restarts:

* ``world-bay``: a bay with a package and a serializable quarantined bay. Both roots must be written unchanged
  (typed NBT equality) through every save; the quarantined one stays guarded at every load and refuses nothing.
  The second restart then lets a vanilla hopper pull the package, proving the reloaded package is live.
* ``world-refusal``: a bay whose root is too deep for the native chunk reader refuses exactly its terrain chunk
  while another chunk of the same Level and a Nether chunk save; the refusal ends with the Level. Then 257 such
  bays exhaust the 256 denied coordinates, so an unrelated Overworld chunk is refused too (Level scope) while
  the Nether still saves; the next start saves that chunk again.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import struct
import time
import uuid
import zlib

import run_dedicated_server_smoke as smoke
import v120_precision_world_fixture as regions
from run_v060_flight_server_smoke import _server_command

NS = "advancedrocketrycommunity"
BAY = NS + ":rocket_satellite_bay"
KEY = "arce_satellite_bay"
SAVE_REFUSAL = ("Refusing chunk save with a satellite bay root that native serialization cannot keep; "
                "back up and repair offline")
SATURATED = "Refusing chunk save after denial capacity exhausted; back up and repair first"
OWNER = [5, 6, 7, 8]
PACKAGE_SNBT = ('{id:"advancedrocketrycommunity:data_satellite_package",Count:1b,tag:{SatelliteIdentity:'
                '{schema_version:1,satellite_id:[I;1,2,3,4],owner_id:[I;5,6,7,8],'
                'definition_id:"advancedrocketrycommunity:w27b_native"}}}')
ROOT_SNBT = "{schema_version:1,owner:[I;5,6,7,8],package:" + PACKAGE_SNBT + "}"
QUARANTINE_SNBT = '{schema_version:1,owner:[I;5,6,7,8],unknown_field:"kept",extra:[B;1b,2b,3b]}'
# 520 nested compounds under the root: deeper than the native chunk reader's 512 levels.
DEEP_SNBT = "{schema_version:1,owner:[I;5,6,7,8],nested:" + "{n:" * 520 + "{}" + "}" * 520 + "}"

LOADED_BAY = (72, 0, 72)             # chunk [4, 4]
QUARANTINED_BAY = (88, 0, 72)        # chunk [5, 4]
HOPPER = (72, -1, 72)
REFUSED = (162, 0, 162)              # chunk [10, 10]
REFUSED_MARKER = (163, 0, 161)
CONTROL_MARKER = (195, 0, 161)       # chunk [12, 10]
NETHER_MARKER = (963, 70, 963)       # Nether chunk [60, 60]; no Overworld chunk there is loaded
UNRELATED_MARKER = (643, 0, 643)     # chunk [40, 40]
SATURATION_CHUNKS = [(x, z) for z in range(20, 36) for x in range(20, 37)][:257]

PREFIX = r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/(?:INFO|WARN|ERROR)\] "
EVENT_ERROR = re.compile(PREFIX + r"\[(?:ne\.mi\.ev\.EventBus|net\.minecraftforge\.eventbus\.EventBus)/EVENTBUS\]: "
                         r"Exception caught during firing event: (?P<reason>.+?)\s*$")
CHUNK_ERROR = re.compile(PREFIX + r"\[(?:minecraft/ChunkMap|net\.minecraft\.server\.level\.ChunkMap/?)\]: "
                         r"Failed to save chunk (?:(?P<x1>-?\d+),(?P<z1>-?\d+)|\[(?P<x2>-?\d+), (?P<z2>-?\d+)\])\s*$")
GUARDED = re.compile(r"ARCE_SATELLITE_BAY_GUARDED pos=(?P<pos>-?\d+, -?\d+, -?\d+) guard=(?P<guard>[A-Z_]+) "
                     r"keepable=(?P<keepable>true|false)\s*$")
SAY = r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] \[minecraft/MinecraftServer\]: (?:\[Not Secure\] )?\[Server\] "
CHANGED = re.compile(r"Changed the block at (?P<x>-?\d+), (?P<y>-?\d+), (?P<z>-?\d+)\s*$")
# W44's startup baseline: at most one temporary-hold WARN per start, as the GameTest manifest's
# w44-first-moon-landing-startup-baseline expectation allows.
FIRST_EVENT_BASELINE = re.compile(r"/WARN\] \[[^\]]*AdvancedRocketryCommunity/\]: "
                                  r"ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED\s*$")


class TypedReader:
    """A bounded NBT reader that keeps every tag type, so equality is equality of the written NBT up to compound
    key order. Chunks of generated terrain hold more tags than the celestial inspector's reader admits."""
    MAX_DEPTH = 64
    MAX_TAGS = 262_144
    SCALARS = {1: (">b", 1), 2: (">h", 2), 3: (">i", 4), 4: (">q", 8), 5: (">f", 4), 6: (">d", 8)}

    def __init__(self, payload: bytes):
        self.payload, self.offset, self.tags = payload, 0, 0

    def read_root(self) -> tuple:
        kind = self._take(">B", 1)
        self._string()
        value = self._payload(kind, 0)
        require(self.offset == len(self.payload), "NBT has trailing bytes")
        return kind, value

    def _take(self, fmt: str, size: int):
        require(self.offset + size <= len(self.payload), "Truncated NBT")
        value = struct.unpack_from(fmt, self.payload, self.offset)[0]
        self.offset += size
        return value

    def _string(self) -> str:
        length = self._take(">H", 2)
        require(self.offset + length <= len(self.payload), "Truncated NBT string")
        text = self.payload[self.offset:self.offset + length].decode("utf-8", errors="surrogatepass")
        self.offset += length
        return text

    def _length(self) -> int:
        length = self._take(">i", 4)
        require(0 <= length <= len(self.payload) - self.offset, "Invalid NBT length")
        return length

    def _payload(self, kind: int, depth: int):
        self.tags += 1
        require(depth <= self.MAX_DEPTH and self.tags <= self.MAX_TAGS, "NBT exceeds the reader's bounds")
        if kind in self.SCALARS:
            return self._take(*self.SCALARS[kind])
        if kind == 7:
            length = self._length()
            values = list(self.payload[self.offset:self.offset + length])
            self.offset += length
            return values
        if kind == 8:
            return self._string()
        if kind == 9:
            child, length = self._take(">B", 1), self._length()
            require(child != 0 or length == 0, "A non-empty list uses TAG_End")
            return [(child, self._payload(child, depth + 1)) for _ in range(length)]
        if kind == 10:
            value = {}
            while (child := self._take(">B", 1)) != 0:
                name = self._string()
                require(name not in value, "Duplicate compound key " + name)
                value[name] = (child, self._payload(child, depth + 1))
            return value
        if kind in (11, 12):
            fmt, size = (">i", 4) if kind == 11 else (">q", 8)
            return [self._take(fmt, size) for _ in range(self._length_items(size))]
        raise smoke.SmokeError(f"Unknown NBT tag type {kind}")

    def _length_items(self, size: int) -> int:
        length = self._take(">i", 4)
        require(0 <= length and length * size <= len(self.payload) - self.offset, "Invalid NBT array length")
        return length


def typed_package() -> tuple:
    identity = (10, {"schema_version": (3, 1), "satellite_id": (11, [1, 2, 3, 4]), "owner_id": (11, OWNER),
                     "definition_id": (8, NS + ":w27b_native")})
    return 10, {"id": (8, NS + ":data_satellite_package"), "Count": (1, 1),
                "tag": (10, {"SatelliteIdentity": identity})}


def typed_root(with_package: bool) -> tuple:
    root = {"schema_version": (3, 1), "owner": (11, OWNER)}
    if with_package:
        root["package"] = typed_package()
    return 10, root


TYPED_QUARANTINE = (10, {"schema_version": (3, 1), "owner": (11, OWNER), "unknown_field": (8, "kept"),
                         "extra": (7, [1, 2, 3])})


def require(condition: bool, message: str) -> None:
    if not condition:
        raise smoke.SmokeError(message)


def chunk_of(position: tuple[int, int, int]) -> tuple[int, int]:
    return position[0] >> 4, position[2] >> 4


def record(world: Path, dimension: str, chunk: tuple[int, int]) -> bytes:
    region = world / dimension / "region" / f"r.{chunk[0] >> 5}.{chunk[1] >> 5}.mca"
    return regions._region_chunk(regions._read_regular(region, regions.MAX_REGION_BYTES), *chunk)


def typed_chunk(compressed: bytes) -> dict:
    require(len(compressed) <= regions.MAX_COMPRESSED_CHUNK, "Oversized compressed chunk")
    stream = zlib.decompressobj()
    expanded = stream.decompress(compressed, regions.MAX_EXPANDED_CHUNK + 1)
    require(len(expanded) <= regions.MAX_EXPANDED_CHUNK and stream.eof and not stream.unconsumed_tail
            and not stream.unused_data, "Invalid or oversized chunk NBT")
    kind, root = TypedReader(expanded).read_root()
    require(kind == 10, "Chunk root is not a compound")
    return root


def block_entity(chunk: dict, position: tuple[int, int, int]) -> dict | None:
    matches = [entity for kind, entity in chunk.get("block_entities", (9, []))[1]
               if kind == 10 and (entity.get("x"), entity.get("y"), entity.get("z"))
               == ((3, position[0]), (3, position[1]), (3, position[2]))]
    require(len(matches) <= 1, f"Duplicated block entity at {position}")
    return matches[0] if matches else None


def palette(chunk: dict, section_y: int) -> set[str]:
    for kind, section in chunk.get("sections", (9, []))[1]:
        if kind == 10 and section.get("Y") == (1, section_y):
            states = section.get("block_states", (10, {}))[1]
            return {entry["Name"][1] for _, entry in states.get("palette", (9, []))[1]}
    return set()


def bay_root(world: Path, position: tuple[int, int, int]) -> tuple:
    entity = block_entity(typed_chunk(record(world, "", chunk_of(position))), position)
    require(entity is not None and entity.get("id") == (8, BAY) and KEY in entity,
            f"No saved satellite bay root at {position}")
    return entity[KEY]


class Server:
    """One packaged server start with fresh-line command barriers."""

    def __init__(self, args, session: Path, evidence: Path, name: str):
        command = _server_command(args.java)
        command[2] = "-Xmx3G"
        self.name = name
        self.commands: list[str] = []
        self.process = smoke.CapturedProcess(command, session, evidence / f"{name}.log")
        self.process.wait_for(smoke.READY_MARKER, args.startup_timeout)

    def send(self, text: str) -> None:
        self.commands.append(text if len(text) <= 200 else text[:200] + f"...<{len(text)} chars>")
        self.process.command(text)

    def barrier(self, label: str, timeout: float = 120) -> list[str]:
        start = len(self.process.lines)
        self.send("say " + label)
        end = self.process.wait_for(re.compile(SAY + re.escape(label) + r"\s*$"), timeout, start_at=start)
        return self.process.lines[:end]

    def batch(self, commands: list[str], label: str, timeout: float = 300) -> list[str]:
        start = len(self.process.lines)
        for command in commands:
            self.send(command)
        return self.barrier(label, timeout)[start:]

    def changed(self, commands: list[str], positions: list[tuple[int, int, int]], label: str) -> None:
        fresh = self.batch(commands, label)
        changed = {(int(match["x"]), int(match["y"]), int(match["z"]))
                   for match in (CHANGED.search(line) for line in fresh) if match}
        require(set(positions) <= changed, f"{label}: blocks not changed: {sorted(set(positions) - changed)[:4]}")

    def says(self, command: str, marker: str) -> bool:
        fresh = self.batch([command + " run say " + marker], marker + "_END")
        return any(re.search(SAY + re.escape(marker) + r"\s*$", line) for line in fresh)

    def wait_loaded(self, probes: list[str], timeout: float = 120) -> None:
        """``probes`` are ``execute`` prefixes such as ``execute if loaded 72 0 72``; all must pass together."""
        deadline = time.monotonic() + timeout
        pending = list(probes)
        attempt = 0
        while pending:
            require(time.monotonic() < deadline, f"{self.name}: chunks did not load: {pending[:3]}")
            attempt += 1
            fresh = self.batch([probe + f" run say ARCE_BAY_LOADED_{index}_{attempt}"
                                for index, probe in enumerate(pending)], f"ARCE_BAY_PROBE_END_{attempt}")
            done = {index for index in range(len(pending))
                    if any(f"ARCE_BAY_LOADED_{index}_{attempt}" in line for line in fresh)}
            pending = [probe for index, probe in enumerate(pending) if index not in done]
            if pending:
                time.sleep(0.5)

    def save(self) -> list[str]:
        start = len(self.process.lines)
        self.send("save-all flush")
        self.process.wait_for(smoke.SAVE_MARKER, 600, start_at=start)
        return self.barrier("ARCE_BAY_SAVED_" + uuid.uuid4().hex[:8])[start:]

    def stop(self) -> list[str]:
        self.send("stop")
        require(self.process.finish(600) == 0, f"{self.name}: server did not stop cleanly")
        return self.process.lines

    def abort(self) -> None:
        self.process.abort()


def audit(lines: list[str], allowed_chunks: set[tuple[int, int]], reasons: set[str],
          guarded: dict[str, int]) -> dict:
    """Every ERROR is a paired refusal of an allowed chunk; the bay's audit lines are exactly the expected guards.

    The console abbreviates logger names, so the bay's WARN lines are found by their marker, not by logger."""
    events, failures, warns, baseline = 0, [], {}, 0
    for finding in smoke.scan_log(lines):
        if FIRST_EVENT_BASELINE.search(finding) is not None:
            baseline += 1
        elif (match := EVENT_ERROR.search(finding)) is not None:
            require(match["reason"] in reasons, "Unexpected refusal reason: " + finding)
            events += 1
        elif (match := CHUNK_ERROR.search(finding)) is not None:
            chunk = (int(match["x1"] or match["x2"]), int(match["z1"] or match["z2"]))
            require(chunk in allowed_chunks, f"Unexpected refused chunk {chunk}")
            failures.append(chunk)
        elif GUARDED.search(finding) is None:
            raise smoke.SmokeError("Unexpected server log finding: " + finding.strip())
    for line in lines:
        if "ARCE_SATELLITE_BAY_" not in line:
            continue
        match = GUARDED.search(line)
        require(match is not None and "/WARN]" in line, "Unexpected bay audit line: " + line.strip())
        text = f"{match['pos']}|{match['guard']}|{match['keepable']}"
        warns[text] = warns.get(text, 0) + 1
    require(baseline <= 1, f"{baseline} first-event startup holds in one start")
    require(events == len(failures), f"Refusal headers are unpaired: {events} events, {len(failures)} chunk failures")
    require(warns == guarded, f"Guard lines differ: expected {guarded}, observed {warns}")
    return {"refusal_pairs": events, "refused_chunks": sorted(set(failures)), "guard_lines": warns,
            "first_event_startup_holds": baseline,
            "log_counts": smoke.log_audit_counts(lines)}


def world_bay(args, session: Path, evidence: Path, report: dict) -> None:
    world = session / "world-bay"
    quarantine_line = {f"{QUARANTINED_BAY[0]}, {QUARANTINED_BAY[1]}, {QUARANTINED_BAY[2]}|QUARANTINED|true": 1}
    baseline = None
    for start in range(3):
        name = f"bay-{start}"
        server = Server(args, session, evidence, name)
        row = {"start": name}
        try:
            if start == 0:
                server.batch(["forceload add 64 64 95 79"], "ARCE_BAY_FORCED")
            server.wait_loaded([f"execute if loaded {x} {y} {z}" for x, y, z in (LOADED_BAY, QUARANTINED_BAY)])
            if start == 0:
                server.changed([f"setblock {LOADED_BAY[0]} {LOADED_BAY[1]} {LOADED_BAY[2]} {BAY}{{{KEY}:{ROOT_SNBT}}}",
                                f"setblock {QUARANTINED_BAY[0]} {QUARANTINED_BAY[1]} {QUARANTINED_BAY[2]} "
                                f"{BAY}{{{KEY}:{QUARANTINE_SNBT}}}"], [LOADED_BAY, QUARANTINED_BAY], "ARCE_BAY_PLACED")
            x, y, z = LOADED_BAY
            require(server.says(f"execute if data block {x} {y} {z} {KEY}.package", "ARCE_BAY_PACKAGE_PRESENT"),
                    f"{name}: the bay lost its package")
            if start == 2:
                hx, hy, hz = HOPPER
                server.changed([f"setblock {hx} {hy} {hz} minecraft:hopper[facing=down]"], [HOPPER], "ARCE_BAY_HOPPER")
                deadline = time.monotonic() + 60
                while not server.says(f"execute if data block {hx} {hy} {hz} Items[0]", "ARCE_BAY_PULLED"):
                    require(time.monotonic() < deadline, "The hopper never pulled the reloaded package")
                    time.sleep(0.5)
                require(server.says(f"execute unless data block {x} {y} {z} {KEY}.package", "ARCE_BAY_EMPTIED"),
                        "The pulled package is still in the bay")
            server.save()
            lines = server.stop()
            row["log"] = audit(lines, set(), set(), quarantine_line)
            row["commands"] = server.commands
        except BaseException:
            server.abort()
            raise
        loaded = bay_root(world, LOADED_BAY)
        quarantined = bay_root(world, QUARANTINED_BAY)
        require(quarantined == TYPED_QUARANTINE, f"{name}: the quarantined root changed")
        if start < 2:
            require(loaded == typed_root(True), f"{name}: the bay root changed: {loaded}")
        else:
            require(loaded == typed_root(False), f"{name}: the emptied root is not the owner alone: {loaded}")
            hopper = block_entity(typed_chunk(record(world, "", chunk_of(HOPPER))), HOPPER)
            items = hopper.get("Items", (9, []))[1] if hopper else []
            expected = (10, {**typed_package()[1], "Slot": (1, 0)})
            require(items == [expected], f"The hopper does not hold exactly the package: {items}")
            row["hopper_items"] = repr(items)
        for position in (LOADED_BAY, QUARANTINED_BAY):
            (evidence / f"{name}-chunk-{chunk_of(position)[0]}-{chunk_of(position)[1]}.nbt.zlib").write_bytes(
                record(world, "", chunk_of(position)))
        row.update(bay_root=repr(loaded), quarantined_root=repr(quarantined))
        baseline = baseline or loaded
        report["world_bay"].append(row)
        print(f"{name}: PASS", flush=True)


def world_refusal(args, session: Path, evidence: Path, report: dict) -> None:
    world = session / "world-refusal"
    refused_chunk, control_chunk = chunk_of(REFUSED), chunk_of(CONTROL_MARKER)
    nether_chunk, unrelated_chunk = chunk_of(NETHER_MARKER), chunk_of(UNRELATED_MARKER)
    nether = "execute in minecraft:the_nether"
    marker = "minecraft:diamond_block"

    def put(position, block, dimension=""):
        return f"{dimension + ' run ' if dimension else ''}setblock {position[0]} {position[1]} {position[2]} {block}"

    # R0: the chunk scope.
    server = Server(args, session, evidence, "refusal-0")
    try:
        server.batch(["forceload add 160 160", "forceload add 192 160", nether + " run forceload add 960 960"],
                     "ARCE_BAY_FORCED")
        server.wait_loaded([f"execute if loaded {REFUSED[0]} 0 {REFUSED[2]}", f"execute if loaded {CONTROL_MARKER[0]} 0 "
                            f"{CONTROL_MARKER[2]}", nether + f" if loaded {NETHER_MARKER[0]} 70 {NETHER_MARKER[2]}"])
        baseline_audit = audit(server.save(), set(), set(), {})
        before = {"refused": record(world, "", refused_chunk), "control": record(world, "", control_chunk),
                  "nether": record(world, "DIM-1", nether_chunk)}
        server.changed([put(REFUSED, f"{BAY}{{{KEY}:{DEEP_SNBT}}}")], [REFUSED], "ARCE_BAY_DEEP_PLACED")
        server.changed([put(REFUSED_MARKER, marker), put(CONTROL_MARKER, marker), put(NETHER_MARKER, marker, nether)],
                       [REFUSED_MARKER, CONTROL_MARKER, NETHER_MARKER], "ARCE_BAY_MARKED")
        refused_save = server.save()
        lines = server.stop()
    except BaseException:
        server.abort()
        raise
    deep_line = {f"{REFUSED[0]}, {REFUSED[1]}, {REFUSED[2]}|QUARANTINED|false": 1}
    row = {"start": "refusal-0", "baseline_save": baseline_audit,
           "explicit_save": audit(refused_save, {refused_chunk}, {SAVE_REFUSAL}, {}),
           "log": audit(lines, {refused_chunk}, {SAVE_REFUSAL}, deep_line), "commands": server.commands}
    require(row["explicit_save"]["refused_chunks"] == [refused_chunk], "The explicit save did not refuse the bay chunk")
    after = {"refused": record(world, "", refused_chunk), "control": record(world, "", control_chunk),
             "nether": record(world, "DIM-1", nether_chunk)}
    require(after["refused"] == before["refused"], "The refused chunk record changed")
    require(after["control"] != before["control"]
            and marker in palette(typed_chunk(after["control"]), 0), "The same-Level control chunk did not save")
    require(after["nether"] != before["nether"]
            and marker in palette(typed_chunk(after["nether"]), 4), "The Nether chunk did not save")
    for key, value in after.items():
        (evidence / f"refusal-0-{key}.nbt.zlib").write_bytes(value)
    report["world_refusal"].append(row)
    print("refusal-0: PASS (chunk scope)", flush=True)

    # R1: the Level closed, the deep root never reached disk, and the chunk saves again.
    server = Server(args, session, evidence, "refusal-1")
    try:
        server.wait_loaded([f"execute if loaded {REFUSED[0]} 0 {REFUSED[2]}"])
        require(server.says(f"execute if block {REFUSED[0]} {REFUSED[1]} {REFUSED[2]} minecraft:air",
                            "ARCE_BAY_DEEP_ABSENT"), "The unkeepable root reached disk")
        server.changed([put(REFUSED_MARKER, "minecraft:emerald_block")], [REFUSED_MARKER], "ARCE_BAY_REMARKED")
        server.save()
        lines = server.stop()
    except BaseException:
        server.abort()
        raise
    row = {"start": "refusal-1", "log": audit(lines, set(), set(), {}), "commands": server.commands}
    require("minecraft:emerald_block" in palette(typed_chunk(record(world, "", refused_chunk)), 0),
            "The chunk did not save after the Level closed")
    report["world_refusal"].append(row)
    print("refusal-1: PASS (refusal ended with the Level)", flush=True)

    # R2: 257 denied coordinates exhaust the 256 the Level keeps; the Level then refuses every terrain save.
    bays = [(x * 16 + 2, 0, z * 16 + 2) for x, z in SATURATION_CHUNKS]
    server = Server(args, session, evidence, "refusal-2")
    try:
        server.batch(["forceload add 320 320 575 575", "forceload add 576 320 591 575", "forceload add 640 640"],
                     "ARCE_BAY_FORCED_MANY")
        server.wait_loaded([f"execute if loaded {x} 0 {z}" for x, _, z in bays]
                           + [f"execute if loaded {UNRELATED_MARKER[0]} 0 {UNRELATED_MARKER[2]}",
                              nether + f" if loaded {NETHER_MARKER[0]} 70 {NETHER_MARKER[2]}"], 300)
        baseline_audit = audit(server.save(), set(), set(), {})
        unrelated_before = record(world, "", unrelated_chunk)
        nether_before = record(world, "DIM-1", nether_chunk)
        server.changed([put(position, f"{BAY}{{{KEY}:{DEEP_SNBT}}}") for position in bays], bays, "ARCE_BAY_MANY_PLACED")
        server.changed([put(UNRELATED_MARKER, marker), put(NETHER_MARKER, "minecraft:lapis_block", nether)],
                       [UNRELATED_MARKER, NETHER_MARKER], "ARCE_BAY_SATURATION_MARKED")
        saturated_save = server.save()
        lines = server.stop()
    except BaseException:
        server.abort()
        raise
    # After saturation every dirty Overworld chunk may be refused: the bays', the unrelated one, the other forced
    # chunks and the spawn area. The refusal line names no dimension, so the Nether chunk sits where no
    # Overworld chunk is loaded, and its disk record proves the other Level kept saving.
    forced = {(x, z) for x in range(20, 37) for z in range(20, 36)} | {refused_chunk, control_chunk}
    spawn = {(x, z) for x in range(-12, 13) for z in range(-12, 13)}
    allowed = forced | spawn | {unrelated_chunk}
    required = set(SATURATION_CHUNKS) | {unrelated_chunk}
    guards = {f"{x}, {y}, {z}|QUARANTINED|false": 1 for x, y, z in bays}
    row = {"start": "refusal-2", "baseline_save": baseline_audit,
           "explicit_save": audit(saturated_save, allowed, {SAVE_REFUSAL, SATURATED}, {}),
           "log": audit(lines, allowed, {SAVE_REFUSAL, SATURATED}, guards), "commands_sent": len(server.commands)}
    refused = set(row["explicit_save"]["refused_chunks"])
    require(required <= refused and nether_chunk not in refused,
            "The saturated save did not refuse every bay chunk and the unrelated chunk")
    row["refused_beyond_required"] = sorted(refused - required)
    require(record(world, "", unrelated_chunk) == unrelated_before, "An unrelated chunk saved after saturation")
    nether_after = record(world, "DIM-1", nether_chunk)
    require(nether_after != nether_before and "minecraft:lapis_block" in palette(typed_chunk(nether_after), 4),
            "Another Level stopped saving")
    (evidence / "refusal-2-unrelated.nbt.zlib").write_bytes(record(world, "", unrelated_chunk))
    (evidence / "refusal-2-nether.nbt.zlib").write_bytes(nether_after)
    report["world_refusal"].append(row)
    print("refusal-2: PASS (Level scope)", flush=True)

    # R3: the next Level saves the unrelated chunk again; no bay root persisted.
    server = Server(args, session, evidence, "refusal-3")
    try:
        server.wait_loaded([f"execute if loaded {UNRELATED_MARKER[0]} 0 {UNRELATED_MARKER[2]}",
                            f"execute if loaded {bays[0][0]} 0 {bays[0][2]}"])
        require(server.says(f"execute if block {bays[0][0]} 0 {bays[0][2]} minecraft:air", "ARCE_BAY_MANY_ABSENT"),
                "An unkeepable root reached disk")
        server.changed([put(UNRELATED_MARKER, "minecraft:emerald_block")], [UNRELATED_MARKER], "ARCE_BAY_UNRELATED")
        server.save()
        lines = server.stop()
    except BaseException:
        server.abort()
        raise
    row = {"start": "refusal-3", "log": audit(lines, set(), set(), {}), "commands": server.commands}
    require("minecraft:emerald_block" in palette(typed_chunk(record(world, "", unrelated_chunk)), 0),
            "The unrelated chunk did not save in the next Level")
    report["world_refusal"].append(row)
    print("refusal-3: PASS (Level-wide refusal ended with the Level)", flush=True)


def configure(session: Path, port: int, level: str) -> None:
    properties = smoke.expected_server_properties(port, True)
    properties.update({"level-name": level, "level-type": "minecraft:flat", "max-tick-time": "120000",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                              encoding="ascii")


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    require(session.parent == intended and not session.exists() and not smoke.is_link_or_junction(session),
            "Use a new disposable directory directly under the evidence Temp folder")
    require(not evidence.exists() and evidence != session and session not in evidence.parents,
            "Evidence must be new and outside the disposable directory")
    for drive in (Path("C:/"), intended):
        require(shutil.disk_usage(drive).free >= 10 * 1024**3, "Native bay runs require 10 GiB free on C: and D:")
    libraries = args.source_server.resolve() / "libraries"
    require(libraries.is_dir() and not smoke.is_link_or_junction(libraries)
            and not any(smoke.is_link_or_junction(path) for path in libraries.rglob("*")), "Unsafe Forge libraries")
    require((libraries / f"net/minecraftforge/forge/{smoke.FORGE_COORDINATE}/win_args.txt").is_file(),
            "Missing pinned Forge arguments")
    session.mkdir()
    evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    owned = session / ".arce-w27b-bay-owned"
    owned.write_text(token, encoding="ascii")
    report = {"status": "IN_PROGRESS", "source_commit": args.source_commit, "jar": args.jar.name,
              "jar_sha256": smoke.digest_file(args.jar), "java": args.java, "world_bay": [], "world_refusal": [],
              "scope": "Native packaged server; typed NBT equality up to compound key order; no crash cut"}
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port()
        smoke.write_server_configuration(session, port, True)
        configure(session, port, "world-bay")
        world_bay(args, session, evidence, report)
        configure(session, port, "world-refusal")
        world_refusal(args, session, evidence, report)
        report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=f"{type(failure).__name__}: {failure}")
        raise
    finally:
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if (session.parent == intended and not smoke.is_link_or_junction(session) and owned.is_file()
                and owned.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source_server", type=Path, help="An installed Forge 47.4.10 server (its libraries are copied)")
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--session-dir", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--startup-timeout", type=float, default=300)
    args = parser.parse_args()
    try:
        run(args)
        print("PASS: bay root save and two restarts; refusal chunk and Level scopes with two restarts")
        return 0
    except (OSError, smoke.SmokeError, regions.FixtureError) as failure:
        print(f"FAIL: {failure}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
