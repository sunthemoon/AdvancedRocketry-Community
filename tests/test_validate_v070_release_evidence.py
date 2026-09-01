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
            "post_merge_ready",
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

    def test_postmerge_bundle_is_bound_to_pr_and_merge(self) -> None:
        errors, details = validate_v070_release_evidence(ROOT, require_approved=True)

        self.assertEqual([], errors)
        self.assertIs(details["post_merge_ready"], True)
        self.assertEqual(
            "b75e301f6cd77cfc1c1ade0e9b16c485f736c93b",
            details["merge_commit"],
        )
        self.assertEqual("4/4_PASS", details["pull_request_checks"])
        self.assertEqual("2026-09-01", details["human_approved_at"])

    def test_unsafe_evidence_path_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe evidence path"):
            _safe_relative("../outside.json")


if __name__ == "__main__":
    unittest.main()
