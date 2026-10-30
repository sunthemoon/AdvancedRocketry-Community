"""Adversarial checks of the W208 observation boundary and evidence oracle."""
import sys
from pathlib import Path
import unittest
import tempfile
import json
import re
import struct
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import w208_availability as subject
import run_w208_availability as runner
import w208_summary as summary


class AvailabilityTest(unittest.TestCase):
    def test_journal_reader_accepts_only_the_fixture_byte_array_bound(self):
        def root(size):
            return b'\x0a\x00\x00\x07\x00\x09benchmark' + struct.pack('>i', size) + bytes(size) + b'\x00'
        self.assertEqual(len(runner.JournalReader(root(43_950)).read_root()['benchmark']), 43_950)
        self.assertEqual(len(runner.JournalReader(root(125_000)).read_root()['benchmark']), 125_000)
        with self.assertRaises(ValueError):
            runner.JournalReader(root(125_001)).read_root()
        with self.assertRaises(ValueError):
            runner.JournalReader(b'\x0a\x00\x00\x0b\x00\x01a' + struct.pack('>i', 4097)).read_root()

    def test_summary_pairs_only_observed_captures_and_reports_completion_separately(self):
        raw = dict(status='COMPLETED', completion='SEALED', request_tick=10, observation_ticks=40,
                   observation_ns=2_000_000_000, stop_close={}, events=[
                       dict(label='REQUEST', tick=10, ns=1_000_000_000),
                       dict(label='ROOT_publish', tick=11, ns=10),
                       dict(label='ROOT_capture', tick=12, ns=10),
                       dict(label='ROOT_publish', tick=22, ns=10),
                       dict(label='COMPLETE_PROOF_CURRENT', tick=23, ns=1_600_000_000),
                       dict(label='COMPLETE_SEALED', tick=30, ns=2_000_000_000)],
                   ticks=[dict(tick=10, tick_ns=900_000_000), dict(tick=11, tick_ns=2_000_000)])
        result = summary.sample(raw, '')
        self.assertEqual(result['capture_to_publish_ticks'], [10])
        self.assertEqual(result['completion_ticks'], 20)
        self.assertEqual(result['completion_seconds'], 1)
        self.assertEqual(result['workload_tick_ms']['maximum'], 2)
        self.assertEqual(result['tick_ms']['maximum'], 900)

    def test_only_first_c3_election_tick_contains_fixture_growth(self):
        raw = dict(case='near-c3-unknown', stage='start', status='COMPLETED', completion='SEALED',
                   request_tick=10, observation_ticks=20, observation_ns=1, stop_close={},
                   events=[dict(label='C3_ELECT_AFTER', tick=12), dict(label='C3_ELECT_AFTER', tick=13)],
                   ticks=[dict(tick=10, tick_ns=900_000_000), dict(tick=12, tick_ns=800_000_000),
                          dict(tick=13, tick_ns=7_000_000), dict(tick=14, tick_ns=2_000_000)])
        result = summary.sample(raw, '')
        self.assertEqual(result['workload_tick_ms']['maximum'], 7)
        self.assertEqual(result['workload_tick_ms']['samples'], 2)
        self.assertEqual(result['fixture_tick_ms']['samples'], 2)

    def test_native_request_runs_before_end_tick_discovery_spends_the_budget(self):
        source = (Path(__file__).resolve().parents[1] / 'src/test/java/io/github/sunthemoon/'
                  'advancedrocketrycommunity/rocket/server/W208NativeAvailability.java').read_text()
        early = re.search(r'@SubscribeEvent\(priority = EventPriority.HIGHEST\) public void beginEnd\b(.*?)'
                          r'@SubscribeEvent', source, re.S).group(1)
        late = re.search(r'public void end\(TickEvent.ServerTickEvent event\)(.*?)private void step', source, re.S).group(1)
        self.assertIn('step();', early)
        self.assertNotIn('step();', late)

    def test_prelaunch_failure_has_a_nonlive_result_without_starting_java(self):
        with tempfile.TemporaryDirectory() as temp:
            argv = ["runner", "--case", "small-quiet", "--jar", "diagnostic.jar", "--production-jar", "normal.jar",
                    "--libraries", "libraries", "--evidence", temp, "--source-commit", "pin", "--lock-owner", "owner"]
            with patch.object(sys, "argv", argv), patch.object(runner, "require_lock", side_effect=RuntimeError("changed lock")), \
                    patch.object(runner.smoke, "CapturedProcess") as process:
                self.assertEqual(runner.main(), 1)
                process.assert_not_called()
            result = json.loads((Path(temp) / "small-quiet/result.json").read_text())
            self.assertFalse(result["live_process"])
            self.assertEqual(result["status"], "FAILED")

    def test_driver_passes_the_complete_lock_record(self):
        source = (Path(__file__).resolve().parents[1] / "scripts/run_w208_availability.ps1").read_text()
        self.assertIn('--lock-owner $ownerRecord', source)
        self.assertIn('Set-Content -LiteralPath "$lock/owner.txt" $ownerRecord', source)

    def test_summary_does_not_turn_a_hold_into_completion(self):
        result = summary.sample(dict(status="NOT_COMPLETED", completion="NONE", observation_ticks=1200,
                                     observation_ns=60_000_000_000, events=[], ticks=[], stop_close={}), "")
        self.assertEqual(result["status"], "NOT_COMPLETED")
        self.assertEqual(result["observed_seconds"], 60)
        self.assertEqual(result["tick_ms"]["samples"], 0)

    def test_runner_never_uses_implicit_process_abort(self):
        import inspect
        source = inspect.getsource(runner)
        self.assertNotIn(".abort(", source)
        self.assertNotIn(".kill(", source)
        self.assertNotIn(".terminate(", source)
        self.assertEqual(runner.OBSERVATION_SECONDS, 240)
        self.assertEqual(runner.CLEANUP_SECONDS, 180)

    def test_ceiling_never_authorizes_kill_or_reopen(self):
        self.assertEqual(subject.observation_status(False, True), "NOT_COMPLETED")
        self.assertFalse(subject.may_reopen(None))
        self.assertTrue(subject.may_reopen(0))
        self.assertTrue(subject.may_reopen(1))

    def test_acknowledgement_is_required_at_every_effect(self):
        for label in ("C2_STAGE_BEFORE", "C4_DISCARD_BEFORE", "UNSEAL_PUBLISH_BEFORE"):
            with self.assertRaises(ValueError):
                subject.check_events([dict(label=label, authorized=False, tick=1)])
            subject.check_events([dict(label=label, authorized=True, tick=1)])

    def test_matrix_is_explicit_and_small_is_not_near_limit(self):
        self.assertEqual(len(subject.CASES), 8)
        self.assertFalse(subject.near_qualified(4096, 0))
        self.assertTrue(subject.near_qualified(67_000_000, 255))
        self.assertFalse(subject.near_qualified(67_108_865, 255))

    def test_resource_oracle_does_not_sum_inert_shadows(self):
        stack = {"id": "minecraft:diamond", "Count": 7, "tag": {"W152": "w208-original"}}
        result = subject.conservation("LOCKED", [stack], [stack], [], stack)
        self.assertEqual(result["effective"], {"w208-original": 7})
        with self.assertRaises(ValueError):
            subject.conservation(None, [stack], [], [stack], None)


if __name__ == "__main__":
    unittest.main()
