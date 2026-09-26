import hashlib
import json
import shutil
import struct
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

from scripts import v120_electrolyzer_world_fixture as fixture
from scripts import v120_precision_world_fixture as common


class ElectrolyzerWorldFixtureTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def copy_fixture(self):
        target = self.root / "fixture"
        shutil.copytree(fixture.FIXTURE, target)
        return target

    def rehash_chunk(self, directory, raw):
        encoded = zlib.compress(raw)
        (directory / fixture.CHUNK).write_bytes(encoded)
        manifest_path = directory / "manifest.json"
        manifest = json.loads(manifest_path.read_text())
        manifest["chunks"][fixture.CHUNK] = hashlib.sha256(encoded).hexdigest()
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    def test_real_beta_chunk_contains_both_legacy_machine_states(self):
        manifest = fixture.verify()
        self.assertEqual("f7a92f8f4d8656d5d6058c4cbc29e51d1f8d5eea7703a2d48806bc8ab247113c",
                         manifest["chunks"][fixture.CHUNK])
        self.assertEqual("1.20.1-0.9.0-beta.1", manifest["source_version"])

    def test_source_extraction_is_exact_and_never_overwrites(self):
        output = self.root / "new-fixture"
        self.assertEqual(fixture.verify(), fixture.create(output=output))
        self.assertEqual(fixture.verify(), fixture.verify(output))
        with self.assertRaisesRegex(fixture.FixtureError, "existing"):
            fixture.create(output=output)

    def test_wrong_archive_is_rejected_without_output(self):
        archive = self.root / "archive.zip"
        archive.write_bytes(b"not the source archive")
        output = self.root / "new-fixture"
        with self.assertRaisesRegex(fixture.FixtureError, "immutable Beta-world hash"):
            fixture.create(archive_path=archive, output=output)
        self.assertFalse(output.exists())

    def test_tampered_chunk_is_rejected(self):
        directory = self.copy_fixture()
        path = directory / fixture.CHUNK
        path.write_bytes(path.read_bytes() + b"tampered")
        with self.assertRaisesRegex(fixture.FixtureError, "hash mismatch"):
            fixture.verify(directory)

    def test_rehashed_changed_energy_is_rejected_semantically(self):
        directory = self.copy_fixture()
        raw = zlib.decompress((directory / fixture.CHUNK).read_bytes())
        old = b"\x03\x00\x06energy" + struct.pack(">i", 19500)
        new = b"\x03\x00\x06energy" + struct.pack(">i", 19501)
        self.assertEqual(1, raw.count(old))
        self.rehash_chunk(directory, raw.replace(old, new))
        with self.assertRaisesRegex(fixture.FixtureError, "resources/progress differ"):
            fixture.verify(directory)

    def test_rehashed_wrong_data_version_is_rejected(self):
        directory = self.copy_fixture()
        raw = zlib.decompress((directory / fixture.CHUNK).read_bytes())
        old = b"\x03\x00\x0bDataVersion" + struct.pack(">i", 3465)
        self.assertEqual(1, raw.count(old))
        self.rehash_chunk(directory, raw.replace(old, old[:-4] + struct.pack(">i", 3464)))
        with self.assertRaisesRegex(fixture.FixtureError, "data version"):
            fixture.verify(directory)

    def test_extra_file_and_false_provenance_are_rejected(self):
        directory = self.copy_fixture()
        extra = directory / "extra.nbt"
        extra.write_bytes(b"")
        with self.assertRaisesRegex(fixture.FixtureError, "manifest/file set"):
            fixture.verify(directory)
        extra.unlink()
        manifest_path = directory / "manifest.json"
        manifest = json.loads(manifest_path.read_text())
        manifest["source_artifact_sha256"] = "0" * 64
        manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
        with self.assertRaisesRegex(fixture.FixtureError, "manifest/file set"):
            fixture.verify(directory)

    def test_duplicate_identity_and_already_migrated_roots_are_rejected(self):
        compressed = (fixture.FIXTURE / fixture.CHUNK).read_bytes()
        _, expected = fixture._source(fixture.SUMMARY, fixture.STATE)
        root = common._decode_chunk(compressed, 0, 0)
        entities = root["block_entities"]
        with patch.object(common, "_decode_chunk", return_value=root):
            old_x = entities[1]["x"]
            entities[1]["x"] = entities[0]["x"]
            with self.assertRaisesRegex(fixture.FixtureError, "duplicated"):
                fixture._validate(compressed, expected)
            entities[1]["x"] = old_x
            entities[0]["arce_process"] = {}
            with self.assertRaisesRegex(fixture.FixtureError, "already contains"):
                fixture._validate(compressed, expected)

    def test_linked_parent_is_rejected_before_read(self):
        with patch.object(fixture, "is_link_or_junction", side_effect=lambda p: p == fixture.FIXTURE):
            with self.assertRaisesRegex(fixture.FixtureError, "link or junction"):
                fixture.verify()

    def test_short_region_header_and_trailing_payload_are_rejected(self):
        with self.assertRaisesRegex(fixture.FixtureError, "header is truncated"):
            common._region_chunk(b"\0" * 4096, 0, 0)
        compressed = (fixture.FIXTURE / fixture.CHUNK).read_bytes()
        with self.assertRaisesRegex(fixture.FixtureError, "trailing bytes"):
            common._decode_chunk(compressed + b"trailing", 0, 0)


if __name__ == "__main__":
    unittest.main()
