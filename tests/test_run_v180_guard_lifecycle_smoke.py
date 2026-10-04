"""Fail-closed oracles for the fixed native guard lifecycle exercise."""

import copy
import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

from scripts import run_v180_guard_lifecycle_smoke as guard
from tests.test_run_v180_tank_smoke import fixture


def oversized_fixture():
    chunk = fixture(True)
    chunk["block_entities"][2][guard.tanks.ROOT]["extension"] = {
        "part0": [0] * 4096, "part1": [0] * 4096}
    return chunk


def info(text):
    return "[Server thread/INFO] [minecraft/MinecraftServer]: [Server] " + text


def errors():
    return ["[Server thread/ERROR] [net.minecraftforge.eventbus.EventBus/EVENTBUS]: "
            "Exception caught during firing event: " + guard.tanks.SAVE_REFUSAL,
            "[Server thread/ERROR] [minecraft/ChunkMap]: Failed to save chunk [11, 11]"]


class GuardFixtureTest(unittest.TestCase):
    def test_exact_four_roots_and_original_not_mutated(self):
        chunk = oversized_fixture()
        before = copy.deepcopy(chunk)
        self.assertEqual(set(guard.check_fixture(chunk)), set(guard.tanks.NAMES[1:]))
        self.assertEqual(chunk, before)

    def test_wrong_coordinate_duplicate_missing_or_changed_root_rejected(self):
        def altered_id(chunk):
            chunk["block_entities"][2]["id"] = "minecraft:chest"

        def altered_root(chunk):
            chunk["block_entities"][2][guard.tanks.ROOT]["extension"]["part1"][0] = 1

        for mutate in (lambda chunk: chunk.update(xPos=12),
                       lambda chunk: chunk["block_entities"].append(chunk["block_entities"][0]),
                       lambda chunk: chunk["block_entities"].pop(), altered_id, altered_root):
            with self.subTest(mutate=mutate):
                chunk = oversized_fixture()
                mutate(chunk)
                with self.assertRaises(RuntimeError):
                    guard.check_fixture(chunk)

    def test_palette_alone_cannot_pass_fixed_cell_checks(self):
        chunk = oversized_fixture()
        chunk["sections"][0]["block_states"]["data"] = [0] * 256
        with self.assertRaisesRegex(RuntimeError, "exact tank carrier"):
            guard.check_fixture(chunk)

    def test_record_requires_compressed_byte_equality_before_decode(self):
        with patch.object(guard.regions, "_decode_chunk") as decode:
            with self.assertRaisesRegex(RuntimeError, "terrain record changed"):
                guard.check_record(b"after", b"before")
            decode.assert_not_called()

    def test_equal_record_still_requires_semantic_fixture(self):
        with patch.object(guard.regions, "_decode_chunk", return_value=oversized_fixture()):
            self.assertEqual(guard.check_record(b"same", b"same")["bytes"], 4)
        with patch.object(guard.regions, "_decode_chunk", return_value=fixture(True)):
            with self.assertRaisesRegex(RuntimeError, "root differs"):
                guard.check_record(b"same", b"same")


