"""ApprovedRecordCases for the bootstrap provenance facade."""
from scripts.validate_bootstrap_provenance import APPROVED_RECORD_STATUS, EXPECTED_NOTICE_PATH, EXPECTED_RECORD_PATH


class ApprovedRecordCases:
    def test_complete_approved_review_is_accepted(self) -> None:
        self.approve_current_content()

        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(APPROVED_RECORD_STATUS, details["review_status"])

    def test_approved_review_is_invalid_after_notice_changes(self) -> None:
        self.approve_current_content()
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_text(
            notice_path.read_text(encoding="utf-8") + "\nPost-review notice change.\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "content digest does not match the current manifest, provenance "
                "record, and third-party notice"
                in error
                for error in errors
            ),
            errors,
        )

    def test_approved_review_rejects_residual_record_pending_status(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8")
            + "\nResidual status: PENDING_HUMAN_REVIEW\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "provenance Markdown still contains pending provenance status"
                in error
                for error in errors
            ),
            errors,
        )

    def test_approved_review_rejects_residual_notice_pending_status(self) -> None:
        self.approve_current_content()
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_text(
            notice_path.read_text(encoding="utf-8")
            + "\nResidual status: PENDING_HUMAN_REVIEW\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "third-party notice still contains pending provenance status"
                in error
                for error in errors
            ),
            errors,
        )

    def test_approved_review_rejects_unchecked_approval_checklist(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8")
            + "\n- [ ] Human reviewer confirms the approval.\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("unchecked checklist item" in error for error in errors),
            errors,
        )

    def test_approved_review_rejects_pending_approval_prose(self) -> None:
        self.approve_current_content()
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_text(
            notice_path.read_text(encoding="utf-8")
            + "\nFinal legal approval remains pending.\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("still contains pending approval prose" in error for error in errors),
            errors,
        )

    def test_approved_review_rejects_incomplete_binary_obligations_disclaimers(
        self,
    ) -> None:
        cases = (
            (
                EXPECTED_RECORD_PATH,
                "provenance Markdown",
                "This record does not claim binary-distribution notice "
                "obligations are complete.",
            ),
            (
                EXPECTED_NOTICE_PATH,
                "third-party notice",
                "This notice does not claim that binary-distribution "
                "obligations are complete.",
            ),
        )
        for relative_path, document_label, disclaimer in cases:
            with self.subTest(relative_path=relative_path):
                self.approve_current_content()
                path = self.root / relative_path
                path.write_text(
                    path.read_text(encoding="utf-8") + f"\n{disclaimer}\n",
                    encoding="utf-8",
                )

                errors, _ = self.validate()

                self.assertTrue(
                    any(
                        f"approved review {document_label} still contains "
                        "incomplete binary-distribution obligations disclaimer"
                        in error
                        for error in errors
                    ),
                    errors,
                )

    def test_approved_review_is_invalid_after_audited_commit_changes(self) -> None:
        self.approve_current_content()
        previous = self.document["audited_target_commit"]
        replacement = "0" * 40
        self.document["audited_target_commit"] = replacement
        record_path = self.root / EXPECTED_RECORD_PATH
        record = record_path.read_text(encoding="utf-8").replace(
            f"audited_target_commit: {previous}",
            f"audited_target_commit: {replacement}",
            1,
        )
        record_path.write_text(record, encoding="utf-8")
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("bound to a different audited_target_commit" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_review_is_invalid_after_manifest_target_changes(self) -> None:
        self.approve_current_content()
        self.document["targets"][0]["transformations"].append("post-review edit")
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_review_is_invalid_after_markdown_table_changes(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        self.write_utf8(
            record_path,
            self.read_utf8(record_path).replace(
                "| imported targets | baseline |",
                "| imported targets | post-review change |",
                1,
            ),
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_digest_detects_record_line_ending_changes(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        original = record_path.read_bytes()
        self.assertIn(b"\n", original)
        self.assertNotIn(b"\r\n", original)
        record_path.write_bytes(original.replace(b"\n", b"\r\n"))

        errors, _ = self.validate()

        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_invalid_utf8_provenance_record_is_rejected(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_bytes(record_path.read_bytes() + b"\xff")

        errors, _ = self.validate()

        self.assertTrue(
            any("Cannot read provenance Markdown record" in error for error in errors),
            errors,
        )

    def test_invalid_utf8_third_party_notice_is_rejected(self) -> None:
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_bytes(notice_path.read_bytes() + b"\xff")

        errors, _ = self.validate()

        self.assertTrue(
            any("Cannot read third-party notice" in error for error in errors),
            errors,
        )

    def test_approved_review_is_invalid_after_target_hash_changes(self) -> None:
        self.approve_current_content()
        target = self.find_target("build.gradle")
        old_hash = target["audited_target_raw_blob_sha256"]
        content = (self.root / "build.gradle").read_bytes() + b"post-review\n"
        new_hash = self.digest(content)
        (self.root / "build.gradle").write_bytes(content)
        target["audited_target_raw_blob_sha256"] = new_hash
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                str(old_hash), new_hash, 1
            ),
            encoding="utf-8",
        )
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "imported target build.gradle audited snapshot" in error
                for error in errors
            ),
            errors,
        )
        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_review_requires_date_and_target_transition(self) -> None:
        self.document["review"] = {
            "record_status": APPROVED_RECORD_STATUS,
            "reviewer": "license-reviewer",
            "reviewed_at": None,
            "final_status_after_review": APPROVED_RECORD_STATUS,
            "reviewed_audited_target_commit": self.document[
                "audited_target_commit"
            ],
            "reviewed_content_sha256": "0" * 64,
        }
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("approved review requires a valid ISO reviewed_at date" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("status is inconsistent with review state" in error for error in errors),
            errors,
        )
