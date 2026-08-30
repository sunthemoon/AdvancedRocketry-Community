#!/usr/bin/env python3
"""Validate the machine-readable v0.0.2 bootstrap provenance record."""

from __future__ import annotations

import argparse
import copy
import fnmatch
import hashlib
import json
import math
import os
import re
import shutil
import stat
import subprocess
import sys
import threading
import unicodedata
from datetime import date
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any, Callable
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
GIT_EXECUTABLE_CANDIDATE = shutil.which("git")
DEFAULT_MANIFEST = Path("docs/provenance/v0.0.2-bootstrap-inputs.json")
EXPECTED_RECORD_PATH = "docs/provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md"
EXPECTED_NOTICE_PATH = "THIRD-PARTY-NOTICES.md"

SHA256 = re.compile(r"[0-9a-f]{64}")
COMMIT = re.compile(r"[0-9a-f]{40}")
GIT_OBJECT_ID = re.compile(r"[0-9a-f]{40}")

PENDING_RECORD_STATUS = "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING"
APPROVED_RECORD_STATUS = "THIRD_PARTY_APPROVED"
PENDING_TARGET_STATUS = "PENDING_HUMAN_REVIEW"

EXPECTED_COMPONENTS = {
    "forge_mdk": {
        "source_repository": "https://github.com/MinecraftForge/MinecraftForge",
        "source_commit": "132704e5f23dbee28d776738eb1c0c42fefc0bf6",
        "source_artifact": "forge-1.20.1-47.4.10-mdk.zip",
        "source_sha256": "73e0122becd05e39b47eced54e030380d66411850ed86786a2d58ecd886b0451",
        "license": "LGPL-2.1-only",
        "license_source_path": "LICENSE.txt",
        "license_source_sha256": "481c96d94d182382c4225d5b210f8c658c85350cf548f25c9f56c058804f1e57",
        "license_copy_target": (
            "docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt"
        ),
        "license_copy_target_sha256": "481c96d94d182382c4225d5b210f8c658c85350cf548f25c9f56c058804f1e57",
        "source_tree_materializations": [
            {
                "artifact_eol": "CRLF",
                "artifact_member": "build.gradle",
                "artifact_member_sha256": (
                    "f50947b2af27e834f58860360a084825951fe987d9dd8ea180e7eee261629d77"
                ),
                "comparison": "BYTE_IDENTICAL_AFTER_CRLF_TO_LF_NORMALIZATION",
                "source_eol": "LF",
                "source_path": "mdk/build.gradle",
                "source_raw_sha256": (
                    "c068e44d6b6eca1f1588527ad23554c5798e146515ff321fe7a0804d0daefb07"
                ),
            },
            {
                "artifact_eol": "CRLF",
                "artifact_member": "settings.gradle",
                "artifact_member_sha256": (
                    "21c8c1cfea9f78f7fed6d7ad325aafa24e0d1bd330a40719997c303d2217b830"
                ),
                "comparison": "BYTE_IDENTICAL_AFTER_CRLF_TO_LF_NORMALIZATION",
                "source_eol": "LF",
                "source_path": "mdk/settings.gradle",
                "source_raw_sha256": (
                    "f0caadd216f2cccbe612f85ba95f8c4996e84c16a5abfe5b9daef30acfc93945"
                ),
            },
        ],
    },
    "gradle_wrapper": {
        "source_repository": "https://github.com/gradle/gradle",
        "source_commit": "1cf537a851c635c364a4214885f8b9798051175b",
        "source_artifact": "gradle/wrapper/gradle-wrapper.jar",
        "source_sha256": "ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58",
        "license": "Apache-2.0",
        "license_source_path": "LICENSE",
        "license_source_sha256": "e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437",
        "license_copy_target": "docs/licenses/GRADLE-8.1.1-LICENSE.txt",
        "license_copy_target_sha256": "e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437",
    },
}

EXPECTED_TARGET_COMPONENTS = {
    ".gitattributes": "forge_mdk",
    ".gitignore": "forge_mdk",
    "build.gradle": "forge_mdk",
    "gradle.properties": "forge_mdk",
    "settings.gradle": "forge_mdk",
    "gradle/wrapper/gradle-wrapper.properties": "forge_mdk",
    "src/main/resources/pack.mcmeta": "forge_mdk",
    "src/main/resources/META-INF/mods.toml": "forge_mdk",
    "gradlew": "gradle_wrapper",
    "gradlew.bat": "gradle_wrapper",
    "gradle/wrapper/gradle-wrapper.jar": "gradle_wrapper",
}

EXPECTED_TARGET_SOURCES = {
    ".gitattributes": (
        ".gitattributes",
        "448fc4f5b88765df18eec1a82fb0ed09d7fb148e50fe49bd004be05effb06285",
    ),
    ".gitignore": (
        ".gitignore",
        "efc7995172c172d5e2a5dfad4484abc9f4b6030aa2bf5bb9453180043f8d593f",
    ),
    "build.gradle": (
        "build.gradle",
        "f50947b2af27e834f58860360a084825951fe987d9dd8ea180e7eee261629d77",
    ),
    "gradle.properties": (
        "gradle.properties",
        "5b5aa1d63c2d02c2a39f8a207bdc91a7fbcddbfb50e0e92c59da3ecd6a6478a6",
    ),
    "settings.gradle": (
        "settings.gradle",
        "21c8c1cfea9f78f7fed6d7ad325aafa24e0d1bd330a40719997c303d2217b830",
    ),
    "gradle/wrapper/gradle-wrapper.properties": (
        "gradle/wrapper/gradle-wrapper.properties",
        "0c0c22ccb8e653a13a7df19493b11fe5ce0f5fae7ee4835223d2d2ad028799da",
    ),
    "src/main/resources/pack.mcmeta": (
        "src/main/resources/pack.mcmeta",
        "69e4b682449054686cb0e5918f13501cd53ea33477d306d9d27f82f0fd9da3f1",
    ),
    "src/main/resources/META-INF/mods.toml": (
        "src/main/resources/META-INF/mods.toml",
        "ed0e7e454f6f1c1ffbfcabdece14ded32321450a23e7203acf1382523b87415a",
    ),
    "gradlew": (
        "gradlew",
        "fb3cbfe6d066ee52bc07f62ed61ff77bde195384f52496c94280a83008d9f531",
    ),
    "gradlew.bat": (
        "gradlew.bat",
        "8e327fcb99d29ce0fe3ee2fec6e6a25de815a2df83a6a44a553dea89ffc92955",
    ),
    "gradle/wrapper/gradle-wrapper.jar": (
        "gradle/wrapper/gradle-wrapper.jar",
        "ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58",
    ),
}

EXPECTED_MATERIALIZED_TARGETS = frozenset(("gradlew.bat",))

EXPECTED_LOCAL_ASSETS = {
    "src/main/resources/advancedrocketrycommunity.png": ("NEW", "MIT"),
    "src/generated/resources/data/advancedrocketrycommunity/structures/empty.nbt": (
        "GENERATED",
        "MIT",
    ),
}

RESOURCE_ROOTS = (
    "src/main/resources/",
    "src/generated/resources/",
)
# ForgeGradle DataGen writes implementation metadata here. It is ignored by Git,
# excluded from the JAR in build.gradle, and rejected by the artifact auditor, so
# it is not a distributable source resource that needs a provenance entry.
EXCLUDED_RESOURCE_PREFIXES = ("src/generated/resources/.cache/",)
EXPECTED_RESOURCE_PATHS = frozenset(
    path
    for path in (*EXPECTED_TARGET_COMPONENTS, *EXPECTED_LOCAL_ASSETS)
    if path.startswith(RESOURCE_ROOTS)
)

REVIEW_METADATA_FIELDS = (
    "record_status",
    "reviewer",
    "reviewed_at",
    "final_status_after_review",
    "reviewed_audited_target_commit",
    "reviewed_content_sha256",
)
REVIEW_DIGEST_DOMAIN = b"arce-v0.0.2-bootstrap-provenance-review-v3\0"
REVIEW_METADATA_SENTINEL = "<REVIEW-METADATA>"
MISSING_APPROVAL_DIGEST_ERROR = (
    f"approved review reviewed_content_sha256 must be lowercase {SHA256.pattern}"
)
GIT_TIMEOUT_SECONDS = 15
GIT_REGULAR_FILE_MODES = frozenset(("100644", "100755"))
GIT_TARGET_SNAPSHOTS = ("import", "audited")
MAX_JSON_DEPTH = 64
MAX_JSON_NODES = 100_000
MAX_SELECTED_RESOURCE_FILES = 1_024
MAX_SELECTED_RESOURCE_DIRECTORIES = 512
MAX_SELECTED_RESOURCE_RECORD_BYTES = 2_048
MAX_WORKTREE_RESOURCE_DIRECTORIES = 512
GIT_STREAM_CHUNK_BYTES = 64 * 1024
MAX_PROVENANCE_BLOB_BYTES = 32 * 1024 * 1024
MAX_PROVENANCE_PATH_BYTES = 512
MAX_PROVENANCE_PATH_DEPTH = 32
MAX_PROVENANCE_PATH_COMPONENT_BYTES = 255
MAX_PROVENANCE_PATH_COMPONENT_UTF16_UNITS = 255
MAX_ERROR_VALUE_CHARS = 256
MAX_GIT_COMMIT_OBJECT_BYTES = 1024 * 1024
MAX_GIT_COMMIT_PARENTS = 64
MAX_GIT_ANCESTRY_COMMITS = 100_000
MAX_GIT_ANCESTRY_BYTES = 256 * 1024 * 1024
MAX_GIT_BATCH_HEADER_BYTES = 128
MAX_GIT_TREE_OBJECT_BYTES = 8 * 1024 * 1024
MAX_GIT_TREE_LOOKUP_BYTES = 64 * 1024 * 1024
MAX_SELECTED_RESOURCE_TREE_BYTES = 64 * 1024 * 1024
MAX_MARKDOWN_YAML_FENCES = 32
MAX_MARKDOWN_FIELD_OCCURRENCES = 64
GitTreeEntry = tuple[str, str, str]

RECORD_IDENTITY_FIELDS = (
    "record_version",
    "scope_version",
    "import_commit",
    "audited_target_commit",
)
RECORD_ONLY_REVIEW_FIELDS = (
    "record_status",
    "final_status_after_review",
    "reviewed_audited_target_commit",
    "reviewed_content_sha256",
)
INITIAL_RECORD_FIELDS = (
    *RECORD_IDENTITY_FIELDS,
    *RECORD_ONLY_REVIEW_FIELDS,
    "reviewer",
    "reviewed_at",
)

APPROVED_DOCUMENT_CONTRADICTIONS = (
    (
        "pending provenance status",
        re.compile(
            r"\b(?:PENDING_HUMAN_REVIEW|EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING)\b"
        ),
    ),
    (
        "pending approval prose",
        re.compile(
            r"(?:record_status\s+is\s+intentionally\s+pending|"
            r"does\s+not\s+assign\s+`?THIRD_PARTY_APPROVED`?|"
            r"does\s+not\s+claim\s+human\s+license\s+approval|"
            r"pending\s+human\s+(?:license|decision|review)|"
            r"human\s+review\s+must\s+resolve|"
            r"approval\s+remains\s+pending|"
            r"pending\s+reviewer\s+confirmation|"
            r"unresolved\s+(?:record\s+)?fields)",
            re.IGNORECASE,
        ),
    ),
    (
        "incomplete binary-distribution obligations disclaimer",
        re.compile(
            r"does\s+not\s+claim(?:\s+that)?\s+"
            r"binary(?:-|\s+)distribution(?:\s+notice)?\s+"
            r"obligations\s+are\s+complete",
            re.IGNORECASE,
        ),
    ),
)
UNCHECKED_CHECKBOX = re.compile(r"^\s*-\s*\[\s\]\s+", re.MULTILINE)
YAML_FENCE = re.compile(
    r"```yaml[^\S\r\n]*\r?\n(?P<body>.*?)(?:\r?\n)```",
    re.DOTALL,
)


class DuplicateJsonKeyError(ValueError):
    """Raised when a provenance JSON object contains an ambiguous duplicate key."""


def _reject_nonfinite_json(value: str) -> None:
    raise ValueError(f"non-finite JSON number is forbidden: {value}")


def _reject_duplicate_json_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _nonempty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _validate_lower_hex(
    value: object,
    pattern: re.Pattern[str],
    label: str,
    errors: list[str],
) -> bool:
    if not isinstance(value, str) or pattern.fullmatch(value) is None:
        errors.append(f"{label} must be lowercase {pattern.pattern}")
        return False
    return True


