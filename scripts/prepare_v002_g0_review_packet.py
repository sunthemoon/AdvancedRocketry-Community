#!/usr/bin/env python3
"""Generate or verify a commit-bound v0.0.2 G0 human-review packet.

The packet is a review input, not an approval form. Authoritative bytes come
from one selected Git commit. The complete schema-3 provenance validator is
called through its explicit selected-commit API before a packet can be
generated or accepted; no checkout or mutable worktree input is used.
"""

from __future__ import annotations

import sys

if __name__ == "__main__" and (
    not sys.flags.isolated or not sys.flags.no_site
):
    print(
        "[FAIL] secure CLI execution requires Python isolated mode; rerun as "
        "python -I -S scripts/prepare_v002_g0_review_packet.py ...",
        file=sys.stderr,
    )
    raise SystemExit(2)

import argparse
import copy as _validator_copy_dependency
import datetime as _validator_datetime_dependency
import fnmatch as _validator_fnmatch_dependency
import hashlib
import json
import math
import os
import re
import shutil
import stat
import subprocess
import threading
import types
import unicodedata
import urllib as _validator_urllib_dependency
import urllib.parse as _validator_urlparse_dependency
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Callable


sys.dont_write_bytecode = True
_LOADED_GENERATOR_MODULE_CODE = sys._getframe().f_code

ROOT = Path(__file__).resolve().parents[1]
GIT_EXECUTABLE_CANDIDATE = shutil.which("git")
SCHEMA_VERSION = 3
SCOPE_VERSION = "v0.0.2"
MANIFEST_NAME = "packet-manifest.json"
REVIEW_INSTRUCTIONS_NAME = "REVIEW-INSTRUCTIONS.md"
PAYLOAD_ROOT = "files"
DEFAULT_COMMIT = "HEAD"
DEFAULT_PACKET_DIRECTORY = "build/v0.0.2-g0-review-packet"
POST_DECISION_PACKET_DIRECTORY = "build/v0.0.2-g0-review-packet-after-decision"

FULL_COMMIT = re.compile(r"[0-9a-f]{40}")
OBJECT_ID = re.compile(r"[0-9a-f]{40,64}")
SHA256 = re.compile(r"[0-9a-f]{64}")
YAML_FENCE = re.compile(
    r"```yaml[^\S\r\n]*\r?\n(?P<body>.*?)(?:\r?\n)```", re.DOTALL
)
REVIEW_DECISION_HEADING = re.compile(
    r"^## (?P<heading>Existing notices and [^\r\n]*human decisions)\s*$",
    re.MULTILINE,
)
NUMBERED_DECISION = re.compile(
    r"^(?P<number>[1-9][0-9]*)\.\s+(?P<text>.*?)"
    r"(?=^[1-9][0-9]*\.\s+|\Z)",
    re.MULTILINE | re.DOTALL,
)
ALLOWED_GIT_MODES = frozenset(("100644", "100755"))

MAX_FILE_BYTES = 32 * 1024 * 1024
MAX_PACKET_BYTES = 64 * 1024 * 1024
MAX_MANIFEST_BYTES = 4 * 1024 * 1024
MAX_PACKET_FILES = 512
MAX_PACKET_DIRECTORIES = 256
MAX_PACKET_PATH_BYTES = 512
MAX_PACKET_PATH_DEPTH = 32
MAX_PACKET_PATH_COMPONENT_BYTES = 255
MAX_PACKET_PATH_COMPONENT_UTF16_UNITS = 255
MAX_OBSERVED_PACKET_BYTES = MAX_PACKET_BYTES + MAX_MANIFEST_BYTES
MAX_SELECTED_TREE_FILES = 20_000
MAX_SELECTED_TREE_DIRECTORIES = 5_000
MAX_SELECTED_TREE_BYTES = 512 * 1024 * 1024
MAX_SELECTED_TREE_FILE_BYTES = 64 * 1024 * 1024
MAX_GIT_COMMIT_OBJECT_BYTES = 1024 * 1024
MAX_GIT_TREE_OBJECT_BYTES = 8 * 1024 * 1024
MAX_GIT_TREE_LOOKUP_BYTES = 64 * 1024 * 1024
MAX_JSON_DEPTH = 64
MAX_JSON_NODES = 100_000
MAX_VALIDATOR_ERROR_CHARS = 2_048
MAX_VALIDATOR_ERRORS_SHOWN = 25
MAX_MARKDOWN_YAML_FENCES = 32
MAX_MARKDOWN_FIELD_OCCURRENCES = 64
GIT_TIMEOUT_SECONDS = 30
GIT_STREAM_CHUNK_BYTES = 64 * 1024
GIT_TREE_RECORD_OVERHEAD_BYTES = 256
MAX_GIT_BATCH_HEADER_BYTES = 128

PROVENANCE_MANIFEST = "docs/provenance/v0.0.2-bootstrap-inputs.json"
PROVENANCE_RECORD = "docs/provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md"
THIRD_PARTY_NOTICE = "THIRD-PARTY-NOTICES.md"

GENERATOR_PATH = "scripts/prepare_v002_g0_review_packet.py"
VALIDATOR_PATH = "scripts/validate_bootstrap_provenance.py"
TOOL_DEFINITIONS = (
    ("packet_generator", GENERATOR_PATH),
    ("schema3_provenance_validator", VALIDATOR_PATH),
)

RUNTIME_DEPENDENCY_MODULES = (
    argparse,
    _validator_copy_dependency,
    _validator_datetime_dependency,
    _validator_fnmatch_dependency,
    hashlib,
    json,
    math,
    os,
    re,
    shutil,
    stat,
    subprocess,
    sys,
    threading,
    types,
    unicodedata,
    _validator_urllib_dependency,
    _validator_urlparse_dependency,
    sys.modules["dataclasses"],
    sys.modules["pathlib"],
    sys.modules["typing"],
)
RUNTIME_DEPENDENCY_ATTRIBUTE_BINDINGS = tuple(
    (module, tuple(vars(module).items()))
    for module in RUNTIME_DEPENDENCY_MODULES
    if module is not sys
)
RUNTIME_DEPENDENCY_CODE_BINDINGS = tuple(
    (value, value.__code__)
    for module in RUNTIME_DEPENDENCY_MODULES
    for value in vars(module).values()
    if isinstance(value, types.FunctionType)
)

STATIC_REVIEW_PATHS = (
    "LICENSE",
    "NOTICE.md",
    "PROJECT-CONFIG.md",
    "README.md",
    "UPSTREAM.md",
    THIRD_PARTY_NOTICE,
    PROVENANCE_RECORD,
    PROVENANCE_MANIFEST,
    "docs/06-RELEASE-AND-ACCEPTANCE-GATES.md",
    "docs/08-ASSET-LICENSE-AND-PROVENANCE.md",
    "docs/releases/v0.0.2/INSTALLATION.md",
    "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
    "docs/releases/v0.0.2/evidence/g0-mechanical/README.md",
    "docs/releases/v0.0.2/evidence/g0-mechanical/license-notice-scan.json",
    "docs/releases/v0.0.2/evidence/g0-mechanical/mods.toml",
    "docs/releases/v0.0.2/evidence/g0-mechanical/sources-jar-manifest.json",
    "docs/work/v0.0.2-test-machine-handoff.md",
    *(path for _, path in TOOL_DEFINITIONS),
)

MECHANICAL_JSON_PATHS = (
    "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
    "docs/releases/v0.0.2/evidence/g0-mechanical/license-notice-scan.json",
    "docs/releases/v0.0.2/evidence/g0-mechanical/sources-jar-manifest.json",
)

PENDING_RECORD_STATUS = "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING"
APPROVED_RECORD_STATUS = "THIRD_PARTY_APPROVED"
REVIEW_METADATA_FIELDS = (
    "record_status",
    "reviewer",
    "reviewed_at",
    "final_status_after_review",
    "reviewed_audited_target_commit",
    "reviewed_content_sha256",
)

QUESTION_SECTION_ID = "V002_G0_PROVENANCE_HUMAN_DECISIONS_V1"
QUESTION_DEFINITIONS = (
    {
        "id": "V002_G0_FORGE_MDK_TARGET_LICENSE_SCOPE",
        "source_decision_number": 1,
        "subject": "Application of recorded Forge license evidence to adapted MDK targets.",
    },
    {
        "id": "V002_G0_SOURCE_BINARY_NOTICE_TREATMENT",
        "source_decision_number": 2,
        "subject": (
            "Exact supplemental licenses, third-party notice, and packaging "
            "evidence for source and binary distributions."
        ),
    },
    {
        "id": "V002_G0_GIT_METADATA_TARGET_SCOPE",
        "source_decision_number": 3,
        "subject": "Recorded third-party scope of .gitattributes and .gitignore.",
    },
    {
        "id": "V002_G0_REVIEW_METADATA_AND_STATUS",
        "source_decision_number": 4,
        "subject": "Authoritative provenance status and required review metadata.",
    },
)


class PacketError(ValueError):
    """Raised when a safe authoritative packet cannot be produced."""


class DuplicateJsonKeyError(ValueError):
    """Raised when JSON contains an ambiguous duplicate key."""


@dataclass(frozen=True)
class GitBlob:
    mode: str
    object_type: str
    oid: str
    content: bytes


