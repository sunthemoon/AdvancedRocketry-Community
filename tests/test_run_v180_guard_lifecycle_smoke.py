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

            def wait_for(self, pattern, timeout, start_at):
                self.remaining, self.start = timeout, start_at
                return 1

        process = Process()
        with patch.object(guard, "wait_chunk") as predicate, \
                patch.object(guard.time, "monotonic", side_effect=[0, 40, 40.1]):
            self.assertEqual(guard.wait_unload_begin(process, [], 1), self.LINE)
        predicate.assert_called_once_with(process, [], False, 60)
        self.assertEqual((process.remaining, process.start), (20, 1))

    def test_old_marker_does_not_substitute_for_new_unload(self):
        class Process:
            lines = [NativeUnloadObservationTest.LINE]

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

            def wait_for(self, *args, **kwargs):
                return 0

        with patch.object(guard, "wait_chunk"), \
                patch.object(guard.time, "monotonic", side_effect=[0, 0, 61]), \
                self.assertRaisesRegex(guard.server.SmokeError, "after its original deadline"):
            guard.wait_unload_begin(Process(), [], 0)


if __name__ == "__main__":
    unittest.main()
