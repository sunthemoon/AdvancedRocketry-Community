#!/usr/bin/env python3
"""Validate the v0.0.2 G4 applicability ADR and canonical evidence binding."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import stat
import sys
from pathlib import Path
from typing import Any

if __package__:
    from .collect_v002_manual_evidence import (
        COMMITTED_BUNDLE,
        RECORD_NAME,
        validate_bundle,
    )
else:
    # ``python -I -S path/to/script.py`` intentionally omits the script
    # directory from sys.path. Add only this already-selected repository script
    # directory after all standard-library imports so the sibling validator can
    # be loaded without enabling ambient Python paths or site customization.
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from collect_v002_manual_evidence import (
        COMMITTED_BUNDLE,
        RECORD_NAME,
        validate_bundle,
    )


ROOT = Path(__file__).resolve().parents[1]
ADR_PATH = Path("docs/decisions/ADR-005-V0.0.2-G4-APPLICABILITY.md")
MAX_ADR_BYTES = 256 * 1024
MAX_JSON_DEPTH = 32
MAX_JSON_NODES = 4096
MAX_TEXT_LENGTH = 4096
FILE_ATTRIBUTE_REPARSE_POINT = 0x400

CASE_IDS = (
    "project_state_synchronization",
    "two_player_consistency",
    "chunk_unload_behavior",
    "configuration_mismatch",
    "optional_client_dependency_absence",
)
CASE_DECISIONS = {
    "PENDING",
    "ACCEPT_NOT_APPLICABLE",
    "REQUIRE_ADDITIONAL_TEST",
}
ADR_STATUSES = {"PROPOSED", "ACCEPTED"}
ALLOWED_REVIEWERS = frozenset({"sunthemoon"})
IDENTIFIER_RE = re.compile(r"[A-Za-z0-9_.@-]{1,128}")
JSON_BLOCK_RE = re.compile(r"```json[ \t]*\r?\n(.*?)\r?\n```", re.DOTALL)


class DuplicateJsonKeyError(ValueError):
    """Raised when a machine-readable ADR block contains duplicate keys."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _validate_json_bounds(value: Any, label: str) -> None:
    pending: list[tuple[Any, int]] = [(value, 1)]
    nodes = 0
    while pending:
        current, depth = pending.pop()
        nodes += 1
        if nodes > MAX_JSON_NODES:
            raise ValueError(f"{label} exceeds {MAX_JSON_NODES} JSON values")
        if depth > MAX_JSON_DEPTH:
            raise ValueError(f"{label} exceeds JSON depth {MAX_JSON_DEPTH}")
        if isinstance(current, str) and len(current) > MAX_TEXT_LENGTH:
            raise ValueError(f"{label} contains an overlong string")
        if isinstance(current, dict):
            pending.extend((item, depth + 1) for item in current.values())
        elif isinstance(current, list):
            pending.extend((item, depth + 1) for item in current)


def _parse_json_block(payload: str, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            payload,
            object_pairs_hook=_reject_duplicate_keys,
            parse_constant=lambda item: (_ for _ in ()).throw(
                ValueError(f"non-finite JSON value: {item}")
            ),
        )
    except (json.JSONDecodeError, DuplicateJsonKeyError, ValueError) as exc:
        raise ValueError(f"invalid {label}: {exc}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be a JSON object")
    _validate_json_bounds(value, label)
    return value


def _is_reparse(path: Path, status: os.stat_result | None = None) -> bool:
    observed = status if status is not None else path.lstat()
    return bool(getattr(observed, "st_file_attributes", 0) & FILE_ATTRIBUTE_REPARSE_POINT)


def _read_stable_file(path: Path, maximum: int, label: str) -> bytes:
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or _is_reparse(path, before):
        raise ValueError(f"{label} must be an ordinary file")
    if before.st_size < 0 or before.st_size > maximum:
        raise ValueError(f"{label} exceeds {maximum} bytes")
    with path.open("rb") as stream:
        payload = stream.read(maximum + 1)
        opened = os.fstat(stream.fileno())
    after = path.lstat()
    if len(payload) > maximum:
        raise ValueError(f"{label} exceeds {maximum} bytes")
    identity = lambda item: (
        item.st_dev,
        item.st_ino,
        item.st_size,
        item.st_mtime_ns,
        item.st_mode,
    )
    if identity(before) != identity(opened) or identity(opened) != identity(after):
        raise ValueError(f"{label} changed while it was read")
    return payload


