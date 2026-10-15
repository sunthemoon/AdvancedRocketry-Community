"""Downgrade runner oracles; no native server is started by these tests."""
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from scripts import run_v180_first_event_downgrade_cuts as downgrade


class DowngradeRunnerTest(unittest.TestCase):
    def exercise(self, wrong=False):
        with tempfile.TemporaryDirectory() as folder:
            evidence = Path(folder)
            rows = []
            for case in downgrade.CASES:
                moon = case.startswith("FM")
                diagnostic = "ARCE-BETA-2001" if moon else "ARCE-BETA-2000"
                reason = "uses future schema 3" if moon else "has an invalid format epoch"
                starts = []
                for start in range(3):
                    log = f"{case}-{start}.log"
                    (evidence / log).write_text(reason, encoding="utf-8")
                    starts.append(dict(diagnostic=diagnostic, log=log))
                rows.append(dict(case=case, starts=starts))
            if wrong: rows[-1]["starts"][-1]["diagnostic"] = "ARCE-BETA-2002"
            args = SimpleNamespace(evidence_dir=evidence, harness_commit="harness")
            with patch.object(downgrade.cuts, "run", return_value=dict(status="PASS", cases=rows)):
                if wrong:
                    with self.assertRaisesRegex(downgrade.cuts.smoke.SmokeError, "wrong refusal diagnostic"):
                        downgrade.run(args)
                else:
                    self.assertEqual("PASS", downgrade.run(args)["status"])
            stored = json.loads((evidence / "results.json").read_text())
            self.assertEqual("FAILED" if wrong else "PASS", stored["status"])
            self.assertEqual("harness", stored["fixture_commit"])

    def test_four_rows_require_the_exact_old_schema_or_epoch_refusal(self): self.exercise()

    def test_an_unrelated_refusal_is_not_a_downgrade_pass(self): self.exercise(wrong=True)


if __name__ == "__main__": unittest.main()