def _reject_nonfinite_json(value: str) -> None:
    raise ValueError(f"non-finite JSON number is forbidden: {value}")


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _load_json(content: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            content.decode("utf-8", errors="strict"),
            object_pairs_hook=_reject_duplicate_keys,
            parse_constant=_reject_nonfinite_json,
        )
    except (
        UnicodeError,
        json.JSONDecodeError,
        DuplicateJsonKeyError,
        RecursionError,
        ValueError,
    ) as exc:
        raise PacketError(f"{label} is not unambiguous UTF-8 JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise PacketError(f"{label} must contain a JSON object")
    _validate_json_bounds(value, label)
    return value


def _validate_json_bounds(value: object, label: str) -> None:
    stack: list[tuple[object, int]] = [(value, 1)]
    nodes = 0
    while stack:
        current, depth = stack.pop()
        nodes += 1
        if nodes > MAX_JSON_NODES:
            raise PacketError(f"{label} exceeds {MAX_JSON_NODES} JSON values")
        if depth > MAX_JSON_DEPTH:
            raise PacketError(f"{label} exceeds JSON depth {MAX_JSON_DEPTH}")
        if isinstance(current, float) and not math.isfinite(current):
            raise PacketError(f"{label} contains a non-finite JSON number")
        if isinstance(current, str):
            try:
                current.encode("utf-8", errors="strict")
            except UnicodeEncodeError as exc:
                raise PacketError(
                    f"{label} contains a string that is not valid UTF-8"
                ) from exc
        if isinstance(current, dict):
            for key in current:
                try:
                    key.encode("utf-8", errors="strict")
                except UnicodeEncodeError as exc:
                    raise PacketError(
                        f"{label} contains an object key that is not valid UTF-8"
                    ) from exc
            stack.extend((item, depth + 1) for item in current.values())
        elif isinstance(current, list):
            stack.extend((item, depth + 1) for item in current)


def _canonical_json(value: object) -> bytes:
    try:
        return (
            json.dumps(
                value,
                allow_nan=False,
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
            + "\n"
        ).encode("utf-8")
    except (RecursionError, TypeError, ValueError) as exc:
        raise PacketError(f"cannot canonically encode bounded JSON: {exc}") from exc


def _sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def _bounded_error_text(value: str) -> str:
    if len(value) <= MAX_VALIDATOR_ERROR_CHARS:
        return value
    return value[: MAX_VALIDATOR_ERROR_CHARS - 3] + "..."


def _relative_path_error(value: object) -> str | None:
    if not isinstance(value, str) or not value:
        return "path must be a non-empty string"
    if "\\" in value or "\x00" in value:
        return "path must use normalized POSIX separators"
    try:
        encoded_value = value.encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        return "path must be valid Unicode encodable as UTF-8"
    if len(encoded_value) > MAX_PACKET_PATH_BYTES:
        return f"path exceeds {MAX_PACKET_PATH_BYTES} UTF-8 bytes"
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        return "control characters are forbidden"
    if any(character in '<>:"|?*' for character in value):
        return "platform-unsafe path characters are forbidden"

    path = PurePosixPath(value)
    if path.is_absolute():
        return "absolute paths are forbidden"
    if any(part in ("", ".", "..") for part in path.parts):
        return "dot, empty, and parent components are forbidden"
    if len(path.parts) > MAX_PACKET_PATH_DEPTH:
        return f"path exceeds {MAX_PACKET_PATH_DEPTH} components"
    if path.as_posix() != value:
        return "path is not normalized"
    for part in path.parts:
        if len(part.encode("utf-8")) > MAX_PACKET_PATH_COMPONENT_BYTES:
            return (
                "path components must not exceed "
                f"{MAX_PACKET_PATH_COMPONENT_BYTES} UTF-8 bytes"
            )
        if len(part.encode("utf-16-le")) // 2 > MAX_PACKET_PATH_COMPONENT_UTF16_UNITS:
            return (
                "path components must not exceed "
                f"{MAX_PACKET_PATH_COMPONENT_UTF16_UNITS} UTF-16 code units"
            )
        if part.endswith((" ", ".")):
            return "path components must not end with a space or dot"
        if _portable_path_key(part) == ".git":
            return "Git control-directory path components are forbidden"
        stem = part.split(".", 1)[0].casefold()
        if stem in {"con", "prn", "aux", "nul", "conin$", "conout$"} or re.fullmatch(
            r"(?:com|lpt)(?:[1-9]|[¹²³])", stem
        ):
            return "platform-reserved path components are forbidden"
    return None


def _safe_relative_path(value: object, label: str) -> str:
    error = _relative_path_error(value)
    if error:
        raise PacketError(
            f"{label} is unsafe: {_bounded_error_text(repr(value))}: {error}"
        )
    assert isinstance(value, str)
    return value


def _portable_path_key(value: str) -> str:
    return unicodedata.normalize("NFC", value).casefold()


def _path_is_relative_to(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
    except ValueError:
        return False
    return True


def _run_command(
    arguments: list[str],
    *,
    timeout: int = GIT_TIMEOUT_SECONDS,
    input_content: bytes | None = None,
    check: bool = True,
) -> subprocess.CompletedProcess[bytes]:
    try:
        result = subprocess.run(
            arguments,
            check=False,
            input=input_content,
            capture_output=True,
            timeout=timeout,
            env=_command_environment(),
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PacketError(f"command failed: {arguments[0]}: {exc}") from exc
    if check and result.returncode != 0:
        message = result.stderr.decode("utf-8", errors="replace").strip()
        raise PacketError(
            f"command failed with exit {result.returncode}: "
            f"{' '.join(arguments)}: {message}"
        )
    return result


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
    config_count = environment.pop("GIT_CONFIG_COUNT", None)
    if config_count is not None:
        try:
            maximum_config_index = min(max(int(config_count), 0), 10_000)
        except ValueError:
            maximum_config_index = 10_000
        for index in range(maximum_config_index):
            environment.pop(f"GIT_CONFIG_KEY_{index}", None)
            environment.pop(f"GIT_CONFIG_VALUE_{index}", None)
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_SYSTEM": os.devnull,
            "GIT_OPTIONAL_LOCKS": "0",
            "GIT_NO_LAZY_FETCH": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_TERMINAL_PROMPT": "0",
            "LC_ALL": "C",
            "PYTHONDONTWRITEBYTECODE": "1",
        }
    )
    return environment


def _git_executable(repository_root: Path) -> str:
    if not GIT_EXECUTABLE_CANDIDATE:
        raise PacketError("cannot locate a Git executable on the trusted runtime PATH")
    try:
        executable = Path(GIT_EXECUTABLE_CANDIDATE).resolve(strict=True)
        status = executable.lstat()
    except OSError as exc:
        raise PacketError(f"cannot resolve the Git executable: {exc}") from exc
    if not stat.S_ISREG(status.st_mode) or _is_reparse_point(executable, status):
        raise PacketError("Git executable must resolve to an ordinary regular file")
    try:
        executable.relative_to(repository_root.resolve())
    except ValueError:
        pass
    else:
        raise PacketError("Git executable must not be contained in the repository")
    return str(executable)


def _run_git(
    repository_root: Path,
    *arguments: str,
    check: bool = True,
    input_content: bytes | None = None,
) -> subprocess.CompletedProcess[bytes]:
    return _run_command(
        [
            _git_executable(repository_root),
            "-c",
            "core.commitGraph=false",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-C",
            str(repository_root),
            *arguments,
        ],
        check=check,
        input_content=input_content,
    )


def _stream_git_nul_records(
    repository_root: Path,
    arguments: list[str],
    *,
    max_records: int,
    on_record: Callable[[bytes], None],
) -> None:
    command = [
        _git_executable(repository_root),
        "-c",
        "core.commitGraph=false",
        "-c",
        "core.fsmonitor=false",
        "-c",
        "core.untrackedCache=false",
        "-C",
        str(repository_root),
        *arguments,
    ]
    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=_command_environment(),
        )
    except OSError as exc:
        raise PacketError(f"cannot start bounded Git tree traversal: {exc}") from exc
    assert process.stdout is not None
    timed_out = threading.Event()

    def terminate_on_timeout() -> None:
        if process.poll() is None:
            timed_out.set()
            process.kill()

    timer = threading.Timer(GIT_TIMEOUT_SECONDS, terminate_on_timeout)
    timer.daemon = True
    timer.start()
    buffer = bytearray()
    total_bytes = 0
    record_count = 0
    maximum_record_bytes = MAX_PACKET_PATH_BYTES + GIT_TREE_RECORD_OVERHEAD_BYTES
    maximum_output_bytes = max_records * (maximum_record_bytes + 1)
    try:
        while True:
            chunk = process.stdout.read(GIT_STREAM_CHUNK_BYTES)
            if not chunk:
                break
            total_bytes += len(chunk)
            buffer.extend(chunk)
            while True:
                separator = buffer.find(b"\0")
                if separator < 0:
                    break
                record = bytes(buffer[:separator])
                del buffer[: separator + 1]
                if len(record) > maximum_record_bytes:
                    raise PacketError(
                        "bounded Git tree traversal emitted an oversized record"
                    )
                if not record:
                    continue
                record_count += 1
                if record_count > max_records:
                    raise PacketError(
                        "bounded Git tree traversal exceeded its record limit"
                    )
                on_record(record)
            if len(buffer) > maximum_record_bytes:
                raise PacketError(
                    "bounded Git tree traversal emitted an unterminated oversized record"
                )
            if total_bytes > maximum_output_bytes:
                raise PacketError(
                    "bounded Git tree traversal exceeded its output byte limit"
                )
        if buffer:
            raise PacketError("bounded Git tree traversal ended with a partial record")
        return_code = process.wait()
        if timed_out.is_set():
            raise PacketError("bounded Git tree traversal timed out")
        if return_code != 0:
            raise PacketError(
                f"bounded Git tree traversal failed with exit {return_code}"
            )
    finally:
        timer.cancel()
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()


def _git_command_has_output(repository_root: Path, arguments: list[str]) -> bool:
    try:
        process = subprocess.Popen(
            [
                _git_executable(repository_root),
                "-c",
                "core.commitGraph=false",
                "-c",
                "core.fsmonitor=false",
                "-c",
                "core.untrackedCache=false",
                "-C",
                str(repository_root),
                *arguments,
            ],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=_command_environment(),
        )
    except OSError as exc:
        raise PacketError(f"cannot start bounded Git cleanliness probe: {exc}") from exc
    assert process.stdout is not None
    timed_out = threading.Event()

    def terminate_on_timeout() -> None:
        if process.poll() is None:
            timed_out.set()
            process.kill()

    timer = threading.Timer(GIT_TIMEOUT_SECONDS, terminate_on_timeout)
    timer.daemon = True
    timer.start()
    found_output = False
    try:
        found_output = bool(process.stdout.read(1))
        if found_output and process.poll() is None:
            process.kill()
        return_code = process.wait()
        if timed_out.is_set():
            raise PacketError("bounded Git cleanliness probe timed out")
        if not found_output and return_code != 0:
            raise PacketError(
                f"bounded Git cleanliness probe failed with exit {return_code}"
            )
        return found_output
    finally:
        timer.cancel()
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()


def _index_contains_path_or_descendant(
    repository_root: Path, repository_path: str
) -> bool:
    """Return whether the index tracks the literal path or anything below it."""

    repository_path = _safe_relative_path(repository_path, "index query path")
    return _git_command_has_output(
        repository_root,
        [
            "ls-files",
            "--cached",
            "-z",
            "--",
            f":(top,literal){repository_path}",
        ],
    )


def _git_object_sha1(object_type: str, content: bytes) -> str:
    header = f"{object_type} {len(content)}\0".encode("ascii")
    return hashlib.sha1(header + content).hexdigest()


def _read_verified_git_object(
    repository_root: Path,
    oid: str,
    object_type: str,
    maximum_size: int,
    label: str,
) -> bytes:
    if FULL_COMMIT.fullmatch(oid) is None:
        raise PacketError(f"{label} has an invalid SHA-1 Git object ID")
    try:
        process = subprocess.Popen(
            [
                _git_executable(repository_root),
                "-c",
                "core.commitGraph=false",
                "-c",
                "core.fsmonitor=false",
                "-c",
                "core.untrackedCache=false",
                "-C",
                str(repository_root),
                "cat-file",
                "--batch",
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=_command_environment(),
        )
    except OSError as exc:
        raise PacketError(f"cannot start bounded Git object read for {label}: {exc}") from exc
    assert process.stdin is not None
    assert process.stdout is not None
    timed_out = threading.Event()

    def terminate_on_timeout() -> None:
        if process.poll() is None:
            timed_out.set()
            process.kill()

    timer = threading.Timer(GIT_TIMEOUT_SECONDS, terminate_on_timeout)
    timer.daemon = True
    timer.start()
    try:
        process.stdin.write(oid.encode("ascii") + b"\n")
        process.stdin.close()
        header = process.stdout.readline(MAX_GIT_BATCH_HEADER_BYTES + 1)
        if (
            not header
            or len(header) > MAX_GIT_BATCH_HEADER_BYTES
            or not header.endswith(b"\n")
        ):
            raise PacketError(f"bounded Git object header for {label} is malformed")
        fields = header[:-1].split()
        if len(fields) != 3:
            raise PacketError(f"{label} does not exist as a local Git object: {oid}")
        try:
            observed_oid = fields[0].decode("ascii", errors="strict")
            observed_type = fields[1].decode("ascii", errors="strict")
            size = int(fields[2].decode("ascii", errors="strict"))
        except (UnicodeError, ValueError) as exc:
            raise PacketError(f"cannot parse the Git object header for {label}") from exc
        if observed_oid != oid or observed_type != object_type:
            raise PacketError(
                f"{label} must be an exact Git {object_type}, got "
                f"{observed_oid} {observed_type}"
            )
        if size < 0 or size > maximum_size:
            raise PacketError(
                f"{label} exceeds the {maximum_size}-byte Git object limit"
            )
        chunks: list[bytes] = []
        remaining = size
        while remaining:
            chunk = process.stdout.read(min(remaining, GIT_STREAM_CHUNK_BYTES))
            if not chunk:
                raise PacketError(f"bounded Git object read for {label} ended early")
            chunks.append(chunk)
            remaining -= len(chunk)
        if process.stdout.read(1) != b"\n":
            raise PacketError(
                f"bounded Git object read for {label} emitted undeclared bytes"
            )
        if process.stdout.read(1):
            raise PacketError(
                f"bounded Git object read for {label} emitted trailing bytes"
            )
        return_code = process.wait()
        if timed_out.is_set():
            raise PacketError(f"bounded Git object read timed out for {label}")
        if return_code != 0:
            raise PacketError(
                f"bounded Git object read for {label} failed with exit {return_code}"
            )
        content = b"".join(chunks)
        recomputed_oid = _git_object_sha1(object_type, content)
        if recomputed_oid != oid:
            raise PacketError(
                f"Git object identity mismatch for {label}: expected {oid}, "
                f"recomputed {recomputed_oid}"
            )
        return content
    except (BrokenPipeError, OSError) as exc:
        raise PacketError(f"cannot read bounded Git object for {label}: {exc}") from exc
    finally:
        timer.cancel()
        try:
            if not process.stdin.closed:
                process.stdin.close()
        except OSError:
            pass
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()


def _read_verified_git_blobs_batch(
    repository_root: Path,
    oids: tuple[str, ...],
    *,
    maximum_file_size: int,
    maximum_aggregate_size: int,
) -> dict[str, int]:
    if any(FULL_COMMIT.fullmatch(oid) is None for oid in oids):
        raise PacketError("selected commit tree contains an invalid blob OID")
    try:
        process = subprocess.Popen(
            [
                _git_executable(repository_root),
                "-c",
                "core.commitGraph=false",
                "-c",
                "core.fsmonitor=false",
                "-c",
                "core.untrackedCache=false",
                "-C",
                str(repository_root),
                "cat-file",
                "--batch",
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=_command_environment(),
        )
    except OSError as exc:
        raise PacketError(f"cannot start bounded Git blob verification: {exc}") from exc
    assert process.stdin is not None
    assert process.stdout is not None
    timed_out = threading.Event()

    def terminate_on_timeout() -> None:
        if process.poll() is None:
            timed_out.set()
            process.kill()

    timer = threading.Timer(GIT_TIMEOUT_SECONDS, terminate_on_timeout)
    timer.daemon = True
    timer.start()
    sizes: dict[str, int] = {}
    aggregate_size = 0
    failure: str | None = None
    try:
        for oid in oids:
            try:
                process.stdin.write(oid.encode("ascii") + b"\n")
                process.stdin.flush()
            except (BrokenPipeError, OSError) as exc:
                failure = f"cannot query bounded Git blob verification: {exc}"
                break
            header = process.stdout.readline(MAX_GIT_BATCH_HEADER_BYTES + 1)
            if (
                not header
                or len(header) > MAX_GIT_BATCH_HEADER_BYTES
                or not header.endswith(b"\n")
            ):
                failure = "bounded Git blob verification emitted a malformed header"
                break
            fields = header[:-1].split()
            if len(fields) != 3:
                failure = "selected commit tree references a missing Git blob"
                break
            try:
                observed_oid = fields[0].decode("ascii", errors="strict")
                object_type = fields[1].decode("ascii", errors="strict")
                size = int(fields[2].decode("ascii", errors="strict"))
            except (UnicodeError, ValueError) as exc:
                failure = f"selected commit blob metadata is invalid: {exc}"
                break
            if observed_oid != oid or object_type != "blob" or size < 0:
                failure = "selected commit tree references an invalid Git blob"
                break
            if size > maximum_file_size:
                failure = (
                    f"selected commit blob exceeds {maximum_file_size} bytes: {oid}"
                )
                break
            aggregate_size += size
            if aggregate_size > maximum_aggregate_size:
                failure = (
                    "selected commit blobs exceed "
                    f"{maximum_aggregate_size} aggregate bytes"
                )
                break
            digest = hashlib.sha1(f"blob {size}\0".encode("ascii"))
            remaining = size
            while remaining:
                chunk = process.stdout.read(min(remaining, GIT_STREAM_CHUNK_BYTES))
                if not chunk:
                    failure = "bounded Git blob verification ended early"
                    break
                digest.update(chunk)
                remaining -= len(chunk)
            if failure is not None:
                break
            if process.stdout.read(1) != b"\n":
                failure = "bounded Git blob verification has no object terminator"
                break
            recomputed_oid = digest.hexdigest()
            if recomputed_oid != oid:
                failure = (
                    f"Git object identity mismatch for selected commit blob {oid}: "
                    f"recomputed {recomputed_oid}"
                )
                break
            sizes[oid] = size

        try:
            process.stdin.close()
        except OSError:
            pass
        if failure is not None and process.poll() is None:
            process.kill()
        return_code = process.wait()
        if timed_out.is_set():
            raise PacketError("bounded Git blob verification timed out")
        if failure is not None:
            raise PacketError(failure)
        if return_code != 0:
            raise PacketError(
                f"bounded Git blob verification failed with exit {return_code}"
            )
        return sizes
    finally:
        timer.cancel()
        try:
            if not process.stdin.closed:
                process.stdin.close()
        except OSError:
            pass
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()


def _parse_verified_git_tree(
    repository_root: Path,
    tree_oid: str,
    label: str,
    cache: dict[str, tuple[list[tuple[str, str, str, str]], int]] | None = None,
) -> tuple[list[tuple[str, str, str, str]], int]:
    if cache is not None and tree_oid in cache:
        return cache[tree_oid]
    content = _read_verified_git_object(
        repository_root,
        tree_oid,
        "tree",
        MAX_GIT_TREE_OBJECT_BYTES,
        label,
    )
    entries: list[tuple[str, str, str, str]] = []
    offset = 0
    while offset < len(content):
        space = content.find(b" ", offset)
        nul = content.find(b"\0", space + 1 if space >= 0 else offset)
        if space <= offset or nul <= space + 1 or nul + 21 > len(content):
            raise PacketError(f"{label} contains a malformed raw Git tree entry")
        mode_bytes = content[offset:space]
        name_bytes = content[space + 1 : nul]
        oid_bytes = content[nul + 1 : nul + 21]
        offset = nul + 21
        try:
            raw_mode = mode_bytes.decode("ascii", errors="strict")
            name = name_bytes.decode("utf-8", errors="strict")
        except UnicodeError as exc:
            raise PacketError(f"{label} contains an undecodable tree entry") from exc
        if not name or name in (".", "..") or "/" in name:
            raise PacketError(f"{label} contains an invalid Git tree entry name")
        oid = oid_bytes.hex()
        if raw_mode in ("40000", "040000"):
            mode, object_type = "040000", "tree"
        elif raw_mode in ALLOWED_GIT_MODES:
            mode, object_type = raw_mode, "blob"
        else:
            raise PacketError(
                f"{label} contains a symlink, submodule, or non-regular entry: "
                f"{name} ({raw_mode})"
            )
        entries.append((name, mode, object_type, oid))
    parsed = (entries, len(content))
    if cache is not None:
        cache[tree_oid] = parsed
    return parsed


def _verified_commit_tree_oid(repository_root: Path, commit: str) -> str:
    content = _read_verified_git_object(
        repository_root,
        commit,
        "commit",
        MAX_GIT_COMMIT_OBJECT_BYTES,
        f"selected commit {commit}",
    )
    first_line = content.split(b"\n", 1)[0]
    if not first_line.startswith(b"tree "):
        raise PacketError(f"selected commit {commit} has no initial tree header")
    try:
        tree_oid = first_line[5:].decode("ascii", errors="strict")
    except UnicodeError as exc:
        raise PacketError(f"selected commit {commit} has an invalid tree header") from exc
    if FULL_COMMIT.fullmatch(tree_oid) is None:
        raise PacketError(f"selected commit {commit} has an invalid tree header")
    return tree_oid


def _verified_tree_entry(
    repository_root: Path,
    commit: str,
    repository_path: str,
    *,
    root_tree_oid: str | None = None,
    tree_cache: dict[
        str, tuple[list[tuple[str, str, str, str]], int]
    ] | None = None,
) -> tuple[str, str, str]:
    repository_path = _safe_relative_path(repository_path, "repository path")
    tree_oid = root_tree_oid or _verified_commit_tree_oid(repository_root, commit)
    tree_bytes = 0
    parts = PurePosixPath(repository_path).parts
    for index, part in enumerate(parts):
        entries, observed_bytes = _parse_verified_git_tree(
            repository_root,
            tree_oid,
            f"tree for {repository_path}",
            tree_cache,
        )
        tree_bytes += observed_bytes
        if tree_bytes > MAX_GIT_TREE_LOOKUP_BYTES:
            raise PacketError(
                f"tree lookup for {repository_path} exceeds "
                f"{MAX_GIT_TREE_LOOKUP_BYTES} bytes"
            )
        matches = [entry for entry in entries if entry[0] == part]
        if len(matches) != 1:
            raise PacketError(
                f"commit {commit} must contain exactly one Git entry for "
                f"{repository_path}"
            )
        _, mode, object_type, oid = matches[0]
        if index + 1 == len(parts):
            return mode, object_type, oid
        if mode != "040000" or object_type != "tree":
            raise PacketError(
                f"commit {commit} does not contain a directory prefix for "
                f"{repository_path}"
            )
        tree_oid = oid
    raise PacketError(f"repository path is empty: {repository_path}")


def _repository_root(value: Path) -> Path:
    root = value.resolve()
    result = _run_git(root, "rev-parse", "--show-toplevel")
    try:
        observed = Path(
            result.stdout.decode("utf-8", errors="strict").strip()
        ).resolve()
    except (OSError, UnicodeError) as exc:
        raise PacketError(f"cannot resolve Git repository root: {exc}") from exc
    if os.path.normcase(str(observed)) != os.path.normcase(str(root)):
        raise PacketError(
            f"repository root must be the Git top level: expected {observed}, got {root}"
        )
    return root


def _clean_worktree(repository_root: Path) -> bool:
    tracked = _run_git(
        repository_root,
        "diff-index",
        "--quiet",
        "HEAD",
        "--",
        check=False,
    )
    if tracked.returncode == 1:
        return False
    if tracked.returncode != 0:
        message = tracked.stderr.decode("utf-8", errors="replace").strip()
        raise PacketError(
            f"cannot inspect tracked/staged worktree cleanliness: {message}"
        )
    return not _git_command_has_output(
        repository_root,
        ["ls-files", "--others", "--exclude-standard", "-z"],
    )


def resolve_commit(repository_root: Path, commit_spec: str = DEFAULT_COMMIT) -> str:
    """Resolve a full commit, or clean HEAD, exactly once."""

    repository_root = _repository_root(repository_root)
    if commit_spec == DEFAULT_COMMIT:
        if not _clean_worktree(repository_root):
            raise PacketError(
                "HEAD may be used only with a clean tracked/staged/untracked worktree"
            )
        expression = "HEAD"
    else:
        if FULL_COMMIT.fullmatch(commit_spec) is None:
            raise PacketError(
                "commit must be a lowercase full 40-character SHA or the literal HEAD"
            )
        expression = commit_spec

    result = _run_git(repository_root, "rev-parse", "--verify", expression)
    resolved = result.stdout.decode("ascii", errors="strict").strip()
    if FULL_COMMIT.fullmatch(resolved) is None:
        raise PacketError(f"Git returned an invalid commit object ID: {resolved!r}")
    if commit_spec != DEFAULT_COMMIT and resolved != commit_spec:
        raise PacketError("explicit commit did not resolve to the identical commit object")
    _verified_commit_tree_oid(repository_root, resolved)
    return resolved


def _git_tree_oid(repository_root: Path, commit: str) -> str:
    return _verified_commit_tree_oid(repository_root, commit)


def _git_blob(
    repository_root: Path,
    commit: str,
    repository_path: str,
    *,
    root_tree_oid: str | None = None,
    tree_cache: dict[
        str, tuple[list[tuple[str, str, str, str]], int]
    ] | None = None,
) -> GitBlob:
    repository_path = _safe_relative_path(repository_path, "repository path")
    mode, object_type, oid = _verified_tree_entry(
        repository_root,
        commit,
        repository_path,
        root_tree_oid=root_tree_oid,
        tree_cache=tree_cache,
    )
    if mode not in ALLOWED_GIT_MODES or object_type != "blob":
        raise PacketError(
            f"{repository_path} must be a regular Git blob, got {mode} {object_type}"
        )
    content = _read_verified_git_object(
        repository_root,
        oid,
        "blob",
        MAX_FILE_BYTES,
        repository_path,
    )
    return GitBlob(mode, object_type, oid, content)


def _validate_selected_tree_bounds(
    repository_root: Path,
    commit: str,
    *,
    root_tree_oid: str | None = None,
    tree_cache: dict[
        str, tuple[list[tuple[str, str, str, str]], int]
    ] | None = None,
) -> None:
    file_count = 0
    aggregate_size = 0
    directories: set[str] = set()
    portable_paths: dict[str, tuple[str, str]] = {}
    exact_paths: set[str] = set()
    root_tree = root_tree_oid or _verified_commit_tree_oid(repository_root, commit)
    pending: list[tuple[str, str]] = [(root_tree, "")]
    blobs: list[tuple[str, str]] = []
    while pending:
        tree_oid, prefix = pending.pop()
        entries, tree_size = _parse_verified_git_tree(
            repository_root,
            tree_oid,
            f"selected commit tree at {prefix or '/'}",
            tree_cache,
        )
        aggregate_size += tree_size
        for name, mode, object_type, oid in entries:
            path = f"{prefix}/{name}" if prefix else name
            _safe_relative_path(path, "selected commit tree path")
            if path in exact_paths:
                raise PacketError(
                    f"selected commit tree contains a duplicate exact path: {path}"
                )
            exact_paths.add(path)
            entry_kind = "directory" if object_type == "tree" else "file"
            path_parts = PurePosixPath(path).parts
            for index in range(1, len(path_parts) + 1):
                candidate_path = "/".join(path_parts[:index])
                candidate_kind = (
                    entry_kind if index == len(path_parts) else "directory"
                )
                portable_key = _portable_path_key(candidate_path)
                previous = portable_paths.get(portable_key)
                if previous is not None and previous != (
                    candidate_path,
                    candidate_kind,
                ):
                    raise PacketError(
                        "selected commit tree contains a case-insensitive or "
                        "Unicode-normalized path collision: "
                        f"{previous[0]} ({previous[1]}) and "
                        f"{candidate_path} ({candidate_kind})"
                    )
                portable_paths[portable_key] = (candidate_path, candidate_kind)

            if object_type == "tree":
                if mode != "040000":
                    raise PacketError(f"selected commit tree entry is invalid: {path}")
                directories.add(path)
                if len(directories) > MAX_SELECTED_TREE_DIRECTORIES:
                    raise PacketError(
                        "selected commit tree exceeds "
                        f"{MAX_SELECTED_TREE_DIRECTORIES} directories"
                    )
                pending.append((oid, path))
                continue

            if mode not in ALLOWED_GIT_MODES or object_type != "blob":
                raise PacketError(
                    "selected commit tree contains a symlink, submodule, or "
                    f"non-regular entry: {path} ({mode} {object_type})"
                )
            blobs.append((path, oid))
            file_count += 1
            if file_count > MAX_SELECTED_TREE_FILES:
                raise PacketError(
                    f"selected commit tree exceeds {MAX_SELECTED_TREE_FILES} files"
                )
        if aggregate_size > MAX_SELECTED_TREE_BYTES:
            raise PacketError(
                f"selected commit tree exceeds {MAX_SELECTED_TREE_BYTES} total bytes"
            )

    unique_oids = tuple(dict.fromkeys(oid for _, oid in blobs))
    sizes = _read_verified_git_blobs_batch(
        repository_root,
        unique_oids,
        maximum_file_size=MAX_SELECTED_TREE_FILE_BYTES,
        maximum_aggregate_size=MAX_SELECTED_TREE_BYTES - aggregate_size,
    )
    for path, oid in blobs:
        size = sizes[oid]
        if size > MAX_SELECTED_TREE_FILE_BYTES:
            raise PacketError(
                f"selected commit file exceeds {MAX_SELECTED_TREE_FILE_BYTES} bytes: {path}"
            )
        aggregate_size += size
        if aggregate_size > MAX_SELECTED_TREE_BYTES:
            raise PacketError(
                f"selected commit tree exceeds {MAX_SELECTED_TREE_BYTES} total bytes"
            )


def _is_reparse_point(path: Path, status: os.stat_result | None = None) -> bool:
    if status is None:
        try:
            status = path.lstat()
        except OSError:
            return False
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)
    if bool(getattr(status, "st_file_attributes", 0) & reparse_flag):
        return True
    is_junction = getattr(path, "is_junction", None)
    if callable(is_junction):
        try:
            return bool(is_junction())
        except OSError:
            return True
    return False


def _ordinary_path_error(path: Path, *, directory: bool) -> str | None:
    try:
        status = path.lstat()
    except OSError as exc:
        return str(exc)
    if stat.S_ISLNK(status.st_mode) or _is_reparse_point(path, status):
        return "symbolic links, junctions, and reparse points are forbidden"
    expected = stat.S_ISDIR if directory else stat.S_ISREG
    if not expected(status.st_mode):
        return "entry is not an ordinary " + ("directory" if directory else "file")
    return None


def _stable_file_identity(status: os.stat_result) -> tuple[int, int, int, int, int]:
    return (
        stat.S_IFMT(status.st_mode),
        status.st_dev,
        status.st_ino,
        status.st_size,
        status.st_mtime_ns,
    )


def _stable_directory_identity(
    status: os.stat_result,
) -> tuple[int, int, int, int, int, int]:
    return (
        stat.S_IFMT(status.st_mode),
        status.st_dev,
        status.st_ino,
        status.st_mtime_ns,
        status.st_ctime_ns,
        getattr(status, "st_file_attributes", 0),
    )


def _read_bounded_regular_file(
    path: Path,
    label: str,
    *,
    maximum_size: int,
    expected_size: int | None = None,
) -> bytes:
    try:
        initial_status = path.lstat()
        if stat.S_ISLNK(initial_status.st_mode) or _is_reparse_point(
            path, initial_status
        ):
            raise PacketError(
                f"{label} is not a safe ordinary file: symbolic links, junctions, "
                "and reparse points are forbidden"
            )
        if not stat.S_ISREG(initial_status.st_mode):
            raise PacketError(
                f"{label} is not a safe ordinary file: entry is not an "
                "ordinary file"
            )
        declared_size = initial_status.st_size
        if declared_size < 0 or declared_size > maximum_size:
            raise PacketError(f"{label} exceeds {maximum_size} bytes")
        if expected_size is not None and declared_size != expected_size:
            raise PacketError(
                f"{label} size is {declared_size}, expected {expected_size} bytes"
            )
        with path.open("rb") as stream:
            opened_status = os.fstat(stream.fileno())
            if not stat.S_ISREG(opened_status.st_mode):
                raise PacketError(f"{label} changed to a non-regular file while reading")
            if _stable_file_identity(opened_status) != _stable_file_identity(
                initial_status
            ):
                raise PacketError(f"{label} changed identity before reading")
            content = stream.read(maximum_size + 1)
            opened_after = os.fstat(stream.fileno())
            if _stable_file_identity(opened_after) != _stable_file_identity(
                opened_status
            ):
                raise PacketError(f"{label} changed while reading")
        final_status = path.lstat()
        if stat.S_ISLNK(final_status.st_mode) or _is_reparse_point(path, final_status):
            raise PacketError(f"{label} changed to a link or reparse point while reading")
        if _stable_file_identity(final_status) != _stable_file_identity(
            initial_status
        ):
            raise PacketError(f"{label} changed identity or metadata while reading")
    except PacketError:
        raise
    except OSError as exc:
        raise PacketError(f"cannot read {label}: {exc}") from exc
    if len(content) > maximum_size:
        raise PacketError(f"{label} exceeds {maximum_size} bytes")
    if len(content) != declared_size:
        raise PacketError(f"{label} changed size while reading")
    if expected_size is not None and len(content) != expected_size:
        raise PacketError(f"{label} does not contain the expected byte count")
    return content


def _assert_no_link_components(
    repository_root: Path, candidate: Path, label: str
) -> None:
    try:
        relative = candidate.relative_to(repository_root)
    except ValueError as exc:
        raise PacketError(f"{label} must remain under the repository root") from exc
    cursor = repository_root
    for part in relative.parts:
        cursor /= part
        if not cursor.exists() and not cursor.is_symlink():
            continue
        try:
            status = cursor.lstat()
        except OSError as exc:
            raise PacketError(f"cannot inspect {label} component {cursor}: {exc}") from exc
        if stat.S_ISLNK(status.st_mode) or _is_reparse_point(cursor, status):
            raise PacketError(
                f"{label} must not traverse a symlink, junction, or reparse point: {cursor}"
            )


def _path_within_repository(
    repository_root: Path, value: Path, label: str
) -> tuple[Path, str]:
    raw = value if value.is_absolute() else repository_root / value
    candidate = Path(os.path.abspath(raw))
    try:
        relative_path = candidate.relative_to(repository_root)
    except ValueError as exc:
        raise PacketError(f"{label} must remain under the repository root") from exc
    relative = _safe_relative_path(relative_path.as_posix(), label)
    if relative.casefold() == ".git" or relative.casefold().startswith(".git/"):
        raise PacketError(f"{label} must not be inside .git")
    _assert_no_link_components(repository_root, candidate, label)
    try:
        resolved = candidate.resolve(strict=False)
        resolved.relative_to(repository_root)
    except (OSError, ValueError) as exc:
        raise PacketError(f"{label} resolves outside the repository root") from exc
    return candidate, relative


def _safe_ignored_directory(
    repository_root: Path,
    value: Path,
    label: str,
    *,
    require_exists: bool,
) -> Path:
    repository_root = _repository_root(repository_root)
    candidate, relative = _path_within_repository(repository_root, value, label)
    ignored = _run_git(
        repository_root,
        "check-ignore",
        "--quiet",
        "--no-index",
        "--",
        relative,
        check=False,
    )
    if ignored.returncode != 0:
        raise PacketError(f"{label} must be a Git-ignored path (for example build/...)")
    if require_exists:
        error = _ordinary_path_error(candidate, directory=True)
        if error:
            raise PacketError(f"{label} must be an existing ordinary directory: {error}")
    else:
        if candidate.exists() or candidate.is_symlink():
            raise PacketError(f"{label} must not already exist")
        if _index_contains_path_or_descendant(repository_root, relative):
            raise PacketError(
                f"{label} must not overlap a tracked index path or descendant"
            )
        parent_error = _ordinary_path_error(candidate.parent, directory=True)
        if parent_error:
            raise PacketError(
                f"{label} parent must be an existing ordinary directory: {parent_error}"
            )
    return candidate


def _safe_offline_packet_directory(value: Path) -> Path:
    raw = value if value.is_absolute() else Path.cwd() / value
    try:
        # The caller explicitly selects this root. Resolve its host path once so
        # legitimate workspace symlinks/junctions above the packet do not make
        # the offline verifier unusable. Packet-internal entries remain subject
        # to strict no-link inventory and per-read component checks.
        candidate = raw.resolve(strict=True)
    except OSError as exc:
        raise PacketError(
            f"cannot resolve offline packet directory {raw}: {exc}"
        ) from exc
    error = _ordinary_path_error(candidate, directory=True)
    if error:
        raise PacketError(
            f"offline packet must be an existing ordinary directory: {error}"
        )
    return candidate


def _validate_runtime_tool_bindings(
    repository_root: Path,
    commit: str,
    *,
    root_tree_oid: str | None = None,
    tree_cache: dict[
        str, tuple[list[tuple[str, str, str, str]], int]
    ] | None = None,
) -> dict[str, GitBlob]:
    runtime_paths = {
        GENERATOR_PATH: Path(__file__).resolve(),
        VALIDATOR_PATH: ROOT / VALIDATOR_PATH,
    }
    bindings: dict[str, GitBlob] = {}
    for role, path in TOOL_DEFINITIONS:
        binding = _git_blob(
            repository_root,
            commit,
            path,
            root_tree_oid=root_tree_oid,
            tree_cache=tree_cache,
        )
        runtime_path = runtime_paths[path]
        error = _ordinary_path_error(runtime_path, directory=False)
        if error:
            raise PacketError(f"runtime {role} is unsafe: {runtime_path}: {error}")
        runtime_content = _read_bounded_regular_file(
            runtime_path,
            f"runtime {role}",
            maximum_size=MAX_FILE_BYTES,
            expected_size=len(binding.content),
        )
        if runtime_content != binding.content:
            raise PacketError(
                f"runtime {role} bytes do not match selected commit {commit} blob {path}; "
                "commit the exact tools or execute the selected tool version"
            )
        if path == GENERATOR_PATH:
            try:
                selected_code = compile(
                    binding.content,
                    _LOADED_GENERATOR_MODULE_CODE.co_filename,
                    "exec",
                    dont_inherit=True,
                    optimize=sys.flags.optimize,
                )
            except (OSError, SyntaxError, ValueError) as exc:
                raise PacketError(
                    f"cannot bind executing packet generator semantics: {exc}"
                ) from exc
            if _LOADED_GENERATOR_MODULE_CODE != selected_code:
                raise PacketError(
                    "executing packet generator bytecode does not match the exact "
                    f"selected source blob at {commit}:{GENERATOR_PATH}"
                )
        bindings[path] = binding
    return bindings


def _validate_runtime_dependency_origins(repository_root: Path) -> None:
    for module, attributes in RUNTIME_DEPENDENCY_ATTRIBUTE_BINDINGS:
        observed = vars(module)
        if any(observed.get(name) is not value for name, value in attributes):
            raise PacketError(
                "runtime dependency attributes changed after packet generator "
                f"import: {getattr(module, '__name__', None)!r}"
            )
    for function, code in RUNTIME_DEPENDENCY_CODE_BINDINGS:
        if function.__code__ is not code:
            raise PacketError(
                "runtime dependency callable changed after packet generator import: "
                f"{getattr(function, '__module__', None)!r}."
                f"{getattr(function, '__qualname__', None)!r}"
            )
    repository_root = repository_root.resolve()
    trusted_roots = {
        Path(sys.base_prefix).resolve(),
        Path(sys.exec_prefix).resolve(),
    }
    for module in RUNTIME_DEPENDENCY_MODULES:
        module_name = getattr(module, "__name__", None)
        if not isinstance(module_name, str) or sys.modules.get(module_name) is not module:
            raise PacketError(
                "runtime dependency identity changed after packet generator import: "
                f"{module_name!r}"
            )
        specification = getattr(module, "__spec__", None)
        origin = getattr(specification, "origin", None)
        if origin in ("built-in", "frozen"):
            continue
        if not isinstance(origin, str):
            raise PacketError(
                f"runtime dependency {module_name} has no trusted module origin"
            )
        try:
            origin_path = Path(origin).resolve(strict=True)
        except OSError as exc:
            raise PacketError(
                f"cannot resolve runtime dependency {module_name}: {exc}"
            ) from exc
        try:
            origin_path.relative_to(repository_root)
        except ValueError:
            pass
        else:
            raise PacketError(
                f"runtime dependency {module_name} resolves inside the mutable "
                f"repository: {origin_path}"
            )
        if not any(
            _path_is_relative_to(origin_path, trusted_root)
            for trusted_root in trusted_roots
        ):
            raise PacketError(
                f"runtime dependency {module_name} is outside trusted Python roots: "
                f"{origin_path}"
            )


def _run_selected_commit_validation(
    repository_root: Path,
    commit: str,
    tool_bindings: dict[str, GitBlob],
) -> dict[str, int | str]:
    """Compile the exact bound validator bytes and call its selected-commit API."""

    _validate_runtime_dependency_origins(repository_root)
    validator_binding = tool_bindings.get(VALIDATOR_PATH)
    if validator_binding is None:
        raise PacketError("bound schema-3 validator identity is missing")
    module_name = f"_v002_g0_bound_validator_{commit}"
    validator = types.ModuleType(module_name)
    validator.__file__ = str((ROOT / VALIDATOR_PATH).resolve())
    validator.__package__ = ""
    try:
        code = compile(
            validator_binding.content,
            f"{commit}:{VALIDATOR_PATH}",
            "exec",
            dont_inherit=True,
        )
        exec(code, validator.__dict__)
        api = validator.validate_bootstrap_provenance_at_commit
        default_manifest = validator.DEFAULT_MANIFEST
        validator_record_path = validator.EXPECTED_RECORD_PATH
        validator_notice_path = validator.EXPECTED_NOTICE_PATH
    except Exception as exc:
        raise PacketError(f"cannot load bound schema-3 validator: {exc}") from exc
    if not callable(api) or getattr(api, "__module__", None) != module_name:
        raise PacketError("bound schema-3 validator API identity is invalid")
    expected_manifest = Path(PROVENANCE_MANIFEST)
    if default_manifest != expected_manifest:
        raise PacketError(
            "bound schema-3 validator DEFAULT_MANIFEST does not match the packet's "
            f"authoritative manifest selector {PROVENANCE_MANIFEST}"
        )
    if validator_record_path != PROVENANCE_RECORD:
        raise PacketError(
            "bound schema-3 validator EXPECTED_RECORD_PATH does not match the "
            f"packet record selector {PROVENANCE_RECORD}"
        )
    if validator_notice_path != THIRD_PARTY_NOTICE:
        raise PacketError(
            "bound schema-3 validator EXPECTED_NOTICE_PATH does not match the "
            f"packet notice selector {THIRD_PARTY_NOTICE}"
        )
    try:
        errors, details = api(
            repository_root=repository_root,
            selected_commit=commit,
            manifest_path=expected_manifest,
        )
    except Exception as exc:
        raise PacketError(f"bound schema-3 validator execution failed: {exc}") from exc
    if not isinstance(errors, list) or any(not isinstance(item, str) for item in errors):
        raise PacketError("selected-commit validator returned an invalid errors list")
    if errors:
        shown = "; ".join(
            _bounded_error_text(error)
            for error in errors[:MAX_VALIDATOR_ERRORS_SHOWN]
        )
        suffix = (
            ""
            if len(errors) <= MAX_VALIDATOR_ERRORS_SHOWN
            else f"; ... {len(errors) - MAX_VALIDATOR_ERRORS_SHOWN} more"
        )
        raise PacketError(
            f"complete schema-3 provenance validation failed for {commit}: "
            f"{shown}{suffix}"
        )
    if not isinstance(details, dict):
        raise PacketError("selected-commit validator returned invalid details")

    required_counts = {"components": 2, "targets": 11, "local_assets": 2}
    for field, expected in required_counts.items():
        if details.get(field) != expected:
            raise PacketError(
                f"selected-commit validator {field} must be {expected}, got "
                f"{details.get(field)!r}"
            )
    status = details.get("review_status")
    if status not in (PENDING_RECORD_STATUS, APPROVED_RECORD_STATUS):
        raise PacketError(
            f"selected-commit validator returned invalid review_status: {status!r}"
        )
    digest = details.get("review_content_sha256")
    if not isinstance(digest, str) or SHA256.fullmatch(digest) is None:
        raise PacketError("selected-commit validator returned an invalid review digest")
    return {
        "components": required_counts["components"],
        "targets": required_counts["targets"],
        "local_assets": required_counts["local_assets"],
        "review_status": status,
        "review_content_sha256": digest,
    }


def _exact_object_list(
    value: object, label: str, key: str
) -> tuple[list[dict[str, Any]], dict[str, dict[str, Any]]]:
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise PacketError(f"{label} must be a list of JSON objects")
    items = list(value)
    indexed: dict[str, dict[str, Any]] = {}
    for item in items:
        path = _safe_relative_path(item.get(key), f"{label} {key}")
        if path in indexed:
            raise PacketError(f"{label} contains duplicate {key}: {path}")
        indexed[path] = item
    return items, indexed


def _required_paths_from_manifest(document: dict[str, Any]) -> list[str]:
    paths = set(STATIC_REVIEW_PATHS)
    _, targets = _exact_object_list(document.get("targets"), "targets", "path")
    _, assets = _exact_object_list(
        document.get("local_assets"), "local_assets", "path"
    )
    paths.update(targets)
    paths.update(assets)
    for path, asset in assets.items():
        if asset.get("status") == "GENERATED":
            paths.add(
                _safe_relative_path(
                    asset.get("generator_path"),
                    f"local asset {path} generator_path",
                )
            )

    components = document.get("components")
    if not isinstance(components, list) or not all(
        isinstance(item, dict) for item in components
    ):
        raise PacketError("components must be a list of JSON objects")
    for index, component in enumerate(components):
        paths.add(
            _safe_relative_path(
                component.get("license_copy_target"),
                f"components[{index}].license_copy_target",
            )
        )
    return sorted(paths)


def _markdown_scalar_occurrences(text: str, field: str) -> list[object]:
    values: list[object] = []
    fence_count = 0
    for fence in YAML_FENCE.finditer(text):
        fence_count += 1
        if fence_count > MAX_MARKDOWN_YAML_FENCES:
            raise PacketError(
                "review document exceeds "
                f"{MAX_MARKDOWN_YAML_FENCES} YAML metadata blocks"
            )
        for match in re.finditer(
            rf"^{re.escape(field)}:\s*(.*?)\s*$",
            fence.group("body"),
            re.MULTILINE,
        ):
            raw = match.group(1)
            if raw in ("null", "~"):
                values.append(None)
            elif len(raw) >= 2 and raw[0] == raw[-1] and raw[0] in ("'", '"'):
                values.append(raw[1:-1])
            else:
                values.append(raw)
            if len(values) > MAX_MARKDOWN_FIELD_OCCURRENCES:
                raise PacketError(
                    f"review document field {field} exceeds "
                    f"{MAX_MARKDOWN_FIELD_OCCURRENCES} occurrences"
                )
    return values


def _initial_record_fields(record_text: str) -> dict[str, object]:
    fence = YAML_FENCE.search(record_text)
    if fence is None:
        raise PacketError("provenance record has no YAML metadata block")
    body = fence.group("body")
    result: dict[str, object] = {}
    for field in REVIEW_METADATA_FIELDS:
        match = re.search(rf"^{re.escape(field)}:\s*(.*?)\s*$", body, re.MULTILINE)
        if match is None:
            raise PacketError(f"provenance record is missing observed field {field}")
        raw = match.group(1)
        if raw in ("null", "~"):
            result[field] = None
        elif len(raw) >= 2 and raw[0] == raw[-1] and raw[0] in ("'", '"'):
            result[field] = raw[1:-1]
        else:
            result[field] = raw
    return result


def _single_markdown_scalar(text: str, field: str, label: str) -> object:
    values = _markdown_scalar_occurrences(text, field)
    if len(values) != 1:
        raise PacketError(f"{label} must contain exactly one observed {field} value")
    return values[0]


def _source_decision_bindings(record_text: str) -> tuple[str, dict[int, str]]:
    headings = []
    for heading in REVIEW_DECISION_HEADING.finditer(record_text):
        headings.append(heading)
        if len(headings) > 1:
            break
    if len(headings) != 1:
        raise PacketError(
            "provenance record must contain exactly one human-decisions source heading"
        )
    heading_match = headings[0]
    section_start = heading_match.end()
    next_heading = re.search(r"^##\s+", record_text[section_start:], re.MULTILINE)
    section_end = (
        len(record_text)
        if next_heading is None
        else section_start + next_heading.start()
    )
    section = record_text[section_start:section_end]
    decisions: dict[int, str] = {}
    expected_numbers = {
        item["source_decision_number"] for item in QUESTION_DEFINITIONS
    }
    expected_number_text = {str(number) for number in expected_numbers}
    for match in NUMBERED_DECISION.finditer(section):
        if len(decisions) >= len(QUESTION_DEFINITIONS):
            raise PacketError(
                "provenance record human decisions must be exactly four numbered "
                "source items"
            )
        number_text = match.group("number")
        if number_text not in expected_number_text:
            raise PacketError(
                "provenance record human decisions must use source numbers 1 through 4"
            )
        number = int(number_text)
        if number in decisions:
            raise PacketError(f"provenance record repeats human decision {number}")
        decisions[number] = match.group("text").strip()
    if set(decisions) != expected_numbers or any(not text for text in decisions.values()):
        raise PacketError(
            "provenance record human decisions must be the four numbered source "
            "items 1 through 4"
        )
    return heading_match.group("heading"), decisions


def _tool_descriptor(
    role: str, path: str, commit: str, binding: GitBlob
) -> dict[str, object]:
    return {
        "role": role,
        "tool_commit": commit,
        "repository_path": path,
        "git_mode": binding.mode,
        "git_object_type": binding.object_type,
        "git_blob_oid": binding.oid,
        "raw_sha256": _sha256(binding.content),
        "size": len(binding.content),
    }


def _review_content_binding(
    source_document: dict[str, Any], validation: dict[str, int | str]
) -> dict[str, object]:
    review = source_document.get("review")
    if not isinstance(review, dict):
        raise PacketError("validated provenance review must be an object")
    status = validation["review_status"]
    digest = validation["review_content_sha256"]
    if status == PENDING_RECORD_STATUS:
        if review.get("reviewed_content_sha256") is not None:
            raise PacketError("pending review unexpectedly records an approval digest")
        return {
            "algorithm": "sha256",
            "value": digest,
            "classification": "PENDING_CONTENT_DIAGNOSTIC_ONLY",
            "recorded_in_authoritative_review": None,
            "statement": (
                "This value describes pending content only. It must never be copied "
                "into reviewed_content_sha256 or any approval metadata."
            ),
        }
    if status == APPROVED_RECORD_STATUS:
        recorded = review.get("reviewed_content_sha256")
        if recorded != digest or not isinstance(recorded, str):
            raise PacketError(
                "approved review digest was not already validly recorded in the "
                "selected commit"
            )
        return {
            "algorithm": "sha256",
            "value": recorded,
            "classification": "VALID_RECORDED_APPROVAL_BINDING_OBSERVED_ONLY",
            "recorded_in_authoritative_review": recorded,
            "statement": (
                "This binding was already recorded and fully validated in the "
                "selected commit. The packet only observes it and does not create, "
                "renew, or alter approval."
            ),
        }
    raise PacketError(f"unsupported validated review status: {status}")


def _reviewer_instructions(
    commit: str,
    tree_oid: str,
    review_status: str,
) -> bytes:
    authoritative_command = (
        f"python -I -S {GENERATOR_PATH} --repository-root . verify "
        f"--commit {commit} --packet {DEFAULT_PACKET_DIRECTORY}"
    )
    content_only_command = (
        f"python -I -S {GENERATOR_PATH} verify-content-only "
        f"--packet {DEFAULT_PACKET_DIRECTORY}"
    )
    decision_lines = "\n".join(
        (
            f"{definition['source_decision_number']}. "
            f"`{definition['id']}` - {definition['subject']}"
        )
        for definition in QUESTION_DEFINITIONS
    )
    if review_status == PENDING_RECORD_STATUS:
        decision_intro = (
            "Review the exact wording of these four existing decision items in the\n"
            "authoritative provenance record:"
        )
        state_workflow = f"""## Pending-review workflow

This selected commit is pending. Review each decision independently; the packet
does not supply an answer. Do not edit packet files and do not copy the pending
diagnostic digest into approval metadata.

If the independent decision is applied as an approval, complete the documented
authoritative metadata and digest procedure, then use this order:

1. Before committing, follow the bundled installation/handoff procedure to
   clean-build both JARs and refresh the artifact manifest, all G0 mechanical
   packaging evidence, and release checksums. Run their validators so the
   packet cannot carry packaging evidence from the pending notice bytes.
2. Commit the exact decision application and refreshed evidence together, then
   leave the checkout clean.
3. Run the complete provenance validator against that committed state:

   ```text
   python -I -S {VALIDATOR_PATH} --require-approved-review
   ```

4. Generate and authoritatively verify a new packet for the new commit:

   ```text
   python -I -S {GENERATOR_PATH} generate --commit HEAD --output {POST_DECISION_PACKET_DIRECTORY}
   python -I -S {GENERATOR_PATH} verify --commit HEAD --packet {POST_DECISION_PACKET_DIRECTORY}
   ```

5. Review the new commit-bound packet before relying on the approved-state
   observation. The pending packet cannot authenticate the later source edits.

If any substantive decision is negative or requires changes, or if it changes a
target, recorded source, notice obligation, tooling, manifest, test, or intended
subreview scope, keep the authoritative state pending and record the change
request in the documented correction log. Rebuild and refresh every affected
packaging/evidence file before committing the revised pending material, then
generate and verify a new packet and re-review the affected scope. Do not carry
this packet's observations forward as approval for different content.
"""
    elif review_status == APPROVED_RECORD_STATUS:
        decision_intro = (
            "For observation only, inspect the exact wording of these four recorded\n"
            "decision items in the authoritative provenance record:"
        )
        state_workflow = """## Approved-state observation

This selected commit already contains a mechanically valid, digest-bound
approved-state record. This packet only exposes that existing state for
observation. It does not ask the reviewer to rewrite decisions or metadata and
does not create, renew, or broaden approval.

Any later change to a target, recorded source, notice obligation, review-bound
content, tooling, manifest, test, or intended scope requires a new committed
state, complete validation, a newly generated and verified packet, and human
review of the affected scope.
"""
    else:
        raise PacketError(
            f"unsupported review status for instructions: {review_status}"
        )
    content = f"""# v0.0.2 Forge/Gradle provenance and license subreview

This packet supports only the human provenance and license subreview for the
recorded Forge MDK and Gradle Wrapper inputs and their target files. It does not
establish the originality of the full repository, approve unrelated content, or
complete Gate G0. Packet generation, mechanical validation, and either
verification command below do not answer a review question or create approval.

## Exact review scope

- Selected commit: `{commit}`
- Selected root tree: `{tree_oid}`
- Authoritative target list: `files/{PROVENANCE_MANIFEST}` (`targets`)
- Authoritative decision text: `files/{PROVENANCE_RECORD}`; the manifest binds
  the decision section and each source-question digest without reproducing
  free-form source headings here
- Supplemental operating context: `files/docs/releases/v0.0.2/INSTALLATION.md`
  and `files/docs/work/v0.0.2-test-machine-handoff.md`

{decision_intro}

{decision_lines}

The identifiers and subjects above locate the items; they do not prescribe an
answer.

{state_workflow}

## Authoritative exact-Git verification

From a checkout containing the selected commit and the exact committed tool,
place the packet at `{DEFAULT_PACKET_DIRECTORY}`, then run:

```text
{authoritative_command}
```

`verify` is authoritative for packet construction: it resolves and validates the
selected Git commit and tree, checks the executing tools against their selected
Git blobs, rebuilds the expected packet from exact Git objects, and compares the
manifest and every payload byte.

## Weaker offline content-only check

Never execute `files/{GENERATOR_PATH}` or any other code from an unauthenticated
packet. `-I -S` isolates Python imports but does not sandbox a script. Obtain the
verifier from a separately authenticated trusted checkout or tool distribution,
place the packet at `{DEFAULT_PACKET_DIRECTORY}`, and run from that trusted
checkout:

```text
{content_only_command}
```

`verify-content-only` checks only a bounded packet inventory and the sizes
and SHA-256 values declared by the bundled manifest. Because that manifest is
itself only packet content, this weaker check does not authenticate the claimed
Git commit or tree, prove repository origin, validate provenance semantics, or
create/confirm human approval.

Both verification modes assume a private, quiescent packet copy that other
processes cannot rewrite during the check. File and component identities are
checked before and after reads, but this cross-platform verifier is not a
sandbox and does not defend against an actor that can concurrently replace
directories. Copy the artifact to a private ordinary directory and stop sync or
extraction tools before verification.
"""
    return content.encode("utf-8")


def _build_packet(
    repository_root: Path,
    commit: str,
    validation: dict[str, int | str],
    tool_bindings: dict[str, GitBlob],
    *,
    root_tree_oid: str | None = None,
    tree_cache: dict[
        str, tuple[list[tuple[str, str, str, str]], int]
    ] | None = None,
) -> tuple[dict[str, Any], dict[str, bytes]]:
    manifest_binding = _git_blob(
        repository_root,
        commit,
        PROVENANCE_MANIFEST,
        root_tree_oid=root_tree_oid,
        tree_cache=tree_cache,
    )
    source_document = _load_json(manifest_binding.content, PROVENANCE_MANIFEST)
    if source_document.get("schema_version") != 3:
        raise PacketError("fully validated provenance schema_version must be 3")
    if source_document.get("scope_version") != SCOPE_VERSION:
        raise PacketError(f"fully validated provenance scope_version must be {SCOPE_VERSION}")
    if source_document.get("record_path") != PROVENANCE_RECORD:
        raise PacketError(f"fully validated record_path must be {PROVENANCE_RECORD}")
    if source_document.get("review", {}).get("record_status") != validation.get(
        "review_status"
    ):
        raise PacketError("full-validator review status differs from selected manifest")

    required_paths = _required_paths_from_manifest(source_document)
    if len(required_paths) + 1 > MAX_PACKET_FILES:
        raise PacketError(f"packet requires more than {MAX_PACKET_FILES} payload files")
    bindings: dict[str, GitBlob] = {PROVENANCE_MANIFEST: manifest_binding}
    total_size = len(manifest_binding.content)
    for path in required_paths:
        if path in bindings:
            continue
        binding = tool_bindings.get(path) or _git_blob(
            repository_root,
            commit,
            path,
            root_tree_oid=root_tree_oid,
            tree_cache=tree_cache,
        )
        total_size += len(binding.content)
        if total_size > MAX_PACKET_BYTES:
            raise PacketError(f"packet payload exceeds {MAX_PACKET_BYTES} total bytes")
        bindings[path] = binding

    for path in MECHANICAL_JSON_PATHS:
        _load_json(bindings[path].content, path)

    try:
        record_text = bindings[PROVENANCE_RECORD].content.decode(
            "utf-8", errors="strict"
        )
        notice_text = bindings[THIRD_PARTY_NOTICE].content.decode(
            "utf-8", errors="strict"
        )
    except UnicodeError as exc:
        raise PacketError(f"review documents must be UTF-8: {exc}") from exc

    observed_review = dict(source_document["review"])
    observed_record = _initial_record_fields(record_text)
    observed_notice = {
        field: _single_markdown_scalar(
            notice_text, field, "third-party notice review metadata"
        )
        for field in ("status", "reviewer", "reviewed_at")
    }
    target_states = sorted(
        (
            {"path": target["path"], "status": target.get("status")}
            for target in source_document["targets"]
        ),
        key=lambda item: item["path"],
    )

    review_status = str(validation["review_status"])
    question_state = (
        "PENDING_HUMAN_DECISION"
        if review_status == PENDING_RECORD_STATUS
        else "VALID_APPROVED_RECORD_OBSERVATION_ONLY"
    )
    source_heading, source_decisions = _source_decision_bindings(record_text)
    questions = [
        {
            **definition,
            "authoritative_source": PROVENANCE_RECORD,
            "packet_question_section_id": QUESTION_SECTION_ID,
            "source_question_sha256": _sha256(
                source_decisions[definition["source_decision_number"]].encode("utf-8")
            ),
            "observed_record_status": review_status,
            "workflow_state": question_state,
        }
        for definition in QUESTION_DEFINITIONS
    ]

    selected_tree_oid = root_tree_oid or _git_tree_oid(repository_root, commit)
    instruction_content = _reviewer_instructions(
        commit,
        selected_tree_oid,
        review_status,
    )
    total_size += len(instruction_content)
    if total_size > MAX_PACKET_BYTES:
        raise PacketError(f"packet payload exceeds {MAX_PACKET_BYTES} total bytes")

    file_entries: list[dict[str, object]] = []
    payloads: dict[str, bytes] = {}
    for repository_path in sorted(bindings):
        binding = bindings[repository_path]
        packet_path = _safe_relative_path(
            f"{PAYLOAD_ROOT}/{repository_path}",
            f"derived packet path for {repository_path}",
        )
        file_entries.append(
            {
                "repository_path": repository_path,
                "packet_path": packet_path,
                "git_mode": binding.mode,
                "git_object_type": binding.object_type,
                "git_blob_oid": binding.oid,
                "raw_sha256": _sha256(binding.content),
                "size": len(binding.content),
            }
        )
        payloads[packet_path] = binding.content

    instruction_entry = {
        "packet_path": REVIEW_INSTRUCTIONS_NAME,
        "media_type": "text/markdown; charset=utf-8",
        "content_role": "REVIEWER_ENTRY_POINT",
        "scope": "FORGE_GRADLE_PROVENANCE_LICENSE_SUBREVIEW_ONLY",
        "binding": "PACKET_MANIFEST_SIZE_AND_SHA256",
        "raw_sha256": _sha256(instruction_content),
        "size": len(instruction_content),
    }
    payloads[REVIEW_INSTRUCTIONS_NAME] = instruction_content

    tools = [
        _tool_descriptor(role, path, commit, tool_bindings[path])
        for role, path in TOOL_DEFINITIONS
    ]
    manifest: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "scope_version": SCOPE_VERSION,
        "packet_purpose": "HUMAN_REVIEW_INPUTS_ONLY",
        "scope_statement": (
            "This packet is a read-only snapshot of selected-commit Git-object "
            "inputs for the Forge/Gradle provenance and license subreview only. "
            "It does not establish full-repository originality or final G0. "
            "Complete mechanical validation does not answer a human decision, "
            "author review metadata, or change any release Gate."
        ),
        "source_commit": commit,
        "source_commit_object_type": "commit",
        "source_tree_oid": selected_tree_oid,
        "tool_identity": {
            "tool_commit": commit,
            "runtime_match_policy": (
                "Runtime generator and selected validator matched the selected "
                "Git blobs byte for byte."
            ),
            "tools": tools,
        },
        "files": file_entries,
        "reviewer_instructions": instruction_entry,
        "mechanical_validation": {
            "status": "PASS",
            "scope": "COMPLETE_SCHEMA3_PROVENANCE_SELECTED_COMMIT",
            "selected_commit": commit,
            "validator_path": VALIDATOR_PATH,
            "validator_git_blob_oid": tool_bindings[VALIDATOR_PATH].oid,
            "components": validation["components"],
            "targets": validation["targets"],
            "local_assets": validation["local_assets"],
            "observed_review_status": review_status,
            "human_approval_effect": "NONE",
        },
        "packet_construction": {
            "bound_payload_file_count": len(file_entries),
            "bound_payload_bytes": sum(entry["size"] for entry in file_entries),
            "generated_payload_file_count": 1,
            "generated_payload_bytes": len(instruction_content),
            "total_payload_file_count": len(payloads),
            "total_payload_bytes": sum(len(content) for content in payloads.values()),
            "mechanical_evidence_json_count": len(MECHANICAL_JSON_PATHS),
        },
        "observed_review_state": {
            "manifest_review": observed_review,
            "record_review_fields": observed_record,
            "notice_review_fields": observed_notice,
            "target_review_fields": target_states,
        },
        "question_section": {
            "id": QUESTION_SECTION_ID,
            "id_owner": "PACKET_SCHEMA",
            "authoritative_source": PROVENANCE_RECORD,
            "authoritative_source_heading": source_heading,
            "authoritative_source_question_count": len(source_decisions),
            "observed_record_status": review_status,
            "workflow_state": question_state,
            "questions": questions,
        },
        "review_content_binding": _review_content_binding(
            source_document, validation
        ),
    }
    return manifest, payloads


def _authoritative_expectation(
    repository_root: Path, commit: str
) -> tuple[dict[str, Any], dict[str, bytes]]:
    _validate_runtime_dependency_origins(repository_root)
    root_tree_oid = _verified_commit_tree_oid(repository_root, commit)
    tree_cache: dict[str, tuple[list[tuple[str, str, str, str]], int]] = {}
    tool_bindings = _validate_runtime_tool_bindings(
        repository_root,
        commit,
        root_tree_oid=root_tree_oid,
        tree_cache=tree_cache,
    )
    _validate_selected_tree_bounds(
        repository_root,
        commit,
        root_tree_oid=root_tree_oid,
        tree_cache=tree_cache,
    )
    validation = _run_selected_commit_validation(
        repository_root, commit, tool_bindings
    )
    return _build_packet(
        repository_root,
        commit,
        validation,
        tool_bindings,
        root_tree_oid=root_tree_oid,
        tree_cache=tree_cache,
    )


def _destination(packet_root: Path, packet_path: str) -> Path:
    packet_path = _safe_relative_path(packet_path, "packet path")
    destination = packet_root.joinpath(*PurePosixPath(packet_path).parts)
    try:
        destination.relative_to(packet_root)
    except ValueError as exc:
        raise PacketError(f"packet path escapes packet directory: {packet_path}") from exc
    return destination


def _packet_directory_component_snapshot(
    packet_root: Path,
    packet_path: str,
    label: str,
) -> tuple[tuple[str, tuple[int, int, int, int, int, int]], ...]:
    _destination(packet_root, packet_path)
    components: list[tuple[str, tuple[int, int, int, int, int, int]]] = []
    cursor = packet_root
    relative_components = (".", *PurePosixPath(packet_path).parts[:-1])
    for index, component in enumerate(relative_components):
        if index:
            cursor /= component
        try:
            status = cursor.lstat()
        except OSError as exc:
            raise PacketError(
                f"cannot inspect {label} directory component {cursor}: {exc}"
            ) from exc
        if stat.S_ISLNK(status.st_mode) or _is_reparse_point(cursor, status):
            raise PacketError(
                f"{label} traverses a symlink, junction, or reparse point: {cursor}"
            )
        if not stat.S_ISDIR(status.st_mode):
            raise PacketError(
                f"{label} directory component is not an ordinary directory: {cursor}"
            )
        components.append(
            (
                "." if index == 0 else component,
                _stable_directory_identity(status),
            )
        )
    return tuple(components)


def _read_bounded_packet_file(
    packet_root: Path,
    packet_path: str,
    label: str,
    *,
    maximum_size: int,
    expected_size: int | None = None,
) -> bytes:
    before = _packet_directory_component_snapshot(packet_root, packet_path, label)
    content = _read_bounded_regular_file(
        _destination(packet_root, packet_path),
        label,
        maximum_size=maximum_size,
        expected_size=expected_size,
    )
    after = _packet_directory_component_snapshot(packet_root, packet_path, label)
    if after != before:
        raise PacketError(f"{label} parent directory components changed while reading")
    return content


def _expected_directories(files: set[str]) -> set[str]:
    directories: set[str] = set()
    for value in files:
        parts = PurePosixPath(value).parts[:-1]
        for index in range(1, len(parts) + 1):
            directories.add("/".join(parts[:index]))
    return directories


def _packet_inventory(
    packet_root: Path,
) -> tuple[set[str], set[str], list[str]]:
    files: set[str] = set()
    directories_seen: set[str] = set()
    errors: list[str] = []
    aggregate_size = 0
    entry_count = 0
    maximum_entries = MAX_PACKET_FILES + MAX_PACKET_DIRECTORIES + 1
    pending_directories = [packet_root]
    portable_entries: dict[str, tuple[str, str]] = {}

    while pending_directories:
        current = pending_directories.pop()
        try:
            iterator = os.scandir(current)
        except OSError as exc:
            errors.append(f"cannot traverse packet directory {current}: {exc}")
            continue
        with iterator:
            for entry in iterator:
                entry_count += 1
                if entry_count > maximum_entries:
                    errors.append(
                        f"packet exceeds {maximum_entries} total filesystem entries"
                    )
                    return files, directories_seen, errors
                path = Path(entry.path)
                try:
                    relative = path.relative_to(packet_root).as_posix()
                    path_error = _relative_path_error(relative)
                    status = entry.stat(follow_symlinks=False)
                except (OSError, ValueError) as exc:
                    errors.append(f"cannot inspect packet entry {path}: {exc}")
                    continue
                if path_error:
                    errors.append(
                        f"packet entry path is unsafe: {relative!r}: {path_error}"
                    )
                    continue
                if stat.S_ISLNK(status.st_mode) or _is_reparse_point(path, status):
                    errors.append(
                        "packet contains an unsafe symlink, junction, or reparse "
                        f"point: {relative}"
                    )
                    continue
                entry_kind = (
                    "directory"
                    if stat.S_ISDIR(status.st_mode)
                    else "file"
                    if stat.S_ISREG(status.st_mode)
                    else "other"
                )
                portable_key = _portable_path_key(relative)
                previous = portable_entries.get(portable_key)
                if previous is not None and previous != (relative, entry_kind):
                    errors.append(
                        "packet contains a case-insensitive or Unicode-normalized "
                        f"path collision: {previous[0]} ({previous[1]}) and "
                        f"{relative} ({entry_kind})"
                    )
                    continue
                portable_entries[portable_key] = (relative, entry_kind)
                if stat.S_ISDIR(status.st_mode):
                    directories_seen.add(relative)
                    if len(directories_seen) > MAX_PACKET_DIRECTORIES:
                        errors.append(
                            f"packet exceeds {MAX_PACKET_DIRECTORIES} directories"
                        )
                        return files, directories_seen, errors
                    pending_directories.append(path)
                    continue
                if not stat.S_ISREG(status.st_mode):
                    errors.append(
                        f"packet contains a non-regular file: {relative}"
                    )
                    continue
                if status.st_size > MAX_FILE_BYTES:
                    errors.append(
                        f"packet file exceeds {MAX_FILE_BYTES} bytes: {relative}"
                    )
                    continue
                aggregate_size += status.st_size
                if aggregate_size > MAX_OBSERVED_PACKET_BYTES:
                    errors.append(
                        "packet aggregate size exceeds "
                        f"{MAX_OBSERVED_PACKET_BYTES} bytes"
                    )
                    return files, directories_seen, errors
                files.add(relative)
                if len(files) > MAX_PACKET_FILES + 1:
                    errors.append(f"packet exceeds {MAX_PACKET_FILES + 1} files")
                    return files, directories_seen, errors
    return files, directories_seen, errors


def _validate_untrusted_manifest(document: dict[str, Any], errors: list[str]) -> None:
    expected_top_level_fields = {
        "schema_version",
        "scope_version",
        "packet_purpose",
        "scope_statement",
        "source_commit",
        "source_commit_object_type",
        "source_tree_oid",
        "tool_identity",
        "files",
        "reviewer_instructions",
        "mechanical_validation",
        "packet_construction",
        "observed_review_state",
        "question_section",
        "review_content_binding",
    }
    if set(document) != expected_top_level_fields:
        errors.append("packet manifest top-level fields are invalid")
    if document.get("schema_version") != SCHEMA_VERSION:
        errors.append(f"packet schema_version must be {SCHEMA_VERSION}")
    if document.get("scope_version") != SCOPE_VERSION:
        errors.append(f"packet scope_version must be {SCOPE_VERSION}")
    if document.get("packet_purpose") != "HUMAN_REVIEW_INPUTS_ONLY":
        errors.append("packet purpose is invalid")
    if FULL_COMMIT.fullmatch(str(document.get("source_commit", ""))) is None:
        errors.append("packet source_commit must be a full lowercase commit SHA")
    if document.get("source_commit_object_type") != "commit":
        errors.append("packet source_commit_object_type must be commit")
    if OBJECT_ID.fullmatch(str(document.get("source_tree_oid", ""))) is None:
        errors.append("packet source_tree_oid is invalid")

    entries = document.get("files")
    if not isinstance(entries, list):
        errors.append("packet files must be a list")
        return
    if len(entries) + 1 > MAX_PACKET_FILES:
        errors.append(
            f"packet files plus reviewer instructions exceeds {MAX_PACKET_FILES} entries"
        )
        return
    seen_repository: set[str] = set()
    seen_packet: set[str] = set()
    portable_repository: dict[str, str] = {}
    portable_packet: dict[str, str] = {}
    declared_size = 0
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"packet files[{index}] must be an object")
            continue
        expected_fields = {
            "repository_path",
            "packet_path",
            "git_mode",
            "git_object_type",
            "git_blob_oid",
            "raw_sha256",
            "size",
        }
        if set(entry) != expected_fields:
            errors.append(f"packet files[{index}] fields are invalid")
        for field, seen, portable_seen in (
            ("repository_path", seen_repository, portable_repository),
            ("packet_path", seen_packet, portable_packet),
        ):
            value = entry.get(field)
            path_error = _relative_path_error(value)
            if path_error:
                errors.append(
                    f"packet files[{index}].{field} is unsafe: "
                    f"{_bounded_error_text(repr(value))}: {path_error}"
                )
            elif value in seen:
                errors.append(f"packet contains duplicate {field}: {value}")
            else:
                assert isinstance(value, str)
                seen.add(value)
                portable_key = _portable_path_key(value)
                previous = portable_seen.get(portable_key)
                if previous is not None and previous != value:
                    errors.append(
                        f"packet contains a case-insensitive or Unicode-normalized "
                        f"{field} collision: {previous} and {value}"
                    )
                else:
                    portable_seen[portable_key] = value
        repository_path = entry.get("repository_path")
        packet_path = entry.get("packet_path")
        if (
            isinstance(repository_path, str)
            and _relative_path_error(repository_path) is None
            and isinstance(packet_path, str)
            and _relative_path_error(packet_path) is None
            and packet_path != f"{PAYLOAD_ROOT}/{repository_path}"
        ):
            errors.append(
                f"packet files[{index}] packet_path is not derived from repository_path"
            )
        if entry.get("git_mode") not in ALLOWED_GIT_MODES:
            errors.append(f"packet files[{index}] git_mode is invalid")
        if entry.get("git_object_type") != "blob":
            errors.append(f"packet files[{index}] git_object_type must be blob")
        if OBJECT_ID.fullmatch(str(entry.get("git_blob_oid", ""))) is None:
            errors.append(f"packet files[{index}] git_blob_oid is invalid")
        if SHA256.fullmatch(str(entry.get("raw_sha256", ""))) is None:
            errors.append(f"packet files[{index}] raw_sha256 is invalid")
        size = entry.get("size")
        if type(size) is not int or size < 0 or size > MAX_FILE_BYTES:
            errors.append(f"packet files[{index}] size is invalid")
        else:
            declared_size += size

    valid_repository_paths = [
        entry.get("repository_path")
        for entry in entries
        if isinstance(entry, dict)
        and isinstance(entry.get("repository_path"), str)
        and _relative_path_error(entry.get("repository_path")) is None
    ]
    if valid_repository_paths != sorted(valid_repository_paths):
        errors.append("packet files must be sorted by repository_path")

    instruction = document.get("reviewer_instructions")
    instruction_size: int | None = None
    if not isinstance(instruction, dict):
        errors.append("packet reviewer_instructions must be an object")
    else:
        expected_instruction_fields = {
            "packet_path",
            "media_type",
            "content_role",
            "scope",
            "binding",
            "raw_sha256",
            "size",
        }
        if set(instruction) != expected_instruction_fields:
            errors.append("packet reviewer_instructions fields are invalid")
        instruction_path = instruction.get("packet_path")
        instruction_path_error = _relative_path_error(instruction_path)
        if instruction_path_error:
            errors.append(
                "packet reviewer instructions packet_path is unsafe: "
                f"{_bounded_error_text(repr(instruction_path))}: "
                f"{instruction_path_error}"
            )
        else:
            assert isinstance(instruction_path, str)
            if instruction_path != REVIEW_INSTRUCTIONS_NAME:
                errors.append(
                    "packet reviewer instructions path must be "
                    f"{REVIEW_INSTRUCTIONS_NAME}"
                )
            if instruction_path in seen_packet:
                errors.append(
                    f"packet contains duplicate packet_path: {instruction_path}"
                )
            portable_key = _portable_path_key(instruction_path)
            previous = portable_packet.get(portable_key)
            if previous is not None and previous != instruction_path:
                errors.append(
                    "packet contains a case-insensitive or Unicode-normalized "
                    f"packet_path collision: {previous} and {instruction_path}"
                )
            seen_packet.add(instruction_path)
            portable_packet[portable_key] = instruction_path
        if instruction.get("media_type") != "text/markdown; charset=utf-8":
            errors.append("packet reviewer instructions media_type is invalid")
        if instruction.get("content_role") != "REVIEWER_ENTRY_POINT":
            errors.append("packet reviewer instructions content_role is invalid")
        if (
            instruction.get("scope")
            != "FORGE_GRADLE_PROVENANCE_LICENSE_SUBREVIEW_ONLY"
        ):
            errors.append("packet reviewer instructions scope is invalid")
        if instruction.get("binding") != "PACKET_MANIFEST_SIZE_AND_SHA256":
            errors.append("packet reviewer instructions binding is invalid")
        if SHA256.fullmatch(str(instruction.get("raw_sha256", ""))) is None:
            errors.append("packet reviewer instructions raw_sha256 is invalid")
        raw_instruction_size = instruction.get("size")
        if (
            type(raw_instruction_size) is not int
            or raw_instruction_size < 0
            or raw_instruction_size > MAX_FILE_BYTES
        ):
            errors.append("packet reviewer instructions size is invalid")
        else:
            instruction_size = raw_instruction_size
            declared_size += raw_instruction_size

    if declared_size > MAX_PACKET_BYTES:
        errors.append(f"packet manifest declares more than {MAX_PACKET_BYTES} payload bytes")

    construction = document.get("packet_construction")
    expected_construction_fields = {
        "bound_payload_file_count",
        "bound_payload_bytes",
        "generated_payload_file_count",
        "generated_payload_bytes",
        "total_payload_file_count",
        "total_payload_bytes",
        "mechanical_evidence_json_count",
    }
    if not isinstance(construction, dict):
        errors.append("packet packet_construction must be an object")
    elif set(construction) != expected_construction_fields:
        errors.append("packet packet_construction fields are invalid")
    else:
        bound_size = sum(
            entry.get("size", 0)
            for entry in entries
            if isinstance(entry, dict) and type(entry.get("size")) is int
        )
        expected_counts = {
            "bound_payload_file_count": len(entries),
            "bound_payload_bytes": bound_size,
            "generated_payload_file_count": 1,
            "generated_payload_bytes": instruction_size,
            "total_payload_file_count": len(entries) + 1,
            "total_payload_bytes": (
                None if instruction_size is None else bound_size + instruction_size
            ),
            "mechanical_evidence_json_count": len(MECHANICAL_JSON_PATHS),
        }
        for field, expected in expected_counts.items():
            if construction.get(field) != expected:
                errors.append(
                    f"packet packet_construction {field} is inconsistent with payload declarations"
                )


