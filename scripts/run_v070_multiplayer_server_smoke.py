#!/usr/bin/env python3
"""Bind two Forge userdev clients to one packaged v0.7 dedicated server."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import (
        JOIN_MARKER,
        LEAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
    )
    from .run_v060_flight_server_smoke import _load_summary, _verify_inputs
    from .run_v070_station_server_smoke import (
        STATION_TRANSACTION_LOG,
        StationHarness,
        _delete_station,
        _station_from_match,
    )
else:
    from run_dedicated_server_smoke import (
        JOIN_MARKER,
        LEAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
    )
    from run_v060_flight_server_smoke import _load_summary, _verify_inputs
    from run_v070_station_server_smoke import (
        STATION_TRANSACTION_LOG,
        StationHarness,
        _delete_station,
        _station_from_match,
    )


EXPECTED_VERSION = "1.20.1-0.7.0-dev"
CLIENT_NAMES = ("ClientA", "PilotB")
CLIENT_LOG_MARKERS = (
    "Setting user:",
    "Advanced Rocketry: Community Edition",
    "Connecting to 127.0.0.1",
    "Connected to a modded server.",
    "ARCE_G4_SHARED_STATE",
    "Stopping!",
)
FORBIDDEN_CLIENT_MARKERS = (
    "NoClassDefFoundError",
    "ClassNotFoundException: net.minecraft.client",
    "Attempted to load class net/minecraft/client",
)
SERVER_LOG_MARKERS = (
    "Advanced Rocketry: Community Edition",
    "joined the game",
    "left the game",
    "ARCE_STATION_",
    "ARCE_G4_SHARED_STATE",
    "Saved the game",
)


def offline_uuid(name: str) -> uuid.UUID:
    digest = hashlib.md5(f"OfflinePlayer:{name}".encode("utf-8")).digest()
    return uuid.UUID(bytes=digest, version=3)


def _wait_for_client_marker(paths: list[Path], marker: str, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if all(path.is_file() and marker in path.read_text(
                encoding="utf-8", errors="replace") for path in paths):
            return
        time.sleep(0.25)
    raise SmokeError(f"Both client logs did not receive marker within {timeout} seconds")


def _filter_client_log(path: Path, name: str, marker: str) -> tuple[list[str], dict[str, object]]:
    if is_link_or_junction(path) or not path.is_file() or path.stat().st_size > 16 * 1024 * 1024:
        raise SmokeError(f"Client log is missing, unsafe, or too large: {path}")
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    text = "\n".join(lines)
    required = (
        f"Setting user: {name}",
        f"Advanced Rocketry: Community Edition {EXPECTED_VERSION} initialized",
        "Connected to a modded server.",
        marker,
        "Stopping!",
    )
    if any(value not in text for value in required):
        raise SmokeError(f"Client {name} did not complete the bounded G4 lifecycle")
    if any(value in text for value in FORBIDDEN_CLIENT_MARKERS):
        raise SmokeError(f"Client {name} logged a linkage failure")
    filtered = [line.rstrip() for line in lines if any(value in line for value in CLIENT_LOG_MARKERS)]
    return filtered, {
        "username": name,
        "kind": "forge_userdev",
        "connected_to_modded_server": True,
        "received_shared_marker": True,
        "clean_shutdown": True,
        "full_log_sha256": digest_file(path),
    }


def _filter_server_log(lines: list[str]) -> list[str]:
    return [
        line.rstrip()
        for line in lines
        if any(value in line for value in SERVER_LOG_MARKERS)
    ]


def _create_client_station(process, name: str, orbit: str) -> dict[str, object]:  # type: ignore[no-untyped-def]
    owner = str(offline_uuid(name))
    start = len(process.lines)
    process.command(f"arce station admin create {owner} {orbit} {name}-G4")
    index = process.wait_for(STATION_TRANSACTION_LOG, 45.0, start_at=start)
    match = STATION_TRANSACTION_LOG.search(process.lines[index])
    if match is None:
        raise SmokeError(f"Could not parse {name} station creation receipt")
    station = _station_from_match(match)
    if station["owner_id"] != owner:
        raise SmokeError(f"{name} station changed the offline player authority")
    station["orbit"] = orbit
    return station


def _write_evidence(
    directory: Path,
    summary: dict[str, object],
    server_lines: list[str],
    clients: list[list[str]],
) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite v0.7 multiplayer evidence: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    documents = {
        "server.txt": server_lines,
        "client-a.txt": clients[0],
        "client-b.txt": clients[1],
    }
    for name, lines in documents.items():
        (directory / name).write_text(
            "\n".join(lines) + "\n",
            encoding="utf-8",
            newline="\n",
        )


def parse_args() -> argparse.Namespace:
    java_home = os.environ.get("JAVA_HOME")
    default_java = str(Path(java_home) / "bin" / "java") if java_home else "java"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    parser.add_argument("--baseline-summary", type=Path, required=True)
    parser.add_argument("--client-a-log", type=Path, required=True)
    parser.add_argument("--client-b-log", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--tested-commit", required=True)
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--expected-version", default=EXPECTED_VERSION)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    parser.add_argument("--player-timeout", type=float, default=360.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    process = None
    try:
        if re.fullmatch(r"[0-9a-f]{40}", args.tested_commit) is None:
            raise SmokeError("Tested commit must be a full lowercase Git SHA-1")
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(server, baseline, args.expected_version)
        java, java_version = resolve_java(args.java)
        harness = StationHarness(
            java=java,
            server=server,
            port=port,
            expected_version=args.expected_version,
            startup_timeout=args.startup_timeout,
        )
        process = harness.start("v070-two-client")
        joined: list[str] = []
        scan_at = 0
        while len(joined) < len(CLIENT_NAMES):
            index = process.wait_for(JOIN_MARKER, args.player_timeout, start_at=scan_at)
            match = JOIN_MARKER.search(process.lines[index])
            if match is not None and match.group("player") not in joined:
                joined.append(match.group("player"))
            scan_at = index + 1
        if set(joined) != set(CLIENT_NAMES):
            raise SmokeError(f"Unexpected packaged clients joined: {joined}")

        stations = [
            _create_client_station(process, CLIENT_NAMES[0], "earth"),
            _create_client_station(process, CLIENT_NAMES[1], "moon"),
        ]
        marker = (
            "ARCE_G4_SHARED_STATE players=2 stations=2 artifact="
            + artifact_sha256[:16]
        )
        marker_start = len(process.lines)
        process.command("list")
        process.command(f"say {marker}")
        process.wait_for(re.compile(re.escape(marker)), 30.0, start_at=marker_start)
        client_logs = [args.client_a_log.resolve(), args.client_b_log.resolve()]
        _wait_for_client_marker(client_logs, marker, 90.0)
        print("[READY_TO_CLOSE_CLIENTS] Both clients received the shared marker", flush=True)

        departed: list[str] = []
        scan_at = marker_start
        while len(departed) < len(CLIENT_NAMES):
            index = process.wait_for(LEAVE_MARKER, args.player_timeout, start_at=scan_at)
            match = LEAVE_MARKER.search(process.lines[index])
            if match is not None and match.group("player") not in departed:
                departed.append(match.group("player"))
            scan_at = index + 1
        if set(departed) != set(CLIENT_NAMES):
            raise SmokeError(f"Unexpected packaged clients departed: {departed}")
        _wait_for_client_marker(client_logs, "Stopping!", 90.0)

        for station in stations:
            _delete_station(process, str(station["station_id"]))
        harness.stop(process)
        server_lines = list(process.lines)
        process = None

        client_documents: list[dict[str, object]] = []
        filtered_clients: list[list[str]] = []
        for path, name in zip(client_logs, CLIENT_NAMES, strict=True):
            filtered, document = _filter_client_log(path, name, marker)
            filtered_clients.append(filtered)
            client_documents.append(document)
        filtered_server = _filter_server_log(server_lines)
        summary = {
            "schema_version": 1,
            "version": "v0.7.0",
            "build": args.expected_version,
            "artifact_sha256": artifact_sha256,
            "tested_implementation_commit": args.tested_commit,
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "server": {
                "kind": "packaged_forge_server",
                "loopback_only": True,
                "port": port,
                "clean_save_and_stop": True,
            },
            "clients": client_documents,
            "observations": {
                "simultaneous_players": 2,
                "same_server_marker_received_by_both": True,
                "two_player_station_owners": True,
                "station_count": 2,
                "station_ids": [station["station_id"] for station in stations],
                "client_linkage_failures": 0,
            },
            "shared_marker": marker,
            "java": java_version,
            "limitations": [
                "Clients are Forge user-development launches from the exact tested source; the server uses the packaged JAR.",
                "No screenshot or video is claimed by this G4 record; owner G8/G9 acceptance is separate.",
            ],
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary, filtered_server, filtered_clients)
        print("[PASS] Two simultaneous Forge clients joined the packaged server")
        print("[PASS] Both clients received the same two-station authority marker")
        print("[PASS] Both clients disconnected and stopped cleanly")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        if process is not None:
            process.abort()
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
