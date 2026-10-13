"""Native snapshot comparison must pin identities, progress, balances and independent FE checkpoints."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
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


def git(repository, *args):
    return subprocess.run(("git", *args), cwd=repository, check=True, capture_output=True, text=True).stdout


class SourceIdentityWiringTest(unittest.TestCase):
    """W21c review L4: the run is tied to HEAD of a clean tree before it creates anything; no server launch."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.repository = self.root / "repository"
        self.repository.mkdir()
        git(self.repository, "init", "-q")
        git(self.repository, "config", "user.email", "w21cf@example.invalid")
        git(self.repository, "config", "user.name", "W21cF")
        git(self.repository, "config", "core.autocrlf", "false")
        (self.repository / "source.txt").write_text("tested\n", encoding="ascii")
        git(self.repository, "add", ".")
        git(self.repository, "commit", "-q", "-m", "fixture")
        self.head = git(self.repository, "rev-parse", "HEAD").strip()

    def tearDown(self):
        self.temporary.cleanup()

    def arguments(self, *extra):
        return module.parser().parse_args([
            "--template", str(self.root / "template"), "--candidate-jar", str(self.root / "candidate.jar"),
            "--work-dir", str(self.root / "work"), "--evidence-dir", str(self.root / "evidence"),
            "--repository", str(self.repository), *extra])

    def assert_refused_before_creating_anything(self, args):
        with self.assertRaises(module.SmokeError):
            module.run(args, {})
        self.assertFalse((self.root / "work").exists() or (self.root / "evidence").exists())

    def test_tested_commit_is_optional_and_the_build_is_opt_in(self):
        args = module.parser().parse_args(["--template", "t", "--candidate-jar", "j", "--work-dir", "w", "--evidence-dir", "e"])
        self.assertIsNone(args.tested_commit)
        self.assertFalse(args.build_candidate)
        self.assertEqual(module.ROOT, args.repository)
        self.assertTrue(self.arguments("--build-candidate").build_candidate)

    def test_refuses_an_uncommitted_or_untracked_change(self):
        (self.repository / "source.txt").write_text("changed\n", encoding="ascii")
        self.assert_refused_before_creating_anything(self.arguments())
        git(self.repository, "checkout", "-q", "--", "source.txt")
        (self.repository / "untracked.txt").write_text("new\n", encoding="ascii")
        self.assert_refused_before_creating_anything(self.arguments())

    def test_refuses_a_claimed_commit_that_is_not_head(self):
        self.assert_refused_before_creating_anything(self.arguments("--tested-commit", "0" * 40))
        self.assertEqual({"tested_commit": self.head, "worktree_clean": True},
                         module.source_identity(self.repository, self.head[:12]))


if __name__ == "__main__":
    unittest.main()