def _exact_keys(
    value: Any,
    expected: set[str],
    label: str,
    errors: list[str],
) -> dict[str, Any]:
    if not isinstance(value, dict):
        errors.append(f"{label} must be an object")
        return {}
    missing = sorted(expected - set(value))
    extra = sorted(set(value) - expected)
    if missing:
        errors.append(f"{label} is missing keys: {', '.join(missing)}")
    if extra:
        errors.append(f"{label} has unexpected keys: {', '.join(extra)}")
    return value


def _valid_date(value: Any) -> bool:
    if not isinstance(value, str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        return False
    try:
        return dt.date.fromisoformat(value).isoformat() == value
    except ValueError:
        return False


def validate_adr_text(text: str) -> tuple[list[str], dict[str, Any]]:
    """Validate the two bounded JSON records embedded in ADR-005."""

    errors: list[str] = []
    blocks = JSON_BLOCK_RE.findall(text)
    if len(blocks) != 2:
        return ["ADR-005 must contain exactly two machine-readable JSON blocks"], {}
    try:
        metadata = _parse_json_block(blocks[0], "ADR metadata block")
        acceptance = _parse_json_block(blocks[1], "ADR acceptance block")
    except ValueError as exc:
        return [str(exc)], {}

    metadata = _exact_keys(
        metadata,
        {
            "status",
            "date",
            "deciders",
            "owner",
            "target_version",
            "expires",
            "recovery_condition",
            "automated_failure_reminder",
            "supersedes",
        },
        "ADR metadata",
        errors,
    )
    acceptance = _exact_keys(
        acceptance,
        set(CASE_IDS) | {"final_status"},
        "ADR acceptance record",
        errors,
    )

    status = metadata.get("status")
    if status not in ADR_STATUSES:
        errors.append(f"ADR metadata status is invalid: {status}")
    if not _valid_date(metadata.get("date")):
        errors.append("ADR metadata date must be YYYY-MM-DD")
    if metadata.get("owner") != "sunthemoon":
        errors.append("ADR metadata owner must remain sunthemoon")
    if metadata.get("target_version") != "v0.0.2":
        errors.append("ADR metadata target_version must remain v0.0.2")
    if metadata.get("expires") != "v0.1.0":
        errors.append("ADR metadata expires must remain v0.1.0")
    for field in ("recovery_condition", "automated_failure_reminder"):
        if not isinstance(metadata.get(field), str) or not metadata[field].strip():
            errors.append(f"ADR metadata {field} must be nonempty")
    if not isinstance(metadata.get("supersedes"), str):
        errors.append("ADR metadata supersedes must be a string")

    deciders = metadata.get("deciders")
    if not isinstance(deciders, list) or any(
        not isinstance(item, str) or IDENTIFIER_RE.fullmatch(item) is None
        for item in deciders
    ):
        errors.append("ADR metadata deciders must be an identifier list")
        decider_values: list[str] = []
    else:
        decider_values = list(deciders)
        if decider_values != sorted(set(decider_values)):
            errors.append("ADR metadata deciders must be unique and sorted")
        unauthorized_deciders = [
            item for item in decider_values if item not in ALLOWED_REVIEWERS
        ]
        if unauthorized_deciders:
            errors.append(
                "ADR metadata deciders are not authorized: "
                + ", ".join(unauthorized_deciders)
            )

    normalized_cases: dict[str, dict[str, str]] = {}
    recorded_reviewers: set[str] = set()
    for case_id in CASE_IDS:
        item = _exact_keys(
            acceptance.get(case_id),
            {"decision", "reviewed_by", "reviewed_at"},
            f"ADR acceptance {case_id}",
            errors,
        )
        decision = item.get("decision")
        reviewer = item.get("reviewed_by")
        reviewed_at = item.get("reviewed_at")
        if decision not in CASE_DECISIONS:
            errors.append(f"ADR acceptance {case_id} decision is invalid")
        if not isinstance(reviewer, str) or not isinstance(reviewed_at, str):
            errors.append(
                f"ADR acceptance {case_id} reviewer/date must be strings"
            )
            reviewer = ""
            reviewed_at = ""
        if decision == "PENDING":
            if reviewer or reviewed_at:
                errors.append(
                    f"ADR acceptance {case_id} pending decision must have empty reviewer/date"
                )
        elif (
            IDENTIFIER_RE.fullmatch(reviewer) is None
            or not _valid_date(reviewed_at)
        ):
            errors.append(
                f"ADR acceptance {case_id} decision requires reviewer and date"
            )
        else:
            if reviewer not in ALLOWED_REVIEWERS:
                errors.append(
                    f"ADR acceptance {case_id} reviewer is not authorized: {reviewer}"
                )
            recorded_reviewers.add(reviewer)
        normalized_cases[case_id] = {
            "decision": str(decision),
            "reviewed_by": reviewer,
            "reviewed_at": reviewed_at,
        }

    final_status = acceptance.get("final_status")
    if final_status != status:
        errors.append("ADR metadata status and acceptance final_status differ")
    if sorted(recorded_reviewers) != decider_values:
        errors.append("ADR metadata deciders differ from recorded case reviewers")
    if status == "ACCEPTED":
        rejected = [
            case_id
            for case_id, item in normalized_cases.items()
            if item["decision"] != "ACCEPT_NOT_APPLICABLE"
        ]
        if rejected:
            errors.append(
                "ADR ACCEPTED requires ACCEPT_NOT_APPLICABLE for every case: "
                + ", ".join(rejected)
            )
        if not decider_values:
            errors.append("ADR ACCEPTED requires at least one decider")

    return errors, {
        "status": status,
        "deciders": decider_values,
        "cases": normalized_cases,
    }


def cross_check_bundle_record(
    adr: dict[str, Any], bundle_record: dict[str, Any] | None
) -> list[str]:
    """Cross-check a validated ADR record with canonical client evidence."""

    errors: list[str] = []
    if bundle_record is None:
        if adr.get("status") == "ACCEPTED":
            errors.append("ADR-005 cannot be ACCEPTED without canonical client evidence")
        return errors

    if adr.get("status") != "ACCEPTED":
        errors.append("canonical client evidence requires ADR-005 status ACCEPTED")
    readiness = bundle_record.get("review_readiness")
    if not isinstance(readiness, dict) or readiness.get("status") != (
        "READY_FOR_HUMAN_GATE_REVIEW"
    ):
        errors.append(
            "canonical client evidence is not READY_FOR_HUMAN_GATE_REVIEW"
        )
    reviews = bundle_record.get("applicability_reviews")
    if not isinstance(reviews, dict):
        return errors + ["canonical client evidence has no applicability reviews"]
    for case_id in CASE_IDS:
        actual = reviews.get(case_id)
        expected = adr.get("cases", {}).get(case_id)
        if not isinstance(actual, dict) or not isinstance(expected, dict):
            errors.append(f"cannot compare applicability review {case_id}")
            continue
        for field in ("decision", "reviewed_by", "reviewed_at"):
            if actual.get(field) != expected.get(field):
                errors.append(
                    f"ADR-005 and canonical bundle differ for {case_id}.{field}"
                )
    return errors


def validate_v002_g4_applicability(
    repository_root: Path = ROOT,
) -> tuple[list[str], dict[str, Any]]:
    """Validate ADR-005 and, when present, its canonical evidence agreement."""

    root = repository_root.resolve()
    errors: list[str] = []
    try:
        payload = _read_stable_file(root / ADR_PATH, MAX_ADR_BYTES, "ADR-005")
        text = payload.decode("utf-8", errors="strict")
    except (OSError, UnicodeError, ValueError) as exc:
        return [f"cannot safely read ADR-005: {exc}"], {}
    adr_errors, details = validate_adr_text(text)
    errors.extend(adr_errors)
    if adr_errors:
        return errors, details

    bundle = root / COMMITTED_BUNDLE
    try:
        bundle.lstat()
    except FileNotFoundError:
        bundle_record = None
    except OSError as exc:
        return [f"cannot safely inspect canonical client evidence: {exc}"], details
    else:
        bundle_errors, bundle_record = validate_bundle(
            bundle,
            repository_root=root,
            require_acceptance_ready=True,
        )
        if bundle_errors:
            errors.append(
                "canonical client evidence is invalid: " + "; ".join(bundle_errors)
            )
            bundle_record = None
    errors.extend(cross_check_bundle_record(details, bundle_record))
    details = {
        **details,
        "canonical_bundle": (
            (COMMITTED_BUNDLE / RECORD_NAME).as_posix()
            if bundle_record is not None
            else None
        ),
    }
    return errors, details


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=ROOT,
        help="repository root (defaults to this project)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    errors, details = validate_v002_g4_applicability(args.repository_root)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    if details.get("status") == "ACCEPTED":
        print("[PASS] ADR-005 is ACCEPTED and matches canonical client evidence")
    else:
        print(
            "[PENDING] ADR-005 is structurally valid and remains PROPOSED; "
            "G4 is not approved"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