class GuardMarkerTest(unittest.TestCase):
    def test_only_authoritative_exact_info_markers(self):
        expression = guard.marker("ARCE_GUARD_NOT_LOADED")
        self.assertIsNotNone(expression.search(info("ARCE_GUARD_NOT_LOADED")))
        for line in ("ARCE_GUARD_NOT_LOADED", info("ARCE_GUARD_NOT_LOADED_EXTRA"),
                     info("ARCE_GUARD_NOT_LOADED").replace("/INFO]", "/ERROR]"),
                     info("ARCE_GUARD_NOT_LOADED").replace("minecraft/MinecraftServer", "example/Other"),
                     "echo " + info("ARCE_GUARD_NOT_LOADED")):
            self.assertIsNone(expression.search(line))

    def test_literal_marker_metacharacters_not_patterns(self):
        self.assertIsNotNone(guard.marker("TEST.1").search(info("TEST.1")))
        self.assertIsNone(guard.marker("TEST.1").search(info("TESTx1")))

    def test_barrier_ignores_old_matching_line(self):
        class Process:
            lines = [info("END")]

            def command(self, command):
                self.lines.append(info(command.removeprefix("say ")))

            def wait_for(self, pattern, timeout, start_at):
                self.start = start_at
                assert any(pattern.search(line) for line in self.lines[start_at:])

        process, commands = Process(), []
        self.assertEqual(guard.say_barrier(process, commands, "END"), 1)
        self.assertEqual(process.start, 1)
        self.assertEqual(commands, ["say END"])

    def test_false_loaded_predicate_requires_fresh_native_observation(self):
        class Process:
            def __init__(self):
                self.lines = [info("ARCE_GUARD_NOT_LOADED")]
                self.probes = 0

            def command(self, command):
                if command.startswith("execute"):
                    self.probes += 1
                    if self.probes == 2:
                        self.lines.append(info("ARCE_GUARD_NOT_LOADED"))
                else:
                    self.lines.append(info(command.removeprefix("say ")))

            def wait_for(self, pattern, timeout, start_at):
                assert timeout <= 60
                assert any(pattern.search(line) for line in self.lines[start_at:])

        process, commands = Process(), []
        with patch.object(guard.time, "monotonic", return_value=0), patch.object(guard.time, "sleep"):
            guard.wait_chunk(process, commands, False)
        self.assertEqual(process.probes, 2)
        self.assertTrue(commands[0].startswith("execute unless loaded "))

    def test_total_deadline_not_reset_after_probe(self):
        class Process:
            lines = []

            def command(self, command):
                if command.startswith("say "):
                    self.lines.append(info(command.removeprefix("say ")))

            def wait_for(self, pattern, timeout, start_at):
                assert any(pattern.search(line) for line in self.lines[start_at:])

        with patch.object(guard.time, "monotonic", side_effect=[0, 0, 0, 60, 60, 60]), \
                patch.object(guard.time, "sleep"), self.assertRaises(guard.server.SmokeError):
            guard.wait_chunk(Process(), [], False)

    def test_observation_after_deadline_does_not_pass(self):
        class Process:
            lines = []

            def command(self, command):
                if command.startswith("execute"):
                    self.lines.append(info("ARCE_GUARD_LOADED"))
                else:
                    self.lines.append(info(command.removeprefix("say ")))

            def wait_for(self, pattern, timeout, start_at):
                assert any(pattern.search(line) for line in self.lines[start_at:])

        with patch.object(guard.time, "monotonic", side_effect=[0, 0, 0, 61]), \
                self.assertRaises(guard.server.SmokeError):
            guard.wait_chunk(Process(), [], True)


class GuardLoggerTest(unittest.TestCase):
    def test_actual_header_pairs_not_stack_trace_occurrences(self):
        result = guard.audit_refusal(errors() * 2 + [guard.tanks.SAVE_REFUSAL] * 3)
        self.assertEqual(result["event_bus_error_headers"], 2)
        self.assertEqual(result["chunk_map_error_headers"], 2)

    def test_missing_or_unpaired_headers_fail(self):
        for lines in (errors()[:1], errors()[1:], errors() + errors()[:1], []):
            with self.assertRaises(RuntimeError):
                guard.audit_refusal(lines)

    def test_unrelated_error_is_not_waived(self):
        with self.assertRaisesRegex(RuntimeError, "Unexpected packaged-server"):
            guard.audit_refusal(errors() + ["[Server thread/ERROR] [example/Other]: broken"])

    def test_known_phrase_cannot_waive_fatal_or_wrong_logger(self):
        fatal = "[Server thread/FATAL] [example/Other]: Exception caught during firing event: " + guard.tanks.SAVE_REFUSAL
        foreign = [line.replace("net.minecraftforge.eventbus.EventBus/EVENTBUS", "example/Other")
                   .replace("minecraft/ChunkMap", "example/Other") for line in errors()]
        for lines in (errors() + [fatal], foreign):
            with self.subTest(lines=lines), self.assertRaises(RuntimeError):
                guard.audit_refusal(lines)

    def test_native_abbreviated_logger_and_timestamp_are_supported(self):
        lines = ["[03:17:40] " + line.replace("net.minecraftforge.eventbus.EventBus", "ne.mi.ev.EventBus")
                 for line in errors()]
        result = guard.audit_refusal(lines)
        self.assertEqual(result["event_bus_error_headers"], 1)
        self.assertEqual(result["chunk_map_error_headers"], 1)


