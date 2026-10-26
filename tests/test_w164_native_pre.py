"""W164's tranche count and reuse boundaries, without launching a native server."""
import inspect
from pathlib import Path
import sys
import tempfile
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
