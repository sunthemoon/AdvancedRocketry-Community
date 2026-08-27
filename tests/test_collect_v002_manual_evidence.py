import copy
import hashlib
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

from scripts.collect_v002_manual_evidence import (
    COMMITTED_BUNDLE,
    CONTENT_MANIFEST,
    LOG_ROLES,
    OBSERVATIONS,
    RECORD_NAME,
    SCREENSHOT_ROLES,
    build_template,
    collect_evidence,
    create_template,
    extract_log_excerpt,
    inspect_png,
    validate_bundle,
    validate_session,
)


def png_chunk(chunk_type: bytes, content: bytes) -> bytes:
    return (
        struct.pack(">I", len(content))
        + chunk_type
        + content
        + struct.pack(">I", zlib.crc32(chunk_type + content) & 0xFFFFFFFF)
    )


def make_png(
    width: int = 640,
    height: int = 360,
    *,
    metadata: tuple[bytes, bytes] | None = None,
    seed: int = 0,
) -> bytes:
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    pixel = bytes(((0x10 + seed) % 256, (0x20 + seed) % 256, (0x30 + seed) % 256))
    scanline = b"\0" + (pixel * width)
    chunks = [png_chunk(b"IHDR", header)]
    if metadata is not None:
        chunks.append(png_chunk(*metadata))
    chunks.extend(
        [png_chunk(b"IDAT", zlib.compress(scanline * height)), png_chunk(b"IEND", b"")]
    )
    return b"\x89PNG\r\n\x1a\n" + b"".join(chunks)


