"""Terminal shape, diagnostic and immutable ownership checks; no host tests."""
import copy
from dataclasses import FrozenInstanceError, fields
import hashlib
import json
import re
import sys
import unittest
from unittest import mock

import classic_inventory_fixture_json as syntax
import classic_inventory_fixture_reports as reports


RUN_ID = "01234567-89ab-cdef-0123-456789abcdef"
REASONS = (
    "AUTHORITY", "PHASE", "LOADED_AREA", "BINDING", "COLLISION", "RECEIPT",
    "RECORD_SHAPE", "CACHE_ACCESS", "CACHE_SHAPE", "CACHE_CHANGED",
    "CONSTRUCTION", "JOIN", "LOADED_PROGRESS", "INVENTORY_OBSERVATION",
    "REPLAY_OBSERVATION", "DISPOSAL", "DEADLINE", "INTERNAL",
)


def ready(phase="seed", complete=True):
    return {"schema": 1, "run_id": RUN_ID, "phase": phase, "event": "READY_FOR_STOP",
            "index": 11 if phase == "seed" else 10,
            "payload": {"action_complete": complete, "owned_handles": 0,
                        "observed_rows": 12 if phase == "seed" else 11}}


def failed(phase="seed", index=0):
    return {"schema": 1, "run_id": RUN_ID, "phase": phase, "event": "FAILED", "index": index,
            "payload": {"stage": "construction", "reason": "CONSTRUCTION",
                        "owned_handles_remaining": 0, "cleanup_errors": 0}}


