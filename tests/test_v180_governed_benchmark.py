import copy
import csv
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_governed_benchmark as runner


class GovernedBenchmarkTest(unittest.TestCase):
    def test_every_catalogued_phase_is_accepted_and_preserved(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "samples.csv"
            for phase in sorted(runner.KNOWN_PHASES):
                with self.subTest(phase=phase):
                    with path.open("w", newline="", encoding="utf-8") as stream:
                        writer = csv.writer(stream)
                        writer.writerow(["phase", "generation", "value"])
                        writer.writerow([phase, 1 if phase in runner.GENERATION_PHASES else -1, 17])
                    phases, _ = runner.read_samples(path)
                    self.assertEqual({phase}, set(phases))
                    self.assertEqual(1, phases[phase]["count"])

    def test_unknown_phase_or_diagnostic_fails_the_entire_collection(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "samples.csv"
            for phase in ("unknown_phase", "settlement.unknown", "settlement.stall.UNKNOWN",
                          "f1.stall.UNKNOWN", "cancel.wait.UNKNOWN", "settlement.attempt.extra", ""):
                with self.subTest(phase=phase):
                    path.write_text("phase,generation,value\nsettlement.attempt,-1,0\n" + phase + ",-1,0\n", encoding="utf-8")
                    with self.assertRaises(runner.smoke.SmokeError):
                        runner.read_samples(path)

    def proof(self):
        load = dict(schema_version=1, players=2, active_oxygen=16, active_providers=16, stations=10, satellite_tasks=100,
                    flights_completed=8, peak_concurrent=8, assembly_blocks=2048, dimension_transfers=8,
                    ticks=1400, player_tick_delta=2800, periodic_saves=7, ordinary_retired=True, root_encode_bytes=1048576,
                    generation_topology={"1": dict(records=0, floors=0, receipts=1, heads=8, armed=0)},
                    checkpoints={point: 8 for point in runner.FLIGHT_POINTS})
        load["checkpoints"].update(C8_PRUNE_AFTER=1, O3_AFTER=1, O4_AFTER=1, WORLD_FIRST_DEFER=1)
        phases = {phase: dict(count=1) for phase in runner.REQUIRED_PHASES}
        phases["flight_handoff_latency"]["count"] = 8
        phases["tick_main"]["count"] = 1400
        phases["root_1mib_encode_main"] = dict(count=1)
        generations = {1: {phase: [1] for phase in runner.GENERATION_PHASES}}
        return load, phases, generations

    def test_complete_evidence_and_each_required_load_counter(self):
        args = self.proof()
        self.assertEqual([], runner.coverage(*args, True))
        for key in ("players", "player_tick_delta", "active_oxygen", "active_providers", "stations", "satellite_tasks", "flights_completed",
                    "peak_concurrent", "assembly_blocks", "dimension_transfers", "ticks", "periodic_saves"):
            bad = copy.deepcopy(args); bad[0][key] = 0
            self.assertIn("load:" + key, runner.coverage(*bad, True))

    def test_cannot_substitute_empty_or_saturated_worker_results(self):
        self.assertTrue(runner.coverage({}, {}, {}, False))
        load, phases, generations = self.proof()
        for point in ("C8_PRUNE_AFTER", "O4_AFTER", "WORLD_FIRST_DEFER", "F8_PRUNE_ACK"):
            bad = copy.deepcopy(load); bad["checkpoints"].pop(point)
            self.assertIn("checkpoint:" + point, runner.coverage(bad, phases, generations, True))
        phases.pop("root_1mib_encode_main")
        self.assertIn("1 MiB root encode", runner.coverage(load, phases, generations, True))

    def test_f2_requires_eight_checked_countdowns_even_when_callbacks_overflow(self):
        load, phases, generations = self.proof()
        load["checkpoints"].update(F2_PREPARED_ACK=2, F2_COUNTDOWN=2)
        self.assertEqual([], runner.coverage(load, phases, generations, True))
        for point in ("BENCH_F2_ACKNOWLEDGED", "BENCH_COUNTDOWN_OBSERVED"):
            bad = copy.deepcopy(load); bad["checkpoints"][point] = 7
            self.assertIn("checkpoint:" + point, runner.coverage(bad, phases, generations, True))

    def test_no_pruning_generation_can_be_silently_omitted_from_accounting(self):
        load, phases, generations = self.proof()
        generations[2] = {"generation_capture_main": [1]}
        self.assertIn("complete per-generation accounting (including pruning)", runner.coverage(load, phases, generations, True))
        self.assertIn("per-generation root topology", runner.coverage(load, phases, generations, True))

    def test_budgets_are_not_inferred_from_coverage_or_averages_alone(self):
        phases = dict(tick_main=dict(mean=25, p95=50, p99=100), tick_main_nonsave=dict(maximum=500))
        self.assertTrue(all(runner.compare(phases, [runner.BUDGETS["rss_bytes"]]).values()))
        for metric, budget in (("mean", 25), ("p95", 50), ("p99", 100)):
            bad = copy.deepcopy(phases); bad["tick_main"][metric] = budget + .001
            self.assertFalse(all(runner.compare(bad, [1]).values()))
        self.assertFalse(runner.compare(phases, [])["rss_bytes"])

    def test_samples_preserve_generation_identity_and_bytes_units(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "samples.csv"
            path.write_text("phase,generation,value\ntick_main,-1,25000000\ngeneration_root_bytes,2,1048576\n", encoding="utf-8")
            phases, generations = runner.read_samples(path)
            self.assertEqual(25, phases["tick_main"]["mean"])
            self.assertEqual("bytes", phases["generation_root_bytes"]["unit"])
            self.assertEqual([1048576], generations[2]["generation_root_bytes"])
            for row in ("tick_main,-1,-1", "generation_root_bytes,-1,10", "tick_main,2,10"):
                path.write_text("phase,generation,value\n" + row + "\n", encoding="utf-8")
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.read_samples(path)

    def test_quiet_preflight_records_busy_and_empty_inventories(self):
        with tempfile.TemporaryDirectory() as root:
            rows = iter([[dict(ProcessId=123, CommandLine="other worker test")], []])
            runner.wait_quiet(Path(root), "start", 2, inventory=lambda: next(rows), sleep=lambda _: None)
            text = (Path(root) / "start-java-processes.jsonl").read_text()
            self.assertIn("other worker test", text); self.assertIn('"processes": []', text)

    def test_owned_lock_is_released_and_foreign_lock_is_not_removed(self):
        with tempfile.TemporaryDirectory() as root:
            lock = Path(root) / "lock"
            with runner.heavy_lock(lock, 0):
                self.assertTrue((lock / "owner.txt").is_file())
            self.assertFalse(lock.exists())
            lock.mkdir(); (lock / "owner.txt").write_text("another worker")
            with self.assertRaises(runner.smoke.SmokeError):
                with runner.heavy_lock(lock, 0):
                    self.fail("foreign lock acquired")
            self.assertEqual("another worker", (lock / "owner.txt").read_text())


if __name__ == "__main__":
    unittest.main()
