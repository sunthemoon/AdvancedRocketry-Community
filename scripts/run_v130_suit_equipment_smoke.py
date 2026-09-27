#!/usr/bin/env python3
"""Four finite packaged-server cycles for external suit oxygen and native item restart.

The API-only fixture invokes twenty production LivingTick events on a FakePlayer
per phase. This is not real-client, wall-clock player, power-loss or load evidence.
Only setup introduces the chest armor and oxygen resources. Later phases reuse
that saved chest stack and full/empty canister quantities; the other armor pieces
are fresh fixture setup, not evidence of four-piece identity persistence.
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
import time
import uuid
from pathlib import Path

if __package__:
    from . import run_v130_atmosphere_boundary_smoke as boundary
else:
    import run_v130_atmosphere_boundary_smoke as boundary

recovery = boundary.recovery
server = recovery.server_smoke
SmokeError = recovery.SmokeError
write_json = recovery.write_json
require = boundary.require
HOST = recovery.HOST
PROVIDER = recovery.FIXTURE + ":suit_oxygen"
MOON = HOST + ":moon"
POSITION = (264, 220, 264)
REGION = Path("world/dimensions") / HOST / "moon/region/r.0.0.mca"
PHASES = ("setup", "restart", "skipped", "restored")
REGISTERED = f"Registered suit equipment {PROVIDER} (API 1.6, event 1)"
SKIPPED = f"Skipped suit equipment {PROVIDER} (event 1)"


def validate_registration(lines: list[str], skipped: bool) -> None:
    present = [line for line in lines if "Registered suit equipment " in line]
    absent = [line for line in lines if "Skipped suit equipment " in line]
    require((not present and len(absent) == 1 and SKIPPED in absent[0]) if skipped else
            (not absent and len(present) == 1 and REGISTERED in present[0]),
            "Equipment registration did not match startup selection")


def expected_items(phase: str) -> list[dict]:
    require(phase in PHASES, "Unknown equipment phase")
    units = {"setup": 999, "restart": 998, "skipped": 998, "restored": 1997}[phase]
    items = [{"Slot": 0, "id": "minecraft:leather_chestplate", "Count": 1,
              "tag": {"Damage": 0, "fixture_marker": "persist-exactly", "arce_suit_provider": {
                  "schema_version": 1, "provider": PROVIDER, "payload_version": 1,
                  "data": {"oxygen": units}}}}]
    if phase != "restored":
        items.append({"Slot": 1, "id": HOST + ":oxygen_canister", "Count": 1})
    items.append({"Slot": 2, "id": HOST + ":empty_canister", "Count": 2 if phase == "restored" else 1})
    return items


def validate_disk(state: dict, phase: str, previous: dict | None) -> None:
    require(state["block"] == {"Name": "minecraft:chest", "Properties": {
        "facing": "north", "type": "single", "waterlogged": "false"}}, "Native equipment chest block differs")
    require(sorted(state["items"], key=lambda item: item["Slot"]) == expected_items(phase),
            "Native armor payload or canister/shell authority differs")
    if phase == "skipped":
        require(previous is not None and state["items"] == previous["items"],
                "Skipped provider did not retain the exact previous inventory")


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
    chests = [entity for entity in chunk.get("block_entities", [])
              if tuple(entity.get(axis) for axis in ("x", "y", "z")) == POSITION]
    require(len(chests) == 1 and chests[0].get("id") == "minecraft:chest", "Saved equipment chest is not unique")
    return {"block": recovery.block_at(chunk, POSITION), "items": chests[0].get("Items"), "files": files}


class SuitRun(recovery.RecoveryRun):
    def __init__(self, *args):
        super().__init__(*args)
        self.previous = None

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        if self.world_identity:
            server.complete_world_identity(self.server, self.world_identity)
        command = recovery.rocket_smoke._server_command(self.java)
        command.remove("-Dadvancedrocketrycommunity.releaseTestHooks=true")
        command.insert(1, "-Darce_adapter_test.suitSmoke=true")
        skipped = phase == "skipped"
        if skipped:
            command.insert(1, "-Darce_adapter_test.skipSuitEquipment=true")
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.server), "mods": self.mods(True)})
        process = server.CapturedProcess(command, self.server, directory / "stdout.txt")
        original_command = process.command
        self.commands = []

        def recorded(value):
            self.commands.append(value)
            original_command(value)

        process.command = recorded
        try:
            process.wait_for(server.READY_MARKER, self.timeout)
            status = server.wait_for_status(self.port)
            document["status_mods"] = recovery.validate_status(status, True, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            validate_registration(process.lines, skipped)
            process.command(f"execute in {MOON} run forceload add 264 264")
            recovery.wait_condition(process, f"execute in {MOON} if loaded 264 220 264", "V130_SUIT_LOADED")
            if phase == "setup":
                process.command("gamerule doMobSpawning false")
                process.command("gamerule randomTickSpeed 0")
                process.command(f"execute in {MOON} run setblock 264 220 264 minecraft:chest")
            self.query(process, "arce_fixture_suit " + phase, re.compile(r"V130_SUIT_PHASE_PASS " + phase + r"\s*$"))
            document["simulated_player_ticks"] = 20
            before = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
            deadline = time.monotonic() + 15
            while True:
                time.sleep(0.2)
                after = int(self.query(process, "time query gametime", re.compile(r"The time is (\d+)")).group(1))
                if after - before >= 20:
                    break
                require(time.monotonic() < deadline, "Finite server observation window did not advance")
            document["observation_ticks"] = after - before
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop")
            require(process.finish() == 0, "Dedicated process did not exit cleanly")
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
        boundary.validate_registration(process.lines, False)
        recovery.validate_registration(process.lines, "assemble")
        recovery.audit_log(process.lines, "assemble")
        require(not any("Suit oxygen provider " in line and "disabled for this server session" in line
                        for line in process.lines), "Packaged fixture unexpectedly disabled its provider")
        document["log_counts"] = server.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server.verify_active_server_properties(
                recovery.regular(self.server / "server.properties", 65536), self.port)
        configs = {}
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(recovery.regular(self.server / relative, 65536))
            if relative != "server.properties":
                configs[relative] = server.digest_file(target)
        require(self.config_hashes is None or self.config_hashes == configs, "Mod configs changed across restart")
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
        run = SuitRun(root, output, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "four clean processes; FakePlayer events; native chest item restart",
                   "artifacts": artifacts, "java": java_version, "server": str(root), "port": port,
                   "forge_args_sha256": server.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(output / "summary.json", summary)
        for phase in PHASES:
            summary["cycles"].append(run.run_phase(phase))
            if phase == "setup":
                run.world_identity = server.establish_world_identity(root, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
            summary["world"] = server.complete_world_identity(root, run.world_identity)
            write_json(output / "summary.json", summary)
        for item in artifacts:
            require(server.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed")
        summary["result"] = "PASS"
        write_json(output / "summary.json", summary)
        print(f"[PASS] Four finite suit equipment cycles; evidence: {output}")
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
