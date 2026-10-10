"""Check packet scenario discovery identity and preserving composition."""
import ast
import importlib
import inspect
import unittest
from pathlib import Path

from tests import test_prepare_v002_g0_review_packet as packet
from tests.packet_review_fixture import PacketReviewFixtureMixin


SCENARIO_MODULES = (
    "construction", "offline", "identity", "manifest", "git_objects",
    "filesystem", "cli",
)
FIXTURE_METHODS = {
    "setUpClass", "tearDownClass", "run_command", "seed_git",
    "ensure_pending_seed_state", "setUp", "git", "git_with_input",
    "commit_all", "generate", "verify_fast", "verify_content_only",
    "snapshot", "load_manifest", "write_manifest", "write_source_manifest",
    "approve_fixture",
}


def flatten(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from flatten(item)
        else:
            yield item


class PacketReviewOrganizationTests(unittest.TestCase):
    def test_original_selections_and_discovery_identity(self) -> None:
        expected = (Path(__file__).parent / "fixtures" / "packet_review_case_ids.txt").read_text(
            encoding="utf-8"
        ).splitlines()
        self.assertEqual(35, len(expected))
        self.assertEqual(sorted(set(expected)), expected)
        loader = unittest.TestLoader()
        cases = list(flatten(loader.loadTestsFromModule(packet)))
        self.assertEqual(expected, [type(case).__name__ + "." + case._testMethodName for case in cases])
        self.assertEqual([packet.__name__ + "." + name for name in expected], [case.id() for case in cases])
        self.assertEqual({packet.V002G0ReviewPacketTests}, {type(case) for case in cases})
        for case in cases:
            with self.subTest(selection=case.id()):
                selected = list(flatten(loader.loadTestsFromName(case.id())))
                self.assertEqual([case.id()], [item.id() for item in selected])
                self.assertIs(type(case), type(selected[0]))
        self.assertEqual([], loader.errors)

    def test_scenarios_are_non_runnable_and_have_no_lifecycle_overrides(self) -> None:
        lifecycle = {"setUp", "tearDown", "setUpClass", "tearDownClass", "run", "__init__"}
        loader = unittest.TestLoader()
        methods = []
        groups = []
        for name in SCENARIO_MODULES:
            module = importlib.import_module("tests.packet_review_cases." + name)
            self.assertNotIn("load_tests", vars(module))
            self.assertEqual(0, loader.loadTestsFromModule(module).countTestCases())
            declared = [value for value in vars(module).values()
                        if inspect.isclass(value) and value.__module__ == module.__name__]
            self.assertEqual(1, len(declared))
            for cls in declared:
                with self.subTest(group=cls.__name__):
                    self.assertFalse(issubclass(cls, unittest.TestCase))
                    self.assertEqual(set(), lifecycle.intersection(vars(cls)))
                    self.assertIn(cls, packet.V002G0ReviewPacketTests.__mro__)
                    names = [name for name in vars(cls) if name.startswith("test_")]
                    self.assertTrue(names)
                    methods.extend(names)
            groups.extend(declared)
        self.assertEqual(7, len(groups))
        self.assertEqual(35, len(methods))
        self.assertEqual(len(methods), len(set(methods)))
        self.assertEqual(sorted(methods), loader.getTestCaseNames(packet.V002G0ReviewPacketTests))

    def test_fixture_global_bindings_root_and_class_size(self) -> None:
        cls = packet.V002G0ReviewPacketTests
        self.assertEqual(FIXTURE_METHODS, {
            name for name, value in vars(PacketReviewFixtureMixin).items()
            if inspect.isfunction(value) or isinstance(value, (staticmethod, classmethod))
        })
        self.assertFalse(issubclass(PacketReviewFixtureMixin, unittest.TestCase))
        self.assertEqual([], unittest.TestLoader().getTestCaseNames(PacketReviewFixtureMixin))
        self.assertLess(cls.__mro__.index(PacketReviewFixtureMixin), cls.__mro__.index(unittest.TestCase))
        for name in FIXTURE_METHODS:
            with self.subTest(helper=name):
                self.assertNotIn(name, vars(cls))
                descriptor = vars(PacketReviewFixtureMixin)[name]
                if isinstance(descriptor, classmethod):
                    self.assertIs(descriptor.__func__, getattr(cls, name).__func__)
                elif isinstance(descriptor, staticmethod):
                    self.assertIs(descriptor.__func__, getattr(cls, name))
                else:
                    self.assertIs(descriptor, getattr(cls, name))
        fixture = importlib.import_module(PacketReviewFixtureMixin.__module__)
        self.assertEqual(Path(packet.__file__).parent, Path(fixture.__file__).parent)
        self.assertEqual(Path(packet.__file__).resolve().parents[1], Path(fixture.__file__).resolve().parents[1])
        modules = [fixture]
        modules.extend(importlib.import_module("tests.packet_review_cases." + name)
                       for name in SCENARIO_MODULES)
        for module in modules:
            tree = ast.parse(Path(module.__file__).read_text(encoding="utf-8"))
            for node in tree.body:
                if not isinstance(node, (ast.Import, ast.ImportFrom)):
                    continue
                for alias in node.names:
                    name = alias.asname or (alias.name.split(".")[0] if isinstance(node, ast.Import) else alias.name)
                    with self.subTest(module=module.__name__, binding=name):
                        self.assertIs(getattr(packet, name), getattr(module, name))
        files = [Path(packet.__file__), Path(__file__)]
        files.extend(Path(module.__file__) for module in modules)
        for path in files:
            for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
                if isinstance(node, ast.ClassDef):
                    with self.subTest(path=path.name, cls=node.name):
                        self.assertLess(node.end_lineno - node.lineno + 1, 500)


if __name__ == "__main__":
    unittest.main()
