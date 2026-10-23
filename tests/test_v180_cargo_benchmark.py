import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_benchmark as runner


class CargoBenchmarkTest(unittest.TestCase):
    def report(self):
        fixtures = []
        for device in sorted(runner.DEVICES):
            rows = [dict(phase=phase, operation=i, tick=1, wall_ns=25_000_000, charged_bytes=16 * runner.MIB,
                         visits=16_384, reconstructions=1, preparation_retained_native_bytes=16 * runner.MIB)
                    for phase in runner.PHASES for i in range(5, 55)]
            fixtures.append(dict(device=device, blocks=2048, payloads=128, oversized_aggregate_bytes=runner.MIB,
                                 one_mib_aggregate_refused=True, adjacent_aggregate_refused=True, samples=rows))
        return dict(schema_version=1, environment="A0_MICROBENCHMARK", max_heap_bytes=4 * 1024**3, fixtures=fixtures)

    def test_complete_microbenchmark_never_passes_native_coverage(self):
        result = runner.evaluate(self.report(), [1])
        self.assertEqual("STOP_MISSING_MEASUREMENT", result["status"])
        self.assertEqual(runner.NATIVE, result["missing_measurements"])
        self.assertEqual([], result["budget_failures"])

    def test_all_timing_budgets_recomputed_not_trusted_from_summary(self):
        for nanos in (25_000_001, 50_000_001, 100_000_001, 500_000_001):
            report = self.report()
            for row in report["fixtures"][0]["samples"]:
                if row["phase"] == "capture_tick":
                    row["wall_ns"] = nanos
            report["fixtures"][0]["phases"] = {"capture_tick": {"within": True}}
            self.assertEqual("STOP_BUDGET", runner.evaluate(report, [1])["status"])

    def test_each_work_budget_is_checked_and_readback_not_called_mspt(self):
        for key, bound in (("charged_bytes", 16 * runner.MIB), ("visits", 16_384), ("reconstructions", 1),
                           ("preparation_retained_native_bytes", 16 * runner.MIB)):
            report = self.report()
            row = next(row for row in report["fixtures"][0]["samples"] if row["phase"] == "prepare_tick")
            row[key] = bound + 1
            self.assertEqual("STOP_BUDGET", runner.evaluate(report, [1])["status"])
        report = self.report()
        for row in report["fixtures"][0]["samples"]:
            if row["phase"] == "readback_hash_io":
                row["wall_ns"] = 900_000_000
        self.assertEqual([], runner.evaluate(report, [1])["budget_failures"])

    def test_missing_operation_phase_fixture_rss_or_refusal_remains_missing(self):
        original = self.report()
        variants = []
        for field in ("one_mib_aggregate_refused", "adjacent_aggregate_refused", "oversized_aggregate_bytes", "blocks", "payloads"):
            report = copy.deepcopy(original); report["fixtures"][0].pop(field); variants.append(report)
        report = copy.deepcopy(original); report["fixtures"][0]["samples"].pop(); variants.append(report)
        report = copy.deepcopy(original); report["fixtures"].pop(); variants.append(report)
        for report in variants:
            self.assertGreater(len(runner.evaluate(report, [1])["missing_measurements"]), len(runner.NATIVE))
        self.assertIn("RSS", runner.evaluate(original, [])["missing_measurements"])

    def test_oversize_heap_rss_and_invalid_sample_are_not_accepted(self):
        report = self.report(); report["max_heap_bytes"] += 1
        self.assertEqual("STOP_BUDGET", runner.evaluate(report, [1])["status"])
        self.assertEqual("STOP_BUDGET", runner.evaluate(self.report(), [runner.BUDGETS["rss_bytes"] + 1])["status"])
        report = self.report(); report["fixtures"][0]["samples"][0]["wall_ns"] = -1
        with self.assertRaises(ValueError):
            runner.evaluate(report, [1])

    def test_duplicate_or_unidentified_tick_cannot_dilute_the_percentiles(self):
        report = self.report()
        report["fixtures"][0]["samples"].append(report["fixtures"][0]["samples"][0])
        with self.assertRaises(ValueError):
            runner.evaluate(report, [1])
        report = self.report()
        row = next(row for row in report["fixtures"][0]["samples"] if row["phase"] == "prepare_tick")
        row.pop("tick")
        with self.assertRaises(ValueError):
            runner.evaluate(report, [1])


if __name__ == "__main__":
    unittest.main()
