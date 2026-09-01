import unittest

from scripts.validate_v070_release_evidence import (
    ROOT,
    _safe_relative,
    validate_v070_release_evidence,
)


class V070ReleaseEvidenceTests(unittest.TestCase):
    def test_committed_release_evidence_is_complete(self) -> None:
        errors, details = validate_v070_release_evidence(ROOT, require_approved=True)

        self.assertEqual([], errors)
        for key in (
            "provenance_ready",
            "artifact_ready",
            "data_ready",
            "automated_ready",
            "server_ready",
            "persistence_ready",
            "authority_ready",
            "performance_ready",
            "client_ready",
            "docs_ready",
            "checksums_ready",
            "human_approved",
        ):
            self.assertIs(True, details[key], key)

    def test_premerge_bundle_discloses_postmerge_state(self) -> None:
        errors, details = validate_v070_release_evidence(ROOT, require_approved=True)

        self.assertEqual([], errors)
        self.assertIsInstance(details["post_merge_ready"], bool)
        self.assertEqual("2026-09-01", details["human_approved_at"])

    def test_unsafe_evidence_path_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe evidence path"):
            _safe_relative("../outside.json")


if __name__ == "__main__":
    unittest.main()
