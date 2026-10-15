from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import read_bounded_bytes
from scripts.collect_v002_manual_evidence import scan_log_text
from scripts.collect_v002_manual_evidence import validate_bundle
from tests.manual_evidence_cases.png import make_png
from unittest.mock import patch
import json
import os


class SourceLogCases:
    """Source log decoding and physical identity."""

    def test_gb18030_client_log_is_scanned_and_archived(self) -> None:
        session = self.ready_session()
        source = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        text = source.read_text(encoding="utf-8") + "客户端启动完成\n"
        source.write_bytes(text.encode("gb18030"))

        errors, record, output = self.collect(session, "gb18030-client-log")

        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        self.assertTrue(output.is_dir())

    def test_log4j_xml_fragment_levels_and_project_logger_are_scanned(self) -> None:
        text = """\
  <log4j:Event logger="io.github.example.AdvancedRocketryCommunity" timestamp="1" level="WARN" thread="main">
    <log4j:Message><![CDATA[project warning]]></log4j:Message>
  </log4j:Event>
  <log4j:Event logger="net.minecraftforge.network.NetworkHooks" timestamp="2" level="ERROR" thread="main">
    <log4j:Message><![CDATA[forge error]]></log4j:Message>
  </log4j:Event>
  <log4j:Event logger="net.minecraft.client.Minecraft" timestamp="3" level="INFO" thread="main">
    <log4j:Message><![CDATA[type=ERROR NoClassDefFoundError: net.minecraft.client.Example]]></log4j:Message>
  </log4j:Event>
"""

        counts = scan_log_text(text)

        self.assertEqual(1, counts["warning_count"])
        self.assertEqual(1, counts["project_warning_count"])
        self.assertEqual(1, counts["error_count"])
        self.assertEqual(0, counts["project_error_count"])
        self.assertEqual(0, counts["fatal_count"])
        self.assertEqual(1, counts["client_linkage_failure_count"])

    def test_gb18030_mismatch_server_log_is_scanned_and_bound(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        text = source.read_text(encoding="utf-8") + "服务器保存完成\n"
        payload = text.encode("gb18030")
        source.write_bytes(payload)
        runtime = self.jar_paths["server"].parent.parent / "logs" / "latest.log"
        runtime.write_bytes(payload)
        self.refresh_mismatch_receipt(session)

        errors, record, output = self.collect(session, "gb18030-server-log")

        self.assertEqual([], errors)
        self.assertIsNotNone(record)
        self.assertTrue(output.is_dir())

    def test_mismatch_client_log_cannot_reuse_matching_profile_log(self) -> None:
        session = self.ready_session()
        matching_item = session["log_excerpts"]["matching_client_connection"]
        matching_log = self.root / matching_item["source"]
        with matching_log.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write(
                "[Render thread/INFO] [minecraft/ConnectScreen]: "
                "Connecting to 127.0.0.1, 25565\n"
            )
        mismatch = session["log_excerpts"]["mismatch_attempt"]
        mismatch["source"] = matching_item["source"]
        mismatch["line_start"] = 5
        mismatch["line_end"] = 5

        errors, _, output = self.collect(session)

        self.assertTrue(
            any("missing_mod client profile logs directory" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_cross_profile_client_logs_must_not_be_hard_links(self) -> None:
        session = self.ready_session()
        matching_item = session["log_excerpts"]["matching_client_connection"]
        matching_log = self.root / matching_item["source"]
        with matching_log.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write(
                "[Render thread/INFO] [minecraft/ConnectScreen]: "
                "Connecting to 127.0.0.1, 25565\n"
            )
        mismatch_item = session["log_excerpts"]["mismatch_attempt"]
        mismatch_log = self.root / mismatch_item["source"]
        mismatch_log.unlink()
        os.link(matching_log, mismatch_log)
        mismatch_item["line_start"] = 5
        mismatch_item["line_end"] = 5

        errors, _, output = self.collect(session, "hard-linked-client-logs")

        self.assertTrue(os.path.samefile(matching_log, mismatch_log))
        self.assertTrue(
            any("physical file or hard link" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_build_bundle_rejects_late_cross_profile_hard_link(self) -> None:
        session = self.ready_session()
        errors, _, output = self.collect(session, "late-hard-linked-client-logs")
        self.assertEqual([], errors)
        matching_log = self.root / session["log_excerpts"][
            "matching_client_connection"
        ]["source"]
        mismatch_log = self.root / session["log_excerpts"]["mismatch_attempt"][
            "source"
        ]
        mismatch_log.unlink()
        os.link(matching_log, mismatch_log)

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any("physical file or hard link" in error for error in validation_errors),
            validation_errors,
        )

    def test_committed_bundle_rejects_cross_profile_physical_log_identity(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "physical-log-record-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        matching_identity = record["log_excerpts"][
            "matching_client_connection"
        ]["source_audit"]["physical_file_identity"]
        record["log_excerpts"]["mismatch_attempt"]["source_audit"][
            "physical_file_identity"
        ] = matching_identity
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                "physical-file identity across matching" in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_blocked_session_can_archive_missing_profile_snapshots(self) -> None:
        session = self.ready_session()
        for observation in session["observations"].values():
            observation["outcome"] = "BLOCKED"
            observation["actual"] = "Client profile evidence was not captured."
        for role in ("matching", "missing_mod"):
            session["client_profiles"][role] = {
                "status": "MISSING",
                "game_directory": "",
                "before_snapshot": "",
                "after_snapshot": "",
                "note": "Profile snapshot was not captured.",
            }

        errors, record, output = self.collect(session)

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("INCOMPLETE", record["review_readiness"]["status"])
        self.assertFalse((output / "client-profiles").exists())
        validation_errors, _ = validate_bundle(output, self.root)
        self.assertEqual([], validation_errors)
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertTrue(
            any("client profile before/after binding" in error for error in strict_errors),
            strict_errors,
        )

    def test_input_outside_build_is_rejected(self) -> None:
        session = self.ready_session()
        outside = self.root / "outside.png"
        outside.write_bytes(make_png())
        (self.root / ".git" / "info" / "exclude").write_text(
            "outside.png\n", encoding="utf-8", newline="\n"
        )
        session["evidence"]["mods_page"]["source"] = "outside.png"

        errors, _, _ = self.collect(session)

        self.assertTrue(any("under the repository build" in error for error in errors), errors)

    def test_symlink_input_is_rejected(self) -> None:
        session = self.ready_session()
        target = self.build / "capture" / "mods_page.png"
        link = self.build / "capture" / "linked.png"
        try:
            link.symlink_to(target)
        except OSError as exc:
            self.skipTest(f"symlinks unavailable: {exc}")
        session["evidence"]["mods_page"]["source"] = link.relative_to(
            self.root
        ).as_posix()

        errors, _, _ = self.collect(session)

        self.assertTrue(any("symlink or junction" in error for error in errors), errors)

    def test_bounded_read_rechecks_pathname_identity_after_read(self) -> None:
        path = self.build / "bounded-path-identity.txt"
        replacement = self.build / "bounded-path-replacement.txt"
        path.write_bytes(b"original")
        replacement.write_bytes(b"replaced")
        initial_stat = path.stat()
        replacement_stat = replacement.stat()

        with patch.object(
            type(path),
            "stat",
            side_effect=[initial_stat, replacement_stat],
        ):
            with self.assertRaisesRegex(ValueError, "pathname changed"):
                read_bounded_bytes(path, 64, "bounded fixture")
