#!/usr/bin/env python3
"""Two finite dedicated processes for API environment queries and native station identity.

Creates two stations through the real operator service, changes Moon data through
a live datapack reload, and compares API projections to saved station records on
restart. No players, forced crash, remote execution or load campaign is involved.
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
import zipfile
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
PHASES = ("create", "restart")
READY = "Environment query handle ready (API 1.7)"
EXPIRED = "Environment query handle expired at server stopping"
STATIONS = "world/data/advancedrocketrycommunity_stations.dat"
MOON_DATA = f"data/{HOST}/celestial_bodies/moon.json"
FAR = (28_000_000, 0, -28_000_000)


def validate_lifecycle(lines: list[str]) -> None:
    ready = [line for line in lines if "Environment query handle ready" in line]
    expired = [line for line in lines if "Environment query handle expired" in line]
    require(len(ready) == len(expired) == 1 and ready[0].rstrip().endswith(READY)
            and expired[0].rstrip().endswith(EXPIRED), "Environment lifecycle receipt differs")
    require(lines.index(ready[0]) < lines.index(expired[0]), "Handle expired before publication")


def validate_probe(report: dict, label: str, dimension: str, snapshot: dict | None,
                   unloaded: bool = False) -> None:
    require(report.get("label") == label and report.get("dimension") == dimension,
            "Environment probe identity differs")
    require(report.get("resolved") is (snapshot is not None), "Environment resolution differs")
    require(report.get("snapshot") == snapshot, "Configured environment snapshot differs")
    before, after = report.get("loaded_before"), report.get("loaded_after")
    require(type(before) is bool and type(after) is bool and before == after,
            "Environment query changed immediate loaded-chunk state")
    if unloaded:
        require(not before and not after, "Unloaded query precondition or result differs")


def surface(body: str, gravity: float) -> dict:
    earth = body == "earth"
    return {"body": HOST + ":" + body, "locus": "SURFACE", "gravity": gravity,
            "vacuum": not earth, "atmosphere": {"pressure": 1.0 if earth else 0.0,
            "breathable": earth, "temperature": 288.0 if earth else 220.0,
            "profile": HOST + (":earth" if earth else ":vacuum")}}


def orbital(body: str, station_id: str) -> dict:
    return {"body": HOST + ":" + body, "locus": "STATION_ORBIT", "instance": station_id,
            "gravity": 0.0, "vacuum": True}


def decode_stations(raw: bytes) -> dict:
    with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:
        payload = stream.read(4 * 1024**2 + 1)
    require(len(payload) <= 4 * 1024**2, "Station SavedData exceeds fixture bound")
    root = recovery.NbtReader(payload).read_root()
    require(isinstance(root, dict) and isinstance(root.get("data"), dict), "Missing SavedData root")
    data = root["data"]
    require(data.get("schema_version") == 2 and data.get("reservations") == []
            and isinstance(data.get("stations"), list) and len(data["stations"]) == 2,
            "Native station count/schema differs")
    return data


def validate_stations(data: dict, receipts: dict, previous: dict | None) -> None:
    require(set(receipts) == {"earth", "moon"}, "Station receipts are incomplete")
    actual = {recovery.uuid_from_nbt(value["station_id"]): value for value in data["stations"]}
    require(len(actual) == 2 and set(actual) == {value["id"] for value in receipts.values()},
            "Native station identities differ")
    for body, receipt in receipts.items():
        value = actual[receipt["id"]]
        require(value.get("schema_version") == 1 and value.get("orbit_body") == HOST + ":" + body
                and value.get("landing_pad") == receipt["pad"] and value.get("environment") == {
                    "gravity_milli": 0, "vacuum": 1, "solar_angle_milli_degrees": 270000},
                "Native station environment/context differs")
    if previous is not None:
        require(data == previous, "Station registry changed across query-only restart")


class EnvironmentRun(recovery.RecoveryRun):
    def probe(self, process, label: str, dimension: str, position: tuple | list,
              snapshot: dict | None, unloaded: bool = False) -> dict:
        match = self.query(process, f"arce_env_probe {label} {dimension} {' '.join(map(str, position))}",
                           re.compile(r"ARCE_ENV_PROBE (\{.*\})\s*$"))
        report = json.loads(match.group(1))
        validate_probe(report, label, dimension, snapshot, unloaded)
        return report

    def _run_phase(self, phase: str, directory: Path, document: dict) -> dict:
        require(phase in PHASES, "Unknown environment phase")
        if self.world_identity:
            server.complete_world_identity(self.server, self.world_identity)
        command = recovery.rocket_smoke._server_command(self.java)
        command.insert(1, "-Darce_adapter_test.environmentSmoke=true")
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.server), "mods": self.mods(True)})
        process = server.CapturedProcess(command, self.server, directory / "stdout.txt")
        original, self.commands = process.command, []

        def recorded(value):
            self.commands.append(value)
            original(value)

        process.command = recorded
        probes = []
        try:
            process.wait_for(server.READY_MARKER, self.timeout)
            status = server.wait_for_status(self.port)
            document["status_mods"] = recovery.validate_status(status, True, self.artifacts[0]["version"])
            write_json(directory / "status.json", status)
            recovery.validate_registration(process.lines, "assemble")
            probes.append(self.probe(process, "earth", "minecraft:overworld", FAR, surface("earth", 1.0), True))
            probes.append(self.probe(process, "moon", HOST + ":moon", FAR,
                                     surface("moon", 0.165 if phase == "create" else 0.4), True))
            probes.append(self.probe(process, "space_gap", HOST + ":space", FAR, None, True))
            probes.append(self.probe(process, "unknown", "arce_adapter_test:absent", FAR, None, True))
            if phase == "create":
                self.receipts = {}
                for body in ("earth", "moon"):
                    created = self.query(process, f"arce station admin create {uuid.uuid4()} {HOST}:{body} Env {body}",
                                         re.compile(rf"Created Env {body} id=([0-9a-f-]{{36}}) cell=(-?\d+),(-?\d+)"))
                    station_id = str(uuid.UUID(created.group(1)))
                    inspected = self.query(process, f"arce station admin inspect {station_id}",
                                           re.compile(r"region=(-?\d+),(-?\d+)\.\.(-?\d+),(-?\d+) "
                                                      r"pad=(-?\d+),(-?\d+),(-?\d+) orbit=(\S+) gravity_milli=(\d+) vacuum=(true|false)"))
                    require(inspected.group(8) == HOST + ":" + body and inspected.group(9, 10) == ("0", "true"),
                            "Station inspect receipt differs")
                    self.receipts[body] = {"id": station_id, "pad": [int(inspected.group(i)) for i in (5, 6, 7)]}
            for body, receipt in self.receipts.items():
                probes.append(self.probe(process, body + "_orbit", HOST + ":space", receipt["pad"], orbital(body, receipt["id"])))
            if phase == "create":
                pack = self.server / "world/datapacks/env_query"
                target = pack / MOON_DATA
                target.parent.mkdir(parents=True)
                write_json(pack / "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Environment query fixture"}})
                with zipfile.ZipFile(self.artifacts[0]["path"]) as archive:
                    definition = json.loads(archive.read(MOON_DATA))
                definition["gravity_multiplier"] = 0.4
                write_json(target, definition)
                self.query(process, "reload", re.compile(r"Accepted celestial catalog generation 2 with 3 bodies"), 60)
                probes.append(self.probe(process, "reloaded_moon", HOST + ":moon", FAR, surface("moon", 0.4), True))
                probes.append(self.probe(process, "reloaded_orbit", HOST + ":space", self.receipts["moon"]["pad"],
                                         orbital("moon", self.receipts["moon"]["id"])))
            document["station_receipts"] = self.receipts
            document["probes"] = probes
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop")
            require(process.finish() == 0, "Dedicated environment process did not exit cleanly")
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
        validate_lifecycle(process.lines)
        recovery.audit_log(process.lines, "assemble")
        document["log_counts"] = server.log_audit_counts(process.lines)
        document["warnings"] = [line.rstrip() for line in process.lines if "WARN" in line]
        document["active_properties"] = server.verify_active_server_properties(
                recovery.regular(self.server / "server.properties", 65536), self.port)
        configs = {}
        for relative in ("server.properties", "config/advancedrocketrycommunity-common.toml", "config/fml.toml"):
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            payload = recovery.regular(self.server / relative, 65536)
            target.write_bytes(payload)
            configs[relative] = flight.configuration_identity(relative, payload)
        require(self.config_hashes is None or self.config_hashes == configs, "Configuration changed across cycles")
        self.config_hashes = configs
        files = {}
        for relative in (STATIONS, "world/level.dat", "world/datapacks/env_query/pack.mcmeta",
                         "world/datapacks/env_query/" + MOON_DATA):
            target = directory / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(recovery.regular(self.server / relative, 4 * 1024**2))
            files[relative] = {"sha256": server.digest_file(target), "bytes": target.stat().st_size}
        data = decode_stations((directory / STATIONS).read_bytes())
        validate_stations(data, self.receipts, self.baseline)
        self.baseline = data
        write_json(directory / "native-stations.json", data)
        document["disk_evidence"] = files
        self.mods(True)
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
    args, output = parser.parse_args(), None
    try:
        require(0 < args.startup_timeout <= 240, "Startup timeout must be within (0, 240]")
        root, output_path, artifacts, args_file = recovery.validate_inputs(args.server_dir, args.evidence_dir,
                args.host_jar, args.fixture_jar, args.expected_version)
        java, java_version = server.resolve_java(args.java)
        output_path.mkdir(parents=True)
        output = output_path
        port = server.allocate_port()
        properties = server.write_server_configuration(root, port, True)
        (root / "mods").mkdir()
        for item in artifacts:
            shutil.copyfile(item["path"], root / "mods" / item["name"])
        run = EnvironmentRun(root, output, artifacts, java, port, args.startup_timeout)
        summary = {"schema_version": 1, "scope": "two clean processes; configured environment; live reload; native station identity",
                   "artifacts": artifacts, "java": java_version, "server": str(root), "port": port,
                   "forge_args_sha256": server.digest_file(args_file), "cycles": [], "result": "IN_PROGRESS"}
        write_json(output / "summary.json", summary)
        for phase in PHASES:
            summary["cycles"].append(run.run_phase(phase))
            if phase == "create":
                run.world_identity = server.establish_world_identity(root, str(uuid.uuid4()), artifacts[0]["sha256"], properties)
            summary["world"] = server.complete_world_identity(root, run.world_identity)
            write_json(output / "summary.json", summary)
        for item in artifacts:
            require(server.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed")
        summary["result"] = "PASS"
        write_json(output / "summary.json", summary)
        print(f"[PASS] Two finite environment query cycles; evidence: {output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, SmokeError) as exc:
        if output is not None:
            write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if output is not None and output.is_dir():
            paths = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in paths),
                                              encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
