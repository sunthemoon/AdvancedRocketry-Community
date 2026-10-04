"""Pure inventory restart parsing checks, without host/world/process execution."""
import contextlib
import hashlib
import io
import unittest

import run_v180_classic_inventory_smoke as fixture


CONFIGURED = (
    b"#configured\ngamemode=adventure\nforce-gamemode=false\n"
    b"view-distance=2\nsimulation-distance=3\nunknown=value\n"
)
NATIVE_REWRITE = (
    b"!native comment\r\nunknown=value\r\nsimulation-distance=3\r\n"
    b"view-distance=2\r\nforce-gamemode=false\r\ngamemode=adventure\r\n"
)
SECOND_REWRITE = b"#second native store\n" + CONFIGURED


class PropertiesParserTest(unittest.TestCase):
    def refuse(self, raw):
        with self.assertRaises(fixture.FixtureInputError):
            fixture.parse_properties(raw)

    def test_literal_unicode_and_unknown_entries_are_preserved(self):
        raw = "unknown=\u03b1\u4e2d\U0001f600\nkey=value\n".encode("utf-8")
        self.assertEqual({"unknown": "\u03b1\u4e2d\U0001f600", "key": "value"},
                         fixture.parse_properties(raw))

    def test_lf_crlf_and_final_line_without_delimiter(self):
        for raw in (b"k=v\n", b"k=v\r\n", b"k=v"):
            with self.subTest(raw=raw):
                self.assertEqual({"k": "v"}, fixture.parse_properties(raw))

    def test_comments_and_empty_lines_have_no_logical_entries(self):
        self.assertEqual({"k": "v"}, fixture.parse_properties(b"\n#x=1\n!x=2\r\nk=v\n"))
        self.assertEqual({}, fixture.parse_properties(b"#only a comment\n"))

    def test_ascii_space_only_physical_lines_are_blank(self):
        for raw in (b" ", b"   \n", b" \r\n  \n", b" \nk=v\n  ", b"   \r\nk=v\r\n "):
            with self.subTest(raw=raw):
                expected = {"k": "v"} if b"k=v" in raw else {}
                self.assertEqual(expected, fixture.parse_properties(raw))

    def test_other_whitespace_and_indented_comments_are_not_blank(self):
        for text in ("\t", "\f", "\u0085", "\u00a0", "\u2003", "\u2028", "\u3000",
                     " \t ", " \u00a0 ", " #comment", " !comment", "  k=v", "k= v"):
            with self.subTest(text=text):
                self.refuse(text.encode("utf-8"))
        # Only entire ASCII-space physical lines are ignored; data is not trimmed.
        self.assertEqual({"k": "v  "}, fixture.parse_properties(b"  \nk=v  \n "))

    def test_first_unescaped_equals_and_native_escape_allowlist(self):
        raw = br"key\=\:\ \#\!=value=\:\#\!\ \\tail\t\n\r\f"
        self.assertEqual({"key=: #!": "value=:#! \\tail\t\n\r\f"}, fixture.parse_properties(raw))

    def test_empty_value_is_valid_and_empty_key_is_not(self):
        self.assertEqual({"k": ""}, fixture.parse_properties(b"k="))
        self.refuse(b"=v")

    def test_literal_backslash_u_is_not_unicode_escape(self):
        self.assertEqual({"k": r"\u0000"}, fixture.parse_properties(br"k=\\u0000"))
        self.refuse(br"k=\u0000")

    def test_all_c0_del_and_c1_controls_refuse_in_keys_values_and_comments(self):
        controls = list(range(32)) + list(range(127, 160))
        # LF and CR delimiters are tested separately; an embedded CR is not CRLF.
        for codepoint in controls:
            char = chr(codepoint).encode("utf-8")
            for before, after in ((b"key", b"=v"), (b"k=v", b"x"), (b"#comment", b"x"),
                                  (b"!comment", b"x")):
                if codepoint == 10:
                    continue
                with self.subTest(codepoint=codepoint, before=before):
                    self.refuse(before + char + after)

    def test_lf_is_a_physical_separator_not_a_literal_value_control(self):
        self.assertEqual({"k": "v", "x": "y"}, fixture.parse_properties(b"k=v\nx=y"))
        self.refuse(b"k=v\nx")

    def test_escaped_controls_remain_valid_decoded_values_and_keys(self):
        self.assertEqual({"key\t\n\r\f": "\t\n\r\f"},
                         fixture.parse_properties(br"key\t\n\r\f=\t\n\r\f"))

    def test_malformed_utf8_and_all_bom_prefixes_refuse(self):
        for raw in (b"k=\xff", b"k=\xc0\x80", b"k=\xed\xa0\x80", b"k=\xe2\x82",
                    b"\xef\xbb\xbfk=v", b"\xff\xfek\x00=\x00v\x00"):
            with self.subTest(raw=raw):
                self.refuse(raw)

    def test_lone_cr_and_unsupported_physical_continuation_refuse(self):
        for raw in (b"k=v\r", b"k=v\rx=y", b"k=v\\\nx=y", b"k=v\\\r\nx=y"):
            with self.subTest(raw=raw):
                self.refuse(raw)

    def test_unknown_and_trailing_escapes_refuse(self):
        for raw in (br"k=\q", br"k\q=v", b"k=v\\", b"k\\=v"):
            with self.subTest(raw=raw):
                self.refuse(raw)

    def test_duplicate_decoded_keys_include_native_escape_aliases(self):
        for raw in (b"k=v\nk=v", br"k#=v" + b"\n" + br"k\#=v",
                    br"k:=v" + b"\n" + br"k\:=v"):
            with self.subTest(raw=raw):
                self.refuse(raw)
        self.assertEqual({"k#": "v"}, fixture.parse_properties(br"k\#=v"))

    def test_no_whitespace_colon_or_default_separator_fallback(self):
        for raw in (b"k v", b"k:v", b"k:v=z", b"key =v", b" key=v", b"k= v",
                    "k\u00a0=v".encode(), "k=\u00a0v".encode()):
            with self.subTest(raw=raw):
                self.refuse(raw)
        self.assertEqual({"k:": " v"}, fixture.parse_properties(br"k\:=\ v"))
        self.assertEqual({"k": "v : = # ! x"}, fixture.parse_properties(b"k=v : = # ! x"))

    def test_raw_bytes_limit_is_exact_and_not_a_character_limit(self):
        self.assertEqual("a" * 16382, fixture.parse_properties(b"k=" + b"a" * 16382)["k"])
        self.refuse(b"k=" + b"a" * 16383)
        exact = ("k=" + "\u03b1" * 8191).encode()
        self.assertEqual(16384, len(exact))
        self.assertEqual("\u03b1" * 8191, fixture.parse_properties(exact)["k"])
        self.refuse(exact + "\u03b1".encode())

    def test_pair_and_string_node_boundary(self):
        raw = "\n".join(f"k{i}=v" for i in range(1023)).encode()
        self.assertLess(len(raw), fixture.MAX_PROPERTIES_BYTES)
        values = fixture.parse_properties(raw)
        self.assertEqual(1023, len(values))
        self.assertEqual(2047, 1 + 2 * len(values))
        self.assertLessEqual(1 + 2 * len(values), fixture.MAX_PROPERTIES_NODES)
        self.refuse(raw + b"\nk1023=v")

    def test_non_bytes_empty_and_mutable_input_refuse(self):
        for raw in (b"", None, "k=v", bytearray(b"k=v"), memoryview(b"k=v")):
            with self.subTest(kind=type(raw)):
                self.refuse(raw)

    def test_returned_maps_do_not_alias_subsequent_observations(self):
        values = fixture.parse_properties(b"k=v")
        values["k"] = "changed"
        self.assertEqual({"k": "v"}, fixture.parse_properties(b"k=v"))

    def test_errors_do_not_echo_secret_values(self):
        with self.assertRaises(fixture.FixtureInputError) as error:
            fixture.parse_properties(b"secret=password\nsecret=other")
        self.assertEqual("PROPERTIES_DUPLICATE", str(error.exception))


