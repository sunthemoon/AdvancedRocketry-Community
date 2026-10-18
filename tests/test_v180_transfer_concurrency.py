"""The concurrency reducer must distinguish missing evidence from zero misses."""
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_transfer_concurrency as runner


def event(kind, sequence=2, nanos=100, tick=1, detail="", value=0):
    return dict(kind=kind, sequence=sequence, nanos=nanos, tick=tick, detail=detail, value=value)


class ConcurrencyTests(unittest.TestCase):
    def fixture(self):
        ids = [str(uuid.UUID(int=i + 1)) for i in range(8)]
        records = {logical: [event("mutation", detail="PREPARED"),
                             event("continuation_submit", nanos=110, value=8),
                             event("continuation_run", nanos=350, tick=2),
                             event("visit", nanos=400, tick=2, value=1),
                             event("step", nanos=401, tick=2), event("due", nanos=402, tick=2),
                             event("work", nanos=403, tick=2), event("flight", nanos=500, tick=3, detail="LANDED")] for logical in ids}
        return ids, dict(schema=1, capped=False, elapsed_nanos=1000, records=records,
                         global_ignored=[], **{"global": [event("snapshot", nanos=200, value=2),
                                                         event("completion", nanos=300, detail="COMMITTED", value=100)]},
                         ticks=[dict(tick=1, nanos=1_000_000), dict(tick=2, nanos=2_000_000)])

    def test_complete_evidence_reports_real_zero_and_timings(self):
        ids, probe = self.fixture(); result = runner.analyze(probe, ids)
        self.assertEqual(0, result["hard_criterion"])
        self.assertEqual(8, result["continuation_ms"]["count"])
        self.assertEqual(8, result["largest_continuation_queue"])
        self.assertTrue(result["main_thread_budget_pass"])
        self.assertEqual(1, result["flights"][ids[0]]["phases"][0]["writes_to_ack"])

    def test_missing_and_capped_evidence_cannot_pass(self):
        ids, probe = self.fixture()
        for change in (dict(capped=True), dict(ticks=[]), dict(records={}), {"global": []}):
            broken = copy.deepcopy(probe); broken.update(change)
            with self.assertRaises(runner.smoke.SmokeError):
                runner.analyze(broken, ids)

    def test_actionable_visit_without_dispatch_is_a_finding(self):
        ids, probe = self.fixture()
        probe["records"][ids[0]] = [e for e in probe["records"][ids[0]] if e["kind"] != "step"]
        self.assertEqual(1, runner.analyze(probe, ids)["hard_criterion"])

    def test_due_work_without_work_or_own_wait_is_a_finding(self):
        ids, probe = self.fixture()
        probe["records"][ids[0]] = [e for e in probe["records"][ids[0]] if e["kind"] != "work"]
        self.assertEqual(1, runner.analyze(probe, ids)["hard_criterion"])
        probe["records"][ids[0]].append(event("owned_wait", tick=2, detail="DESTINATION_CHUNK"))
        self.assertEqual(0, runner.analyze(probe, ids)["hard_criterion"])

    def test_four_completed_writes_exceed_step_bound(self):
        ids, probe = self.fixture()
        for i in range(3):
            probe["global"].append(event("completion", sequence=1, nanos=120 + i, detail="COMMITTED", value=1))
        self.assertEqual(8, len(runner.analyze(probe, ids)["three_write_bound_findings"]))

    def test_unacknowledged_or_discarded_work_is_not_a_completed_flight(self):
        ids, probe = self.fixture()
        probe["global"][1]["sequence"] = 1
        with self.assertRaises(runner.smoke.SmokeError):
            runner.analyze(probe, ids)
        ids, probe = self.fixture()
        probe["records"][ids[0]][2]["kind"] = "continuation_discard"
        with self.assertRaises(runner.smoke.SmokeError):
            runner.analyze(probe, ids)

    def test_tick_budget_is_not_relaxed(self):
        ids, probe = self.fixture(); probe["ticks"][0]["nanos"] = 101_000_000
        self.assertFalse(runner.analyze(probe, ids)["main_thread_budget_pass"])

    def test_signed_nbt_uuid_and_nearest_rank_percentiles(self):
        self.assertEqual(uuid.UUID("ffffffff-0000-0000-0000-000000000001"), runner.nbt_uuid([-1, 0, 0, 1]))
        self.assertEqual(95, runner.summary(list(range(1, 101)))["p95"])
        self.assertEqual(0, runner.summary([])["count"])

    def test_staggered_requests_wait_for_actual_admission_receipts(self):
        actions = []
        class Process:
            def command(self, value):
                actions.append("command")
            def wait_for(self, pattern, timeout, start_at):
                self.assertions = (timeout, start_at)
                actions.append("receipt")
        flights = [dict(entity=str(uuid.UUID(int=i + 1)), logical=str(uuid.UUID(int=i + 11))) for i in range(8)]
        with patch.object(runner.time, "sleep", side_effect=lambda seconds: actions.append("delay")):
            runner.launch_wave(Process(), flights, True, 0)
        self.assertEqual(["command", "receipt", "delay"] * 8, actions)
        actions.clear()
        runner.launch_wave(Process(), flights, False, 0)
        self.assertEqual(["command"] * 8, actions)

    def consumer_fixture(self, case="f1", complete=True):
        ids, journal = self.fixture()
        start = case + (".start" if case == "settlement" else ".attempt")
        journal["global"].append(event("consumer", detail=start, nanos=200))
        # A global outbox mutation is just as relevant as any ordinary record's mutation.
        journal["global"].append(event("mutation", nanos=300))
        rows = [dict(phase=start, nanos=10_000, value=0),
                dict(phase=case + ".barrier", nanos=10_100, value=70),
                dict(phase="ledger_io", nanos=10_150, value=30),
                dict(phase=case + ".wait.JOURNAL", nanos=10_160, value=0)]
        if complete:
            rows.append(dict(phase=case + ".complete", nanos=10_200, value=0))
            journal["global"].append(event("consumer", detail=case + ".complete", nanos=400))
        return ids, journal, dict(schema=1, capped=False, events=rows)

    def test_consumer_completion_uses_its_own_window_and_clock(self):
        for case in runner.CONSUMERS:
            ids, journal, timing = self.consumer_fixture(case)
            result = runner.analyze_consumer(timing, journal, case, ids)
            self.assertTrue(result["completed"])
            self.assertEqual(.0002, result["completion_ms"])
            self.assertEqual(.0001, result["longest_no_mutation_while_waiting_ms"])
            self.assertEqual(.00007, result["barrier_ms"]["maximum"])
            self.assertEqual(.00003, result["ledger_write_ms"]["maximum"])
            self.assertEqual({case + ".wait.JOURNAL": 1}, result["reasons"])

    def test_incomplete_consumer_is_reported_not_called_zero_latency(self):
        ids, journal, timing = self.consumer_fixture(complete=False)
        result = runner.analyze_consumer(timing, journal, "f1", ids)
        self.assertFalse(result["completed"])
        self.assertIsNone(result["completion_ms"])
        self.assertEqual(.0007, result["longest_no_mutation_while_waiting_ms"])

    def test_consumer_requires_real_start_uncapped_evidence_and_completion_agreement(self):
        for fault in ("missing_start", "cap", "completion"):
            ids, journal, timing = self.consumer_fixture()
            if fault == "missing_start": timing["events"] = timing["events"][1:]
            if fault == "cap": timing["capped"] = True
            if fault == "completion": timing["events"] = timing["events"][:-1]
            with self.assertRaises(runner.smoke.SmokeError): runner.analyze_consumer(timing, journal, "f1", ids)

    def test_consumer_start_after_landing_does_not_prove_traffic_overlap(self):
        ids, journal, timing = self.consumer_fixture()
        journal["global"][2]["nanos"] = 600
        with self.assertRaises(runner.smoke.SmokeError): runner.analyze_consumer(timing, journal, "f1", ids)

    def test_spike_report_keeps_the_tick_and_value(self):
        ids, journal = self.fixture(); journal["ticks"][0]["nanos"] = 501_000_000
        self.assertEqual([dict(tick=1, ms=501)], runner.analyze(journal, ids)["spikes_over_500_ms"])


if __name__ == "__main__":
    unittest.main()
