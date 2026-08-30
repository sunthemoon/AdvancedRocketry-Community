from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.manage_v010_generated_manifest import generate, verify


class ManageV010GeneratedManifestTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.imported = "src/generated/resources/assets/advancedrocketrycommunity/lang/en_us.json"
        self.generated = "src/generated/resources/assets/advancedrocketrycommunity/models/item/fixture.json"
        self.write(self.imported, b'{"item.fixture":"Fixture"}')
        self.write(self.generated, b'{"parent":"item/generated"}')
        record = {"entries": [{"target_path": self.imported}]}
        self.write(
            "docs/provenance/v0.1.0-minimal-content.json",
            (json.dumps(record, sort_keys=True) + "\n").encode(),
        )
        self.output = self.root / "docs/provenance/v0.1.0-generated-resources.json"

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write(self, relative: str, data: bytes) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def test_generate_excludes_provenance_managed_target(self) -> None:
        manifest = generate(self.root, self.output)
        self.assertEqual([self.imported], manifest["provenance_managed_targets"])
        self.assertEqual([self.generated], [entry["path"] for entry in manifest["targets"]])
        self.assertEqual([], verify(self.root, self.output))

    def test_changed_generated_file_is_rejected(self) -> None:
        generate(self.root, self.output)
        self.write(self.generated, b"changed")
        self.assertTrue(any("does not match" in error for error in verify(self.root, self.output)))

    def test_missing_generated_root_is_rejected(self) -> None:
        empty = Path(self.temporary.name) / "empty-project"
        empty.mkdir()
        record = empty / "docs/provenance/v0.1.0-minimal-content.json"
        record.parent.mkdir(parents=True)
        record.write_text('{"entries": []}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "root is missing"):
            generate(empty, empty / "manifest.json")


if __name__ == "__main__":
    unittest.main()
