"""OfflineCases for the packet review facade."""
import shutil
import sys
from pathlib import Path
from unittest.mock import patch
import scripts.prepare_v002_g0_review_packet as packet_module
from scripts.prepare_v002_g0_review_packet import (
    GENERATOR_PATH,
    REVIEW_INSTRUCTIONS_NAME,
    verify_packet_content_only,
)


class OfflineCases:
    def test_content_only_verifier_is_offline_and_explicitly_weaker(self) -> None:
        offline = Path(self.root.parent) / "offline-packet"
        shutil.copytree(self.packet, offline)
        with (
            patch.object(
                packet_module,
                "_run_git",
                side_effect=AssertionError("offline verification must not invoke Git"),
            ),
            patch.object(
                packet_module,
                "_authoritative_expectation",
                side_effect=AssertionError(
                    "offline verification must not rebuild Git expectations"
                ),
            ),
        ):
            self.assertEqual([], verify_packet_content_only(offline))

        document = self.load_manifest(offline)
        document["source_commit"] = "f" * 40
        self.write_manifest(offline, document)
        self.assertEqual([], verify_packet_content_only(offline))

        # The packet cannot authenticate a verifier bundled inside itself. Run
        # content-only validation with the independently trusted source checkout.
        script = self.source_root / GENERATOR_PATH
        result = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "verify-content-only",
                "--packet",
                str(offline),
            ],
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("CONTENT-ONLY CHECK", result.stdout)
        self.assertIn("were not verified", result.stdout)
        self.assertIn("separately authenticated verifier", result.stdout)
        self.assertIn("never execute code from an unauthenticated packet", result.stdout)
        self.assertIn("private, quiescent packet copy", result.stdout)

        marker = self.root.parent / "bundled-verifier-executed.txt"
        bundled_script = offline / "files" / GENERATOR_PATH
        bundled_script.write_text(
            f"open({str(marker)!r}, 'w', encoding='utf-8').write('executed')\n",
            encoding="utf-8",
            newline="\n",
        )
        rejected = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "verify-content-only",
                "--packet",
                str(offline),
            ],
            check=False,
        )
        self.assertEqual(1, rejected.returncode)
        self.assertFalse(marker.exists())

    def test_content_only_rejects_inventory_hash_and_manifest_attacks(self) -> None:
        instruction = self.packet / REVIEW_INSTRUCTIONS_NAME
        original = instruction.read_bytes()
        instruction.write_bytes(b"X" + original[1:])
        errors = self.verify_content_only()
        self.assertTrue(
            any(REVIEW_INSTRUCTIONS_NAME in error and "SHA-256" in error for error in errors),
            errors,
        )

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        missing_path = self.packet / "files" / "README.md"
        missing_path.unlink()
        (self.packet / "unexpected.txt").write_text("extra\n", encoding="utf-8")
        (self.packet / "unexpected-directory").mkdir()
        errors = self.verify_content_only()
        self.assertTrue(any("missing files" in error for error in errors), errors)
        self.assertTrue(any("unexpected files" in error for error in errors), errors)
        self.assertTrue(any("unexpected directories" in error for error in errors), errors)

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        document = self.load_manifest(self.packet)
        document["files"][1]["packet_path"] = document["files"][0]["packet_path"]
        self.write_manifest(self.packet, document)
        errors = self.verify_content_only()
        self.assertTrue(any("duplicate packet_path" in error for error in errors), errors)

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        document = self.load_manifest(self.packet)
        document["files"][0]["packet_path"] = "../escape.txt"
        self.write_manifest(self.packet, document)
        errors = self.verify_content_only()
        self.assertTrue(
            any("packet_path is unsafe" in error for error in errors), errors
        )

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        document = self.load_manifest(self.packet)
        document["reviewer_instructions"]["packet_path"] = document["files"][0][
            "packet_path"
        ]
        self.write_manifest(self.packet, document)
        errors = self.verify_content_only()
        self.assertTrue(
            any("reviewer instructions path" in error for error in errors), errors
        )
        self.assertTrue(any("duplicate packet_path" in error for error in errors), errors)

    def test_content_only_resolves_an_explicit_root_below_linked_ancestors(self) -> None:
        real_parent = self.root.parent / "offline-real-parent"
        real_parent.mkdir()
        offline = real_parent / "packet"
        shutil.copytree(self.packet, offline)
        linked_parent = self.root.parent / "offline-linked-parent"
        try:
            linked_parent.symlink_to(real_parent, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"directory symlink creation is unavailable: {exc}")

        selected = linked_parent / "packet"
        self.assertEqual(
            offline.resolve(),
            packet_module._safe_offline_packet_directory(selected),
        )
        self.assertEqual([], verify_packet_content_only(selected))

    def test_content_only_rejects_links_bounds_and_mid_verification_mutation(self) -> None:
        target = self.packet / "files" / "README.md"
        target.unlink()
        try:
            target.symlink_to(self.root / "README.md")
        except OSError:
            pass
        else:
            errors = self.verify_content_only()
            self.assertTrue(
                any(
                    "symlink" in error or "reparse point" in error
                    for error in errors
                ),
                errors,
            )

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        expected_files = sum(1 for path in self.packet.rglob("*") if path.is_file())
        with patch.object(packet_module, "MAX_PACKET_FILES", expected_files - 2):
            errors = self.verify_content_only()
        self.assertTrue(any("exceeds" in error and "files" in error for error in errors), errors)

        maximum_size = max(
            path.stat().st_size for path in self.packet.rglob("*") if path.is_file()
        )
        with patch.object(packet_module, "MAX_FILE_BYTES", maximum_size - 1):
            errors = self.verify_content_only()
        self.assertTrue(any("file exceeds" in error for error in errors), errors)

        original_reader = packet_module._read_bounded_regular_file
        instruction = self.packet / REVIEW_INSTRUCTIONS_NAME
        calls = 0

        def mutate_after_first_read(path: Path, *args: object, **kwargs: object) -> bytes:
            nonlocal calls
            content = original_reader(path, *args, **kwargs)
            if path == instruction:
                calls += 1
                if calls == 1:
                    mutated = bytes((content[0] ^ 1,)) + content[1:]
                    instruction.write_bytes(mutated)
            return content

        with patch.object(
            packet_module,
            "_read_bounded_regular_file",
            side_effect=mutate_after_first_read,
        ):
            errors = self.verify_content_only()
        self.assertTrue(
            any(
                "changed during verification" in error
                or "SHA-256 differs during final pass" in error
                for error in errors
            ),
            errors,
        )

    def test_packet_read_detects_parent_directory_replacement(self) -> None:
        original_files = self.packet / "files"
        replacement_files = self.build / "replacement-files"
        saved_files = self.build / "files-before-swap"
        replacement_files.mkdir()
        content = (original_files / "README.md").read_bytes()
        (replacement_files / "README.md").write_bytes(content)
        original_snapshot = packet_module._packet_directory_component_snapshot
        swapped = False

        def swap_after_snapshot(
            packet_root: Path, packet_path: str, label: str
        ) -> tuple[tuple[str, tuple[int, int, int, int, int, int]], ...]:
            nonlocal swapped
            snapshot = original_snapshot(packet_root, packet_path, label)
            if not swapped and packet_path == "files/README.md":
                original_files.rename(saved_files)
                replacement_files.rename(original_files)
                swapped = True
            return snapshot

        with patch.object(
            packet_module,
            "_packet_directory_component_snapshot",
            side_effect=swap_after_snapshot,
        ):
            errors = self.verify_content_only()
        self.assertTrue(
            any(
                "parent directory components changed while reading" in error
                for error in errors
            ),
            errors,
        )
