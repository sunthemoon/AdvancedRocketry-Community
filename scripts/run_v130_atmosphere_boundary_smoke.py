#!/usr/bin/env python3
"""Four finite packaged-server cycles for state boundaries, tag reload and restart.

Requires a new disposable libraries-only Forge directory and explicit host/fixture
JARs. Uses native operator commands to prepare a single Moon room; no player, crash,
long-load, rendering or equipment-API acceptance is implied.
"""

from __future__ import annotations

import re
import argparse
import shutil
import sys
import time
import uuid
from pathlib import Path

if __package__:
    from . import run_v130_adapter_recovery_smoke as recovery
else:
    import run_v130_adapter_recovery_smoke as recovery

server = recovery.server_smoke
SmokeError = recovery.SmokeError
write_json = recovery.write_json
HOST = recovery.HOST
MOON = HOST + ":moon"
BOUNDARY = recovery.FIXTURE + ":state_boundary"
VENT = HOST + ":oxygen_vent"
PHASES = ("setup-reload", "open-restart", "provider-skipped", "provider-restored")
REGISTERED = f"Registered atmosphere boundary {BOUNDARY} (API 1.5, event 1)"
SKIPPED = f"Skipped atmosphere boundary {BOUNDARY} (event 1)"
PREFIX = f"execute in {MOON} run "
POSITION = (264, 220, 264)
REGION = Path("world/dimensions") / HOST / "moon/region/r.0.0.mca"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeError(message)


def validate_registration(lines: list[str], skipped: bool) -> None:
    registered = [line for line in lines if "Registered atmosphere boundary " in line]
    absent = [line for line in lines if "Skipped atmosphere boundary " in line]
    require((not registered and len(absent) == 1 and SKIPPED in absent[0]) if skipped else
            (not absent and len(registered) == 1 and REGISTERED in registered[0]),
            "Boundary registration observations do not match the startup selection")


def validate_disk(state: dict, phase: str, previous: dict | None) -> None:
    require(phase in PHASES, "Unknown atmosphere phase")
    require(state["boundary"] == {"Name": BOUNDARY, "Properties": {"open": "true"}},
            "Native save did not retain the open custom boundary state")
    require(state["vent_block"] == {"Name": VENT, "Properties": {
        "lit": "true" if phase == "provider-skipped" else "false"}}, "Native vent state differs")
    data = state["vent_data"]
    require(set(data) == {"schema_version", "oxygen_canisters", "empty_canisters", "oxygen_units",
                          "energy", "oxygen_phase"}, "Vent schema acquired unexpected fields")
    require(data["schema_version"] == 1 and data["oxygen_canisters"] == data["empty_canisters"] == 0
            and 0 < data["oxygen_units"] <= 3000 and 0 < data["energy"] <= 40000
            and 0 <= data["oxygen_phase"] < 20, "Vent resources escaped their fixture bounds")
    if previous:
        before = previous["vent_data"]
        if phase == "provider-skipped":
            require(data["energy"] <= before["energy"] - 400
                    and data["oxygen_units"] < before["oxygen_units"],
                    "Legacy full-cube fallback did not supply oxygen across 20 ticks")
        else:
            require(data == {**before, "oxygen_phase": 0},
                    "Open room resources changed beyond the existing inactive-phase reset")


def capture_disk(root: Path, output: Path) -> dict:
    files = {}
    for relative in (Path("world/level.dat"), REGION):
        payload = recovery.regular(root / relative, recovery.region_nbt.MAX_REGION_BYTES)
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
        files[relative.as_posix()] = {"sha256": server.digest_file(target), "bytes": len(payload)}
    raw = recovery.regular(output / REGION, recovery.region_nbt.MAX_REGION_BYTES)
    chunk = recovery.region_nbt._decode_chunk(recovery.region_nbt._region_chunk(raw, 16, 16), 16, 16)
    vents = [be for be in chunk.get("block_entities", [])
             if (be.get("x"), be.get("y"), be.get("z")) == POSITION]
    require(len(vents) == 1 and vents[0].get("id") == VENT, "Expected exactly one saved fixture vent")
    return {"boundary": recovery.block_at(chunk, (264, 222, 264)),
            "vent_block": recovery.block_at(chunk, POSITION), "vent_data": vents[0]["arce_oxygen_vent"],
            "files": files}


