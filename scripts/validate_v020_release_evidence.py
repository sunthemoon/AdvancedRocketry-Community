#!/usr/bin/env python3
"""Validate the committed v0.2.0 machine-slice acceptance evidence."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any

if __package__:
    from .validate_release_checksums import load_artifact_metadata
else:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from validate_release_checksums import load_artifact_metadata


ROOT = Path(__file__).resolve().parents[1]
RELEASE_ROOT = Path("docs/releases/v0.2.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
PROVENANCE_RECORD = Path("docs/provenance/v0.2.0-electrolyzer.md")
GENERATED_RECORD = Path("docs/provenance/v0.2.0-generated-resources.json")
EXPECTED_VERSION = "v0.2.0"
EXPECTED_BUILD = "1.20.1-0.2.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.2.0-dev.jar"
EXPECTED_SOURCES_ARTIFACT = (
    "advancedrocketry-community-1.20.1-0.2.0-dev-sources.jar"
)
AUTHORIZED_REVIEWERS = {"sunthemoon"}
EXPECTED_SCREENSHOTS = {
    "mods_zh_cn.png",
    "singleplayer_world.png",
    "electrolyzer_gui_scale_1.png",
    "electrolyzer_gui_scale_2.png",
    "electrolyzer_gui_scale_3.png",
    "electrolyzer_gui_scale_4.png",
    "dedicated_first_join.png",
    "dedicated_restart_rejoin.png",
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
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_TEXT_BYTES = 512 * 1024
MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024
SHA256 = re.compile(r"[0-9a-f]{64}")


class EvidenceError(ValueError):
    """Raised when an evidence input cannot be read safely."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise EvidenceError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _safe_relative(raw: str) -> PurePosixPath:
    if not isinstance(raw, str) or not raw or "\\" in raw or "\x00" in raw:
        raise EvidenceError(f"unsafe evidence path: {raw!r}")
    path = PurePosixPath(raw)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise EvidenceError(f"unsafe evidence path: {raw!r}")
    return path


def _regular_file(repository_root: Path, raw: str, limit: int) -> Path:
    relative = _safe_relative(raw)
    root = repository_root.resolve()
    candidate = repository_root.joinpath(*relative.parts)
    try:
        resolved = candidate.resolve(strict=True)
        resolved.relative_to(root)
    except (OSError, ValueError) as exc:
        raise EvidenceError(f"evidence path escapes or is missing: {raw}") from exc
    if candidate.is_symlink() or not candidate.is_file():
        raise EvidenceError(f"evidence path is not a regular file: {raw}")
    size = candidate.stat().st_size
    if size <= 0 or size > limit:
        raise EvidenceError(f"evidence file size is outside bounds: {raw}")
    return candidate


def _load_json(repository_root: Path, relative: Path) -> dict[str, Any]:
    path = _regular_file(repository_root, relative.as_posix(), MAX_JSON_BYTES)
    try:
        value = json.loads(
            path.read_text(encoding="utf-8", errors="strict"),
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"invalid JSON {relative.as_posix()}: {exc}") from exc
    if not isinstance(value, dict):
        raise EvidenceError(f"JSON root must be an object: {relative.as_posix()}")
    return value


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _valid_date(value: object) -> bool:
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        return False
    try:
        dt.date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _png_dimensions(path: Path) -> list[int]:
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) != 24 or not header.startswith(b"\x89PNG\r\n\x1a\n"):
        raise EvidenceError(f"invalid PNG evidence: {path.name}")
    return [int.from_bytes(header[16:20], "big"), int.from_bytes(header[20:24], "big")]


