#!/usr/bin/env python3
"""Generate or verify deterministic final-G0 source/resource review inputs.

The report is a mechanical review input only. Every authoritative byte is read
from one selected Git commit; mutable source/resource worktree files are never
used. The tool records no human decision and does not change any Gate status.
"""

from __future__ import annotations

import sys

if __name__ == "__main__" and (
    not sys.flags.isolated or not sys.flags.no_site
):
    print(
        "[FAIL] secure CLI execution requires Python isolated mode; rerun as "
        "python -I -S scripts/prepare_v002_final_g0_review_inputs.py ...",
        file=sys.stderr,
    )
    raise SystemExit(2)

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import threading
import unicodedata
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = "scripts/prepare_v002_final_g0_review_inputs.py"
BASE_COMMIT = "86b9db01b1cb4c8b8f673590baf1dc185d1716b3"
DEFAULT_COMMIT = "HEAD"
DEFAULT_OUTPUT = "build/v0.0.2-final-g0-review-inputs"
REPORT_NAME = "final-g0-review-inputs.json"

SCHEMA_VERSION = 1
SCOPE_VERSION = "v0.0.2"
FULL_OID = re.compile(r"[0-9a-f]{40}")
GIT_MODE = re.compile(r"[0-7]{6}")
DIFF_STATUS = re.compile(r"[ACDMTUXB]")

INVENTORY_PREFIXES = (
    "src/main/java",
    "src/main/resources",
    "src/generated/resources",
    "docs/licenses",
)
INVENTORY_EXACT_PATHS = (
    "LICENSE",
    "NOTICE.md",
    "THIRD-PARTY-NOTICES.md",
)
BINDING_PATHS = {
    "bootstrap_manifest": "docs/provenance/v0.0.2-bootstrap-inputs.json",
    "main_jar_content_manifest": (
        "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json"
    ),
    "sources_jar_manifest": (
        "docs/releases/v0.0.2/evidence/g0-mechanical/sources-jar-manifest.json"
    ),
}
REQUIRED_BOOTSTRAP_BUILD_TARGETS = (
    ".gitattributes",
    ".gitignore",
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradlew",
    "gradlew.bat",
)

MAX_GIT_OUTPUT_BYTES = 16 * 1024 * 1024
MAX_REPORT_BYTES = 8 * 1024 * 1024
MAX_FILE_BYTES = 32 * 1024 * 1024
MAX_TOTAL_BLOB_BYTES = 256 * 1024 * 1024
MAX_INVENTORY_ENTRIES = 20_000
MAX_BOOTSTRAP_TARGETS = 512
MAX_HISTORY_COMMITS = 4_096
MAX_PARENTS_PER_COMMIT = 64
MAX_HISTORY_PARENT_EDGES = 4_096
MAX_PATH_CHANGES = 100_000
MAX_PATH_BYTES = 512
MAX_PATH_DEPTH = 32
MAX_JSON_DEPTH = 64
MAX_JSON_NODES = 200_000
GIT_TIMEOUT_SECONDS = 60


class ReviewInputError(ValueError):
    """Raised when safe, commit-bound review inputs cannot be produced."""


class DuplicateJsonKeyError(ValueError):
    """Raised when an input JSON document contains duplicate keys."""


@dataclass(frozen=True)
class GitEntry:
    path: str
    mode: str
    object_type: str
    oid: str
    content: bytes

    def descriptor(self) -> dict[str, int | str]:
        return {
            "mode": self.mode,
            "object_type": self.object_type,
            "oid": self.oid,
            "path": self.path,
            "raw_blob_sha256": _sha256(self.content),
            "size": len(self.content),
        }


def _sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def _git_blob_oid(content: bytes) -> str:
    header = f"blob {len(content)}\0".encode("ascii")
    return hashlib.sha1(header + content).hexdigest()


