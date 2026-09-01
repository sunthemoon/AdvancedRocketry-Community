#!/usr/bin/env python3
"""Validate the bounded v0.7.0 shared-space station evidence bundle."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path
from typing import Any

if __package__:
    from .manage_v070_generated_manifest import verify as verify_generated_manifest
    from .validate_release_checksums import load_artifact_metadata
    from .validate_v020_release_evidence import (
        EvidenceError,
        _load_json,
        _regular_file,
        _safe_relative,
        _sha256,
    )
else:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from manage_v070_generated_manifest import verify as verify_generated_manifest
    from validate_release_checksums import load_artifact_metadata
    from validate_v020_release_evidence import (
        EvidenceError,
        _load_json,
        _regular_file,
        _safe_relative,
        _sha256,
    )


ROOT = Path(__file__).resolve().parents[1]
RELEASE_ROOT = Path("docs/releases/v0.7.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
PROVENANCE_RECORD = Path("docs/provenance/v0.7.0-space-station.md")
GENERATED_RECORD = Path("docs/provenance/v0.7.0-generated-resources.json")
WAIVER_RECORD = Path("docs/decisions/ADR-009-V070-VISUAL-EVIDENCE-ATTESTATION.md")
EXPECTED_VERSION = "v0.7.0"
EXPECTED_BUILD = "1.20.1-0.7.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.7.0-dev.jar"
EXPECTED_ARTIFACT_SHA256 = (
    "4c049a4e0c2a74f78d383af7bc56ad31d746f8b7f8872cbc7258c58981d9c068"
)
EXPECTED_ARTIFACT_BYTES = 1_009_631
EXPECTED_COMMIT = "e1c2db8ca3e67ae7f92fbbbbd5b6c23a25f7412f"
EXPECTED_REVIEWED_HEAD = "d4caac833ba20c1f017631fb18dafd43e50a6f7d"
EXPECTED_MERGE_COMMIT = "b75e301f6cd77cfc1c1ade0e9b16c485f736c93b"
EXPECTED_PULL_REQUEST = (
    "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/11"
)
EXPECTED_FORGE_CI = (
    "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33506933608"
)
EXPECTED_GOVERNANCE_CI = (
    "https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33506933587"
)
EXPECTED_MANIFEST_SHA256 = (
    "a1f395969cf105627d3e6c8bbe811d23d36b4331153b140852efed0e9e5172fd"
)
EXPECTED_GENERATED_SHA256 = (
    "9a94153a3f831a0dce4b2cdf02316eb8f14da1ac42bafcf8dc6dc0850b104b20"
)
AUTHORIZED_REVIEWERS = {"sunthemoon"}
REQUIRED_RELEASE_DOCS = {
    "GATE-STATUS.md",
    "INSTALLATION.md",
    "KNOWN-ISSUES.md",
    "MANUAL-TEST.md",
    "PERFORMANCE.md",
    "RELEASE-EVIDENCE.md",
    "TEST-REPORT.md",
    "checksums.txt",
}
SHA256 = re.compile(r"[0-9a-f]{64}")
MACHINE_PATH = re.compile(r"(?<![A-Za-z])[A-Za-z]:[\\/]|server-work[/\\]run-")
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_TEXT_BYTES = 512 * 1024
MAX_EVIDENCE_BYTES = 8 * 1024 * 1024


def _valid_date(value: object) -> bool:
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        return False
    try:
        dt.date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _validate_provenance(repository_root: Path, errors: list[str]) -> bool:
    try:
        provenance_path = _regular_file(
            repository_root, PROVENANCE_RECORD.as_posix(), MAX_TEXT_BYTES
        )
        provenance = provenance_path.read_text(encoding="utf-8", errors="strict")
        generated_path = _regular_file(
            repository_root, GENERATED_RECORD.as_posix(), MAX_JSON_BYTES
        )
        generated = _load_json(repository_root, GENERATED_RECORD)
        errors.extend(verify_generated_manifest(repository_root, generated_path))
    except (EvidenceError, OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        errors.append(str(exc))
        return False
    required = {
        "target_version: v0.7.0",
        "status: APPROVED",
        'reviewer: "sunthemoon"',
        'reviewed_at: "2026-09-01"',
        "copied_source_files: 0",
        "copied_binary_assets: 0",
        "generated_resources: 5",
        f"generated_manifest_sha256: {EXPECTED_GENERATED_SHA256}",
    }
    targets = generated.get("targets")
    if any(item not in provenance for item in required):
        errors.append("v0.7.0 provenance is not explicitly owner-approved")
    if (
        generated.get("schema_version") != 1
        or generated.get("target_version") != EXPECTED_VERSION
        or generated.get("status") != "COMMUNITY_AUTHORED_DATAGEN"
        or not isinstance(targets, list)
        or len(targets) != 5
        or _sha256(generated_path) != EXPECTED_GENERATED_SHA256
    ):
        errors.append("v0.7.0 generated-resource provenance is incomplete")
    return not errors


def _validate_artifact(
    repository_root: Path, artifact: Path | None, errors: list[str]
) -> dict[str, Any]:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "artifact/artifact-summary.json"
        )
        manifest = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            MAX_JSON_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    metadata, metadata_errors = load_artifact_metadata(manifest)
    errors.extend(f"v0.7.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    repeated = summary.get("repeated_clean_builds")
    server_copy = summary.get("packaged_server_copy")
    content = summary.get("content_manifest")
    if not all(isinstance(item, dict) for item in (main, repeated, server_copy, content)):
        errors.append("v0.7.0 artifact summary is missing required records")
        return summary
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or main
        != {
            "path": f"build/libs/{EXPECTED_ARTIFACT}",
            "filename": EXPECTED_ARTIFACT,
            "bytes": EXPECTED_ARTIFACT_BYTES,
            "sha256": EXPECTED_ARTIFACT_SHA256,
        }
        or repeated.get("count") != 2
        or repeated.get("byte_identical") is not True
        or repeated.get("main_sha256_values")
        != [EXPECTED_ARTIFACT_SHA256, EXPECTED_ARTIFACT_SHA256]
        or server_copy != {"byte_equal": True, "sha256": EXPECTED_ARTIFACT_SHA256}
        or content
        != {
            "path": (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            "sha256": EXPECTED_MANIFEST_SHA256,
            "entry_count": 636,
        }
        or _sha256(manifest) != EXPECTED_MANIFEST_SHA256
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != EXPECTED_ARTIFACT_SHA256
        or metadata.manifest.get("entry_count") != 636
    ):
        errors.append("v0.7.0 artifact summary does not bind the repeated packaged JAR")
    if artifact is not None:
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if (
                not actual.is_file()
                or actual.is_symlink()
                or actual.stat().st_size != EXPECTED_ARTIFACT_BYTES
            ):
                errors.append("artifact is missing, unsafe, or has the wrong size")
            elif _sha256(actual) != EXPECTED_ARTIFACT_SHA256:
                errors.append("artifact SHA-256 differs from the v0.7.0 evidence")
    return summary


def _validate_automated(repository_root: Path, errors: list[str]) -> dict[str, Any]:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "automated/summary.json")
        gametest = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "automated/gametest.txt").as_posix(),
            MAX_TEXT_BYTES,
        ).read_text(encoding="utf-8", errors="strict")
        authority = _load_json(
            repository_root, EVIDENCE_ROOT / "automated/authority-matrix.json"
        )
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return {}
    results = summary.get("results")
    security = summary.get("security")
    cases = authority.get("cases")
    required_results = {
        "clean_builds",
        "junit",
        "python",
        "gametest",
        "datagen",
        "jar_audit",
        "client_boundary",
        "repository",
        "packaged_station",
        "multiplayer",
    }
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or not isinstance(results, dict)
        or not required_results.issubset(results)
        or any(results[key].get("result") != "PASS" for key in required_results)
        or results["clean_builds"].get("runs") != 2
        or results["clean_builds"].get("byte_identical") is not True
        or results["junit"].get("passed") != 220
        or results["junit"].get("failed") != 0
        or results["junit"].get("skipped") != 0
        or results["python"].get("failed") != 0
        or results["python"].get("passed", 0) < 620
        or results["gametest"].get("passed") != 42
        or results["gametest"].get("failed") != 0
        or results["datagen"].get("files") != 5
        or results["datagen"].get("git_diff_clean") is not True
        or results["jar_audit"].get("entries") != 636
        or results["jar_audit"].get("findings") != 0
        or results["client_boundary"].get("findings") != 0
        or results["repository"].get("failed") != 0
        or results["repository"].get("warnings") != 0
        or not isinstance(security, dict)
        or security.get("client_final_station_decisions") != 0
        or security.get("coordinate_free_station_intents") is not True
        or security.get("server_revalidated_station_access") is not True
        or security.get("unloaded_destination_forcing") != 0
        or security.get("bounded_station_nbt") is not True
        or security.get("permanent_station_tickets") != 0
        or authority.get("schema_version") != 1
        or authority.get("version") != EXPECTED_VERSION
        or authority.get("critical_or_high_findings") != 0
        or not isinstance(cases, list)
        or len(cases) < 8
        or any(case.get("result") != "PASS" for case in cases)
        or "All 42 required tests passed" not in gametest
        or "atmosphere_simulated_ticks=6000" not in gametest
    ):
        errors.append("v0.7.0 automated or authority evidence is incomplete")
    return summary


def _validate_dedicated(repository_root: Path, errors: list[str]) -> bool:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "dedicated-server/summary.json"
        )
        first = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "dedicated-server/first-start.txt").as_posix(),
            MAX_TEXT_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
        restart = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "dedicated-server/restart.txt").as_posix(),
            MAX_TEXT_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    cycles = summary.get("cycles")
    if (
        summary.get("schema_version") != 2
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("server_artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("mod_version") != EXPECTED_BUILD
        or not isinstance(cycles, list)
        or [cycle.get("name") for cycle in cycles] != ["first-start", "restart"]
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
        or any(cycle.get("error_count") != 0 for cycle in cycles)
        or any(cycle.get("client_linkage_failure_count") != 0 for cycle in cycles)
        or summary.get("world", {}).get("same_world_verified") is not True
        or "Saved the game" not in first
        or "Saved the game" not in restart
    ):
        errors.append("v0.7.0 dedicated-server lifecycle is incomplete")
    return not errors


def _validate_station(repository_root: Path, errors: list[str]) -> bool:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "station-server/summary.json")
        station_map = _load_json(
            repository_root, EVIDENCE_ROOT / "station-server/station-map.json"
        )
        ledger = _load_json(
            repository_root, EVIDENCE_ROOT / "station-server/flight-ledger.json"
        )
        restart = _load_json(
            repository_root, EVIDENCE_ROOT / "station-server/restart-and-deletion.json"
        )
        lifecycle = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "station-server/filtered-lifecycle.log").as_posix(),
            MAX_EVIDENCE_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    stations = station_map.get("stations")
    if not isinstance(stations, list):
        stations = []
    ids = {station.get("station_id") for station in stations}
    cells = {tuple(station.get("cell", [])) for station in stations}
    regions = [station.get("region") for station in stations]
    geometry_ok = len(stations) == len(ids) == len(cells) == 10
    for index, region in enumerate(regions):
        if not isinstance(region, list) or len(region) != 4:
            geometry_ok = False
            continue
        if region[2] - region[0] + 1 != 512 or region[3] - region[1] + 1 != 512:
            geometry_ok = False
        for other in regions[index + 1 :]:
            if not isinstance(other, list) or len(other) != 4:
                geometry_ok = False
                continue
            overlap = not (
                region[2] < other[0]
                or other[2] < region[0]
                or region[3] < other[1]
                or other[3] < region[1]
            )
            if overlap:
                geometry_ok = False
    station_legs = ledger.get("station_legs")
    ordinary_legs = ledger.get("ordinary_legs")
    if (
        summary.get("schema_version") != 1
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("artifact_version") != EXPECTED_BUILD
        or summary.get("station_count") != 10
        or summary.get("pairwise_non_overlapping") is not True
        or summary.get("restart_persistence_verified") is not True
        or summary.get("same_world_verified") is not True
        or summary.get("neighbor_deletion_isolated") is not True
        or summary.get("critical_or_high_findings") != 0
        or not geometry_ok
        or station_map.get("region_size") != 512
        or station_map.get("grid_spacing") != 1024
        or restart.get("restart_station_count") != 10
        or restart.get("same_ids_owners_cells_regions") is not True
        or restart.get("neighbor_survived") is not True
        or not isinstance(station_legs, list)
        or len(station_legs) != 2
        or any(leg.get("landing_inside_region") is not True for leg in station_legs)
        or any(leg.get("exact_fuel_debit") is not True for leg in station_legs)
        or not isinstance(ordinary_legs, list)
        or len(ordinary_legs) != 4
        or ledger.get("earth_station_round_trip") is not True
        or ledger.get("moon_station_round_trip") is not True
        or "ARCE_STATION_REGION_DUMP count=10" not in lifecycle
        or "ARCE_RELEASE_TEST_STATION_LAUNCH" not in lifecycle
        or MACHINE_PATH.search(lifecycle)
    ):
        errors.append("v0.7.0 packaged station, persistence, or flight evidence is incomplete")
    return not errors


def _validate_multiplayer(repository_root: Path, errors: list[str]) -> bool:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "multiplayer/summary.json")
        server = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "multiplayer/server.txt").as_posix(),
            MAX_TEXT_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
        client_a = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "multiplayer/client-a.txt").as_posix(),
            MAX_TEXT_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
        client_b = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "multiplayer/client-b.txt").as_posix(),
            MAX_TEXT_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    clients = summary.get("clients")
    observations = summary.get("observations")
    limitations = summary.get("limitations")
    marker = summary.get("shared_marker")
    expected_names = ["ClientA", "PilotB"]
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or not isinstance(clients, list)
        or [client.get("username") for client in clients] != expected_names
        or any(client.get("connected_to_modded_server") is not True for client in clients)
        or any(client.get("received_shared_marker") is not True for client in clients)
        or any(client.get("clean_shutdown") is not True for client in clients)
        or not isinstance(observations, dict)
        or observations.get("simultaneous_players") != 2
        or observations.get("station_count") != 2
        or observations.get("same_server_marker_received_by_both") is not True
        or observations.get("two_player_station_owners") is not True
        or observations.get("client_linkage_failures") != 0
        or not isinstance(marker, str)
        or marker not in server
        or marker not in client_a
        or marker not in client_b
        or "ClientA joined the game" not in server
        or "PilotB joined the game" not in server
        or "ClientA left the game" not in server
        or "PilotB left the game" not in server
        or "Setting user: ClientA" not in client_a
        or "Setting user: PilotB" not in client_b
        or "Connected to a modded server." not in client_a
        or "Connected to a modded server." not in client_b
        or "Stopping!" not in client_a
        or "Stopping!" not in client_b
        or not isinstance(limitations, list)
        or not any("No screenshot or video" in item for item in limitations)
        or any(MACHINE_PATH.search(text) for text in (server, client_a, client_b))
    ):
        errors.append("v0.7.0 two-client packaged-server evidence is incomplete")
    return not errors


def _validate_performance(repository_root: Path, errors: list[str]) -> bool:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "performance/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    atmosphere = summary.get("atmosphere_soak")
    limits = summary.get("station_limits")
    station = summary.get("station_packaged_run")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or not all(isinstance(item, dict) for item in (atmosphere, limits, station))
        or atmosphere.get("result") != "PASS"
        or atmosphere.get("active_vents") != 16
        or atmosphere.get("simulated_ticks") != 6000
        or atmosphere.get("peak_inspections", 1025)
        > atmosphere.get("maximum_level_inspections_per_tick", 1024)
        or limits.get("stations") != 4096
        or limits.get("reservations") != 64
        or limits.get("region_size_blocks") != 512
        or limits.get("grid_spacing_blocks") != 1024
        or limits.get("flight_ticket_timeout_ticks", 401) > 400
        or station.get("result") != "PASS"
        or station.get("station_count") != 10
        or station.get("critical_or_high_findings") != 0
    ):
        errors.append("v0.7.0 performance evidence is incomplete")
    return not errors


def _validate_owner(repository_root: Path, errors: list[str]) -> tuple[dict[str, Any], bool]:
    try:
        attestation = _load_json(
            repository_root, EVIDENCE_ROOT / "manual/owner-attestation.json"
        )
        waiver = _regular_file(
            repository_root, WAIVER_RECORD.as_posix(), MAX_TEXT_BYTES
        ).read_text(encoding="utf-8", errors="strict")
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return {}, False
    approvals = attestation.get("approvals")
    basis = attestation.get("acceptance_basis")
    limitations = attestation.get("limitations_accepted")
    human_approved = bool(
        attestation.get("schema_version") == 1
        and attestation.get("version") == EXPECTED_VERSION
        and attestation.get("approved_by") in AUTHORIZED_REVIEWERS
        and _valid_date(attestation.get("approved_at"))
        and approvals
        == {
            "G0_provenance": "PASS",
            "G8_visible_client_acceptance": "PASS",
            "G9_release_acceptance": "PASS",
        }
        and isinstance(basis, dict)
        and basis.get("artifact_sha256") == EXPECTED_ARTIFACT_SHA256
        and basis.get("tested_implementation_commit") == EXPECTED_COMMIT
        and basis.get("waiver") == WAIVER_RECORD.as_posix()
        and isinstance(limitations, list)
        and any("No screenshot or video" in item for item in limitations)
        and all(
            marker in waiver
            for marker in (
                "**Status:** Accepted",
                "**Owner:** `sunthemoon`",
                "**Applies to:** v0.7.0 only",
                "**Expires:** when v0.8.0 validation begins",
                "## Risk and user impact",
                "## Mitigation",
                "## Recollection condition",
                "## Automatic failure reminder",
            )
        )
    )
    if not human_approved:
        errors.append("v0.7.0 G0/G8/G9 owner attestation or ADR is incomplete")
    return attestation, human_approved


def _validate_docs(repository_root: Path, errors: list[str]) -> bool:
    for name in sorted(REQUIRED_RELEASE_DOCS):
        try:
            path = _regular_file(
                repository_root, (RELEASE_ROOT / name).as_posix(), MAX_TEXT_BYTES
            )
            if name != "checksums.txt":
                text = path.read_text(encoding="utf-8", errors="strict")
                if MACHINE_PATH.search(text):
                    errors.append(f"v0.7.0 release document contains a machine path: {name}")
        except (EvidenceError, UnicodeError) as exc:
            errors.append(str(exc))
    return not errors


def _validate_checksums(
    repository_root: Path, artifact_summary: dict[str, Any], errors: list[str]
) -> bool:
    try:
        path = _regular_file(
            repository_root, (RELEASE_ROOT / "checksums.txt").as_posix(), MAX_TEXT_BYTES
        )
        lines = path.read_text(encoding="utf-8", errors="strict").splitlines()
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return False
    recorded: dict[str, str] = {}
    for line_number, line in enumerate(lines, start=1):
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or SHA256.fullmatch(parts[0]) is None:
            errors.append(f"v0.7.0 checksums line {line_number} is invalid")
            continue
        try:
            relative = _safe_relative(parts[1].strip()).as_posix()
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if relative in recorded:
            errors.append(f"v0.7.0 checksums repeats {relative}")
        recorded[relative] = parts[0]
    evidence_files = {
        item.relative_to(repository_root).as_posix()
        for item in (repository_root / EVIDENCE_ROOT).rglob("*")
        if item.is_file()
    }
    main = artifact_summary.get("main_jar", {})
    artifact_path = main.get("path")
    expected = evidence_files | {artifact_path}
    if artifact_path is None:
        errors.append("v0.7.0 artifact checksum path is missing")
        expected.discard(None)
    if set(recorded) != expected:
        errors.append("v0.7.0 checksums inventory is incomplete or contains extras")
    for relative in sorted(evidence_files):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_EVIDENCE_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.7.0 checksum mismatch: {relative}")
    if recorded.get(artifact_path) != main.get("sha256"):
        errors.append("v0.7.0 checksums omit or change the artifact binding")
    return not errors


def _validate_post_merge(
    repository_root: Path, errors: list[str]
) -> dict[str, Any]:
    relative = EVIDENCE_ROOT / "artifact/post-merge-reproduction.json"
    before = len(errors)
    try:
        record = _load_json(repository_root, relative)
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    main = record.get("main_jar")
    manifest = record.get("content_manifest")
    checks = record.get("pull_request_checks")
    tests = record.get("unit_tests")
    environment = record.get("environment")
    if (
        record.get("schema_version") != 1
        or record.get("version") != EXPECTED_VERSION
        or record.get("build") != EXPECTED_BUILD
        or record.get("tested_implementation_commit") != EXPECTED_COMMIT
        or record.get("reviewed_head_commit") != EXPECTED_REVIEWED_HEAD
        or record.get("merge_commit") != EXPECTED_MERGE_COMMIT
        or record.get("pull_request") != EXPECTED_PULL_REQUEST
        or record.get("reproduced_at") != "2026-09-01"
        or record.get("build_result") != "PASS"
        or record.get("build_command")
        != ".\\gradlew.bat clean build --no-daemon --stacktrace --no-build-cache --rerun-tasks"
        or main
        != {
            "byte_equal_to_candidate": True,
            "bytes": EXPECTED_ARTIFACT_BYTES,
            "candidate_sha256": EXPECTED_ARTIFACT_SHA256,
            "reproduced_sha256": EXPECTED_ARTIFACT_SHA256,
        }
        or manifest
        != {
            "byte_equal_to_candidate": True,
            "entry_count": 636,
            "candidate_sha256": EXPECTED_MANIFEST_SHA256,
            "reproduced_sha256": EXPECTED_MANIFEST_SHA256,
        }
        or environment
        != {
            "java": "Microsoft OpenJDK 17.0.8+7-LTS",
            "os": "Microsoft Windows 11 Pro 10.0.26200 amd64",
        }
        or tests
        != {
            "errors": 0,
            "failures": 0,
            "passed": 220,
            "skipped": 0,
            "total": 220,
        }
        or checks
        != {
            "checks": [
                "Forge 47.4.10 baseline",
                "Forge 47.4.23 compatibility (advisory)",
                "v0.7.0 packaged station gate",
                "validate-repository-docs",
            ],
            "forge_ci": EXPECTED_FORGE_CI,
            "governance_ci": EXPECTED_GOVERNANCE_CI,
            "result": "4/4_PASS",
        }
    ):
        errors.append("v0.7.0 post-merge reproduction is incomplete or inconsistent")
    return record if len(errors) == before else {}


def validate_v070_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
    *,
    require_approved: bool = False,
) -> tuple[list[str], dict[str, Any]]:
    """Return validation errors and v0.7.0 Gate readiness details."""
    repository_root = repository_root.resolve()
    errors: list[str] = []

    before = len(errors)
    provenance_ready = _validate_provenance(repository_root, errors) and len(errors) == before
    before = len(errors)
    artifact_summary = _validate_artifact(repository_root, artifact, errors)
    artifact_ready = bool(artifact_summary) and len(errors) == before
    before = len(errors)
    automated = _validate_automated(repository_root, errors)
    automated_ready = bool(automated) and len(errors) == before
    before = len(errors)
    dedicated_ready = _validate_dedicated(repository_root, errors) and len(errors) == before
    before = len(errors)
    station_ready = _validate_station(repository_root, errors) and len(errors) == before
    before = len(errors)
    multiplayer_ready = _validate_multiplayer(repository_root, errors) and len(errors) == before
    before = len(errors)
    performance_ready = _validate_performance(repository_root, errors) and len(errors) == before
    before = len(errors)
    attestation, human_approved = _validate_owner(repository_root, errors)
    client_ready = multiplayer_ready and human_approved and len(errors) == before
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before
    before = len(errors)
    checksums_ready = _validate_checksums(repository_root, artifact_summary, errors)
    checksums_ready = checksums_ready and len(errors) == before
    before = len(errors)
    post_merge = _validate_post_merge(repository_root, errors)
    post_merge_ready = bool(post_merge) and len(errors) == before
    if require_approved and not human_approved:
        errors.append("v0.7.0 evidence has not received explicit G0/G8/G9 owner approval")

    security = automated.get("security", {}) if isinstance(automated, dict) else {}
    details = {
        "artifact_sha256": EXPECTED_ARTIFACT_SHA256 if artifact_ready else "",
        "tested_implementation_commit": EXPECTED_COMMIT,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "post_merge_ready": post_merge_ready,
        "merge_commit": post_merge.get("merge_commit") if post_merge_ready else None,
        "reviewed_head_commit": post_merge.get("reviewed_head_commit")
        if post_merge_ready
        else None,
        "pull_request": post_merge.get("pull_request") if post_merge_ready else None,
        "pull_request_checks": post_merge.get("pull_request_checks", {}).get("result")
        if post_merge_ready
        else None,
        "forge_ci": post_merge.get("pull_request_checks", {}).get("forge_ci")
        if post_merge_ready
        else None,
        "governance_ci": post_merge.get("pull_request_checks", {}).get(
            "governance_ci"
        )
        if post_merge_ready
        else None,
        "data_ready": provenance_ready and automated_ready,
        "automated_ready": automated_ready,
        "server_ready": dedicated_ready and station_ready and multiplayer_ready,
        "persistence_ready": station_ready,
        "authority_ready": automated_ready
        and station_ready
        and security.get("server_revalidated_station_access") is True
        and security.get("client_final_station_decisions") == 0,
        "performance_ready": performance_ready,
        "client_ready": client_ready,
        "docs_ready": docs_ready and checksums_ready and post_merge_ready,
        "checksums_ready": checksums_ready,
        "human_approved": human_approved,
        "human_approved_at": attestation.get("approved_at")
        if isinstance(attestation, dict)
        else None,
    }
    return errors, details


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--require-approved", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors, details = validate_v070_release_evidence(
        ROOT, artifact=args.artifact, require_approved=args.require_approved
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(
        "[PASS] v0.7.0 release evidence is complete: "
        + ", ".join(key for key, value in details.items() if value is True)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