class NativeUnloadObservationTest(unittest.TestCase):
    LINE = "[Server thread/INFO] [advancedrocketrycommunity/]: ARCE_GUARD_UNLOAD_BEGIN chunk=11,11"

    def test_exact_info_logger_and_fixed_chunk_required(self):
        self.assertIsNotNone(guard.UNLOAD_BEGIN.search(self.LINE))
        for line in (info("ARCE_GUARD_UNLOAD_BEGIN chunk=11,11"),
                     self.LINE.replace("/INFO]", "/ERROR]"),
                     self.LINE.replace("advancedrocketrycommunity/", "example/Other"),
                     self.LINE.replace("11,11", "11,12"), "echo " + self.LINE,
                     self.LINE + " extra"):
            self.assertIsNone(guard.UNLOAD_BEGIN.search(line))

    def test_predicate_and_event_share_one_total_deadline(self):
        class Process:
            lines = [NativeUnloadObservationTest.LINE, NativeUnloadObservationTest.LINE]

            def command(self, command):
                self.command_sent = command

            def wait_for(self, pattern, timeout, start_at):
                self.remaining, self.start = timeout, start_at
                return 1

        process = Process()
        commands = []
        observed_predicates = []
        with patch.object(guard, "wait_chunk", side_effect=lambda proc, rows, loaded, timeout:
                          observed_predicates.append((proc, list(rows), loaded, timeout))), \
                patch.object(guard.time, "monotonic", side_effect=[0, 40, 40, 40.1]):
            self.assertEqual(guard.wait_unload_begin(process, commands, 1), self.LINE)
        self.assertEqual(observed_predicates, [(process, [], False, 60)])
        self.assertEqual(commands, ["arce-guard-state"])
        self.assertEqual(process.command_sent, "arce-guard-state")
        self.assertEqual((process.remaining, process.start), (20, 1))

    def test_old_marker_does_not_substitute_for_new_unload(self):
        class Process:
            lines = [NativeUnloadObservationTest.LINE]

            def command(self, command):
                pass

            def wait_for(self, pattern, timeout, start_at):
                if not any(pattern.search(line) for line in self.lines[start_at:]):
                    raise guard.server.SmokeError("No fresh native event")
                return 0

        with patch.object(guard, "wait_chunk"), patch.object(guard.time, "monotonic", return_value=0), \
                self.assertRaisesRegex(guard.server.SmokeError, "No fresh"):
            guard.wait_unload_begin(Process(), [], 1)

    def test_predicate_exhausting_deadline_does_not_get_second_wait(self):
        class Process:
            def wait_for(self, *args, **kwargs):
                raise AssertionError("A second observation budget was granted")

        with patch.object(guard, "wait_chunk"), \
                patch.object(guard.time, "monotonic", side_effect=[0, 60]), \
                self.assertRaisesRegex(guard.server.SmokeError, "original deadline"):
            guard.wait_unload_begin(Process(), [], 0)

    def test_late_event_does_not_pass(self):
        class Process:
            lines = [NativeUnloadObservationTest.LINE]

            def command(self, command):
                pass

            def wait_for(self, *args, **kwargs):
                return 0

        with patch.object(guard, "wait_chunk"), \
                patch.object(guard.time, "monotonic", side_effect=[0, 0, 0, 61]), \
                self.assertRaisesRegex(guard.server.SmokeError, "after its original deadline"):
            guard.wait_unload_begin(Process(), [], 0)


