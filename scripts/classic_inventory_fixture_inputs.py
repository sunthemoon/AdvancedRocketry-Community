"""Private quiescent-copy properties bytes input, not ownership or live admission.

The caller independently admits the owned root and the actual preboot/clean-stop
point. This helper neither establishes quiescence nor launches a host. Detected
ordinary changes refuse; malicious ancestor swaps and hidden same-identity changes
are not claimed safe. No parser, writer, CLI or driver is implemented here.
"""
from __future__ import annotations

import os
from pathlib import Path
import stat
from types import MappingProxyType


MAX_PROPERTIES_BYTES = 16_384
MAX_ROOT_BYTES = 1_024
_PATH_TYPE = type(Path("D:/"))
_ROLES = MappingProxyType({
    "configured": (".classic-inventory-fixture", "snapshot-server-properties-v1.txt"),
    "active": ("server.properties",),
})
_POINTS = frozenset(("seed_preboot", "reload_preboot", "stopped"))
_CODES = frozenset(("PLATFORM", "ROOT", "SELECTOR", "PATH", "FILE", "CHANGED", "BYTES", "IO"))


class PropertiesFileInputError(ValueError):
    """Fixed private failure codes; no supplied path, label or OS message."""

    def __init__(self, code: str):
        code = code if type(code) is str and code in _CODES else "IO"
        self.code = code
        super().__init__(code)


def _require(condition: bool, code: str) -> None:
    if not condition:
        raise PropertiesFileInputError(code) from None


def _root_path(root: Path) -> Path:
    _require(os.name == "nt" and hasattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT"), "PLATFORM")
    _require(type(root) is _PATH_TYPE, "ROOT")
    text = str(root)
    _require(0 < len(text) <= MAX_ROOT_BYTES, "ROOT")
    try:
        encoded_size = len(text.encode("utf-8", errors="strict"))
    except UnicodeError:
        raise PropertiesFileInputError("ROOT") from None
    _require(encoded_size <= MAX_ROOT_BYTES, "ROOT")
    _require(root.is_absolute() and root.drive.casefold() == "d:", "ROOT")
    _require(root.anchor.casefold() == "d:\\", "ROOT")
    _require(not any(part == ".." or ":" in part or "\x00" in part
                     for part in root.parts[1:]), "ROOT")
    return root


def _identity(status: os.stat_result) -> tuple[int, ...]:
    """Windows file ID plus type/size/write/creation/link/reparse observations.

    Windows lstat ctime may expose creation while fstat ctime exposes change;
    their explicit birthtime field is the comparable creation observation.
    Access time is intentionally excluded because the read may update it. Zero
    or absent identity/attribute capability refuses rather than guessing. These
    fields do not establish hostile-storage or same-identity mutation protection.
    """
    names = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_birthtime_ns",
             "st_nlink", "st_file_attributes", "st_reparse_tag")
    values = tuple(getattr(status, name, None) for name in names)
    _require(all(type(value) is int for value in values), "PLATFORM")
    _require(values[0] != 0 and values[1] > 0, "PLATFORM")
    _require(values[3] >= 0, "FILE")
    _require(not stat.S_ISLNK(values[2])
             and not values[7] & stat.FILE_ATTRIBUTE_REPARSE_POINT
             and values[8] == 0, "PATH")
    return values


def _path_identity(path: Path, *, directory: bool) -> tuple[int, ...]:
    status = path.lstat()
    identity = _identity(status)
    required_type = stat.S_ISDIR if directory else stat.S_ISREG
    _require(required_type(status.st_mode), "FILE")
    return identity


def _literal_observations(root: Path, relative: tuple[str, ...]) -> tuple[tuple[Path, tuple[int, ...]], ...]:
    directories = (*reversed(root.parents), root)
    cursor = root
    for part in relative[:-1]:
        cursor = cursor / part
        directories += (cursor,)
    target = cursor / relative[-1]
    observations = tuple((path, _path_identity(path, directory=True)) for path in directories)
    return observations + ((target, _path_identity(target, directory=False)),)


def _canonical_locations(root: Path, target: Path) -> tuple[Path, Path]:
    canonical_root = root.resolve(strict=True)
    canonical_target = target.resolve(strict=True)
    _require(canonical_root.drive.casefold() == "d:"
             and canonical_root.anchor.casefold() == "d:\\"
             and canonical_target != canonical_root
             and canonical_target.is_relative_to(canonical_root), "PATH")
    return canonical_root, canonical_target


def read_quiescent_properties(owned_server_root: Path, role: str, point: str) -> bytes:
    """Read one fixed required file in an independently admitted quiescent D copy.

    Exact roles are configured/active; points are seed_preboot/reload_preboot/
    stopped. Point labels are selectors, not evidence of host timing. Returns
    immutable raw bytes only; Root's later composition owns properties decoding,
    binding, shared deadlines and all live/receipt/host admission.
    """
    try:
        _require(type(role) is str and role in _ROLES
                 and type(point) is str and point in _POINTS, "SELECTOR")
        root = _root_path(owned_server_root)
        before = _literal_observations(root, _ROLES[role])
        target, leaf_before = before[-1]
        # All literal components were checked before following resolution.
        canonical_before = _canonical_locations(root, target)
        with target.open("rb", buffering=0) as stream:
            opened_status = os.fstat(stream.fileno())
            opened = _identity(opened_status)
            _require(stat.S_ISREG(opened_status.st_mode), "FILE")
            _require(opened == leaf_before, "CHANGED")
            content = stream.read(MAX_PROPERTIES_BYTES + 1)
            _require(type(content) is bytes, "IO")
            _require(0 < len(content) <= MAX_PROPERTIES_BYTES, "BYTES")
            _require(len(content) == opened_status.st_size, "CHANGED")
            _require(_identity(os.fstat(stream.fileno())) == opened, "CHANGED")
        after = _literal_observations(root, _ROLES[role])
        _require(after == before, "CHANGED")
        _require(_canonical_locations(root, target) == canonical_before, "CHANGED")
        return content
    except PropertiesFileInputError as error:
        raise error from None
    except (OSError, ValueError, RuntimeError):
        raise PropertiesFileInputError("IO") from None
