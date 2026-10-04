"""Fail-closed oracles for the fixed native guard lifecycle exercise."""

import copy
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

        def barrier(proc, rows, text):
            self.assertEqual(text, "ARCE_GUARD_COPY_SPAWN_END")
            self.assertEqual(rows, ["setworldspawn 256 74 -256 0"])
            proc.lines.append(self.LINE)

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
                    patch.object(guard, "say_barrier", side_effect=lambda *args: process.lines.extend(new)), \
                    self.assertRaisesRegex(RuntimeError, "exactly once"):
                guard.relocate_copy_spawn(process, [])


if __name__ == "__main__":
    unittest.main()
