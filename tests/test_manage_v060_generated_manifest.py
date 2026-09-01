import json
import tempfile
import unittest
from pathlib import Path

from scripts.manage_v060_generated_manifest import (
    EXPECTED_PATHS,
    GENERATED_ROOT,
    generate,
    render_manifest,
    verify,
)


class V060GeneratedManifestTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        for relative in EXPECTED_PATHS:
            target = self.root / GENERATED_ROOT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text('{"fixture":true}\n', encoding="utf-8", newline="\n")

    def test_exact_inventory_is_sorted_and_bounded(self) -> None:
        manifest = render_manifest(self.root)

        self.assertEqual("v0.6.0", manifest["target_version"])
        self.assertEqual(len(EXPECTED_PATHS), len(manifest["targets"]))
        paths = [target["path"] for target in manifest["targets"]]
        self.assertEqual(sorted(paths), paths)

    def test_generated_manifest_verifies_and_detects_tampering(self) -> None:
        output = self.root / "manifest.json"
        generate(self.root, output)

        self.assertEqual([], verify(self.root, output))
        value = json.loads(output.read_text(encoding="utf-8"))
        value["targets"][0]["sha256"] = "0" * 64
        output.write_text(json.dumps(value), encoding="utf-8")

        errors = verify(self.root, output)
        self.assertTrue(any("does not match" in error for error in errors), errors)

    def test_inventory_or_file_type_change_is_rejected(self) -> None:
        (self.root / GENERATED_ROOT / sorted(EXPECTED_PATHS)[0]).unlink()
        extra = self.root / GENERATED_ROOT / "data/example/unexpected.json"
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_text("{}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            render_manifest(self.root)

        extra.unlink()
        disallowed = self.root / GENERATED_ROOT / "data/example/unexpected.txt"
        disallowed.write_text("not JSON\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "unapproved type"):
            render_manifest(self.root)


if __name__ == "__main__":
    unittest.main()
