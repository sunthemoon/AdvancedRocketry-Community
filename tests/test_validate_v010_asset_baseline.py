from __future__ import annotations

import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path, PurePosixPath

from scripts.manage_v010_generated_manifest import generate as generate_manifest
from scripts.validate_v010_asset_baseline import (
    GENERATED_MANIFEST,
    validate_v010_asset_baseline,
)


SOURCE_ROOT = Path(__file__).resolve().parents[1]


class ValidateV010AssetBaselineTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        fixed = (
            "docs/provenance/schema-v1.json",
            "docs/provenance/v0.1.0-minimal-content.json",
            "docs/provenance/v0.1.0-generated-resources.json",
            "legacy-manifest/assets.csv",
            "legacy-manifest/UPSTREAM_COMMIT.txt",
        )
        for relative in fixed:
            self.copy(relative)
        record = json.loads(
            (SOURCE_ROOT / "docs/provenance/v0.1.0-minimal-content.json").read_text(encoding="utf-8")
        )
        generated = json.loads(
            (SOURCE_ROOT / "docs/provenance/v0.1.0-generated-resources.json").read_text(encoding="utf-8")
        )
        self.managed = [entry["target_path"] for entry in record["entries"]]
        self.managed.extend(entry["path"] for entry in generated["targets"])
        for relative in self.managed:
            self.copy(relative)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def copy(self, relative: str) -> None:
        source = SOURCE_ROOT / relative
        target = self.root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

    def test_current_fixture_is_valid(self) -> None:
        errors, details = validate_v010_asset_baseline(self.root)
        self.assertEqual([], errors)
        self.assertEqual(37, details["resource_count"])
        self.assertEqual("APPROVED", details["review_status"])

    def test_changed_imported_target_is_rejected(self) -> None:
        target = self.root / "src/main/resources/assets/advancedrocketrycommunity/textures/item/basic_circuit.png"
        target.write_bytes(b"tampered")
        errors, _ = validate_v010_asset_baseline(self.root)
        self.assertTrue(any("target hash mismatch" in error for error in errors))

    def test_unrecorded_resource_is_rejected(self) -> None:
        extra = self.root / "src/main/resources/assets/advancedrocketrycommunity/textures/item/unrecorded.png"
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\x00" * 40)
        errors, _ = validate_v010_asset_baseline(self.root)
        self.assertTrue(any("unrecorded resources" in error for error in errors))

    def test_missing_local_model_reference_is_rejected(self) -> None:
        model = self.root / "src/generated/resources/assets/advancedrocketrycommunity/models/item/basic_circuit.json"
        value = json.loads(model.read_text(encoding="utf-8"))
        value["textures"]["layer0"] = "advancedrocketrycommunity:item/not_present"
        model.write_text(json.dumps(value, sort_keys=True), encoding="utf-8")
        generate_manifest(self.root, self.root / GENERATED_MANIFEST)
        errors, _ = validate_v010_asset_baseline(self.root)
        self.assertTrue(any("missing texture reference" in error for error in errors))

    def test_jar_must_match_managed_resource_set(self) -> None:
        jar = self.root / "valid.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for relative in self.managed:
                archive_name = relative
                for prefix in ("src/main/resources/", "src/generated/resources/"):
                    archive_name = archive_name.removeprefix(prefix)
                archive.writestr(archive_name, (self.root / relative).read_bytes())
        errors, _ = validate_v010_asset_baseline(self.root, jar)
        self.assertEqual([], errors)

        bad = self.root / "bad.jar"
        shutil.copyfile(jar, bad)
        with zipfile.ZipFile(bad, "a") as archive:
            archive.writestr("assets/advancedrocketry/unapproved.png", b"legacy")
        errors, _ = validate_v010_asset_baseline(self.root, bad)
        self.assertTrue(any("legacy namespace" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
