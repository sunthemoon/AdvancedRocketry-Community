import importlib.util
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
import run_w151_native as native


class NativeBoundTest(unittest.TestCase):
    def fixture(self):
        cycle = dict(request="one", capture=100, callback=130, writer_capture=140, ack=145)
        labels = {"BARRIER_STORE_AFTER": 120, "BARRIER_FORCE_AFTER": 125,
                  "BARRIER_READBACK_AFTER": 128, "K1": 141, "K2": 144}
        return dict(cycles=[cycle], events=[dict(event="checkpoint", cycle="one", label=key, tick=value)
                                          for key, value in labels.items()])

    def test_batch_join_and_reader_queue_are_included(self):
        row = native.cycle_bounds(self.fixture())[0]
        self.assertEqual(row["measured"], dict(S=20, F=5, R=3, C=2, Q=10, W=5))
        self.assertEqual(row["bound"], 1860)
        self.assertTrue(row["passed"] and row["guard_and_replacement_observed"])

    def test_service_bound_failure_is_not_hidden_by_total(self):
        raw = self.fixture(); raw["cycles"][0]["ack"] = 341
        self.assertFalse(native.cycle_bounds(raw)[0]["passed"])

    def test_missing_stage_is_not_zero_duration(self):
        raw = self.fixture(); raw["events"].pop(1)
        with self.assertRaises(KeyError): native.cycle_bounds(raw)

    def test_backlog_write_does_not_stand_for_arm_guard(self):
        raw = self.fixture()
        for event in raw["events"]:
            if event["label"] in ("K1", "K2"): event["tick"] = 131
        self.assertFalse(native.cycle_bounds(raw)[0]["guard_and_replacement_observed"])

    def test_external_save_refusal_is_not_an_eligible_success(self):
        raw = self.fixture(); raw["cycles"].insert(0, dict(expected_refusal=True))
        self.assertEqual(len(native.cycle_bounds(raw)), 1)
        with self.assertRaises(ValueError): native.cycle_bounds(dict(cycles=[dict(expected_refusal=True)]))

    def test_empty_performance_evidence_fails(self):
        with self.assertRaises(ValueError): native.costs({}, [100])
        with self.assertRaises(ValueError): native.costs({"tick_ns": [100]}, [])

    def test_reference_rerequest_requires_a_failed_predecessor_and_fresh_id(self):
        first = dict(event="reference_request", request="one", attempt=1, previous_request="none", previous_outcome="none")
        second = dict(event="reference_request", request="two", attempt=2, previous_request="one", previous_outcome="FAILED")
        raw = dict(reference_requests=2, events=[first, second])
        self.assertEqual(len(native.reference_requests(raw)), 2)
        second["previous_outcome"] = "ACTIVE"
        with self.assertRaises(ValueError): native.reference_requests(raw)
        second["previous_outcome"] = "FAILED"; second["request"] = "one"
        with self.assertRaises(ValueError): native.reference_requests(raw)

    def test_missing_reference_request_observation_fails(self):
        with self.assertRaises(ValueError): native.reference_requests(dict(reference_requests=1, events=[]))


if __name__ == "__main__": unittest.main()
