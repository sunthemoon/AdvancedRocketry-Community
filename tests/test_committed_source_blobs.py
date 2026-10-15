import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import scripts.validate_bootstrap_provenance as validator


class CommittedSourceBlobTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.payloads = {"README.md": b"raw\r\nbytes\x00\xff\n", "nested/data.bin": b"second\x00blob"}
        for relative, payload in self.payloads.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(payload)
        self.git("init", "--quiet")
        self.git("-c", "core.autocrlf=false", "add", "--all")
        self.git("-c", "user.name=Source Blob Test", "-c",
                 "user.email=source-blob@example.invalid", "commit", "--quiet", "-m", "fixture")
        self.commit = self.git("rev-parse", "HEAD")

    def git(self, *args: str) -> str:
        result = subprocess.run(["git", "-C", str(self.root), *args], check=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15)
        return result.stdout.decode("ascii").strip()

    def read(self, *paths: str, maximum_size: int = 1024) -> dict[str, tuple[str, bytes]]:
        return validator.read_git_blobs_at_commit(self.root, self.commit, tuple(paths), maximum_size)

    def test_group_preserves_raw_bytes_and_exact_blob_identity(self) -> None:
        blobs = self.read(*self.payloads)
        self.assertEqual(set(self.payloads), set(blobs))
        for path, payload in self.payloads.items():
            with self.subTest(path=path):
                oid, observed = blobs[path]
                self.assertEqual(payload, observed)
                self.assertEqual(self.git("rev-parse", f"{self.commit}:{path}"), oid)
                self.assertEqual(hashlib.sha1(f"blob {len(payload)}\0".encode() + payload).hexdigest(), oid)

    def test_historical_commit_is_read_after_head_changes(self) -> None:
        (self.root / "README.md").write_bytes(b"new content\n")
        self.git("-c", "core.autocrlf=false", "add", "--all")
        self.git("-c", "user.name=Source Blob Test", "-c", "user.email=source-blob@example.invalid",
                 "commit", "--quiet", "-m", "new tip")
        self.assertEqual(self.payloads["README.md"], self.read("README.md")["README.md"][1])

    def test_wrong_exact_commit_type_is_rejected(self) -> None:
        blob = self.git("rev-parse", f"{self.commit}:README.md")
        with self.assertRaisesRegex(ValueError, "exact Git commit"):
            validator.read_git_blobs_at_commit(self.root, blob, ("README.md",), 1024)

    def test_missing_path_is_rejected_without_partial_result(self) -> None:
        with self.assertRaisesRegex(ValueError, "missing"):
            self.read("README.md", "missing.md")

    def test_tree_and_symbolic_link_modes_are_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "regular Git blob"):
            self.read("nested")
        oid = self.git("rev-parse", f"{self.commit}:README.md")
        self.git("update-index", "--cacheinfo", f"120000,{oid},README.md")
        self.git("-c", "user.name=Source Blob Test", "-c", "user.email=source-blob@example.invalid",
                 "commit", "--quiet", "-m", "symbolic link entry")
        self.commit = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(ValueError, "regular Git blob"):
            self.read("README.md")

    def test_per_blob_size_bound_is_enforced(self) -> None:
        with self.assertRaisesRegex(ValueError, "byte Git object limit"):
            self.read("README.md", maximum_size=1)

    def test_input_bounds_are_checked_before_transport(self) -> None:
        with patch.object(validator, "_with_git_object_session") as transport:
            for paths in ((), ("README.md",) * 2, tuple(str(i) for i in range(17)), ([],), ("../outside",)):
                with self.subTest(paths=paths), self.assertRaises(ValueError):
                    validator.read_git_blobs_at_commit(self.root, self.commit, paths, 1024)
            for limit in (0, -1, True, 1.5, validator.MAX_SOURCE_BLOB_BYTES + 1, validator.MAX_PROVENANCE_BLOB_BYTES + 1):
                with self.subTest(limit=limit), self.assertRaises(ValueError):
                    validator.read_git_blobs_at_commit(self.root, self.commit, ("README.md",), limit)
            for commit in (None, "HEAD", "a" * 64, "A" * 40):
                with self.subTest(commit=commit), self.assertRaises(ValueError):
                    validator.read_git_blobs_at_commit(self.root, commit, ("README.md",), 1024)
            transport.assert_not_called()

    def test_session_finalization_failure_withholds_successful_payloads(self) -> None:
        original = validator._GitObjectSession.close

        def fail_after_close(session: validator._GitObjectSession) -> None:
            original(session)
            session._fail("injected finalization failure")

        with patch.object(validator._GitObjectSession, "close", fail_after_close):
            with self.assertRaisesRegex(ValueError, "injected finalization failure"):
                self.read("README.md")

    def test_repeated_calls_revalidate_objects_in_fresh_finalized_sessions(self) -> None:
        original = validator._GitObjectSession.read
        sessions = []

        def observe(session, *args):
            if not any(session is old for old in sessions):
                sessions.append(session)
            return original(session, *args)

        with patch.object(validator._GitObjectSession, "read", observe):
            self.assertEqual(self.read("README.md"), self.read("README.md"))
        self.assertEqual(2, len(sessions))
        self.assertTrue(all(session.closed and session.requests >= 3 for session in sessions))


if __name__ == "__main__":
    unittest.main()
