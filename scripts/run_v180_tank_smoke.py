#!/usr/bin/env python3
"""Copy-only packaged tank root, reduced-capacity and dropped-Item restart checks."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import struct
import time
import zlib
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server
    from . import run_v050_rocket_server_smoke as rocket
    from . import run_v180_motor_smoke as motors
    from . import v120_precision_world_fixture as regions
    from . import v140_migration_fixture as worlds
    from .inspect_celestial_saved_data import NbtReader
else:
    import run_dedicated_server_smoke as server
    import run_v050_rocket_server_smoke as rocket
    import run_v180_motor_smoke as motors
    import v120_precision_world_fixture as regions
    import v140_migration_fixture as worlds
    from inspect_celestial_saved_data import NbtReader

NS = "advancedrocketrycommunity"
ROOT = "arce_pressurized_tank"
CHUNK = (11, 11)
NAMES = ("current", "overflow", "tagged", "future", "corrupt")
REPORT_END = re.compile(r"\[minecraft/MinecraftServer\]: ARCE_TANK_REPORT cell=corrupt ")
SAVE_REFUSAL = "Refusing oversized tank chunk save; back up and repair first"
SAVE_ERROR = re.compile(r"Failed to save chunk (?:11,11|\[11, 11\])(?:$|\s)")
SAVE_MARKER_POSITION = (190, 180, 180)
CONSOLE_SAY_PREFIX = r"^(?:\[\d{2}:\d{2}:\d{2}\] )?\[Server thread/INFO\] \[minecraft/MinecraftServer\]: (?:\[Not Secure\] )?\[Server\] "
FULL_READY = re.compile(CONSOLE_SAY_PREFIX + r"ARCE_TANK_FULL_LOADED\s*$")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def wait_full_loaded(process, commands: list[str], timeout: float = 60) -> None:
    """A force-load ticket is not a FULL chunk; retry status within one deadline."""
    deadline = time.monotonic() + timeout
    for attempt in range(240):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        start = len(process.lines)
        probe = "execute if loaded 180 180 180 run say ARCE_TANK_FULL_LOADED"
        barrier = f"ARCE_TANK_LOAD_PROBE_END_{attempt}"
        for command in (probe, "say " + barrier):
            commands.append(command)
            process.command(command)
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        process.wait_for(re.compile(CONSOLE_SAY_PREFIX + barrier + r"\s*$"), remaining, start_at=start)
        if time.monotonic() > deadline:
            break
        if any(FULL_READY.search(line) for line in process.lines[start:]):
            return
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise server.SmokeError("Tank fixture chunk did not become FULL within its 60-second deadline")


def write(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def expected_root(index: int, current_amount: int = 11_345) -> dict:
    payload = {"FluidName": "minecraft:lava" if index == 2 else "minecraft:water",
               "Amount": (current_amount, 300_000, 5_000, 5_000, -1)[index]}
    if index == 2:
        payload["Tag"] = {"batch": "native-retained"}
    root = {"schema": 2 if index == 3 else 1, "fluid": payload}
    if index == 3:
        root["extension"] = "retain-verbatim"
    return root


def block_at(chunk: dict, position: tuple[int, int, int]) -> dict:
    """Use the audited exact-cell decoder without changing its module-global fixture."""
    require((chunk.get("xPos"), chunk.get("zPos")) == CHUNK, "Wrong tank chunk identity")
    require((position[0] >> 4, position[2] >> 4) == CHUNK, "Position outside tank chunk")
    # Palette local coordinates are unchanged by this whole-chunk translation.
    translated = chunk | {"xPos": motors.CHUNK[0], "zPos": motors.CHUNK[1]}
    translated_position = (position[0] - 48, position[1], position[2] - 48)
    return motors.block_at(translated, translated_position)


def check_chunk(chunk: dict, packaged: bool = False, current_amount: int = 11_345) -> dict:
    result = {}
    for index, name in enumerate(NAMES):
        position = (180 + index * 2, 180, 180)
        matches = [entity for entity in chunk.get("block_entities", [])
                   if (entity.get("x"), entity.get("y"), entity.get("z")) == position]
        if index == 0 and packaged:
            require(block_at(chunk, position) == {"Name": "minecraft:air"} and not matches,
                    "Packaged Item still has a second native tank carrier")
            continue
        require(block_at(chunk, position) == {"Name": f"{NS}:pressurized_tank"},
                "Tank missing at its exact saved coordinate")
        require(len(matches) == 1 and matches[0].get("id") == f"{NS}:pressurized_tank",
                "Missing/duplicated tank BlockEntity")
        require(matches[0].get(ROOT) == expected_root(index, current_amount),
                f"Tank {name} payload/schema/metadata changed")
        result[name] = matches[0][ROOT]
    return result


def decode_entities(compressed: bytes) -> dict:
    require(len(compressed) <= regions.MAX_COMPRESSED_CHUNK, "Oversized compressed entity chunk")
    stream = zlib.decompressobj()
    expanded = stream.decompress(compressed, regions.MAX_EXPANDED_CHUNK + 1)
    require(len(expanded) <= regions.MAX_EXPANDED_CHUNK and stream.eof
            and not stream.unconsumed_tail and not stream.unused_data, "Invalid/oversized entity NBT")
    root = NbtReader(expanded).read_root()
    require(isinstance(root, dict) and root.get("Position") == list(CHUNK), "Wrong entity chunk identity")
    return root


def check_item(entities: dict) -> dict:
    tanks = [entity for entity in entities.get("Entities", []) if isinstance(entity, dict)
             and isinstance(entity.get("Item"), dict) and entity["Item"].get("id") == f"{NS}:pressurized_tank"]
    require(len(tanks) == 1 and tanks[0].get("id") == "minecraft:item", "Expected one tank Item entity")
    item = tanks[0]["Item"]
    require(item == {"id": f"{NS}:pressurized_tank", "Count": 1, "tag": {ROOT: expected_root(0)}},
            "Dropped Item changed fluid/count/metadata or contains another resource copy")
    position = tanks[0].get("Pos", [])
    require(len(position) == 3 and all(isinstance(value, (int, float)) for value in position)
            and abs(position[0] - 180.5) < 2 and abs(position[1] - 180.5) < 2
            and abs(position[2] - 180.5) < 2, "Dropped Item left the fixed fixture cell")
    return item


def capture(runtime: Path, evidence: Path, packaged: bool) -> dict:
    region = regions._read_regular(runtime / "world/region/r.0.0.mca", regions.MAX_REGION_BYTES)
    compressed = regions._region_chunk(region, *CHUNK)
    result = {"banks": check_chunk(regions._decode_chunk(compressed, *CHUNK), packaged)}
    (evidence / "chunk-11-11.nbt.zlib").write_bytes(compressed)
    if packaged:
        entity_region = regions._read_regular(runtime / "world/entities/r.0.0.mca", regions.MAX_REGION_BYTES)
        entity_chunk = regions._region_chunk(entity_region, *CHUNK)
        result["item"] = check_item(decode_entities(entity_chunk))
        (evidence / "entities-11-11.nbt.zlib").write_bytes(entity_chunk)
    write(evidence / "saved-resources.json", result)
    write(evidence / "saved-chunk-identity.json", {"sha256": hashlib.sha256(compressed).hexdigest(),
                                                 "bytes": len(compressed)})
    return result


def lower_capacity(runtime: Path, evidence: Path) -> None:
    path = runtime / "config/advancedrocketrycommunity-common.toml"
    require(path.is_file() and not server.is_link_or_junction(path), "Missing/linked common config")
    before = path.read_bytes()
    text = before.decode("utf-8")
    changed, count = re.subn(r"(?m)^([ \t]*tankCapacityMultiplier[ \t]*=[ \t]*)1\.0[ \t\r]*$",
                             r"\g<1>0.25", text)
    require(count == 1, "Capacity config must change one existing exact default")
    after = changed.encode("utf-8")
    (evidence / "common-before.toml").write_bytes(before)
    (evidence / "common-after.toml").write_bytes(after)
    path.write_bytes(after)  # Exact config in this harness's newly-created runtime only.


def patch_oversized(runtime: Path, evidence: Path) -> bytes:
    """Expand one known future root in the stopped disposable server, retaining its sector allocation."""
    path = runtime / "world/region/r.0.0.mca"
    before = regions._read_regular(path, regions.MAX_REGION_BYTES)
    compressed = regions._region_chunk(before, *CHUNK)
    chunk = regions._decode_chunk(compressed, *CHUNK)
    check_chunk(chunk, packaged=True)
    require(block_at(chunk, SAVE_MARKER_POSITION) == {"Name": "minecraft:air"}, "Save marker cell must be fresh")
    expanded = zlib.decompress(compressed)  # Already bounded and fully decoded above.
    key, value = b"extension", b"retain-verbatim"
    needle = b"\x08" + struct.pack(">H", len(key)) + key + struct.pack(">H", len(value)) + value
    require(expanded.count(needle) == 1, "Oversized fixture marker is not unique")
    replacement = b"\x0a" + struct.pack(">H", len(key)) + key
    for part in (b"part0", b"part1"):
        replacement += b"\x07" + struct.pack(">H", len(part)) + part + struct.pack(">i", 4_096) + bytes(4_096)
    replacement += b"\x00"
    patched = zlib.compress(expanded.replace(needle, replacement))
    decoded = regions._decode_chunk(patched, *CHUNK)
    expected = expected_root(3) | {"extension": {"part0": [0] * 4_096, "part1": [0] * 4_096}}
    roots = [entity.get(ROOT) for entity in decoded.get("block_entities", [])
             if (entity.get("x"), entity.get("y"), entity.get("z")) == (186, 180, 180)]
    require(roots == [expected], "Oversized patch changed the wrong native carrier")
    index = (CHUNK[0] + CHUNK[1] * 32) * 4
    offset = int.from_bytes(before[index:index + 3], "big") * 4_096
    sectors = before[index + 3]
    record = struct.pack(">I", len(patched) + 1) + b"\x02" + patched
    require(len(record) <= sectors * 4_096, "Patch must fit the original chunk allocation")
    result = bytearray(before)
    result[offset:offset + sectors * 4_096] = record + bytes(sectors * 4_096 - len(record))
    require(regions._region_chunk(bytes(result), *CHUNK) == patched, "Patch failed to round-trip")
    (evidence / "region-before-patch.mca").write_bytes(before)
    (evidence / "chunk-11-11.nbt.zlib").write_bytes(patched)
    path.write_bytes(result)
    write(evidence / "patch.json", {"chunk": list(CHUNK), "root": ROOT, "array_bytes": 8_192,
                                   "before_sha256": hashlib.sha256(compressed).hexdigest(),
                                   "after_sha256": hashlib.sha256(patched).hexdigest()})
    return patched


def audit_save_refusal(lines: list[str]) -> list[str]:
    findings = server.scan_log(lines)
    require(findings and any(SAVE_REFUSAL in line for line in lines), "Missing explicit oversized tank save refusal")
    require(any(SAVE_ERROR.search(finding) for finding in findings), "Missing exact tank chunk-save failure")
    for finding in findings:
        require(SAVE_ERROR.search(finding) is not None
                or "Exception caught during firing event: " + SAVE_REFUSAL in finding,
                "Unexpected packaged-server log finding: " + finding)
    return findings


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runtime-template", "source-world", "v17-jar", "host-jar", "work-root"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    work = args.work_root.resolve()
    require(not work.exists(), "Use a fresh disposable evidence directory")
    for source in (args.runtime_template, args.source_world, args.v17_jar, args.host_jar, args.fixture_jar):
        if source:
            resolved = source.resolve()
            require(not work.is_relative_to(resolved) and not resolved.is_relative_to(work),
                    "Disposable runtime must be disjoint from every input")
    old = motors.artifact(args.v17_jar, "1.20.1-1.7.0-dev")
    current = motors.artifact(args.host_jar, "1.20.1-1.8.0-dev")
    fixture = motors.artifact(args.fixture_jar) if args.fixture_jar else None
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
        require(fixture["name"] not in (old["name"], current["name"]), "Fixture/host name collision")
        shutil.copyfile(fixture["path"], mods / fixture["name"])
        require(server.digest_file(mods / fixture["name"]) == fixture["sha256"], "Installed fixture changed")
    properties = server.write_server_configuration(runtime, server.allocate_port(), True)
    java, java_version = server.resolve_java(args.java)
    command = rocket._server_command(java)
    command[2] = "-Xmx2G"
    write(work / "inputs.json", {"old": old, "current": current, "fixture": fixture,
                               "source_world": str(args.source_world.resolve()),
                               "runtime_template": str(args.runtime_template.resolve()),
                               "server_properties_sha256": properties, "java": java_version})
    write(work / "source-world-files.json", original_world)
    receipts = []

    def phase(name: str, host: dict, actions: tuple[str, ...] = (), packaged: bool = False,
              oversized_chunk: bytes | None = None):
        directory = work / name
        directory.mkdir()
        for installed_host in (old, current):
            installed = mods / installed_host["name"]
            if installed.exists():
                installed.unlink()  # Only the two exact files installed in this fresh runtime.
        shutil.copyfile(host["path"], mods / host["name"])
        require(server.digest_file(mods / host["name"]) == host["sha256"], "Installed host changed")
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

            query("forceload add 176 176", motors.FORCELOAD_RESULT)
            query("forceload query 176 176", re.compile(r"Chunk at \[11, ?11\].*marked for force loading"))
            wait_full_loaded(process, commands)
            for action in actions:
                query("arce tank release-test " + action, REPORT_END)
            if host == current and oversized_chunk is None:
                start = len(process.lines)
                query("arce tank release-test report", REPORT_END)
                reports = [line for line in process.lines[start:] if "ARCE_TANK_REPORT" in line]
                for cell in ("future", "corrupt"):
                    require(any(f"cell={cell}" in line and "repair=true fluid_cap=false" in line for line in reports),
                            "Unsupported tank exposed resources")
                require(any("cell=overflow" in line and "amount=300000" in line for line in reports),
                        "Overflow fluid was truncated")
                expected_capacity = 64_000 if name == "v18-tank-seed" else 16_000
                require(any("cell=overflow" in line and f"capacity={expected_capacity} " in line for line in reports),
                        "Tank capacity setting did not apply")
                receipt["reports"] = reports
            if oversized_chunk is not None:
                query("setblock 190 180 180 minecraft:stone", re.compile(r"Changed the block at 190, ?180, ?180"))
            query("save-all flush", server.SAVE_MARKER, 300)
            commands.append("stop")
            process.command("stop")
            require(process.finish(300) == 0, "Packaged server failed clean stop")
            if oversized_chunk is not None:
                receipt["expected_refusal_findings"] = audit_save_refusal(process.lines)
                region = regions._read_regular(runtime / "world/region/r.0.0.mca", regions.MAX_REGION_BYTES)
                persisted = regions._region_chunk(region, *CHUNK)
                require(persisted == oversized_chunk, "Refused save overwrote the original native chunk")
                require(block_at(regions._decode_chunk(persisted, *CHUNK), SAVE_MARKER_POSITION)
                        == {"Name": "minecraft:air"}, "Whole-chunk veto persisted another block mutation")
                (directory / "chunk-11-11.nbt.zlib").write_bytes(persisted)
                result = {"oversized_chunk_unchanged": True, "other_block_change_not_persisted": True}
            else:
                require(not server.scan_log(process.lines), "Unexpected packaged-server log findings")
                result = capture(runtime, directory, packaged) if host == current else None
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

    phase("v17-saved-world", old)
    seeded = phase("v18-tank-seed", current, ("prepare", "drain"))
    config_evidence = work / "capacity-change"
    config_evidence.mkdir()
    lower_capacity(runtime, config_evidence)
    for name in ("restart-1", "restart-2"):
        require(phase(name, current) == seeded, "Same-world restart changed tank roots")
    packaged = phase("package-item", current, ("package",), True)
    require(packaged["banks"] == {name: root for name, root in seeded["banks"].items() if name != "current"},
            "Packaging one tank changed another bank")
    require(phase("restart-item", current, packaged=True) == packaged, "Dropped Item changed on restart")
    patch_evidence = work / "oversized-patch"
    patch_evidence.mkdir()
    oversized = patch_oversized(runtime, patch_evidence)
    phase("oversized-refusal", current, oversized_chunk=oversized)
    require(worlds.inventory(args.source_world) == original_world, "Historical input world changed")
    for bound in (old, current, fixture):
        if bound:
            require(server.digest_file(Path(bound["path"])) == bound["sha256"], "Input JAR changed")
    write(work / "summary.json", {"result": "PASS", "phases": receipts, "same_world_restarts": 3,
                                 "source_world_unchanged": True, "client_evidence": "NOT_PERFORMED",
                                 "crash_atomicity": "NOT_CLAIMED", "oversized_native_save": "PASS",
                                 "required_gates_passed": False})
    print("PASS: tank roots, reduced capacity, BE/Item restarts and oversized whole-chunk save refusal")


if __name__ == "__main__":
    main()
