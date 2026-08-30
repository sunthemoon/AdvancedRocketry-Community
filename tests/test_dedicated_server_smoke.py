import hashlib
import io
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.run_dedicated_server_smoke import (
    CapturedProcess,
    MOD_ID,
    MOD_VERSION,
    MINECRAFT_PROTOCOL,
    MINECRAFT_VERSION,
    SERVER_PROPERTIES_IDENTITY_FILE,
    SmokeError,
    bind_player_identity,
    build_session_id,
    complete_world_identity,
    create_session,
    decode_optimized_forge_data,
    encode_varint,
    establish_world_identity,
    evidence_lines,
    log_audit_counts,
    extract_java_version,
    forge_mod_versions,
    install_server,
    matching_player_name,
    parse_java_properties,
    read_varint,
    scan_log,
    server_configuration_payload,
    summary_log_audit_counts,
    summary_schema_version,
    validate_status_identity,
    verify_active_server_properties,
    write_server_configuration,
)


class PlayerIdentityTests(unittest.TestCase):
    @staticmethod
    def lifecycle_line(player: str, action: str) -> str:
        return (
            "[28Aug2026 12:00:00.000] [Server thread/INFO] "
            f"[net.minecraft.server.MinecraftServer/]: {player} {action} the game"
        )

    def test_join_leave_identity_is_parsed_case_insensitively(self) -> None:
        self.assertEqual(
            "TestPlayer",
            matching_player_name(
                self.lifecycle_line("TestPlayer", "joined"),
                self.lifecycle_line("testplayer", "left"),
            ),
        )

    def test_different_join_leave_identity_is_rejected(self) -> None:
        with self.assertRaisesRegex(SmokeError, "identities differ"):
            matching_player_name(
                self.lifecycle_line("FirstPlayer", "joined"),
                self.lifecycle_line("OtherPlayer", "left"),
            )

    def test_chat_text_cannot_spoof_a_player_lifecycle_marker(self) -> None:
        with self.assertRaisesRegex(SmokeError, "Could not parse"):
            matching_player_name(
                self.lifecycle_line("TestPlayer", "joined"),
                "[Server thread/INFO] [minecraft/MinecraftServer]: "
                "<TestPlayer> TestPlayer left the game",
            )

    def test_similar_logger_name_cannot_spoof_a_player_lifecycle_marker(self) -> None:
        with self.assertRaisesRegex(SmokeError, "Could not parse"):
            matching_player_name(
                "[Server thread/INFO] [evil/FakeMinecraftServerChat]: "
                "TestPlayer joined the game",
                "[Server thread/INFO] [evil/FakeMinecraftServerChat]: "
                "TestPlayer left the game",
            )

    def test_unscoped_lifecycle_text_is_rejected(self) -> None:
        with self.assertRaisesRegex(SmokeError, "Could not parse"):
            matching_player_name(
                "TestPlayer joined the game",
                "TestPlayer left the game",
            )

    def test_identity_binding_uses_secret_key_and_is_case_stable(self) -> None:
        first = bind_player_identity(b"a" * 32, "TestPlayer")
        self.assertEqual(
            first,
            bind_player_identity(b"a" * 32, "testplayer"),
        )
        self.assertNotEqual(
            first,
            bind_player_identity(b"b" * 32, "TestPlayer"),
        )

    def test_identity_binding_rejects_public_or_short_salts(self) -> None:
        with self.assertRaisesRegex(SmokeError, "32-byte secret"):
            bind_player_identity(b"short", "TestPlayer")
        with self.assertRaisesRegex(SmokeError, "32-byte secret"):
            bind_player_identity("v002-public-session", "TestPlayer")  # type: ignore[arg-type]

    def test_headless_schema_two_and_manual_schema_four_are_distinct(self) -> None:
        self.assertEqual(2, summary_schema_version(False))
        self.assertEqual(4, summary_schema_version(True))


class VarIntTests(unittest.TestCase):
    def test_round_trip_protocol_values(self) -> None:
        for value in (0, 1, 127, 128, 255, 763, 2_147_483_647):
            self.assertEqual(value, read_varint(io.BytesIO(encode_varint(value))))

    def test_negative_value_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            encode_varint(-1)


