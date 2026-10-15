from __future__ import annotations

import io
import stat
import sys
import unittest
from pathlib import Path
from unittest import mock

from tests import final_review_input_fixture as fixture_module
from tests.final_review_input_fixture import (
    FinalReviewInputFixtureMixin,
    GitFixture,
    SOURCE_TOOL,
    TOOL_PATH,
)


def repository_snapshot(root: Path) -> dict[str, tuple[bytes | None, int]]:
    return {
        path.relative_to(root).as_posix(): (
            path.read_bytes() if path.is_file() else None,
            stat.S_IMODE(path.stat().st_mode),
        )
        for path in sorted(root.rglob("*"))
    }


class FinalReviewInputFixtureTests(FinalReviewInputFixtureMixin, unittest.TestCase):
    def test_normal_copy_preserves_raw_repository_without_shared_files(self) -> None:
        seed = self._final_input_repository_seed
        expected = repository_snapshot(seed.root)
        with mock.patch.object(sys, "dont_write_bytecode", True):
            with mock.patch.object(
                GitFixture, "_build_repository", side_effect=AssertionError("rebuild")
            ):
                fixture = GitFixture(self)
        self.assertEqual(expected, repository_snapshot(fixture.root))
        self.assertNotEqual(seed.root, fixture.root)
        self.assertEqual(seed.base_commit, fixture.base_commit)
        self.assertEqual(seed.selected_commit, fixture.selected_commit)
        self.assertFalse((fixture.root / ".git/objects/info/alternates").exists())
        for relative, (content, _) in expected.items():
            if content is not None:
                original, copied = seed.root / relative, fixture.root / relative
                self.assertFalse(original.samefile(copied), relative)
                self.assertEqual(1, copied.stat().st_nlink, relative)
        self.assertEqual(seed.selected_commit, fixture._git("rev-parse", "HEAD").strip())
        self.assertEqual("", fixture._git("status", "--porcelain"))
        self.assertEqual(
            [seed.selected_commit, seed.base_commit],
            fixture._git("rev-list", "HEAD").splitlines(),
        )

    def test_repository_and_module_mutations_are_case_local(self) -> None:
        seed = self._final_input_repository_seed
        with mock.patch.object(sys, "dont_write_bytecode", True):
            first, second = GitFixture(self), GitFixture(self)
        seed_before = repository_snapshot(seed.root)
        second_before = repository_snapshot(second.root)
        original_limit = second.tool.MAX_HISTORY_PARENT_EDGES
        first._write(Path("src/main/java/example/Example.java"), b"mutated\n")
        first._write(Path(".git/config"), b"mutated config\n")
        first._write(Path(".git/index"), b"mutated index\n")
        first._write(Path(".git/HEAD"), b"mutated HEAD\n")
        first_object = next(
            path for path in (first.root / ".git/objects").rglob("*") if path.is_file()
        )
        first_object.chmod(first_object.stat().st_mode | stat.S_IWUSR)
        first_object.write_bytes(b"mutated object\n")
        first.tool.MAX_HISTORY_PARENT_EDGES = -1
        self.assertEqual(seed_before, repository_snapshot(seed.root))
        self.assertEqual(second_before, repository_snapshot(second.root))
        self.assertEqual(original_limit, second.tool.MAX_HISTORY_PARENT_EDGES)
        self.assertNotEqual(first.module_name, second.module_name)
        self.assertIsNot(first.tool, second.tool)
        self.assertIs(sys.modules[first.module_name], first.tool)
        self.assertIs(sys.modules[second.module_name], second.tool)
        self.assertEqual(second.base_commit, second.tool.BASE_COMMIT)
        self.assertEqual(seed.selected_commit, second._git("rev-parse", "HEAD").strip())

    def test_tool_after_base_uses_original_construction(self) -> None:
        seed = self._final_input_repository_seed
        before = repository_snapshot(seed.root)
        original = GitFixture._build_repository
        calls = []

        def build(fixture, *, tool_after_base):
            calls.append(tool_after_base)
            return original(fixture, tool_after_base=tool_after_base)

        with mock.patch.object(GitFixture, "_build_repository", build):
            fixture = GitFixture(self, tool_after_base=True)
        self.assertEqual([True], calls)
        self.assertNotEqual(seed.base_commit, fixture.base_commit)
        self.assertEqual(
            "", fixture._git("ls-tree", fixture.base_commit, "--", TOOL_PATH.as_posix())
        )
        configured = SOURCE_TOOL.read_text(encoding="utf-8").replace(
            'BASE_COMMIT = "86b9db01b1cb4c8b8f673590baf1dc185d1716b3"',
            f'BASE_COMMIT = "{fixture.base_commit}"',
            1,
        )
        self.assertEqual(configured, (fixture.root / TOOL_PATH).read_text(encoding="utf-8"))
        self.assertEqual(
            configured, fixture._git("show", f"{fixture.selected_commit}:{TOOL_PATH.as_posix()}")
        )
        self.assertEqual(
            [fixture.selected_commit, fixture.base_commit],
            fixture._git("rev-list", "HEAD").splitlines(),
        )
        self.assertEqual(before, repository_snapshot(seed.root))

    def test_failed_class_construction_cleans_partial_seed(self) -> None:
        roots, bodies = [], []

        def fail(fixture, *, tool_after_base):
            roots.append(fixture.root)
            fixture._write(Path("partial"), b"partial seed")
            raise RuntimeError("class construction failure")

        class FailingClass(FinalReviewInputFixtureMixin, unittest.TestCase):
            def test_body(self):
                bodies.append(True)

        with mock.patch.object(GitFixture, "_build_repository", fail):
            result = unittest.TextTestRunner(stream=io.StringIO()).run(
                unittest.defaultTestLoader.loadTestsFromTestCase(FailingClass)
            )
        self.assertEqual([], bodies)
        self.assertEqual(0, result.testsRun)
        self.assertEqual(1, len(result.errors))
        self.assertEqual([], result.failures)
        self.assertIn("class construction failure", result.errors[0][1])
        self.assertEqual(1, len(roots))
        self.assertFalse(roots[0].exists())

    def test_failed_case_copy_cleans_partial_case_and_class_seed(self) -> None:
        sources, destinations, bodies = [], [], []

        def fail(source, destination, **kwargs):
            sources.append(Path(source))
            destinations.append(Path(destination))
            (Path(destination) / "partial").write_bytes(b"partial copy")
            raise RuntimeError("case copy failure")

        class FailingCase(FinalReviewInputFixtureMixin, unittest.TestCase):
            def test_body(self):
                GitFixture(self)
                bodies.append(True)

        with mock.patch.object(fixture_module.shutil, "copytree", fail):
            result = unittest.TextTestRunner(stream=io.StringIO()).run(
                unittest.defaultTestLoader.loadTestsFromTestCase(FailingCase)
            )
        self.assertEqual([], bodies)
        self.assertEqual(1, result.testsRun)
        self.assertEqual(1, len(result.errors))
        self.assertEqual([], result.failures)
        self.assertIn("case copy failure", result.errors[0][1])
        self.assertEqual(1, len(sources))
        self.assertEqual(1, len(destinations))
        self.assertFalse(sources[0].exists())
        self.assertFalse(destinations[0].exists())


if __name__ == "__main__":
    unittest.main()
