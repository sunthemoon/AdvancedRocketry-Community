"""The callback-inclusive view supplements, rather than rewrites, W197's view."""
import csv
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
import w197_cost_summary as legacy


class CallbackSummaryTest(unittest.TestCase):
    def test_release_only_ticks_are_included_without_idle_dilution_or_double_counting(self):
        spec = importlib.util.spec_from_file_location("w202_callback_summary", SCRIPTS / "w202_callback_summary.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory(prefix="w202-summary-") as directory:
            root = Path(directory)
            (root / "w197-result.json").write_text(json.dumps({"outcome": "MEASURED"}))
            with (root / "w197-ticks.csv").open("w", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["phase", "round", "tick", "tick_ns", "observed_ns", "pending", "active"])
                writer.writerows([
                    ["PROOF", 1, 1, 10_000_000, 1, 64, 1],
                    ["PROOF", 1, 2, 160_000_000, 1, 63, 1],
                    ["PROOF", 2, 3, 120_000_000, 1, 62, 1],
                    ["DRAIN", 2, 4, 60_000_000, 1, 0, 0],
                ])
                writer.writerows(["PROOF", 1, tick, 1_000_000, 0, 0, 0] for tick in range(5, 105))
            (root / "w197-steps.csv").write_text(
                "phase,round,tick,step,calls,exclusive_ns,max_inclusive_ns\n"
                "PROOF,1,1,capture,1,1,1\nPROOF,1,1,releaseTickets,1,1,1\n"
                "PROOF,1,2,releaseTickets,4,1,1\nPROOF,2,3,releaseTickets,1,1,1\n"
                "DRAIN,2,4,releaseTickets,1,1,1\n")
            (root / "w197-proofs.csv").write_text("round,index,attempt,attempt_ticks,queue_ticks,accepted\n")
            (root / "w197-events.csv").write_text("phase,round,tick,index,event,value\n")
            original = legacy.summarize(root)
            (root / "summary.json").write_text(json.dumps(original))
            original_bytes = (root / "summary.json").read_bytes()
            result = module.summarize_callbacks(root)
            self.assertEqual(original, legacy.summarize(root))
            self.assertEqual(original_bytes, (root / "summary.json").read_bytes())
            all_rounds = result["windows"]["PROOF.round_all"]
            self.assertEqual(all_rounds["legacy_work_tick_ms"], original["windows"]["PROOF.round_all"]["work_tick_ms"])
            self.assertEqual(all_rounds["work_or_release_tick_ms"]["count"], 3)
            self.assertEqual(all_rounds["work_or_release_tick_ms"]["p99"], 160)
            self.assertFalse(all_rounds["work_or_release_tick_ms"]["docs17_numeric_comparison"]["p99_le_100"])
            self.assertEqual([row["tick"] for row in all_rounds["added_ticks"]], ["2", "3"])
            self.assertEqual(result["windows"]["PROOF.round_1"]["work_or_release_tick_ms"]["count"], 2)
            self.assertEqual(result["windows"]["DRAIN.round_all"]["work_or_release_tick_ms"]["count"], 1)


if __name__ == "__main__":
    unittest.main()
