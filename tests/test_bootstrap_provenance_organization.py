"""Check discovery identity and composition of bootstrap provenance scenarios."""
import ast
import importlib
import inspect
import unittest
from pathlib import Path

from tests import test_validate_bootstrap_provenance as bootstrap
from tests.bootstrap_provenance_fixture import BootstrapProvenanceFixtureMixin


SCENARIO_MODULES = (
    "fixture_copy", "selected_commit", "git_objects", "input_bounds",
    "selected_snapshots", "cli", "pending_records", "history", "tree_metadata",
    "manifest_resources", "approved_records",
)


def flatten(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from flatten(item)
        else:
            yield item


class BootstrapProvenanceOrganizationTests(unittest.TestCase):
    def test_original_selections_and_discovery_identity(self) -> None:
        expected = (Path(__file__).parent / "fixtures" / "bootstrap_provenance_case_ids.txt").read_text(
            encoding="utf-8"
        ).splitlines()
        self.assertEqual(95, len(expected))
        self.assertEqual(len(expected), len(set(expected)))
        loader = unittest.TestLoader()
        cases = list(flatten(loader.loadTestsFromModule(bootstrap)))
        self.assertEqual(expected, [type(case).__name__ + "." + case._testMethodName for case in cases])
        self.assertEqual([bootstrap.__name__ + "." + name for name in expected], [case.id() for case in cases])
        self.assertEqual(
            {bootstrap.BootstrapProvenanceValidationTests, bootstrap.RealBootstrapProvenanceValidationTests},
            {type(case) for case in cases},
        )
        for case in cases:
            with self.subTest(selection=case.id()):
                selected = list(flatten(loader.loadTestsFromName(case.id())))
                self.assertEqual([case.id()], [item.id() for item in selected])
                self.assertIs(type(case), type(selected[0]))
        self.assertEqual([], loader.errors)

    def test_scenario_groups_are_non_runnable_and_have_no_lifecycle_overrides(self) -> None:
        lifecycle = {"setUp", "tearDown", "setUpClass", "tearDownClass", "run", "__init__"}
        groups = []
        methods = []
        loader = unittest.TestLoader()
        for name in SCENARIO_MODULES:
            module = importlib.import_module("tests.bootstrap_provenance_cases." + name)
            self.assertNotIn("load_tests", vars(module))
            self.assertEqual(0, loader.loadTestsFromModule(module).countTestCases())
            declared = [value for value in vars(module).values()
                        if inspect.isclass(value) and value.__module__ == module.__name__]
            self.assertEqual(1, len(declared))
            for cls in declared:
                with self.subTest(group=cls.__name__):
                    self.assertFalse(issubclass(cls, unittest.TestCase))
                    self.assertEqual(set(), lifecycle.intersection(vars(cls)))
                    self.assertIn(cls, bootstrap.BootstrapProvenanceValidationTests.__mro__)
                    names = [name for name in vars(cls) if name.startswith("test_")]
                    self.assertTrue(names)
                    methods.extend(names)
            if hasattr(module, "validator_module"):
                self.assertIs(bootstrap.validator_module, module.validator_module)
            groups.extend(declared)
        self.assertEqual(11, len(groups))
        self.assertEqual(94, len(methods))
        self.assertEqual(len(methods), len(set(methods)))
        self.assertEqual(sorted(methods), loader.getTestCaseNames(bootstrap.BootstrapProvenanceValidationTests))

    def test_fixture_lifecycle_bindings_and_class_size(self) -> None:
        cls = bootstrap.BootstrapProvenanceValidationTests
        self.assertNotIn("setUp", vars(cls))
        self.assertNotIn("setUpClass", vars(cls))
        self.assertIs(cls.setUp, BootstrapProvenanceFixtureMixin.setUp)
        self.assertIs(cls.setUpClass.__func__, BootstrapProvenanceFixtureMixin.setUpClass.__func__)
        self.assertLess(cls.__mro__.index(BootstrapProvenanceFixtureMixin), cls.__mro__.index(unittest.TestCase))
        self.assertFalse(issubclass(BootstrapProvenanceFixtureMixin, unittest.TestCase))
        self.assertEqual([], unittest.TestLoader().getTestCaseNames(BootstrapProvenanceFixtureMixin))
        for name, method in vars(BootstrapProvenanceFixtureMixin).items():
            if inspect.isfunction(method):
                self.assertIs(method, getattr(cls, name))
            elif isinstance(method, staticmethod):
                self.assertIs(method.__func__, getattr(cls, name))
        fixture_module = importlib.import_module(BootstrapProvenanceFixtureMixin.__module__)
        self.assertEqual(Path(bootstrap.__file__).parent, Path(fixture_module.__file__).parent)
        self.assertIs(bootstrap.validate_bootstrap_provenance, fixture_module.validate_bootstrap_provenance)
        self.assertIs(bootstrap.validate_bootstrap_provenance_at_commit, fixture_module.validate_bootstrap_provenance_at_commit)
        files = [Path(bootstrap.__file__), Path(fixture_module.__file__), Path(__file__)]
        files.extend(Path(importlib.import_module("tests.bootstrap_provenance_cases." + name).__file__)
                     for name in SCENARIO_MODULES)
        for path in files:
            for node in ast.parse(path.read_text(encoding="utf-8")).body:
                if isinstance(node, ast.ClassDef):
                    with self.subTest(path=path.name, cls=node.name):
                        self.assertLess(node.end_lineno - node.lineno + 1, 500)


if __name__ == "__main__":
    unittest.main()
