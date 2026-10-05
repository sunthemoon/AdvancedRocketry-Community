"""Field, numeric and ownership controls for private stage observations."""
from __future__ import annotations

import ast
import copy
from dataclasses import FrozenInstanceError, fields, is_dataclass
import hashlib
import inspect
import json
import re
import sys
import unittest
from unittest import mock

import classic_inventory_fixture_stages as stages
from classic_inventory_fixture_json import FixtureJsonError, JsonNumber, JsonRole, parse_fixture_json


ROLES = (
    ("earned", "180c1800-0000-4000-8000-000000000001", "ArceC18Owned"),
    ("control", "180c1800-0000-4000-8000-000000000002", "ArceC18Control"),
)
GOALS = tuple("advancedrocketrycommunity:classic/" + key for key in (
    "root", "block_press", "rolling", "electrolysis", "suited_up", "warp_core"))


def encode(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"), allow_nan=False).encode()


def player(slot):
    role, uuid, name = ROLES[slot]
    return {
        "role": role, "uuid": uuid, "name": name, "listed": False,
        "permission_level": 0, "non_fake": False, "game_mode": "creative",
        "item_counts": [0] * 10, "other_nonempty_slots": 0, "total_experience": 0,
        "done": [False] * 6, "obtained_ms": [None] * 6, "obtained_seconds": [None] * 6,
        "criteria_keys": [["has_inventory"] for _ in range(6)],
        "native_location": {"dimension": "minecraft:overworld",
                            "position": [8.5 if slot == 0 else 10.5, 200, 8.5],
                            "motion_bits": ["0000000000000000"] * 3,
                            "rotation_bits": ["00000000"] * 2, "vehicle_present": True},
    }


def preflight(phase):
    return {
        "world": "minecraft:overworld", "run_marker_sha256": "a" * 64,
        "receipt_sha256": None if phase == "seed" else "b" * 64,
        "identities": [dict(zip(("role", "uuid", "name"), row)) for row in ROLES],
        "file_presence": [False] * 8, "profile_map_counts": [0, 1000],
        "pending_requests": 0, "native_progress_cache_counts": [0, 0],
        "online_count": 0, "loaded_chunk": [0, 0], "cache_snapshot_sha256": "c" * 64,
        "host_admission": {
            "revision": 3, "world_data_game_type": "adventure",
            "raw_spawn": [-79, 64, 94], "shared_spawn": [-79, 64, 94],
            "border_contains_collision_region": False, "minimum_height": -64,
            "maximum_height": 320, "view_distance": 2, "simulation_distance": 3,
            "loaded_envelope": [-5, -5, 5, 5], "loaded_chunks": 121,
            "loaded_entities": 1024, "player_dimensions_bits": ["3f19999a", "3fe66666"],
            "constructor_iteration_upper_bound": 256,
            "setup_mode": "seed_add" if phase == "seed" else "reload_retained",
            "forced_target_membership": [False] * 121,
            "properties": {"configured_bytes": 1, "configured_sha256": "d" * 64,
                           "boot_sha256": "e" * 64, "live_bytes": 16384,
                           "live_sha256": "f" * 64, "logical_map_equal": False,
                           "reader_version": 1},
        },
    }


def record(phase="seed", event="INITIAL", index=1):
    if event == "PREFLIGHT":
        payload = preflight(phase)
    elif event == "DISPOSED":
        payload = {"players": [{"role": row[0], "uuid": row[1], "listed": True,
                                "connected": True, "channel_open": True,
                                "channel_active": True, "drained_messages": 2048,
                                "release_attempt_completed": False} for row in ROLES]}
    else:
        goal = (GOALS[index - (3 if phase == "seed" else 2)]
                if event in ("ACQUIRE", "REPLAY") and 0 <= index - (3 if phase == "seed" else 2) < 6
                else None)
        payload = {"goal": goal, "players": [player(0), player(1)]}
    return {"schema": 1, "run_id": "00000000-0000-0000-0000-000000000000",
            "phase": phase, "event": event, "index": index, "payload": payload}


def set_path(value, path, replacement):
    for key in path[:-1]:
        value = value[key]
    value[path[-1]] = replacement


def token(value, path, lexeme):
    changed = copy.deepcopy(value)
    marker = "TOKEN_MARKER_47E1"
    set_path(changed, path, marker)
    raw = encode(changed)
    assert raw.count(encode(marker)) == 1
    return raw.replace(encode(marker), lexeme.encode("ascii"))


class StageCase(unittest.TestCase):
    def parse(self, value):
        return stages.parse_stage_report(encode(value))

    def refuse(self, value):
        raw = value if type(value) is bytes else encode(value)
        with self.assertRaises(stages.FixtureStageError) as caught:
            stages.parse_stage_report(raw)
        error = caught.exception
        self.assertEqual("RECORD_SHAPE", error.code)
        self.assertEqual("RECORD_SHAPE", str(error))
        self.assertEqual(("RECORD_SHAPE",), error.args)
        self.assertIsNone(error.__cause__)
        self.assertIsNone(error.__context__)
        self.assertTrue(error.__suppress_context__)

    def mutate(self, value, path, replacement):
        changed = copy.deepcopy(value)
        set_path(changed, path, replacement)
        self.refuse(changed)


class EnvelopeTest(StageCase):
    def test_every_nonterminal_row(self):
        count = 0
        for phase in ("seed", "reload"):
            events = ([("PREFLIGHT", 0), ("INITIAL", 1), ("CONTROL", 2)]
                      + [("ACQUIRE", i) for i in range(3, 9)] + [("REMOVED", 9), ("DISPOSED", 10)]
                      if phase == "seed" else [("PREFLIGHT", 0), ("LOADED", 1)]
                      + [("REPLAY", i) for i in range(2, 8)] + [("REMOVED", 8), ("DISPOSED", 9)])
            for event, index in events:
                with self.subTest(phase=phase, event=event, index=index):
                    observed = self.parse(record(phase, event, index))
                    self.assertEqual((phase, event, index), (observed.phase, observed.event, observed.index))
                    family = (stages.PreflightPayload if event == "PREFLIGHT" else
                              stages.DisposedPayload if event == "DISPOSED" else stages.PlayerStagePayload)
                    self.assertIs(type(observed.payload), family)
                    if event in ("ACQUIRE", "REPLAY"):
                        self.assertEqual(GOALS[index - (3 if phase == "seed" else 2)], observed.payload.goal)
                    count += 1
        self.assertEqual(21, count)

    def test_full_phase_index_matrix(self):
        for phase in ("seed", "reload"):
            valid = ({"PREFLIGHT": {0}, "INITIAL": {1}, "CONTROL": {2}, "ACQUIRE": set(range(3, 9)),
                      "REMOVED": {9}, "DISPOSED": {10}} if phase == "seed" else
                     {"PREFLIGHT": {0}, "LOADED": {1}, "REPLAY": set(range(2, 8)),
                      "REMOVED": {8}, "DISPOSED": {9}})
            for event in ("PREFLIGHT", "INITIAL", "CONTROL", "ACQUIRE", "LOADED", "REPLAY",
                          "REMOVED", "DISPOSED", "READY_FOR_STOP", "FAILED", "unknown"):
                for index in range(12):
                    if index not in valid.get(event, set()):
                        with self.subTest(phase=phase, event=event, index=index):
                            self.refuse(record(phase, event, index))

    def test_envelope_scalars(self):
        value = record()
        for key, wrongs in {
            "schema": (0, 2, True, None, "1", [], {}),
            "phase": ("SEED", " seed", "", True, None, [], {}),
            "event": ("initial", "INITIAL ", True, None, 1, [], {}),
            "index": (-1, 11, True, None, "1", [], {}),
        }.items():
            for wrong in wrongs:
                with self.subTest(key=key, wrong=wrong):
                    self.mutate(value, [key], wrong)

    def test_uuid_spelling_without_version_variant_restriction(self):
        value = record()
        for good in ("00000000-0000-0000-0000-000000000000", "abcdefab-cdef-fabc-defa-bcdefabcdefa"):
            changed = copy.deepcopy(value)
            changed["run_id"] = good
            self.assertEqual(good, self.parse(changed).run_id)
        for wrong in ("ABCDEFAB-cdef-fabc-defa-bcdefabcdefa", "0" * 36,
                      "00000000-0000-0000-0000-00000000000", value["run_id"] + "\n", None, 1):
            self.mutate(value, ["run_id"], wrong)

    def test_exact_envelope_keys(self):
        for key in record():
            value = record()
            del value[key]
            self.refuse(value)
        for key in ("unknown", "raw", "sha256", "Schema"):
            value = record()
            value[key] = None
            self.refuse(value)

    def test_goal_mapping_and_null(self):
        for phase, event, start in (("seed", "ACQUIRE", 3), ("reload", "REPLAY", 2)):
            for index in range(6):
                value = record(phase, event, start + index)
                for wrong in (None, GOALS[(index + 1) % 6], GOALS[index].upper(), True, [], {}):
                    self.mutate(value, ["payload", "goal"], wrong)
        for phase, event, index in (("seed", "INITIAL", 1), ("seed", "CONTROL", 2),
                                    ("reload", "LOADED", 1), ("seed", "REMOVED", 9),
                                    ("reload", "REMOVED", 8)):
            self.mutate(record(phase, event, index), ["payload", "goal"], GOALS[0])

    def test_payload_family_cannot_be_exchanged(self):
        values = [record(), record("seed", "PREFLIGHT", 0), record("seed", "DISPOSED", 10)]
        for i, value in enumerate(values):
            for j, other in enumerate(values):
                if i != j:
                    self.mutate(value, ["payload"], other["payload"])


class ShapeTest(StageCase):
    def test_every_leaf_refuses_wrong_scalar_types(self):
        def walk(value, path=()):
            if type(value) is dict:
                for key, item in value.items():
                    yield from walk(item, path + (key,))
            elif type(value) is list:
                for index, item in enumerate(value):
                    yield from walk(item, path + (index,))
            else:
                yield path, value
        for value in (record(), record("seed", "PREFLIGHT", 0), record("seed", "DISPOSED", 10)):
            for path, leaf in walk(value):
                wrongs = ([], {}, "wrong", 1) if type(leaf) is bool else (
                    ([], {}, True, 0) if type(leaf) is str else ([], {}, True, "number"))
                for wrong in wrongs:
                    with self.subTest(path=path, wrong=wrong):
                        self.mutate(value, list(path), wrong)

    def test_every_object_missing_extra_and_wrong_container(self):
        cases = [record(), record("seed", "PREFLIGHT", 0), record("seed", "DISPOSED", 10)]
        def walk(value, path=()):
            if type(value) is dict:
                yield path, value
                for key, item in value.items():
                    yield from walk(item, path + (key,))
            elif type(value) is list:
                for index, item in enumerate(value):
                    yield from walk(item, path + (index,))
        for value in cases:
            for path, obj in walk(value):
                for key in obj:
                    changed = copy.deepcopy(value)
                    target = changed
                    for part in path:
                        target = target[part]
                    del target[key]
                    with self.subTest(path=path, missing=key):
                        self.refuse(changed)
                changed = copy.deepcopy(value)
                target = changed
                for part in path:
                    target = target[part]
                target["unknown"] = "secret-context"
                self.refuse(changed)
                if path:
                    for replacement in ([], None, "object", True, 0):
                        self.mutate(value, list(path), replacement)

    def test_every_array_length_and_wrong_container(self):
        def walk(value, path=()):
            if type(value) is dict:
                for key, item in value.items():
                    yield from walk(item, path + (key,))
            elif type(value) is list:
                yield path, value
                for index, item in enumerate(value):
                    yield from walk(item, path + (index,))
        for value in (record(), record("seed", "PREFLIGHT", 0), record("seed", "DISPOSED", 10)):
            for path, array in walk(value):
                for replacement in (array[:-1], array + [array[-1]], {}, None, True, "array", 1):
                    with self.subTest(path=path, replacement=replacement):
                        self.mutate(value, list(path), replacement)

    def test_role_order_and_identity_constants(self):
        for value, path in ((record(), ["payload", "players"]),
                            (record("seed", "PREFLIGHT", 0), ["payload", "identities"]),
                            (record("seed", "DISPOSED", 10), ["payload", "players"])):
            array = value["payload"][path[-1]]
            self.mutate(value, path, array[::-1])
            for slot, identity in enumerate(array):
                for key in ("role", "uuid", "name"):
                    if key in identity:
                        for wrong in (identity[key].upper(), None, 0, identity[key] + " "):
                            self.mutate(value, path + [slot, key], wrong)

    def test_all_boolean_fields_accept_either_and_reject_nonbool(self):
        def walk(value, path=()):
            if type(value) is dict:
                for key, item in value.items():
                    yield from walk(item, path + (key,))
            elif type(value) is list:
                for index, item in enumerate(value):
                    yield from walk(item, path + (index,))
            elif type(value) is bool:
                yield path
        for value in (record(), record("seed", "PREFLIGHT", 0), record("seed", "DISPOSED", 10)):
            for path in walk(value):
                for good in (False, True):
                    changed = copy.deepcopy(value)
                    set_path(changed, path, good)
                    self.parse(changed)
                for wrong in (0, 1, None, "true", [], {}):
                    with self.subTest(path=path, wrong=wrong):
                        self.mutate(value, list(path), wrong)

    def test_hash_spellings_and_phase_receipt(self):
        value = record("reload", "PREFLIGHT", 0)
        paths = [["payload", key] for key in ("run_marker_sha256", "receipt_sha256", "cache_snapshot_sha256")]
        paths += [["payload", "host_admission", "properties", key]
                  for key in ("configured_sha256", "boot_sha256", "live_sha256")]
        for path in paths:
            for wrong in ("A" * 64, "g" * 64, "a" * 63, "a" * 65, "a" * 64 + "\n", None, 1, [], {}):
                self.mutate(value, path, wrong)
        self.mutate(record("seed", "PREFLIGHT", 0), ["payload", "receipt_sha256"], "a" * 64)

    def test_all_fixed_string_and_tuple_constants(self):
        value = record("seed", "PREFLIGHT", 0)
        for path, wrong in ((["payload", "world"], "minecraft:the_nether"),
                            (["payload", "host_admission", "world_data_game_type"], "creative"),
                            (["payload", "host_admission", "setup_mode"], "reload_retained"),
                            (["payload", "host_admission", "player_dimensions_bits", 0], "3F19999A"),
                            (["payload", "host_admission", "player_dimensions_bits", 1], "3f19999a")):
            self.mutate(value, path, wrong)
        for slot in range(2):
            base = ["payload", "players", slot]
            for path, wrong in ((["game_mode"], "adventure"),
                                (["native_location", "dimension"], "minecraft:the_nether"),
                                (["native_location", "motion_bits", 0], "8000000000000000"),
                                (["native_location", "rotation_bits", 0], "80000000")):
                self.mutate(record(), base + path, wrong)
            for index in range(6):
                self.mutate(record(), base + ["criteria_keys", index, 0], "other")

    def test_spawn_equality_and_envelope_order(self):
        value = record("seed", "PREFLIGHT", 0)
        self.mutate(value, ["payload", "host_admission", "shared_spawn"], [-78, 64, 94])
        self.mutate(value, ["payload", "host_admission", "loaded_envelope"], [5, -5, -5, 5])
        self.mutate(record("reload", "PREFLIGHT", 0), ["payload", "host_admission", "setup_mode"], "seed_add")


class IntegerTest(StageCase):
    def cases(self):
        pre = record("seed", "PREFLIGHT", 0)
        host = ["payload", "host_admission"]
        properties = host + ["properties"]
        player_path = ["payload", "players", 0]
        result = [(record(), ["schema"], 1, 1), (record(), ["index"], 1, 1)]
        result += [(pre, ["payload"] + path, lo, hi) for path, lo, hi in (
            (["profile_map_counts", 0], 0, 1000), (["pending_requests"], 0, 0),
            (["native_progress_cache_counts", 0], 0, 0), (["online_count"], 0, 0),
            (["loaded_chunk", 0], 0, 0))]
        result += [(pre, host + path, lo, hi) for path, lo, hi in (
            (["revision"], 3, 3), (["minimum_height"], -64, -64), (["maximum_height"], 320, 320),
            (["view_distance"], 2, 2), (["simulation_distance"], 3, 3), (["loaded_chunks"], 121, 121),
            (["loaded_entities"], 0, 1024), (["constructor_iteration_upper_bound"], 256, 256))]
        result += [(pre, properties + [key], lo, hi) for key, lo, hi in (
            ("configured_bytes", 1, 16384), ("live_bytes", 1, 16384), ("reader_version", 1, 1))]
        result += [(record(), player_path + [key], lo, hi) for key, lo, hi in (
            ("permission_level", 0, 0), ("other_nonempty_slots", 0, 41), ("total_experience", 0, 2147483647))]
        result += [(record(), player_path + ["item_counts", i], 0, 64) for i in range(10)]
        result += [(record("seed", "DISPOSED", 10), ["payload", "players", i, "drained_messages"], 0, 2048)
                   for i in range(2)]
        return result

    def test_all_integer_bounds(self):
        for value, path, lo, hi in self.cases():
            for good in {lo, hi}:
                changed = copy.deepcopy(value)
                set_path(changed, path, good)
                self.parse(changed)
            for wrong in (lo - 1, hi + 1, True, False, None, "0", [], {}):
                with self.subTest(path=path, wrong=wrong):
                    self.mutate(value, path, wrong)

    def test_all_integer_grammar_not_binary64_coercion(self):
        for value, path, lo, hi in self.cases():
            for wrong in (str(lo) + ".0", str(lo) + "e0"):
                self.refuse(token(value, path, wrong))

    def test_negative_zero_where_range_contains_zero(self):
        for value, path, lo, hi in self.cases():
            raw = token(value, path, "-0")
            if lo <= 0 <= hi:
                self.assertIs(stages.parse_stage_report(raw).raw, raw)
            else:
                self.refuse(raw)
        value = record("seed", "PREFLIGHT", 0)
        value["payload"]["host_admission"]["raw_spawn"] = [0, 200, 0]
        value["payload"]["host_admission"]["shared_spawn"] = [0, 200, 0]
        raw = token(value, ["payload", "host_admission", "raw_spawn", 0], "-0")
        self.assertEqual((0, 200, 0), stages.parse_stage_report(raw).payload.host_admission.raw_spawn)

    def test_spawn_signed_ranges_every_axis(self):
        for key in ("raw_spawn", "shared_spawn"):
            for axis, low, high in ((0, -79, 94), (1, 64, 256), (2, -79, 94)):
                for good in (low, high):
                    value = record("seed", "PREFLIGHT", 0)
                    for name in ("raw_spawn", "shared_spawn"):
                        value["payload"]["host_admission"][name][axis] = good
                    self.parse(value)
                for wrong in (low - 1, high + 1, True, None, "0", 1.0):
                    self.mutate(record("seed", "PREFLIGHT", 0), ["payload", "host_admission", key, axis], wrong)

    def test_long_integer_tokens_bound_before_conversion(self):
        limit = sys.get_int_max_str_digits()
        for value, path in ((record(), ["schema"]), (record(), ["payload", "players", 0, "total_experience"]),
                            (record("seed", "PREFLIGHT", 0), ["payload", "host_admission", "minimum_height"])):
            for wrong in ("9" * 6000, "-" + "9" * 6000):
                self.refuse(token(value, path, wrong))
        self.assertEqual(limit, sys.get_int_max_str_digits())

    def test_timestamp_floor_and_null_pairs(self):
        for millis in (0, 1, 999, 1000, 1001, 9223372036854775807):
            value = record()
            value["payload"]["players"][0]["obtained_ms"] = [millis] * 6
            value["payload"]["players"][0]["obtained_seconds"] = [millis // 1000] * 6
            observed = self.parse(value).payload.players[0]
            self.assertEqual((millis,) * 6, observed.obtained_ms)
            self.assertEqual((False,) * 6, observed.done)
        for index in range(6):
            for ms, seconds in ((None, 0), (0, None), (1000, 0), (-1, 0),
                                (9223372036854775808, 9223372036854775),
                                (9223372036854775807, 9223372036854776)):
                value = record()
                value["payload"]["players"][0]["obtained_ms"][index] = ms
                value["payload"]["players"][0]["obtained_seconds"][index] = seconds
                self.refuse(value)

    def test_timestamp_type_grammar_and_zero(self):
        for key in ("obtained_ms", "obtained_seconds"):
            for slot in range(2):
                for index in range(6):
                    value = record()
                    value["payload"]["players"][slot]["obtained_ms"][index] = 0
                    value["payload"]["players"][slot]["obtained_seconds"][index] = 0
                    path = ["payload", "players", slot, key, index]
                    for wrong in (True, "0", [], {}):
                        self.mutate(value, path, wrong)
                    for wrong in ("0.0", "0e0", "9" * 6000):
                        self.refuse(token(value, path, wrong))
                    observed = stages.parse_stage_report(token(value, path, "-0"))
                    self.assertEqual(0, getattr(observed.payload.players[slot], key)[index])


class DecimalTest(StageCase):
    def test_decimal_normalization_against_small_exact_reference(self):
        # A separately expressed bounded rational oracle for emitted spellings,
        # not a production dependency or an arbitrary-length conversion.
        from fractions import Fraction
        for target, axis in ((Fraction(17, 2), 0), (Fraction(200), 1)):
            for coefficient in (17, 85, 200, 2000, 8500):
                for exponent in range(-4, 4):
                    lexeme = str(coefficient) + "e" + str(exponent)
                    raw = token(record(), ["payload", "players", 0, "native_location", "position", axis], lexeme)
                    actual = Fraction(coefficient) * Fraction(10) ** exponent
                    if actual == target:
                        self.assertEqual(lexeme, stages.parse_stage_report(raw).payload.players[0]
                                         .native_location.position[axis].lexeme)
                    else:
                        self.refuse(raw)

    def test_equivalent_position_lexemes_retained(self):
        for slot in range(2):
            candidates = (("8.5", "85e-1", "0.85e1", "8.5000", "85000E-4") if slot == 0 else
                          ("10.5", "105e-1", "1.05e1", "10.5000", "105000E-4"))
            for axis, lexemes in ((0, candidates), (1, ("200", "200.0", "2e2", "20000e-2", "0.002e+5")),
                                  (2, ("8.5", "85e-1", "0.85e1", "8.5000"))):
                for lexeme in lexemes:
                    raw = token(record(), ["payload", "players", slot, "native_location", "position", axis], lexeme)
                    observed = stages.parse_stage_report(raw).payload.players[slot].native_location.position[axis]
                    self.assertIs(type(observed), JsonNumber)
                    self.assertEqual(lexeme, observed.lexeme)
                    self.assertEqual(parse_fixture_json(raw, JsonRole.REPORT).value.pairs[-1][1].pairs[1][1][slot]
                                     .pairs[-1][1].pairs[1][1][axis], observed)

    def test_exponent_leading_zeroes_and_large_coefficients(self):
        lexemes = ("2e+" + "0" * 5000 + "2", "2e" + "0" * 5000 + "2",
                   "200." + "0" * 5000, "2" + "0" * 5000 + "e-4998")
        for lexeme in lexemes:
            raw = token(record(), ["payload", "players", 0, "native_location", "position", 1], lexeme)
            observed = stages.parse_stage_report(raw)
            self.assertEqual(lexeme, observed.payload.players[0].native_location.position[1].lexeme)

    def test_exact_decimal_not_rounded_projection(self):
        for lexeme in ("200.000000000000000000001", "199.999999999999999999999",
                       "2.00000000000000000000001e2", "8.500000000000000000000001"):
            axis = 0 if lexeme.startswith("8") else 1
            expected = 8.5 if axis == 0 else 200.0
            self.assertEqual(expected, float(lexeme))
            self.refuse(token(record(), ["payload", "players", 0, "native_location", "position", axis], lexeme))

    def test_positions_wrong_scalar_and_order(self):
        for slot in range(2):
            for axis in range(3):
                for wrong in (None, True, "8.5", [], {}, 0, -0.0, -200):
                    self.mutate(record(), ["payload", "players", slot, "native_location", "position", axis], wrong)
        self.mutate(record(), ["payload", "players", 1, "native_location", "position"], [8.5, 200, 10.5])

    def test_huge_exponent_rejection_and_projection_precedence(self):
        path = ["payload", "players", 0, "native_location", "position", 1]
        self.refuse(token(record(), path, "2e-" + "9" * 5000))
        for lexeme in ("2e" + "9" * 5000, "1e309"):
            with self.assertRaises(FixtureJsonError) as caught:
                stages.parse_stage_report(token(record(), path, lexeme))
            self.assertEqual("JSON_NUMBER", caught.exception.code)
        self.refuse(token(record(), path, "2e-2"))


class OwnershipAndBoundaryTest(StageCase):
    def test_raw_hash_and_object_order(self):
        value = record()
        raw = encode(value)
        observed = stages.parse_stage_report(raw)
        self.assertIs(raw, observed.raw)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), observed.sha256)
        def reverse(item):
            if type(item) is dict:
                return {key: reverse(value) for key, value in reversed(list(item.items()))}
            if type(item) is list:
                return [reverse(value) for value in item]
            return item
        other = self.parse(reverse(value))
        self.assertEqual(observed.payload, other.payload)
        self.assertNotEqual(observed.sha256, other.sha256)

    def test_all_return_records_frozen_slotted_and_collections_tuples(self):
        def walk(value):
            if is_dataclass(value):
                self.assertFalse(hasattr(value, "__dict__"))
                for field in fields(value):
                    with self.assertRaises(FrozenInstanceError):
                        setattr(value, field.name, getattr(value, field.name))
                    walk(getattr(value, field.name))
            elif isinstance(value, tuple):
                for item in value:
                    walk(item)
            else:
                self.assertIn(type(value), (str, bytes, bool, int, float, type(None)))
        for value in (record(), record("seed", "PREFLIGHT", 0), record("seed", "DISPOSED", 10)):
            walk(self.parse(value))
        source = record()
        result = self.parse(source)
        source["payload"]["players"][0]["item_counts"][0] = 64
        self.assertEqual((0,) * 10, result.payload.players[0].item_counts)

    def test_exact_record_field_names(self):
        expected = {
            stages.StageReportObservation: ("raw", "sha256", "schema", "run_id", "phase", "event", "index", "payload"),
            stages.Identity: ("role", "uuid", "name"),
            stages.PreflightPayload: tuple(preflight("seed")),
            stages.HostAdmission: tuple(preflight("seed")["host_admission"]),
            stages.PropertiesObservation: tuple(preflight("seed")["host_admission"]["properties"]),
            stages.PlayerStagePayload: ("goal", "players"), stages.PlayerState: tuple(player(0)),
            stages.NativeLocation: tuple(player(0)["native_location"]),
            stages.DisposedPayload: ("players",),
            stages.DisposalState: tuple(record("seed", "DISPOSED", 10)["payload"]["players"][0]),
        }
        for cls, names in expected.items():
            self.assertEqual(names, tuple(field.name for field in fields(cls)))

    def test_calls_syntax_once_and_report_role_before_fields(self):
        raw = encode(record())
        with mock.patch.object(stages, "parse_fixture_json", wraps=parse_fixture_json) as parser:
            stages.parse_stage_report(raw)
            parser.assert_called_once_with(raw, JsonRole.REPORT)
        error = FixtureJsonError("JSON_BYTES", JsonRole.REPORT)
        with mock.patch.object(stages, "parse_fixture_json", side_effect=error) as parser:
            with self.assertRaises(FixtureJsonError) as caught:
                stages.parse_stage_report(b"contents")
            self.assertIs(error, caught.exception)
            parser.assert_called_once_with(b"contents", JsonRole.REPORT)

    def test_inherited_json_errors_propagate(self):
        cases = ((bytearray(b"{}"), "JSON_BYTES"), (b"{" + b" " * 16384, "JSON_BYTES"),
                 (b"\xef\xbb\xbf{}", "JSON_ENCODING"), (b"{", "JSON_SYNTAX"),
                 (b'{"secret":0,"secret":1}', "JSON_DUPLICATE"), (b"[]", "JSON_ROOT"),
                 (b'{"x":1e309}', "JSON_NUMBER"),
                 (b'{"x":' + b"[" * 8 + b"0" + b"]" * 8 + b"}", "JSON_DEPTH"),
                 (b'{"x":[' + b",".join([b"0"] * 2046) + b"]}", "JSON_NODES"))
        for raw, code in cases:
            with self.subTest(code=code):
                with self.assertRaises(FixtureJsonError) as caught:
                    stages.parse_stage_report(raw)
                self.assertEqual(code, caught.exception.code)
                self.assertIs(JsonRole.REPORT, caught.exception.role)

    def test_equality_limits_and_unknown_structure_charged(self):
        raw = encode(record())
        padded = raw + b" " * (16384 - len(raw))
        self.assertIs(padded, stages.parse_stage_report(padded).raw)
        with self.assertRaises(FixtureJsonError) as caught:
            stages.parse_stage_report(padded + b" ")
        self.assertEqual("JSON_BYTES", caught.exception.code)
        raw = b'{"x":[' + b",".join([b"0"] * 2045) + b"]}"
        self.assertEqual(2048, parse_fixture_json(raw, JsonRole.REPORT).nodes)
        self.refuse(raw)
        depth = b'{"x":' + b"[" * 6 + b"0" + b"]" * 6 + b"}"
        self.assertEqual(8, parse_fixture_json(depth, JsonRole.REPORT).depth)
        self.refuse(depth)

    def test_no_permission_fields_or_io_cli_surface(self):
        tree = ast.parse(inspect.getsource(stages))
        imports = {node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom)}
        imports.update(alias.name for node in ast.walk(tree) if isinstance(node, ast.Import) for alias in node.names)
        self.assertEqual({"__future__", "dataclasses", "re", "classic_inventory_fixture_json"}, imports)
        calls = {node.func.id for node in ast.walk(tree) if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)}
        self.assertTrue(calls.isdisjoint({"open", "print", "exec", "eval", "input", "compile"}))
        self.assertNotIn("if __name__", inspect.getsource(stages))
        result = self.parse(record("seed", "PREFLIGHT", 0))
        for name in ("ready", "owned", "origin", "freshness", "success", "permission", "admitted"):
            self.assertFalse(hasattr(result, name))


if __name__ == "__main__":
    unittest.main()
