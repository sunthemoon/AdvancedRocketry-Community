"""Pure summary arithmetic and qualification checks for the native benchmark."""
import importlib.util
import csv
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("w197_cost_summary", Path(__file__).resolve().parents[1] / "scripts/w197_cost_summary.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SummaryTest(unittest.TestCase):
    def test_nearest_rank_keeps_maximum_at_small_sample_p99(self):
        self.assertEqual(MODULE.statistics(range(1, 101)),
                         dict(count=100, mean=50.5, p50=50, p95=95, p99=99, maximum=100))
        self.assertEqual(MODULE.statistics([1000, 5000], 1000)["p99"], 5)

    def test_empty_is_not_reported_as_zero_cost(self):
        self.assertEqual(MODULE.statistics([]), {"count": 0})

    def test_host_load_separates_native_process_from_other_work(self):
        rows = [dict(host_total_100ns=100, host_idle_100ns=20, server_cpu_100ns=10, peak_rss=1024**3),
                dict(host_total_100ns=200, host_idle_100ns=80, server_cpu_100ns=40, peak_rss=2 * 1024**3)]
        stats = MODULE.host_load(rows)
        self.assertEqual(stats["host_busy_percent"]["mean"], 40)
        self.assertEqual(stats["server_percent_of_host_capacity"]["mean"], 30)
        self.assertEqual(stats["other_busy_percent"]["mean"], 10)
        self.assertEqual(stats["peak_rss_gib"], 2)

    def test_timing_qualification_requires_full_non_overlapping_components(self):
        row = dict(vanilla_core_ns="600", forge_end_ns="50", tick_ns="650", observed_ns="100")
        self.assertTrue(MODULE.timing_qualified([row]))
        self.assertFalse(MODULE.timing_qualified([]))
        self.assertFalse(MODULE.timing_qualified([dict(row, tick_ns="150")]))
        self.assertFalse(MODULE.timing_qualified([dict(row, observed_ns="651")]))
        self.assertFalse(MODULE.timing_qualified([dict(tick_ns="150", observed_ns="100")]))

    def test_active_window_retains_slow_tick_and_incomplete_proofs_do_not_qualify(self):
        with tempfile.TemporaryDirectory(prefix="w197-summary-") as directory:
            root = Path(directory)
            (root / "w197-result.json").write_text(json.dumps({"outcome": "MEASURED", "prepared": 64, "handed_off": 6}))
            with (root / "w197-ticks.csv").open("w", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["phase", "round", "tick", "tick_ns", "observed_ns", "pending", "active"])
                writer.writerow(["PREPARATION", 0, 1, 600_000_000, 590_000_000, 64, 0])
                writer.writerows(["PREPARATION", 0, tick, 1_000_000, 0, 0, 0] for tick in range(2, 102))
            (root / "w197-steps.csv").write_text(
                "phase,round,tick,step,calls,exclusive_ns,max_inclusive_ns\nPREPARATION,0,1,keepLoaded,4,590000000,200000000\n")
            (root / "w197-proofs.csv").write_text("round,index,attempt,attempt_ticks,queue_ticks,accepted\n1,0,1,21,22,true\n")
            (root / "w197-events.csv").write_text("phase,round,tick,index,event,value\n")
            summary = MODULE.summarize(root)
            self.assertFalse(summary["fixture_qualified"])
            window = summary["windows"]["PREPARATION.round_all"]
            self.assertLess(window["tick_ms"]["mean"], 25)
            self.assertEqual(window["work_tick_ms"]["mean"], 600)
            self.assertFalse(window["work_tick_ms"]["docs17_numeric_comparison"]["max_le_500"])


if __name__ == "__main__":
    unittest.main()
