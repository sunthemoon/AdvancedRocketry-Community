import copy
import hashlib
import hmac
import json
import struct
import subprocess
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

from scripts.collect_v002_manual_evidence import (
    APPLICABILITY,
    COMMITTED_BUNDLE,
    CONTENT_MANIFEST,
    LOG_ROLES,
    OBSERVATIONS,
    RECORD_NAME,
    SCREENSHOT_ROLES,
    build_template,
    bind_player_identity,
    collect_evidence,
    create_template,
    extract_log_excerpt,
    inspect_png,
    parse_player_lifecycle,
    validate_bundle,
    validate_session,
)
from scripts.run_dedicated_server_smoke import (
    SERVER_PROPERTIES_IDENTITY_FILE,
    server_configuration_payload as smoke_server_configuration_payload,
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
        (self.root / ".gitattributes").write_bytes(b"* text eol=lf\n")
        (self.root / "README.md").write_bytes(
            b"# Test repository\n\nBound manual evidence fixture.\n"
        )
        (self.root / ".gitignore").write_bytes(b"/build/\n")
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
            newline="\n",
        )
        subprocess.run(["git", "init", "-q"], cwd=self.root, check=True)
        subprocess.run(
            [
                "git",
                "add",
                ".gitattributes",
                ".gitignore",
                "README.md",
                CONTENT_MANIFEST.as_posix(),
            ],
            cwd=self.root,
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-c",
                "user.name=Evidence Fixture",
                "-c",
                "user.email=evidence@example.invalid",
                "commit",
                "-q",
                "-m",
                "test fixture",
            ],
            cwd=self.root,
            check=True,
        )
        self.source_commit = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=self.root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        self.jar_paths: dict[str, Path] = {}
        for role in ("source", "server", "client"):
            path = self.build / role / "mods" / self.artifact_name
            path.parent.mkdir(parents=True)
            path.write_bytes(self.artifact_content)
            self.jar_paths[role] = path

    def ready_session(self) -> dict[str, object]:
        session = build_template(self.artifact_name)
        session["metadata"] = {
            "source_commit": self.source_commit,
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
            "SecretPlayer initialized from C:\\Users\\private user\\instance "
            "with UUID 123e4567-e89b-42d3-a456-426614174000\n"
            "remote 203.0.113.8 Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz\n"
            "matching connection to 127.0.0.1\n"
            "matching client entered the world\n"
            "missing-mod indicator observed\n"
            "[Render thread/INFO] [minecraft/ConnectScreen]: "
            "Connecting to 127.0.0.1, 25565\n"
            "missing-mod connection result recorded\n",
            encoding="utf-8",
        )
        server_root = self.jar_paths["server"].parent.parent
        first_log = server_root / "first-start-full.txt"
        first_log.write_text(
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer left the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Saved the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server\n",
            encoding="utf-8",
        )
        restart_log = server_root / "restart-full.txt"
        restart_log.write_text(
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Done (1.00s)! For help, type help\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer left the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Saved the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server\n",
            encoding="utf-8",
        )
        mismatch_payload = (
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Starting Minecraft server on 127.0.0.1:25565\n"
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Preparing level \"world\"\n"
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Done (1.00s)! For help, type help\n"
            "[Netty Server IO #1/INFO] "
            "[net.minecraftforge.server.ServerLifecycleHooks/SERVERHOOKS]: "
            "Disconnecting VANILLA connection attempt from isolated client\n"
            "Missing-mod connection attempt was rejected\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Saved the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server\n"
        )
        runtime_log = server_root / "logs" / "latest.log"
        runtime_log.parent.mkdir(parents=True)
        runtime_log.write_text(mismatch_payload, encoding="utf-8")
        mismatch_log = self.build / "mismatch-server-full.txt"
        mismatch_log.write_text(mismatch_payload, encoding="utf-8")
        mismatch_receipt = self.build / "mismatch-server-receipt.json"
        mismatch_receipt.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "exit_code": 0,
                    "full_log_sha256": hashlib.sha256(
                        mismatch_log.read_bytes()
                    ).hexdigest(),
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
            newline="\n",
        )
        server_properties_payload = smoke_server_configuration_payload(25565, True)
        (server_root / "server.properties").write_bytes(server_properties_payload)
        (server_root / SERVER_PROPERTIES_IDENTITY_FILE).write_bytes(
            server_properties_payload
        )
        server_properties_sha256 = hashlib.sha256(
            server_properties_payload
        ).hexdigest()
        log_inputs = {
            "client_startup_world": (client_log, 1, 2),
            "matching_client_connection": (client_log, 3, 4),
            "server_first_join_leave_save_stop": (first_log, 1, 4),
            "server_restart_reconnect_save_stop": (restart_log, 1, 5),
            "mismatch_attempt": (client_log, 5, 7),
            "mismatch_server_attempt_save_stop": (mismatch_log, 1, 7),
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
                    "warning_disposition": {
                        "status": "NONE",
                        "warning_count": 0,
                        "origins": [],
                        "explanation": "",
                    },
                }
            )
            if role == "mismatch_server_attempt_save_stop":
                item["server_exit_code"] = 0
                item["receipt"] = mismatch_receipt.relative_to(
                    self.root
                ).as_posix()

        session_id = "v002-" + "a" * 24
        player_binding = bind_player_identity(b"\x17" * 32, "SecretPlayer")
        cycle_base = {
            "error_count": 0,
            "warning_count": 0,
            "project_error_count": 0,
            "project_warning_count": 0,
            "client_linkage_failure_count": 0,
            "exit_code": 0,
            "mod_marker": "1.20.1-0.0.2-dev",
            "player_join_observed": True,
            "player_identity_binding": player_binding,
            "player_leave_observed": True,
            "status_protocol": 763,
            "status_version": "1.20.1",
        }
        world_before_sha256 = "0" * 64
        world_identity = hashlib.sha256(
            (
                f"{session_id}\0{self.artifact_hash}\0{world_before_sha256}\0"
                f"{server_properties_sha256}"
            ).encode("utf-8")
        ).hexdigest()
        summary = {
            "schema_version": 3,
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
            "same_player_verified": True,
            "started_at": "2026-08-27T12:00:00+00:00",
            "world": {
                "identity": world_identity,
                "identity_marker": "world/.v002-smoke-world-identity.json",
                "identity_marker_sha256": "",
                "level_dat_after_restart_sha256": "f" * 64,
                "level_dat_after_restart_size": 2048,
                "level_dat_before_restart_sha256": world_before_sha256,
                "level_dat_before_restart_size": 1024,
                "level_name": "world",
                "same_world_verified": True,
                "server_properties_sha256": server_properties_sha256,
            },
            "world_level_dat": True,
        }
        world_marker = server_root / "world" / ".v002-smoke-world-identity.json"
        world_marker.parent.mkdir()
        world_marker.write_text(
            json.dumps(
                {
                    "artifact_sha256": self.artifact_hash,
                    "server_properties_sha256": server_properties_sha256,
                    "session_id": session_id,
                    "world_identity": summary["world"]["identity"],
                },
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        summary["world"]["identity_marker_sha256"] = hashlib.sha256(
            world_marker.read_bytes()
        ).hexdigest()
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

    def set_warning_disposition(
        self,
        session: dict[str, object],
        roles: tuple[str, ...],
        *,
        count: int,
        status: str = "UNRESOLVED",
    ) -> None:
        for role in roles:
            session["log_excerpts"][role]["warning_disposition"] = {
                "status": status,
                "warning_count": count,
                "origins": ["Test logger"],
                "explanation": "Preserved warning disposition for the rejection-path test.",
            }

    def refresh_mismatch_receipt(
        self,
        session: dict[str, object],
        *,
        exit_code: int | None = None,
    ) -> None:
        role = "mismatch_server_attempt_save_stop"
        item = session["log_excerpts"][role]
        if exit_code is not None:
            item["server_exit_code"] = exit_code
        source = self.root / item["source"]
        receipt = self.root / item["receipt"]
        receipt.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "exit_code": item["server_exit_code"],
                    "full_log_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )

    def replace_mismatch_server_log(
        self,
        session: dict[str, object],
        payload: str,
    ) -> None:
        role = "mismatch_server_attempt_save_stop"
        item = session["log_excerpts"][role]
        source = self.root / item["source"]
        source.write_text(payload, encoding="utf-8")
        runtime = self.jar_paths["server"].parent.parent / "logs" / "latest.log"
        runtime.write_text(payload, encoding="utf-8")
        item["line_start"] = 1
        item["line_end"] = len(payload.splitlines())
        self.refresh_mismatch_receipt(session)

    def replace_client_connection_marker(
        self,
        session: dict[str, object],
        replacement: str,
    ) -> None:
        role = "mismatch_attempt"
        item = session["log_excerpts"][role]
        source = self.root / item["source"]
        lines = source.read_text(encoding="utf-8").splitlines()
        lines[5] = replacement
        source.write_text("\n".join(lines) + "\n", encoding="utf-8")

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
                "mismatch_attempt",
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
            "mismatch_attempt",
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

    def test_dirty_worktree_is_rejected_before_collection(self) -> None:
        session = self.ready_session()
        (self.root / ".gitignore").write_bytes(b"/build/\n/local-only/\n")

        errors, _, output = self.collect(session)

        self.assertTrue(any("clean tracked/untracked worktree" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_source_commit_must_equal_existing_checkout_head(self) -> None:
        session = self.ready_session()
        session["metadata"]["source_commit"] = "a" * 40

        errors, _, output = self.collect(session)

        self.assertTrue(any("rev-parse" in error or "source commit" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_committed_bundle_validates_original_source_commit_after_head_moves(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "source-revision-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        (self.root / "README.md").write_bytes(b"# Later documentation commit\n")
        manifest_path = self.root / CONTENT_MANIFEST
        manifest_path.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "artifact": "future-artifact.jar",
                    "artifact_sha256": "1" * 64,
                    "entry_count": 0,
                    "entries": [],
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
            newline="\n",
        )
        subprocess.run(
            ["git", "add", "README.md", CONTENT_MANIFEST.as_posix()],
            cwd=self.root,
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-c",
                "user.name=Evidence Fixture",
                "-c",
                "user.email=evidence@example.invalid",
                "commit",
                "-q",
                "-m",
                "later docs",
            ],
            cwd=self.root,
            check=True,
        )

        validation_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )

        self.assertEqual([], validation_errors)

    def test_player_identity_binding_uses_private_hmac_protocol(self) -> None:
        secret = b"private-fixture-secret-material!" + b"x" * 8
        expected = hmac.new(
            secret,
            b"v0.0.2-player-identity\0secretplayer",
            hashlib.sha256,
        ).hexdigest()

        actual = bind_player_identity(secret, "SecretPlayer")

        self.assertEqual(expected, actual)
        public_session_digest = hashlib.sha256(
            b"v002-aaaaaaaaaaaaaaaaaaaaaaaa\0secretplayer"
        ).hexdigest()
        self.assertNotEqual(public_session_digest, actual)
        with self.assertRaisesRegex(ValueError, "at least 32 bytes"):
            bind_player_identity(b"short", "SecretPlayer")

    def test_collector_treats_private_player_binding_as_opaque(self) -> None:
        session = self.ready_session()
        summary_path = self.root / session["server_harness"]["summary"]
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        for cycle in summary["cycles"]:
            cycle["player_identity_binding"] = "9" * 64
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        errors, record, _ = self.collect(session, "opaque-binding")

        self.assertEqual([], errors)
        assert record is not None
        bindings = {
            record["log_excerpts"][role]["player_identity_binding"]
            for role in (
                "server_first_join_leave_save_stop",
                "server_restart_reconnect_save_stop",
            )
        }
        self.assertEqual({"9" * 64}, bindings)

    def test_windows_home_with_spaces_is_fully_redacted(self) -> None:
        source = self.build / "logs" / "home-space.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "loaded C:\\Users\\Private Test User\\isolated-instance\\options.txt\n",
            encoding="utf-8",
        )

        payload, counts = extract_log_excerpt(source, 1, 1, [])
        text = payload.decode("utf-8")

        self.assertEqual(1, counts["home"])
        self.assertIn("[REDACTED_HOME]", text)
        self.assertNotIn("Private Test User", text)
        self.assertNotIn("C:\\Users", text)

    def test_mismatch_server_must_use_the_harness_world(self) -> None:
        session = self.ready_session()
        server_root = self.jar_paths["server"].parent.parent
        (server_root / SERVER_PROPERTIES_IDENTITY_FILE).write_text(
            "server-ip=127.0.0.1\n"
            "server-port=25565\n"
            "level-name=other-world\n",
            encoding="utf-8",
        )

        errors, _, output = self.collect(session, "wrong-mismatch-world")

        self.assertTrue(
            any("harness-owned ASCII canonical" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_mismatch_server_rejects_noncanonical_java_properties_syntax(self) -> None:
        session = self.ready_session()
        server_root = self.jar_paths["server"].parent.parent
        properties = server_root / SERVER_PROPERTIES_IDENTITY_FILE
        properties.write_text(
            properties.read_text(encoding="utf-8") + "level-name:other-world\n",
            encoding="utf-8",
        )

        errors, _, output = self.collect(session, "alternate-properties-separator")

        self.assertTrue(
            any("harness-owned ASCII canonical" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_java_rewritten_runtime_properties_do_not_break_startup_binding(self) -> None:
        session = self.ready_session()
        server_root = self.jar_paths["server"].parent.parent
        (server_root / "server.properties").write_text(
            "#Minecraft server properties\n"
            "#Fri Aug 29 12:00:00 CST 2026\n"
            "level-name=world\n"
            "level-type=minecraft\\:normal\n"
            "server-ip=127.0.0.1\n"
            "server-port=25565\n",
            encoding="iso-8859-1",
        )

        errors, record, _ = self.collect(session, "java-rewritten-properties")

        self.assertEqual([], errors)
        self.assertIsNotNone(record)

    def test_mismatch_world_load_requires_exact_dedicated_server_logger(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = source.read_text(encoding="utf-8").replace(
            "[minecraft/DedicatedServer]: Preparing level \"world\"",
            "[evil/FakeDedicatedServer]: Preparing level \"world\"",
        )
        self.replace_mismatch_server_log(session, payload)
        output = self.build / "fake-world-load-logger"

        errors, record = collect_evidence(
            self.write_session(session, "fake-world-load-logger-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(
            any("Preparing level" in error or "world-load" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_strict_mismatch_accepts_server_connection_marker_without_client_marker(self) -> None:
        session = self.ready_session()
        self.replace_client_connection_marker(
            session, "missing-mod client result without a connection log marker"
        )
        output = self.build / "server-connection-marker-only"

        errors, record = collect_evidence(
            self.write_session(session, "server-connection-marker-only-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("READY_FOR_HUMAN_GATE_REVIEW", record["review_readiness"]["status"])
        self.assertEqual(
            "server",
            record["log_excerpts"]["mismatch_server_attempt_save_stop"][
                "connection_attempt_marker"
            ]["source"],
        )

    def test_strict_mismatch_accepts_bound_client_marker_without_server_marker(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        output = self.build / "client-connection-marker-only"

        errors, record = collect_evidence(
            self.write_session(session, "client-connection-marker-only-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("READY_FOR_HUMAN_GATE_REVIEW", record["review_readiness"]["status"])
        self.assertEqual(
            "client",
            record["log_excerpts"]["mismatch_attempt"][
                "connection_attempt_marker"
            ]["source"],
        )

    def test_strict_mismatch_rejects_client_marker_for_other_port(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session,
            "[Render thread/INFO] [minecraft/ConnectScreen]: "
            "Connecting to 127.0.0.1, 25566",
        )
        output = self.build / "wrong-client-connection-port"

        errors, record = collect_evidence(
            self.write_session(session, "wrong-client-connection-port-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("connection-attempt marker" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_strict_mismatch_rejects_forged_client_logger(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session,
            "[Render thread/INFO] [evil/ConnectScreen]: "
            "Connecting to 127.0.0.1, 25565",
        )
        output = self.build / "forged-client-connection-logger"

        errors, record = collect_evidence(
            self.write_session(session, "forged-client-connection-logger-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("connection-attempt marker" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_strict_mismatch_rejects_when_both_connection_markers_are_absent(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session, "missing-mod result without any connection marker"
        )
        output = self.build / "no-connection-markers"

        errors, record = collect_evidence(
            self.write_session(session, "no-connection-markers-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("connection-attempt marker" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_strict_mismatch_requires_logger_anchored_connection_attempt(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        item = session["log_excerpts"][role]
        source = self.root / item["source"]
        payload = source.read_text(encoding="utf-8").replace(
            "[Netty Server IO #1/INFO] "
            "[net.minecraftforge.server.ServerLifecycleHooks/SERVERHOOKS]: "
            "Disconnecting VANILLA connection attempt from isolated client",
            "[Server thread/INFO] [minecraft/Chat]: injected "
            "[Netty Server IO #1/INFO] "
            "[net.minecraftforge.server.ServerLifecycleHooks/SERVERHOOKS]: "
            "Disconnecting VANILLA connection attempt from isolated client",
        )
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session, "missing-mod client result without a connection log marker"
        )
        output = self.build / "missing-connection-marker"

        errors, record = collect_evidence(
            self.write_session(session, "missing-connection-marker-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(
            any("connection-attempt marker" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_embedded_minecraft_logger_text_cannot_forge_player_lifecycle(self) -> None:
        session = self.ready_session()
        role = "server_first_join_leave_save_stop"
        first_log = self.root / session["log_excerpts"][role]["source"]
        first_log.write_text(
            "[Server thread/INFO] [minecraft/Chat]: injected "
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/Chat]: injected "
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer left the game\n"
            "Saved the game\n"
            "Stopping server\n",
            encoding="utf-8",
        )
        self.refresh_summary_log_hash(session, "first-start", first_log)

        errors, _, output = self.collect(session, "embedded-player-spoof")

        self.assertTrue(
            any("exactly one player join and one player leave" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_similar_minecraft_logger_name_cannot_forge_player_lifecycle(self) -> None:
        payload = (
            "[Server thread/INFO] [evil/FakeMinecraftServerChat]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [evil/FakeMinecraftServerChat]: "
            "SecretPlayer left the game\n"
        )

        with self.assertRaisesRegex(ValueError, "exactly one player join"):
            parse_player_lifecycle(payload, "fake-logger")

    def test_committed_bundle_rejects_conflicting_shared_source_audits(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "shared-audit-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        record["log_excerpts"]["mismatch_attempt"]["source_audit"][
            "audit_counts"
        ]["error_count"] = 1
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
            any("raw log audits for shared source" in error for error in validation_errors),
            validation_errors,
        )

    def test_strict_collect_rejects_colon_or_ads_source_without_output(self) -> None:
        session = self.ready_session()
        carrier = self.build / "capture" / "unsafe-source"
        carrier.write_bytes(b"carrier")
        unsafe = Path(str(carrier) + ":capture")
        unsafe.write_bytes(make_png(seed=91))
        session["evidence"]["mods_page"]["source"] = unsafe.relative_to(
            self.root
        ).as_posix()
        output = self.build / "strict-unsafe-source"

        errors, record = collect_evidence(
            self.write_session(session, "strict-unsafe-source-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("not portable" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_staged_validation_uses_build_mode_and_rejects_changed_raw_input(self) -> None:
        session = self.ready_session()
        output = self.build / "staged-validation-failure"
        client_log = self.build / "logs" / "client-full.log"
        changed = False

        def mutate_then_validate(*args, **kwargs):
            nonlocal changed
            if not changed:
                changed = True
                client_log.write_text(
                    client_log.read_text(encoding="utf-8")
                    + "[Render thread/ERROR] [minecraft/Test]: changed during collect\n",
                    encoding="utf-8",
                )
            return validate_bundle(*args, **kwargs)

        with patch(
            "scripts.collect_v002_manual_evidence.validate_bundle",
            side_effect=mutate_then_validate,
        ) as validator:
            errors, record = collect_evidence(
                self.write_session(session, "staged-validation-session.json"),
                output,
                self.root,
                require_acceptance_ready=True,
            )

        self.assertIsNone(record)
        self.assertTrue(any("raw log source no longer matches" in error for error in errors), errors)
        self.assertFalse(output.exists())
        staging = self.build / ".v002-evidence-staging"
        self.assertFalse(staging.exists())
        self.assertEqual("build", validator.call_args.kwargs["_validation_mode"])

    def test_staged_committed_validation_rechecks_raw_inputs_before_publish(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        client_log = self.build / "logs" / "client-full.log"
        modes: list[str] = []
        changed = False

        def mutate_after_committed_check(*args, **kwargs):
            nonlocal changed
            mode = kwargs["_validation_mode"]
            modes.append(mode)
            result = validate_bundle(*args, **kwargs)
            if mode == "committed" and not changed:
                changed = True
                client_log.write_text(
                    client_log.read_text(encoding="utf-8")
                    + "[Render thread/ERROR] [minecraft/Test]: changed during collect\n",
                    encoding="utf-8",
                )
            return result

        with patch(
            "scripts.collect_v002_manual_evidence.validate_bundle",
            side_effect=mutate_after_committed_check,
        ):
            errors, record = collect_evidence(
                self.write_session(session, "staged-committed-session.json"),
                output,
                self.root,
                require_acceptance_ready=True,
            )

        self.assertIsNone(record)
        self.assertTrue(any("raw log source no longer matches" in error for error in errors), errors)
        self.assertEqual(["committed", "build"], modes)
        self.assertFalse(output.exists())
        self.assertFalse((self.build / ".v002-evidence-staging").exists())

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


if __name__ == "__main__":
    unittest.main()