class ChunkStateDiagnosticTest(unittest.TestCase):
    LINE = ('[Server thread/INFO] [advancedrocketrycommunity/]: ARCE_GUARD_STATE '
            'chunk=11,11 no_save=false holder="null" truncated=false')

    def test_fresh_native_state_keeps_values_without_inventing_an_event(self):
        self.assertEqual(guard.chunk_state_observations([self.LINE, self.LINE], 1), [{
            "line": self.LINE, "no_save": False, "holder_text": "null", "truncated": False}])
        self.assertIsNone(guard.UNLOAD_BEGIN.search(self.LINE))

    def test_both_flags_and_escaped_text_are_preserved_as_data(self):
        line = self.LINE.replace("no_save=false", "no_save=true").replace(
            'holder="null"', r'holder="a\u000a\"\\"').replace("truncated=false", "truncated=true")
        value = guard.chunk_state_observations([line], 0)[0]
        self.assertTrue(value["no_save"])
        self.assertTrue(value["truncated"])
        self.assertEqual(value["holder_text"], r'a\u000a\"\\')

    def test_wrong_logger_thread_cell_flags_severity_and_control_text_are_ignored(self):
        for line in (self.LINE.replace("/INFO]", "/ERROR]"), self.LINE.replace("Server thread", "Worker thread"),
                     self.LINE.replace("advancedrocketrycommunity/", "example/Other"),
                     self.LINE.replace("11,11", "11,12"), self.LINE.replace("no_save=false", "no_save=unknown"),
                     self.LINE.replace('holder="null"', 'holder="line\nother"'),
                     self.LINE.replace('holder="null"', r'holder="\n"'), "echo " + self.LINE, self.LINE + " extra"):
            with self.subTest(line=line):
                self.assertEqual(guard.chunk_state_observations([line], 0), [])

    def test_holder_budget_is_encoded_bytes_not_escape_tokens(self):
        accepted = self.LINE.replace('holder="null"', 'holder="' + "x" * 256 + '"')
        rejected = self.LINE.replace('holder="null"', 'holder="' + "x" * 257 + '"')
        escaped = self.LINE.replace('holder="null"', 'holder="' + r'\u000a' * 43 + '"')
        self.assertEqual(len(guard.chunk_state_observations([accepted], 0)), 1)
        self.assertEqual(guard.chunk_state_observations([rejected, escaped], 0), [])

    def test_diagnostic_receipt_has_a_hard_report_count(self):
        self.assertEqual(len(guard.chunk_state_observations([self.LINE] * 300, 0)), 240)

    def test_state_dispatch_does_not_reset_the_unload_deadline(self):
        class Process:
            def command(self, command):
                self.command_sent = command

            def wait_for(self, *args, **kwargs):
                raise AssertionError("State dispatch cannot grant a second deadline")

        process, commands = Process(), []
        with patch.object(guard, "wait_chunk"), \
                patch.object(guard.time, "monotonic", side_effect=[0, 0, 60]), \
                self.assertRaisesRegex(guard.server.SmokeError, "original unload deadline"):
            guard.wait_unload_begin(process, commands, 0)
        self.assertEqual(commands, ["arce-guard-state"])
        self.assertEqual(process.command_sent, "arce-guard-state")


class CopiedSpawnSetupTest(unittest.TestCase):
    LINE = "[Server thread/INFO] [minecraft/MinecraftServer]: Set the world spawn point to 256, 74, -256 [0.0]"

    def test_exact_native_success_requires_logger_coordinates_and_angle(self):
        self.assertIsNotNone(guard.COPY_SPAWN_SUCCESS.search(self.LINE))
        for line in (self.LINE.replace("/INFO]", "/ERROR]"),
                     self.LINE.replace("minecraft/MinecraftServer", "example/Other"),
                     self.LINE.replace("-256", "-32"), self.LINE.replace("[0.0]", "[1.0]"),
                     "echo " + self.LINE, self.LINE + " extra"):
            self.assertIsNone(guard.COPY_SPAWN_SUCCESS.search(line))

    def test_fixed_copy_command_and_barrier_precede_success_check(self):
        class Process:
            lines = [CopiedSpawnSetupTest.LINE]

            def command(self, command):
                self.commands.append(command)

        process, commands = Process(), []
        process.commands = []

        def barrier(proc, rows, text, timeout):
            self.assertEqual(text, "ARCE_GUARD_COPY_SPAWN_END")
            self.assertEqual(rows, ["setworldspawn 256 74 -256 0"])
            self.assertGreater(timeout, 0)
            self.assertLessEqual(timeout, 60)
            proc.lines.append(self.LINE)
            proc.lines.append(info(text))

        with patch.object(guard, "say_barrier", side_effect=barrier):
            self.assertEqual(guard.relocate_copy_spawn(process, commands), self.LINE)
        self.assertEqual(process.commands, ["setworldspawn 256 74 -256 0"])

    def test_stale_absent_wrong_or_duplicate_success_does_not_pass(self):
        class Process:
            def command(self, command):
                pass

        for new in ([], [self.LINE.replace("-256", "-32")], [self.LINE, self.LINE]):
            process = Process()
            process.lines = [self.LINE]
            with self.subTest(new=new), \
                    patch.object(guard, "say_barrier", side_effect=lambda *args: process.lines.extend(new + [info(args[2])])), \
                    self.assertRaisesRegex(RuntimeError, "exactly once"):
                guard.relocate_copy_spawn(process, [])