class PropertiesBindingTest(unittest.TestCase):
    def setUp(self):
        self.configured = fixture.PropertiesObservation(CONFIGURED)
        self.rewritten = fixture.PropertiesObservation(NATIVE_REWRITE)
        self.second = fixture.PropertiesObservation(SECOND_REWRITE)

    def test_observation_owns_immutable_raw_and_map_with_actual_raw_hash(self):
        self.assertEqual(hashlib.sha256(CONFIGURED).hexdigest(), self.configured.sha256)
        self.assertEqual(CONFIGURED, self.configured.raw)
        with self.assertRaises(TypeError):
            self.configured.values["unknown"] = "changed"
        self.assertNotIn("unknown", repr(self.configured))
        self.assertNotIn("value", repr(self.configured))

    def test_seed_requires_exact_raw_not_only_whole_map(self):
        fixture.require_boot_binding("seed", self.configured, self.configured, self.configured.sha256)
        fixture.require_logical_equality(self.configured, self.rewritten)
        self.assertNotEqual(self.configured.sha256, self.rewritten.sha256)
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_boot_binding("seed", self.configured, self.rewritten, self.rewritten.sha256)

    def test_blank_ascii_spaces_change_raw_hash_not_logical_map(self):
        raw = b"   \n" + CONFIGURED + b" \r\n  "
        observed = fixture.PropertiesObservation(raw)
        self.assertEqual(raw, observed.raw)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), observed.sha256)
        self.assertNotEqual(self.configured.sha256, observed.sha256)
        fixture.require_logical_equality(self.configured, observed)
        fixture.require_live_binding(self.configured, observed, observed.sha256)
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_boot_binding("seed", self.configured, observed, observed.sha256)
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_stopped_binding(self.configured, observed)

    def test_reload_boot_binds_seed_stopped_not_initial_snapshot(self):
        fixture.require_boot_binding("reload", self.configured, self.rewritten, self.rewritten.sha256,
                                     seed_stopped=self.rewritten)
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_boot_binding("reload", self.configured, self.configured, self.configured.sha256,
                                         seed_stopped=self.rewritten)

    def test_missing_or_cross_phase_stopped_binding_refuses(self):
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_boot_binding("reload", self.configured, self.configured, self.configured.sha256)
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_boot_binding("seed", self.configured, self.configured, self.configured.sha256,
                                         seed_stopped=self.configured)

    def test_launch_hash_must_be_exact_lowercase_actual_hash(self):
        for value in (None, 0, "", "a" * 64, self.configured.sha256.upper(),
                      self.configured.sha256 + "\n", self.configured.sha256[:-1]):
            with self.subTest(value=value), self.assertRaises(fixture.FixtureInputError):
                fixture.require_boot_binding("seed", self.configured, self.configured, value)

    def test_native_raw_rewrites_must_preserve_whole_map_and_hook_hash(self):
        fixture.require_live_binding(self.configured, self.rewritten, self.rewritten.sha256)
        fixture.require_live_binding(self.configured, self.second, self.second.sha256)
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_live_binding(self.configured, self.rewritten, self.configured.sha256)

    def test_unknown_key_add_delete_or_value_change_refuses(self):
        changes = (CONFIGURED + b"new=value\n", CONFIGURED.replace(b"unknown=value\n", b""),
                   CONFIGURED.replace(b"unknown=value", b"unknown=changed"))
        for raw in changes:
            observed = fixture.PropertiesObservation(raw)
            with self.subTest(raw=raw), self.assertRaises(fixture.FixtureInputError):
                fixture.require_live_binding(self.configured, observed, observed.sha256)

    def test_host_settings_cannot_be_defaulted_or_coerced(self):
        changes = (CONFIGURED.replace(b"gamemode=adventure", b"gamemode=creative"),
                   CONFIGURED.replace(b"view-distance=2", b"view-distance=02"),
                   CONFIGURED.replace(b"force-gamemode=false\n", b""),
                   CONFIGURED.replace(b"simulation-distance=3", b"simulation-distance=4"))
        for raw in changes:
            with self.subTest(raw=raw), self.assertRaises(fixture.FixtureInputError):
                fixture.require_host_settings(fixture.PropertiesObservation(raw))

    def test_same_host_stopped_file_requires_exact_live_raw(self):
        fixture.require_stopped_binding(self.rewritten, self.rewritten)
        with self.assertRaises(fixture.FixtureInputError):
            fixture.require_stopped_binding(self.rewritten, self.configured)

    def test_unvalidated_observations_and_unknown_phases_refuse(self):
        for value in (None, {}, CONFIGURED):
            with self.subTest(value=value), self.assertRaises(fixture.FixtureInputError):
                fixture.require_logical_equality(self.configured, value)
        for phase in (None, "SEED", "checkpoint", "seed ", 3):
            with self.subTest(phase=phase), self.assertRaises(fixture.FixtureInputError):
                fixture.require_boot_binding(phase, self.configured, self.configured, self.configured.sha256)


