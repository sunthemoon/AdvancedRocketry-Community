#!/usr/bin/env python3
"""Conditional copy-only Pump native schema/search checkpoint and clean-restart evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import time
import zlib
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import run_v050_rocket_server_smoke as rocket
    from . import run_v180_motor_smoke as motors
    from . import v120_precision_world_fixture as regions
    from . import v140_migration_fixture as worlds
    from . import inspect_celestial_saved_data as nbt
else:
    import run_dedicated_server_smoke as server
    import run_v050_rocket_server_smoke as rocket
    import run_v180_motor_smoke as motors
    import v120_precision_world_fixture as regions
    import v140_migration_fixture as worlds
    import inspect_celestial_saved_data as nbt

NS = "advancedrocketrycommunity"
ROOT = "arce_pump"
CHUNK = (13, 13)
NAMES = ("current", "overflow", "future", "corrupt", "unsupported", "tagged", "search")
POSITIONS = tuple((210 + index * 2, 200, 212) for index in range(6)) + ((216, 200, 216),)
INTAKE = (216, 136, 216)
SOURCE = (217, 136, 216)
OWNER = [402_653_184, 0, 0, 6]  # UUID 18000000-0000-0000-0000-000000000006, fixture only.
IDENTITY = ".v180-pump-smoke-identity.json"
# Match the reviewed Tank r5 stdout / latest.log forms, not nested/echoed log text.
CONSOLE = r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] \[minecraft/MinecraftServer\]: "
CONSOLE_SAY = CONSOLE + r"(?:\[Not Secure\] )?\[Server\] "
FULL_READY = re.compile(CONSOLE_SAY + r"ARCE_PUMP_FULL_LOADED[ \t]*$")
ROW = re.compile(CONSOLE + r"ARCE_PUMP_REPORT (\{[^\r\n]*\})[ \t]*$")
END = re.compile(CONSOLE + r"ARCE_PUMP_REPORT_END phase=(checkpoint|terminal)[ \t]*$")
WAIT = re.compile(CONSOLE + r"ARCE_PUMP_REPORT_WAIT[ \t]*$")
REPORT_PREFIX = re.compile(CONSOLE + r"ARCE_PUMP_REPORT(?: |$)")
MAX_REPORT_BYTES = 4_096
MAX_REPORT_FIELDS = 9
MAX_BARRIERS = 240
WAIT_SECONDS = 60
STOP_MARKER = re.compile(CONSOLE + r"Stopping server[ \t]*$")
SAVED_AFTER_STOP = re.compile(CONSOLE + r"ThreadedAnvilChunkStorage: All dimensions are saved[ \t]*$")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise server.SmokeError(message)


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def expected_root(index: int, phase: str) -> object:
    require(phase in {"checkpoint", "terminal"}, "Unknown Pump evidence phase")
    if index == 4:
        return "opaque-pump-root"
    fluid = {"FluidName": "minecraft:water", "Amount": (12_345, 16_001, 8_000, -1, 0, 7_000, 0)[index]}
    root = {"schema": 2 if index == 2 else 1,
            "energy": (7_654, 437, 999, 17, 0, 4_321, 100)[index],
            "cooldown": 3 if index == 1 else 0, "fluid": fluid}
    if index in (2, 5):
        fluid.update(FluidName="minecraft:lava", Tag={"batch": "native-retained"})
    if index == 2:
        root["extension"] = "native-future-retained"
    if index == 6:
        root["owner"] = list(OWNER)
        root["fluid"] = {} if phase == "checkpoint" else {"FluidName": "minecraft:lava", "Amount": 1_000}
        root["energy"] = 100 if phase == "checkpoint" else 0
    return root


def strict_equal(left: object, right: object) -> bool:
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        return left.keys() == right.keys() and all(strict_equal(left[key], right[key]) for key in left)
    if isinstance(left, list):
        return len(left) == len(right) and all(strict_equal(a, b) for a, b in zip(left, right))
    return left == right


def block_at(chunk: dict, position: tuple[int, int, int]) -> dict:
    require((chunk.get("xPos"), chunk.get("zPos")) == CHUNK, "Wrong Pump chunk identity")
    require((position[0] >> 4, position[2] >> 4) == CHUNK, "Pump fixture outside its fixed chunk")
    offset = (CHUNK[0] - motors.CHUNK[0]) * 16
    translated = chunk | {"xPos": motors.CHUNK[0], "zPos": motors.CHUNK[1]}
    return motors.block_at(translated, (position[0] - offset, position[1], position[2] - offset))


def source_state_valid(state: dict, phase: str) -> bool:
    require(phase in {"checkpoint", "terminal"}, "Unknown Pump source phase")
    if phase == "checkpoint":
        return state == {"Name": "minecraft:lava", "Properties": {"level": "0"}}
    return state == {"Name": "minecraft:air"} or any(
        state == {"Name": "minecraft:lava", "Properties": {"level": str(level)}} for level in range(1, 16))


def check_chunk(chunk: dict, phase: str) -> dict:
    entities = chunk.get("block_entities", [])
    require(isinstance(entities, list) and all(isinstance(entity, dict) for entity in entities), "Invalid BE collection")
    pumps = [entity for entity in entities if entity.get("id") == f"{NS}:pump"]
    require(len(pumps) == len(NAMES), "Unexpected second/missing native Pump carrier")
    roots = {}
    for index, (name, position) in enumerate(zip(NAMES, POSITIONS)):
        require(block_at(chunk, position) == {"Name": f"{NS}:pump"}, "Pump block differs at its exact cell")
        matches = [entity for entity in entities if (entity.get("x"), entity.get("y"), entity.get("z")) == position]
        require(len(matches) == 1 and matches[0].get("id") == f"{NS}:pump", "Missing/duplicate Pump BE identity")
        require(ROOT in matches[0] and strict_equal(matches[0][ROOT], expected_root(index, phase)),
                "Pump " + name + " root/schema/owner/resource metadata changed or gained frontier fields")
        roots[name] = matches[0][ROOT]
    require(source_state_valid(block_at(chunk, SOURCE), phase), "Pump source identity/removal state changed")
    if phase == "checkpoint":
        require(block_at(chunk, INTAKE) == {"Name": "minecraft:lava", "Properties": {"level": "1"}},
                "First-search intake was changed before restart")
    return roots


class TypedNbtReader(nbt.NbtReader):
    """Retain wire tag IDs while reusing the existing reader's hard depth/tag/length bounds."""

    def _payload(self, tag_type: int, depth: int) -> tuple:
        return tag_type, super()._payload(tag_type, depth)


