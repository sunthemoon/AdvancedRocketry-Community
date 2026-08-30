#!/usr/bin/env python3
"""Run and attest the fresh third server cycle used by v0.0.2 manual evidence.

The matching-client harness owns the disposable server.  This helper starts that
same server exactly once more while a missing-project-mod client is tested, then
creates a non-overwriting runtime-log snapshot and a machine-generated receipt.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import secrets
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

if __package__:
    from .run_dedicated_server_smoke import (
        ARTIFACT_NAME,
        FORGE_COORDINATE,
        MANUAL_PLAYER_SUMMARY_SCHEMA_VERSION,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        verify_active_server_properties,
    )
else:
    # Isolated script execution omits this directory from sys.path. Add only
    # the already-selected repository scripts directory after stdlib imports.
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from run_dedicated_server_smoke import (
        ARTIFACT_NAME,
        FORGE_COORDINATE,
        MANUAL_PLAYER_SUMMARY_SCHEMA_VERSION,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        verify_active_server_properties,
    )


ROOT = Path(__file__).resolve().parents[1]
BUILD_ROOT = ROOT / "build"
RECEIPT_SCHEMA_VERSION = 2
MAX_JSON_BYTES = 1024 * 1024
MAX_LOG_BYTES = 32 * 1024 * 1024
MAX_SERVER_MODS_ENTRIES = 16
SESSION_ID_RE = re.compile(r"v002-[0-9a-f]{24}")


def _read_bounded(path: Path, maximum: int, label: str) -> bytes:
    if is_link_or_junction(path) or not path.is_file():
        raise SmokeError(f"{label} is missing or unsafe: {path}")
    size = path.stat().st_size
    if size > maximum:
        raise SmokeError(f"{label} exceeds {maximum} bytes: {path}")
    return path.read_bytes()


def _safe_file_below(path: Path, root: Path, label: str) -> Path:
    root_lexical = Path(os.path.abspath(root))
    candidate = Path(os.path.abspath(path))
    try:
        candidate.relative_to(root_lexical)
    except ValueError as exc:
        raise SmokeError(f"{label} escapes the retained server directory") from exc
    cursor = candidate
    while cursor != root_lexical:
        if cursor.exists() and is_link_or_junction(cursor):
            raise SmokeError(f"{label} contains a link or junction: {cursor}")
        cursor = cursor.parent
    if not candidate.is_file():
        raise SmokeError(f"{label} is missing: {candidate}")
    try:
        candidate.resolve(strict=True).relative_to(root_lexical.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise SmokeError(f"{label} escapes the retained server directory") from exc
    return candidate


def inspect_server_mods(server: Path, expected_sha256: str) -> list[dict[str, str]]:
    """Require the disposable server's mods directory to contain only this mod."""

    mods = server / "mods"
    if is_link_or_junction(mods) or not mods.is_dir():
        raise SmokeError(f"server mods directory is missing or unsafe: {mods}")
    try:
        mods.resolve(strict=True).relative_to(server.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise SmokeError("server mods directory escapes the retained server") from exc
    entries: list[Path] = []
    with os.scandir(mods) as stream:
        for entry in stream:
            entries.append(Path(entry.path))
            if len(entries) > MAX_SERVER_MODS_ENTRIES:
                raise SmokeError(
                    "server mods directory exceeds the bounded inventory limit"
                )
    if len(entries) != 1 or entries[0].name != ARTIFACT_NAME:
        names = sorted(item.name for item in entries)
        raise SmokeError(
            "server mods directory must contain only the project JAR; found: "
            + (", ".join(names) if names else "<empty>")
        )
    artifact = _safe_file_below(entries[0], server, "server mods inventory entry")
    observed_hash = digest_file(artifact)
    if observed_hash != expected_sha256:
        raise SmokeError("server mods inventory project JAR hash differs from the harness")
    return [{"filename": ARTIFACT_NAME, "sha256": observed_hash}]


def _resolve_under_build(path: Path, *, must_exist: bool) -> Path:
    build_lexical = Path(os.path.abspath(BUILD_ROOT))
    if is_link_or_junction(build_lexical) or not build_lexical.is_dir():
        raise SmokeError(f"build root is missing or unsafe: {BUILD_ROOT}")
    candidate = path if path.is_absolute() else ROOT / path
    candidate_lexical = Path(os.path.abspath(candidate))
    try:
        candidate_lexical.relative_to(build_lexical)
    except ValueError as exc:
        raise SmokeError(f"evidence path must remain below {BUILD_ROOT}: {path}") from exc
    cursor = candidate_lexical if must_exist else candidate_lexical.parent
    while cursor != build_lexical:
        if cursor.exists() and is_link_or_junction(cursor):
            raise SmokeError(f"evidence path contains a link or junction: {cursor}")
        cursor = cursor.parent

    build = build_lexical.resolve(strict=True)
    resolved = candidate.resolve(strict=must_exist)
    try:
        resolved.relative_to(build)
    except ValueError as exc:
        raise SmokeError(f"evidence path must remain below {BUILD_ROOT}: {path}") from exc
    return resolved


def _parse_aware_timestamp(value: Any, label: str) -> datetime:
    if not isinstance(value, str):
        raise SmokeError(f"{label} must be an aware ISO timestamp")
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError as exc:
        raise SmokeError(f"{label} must be an aware ISO timestamp") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise SmokeError(f"{label} must be an aware ISO timestamp")
    return parsed


def _load_harness_summary(path: Path, server: Path) -> tuple[dict[str, Any], bytes]:
    payload = _read_bounded(path, MAX_JSON_BYTES, "manual player-cycle summary")
    try:
        document = json.loads(payload.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise SmokeError("manual player-cycle summary is not valid UTF-8 JSON") from exc
    if not isinstance(document, dict):
        raise SmokeError("manual player-cycle summary must be a JSON object")
    if document.get("schema_version") != MANUAL_PLAYER_SUMMARY_SCHEMA_VERSION:
        raise SmokeError(
            "manual player-cycle summary schema_version must be "
            f"{MANUAL_PLAYER_SUMMARY_SCHEMA_VERSION}"
        )
    session_id = document.get("session_id")
    if not isinstance(session_id, str) or SESSION_ID_RE.fullmatch(session_id) is None:
        raise SmokeError("manual player-cycle summary has an invalid session_id")
    if document.get("manual_player_cycles") is not True or document.get(
        "same_player_verified"
    ) is not True:
        raise SmokeError("summary does not attest completed matching-player cycles")
    if document.get("offline_mode") is not True:
        raise SmokeError("third-cycle protocol requires the isolated offline-mode server")
    _parse_aware_timestamp(document.get("completed_at"), "summary completed_at")
    port = document.get("server_port")
    if not isinstance(port, int) or isinstance(port, bool) or not 1 <= port <= 65535:
        raise SmokeError("manual player-cycle summary has an invalid server_port")

    artifact = _safe_file_below(
        server / "mods" / ARTIFACT_NAME, server, "server artifact"
    )
    artifact_hash = digest_file(artifact)
    if artifact_hash != document.get("server_artifact_sha256"):
        raise SmokeError("server artifact differs from the matching-player harness")

    cycles = document.get("cycles")
    if not isinstance(cycles, list) or len(cycles) != 2:
        raise SmokeError("manual player-cycle summary must contain two cycles")
    expected_names = ("first-start", "restart")
    seen_paths: list[Path] = []
    for cycle, expected_name in zip(cycles, expected_names, strict=True):
        if not isinstance(cycle, dict) or cycle.get("name") != expected_name:
            raise SmokeError("manual player-cycle summary cycle ordering is invalid")
        if cycle.get("exit_code") != 0:
            raise SmokeError(f"harness cycle {expected_name} did not exit cleanly")
        log_name = cycle.get("full_log_file")
        if log_name != f"{expected_name}-full.txt":
            raise SmokeError(f"harness cycle {expected_name} full-log name is invalid")
        cycle_log = _safe_file_below(
            server / log_name, server, f"harness cycle log {expected_name}"
        )
        if digest_file(cycle_log) != cycle.get("full_log_sha256"):
            raise SmokeError(f"harness cycle log hash differs from summary: {expected_name}")
        if any(os.path.samefile(cycle_log, previous) for previous in seen_paths):
            raise SmokeError("harness cycles reuse one physical full-log file")
        seen_paths.append(cycle_log)
    return document, payload


def _cycle_log_hashes(summary: dict[str, Any]) -> dict[str, str]:
    return {
        str(cycle["name"]): str(cycle["full_log_sha256"])
        for cycle in summary["cycles"]
    }


def _write_exclusive(path: Path, payload: bytes, label: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with path.open("xb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
    except FileExistsError as exc:
        raise SmokeError(f"refusing to overwrite existing {label}: {path}") from exc


def build_receipt(
    *,
    summary: dict[str, Any],
    summary_payload: bytes,
    run_id: str,
    java_version: str,
    started_at: str,
    completed_at: str,
    duration_millis: int,
    exit_code: int,
    previous_runtime_log_sha256: str,
    full_log_sha256: str,
    active_server_properties_sha256: str,
    critical_server_properties: dict[str, str],
    server_mods_files: list[dict[str, str]],
) -> dict[str, Any]:
    return {
        "schema_version": RECEIPT_SCHEMA_VERSION,
        "run_id": run_id,
        "session_id": summary["session_id"],
        "harness_summary_sha256": hashlib.sha256(summary_payload).hexdigest(),
        "harness_cycle_log_sha256": _cycle_log_hashes(summary),
        "started_at": started_at,
        "completed_at": completed_at,
        "duration_millis": duration_millis,
        "java_version": java_version,
        "exit_code": exit_code,
        "previous_runtime_log_sha256": previous_runtime_log_sha256,
        "full_log_sha256": full_log_sha256,
        "server_artifact_sha256": summary["server_artifact_sha256"],
        "active_server_properties_sha256": active_server_properties_sha256,
        "critical_server_properties": critical_server_properties,
        "server_mods_files": server_mods_files,
    }


def run_mismatch_cycle(
    *,
    server: Path,
    summary_path: Path,
    java: str,
    log_output: Path,
    receipt_output: Path,
) -> dict[str, Any]:
    server = _resolve_under_build(server, must_exist=True)
    summary_path = _resolve_under_build(summary_path, must_exist=True)
    log_output = _resolve_under_build(log_output, must_exist=False)
    receipt_output = _resolve_under_build(receipt_output, must_exist=False)
    if not server.is_dir() or is_link_or_junction(server):
        raise SmokeError(f"server directory is missing or unsafe: {server}")
    if log_output == receipt_output:
        raise SmokeError("log and receipt outputs must be different paths")
    if log_output.exists() or receipt_output.exists():
        existing = log_output if log_output.exists() else receipt_output
        raise SmokeError(f"refusing to overwrite third-cycle evidence: {existing}")

    summary, summary_payload = _load_harness_summary(summary_path, server)
    port = int(summary["server_port"])
    server_mods_before = inspect_server_mods(
        server, str(summary["server_artifact_sha256"])
    )
    java_executable, java_version = resolve_java(java)
    active_properties_path = _safe_file_below(
        server / "server.properties", server, "active server.properties"
    )
    active_properties_before = _read_bounded(
        active_properties_path, MAX_JSON_BYTES, "active server.properties"
    )
    verify_active_server_properties(active_properties_before, port)

    runtime_log = _safe_file_below(
        server / "logs" / "latest.log", server, "runtime latest.log"
    )
    previous_payload = _read_bounded(runtime_log, MAX_LOG_BYTES, "previous runtime latest.log")
    previous_hash = hashlib.sha256(previous_payload).hexdigest()
    cycle_hashes = _cycle_log_hashes(summary)
    cycle_paths = [server / f"{name}-full.txt" for name in cycle_hashes]
    if any(os.path.samefile(runtime_log, cycle_path) for cycle_path in cycle_paths):
        raise SmokeError("runtime latest.log reuses a harness-cycle full log")

    args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    args_file = (
        server
        / "libraries"
        / "net"
        / "minecraftforge"
        / "forge"
        / FORGE_COORDINATE
        / args_name
    )
    args_file = _safe_file_below(args_file, server, "Forge server arguments file")
    command = [
        java_executable,
        "-Xms512M",
        "-Xmx1024M",
        "-Djava.net.preferIPv4Stack=true",
        f"@{args_file.relative_to(server).as_posix()}",
        "nogui",
    ]
    run_id = "v002-mismatch-" + secrets.token_hex(12)
    started = datetime.now(timezone.utc)
    monotonic_started = time.monotonic()
    completed = subprocess.run(command, cwd=server, check=False)
    duration_millis = round((time.monotonic() - monotonic_started) * 1000)
    ended = datetime.now(timezone.utc)

    fresh_payload = _read_bounded(runtime_log, MAX_LOG_BYTES, "third-cycle runtime latest.log")
    fresh_hash = hashlib.sha256(fresh_payload).hexdigest()
    if fresh_hash == previous_hash:
        raise SmokeError("third startup did not create fresh runtime log content")
    if fresh_hash in set(cycle_hashes.values()) or any(
        os.path.samefile(runtime_log, cycle_path) for cycle_path in cycle_paths
    ):
        raise SmokeError("third-cycle runtime log reuses a harness-cycle full log")

    active_properties_after = _read_bounded(
        active_properties_path, MAX_JSON_BYTES, "active server.properties"
    )
    critical_properties = verify_active_server_properties(active_properties_after, port)
    active_properties_hash = hashlib.sha256(active_properties_after).hexdigest()
    server_mods_after = inspect_server_mods(
        server, str(summary["server_artifact_sha256"])
    )
    if server_mods_after != server_mods_before:
        raise SmokeError("server mods inventory changed during the third cycle")
    receipt = build_receipt(
        summary=summary,
        summary_payload=summary_payload,
        run_id=run_id,
        java_version=java_version,
        started_at=started.isoformat(),
        completed_at=ended.isoformat(),
        duration_millis=duration_millis,
        exit_code=completed.returncode,
        previous_runtime_log_sha256=previous_hash,
        full_log_sha256=fresh_hash,
        active_server_properties_sha256=active_properties_hash,
        critical_server_properties=critical_properties,
        server_mods_files=server_mods_after,
    )
    receipt_payload = (
        json.dumps(receipt, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")
    _write_exclusive(log_output, fresh_payload, "third-cycle log")
    _write_exclusive(receipt_output, receipt_payload, "third-cycle receipt")
    if completed.returncode != 0:
        raise SmokeError(
            f"third-cycle server exited with code {completed.returncode}; evidence was retained"
        )
    return receipt


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--java", default=os.environ.get("JAVA", "java"))
    parser.add_argument("--log-output", type=Path, required=True)
    parser.add_argument("--receipt-output", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        receipt = run_mismatch_cycle(
            server=args.server_dir,
            summary_path=args.summary,
            java=args.java,
            log_output=args.log_output,
            receipt_output=args.receipt_output,
        )
    except (OSError, SmokeError, subprocess.SubprocessError, ValueError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print(f"[PASS] Third-cycle run ID: {receipt['run_id']}")
    print(f"[PASS] Third-cycle server exit code: {receipt['exit_code']}")
    print(f"[PASS] Third-cycle log SHA-256: {receipt['full_log_sha256']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
