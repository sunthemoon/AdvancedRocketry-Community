"""W164's tranche count and reuse boundaries, without launching a native server."""
import inspect
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_predecessor_matrix as predecessor


class W164NativePreTests(unittest.TestCase):
    def test_predecessor_oracle_accepts_demand_and_observation_ports(self):
        parameters = inspect.signature(predecessor.run_case).parameters
        self.assertIn("demand", parameters)
        self.assertIn("preserve", parameters)
        self.assertIn("started", parameters)
        self.assertIn("command_builder", parameters)

    def test_twenty_cases_reserve_exactly_sixty_starts(self):
        import run_w164_native_pre as native
        self.assertEqual(20, len(native.CASES))
        self.assertEqual({"I6a", "I6b"}, {name.split("_", 1)[0] for name in native.CASES})
        self.assertEqual(60, sum(native.STARTS_PER_CASE for _ in native.CASES))

    def test_r124_budget_counts_failed_launches_and_allows_only_one_retry(self):
        import run_w164_native_pre as native
        self.assertEqual(66, native.MAX_STARTS)
        native.check_attempt([], "I6b_UNSAVED_STORE_D", 1)
        rows = [dict(case="I6b_UNSAVED_STORE_D", attempt=1, restart=0)]
        native.check_attempt(rows, "I6b_UNSAVED_STORE_D", 2)
        for attempt in (1, 3):
            with self.assertRaises(Exception): native.check_attempt(rows, "I6b_UNSAVED_STORE_D", attempt)
        with self.assertRaises(Exception): native.check_attempt([], "I6b_UNSAVED_STORE_D", 2)
        with self.assertRaises(Exception): native.check_attempt(rows * 64, "I6a_DESCENT_TERMINAL", 1)

    def test_plain_launch_uses_registered_cut_spelling_only_before_restart(self):
        import run_w164_native_pre as native
        from run_v180_first_event_moon_cuts import CASES as first_event_cases
        prefix = "-Darce.releaseTest.firstEventCut="
        for site in ("I6a", "I6b"):
            for restart in range(3):
                command = native.command_for(site, SimpleNamespace(java="java"), "UNSAVED", "STORE_D", restart)
                cuts = [argument.removeprefix(prefix) for argument in command if argument.startswith(prefix)]
                if site == "I6a" and restart == 0:
                    self.assertEqual(1, len(cuts))
                    self.assertIn(cuts[0], first_event_cases)
                    self.assertEqual("FM12a", cuts[0])
                else:
                    self.assertEqual([], cuts)

    def test_native_archive_round_trips_all_observed_bytes(self):
        import run_w164_native_pre as native
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root / "world"; evidence = root / "evidence"
            (world / "entities").mkdir(parents=True); (world / "data").mkdir(); evidence.mkdir()
            samples = {"entities/r.0.0.mca": bytes(range(256)) * 32, "data/record.dat": b"exact native bytes",
                       "entities/c.0.0.mcc": b"external oversized entity chunk"}
            for name, raw in samples.items(): (world / name).write_bytes(raw)
            result = native.preserve(world, evidence, 0)
            import zipfile
            with zipfile.ZipFile(evidence / result["file"]) as archive:
                for name, raw in samples.items(): self.assertEqual(raw, archive.read(name))


if __name__ == "__main__": unittest.main()
