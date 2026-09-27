#!/usr/bin/env python3
"""Three finite dedicated cycles: terminal payload, reload, actual mod removal and restored missions.

Uses a network-free fixture actor for terminal intents and the existing opt-in
operator claim hook while the consumer is uninstalled. Native SavedData and chunk
roots are the persistence evidence. No power-loss, real-player or load claim.
"""
from __future__ import annotations

import argparse
import gzip
import io
import json
import re
import shutil
import sys
import uuid
from pathlib import Path

if __package__:
    from . import run_v130_adapter_recovery_smoke as recovery
    from . import run_v130_adapter_flight_smoke as flight
else:
    import run_v130_adapter_recovery_smoke as recovery
    import run_v130_adapter_flight_smoke as flight

server, SmokeError = recovery.server_smoke, recovery.SmokeError
write_json, require = recovery.write_json, flight.require
HOST = recovery.HOST
PHASES = ("create", "mod-uninstalled", "reinstalled")
OWNER = "c6fcdf97-8f10-450e-ad36-67a644d23c2b"
DEFINITION = "arce_adapter_test:research_payload"
POSITION = (264, 101, 264)
SAVED = f"world/data/{HOST}_satellite_missions.dat"
PACK = "world/datapacks/satellite_payload"
DEFINITION_FILE = PACK + "/data/arce_adapter_test/satellite_definitions/research_payload.json"
REGISTERED = f"Registered satellite payload {DEFINITION} (API 1.7, event 1)"
PROBE = re.compile(r"ARCE_SAT_PAYLOAD (\{.*\})\s*$")
SCHEDULED = re.compile(r"ARCE_SATELLITE_SCHEDULER completed=1 inspected=1 remaining=0")


def validate_registration(lines: list[str], installed: bool) -> None:
    records = [line for line in lines if "Registered satellite payload " in line or "Skipped satellite payload " in line]
    require((len(records) == 1 and records[0].rstrip().endswith(REGISTERED)) if installed else not records,
            "Satellite payload registration receipt differs")


def validate_probe(value: dict, action: str, satellite: str | None, code: int) -> str:
    require(value.get("action") == action and value.get("energy") == 9000 and value.get("result") == code
            and value.get("payload_count") == 1 and value.get("package_empty") is True
            and value.get("owner") == OWNER and value.get("definition") == DEFINITION, "Terminal payload receipt differs")
    identity = str(uuid.UUID(value["satellite"]))
    require(identity == value["satellite"] and (satellite is None or satellite == identity), "Satellite identity changed")
    return identity


def decode_saved(payload: bytes) -> dict:
    with gzip.GzipFile(fileobj=io.BytesIO(payload)) as stream:
        raw = stream.read(4 * 1024**2 + 1)
    require(len(raw) <= 4 * 1024**2, "Satellite SavedData exceeds bound")
    root = recovery.NbtReader(raw).read_root()
    require(isinstance(root, dict) and isinstance(root.get("data"), dict), "Missing SavedData root")
    data = root["data"]
    require(data.get("schema_version") == 2 and len(data.get("satellites", [])) == 1
            and len(data.get("research_accounts", [])) == 1 and 1 <= len(data.get("missions", [])) <= 2,
            "Satellite native schema/counts differ")
    return data


