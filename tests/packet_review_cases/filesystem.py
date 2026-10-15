"""FilesystemCases for the packet review facade."""
import os
import shutil
import unittest
import scripts.prepare_v002_g0_review_packet as packet_module
from scripts.prepare_v002_g0_review_packet import (
    PacketError,
    generate_packet,
    resolve_commit,
    verify_packet,
)


class FilesystemCases:
    def test_paths_reject_all_windows_device_names_and_component_bounds(self) -> None:
        for value in (
            "CONIN$/value",
            "CONOUT$.txt",
            "COM¹/value",
            "LPT³.txt",
            ".Git/config",
            "a" * 256,
        ):
            with self.subTest(value=value):
                self.assertIsNotNone(packet_module._relative_path_error(value))

        repository_path = "/".join("a" for _ in range(32))
        self.assertIsNone(packet_module._relative_path_error(repository_path))
        with self.assertRaisesRegex(PacketError, "components"):
            packet_module._safe_relative_path(
                f"files/{repository_path}", "derived packet path"
            )

    @unittest.skipUnless(os.name == "nt", "Windows junction coverage")
    def test_verify_rejects_windows_junction(self) -> None:
        target = self.build / "junction-target"
        target.mkdir()
        junction = self.packet / "unsafe-junction"
        result = self.run_command(
            ["cmd", "/c", "mklink", "/J", str(junction), str(target)],
            check=False,
        )
        if result.returncode != 0:
            self.skipTest("junction creation is unavailable")
        try:
            errors = self.verify_fast()
            self.assertTrue(
                any("junction" in error or "reparse point" in error for error in errors),
                errors,
            )
            content_errors = self.verify_content_only()
            self.assertTrue(
                any(
                    "junction" in error or "reparse point" in error
                    for error in content_errors
                ),
                content_errors,
            )
        finally:
            os.rmdir(junction)

    def test_head_commit_and_output_safety_rules(self) -> None:
        with self.assertRaisesRegex(PacketError, "lowercase full 40-character"):
            resolve_commit(self.root, self.commit[:12])
        with self.assertRaisesRegex(PacketError, "Git-ignored path"):
            generate_packet(self.root, self.commit, self.root / "not-ignored")
        with self.assertRaisesRegex(PacketError, "must not already exist"):
            generate_packet(self.root, self.commit, self.packet)

        clean_output = self.build / "clean-head"
        generate_packet(self.root, "HEAD", clean_output)
        self.assertEqual([], verify_packet(self.root, "HEAD", clean_output))
        (self.root / "untracked.txt").write_text("dirty\n", encoding="utf-8")
        with self.assertRaisesRegex(PacketError, "HEAD may be used only"):
            generate_packet(self.root, "HEAD", self.build / "dirty-head")

        (self.root / "untracked.txt").unlink()
        many_untracked = self.root / "many-untracked"
        many_untracked.mkdir()
        for index in range(200):
            (many_untracked / f"entry-{index:03d}.txt").write_bytes(b"")
        with self.assertRaisesRegex(PacketError, "HEAD may be used only"):
            resolve_commit(self.root, "HEAD")

    def test_generation_rejects_missing_tracked_output_without_writing(self) -> None:
        tracked_output = self.build / "tracked-packet"
        tracked_output.write_text("tracked file\n", encoding="utf-8")
        self.git("add", "-f", "--", "build/tracked-packet")
        self.git("commit", "--quiet", "-m", "track ignored packet output")
        tracked_output.unlink()
        status_before = self.git("status", "--short")

        with self.assertRaisesRegex(PacketError, "tracked index path"):
            generate_packet(
                self.root,
                self.commit,
                tracked_output,
            )

        self.assertFalse(tracked_output.exists())
        self.assertEqual(status_before, self.git("status", "--short"))

    def test_tracked_output_query_is_literal_and_large_result_bounded(self) -> None:
        wildcard_match = self.build / "metaa" / "tracked.txt"
        wildcard_match.parent.mkdir()
        wildcard_match.write_text("tracked wildcard neighbor\n", encoding="utf-8")

        literal_output = self.build / "meta[ab]"
        tracked_descendants = [
            literal_output / f"directory-{index:03d}" / "tracked.txt"
            for index in range(256)
        ]
        for path in tracked_descendants:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("tracked descendant\n", encoding="utf-8")
        self.git(
            "add",
            "-f",
            "--",
            "build/metaa",
            ":(top,literal)build/meta[ab]",
        )
        self.git("commit", "--quiet", "-m", "track bounded literal output fixtures")
        shutil.rmtree(literal_output)

        self.assertFalse(
            packet_module._index_contains_path_or_descendant(
                self.root, "build/meta[ac]"
            )
        )
        self.assertTrue(
            packet_module._index_contains_path_or_descendant(
                self.root, "build/meta[ab]"
            )
        )
        with self.assertRaisesRegex(PacketError, "tracked index path"):
            packet_module._safe_ignored_directory(
                self.root,
                literal_output,
                "output directory",
                require_exists=False,
            )
        self.assertFalse(literal_output.exists())
