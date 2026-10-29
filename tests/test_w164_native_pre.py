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

    def test_bounded_archive_keeps_complete_census_files_and_external_chunks(self):
        from w164_native_inputs import preserve_bounded
        import json
        import tarfile
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root / "world"; evidence = root / "evidence"
            evidence.mkdir()
            snapshot = dict(source_origin=[512, 100, 512], bounding_box=[-1, 0, -1, 1, 2, 1])
            old = dict(source_snapshot=dict(snapshot, source_dimension="minecraft:overworld"),
                       destination_snapshot=dict(snapshot, source_dimension="advancedrocketrycommunity:moon"))
            (evidence / "predecessor.json").write_text(json.dumps(old), encoding="utf-8")
            samples = {}
            for prefix in ("", "dimensions/advancedrocketrycommunity/moon/"):
                for kind in ("region", "poi"):
                    for x in (0, 1):
                        for z in (0, 1): samples[f"{prefix}{kind}/r.{x}.{z}.mca"] = bytes(range(256)) * 33
            samples.update({"entities/r.9.9.mca": b"off-origin entity census",
                            "entities/c.288.288.mcc": b"external entity section",
                            "region/c.32.32.mcc": b"external terrain section",
                            "data/journal.dat": b"exact saved journal",
                            "playerdata/player.dat": b"exact player image", "level.dat_old": b"metadata"})
            for name, raw in dict(samples, **{"region/r.9.9.mca": b"unobserved terrain"}).items():
                path = world / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
            result = preserve_bounded(world, evidence, 0)
            with tarfile.open(evidence / result["file"], "r:xz") as archive:
                self.assertEqual(set(samples), set(archive.getnames()))
                for name, raw in samples.items(): self.assertEqual(raw, archive.extractfile(name).read())

    def test_case_compaction_retains_each_restart_as_a_distinct_exact_image(self):
        from w164_native_inputs import compact_case, preserve_bounded
        import json
        import tarfile
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root / "world"; evidence = root / "evidence"
            (world / "entities").mkdir(parents=True); evidence.mkdir()
            snapshot = dict(source_origin=[8, 100, 8], bounding_box=[-1, 0, 0, 1, 2, 0], source_dimension="minecraft:overworld")
            (evidence / "predecessor.json").write_text(json.dumps(dict(source_snapshot=snapshot, destination_snapshot=snapshot)))
            row = dict(starts=[])
            for stage in range(3):
                (world / "entities/r.0.0.mca").write_bytes(b"native " + bytes([stage]) * 8192)
                row["starts"].append(dict(restart=stage, native_inputs=preserve_bounded(world, evidence, stage)))
            compact_case(row, evidence)
            with tarfile.open(evidence / row["native_archive"]["file"], "r:xz") as archive:
                self.assertEqual(3, len(archive.getnames()))
                for stage, start in enumerate(row["starts"]):
                    self.assertEqual(b"native " + bytes([stage]) * 8192,
                                     archive.extractfile(start["native_inputs"]["member_prefix"] + "entities/r.0.0.mca").read())
                    self.assertFalse((evidence / f"raw-{stage}.tar.xz").exists())


if __name__ == "__main__": unittest.main()
