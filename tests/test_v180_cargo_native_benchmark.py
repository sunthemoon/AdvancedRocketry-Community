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


if __name__ == "__main__":
    unittest.main()
