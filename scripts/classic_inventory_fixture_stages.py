"""Private nonterminal report observations for inventory restart validation.

Accepts acquired JSON bytes only. Field observations do not establish origin,
identity, ownership, sequence, deadlines, native success or stop permission.
"""
from __future__ import annotations

from dataclasses import dataclass
import re

from classic_inventory_fixture_json import JsonNumber, JsonObject, JsonRole, parse_fixture_json


@dataclass(frozen=True, slots=True)
class Identity:
    role: str
    uuid: str
    name: str


@dataclass(frozen=True, slots=True)
class PropertiesObservation:
    configured_bytes: int
    configured_sha256: str
    boot_sha256: str
    live_bytes: int
    live_sha256: str
    logical_map_equal: bool
    reader_version: int


@dataclass(frozen=True, slots=True)
class HostAdmission:
    revision: int
    world_data_game_type: str
    raw_spawn: tuple[int, int, int]
    shared_spawn: tuple[int, int, int]
    border_contains_collision_region: bool
    minimum_height: int
    maximum_height: int
    view_distance: int
    simulation_distance: int
    loaded_envelope: tuple[int, int, int, int]
    loaded_chunks: int
    loaded_entities: int
    player_dimensions_bits: tuple[str, str]
    constructor_iteration_upper_bound: int
    setup_mode: str
    forced_target_membership: tuple[bool, ...]
    properties: PropertiesObservation


@dataclass(frozen=True, slots=True)
class PreflightPayload:
    world: str
    run_marker_sha256: str
    receipt_sha256: str | None
    identities: tuple[Identity, Identity]
    file_presence: tuple[bool, ...]
    profile_map_counts: tuple[int, int]
    pending_requests: int
    native_progress_cache_counts: tuple[int, int]
    online_count: int
    loaded_chunk: tuple[int, int]
    cache_snapshot_sha256: str
    host_admission: HostAdmission


@dataclass(frozen=True, slots=True)
class NativeLocation:
    dimension: str
    position: tuple[JsonNumber, JsonNumber, JsonNumber]
    motion_bits: tuple[str, str, str]
    rotation_bits: tuple[str, str]
    vehicle_present: bool


@dataclass(frozen=True, slots=True)
class PlayerState:
    role: str
    uuid: str
    name: str
    listed: bool
    permission_level: int
    non_fake: bool
    game_mode: str
    item_counts: tuple[int, ...]
    other_nonempty_slots: int
    total_experience: int
    done: tuple[bool, ...]
    obtained_ms: tuple[int | None, ...]
    obtained_seconds: tuple[int | None, ...]
    criteria_keys: tuple[tuple[str], ...]
    native_location: NativeLocation


@dataclass(frozen=True, slots=True)
class PlayerStagePayload:
    goal: str | None
    players: tuple[PlayerState, PlayerState]


@dataclass(frozen=True, slots=True)
class DisposalState:
    role: str
    uuid: str
    listed: bool
    connected: bool
    channel_open: bool
    channel_active: bool
    drained_messages: int
    release_attempt_completed: bool


@dataclass(frozen=True, slots=True)
class DisposedPayload:
    players: tuple[DisposalState, DisposalState]


@dataclass(frozen=True, slots=True)
class StageReportObservation:
    raw: bytes
    sha256: str
    schema: int
    run_id: str
    phase: str
    event: str
    index: int
    payload: PreflightPayload | PlayerStagePayload | DisposedPayload


_UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
_HASH = re.compile(r"[0-9a-f]{64}")
_ROLES = (
    ("earned", "180c1800-0000-4000-8000-000000000001", "ArceC18Owned"),
    ("control", "180c1800-0000-4000-8000-000000000002", "ArceC18Control"),
)
_GOALS = tuple("advancedrocketrycommunity:classic/" + name for name in (
    "root", "block_press", "rolling", "electrolysis", "suited_up", "warp_core"))


class FixtureStageError(ValueError):
    """Fixed field-shape failure without supplied input in diagnostic text."""

    def __init__(self):
        self.code = "RECORD_SHAPE"
        super().__init__(self.code)


def _require(condition: bool) -> None:
    if not condition:
        raise FixtureStageError() from None


def _fields(value: object, record: type, excluded: tuple[str, ...] = ()) -> dict[str, object]:
    expected = frozenset(record.__dataclass_fields__).difference(excluded)
    _require(type(value) is JsonObject)
    _require(len(value.pairs) == len(expected))
    _require(frozenset(key for key, _ in value.pairs) == frozenset(expected))
    return dict(value.pairs)


def _array(value: object, length: int) -> tuple:
    _require(type(value) is tuple and len(value) == length)
    return value


def _boolean(value: object) -> bool:
    _require(type(value) is bool)
    return value


def _exact(value: object, expected: str) -> str:
    _require(type(value) is str and value == expected)
    return value


def _hash(value: object) -> str:
    _require(type(value) is str and _HASH.fullmatch(value) is not None)
    return value