def validate_disk(data: dict, terminal: dict, phase: str, satellite: str, missions: list[str], previous: dict | None) -> None:
    require(phase in PHASES, "Unknown satellite phase")
    owner = lambda value: recovery.uuid_from_nbt(value["owner_id"]) == OWNER
    state = data["satellites"][0]
    require(state.get("schema_version") == 1 and state.get("status") == "operational" and state.get("definition_id") == DEFINITION and owner(state)
            and recovery.uuid_from_nbt(state["satellite_id"]) == satellite, "Native satellite identity differs")
    expected_count = 2 if phase == "reinstalled" else 1
    require(len(data["missions"]) == len(missions) == expected_count, "Mission count differs")
    by_id = {recovery.uuid_from_nbt(value["mission_id"]): value for value in data["missions"]}
    require(len(by_id) == expected_count and set(by_id) == set(missions), "Native mission IDs differ")
    for index, identity in enumerate(missions):
        mission = by_id[identity]
        require(mission.get("schema_version") == 1 and owner(mission) and mission.get("definition_id") == DEFINITION
                and recovery.uuid_from_nbt(mission["satellite_id"]) == satellite
                and mission.get("target_body_id") == HOST + ":earth" and mission.get("discovery_required") == (1 if index == 0 else 0)
                and mission["completes_at"] - mission["started_at"] == (400 if index == 0 else 20)
                and mission.get("research_yield") == (137 if index == 0 else 211)
                and mission.get("discovery_cost") == 11
                and mission.get("status") == ("active" if phase == "create" else "claimed"), "Mission snapshot or reward changed")
        if previous is not None and identity in previous:
            immutable = {key: value for key, value in mission.items() if key not in ("status", "ready_at", "resolved_at")}
            require(immutable == {key: value for key, value in previous[identity].items()
                                 if key not in ("status", "ready_at", "resolved_at")}, "Restart rewrote mission snapshot")
    account = data["research_accounts"][0]
    balance = {"create": 0, "mod-uninstalled": 126, "reinstalled": 337}[phase]
    earned = {"create": 0, "mod-uninstalled": 137, "reinstalled": 348}[phase]
    spent = 0 if phase == "create" else 11
    require(account.get("schema_version") == 1 and owner(account) and account.get("balance") == balance
            and account.get("lifetime_earned") == earned and account.get("lifetime_spent") == spent, "Research reward duplicated or lost")
    if phase == "create":
        require(recovery.uuid_from_nbt(state["current_mission_id"]) == missions[0], "Active mission binding differs")
    else:
        require("current_mission_id" not in state, "Claim left unfinished mission binding")
    require(terminal.get("schema_version") == 1 and owner(terminal) and terminal.get("energy") == 9000
            and terminal.get("selected_target") == 0 and terminal.get("last_result") == (9 if phase == "reinstalled" else 0),
            "Terminal native scalars differ")
    inventory = terminal["inventory"]
    require(inventory.get("Size") == 6 and len(inventory.get("Items", [])) == 2, "Terminal inventory count differs")
    slots = {item["Slot"]: item for item in inventory["Items"]}
    require(set(slots) == {2, 3} and slots[2] == {"Slot": 2, "id": "minecraft:amethyst_shard", "Count": 1},
            "Native payload consumption differs")
    chip = slots[3]
    require(chip.get("id") == HOST + ":satellite_control_chip" and chip.get("Count") == 1, "Native control chip differs")
    identity = chip["tag"]["SatelliteIdentity"]
    require(identity.get("schema_version") == 1 and owner(identity) and identity.get("definition_id") == DEFINITION
            and recovery.uuid_from_nbt(identity["satellite_id"]) == satellite, "Native chip binding changed")


