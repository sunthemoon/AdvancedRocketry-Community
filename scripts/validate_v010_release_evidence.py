#!/usr/bin/env python3
"""Validate the committed v0.1.0 artifact, server, client, and review evidence."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
RELEASE_ROOT = Path("docs/releases/v0.1.0")
EVIDENCE_ROOT = RELEASE_ROOT / "evidence"
PROVENANCE_RECORD = Path("docs/provenance/v0.1.0-minimal-content.json")
EXPECTED_VERSION = "v0.1.0"
EXPECTED_BUILD = "1.20.1-0.1.0-dev"
EXPECTED_ARTIFACT = "advancedrocketry-community-1.20.1-0.1.0-dev.jar"
EXPECTED_SOURCES_ARTIFACT = (
    "advancedrocketry-community-1.20.1-0.1.0-dev-sources.jar"
)
AUTHORIZED_REVIEWERS = {"sunthemoon"}
EXPECTED_SCREENSHOTS = {
    "creative_tab_en_us.png",
    "creative_tab_zh_cn.png",
    "data_storage_tooltip_en_us.png",
    "data_storage_tooltip_zh_cn.png",
    "dedicated_first_join.png",
    "dedicated_restart_rejoin.png",
    "machine_casing_break_zh_cn.png",
    "machine_casing_orientations_zh_cn.png",
    "machine_casing_place_zh_cn.png",
    "mods_en_us.png",
    "mods_zh_cn.png",
}
MAX_JSON_BYTES = 2 * 1024 * 1024
MAX_TEXT_BYTES = 512 * 1024
MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024


class EvidenceError(ValueError):
    """Raised when one evidence input cannot be read safely."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise EvidenceError(f"duplicate JSON key: {key}")
        value[key] = item
    return value


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
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
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
    header = path.read_bytes()[:24]
    if len(header) != 24 or not header.startswith(b"\x89PNG\r\n\x1a\n"):
        raise EvidenceError(f"invalid PNG evidence: {path.name}")
    return [int.from_bytes(header[16:20], "big"), int.from_bytes(header[20:24], "big")]


def _validate_provenance(
    repository_root: Path, errors: list[str]
) -> tuple[dict[str, Any], bool]:
    try:
        manifest = _load_json(repository_root, PROVENANCE_RECORD)
        review = _load_json(repository_root, EVIDENCE_ROOT / "provenance/human-review.json")
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}, False
    decision = manifest.get("review")
    if not isinstance(decision, dict):
        errors.append("v0.1.0 provenance manifest has no review object")
        return manifest, False
    if (
        decision.get("status") != "APPROVED"
        or decision.get("reviewer") not in AUTHORIZED_REVIEWERS
        or not _valid_date(decision.get("reviewed_at"))
        or decision.get("findings") != []
    ):
        errors.append("v0.1.0 provenance manifest is not cleanly owner-approved")
    entries = manifest.get("entries")
    if not isinstance(entries, list) or len(entries) != 10:
        errors.append("v0.1.0 provenance manifest must contain exactly ten entries")
        entries = []
    if (
        review.get("outcome") != "APPROVED"
        or review.get("reviewer") != decision.get("reviewer")
        or review.get("reviewed_at") != decision.get("reviewed_at")
        or review.get("content_digest") != decision.get("content_digest")
        or review.get("findings") != []
        or review.get("sample_size") != 10
    ):
        errors.append("committed provenance human-review record does not match its decision")
    reviewed_entries = review.get("entries")
    expected = {
        item.get("target_path"): (
            item.get("source_path"),
            item.get("source_sha256"),
            item.get("target_sha256"),
            item.get("license"),
            item.get("status"),
        )
        for item in entries
        if isinstance(item, dict)
    }
    observed = {
        item.get("target_path"): (
            item.get("source_path"),
            item.get("source_sha256"),
            item.get("target_sha256"),
            item.get("license"),
            item.get("status"),
        )
        for item in reviewed_entries or []
        if isinstance(item, dict) and item.get("outcome") == "MATCH"
    }
    if observed != expected:
        errors.append("provenance human-review samples do not exactly cover the ten-entry batch")
    return manifest, not errors


