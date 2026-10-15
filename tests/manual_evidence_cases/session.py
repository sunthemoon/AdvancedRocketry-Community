from scripts.collect_v002_manual_evidence import APPLICABILITY
from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import LOG_ROLES
from scripts.collect_v002_manual_evidence import OBSERVATIONS
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import SCREENSHOT_ROLES
from scripts.collect_v002_manual_evidence import _profile_inventory_sha256
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import create_template
from scripts.collect_v002_manual_evidence import validate_bundle
from scripts.collect_v002_manual_evidence import validate_session
from unittest.mock import patch
import hashlib
import json


class SessionCases:
    """Session templates, bundle outcomes and lifecycle evidence."""

    def test_template_is_fixed_and_blocked_by_default(self) -> None:
        output = self.build / "template" / "session.json"

        create_template(output, self.root)

        document = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(
            {
                "source": f"build/libs/{self.artifact_name}",
                "server": (
                    "build/v0.0.2-manual/server/mods/"
                    f"{self.artifact_name}"
                ),
                "client": (
                    "build/v0.0.2-manual/client-matching/mods/"
                    f"{self.artifact_name}"
                ),
            },
            document["artifacts"],
        )
        self.assertEqual(set(OBSERVATIONS), set(document["observations"]))
        self.assertEqual(set(SCREENSHOT_ROLES), set(document["evidence"]))
        self.assertEqual(set(LOG_ROLES), set(document["log_excerpts"]))
        self.assertEqual({"matching", "missing_mod"}, set(document["client_profiles"]))
        self.assertEqual(
            {"MISSING"},
            {item["status"] for item in document["client_profiles"].values()},
        )
        self.assertEqual(5, len(APPLICABILITY))
        self.assertEqual(set(APPLICABILITY), set(document["applicability_reviews"]))
        self.assertEqual(
            {"BLOCKED"},
            {item["outcome"] for item in document["observations"].values()},
        )
        self.assertEqual(
            {"PENDING"},
            {
                item["decision"]
                for item in document["applicability_reviews"].values()
            },
        )
        self.assertEqual("MISSING", document["server_harness"]["status"])

    def test_ready_bundle_redacts_private_log_data_and_validates_strictly(self) -> None:
        errors, record, output = self.collect(self.ready_session())

        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        assert record is not None
        expected_matching_directory = "build/v0.0.2-manual/client-matching"
        self.assertEqual(
            f"{expected_matching_directory}/mods/{self.artifact_name}",
            record["artifacts"]["client"]["path"],
        )
        self.assertEqual(
            expected_matching_directory,
            record["client_profiles"]["matching"]["game_directory"],
        )
        self.assertEqual(
            "READY_FOR_HUMAN_GATE_REVIEW", record["review_readiness"]["status"]
        )
        validation_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertEqual([], validation_errors)

        archived_text = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (output / "logs").glob("*.txt")
        )
        record_text = (output / RECORD_NAME).read_text(encoding="utf-8")
        combined = archived_text + record_text
        for private in (
            "SecretPlayer",
            "123e4567-e89b-42d3-a456-426614174000",
            "private user",
            "203.0.113.8",
            "ghp_abcdefghijklmnopqrstuvwxyz",
        ):
            self.assertNotIn(private, combined)
        self.assertIn("127.0.0.1", combined)
        self.assertIn("[REDACTED_TEST_PLAYER]", archived_text)
        self.assertFalse(any("player_names" in key for key in record))
        self.assertIn("not set or approve any release Gate", record["scope_statement"])
        receipt = record["log_excerpts"][
            "mismatch_server_attempt_save_stop"
        ]["receipt"]
        self.assertEqual(
            receipt["sha256"],
            record["log_excerpts"]["mismatch_server_attempt_save_stop"][
                "mismatch_server_binding"
            ]["receipt_sha256"],
        )
        self.assertTrue((output / receipt["file"]).is_file())
        self.assertNotEqual(
            record["client_profiles"]["matching"]["game_directory"],
            record["client_profiles"]["missing_mod"]["game_directory"],
        )
        self.assertNotEqual(
            record["log_excerpts"]["matching_client_connection"]["source_path"],
            record["log_excerpts"]["mismatch_attempt"]["source_path"],
        )
        for profile in record["client_profiles"].values():
            self.assertEqual("PRESENT", profile["status"])
            for phase in ("before_snapshot", "after_snapshot"):
                self.assertTrue((output / profile[phase]["file"]).is_file())

    def test_fail_is_archived_but_never_acceptance_ready(self) -> None:
        session = self.ready_session()
        session["observations"]["MANUAL-V002-003"]["outcome"] = "FAIL"
        session["observations"]["MANUAL-V002-003"][
            "actual"
        ] = "The observed indicator contradicted the declared policy."

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        default_errors, _ = validate_bundle(output, self.root)
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertEqual([], default_errors)
        self.assertTrue(any("not PASS" in error for error in strict_errors), strict_errors)

    def test_committed_bundle_validates_without_raw_build_inputs(self) -> None:
        session = self.ready_session()
        session_path = self.write_session(session, "committed-session.json")
        output = self.root / COMMITTED_BUNDLE
        errors, record = collect_evidence(session_path, output, self.root)
        self.assertEqual([], errors)
        self.assertIsNotNone(record)

        for path in self.jar_paths.values():
            path.unlink()
        for item in session["evidence"].values():
            (self.root / item["source"]).unlink()
        raw_logs = {
            self.root / item["source"] for item in session["log_excerpts"].values()
        }
        for raw_log in raw_logs:
            raw_log.unlink()
        for profile in session["client_profiles"].values():
            for phase in ("before_snapshot", "after_snapshot"):
                (self.root / profile[phase]).unlink()
        (
            self.root
            / session["log_excerpts"]["mismatch_server_attempt_save_stop"][
                "receipt"
            ]
        ).unlink()
        (self.root / session["server_harness"]["summary"]).unlink()

        validation_errors, validated = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )

        self.assertEqual([], validation_errors)
        self.assertEqual(
            "READY_FOR_HUMAN_GATE_REVIEW",
            validated["review_readiness"]["status"],
        )

    def test_committed_bundle_rejects_payload_and_record_hash_tampering(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "tamper-session.json"), output, self.root
        )
        self.assertEqual([], errors)

        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        record["artifacts"]["client"]["sha256"] = "0" * 64
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        hash_errors, _ = validate_bundle(output, self.root)
        self.assertTrue(
            any("metadata does not match" in error for error in hash_errors),
            hash_errors,
        )

        record["artifacts"]["client"]["sha256"] = self.artifact_hash
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        archived_log = output / "logs" / "mismatch_attempt.txt"
        archived_log.write_text("tampered evidence\n", encoding="utf-8")
        payload_errors, _ = validate_bundle(output, self.root)
        self.assertTrue(
            any("log excerpt metadata mismatch" in error for error in payload_errors),
            payload_errors,
        )

    def test_committed_profile_inventory_cannot_rebind_false_jar_size(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "profile-size-tamper-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        profile = record["client_profiles"]["matching"]

        rebound_inventory = ""
        for phase in ("before_snapshot", "after_snapshot"):
            snapshot_record = profile[phase]
            snapshot_path = output / snapshot_record["file"]
            document = json.loads(snapshot_path.read_text(encoding="utf-8"))
            document["mods_files"][0]["size"] += 1
            document["inventory_sha256"] = _profile_inventory_sha256(document)
            payload = (
                json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True)
                + "\n"
            ).encode("utf-8")
            digest = hashlib.sha256(payload).hexdigest()
            snapshot_path.write_bytes(payload)
            snapshot_record.update(
                source_sha256=digest,
                sha256=digest,
                size=len(payload),
            )
            rebound_inventory = document["inventory_sha256"]
        profile["inventory_sha256"] = rebound_inventory
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        validation_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )

        self.assertTrue(
            any(
                "matching client profile snapshot must contain exactly" in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_committed_bundle_rejects_client_log_profile_rebinding(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "profile-log-rebind-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        item = record["log_excerpts"]["matching_client_connection"]
        rebound_path = "build/unrelated-client/logs/latest.log"
        item["source_path"] = rebound_path
        item["source_audit"]["source_path"] = rebound_path
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        validation_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )

        self.assertTrue(
            any(
                "matching_client_connection raw log is not bound to the matching "
                "client profile" in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_total_screenshot_payload_is_bounded(self) -> None:
        session = self.ready_session()

        with patch(
            "scripts.collect_v002_manual_evidence.MAX_SCREENSHOT_TOTAL", 1
        ):
            errors, _, output = self.collect(session)

        self.assertTrue(any("total screenshot payload" in error for error in errors))
        self.assertFalse(output.exists())

    def test_out_of_order_server_lifecycle_blocks_acceptance_readiness(self) -> None:
        session = self.ready_session()
        lifecycle = self.root / session["log_excerpts"][
            "server_first_join_leave_save_stop"
        ]["source"]
        lines = lifecycle.read_text(encoding="utf-8").splitlines()
        lines[2], lines[3] = lines[3], lines[2]
        lifecycle.write_text("\n".join(lines) + "\n", encoding="utf-8")
        self.refresh_summary_log_hash(session, "first-start", lifecycle)
        self.refresh_mismatch_receipt(session)

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertFalse(
            record["log_excerpts"]["server_first_join_leave_save_stop"][
                "lifecycle_markers"
            ]["order_valid"]
        )
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        default_errors, _ = validate_bundle(output, self.root)
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertEqual([], default_errors)
        self.assertTrue(
            any("lifecycle markers" in error for error in strict_errors),
            strict_errors,
        )

    def test_restart_lifecycle_requires_done_before_join(self) -> None:
        session = self.ready_session()
        lifecycle = self.root / session["log_excerpts"][
            "server_restart_reconnect_save_stop"
        ]["source"]
        lines = lifecycle.read_text(encoding="utf-8").splitlines()
        done = lines.pop(0)
        lines.insert(1, done)
        lifecycle.write_text("\n".join(lines) + "\n", encoding="utf-8")
        self.refresh_summary_log_hash(session, "restart", lifecycle)
        self.refresh_mismatch_receipt(session)

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertFalse(
            record["log_excerpts"]["server_restart_reconnect_save_stop"][
                "lifecycle_markers"
            ]["order_valid"]
        )
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(
            any("server_restart_reconnect_save_stop" in error for error in strict_errors),
            strict_errors,
        )

    def test_blocked_attempt_with_missing_evidence_is_archived(self) -> None:
        session = self.ready_session()
        session["privacy"]["player_names"] = []
        session["privacy"]["visual_review"] = {
            "completed": False,
            "reviewed_by": "",
            "reviewed_at": "",
            "notes": "",
        }
        for item in session["observations"].values():
            item["outcome"] = "BLOCKED"
            item["actual"] = "Packaged client could not be launched on the test host."
        for item in session["evidence"].values():
            item.update(status="MISSING", source="", note="Client did not launch.")
        for item in session["log_excerpts"].values():
            item.update(status="MISSING", source="", note="Client did not launch.")
            item["warning_disposition"] = {
                "status": "PENDING",
                "warning_count": None,
                "origins": [],
                "explanation": "",
            }
        session["log_excerpts"]["mismatch_server_attempt_save_stop"][
            "server_exit_code"
        ] = None
        session["log_excerpts"]["mismatch_server_attempt_save_stop"][
            "receipt"
        ] = ""
        session["server_harness"] = {
            "status": "MISSING",
            "summary": "",
            "note": "Manual player-cycle harness was not run.",
        }
        session["findings"] = {
            **{key: None for key in session["findings"] if key != "notes"},
            "notes": "Counts unavailable because launch was blocked.",
        }
        for review in session["applicability_reviews"].values():
            review.update(
                decision="PENDING", reviewed_by="", reviewed_at="", notes=""
            )

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        validation_errors, _ = validate_bundle(output, self.root)
        self.assertEqual([], validation_errors)

    def test_pass_claim_with_missing_fixed_role_is_rejected(self) -> None:
        session = self.ready_session()
        session["evidence"]["matching_reconnect"].update(
            status="MISSING", source="", note="Not captured."
        )

        errors = validate_session(session)

        self.assertTrue(any("cannot claim PASS" in error for error in errors), errors)

    def test_unknown_evidence_role_is_rejected(self) -> None:
        session = self.ready_session()
        session["evidence"]["arbitrary_screenshot"] = {
            "status": "MISSING",
            "source": "",
            "note": "Unexpected.",
        }

        errors = validate_session(session)

        self.assertTrue(any("unexpected keys" in error for error in errors), errors)

    def test_jar_hash_mismatch_is_rejected(self) -> None:
        session = self.ready_session()
        self.jar_paths["client"].write_bytes(b"different-client-copy")

        errors, _, output = self.collect(session)

        self.assertTrue(any("client JAR SHA-256" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_same_physical_jar_is_rejected(self) -> None:
        session = self.ready_session()
        session["artifacts"]["client"] = session["artifacts"]["server"]

        errors, _, _ = self.collect(session)

        self.assertTrue(any("distinct physical copy" in error for error in errors), errors)