class SatelliteRun(recovery.RecoveryRun):
    def fixture(self, process, action: str, code: int) -> dict:
        result = json.loads(self.query(process, f"arce_sat_fixture {action} {' '.join(map(str, POSITION))}", PROBE).group(1))
        self.satellite = validate_probe(result, action, getattr(self, "satellite", None), code)
        return result

    def inspect_mission(self, process) -> str:
        result = self.query(process, f"arce satellite inspect {self.satellite}",
                re.compile(rf"satellite={self.satellite} owner={OWNER} type={DEFINITION} status=OPERATIONAL mission=([0-9a-f-]{{36}})"))
        return str(uuid.UUID(result.group(1)))

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        installed = phase != "mod-uninstalled"
        if self.world_identity: server.complete_world_identity(self.server, self.world_identity)
        command = recovery.rocket_smoke._server_command(self.java)
        command.insert(1, "-Darce_adapter_test.satelliteSmoke=true")
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.server), "mods": self.mods(installed)})
        process = server.CapturedProcess(command, self.server, directory / "stdout.txt")
        original, self.commands = process.command, []
        def recorded(value):
            self.commands.append(value); original(value)
        process.command = recorded
        try:
            process.wait_for(server.READY_MARKER, self.timeout)
            status = server.wait_for_status(self.port)
            document["status_mods"] = recovery.validate_status(status, installed, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            validate_registration(process.lines, installed)
            process.command("forceload add 264 264")
            recovery.wait_condition(process, "execute if loaded 264 101 264", "V130_SAT_LOADED")
            if phase == "create":
                process.command("gamerule doMobSpawning false"); process.command("gamerule randomTickSpeed 0")
                process.command("setblock 264 101 264 minecraft:air")
                document["create"] = self.fixture(process, "create", 0)
                self.missions = [self.inspect_mission(process)]
                self.write_override()
                self.query(process, "reload", re.compile(r"Accepted satellite catalog generation 2 with 2 definitions"), 60)
                document["after_reload"] = self.fixture(process, "probe", 0)
            elif phase == "mod-uninstalled":
                process.wait_for(SCHEDULED, 45)
                for expected in ("SUCCESS", "ALREADY_CLAIMED"):
                    self.query(process, f"arce satellite release-test claim {self.missions[0]}", re.compile(
                            rf"ARCE_RELEASE_TEST_SATELLITE_CLAIM mission={self.missions[0]} owner={OWNER} target={HOST}:earth "
                            rf"code={expected} status=CLAIMED research=126 discovered=true"))
            else:
                document["before_launch"] = self.fixture(process, "probe", 0)
                document["launch"] = self.fixture(process, "launch", 0)
                self.missions.append(self.inspect_mission(process))
                process.wait_for(SCHEDULED, 10)
                document["claim"] = self.fixture(process, "claim", 0)
                document["replay"] = self.fixture(process, "claim", 9)
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop"); require(process.finish() == 0, "Satellite process did not exit cleanly")
        except BaseException:
            process.abort(); raise
        finally:
            document["exit_code"] = process.process.poll(); write_json(directory / "commands.json", self.commands)
            for name in ("debug.log", "latest.log"):
                source = self.server / "logs" / name
                if source.exists(): (directory / name).write_bytes(recovery.regular(source, 32 * 1024**2))
        mode = "assemble" if installed else "mod-uninstalled"
        document["accepted_missing_mapping_diagnostics"] = recovery.audit_log(process.lines, mode)
        recovery.validate_registration(process.lines, mode); validate_registration(process.lines, installed)
        document["log_counts"] = server.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server.verify_active_server_properties(recovery.regular(self.server / "server.properties", 65536), self.port)
        configs, files = {}, {}
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            target = directory / relative; target.parent.mkdir(parents=True, exist_ok=True)
            payload = recovery.regular(self.server / relative, 65536); target.write_bytes(payload)
            configs[relative] = flight.configuration_identity(relative, payload)
        require(self.config_hashes is None or self.config_hashes == configs, "Configuration changed across cycles")
        self.config_hashes = configs
        paths = [SAVED, "world/level.dat", "world/region/r.0.0.mca", PACK + "/pack.mcmeta"]
        if installed: paths.append(DEFINITION_FILE)
        for relative in paths:
            target = directory / relative; target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(recovery.regular(self.server / relative, recovery.region_nbt.MAX_REGION_BYTES))
            files[relative] = {"sha256": server.digest_file(target), "bytes": target.stat().st_size}
        data = decode_saved((directory / SAVED).read_bytes())
        chunk = recovery.region_nbt._decode_chunk(recovery.region_nbt._region_chunk(
                (directory / "world/region/r.0.0.mca").read_bytes(), 16, 16), 16, 16)
        terminals = [entry for entry in chunk.get("block_entities", []) if tuple(entry.get(axis) for axis in ("x", "y", "z")) == POSITION]
        require(len(terminals) == 1 and terminals[0].get("id") == HOST + ":satellite_terminal", "Native terminal missing")
        terminal = terminals[0]["SatelliteTerminal"]
        validate_disk(data, terminal, phase, self.satellite, self.missions, self.baseline)
        self.baseline = {recovery.uuid_from_nbt(value["mission_id"]): value for value in data["missions"]}
        write_json(directory / "native-state.json", {"satellites": data, "terminal": terminal})
        document.update(disk_evidence=files, satellite=self.satellite, missions=list(self.missions))
        self.mods(installed)
        return document

    def write_override(self) -> None:
        target = self.server / DEFINITION_FILE; target.parent.mkdir(parents=True, exist_ok=True)
        write_json(self.server / PACK / "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Satellite payload fixture"}})
        write_json(target, {"schema_version": 1, "id": DEFINITION, "mission_duration_ticks": 20,
                           "research_yield": 211, "discovery_cost": 11, "allowed_targets": [HOST + ":earth", HOST + ":moon"]})


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--host-jar", type=Path, required=True); parser.add_argument("--fixture-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.3.0-dev")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    args, output = parser.parse_args(), None
    try:
        require(0 < args.startup_timeout <= 240, "Startup timeout must be within (0, 240]")
        root, output_path, artifacts, args_file = recovery.validate_inputs(args.server_dir, args.evidence_dir,
                args.host_jar, args.fixture_jar, args.expected_version)
        java, java_version = server.resolve_java(args.java)
        output_path.mkdir(parents=True); output = output_path
        port = server.allocate_port(); properties = server.write_server_configuration(root, port, True)
        (root / "mods").mkdir()
        for item in artifacts: shutil.copyfile(item["path"], root / "mods" / item["name"])
        run = SatelliteRun(root, output, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "three clean processes; actual terminal, reload, mod removal and snapshotted research",
                   "artifacts": artifacts, "java": java_version, "server": str(root), "port": port,
                   "forge_args_sha256": server.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(output / "summary.json", summary)
        for phase in PHASES:
            fixture = root / "mods" / artifacts[1]["name"]
            if phase == "mod-uninstalled":
                run.mods(True); recovery.safe_path(fixture).unlink()
                # Remove only the definition authored by this run; retain the valid empty pack metadata.
                recovery.safe_path(root / DEFINITION_FILE).unlink()
            elif phase == "reinstalled":
                run.mods(False)
                require(server.digest_file(Path(artifacts[1]["path"])) == artifacts[1]["sha256"], "Fixture input changed")
                shutil.copyfile(artifacts[1]["path"], fixture); run.write_override()
            summary["cycles"].append(run.run_phase(phase))
            if phase == "create": run.world_identity = server.establish_world_identity(root, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
            summary["world"] = server.complete_world_identity(root, run.world_identity)
            write_json(output / "summary.json", summary)
        for item in artifacts: require(server.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed")
        summary["result"] = "PASS"; write_json(output / "summary.json", summary)
        print(f"[PASS] Three finite satellite payload cycles; evidence: {output}"); return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError) as exc:
        if output is not None: write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr); return 1
    finally:
        if output is not None and output.is_dir():
            paths = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in paths),
                                              encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
