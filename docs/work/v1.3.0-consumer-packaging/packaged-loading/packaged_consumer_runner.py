"""Bounded two-JAR production loading smoke; Temp-only orchestration."""
from __future__ import annotations

import json
import re
import shutil
import socket
import sys
import tomllib
import zipfile
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
REPO = Path("D:/GitHub/AdvancedRocketry-Community")
sys.path.insert(0, str(REPO / "scripts"))
import run_dedicated_server_smoke as smoke

BASE = Path(__file__).resolve().parent
SERVER = BASE / "server"
LIBRARIES = Path("C:/Users/Administrator/AppData/Local/Temp/arce-v130-registration-restart-ddbc229d61ca44caa9b6d26afcb536f6/server/libraries")
JAVA = "C:/Program Files/Java/jdk-17.0.7/bin/java.exe"
REGISTRATION = "Registered rocket adapter arce_adapter_test:cargo_inventory (payload 1, API 1.1, event 1)"
LINKAGE = re.compile(r"NoSuchMethodError|AbstractMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|LinkageError")


def write_json(path: Path, value: object) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, sort_keys=True, indent=2) + "\n")


def prepare() -> None:
    SERVER.mkdir()
    files = sorted(LIBRARIES.rglob("*"))
    if any(smoke.is_link_or_junction(path) for path in [LIBRARIES, *files]):
        raise smoke.SmokeError("Source libraries contain a link or junction")
    shutil.copytree(LIBRARIES, SERVER / "libraries")
    manifest = []
    for source in files:
        if source.is_file():
            relative = source.relative_to(LIBRARIES)
            digest = smoke.digest_file(source)
            if smoke.digest_file(SERVER / "libraries" / relative) != digest:
                raise smoke.SmokeError(f"Library copy differs: {relative}")
            manifest.append({"path": relative.as_posix(), "bytes": source.stat().st_size, "sha256": digest})
    if {path.name for path in SERVER.iterdir()} != {"libraries"}:
        raise smoke.SmokeError("Preparation copied state outside libraries")
    write_json(BASE / "preparation.json", {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "source": str(LIBRARIES), "libraries": manifest,
        "server_entries_after_copy": sorted(path.name for path in SERVER.iterdir()),
        "helper": {"path": str(REPO / "scripts/run_dedicated_server_smoke.py"),
                   "sha256": smoke.digest_file(REPO / "scripts/run_dedicated_server_smoke.py")},
    })
    print(f"Prepared {len(manifest)} checked libraries; no world/mods/config or Java start", flush=True)


def install_artifacts(spec: dict) -> list[dict]:
    if any((SERVER / name).exists() for name in ("world", "mods", "config")):
        raise smoke.SmokeError("Expected a new world/mods/config state")
    artifacts = spec["artifacts"]
    if {item["mod_id"] for item in artifacts} != {"advancedrocketrycommunity", "arce_adapter_test"} or len(artifacts) != 2:
        raise smoke.SmokeError("Expected exactly the host and independent fixture artifacts")
    (SERVER / "mods").mkdir()
    result = []
    for item in artifacts:
        source = Path(item["path"])
        if smoke.is_link_or_junction(source) or not source.is_file() or smoke.digest_file(source) != item["sha256"]:
            raise smoke.SmokeError(f"Artifact identity mismatch: {source}")
        with zipfile.ZipFile(source) as jar:
            metadata = tomllib.loads(jar.read("META-INF/mods.toml").decode("utf-8"))
            records = [(mod["modId"], mod["version"]) for mod in metadata["mods"]]
            if records != [(item["mod_id"], item["version"])]:
                raise smoke.SmokeError(f"Unexpected JAR mod metadata: {records}")
            if item["mod_id"] == "arce_adapter_test" and any(
                    name.startswith("io/github/sunthemoon/advancedrocketrycommunity/") for name in jar.namelist()):
                raise smoke.SmokeError("Consumer bundles host classes")
        target = SERVER / "mods" / source.name
        shutil.copy2(source, target)
        if smoke.digest_file(target) != item["sha256"]:
            raise smoke.SmokeError("Installed JAR differs from the supplied artifact")
        result.append({**item, "installed": str(target), "bytes": target.stat().st_size})
    return result


