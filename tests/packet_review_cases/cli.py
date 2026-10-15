"""CliCases for the packet review facade."""
import io
import sys
from unittest.mock import patch
import scripts.prepare_v002_g0_review_packet as packet_module
from scripts.prepare_v002_g0_review_packet import GENERATOR_PATH, main


class CliCases:
    def test_cli_generate_verify_and_failure_exit_bind_one_printed_sha(self) -> None:
        script = self.root / GENERATOR_PATH
        output = self.build / "cli-packet"
        nonisolated = self.run_command(
            [sys.executable, str(script), "--help"], check=False
        )
        self.assertEqual(2, nonisolated.returncode)
        self.assertIn("requires Python isolated mode", nonisolated.stderr)

        generate_result = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "--repository-root",
                str(self.root),
                "generate",
                "--commit",
                self.commit,
                "--output",
                str(output),
            ],
            check=False,
        )
        self.assertEqual(0, generate_result.returncode, generate_result.stderr)
        self.assertEqual(1, generate_result.stdout.count(self.commit))

        verify_result = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "--repository-root",
                str(self.root),
                "verify",
                "--commit",
                self.commit,
                "--packet",
                str(output),
            ],
            check=False,
        )
        self.assertEqual(0, verify_result.returncode, verify_result.stderr)
        self.assertEqual(1, verify_result.stdout.count(self.commit))

        invalid_result = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "--repository-root",
                str(self.root),
                "verify",
                "--commit",
                self.commit[:12],
                "--packet",
                str(output),
            ],
            check=False,
        )
        self.assertEqual(1, invalid_result.returncode)
        self.assertIn("lowercase full 40-character", invalid_result.stderr)

    def test_nonisolated_cli_rejects_local_stdlib_shadow_before_import(self) -> None:
        script = self.root / GENERATOR_PATH
        marker = self.build / "shadow-imported.txt"
        shadow = self.root / "scripts/json.py"
        shadow.write_text(
            f"open({str(marker)!r}, 'w', encoding='utf-8').write('executed')\n",
            encoding="utf-8",
            newline="\n",
        )

        result = self.run_command(
            [sys.executable, str(script), "--help"], check=False
        )

        self.assertEqual(2, result.returncode)
        self.assertIn("requires Python isolated mode", result.stderr)
        self.assertFalse(marker.exists())

    def test_main_resolves_commit_once(self) -> None:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            patch.object(
                packet_module,
                "_run_selected_commit_validation",
                return_value=dict(self.base_validation),
            ),
            patch.object(
                packet_module,
                "resolve_commit",
                wraps=packet_module.resolve_commit,
            ) as resolver,
            patch("sys.stdout", stdout),
            patch("sys.stderr", stderr),
        ):
            result = main(
                [
                    "--repository-root",
                    str(self.root),
                    "verify",
                    "--commit",
                    self.commit,
                    "--packet",
                    str(self.packet),
                ]
            )
        self.assertEqual(0, result, stderr.getvalue())
        self.assertEqual(1, resolver.call_count)
        self.assertEqual(1, stdout.getvalue().count(self.commit))
