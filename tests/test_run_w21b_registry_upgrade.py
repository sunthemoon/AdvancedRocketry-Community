"""W21b review F8: the native harness ties its evidence to a clean HEAD and a jar it built; no server launch."""
import hashlib
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts import run_w21b_registry_upgrade as harness


def git(repository, *args):
    return subprocess.run(("git", *args), cwd=repository, check=True, capture_output=True, text=True).stdout


class SourceIdentityTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.repository = Path(self.temporary.name)
        git(self.repository, "init", "-q")
        git(self.repository, "config", "user.email", "w21bf@example.invalid")
        git(self.repository, "config", "user.name", "W21bF")
        git(self.repository, "config", "core.autocrlf", "false")
        (self.repository / ".gitignore").write_text("build/\n", encoding="ascii")
        (self.repository / "source.txt").write_text("tested\n", encoding="ascii")
        git(self.repository, "add", ".")
        git(self.repository, "commit", "-q", "-m", "fixture")
        self.head = git(self.repository, "rev-parse", "HEAD").strip()
        self.jar = self.repository / "build" / "libs" / "candidate.jar"
        self.log = self.repository / "build" / "build.log"

    def tearDown(self):
        self.temporary.cleanup()

    def test_records_head_of_a_clean_tree_and_accepts_its_prefix(self):
        expected = {"tested_commit": self.head, "worktree_clean": True}
        self.assertEqual(expected, harness.source_identity(self.repository))
        self.assertEqual(expected, harness.source_identity(self.repository, self.head[:12]))
        self.assertEqual(expected, harness.source_identity(self.repository, self.head.upper()))

    def test_refuses_another_commit_a_short_prefix_and_a_dirty_or_untracked_tree(self):
        for claimed in ("0" * 40, self.head[:6]):
            with self.assertRaises(harness.SmokeError):
                harness.source_identity(self.repository, claimed)
        (self.repository / "source.txt").write_text("changed\n", encoding="ascii")
        with self.assertRaises(harness.SmokeError):
            harness.source_identity(self.repository)
        git(self.repository, "checkout", "-q", "--", "source.txt")
        (self.repository / "untracked.txt").write_text("new\n", encoding="ascii")
        with self.assertRaises(harness.SmokeError):
            harness.source_identity(self.repository)

    def test_build_records_the_hash_of_the_jar_it_wrote(self):
        built = harness.build_candidate(self.repository, self.fake_build(
            "pathlib.Path('build/libs/candidate.jar').write_bytes(b'candidate')"), self.jar, self.log)
        self.assertEqual(hashlib.sha256(b"candidate").hexdigest(), built["candidate_jar_sha256_at_build"])
        self.assertTrue(built["candidate_built_by_harness"])
        self.assertEqual("build.log", built["candidate_build_log"])

    def test_build_refuses_failure_a_stale_jar_and_a_changed_tree(self):
        with self.assertRaises(harness.SmokeError):
            harness.build_candidate(self.repository, self.fake_build("raise SystemExit(3)"), self.jar, self.log)
        self.jar.write_bytes(b"stale")
        old = self.jar.stat().st_mtime - 60
        os.utime(self.jar, (old, old))
        with self.assertRaises(harness.SmokeError):
            harness.build_candidate(self.repository, self.fake_build("pass"), self.jar, self.log)
        with self.assertRaises(harness.SmokeError):
            harness.build_candidate(self.repository, self.fake_build(
                "pathlib.Path('build/libs/candidate.jar').write_bytes(b'x'); "
                "pathlib.Path('source.txt').write_text('changed')"), self.jar, self.log)

    def test_default_build_uses_the_offline_jar_task_of_the_wrapper(self):
        command = harness.default_build_command(self.repository)
        self.assertTrue(Path(command[0]).name.startswith("gradlew"))
        self.assertEqual(["--offline", "--console=plain", "jar"], command[1:])

    def fake_build(self, statement):
        self.jar.parent.mkdir(parents=True, exist_ok=True)
        return [sys.executable, "-I", "-c", "import pathlib; " + statement]


if __name__ == "__main__":
    unittest.main()
