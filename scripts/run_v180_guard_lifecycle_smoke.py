#!/usr/bin/env python3
"""Copy-only native checks of sticky refusal and native loaded-predicate cuts."""

from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import time
from pathlib import Path

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
ERROR_PREFIX = r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/ERROR\] "
EVENT_ERROR = re.compile(ERROR_PREFIX
                        + r"\[(?:ne\.mi\.ev\.EventBus|net\.minecraftforge\.eventbus\.EventBus)/EVENTBUS\]: "
                        + re.escape("Exception caught during firing event: " + tanks.SAVE_REFUSAL) + r"\s*$")
CHUNK_ERROR = re.compile(ERROR_PREFIX
                        + r"\[(?:minecraft/ChunkMap|net\.minecraft\.server\.level\.ChunkMap/?)\]: "
                        + r"Failed to save chunk (?:11,11|\[11, 11\])\s*$")
UNLOAD_BEGIN = re.compile(r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] "
                          + r"\[advancedrocketrycommunity/\]: ARCE_GUARD_UNLOAD_BEGIN chunk=11,11\s*$")


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
    index = process.wait_for(UNLOAD_BEGIN, remaining, start_at=start_at)
    if time.monotonic() > deadline:
        raise server.SmokeError("Native unload marker arrived after its original deadline")
    return process.lines[index].rstrip()


def relocate_copy_spawn(process, commands: list[str]) -> str:
    """Remove the original START-ticket halo from the copied fixture's target."""
    start = len(process.lines)
    commands.append(COPY_SPAWN_COMMAND)
    process.command(COPY_SPAWN_COMMAND)
    say_barrier(process, commands, "ARCE_GUARD_COPY_SPAWN_END")
    matches = [line.rstrip() for line in process.lines[start:] if COPY_SPAWN_SUCCESS.search(line)]
    require(len(matches) == 1, "Native copied-world spawn relocation was not confirmed exactly once")
    return matches[0]


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
    receipt = {"result": "IN_PROGRESS", "removal_cut": removal_cut}
    try:
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
        receipt["copy_spawn_setup"] = relocate_copy_spawn(process, commands)
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
        receipt.update(result="PASS", exit_code=0, record=check_record(actual, expected),
                       refusal=audit_refusal(process.lines), log_counts=server.log_audit_counts(process.lines))
        (evidence / "chunk-11-11.nbt.zlib").write_bytes(actual)
    except BaseException as error:
        receipt.update(result="FAIL", error=f"{type(error).__name__}: {error}")
        if process:
            process.abort()
        raise
    finally:
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
    runtime = work / "server"
    runtime.mkdir(parents=True)
    original = worlds.copy_world(args.source_world, runtime / "world")
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
                               "server_properties_sha256": properties})
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
