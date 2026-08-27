#!/usr/bin/env python3
"""Create, collect, and validate privacy-reviewed v0.0.2 client evidence."""

from __future__ import annotations

import argparse
import copy
import datetime as dt
import hashlib
import ipaddress
import json
import os
import re
import shutil
import struct
import sys
import tempfile
import zlib
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
VERSION = "v0.0.2"
SCHEMA_VERSION = 2
CONTENT_MANIFEST = Path(
    "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json"
)
COMMITTED_BUNDLE = Path("docs/releases/v0.0.2/evidence/client")
RECORD_NAME = "manual-evidence.json"
MAX_JSON_BYTES = 1024 * 1024
MAX_PNG_BYTES = 16 * 1024 * 1024
MAX_SCREENSHOT_TOTAL = 40 * 1024 * 1024
MAX_PNG_PIXELS = 4096 * 4096
MAX_PNG_CHUNKS = 128
MAX_PNG_CHUNK_BYTES = 16 * 1024 * 1024
MIN_PNG_WIDTH = 640
MIN_PNG_HEIGHT = 360
MAX_LOG_BYTES = 32 * 1024 * 1024
MAX_EXCERPT_LINES = 200
MAX_EXCERPT_BYTES = 64 * 1024
HASH_CHUNK_SIZE = 1024 * 1024
SHA256_RE = re.compile(r"[0-9a-f]{64}")
COMMIT_RE = re.compile(r"[0-9a-f]{40}")
IDENTIFIER_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{2,63}")
PLAYER_NAME_RE = re.compile(r"[A-Za-z0-9_]{3,16}")
UUID_RE = re.compile(
    r"(?i)(?<![0-9a-f])(?:[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}|"
    r"[0-9a-f]{32})(?![0-9a-f])"
)
HOME_RE = re.compile(
    r"(?i)(?:[A-Z]:[\\/]Users[\\/][^\\/\s]+|/(?:home|Users)/[^/\s]+)"
)
IPV4_RE = re.compile(r"(?<![0-9.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9.])")
IPV6_RE = re.compile(
    r"(?i)(?<![0-9a-f:])(?:\[[0-9a-f:]+\]|(?:[0-9a-f]{1,4}:){2,7}"
    r"[0-9a-f:]{0,4})(?![0-9a-f:])"
)
CREDENTIAL_PATTERNS = (
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b"),
    re.compile(
        r"(?i)\b(?:access[_-]?token|authorization|password|passwd|secret)"
        r"\s*[:=]\s*(?:Bearer\s+)?[^\s,;]+"
    ),
)
PRIVACY_CATEGORIES = ("player_name", "uuid", "credential", "home", "ip")
ERROR_LINE = re.compile(r"\[[^\]\r\n]+/ERROR\]")
WARNING_LINE = re.compile(r"\[[^\]\r\n]+/WARN\]")
PROJECT_LOGGER = re.compile(
    r"\[[^\]\r\n]*advancedrocketrycommunity[^\]\r\n]*/[^\]\r\n]*\]",
    re.I,
)
CLIENT_LINKAGE_MARKERS = (
    "Attempted to load class net/minecraft/client",
    "NoClassDefFoundError: net/minecraft/client",
    "NoClassDefFoundError: net.minecraft.client",
    "ClassNotFoundException: net.minecraft.client",
)
LOG_AUDIT_FIELDS = (
    "error_count",
    "warning_count",
    "project_error_count",
    "project_warning_count",
    "client_linkage_failure_count",
)
SERVER_SUMMARY_SCHEMA_VERSION = 2
SERVER_SUMMARY_ARCHIVE = "server/server-summary.json"
SESSION_ID_RE = re.compile(r"v002-[0-9a-f]{24}")

OBSERVATIONS = {
    "MANUAL-V002-001": (
        "The current rendered README and packaged-client metadata match the "
        "approved identity, and the client enters a disposable single-player "
        "world without a project-source ERROR."
    ),
    "MANUAL-V002-002": (
        "The matching packaged client joins the loopback server, disconnects, "
        "and reconnects after a clean same-world restart without a project-source "
        "ERROR or client-class linkage failure."
    ),
    "MANUAL-V002-003": (
        "The missing-project-mod client exposes the actual MATCH_VERSION indicator, "
        "message, and connection result without a prefilled verdict."
    ),
}

SCREENSHOT_ROLES = {
    "readme_render": "MANUAL-V002-001",
    "mods_page": "MANUAL-V002-001",
    "singleplayer_world": "MANUAL-V002-001",
    "matching_server_list": "MANUAL-V002-002",
    "matching_first_join": "MANUAL-V002-002",
    "matching_reconnect": "MANUAL-V002-002",
    "missing_mod_server_list": "MANUAL-V002-003",
}
LOG_ROLES = {
    "client_startup_world": "MANUAL-V002-001",
    "matching_client_connection": "MANUAL-V002-002",
    "server_first_join_leave_save_stop": "MANUAL-V002-002",
    "server_restart_reconnect_save_stop": "MANUAL-V002-002",
    "mismatch_attempt": "MANUAL-V002-003",
}
ROLE_MAP = {**SCREENSHOT_ROLES, **LOG_ROLES}
FINDING_FIELDS = (
    "client_project_error_count",
    "client_project_warning_count",
    "server_project_error_count",
    "server_project_warning_count",
    "client_class_linkage_failure_count",
)
APPLICABILITY = {
    "two_player_consistency": (
        "v0.0.2 registers no playable content, project packets, shared player state, "
        "permissions, inventories, or interactions; there is no two-player project "
        "state whose consistency can be compared in this milestone."
    ),
    "optional_client_dependency_absence": (
        "v0.0.2 declares no optional runtime or client-only dependency. The clean "
        "packaged profile intentionally contains only Forge and the project JAR, so "
        "no optional client dependency can be removed for a distinct test case."
    ),
}
SCOPE_STATEMENT = (
    "This package records v0.0.2 evidence for human review; it does not set or "
    "approve any release Gate."
)
PNG_PRIVACY_CHUNKS = {b"tEXt", b"zTXt", b"iTXt", b"eXIf", b"tIME"}
PNG_CRITICAL_CHUNKS = {b"IHDR", b"PLTE", b"IDAT", b"IEND"}
PNG_ALLOWED_ANCILLARY = {b"cHRM": 32, b"gAMA": 4, b"sRGB": 1, b"tRNS": 6}
SERVER_LOG_CYCLES = {
    "server_first_join_leave_save_stop": "first-start",
    "server_restart_reconnect_save_stop": "restart",
}
CLIENT_LOG_ROLES = {
    "client_startup_world",
    "matching_client_connection",
    "mismatch_attempt",
}
LIFECYCLE_MARKERS = {
    "server_first_join_leave_save_stop": (
        ("join", "joined the game"),
        ("leave", "left the game"),
        ("save", "Saved the game"),
        ("stop", "Stopping server"),
    ),
    "server_restart_reconnect_save_stop": (
        ("ready", "Done ("),
        ("join", "joined the game"),
        ("leave", "left the game"),
        ("save", "Saved the game"),
        ("stop", "Stopping server"),
    ),
}


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            chunk = stream.read(HASH_CHUNK_SIZE)
            if not chunk:
                return digest.hexdigest()
            digest.update(chunk)


