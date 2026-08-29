#!/usr/bin/env python3
"""Create, collect, and validate privacy-reviewed v0.0.2 client evidence."""

from __future__ import annotations

import argparse
import copy
import datetime as dt
import hashlib
import hmac
import ipaddress
import json
import os
import re
import shutil
import stat
import struct
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
VERSION = "v0.0.2"
SCHEMA_VERSION = 4
CONTENT_MANIFEST = Path(
    "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json"
)
COMMITTED_BUNDLE = Path("docs/releases/v0.0.2/evidence/client")
RECORD_NAME = "manual-evidence.json"
MAX_JSON_BYTES = 1024 * 1024
MAX_JSON_DEPTH = 64
MAX_BUNDLE_ENTRIES = 128
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
MAX_PEM_PRIVATE_KEY_CHARS = 32 * 1024
MAX_PEM_PRIVATE_KEY_BLOCKS = 16
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
    r"(?i)(?:[A-Z]:[\\/]Users[\\/][^\\/\r\n]+|/(?:home|Users)/[^/\s]+)"
)
IPV4_RE = re.compile(r"(?<![0-9.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9.])")
IPV6_RE = re.compile(
    r"(?i)(?<![0-9a-f:])(?:\[[0-9a-f:]+\]|(?:[0-9a-f]{1,4}:){2,7}"
    r"[0-9a-f:]{0,4})(?![0-9a-f:])"
)
KEY_VALUE_CREDENTIAL_PREFIX = re.compile(
    r"(?i)\b(?:access[_-]?token|authorization|password|passwd|secret)"
    r"\s*[:=]\s*(?:Bearer\s+)?"
)
LAUNCHER_CREDENTIAL_PREFIX = re.compile(
    r"(?i)(?<![A-Za-z0-9_-])"
    r"--(?:access[-_]?token|client[-_]?id|xuid)"
    r"(?:\s*,\s*|\s+)(?:Bearer\s+)?"
)
PEM_PRIVATE_KEY_LABEL = r"[A-Z0-9 ]{0,48}PRIVATE KEY"
PEM_PRIVATE_KEY_BLOCK = re.compile(
    rf"-----BEGIN (?P<label>{PEM_PRIVATE_KEY_LABEL})-----"
    rf"[\s\S]{{0,{MAX_PEM_PRIVATE_KEY_CHARS}}}?"
    r"-----END (?P=label)-----",
    re.I,
)
PEM_PRIVATE_KEY_MARKER = re.compile(
    rf"-----(?:BEGIN|END) {PEM_PRIVATE_KEY_LABEL}-----",
    re.I,
)
CREDENTIAL_PATTERNS = (
    re.compile(
        r"(?i)\b(?:access[_-]?token|authorization|password|passwd|secret)"
        r"\s*[:=]\s*(?:Bearer\s+)?[^\s,;]{1,4096}(?=$|[\s,;])"
    ),
    re.compile(
        r"(?i)(?<![A-Za-z0-9_-])"
        r"--(?:access[-_]?token|client[-_]?id|xuid)"
        r"(?:\s*,\s*|\s+)(?:Bearer\s+)?"
        r"[^\s,;\[\]]{1,4096}(?=$|[\s,;\[\]])"
    ),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}\b"),
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
SERVER_SUMMARY_SCHEMA_VERSION = 3
SERVER_SUMMARY_ARCHIVE = "server/server-summary.json"
MISMATCH_RECEIPT_SCHEMA_VERSION = 1
MISMATCH_RECEIPT_ARCHIVE = "server/mismatch-server-receipt.json"
MISMATCH_PROPERTIES_SCHEMA_VERSION = 1
MISMATCH_PROPERTIES_ARCHIVE = "server/mismatch-server-properties.json"
SERVER_PROPERTIES_IDENTITY_FILE = "server.properties.v002-startup"
PROFILE_SNAPSHOT_SCHEMA_VERSION = 1
PROFILE_ROLES = ("matching", "missing_mod")
PROFILE_PHASES = ("before", "after")
PROFILE_ARCHIVES = {
    (role, phase): f"client-profiles/{role}-{phase}.json"
    for role in PROFILE_ROLES
    for phase in PROFILE_PHASES
}
SESSION_ID_RE = re.compile(r"v002-[0-9a-f]{24}")
MINECRAFT_SERVER_LOGGER = (
    r"(?:minecraft/MinecraftServer|net\.minecraft\.server\.MinecraftServer)/?"
)
DEDICATED_SERVER_LOGGER = (
    r"(?:minecraft/DedicatedServer|"
    r"net\.minecraft\.server\.dedicated\.DedicatedServer)/?"
)
SERVER_LOG_LINE_PREFIX = (
    r"^(?:\[[^\]\r\n]+\]\s*)*"
    r"\[Server thread/INFO\]\s+"
)
PLAYER_JOIN_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{MINECRAFT_SERVER_LOGGER}\]:\s+"
    + r"(?P<player>[A-Za-z0-9_]{3,16}) joined the game\s*$"
)
PLAYER_LEAVE_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{MINECRAFT_SERVER_LOGGER}\]:\s+"
    + r"(?P<player>[A-Za-z0-9_]{3,16}) left the game\s*$"
)
ARCHIVED_PLAYER_JOIN_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{MINECRAFT_SERVER_LOGGER}\]:\s+"
    + r"\[REDACTED_TEST_PLAYER\] joined the game\s*$"
)
ARCHIVED_PLAYER_LEAVE_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{MINECRAFT_SERVER_LOGGER}\]:\s+"
    + r"\[REDACTED_TEST_PLAYER\] left the game\s*$"
)
SERVER_BIND_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{DEDICATED_SERVER_LOGGER}\]:\s+"
    + r"Starting Minecraft server on (?P<bind>[^:\s]+):(?P<port>[0-9]{1,5})\s*$"
)
WORLD_PREPARE_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{DEDICATED_SERVER_LOGGER}\]:\s+"
    + r'Preparing level "(?P<level>[^"\r\n]+)"\s*$'
)
READY_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{DEDICATED_SERVER_LOGGER}\]:\s+"
    + r'Done \([^)]+\)! For help, type (?:"help"|help)\s*$'
)
SAVE_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{MINECRAFT_SERVER_LOGGER}\]:\s+Saved the game\s*$"
)
STOP_LINE = re.compile(
    SERVER_LOG_LINE_PREFIX
    + rf"\[{MINECRAFT_SERVER_LOGGER}\]:\s+Stopping server\s*$"
)
LOG_LOGGER_LINE = re.compile(
    r"^(?:\[[^\]\r\n]+\]\s*)*\[[^\]\r\n]+/(?:INFO|WARN|ERROR)\]\s+"
    r"\[(?P<logger>[^\]\r\n]+)/?\]\s*:\s*(?P<message>.*)$",
    re.I,
)
SERVER_CONNECTION_ATTEMPT_LOGGERS = {
    "forge/handshakehandler/fmlhandshake",
    "forge/networkdispatcher",
    "forge/networkhooks",
    "forge/networkregistry/netregistry",
    "forge/serverlifecyclehooks/serverhooks",
    "minecraft/serverloginpacketlistenerimpl",
    "net.minecraft.server.network.serverloginpacketlistenerimpl",
    "net.minecraftforge.network.handshakehandler/fmlhandshake",
    "net.minecraftforge.network.networkdispatcher",
    "net.minecraftforge.network.networkhooks",
    "net.minecraftforge.network.networkregistry/netregistry",
    "net.minecraftforge.server.serverlifecyclehooks/serverhooks",
    "serverlifecyclehooks/serverhooks",
}
CLIENT_CONNECTION_ATTEMPT_LOGGERS = {
    "minecraft/connectscreen",
    "net.minecraft.client.gui.screens.connectscreen",
    "forge/clienthandshakehandler/fmlhandshake",
    "net.minecraftforge.network.clienthandshakehandler/fmlhandshake",
}
CONNECTION_ATTEMPT_TERMS = re.compile(
    r"\b(?:connection|connect|disconnect|handshake|login|lost connection|uuid of player)\b",
    re.I,
)
CLIENT_CONNECTION_TARGET = re.compile(
    r"^Connecting to (?P<host>[A-Za-z0-9_.:-]+), (?P<port>[0-9]{1,5})\s*$"
)
HARNESS_SERVER_PROPERTIES = {
    "difficulty": "peaceful",
    "enable-command-block": "false",
    "enforce-secure-profile": "false",
    "generate-structures": "false",
    "level-name": "world",
    "level-type": "minecraft:normal",
    "max-players": "2",
    "motd": "ARCE v0.0.2 dedicated smoke",
    "online-mode": "false",
    "server-ip": "127.0.0.1",
    "simulation-distance": "2",
    "spawn-protection": "0",
    "sync-chunk-writes": "true",
    "view-distance": "2",
}

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
    "missing_mod_connection_result": "MANUAL-V002-003",
}
LOG_ROLES = {
    "client_startup_world": "MANUAL-V002-001",
    "matching_client_connection": "MANUAL-V002-002",
    "server_first_join_leave_save_stop": "MANUAL-V002-002",
    "server_restart_reconnect_save_stop": "MANUAL-V002-002",
    "mismatch_attempt": "MANUAL-V002-003",
    "mismatch_server_attempt_save_stop": "MANUAL-V002-003",
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
    "project_state_synchronization": (
        "v0.0.2 defines no project packet or project-owned mutable player/world state; "
        "a reviewer must decide whether a separate synchronization comparison has an "
        "observable subject in this bootstrap milestone."
    ),
    "chunk_unload_behavior": (
        "v0.0.2 defines no project block, entity, block entity, SavedData, chunk ticket, "
        "or chunk-bound operation; a reviewer must decide whether chunk-unload behavior "
        "has an observable project subject in this bootstrap milestone."
    ),
    "configuration_mismatch": (
        "v0.0.2 exposes only the bootstrap lifecycle logging option and no gameplay, "
        "packet, persistence, or authority behavior controlled by project configuration; "
        "a reviewer must decide whether a distinct mismatch case is applicable."
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
CLIENT_LOG_PROFILES = {
    "client_startup_world": "matching",
    "matching_client_connection": "matching",
    "mismatch_attempt": "missing_mod",
}
MISMATCH_SERVER_LOG_ROLE = "mismatch_server_attempt_save_stop"
SERVER_LOG_ROLES = set(SERVER_LOG_CYCLES) | {MISMATCH_SERVER_LOG_ROLE}
LIFECYCLE_MARKERS = {
    "server_first_join_leave_save_stop": (
        ("join", ARCHIVED_PLAYER_JOIN_LINE),
        ("leave", ARCHIVED_PLAYER_LEAVE_LINE),
        ("save", SAVE_LINE),
        ("stop", STOP_LINE),
    ),
    "server_restart_reconnect_save_stop": (
        ("ready", READY_LINE),
        ("join", ARCHIVED_PLAYER_JOIN_LINE),
        ("leave", ARCHIVED_PLAYER_LEAVE_LINE),
        ("save", SAVE_LINE),
        ("stop", STOP_LINE),
    ),
    MISMATCH_SERVER_LOG_ROLE: (
        ("world", WORLD_PREPARE_LINE),
        ("ready", READY_LINE),
        ("save", SAVE_LINE),
        ("stop", STOP_LINE),
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


def physical_file_identity(path: Path) -> str:
    stat_result = path.stat()
    payload = f"{stat_result.st_dev}\0{stat_result.st_ino}".encode("ascii")
    return hashlib.sha256(payload).hexdigest()


def read_bounded_bytes(path: Path, maximum: int, label: str) -> bytes:
    if maximum < 0:
        raise ValueError("bounded read maximum must be non-negative")
    path_stat = path.stat()
    if not stat.S_ISREG(path_stat.st_mode):
        raise ValueError(f"{label} must be a regular file: {path}")
    if path_stat.st_size > maximum:
        raise ValueError(f"{label} exceeds {maximum} bytes: {path}")
    with path.open("rb") as stream:
        opened_stat = os.fstat(stream.fileno())
        if not stat.S_ISREG(opened_stat.st_mode):
            raise ValueError(f"{label} must be a regular file: {path}")
        if (
            (opened_stat.st_dev, opened_stat.st_ino)
            != (path_stat.st_dev, path_stat.st_ino)
            or opened_stat.st_size != path_stat.st_size
            or opened_stat.st_mtime_ns != path_stat.st_mtime_ns
        ):
            raise ValueError(f"{label} changed before it could be read: {path}")
        if opened_stat.st_size > maximum:
            raise ValueError(f"{label} exceeds {maximum} bytes: {path}")
        payload = stream.read(maximum + 1)
        final_stat = os.fstat(stream.fileno())
    if len(payload) > maximum:
        raise ValueError(f"{label} exceeds {maximum} bytes: {path}")
    if (
        final_stat.st_size != opened_stat.st_size
        or final_stat.st_mtime_ns != opened_stat.st_mtime_ns
        or len(payload) != final_stat.st_size
    ):
        raise ValueError(f"{label} changed while it was being read: {path}")
    return payload


def read_bounded_text(path: Path, maximum: int, label: str) -> str:
    return read_bounded_bytes(path, maximum, label).decode("utf-8", errors="strict")


def _duplicates_rejected(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def parse_json_payload(payload: bytes, label: str = "JSON file") -> Any:
    try:
        document = json.loads(
            payload.decode("utf-8", errors="strict"),
            object_pairs_hook=_duplicates_rejected,
        )
    except RecursionError as exc:
        raise ValueError(f"{label} exceeds the JSON nesting limit") from exc
    stack: list[tuple[Any, int]] = [(document, 1)]
    while stack:
        value, depth = stack.pop()
        if depth > MAX_JSON_DEPTH:
            raise ValueError(f"{label} exceeds the JSON nesting limit")
        if isinstance(value, dict):
            stack.extend((item, depth + 1) for item in value.values())
        elif isinstance(value, list):
            stack.extend((item, depth + 1) for item in value)
    return document


def load_json_payload(
    path: Path, label: str = "JSON file"
) -> tuple[bytes, Any]:
    payload = read_bounded_bytes(path, MAX_JSON_BYTES, label)
    return payload, parse_json_payload(payload, label)


def load_json(path: Path) -> Any:
    return load_json_payload(path)[1]


def write_json(path: Path, document: Any) -> None:
    path.write_text(
        json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _is_link(path: Path) -> bool:
    junction = getattr(path, "is_junction", None)
    if path.is_symlink() or bool(junction and junction()):
        return True
    try:
        attributes = path.lstat().st_file_attributes
    except (AttributeError, OSError):
        return False
    return bool(attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400))


def _reject_link_components(path: Path, build_root: Path) -> None:
    relative = path.relative_to(build_root)
    current = build_root
    if _is_link(current):
        raise ValueError(
            f"build directory must not be a symlink or junction/reparse point: {current}"
        )
    for part in relative.parts:
        current /= part
        if _is_link(current):
            raise ValueError(
                f"path must not contain a symlink or junction/reparse point: {current}"
            )


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
    relative = path.resolve().relative_to(repository_root.resolve()).as_posix()
    return validate_recorded_build_path(relative)


def recorded_paths_overlap(first: str, second: str) -> bool:
    first_parts = tuple(part.casefold() for part in PurePosixPath(first).parts)
    second_parts = tuple(part.casefold() for part in PurePosixPath(second).parts)
    shorter = min(len(first_parts), len(second_parts))
    return first_parts[:shorter] == second_parts[:shorter]


def resolved_paths_overlap(first: Path, second: Path) -> bool:
    first = first.resolve()
    second = second.resolve()
    try:
        first.relative_to(second)
        return True
    except ValueError:
        pass
    try:
        second.relative_to(first)
        return True
    except ValueError:
        return False


def canonical_json_payload(document: Any) -> bytes:
    return (
        json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def _profile_inventory_sha256(document: dict[str, Any]) -> str:
    binding = {
        "artifact_filename": document["artifact_filename"],
        "artifact_sha256": document["artifact_sha256"],
        "game_directory": document["game_directory"],
        "mods_directory": document["mods_directory"],
        "mods_files": document["mods_files"],
        "profile_role": document["profile_role"],
    }
    payload = json.dumps(
        binding, ensure_ascii=True, separators=(",", ":"), sort_keys=True
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def inspect_profile_inventory(
    game_directory: Path,
    *,
    profile_role: str,
    artifact_metadata: dict[str, str],
    repository_root: Path,
) -> dict[str, Any]:
    if profile_role not in PROFILE_ROLES:
        raise ValueError(f"unknown client profile role: {profile_role}")
    game_directory = resolve_build_path(
        game_directory, repository_root, must_exist=True
    )
    if not game_directory.is_dir():
        raise ValueError("client profile game directory must be a directory")
    game_relative = relative_build_path(game_directory, repository_root)
    mods_directory = game_directory / "mods"
    _reject_link_components(
        mods_directory.absolute(), (repository_root.resolve() / "build").absolute()
    )
    if _is_link(mods_directory) or not mods_directory.is_dir():
        raise ValueError(
            f"client profile mods directory is missing or unsafe: {mods_directory}"
        )

    files: list[dict[str, Any]] = []
    entries = mods_directory.iterdir()
    first = next(entries, None)
    if profile_role == "missing_mod":
        if first is not None:
            raise ValueError(
                "missing-project-mod client profile mods directory must be empty"
            )
    else:
        second = next(entries, None)
        if first is None or second is not None:
            raise ValueError(
                "matching client profile mods directory must contain exactly the "
                "expected project JAR"
            )
        if first.name != artifact_metadata["filename"]:
            raise ValueError(
                "matching client profile mods directory does not contain the exact "
                "committed project JAR"
            )
        if _is_link(first) or not first.is_file():
            raise ValueError(
                "client profile mods directory must contain regular top-level files "
                f"only: {first}"
            )
        size = first.stat().st_size
        if size <= 0:
            raise ValueError(
                "matching client profile mods directory does not contain the exact "
                "committed project JAR"
            )
        files.append(
            {
                "path": first.name,
                "sha256": file_sha256(first),
                "size": size,
            }
        )

    if profile_role == "matching":
        expected_file = files[0]
        if (
            expected_file["sha256"] != artifact_metadata["sha256"]
        ):
            raise ValueError(
                "matching client profile mods directory does not contain the exact "
                "committed project JAR"
            )

    document: dict[str, Any] = {
        "artifact_filename": artifact_metadata["filename"],
        "artifact_sha256": artifact_metadata["sha256"],
        "game_directory": game_relative,
        "mods_directory": f"{game_relative}/mods",
        "mods_files": files,
        "profile_role": profile_role,
    }
    document["inventory_sha256"] = _profile_inventory_sha256(document)
    return document


def build_profile_snapshot_document(
    game_directory: Path,
    *,
    profile_role: str,
    phase: str,
    artifact_metadata: dict[str, str],
    repository_root: Path,
    captured_at: str | None = None,
) -> dict[str, Any]:
    if phase not in PROFILE_PHASES:
        raise ValueError(f"unknown client profile snapshot phase: {phase}")
    inventory = inspect_profile_inventory(
        game_directory,
        profile_role=profile_role,
        artifact_metadata=artifact_metadata,
        repository_root=repository_root,
    )
    timestamp = captured_at or dt.datetime.now(dt.timezone.utc).replace(
        microsecond=0
    ).isoformat()
    try:
        parsed = dt.datetime.fromisoformat(timestamp)
    except (TypeError, ValueError) as exc:
        raise ValueError("profile snapshot captured_at must be an ISO timestamp") from exc
    if parsed.tzinfo is None:
        raise ValueError("profile snapshot captured_at must include a timezone")
    return {
        "schema_version": PROFILE_SNAPSHOT_SCHEMA_VERSION,
        "phase": phase,
        "captured_at": timestamp,
        **inventory,
    }


def validate_profile_snapshot_document(
    value: Any,
    *,
    expected_role: str,
    expected_phase: str,
    expected_game_directory: str,
    artifact_metadata: dict[str, str],
    expected_artifact_size: int | None = None,
) -> dict[str, Any]:
    errors: list[str] = []
    document = _exact_keys(
        value,
        {
            "schema_version",
            "phase",
            "captured_at",
            "artifact_filename",
            "artifact_sha256",
            "game_directory",
            "mods_directory",
            "mods_files",
            "profile_role",
            "inventory_sha256",
        },
        f"{expected_role} {expected_phase} profile snapshot",
        errors,
    )
    if errors:
        raise ValueError("; ".join(errors))
    if document.get("schema_version") != PROFILE_SNAPSHOT_SCHEMA_VERSION:
        raise ValueError("client profile snapshot schema version is invalid")
    if document.get("profile_role") != expected_role:
        raise ValueError("client profile snapshot role is invalid")
    if document.get("phase") != expected_phase:
        raise ValueError("client profile snapshot phase is invalid")
    try:
        captured_at = dt.datetime.fromisoformat(document.get("captured_at", ""))
    except (TypeError, ValueError) as exc:
        raise ValueError(
            "client profile snapshot captured_at must be an ISO timestamp"
        ) from exc
    if captured_at.tzinfo is None:
        raise ValueError("client profile snapshot captured_at must include a timezone")

    game_directory = validate_recorded_build_path(document.get("game_directory"))
    if game_directory != expected_game_directory:
        raise ValueError("client profile snapshot game directory differs from session")
    expected_mods = f"{game_directory}/mods"
    if validate_recorded_build_path(document.get("mods_directory")) != expected_mods:
        raise ValueError("client profile snapshot mods directory is invalid")
    if (
        document.get("artifact_filename") != artifact_metadata["filename"]
        or document.get("artifact_sha256") != artifact_metadata["sha256"]
    ):
        raise ValueError(
            "client profile snapshot artifact differs from the committed manifest"
        )

    files = document.get("mods_files")
    if not isinstance(files, list):
        raise ValueError("client profile snapshot mods_files must be a list")
    normalized_files: list[dict[str, Any]] = []
    for index, item_value in enumerate(files):
        item_errors: list[str] = []
        item = _exact_keys(
            item_value,
            {"path", "sha256", "size"},
            f"client profile snapshot mods_files[{index}]",
            item_errors,
        )
        if item_errors:
            raise ValueError("; ".join(item_errors))
        filename = item.get("path")
        if (
            not isinstance(filename, str)
            or not filename
            or PurePosixPath(filename).name != filename
            or PureWindowsPath(filename).name != filename
            or ":" in filename
        ):
            raise ValueError("client profile snapshot contains an unsafe mod filename")
        checksum = item.get("sha256")
        size = item.get("size")
        if not isinstance(checksum, str) or SHA256_RE.fullmatch(checksum) is None:
            raise ValueError("client profile snapshot contains an invalid mod hash")
        if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
            raise ValueError("client profile snapshot contains an invalid mod size")
        normalized_files.append(
            {"path": filename, "sha256": checksum, "size": size}
        )
    if normalized_files != sorted(
        normalized_files, key=lambda item: item["path"].casefold()
    ):
        raise ValueError("client profile snapshot mod inventory is not sorted")
    if len({item["path"].casefold() for item in normalized_files}) != len(
        normalized_files
    ):
        raise ValueError("client profile snapshot mod filenames are not distinct")

    if expected_role == "matching":
        if (
            len(normalized_files) != 1
            or normalized_files[0]["path"] != artifact_metadata["filename"]
            or normalized_files[0]["sha256"] != artifact_metadata["sha256"]
            or (
                expected_artifact_size is not None
                and normalized_files[0]["size"] != expected_artifact_size
            )
        ):
            raise ValueError(
                "matching client profile snapshot must contain exactly the expected JAR"
            )
    elif normalized_files:
        raise ValueError(
            "missing-project-mod client profile snapshot must contain no mod files"
        )
    if document.get("inventory_sha256") != _profile_inventory_sha256(document):
        raise ValueError("client profile snapshot inventory hash is invalid")
    return document


def create_profile_snapshot(
    game_directory: Path,
    output: Path,
    *,
    profile_role: str,
    phase: str,
    repository_root: Path = ROOT,
) -> None:
    game_directory = resolve_build_path(
        game_directory, repository_root, must_exist=True
    )
    output = resolve_build_path(output, repository_root, must_exist=False)
    if output.exists():
        raise ValueError(f"profile snapshot output already exists: {output}")
    mods_directory = game_directory / "mods"
    try:
        output.absolute().relative_to(mods_directory.absolute())
    except ValueError:
        pass
    else:
        raise ValueError("profile snapshot output must remain outside the mods directory")
    _, artifact_metadata = load_content_manifest(repository_root)
    document = build_profile_snapshot_document(
        game_directory,
        profile_role=profile_role,
        phase=phase,
        artifact_metadata=artifact_metadata,
        repository_root=repository_root,
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    _reject_link_components(
        output.parent.absolute(), (repository_root.resolve() / "build").absolute()
    )
    with output.open("xb") as stream:
        stream.write(canonical_json_payload(document))


def recorded_path_is_below(value: str, parent: str) -> bool:
    try:
        relative = PurePosixPath(value).relative_to(PurePosixPath(parent))
    except ValueError:
        return False
    return bool(relative.parts)


def profile_capture_timeline_errors(
    captures: dict[str, dict[str, str]], summary: dict[str, Any]
) -> list[str]:
    if set(captures) != set(PROFILE_ROLES):
        return []
    try:
        matching_before = dt.datetime.fromisoformat(captures["matching"]["before"])
        matching_after = dt.datetime.fromisoformat(captures["matching"]["after"])
        missing_before = dt.datetime.fromisoformat(captures["missing_mod"]["before"])
        missing_after = dt.datetime.fromisoformat(captures["missing_mod"]["after"])
        server_start = dt.datetime.fromisoformat(summary["started_at"])
        server_end = dt.datetime.fromisoformat(summary["completed_at"])
    except (KeyError, TypeError, ValueError):
        return ["client profile and server capture timestamps are invalid"]
    if any(
        value.tzinfo is None
        for value in (
            matching_before,
            matching_after,
            missing_before,
            missing_after,
            server_start,
            server_end,
        )
    ):
        return ["client profile and server capture timestamps must be timezone-aware"]
    errors: list[str] = []
    if matching_before > server_start:
        errors.append(
            "matching client before snapshot must predate the player harness"
        )
    if matching_after < server_end:
        errors.append(
            "matching client after snapshot must follow the player harness"
        )
    if missing_before < matching_after:
        errors.append(
            "missing-project-mod before snapshot must follow the matching client "
            "after snapshot"
        )
    if missing_after < missing_before:
        errors.append(
            "missing-project-mod after snapshot must follow its before snapshot"
        )
    return errors


def _parse_content_manifest(document: Any) -> dict[str, str]:
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
    return {"filename": filename, "sha256": checksum}


def load_content_manifest(repository_root: Path) -> tuple[Path, dict[str, str]]:
    path = (repository_root.resolve() / CONTENT_MANIFEST).resolve()
    expected = repository_root.resolve() / CONTENT_MANIFEST
    if path != expected or _is_link(expected) or not path.is_file():
        raise ValueError(f"committed content manifest is missing or unsafe: {CONTENT_MANIFEST}")
    return path, _parse_content_manifest(load_json(path))


def _git(
    repository_root: Path,
    *arguments: str,
    text: bool = True,
) -> subprocess.CompletedProcess[Any]:
    command = [
        "git",
        "-c",
        f"safe.directory={repository_root.resolve().as_posix()}",
        "-C",
        str(repository_root.resolve()),
        *arguments,
    ]
    return subprocess.run(
        command,
        check=False,
        capture_output=True,
        text=text,
        encoding="utf-8" if text else None,
        errors="strict" if text else None,
    )


def _git_text(repository_root: Path, *arguments: str) -> str:
    completed = _git(repository_root, *arguments)
    if completed.returncode != 0:
        message = (completed.stderr or completed.stdout).strip()
        raise ValueError(f"git {' '.join(arguments)} failed: {message}")
    return completed.stdout.strip()


def _git_blob(repository_root: Path, commit: str, relative: str) -> tuple[str, bytes]:
    blob = _git_text(repository_root, "rev-parse", f"{commit}:{relative}")
    if re.fullmatch(r"[0-9a-f]{40,64}", blob) is None:
        raise ValueError(f"git returned an invalid blob id for {relative}")
    completed = _git(repository_root, "show", f"{commit}:{relative}", text=False)
    if completed.returncode != 0:
        stderr = completed.stderr.decode("utf-8", errors="replace").strip()
        raise ValueError(f"cannot read {relative} from source commit {commit}: {stderr}")
    return blob, completed.stdout


def load_content_manifest_at_commit(
    repository_root: Path, source_commit: str
) -> tuple[bytes, dict[str, str]]:
    _, payload = _git_blob(
        repository_root, source_commit, CONTENT_MANIFEST.as_posix()
    )
    if len(payload) > MAX_JSON_BYTES:
        raise ValueError("source-commit content manifest exceeds the JSON size limit")
    document = parse_json_payload(payload, "source-commit content manifest")
    return payload, _parse_content_manifest(document)


def build_source_revision(
    repository_root: Path,
    source_commit: str,
    *,
    require_head: bool,
) -> dict[str, Any]:
    if COMMIT_RE.fullmatch(source_commit) is None:
        raise ValueError("source commit must be a lowercase 40-character commit")
    resolved_commit = _git_text(
        repository_root, "rev-parse", "--verify", f"{source_commit}^{{commit}}"
    )
    if resolved_commit != source_commit:
        raise ValueError("source commit does not resolve to the recorded commit")
    if require_head:
        head = _git_text(repository_root, "rev-parse", "--verify", "HEAD^{commit}")
        if source_commit != head:
            raise ValueError(
                f"metadata.source_commit must equal checkout HEAD: {head}"
            )
        dirty = _git_text(
            repository_root,
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
        )
        if dirty:
            first = dirty.splitlines()[0]
            raise ValueError(
                "manual evidence collection requires a clean tracked/untracked "
                f"worktree outside ignored build inputs; first finding: {first}"
            )

    files: dict[str, dict[str, Any]] = {}
    for relative in ("README.md", CONTENT_MANIFEST.as_posix()):
        blob_id, payload = _git_blob(repository_root, source_commit, relative)
        if require_head:
            working_path = repository_root.resolve() / relative
            if _is_link(working_path) or not working_path.is_file():
                raise ValueError(f"source-bound file is missing or unsafe: {relative}")
            if working_path.read_bytes() != payload:
                raise ValueError(
                    f"working {relative} differs from metadata.source_commit"
                )
        files[relative] = {
            "git_blob": blob_id,
            "sha256": hashlib.sha256(payload).hexdigest(),
            "size": len(payload),
        }
    return {"commit": source_commit, "files": files}


def validate_source_revision(
    value: Any,
    repository_root: Path,
    source_commit: Any,
) -> list[str]:
    errors: list[str] = []
    revision = _exact_keys(value, {"commit", "files"}, "source_revision", errors)
    if revision.get("commit") != source_commit:
        errors.append("source_revision commit differs from metadata.source_commit")
        return errors
    files = _exact_keys(
        revision.get("files"),
        {"README.md", CONTENT_MANIFEST.as_posix()},
        "source_revision.files",
        errors,
    )
    try:
        expected = build_source_revision(
            repository_root, str(source_commit), require_head=False
        )
    except (OSError, UnicodeError, ValueError) as exc:
        errors.append(str(exc))
        return errors
    for relative, expected_item in expected["files"].items():
        item = _exact_keys(
            files.get(relative),
            {"git_blob", "sha256", "size"},
            f"source_revision.files.{relative}",
            errors,
        )
        if item != expected_item:
            errors.append(
                f"source_revision file binding differs from {source_commit}:{relative}"
            )
    return errors


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


def _redact_pem_private_keys(
    text: str,
) -> tuple[str, list[tuple[int, int]]]:
    matches: list[re.Match[str]] = []
    for match in PEM_PRIVATE_KEY_BLOCK.finditer(text):
        matches.append(match)
        if len(matches) > MAX_PEM_PRIVATE_KEY_BLOCKS:
            raise ValueError(
                "text contains too many PEM private-key blocks to redact safely"
            )

    spans = [(match.start(), match.end()) for match in matches]
    redacted = PEM_PRIVATE_KEY_BLOCK.sub(
        lambda match: "[REDACTED_CREDENTIAL]"
        + ("\n" * match.group(0).count("\n")),
        text,
    )
    return redacted, spans


def redact_text(text: str, player_names: list[str]) -> tuple[str, dict[str, int]]:
    counts = {category: 0 for category in PRIVACY_CATEGORIES}
    result, pem_spans = _redact_pem_private_keys(text)
    counts["credential"] += len(pem_spans)
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
    if (
        PEM_PRIVATE_KEY_MARKER.search(text)
        or KEY_VALUE_CREDENTIAL_PREFIX.search(text)
        or LAUNCHER_CREDENTIAL_PREFIX.search(text)
        or any(pattern.search(text) for pattern in CREDENTIAL_PATTERNS)
    ):
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


def read_log_snapshot(
    path: Path,
) -> tuple[bytes, str, dict[str, int], str, int]:
    payload = read_bounded_bytes(path, MAX_LOG_BYTES, "log source")
    text = payload.decode("utf-8", errors="strict")
    return (
        payload,
        text,
        scan_log_text(text),
        hashlib.sha256(payload).hexdigest(),
        len(payload),
    )


def scan_log_file(path: Path) -> tuple[dict[str, int], str, int]:
    _, _, counts, digest, size = read_log_snapshot(path)
    return counts, digest, size


def bind_player_identity(secret: bytes, player_name: str) -> str:
    """Create the harness-compatible opaque player token for test fixtures."""
    if not isinstance(secret, bytes) or len(secret) < 32:
        raise ValueError("player identity binding secret must contain at least 32 bytes")
    if PLAYER_NAME_RE.fullmatch(player_name) is None:
        raise ValueError("player identity binding requires a valid Minecraft name")
    return hmac.new(
        secret,
        b"v0.0.2-player-identity\0"
        + player_name.casefold().encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def parse_player_lifecycle(text: str, role: str) -> dict[str, Any]:
    joins: list[tuple[int, str]] = []
    leaves: list[tuple[int, str]] = []
    for index, line in enumerate(text.splitlines()):
        join = PLAYER_JOIN_LINE.search(line)
        if join is not None:
            joins.append((index, join.group("player")))
        leave = PLAYER_LEAVE_LINE.search(line)
        if leave is not None:
            leaves.append((index, leave.group("player")))
    if len(joins) != 1 or len(leaves) != 1:
        raise ValueError(
            f"{role} must contain exactly one player join and one player leave"
        )
    join_index, joined_player = joins[0]
    leave_index, left_player = leaves[0]
    if leave_index <= join_index:
        raise ValueError(f"{role} player leave must follow the join")
    if joined_player.casefold() != left_player.casefold():
        raise ValueError(f"{role} join and leave identities differ")
    return {
        "player_name": joined_player,
        "join_line_index": join_index,
        "leave_line_index": leave_index,
    }


def _normalized_logger(parsed: re.Match[str]) -> str:
    return parsed.group("logger").rstrip("/").casefold()


def server_connection_attempt_marker_result(
    text: str,
    *,
    expected_host: str,
    expected_port: int,
) -> dict[str, Any]:
    matches: list[dict[str, Any]] = []
    lines = text.splitlines()
    for index, line in enumerate(lines):
        parsed = LOG_LOGGER_LINE.search(line)
        if parsed is None or CONNECTION_ATTEMPT_TERMS.search(parsed.group("message")) is None:
            continue
        logger = _normalized_logger(parsed)
        if logger in SERVER_CONNECTION_ATTEMPT_LOGGERS:
            matches.append({"line_index": index, "logger": logger})

    ready = next(
        (index for index, line in enumerate(lines) if READY_LINE.search(line)), -1
    )
    save = next(
        (index for index, line in enumerate(lines) if SAVE_LINE.search(line)), -1
    )
    stop = next(
        (index for index, line in enumerate(lines) if STOP_LINE.search(line)), -1
    )
    ordered = [
        item
        for item in matches
        if ready >= 0 and ready < item["line_index"] < save < stop
    ]
    return {
        "source": "server",
        "count": len(matches),
        "loggers": sorted({item["logger"] for item in matches}),
        "line_indexes": [item["line_index"] for item in matches],
        "target_host": expected_host,
        "target_port": expected_port,
        "target_verified": bool(ordered),
        "order_valid": bool(ordered),
    }


def client_connection_attempt_marker_result(
    text: str,
    *,
    expected_host: str,
    expected_port: int,
) -> dict[str, Any]:
    candidates: list[dict[str, Any]] = []
    matching: list[dict[str, Any]] = []
    for index, line in enumerate(text.splitlines()):
        parsed = LOG_LOGGER_LINE.search(line)
        if parsed is None:
            continue
        logger = _normalized_logger(parsed)
        if logger not in CLIENT_CONNECTION_ATTEMPT_LOGGERS:
            continue
        target = CLIENT_CONNECTION_TARGET.fullmatch(parsed.group("message"))
        if target is None:
            continue
        port = int(target.group("port"))
        item = {
            "line_index": index,
            "logger": logger,
            "host": target.group("host"),
            "port": port,
        }
        candidates.append(item)
        if item["host"] == expected_host and port == expected_port:
            matching.append(item)
    return {
        "source": "client",
        "count": len(candidates),
        "loggers": sorted({item["logger"] for item in candidates}),
        "line_indexes": [item["line_index"] for item in candidates],
        "target_host": expected_host,
        "target_port": expected_port,
        "target_verified": bool(matching),
        "order_valid": bool(matching),
    }


def validate_mismatch_receipt(
    document: Any,
    *,
    full_log_sha256: str,
    expected_exit_code: int | None = None,
) -> dict[str, Any]:
    if not isinstance(document, dict):
        raise ValueError("mismatch server receipt must contain a JSON object")
    expected_keys = {"schema_version", "exit_code", "full_log_sha256"}
    actual_keys = set(document)
    if actual_keys != expected_keys:
        missing = sorted(expected_keys - actual_keys)
        extra = sorted(actual_keys - expected_keys)
        details: list[str] = []
        if missing:
            details.append("missing " + ", ".join(missing))
        if extra:
            details.append("unexpected " + ", ".join(extra))
        raise ValueError(
            "mismatch server receipt fields are invalid: " + "; ".join(details)
        )
    if document.get("schema_version") != MISMATCH_RECEIPT_SCHEMA_VERSION:
        raise ValueError(
            "mismatch server receipt schema_version must be "
            f"{MISMATCH_RECEIPT_SCHEMA_VERSION}"
        )
    exit_code = document.get("exit_code")
    if not isinstance(exit_code, int) or isinstance(exit_code, bool):
        raise ValueError("mismatch server receipt exit_code must be an integer")
    receipt_log_sha256 = document.get("full_log_sha256")
    if (
        not isinstance(receipt_log_sha256, str)
        or SHA256_RE.fullmatch(receipt_log_sha256) is None
    ):
        raise ValueError(
            "mismatch server receipt full_log_sha256 must be a lowercase SHA-256"
        )
    if receipt_log_sha256 != full_log_sha256:
        raise ValueError(
            "mismatch server receipt full_log_sha256 differs from the retained full log"
        )
    if expected_exit_code is not None and exit_code != expected_exit_code:
        raise ValueError(
            "mismatch server receipt exit_code differs from the session record"
        )
    return {
        "schema_version": MISMATCH_RECEIPT_SCHEMA_VERSION,
        "exit_code": exit_code,
        "full_log_sha256": receipt_log_sha256,
    }


def mismatch_receipt_payload(document: dict[str, Any]) -> bytes:
    return (
        json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def server_configuration_payload(port: int) -> bytes:
    if not isinstance(port, int) or isinstance(port, bool) or not 1 <= port <= 65535:
        raise ValueError("harness server port must be an integer from 1 to 65535")
    properties = {**HARNESS_SERVER_PROPERTIES, "server-port": str(port)}
    text = "".join(f"{key}={value}\n" for key, value in sorted(properties.items()))
    return text.encode("ascii", errors="strict")


def mismatch_properties_payload(document: dict[str, Any]) -> bytes:
    return (
        json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    ).encode("utf-8")


def validate_mismatch_properties(
    document: Any,
    *,
    summary: dict[str, Any],
) -> dict[str, Any]:
    if not isinstance(document, dict):
        raise ValueError("mismatch server properties archive must contain a JSON object")
    expected_keys = {
        "schema_version",
        "server_bind",
        "server_port",
        "level_name",
        "source_sha256",
    }
    if set(document) != expected_keys:
        raise ValueError("mismatch server properties archive fields are invalid")
    if document.get("schema_version") != MISMATCH_PROPERTIES_SCHEMA_VERSION:
        raise ValueError(
            "mismatch server properties schema_version must be "
            f"{MISMATCH_PROPERTIES_SCHEMA_VERSION}"
        )
    if (
        document.get("server_bind") != summary["server_bind"]
        or document.get("server_port") != summary["server_port"]
    ):
        raise ValueError("mismatch server properties bind/port differs from player harness")
    if document.get("level_name") != summary["world"]["level_name"]:
        raise ValueError("mismatch server properties level-name differs from player harness")
    source_sha256 = document.get("source_sha256")
    if not isinstance(source_sha256, str) or SHA256_RE.fullmatch(source_sha256) is None:
        raise ValueError("mismatch server properties source SHA-256 is invalid")
    expected_source_sha256 = hashlib.sha256(
        server_configuration_payload(summary["server_port"])
    ).hexdigest()
    if source_sha256 != expected_source_sha256:
        raise ValueError(
            "mismatch server properties source SHA-256 differs from the "
            "canonical harness startup-properties payload"
        )
    if source_sha256 != summary["world"]["server_properties_sha256"]:
        raise ValueError(
            "mismatch server properties source SHA-256 differs from the "
            "harness world binding"
        )
    return {
        "schema_version": MISMATCH_PROPERTIES_SCHEMA_VERSION,
        "server_bind": document["server_bind"],
        "server_port": document["server_port"],
        "level_name": document["level_name"],
        "source_sha256": source_sha256,
    }


def build_mismatch_properties(
    path: Path,
    *,
    summary: dict[str, Any],
) -> tuple[dict[str, Any], bytes]:
    source_payload = read_bounded_bytes(
        path, MAX_JSON_BYTES, "mismatch startup-properties identity"
    )
    expected_payload = server_configuration_payload(summary["server_port"])
    if source_payload != expected_payload:
        raise ValueError(
            "mismatch startup-properties identity must exactly match the harness-owned "
            "ASCII canonical properties; duplicate keys, alternate separators, "
            "escapes, comments, and rewritten values are not allowed"
        )
    document = validate_mismatch_properties(
        {
            "schema_version": MISMATCH_PROPERTIES_SCHEMA_VERSION,
            "server_bind": summary["server_bind"],
            "server_port": summary["server_port"],
            "level_name": summary["world"]["level_name"],
            "source_sha256": hashlib.sha256(source_payload).hexdigest(),
        },
        summary=summary,
    )
    return document, mismatch_properties_payload(document)


def build_mismatch_server_binding(
    *,
    source_log: Path,
    source_payload: bytes,
    source_sha256: str,
    server_artifact: Path,
    summary: dict[str, Any],
    server_exit_code: int,
    receipt_sha256: str,
) -> tuple[dict[str, Any], bytes, dict[str, Any]]:
    server_root = server_artifact.parent.parent
    server_root_anchor = server_root.absolute()
    runtime_log = server_root / "logs" / "latest.log"
    _reject_link_components(runtime_log.absolute(), server_root_anchor)
    try:
        runtime_log.resolve(strict=True).relative_to(server_root.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise ValueError("mismatch server runtime log escapes the server root") from exc
    if _is_link(runtime_log) or not runtime_log.is_file():
        raise ValueError("mismatch server runtime latest.log is missing or unsafe")
    if not isinstance(source_payload, bytes) or len(source_payload) > MAX_LOG_BYTES:
        raise ValueError("mismatch server retained log snapshot is invalid")
    runtime_payload = (
        source_payload
        if os.path.samefile(runtime_log, source_log)
        else read_bounded_bytes(
            runtime_log, MAX_LOG_BYTES, "mismatch server runtime log"
        )
    )
    runtime_hash = hashlib.sha256(runtime_payload).hexdigest()
    if (
        runtime_hash != source_sha256
        or hashlib.sha256(source_payload).hexdigest() != runtime_hash
    ):
        raise ValueError(
            "mismatch server log is not the retained runtime logs/latest.log content"
        )
    text = source_payload.decode("utf-8", errors="strict")
    bind_matches = [
        match for line in text.splitlines()
        if (match := SERVER_BIND_LINE.search(line)) is not None
    ]
    if len(bind_matches) != 1:
        raise ValueError("mismatch server log must contain exactly one bind marker")
    bind = bind_matches[0].group("bind")
    port = int(bind_matches[0].group("port"))
    if bind != summary["server_bind"] or port != summary["server_port"]:
        raise ValueError("mismatch server log bind/port differs from player harness")
    world_matches = [
        match
        for line in text.splitlines()
        if (match := WORLD_PREPARE_LINE.search(line)) is not None
    ]
    if len(world_matches) != 1:
        raise ValueError(
            "mismatch server log must contain exactly one logger-anchored "
            "Preparing level marker"
        )
    runtime_world_level_name = world_matches[0].group("level")
    if runtime_world_level_name != summary["world"]["level_name"]:
        raise ValueError(
            "mismatch server runtime world-load marker differs from player harness"
        )

    properties_path = server_root / SERVER_PROPERTIES_IDENTITY_FILE
    _reject_link_components(properties_path.absolute(), server_root_anchor)
    if _is_link(properties_path) or not properties_path.is_file():
        raise ValueError(
            "harness startup server.properties identity is missing or unsafe"
        )
    properties_document, properties_payload = build_mismatch_properties(
        properties_path,
        summary=summary,
    )
    properties_sha256 = properties_document["source_sha256"]

    marker_relative = str(summary["world"]["identity_marker"])
    marker = server_root / PurePosixPath(marker_relative)
    _reject_link_components(marker.absolute(), server_root_anchor)
    try:
        marker.resolve(strict=True).relative_to(server_root.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise ValueError("mismatch server world marker escapes the server root") from exc
    if _is_link(marker) or not marker.is_file():
        raise ValueError("mismatch server world identity marker is missing or unsafe")
    marker_payload, marker_document = load_json_payload(
        marker, "mismatch server world identity marker"
    )
    expected_marker = {
        "artifact_sha256": summary["artifact_sha256"],
        "server_properties_sha256": summary["world"][
            "server_properties_sha256"
        ],
        "session_id": summary["session_id"],
        "world_identity": summary["world"]["identity"],
    }
    if marker_document != expected_marker:
        raise ValueError("mismatch server world identity marker differs from player harness")
    marker_hash = hashlib.sha256(marker_payload).hexdigest()
    if marker_hash != summary["world"]["identity_marker_sha256"]:
        raise ValueError("mismatch server world identity marker hash differs from player harness")
    artifact_hash = file_sha256(server_artifact)
    if artifact_hash != summary["server_artifact_sha256"]:
        raise ValueError("mismatch server artifact differs from player harness")
    binding = {
        "session_id": summary["session_id"],
        "server_artifact_sha256": artifact_hash,
        "server_bind": bind,
        "server_port": port,
        "world_identity": summary["world"]["identity"],
        "runtime_world_level_name": runtime_world_level_name,
        "world_identity_marker_sha256": marker_hash,
        "server_properties_sha256": properties_sha256,
        "runtime_latest_log_sha256": runtime_hash,
        "full_log_sha256": source_sha256,
        "server_exit_code": server_exit_code,
        "receipt_sha256": receipt_sha256,
    }
    return binding, properties_payload, properties_document


def _chunk(chunk_type: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(
        ">I", zlib.crc32(chunk_type + data) & 0xFFFFFFFF
    )


def inspect_png(path: Path) -> dict[str, Any]:
    content = read_bounded_bytes(path, MAX_PNG_BYTES, "PNG")
    size = len(content)
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


def extract_log_excerpt_from_text(
    raw_text: str,
    line_start: int,
    line_end: int,
    player_names: list[str],
    *,
    source_label: str = "log source",
) -> tuple[bytes, dict[str, int]]:
    if line_start < 1 or line_end < line_start:
        raise ValueError("log line range must be positive and ordered")
    if line_end - line_start + 1 > MAX_EXCERPT_LINES:
        raise ValueError(f"log excerpt exceeds {MAX_EXCERPT_LINES} lines")

    pem_redacted, pem_spans = _redact_pem_private_keys(raw_text)
    if PEM_PRIVATE_KEY_MARKER.search(pem_redacted):
        raise ValueError(
            "log source contains an incomplete or oversized PEM private-key block"
        )
    selected = pem_redacted.splitlines()[line_start - 1 : line_end]
    if len(selected) != line_end - line_start + 1:
        raise ValueError(
            f"log does not contain requested line range: {source_label}"
        )
    text, counts = redact_text("\n".join(selected) + "\n", player_names)
    counts["credential"] += sum(
        1
        for start, end in pem_spans
        if not (
            raw_text.count("\n", 0, end) + 1 < line_start
            or raw_text.count("\n", 0, start) + 1 > line_end
        )
    )
    encoded = text.encode("utf-8")
    if len(encoded) > MAX_EXCERPT_BYTES:
        raise ValueError(f"redacted log excerpt exceeds {MAX_EXCERPT_BYTES} bytes")
    findings = privacy_findings(text, player_names)
    if findings:
        raise ValueError("redacted log still contains: " + ", ".join(findings))
    return encoded, counts


def extract_log_excerpt(
    path: Path,
    line_start: int,
    line_end: int,
    player_names: list[str],
) -> tuple[bytes, dict[str, int]]:
    _, raw_text, _, _, _ = read_log_snapshot(path)
    return extract_log_excerpt_from_text(
        raw_text,
        line_start,
        line_end,
        player_names,
        source_label=str(path),
    )


def lifecycle_marker_result(role: str, text: str) -> dict[str, Any] | None:
    markers = LIFECYCLE_MARKERS.get(role)
    if markers is None:
        return None
    positions: dict[str, int] = {}
    cursor = 0
    valid = True
    lines = text.splitlines()
    for label, marker in markers:
        position = next(
            (
                index
                for index, line in enumerate(lines[cursor:], start=cursor)
                if marker.search(line) is not None
            ),
            -1,
        )
        positions[label] = position
        if position < 0:
            valid = False
        else:
            cursor = position + 1
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
            "client": (
                "build/v0.0.2-manual/client-matching/mods/"
                f"{artifact_filename}"
            ),
        },
        "client_profiles": {
            role: {
                "status": "MISSING",
                "game_directory": "",
                "before_snapshot": "",
                "after_snapshot": "",
                "note": "No before/after client profile inventory was captured.",
            }
            for role in PROFILE_ROLES
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
                "warning_disposition": {
                    "status": "PENDING",
                    "warning_count": None,
                    "origins": [],
                    "explanation": "",
                },
                **(
                    {"server_exit_code": None, "receipt": ""}
                    if role == MISMATCH_SERVER_LOG_ROLE
                    else {}
                ),
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
            "client_profiles",
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

    client_profiles = _exact_keys(
        top.get("client_profiles"), set(PROFILE_ROLES), "client_profiles", errors
    )
    for role in PROFILE_ROLES:
        profile = _exact_keys(
            client_profiles.get(role),
            {
                "status",
                "game_directory",
                "before_snapshot",
                "after_snapshot",
                "note",
            },
            f"client_profiles.{role}",
            errors,
        )
        if profile.get("status") not in {"PRESENT", "MISSING"}:
            errors.append(
                f"client_profiles.{role}.status must be PRESENT or MISSING"
            )
        for field in (
            "game_directory",
            "before_snapshot",
            "after_snapshot",
            "note",
        ):
            if not isinstance(profile.get(field), str):
                errors.append(f"client_profiles.{role}.{field} must be a string")
        if profile.get("status") == "PRESENT" and any(
            not profile.get(field)
            for field in ("game_directory", "before_snapshot", "after_snapshot")
        ):
            errors.append(
                f"client_profiles.{role} PRESENT requires a game directory and "
                "both snapshots"
            )
        if profile.get("status") == "MISSING" and (
            profile.get("game_directory")
            or profile.get("before_snapshot")
            or profile.get("after_snapshot")
            or not profile.get("note", "").strip()
        ):
            errors.append(
                f"client_profiles.{role} MISSING requires empty paths and a reason"
            )

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
            keys = {"status", "source", "note"} | (
                {"line_start", "line_end", "warning_disposition"}
                if label == "log_excerpts"
                else set()
            )
            if label == "log_excerpts" and role == MISMATCH_SERVER_LOG_ROLE:
                keys.update({"server_exit_code", "receipt"})
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
            if label == "log_excerpts":
                disposition = _exact_keys(
                    item.get("warning_disposition"),
                    {"status", "warning_count", "origins", "explanation"},
                    f"log_excerpts.{role}.warning_disposition",
                    errors,
                )
                disposition_status = disposition.get("status")
                if disposition_status not in {
                    "PENDING",
                    "NONE",
                    "ACCEPTED",
                    "UNRESOLVED",
                }:
                    errors.append(
                        f"log_excerpts.{role}.warning_disposition.status is invalid"
                    )
                warning_count = disposition.get("warning_count")
                if warning_count is not None and (
                    not isinstance(warning_count, int)
                    or isinstance(warning_count, bool)
                    or warning_count < 0
                ):
                    errors.append(
                        f"log_excerpts.{role}.warning_disposition.warning_count "
                        "must be null or a non-negative integer"
                    )
                origins = disposition.get("origins")
                if (
                    not isinstance(origins, list)
                    or len(origins) > 16
                    or any(
                        not isinstance(origin, str)
                        or not origin.strip()
                        or len(origin) > 200
                        for origin in origins
                    )
                ):
                    errors.append(
                        f"log_excerpts.{role}.warning_disposition.origins must be "
                        "a list of at most 16 non-empty strings"
                    )
                    origins = []
                explanation = disposition.get("explanation")
                if not isinstance(explanation, str) or len(explanation) > 2000:
                    errors.append(
                        f"log_excerpts.{role}.warning_disposition.explanation "
                        "must be a string of at most 2000 characters"
                    )
                    explanation = ""
                if item.get("status") == "MISSING" and disposition_status != "PENDING":
                    errors.append(
                        f"log_excerpts.{role} MISSING requires a PENDING warning disposition"
                    )
                if disposition_status == "PENDING" and (
                    warning_count is not None or origins or explanation
                ):
                    errors.append(
                        f"log_excerpts.{role} PENDING warning disposition must be empty"
                    )
                if disposition_status == "NONE" and (
                    warning_count != 0 or origins or explanation
                ):
                    errors.append(
                        f"log_excerpts.{role} NONE warning disposition must record zero"
                    )
                if disposition_status in {"ACCEPTED", "UNRESOLVED"} and (
                    not isinstance(warning_count, int)
                    or warning_count <= 0
                    or not origins
                    or not explanation.strip()
                ):
                    errors.append(
                        f"log_excerpts.{role} {disposition_status} warning disposition "
                        "requires a positive count, origins, and explanation"
                    )
                if role == MISMATCH_SERVER_LOG_ROLE:
                    exit_code = item.get("server_exit_code")
                    receipt = item.get("receipt")
                    if item.get("status") == "MISSING" and exit_code is not None:
                        errors.append(
                            "mismatch server MISSING requires a null server_exit_code"
                        )
                    if item.get("status") == "MISSING" and receipt != "":
                        errors.append(
                            "mismatch server MISSING requires an empty receipt path"
                        )
                    if item.get("status") == "PRESENT" and (
                        not isinstance(exit_code, int) or isinstance(exit_code, bool)
                    ):
                        errors.append(
                            "mismatch server PRESENT requires an integer server_exit_code"
                        )
                    if item.get("status") == "PRESENT" and (
                        not isinstance(receipt, str) or not receipt
                    ):
                        errors.append(
                            "mismatch server PRESENT requires a receipt path"
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
            "same_player_verified",
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
    if top.get("same_player_verified") is not True:
        errors.append("server summary must confirm the same player across both cycles")
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
        "player_identity_binding",
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
        if not isinstance(item.get("player_identity_binding"), str) or SHA256_RE.fullmatch(
            item.get("player_identity_binding", "")
        ) is None:
            errors.append(
                f"server summary {expected_name} player identity binding is invalid"
            )
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

    if len(cycles) == 2 and (
        cycles["first-start"].get("player_identity_binding")
        != cycles["restart"].get("player_identity_binding")
    ):
        errors.append("server summary cycles do not bind the same player identity")

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
            "server_properties_sha256",
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
        "server_properties_sha256",
    ):
        if not isinstance(world.get(field), str) or SHA256_RE.fullmatch(
            world.get(field, "")
        ) is None:
            errors.append(f"server summary world {field} is invalid")
    properties_sha256 = world.get("server_properties_sha256")
    if isinstance(port, int) and not isinstance(port, bool) and 1 <= port <= 65535:
        expected_properties_sha256 = hashlib.sha256(
            server_configuration_payload(port)
        ).hexdigest()
        if properties_sha256 != expected_properties_sha256:
            errors.append(
                "server summary world server_properties_sha256 differs from "
                "the canonical harness startup payload"
            )
    before_hash = world.get("level_dat_before_restart_sha256")
    if (
        session_id
        and isinstance(before_hash, str)
        and SHA256_RE.fullmatch(before_hash) is not None
        and isinstance(properties_sha256, str)
        and SHA256_RE.fullmatch(properties_sha256) is not None
    ):
        expected_identity = hashlib.sha256(
            (
                f"{session_id}\0{artifact_metadata['sha256']}\0{before_hash}\0"
                f"{properties_sha256}"
            ).encode("utf-8")
        ).hexdigest()
        if world.get("identity") != expected_identity:
            errors.append(
                "server summary world identity is not bound to the startup "
                "properties, artifact, session, and first level.dat"
            )
    for field in ("level_dat_after_restart_size", "level_dat_before_restart_size"):
        size = world.get(field)
        if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
            errors.append(f"server summary world {field} must be positive")
    return errors, cycles


def _merge_counts(total: dict[str, int], added: dict[str, int]) -> None:
    for key in PRIVACY_CATEGORIES:
        total[key] += added[key]


def source_audit_consistency_errors(
    audits: dict[str, dict[str, Any]],
) -> list[str]:
    errors: list[str] = []
    seen: dict[str, tuple[str, str, dict[str, Any]]] = {}
    for role, audit in audits.items():
        source_path = str(audit.get("source_path", ""))
        portable_key = source_path.casefold()
        previous = seen.get(portable_key)
        if previous is None:
            seen[portable_key] = (role, source_path, audit)
            continue
        previous_role, previous_path, previous_audit = previous
        if source_path != previous_path:
            errors.append(
                f"raw log paths for {previous_role} and {role} differ only by case"
            )
        elif audit != previous_audit:
            errors.append(
                f"raw log audits for shared source {source_path} differ between "
                f"{previous_role} and {role}"
            )
    return errors


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
    for role in PROFILE_ROLES:
        profile = record.get("client_profiles", {}).get(role, {})
        if not isinstance(profile, dict) or profile.get("status") != "PRESENT":
            blockers.append(f"{role} client profile before/after binding is missing")
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
    seen_sources: set[str] = set()
    for role, item in record["log_excerpts"].items():
        if not isinstance(item, dict) or item.get("status") != "PRESENT":
            continue
        source_audit = item.get("source_audit", {})
        source_identity = str(source_audit.get("source_path", ""))
        audit_counts = source_audit.get("audit_counts", {})
        if source_identity not in seen_sources:
            seen_sources.add(source_identity)
            error_count = audit_counts.get("error_count")
            if isinstance(error_count, int) and error_count > 0:
                blockers.append(
                    f"{role} raw log has {error_count} broad ERROR finding(s)"
                )
        disposition = item.get("warning_disposition", {})
        if disposition.get("status") not in {"NONE", "ACCEPTED"}:
            blockers.append(
                f"{role} warning disposition is {disposition.get('status')}, "
                "not NONE or ACCEPTED"
            )
    for role in SERVER_LOG_CYCLES:
        item = record["log_excerpts"].get(role, {})
        counts = item.get("redaction_counts", {})
        if not isinstance(counts, dict) or counts.get("player_name", 0) < 2:
            blockers.append(f"{role} did not redact its join/leave player identity")
    mismatch_item = record["log_excerpts"].get(MISMATCH_SERVER_LOG_ROLE, {})
    if not isinstance(mismatch_item.get("mismatch_server_binding"), dict):
        blockers.append("missing-mod third server binding is missing")
    if not isinstance(mismatch_item.get("server_properties"), dict):
        blockers.append("missing-mod third server properties archive is missing")
    client_mismatch_item = record["log_excerpts"].get("mismatch_attempt", {})
    connection_attempts = (
        mismatch_item.get("connection_attempt_marker", {}),
        client_mismatch_item.get("connection_attempt_marker", {}),
    )
    if not any(
        isinstance(marker, dict)
        and marker.get("count", 0) >= 1
        and marker.get("target_verified") is True
        and marker.get("order_valid") is True
        for marker in connection_attempts
    ):
        blockers.append(
            "missing-mod evidence has no logger-anchored connection-attempt marker "
            "bound to the harness loopback host and port"
        )
    if mismatch_item.get("server_exit_code") != 0:
        blockers.append(
            "missing-mod third server exit code is "
            f"{mismatch_item.get('server_exit_code')}, not 0"
        )
    return blockers


def collect_evidence(
    session_path: Path,
    output_dir: Path,
    repository_root: Path = ROOT,
    *,
    require_acceptance_ready: bool = False,
) -> tuple[list[str], dict[str, Any] | None]:
    errors: list[str] = []
    try:
        session_path = resolve_build_path(session_path, repository_root, must_exist=True, require_file=True)
        output_dir, output_mode = resolve_bundle_path(
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
    try:
        source_revision = build_source_revision(
            repository_root,
            session["metadata"]["source_commit"],
            require_head=True,
        )
    except (OSError, UnicodeError, ValueError) as exc:
        return [str(exc)], None

    payloads: dict[str, bytes] = {}
    artifacts: dict[str, dict[str, Any]] = {}
    resolved_artifacts: list[Path] = []
    resolved_artifact_roles: dict[str, Path] = {}
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
            resolved_artifact_roles[role] = path
            artifacts[role] = {
                "path": relative,
                "filename": path.name,
                "sha256": digest,
                "size": path.stat().st_size,
            }
        except (OSError, ValueError) as exc:
            errors.append(str(exc))

    client_profiles_record: dict[str, dict[str, Any]] = {}
    resolved_profile_dirs: dict[str, Path] = {}
    resolved_snapshot_sources: list[Path] = []
    inventory_fields = (
        "artifact_filename",
        "artifact_sha256",
        "game_directory",
        "mods_directory",
        "mods_files",
        "profile_role",
        "inventory_sha256",
    )
    for role in PROFILE_ROLES:
        item = session["client_profiles"][role]
        if item["status"] == "MISSING":
            client_profiles_record[role] = {
                "status": "MISSING",
                "note": item["note"],
            }
            continue
        try:
            game_directory = resolve_build_path(
                item["game_directory"],
                repository_root,
                must_exist=True,
            )
            if not game_directory.is_dir():
                raise ValueError(
                    f"{role} client profile game directory must be a directory"
                )
            game_relative = relative_build_path(game_directory, repository_root)
            if privacy_findings(game_relative, player_names):
                raise ValueError(
                    f"{role} client profile path contains private data: "
                    + game_relative
                )

            snapshot_documents: dict[str, dict[str, Any]] = {}
            snapshot_records: dict[str, dict[str, Any]] = {}
            for phase in PROFILE_PHASES:
                source = resolve_build_path(
                    item[f"{phase}_snapshot"],
                    repository_root,
                    must_exist=True,
                    require_file=True,
                )
                if any(
                    source == previous or os.path.samefile(source, previous)
                    for previous in resolved_snapshot_sources
                ):
                    raise ValueError(
                        "client profile before/after snapshots must be distinct "
                        "physical files"
                    )
                resolved_snapshot_sources.append(source)
                source_relative = relative_build_path(source, repository_root)
                if privacy_findings(source_relative, player_names):
                    raise ValueError(
                        f"{role} {phase} profile snapshot path contains private data"
                    )
                source_payload = read_bounded_bytes(
                    source,
                    MAX_JSON_BYTES,
                    f"{role} {phase} profile snapshot",
                )
                document = parse_json_payload(
                    source_payload, f"{role} {phase} profile snapshot"
                )
                document = validate_profile_snapshot_document(
                    document,
                    expected_role=role,
                    expected_phase=phase,
                    expected_game_directory=game_relative,
                    artifact_metadata=artifact_metadata,
                    expected_artifact_size=artifacts.get("client", {}).get("size"),
                )
                archive_payload = canonical_json_payload(document)
                if source_payload != archive_payload:
                    raise ValueError(
                        f"{role} {phase} profile snapshot is not canonical JSON"
                    )
                if privacy_findings_in_value(document, player_names):
                    raise ValueError(
                        f"{role} {phase} profile snapshot contains private data"
                    )
                archive = PROFILE_ARCHIVES[(role, phase)]
                payloads[archive] = archive_payload
                snapshot_documents[phase] = document
                snapshot_records[phase] = {
                    "source_path": source_relative,
                    "file": archive,
                    "source_sha256": hashlib.sha256(source_payload).hexdigest(),
                    "sha256": hashlib.sha256(archive_payload).hexdigest(),
                    "size": len(archive_payload),
                    "captured_at": document["captured_at"],
                }

            before_time = dt.datetime.fromisoformat(
                snapshot_documents["before"]["captured_at"]
            )
            after_time = dt.datetime.fromisoformat(
                snapshot_documents["after"]["captured_at"]
            )
            if after_time < before_time:
                raise ValueError(
                    f"{role} client profile after snapshot predates before snapshot"
                )
            if any(
                snapshot_documents["before"][field]
                != snapshot_documents["after"][field]
                for field in inventory_fields
            ):
                raise ValueError(
                    f"{role} client profile changed between before and after snapshots"
                )
            current_inventory = inspect_profile_inventory(
                game_directory,
                profile_role=role,
                artifact_metadata=artifact_metadata,
                repository_root=repository_root,
            )
            if any(
                current_inventory[field] != snapshot_documents["after"][field]
                for field in inventory_fields
            ):
                raise ValueError(
                    f"{role} client profile no longer matches its after snapshot"
                )
            if role == "matching":
                client_artifact = resolved_artifact_roles.get("client")
                expected_client_artifact = (
                    game_directory / "mods" / artifact_metadata["filename"]
                )
                if (
                    client_artifact is None
                    or not expected_client_artifact.is_file()
                    or not os.path.samefile(client_artifact, expected_client_artifact)
                ):
                    raise ValueError(
                        "artifacts.client must be the exact JAR in the matching "
                        "client profile"
                    )
            resolved_profile_dirs[role] = game_directory
            client_profiles_record[role] = {
                "status": "PRESENT",
                "game_directory": game_relative,
                "mods_directory": snapshot_documents["after"]["mods_directory"],
                "artifact_filename": artifact_metadata["filename"],
                "artifact_sha256": artifact_metadata["sha256"],
                "inventory_sha256": snapshot_documents["after"][
                    "inventory_sha256"
                ],
                "before_snapshot": snapshot_records["before"],
                "after_snapshot": snapshot_records["after"],
                "note": item["note"],
            }
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(f"client_profiles.{role}: {exc}")
            client_profiles_record[role] = {
                "status": "MISSING",
                "note": "Invalid client profile evidence was not archived.",
            }

    if len(resolved_profile_dirs) == len(PROFILE_ROLES):
        matching_directory = resolved_profile_dirs["matching"]
        missing_directory = resolved_profile_dirs["missing_mod"]
        try:
            matching_recorded = client_profiles_record["matching"][
                "game_directory"
            ]
            missing_recorded = client_profiles_record["missing_mod"][
                "game_directory"
            ]
            if recorded_paths_overlap(matching_recorded, missing_recorded):
                errors.append(
                    "matching and missing-project-mod client profile paths must be "
                    "disjoint, not the same path or an ancestor/descendant pair"
                )
            if resolved_paths_overlap(matching_directory, missing_directory):
                errors.append(
                    "matching and missing-project-mod client profile physical "
                    "directories must be disjoint, not the same directory or an "
                    "ancestor/descendant pair"
                )
        except OSError as exc:
            errors.append(f"cannot compare client profile directories: {exc}")

    server_harness_record: dict[str, Any]
    summary_cycles: dict[str, dict[str, Any]] = {}
    server_summary: dict[str, Any] = {}
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
            summary_source_payload, summary = load_json_payload(
                summary_source, "server harness summary source"
            )
            summary_errors, summary_cycles = validate_server_summary(
                summary, artifact_metadata
            )
            if summary_errors:
                raise ValueError("; ".join(summary_errors))
            server_summary = summary
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
                "source_sha256": hashlib.sha256(
                    summary_source_payload
                ).hexdigest(),
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

    if server_summary:
        profile_captures = {
            role: {
                phase: client_profiles_record[role][f"{phase}_snapshot"][
                    "captured_at"
                ]
                for phase in PROFILE_PHASES
            }
            for role in PROFILE_ROLES
            if client_profiles_record.get(role, {}).get("status") == "PRESENT"
        }
        errors.extend(profile_capture_timeline_errors(profile_captures, server_summary))

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
    resolved_client_log_sources: list[tuple[str, str, Path]] = []
    collected_log_snapshots: list[
        tuple[Path, tuple[bytes, str, dict[str, int], str, int]]
    ] = []
    observed_player_names: dict[str, str] = {}
    for role, item in session["log_excerpts"].items():
        if item["status"] == "MISSING":
            log_record[role] = {
                "status": "MISSING",
                "note": item["note"],
                "warning_disposition": copy.deepcopy(item["warning_disposition"]),
                **(
                    {"server_exit_code": None}
                    if role == MISMATCH_SERVER_LOG_ROLE
                    else {}
                ),
            }
            continue
        try:
            source = resolve_build_path(item["source"], repository_root, must_exist=True, require_file=True)
            relative = relative_build_path(source, repository_root)
            if privacy_findings(relative, player_names):
                raise ValueError(f"log path contains private data: {relative}")
            profile_role = CLIENT_LOG_PROFILES.get(role)
            if profile_role is not None:
                profile_record = client_profiles_record.get(profile_role, {})
                if profile_record.get("status") == "PRESENT" and not recorded_path_is_below(
                    relative, f"{profile_record['game_directory']}/logs"
                ):
                    raise ValueError(
                        f"{role} raw log must remain under the {profile_role} "
                        "client profile logs directory"
                    )
                for previous_profile, previous_role, previous_source in (
                    resolved_client_log_sources
                ):
                    if previous_profile != profile_role and os.path.samefile(
                        source, previous_source
                    ):
                        raise ValueError(
                            f"{role} and {previous_role} client raw logs must not "
                            "reuse one physical file or hard link across matching "
                            "and missing-project-mod profiles"
                        )
                resolved_client_log_sources.append((profile_role, role, source))
            snapshot = next(
                (
                    previous_snapshot
                    for previous_source, previous_snapshot in collected_log_snapshots
                    if source == previous_source
                    or os.path.samefile(source, previous_source)
                ),
                None,
            )
            if snapshot is None:
                snapshot = read_log_snapshot(source)
                collected_log_snapshots.append((source, snapshot))
            (
                raw_payload,
                raw_text,
                raw_audit_counts,
                raw_sha256,
                raw_size,
            ) = snapshot
            warning_disposition = copy.deepcopy(item["warning_disposition"])
            warning_status = warning_disposition["status"]
            warning_count = raw_audit_counts["warning_count"]
            if warning_count == 0:
                if warning_status != "NONE" or warning_disposition["warning_count"] != 0:
                    raise ValueError(
                        f"{role} clean raw log requires a NONE warning disposition"
                    )
            elif (
                warning_status not in {"ACCEPTED", "UNRESOLVED"}
                or warning_disposition["warning_count"] != warning_count
            ):
                raise ValueError(
                    f"{role} raw warning count {warning_count} requires a matching "
                    "ACCEPTED or UNRESOLVED disposition"
                )
            raw_log_audits[role] = {
                "source_path": relative,
                "sha256": raw_sha256,
                "size": raw_size,
                "audit_counts": raw_audit_counts,
                **(
                    {
                        "profile_role": profile_role,
                        "physical_file_identity": physical_file_identity(source),
                    }
                    if profile_role is not None
                    else {}
                ),
            }
            payload, counts = extract_log_excerpt_from_text(
                raw_text,
                item["line_start"],
                item["line_end"],
                player_names,
                source_label=str(source),
            )
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
                "warning_disposition": warning_disposition,
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
                    identity = parse_player_lifecycle(raw_text, role)
                    joined_player = identity["player_name"]
                    supplied = next(
                        (
                            name for name in player_names
                            if name.casefold() == joined_player.casefold()
                        ),
                        None,
                    )
                    if supplied is None:
                        raise ValueError(
                            f"{role} player identity is absent from privacy.player_names"
                        )
                    if not server_summary:
                        raise ValueError(
                            f"{role} cannot bind identity without a valid server summary"
                        )
                    if counts["player_name"] < 2 or joined_player in excerpt_text:
                        raise ValueError(
                            f"{role} join/leave player identity was not fully redacted"
                        )
                    observed_player_names[cycle_name] = joined_player.casefold()
                    log_record[role]["player_identity_binding"] = cycle[
                        "player_identity_binding"
                    ]
                log_record[role]["harness_cycle_id"] = cycle_id
            if role == "mismatch_attempt":
                if not server_summary:
                    raise ValueError(
                        "mismatch client evidence requires a valid player harness summary"
                    )
                log_record[role]["connection_attempt_marker"] = (
                    client_connection_attempt_marker_result(
                        excerpt_text,
                        expected_host=server_summary["server_bind"],
                        expected_port=server_summary["server_port"],
                    )
                )
            if role == MISMATCH_SERVER_LOG_ROLE:
                if not server_summary or "server" not in resolved_artifact_roles:
                    raise ValueError(
                        "mismatch server evidence requires a valid player harness summary"
                    )
                receipt_source = resolve_build_path(
                    item["receipt"],
                    repository_root,
                    must_exist=True,
                    require_file=True,
                )
                receipt_relative = relative_build_path(
                    receipt_source, repository_root
                )
                if privacy_findings(receipt_relative, player_names):
                    raise ValueError(
                        "mismatch server receipt path contains private data: "
                        + receipt_relative
                    )
                receipt_source_payload, receipt_source_document = load_json_payload(
                    receipt_source, "mismatch server receipt source"
                )
                receipt_source_sha256 = hashlib.sha256(
                    receipt_source_payload
                ).hexdigest()
                receipt_document = validate_mismatch_receipt(
                    receipt_source_document,
                    full_log_sha256=raw_sha256,
                    expected_exit_code=item["server_exit_code"],
                )
                receipt_payload = mismatch_receipt_payload(receipt_document)
                receipt_sha256 = hashlib.sha256(receipt_payload).hexdigest()
                payloads[MISMATCH_RECEIPT_ARCHIVE] = receipt_payload
                log_record[role]["receipt"] = {
                    "source_path": receipt_relative,
                    "file": MISMATCH_RECEIPT_ARCHIVE,
                    "source_sha256": receipt_source_sha256,
                    "sha256": receipt_sha256,
                    "size": len(receipt_payload),
                    **receipt_document,
                }
                connection_attempt = server_connection_attempt_marker_result(
                    excerpt_text,
                    expected_host=server_summary["server_bind"],
                    expected_port=server_summary["server_port"],
                )
                log_record[role]["connection_attempt_marker"] = connection_attempt
                (
                    mismatch_binding,
                    properties_payload,
                    properties_document,
                ) = build_mismatch_server_binding(
                    source_log=source,
                    source_payload=raw_payload,
                    source_sha256=raw_sha256,
                    server_artifact=resolved_artifact_roles["server"],
                    summary=server_summary,
                    server_exit_code=receipt_document["exit_code"],
                    receipt_sha256=receipt_sha256,
                )
                properties_sha256 = hashlib.sha256(properties_payload).hexdigest()
                payloads[MISMATCH_PROPERTIES_ARCHIVE] = properties_payload
                log_record[role]["server_properties"] = {
                    "file": MISMATCH_PROPERTIES_ARCHIVE,
                    "sha256": properties_sha256,
                    "size": len(properties_payload),
                    **properties_document,
                }
                log_record[role]["mismatch_server_binding"] = mismatch_binding
                log_record[role]["server_exit_code"] = receipt_document["exit_code"]
        except (OSError, UnicodeError, ValueError) as exc:
            errors.append(f"log_excerpts.{role}: {exc}")

    if len(observed_player_names) == 2 and (
        observed_player_names.get("first-start")
        != observed_player_names.get("restart")
    ):
        errors.append("matching server cycles did not observe the same player identity")

    errors.extend(source_audit_consistency_errors(raw_log_audits))

    def merge_unique_raw_audits(roles: set[str]) -> dict[str, int]:
        total = {field: 0 for field in LOG_AUDIT_FIELDS}
        seen: set[str] = set()
        for role in roles:
            audit = raw_log_audits.get(role)
            if audit is None:
                continue
            identity = str(audit["source_path"])
            if identity in seen:
                continue
            seen.add(identity)
            for field in LOG_AUDIT_FIELDS:
                total[field] += int(audit["audit_counts"][field])
        return total

    client_audit = merge_unique_raw_audits(CLIENT_LOG_ROLES)
    server_audit = merge_unique_raw_audits(SERVER_LOG_ROLES)
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
        "source_revision": source_revision,
        "artifact_manifest": {
            "path": CONTENT_MANIFEST.as_posix(),
            "sha256": file_sha256(manifest_path),
            "artifact_filename": artifact_metadata["filename"],
            "artifact_sha256": artifact_metadata["sha256"],
        },
        "metadata": sanitized["metadata"],
        "artifacts": artifacts,
        "client_profiles": _sanitize(
            client_profiles_record, player_names, redaction_counts
        ),
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
    if require_acceptance_ready and blockers:
        return [
            "evidence is not mechanically ready for human Gate review: "
            + "; ".join(blockers)
        ], None
    serialized = json.dumps(record, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
    findings = privacy_findings_in_value(record)
    if findings:
        return ["generated record still contains: " + ", ".join(findings)], None

    staging_root = (
        repository_root.resolve() / "build" / ".v002-evidence-staging"
    )
    temporary: Path | None = None
    try:
        staging_root.mkdir(parents=True, exist_ok=True)
        _reject_link_components(
            staging_root.absolute(), (repository_root.resolve() / "build").absolute()
        )
        temporary = Path(
            tempfile.mkdtemp(prefix="bundle-", dir=staging_root)
        )
        for relative, payload in payloads.items():
            target = temporary / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
        (temporary / RECORD_NAME).write_text(serialized, encoding="utf-8", newline="\n")
        validation_modes = [output_mode]
        if output_mode != "build":
            # The committed bundle intentionally omits raw inputs. Re-run the
            # staged record in build mode immediately before publication so a
            # source changed during collection cannot escape the archive check.
            validation_modes.append("build")
        for validation_mode in validation_modes:
            validation_errors, _ = validate_bundle(
                temporary,
                repository_root,
                require_acceptance_ready=require_acceptance_ready,
                _validation_mode=validation_mode,
            )
            if validation_errors:
                return [
                    f"staged {validation_mode}-mode evidence failed self-validation: "
                    + error
                    for error in validation_errors
                ], None

        output_dir.parent.mkdir(parents=True, exist_ok=True)
        _reject_link_components(
            output_dir.parent.absolute(), repository_root.resolve().absolute()
        )
        if output_dir.exists():
            raise ValueError(f"output directory already exists: {output_dir}")
        os.replace(temporary, output_dir)
        temporary = None
    except (OSError, ValueError) as exc:
        return [str(exc)], None
    finally:
        if temporary is not None and temporary.exists():
            shutil.rmtree(temporary)
        try:
            staging_root.rmdir()
        except OSError:
            pass
    return [], record


def validate_bundle(
    bundle_dir: Path,
    repository_root: Path = ROOT,
    *,
    require_acceptance_ready: bool = False,
    _validation_mode: str | None = None,
) -> tuple[list[str], dict[str, Any] | None]:
    errors: list[str] = []
    try:
        bundle, detected_bundle_mode = resolve_bundle_path(
            bundle_dir, repository_root, must_exist=True
        )
        if _validation_mode not in {None, "build", "committed"}:
            raise ValueError("internal bundle validation mode is invalid")
        bundle_mode = _validation_mode or detected_bundle_mode
        if not bundle.is_dir():
            raise ValueError("bundle path must be a directory")
        record_path = bundle / RECORD_NAME
        if _is_link(record_path) or not record_path.is_file():
            raise ValueError(f"bundle is missing safe {RECORD_NAME}")
        record = load_json(record_path)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        return [str(exc)], None
    if not isinstance(record, dict):
        return ["manual evidence record must be an object"], None

    expected_top = {
        "schema_version", "version", "collector", "generated_at", "scope_statement",
        "source_revision", "artifact_manifest", "metadata", "artifacts", "client_profiles", "server_harness", "observations", "evidence",
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
    metadata_value = record.get("metadata")
    source_commit = (
        metadata_value.get("source_commit")
        if isinstance(metadata_value, dict)
        else None
    )
    errors.extend(
        validate_source_revision(
            record.get("source_revision"), repository_root, source_commit
        )
    )
    manifest_payload = b""
    artifact_metadata = {"filename": "", "sha256": ""}
    if isinstance(source_commit, str) and COMMIT_RE.fullmatch(source_commit):
        try:
            manifest_payload, artifact_metadata = load_content_manifest_at_commit(
                repository_root, source_commit
            )
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(str(exc))
    artifact_manifest = _exact_keys(
        record.get("artifact_manifest"), {"path", "sha256", "artifact_filename", "artifact_sha256"},
        "artifact_manifest", errors,
    )
    if (
        artifact_manifest.get("path") != CONTENT_MANIFEST.as_posix()
        or artifact_manifest.get("sha256")
        != hashlib.sha256(manifest_payload).hexdigest()
    ):
        errors.append("record is not bound to the source-commit content manifest")
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
    record_profiles = record.get("client_profiles")
    if not isinstance(record_profiles, dict):
        record_profiles = {}

    session_shape = {
        "schema_version": SCHEMA_VERSION, "version": VERSION,
        "metadata": record.get("metadata"),
        "artifacts": {
            role: item.get("path", "") if isinstance(item, dict) else ""
            for role, item in record_artifacts.items()
        },
        "client_profiles": {
            role: {
                "status": item.get("status"),
                "game_directory": item.get("game_directory", ""),
                "before_snapshot": (
                    item.get("before_snapshot", {}).get("source_path", "")
                    if isinstance(item.get("before_snapshot"), dict)
                    else ""
                ),
                "after_snapshot": (
                    item.get("after_snapshot", {}).get("source_path", "")
                    if isinstance(item.get("after_snapshot"), dict)
                    else ""
                ),
                "note": item.get("note", ""),
            }
            for role, item in record_profiles.items()
            if isinstance(item, dict)
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
                "warning_disposition": item.get("warning_disposition"),
                **(
                    {
                        "server_exit_code": item.get("server_exit_code"),
                        "receipt": (
                            item.get("receipt", {}).get("source_path", "")
                            if isinstance(item.get("receipt"), dict)
                            else ""
                        ),
                    }
                    if role == MISMATCH_SERVER_LOG_ROLE
                    else {}
                ),
            } for role, item in record_logs.items() if isinstance(item, dict)
        },
        "findings": record.get("findings"),
        "applicability_reviews": record.get("applicability_reviews"),
    }
    errors.extend(validate_session(session_shape))

    artifacts = _exact_keys(record.get("artifacts"), {"source", "server", "client"}, "artifacts", errors)
    resolved: list[Path] = []
    resolved_artifact_roles: dict[str, Path] = {}
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
                resolved_artifact_roles[role] = path
        except (OSError, ValueError) as exc:
            errors.append(str(exc))
    if recorded_sizes and len(set(recorded_sizes)) != 1:
        errors.append("recorded source/server/client JAR sizes differ")

    expected_files = {RECORD_NAME}
    _exact_keys(
        record_profiles, set(PROFILE_ROLES), "client_profiles", errors
    )
    validated_profile_paths: dict[str, str] = {}
    validated_profile_captures: dict[str, dict[str, str]] = {}
    resolved_validation_profile_dirs: dict[str, Path] = {}
    resolved_validation_snapshot_sources: list[Path] = []
    recorded_validation_snapshot_paths: set[str] = set()
    profile_inventory_fields = (
        "artifact_filename",
        "artifact_sha256",
        "game_directory",
        "mods_directory",
        "mods_files",
        "profile_role",
        "inventory_sha256",
    )
    for role in PROFILE_ROLES:
        item_value = record_profiles.get(role)
        item = item_value if isinstance(item_value, dict) else {}
        if item.get("status") == "MISSING":
            _exact_keys(
                item, {"status", "note"}, f"client_profiles.{role}", errors
            )
            continue
        if item.get("status") != "PRESENT":
            errors.append(
                f"client_profiles.{role}.status must be PRESENT or MISSING"
            )
            continue
        _exact_keys(
            item,
            {
                "status",
                "game_directory",
                "mods_directory",
                "artifact_filename",
                "artifact_sha256",
                "inventory_sha256",
                "before_snapshot",
                "after_snapshot",
                "note",
            },
            f"client_profiles.{role}",
            errors,
        )
        try:
            game_relative = validate_recorded_build_path(
                item.get("game_directory")
            )
            if privacy_findings(game_relative):
                raise ValueError(
                    f"client_profiles.{role} game directory contains private data"
                )
            expected_mods_relative = f"{game_relative}/mods"
            if item.get("mods_directory") != expected_mods_relative:
                raise ValueError(
                    f"client_profiles.{role} mods directory is invalid"
                )
            if (
                item.get("artifact_filename") != artifact_metadata["filename"]
                or item.get("artifact_sha256") != artifact_metadata["sha256"]
            ):
                raise ValueError(
                    f"client_profiles.{role} artifact binding differs from manifest"
                )
            inventory_sha256 = item.get("inventory_sha256")
            if (
                not isinstance(inventory_sha256, str)
                or SHA256_RE.fullmatch(inventory_sha256) is None
            ):
                raise ValueError(
                    f"client_profiles.{role} inventory SHA-256 is invalid"
                )

            documents: dict[str, dict[str, Any]] = {}
            for phase in PROFILE_PHASES:
                snapshot = _exact_keys(
                    item.get(f"{phase}_snapshot"),
                    {
                        "source_path",
                        "file",
                        "source_sha256",
                        "sha256",
                        "size",
                        "captured_at",
                    },
                    f"client_profiles.{role}.{phase}_snapshot",
                    errors,
                )
                source_relative = validate_recorded_build_path(
                    snapshot.get("source_path")
                )
                portable_source = source_relative.casefold()
                if portable_source in recorded_validation_snapshot_paths:
                    raise ValueError(
                        "client profile snapshot source paths are not distinct"
                    )
                recorded_validation_snapshot_paths.add(portable_source)
                if privacy_findings(source_relative):
                    raise ValueError(
                        f"client_profiles.{role} {phase} snapshot path contains "
                        "private data"
                    )
                archive = PROFILE_ARCHIVES[(role, phase)]
                expected_files.add(archive)
                archive_path = bundle / archive
                _reject_link_components(archive_path.absolute(), bundle.absolute())
                if _is_link(archive_path) or not archive_path.is_file():
                    raise ValueError(
                        f"bundle is missing safe {role} {phase} profile snapshot"
                    )
                archive_payload = read_bounded_bytes(
                    archive_path,
                    MAX_JSON_BYTES,
                    f"archived {role} {phase} profile snapshot",
                )
                archive_hash = hashlib.sha256(archive_payload).hexdigest()
                if (
                    snapshot.get("file") != archive
                    or snapshot.get("sha256") != archive_hash
                    or snapshot.get("source_sha256") != archive_hash
                    or snapshot.get("size") != len(archive_payload)
                ):
                    raise ValueError(
                        f"{role} {phase} profile snapshot archive metadata mismatch"
                    )
                document = validate_profile_snapshot_document(
                    parse_json_payload(
                        archive_payload,
                        f"archived {role} {phase} profile snapshot",
                    ),
                    expected_role=role,
                    expected_phase=phase,
                    expected_game_directory=game_relative,
                    artifact_metadata=artifact_metadata,
                    expected_artifact_size=(
                        record_artifacts.get("client", {}).get("size")
                        if isinstance(record_artifacts.get("client"), dict)
                        else None
                    ),
                )
                if archive_payload != canonical_json_payload(document):
                    raise ValueError(
                        f"archived {role} {phase} profile snapshot is not canonical"
                    )
                if snapshot.get("captured_at") != document["captured_at"]:
                    raise ValueError(
                        f"{role} {phase} profile capture time differs from archive"
                    )
                if privacy_findings_in_value(document):
                    raise ValueError(
                        f"archived {role} {phase} profile snapshot contains private data"
                    )
                documents[phase] = document
                if bundle_mode == "build":
                    source_path = resolve_build_path(
                        source_relative,
                        repository_root,
                        must_exist=True,
                        require_file=True,
                    )
                    if any(
                        source_path == previous
                        or os.path.samefile(source_path, previous)
                        for previous in resolved_validation_snapshot_sources
                    ):
                        raise ValueError(
                            "client profile snapshot source files are not distinct"
                        )
                    resolved_validation_snapshot_sources.append(source_path)
                    if read_bounded_bytes(
                        source_path,
                        MAX_JSON_BYTES,
                        f"{role} {phase} profile snapshot source",
                    ) != archive_payload:
                        raise ValueError(
                            f"{role} {phase} profile snapshot source no longer "
                            "matches its archive"
                        )

            before_time = dt.datetime.fromisoformat(documents["before"]["captured_at"])
            after_time = dt.datetime.fromisoformat(documents["after"]["captured_at"])
            if after_time < before_time:
                raise ValueError(
                    f"{role} client profile after snapshot predates before snapshot"
                )
            if any(
                documents["before"][field] != documents["after"][field]
                for field in profile_inventory_fields
            ):
                raise ValueError(
                    f"{role} client profile changed between before and after snapshots"
                )
            if (
                documents["after"]["inventory_sha256"] != inventory_sha256
                or documents["after"]["mods_directory"]
                != item.get("mods_directory")
            ):
                raise ValueError(
                    f"client_profiles.{role} record differs from its snapshots"
                )
            validated_profile_paths[role] = game_relative
            validated_profile_captures[role] = {
                phase: documents[phase]["captured_at"] for phase in PROFILE_PHASES
            }

            if role == "matching":
                expected_client_path = (
                    f"{game_relative}/mods/{artifact_metadata['filename']}"
                )
                client_record = record_artifacts.get("client", {})
                if (
                    not isinstance(client_record, dict)
                    or client_record.get("path") != expected_client_path
                ):
                    raise ValueError(
                        "artifacts.client is not the exact matching-profile JAR"
                    )
            if bundle_mode == "build":
                game_directory = resolve_build_path(
                    game_relative, repository_root, must_exist=True
                )
                if not game_directory.is_dir():
                    raise ValueError(
                        f"client_profiles.{role} game directory is not a directory"
                    )
                current_inventory = inspect_profile_inventory(
                    game_directory,
                    profile_role=role,
                    artifact_metadata=artifact_metadata,
                    repository_root=repository_root,
                )
                if any(
                    current_inventory[field] != documents["after"][field]
                    for field in profile_inventory_fields
                ):
                    raise ValueError(
                        f"{role} client profile no longer matches its after snapshot"
                    )
                resolved_validation_profile_dirs[role] = game_directory
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(str(exc))

    if len(validated_profile_paths) == len(PROFILE_ROLES):
        if recorded_paths_overlap(
            validated_profile_paths["matching"],
            validated_profile_paths["missing_mod"],
        ):
            errors.append(
                "matching and missing-project-mod client profile paths must be "
                "disjoint, not the same path or an ancestor/descendant pair"
            )
    if len(resolved_validation_profile_dirs) == len(PROFILE_ROLES):
        try:
            if resolved_paths_overlap(
                resolved_validation_profile_dirs["matching"],
                resolved_validation_profile_dirs["missing_mod"],
            ):
                errors.append(
                    "matching and missing-project-mod client profile physical "
                    "directories must be disjoint, not the same directory or an "
                    "ancestor/descendant pair"
                )
        except OSError as exc:
            errors.append(f"cannot compare client profile directories: {exc}")

    record_harness = record.get("server_harness")
    if not isinstance(record_harness, dict):
        record_harness = {}
    summary_cycles: dict[str, dict[str, Any]] = {}
    server_summary: dict[str, Any] = {}
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
            _reject_link_components(summary_path.absolute(), bundle.absolute())
            if _is_link(summary_path) or not summary_path.is_file():
                raise ValueError("bundle is missing the safe server harness summary")
            summary_payload = read_bounded_bytes(
                summary_path, MAX_JSON_BYTES, "server harness summary archive"
            )
            if (
                len(summary_payload) != record_harness.get("size")
                or hashlib.sha256(summary_payload).hexdigest()
                != record_harness.get("sha256")
                or record_harness.get("file") != SERVER_SUMMARY_ARCHIVE
            ):
                raise ValueError("server harness summary archive metadata mismatch")
            summary = parse_json_payload(
                summary_payload, "server harness summary archive"
            )
            summary_errors, summary_cycles = validate_server_summary(
                summary, artifact_metadata
            )
            if summary_errors:
                raise ValueError("; ".join(summary_errors))
            server_summary = summary
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
                raw_summary_payload = read_bounded_bytes(
                    raw_summary,
                    MAX_JSON_BYTES,
                    "server harness summary source",
                )
                if hashlib.sha256(raw_summary_payload).hexdigest() != source_hash:
                    raise ValueError("server harness source no longer matches its record")
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(str(exc))
    else:
        errors.append("server_harness.status must be PRESENT or MISSING")

    if server_summary:
        errors.extend(
            profile_capture_timeline_errors(
                validated_profile_captures, server_summary
            )
        )

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
            _reject_link_components(path.absolute(), bundle.absolute())
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
    validated_client_physical_identities: dict[str, tuple[str, str]] = {}
    resolved_validation_client_log_sources: list[tuple[str, str, Path]] = []
    validation_log_snapshots: list[
        tuple[Path, tuple[bytes, str, dict[str, int], str, int]]
    ] = []
    validated_player_names: dict[str, str] = {}
    for role, item_value in record_logs.items():
        if role not in LOG_ROLES:
            continue
        item = item_value if isinstance(item_value, dict) else {}
        if item.get("status") != "PRESENT":
            missing_keys = {"status", "note", "warning_disposition"}
            if role == MISMATCH_SERVER_LOG_ROLE:
                missing_keys.add("server_exit_code")
            _exact_keys(
                item,
                missing_keys,
                f"log_excerpts.{role}",
                errors,
            )
            continue
        log_keys = {
            "status", "source_path", "file", "sha256", "size", "line_start",
            "line_end", "note", "redaction_counts", "source_audit",
            "excerpt_audit_counts", "warning_disposition",
        }
        if role in LIFECYCLE_MARKERS:
            log_keys.add("lifecycle_markers")
        if role in SERVER_LOG_CYCLES:
            log_keys.update({"harness_cycle_id", "player_identity_binding"})
        if role == "mismatch_attempt":
            log_keys.add("connection_attempt_marker")
        if role == MISMATCH_SERVER_LOG_ROLE:
            log_keys.update(
                {
                    "connection_attempt_marker",
                    "mismatch_server_binding",
                    "server_exit_code",
                    "server_properties",
                    "receipt",
                }
            )
        _exact_keys(item, log_keys, f"log_excerpts.{role}", errors)
        expected_file = f"logs/{role}.txt"
        expected_files.add(expected_file)
        physical_payload: bytes | None = None
        raw_path: Path | None = None
        try:
            recorded_log_path = validate_recorded_build_path(
                item.get("source_path")
            )
            profile_role = CLIENT_LOG_PROFILES.get(role)
            if profile_role is not None and profile_role in validated_profile_paths:
                if not recorded_path_is_below(
                    recorded_log_path,
                    f"{validated_profile_paths[profile_role]}/logs",
                ):
                    raise ValueError(
                        f"{role} raw log is not bound to the {profile_role} "
                        "client profile"
                    )
            path = bundle / expected_file
            _reject_link_components(path.absolute(), bundle.absolute())
            if _is_link(path) or not path.is_file():
                raise ValueError(f"missing safe bundle log: {expected_file}")
            content = read_bounded_bytes(
                path, MAX_EXCERPT_BYTES, f"archived log excerpt {role}"
            )
            if hashlib.sha256(content).hexdigest() != item.get("sha256") or len(content) != item.get("size"):
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
            source_audit_keys = {"source_path", "sha256", "size", "audit_counts"}
            if profile_role is not None:
                source_audit_keys.update(
                    {"profile_role", "physical_file_identity"}
                )
            source_audit = _exact_keys(
                item.get("source_audit"),
                source_audit_keys,
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
            physical_identity = source_audit.get("physical_file_identity")
            if profile_role is not None:
                if source_audit.get("profile_role") != profile_role:
                    raise ValueError(
                        f"raw log audit client profile role is invalid: {role}"
                    )
                if (
                    not isinstance(physical_identity, str)
                    or SHA256_RE.fullmatch(physical_identity) is None
                ):
                    raise ValueError(
                        f"raw log physical file identity is invalid: {role}"
                    )
                previous_identity = validated_client_physical_identities.get(
                    physical_identity
                )
                if (
                    previous_identity is not None
                    and previous_identity[0] != profile_role
                ):
                    raise ValueError(
                        f"{role} and {previous_identity[1]} client raw logs reuse "
                        "one physical-file identity across matching and "
                        "missing-project-mod profiles"
                    )
                validated_client_physical_identities[physical_identity] = (
                    profile_role,
                    role,
                )
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
            warning_disposition = item.get("warning_disposition")
            if isinstance(warning_disposition, dict):
                disposition_count = warning_disposition.get("warning_count")
                if disposition_count != raw_counts.get("warning_count"):
                    raise ValueError(
                        f"{role} warning disposition count differs from raw log audit"
                    )
                if raw_counts.get("warning_count") == 0 and warning_disposition.get(
                    "status"
                ) != "NONE":
                    raise ValueError(
                        f"{role} clean raw log requires a NONE warning disposition"
                    )
                if raw_counts.get("warning_count", 0) > 0 and warning_disposition.get(
                    "status"
                ) not in {"ACCEPTED", "UNRESOLVED"}:
                    raise ValueError(
                        f"{role} warnings lack an accepted or unresolved disposition"
                    )
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
                if profile_role is not None:
                    for (
                        previous_profile,
                        previous_role,
                        previous_source,
                    ) in resolved_validation_client_log_sources:
                        if previous_profile != profile_role and os.path.samefile(
                            raw_path, previous_source
                        ):
                            raise ValueError(
                                f"{role} and {previous_role} client raw logs reuse "
                                "one physical file or hard link across matching and "
                                "missing-project-mod profiles"
                            )
                    resolved_validation_client_log_sources.append(
                        (profile_role, role, raw_path)
                    )
                physical_snapshot = next(
                    (
                        previous_snapshot
                        for previous_path, previous_snapshot in validation_log_snapshots
                        if raw_path == previous_path
                        or os.path.samefile(raw_path, previous_path)
                    ),
                    None,
                )
                if physical_snapshot is None:
                    physical_snapshot = read_log_snapshot(raw_path)
                    validation_log_snapshots.append((raw_path, physical_snapshot))
                (
                    physical_payload,
                    physical_text,
                    physical_counts,
                    physical_hash,
                    physical_size,
                ) = physical_snapshot
                if (
                    physical_hash != raw_hash
                    or physical_size != raw_size
                    or physical_counts != raw_counts
                ):
                    raise ValueError(f"raw log source no longer matches its audit: {role}")
                if (
                    profile_role is not None
                    and physical_file_identity(raw_path) != physical_identity
                ):
                    raise ValueError(
                        f"raw log physical file identity no longer matches: {role}"
                    )
                if role in SERVER_LOG_CYCLES:
                    raw_identity = parse_player_lifecycle(physical_text, role)
                    validated_player_names[SERVER_LOG_CYCLES[role]] = raw_identity[
                        "player_name"
                    ].casefold()
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
                    if item.get("player_identity_binding") != cycle.get(
                        "player_identity_binding"
                    ):
                        raise ValueError(
                            f"{role} player identity differs from its harness cycle"
                        )
                    for field in LOG_AUDIT_FIELDS:
                        if raw_counts.get(field) != cycle.get(field):
                            raise ValueError(
                                f"{role} raw log {field} differs from its "
                                "harness cycle"
                            )
                    if counts_for_log.get("player_name", 0) < 2:
                        raise ValueError(
                            f"{role} did not redact its join/leave player identity"
                        )
            if role == "mismatch_attempt":
                if not server_summary:
                    raise ValueError(
                        "mismatch client connection marker lacks a harness summary"
                    )
                connection_attempt = client_connection_attempt_marker_result(
                    text,
                    expected_host=server_summary["server_bind"],
                    expected_port=server_summary["server_port"],
                )
                if item.get("connection_attempt_marker") != connection_attempt:
                    raise ValueError(
                        "mismatch client connection-attempt marker record differs "
                        "from its archived excerpt"
                    )
            if role == MISMATCH_SERVER_LOG_ROLE:
                if not server_summary:
                    raise ValueError(
                        "mismatch server connection marker lacks a harness summary"
                    )
                connection_attempt = server_connection_attempt_marker_result(
                    text,
                    expected_host=server_summary["server_bind"],
                    expected_port=server_summary["server_port"],
                )
                if item.get("connection_attempt_marker") != connection_attempt:
                    raise ValueError(
                        "mismatch server connection-attempt marker record differs "
                        "from its archived excerpt"
                    )
                receipt = _exact_keys(
                    item.get("receipt"),
                    {
                        "source_path",
                        "file",
                        "source_sha256",
                        "sha256",
                        "size",
                        "schema_version",
                        "exit_code",
                        "full_log_sha256",
                    },
                    f"log_excerpts.{role}.receipt",
                    errors,
                )
                receipt_source_path = validate_recorded_build_path(
                    receipt.get("source_path")
                )
                expected_files.add(MISMATCH_RECEIPT_ARCHIVE)
                receipt_path = bundle / MISMATCH_RECEIPT_ARCHIVE
                _reject_link_components(receipt_path.absolute(), bundle.absolute())
                if _is_link(receipt_path) or not receipt_path.is_file():
                    raise ValueError(
                        "bundle is missing the safe mismatch server receipt"
                    )
                receipt_payload = read_bounded_bytes(
                    receipt_path,
                    MAX_JSON_BYTES,
                    "mismatch server receipt archive",
                )
                receipt_hash = hashlib.sha256(receipt_payload).hexdigest()
                if (
                    receipt.get("file") != MISMATCH_RECEIPT_ARCHIVE
                    or receipt.get("sha256") != receipt_hash
                    or receipt.get("size") != len(receipt_payload)
                ):
                    raise ValueError(
                        "mismatch server receipt archive metadata mismatch"
                    )
                receipt_document = validate_mismatch_receipt(
                    parse_json_payload(
                        receipt_payload, "mismatch server receipt archive"
                    ),
                    full_log_sha256=raw_hash,
                    expected_exit_code=item.get("server_exit_code"),
                )
                if receipt_payload != mismatch_receipt_payload(receipt_document):
                    raise ValueError(
                        "archived mismatch server receipt is not canonical JSON"
                    )
                for field in (
                    "schema_version",
                    "exit_code",
                    "full_log_sha256",
                ):
                    if receipt.get(field) != receipt_document[field]:
                        raise ValueError(
                            "mismatch server receipt record differs from archive: "
                            + field
                        )
                receipt_source_sha256 = receipt.get("source_sha256")
                if (
                    not isinstance(receipt_source_sha256, str)
                    or SHA256_RE.fullmatch(receipt_source_sha256) is None
                ):
                    raise ValueError(
                        "mismatch server receipt source SHA-256 is invalid"
                    )
                if bundle_mode == "build":
                    raw_receipt = resolve_build_path(
                        receipt_source_path,
                        repository_root,
                        must_exist=True,
                        require_file=True,
                    )
                    (
                        raw_receipt_payload,
                        raw_receipt_document,
                    ) = load_json_payload(
                        raw_receipt, "mismatch server receipt source"
                    )
                    if (
                        hashlib.sha256(raw_receipt_payload).hexdigest()
                        != receipt_source_sha256
                    ):
                        raise ValueError(
                            "mismatch server receipt source no longer matches its record"
                        )
                    physical_receipt = validate_mismatch_receipt(
                        raw_receipt_document,
                        full_log_sha256=raw_hash,
                        expected_exit_code=item.get("server_exit_code"),
                    )
                    if physical_receipt != receipt_document:
                        raise ValueError(
                            "mismatch server receipt archive differs from its source"
                        )
                properties_record = _exact_keys(
                    item.get("server_properties"),
                    {
                        "file",
                        "sha256",
                        "size",
                        "schema_version",
                        "server_bind",
                        "server_port",
                        "level_name",
                        "source_sha256",
                    },
                    f"log_excerpts.{role}.server_properties",
                    errors,
                )
                expected_files.add(MISMATCH_PROPERTIES_ARCHIVE)
                properties_path = bundle / MISMATCH_PROPERTIES_ARCHIVE
                _reject_link_components(properties_path.absolute(), bundle.absolute())
                if _is_link(properties_path) or not properties_path.is_file():
                    raise ValueError(
                        "bundle is missing the safe mismatch server properties archive"
                    )
                properties_payload = read_bounded_bytes(
                    properties_path,
                    MAX_JSON_BYTES,
                    "mismatch server properties archive",
                )
                properties_hash = hashlib.sha256(properties_payload).hexdigest()
                if (
                    properties_record.get("file") != MISMATCH_PROPERTIES_ARCHIVE
                    or properties_record.get("sha256") != properties_hash
                    or properties_record.get("size") != len(properties_payload)
                ):
                    raise ValueError(
                        "mismatch server properties archive metadata mismatch"
                    )
                if not server_summary:
                    raise ValueError(
                        "mismatch server properties archive lacks a harness summary"
                    )
                properties_document = validate_mismatch_properties(
                    parse_json_payload(
                        properties_payload,
                        "mismatch server properties archive",
                    ),
                    summary=server_summary,
                )
                if properties_payload != mismatch_properties_payload(
                    properties_document
                ):
                    raise ValueError(
                        "archived mismatch server properties are not canonical JSON"
                    )
                for field, expected in properties_document.items():
                    if properties_record.get(field) != expected:
                        raise ValueError(
                            "mismatch server properties record differs from archive: "
                            + field
                        )
                binding = _exact_keys(
                    item.get("mismatch_server_binding"),
                    {
                        "session_id",
                        "server_artifact_sha256",
                        "server_bind",
                        "server_port",
                        "world_identity",
                        "runtime_world_level_name",
                        "world_identity_marker_sha256",
                        "server_properties_sha256",
                        "runtime_latest_log_sha256",
                        "full_log_sha256",
                        "server_exit_code",
                        "receipt_sha256",
                    },
                    f"log_excerpts.{role}.mismatch_server_binding",
                    errors,
                )
                if not server_summary:
                    raise ValueError("mismatch server binding lacks a harness summary")
                expected_binding_values = {
                    "session_id": server_summary["session_id"],
                    "server_artifact_sha256": artifact_metadata["sha256"],
                    "server_bind": server_summary["server_bind"],
                    "server_port": server_summary["server_port"],
                    "world_identity": server_summary["world"]["identity"],
                    "runtime_world_level_name": server_summary["world"]["level_name"],
                    "world_identity_marker_sha256": server_summary["world"][
                        "identity_marker_sha256"
                    ],
                    "server_properties_sha256": properties_document[
                        "source_sha256"
                    ],
                    "runtime_latest_log_sha256": raw_hash,
                    "full_log_sha256": raw_hash,
                    "server_exit_code": item.get("server_exit_code"),
                    "receipt_sha256": receipt_hash,
                }
                for field, expected in expected_binding_values.items():
                    if binding.get(field) != expected:
                        raise ValueError(
                            f"mismatch server binding differs from harness: {field}"
                        )
                if bundle_mode == "build":
                    server_artifact = resolved_artifact_roles.get("server")
                    if server_artifact is None:
                        raise ValueError(
                            "mismatch server binding lacks the physical server artifact"
                        )
                    if raw_path is None or physical_payload is None:
                        raise ValueError(
                            "mismatch server binding lacks a raw-log snapshot"
                        )
                    (
                        physical_binding,
                        physical_properties_payload,
                        physical_properties_document,
                    ) = build_mismatch_server_binding(
                        source_log=raw_path,
                        source_payload=physical_payload,
                        source_sha256=raw_hash,
                        server_artifact=server_artifact,
                        summary=server_summary,
                        server_exit_code=item.get("server_exit_code"),
                        receipt_sha256=receipt_hash,
                    )
                    if binding != physical_binding:
                        raise ValueError(
                            "mismatch server binding no longer matches runtime inputs"
                        )
                    if (
                        properties_payload != physical_properties_payload
                        or properties_document != physical_properties_document
                    ):
                        raise ValueError(
                            "mismatch server properties archive no longer matches "
                            "runtime inputs"
                        )
        except (OSError, UnicodeError, ValueError) as exc:
            errors.append(str(exc))

    errors.extend(source_audit_consistency_errors(validated_source_audits))
    if len(validated_player_names) == 2 and (
        validated_player_names.get("first-start")
        != validated_player_names.get("restart")
    ):
        errors.append(
            "matching server raw logs do not prove the same player identity"
        )

    def merge_recorded_audits(roles: set[str]) -> dict[str, int]:
        total = {field: 0 for field in LOG_AUDIT_FIELDS}
        seen: set[str] = set()
        for role in roles:
            audit = validated_source_audits.get(role)
            if audit is None:
                continue
            identity = str(audit.get("source_path", ""))
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
    server_audit = merge_recorded_audits(SERVER_LOG_ROLES)
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
    pending_directories = [bundle]
    scanned_entries = 0
    try:
        while pending_directories:
            directory = pending_directories.pop()
            with os.scandir(directory) as entries:
                for entry in entries:
                    scanned_entries += 1
                    if scanned_entries > MAX_BUNDLE_ENTRIES:
                        raise ValueError(
                            "bundle contains more than "
                            f"{MAX_BUNDLE_ENTRIES} filesystem entries"
                        )
                    path = Path(entry.path)
                    if _is_link(path):
                        errors.append(
                            f"bundle must not contain symlinks or junctions: {path}"
                        )
                    elif entry.is_file(follow_symlinks=False):
                        actual_files.add(path.relative_to(bundle).as_posix())
                    elif entry.is_dir(follow_symlinks=False):
                        pending_directories.append(path)
                    else:
                        errors.append(
                            f"bundle contains an unsupported filesystem entry: {path}"
                        )
    except (OSError, ValueError) as exc:
        errors.append(str(exc))
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
    profile_snapshot = subparsers.add_parser(
        "profile-snapshot",
        help="capture one canonical before/after client profile mod inventory",
    )
    profile_snapshot.add_argument(
        "--profile-role", required=True, choices=PROFILE_ROLES
    )
    profile_snapshot.add_argument("--phase", required=True, choices=PROFILE_PHASES)
    profile_snapshot.add_argument("--game-directory", required=True, type=Path)
    profile_snapshot.add_argument("--output", required=True, type=Path)
    collect = subparsers.add_parser("collect", help="archive safe evidence without deciding a Gate")
    collect.add_argument("--session", required=True, type=Path)
    collect.add_argument("--output", required=True, type=Path)
    collect.add_argument(
        "--require-acceptance-ready",
        action="store_true",
        help=(
            "atomically refuse to create the output unless evidence is mechanically "
            "ready for human Gate review"
        ),
    )
    validate = subparsers.add_parser(
        "validate", help="validate an archived build-local or committed bundle"
    )
    validate.add_argument("--bundle", required=True, type=Path)
    validate.add_argument(
        "--require-acceptance-ready",
        action="store_true",
        help=(
            "require all fixed observations PASS, every scoped applicability "
            "proposal accepted, "
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
        if args.command == "profile-snapshot":
            create_profile_snapshot(
                args.game_directory,
                args.output,
                profile_role=args.profile_role,
                phase=args.phase,
            )
            print(
                f"[PROFILE] {args.profile_role} {args.phase} snapshot: "
                f"{args.output}"
            )
            return 0
        if args.command == "collect":
            errors, record = collect_evidence(
                args.session,
                args.output,
                require_acceptance_ready=args.require_acceptance_ready,
            )
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
