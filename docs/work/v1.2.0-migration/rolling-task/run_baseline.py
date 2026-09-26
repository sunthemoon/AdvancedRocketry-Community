"""Create a fresh loopback world using an existing local Forge runtime, then restart it."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path
import shutil
import socket
import sys

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "scripts"))
import run_dedicated_server_smoke as smoke


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runtime", type=Path)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--java", required=True)
    args = parser.parse_args()
    server = ROOT / "build/rolling-fixture-server"
    evidence = ROOT / "docs/work/v1.2.0-migration/rolling-restart/baseline"
    for path in (args.runtime, args.artifact, server, evidence):
        if any(smoke.is_link_or_junction(parent) for parent in (path, *path.parents)):
            raise smoke.SmokeError(f"Linked path rejected: {path}")
    if server.exists() or evidence.exists():
        raise smoke.SmokeError("Refusing to overwrite baseline world or evidence")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 56062))
    artifact_hash = smoke.digest_file(args.artifact)
    if artifact_hash != "6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a":
        raise smoke.SmokeError("Candidate artifact hash differs from assigned implementation")
    java, java_version = smoke.resolve_java(args.java)
    libraries = args.runtime / "libraries"
    for path in libraries.rglob("*"):
        if smoke.is_link_or_junction(path):
            raise smoke.SmokeError(f"Linked runtime file rejected: {path}")
    shutil.copytree(libraries, server / "libraries")
    (server / "mods").mkdir()
    shutil.copy2(args.artifact, server / "mods" / args.artifact.name)
    if smoke.digest_file(server / "mods" / args.artifact.name) != artifact_hash:
        raise smoke.SmokeError("Copied artifact hash changed")
    properties_hash = smoke.write_server_configuration(server, 56062, True)
    started = datetime.now(timezone.utc).isoformat()
    session_id = smoke.build_session_id(artifact_hash, started, 56062)
    version = "1.20.1-1.1.1-dev"
    first = smoke.run_cycle("first-start", session_id + "-first", java, server, 56062,
                            240, expected_mod_version=version)
    identity = smoke.establish_world_identity(server, session_id, artifact_hash, properties_hash)
    restart = smoke.run_cycle("restart", session_id + "-restart", java, server, 56062,
                              240, expected_mod_version=version)
    identity = smoke.complete_world_identity(server, identity)
    cycles = [first, restart]
    summary = {
        "schema_version": 2,
        "scenario": "fresh_world_from_existing_local_forge_runtime",
        "runtime_source": "prec04b-removal-legacy-20260926/libraries",
        "installer_executed": False,
        "artifact": args.artifact.name, "artifact_sha256": artifact_hash,
        "mod_version": version, "server_port": 56062, "server_bind": "127.0.0.1",
        "offline_mode": True, "java": java_version, "session_id": session_id,
        "started_at": started, "completed_at": datetime.now(timezone.utc).isoformat(),
        "world": identity,
        "cycles": [{"name": c.name, "exit_code": c.exit_code,
                    "full_log_file": c.full_log_file, "full_log_sha256": c.full_log_sha256,
                    "mod_marker": smoke.forge_mod_versions(c.status).get(smoke.MOD_ID)}
                   for c in cycles],
    }
    smoke.write_evidence(evidence, summary, cycles)
    print("[PASS] Fresh world, status query, save, clean stop and same-world restart")
    print(f"[PASS] Artifact SHA-256: {artifact_hash}")
    print(f"[PASS] Baseline evidence: {evidence}")


if __name__ == "__main__":
    main()
