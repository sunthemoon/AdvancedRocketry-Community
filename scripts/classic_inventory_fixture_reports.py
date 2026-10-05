"""Private terminal report observations for inventory restart validation.

Accepts acquired JSON bytes only. Type-valid terminal fields establish neither
completion nor origin, ownership, sequence, freshness or stop permission.
"""
from __future__ import annotations

from dataclasses import dataclass
import re

from classic_inventory_fixture_json import JsonNumber, JsonObject, JsonRole, parse_fixture_json


@dataclass(frozen=True, slots=True)
class ReadyForStopPayload:
    action_complete: bool
    owned_handles: int
    observed_rows: int


@dataclass(frozen=True, slots=True)
class FailedPayload:
    stage: str
    reason: str
    owned_handles_remaining: int
    cleanup_errors: int


@dataclass(frozen=True, slots=True)
class TerminalReportObservation:
    raw: bytes
    sha256: str
    schema: int
    run_id: str
    phase: str
    event: str
    index: int
    payload: ReadyForStopPayload | FailedPayload


_ENVELOPE = frozenset(("schema", "run_id", "phase", "event", "index", "payload"))
_READY = frozenset(("action_complete", "owned_handles", "observed_rows"))
_FAILED = frozenset(("stage", "reason", "owned_handles_remaining", "cleanup_errors"))
_REASONS = frozenset((
    "AUTHORITY", "PHASE", "LOADED_AREA", "BINDING", "COLLISION", "RECEIPT",
    "RECORD_SHAPE", "CACHE_ACCESS", "CACHE_SHAPE", "CACHE_CHANGED",
    "CONSTRUCTION", "JOIN", "LOADED_PROGRESS", "INVENTORY_OBSERVATION",
    "REPLAY_OBSERVATION", "DISPOSAL", "DEADLINE", "INTERNAL",
))
_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")


class FixtureReportError(ValueError):
    """Fixed terminal-shape failure; input fields are never diagnostic text."""

    def __init__(self):
        self.code = "RECORD_SHAPE"
        super().__init__(self.code)


def _require(condition: bool) -> None:
    if not condition:
        raise FixtureReportError() from None


def _fields(value: object, expected: frozenset[str]) -> dict[str, object]:
    _require(type(value) is JsonObject)
    _require(len(value.pairs) == len(expected))
    _require(frozenset(key for key, _ in value.pairs) == expected)
    return dict(value.pairs)


def _integer(value: object, minimum: int, maximum: int) -> int:
    _require(type(value) is JsonNumber and value.integer_grammar)
    lexeme = value.lexeme
    if lexeme == "-0":
        _require(minimum == 0)
        return 0
    _require(not lexeme.startswith("-"))
    low, high = str(minimum), str(maximum)
    # The JSON parser has already proved standard integer grammar (no leading
    # zeroes). Establish bounded length AND range before any int conversion.
    _require(len(low) <= len(lexeme) <= len(high))
    _require(len(lexeme) != len(low) or lexeme >= low)
    _require(len(lexeme) != len(high) or lexeme <= high)
    return int(lexeme)


def parse_terminal_report(raw: bytes) -> TerminalReportObservation:
    """Observe exactly READY_FOR_STOP/FAILED fields; no cohort authority."""
    observation = parse_fixture_json(raw, JsonRole.REPORT)
    fields = _fields(observation.value, _ENVELOPE)
    schema = _integer(fields["schema"], 1, 1)
    run_id = fields["run_id"]
    _require(type(run_id) is str and _UUID.fullmatch(run_id) is not None)
    phase = fields["phase"]
    _require(type(phase) is str and phase in ("seed", "reload"))
    last_index = 11 if phase == "seed" else 10
    index = _integer(fields["index"], 0, last_index)
    event = fields["event"]
    _require(type(event) is str and event in ("READY_FOR_STOP", "FAILED"))
    if event == "READY_FOR_STOP":
        _require(index == last_index)
        payload = _fields(fields["payload"], _READY)
        action_complete = payload["action_complete"]
        _require(type(action_complete) is bool)
        handles = _integer(payload["owned_handles"], 0, 0)
        rows = _integer(payload["observed_rows"], last_index + 1, last_index + 1)
        result = ReadyForStopPayload(action_complete, handles, rows)
    else:
        payload = _fields(fields["payload"], _FAILED)
        stage = payload["stage"]
        _require(type(stage) is str and 1 <= len(stage) <= 64 and stage.isascii())
        reason = payload["reason"]
        _require(type(reason) is str and reason in _REASONS)
        handles = _integer(payload["owned_handles_remaining"], 0, 2)
        errors = _integer(payload["cleanup_errors"], 0, 16)
        result = FailedPayload(stage, reason, handles, errors)
    return TerminalReportObservation(observation.raw, observation.sha256, schema,
                                     run_id, phase, event, index, result)
