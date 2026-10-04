#!/usr/bin/env python3
"""Parsing primitives for inventory restart verification, not a two-host driver.

This module performs no filesystem, process, world, record or receipt writes.
The acknowledgement parser accepts a payload only: console authority, freshness,
deadlines and native membership/loading must be established by a future driver.
"""
from __future__ import annotations

import hashlib
import re
import sys
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Mapping

MAX_PROPERTIES_BYTES = 16_384
MAX_PROPERTIES_PAIRS = 1_023
MAX_PROPERTIES_NODES = 2_048
HOST_REVISION = 3
PROPERTIES_READER_VERSION = 1
SEED_FORCED_COMMAND = "forceload add -80 -80 95 95"

_ESCAPES = MappingProxyType({
    "\\": "\\", " ": " ", "=": "=", ":": ":", "#": "#", "!": "!",
    "t": "\t", "n": "\n", "r": "\r", "f": "\f",
})
_HOST_SETTINGS = MappingProxyType({
    "gamemode": "adventure", "force-gamemode": "false",
    "view-distance": "2", "simulation-distance": "3",
})
_HASH = re.compile(r"[0-9a-f]{64}")
_COORD = r"(?:-[1-5]|[0-5])"
_SINGLE_ACK = re.compile(
    rf"Marked chunk \[({_COORD}), ({_COORD})\] in minecraft:overworld to be force loaded"
)
_MULTIPLE_ACK = re.compile(
    rf"Marked \[({_COORD}), ({_COORD})\] chunks in minecraft:overworld "
    r"from \[-5, -5\] to \[5, 5\] to be force loaded"
)


class FixtureInputError(ValueError):
    """Fixed error codes, without echoing untrusted input or secret properties."""


def _require(condition: bool, code: str) -> None:
    if not condition:
        raise FixtureInputError(code)


def _phase(phase: str) -> None:
    _require(type(phase) is str and phase in ("seed", "reload"), "PHASE")


def _decode_piece(encoded: str, *, key: bool) -> str:
    result = []
    index = 0
    while index < len(encoded):
        char = encoded[index]
        if char == "\\":
            index += 1
            _require(index < len(encoded), "PROPERTIES_ESCAPE")
            escaped = encoded[index]
            _require(escaped in _ESCAPES, "PROPERTIES_ESCAPE")
            result.append(_ESCAPES[escaped])
        else:
            _require(not key or (not char.isspace() and char != ":"), "PROPERTIES_KEY")
            result.append(char)
        index += 1
    return "".join(result)


def _separator(line: str) -> int:
    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == "=":
            return index
    raise FixtureInputError("PROPERTIES_SEPARATOR")


def parse_properties(raw: bytes) -> dict[str, str]:
    """Decode only protocol03's bounded test-owned native-store subset.

    The raw subset rejects C0 plus DEL/C1. LF and
    CRLF are recognized first as physical separators; supported escaped control
    values remain valid. Returned maps are newly owned, not configuration writes.
    """
    _require(type(raw) is bytes, "PROPERTIES_TYPE")
    _require(1 <= len(raw) <= MAX_PROPERTIES_BYTES, "PROPERTIES_BYTES")
    _require(not raw.startswith(b"\xef\xbb\xbf"), "PROPERTIES_BOM")
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise FixtureInputError("PROPERTIES_ENCODING") from None
    # This temporary delimiter handling does not change raw bytes or their hash.
    lines = text.replace("\r\n", "\n")
    _require("\r" not in lines, "PROPERTIES_LINE_ENDING")
    values: dict[str, str] = {}
    for line in lines.split("\n"):
        _require(not any(ord(char) < 32 or 127 <= ord(char) <= 159 for char in line),
                 "PROPERTIES_LITERAL_CONTROL")
        if not line or all(char == " " for char in line) or line.startswith(("#", "!")):
            continue
        separator = _separator(line)
        key = _decode_piece(line[:separator], key=True)
        value_raw = line[separator + 1:]
        _require(bool(key), "PROPERTIES_KEY")
        _require(not value_raw or not value_raw[0].isspace(), "PROPERTIES_VALUE")
        value = _decode_piece(value_raw, key=False)
        _require(key not in values, "PROPERTIES_DUPLICATE")
        _require(len(values) < MAX_PROPERTIES_PAIRS
                 and 1 + 2 * (len(values) + 1) <= MAX_PROPERTIES_NODES,
                 "PROPERTIES_PAIRS")
        values[key] = value
    return values


