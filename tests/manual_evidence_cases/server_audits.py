from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import validate_bundle
import hashlib
import json


class ServerAuditCases:
    """Server findings and player lifecycle audit."""

    def test_raw_log_error_cannot_be_reported_as_zero(self) -> None:
        session = self.ready_session()
        client_log = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        with client_log.open("a", encoding="utf-8") as stream:
            stream.write(
                "[main/ERROR] [advancedrocketrycommunity/]: visible contradiction\n"
            )

        errors, _, output = self.collect(session)

        self.assertTrue(
            any("client_project_error_count" in error and "found 1" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_server_summary_audit_must_match_bound_raw_log(self) -> None:
        session = self.ready_session()
        first_log = self.root / session["log_excerpts"][
            "server_first_join_leave_save_stop"
        ]["source"]
        with first_log.open("a", encoding="utf-8") as stream:
            stream.write(
                "[Server thread/WARN] [advancedrocketrycommunity/]: "
                "raw log contradicts summary\n"
            )
        self.refresh_summary_log_hash(session, "first-start", first_log)
        self.set_warning_disposition(
            session, ("server_first_join_leave_save_stop",), count=1
        )
        summary_path = self.root / session["server_harness"]["summary"]
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        first_cycle = next(
            item for item in summary["cycles"] if item["name"] == "first-start"
        )
        first_cycle["warning_count"] = 1
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        session["findings"]["server_project_warning_count"] = 1
        session["findings"]["notes"] = "Preserved project warning for review."

        errors, _, output = self.collect(session)

        self.assertTrue(
            any(
                "raw log project_warning_count is 1" in error
                and "reports 0" in error
                for error in errors
            ),
            errors,
        )
        self.assertFalse(output.exists())

    def test_committed_server_audit_must_match_archived_harness_summary(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "audit-tamper-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        role = "server_first_join_leave_save_stop"
        record["log_excerpts"][role]["source_audit"]["audit_counts"][
            "warning_count"
        ] = 1
        record["log_excerpts"][role]["warning_disposition"] = {
            "status": "ACCEPTED",
            "warning_count": 1,
            "origins": ["Test logger"],
            "explanation": "Tamper fixture keeps disposition structurally valid.",
        }
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                f"{role} raw log warning_count differs from its harness cycle"
                in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_nonzero_finding_blocks_strict_review_readiness(self) -> None:
        session = self.ready_session()
        client_log = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        with client_log.open("a", encoding="utf-8") as stream:
            stream.write(
                "[Render thread/WARN] [advancedrocketrycommunity/]: "
                "preserved client warning\n"
            )
        self.set_warning_disposition(
            session,
            (
                "client_startup_world",
                "matching_client_connection",
            ),
            count=1,
        )
        session["findings"]["client_project_warning_count"] = 1
        session["findings"]["notes"] = "Preserved project warning for review."

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        assert record is not None
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        self.assertIn(
            "client_project_warning_count is 1, not 0",
            record["review_readiness"]["blockers"],
        )
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(
            any("client_project_warning_count is 1, not 0" in error for error in strict_errors),
            strict_errors,
        )

    def test_archived_log_is_independently_rescanned_after_metadata_update(self) -> None:
        errors, _, output = self.collect(self.ready_session())
        self.assertEqual([], errors)
        target = output / "logs" / "client_startup_world.txt"
        lines = target.read_text(encoding="utf-8").splitlines()
        lines[0] = "[main/ERROR] [advancedrocketrycommunity/]: archived contradiction"
        payload = ("\n".join(lines) + "\n").encode("utf-8")
        target.write_bytes(payload)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        item = record["log_excerpts"]["client_startup_world"]
        item["sha256"] = hashlib.sha256(payload).hexdigest()
        item["size"] = len(payload)
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any("archived log audit mismatch" in error for error in validation_errors),
            validation_errors,
        )

    def test_server_log_must_match_harness_cycle_hash(self) -> None:
        session = self.ready_session()
        first_log = self.root / session["log_excerpts"][
            "server_first_join_leave_save_stop"
        ]["source"]
        with first_log.open("a", encoding="utf-8") as stream:
            stream.write("additional unbound line\n")

        errors, _, output = self.collect(session)

        self.assertTrue(any("does not match harness cycle" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_server_log_filename_must_match_harness_cycle(self) -> None:
        session = self.ready_session()
        role = "server_first_join_leave_save_stop"
        first_log = self.root / session["log_excerpts"][role]["source"]
        renamed = first_log.with_name("unbound-copy.txt")
        first_log.rename(renamed)
        session["log_excerpts"][role]["source"] = renamed.relative_to(
            self.root
        ).as_posix()

        errors, _, output = self.collect(session)

        self.assertTrue(
            any("filename does not match harness cycle" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_server_summary_must_confirm_same_world(self) -> None:
        session = self.ready_session()
        summary_path = self.root / session["server_harness"]["summary"]
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        summary["world"]["same_world_verified"] = False
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        errors, _, output = self.collect(session)

        self.assertTrue(any("same named world" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_false_player_redaction_term_is_rejected(self) -> None:
        session = self.ready_session()
        session["privacy"]["player_names"] = ["FakePlayer"]

        errors, _, output = self.collect(session)

        self.assertTrue(
            any("absent from privacy.player_names" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_different_join_and_leave_players_are_rejected(self) -> None:
        session = self.ready_session()
        session["privacy"]["player_names"].append("OtherPlayer")
        role = "server_first_join_leave_save_stop"
        first_log = self.root / session["log_excerpts"][role]["source"]
        first_log.write_text(
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "OtherPlayer left the game\n"
            "Saved the game\n"
            "Stopping server\n",
            encoding="utf-8",
        )
        self.refresh_summary_log_hash(session, "first-start", first_log)

        errors, _, output = self.collect(session)

        self.assertTrue(any("join and leave identities differ" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_different_players_across_cycles_are_rejected(self) -> None:
        session = self.ready_session()
        session["privacy"]["player_names"].append("OtherPlayer")
        role = "server_restart_reconnect_save_stop"
        restart_log = self.root / session["log_excerpts"][role]["source"]
        restart_log.write_text(
            "Done (1.00s)! For help, type help\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "OtherPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "OtherPlayer left the game\n"
            "Saved the game\n"
            "Stopping server\n",
            encoding="utf-8",
        )
        self.refresh_summary_log_hash(session, "restart", restart_log)
        errors, _, output = self.collect(session)

        self.assertTrue(any("same player identity" in error for error in errors), errors)
        self.assertFalse(output.exists())