SOURCE_FORCED = bytes.fromhex(
    "1f8b08000000000000ffe36260e0626049492c49e4616073cb2f4a4e4d61606060056201"
    "285680626e10feffffff3fa8b80418333370bb003587a5161567e6e73130f076320000"
    "b824068453000000")


def forced_blob(coords=((11, 11), (32, 32)), *, forced_type=12, version=3465,
                version_type=3, root_name="", extra_root=b"", extra_data=b"", trailing=b""):
    def name(value):
        raw = value.encode("ascii")
        return struct.pack(">H", len(raw)) + raw

    values = []
    for x, z in coords:
        value = (z & 0xffffffff) << 32 | x & 0xffffffff
        values.append(value - 2**64 if value >= 2**63 else value)
    array = struct.pack(">i", len(values))
    if forced_type == 12:
        array += b"".join(struct.pack(">q", value) for value in values)
    elif forced_type == 11:
        array += b"".join(struct.pack(">i", 0) for _ in values)
    else:
        array = b"\x04" + array + b"".join(struct.pack(">q", value) for value in values)
    data = bytes([forced_type]) + name("Forced") + array + extra_data + b"\0"
    version_tag = bytes([version_type]) + name("DataVersion") + struct.pack(
        ">i" if version_type == 3 else ">q", version)
    payload = b"\x0a" + name(root_name) + b"\x0a" + name("data") + data
    return gzip.compress(payload + version_tag + extra_root + b"\0" + trailing, mtime=0)


class ForcedMetadataTest(unittest.TestCase):
    def test_bootstrap_exact_pin_and_native_signed_five_mark_set(self):
        result = guard.check_forced_metadata(SOURCE_FORCED, True)
        self.assertEqual(result["bytes"], 79)
        self.assertEqual(result["sha256"], "c9b7030c645d2995ac9be13d08964eb98f1942b2170c34b3f893100daa6acc50")
        self.assertEqual(result["forced"], [[11, 11], [16, -2], [16, 16], [24, 24], [32, 32]])
        self.assertEqual(result["data_version"], 3465)
        self.assertEqual(result["tag_count"], 4)

    def test_retained_set_allows_native_array_order_not_original_five_marks(self):
        for coords in (((11, 11), (32, 32)), ((32, 32), (11, 11))):
            result = guard.check_forced_metadata(forced_blob(coords), False)
            self.assertEqual(result["forced"], [[11, 11], [32, 32]])
        with self.assertRaises(RuntimeError):
            guard.check_forced_metadata(SOURCE_FORCED, False)
        for phase in (0, 1, None, "retained"):
            with self.subTest(phase=phase), self.assertRaises(RuntimeError):
                guard.check_forced_metadata(forced_blob(), phase)
            with self.subTest(phase=phase), self.assertRaises(RuntimeError):
                guard.setup_copy_forced_marks(FixedSetupProcess(), [], phase)

    def test_bootstrap_equal_set_with_different_compressed_bytes_is_not_the_pinned_input(self):
        raw = forced_blob(((11, 11), (16, 16), (16, -2), (24, 24), (32, 32)))
        self.assertNotEqual(raw, SOURCE_FORCED)
        with self.assertRaisesRegex(RuntimeError, "pinned"):
            guard.check_forced_metadata(raw, True)

    def test_missing_duplicate_extra_and_arbitrary_marks_are_rejected(self):
        for coords in ((), ((11, 11),), ((11, 11), (11, 11)),
                       ((11, 11), (32, 32), (24, 24)), ((11, 11), (-123, 999))):
            with self.subTest(coords=coords), self.assertRaises(RuntimeError):
                guard.check_forced_metadata(forced_blob(coords), False)

    def test_native_type_unrelated_fields_and_version_are_not_normalized(self):
        mutations = ({"forced_type": 11}, {"forced_type": 9}, {"version_type": 4},
                     {"version": 3466}, {"root_name": "unexpected"},
                     {"extra_root": b"\x01\x00\x01x\x00"},
                     {"extra_data": b"\x01\x00\x01x\x00"})
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(RuntimeError):
                guard.check_forced_metadata(forced_blob(**mutation), False)

    def test_compressed_expanded_truncated_and_trailing_payloads_are_bounded(self):
        raw = forced_blob()
        deep = gzip.compress(b"\x0a\x00\x00" + b"\x0a\x00\x01x" * 17 + b"\0" * 18)
        for broken in (b"x" * 4097, gzip.compress(b"x" * 4097), raw[:-1], raw + b"extra",
                       raw + raw, forced_blob(trailing=b"extra"), b"not-gzip", deep):
            with self.subTest(length=len(broken)), self.assertRaises(RuntimeError):
                guard.check_forced_metadata(broken, False)

    def test_duplicate_native_field_and_negative_array_count_are_rejected(self):
        duplicate = b"\x0c\x00\x06Forced\x00\x00\x00\x00"
        raw = gzip.decompress(forced_blob())
        offset = raw.index(b"Forced") + 6
        altered = [gzip.compress(raw[:offset] + struct.pack(">i", length) + raw[offset + 4:])
                   for length in (-1, 4097)]
        for broken in (forced_blob(extra_data=duplicate), *altered):
            with self.assertRaises(RuntimeError):
                guard.check_forced_metadata(broken, False)

    def test_named_metadata_read_captures_exact_bounded_bytes_even_on_decode_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "data").mkdir()
            path, capture = root / "data/chunks.dat", root / "capture.dat"
            path.write_bytes(forced_blob())
            result = guard.read_forced_metadata(root, False, capture)
            self.assertEqual(capture.read_bytes(), path.read_bytes())
            self.assertEqual(result["forced"], [[11, 11], [32, 32]])
            path.write_bytes(b"bad")
            with self.assertRaises(RuntimeError):
                guard.read_forced_metadata(root, False, capture)
            self.assertEqual(capture.read_bytes(), b"bad")