def _validate_provenance(repository_root: Path, errors: list[str]) -> bool:
    try:
        provenance_path = _regular_file(
            repository_root, PROVENANCE_RECORD.as_posix(), MAX_TEXT_BYTES
        )
        provenance = provenance_path.read_text(encoding="utf-8", errors="strict")
        review = _load_json(
            repository_root, EVIDENCE_ROOT / "provenance/human-review.json"
        )
        generated = _load_json(repository_root, GENERATED_RECORD)
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return False
    required_text = {
        "target_version: v0.2.0",
        "status: APPROVED",
        "reviewer: sunthemoon",
        "reviewed_at: 2026-08-31",
        "copied_source_files: 0",
        "copied_binary_assets: 0",
    }
    if any(value not in provenance for value in required_text):
        errors.append("v0.2.0 provenance record is not cleanly owner-approved")
    if (
        review.get("schema_version") != 1
        or review.get("version") != EXPECTED_VERSION
        or review.get("outcome") != "APPROVED"
        or review.get("reviewer") not in AUTHORIZED_REVIEWERS
        or not _valid_date(review.get("reviewed_at"))
        or review.get("findings") != []
        or review.get("copied_source_files") != 0
        or review.get("copied_binary_assets") != 0
        or review.get("provenance_document_sha256") != _sha256(provenance_path)
    ):
        errors.append("v0.2.0 human provenance review is incomplete or unbound")
    targets = generated.get("targets")
    if (
        generated.get("target_version") != EXPECTED_VERSION
        or generated.get("status") != "COMMUNITY_AUTHORED_DATAGEN"
        or not isinstance(targets, list)
        or len(targets) != 15
    ):
        errors.append("v0.2.0 generated-resource inventory is incomplete")
    return not errors


def _validate_artifact(
    repository_root: Path, artifact: Path | None, errors: list[str]
) -> tuple[dict[str, Any], str]:
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
        return {}, ""
    metadata, metadata_errors = load_artifact_metadata(manifest_path)
    errors.extend(f"v0.2.0 {error}" for error in metadata_errors)
    main = summary.get("main_jar")
    sources = summary.get("sources_jar")
    copies = summary.get("packaged_copies")
    repeated = summary.get("repeated_clean_builds")
    content = summary.get("content_manifest")
    if not all(isinstance(item, dict) for item in (main, sources, copies, repeated, content)):
        errors.append("v0.2.0 artifact summary is missing required records")
        return summary, ""
    main_hash = main.get("sha256")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or main.get("path") != f"build/libs/{EXPECTED_ARTIFACT}"
        or main.get("filename") != EXPECTED_ARTIFACT
        or not isinstance(main_hash, str)
        or SHA256.fullmatch(main_hash) is None
        or sources.get("path") != f"build/libs/{EXPECTED_SOURCES_ARTIFACT}"
        or not isinstance(sources.get("sha256"), str)
        or SHA256.fullmatch(sources["sha256"]) is None
        or repeated.get("count") != 2
        or repeated.get("byte_identical") is not True
        or repeated.get("sha256_values") != [main_hash, main_hash]
        or copies.get("all_equal") is not True
        or {
            copies.get("source_sha256"),
            copies.get("client_sha256"),
            copies.get("server_sha256"),
        }
        != {main_hash}
        or content.get("path")
        != (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix()
        or content.get("sha256") != _sha256(manifest_path)
        or metadata is None
        or metadata.filename != EXPECTED_ARTIFACT
        or metadata.sha256 != main_hash
        or content.get("entry_count") != metadata.manifest.get("entry_count")
    ):
        errors.append("v0.2.0 artifact summary does not bind the accepted JAR and copies")
    if artifact is not None and isinstance(main_hash, str):
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if not actual.is_file() or actual.stat().st_size > 64 * 1024 * 1024:
                errors.append("artifact is missing, unsafe, or too large")
            elif _sha256(actual) != main_hash:
                errors.append("artifact SHA-256 differs from the accepted v0.2.0 binding")
    return summary, main_hash if isinstance(main_hash, str) else ""