def _manifest_payload_declarations(
    document: dict[str, Any],
) -> dict[str, tuple[int, str]]:
    declarations: dict[str, tuple[int, str]] = {}
    entries = document.get("files")
    if isinstance(entries, list):
        for entry in entries[:MAX_PACKET_FILES]:
            if not isinstance(entry, dict):
                continue
            path = entry.get("packet_path")
            size = entry.get("size")
            digest = entry.get("raw_sha256")
            if (
                isinstance(path, str)
                and _relative_path_error(path) is None
                and type(size) is int
                and 0 <= size <= MAX_FILE_BYTES
                and isinstance(digest, str)
                and SHA256.fullmatch(digest) is not None
                and path not in declarations
            ):
                declarations[path] = (size, digest)
    instruction = document.get("reviewer_instructions")
    if isinstance(instruction, dict):
        path = instruction.get("packet_path")
        size = instruction.get("size")
        digest = instruction.get("raw_sha256")
        if (
            isinstance(path, str)
            and _relative_path_error(path) is None
            and type(size) is int
            and 0 <= size <= MAX_FILE_BYTES
            and isinstance(digest, str)
            and SHA256.fullmatch(digest) is not None
            and path not in declarations
        ):
            declarations[path] = (size, digest)
    return declarations


def _generate_packet_resolved(
    repository_root: Path, commit: str, output_directory: Path
) -> dict[str, Any]:
    output = _safe_ignored_directory(
        repository_root,
        output_directory,
        "output directory",
        require_exists=False,
    )
    manifest, payloads = _authoritative_expectation(repository_root, commit)
    manifest_content = _canonical_json(manifest)
    if len(manifest_content) > MAX_MANIFEST_BYTES:
        raise PacketError(
            f"generated manifest exceeds {MAX_MANIFEST_BYTES} bytes"
        )
    output.mkdir()
    try:
        for packet_path in sorted(payloads):
            destination = _destination(output, packet_path)
            destination.parent.mkdir(parents=True, exist_ok=True)
            with destination.open("xb") as stream:
                stream.write(payloads[packet_path])
        with (output / MANIFEST_NAME).open("xb") as stream:
            stream.write(manifest_content)
        return manifest
    except BaseException:
        shutil.rmtree(output, ignore_errors=True)
        raise


