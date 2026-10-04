#!/usr/bin/env python3
"""Copy-only native checks of sticky refusal and native loaded-predicate cuts."""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import time
from pathlib import Path
import zlib

if __package__:
    from . import run_v180_tank_smoke as tanks
else:
    import run_v180_tank_smoke as tanks

server, regions, worlds = tanks.server, tanks.regions, tanks.worlds
require, write = tanks.require, tanks.write
CHUNK = tanks.CHUNK
REGION = Path("region/r.0.0.mca")
CULPRIT = (186, 180, 180)
COPY_SPAWN_COMMAND = "setworldspawn 256 74 -256 0"
COPY_SPAWN_SUCCESS = re.compile(r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] "
                                + r"\[minecraft/MinecraftServer\]: "
                                + re.escape("Set the world spawn point to 256, 74, -256 [0.0]") + r"\s*$")
FORCED_METADATA_BYTES = 4096
SOURCE_FORCED_SHA256 = "c9b7030c645d2995ac9be13d08964eb98f1942b2170c34b3f893100daa6acc50"
SOURCE_FORCED = frozenset({(11, 11), (16, 16), (16, -2), (24, 24), (32, 32)})
RETAINED_FORCED = frozenset({(11, 11), (32, 32)})


def console_feedback(text: str) -> re.Pattern:
    return re.compile(r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] "
                      + r"\[minecraft/MinecraftServer\]: " + re.escape(text) + r"\s*$")


# Literal fixture commands, never derived from decoded SavedData coordinates.
FORCED_REMOVALS = (
    ("execute in minecraft:overworld run forceload remove 256 256",
     console_feedback("Unmarked chunk [16, 16] in minecraft:overworld for force loading")),
    ("execute in minecraft:overworld run forceload remove 256 -32",
     console_feedback("Unmarked chunk [16, -2] in minecraft:overworld for force loading")),
    ("execute in minecraft:overworld run forceload remove 384 384",
     console_feedback("Unmarked chunk [24, 24] in minecraft:overworld for force loading")),
)
PROTECTED_FORCED_QUERIES = (
    ("execute in minecraft:overworld run forceload query 176 176",
     console_feedback("Chunk at [11, 11] in minecraft:overworld is marked for force loading")),
    ("execute in minecraft:overworld run forceload query 512 512",
     console_feedback("Chunk at [32, 32] in minecraft:overworld is marked for force loading")),
)
ERROR_PREFIX = r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/ERROR\] "
EVENT_ERROR = re.compile(ERROR_PREFIX
                        + r"\[(?:ne\.mi\.ev\.EventBus|net\.minecraftforge\.eventbus\.EventBus)/EVENTBUS\]: "
                        + re.escape("Exception caught during firing event: " + tanks.SAVE_REFUSAL) + r"\s*$")
CHUNK_ERROR = re.compile(ERROR_PREFIX
                        + r"\[(?:minecraft/ChunkMap|net\.minecraft\.server\.level\.ChunkMap/?)\]: "
                        + r"Failed to save chunk (?:11,11|\[11, 11\])\s*$")
UNLOAD_BEGIN = re.compile(r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] "
                          + r"\[advancedrocketrycommunity/\]: ARCE_GUARD_UNLOAD_BEGIN chunk=11,11\s*$")
STATE_COMMAND = "arce-guard-state"
CHUNK_STATE = re.compile(r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] "
                         + r'\[advancedrocketrycommunity/\]: ARCE_GUARD_STATE chunk=11,11 '
                         + r'no_save=(?P<no_save>true|false) holder="'
                         + r'(?P<holder>(?:[\x20-\x21\x23-\x5b\x5d-\x7e]|\\(?:["\\]|u[0-9a-f]{4})){0,256})'
                         + r'" truncated=(?P<truncated>true|false)\s*$')


def marker(text: str) -> re.Pattern:
    return re.compile(tanks.CONSOLE_SAY_PREFIX + re.escape(text) + r"\s*$")


def say_barrier(process, commands: list[str], text: str, timeout: float = 60) -> int:
    start = len(process.lines)
    command = "say " + text
    commands.append(command)
    process.command(command)
    process.wait_for(marker(text), timeout, start_at=start)
    return start


