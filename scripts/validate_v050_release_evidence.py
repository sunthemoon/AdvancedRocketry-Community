#!/usr/bin/env python3
"""Validate the bounded v0.5.0 rocket-assembly release-evidence bundle."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path
from typing import Any

if __package__:
    from .manage_v050_generated_manifest import verify as verify_generated_manifest
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
    from manage_v050_generated_manifest import verify as verify_generated_manifest
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
RELEASE_ROOT = Path("docs/releases/v0.5.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
PROVENANCE_RECORD = Path("docs/provenance/v0.5.0-rocket-assembly.md")
GENERATED_RECORD = Path("docs/provenance/v0.5.0-generated-resources.json")
EXPECTED_VERSION = "v0.5.0"
EXPECTED_BUILD = "1.20.1-0.5.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.5.0-dev.jar"
EXPECTED_SOURCES_ARTIFACT = (
    "advancedrocketry-community-1.20.1-0.5.0-dev-sources.jar"
)
EXPECTED_ARTIFACT_SHA256 = (
    "45782780eeec54f1710cee4425f96b4d0152d29590559f519130ca9f227f0ba0"
)
EXPECTED_SOURCES_SHA256 = (
    "a1220e5066c487e009edad46311f912430b8bd2ef39881e46bb79d96c9afc7eb"
)
EXPECTED_COMMIT = "eae8d9224c708924930b781d7332eb69b6a4bf8d"
EXPECTED_MANIFEST_SHA256 = (
    "0184e2c49061da76e91933c020447ad1f4c13e87fc34499843b8dc6952c9d24d"
)
VISUAL_SOURCE_ARTIFACT_SHA256 = (
    "0e232ace303912d8487c0b26853341801c9ffe4468d2a73ae322cfce049ff42b"
)
EXPECTED_GENERATED_SHA256 = (
    "2c6cc995ba2bd08f5202901d081e5d55938c417515bce44b1ca4049d3529b40e"
)
EXPECTED_SCREENSHOTS = {"rocket-render.png"}
EXPECTED_FILTERED_LOGS = {"client.txt", "server.txt"}
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
AUTHORIZED_REVIEWERS = {"sunthemoon"}
SHA256 = re.compile(r"[0-9a-f]{64}")
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_TEXT_BYTES = 512 * 1024
MAX_MEDIA_BYTES = 8 * 1024 * 1024


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
        manifest_errors = verify_generated_manifest(repository_root, generated_path)
    except (EvidenceError, OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        errors.append(str(exc))
        return False
    errors.extend(manifest_errors)
    required = {
        "target_version: v0.5.0",
        "status: APPROVED",
        'reviewer: "sunthemoon"',
        'reviewed_at: "2026-09-01"',
        "copied_source_files: 0",
        "copied_binary_assets: 0",
        "generated_resources: 39",
        f"generated_manifest_sha256: {EXPECTED_GENERATED_SHA256}",
    }
    if any(item not in provenance for item in required):
        errors.append("v0.5.0 provenance is not explicitly owner-approved")
    targets = generated.get("targets")
    if (
        generated.get("schema_version") != 1
        or generated.get("target_version") != EXPECTED_VERSION
        or generated.get("status") != "COMMUNITY_AUTHORED_DATAGEN"
        or not isinstance(targets, list)
        or len(targets) != 39
        or _sha256(generated_path) != EXPECTED_GENERATED_SHA256
    ):
        errors.append("v0.5.0 generated-resource provenance is incomplete")
    return not errors


def _validate_artifact(
    repository_root: Path, artifact: Path | None, errors: list[str]
) -> tuple[dict[str, Any], str, str]:
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
        return {}, "", ""
    metadata, metadata_errors = load_artifact_metadata(manifest)
    errors.extend(f"v0.5.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    sources = summary.get("sources_jar")
    repeated = summary.get("repeated_clean_builds")
    server_copy = summary.get("packaged_server_copy")
    content = summary.get("content_manifest")
    if not all(
        isinstance(item, dict)
        for item in (main, sources, repeated, server_copy, content)
    ):
        errors.append("v0.5.0 artifact summary is missing required records")
        return summary, "", ""
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or main
        != {
            "path": f"build/libs/{EXPECTED_ARTIFACT}",
            "filename": EXPECTED_ARTIFACT,
            "bytes": 703307,
            "sha256": EXPECTED_ARTIFACT_SHA256,
        }
        or sources
        != {
            "path": f"build/libs/{EXPECTED_SOURCES_ARTIFACT}",
            "filename": EXPECTED_SOURCES_ARTIFACT,
            "bytes": 357173,
            "sha256": EXPECTED_SOURCES_SHA256,
        }
        or repeated.get("count") != 2
        or repeated.get("byte_identical") is not True
        or repeated.get("main_sha256_values")
        != [EXPECTED_ARTIFACT_SHA256, EXPECTED_ARTIFACT_SHA256]
        or server_copy
        != {"byte_equal": True, "sha256": EXPECTED_ARTIFACT_SHA256}
        or content
        != {
            "path": (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            "sha256": EXPECTED_MANIFEST_SHA256,
            "entry_count": 497,
        }
        or _sha256(manifest) != EXPECTED_MANIFEST_SHA256
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != EXPECTED_ARTIFACT_SHA256
        or metadata.manifest.get("entry_count") != 497
    ):
        errors.append("v0.5.0 artifact summary does not bind the repeated packaged JAR")
    if artifact is not None:
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if not actual.is_file() or actual.is_symlink() or actual.stat().st_size != 703307:
                errors.append("artifact is missing, unsafe, or has the wrong size")
            elif _sha256(actual) != EXPECTED_ARTIFACT_SHA256:
                errors.append("artifact SHA-256 differs from the v0.5.0 evidence")
    return summary, EXPECTED_ARTIFACT_SHA256, EXPECTED_COMMIT


def _validate_automated(
    repository_root: Path, artifact_hash: str, tested_commit: str, errors: list[str]
) -> dict[str, Any]:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "automated/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    results = summary.get("results")
    security = summary.get("security")
    required_results = {
        "clean_builds": {"result": "PASS", "runs": 2, "byte_identical": True},
        "client_boundary": {"result": "PASS", "findings": 0},
        "datagen": {"result": "PASS", "files": 39, "git_diff_clean": True},
        "gametest": {"result": "PASS", "passed": 34, "failed": 0},
        "jar_audit": {"result": "PASS", "entries": 497, "findings": 0},
        "junit": {"result": "PASS", "passed": 156, "failed": 0, "skipped": 0},
        "packaged_rocket_restart": {
            "result": "PASS",
            "entity_nbt_restart_byte_equal": True,
            "container_items_conserved": True,
            "stale_transaction_recovered": True,
        },
        "repository": {"result": "PASS", "warnings": 0, "failed": 0},
    }
    python_result = results.get("python") if isinstance(results, dict) else None
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("tested_implementation_commit") != tested_commit
        or not isinstance(results, dict)
        or any(results.get(key) != value for key, value in required_results.items())
        or not isinstance(python_result, dict)
        or python_result.get("result") != "PASS"
        or python_result.get("discovered") != 609
        or python_result.get("passed") != 606
        or python_result.get("failed") != 0
        or python_result.get("skipped") != 3
        or not isinstance(security, dict)
        or any(value is not True for key, value in security.items() if key not in {"forced_chunk_loads", "client_final_decisions"})
        or security.get("forced_chunk_loads") != 0
        or security.get("client_final_decisions") != 0
    ):
        errors.append("v0.5.0 automated summary is incomplete or inconsistent")
    return summary


def _validate_servers(repository_root: Path, artifact_hash: str, errors: list[str]) -> bool:
    try:
        dedicated = _load_json(
            repository_root, EVIDENCE_ROOT / "dedicated-server/summary.json"
        )
        rocket = _load_json(repository_root, EVIDENCE_ROOT / "rocket-server/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    cycles = dedicated.get("cycles")
    assembled = rocket.get("assembled")
    persisted = rocket.get("persisted")
    recovered = rocket.get("recovered")
    if (
        dedicated.get("schema_version") != 2
        or dedicated.get("artifact_sha256") != artifact_hash
        or dedicated.get("server_artifact_sha256") != artifact_hash
        or dedicated.get("mod_version") != EXPECTED_BUILD
        or dedicated.get("offline_mode") is not False
        or dedicated.get("server_bind") != "127.0.0.1"
        or dedicated.get("world", {}).get("same_world_verified") is not True
        or not isinstance(cycles, list)
        or [cycle.get("name") for cycle in cycles] != ["first-start", "restart"]
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
        or any(cycle.get("client_linkage_failure_count") != 0 for cycle in cycles)
        or any(cycle.get("project_error_count") != 0 for cycle in cycles)
        or rocket.get("schema_version") != 1
        or rocket.get("artifact_sha256") != artifact_hash
        or rocket.get("artifact_version") != EXPECTED_BUILD
        or rocket.get("same_world_verified") is not True
        or rocket.get("entity_nbt_restart_byte_equal") is not True
        or rocket.get("container_items_conserved") is not True
        or rocket.get("stale_transaction_recovered") is not True
        or rocket.get("durable_journal_cleared") is not True
        or not all(isinstance(item, dict) for item in (assembled, persisted, recovered))
        or assembled.get("exit_code") != 0
        or persisted.get("exit_code") != 0
        or recovered.get("exit_code") != 0
        or assembled.get("rocket_id") != persisted.get("rocket_id")
        or assembled.get("snapshot_hash") != persisted.get("snapshot_hash")
        or assembled.get("entity_data") != persisted.get("entity_data")
        or "minecraft:diamond\", Count: 17b" not in recovered.get("restored_chest_data", "")
        or "minecraft:iron_ingot\", Count: 64b" not in recovered.get("restored_chest_data", "")
    ):
        errors.append("v0.5.0 packaged-server and rocket-recovery evidence is incomplete")
    return not errors


def _validate_performance(repository_root: Path, errors: list[str]) -> dict[str, Any]:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "performance/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    scan = summary.get("maximum_scan")
    visual = summary.get("maximum_visual_projection")
    render = summary.get("render_cache")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or not all(isinstance(item, dict) for item in (scan, visual, render))
        or scan.get("result") != "PASS"
        or scan.get("blocks") != 2048
        or scan.get("per_tick_budget") != 256
        or scan.get("inspections", 12290) > scan.get("maximum_inspections", 12289)
        or scan.get("ticks", 10**9) > 49
        or visual.get("result") != "PASS"
        or visual.get("blocks") != 2048
        or visual.get("payload_bytes", 524289) > visual.get("payload_byte_limit", 524288)
        or visual.get("chunks") != 2
        or visual.get("chunk_byte_limit") != 32768
        or render.get("content_hash_keyed") is not True
        or render.get("per_frame_block_state_restore") is not False
        or render.get("maximum_cached_entities") != 256
    ):
        errors.append("v0.5.0 maximum-structure performance evidence is incomplete")
    return summary


def _validate_manual(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> tuple[dict[str, Any], bool]:
    try:
        manual = _load_json(repository_root, EVIDENCE_ROOT / "client/manual-evidence.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}, False
    client = manual.get("client")
    server = manual.get("server")
    observations = manual.get("observations")
    owner = manual.get("owner_review")
    candidate = manual.get("candidate_revalidation")
    if (
        manual.get("schema_version") != 1
        or manual.get("version") != EXPECTED_VERSION
        or manual.get("build") != EXPECTED_BUILD
        or manual.get("artifact_sha256") != artifact_hash
        or not isinstance(client, dict)
        or client.get("kind") != "forge_userdev"
        or client.get("matching_implementation") is not True
        or client.get("minecraft") != "1.20.1"
        or client.get("forge") != "47.4.10"
        or client.get("visual_source_artifact_sha256")
        != VISUAL_SOURCE_ARTIFACT_SHA256
        or not isinstance(server, dict)
        or server.get("kind") != "packaged_forge_server"
        or server.get("artifact_sha256") != VISUAL_SOURCE_ARTIFACT_SHA256
        or server.get("loopback_only") is not True
        or not isinstance(candidate, dict)
        or candidate.get("artifact_sha256") != artifact_hash
        or candidate.get("packaged_server_and_recovery") != "PASS"
        or "GameTest-only" not in str(candidate.get("carry_forward_reason"))
        or not isinstance(observations, dict)
        or observations.get("matching_modded_connection") is not True
        or observations.get("visual_cache_rendered_after_server_restart") is not True
        or observations.get("rocket_blocks_visible") != 4
        or observations.get("rocket_entity_operational") is not True
        or observations.get("clean_client_shutdown") is not True
        or observations.get("server_saved_and_stopped_cleanly") is not True
        or SHA256.fullmatch(str(observations.get("snapshot_hash"))) is None
    ):
        errors.append("v0.5.0 manual player-flow evidence is incomplete")

    screenshots = manual.get("screenshots")
    recorded_screenshots: set[str] = set()
    if not isinstance(screenshots, list):
        errors.append("v0.5.0 manual evidence has no screenshot inventory")
    else:
        for record in screenshots:
            raw = record.get("file") if isinstance(record, dict) else None
            try:
                relative = _safe_relative(raw)
                path = _regular_file(repository_root, raw, MAX_MEDIA_BYTES)
                dimensions = _png_dimensions(path)
            except (EvidenceError, ValueError) as exc:
                errors.append(str(exc))
                continue
            recorded_screenshots.add(relative.name)
            if (
                relative.parent.as_posix()
                != (EVIDENCE_ROOT / "client/screenshots").as_posix()
                or record.get("sha256") != _sha256(path)
                or [record.get("width"), record.get("height")] != dimensions
                or not isinstance(record.get("observation"), str)
                or not record.get("observation")
            ):
                errors.append(f"v0.5.0 screenshot binding differs: {raw}")
    screenshot_root = repository_root / EVIDENCE_ROOT / "client/screenshots"
    actual_screenshots = {path.name for path in screenshot_root.glob("*.png")}
    if recorded_screenshots != EXPECTED_SCREENSHOTS or actual_screenshots != EXPECTED_SCREENSHOTS:
        errors.append("v0.5.0 screenshot inventory is incomplete or contains extras")

    logs = manual.get("logs")
    recorded_logs: set[str] = set()
    if not isinstance(logs, list):
        errors.append("v0.5.0 manual evidence has no filtered-log inventory")
    else:
        for record in logs:
            raw = record.get("file") if isinstance(record, dict) else None
            try:
                relative = _safe_relative(raw)
                path = _regular_file(repository_root, raw, MAX_TEXT_BYTES)
                text = path.read_text(encoding="utf-8", errors="strict")
            except (EvidenceError, UnicodeError, ValueError) as exc:
                errors.append(str(exc))
                continue
            recorded_logs.add(relative.name)
            if (
                relative.parent.as_posix() != (EVIDENCE_ROOT / "client/logs").as_posix()
                or record.get("sha256") != _sha256(path)
                or re.search(r"[A-Za-z]:[\\/]", text)
            ):
                errors.append(f"v0.5.0 filtered-log binding differs: {raw}")
    log_root = repository_root / EVIDENCE_ROOT / "client/logs"
    actual_logs = {path.name for path in log_root.glob("*.txt")}
    if recorded_logs != EXPECTED_FILTERED_LOGS or actual_logs != EXPECTED_FILTERED_LOGS:
        errors.append("v0.5.0 filtered-log inventory is incomplete or contains extras")

    human_approved = bool(
        isinstance(owner, dict)
        and owner.get("approved_by") in AUTHORIZED_REVIEWERS
        and _valid_date(owner.get("approved_at"))
        and owner.get("g8_visible_client_acceptance") == "PASS"
        and owner.get("g9_release_acceptance") == "PASS"
    )
    if not human_approved:
        errors.append("v0.5.0 human review is incomplete or unauthorized")
    return manual, human_approved


def _validate_docs(repository_root: Path, errors: list[str]) -> bool:
    for name in sorted(REQUIRED_RELEASE_DOCS):
        try:
            _regular_file(repository_root, (RELEASE_ROOT / name).as_posix(), MAX_TEXT_BYTES)
        except EvidenceError as exc:
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
            errors.append(f"v0.5.0 checksums line {line_number} is invalid")
            continue
        try:
            relative = _safe_relative(parts[1].strip()).as_posix()
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if relative in recorded:
            errors.append(f"v0.5.0 checksums repeats {relative}")
        recorded[relative] = parts[0]
    evidence_files = {
        item.relative_to(repository_root).as_posix()
        for item in (repository_root / EVIDENCE_ROOT).rglob("*")
        if item.is_file()
    }
    main = artifact_summary.get("main_jar", {})
    sources = artifact_summary.get("sources_jar", {})
    artifact_paths = {main.get("path"), sources.get("path")}
    expected = evidence_files | artifact_paths
    if None in expected:
        errors.append("v0.5.0 artifact checksum paths are missing")
        expected.discard(None)
    if set(recorded) != expected:
        errors.append("v0.5.0 checksums inventory is incomplete or contains extras")
    for relative in sorted(evidence_files):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_MEDIA_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.5.0 checksum mismatch: {relative}")
    for record in (main, sources):
        if recorded.get(record.get("path")) != record.get("sha256"):
            errors.append("v0.5.0 checksums omit or change an artifact binding")
    return not errors


def validate_v050_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
    *,
    require_approved: bool = False,
) -> tuple[list[str], dict[str, Any]]:
    """Return bounded validation errors and v0.5.0 Gate readiness details."""
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
    automated = _validate_automated(repository_root, artifact_hash, tested_commit, errors)
    automated_ready = len(errors) == before and bool(automated)
    before = len(errors)
    server_ready = _validate_servers(repository_root, artifact_hash, errors) and len(errors) == before
    before = len(errors)
    performance = _validate_performance(repository_root, errors)
    performance_ready = len(errors) == before and bool(performance)
    before = len(errors)
    manual, human_approved = _validate_manual(repository_root, artifact_hash, errors)
    client_ready = len(errors) == before and bool(manual)
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before
    before = len(errors)
    checksums_ready = _validate_checksums(repository_root, artifact_summary, errors) and len(errors) == before
    if require_approved and not human_approved:
        errors.append("v0.5.0 evidence has not received explicit G0/G8/G9 owner approval")

    security = automated.get("security", {}) if isinstance(automated, dict) else {}
    details = {
        "artifact_sha256": artifact_hash,
        "tested_implementation_commit": tested_commit,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "data_ready": provenance_ready and automated_ready,
        "automated_ready": automated_ready,
        "server_ready": server_ready and client_ready,
        "persistence_ready": server_ready,
        "authority_ready": automated_ready
        and security.get("server_recomputed_stats") is True
        and security.get("client_final_decisions") == 0,
        "performance_ready": performance_ready,
        "client_ready": client_ready,
        "docs_ready": docs_ready and checksums_ready,
        "checksums_ready": checksums_ready,
        "human_approved": human_approved,
        "human_approved_at": owner_date(manual),
    }
    return errors, details


def owner_date(manual: dict[str, Any]) -> object:
    owner = manual.get("owner_review") if isinstance(manual, dict) else None
    return owner.get("approved_at") if isinstance(owner, dict) else None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path)
    parser.add_argument("--require-approved", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors, details = validate_v050_release_evidence(
        ROOT, artifact=args.artifact, require_approved=args.require_approved
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(
        "[PASS] v0.5.0 release evidence is complete: "
        + ", ".join(key for key, value in details.items() if value is True)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
