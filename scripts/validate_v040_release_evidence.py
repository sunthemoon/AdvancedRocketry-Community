#!/usr/bin/env python3
"""Validate the bounded v0.4.0 atmosphere release-evidence bundle."""

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
RELEASE_ROOT = Path("docs/releases/v0.4.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
PROVENANCE_RECORD = Path("docs/provenance/v0.4.0-atmosphere.md")
GENERATED_RECORD = Path("docs/provenance/v0.4.0-generated-resources.json")
EXPECTED_VERSION = "v0.4.0"
EXPECTED_BUILD = "1.20.1-0.4.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.4.0-dev.jar"
EXPECTED_SOURCES_ARTIFACT = (
    "advancedrocketry-community-1.20.1-0.4.0-dev-sources.jar"
)
EXPECTED_ARTIFACT_SHA256 = (
    "05279656dfae21f682ca45a000517628dfcf706ebc4cce9ce2fe16e0723e96f1"
)
EXPECTED_SOURCES_SHA256 = (
    "44841519742a54e43b27642eb2442435973b7d30e9e5a642dc95dca97b2f984d"
)
EXPECTED_COMMIT = "f880870aa4db0a46758dcc8615dfa2c16b2e3b59"
EXPECTED_SCREENSHOTS = {
    "a-after-restart-no-power.png",
    "a-after-restart-recovered.png",
    "a-room-open.png",
    "a-room-sealed.png",
    "a-space-full-suit-separated.png",
    "a-space-oxygen-empty.png",
    "b-after-restart-recovered.png",
    "b-room-open.png",
    "b-room-sealed-scale3.png",
    "b-space-no-suit-separated.png",
    "b-space-partial-suit.png",
    "room-seal-flow.gif",
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


def _gif_metadata(path: Path) -> tuple[list[int], int]:
    """Return dimensions and image-frame count from a bounded GIF."""
    data = path.read_bytes()
    if len(data) < 14 or data[:6] not in {b"GIF87a", b"GIF89a"}:
        raise EvidenceError(f"invalid GIF evidence: {path.name}")
    dimensions = [int.from_bytes(data[6:8], "little"), int.from_bytes(data[8:10], "little")]
    packed = data[10]
    offset = 13
    if packed & 0x80:
        offset += 3 * (2 ** ((packed & 0x07) + 1))
    frames = 0

    def skip_sub_blocks(start: int) -> int:
        while True:
            if start >= len(data):
                raise EvidenceError(f"truncated GIF evidence: {path.name}")
            size = data[start]
            start += 1
            if size == 0:
                return start
            start += size
            if start > len(data):
                raise EvidenceError(f"truncated GIF evidence: {path.name}")

    while offset < len(data):
        marker = data[offset]
        offset += 1
        if marker == 0x3B:
            break
        if marker == 0x21:
            if offset >= len(data):
                raise EvidenceError(f"truncated GIF evidence: {path.name}")
            offset += 1
            offset = skip_sub_blocks(offset)
            continue
        if marker != 0x2C or offset + 9 > len(data):
            raise EvidenceError(f"invalid GIF block in evidence: {path.name}")
        frames += 1
        descriptor_packed = data[offset + 8]
        offset += 9
        if descriptor_packed & 0x80:
            offset += 3 * (2 ** ((descriptor_packed & 0x07) + 1))
        if offset >= len(data):
            raise EvidenceError(f"truncated GIF evidence: {path.name}")
        offset += 1
        offset = skip_sub_blocks(offset)
    return dimensions, frames


def _validate_provenance(repository_root: Path, errors: list[str]) -> bool:
    try:
        provenance_path = _regular_file(
            repository_root, PROVENANCE_RECORD.as_posix(), MAX_TEXT_BYTES
        )
        provenance = provenance_path.read_text(encoding="utf-8", errors="strict")
        generated = _load_json(repository_root, GENERATED_RECORD)
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return False
    required = {
        "target_version: v0.4.0",
        "status: APPROVED",
        "reviewer: \"sunthemoon\"",
        "reviewed_at: \"2026-09-01\"",
        "copied_source_files: 0",
        "copied_binary_assets: 0",
        "generated_resources: 29",
        "generated_manifest_sha256: 1aba512b16204295e1f8ca127affb96c7d4277d53984483188c4982b51a9a8b8",
    }
    if any(item not in provenance for item in required):
        errors.append("v0.4.0 provenance is not explicitly owner-approved")
    targets = generated.get("targets")
    if (
        generated.get("schema_version") != 1
        or generated.get("target_version") != EXPECTED_VERSION
        or generated.get("status") != "COMMUNITY_AUTHORED_DATAGEN"
        or not isinstance(targets, list)
        or len(targets) != 29
    ):
        errors.append("v0.4.0 generated-resource provenance is incomplete")
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
    errors.extend(f"v0.4.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    sources = summary.get("sources_jar")
    repeated = summary.get("repeated_clean_builds")
    copies = summary.get("packaged_copies")
    content = summary.get("content_manifest")
    if not all(isinstance(item, dict) for item in (main, sources, repeated, copies, content)):
        errors.append("v0.4.0 artifact summary is missing required records")
        return summary, "", ""
    copy_hashes = copies.get("sha256_values")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("tested_implementation_commit") != EXPECTED_COMMIT
        or main
        != {
            "path": f"build/libs/{EXPECTED_ARTIFACT}",
            "filename": EXPECTED_ARTIFACT,
            "bytes": 466433,
            "sha256": EXPECTED_ARTIFACT_SHA256,
        }
        or sources
        != {
            "path": f"build/libs/{EXPECTED_SOURCES_ARTIFACT}",
            "filename": EXPECTED_SOURCES_ARTIFACT,
            "bytes": 242727,
            "sha256": EXPECTED_SOURCES_SHA256,
        }
        or repeated.get("count") != 2
        or repeated.get("byte_identical") is not True
        or repeated.get("main_sha256_values")
        != [EXPECTED_ARTIFACT_SHA256, EXPECTED_ARTIFACT_SHA256]
        or copies.get("all_equal") is not True
        or not isinstance(copy_hashes, dict)
        or set(copy_hashes) != {"source", "server", "client_a", "client_b"}
        or set(copy_hashes.values()) != {EXPECTED_ARTIFACT_SHA256}
        or content
        != {
            "path": (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            "sha256": _sha256(manifest),
            "entry_count": 346,
        }
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != EXPECTED_ARTIFACT_SHA256
        or metadata.manifest.get("entry_count") != 346
    ):
        errors.append("v0.4.0 artifact summary does not bind the repeated packaged JAR")
    if artifact is not None:
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if not actual.is_file() or actual.stat().st_size != 466433:
                errors.append("artifact is missing, unsafe, or has the wrong size")
            elif _sha256(actual) != EXPECTED_ARTIFACT_SHA256:
                errors.append("artifact SHA-256 differs from the v0.4.0 evidence")
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
    performance = summary.get("performance")
    required_results = {
        "clean_builds": {"result": "PASS", "runs": 2, "byte_identical": True},
        "junit": {"result": "PASS", "passed": 86, "failed": 0, "skipped": 0},
        "python_targeted": {"result": "PASS", "passed": 124, "failed": 0, "skipped": 0},
        "jar_audit": {"result": "PASS", "entries": 346, "findings": 0},
        "client_boundary": {"result": "PASS", "findings": 0},
        "celestial_identity": {"result": "PASS", "findings": 0},
        "datagen": {"result": "PASS", "files": 29, "written": 0, "git_diff_clean": True},
        "gametest": {"result": "PASS", "passed": 25, "failed": 0},
        "repository": {"result": "PASS", "warnings": 0, "failed": 0},
    }
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("tested_implementation_commit") != tested_commit
        or not isinstance(results, dict)
        or any(results.get(key) != value for key, value in required_results.items())
        or results.get("python_full_ci_inventory")
        != {"result": "PENDING_CI", "discovered": 596}
        or not isinstance(security, dict)
        or any(value is not True for key, value in security.items() if key != "forced_chunk_loads" and key != "client_final_decisions")
        or security.get("forced_chunk_loads") != 0
        or security.get("client_final_decisions") != 0
        or not isinstance(performance, dict)
        or performance.get("gametest_vents") != 16
        or performance.get("gametest_simulated_ticks") != 6000
        or performance.get("gametest_peak_inspections", 10**9) > 102
        or performance.get("packaged_duration_seconds") != 300.0
        or performance.get("packaged_sample_count") != 60
        or performance.get("tps_minimum", 0) < 20.0
        or performance.get("full_gc_count") != 0
    ):
        errors.append("v0.4.0 automated summary is incomplete or inconsistent")
    return summary


def _validate_servers(repository_root: Path, artifact_hash: str, errors: list[str]) -> bool:
    try:
        dedicated = _load_json(repository_root, EVIDENCE_ROOT / "dedicated-server/summary.json")
        machine = _load_json(repository_root, EVIDENCE_ROOT / "machine-regression/summary.json")
        celestial = _load_json(repository_root, EVIDENCE_ROOT / "celestial-regression/summary.json")
        atmosphere = _load_json(repository_root, EVIDENCE_ROOT / "atmosphere-server/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return False
    cycles = dedicated.get("cycles")
    performance = atmosphere.get("performance")
    scenario = atmosphere.get("scenario")
    if (
        dedicated.get("schema_version") != 2
        or dedicated.get("artifact_sha256") != artifact_hash
        or dedicated.get("server_artifact_sha256") != artifact_hash
        or dedicated.get("mod_version") != EXPECTED_BUILD
        or dedicated.get("offline_mode") is not False
        or dedicated.get("world", {}).get("same_world_verified") is not True
        or not isinstance(cycles, list)
        or [cycle.get("name") for cycle in cycles] != ["first-start", "restart"]
        or any(cycle.get("exit_code") != 0 for cycle in cycles)
        or any(cycle.get("client_linkage_failure_count") != 0 for cycle in cycles)
        or machine.get("artifact_sha256") != artifact_hash
        or machine.get("artifact_version") != EXPECTED_BUILD
        or machine.get("paused_state_preserved") is not True
        or machine.get("atomic_completion_verified") is not True
        or machine.get("same_world_verified") is not True
        or celestial.get("artifact_sha256") != artifact_hash
        or celestial.get("artifact_version") != EXPECTED_BUILD
        or celestial.get("catalog_body_count") != 3
        or celestial.get("invalid_reload_rejected") is not True
        or celestial.get("last_valid_catalog_retained") is not True
        or celestial.get("valid_catalog_recovered") is not True
        or celestial.get("fixed_level_blocks_persisted") is not True
        or atmosphere.get("artifact_sha256") != artifact_hash
        or atmosphere.get("artifact_version") != EXPECTED_BUILD
        or atmosphere.get("same_world_verified") is not True
        or atmosphere.get("vent_nbt_restart_byte_equal") is not True
        or atmosphere.get("post_restart_volume_rebuilt") is not True
        or scenario != {"active_vents": 16, "non_air_blocks": 416, "rooms": 16, "traversable_cells_per_room": 1}
        or not isinstance(performance, dict)
        or performance.get("duration_seconds") != 300.0
        or performance.get("sample_count") != 60
        or performance.get("sampled_tps", {}).get("minimum", 0) < 20.0
        or performance.get("sampled_mean_tick_ms", {}).get("p95", 10**9) > 1.44
        or performance.get("gc_after", {}).get("FGC") != 0.0
    ):
        errors.append("v0.4.0 packaged-server and regression evidence is incomplete")
    return not errors


def _validate_manual(
    repository_root: Path, artifact_hash: str, tested_commit: str, errors: list[str]
) -> tuple[dict[str, Any], bool]:
    try:
        manual = _load_json(repository_root, EVIDENCE_ROOT / "client/manual-evidence.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}, False
    artifact = manual.get("artifact")
    observations = manual.get("observations")
    players = manual.get("players")
    executed = manual.get("executed_at")
    required_observations = {
        "two_players_simultaneously_online",
        "room_sealed_breathable_both",
        "wall_open_vacuum_both",
        "wall_resealed_breathable_both",
        "same_world_server_restart",
        "both_clients_rejoined_after_restart",
        "vent_nbt_restart_exact",
        "post_restart_volume_rebuilt",
        "space_full_suit_safe",
        "space_no_suit_vacuum",
        "space_partial_suit_incomplete",
        "space_oxygen_empty_damage",
        "space_oxygen_empty_death",
        "vacuum_death_message_observed",
        "hud_readable_gui_scale_2",
        "hud_readable_gui_scale_3",
        "server_clean_stop",
    }
    if (
        manual.get("schema_version") != 1
        or manual.get("version") != EXPECTED_VERSION
        or manual.get("build") != EXPECTED_BUILD
        or manual.get("tested_implementation_commit") != tested_commit
        or not isinstance(executed, list)
        or not executed
        or any(not _valid_date(value) for value in executed)
        or not isinstance(artifact, dict)
        or artifact.get("filename") != EXPECTED_ARTIFACT
        or artifact.get("sha256") != artifact_hash
        or artifact.get("bytes") != 466433
        or artifact.get("all_copies_equal") is not True
        or set(artifact.get("copies", {})) != {"source", "server", "client_a", "client_b"}
        or set(artifact.get("copies", {}).values()) != {artifact_hash}
        or not isinstance(players, list)
        or {player.get("username") for player in players} != {"ARCEV040A", "ARCEV040B"}
        or not isinstance(observations, dict)
        or any(observations.get(key) is not True for key in required_observations)
        or observations.get("client_exit_codes") != {"A": 0, "B": 0}
        or observations.get("project_client_linkage_failures") != 0
    ):
        errors.append("v0.4.0 manual player-flow evidence is incomplete")

    screenshots = manual.get("screenshots")
    recorded_names: set[str] = set()
    if not isinstance(screenshots, list):
        errors.append("v0.4.0 manual evidence has no screenshot inventory")
    else:
        for record in screenshots:
            raw = record.get("path") if isinstance(record, dict) else None
            try:
                relative = _safe_relative(raw)
                path = _regular_file(repository_root, raw, MAX_MEDIA_BYTES)
                if path.suffix.lower() == ".png":
                    dimensions = _png_dimensions(path)
                    frames = None
                elif path.suffix.lower() == ".gif":
                    dimensions, frames = _gif_metadata(path)
                else:
                    raise EvidenceError(f"unsupported screenshot evidence: {raw}")
            except (EvidenceError, ValueError) as exc:
                errors.append(str(exc))
                continue
            recorded_names.add(relative.name)
            if (
                relative.parent.as_posix() != (EVIDENCE_ROOT / "client/screenshots").as_posix()
                or record.get("sha256") != _sha256(path)
                or record.get("bytes") != path.stat().st_size
                or record.get("dimensions") != dimensions
                or not isinstance(record.get("subject"), str)
                or not record.get("subject")
            ):
                errors.append(f"v0.4.0 screenshot binding differs: {raw}")
            if path.suffix.lower() == ".gif" and (
                frames != 80
                or record.get("frames") != 80
                or record.get("duration_seconds") != 20.0
            ):
                errors.append("v0.4.0 room transition animation is incomplete")
    actual_names = {
        path.name
        for path in (repository_root / EVIDENCE_ROOT / "client/screenshots").iterdir()
        if path.is_file()
    }
    if recorded_names != EXPECTED_SCREENSHOTS or actual_names != EXPECTED_SCREENSHOTS:
        errors.append("v0.4.0 screenshot inventory is incomplete or contains extras")

    logs = manual.get("filtered_logs")
    recorded_logs: set[str] = set()
    if not isinstance(logs, list):
        errors.append("v0.4.0 manual evidence has no filtered-log inventory")
    else:
        for record in logs:
            raw = record.get("path") if isinstance(record, dict) else None
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
                or record.get("bytes") != path.stat().st_size
                or re.search(r"[A-Za-z]:[\\/]", text)
            ):
                errors.append(f"v0.4.0 filtered-log binding differs: {raw}")
    actual_logs = {
        path.name
        for path in (repository_root / EVIDENCE_ROOT / "client/logs").glob("*.txt")
    }
    if recorded_logs != EXPECTED_FILTERED_LOGS or actual_logs != EXPECTED_FILTERED_LOGS:
        errors.append("v0.4.0 filtered-log inventory is incomplete or contains extras")

    human_approved = (
        manual.get("review_status") == "APPROVED"
        and manual.get("reviewer") in AUTHORIZED_REVIEWERS
        and _valid_date(manual.get("reviewed_at"))
        and manual.get("approved_gates") == ["G0", "G8", "G9"]
    )
    if not human_approved:
        errors.append("v0.4.0 human review is incomplete or unauthorized")
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
            errors.append(f"v0.4.0 checksums line {line_number} is invalid")
            continue
        try:
            relative = _safe_relative(parts[1].strip()).as_posix()
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if relative in recorded:
            errors.append(f"v0.4.0 checksums repeats {relative}")
        recorded[relative] = parts[0]
    evidence_files = {
        path.relative_to(repository_root).as_posix()
        for path in (repository_root / EVIDENCE_ROOT).rglob("*")
        if path.is_file()
    }
    main = artifact_summary.get("main_jar", {})
    sources = artifact_summary.get("sources_jar", {})
    artifact_paths = {main.get("path"), sources.get("path")}
    expected = evidence_files | artifact_paths
    if None in expected:
        errors.append("v0.4.0 artifact checksum paths are missing")
        expected.discard(None)
    if set(recorded) != expected:
        errors.append("v0.4.0 checksums inventory is incomplete or contains extras")
    for relative in sorted(evidence_files):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_MEDIA_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.4.0 checksum mismatch: {relative}")
    for record in (main, sources):
        if recorded.get(record.get("path")) != record.get("sha256"):
            errors.append("v0.4.0 checksums omit or change an artifact binding")
    return not errors


def validate_v040_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
    *,
    require_approved: bool = False,
) -> tuple[list[str], dict[str, Any]]:
    """Return bounded validation errors and v0.4.0 Gate readiness details."""
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
    manual, human_approved = _validate_manual(
        repository_root, artifact_hash, tested_commit, errors
    )
    client_ready = len(errors) == before and bool(manual)
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before
    before = len(errors)
    checksums_ready = _validate_checksums(repository_root, artifact_summary, errors) and len(errors) == before
    if require_approved and not human_approved:
        errors.append("v0.4.0 evidence has not received explicit G0/G8/G9 owner approval")

    results = automated.get("results", {}) if isinstance(automated, dict) else {}
    security = automated.get("security", {}) if isinstance(automated, dict) else {}
    performance = automated.get("performance", {}) if isinstance(automated, dict) else {}
    details = {
        "artifact_sha256": artifact_hash,
        "tested_implementation_commit": tested_commit,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "data_ready": automated_ready and results.get("datagen", {}).get("git_diff_clean") is True,
        "automated_ready": automated_ready,
        "server_ready": server_ready and client_ready,
        "persistence_ready": server_ready and client_ready,
        "authority_ready": automated_ready
        and security.get("server_authoritative_breathability") is True,
        "performance_ready": server_ready
        and performance.get("packaged_duration_seconds") == 300.0,
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
    errors, details = validate_v040_release_evidence(
        ROOT, artifact=args.artifact, require_approved=args.require_approved
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(
        "[PASS] v0.4.0 release evidence is complete: "
        + ", ".join(key for key, value in details.items() if value is True)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
