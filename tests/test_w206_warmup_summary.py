"""Independent arithmetic and qualification checks for the W206 measurement oracle."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from w206_warmup_summary import summarize_rows, readiness, qualify


class SummaryTest(unittest.TestCase):
    def test_lock_owner_written_is_the_exact_token_passed_to_native(self):
        script = (Path(__file__).resolve().parents[1] / "scripts/run_w206_warmup.ps1").read_text()
        owner_line = next(line.strip() for line in script.splitlines() if line.strip().startswith('Set-Content -LiteralPath "$lock/owner.txt"'))
        self.assertEqual(owner_line, 'Set-Content -LiteralPath "$lock/owner.txt" $token')
        self.assertIn('--lock-owner $token', script)

    def test_idle_ticks_do_not_dilute_work_or_callback_cohorts(self):
        rows = [dict(tick=i, core_ns=1_000_000, end_ns=0, tick_ns=1_000_000,
                     work=False, callbacks=0, phase="IDLE") for i in range(100)]
        rows += [dict(tick=101, core_ns=100_000_000, end_ns=20_000_000, tick_ns=120_000_000,
                      work=True, callbacks=1, phase="FLIGHT")]
        result = summarize_rows(rows)
        self.assertEqual(result["work"]["count"], 1)
        self.assertEqual(result["work"]["mean"], 120)
        self.assertEqual(result["callbacks"]["p99"], 120)
        self.assertFalse(result["work"]["docs17"]["p99_le_100"])
        self.assertTrue(result["timing_qualified"])

    def test_missing_end_time_cannot_qualify(self):
        self.assertFalse(summarize_rows([dict(tick_ns=1, work=True, callbacks=0, phase="X")])["timing_qualified"])

    def test_readiness_latency_is_per_allocation_not_process_age(self):
        events = [dict(kind="STARTED", logical="a", request="r1", tick=120, ns=10_000_000),
                  dict(kind="READY", logical="a", request="r1", tick=125, ns=30_000_000),
                  dict(kind="STARTED", logical="a", request="r2", tick=200, ns=50_000_000)]
        result = readiness(events)
        self.assertEqual(result[0]["ticks"], 5)
        self.assertEqual(result[0]["ms"], 20)
        self.assertIsNone(result[1]["ticks"])
        self.assertEqual(result[1]["status"], "NOT_READY")

    def test_fixture_requires_real_cold_and_exact_release_evidence(self):
        base = dict(outcome="MEASURED", cold=True, no_other_ticket=True, new_terrain=True,
                    selected_columns=16, expected_columns=16, f2=True, handover=True,
                    f8=True, tickets_after_f8=[], warmups_after_f8=[], source_unchanged=True)
        self.assertTrue(qualify(base))
        for key in ("cold", "no_other_ticket", "source_unchanged", "f2", "handover", "f8"):
            self.assertFalse(qualify(dict(base, **{key: False})), key)
        self.assertFalse(qualify(dict(base, tickets_after_f8=["flight"])))
        self.assertFalse(qualify(dict(base, selected_columns=1)))


if __name__ == "__main__":
    unittest.main()