def relative_path_error(value: str) -> str | None:
    """Return why a provenance path is unsafe, or ``None`` when portable."""

    if not value:
        return "path is empty"
    if value != value.strip():
        return "leading or trailing whitespace is not allowed"
    try:
        encoded_value = value.encode("utf-8", errors="strict")
    except UnicodeEncodeError:
        return "path must be valid Unicode encodable as UTF-8"
    if len(encoded_value) > MAX_PROVENANCE_PATH_BYTES:
        return f"path exceeds {MAX_PROVENANCE_PATH_BYTES} UTF-8 bytes"
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        return "control characters are not allowed"
    if "\\" in value:
        return "backslashes are not allowed; use POSIX separators"
    if any(character in '<>:"|?*' for character in value):
        return "platform-unsafe path characters are not allowed"

    posix_path = PurePosixPath(value)
    windows_path = PureWindowsPath(value)
    if posix_path.is_absolute() or windows_path.is_absolute() or windows_path.drive:
        return "absolute or drive-qualified paths are not allowed"
    raw_parts = value.split("/")
    if any(part in ("", ".", "..") for part in raw_parts):
        return "empty, current-directory, or traversal path segments are not allowed"
    if len(raw_parts) > MAX_PROVENANCE_PATH_DEPTH:
        return f"path exceeds {MAX_PROVENANCE_PATH_DEPTH} components"
    if posix_path.as_posix() != value:
        return "path is not a normalized POSIX relative path"
    for part in raw_parts:
        if len(part.encode("utf-8")) > MAX_PROVENANCE_PATH_COMPONENT_BYTES:
            return (
                "path components must not exceed "
                f"{MAX_PROVENANCE_PATH_COMPONENT_BYTES} UTF-8 bytes"
            )
        if (
            len(part.encode("utf-16-le")) // 2
            > MAX_PROVENANCE_PATH_COMPONENT_UTF16_UNITS
        ):
            return (
                "path components must not exceed "
                f"{MAX_PROVENANCE_PATH_COMPONENT_UTF16_UNITS} UTF-16 code units"
            )
        if part.endswith((" ", ".")):
            return "path components must not end with a space or dot"
        if unicodedata.normalize("NFC", part).casefold() == ".git":
            return "Git control-directory path components are not allowed"
        stem = part.split(".", 1)[0].casefold()
        if stem in {"con", "prn", "aux", "nul", "conin$", "conout$"} or re.fullmatch(
            r"(?:com|lpt)(?:[1-9]|[¹²³])", stem
        ):
            return "platform-reserved path components are not allowed"
    return None


def _display_error_value(value: object) -> str:
    rendered = repr(value)
    if len(rendered) <= MAX_ERROR_VALUE_CHARS:
        return rendered
    return rendered[: MAX_ERROR_VALUE_CHARS - 3] + "..."


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


def _validate_source_path(value: object, label: str, errors: list[str]) -> None:
    if not isinstance(value, str):
        errors.append(f"{label} must be a normalized POSIX relative path")
        return
    path_error = relative_path_error(value)
    if path_error:
        errors.append(
            f"{label} is an unsafe path {_display_error_value(value)}: {path_error}"
        )


def _required_local_file(
    repository_root: Path,
    value: object,
    label: str,
    errors: list[str],
) -> Path | None:
    if not isinstance(value, str):
        errors.append(f"{label} must be a normalized POSIX relative path")
        return None

    path_error = relative_path_error(value)
    if path_error:
        errors.append(
            f"{label} is an unsafe path {_display_error_value(value)}: {path_error}"
        )
        return None

    candidate = repository_root.joinpath(*PurePosixPath(value).parts)
    cursor = repository_root
    for part in PurePosixPath(value).parts:
        cursor /= part
        try:
            status = cursor.lstat()
        except FileNotFoundError:
            break
        except OSError as exc:
            errors.append(f"cannot inspect {label} path component: {exc}")
            return None
        if stat.S_ISLNK(status.st_mode) or _is_reparse_point(cursor, status):
            errors.append(
                f"{label} must not use a symlink, junction, or reparse point: {value}"
            )
            return None

    try:
        candidate.resolve(strict=False).relative_to(repository_root)
    except (OSError, ValueError) as exc:
        errors.append(f"{label} must remain under the repository root: {value} ({exc})")
        return None

    try:
        candidate_status = candidate.lstat()
    except OSError:
        candidate_status = None
    if candidate_status is None or not stat.S_ISREG(candidate_status.st_mode):
        errors.append(f"{label} does not exist as a regular file: {value}")
        return None
    return candidate


def _read_bounded_worktree_file(
    path: Path,
    label: str,
    errors: list[str],
) -> bytes | None:
    try:
        status = path.lstat()
        if (
            not stat.S_ISREG(status.st_mode)
            or stat.S_ISLNK(status.st_mode)
            or _is_reparse_point(path, status)
        ):
            errors.append(f"{label} must remain an ordinary regular file")
            return None
        size = status.st_size
        if size < 0 or size > MAX_PROVENANCE_BLOB_BYTES:
            errors.append(
                f"{label} exceeds the {MAX_PROVENANCE_BLOB_BYTES}-byte input limit"
            )
            return None
        with path.open("rb") as stream:
            opened_status = os.fstat(stream.fileno())
            if not stat.S_ISREG(opened_status.st_mode):
                errors.append(f"{label} changed to a non-regular file while reading")
                return None
            content = stream.read(MAX_PROVENANCE_BLOB_BYTES + 1)
    except OSError as exc:
        errors.append(f"Cannot read {label}: {exc}")
        return None
    if len(content) > MAX_PROVENANCE_BLOB_BYTES:
        errors.append(
            f"{label} exceeds the {MAX_PROVENANCE_BLOB_BYTES}-byte input limit"
        )
        return None
    if len(content) != size:
        errors.append(f"{label} changed size while reading")
        return None
    return content


def _validate_content_hash(
    content: bytes | None,
    declared_hash: object,
    label: str,
    errors: list[str],
) -> None:
    if not _validate_lower_hex(declared_hash, SHA256, f"{label} SHA-256", errors):
        return
    if content is None:
        return
    actual = hashlib.sha256(content).hexdigest()
    if actual != declared_hash:
        errors.append(
            f"SHA-256 mismatch for {label}: expected {declared_hash}, got {actual}"
        )


def _run_git(
    repository_root: Path,
    arguments: list[str],
) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
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
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=GIT_TIMEOUT_SECONDS,
        env=_git_environment(),
    )


def _git_environment() -> dict[str, str]:
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
        }
    )
    return environment


def _git_executable(repository_root: Path) -> str:
    if not GIT_EXECUTABLE_CANDIDATE:
        raise OSError("cannot locate a Git executable on the trusted runtime PATH")
    try:
        executable = Path(GIT_EXECUTABLE_CANDIDATE).resolve(strict=True)
        status = executable.lstat()
    except OSError as exc:
        raise OSError(f"cannot resolve the Git executable: {exc}") from exc
    if not stat.S_ISREG(status.st_mode) or _is_reparse_point(executable, status):
        raise OSError("Git executable must resolve to an ordinary regular file")
    try:
        executable.relative_to(repository_root.resolve())
    except ValueError:
        pass
    else:
        raise OSError("Git executable must not be contained in the repository")
    return str(executable)


def _stream_git_nul_records(
    repository_root: Path,
    arguments: list[str],
    *,
    label: str,
    max_records: int,
    max_record_bytes: int,
    on_record: Callable[[bytes], None],
    errors: list[str],
) -> bool:
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
            env=_git_environment(),
        )
    except OSError as exc:
        errors.append(f"cannot start bounded {label}: {exc}")
        return False
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
    maximum_output_bytes = max_records * (max_record_bytes + 1)
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
                if len(record) > max_record_bytes:
                    errors.append(f"bounded {label} emitted an oversized record")
                    return False
                if not record:
                    continue
                record_count += 1
                if record_count > max_records:
                    errors.append(f"bounded {label} exceeded {max_records} records")
                    return False
                on_record(record)
            if len(buffer) > max_record_bytes:
                errors.append(
                    f"bounded {label} emitted an unterminated oversized record"
                )
                return False
            if total_bytes > maximum_output_bytes:
                errors.append(f"bounded {label} exceeded its output byte limit")
                return False
        if buffer:
            errors.append(f"bounded {label} ended with a partial record")
            return False
        return_code = process.wait()
        if timed_out.is_set():
            errors.append(f"bounded {label} timed out")
            return False
        if return_code != 0:
            errors.append(f"bounded {label} failed with exit {return_code}")
            return False
        return True
    finally:
        timer.cancel()
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()


def _git_error(result: subprocess.CompletedProcess[bytes]) -> str:
    return result.stderr.decode("utf-8", errors="replace").strip() or (
        f"git exited with status {result.returncode}"
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
    errors: list[str],
) -> bytes | None:
    if GIT_OBJECT_ID.fullmatch(oid) is None:
        errors.append(f"{label} has an invalid SHA-1 Git object ID")
        return None
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
            env=_git_environment(),
        )
    except OSError as exc:
        errors.append(f"cannot start bounded Git object read for {label}: {exc}")
        return None
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
            errors.append(f"bounded Git object header for {label} is malformed")
            return None
        fields = header[:-1].split()
        if len(fields) != 3:
            errors.append(f"{label} does not exist as a local Git object: {oid}")
            return None
        try:
            observed_oid = fields[0].decode("ascii", errors="strict")
            observed_type = fields[1].decode("ascii", errors="strict")
            size = int(fields[2].decode("ascii", errors="strict"))
        except (UnicodeError, ValueError) as exc:
            errors.append(f"cannot parse Git object header for {label}: {exc}")
            return None
        if observed_oid != oid or observed_type != object_type:
            errors.append(
                f"{label} must be an exact Git {object_type}; observed "
                f"{observed_oid} {observed_type}"
            )
            return None
        if size < 0 or size > maximum_size:
            errors.append(
                f"{label} exceeds the {maximum_size}-byte Git object limit"
            )
            return None
        chunks: list[bytes] = []
        remaining = size
        while remaining:
            chunk = process.stdout.read(min(remaining, GIT_STREAM_CHUNK_BYTES))
            if not chunk:
                errors.append(f"bounded Git object read for {label} ended early")
                return None
            chunks.append(chunk)
            remaining -= len(chunk)
        if process.stdout.read(1) != b"\n":
            errors.append(
                f"bounded Git object read for {label} emitted undeclared bytes"
            )
            return None
        if process.stdout.read(1):
            errors.append(f"bounded Git object read for {label} emitted trailing bytes")
            return None
        return_code = process.wait()
        if timed_out.is_set():
            errors.append(f"bounded Git object read timed out for {label}")
            return None
        if return_code != 0:
            errors.append(
                f"bounded Git object read for {label} failed with exit {return_code}"
            )
            return None
        content = b"".join(chunks)
        recomputed_oid = _git_object_sha1(object_type, content)
        if recomputed_oid != oid:
            errors.append(
                f"Git object identity mismatch for {label}: expected {oid}, "
                f"recomputed {recomputed_oid}"
            )
            return None
        return content
    except (BrokenPipeError, OSError) as exc:
        errors.append(f"cannot read bounded Git object for {label}: {exc}")
        return None
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
    errors: list[str],
) -> tuple[list[tuple[str, str, str, str]], int] | None:
    content = _read_verified_git_object(
        repository_root,
        tree_oid,
        "tree",
        MAX_GIT_TREE_OBJECT_BYTES,
        label,
        errors,
    )
    if content is None:
        return None
    entries: list[tuple[str, str, str, str]] = []
    offset = 0
    while offset < len(content):
        space = content.find(b" ", offset)
        nul = content.find(b"\0", space + 1 if space >= 0 else offset)
        if space <= offset or nul <= space + 1 or nul + 21 > len(content):
            errors.append(f"{label} contains a malformed raw Git tree entry")
            return None
        mode_bytes = content[offset:space]
        name_bytes = content[space + 1 : nul]
        oid_bytes = content[nul + 1 : nul + 21]
        offset = nul + 21
        try:
            raw_mode = mode_bytes.decode("ascii", errors="strict")
            name = name_bytes.decode("utf-8", errors="strict")
        except UnicodeError as exc:
            errors.append(f"{label} contains an undecodable tree entry: {exc}")
            return None
        if not name or name in (".", "..") or "/" in name:
            errors.append(f"{label} contains an invalid Git tree entry name")
            return None
        oid = oid_bytes.hex()
        if raw_mode in ("40000", "040000"):
            mode, observed_type = "040000", "tree"
        elif raw_mode in GIT_REGULAR_FILE_MODES:
            mode, observed_type = raw_mode, "blob"
        else:
            mode, observed_type = raw_mode, "unsupported"
        entries.append((name, mode, observed_type, oid))
    return entries, len(content)


def _verified_commit_tree_oid(
    repository_root: Path,
    commit: str,
    label: str,
    errors: list[str],
) -> str | None:
    content = _read_verified_git_object(
        repository_root,
        commit,
        "commit",
        MAX_GIT_COMMIT_OBJECT_BYTES,
        f"{label} {commit}",
        errors,
    )
    if content is None:
        return None
    first_line = content.split(b"\n", 1)[0]
    if not first_line.startswith(b"tree "):
        errors.append(f"commit object for {label} {commit} has no initial tree header")
        return None
    try:
        tree_oid = first_line[5:].decode("ascii", errors="strict")
    except UnicodeError as exc:
        errors.append(f"cannot decode tree header for {label} {commit}: {exc}")
        return None
    if GIT_OBJECT_ID.fullmatch(tree_oid) is None:
        errors.append(f"commit object for {label} {commit} has an invalid tree header")
        return None
    return tree_oid


