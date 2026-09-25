import hashlib
import json
import shutil
import tempfile
import unittest
import zlib
from pathlib import Path

from scripts.v120_precision_world_fixture import (
    FIXTURE,
    SUMMARY,
    FixtureError,
    create,
    verify,
)


class PrecisionWorldFixtureTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def copy_fixture(self) -> Path:
        target = self.root / "fixture"
        shutil.copytree(FIXTURE, target)
        return target

    @staticmethod
    def write_json(path: Path, document: dict) -> None:
        path.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n",
            encoding="utf-8", newline="\n",
        )

    def test_archived_chunks_match_the_packaged_world_report(self) -> None:
        manifest = verify()
        self.assertEqual(4, len(manifest["chunks"]))
        self.assertEqual(
            "66ba5141c0568e7abcd156f1b19c6a9b6de25d63af14acbb8308ddf2b242954e",
            manifest["source_region_sha256"],
        )

    def test_tampered_chunk_is_rejected(self) -> None:
        fixture = self.copy_fixture()
        chunk = fixture / "c.8.8.nbt.zlib"
        chunk.write_bytes(chunk.read_bytes() + b"tampered")
        with self.assertRaisesRegex(FixtureError, "hash mismatch"):
            verify(fixture)

    def test_rehashed_chunk_with_changed_output_is_rejected(self) -> None:
        fixture = self.copy_fixture()
        chunk = fixture / "c.9.9.nbt.zlib"
        decoded = zlib.decompress(chunk.read_bytes())
        original = b"advancedrocketrycommunity:advanced_circuit"
        replacement = b"advancedrocketrycommunity:advanced_circuiX"
        self.assertEqual(len(original), len(replacement))
        self.assertIn(original, decoded)
        encoded = zlib.compress(decoded.replace(original, replacement))
        chunk.write_bytes(encoded)
        manifest_path = fixture / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["chunks"][chunk.name] = hashlib.sha256(encoded).hexdigest()
        self.write_json(manifest_path, manifest)
        with self.assertRaisesRegex(FixtureError, "differs from the final report"):
            verify(fixture)

    def test_rehashed_summary_with_changed_energy_is_rejected(self) -> None:
        fixture = self.copy_fixture()
        summary_path = self.root / "summary.json"
        summary = json.loads(SUMMARY.read_text(encoding="utf-8"))
        for field in ("completed", "stable_after_completion", "after_final_restart"):
            summary[field]["energy"] = 999
        self.write_json(summary_path, summary)
        manifest_path = fixture / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["source_summary_sha256"] = hashlib.sha256(summary_path.read_bytes()).hexdigest()
        self.write_json(manifest_path, manifest)
        with self.assertRaisesRegex(FixtureError, "Energy port differs"):
            verify(fixture, summary_path)

    def test_rehashed_summary_with_extra_input_is_rejected(self) -> None:
        fixture = self.copy_fixture()
        summary_path = self.root / "summary.json"
        summary = json.loads(SUMMARY.read_text(encoding="utf-8"))
        for field in ("completed", "stable_after_completion", "after_final_restart"):
            summary[field]["inputs"].append("minecraft:air:0")
        self.write_json(summary_path, summary)
        manifest_path = fixture / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["source_summary_sha256"] = hashlib.sha256(summary_path.read_bytes()).hexdigest()
        self.write_json(manifest_path, manifest)
        with self.assertRaisesRegex(FixtureError, "incomplete Item report"):
            verify(fixture, summary_path)

    def test_create_rejects_a_region_without_the_archived_hash(self) -> None:
        region = self.root / "r.0.0.mca"
        region.write_bytes(b"\0" * 8192)
        output = self.root / "new-fixture"
        with self.assertRaisesRegex(FixtureError, "Source region does not match"):
            create(region, output)
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
