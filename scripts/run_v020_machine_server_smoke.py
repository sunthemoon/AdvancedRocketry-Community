#!/usr/bin/env python3
"""Verify packaged Electrolyzer persistence and completion in an installed server."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import sys
import time
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


EXPECTED_VERSION = "1.20.1-0.2.0-dev"
COORDINATES = "0 100 0"
REDSTONE_COORDINATES = "1 100 0"
BEFORE_MARKER = "V020_PERSISTED_BEFORE_RESTART"
AFTER_MARKER = "V020_PERSISTED_AFTER_RESTART"
COMPLETED_MARKER = "V020_COMPLETED_AFTER_RESTART"
BLOCK_DATA_MARKER = re.compile(r"has the following block data")


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
    expected_artifact = f"advancedrocketry-community-{expected_version}.jar"
    server_artifact = server / "mods" / expected_artifact
    if is_link_or_junction(server_artifact) or not server_artifact.is_file():
        raise SmokeError(f"Installed artifact is missing: {server_artifact}")
    if digest_file(server_artifact) != artifact_sha256:
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
        f"@{args_file.as_posix()}",
        "nogui",
    ]


def _wait_for_text(process: CapturedProcess, marker: str, start_at: int, timeout: float = 30.0) -> int:
    return process.wait_for(re.compile(re.escape(marker)), timeout, start_at=start_at)


def _run_phase(
    *,
    name: str,
    java: str,
    server: Path,
    port: int,
    startup_timeout: float,
    before_restart: bool,
    expected_version: str,
) -> dict[str, object]:
    started_at = datetime.now(timezone.utc).isoformat()
    full_log = server / f"{name}-full.txt"
    process = CapturedProcess(_server_command(java), server, full_log)
    try:
        process.wait_for(READY_MARKER, startup_timeout)
        status = wait_for_status(port)
        validate_status_identity(status, expected_version)
        if before_restart:
            process.command(f"forceload add {COORDINATES.split()[0]} {COORDINATES.split()[2]}")
            process.command(f"setblock {REDSTONE_COORDINATES} minecraft:redstone_block")
            process.command(
                f"setblock {COORDINATES} "
                "advancedrocketrycommunity:electrolyzer[facing=north,lit=false,powered=true]"
            )
            process.command(
                f"data merge block {COORDINATES} "
                "{arce_machine:{schema_version:1,inventory:{Size:4,Items:["
                "{Slot:0,id:\"advancedrocketrycommunity:empty_canister\",Count:2b}]},"
                "fluid:{FluidName:\"minecraft:water\",Amount:1000},energy:1200,"
                "progress:40,active_recipe:\"advancedrocketrycommunity:electrolyzer_water\"}}"
            )
            marker = BEFORE_MARKER
        else:
            marker = AFTER_MARKER

        start = len(process.lines)
        process.command(
            f"execute if data block {COORDINATES} "
            "{arce_machine:{schema_version:1,energy:1200,progress:40,"
            "active_recipe:\"advancedrocketrycommunity:electrolyzer_water\"}} "
            f"run say {marker}"
        )
        _wait_for_text(process, marker, start)

        snapshot_start = len(process.lines)
        process.command(f"data get block {COORDINATES} arce_machine")
        snapshot_index = process.wait_for(BLOCK_DATA_MARKER, 30, start_at=snapshot_start)
        snapshot = process.lines[snapshot_index].strip()

        if not before_restart:
            process.command(f"setblock {REDSTONE_COORDINATES} minecraft:air")
            time.sleep(5.0)
            completed_start = len(process.lines)
            process.command(
                f"execute if data block {COORDINATES} "
                "{arce_machine:{schema_version:1,energy:0,progress:0,inventory:{Items:["
                "{Slot:2,id:\"advancedrocketrycommunity:hydrogen_canister\",Count:1b},"
                "{Slot:3,id:\"advancedrocketrycommunity:oxygen_canister\",Count:1b}]}}} "
                f"unless data block {COORDINATES} arce_machine.inventory.Items[{{Slot:0}}] "
                f"if data block {COORDINATES} {{arce_machine:{{fluid:{{Amount:0}}}}}} "
                f"unless data block {COORDINATES} arce_machine.active_recipe "
                f"run say {COMPLETED_MARKER}"
            )
            _wait_for_text(process, COMPLETED_MARKER, completed_start)
            completed_snapshot_start = len(process.lines)
            process.command(f"data get block {COORDINATES} arce_machine")
            completed_index = process.wait_for(
                BLOCK_DATA_MARKER,
                30,
                start_at=completed_snapshot_start,
            )
            completed_snapshot = process.lines[completed_index].strip()
        else:
            completed_snapshot = None

        save_start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60, start_at=save_start)
        process.command("stop")
        exit_code = process.finish()
    except BaseException:
        process.abort()
        raise

    if exit_code != 0:
        raise SmokeError(f"Machine server phase {name} exited with code {exit_code}")
    findings = scan_log(process.lines)
    if findings:
        raise SmokeError(f"Machine server phase {name} has a blocking log finding: {findings[0]}")
    return {
        "name": name,
        "started_at": started_at,
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": exit_code,
        "full_log_file": full_log.name,
        "full_log_sha256": digest_file(full_log),
        "paused_snapshot": snapshot,
        "completed_snapshot": completed_snapshot,
    }


def _write_evidence(directory: Path, summary: dict[str, object]) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite machine evidence: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    lines = [
        f"artifact_sha256={summary['artifact_sha256']}",
        f"baseline_session_id={summary['baseline_session_id']}",
        f"before_marker={BEFORE_MARKER}",
        f"after_marker={AFTER_MARKER}",
        f"completion_marker={COMPLETED_MARKER}",
        "paused_progress=40",
        "paused_energy=1200",
        "completed_progress=0",
        "completed_energy=0",
        "hydrogen_count=1",
        "oxygen_count=1",
        "input_remaining=0",
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
        baseline_summary_path = args.baseline_summary.resolve()
        baseline = _load_summary(baseline_summary_path)
        port, artifact_sha256 = _verify_inputs(
            server,
            baseline,
            args.expected_version,
        )
        java, java_version = resolve_java(args.java)
        before = _run_phase(
            name="v020-machine-before-restart",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            before_restart=True,
            expected_version=args.expected_version,
        )
        after = _run_phase(
            name="v020-machine-after-restart",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            before_restart=False,
            expected_version=args.expected_version,
        )
        summary = {
            "schema_version": 1,
            "artifact_sha256": artifact_sha256,
            "artifact_version": args.expected_version,
            "baseline_session_id": baseline.get("session_id"),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "coordinates": COORDINATES,
            "java": java_version,
            "port": port,
            "before_restart": before,
            "after_restart": after,
            "same_world_verified": True,
            "paused_state_preserved": True,
            "atomic_completion_verified": True,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary)
        print(f"[PASS] Paused Electrolyzer progress 40/100 persisted across packaged-server restart")
        print("[PASS] Restart continuation produced exactly one hydrogen and one oxygen canister")
        print("[PASS] Completion consumed both inputs, 1000 mB water, and 1200 remaining FE")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
