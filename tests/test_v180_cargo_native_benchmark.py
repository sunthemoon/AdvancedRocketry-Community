import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_native_benchmark as runner


class CargoNativeVerdictTest(unittest.TestCase):
    def fixture(self):
        return dict(schema_version=1, status="NATIVE_MEASURED", heap_max_bytes=4 * 1024**3, mixed_queue_start=8,
                    item_nbt_refusals=[dict(status="CARGO_TOO_LARGE", unmutated=True) for _ in range(2)],
                    fuel_device_epochs=[1, 1], devices=[dict(device=d, large=large, resources_verified=True,
                        updated_visual=True, adjacent_codec_refused=True, blocks=2048 if large else 1,
                        payloads=128 if large else 1, cycle_ticks=100, cycle_ns=5_000_000_000)
                        for d in runner.micro.DEVICES for large in (True, False)],
                    ticks=[dict(tick=i, charged_bytes=16 * runner.micro.MIB, visits=16384, reconstructions=1,
                                commits=64, reconciliations=8, detached_capture_bytes=16 * runner.micro.MIB) for i in range(1400)])

    def test_requires_real_device_cycles_maximum_and_republication(self):
        good = self.fixture(); self.assertEqual([], runner.native_coverage(good))
        for key in ("resources_verified", "updated_visual", "adjacent_codec_refused", "cycle_ticks", "cycle_ns", "payloads"):
            bad = copy.deepcopy(good); bad["devices"][0].pop(key)
            self.assertTrue(runner.native_coverage(bad), key)
        bad = copy.deepcopy(good); bad["fuel_device_epochs"] = [0, 0]
        self.assertTrue(runner.native_coverage(bad))
        bad = copy.deepcopy(good); bad["ticks"].pop()
        self.assertTrue(runner.native_coverage(bad))

    def test_all_work_budgets_and_duplicate_ticks(self):
        good = self.fixture(); self.assertEqual([], runner.native_budgets(good))
        for key in ("charged_bytes", "visits", "reconstructions", "commits", "reconciliations", "detached_capture_bytes"):
            bad = copy.deepcopy(good); bad["ticks"][0][key] += 1
            self.assertTrue(runner.native_budgets(bad), key)
        good["ticks"].append(good["ticks"][0])
        with self.assertRaises(ValueError):
            runner.native_budgets(good)

    def test_failed_or_incomplete_probe_never_supplies_coverage(self):
        for raw in ({}, dict(status="STOP_MEASUREMENT_ERROR"), dict(status="STOP_MISSING_MEASUREMENT")):
            self.assertTrue(runner.native_coverage(raw))

    def test_pressure_preserves_count_components_bytes_and_full_completion(self):
        good = dict(schema_version=1, status="NATIVE_MEASURED", requested_records=64, attempted_requests=64,
                    requested_interpretation="competing_requests", effective_capacity=62, admitted_records=62,
                    acknowledged_records=62, retired_records=59, retained_records=3, payload_millibuckets=64000,
                    component_sizes=[4, 4] + [1] * 56, operation_bytes=[1234] * 64,
                    historical_operation_bytes=[1234] * 64, elapsed_ticks=1400, heap_max_bytes=4 * 1024**3,
                    root_limit_bytes=runner.micro.MIB, floor_retirement_headroom_bytes=15360, admitted_reserved_bytes=1020178,
                    refusals=[dict(request=i, sequence=63, status="ReservationFull", unmutated=True, wall_ns=1000000,
                                   candidate_reserved_bytes=1034514, retirement_headroom_bytes=15360) for i in (63, 64)])
        self.assertEqual([], runner.pressure_coverage(good))
        for key in good:
            bad = copy.deepcopy(good); bad.pop(key)
            self.assertTrue(runner.pressure_coverage(bad), key)
        for key, value in (("unmutated", False), ("wall_ns", 0), ("status", "OTHER"), ("request", 63),
                           ("candidate_reserved_bytes", 1000000)):
            bad = copy.deepcopy(good); bad["refusals"][1][key] = value
            self.assertTrue(runner.pressure_coverage(bad), key)
        for key, value in (("component_sizes", [8] + [1] * 56), ("admitted_records", 63),
                           ("historical_operation_bytes", [1235] * 64)):
            bad = copy.deepcopy(good); bad[key] = value
            self.assertTrue(runner.pressure_coverage(bad), key)

    def test_refusal_cost_is_not_exempt_from_owner_thread_budgets(self):
        good = {p: dict(mean=25, p95=50, p99=100, maximum=500) for p in ("admission_main", "admission_refusal_main")}
        self.assertTrue(all(runner.pressure_costs(good).values()))
        for phase in good:
            for metric in good[phase]:
                bad = copy.deepcopy(good); bad[phase][metric] += .001
                self.assertFalse(all(runner.pressure_costs(bad).values()))
        self.assertFalse(all(runner.pressure_costs({}).values()))

    def test_pressure_does_not_select_historical_or_generic_release_probe(self):
        flags = runner.probe_flags("pressure", "start", "abc")
        self.assertIn("-Dadvancedrocketrycommunity.cargoPressureBenchmark=true", flags)
        self.assertFalse(any("escrowCase" in f or "escrowScenario" in f for f in flags))
        for stage in ("start", "recover"):
            flags = runner.probe_flags("reference", stage, "abc")
            self.assertIn("-Dadvancedrocketrycommunity.escrowScenario=" + stage, flags)
            self.assertIn("-Dadvancedrocketrycommunity.escrowCase=GOVERNED_BENCH", flags)


if __name__ == "__main__":
    unittest.main()
