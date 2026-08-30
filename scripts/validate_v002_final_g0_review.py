#!/usr/bin/env python3
"""Validate commit-bound v0.0.2 final-G0 human review records.

The validator checks record structure and exact Git bindings. It never supplies
a reviewer answer, inspects visible pixels, or changes a release Gate.
"""

from __future__ import annotations

import sys

if __name__ == "__main__" and (not sys.flags.isolated or not sys.flags.no_site):
    print(
        "[FAIL] secure CLI execution requires Python isolated mode; rerun as "
        "python -I -S scripts/validate_v002_final_g0_review.py ...",
        file=sys.stderr,
    )
    raise SystemExit(2)

import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import stat
import struct
import subprocess
import threading
import types
import unicodedata
import zlib
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Callable


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_RECORD_PATH = Path("docs/releases/v0.0.2/RELEASE-EVIDENCE.md")
REVIEW_INPUT_TOOL_PATH = "scripts/prepare_v002_final_g0_review_inputs.py"
PROVENANCE_TOOL_PATH = "scripts/validate_bootstrap_provenance.py"
README_PATH = "README.md"

SCHEMA_VERSION = 1
FINAL_INPUT_REPORT_SCHEMA_VERSION = 2
RECORD_KIND = "V0_0_2_FINAL_G0_HUMAN_REVIEW_RECORDS"
PENDING = "PENDING_HUMAN_REVIEW"
APPROVED = "APPROVED"
CHANGES_REQUIRED = "CHANGES_REQUIRED"
OUTCOMES = {PENDING, APPROVED, CHANGES_REQUIRED}
APPROVED_PROVENANCE_STATUS = "THIRD_PARTY_APPROVED"

START_MARKER = b"<!-- v0.0.2-final-g0-review-records:start -->"
END_MARKER = b"<!-- v0.0.2-final-g0-review-records:end -->"
JSON_FENCE = re.compile(
    rb"\A[ \t\r\n]*```json[ \t]*\r?\n(?P<body>.*?)\r?\n```[ \t\r\n]*\Z",
    re.DOTALL,
)
FULL_OID = re.compile(r"[0-9a-f]{40}")
SHA256 = re.compile(r"[0-9a-f]{64}")

SOURCE_FIELDS = {
    "outcome",
    "selected_implementation_commit",
    "selected_tree_oid",
    "review_inputs_report",
    "review_inputs_report_sha256",
    "reviewer",
    "reviewed_at",
    "findings",
}
README_FIELDS = {
    "outcome",
    "selected_commit",
    "selected_tree_oid",
    "screenshot_file",
    "screenshot_sha256",
    "reviewer",
    "reviewed_at",
    "findings",
}
ROOT_FIELDS = {
    "schema_version",
    "record_kind",
    "record_semantics",
    "final_g0_source_resource_review",
    "final_g0_readme_visual_review",
}
EXPECTED_SEMANTICS = {
    "mechanical_validation_result": "INPUTS_ONLY",
    "gate_decision": "HUMAN_ONLY",
    "visible_pixel_judgment": "HUMAN_ONLY",
}
REPORT_ROOT_FIELDS = {
    "base_commit",
    "base_tree_oid",
    "bindings",
    "bootstrap_manifest_coverage",
    "jar_manifest_coverage",
    "history",
    "inventory",
    "inventory_scope",
    "prerequisites",
    "review_semantics",
    "schema_version",
    "scope_version",
    "selected_commit",
    "selected_tree_oid",
    "tool",
}
EXPECTED_REPORT_BINDINGS = {
    "bootstrap_manifest",
    "main_jar_content_manifest",
    "sources_jar_manifest",
}
EXPECTED_APPROVED_PREREQUISITE = {
    "ready_for_final_human_review": True,
    "record_status": "THIRD_PARTY_APPROVED",
    "state": "APPROVED_PREREQUISITE_OBSERVED",
}

SOURCE_REVIEW_ALLOWED_OUTPUT_PREFIXES = (
    "docs/releases/v0.0.2/evidence/g0-final",
    "docs/releases/v0.0.2/evidence/client",
)
SOURCE_REVIEW_ALLOWED_OUTPUT_PATHS = frozenset(
    {
        "CHANGELOG.md",
        DEFAULT_RECORD_PATH.as_posix(),
        "docs/decisions/ADR-005-V0.0.2-G4-APPLICABILITY.md",
        "docs/releases/v0.0.2/INSTALLATION.md",
        "docs/releases/v0.0.2/KNOWN-ISSUES.md",
        "docs/releases/v0.0.2/MANUAL-TEST.md",
        "docs/releases/v0.0.2/TEST-REPORT.md",
        "docs/releases/v0.0.2/checksums.txt",
        "docs/status/CURRENT_VERSION.md",
        "docs/status/GATE_STATUS.md",
        "docs/versions/V0.0.2-FORGE-BOOTSTRAP.md",
        "docs/work/v0.0.2-implementation-log.md",
        "docs/work/v0.0.2-test-machine-handoff.md",
    }
)

MAX_RECORD_BYTES = 2 * 1024 * 1024
MAX_REPORT_BYTES = 8 * 1024 * 1024
MAX_PNG_BYTES = 16 * 1024 * 1024
MAX_README_BYTES = 8 * 1024 * 1024
MAX_GIT_OUTPUT_BYTES = 16 * 1024 * 1024
MAX_JSON_DEPTH = 64
MAX_JSON_NODES = 200_000
MAX_PATH_BYTES = 512
MAX_PATH_DEPTH = 32
MAX_FINDINGS = 256
MAX_FINDING_BYTES = 4_096
MAX_REVIEWER_BYTES = 256
MAX_HISTORY_COMMITS = 4_096
MAX_CHANGED_PATHS = 100_000
GIT_TIMEOUT_SECONDS = 60

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PNG_MIN_WIDTH = 640
PNG_MIN_HEIGHT = 360
PNG_MAX_PIXELS = 4096 * 4096
PNG_MAX_CHUNKS = 128
PNG_MAX_CHUNK_BYTES = 16 * 1024 * 1024
PNG_CRITICAL_CHUNKS = {b"IHDR", b"PLTE", b"IDAT", b"IEND"}
PNG_PRIVACY_CHUNKS = {b"tEXt", b"zTXt", b"iTXt", b"eXIf", b"tIME"}
PNG_ALLOWED_ANCILLARY = {
    b"cHRM": 32,
    b"gAMA": 4,
    b"pHYs": 9,
    b"sRGB": 1,
    b"tRNS": 6,
}


class FinalG0ReviewError(ValueError):
    """Raised when a final-G0 record cannot be validated fail closed."""


class DuplicateJsonKeyError(ValueError):
    """Raised when a JSON object contains an ambiguous duplicate key."""


@dataclass(frozen=True)
class _InvalidationScope:
    prefixes: tuple[str, ...]
    exact_paths: frozenset[str]
    allowed_prefixes: tuple[str, ...] = ()
    allowed_exact_paths: frozenset[str] = frozenset()
    invalidate_all_other_paths: bool = False

    def matches(self, path: str) -> bool:
        if path in self.allowed_exact_paths or any(
            path == prefix or path.startswith(prefix + "/")
            for prefix in self.allowed_prefixes
        ):
            return False
        return self.invalidate_all_other_paths or path in self.exact_paths or any(
            path == prefix or path.startswith(prefix + "/")
            for prefix in self.prefixes
        )


