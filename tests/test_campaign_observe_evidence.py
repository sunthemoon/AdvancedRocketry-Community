from collections import namedtuple
import contextlib
import io
from pathlib import Path
import tempfile
import unittest

from scripts.campaign_observe import evidence
from scripts import run_campaign_observer as cli

IDENTITY = {"git_commit": "a" * 40, "jar_sha256": "b" * 64, "forge": "47.4.10", "java": "17"}
Disk = namedtuple("Disk", "total used free")


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()

    def create(self, **kwargs):
        return evidence.evidence_root(self.root, "aaaaaaa", IDENTITY,
                                      disk_usage=lambda p: Disk(0, 0, evidence.MIN_FREE_BYTES), **kwargs)

    def test_create_refuse_and_read_only_check(self):
        target = self.create()
        self.assertEqual(set(evidence.FOLDERS), {p.name for p in target.iterdir()})
        before = {str(p): p.stat().st_mtime_ns for p in target.rglob("*")}
        self.assertEqual(target, self.create(check=True))
        self.assertEqual(before, {str(p): p.stat().st_mtime_ns for p in target.rglob("*")})
        with self.assertRaises(FileExistsError):
            self.create()
        (target / "phase5-ref").rmdir()
        with self.assertRaises(ValueError):
            self.create(check=True)

    def test_check_refuses_changed_identity_and_missing_root_without_writes(self):
        with self.assertRaises(ValueError):
            self.create(check=True)
        self.assertEqual([], list(self.root.iterdir()))
        target = self.create()
        (target / "candidate/IDENTITY.json").write_text("{}")
        with self.assertRaises(ValueError):
            self.create(check=True)

    def test_low_disk_including_each_named_drive_before_writes(self):
        extra = self.root / "extra"
        extra.mkdir()
        calls = []
        def disk(path):
            calls.append(path)
            return Disk(0, 0, 1 if path == extra else evidence.MIN_FREE_BYTES)
        with self.assertRaisesRegex(ValueError, "10 GiB"):
            evidence.evidence_root(self.root, "aaaaaaa", IDENTITY, drives=[extra], disk_usage=disk)
        self.assertEqual([self.root, extra], calls)
        self.assertFalse((self.root / "aaaaaaa").exists())

    def test_identity_validation(self):
        for short in ("../bad", "a" * 41, "bbb1234", "AA12345"):
            with self.assertRaises(ValueError):
                evidence.identity_values(IDENTITY, short)
        with self.assertRaises(ValueError):
            evidence.identity_values({**IDENTITY, "jar_sha256": "x"}, "aaaaaaa")

    def test_checksums_inventory_corruption_missing_extra_and_seal_refusal(self):
        (self.root / "a.txt").write_bytes(b"one")
        nested = self.root / "nested"
        nested.mkdir()
        (nested / "space file").write_bytes(b"two")
        self.assertEqual(2, evidence.write_checksums(self.root))
        self.assertEqual(2, evidence.verify_checksums(self.root))
        with self.assertRaises(FileExistsError):
            evidence.write_checksums(self.root)
        (self.root / "a.txt").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "mismatch"):
            evidence.verify_checksums(self.root)
        (self.root / "a.txt").unlink()
        with self.assertRaisesRegex(ValueError, "inventory"):
            evidence.verify_checksums(self.root)
        (self.root / "a.txt").write_bytes(b"one")
        (self.root / "extra.txt").write_bytes(b"extra")
        with self.assertRaisesRegex(ValueError, "inventory"):
            evidence.verify_checksums(self.root)

    def test_checksum_manifest_traversal_duplicate_and_malformed(self):
        (self.root / "a").write_bytes(b"a")
        digest = evidence.digest_file(self.root / "a")
        manifest = self.root / evidence.MANIFEST
        for text in (f"{digest}  ../outside\n", f"{digest}  a\n{digest}  a\n", "bad\n"):
            manifest.write_text(text)
            with self.assertRaises(ValueError):
                evidence.verify_checksums(self.root)

    def test_linked_tree_refused(self):
        link = self.root / "link"
        try:
            link.symlink_to(self.root / "missing")
        except OSError:
            self.skipTest("Symlink privilege unavailable")
        with self.assertRaises(ValueError):
            evidence.write_checksums(self.root)

    def test_cli_seal_verify_and_error(self):
        (self.root / "one").write_bytes(b"test")
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(0, cli.main(["seal", str(self.root)]))
            self.assertEqual(0, cli.main(["verify", str(self.root)]))
            self.assertEqual(1, cli.main(["seal", str(self.root)]))
