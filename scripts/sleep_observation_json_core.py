"""Bounded, compact JSON object decoding for already-isolated offline bytes.

This generic layer performs no framing, schema conversion, I/O or qualification.
Numbers remain lexical records; callers exclusively own successful mutable trees.
"""
from dataclasses import dataclass, field
from time import monotonic_ns as _monotonic_ns


@dataclass(frozen=True, slots=True)
class JsonNumber:
    lexeme: str
    start: int
    end: int


@dataclass(frozen=True, slots=True)
class DecodeMetrics:
    input_bytes: int
    consumed_bytes: int
    nodes: int
    names: int
    max_container_depth: int
    max_string_utf16_units: int
    max_number_bytes: int


@dataclass(frozen=True, slots=True)
class DecodeError:
    code: str
    byte_offset: int
    location: tuple[int, ...]
    name_span: tuple[int, int] | None = None
    limit_name: str | None = None
    limit: int | None = None
    observed: int | None = None


@dataclass(frozen=True, slots=True)
class Decoded:
    value: dict
    metrics: DecodeMetrics
    kind: str = field(default="JSON_CORE_DECODED", init=False)


@dataclass(frozen=True, slots=True)
class Refused:
    error: DecodeError
    metrics: DecodeMetrics
    kind: str = field(default="JSON_CORE_REFUSED", init=False)


DecodeResult = Decoded | Refused
_MAX_INPUT = 524288
_MAX_NODES = 65536
_MAX_NAMES = 65536
_MAX_OBJECT_NAMES = 64
_MAX_DEPTH = 64
_MAX_STRING_UNITS = 4096
_MAX_NUMBER_BYTES = 64
_MAX_DEADLINE_NS = 60000000000
_WHITESPACE = b" \t\r\n"
_NUMBER_END = b",]} \t\r\n"
_ESCAPES = {34: '"', 92: "\\", 47: "/", 98: "\b", 102: "\f",
            110: "\n", 114: "\r", 116: "\t"}


class _Stop(Exception):
    """Only deliberate, bounded admission refusals use this internal signal."""
    def __init__(self, error):
        self.error = error


@dataclass(slots=True)
class _Frame:
    value: dict | list
    location: tuple[int, ...]
    phase: str = "first"
    ordinal: int = 0
    key: str | None = None
    name_span: tuple[int, int] | None = None