def inspect_cycle(cycle, artifacts: list[dict], captures: Path) -> dict:
    full = SERVER / cycle.full_log_file
    target = captures / full.name
    shutil.copy2(full, target)
    if smoke.digest_file(target) != cycle.full_log_sha256:
        raise smoke.SmokeError("Full process capture copy differs")
    debug = SERVER / "logs/debug.log"
    saved_debug = captures / f"{cycle.name}-debug.log"
    shutil.copy2(debug, saved_debug)
    if smoke.digest_file(debug) != smoke.digest_file(saved_debug):
        raise smoke.SmokeError("Debug log capture copy differs")
    debug_lines = saved_debug.read_text(encoding="utf-8").splitlines()
    lines = cycle.lines + debug_lines
    findings = smoke.scan_log(lines)
    linkage = [line for line in lines if LINKAGE.search(line)]
    if findings or linkage:
        raise smoke.SmokeError(f"Blocking load/log finding: {(findings + linkage)[0]}")
    registration = [line for line in cycle.lines if REGISTRATION in line]
    if len(registration) != 1:
        raise smoke.SmokeError(f"Expected exactly one successful fixture registration, got {registration}")
    versions = smoke.forge_mod_versions(cycle.status)
    sources = []
    for item in artifacts:
        if versions.get(item["mod_id"]) != item["version"]:
            raise smoke.SmokeError(f"Status omitted or changed {item['mod_id']}")
        loaded = [line for line in debug_lines if "Loading mod file " + item["installed"] in line]
        valid = [line for line in debug_lines if f"Found valid mod file {Path(item['installed']).name} with {{{item['mod_id']}}} mods - versions {{{item['version']}}}" in line]
        if not loaded or not valid:
            raise smoke.SmokeError(f"No actual packaged JAR source receipt for {item['mod_id']}")
        sources.extend(loaded + valid)
    status_source = SERVER / f"{cycle.name}-status.json"
    shutil.copy2(status_source, captures / status_source.name)
    return {
        "name": cycle.name, "exit_code": cycle.exit_code,
        "started_at": cycle.started_at, "completed_at": cycle.completed_at,
        "full_log": str(target.relative_to(BASE)), "full_log_sha256": smoke.digest_file(target),
        "debug_log": str(saved_debug.relative_to(BASE)), "debug_log_sha256": smoke.digest_file(saved_debug),
        "actual_mod_sources": sources, "registration_lines": registration,
        "status_mod_versions": versions, "audit_counts": smoke.log_audit_counts(cycle.lines),
        "warnings": [line for line in cycle.lines if re.search(r"\bWARN\b", line)],
    }


def run() -> None:
    spec = json.loads((BASE / "artifact-spec.json").read_text(encoding="utf-8"))
    artifacts = install_artifacts(spec)
    write_json(BASE / "installed-artifacts.json", artifacts)
    java, java_version = smoke.resolve_java(JAVA)
    if not java_version.startswith("17."):
        raise smoke.SmokeError("Packaged smoke requires Java 17")
    port = smoke.allocate_port()
    properties_hash = smoke.write_server_configuration(SERVER, port, True)
    smoke.verify_active_server_properties((SERVER / "server.properties").read_bytes(), port)
    captures = BASE / "captures"
    captures.mkdir()
    host = next(item for item in artifacts if item["mod_id"] == "advancedrocketrycommunity")
    session_id = smoke.build_session_id(host["sha256"], datetime.now(timezone.utc).isoformat(), port)
    print(f"Starting isolated packaged pair at 127.0.0.1:{port}; host={host['sha256']}", flush=True)
    first = smoke.run_cycle("first-start", session_id + "-first", java, SERVER, port, 240,
                            expected_mod_version=host["version"])
    first_report = inspect_cycle(first, artifacts, captures)
    write_json(BASE / "first-start-observations.json", first_report)
    identity = smoke.establish_world_identity(SERVER, session_id, host["sha256"], properties_hash)
    print("First packaged pair start/status/registration/save/stop verified", flush=True)
    restart = smoke.run_cycle("restart", session_id + "-restart", java, SERVER, port, 240,
                              expected_mod_version=host["version"])
    restart_report = inspect_cycle(restart, artifacts, captures)
    write_json(BASE / "restart-observations.json", restart_report)
    identity = smoke.complete_world_identity(SERVER, identity)
    for item in artifacts:
        if smoke.digest_file(Path(item["path"])) != item["sha256"] or smoke.digest_file(Path(item["installed"])) != item["sha256"]:
            raise smoke.SmokeError("Artifact changed during packaged loading check")
    smoke.verify_active_server_properties((SERVER / "server.properties").read_bytes(), port)
    try:
        connection = socket.create_connection(("127.0.0.1", port), timeout=1)
    except OSError:
        pass
    else:
        connection.close()
        raise smoke.SmokeError("Packaged smoke port remains open")
    prepared = json.loads((BASE / "preparation.json").read_text(encoding="utf-8"))
    if smoke.digest_file(Path(prepared["helper"]["path"])) != prepared["helper"]["sha256"]:
        raise smoke.SmokeError("Repository smoke helper changed during verification")
    summary = {"schema_version": 1, "session_id": session_id, "artifacts": artifacts,
               "java": java_version, "forge": smoke.FORGE_VERSION, "minecraft": smoke.MINECRAFT_VERSION,
               "server_port": port, "server_bind": "127.0.0.1", "offline_mode": True,
               "world": identity, "cycles": [first_report, restart_report],
               "completed_at": datetime.now(timezone.utc).isoformat(),
               "scope": "production two-JAR startup and clean same-world restart; no external payload persistence"}
    write_json(BASE / "summary.json", summary)
    shutil.copy2(SERVER / smoke.SERVER_PROPERTIES_IDENTITY_FILE, captures / smoke.SERVER_PROPERTIES_IDENTITY_FILE)
    print("PASS: both packaged JARs and one registration per process observed; restart clean; port closed", flush=True)


if __name__ == "__main__":
    try:
        {"prepare": prepare, "run": run}[sys.argv[1]]()
    except Exception as exc:
        print(f"FAIL: {type(exc).__name__}: {exc}", file=sys.stderr, flush=True)
        raise
