"""Native snapshot comparison must pin identities, progress, balances and independent FE checkpoints."""
import importlib.util
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("w21c_native", SCRIPTS / "run_w21c_research_restart.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ResearchRestartComparisonTest(unittest.TestCase):
    def snapshot(self):
        return dict(device0="a", device1="b", device2="c", job="j", instance="i", source="a", processor="c",
                    phase="READY", survey_progress=1000, analysis_progress=199, analysis="RUNNING", balance=0,
                    fe0=0, fe1=3000, fe2=1020)

    def test_normal_restart_pins_every_required_value(self):
        before = self.snapshot()
        module.same(before, dict(before))
        for key in before:
            after = dict(before)
            after[key] = "changed"
            with self.subTest(key=key), self.assertRaises(module.SmokeError):
                module.same(before, after)

    def test_crash_accepts_only_the_two_actual_fe_checkpoints(self):
        before = self.snapshot()
        cut = dict(before, fe2=1000, analysis_progress=200, balance=120, phase="PROCESSED", analysis="CREDITED")
        module.cut_recovery(before, cut, dict(before))
        module.cut_recovery(before, cut, dict(before, fe2=1000))
        for after in (dict(before, fe2=5000), dict(before, job="different"), dict(before, balance=120)):
            with self.assertRaises(module.SmokeError):
                module.cut_recovery(before, cut, after)


if __name__ == "__main__":
    unittest.main()
