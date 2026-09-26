"""Local one-off V130-ROCKET-01 setup/baseline; reuses unmodified smoke helpers."""
from __future__ import annotations
import json
import platform
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
REPO = Path("D:/GitHub/AdvancedRocketry-Community")
sys.path.insert(0, str(REPO / "scripts"))
import run_dedicated_server_smoke as smoke

BASE = Path(__file__).resolve().parent
SERVER = BASE / "server"
LIBRARIES = Path("D:/GitHub/arce-v120-mig03b-rolling/build/rolling-fixture-server/libraries")
VERSION = "1.20.1-1.3.0-dev"
JAVA = "C:/Program Files/Java/jdk-17.0.7/bin/java.exe"
ARTIFACT = REPO / "build/libs" / f"advancedrocketry-community-{VERSION}.jar"

def write_json(path, value):
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, sort_keys=True, indent=2) + "\n")

def prepare():
    SERVER.mkdir()
    source_files = list(LIBRARIES.rglob("*"))
    if any(smoke.is_link_or_junction(path) for path in [LIBRARIES, *source_files]):
        raise smoke.SmokeError("Runtime contains links or junctions")
    shutil.copytree(LIBRARIES, SERVER / "libraries")
    manifest = []
    for path in sorted(source_files):
        if path.is_file():
            relative = path.relative_to(LIBRARIES)
            digest = smoke.digest_file(path)
            target = SERVER / "libraries" / relative
            if smoke.digest_file(target) != digest:
                raise smoke.SmokeError(f"Runtime copy differs: {relative}")
            manifest.append({"path": relative.as_posix(), "size": path.stat().st_size, "sha256": digest})
    write_json(BASE / "runtime-copy.json", {"source": str(LIBRARIES), "files": manifest,
        "world_mods_config_copied": False, "network_installer_used": False})
    print(f"Prepared {len(manifest)} runtime files at {SERVER}", flush=True)

def baseline():
    started = datetime.now(timezone.utc).isoformat()
    java, java_version = smoke.resolve_java(JAVA)
    if not ARTIFACT.is_file() or smoke.is_link_or_junction(ARTIFACT):
        raise smoke.SmokeError("Primary artifact missing or linked")
    artifact_hash = smoke.digest_file(ARTIFACT)
    (SERVER / "mods").mkdir()
    copied = SERVER / "mods" / ARTIFACT.name
    shutil.copy2(ARTIFACT, copied)
    if smoke.digest_file(copied) != artifact_hash:
        raise smoke.SmokeError("Artifact copy differs")
    port = smoke.allocate_port()
    session_id = smoke.build_session_id(artifact_hash, started, port)
    properties_hash = smoke.write_server_configuration(SERVER, port, True)
    smoke.verify_active_server_properties((SERVER / "server.properties").read_bytes(), port)
    print(f"Artifact {artifact_hash}; bind 127.0.0.1:{port}; offline=true", flush=True)
    first = smoke.run_cycle("first-start", session_id + "-first-start", java, SERVER, port, 240,
        expected_mod_version=VERSION)
    world_identity = smoke.establish_world_identity(SERVER, session_id, artifact_hash, properties_hash)
    print("First start/status/save/stop completed", flush=True)
    restart = smoke.run_cycle("restart", session_id + "-restart", java, SERVER, port, 240,
        expected_mod_version=VERSION)
    world_identity = smoke.complete_world_identity(SERVER, world_identity)
    smoke.verify_active_server_properties((SERVER / "server.properties").read_bytes(), port)
    cycles = [first, restart]
    cycle_docs = []
    for cycle in cycles:
        doc = {key: value for key, value in vars(cycle).items() if key not in ("lines", "status")}
        doc.update(smoke.log_audit_counts(cycle.lines))
        doc.update({"mod_marker": smoke.forge_mod_versions(cycle.status).get(smoke.MOD_ID),
            "status_protocol": cycle.status["version"]["protocol"], "status_version": cycle.status["version"]["name"]})
        cycle_docs.append(doc)
    summary = {"schema_version": 2, "session_id": session_id, "artifact": ARTIFACT.name,
        "artifact_sha256": artifact_hash, "server_artifact_sha256": smoke.digest_file(copied),
        "completed_at": datetime.now(timezone.utc).isoformat(), "started_at": started, "cycles": cycle_docs,
        "forge": smoke.FORGE_VERSION, "minecraft": smoke.MINECRAFT_VERSION, "java": java_version,
        "mod_version": VERSION, "manual_player_cycles": False, "offline_mode": True,
        "platform": platform.platform(), "server_bind": "127.0.0.1", "server_port": port,
        "world": world_identity, "world_level_dat": True, "runtime_source": str(LIBRARIES),
        "runtime_copy_manifest_sha256": smoke.digest_file(BASE / "runtime-copy.json"),
        "network_installer_used": False, "scope": "local fresh-world headless startup/restart only"}
    smoke.write_evidence(BASE / "baseline-evidence", summary, cycles)
    print("Same-world restart/status/save/stop completed", flush=True)