def _validate_artifact(
    repository_root: Path, artifact: Path | None, errors: list[str]
) -> tuple[dict[str, Any], str]:
    try:
        summary = _load_json(repository_root, EVIDENCE_ROOT / "artifact/artifact-summary.json")
        content_path = _regular_file(
            repository_root,
            (EVIDENCE_ROOT / "artifact/jar-content-manifest.json").as_posix(),
            MAX_JSON_BYTES,
        )
        content = _load_json(
            repository_root, EVIDENCE_ROOT / "artifact/jar-content-manifest.json"
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return {}, ""
    main = summary.get("main_jar")
    sources = summary.get("sources_jar")
    copies = summary.get("packaged_copies")
    repeated = summary.get("repeated_clean_builds")
    content_record = summary.get("content_manifest")
    if not all(isinstance(item, dict) for item in (main, sources, copies, repeated, content_record)):
        errors.append("artifact summary is missing required records")
        return summary, ""
    main_hash = main.get("sha256")
    copy_hashes = {
        copies.get("source_sha256"),
        copies.get("client_sha256"),
        copies.get("server_sha256"),
    }
    if (
        summary.get("version") != EXPECTED_VERSION
        or summary.get("build") != EXPECTED_BUILD
        or main.get("path") != f"build/libs/{EXPECTED_ARTIFACT}"
        or sources.get("path") != f"build/libs/{EXPECTED_SOURCES_ARTIFACT}"
        or not isinstance(main_hash, str)
        or not re.fullmatch(r"[0-9a-f]{64}", main_hash)
        or copy_hashes != {main_hash}
        or copies.get("all_equal") is not True
        or repeated.get("count") != 2
        or repeated.get("byte_identical") is not True
        or repeated.get("sha256_values") != [main_hash, main_hash]
    ):
        errors.append("artifact summary does not bind two identical builds and matching copies")
    entries = content.get("entries", content.get("files"))
    if (
        not isinstance(entries, list)
        or content_record.get("entry_count") != len(entries)
        or content_record.get("sha256") != _sha256(content_path)
    ):
        errors.append("artifact content-manifest binding is invalid")
    if artifact is not None:
        try:
            actual = artifact.resolve(strict=True)
        except OSError as exc:
            errors.append(f"artifact cannot be read: {exc}")
        else:
            if not actual.is_file() or actual.stat().st_size > 64 * 1024 * 1024:
                errors.append("artifact is missing, unsafe, or too large")
            elif _sha256(actual) != main_hash:
                errors.append("artifact SHA-256 differs from the accepted v0.1.0 binding")
    return summary, main_hash if isinstance(main_hash, str) else ""


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
    if (
        summary.get("artifact") != EXPECTED_ARTIFACT
        or summary.get("artifact_sha256") != artifact_hash
        or summary.get("server_artifact_sha256") != artifact_hash
        or summary.get("minecraft") != "1.20.1"
        or summary.get("forge") != "47.4.10"
        or summary.get("mod_version") != EXPECTED_BUILD
        or summary.get("manual_player_cycles") is not True
        or summary.get("same_player_verified") is not True
        or summary.get("world", {}).get("same_world_verified") is not True
        or not isinstance(cycles, list)
        or len(cycles) != 2
    ):
        errors.append("dedicated-server summary lacks the matching-client two-cycle contract")
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
            errors.append(f"dedicated-server cycle is incomplete: {cycle.get('name', '<invalid>')}")
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
        manual.get("version") != EXPECTED_VERSION
        or manual.get("build") != EXPECTED_BUILD
        or manual.get("status") != "PASS"
        or manual.get("reviewed_by") not in AUTHORIZED_REVIEWERS
        or not _valid_date(manual.get("reviewed_at"))
        or not isinstance(approval, dict)
        or approval.get("G8") != "APPROVED"
        or approval.get("G9") != "APPROVED"
        or not isinstance(binding, dict)
        or binding.get("all_equal") is not True
        or {
            binding.get("source_sha256"),
            binding.get("client_sha256"),
            binding.get("server_sha256"),
        }
        != {artifact_hash}
    ):
        errors.append("packaged-client evidence is not owner-approved or artifact-bound")
    required_passes = {
        "mods_page_identity",
        "singleplayer_world_entry",
        "creative_tab_all_five_entries",
        "machine_casing_orientation_models",
        "manual_block_break",
        "manual_block_place",
        "zh_cn_names",
        "en_us_names",
        "expected_inert_behavior_notice",
        "clean_shutdown_both_runs",
        "dedicated_first_join",
        "dedicated_restart_rejoin",
    }
    if not isinstance(observations, dict) or any(
        observations.get(key) != "PASS" for key in required_passes
    ) or observations.get("missing_texture_findings") != 0 or observations.get(
        "item_model_findings"
    ) != 0:
        errors.append("packaged-client observations are incomplete")

    screenshots = manual.get("screenshots")
    observed_names: set[str] = set()
    if not isinstance(screenshots, list) or len(screenshots) != len(EXPECTED_SCREENSHOTS):
        errors.append("packaged-client evidence must list exactly eleven screenshots")
        screenshots = []
    for record in screenshots:
        if not isinstance(record, dict):
            errors.append("packaged-client screenshot record is not an object")
            continue
        raw = record.get("file")
        if not isinstance(raw, str):
            errors.append("packaged-client screenshot record has no file")
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
                errors.append(f"screenshot hash mismatch: {raw}")
            if record.get("bytes") != path.stat().st_size:
                errors.append(f"screenshot size mismatch: {raw}")
            if record.get("dimensions") != _png_dimensions(path):
                errors.append(f"screenshot dimensions mismatch: {raw}")
            if record.get("language") not in {"zh_cn", "en_us"}:
                errors.append(f"screenshot language is invalid: {raw}")
            if record.get("effective_gui_scale") not in {2, 3}:
                errors.append(f"screenshot GUI scale is invalid: {raw}")
        except EvidenceError as exc:
            errors.append(str(exc))
    if observed_names != EXPECTED_SCREENSHOTS:
        errors.append("packaged-client screenshot set differs from the required matrix")

    runs = log_review.get("runs")
    if not isinstance(runs, dict) or set(runs) != {"zh_cn", "en_us"}:
        errors.append("client log review must contain zh_cn and en_us runs")
        runs = {}
    for language, run in runs.items():
        if not isinstance(run, dict) or any(
            (
                run.get("error_count") != 0,
                run.get("fatal_count") != 0,
                run.get("project_warning_error_fatal_count") != 0,
                run.get("client_linkage_failure_count") != 0,
                run.get("project_initialized") is not True,
                run.get("singleplayer_login_observed") is not True,
                run.get("clean_shutdown_observed") is not True,
            )
        ):
            errors.append(f"client log review has a blocking finding: {language}")
            continue
        try:
            _regular_file(repository_root, run.get("filtered_log", ""), MAX_TEXT_BYTES)
        except EvidenceError as exc:
            errors.append(str(exc))
    return manual