class FixedSetupProcess:
    def __init__(self):
        self.lines, self.sent, self.waits = [], [], []
        self.responses = {
            guard.COPY_SPAWN_COMMAND: [CopiedSpawnSetupTest.LINE],
            "execute in minecraft:overworld run forceload query 176 176": [
                "[Server thread/INFO] [minecraft/MinecraftServer]: Chunk at [11, 11] in minecraft:overworld is marked for force loading"],
            "execute in minecraft:overworld run forceload query 512 512": [
                "[Server thread/INFO] [minecraft/MinecraftServer]: Chunk at [32, 32] in minecraft:overworld is marked for force loading"],
        }
        for command, chunk in (("256 256", "[16, 16]"), ("256 -32", "[16, -2]"),
                               ("384 384", "[24, 24]")):
            self.responses["execute in minecraft:overworld run forceload remove " + command] = [
                "[Server thread/INFO] [minecraft/MinecraftServer]: Unmarked chunk " + chunk
                + " in minecraft:overworld for force loading"]

    def command(self, command):
        self.sent.append(command)
        if command.startswith("say "):
            self.lines.append(info(command.removeprefix("say ")))
        else:
            self.lines.extend(self.responses[command])

    def wait_for(self, pattern, timeout, start_at):
        self.waits.append(timeout)
        for index in range(start_at, len(self.lines)):
            if pattern.search(self.lines[index]):
                return index
        raise guard.server.SmokeError("No fresh fixed setup barrier")