def _sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def _git_blob_oid(content: bytes) -> str:
    return hashlib.sha1(f"blob {len(content)}\0".encode("ascii") + content).hexdigest()


def _is_reparse_point(path: Path, status: os.stat_result | None = None) -> bool:
    if status is None:
        status = path.lstat()
    attributes = getattr(status, "st_file_attributes", 0)
    marker = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    return bool(attributes & marker)


def _file_identity(status: os.stat_result) -> tuple[int, int, int, int, int, int]:
    return (
        status.st_dev,
        status.st_ino,
        status.st_size,
        status.st_mtime_ns,
        status.st_mode,
        status.st_nlink,
    )


def _safe_git_path(value: object, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise FinalG0ReviewError(f"{label} must be a non-empty string")
    if "\\" in value or "\x00" in value:
        raise FinalG0ReviewError(f"{label} must use normalized POSIX separators")
    try:
        encoded = value.encode("utf-8", errors="strict")
    except UnicodeEncodeError as exc:
        raise FinalG0ReviewError(f"{label} is not valid UTF-8 text") from exc
    if len(encoded) > MAX_PATH_BYTES:
        raise FinalG0ReviewError(f"{label} exceeds {MAX_PATH_BYTES} UTF-8 bytes")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise FinalG0ReviewError(f"{label} contains a control character")
    path = PurePosixPath(value)
    if path.is_absolute() or path.as_posix() != value:
        raise FinalG0ReviewError(f"{label} must be a normalized relative path")
    if any(part in ("", ".", "..") for part in path.parts):
        raise FinalG0ReviewError(f"{label} contains an unsafe path component")
    if len(path.parts) > MAX_PATH_DEPTH:
        raise FinalG0ReviewError(f"{label} exceeds {MAX_PATH_DEPTH} components")
    if unicodedata.normalize("NFC", value) != value:
        raise FinalG0ReviewError(f"{label} must use NFC Unicode normalization")
    return value


def _ordinary_root(repository_root: Path) -> Path:
    try:
        root = repository_root.resolve(strict=True)
        status = root.lstat()
    except OSError as exc:
        raise FinalG0ReviewError(f"cannot resolve repository root: {exc}") from exc
    if not stat.S_ISDIR(status.st_mode) or _is_reparse_point(root, status):
        raise FinalG0ReviewError("repository root must be an ordinary directory")
    return root


def _stable_worktree_file(
    repository_root: Path, relative_path: str, maximum: int, label: str
) -> bytes:
    relative_path = _safe_git_path(relative_path, label)
    candidate = repository_root.joinpath(*PurePosixPath(relative_path).parts)
    current = repository_root
    for part in PurePosixPath(relative_path).parts[:-1]:
        current = current / part
        try:
            status = current.lstat()
        except OSError as exc:
            raise FinalG0ReviewError(f"cannot inspect {label} directory: {exc}") from exc
        if not stat.S_ISDIR(status.st_mode) or _is_reparse_point(current, status):
            raise FinalG0ReviewError(f"{label} directory chain is not ordinary")
    try:
        before = candidate.lstat()
    except OSError as exc:
        raise FinalG0ReviewError(f"cannot inspect {label}: {exc}") from exc
    if (
        not stat.S_ISREG(before.st_mode)
        or _is_reparse_point(candidate, before)
        or before.st_nlink != 1
    ):
        raise FinalG0ReviewError(f"{label} must be an ordinary, non-hardlinked file")
    if before.st_size > maximum:
        raise FinalG0ReviewError(f"{label} exceeds {maximum} bytes")
    try:
        with candidate.open("rb") as handle:
            opened_before = os.fstat(handle.fileno())
            content = handle.read(maximum + 1)
            opened_after = os.fstat(handle.fileno())
        after = candidate.lstat()
    except OSError as exc:
        raise FinalG0ReviewError(f"cannot read {label}: {exc}") from exc
    if len(content) > maximum:
        raise FinalG0ReviewError(f"{label} exceeds {maximum} bytes")
    identities = {
        _file_identity(before),
        _file_identity(opened_before),
        _file_identity(opened_after),
        _file_identity(after),
    }
    if len(identities) != 1:
        raise FinalG0ReviewError(f"{label} changed while it was read")
    return content


def _git_environment() -> dict[str, str]:
    environment = dict(os.environ)
    for name in tuple(environment):
        if name.upper().startswith("GIT_TRACE"):
            environment.pop(name, None)
        if name.startswith("GIT_CONFIG_KEY_") or name.startswith("GIT_CONFIG_VALUE_"):
            environment.pop(name, None)
    for name in (
        "GIT_ALTERNATE_OBJECT_DIRECTORIES",
        "GIT_COMMON_DIR",
        "GIT_CONFIG",
        "GIT_CONFIG_COUNT",
        "GIT_CONFIG_PARAMETERS",
        "GIT_DIR",
        "GIT_EXEC_PATH",
        "GIT_EXTERNAL_DIFF",
        "GIT_INDEX_FILE",
        "GIT_NAMESPACE",
        "GIT_OBJECT_DIRECTORY",
        "GIT_REPLACE_REF_BASE",
        "GIT_SHALLOW_FILE",
        "GIT_WORK_TREE",
    ):
        environment.pop(name, None)
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_SYSTEM": os.devnull,
            "GIT_NO_LAZY_FETCH": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "GIT_TERMINAL_PROMPT": "0",
            "LC_ALL": "C",
        }
    )
    return environment


def _git_executable(repository_root: Path) -> str:
    candidate = shutil.which("git")
    if not candidate:
        raise FinalG0ReviewError("cannot locate Git on the trusted runtime PATH")
    try:
        path = Path(candidate).resolve(strict=True)
        status = path.lstat()
    except OSError as exc:
        raise FinalG0ReviewError(f"cannot resolve Git executable: {exc}") from exc
    if not stat.S_ISREG(status.st_mode) or _is_reparse_point(path, status):
        raise FinalG0ReviewError("Git executable must resolve to an ordinary file")
    try:
        path.relative_to(repository_root.resolve())
    except ValueError:
        pass
    else:
        raise FinalG0ReviewError("Git executable must not be contained in the repository")
    return str(path)


def _git_command(repository_root: Path, arguments: list[str]) -> list[str]:
    return [
        _git_executable(repository_root),
        "--no-pager",
        "--no-replace-objects",
        "--literal-pathspecs",
        "-c",
        "core.commitGraph=false",
        "-c",
        "core.fsmonitor=false",
        "-c",
        "core.untrackedCache=false",
        "-c",
        "diff.external=",
        "-C",
        str(repository_root),
        *arguments,
    ]


