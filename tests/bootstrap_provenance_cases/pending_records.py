"""PendingRecordCases for the bootstrap provenance facade."""
from scripts.validate_bootstrap_provenance import APPROVED_RECORD_STATUS, EXPECTED_NOTICE_PATH, EXPECTED_RECORD_PATH, PENDING_RECORD_STATUS


class PendingRecordCases:
    def test_pending_record_rejects_duplicate_reserved_metadata(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record = record_path.read_bytes()
        marker = b"reviewed_content_sha256: null\n```"
        self.assertIn(marker, record)
        record_path.write_bytes(
            record.replace(
                marker,
                b"reviewed_content_sha256: null\n"
                b"record_status: THIRD_PARTY_APPROVED\n"
                b"reviewer: forged-reviewer\n"
                b"reviewed_at: 2099-01-01\n```",
                1,
            )
        )

        errors, _ = self.validate()

        for field in ("record_status", "reviewer", "reviewed_at"):
            self.assertTrue(
                any(
                    f"initial provenance YAML {field} must occur exactly once"
                    in error
                    for error in errors
                ),
                errors,
            )

    def test_approved_record_rejects_reserved_metadata_in_later_yaml_block(
        self,
    ) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        with record_path.open("ab") as stream:
            stream.write(
                b"\n```yaml\n"
                b"final_status_after_review: REJECTED\n"
                b"reviewed_audited_target_commit: " + b"0" * 40 + b"\n"
                b"```\n"
            )

        errors, _ = self.validate()

        for field in (
            "final_status_after_review",
            "reviewed_audited_target_commit",
        ):
            self.assertTrue(
                any(
                    f"reserved provenance YAML field {field} must occur only "
                    "in the initial metadata block" in error
                    for error in errors
                ),
                errors,
            )

    def test_pending_fixture_can_be_rebuilt_from_approved_machine_state(self) -> None:
        self.document["review"] = {
            "record_status": APPROVED_RECORD_STATUS,
            "reviewer": "prior-reviewer",
            "reviewed_at": "2026-08-27",
            "final_status_after_review": APPROVED_RECORD_STATUS,
            "reviewed_audited_target_commit": self.document[
                "audited_target_commit"
            ],
            "reviewed_content_sha256": "0" * 64,
        }
        for target in self.document["targets"]:
            target["status"] = APPROVED_RECORD_STATUS
            target["proposed_status_after_review"] = None

        self.reset_to_pending_review()
        self.write_review_documents(approved=False)
        self.write_manifest()
        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(PENDING_RECORD_STATUS, details["review_status"])

    def test_missing_third_party_notice_is_rejected(self) -> None:
        (self.root / EXPECTED_NOTICE_PATH).unlink()

        errors, _ = self.validate()

        self.assertTrue(
            any("third-party notice does not exist" in error for error in errors),
            errors,
        )

    def test_pending_review_cannot_carry_reviewer_metadata(self) -> None:
        self.document["review"]["reviewer"] = "premature-reviewer"
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("pending review must have null approval metadata" in error for error in errors),
            errors,
        )

    def test_pending_review_rejects_nonpending_notice_review_block(self) -> None:
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice = notice_path.read_text(encoding="utf-8")
        notice = notice.replace(
            "status: PENDING_HUMAN_REVIEW",
            f"status: {APPROVED_RECORD_STATUS}",
        )
        notice = notice.replace("reviewer: null", "reviewer: premature-reviewer")
        notice = notice.replace("reviewed_at: null", "reviewed_at: 2026-08-28")
        notice_path.write_text(notice, encoding="utf-8")

        errors, _ = self.validate()

        for field in ("status", "reviewer", "reviewed_at"):
            self.assertTrue(
                any(
                    f"pending third-party notice {field} must occur exactly once"
                    in error
                    for error in errors
                ),
                errors,
            )

    def test_pending_review_rejects_approved_provenance_target_block(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                "status: PENDING_HUMAN_REVIEW",
                f"status: {APPROVED_RECORD_STATUS}",
                1,
            ),
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "pending provenance Markdown target status fields contradict"
                in error
                for error in errors
            ),
            errors,
        )

    def test_pending_review_rejects_target_reviewer_and_date(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record = self.read_utf8(record_path)
        target_block = """status: PENDING_HUMAN_REVIEW
proposed_status_after_review: THIRD_PARTY_APPROVED
reviewer: null
reviewed_at: null"""
        changed_block = """status: PENDING_HUMAN_REVIEW
proposed_status_after_review: THIRD_PARTY_APPROVED
reviewer: premature-reviewer
reviewed_at: 2026-08-28"""
        self.assertIn(target_block, record)
        self.write_utf8(record_path, record.replace(target_block, changed_block, 1))

        errors, _ = self.validate()

        for field in ("reviewer", "reviewed_at"):
            self.assertTrue(
                any(
                    f"pending provenance Markdown target {field} fields contradict"
                    in error
                    for error in errors
                ),
                errors,
            )