def generate_packet(
    repository_root: Path, commit_spec: str, output_directory: Path
) -> dict[str, Any]:
    repository_root = _repository_root(repository_root)
    commit = resolve_commit(repository_root, commit_spec)
    return _generate_packet_resolved(repository_root, commit, output_directory)


def _read_declared_payloads(
    packet_root: Path,
    declarations: dict[str, tuple[int, str]],
    observed_files: set[str],
    errors: list[str],
    *,
    pass_label: str,
) -> dict[str, bytes]:
    snapshots: dict[str, bytes] = {}
    for packet_path, (expected_size, expected_digest) in sorted(
        declarations.items()
    ):
        if packet_path not in observed_files:
            continue
        try:
            content = _read_bounded_packet_file(
                packet_root,
                packet_path,
                f"packet payload {packet_path}",
                maximum_size=MAX_FILE_BYTES,
                expected_size=expected_size,
            )
        except PacketError as exc:
            errors.append(
                f"cannot read packet payload during {pass_label} pass "
                f"{packet_path}: {exc}"
            )
            continue
        if _sha256(content) != expected_digest:
            errors.append(
                f"packet payload SHA-256 differs during {pass_label} pass: "
                f"{packet_path}"
            )
        snapshots[packet_path] = content
    return snapshots


def _inspect_packet_content(
    packet_root: Path,
) -> tuple[list[str], dict[str, Any] | None, dict[str, bytes]]:
    errors: list[str] = []
    manifest_path = packet_root / MANIFEST_NAME
    manifest_error = _ordinary_path_error(manifest_path, directory=False)
    if manifest_error:
        return (
            [f"packet is missing a safe ordinary {MANIFEST_NAME}: {manifest_error}"],
            None,
            {},
        )
    try:
        manifest_content = _read_bounded_packet_file(
            packet_root,
            MANIFEST_NAME,
            "packet manifest",
            maximum_size=MAX_MANIFEST_BYTES,
        )
        document = _load_json(manifest_content, MANIFEST_NAME)
    except (OSError, PacketError) as exc:
        return [f"cannot read packet manifest: {exc}"], None, {}

    _validate_untrusted_manifest(document, errors)
    try:
        canonical_manifest = _canonical_json(document)
    except PacketError as exc:
        errors.append(f"packet manifest cannot be canonically encoded: {exc}")
    else:
        if manifest_content != canonical_manifest:
            errors.append("packet manifest is not the deterministic canonical encoding")

    declarations = _manifest_payload_declarations(document)
    observed_files, observed_directories, inventory_errors = _packet_inventory(
        packet_root
    )
    errors.extend(inventory_errors)
    expected_files = {MANIFEST_NAME, *declarations}
    expected_directories = _expected_directories(expected_files)
    missing = sorted(expected_files - observed_files)
    extra = sorted(observed_files - expected_files)
    extra_directories = sorted(observed_directories - expected_directories)
    if missing:
        errors.append("packet is missing files: " + ", ".join(missing))
    if extra:
        errors.append("packet has unexpected files: " + ", ".join(extra))
    if extra_directories:
        errors.append(
            "packet has unexpected directories: " + ", ".join(extra_directories)
        )

    first_snapshots = _read_declared_payloads(
        packet_root,
        declarations,
        observed_files,
        errors,
        pass_label="initial",
    )

    final_files, final_directories, final_inventory_errors = _packet_inventory(
        packet_root
    )
    errors.extend(f"final inventory: {error}" for error in final_inventory_errors)
    if final_files != observed_files or final_directories != observed_directories:
        errors.append("packet inventory changed during verification")
    try:
        final_manifest_content = _read_bounded_packet_file(
            packet_root,
            MANIFEST_NAME,
            "packet manifest final snapshot",
            maximum_size=MAX_MANIFEST_BYTES,
            expected_size=len(manifest_content),
        )
    except PacketError as exc:
        errors.append(f"cannot re-read packet manifest: {exc}")
    else:
        if final_manifest_content != manifest_content:
            errors.append("packet manifest changed during verification")
    final_snapshots = _read_declared_payloads(
        packet_root,
        declarations,
        final_files,
        errors,
        pass_label="final",
    )
    if final_snapshots != first_snapshots:
        errors.append("packet payload bytes changed during verification")
    return errors, document, final_snapshots