def _validate_automated(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> dict[str, Any]:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "automated/summary.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    commands = summary.get("commands")
    conservation = summary.get("conservation")
    authority = summary.get("authority")
    performance = summary.get("performance")
    data = summary.get("data_generation")
    if (
        summary.get("schema_version") != 1
        or summary.get("version") != EXPECTED_VERSION
        or summary.get("artifact_sha256") != artifact_hash
        or not isinstance(commands, dict)
        or any(
            not isinstance(commands.get(name), dict)
            or commands[name].get("status") != "PASS"
            for name in (
                "clean_build",
                "junit",
                "python_unittest",
                "jar_audit",
                "client_boundary",
                "run_data",
                "game_tests",
                "repository_validation",
            )
        )
        or commands.get("junit", {}).get("tests") != 12
        or commands.get("game_tests", {}).get("tests") != 12
        or commands.get("game_tests", {}).get("completion_marker") is not True
        or not isinstance(conservation, dict)
        or conservation.get("cycles") != 50
        or conservation.get("input_canisters") != 100
        or conservation.get("output_canisters") != 100
        or conservation.get("water_mb") != 50_000
        or conservation.get("energy_fe") != 100_000
        or not isinstance(data, dict)
        or data.get("target_count") != 15
        or data.get("worktree_clean") is not True
    ):
        errors.append("v0.2.0 automated evidence is incomplete")
    if (
        not isinstance(authority, dict)
        or authority.get("server_authoritative") is not True
        or authority.get("custom_c2s_result_packets") != 0
        or authority.get("two_menu_viewers_consistent") is not True
        or authority.get("bounded_serializer") is not True
    ):
        errors.append("v0.2.0 authority evidence is incomplete")
    if (
        not isinstance(performance, dict)
        or performance.get("idle_machines") != 20
        or performance.get("bounded_tick_work") is not True
        or performance.get("idle_log_spam") is not False
        or performance.get("unbounded_world_scan") is not False
    ):
        errors.append("v0.2.0 performance evidence is incomplete")
    return summary


def _validate_server(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> dict[str, Any]:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "dedicated-server/summary.json"
        )
        for name in ("first-start.txt", "restart.txt"):
            _regular_file(
                repository_root,
                (EVIDENCE_ROOT / "dedicated-server" / name).as_posix(),
                MAX_TEXT_BYTES,
            )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    cycles = summary.get("cycles")
    world = summary.get("world")
    if (
        summary.get("artifact") != EXPECTED_ARTIFACT
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("server_artifact_sha256") != artifact_hash
        or summary.get("minecraft") != "1.20.1"
        or summary.get("forge") != "47.4.10"
        or summary.get("mod_version") != EXPECTED_BUILD
        or summary.get("manual_player_cycles") is not True
        or summary.get("same_player_verified") is not True
        or not isinstance(world, dict)
        or world.get("same_world_verified") is not True
        or not isinstance(cycles, list)
        or len(cycles) != 2
    ):
        errors.append("v0.2.0 dedicated-server summary lacks the two-cycle contract")
        cycles = []
    for cycle in cycles:
        if not isinstance(cycle, dict) or any(
            (
                cycle.get("exit_code") != 0,
                cycle.get("mod_marker") != EXPECTED_BUILD,
                cycle.get("status_version") != "1.20.1",
                cycle.get("status_protocol") != 763,
                cycle.get("player_join_observed") is not True,
                cycle.get("player_leave_observed") is not True,
                cycle.get("project_error_count") != 0,
                cycle.get("project_warning_count") != 0,
                cycle.get("client_linkage_failure_count") != 0,
            )
        ):
            name = cycle.get("name", "<invalid>") if isinstance(cycle, dict) else "<invalid>"
            errors.append(f"v0.2.0 dedicated-server cycle is incomplete: {name}")
    return summary


def _validate_machine(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> dict[str, Any]:
    try:
        summary = _load_json(
            repository_root, EVIDENCE_ROOT / "machine-restart/summary.json"
        )
        _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "machine-restart/lifecycle.txt").as_posix(),
            MAX_TEXT_BYTES,
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    before = summary.get("before_restart")
    after = summary.get("after_restart")
    if (
        summary.get("schema_version") != 1
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("paused_state_preserved") is not True
        or summary.get("atomic_completion_verified") is not True
        or summary.get("same_world_verified") is not True
        or not isinstance(before, dict)
        or not isinstance(after, dict)
        or before.get("exit_code") != 0
        or after.get("exit_code") != 0
        or "progress: 40" not in str(before.get("paused_snapshot"))
        or "progress: 40" not in str(after.get("paused_snapshot"))
        or "hydrogen_canister" not in str(after.get("completed_snapshot"))
        or "oxygen_canister" not in str(after.get("completed_snapshot"))
    ):
        errors.append("v0.2.0 machine restart evidence is incomplete")
    return summary


def _validate_client(
    repository_root: Path, artifact_hash: str, errors: list[str]
) -> dict[str, Any]:
    try:
        manual = _load_json(repository_root, EVIDENCE_ROOT / "client/manual-evidence.json")
        log_review = _load_json(
            repository_root, EVIDENCE_ROOT / "client/logs/client-log-review.json"
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}
    approval = manual.get("human_gate_approval")
    binding = manual.get("artifact_binding")
    observations = manual.get("observations")
    if (
        manual.get("schema_version") != 1
        or manual.get("version") != EXPECTED_VERSION
        or manual.get("build") != EXPECTED_BUILD
        or manual.get("status") != "PASS"
        or manual.get("reviewed_by") not in AUTHORIZED_REVIEWERS
        or not _valid_date(manual.get("reviewed_at"))
        or not isinstance(approval, dict)
        or approval.get("G8") != "APPROVED"
        or approval.get("G9") != "APPROVED"
        or approval.get("findings") != []
        or not isinstance(binding, dict)
        or binding.get("all_equal") is not True
        or {
            binding.get("source_sha256"),
            binding.get("client_sha256"),
            binding.get("server_sha256"),
        }
        != {artifact_hash}
        or manual.get("gui_scales_verified") != [1, 2, 3, 4]
    ):
        errors.append("v0.2.0 packaged-client evidence is not owner-approved or artifact-bound")
    required_observations = {
        "mods_page_identity",
        "singleplayer_world_entry",
        "electrolyzer_place_and_open",
        "energy_water_progress_and_status_visible",
        "redstone_pause_visible",
        "inventory_labels_do_not_overlap",
        "gui_scale_1",
        "gui_scale_2",
        "gui_scale_3",
        "gui_scale_4",
        "dedicated_first_join",
        "dedicated_restart_rejoin",
        "clean_shutdown",
    }
    if not isinstance(observations, dict) or any(
        observations.get(name) != "PASS" for name in required_observations
    ) or observations.get("missing_texture_findings") != 0:
        errors.append("v0.2.0 packaged-client observations are incomplete")

    screenshots = manual.get("screenshots")
    observed_names: set[str] = set()
    observed_scales: set[int] = set()
    if not isinstance(screenshots, list) or len(screenshots) != len(EXPECTED_SCREENSHOTS):
        errors.append("v0.2.0 packaged-client evidence must list exactly eight screenshots")
        screenshots = []
    for record in screenshots:
        if not isinstance(record, dict):
            errors.append("v0.2.0 screenshot record is not an object")
            continue
        raw = record.get("file")
        if not isinstance(raw, str):
            errors.append("v0.2.0 screenshot record has no file")
            continue
        try:
            path = _regular_file(repository_root, raw, MAX_SCREENSHOT_BYTES)
            relative = PurePosixPath(raw)
            if relative.parent.as_posix() != (
                EVIDENCE_ROOT / "client/screenshots"
            ).as_posix():
                raise EvidenceError(f"screenshot is outside the canonical directory: {raw}")
            observed_names.add(relative.name)
            if record.get("sha256") != _sha256(path):
                errors.append(f"v0.2.0 screenshot hash mismatch: {raw}")
            if record.get("bytes") != path.stat().st_size:
                errors.append(f"v0.2.0 screenshot size mismatch: {raw}")
            if record.get("dimensions") != _png_dimensions(path):
                errors.append(f"v0.2.0 screenshot dimensions mismatch: {raw}")
            scale = record.get("effective_gui_scale")
            if scale is not None:
                if scale not in {1, 2, 3, 4}:
                    errors.append(f"v0.2.0 screenshot GUI scale is invalid: {raw}")
                else:
                    observed_scales.add(scale)
        except EvidenceError as exc:
            errors.append(str(exc))
    if observed_names != EXPECTED_SCREENSHOTS or observed_scales != {1, 2, 3, 4}:
        errors.append("v0.2.0 packaged-client screenshot set differs from the required matrix")

    runs = log_review.get("runs")
    if not isinstance(runs, dict) or set(runs) != {"singleplayer", "dedicated"}:
        errors.append("v0.2.0 client log review must contain singleplayer and dedicated runs")
        runs = {}
    for name, run in runs.items():
        if not isinstance(run, dict) or any(
            (
                run.get("project_error_count") != 0,
                run.get("project_fatal_count") != 0,
                run.get("client_linkage_failure_count") != 0,
                run.get("project_initialized") is not True,
                run.get("clean_shutdown_observed") is not True,
            )
        ):
            errors.append(f"v0.2.0 client log review has a blocking finding: {name}")
            continue
        try:
            _regular_file(repository_root, run.get("filtered_log", ""), MAX_TEXT_BYTES)
        except EvidenceError as exc:
            errors.append(str(exc))
    return manual


def _validate_checksums(
    repository_root: Path, artifact_summary: dict[str, Any], errors: list[str]
) -> bool:
    try:
        path = _regular_file(
            repository_root, (RELEASE_ROOT / "checksums.txt").as_posix(), MAX_TEXT_BYTES
        )
        text = path.read_text(encoding="utf-8", errors="strict")
    except (EvidenceError, UnicodeError) as exc:
        errors.append(str(exc))
        return False
    recorded: dict[str, str] = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  (\S+)", line)
        if match is None or match.group(2) in recorded:
            errors.append("v0.2.0 checksums.txt has a malformed or duplicate entry")
            continue
        try:
            _safe_relative(match.group(2))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        recorded[match.group(2)] = match.group(1)
    evidence_dir = repository_root / EVIDENCE_ROOT
    expected_paths = {
        item.relative_to(repository_root).as_posix()
        for item in evidence_dir.rglob("*")
        if item.is_file()
    }
    listed_evidence = {
        item for item in recorded if item.startswith(EVIDENCE_ROOT.as_posix() + "/")
    }
    if listed_evidence != expected_paths:
        errors.append("v0.2.0 checksum inventory differs from committed evidence files")
    for relative in sorted(expected_paths):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_SCREENSHOT_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.2.0 checksum mismatch: {relative}")
    for key in ("main_jar", "sources_jar"):
        record = artifact_summary.get(key)
        if not isinstance(record, dict) or recorded.get(record.get("path")) != record.get("sha256"):
            errors.append("v0.2.0 checksums.txt omits or changes an artifact binding")
    return not errors


def _validate_docs(repository_root: Path, errors: list[str]) -> bool:
    for name in sorted(REQUIRED_RELEASE_DOCS):
        try:
            _regular_file(repository_root, (RELEASE_ROOT / name).as_posix(), MAX_TEXT_BYTES)
        except EvidenceError as exc:
            errors.append(str(exc))
    return not errors


def validate_v020_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
) -> tuple[list[str], dict[str, Any]]:
    """Return validation errors and evidence readiness for all v0.2.0 Gates."""
    repository_root = repository_root.resolve()
    errors: list[str] = []

    before = len(errors)
    provenance_ready = _validate_provenance(repository_root, errors) and len(errors) == before
    before = len(errors)
    artifact_summary, artifact_hash = _validate_artifact(repository_root, artifact, errors)
    artifact_ready = len(errors) == before and bool(artifact_hash)
    before = len(errors)
    automated = _validate_automated(repository_root, artifact_hash, errors)
    automated_ready = len(errors) == before and bool(automated)
    before = len(errors)
    server = _validate_server(repository_root, artifact_hash, errors)
    server_ready = len(errors) == before and bool(server)
    before = len(errors)
    machine = _validate_machine(repository_root, artifact_hash, errors)
    persistence_ready = len(errors) == before and bool(machine)
    before = len(errors)
    client = _validate_client(repository_root, artifact_hash, errors)
    client_ready = len(errors) == before and bool(client)
    before = len(errors)
    checksums_ready = _validate_checksums(repository_root, artifact_summary, errors)
    checksums_ready = checksums_ready and len(errors) == before
    before = len(errors)
    docs_ready = _validate_docs(repository_root, errors) and len(errors) == before

    authority = automated.get("authority", {}) if isinstance(automated, dict) else {}
    performance = automated.get("performance", {}) if isinstance(automated, dict) else {}
    data = automated.get("data_generation", {}) if isinstance(automated, dict) else {}
    details = {
        "artifact_sha256": artifact_hash,
        "provenance_ready": provenance_ready,
        "artifact_ready": artifact_ready,
        "data_ready": automated_ready and data.get("worktree_clean") is True,
        "automated_ready": automated_ready,
        "server_ready": server_ready,
        "persistence_ready": persistence_ready,
        "authority_ready": automated_ready and authority.get("server_authoritative") is True,
        "performance_ready": automated_ready and performance.get("bounded_tick_work") is True,
        "client_ready": client_ready,
        "docs_ready": checksums_ready and docs_ready,
        "checksums_ready": checksums_ready,
        "human_approved": client.get("reviewed_by") in AUTHORIZED_REVIEWERS,
        "human_approved_at": client.get("reviewed_at"),
    }
    return errors, details


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    errors, details = validate_v020_release_evidence(artifact=args.artifact)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(
        "[PASS] v0.2.0 release evidence: provenance, repeated artifact, automated "
        "tests, matching-client server restart, machine persistence, client matrix, "
        "and checksums"
    )
    print(f"[INFO] Accepted JAR SHA-256: {details['artifact_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