class _Scanner:
    """Iterative structural parser with atomically admitted lexical prefixes."""
    def __init__(self, payload, deadline):
        self.raw = payload
        self.deadline = deadline
        self.n = len(payload)
        self.c = self.nodes = self.names = self.depth = 0
        self.string_units = self.number_bytes = 0
        self.checked_at = 0
        self.location = ()
        self.name_span = None
        self.key_start = None
        self.stack = []

    def metrics(self):
        return DecodeMetrics(self.n, self.c, self.nodes, self.names, self.depth,
                             self.string_units, self.number_bytes)

    def fail(self, code, offset=None, limit_name=None, limit=None, observed=None):
        span = self.name_span
        if self.key_start is not None:
            span = (self.key_start, self.c)
        raise _Stop(DecodeError(code, self.c if offset is None else offset,
                                self.location, span, limit_name, limit, observed))

    def checkpoint(self):
        if _monotonic_ns() >= self.deadline:
            self.fail("DEADLINE")
        self.checked_at = self.c

    def advance(self, width=1):
        # Check before an indivisible atom would cross the 4096-byte interval.
        if self.c + width - self.checked_at >= 4096:
            self.checkpoint()
        self.c += width

    def peek(self):
        return self.raw[self.c] if self.c < self.n else None

    def unexpected(self, code="SYNTAX"):
        byte = self.peek()
        self.fail("WHITESPACE" if byte is not None and byte in _WHITESPACE else code)

    def reserve_value(self, container):
        self.checkpoint()
        if self.nodes == _MAX_NODES:
            self.fail("NODES", limit_name="nodes", limit=_MAX_NODES,
                      observed=self.nodes + 1)
        next_depth = len(self.stack) + 1
        if container and next_depth > _MAX_DEPTH:
            self.fail("DEPTH", limit_name="container_depth", limit=_MAX_DEPTH,
                      observed=next_depth)
        self.nodes += 1
        if container:
            self.depth = max(self.depth, next_depth)

    def reserve_name(self, frame):
        self.checkpoint()
        if frame.ordinal == _MAX_OBJECT_NAMES:
            self.fail("OBJECT_NAMES", limit_name="object_names",
                      limit=_MAX_OBJECT_NAMES, observed=frame.ordinal + 1)
        if self.names == _MAX_NAMES:
            self.fail("NAMES", limit_name="names", limit=_MAX_NAMES,
                      observed=self.names + 1)
        self.names += 1
        frame.ordinal += 1

    def hex4(self, start):
        scalar = 0
        for pos in range(start, start + 4):
            if pos >= self.n:
                self.fail("STRING_ESCAPE", start - 2)
            byte = self.raw[pos]
            if 48 <= byte <= 57:
                digit = byte - 48
            elif 65 <= byte <= 70:
                digit = byte - 55
            elif 97 <= byte <= 102:
                digit = byte - 87
            else:
                self.fail("STRING_ESCAPE", start - 2)
            scalar = scalar * 16 + digit
        return scalar

    def string_atom(self):
        pos = self.c
        byte = self.raw[pos]
        if byte < 32:
            self.fail("STRING_CONTROL", pos)
        if byte == 92:
            if pos + 1 >= self.n:
                self.fail("STRING_ESCAPE", pos)
            escaped = self.raw[pos + 1]
            if escaped in _ESCAPES:
                return ord(_ESCAPES[escaped]), 2
            if escaped != 117:
                self.fail("STRING_ESCAPE", pos)
            scalar = self.hex4(pos + 2)
            if 0xDC00 <= scalar <= 0xDFFF:
                self.fail("SURROGATE", pos)
            if 0xD800 <= scalar <= 0xDBFF:
                if self.raw[pos + 6:pos + 8] != b"\\u":
                    self.fail("SURROGATE", pos)
                low = self.hex4(pos + 8)
                if not 0xDC00 <= low <= 0xDFFF:
                    self.fail("SURROGATE", pos)
                return 0x10000 + ((scalar - 0xD800) << 10) + low - 0xDC00, 12
            return scalar, 6
        if byte < 128:
            return byte, 1
        if 0xC2 <= byte <= 0xDF:
            width, scalar = 2, byte & 31
        elif 0xE0 <= byte <= 0xEF:
            width, scalar = 3, byte & 15
        elif 0xF0 <= byte <= 0xF4:
            width, scalar = 4, byte & 7
        else:
            self.fail("UTF8", pos)
        if pos + width > self.n:
            self.fail("UTF8", pos)
        second = self.raw[pos + 1]
        if ((byte == 0xE0 and second < 0xA0)
                or (byte == 0xED and second >= 0xA0)
                or (byte == 0xF0 and second < 0x90)
                or (byte == 0xF4 and second > 0x8F)):
            self.fail("UTF8", pos)
        for index in range(1, width):
            continuation = self.raw[pos + index]
            if not 0x80 <= continuation <= 0xBF:
                self.fail("UTF8", pos)
            scalar = (scalar << 6) | (continuation & 63)
        return scalar, width

    def string(self):
        start = self.c
        self.advance()  # The opening quote was admitted by the slot reservation.
        chars = []
        units = 0
        while True:
            if self.c == self.n:
                self.fail("SYNTAX")
            if self.raw[self.c] == 34:
                self.advance()
                return "".join(chars)
            scalar, width = self.string_atom()
            next_units = units + (2 if scalar > 0xFFFF else 1)
            if next_units > _MAX_STRING_UNITS:
                self.fail("STRING_UNITS", start, "string_utf16_units",
                          _MAX_STRING_UNITS, next_units)
            self.advance(width)
            chars.append(chr(scalar))
            units = next_units
            self.string_units = max(self.string_units, units)

    def number(self):
        start = self.c
        state = "start"
        while self.c < self.n:
            byte = self.raw[self.c]
            digit = 48 <= byte <= 57
            next_state = None
            if state == "start":
                next_state = "sign" if byte == 45 else "zero" if byte == 48 else "integer"
            elif state in ("sign", "fraction_start", "exponent_start", "exponent_sign"):
                if digit:
                    next_state = ("zero" if byte == 48 else "integer") if state == "sign" else (
                        "fraction" if state == "fraction_start" else "exponent")
                elif state == "exponent_start" and byte in (43, 45):
                    next_state = "exponent_sign"
            elif state == "integer" and digit:
                next_state = "integer"
            elif state in ("zero", "integer", "fraction"):
                if byte == 46 and state != "fraction":
                    next_state = "fraction_start"
                elif byte in (69, 101):
                    next_state = "exponent_start"
                elif state == "fraction" and digit:
                    next_state = "fraction"
            elif state == "exponent" and digit:
                next_state = "exponent"
            if next_state is None:
                if state in ("zero", "integer", "fraction", "exponent") and byte in _NUMBER_END:
                    break
                self.fail("NUMBER_GRAMMAR")
            observed = self.c - start + 1
            if observed > _MAX_NUMBER_BYTES:
                self.fail("NUMBER_BYTES", start, "number_bytes", _MAX_NUMBER_BYTES, observed)
            self.advance()
            self.number_bytes = max(self.number_bytes, observed)
            state = next_state
        if state not in ("zero", "integer", "fraction", "exponent"):
            self.fail("NUMBER_GRAMMAR")
        return JsonNumber(self.raw[start:self.c].decode("ascii"), start, self.c)

    def literal(self, spelling, value):
        for byte in spelling:
            if self.peek() != byte:
                self.unexpected()
            self.advance()
        if self.peek() is not None and self.peek() not in _NUMBER_END:
            self.unexpected()
        return value

    def attach(self, frame, value):
        if isinstance(frame.value, dict):
            frame.value[frame.key] = value
        else:
            frame.value.append(value)
            frame.ordinal += 1
        frame.key = None
        frame.name_span = None
        frame.phase = "after"

    def value(self, parent=None):
        byte = self.peek()
        if byte is None or byte not in b'{["-0123456789tfn':
            self.unexpected()
        container = byte in (123, 91)
        self.reserve_value(container)
        if container:
            value = {} if byte == 123 else []
            location = self.location
            if parent is not None:
                self.attach(parent, value)
            self.advance()
            self.stack.append(_Frame(value, location))
            return value
        if byte == 34:
            value = self.string()
        elif byte == 116:
            value = self.literal(b"true", True)
        elif byte == 102:
            value = self.literal(b"false", False)
        elif byte == 110:
            value = self.literal(b"null", None)
        else:
            value = self.number()
        self.attach(parent, value)

    def run(self):
        if self.raw.startswith(b"\xef\xbb\xbf"):
            self.fail("BOM", 0)
        if self.peek() != 123:
            self.unexpected("ROOT_TYPE")
        root = self.value()
        while self.stack:
            frame = self.stack[-1]
            is_object = isinstance(frame.value, dict)
            close = 125 if is_object else 93
            self.location = frame.location
            self.name_span = None
            self.key_start = None
            byte = self.peek()
            if frame.phase == "after":
                if byte == close:
                    self.advance()
                    self.stack.pop()
                elif byte == 44:
                    self.advance()
                    frame.phase = "required"
                else:
                    self.unexpected()
                continue
            if frame.phase == "first" and byte == close:
                self.advance()
                self.stack.pop()
                continue
            ordinal = frame.ordinal - 1 if frame.phase in ("colon", "value") else frame.ordinal
            self.location = frame.location + (ordinal,)
            if frame.phase == "colon":
                self.name_span = frame.name_span
                if byte != 58:
                    self.unexpected()
                self.advance()
                frame.phase = "value"
            elif is_object and frame.phase in ("first", "required"):
                if byte != 34:
                    self.unexpected()
                self.reserve_name(frame)
                self.key_start = self.c
                key = self.string()
                span = (self.key_start, self.c)
                if key in frame.value:
                    self.fail("DUPLICATE_NAME", self.key_start)
                self.key_start = None
                frame.key, frame.name_span, frame.phase = key, span, "colon"
            else:
                self.name_span = frame.name_span if is_object else None
                self.value(frame)
        self.location, self.name_span = (), None
        if self.c != self.n:
            self.unexpected("TRAILING_DATA")
        self.checkpoint()
        return Decoded(root, self.metrics())


def decode_payload(payload: bytes, *, deadline_ns: int) -> DecodeResult:
    """Decode one compact object; expose only deliberate admission refusals."""
    entry = _monotonic_ns()
    if type(payload) is not bytes:
        return Refused(DecodeError("INPUT_TYPE", 0, ()), DecodeMetrics(0, 0, 0, 0, 0, 0, 0))
    size = len(payload)
    empty = DecodeMetrics(size, 0, 0, 0, 0, 0, 0)
    if size > _MAX_INPUT:
        return Refused(DecodeError("INPUT_BYTES", 0, (), None, "input_bytes", _MAX_INPUT, size), empty)
    if type(deadline_ns) is not int or deadline_ns > entry + _MAX_DEADLINE_NS:
        return Refused(DecodeError("DEADLINE_ARGUMENT", 0, ()), empty)
    if deadline_ns <= entry:
        return Refused(DecodeError("DEADLINE", 0, ()), empty)
    scanner = _Scanner(payload, deadline_ns)
    try:
        return scanner.run()
    except _Stop as refusal:
        return Refused(refusal.error, scanner.metrics())