def verify_packet_content_only(packet_directory: Path) -> list[str]:
    """Verify a private, quiescent packet copy without Git authenticity."""

    try:
        packet_root = _safe_offline_packet_directory(packet_directory)
    except PacketError as exc:
        return [str(exc)]
    errors, _, _ = _inspect_packet_content(packet_root)
    return errors


def _verify_packet_resolved(
    repository_root: Path, commit: str, packet_directory: Path
) -> list[str]:
    try:
        packet_root = _safe_ignored_directory(
            repository_root,
            packet_directory,
            "packet directory",
            require_exists=True,
        )
    except PacketError as exc:
        return [str(exc)]

    errors, document, observed_payloads = _inspect_packet_content(packet_root)
    if document is None:
        return errors
    if document.get("source_commit") != commit:
        errors.append(
            f"packet commit binding is {document.get('source_commit')}, expected {commit}"
        )

    try:
        expected_manifest, expected_payloads = _authoritative_expectation(
            repository_root, commit
        )
    except PacketError as exc:
        errors.append(f"cannot rebuild authoritative packet expectation: {exc}")
        return errors
    if document != expected_manifest:
        errors.append("packet manifest differs from authoritative selected-commit inputs")
    for packet_path, expected_content in sorted(expected_payloads.items()):
        observed_content = observed_payloads.get(packet_path)
        if observed_content is not None and observed_content != expected_content:
            errors.append(
                "packet payload differs from authoritative selected-commit "
                f"expectation: {packet_path}"
            )
    return errors


