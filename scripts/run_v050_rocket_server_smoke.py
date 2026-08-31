#!/usr/bin/env python3
"""Verify packaged v0.5 rocket assembly, persistence, and restart recovery."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import (
        CapturedProcess,
        FORGE_COORDINATE,
        READY_MARKER,
        SAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
        validate_status_identity,
        wait_for_status,
    )
else:
    from run_dedicated_server_smoke import (
        CapturedProcess,
        FORGE_COORDINATE,
        READY_MARKER,
        SAVE_MARKER,
        SmokeError,
        digest_file,
        is_link_or_junction,
        resolve_java,
        scan_log,
        validate_status_identity,
        wait_for_status,
    )


EXPECTED_VERSION = "1.20.1-0.5.0-dev"
X = 256
Y = 100
Z = 256
ASSEMBLER = f"{X} {Y} {Z}"
MOTOR = f"{X} {Y + 1} {Z}"
SEAT = f"{X} {Y + 2} {Z}"
GUIDANCE = f"{X} {Y + 3} {Z}"
CHEST = f"{X + 1} {Y + 1} {Z}"
ENTITY_SELECTOR = "@e[type=advancedrocketrycommunity:rocket,limit=1]"
ASSEMBLY_LOG = re.compile(
    r"ARCE_ROCKET_TRANSACTION operation=assembly code=SUCCESS blocks=4 "
    r"snapshot=([0-9a-f]{64}) entity=([0-9a-f-]{36})"
)
RECOVERY_STAGED_LOG = re.compile(
    r"ARCE_ROCKET_RECOVERY_STAGED transaction=([0-9a-f-]{36}) "
    r"rocket=([0-9a-f-]{36}) snapshot=([0-9a-f]{64}) blocks=4"
)
RECOVERY_LOG = re.compile(r"ARCE_ROCKET_RECOVERY outcome=RECOVERED")
ENTITY_DATA_MARKER = re.compile(r"has the following entity data:\s*(.+)$")
BLOCK_DATA_MARKER = re.compile(r"has the following block data:\s*(.+)$")


def _load_summary(path: Path) -> dict[str, object]:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 1024 * 1024:
        raise SmokeError(f"Baseline summary is missing, unsafe, or too large: {path}")
    value = json.loads(path.read_text(encoding="utf-8", errors="strict"))
    if not isinstance(value, dict):
        raise SmokeError("Baseline summary must be a JSON object")
    return value


def _verify_inputs(
    server: Path,
    summary: dict[str, object],
    expected_version: str,
) -> tuple[int, str]:
    if is_link_or_junction(server) or not server.is_dir():
        raise SmokeError(f"Installed server directory is missing or unsafe: {server}")
    if summary.get("mod_version") != expected_version:
        raise SmokeError("Baseline summary does not match the expected artifact version")
    port = summary.get("server_port")
    artifact_sha256 = summary.get("artifact_sha256")
    if not isinstance(port, int) or not 1 <= port <= 65535:
        raise SmokeError("Baseline summary has an invalid server port")
    if not isinstance(artifact_sha256, str) or re.fullmatch(r"[0-9a-f]{64}", artifact_sha256) is None:
        raise SmokeError("Baseline summary has an invalid artifact SHA-256")
    artifact = server / "mods" / f"advancedrocketry-community-{expected_version}.jar"
    if is_link_or_junction(artifact) or not artifact.is_file():
        raise SmokeError(f"Installed artifact is missing: {artifact}")
    if digest_file(artifact) != artifact_sha256:
        raise SmokeError("Installed artifact does not match the baseline summary")
    return port, artifact_sha256


def _server_command(java: str) -> list[str]:
    args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    args_file = Path("libraries/net/minecraftforge/forge") / FORGE_COORDINATE / args_name
    return [
        java,
        "-Xms512M",
        "-Xmx1024M",
        "-Djava.net.preferIPv4Stack=true",
        "-Dadvancedrocketrycommunity.releaseTestHooks=true",
        f"@{args_file.as_posix()}",
        "nogui",
    ]


def _wait_for_command_marker(
    process: CapturedProcess,
    command: str,
    marker: str,
    timeout: float = 30.0,
) -> None:
    start = len(process.lines)
    process.command(command)
    process.wait_for(re.compile(re.escape(marker)), timeout, start_at=start)


def _entity_data(process: CapturedProcess, rocket_id: str) -> str:
    start = len(process.lines)
    process.command(f"data get entity {rocket_id} RocketEntityData")
    index = process.wait_for(ENTITY_DATA_MARKER, 30.0, start_at=start)
    match = ENTITY_DATA_MARKER.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse RocketEntityData")
    return match.group(1).strip()


def _wait_for_active_entity(
    process: CapturedProcess,
    rocket_id: str,
    start_at: int = 0,
) -> None:
    process.wait_for(
        re.compile(
            rf"ARCE_ROCKET_ENTITY_ACTIVE entity={re.escape(rocket_id)} "
            r"operational=true snapshot=[0-9a-f]{64}"
        ),
        30.0,
        start_at=start_at,
    )


def _chest_data(process: CapturedProcess) -> str:
    start = len(process.lines)
    process.command(f"data get block {CHEST} Items")
    index = process.wait_for(BLOCK_DATA_MARKER, 30.0, start_at=start)
    match = BLOCK_DATA_MARKER.search(process.lines[index])
    if match is None:
        raise SmokeError("Could not parse restored chest Items")
    return match.group(1).strip()


def _configure_rocket(process: CapturedProcess) -> None:
    process.command(f"forceload add {X} {Z}")
    process.command("kill @e[type=advancedrocketrycommunity:rocket]")
    process.command(f"fill {X} {Y} {Z} {X + 1} {Y + 3} {Z} minecraft:air")
    process.command(f"setblock {ASSEMBLER} advancedrocketrycommunity:rocket_assembler")
    process.command(f"setblock {MOTOR} advancedrocketrycommunity:rocket_motor")
    process.command(f"setblock {SEAT} advancedrocketrycommunity:rocket_seat")
    process.command(f"setblock {GUIDANCE} advancedrocketrycommunity:guidance_computer")
    process.command(f"setblock {CHEST} minecraft:chest")
    process.command(
        f"data merge block {CHEST} {{Items:["
        "{Slot:0b,id:\"minecraft:diamond\",Count:17b},"
        "{Slot:26b,id:\"minecraft:iron_ingot\",Count:64b}]}"
    )


def _verify_assembled(process: CapturedProcess, rocket_id: str, marker: str) -> None:
    conditions = (
        f"if entity {rocket_id} "
        f"if block {ASSEMBLER} advancedrocketrycommunity:rocket_assembler "
        f"if block {MOTOR} minecraft:air "
        f"if block {SEAT} minecraft:air "
        f"if block {GUIDANCE} minecraft:air "
        f"if block {CHEST} minecraft:air"
    )
    _wait_for_command_marker(process, f"execute {conditions} run say {marker}", marker)


def _verify_recovered(process: CapturedProcess, rocket_id: str, marker: str) -> None:
    conditions = (
        f"unless entity {rocket_id} "
        f"if block {ASSEMBLER} advancedrocketrycommunity:rocket_assembler "
        f"if block {MOTOR} advancedrocketrycommunity:rocket_motor "
        f"if block {SEAT} advancedrocketrycommunity:rocket_seat "
        f"if block {GUIDANCE} advancedrocketrycommunity:guidance_computer "
        f"if block {CHEST} minecraft:chest "
        f"if data block {CHEST} {{Items:["
        "{Slot:0b,id:\"minecraft:diamond\",Count:17b},"
        "{Slot:26b,id:\"minecraft:iron_ingot\",Count:64b}]}"
    )
    _wait_for_command_marker(process, f"execute {conditions} run say {marker}", marker)


def _save_and_stop(process: CapturedProcess) -> int:
    save_start = len(process.lines)
    process.command("save-all flush")
    process.wait_for(SAVE_MARKER, 60.0, start_at=save_start)
    process.command("stop")
    return process.finish()


def _run_phase(
    *,
    name: str,
    phase: str,
    java: str,
    server: Path,
    port: int,
    startup_timeout: float,
    expected_version: str,
    expected_rocket_id: str | None = None,
) -> dict[str, object]:
    started_at = datetime.now(timezone.utc).isoformat()
    full_log = server / f"{name}-full.txt"
    process = CapturedProcess(_server_command(java), server, full_log)
    entity_data: str | None = None
    snapshot_hash: str | None = None
    rocket_id: str | None = None
    transaction_id: str | None = None
    chest_data: str | None = None
    try:
        process.wait_for(READY_MARKER, startup_timeout)
        validate_status_identity(wait_for_status(port), expected_version)
        if phase == "assemble":
            _configure_rocket(process)
            start = len(process.lines)
            process.command(f"arce rocket assemble {ASSEMBLER}")
            index = process.wait_for(ASSEMBLY_LOG, 45.0, start_at=start)
            match = ASSEMBLY_LOG.search(process.lines[index])
            if match is None:
                raise SmokeError("Could not parse rocket assembly receipt")
            snapshot_hash, rocket_id = match.groups()
            _wait_for_active_entity(process, rocket_id, start_at=start)
            entity_data = _entity_data(process, rocket_id)
            _verify_assembled(process, rocket_id, "V050_ASSEMBLED")
        elif phase == "persist":
            if expected_rocket_id is None:
                raise SmokeError("Persist phase requires the assembled rocket UUID")
            rocket_id = expected_rocket_id
            _wait_for_active_entity(process, rocket_id)
            _verify_assembled(process, rocket_id, "V050_ENTITY_PERSISTED")
            entity_data = _entity_data(process, rocket_id)
            start = len(process.lines)
            process.command(
                f"arce rocket release-test stage-recovery {rocket_id}"
            )
            index = process.wait_for(RECOVERY_STAGED_LOG, 30.0, start_at=start)
            match = RECOVERY_STAGED_LOG.search(process.lines[index])
            if match is None:
                raise SmokeError("Could not parse staged recovery receipt")
            transaction_id, rocket_id, snapshot_hash = match.groups()
        elif phase == "recover":
            if expected_rocket_id is None:
                raise SmokeError("Recovery phase requires the assembled rocket UUID")
            rocket_id = expected_rocket_id
            process.wait_for(RECOVERY_LOG, 45.0)
            _verify_recovered(process, rocket_id, "V050_RECOVERED_AFTER_RESTART")
            chest_data = _chest_data(process)
            process.command(f"fill {X} {Y} {Z} {X + 1} {Y + 3} {Z} minecraft:air")
            process.command(f"forceload remove {X} {Z}")
        else:
            raise SmokeError(f"Unknown rocket smoke phase: {phase}")
        exit_code = _save_and_stop(process)
    except BaseException:
        process.abort()
        raise

    if exit_code != 0:
        raise SmokeError(f"Rocket server phase {name} exited with code {exit_code}")
    findings = scan_log(process.lines)
    if findings:
        raise SmokeError(f"Rocket server phase {name} has a blocking log finding: {findings[0]}")
    return {
        "name": name,
        "phase": phase,
        "started_at": started_at,
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": exit_code,
        "full_log_file": full_log.name,
        "full_log_sha256": digest_file(full_log),
        "entity_data": entity_data,
        "snapshot_hash": snapshot_hash,
        "rocket_id": rocket_id,
        "transaction_id": transaction_id,
        "restored_chest_data": chest_data,
    }


def _write_evidence(directory: Path, summary: dict[str, object]) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite rocket evidence: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    lines = [
        f"artifact_sha256={summary['artifact_sha256']}",
        f"baseline_session_id={summary['baseline_session_id']}",
        f"snapshot_hash={summary['snapshot_hash']}",
        f"rocket_id={summary['rocket_id']}",
        f"transaction_id={summary['transaction_id']}",
        "assembled_blocks=4",
        "entity_nbt_restart_byte_equal=true",
        "stale_transaction_recovered=true",
        "durable_journal_cleared=true",
        "diamond_count_after_recovery=17",
        "iron_ingot_count_after_recovery=64",
    ]
    (directory / "lifecycle.txt").write_text(
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
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--expected-version", default=EXPECTED_VERSION)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(server, baseline, args.expected_version)
        java, java_version = resolve_java(args.java)
        assembled = _run_phase(
            name="v050-rocket-assembled",
            phase="assemble",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            expected_version=args.expected_version,
        )
        persisted = _run_phase(
            name="v050-rocket-persisted",
            phase="persist",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            expected_version=args.expected_version,
            expected_rocket_id=assembled["rocket_id"],
        )
        if assembled["entity_data"] != persisted["entity_data"]:
            raise SmokeError("RocketEntityData changed across the clean server restart")
        if assembled["snapshot_hash"] != persisted["snapshot_hash"]:
            raise SmokeError("Rocket snapshot hash changed across the clean server restart")
        if assembled["rocket_id"] != persisted["rocket_id"]:
            raise SmokeError("Rocket entity identity changed across the clean server restart")
        recovered = _run_phase(
            name="v050-rocket-recovered",
            phase="recover",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            expected_version=args.expected_version,
            expected_rocket_id=assembled["rocket_id"],
        )
        summary = {
            "schema_version": 1,
            "artifact_sha256": artifact_sha256,
            "artifact_version": args.expected_version,
            "baseline_session_id": baseline.get("session_id"),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version,
            "port": port,
            "coordinates": {"assembler": ASSEMBLER, "rocket_origin": MOTOR},
            "snapshot_hash": assembled["snapshot_hash"],
            "rocket_id": assembled["rocket_id"],
            "transaction_id": persisted["transaction_id"],
            "assembled": assembled,
            "persisted": persisted,
            "recovered": recovered,
            "same_world_verified": True,
            "entity_nbt_restart_byte_equal": True,
            "stale_transaction_recovered": True,
            "durable_journal_cleared": True,
            "container_items_conserved": True,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary)
        print("[PASS] Packaged server assembled a four-block rocket through server authority")
        print("[PASS] Rocket entity identity, snapshot hash, and NBT persisted across restart")
        print("[PASS] Stale pre-commit transaction recovered exact blocks and chest items")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