class StatusParsingTests(unittest.TestCase):
    @staticmethod
    def encode_optimized(payload: bytes) -> str:
        buffer = 0
        bits = 0
        encoded = [chr(len(payload) & 0x7FFF), chr(len(payload) >> 15)]
        for byte in payload:
            buffer |= byte << bits
            bits += 8
            if bits >= 15:
                encoded.append(chr(buffer & 0x7FFF))
                buffer >>= 15
                bits -= 15
        if bits:
            encoded.append(chr(buffer & 0x7FFF))
        return "".join(encoded)

    @staticmethod
    def utf(value: str) -> bytes:
        raw = value.encode("utf-8")
        return encode_varint(len(raw)) + raw

    def test_forge_mod_markers_are_indexed(self) -> None:
        status = {
            "forgeData": {
                "mods": [
                    {"modId": "minecraft", "modmarker": "1.20.1"},
                    {"modId": MOD_ID, "modmarker": "1.20.1-0.0.2-dev"},
                ]
            }
        }

        self.assertEqual(
            "1.20.1-0.0.2-dev",
            forge_mod_versions(status)[MOD_ID],
        )

    def test_absent_forge_data_is_empty(self) -> None:
        self.assertEqual({}, forge_mod_versions({}))

    def test_optimized_forge_mod_data_is_decoded(self) -> None:
        payload = b"".join(
            (
                b"\x00",  # not truncated
                b"\x00\x01",  # one mod
                b"\x00",  # zero channels and explicit version
                self.utf(MOD_ID),
                self.utf("1.20.1-0.0.2-dev"),
                b"\x00",  # zero non-mod channels
            )
        )
        encoded = self.encode_optimized(payload)

        self.assertEqual(
            {MOD_ID: "1.20.1-0.0.2-dev"},
            decode_optimized_forge_data(encoded),
        )
        self.assertEqual(
            {MOD_ID: "1.20.1-0.0.2-dev"},
            forge_mod_versions({"forgeData": {"mods": [], "d": encoded}}),
        )

    @staticmethod
    def valid_status() -> dict:
        return {
            "version": {
                "name": MINECRAFT_VERSION,
                "protocol": MINECRAFT_PROTOCOL,
            },
            "forgeData": {
                "mods": [
                    {"modId": MOD_ID, "modmarker": MOD_VERSION},
                ]
            },
        }

    def test_expected_status_identity_is_accepted(self) -> None:
        validate_status_identity(self.valid_status())

    def test_wrong_minecraft_version_is_rejected(self) -> None:
        status = self.valid_status()
        status["version"]["name"] = "1.20.2"

        with self.assertRaisesRegex(SmokeError, "Minecraft version"):
            validate_status_identity(status)

    def test_wrong_protocol_is_rejected(self) -> None:
        status = self.valid_status()
        status["version"]["protocol"] = MINECRAFT_PROTOCOL + 1

        with self.assertRaisesRegex(SmokeError, "protocol"):
            validate_status_identity(status)

    def test_wrong_mod_marker_is_rejected(self) -> None:
        status = self.valid_status()
        status["forgeData"]["mods"][0]["modmarker"] = "wrong"

        with self.assertRaisesRegex(SmokeError, "mod marker"):
            validate_status_identity(status)


class LogAuditTests(unittest.TestCase):
    def test_clean_lifecycle_log_passes(self) -> None:
        lines = [
            '[Server thread/INFO] Done (1.000s)! For help, type "help"\n',
            "[Server thread/INFO] Saved the game\n",
        ]

        self.assertEqual([], scan_log(lines))

    def test_error_and_client_linkage_are_reported(self) -> None:
        lines = [
            "[Server thread/ERROR] [advancedrocketrycommunity/]: broken\n",
            "java.lang.NoClassDefFoundError: net/minecraft/client/Minecraft\n",
        ]

        self.assertEqual(2, len(scan_log(lines)))

    def test_project_warning_is_blocking_but_third_party_warning_is_not(self) -> None:
        project = "[Server thread/WARN] [advancedrocketrycommunity/]: risky\n"
        third_party = "[Server thread/WARN] [forge/]: unrelated\n"

        self.assertEqual([project.rstrip()], scan_log([project]))
        self.assertEqual([], scan_log([third_party]))

    def test_fatal_is_blocking_and_not_counted_as_error(self) -> None:
        fatal = "[Server thread/FATAL] [advancedrocketrycommunity/]: broken\n"

        self.assertEqual([fatal.rstrip()], scan_log([fatal]))
        counts = log_audit_counts([fatal])
        self.assertEqual(0, counts["error_count"])
        self.assertEqual(1, counts["fatal_count"])
        self.assertEqual(1, counts["project_fatal_count"])

    def test_fatal_fields_extend_only_the_manual_summary_schema(self) -> None:
        fatal = ["[Server thread/FATAL] [advancedrocketrycommunity/]: broken\n"]

        headless = summary_log_audit_counts(fatal, manual_player_cycles=False)
        manual = summary_log_audit_counts(fatal, manual_player_cycles=True)

        self.assertNotIn("fatal_count", headless)
        self.assertNotIn("project_fatal_count", headless)
        self.assertEqual(1, manual["fatal_count"])
        self.assertEqual(1, manual["project_fatal_count"])

    def test_log_audit_counts_separate_project_and_broad_findings(self) -> None:
        lines = [
            "[Server thread/ERROR] [advancedrocketrycommunity/]: project error\n",
            "[Server thread/WARN] [forge/]: third-party warning\n",
            "[Server thread/WARN] [AdvancedRocketryCommunity/]: project warning\n",
            "[main/WARN] [ForgeConfigSpec/CORE]: config/advancedrocketrycommunity-common.toml\n",
            "NoClassDefFoundError: net.minecraft.client.Minecraft\n",
        ]

        self.assertEqual(
            {
                "error_count": 1,
                "warning_count": 3,
                "fatal_count": 0,
                "project_error_count": 1,
                "project_warning_count": 1,
                "project_fatal_count": 0,
                "client_linkage_failure_count": 1,
            },
            log_audit_counts(lines),
        )

    def test_evidence_filter_keeps_lifecycle_markers(self) -> None:
        lines = [
            "noise\n",
            "[Server thread/INFO] [minecraft/MinecraftServer]: Dev joined the game\n",
            "[Server thread/INFO] Saved the game\n",
        ]

        self.assertEqual(
            [
                "[Server thread/INFO] [minecraft/MinecraftServer]: Dev joined the game",
                "[Server thread/INFO] Saved the game",
            ],
            evidence_lines(lines),
        )

    def test_evidence_filter_drops_chat_lifecycle_text(self) -> None:
        lines = [
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "<Dev> Dev left the game\n",
        ]

        self.assertEqual(
            ["No evidence markers were selected from the captured log."],
            evidence_lines(lines),
        )


