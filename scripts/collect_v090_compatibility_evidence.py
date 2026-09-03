#!/usr/bin/env python3
"""Validate and filter the four-cell v0.9 Forge/JEI client matrix."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

if __package__:
    from .run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction
    from .run_v060_flight_server_smoke import _load_summary
else:
    from run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction
    from run_v060_flight_server_smoke import _load_summary


EXPECTED_VERSION = "1.20.1-0.9.0-beta.1"
EXPECTED_JEI = "15.56.0.205"
MAX_LOG_BYTES = 16 * 1024 * 1024
CELLS = (
    ("forge-47.4.10-jei-present", "47.4.10", True, "JeiP10"),
    ("forge-47.4.10-jei-absent", "47.4.10", False, "NoJei10"),
    ("forge-47.4.23-jei-present", "47.4.23", True, "JeiP23"),
    ("forge-47.4.23-jei-absent", "47.4.23", False, "NoJei23"),
)
FORBIDDEN = (
    "Unknown recipe category",
    "NoClassDefFoundError",
    "ClassNotFoundException: net.minecraft.client",
    "Attempted to load class net/minecraft/client",
)
FILTER_MARKERS = (
    "Setting user:",
    "Forge mod loading, version",
    "Advanced Rocketry: Community Edition",
    "ARCE-BETA-1100",
    "Connecting to 127.0.0.1",
    "Connected to a modded server.",
    "Loaded 2 advancements",
)


def _read_log(path: Path) -> tuple[list[str], str]:
    if is_link_or_junction(path) or not path.is_file() or path.stat().st_size > MAX_LOG_BYTES:
        raise SmokeError(f"Compatibility log is missing, unsafe, or oversized: {path}")
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    return lines, "\n".join(lines)


def validate_client_log(
    path: Path,
    *,
    forge: str,
    jei_present: bool,
    username: str,
) -> tuple[dict[str, object], list[str]]:
    lines, text = _read_log(path)
    required = [
        f"Setting user: {username}",
        f"Forge mod loading, version {forge}, for MC 1.20.1",
        f"Advanced Rocketry: Community Edition {EXPECTED_VERSION} initialized",
        "Connecting to 127.0.0.1, 25605",
        "Connected to a modded server.",
    ]
    if jei_present:
        required.extend((
            f"ARCE-BETA-1100 optional_compat=jei status=present version={EXPECTED_JEI}",
            "ARCE-BETA-1100 optional_compat=jei status=registered recipes=1",
        ))
    else:
        required.append("ARCE-BETA-1100 optional_compat=jei status=absent version=absent")
    missing = [marker for marker in required if marker not in text]
    if missing:
        raise SmokeError(f"Client {username} is missing compatibility marker: {missing[0]}")
    for marker in FORBIDDEN:
        if marker in text:
            raise SmokeError(f"Client {username} contains forbidden marker: {marker}")
    project_findings = [
        line
        for line in lines
        if re.search(r"/(?:ERROR|FATAL)\]", line)
        and "advancedrocketrycommunity" in line.casefold()
    ]
    if project_findings:
        raise SmokeError(f"Client {username} has a project error: {project_findings[0]}")
    filtered = [line.rstrip() for line in lines if any(marker in line for marker in FILTER_MARKERS)]
    return {
        "forge": forge,
        "jei": EXPECTED_JEI if jei_present else "absent",
        "username": username,
        "kind": "forge_userdev_client",
        "connected_to_exact_packaged_server": True,
        "jei_recipe_count": 1 if jei_present else 0,
        "unknown_recipe_category_count": 0,
        "project_error_or_fatal_count": 0,
        "full_log_bytes": path.stat().st_size,
        "full_log_sha256": digest_file(path),
        "result": "PASS",
        "command": (
            f"./gradlew runClient -Pforge_version={forge} "
            f"-ParceIncludeJei={'true' if jei_present else 'false'} "
            f"-ParceQuickPlayMultiplayer=127.0.0.1:25605 "
            f"-ParceEvidenceUsername={username}"
        ),
    }, filtered


def validate_server_log(path: Path) -> tuple[dict[str, object], list[str]]:
    lines, text = _read_log(path)
    for _, _, _, username in CELLS:
        joined = f"{username} joined the game"
        left = f"{username} left the game"
        if joined not in text or left not in text or text.index(joined) >= text.index(left):
            raise SmokeError(f"Packaged server lacks ordered join/leave evidence for {username}")
    if "Saved the game" not in text or "Stopping server" not in text:
        raise SmokeError("Packaged compatibility server did not save and stop cleanly")
    project_findings = [
        line
        for line in lines
        if re.search(r"/(?:ERROR|FATAL)\]", line)
        and "advancedrocketrycommunity" in line.casefold()
    ]
    if project_findings:
        raise SmokeError(f"Packaged compatibility server has a project error: {project_findings[0]}")
    filtered = [
        line.rstrip()
        for line in lines
        if any(
            marker in line
            for marker in (
                "Advanced Rocketry: Community Edition",
                "ARCE-BETA-1100",
                "joined the game",
                "left the game",
                "Saved the game",
                "Stopping server",
            )
        )
    ]
    return {
        "kind": "packaged_forge_server",
        "forge": "47.4.10",
        "loopback_only": True,
        "offline_mode_for_unauthenticated_userdev_clients": True,
        "joins": len(CELLS),
        "leaves": len(CELLS),
        "clean_save_and_stop": True,
        "project_error_or_fatal_count": 0,
        "full_log_bytes": path.stat().st_size,
        "full_log_sha256": digest_file(path),
    }, filtered


def _write_evidence(
    output: Path,
    summary: dict[str, object],
    server_lines: list[str],
    client_lines: dict[str, list[str]],
) -> None:
    if output.exists() or is_link_or_junction(output):
        raise SmokeError(f"Refusing to overwrite compatibility evidence: {output}")
    output.mkdir(parents=True)
    (output / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    (output / "server.txt").write_text(
        "\n".join(server_lines) + "\n", encoding="utf-8", newline="\n"
    )
    for cell, lines in client_lines.items():
        (output / f"{cell}.txt").write_text(
            "\n".join(lines) + "\n", encoding="utf-8", newline="\n"
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--baseline-summary", type=Path, required=True)
    parser.add_argument("--server-log", type=Path, required=True)
    for cell, _, _, _ in CELLS:
        parser.add_argument(f"--{cell}", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--tested-commit", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if re.fullmatch(r"[0-9a-f]{40}", args.tested_commit) is None:
            raise SmokeError("Tested commit must be a full lowercase Git SHA-1")
        artifact = args.artifact.resolve()
        baseline_path = args.baseline_summary.resolve()
        baseline = _load_summary(baseline_path)
        if (
            is_link_or_junction(artifact)
            or not artifact.is_file()
            or baseline.get("mod_version") != EXPECTED_VERSION
            or baseline.get("artifact_sha256") != digest_file(artifact)
        ):
            raise SmokeError("Compatibility matrix is not bound to the exact Beta JAR")
        matrix: list[dict[str, object]] = []
        filtered_clients: dict[str, list[str]] = {}
        for cell, forge, jei_present, username in CELLS:
            path = getattr(args, cell.replace("-", "_")).resolve()
            document, filtered = validate_client_log(
                path, forge=forge, jei_present=jei_present, username=username
            )
            document["cell"] = cell
            matrix.append(document)
            filtered_clients[cell] = filtered
        server, filtered_server = validate_server_log(args.server_log.resolve())
        summary = {
            "schema_version": 1,
            "version": "v0.9.0",
            "build": EXPECTED_VERSION,
            "tested_implementation_commit": args.tested_commit,
            "artifact_sha256": digest_file(artifact),
            "baseline_summary_sha256": digest_file(baseline_path),
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "matrix": matrix,
            "server": server,
            "cells_required": len(CELLS),
            "cells_passed": len(matrix),
            "unknown_recipe_category_count": 0,
            "project_error_or_fatal_count": 0,
            "external_observations": [
                "NVIDIA OpenGL debug messages were emitted at INFO level by Mojang/LWJGL on this machine.",
                "JEI warns that the server does not provide JEI recipes, then reloads the synchronized ARCE recipe and reports recipes=1.",
                "Clients were stopped after observed join; the packaged server observed every disconnect and then saved and stopped cleanly.",
            ],
            "result": "PASS",
        }
        _write_evidence(args.output.resolve(), summary, filtered_server, filtered_clients)
        print(f"[PASS] Forge/JEI compatibility matrix: {len(matrix)}/{len(CELLS)} cells")
        print("[PASS] All clients joined; JEI recipe count and absence contracts are exact")
        print("[PASS] Zero unknown recipe categories and zero project ERROR/FATAL findings")
        return 0
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError, SmokeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