def wait_chunk(process, commands: list[str], loaded: bool, timeout: float = 60) -> None:
    """Observe execute's loaded predicate, not unload callbacks or a forced ticket."""
    deadline = time.monotonic() + timeout
    state = "LOADED" if loaded else "NOT_LOADED"
    observed = marker("ARCE_GUARD_" + state)
    for attempt in range(240):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        start = len(process.lines)
        probe = ("execute " + ("if" if loaded else "unless")
                 + " loaded 180 180 180 run say ARCE_GUARD_" + state)
        commands.append(probe)
        process.command(probe)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        say_barrier(process, commands, f"ARCE_GUARD_PROBE_END_{attempt}", remaining)
        if time.monotonic() > deadline:
            break
        if any(observed.search(line) for line in process.lines[start:]):
            return
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError(f"Native loaded predicate did not become {loaded} within {timeout} seconds")


def wait_unload_begin(process, commands: list[str], start_at: int, timeout: float = 60) -> str:
    """One deadline covers both the predicate change and a fresh real unload event."""
    deadline = time.monotonic() + timeout
    wait_chunk(process, commands, False, timeout)
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise server.SmokeError("Native unload observation exhausted its original deadline")
    commands.append(STATE_COMMAND)
    process.command(STATE_COMMAND)
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise server.SmokeError("Native state query exhausted its original unload deadline")
    index = process.wait_for(UNLOAD_BEGIN, remaining, start_at=start_at)
    if time.monotonic() > deadline:
        raise server.SmokeError("Native unload marker arrived after its original deadline")
    return process.lines[index].rstrip()


def chunk_state_observations(lines: list[str], start_at: int) -> list[dict]:
    """Capture bounded fresh diagnostic text; it is never an unload oracle."""
    observations = []
    for line in lines[start_at:]:
        if len(line) > 400:
            continue
        match = CHUNK_STATE.search(line)
        if match is None or len(match["holder"]) > 256:
            continue
        observations.append({"line": line.rstrip(), "no_save": match["no_save"] == "true",
                             "holder_text": match["holder"], "truncated": match["truncated"] == "true"})
        if len(observations) == 240:
            break
    return observations


def setup_remaining(deadline: float) -> float:
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise server.SmokeError("Native copied-world setup exhausted its shared deadline")
    return remaining


def setup_feedback(process, start_at: int, label: str) -> list[str]:
    end_markers = [index for index in range(start_at, len(process.lines))
                   if marker(label).search(process.lines[index])]
    require(len(end_markers) == 1, "Native copied-world setup barrier was not confirmed exactly once")
    return process.lines[start_at:end_markers[0]]


def relocate_copy_spawn(process, commands: list[str], deadline: float | None = None) -> str:
    """Remove the original START-ticket halo from the copied fixture's target."""
    if deadline is None:
        deadline = time.monotonic() + 60
    setup_remaining(deadline)
    start = len(process.lines)
    commands.append(COPY_SPAWN_COMMAND)
    process.command(COPY_SPAWN_COMMAND)
    say_barrier(process, commands, "ARCE_GUARD_COPY_SPAWN_END", setup_remaining(deadline))
    require(time.monotonic() <= deadline, "Native copied-world setup success arrived after its shared deadline")
    matches = [line.rstrip() for line in setup_feedback(process, start, "ARCE_GUARD_COPY_SPAWN_END")
               if COPY_SPAWN_SUCCESS.search(line)]
    require(len(matches) == 1, "Native copied-world spawn relocation was not confirmed exactly once")
    return matches[0]


def setup_batch(process, commands: list[str], operations: tuple, label: str, deadline: float) -> list[str]:
    start = len(process.lines)
    for command, _ in operations:
        setup_remaining(deadline)
        commands.append(command)
        process.command(command)
    say_barrier(process, commands, label, setup_remaining(deadline))
    require(time.monotonic() <= deadline, "Native copied-world setup success arrived after its shared deadline")
    successes = []
    fresh = setup_feedback(process, start, label)
    for _, expression in operations:
        matches = [line.rstrip() for line in fresh if expression.search(line)]
        require(len(matches) == 1, "Native copied-world force-mark command was not confirmed exactly once")
        successes.append(matches[0])
    return successes


def setup_copy_forced_marks(process, commands: list[str], bootstrap: bool) -> dict:
    """One bounded copy-only setup; restart never repeats neighbor removals."""
    require(type(bootstrap) is bool, "Unknown copied-world setup phase")
    deadline = time.monotonic() + 60
    before = setup_batch(process, commands, PROTECTED_FORCED_QUERIES,
                         "ARCE_GUARD_FORCED_BEFORE_END", deadline)
    spawn = relocate_copy_spawn(process, commands, deadline)
    removals = FORCED_REMOVALS if bootstrap else ()
    after = setup_batch(process, commands, removals + PROTECTED_FORCED_QUERIES,
                        "ARCE_GUARD_FORCED_AFTER_END", deadline)
    return {"bootstrap": bootstrap, "protected_before": before, "spawn": spawn,
            "removed": after[:len(removals)], "protected_after": after[len(removals):],
            "shared_setup_timeout_seconds": 60}