def expected_typed(value: object, key: str = "") -> tuple:
    if isinstance(value, dict):
        return 10, {name: expected_typed(child, name) for name, child in value.items()}
    if isinstance(value, str):
        return 8, value
    if type(value) is int:
        return 3, value
    require(key == "owner" and type(value) is list and all(type(item) is int for item in value),
            "Unknown expected native tag shape")
    return 11, value


def check_native_schema(compressed: bytes, phase: str) -> dict:
    """Independently verify exact native tag IDs; equal Python integers are not sufficient."""
    require(len(compressed) <= regions.MAX_COMPRESSED_CHUNK, "Oversized compressed Pump chunk")
    stream = zlib.decompressobj()
    expanded = stream.decompress(compressed, regions.MAX_EXPANDED_CHUNK + 1)
    require(len(expanded) <= regions.MAX_EXPANDED_CHUNK and stream.eof
            and not stream.unconsumed_tail and not stream.unused_data, "Invalid/oversized Pump native NBT")
    root_type, root = TypedNbtReader(expanded).read_root()
    require(root_type == 10 and root.get("xPos") == (3, CHUNK[0]) and root.get("zPos") == (3, CHUNK[1]),
            "Wrong typed native Pump chunk identity")
    entities_type, entities = root.get("block_entities", (0, []))
    require(entities_type == 9 and isinstance(entities, list), "Invalid typed native BE list")
    pumps = []
    for kind, entity in entities:
        require(kind == 10 and isinstance(entity, dict), "Invalid typed native BE compound")
        if entity.get("id") == (8, f"{NS}:pump"):
            pumps.append(entity)
    require(len(pumps) == len(NAMES), "Wrong typed native Pump carrier count")
    types = {}
    for index, (name, position) in enumerate(zip(NAMES, POSITIONS)):
        matches = [entity for entity in pumps if tuple(entity.get(axis) for axis in ("x", "y", "z"))
                   == tuple((3, coordinate) for coordinate in position)]
        require(len(matches) == 1 and matches[0].get(ROOT) == expected_typed(expected_root(index, phase)),
                "Pump native wire schema/root differs (including tag widths or transient frontier)")
        types[name] = matches[0][ROOT]
    return types


def unique_json_object(pairs: list[tuple]) -> dict:
    require(len(pairs) <= MAX_REPORT_FIELDS, "Pump report object exceeds field bound")
    value = {}
    for name, child in pairs:
        require(name not in value, "Duplicate Pump JSON report key")
        value[name] = child
    return value