def _validate_checksums(
    repository_root: Path, artifact_summary: dict[str, Any], errors: list[str]
) -> None:
    try:
        path = _regular_file(
            repository_root, (RELEASE_ROOT / "checksums.txt").as_posix(), MAX_TEXT_BYTES
        )
    except EvidenceError as exc:
        errors.append(str(exc))
        return
    recorded: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8", errors="strict").splitlines():
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  (\S+)", line)
        if match is None or match.group(2) in recorded:
            errors.append("v0.1.0 checksums.txt has a malformed or duplicate entry")
            continue
        recorded[match.group(2)] = match.group(1)
    evidence_dir = repository_root / EVIDENCE_ROOT
    expected_paths = {
        path.relative_to(repository_root).as_posix()
        for path in evidence_dir.rglob("*")
        if path.is_file()
    }
    missing = sorted(expected_paths.difference(recorded))
    extra_evidence = sorted(
        path for path in recorded if path.startswith(EVIDENCE_ROOT.as_posix() + "/") and path not in expected_paths
    )
    if missing or extra_evidence:
        errors.append(
            "v0.1.0 checksum inventory differs from committed evidence"
            + (f"; missing={missing}" if missing else "")
            + (f"; extra={extra_evidence}" if extra_evidence else "")
        )
    for relative in sorted(expected_paths):
        try:
            actual = _sha256(_regular_file(repository_root, relative, MAX_SCREENSHOT_BYTES))
        except EvidenceError as exc:
            errors.append(str(exc))
            continue
        if recorded.get(relative) != actual:
            errors.append(f"v0.1.0 checksum mismatch: {relative}")
    main = artifact_summary.get("main_jar", {})
    sources = artifact_summary.get("sources_jar", {})
    for record in (main, sources):
        if not isinstance(record, dict) or recorded.get(record.get("path")) != record.get("sha256"):
            errors.append("v0.1.0 checksums.txt omits or changes an artifact binding")


def validate_v010_release_evidence(
    repository_root: Path = ROOT,
    artifact: Path | None = None,
) -> tuple[list[str], dict[str, Any]]:
    repository_root = repository_root.resolve()
    errors: list[str] = []
    _, provenance_ready = _validate_provenance(repository_root, errors)
    artifact_errors_before = len(errors)
    artifact_summary, artifact_hash = _validate_artifact(repository_root, artifact, errors)
    artifact_ready = len(errors) == artifact_errors_before and bool(artifact_hash)
    server_errors_before = len(errors)
    server = _validate_server(repository_root, artifact_hash, errors)
    server_ready = len(errors) == server_errors_before and bool(server)
    client_errors_before = len(errors)
    client = _validate_client(repository_root, artifact_hash, errors)
    client_ready = len(errors) == client_errors_before and bool(client)
    checksum_errors_before = len(errors)
    _validate_checksums(repository_root, artifact_summary, errors)
    checksums_ready = len(errors) == checksum_errors_before
    details = {
        "artifact_sha256": artifact_hash,
        "artifact_ready": artifact_ready,
        "provenance_ready": provenance_ready,
        "server_ready": server_ready,
        "client_ready": client_ready,
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
    errors, details = validate_v010_release_evidence(artifact=args.artifact)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(
        "[PASS] v0.1.0 release evidence: artifact, provenance, packaged client, "
        "matching-client dedicated server, and checksums"
    )
    print(f"[INFO] Accepted JAR SHA-256: {details['artifact_sha256']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
