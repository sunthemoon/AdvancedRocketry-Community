from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from scripts import validate_v002_g4_applicability as MODULE

ROOT = Path(__file__).resolve().parents[1]


def blocks(metadata: dict, acceptance: dict) -> str:
    return (
        "# ADR fixture\n\n```json\n"
        + json.dumps(metadata, indent=2, sort_keys=True)
        + "\n```\n\n```json\n"
        + json.dumps(acceptance, indent=2, sort_keys=True)
        + "\n```\n"
    )


class G4ApplicabilityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.adr_text = (ROOT / MODULE.ADR_PATH).read_text(encoding="utf-8")

    @staticmethod
    def pending_records() -> tuple[dict, dict]:
        metadata = {
            "status": "PROPOSED",
            "date": "2026-08-27",
            "deciders": [],
            "owner": "sunthemoon",
            "target_version": "v0.0.2",
            "expires": "v0.1.0",
            "recovery_condition": "Define and review the required additional test.",
            "automated_failure_reminder": "Pending reviews keep G4 open.",
            "supersedes": "",
        }
        acceptance = {
            case_id: {
                "decision": "PENDING",
                "reviewed_by": "",
                "reviewed_at": "",
            }
            for case_id in MODULE.CASE_IDS
        }
        acceptance["final_status"] = "PROPOSED"
        return metadata, acceptance

    def validate(self, metadata: dict | None = None, acceptance: dict | None = None):
        pending_metadata, pending_acceptance = self.pending_records()
        return MODULE.validate_adr_text(
            blocks(
                copy.deepcopy(pending_metadata if metadata is None else metadata),
                copy.deepcopy(pending_acceptance if acceptance is None else acceptance),
            )
        )

    def accepted_records(self) -> tuple[dict, dict, dict]:
        metadata, acceptance = self.pending_records()
        metadata["status"] = "ACCEPTED"
        metadata["deciders"] = ["sunthemoon"]
        for case_id in MODULE.CASE_IDS:
            acceptance[case_id] = {
                "decision": "ACCEPT_NOT_APPLICABLE",
                "reviewed_by": "sunthemoon",
                "reviewed_at": "2026-08-30",
            }
        acceptance["final_status"] = "ACCEPTED"
        bundle = {
            "review_readiness": {"status": "READY_FOR_HUMAN_GATE_REVIEW"},
            "applicability_reviews": {
                case_id: {
                    **acceptance[case_id],
                    "proposed_status": "NOT_APPLICABLE",
                    "rationale": "fixture",
                    "notes": "reviewed",
                }
                for case_id in MODULE.CASE_IDS
            },
        }
        return metadata, acceptance, bundle

    def test_current_adr_is_valid_in_its_recorded_lifecycle_state(self) -> None:
        errors, details = MODULE.validate_adr_text(self.adr_text)
        self.assertEqual([], errors)
        self.assertIn(details["status"], MODULE.ADR_STATUSES)

    def test_synthetic_pending_state_is_valid(self) -> None:
        errors, details = self.validate()
        self.assertEqual([], errors)
        self.assertEqual("PROPOSED", details["status"])

    def test_proposed_state_allows_an_authorized_partial_review(self) -> None:
        metadata, acceptance = self.pending_records()
        metadata["deciders"] = ["sunthemoon"]
        acceptance[MODULE.CASE_IDS[0]] = {
            "decision": "REQUIRE_ADDITIONAL_TEST",
            "reviewed_by": "sunthemoon",
            "reviewed_at": "2026-08-30",
        }

        errors, details = self.validate(metadata, acceptance)

        self.assertEqual([], errors)
        self.assertEqual("PROPOSED", details["status"])

    def test_duplicate_json_key_is_rejected(self) -> None:
        metadata, acceptance = self.pending_records()
        text = blocks(metadata, acceptance).replace(
            '  "status": "PROPOSED",',
            '  "status": "PROPOSED",\n  "status": "ACCEPTED",',
            1,
        )
        errors, _ = MODULE.validate_adr_text(text)
        self.assertTrue(any("duplicate JSON key" in item for item in errors))

    def test_missing_or_extra_block_is_rejected(self) -> None:
        errors, _ = MODULE.validate_adr_text("# no records\n")
        self.assertIn(
            "ADR-005 must contain exactly two machine-readable JSON blocks", errors
        )

    def test_pending_decision_rejects_reviewer_metadata(self) -> None:
        _, acceptance = self.pending_records()
        acceptance[MODULE.CASE_IDS[0]]["reviewed_by"] = "someone"
        errors, _ = self.validate(acceptance=acceptance)
        self.assertTrue(any("pending decision" in item for item in errors))

    def test_nonpending_decision_requires_valid_reviewer_and_date(self) -> None:
        _, acceptance = self.pending_records()
        acceptance[MODULE.CASE_IDS[0]]["decision"] = "ACCEPT_NOT_APPLICABLE"
        errors, _ = self.validate(acceptance=acceptance)
        self.assertTrue(any("requires reviewer and date" in item for item in errors))

    def test_deciders_must_match_recorded_reviewers(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        metadata["deciders"] = []
        errors, _ = self.validate(metadata, acceptance)
        self.assertTrue(any("deciders differ" in item for item in errors))

    def test_unauthorized_case_reviewer_and_decider_are_rejected(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        metadata["deciders"] = ["unauthorized-reviewer"]
        for case_id in MODULE.CASE_IDS:
            acceptance[case_id]["reviewed_by"] = "unauthorized-reviewer"

        errors, _ = self.validate(metadata, acceptance)

        self.assertTrue(any("deciders are not authorized" in item for item in errors))
        self.assertTrue(any("reviewer is not authorized" in item for item in errors))

    def test_accepted_requires_every_proposal_to_be_accepted(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        acceptance[MODULE.CASE_IDS[0]]["decision"] = "REQUIRE_ADDITIONAL_TEST"
        errors, _ = self.validate(metadata, acceptance)
        self.assertTrue(any("ACCEPT_NOT_APPLICABLE for every case" in item for item in errors))

    def test_accepted_adr_requires_canonical_bundle(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        errors, details = self.validate(metadata, acceptance)
        self.assertEqual([], errors)
        self.assertIn(
            "ADR-005 cannot be ACCEPTED without canonical client evidence",
            MODULE.cross_check_bundle_record(details, None),
        )

    def test_matching_accepted_bundle_passes(self) -> None:
        metadata, acceptance, bundle = self.accepted_records()
        errors, details = self.validate(metadata, acceptance)
        self.assertEqual([], errors)
        self.assertEqual([], MODULE.cross_check_bundle_record(details, bundle))

    def test_bundle_decision_or_reviewer_mismatch_is_rejected(self) -> None:
        metadata, acceptance, bundle = self.accepted_records()
        errors, details = self.validate(metadata, acceptance)
        self.assertEqual([], errors)
        bundle["applicability_reviews"][MODULE.CASE_IDS[0]]["reviewed_by"] = "other"
        observed = MODULE.cross_check_bundle_record(details, bundle)
        self.assertTrue(any("reviewed_by" in item for item in observed))

    def test_bundle_requires_accepted_adr_and_readiness(self) -> None:
        errors, details = self.validate()
        self.assertEqual([], errors)
        _, acceptance = self.pending_records()
        bundle = {
            "review_readiness": {"status": "INCOMPLETE"},
            "applicability_reviews": {
                case_id: copy.deepcopy(acceptance[case_id])
                for case_id in MODULE.CASE_IDS
            },
        }
        observed = MODULE.cross_check_bundle_record(details, bundle)
        self.assertTrue(any("status ACCEPTED" in item for item in observed))
        self.assertTrue(any("not READY_FOR_HUMAN_GATE_REVIEW" in item for item in observed))


if __name__ == "__main__":
    unittest.main()
