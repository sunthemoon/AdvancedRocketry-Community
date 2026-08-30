import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from scripts.run_dedicated_server_smoke import (
    ARTIFACT_NAME,
    FORGE_COORDINATE,
    SmokeError,
    server_configuration_payload,
)
from scripts.run_v002_mismatch_server_cycle import run_mismatch_cycle


class MismatchServerCycleTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.build = self.root / "build"
        self.server = self.build / "v0.0.2-manual" / "server"
        self.server.mkdir(parents=True)
        artifact = self.server / "mods" / ARTIFACT_NAME
        artifact.parent.mkdir()
        artifact.write_bytes(b"artifact")
        self.artifact_hash = hashlib.sha256(b"artifact").hexdigest()
        self.cycle_hashes = {}
        cycles = []
        now = datetime.now(timezone.utc)
        for name in ("first-start", "restart"):
            payload = f"{name} distinct harness log\n".encode()
            path = self.server / f"{name}-full.txt"
            path.write_bytes(payload)
            digest = hashlib.sha256(payload).hexdigest()
            self.cycle_hashes[name] = digest
            cycles.append(
                {
                    "name": name,
                    "exit_code": 0,
                    "full_log_file": path.name,
                    "full_log_sha256": digest,
                }
            )
        self.summary = {
            "schema_version": 4,
            "session_id": "v002-" + "a" * 24,
            "manual_player_cycles": True,
            "same_player_verified": True,
            "offline_mode": True,
            "completed_at": (now - timedelta(minutes=5)).isoformat(),
            "server_port": 25565,
            "server_artifact_sha256": self.artifact_hash,
            "cycles": cycles,
        }
        self.summary_path = self.build / "server-player-evidence" / "summary.json"
        self.summary_path.parent.mkdir(parents=True)
        self.summary_path.write_text(
            json.dumps(self.summary, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        (self.server / "server.properties").write_bytes(
            server_configuration_payload(25565, True)
        )
        runtime_log = self.server / "logs" / "latest.log"
        runtime_log.parent.mkdir()
        runtime_log.write_bytes(b"previous runtime log\n")
        args_file = (
            self.server
            / "libraries"
            / "net"
            / "minecraftforge"
            / "forge"
            / FORGE_COORDINATE
            / "win_args.txt"
        )
        args_file.parent.mkdir(parents=True)
        args_file.write_text("", encoding="utf-8")
        self.log_output = self.build / "v0.0.2-manual" / "mismatch-server-full.txt"
        self.receipt_output = (
            self.build / "v0.0.2-manual" / "mismatch-server-receipt.json"
        )
        self.patchers = [
            patch(
                "scripts.run_v002_mismatch_server_cycle.ROOT", self.root
            ),
            patch(
                "scripts.run_v002_mismatch_server_cycle.BUILD_ROOT", self.build
            ),
            patch(
                "scripts.run_v002_mismatch_server_cycle.platform.system",
                return_value="Windows",
            ),
            patch(
                "scripts.run_v002_mismatch_server_cycle.resolve_java",
                return_value=("java", "17.0.16"),
            ),
        ]
        for patcher in self.patchers:
            patcher.start()
            self.addCleanup(patcher.stop)

    def run_with_log(self, payload: bytes, exit_code: int = 0):
        def run_process(*args, **kwargs):
            (self.server / "logs" / "latest.log").write_bytes(payload)
            return subprocess.CompletedProcess(args[0], exit_code)

        with patch(
            "scripts.run_v002_mismatch_server_cycle.subprocess.run",
            side_effect=run_process,
        ):
            return run_mismatch_cycle(
                server=self.server,
                summary_path=self.summary_path,
                java="java",
                log_output=self.log_output,
                receipt_output=self.receipt_output,
            )

    def test_fresh_cycle_creates_bound_receipt_and_log_once(self) -> None:
        payload = b"fresh third-cycle runtime log\n"

        receipt = self.run_with_log(payload)

        self.assertEqual(payload, self.log_output.read_bytes())
        self.assertEqual(2, receipt["schema_version"])
        self.assertRegex(receipt["run_id"], r"^v002-mismatch-[0-9a-f]{24}$")
        self.assertEqual(self.summary["session_id"], receipt["session_id"])
        self.assertEqual("17.0.16", receipt["java_version"])
        self.assertEqual(self.cycle_hashes, receipt["harness_cycle_log_sha256"])
        self.assertEqual(
            [{"filename": ARTIFACT_NAME, "sha256": self.artifact_hash}],
            receipt["server_mods_files"],
        )
        self.assertEqual(
            hashlib.sha256(payload).hexdigest(), receipt["full_log_sha256"]
        )
        self.assertEqual(
            receipt,
            json.loads(self.receipt_output.read_text(encoding="utf-8")),
        )

    def test_help_runs_with_isolated_python(self) -> None:
        script = (
            Path(__file__).resolve().parents[1]
            / "scripts"
            / "run_v002_mismatch_server_cycle.py"
        )

        completed = subprocess.run(
            [sys.executable, "-I", "-S", str(script), "--help"],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="strict",
        )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("--server-dir", completed.stdout)

    def test_existing_output_blocks_process_launch(self) -> None:
        self.log_output.write_text("existing\n", encoding="utf-8")

        with patch(
            "scripts.run_v002_mismatch_server_cycle.subprocess.run"
        ) as process:
            with self.assertRaisesRegex(SmokeError, "refusing to overwrite"):
                run_mismatch_cycle(
                    server=self.server,
                    summary_path=self.summary_path,
                    java="java",
                    log_output=self.log_output,
                    receipt_output=self.receipt_output,
                )

        process.assert_not_called()

    def test_output_outside_build_blocks_process_launch(self) -> None:
        with patch(
            "scripts.run_v002_mismatch_server_cycle.subprocess.run"
        ) as process:
            with self.assertRaisesRegex(SmokeError, "must remain below"):
                run_mismatch_cycle(
                    server=self.server,
                    summary_path=self.summary_path,
                    java="java",
                    log_output=self.root / "outside.log",
                    receipt_output=self.receipt_output,
                )

        process.assert_not_called()

    def test_cycle_log_hash_reuse_is_rejected(self) -> None:
        payload = (self.server / "restart-full.txt").read_bytes()

        with self.assertRaisesRegex(SmokeError, "reuses a harness-cycle"):
            self.run_with_log(payload)

        self.assertFalse(self.log_output.exists())
        self.assertFalse(self.receipt_output.exists())

    def test_active_security_property_tamper_blocks_launch(self) -> None:
        path = self.server / "server.properties"
        path.write_bytes(
            path.read_bytes().replace(b"online-mode=false", b"online-mode=true")
        )

        with patch(
            "scripts.run_v002_mismatch_server_cycle.subprocess.run"
        ) as process:
            with self.assertRaisesRegex(SmokeError, "online-mode"):
                run_mismatch_cycle(
                    server=self.server,
                    summary_path=self.summary_path,
                    java="java",
                    log_output=self.log_output,
                    receipt_output=self.receipt_output,
                )

        process.assert_not_called()

    def test_extra_server_mod_blocks_process_launch(self) -> None:
        (self.server / "mods" / "extra-server-mod.jar").write_bytes(b"extra")

        with patch(
            "scripts.run_v002_mismatch_server_cycle.subprocess.run"
        ) as process:
            with self.assertRaisesRegex(SmokeError, "only the project JAR"):
                run_mismatch_cycle(
                    server=self.server,
                    summary_path=self.summary_path,
                    java="java",
                    log_output=self.log_output,
                    receipt_output=self.receipt_output,
                )

        process.assert_not_called()

    def test_server_mod_added_during_cycle_blocks_receipt(self) -> None:
        def run_process(*args, **kwargs):
            (self.server / "logs" / "latest.log").write_bytes(
                b"fresh third-cycle runtime log\n"
            )
            (self.server / "mods" / "late-extra.jar").write_bytes(b"extra")
            return subprocess.CompletedProcess(args[0], 0)

        with patch(
            "scripts.run_v002_mismatch_server_cycle.subprocess.run",
            side_effect=run_process,
        ):
            with self.assertRaisesRegex(SmokeError, "only the project JAR"):
                run_mismatch_cycle(
                    server=self.server,
                    summary_path=self.summary_path,
                    java="java",
                    log_output=self.log_output,
                    receipt_output=self.receipt_output,
                )

        self.assertFalse(self.log_output.exists())
        self.assertFalse(self.receipt_output.exists())

    def test_nonzero_process_exit_is_attested_before_failure(self) -> None:
        with self.assertRaisesRegex(SmokeError, "exited with code 9"):
            self.run_with_log(b"fresh failed third cycle\n", exit_code=9)

        receipt = json.loads(self.receipt_output.read_text(encoding="utf-8"))
        self.assertEqual(9, receipt["exit_code"])
        self.assertTrue(self.log_output.is_file())


if __name__ == "__main__":
    unittest.main()