def barrier_lines(process, start: int, barrier: str) -> list[str]:
    marker = re.compile(CONSOLE_SAY + barrier + r"[ \t]*$")
    matches = [index for index, line in enumerate(process.lines[start:], start) if marker.search(line)]
    require(len(matches) == 1, "Missing/duplicate authoritative Pump barrier")
    return process.lines[start:matches[0] + 1]


def check_clean_stop(lines: list[str]) -> dict:
    stopping = [index for index, line in enumerate(lines) if STOP_MARKER.search(line)]
    require(len(stopping) == 1, "Missing/duplicate genuine clean-stop trace")
    saved = [index for index, line in enumerate(lines) if index > stopping[0] and SAVED_AFTER_STOP.search(line)]
    require(len(saved) >= 1, "No completed dimension-save trace after clean stop")
    return {"stopping_line": stopping[0] + 1, "saved_after_stop_line": saved[-1] + 1}


def check_reports(lines: list[str], phase: str) -> list[dict]:
    require(phase in {"checkpoint", "terminal"}, "Unknown Pump report phase")
    rows = []
    ends = []
    for line in lines:
        match = ROW.search(line)
        require(not REPORT_PREFIX.search(line) or match is not None, "Malformed Pump JSON report line")
        if match:
            require(len(rows) < len(NAMES), "Pump report exceeds seven-row bound")
            require(not ends, "Pump report row appeared after its end marker")
            require(len(match.group(1).encode("utf-8")) <= MAX_REPORT_BYTES, "Oversized Pump UTF-8 report")
            try:
                row = json.loads(match.group(1), object_pairs_hook=unique_json_object)
            except (ValueError, RecursionError) as error:
                raise server.SmokeError("Invalid Pump JSON report") from error
            require(type(row) is dict and len(row) == MAX_REPORT_FIELDS, "Wrong Pump JSON report object shape")
            rows.append(row)
        end = END.search(line)
        if end:
            require(len(ends) == 0 and len(rows) == len(NAMES), "Pump end marker duplicate or before complete rows")
            ends.append(end.group(1))
        require(WAIT.search(line) is None, "WAIT mixed into completed Pump report")
    require(len(rows) == len(NAMES) and [row.get("cell") for row in rows] == list(NAMES), "Missing/duplicate/order-mismatched Pump report")
    for index, row in enumerate(rows):
        refused = index in (1, 2, 3, 4)
        root = expected_root(index, phase)
        expected = {"phase": phase, "cell": NAMES[index], "repair": refused,
                    "fluid_cap": not refused, "energy_cap": not refused,
                    "energy": 0 if refused else root["energy"],
                    "amount": 0 if refused else root["fluid"].get("Amount", 0), "cooldown": 0,
                    "status": "REPAIR_REQUIRED" if refused else "NO_OWNER" if index != 6
                    else "SEARCHING" if phase == "checkpoint" else "NO_ENERGY"}
        require(strict_equal(row, expected), "Pump runtime report disagrees with its required typed/native state")
    require(ends == [phase], "Missing exact console end marker")
    return rows


def wait_full_loaded(process, commands: list[str], timeout: float = WAIT_SECONDS) -> None:
    require(0 < timeout <= WAIT_SECONDS, "Pump readiness timeout exceeds its agreed deadline")
    deadline = time.monotonic() + timeout
    for attempt in range(MAX_BARRIERS):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        start = len(process.lines)
        barrier = f"ARCE_PUMP_LOAD_END_{attempt}"
        for text in ("execute if loaded 216 200 216 run say ARCE_PUMP_FULL_LOADED", "say " + barrier):
            commands.append(text)
            process.command(text)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        process.wait_for(re.compile(CONSOLE_SAY + barrier + r"[ \t]*$"), remaining, start_at=start)
        if time.monotonic() > deadline:
            break
        if any(FULL_READY.search(line) for line in barrier_lines(process, start, barrier)):
            return
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError("Pump chunk did not become FULL within one 60-second deadline")


def wait_terminal(process, commands: list[str], timeout: float = WAIT_SECONDS) -> list[dict]:
    require(0 < timeout <= WAIT_SECONDS, "Pump report timeout exceeds its agreed deadline")
    deadline = time.monotonic() + timeout
    for attempt in range(MAX_BARRIERS):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        start = len(process.lines)
        barrier = f"ARCE_PUMP_REPORT_BARRIER_{attempt}"
        for text in ("arce pump release-test report", "say " + barrier):
            commands.append(text)
            process.command(text)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        process.wait_for(re.compile(CONSOLE_SAY + barrier + r"[ \t]*$"), remaining, start_at=start)
        if time.monotonic() > deadline:
            break
        lines = barrier_lines(process, start, barrier)
        if any(END.search(line) for line in lines):
            return check_reports(lines, "terminal")
        require(sum(WAIT.search(line) is not None for line in lines) == 1
                and not any(REPORT_PREFIX.search(line) for line in lines),
                "Missing/ambiguous Pump fixture hook/result; refusal cannot be treated as progress")
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError("Pump did not finish its bounded source reconstruction within one 60-second deadline")