def _duplicates_rejected(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json(path: Path) -> Any:
    if path.stat().st_size > MAX_JSON_BYTES:
        raise ValueError(f"JSON file exceeds {MAX_JSON_BYTES} bytes: {path}")
    return json.loads(
        path.read_text(encoding="utf-8"), object_pairs_hook=_duplicates_rejected
    )


def write_json(path: Path, document: Any) -> None:
    path.write_text(
        json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _is_link(path: Path) -> bool:
    junction = getattr(path, "is_junction", None)
    return path.is_symlink() or bool(junction and junction())


def _reject_link_components(path: Path, build_root: Path) -> None:
    relative = path.relative_to(build_root)
    current = build_root
    if current.exists() and _is_link(current):
        raise ValueError(f"build directory must not be a symlink or junction: {current}")
    for part in relative.parts:
        current /= part
        if current.exists() and _is_link(current):
            raise ValueError(f"path must not contain a symlink or junction: {current}")


def resolve_build_path(
    value: str | Path,
    repository_root: Path,
    *,
    must_exist: bool,
    require_file: bool = False,
) -> Path:
    if not isinstance(value, (str, Path)) or not str(value):
        raise ValueError("path must be a non-empty string")
    text = str(value)
    if any(ord(character) < 32 or ord(character) == 127 for character in text):
        raise ValueError("path must not contain control characters")
    raw = Path(text)
    if ".." in raw.parts:
        raise ValueError(f"path traversal is not allowed: {text}")

    root = repository_root.resolve()
    build_anchor = (root / "build").absolute()
    build_root = build_anchor.resolve(strict=False)
    candidate = raw if raw.is_absolute() else root / raw
    lexical = candidate.absolute()
    try:
        lexical.relative_to(build_anchor)
    except ValueError as exc:
        raise ValueError(
            f"path must remain under the repository build directory: {text}"
        ) from exc
    _reject_link_components(lexical, build_anchor)
    resolved = candidate.resolve(strict=must_exist)
    try:
        resolved.relative_to(build_root)
    except ValueError as exc:
        raise ValueError(f"path must remain under the repository build directory: {text}") from exc
    if must_exist and require_file and not resolved.is_file():
        raise ValueError(f"path must name a regular file: {text}")
    return resolved


def resolve_bundle_path(
    value: str | Path,
    repository_root: Path,
    *,
    must_exist: bool,
) -> tuple[Path, str]:
    if not isinstance(value, (str, Path)) or not str(value):
        raise ValueError("bundle path must be a non-empty string")
    text = str(value)
    if any(ord(character) < 32 or ord(character) == 127 for character in text):
        raise ValueError("bundle path must not contain control characters")
    raw = Path(text)
    if ".." in raw.parts:
        raise ValueError(f"bundle path traversal is not allowed: {text}")
    root = repository_root.resolve()
    candidate = raw if raw.is_absolute() else root / raw
    resolved = candidate.resolve(strict=must_exist)
    build_root = (root / "build").resolve(strict=False)
    committed = root / COMMITTED_BUNDLE
    try:
        resolved.relative_to(build_root)
        mode = "build"
        anchor = root / "build"
    except ValueError:
        if resolved != committed:
            raise ValueError(
                "bundle must be under build/ or exactly "
                + COMMITTED_BUNDLE.as_posix()
            )
        mode = "committed"
        anchor = root
    _reject_link_components(candidate.absolute(), anchor.absolute())
    return resolved, mode


def validate_recorded_build_path(value: Any) -> str:
    if not isinstance(value, str) or not value:
        raise ValueError("recorded artifact path must be a non-empty string")
    if value != value.strip() or "\\" in value:
        raise ValueError("recorded artifact path must use normalized POSIX separators")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise ValueError("recorded artifact path contains control characters")
    posix = PurePosixPath(value)
    windows = PureWindowsPath(value)
    if posix.is_absolute() or windows.is_absolute() or windows.drive:
        raise ValueError("recorded artifact path must be repository-relative")
    parts = value.split("/")
    if any(part in ("", ".", "..") for part in parts) or parts[0] != "build":
        raise ValueError("recorded artifact path must be a safe path below build/")
    if any(":" in part or part.endswith((" ", ".")) for part in parts):
        raise ValueError("recorded artifact path is not portable across test systems")
    if posix.as_posix() != value:
        raise ValueError("recorded artifact path is not normalized")
    return value


def relative_build_path(path: Path, repository_root: Path) -> str:
    return path.resolve().relative_to(repository_root.resolve()).as_posix()


def load_content_manifest(repository_root: Path) -> tuple[Path, dict[str, str]]:
    path = (repository_root.resolve() / CONTENT_MANIFEST).resolve()
    expected = repository_root.resolve() / CONTENT_MANIFEST
    if path != expected or _is_link(expected) or not path.is_file():
        raise ValueError(f"committed content manifest is missing or unsafe: {CONTENT_MANIFEST}")
    document = load_json(path)
    if not isinstance(document, dict):
        raise ValueError("committed content manifest must contain a JSON object")
    filename = document.get("artifact")
    checksum = document.get("artifact_sha256")
    if not isinstance(filename, str) or not filename.endswith(".jar"):
        raise ValueError("committed content manifest has no artifact filename")
    if (
        PurePosixPath(filename).name != filename
        or PureWindowsPath(filename).name != filename
    ):
        raise ValueError("committed artifact must be a plain JAR filename")
    if not isinstance(checksum, str) or SHA256_RE.fullmatch(checksum) is None:
        raise ValueError("committed content manifest has no lowercase artifact SHA-256")
    return path, {"filename": filename, "sha256": checksum}


def _redact_ip(match: re.Match[str], counts: dict[str, int]) -> str:
    candidate = match.group(0)
    unwrapped = candidate[1:-1] if candidate.startswith("[") else candidate
    try:
        address = ipaddress.ip_address(unwrapped)
    except ValueError:
        return candidate
    if address.is_loopback:
        return candidate
    counts["ip"] += 1
    return "[REDACTED_NON_LOOPBACK_IP]"


def redact_text(text: str, player_names: list[str]) -> tuple[str, dict[str, int]]:
    counts = {category: 0 for category in PRIVACY_CATEGORIES}
    result = text
    for pattern in CREDENTIAL_PATTERNS:
        result, count = pattern.subn("[REDACTED_CREDENTIAL]", result)
        counts["credential"] += count
    result, counts["home"] = HOME_RE.subn("[REDACTED_HOME]", result)
    result, counts["uuid"] = UUID_RE.subn("[REDACTED_UUID]", result)
    result = IPV4_RE.sub(lambda match: _redact_ip(match, counts), result)
    result = IPV6_RE.sub(lambda match: _redact_ip(match, counts), result)
    for player_name in sorted(player_names, key=len, reverse=True):
        pattern = re.compile(
            rf"(?i)(?<![A-Za-z0-9_]){re.escape(player_name)}(?![A-Za-z0-9_])"
        )
        result, count = pattern.subn("[REDACTED_TEST_PLAYER]", result)
        counts["player_name"] += count
    return result, counts


def privacy_findings(text: str, player_names: list[str] | None = None) -> list[str]:
    findings: list[str] = []
    if UUID_RE.search(text):
        findings.append("UUID")
    if HOME_RE.search(text):
        findings.append("user-home path")
    if any(pattern.search(text) for pattern in CREDENTIAL_PATTERNS):
        findings.append("credential-like value")
    for match in (*IPV4_RE.finditer(text), *IPV6_RE.finditer(text)):
        candidate = match.group(0).strip("[]")
        try:
            if not ipaddress.ip_address(candidate).is_loopback:
                findings.append("non-loopback IP address")
                break
        except ValueError:
            continue
    for player_name in player_names or []:
        if re.search(
            rf"(?i)(?<![A-Za-z0-9_]){re.escape(player_name)}(?![A-Za-z0-9_])",
            text,
        ):
            findings.append("player name")
            break
    return findings


def scan_log_text(text: str) -> dict[str, int]:
    counts = {field: 0 for field in LOG_AUDIT_FIELDS}
    for line in text.splitlines():
        is_project = PROJECT_LOGGER.search(line) is not None
        if ERROR_LINE.search(line):
            counts["error_count"] += 1
            if is_project:
                counts["project_error_count"] += 1
        if WARNING_LINE.search(line):
            counts["warning_count"] += 1
            if is_project:
                counts["project_warning_count"] += 1
        lowered = line.casefold()
        if any(marker.casefold() in lowered for marker in CLIENT_LINKAGE_MARKERS):
            counts["client_linkage_failure_count"] += 1
    return counts


def scan_log_file(path: Path) -> tuple[dict[str, int], str, int]:
    size = path.stat().st_size
    if size > MAX_LOG_BYTES:
        raise ValueError(f"log source exceeds {MAX_LOG_BYTES} bytes: {path}")
    text = path.read_text(encoding="utf-8", errors="strict")
    return scan_log_text(text), file_sha256(path), size


def _chunk(chunk_type: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(
        ">I", zlib.crc32(chunk_type + data) & 0xFFFFFFFF
    )


def inspect_png(path: Path) -> dict[str, Any]:
    size = path.stat().st_size
    if size > MAX_PNG_BYTES:
        raise ValueError(f"PNG exceeds {MAX_PNG_BYTES} bytes: {path}")
    content = path.read_bytes()
    if not content.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"invalid PNG signature: {path}")

    offset = 8
    chunks: list[bytes] = []
    idat_parts: list[bytes] = []
    width = height = 0
    channels = 0
    color_type = -1
    idat_ended = False
    while offset < len(content):
        if len(chunks) >= MAX_PNG_CHUNKS:
            raise ValueError(f"PNG exceeds {MAX_PNG_CHUNKS} chunks: {path}")
        if b"IEND" in chunks:
            raise ValueError(f"PNG contains data after IEND: {path}")
        if len(content) - offset < 12:
            raise ValueError(f"truncated PNG chunk: {path}")
        length = struct.unpack(">I", content[offset : offset + 4])[0]
        if length > MAX_PNG_CHUNK_BYTES:
            raise ValueError(
                f"PNG chunk exceeds {MAX_PNG_CHUNK_BYTES} bytes: {path}"
            )
        chunk_type = content[offset + 4 : offset + 8]
        end = offset + 12 + length
        if end > len(content):
            raise ValueError(f"truncated PNG chunk data: {path}")
        data = content[offset + 8 : offset + 8 + length]
        expected_crc = struct.unpack(">I", content[offset + 8 + length : end])[0]
        actual_crc = zlib.crc32(chunk_type + data) & 0xFFFFFFFF
        if expected_crc != actual_crc:
            raise ValueError(f"PNG CRC mismatch in {chunk_type!r}: {path}")
        if not re.fullmatch(rb"[A-Za-z]{4}", chunk_type):
            raise ValueError(f"invalid PNG chunk type: {path}")
        if chunk_type[0] & 0x20 == 0 and chunk_type not in PNG_CRITICAL_CHUNKS:
            raise ValueError(f"unknown critical PNG chunk {chunk_type!r}: {path}")
        if chunk_type in PNG_PRIVACY_CHUNKS:
            raise ValueError(f"privacy-bearing PNG metadata {chunk_type.decode()}: {path}")
        if chunk_type[0] & 0x20:
            expected_length = PNG_ALLOWED_ANCILLARY.get(chunk_type)
            if expected_length is None:
                raise ValueError(
                    f"unknown or nonessential PNG ancillary chunk "
                    f"{chunk_type.decode()}: {path}"
                )
            if length != expected_length:
                raise ValueError(
                    f"PNG ancillary chunk {chunk_type.decode()} has invalid size: {path}"
                )
            if chunk_type in chunks:
                raise ValueError(
                    f"PNG ancillary chunk {chunk_type.decode()} must be unique: {path}"
                )
            if b"IDAT" in chunks:
                raise ValueError(
                    f"PNG ancillary chunk {chunk_type.decode()} must precede IDAT: {path}"
                )
        chunks.append(chunk_type)
        if chunk_type == b"IDAT":
            if idat_ended:
                raise ValueError(f"PNG IDAT chunks must be consecutive: {path}")
            idat_parts.append(data)
        elif idat_parts:
            idat_ended = True
        if len(chunks) == 1:
            if chunk_type != b"IHDR" or length != 13:
                raise ValueError(f"PNG must start with a 13-byte IHDR: {path}")
            width, height, depth, color_type, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", data
            )
            if depth != 8 or color_type not in (2, 6):
                raise ValueError(f"PNG must be 8-bit RGB or RGBA: {path}")
            channels = 3 if color_type == 2 else 4
            if compression != 0 or filtering != 0 or interlace != 0:
                raise ValueError(f"unsupported PNG encoding flags: {path}")
        elif chunk_type == b"PLTE":
            raise ValueError(
                f"PNG PLTE is nonessential for RGB/RGBA evidence captures: {path}"
            )
        elif chunk_type == b"tRNS" and color_type != 2:
            raise ValueError(f"PNG tRNS is only allowed for RGB captures: {path}")
        offset = end

    if not chunks or chunks[-1] != b"IEND" or content[-12:] != _chunk(b"IEND", b""):
        raise ValueError(f"PNG must end with one IEND chunk: {path}")
    if chunks.count(b"IHDR") != 1 or chunks.count(b"IEND") != 1 or b"IDAT" not in chunks:
        raise ValueError(f"PNG is missing required unique chunks: {path}")
    if width < MIN_PNG_WIDTH or height < MIN_PNG_HEIGHT:
        raise ValueError(
            f"PNG dimensions must be at least {MIN_PNG_WIDTH}x{MIN_PNG_HEIGHT}: "
            f"{width}x{height}"
        )
    if width * height > MAX_PNG_PIXELS:
        raise ValueError(f"PNG pixel count exceeds {MAX_PNG_PIXELS}: {path}")
    expected_decoded = height * (1 + width * channels)
    decompressor = zlib.decompressobj()
    decoded = decompressor.decompress(b"".join(idat_parts), expected_decoded + 1)
    if (
        len(decoded) != expected_decoded
        or not decompressor.eof
        or decompressor.unconsumed_tail
        or decompressor.unused_data
    ):
        raise ValueError(f"PNG IDAT stream does not match declared dimensions: {path}")
    row_stride = 1 + width * channels
    if any(decoded[offset] > 4 for offset in range(0, len(decoded), row_stride)):
        raise ValueError(f"PNG contains an invalid row filter: {path}")
    ancillary = sorted({chunk.decode() for chunk in chunks if chunk[0] & 0x20})
    return {
        "width": width,
        "height": height,
        "size": size,
        "metadata_chunks": ancillary,
        "sha256": hashlib.sha256(content).hexdigest(),
        "content": content,
    }


def extract_log_excerpt(
    path: Path,
    line_start: int,
    line_end: int,
    player_names: list[str],
) -> tuple[bytes, dict[str, int]]:
    if path.stat().st_size > MAX_LOG_BYTES:
        raise ValueError(f"log source exceeds {MAX_LOG_BYTES} bytes: {path}")
    if line_start < 1 or line_end < line_start:
        raise ValueError("log line range must be positive and ordered")
    if line_end - line_start + 1 > MAX_EXCERPT_LINES:
        raise ValueError(f"log excerpt exceeds {MAX_EXCERPT_LINES} lines")

    selected: list[str] = []
    with path.open("r", encoding="utf-8", errors="strict", newline=None) as stream:
        for line_number, line in enumerate(stream, start=1):
            if line_number > line_end:
                break
            if line_number >= line_start:
                selected.append(line.rstrip("\r\n"))
    if len(selected) != line_end - line_start + 1:
        raise ValueError(f"log does not contain requested line range: {path}")
    text, counts = redact_text("\n".join(selected) + "\n", player_names)
    encoded = text.encode("utf-8")
    if len(encoded) > MAX_EXCERPT_BYTES:
        raise ValueError(f"redacted log excerpt exceeds {MAX_EXCERPT_BYTES} bytes")
    findings = privacy_findings(text, player_names)
    if findings:
        raise ValueError("redacted log still contains: " + ", ".join(findings))
    return encoded, counts


def lifecycle_marker_result(role: str, text: str) -> dict[str, Any] | None:
    markers = LIFECYCLE_MARKERS.get(role)
    if markers is None:
        return None
    positions: dict[str, int] = {}
    cursor = 0
    valid = True
    for label, marker in markers:
        position = text.find(marker, cursor)
        positions[label] = position
        if position < 0:
            valid = False
        else:
            cursor = position + len(marker)
    return {"order_valid": valid, "positions": positions}


def build_template(artifact_filename: str) -> dict[str, Any]:
    default_actual = "Not executed; replace with the observed result or blocking reason."
    return {
        "schema_version": SCHEMA_VERSION,
        "version": VERSION,
        "metadata": {
            "source_commit": "",
            "test_date": "",
            "tester_id": "",
            "environment": {
                "os": "",
                "java": "",
                "minecraft": "1.20.1",
                "forge": "47.4.10",
            },
        },
        "artifacts": {
            "source": f"build/libs/{artifact_filename}",
            "server": f"build/v0.0.2-manual/server/mods/{artifact_filename}",
            "client": f"build/v0.0.2-manual/client/mods/{artifact_filename}",
        },
        "server_harness": {
            "status": "MISSING",
            "summary": "",
            "note": "No harness-generated manual player-cycle summary captured.",
        },
        "privacy": {
            "player_names": [],
            "visual_review": {
                "completed": False,
                "reviewed_by": "",
                "reviewed_at": "",
                "notes": "",
            },
        },
        "observations": {
            key: {"outcome": "BLOCKED", "expected": expected, "actual": default_actual}
            for key, expected in OBSERVATIONS.items()
        },
        "evidence": {
            role: {"status": "MISSING", "source": "", "note": "Not captured."}
            for role in SCREENSHOT_ROLES
        },
        "log_excerpts": {
            role: {
                "status": "MISSING",
                "source": "",
                "line_start": 1,
                "line_end": 1,
                "note": "Not captured.",
            }
            for role in LOG_ROLES
        },
        "findings": {**{key: None for key in FINDING_FIELDS}, "notes": ""},
        "applicability_reviews": {
            key: {
                "proposed_status": "NOT_APPLICABLE",
                "rationale": rationale,
                "decision": "PENDING",
                "reviewed_by": "",
                "reviewed_at": "",
                "notes": "",
            }
            for key, rationale in APPLICABILITY.items()
        },
    }


def _exact_keys(value: Any, expected: set[str], label: str, errors: list[str]) -> dict[str, Any]:
    if not isinstance(value, dict):
        errors.append(f"{label} must be an object")
        return {}
    missing = sorted(expected - value.keys())
    extra = sorted(value.keys() - expected)
    if missing:
        errors.append(f"{label} is missing keys: {', '.join(missing)}")
    if extra:
        errors.append(f"{label} has unexpected keys: {', '.join(extra)}")
    return value


def _valid_date(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    try:
        return dt.date.fromisoformat(value).isoformat() == value
    except ValueError:
        return False


def validate_session(document: Any) -> list[str]:
    errors: list[str] = []
    top = _exact_keys(
        document,
        {
            "schema_version",
            "version",
            "metadata",
            "artifacts",
            "server_harness",
            "privacy",
            "observations",
            "evidence",
            "log_excerpts",
            "findings",
            "applicability_reviews",
        },
        "session",
        errors,
    )
    if top.get("schema_version") != SCHEMA_VERSION or top.get("version") != VERSION:
        errors.append(f"session must use schema {SCHEMA_VERSION} and version {VERSION}")

    metadata = _exact_keys(
        top.get("metadata"), {"source_commit", "test_date", "tester_id", "environment"},
        "metadata", errors,
    )
    if not isinstance(metadata.get("source_commit"), str) or COMMIT_RE.fullmatch(
        metadata.get("source_commit", "")
    ) is None:
        errors.append("metadata.source_commit must be a lowercase 40-character commit")
    if not _valid_date(metadata.get("test_date")):
        errors.append("metadata.test_date must be an ISO date")
    if not isinstance(metadata.get("tester_id"), str) or IDENTIFIER_RE.fullmatch(
        metadata.get("tester_id", "")
    ) is None:
        errors.append("metadata.tester_id must be a 3-64 character review identifier")
    environment = _exact_keys(
        metadata.get("environment"), {"os", "java", "minecraft", "forge"},
        "metadata.environment", errors,
    )
    for key in ("os", "java"):
        if not isinstance(environment.get(key), str) or not environment.get(key).strip():
            errors.append(f"metadata.environment.{key} must be non-empty")
    if isinstance(environment.get("java"), str) and re.search(
        r"(?<!\d)17(?:[.\s]|$)", environment["java"]
    ) is None:
        errors.append("metadata.environment.java must identify Java 17")
    if environment.get("minecraft") != "1.20.1" or environment.get("forge") != "47.4.10":
        errors.append("environment must use Minecraft 1.20.1 and Forge 47.4.10")

    artifacts = _exact_keys(top.get("artifacts"), {"source", "server", "client"}, "artifacts", errors)
    for role, value in artifacts.items():
        if not isinstance(value, str) or not value:
            errors.append(f"artifacts.{role} must be a non-empty path")

    server_harness = _exact_keys(
        top.get("server_harness"),
        {"status", "summary", "note"},
        "server_harness",
        errors,
    )
    if server_harness.get("status") not in {"PRESENT", "MISSING"}:
        errors.append("server_harness.status must be PRESENT or MISSING")
    if not isinstance(server_harness.get("summary"), str) or not isinstance(
        server_harness.get("note"), str
    ):
        errors.append("server_harness summary and note must be strings")
    elif server_harness.get("status") == "PRESENT" and not server_harness["summary"]:
        errors.append("server_harness PRESENT requires a summary path")
    elif server_harness.get("status") == "MISSING" and (
        server_harness["summary"] or not server_harness["note"].strip()
    ):
        errors.append("server_harness MISSING requires an empty summary and reason")

    privacy = _exact_keys(top.get("privacy"), {"player_names", "visual_review"}, "privacy", errors)
    names = privacy.get("player_names")
    if not isinstance(names, list) or len(names) > 8:
        errors.append("privacy.player_names must be a list of at most 8 names")
        names = []
    elif any(not isinstance(name, str) or PLAYER_NAME_RE.fullmatch(name) is None for name in names):
        errors.append("privacy.player_names entries must be 3-16 Minecraft-name characters")
    elif len({name.casefold() for name in names}) != len(names):
        errors.append("privacy.player_names must not contain duplicates")
    if isinstance(metadata.get("tester_id"), str) and any(
        metadata["tester_id"].casefold() == name.casefold() for name in names
    ):
        errors.append("tester_id must not reuse a Minecraft player name")
    visual = _exact_keys(
        privacy.get("visual_review"), {"completed", "reviewed_by", "reviewed_at", "notes"},
        "privacy.visual_review", errors,
    )
    if not isinstance(visual.get("completed"), bool):
        errors.append("privacy.visual_review.completed must be boolean")
    for key in ("reviewed_by", "reviewed_at", "notes"):
        if not isinstance(visual.get(key), str):
            errors.append(f"privacy.visual_review.{key} must be a string")
    if visual.get("completed") and (
        IDENTIFIER_RE.fullmatch(visual.get("reviewed_by", "")) is None
        or not _valid_date(visual.get("reviewed_at"))
        or not visual.get("notes", "").strip()
    ):
        errors.append("completed visual review requires reviewer, date, and notes")

    observations = _exact_keys(top.get("observations"), set(OBSERVATIONS), "observations", errors)
    for key, expected in OBSERVATIONS.items():
        item = _exact_keys(observations.get(key), {"outcome", "expected", "actual"}, f"observations.{key}", errors)
        if item.get("outcome") not in {"PASS", "FAIL", "BLOCKED"}:
            errors.append(f"observations.{key}.outcome must be PASS, FAIL, or BLOCKED")
        if item.get("expected") != expected:
            errors.append(f"observations.{key}.expected must retain the fixed criterion")
        if not isinstance(item.get("actual"), str) or not item.get("actual", "").strip() or len(item.get("actual", "")) > 4000:
            errors.append(f"observations.{key}.actual must be 1-4000 characters")

    evidence = _exact_keys(top.get("evidence"), set(SCREENSHOT_ROLES), "evidence", errors)
    logs = _exact_keys(top.get("log_excerpts"), set(LOG_ROLES), "log_excerpts", errors)
    for collection, roles, label in ((evidence, SCREENSHOT_ROLES, "evidence"), (logs, LOG_ROLES, "log_excerpts")):
        for role in roles:
            keys = {"status", "source", "note"} | ({"line_start", "line_end"} if label == "log_excerpts" else set())
            item = _exact_keys(collection.get(role), keys, f"{label}.{role}", errors)
            if item.get("status") not in {"PRESENT", "MISSING"}:
                errors.append(f"{label}.{role}.status must be PRESENT or MISSING")
            if not isinstance(item.get("source"), str) or not isinstance(item.get("note"), str):
                errors.append(f"{label}.{role} source and note must be strings")
            elif item.get("status") == "PRESENT" and not item["source"]:
                errors.append(f"{label}.{role} PRESENT requires a source")
            elif item.get("status") == "MISSING" and (item["source"] or not item["note"].strip()):
                errors.append(f"{label}.{role} MISSING requires an empty source and reason")
            if label == "log_excerpts" and (
                not isinstance(item.get("line_start"), int)
                or isinstance(item.get("line_start"), bool)
                or not isinstance(item.get("line_end"), int)
                or isinstance(item.get("line_end"), bool)
            ):
                errors.append(f"log_excerpts.{role} line bounds must be integers")
            elif label == "log_excerpts" and (
                item["line_start"] < 1
                or item["line_end"] < item["line_start"]
                or item["line_end"] - item["line_start"] + 1 > MAX_EXCERPT_LINES
            ):
                errors.append(
                    f"log_excerpts.{role} must select 1-{MAX_EXCERPT_LINES} ordered lines"
                )

    findings = _exact_keys(top.get("findings"), set(FINDING_FIELDS) | {"notes"}, "findings", errors)
    for key in FINDING_FIELDS:
        value = findings.get(key)
        if value is not None and (not isinstance(value, int) or isinstance(value, bool) or value < 0):
            errors.append(f"findings.{key} must be null or a non-negative integer")
    if not isinstance(findings.get("notes"), str):
        errors.append("findings.notes must be a string")
    if any(isinstance(findings.get(key), int) and findings[key] > 0 for key in FINDING_FIELDS) and not findings.get("notes", "").strip():
        errors.append("non-zero finding counts require findings.notes")

    reviews = _exact_keys(top.get("applicability_reviews"), set(APPLICABILITY), "applicability_reviews", errors)
    for key, rationale in APPLICABILITY.items():
        review = _exact_keys(
            reviews.get(key), {"proposed_status", "rationale", "decision", "reviewed_by", "reviewed_at", "notes"},
            f"applicability_reviews.{key}", errors,
        )
        if review.get("proposed_status") != "NOT_APPLICABLE" or review.get("rationale") != rationale:
            errors.append(f"applicability_reviews.{key} must retain the fixed proposal")
        if review.get("decision") not in {
            "PENDING",
            "ACCEPT_NOT_APPLICABLE",
            "REQUIRE_ADDITIONAL_TEST",
        }:
            errors.append(f"applicability_reviews.{key}.decision is invalid")
        for field in ("reviewed_by", "reviewed_at", "notes"):
            if not isinstance(review.get(field), str):
                errors.append(f"applicability_reviews.{key}.{field} must be a string")
        if review.get("decision") != "PENDING" and (
            IDENTIFIER_RE.fullmatch(review.get("reviewed_by", "")) is None
            or not _valid_date(review.get("reviewed_at"))
            or not review.get("notes", "").strip()
        ):
            errors.append(f"applicability_reviews.{key} decision requires reviewer, date, and notes")

    for observation, item in observations.items():
        if not isinstance(item, dict):
            continue
        present = [
            role for role, owner in ROLE_MAP.items()
            if owner == observation
            and isinstance((evidence if role in evidence else logs).get(role), dict)
            and (evidence if role in evidence else logs)[role].get("status") == "PRESENT"
        ]
        if item.get("outcome") == "PASS":
            missing = sorted(role for role, owner in ROLE_MAP.items() if owner == observation and role not in present)
            if missing:
                errors.append(f"{observation} cannot claim PASS with missing roles: {', '.join(missing)}")
            screenshot_sources = [
                evidence[role].get("source")
                for role, owner in SCREENSHOT_ROLES.items()
                if owner == observation and role in evidence
            ]
            if len(screenshot_sources) != len(set(screenshot_sources)):
                errors.append(
                    f"{observation} PASS cannot reuse one screenshot source for multiple roles"
                )
            log_ranges = [
                (
                    logs[role].get("source"),
                    logs[role].get("line_start"),
                    logs[role].get("line_end"),
                )
                for role, owner in LOG_ROLES.items()
                if owner == observation and role in logs
            ]
            if len(log_ranges) != len(set(log_ranges)):
                errors.append(
                    f"{observation} PASS cannot reuse one log range for multiple roles"
                )
        if item.get("outcome") == "FAIL" and not present:
            errors.append(f"{observation} FAIL requires at least one evidence role")
        if item.get("outcome") in {"PASS", "FAIL"} and item.get("actual") == "Not executed; replace with the observed result or blocking reason.":
            errors.append(f"{observation} must replace the template actual result")
    if observations.get("MANUAL-V002-001", {}).get("outcome") == "PASS" and findings.get("client_project_error_count") not in (0,):
        errors.append("MANUAL-V002-001 PASS requires client_project_error_count 0")
    if observations.get("MANUAL-V002-002", {}).get("outcome") == "PASS" and (
        findings.get("server_project_error_count") not in (0,)
        or findings.get("client_class_linkage_failure_count") not in (0,)
    ):
        errors.append("MANUAL-V002-002 PASS requires server ERROR and linkage counts 0")
    if (
        observations.get("MANUAL-V002-002", {}).get("outcome") == "PASS"
        and server_harness.get("status") != "PRESENT"
    ):
        errors.append("MANUAL-V002-002 PASS requires a harness-generated server summary")
    return errors


def _aware_timestamp(value: Any) -> dt.datetime | None:
    if not isinstance(value, str):
        return None
    try:
        parsed = dt.datetime.fromisoformat(value)
    except ValueError:
        return None
    return parsed if parsed.tzinfo is not None else None


def validate_server_summary(
    document: Any,
    artifact_metadata: dict[str, str],
) -> tuple[list[str], dict[str, dict[str, Any]]]:
    errors: list[str] = []
    top = _exact_keys(
        document,
        {
            "schema_version",
            "session_id",
            "artifact",
            "artifact_sha256",
            "completed_at",
            "cycles",
            "forge",
            "installer_sha1",
            "installer_sha256",
            "installer_attempts",
            "java",
            "manual_player_cycles",
            "minecraft",
            "offline_mode",
            "platform",
            "server_artifact_sha256",
            "server_bind",
            "server_port",
            "started_at",
            "world",
            "world_level_dat",
        },
        "server summary",
        errors,
    )
    if top.get("schema_version") != SERVER_SUMMARY_SCHEMA_VERSION:
        errors.append(
            f"server summary schema_version must be {SERVER_SUMMARY_SCHEMA_VERSION}"
        )
    session_id = top.get("session_id")
    if not isinstance(session_id, str) or SESSION_ID_RE.fullmatch(session_id) is None:
        errors.append("server summary session_id is invalid")
        session_id = ""
    if (
        top.get("artifact") != artifact_metadata["filename"]
        or top.get("artifact_sha256") != artifact_metadata["sha256"]
        or top.get("server_artifact_sha256") != artifact_metadata["sha256"]
    ):
        errors.append("server summary artifact identity differs from the committed manifest")
    if top.get("minecraft") != "1.20.1" or top.get("forge") != "47.4.10":
        errors.append("server summary Minecraft/Forge identity is invalid")
    if top.get("server_bind") != "127.0.0.1":
        errors.append("server summary must record a loopback-only bind")
    port = top.get("server_port")
    if not isinstance(port, int) or isinstance(port, bool) or not 1 <= port <= 65535:
        errors.append("server summary port must be an integer from 1 to 65535")
    if top.get("manual_player_cycles") is not True:
        errors.append("server summary must come from --manual-player-cycles")
    if top.get("offline_mode") is not True:
        errors.append("server summary must record the isolated offline-mode test")
    if top.get("world_level_dat") is not True:
        errors.append("server summary must confirm world/level.dat")
    for field in ("platform", "java"):
        if not isinstance(top.get(field), str) or not top[field].strip():
            errors.append(f"server summary {field} must be non-empty")
    if isinstance(top.get("java"), str) and re.search(
        r"(?<!\d)17(?:[.\s]|$)", top["java"]
    ) is None:
        errors.append("server summary must identify Java 17")
    if not isinstance(top.get("installer_attempts"), int) or isinstance(
        top.get("installer_attempts"), bool
    ) or top.get("installer_attempts", 0) < 1:
        errors.append("server summary installer_attempts must be positive")
    if not isinstance(top.get("installer_sha1"), str) or COMMIT_RE.fullmatch(
        top.get("installer_sha1", "")
    ) is None:
        errors.append("server summary installer_sha1 is invalid")
    if not isinstance(top.get("installer_sha256"), str) or SHA256_RE.fullmatch(
        top.get("installer_sha256", "")
    ) is None:
        errors.append("server summary installer_sha256 is invalid")

    summary_start = _aware_timestamp(top.get("started_at"))
    summary_end = _aware_timestamp(top.get("completed_at"))
    if summary_start is None or summary_end is None or summary_end < summary_start:
        errors.append("server summary timestamps must be aware and ordered")

    cycles_value = top.get("cycles")
    cycles: dict[str, dict[str, Any]] = {}
    if not isinstance(cycles_value, list) or len(cycles_value) != 2:
        errors.append("server summary must contain exactly two cycles")
        cycles_value = []
    expected_cycle_names = ("first-start", "restart")
    cycle_keys = {
        *LOG_AUDIT_FIELDS,
        "completed_at",
        "cycle_id",
        "exit_code",
        "full_log_file",
        "full_log_sha256",
        "mod_marker",
        "name",
        "player_join_observed",
        "player_leave_observed",
        "started_at",
        "status_protocol",
        "status_version",
    }
    for index, value in enumerate(cycles_value):
        item = _exact_keys(value, cycle_keys, f"server summary cycles[{index}]", errors)
        expected_name = expected_cycle_names[index]
        if item.get("name") != expected_name:
            errors.append(f"server summary cycle {index} must be {expected_name}")
        else:
            cycles[expected_name] = item
        if item.get("cycle_id") != f"{session_id}-{expected_name}":
            errors.append(f"server summary {expected_name} cycle_id is invalid")
        if item.get("exit_code") != 0:
            errors.append(f"server summary {expected_name} exit_code must be 0")
        if item.get("full_log_file") != f"{expected_name}-full.txt":
            errors.append(f"server summary {expected_name} full_log_file is invalid")
        if not isinstance(item.get("full_log_sha256"), str) or SHA256_RE.fullmatch(
            item.get("full_log_sha256", "")
        ) is None:
            errors.append(f"server summary {expected_name} full_log_sha256 is invalid")
        if item.get("player_join_observed") is not True or item.get(
            "player_leave_observed"
        ) is not True:
            errors.append(f"server summary {expected_name} lacks join/leave observations")
        if (
            item.get("status_version") != "1.20.1"
            or item.get("status_protocol") != 763
            or item.get("mod_marker") != "1.20.1-0.0.2-dev"
        ):
            errors.append(f"server summary {expected_name} status identity is invalid")
        for field in LOG_AUDIT_FIELDS:
            count = item.get(field)
            if not isinstance(count, int) or isinstance(count, bool) or count < 0:
                errors.append(f"server summary {expected_name} {field} is invalid")
        if any(
            item.get(field) != 0
            for field in (
                "error_count",
                "project_error_count",
                "project_warning_count",
                "client_linkage_failure_count",
            )
        ):
            errors.append(f"server summary {expected_name} has blocking log findings")
        cycle_start = _aware_timestamp(item.get("started_at"))
        cycle_end = _aware_timestamp(item.get("completed_at"))
        if (
            cycle_start is None
            or cycle_end is None
            or cycle_end < cycle_start
            or (summary_start is not None and cycle_start < summary_start)
            or (summary_end is not None and cycle_end > summary_end)
        ):
            errors.append(f"server summary {expected_name} timestamps are invalid")

    world = _exact_keys(
        top.get("world"),
        {
            "identity",
            "identity_marker",
            "identity_marker_sha256",
            "level_dat_after_restart_sha256",
            "level_dat_after_restart_size",
            "level_dat_before_restart_sha256",
            "level_dat_before_restart_size",
            "level_name",
            "same_world_verified",
        },
        "server summary world",
        errors,
    )
    if world.get("level_name") != "world" or world.get("same_world_verified") is not True:
        errors.append("server summary does not prove the same named world was restarted")
    if world.get("identity_marker") != "world/.v002-smoke-world-identity.json":
        errors.append("server summary world identity marker path is invalid")
    for field in (
        "identity",
        "identity_marker_sha256",
        "level_dat_after_restart_sha256",
        "level_dat_before_restart_sha256",
    ):
        if not isinstance(world.get(field), str) or SHA256_RE.fullmatch(
            world.get(field, "")
        ) is None:
            errors.append(f"server summary world {field} is invalid")
    for field in ("level_dat_after_restart_size", "level_dat_before_restart_size"):
        size = world.get(field)
        if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
            errors.append(f"server summary world {field} must be positive")
    return errors, cycles


def _merge_counts(total: dict[str, int], added: dict[str, int]) -> None:
    for key in PRIVACY_CATEGORIES:
        total[key] += added[key]


def _sanitize(value: Any, player_names: list[str], counts: dict[str, int]) -> Any:
    if isinstance(value, str):
        redacted, added = redact_text(value, player_names)
        _merge_counts(counts, added)
        return redacted
    if isinstance(value, list):
        return [_sanitize(item, player_names, counts) for item in value]
    if isinstance(value, dict):
        return {key: _sanitize(item, player_names, counts) for key, item in value.items()}
    return value


def privacy_findings_in_value(
    value: Any,
    player_names: list[str] | None = None,
) -> list[str]:
    findings: list[str] = []
    if isinstance(value, str):
        findings.extend(privacy_findings(value, player_names))
    elif isinstance(value, list):
        for item in value:
            findings.extend(privacy_findings_in_value(item, player_names))
    elif isinstance(value, dict):
        for key, item in value.items():
            findings.extend(privacy_findings(str(key), player_names))
            findings.extend(privacy_findings_in_value(item, player_names))
    return sorted(set(findings))


def _review_blockers(record: dict[str, Any]) -> list[str]:
    blockers: list[str] = []
    for key, observation in record["observations"].items():
        if observation["outcome"] != "PASS":
            blockers.append(f"{key} outcome is {observation['outcome']}, not PASS")
    incomplete_findings = [
        key for key in FINDING_FIELDS if record["findings"][key] is None
    ]
    if incomplete_findings:
        blockers.append("finding counts are incomplete")
    for key in FINDING_FIELDS:
        value = record["findings"][key]
        if isinstance(value, int) and not isinstance(value, bool) and value > 0:
            blockers.append(f"{key} is {value}, not 0")
    if record["observations"]["MANUAL-V002-002"]["outcome"] != "BLOCKED" and record["privacy"]["player_name_terms_supplied"] == 0:
        blockers.append("matching-client review supplied no player-name redaction term")
    if record["server_harness"].get("status") != "PRESENT":
        blockers.append("harness-generated manual player-cycle summary is missing")
    if any(item["status"] == "PRESENT" for item in record["evidence"].values()) and not record["privacy"]["visual_review"]["completed"]:
        blockers.append("screenshot pixel content lacks a completed human privacy review")
    for key, review in record["applicability_reviews"].items():
        if review["decision"] != "ACCEPT_NOT_APPLICABLE":
            blockers.append(
                f"{key} applicability decision is {review['decision']}, "
                "not ACCEPT_NOT_APPLICABLE"
            )
    for role in LIFECYCLE_MARKERS:
        item = record["log_excerpts"].get(role, {})
        lifecycle = item.get("lifecycle_markers")
        if not isinstance(lifecycle, dict) or lifecycle.get("order_valid") is not True:
            blockers.append(f"{role} lifecycle markers are missing or out of order")
    return blockers


def collect_evidence(
    session_path: Path,
    output_dir: Path,
    repository_root: Path = ROOT,
) -> tuple[list[str], dict[str, Any] | None]:
    errors: list[str] = []
    try:
        session_path = resolve_build_path(session_path, repository_root, must_exist=True, require_file=True)
        output_dir, _ = resolve_bundle_path(
            output_dir, repository_root, must_exist=False
        )
        if output_dir.exists():
            raise ValueError(f"output directory already exists: {output_dir}")
        manifest_path, artifact_metadata = load_content_manifest(repository_root)
        session = load_json(session_path)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        return [str(exc)], None

    errors.extend(validate_session(session))
    if errors:
        return errors, None
    player_names = session["privacy"]["player_names"]

    payloads: dict[str, bytes] = {}
    artifacts: dict[str, dict[str, Any]] = {}
    resolved_artifacts: list[Path] = []
    for role in ("source", "server", "client"):
        try:
            path = resolve_build_path(session["artifacts"][role], repository_root, must_exist=True, require_file=True)
            relative = relative_build_path(path, repository_root)
            if privacy_findings(relative, player_names):
                raise ValueError(f"artifact path contains private data; use a generic build path: {relative}")
            digest = file_sha256(path)
            if path.name != artifact_metadata["filename"]:
                raise ValueError(f"{role} JAR filename does not match committed manifest")
            if digest != artifact_metadata["sha256"]:
                raise ValueError(f"{role} JAR SHA-256 does not match committed manifest")
            if any(path == previous or os.path.samefile(path, previous) for previous in resolved_artifacts):
                raise ValueError(f"{role} JAR must be a distinct physical copy")
            resolved_artifacts.append(path)
            artifacts[role] = {
                "path": relative,
                "filename": path.name,
                "sha256": digest,
                "size": path.stat().st_size,
            }
        except (OSError, ValueError) as exc:
            errors.append(str(exc))

    server_harness_record: dict[str, Any]
    summary_cycles: dict[str, dict[str, Any]] = {}
    harness_item = session["server_harness"]
    if harness_item["status"] == "MISSING":
        server_harness_record = {
            "status": "MISSING",
            "note": harness_item["note"],
        }
    else:
        try:
            summary_source = resolve_build_path(
                harness_item["summary"],
                repository_root,
                must_exist=True,
                require_file=True,
            )
            summary_relative = relative_build_path(summary_source, repository_root)
            if privacy_findings(summary_relative, player_names):
                raise ValueError(
                    f"server summary path contains private data: {summary_relative}"
                )
            summary = load_json(summary_source)
            summary_errors, summary_cycles = validate_server_summary(
                summary, artifact_metadata
            )
            if summary_errors:
                raise ValueError("; ".join(summary_errors))
            summary_privacy = privacy_findings_in_value(summary, player_names)
            if summary_privacy:
                raise ValueError(
                    "server summary contains private data: "
                    + ", ".join(summary_privacy)
                )
            summary_payload = (
                json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
            ).encode("utf-8")
            payloads[SERVER_SUMMARY_ARCHIVE] = summary_payload
            server_harness_record = {
                "status": "PRESENT",
                "source_path": summary_relative,
                "file": SERVER_SUMMARY_ARCHIVE,
                "source_sha256": file_sha256(summary_source),
                "sha256": hashlib.sha256(summary_payload).hexdigest(),
                "size": len(summary_payload),
                "session_id": summary["session_id"],
                "server_port": summary["server_port"],
                "world_identity": summary["world"]["identity"],
            }
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(f"server_harness: {exc}")
            server_harness_record = {
                "status": "MISSING",
                "note": "Invalid server summary was not archived.",
            }

    evidence_record: dict[str, dict[str, Any]] = {}
    log_record: dict[str, dict[str, Any]] = {}
    redaction_counts = {category: 0 for category in PRIVACY_CATEGORIES}
    screenshot_total = 0
    pass_screenshot_paths: list[tuple[str, Path, str]] = []
    for role, item in session["evidence"].items():
        if item["status"] == "MISSING":
            evidence_record[role] = {"status": "MISSING", "note": item["note"]}
            continue
        try:
            source = resolve_build_path(item["source"], repository_root, must_exist=True, require_file=True)
            relative = relative_build_path(source, repository_root)
            if privacy_findings(relative, player_names):
                raise ValueError(f"screenshot path contains private data: {relative}")
            details = inspect_png(source)
            screenshot_total += details["size"]
            output = f"screenshots/{role}.png"
            payloads[output] = details.pop("content")
            evidence_record[role] = {
                "status": "PRESENT", "source_path": relative, "file": output,
                "note": item["note"], **details,
            }
            if session["observations"][SCREENSHOT_ROLES[role]]["outcome"] == "PASS":
                pass_screenshot_paths.append((role, source, details["sha256"]))
        except (OSError, ValueError) as exc:
            errors.append(f"evidence.{role}: {exc}")

    raw_log_audits: dict[str, dict[str, Any]] = {}
    for role, item in session["log_excerpts"].items():
        if item["status"] == "MISSING":
            log_record[role] = {"status": "MISSING", "note": item["note"]}
            continue
        try:
            source = resolve_build_path(item["source"], repository_root, must_exist=True, require_file=True)
            relative = relative_build_path(source, repository_root)
            if privacy_findings(relative, player_names):
                raise ValueError(f"log path contains private data: {relative}")
            raw_audit_counts, raw_sha256, raw_size = scan_log_file(source)
            raw_log_audits[role] = {
                "source_path": relative,
                "sha256": raw_sha256,
                "size": raw_size,
                "audit_counts": raw_audit_counts,
            }
            payload, counts = extract_log_excerpt(source, item["line_start"], item["line_end"], player_names)
            _merge_counts(redaction_counts, counts)
            output = f"logs/{role}.txt"
            payloads[output] = payload
            excerpt_text = payload.decode("utf-8")
            lifecycle = lifecycle_marker_result(role, excerpt_text)
            log_record[role] = {
                "status": "PRESENT", "source_path": relative, "file": output,
                "sha256": hashlib.sha256(payload).hexdigest(), "size": len(payload),
                "line_start": item["line_start"], "line_end": item["line_end"],
                "note": item["note"], "redaction_counts": counts,
                "source_audit": raw_log_audits[role],
                "excerpt_audit_counts": scan_log_text(excerpt_text),
            }
            if lifecycle is not None:
                log_record[role]["lifecycle_markers"] = lifecycle
            if role in SERVER_LOG_CYCLES:
                cycle_name = SERVER_LOG_CYCLES[role]
                cycle = summary_cycles.get(cycle_name)
                cycle_id = ""
                if cycle is not None:
                    cycle_id = str(cycle["cycle_id"])
                    if PurePosixPath(relative).name != cycle["full_log_file"]:
                        raise ValueError(
                            f"{role} raw log filename does not match harness cycle "
                            f"{cycle_id}"
                        )
                    if raw_sha256 != cycle["full_log_sha256"]:
                        raise ValueError(
                            f"{role} raw log SHA-256 does not match harness cycle "
                            f"{cycle_id}"
                        )
                    for field in LOG_AUDIT_FIELDS:
                        if raw_audit_counts[field] != cycle[field]:
                            raise ValueError(
                                f"{role} raw log {field} is "
                                f"{raw_audit_counts[field]}, but harness cycle "
                                f"{cycle_id} reports {cycle[field]}"
                            )
                log_record[role]["harness_cycle_id"] = cycle_id
        except (OSError, UnicodeError, ValueError) as exc:
            errors.append(f"log_excerpts.{role}: {exc}")

    def merge_unique_raw_audits(roles: set[str]) -> dict[str, int]:
        total = {field: 0 for field in LOG_AUDIT_FIELDS}
        seen: set[str] = set()
        for role in roles:
            audit = raw_log_audits.get(role)
            if audit is None:
                continue
            identity = str(audit["source_path"]).casefold()
            if identity in seen:
                continue
            seen.add(identity)
            for field in LOG_AUDIT_FIELDS:
                total[field] += int(audit["audit_counts"][field])
        return total

    client_audit = merge_unique_raw_audits(CLIENT_LOG_ROLES)
    server_audit = merge_unique_raw_audits(set(SERVER_LOG_CYCLES))
    computed_findings = {
        "client_project_error_count": client_audit["project_error_count"],
        "client_project_warning_count": client_audit["project_warning_count"],
        "server_project_error_count": server_audit["project_error_count"],
        "server_project_warning_count": server_audit["project_warning_count"],
        "client_class_linkage_failure_count": client_audit[
            "client_linkage_failure_count"
        ],
    }
    for field, computed in computed_findings.items():
        declared = session["findings"][field]
        if declared is not None and declared != computed:
            errors.append(
                f"findings.{field} is {declared}, but raw-log scan found {computed}"
            )
    if screenshot_total > MAX_SCREENSHOT_TOTAL:
        errors.append(
            f"total screenshot payload exceeds {MAX_SCREENSHOT_TOTAL} bytes: "
            f"{screenshot_total}"
        )
    for index, (role, path, digest) in enumerate(pass_screenshot_paths):
        for old_role, old_path, old_digest in pass_screenshot_paths[:index]:
            try:
                same_file = os.path.samefile(path, old_path)
            except OSError as exc:
                errors.append(f"cannot compare screenshot paths: {exc}")
                continue
            if same_file or digest == old_digest:
                errors.append(
                    f"PASS screenshot roles {old_role} and {role} must use distinct captures"
                )
    if errors:
        return errors, None

    session_copy = {
        "metadata": copy.deepcopy(session["metadata"]),
        "observations": copy.deepcopy(session["observations"]),
        "findings": copy.deepcopy(session["findings"]),
        "applicability_reviews": copy.deepcopy(session["applicability_reviews"]),
        "visual_review": copy.deepcopy(session["privacy"]["visual_review"]),
    }
    sanitized = _sanitize(session_copy, player_names, redaction_counts)
    record: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "version": VERSION,
        "collector": Path(__file__).name,
        "generated_at": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "scope_statement": SCOPE_STATEMENT,
        "artifact_manifest": {
            "path": CONTENT_MANIFEST.as_posix(),
            "sha256": file_sha256(manifest_path),
            "artifact_filename": artifact_metadata["filename"],
            "artifact_sha256": artifact_metadata["sha256"],
        },
        "metadata": sanitized["metadata"],
        "artifacts": artifacts,
        "server_harness": _sanitize(
            server_harness_record, player_names, redaction_counts
        ),
        "observations": sanitized["observations"],
        "evidence": _sanitize(evidence_record, player_names, redaction_counts),
        "log_excerpts": _sanitize(log_record, player_names, redaction_counts),
        "findings": sanitized["findings"],
        "applicability_reviews": sanitized["applicability_reviews"],
        "privacy": {
            "player_name_terms_supplied": len(player_names),
            "automated_redaction_counts": redaction_counts,
            "visual_review": sanitized["visual_review"],
            "png_pixel_content_scanned_by_tool": False,
            "png_privacy_metadata_rejected": True,
        },
    }
    blockers = _review_blockers(record)
    record["review_readiness"] = {
        "status": "READY_FOR_HUMAN_GATE_REVIEW" if not blockers else "INCOMPLETE",
        "blockers": blockers,
    }
    serialized = json.dumps(record, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    findings = privacy_findings_in_value(record)
    if findings:
        return ["generated record still contains: " + ", ".join(findings)], None

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    _reject_link_components(
        output_dir.parent.absolute(), repository_root.resolve().absolute()
    )
    temporary = Path(tempfile.mkdtemp(prefix=".v002-evidence-", dir=output_dir.parent))
    try:
        for relative, payload in payloads.items():
            target = temporary / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
        (temporary / RECORD_NAME).write_text(serialized, encoding="utf-8", newline="\n")
        os.replace(temporary, output_dir)
    except OSError:
        if temporary.exists():
            shutil.rmtree(temporary)
        raise
    return [], record


def validate_bundle(
    bundle_dir: Path,
    repository_root: Path = ROOT,
    *,
    require_acceptance_ready: bool = False,
) -> tuple[list[str], dict[str, Any] | None]:
    errors: list[str] = []
    try:
        bundle, bundle_mode = resolve_bundle_path(
            bundle_dir, repository_root, must_exist=True
        )
        if not bundle.is_dir():
            raise ValueError("bundle path must be a directory")
        record_path = bundle / RECORD_NAME
        if _is_link(record_path) or not record_path.is_file():
            raise ValueError(f"bundle is missing safe {RECORD_NAME}")
        record = load_json(record_path)
        manifest_path, artifact_metadata = load_content_manifest(repository_root)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        return [str(exc)], None
    if not isinstance(record, dict):
        return ["manual evidence record must be an object"], None

    expected_top = {
        "schema_version", "version", "collector", "generated_at", "scope_statement",
        "artifact_manifest", "metadata", "artifacts", "server_harness", "observations", "evidence",
        "log_excerpts", "findings", "applicability_reviews", "privacy", "review_readiness",
    }
    _exact_keys(record, expected_top, "record", errors)
    if record.get("schema_version") != SCHEMA_VERSION or record.get("version") != VERSION:
        errors.append("record schema/version mismatch")
    if record.get("collector") != Path(__file__).name or record.get("scope_statement") != SCOPE_STATEMENT:
        errors.append("record collector/scope statement mismatch")
    try:
        generated_at = dt.datetime.fromisoformat(record.get("generated_at", ""))
        if generated_at.tzinfo is None:
            raise ValueError
    except (TypeError, ValueError):
        errors.append("record.generated_at must be a timezone-aware ISO timestamp")
    artifact_manifest = _exact_keys(
        record.get("artifact_manifest"), {"path", "sha256", "artifact_filename", "artifact_sha256"},
        "artifact_manifest", errors,
    )
    if artifact_manifest.get("path") != CONTENT_MANIFEST.as_posix() or artifact_manifest.get("sha256") != file_sha256(manifest_path):
        errors.append("record is not bound to the current committed content manifest")
    if artifact_manifest.get("artifact_filename") != artifact_metadata["filename"] or artifact_manifest.get("artifact_sha256") != artifact_metadata["sha256"]:
        errors.append("record artifact metadata differs from the committed manifest")

    record_artifacts = record.get("artifacts")
    if not isinstance(record_artifacts, dict):
        record_artifacts = {}
    record_privacy = record.get("privacy")
    if not isinstance(record_privacy, dict):
        record_privacy = {}
    supplied_terms = record_privacy.get("player_name_terms_supplied")
    if (
        not isinstance(supplied_terms, int)
        or isinstance(supplied_terms, bool)
        or not 0 <= supplied_terms <= 8
    ):
        errors.append("privacy.player_name_terms_supplied must be an integer from 0 to 8")
        supplied_terms = 0
    record_evidence = record.get("evidence")
    if not isinstance(record_evidence, dict):
        record_evidence = {}
    record_logs = record.get("log_excerpts")
    if not isinstance(record_logs, dict):
        record_logs = {}
    record_observations = record.get("observations")
    if not isinstance(record_observations, dict):
        record_observations = {}

    session_shape = {
        "schema_version": SCHEMA_VERSION, "version": VERSION,
        "metadata": record.get("metadata"),
        "artifacts": {
            role: item.get("path", "") if isinstance(item, dict) else ""
            for role, item in record_artifacts.items()
        },
        "server_harness": {
            "status": record.get("server_harness", {}).get("status"),
            "summary": record.get("server_harness", {}).get("source_path", ""),
            "note": record.get("server_harness", {}).get("note", ""),
        }
        if isinstance(record.get("server_harness"), dict)
        else {},
        "privacy": {
            "player_names": [f"TestPlayer{i}" for i in range(supplied_terms)],
            "visual_review": record_privacy.get("visual_review"),
        },
        "observations": record_observations,
        "evidence": {
            role: {
                "status": item.get("status"), "source": item.get("source_path", ""),
                "note": item.get("note", ""),
            } for role, item in record_evidence.items() if isinstance(item, dict)
        },
        "log_excerpts": {
            role: {
                "status": item.get("status"), "source": item.get("source_path", ""),
                "line_start": item.get("line_start", 1), "line_end": item.get("line_end", 1),
                "note": item.get("note", ""),
            } for role, item in record_logs.items() if isinstance(item, dict)
        },
        "findings": record.get("findings"),
        "applicability_reviews": record.get("applicability_reviews"),
    }
    errors.extend(validate_session(session_shape))

    artifacts = _exact_keys(record.get("artifacts"), {"source", "server", "client"}, "artifacts", errors)
    resolved: list[Path] = []
    recorded_paths: list[str] = []
    recorded_sizes: list[int] = []
    for role in ("source", "server", "client"):
        item = _exact_keys(artifacts.get(role), {"path", "filename", "sha256", "size"}, f"artifacts.{role}", errors)
        try:
            recorded_path = validate_recorded_build_path(item.get("path"))
            if recorded_path.casefold() in {
                previous.casefold() for previous in recorded_paths
            }:
                raise ValueError(f"artifacts.{role} path is not distinct")
            recorded_paths.append(recorded_path)
            size = item.get("size")
            if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
                raise ValueError(f"artifacts.{role} size must be a positive integer")
            recorded_sizes.append(size)
            if (
                PurePosixPath(recorded_path).name != artifact_metadata["filename"]
                or item.get("filename") != artifact_metadata["filename"]
                or item.get("sha256") != artifact_metadata["sha256"]
            ):
                raise ValueError(
                    f"artifacts.{role} metadata does not match the committed manifest"
                )
            if privacy_findings(recorded_path):
                raise ValueError(f"artifacts.{role} path contains private data")
            if bundle_mode == "build":
                path = resolve_build_path(
                    recorded_path,
                    repository_root,
                    must_exist=True,
                    require_file=True,
                )
                digest = file_sha256(path)
                if digest != artifact_metadata["sha256"] or size != path.stat().st_size:
                    raise ValueError(
                        f"artifacts.{role} physical copy no longer matches its record"
                    )
                if any(path == old or os.path.samefile(path, old) for old in resolved):
                    raise ValueError(f"artifacts.{role} is not a distinct physical copy")
                resolved.append(path)
        except (OSError, ValueError) as exc:
            errors.append(str(exc))
    if recorded_sizes and len(set(recorded_sizes)) != 1:
        errors.append("recorded source/server/client JAR sizes differ")

    expected_files = {RECORD_NAME}
    record_harness = record.get("server_harness")
    if not isinstance(record_harness, dict):
        record_harness = {}
    summary_cycles: dict[str, dict[str, Any]] = {}
    if record_harness.get("status") == "MISSING":
        _exact_keys(
            record_harness,
            {"status", "note"},
            "server_harness",
            errors,
        )
    elif record_harness.get("status") == "PRESENT":
        _exact_keys(
            record_harness,
            {
                "status",
                "source_path",
                "file",
                "source_sha256",
                "sha256",
                "size",
                "session_id",
                "server_port",
                "world_identity",
            },
            "server_harness",
            errors,
        )
        expected_files.add(SERVER_SUMMARY_ARCHIVE)
        try:
            recorded_summary_path = validate_recorded_build_path(
                record_harness.get("source_path")
            )
            summary_path = bundle / SERVER_SUMMARY_ARCHIVE
            if _is_link(summary_path) or not summary_path.is_file():
                raise ValueError("bundle is missing the safe server harness summary")
            summary_payload = summary_path.read_bytes()
            if (
                len(summary_payload) > MAX_JSON_BYTES
                or len(summary_payload) != record_harness.get("size")
                or hashlib.sha256(summary_payload).hexdigest()
                != record_harness.get("sha256")
                or record_harness.get("file") != SERVER_SUMMARY_ARCHIVE
            ):
                raise ValueError("server harness summary archive metadata mismatch")
            summary = json.loads(
                summary_payload.decode("utf-8"),
                object_pairs_hook=_duplicates_rejected,
            )
            summary_errors, summary_cycles = validate_server_summary(
                summary, artifact_metadata
            )
            if summary_errors:
                raise ValueError("; ".join(summary_errors))
            if (
                record_harness.get("session_id") != summary["session_id"]
                or record_harness.get("server_port") != summary["server_port"]
                or record_harness.get("world_identity")
                != summary["world"]["identity"]
            ):
                raise ValueError("server harness record differs from its archived summary")
            if privacy_findings_in_value(summary):
                raise ValueError("server harness summary contains private data")
            source_hash = record_harness.get("source_sha256")
            if not isinstance(source_hash, str) or SHA256_RE.fullmatch(source_hash) is None:
                raise ValueError("server harness source SHA-256 is invalid")
            if bundle_mode == "build":
                raw_summary = resolve_build_path(
                    recorded_summary_path,
                    repository_root,
                    must_exist=True,
                    require_file=True,
                )
                if file_sha256(raw_summary) != source_hash:
                    raise ValueError("server harness source no longer matches its record")
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(str(exc))
    else:
        errors.append("server_harness.status must be PRESENT or MISSING")

    screenshot_total = 0
    _exact_keys(record_evidence, set(SCREENSHOT_ROLES), "evidence", errors)
    for role, item_value in record_evidence.items():
        if role not in SCREENSHOT_ROLES:
            continue
        item = item_value if isinstance(item_value, dict) else {}
        if item.get("status") != "PRESENT":
            _exact_keys(item, {"status", "note"}, f"evidence.{role}", errors)
            continue
        _exact_keys(
            item,
            {
                "status", "source_path", "file", "note", "sha256", "size",
                "width", "height", "metadata_chunks",
            },
            f"evidence.{role}", errors,
        )
        expected_file = f"screenshots/{role}.png"
        expected_files.add(expected_file)
        try:
            validate_recorded_build_path(item.get("source_path"))
            path = bundle / expected_file
            if _is_link(path) or not path.is_file():
                raise ValueError(f"missing safe bundle screenshot: {expected_file}")
            details = inspect_png(path)
            screenshot_total += details["size"]
            for key in ("sha256", "size", "width", "height", "metadata_chunks"):
                if item.get(key) != details[key]:
                    raise ValueError(f"screenshot metadata mismatch for {role}: {key}")
            if item.get("file") != expected_file:
                raise ValueError(f"unexpected screenshot archive role path: {role}")
        except (OSError, ValueError) as exc:
            errors.append(str(exc))
    if screenshot_total > MAX_SCREENSHOT_TOTAL:
        errors.append(
            f"total screenshot payload exceeds {MAX_SCREENSHOT_TOTAL} bytes: "
            f"{screenshot_total}"
        )
    if all(
        isinstance(record_observations.get(key), dict)
        and record_observations[key].get("outcome") == "PASS"
        for key in OBSERVATIONS
    ):
        screenshot_hashes = [
            item.get("sha256")
            for item in record_evidence.values()
            if isinstance(item, dict) and item.get("status") == "PRESENT"
        ]
        if len(screenshot_hashes) != len(set(screenshot_hashes)):
            errors.append("PASS evidence must use distinct screenshot captures")

    _exact_keys(record_logs, set(LOG_ROLES), "log_excerpts", errors)
    validated_source_audits: dict[str, dict[str, Any]] = {}
    for role, item_value in record_logs.items():
        if role not in LOG_ROLES:
            continue
        item = item_value if isinstance(item_value, dict) else {}
        if item.get("status") != "PRESENT":
            _exact_keys(item, {"status", "note"}, f"log_excerpts.{role}", errors)
            continue
        log_keys = {
            "status", "source_path", "file", "sha256", "size", "line_start",
            "line_end", "note", "redaction_counts", "source_audit",
            "excerpt_audit_counts",
        }
        if role in LIFECYCLE_MARKERS:
            log_keys.add("lifecycle_markers")
        if role in SERVER_LOG_CYCLES:
            log_keys.add("harness_cycle_id")
        _exact_keys(item, log_keys, f"log_excerpts.{role}", errors)
        expected_file = f"logs/{role}.txt"
        expected_files.add(expected_file)
        try:
            validate_recorded_build_path(item.get("source_path"))
            path = bundle / expected_file
            if _is_link(path) or not path.is_file():
                raise ValueError(f"missing safe bundle log: {expected_file}")
            content = path.read_bytes()
            if len(content) > MAX_EXCERPT_BYTES or hashlib.sha256(content).hexdigest() != item.get("sha256") or len(content) != item.get("size"):
                raise ValueError(f"log excerpt metadata mismatch: {role}")
            text = content.decode("utf-8")
            if privacy_findings(text):
                raise ValueError(f"log excerpt contains private data: {role}")
            line_start = item.get("line_start")
            line_end = item.get("line_end")
            if not isinstance(line_start, int) or not isinstance(line_end, int):
                raise ValueError(f"log excerpt line bounds are invalid: {role}")
            expected_lines = line_end - line_start + 1
            if len(text.splitlines()) != expected_lines:
                raise ValueError(f"log excerpt line count mismatch: {role}")
            if item.get("file") != expected_file:
                raise ValueError(f"unexpected log archive role path: {role}")
            excerpt_audit = scan_log_text(text)
            recorded_excerpt_audit = _exact_keys(
                item.get("excerpt_audit_counts"),
                set(LOG_AUDIT_FIELDS),
                f"log_excerpts.{role}.excerpt_audit_counts",
                errors,
            )
            if recorded_excerpt_audit != excerpt_audit:
                raise ValueError(f"archived log audit mismatch: {role}")
            source_audit = _exact_keys(
                item.get("source_audit"),
                {"source_path", "sha256", "size", "audit_counts"},
                f"log_excerpts.{role}.source_audit",
                errors,
            )
            if source_audit.get("source_path") != item.get("source_path"):
                raise ValueError(f"raw log audit path mismatch: {role}")
            raw_hash = source_audit.get("sha256")
            raw_size = source_audit.get("size")
            if not isinstance(raw_hash, str) or SHA256_RE.fullmatch(raw_hash) is None:
                raise ValueError(f"raw log audit SHA-256 is invalid: {role}")
            if not isinstance(raw_size, int) or isinstance(raw_size, bool) or not 0 < raw_size <= MAX_LOG_BYTES:
                raise ValueError(f"raw log audit size is invalid: {role}")
            raw_counts = _exact_keys(
                source_audit.get("audit_counts"),
                set(LOG_AUDIT_FIELDS),
                f"log_excerpts.{role}.source_audit.audit_counts",
                errors,
            )
            if any(
                not isinstance(value, int)
                or isinstance(value, bool)
                or value < 0
                for value in raw_counts.values()
            ):
                raise ValueError(f"raw log audit counts are invalid: {role}")
            for field in LOG_AUDIT_FIELDS:
                if isinstance(raw_counts.get(field), int) and excerpt_audit[field] > raw_counts[field]:
                    raise ValueError(
                        f"archived {role} {field} exceeds its raw-log audit"
                    )
            validated_source_audits[role] = source_audit
            if bundle_mode == "build":
                raw_path = resolve_build_path(
                    item.get("source_path"),
                    repository_root,
                    must_exist=True,
                    require_file=True,
                )
                physical_counts, physical_hash, physical_size = scan_log_file(raw_path)
                if (
                    physical_hash != raw_hash
                    or physical_size != raw_size
                    or physical_counts != raw_counts
                ):
                    raise ValueError(f"raw log source no longer matches its audit: {role}")
            counts_for_log = _exact_keys(
                item.get("redaction_counts"),
                set(PRIVACY_CATEGORIES),
                f"log_excerpts.{role}.redaction_counts",
                errors,
            )
            if any(
                not isinstance(value, int)
                or isinstance(value, bool)
                or value < 0
                for value in counts_for_log.values()
            ):
                raise ValueError(f"invalid redaction counts for log role: {role}")
            lifecycle = lifecycle_marker_result(role, text)
            if lifecycle is not None and item.get("lifecycle_markers") != lifecycle:
                raise ValueError(f"lifecycle marker record mismatch: {role}")
            if role in SERVER_LOG_CYCLES:
                cycle_name = SERVER_LOG_CYCLES[role]
                cycle = summary_cycles.get(cycle_name)
                if cycle is None:
                    if item.get("harness_cycle_id"):
                        raise ValueError(f"{role} refers to a missing harness cycle")
                else:
                    if (
                        item.get("harness_cycle_id") != cycle.get("cycle_id")
                        or PurePosixPath(str(item.get("source_path", ""))).name
                        != cycle.get("full_log_file")
                        or raw_hash != cycle.get("full_log_sha256")
                    ):
                        raise ValueError(f"{role} is not bound to its harness cycle")
                    for field in LOG_AUDIT_FIELDS:
                        if raw_counts.get(field) != cycle.get(field):
                            raise ValueError(
                                f"{role} raw log {field} differs from its "
                                "harness cycle"
                            )
        except (OSError, UnicodeError, ValueError) as exc:
            errors.append(str(exc))

    def merge_recorded_audits(roles: set[str]) -> dict[str, int]:
        total = {field: 0 for field in LOG_AUDIT_FIELDS}
        seen: set[str] = set()
        for role in roles:
            audit = validated_source_audits.get(role)
            if audit is None:
                continue
            identity = str(audit.get("source_path", "")).casefold()
            if identity in seen:
                continue
            seen.add(identity)
            counts_value = audit.get("audit_counts", {})
            if not isinstance(counts_value, dict):
                continue
            for field in LOG_AUDIT_FIELDS:
                value = counts_value.get(field)
                if isinstance(value, int) and not isinstance(value, bool) and value >= 0:
                    total[field] += value
        return total

    client_audit = merge_recorded_audits(CLIENT_LOG_ROLES)
    server_audit = merge_recorded_audits(set(SERVER_LOG_CYCLES))
    computed_findings = {
        "client_project_error_count": client_audit["project_error_count"],
        "client_project_warning_count": client_audit["project_warning_count"],
        "server_project_error_count": server_audit["project_error_count"],
        "server_project_warning_count": server_audit["project_warning_count"],
        "client_class_linkage_failure_count": client_audit[
            "client_linkage_failure_count"
        ],
    }
    record_findings = record.get("findings")
    if isinstance(record_findings, dict):
        for field, computed in computed_findings.items():
            declared = record_findings.get(field)
            if declared is not None and declared != computed:
                errors.append(
                    f"findings.{field} is {declared}, but recorded raw-log audits "
                    f"show {computed}"
                )

    actual_files: set[str] = set()
    for path in bundle.rglob("*"):
        if _is_link(path):
            errors.append(f"bundle must not contain symlinks or junctions: {path}")
        elif path.is_file():
            actual_files.add(path.relative_to(bundle).as_posix())
    if actual_files != expected_files:
        errors.append(
            "bundle file set mismatch: expected " + ", ".join(sorted(expected_files))
            + "; found " + ", ".join(sorted(actual_files))
        )

    privacy = _exact_keys(
        record.get("privacy"),
        {"player_name_terms_supplied", "automated_redaction_counts", "visual_review", "png_pixel_content_scanned_by_tool", "png_privacy_metadata_rejected"},
        "privacy", errors,
    )
    counts = _exact_keys(privacy.get("automated_redaction_counts"), set(PRIVACY_CATEGORIES), "privacy.automated_redaction_counts", errors)
    if any(not isinstance(value, int) or isinstance(value, bool) or value < 0 for value in counts.values()):
        errors.append("automated redaction counts must be non-negative integers")
    if privacy.get("png_pixel_content_scanned_by_tool") is not False or privacy.get("png_privacy_metadata_rejected") is not True:
        errors.append("privacy capability statements must not be changed")
    log_count_totals = {category: 0 for category in PRIVACY_CATEGORIES}
    for item in record_logs.values():
        if not isinstance(item, dict) or item.get("status") != "PRESENT":
            continue
        item_counts = item.get("redaction_counts")
        if not isinstance(item_counts, dict):
            continue
        for category in PRIVACY_CATEGORIES:
            value = item_counts.get(category)
            if isinstance(value, int) and not isinstance(value, bool) and value >= 0:
                log_count_totals[category] += value
    for category, log_total in log_count_totals.items():
        global_value = counts.get(category)
        if isinstance(global_value, int) and global_value < log_total:
            errors.append(
                f"privacy redaction count for {category} is below archived log total"
            )
    if privacy_findings_in_value(record):
        errors.append("record contains unredacted general privacy findings")

    blockers = _review_blockers(record) if not errors else []
    expected_readiness = {
        "status": "READY_FOR_HUMAN_GATE_REVIEW" if not blockers else "INCOMPLETE",
        "blockers": blockers,
    }
    if record.get("review_readiness") != expected_readiness:
        errors.append("review readiness does not match the evidence contents")
    if require_acceptance_ready and not errors and blockers:
        errors.append(
            "evidence is not mechanically ready for human Gate review: "
            + "; ".join(blockers)
        )
    return errors, record


def create_template(output: Path, repository_root: Path = ROOT) -> None:
    output = resolve_build_path(output, repository_root, must_exist=False)
    if output.exists():
        raise ValueError(f"template output already exists: {output}")
    _, artifact = load_content_manifest(repository_root)
    output.parent.mkdir(parents=True, exist_ok=True)
    _reject_link_components(output.parent.absolute(), (repository_root.resolve() / "build").absolute())
    write_json(output, build_template(artifact["filename"]))


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    template = subparsers.add_parser("template", help="write a BLOCKED-by-default session template")
    template.add_argument("--output", required=True, type=Path)
    collect = subparsers.add_parser("collect", help="archive safe evidence without deciding a Gate")
    collect.add_argument("--session", required=True, type=Path)
    collect.add_argument("--output", required=True, type=Path)
    validate = subparsers.add_parser(
        "validate", help="validate an archived build-local or committed bundle"
    )
    validate.add_argument("--bundle", required=True, type=Path)
    validate.add_argument(
        "--require-acceptance-ready",
        action="store_true",
        help=(
            "require all fixed observations PASS, both N/A proposals accepted, "
            "and mechanically complete evidence for human Gate review; never "
            "marks a Gate PASSED"
        ),
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        if args.command == "template":
            create_template(args.output)
            print(f"[TEMPLATE] BLOCKED-by-default session: {args.output}")
            return 0
        if args.command == "collect":
            errors, record = collect_evidence(args.session, args.output)
            if errors:
                for error in errors:
                    print(f"[FAIL] {error}")
                return 1
            assert record is not None
            print(f"[ARCHIVED] Evidence bundle: {args.output}")
            print(f"[{record['review_readiness']['status']}] No release Gate was changed")
            return 0
        errors, record = validate_bundle(
            args.bundle, require_acceptance_ready=args.require_acceptance_ready
        )
        if errors:
            for error in errors:
                print(f"[FAIL] {error}")
            return 1
        assert record is not None
        print(f"[VALID] Evidence bundle: {args.bundle}")
        if args.require_acceptance_ready:
            print(
                "[READY_FOR_HUMAN_GATE_REVIEW] Evidence is mechanically complete; "
                "this is not Gate PASSED and no release Gate was changed"
            )
        else:
            print(f"[{record['review_readiness']['status']}] No release Gate was changed")
        return 0
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