class ForcedMetadataReader(tanks.NbtReader):
    def _payload(self, tag_type: int, depth: int) -> tuple:
        return tag_type, super()._payload(tag_type, depth)


def check_forced_metadata(compressed: bytes, bootstrap: bool) -> dict:
    """Fixed tiny SavedData shape and native types, not a live ticket-map oracle."""
    require(type(bootstrap) is bool, "Unknown copied-world metadata phase")
    require(type(compressed) is bytes and 0 < len(compressed) <= FORCED_METADATA_BYTES,
            "Copied forced metadata exceeds its compressed bound")
    digest = hashlib.sha256(compressed).hexdigest()
    if bootstrap:
        require(len(compressed) == 79 and digest == SOURCE_FORCED_SHA256,
                "Copied bootstrap forced metadata differs from the pinned original")
    try:
        stream = zlib.decompressobj(31)
        expanded = stream.decompress(compressed, FORCED_METADATA_BYTES + 1)
        require(len(expanded) <= FORCED_METADATA_BYTES and stream.eof
                and not stream.unconsumed_tail and not stream.unused_data,
                "Copied forced metadata exceeds its expanded bound or has trailing bytes")
        require(expanded.startswith(b"\x0a\x00\x00"), "Copied forced metadata root name changed")
        reader = ForcedMetadataReader(expanded)
        _, root = reader.read_root()
    except (ValueError, zlib.error) as error:
        raise server.SmokeError("Invalid bounded copied forced metadata") from error
    require(set(root) == {"data", "DataVersion"} and root["DataVersion"] == (3, 3465),
            "Copied forced metadata unrelated fields/types or DataVersion changed")
    kind, data = root["data"]
    require(kind == 10 and isinstance(data, dict) and set(data) == {"Forced"},
            "Copied forced metadata data compound changed")
    kind, entries = data["Forced"]
    expected = SOURCE_FORCED if bootstrap else RETAINED_FORCED
    require(kind == 12 and isinstance(entries, list) and len(entries) == len(expected),
            "Copied forced metadata requires the phase-specific native LongArray")
    coords = []
    for value in entries:
        low, high = value & 0xffffffff, value >> 32 & 0xffffffff
        coords.append((low - 2**32 if low >= 2**31 else low, high - 2**32 if high >= 2**31 else high))
    require(len(set(coords)) == len(coords) and frozenset(coords) == expected and reader.tags == 4,
            "Copied forced metadata has missing, duplicate or extra phase-specific marks")
    return {"bytes": len(compressed), "sha256": digest, "expanded_bytes": len(expanded),
            "tag_count": reader.tags, "forced_type": kind, "data_version": 3465,
            "phase": "bootstrap" if bootstrap else "retained", "forced": [list(coord) for coord in sorted(coords)]}


def read_forced_metadata(world: Path, bootstrap: bool, capture: Path | None = None) -> dict:
    try:
        path = worlds.support.safe_path(world / "data/chunks.dat")
        raw = regions._read_regular(path, FORCED_METADATA_BYTES)
    except (OSError, ValueError) as error:
        raise server.SmokeError("Missing or unsafe bounded copied forced metadata") from error
    if capture is not None:
        capture.write_bytes(raw)
    return check_forced_metadata(raw, bootstrap)


def check_fixture(chunk: dict) -> dict:
    """Require the exact stopped oversized Tank06 fixture, not any rejected root."""
    require((chunk.get("xPos"), chunk.get("zPos")) == CHUNK, "Wrong fixture chunk identity")
    require(tanks.block_at(chunk, tanks.SAVE_MARKER_POSITION) == {"Name": "minecraft:air"},
            "Fixture marker must be absent")
    require(tanks.block_at(chunk, (180, 180, 180)) == {"Name": "minecraft:air"},
            "Packaged tank has a second block carrier")
    entities = chunk.get("block_entities", [])
    require(len(entities) == 4, "Expected exactly four retained BlockEntities")
    retained = {}
    for index in range(1, 5):
        position = (180 + index * 2, 180, 180)
        matches = [entity for entity in entities
                   if (entity.get("x"), entity.get("y"), entity.get("z")) == position]
        require(tanks.block_at(chunk, position) == {"Name": tanks.NS + ":pressurized_tank"}
                and len(matches) == 1 and matches[0].get("id") == tanks.NS + ":pressurized_tank",
                "Missing/duplicate exact tank carrier")
        expected = tanks.expected_root(index)
        if index == 3:
            expected = expected | {"extension": {"part0": [0] * 4096, "part1": [0] * 4096}}
        require(matches[0].get(tanks.ROOT) == expected, "Retained native root differs")
        retained[tanks.NAMES[index]] = expected
    return retained


