import io
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tests.manual_evidence_fixture import ManualRepositoryFixtureMixin


class ManualRepositoryFixtureTests(unittest.TestCase):
    def fixture_class(self):
        class FixtureCase(ManualRepositoryFixtureMixin, unittest.TestCase):
            artifact_name = "advancedrocketry-community-1.20.1-0.0.2-dev.jar"

        FixtureCase.setUpClass()
        self.addCleanup(FixtureCase.doClassCleanups)
        return FixtureCase

    def new_root(self) -> Path:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        return Path(directory.name)

    @staticmethod
    def git(root: Path, *arguments: str) -> str:
        return subprocess.run(
            ["git", "-C", str(root), *arguments],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()

    def test_copy_preserves_raw_files_modes_and_complete_history(self) -> None:
        fixture = self.fixture_class()
        seed = Path(fixture._repository_fixture.name)
        root = self.new_root()
        observed_commit = fixture().copy_repository_fixture(root)
        expected = sorted(p.relative_to(seed) for p in seed.rglob("*") if p.is_file())
        observed = sorted(p.relative_to(root) for p in root.rglob("*") if p.is_file())
        self.assertEqual(expected, observed)
        for relative in expected:
            with self.subTest(path=relative.as_posix()):
                self.assertEqual((seed / relative).read_bytes(), (root / relative).read_bytes())
                self.assertEqual((seed / relative).stat().st_mode, (root / relative).stat().st_mode)
                self.assertFalse(os.path.samefile(seed / relative, root / relative))
        self.assertEqual(fixture._repository_fixture_commit, observed_commit)
        self.assertEqual(observed_commit, self.git(root, "rev-parse", "HEAD"))
        self.assertEqual("", self.git(root, "status", "--porcelain=v1", "--untracked-files=all"))
        self.assertFalse((root / ".git" / "objects" / "info" / "alternates").exists())

    def test_copies_isolate_files_index_config_and_head_mutations(self) -> None:
        fixture = self.fixture_class()
        seed = Path(fixture._repository_fixture.name)
        baseline = {p.relative_to(seed): p.read_bytes() for p in seed.rglob("*") if p.is_file()}
        first, second = self.new_root(), self.new_root()
        fixture().copy_repository_fixture(first)
        fixture().copy_repository_fixture(second)
        (first / "README.md").write_bytes(b"private changed README\n")
        self.git(first, "config", "fixture.private", "changed")
        self.git(first, "add", "README.md")
        self.assertNotEqual((first / ".git" / "index").read_bytes(), (second / ".git" / "index").read_bytes())
        self.assertNotEqual((first / ".git" / "config").read_bytes(), (second / ".git" / "config").read_bytes())
        self.git(first, "-c", "user.name=Fixture Isolation", "-c", "user.email=fixture@example.invalid",
                 "commit", "--quiet", "-m", "private case mutation")
        self.assertNotEqual(fixture._repository_fixture_commit, self.git(first, "rev-parse", "HEAD"))
        self.assertEqual(fixture._repository_fixture_commit, self.git(second, "rev-parse", "HEAD"))
        self.assertEqual(baseline, {p.relative_to(seed): p.read_bytes() for p in seed.rglob("*") if p.is_file()})
        self.assertEqual(baseline, {p.relative_to(second): p.read_bytes() for p in second.rglob("*") if p.is_file()})

    def test_failed_class_construction_runs_registered_unittest_cleanup(self) -> None:
        prepared_roots = []

        def fail_construction(root: Path, artifact_name: str) -> str:
            prepared_roots.append(root)
            (root / "partial-fixture").write_bytes(b"partial fixture")
            raise RuntimeError("fixture construction failure")

        class FailureCase(ManualRepositoryFixtureMixin, unittest.TestCase):
            artifact_name = "fixture.jar"

            def test_unused(self) -> None:
                self.fail("body must not run after failed class setup")

        with patch("tests.manual_evidence_fixture._build_repository_fixture", side_effect=fail_construction):
            result = unittest.TextTestRunner(stream=io.StringIO()).run(
                unittest.TestSuite([FailureCase("test_unused")])
            )
        self.assertEqual(0, result.testsRun)
        self.assertEqual(1, len(result.errors))
        self.assertEqual([], result.failures)
        self.assertEqual(1, len(prepared_roots))
        self.assertFalse(prepared_roots[0].exists())
