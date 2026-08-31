#!/usr/bin/env python3
"""Verify v0.3 fixed Levels, catalog reload, and restart on an installed server."""

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


EXPECTED_VERSION = "1.20.1-0.3.0-dev"
INVALID_PACK_ID = "file/v030-invalid"
INVALID_RELOAD_MARKER = (
    "Rejected celestial catalog; last valid generation remains active"
)
VALID_CATALOG_MARKER = "Celestial catalog generation 1 is valid with 3 bodies"
RECOVERED_CATALOG_MARKER = "Celestial catalog generation 2 is valid with 3 bodies"
CATALOG_LIST_MARKER = "Celestial bodies: 3"
MOON_READY_MARKER = "V030_MOON_LEVEL_READY"
SPACE_READY_MARKER = "V030_SPACE_LEVEL_READY"
MOON_PERSISTED_MARKER = "V030_MOON_BLOCK_PERSISTED"
SPACE_PERSISTED_MARKER = "V030_SPACE_BLOCK_PERSISTED"
REJECTED_STATE_MARKER = "retained after rejected reload"


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
    if not isinstance(artifact_sha256, str) or re.fullmatch(
        r"[0-9a-f]{64}", artifact_sha256
    ) is None:
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
        f"@{args_file.as_posix()}",
        "nogui",
    ]


def _wait_for_text(
    process: CapturedProcess,
    marker: str,
    start_at: int,
    timeout: float = 60.0,
) -> int:
    return process.wait_for(re.compile(re.escape(marker)), timeout, start_at=start_at)


def _command_and_wait(
    process: CapturedProcess,
    command: str,
    marker: str,
    timeout: float = 60.0,
) -> str:
    start = len(process.lines)
    process.command(command)
    index = _wait_for_text(process, marker, start, timeout)
    return process.lines[index].strip()


def _force_load(process: CapturedProcess, dimension: str) -> None:
    _command_and_wait(
        process,
        f"execute in advancedrocketrycommunity:{dimension} run forceload add 0 0",
        "Marked chunk [0, 0]",
    )


def _release_force_load(process: CapturedProcess, dimension: str) -> None:
    _command_and_wait(
        process,
        f"execute in advancedrocketrycommunity:{dimension} run forceload remove 0 0",
        "Unmarked chunk [0, 0]",
    )


