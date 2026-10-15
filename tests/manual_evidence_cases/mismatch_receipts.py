from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import validate_bundle
from scripts.collect_v002_manual_evidence import validate_session
import hashlib
import json
import os


class MismatchReceiptCases:
    """Mismatch receipt and runtime binding."""

    def test_missing_mismatch_server_evidence_rejects_pass(self) -> None:
        session = self.ready_session()
        item = session["log_excerpts"]["mismatch_server_attempt_save_stop"]
        item.update(
            status="MISSING",
            source="",
            note="Third server cycle was not captured.",
            server_exit_code=None,
            receipt="",
            warning_disposition={
                "status": "PENDING",
                "warning_count": None,
                "origins": [],
                "explanation": "",
            },
        )

        errors = validate_session(session)

        self.assertTrue(any("cannot claim PASS" in error for error in errors), errors)

    def test_mismatch_nonzero_exit_blocks_strict_readiness(self) -> None:
        session = self.ready_session()
        self.refresh_mismatch_receipt(session, exit_code=7)

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertIn(
            "missing-mod third server exit code is 7, not 0",
            record["review_readiness"]["blockers"],
        )
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(any("exit code is 7" in error for error in strict_errors))

    def test_mismatch_server_client_linkage_always_blocks_readiness(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = source.read_text(encoding="utf-8") + (
            "java.lang.NoClassDefFoundError: net.minecraft.client.Minecraft\n"
        )
        self.replace_mismatch_server_log(session, payload)
        session["observations"]["MANUAL-V002-002"]["outcome"] = "BLOCKED"
        session["observations"]["MANUAL-V002-002"][
            "actual"
        ] = "Client-class linkage appeared during the third server cycle."
        session["findings"]["client_class_linkage_failure_count"] = None

        errors, record, output = self.collect(session, "mismatch-linkage")

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual(
            1,
            record["log_excerpts"][role]["source_audit"]["audit_counts"][
                "client_linkage_failure_count"
            ],
        )
        self.assertTrue(
            any(
                "client-class linkage" in blocker
                for blocker in record["review_readiness"]["blockers"]
            )
        )
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(any("client-class linkage" in error for error in strict_errors))

    def test_mismatch_server_rejects_harness_cycle_log_hash_reuse(self) -> None:
        session = self.ready_session()
        restart = self.root / session["log_excerpts"][
            "server_restart_reconnect_save_stop"
        ]["source"]
        self.replace_mismatch_server_log(
            session, restart.read_text(encoding="utf-8")
        )

        errors, _, output = self.collect(session, "mismatch-cycle-hash-reuse")

        self.assertTrue(
            any("reuses a harness-cycle log hash" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_mismatch_server_rejects_harness_cycle_physical_file_reuse(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        restart = self.root / session["log_excerpts"][
            "server_restart_reconnect_save_stop"
        ]["source"]
        mismatch = self.root / session["log_excerpts"][role]["source"]
        mismatch.unlink()
        os.link(restart, mismatch)
        runtime = self.jar_paths["server"].parent.parent / "logs" / "latest.log"
        runtime.write_bytes(restart.read_bytes())
        session["log_excerpts"][role]["line_end"] = len(
            restart.read_text(encoding="utf-8").splitlines()
        )
        self.refresh_mismatch_receipt(session)

        errors, _, output = self.collect(session, "mismatch-cycle-hardlink-reuse")

        self.assertTrue(
            any("server raw logs must not reuse one physical file" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_mismatch_receipt_rejects_java_tamper(self) -> None:
        session = self.ready_session()
        receipt = self.root / session["log_excerpts"][
            "mismatch_server_attempt_save_stop"
        ]["receipt"]
        document = json.loads(receipt.read_text(encoding="utf-8"))
        document["java_version"] = "21.0.1"
        receipt.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        errors, _, output = self.collect(session, "mismatch-receipt-java-tamper")

        self.assertTrue(any("identify Java 17" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_mismatch_receipt_rejects_run_id_tamper(self) -> None:
        session = self.ready_session()
        receipt = self.root / session["log_excerpts"][
            "mismatch_server_attempt_save_stop"
        ]["receipt"]
        document = json.loads(receipt.read_text(encoding="utf-8"))
        document["run_id"] = "v002-mismatch-not-a-run"
        receipt.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        errors, _, output = self.collect(session, "mismatch-receipt-run-id-tamper")

        self.assertTrue(any("run_id is invalid" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_mismatch_receipt_rejects_timestamp_tamper(self) -> None:
        session = self.ready_session()
        receipt = self.root / session["log_excerpts"][
            "mismatch_server_attempt_save_stop"
        ]["receipt"]
        document = json.loads(receipt.read_text(encoding="utf-8"))
        document["started_at"] = "2026-08-27T12:09:00+00:00"
        receipt.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        errors, _, output = self.collect(session, "mismatch-receipt-time-tamper")

        self.assertTrue(
            any("timestamps must be aware, ordered" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_mismatch_active_security_property_tamper_is_rejected(self) -> None:
        session = self.ready_session()
        active = self.jar_paths["server"].parent.parent / "server.properties"
        active.write_bytes(
            active.read_bytes().replace(b"online-mode=false", b"online-mode=true")
        )
        self.refresh_mismatch_receipt(session)

        errors, _, output = self.collect(session, "mismatch-active-properties-tamper")

        self.assertTrue(any("online-mode" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_extra_server_mod_is_rejected(self) -> None:
        session = self.ready_session()
        extra = self.jar_paths["server"].parent / "extra-server-mod.jar"
        extra.write_bytes(b"extra")

        errors, _, output = self.collect(session, "extra-server-mod")

        self.assertTrue(any("only the project JAR" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_mismatch_receipt_log_digest_must_match_retained_full_log(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        receipt = self.root / session["log_excerpts"][role]["receipt"]
        document = json.loads(receipt.read_text(encoding="utf-8"))
        document["full_log_sha256"] = "0" * 64
        receipt.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        errors, _, output = self.collect(session)

        self.assertTrue(any("differs from the retained full log" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_mismatch_receipt_exit_must_match_session_record(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        session["log_excerpts"][role]["server_exit_code"] = 9

        errors, _, output = self.collect(session)

        self.assertTrue(any("differs from the session record" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_committed_bundle_rejects_mismatch_receipt_cross_binding_tamper(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "receipt-tamper-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        receipt_path = output / "server" / "mismatch-server-receipt.json"
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        receipt["full_log_sha256"] = "0" * 64
        receipt_path.write_text(
            json.dumps(receipt, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        item = record["log_excerpts"]["mismatch_server_attempt_save_stop"]
        payload = receipt_path.read_bytes()
        item["receipt"]["sha256"] = hashlib.sha256(payload).hexdigest()
        item["receipt"]["size"] = len(payload)
        item["receipt"]["full_log_sha256"] = "0" * 64
        item["mismatch_server_binding"]["receipt_sha256"] = item["receipt"][
            "sha256"
        ]
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any("differs from the retained full log" in error for error in validation_errors),
            validation_errors,
        )

    def test_committed_bundle_cross_checks_receipt_and_active_properties(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "properties-cross-binding-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        receipt_path = output / "server" / "mismatch-server-receipt.json"
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        receipt["active_server_properties_sha256"] = "0" * 64
        receipt_path.write_text(
            json.dumps(receipt, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        item = record["log_excerpts"]["mismatch_server_attempt_save_stop"]
        payload = receipt_path.read_bytes()
        item["receipt"]["sha256"] = hashlib.sha256(payload).hexdigest()
        item["receipt"]["size"] = len(payload)
        item["receipt"]["active_server_properties_sha256"] = "0" * 64
        item["mismatch_server_binding"]["receipt_sha256"] = item["receipt"][
            "sha256"
        ]
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                "receipt differs from active server.properties" in error
                for error in validation_errors
            ),
            validation_errors,
        )
