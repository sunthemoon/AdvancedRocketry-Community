from __future__ import annotations

import io
import stat
import sys
import types
import unittest
import uuid
from pathlib import Path
from unittest import mock

from tests import test_validate_v002_final_g0_review as fixture_module


GitFixture = fixture_module.GitFixture


def snapshot(root: Path) -> dict[str, tuple[bytes | None, int]]:
    return {
        path.relative_to(root).as_posix(): (
            path.read_bytes() if path.is_file() else None,
            stat.S_IMODE(path.stat().st_mode),
        )
        for path in sorted(root.rglob("*"))
    }


class FinalG0ReviewFixtureTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        super().setUpClass()
        fixture_module.install_repository_seed((cls,), cls.addClassCleanup)

    def test_copy_preserves_raw_repository_and_physical_isolation(self) -> None:
        seed = self._final_review_repository_seed
        expected = snapshot(seed.root)
        with mock.patch.object(
            GitFixture, "_build_repository", side_effect=AssertionError("rebuild")
        ):
            fixture = GitFixture(self)
        self.assertEqual(expected, snapshot(fixture.root))
        self.assertNotEqual(seed.root, fixture.root)
        self.assertEqual(seed.selected, fixture.selected)
        self.assertEqual(seed.selected_tree, fixture.selected_tree)
        self.assertFalse((fixture.root / ".git/objects/info/alternates").exists())
        for relative, (content, _) in expected.items():
            if content is not None:
                original, copied = seed.root / relative, fixture.root / relative
                self.assertFalse(original.samefile(copied), relative)
                self.assertEqual(1, copied.stat().st_nlink, relative)
        self.assertEqual(seed.selected, fixture.rev_parse("HEAD"))
        self.assertEqual(seed.selected_tree, fixture.rev_parse("HEAD^{tree}"))
        self.assertEqual(b"", fixture._git("status", "--porcelain"))
        self.assertEqual([seed.selected], fixture._git("rev-list", "HEAD").decode().splitlines())

    def test_mutable_repository_and_reports_are_case_local(self) -> None:
        seed = self._final_review_repository_seed
        first, second = GitFixture(self), GitFixture(self)
        seed_before, second_before = snapshot(seed.root), snapshot(second.root)
        original_report = second.report()
        first_document, second_document = first.pending_document(), second.pending_document()
        self.assertIsNot(first_document, second_document)
        first_document["final_g0_source_resource_review"]["reviewer"] = "mutated"
        self.assertIsNone(second_document["final_g0_source_resource_review"]["reviewer"])
        first.write("README.md", b"mutated source\n")
        first.write(".git/config", b"mutated config\n")
        first.write(".git/index", b"mutated index\n")
        first.write(".git/HEAD", b"mutated HEAD\n")
        first_object = next(
            path for path in (first.root / ".git/objects").rglob("*") if path.is_file()
        )
        first_object.chmod(first_object.stat().st_mode | stat.S_IWUSR)
        first_object.write_bytes(b"mutated object\n")
        first.selected, first.selected_tree = "0" * 40, "0" * 40
        self.assertEqual(seed_before, snapshot(seed.root))
        self.assertEqual(second_before, snapshot(second.root))
        self.assertEqual(original_report, second.report())
        self.assertEqual(seed.selected, second.rev_parse("HEAD"))

    def test_unseeded_caller_retains_original_construction(self) -> None:
        unseeded = unittest.TestCase()
        self.addCleanup(unseeded.doCleanups)
        original = GitFixture._build_repository
        calls = []

        def build(fixture):
            calls.append(fixture.root)
            return original(fixture)

        with mock.patch.object(GitFixture, "_build_repository", build):
            fixture = GitFixture(unseeded)
        self.assertEqual([fixture.root], calls)
        seed = self._final_review_repository_seed
        self.assertEqual(seed.selected, fixture.selected)
        self.assertEqual(seed.selected_tree, fixture.selected_tree)
        self.assertEqual(b"", fixture._git("status", "--porcelain"))
        self.assertEqual(b"# Fixture\n\nUnofficial project.\n", (fixture.root / "README.md").read_bytes())

    def run_module_case(self, case_class):
        name = "final_g0_fixture_cleanup_" + uuid.uuid4().hex
        module = types.ModuleType(name)
        case_class.__module__ = name
        module.setUpModule = lambda: fixture_module.install_repository_seed(
            (case_class,), unittest.addModuleCleanup
        )
        sys.modules[name] = module
        self.addCleanup(sys.modules.pop, name, None)
        return unittest.TextTestRunner(stream=io.StringIO()).run(
            unittest.defaultTestLoader.loadTestsFromTestCase(case_class)
        )

    def test_failed_module_seed_cleans_partial_repository(self) -> None:
        roots, bodies = [], []

        def fail(fixture):
            roots.append(fixture.root)
            fixture.write("partial", b"partial seed")
            raise RuntimeError("module seed failure")

        class FailingSeed(unittest.TestCase):
            def test_body(self):
                bodies.append(True)

        with mock.patch.object(GitFixture, "_build_repository", fail):
            result = self.run_module_case(FailingSeed)
        self.assertEqual([], bodies)
        self.assertEqual(0, result.testsRun)
        self.assertEqual(1, len(result.errors))
        self.assertEqual([], result.failures)
        self.assertIn("module seed failure", result.errors[0][1])
        self.assertEqual(1, len(roots))
        self.assertFalse(roots[0].exists())
        self.assertNotIn("_final_review_repository_seed", FailingSeed.__dict__)

    def test_partial_case_copy_cleans_case_and_module_seed(self) -> None:
        sources, destinations, bodies = [], [], []

        def fail(source, destination, **kwargs):
            sources.append(Path(source))
            destinations.append(Path(destination))
            (Path(destination) / "partial").write_bytes(b"partial copy")
            raise RuntimeError("case copy failure")

        class FailingCopy(unittest.TestCase):
            def test_body(self):
                GitFixture(self)
                bodies.append(True)

        with mock.patch.object(fixture_module.shutil, "copytree", fail):
            result = self.run_module_case(FailingCopy)
        self.assertEqual([], bodies)
        self.assertEqual(1, result.testsRun)
        self.assertEqual(1, len(result.errors))
        self.assertEqual([], result.failures)
        self.assertIn("case copy failure", result.errors[0][1])
        self.assertEqual(1, len(sources))
        self.assertEqual(1, len(destinations))
        self.assertFalse(sources[0].exists())
        self.assertFalse(destinations[0].exists())
        self.assertNotIn("_final_review_repository_seed", FailingCopy.__dict__)


if __name__ == "__main__":
    unittest.main()
