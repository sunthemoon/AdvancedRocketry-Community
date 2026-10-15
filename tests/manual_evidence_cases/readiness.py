from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import validate_bundle
from scripts.validate_bootstrap_provenance import APPROVED_RECORD_STATUS
from scripts.validate_bootstrap_provenance import PENDING_RECORD_STATUS
from scripts.validate_v002_final_g0_review import PENDING as FINAL_G0_PENDING


class ReadinessCases:
    """Warning dispositions and source approval readiness."""

    def test_mismatch_project_warning_is_a_server_finding(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        mismatch_log = self.root / session["log_excerpts"][role]["source"]
        payload = mismatch_log.read_text(encoding="utf-8") + (
            "[Server thread/WARN] [advancedrocketrycommunity/]: mismatch finding\n"
        )
        mismatch_log.write_text(payload, encoding="utf-8")
        runtime_log = self.jar_paths["server"].parent.parent / "logs" / "latest.log"
        runtime_log.write_text(payload, encoding="utf-8")
        self.refresh_mismatch_receipt(session)
        self.set_warning_disposition(session, (role,), count=1)
        session["findings"]["server_project_warning_count"] = 1
        session["findings"]["notes"] = "Mismatch project warning retained."

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual(1, record["findings"]["server_project_warning_count"])
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(
            any("server_project_warning_count is 1" in error for error in strict_errors),
            strict_errors,
        )

    def test_accepted_third_party_warning_requires_and_preserves_disposition(self) -> None:
        session = self.ready_session()
        client_log = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        with client_log.open("a", encoding="utf-8") as stream:
            stream.write("[Render thread/WARN] [forge/]: reviewed third-party warning\n")
        roles = (
            "client_startup_world",
            "matching_client_connection",
        )
        self.set_warning_disposition(
            session, roles, count=1, status="ACCEPTED"
        )

        errors, record, output = self.collect(session)
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )

        self.assertEqual([], errors)
        self.assertEqual([], strict_errors)
        assert record is not None
        self.assertEqual(
            "ACCEPTED",
            record["log_excerpts"]["client_startup_world"][
                "warning_disposition"
            ]["status"],
        )

    def test_broad_third_party_error_blocks_strict_readiness(self) -> None:
        session = self.ready_session()
        client_log = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        with client_log.open("a", encoding="utf-8") as stream:
            stream.write("[Render thread/ERROR] [forge/]: broad client failure\n")

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(any("broad ERROR" in error for error in strict_errors), strict_errors)

    def test_fatal_is_counted_separately_and_blocks_strict_readiness(self) -> None:
        session = self.ready_session()
        role = "client_startup_world"
        source = self.root / session["log_excerpts"][role]["source"]
        with source.open("a", encoding="utf-8") as stream:
            stream.write(
                "[Render thread/FATAL] [advancedrocketrycommunity/]: fatal bootstrap\n"
            )

        errors, record, output = self.collect(session, "fatal-audit")

        self.assertEqual([], errors)
        assert record is not None
        counts = record["log_excerpts"][role]["source_audit"]["audit_counts"]
        self.assertEqual(0, counts["error_count"])
        self.assertEqual(0, counts["project_error_count"])
        self.assertEqual(1, counts["fatal_count"])
        self.assertEqual(1, counts["project_fatal_count"])
        self.assertTrue(
            any(
                "broad FATAL" in blocker
                for blocker in record["review_readiness"]["blockers"]
            )
        )
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(any("broad FATAL" in error for error in strict_errors))

    def test_strict_collect_is_atomic_when_review_is_incomplete(self) -> None:
        session = self.ready_session()
        review = session["applicability_reviews"]["chunk_unload_behavior"]
        review.update(decision="PENDING", reviewed_by="", reviewed_at="", notes="")
        output = self.build / "strict-incomplete"

        errors, record = collect_evidence(
            self.write_session(session, "strict-incomplete-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("chunk_unload_behavior" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_committed_output_implicitly_requires_acceptance_readiness(self) -> None:
        session = self.ready_session()
        review = session["applicability_reviews"]["chunk_unload_behavior"]
        review.update(decision="PENDING", reviewed_by="", reviewed_at="", notes="")
        output = self.root / COMMITTED_BUNDLE

        errors, record = collect_evidence(
            self.write_session(session, "canonical-incomplete-session.json"),
            output,
            self.root,
        )

        self.assertIsNone(record)
        self.assertTrue(any("chunk_unload_behavior" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_strict_collect_requires_approved_source_commit_provenance(self) -> None:
        session = self.ready_session()
        output = self.build / "strict-pending-provenance"
        self.provenance_validator.reset_mock()
        self.provenance_validator.return_value = (
            [],
            {"review_status": PENDING_RECORD_STATUS},
        )

        errors, record = collect_evidence(
            self.write_session(session, "strict-pending-provenance-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any(APPROVED_RECORD_STATUS in error for error in errors), errors)
        self.assertFalse(output.exists())
        self.provenance_validator.assert_called_once_with(
            self.root.resolve(), self.source_commit
        )

    def test_committed_collect_implicitly_requires_approved_provenance(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        self.provenance_validator.reset_mock()
        self.provenance_validator.return_value = (
            [],
            {"review_status": PENDING_RECORD_STATUS},
        )

        errors, record = collect_evidence(
            self.write_session(session, "canonical-pending-provenance-session.json"),
            output,
            self.root,
        )

        self.assertIsNone(record)
        self.assertTrue(any(APPROVED_RECORD_STATUS in error for error in errors), errors)
        self.assertFalse(output.exists())
        self.provenance_validator.assert_called_once_with(
            self.root.resolve(), self.source_commit
        )

    def test_strict_collect_requires_approved_final_g0_source_review(self) -> None:
        session = self.ready_session()
        output = self.build / "strict-pending-final-g0"
        self.final_g0_validator.reset_mock()
        self.final_g0_validator.return_value = (
            [],
            {
                "source_review_outcome": FINAL_G0_PENDING,
                "readme_review_outcome": FINAL_G0_PENDING,
            },
        )

        errors, record = collect_evidence(
            self.write_session(session, "strict-pending-final-g0-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(
            any("final-G0 source/resource review" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())
        self.final_g0_validator.assert_called_once_with(
            self.root.resolve(), self.source_commit
        )

    def test_non_strict_build_failure_archive_allows_pending_provenance(self) -> None:
        session = self.ready_session()
        session["observations"]["MANUAL-V002-003"].update(
            outcome="FAIL",
            actual="The observed mismatch policy needs human investigation.",
        )
        output = self.build / "pending-provenance-failure"
        self.provenance_validator.reset_mock()
        self.provenance_validator.return_value = (
            [],
            {"review_status": PENDING_RECORD_STATUS},
        )

        errors, record = collect_evidence(
            self.write_session(session, "pending-provenance-failure-session.json"),
            output,
            self.root,
        )

        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        assert record is not None
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        self.assertTrue(output.is_dir())
        self.provenance_validator.assert_not_called()

    def test_non_strict_failure_archive_allows_pending_final_g0_review(self) -> None:
        session = self.ready_session()
        session["observations"]["MANUAL-V002-003"].update(
            outcome="FAIL",
            actual="The observed mismatch policy needs human investigation.",
        )
        output = self.build / "pending-final-g0-failure"
        self.final_g0_validator.reset_mock()
        self.final_g0_validator.return_value = (
            [],
            {
                "source_review_outcome": FINAL_G0_PENDING,
                "readme_review_outcome": FINAL_G0_PENDING,
            },
        )

        errors, record = collect_evidence(
            self.write_session(session, "pending-final-g0-failure-session.json"),
            output,
            self.root,
        )

        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        self.assertTrue(output.is_dir())
        self.final_g0_validator.assert_not_called()

    def test_committed_validation_requires_source_commit_provenance(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, record = collect_evidence(
            self.write_session(session, "canonical-provenance-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        self.provenance_validator.reset_mock()
        self.provenance_validator.return_value = (
            [],
            {"review_status": PENDING_RECORD_STATUS},
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(APPROVED_RECORD_STATUS in error for error in validation_errors),
            validation_errors,
        )
        self.provenance_validator.assert_called_once_with(
            self.root.resolve(), self.source_commit
        )

    def test_strict_build_validation_requires_source_commit_provenance(self) -> None:
        session = self.ready_session()
        output = self.build / "strict-validation-pending-provenance"
        errors, record = collect_evidence(
            self.write_session(session, "strict-validation-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        self.provenance_validator.reset_mock()
        self.provenance_validator.return_value = (
            [],
            {"review_status": PENDING_RECORD_STATUS},
        )

        validation_errors, _ = validate_bundle(
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertTrue(
            any(APPROVED_RECORD_STATUS in error for error in validation_errors),
            validation_errors,
        )
        self.provenance_validator.assert_called_once_with(
            self.root.resolve(), self.source_commit
        )

    def test_strict_bundle_validation_requires_final_g0_source_review(self) -> None:
        session = self.ready_session()
        output = self.build / "strict-validation-pending-final-g0"
        errors, record = collect_evidence(
            self.write_session(session, "strict-final-g0-validation-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        self.final_g0_validator.reset_mock()
        self.final_g0_validator.return_value = (
            [],
            {
                "source_review_outcome": FINAL_G0_PENDING,
                "readme_review_outcome": FINAL_G0_PENDING,
            },
        )

        validation_errors, _ = validate_bundle(
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertTrue(
            any(
                "final-G0 source/resource review" in error
                for error in validation_errors
            ),
            validation_errors,
        )
        self.final_g0_validator.assert_called_once_with(
            self.root.resolve(), self.source_commit
        )
