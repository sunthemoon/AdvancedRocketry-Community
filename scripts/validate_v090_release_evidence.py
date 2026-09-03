#!/usr/bin/env python3
"""Validate the bounded v0.9.0 Beta release-evidence bundle."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

if __package__:
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
    from validate_release_checksums import load_artifact_metadata
    from validate_v020_release_evidence import (
        EvidenceError,
        _load_json,
        _regular_file,
        _safe_relative,
        _sha256,
    )


ROOT = Path(__file__).resolve().parents[1]
RELEASE_ROOT = Path("docs/releases/v0.9.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
VISUAL_DECISION = Path("docs/decisions/ADR-013-V090-VISUAL-EVIDENCE-INHERITANCE.md")
EXPECTED_VERSION = "v0.9.0"
EXPECTED_BUILD = "1.20.1-0.9.0-beta.1"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.9.0-beta.1.jar"
EXPECTED_ARTIFACT_SHA256 = (
    "fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad"
)
EXPECTED_ARTIFACT_BYTES = 1_225_536
EXPECTED_COMMIT = "f6cd77cebdb0a851cab76accbf66de565473b545"
EXPECTED_BASE = "0d59c01da458e13ed0014e98f91379c6f783e19d"
EXPECTED_MANIFEST_SHA256 = (
    "d3b9d951134791c5b3703542cbe9ff107a95e14358dec18ae7de7bbd6b42066b"
)
EXPECTED_RESOURCE_SHA256 = (
    "05cf7d6fcb84e454ebd630a44f8f1817790d99eecc78b7f54b1feb8a9c64eb14"
)
AUTHORIZED_REVIEWERS = {"sunthemoon"}
REQUIRED_RELEASE_DOCS = {
    "GATE-STATUS.md",
    "INSTALLATION.md",
    "KNOWN-ISSUES.md",
    "MANUAL-TEST.md",
    "PERFORMANCE.md",
    "RELEASE-EVIDENCE.md",
    "RELEASE-NOTES.md",
    "TEST-REPORT.md",
    "checksums.txt",
}
SHA256 = re.compile(r"[0-9a-f]{64}")
GIT_SHA = re.compile(r"[0-9a-f]{40}")
MACHINE_PATH = re.compile(r"(?<![A-Za-z])[A-Za-z]:[\\/]|server-work[/\\]run-")
MAX_JSON_BYTES = 4 * 1024 * 1024
MAX_TEXT_BYTES = 2 * 1024 * 1024
MAX_EVIDENCE_BYTES = 8 * 1024 * 1024


def _valid_date(value: object) -> bool:
    if not isinstance(value, str) or re.fullmatch(r"\d{4}-\d{2}-\d{2}", value) is None:
        return False
    try:
        dt.date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _identity(
    document: dict[str, Any],
    *,
    commit_key: str = "tested_implementation_commit",
    build_key: str = "build",
) -> bool:
    return (
        document.get("schema_version") == 1
        and document.get("version") == EXPECTED_VERSION
        and document.get(build_key) == EXPECTED_BUILD
        and document.get("artifact_sha256") == EXPECTED_ARTIFACT_SHA256
        and document.get(commit_key) == EXPECTED_COMMIT
    )


def _load(repository_root: Path, relative: str, errors: list[str]) -> dict[str, Any]:
    try:
        return _load_json(repository_root, Path(relative))
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}


def _text(repository_root: Path, relative: str, errors: list[str]) -> str:
    try:
        return _regular_file(repository_root, relative, MAX_TEXT_BYTES).read_text(
            encoding="utf-8", errors="strict"
        )
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return ""


def _validate_provenance(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    records = {
        "LICENSE": ("Copyright (c) 2017", "MIT License"),
        "NOTICE.md": ("unofficial", "Advanced Rocketry"),
        "README.md": ("unofficial", "Advanced Rocketry"),
        "src/main/resources/META-INF/mods.toml": (
            'license="${mod_license}"',
            "${mod_description}",
        ),
        "gradle.properties": (
            "mod_license=MIT",
            "unofficial community rewrite",
        ),
        "docs/work/v0.9.0-feature-freeze.md": ("status: APPROVED",),
        "docs/decisions/ADR-012-V090-BETA-COMPATIBILITY-CONTRACT.md": (
            "Status: `ACCEPTED`",
        ),
        "docs/provenance/v0.9.0-beta-hardening.md": (
            "status: APPROVED",
            'reviewer: "sunthemoon"',
            "copied_upstream_source_files: 0",
            "copied_upstream_binary_assets: 0",
            EXPECTED_COMMIT,
        ),
        "THIRD-PARTY-NOTICES.md": (
            "Just Enough Items 1.20.1 optional API/runtime",
            "15.56.0.205",
            "neither the API nor the runtime is embedded",
        ),
    }
    for relative, markers in records.items():
        value = _text(repository_root, relative, errors)
        if value and any(marker.casefold() not in value.casefold() for marker in markers):
            errors.append(f"v0.9.0 provenance marker is missing from {relative}")
    try:
        completed = subprocess.run(
            (
                "git",
                "-c",
                f"safe.directory={repository_root.as_posix()}",
                "-C",
                str(repository_root),
                "diff",
                "--name-only",
                EXPECTED_BASE,
                EXPECTED_COMMIT,
                "--",
                "src/main/resources/assets",
                "src/generated/resources/assets",
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/ElectrolyzerScreen.java",
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/LifeSupportHud.java",
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/RocketEntityRenderer.java",
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/RocketFlightScreen.java",
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/SatelliteTerminalScreen.java",
            ),
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        if completed.stdout.strip():
            errors.append(
                "v0.9.0 feature freeze unexpectedly changes core visual or asset files"
            )
    except (OSError, subprocess.CalledProcessError, UnicodeError) as exc:
        errors.append(f"cannot verify the v0.9.0 frozen asset diff: {exc}")
    return len(errors) == before


def _validate_artifact(
    repository_root: Path, artifact: Path | None, errors: list[str]
) -> dict[str, Any]:
    summary = _load(
        repository_root,
        (EVIDENCE_ROOT / "artifact/artifact-summary.json").as_posix(),
        errors,
    )
    try:
        manifest = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            MAX_JSON_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return summary
    metadata, metadata_errors = load_artifact_metadata(manifest)
    errors.extend(f"v0.9.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    repeated = summary.get("repeated_clean_builds")
    server_copy = summary.get("packaged_server_copy")
    content = summary.get("content_manifest")
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
        or repeated
        != {
            "count": 2,
            "byte_identical": True,
            "main_sha256_values": [
                EXPECTED_ARTIFACT_SHA256,
                EXPECTED_ARTIFACT_SHA256,
            ],
        }
        or server_copy != {"byte_equal": True, "sha256": EXPECTED_ARTIFACT_SHA256}
        or content
        != {
            "path": (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            "sha256": EXPECTED_MANIFEST_SHA256,
            "entry_count": 758,
        }
        or _sha256(manifest) != EXPECTED_MANIFEST_SHA256
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != EXPECTED_ARTIFACT_SHA256
        or metadata.manifest.get("entry_count") != 758
    ):
        errors.append("v0.9.0 artifact evidence does not bind the repeated packaged JAR")
    if artifact is not None:
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if actual.is_symlink() or not actual.is_file():
                errors.append("artifact is not a regular file")
            elif actual.stat().st_size != EXPECTED_ARTIFACT_BYTES:
                errors.append("artifact byte size differs from the v0.9.0 evidence")
            elif _sha256(actual) != EXPECTED_ARTIFACT_SHA256:
                errors.append("artifact SHA-256 differs from the v0.9.0 evidence")
    return summary


def _validate_automated(repository_root: Path, errors: list[str]) -> dict[str, Any]:
    summary = _load(
        repository_root,
        (EVIDENCE_ROOT / "automated/summary.json").as_posix(),
        errors,
    )
    results = summary.get("results")
    if not _identity(summary) or not isinstance(results, dict):
        errors.append("v0.9.0 automated summary identity is incomplete")
        return summary
    exact = {
        "junit": {"passed": 273, "failed": 0, "skipped": 0, "result": "PASS"},
        "gametest": {"passed": 44, "failed": 0, "result": "PASS"},
        "clean_builds": {"runs": 2, "byte_identical": True, "result": "PASS"},
        "jar_audit": {"entries": 758, "findings": 0, "result": "PASS"},
        "client_boundary": {"findings": 0, "result": "PASS"},
        "datagen": {"git_diff_clean": True, "result": "PASS"},
        "packaged_server": {"cycles": 2, "result": "PASS"},
        "migration": {"roots": 5, "restart_current": True, "result": "PASS"},
        "forced_stop": {"single_authority": True, "result": "PASS"},
        "compatibility": {"passed": 4, "required": 4, "result": "PASS"},
        "continuation": {
            "machine_restart": True,
            "round_trips": 20,
            "restart_cases": 8,
            "result": "PASS",
        },
        "resource_audit": {"errors": 0, "result": "PASS"},
        "soak": {"minimum_seconds": 7200, "clients": 4, "result": "PASS"},
    }
    for key, required in exact.items():
        record = results.get(key)
        if not isinstance(record, dict) or any(record.get(k) != v for k, v in required.items()):
            errors.append(f"v0.9.0 automated result is incomplete: {key}")
    repository = results.get("repository")
    python = results.get("python")
    if (
        not isinstance(repository, dict)
        or repository.get("result") != "PASS"
        or repository.get("failed") != 0
        or repository.get("warnings") != 0
        or not isinstance(repository.get("passed"), int)
        or repository["passed"] < 43
    ):
        errors.append("v0.9.0 strict repository result is incomplete")
    if (
        not isinstance(python, dict)
        or python.get("result") != "PASS"
        or python.get("failed") != 0
        or not isinstance(python.get("passed"), int)
        or python["passed"] < 150
    ):
        errors.append("v0.9.0 Python suite result is incomplete")
    gametest = _text(
        repository_root,
        (EVIDENCE_ROOT / "automated/gametest.txt").as_posix(),
        errors,
    )
    if gametest and (
        "44 tests are now running!" not in gametest
        or "All 44 required tests passed" not in gametest
    ):
        errors.append("v0.9.0 GameTest extract does not prove 44/44")
    return summary


def _validate_dedicated(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    summary = _load(
        repository_root,
        (EVIDENCE_ROOT / "dedicated-server/summary.json").as_posix(),
        errors,
    )
    cycles = summary.get("cycles")
    world = summary.get("world")
    if (
        summary.get("schema_version") != 2
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("server_artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("mod_version") != EXPECTED_BUILD
        or summary.get("forge") != "47.4.10"
        or summary.get("java") != "17.0.8"
        or not isinstance(cycles, list)
        or len(cycles) != 2
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
        or any(cycle.get("project_error_count") != 0 for cycle in cycles)
        or not isinstance(world, dict)
        or world.get("same_world_verified") is not True
    ):
        errors.append("v0.9.0 packaged-server lifecycle evidence is incomplete")
    for name in ("first-start.txt", "restart.txt"):
        value = _text(
            repository_root,
            (EVIDENCE_ROOT / f"dedicated-server/{name}").as_posix(),
            errors,
        )
        if value and EXPECTED_BUILD not in value:
            errors.append(f"v0.9.0 dedicated extract lacks build identity: {name}")
    return len(errors) == before


def _validate_migration(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    summary = _load(
        repository_root, (EVIDENCE_ROOT / "migration/summary.json").as_posix(), errors
    )
    manifest_relative = EVIDENCE_ROOT / "migration/backup-manifest.json"
    manifest = _load(repository_root, manifest_relative.as_posix(), errors)
    backup = summary.get("backup")
    seeded = summary.get("seeded_files")
    cycles = summary.get("cycles")
    files = manifest.get("files")
    try:
        manifest_path = _regular_file(
            repository_root, manifest_relative.as_posix(), MAX_JSON_BYTES
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        manifest_path = None
    source_hashes = {
        item.get("file"): item.get("sha256")
        for item in files
        if isinstance(item, dict)
    } if isinstance(files, list) else {}
    seeded_hashes = {
        name: value.get("source_sha256")
        for name, value in seeded.items()
        if isinstance(value, dict)
    } if isinstance(seeded, dict) else {}
    if (
        summary.get("schema_version") != 1
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("mod_version") != EXPECTED_BUILD
        or summary.get("tested_commit") != EXPECTED_COMMIT
        or summary.get("restart_current") is not True
        or summary.get("operator_report_operational") is not True
        or summary.get("diagnostics") != ["ARCE-BETA-1002", "ARCE-BETA-1001"]
        or not isinstance(backup, dict)
        or backup.get("file_count") != 5
        or manifest_path is None
        or backup.get("manifest_sha256") != _sha256(manifest_path)
        or manifest.get("manifestSchema") != 1
        or manifest.get("sourceSchema") != 1
        or manifest.get("targetSchema") != 2
        or len(source_hashes) != 5
        or source_hashes != seeded_hashes
        or not isinstance(cycles, list)
        or len(cycles) != 2
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
    ):
        errors.append("v0.9.0 packaged migration evidence is incomplete")
    return len(errors) == before


def _validate_recovery(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    summary = _load(
        repository_root, (EVIDENCE_ROOT / "recovery/summary.json").as_posix(), errors
    )
    ledger = _load(
        repository_root,
        (EVIDENCE_ROOT / "recovery/recovery-ledger.json").as_posix(),
        errors,
    )
    recovery = ledger.get("recovery")
    if (
        not _identity(summary, commit_key="tested_commit")
        or summary.get("checkpoint") != "DESTINATION_SPAWNED"
        or summary.get("forced_exit_code") != 1
        or summary.get("graceful_stop_command_sent_before_kill") is not False
        or summary.get("durable_save_before_kill") is not True
        or summary.get("same_world_restart") is not True
        or summary.get("schema_after_restart") != 2
        or summary.get("recovery_status") != "RECOVERED"
        or summary.get("single_authority") is not True
        or summary.get("material_and_inventory_conserved") is not True
        or summary.get("critical_or_high_findings") != 0
        or ledger.get("single_authority_after_restart") is not True
        or ledger.get("container_inventory_conserved") is not True
        or ledger.get("exact_disassembly") is not True
        or not isinstance(recovery, dict)
        or recovery.get("action") != "REMOVE_SOURCE_KEEP_DESTINATION"
        or recovery.get("status") != "RECOVERED"
    ):
        errors.append("v0.9.0 forced-stop recovery evidence is incomplete")
    return len(errors) == before


def _validate_continuation(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    bundle = _load(
        repository_root,
        (EVIDENCE_ROOT / "continuation/summary.json").as_posix(),
        errors,
    )
    machine_relative = EVIDENCE_ROOT / "continuation/machine/summary.json"
    flight_relative = EVIDENCE_ROOT / "continuation/flight/summary.json"
    machine = _load(
        repository_root,
        machine_relative.as_posix(),
        errors,
    )
    flight = _load(
        repository_root,
        flight_relative.as_posix(),
        errors,
    )
    machine_before = machine.get("before_restart")
    machine_after = machine.get("after_restart")
    round_trips = flight.get("round_trips")
    lineage = bundle.get("server_lineage")
    bundle_machine = bundle.get("machine")
    bundle_flight = bundle.get("flight")
    baseline = _load(
        repository_root,
        (EVIDENCE_ROOT / "dedicated-server/summary.json").as_posix(),
        errors,
    )
    try:
        machine_path = _regular_file(
            repository_root, machine_relative.as_posix(), MAX_JSON_BYTES
        )
        flight_path = _regular_file(
            repository_root, flight_relative.as_posix(), MAX_JSON_BYTES
        )
        migration_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "migration/summary.json").as_posix(),
            MAX_JSON_BYTES,
        )
        soak_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "performance/summary.json").as_posix(),
            MAX_JSON_BYTES,
        )
        baseline_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "dedicated-server/summary.json").as_posix(),
            MAX_JSON_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        machine_path = flight_path = migration_path = soak_path = baseline_path = None
    if (
        not _identity(bundle)
        or bundle.get("result") != "PASS"
        or not isinstance(lineage, dict)
        or lineage.get("world_identity_marker_sha256")
        != "7d262fe7086de63fcafb8fc0345ba530f4810ea68a4639d7ac0ed0bf58b5e0c1"
        or baseline_path is None
        or lineage.get("baseline_summary_sha256") != _sha256(baseline_path)
        or lineage.get("baseline_session_id") != baseline.get("session_id")
        or not isinstance(bundle_machine, dict)
        or machine_path is None
        or bundle_machine.get("summary_sha256") != _sha256(machine_path)
        or bundle_machine.get("atomic_completion_verified") is not True
        or not isinstance(bundle_flight, dict)
        or flight_path is None
        or bundle_flight.get("summary_sha256") != _sha256(flight_path)
        or bundle_flight.get("round_trips") != 20
        or bundle_flight.get("restart_cases") != 8
        or migration_path is None
        or lineage.get("migration_summary_sha256") != _sha256(migration_path)
        or soak_path is None
        or lineage.get("soak_summary_sha256") != _sha256(soak_path)
        or machine.get("schema_version") != 1
        or machine.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or machine.get("artifact_version") != EXPECTED_BUILD
        or machine.get("baseline_session_id") != baseline.get("session_id")
        or machine.get("same_world_verified") is not True
        or machine.get("paused_state_preserved") is not True
        or machine.get("atomic_completion_verified") is not True
        or not isinstance(machine_before, dict)
        or machine_before.get("exit_code") != 0
        or not isinstance(machine_after, dict)
        or machine_after.get("exit_code") != 0
        or flight.get("schema_version") != 1
        or flight.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or flight.get("artifact_version") != EXPECTED_BUILD
        or flight.get("baseline_session_id") != baseline.get("session_id")
        or flight.get("same_world_verified") is not True
        or flight.get("single_authority_after_each_leg") is not True
        or flight.get("critical_or_high_findings") != 0
        or flight.get("restart_matrix_cases") != 8
        or flight.get("restart_matrix_passed") != 8
        or not isinstance(round_trips, dict)
        or round_trips.get("round_trips") != 20
        or round_trips.get("legs") != 40
        or round_trips.get("exact_fuel_debits") is not True
        or round_trips.get("material_conserved_after_disassembly") is not True
        or round_trips.get("container_inventory_conserved") is not True
    ):
        errors.append("v0.9.0 migrated-world gameplay continuation is incomplete")
    return len(errors) == before


def _validate_compatibility(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    summary = _load(
        repository_root,
        (EVIDENCE_ROOT / "compatibility/summary.json").as_posix(),
        errors,
    )
    matrix = summary.get("matrix")
    server = summary.get("server")
    expected = {
        ("47.4.10", "15.56.0.205", 1),
        ("47.4.10", "absent", 0),
        ("47.4.23", "15.56.0.205", 1),
        ("47.4.23", "absent", 0),
    }
    observed = set()
    if isinstance(matrix, list):
        for item in matrix:
            if isinstance(item, dict):
                observed.add((item.get("forge"), item.get("jei"), item.get("jei_recipe_count")))
                if (
                    item.get("result") != "PASS"
                    or item.get("connected_to_exact_packaged_server") is not True
                    or item.get("unknown_recipe_category_count") != 0
                    or item.get("project_error_or_fatal_count") != 0
                ):
                    errors.append("v0.9.0 compatibility cell contains a failed assertion")
    if (
        not _identity(summary)
        or summary.get("cells_required") != 4
        or summary.get("cells_passed") != 4
        or summary.get("result") != "PASS"
        or observed != expected
        or not isinstance(server, dict)
        or server.get("joins") != 4
        or server.get("leaves") != 4
        or server.get("clean_save_and_stop") is not True
        or server.get("project_error_or_fatal_count") != 0
    ):
        errors.append("v0.9.0 Forge/JEI compatibility matrix is incomplete")
    return len(errors) == before


def _validate_resources(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    relative = EVIDENCE_ROOT / "resources/summary.json"
    summary = _load(repository_root, relative.as_posix(), errors)
    try:
        path = _regular_file(repository_root, relative.as_posix(), MAX_JSON_BYTES)
    except EvidenceError as exc:
        errors.append(str(exc))
        path = None
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("result") != "PASS"
        or summary.get("errors") != 0
        or summary.get("resource_files") != 220
        or summary.get("json_files") != 64
        or summary.get("en_us_keys") != 231
        or summary.get("zh_cn_keys") != 231
        or summary.get("parity") is not True
        or summary.get("asset_references_checked") != 67
        or summary.get("missing_or_case_mismatched_assets") != 0
        or summary.get("textual_status_surfaces") != 4
        or path is None
        or _sha256(path) != EXPECTED_RESOURCE_SHA256
    ):
        errors.append("v0.9.0 localization/resource evidence is incomplete")
    return len(errors) == before


def _validate_security(repository_root: Path, errors: list[str]) -> dict[str, Any]:
    summary = _load(
        repository_root,
        (EVIDENCE_ROOT / "automated/security-audit.json").as_posix(),
        errors,
    )
    cases = summary.get("cases")
    required_cases = {
        "malformed_and_noncanonical_flight_intent_frames",
        "flight_intent_authority_distance_state_and_loaded_chunk",
        "flight_intent_replay_and_rate_limit",
        "station_and_satellite_non_owner_access",
        "duplicate_satellite_claim",
        "future_malformed_and_oversized_managed_saved_data",
        "partial_multi_root_migration_commit",
        "forced_stop_after_destination_spawn",
        "maximum_rocket_room_station_and_mission_limits",
        "satellite_and_transfer_chunk_tickets",
        "optional_jei_and_common_server_side_boundary",
        "artifact_credentials_license_and_provenance",
    }
    observed_cases = {
        case.get("case") for case in cases if isinstance(case, dict)
    } if isinstance(cases, list) else set()
    if (
        not _identity(summary)
        or summary.get("result") != "PASS"
        or summary.get("critical_findings") != 0
        or summary.get("high_findings") != 0
        or summary.get("open_findings") != []
        or not isinstance(cases, list)
        or len(cases) != 12
        or observed_cases != required_cases
        or any(not isinstance(case, dict) or case.get("result") != "PASS" for case in cases)
    ):
        errors.append("v0.9.0 security audit is incomplete")
    return summary


def _csv_rows(
    repository_root: Path,
    relative: str,
    expected_header: list[str],
    errors: list[str],
) -> list[dict[str, str]]:
    value = _text(repository_root, relative, errors)
    if not value:
        return []
    reader = csv.DictReader(value.splitlines())
    if reader.fieldnames != expected_header:
        errors.append(f"v0.9.0 CSV header is invalid: {relative}")
        return []
    try:
        return list(reader)
    except csv.Error as exc:
        errors.append(f"invalid CSV {relative}: {exc}")
        return []


def _validate_performance(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    summary = _load(
        repository_root, (EVIDENCE_ROOT / "performance/summary.json").as_posix(), errors
    )
    combined = summary.get("combined")
    soak = summary.get("soak")
    restart = summary.get("restart")
    migration_backup = summary.get("migration_backup")
    mission_batch = combined.get("mission_batch") if isinstance(combined, dict) else None
    metrics = _csv_rows(
        repository_root,
        (EVIDENCE_ROOT / "performance/metrics.csv").as_posix(),
        [
            "elapsed_seconds",
            "tick_ms",
            "tps",
            "rss_bytes",
            "process_cpu_seconds",
            "normalized_cpu_percent",
            "old_generation_percent",
            "young_gc_count",
            "full_gc_count",
            "gc_time_seconds",
        ],
        errors,
    )
    probes = _csv_rows(
        repository_root,
        (EVIDENCE_ROOT / "performance/client-probes.csv").as_posix(),
        ["elapsed_seconds", "client_id", "latency_ms"],
        errors,
    )
    try:
        baseline_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "dedicated-server/summary.json").as_posix(),
            MAX_JSON_BYTES,
        )
        migration_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "migration/summary.json").as_posix(),
            MAX_JSON_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        baseline_path = migration_path = None
    duration = soak.get("duration_seconds", 0) if isinstance(soak, dict) else 0
    tick = soak.get("tick_ms") if isinstance(soak, dict) else None
    rss = soak.get("rss") if isinstance(soak, dict) else None
    old = soak.get("old_generation") if isinstance(soak, dict) else None
    tick_maximum = tick.get("maximum", 51) if isinstance(tick, dict) else 51
    rss_growth = rss.get("growth_analysis") if isinstance(rss, dict) else None
    old_growth = old.get("growth_analysis") if isinstance(old, dict) else None
    try:
        duration_value = float(duration)
        tick_maximum_value = float(tick_maximum)
    except (TypeError, ValueError):
        duration_value = 0.0
        tick_maximum_value = 51.0
    client_counts: dict[str, int] = {}
    for row in probes:
        client_id = row.get("client_id", "")
        client_counts[client_id] = client_counts.get(client_id, 0) + 1
    if (
        summary.get("schema_version") != 1
        or summary.get("tested_commit") != EXPECTED_COMMIT
        or summary.get("artifact_version") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or baseline_path is None
        or summary.get("baseline_summary_sha256") != _sha256(baseline_path)
        or migration_path is None
        or summary.get("migration_summary_sha256") != _sha256(migration_path)
        or summary.get("passed") is not True
        or summary.get("blocking_log_findings") != 0
        or summary.get("critical_or_high_findings") != 0
        or not isinstance(migration_backup, dict)
        or migration_backup.get("file_count") != 5
        or not isinstance(combined, dict)
        or combined.get("rocket_blocks") != 2048
        or combined.get("vent_count") != 16
        or combined.get("station_count") != 10
        or combined.get("exact_maximum_counts") is not True
        or not isinstance(mission_batch, dict)
        or mission_batch.get("created") != 100
        or mission_batch.get("rejected") != 0
        or mission_batch.get("scheduler") != "deadline_queue"
        or not isinstance(soak, dict)
        or duration_value < 7200
        or soak.get("minimum_duration_seconds") != 7200
        or soak.get("simulation") != "four_concurrent_minecraft_status_clients"
        or soak.get("client_count") != 4
        or soak.get("client_probe_failures") != 0
        or soak.get("maximum_ticket_count") != 0
        or not isinstance(soak.get("vent_active_checks"), int)
        or soak["vent_active_checks"] < 240
        or soak.get("vent_activity_failures") != 0
        or soak.get("budgets_passed") is not True
        or not isinstance(tick, dict)
        or tick_maximum_value > 50
        or not isinstance(rss_growth, dict)
        or rss_growth.get("sustained_growth") is not False
        or not isinstance(old_growth, dict)
        or old_growth.get("sustained_growth") is not False
        or not isinstance(restart, dict)
        or restart.get("station_count") != 10
        or restart.get("vent_count") != 16
        or restart.get("same_authority") is not True
        or len(metrics) < 240
        or len(probes) < 1920
        or set(client_counts) != {"1", "2", "3", "4"}
        or any(count < 480 for count in client_counts.values())
    ):
        errors.append("v0.9.0 two-hour combined-soak evidence is incomplete")
    return len(errors) == before


def _validate_owner(repository_root: Path, errors: list[str]) -> tuple[dict[str, Any], bool]:
    before = len(errors)
    attestation = _load(
        repository_root,
        (EVIDENCE_ROOT / "manual/owner-attestation.json").as_posix(),
        errors,
    )
    approvals = attestation.get("approvals")
    basis = attestation.get("acceptance_basis")
    visual = attestation.get("visual_baseline")
    screenshots = visual.get("screenshots") if isinstance(visual, dict) else None
    expected_screenshots = {
        "docs/releases/v0.8.0/evidence/manual/final-candidate-client-a-multiplayer.png": (
            225456,
            "4e60dd5487c94d6e17056133f3f03b28a5bae37b2c71be43b017911829fca654",
        ),
        "docs/releases/v0.8.0/evidence/manual/final-candidate-client-b-multiplayer.png": (
            225319,
            "78cbc3290899d1478743e63e9db0135936d0872a534ed810650e542bb2514f0b",
        ),
    }
    observed: dict[str, tuple[int, str]] = {}
    if isinstance(screenshots, list):
        for item in screenshots:
            if not isinstance(item, dict):
                continue
            path = item.get("path")
            if isinstance(path, str) and isinstance(item.get("bytes"), int) and isinstance(item.get("sha256"), str):
                observed[path] = (item["bytes"], item["sha256"])
                try:
                    screenshot = _regular_file(repository_root, path, MAX_EVIDENCE_BYTES)
                except EvidenceError as exc:
                    errors.append(str(exc))
                    continue
                if screenshot.stat().st_size != item["bytes"] or _sha256(screenshot) != item["sha256"]:
                    errors.append(f"v0.9.0 visual baseline checksum mismatch: {path}")
    decision = _text(repository_root, VISUAL_DECISION.as_posix(), errors)
    approved = (
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
        and isinstance(visual, dict)
        and visual.get("core_asset_or_screen_changes") == 0
        and visual.get("source_version") == "v0.8.0"
        and observed == expected_screenshots
        and "Status: `ACCEPTED`" in decision
        and "Expires: `v1.0.0`" in decision
    )
    if not approved:
        errors.append("v0.9.0 owner G0/G8/G9 attestation is incomplete or unbound")
    return attestation, approved and len(errors) == before


def _validate_docs(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    for name in sorted(REQUIRED_RELEASE_DOCS):
        relative = (RELEASE_ROOT / name).as_posix()
        value = _text(repository_root, relative, errors)
        if name != "checksums.txt" and value and MACHINE_PATH.search(value):
            errors.append(f"v0.9.0 release document contains a machine path: {name}")
    for item in (repository_root / EVIDENCE_ROOT).rglob("*"):
        if not item.is_file() or item.suffix.lower() == ".png":
            continue
        try:
            value = item.read_text(encoding="utf-8", errors="strict")
        except UnicodeError:
            errors.append(f"v0.9.0 evidence is not strict UTF-8: {item.name}")
            continue
        if MACHINE_PATH.search(value):
            errors.append(
                "v0.9.0 evidence contains a machine path: "
                + item.relative_to(repository_root).as_posix()
            )
    return len(errors) == before


def _validate_checksums(
    repository_root: Path, artifact_summary: dict[str, Any], errors: list[str]
) -> bool:
    before = len(errors)
    value = _text(repository_root, (RELEASE_ROOT / "checksums.txt").as_posix(), errors)
    recorded: dict[str, str] = {}
    for line_number, line in enumerate(value.splitlines(), start=1):
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or SHA256.fullmatch(parts[0]) is None:
            errors.append(f"v0.9.0 checksums line {line_number} is invalid")
            continue
        try:
            relative = _safe_relative(parts[1].strip()).as_posix()
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if relative in recorded:
            errors.append(f"v0.9.0 checksums repeats {relative}")
        recorded[relative] = parts[0]
    evidence_files = {
        item.relative_to(repository_root).as_posix()
        for item in (repository_root / EVIDENCE_ROOT).rglob("*")
        if item.is_file()
    }
    main = artifact_summary.get("main_jar", {})
    artifact_path = main.get("path") if isinstance(main, dict) else None
    expected = evidence_files | ({artifact_path} if isinstance(artifact_path, str) else set())
    if set(recorded) != expected:
        errors.append("v0.9.0 checksums inventory is incomplete or contains extras")
    for relative in sorted(evidence_files):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_EVIDENCE_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.9.0 checksum mismatch: {relative}")
    if isinstance(artifact_path, str) and recorded.get(artifact_path) != EXPECTED_ARTIFACT_SHA256:
        errors.append("v0.9.0 checksums omit or change the candidate artifact binding")
    return len(errors) == before


def _validate_post_merge(repository_root: Path, errors: list[str]) -> dict[str, Any]:
    relative = EVIDENCE_ROOT / "artifact/post-merge-reproduction.json"
    if not (repository_root / relative).exists():
        return {}
    record = _load(repository_root, relative.as_posix(), errors)
    main = record.get("main_jar")
    manifest = record.get("content_manifest")
    checks = record.get("pull_request_checks")
    merge_checks = record.get("merge_checks")
    tests = record.get("unit_tests")
    release = record.get("github_prerelease")
    if (
        record.get("schema_version") != 1
        or record.get("version") != EXPECTED_VERSION
        or record.get("build") != EXPECTED_BUILD
        or record.get("tested_implementation_commit") != EXPECTED_COMMIT
        or GIT_SHA.fullmatch(str(record.get("reviewed_head_commit", ""))) is None
        or GIT_SHA.fullmatch(str(record.get("merge_commit", ""))) is None
        or record.get("pull_request")
        != "https://github.com/sunthemoon/AdvancedRocketry-Community/pull/13"
        or not _valid_date(record.get("reproduced_at"))
        or record.get("build_result") != "PASS"
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
            "entry_count": 758,
            "candidate_sha256": EXPECTED_MANIFEST_SHA256,
            "reproduced_sha256": EXPECTED_MANIFEST_SHA256,
        }
        or tests != {"total": 273, "passed": 273, "failures": 0, "errors": 0, "skipped": 0}
        or not isinstance(checks, dict)
        or checks.get("result") != "4/4_PASS"
        or not isinstance(checks.get("checks"), list)
        or len(checks["checks"]) != 4
        or re.fullmatch(
            r"https://github\.com/sunthemoon/AdvancedRocketry-Community/actions/runs/\d+",
            str(checks.get("forge_ci", "")),
        )
        is None
        or re.fullmatch(
            r"https://github\.com/sunthemoon/AdvancedRocketry-Community/actions/runs/\d+",
            str(checks.get("governance_ci", "")),
        )
        is None
        or not isinstance(merge_checks, dict)
        or merge_checks.get("result") != "4/4_PASS"
        or merge_checks.get("head_commit") != record.get("merge_commit")
        or not isinstance(merge_checks.get("checks"), list)
        or len(merge_checks["checks"]) != 4
        or re.fullmatch(
            r"https://github\.com/sunthemoon/AdvancedRocketry-Community/actions/runs/\d+",
            str(merge_checks.get("forge_ci", "")),
        )
        is None
        or re.fullmatch(
            r"https://github\.com/sunthemoon/AdvancedRocketry-Community/actions/runs/\d+",
            str(merge_checks.get("governance_ci", "")),
        )
        is None
        or not isinstance(release, dict)
        or release.get("tag") != "v0.9.0-beta.1"
        or release.get("prerelease") is not True
        or release.get("url")
        != "https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1"
        or release.get("asset_name") != EXPECTED_ARTIFACT
        or release.get("asset_bytes") != EXPECTED_ARTIFACT_BYTES
        or release.get("asset_sha256") != EXPECTED_ARTIFACT_SHA256
    ):
        errors.append("v0.9.0 post-merge reproduction or pre-release record is incomplete")
        return {}
    return record


def validate_v090_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
    *,
    require_approved: bool = False,
) -> tuple[list[str], dict[str, Any]]:
    """Return validation errors and v0.9.0 Gate-readiness details."""
    repository_root = repository_root.resolve()
    errors: list[str] = []

    provenance_ready = _validate_provenance(repository_root, errors)
    before = len(errors)
    artifact_summary = _validate_artifact(repository_root, artifact, errors)
    artifact_ready = bool(artifact_summary) and len(errors) == before
    before = len(errors)
    automated = _validate_automated(repository_root, errors)
    automated_ready = bool(automated) and len(errors) == before
    before = len(errors)
    dedicated_ready = _validate_dedicated(repository_root, errors) and len(errors) == before
    before = len(errors)
    migration_ready = _validate_migration(repository_root, errors) and len(errors) == before
    before = len(errors)
    recovery_ready = _validate_recovery(repository_root, errors) and len(errors) == before
    before = len(errors)
    continuation_ready = _validate_continuation(repository_root, errors) and len(errors) == before
    before = len(errors)
    compatibility_ready = _validate_compatibility(repository_root, errors) and len(errors) == before
    before = len(errors)
    resource_ready = _validate_resources(repository_root, errors) and len(errors) == before
    before = len(errors)
    security = _validate_security(repository_root, errors)
    security_ready = bool(security) and len(errors) == before
    before = len(errors)
    performance_ready = _validate_performance(repository_root, errors) and len(errors) == before
    before = len(errors)
    attestation, human_approved = _validate_owner(repository_root, errors)
    human_approved = human_approved and len(errors) == before
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before
    before = len(errors)
    checksums_ready = _validate_checksums(repository_root, artifact_summary, errors)
    checksums_ready = checksums_ready and len(errors) == before
    before = len(errors)
    post_merge = _validate_post_merge(repository_root, errors)
    post_merge_ready = bool(post_merge) and len(errors) == before
    if require_approved and not human_approved:
        errors.append("v0.9.0 evidence lacks explicit G0/G8/G9 owner approval")

    details = {
        "artifact_sha256": EXPECTED_ARTIFACT_SHA256 if artifact_ready else "",
        "tested_implementation_commit": EXPECTED_COMMIT,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "data_ready": resource_ready and automated_ready,
        "automated_ready": automated_ready,
        "server_ready": dedicated_ready and compatibility_ready and continuation_ready,
        "persistence_ready": migration_ready
        and recovery_ready
        and continuation_ready
        and performance_ready,
        "authority_ready": security_ready and recovery_ready,
        "performance_ready": performance_ready,
        "client_ready": compatibility_ready and resource_ready and human_approved,
        "docs_ready": docs_ready and checksums_ready,
        "checksums_ready": checksums_ready,
        "human_approved": human_approved,
        "human_approved_at": attestation.get("approved_at") if attestation else None,
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
        "governance_ci": post_merge.get("pull_request_checks", {}).get("governance_ci")
        if post_merge_ready
        else None,
        "release_url": post_merge.get("github_prerelease", {}).get("url")
        if post_merge_ready
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
    errors, details = validate_v090_release_evidence(
        ROOT, artifact=args.artifact, require_approved=args.require_approved
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(
        "[PASS] v0.9.0 release evidence is complete: "
        + ", ".join(key for key, value in details.items() if value is True)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