def saved_record(world: Path) -> bytes:
    raw = regions._read_regular(world / REGION, regions.MAX_REGION_BYTES)
    return regions._region_chunk(raw, *CHUNK)


def check_record(actual: bytes, expected: bytes) -> dict:
    require(actual == expected, "Refused native terrain record changed")
    roots = check_fixture(regions._decode_chunk(actual, *CHUNK))
    return {"bytes": len(actual), "sha256": hashlib.sha256(actual).hexdigest(), "roots": roots}


def audit_refusal(lines: list[str]) -> dict:
    findings = tanks.audit_save_refusal(lines)
    require(all(EVENT_ERROR.search(line) or CHUNK_ERROR.search(line) for line in findings),
            "Unexpected native severity/logger in refusal audit")
    event_headers = [line for line in lines if EVENT_ERROR.search(line)]
    chunk_headers = [line for line in lines if CHUNK_ERROR.search(line)]
    require(len(event_headers) > 0 and len(event_headers) == len(chunk_headers),
            "Refusal logger headers are missing or unpaired")
    return {"event_bus_error_headers": len(event_headers), "chunk_map_error_headers": len(chunk_headers),
            "findings": findings, "log_bytes_utf8": len("\n".join(lines).encode("utf-8"))}


def native_cycle(runtime: Path, evidence: Path, command: list[str], expected: bytes, removal_cut: bool) -> dict:
    evidence.mkdir()
    write(evidence / "launch.json", {"command": command, "cwd": str(runtime)})
    process = None
    commands = []
    unload_start = None
    receipt = {"result": "IN_PROGRESS", "removal_cut": removal_cut}
    try:
        receipt["forced_preflight"] = read_forced_metadata(
            runtime / "world", removal_cut, evidence / "forced-before.dat")
        process = server.CapturedProcess(command, runtime, evidence / "stdout.txt")
        process.wait_for(server.READY_MARKER, 300)

        def send(text: str) -> None:
            commands.append(text)
            process.command(text)

        def assert_block(position: tuple[int, int, int], block: str, label: str) -> None:
            start = len(process.lines)
            coords = " ".join(map(str, position))
            send(f"execute if block {coords} {block} run say {label}")
            say_barrier(process, commands, label + "_END")
            require(any(marker(label).search(line) for line in process.lines[start:]),
                    "Native fixed-cell block check failed: " + label)

        def save(label: str) -> None:
            start = len(process.lines)
            send("save-all flush")
            process.wait_for(server.SAVE_MARKER, 300, start_at=start)
            say_barrier(process, commands, label + "_END")
            receipt[label] = audit_refusal(process.lines[start:])

        send("forceload add 176 176")
        wait_chunk(process, commands, True)
        receipt["copy_forced_setup"] = setup_copy_forced_marks(process, commands, removal_cut)
        receipt["copy_spawn_setup"] = receipt["copy_forced_setup"]["spawn"]
        assert_block(CULPRIT, tanks.NS + ":pressurized_tank", "ARCE_GUARD_ROOT_RELOADED")
        assert_block(tanks.SAVE_MARKER_POSITION, "minecraft:air", "ARCE_GUARD_MARKER_ABSENT")
        send("setblock 190 180 180 minecraft:stone")
        assert_block(tanks.SAVE_MARKER_POSITION, "minecraft:stone", "ARCE_GUARD_MARKER_MUTATED")
        save("initial_refusal")
        if removal_cut:
            send("setblock 186 180 180 minecraft:air")
            assert_block(CULPRIT, "minecraft:air", "ARCE_GUARD_ROOT_REMOVED")
            save("after_removal_refusal")
            unload_start = len(process.lines)
            send("forceload remove 176 176")
            receipt["unload_begin_line"] = wait_unload_begin(process, commands, unload_start)
            send("forceload add 176 176")
            wait_chunk(process, commands, True)
            assert_block(CULPRIT, tanks.NS + ":pressurized_tank", "ARCE_GUARD_REMOVAL_ROLLED_BACK")
            assert_block(tanks.SAVE_MARKER_POSITION, "minecraft:air", "ARCE_GUARD_MUTATION_ROLLED_BACK")
        send("stop")
        require(process.finish(300) == 0, "Native process did not stop cleanly")
        actual = saved_record(runtime / "world")
        receipt["forced_after_clean_stop"] = read_forced_metadata(
            runtime / "world", False, evidence / "forced-after.dat")
        receipt.update(result="PASS", exit_code=0, record=check_record(actual, expected),
                       refusal=audit_refusal(process.lines), log_counts=server.log_audit_counts(process.lines))
        (evidence / "chunk-11-11.nbt.zlib").write_bytes(actual)
    except BaseException as error:
        receipt.update(result="FAIL", error=f"{type(error).__name__}: {error}")
        if process:
            process.abort()
        raise
    finally:
        receipt["chunk_state_observations"] = (chunk_state_observations(process.lines, unload_start)
                                               if process is not None and unload_start is not None else [])
        write(evidence / "commands.json", commands)
        write(evidence / "receipt.json", receipt)
        for name in ("latest.log", "debug.log"):
            source = runtime / "logs" / name
            if source.is_file():
                shutil.copyfile(source, evidence / name)
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime-template", "source-world", "host-jar", "fixture-jar", "work-root"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    work = args.work_root.resolve()
    require(not work.exists(), "Use a fresh disposable work root")
    for source in (args.runtime_template, args.source_world, args.host_jar, args.fixture_jar):
        resolved = source.resolve()
        require(not work.is_relative_to(resolved) and not resolved.is_relative_to(work),
                "Work root overlaps an input")
    host = tanks.motors.artifact(args.host_jar, "1.20.1-1.8.0-dev")
    fixture = tanks.motors.artifact(args.fixture_jar)
    require(host["name"] != fixture["name"], "Host/fixture name collision")
    expected = saved_record(args.source_world)
    check_fixture(regions._decode_chunk(expected, *CHUNK))
    source_forced = read_forced_metadata(args.source_world, True)
    runtime = work / "server"
    runtime.mkdir(parents=True)
    original = worlds.copy_world(args.source_world, runtime / "world")
    require(read_forced_metadata(runtime / "world", True) == source_forced,
            "Copied forced metadata differs from its original input")
    library_source = args.runtime_template / "libraries"
    require(library_source.is_dir() and not server.is_link_or_junction(library_source), "Unsafe libraries")
    for child in library_source.rglob("*"):
        require(not server.is_link_or_junction(child), "Linked runtime library")
    shutil.copytree(library_source, runtime / "libraries")
    mods = runtime / "mods"
    mods.mkdir()
    for artifact in (host, fixture):
        target = mods / artifact["name"]
        shutil.copyfile(artifact["path"], target)
        require(server.digest_file(target) == artifact["sha256"], "Installed artifact changed")
    properties = server.write_server_configuration(runtime, server.allocate_port(), True)
    java, java_version = server.resolve_java(args.java)
    command = tanks.rocket._server_command(java)
    command[2] = "-Xmx2G"
    write(work / "inputs.json", {"host": host, "fixture": fixture, "java": java_version,
                               "runtime_template": str(args.runtime_template.resolve()),
                               "source_world": str(args.source_world.resolve()),
                               "server_properties_sha256": properties, "source_forced_metadata": source_forced})
    write(work / "source-world-files.json", original)
    (work / "source-chunk-11-11.nbt.zlib").write_bytes(expected)
    cycles = []
    try:
        cycles.append(native_cycle(runtime, work / "removal-availability-reload", command, expected, True))
        cycles.append(native_cycle(runtime, work / "restart-retrigger", command, expected, False))
    finally:
        unchanged = worlds.inventory(args.source_world) == original
        artifacts_unchanged = all(server.digest_file(Path(row["path"])) == row["sha256"] for row in (host, fixture))
        write(work / "input-postcheck.json", {"world_unchanged": unchanged, "artifacts_unchanged": artifacts_unchanged})
        require(unchanged and artifacts_unchanged, "Original input changed")
    write(work / "summary.json", {"result": "PASS", "cycles": cycles,
                                 "loaded_predicate_cut": True, "native_unload_begin": "OBSERVED",
                                 "actual_block_entity_disposal_callbacks": "NOT_INSTRUMENTED",
                                 "cross_store_conservation": "NOT_VERIFIED", "crash_recovery": "NOT_VERIFIED",
                                 "first_save_writer": "NOT_VERIFIED", "required_gates_passed": False})
    print("PASS: fixed native removal, loaded-predicate cut, old terrain rollback and restart retrigger")


if __name__ == "__main__":
    main()