class FixedForcedSetupTest(unittest.TestCase):
    def test_exact_commands_protect_target_and_far_before_and_after_bootstrap(self):
        process, commands = FixedSetupProcess(), []
        process.responses = {command: ["[01:02:03] " + line for line in lines]
                             for command, lines in process.responses.items()}
        with patch.object(guard.time, "monotonic", return_value=0):
            result = guard.setup_copy_forced_marks(process, commands, True)
        self.assertEqual(commands, process.sent)
        self.assertEqual(commands, [
            "execute in minecraft:overworld run forceload query 176 176",
            "execute in minecraft:overworld run forceload query 512 512",
            "say ARCE_GUARD_FORCED_BEFORE_END", "setworldspawn 256 74 -256 0",
            "say ARCE_GUARD_COPY_SPAWN_END",
            "execute in minecraft:overworld run forceload remove 256 256",
            "execute in minecraft:overworld run forceload remove 256 -32",
            "execute in minecraft:overworld run forceload remove 384 384",
            "execute in minecraft:overworld run forceload query 176 176",
            "execute in minecraft:overworld run forceload query 512 512",
            "say ARCE_GUARD_FORCED_AFTER_END"])
        self.assertEqual(len(result["removed"]), 3)
        self.assertEqual(len(result["protected_before"]), 2)
        self.assertEqual(len(result["protected_after"]), 2)

    def test_restart_queries_both_marks_without_repeating_neighbor_removals(self):
        process, commands = FixedSetupProcess(), []
        with patch.object(guard.time, "monotonic", return_value=0):
            result = guard.setup_copy_forced_marks(process, commands, False)
        self.assertEqual(result["removed"], [])
        self.assertFalse(any("forceload remove" in command for command in commands))
        self.assertEqual(commands.count("execute in minecraft:overworld run forceload query 176 176"), 2)
        self.assertEqual(commands.count("execute in minecraft:overworld run forceload query 512 512"), 2)

    def test_stale_missing_wrong_and_duplicate_native_successes_refuse(self):
        target = "execute in minecraft:overworld run forceload remove 256 -32"
        native = FixedSetupProcess().responses[target][0]
        for fresh in ([], [native.replace("-2", "-3")], [native, native],
                      [native.replace("Unmarked", "Marked")],
                      ["[Server thread/INFO] [minecraft/MinecraftServer]: No chunks were removed from force loading"]):
            process = FixedSetupProcess()
            process.lines = [native]
            process.responses[target] = fresh
            with self.subTest(fresh=fresh), patch.object(guard.time, "monotonic", return_value=0), \
                    self.assertRaisesRegex(RuntimeError, "exactly once"):
                guard.setup_copy_forced_marks(process, [], True)

    def test_failed_membership_query_and_wrong_framing_are_not_success(self):
        target = "execute in minecraft:overworld run forceload query 512 512"
        native = FixedSetupProcess().responses[target][0]
        for line in (native.replace("is marked", "is not marked"), "echo " + native,
                     native.replace("Server thread", "Worker thread"), native.replace("/INFO]", "/ERROR]"),
                     native.replace("minecraft/MinecraftServer", "example/Other"),
                     native.replace("minecraft:overworld", "minecraft:the_nether"), native + " extra"):
            process = FixedSetupProcess()
            process.responses[target] = [line]
            with self.subTest(line=line), patch.object(guard.time, "monotonic", return_value=0), \
                    self.assertRaisesRegex(RuntimeError, "exactly once"):
                guard.setup_copy_forced_marks(process, [], True)
            self.assertFalse(any("forceload remove" in command for command in process.sent))

    def test_shared_deadline_is_not_reset_by_an_intermediate_barrier(self):
        process, clock = FixedSetupProcess(), [0]
        original_wait = process.wait_for

        def advance(pattern, timeout, start_at):
            result = original_wait(pattern, timeout, start_at)
            clock[0] += 20
            return result

        process.wait_for = advance
        with patch.object(guard.time, "monotonic", side_effect=lambda: clock[0]):
            guard.setup_copy_forced_marks(process, [], True)
        self.assertEqual(process.waits, [60, 40, 20])

    def test_feedback_after_barrier_does_not_acknowledge_prior_command(self):
        process = FixedSetupProcess()
        target = "execute in minecraft:overworld run forceload query 512 512"
        delayed = process.responses[target][0]
        process.responses[target] = []
        original_wait = process.wait_for

        def late_reply(pattern, timeout, start_at):
            result = original_wait(pattern, timeout, start_at)
            process.lines.append(delayed)
            return result

        process.wait_for = late_reply
        with patch.object(guard.time, "monotonic", return_value=0), \
                self.assertRaisesRegex(RuntimeError, "exactly once"):
            guard.setup_copy_forced_marks(process, [], True)
        self.assertFalse(any("forceload remove" in command for command in process.sent))

    def test_late_success_or_exhausted_dispatch_cannot_grant_another_wait(self):
        for exhausted_at_dispatch in (False, True):
            process, clock = FixedSetupProcess(), [0]
            original_wait, original_command = process.wait_for, process.command

            def late_wait(pattern, timeout, start_at):
                result = original_wait(pattern, timeout, start_at)
                clock[0] = 61
                return result

            def late_command(command):
                original_command(command)
                clock[0] = 60

            if exhausted_at_dispatch:
                process.command = late_command
            else:
                process.wait_for = late_wait
            with self.subTest(dispatch=exhausted_at_dispatch), \
                    patch.object(guard.time, "monotonic", side_effect=lambda: clock[0]), \
                    self.assertRaisesRegex(RuntimeError, "setup.*deadline"):
                guard.setup_copy_forced_marks(process, [], True)
            self.assertLessEqual(len(process.waits), 1)
            self.assertFalse(any("forceload remove" in command for command in process.sent))


