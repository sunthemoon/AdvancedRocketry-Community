"""CliCases for the bootstrap provenance facade."""
from scripts.validate_bootstrap_provenance import EXPECTED_NOTICE_PATH, EXPECTED_RECORD_PATH, PENDING_RECORD_STATUS


class CliCases:
    def test_cli_default_separates_mechanical_pass_from_human_pending(self) -> None:
        result = self.run_cli()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(
            "[PASS] Bootstrap provenance mechanical validation:", result.stdout
        )
        self.assertIn(
            f"[PENDING] Human provenance review: {PENDING_RECORD_STATUS}",
            result.stdout,
        )
        self.assertIn("mechanical validation does not approve G0", result.stdout)
        self.assertNotIn("reviewed_content_sha256:", result.stdout)
        self.assertNotIn("[PASS] Provenance review metadata", result.stdout)
        self.assertEqual("", result.stderr)

    def test_cli_diagnostic_pending_digest_is_explicitly_nonapproval(self) -> None:
        _, details = self.validate()

        result = self.run_cli("--diagnostic-pending-digest")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(
            "[DIAGNOSTIC] pending_review_content_sha256: "
            f"{details['review_content_sha256']}",
            result.stdout,
        )
        self.assertIn("pending content only", result.stdout)
        self.assertIn("must not be copied into approval metadata", result.stdout)
        self.assertIn("[PENDING] Human provenance review:", result.stdout)
        self.assertNotIn("approval_candidate_reviewed_content_sha256", result.stdout)

        self.approve_current_content()
        approved_result = self.run_cli("--diagnostic-pending-digest")

        self.assertEqual(1, approved_result.returncode, approved_result.stdout)
        self.assertIn(
            "--diagnostic-pending-digest requires a valid pending review",
            approved_result.stdout,
        )
        self.assertNotIn("pending_review_content_sha256:", approved_result.stdout)

    def test_cli_approval_candidate_mode_accepts_only_missing_final_digest(
        self,
    ) -> None:
        pending_result = self.run_cli("--prepare-approval-digest")
        self.assertEqual(1, pending_result.returncode, pending_result.stdout)
        self.assertIn(
            "requires an otherwise-valid THIRD_PARTY_APPROVED candidate",
            pending_result.stdout,
        )
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256", pending_result.stdout
        )

        self.approve_current_content()
        self.document["review"]["reviewed_content_sha256"] = None
        self.write_review_documents(approved=True)
        self.write_manifest()
        protected_paths = (
            self.manifest,
            self.root / EXPECTED_RECORD_PATH,
            self.root / EXPECTED_NOTICE_PATH,
        )
        before = {path: path.read_bytes() for path in protected_paths}

        candidate_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(
            0,
            candidate_result.returncode,
            candidate_result.stdout + candidate_result.stderr,
        )
        self.assertRegex(
            candidate_result.stdout,
            r"\[CANDIDATE\] approval_candidate_reviewed_content_sha256: "
            r"[0-9a-f]{64}",
        )
        self.assertIn("changes no files and records no approval", candidate_result.stdout)
        self.assertEqual(before, {path: path.read_bytes() for path in protected_paths})

        self.document["review"]["reviewed_at"] = "not-an-iso-date"
        self.write_manifest()
        invalid_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(1, invalid_result.returncode, invalid_result.stdout)
        self.assertIn(
            "approved review requires a valid ISO reviewed_at date",
            invalid_result.stdout,
        )
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256", invalid_result.stdout
        )

        self.document["review"]["reviewed_at"] = "2026-08-27"
        self.document["review"]["reviewed_content_sha256"] = "not-a-digest"
        self.write_review_documents(approved=True, digest="not-a-digest")
        self.write_manifest()
        malformed_digest_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(
            1, malformed_digest_result.returncode, malformed_digest_result.stdout
        )
        self.assertIn(
            "approved review reviewed_content_sha256 must be lowercase",
            malformed_digest_result.stdout,
        )
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256",
            malformed_digest_result.stdout,
        )

        self.approve_current_content()
        already_bound_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(1, already_bound_result.returncode, already_bound_result.stdout)
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256", already_bound_result.stdout
        )

    def test_cli_require_approved_review_blocks_pending_then_accepts_bound_review(
        self,
    ) -> None:
        pending_result = self.run_cli("--require-approved-review")

        self.assertEqual(1, pending_result.returncode, pending_result.stdout)
        self.assertIn("[PENDING] Human provenance review:", pending_result.stdout)
        self.assertIn(
            "[FAIL] --require-approved-review requires a valid, digest-bound",
            pending_result.stdout,
        )

        self.approve_current_content()
        approved_result = self.run_cli("--require-approved-review")

        self.assertEqual(
            0,
            approved_result.returncode,
            approved_result.stdout + approved_result.stderr,
        )
        self.assertIn(
            "[PASS] Recorded provenance review is mechanically consistent and "
            "digest-bound: THIRD_PARTY_APPROVED",
            approved_result.stdout,
        )
        self.assertNotIn("[PENDING]", approved_result.stdout)

    def test_cli_retires_ambiguous_print_review_digest_option(self) -> None:
        result = self.run_cli("--print-review-digest")

        self.assertEqual(2, result.returncode, result.stdout)
        self.assertIn("--print-review-digest is retired", result.stdout)
        self.assertIn("--diagnostic-pending-digest", result.stdout)
        self.assertIn("--prepare-approval-digest", result.stdout)
        self.assertNotIn("reviewed_content_sha256:", result.stdout)
