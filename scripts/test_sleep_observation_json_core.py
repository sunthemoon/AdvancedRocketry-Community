"""Finite functional checks for the frozen generic core and diagnostic convention.

These checks do not qualify process memory, runtime/native behavior or schema use.
"""
import dataclasses
import inspect
import unittest
from unittest.mock import patch

import sleep_observation_json_core as core


class SleepObservationJsonCoreTests(unittest.TestCase):
    def decode(self, payload):
        with patch.object(core, "_monotonic_ns", return_value=100):
            return core.decode_payload(payload, deadline_ns=1000000000)

    def accepted(self, payload):
        result = self.decode(payload)
        self.assertIsInstance(result, core.Decoded)
        self.assertEqual(result.kind, "JSON_CORE_DECODED")
        self.assertEqual(result.metrics.input_bytes, len(payload))
        self.assertEqual(result.metrics.consumed_bytes, len(payload))
        return result

    def refused(self, payload, code, offset, location=(), span=None, bound=None):
        result = self.decode(payload)
        self.assertIsInstance(result, core.Refused)
        self.assertEqual(result.kind, "JSON_CORE_REFUSED")
        expected = core.DecodeError(code, offset, location, span,
                                    *(bound if bound is not None else (None, None, None)))
        self.assertEqual(result.error, expected)
        self.assertFalse(hasattr(result, "value"))
        return result

    def metrics(self, result, *expected):
        self.assertEqual(dataclasses.astuple(result.metrics), expected)

    def test_api_and_frozen_records(self):
        signature = inspect.signature(core.decode_payload)
        self.assertEqual(tuple(signature.parameters), ("payload", "deadline_ns"))
        self.assertEqual(signature.parameters["deadline_ns"].kind, inspect.Parameter.KEYWORD_ONLY)
        result = self.accepted(b'{"n":-0}')
        number = result.value["n"]
        self.assertEqual(number, core.JsonNumber("-0", 5, 7))
        for record, name, value in ((result, "kind", "x"), (result.metrics, "nodes", 0),
                                    (number, "lexeme", "1")):
            with self.subTest(record=type(record).__name__):
                with self.assertRaises(dataclasses.FrozenInstanceError):
                    setattr(record, name, value)
        error = self.refused(b"[]", "ROOT_TYPE", 0).error
        with self.assertRaises(dataclasses.FrozenInstanceError):
            error.code = "x"

    def test_empty_nested_values_order_and_number_spans(self):
        self.metrics(self.accepted(b"{}"), 2, 2, 1, 0, 1, 0, 0)
        raw = b'{"z":[true,false,null,{"n":1e999999}],"a":""}'
        result = self.accepted(raw)
        self.assertEqual(list(result.value), ["z", "a"])
        values = result.value["z"]
        self.assertIs(values[0], True)
        self.assertIs(values[1], False)
        self.assertIsNone(values[2])
        start = raw.index(b"1e999999")
        self.assertEqual(values[3]["n"], core.JsonNumber("1e999999", start, start + 8))
        self.assertNotIsInstance(values[3]["n"], (int, float, bool))
        self.metrics(result, len(raw), len(raw), 8, 3, 3, 1, 8)

    def test_exclusive_tree_ownership_and_repeat_calls(self):
        first = self.accepted(b'{"a":[]}')
        second = self.accepted(b'{"a":[]}')
        first.value["a"].append("changed")
        first.value["b"] = None
        self.assertEqual(second.value, {"a": []})
        self.assertEqual(first.metrics, second.metrics)
        self.refused(b'{"a":[],"a":0}', "DUPLICATE_NAME", 8, (1,), (8, 11))
        self.assertEqual(self.accepted(b'{"a":[]}').value, {"a": []})

    def test_exact_input_type_without_hooks(self):
        class Trap:
            def __len__(self):
                raise AssertionError("input hook")

            def __repr__(self):
                raise AssertionError("input hook")

        class BytesSubclass(bytes):
            pass

        for payload in (Trap(), "{}", bytearray(b"{}"), memoryview(b"{}"),
                        BytesSubclass(b"{}"), iter((b"{}",))):
            with self.subTest(type=type(payload).__name__):
                result = self.refused(payload, "INPUT_TYPE", 0)
                self.metrics(result, 0, 0, 0, 0, 0, 0, 0)

    def test_preflight_order_and_unscanned_one_mib(self):
        for size in (524289, 1048576):
            with self.subTest(size=size), patch.object(core, "_Scanner", side_effect=AssertionError("scan")):
                raw = b"!" * size
                with patch.object(core, "_monotonic_ns", return_value=100):
                    result = core.decode_payload(raw, deadline_ns=True)
                self.assertEqual(result.error, core.DecodeError("INPUT_BYTES", 0, (), None,
                                                               "input_bytes", 524288, size))
                self.metrics(result, size, 0, 0, 0, 0, 0, 0)

    def test_deadline_arguments_and_entry(self):
        class IntSubclass(int):
            pass

        for deadline, code in ((True, "DEADLINE_ARGUMENT"), (1.0, "DEADLINE_ARGUMENT"),
                               (IntSubclass(200), "DEADLINE_ARGUMENT"),
                               (60000000101, "DEADLINE_ARGUMENT"),
                               (100, "DEADLINE"), (99, "DEADLINE")):
            with self.subTest(deadline=deadline), patch.object(core, "_monotonic_ns", return_value=100):
                result = core.decode_payload(b"{}", deadline_ns=deadline)
                self.assertEqual(result.error, core.DecodeError(code, 0, ()))
                self.metrics(result, 2, 0, 0, 0, 0, 0, 0)
        with patch.object(core, "_monotonic_ns", return_value=100):
            self.assertIsInstance(core.decode_payload(b"{}", deadline_ns=60000000100), core.Decoded)

    def test_deadline_before_root_and_key_reservation(self):
        for clocks, raw, location, expected in (
                ((100, 200), b"{}", (), (2, 0, 0, 0, 0, 0, 0)),
                ((100, 100, 200), b'{"x":0}', (0,), (7, 1, 1, 0, 1, 0, 0))):
            with self.subTest(raw=raw), patch.object(core, "_monotonic_ns", side_effect=clocks):
                result = core.decode_payload(raw, deadline_ns=200)
                self.assertEqual(result.error, core.DecodeError("DEADLINE", expected[1], location))
                self.metrics(result, *expected)

    def test_deadline_before_value_and_success_return(self):
        with patch.object(core, "_monotonic_ns", side_effect=(100, 100, 100, 200)):
            result = core.decode_payload(b'{"x":0}', deadline_ns=200)
        self.assertEqual(result.error, core.DecodeError("DEADLINE", 5, (0,), (1, 4)))
        self.metrics(result, 7, 5, 1, 1, 1, 1, 0)
        with patch.object(core, "_monotonic_ns", side_effect=(100, 100, 200)):
            result = core.decode_payload(b"{}", deadline_ns=200)
        self.assertEqual(result.error, core.DecodeError("DEADLINE", 2, ()))
        self.metrics(result, 2, 2, 1, 0, 1, 0, 0)

    def test_deadline_byte_interval_and_atomic_string_checkpoint(self):
        raw = b'{"x":"' + b"a" * 4096 + b'"}'
        with patch.object(core, "_monotonic_ns", side_effect=(100, 100, 100, 100, 200)):
            result = core.decode_payload(raw, deadline_ns=200)
        self.assertEqual(result.error, core.DecodeError("DEADLINE", 4100, (0,), (1, 4)))
        self.metrics(result, len(raw), 4100, 2, 1, 1, 4094, 0)

    def test_deadline_does_not_split_utf8_or_surrogate_pair(self):
        for atom in ("😀".encode("utf-8"), b"\\uD83D\\uDE00"):
            raw = b'{"x":"' + b"a" * 4092 + atom + b'"}'
            with self.subTest(atom=atom), patch.object(core, "_monotonic_ns", side_effect=(100, 100, 100, 100, 200)):
                result = core.decode_payload(raw, deadline_ns=200)
            self.assertEqual(result.error, core.DecodeError("DEADLINE", 4098, (0,), (1, 4)))
            self.metrics(result, len(raw), 4098, 2, 1, 1, 4092, 0)

    def test_repeated_calls_preserve_absolute_deadline(self):
        with patch.object(core, "_monotonic_ns", return_value=100):
            first = core.decode_payload(b"{}", deadline_ns=200)
        with patch.object(core, "_monotonic_ns", return_value=200):
            second = core.decode_payload(b"{}", deadline_ns=200)
        self.assertIsInstance(first, core.Decoded)
        self.assertEqual(second.error, core.DecodeError("DEADLINE", 0, ()))
        self.metrics(second, 2, 0, 0, 0, 0, 0, 0)

    def test_root_profile_and_no_reservations(self):
        for raw, code in ((b"", "ROOT_TYPE"), (b"[]", "ROOT_TYPE"),
                          (b"null", "ROOT_TYPE"), (b'"x"', "ROOT_TYPE"),
                          (b" {}", "WHITESPACE"), (b"\xef\xbb\xbf{}", "BOM")):
            with self.subTest(raw=raw):
                result = self.refused(raw, code, 0)
                self.metrics(result, len(raw), 0, 0, 0, 0, 0, 0)

    def test_compact_whitespace_and_trailing_bytes(self):
        cases = ((b"{ }", 1, (0,), None), (b'{"x" :0}', 4, (0,), (1, 4)),
                 (b'{"x": 0}', 5, (0,), (1, 4)), (b'{"x":0 }', 6, (), None),
                 (b'{"x":[0, 1]}', 8, (0, 1), None), (b"{}\n", 2, (), None))
        for raw, offset, location, span in cases:
            with self.subTest(raw=raw):
                result = self.refused(raw, "WHITESPACE", offset, location, span)
                self.assertEqual(result.metrics.consumed_bytes, offset)
        for raw in (b"{}{}", b"{}x", b"{}\xef\xbb\xbf"):
            self.refused(raw, "TRAILING_DATA", 2)
        self.assertEqual(self.accepted(b'{"x":" \t"}'.replace(b"\t", b"\\t")).value["x"], " \t")

    def test_syntax_slots_separators_and_partial_literals(self):
        cases = ((b"{", 1, (0,), None, 1, 0),
                 (b'{"x"}', 4, (0,), (1, 4), 1, 1),
                 (b'{"x":}', 5, (0,), (1, 4), 1, 1),
                 (b'{"x":truX}', 8, (0,), (1, 4), 2, 1),
                 (b'{"x":trueX}', 9, (0,), (1, 4), 2, 1),
                 (b'{"x":0,}', 7, (1,), None, 2, 1),
                 (b'{"x":[0,]}', 8, (0, 1), None, 3, 1),
                 (b'{"x":[0}', 7, (0,), None, 3, 1))
        for raw, offset, location, span, nodes, names in cases:
            with self.subTest(raw=raw):
                result = self.refused(raw, "SYNTAX", offset, location, span)
                self.assertEqual(result.metrics.consumed_bytes, offset)
                self.assertEqual((result.metrics.nodes, result.metrics.names), (nodes, names))
        for raw in (b"{/*x*/}", b"{'x':0}", b'{"x":NaN}', b'{"x":Infinity}', b'{"x":+1}'):
            with self.subTest(raw=raw):
                self.assertIsInstance(self.decode(raw), core.Refused)

    def test_string_scalars_and_all_simple_escapes(self):
        raw = b'{"x":"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0000\\uFeFf' + "é😀".encode("utf-8") + b'"}'
        result = self.accepted(raw)
        self.assertEqual(result.value["x"], '"\\/\b\f\n\r\t\x00\ufeffé😀')
        self.assertEqual(result.metrics.max_string_utf16_units, 13)
        self.assertEqual(self.accepted(b'{"x":"\\uD83D\\uDe00"}').value["x"], "😀")

    def test_raw_utf8_invalid_atom_no_consumption(self):
        bad_atoms = (b"\xc0\x80", b"\xc2", b"\xc2A", b"\xe0\x80\x80",
                     b"\xed\xa0\x80", b"\xf0\x80\x80\x80", b"\xf4\x90\x80\x80",
                     b"\xf5\x80\x80\x80", b"\x80", b"\xe2\x82")
        for atom in bad_atoms:
            for prefix, location, span, nodes, names in ((b'{"x":"a', (0,), (1, 4), 2, 1),
                                                        (b'{"a', (0,), (1, 3), 1, 1)):
                raw = prefix + atom
                with self.subTest(atom=atom, prefix=prefix):
                    result = self.refused(raw, "UTF8", len(prefix), location, span)
                    self.metrics(result, len(raw), len(prefix), nodes, names, 1, 1, 0)

    def test_canonical_utf8_scalar_edges_and_escaped_aliases(self):
        for scalar, escape in (("\x7f", b"\\u007f"), ("\x80", b"\\u0080"),
                               ("\u07ff", b"\\u07ff"), ("\u0800", b"\\u0800"),
                               ("\ud7ff", b"\\ud7ff"), ("\ue000", b"\\ue000"),
                               ("\U00010000", b"\\ud800\\udc00"),
                               ("\U0010ffff", b"\\udbff\\udfff")):
            for atom in (scalar.encode("utf-8"), escape):
                with self.subTest(scalar=escape, atom=atom):
                    result = self.accepted(b'{"x":"' + atom + b'"}')
                    self.assertEqual(result.value["x"], scalar)
                    self.assertEqual(result.metrics.max_string_utf16_units,
                                     2 if len(scalar.encode("utf-8")) == 4 else 1)

    def test_escape_and_surrogate_atoms_no_partial_commit(self):
        cases = ((b"\\q", "STRING_ESCAPE"), (b"\\", "STRING_ESCAPE"),
                 (b"\\u00X0", "STRING_ESCAPE"), (b"\\u123", "STRING_ESCAPE"),
                 (b"\\uD800", "SURROGATE"), (b"\\uDC00", "SURROGATE"),
                 (b"\\uD800\\u0041", "SURROGATE"), (b"\\uD800x", "SURROGATE"))
        for atom, code in cases:
            raw = b'{"x":"a' + atom
            with self.subTest(atom=atom):
                result = self.refused(raw, code, 7, (0,), (1, 4))
                self.metrics(result, len(raw), 7, 2, 1, 1, 1, 0)
        result = self.refused(b'{"x":"a\x00"}', "STRING_CONTROL", 7, (0,), (1, 4))
        self.metrics(result, 10, 7, 2, 1, 1, 1, 0)
        result = self.refused(b'{"a\\q":0}', "STRING_ESCAPE", 3, (0,), (1, 3))
        self.metrics(result, 9, 3, 1, 1, 1, 1, 0)
        self.refused(b'{"x":"a', "SYNTAX", 7, (0,), (1, 4))

    def test_duplicate_decoded_aliases_every_depth(self):
        for raw, offset, end, location, nodes, names in (
                (b'{"x":0,"\\u0078":1}', 7, 15, (1,), 2, 2),
                (b'{"x":[{"a":0,"a":1}]}', 13, 16, (0, 0, 1), 4, 3),
                (b'{"\\u0000":0,"\\u0000":1}', 12, 20, (1,), 2, 2),
                ('{"😀":0,"\\uD83D\\uDE00":1}'.encode("utf-8"), 10, 24, (1,), 2, 2)):
            with self.subTest(raw=raw):
                result = self.refused(raw, "DUPLICATE_NAME", offset, location, (offset, end))
                self.assertEqual((result.metrics.consumed_bytes, result.metrics.nodes,
                                  result.metrics.names), (end, nodes, names))
        distinct = self.accepted('{"é":0,"e\\u0301":1,"A":2,"a":3}'.encode("utf-8"))
        self.assertEqual(list(distinct.value), ["é", "é", "A", "a"])

    def test_string_utf16_boundaries_and_rejected_atom_width(self):
        self.assertEqual(self.accepted(b'{"x":"' + b"a" * 4096 + b'"}').metrics.max_string_utf16_units, 4096)
        raw = b'{"x":"' + b"a" * 4097 + b'"}'
        result = self.refused(raw, "STRING_UNITS", 5, (0,), (1, 4),
                              ("string_utf16_units", 4096, 4097))
        self.metrics(result, len(raw), 4102, 2, 1, 1, 4096, 0)
        supplementary = "😀".encode("utf-8")
        self.assertEqual(self.accepted(b'{"x":"' + supplementary * 2048 + b'"}').metrics.max_string_utf16_units, 4096)
        for prefix, atom, observed in ((supplementary * 2048, b"a", 4097),
                                       (b"a" * 4095, supplementary, 4097),
                                       (b"a" * 4096, b"\\uD83D\\uDE00", 4098)):
            raw = b'{"x":"' + prefix + atom + b'"}'
            result = self.refused(raw, "STRING_UNITS", 5, (0,), (1, 4),
                                  ("string_utf16_units", 4096, observed))
            self.assertEqual(result.metrics.consumed_bytes, 6 + len(prefix))
        raw = b'{"' + b"a" * 4097 + b'":0}'
        result = self.refused(raw, "STRING_UNITS", 1, (0,), (1, 4098),
                              ("string_utf16_units", 4096, 4097))
        self.metrics(result, len(raw), 4098, 1, 1, 1, 4096, 0)

    def test_name_string_boundary_and_lexical_validation_precedes_units(self):
        raw = b'{"' + b"a" * 4096 + b'":0}'
        self.metrics(self.accepted(raw), 4102, 4102, 2, 1, 1, 4096, 1)
        supplementary = "😀".encode("utf-8")
        raw = b'{"' + supplementary * 2048 + b'":0}'
        result = self.accepted(raw)
        self.assertEqual(result.metrics.max_string_utf16_units, 4096)
        raw = b'{"' + supplementary * 2048 + b'a":0}'
        result = self.refused(raw, "STRING_UNITS", 1, (0,), (1, 8194),
                              ("string_utf16_units", 4096, 4097))
        self.metrics(result, len(raw), 8194, 1, 1, 1, 4096, 0)
        raw = b'{"x":"' + b"a" * 4096 + b"\xc0\x80"
        result = self.refused(raw, "UTF8", 4102, (0,), (1, 4))
        self.metrics(result, len(raw), 4102, 2, 1, 1, 4096, 0)

    def test_numbers_inert_lexical_forms(self):
        for lexeme in (b"0", b"-0", b"-0.0", b"1.25E+03", b"1e999999",
                       b"-1E-999999", b"9" * 64):
            raw = b'{"x":' + lexeme + b"}"
            with self.subTest(lexeme=lexeme):
                result = self.accepted(raw)
                self.assertEqual(result.value["x"], core.JsonNumber(lexeme.decode("ascii"), 5, 5 + len(lexeme)))
                self.assertEqual(result.metrics.max_number_bytes, len(lexeme))
        self.assertEqual(self.accepted(b'{"x":"NaN Infinity"}').value["x"], "NaN Infinity")

    def test_lexical_limit_huge_exponent_remains_inert(self):
        lexeme = b"7E+" + b"8" * 61
        self.assertEqual(len(lexeme), 64)
        raw = b'{"v":' + lexeme + b"}"
        self.assertEqual(len(raw), 70)
        result = self.accepted(raw)
        self.assertEqual(result.value, {"v": core.JsonNumber("7E+" + "8" * 61, 5, 69)})
        self.assertNotIsInstance(result.value["v"], (int, float, bool))
        self.metrics(result, 70, 70, 2, 1, 1, 1, 64)

    def test_number_grammar_prefixes_and_bound_precedence(self):
        for lexeme, prefix in ((b"-", 1), (b"01", 1), (b"1.", 2), (b"1e", 2),
                               (b"1e+", 3), (b"1e-X", 3), (b"1X", 1),
                               (b"1\xd9\xa1", 1), (b"1.2.3", 3)):
            raw = b'{"x":' + lexeme + b"}"
            with self.subTest(lexeme=lexeme):
                result = self.refused(raw, "NUMBER_GRAMMAR", 5 + prefix, (0,), (1, 4))
                self.metrics(result, len(raw), 5 + prefix, 2, 1, 1, 1, prefix)
        raw = b'{"x":' + b"9" * 65 + b"}"
        result = self.refused(raw, "NUMBER_BYTES", 5, (0,), (1, 4), ("number_bytes", 64, 65))
        self.metrics(result, 71, 69, 2, 1, 1, 1, 64)
        raw = b'{"x":' + b"9" * 64 + b"X}"
        result = self.refused(raw, "NUMBER_GRAMMAR", 69, (0,), (1, 4))
        self.metrics(result, 71, 69, 2, 1, 1, 1, 64)

    def test_exact_payload_byte_boundary_legal_content(self):
        lengths = [4096] * 127 + [3705]
        raw = b'{"x":[' + b",".join(b'"' + b"a" * size + b'"' for size in lengths) + b"]}"
        self.assertEqual(len(raw), 524288)
        result = self.accepted(raw)
        self.metrics(result, 524288, 524288, 130, 1, 2, 4096, 0)
        oversized = raw[:-3] + b'a" ]}'.replace(b" ", b"")
        self.assertEqual(len(oversized), 524289)
        result = self.refused(oversized, "INPUT_BYTES", 0, bound=("input_bytes", 524288, 524289))
        self.metrics(result, 524289, 0, 0, 0, 0, 0, 0)

    def test_value_node_boundary_root_counts(self):
        raw = b'{"x":[' + b",".join(b"0" for _ in range(65534)) + b"]}"
        result = self.accepted(raw)
        self.metrics(result, 131075, 131075, 65536, 1, 2, 1, 1)
        oversized = raw[:-2] + b",0]}"
        result = self.refused(oversized, "NODES", 131074, (0, 65534),
                              bound=("nodes", 65536, 65537))
        self.metrics(result, 131077, 131074, 65536, 1, 2, 1, 1)

    def test_active_depth_boundary_and_atomic_failed_node(self):
        raw = b'{"x":' + b"[" * 63 + b"0" + b"]" * 63 + b"}"
        result = self.accepted(raw)
        self.metrics(result, 133, 133, 65, 1, 64, 1, 1)
        raw = b'{"x":' + b"[" * 64 + b"0" + b"]" * 64 + b"}"
        result = self.refused(raw, "DEPTH", 68, (0,) * 64,
                              bound=("container_depth", 64, 65))
        self.metrics(result, 135, 68, 64, 1, 64, 1, 0)

    def test_object_member_boundary_before_key_decode(self):
        members = [b'"k' + str(index).encode("ascii") + b'":0' for index in range(64)]
        raw = b"{" + b",".join(members) + b"}"
        result = self.accepted(raw)
        self.metrics(result, len(raw), len(raw), 65, 64, 1, 3, 1)
        raw = raw[:-1] + b',"\\q":0}'
        offset = raw.index(b'"\\q"')
        result = self.refused(raw, "OBJECT_NAMES", offset, (64,),
                              bound=("object_names", 64, 65))
        self.metrics(result, len(raw), offset, 65, 64, 1, 3, 1)

    def test_object_names_reset_per_object_and_depth_is_active(self):
        members = [b'"k' + str(index).encode("ascii") + b'":0' for index in range(64)]
        object_bytes = b"{" + b",".join(members) + b"}"
        raw = b'{"x":[' + object_bytes + b"," + object_bytes + b"]}"
        result = self.accepted(raw)
        self.metrics(result, len(raw), len(raw), 132, 129, 3, 3, 1)
        self.assertIsNot(result.value["x"][0], result.value["x"][1])

    def test_incomplete_tokens_keep_admitted_reservations(self):
        for raw, location, span, expected in (
                (b'{"', (0,), (1, 2), (2, 2, 1, 1, 1, 0, 0)),
                (b'{"x":"', (0,), (1, 4), (6, 6, 2, 1, 1, 1, 0)),
                (b'{"x":[', (0, 0), None, (6, 6, 2, 1, 2, 1, 0)),
                (b'{"x":{', (0, 0), None, (6, 6, 2, 1, 2, 1, 0))):
            with self.subTest(raw=raw):
                result = self.refused(raw, "SYNTAX", len(raw), location, span)
                self.metrics(result, *expected)

    def test_private_global_name_guard_and_atomic_tie_order(self):
        scanner = core._Scanner(b'"x"', 1000000000)
        frame = core._Frame({}, ())
        scanner.names = 65535
        with patch.object(core, "_monotonic_ns", return_value=100):
            scanner.reserve_name(frame)
            self.assertEqual((scanner.names, frame.ordinal), (65536, 1))
            with self.assertRaises(core._Stop) as stopped:
                scanner.reserve_name(frame)
        self.assertEqual(stopped.exception.error, core.DecodeError("NAMES", 0, (), None,
                                                                  "names", 65536, 65537))
        self.assertEqual((scanner.names, frame.ordinal), (65536, 1))
        frame.ordinal = 64
        with patch.object(core, "_monotonic_ns", return_value=100):
            with self.assertRaises(core._Stop) as stopped:
                scanner.reserve_name(frame)
        self.assertEqual(stopped.exception.error.code, "OBJECT_NAMES")
        self.assertEqual(stopped.exception.error, core.DecodeError("OBJECT_NAMES", 0, (), None,
                                                                  "object_names", 64, 65))
        self.assertEqual(scanner.metrics(), core.DecodeMetrics(3, 0, 0, 65536, 0, 0, 0))
        self.assertEqual((scanner.names, frame.ordinal), (65536, 64))
        scanner.nodes = 65536
        scanner.stack = [core._Frame([], ()) for _ in range(64)]
        stack_before = tuple(scanner.stack)
        with patch.object(core, "_monotonic_ns", return_value=100):
            with self.assertRaises(core._Stop) as stopped:
                scanner.reserve_value(True)
        self.assertEqual(stopped.exception.error.code, "NODES")
        self.assertEqual(stopped.exception.error, core.DecodeError("NODES", 0, (), None,
                                                                  "nodes", 65536, 65537))
        self.assertEqual(scanner.metrics(), core.DecodeMetrics(3, 0, 65536, 65536, 0, 0, 0))
        self.assertEqual((scanner.nodes, scanner.depth), (65536, 0))
        self.assertEqual(len(scanner.stack), 64)
        self.assertTrue(all(after is before for after, before in zip(scanner.stack, stack_before)))

    def test_nested_locations_do_not_retain_outer_name_span(self):
        self.refused(b'{"outer":[{"inner":}]}', "SYNTAX", 19, (0, 0, 0), (11, 18))
        self.refused(b'{"outer":[0 X]}', "WHITESPACE", 11, (0,), None)
        self.refused(b'{"outer":{"x":0 X}}', "WHITESPACE", 15, (0,), None)
        self.refused(b'{"outer":{"x":0,}}', "SYNTAX", 16, (0, 1), None)

    def test_no_io_no_payload_echo_and_unexpected_exceptions_propagate(self):
        with patch("builtins.open", side_effect=AssertionError("I/O")):
            self.accepted(b'{"x":0}')
        raw = b'{"private_secret":0,"private_secret":1}'
        result = self.decode(raw)
        self.assertNotIn("private_secret", repr(result.error))
        for error in (MemoryError("allocation"), RuntimeError("implementation")):
            with self.subTest(type=type(error).__name__), patch.object(core._Scanner, "run", side_effect=error):
                with self.assertRaises(type(error)):
                    self.decode(b"{}")


if __name__ == "__main__":
    unittest.main()