def _canonical_json(value: object) -> bytes:
    try:
        content = (
            json.dumps(
                value,
                allow_nan=False,
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
            + "\n"
        ).encode("utf-8", errors="strict")
    except (RecursionError, TypeError, UnicodeError, ValueError) as exc:
        raise ReviewInputError(f"cannot encode canonical report JSON: {exc}") from exc
    if len(content) > MAX_REPORT_BYTES:
        raise ReviewInputError(
            f"canonical report exceeds {MAX_REPORT_BYTES} bytes"
        )
    return content


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _reject_nonfinite_json(value: str) -> None:
    raise ValueError(f"non-finite JSON value is forbidden: {value}")


def _validate_json_bounds(value: object, label: str) -> None:
    stack: list[tuple[object, int]] = [(value, 1)]
    nodes = 0
    while stack:
        current, depth = stack.pop()
        nodes += 1
        if nodes > MAX_JSON_NODES:
            raise ReviewInputError(f"{label} exceeds {MAX_JSON_NODES} JSON values")
        if depth > MAX_JSON_DEPTH:
            raise ReviewInputError(f"{label} exceeds JSON depth {MAX_JSON_DEPTH}")
        if isinstance(current, dict):
            stack.extend((item, depth + 1) for item in current.values())
        elif isinstance(current, list):
            stack.extend((item, depth + 1) for item in current)


def _load_json(content: bytes, label: str) -> dict[str, Any]:
    if len(content) > MAX_FILE_BYTES:
        raise ReviewInputError(f"{label} exceeds {MAX_FILE_BYTES} bytes")
    try:
        value = json.loads(
            content.decode("utf-8", errors="strict"),
            object_pairs_hook=_reject_duplicate_keys,
            parse_constant=_reject_nonfinite_json,
        )
    except (
        DuplicateJsonKeyError,
        RecursionError,
        UnicodeError,
        ValueError,
        json.JSONDecodeError,
    ) as exc:
        raise ReviewInputError(f"{label} is not unambiguous UTF-8 JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise ReviewInputError(f"{label} must contain a JSON object")
    _validate_json_bounds(value, label)
    return value


def _portable_path_key(value: str) -> str:
    return unicodedata.normalize("NFC", value).casefold()


def _safe_git_path(value: object, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise ReviewInputError(f"{label} must be a non-empty string")
    if "\\" in value or "\x00" in value:
        raise ReviewInputError(f"{label} must use normalized POSIX separators")
    try:
        encoded = value.encode("utf-8", errors="strict")
    except UnicodeEncodeError as exc:
        raise ReviewInputError(f"{label} is not valid UTF-8 text") from exc
    if len(encoded) > MAX_PATH_BYTES:
        raise ReviewInputError(f"{label} exceeds {MAX_PATH_BYTES} UTF-8 bytes")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise ReviewInputError(f"{label} contains a control character")
    path = PurePosixPath(value)
    if path.is_absolute() or path.as_posix() != value:
        raise ReviewInputError(f"{label} must be a normalized relative path")
    if any(part in ("", ".", "..") for part in path.parts):
        raise ReviewInputError(f"{label} contains an unsafe path component")
    if len(path.parts) > MAX_PATH_DEPTH:
        raise ReviewInputError(f"{label} exceeds {MAX_PATH_DEPTH} components")
    return value


def _command_environment() -> dict[str, str]:
    environment = dict(os.environ)
    for name in tuple(environment):
        if name.upper().startswith("GIT_TRACE"):
            environment.pop(name, None)
    for name in (
        "GIT_ALTERNATE_OBJECT_DIRECTORIES",
        "GIT_COMMON_DIR",
        "GIT_CONFIG",
        "GIT_CONFIG_GLOBAL",
        "GIT_CONFIG_PARAMETERS",
        "GIT_CONFIG_SYSTEM",
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
    environment.pop("GIT_CONFIG_COUNT", None)
    for name in tuple(environment):
        if name.startswith("GIT_CONFIG_KEY_") or name.startswith("GIT_CONFIG_VALUE_"):
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
            "PYTHONDONTWRITEBYTECODE": "1",
        }
    )
    return environment


def _git_executable() -> str:
    candidate = shutil.which("git")
    if not candidate:
        raise ReviewInputError("cannot locate Git on the trusted runtime PATH")
    try:
        path = Path(candidate).resolve(strict=True)
        status = path.lstat()
    except OSError as exc:
        raise ReviewInputError(f"cannot resolve Git executable: {exc}") from exc
    if not stat.S_ISREG(status.st_mode) or _is_reparse_point(path, status):
        raise ReviewInputError("Git executable must resolve to an ordinary regular file")
    return str(path)


def _run_git(
    repository_root: Path,
    arguments: list[str],
    *,
    input_content: bytes | None = None,
    check: bool = True,
    max_output: int = MAX_GIT_OUTPUT_BYTES,
) -> subprocess.CompletedProcess[bytes]:
    command = [
        _git_executable(),
        "--no-pager",
        "--no-replace-objects",
        "--literal-pathspecs",
        "-C",
        str(repository_root),
        *arguments,
    ]
    if max_output < 0:
        raise ReviewInputError("Git output bound must not be negative")
    if input_content is not None and len(input_content) > MAX_GIT_OUTPUT_BYTES:
        raise ReviewInputError("Git command input exceeds the configured bound")
    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE if input_content is not None else subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=_command_environment(),
        )
    except OSError as exc:
        raise ReviewInputError(f"Git command failed to run: {exc}") from exc

    stdout = bytearray()
    stderr = bytearray()
    overflows: list[str] = []
    reader_errors: list[BaseException] = []
    lock = threading.Lock()

    def read_bounded(stream: Any, destination: bytearray, label: str) -> None:
        overflowed = False
        try:
            while True:
                chunk = stream.read(64 * 1024)
                if not chunk:
                    break
                if overflowed:
                    continue
                remaining = max_output - len(destination)
                if len(chunk) > remaining:
                    destination.extend(chunk[: max(remaining, 0)])
                    with lock:
                        overflows.append(label)
                    overflowed = True
                    try:
                        process.kill()
                    except OSError:
                        pass
                else:
                    destination.extend(chunk)
        except BaseException as exc:  # pragma: no cover - OS pipe failure
            with lock:
                reader_errors.append(exc)
            try:
                process.kill()
            except OSError:
                pass
        finally:
            try:
                stream.close()
            except OSError:
                pass

    assert process.stdout is not None
    assert process.stderr is not None
    stdout_thread = threading.Thread(
        target=read_bounded,
        args=(process.stdout, stdout, "stdout"),
        daemon=True,
    )
    stderr_thread = threading.Thread(
        target=read_bounded,
        args=(process.stderr, stderr, "stderr"),
        daemon=True,
    )
    stdout_thread.start()
    stderr_thread.start()
    input_error: BaseException | None = None
    if input_content is not None:
        assert process.stdin is not None
        try:
            process.stdin.write(input_content)
            process.stdin.close()
        except (BrokenPipeError, OSError) as exc:
            input_error = exc
    try:
        returncode = process.wait(timeout=GIT_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired as exc:
        process.kill()
        process.wait()
        stdout_thread.join()
        stderr_thread.join()
        raise ReviewInputError("Git command exceeded the time limit") from exc
    stdout_thread.join()
    stderr_thread.join()
    if reader_errors:
        raise ReviewInputError(f"cannot read bounded Git output: {reader_errors[0]}")
    if overflows:
        raise ReviewInputError(
            "Git command output exceeds the configured bound: "
            + ", ".join(sorted(set(overflows)))
        )
    if input_error is not None and returncode == 0:
        raise ReviewInputError(f"cannot write bounded Git input: {input_error}")
    result = subprocess.CompletedProcess(
        command, returncode, bytes(stdout), bytes(stderr)
    )
    if check and result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        if len(message) > 1_024:
            message = message[:1_021] + "..."
        raise ReviewInputError(
            f"Git command failed with exit {result.returncode}: {message}"
        )
    return result


def _git_common_directory(repository_root: Path) -> Path:
    result = _run_git(
        repository_root,
        ["rev-parse", "--path-format=absolute", "--git-common-dir"],
    )
    try:
        value = result.stdout.decode("utf-8", errors="strict").strip()
    except UnicodeError as exc:
        raise ReviewInputError("Git common directory path is not UTF-8") from exc
    if not value:
        raise ReviewInputError("Git did not report a common directory")
    path = Path(value)
    if not path.is_absolute():
        path = repository_root / path
    try:
        resolved = path.resolve(strict=True)
    except OSError as exc:
        raise ReviewInputError(f"cannot resolve Git common directory: {exc}") from exc
    _ordinary_directory(resolved, "Git common directory")
    return resolved


def _assert_no_history_overrides(repository_root: Path) -> None:
    common_directory = _git_common_directory(repository_root)
    info_directory = common_directory / "info"
    try:
        info_status = info_directory.lstat()
    except FileNotFoundError:
        info_status = None
    except OSError as exc:
        raise ReviewInputError(f"cannot inspect Git info directory: {exc}") from exc
    if info_status is not None and (
        not stat.S_ISDIR(info_status.st_mode)
        or _is_reparse_point(info_directory, info_status)
    ):
        raise ReviewInputError("Git info directory must be an ordinary directory")
    for label, path in (
        ("legacy Git grafts", info_directory / "grafts"),
        ("shallow Git history", common_directory / "shallow"),
    ):
        try:
            path.lstat()
        except FileNotFoundError:
            continue
        except OSError as exc:
            raise ReviewInputError(f"cannot inspect {label}: {exc}") from exc
        raise ReviewInputError(f"{label} are forbidden for exact-object history")


def _is_reparse_point(path: Path, status: os.stat_result | None = None) -> bool:
    try:
        observed = status if status is not None else path.lstat()
    except OSError:
        return True
    attributes = getattr(observed, "st_file_attributes", 0)
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    return stat.S_ISLNK(observed.st_mode) or bool(attributes & reparse_flag)


def _ordinary_directory(path: Path, label: str) -> os.stat_result:
    try:
        status = path.lstat()
    except OSError as exc:
        raise ReviewInputError(f"cannot inspect {label}: {exc}") from exc
    if not stat.S_ISDIR(status.st_mode) or _is_reparse_point(path, status):
        raise ReviewInputError(f"{label} must be an ordinary directory")
    return status


def _ordinary_file(path: Path, label: str) -> os.stat_result:
    try:
        status = path.lstat()
    except OSError as exc:
        raise ReviewInputError(f"cannot inspect {label}: {exc}") from exc
    if (
        not stat.S_ISREG(status.st_mode)
        or _is_reparse_point(path, status)
        or status.st_nlink != 1
    ):
        raise ReviewInputError(f"{label} must be an unlinked ordinary file")
    return status


def _assert_ordinary_directory_chain(root: Path, descendant: Path, label: str) -> None:
    try:
        relative = descendant.relative_to(root)
    except ValueError as exc:
        raise ReviewInputError(f"{label} is outside its required root") from exc
    current = root
    _ordinary_directory(current, f"{label} root")
    for component in relative.parts:
        current = current / component
        _ordinary_directory(current, label)


def _file_identity(status: os.stat_result) -> tuple[int, int, int, int, int]:
    return (
        status.st_dev,
        status.st_ino,
        status.st_size,
        status.st_mtime_ns,
        status.st_ctime_ns,
    )


def _directory_identity(status: os.stat_result) -> tuple[int, int]:
    return (status.st_dev, status.st_ino)


def _read_stable_file(path: Path, label: str, maximum: int) -> bytes:
    before = _ordinary_file(path, label)
    if before.st_size > maximum:
        raise ReviewInputError(f"{label} exceeds {maximum} bytes")
    try:
        with path.open("rb") as stream:
            content = stream.read(maximum + 1)
    except OSError as exc:
        raise ReviewInputError(f"cannot read {label}: {exc}") from exc
    if len(content) > maximum:
        raise ReviewInputError(f"{label} exceeds {maximum} bytes")
    after = _ordinary_file(path, label)
    if _file_identity(before) != _file_identity(after) or len(content) != after.st_size:
        raise ReviewInputError(f"{label} changed while it was read")
    return content


def _repository_root(value: Path) -> Path:
    try:
        root = value.resolve(strict=True)
    except OSError as exc:
        raise ReviewInputError(f"cannot resolve repository root: {exc}") from exc
    _ordinary_directory(root, "repository root")
    result = _run_git(root, ["rev-parse", "--show-toplevel"])
    try:
        observed = Path(result.stdout.decode("utf-8", errors="strict").strip()).resolve(
            strict=True
        )
    except (OSError, UnicodeError) as exc:
        raise ReviewInputError(f"cannot resolve Git repository root: {exc}") from exc
    if observed != root:
        raise ReviewInputError("--repository-root must be the exact Git worktree root")
    return root


def resolve_commit(repository_root: Path, commit_spec: str) -> str:
    if (
        not isinstance(commit_spec, str)
        or not commit_spec
        or commit_spec.startswith("-")
        or any(character in commit_spec for character in ("\x00", "\r", "\n"))
        or len(commit_spec) > 256
    ):
        raise ReviewInputError("selected commit spec is unsafe")
    result = _run_git(
        repository_root,
        ["rev-parse", "--verify", "--end-of-options", f"{commit_spec}^{{commit}}"],
    )
    try:
        commit = result.stdout.decode("ascii", errors="strict").strip()
    except UnicodeError as exc:
        raise ReviewInputError("selected commit did not resolve to an ASCII object ID") from exc
    if FULL_OID.fullmatch(commit) is None:
        raise ReviewInputError("selected commit must resolve to one full 40-character commit ID")
    object_type = _run_git(repository_root, ["cat-file", "-t", commit]).stdout.strip()
    if object_type != b"commit":
        raise ReviewInputError("selected object is not a commit")
    return commit


def _tree_oid(repository_root: Path, commit: str) -> str:
    result = _run_git(
        repository_root,
        ["rev-parse", "--verify", "--end-of-options", f"{commit}^{{tree}}"],
    )
    try:
        oid = result.stdout.decode("ascii", errors="strict").strip()
    except UnicodeError as exc:
        raise ReviewInputError("tree object ID is not ASCII") from exc
    if FULL_OID.fullmatch(oid) is None:
        raise ReviewInputError("commit tree must have a full 40-character object ID")
    return oid


def _require_base_ancestor(repository_root: Path, selected_commit: str) -> None:
    _assert_no_history_overrides(repository_root)
    if FULL_OID.fullmatch(BASE_COMMIT) is None:
        raise ReviewInputError("configured v0.0.1 base commit is not a full object ID")
    base_type = _run_git(
        repository_root, ["cat-file", "-t", BASE_COMMIT], check=False
    )
    if base_type.returncode != 0 or base_type.stdout.strip() != b"commit":
        raise ReviewInputError(f"fixed base commit {BASE_COMMIT} is unavailable")
    ancestry = _run_git(
        repository_root,
        ["merge-base", "--is-ancestor", BASE_COMMIT, selected_commit],
        check=False,
    )
    if ancestry.returncode == 1:
        raise ReviewInputError("fixed v0.0.1 base is not an ancestor of selected commit")
    if ancestry.returncode != 0:
        raise ReviewInputError("cannot verify fixed-base ancestry")
    _assert_no_history_overrides(repository_root)


def _parse_ls_tree(content: bytes) -> dict[str, tuple[str, str, str]]:
    result: dict[str, tuple[str, str, str]] = {}
    portable_paths: dict[str, str] = {}
    records = content.split(b"\0")
    if records and records[-1] == b"":
        records.pop()
    if len(records) > MAX_INVENTORY_ENTRIES + MAX_BOOTSTRAP_TARGETS + 32:
        raise ReviewInputError("selected tree query returned too many entries")
    for raw_record in records:
        try:
            metadata, raw_path = raw_record.split(b"\t", 1)
            mode_bytes, type_bytes, oid_bytes = metadata.split(b" ", 2)
            mode = mode_bytes.decode("ascii", errors="strict")
            object_type = type_bytes.decode("ascii", errors="strict")
            oid = oid_bytes.decode("ascii", errors="strict")
            path = raw_path.decode("utf-8", errors="strict")
        except (UnicodeError, ValueError) as exc:
            raise ReviewInputError("selected tree contains a malformed entry") from exc
        _safe_git_path(path, "selected-tree path")
        if GIT_MODE.fullmatch(mode) is None or FULL_OID.fullmatch(oid) is None:
            raise ReviewInputError(f"selected-tree entry metadata is invalid: {path}")
        if object_type != "blob" or mode not in ("100644", "100755"):
            raise ReviewInputError(
                f"reviewed path must be an ordinary Git blob, not {mode} {object_type}: {path}"
            )
        if path in result:
            raise ReviewInputError(f"selected tree returned duplicate path: {path}")
        portable = _portable_path_key(path)
        previous = portable_paths.get(portable)
        if previous is not None and previous != path:
            raise ReviewInputError(
                f"selected tree contains a portable path collision: {previous}, {path}"
            )
        portable_paths[portable] = path
        result[path] = (mode, object_type, oid)
    return result


def _tree_metadata(
    repository_root: Path, selected_commit: str, pathspecs: list[str]
) -> dict[str, tuple[str, str, str]]:
    if not pathspecs:
        return {}
    safe_pathspecs = sorted({_safe_git_path(path, "tree pathspec") for path in pathspecs})
    result = _run_git(
        repository_root,
        [
            "ls-tree",
            "-r",
            "-z",
            "--full-tree",
            selected_commit,
            "--",
            *safe_pathspecs,
        ],
    )
    return _parse_ls_tree(result.stdout)


def _read_blob_contents(
    repository_root: Path, metadata: dict[str, tuple[str, str, str]]
) -> dict[str, bytes]:
    unique_oids = sorted({oid for _, _, oid in metadata.values()})
    if not unique_oids:
        return {}
    request = ("\n".join(unique_oids) + "\n").encode("ascii")
    check_result = _run_git(
        repository_root,
        ["cat-file", "--batch-check=%(objectname) %(objecttype) %(objectsize)"],
        input_content=request,
        max_output=max(MAX_GIT_OUTPUT_BYTES, len(unique_oids) * 128),
    )
    try:
        lines = check_result.stdout.decode("ascii", errors="strict").splitlines()
    except UnicodeError as exc:
        raise ReviewInputError("Git blob size query is not ASCII") from exc
    if len(lines) != len(unique_oids):
        raise ReviewInputError("Git blob size query returned an unexpected result count")
    sizes: dict[str, int] = {}
    total = 0
    for expected_oid, line in zip(unique_oids, lines, strict=True):
        fields = line.split(" ")
        if len(fields) != 3 or fields[0] != expected_oid or fields[1] != "blob":
            raise ReviewInputError("Git blob size query returned malformed metadata")
        try:
            size = int(fields[2])
        except ValueError as exc:
            raise ReviewInputError("Git blob size is not an integer") from exc
        if size < 0 or size > MAX_FILE_BYTES:
            raise ReviewInputError(f"Git blob {expected_oid} exceeds the per-file bound")
        total += size
        if total > MAX_TOTAL_BLOB_BYTES:
            raise ReviewInputError("selected Git blobs exceed the aggregate byte bound")
        sizes[expected_oid] = size

    batch_result = _run_git(
        repository_root,
        ["cat-file", "--batch"],
        input_content=request,
        max_output=total + len(unique_oids) * 128,
    )
    output = batch_result.stdout
    offset = 0
    contents: dict[str, bytes] = {}
    for expected_oid in unique_oids:
        newline = output.find(b"\n", offset)
        if newline < 0 or newline - offset > 128:
            raise ReviewInputError("Git blob batch output has a malformed header")
        try:
            header = output[offset:newline].decode("ascii", errors="strict").split(" ")
        except UnicodeError as exc:
            raise ReviewInputError("Git blob batch header is not ASCII") from exc
        expected_size = sizes[expected_oid]
        if header != [expected_oid, "blob", str(expected_size)]:
            raise ReviewInputError("Git blob batch header does not match its request")
        start = newline + 1
        end = start + expected_size
        if end >= len(output) or output[end : end + 1] != b"\n":
            raise ReviewInputError("Git blob batch output is truncated or ambiguous")
        content = output[start:end]
        if _git_blob_oid(content) != expected_oid:
            raise ReviewInputError(f"Git blob bytes do not match object ID {expected_oid}")
        contents[expected_oid] = content
        offset = end + 1
    if offset != len(output):
        raise ReviewInputError("Git blob batch output has trailing bytes")
    return contents


def _materialize_entries(
    metadata: dict[str, tuple[str, str, str]], contents: dict[str, bytes]
) -> dict[str, GitEntry]:
    result: dict[str, GitEntry] = {}
    for path, (mode, object_type, oid) in metadata.items():
        content = contents.get(oid)
        if content is None:
            raise ReviewInputError(f"missing verified Git blob bytes for {path}")
        result[path] = GitEntry(path, mode, object_type, oid, content)
    return result


def _bootstrap_targets(manifest_content: bytes) -> list[dict[str, str]]:
    document = _load_json(manifest_content, "bootstrap provenance manifest")
    raw_targets = document.get("targets")
    if not isinstance(raw_targets, list) or len(raw_targets) > MAX_BOOTSTRAP_TARGETS:
        raise ReviewInputError("bootstrap manifest targets must be a bounded array")
    targets: list[dict[str, str]] = []
    seen: set[str] = set()
    for index, raw_target in enumerate(raw_targets):
        if not isinstance(raw_target, dict):
            raise ReviewInputError(f"bootstrap target {index} must be an object")
        path = _safe_git_path(raw_target.get("path"), f"bootstrap target {index} path")
        component = raw_target.get("component")
        status_value = raw_target.get("status")
        if not isinstance(component, str) or not component:
            raise ReviewInputError(f"bootstrap target {path} has no component")
        if not isinstance(status_value, str) or not status_value:
            raise ReviewInputError(f"bootstrap target {path} has no recorded status")
        if path in seen:
            raise ReviewInputError(f"bootstrap manifest repeats target path {path}")
        seen.add(path)
        targets.append(
            {"component": component, "path": path, "recorded_status": status_value}
        )
    missing = sorted(set(REQUIRED_BOOTSTRAP_BUILD_TARGETS) - seen)
    if missing:
        raise ReviewInputError(
            "bootstrap manifest does not declare required build/Gradle targets: "
            + ", ".join(missing)
        )
    return sorted(targets, key=lambda target: target["path"])


def _runtime_script_binding(
    repository_root: Path, selected_commit: str
) -> tuple[GitEntry, bytes]:
    expected_path = repository_root / Path(SCRIPT_PATH)
    _assert_ordinary_directory_chain(
        repository_root, expected_path.parent, "running tool parent directory"
    )
    _ordinary_file(expected_path, "running review-input tool path")
    try:
        runtime_path = Path(__file__).resolve(strict=True)
        expected_resolved = expected_path.resolve(strict=True)
    except OSError as exc:
        raise ReviewInputError(f"cannot resolve running review-input tool: {exc}") from exc
    if expected_resolved != expected_path or runtime_path != expected_resolved:
        raise ReviewInputError(
            "running tool must be the repository copy at " + SCRIPT_PATH
        )
    runtime_content = _read_stable_file(
        runtime_path, "running review-input tool", MAX_FILE_BYTES
    )
    metadata = _tree_metadata(repository_root, selected_commit, [SCRIPT_PATH])
    if set(metadata) != {SCRIPT_PATH}:
        raise ReviewInputError("selected commit does not contain the review-input tool")
    contents = _read_blob_contents(repository_root, metadata)
    entry = _materialize_entries(metadata, contents)[SCRIPT_PATH]
    if runtime_content != entry.content:
        raise ReviewInputError(
            "running review-input tool bytes do not match the selected commit"
        )
    return entry, runtime_content


def _inventory_paths(entries: dict[str, GitEntry]) -> list[str]:
    result = []
    for path in entries:
        if path in INVENTORY_EXACT_PATHS or any(
            path.startswith(prefix + "/") for prefix in INVENTORY_PREFIXES
        ):
            result.append(path)
    missing_exact = sorted(set(INVENTORY_EXACT_PATHS) - set(result))
    if missing_exact:
        raise ReviewInputError(
            "selected commit is missing exact inventory paths: " + ", ".join(missing_exact)
        )
    for prefix in INVENTORY_PREFIXES:
        if not any(path.startswith(prefix + "/") for path in result):
            raise ReviewInputError(f"selected commit inventory prefix is empty: {prefix}")
    if len(result) > MAX_INVENTORY_ENTRIES:
        raise ReviewInputError("source/resource inventory exceeds the entry bound")
    return sorted(result)


def _history(
    repository_root: Path,
    selected_commit: str,
    history_pathspecs: list[str],
) -> tuple[list[str], list[dict[str, str]]]:
    _assert_no_history_overrides(repository_root)
    result = _run_git(
        repository_root,
        ["rev-list", "--parents", f"{BASE_COMMIT}..{selected_commit}"],
    )
    try:
        lines = result.stdout.decode("ascii", errors="strict").splitlines()
    except UnicodeError as exc:
        raise ReviewInputError("history commit list is not ASCII") from exc
    if len(lines) > MAX_HISTORY_COMMITS:
        raise ReviewInputError("base-to-selected history exceeds the commit bound")
    parents_by_commit: dict[str, list[str]] = {}
    parent_edges = 0
    for line in lines:
        fields = line.split()
        if not fields or any(FULL_OID.fullmatch(field) is None for field in fields):
            raise ReviewInputError("history contains a malformed commit or parent ID")
        if fields[0] in parents_by_commit:
            raise ReviewInputError("history returned a duplicate commit")
        if len(fields) - 1 > MAX_PARENTS_PER_COMMIT:
            raise ReviewInputError("history commit exceeds the parent-count bound")
        parent_edges += len(fields) - 1
        if parent_edges > MAX_HISTORY_PARENT_EDGES:
            raise ReviewInputError("history exceeds the aggregate parent-edge bound")
        parents_by_commit[fields[0]] = fields[1:]

    safe_pathspecs = sorted(
        {_safe_git_path(path, "history pathspec") for path in history_pathspecs}
    )
    changes: list[dict[str, str]] = []
    for commit in sorted(parents_by_commit):
        parents = parents_by_commit[commit]
        parent_values: list[str | None] = parents if parents else [None]
        for parent in parent_values:
            commit_arguments = [parent, commit] if parent is not None else ["--root", commit]
            diff = _run_git(
                repository_root,
                [
                    "diff-tree",
                    "--no-commit-id",
                    "--name-status",
                    "-r",
                    "-z",
                    "--no-renames",
                    *commit_arguments,
                    "--",
                    *safe_pathspecs,
                ],
            )
            fields = diff.stdout.split(b"\0")
            if fields and fields[-1] == b"":
                fields.pop()
            if len(fields) % 2 != 0:
                raise ReviewInputError("Git history path-change output is malformed")
            for offset in range(0, len(fields), 2):
                try:
                    status_value = fields[offset].decode("ascii", errors="strict")
                    path = fields[offset + 1].decode("utf-8", errors="strict")
                except UnicodeError as exc:
                    raise ReviewInputError("Git history path change is not valid text") from exc
                if DIFF_STATUS.fullmatch(status_value) is None:
                    raise ReviewInputError(
                        f"unsupported Git path-change status: {status_value}"
                    )
                _safe_git_path(path, "history changed path")
                changes.append(
                    {
                        "commit": commit,
                        "parent": parent if parent is not None else "ROOT",
                        "path": path,
                        "status": status_value,
                    }
                )
                if len(changes) > MAX_PATH_CHANGES:
                    raise ReviewInputError("history exceeds the path-change bound")
    changes.sort(
        key=lambda change: (
            change["commit"],
            change["parent"],
            change["path"],
            change["status"],
        )
    )
    _assert_no_history_overrides(repository_root)
    return sorted(parents_by_commit), changes


def build_report(repository_root: Path, selected_commit: str) -> bytes:
    """Reconstruct canonical report bytes from exact Git objects."""

    root = _repository_root(repository_root)
    if FULL_OID.fullmatch(selected_commit) is None:
        raise ReviewInputError("selected_commit must be a full lowercase commit ID")
    resolved = resolve_commit(root, selected_commit)
    if resolved != selected_commit:
        raise ReviewInputError("selected commit resolution changed unexpectedly")
    _require_base_ancestor(root, selected_commit)
    selected_tree = _tree_oid(root, selected_commit)
    base_tree = _tree_oid(root, BASE_COMMIT)
    tool_entry, _ = _runtime_script_binding(root, selected_commit)

    binding_metadata = _tree_metadata(root, selected_commit, list(BINDING_PATHS.values()))
    if set(binding_metadata) != set(BINDING_PATHS.values()):
        missing = sorted(set(BINDING_PATHS.values()) - set(binding_metadata))
        raise ReviewInputError("selected commit is missing bound evidence: " + ", ".join(missing))
    binding_contents = _read_blob_contents(root, binding_metadata)
    bindings = _materialize_entries(binding_metadata, binding_contents)
    bootstrap_targets = _bootstrap_targets(
        bindings[BINDING_PATHS["bootstrap_manifest"]].content
    )

    pathspecs = [
        *INVENTORY_PREFIXES,
        *INVENTORY_EXACT_PATHS,
        *(target["path"] for target in bootstrap_targets),
    ]
    selected_metadata = _tree_metadata(root, selected_commit, pathspecs)
    selected_contents = _read_blob_contents(root, selected_metadata)
    selected_entries = _materialize_entries(selected_metadata, selected_contents)
    inventory_paths = _inventory_paths(selected_entries)

    bootstrap_coverage = []
    for target in bootstrap_targets:
        path = target["path"]
        entry = selected_entries.get(path)
        if entry is None:
            raise ReviewInputError(
                f"bootstrap target is absent from selected commit: {path}"
            )
        bootstrap_coverage.append(
            {
                **target,
                "selected_git_entry": entry.descriptor(),
            }
        )

    history_pathspecs = sorted(
        {
            *INVENTORY_PREFIXES,
            *INVENTORY_EXACT_PATHS,
            *BINDING_PATHS.values(),
            SCRIPT_PATH,
            *(target["path"] for target in bootstrap_targets),
        }
    )
    range_commits, path_changes = _history(root, selected_commit, history_pathspecs)

    report = {
        "base_commit": BASE_COMMIT,
        "base_tree_oid": base_tree,
        "bindings": {
            name: bindings[path].descriptor()
            for name, path in sorted(BINDING_PATHS.items())
        },
        "bootstrap_manifest_coverage": {
            "coverage_kind": "BOUND_MANIFEST_DECLARED_IMPORTED_TARGETS",
            "required_build_gradle_target_paths": list(
                REQUIRED_BOOTSTRAP_BUILD_TARGETS
            ),
            "targets": bootstrap_coverage,
        },
        "history": {
            "path_changes": path_changes,
            "pathspecs": history_pathspecs,
            "range": f"{BASE_COMMIT}..{selected_commit}",
            "range_commit_oids": range_commits,
        },
        "inventory": [selected_entries[path].descriptor() for path in inventory_paths],
        "inventory_scope": {
            "exact_paths": list(INVENTORY_EXACT_PATHS),
            "recursive_prefixes": list(INVENTORY_PREFIXES),
            "scope_kind": "DISTRIBUTABLE_SOURCE_RESOURCE_LEGAL",
        },
        "review_semantics": {
            "records_final_g0_human_decision": False,
            "result": "INPUTS_ONLY",
        },
        "schema_version": SCHEMA_VERSION,
        "scope_version": SCOPE_VERSION,
        "selected_commit": selected_commit,
        "selected_tree_oid": selected_tree,
        "tool": tool_entry.descriptor(),
    }
    return _canonical_json(report)


def _safe_output_path(
    repository_root: Path, value: Path, *, must_exist: bool
) -> Path:
    build_root = repository_root / "build"
    build_status = _ordinary_directory(build_root, "repository build directory")
    build_resolved = build_root.resolve(strict=True)
    if build_resolved != build_root:
        raise ReviewInputError("repository build directory must not resolve elsewhere")

    lexical = value if value.is_absolute() else repository_root / value
    if ".." in value.parts:
        raise ReviewInputError("output path traversal is forbidden")
    try:
        relative = lexical.relative_to(build_root)
    except ValueError as exc:
        raise ReviewInputError("output directory must be below repository build/") from exc
    if not relative.parts:
        raise ReviewInputError("output directory must not be build/ itself")
    if any(part in ("", ".", "..") for part in relative.parts):
        raise ReviewInputError("output directory contains an unsafe component")

    parent = lexical.parent
    _assert_ordinary_directory_chain(
        build_root, parent, "output parent directory"
    )
    parent_status = _ordinary_directory(parent, "output parent directory")
    try:
        parent_resolved = parent.resolve(strict=True)
        parent_resolved.relative_to(build_resolved)
    except (OSError, ValueError) as exc:
        raise ReviewInputError("output parent resolves outside repository build/") from exc
    if _directory_identity(build_status) != _directory_identity(
        _ordinary_directory(build_root, "repository build directory")
    ):
        raise ReviewInputError("repository build directory changed during validation")
    if _directory_identity(parent_status) != _directory_identity(
        _ordinary_directory(parent, "output parent directory")
    ):
        raise ReviewInputError("output parent directory changed during validation")

    try:
        existing_status = lexical.lstat()
    except FileNotFoundError:
        existing_status = None
    except OSError as exc:
        raise ReviewInputError(f"cannot inspect output directory: {exc}") from exc
    if must_exist:
        if existing_status is None:
            raise ReviewInputError("output directory does not exist")
        _ordinary_directory(lexical, "output directory")
    elif existing_status is not None:
        raise ReviewInputError("output directory already exists; output is create-once")
    return lexical


def _write_report_create_once(output_directory: Path, content: bytes) -> None:
    try:
        output_directory.mkdir(mode=0o700)
    except OSError as exc:
        raise ReviewInputError(f"cannot create output directory: {exc}") from exc
    directory_status = _ordinary_directory(output_directory, "new output directory")
    report_path = output_directory / REPORT_NAME
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_BINARY"):
        flags |= os.O_BINARY
    created = False
    try:
        descriptor = os.open(report_path, flags, 0o600)
        created = True
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        observed = _read_stable_file(report_path, "generated report", MAX_REPORT_BYTES)
        if observed != content:
            raise ReviewInputError("generated report bytes changed during publication")
        if _directory_identity(directory_status) != _directory_identity(
            _ordinary_directory(output_directory, "new output directory")
        ):
            raise ReviewInputError("output directory changed during publication")
    except Exception:
        if created:
            try:
                report_path.unlink()
            except OSError:
                pass
        try:
            output_directory.rmdir()
        except OSError:
            pass
        raise


def _read_verified_output(output_directory: Path) -> bytes:
    before = _ordinary_directory(output_directory, "output directory")
    try:
        entries = list(os.scandir(output_directory))
    except OSError as exc:
        raise ReviewInputError(f"cannot enumerate output directory: {exc}") from exc
    if len(entries) != 1 or entries[0].name != REPORT_NAME:
        raise ReviewInputError(
            f"output directory must contain exactly {REPORT_NAME} and no extra files"
        )
    content = _read_stable_file(
        output_directory / REPORT_NAME, "stored final-G0 report", MAX_REPORT_BYTES
    )
    after = _ordinary_directory(output_directory, "output directory")
    if _directory_identity(before) != _directory_identity(after):
        raise ReviewInputError("output directory changed during verification")
    return content


def generate(
    repository_root: Path,
    commit_spec: str,
    output_directory: Path,
) -> tuple[str, str]:
    root = _repository_root(repository_root)
    selected_commit = resolve_commit(root, commit_spec)
    output = _safe_output_path(root, output_directory, must_exist=False)
    content = build_report(root, selected_commit)
    _write_report_create_once(output, content)
    return selected_commit, _sha256(content)


def verify(
    repository_root: Path,
    commit_spec: str,
    output_directory: Path,
) -> tuple[str, str]:
    root = _repository_root(repository_root)
    selected_commit = resolve_commit(root, commit_spec)
    output = _safe_output_path(root, output_directory, must_exist=True)
    observed = _read_verified_output(output)
    expected = build_report(root, selected_commit)
    final_observed = _read_verified_output(output)
    if observed != final_observed:
        raise ReviewInputError("stored report changed during Git reconstruction")
    if final_observed != expected:
        raise ReviewInputError(
            "stored report does not exactly match reconstruction from selected Git objects"
        )
    return selected_commit, _sha256(final_observed)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("generate", "verify"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument(
            "--repository-root",
            type=Path,
            default=ROOT,
            help="exact Git worktree root",
        )
        subparser.add_argument(
            "--commit",
            default=DEFAULT_COMMIT,
            help="commit or ref resolved once to a full commit ID",
        )
        subparser.add_argument(
            "--output",
            type=Path,
            default=Path(DEFAULT_OUTPUT),
            help="create-once report directory below repository build/",
        )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        if args.command == "generate":
            selected_commit, digest = generate(
                args.repository_root, args.commit, args.output
            )
            action = "generated"
        else:
            selected_commit, digest = verify(
                args.repository_root, args.commit, args.output
            )
            action = "verified"
    except ReviewInputError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    print(
        f"[PASS] {action} {REPORT_NAME} for selected commit {selected_commit}; "
        f"SHA-256 {digest}"
    )
    print("[INFO] This deterministic report is review input only; it records no human decision.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
