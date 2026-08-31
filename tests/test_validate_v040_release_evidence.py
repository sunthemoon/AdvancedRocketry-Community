import unittest

from scripts.validate_v040_release_evidence import (
    ROOT,
    _safe_relative,
    validate_v040_release_evidence,
)


class V040ReleaseEvidenceTests(unittest.TestCase):
    def test_committed_release_evidence_is_complete(self) -> None:
        errors, details = validate_v040_release_evidence(
            ROOT, require_approved=True
        )

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

    def test_current_artifact_matches_when_available(self) -> None:
        artifact = (
            ROOT
            / "build/libs/advancedrocketry-community-1.20.1-0.4.0-dev.jar"
        )
        if not artifact.exists():
            self.skipTest("local build artifact is not present")

        errors, _ = validate_v040_release_evidence(ROOT, artifact=artifact)

        self.assertEqual([], errors)

    def test_owner_review_is_bound_to_the_evidence_bundle(self) -> None:
        errors, details = validate_v040_release_evidence(
            ROOT, require_approved=True
        )

        self.assertEqual([], errors)
        self.assertIs(details["human_approved"], True)
        self.assertEqual("2026-09-01", details["human_approved_at"])

    def test_unsafe_evidence_path_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "unsafe evidence path"):
            _safe_relative("../outside.json")


if __name__ == "__main__":
    unittest.main()
