#!/usr/bin/env python3
"""Validate the bounded v0.8.0 research and satellite evidence bundle."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path
from typing import Any

if __package__:
    from .manage_v080_generated_manifest import verify as verify_generated_manifest
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
    from manage_v080_generated_manifest import verify as verify_generated_manifest
    from validate_release_checksums import load_artifact_metadata
    from validate_v020_release_evidence import (
        EvidenceError,
        _load_json,
        _regular_file,
        _safe_relative,
        _sha256,
    )


ROOT = Path(__file__).resolve().parents[1]
RELEASE_ROOT = Path("docs/releases/v0.8.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
PROVENANCE_RECORD = Path("docs/provenance/v0.8.0-progression-satellites.md")
GENERATED_RECORD = Path("docs/provenance/v0.8.0-generated-resources.json")
VISUAL_DECISION = Path("docs/decisions/ADR-011-V080-VISUAL-EVIDENCE-SEQUENCE.md")
EXPECTED_VERSION = "v0.8.0"
EXPECTED_BUILD = "1.20.1-0.8.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.8.0-dev.jar"
EXPECTED_ARTIFACT_SHA256 = (
    "0ce6c6bf9eb603f5973f35c19a47b295454a1f8c74ee74a6a99af3c2627a1937"
)
EXPECTED_ARTIFACT_BYTES = 1_166_061
EXPECTED_COMMIT = "a3b4192d37c524687a0a26bf12d075a8ec6c1e99"
EXPECTED_MANIFEST_SHA256 = (
    "47f26d36b218f44098794061696e8064afc3517dd413724b3742f5e7dca931de"
)
EXPECTED_GENERATED_SHA256 = (
    "122aa57c53a014ee890844738c7239f09fcc703f610251ce67ab8d58850b7a09"
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
GIT_SHA = re.compile(r"[0-9a-f]{40}")
MACHINE_PATH = re.compile(r"(?<![A-Za-z])[A-Za-z]:[\\/]|server-work[/\\]run-")
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_TEXT_BYTES = 512 * 1024
MAX_EVIDENCE_BYTES = 8 * 1024 * 1024


def _valid_date(value: object) -> bool:
    if not isinstance(value, str) or re.fullmatch(r"\d{4}-\d{2}-\d{2}", value) is None:
        return False
    try:
        dt.date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _record_list(value: object) -> bool:
    return isinstance(value, list) and all(isinstance(item, dict) for item in value)


def _validate_provenance(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
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
        "target_version: v0.8.0",
        "status: APPROVED",
        'reviewer: "sunthemoon"',
        'reviewed_at: "2026-09-03"',
        "copied_source_files: 0",
        "copied_binary_assets: 0",
        "generated_resources: 21",
        f"generated_manifest_sha256: {EXPECTED_GENERATED_SHA256}",
    }
    targets = generated.get("targets")
    if any(item not in provenance for item in required):
        errors.append("v0.8.0 provenance is not explicitly owner-approved")
    if (
        generated.get("schema_version") != 1
        or generated.get("target_version") != EXPECTED_VERSION
        or generated.get("status") != "COMMUNITY_AUTHORED_DATAGEN"
        or not isinstance(targets, list)
        or len(targets) != 21
        or _sha256(generated_path) != EXPECTED_GENERATED_SHA256
    ):
        errors.append("v0.8.0 generated-resource provenance is incomplete")
    return len(errors) == before


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
    errors.extend(f"v0.8.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    repeated = summary.get("repeated_clean_builds")
    server_copy = summary.get("packaged_server_copy")
    content = summary.get("content_manifest")
    if not all(isinstance(item, dict) for item in (main, repeated, server_copy, content)):
        errors.append("v0.8.0 artifact summary is missing required records")
        return summary
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or main != {
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
        or content != {
            "path": (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            "sha256": EXPECTED_MANIFEST_SHA256,
            "entry_count": 723,
        }
        or _sha256(manifest) != EXPECTED_MANIFEST_SHA256
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != EXPECTED_ARTIFACT_SHA256
        or metadata.manifest.get("entry_count") != 723
    ):
        errors.append("v0.8.0 artifact summary does not bind the repeated packaged JAR")
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
                errors.append("artifact SHA-256 differs from the v0.8.0 evidence")
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
        "python_targeted",
        "gametest",
        "datagen",
        "jar_audit",
        "client_boundary",
        "repository",
        "packaged_satellite",
        "multiplayer",
        "reconnect",
        "performance",
    }
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or not isinstance(results, dict)
        or not required_results.issubset(results)
        or not all(isinstance(results.get(key), dict) for key in required_results)
        or any(results[key].get("result") != "PASS" for key in required_results)
        or results["clean_builds"].get("runs") != 2
        or results["clean_builds"].get("byte_identical") is not True
        or results["junit"].get("passed") != 247
        or results["junit"].get("failed") != 0
        or results["junit"].get("skipped") != 0
        or results["python_targeted"].get("passed", 0) < 8
        or results["python_targeted"].get("failed") != 0
        or results["gametest"].get("passed") != 44
        or results["gametest"].get("failed") != 0
        or results["datagen"].get("files") != 21
        or results["datagen"].get("git_diff_clean") is not True
        or results["jar_audit"].get("entries") != 723
        or results["jar_audit"].get("findings") != 0
        or results["client_boundary"].get("findings") != 0
        or results["repository"].get("failed") != 0
        or results["repository"].get("warnings") != 0
        or results["packaged_satellite"].get("stress_missions") != 100
        or results["multiplayer"].get("simultaneous_clients") != 2
        or results["reconnect"].get("simultaneous_clients") != 2
        or not isinstance(security, dict)
        or security.get("client_final_satellite_decisions") != 0
        or security.get("server_revalidated_terminal_access") is not True
        or security.get("exact_once_claim") is not True
        or security.get("future_schema_fails_closed") is not True
        or security.get("permanent_satellite_chunk_tickets") != 0
        or authority.get("schema_version") != 1
        or authority.get("version") != EXPECTED_VERSION
        or authority.get("critical_or_high_findings") != 0
        or not _record_list(cases)
        or len(cases) < 10
        or any(case.get("result") != "PASS" for case in cases)
        or "All 44 required tests passed" not in gametest
        or "ARCE_SATELLITE_SCHEDULER completed=1 inspected=1 remaining=0" not in gametest
    ):
        errors.append("v0.8.0 automated or authority evidence is incomplete")
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
        or not _record_list(cycles)
        or [cycle.get("name") for cycle in cycles] != ["first-start", "restart"]
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
        or any(cycle.get("error_count") != 0 for cycle in cycles)
        or any(cycle.get("client_linkage_failure_count") != 0 for cycle in cycles)
        or summary.get("world", {}).get("same_world_verified") is not True
        or "Saved the game" not in first
        or "Saved the game" not in restart
    ):
        errors.append("v0.8.0 dedicated-server lifecycle is incomplete")
        return False
    return True


def _validate_satellite(repository_root: Path, errors: list[str]) -> bool:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "satellite-server/summary.json"
        )
        ledger = _load_json(
            repository_root, EVIDENCE_ROOT / "satellite-server/mission-ledger.json"
        )
        lifecycle = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "satellite-server/filtered-lifecycle.log").as_posix(),
            MAX_EVIDENCE_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    stress = summary.get("stress")
    missions = ledger.get("missions")
    passes = stress.get("scheduler_passes") if isinstance(stress, dict) else None
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or summary.get("same_world_verified") is not True
        or summary.get("restart_persistence_verified") is not True
        or summary.get("exact_once_claim_verified") is not True
        or summary.get("celestial_discoveries_verified") is not True
        or summary.get("owner_count") != 2
        or summary.get("chunk_tickets") != 0
        or summary.get("scheduler") != "deadline_queue"
        or not isinstance(stress, dict)
        or (stress.get("requested"), stress.get("created"), stress.get("rejected"))
        != (100, 100, 0)
        or not _record_list(passes)
        or [item.get("completed") for item in passes] != [32, 32, 32, 4]
        or any(item.get("inspected", 65) > 64 for item in passes)
        or passes[-1].get("remaining") != 0
        or not _record_list(missions)
        or len(missions) != 2
        or any(item.get("first_claim", {}).get("code") != "SUCCESS" for item in missions)
        or any(item.get("replayed_claim", {}).get("code") != "ALREADY_CLAIMED" for item in missions)
        or "ARCE_RELEASE_TEST_SATELLITE_BATCH requested=100 created=100 rejected=0" not in lifecycle
        or "ARCE_SATELLITE_SCHEDULER completed=32 inspected=32 remaining=68" not in lifecycle
        or MACHINE_PATH.search(lifecycle)
    ):
        errors.append("v0.8.0 packaged satellite, restart, or exact-once evidence is incomplete")
        return False
    return True


def _validate_multiplayer_cycle(
    repository_root: Path, directory: str, errors: list[str]
) -> bool:
    prefix = EVIDENCE_ROOT / directory
    try:
        summary = _load_json(repository_root, prefix / "summary.json")
        documents = [
            _regular_file(repository_root, (prefix / name).as_posix(), MAX_TEXT_BYTES)
            .read_text(encoding="utf-8", errors="replace")
            for name in ("server.txt", "client-a.txt", "client-b.txt")
        ]
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    clients = summary.get("clients")
    observations = summary.get("observations")
    authority = summary.get("authority")
    marker = summary.get("shared_marker")
    server, client_a, client_b = documents
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or not _record_list(clients)
        or [client.get("username") for client in clients] != ["ClientA", "PilotB"]
        or any(client.get("connected_to_modded_server") is not True for client in clients)
        or any(client.get("received_shared_marker") is not True for client in clients)
        or any(client.get("clean_shutdown") is not True for client in clients)
        or not isinstance(observations, dict)
        or observations.get("simultaneous_players") != 2
        or observations.get("same_server_marker_received_by_both") is not True
        or observations.get("client_linkage_failures") != 0
        or not isinstance(authority, dict)
        or authority.get("satellites") != 102
        or authority.get("missions") != 102
        or authority.get("chunk_tickets") != 0
        or not isinstance(marker, str)
        or not marker.startswith("ARCE_V080_G4_SHARED_STATE players=2 satellites=102 missions=102")
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
        or any(MACHINE_PATH.search(text) for text in documents)
    ):
        errors.append(f"v0.8.0 {directory} two-client evidence is incomplete")
        return False
    return True


def _validate_performance(repository_root: Path, errors: list[str]) -> bool:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "performance/summary.json")
        lifecycle = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "performance/scheduler-jfr.txt").as_posix(),
            MAX_TEXT_BYTES,
        ).read_text(encoding="utf-8", errors="replace")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    workload = summary.get("workload")
    ticks = summary.get("server_tick_workload_window")
    budgets = summary.get("budgets")
    heap = summary.get("heap")
    environment = summary.get("environment")
    scheduler_passes = workload.get("scheduler_passes") if isinstance(workload, dict) else None
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != EXPECTED_ARTIFACT_SHA256
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or summary.get("result") != "PASS"
        or not all(isinstance(item, dict) for item in (workload, ticks, budgets, heap, environment))
        or workload.get("logical_missions") != 100
        or workload.get("final_ready_missions") != 100
        or workload.get("scheduler") != "deadline_queue"
        or not _record_list(scheduler_passes)
        or [item.get("completed") for item in scheduler_passes]
        != [32, 32, 32, 4]
        or ticks.get("samples", 0) < 10
        or ticks.get("mean_ms", 51) >= 50
        or ticks.get("p95_ms", 51) >= 50
        or ticks.get("maximum_ms", 51) >= 50
        or budgets.get("maximum_completions_per_pass") != 32
        or budgets.get("maximum_queue_inspections_per_pass") != 64
        or budgets.get("observed_maximum_completions_per_pass", 33) > 32
        or budgets.get("observed_maximum_queue_inspections_per_pass", 65) > 64
        or budgets.get("permanent_chunk_tickets") != 0
        or budgets.get("chunk_generation_duration_nanos") != 0.0
        or heap.get("gc_overhead_percent", 101) >= 5
        or environment.get("java") != "Microsoft OpenJDK 17.0.8+7-LTS"
        or "ARCE_SATELLITE_SCHEDULER completed=32 inspected=32 remaining=68" not in lifecycle
        or "JFR profiling stopped" not in lifecycle
        or MACHINE_PATH.search(lifecycle)
    ):
        errors.append("v0.8.0 scheduler performance evidence is incomplete or over budget")
        return False
    return True


def _validate_owner(repository_root: Path, errors: list[str]) -> tuple[dict[str, Any], bool]:
    try:
        attestation = _load_json(
            repository_root, EVIDENCE_ROOT / "manual/owner-attestation.json"
        )
        final = _load_json(
            repository_root, EVIDENCE_ROOT / "manual/final-candidate-metadata.json"
        )
        pre = _load_json(
            repository_root, EVIDENCE_ROOT / "manual/pre-candidate/metadata.json"
        )
        decision = _regular_file(
            repository_root, VISUAL_DECISION.as_posix(), MAX_TEXT_BYTES
        ).read_text(encoding="utf-8", errors="strict")
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return {}, False
    final_files = final.get("files")
    if not _record_list(final_files):
        final_files = []
    pre_files = pre.get("files")
    if not isinstance(pre_files, list) or not all(isinstance(item, str) for item in pre_files):
        pre_files = []
    media_ready = bool(
        final.get("schema_version") == 1
        and final.get("build") == EXPECTED_BUILD
        and final.get("artifact_sha256") == EXPECTED_ARTIFACT_SHA256
        and final.get("tested_implementation_commit") == EXPECTED_COMMIT
        and len(final_files) == 2
        and pre.get("schema_version") == 1
        and pre.get("evidence_status") == "pre_candidate_visual_check"
        and len(pre_files) == 7
    )
    for record in final_files:
        try:
            path = _regular_file(
                repository_root,
                (EVIDENCE_ROOT / "manual" / str(record.get("path", ""))).as_posix(),
                MAX_EVIDENCE_BYTES,
            )
        except EvidenceError as exc:
            errors.append(str(exc))
            media_ready = False
            continue
        if path.stat().st_size != record.get("bytes") or _sha256(path) != record.get("sha256"):
            errors.append("v0.8.0 final-candidate screenshot metadata mismatch")
            media_ready = False
    for name in pre_files:
        try:
            _regular_file(
                repository_root,
                (EVIDENCE_ROOT / "manual/pre-candidate" / str(name)).as_posix(),
                MAX_EVIDENCE_BYTES,
            )
        except EvidenceError as exc:
            errors.append(str(exc))
            media_ready = False
    approvals = attestation.get("approvals")
    basis = attestation.get("acceptance_basis")
    limitations = attestation.get("limitations_accepted")
    human_approved = bool(
        attestation.get("schema_version") == 1
        and attestation.get("version") == EXPECTED_VERSION
        and attestation.get("approved_by") in AUTHORIZED_REVIEWERS
        and _valid_date(attestation.get("approved_at"))
        and approvals == {
            "G0_provenance": "PASS",
            "G8_visible_client_acceptance": "PASS",
            "G9_release_acceptance": "PASS",
        }
        and isinstance(basis, dict)
        and basis.get("artifact_sha256") == EXPECTED_ARTIFACT_SHA256
        and basis.get("tested_implementation_commit") == EXPECTED_COMMIT
        and basis.get("visual_decision") == VISUAL_DECISION.as_posix()
        and isinstance(limitations, list)
        and any("rather than continuous video" in item for item in limitations)
        and all(
            marker in decision
            for marker in (
                "**Status:** Accepted",
                "**Owner:** `sunthemoon`",
                "**Applies to:** v0.8.0 only",
                "**Expires:** when v0.9.0 validation begins",
                "## Risk and user impact",
                "## Mitigation",
                "## Recollection condition",
                "## Automatic failure reminder",
            )
        )
    )
    if not media_ready or not human_approved:
        errors.append("v0.8.0 G0/G8/G9 owner attestation or visual evidence is incomplete")
    return attestation, media_ready and human_approved


def _validate_docs(repository_root: Path, errors: list[str]) -> bool:
    before = len(errors)
    for name in sorted(REQUIRED_RELEASE_DOCS):
        try:
            path = _regular_file(
                repository_root, (RELEASE_ROOT / name).as_posix(), MAX_TEXT_BYTES
            )
            if name != "checksums.txt":
                text = path.read_text(encoding="utf-8", errors="strict")
                if MACHINE_PATH.search(text):
                    errors.append(f"v0.8.0 release document contains a machine path: {name}")
        except (EvidenceError, UnicodeError) as exc:
            errors.append(str(exc))
    return len(errors) == before


def _validate_checksums(
    repository_root: Path, artifact_summary: dict[str, Any], errors: list[str]
) -> bool:
    before = len(errors)
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
            errors.append(f"v0.8.0 checksums line {line_number} is invalid")
            continue
        try:
            relative = _safe_relative(parts[1].strip()).as_posix()
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if relative in recorded:
            errors.append(f"v0.8.0 checksums repeats {relative}")
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
        errors.append("v0.8.0 artifact checksum path is missing")
        expected.discard(None)
    if set(recorded) != expected:
        errors.append("v0.8.0 checksums inventory is incomplete or contains extras")
    for relative in sorted(evidence_files):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_EVIDENCE_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.8.0 checksum mismatch: {relative}")
    if recorded.get(artifact_path) != main.get("sha256"):
        errors.append("v0.8.0 checksums omit or change the artifact binding")
    return len(errors) == before


def _validate_post_merge(repository_root: Path, errors: list[str]) -> dict[str, Any]:
    relative = EVIDENCE_ROOT / "artifact/post-merge-reproduction.json"
    path = repository_root / relative
    if not path.exists():
        return {}
    try:
        record = _load_json(repository_root, relative)
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    main = record.get("main_jar")
    manifest = record.get("content_manifest")
    checks = record.get("pull_request_checks")
    tests = record.get("unit_tests")
    if (
        record.get("schema_version") != 1
        or record.get("version") != EXPECTED_VERSION
        or record.get("build") != EXPECTED_BUILD
        or record.get("tested_implementation_commit") != EXPECTED_COMMIT
        or GIT_SHA.fullmatch(str(record.get("reviewed_head_commit", ""))) is None
        or GIT_SHA.fullmatch(str(record.get("merge_commit", ""))) is None
        or re.fullmatch(
            r"https://github\.com/sunthemoon/AdvancedRocketry-Community/pull/\d+",
            str(record.get("pull_request", "")),
        ) is None
        or not _valid_date(record.get("reproduced_at"))
        or record.get("build_result") != "PASS"
        or main != {
            "byte_equal_to_candidate": True,
            "bytes": EXPECTED_ARTIFACT_BYTES,
            "candidate_sha256": EXPECTED_ARTIFACT_SHA256,
            "reproduced_sha256": EXPECTED_ARTIFACT_SHA256,
        }
        or manifest != {
            "byte_equal_to_candidate": True,
            "entry_count": 723,
            "candidate_sha256": EXPECTED_MANIFEST_SHA256,
            "reproduced_sha256": EXPECTED_MANIFEST_SHA256,
        }
        or tests != {
            "errors": 0,
            "failures": 0,
            "passed": 247,
            "skipped": 0,
            "total": 247,
        }
        or not isinstance(checks, dict)
        or checks.get("result") != "4/4_PASS"
        or not isinstance(checks.get("checks"), list)
        or len(checks["checks"]) != 4
        or re.fullmatch(
            r"https://github\.com/sunthemoon/AdvancedRocketry-Community/actions/runs/\d+",
            str(checks.get("forge_ci", "")),
        ) is None
        or re.fullmatch(
            r"https://github\.com/sunthemoon/AdvancedRocketry-Community/actions/runs/\d+",
            str(checks.get("governance_ci", "")),
        ) is None
    ):
        errors.append("v0.8.0 post-merge reproduction is incomplete or inconsistent")
        return {}
    return record


def validate_v080_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
    *,
    require_approved: bool = False,
) -> tuple[list[str], dict[str, Any]]:
    """Return validation errors and v0.8.0 Gate readiness details."""
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
    satellite_ready = _validate_satellite(repository_root, errors) and len(errors) == before
    before = len(errors)
    multiplayer_ready = _validate_multiplayer_cycle(
        repository_root, "multiplayer", errors
    ) and len(errors) == before
    before = len(errors)
    reconnect_ready = _validate_multiplayer_cycle(
        repository_root, "multiplayer-reconnect", errors
    ) and len(errors) == before
    before = len(errors)
    performance_ready = _validate_performance(repository_root, errors) and len(errors) == before
    before = len(errors)
    attestation, human_approved = _validate_owner(repository_root, errors)
    client_ready = human_approved and multiplayer_ready and reconnect_ready and len(errors) == before
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before
    before = len(errors)
    checksums_ready = _validate_checksums(repository_root, artifact_summary, errors)
    checksums_ready = checksums_ready and len(errors) == before
    before = len(errors)
    post_merge = _validate_post_merge(repository_root, errors)
    post_merge_ready = bool(post_merge) and len(errors) == before
    if require_approved and not human_approved:
        errors.append("v0.8.0 evidence has not received explicit G0/G8/G9 owner approval")

    security = automated.get("security", {}) if isinstance(automated, dict) else {}
    details = {
        "artifact_sha256": EXPECTED_ARTIFACT_SHA256 if artifact_ready else "",
        "tested_implementation_commit": EXPECTED_COMMIT,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "post_merge_ready": post_merge_ready,
        "merge_commit": post_merge.get("merge_commit") if post_merge_ready else None,
        "reviewed_head_commit": post_merge.get("reviewed_head_commit") if post_merge_ready else None,
        "pull_request": post_merge.get("pull_request") if post_merge_ready else None,
        "pull_request_checks": post_merge.get("pull_request_checks", {}).get("result")
        if post_merge_ready else None,
        "forge_ci": post_merge.get("pull_request_checks", {}).get("forge_ci")
        if post_merge_ready else None,
        "governance_ci": post_merge.get("pull_request_checks", {}).get("governance_ci")
        if post_merge_ready else None,
        "data_ready": provenance_ready and automated_ready,
        "automated_ready": automated_ready,
        "server_ready": dedicated_ready and satellite_ready and multiplayer_ready and reconnect_ready,
        "persistence_ready": satellite_ready,
        "authority_ready": automated_ready
        and satellite_ready
        and security.get("server_revalidated_terminal_access") is True
        and security.get("client_final_satellite_decisions") == 0,
        "performance_ready": performance_ready,
        "client_ready": client_ready,
        "docs_ready": docs_ready and checksums_ready,
        "checksums_ready": checksums_ready,
        "human_approved": human_approved,
        "human_approved_at": attestation.get("approved_at")
        if isinstance(attestation, dict) else None,
    }
    return errors, details


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--require-approved", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors, details = validate_v080_release_evidence(
        ROOT, artifact=args.artifact, require_approved=args.require_approved
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(
        "[PASS] v0.8.0 release evidence is complete: "
        + ", ".join(key for key, value in details.items() if value is True)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
