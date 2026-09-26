#!/usr/bin/env python3
"""Run six bounded packaged-server cycles for opaque external rocket cargo recovery.

Requires an unused disposable server containing only an installed libraries tree.
This uses a synthetic pre-commit journal, not a crash or power-loss checkpoint.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import sys
import time
import tomllib
import uuid
import zipfile
import zlib
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from . import run_dedicated_server_smoke as server_smoke
    from . import run_v050_rocket_server_smoke as rocket_smoke
    from . import v120_precision_world_fixture as region_nbt
    from .inspect_celestial_saved_data import NbtReader
    from .run_v100_transaction_forced_stop import read_journal, uuid_from_nbt
    from .v100_beta_world_fixture import wait_condition
else:
    import run_dedicated_server_smoke as server_smoke
    import run_v050_rocket_server_smoke as rocket_smoke
    import v120_precision_world_fixture as region_nbt
    from inspect_celestial_saved_data import NbtReader
    from run_v100_transaction_forced_stop import read_journal, uuid_from_nbt
    from v100_beta_world_fixture import wait_condition


SmokeError = server_smoke.SmokeError
HOST = "advancedrocketrycommunity"
FIXTURE = "arce_adapter_test"
CARGO = FIXTURE + ":cargo_container"
BOUNDARY = FIXTURE + ":state_boundary"
ADAPTER = FIXTURE + ":cargo_inventory"
ROCKET = HOST + ":rocket"
ORIGIN = (256, 101, 256)
CHUNK = (16, 16)
POSITIONS = {(256, 100, 256): HOST + ":rocket_assembler",
             ORIGIN: HOST + ":rocket_motor", (256, 102, 256): HOST + ":rocket_seat",
             (256, 103, 256): HOST + ":guidance_computer", (257, 101, 256): CARGO}
INVENTORY = {"Items": [
    {"Slot": 0, "id": "minecraft:diamond", "Count": 17,
     "tag": {"display": {"Name": '{"text":"Public adapter cargo"}'}}},
    {"Slot": 1, "id": "minecraft:iron_ingot", "Count": 64},
]}
REGISTERED = "Registered rocket adapter arce_adapter_test:cargo_inventory (payload 1, API 1.3, event 1)"
SKIPPED = "Skipped rocket adapter arce_adapter_test:cargo_inventory (API 1.3, event 1)"
PHASES = ("assemble", "entity-restart", "provider-skipped", "mod-uninstalled",
          "provider-reinstalled", "container-restart")
JOURNAL = "advancedrocketrycommunity_rocket_transactions.dat"
MISSING_MAPPING_ERROR = "There are unidentified mappings in this world - we are going to attempt to process anyway"
MISSING_BLOCK_ERROR = "Unidentified mapping from registry minecraft:block"


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")


def safe_path(path: Path) -> Path:
    if ".." in path.parts:
        raise SmokeError(f"Parent traversal is forbidden: {path}")
    absolute = Path(os.path.abspath(path))
    if any(server_smoke.is_link_or_junction(part) for part in (absolute, *absolute.parents)):
        raise SmokeError(f"Linked path or ancestor is forbidden: {path}")
    return absolute


def regular(path: Path, limit: int) -> bytes:
    safe_path(path)
    if not path.is_file() or path.stat().st_size > limit:
        raise SmokeError(f"Missing or oversized regular file: {path}")
    data = path.read_bytes()
    if len(data) > limit:
        raise SmokeError(f"File exceeded its bound while reading: {path}")
    return data


def artifact(path: Path, mod_id: str, version: str) -> dict:
    path = safe_path(path)
    regular(path, 32 * 1024**2)
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.getinfo("META-INF/mods.toml").file_size > 65536:
            raise SmokeError("Duplicate JAR entries or oversized mod metadata")
        metadata = tomllib.loads(archive.read("META-INF/mods.toml").decode("utf-8"))
        if [(mod.get("modId"), mod.get("version")) for mod in metadata.get("mods", [])] != [(mod_id, version)]:
            raise SmokeError(f"JAR identity does not match {mod_id} {version}")
        prefix = "io/github/sunthemoon/"
        required = prefix + ("arceadaptertest/AdapterTestMod.class" if mod_id == FIXTURE
                             else HOST + "/AdvancedRocketryCommunity.class")
        if required not in names:
            raise SmokeError("JAR lacks its actual mod entry point (classifier/source JAR rejected)")
        if mod_id == FIXTURE and any(name.endswith(".class") and not name.startswith(prefix + "arceadaptertest/")
                                     for name in names):
            raise SmokeError("Fixture JAR bundles non-fixture classes")
        if mod_id == HOST and any(name.startswith(prefix + "arceadaptertest/") for name in names):
            raise SmokeError("Host JAR bundles the fixture")
    return {"path": str(path), "name": path.name, "mod_id": mod_id, "version": version,
            "bytes": path.stat().st_size, "sha256": server_smoke.digest_file(path)}


def validate_inputs(server: Path, evidence: Path, host: Path, fixture: Path, version: str) -> tuple:
    server, evidence, host, fixture = map(safe_path, (server, evidence, host, fixture))
    if (not server.is_dir() or {p.name for p in server.iterdir()} != {"libraries"}
            or not (server / "libraries").is_dir()):
        raise SmokeError("Disposable server must contain only the prepared libraries directory")
    if evidence.exists() or server == evidence or server in evidence.parents or evidence in server.parents:
        raise SmokeError("Evidence must be new and disjoint from the disposable server")
    for source in (host, fixture):
        if server in source.parents or evidence in source.parents:
            raise SmokeError("Input artifacts must be outside the disposable outputs")
    # Reject links before walking them; do not trust a prepared directory junction.
    for directory, children, files in os.walk(server / "libraries", followlinks=False):
        for name in children + files:
            safe_path(Path(directory) / name)
    args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    args_file = server / "libraries/net/minecraftforge/forge" / server_smoke.FORGE_COORDINATE / args_name
    regular(args_file, 65536)
    artifacts = [artifact(host, HOST, version), artifact(fixture, FIXTURE, "1.0.0")]
    if artifacts[0]["name"] == artifacts[1]["name"]:
        raise SmokeError("Input JAR names collide")
    return server, evidence, artifacts, args_file


def validate_status(status: dict, installed: bool, version: str) -> dict:
    server_smoke.validate_status_identity(status, version)
    mods = server_smoke.forge_mod_versions(status)
    expected = {HOST, "minecraft", "forge"} | ({FIXTURE} if installed else set())
    if set(mods) != expected or (installed and mods[FIXTURE] != "1.0.0"):
        raise SmokeError(f"Unexpected actual Forge status mod set: {mods}")
    if status.get("players", {}).get("online") != 0:
        raise SmokeError("Recovery fixture requires an empty dedicated server")
    return mods


def validate_registration(lines: list[str], phase: str) -> None:
    registered = [line for line in lines if "Registered rocket adapter " in line]
    skipped = [line for line in lines if "Skipped rocket adapter " in line]
    if phase == "mod-uninstalled":
        valid = not registered and not skipped
    elif phase == "provider-skipped":
        valid = not registered and len(skipped) == 1 and SKIPPED in skipped[0]
    else:
        valid = not skipped and len(registered) == 1 and REGISTERED in registered[0]
    if not valid:
        raise SmokeError(f"Registration observations differ from the selected phase: {phase}")


def audit_log(lines: list[str], phase: str) -> list[str]:
    registry_logger = re.compile(r"\[(?:ne\.mi\.re\.|net\.minecraftforge\.registries\.)GameData/REGISTRIES\]")
    known_mapping_lines = set()
    if phase == "mod-uninstalled":
        for index, line in enumerate(lines):
            if (server_smoke.ERROR_LINE.search(line) and registry_logger.search(line)
                    and line.rstrip().endswith(MISSING_BLOCK_ERROR)):
                entries = []
                for following in lines[index + 1:]:
                    if not following.strip():
                        break
                    entries.append(following.strip())
                mappings = [re.fullmatch(r"([a-z0-9_.-]+:[a-z0-9/._-]+): [0-9]+", entry) for entry in entries]
                if (len(mappings) != 2 or any(match is None for match in mappings)
                        or {match.group(1) for match in mappings} != {CARGO, BOUNDARY}):
                    raise SmokeError("Missing-block diagnostic contains unexpected registry entries")
                known_mapping_lines.add(line.rstrip())
    accepted = []
    for finding in server_smoke.scan_log(lines):
        if (phase == "mod-uninstalled" and server_smoke.ERROR_LINE.search(finding)
                and registry_logger.search(finding)
                and (finding.rstrip().endswith(MISSING_MAPPING_ERROR) or finding in known_mapping_lines)):
            accepted.append(finding)
        else:
            raise SmokeError(f"Blocking log finding: {finding}")
    return accepted


def decode_entity_chunk(compressed: bytes) -> dict:
    stream = zlib.decompressobj()
    expanded = stream.decompress(compressed, region_nbt.MAX_EXPANDED_CHUNK + 1)
    if (len(expanded) > region_nbt.MAX_EXPANDED_CHUNK or not stream.eof
            or stream.unused_data or stream.unconsumed_tail):
        raise SmokeError("Entity chunk exceeds its bound or contains trailing data")
    root = NbtReader(expanded).read_root()
    if not isinstance(root, dict) or root.get("Position") != list(CHUNK) or not isinstance(root.get("Entities"), list):
        raise SmokeError("Entity chunk has invalid coordinates or entity list")
    return root


def block_at(chunk: dict, position: tuple) -> dict:
    """Inspect one position in the fixed fixture, using Minecraft's padded palette words."""
    x, y, z = position
    sections = [section for section in chunk.get("sections", []) if section.get("Y") == y // 16]
    if len(sections) != 1:
        raise SmokeError("Fixture section is missing or duplicated")
    states = sections[0].get("block_states", {})
    palette = states.get("palette", [])
    if not 1 <= len(palette) <= 4096:
        raise SmokeError("Invalid block-state palette")
    if len(palette) == 1:
        return palette[0]
    bits = max(4, (len(palette) - 1).bit_length())
    per_word = 64 // bits
    words = states.get("data", [])
    if len(words) != (4096 + per_word - 1) // per_word:
        raise SmokeError("Invalid packed block-state length")
    index = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
    selected = (words[index // per_word] >> ((index % per_word) * bits)) & ((1 << bits) - 1)
    if selected >= len(palette):
        raise SmokeError("Packed block-state palette index is invalid")
    return palette[selected]


def validate_entity(entity: dict, receipt: tuple) -> dict:
    snapshot_hash, entity_id = receipt
    if (entity.get("id") != ROCKET or uuid_from_nbt(entity.get("UUID")) != entity_id
            or entity.get("Pos") != [256.5, 101.0, 256.5] or entity.get("Passengers", [])):
        raise SmokeError("Persisted rocket identity differs from the assembly receipt")
    data = entity.get("RocketEntityData", {})
    snapshot = data.get("snapshot", {})
    blocks, palette = snapshot.get("relative_blocks", []), snapshot.get("block_palette", [])
    if (data.get("schema_version") != 2 or snapshot.get("schema_version") != 1
            or snapshot.get("content_hash") != snapshot_hash or len(blocks) != 4
            or snapshot.get("source_origin") != list(ORIGIN)
            or snapshot.get("source_dimension") != "minecraft:overworld"):
        raise SmokeError("Persisted rocket schema, hash, bounds or source differs")
    uuid.UUID(snapshot["snapshot_id"])
    if uuid_from_nbt(data.get("owner_id")) != "00000000-0000-0000-0000-000000000005":
        raise SmokeError("Console assembly owner was not preserved")
    if (data.get("flight_data", {}).get("state") != "ASSEMBLED"
            or data.get("flight_data", {}).get("fuel", {}).get("amount") != 0
            or uuid_from_nbt(data.get("assembly_transaction_id"))
            != uuid_from_nbt(data.get("flight_data", {}).get("logical_rocket_id"))):
        raise SmokeError("Persisted rocket flight authority differs")
    expected = {(x - ORIGIN[0], y - ORIGIN[1], z - ORIGIN[2]): name
                for (x, y, z), name in POSITIONS.items() if y >= ORIGIN[1]}
    actual = {}
    payloads = []
    for block in blocks:
        position = tuple(block["position"])
        if position in actual or not 0 <= block["palette"] < len(palette):
            raise SmokeError("Invalid snapshot block position or palette index")
        actual[position] = palette[block["palette"]]["id"]
        if "block_entity" in block:
            if actual[position] != CARGO:
                raise SmokeError("External inventory is attached to the wrong block")
            payloads.append(block["block_entity"])
    if actual != expected or payloads != [{"adapter": ADAPTER, "data": {"payload_version": 1, "data": INVENTORY}}]:
        raise SmokeError("External block identities, envelope or exact inventory changed")
    return data


def validate_disk(state: dict, phase: str, receipt: tuple, baseline: dict | None, staged: dict | None) -> None:
    recovered = phase in PHASES[4:]
    rockets = [entity for entity in state["entities"] if entity.get("id") == ROCKET]
    if len(rockets) != (0 if recovered else 1) or any(entity.get("id") == "minecraft:item" for entity in state["entities"]):
        raise SmokeError("Disk entity authority or dropped-item count differs")
    expected_blocks = {str(pos): name if recovered or pos[1] == 100 else "minecraft:air"
                       for pos, name in POSITIONS.items()}
    if {pos: block.get("Name") for pos, block in state["blocks"].items()} != expected_blocks:
        raise SmokeError("Disk world blocks differ from the selected authority")
    cargo = [be for be in state["block_entities"] if be.get("id") == CARGO]
    if recovered:
        if (len(cargo) != 1 or tuple(cargo[0].get(key) for key in ("x", "y", "z")) != (257, 101, 256)
                or cargo[0].get("FixtureInventory") != {"schema_version": 1, **INVENTORY}):
            raise SmokeError("Restored BlockEntity inventory, metadata or schema changed")
    else:
        if cargo or any(tuple(be.get(axis) for axis in ("x", "y", "z")) in POSITIONS
                        and be.get("y") != 100 for be in state["block_entities"]):
            raise SmokeError("Assembled source retained an inventory BlockEntity")
        data = validate_entity(rockets[0], receipt)
        if baseline is not None and data != baseline:
            raise SmokeError("Opaque RocketEntityData changed across a save/restart")
    entries = state["journal"]["transactions"]
    if phase in ("provider-skipped", "mod-uninstalled"):
        if len(entries) != 1:
            raise SmokeError("Pending journal authority was lost or duplicated")
        entry = entries[0]
        if (entry.get("type") != "ASSEMBLY" or entry.get("phase") != "EXTRACTING"
                or entry.get("progress") != 4 or entry.get("snapshot") != baseline["snapshot"]
                or entry.get("owner_id") != baseline["owner_id"]
                or entry.get("transaction_id") != baseline["assembly_transaction_id"]
                or uuid_from_nbt(entry.get("snapshot_id")) != baseline["snapshot"]["snapshot_id"]
                or entry.get("dimension") != "minecraft:overworld"
                or entry.get("minimum") != [256, 101, 256]
                or entry.get("maximum") != [257, 103, 256]
                or uuid_from_nbt(entry.get("rocket_entity_id")) != receipt[1]
                or entry.get("content_hash") != receipt[0]):
            raise SmokeError("Synthetic journal bindings or opaque snapshot changed")
        if staged is not None and state["journal"] != staged:
            raise SmokeError("Missing-mod recovery silently mutated the durable journal")
    elif entries:
        raise SmokeError("Unexpected pending or repeated journal work")


def capture_disk(server: Path, evidence: Path) -> dict:
    files = {}
    for relative in ("world/region/r.0.0.mca", "world/entities/r.0.0.mca", f"world/data/{JOURNAL}", "world/level.dat"):
        source = server / relative
        data = regular(source, 16 * 1024**2)
        target = evidence / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        files[relative] = {"sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
    block_region = regular(evidence / "world/region/r.0.0.mca", 16 * 1024**2)
    chunk = region_nbt._decode_chunk(region_nbt._region_chunk(block_region, *CHUNK), *CHUNK)
    entity_region = regular(evidence / "world/entities/r.0.0.mca", 16 * 1024**2)
    offset = 4 * (CHUNK[0] + 32 * CHUNK[1])
    entities = [] if entity_region[offset:offset + 4] == bytes(4) else decode_entity_chunk(
        region_nbt._region_chunk(entity_region, *CHUNK))["Entities"]
    return {"blocks": {str(pos): block_at(chunk, pos) for pos in POSITIONS},
            "block_entities": chunk.get("block_entities", []), "entities": entities,
            "journal": read_journal(evidence / "world/data" / JOURNAL), "files": files}


class RecoveryRun:
    def __init__(self, server: Path, evidence: Path, artifacts: list[dict], java: str, port: int, timeout: float):
        self.server, self.evidence, self.artifacts = server, evidence, artifacts
        self.java, self.port, self.timeout = java, port, timeout
        self.receipt = None
        self.baseline = self.staged = self.world_identity = None
        self.config_hashes = None
        self.commands = []

    def send(self, process, command: str) -> None:
        process.command(command)

    def query(self, process, command: str, marker: re.Pattern, timeout: float = 30.0):
        start = len(process.lines)
        self.send(process, command)
        return marker.search(process.lines[process.wait_for(marker, timeout, start_at=start)])

    def check_live(self, process, phase: str, suffix: str) -> None:
        recovered = phase in PHASES[4:]
        self.send(process, "scoreboard players set #rockets arce_v130 0")
        self.send(process, f"execute as @e[type={ROCKET}] run scoreboard players add #rockets arce_v130 1")
        condition = f"if score #rockets arce_v130 matches {0 if recovered else 1} "
        if not recovered:
            condition += f"if entity {self.receipt[1]} "
        for position, name in POSITIONS.items():
            name = name if recovered or position[1] == 100 else "minecraft:air"
            condition += f"if block {' '.join(map(str, position))} {name} "
        condition += "unless entity @e[type=minecraft:item,x=254,y=99,z=254,dx=5,dy=6,dz=5] "
        marker = f"V130_{phase}_{suffix}_AUTHORITY"
        self.query(process, f"execute {condition}run say {marker}", re.compile(rf"\[Server\] {re.escape(marker)}\s*$"))

    def mods(self, installed: bool) -> dict:
        expected = self.artifacts if installed else self.artifacts[:1]
        directory = self.server / "mods"
        if {path.name for path in directory.iterdir()} != {item["name"] for item in expected}:
            raise SmokeError("Installed mods directory differs from the approved JAR set")
        result = {}
        for item in expected:
            path = directory / item["name"]
            regular(path, 32 * 1024**2)
            result[path.name] = server_smoke.digest_file(path)
            if result[path.name] != item["sha256"]:
                raise SmokeError("Installed JAR bytes changed")
        return result

    def run_phase(self, phase: str) -> dict:
        directory = self.evidence / phase
        directory.mkdir()
        document = {"phase": phase, "started_at": datetime.now(timezone.utc).isoformat(),
                    "exit_code": None, "result": "IN_PROGRESS"}
        try:
            result = self._run_phase(phase, directory, document)
            document["result"] = "PASS"
            return result
        except BaseException as exc:
            document["result"] = "FAIL"
            document["error"] = f"{type(exc).__name__}: {exc}"
            raise
        finally:
            document["completed_at"] = datetime.now(timezone.utc).isoformat()
            write_json(directory / "result.json", document)

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        installed = phase != "mod-uninstalled"
        if self.world_identity:
            server_smoke.complete_world_identity(self.server, self.world_identity)
        installed_hashes = self.mods(installed)
        command = rocket_smoke._server_command(self.java)
        if phase == "provider-skipped":
            command.insert(1, "-Darce_adapter_test.skipRocketAdapter=true")
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.server), "mods": installed_hashes})
        self.commands = []
        process = server_smoke.CapturedProcess(command, self.server, directory / "stdout.txt")
        original_command = process.command

        def recorded_command(value: str) -> None:
            self.commands.append(value)
            original_command(value)

        process.command = recorded_command
        try:
            process.wait_for(server_smoke.READY_MARKER, self.timeout)
            status = server_smoke.wait_for_status(self.port)
            document["status_mods"] = validate_status(status, installed, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            validate_registration(process.lines, phase)
            if phase == "assemble":
                for command_text in ("gamerule doMobSpawning false", "gamerule randomTickSpeed 0",
                                     "scoreboard objectives add arce_v130 dummy", "forceload add 255 255 258 257"):
                    self.send(process, command_text)
                wait_condition(process, "execute if loaded 255 101 255 if loaded 258 101 255 "
                               "if loaded 255 101 257 if loaded 258 101 257", "V130_FIXTURE_LOADED", 30.0)
                # A small air shell prevents random terrain joining this four-block scan.
                self.send(process, "fill 255 100 255 258 104 257 minecraft:air")
                for position, name in POSITIONS.items():
                    self.send(process, f"setblock {' '.join(map(str, position))} {name}")
                self.send(process, 'data merge block 257 101 256 {FixtureInventory:{schema_version:1,Items:['
                          '{Slot:0b,id:"minecraft:diamond",Count:17b,tag:{display:{Name:\'{"text":"Public adapter cargo"}\'}}},'
                          '{Slot:1b,id:"minecraft:iron_ingot",Count:64b}]}}')
                match = self.query(process, "arce rocket assemble 256 100 256", rocket_smoke.ASSEMBLY_LOG, 45.0)
                self.receipt = match.groups()
            if phase not in PHASES[4:]:
                rocket_smoke._wait_for_active_entity(process, self.receipt[1])
                document["entity_snbt"] = self.query(process, f"data get entity {self.receipt[1]} RocketEntityData",
                                                     rocket_smoke.ENTITY_DATA_MARKER).group(1)
            if phase == "provider-skipped":
                self.query(process, f"arce rocket release-test disassemble {self.receipt[1]}", re.compile(
                    r"ARCE_RELEASE_TEST_DISASSEMBLY entity=" + re.escape(self.receipt[1])
                    + r" logical=[0-9a-f-]{36} code=UNSUPPORTED_BLOCK_ENTITY blocks=0 rolled_back=0"))
                self.check_live(process, phase, "REJECTED")
                match = self.query(process, f"arce rocket release-test stage-recovery {self.receipt[1]}",
                                   rocket_smoke.RECOVERY_STAGED_LOG)
                if match.group(2) != self.receipt[1] or match.group(3) != self.receipt[0]:
                    raise SmokeError("Staged journal receipt changed authority")
                document["synthetic_transaction"] = match.group(1)
            if phase == "mod-uninstalled":
                process.wait_for(re.compile("ARCE_ROCKET_RECOVERY outcome=CONFLICT"), 45.0)
                self.query(process, f"arce rocket release-test disassemble {self.receipt[1]}",
                           re.compile("Release-test disassembly failed: REGION_BUSY"))
            if phase == "provider-reinstalled":
                process.wait_for(rocket_smoke.RECOVERY_LOG, 45.0)
            self.check_live(process, phase, "FIRST")
            start_tick = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
            deadline = time.monotonic() + 10
            while True:
                time.sleep(0.5)
                end_tick = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
                if end_tick - start_tick >= 20:
                    break
                if time.monotonic() >= deadline:
                    raise SmokeError("Server did not advance the bounded 20-tick observation window")
            self.check_live(process, phase, "SECOND")
            document["observation_ticks"] = end_tick - start_tick
            start = len(process.lines)
            self.send(process, "save-all flush")
            process.wait_for(server_smoke.SAVE_MARKER, 60.0, start_at=start)
            self.send(process, "stop")
            document["exit_code"] = process.finish()
            if document["exit_code"] != 0:
                raise SmokeError("Server did not exit cleanly")
        except BaseException:
            process.abort()
            raise
        finally:
            document["exit_code"] = process.process.poll()
            write_json(directory / "commands.json", self.commands)
            for name in ("debug.log", "latest.log"):
                source = self.server / "logs" / name
                if source.exists():
                    (directory / name).write_bytes(regular(source, 32 * 1024**2))
        document["accepted_loader_findings"] = audit_log(process.lines, phase)
        document["log_counts"] = server_smoke.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server_smoke.verify_active_server_properties(
            regular(self.server / "server.properties", 65536), self.port)
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(regular(self.server / relative, 65536))
        configs = {str(path.relative_to(directory)): server_smoke.digest_file(path)
                   for path in (directory / "config").iterdir()}
        if self.config_hashes is not None and self.config_hashes != configs:
            raise SmokeError("Mod configuration changed between recovery cycles")
        self.config_hashes = configs
        self.mods(installed)
        state = capture_disk(self.server, directory)
        write_json(directory / "disk-state.json", state)
        validate_disk(state, phase, self.receipt, self.baseline, self.staged)
        if phase == "assemble":
            self.baseline = next(entity["RocketEntityData"] for entity in state["entities"] if entity.get("id") == ROCKET)
        if phase == "provider-skipped":
            self.staged = state["journal"]
        document["completed_at"] = datetime.now(timezone.utc).isoformat()
        document["disk_evidence"] = state["files"]
        return document


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--host-jar", type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.3.0-dev")
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    evidence = None
    try:
        if not 0 < args.startup_timeout <= 240:
            raise SmokeError("Startup timeout must be within (0, 240] seconds")
        server, output, artifacts, args_file = validate_inputs(
            args.server_dir, args.evidence_dir, args.host_jar, args.fixture_jar, args.expected_version)
        java, java_version = server_smoke.resolve_java(args.java)
        output.mkdir(parents=True)
        evidence = output
        port = server_smoke.allocate_port()
        properties_hash = server_smoke.write_server_configuration(server, port, True)
        (server / "mods").mkdir()
        for item in artifacts:
            shutil.copyfile(item["path"], server / "mods" / item["name"])
        run = RecoveryRun(server, evidence, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "six clean processes; synthetic pre-commit journal, not crash/power loss",
                   "artifacts": artifacts, "java": java_version, "server": str(server), "port": port,
                   "forge_args_sha256": server_smoke.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(evidence / "summary.json", summary)
        for phase in PHASES:
            fixture = server / "mods" / artifacts[1]["name"]
            if phase == "mod-uninstalled":
                run.mods(True)
                safe_path(fixture).unlink()  # Only the exact verified JAR copied by this run.
            elif phase == "provider-reinstalled":
                run.mods(False)
                if server_smoke.digest_file(Path(artifacts[1]["path"])) != artifacts[1]["sha256"]:
                    raise SmokeError("Fixture input changed before reinstall")
                shutil.copyfile(artifacts[1]["path"], fixture)
            summary["cycles"].append(run.run_phase(phase))
            if phase == "assemble":
                run.world_identity = server_smoke.establish_world_identity(
                    server, str(uuid.uuid4()), artifacts[0]["sha256"], properties_hash)
            summary["world"] = server_smoke.complete_world_identity(server, run.world_identity)
            write_json(evidence / "summary.json", summary)
        summary["result"] = "PASS"
        summary["receipt"] = run.receipt
        write_json(evidence / "summary.json", summary)
        print(f"[PASS] Six finite external-container recovery cycles; evidence: {evidence}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError, zipfile.BadZipFile) as exc:
        if evidence is not None:
            write_json(evidence / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if evidence is not None:
            files = sorted(path for path in evidence.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (evidence / "SHA256SUMS").write_text("".join(
                f"{server_smoke.digest_file(path)}  {path.relative_to(evidence).as_posix()}\n" for path in files),
                encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
