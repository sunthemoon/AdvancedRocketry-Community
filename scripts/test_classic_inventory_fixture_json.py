"""Pure byte, grammar, ownership and pre-admission JSON boundary checks."""
from dataclasses import FrozenInstanceError
import hashlib
import math
import sys
import unittest
from unittest import mock

import classic_inventory_fixture_json as fixture


# Independent literal contract table, not imported from implementation limits.
CAPS = (
    (fixture.JsonRole.EXTERNAL, 16_384, 12, 2_048, None),
    (fixture.JsonRole.REPORT, 16_384, 8, 2_048, None),
    (fixture.JsonRole.ADVANCEMENT, 262_144, 16, 32_768, 2_048),
    (fixture.JsonRole.STATS, 131_072, 16, 16_384, 2_048),
    (fixture.JsonRole.USERCACHE, 262_144, 8, 16_384, 1_000),
)


def _base(role):
    if role is fixture.JsonRole.STATS:
        return b'{"stats":{}}'
    return b"[]" if role is fixture.JsonRole.USERCACHE else b"{}"


def _nested(role, depth, leaf=b"0"):
    if role is fixture.JsonRole.USERCACHE:
        return b"[" * (depth - 1) + leaf + b"]" * (depth - 1)
    prefix = b'{"stats":{},"x":' if role is fixture.JsonRole.STATS else b'{"x":'
    return prefix + b"[" * (depth - 2) + leaf + b"]" * (depth - 2) + b"}"


def _nodes(role, count, last=b"0"):
    if role is fixture.JsonRole.USERCACHE:
        prefix, suffix, overhead = b"[[", b"]]", 2
    elif role is fixture.JsonRole.STATS:
        prefix, suffix, overhead = b'{"stats":{},"x":[', b"]}", 5
    else:
        prefix, suffix, overhead = b'{"x":[', b"]}", 3
    return prefix + b",".join([b"0"] * (count - overhead - 1) + [last]) + suffix


def _members(count, prefix="k"):
    return b",".join(('"' + prefix + str(index) + '":null').encode("ascii")
                     for index in range(count))


