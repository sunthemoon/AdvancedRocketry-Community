from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import validate_bootstrap_provenance as validator
from tests.test_bootstrap_git_object_session import FakeProcess, frame, object_id


class RealCommitProbeTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.git("init", "--quiet")
        self.git(
            "-c", "user.name=Commit Probe", "-c", "user.email=probe@example.invalid",
            "commit", "--quiet", "--allow-empty", "-m", "probe commit",
        )
        self.commit = self.git("rev-parse", "HEAD").decode("ascii").strip()

    def git(self, *arguments: str, content: bytes | None = None) -> bytes:
        result = subprocess.run(
            [validator._git_executable(self.root), "-C", str(self.root), *arguments],
            input=content, capture_output=True, check=True, timeout=15,
            env=validator._git_environment(),
        )
        self.assertLessEqual(max(len(result.stdout), len(result.stderr)), 262144)
        return result.stdout

    def test_exact_commit_direct_read_needs_no_type_only_probe(self) -> None:
        errors: list[str] = []
        with patch.object(validator, "_run_git", wraps=validator._run_git) as metadata:
            with patch.object(validator, "_git_object_sha1", wraps=validator._git_object_sha1) as digest:
                result = validator._git_commit_exists(self.root, self.commit, "commit", errors)
        self.assertTrue(result)
        self.assertEqual([], errors)
        self.assertEqual(0, metadata.call_count)
        self.assertEqual(1, digest.call_count)

    def test_repeated_session_probes_reverify_without_a_result_cache(self) -> None:
        requests: list[int] = []

        def operation() -> tuple[list[str], list[bool]]:
            errors: list[str] = []
            results = [
                validator._git_commit_exists(self.root, self.commit, "commit", errors)
                for _ in range(2)
            ]
            requests.append(validator._git_object_scope.session.requests)
            return errors, results

        with patch.object(validator, "_run_git", wraps=validator._run_git) as metadata:
            with patch.object(validator.subprocess, "Popen", wraps=subprocess.Popen) as launch:
                with patch.object(validator, "_git_object_sha1", wraps=validator._git_object_sha1) as digest:
                    errors, results = validator._with_git_object_session(self.root, operation)
        self.assertEqual([], errors)
        self.assertEqual([True, True], results)
        self.assertEqual([2], requests)
        self.assertEqual(2, digest.call_count)
        self.assertEqual(1, launch.call_count)
        self.assertEqual(0, metadata.call_count)
        self.assertIsNone(validator._git_object_scope.session)

    def test_missing_commit_preserves_legacy_diagnostic(self) -> None:
        errors: list[str] = []
        with patch.object(validator, "_run_git", wraps=validator._run_git) as metadata:
            result = validator._git_commit_exists(self.root, "0" * 40, "import_commit", errors)
        self.assertFalse(result)
        self.assertTrue(any("import_commit does not exist as a local Git commit" in error for error in errors), errors)
        self.assertEqual(1, metadata.call_count)
        self.assertEqual(["cat-file", "-t", "0" * 40], metadata.call_args.args[1])

    def test_blob_and_annotated_tag_are_not_exact_commit_identities(self) -> None:
        blob = self.git("hash-object", "-w", "--stdin", content=b"probe blob\n").decode("ascii").strip()
        self.git(
            "-c", "user.name=Commit Probe", "-c", "user.email=probe@example.invalid",
            "tag", "-a", "not-a-commit", "-m", "probe tag", self.commit,
        )
        tag = self.git("rev-parse", "not-a-commit^{tag}").decode("ascii").strip()
        for kind, oid in (("blob", blob), ("tag", tag)):
            with self.subTest(kind=kind):
                errors: list[str] = []
                with patch.object(validator, "_run_git", wraps=validator._run_git) as metadata:
                    result = validator._git_commit_exists(self.root, oid, "import_commit", errors)
                self.assertFalse(result)
                self.assertTrue(any(
                    "import_commit does not exist as a local Git commit" in error
                    and f"exact object type is '{kind}'" in error for error in errors
                ), errors)
                self.assertEqual(1, metadata.call_count)

    def test_metadata_resolution_cannot_admit_a_non_oid_reference(self) -> None:
        errors: list[str] = []
        with patch.object(validator, "_run_git", wraps=validator._run_git) as metadata:
            result = validator._git_commit_exists(self.root, "HEAD", "commit", errors)
        self.assertFalse(result)
        self.assertTrue(any("invalid SHA-1 Git object ID" in error for error in errors), errors)
        self.assertEqual(1, metadata.call_count)

    def test_wrong_type_poisoned_session_cannot_be_rescued_by_later_metadata(self) -> None:
        blob = self.git("hash-object", "-w", "--stdin", content=b"wrong type\n").decode("ascii").strip()

        def operation() -> tuple[list[str], list[bool]]:
            errors: list[str] = []
            return errors, [
                validator._git_commit_exists(self.root, oid, "commit", errors)
                for oid in (blob, self.commit)
            ]

        with patch.object(validator, "_run_git", wraps=validator._run_git) as metadata:
            errors, results = validator._with_git_object_session(self.root, operation)
        self.assertEqual([False, False], results)
        self.assertTrue(errors)
        self.assertTrue(any("exact object type is 'blob'" in error for error in errors), errors)
        self.assertEqual(2, metadata.call_count)
        self.assertIsNone(validator._git_object_scope.session)


