import gzip
import json
import tempfile
import unittest
from pathlib import Path

from scripts.manage_v040_generated_manifest import (
    EXPECTED_PATHS,
    GENERATED_ROOT,
    generate,
    render_manifest,
    verify,
)


class V040GeneratedManifestTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        for relative in EXPECTED_PATHS:
            target = self.root / GENERATED_ROOT / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.suffix == ".nbt":
                target.write_bytes(gzip.compress(b"\x0a\x00\x00\x00"))
            else:
                target.write_text('{"fixture":true}\n', encoding="utf-8", newline="\n")

    def test_exact_inventory_is_sorted_and_bounded(self) -> None:
        manifest = render_manifest(self.root)

        self.assertEqual("v0.4.0", manifest["target_version"])
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

    def test_missing_unexpected_or_invalid_nbt_is_rejected(self) -> None:
        nbt = next(path for path in EXPECTED_PATHS if path.endswith(".nbt"))
        target = self.root / GENERATED_ROOT / nbt
        target.write_bytes(b"not-nbt")
        with self.assertRaisesRegex(ValueError, "invalid compressed NBT"):
            render_manifest(self.root)

        target.write_bytes(gzip.compress(b"\x0a\x00\x00\x00"))
        missing = self.root / GENERATED_ROOT / sorted(EXPECTED_PATHS)[0]
        missing.unlink()
        extra = self.root / GENERATED_ROOT / "data/example/unexpected.json"
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_text("{}\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            render_manifest(self.root)


if __name__ == "__main__":
    unittest.main()
