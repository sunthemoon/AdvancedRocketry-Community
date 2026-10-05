"""Bounded immutable JSON syntax observations for inventory restart validation.

Accepts already acquired bytes only. Structural acceptance does not establish
field types, native compatibility, file origin, freshness or fixture ownership.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import math
from types import MappingProxyType


class JsonRole(Enum):
    EXTERNAL = "external"
    REPORT = "report"
    ADVANCEMENT = "advancement"
    STATS = "stats"
    USERCACHE = "usercache"


@dataclass(frozen=True, slots=True)
class JsonNumber:
    """Validated lexeme; binary64 is lossy and absent for integer grammar."""

    lexeme: str
    integer_grammar: bool
    binary64: float | None


@dataclass(frozen=True, slots=True)
class JsonObject:
    """Encounter-ordered immutable pairs, distinct from an array tuple."""

    pairs: tuple[tuple[str, object], ...]


@dataclass(frozen=True, slots=True)
class JsonObservation:
    raw: bytes
    sha256: str
    role: JsonRole
    value: object
    depth: int
    nodes: int
    entries: int | None


@dataclass(frozen=True, slots=True)
class _Limits:
    bytes: int
    depth: int
    nodes: int
    entries: int | None


_LIMITS = MappingProxyType({
    JsonRole.EXTERNAL: _Limits(16_384, 12, 2_048, None),
    JsonRole.REPORT: _Limits(16_384, 8, 2_048, None),
    JsonRole.ADVANCEMENT: _Limits(262_144, 16, 32_768, 2_048),
    JsonRole.STATS: _Limits(131_072, 16, 16_384, 2_048),
    JsonRole.USERCACHE: _Limits(262_144, 8, 16_384, 1_000),
})
_CODES = frozenset((
    "JSON_ROLE", "JSON_BYTES", "JSON_ENCODING", "JSON_SYNTAX",
    "JSON_DUPLICATE", "JSON_STRING", "JSON_NUMBER", "JSON_DEPTH",
    "JSON_NODES", "JSON_ROOT", "JSON_ENTRIES",
))
_WHITESPACE = " \t\n\r"
_HEX = frozenset("0123456789abcdefABCDEF")
_ESCAPES = MappingProxyType({
    '"': '"', "\\": "\\", "/": "/", "b": "\b", "f": "\f",
    "n": "\n", "r": "\r", "t": "\t",
})


class FixtureJsonError(ValueError):
    """Fixed parser diagnostics; no supplied input or decoder text is echoed."""

    def __init__(self, code: str, role: JsonRole | None):
        self.code = code if type(code) is str and code in _CODES else "JSON_SYNTAX"
        self.role = role if type(role) is JsonRole else None
        super().__init__(self.code, self.role.value if self.role is not None else None)


class _Parser:
    def __init__(self, text: str, role: JsonRole):
        self.text = text
        self.role = role
        self.limits = _LIMITS[role]
        self.pos = 0
        self.nodes = 0
        self.depth = 0
        self.entries = 0

    def _fail(self, code: str) -> None:
        raise FixtureJsonError(code, self.role) from None

    def _space(self) -> None:
        while self.pos < len(self.text) and self.text[self.pos] in _WHITESPACE:
            self.pos += 1

    def _peek(self) -> str:
        return self.text[self.pos] if self.pos < len(self.text) else ""

    def _node(self, depth: int) -> None:
        # Charge before token decoding, container allocation or child descent.
        if depth > self.limits.depth:
            self._fail("JSON_DEPTH")
        if self.nodes == self.limits.nodes:
            self._fail("JSON_NODES")
        self.nodes += 1
        self.depth = max(self.depth, depth)

    def _entry(self) -> None:
        if self.entries == self.limits.entries:
            self._fail("JSON_ENTRIES")
        self.entries += 1

    def parse(self) -> object:
        self._space()
        root = "[" if self.role is JsonRole.USERCACHE else "{"
        if self._peek() != root:
            self._fail("JSON_ROOT")
        mode = self.role.value
        value = self._value(1, mode)
        self._space()
        if self.pos != len(self.text):
            self._fail("JSON_SYNTAX")
        return value

    def _value(self, depth: int, mode: str = "ordinary") -> object:
        self._node(depth)
        token = self._peek()
        if token == "{":
            return self._object(depth, mode)
        if token == "[":
            return self._array(depth, mode)
        if token == '"':
            return self._string()
        if token and (token == "-" or "0" <= token <= "9"):
            return self._number()
        for literal, value in (("true", True), ("false", False), ("null", None)):
            if self.text.startswith(literal, self.pos):
                self.pos += len(literal)
                return value
        self._fail("JSON_SYNTAX")

    def _object(self, depth: int, mode: str) -> JsonObject:
        self.pos += 1
        self._space()
        pairs = []
        keys = set()
        has_stats = False
        if self._peek() != "}":
            while True:
                self._node(depth + 1)
                if self._peek() != '"':
                    self._fail("JSON_SYNTAX")
                key = self._string()
                if key in keys:
                    self._fail("JSON_DUPLICATE")
                keys.add(key)
                if mode == "advancement" and key != "DataVersion":
                    self._entry()
                elif mode == "stats_category":
                    self._entry()
                self._space()
                if self._peek() != ":":
                    self._fail("JSON_SYNTAX")
                self.pos += 1
                self._space()
                child_mode = "ordinary"
                if mode == "stats" and key == "stats":
                    has_stats = True
                    child_mode = "stats_categories"
                elif mode == "stats_categories":
                    child_mode = "stats_category"
                if child_mode != "ordinary" and self._peek() != "{":
                    self._fail("JSON_ENTRIES")
                value = self._value(depth + 1, child_mode)
                pairs.append((key, value))
                self._space()
                if self._peek() != ",":
                    break
                self.pos += 1
                self._space()
        if self._peek() != "}":
            self._fail("JSON_SYNTAX")
        self.pos += 1
        if mode == "stats" and not has_stats:
            self._fail("JSON_ENTRIES")
        return JsonObject(tuple(pairs))

    def _array(self, depth: int, mode: str) -> tuple:
        self.pos += 1
        self._space()
        values = []
        if self._peek() != "]":
            while True:
                if mode == "usercache":
                    self._entry()
                values.append(self._value(depth + 1))
                self._space()
                if self._peek() != ",":
                    break
                self.pos += 1
                self._space()
        if self._peek() != "]":
            self._fail("JSON_SYNTAX")
        self.pos += 1
        return tuple(values)

    def _hex4(self) -> int:
        token = self.text[self.pos:self.pos + 4]
        if len(token) != 4 or any(char not in _HEX for char in token):
            self._fail("JSON_STRING")
        self.pos += 4
        return int(token, 16)

    def _string(self) -> str:
        self.pos += 1  # Opening quote is checked by the caller.
        start = self.pos
        pieces = []
        while self.pos < len(self.text):
            char = self.text[self.pos]
            if char == '"':
                pieces.append(self.text[start:self.pos])
                self.pos += 1
                return "".join(pieces)
            if ord(char) < 32:
                self._fail("JSON_STRING")
            if char != "\\":
                self.pos += 1
                continue
            pieces.append(self.text[start:self.pos])
            self.pos += 1
            escape = self._peek()
            if escape in _ESCAPES:
                pieces.append(_ESCAPES[escape])
                self.pos += 1
            elif escape == "u":
                self.pos += 1
                scalar = self._hex4()
                if 0xD800 <= scalar <= 0xDBFF:
                    if self.text[self.pos:self.pos + 2] != "\\u":
                        self._fail("JSON_STRING")
                    self.pos += 2
                    low = self._hex4()
                    if not 0xDC00 <= low <= 0xDFFF:
                        self._fail("JSON_STRING")
                    scalar = 0x10000 + ((scalar - 0xD800) << 10) + low - 0xDC00
                elif 0xDC00 <= scalar <= 0xDFFF:
                    self._fail("JSON_STRING")
                pieces.append(chr(scalar))
            else:
                self._fail("JSON_STRING")
            start = self.pos
        self._fail("JSON_STRING")

    def _digits(self) -> bool:
        start = self.pos
        while self.pos < len(self.text) and "0" <= self.text[self.pos] <= "9":
            self.pos += 1
        return self.pos != start

    def _number(self) -> JsonNumber:
        start = self.pos
        if self._peek() == "-":
            self.pos += 1
        if self._peek() == "0":
            self.pos += 1
        elif self._peek() and "1" <= self._peek() <= "9":
            self._digits()
        else:
            self._fail("JSON_NUMBER")
        integer = True
        if self._peek() == ".":
            integer = False
            self.pos += 1
            if not self._digits():
                self._fail("JSON_NUMBER")
        if self._peek() in ("e", "E"):
            integer = False
            self.pos += 1
            if self._peek() in ("+", "-"):
                self.pos += 1
            if not self._digits():
                self._fail("JSON_NUMBER")
        lexeme = self.text[start:self.pos]
        projection = None
        if not integer:
            failed = False
            try:
                projection = float(lexeme)
            except (ValueError, OverflowError):
                failed = True
            if failed or not math.isfinite(projection):
                self._fail("JSON_NUMBER")
        return JsonNumber(lexeme, integer, projection)


def parse_fixture_json(raw: bytes, role: JsonRole) -> JsonObservation:
    """Observe fixed-role JSON bytes without file, schema or receipt authority."""
    if type(role) is not JsonRole:
        raise FixtureJsonError("JSON_ROLE", None) from None
    limits = _LIMITS[role]
    if type(raw) is not bytes or not 0 < len(raw) <= limits.bytes:
        raise FixtureJsonError("JSON_BYTES", role) from None
    encoding_failed = False
    try:
        text = raw.decode("utf-8", errors="strict")
    except UnicodeError:
        encoding_failed = True
    if encoding_failed or text.startswith("\ufeff"):
        raise FixtureJsonError("JSON_ENCODING", role) from None
    parser = _Parser(text, role)
    value = parser.parse()
    entries = parser.entries if limits.entries is not None else None
    return JsonObservation(raw, hashlib.sha256(raw).hexdigest(), role,
                           value, parser.depth, parser.nodes, entries)
