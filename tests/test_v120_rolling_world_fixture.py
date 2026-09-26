import copy
import hashlib
import json
from pathlib import Path
import shutil
import struct
import tempfile
import unittest
from unittest.mock import patch
import zlib

from scripts import v120_rolling_world_fixture as fixture


class RollingWorldFixtureTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def copy_fixture(self):
        target = self.root / "fixture"
        shutil.copytree(fixture.FIXTURE, target)
        return target

    @staticmethod
    def write_json(path, value):
        path.write_text(json.dumps(value, sort_keys=True, indent=2) + "\n",
                        encoding="utf-8", newline="\n")

    def replace_chunk(self, target, old, new):
        path = target / fixture.CHUNK
        expanded = zlib.decompress(path.read_bytes())
        self.assertEqual(1, expanded.count(old))
        compressed = zlib.compress(expanded.replace(old, new))
        path.write_bytes(compressed)
        manifest_path = target / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["chunks"][fixture.CHUNK] = hashlib.sha256(compressed).hexdigest()
        self.write_json(manifest_path, manifest)

    def test_actual_candidate_world_chunk_matches_report(self):
        manifest = fixture.verify()
        self.assertEqual("e6f27d28197813de6ed5aaddb3f694310e6c1b532b3299fb3fd282578a97dc9d",
                         manifest["source_region_sha256"])
        self.assertTrue(manifest["uncommitted_worktree"])
        self.assertIsNone(manifest["source_tested_commit"])
        self.assertEqual("6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a",
                         manifest["artifact_sha256"])

    def test_tampered_chunk_hash_is_rejected(self):
        target = self.copy_fixture()
        path = target / fixture.CHUNK
        path.write_bytes(path.read_bytes() + b"tamper")
        with self.assertRaisesRegex(fixture.FixtureError, "hash mismatch"):
            fixture.verify(target)

    def test_rehashed_output_item_is_rejected(self):
        target = self.copy_fixture()
        self.replace_chunk(target, b"minecraft:iron_bars", b"minecraft:iron_barX")
        with self.assertRaisesRegex(fixture.FixtureError, "resources or binding"):
            fixture.verify(target)

    def test_rehashed_energy_is_rejected(self):
        target = self.copy_fixture()
        prefix = b"\x03\x00\x06energy"
        self.replace_chunk(target, prefix + struct.pack(">i", 2000), prefix + struct.pack(">i", 2001))
        with self.assertRaisesRegex(fixture.FixtureError, "resources or binding"):
            fixture.verify(target)

    def test_rehashed_fluid_is_rejected(self):
        target = self.copy_fixture()
        prefix = b"\x03\x00\x06Amount"
        self.replace_chunk(target, prefix + struct.pack(">i", 400), prefix + struct.pack(">i", 401))
        with self.assertRaisesRegex(fixture.FixtureError, "resources or binding"):
            fixture.verify(target)

    def test_rehashed_transaction_id_is_rejected(self):
        target = self.copy_fixture()
        self.replace_chunk(target, b"36940a00-82f2-4f56-8880-4413a8edb968",
                           b"36940a00-82f2-4f56-8880-4413a8edb969")
        with self.assertRaisesRegex(fixture.FixtureError, "process differs"):
            fixture.verify(target)

    def test_rehashed_dataversion_is_rejected(self):
        target = self.copy_fixture()
        prefix = b"\x03\x00\x0bDataVersion"
        self.replace_chunk(target, prefix + struct.pack(">i", 3465), prefix + struct.pack(">i", 3464))
        with self.assertRaisesRegex(fixture.FixtureError, "DataVersion"):
            fixture.verify(target)

    def test_rehashed_rotation_is_rejected(self):
        target = self.copy_fixture()
        prefix = b"\x03\x00\x08rotation"
        self.replace_chunk(target, prefix + struct.pack(">i", 0), prefix + struct.pack(">i", 1))
        with self.assertRaisesRegex(fixture.FixtureError, "formation is invalid"):
            fixture.verify(target)

    def test_duplicate_identity_or_foreign_binding_is_rejected(self):
        source = fixture.common._decode_chunk((fixture.FIXTURE / fixture.CHUNK).read_bytes(), 8, 8)
        summary, _ = fixture._source(fixture.SUMMARY)
        for mutation in ("duplicate", "binding"):
            decoded = copy.deepcopy(source)
            if mutation == "duplicate":
                decoded["block_entities"][1] = copy.deepcopy(decoded["block_entities"][0])
            else:
                decoded["block_entities"][0]["arce_part_binding"]["machine_instance_id"] = (
                    "00000000-0000-0000-0000-000000000001")
            with self.subTest(mutation=mutation), patch.object(
                    fixture.common, "_decode_chunk", return_value=decoded), self.assertRaises(fixture.FixtureError):
                fixture._validate(b"test-double", summary)

    def test_extra_file_or_manifest_provenance_is_rejected(self):
        target = self.copy_fixture()
        extra = target / "unexpected"
        extra.write_bytes(b"extra")
        with self.assertRaisesRegex(fixture.FixtureError, "manifest or file set"):
            fixture.verify(target)
        extra.unlink()
        manifest_path = target / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["artifact_sha256"] = "0" * 64
        self.write_json(manifest_path, manifest)
        with self.assertRaisesRegex(fixture.FixtureError, "manifest or file set"):
            fixture.verify(target)

    def test_summary_cannot_claim_dirty_artifact_as_clean_commit(self):
        summary = json.loads(fixture.SUMMARY.read_text(encoding="utf-8"))
        summary["tested_implementation_commit"] = summary["implementation_base_commit"]
        path = self.root / "summary.json"
        self.write_json(path, summary)
        with self.assertRaisesRegex(fixture.FixtureError, "source provenance"):
            fixture._source(path)

    def test_rehashed_summary_cannot_change_batch_ledger(self):
        summary = json.loads(fixture.SUMMARY.read_text(encoding="utf-8"))
        for key in ("completed", "after_final_restart", "stable_after_completion"):
            summary[key]["energy"] = 2001
        path = self.root / "summary.json"
        self.write_json(path, summary)
        with self.assertRaisesRegex(fixture.FixtureError, "conserve the seeded batch"):
            fixture._source(path)

    def test_create_rejects_wrong_region_without_writing(self):
        region = self.root / "r.0.0.mca"
        region.write_bytes(b"\0" * 8192)
        output = self.root / "new"
        with self.assertRaisesRegex(fixture.FixtureError, "Source region does not match"):
            fixture.create(region, output)
        self.assertFalse(output.exists())

    def test_create_refuses_existing_output(self):
        target = self.copy_fixture()
        with self.assertRaisesRegex(fixture.FixtureError, "Refusing to overwrite"):
            fixture.create(self.root / "missing-region", target)

    def test_linked_parent_is_rejected(self):
        with patch.object(fixture, "is_link_or_junction", side_effect=lambda path: path == self.root):
            with self.assertRaisesRegex(fixture.FixtureError, "Linked fixture path"):
                fixture.verify(self.root / "child")

    def test_compressed_size_and_trailing_bytes_are_bounded(self):
        summary, _ = fixture._source(fixture.SUMMARY)
        compressed = (fixture.FIXTURE / fixture.CHUNK).read_bytes()
        for payload in (compressed + b"trailing", b"\0" * (fixture.common.MAX_COMPRESSED_CHUNK + 1),
                        zlib.compress(b"\0" * (fixture.common.MAX_EXPANDED_CHUNK + 1))):
            with self.subTest(size=len(payload)), self.assertRaises(fixture.FixtureError):
                fixture._validate(payload, summary)


if __name__ == "__main__":
    unittest.main()