class NativeForcedPhaseTest(unittest.TestCase):
    def test_bad_metadata_is_captured_and_refused_before_process_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / "server"
            (runtime / "world/data").mkdir(parents=True)
            raw = forced_blob(forced_type=11)
            (runtime / "world/data/chunks.dat").write_bytes(raw)
            evidence = root / "phase"
            with patch.object(guard.server, "CapturedProcess") as launch, self.assertRaises(RuntimeError):
                guard.native_cycle(runtime, evidence, ["not-launched"], b"unchanged", False)
            launch.assert_not_called()
            self.assertEqual((evidence / "forced-before.dat").read_bytes(), raw)
            self.assertEqual(json.loads((evidence / "receipt.json").read_bytes())["result"], "FAIL")
            self.assertEqual(json.loads((evidence / "commands.json").read_bytes()), [])

    def test_two_mocked_hosts_use_five_then_two_preflights_and_both_clean_stop_checks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / "server"
            (runtime / "world/data").mkdir(parents=True)
            saved = runtime / "world/data/chunks.dat"
            saved.write_bytes(SOURCE_FORCED)
            hosts = []

            class Process(FixedSetupProcess):
                def command(self, command):
                    if command in self.responses or command.startswith("say "):
                        super().command(command)
                    else:
                        self.sent.append(command)
                        if command.startswith("execute if block "):
                            self.lines.append(info(command.split("run say ", 1)[1]))
                        elif command == "save-all flush":
                            self.lines.extend(errors())

                def wait_for(self, pattern, timeout, start_at=0):
                    if pattern in (guard.server.READY_MARKER, guard.server.SAVE_MARKER):
                        return 0
                    return super().wait_for(pattern, timeout, start_at)

                def finish(self, timeout):
                    self.finish_timeout = timeout
                    saved.write_bytes(forced_blob())
                    return 0

                def abort(self):
                    raise AssertionError("Positive mocked host unexpectedly aborted")

            def launch(*args):
                process = Process()
                hosts.append(process)
                return process

            with patch.object(guard.server, "CapturedProcess", side_effect=launch), \
                    patch.object(guard, "wait_chunk"), \
                    patch.object(guard, "wait_unload_begin", return_value=NativeUnloadObservationTest.LINE), \
                    patch.object(guard, "saved_record", return_value=b"same"), \
                    patch.object(guard, "check_record", return_value={"bytes": 4}) as record, \
                    patch.object(guard.time, "monotonic", return_value=0):
                first = guard.native_cycle(runtime, root / "first", ["mock-only"], b"same", True)
                second = guard.native_cycle(runtime, root / "second", ["mock-only"], b"same", False)
            self.assertEqual(len(hosts), 2)
            self.assertEqual(first["forced_preflight"]["phase"], "bootstrap")
            self.assertEqual(second["forced_preflight"]["phase"], "retained")
            self.assertEqual(len(first["forced_preflight"]["forced"]), 5)
            self.assertEqual(len(second["forced_preflight"]["forced"]), 2)
            for receipt, host in zip((first, second), hosts):
                self.assertEqual(receipt["result"], "PASS")
                self.assertEqual(receipt["forced_after_clean_stop"]["forced"], [[11, 11], [32, 32]])
                self.assertEqual(host.finish_timeout, 300)
                self.assertEqual(host.sent[-1], "stop")
            self.assertEqual(record.call_count, 2)
            self.assertEqual(record.call_args_list[0].args, (b"same", b"same"))
            self.assertEqual(record.call_args_list[1].args, (b"same", b"same"))
            self.assertTrue(all(command in hosts[0].sent for command, _ in guard.FORCED_REMOVALS))
            self.assertFalse(any(command in hosts[1].sent for command, _ in guard.FORCED_REMOVALS))
            self.assertIn("execute if block 186 180 180 " + guard.tanks.NS
                          + ":pressurized_tank run say ARCE_GUARD_REMOVAL_ROLLED_BACK", hosts[0].sent)
            self.assertIn("execute if block 190 180 180 minecraft:air run say ARCE_GUARD_MUTATION_ROLLED_BACK", hosts[0].sent)


if __name__ == "__main__":
    unittest.main()