def capture(runtime: Path, evidence: Path, phase: str | None) -> dict:
    region = regions._read_regular(runtime / "world/region/r.0.0.mca", regions.MAX_REGION_BYTES)
    compressed = regions._region_chunk(region, *CHUNK)
    # Preserve the stopped actual payload even if any subsequent evidence check refuses it.
    (evidence / "chunk-13-13.nbt.zlib").write_bytes(compressed)
    write(evidence / "chunk-identity.json", {"chunk": list(CHUNK), "bytes": len(compressed), "sha256": hashlib.sha256(compressed).hexdigest()})
    chunk = regions._decode_chunk(compressed, *CHUNK)
    if phase is None:
        require(not any(entity.get("id") == f"{NS}:pump" for entity in chunk.get("block_entities", [])), "Historical fixture already contains a Pump")
        roots = {}
    else:
        roots = check_chunk(chunk, phase)
        write(evidence / "native-wire-schema.json", check_native_schema(compressed, phase))
    write(evidence / "saved-pump-roots.json", roots)
    return roots


def bind_artifact(path: Path, expected_sha256: str, version: str | None = None) -> dict:
    require(re.fullmatch(r"[0-9a-f]{64}", expected_sha256) is not None, "Expected artifact SHA-256 must be explicit")
    bound = motors.artifact(path, version)
    require(bound["sha256"] == expected_sha256, "Artifact differs from its frozen expected SHA-256")
    return bound


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime-template", "source-world", "v17-jar", "host-jar", "work-root"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--v17-sha256", required=True)
    parser.add_argument("--host-sha256", required=True)
    parser.add_argument("--fixture-jar", type=Path)
    parser.add_argument("--fixture-sha256")
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    require(bool(args.fixture_jar) == bool(args.fixture_sha256), "Fixture path/hash must be supplied together")
    work = args.work_root.resolve()
    require(not work.exists(), "Use a fresh disposable evidence/runtime directory")
    for path in (args.runtime_template, args.source_world, args.v17_jar, args.host_jar, args.fixture_jar):
        if path:
            source = path.resolve()
            require(not work.is_relative_to(source) and not source.is_relative_to(work), "Work must be disjoint from every input")
    old = bind_artifact(args.v17_jar, args.v17_sha256, "1.20.1-1.7.0-dev")
    current = bind_artifact(args.host_jar, args.host_sha256, "1.20.1-1.8.0-dev")
    fixture = bind_artifact(args.fixture_jar, args.fixture_sha256) if args.fixture_jar else None
    runtime = work / "server"
    runtime.mkdir(parents=True)
    original_world = worlds.copy_world(args.source_world, runtime / "world")
    libraries = args.runtime_template / "libraries"
    require(libraries.is_dir() and not server.is_link_or_junction(libraries), "Missing/linked runtime libraries")
    for child in libraries.rglob("*"):
        require(not server.is_link_or_junction(child), "Linked runtime library")
    shutil.copytree(libraries, runtime / "libraries")
    mods = runtime / "mods"
    mods.mkdir()
    if fixture:
        require(fixture["name"] not in {old["name"], current["name"]}, "Fixture/host filename collision")
        shutil.copyfile(fixture["path"], mods / fixture["name"])
        require(server.digest_file(mods / fixture["name"]) == fixture["sha256"], "Copied fixture changed")
    properties = server.write_server_configuration(runtime, server.allocate_port(), True)
    java, java_version = server.resolve_java(args.java)
    command = rocket._server_command(java)
    command[2] = "-Xmx2G"
    dependency_files = [Path(module.__file__).resolve() for module in (server, rocket, motors, regions, worlds, nbt)]
    dependency_files.append(Path(__file__).resolve())
    dependencies = [{"path": str(path), "sha256": server.digest_file(path)} for path in dependency_files]
    write(work / "inputs.json", {"old": old, "current": current, "fixture": fixture, "java": java_version,
                                "source_world": str(args.source_world.resolve()), "runtime_template": str(args.runtime_template.resolve()),
                                "server_properties_sha256": properties, "script_sha256": server.digest_file(Path(__file__)),
                                "harness_dependencies": dependencies})
    write(work / "source-world-files.json", original_world)
    marker = runtime / "world" / IDENTITY
    require(not marker.exists() and not server.is_link_or_junction(marker), "Pump identity marker already present")
    write(marker, {"world_identity": hashlib.sha256((str(work) + current["sha256"] + properties).encode()).hexdigest()})
    marker_hash = server.digest_file(marker)
    receipts = []

    def phase(name: str, host: dict, stage: str | None):
        evidence = work / name
        evidence.mkdir()
        for installed in (old, current):
            path = mods / installed["name"]
            if path.exists():
                path.unlink()  # Only these two exact files in this newly created runtime.
        shutil.copyfile(host["path"], mods / host["name"])
        require(server.digest_file(mods / host["name"]) == host["sha256"], "Installed host changed")
        write(evidence / "launch.json", {"cwd": str(runtime), "command": command, "host": host})
        process, commands = None, []
        receipt = {"phase": name, "stage": stage, "result": "IN_PROGRESS", "host_sha256": host["sha256"]}
        try:
            process = server.CapturedProcess(command, runtime, evidence / "stdout.txt")
            process.wait_for(server.READY_MARKER, 300)
            for text in ("forceload add 208 208", "forceload query 208 208"):
                start = len(process.lines)
                commands.append(text); process.command(text)
                result = motors.FORCELOAD_RESULT if text.startswith("forceload add") else re.compile(r"Chunk at \[13, ?13\].*marked for force loading")
                process.wait_for(result, 60, start_at=start)
            wait_full_loaded(process, commands)
            if stage == "checkpoint":
                start = len(process.lines)
                text = "arce pump release-test prepare-search-stop"
                deadline = time.monotonic() + WAIT_SECONDS
                commands.append(text); process.command(text)
                remaining = deadline - time.monotonic()
                require(remaining > 0, "Pump checkpoint command exceeded its original deadline")
                process.wait_for(END, remaining, start_at=start)
                require(time.monotonic() <= deadline, "Pump checkpoint report woke after its original deadline")
                receipt["reports"] = check_reports(process.lines[start:], stage)
                # The fixed checkpoint hook requests an ordinary clean halt after its one real search tick.
            else:
                if stage == "terminal":
                    receipt["reports"] = wait_terminal(process, commands)
                start = len(process.lines)
                commands.append("save-all flush"); process.command("save-all flush")
                process.wait_for(server.SAVE_MARKER, 300, start_at=start)
                commands.append("stop"); process.command("stop")
            require(process.finish(300) == 0, "Pump packaged server did not clean-stop")
            receipt["clean_stop"] = check_clean_stop(process.lines)
            require(not server.scan_log(process.lines), "Unexpected native-server log findings")
            result = capture(runtime, evidence, stage)
            require(server.digest_file(marker) == marker_hash, "Same-world identity marker changed")
            require(server.digest_file(runtime / server.SERVER_PROPERTIES_IDENTITY_FILE) == properties, "Startup properties identity changed")
            level = regions._read_regular(runtime / "world/level.dat", 16 * 1024**2)
            receipt.update(result="PASS", exit_code=0, log_counts=server.log_audit_counts(process.lines),
                           world_identity_sha256=marker_hash, level_dat_sha256=hashlib.sha256(level).hexdigest())
        except BaseException as error:
            receipt.update(result="FAIL", error=f"{type(error).__name__}: {error}")
            if process:
                process.abort()
            raise
        finally:
            write(evidence / "receipt.json", receipt); write(evidence / "commands.json", commands)
            for name in ("latest.log", "debug.log"):
                path = runtime / "logs" / name
                if path.exists():
                    shutil.copyfile(path, evidence / name)
        receipts.append(receipt)
        return result

    phase("v17-saved-world", old, None)
    phase("v18-checkpoint", current, "checkpoint")
    terminal = phase("restart-1", current, "terminal")
    require(phase("restart-2", current, "terminal") == terminal, "Second clean restart changed retained Pump roots")
    require(worlds.inventory(args.source_world) == original_world, "Historical input world changed")
    for artifact in (old, current, fixture):
        if artifact:
            require(server.digest_file(Path(artifact["path"])) == artifact["sha256"], "Input artifact changed")
    for dependency in dependencies:
        require(server.digest_file(Path(dependency["path"])) == dependency["sha256"], "Harness dependency changed during execution")
    write(work / "summary.json", {"result": "PASS", "phases": receipts, "same_world_restarts": 2,
                                 "source_world_unchanged": True, "client_evidence": "NOT_PERFORMED",
                                 "crash_atomicity": "NOT_CLAIMED", "required_gates_passed": False})
    print("PASS: native Pump schema retention, one-source reconstruction and two clean same-world restarts")


if __name__ == "__main__":
    main()
