"""GitObjectCases for the packet review facade."""
import zlib
from pathlib import Path
from unittest.mock import patch
import scripts.prepare_v002_g0_review_packet as packet_module
from scripts.prepare_v002_g0_review_packet import PacketError


class GitObjectCases:
    def test_selected_tree_bounds_and_symlink_are_rejected(self) -> None:
        for field, value, message in (
            ("MAX_SELECTED_TREE_FILES", 1, "exceeds 1 files"),
            ("MAX_SELECTED_TREE_DIRECTORIES", 0, "exceeds 0 directories"),
            ("MAX_SELECTED_TREE_FILE_BYTES", 1, "selected commit blob exceeds 1"),
            ("MAX_SELECTED_TREE_BYTES", 1, "exceeds 1 total bytes"),
        ):
            with (
                self.subTest(field=field),
                patch.object(packet_module, field, value),
                self.assertRaisesRegex(PacketError, message),
            ):
                packet_module._validate_selected_tree_bounds(self.root, self.commit)

        link_blob = self.git("hash-object", "-w", "README.md")
        self.git(
            "update-index",
            "--add",
            "--cacheinfo",
            f"120000,{link_blob},tracked-selected-link",
        )
        self.git("commit", "--quiet", "-m", "add tracked selected symlink")
        selected = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(PacketError, "symlink, submodule, or non-regular"):
            packet_module._validate_selected_tree_bounds(self.root, selected)

        self.git("reset", "--hard", self.commit)
        self.git("config", "core.ignorecase", "false")
        readme_blob = self.git("hash-object", "-w", "README.md")
        self.git(
            "update-index",
            "--add",
            "--cacheinfo",
            f"100644,{readme_blob},readme.md",
        )
        self.git("commit", "--quiet", "-m", "add portable path collision")
        collision_commit = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(PacketError, "path collision"):
            packet_module._validate_selected_tree_bounds(
                self.root, collision_commit
            )

        duplicate_blob = self.git("hash-object", "-w", "README.md")
        duplicate_tree_content = (
            b"100644 README.md\0"
            + bytes.fromhex(duplicate_blob)
            + b"100644 README.md\0"
            + bytes.fromhex(duplicate_blob)
        )
        duplicate_tree = self.git_with_input(
            duplicate_tree_content,
            "hash-object",
            "--literally",
            "-t",
            "tree",
            "-w",
            "--stdin",
        )
        duplicate_commit = self.git_with_input(
            b"duplicate exact tree path\n",
            "commit-tree",
            duplicate_tree,
            "-p",
            self.commit,
        )
        with self.assertRaisesRegex(PacketError, "duplicate exact path"):
            packet_module._validate_selected_tree_bounds(
                self.root, duplicate_commit
            )
        with self.assertRaisesRegex(PacketError, "exactly one Git entry"):
            packet_module._git_blob(self.root, duplicate_commit, "README.md")

        empty_tree = self.git_with_input(b"", "mktree")
        empty_directories = (
            b"040000 empty-one\0"
            + bytes.fromhex(empty_tree)
            + b"040000 empty-two\0"
            + bytes.fromhex(empty_tree)
        )
        empty_root = self.git_with_input(
            empty_directories,
            "hash-object",
            "--literally",
            "-t",
            "tree",
            "-w",
            "--stdin",
        )
        empty_commit = self.git_with_input(
            b"bounded empty trees\n",
            "commit-tree",
            empty_root,
            "-p",
            self.commit,
        )
        with (
            patch.object(packet_module, "MAX_SELECTED_TREE_DIRECTORIES", 1),
            self.assertRaisesRegex(PacketError, "exceeds 1 directories"),
        ):
            packet_module._validate_selected_tree_bounds(self.root, empty_commit)

    def test_git_object_reads_recompute_oid_and_bound_undeclared_bytes(self) -> None:
        oid = self.git("rev-parse", f"{self.commit}:README.md")
        objects = Path(self.git("rev-parse", "--git-path", "objects"))
        if not objects.is_absolute():
            objects = self.root / objects
        loose = objects / oid[:2] / oid[2:]
        loose.parent.mkdir(parents=True, exist_ok=True)
        if loose.exists():
            loose.chmod(0o600)

        loose.write_bytes(zlib.compress(b"blob 4\0BBBB"))
        with self.assertRaisesRegex(PacketError, "Git object identity mismatch"):
            packet_module._read_verified_git_object(
                self.root, oid, "blob", 1024, "corrupt blob"
            )

        loose.write_bytes(zlib.compress(b"blob 1\0" + b"A" * (1024 * 1024)))
        with self.assertRaisesRegex(PacketError, "undeclared bytes"):
            packet_module._read_verified_git_object(
                self.root, oid, "blob", 1024, "size-forged blob"
            )