class JsonParserTest(unittest.TestCase):
    def observe(self, raw, role=fixture.JsonRole.EXTERNAL):
        return fixture.parse_fixture_json(raw, role)

    def refuse(self, raw, code=None, role=fixture.JsonRole.EXTERNAL):
        with self.assertRaises(fixture.FixtureJsonError) as failure:
            self.observe(raw, role)
        error = failure.exception
        if code is not None:
            self.assertEqual(code, error.code)
        self.assertIs(error.role, role if type(role) is fixture.JsonRole else None)
        self.assertEqual((error.code, error.role.value if error.role else None), error.args)
        self.assertTrue(error.__suppress_context__)
        return error

    def test_exact_role_type_and_invalid_context(self):
        for role in ("external", None, 0, True, object()):
            with self.subTest(role=type(role)):
                self.refuse(b"{}", "JSON_ROLE", role)

    def test_exact_immutable_bytes_type(self):
        class BytesSubclass(bytes):
            pass
        for raw in (None, "{}", bytearray(b"{}"), memoryview(b"{}"), BytesSubclass(b"{}")):
            with self.subTest(raw=type(raw)):
                self.refuse(raw, "JSON_BYTES")

    def test_all_byte_caps_at_equality(self):
        for role, size, _, _, _ in CAPS:
            raw = _base(role)
            raw += b" " * (size - len(raw))
            with self.subTest(role=role):
                result = self.observe(raw, role)
                self.assertEqual(size, len(result.raw))
                self.assertEqual(raw, result.raw)
                self.assertEqual(hashlib.sha256(raw).hexdigest(), result.sha256)

    def test_all_byte_caps_plus_one_before_decode_or_parser(self):
        with mock.patch.object(fixture, "_Parser", side_effect=AssertionError("PARSER_CALLED")):
            for role, size, _, _, _ in CAPS:
                with self.subTest(role=role):
                    self.refuse(b"x" * size + b"\xff", "JSON_BYTES", role)
                    self.refuse(b"", "JSON_BYTES", role)

    def test_byte_limits_measure_utf8_not_character_count(self):
        raw = ('{"x":"' + "\u03b1" * 8_188 + '"}').encode("utf-8")
        self.assertEqual(16_384, len(raw))
        self.observe(raw)
        self.refuse(raw[:-2] + "\u03b1".encode("utf-8") + raw[-2:], "JSON_BYTES")

    def test_all_depth_caps_at_equality(self):
        for role, _, depth, _, _ in CAPS:
            with self.subTest(role=role):
                self.assertEqual(depth, self.observe(_nested(role, depth), role).depth)

    def test_all_depth_caps_plus_one_before_invalid_child_decode(self):
        for role, _, depth, _, _ in CAPS:
            with self.subTest(role=role):
                self.refuse(_nested(role, depth + 1, b'"\\qbad"'), "JSON_DEPTH", role)

    def test_all_node_caps_at_equality(self):
        for role, _, _, nodes, _ in CAPS:
            with self.subTest(role=role):
                result = self.observe(_nodes(role, nodes), role)
                self.assertEqual(nodes, result.nodes)

    def test_all_node_caps_plus_one_before_invalid_child_decode(self):
        for role, _, _, nodes, _ in CAPS:
            with self.subTest(role=role):
                self.refuse(_nodes(role, nodes + 1, b'"\\qbad"'), "JSON_NODES", role)

    def test_object_keys_count_as_nodes_and_depth(self):
        result = self.observe(b'{"a":{},"b":[],"c":null,"d":false}')
        self.assertEqual(9, result.nodes)
        self.assertEqual(2, result.depth)

    def test_empty_root_depth_one_node_one(self):
        for raw, role in ((b"{}", fixture.JsonRole.EXTERNAL),
                          (b"[]", fixture.JsonRole.USERCACHE)):
            result = self.observe(raw, role)
            self.assertEqual((1, 1), (result.depth, result.nodes))

    def test_key_depth_refuses_before_key_decoder(self):
        raw = b'{"x":' + b"[" * 10 + b'{"\\qbad":0}' + b"]" * 10 + b"}"
        self.refuse(raw, "JSON_DEPTH")

    def test_node_limit_refuses_before_excess_key_decoder(self):
        raw = b'{"x":[' + b",".join([b"0"] * 2_044) + b'],"\\qbad":0}'
        # First pair contributes 2047 nodes; the next key is admitted as node2048.
        self.refuse(raw, "JSON_STRING")
        raw = b'{"x":[' + b",".join([b"0"] * 2_045) + b'],"\\qbad":0}'
        self.refuse(raw, "JSON_NODES")

    def test_overflow_sentinel_is_invalid_without_budget_exhaustion(self):
        self.refuse(b'{"x":"\\qbad"}', "JSON_STRING")
        self.refuse(b'{"\\qbad":0}', "JSON_STRING")
        self.assertEqual("\bad", self.observe(b'{"x":"\\bad"}').value.pairs[0][1])

    def test_only_json_whitespace_surrounds_single_document(self):
        self.observe(b" \t\r\n{}\n\r\t ")
        for prefix in (b"\v", b"\f", b"\xc2\xa0", b"\x7f"):
            with self.subTest(prefix=prefix):
                self.refuse(prefix + b"{}")
                self.refuse(b"{}" + prefix, "JSON_SYNTAX")

    def test_strict_utf8_and_bom_refusal(self):
        for raw in (b"\xef\xbb\xbf{}", b'{"x":"\xed\xa0\x80"}',
                    b'{"x":"\xc0\xaf"}', b"\xff", b'{"x":"\xf4\x90\x80\x80"}'):
            with self.subTest(raw=raw):
                self.refuse(raw, "JSON_ENCODING")

    def test_wrong_root_type_for_every_role(self):
        for role, _, _, _, _ in CAPS:
            for raw in ((b"{}", b"null", b'"x"', b"1", b"true")
                        if role is fixture.JsonRole.USERCACHE else
                        (b"[]", b"null", b'"x"', b"1", b"true")):
                with self.subTest(role=role, raw=raw):
                    self.refuse(raw, "JSON_ROOT", role)

    def test_invalid_json_grammar(self):
        for raw in (b"{}{}", b"{}true", b"{}#x", b"{/*comment*/}", b"{a:0}",
                    b"{'a':0}", b'{"a":0,}', b'{"a":[0,]}', b'{"a" 0}',
                    b'{"a":}', b'{"a":True}', b'{"a":undefined}', b'{"a":[}',
                    b'{"a":0 "b":0}', b'{"a":NaN}', b'{"a":Infinity}',
                    b'{"a":-Infinity}', b'{"a":\xef\xbb\xbf0}'):
            with self.subTest(raw=raw):
                self.refuse(raw)

    def test_incomplete_documents(self):
        for raw in (b"{", b'{"x"', b'{"x":', b'{"x":[', b'{"x":{', b'{"x":false'):
            with self.subTest(raw=raw):
                self.refuse(raw)

    def test_literal_c0_refused_in_values_and_keys(self):
        for code in range(32):
            for raw in (b'{"x":"' + bytes([code]) + b'"}',
                        b'{"' + bytes([code]) + b'":0}'):
                with self.subTest(code=code, raw=raw):
                    self.refuse(raw, "JSON_STRING")

    def test_escaped_controls_del_c1_noncharacters_are_structural_strings(self):
        raw = b'{"x":"\\u0000\\b\\t\\n\\f\\r\\u001f\\u007f\\u0080\\u009f\\ufdd0\\ufffe"}'
        value = self.observe(raw).value.pairs[0][1]
        self.assertEqual("\x00\b\t\n\f\r\x1f\x7f\x80\x9f\ufdd0\ufffe", value)
        for char in ("\x7f", "\x80", "\x9f", "\ufdd0", "\uffff"):
            raw = ('{"' + char + '":"' + char + '"}').encode("utf-8")
            self.assertEqual(((char, char),), self.observe(raw).value.pairs)

    def test_valid_escapes_and_surrogate_pairs(self):
        raw = b'{"\\ud83d\\ude00":"\\\"\\\\\\/\\ud800\\udc00\\udbff\\udfff"}'
        self.assertEqual((("\U0001f600", '"\\/\U00010000\U0010ffff'),),
                         self.observe(raw).value.pairs)

    def test_isolated_reversed_or_mispaired_surrogates(self):
        for token in (b"\\ud800", b"\\udfff", b"\\udc00\\ud800", b"\\ud800\\ud800",
                      b"\\ud800\\u0000", b"\\ud800x", b"\\ud800\\n"):
            for raw in (b'{"x":"' + token + b'"}', b'{"' + token + b'":0}'):
                with self.subTest(token=token, raw=raw):
                    self.refuse(raw, "JSON_STRING")

    def test_invalid_string_escapes_and_unterminated_strings(self):
        for token in (b"\\a", b"\\v", b"\\x41", b"\\U0001f600", b"\\u12", b"\\u123z"):
            with self.subTest(token=token):
                self.refuse(b'{"x":"' + token + b'"}', "JSON_STRING")
        self.refuse(b'{"x":"unterminated', "JSON_STRING")
        self.refuse(b'{"x":"\\', "JSON_STRING")

    def test_duplicate_keys_after_escape_decoding_and_surrogate_scalar_conversion(self):
        for raw in (b'{"a":0,"\\u0061":1}', b'{"\\u0000":0,"\\u0000":1}',
                    '{"\U0001f600":0,"\\ud83d\\ude00":1}'.encode("utf-8"),
                    b'{"x":{"z":0,"z":1}}'):
            with self.subTest(raw=raw):
                self.refuse(raw, "JSON_DUPLICATE")

    def test_case_normalization_and_unicode_normalization_not_applied(self):
        raw = '{"A":0,"a":1,"\u00e9":2,"e\u0301":3,"a/b":4,"a\\\\b":5}'.encode("utf-8")
        result = self.observe(raw)
        self.assertEqual(("A", "a", "\u00e9", "e\u0301", "a/b", "a\\b"),
                         tuple(key for key, _ in result.value.pairs))

    def test_key_order_and_unknown_values_preserved(self):
        result = self.observe(b'{"future":{"z":[null,false,{},[]]},"a":true}')
        self.assertEqual(("future", "a"), tuple(key for key, _ in result.value.pairs))
        nested = result.value.pairs[0][1].pairs[0][1]
        self.assertEqual((None, False, fixture.JsonObject(()), ()), nested)
        self.assertIs(result.value.pairs[1][1], True)

    def test_deep_immutability_and_array_object_distinction(self):
        result = self.observe(b'{"x":[{"n":1},[]]}')
        with self.assertRaises(FrozenInstanceError):
            result.value = None
        with self.assertRaises(FrozenInstanceError):
            result.value.pairs = ()
        array = result.value.pairs[0][1]
        with self.assertRaises(TypeError):
            array[0] = None
        with self.assertRaises(FrozenInstanceError):
            array[0].pairs[0][1].lexeme = "2"
        self.assertIs(type(array), tuple)
        self.assertIs(type(array[0]), fixture.JsonObject)

    def test_raw_bytes_hash_not_logical_canonicalization(self):
        first = self.observe(b'{"x":1}')
        second = self.observe(b' { "x" : 1 }\n')
        self.assertEqual(first.value, second.value)
        self.assertNotEqual(first.raw, second.raw)
        self.assertNotEqual(first.sha256, second.sha256)

    def test_integer_lexemes_do_not_cross_global_conversion_guard(self):
        before = sys.get_int_max_str_digits()
        lexeme = "9" * 6_000
        number = self.observe(b'{"x":' + lexeme.encode("ascii") + b"}").value.pairs[0][1]
        self.assertEqual(lexeme, number.lexeme)
        self.assertTrue(number.integer_grammar)
        self.assertIsNone(number.binary64)
        self.assertEqual(before, sys.get_int_max_str_digits())

    def test_integer_token_can_fill_the_raw_role_ceiling(self):
        before = sys.get_int_max_str_digits()
        for role, size, _, _, _ in CAPS:
            if role is fixture.JsonRole.USERCACHE:
                prefix, suffix = b"[", b"]"
            elif role is fixture.JsonRole.STATS:
                prefix, suffix = b'{"stats":{},"x":', b"}"
            else:
                prefix, suffix = b'{"x":', b"}"
            token = b"9" * (size - len(prefix) - len(suffix))
            result = self.observe(prefix + token + suffix, role)
            number = result.value[0] if role is fixture.JsonRole.USERCACHE else result.value.pairs[-1][1]
            self.assertEqual(size, len(result.raw))
            self.assertEqual(token.decode("ascii"), number.lexeme)
            self.assertTrue(number.integer_grammar)
            self.assertIsNone(number.binary64)
        self.assertEqual(before, sys.get_int_max_str_digits())

    def test_integer_grammar_including_negative_zero_has_no_projection(self):
        for token in ("0", "-0", "1", "-1", "9223372036854775808"):
            with self.subTest(token=token):
                number = self.observe(('{"x":' + token + '}').encode("ascii")).value.pairs[0][1]
                self.assertEqual(token, number.lexeme)
                self.assertTrue(number.integer_grammar)
                self.assertIsNone(number.binary64)

    def test_fraction_exponent_projection_and_lexeme_fidelity(self):
        for token, value in (("1.2300E+4", 12_300.0), ("0e999999", 0.0),
                             ("1e-10000", 0.0), ("1.5", 1.5), ("1E0", 1.0)):
            with self.subTest(token=token):
                number = self.observe(('{"x":' + token + '}').encode("ascii")).value.pairs[0][1]
                self.assertEqual(token, number.lexeme)
                self.assertFalse(number.integer_grammar)
                self.assertEqual(value, number.binary64)
                self.assertTrue(math.isfinite(number.binary64))

    def test_fraction_signed_zero_and_underflow_sign(self):
        for token in ("-0.0", "-0e0", "-1e-10000"):
            with self.subTest(token=token):
                number = self.observe(('{"x":' + token + '}').encode("ascii")).value.pairs[0][1]
                self.assertEqual(token, number.lexeme)
                self.assertEqual(-1.0, math.copysign(1.0, number.binary64))

    def test_fraction_overflow_refused(self):
        for token in (b"1e309", b"-1e309", b"9.9e999999"):
            with self.subTest(token=token):
                self.refuse(b'{"x":' + token + b"}", "JSON_NUMBER")

    def test_projection_is_lossy_not_exact_field_authority(self):
        result = self.observe(b'{"x":9007199254740993.0}')
        number = result.value.pairs[0][1]
        self.assertEqual("9007199254740993.0", number.lexeme)
        self.assertEqual(9007199254740992.0, number.binary64)
        self.assertFalse(number.integer_grammar)

    def test_huge_exponent_is_lexically_bounded_and_never_integer_converted(self):
        exponent = b"9" * 6_000
        number = self.observe(b'{"x":1e-' + exponent + b"}").value.pairs[0][1]
        self.assertEqual("1e-" + exponent.decode("ascii"), number.lexeme)
        self.assertEqual(0.0, number.binary64)
        self.refuse(b'{"x":1e' + exponent + b"}", "JSON_NUMBER")

    def test_nonstandard_number_grammar_refused(self):
        for token in (b"01", b"-01", b"+1", b".5", b"1.", b"1e", b"1E+", b"--1",
                      b"0x10", b"1_000", b"1e 2", b"1\xd9\xa1", b"\xd9\xa1"):
            with self.subTest(token=token):
                self.refuse(b'{"x":' + token + b"}")

    def test_booleans_null_and_numbers_are_not_coerced(self):
        result = self.observe(b'{"a":true,"b":false,"c":null,"d":1.0,"e":1}')
        values = tuple(value for _, value in result.value.pairs)
        self.assertIs(values[0], True)
        self.assertIs(values[1], False)
        self.assertIsNone(values[2])
        self.assertIs(type(values[3]), fixture.JsonNumber)
        self.assertIs(type(values[4]), fixture.JsonNumber)
        self.assertFalse(values[3].integer_grammar)
        self.assertTrue(values[4].integer_grammar)

    def test_advancement_entry_equality_unknown_and_reserved_fields(self):
        raw = b'{"DataVersion":null,' + _members(2_047) + b',"unknown":[]}'
        result = self.observe(raw, fixture.JsonRole.ADVANCEMENT)
        self.assertEqual(2_048, result.entries)
        self.assertEqual(4_099, result.nodes)

    def test_advancement_entry_plus_one_before_overflow_value(self):
        raw = b"{" + _members(2_048) + b',"future":"\\qbad"}'
        self.refuse(raw, "JSON_ENTRIES", fixture.JsonRole.ADVANCEMENT)

    def test_advancement_only_exact_decoded_dataversion_excluded(self):
        result = self.observe(b'{"\\u0044ataVersion":{},"dataversion":null,"DataVersion2":false}',
                              fixture.JsonRole.ADVANCEMENT)
        self.assertEqual(2, result.entries)
        self.assertEqual(7, result.nodes)

    def test_stats_entry_sum_unknown_categories_at_equality(self):
        raw = b'{"unknown_root":null,"stats":{"future":{' + _members(1_024) + b'},"other":{'
        raw += _members(1_024) + b"}}}"
        result = self.observe(raw, fixture.JsonRole.STATS)
        self.assertEqual(2_048, result.entries)
        self.assertEqual(4_105, result.nodes)

    def test_stats_entry_plus_one_before_overflow_value(self):
        raw = b'{"stats":{"one":{' + _members(2_048) + b'},"two":{"future":"\\qbad"}}}'
        self.refuse(raw, "JSON_ENTRIES", fixture.JsonRole.STATS)

    def test_stats_missing_or_nonobject_framing_refuses_without_defaults(self):
        for raw in (b"{}", b'{"Stats":{}}', b'{"stats":null}', b'{"stats":[]}',
                    b'{"stats":{"future":1}}', b'{"stats":{"future":[]}}'):
            with self.subTest(raw=raw):
                self.refuse(raw, "JSON_ENTRIES", fixture.JsonRole.STATS)

    def test_stats_decoded_key_and_nested_stat_payloads_count_entries_only_once(self):
        result = self.observe(b'{"\\u0073tats":{"x":{"unknown":{"nested":1}}}}',
                              fixture.JsonRole.STATS)
        self.assertEqual(1, result.entries)
        self.assertEqual(9, result.nodes)
        self.assertEqual(5, result.depth)

    def test_usercache_entry_equality_includes_malformed_future_elements(self):
        raw = b"[" + b",".join([b"null", b"true", b"0", b'"future"', b"[]", b"{}"]
                               + [b"null"] * 994) + b"]"
        result = self.observe(raw, fixture.JsonRole.USERCACHE)
        self.assertEqual(1_000, result.entries)
        self.assertEqual(1_001, result.nodes)

    def test_usercache_entry_plus_one_before_overflow_value(self):
        raw = b"[" + b",".join([b"null"] * 1_000) + b',"\\qbad"]'
        self.refuse(raw, "JSON_ENTRIES", fixture.JsonRole.USERCACHE)

    def test_report_and_external_have_no_entry_schema_observation(self):
        for role in (fixture.JsonRole.REPORT, fixture.JsonRole.EXTERNAL):
            result = self.observe(b'{"stats":null,"future":[]}', role)
            self.assertIsNone(result.entries)

    def test_fixed_non_echo_error_surface(self):
        secret = b"PRIVATE_INPUT_PAYLOAD"
        cases = ((secret, "JSON_ROLE", "caller:" + secret.decode("ascii")),
                 (secret * 1_000, "JSON_BYTES", fixture.JsonRole.EXTERNAL),
                 (b'{"' + secret + b'":"\xff"}', "JSON_ENCODING", fixture.JsonRole.EXTERNAL),
                 (b'{"' + secret + b'":"\\qbad"}', "JSON_STRING", fixture.JsonRole.EXTERNAL),
                 (b'{"' + secret + b'":0,"' + secret + b'":1}', "JSON_DUPLICATE", fixture.JsonRole.EXTERNAL))
        for raw, code, role in cases:
            with self.subTest(code=code):
                error = self.refuse(raw, code, role)
                for value in (str(error), repr(error), repr(error.args), repr(error.code), repr(error.role)):
                    self.assertNotIn(secret.decode("ascii"), value)
                self.assertIsNone(error.__cause__)
                self.assertIsNone(error.__context__)

    def test_invalid_error_constructor_context_is_not_echoed(self):
        error = fixture.FixtureJsonError("untrusted", "untrusted")
        self.assertEqual(("JSON_SYNTAX", None), error.args)

    def test_no_import_or_parse_file_process_or_global_policy_seams(self):
        with mock.patch("builtins.open", side_effect=AssertionError("OPEN_CALLED")):
            result = self.observe(b'{"a":[1,true,null]}')
        self.assertEqual(6, result.nodes)
        self.assertFalse(hasattr(result, "authority"))
        self.assertFalse(hasattr(fixture, "main"))


if __name__ == "__main__":
    unittest.main()