class AtmosphereRun(recovery.RecoveryRun):
    def __init__(self, *args):
        super().__init__(*args)
        self.previous = None

    def lit(self, process, expected: bool, marker: str) -> None:
        condition = f"execute in {MOON} if block 264 220 264 {VENT}[lit={str(expected).lower()}]"
        recovery.wait_condition(process, condition, "V130_ATM_" + marker, timeout=15)

    def tag_pack(self, enabled: bool, directory: Path) -> None:
        pack = self.server / "world/datapacks/arce_boundary_fixture"
        tag = pack / f"data/{HOST}/tags/blocks/atmosphere_sealing.json"
        tag.parent.mkdir(parents=True, exist_ok=True)
        write_json(pack / "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Boundary reload fixture"}})
        write_json(tag, {"replace": False, "values": [BOUNDARY] if enabled else []})
        write_json(directory / ("sealing-enabled.json" if enabled else "sealing-disabled.json"),
                   {"replace": False, "values": [BOUNDARY] if enabled else []})

    def reload(self, process) -> None:
        self.query(process, "reload", re.compile(r"Accepted celestial catalog generation [2-9][0-9]* with 3 bodies"))

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        if self.world_identity:
            server.complete_world_identity(self.server, self.world_identity)
        command = recovery.rocket_smoke._server_command(self.java)
        command.remove("-Dadvancedrocketrycommunity.releaseTestHooks=true")
        skipped = phase == "provider-skipped"
        if skipped:
            command.insert(1, "-Darce_adapter_test.skipAtmosphereBoundary=true")
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.server), "mods": self.mods(True)})
        process = server.CapturedProcess(command, self.server, directory / "stdout.txt")
        original = process.command
        self.commands = []

        def recorded(value):
            self.commands.append(value)
            original(value)

        process.command = recorded
        try:
            process.wait_for(server.READY_MARKER, self.timeout)
            status = server.wait_for_status(self.port)
            document["status_mods"] = recovery.validate_status(status, True, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            recovery.validate_registration(process.lines, "assemble")
            validate_registration(process.lines, skipped)
            process.command(PREFIX + "forceload add 264 264")
            recovery.wait_condition(process, f"execute in {MOON} if loaded 264 220 264", "V130_ATM_LOADED")
            if phase == "setup-reload":
                process.command("gamerule doMobSpawning false")
                process.command("gamerule randomTickSpeed 0")
                process.command(PREFIX + "fill 262 219 262 266 224 266 minecraft:air")
                process.command(PREFIX + "fill 263 220 263 265 222 265 minecraft:iron_block")
                process.command(PREFIX + "setblock 264 221 264 minecraft:air")
                process.command(PREFIX + f"setblock 264 222 264 {BOUNDARY}[open=false]")
                process.command(PREFIX + f"setblock 264 220 264 {VENT}")
                process.command(PREFIX + "data merge block 264 220 264 {arce_oxygen_vent:{schema_version:1,"
                                "oxygen_canisters:0,empty_canisters:0,oxygen_units:3000,energy:40000,oxygen_phase:0}}")
                self.lit(process, True, "CLOSED_ACTIVE")
                process.command(PREFIX + f"setblock 264 222 264 {BOUNDARY}[open=true]")
                self.lit(process, False, "OPEN_INACTIVE")
                process.command(PREFIX + f"setblock 264 222 264 {BOUNDARY}[open=false]")
                self.lit(process, True, "RECLOSED_ACTIVE")
                process.command(PREFIX + "setblock 264 222 264 minecraft:air")
                self.lit(process, False, "REPLACED_INACTIVE")
                process.command(PREFIX + f"setblock 264 222 264 {BOUNDARY}[open=true]")
                self.tag_pack(True, directory)
                self.reload(process)
                self.lit(process, True, "SEALING_TAG_OVERRIDES")
                self.tag_pack(False, directory)
                self.reload(process)
                self.lit(process, False, "TAG_REMOVED_RULE_RESTORED")
            self.lit(process, skipped, phase + "_FIRST")
            before = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
            deadline = time.monotonic() + 10
            while True:
                time.sleep(0.5)
                after = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
                if after - before >= 20:
                    break
                require(time.monotonic() < deadline, "The finite observation window did not advance")
            document["observation_ticks"] = after - before
            self.lit(process, skipped, phase + "_SECOND")
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop")
            require(process.finish() == 0, "Dedicated server did not exit cleanly")
        except BaseException:
            process.abort()
            raise
        finally:
            document["exit_code"] = process.process.poll()
            write_json(directory / "commands.json", self.commands)
            for name in ("debug.log", "latest.log"):
                source = self.server / "logs" / name
                if source.exists():
                    (directory / name).write_bytes(recovery.regular(source, 32 * 1024**2))
        validate_registration(process.lines, skipped)
        recovery.validate_registration(process.lines, "assemble")
        recovery.audit_log(process.lines, "assemble")
        document["log_counts"] = server.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server.verify_active_server_properties(
                recovery.regular(self.server / "server.properties", 65536), self.port)
        configs = {}
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            source = self.server / relative
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(recovery.regular(source, 65536))
            if relative != "server.properties":
                configs[relative] = server.digest_file(target)
        require(self.config_hashes is None or self.config_hashes == configs, "Mod configs changed between cycles")
        self.config_hashes = configs
        self.mods(True)
        state = capture_disk(self.server, directory)
        write_json(directory / "disk-state.json", state)
        validate_disk(state, phase, self.previous)
        self.previous = state
        document["disk_evidence"] = state["files"]
        return document


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--host-jar", type=Path, required=True)
    parser.add_argument("--fixture-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.3.0-dev")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    args = parser.parse_args()
    output = None
    try:
        require(0 < args.startup_timeout <= 240, "Startup timeout must be within (0, 240] seconds")
        root, output_path, artifacts, args_file = recovery.validate_inputs(
            args.server_dir, args.evidence_dir, args.host_jar, args.fixture_jar, args.expected_version)
        java, java_version = server.resolve_java(args.java)
        output_path.mkdir(parents=True)
        output = output_path
        port = server.allocate_port()
        properties = server.write_server_configuration(root, port, True)
        (root / "mods").mkdir()
        for item in artifacts:
            shutil.copyfile(item["path"], root / "mods" / item["name"])
        run = AtmosphereRun(root, output, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "four clean processes; one room; two live tag reloads; no long load",
                   "artifacts": artifacts, "java": java_version, "server": str(root), "port": port,
                   "forge_args_sha256": server.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(output / "summary.json", summary)
        for phase in PHASES:
            summary["cycles"].append(run.run_phase(phase))
            if phase == "setup-reload":
                run.world_identity = server.establish_world_identity(root, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
            summary["world"] = server.complete_world_identity(root, run.world_identity)
            write_json(output / "summary.json", summary)
        summary["result"] = "PASS"
        write_json(output / "summary.json", summary)
        print(f"[PASS] Four finite atmosphere boundary cycles; evidence: {output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError) as exc:
        if output is not None:
            write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if output is not None:
            paths = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(
                f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in paths),
                encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