def postcheck():
    from inspect_v100_world_upgrade import read_nbt
    path = SERVER / "world/data/advancedrocketrycommunity_rocket_transactions.dat"
    actual = read_nbt(path)
    if actual.get("schema_version") != 2 or actual.get("transactions") != []:
        raise smoke.SmokeError(f"Unexpected recovered journal content: {actual!r}")
    baseline_summary = json.loads((BASE / "baseline-evidence/summary.json").read_text(encoding="utf-8"))
    rocket = json.loads((BASE / "rocket-evidence/summary.json").read_text(encoding="utf-8"))
    entity_data = rocket["assembled"]["entity_data"]
    observed_schemas = {}
    for key, pattern, expected in (
        ("entity", r"^\{schema_version: (\d+),", 2),
        ("flight", r"flight_data: \{schema_version: (\d+),", 2),
        ("snapshot", r"snapshot: \{schema_version: (\d+),", 1),
        ("travel_target", r"current_target: \{schema_version: (\d+),", 1),
    ):
        match = re.search(pattern, entity_data)
        if match is None or int(match.group(1)) != expected:
            raise smoke.SmokeError(f"Unexpected observed {key} schema")
        observed_schemas[key] = int(match.group(1))
    if entity_data != rocket["persisted"]["entity_data"]:
        raise smoke.SmokeError("Entity authority SNBT differs across restart")
    if 'adapter: "advancedrocketrycommunity:vanilla_container_v1"' not in entity_data:
        raise smoke.SmokeError("Vanilla adapter payload absent")
    artifact = SERVER / "mods" / ARTIFACT.name
    if smoke.digest_file(artifact) != baseline_summary["artifact_sha256"]:
        raise smoke.SmokeError("Server artifact changed")
    smoke.verify_active_server_properties((SERVER / "server.properties").read_bytes(), baseline_summary["server_port"])
    shutil.copy2(path, BASE / "recovered-rocket-transactions.dat")
    report = {"DataVersion": 3465, "journal": actual, "journal_sha256": smoke.digest_file(path),
        "observed_authority_schemas": observed_schemas, "entity_authority_snbt_restart_equal": True,
        "vanilla_adapter_id": "advancedrocketrycommunity:vanilla_container_v1",
        "artifact_sha256": smoke.digest_file(artifact), "journal_empty_verified": True,
        "read_only_inspector": "scripts/inspect_v100_world_upgrade.py:read_nbt",
        "scope": "post-clean-stop journal schema and empty transactions; not power-loss durability"}
    write_json(BASE / "journal-postcheck.json", report)
    print(json.dumps(report, sort_keys=True), flush=True)

if __name__ == "__main__":
    try:
        {"prepare": prepare, "baseline": baseline, "postcheck": postcheck}[sys.argv[1]]()
    except Exception as exc:
        print(f"[FAIL] {type(exc).__name__}: {exc}", file=sys.stderr, flush=True)
        raise