class CapturedProcessSafetyTests(unittest.TestCase):
    def test_existing_log_is_rejected_before_starting_process(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            work = Path(temporary_directory)
            log = work / "server-full.txt"
            log.write_text("existing evidence\n", encoding="utf-8")

            with patch(
                "scripts.run_dedicated_server_smoke.subprocess.Popen"
            ) as popen:
                with self.assertRaises(FileExistsError):
                    CapturedProcess(["java"], work, log)

            popen.assert_not_called()

    def test_log_reservation_is_closed_if_process_start_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            work = Path(temporary_directory)
            log = work / "server-full.txt"

            with patch(
                "scripts.run_dedicated_server_smoke.subprocess.Popen",
                side_effect=OSError("cannot start"),
            ):
                with self.assertRaisesRegex(OSError, "cannot start"):
                    CapturedProcess(["java"], work, log)

            log.unlink()


class JavaVersionTests(unittest.TestCase):
    def test_java_17_is_accepted(self) -> None:
        self.assertEqual(
            "17.0.12",
            extract_java_version('openjdk version "17.0.12" 2024-07-16'),
        )

    def test_other_java_major_is_rejected(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "Java 17 is required"):
            extract_java_version('openjdk version "21.0.4" 2024-07-16')


class ServerConfigurationTests(unittest.TestCase):
    def test_manual_server_properties_are_ascii_sorted_and_canonical(self) -> None:
        payload = server_configuration_payload(25565, True)

        self.assertEqual(payload, payload.decode("ascii").encode("ascii"))
        lines = payload.decode("ascii").splitlines()
        self.assertEqual(lines, sorted(lines))
        self.assertIn("level-name=world", lines)
        self.assertIn("server-ip=127.0.0.1", lines)
        self.assertIn("server-port=25565", lines)
        self.assertIn("online-mode=false", lines)

    def test_configuration_reserves_an_immutable_startup_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            server = Path(temporary_directory)

            sha256 = write_server_configuration(server, 25565, True)

            payload = server_configuration_payload(25565, True)
            self.assertEqual(payload, (server / "server.properties").read_bytes())
            self.assertEqual(
                payload,
                (server / SERVER_PROPERTIES_IDENTITY_FILE).read_bytes(),
            )
            self.assertEqual(sha256, hashlib.sha256(payload).hexdigest())
            with self.assertRaises(FileExistsError):
                write_server_configuration(server, 25565, True)

    def test_java_rewritten_active_properties_are_verified_semantically(self) -> None:
        payload = (
            b"#written by java\n"
            b"online-mode=false\n"
            b"level-type=minecraft\\:normal\n"
            + b"\n".join(
                line
                for line in server_configuration_payload(25565, True).splitlines()
                if not line.startswith((b"online-mode=", b"level-type="))
            )
            + b"\n"
        )

        critical = verify_active_server_properties(payload, 25565)

        self.assertEqual("minecraft:normal", critical["level-type"])
        self.assertEqual("false", critical["online-mode"])

    def test_active_security_property_tamper_is_rejected(self) -> None:
        payload = server_configuration_payload(25565, True).replace(
            b"online-mode=false", b"online-mode=true"
        )

        with self.assertRaisesRegex(SmokeError, "online-mode"):
            verify_active_server_properties(payload, 25565)

    def test_duplicate_active_property_is_rejected(self) -> None:
        payload = server_configuration_payload(25565, True) + b"server-port=25565\n"

        with self.assertRaisesRegex(SmokeError, "duplicate key"):
            parse_java_properties(payload)

    def test_non_java_line_separator_cannot_smuggle_a_critical_property(self) -> None:
        payload = (
            b"# Java treats NEL as comment content\x85online-mode=false\n"
            + b"\n".join(
                line
                for line in server_configuration_payload(25565, True).splitlines()
                if not line.startswith(b"online-mode=")
            )
            + b"\n"
        )

        with self.assertRaisesRegex(SmokeError, "online-mode"):
            verify_active_server_properties(payload, 25565)


class InstallerRetryTests(unittest.TestCase):
    def test_timeout_is_logged_and_retried_with_same_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            server = Path(temporary_directory)
            installer = server / "installer.jar"
            calls = 0

            def run_installer(*args, **kwargs):
                nonlocal calls
                calls += 1
                if calls == 1:
                    raise subprocess.TimeoutExpired(
                        cmd=args[0], timeout=1, output=b"partial output\n"
                    )
                args_file = (
                    server
                    / "libraries"
                    / "net"
                    / "minecraftforge"
                    / "forge"
                    / "1.20.1-47.4.10"
                    / "win_args.txt"
                )
                args_file.parent.mkdir(parents=True)
                args_file.write_text("", encoding="utf-8")
                return subprocess.CompletedProcess(args[0], 0, "complete\n", "")

            with patch(
                "scripts.run_dedicated_server_smoke.platform.system",
                return_value="Windows",
            ), patch(
                "scripts.run_dedicated_server_smoke.subprocess.run",
                side_effect=run_installer,
            ):
                args_file, attempts = install_server(
                    "java", installer, server, timeout=1, max_attempts=2
                )

            self.assertEqual(2, attempts)
            self.assertTrue(args_file.is_file())
            timeout_log = (server / "installer-attempt-1-full.txt").read_text(
                encoding="utf-8"
            )
            self.assertIn("partial output", timeout_log)
            self.assertIn("[TIMEOUT]", timeout_log)


class SessionSafetyTests(unittest.TestCase):
    def test_partial_installer_directory_requires_explicit_resume(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            work_root = Path(temporary_directory)
            session = work_root / "partial"
            (session / "libraries").mkdir(parents=True)

            with self.assertRaisesRegex(SmokeError, "Refusing to reuse"):
                create_session(work_root, session)

            self.assertEqual(
                session.resolve(),
                create_session(work_root, session, resume_install_session=True),
            )

    def test_resume_rejects_server_runtime_state(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            work_root = Path(temporary_directory)
            session = work_root / "started"
            (session / "world").mkdir(parents=True)

            with self.assertRaisesRegex(SmokeError, "server runtime state"):
                create_session(work_root, session, resume_install_session=True)


class WorldIdentityTests(unittest.TestCase):
    def test_world_identity_survives_level_dat_changes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            server = Path(temporary_directory)
            level_dat = server / "world" / "level.dat"
            level_dat.parent.mkdir()
            level_dat.write_bytes(b"first-save")
            session_id = build_session_id("a" * 64, "2026-08-28T00:00:00+00:00", 25565)

            properties_sha256 = write_server_configuration(server, 25565, True)
            identity = establish_world_identity(
                server, session_id, "a" * 64, properties_sha256
            )
            level_dat.write_bytes(b"restart-save")
            completed = complete_world_identity(server, identity)

            self.assertTrue(completed["same_world_verified"])
            self.assertEqual("world", completed["level_name"])
            self.assertNotEqual(
                completed["level_dat_before_restart_sha256"],
                completed["level_dat_after_restart_sha256"],
            )

    def test_changed_world_identity_marker_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            server = Path(temporary_directory)
            level_dat = server / "world" / "level.dat"
            level_dat.parent.mkdir()
            level_dat.write_bytes(b"first-save")
            properties_sha256 = write_server_configuration(server, 25565, True)
            identity = establish_world_identity(
                server, "v002-session", "b" * 64, properties_sha256
            )
            (server / str(identity["identity_marker"])).write_text(
                "tampered\n", encoding="utf-8"
            )

            with self.assertRaisesRegex(SmokeError, "changed"):
                complete_world_identity(server, identity)


if __name__ == "__main__":
    unittest.main()