def _validate_git_repository(repository_root: Path, errors: list[str]) -> bool:
    try:
        result = _run_git(repository_root, ["rev-parse", "--show-toplevel"])
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot inspect provenance Git repository: {exc}")
        return False
    if result.returncode != 0:
        errors.append(
            "provenance root must be a Git worktree: " + _git_error(result)
        )
        return False
    try:
        discovered = Path(
            result.stdout.decode("utf-8", errors="strict").strip()
        ).resolve()
    except (OSError, UnicodeError) as exc:
        errors.append(f"cannot resolve provenance Git worktree root: {exc}")
        return False
    if os.path.normcase(str(discovered)) != os.path.normcase(str(repository_root)):
        errors.append(
            "provenance repository root does not match the Git worktree root: "
            f"{discovered}"
        )
        return False

    try:
        shallow_result = _run_git(
            repository_root, ["rev-parse", "--is-shallow-repository"]
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot inspect provenance Git history depth: {exc}")
        return False
    if shallow_result.returncode != 0:
        errors.append(
            "cannot inspect provenance Git history depth: " + _git_error(shallow_result)
        )
        return False
    if shallow_result.stdout.strip() == b"true":
        errors.append(
            "provenance validation requires a complete, non-shallow Git history"
        )
        return False

    try:
        graft_result = _run_git(
            repository_root, ["rev-parse", "--git-path", "info/grafts"]
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot inspect provenance Git graft metadata: {exc}")
        return False
    if graft_result.returncode != 0:
        errors.append(
            "cannot inspect provenance Git graft metadata: " + _git_error(graft_result)
        )
        return False
    try:
        graft_value = graft_result.stdout.decode("utf-8", errors="strict").strip()
        graft_path = Path(graft_value)
        if not graft_path.is_absolute():
            graft_path = repository_root / graft_path
        graft_path.lstat()
    except FileNotFoundError:
        pass
    except (OSError, UnicodeError) as exc:
        errors.append(f"cannot inspect provenance Git graft metadata: {exc}")
        return False
    else:
        errors.append(
            "provenance validation forbids legacy Git info/grafts metadata because "
            "it can rewrite parent and ancestry proofs"
        )
        return False
    return True


def _git_commit_exists(
    repository_root: Path,
    commit: str,
    label: str,
    errors: list[str],
) -> bool:
    try:
        result = _run_git(
            repository_root,
            ["cat-file", "-t", commit],
        )
    except (OSError, subprocess.SubprocessError) as exc:
        errors.append(f"cannot verify {label} {commit}: {exc}")
        return False
    if result.returncode != 0:
        errors.append(f"{label} does not exist as a local Git commit: {commit}")
        return False
    try:
        object_type = result.stdout.decode("ascii", errors="strict").strip()
    except UnicodeError as exc:
        errors.append(f"cannot decode exact object type for {label} {commit}: {exc}")
        return False
    if object_type != "commit":
        errors.append(
            f"{label} does not exist as a local Git commit: {commit}; exact object "
            f"type is {_display_error_value(object_type)}"
        )
        return False
    return (
        _read_verified_git_object(
            repository_root,
            commit,
            "commit",
            MAX_GIT_COMMIT_OBJECT_BYTES,
            f"{label} {commit}",
            errors,
        )
        is not None
    )


def _parse_git_commit_parents(
    content: bytes,
    commit: str,
    label: str,
    errors: list[str],
) -> list[str] | None:
    header, separator, _ = content.partition(b"\n\n")
    if not separator:
        errors.append(f"commit object for {label} {commit} has no message separator")
        return None
    lines = header.split(b"\n")
    if not lines or not lines[0].startswith(b"tree "):
        errors.append(f"commit object for {label} {commit} has no initial tree header")
        return None
    try:
        tree_oid = lines[0][5:].decode("ascii", errors="strict")
    except UnicodeError as exc:
        errors.append(f"cannot decode tree header for {label} {commit}: {exc}")
        return None
    if COMMIT.fullmatch(tree_oid) is None:
        errors.append(f"commit object for {label} {commit} has an invalid tree header")
        return None

    parents: list[str] = []
    index = 1
    while index < len(lines) and lines[index].startswith(b"parent "):
        try:
            parent = lines[index][7:].decode("ascii", errors="strict")
        except UnicodeError as exc:
            errors.append(f"cannot decode parents of {label} {commit}: {exc}")
            return None
        if COMMIT.fullmatch(parent) is None:
            errors.append(f"cannot parse parents of {label} {commit}")
            return None
        parents.append(parent)
        if len(parents) > MAX_GIT_COMMIT_PARENTS:
            errors.append(
                f"commit object for {label} {commit} exceeds "
                f"{MAX_GIT_COMMIT_PARENTS} parents"
            )
            return None
        index += 1

    if not any(line.startswith(b"author ") for line in lines[index:]):
        errors.append(f"commit object for {label} {commit} has no author header")
        return None
    if not any(line.startswith(b"committer ") for line in lines[index:]):
        errors.append(f"commit object for {label} {commit} has no committer header")
        return None
    return parents


def _git_commit_parents(
    repository_root: Path,
    commit: str,
    label: str,
    errors: list[str],
) -> list[str] | None:
    if not _git_commit_exists(repository_root, commit, label, errors):
        return None
    content = _read_verified_git_object(
        repository_root,
        commit,
        "commit",
        MAX_GIT_COMMIT_OBJECT_BYTES,
        f"{label} {commit}",
        errors,
    )
    if content is None:
        return None
    parents = _parse_git_commit_parents(
        content, commit, label, errors
    )
    if parents is None:
        return None
    for parent in parents:
        if not _git_commit_exists(
            repository_root,
            parent,
            f"parent of {label} {commit}",
            errors,
        ):
            return None
    return parents


def _git_tree_entry(
    repository_root: Path,
    commit: str,
    path: str,
    label: str,
    errors: list[str],
) -> tuple[bool, GitTreeEntry | None]:
    path_error = relative_path_error(path)
    if path_error:
        errors.append(
            f"cannot inspect {label} at Git commit {commit}: unsafe path "
            f"{_display_error_value(path)}: {path_error}"
        )
        return False, None
    tree_oid = _verified_commit_tree_oid(
        repository_root, commit, label, errors
    )
    if tree_oid is None:
        return False, None
    aggregate_tree_bytes = 0
    parts = PurePosixPath(path).parts
    for index, part in enumerate(parts):
        parsed = _parse_verified_git_tree(
            repository_root,
            tree_oid,
            f"tree for {label} at Git commit {commit}",
            errors,
        )
        if parsed is None:
            return False, None
        entries, observed_bytes = parsed
        aggregate_tree_bytes += observed_bytes
        if aggregate_tree_bytes > MAX_GIT_TREE_LOOKUP_BYTES:
            errors.append(
                f"tree lookup for {label} at Git commit {commit} exceeds "
                f"{MAX_GIT_TREE_LOOKUP_BYTES} bytes"
            )
            return False, None
        matches = [entry for entry in entries if entry[0] == part]
        if not matches:
            return True, None
        if len(matches) != 1:
            errors.append(
                f"cannot parse {label} at Git commit {commit}: expected exactly "
                "one tree entry"
            )
            return False, None
        _, mode, object_type, object_id = matches[0]
        if index + 1 == len(parts):
            return True, (mode, object_type, object_id)
        if mode != "040000" or object_type != "tree":
            return True, None
        tree_oid = object_id
    return True, None


def _validate_git_tree_snapshot(
    repository_root: Path,
    commit: str,
    path: str,
    declared_mode: object,
    declared_object_type: object,
    declared_blob_oid: object,
    label: str,
    errors: list[str],
) -> GitTreeEntry | None:
    entry_valid, entry = _git_tree_entry(
        repository_root, commit, path, label, errors
    )
    if not entry_valid:
        return None
    if entry is None:
        errors.append(f"{label} is missing from Git commit {commit}")
        return None

    mode, object_type, object_id = entry
    if mode not in GIT_REGULAR_FILE_MODES or object_type != "blob":
        errors.append(
            f"{label} must be a regular Git blob with mode 100644 or 100755; "
            f"observed mode={mode} type={object_type}"
        )
    if isinstance(declared_mode, str) and mode != declared_mode:
        errors.append(
            f"Git mode mismatch for {label} at commit {commit}: expected "
            f"{declared_mode}, observed {mode}"
        )
    if isinstance(declared_object_type, str) and object_type != declared_object_type:
        errors.append(
            f"Git object type mismatch for {label} at commit {commit}: expected "
            f"{declared_object_type}, observed {object_type}"
        )
    if isinstance(declared_blob_oid, str) and object_id != declared_blob_oid:
        errors.append(
            f"Git blob OID mismatch for {label} at commit {commit}: expected "
            f"{declared_blob_oid}, observed {object_id}"
        )
    return entry


def _commit_parents_cached(
    repository_root: Path,
    commit: str,
    label: str,
    parent_cache: dict[str, list[str] | None],
    errors: list[str],
) -> list[str] | None:
    if commit not in parent_cache:
        parent_cache[commit] = _git_commit_parents(
            repository_root, commit, label, errors
        )
    return parent_cache[commit]


def _validate_path_changed_from_first_parent(
    repository_root: Path,
    commit: str,
    path: str,
    current_entry: GitTreeEntry | None,
    label: str,
    parent_cache: dict[str, list[str] | None],
    errors: list[str],
) -> None:
    if current_entry is None:
        return
    parents = _commit_parents_cached(
        repository_root, commit, label, parent_cache, errors
    )
    if parents is None:
        return
    if not parents:
        # A root commit is compared to Git's conceptual empty parent tree.
        return

    first_parent = parents[0]
    parent_valid, parent_entry = _git_tree_entry(
        repository_root,
        first_parent,
        path,
        f"{label} first-parent snapshot",
        errors,
    )
    if not parent_valid:
        return
    if parent_entry == current_entry:
        errors.append(
            f"{label} {commit} does not add or change {path} relative to its "
            f"first parent {first_parent}"
        )


def _validate_path_changed_from_every_parent(
    repository_root: Path,
    commit: str,
    path: str,
    current_entry: GitTreeEntry | None,
    label: str,
    parent_cache: dict[str, list[str] | None],
    errors: list[str],
) -> None:
    if current_entry is None:
        return
    parents = _commit_parents_cached(
        repository_root, commit, label, parent_cache, errors
    )
    if parents is None or not parents:
        return

    for parent in parents:
        parent_valid, parent_entry = _git_tree_entry(
            repository_root,
            parent,
            path,
            f"{label} parent snapshot",
            errors,
        )
        if not parent_valid:
            return
        if parent_entry == current_entry:
            errors.append(
                f"{label} {commit} does not introduce or change {path}: the path "
                f"is unchanged in parent {parent}"
            )
            return


def _validate_git_ancestor(
    repository_root: Path,
    ancestor: str,
    descendant: str,
    label: str,
    errors: list[str],
) -> None:
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
            env=_git_environment(),
        )
    except OSError as exc:
        errors.append(f"cannot start bounded ancestry validation for {label}: {exc}")
        return
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
    pending = [descendant]
    queued = {descendant}
    visited: set[str] = set()
    found = descendant == ancestor
    aggregate_bytes = 0
    failure: str | None = None
    try:
        while pending:
            current = pending.pop()
            if current in visited:
                continue
            visited.add(current)
            if len(visited) > MAX_GIT_ANCESTRY_COMMITS:
                failure = (
                    f"cannot verify {label}: ancestry traversal exceeds "
                    f"{MAX_GIT_ANCESTRY_COMMITS} commits"
                )
                break
            try:
                process.stdin.write(current.encode("ascii") + b"\n")
                process.stdin.flush()
            except (BrokenPipeError, OSError, UnicodeError) as exc:
                failure = f"cannot query bounded ancestry object for {label}: {exc}"
                break

            header = process.stdout.readline(MAX_GIT_BATCH_HEADER_BYTES + 1)
            if (
                not header
                or len(header) > MAX_GIT_BATCH_HEADER_BYTES
                or not header.endswith(b"\n")
            ):
                failure = f"bounded ancestry object header for {label} is malformed"
                break
            fields = header[:-1].split()
            if len(fields) != 3:
                failure = f"bounded ancestry object for {label} is missing or malformed"
                break
            try:
                observed_oid = fields[0].decode("ascii", errors="strict")
                object_type = fields[1].decode("ascii", errors="strict")
                size = int(fields[2].decode("ascii", errors="strict"))
            except (UnicodeError, ValueError) as exc:
                failure = f"cannot parse bounded ancestry object for {label}: {exc}"
                break
            if observed_oid != current or object_type != "commit":
                failure = (
                    f"bounded ancestry object for {label} must be exact commit "
                    f"{current}; observed {observed_oid} {object_type}"
                )
                break
            if size < 0 or size > MAX_GIT_COMMIT_OBJECT_BYTES:
                failure = (
                    f"commit object for {label} {current} exceeds the "
                    f"{MAX_GIT_COMMIT_OBJECT_BYTES}-byte history limit"
                )
                break
            aggregate_bytes += size
            if aggregate_bytes > MAX_GIT_ANCESTRY_BYTES:
                failure = (
                    f"cannot verify {label}: ancestry objects exceed "
                    f"{MAX_GIT_ANCESTRY_BYTES} aggregate bytes"
                )
                break

            chunks: list[bytes] = []
            remaining = size
            while remaining:
                chunk = process.stdout.read(min(remaining, GIT_STREAM_CHUNK_BYTES))
                if not chunk:
                    failure = f"bounded ancestry object for {label} ended early"
                    break
                chunks.append(chunk)
                remaining -= len(chunk)
            if failure is not None:
                break
            if process.stdout.read(1) != b"\n":
                failure = f"bounded ancestry object for {label} has no terminator"
                break
            content = b"".join(chunks)
            recomputed_oid = _git_object_sha1("commit", content)
            if recomputed_oid != current:
                failure = (
                    f"Git object identity mismatch for {label} ancestry commit "
                    f"{current}: recomputed {recomputed_oid}"
                )
                break
            parent_errors: list[str] = []
            parents = _parse_git_commit_parents(
                content,
                current,
                f"{label} ancestry commit",
                parent_errors,
            )
            if parents is None:
                errors.extend(parent_errors)
                failure = ""
                break
            if current == ancestor:
                found = True
                break
            for parent in parents:
                if parent in queued:
                    continue
                queued.add(parent)
                if len(queued) > MAX_GIT_ANCESTRY_COMMITS:
                    failure = (
                        f"cannot verify {label}: ancestry traversal exceeds "
                        f"{MAX_GIT_ANCESTRY_COMMITS} commits"
                    )
                    break
                pending.append(parent)
            if failure is not None:
                break

        try:
            process.stdin.close()
        except OSError:
            pass
        if failure is not None and process.poll() is None:
            process.kill()
        return_code = process.wait()
        if timed_out.is_set():
            errors.append(f"bounded ancestry validation timed out for {label}")
            return
        if failure is not None:
            if failure:
                errors.append(failure)
            return
        if return_code != 0:
            errors.append(
                f"bounded ancestry validation failed for {label} with exit "
                f"{return_code}"
            )
            return
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
    if not found:
        errors.append(
            f"{label} has an invalid ancestry: {ancestor} is not an ancestor of "
            f"{descendant}"
        )


def _git_blob(
    repository_root: Path,
    commit: str,
    path: str,
    label: str,
    errors: list[str],
) -> bytes | None:
    entry_valid, entry = _git_tree_entry(
        repository_root, commit, path, label, errors
    )
    if not entry_valid:
        return None
    if entry is None:
        errors.append(f"{label} is missing from Git commit {commit}")
        return None
    mode, object_type, oid = entry
    if mode not in GIT_REGULAR_FILE_MODES or object_type != "blob":
        errors.append(
            f"{label} must be a regular Git blob with mode 100644 or 100755; "
            f"observed mode={mode} type={object_type}"
        )
        return None
    return _read_verified_git_object(
        repository_root,
        oid,
        "blob",
        MAX_PROVENANCE_BLOB_BYTES,
        f"{label} at Git commit {commit}",
        errors,
    )


def _git_text_attributes(
    repository_root: Path,
    commit: str,
    path: str,
    errors: list[str],
) -> tuple[str | None, str | None]:
    if "/" in path:
        errors.append(
            f"worktree materialization policy is unsupported for non-root path {path}"
        )
        return None, None
    attributes_blob = _git_blob(
        repository_root,
        commit,
        ".gitattributes",
        f"selected .gitattributes policy for {path}",
        errors,
    )
    if attributes_blob is None:
        return None, None
    try:
        attribute_text = attributes_blob.decode("utf-8", errors="strict")
    except UnicodeError as exc:
        errors.append(
            f"cannot decode selected .gitattributes for {path} at {commit}: {exc}"
        )
        return None, None

    text_attribute: str | None = None
    eol_attribute: str | None = None
    for line_number, line in enumerate(attribute_text.splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "\\" in stripped or '"' in stripped or "'" in stripped:
            errors.append(
                "selected .gitattributes contains unsupported quoted or escaped "
                f"syntax at line {line_number}"
            )
            return None, None
        fields = stripped.split()
        pattern = fields[0]
        if pattern.startswith("[attr]"):
            errors.append(
                "selected .gitattributes custom macros are unsupported for "
                "commit-only materialization validation"
            )
            return None, None
        if pattern.startswith("!"):
            errors.append(
                f"selected .gitattributes has a forbidden negative pattern at "
                f"line {line_number}"
            )
            return None, None
        candidate = path if "/" in pattern else PurePosixPath(path).name
        if pattern.endswith("/") or not fnmatch.fnmatchcase(candidate, pattern):
            continue
        for attribute in fields[1:]:
            if attribute == "binary":
                text_attribute = "unset"
            elif attribute == "text":
                text_attribute = "set"
            elif attribute == "-text":
                text_attribute = "unset"
            elif attribute == "!text":
                text_attribute = "unspecified"
            elif attribute.startswith("text="):
                text_attribute = attribute.split("=", 1)[1]
            elif attribute == "eol":
                eol_attribute = "set"
            elif attribute == "-eol":
                eol_attribute = "unset"
            elif attribute == "!eol":
                eol_attribute = "unspecified"
            elif attribute.startswith("eol="):
                eol_attribute = attribute.split("=", 1)[1]
            else:
                errors.append(
                    "selected .gitattributes has an unsupported matched attribute "
                    f"for {path} at line {line_number}: "
                    f"{_display_error_value(attribute)}"
                )
                return None, None
    return text_attribute, eol_attribute


def _git_blob_hash_candidates(
    repository_root: Path,
    commit: str,
    path: str,
    blob: bytes,
    errors: list[str],
) -> dict[str, str]:
    candidates = {"raw Git blob": hashlib.sha256(blob).hexdigest()}
    text_attribute, eol_attribute = _git_text_attributes(
        repository_root, commit, path, errors
    )
    if text_attribute in ("set", "auto") and eol_attribute in ("lf", "crlf"):
        canonical_lf = blob.replace(b"\r\n", b"\n")
        materialized = (
            canonical_lf
            if eol_attribute == "lf"
            else canonical_lf.replace(b"\n", b"\r\n")
        )
        candidates[f"declared eol={eol_attribute} materialization"] = hashlib.sha256(
            materialized
        ).hexdigest()
    return candidates


def _validate_git_raw_blob_hash(
    repository_root: Path,
    commit: str,
    path: str,
    declared_hash: object,
    label: str,
    errors: list[str],
) -> None:
    if not isinstance(declared_hash, str) or SHA256.fullmatch(declared_hash) is None:
        return
    blob = _git_blob(repository_root, commit, path, label, errors)
    if blob is None:
        return
    observed_hash = hashlib.sha256(blob).hexdigest()
    if declared_hash != observed_hash:
        errors.append(
            f"raw Git blob SHA-256 mismatch for {label} at Git commit {commit}: "
            f"expected {declared_hash}, observed {observed_hash}"
        )


def _validate_git_materialized_hash(
    repository_root: Path,
    commit: str,
    path: str,
    declared_hash: object,
    label: str,
    errors: list[str],
) -> None:
    if not isinstance(declared_hash, str) or SHA256.fullmatch(declared_hash) is None:
        return
    blob = _git_blob(repository_root, commit, path, label, errors)
    if blob is None:
        return
    candidates = _git_blob_hash_candidates(
        repository_root, commit, path, blob, errors
    )
    raw_hash = candidates["raw Git blob"]
    materialized_candidates = {
        description: digest
        for description, digest in candidates.items()
        if description != "raw Git blob"
    }
    if declared_hash == raw_hash or declared_hash not in materialized_candidates.values():
        observed = ", ".join(
            f"{description}={digest}"
            for description, digest in sorted(candidates.items())
        )
        errors.append(
            f"worktree materialized SHA-256 for {label} at Git commit {commit} "
            f"must match a non-raw declared-EOL materialization: expected "
            f"{declared_hash}; {observed}"
        )


def _load_json(path: Path, errors: list[str]) -> dict[str, Any] | None:
    content = _read_bounded_worktree_file(
        path, f"bootstrap provenance manifest {path}", errors
    )
    if content is None:
        return None
    return _load_json_content(content, str(path), errors)


def _load_json_content(
    content: bytes,
    label: str,
    errors: list[str],
) -> dict[str, Any] | None:
    try:
        document = json.loads(
            content.decode("utf-8", errors="strict"),
            object_pairs_hook=_reject_duplicate_json_keys,
            parse_constant=_reject_nonfinite_json,
        )
    except (
        UnicodeError,
        json.JSONDecodeError,
        DuplicateJsonKeyError,
        RecursionError,
        ValueError,
    ) as exc:
        errors.append(f"Cannot read bootstrap provenance manifest {label}: {exc}")
        return None
    if not isinstance(document, dict):
        errors.append("Bootstrap provenance manifest must contain a JSON object")
        return None
    bounds_error = _json_bounds_error(document)
    if bounds_error is not None:
        errors.append(f"Bootstrap provenance manifest {label} {bounds_error}")
        return None
    return document


def _json_bounds_error(value: object) -> str | None:
    stack: list[tuple[object, int]] = [(value, 1)]
    nodes = 0
    while stack:
        current, depth = stack.pop()
        nodes += 1
        if nodes > MAX_JSON_NODES:
            return f"exceeds {MAX_JSON_NODES} JSON values"
        if depth > MAX_JSON_DEPTH:
            return f"exceeds JSON depth {MAX_JSON_DEPTH}"
        if isinstance(current, float) and not math.isfinite(current):
            return "contains a non-finite JSON number"
        if isinstance(current, str):
            try:
                current.encode("utf-8", errors="strict")
            except UnicodeEncodeError:
                return "contains a string that is not valid UTF-8"
        if isinstance(current, dict):
            for key in current:
                try:
                    key.encode("utf-8", errors="strict")
                except UnicodeEncodeError:
                    return "contains an object key that is not valid UTF-8"
            stack.extend((item, depth + 1) for item in current.values())
        elif isinstance(current, list):
            stack.extend((item, depth + 1) for item in current)
    return None


class _RepositoryContentView:
    """Read validation inputs from one explicit content domain."""

    uses_raw_git_blobs = False

    def required_bytes(
        self,
        value: object,
        label: str,
        errors: list[str],
    ) -> bytes | None:
        raise NotImplementedError

    def resource_files(self, errors: list[str]) -> set[str]:
        raise NotImplementedError


class _WorktreeContentView(_RepositoryContentView):
    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root

    def required_bytes(
        self,
        value: object,
        label: str,
        errors: list[str],
    ) -> bytes | None:
        path = _required_local_file(self.repository_root, value, label, errors)
        if path is None:
            return None
        return _read_bounded_worktree_file(path, label, errors)

    def resource_files(self, errors: list[str]) -> set[str]:
        return _repository_resource_files(self.repository_root, errors)


class _SelectedCommitContentView(_RepositoryContentView):
    uses_raw_git_blobs = True

    def __init__(self, repository_root: Path, selected_commit: str) -> None:
        self.repository_root = repository_root
        self.selected_commit = selected_commit

    def required_bytes(
        self,
        value: object,
        label: str,
        errors: list[str],
    ) -> bytes | None:
        if not isinstance(value, str):
            errors.append(f"{label} must be a normalized POSIX relative path")
            return None
        path_error = relative_path_error(value)
        if path_error:
            errors.append(
                f"{label} is an unsafe path {_display_error_value(value)}: "
                f"{path_error}"
            )
            return None
        entry_valid, entry = _git_tree_entry(
            self.repository_root,
            self.selected_commit,
            value,
            label,
            errors,
        )
        if not entry_valid:
            return None
        if entry is None:
            errors.append(
                f"{label} is missing from selected Git commit {self.selected_commit}: "
                f"{value}"
            )
            return None
        mode, object_type, object_id = entry
        if mode not in GIT_REGULAR_FILE_MODES or object_type != "blob":
            errors.append(
                f"{label} must be a regular Git blob with mode 100644 or 100755; "
                f"observed mode={mode} type={object_type}"
            )
            return None
        return _read_verified_git_object(
            self.repository_root,
            object_id,
            "blob",
            MAX_PROVENANCE_BLOB_BYTES,
            f"{label} from selected Git commit {self.selected_commit}",
            errors,
        )

    def resource_files(self, errors: list[str]) -> set[str]:
        resources: set[str] = set()
        root_tree = _verified_commit_tree_oid(
            self.repository_root,
            self.selected_commit,
            "selected comparison commit",
            errors,
        )
        if root_tree is None:
            return resources
        resource_roots = tuple(prefix.rstrip("/") for prefix in RESOURCE_ROOTS)
        pending: list[tuple[str, str]] = [(root_tree, "")]
        directories = 0
        entries_seen = 0
        aggregate_tree_bytes = 0
        exact_paths: set[str] = set()
        while pending:
            tree_oid, prefix = pending.pop()
            parsed = _parse_verified_git_tree(
                self.repository_root,
                tree_oid,
                f"selected-commit resource tree at {prefix or '/'}",
                errors,
            )
            if parsed is None:
                return resources
            entries, tree_bytes = parsed
            aggregate_tree_bytes += tree_bytes
            if aggregate_tree_bytes > MAX_SELECTED_RESOURCE_TREE_BYTES:
                errors.append(
                    "selected-commit resource traversal exceeds "
                    f"{MAX_SELECTED_RESOURCE_TREE_BYTES} tree bytes"
                )
                return resources
            for name, mode, object_type, oid in entries:
                path = f"{prefix}/{name}" if prefix else name
                is_ancestor_or_member = any(
                    root == path
                    or root.startswith(path + "/")
                    or path.startswith(root + "/")
                    for root in resource_roots
                )
                if not is_ancestor_or_member:
                    continue
                entries_seen += 1
                if entries_seen > (
                    MAX_SELECTED_RESOURCE_FILES + MAX_SELECTED_RESOURCE_DIRECTORIES
                ):
                    errors.append(
                        "selected-commit resource traversal exceeds its total "
                        "entry limit"
                    )
                    return resources
                if path in exact_paths:
                    errors.append(
                        "selected-commit resource tree contains a duplicate exact "
                        f"path: {path}"
                    )
                    return resources
                exact_paths.add(path)
                path_error = relative_path_error(path)
                if path_error:
                    errors.append(
                        "selected-commit resource path is unsafe: "
                        f"{_display_error_value(path)}: {path_error}"
                    )
                    return resources
                if object_type == "tree":
                    if mode != "040000":
                        errors.append(
                            f"selected-commit resource tree is invalid: {path}"
                        )
                        return resources
                    directories += 1
                    if directories > MAX_SELECTED_RESOURCE_DIRECTORIES:
                        errors.append(
                            "selected-commit resource traversal exceeds "
                            f"{MAX_SELECTED_RESOURCE_DIRECTORIES} directories"
                        )
                        return resources
                    pending.append((oid, path))
                    continue
                if not path.startswith(RESOURCE_ROOTS) or path.startswith(
                    EXCLUDED_RESOURCE_PREFIXES
                ):
                    continue
                if mode not in GIT_REGULAR_FILE_MODES or object_type != "blob":
                    errors.append(
                        f"selected-commit resource must be a regular Git blob: "
                        f"{path} ({mode} {object_type})"
                    )
                    return resources
                resources.add(path)
                if len(resources) > MAX_SELECTED_RESOURCE_FILES:
                    errors.append(
                        "selected-commit resource traversal exceeds "
                        f"{MAX_SELECTED_RESOURCE_FILES} files"
                    )
                    return resources
        return resources


def _validate_components(
    content_view: _RepositoryContentView,
    value: object,
    errors: list[str],
) -> dict[str, dict[str, Any]]:
    if not isinstance(value, list):
        errors.append("components must be a JSON array")
        return {}
    expected_count = len(EXPECTED_COMPONENTS)
    if len(value) != expected_count:
        errors.append(f"components must contain exactly {expected_count} entries")
    value = value[: expected_count + 1]

    components: dict[str, dict[str, Any]] = {}
    unexpected_component_ids: set[str] = set()
    for index, component in enumerate(value):
        label = f"components[{index}]"
        if not isinstance(component, dict):
            errors.append(f"{label} must be a JSON object")
            continue

        component_id = component.get("id")
        if not _nonempty_string(component_id):
            errors.append(f"{label}.id must be a non-empty string")
            continue
        assert isinstance(component_id, str)
        if component_id not in EXPECTED_COMPONENTS:
            displayed_id = _display_error_value(component_id)
            errors.append(
                "unexpected component id: " + displayed_id
            )
            unexpected_component_ids.add(displayed_id)
            continue
        if component_id in components:
            errors.append(f"duplicate component id: {component_id}")
            continue
        components[component_id] = component

        repository = component.get("source_repository")
        if not _nonempty_string(repository):
            errors.append(f"component {component_id} source_repository is required")
        else:
            assert isinstance(repository, str)
            parsed = urlparse(repository)
            if parsed.scheme != "https" or not parsed.netloc:
                errors.append(
                    f"component {component_id} source_repository must be an HTTPS URL"
                )

        _validate_lower_hex(
            component.get("source_commit"),
            COMMIT,
            f"component {component_id} source_commit",
            errors,
        )
        if not _nonempty_string(component.get("source_artifact")):
            errors.append(f"component {component_id} source_artifact is required")
        _validate_lower_hex(
            component.get("source_sha256"),
            SHA256,
            f"component {component_id} source_sha256",
            errors,
        )
        _validate_source_path(
            component.get("license_source_path"),
            f"component {component_id} license_source_path",
            errors,
        )
        source_license_hash_valid = _validate_lower_hex(
            component.get("license_source_sha256"),
            SHA256,
            f"component {component_id} license_source_sha256",
            errors,
        )
        copy_license_hash_valid = _validate_lower_hex(
            component.get("license_copy_target_sha256"),
            SHA256,
            f"component {component_id} license_copy_target_sha256",
            errors,
        )

        expected = EXPECTED_COMPONENTS.get(component_id)
        if expected is not None:
            for field, expected_value in expected.items():
                if component.get(field) != expected_value:
                    errors.append(
                        f"component {component_id} {field} must be {expected_value}"
                    )

        copy_content = content_view.required_bytes(
            component.get("license_copy_target"),
            f"component {component_id} license copy",
            errors,
        )
        _validate_content_hash(
            copy_content,
            component.get("license_copy_target_sha256"),
            f"component {component_id} license copy",
            errors,
        )
        if (
            source_license_hash_valid
            and copy_license_hash_valid
            and component.get("license_source_sha256")
            != component.get("license_copy_target_sha256")
        ):
            errors.append(
                f"component {component_id} exact license source/copy hashes differ"
            )

    actual_ids = set(components)
    expected_ids = set(EXPECTED_COMPONENTS)
    missing = sorted(expected_ids - actual_ids)
    extra = sorted((actual_ids - expected_ids) | unexpected_component_ids)
    if missing:
        errors.append("missing required components: " + ", ".join(missing))
    if extra:
        errors.append("unexpected components: " + ", ".join(extra))
    return components


def _validate_targets(
    content_view: _RepositoryContentView,
    value: object,
    errors: list[str],
) -> dict[str, dict[str, Any]]:
    if not isinstance(value, list):
        errors.append("targets must be a JSON array")
        return {}
    expected_count = len(EXPECTED_TARGET_COMPONENTS)
    if len(value) != expected_count:
        errors.append(f"targets must contain exactly {expected_count} entries")
    value = value[: expected_count + 1]

    targets: dict[str, dict[str, Any]] = {}
    unexpected_target_paths: set[str] = set()
    for index, target in enumerate(value):
        label = f"targets[{index}]"
        if not isinstance(target, dict):
            errors.append(f"{label} must be a JSON object")
            continue

        target_path = target.get("path")
        if not _nonempty_string(target_path):
            errors.append(f"{label}.path must be a non-empty string")
            continue
        assert isinstance(target_path, str)
        path_error = relative_path_error(target_path)
        if path_error:
            errors.append(
                f"{label}.path is unsafe: {_display_error_value(target_path)}: "
                f"{path_error}"
            )
            continue
        if target_path not in EXPECTED_TARGET_COMPONENTS:
            errors.append(
                "unexpected imported target path: "
                + _display_error_value(target_path)
            )
            unexpected_target_paths.add(target_path)
            continue
        if target_path in targets:
            errors.append(f"duplicate imported target path: {target_path}")
            continue
        targets[target_path] = target

        target_content = content_view.required_bytes(
            target_path, f"imported target {target_path}", errors
        )
        _validate_source_path(
            target.get("source_path"),
            f"imported target {target_path} source_path",
            errors,
        )
        _validate_lower_hex(
            target.get("source_sha256"),
            SHA256,
            f"imported target {target_path} source_sha256",
            errors,
        )
        expected_source = EXPECTED_TARGET_SOURCES.get(target_path)
        if expected_source is not None:
            expected_source_path, expected_source_hash = expected_source
            if target.get("source_path") != expected_source_path:
                errors.append(
                    f"imported target {target_path} source_path must be "
                    f"{expected_source_path}"
                )
            if target.get("source_sha256") != expected_source_hash:
                errors.append(
                    f"imported target {target_path} source_sha256 must be "
                    f"{expected_source_hash}"
                )
        for deprecated_field in ("import_target_sha256", "current_target_sha256"):
            if deprecated_field in target:
                errors.append(
                    f"imported target {target_path} {deprecated_field} is ambiguous "
                    "in schema 3; use raw Git blob metadata and the optional "
                    "worktree_materialized_sha256 field"
                )
        for snapshot in GIT_TARGET_SNAPSHOTS:
            mode_field = f"{snapshot}_target_git_mode"
            object_type_field = f"{snapshot}_target_git_object_type"
            oid_field = f"{snapshot}_target_git_blob_oid"
            raw_hash_field = f"{snapshot}_target_raw_blob_sha256"
            mode = target.get(mode_field)
            if mode not in GIT_REGULAR_FILE_MODES:
                errors.append(
                    f"imported target {target_path} {mode_field} must be 100644 "
                    "or 100755"
                )
            if target.get(object_type_field) != "blob":
                errors.append(
                    f"imported target {target_path} {object_type_field} must be blob"
                )
            _validate_lower_hex(
                target.get(oid_field),
                GIT_OBJECT_ID,
                f"imported target {target_path} {oid_field}",
                errors,
            )
            _validate_lower_hex(
                target.get(raw_hash_field),
                SHA256,
                f"imported target {target_path} {raw_hash_field}",
                errors,
            )

        audited_raw_hash = target.get("audited_target_raw_blob_sha256")
        materialized_hash = target.get("worktree_materialized_sha256")
        if target_path in EXPECTED_MATERIALIZED_TARGETS:
            if materialized_hash is None:
                errors.append(
                    f"imported target {target_path} must record "
                    "worktree_materialized_sha256"
                )
        elif materialized_hash is not None:
            errors.append(
                f"imported target {target_path} must not record "
                "worktree_materialized_sha256"
            )
        if materialized_hash is not None:
            materialized_valid = _validate_lower_hex(
                materialized_hash,
                SHA256,
                f"imported target {target_path} worktree_materialized_sha256",
                errors,
            )
            if materialized_valid and materialized_hash == audited_raw_hash:
                errors.append(
                    f"imported target {target_path} worktree_materialized_sha256 "
                    "is unnecessary because it equals the audited raw Git blob hash"
                )
        content_hash = (
            audited_raw_hash
            if content_view.uses_raw_git_blobs
            else materialized_hash
            if materialized_hash is not None
            else audited_raw_hash
        )
        _validate_content_hash(
            target_content,
            content_hash,
            f"imported target {target_path}",
            errors,
        )

        expected_component = EXPECTED_TARGET_COMPONENTS.get(target_path)
        if expected_component is not None:
            if target.get("component") != expected_component:
                errors.append(
                    f"imported target {target_path} component must be "
                    f"{expected_component}"
                )
            expected_license = EXPECTED_COMPONENTS[expected_component]["license"]
            if target.get("license") != expected_license:
                errors.append(
                    f"imported target {target_path} license must be {expected_license}"
                )

        transformations = target.get("transformations")
        if (
            not isinstance(transformations, list)
            or not transformations
            or any(not _nonempty_string(item) for item in transformations)
        ):
            errors.append(
                f"imported target {target_path} transformations must contain "
                "at least one non-empty string"
            )

    actual_paths = set(targets)
    expected_paths = set(EXPECTED_TARGET_COMPONENTS)
    missing = sorted(expected_paths - actual_paths)
    extra = sorted((actual_paths - expected_paths) | unexpected_target_paths)
    if missing:
        errors.append("missing required imported targets: " + ", ".join(missing))
    if extra:
        errors.append("unexpected imported targets: " + ", ".join(extra))
    return targets


def _validate_local_assets(
    content_view: _RepositoryContentView,
    value: object,
    errors: list[str],
) -> dict[str, dict[str, Any]]:
    if not isinstance(value, list):
        errors.append("local_assets must be a JSON array")
        return {}
    expected_count = len(EXPECTED_LOCAL_ASSETS)
    if len(value) != expected_count:
        errors.append(f"local_assets must contain exactly {expected_count} entries")
    value = value[: expected_count + 1]

    assets: dict[str, dict[str, Any]] = {}
    unexpected_asset_paths: set[str] = set()
    for index, asset in enumerate(value):
        label = f"local_assets[{index}]"
        if not isinstance(asset, dict):
            errors.append(f"{label} must be a JSON object")
            continue

        asset_path = asset.get("path")
        if not _nonempty_string(asset_path):
            errors.append(f"{label}.path must be a non-empty string")
            continue
        assert isinstance(asset_path, str)
        path_error = relative_path_error(asset_path)
        if path_error:
            errors.append(
                f"{label}.path is unsafe: {_display_error_value(asset_path)}: "
                f"{path_error}"
            )
            continue
        if asset_path not in EXPECTED_LOCAL_ASSETS:
            errors.append(
                "unexpected local asset path: " + _display_error_value(asset_path)
            )
            unexpected_asset_paths.add(asset_path)
            continue
        if asset_path in assets:
            errors.append(f"duplicate local asset path: {asset_path}")
            continue
        assets[asset_path] = asset

        target_content = content_view.required_bytes(
            asset_path, f"local asset {asset_path}", errors
        )
        _validate_content_hash(
            target_content,
            asset.get("target_sha256"),
            f"local asset {asset_path}",
            errors,
        )
        _validate_lower_hex(
            asset.get("introduced_commit"),
            COMMIT,
            f"local asset {asset_path} introduced_commit",
            errors,
        )
        for snapshot in ("introduced", "audited"):
            mode_field = f"{snapshot}_git_mode"
            object_type_field = f"{snapshot}_git_object_type"
            oid_field = f"{snapshot}_git_blob_oid"
            raw_hash_field = f"{snapshot}_raw_blob_sha256"
            if asset.get(mode_field) not in GIT_REGULAR_FILE_MODES:
                errors.append(
                    f"local asset {asset_path} {mode_field} must be 100644 or 100755"
                )
            if asset.get(object_type_field) != "blob":
                errors.append(
                    f"local asset {asset_path} {object_type_field} must be blob"
                )
            _validate_lower_hex(
                asset.get(oid_field),
                GIT_OBJECT_ID,
                f"local asset {asset_path} {oid_field}",
                errors,
            )
            _validate_lower_hex(
                asset.get(raw_hash_field),
                SHA256,
                f"local asset {asset_path} {raw_hash_field}",
                errors,
            )
        if asset.get("target_sha256") != asset.get("audited_raw_blob_sha256"):
            errors.append(
                f"local asset {asset_path} target_sha256 must equal "
                "audited_raw_blob_sha256 for a binary resource"
            )
        if not _nonempty_string(asset.get("description")):
            errors.append(f"local asset {asset_path} description is required")

        expected = EXPECTED_LOCAL_ASSETS.get(asset_path)
        if expected is not None:
            expected_status, expected_license = expected
            if asset.get("status") != expected_status:
                errors.append(
                    f"local asset {asset_path} status must be {expected_status}"
                )
            if asset.get("license") != expected_license:
                errors.append(
                    f"local asset {asset_path} license must be {expected_license}"
                )

        if asset.get("status") == "GENERATED":
            generator_content = content_view.required_bytes(
                asset.get("generator_path"),
                f"local asset {asset_path} generator",
                errors,
            )
            _validate_content_hash(
                generator_content,
                asset.get("generator_sha256"),
                f"local asset {asset_path} generator",
                errors,
            )
            if not _nonempty_string(asset.get("generation_command")):
                errors.append(
                    f"local asset {asset_path} generation_command is required"
                )

    actual_paths = set(assets)
    expected_paths = set(EXPECTED_LOCAL_ASSETS)
    missing = sorted(expected_paths - actual_paths)
    extra = sorted((actual_paths - expected_paths) | unexpected_asset_paths)
    if missing:
        errors.append("missing required local assets: " + ", ".join(missing))
    if extra:
        errors.append("unexpected local assets: " + ", ".join(extra))
    return assets


def _repository_resource_files(
    repository_root: Path, errors: list[str]
) -> set[str]:
    resources: set[str] = set()
    directories_seen = 0
    entries_seen = 0
    for root_prefix in RESOURCE_ROOTS:
        resource_root = repository_root
        root_status: os.stat_result | None = None
        root_unavailable = False
        for part in PurePosixPath(root_prefix).parts:
            resource_root /= part
            try:
                root_status = resource_root.lstat()
            except FileNotFoundError:
                root_unavailable = True
                break
            except OSError as exc:
                errors.append(
                    f"cannot inspect worktree resource root {root_prefix}: {exc}"
                )
                root_unavailable = True
                break
            if stat.S_ISLNK(root_status.st_mode) or _is_reparse_point(
                resource_root, root_status
            ):
                errors.append(
                    "worktree resource root must not traverse a symlink, junction, "
                    f"or reparse point: {resource_root.relative_to(repository_root)}"
                )
                root_unavailable = True
                break
            if not stat.S_ISDIR(root_status.st_mode):
                errors.append(
                    f"worktree resource root component is not a directory: "
                    f"{resource_root.relative_to(repository_root)}"
                )
                root_unavailable = True
                break
        if root_unavailable:
            continue
        assert root_status is not None
        if (
            not stat.S_ISDIR(root_status.st_mode)
            or stat.S_ISLNK(root_status.st_mode)
            or _is_reparse_point(resource_root, root_status)
        ):
            errors.append(
                f"worktree resource root must be an ordinary directory: {root_prefix}"
            )
            continue

        pending = [resource_root]
        while pending:
            current = pending.pop()
            directories_seen += 1
            if directories_seen > MAX_WORKTREE_RESOURCE_DIRECTORIES:
                errors.append(
                    "worktree resource inventory exceeds "
                    f"{MAX_WORKTREE_RESOURCE_DIRECTORIES} directories"
                )
                return resources
            try:
                iterator = os.scandir(current)
            except OSError as exc:
                errors.append(f"cannot traverse worktree resources at {current}: {exc}")
                continue
            with iterator:
                for entry in iterator:
                    entries_seen += 1
                    if entries_seen > (
                        MAX_SELECTED_RESOURCE_FILES
                        + MAX_WORKTREE_RESOURCE_DIRECTORIES
                    ):
                        errors.append("worktree resource inventory exceeds its entry limit")
                        return resources
                    path = Path(entry.path)
                    try:
                        status = entry.stat(follow_symlinks=False)
                        relative = path.relative_to(repository_root).as_posix()
                    except (OSError, ValueError) as exc:
                        errors.append(f"cannot inspect worktree resource entry: {exc}")
                        continue
                    path_error = relative_path_error(relative)
                    if path_error:
                        errors.append(
                            "worktree resource path is unsafe: "
                            f"{_display_error_value(relative)}: {path_error}"
                        )
                        continue
                    if stat.S_ISLNK(status.st_mode) or _is_reparse_point(path, status):
                        errors.append(
                            "worktree resource inventory forbids symlinks, junctions, "
                            f"and reparse points: {relative}"
                        )
                        continue
                    if stat.S_ISDIR(status.st_mode):
                        pending.append(path)
                        continue
                    if not stat.S_ISREG(status.st_mode):
                        errors.append(
                            f"worktree resource entry is not a regular file: {relative}"
                        )
                        continue
                    if relative.startswith(EXCLUDED_RESOURCE_PREFIXES):
                        continue
                    resources.add(relative)
                    if len(resources) > MAX_SELECTED_RESOURCE_FILES:
                        errors.append(
                            "worktree resource inventory exceeds "
                            f"{MAX_SELECTED_RESOURCE_FILES} files"
                        )
                        return resources
    return resources


def _validate_resource_inventory(
    content_view: _RepositoryContentView,
    targets: dict[str, dict[str, Any]],
    assets: dict[str, dict[str, Any]],
    errors: list[str],
) -> None:
    declared = {
        path
        for path in (*targets, *assets)
        if path.startswith(RESOURCE_ROOTS)
    }
    discovered = content_view.resource_files(errors)
    missing_records = sorted(discovered - declared)
    stale_records = sorted(declared - discovered)
    if missing_records:
        errors.append(
            "resource files missing provenance entries: " + ", ".join(missing_records)
        )
    if stale_records:
        errors.append(
            "provenance resource entries without files: " + ", ".join(stale_records)
        )


def _validate_git_history(
    repository_root: Path,
    document: dict[str, Any],
    targets: dict[str, dict[str, Any]],
    assets: dict[str, dict[str, Any]],
    errors: list[str],
    comparison_commit: str | None = None,
) -> None:
    if not _validate_git_repository(repository_root, errors):
        return

    import_commit = document.get("import_commit")
    audited_commit = document.get("audited_target_commit")
    import_valid = isinstance(import_commit, str) and COMMIT.fullmatch(import_commit)
    audited_valid = isinstance(audited_commit, str) and COMMIT.fullmatch(audited_commit)
    import_exists = bool(
        import_valid
        and _git_commit_exists(
            repository_root, import_commit, "import_commit", errors
        )
    )
    audited_exists = bool(
        audited_valid
        and _git_commit_exists(
            repository_root, audited_commit, "audited_target_commit", errors
        )
    )

    head_commit: str | None = None
    comparison_label = "HEAD"
    if comparison_commit is not None:
        comparison_label = "selected commit"
        if COMMIT.fullmatch(comparison_commit) is None:
            errors.append(
                "selected comparison commit is not a lowercase 40-character "
                "Git commit ID"
            )
        elif _git_commit_exists(
            repository_root,
            comparison_commit,
            "selected comparison commit",
            errors,
        ):
            head_commit = comparison_commit
    else:
        try:
            head_result = _run_git(repository_root, ["rev-parse", "--verify", "HEAD"])
        except (OSError, subprocess.SubprocessError) as exc:
            errors.append(f"cannot resolve provenance repository HEAD: {exc}")
            head_result = None
        if head_result is not None and head_result.returncode != 0:
            errors.append(
                "provenance repository must have a valid HEAD commit: "
                + _git_error(head_result)
            )
        elif head_result is not None:
            try:
                candidate_head = head_result.stdout.decode(
                    "ascii", errors="strict"
                ).strip()
            except UnicodeError as exc:
                errors.append(f"cannot decode provenance repository HEAD: {exc}")
            else:
                if COMMIT.fullmatch(candidate_head) is None:
                    errors.append(
                        "provenance repository HEAD is not a lowercase 40-character "
                        "Git commit ID"
                    )
                else:
                    head_commit = candidate_head

    if import_exists and audited_exists:
        assert isinstance(import_commit, str)
        assert isinstance(audited_commit, str)
        _validate_git_ancestor(
            repository_root,
            import_commit,
            audited_commit,
            "import_commit -> audited_target_commit",
            errors,
        )
    if audited_exists and head_commit is not None:
        assert isinstance(audited_commit, str)
        _validate_git_ancestor(
            repository_root,
            audited_commit,
            head_commit,
            f"audited_target_commit -> {comparison_label}",
            errors,
        )

    parent_cache: dict[str, list[str] | None] = {}
    for path, target in targets.items():
        if import_exists:
            assert isinstance(import_commit, str)
            import_entry = _validate_git_tree_snapshot(
                repository_root,
                import_commit,
                path,
                target.get("import_target_git_mode"),
                target.get("import_target_git_object_type"),
                target.get("import_target_git_blob_oid"),
                f"imported target {path} import snapshot",
                errors,
            )
            _validate_path_changed_from_first_parent(
                repository_root,
                import_commit,
                path,
                import_entry,
                f"import_commit for imported target {path}",
                parent_cache,
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                import_commit,
                path,
                target.get("import_target_raw_blob_sha256"),
                f"imported target {path} import snapshot",
                errors,
            )
        if audited_exists:
            assert isinstance(audited_commit, str)
            _validate_git_tree_snapshot(
                repository_root,
                audited_commit,
                path,
                target.get("audited_target_git_mode"),
                target.get("audited_target_git_object_type"),
                target.get("audited_target_git_blob_oid"),
                f"imported target {path} audited snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                audited_commit,
                path,
                target.get("audited_target_raw_blob_sha256"),
                f"imported target {path} audited snapshot",
                errors,
            )
            materialized_hash = target.get("worktree_materialized_sha256")
            if materialized_hash is not None:
                _validate_git_materialized_hash(
                    repository_root,
                    audited_commit,
                    path,
                    materialized_hash,
                    f"imported target {path} audited snapshot",
                    errors,
                )
        if audited_exists and head_commit is not None:
            _validate_git_tree_snapshot(
                repository_root,
                head_commit,
                path,
                target.get("audited_target_git_mode"),
                target.get("audited_target_git_object_type"),
                target.get("audited_target_git_blob_oid"),
                f"imported target {path} {comparison_label} snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                head_commit,
                path,
                target.get("audited_target_raw_blob_sha256"),
                f"imported target {path} {comparison_label} snapshot",
                errors,
            )
            materialized_hash = target.get("worktree_materialized_sha256")
            if materialized_hash is not None:
                _validate_git_materialized_hash(
                    repository_root,
                    head_commit,
                    path,
                    materialized_hash,
                    f"imported target {path} {comparison_label} snapshot",
                    errors,
                )

    introduced_commits: dict[str, bool] = {}
    for path, asset in assets.items():
        introduced_commit = asset.get("introduced_commit")
        if not isinstance(introduced_commit, str) or COMMIT.fullmatch(
            introduced_commit
        ) is None:
            continue
        if introduced_commit not in introduced_commits:
            introduced_commits[introduced_commit] = _git_commit_exists(
                repository_root,
                introduced_commit,
                f"local asset introduced_commit for {path}",
                errors,
            )
        introduced_exists = introduced_commits[introduced_commit]
        if introduced_exists and audited_exists:
            assert isinstance(audited_commit, str)
            _validate_git_ancestor(
                repository_root,
                introduced_commit,
                audited_commit,
                f"local asset {path} introduced_commit -> audited_target_commit",
                errors,
            )
        if introduced_exists:
            introduction_entry = _validate_git_tree_snapshot(
                repository_root,
                introduced_commit,
                path,
                asset.get("introduced_git_mode"),
                asset.get("introduced_git_object_type"),
                asset.get("introduced_git_blob_oid"),
                f"local asset {path} introduction snapshot",
                errors,
            )
            _validate_path_changed_from_every_parent(
                repository_root,
                introduced_commit,
                path,
                introduction_entry,
                f"introduced_commit for local asset {path}",
                parent_cache,
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                introduced_commit,
                path,
                asset.get("introduced_raw_blob_sha256"),
                f"local asset {path} introduction snapshot",
                errors,
            )
        if audited_exists:
            assert isinstance(audited_commit, str)
            _validate_git_tree_snapshot(
                repository_root,
                audited_commit,
                path,
                asset.get("audited_git_mode"),
                asset.get("audited_git_object_type"),
                asset.get("audited_git_blob_oid"),
                f"local asset {path} audited snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                audited_commit,
                path,
                asset.get("audited_raw_blob_sha256"),
                f"local asset {path} audited snapshot",
                errors,
            )
        if audited_exists and head_commit is not None:
            _validate_git_tree_snapshot(
                repository_root,
                head_commit,
                path,
                asset.get("audited_git_mode"),
                asset.get("audited_git_object_type"),
                asset.get("audited_git_blob_oid"),
                f"local asset {path} {comparison_label} snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                head_commit,
                path,
                asset.get("audited_raw_blob_sha256"),
                f"local asset {path} {comparison_label} snapshot",
                errors,
            )

        generator_path = asset.get("generator_path")
        generator_path_safe = bool(
            isinstance(generator_path, str)
            and relative_path_error(generator_path) is None
        )
        if audited_exists and generator_path_safe:
            assert isinstance(generator_path, str)
            assert isinstance(audited_commit, str)
            audited_generator_entry = _validate_git_tree_snapshot(
                repository_root,
                audited_commit,
                generator_path,
                None,
                None,
                None,
                f"local asset {path} generator audited snapshot",
                errors,
            )
            _validate_git_raw_blob_hash(
                repository_root,
                audited_commit,
                generator_path,
                asset.get("generator_sha256"),
                f"local asset {path} generator audited snapshot",
                errors,
            )
            if head_commit is not None:
                head_generator_entry = _validate_git_tree_snapshot(
                    repository_root,
                    head_commit,
                    generator_path,
                    None,
                    None,
                    None,
                    f"local asset {path} generator {comparison_label} snapshot",
                    errors,
                )
                _validate_git_raw_blob_hash(
                    repository_root,
                    head_commit,
                    generator_path,
                    asset.get("generator_sha256"),
                    f"local asset {path} generator {comparison_label} snapshot",
                    errors,
                )
                if (
                    audited_generator_entry is not None
                    and head_generator_entry is not None
                    and audited_generator_entry != head_generator_entry
                ):
                    errors.append(
                        f"local asset {path} generator {comparison_label} snapshot "
                        "must exactly "
                        "match its audited Git tree entry"
                    )


def _parse_markdown_scalar(text: str, field: str) -> object:
    fence = YAML_FENCE.search(text)
    scope = fence.group("body") if fence is not None else ""
    match = re.search(rf"^{re.escape(field)}:\s*(.*?)\s*$", scope, re.MULTILINE)
    if match is None:
        return object()
    value = match.group(1)
    if value in ("null", "~"):
        return None
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def _bounded_yaml_bodies(
    text: str, label: str, errors: list[str]
) -> list[str] | None:
    bodies: list[str] = []
    for fence in YAML_FENCE.finditer(text):
        bodies.append(fence.group("body"))
        if len(bodies) > MAX_MARKDOWN_YAML_FENCES:
            errors.append(
                f"{label} exceeds {MAX_MARKDOWN_YAML_FENCES} YAML metadata blocks"
            )
            return None
    return bodies


def _parse_markdown_scalar_occurrences(
    text: str, field: str, label: str, errors: list[str]
) -> list[object] | None:
    values: list[object] = []
    bodies = _bounded_yaml_bodies(text, label, errors)
    if bodies is None:
        return None
    for body in bodies:
        for match in re.finditer(
            rf"^{re.escape(field)}:\s*(.*?)\s*$", body, re.MULTILINE
        ):
            value = match.group(1)
            if value in ("null", "~"):
                values.append(None)
            elif (
                len(value) >= 2
                and value[0] == value[-1]
                and value[0] in ("'", '"')
            ):
                values.append(value[1:-1])
            else:
                values.append(value)
            if len(values) > MAX_MARKDOWN_FIELD_OCCURRENCES:
                errors.append(
                    f"{label} field {field} exceeds "
                    f"{MAX_MARKDOWN_FIELD_OCCURRENCES} occurrences"
                )
                return None
    return values


def _parse_markdown_target_scalar_occurrences(
    text: str, field: str, label: str, errors: list[str]
) -> list[object] | None:
    values: list[object] = []
    bodies = _bounded_yaml_bodies(text, label, errors)
    if bodies is None:
        return None
    for body in bodies:
        if (
            re.search(r"^status:\s*", body, re.MULTILINE) is None
            or re.search(r"^proposed_status_after_review:\s*", body, re.MULTILINE)
            is None
        ):
            continue
        for match in re.finditer(
            rf"^{re.escape(field)}:\s*(.*?)\s*$", body, re.MULTILINE
        ):
            value = match.group(1)
            if value in ("null", "~"):
                values.append(None)
            elif (
                len(value) >= 2
                and value[0] == value[-1]
                and value[0] in ("'", '"')
            ):
                values.append(value[1:-1])
            else:
                values.append(value)
            if len(values) > MAX_MARKDOWN_FIELD_OCCURRENCES:
                errors.append(
                    f"{label} target field {field} exceeds "
                    f"{MAX_MARKDOWN_FIELD_OCCURRENCES} occurrences"
                )
                return None
    return values


def _field_count(yaml_body: str, field: str) -> int:
    count = 0
    for _ in re.finditer(rf"^{re.escape(field)}:\s*.*?$", yaml_body, re.MULTILINE):
        count += 1
        if count > MAX_MARKDOWN_FIELD_OCCURRENCES:
            break
    return count


def _validate_record_yaml_structure(record_text: str, errors: list[str]) -> None:
    bodies = _bounded_yaml_bodies(record_text, "provenance Markdown", errors)
    if bodies is None:
        return
    if not bodies:
        errors.append("provenance Markdown must contain an initial YAML metadata block")
        return

    initial_body = bodies[0]
    for field in INITIAL_RECORD_FIELDS:
        if _field_count(initial_body, field) != 1:
            errors.append(
                f"initial provenance YAML {field} must occur exactly once"
            )

    record_only_fields = (*RECORD_IDENTITY_FIELDS, *RECORD_ONLY_REVIEW_FIELDS)
    for body in bodies[1:]:
        for field in record_only_fields:
            if _field_count(body, field):
                errors.append(
                    f"reserved provenance YAML field {field} must occur only in "
                    "the initial metadata block"
                )

        is_target_review_block = bool(
            _field_count(body, "status")
            and _field_count(body, "proposed_status_after_review")
        )
        for field in ("reviewer", "reviewed_at"):
            count = _field_count(body, field)
            if count and not is_target_review_block:
                errors.append(
                    f"reserved provenance YAML field {field} outside the initial "
                    "metadata block must belong to a target review block"
                )
            elif is_target_review_block and count != 1:
                errors.append(
                    f"provenance target review YAML {field} must occur exactly once"
                )
        if is_target_review_block:
            for field in ("status", "proposed_status_after_review"):
                if _field_count(body, field) != 1:
                    errors.append(
                        f"provenance target review YAML {field} must occur exactly once"
                    )


def _valid_iso_date(value: object) -> bool:
    if not isinstance(value, str):
        return False
    try:
        date.fromisoformat(value)
    except ValueError:
        return False
    return True


def _canonical_review_document(document: dict[str, Any]) -> bytes:
    canonical = copy.deepcopy(document)
    review = canonical.get("review")
    if isinstance(review, dict):
        for field in REVIEW_METADATA_FIELDS:
            review[field] = REVIEW_METADATA_SENTINEL
    else:
        canonical["review"] = {
            field: REVIEW_METADATA_SENTINEL for field in REVIEW_METADATA_FIELDS
        }
    return json.dumps(
        canonical,
        allow_nan=False,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")


def _canonical_review_record(record_content: bytes) -> bytes:
    record_text = record_content.decode("utf-8", errors="strict")
    fence = YAML_FENCE.search(record_text)
    if fence is None:
        return record_content

    body = fence.group("body")
    for field in REVIEW_METADATA_FIELDS:
        body = re.sub(
            rf"^{re.escape(field)}:[^\r\n]*(?P<eol>\r?\n|$)",
            lambda match, field=field: (
                f"{field}: {REVIEW_METADATA_SENTINEL}{match.group('eol')}"
            ),
            body,
            count=1,
            flags=re.MULTILINE,
        )
    canonical = record_text[: fence.start("body")] + body + record_text[fence.end("body") :]
    return canonical.encode("utf-8")


def compute_review_content_sha256(
    document: dict[str, Any], record_content: bytes, notice_content: bytes
) -> str:
    """Bind approval to every evidence field, record byte, and notice byte.

    The six mutable approval metadata values are replaced by fixed sentinels to
    avoid a circular digest. All other manifest values (including unknown future
    fields), all other provenance Markdown bytes, and the complete third-party
    notice bytes participate in the digest.
    """

    manifest_content = _canonical_review_document(document)
    canonical_record_content = _canonical_review_record(record_content)
    digest = hashlib.sha256()
    digest.update(REVIEW_DIGEST_DOMAIN)
    for content in (manifest_content, canonical_record_content, notice_content):
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    return digest.hexdigest()


def _validate_approved_document_state(
    record_text: str,
    notice_text: str,
    reviewer: object,
    reviewed_at: object,
    errors: list[str],
) -> None:
    documents = (
        ("provenance Markdown", record_text),
        ("third-party notice", notice_text),
    )
    for label, text in documents:
        for description, pattern in APPROVED_DOCUMENT_CONTRADICTIONS:
            if pattern.search(text):
                errors.append(
                    f"approved review {label} still contains {description}"
                )
        if UNCHECKED_CHECKBOX.search(text):
            errors.append(
                f"approved review {label} still contains an unchecked checklist item"
            )

    expected_record_fields: dict[str, object] = {
        "status": APPROVED_RECORD_STATUS,
        "proposed_status_after_review": None,
        "reviewer": reviewer,
        "reviewed_at": reviewed_at,
    }
    for field, expected in expected_record_fields.items():
        occurrences = _parse_markdown_scalar_occurrences(
            record_text, field, "approved provenance Markdown", errors
        )
        if occurrences is None:
            continue
        if not occurrences:
            errors.append(
                f"approved provenance Markdown must retain reviewed {field} fields"
            )
        elif any(value != expected for value in occurrences):
            errors.append(
                f"approved provenance Markdown {field} fields contradict the "
                "manifest review state"
            )

    expected_notice_fields: dict[str, object] = {
        "status": APPROVED_RECORD_STATUS,
        "reviewer": reviewer,
        "reviewed_at": reviewed_at,
    }
    for field, expected in expected_notice_fields.items():
        occurrences = _parse_markdown_scalar_occurrences(
            notice_text, field, "approved third-party notice", errors
        )
        if occurrences is None:
            continue
        if occurrences != [expected]:
            errors.append(
                f"approved third-party notice {field} must occur exactly once and "
                "match the manifest review state"
            )


def _validate_pending_document_state(
    record_text: str,
    notice_text: str,
    errors: list[str],
) -> None:
    expected_notice_fields: dict[str, object] = {
        "status": PENDING_TARGET_STATUS,
        "reviewer": None,
        "reviewed_at": None,
    }
    for field, expected in expected_notice_fields.items():
        occurrences = _parse_markdown_scalar_occurrences(
            notice_text, field, "pending third-party notice", errors
        )
        if occurrences is None:
            continue
        if occurrences != [expected]:
            errors.append(
                f"pending third-party notice {field} must occur exactly once and "
                "match the manifest review state"
            )

    expected_record_target_fields: dict[str, object] = {
        "status": PENDING_TARGET_STATUS,
        "proposed_status_after_review": APPROVED_RECORD_STATUS,
        "reviewer": None,
        "reviewed_at": None,
    }
    for field, expected in expected_record_target_fields.items():
        occurrences = _parse_markdown_target_scalar_occurrences(
            record_text, field, "pending provenance Markdown", errors
        )
        if occurrences is None:
            continue
        if not occurrences:
            errors.append(
                f"pending provenance Markdown must retain target {field} fields"
            )
        elif any(value != expected for value in occurrences):
            errors.append(
                f"pending provenance Markdown target {field} fields contradict the "
                "manifest review state"
            )


def _validate_record_metadata(
    document: dict[str, Any], record_text: str | None, errors: list[str]
) -> None:
    if record_text is None:
        return
    expected_fields = {
        "record_version": str(document.get("schema_version")),
        "scope_version": document.get("scope_version"),
        "import_commit": document.get("import_commit"),
        "audited_target_commit": document.get("audited_target_commit"),
    }
    for field, expected in expected_fields.items():
        observed = _parse_markdown_scalar(record_text, field)
        if observed != expected:
            errors.append(
                f"provenance Markdown {field} does not match the manifest"
            )


def _validate_review(
    review_value: object,
    targets: dict[str, dict[str, Any]],
    record_text: str | None,
    notice_text: str | None,
    audited_target_commit: object,
    calculated_review_digest: str | None,
    errors: list[str],
) -> str:
    if not isinstance(review_value, dict):
        errors.append("review must be a JSON object")
        return "UNKNOWN"

    status = review_value.get("record_status")
    reviewer = review_value.get("reviewer")
    reviewed_at = review_value.get("reviewed_at")
    final_status = review_value.get("final_status_after_review")
    reviewed_commit = review_value.get("reviewed_audited_target_commit")
    reviewed_digest = review_value.get("reviewed_content_sha256")

    actual_fields = set(review_value)
    expected_fields = set(REVIEW_METADATA_FIELDS)
    if actual_fields != expected_fields:
        missing = sorted(expected_fields - actual_fields)
        extra = sorted(actual_fields - expected_fields)
        if missing:
            errors.append("review is missing required fields: " + ", ".join(missing))
        if extra:
            errors.append("review has unexpected fields: " + ", ".join(extra))

    if status == PENDING_RECORD_STATUS:
        if any(
            value is not None
            for value in (
                reviewer,
                reviewed_at,
                final_status,
                reviewed_commit,
                reviewed_digest,
            )
        ):
            errors.append(
                "pending review must have null approval metadata, including "
                "reviewer, reviewed_at, reviewed commit, and reviewed content digest"
            )
        expected_target_status = PENDING_TARGET_STATUS
        expected_proposed_status: str | None = APPROVED_RECORD_STATUS
        if record_text is not None and notice_text is not None:
            _validate_pending_document_state(record_text, notice_text, errors)
    elif status == APPROVED_RECORD_STATUS:
        if not _nonempty_string(reviewer):
            errors.append("approved review requires a non-empty reviewer")
        if not _valid_iso_date(reviewed_at):
            errors.append("approved review requires a valid ISO reviewed_at date")
        if final_status != APPROVED_RECORD_STATUS:
            errors.append(
                "approved review final_status_after_review must be "
                f"{APPROVED_RECORD_STATUS}"
            )
        reviewed_commit_valid = _validate_lower_hex(
            reviewed_commit,
            COMMIT,
            "approved review reviewed_audited_target_commit",
            errors,
        )
        if reviewed_commit_valid and reviewed_commit != audited_target_commit:
            errors.append(
                "approved review is bound to a different audited_target_commit"
            )
        reviewed_digest_valid = _validate_lower_hex(
            reviewed_digest,
            SHA256,
            "approved review reviewed_content_sha256",
            errors,
        )
        if reviewed_digest_valid and calculated_review_digest is None:
            errors.append(
                "approved review content digest cannot be verified without the Markdown record"
            )
        elif reviewed_digest_valid and reviewed_digest != calculated_review_digest:
            errors.append(
                "approved review content digest does not match the current manifest, "
                "provenance record, and third-party notice"
            )
        expected_target_status = APPROVED_RECORD_STATUS
        expected_proposed_status = None
        if record_text is None or notice_text is None:
            errors.append(
                "approved review requires readable provenance Markdown and "
                "third-party notice documents"
            )
        else:
            _validate_approved_document_state(
                record_text,
                notice_text,
                reviewer,
                reviewed_at,
                errors,
            )
    else:
        errors.append(
            "review.record_status must be "
            f"{PENDING_RECORD_STATUS} or {APPROVED_RECORD_STATUS}"
        )
        return str(status) if status is not None else "UNKNOWN"

    for path, target in targets.items():
        if target.get("status") != expected_target_status:
            errors.append(
                f"imported target {path} status is inconsistent with review state; "
                f"expected {expected_target_status}"
            )
        if target.get("proposed_status_after_review") != expected_proposed_status:
            errors.append(
                f"imported target {path} proposed_status_after_review is "
                "inconsistent with review state"
            )

    if record_text is not None:
        expected_fields = {
            "record_status": status,
            "reviewer": reviewer,
            "reviewed_at": reviewed_at,
            "final_status_after_review": final_status,
            "reviewed_audited_target_commit": reviewed_commit,
            "reviewed_content_sha256": reviewed_digest,
        }
        for field, expected in expected_fields.items():
            observed = _parse_markdown_scalar(record_text, field)
            if observed != expected:
                errors.append(
                    f"provenance Markdown {field} does not match the manifest review state"
                )
    return status


def _manifest_path_under_root(
    repository_root: Path,
    manifest_path: Path,
    errors: list[str],
) -> Path | None:
    if manifest_path.is_absolute():
        try:
            relative = manifest_path.relative_to(repository_root).as_posix()
        except ValueError:
            errors.append("Bootstrap provenance manifest must remain under the repository root")
            return None
    else:
        relative = manifest_path.as_posix()
    return _required_local_file(
        repository_root, relative, "bootstrap provenance manifest", errors
    )


def _validation_details() -> dict[str, int | str]:
    return {
        "components": 0,
        "targets": 0,
        "local_assets": 0,
        "review_status": "UNKNOWN",
        "review_content_sha256": "UNKNOWN",
    }


def _validate_provenance_document(
    repository_root: Path,
    content_view: _RepositoryContentView,
    document: dict[str, Any],
    errors: list[str],
    details: dict[str, int | str],
    *,
    comparison_commit: str | None,
) -> None:
    if type(document.get("schema_version")) is not int or document.get(
        "schema_version"
    ) != 3:
        errors.append("schema_version must be integer 3")
    if document.get("scope_version") != "v0.0.2":
        errors.append("scope_version must be v0.0.2")
    _validate_lower_hex(
        document.get("import_commit"), COMMIT, "import_commit", errors
    )
    _validate_lower_hex(
        document.get("audited_target_commit"),
        COMMIT,
        "audited_target_commit",
        errors,
    )

    components = _validate_components(content_view, document.get("components"), errors)
    targets = _validate_targets(content_view, document.get("targets"), errors)
    assets = _validate_local_assets(content_view, document.get("local_assets"), errors)
    _validate_resource_inventory(content_view, targets, assets, errors)
    _validate_git_history(
        repository_root,
        document,
        targets,
        assets,
        errors,
        comparison_commit=comparison_commit,
    )
    details["components"] = len(components)
    details["targets"] = len(targets)
    details["local_assets"] = len(assets)

    record_path_value = document.get("record_path")
    if record_path_value != EXPECTED_RECORD_PATH:
        errors.append(f"record_path must be {EXPECTED_RECORD_PATH}")
    record_content = content_view.required_bytes(
        record_path_value, "provenance Markdown record", errors
    )
    record_text: str | None = None
    if record_content is not None:
        try:
            record_text = record_content.decode("utf-8", errors="strict")
        except UnicodeError as exc:
            record_content = None
            record_text = None
            errors.append(f"Cannot read provenance Markdown record: {exc}")

    notice_content = content_view.required_bytes(
        EXPECTED_NOTICE_PATH,
        "third-party notice",
        errors,
    )
    notice_text: str | None = None
    if notice_content is not None:
        try:
            notice_text = notice_content.decode("utf-8", errors="strict")
        except UnicodeError as exc:
            notice_content = None
            notice_text = None
            errors.append(f"Cannot read third-party notice: {exc}")

    if record_text is not None:
        _validate_record_yaml_structure(record_text, errors)
    _validate_record_metadata(document, record_text, errors)
    calculated_review_digest = (
        compute_review_content_sha256(document, record_content, notice_content)
        if record_content is not None and notice_content is not None
        else None
    )
    if calculated_review_digest is not None:
        details["review_content_sha256"] = calculated_review_digest

    review_status = _validate_review(
        document.get("review"),
        targets,
        record_text,
        notice_text,
        document.get("audited_target_commit"),
        calculated_review_digest,
        errors,
    )
    details["review_status"] = review_status


def validate_bootstrap_provenance(
    repository_root: Path = ROOT,
    manifest_path: Path = DEFAULT_MANIFEST,
) -> tuple[list[str], dict[str, int | str]]:
    """Validate the mutable worktree, including approval-candidate edits."""

    repository_root = repository_root.resolve()
    errors: list[str] = []
    details = _validation_details()
    resolved_manifest = _manifest_path_under_root(
        repository_root, manifest_path, errors
    )
    if resolved_manifest is None:
        return errors, details
    document = _load_json(resolved_manifest, errors)
    if document is None:
        return errors, details
    _validate_provenance_document(
        repository_root,
        _WorktreeContentView(repository_root),
        document,
        errors,
        details,
        comparison_commit=None,
    )
    return errors, details


def validate_bootstrap_provenance_at_commit(
    repository_root: Path,
    selected_commit: str,
    manifest_path: Path = DEFAULT_MANIFEST,
) -> tuple[list[str], dict[str, int | str]]:
    """Validate complete schema-3 semantics from one explicit Git commit.

    No authoritative input is read from the mutable worktree, and ambient HEAD
    is not used as the comparison tip.
    """

    repository_root = repository_root.resolve()
    errors: list[str] = []
    details = _validation_details()
    if COMMIT.fullmatch(selected_commit) is None:
        errors.append(
            "selected_commit must be a lowercase 40-character Git commit ID"
        )
        return errors, details
    if not _validate_git_repository(repository_root, errors):
        return errors, details
    if not _git_commit_exists(
        repository_root, selected_commit, "selected_commit", errors
    ):
        return errors, details
    if manifest_path.is_absolute():
        errors.append("selected-commit manifest path must be relative")
        return errors, details
    manifest_relative = manifest_path.as_posix()
    content_view = _SelectedCommitContentView(repository_root, selected_commit)
    manifest_content = content_view.required_bytes(
        manifest_relative,
        "bootstrap provenance manifest",
        errors,
    )
    if manifest_content is None:
        return errors, details
    document = _load_json_content(manifest_content, manifest_relative, errors)
    if document is None:
        return errors, details
    _validate_provenance_document(
        repository_root,
        content_view,
        document,
        errors,
        details,
        comparison_commit=selected_commit,
    )
    return errors, details


def _manifest_has_null_review_digest(
    repository_root: Path,
    manifest_path: Path,
) -> bool:
    """Return whether the manifest has the required, explicitly null digest field."""

    load_errors: list[str] = []
    resolved_manifest = _manifest_path_under_root(
        repository_root.resolve(), manifest_path, load_errors
    )
    if resolved_manifest is None or load_errors:
        return False
    document = _load_json(resolved_manifest, load_errors)
    if document is None or load_errors:
        return False
    review = document.get("review")
    return (
        isinstance(review, dict)
        and "reviewed_content_sha256" in review
        and review["reviewed_content_sha256"] is None
    )


def _print_mechanical_pass(details: dict[str, int | str]) -> None:
    print(
        "[PASS] Bootstrap provenance mechanical validation: "
        f"{details['components']} components, {details['targets']} imported targets, "
        f"{details['local_assets']} local assets"
    )


def _print_review_state(details: dict[str, int | str]) -> None:
    status = details["review_status"]
    if status == PENDING_RECORD_STATUS:
        print(
            "[PENDING] Human provenance review: "
            f"{status}; mechanical validation does not approve G0"
        )
    elif status == APPROVED_RECORD_STATUS:
        print(
            "[PASS] Recorded provenance review is mechanically consistent and "
            f"digest-bound: {status}"
        )
    else:
        print(f"[INFO] Provenance review state: {status}")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=ROOT,
        help="repository root used to resolve provenance paths",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=DEFAULT_MANIFEST,
        help="machine-readable provenance manifest path",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--diagnostic-pending-digest",
        action="store_true",
        help=(
            "print a clearly non-approval digest for a valid pending review; "
            "refuses approved or invalid states"
        ),
    )
    mode.add_argument(
        "--prepare-approval-digest",
        action="store_true",
        help=(
            "print an approval digest candidate only when explicit approved-state "
            "metadata is complete and reviewed_content_sha256 is the sole missing value"
        ),
    )
    mode.add_argument(
        "--require-approved-review",
        action="store_true",
        help="fail unless the recorded review is approved, valid, and digest-bound",
    )
    parser.add_argument(
        "--print-review-digest",
        action="store_true",
        help=argparse.SUPPRESS,
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.print_review_digest:
        print(
            "[FAIL] --print-review-digest is retired because it could emit an "
            "approval-looking value for pending or invalid content; use "
            "--diagnostic-pending-digest or --prepare-approval-digest"
        )
        return 2

    errors, details = validate_bootstrap_provenance(
        repository_root=args.repository_root,
        manifest_path=args.manifest,
    )

    if args.prepare_approval_digest:
        candidate_ready = (
            details["review_status"] == APPROVED_RECORD_STATUS
            and details["review_content_sha256"] != "UNKNOWN"
            and errors == [MISSING_APPROVAL_DIGEST_ERROR]
            and _manifest_has_null_review_digest(
                args.repository_root, args.manifest
            )
        )
        if candidate_ready:
            _print_mechanical_pass(details)
            print(
                "[CANDIDATE] approval_candidate_reviewed_content_sha256: "
                f"{details['review_content_sha256']}"
            )
            print(
                "[CANDIDATE] This command changes no files and records no approval; "
                "a human must review and explicitly apply the candidate digest"
            )
            return 0
        for error in errors:
            print(f"[FAIL] {error}")
        print(
            "[FAIL] --prepare-approval-digest requires an otherwise-valid "
            f"{APPROVED_RECORD_STATUS} candidate whose required "
            "reviewed_content_sha256 field is explicitly null"
        )
        return 1

    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1

    _print_mechanical_pass(details)
    if args.diagnostic_pending_digest:
        if details["review_status"] != PENDING_RECORD_STATUS:
            _print_review_state(details)
            print(
                "[FAIL] --diagnostic-pending-digest requires a valid pending review"
            )
            return 1
        print(
            "[DIAGNOSTIC] pending_review_content_sha256: "
            f"{details['review_content_sha256']}"
        )
        print(
            "[DIAGNOSTIC] This value describes pending content only; it must not "
            "be copied into approval metadata"
        )
        _print_review_state(details)
        return 0

    _print_review_state(details)
    if (
        args.require_approved_review
        and details["review_status"] != APPROVED_RECORD_STATUS
    ):
        print(
            "[FAIL] --require-approved-review requires a valid, digest-bound "
            f"{APPROVED_RECORD_STATUS} record"
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