@dataclass(frozen=True)
class PropertiesObservation:
    """Validated immutable bytes and a separately labelled logical map."""

    raw: bytes = field(repr=False)
    values: Mapping[str, str] = field(init=False, repr=False, hash=False)
    sha256: str = field(init=False)

    def __post_init__(self) -> None:
        values = parse_properties(self.raw)
        object.__setattr__(self, "values", MappingProxyType(values))
        object.__setattr__(self, "sha256", hashlib.sha256(self.raw).hexdigest())


def _observation(value: PropertiesObservation) -> None:
    _require(type(value) is PropertiesObservation, "PROPERTIES_OBSERVATION")


def require_host_settings(observed: PropertiesObservation) -> None:
    _observation(observed)
    _require(all(observed.values.get(key) == value for key, value in _HOST_SETTINGS.items()),
             "PROPERTIES_HOST_SETTINGS")


def require_logical_equality(configured: PropertiesObservation,
                             observed: PropertiesObservation) -> None:
    _observation(configured)
    _observation(observed)
    _require(configured.values == observed.values, "PROPERTIES_LOGICAL_MISMATCH")


def require_boot_binding(phase: str, configured: PropertiesObservation,
                         boot: PropertiesObservation, launch_sha256: str,
                         *, seed_stopped: PropertiesObservation | None = None) -> None:
    """Pure binding check, not a decoded receipt or past-byte hook observation."""
    _phase(phase)
    require_host_settings(configured)
    require_logical_equality(configured, boot)
    if phase == "seed":
        _require(seed_stopped is None, "PROPERTIES_SEED_BINDING")
        required = configured
    else:
        _observation(seed_stopped)
        require_logical_equality(configured, seed_stopped)
        required = seed_stopped
    _require(boot.raw == required.raw, "PROPERTIES_BOOT_RAW")
    _require(type(launch_sha256) is str and _HASH.fullmatch(launch_sha256) is not None
             and launch_sha256 == boot.sha256, "PROPERTIES_LAUNCH_HASH")


def require_live_binding(configured: PropertiesObservation,
                         driver_live: PropertiesObservation, hook_live_sha256: str) -> None:
    """Require logical equality and a supplied independent hook hash to match."""
    require_host_settings(configured)
    require_logical_equality(configured, driver_live)
    _require(type(hook_live_sha256) is str and _HASH.fullmatch(hook_live_sha256) is not None
             and hook_live_sha256 == driver_live.sha256, "PROPERTIES_LIVE_HASH")


def require_stopped_binding(live: PropertiesObservation,
                            stopped: PropertiesObservation) -> None:
    _observation(live)
    _observation(stopped)
    _require(live.raw == stopped.raw, "PROPERTIES_STOPPED_RAW")


def commands_for_phase(phase: str) -> tuple[str, ...]:
    """Only seed has a command; decoded coordinates cannot affect this tuple."""
    _phase(phase)
    return (SEED_FORCED_COMMAND,) if phase == "seed" else ()


@dataclass(frozen=True)
class ForcedSetupAcknowledgement:
    form: str
    last_changed_chunk: tuple[int, int]


def parse_seed_acknowledgement(payload: str) -> ForcedSetupAcknowledgement:
    """Parse exact English payload only, never count/membership/load authority."""
    _require(type(payload) is str, "FORCED_ACK_TYPE")
    for form, pattern in (("single", _SINGLE_ACK), ("multiple", _MULTIPLE_ACK)):
        match = pattern.fullmatch(payload)
        if match is not None:
            return ForcedSetupAcknowledgement(form, (int(match[1]), int(match[2])))
    raise FixtureInputError("FORCED_ACK_SHAPE")


def main() -> int:
    print("Inventory restart verification parsing primitives only; two-host fixture and driver are not implemented.",
          file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
