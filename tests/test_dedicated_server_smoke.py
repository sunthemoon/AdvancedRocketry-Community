import io
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.run_dedicated_server_smoke import (
    MOD_ID,
    MOD_VERSION,
    MINECRAFT_PROTOCOL,
    MINECRAFT_VERSION,
    SmokeError,
    create_session,
    decode_optimized_forge_data,
    encode_varint,
    evidence_lines,
    extract_java_version,
    forge_mod_versions,
    install_server,
    read_varint,
    scan_log,
    validate_status_identity,
)


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

    def test_evidence_filter_keeps_lifecycle_markers(self) -> None:
        lines = [
            "noise\n",
            "[Server thread/INFO] Dev joined the game\n",
            "[Server thread/INFO] Saved the game\n",
        ]

        self.assertEqual(
            [
                "[Server thread/INFO] Dev joined the game",
                "[Server thread/INFO] Saved the game",
            ],
            evidence_lines(lines),
        )


class JavaVersionTests(unittest.TestCase):
    def test_java_17_is_accepted(self) -> None:
        self.assertEqual(
            "17.0.12",
            extract_java_version('openjdk version "17.0.12" 2024-07-16'),
        )

    def test_other_java_major_is_rejected(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "Java 17 is required"):
            extract_java_version('openjdk version "21.0.4" 2024-07-16')


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


if __name__ == "__main__":
    unittest.main()