def verify_packet(
    repository_root: Path, commit_spec: str, packet_directory: Path
) -> list[str]:
    try:
        repository_root = _repository_root(repository_root)
        commit = resolve_commit(repository_root, commit_spec)
    except PacketError as exc:
        return [str(exc)]
    return _verify_packet_resolved(repository_root, commit, packet_directory)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=ROOT,
        help="Git repository top level (defaults to this project)",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("generate", "verify"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument(
            "--commit",
            default=DEFAULT_COMMIT,
            help="full lowercase commit SHA, or HEAD only when the checkout is clean",
        )
        if command == "generate":
            subparser.add_argument("--output", type=Path, required=True)
        else:
            subparser.add_argument("--packet", type=Path, required=True)
    content_only = subparsers.add_parser(
        "verify-content-only",
        help=(
            "weaker offline check of packet-local inventory, sizes, and hashes; "
            "requires a separately trusted verifier and a private quiescent copy; "
            "does not authenticate Git or approval"
        ),
    )
    content_only.add_argument("--packet", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        if args.command == "verify-content-only":
            errors = verify_packet_content_only(args.packet)
            if errors:
                for error in errors:
                    print(f"[FAIL] {error}", file=sys.stderr)
                return 1
            print("[PASS] Verified bounded packet-local inventory, sizes, and hashes")
            print(
                "[INFO] CONTENT-ONLY CHECK: Git commit/tree authenticity, provenance "
                "semantics, and human approval were not verified."
            )
            print(
                "[INFO] Use only a separately authenticated verifier and a private, "
                "quiescent packet copy; never execute code from an unauthenticated "
                "packet. Hostile concurrent directory replacement is outside this "
                "check's guarantee."
            )
            return 0

        repository_root = _repository_root(args.repository_root)
        commit = resolve_commit(repository_root, args.commit)
        if args.command == "generate":
            manifest = _generate_packet_resolved(
                repository_root, commit, args.output
            )
            print(
                "[PASS] Generated "
                f"{manifest['packet_construction']['total_payload_file_count']} "
                f"manifest-bound G0 subreview payloads for {commit} at {args.output}"
            )
            print(
                "[INFO] Complete mechanical validation does not approve G0 or "
                "author human review data."
            )
            return 0

        errors = _verify_packet_resolved(repository_root, commit, args.packet)
        if errors:
            for error in errors:
                print(f"[FAIL] {error}", file=sys.stderr)
            return 1
        print(f"[PASS] Verified commit-bound v0.0.2 G0 review packet for {commit}")
        print(
            "[INFO] Verification observes selected-commit state and does not "
            "approve G0 or author human review data."
        )
        print(
            "[INFO] Verification assumes a private, quiescent packet directory; "
            "hostile concurrent directory replacement is outside this "
            "cross-platform check's guarantee."
        )
        return 0
    except PacketError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
