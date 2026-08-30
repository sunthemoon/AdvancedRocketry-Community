import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.check_clean_worktree import get_worktree_status


class CleanWorktreeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.repository = Path(self.temporary_directory.name)
        self.run_git("init", "--quiet")
        (self.repository / ".gitignore").write_text("build/\n", encoding="utf-8")
        (self.repository / "tracked.txt").write_text("baseline\n", encoding="utf-8")
        self.run_git("add", ".gitignore", "tracked.txt")
        self.run_git(
            "-c",
            "user.name=Worktree Test",
            "-c",
            "user.email=worktree-test@example.invalid",
            "-c",
            "commit.gpgSign=false",
            "commit",
            "--quiet",
            "-m",
            "Create test baseline",
        )

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def run_git(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ("git", *arguments),
            cwd=self.repository,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )

    def test_clean_repository_has_empty_status(self) -> None:
        self.assertEqual("", get_worktree_status(self.repository))

    def test_modified_tracked_file_is_reported(self) -> None:
        (self.repository / "tracked.txt").write_text("changed\n", encoding="utf-8")

        self.assertIn(" M tracked.txt", get_worktree_status(self.repository))

    def test_staged_change_is_reported(self) -> None:
        (self.repository / "tracked.txt").write_text("staged\n", encoding="utf-8")
        self.run_git("add", "tracked.txt")

        self.assertIn("M  tracked.txt", get_worktree_status(self.repository))

    def test_untracked_file_is_reported(self) -> None:
        generated_directory = self.repository / "generated" / "resources"
        generated_directory.mkdir(parents=True)
        (generated_directory / "data.json").write_text("{}\n", encoding="utf-8")

        self.assertIn(
            "?? generated/resources/data.json",
            get_worktree_status(self.repository),
        )

    def test_ignored_build_output_is_not_reported(self) -> None:
        build_directory = self.repository / "build"
        build_directory.mkdir()
        (build_directory / "report.txt").write_text("ignored\n", encoding="utf-8")

        self.assertEqual("", get_worktree_status(self.repository))


if __name__ == "__main__":
    unittest.main()
