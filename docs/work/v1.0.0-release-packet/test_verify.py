"""Small mutation checks for this handoff's integrity verifier, not release tests."""
from pathlib import Path
import hashlib
import json
import tempfile
import unittest
from unittest.mock import patch
import verify


class HandoffVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.packet = self.root / "docs/releases/v1.0.0"
        self.packet.mkdir(parents=True)
        for name in verify.REQUIRED_FILES:
            (self.packet / name).write_text("# Test document\n", encoding="utf-8")
        for name in ("README.md", "DOCUMENT-INDEX.md"):
            (self.root / name).write_text("# Test\n", encoding="utf-8")
        status = self.root / "docs/status/GATE_STATUS.md"
        status.parent.mkdir()
        status.write_text("overall: IN_PROGRESS\nG9: IN_PROGRESS\n", encoding="utf-8")
        artifact = self.root / "build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar"
        artifact.parent.mkdir(parents=True)
        artifact.write_bytes(b"test-only artifact")
        sha = verify.sha(artifact)
        (self.root / "input.txt").write_text("source", encoding="utf-8")
        (self.root / "report.md").write_text(sha, encoding="utf-8")
        snapshot = dict(artifact_sha256=sha, artifact_bytes=artifact.stat().st_size,
                        results="results.json", source_inventory="source.json")
        (self.root / "results.json").write_text(json.dumps(snapshot), encoding="utf-8")
        (self.root / "source.json").write_text(json.dumps(dict(artifact_sha256=sha,
            files=[dict(path="input.txt", sha256=verify.sha(self.root / "input.txt"))])), encoding="utf-8")
        self.index = dict(schema_version=1, status="IN_PROGRESS", candidate=dict(commit=None,
            artifact_sha256=None, tag=None), development_snapshot=snapshot,
            reports=[dict(id="fixture", path="report.md", report_sha256=verify.sha(self.root / "report.md"),
                          artifact_sha256=sha, role="supporting_development")])
        self.save_index()
        self.addCleanup(patch.stopall)
        patch.object(verify, "ROOT", self.root).start()
        patch.object(verify, "PACKET", self.packet).start()

    def save_index(self):
        (self.packet / "evidence-index.json").write_text(json.dumps(self.index), encoding="utf-8")
        files = sorted(p for p in self.packet.iterdir() if p.name != "checksums.txt")
        (self.packet / "checksums.txt").write_text("".join(
            hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.relative_to(self.root).as_posix() + "\n"
            for p in files), encoding="utf-8")

    def test_valid_handoff_does_not_approve_release(self):
        self.assertFalse(verify.verify()["release_approved"])

    def test_candidate_claim_is_rejected(self):
        self.index["candidate"]["commit"] = "a" * 40
        self.save_index()
        with self.assertRaises(AssertionError):
            verify.verify()

    def test_changed_report_is_rejected(self):
        (self.root / "report.md").write_text("changed", encoding="utf-8")
        with self.assertRaises(AssertionError):
            verify.verify()

    def test_wrong_artifact_binding_is_rejected(self):
        self.index["reports"][0]["artifact_sha256"] = "0" * 64
        self.save_index()
        with self.assertRaises(AssertionError):
            verify.verify()

    def test_changed_source_is_rejected(self):
        (self.root / "input.txt").write_text("changed", encoding="utf-8")
        with self.assertRaises(AssertionError):
            verify.verify()

    def test_missing_required_document_cannot_be_hidden_by_new_checksums(self):
        (self.packet / "PERFORMANCE.md").unlink()
        self.save_index()
        with self.assertRaises(AssertionError):
            verify.verify()

    def test_uncovered_file_is_rejected(self):
        (self.packet / "extra.txt").write_text("uncovered", encoding="utf-8")
        with self.assertRaises(AssertionError):
            verify.verify()


if __name__ == "__main__":
    unittest.main()
