"""Adversarial checks of the W208 observation boundary and evidence oracle."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import w208_availability as subject
import run_w208_availability as runner
import w208_summary as summary


class AvailabilityTest(unittest.TestCase):
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
