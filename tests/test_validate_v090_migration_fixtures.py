import json
import tempfile
import unittest
from pathlib import Path

from scripts.validate_v090_migration_fixtures import (
    EXPECTED_FIXTURES,
    FIXTURE_ROOT,
    render_manifest,
    verify,
    write_manifest,
)


class V090MigrationFixtureTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        fixture_root = self.root / FIXTURE_ROOT
        fixture_root.mkdir(parents=True)
        for name in EXPECTED_FIXTURES:
            (fixture_root / name).write_text(
                "{schema_version:1,fixture:[]}\n",
                encoding="utf-8",
                newline="\n",
            )

    def test_manifest_is_exact_sorted_and_hash_bound(self) -> None:
        manifest = render_manifest(self.root)

        self.assertEqual(2, manifest["target_root_schema"])
        self.assertEqual(len(EXPECTED_FIXTURES), len(manifest["fixtures"]))
        paths = [item["path"] for item in manifest["fixtures"]]
        self.assertEqual(sorted(paths), paths)

    def test_verify_detects_fixture_and_manifest_tampering(self) -> None:
        output = self.root / FIXTURE_ROOT / "manifest.json"
        write_manifest(self.root, output)
        self.assertEqual([], verify(self.root, output))

        fixture = self.root / FIXTURE_ROOT / sorted(EXPECTED_FIXTURES)[0]
        fixture.write_text(
            "{schema_version:1,fixture:[1]}\n",
            encoding="utf-8",
            newline="\n",
        )
        errors = verify(self.root, output)
        self.assertTrue(any("exact fixture bytes" in error for error in errors), errors)

        write_manifest(self.root, output)
        value = json.loads(output.read_text(encoding="utf-8"))
        value["fixtures"][0]["sha256"] = "0" * 64
        output.write_text(json.dumps(value), encoding="utf-8", newline="\n")
        errors = verify(self.root, output)
        self.assertTrue(any("exact fixture bytes" in error for error in errors), errors)

    def test_inventory_and_root_schema_are_enforced(self) -> None:
        extra = self.root / FIXTURE_ROOT / "unexpected.snbt"
        extra.write_text("{schema_version:1}\n", encoding="utf-8", newline="\n")
        with self.assertRaisesRegex(ValueError, "inventory mismatch"):
            render_manifest(self.root)
        extra.unlink()

        fixture = self.root / FIXTURE_ROOT / sorted(EXPECTED_FIXTURES)[0]
        fixture.write_text("{schema_version:2}\n", encoding="utf-8", newline="\n")
        with self.assertRaisesRegex(ValueError, "root schema 1"):
            render_manifest(self.root)


if __name__ == "__main__":
    unittest.main()