def _magnitude_in_range(digits: str, minimum: int, maximum: int) -> bool:
    low, high = str(minimum), str(maximum)
    return (len(low) <= len(digits) <= len(high)
            and (len(digits) != len(low) or digits >= low)
            and (len(digits) != len(high) or digits <= high))


def _integer(value: object, minimum: int, maximum: int) -> int:
    _require(type(value) is JsonNumber and value.integer_grammar)
    negative = value.lexeme.startswith("-")
    digits = value.lexeme[1:] if negative else value.lexeme
    # Standard grammar is already proven. Bound magnitude before conversion;
    # negative zero has numerical value zero on either side of the interval.
    if digits == "0":
        _require(minimum <= 0 <= maximum)
        return 0
    if negative:
        _require(minimum < 0)
        _require(_magnitude_in_range(digits, max(0, -maximum), -minimum))
    else:
        _require(maximum >= 0)
        _require(_magnitude_in_range(digits, max(0, minimum), maximum))
    result = int(digits)
    return -result if negative else result


def _integers(value: object, length: int, minimum: int, maximum: int) -> tuple[int, ...]:
    return tuple(_integer(item, minimum, maximum) for item in _array(value, length))


def _booleans(value: object, length: int) -> tuple[bool, ...]:
    return tuple(_boolean(item) for item in _array(value, length))


def _decimal_equal(value: object, coefficient: str, power: int) -> JsonNumber:
    _require(type(value) is JsonNumber)
    text = value.lexeme
    _require(not text.startswith("-"))
    mantissa, separator, exponent = text.lower().partition("e")
    whole, dot, fraction = mantissa.partition(".")
    digits = (whole + fraction).lstrip("0")
    significant = digits.rstrip("0")
    _require(significant == coefficient)
    # A coefficient match fixes the required exponent to a bounded value derived
    # from token length. Compare exponent spelling, including leading zeroes,
    # lexically; never convert a supplied exponent or long coefficient to int.
    required = power + len(fraction) - (len(digits) - len(significant))
    if not separator:
        _require(required == 0)
    else:
        negative = exponent.startswith("-")
        magnitude = exponent.lstrip("+-").lstrip("0") or "0"
        _require(magnitude == str(abs(required)))
        _require((negative and magnitude != "0") == (required < 0))
    return value


def _identity(value: object, slot: int) -> Identity:
    data = _fields(value, Identity)
    return Identity(*(_exact(data[key], expected)
                      for key, expected in zip(("role", "uuid", "name"), _ROLES[slot])))


def _properties(value: object) -> PropertiesObservation:
    data = _fields(value, PropertiesObservation)
    return PropertiesObservation(
        _integer(data["configured_bytes"], 1, 16384), _hash(data["configured_sha256"]),
        _hash(data["boot_sha256"]), _integer(data["live_bytes"], 1, 16384),
        _hash(data["live_sha256"]), _boolean(data["logical_map_equal"]),
        _integer(data["reader_version"], 1, 1))


def _spawn(value: object) -> tuple[int, int, int]:
    x, y, z = _array(value, 3)
    return _integer(x, -79, 94), _integer(y, 64, 256), _integer(z, -79, 94)


def _host(value: object, phase: str) -> HostAdmission:
    data = _fields(value, HostAdmission)
    raw_spawn, shared_spawn = _spawn(data["raw_spawn"]), _spawn(data["shared_spawn"])
    _require(raw_spawn == shared_spawn)
    envelope = _integers(data["loaded_envelope"], 4, -5, 5)
    _require(envelope == (-5, -5, 5, 5))
    bits = _array(data["player_dimensions_bits"], 2)
    dimensions = tuple(_exact(item, expected)
                       for item, expected in zip(bits, ("3f19999a", "3fe66666")))
    return HostAdmission(
        _integer(data["revision"], 3, 3), _exact(data["world_data_game_type"], "adventure"),
        raw_spawn, shared_spawn, _boolean(data["border_contains_collision_region"]),
        _integer(data["minimum_height"], -64, -64), _integer(data["maximum_height"], 320, 320),
        _integer(data["view_distance"], 2, 2), _integer(data["simulation_distance"], 3, 3),
        envelope, _integer(data["loaded_chunks"], 121, 121),
        _integer(data["loaded_entities"], 0, 1024), dimensions,
        _integer(data["constructor_iteration_upper_bound"], 256, 256),
        _exact(data["setup_mode"], "seed_add" if phase == "seed" else "reload_retained"),
        _booleans(data["forced_target_membership"], 121), _properties(data["properties"]))


def _preflight(value: object, phase: str) -> PreflightPayload:
    data = _fields(value, PreflightPayload)
    receipt = data["receipt_sha256"]
    if phase == "seed":
        _require(receipt is None)
    else:
        receipt = _hash(receipt)
    identities = tuple(_identity(item, slot)
                       for slot, item in enumerate(_array(data["identities"], 2)))
    return PreflightPayload(
        _exact(data["world"], "minecraft:overworld"), _hash(data["run_marker_sha256"]),
        receipt, identities, _booleans(data["file_presence"], 8),
        _integers(data["profile_map_counts"], 2, 0, 1000),
        _integer(data["pending_requests"], 0, 0),
        _integers(data["native_progress_cache_counts"], 2, 0, 0),
        _integer(data["online_count"], 0, 0), _integers(data["loaded_chunk"], 2, 0, 0),
        _hash(data["cache_snapshot_sha256"]), _host(data["host_admission"], phase))


