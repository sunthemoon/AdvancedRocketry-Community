import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_integrated as runner


class IntegratedCargoRunnerTest(unittest.TestCase):
    def test_committed_matrix_lists_each_cut_once_and_names_real_gametests(self):
        matrix = (runner.REPO / "docs/work/v1.8.0-root-queue/records/W326-MATRIX.md").read_text(encoding="utf-8")
        lines = [line for line in matrix.splitlines() if re.match(r"\| N\d{3} \|", line)]
        self.assertEqual(len(lines), 120)
        for row in runner.inventory():
            if row["family"] in ("C-S1", "C-S2", "C-S3", "C-S4", "C-S5", "D-S1", "D-S2"):
                self.assertEqual(sum(line.endswith("`" + row["case"] + "` |") for line in lines), 1)
        for class_name, method in re.findall(r"`(\w+GameTests)\.(\w+)`", matrix):
            source = runner.REPO / "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow" / (class_name + ".java")
            self.assertIn("void " + method + "(", source.read_text(encoding="utf-8"))

    def test_inventory_has_every_design_cut_once(self):
        rows = runner.inventory()
        self.assertEqual(Counter(row["family"] for row in rows),
                         {"C-S1": 28, "C-S2": 16, "C-S3": 10, "C-S4": 20, "C-S5": 16, "D-S1": 12, "D-S2": 18,
                          "C-S4-LOAD": 20, "D-S2-LOAD": 13, "C-Q": 4, "B3-S1-REUSE": 7, "B3-S4-REUSE": len(runner.loader.oracle.S4),
                          "B3-S5-REUSE": 8, "B3-S6-REUSE": len(runner.loader_s6.CASES), "G2-REUSE": 1})
        self.assertEqual(len({row["case"] for row in rows}), len(rows))
        self.assertEqual({row["case"] for row in rows}, set().union(*(set(module.CASES) for module in runner.RUNNERS),
                          *(set(cases) for _, cases in runner.REUSE.values()), {"G2_S1_CAMPAIGN"}))

    def test_direction_uses_actual_probe_and_recovery_role(self):
        kinds = {row["case"]: row["kind"] for row in runner.inventory()}
        for case in ("IC_PARTIAL_STORE", "IR_CONTAINER", "IS_O3_AFTER"):
            self.assertEqual(kinds[case], "ITEM_CARGO_UNLOAD")
        self.assertEqual(kinds["IC_PARTIAL_APPLY"], "ITEM_CARGO_LOAD")
        for case in runner.RECOVERY_UNLOAD | {"FS_O3_AFTER"}:
            self.assertEqual(kinds[case], "FLUID_CARGO_UNLOAD")
        self.assertEqual(kinds["FF_TWO_FLIGHTS"], "FLUID_CARGO_LOAD")
        for row in runner.inventory():
            if row["family"] in ("C-S4-LOAD", "D-S2-LOAD"):
                self.assertTrue(row["kind"].endswith("CARGO_LOAD"))
        for case, kind in runner.cap.CASES.items():
            self.assertEqual(kinds[case], kind["kind"])

    def test_candidate_hash_and_production_equality_are_mandatory(self):
        with patch.object(runner.fluid.native, "assert_pin") as pin, patch.object(runner.subprocess, "run") as diff, \
                patch.object(runner.fluid.native, "digest", return_value="different"):
            diff.return_value.stdout = "a" * 40 + "\n"
            with self.assertRaisesRegex(ValueError, "required candidate SHA-256"):
                runner.verify_candidate(Path("candidate.jar"), "source", "abcdef0", "b" * 64)
            pin.assert_called_once_with("source")
            self.assertIn("--quiet", diff.call_args.args[0])
            self.assertEqual(diff.call_args.args[0][-5:], list(runner.PRODUCTION_PATHS))
            self.assertTrue(diff.call_args.kwargs["check"])
            self.assertEqual(diff.call_args.args[0][3], "a" * 40)
            with patch.object(runner.fluid.native, "digest", return_value="b" * 64):
                result = runner.verify_candidate(Path("candidate.jar"), "source", "abcdef0", "b" * 64)
            self.assertEqual(result["candidate_commit"], "a" * 40)
            self.assertEqual(result["candidate_jar_sha256"], result["jar_sha256"])
        for commit, sha in (("HEAD", "b" * 64), ("abcdef0", "wrong")):
            with self.assertRaises(ValueError):
                runner.verify_candidate(Path("candidate.jar"), "source", commit, sha)

    def test_archive_retains_all_native_regions_and_exact_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp); world = base / "world"
            for name in ("data/a.dat", "entities/r.0.0.mca", "entities/r.9.9.mca", "region/r.0.0.mcc"):
                path = world / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(name.encode())
            result = runner.archive_native(world, base / "native.zip")
            self.assertEqual(len(result["members"]), 4)
            with zipfile.ZipFile(base / "native.zip") as archive:
                self.assertEqual(archive.read("entities/r.9.9.mca"), b"entities/r.9.9.mca")
                self.assertEqual(json.loads(archive.read("MEMBERS.json")), result["members"])

    def test_wrapper_propagates_first_contradiction_and_does_not_run_another_family(self):
        with tempfile.TemporaryDirectory() as temp:
            args = argparse.Namespace(family="C-S1", jar=Path("candidate.jar"), source_commit="source", evidence_dir=Path(temp),
                                      candidate_commit="abcdef0", candidate_sha256="b" * 64)
            original = runner.fluid.raw_snapshot
            with patch.object(runner, "verify_candidate", return_value={}), \
                    patch.object(runner.fluid, "run", side_effect=ValueError("contradiction")) as run, \
                    patch.object(runner.item, "run") as other:
                with self.assertRaisesRegex(ValueError, "contradiction"):
                    runner.run(args)
                self.assertEqual(len(run.call_args.args[0].case), 28)
                self.assertEqual(args.startup_timeout, 240)
                other.assert_not_called()
            self.assertIs(runner.fluid.raw_snapshot, original)

    def test_new_families_delegate_exact_cases_and_preserve_pins(self):
        for family in ("C-Q", "C-S4-LOAD", "D-S2-LOAD"):
            module = {"C-Q": runner.cap, "C-S4-LOAD": runner.settlement, "D-S2-LOAD": runner.item}[family]
            case = next(row["case"] for row in runner.inventory() if row["family"] == family)
            with tempfile.TemporaryDirectory() as temp:
                args = argparse.Namespace(family=family, case=[case], jar=Path("candidate.jar"), source_commit="source",
                                          evidence_dir=Path(temp), candidate_commit="abcdef0", candidate_sha256="b" * 64)
                with patch.object(runner, "verify_candidate", return_value={"candidate_commit": "abcdef0", "jar_sha256": "b" * 64}), \
                        patch.object(module, "run") as run:
                    runner.run(args)
                    self.assertEqual(run.call_args.args[0].case, [case])
                    self.assertEqual(args.startup_timeout, 420 if module is runner.settlement else 240)
                self.assertEqual(json.loads((Path(temp) / "integrated-pin.json").read_text())["candidate_commit"], "abcdef0")

    def test_loader_reuse_stops_on_first_failure_and_passes_original_pins(self):
        from contextlib import nullcontext
        for family, (module, cases) in runner.REUSE.items():
            with tempfile.TemporaryDirectory() as temp:
                args = argparse.Namespace(family=family, case=list(cases[:2]), session_dir=Path(temp), evidence_dir=Path(temp) / "new",
                                          jar=Path("candidate.jar"), source_commit="source", candidate_commit="abcdef0", candidate_sha256="b" * 64)
                with patch.object(runner.loader, "heavy_lock", return_value=nullcontext()), patch.object(runner, "verify_candidate"), \
                        patch.object(runner.fluid.native, "digest", return_value="b" * 64), \
                        patch.object(module, "run_cut", return_value={"status": "FAILED"}) as run:
                    with self.assertRaisesRegex(ValueError, "First contradictory"):
                        runner.run_loader(args, {"candidate_commit": "abcdef0", "jar_sha256": "b" * 64})
                    self.assertEqual(run.call_count, 1)
                    self.assertEqual(run.call_args.args[1], cases[0])
                    self.assertEqual(run.call_args.args[-1]["candidate_commit"], "abcdef0")
                self.assertEqual(json.loads((args.evidence_dir / "results.json").read_text())["status"], "FAILED")


if __name__ == "__main__":
    unittest.main()
