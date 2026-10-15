"""Check discovery identity and lifecycle composition of manual evidence cases."""
import importlib
import inspect
import unittest
from pathlib import Path

from tests import test_collect_v002_manual_evidence as manual
from tests.manual_evidence_cases.fixture_operations import FixtureOperations
from tests.manual_evidence_cases.session_fixture import SessionFixture
from tests.manual_evidence_fixture import ManualRepositoryFixtureMixin


def flatten(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from flatten(item)
        else:
            yield item


class ManualEvidenceOrganizationTests(unittest.TestCase):
    def test_original_selections_and_discovery_identity(self) -> None:
        expected = (Path(__file__).parent / "fixtures" / "manual_evidence_case_ids.txt").read_text(
            encoding="utf-8"
        ).splitlines()
        self.assertEqual(138, len(expected))
        self.assertEqual(len(expected), len(set(expected)))
        loader = unittest.TestLoader()
        cases = list(flatten(loader.loadTestsFromModule(manual)))
        self.assertEqual(expected, [type(case).__name__ + "." + case._testMethodName for case in cases])
        self.assertEqual([manual.__name__ + "." + name for name in expected], [case.id() for case in cases])
        self.assertEqual({manual.CollectorCliTests, manual.ManualEvidenceTests}, {type(case) for case in cases})
        for case in cases:
            with self.subTest(selection=case.id()):
                selected = list(flatten(loader.loadTestsFromName(case.id())))
                self.assertEqual([case.id()], [item.id() for item in selected])
                self.assertIs(type(case), type(selected[0]))
        self.assertEqual([], loader.errors)

    def test_scenario_mixins_are_non_runnable_and_have_no_lifecycle_overrides(self) -> None:
        lifecycle = {"setUp", "tearDown", "setUpClass", "tearDownClass", "run", "__init__"}
        modules = (
            "session", "profiles", "source_logs", "payload_bounds", "server_audits",
            "mismatch_receipts", "readiness", "identity_privacy", "mismatch_runtime",
            "publication", "archive_identity",
        )
        classes = []
        methods = []
        loader = unittest.TestLoader()
        for name in modules:
            module = importlib.import_module("tests.manual_evidence_cases." + name)
            self.assertNotIn("load_tests", vars(module))
            self.assertEqual(0, loader.loadTestsFromModule(module).countTestCases())
            declared = [value for value in vars(module).values()
                        if inspect.isclass(value) and value.__module__ == module.__name__]
            self.assertTrue(declared)
            for cls in declared:
                with self.subTest(group=cls.__name__):
                    self.assertFalse(issubclass(cls, unittest.TestCase))
                    self.assertEqual(set(), lifecycle.intersection(vars(cls)))
                    self.assertIn(cls, manual.ManualEvidenceTests.__mro__)
                    names = [name for name in vars(cls) if name.startswith("test_")]
                    self.assertTrue(names)
                    methods.extend(names)
            classes.extend(declared)
        self.assertEqual(14, len(classes))
        self.assertEqual(137, len(methods))
        self.assertEqual(len(methods), len(set(methods)))
        self.assertEqual(sorted(methods), loader.getTestCaseNames(manual.ManualEvidenceTests))

    def test_fixture_lifecycle_composition(self) -> None:
        cls = manual.ManualEvidenceTests
        self.assertIn("setUp", vars(cls))
        self.assertNotIn("setUpClass", vars(cls))
        self.assertEqual("advancedrocketry-community-1.20.1-0.0.2-dev.jar", cls.artifact_name)
        self.assertIs(cls.ready_session, SessionFixture.ready_session)
        self.assertIs(cls.copy_repository_fixture, ManualRepositoryFixtureMixin.copy_repository_fixture)
        self.assertIs(cls.setUpClass.__func__, ManualRepositoryFixtureMixin.setUpClass.__func__)
        self.assertLess(cls.__mro__.index(ManualRepositoryFixtureMixin), cls.__mro__.index(unittest.TestCase))
        for helper in (SessionFixture, FixtureOperations):
            with self.subTest(helper=helper.__name__):
                self.assertFalse(issubclass(helper, unittest.TestCase))
                self.assertEqual(set(), {"setUp", "tearDown", "setUpClass", "tearDownClass"}.intersection(vars(helper)))
                self.assertEqual([], unittest.TestLoader().getTestCaseNames(helper))
        for name, method in vars(FixtureOperations).items():
            if inspect.isfunction(method):
                self.assertIs(method, getattr(cls, name))


if __name__ == "__main__":
    unittest.main()