def _location(value: object, slot: int) -> NativeLocation:
    data = _fields(value, NativeLocation)
    positions = _array(data["position"], 3)
    target = (("85", -1), ("2", 2), ("85", -1)) if slot == 0 else (
        ("105", -1), ("2", 2), ("85", -1))
    position = tuple(_decimal_equal(item, *expected) for item, expected in zip(positions, target))
    motion = tuple(_exact(item, "0000000000000000")
                   for item in _array(data["motion_bits"], 3))
    rotation = tuple(_exact(item, "00000000")
                     for item in _array(data["rotation_bits"], 2))
    return NativeLocation(_exact(data["dimension"], "minecraft:overworld"), position,
                          motion, rotation, _boolean(data["vehicle_present"]))


def _player(value: object, slot: int) -> PlayerState:
    data = _fields(value, PlayerState)
    identity = tuple(_exact(data[key], expected)
                     for key, expected in zip(("role", "uuid", "name"), _ROLES[slot]))
    ms = tuple(None if item is None else _integer(item, 0, 9223372036854775807)
               for item in _array(data["obtained_ms"], 6))
    seconds = tuple(None if item is None else _integer(item, 0, 9223372036854775)
                    for item in _array(data["obtained_seconds"], 6))
    for millis, second in zip(ms, seconds):
        _require((millis is None and second is None)
                 or (millis is not None and second is not None and millis // 1000 == second))
    criteria = tuple(tuple(_exact(key, "has_inventory") for key in _array(item, 1))
                     for item in _array(data["criteria_keys"], 6))
    return PlayerState(
        *identity, _boolean(data["listed"]), _integer(data["permission_level"], 0, 0),
        _boolean(data["non_fake"]), _exact(data["game_mode"], "creative"),
        _integers(data["item_counts"], 10, 0, 64),
        _integer(data["other_nonempty_slots"], 0, 41),
        _integer(data["total_experience"], 0, 2147483647), _booleans(data["done"], 6),
        ms, seconds, criteria, _location(data["native_location"], slot))


def _players(value: object, goal: str | None) -> PlayerStagePayload:
    data = _fields(value, PlayerStagePayload)
    if goal is None:
        _require(data["goal"] is None)
    else:
        _exact(data["goal"], goal)
    players = tuple(_player(item, slot)
                    for slot, item in enumerate(_array(data["players"], 2)))
    return PlayerStagePayload(goal, players)


def _disposal(value: object, slot: int) -> DisposalState:
    data = _fields(value, DisposalState)
    return DisposalState(
        _exact(data["role"], _ROLES[slot][0]), _exact(data["uuid"], _ROLES[slot][1]),
        _boolean(data["listed"]), _boolean(data["connected"]),
        _boolean(data["channel_open"]), _boolean(data["channel_active"]),
        _integer(data["drained_messages"], 0, 2048), _boolean(data["release_attempt_completed"]))


def parse_stage_report(raw: bytes) -> StageReportObservation:
    """Observe one nonterminal row, without validating a native cohort."""
    observation = parse_fixture_json(raw, JsonRole.REPORT)
    data = _fields(observation.value, StageReportObservation, ("raw", "sha256"))
    # raw/hash are observations, not input members of the envelope.
    schema = _integer(data["schema"], 1, 1)
    run_id = data["run_id"]
    _require(type(run_id) is str and _UUID.fullmatch(run_id) is not None)
    phase = data["phase"]
    _require(type(phase) is str and phase in ("seed", "reload"))
    index = _integer(data["index"], 0, 10 if phase == "seed" else 9)
    event = data["event"]
    _require(type(event) is str)
    if event == "PREFLIGHT":
        _require(index == 0)
        payload = _preflight(data["payload"], phase)
    elif event == "DISPOSED":
        _require(index == (10 if phase == "seed" else 9))
        fields = _fields(data["payload"], DisposedPayload)
        payload = DisposedPayload(tuple(_disposal(item, slot)
                                        for slot, item in enumerate(_array(fields["players"], 2))))
    else:
        goal = None
        if phase == "seed":
            _require((event == "INITIAL" and index == 1)
                     or (event == "CONTROL" and index == 2)
                     or (event == "ACQUIRE" and 3 <= index <= 8)
                     or (event == "REMOVED" and index == 9))
            if event == "ACQUIRE":
                goal = _GOALS[index - 3]
        else:
            _require((event == "LOADED" and index == 1)
                     or (event == "REPLAY" and 2 <= index <= 7)
                     or (event == "REMOVED" and index == 8))
            if event == "REPLAY":
                goal = _GOALS[index - 2]
        payload = _players(data["payload"], goal)
    return StageReportObservation(observation.raw, observation.sha256, schema, run_id,
                                  phase, event, index, payload)