def encode(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def token(value, key, spelling):
    raw = encode(value)
    result, count = re.subn(rb'"' + key.encode("ascii") + rb'":[0-9]+',
                            b'"' + key.encode("ascii") + b'":' + spelling, raw)
    assert count == 1
    return result


class TerminalReportTest(unittest.TestCase):
    def observe(self, value):
        return reports.parse_terminal_report(encode(value))

    def refuse(self, value):
        with self.assertRaises(reports.FixtureReportError) as failure:
            self.observe(value)
        error = failure.exception
        self.assertEqual("RECORD_SHAPE", str(error))
        self.assertEqual(("RECORD_SHAPE",), error.args)
        self.assertEqual("RECORD_SHAPE", error.code)
        self.assertIsNone(error.__cause__)
        self.assertIsNone(error.__context__)
        self.assertTrue(error.__suppress_context__)
        return error

    def refuse_raw_shape(self, raw):
        with self.assertRaises(reports.FixtureReportError) as failure:
            reports.parse_terminal_report(raw)
        self.assertEqual(("RECORD_SHAPE",), failure.exception.args)

    def json_refusal(self, raw, code):
        with self.assertRaises(syntax.FixtureJsonError) as failure:
            reports.parse_terminal_report(raw)
        self.assertEqual(code, failure.exception.code)
        self.assertIs(failure.exception.role, syntax.JsonRole.REPORT)
        self.assertTrue(failure.exception.__suppress_context__)

    def test_numeric_token_fixture_changes_only_the_value_lexeme(self):
        original = encode(ready())
        for spelling in (b"-0", b"1e0", b"1.0", b"9" * 6_000):
            self.assertEqual(original.replace(b'"schema":1', b'"schema":' + spelling),
                             token(ready(), "schema", spelling))

    def test_ready_both_phases_exact_record_values(self):
        for phase, index, rows in (("seed", 11, 12), ("reload", 10, 11)):
            with self.subTest(phase=phase):
                value = ready(phase)
                raw = encode(value)
                result = reports.parse_terminal_report(raw)
                self.assertEqual((1, RUN_ID, phase, "READY_FOR_STOP", index),
                                 (result.schema, result.run_id, result.phase, result.event, result.index))
                self.assertEqual(reports.ReadyForStopPayload(True, 0, rows), result.payload)
                self.assertEqual(raw, result.raw)
                self.assertEqual(hashlib.sha256(raw).hexdigest(), result.sha256)

    def test_false_completion_is_type_valid_not_permission(self):
        for phase in ("seed", "reload"):
            result = self.observe(ready(phase, False))
            self.assertIs(result.payload.action_complete, False)
            for name in ("ready", "owned", "fresh", "success", "receipt", "can_stop", "authority"):
                self.assertFalse(hasattr(result, name))
                self.assertFalse(hasattr(result.payload, name))

    def test_failed_every_phase_index_boundary_and_interior(self):
        for phase, maximum in (("seed", 11), ("reload", 10)):
            for index in range(maximum + 1):
                with self.subTest(phase=phase, index=index):
                    result = self.observe(failed(phase, index))
                    self.assertEqual(index, result.index)
                    self.assertEqual(reports.FailedPayload("construction", "CONSTRUCTION", 0, 0), result.payload)

    def test_failed_all_eighteen_reasons(self):
        self.assertEqual(18, len(REASONS))
        for reason in REASONS:
            value = failed()
            value["payload"]["reason"] = reason
            with self.subTest(reason=reason):
                self.assertEqual(reason, self.observe(value).payload.reason)

    def test_failure_reason_exact_without_normalization(self):
        for reason in ("construction", " CONSTRUCTION", "CONSTRUCTION ", "CONSTRUCTION\n",
                       "OTHER", "\uff23ONSTRUCTION", "", None, True, 1, []):
            value = failed()
            value["payload"]["reason"] = reason
            with self.subTest(reason=reason):
                self.refuse(value)

    def test_all_ascii_stage_scalars_including_escaped_controls(self):
        for scalar in range(128):
            value = failed()
            value["payload"]["stage"] = chr(scalar)
            with self.subTest(scalar=scalar):
                self.assertEqual(chr(scalar), self.observe(value).payload.stage)

    def test_stage_byte_length_equality_and_overflow(self):
        for length in (1, 64):
            value = failed()
            value["payload"]["stage"] = "x" * length
            self.assertEqual(length, len(self.observe(value).payload.stage))
        for stage in ("", "x" * 65, "\x00" * 65):
            value = failed()
            value["payload"]["stage"] = stage
            self.refuse(value)

    def test_stage_non_ascii_wrong_types_and_no_coercion(self):
        for stage in ("\x80", "\x9f", "\u00e9", "\U0001f600", None, False, 1, [], {}):
            value = failed()
            value["payload"]["stage"] = stage
            with self.subTest(stage=stage):
                self.refuse(value)

    def test_literal_c0_stage_propagates_json_string_failure(self):
        raw = encode(failed()).replace(b'"construction"', b'"\x00"')
        self.json_refusal(raw, "JSON_STRING")

    def test_uuid_all_zero_and_all_f_no_variant_or_version_restriction(self):
        for run_id in ("00000000-0000-0000-0000-000000000000",
                       "ffffffff-ffff-ffff-ffff-ffffffffffff"):
            value = ready()
            value["run_id"] = run_id
            self.assertEqual(run_id, self.observe(value).run_id)

    def test_uuid_exact_lowercase_hyphenation_and_length(self):
        for run_id in (RUN_ID.upper(), RUN_ID.replace("-", ""), " " + RUN_ID,
                       RUN_ID + "\n", RUN_ID[:-1], RUN_ID + "0", RUN_ID.replace("-", "_"),
                       "g" + RUN_ID[1:], None, True, 1, []):
            value = ready()
            value["run_id"] = run_id
            with self.subTest(run_id=run_id):
                self.refuse(value)

    def test_phase_exact_decoded_string_without_trim(self):
        for phase in ("SEED", "seed ", " reload", "seed\n", "other", None, False, 0, []):
            value = ready()
            value["phase"] = phase
            with self.subTest(phase=phase):
                self.refuse(value)

    def test_only_two_exact_terminal_events(self):
        for event in ("ready_for_stop", "FAILED ", "FAILED\n", "READY", "HOST_STARTED",
                      "INVENTORY_OBSERVATION", "", None, True, 1, {}, []):
            value = ready()
            value["event"] = event
            with self.subTest(event=event):
                self.refuse(value)

    def test_schema_exact_numerical_one(self):
        for schema in (0, -1, 2, 16, None, True, False, "1", 1.0, {}, []):
            value = ready()
            value["schema"] = schema
            with self.subTest(schema=schema):
                self.refuse(value)

    def test_ready_index_and_row_counts_are_phase_specific(self):
        for phase, last, rows in (("seed", 11, 12), ("reload", 10, 11)):
            for index in (0, last - 1, last + 1, -1):
                value = ready(phase)
                value["index"] = index
                self.refuse(value)
            for count in (0, rows - 1, rows + 1, -1):
                value = ready(phase)
                value["payload"]["observed_rows"] = count
                self.refuse(value)

    def test_failed_index_outside_each_inclusive_phase_range(self):
        for phase, maximum in (("seed", 11), ("reload", 10)):
            for index in (-1, maximum + 1, 99):
                self.refuse(failed(phase, index))

    def test_failed_handle_cleanup_ranges_at_every_in_range_integer(self):
        for handles in range(3):
            for errors in range(17):
                value = failed()
                value["payload"]["owned_handles_remaining"] = handles
                value["payload"]["cleanup_errors"] = errors
                result = self.observe(value).payload
                self.assertEqual((handles, errors), (result.owned_handles_remaining, result.cleanup_errors))

    def test_failed_handle_cleanup_plus_one_and_negative(self):
        for key, values in (("owned_handles_remaining", (-1, 3, 16)),
                            ("cleanup_errors", (-1, 17, 99))):
            for number in values:
                value = failed()
                value["payload"][key] = number
                self.refuse(value)

    def test_ready_requires_numerical_zero_handles(self):
        for handles in (-1, 1, 2, 16):
            value = ready()
            value["payload"]["owned_handles"] = handles
            self.refuse(value)

    def test_all_integer_fields_refuse_boolean_string_fraction_null_containers(self):
        cases = ((ready(), "schema", False), (ready(), "index", False),
                 (ready(), "owned_handles", True), (ready(), "observed_rows", True),
                 (failed(), "owned_handles_remaining", True), (failed(), "cleanup_errors", True))
        for base, key, in_payload in cases:
            for wrong in (True, False, "0", "1", None, 0.0, 1.0, [], {}):
                value = copy.deepcopy(base)
                target = value["payload"] if in_payload else value
                target[key] = wrong
                with self.subTest(key=key, wrong=wrong):
                    self.refuse(value)

    def test_integer_exponent_grammar_is_not_coerced(self):
        for base, key, valid_integer in ((ready(), "schema", b"1"), (ready(), "index", b"11"),
                                         (ready(), "owned_handles", b"0"), (ready(), "observed_rows", b"12"),
                                         (failed(), "owned_handles_remaining", b"0"), (failed(), "cleanup_errors", b"0")):
            for spelling in (valid_integer + b"e0", valid_integer + b".0"):
                self.refuse_raw_shape(token(base, key, spelling))

    def test_negative_zero_accepted_where_zero_in_range_without_raw_rewrite(self):
        for base, key in ((ready(), "owned_handles"), (failed(), "index"),
                          (failed(), "owned_handles_remaining"), (failed(), "cleanup_errors")):
            raw = token(base, key, b"-0")
            result = reports.parse_terminal_report(raw)
            self.assertEqual(raw, result.raw)
            self.assertIn(b":" + b"-0", result.raw)
            observed = result.index if key == "index" else getattr(result.payload, key)
            self.assertIs(type(observed), int)
            self.assertEqual(0, observed)

    def test_negative_zero_refused_where_zero_not_valid(self):
        for base, key in ((ready(), "schema"), (ready(), "observed_rows"), (ready(), "index")):
            self.refuse_raw_shape(token(base, key, b"-0"))

    def test_long_integer_tokens_refuse_before_any_conversion(self):
        before = sys.get_int_max_str_digits()
        def bounded_conversion(lexeme):
            # Earlier valid envelope fields may convert; the target's long
            # lexeme must never reach this seam, nor any other oversized one.
            self.assertLessEqual(len(lexeme), 2)
            return int(lexeme)
        for base, key in ((ready(), "schema"), (ready(), "index"), (ready(), "owned_handles"),
                          (ready(), "observed_rows"), (failed(), "owned_handles_remaining"),
                          (failed(), "cleanup_errors")):
            with mock.patch.object(reports, "int", side_effect=bounded_conversion, create=True):
                self.refuse_raw_shape(token(base, key, b"9" * 6_000))
        self.assertEqual(before, sys.get_int_max_str_digits())

    def test_range_checked_before_even_bounded_integer_conversion(self):
        for base, key, spelling in ((ready(), "schema", b"2"), (ready(), "schema", b"0")):
            with mock.patch.object(reports, "int", side_effect=AssertionError("CONVERTED"), create=True):
                self.refuse_raw_shape(token(base, key, spelling))

    def test_action_complete_requires_exact_boolean(self):
        for complete in (None, 0, 1, "true", "false", [], {}):
            self.refuse(ready(complete=complete))

    def test_envelope_missing_extra_and_renamed_fields(self):
        for base in (ready(), failed()):
            for key in base:
                value = copy.deepcopy(base)
                del value[key]
                self.refuse(value)
                value = copy.deepcopy(base)
                value[key.upper()] = value.pop(key)
                self.refuse(value)
            value = copy.deepcopy(base)
            value["future"] = {"unknown": []}
            self.refuse(value)

    def test_payload_missing_extra_and_renamed_fields(self):
        for base in (ready(), failed()):
            for key in base["payload"]:
                value = copy.deepcopy(base)
                del value["payload"][key]
                self.refuse(value)
                value = copy.deepcopy(base)
                value["payload"][key.upper()] = value["payload"].pop(key)
                self.refuse(value)
            value = copy.deepcopy(base)
            value["payload"]["future"] = None
            self.refuse(value)

    def test_payload_requires_object_and_correct_event_shape(self):
        for payload in (None, [], "x", True, 0):
            value = ready()
            value["payload"] = payload
            self.refuse(value)
        first, second = ready(), failed()
        first["payload"], second["payload"] = second["payload"], first["payload"]
        self.refuse(first)
        self.refuse(second)

    def test_decoded_field_spelling_accepted_without_raw_normalization(self):
        raw = encode(ready()).replace(b'"schema"', b'"\\u0073chema"')
        raw = raw.replace(RUN_ID.encode("ascii"), RUN_ID.encode("ascii").replace(b"0", b"\\u0030", 1))
        result = reports.parse_terminal_report(raw)
        self.assertEqual(RUN_ID, result.run_id)
        self.assertEqual(raw, result.raw)

    def test_raw_bytes_hash_and_key_order_preserved_not_canonicalized(self):
        value = ready()
        first = encode(value)
        second = b" \n" + encode(dict(reversed(tuple(value.items())))) + b"\t"
        a, b = reports.parse_terminal_report(first), reports.parse_terminal_report(second)
        self.assertEqual(a.payload, b.payload)
        self.assertEqual(a.run_id, b.run_id)
        self.assertNotEqual(a.raw, b.raw)
        self.assertNotEqual(a.sha256, b.sha256)
        self.assertEqual(second, b.raw)

    def test_frozen_slotted_exact_record_fields_and_deep_ownership(self):
        expected = {
            reports.TerminalReportObservation: ("raw", "sha256", "schema", "run_id", "phase", "event", "index", "payload"),
            reports.ReadyForStopPayload: ("action_complete", "owned_handles", "observed_rows"),
            reports.FailedPayload: ("stage", "reason", "owned_handles_remaining", "cleanup_errors"),
        }
        for cls, names in expected.items():
            self.assertEqual(names, tuple(field.name for field in fields(cls)))
            self.assertTrue(cls.__dataclass_params__.frozen)
            self.assertEqual(names, cls.__slots__)
        for base in (ready(), failed()):
            result = self.observe(base)
            self.assertFalse(hasattr(result, "__dict__"))
            self.assertFalse(hasattr(result.payload, "__dict__"))
            with self.assertRaises(FrozenInstanceError):
                result.index = 0
            with self.assertRaises(FrozenInstanceError):
                result.payload = None
            first = fields(type(result.payload))[0].name
            with self.assertRaises(FrozenInstanceError):
                setattr(result.payload, first, None)
            self.assertIs(type(result.schema), int)
            self.assertIs(type(result.index), int)

    def test_report_byte_equality_and_overflow_before_shape(self):
        raw = encode(ready())
        equal = raw + b" " * (16_384 - len(raw))
        self.assertEqual(equal, reports.parse_terminal_report(equal).raw)
        self.json_refusal(equal + b"\xff", "JSON_BYTES")

    def test_unknown_fields_structural_limits_precede_shape_refusal(self):
        raw = b'{"ignored":[' + b",".join([b"0"] * 2_046) + b"]}"
        self.json_refusal(raw, "JSON_NODES")
        raw = b'{"ignored":' + b"[" * 7 + b"0" + b"]" * 7 + b"}"
        self.json_refusal(raw, "JSON_DEPTH")

    def test_json_errors_propagate_with_original_fixed_code_and_role(self):
        for raw, code in ((b"", "JSON_BYTES"), (bytearray(b"{}"), "JSON_BYTES"),
                          (b"\xff", "JSON_ENCODING"), (b"[]", "JSON_ROOT"),
                          (b'{"x":0,"x":1}', "JSON_DUPLICATE"),
                          (b'{"x":"\\q"}', "JSON_STRING"), (b'{"x":1e309}', "JSON_NUMBER"),
                          (b'{"x":0,}', "JSON_SYNTAX")):
            with self.subTest(code=code):
                self.json_refusal(raw, code)

    def test_existing_parser_called_once_with_fixed_report_role_and_same_bytes(self):
        raw = encode(ready())
        with mock.patch.object(reports, "parse_fixture_json", wraps=syntax.parse_fixture_json) as call:
            result = reports.parse_terminal_report(raw)
        call.assert_called_once_with(raw, syntax.JsonRole.REPORT)
        self.assertEqual(raw, result.raw)

    def test_parser_exception_is_not_caught_wrapped_or_reclassified(self):
        original = syntax.FixtureJsonError("JSON_DEPTH", syntax.JsonRole.REPORT)
        with mock.patch.object(reports, "parse_fixture_json", side_effect=original):
            with self.assertRaises(syntax.FixtureJsonError) as failure:
                reports.parse_terminal_report(b"{}")
        self.assertIs(original, failure.exception)

    def test_fixed_non_echo_shape_diagnostics(self):
        secret = "SECRET_STAGE_OR_REASON"
        cases = (ready(), failed())
        cases[0]["run_id"] = "D:/private/" + secret
        cases[1]["payload"]["reason"] = secret
        for value in cases:
            error = self.refuse(value)
            for surface in (str(error), repr(error), repr(error.args), repr(error.code)):
                self.assertNotIn(secret, surface)
                self.assertNotIn("D:/", surface)

    def test_consumer_has_no_io_cli_or_permission_surface(self):
        with mock.patch("builtins.open", side_effect=AssertionError("OPEN_CALLED")):
            result = self.observe(ready(complete=False))
        self.assertIs(result.payload.action_complete, False)
        self.assertFalse(hasattr(reports, "main"))
        self.assertFalse(hasattr(reports, "parse_file"))


if __name__ == "__main__":
    unittest.main()
