"""R141 retention must reproduce evidence, not rewrite sealed packets."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/w164_evidence_retention.py"
spec = importlib.util.spec_from_file_location("w164_retention", SCRIPT)
retention = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retention)


class EvidenceRetentionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "evidence"
        self.root.mkdir()
        self.scratch = Path(self.temp.name) / "scratch"
        self.scratch.mkdir()
        self.name = "verification-9336b0fd4"
        packet = self.root / self.name
        (packet / "empty").mkdir(parents=True)
        (packet / "run").mkdir()
        data = b"original\r\n\x00\xff" * 200
        (packet / "run/log.bin").write_bytes(data)
        digest = hashlib.sha256(data).hexdigest()
        (packet / "SHA256SUMS.txt").write_bytes(
            f"{digest}  run/log.bin\r\n".encode("ascii"))
        self.before = retention.inventory(packet)

    def test_pack_rebuilds_all_bytes_and_empty_directories(self):
        manifest = retention.pack(self.root, self.name, self.scratch)
        self.assertFalse((self.root / self.name).exists())
        restored = Path(self.temp.name) / "restored"
        result = retention.verify(self.root, manifest, restored)
        self.assertEqual(2, result["verified_files"])
        self.assertEqual(1, result["sealed_checksums"])
        self.assertEqual(self.before, retention.inventory(restored / self.name))
        self.assertEqual(self.name, json.loads(manifest.read_text())["original_directory"])

    def test_bad_sealed_hash_preserves_original_directory(self):
        (self.root / self.name / "run/log.bin").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Sealed checksum"):
            retention.pack(self.root, self.name, self.scratch)
        self.assertTrue((self.root / self.name / "SHA256SUMS.txt").exists())

    def test_corrupt_archive_refuses_restore(self):
        manifest = retention.pack(self.root, self.name, self.scratch)
        archive = self.root / json.loads(manifest.read_text())["archive"]
        archive.write_bytes(archive.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "Archive hash"):
            retention.verify(self.root, manifest, Path(self.temp.name) / "restored")

    def test_unassigned_directory_is_not_packaged(self):
        with self.assertRaisesRegex(ValueError, "authorized"):
            retention.pack(self.root, "r118-native-pre", self.scratch)
        with self.assertRaisesRegex(ValueError, "authorized"):
            retention.pack(self.root, "../verification-9336b0fd4", self.scratch)

    def test_existing_output_is_not_overwritten(self):
        target = self.root / f"r141-{self.name}.tar.xz"
        target.write_bytes(b"previous")
        with self.assertRaises(FileExistsError):
            retention.pack(self.root, self.name, self.scratch)
        self.assertEqual(b"previous", target.read_bytes())
        self.assertEqual(self.before, retention.inventory(self.root / self.name))

    def test_existing_restore_directory_is_not_modified(self):
        manifest = retention.pack(self.root, self.name, self.scratch)
        with self.assertRaises(FileExistsError):
            retention.verify(self.root, manifest, self.scratch)


if __name__ == "__main__":
    unittest.main()