def _run_git(
    repository_root: Path,
    arguments: list[str],
    *,
    check: bool = True,
    maximum: int = MAX_GIT_OUTPUT_BYTES,
) -> subprocess.CompletedProcess[bytes]:
    command = _git_command(repository_root, arguments)
    process = subprocess.Popen(
        command,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=_git_environment(),
    )
    stdout = bytearray()
    stderr = bytearray()
    failures: list[BaseException] = []
    overflow: list[str] = []
    lock = threading.Lock()

    def consume(stream: Any, target: bytearray, label: str) -> None:
        try:
            while chunk := stream.read(64 * 1024):
                remaining = maximum - len(target)
                if len(chunk) > remaining:
                    target.extend(chunk[: max(remaining, 0)])
                    with lock:
                        overflow.append(label)
                    process.kill()
                    break
                target.extend(chunk)
        except BaseException as exc:  # pragma: no cover - OS pipe failure
            with lock:
                failures.append(exc)
            process.kill()
        finally:
            stream.close()

    assert process.stdout is not None and process.stderr is not None
    threads = [
        threading.Thread(target=consume, args=(process.stdout, stdout, "stdout"), daemon=True),
        threading.Thread(target=consume, args=(process.stderr, stderr, "stderr"), daemon=True),
    ]
    for thread in threads:
        thread.start()
    try:
        returncode = process.wait(timeout=GIT_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired as exc:
        process.kill()
        process.wait()
        for thread in threads:
            thread.join()
        raise FinalG0ReviewError("Git command exceeded the time limit") from exc
    for thread in threads:
        thread.join()
    if failures:
        raise FinalG0ReviewError(f"cannot read bounded Git output: {failures[0]}")
    if overflow:
        raise FinalG0ReviewError(
            "Git command output exceeds the configured bound: "
            + ", ".join(sorted(set(overflow)))
        )
    result = subprocess.CompletedProcess(command, returncode, bytes(stdout), bytes(stderr))
    if check and returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise FinalG0ReviewError(
            f"Git command failed with exit {returncode}: {message[:1024]}"
        )
    return result


def _repository_root(value: Path) -> Path:
    root = _ordinary_root(value)
    result = _run_git(root, ["rev-parse", "--path-format=absolute", "--show-toplevel"])
    try:
        reported = Path(result.stdout.decode("utf-8", errors="strict").strip()).resolve(
            strict=True
        )
    except (OSError, UnicodeError) as exc:
        raise FinalG0ReviewError(f"cannot decode Git worktree root: {exc}") from exc
    if reported != root:
        raise FinalG0ReviewError("repository root does not match Git worktree root")
    shallow = _run_git(root, ["rev-parse", "--is-shallow-repository"])
    if shallow.stdout != b"false\n":
        raise FinalG0ReviewError("exact-record validation requires non-shallow history")
    common = _run_git(
        root, ["rev-parse", "--path-format=absolute", "--git-common-dir"]
    )
    try:
        common_path = Path(common.stdout.decode("utf-8", errors="strict").strip()).resolve(
            strict=True
        )
    except (OSError, UnicodeError) as exc:
        raise FinalG0ReviewError(f"cannot resolve Git common directory: {exc}") from exc
    try:
        common_status = common_path.lstat()
    except OSError as exc:
        raise FinalG0ReviewError(f"cannot inspect Git common directory: {exc}") from exc
    if not stat.S_ISDIR(common_status.st_mode) or _is_reparse_point(common_path, common_status):
        raise FinalG0ReviewError("Git common directory must be an ordinary directory")
    info_path = common_path / "info"
    try:
        info_status = info_path.lstat()
    except FileNotFoundError:
        info_status = None
    except OSError as exc:
        raise FinalG0ReviewError(f"cannot inspect Git info directory: {exc}") from exc
    if info_status is not None and (
        not stat.S_ISDIR(info_status.st_mode) or _is_reparse_point(info_path, info_status)
    ):
        raise FinalG0ReviewError("Git info directory must be an ordinary directory")
    grafts = info_path / "grafts"
    try:
        grafts.lstat()
    except FileNotFoundError:
        pass
    except OSError as exc:
        raise FinalG0ReviewError(f"cannot inspect Git graft metadata: {exc}") from exc
    else:
        raise FinalG0ReviewError("legacy Git graft metadata is forbidden")
    return root


def _resolve_head(repository_root: Path) -> str:
    result = _run_git(repository_root, ["rev-parse", "--verify", "HEAD^{commit}"], maximum=256)
    try:
        commit = result.stdout.decode("ascii", errors="strict").strip()
    except UnicodeError as exc:
        raise FinalG0ReviewError("Git HEAD is not ASCII") from exc
    if FULL_OID.fullmatch(commit) is None:
        raise FinalG0ReviewError("Git HEAD is not a lowercase SHA-1 commit ID")
    return commit


def _require_commit(repository_root: Path, commit: object, label: str) -> str:
    if not isinstance(commit, str) or FULL_OID.fullmatch(commit) is None:
        raise FinalG0ReviewError(f"{label} must be a lowercase 40-character commit ID")
    result = _run_git(
        repository_root,
        ["rev-parse", "--verify", f"{commit}^{{commit}}"],
        check=False,
        maximum=256,
    )
    if result.returncode != 0 or result.stdout != (commit + "\n").encode("ascii"):
        raise FinalG0ReviewError(f"{label} is not an exact commit in this repository")
    return commit


def _tree_oid(repository_root: Path, commit: str) -> str:
    result = _run_git(
        repository_root, ["rev-parse", "--verify", f"{commit}^{{tree}}"], maximum=256
    )
    try:
        tree = result.stdout.decode("ascii", errors="strict").strip()
    except UnicodeError as exc:
        raise FinalG0ReviewError("selected tree ID is not ASCII") from exc
    if FULL_OID.fullmatch(tree) is None:
        raise FinalG0ReviewError("selected commit does not resolve to a SHA-1 tree")
    return tree


def _require_tree_binding(
    repository_root: Path, commit: str, recorded_tree: object, label: str
) -> str:
    if not isinstance(recorded_tree, str) or FULL_OID.fullmatch(recorded_tree) is None:
        raise FinalG0ReviewError(f"{label} must be a lowercase 40-character tree ID")
    actual = _tree_oid(repository_root, commit)
    if actual != recorded_tree:
        raise FinalG0ReviewError(f"{label} does not match the selected commit tree")
    return actual


def _git_blob_at_commit(
    repository_root: Path, commit: str, path: str, maximum: int, label: str
) -> bytes:
    path = _safe_git_path(path, label)
    listing = _run_git(
        repository_root,
        ["ls-tree", "-z", commit, "--", path],
        maximum=MAX_PATH_BYTES + 256,
    ).stdout
    if not listing.endswith(b"\0") or listing.count(b"\0") != 1:
        raise FinalG0ReviewError(f"{label} must be exactly one tracked Git entry")
    record = listing[:-1]
    try:
        metadata, raw_path = record.split(b"\t", 1)
        mode, object_type, oid = metadata.decode("ascii", errors="strict").split(" ")
        decoded_path = raw_path.decode("utf-8", errors="strict")
    except (UnicodeError, ValueError) as exc:
        raise FinalG0ReviewError(f"cannot parse tracked {label} entry") from exc
    if decoded_path != path or mode != "100644" or object_type != "blob" or FULL_OID.fullmatch(oid) is None:
        raise FinalG0ReviewError(f"{label} must be a non-executable regular Git blob")
    content = _run_git(repository_root, ["cat-file", "blob", oid], maximum=maximum).stdout
    if _git_blob_oid(content) != oid:
        raise FinalG0ReviewError(f"{label} Git blob failed object-ID verification")
    return content


def _indexed_worktree_blob(
    repository_root: Path, path: str, maximum: int, label: str
) -> bytes:
    content = _stable_worktree_file(repository_root, path, maximum, label)
    listing = _run_git(
        repository_root,
        ["ls-files", "--stage", "-z", "--", path],
        maximum=MAX_PATH_BYTES + 256,
    ).stdout
    if not listing.endswith(b"\0") or listing.count(b"\0") != 1:
        raise FinalG0ReviewError(f"{label} must be staged as exactly one tracked file")
    try:
        metadata, raw_path = listing[:-1].split(b"\t", 1)
        mode, oid, stage = metadata.decode("ascii", errors="strict").split(" ")
        decoded_path = raw_path.decode("utf-8", errors="strict")
    except (UnicodeError, ValueError) as exc:
        raise FinalG0ReviewError(f"cannot parse staged {label} entry") from exc
    if decoded_path != path or mode != "100644" or stage != "0" or FULL_OID.fullmatch(oid) is None:
        raise FinalG0ReviewError(f"{label} must be a stage-0 non-executable regular blob")
    indexed = _run_git(repository_root, ["cat-file", "blob", oid], maximum=maximum).stdout
    if _git_blob_oid(indexed) != oid or indexed != content:
        raise FinalG0ReviewError(f"{label} worktree bytes must exactly match the index")
    return content


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _reject_nonfinite(value: str) -> None:
    raise ValueError(f"non-finite JSON number is forbidden: {value}")


def _json_document(content: bytes, label: str, maximum: int) -> dict[str, Any]:
    if len(content) > maximum:
        raise FinalG0ReviewError(f"{label} exceeds {maximum} bytes")
    try:
        value = json.loads(
            content.decode("utf-8", errors="strict"),
            object_pairs_hook=_reject_duplicate_keys,
            parse_constant=_reject_nonfinite,
        )
    except (DuplicateJsonKeyError, RecursionError, UnicodeError, ValueError) as exc:
        raise FinalG0ReviewError(f"{label} is not unambiguous UTF-8 JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise FinalG0ReviewError(f"{label} must contain a JSON object")
    stack: list[tuple[object, int]] = [(value, 1)]
    nodes = 0
    while stack:
        current, depth = stack.pop()
        nodes += 1
        if nodes > MAX_JSON_NODES or depth > MAX_JSON_DEPTH:
            raise FinalG0ReviewError(f"{label} exceeds JSON structural bounds")
        if isinstance(current, dict):
            stack.extend((item, depth + 1) for item in current.values())
        elif isinstance(current, list):
            stack.extend((item, depth + 1) for item in current)
    return value


def _extract_records(markdown: bytes) -> dict[str, Any]:
    if len(markdown) > MAX_RECORD_BYTES:
        raise FinalG0ReviewError("release evidence exceeds the record size limit")
    if markdown.count(START_MARKER) != 1 or markdown.count(END_MARKER) != 1:
        raise FinalG0ReviewError("release evidence must contain exactly one final-G0 marker pair")
    start = markdown.index(START_MARKER) + len(START_MARKER)
    end = markdown.index(END_MARKER)
    if end <= start:
        raise FinalG0ReviewError("final-G0 record markers are out of order")
    match = JSON_FENCE.fullmatch(markdown[start:end])
    if match is None:
        raise FinalG0ReviewError("final-G0 markers must contain exactly one strict JSON fence")
    return _json_document(match.group("body"), "final-G0 records", MAX_RECORD_BYTES)


def _require_exact_keys(value: object, expected: set[str], label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise FinalG0ReviewError(f"{label} must be a JSON object")
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        raise FinalG0ReviewError(
            f"{label} fields differ; missing={missing}, unexpected={extra}"
        )
    return value


def _validate_root_schema(document: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    _require_exact_keys(document, ROOT_FIELDS, "final-G0 record root")
    if (
        type(document["schema_version"]) is not int
        or document["schema_version"] != SCHEMA_VERSION
    ):
        raise FinalG0ReviewError(f"final-G0 schema_version must be {SCHEMA_VERSION}")
    if document["record_kind"] != RECORD_KIND:
        raise FinalG0ReviewError(f"final-G0 record_kind must be {RECORD_KIND}")
    if document["record_semantics"] != EXPECTED_SEMANTICS:
        raise FinalG0ReviewError("final-G0 record_semantics must retain neutral INPUTS_ONLY/HUMAN_ONLY values")
    source = _require_exact_keys(
        document["final_g0_source_resource_review"], SOURCE_FIELDS, "source review"
    )
    readme = _require_exact_keys(
        document["final_g0_readme_visual_review"], README_FIELDS, "README review"
    )
    return source, readme


def _validate_outcome_fields(
    record: dict[str, Any], binding_fields: tuple[str, ...], label: str
) -> str:
    outcome = record.get("outcome")
    if outcome not in OUTCOMES:
        raise FinalG0ReviewError(f"{label} outcome is not a supported state")
    findings = record.get("findings")
    if not isinstance(findings, list) or len(findings) > MAX_FINDINGS:
        raise FinalG0ReviewError(f"{label} findings must be a bounded JSON array")
    if outcome == PENDING:
        if findings:
            raise FinalG0ReviewError(f"pending {label} findings must be empty")
        for field in binding_fields:
            if record.get(field) is not None:
                raise FinalG0ReviewError(f"pending {label} field {field} must be null")
        return outcome

    reviewer = record.get("reviewer")
    if not isinstance(reviewer, str) or not reviewer or reviewer.strip() != reviewer:
        raise FinalG0ReviewError(f"non-pending {label} reviewer must be nonempty and trimmed")
    try:
        reviewer_bytes = reviewer.encode("utf-8", errors="strict")
    except UnicodeError as exc:
        raise FinalG0ReviewError(f"non-pending {label} reviewer is not valid UTF-8") from exc
    if unicodedata.normalize("NFC", reviewer) != reviewer:
        raise FinalG0ReviewError(f"non-pending {label} reviewer must use NFC normalization")
    if len(reviewer_bytes) > MAX_REVIEWER_BYTES or any(
        ord(character) < 32 or ord(character) == 127 for character in reviewer
    ):
        raise FinalG0ReviewError(f"non-pending {label} reviewer is invalid")
    reviewed_at = record.get("reviewed_at")
    if not isinstance(reviewed_at, str) or re.fullmatch(r"\d{4}-\d{2}-\d{2}", reviewed_at) is None:
        raise FinalG0ReviewError(f"non-pending {label} reviewed_at must be YYYY-MM-DD")
    try:
        if dt.date.fromisoformat(reviewed_at).isoformat() != reviewed_at:
            raise ValueError
    except ValueError as exc:
        raise FinalG0ReviewError(f"non-pending {label} reviewed_at is not a real date") from exc

    normalized_findings: list[str] = []
    for finding in findings:
        if not isinstance(finding, str) or not finding or finding.strip() != finding:
            raise FinalG0ReviewError(f"non-pending {label} findings must be nonempty trimmed strings")
        try:
            encoded = finding.encode("utf-8", errors="strict")
        except UnicodeError as exc:
            raise FinalG0ReviewError(f"non-pending {label} finding is not valid UTF-8") from exc
        if unicodedata.normalize("NFC", finding) != finding:
            raise FinalG0ReviewError(f"non-pending {label} finding must use NFC normalization")
        if len(encoded) > MAX_FINDING_BYTES or any(
            (ord(character) < 32 and character not in "\t") or ord(character) == 127
            for character in finding
        ):
            raise FinalG0ReviewError(f"non-pending {label} finding is invalid")
        normalized_findings.append(unicodedata.normalize("NFC", finding).casefold())
    if len(set(normalized_findings)) != len(normalized_findings):
        raise FinalG0ReviewError(f"non-pending {label} findings must not repeat")
    if outcome == APPROVED and findings:
        raise FinalG0ReviewError(f"approved {label} findings must be empty")
    if outcome == CHANGES_REQUIRED and not findings:
        raise FinalG0ReviewError(f"changes-required {label} must contain at least one finding")
    return outcome


def _require_hash(value: object, label: str) -> str:
    if not isinstance(value, str) or SHA256.fullmatch(value) is None:
        raise FinalG0ReviewError(f"{label} must be a lowercase SHA-256 value")
    return value


def _load_sibling_module(filename: str, module_label: str) -> types.ModuleType:
    relative = f"scripts/{filename}"
    content = _stable_worktree_file(ROOT, relative, 2 * 1024 * 1024, f"trusted {module_label}")
    digest = _sha256(content)
    name = "_arce_" + module_label.replace("-", "_") + "_" + digest[:16]
    module = sys.modules.get(name)
    if isinstance(module, types.ModuleType):
        return module
    path = ROOT / relative
    module = types.ModuleType(name)
    module.__file__ = str(path)
    module.__package__ = ""
    sys.modules[name] = module
    try:
        code = compile(content, str(path), "exec", dont_inherit=True)
        exec(code, module.__dict__)
        if _stable_worktree_file(
            ROOT, relative, 2 * 1024 * 1024, f"trusted {module_label}"
        ) != content:
            raise FinalG0ReviewError(f"trusted {module_label} changed during import")
    except BaseException:
        sys.modules.pop(name, None)
        raise
    return module


def _reconstruct_source_report(repository_root: Path, selected_commit: str) -> bytes:
    try:
        tool = _load_sibling_module(
            "prepare_v002_final_g0_review_inputs.py", "v002_final_g0_inputs"
        )
        content = tool.build_report(repository_root, selected_commit)
    except Exception as exc:
        raise FinalG0ReviewError(f"cannot reconstruct final-G0 report: {exc}") from exc
    if not isinstance(content, bytes) or len(content) > MAX_REPORT_BYTES:
        raise FinalG0ReviewError("reconstructed final-G0 report is not bounded bytes")
    return content


def _require_approved_bootstrap_provenance(repository_root: Path, selected_commit: str) -> None:
    try:
        tool = _load_sibling_module(
            "validate_bootstrap_provenance.py", "v002_bootstrap_provenance"
        )
        errors, details = tool.validate_bootstrap_provenance_at_commit(
            repository_root, selected_commit
        )
    except Exception as exc:
        raise FinalG0ReviewError(f"cannot validate selected bootstrap provenance: {exc}") from exc
    if errors:
        raise FinalG0ReviewError(
            "selected bootstrap provenance is invalid: " + "; ".join(errors[:8])
        )
    if details.get("review_status") != APPROVED_PROVENANCE_STATUS:
        raise FinalG0ReviewError(
            "selected implementation bootstrap provenance is not THIRD_PARTY_APPROVED"
        )


def _require_ancestor(
    repository_root: Path, selected_commit: str, comparison_commit: str, *, exact_record: bool
) -> None:
    if exact_record and selected_commit == comparison_commit:
        raise FinalG0ReviewError("selected commit must precede the exact record commit")
    result = _run_git(
        repository_root,
        ["merge-base", "--is-ancestor", selected_commit, comparison_commit],
        check=False,
        maximum=1_024,
    )
    if result.returncode == 1:
        raise FinalG0ReviewError("selected commit is not an ancestor of the record comparison commit")
    if result.returncode != 0:
        raise FinalG0ReviewError("cannot establish selected-to-record commit ancestry")


def _validate_reconstructed_report_schema(
    report: dict[str, Any], selected_commit: str, selected_tree: str
) -> None:
    _require_exact_keys(report, REPORT_ROOT_FIELDS, "reconstructed final-G0 report")
    if (
        type(report["schema_version"]) is not int
        or report["schema_version"] != FINAL_INPUT_REPORT_SCHEMA_VERSION
    ):
        raise FinalG0ReviewError(
            "reconstructed final-G0 report schema_version must be 2"
        )
    if report["scope_version"] != "v0.0.2":
        raise FinalG0ReviewError(
            "reconstructed final-G0 report scope_version must be v0.0.2"
        )
    if (
        report["selected_commit"] != selected_commit
        or report["selected_tree_oid"] != selected_tree
    ):
        raise FinalG0ReviewError(
            "reconstructed final-G0 report selected commit/tree is inconsistent"
        )
    review_semantics = _require_exact_keys(
        report["review_semantics"],
        {"records_final_g0_human_decision", "result"},
        "reconstructed report review_semantics",
    )
    if (
        review_semantics["records_final_g0_human_decision"] is not False
        or review_semantics["result"] != "INPUTS_ONLY"
    ):
        raise FinalG0ReviewError(
            "reconstructed final-G0 report does not retain INPUTS_ONLY semantics"
        )
    prerequisites = _require_exact_keys(
        report["prerequisites"],
        {"bootstrap_provenance_review"},
        "reconstructed report prerequisites",
    )
    prerequisite = _require_exact_keys(
        prerequisites["bootstrap_provenance_review"],
        set(EXPECTED_APPROVED_PREREQUISITE),
        "reconstructed report bootstrap prerequisite",
    )
    if (
        prerequisite["ready_for_final_human_review"] is not True
        or prerequisite["record_status"] != "THIRD_PARTY_APPROVED"
        or prerequisite["state"] != "APPROVED_PREREQUISITE_OBSERVED"
    ):
        raise FinalG0ReviewError(
            "reconstructed final-G0 report does not record an approved/ready "
            "bootstrap provenance prerequisite"
        )
    bindings = report["bindings"]
    if not isinstance(bindings, dict) or set(bindings) != EXPECTED_REPORT_BINDINGS:
        raise FinalG0ReviewError(
            "reconstructed final-G0 report bindings are incomplete"
        )
    inventory = report["inventory"]
    if not isinstance(inventory, list) or len(inventory) > 20_000:
        raise FinalG0ReviewError(
            "reconstructed final-G0 report inventory is malformed or unbounded"
        )
    inventory_scope = _require_exact_keys(
        report["inventory_scope"],
        {
            "derivation",
            "repository_input_count",
            "scope_kind",
            "sources_manifest_path",
        },
        "reconstructed report inventory_scope",
    )
    if (
        inventory_scope["derivation"] != "EXACT_SOURCES_JAR_REPOSITORY_INPUTS"
        or type(inventory_scope["repository_input_count"]) is not int
        or inventory_scope["repository_input_count"] != len(inventory)
        or inventory_scope["scope_kind"]
        != "DISTRIBUTABLE_SOURCE_RESOURCE_LEGAL"
        or inventory_scope["sources_manifest_path"]
        != "docs/releases/v0.0.2/evidence/g0-mechanical/sources-jar-manifest.json"
    ):
        raise FinalG0ReviewError(
            "reconstructed final-G0 report inventory_scope is inconsistent"
        )
    jar_coverage = report["jar_manifest_coverage"]
    if (
        not isinstance(jar_coverage, dict)
        or jar_coverage.get("coverage_kind")
        != "STRICT_SELECTED_COMMIT_MANIFEST_SCHEMA_AND_COUNTS"
    ):
        raise FinalG0ReviewError(
            "reconstructed final-G0 report JAR coverage is not strict"
        )
    history = report["history"]
    if (
        not isinstance(history, dict)
        or history.get("scope_kind") != "BOUNDED_FULL_REPOSITORY_RANGE"
    ):
        raise FinalG0ReviewError(
            "reconstructed final-G0 report history is not full-repository bounded"
        )


def _source_invalidation_scope(report: dict[str, Any]) -> _InvalidationScope:
    inventory = report.get("inventory")
    if not isinstance(inventory, list) or len(inventory) > 20_000:
        raise FinalG0ReviewError("reconstructed report inventory is malformed or unbounded")
    for index, descriptor in enumerate(inventory):
        if not isinstance(descriptor, dict):
            raise FinalG0ReviewError("reconstructed report inventory entry is malformed")
        _safe_git_path(descriptor.get("path"), f"report inventory path {index}")
    bindings = report.get("bindings")
    if not isinstance(bindings, dict):
        raise FinalG0ReviewError("reconstructed report bindings are malformed")
    for label, descriptor in bindings.items():
        if not isinstance(label, str) or not isinstance(descriptor, dict):
            raise FinalG0ReviewError("reconstructed report bindings are malformed")
        _safe_git_path(descriptor.get("path"), f"report binding {label}")
    coverage = report.get("bootstrap_manifest_coverage")
    if not isinstance(coverage, dict) or not isinstance(coverage.get("targets"), list):
        raise FinalG0ReviewError("reconstructed report bootstrap coverage is malformed")
    if len(coverage["targets"]) > 1_024:
        raise FinalG0ReviewError("reconstructed report bootstrap coverage is unbounded")
    for index, target in enumerate(coverage["targets"]):
        if not isinstance(target, dict):
            raise FinalG0ReviewError("reconstructed report bootstrap target is malformed")
        _safe_git_path(target.get("path"), f"bootstrap target {index}")
    tool = report.get("tool")
    if not isinstance(tool, dict):
        raise FinalG0ReviewError("reconstructed report tool binding is malformed")
    _safe_git_path(tool.get("path"), "report tool path")

    # A non-pending source/resource decision is bound to the complete selected
    # repository state. Only the canonical review outputs may be added or
    # updated while recording that decision; every other path fails closed.
    return _InvalidationScope(
        prefixes=(),
        exact_paths=frozenset(),
        allowed_prefixes=SOURCE_REVIEW_ALLOWED_OUTPUT_PREFIXES,
        allowed_exact_paths=SOURCE_REVIEW_ALLOWED_OUTPUT_PATHS,
        invalidate_all_other_paths=True,
    )


def _history_commits(repository_root: Path, selected: str, comparison: str) -> list[str]:
    content = _run_git(
        repository_root,
        ["log", "-z", "--format=%H", "--topo-order", f"{selected}..{comparison}"],
        maximum=MAX_HISTORY_COMMITS * 41,
    ).stdout
    if content and not content.endswith(b"\0"):
        raise FinalG0ReviewError("Git history commit stream is truncated")
    commits: list[str] = []
    for item in content.split(b"\0"):
        if not item:
            continue
        try:
            commit = item.decode("ascii", errors="strict")
        except UnicodeError as exc:
            raise FinalG0ReviewError("Git history contains a non-ASCII commit ID") from exc
        if FULL_OID.fullmatch(commit) is None:
            raise FinalG0ReviewError("Git history contains an invalid commit ID")
        commits.append(commit)
    if len(commits) > MAX_HISTORY_COMMITS or len(set(commits)) != len(commits):
        raise FinalG0ReviewError("Git history commit range is invalid or unbounded")
    return commits


def _changed_paths_for_commit(repository_root: Path, commit: str) -> list[str]:
    content = _run_git(
        repository_root,
        ["diff-tree", "--root", "-m", "--no-commit-id", "--name-only", "-r", "-z", commit],
        maximum=MAX_GIT_OUTPUT_BYTES,
    ).stdout
    if content and not content.endswith(b"\0"):
        raise FinalG0ReviewError("Git changed-path stream is truncated")
    paths: list[str] = []
    for item in content.split(b"\0"):
        if not item:
            continue
        try:
            path = item.decode("utf-8", errors="strict")
        except UnicodeError as exc:
            raise FinalG0ReviewError("Git history path is not valid UTF-8") from exc
        paths.append(_safe_git_path(path, "Git history path"))
        if len(paths) > MAX_CHANGED_PATHS:
            raise FinalG0ReviewError("Git changed-path stream is unbounded")
    return paths


def _reject_invalidating_history(
    repository_root: Path,
    selected: str,
    comparison: str,
    scope: _InvalidationScope,
    label: str,
) -> None:
    invalidating: list[str] = []
    total_paths = 0
    for commit in _history_commits(repository_root, selected, comparison):
        changed_paths = _changed_paths_for_commit(repository_root, commit)
        total_paths += len(changed_paths)
        if total_paths > MAX_CHANGED_PATHS:
            raise FinalG0ReviewError(
                f"{label} comparison exceeds the aggregate changed-path bound"
            )
        for path in changed_paths:
            if scope.matches(path):
                invalidating.append(f"{commit}:{path}")
                if len(invalidating) >= 16:
                    break
        if len(invalidating) >= 16:
            break
    if invalidating:
        raise FinalG0ReviewError(
            f"{label} was invalidated after the selected commit: " + ", ".join(invalidating)
        )


def _worktree_changed_paths(repository_root: Path) -> list[str]:
    content = _run_git(
        repository_root,
        ["status", "--porcelain=v1", "-z", "--untracked-files=all"],
        maximum=MAX_GIT_OUTPUT_BYTES,
    ).stdout
    records = content.split(b"\0")
    if records and records[-1] != b"":
        raise FinalG0ReviewError("Git worktree status stream is truncated")
    paths: list[str] = []
    index = 0
    while index < len(records) - 1:
        record = records[index]
        index += 1
        if len(record) < 4 or record[2:3] != b" ":
            raise FinalG0ReviewError("Git worktree status record is malformed")
        status = record[:2]
        raw_paths = [record[3:]]
        if b"R" in status or b"C" in status:
            if index >= len(records) - 1:
                raise FinalG0ReviewError("Git rename status record is truncated")
            raw_paths.append(records[index])
            index += 1
        for raw_path in raw_paths:
            try:
                path = raw_path.decode("utf-8", errors="strict")
            except UnicodeError as exc:
                raise FinalG0ReviewError("Git worktree path is not valid UTF-8") from exc
            paths.append(_safe_git_path(path, "Git worktree path"))
            if len(paths) > MAX_CHANGED_PATHS:
                raise FinalG0ReviewError("Git worktree status is unbounded")
    return paths


def _reject_worktree_invalidation(
    repository_root: Path, scope: _InvalidationScope, label: str
) -> None:
    invalidating = [path for path in _worktree_changed_paths(repository_root) if scope.matches(path)]
    if invalidating:
        raise FinalG0ReviewError(
            f"{label} has uncommitted or staged invalidating paths: "
            + ", ".join(invalidating[:16])
        )


def _png_structure(content: bytes) -> dict[str, int]:
    if len(content) > MAX_PNG_BYTES or not content.startswith(PNG_SIGNATURE):
        raise FinalG0ReviewError("README screenshot is not a bounded PNG")
    offset = len(PNG_SIGNATURE)
    chunks: list[bytes] = []
    idat: list[bytes] = []
    width = height = channels = 0
    color_type = -1
    idat_ended = False
    while offset < len(content):
        if len(chunks) >= PNG_MAX_CHUNKS or len(content) - offset < 12:
            raise FinalG0ReviewError("README screenshot PNG chunk stream is invalid")
        if b"IEND" in chunks:
            raise FinalG0ReviewError("README screenshot PNG contains data after IEND")
        length = struct.unpack(">I", content[offset : offset + 4])[0]
        if length > PNG_MAX_CHUNK_BYTES:
            raise FinalG0ReviewError("README screenshot PNG chunk exceeds the size bound")
        chunk_type = content[offset + 4 : offset + 8]
        end = offset + 12 + length
        if end > len(content) or re.fullmatch(rb"[A-Za-z]{4}", chunk_type) is None:
            raise FinalG0ReviewError("README screenshot PNG chunk is malformed")
        if chunk_type[2] & 0x20:
            raise FinalG0ReviewError("README screenshot PNG chunk reserved bit is invalid")
        data = content[offset + 8 : offset + 8 + length]
        expected_crc = struct.unpack(">I", content[offset + 8 + length : end])[0]
        if expected_crc != (zlib.crc32(chunk_type + data) & 0xFFFFFFFF):
            raise FinalG0ReviewError("README screenshot PNG chunk CRC is invalid")
        if chunk_type[0] & 0x20 == 0 and chunk_type not in PNG_CRITICAL_CHUNKS:
            raise FinalG0ReviewError("README screenshot PNG has an unknown critical chunk")
        if chunk_type in PNG_PRIVACY_CHUNKS:
            raise FinalG0ReviewError("README screenshot PNG contains privacy-bearing metadata")
        if chunk_type[0] & 0x20:
            expected_length = PNG_ALLOWED_ANCILLARY.get(chunk_type)
            if expected_length is None or expected_length != length or chunk_type in chunks or b"IDAT" in chunks:
                raise FinalG0ReviewError("README screenshot PNG ancillary metadata is invalid")
            if chunk_type == b"pHYs":
                pixels_x, pixels_y, unit = struct.unpack(">IIB", data)
                if not (1 <= pixels_x < 2**31 and 1 <= pixels_y < 2**31 and unit in (0, 1)):
                    raise FinalG0ReviewError("README screenshot PNG pHYs metadata is invalid")
        chunks.append(chunk_type)
        if chunk_type == b"IDAT":
            if idat_ended:
                raise FinalG0ReviewError("README screenshot PNG IDAT chunks are not consecutive")
            idat.append(data)
        elif idat:
            idat_ended = True
        if len(chunks) == 1:
            if chunk_type != b"IHDR" or length != 13:
                raise FinalG0ReviewError("README screenshot PNG must start with IHDR")
            width, height, depth, color_type, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", data
            )
            if depth != 8 or color_type not in (2, 6):
                raise FinalG0ReviewError("README screenshot PNG must be 8-bit RGB or RGBA")
            if compression != 0 or filtering != 0 or interlace != 0:
                raise FinalG0ReviewError("README screenshot PNG encoding flags are unsupported")
            channels = 3 if color_type == 2 else 4
        elif chunk_type == b"PLTE" or (chunk_type == b"tRNS" and color_type != 2):
            raise FinalG0ReviewError("README screenshot PNG palette/transparency is invalid")
        if chunk_type == b"IEND" and length != 0:
            raise FinalG0ReviewError("README screenshot PNG IEND chunk must be empty")
        offset = end
    if (
        not chunks
        or chunks[-1] != b"IEND"
        or chunks.count(b"IHDR") != 1
        or chunks.count(b"IEND") != 1
        or not idat
    ):
        raise FinalG0ReviewError("README screenshot PNG is missing required chunks")
    if width < PNG_MIN_WIDTH or height < PNG_MIN_HEIGHT or width * height > PNG_MAX_PIXELS:
        raise FinalG0ReviewError("README screenshot PNG dimensions are outside full-window bounds")
    expected = height * (1 + width * channels)
    decompressor = zlib.decompressobj()
    decoded = decompressor.decompress(b"".join(idat), expected + 1)
    if (
        len(decoded) != expected
        or not decompressor.eof
        or decompressor.unconsumed_tail
        or decompressor.unused_data
    ):
        raise FinalG0ReviewError("README screenshot PNG pixels do not match IHDR dimensions")
    stride = 1 + width * channels
    if any(decoded[index] > 4 for index in range(0, len(decoded), stride)):
        raise FinalG0ReviewError("README screenshot PNG contains an invalid row filter")
    return {"width": width, "height": height}


def _read_bound_file(
    repository_root: Path,
    comparison_commit: str,
    path: str,
    maximum: int,
    label: str,
    exact_record: bool,
) -> bytes:
    if exact_record:
        return _git_blob_at_commit(repository_root, comparison_commit, path, maximum, label)
    return _indexed_worktree_blob(repository_root, path, maximum, label)


def _validate_source_review(
    repository_root: Path,
    record: dict[str, Any],
    comparison_commit: str,
    exact_record: bool,
) -> str:
    binding_fields = (
        "selected_implementation_commit",
        "selected_tree_oid",
        "review_inputs_report",
        "review_inputs_report_sha256",
        "reviewer",
        "reviewed_at",
    )
    outcome = _validate_outcome_fields(record, binding_fields, "source review")
    if outcome == PENDING:
        return outcome
    selected = _require_commit(
        repository_root,
        record["selected_implementation_commit"],
        "source selected_implementation_commit",
    )
    selected_tree = _require_tree_binding(
        repository_root, selected, record["selected_tree_oid"], "source selected_tree_oid"
    )
    expected_path = (
        f"docs/releases/v0.0.2/evidence/g0-final/{selected}/"
        "final-g0-review-inputs.json"
    )
    if record["review_inputs_report"] != expected_path:
        raise FinalG0ReviewError("source review_inputs_report is not the canonical commit-named path")
    expected_digest = _require_hash(
        record["review_inputs_report_sha256"], "source review_inputs_report_sha256"
    )
    _require_ancestor(repository_root, selected, comparison_commit, exact_record=exact_record)
    reconstructed = _reconstruct_source_report(repository_root, selected)
    report = _json_document(reconstructed, "reconstructed final-G0 report", MAX_REPORT_BYTES)
    _validate_reconstructed_report_schema(report, selected, selected_tree)
    archived = _read_bound_file(
        repository_root,
        comparison_commit,
        expected_path,
        MAX_REPORT_BYTES,
        "archived final-G0 report",
        exact_record,
    )
    if archived != reconstructed or _sha256(archived) != expected_digest:
        raise FinalG0ReviewError("archived final-G0 report bytes/hash do not match exact reconstruction")
    _require_approved_bootstrap_provenance(repository_root, selected)
    scope = _source_invalidation_scope(report)
    _reject_invalidating_history(
        repository_root, selected, comparison_commit, scope, "source review"
    )
    if not exact_record:
        _reject_worktree_invalidation(repository_root, scope, "source review")
    return outcome


def _validate_readme_review(
    repository_root: Path,
    record: dict[str, Any],
    comparison_commit: str,
    exact_record: bool,
) -> tuple[str, dict[str, int] | None]:
    binding_fields = (
        "selected_commit",
        "selected_tree_oid",
        "screenshot_file",
        "screenshot_sha256",
        "reviewer",
        "reviewed_at",
    )
    outcome = _validate_outcome_fields(record, binding_fields, "README review")
    if outcome == PENDING:
        return outcome, None
    selected = _require_commit(repository_root, record["selected_commit"], "README selected_commit")
    _require_tree_binding(
        repository_root, selected, record["selected_tree_oid"], "README selected_tree_oid"
    )
    expected_path = (
        f"docs/releases/v0.0.2/evidence/g0-final/{selected}/readme-full-window.png"
    )
    if record["screenshot_file"] != expected_path:
        raise FinalG0ReviewError("README screenshot_file is not the canonical commit-named path")
    expected_digest = _require_hash(record["screenshot_sha256"], "README screenshot_sha256")
    _require_ancestor(repository_root, selected, comparison_commit, exact_record=exact_record)
    _git_blob_at_commit(repository_root, selected, README_PATH, MAX_README_BYTES, "selected README")
    screenshot = _read_bound_file(
        repository_root,
        comparison_commit,
        expected_path,
        MAX_PNG_BYTES,
        "README screenshot",
        exact_record,
    )
    if _sha256(screenshot) != expected_digest:
        raise FinalG0ReviewError("README screenshot SHA-256 does not match the tracked PNG")
    dimensions = _png_structure(screenshot)
    scope = _InvalidationScope((), frozenset((README_PATH,)))
    _reject_invalidating_history(
        repository_root, selected, comparison_commit, scope, "README visual review"
    )
    if not exact_record:
        _reject_worktree_invalidation(repository_root, scope, "README visual review")
    return outcome, dimensions


def _validation_details(binding_mode: str, comparison_commit: str) -> dict[str, Any]:
    return {
        "schema_version": SCHEMA_VERSION,
        "validation_semantics": "INPUTS_ONLY",
        "binding_mode": binding_mode,
        "comparison_commit": comparison_commit,
        "source_review_outcome": "UNREAD",
        "readme_review_outcome": "UNREAD",
        "readme_png_structural_validation": "NOT_APPLICABLE",
        "readme_visible_pixel_judgment": "HUMAN_ONLY",
        "gate_decision": "NOT_COMPUTED",
    }


def _validate_content(
    repository_root: Path,
    content: bytes,
    comparison_commit: str,
    *,
    exact_record: bool,
) -> tuple[list[str], dict[str, Any]]:
    details = _validation_details(
        "EXACT_RECORD_COMMIT" if exact_record else "WORKTREE", comparison_commit
    )
    try:
        document = _extract_records(content)
        source, readme = _validate_root_schema(document)
        details["source_review_outcome"] = _validate_source_review(
            repository_root, source, comparison_commit, exact_record
        )
        readme_outcome, dimensions = _validate_readme_review(
            repository_root, readme, comparison_commit, exact_record
        )
        details["readme_review_outcome"] = readme_outcome
        if dimensions is not None:
            details["readme_png_structural_validation"] = "PASS"
            details["readme_png_width"] = dimensions["width"]
            details["readme_png_height"] = dimensions["height"]
    except (FinalG0ReviewError, OSError, subprocess.SubprocessError, zlib.error) as exc:
        return [str(exc)], details
    except Exception as exc:  # fail closed for malformed dependency/runtime state
        return [f"unexpected final-G0 validation failure: {exc}"], details
    return [], details


def validate_final_g0_review_records(
    repository_root: Path = ROOT,
    record_path: Path = DEFAULT_RECORD_PATH,
) -> tuple[list[str], dict[str, Any]]:
    """Validate the mutable worktree record and staged bound evidence files.

    Non-pending report/PNG evidence must be stage-0 tracked content identical to
    the worktree bytes. HEAD is the comparison tip; the API also rejects staged
    or unstaged invalidating scope changes.
    """

    try:
        root = _repository_root(repository_root)
        if record_path.is_absolute():
            raise FinalG0ReviewError("worktree record_path must be repository-relative")
        relative = _safe_git_path(record_path.as_posix(), "release evidence path")
        content = _stable_worktree_file(root, relative, MAX_RECORD_BYTES, "release evidence")
        head = _resolve_head(root)
    except (FinalG0ReviewError, OSError, subprocess.SubprocessError) as exc:
        return [str(exc)], _validation_details("WORKTREE", "UNRESOLVED")
    return _validate_content(root, content, head, exact_record=False)


def validate_final_g0_review_records_at_commit(
    repository_root: Path,
    record_commit: str,
    record_path: Path = DEFAULT_RECORD_PATH,
) -> tuple[list[str], dict[str, Any]]:
    """Validate records and bound evidence from one exact record commit.

    All record/report/PNG authority is read from Git blobs at ``record_commit``;
    mutable worktree evidence bytes are ignored.
    """

    try:
        root = _repository_root(repository_root)
        commit = _require_commit(root, record_commit, "record_commit")
        if record_path.is_absolute():
            raise FinalG0ReviewError("exact-record record_path must be repository-relative")
        relative = _safe_git_path(record_path.as_posix(), "release evidence path")
        content = _git_blob_at_commit(root, commit, relative, MAX_RECORD_BYTES, "release evidence")
    except (FinalG0ReviewError, OSError, subprocess.SubprocessError) as exc:
        return [str(exc)], _validation_details("EXACT_RECORD_COMMIT", record_commit)
    return _validate_content(root, content, commit, exact_record=True)


# Explicit aliases keep integration call sites descriptive while preserving the
# script-name-oriented public API above.
validate_v002_final_g0_review = validate_final_g0_review_records
validate_v002_final_g0_review_at_commit = validate_final_g0_review_records_at_commit


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository-root", type=Path, default=ROOT)
    parser.add_argument("--record-path", type=Path, default=DEFAULT_RECORD_PATH)
    parser.add_argument(
        "--record-commit",
        help="read the record and its evidence from this exact lowercase commit",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.record_commit:
        errors, details = validate_final_g0_review_records_at_commit(
            args.repository_root, args.record_commit, args.record_path
        )
    else:
        errors, details = validate_final_g0_review_records(
            args.repository_root, args.record_path
        )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(
        "[PASS] final-G0 review records are mechanically valid; "
        f"source={details['source_review_outcome']}, "
        f"README={details['readme_review_outcome']}"
    )
    print(
        "[INFO] INPUTS_ONLY: visible pixels and all Gate/legal/provenance "
        "conclusions remain human decisions."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