class CommitProbeProtocolTests(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path("commit-probe-unit-root").resolve()
        self.content = b"probe commit bytes\n"
        self.oid = object_id(self.content, "commit")

    def scope(self, process: FakeProcess) -> tuple[list[str], bool]:
        def operation() -> tuple[list[str], bool]:
            errors: list[str] = []
            return errors, validator._git_commit_exists(self.root, self.oid, "commit", errors)

        with patch.object(validator.subprocess, "Popen", return_value=process):
            return validator._with_git_object_session(self.root, operation)

    def test_forged_raw_content_is_not_rescued_by_a_commit_type_response(self) -> None:
        process = FakeProcess(frame(b"forged commit bytes\n", "commit", self.oid))
        with patch.object(validator, "_run_git", return_value=subprocess.CompletedProcess([], 0, b"commit\n", b"")) as metadata:
            errors, result = self.scope(process)
        self.assertFalse(result)
        self.assertTrue(any("Git object identity mismatch" in error for error in errors), errors)
        self.assertEqual(1, metadata.call_count)
        self.assertTrue(process.stdin.closed and process.stdout.closed)

    def test_provisional_success_is_rejected_by_close_failure(self) -> None:
        processes = {
            "trailing": FakeProcess(frame(self.content, "commit") + b"undeclared"),
            "exit": FakeProcess(frame(self.content, "commit"), exit_code=7),
        }
        for kind, process in processes.items():
            with self.subTest(kind=kind):
                with patch.object(validator, "_run_git", wraps=validator._run_git) as metadata:
                    errors, provisional = self.scope(process)
                self.assertTrue(provisional)
                self.assertTrue(errors)
                self.assertEqual(0, metadata.call_count)
                self.assertTrue(process.stdin.closed and process.stdout.closed)
                self.assertIsNone(validator._git_object_scope.session)

    def test_transport_failure_is_not_rescued_by_a_commit_type_response(self) -> None:
        errors: list[str] = []
        with patch.object(validator.subprocess, "Popen", side_effect=OSError("injected launch failure")):
            with patch.object(validator, "_run_git", return_value=subprocess.CompletedProcess([], 0, b"commit\n", b"")) as metadata:
                result = validator._git_commit_exists(self.root, self.oid, "commit", errors)
        self.assertFalse(result)
        self.assertTrue(any("cannot start bounded Git object" in error for error in errors), errors)
        self.assertEqual(1, metadata.call_count)


if __name__ == "__main__":
    unittest.main()