class ForcedSetupPrimitivesTest(unittest.TestCase):
    def test_seed_is_literal_and_reload_has_no_setup_commands(self):
        self.assertEqual(("forceload add -80 -80 95 95",), fixture.commands_for_phase("seed"))
        self.assertEqual((), fixture.commands_for_phase("reload"))

    def test_unknown_or_untyped_phase_never_selects_commands(self):
        for phase in ("Seed", "reload ", "other", "seed\nstop", None, True):
            with self.subTest(phase=phase), self.assertRaises(fixture.FixtureInputError):
                fixture.commands_for_phase(phase)

    def test_single_and_multiple_all_fixed_pairs_have_no_numeric_count_claim(self):
        for x in range(-5, 6):
            for z in range(-5, 6):
                for form, text in (
                    ("single", f"Marked chunk [{x}, {z}] in minecraft:overworld to be force loaded"),
                    ("multiple", f"Marked [{x}, {z}] chunks in minecraft:overworld from [-5, -5] to [5, 5] to be force loaded"),
                ):
                    with self.subTest(x=x, z=z, form=form):
                        observed = fixture.parse_seed_acknowledgement(text)
                        self.assertEqual(form, observed.form)
                        self.assertEqual((x, z), observed.last_changed_chunk)
                        self.assertFalse(hasattr(observed, "changed_count"))
        self.assertEqual((), fixture.commands_for_phase("reload"))

    def test_noncanonical_and_out_of_range_pairs_refuse(self):
        for pair in ("[+1, 0]", "[01, 0]", "[-0, 0]", "[-05, 0]", "[6, 0]", "[0, -6]",
                     "[0,0]", "[0,  0]", "[0.0, 0]", "[0, +0]", "[\u0660, 0]"):
            with self.subTest(pair=pair), self.assertRaises(fixture.FixtureInputError):
                fixture.parse_seed_acknowledgement(f"Marked chunk {pair} in minecraft:overworld to be force loaded")

    def test_numeric_counter_foreign_range_dimension_and_refusal_refuse(self):
        valid = "Marked [5, 5] chunks in minecraft:overworld from [-5, -5] to [5, 5] to be force loaded"
        for text in (valid.replace("[5, 5] chunks", "121 chunks"), valid.replace("[5, 5] chunks", "0 chunks"),
                     valid.replace("minecraft:overworld", "minecraft:the_nether"),
                     valid.replace("from [-5, -5]", "from [-4, -5]"),
                     valid.replace("to [5, 5]", "to [6, 5]"), "No chunks were marked for force loading"):
            with self.subTest(text=text), self.assertRaises(fixture.FixtureInputError):
                fixture.parse_seed_acknowledgement(text)

    def test_payload_parser_does_not_strip_log_origin_trailing_or_combined_lines(self):
        valid = "Marked chunk [0, 0] in minecraft:overworld to be force loaded"
        for text in (None, b"payload", valid + "\n", " " + valid, valid + " ", valid + "\n" + valid,
                     "[Server thread/INFO] [minecraft/MinecraftServer]: " + valid):
            with self.subTest(text=text), self.assertRaises(fixture.FixtureInputError):
                fixture.parse_seed_acknowledgement(text)

    def test_direct_entry_point_refuses_incomplete_native_driver(self):
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            code = fixture.main()
        self.assertEqual(2, code)
        self.assertIn("driver are not implemented", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