def _write_invalid_pack(server: Path) -> Path:
    pack = server / "world" / "datapacks" / "v030-invalid"
    if pack.exists() or pack.is_symlink():
        raise SmokeError(f"Refusing to reuse invalid datapack fixture: {pack}")
    definition = pack / "data" / "advancedrocketrycommunity" / "celestial_bodies" / "moon.json"
    definition.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(
        json.dumps(
            {
                "pack": {
                    "pack_format": 15,
                    "description": "v0.3 atomic-reload rejection fixture",
                }
            },
            ensure_ascii=True,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    definition.write_text(
        json.dumps(
            {
                "id": "advancedrocketrycommunity:moon",
                "parent": "advancedrocketrycommunity:missing_parent",
                "level": "advancedrocketrycommunity:moon",
                "gravity_multiplier": 0.165,
                "atmosphere": {
                    "pressure": 0.0,
                    "breathable": False,
                    "temperature_kelvin": 3.0,
                    "profile": "advancedrocketrycommunity:vacuum",
                },
                "orbit": {
                    "distance": 384400,
                    "period_ticks": 2360591,
                    "inclination_degrees": 5.145,
                },
                "visual_profile": "advancedrocketrycommunity:moon",
            },
            ensure_ascii=True,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    return pack


def _assert_dimension_files(server: Path) -> dict[str, object]:
    result: dict[str, object] = {}
    for name in ("moon", "space"):
        dimension = (
            server
            / "world"
            / "dimensions"
            / "advancedrocketrycommunity"
            / name
        )
        if is_link_or_junction(dimension) or not dimension.is_dir():
            raise SmokeError(f"Saved fixed dimension is missing or unsafe: {dimension}")
        region_files = sorted(dimension.glob("region/*.mca"))
        if not region_files:
            raise SmokeError(f"Saved fixed dimension has no region data: {dimension}")
        if any(path.is_symlink() or not path.is_file() for path in region_files):
            raise SmokeError(f"Saved fixed dimension has unsafe region data: {dimension}")
        result[name] = {
            "region_file_count": len(region_files),
            "region_bytes": sum(path.stat().st_size for path in region_files),
            "region_sha256": [digest_file(path) for path in region_files],
        }
    return result


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
    observed: dict[str, str] = {}
    try:
        process.wait_for(READY_MARKER, startup_timeout)
        status = wait_for_status(port)
        validate_status_identity(status, expected_version)
        observed["validate"] = _command_and_wait(
            process, "arce celestial validate", VALID_CATALOG_MARKER
        )
        observed["list"] = _command_and_wait(
            process, "arce celestial list", CATALOG_LIST_MARKER
        )
        observed["earth"] = _command_and_wait(
            process,
            "arce celestial list",
            "advancedrocketrycommunity:earth -> minecraft:overworld",
        )
        observed["moon"] = _command_and_wait(
            process,
            (
                "execute in advancedrocketrycommunity:moon run say "
                + MOON_READY_MARKER
            ),
            MOON_READY_MARKER,
        )
        observed["space"] = _command_and_wait(
            process,
            (
                "execute in advancedrocketrycommunity:space run say "
                + SPACE_READY_MARKER
            ),
            SPACE_READY_MARKER,
        )

        if before_restart:
            _force_load(process, "moon")
            _force_load(process, "space")
            _command_and_wait(
                process,
                (
                    "execute in advancedrocketrycommunity:moon run setblock "
                    "8 79 8 minecraft:gold_block destroy"
                ),
                "Changed the block at 8, 79, 8",
            )
            _command_and_wait(
                process,
                (
                    "execute in advancedrocketrycommunity:space run setblock "
                    "8 79 8 minecraft:diamond_block destroy"
                ),
                "Changed the block at 8, 79, 8",
            )
            _release_force_load(process, "moon")
            _release_force_load(process, "space")
            _write_invalid_pack(server)
            observed["invalid_reload"] = _command_and_wait(
                process,
                "reload",
                INVALID_RELOAD_MARKER,
            )
            observed["retained_generation"] = _command_and_wait(
                process,
                "arce celestial validate",
                REJECTED_STATE_MARKER,
            )
            observed["recovered_reload"] = _command_and_wait(
                process,
                f'datapack disable "{INVALID_PACK_ID}"',
                "Accepted celestial catalog generation 2 with 3 bodies",
            )
            observed["recovered_validate"] = _command_and_wait(
                process,
                "arce celestial validate",
                RECOVERED_CATALOG_MARKER,
            )
        else:
            _force_load(process, "moon")
            _force_load(process, "space")
            observed["moon_persisted"] = _command_and_wait(
                process,
                (
                    "execute in advancedrocketrycommunity:moon if block 8 79 8 "
                    "minecraft:gold_block run say " + MOON_PERSISTED_MARKER
                ),
                MOON_PERSISTED_MARKER,
            )
            observed["space_persisted"] = _command_and_wait(
                process,
                (
                    "execute in advancedrocketrycommunity:space if block 8 79 8 "
                    "minecraft:diamond_block run say " + SPACE_PERSISTED_MARKER
                ),
                SPACE_PERSISTED_MARKER,
            )
            _release_force_load(process, "moon")
            _release_force_load(process, "space")

        save_start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(SAVE_MARKER, 60, start_at=save_start)
        process.command("stop")
        exit_code = process.finish()
    except BaseException:
        process.abort()
        raise

    if exit_code != 0:
        raise SmokeError(f"Celestial server phase {name} exited with code {exit_code}")
    expected_errors = [
        line for line in process.lines if INVALID_RELOAD_MARKER in line
    ]
    if before_restart and len(expected_errors) != 1:
        raise SmokeError(
            "Invalid reload must produce exactly one explicit catalog rejection"
        )
    if not before_restart and expected_errors:
        raise SmokeError("Restart unexpectedly repeated the disabled invalid datapack")
    audited_lines = [
        line for line in process.lines if INVALID_RELOAD_MARKER not in line
    ]
    findings = scan_log(audited_lines)
    if findings:
        raise SmokeError(
            f"Celestial server phase {name} has a blocking log finding: {findings[0]}"
        )
    return {
        "name": name,
        "started_at": started_at,
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": exit_code,
        "full_log_file": full_log.name,
        "full_log_sha256": digest_file(full_log),
        "expected_catalog_rejections": len(expected_errors),
        "observed": observed,
    }


def _write_evidence(directory: Path, summary: dict[str, object]) -> None:
    if directory.exists():
        raise SmokeError(f"Refusing to overwrite celestial evidence: {directory}")
    directory.mkdir(parents=True)
    (directory / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    lines = [
        f"artifact_sha256={summary['artifact_sha256']}",
        f"artifact_version={summary['artifact_version']}",
        f"baseline_session_id={summary['baseline_session_id']}",
        "catalog_body_count=3",
        "fixed_levels=advancedrocketrycommunity:moon,advancedrocketrycommunity:space",
        "invalid_reload_rejected=true",
        "last_valid_catalog_retained=true",
        "valid_catalog_recovered=true",
        "moon_region_persisted=true",
        "space_region_persisted=true",
        f"moon_marker={MOON_PERSISTED_MARKER}",
        f"space_marker={SPACE_PERSISTED_MARKER}",
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
    parser.add_argument("--expected-version", default=EXPECTED_VERSION)
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--startup-timeout", type=float, default=240.0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        server = args.server_dir.resolve()
        baseline = _load_summary(args.baseline_summary.resolve())
        port, artifact_sha256 = _verify_inputs(
            server,
            baseline,
            args.expected_version,
        )
        java, java_version = resolve_java(args.java)
        before = _run_phase(
            name="v030-celestial-before-restart",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            before_restart=True,
            expected_version=args.expected_version,
        )
        before_dimensions = _assert_dimension_files(server)
        after = _run_phase(
            name="v030-celestial-after-restart",
            java=java,
            server=server,
            port=port,
            startup_timeout=args.startup_timeout,
            before_restart=False,
            expected_version=args.expected_version,
        )
        after_dimensions = _assert_dimension_files(server)
        summary = {
            "schema_version": 1,
            "artifact_sha256": artifact_sha256,
            "artifact_version": args.expected_version,
            "baseline_session_id": baseline.get("session_id"),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "java": java_version,
            "port": port,
            "before_restart": before,
            "after_restart": after,
            "dimensions_before_restart": before_dimensions,
            "dimensions_after_restart": after_dimensions,
            "catalog_body_count": 3,
            "invalid_reload_rejected": True,
            "last_valid_catalog_retained": True,
            "valid_catalog_recovered": True,
            "fixed_level_blocks_persisted": True,
        }
        evidence = args.evidence_dir.resolve()
        _write_evidence(evidence, summary)
        print("[PASS] Fixed Moon and Space Levels loaded in the packaged server")
        print("[PASS] Invalid catalog reload was rejected and the last valid catalog retained")
        print("[PASS] Valid catalog reload recovered after disabling the fixture")
        print("[PASS] Moon and Space block state persisted across same-world restart")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] Evidence: {evidence}")
        return 0
    except (OSError, SmokeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
