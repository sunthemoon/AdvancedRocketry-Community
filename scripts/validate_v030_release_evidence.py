#!/usr/bin/env python3
"""Validate the bounded v0.3.0 celestial release-evidence bundle."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path
from typing import Any

if __package__:
    from .validate_release_checksums import load_artifact_metadata
    from .validate_v020_release_evidence import (
        EvidenceError,
        _load_json,
        _png_dimensions,
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
        _png_dimensions,
        _regular_file,
        _safe_relative,
        _sha256,
    )


ROOT = Path(__file__).resolve().parents[1]
RELEASE_ROOT = Path("docs/releases/v0.3.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
EXPECTED_VERSION = "v0.3.0"
EXPECTED_BUILD = "1.20.1-0.3.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.3.0-dev.jar"
EXPECTED_SOURCES_ARTIFACT = (
    "advancedrocketry-community-1.20.1-0.3.0-dev-sources.jar"
)
EXPECTED_SCREENSHOTS = {
    "earth_after_restart.png",
    "earth_safe_return.png",
    "mods_v030.png",
    "moon_after_restart.png",
    "moon_before_restart.png",
    "space_before_restart.png",
}
EXPECTED_FILTERED_LOGS = {
    "client-a.txt",
    "client-b.txt",
    "server-after-restart.txt",
    "server-before-restart.txt",
}
REQUIRED_RELEASE_DOCS = {
    "GATE-STATUS.md",
    "INSTALLATION.md",
    "KNOWN-ISSUES.md",
    "MANUAL-TEST.md",
    "RELEASE-EVIDENCE.md",
    "TEST-REPORT.md",
    "checksums.txt",
}
AUTHORIZED_REVIEWERS = {"sunthemoon"}
SHA256 = re.compile(r"[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_TEXT_BYTES = 512 * 1024
MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024


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
        generated = _load_json(
            repository_root, Path("docs/provenance/v0.3.0-generated-resources.json")
        )
        fixture = _load_json(
            repository_root, Path("docs/provenance/v0.3.0-upstream-xml-fixture.json")
        )
        report = _load_json(
            repository_root, EVIDENCE_ROOT / "xml-import/import-report.json"
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return False

    targets = generated.get("targets")
    files = fixture.get("files")
    fixture_record = files[0] if isinstance(files, list) and len(files) == 1 else {}
    fixture_path = fixture_record.get("target_path")
    fixture_hash = fixture_record.get("target_sha256")
    if (
        generated.get("schema_version") != 1
        or generated.get("target_version") != EXPECTED_VERSION
        or generated.get("status") != "COMMUNITY_AUTHORED_DATAGEN"
        or not isinstance(targets, list)
        or len(targets) != 7
    ):
        errors.append("v0.3.0 generated-resource provenance is incomplete")
    if (
        fixture.get("status") != "UPSTREAM_AR_MIT"
        or fixture.get("license") != "MIT"
        or fixture.get("source_commit")
        != "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
        or not isinstance(fixture_path, str)
        or not isinstance(fixture_hash, str)
        or SHA256.fullmatch(fixture_hash) is None
        or fixture_record.get("source_sha256") != fixture_hash
        or fixture_record.get("distribution")
        != "test fixture only; excluded from the release JAR"
    ):
        errors.append("v0.3.0 upstream XML fixture provenance is incomplete")
    elif _sha256(
        _regular_file(repository_root, fixture_path, MAX_TEXT_BYTES)
    ) != fixture_hash:
        errors.append("v0.3.0 upstream XML fixture bytes differ from provenance")

    definitions = report.get("definitions")
    issues = report.get("issues")
    if (
        report.get("schema_version") != 1
        or report.get("status") != "SUCCESS_WITH_WARNINGS"
        or report.get("source_sha256") != fixture_hash
        or definitions
        != [
            "advancedrocketrycommunity:imported/planet_a",
            "advancedrocketrycommunity:imported/planet_a/planet_a_moon",
        ]
        or report.get("numeric_dimension_metadata") != []
        or not isinstance(issues, list)
        or not issues
        or any(issue.get("severity") != "WARNING" for issue in issues)
    ):
        errors.append("v0.3.0 canonical XML import report is incomplete")
    return not errors


def _validate_artifact(
    repository_root: Path, artifact: Path | None, errors: list[str]
) -> tuple[dict[str, Any], str, str]:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "artifact/artifact-summary.json"
        )
        manifest_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            MAX_JSON_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}, "", ""

    metadata, metadata_errors = load_artifact_metadata(manifest_path)
    errors.extend(f"v0.3.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    sources = summary.get("sources_jar")
    repeated = summary.get("repeated_clean_builds")
    copies = summary.get("packaged_copies")
    content = summary.get("content_manifest")
    if not all(
        isinstance(item, dict) for item in (main, sources, repeated, copies, content)
    ):
        errors.append("v0.3.0 artifact summary is missing required records")
        return summary, "", ""
    main_hash = main.get("sha256")
    sources_hash = sources.get("sha256")
    tested_commit = summary.get("tested_implementation_commit")
    copy_hashes = copies.get("sha256_values")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or not isinstance(tested_commit, str)
        or COMMIT.fullmatch(tested_commit) is None
        or main.get("path") != f"build/libs/{EXPECTED_ARTIFACT}"
        or main.get("filename") != EXPECTED_ARTIFACT
        or not isinstance(main_hash, str)
        or SHA256.fullmatch(main_hash) is None
        or main.get("bytes") != 296189
        or sources.get("path") != f"build/libs/{EXPECTED_SOURCES_ARTIFACT}"
        or not isinstance(sources_hash, str)
        or SHA256.fullmatch(sources_hash) is None
        or sources.get("bytes") != 160314
        or repeated.get("count") != 2
        or repeated.get("byte_identical") is not True
        or repeated.get("main_sha256_values") != [main_hash, main_hash]
        or repeated.get("sources_sha256_values") != [sources_hash, sources_hash]
        or copies.get("all_equal") is not True
        or not isinstance(copy_hashes, dict)
        or set(copy_hashes) != {"source", "client_a", "client_b", "server"}
        or set(copy_hashes.values()) != {main_hash}
        or content.get("path")
        != (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix()
        or content.get("sha256") != _sha256(manifest_path)
        or content.get("entry_count") != 226
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != main_hash
        or metadata.manifest.get("entry_count") != 226
    ):
        errors.append("v0.3.0 artifact summary does not bind the repeated packaged JAR")
    if artifact is not None and isinstance(main_hash, str):
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if not actual.is_file() or actual.stat().st_size > 64 * 1024 * 1024:
                errors.append("artifact is missing, unsafe, or too large")
            elif _sha256(actual) != main_hash:
                errors.append("artifact SHA-256 differs from the v0.3.0 evidence")
    return (
        summary,
        main_hash if isinstance(main_hash, str) else "",
        tested_commit if isinstance(tested_commit, str) else "",
    )


def _validate_automated(
    repository_root: Path,
    artifact_hash: str,
    tested_commit: str,
    errors: list[str],
) -> dict[str, Any]:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "automated/summary.json"
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    results = summary.get("results")
    security = summary.get("security")
    performance = summary.get("performance")
    if not all(isinstance(item, dict) for item in (results, security, performance)):
        errors.append("v0.3.0 automated summary is missing required records")
        return summary
    expected = {
        "clean_builds": {"result": "PASS", "runs": 2, "byte_identical": True},
        "junit": {"result": "PASS", "passed": 50, "failed": 0, "skipped": 0},
        "python": {"result": "PASS", "passed": 580, "failed": 0, "skipped": 1},
        "jar_audit": {"result": "PASS", "entries": 226, "findings": 0},
        "client_boundary": {"result": "PASS", "findings": 0},
        "celestial_identity": {"result": "PASS", "findings": 0},
        "datagen": {"result": "PASS", "files": 7, "written": 0, "git_diff_clean": True},
        "gametest": {"result": "PASS", "passed": 15, "failed": 0},
        "repository": {"result": "PASS", "warnings": 0, "failed": 0},
    }
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("tested_implementation_commit") != tested_commit
        or any(results.get(key) != value for key, value in expected.items())
        or security
        != {
            "bounded_network_payload": True,
            "bounded_saved_data": True,
            "legacy_xml_dtd_xxe_disabled": True,
            "operator_only_travel": True,
            "runtime_numeric_dimension_ids": 0,
            "server_authoritative": True,
        }
        or performance
        != {
            "catalog_lookup_constant_time": True,
            "catalog_rebuild_per_tick": False,
            "persistent_forced_chunks": 0,
            "snapshot_max_bytes": 98304,
            "world_write_budget_per_travel": 100,
        }
    ):
        errors.append("v0.3.0 automated summary is incomplete or inconsistent")
    return summary


def _validate_packaged_server(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> bool:
    try:
        dedicated = _load_json(
            repository_root, EVIDENCE_ROOT / "dedicated-server/summary.json"
        )
        machine = _load_json(
            repository_root, EVIDENCE_ROOT / "machine-regression/summary.json"
        )
        celestial = _load_json(
            repository_root, EVIDENCE_ROOT / "celestial-server/summary.json"
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    cycles = dedicated.get("cycles")
    before = celestial.get("before_restart")
    after = celestial.get("after_restart")
    dimensions_before = celestial.get("dimensions_before_restart")
    dimensions_after = celestial.get("dimensions_after_restart")
    if (
        dedicated.get("schema_version") != 2
        or dedicated.get("artifact_sha256") != artifact_hash
        or dedicated.get("server_artifact_sha256") != artifact_hash
        or dedicated.get("mod_version") != EXPECTED_BUILD
        or not isinstance(cycles, list)
        or len(cycles) != 2
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
        or dedicated.get("world", {}).get("same_world_verified") is not True
    ):
        errors.append("v0.3.0 packaged dedicated-server baseline is incomplete")
    if (
        machine.get("schema_version") != 1
        or machine.get("artifact_sha256") != artifact_hash
        or machine.get("paused_state_preserved") is not True
        or machine.get("atomic_completion_verified") is not True
        or machine.get("same_world_verified") is not True
    ):
        errors.append("v0.3.0 packaged v0.2 machine regression is incomplete")
    if (
        celestial.get("schema_version") != 1
        or celestial.get("artifact_sha256") != artifact_hash
        or celestial.get("catalog_body_count") != 3
        or celestial.get("invalid_reload_rejected") is not True
        or celestial.get("last_valid_catalog_retained") is not True
        or celestial.get("valid_catalog_recovered") is not True
        or celestial.get("fixed_level_blocks_persisted") is not True
        or not isinstance(before, dict)
        or before.get("exit_code") != 0
        or before.get("expected_catalog_rejections") != 1
        or not isinstance(after, dict)
        or after.get("exit_code") != 0
        or after.get("expected_catalog_rejections") != 0
        or not isinstance(dimensions_before, dict)
        or set(dimensions_before) != {"moon", "space"}
        or not isinstance(dimensions_after, dict)
        or set(dimensions_after) != {"moon", "space"}
    ):
        errors.append("v0.3.0 celestial restart/reload smoke is incomplete")
    return not errors


def _validate_manual(
    repository_root: Path,
    artifact_hash: str,
    tested_commit: str,
    errors: list[str],
) -> tuple[dict[str, Any], bool]:
    try:
        manual = _load_json(
            repository_root, EVIDENCE_ROOT / "client/manual-evidence.json"
        )
        comparison = _load_json(
            repository_root, EVIDENCE_ROOT / "persistence/comparison.json"
        )
        before = _load_json(
            repository_root, EVIDENCE_ROOT / "persistence/before-restart.json"
        )
        after = _load_json(
            repository_root, EVIDENCE_ROOT / "persistence/after-restart.json"
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}, False

    artifact = manual.get("artifact")
    observations = manual.get("observations")
    persistence = manual.get("persistence")
    players = manual.get("players")
    if not all(
        isinstance(item, dict) for item in (artifact, observations, persistence)
    ) or not isinstance(players, list):
        errors.append("v0.3.0 manual evidence is missing required records")
        return manual, False
    if (
        manual.get("schema_version") != 1
        or manual.get("version") != EXPECTED_VERSION
        or manual.get("build") != EXPECTED_BUILD
        or manual.get("tested_implementation_commit") != tested_commit
        or not _valid_date(manual.get("executed_at"))
        or artifact.get("filename") != EXPECTED_ARTIFACT
        or artifact.get("sha256") != artifact_hash
        or artifact.get("bytes") != 296189
        or artifact.get("all_copies_equal") is not True
        or set(artifact.get("copies", {}).values()) != {artifact_hash}
        or set(artifact.get("copies", {}))
        != {"source", "client_a", "client_b", "server"}
        or len(players) != 2
        or {player.get("username") for player in players}
        != {"ARCEV030A", "ARCEV030B"}
        or any(
            observations.get(key) is not True
            for key in (
                "two_players_simultaneously_online",
                "moon_safe_platform_visible",
                "space_safe_platform_visible",
                "earth_safe_return",
                "same_world_server_restart",
                "both_clients_rejoined_after_restart",
                "server_clean_stop",
            )
        )
        or observations.get("catalog_body_count") != 3
        or observations.get("snapshot_generation") != 1
        or observations.get("snapshot_payload_bytes_per_join") != 469
        or observations.get("client_exit_codes") != {"A": 0, "B": 0}
        or observations.get("project_client_linkage_failures") != 0
    ):
        errors.append("v0.3.0 manual player-flow evidence is incomplete")

    screenshots = manual.get("screenshots")
    if not isinstance(screenshots, list):
        errors.append("v0.3.0 manual evidence has no screenshot inventory")
    else:
        recorded_names: set[str] = set()
        for record in screenshots:
            if not isinstance(record, dict):
                errors.append("v0.3.0 screenshot record must be an object")
                continue
            raw = record.get("path")
            try:
                relative = _safe_relative(raw)
                path = _regular_file(repository_root, raw, MAX_SCREENSHOT_BYTES)
            except (EvidenceError, ValueError) as exc:
                errors.append(str(exc))
                continue
            if relative.parent.as_posix() != (
                EVIDENCE_ROOT / "client/screenshots"
            ).as_posix():
                errors.append(f"v0.3.0 screenshot is outside the evidence directory: {raw}")
            recorded_names.add(relative.name)
            if (
                record.get("sha256") != _sha256(path)
                or record.get("bytes") != path.stat().st_size
                or record.get("dimensions") != _png_dimensions(path)
                or not isinstance(record.get("subject"), str)
                or not record["subject"]
            ):
                errors.append(f"v0.3.0 screenshot binding differs: {raw}")
        actual_names = {
            path.name
            for path in (
                repository_root / EVIDENCE_ROOT / "client/screenshots"
            ).glob("*.png")
        }
        if recorded_names != EXPECTED_SCREENSHOTS or actual_names != EXPECTED_SCREENSHOTS:
            errors.append("v0.3.0 screenshot inventory is incomplete or contains extras")

    logs = manual.get("filtered_logs")
    if not isinstance(logs, list):
        errors.append("v0.3.0 manual evidence has no filtered-log inventory")
    else:
        log_names: set[str] = set()
        for record in logs:
            raw = record.get("path") if isinstance(record, dict) else None
            try:
                relative = _safe_relative(raw)
                path = _regular_file(repository_root, raw, MAX_TEXT_BYTES)
            except (EvidenceError, ValueError) as exc:
                errors.append(str(exc))
                continue
            log_names.add(relative.name)
            if (
                record.get("sha256") != _sha256(path)
                or record.get("bytes") != path.stat().st_size
            ):
                errors.append(f"v0.3.0 filtered-log binding differs: {raw}")
        if log_names != EXPECTED_FILTERED_LOGS:
            errors.append("v0.3.0 filtered-log inventory is incomplete or contains extras")

    if (
        before != after
        or before.get("schema_version") != 1
        or before.get("body_count") != 3
        or before.get("source_sha256")
        != "1a385871267e5e72a93ec6c13cdc1bd0e3414ded492152fdbaee524a51ee501e"
        or comparison.get("schema_version") != 1
        or comparison.get("source_byte_identical") is not True
        or comparison.get("body_state_identical") is not True
        or comparison.get("body_count") != 3
        or persistence.get("comparison_path")
        != (EVIDENCE_ROOT / "persistence/comparison.json").as_posix()
        or persistence.get("comparison_sha256")
        != _sha256(repository_root / EVIDENCE_ROOT / "persistence/comparison.json")
        or persistence.get("source_byte_identical") is not True
        or persistence.get("body_state_identical") is not True
    ):
        errors.append("v0.3.0 SavedData restart evidence is incomplete")

    review_status = manual.get("review_status")
    reviewer = manual.get("reviewer")
    reviewed_at = manual.get("reviewed_at")
    if review_status == "READY_FOR_HUMAN_REVIEW":
        if reviewer not in {"", None} or reviewed_at not in {"", None}:
            errors.append("pending v0.3.0 review cannot name an approving reviewer")
        human_approved = False
    elif review_status == "APPROVED":
        human_approved = reviewer in AUTHORIZED_REVIEWERS and _valid_date(reviewed_at)
        if not human_approved or manual.get("approved_gates") != ["G0", "G8", "G9"]:
            errors.append("v0.3.0 human review is incomplete or unauthorized")
    else:
        errors.append("v0.3.0 manual review status is unsupported")
        human_approved = False
    return manual, human_approved


def _validate_docs(repository_root: Path, errors: list[str]) -> bool:
    for name in sorted(REQUIRED_RELEASE_DOCS):
        try:
            _regular_file(
                repository_root, (RELEASE_ROOT / name).as_posix(), MAX_TEXT_BYTES
            )
        except EvidenceError as exc:
            errors.append(str(exc))
    return not errors


def _validate_checksums(
    repository_root: Path,
    artifact_summary: dict[str, Any],
    errors: list[str],
) -> bool:
    try:
        path = _regular_file(
            repository_root,
            (RELEASE_ROOT / "checksums.txt").as_posix(),
            MAX_TEXT_BYTES,
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
            errors.append(f"v0.3.0 checksums line {line_number} is invalid")
            continue
        raw = parts[1].strip()
        try:
            relative = _safe_relative(raw)
        except ValueError as exc:
            errors.append(str(exc))
            continue
        normalized = relative.as_posix()
        if normalized in recorded:
            errors.append(f"v0.3.0 checksums repeats {normalized}")
        recorded[normalized] = parts[0]

    evidence_root = repository_root / EVIDENCE_ROOT
    expected_evidence = {
        path.relative_to(repository_root).as_posix()
        for path in evidence_root.rglob("*")
        if path.is_file()
    }
    main = artifact_summary.get("main_jar", {})
    sources = artifact_summary.get("sources_jar", {})
    artifact_paths = {main.get("path"), sources.get("path")}
    if None in artifact_paths:
        errors.append("v0.3.0 artifact checksum paths are missing")
        artifact_paths.discard(None)
    expected = expected_evidence | artifact_paths
    if set(recorded) != expected:
        errors.append("v0.3.0 checksums inventory is incomplete or contains extras")
    for relative in sorted(expected_evidence):
        try:
            actual = _sha256(
                _regular_file(repository_root, relative, MAX_SCREENSHOT_BYTES)
            )
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.3.0 checksum mismatch: {relative}")
    for record in (main, sources):
        if recorded.get(record.get("path")) != record.get("sha256"):
            errors.append("v0.3.0 checksums omit or change an artifact binding")
    return not errors


def validate_v030_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
    *,
    require_approved: bool = False,
) -> tuple[list[str], dict[str, Any]]:
    """Return bounded validation errors and v0.3.0 Gate readiness details."""
    repository_root = repository_root.resolve()
    errors: list[str] = []

    before = len(errors)
    provenance_ready = _validate_provenance(repository_root, errors) and len(errors) == before
    before = len(errors)
    artifact_summary, artifact_hash, tested_commit = _validate_artifact(
        repository_root, artifact, errors
    )
    artifact_ready = len(errors) == before and bool(artifact_hash)
    before = len(errors)
    automated = _validate_automated(
        repository_root, artifact_hash, tested_commit, errors
    )
    automated_ready = len(errors) == before and bool(automated)
    before = len(errors)
    server_ready = _validate_packaged_server(
        repository_root, artifact_hash, errors
    ) and len(errors) == before
    before = len(errors)
    manual, human_approved = _validate_manual(
        repository_root, artifact_hash, tested_commit, errors
    )
    client_ready = len(errors) == before and bool(manual)
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before
    before = len(errors)
    checksums_ready = _validate_checksums(
        repository_root, artifact_summary, errors
    ) and len(errors) == before
    if require_approved and not human_approved:
        errors.append("v0.3.0 evidence has not received explicit G0/G8/G9 owner approval")

    results = automated.get("results", {}) if isinstance(automated, dict) else {}
    security = automated.get("security", {}) if isinstance(automated, dict) else {}
    performance = automated.get("performance", {}) if isinstance(automated, dict) else {}
    details = {
        "artifact_sha256": artifact_hash,
        "tested_implementation_commit": tested_commit,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "data_ready": automated_ready
        and results.get("datagen", {}).get("git_diff_clean") is True,
        "automated_ready": automated_ready,
        "server_ready": server_ready and client_ready,
        "persistence_ready": server_ready and client_ready,
        "authority_ready": automated_ready
        and security.get("server_authoritative") is True,
        "performance_ready": automated_ready
        and performance.get("catalog_rebuild_per_tick") is False,
        "client_ready": client_ready,
        "docs_ready": docs_ready and checksums_ready,
        "checksums_ready": checksums_ready,
        "human_approved": human_approved,
        "human_approved_at": manual.get("reviewed_at") if isinstance(manual, dict) else None,
    }
    return errors, details


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--require-approved", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors, details = validate_v030_release_evidence(
        artifact=args.artifact,
        require_approved=args.require_approved,
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(
        "[PASS] v0.3.0 release evidence: provenance, repeated artifact, "
        "automated tests, fixed worlds, reload recovery, SavedData, multiplayer, "
        "client screenshots, and checksums"
    )
    print(f"[INFO] JAR SHA-256: {details['artifact_sha256']}")
    print(f"[INFO] Human approval: {details['human_approved']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
