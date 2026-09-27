#!/usr/bin/env python3
"""Two finite packaged-server processes for schema-2 data and retained station identity.

Uses a fresh libraries-only disposable server. Adds an unmapped gas body and
closes Moon arrival/orbit after creating a station, then checks native restart.
No clients, new dimensions, crash injection or long-load campaign is involved.
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
import os
import platform
import re
import shutil
import sys
import uuid
import zipfile
from pathlib import Path

if __package__:
    from . import run_v130_adapter_recovery_smoke as support
else:
    import run_v130_adapter_recovery_smoke as support

server, SmokeError = support.server_smoke, support.SmokeError
write_json = support.write_json
HOST = support.HOST
STATIONS = "world/data/advancedrocketrycommunity_stations.dat"
GAS = HOST + ":schema_probe_gas"
PACK = "world/datapacks/schema_probe/data/" + HOST


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeError(message)


def read_nbt(path: Path) -> dict:
    with gzip.GzipFile(fileobj=io.BytesIO(support.regular(path, 4 * 1024**2))) as stream:
        payload = stream.read(4 * 1024**2 + 1)
    require(len(payload) <= 4 * 1024**2, "Native NBT exceeds fixture bound")
    return support.NbtReader(payload).read_root()


def validate_unchanged_station(actual: dict, baseline: dict) -> None:
    require(actual == baseline, "Reload/denied creation/restart changed native station authority")


def install_pack(root: Path, artifact: Path) -> dict:
    pack = root / "world/datapacks/schema_probe"
    directory = pack / "data" / HOST / "celestial_bodies"
    directory.mkdir(parents=True)
    write_json(pack / "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Celestial schema restart fixture"}})
    with zipfile.ZipFile(artifact) as archive:
        moon = json.loads(archive.read(f"data/{HOST}/celestial_bodies/moon.json"))
    moon.update(schema_version=2, capabilities={"landable": False, "orbitable": False, "gas_giant": False},
                solar_intensity=0.5, radiation=0.25)
    gas = {"schema_version": 2, "id": GAS, "parent": HOST + ":earth", "gravity_multiplier": 2.5,
           "atmosphere": {"pressure": 10.0, "breathable": False, "temperature_kelvin": 150.0, "profile": GAS},
           "orbit": {"distance": 100, "period_ticks": 1000, "inclination_degrees": 0.0}, "visual_profile": GAS,
           "capabilities": {"landable": False, "orbitable": True, "gas_giant": True},
           "solar_intensity": 0.04, "radiation": 0.5}
    write_json(directory / "moon.json", moon)
    write_json(directory / "schema_probe_gas.json", gas)
    return {"moon": moon, "gas": gas}


def install_route(root: Path, artifact: Path, distance: int) -> dict:
    with zipfile.ZipFile(artifact) as archive:
        route = json.loads(archive.read(f"data/{HOST}/travel_routes/earth_moon.json"))
    route["distance_units"] = distance
    directory = root / PACK / "travel_routes"
    directory.mkdir(exist_ok=True)
    write_json(directory / "earth_moon.json", route)
    return route


def route_marker(generation: int, distance: int) -> str:
    return (rf"Planetary route generation={generation} id=" + re.escape(HOST + ":earth_moon")
            + r" from=" + re.escape(HOST + ":body_surface/" + HOST + ":earth")
            + r" to=" + re.escape(HOST + ":body_surface/" + HOST + ":moon")
            + rf" distance={distance} bidirectional=true")


def rejection_fixtures() -> list[tuple[str, str, str, str]]:
    missing_route = {"schema_version": 1, "id": HOST + ":raw_probe", "distance_units": 1, "bidirectional": False,
                     "from": {"type": HOST + ":body_surface", "body_id": HOST + ":earth"},
                     "to": {"type": HOST + ":orbit", "body_id": HOST + ":missing"}}
    return [
        ("body-syntax", "celestial_bodies", "{", "celestial_bodies/raw_probe.json"),
        ("route-syntax", "travel_routes", "{", "travel_routes/raw_probe.json"),
        ("duplicate-key", "celestial_bodies", '{"id":null,"id":null}', "duplicate JSON key: id"),
        ("depth", "celestial_bodies", "[" * 17 + "0" + "]" * 17, "JSON nesting exceeds 16"),
        ("route-bytes", "travel_routes", " " * 4097, "definition exceeds 4096 UTF-8 bytes"),
        ("missing-body", "travel_routes", json.dumps(missing_route), "references missing body " + HOST + ":missing"),
    ]


def verify_joint_rejections(root: Path, output: Path, artifact: Path, query) -> tuple[list[str], list[dict]]:
    moon_path = root / PACK / "celestial_bodies/moon.json"
    moon = json.loads(moon_path.read_text(encoding="utf-8"))
    moon["solar_intensity"] = 0.75
    write_json(moon_path, moon)
    route = install_route(root, artifact, 222)
    expected_errors, receipts = [], []
    for label, directory, payload, diagnostic in rejection_fixtures():
        path = root / PACK / directory / "raw_probe.json"
        path.write_text(payload, encoding="utf-8", newline="\n")
        fixture = output / "rejected-inputs" / label
        fixture.mkdir(parents=True)
        (fixture / "raw_probe.json").write_bytes(path.read_bytes())
        write_json(fixture / "moon.json", moon)
        write_json(fixture / "earth_moon.json", route)
        rejected = query("reload", r"Rejected planetary catalog; last valid generation remains active: .*"
                         + re.escape(diagnostic), 60)
        expected_errors.append(rejected.string.rstrip())
        query("arce celestial validate", r"Celestial catalog generation 2 retained after rejected reload")
        observed_route = query("arce celestial route earth_moon", route_marker(2, 123))
        observed_moon = query("arce celestial list", re.escape(HOST + ":moon -> " + HOST + ":moon")
                              + r" .*landable=false orbitable=false gas_giant=false solar=0.5 radiation=0.25")
        receipts.append({"case": label, "diagnostic": rejected.string.rstrip(),
                         "route": observed_route.string.rstrip(), "moon": observed_moon.string.rstrip()})
        path.unlink()
    query("reload", r"Accepted planetary catalog generation 3 with 4 bodies, 4 routes", 60)
    query("arce celestial route earth_moon", route_marker(3, 222))
    return expected_errors, receipts


def validate_log(lines: list[str], expected_errors: list[str]) -> None:
    require(server.scan_log(lines) == expected_errors,
            "Blocking log findings differ from the exact observed fault-injection rejections")


def run_cycle(root: Path, output: Path, artifact: dict, java: str, port: int, phase: str,
              station_id: str | None, baseline: dict | None, joint_reload: bool = False) -> tuple[str, dict]:
    directory = output / phase
    directory.mkdir()
    command = support.rocket_smoke._server_command(java)
    command.remove("-Dadvancedrocketrycommunity.releaseTestHooks=true")
    write_json(directory / "launch.json", {"command": command, "cwd": str(root), "artifact": artifact})
    process = None
    commands, observations = [], {"phase": phase, "result": "IN_PROGRESS"}
    expected_errors = []

    def query(value: str, pattern, timeout: float = 30):
        marker = re.compile(pattern) if isinstance(pattern, str) else pattern
        start = len(process.lines)
        commands.append(value)
        process.command(value)
        index = process.wait_for(marker, timeout, start_at=start)
        return marker.search(process.lines[index])

    try:
        process = server.CapturedProcess(command, root, directory / "stdout.txt")
        process.wait_for(server.READY_MARKER, 240)
        status = server.wait_for_status(port)
        server.validate_status_identity(status, artifact["version"])
        require(set(server.forge_mod_versions(status)) == {HOST, "minecraft", "forge"}, "Unexpected mod set")
        require(status.get("players", {}).get("online") == 0, "Smoke requires an empty server")
        write_json(directory / "status.json", status)
        if phase == "create-reload":
            query("arce celestial validate", r"Celestial catalog generation 1 is valid with 3 bodies")
            if joint_reload:
                query("arce celestial route earth_moon", route_marker(1, 50))
            created = query(f"arce station admin create {uuid.uuid4()} {HOST}:moon Schema probe",
                            r"Created Schema probe id=([0-9a-f-]{36}) cell=(-?\d+),(-?\d+)")
            station_id = str(uuid.UUID(created.group(1)))
            query("save-all flush", server.SAVE_MARKER, 60)
            shutil.copyfile(root / STATIONS, directory / "before-reload-stations.dat")
            baseline = read_nbt(directory / "before-reload-stations.dat")["data"]
            require(baseline.get("schema_version") == 2 and baseline.get("reservations") == []
                    and len(baseline.get("stations", [])) == 1, "Initial native station shape differs")
            station = baseline["stations"][0]
            require(support.uuid_from_nbt(station["station_id"]) == station_id
                    and station["orbit_body"] == HOST + ":moon", "Native station differs from creation receipt")
            observations["definitions"] = install_pack(root, Path(artifact["path"]))
            if joint_reload:
                observations["route"] = install_route(root, Path(artifact["path"]), 123)
            query("reload", r"Accepted planetary catalog generation 2 with 4 bodies, 4 routes", 60)
            if joint_reload:
                query("arce celestial route earth_moon", route_marker(2, 123))
                expected_errors, observations["rejections"] = verify_joint_rejections(
                    root, directory, Path(artifact["path"]), query)
        require(station_id is not None and baseline is not None, "Missing native station baseline")
        generation = (3 if joint_reload else 2) if phase == "create-reload" else 1
        solar = 0.75 if joint_reload else 0.5
        if joint_reload:
            query("arce celestial route earth_moon", route_marker(generation, 222))
        query("arce celestial validate", rf"Celestial catalog generation {generation} is valid with 4 bodies")
        start = len(process.lines)
        query("arce celestial list", re.escape(GAS)
              + r" -> unmapped gravity=2.5 atmosphere=" + re.escape(GAS)
              + r" landable=false orbitable=true gas_giant=true solar=0.04 radiation=0.5")
        listing = process.lines[start:]
        require(any(HOST + ":moon -> " + HOST + ":moon" in line and
                    f"landable=false orbitable=false gas_giant=false solar={solar} radiation=0.25" in line for line in listing),
                "Moon capability or mapping observation differs")
        observations["listing"] = listing
        inspected = query(f"arce station admin inspect {station_id}",
                          r"region=(-?\d+),(-?\d+)\.\.(-?\d+),(-?\d+) pad=(-?\d+),(-?\d+),(-?\d+) "
                          + re.escape("orbit=" + HOST + ":moon") + r" gravity_milli=0 vacuum=true")
        require([int(inspected.group(i)) for i in (5, 6, 7)] == baseline["stations"][0]["landing_pad"],
                "Live station position differs from native record")
        query(f"arce station admin create {uuid.uuid4()} {HOST}:moon Denied schema probe",
              r"Station creation failed: UNKNOWN_ORBIT_BODY")
        # Resolve actual started Levels; a whole Forge level.dat registry is not a
        # celestial SavedData document and must not use its small NBT tag budget.
        dimensions = {}
        for dimension in ("minecraft:overworld", HOST + ":moon", HOST + ":space"):
            observed = query(f"execute in {dimension} run time query gametime", r"The time is (\d+)")
            dimensions[dimension] = {"available": True, "game_time": int(observed.group(1))}
        query(f"execute in {GAS} run time query gametime", re.escape(f"Unknown dimension '{GAS}'"))
        dimensions[GAS] = {"available": False}
        query("save-all flush", server.SAVE_MARKER, 60)
        commands.append("stop")
        process.command("stop")
        require(process.finish() == 0, "Dedicated process did not exit cleanly")
        validate_log(process.lines, expected_errors)
        require(server.digest_file(root / "mods" / artifact["name"]) == artifact["sha256"], "Installed JAR changed")
        support.regular(root / STATIONS, 4 * 1024**2)
        shutil.copyfile(root / STATIONS, directory / "stations.dat")
        actual = read_nbt(directory / "stations.dat")["data"]
        validate_unchanged_station(actual, baseline)
        write_json(directory / "native-stations.json", actual)
        support.regular(root / "world/level.dat", 4 * 1024**2)
        shutil.copyfile(root / "world/level.dat", directory / "level.dat")
        observations.update(result="PASS", station_id=station_id, live_dimension_probes=dimensions,
                            expected_fault_errors=expected_errors,
                            log_counts=server.log_audit_counts(process.lines), exit_code=process.process.returncode)
        return station_id, baseline
    except BaseException as exc:
        observations.update(result="FAIL", error=f"{type(exc).__name__}: {exc}")
        if process is not None:
            process.abort()
        raise
    finally:
        observations["exit_code"] = process.process.poll() if process is not None else None
        write_json(directory / "observations.json", observations)
        write_json(directory / "commands.json", commands)
        for name in ("debug.log", "latest.log"):
            path = root / "logs" / name
            if path.exists():
                (directory / name).write_bytes(support.regular(path, 32 * 1024**2))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--host-jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--expected-version", default="1.20.1-1.4.0-dev")
    parser.add_argument("--check-joint-reload", action="store_true", help="Exercise six bounded rejected candidates and recovery")
    parser.add_argument("--accept-eula", action="store_true", required=True)
    args, output = parser.parse_args(), None
    try:
        root, evidence, host = map(support.safe_path, (args.server_dir, args.evidence_dir, args.host_jar))
        require(root.is_dir() and {p.name for p in root.iterdir()} == {"libraries"},
                "Disposable server must contain only prepared libraries")
        require(not evidence.exists() and root != evidence and root not in evidence.parents and evidence not in root.parents,
                "Evidence must be new and disjoint from server")
        require(root not in host.parents and evidence not in host.parents, "Artifact must be outside outputs")
        for directory, children, files in os.walk(root / "libraries", followlinks=False):
            for name in children + files:
                support.safe_path(Path(directory) / name)
        args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
        args_file = root / "libraries/net/minecraftforge/forge" / server.FORGE_COORDINATE / args_name
        support.regular(args_file, 65536)
        artifact = support.artifact(host, HOST, args.expected_version)
        java, version = server.resolve_java(args.java)
        evidence.mkdir(parents=True)
        output = evidence
        port = server.allocate_port()
        properties = server.write_server_configuration(root, port, True)
        (root / "mods").mkdir()
        shutil.copyfile(host, root / "mods" / artifact["name"])
        summary = {"schema_version": 1, "scope": "schema data and native station restart; no clients or load campaign",
                   "joint_reload": args.check_joint_reload,
                   "artifact": artifact, "java": version, "port": port, "server": str(root),
                   "forge_args_sha256": server.digest_file(args_file), "result": "IN_PROGRESS"}
        write_json(output / "summary.json", summary)
        station, baseline = run_cycle(root, output, artifact, java, port, "create-reload", None, None, args.check_joint_reload)
        identity = server.establish_world_identity(root, str(uuid.uuid4()), artifact["sha256"], properties)
        server.complete_world_identity(root, identity)
        run_cycle(root, output, artifact, java, port, "restart", station, baseline, args.check_joint_reload)
        require(server.digest_file(host) == artifact["sha256"], "Input JAR changed")
        summary.update(result="PASS", station_id=station, world=server.complete_world_identity(root, identity))
        write_json(output / "summary.json", summary)
        print(f"[PASS] Two bounded schema/native-station cycles; evidence: {output}")
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
                f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in paths), encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
