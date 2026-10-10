from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import validate_bundle
import hashlib
import json


class ArchivedPropertiesCases:
    """Archived canonical server-properties binding."""

    def test_committed_bundle_binds_archived_server_properties_hash(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "properties-tamper-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        item = record["log_excerpts"]["mismatch_server_attempt_save_stop"]
        properties_path = output / item["server_properties"]["file"]
        self.assertTrue(properties_path.is_file())
        item["mismatch_server_binding"]["server_properties_sha256"] = "0" * 64
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        validation_errors, _ = validate_bundle(
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertTrue(
            any("server_properties_sha256" in error for error in validation_errors),
            validation_errors,
        )

    def test_committed_bundle_binds_canonical_raw_server_properties_hash(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "raw-properties-tamper-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        item = record["log_excerpts"]["mismatch_server_attempt_save_stop"]
        properties_path = output / item["server_properties"]["file"]
        properties = json.loads(properties_path.read_text(encoding="utf-8"))
        properties["source_sha256"] = "0" * 64
        payload = (
            json.dumps(properties, ensure_ascii=True, indent=2, sort_keys=True) + "\n"
        ).encode("utf-8")
        properties_path.write_bytes(payload)
        archive_sha256 = hashlib.sha256(payload).hexdigest()
        item["server_properties"].update(
            {
                "sha256": archive_sha256,
                "size": len(payload),
                "source_sha256": properties["source_sha256"],
            }
        )
        # Rebind every attacker-controlled archived hash. Validation must still
        # derive the raw properties digest from the canonical harness payload.
        item["mismatch_server_binding"]["server_properties_sha256"] = archive_sha256
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        validation_errors, _ = validate_bundle(
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertTrue(
            any(
                "canonical harness startup-properties" in error
                for error in validation_errors
            ),
            validation_errors,
        )


class UnsafeParentCases:
    """Parent-link rejection before archived reads."""

    def test_mismatch_runtime_parent_symlink_is_rejected(self) -> None:
        session = self.ready_session()
        server_root = self.jar_paths["server"].parent.parent
        logs = server_root / "logs"
        outside = self.build / "outside-runtime-logs"
        logs.rename(outside)
        try:
            logs.symlink_to(outside, target_is_directory=True)
        except OSError as exc:
            outside.rename(logs)
            self.skipTest(f"directory symlinks unavailable: {exc}")

        errors, _, output = self.collect(session)

        self.assertTrue(any("symlink or junction" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_archived_profile_snapshot_parent_link_is_rejected_before_read(
        self,
    ) -> None:
        self.assert_bundle_parent_link_rejected("client-profiles")

    def test_archived_server_summary_parent_link_is_rejected_before_read(
        self,
    ) -> None:
        self.assert_bundle_parent_link_rejected("server")

    def test_archived_screenshot_parent_link_is_rejected_before_read(self) -> None:
        self.assert_bundle_parent_link_rejected("screenshots")

    def test_archived_log_parent_link_is_rejected_before_read(self) -> None:
        self.assert_bundle_parent_link_rejected("logs")