class ManualEvidenceTests(unittest.TestCase):
    artifact_name = "advancedrocketry-community-1.20.1-0.0.2-dev.jar"

    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.build = self.root / "build"
        self.build.mkdir()
        self.artifact_content = b"final-distributable-v002"
        self.artifact_hash = hashlib.sha256(self.artifact_content).hexdigest()
        manifest = self.root / CONTENT_MANIFEST
        manifest.parent.mkdir(parents=True)
        manifest.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "artifact": self.artifact_name,
                    "artifact_sha256": self.artifact_hash,
                    "entry_count": 0,
                    "entries": [],
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        self.jar_paths: dict[str, Path] = {}
        for role in ("source", "server", "client"):
            path = self.build / role / "mods" / self.artifact_name
            path.parent.mkdir(parents=True)
            path.write_bytes(self.artifact_content)
            self.jar_paths[role] = path

    def ready_session(self) -> dict[str, object]:
        session = build_template(self.artifact_name)
        session["metadata"] = {
            "source_commit": "a" * 40,
            "test_date": "2026-08-27",
            "tester_id": "external-tester-01",
            "environment": {
                "os": "Windows 11 test VM",
                "java": "17.0.16",
                "minecraft": "1.20.1",
                "forge": "47.4.10",
            },
        }
        session["artifacts"] = {
            role: path.relative_to(self.root).as_posix()
            for role, path in self.jar_paths.items()
        }
        session["privacy"] = {
            "player_names": ["SecretPlayer"],
            "visual_review": {
                "completed": True,
                "reviewed_by": "privacy-reviewer-01",
                "reviewed_at": "2026-08-27",
                "notes": "Reviewed every full-window capture for account and desktop data.",
            },
        }
        for observation in session["observations"].values():
            observation["outcome"] = "PASS"
            observation["actual"] = "Observed the expected packaged behavior."

        for seed, (role, item) in enumerate(session["evidence"].items()):
            screenshot = self.build / "capture" / f"{role}.png"
            screenshot.parent.mkdir(parents=True, exist_ok=True)
            screenshot.write_bytes(make_png(seed=seed))
            item.update(
                {
                    "status": "PRESENT",
                    "source": screenshot.relative_to(self.root).as_posix(),
                    "note": "Full Minecraft window; pixel content manually reviewed.",
                }
            )

        client_log = self.build / "logs" / "client-full.log"
        client_log.parent.mkdir(parents=True)
        client_log.write_text(
            "SecretPlayer initialized from C:\\Users\\private-user\\instance "
            "with UUID 123e4567-e89b-42d3-a456-426614174000\n"
            "remote 203.0.113.8 Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz\n"
            "matching connection to 127.0.0.1\n"
            "matching client entered the world\n"
            "missing-mod indicator observed\n"
            "missing-mod connection result recorded\n",
            encoding="utf-8",
        )
        first_log = self.build / "server" / "first-start-full.txt"
        first_log.parent.mkdir(parents=True, exist_ok=True)
        first_log.write_text(
            "SecretPlayer joined the game\n"
            "SecretPlayer left the game\n"
            "Saved the game\n"
            "Stopping server\n",
            encoding="utf-8",
        )
        restart_log = self.build / "server" / "restart-full.txt"
        restart_log.write_text(
            "Done (1.00s)! For help, type help\n"
            "SecretPlayer joined the game\n"
            "SecretPlayer left the game\n"
            "Saved the game\n"
            "Stopping server\n",
            encoding="utf-8",
        )
        log_inputs = {
            "client_startup_world": (client_log, 1, 2),
            "matching_client_connection": (client_log, 3, 4),
            "server_first_join_leave_save_stop": (first_log, 1, 4),
            "server_restart_reconnect_save_stop": (restart_log, 1, 5),
            "mismatch_attempt": (client_log, 5, 6),
        }
        for role, item in session["log_excerpts"].items():
            source, line_start, line_end = log_inputs[role]
            item.update(
                {
                    "status": "PRESENT",
                    "source": source.relative_to(self.root).as_posix(),
                    "line_start": line_start,
                    "line_end": line_end,
                    "note": "Selected lifecycle lines only.",
                }
            )

        session_id = "v002-" + "a" * 24
        cycle_base = {
            "error_count": 0,
            "warning_count": 0,
            "project_error_count": 0,
            "project_warning_count": 0,
            "client_linkage_failure_count": 0,
            "exit_code": 0,
            "mod_marker": "1.20.1-0.0.2-dev",
            "player_join_observed": True,
            "player_leave_observed": True,
            "status_protocol": 763,
            "status_version": "1.20.1",
        }
        summary = {
            "schema_version": 2,
            "session_id": session_id,
            "artifact": self.artifact_name,
            "artifact_sha256": self.artifact_hash,
            "completed_at": "2026-08-27T12:10:00+00:00",
            "cycles": [
                {
                    **cycle_base,
                    "completed_at": "2026-08-27T12:04:00+00:00",
                    "cycle_id": f"{session_id}-first-start",
                    "full_log_file": "first-start-full.txt",
                    "full_log_sha256": hashlib.sha256(first_log.read_bytes()).hexdigest(),
                    "name": "first-start",
                    "started_at": "2026-08-27T12:01:00+00:00",
                },
                {
                    **cycle_base,
                    "completed_at": "2026-08-27T12:09:00+00:00",
                    "cycle_id": f"{session_id}-restart",
                    "full_log_file": "restart-full.txt",
                    "full_log_sha256": hashlib.sha256(restart_log.read_bytes()).hexdigest(),
                    "name": "restart",
                    "started_at": "2026-08-27T12:05:00+00:00",
                },
            ],
            "forge": "47.4.10",
            "installer_sha1": "b" * 40,
            "installer_sha256": "c" * 64,
            "installer_attempts": 1,
            "java": "17.0.16",
            "manual_player_cycles": True,
            "minecraft": "1.20.1",
            "offline_mode": True,
            "platform": "Windows 11 test VM",
            "server_artifact_sha256": self.artifact_hash,
            "server_bind": "127.0.0.1",
            "server_port": 25565,
            "started_at": "2026-08-27T12:00:00+00:00",
            "world": {
                "identity": "d" * 64,
                "identity_marker": "world/.v002-smoke-world-identity.json",
                "identity_marker_sha256": "e" * 64,
                "level_dat_after_restart_sha256": "f" * 64,
                "level_dat_after_restart_size": 2048,
                "level_dat_before_restart_sha256": "0" * 64,
                "level_dat_before_restart_size": 1024,
                "level_name": "world",
                "same_world_verified": True,
            },
            "world_level_dat": True,
        }
        summary_path = self.build / "server-evidence" / "summary.json"
        summary_path.parent.mkdir()
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        session["server_harness"] = {
            "status": "PRESENT",
            "summary": summary_path.relative_to(self.root).as_posix(),
            "note": "Harness-generated matching-client cycles.",
        }
        session["findings"] = {
            "client_project_error_count": 0,
            "client_project_warning_count": 0,
            "server_project_error_count": 0,
            "server_project_warning_count": 0,
            "client_class_linkage_failure_count": 0,
            "notes": "",
        }
        for review in session["applicability_reviews"].values():
            review.update(
                {
                    "decision": "ACCEPT_NOT_APPLICABLE",
                    "reviewed_by": "scope-reviewer-01",
                    "reviewed_at": "2026-08-27",
                    "notes": "Accepted only for the v0.0.2 empty bootstrap scope.",
                }
            )
        return session

    def write_session(self, session: dict[str, object], name: str = "session.json") -> Path:
        path = self.build / name
        path.write_text(
            json.dumps(session, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        return path

    def refresh_summary_log_hash(
        self,
        session: dict[str, object],
        cycle_name: str,
        log_path: Path,
    ) -> None:
        summary_path = self.root / session["server_harness"]["summary"]
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        cycle = next(item for item in summary["cycles"] if item["name"] == cycle_name)
        cycle["full_log_sha256"] = hashlib.sha256(log_path.read_bytes()).hexdigest()
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

    def collect(
        self, session: dict[str, object], output_name: str = "bundle"
    ) -> tuple[list[str], dict[str, object] | None, Path]:
        output = self.build / output_name
        errors, record = collect_evidence(
            self.write_session(session, f"{output_name}-session.json"),
            output,
            self.root,
        )
        return errors, record, output

    def test_template_is_fixed_and_blocked_by_default(self) -> None:
        output = self.build / "template" / "session.json"

        create_template(output, self.root)

        document = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(set(OBSERVATIONS), set(document["observations"]))
        self.assertEqual(set(SCREENSHOT_ROLES), set(document["evidence"]))
        self.assertEqual(set(LOG_ROLES), set(document["log_excerpts"]))
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
            "private-user",
            "203.0.113.8",
            "ghp_abcdefghijklmnopqrstuvwxyz",
        ):
            self.assertNotIn(private, combined)
        self.assertIn("127.0.0.1", combined)
        self.assertIn("[REDACTED_TEST_PLAYER]", archived_text)
        self.assertFalse(any("player_names" in key for key in record))
        self.assertIn("not set or approve any release Gate", record["scope_statement"])

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

    def test_input_outside_build_is_rejected(self) -> None:
        session = self.ready_session()
        outside = self.root / "outside.png"
        outside.write_bytes(make_png())
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

    def test_png_crc_and_privacy_metadata_are_rejected(self) -> None:
        valid = self.build / "valid.png"
        valid.write_bytes(make_png())
        corrupted = self.build / "corrupted.png"
        damaged = bytearray(make_png())
        damaged[-1] ^= 1
        corrupted.write_bytes(damaged)
        metadata = self.build / "metadata.png"
        metadata.write_bytes(make_png(metadata=(b"tEXt", b"Author\0private")))

        self.assertEqual(640, inspect_png(valid)["width"])
        with self.assertRaisesRegex(ValueError, "CRC mismatch"):
            inspect_png(corrupted)
        with self.assertRaisesRegex(ValueError, "privacy-bearing PNG metadata"):
            inspect_png(metadata)

    def test_unknown_png_ancillary_chunk_and_chunk_count_are_rejected(self) -> None:
        hidden = self.build / "hidden.png"
        hidden.write_bytes(
            make_png(metadata=(b"raNd", b"ghp_abcdefghijklmnopqrstuvwxyz"))
        )
        valid = self.build / "valid-count.png"
        valid.write_bytes(make_png())

        with self.assertRaisesRegex(ValueError, "unknown or nonessential"):
            inspect_png(hidden)
        with patch("scripts.collect_v002_manual_evidence.MAX_PNG_CHUNKS", 2):
            with self.assertRaisesRegex(ValueError, "chunks"):
                inspect_png(valid)

    def test_png_dimensions_are_bounded(self) -> None:
        tiny = self.build / "tiny.png"
        tiny.write_bytes(make_png(320, 200))
        huge = self.build / "huge.png"
        huge.write_bytes(make_png(4097, 4096))

        with self.assertRaisesRegex(ValueError, "at least"):
            inspect_png(tiny)
        with self.assertRaisesRegex(ValueError, "pixel count"):
            inspect_png(huge)

    def test_log_excerpt_line_count_is_bounded(self) -> None:
        log = self.build / "long.log"
        log.write_text("line\n" * 201, encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "exceeds 200 lines"):
            extract_log_excerpt(log, 1, 201, [])

    def test_tampered_archived_log_is_rejected(self) -> None:
        errors, _, output = self.collect(self.ready_session())
        self.assertEqual([], errors)
        target = output / "logs" / "client_startup_world.txt"
        target.write_text("tampered 198.51.100.5\n", encoding="utf-8")

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any("log excerpt metadata mismatch" in error for error in validation_errors),
            validation_errors,
        )

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


if __name__ == "__main__":
    unittest.main()
