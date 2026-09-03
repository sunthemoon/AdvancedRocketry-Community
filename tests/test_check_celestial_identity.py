import tempfile
import unittest
from pathlib import Path

from scripts.check_celestial_identity import CELESTIAL_ROOT, check_celestial_identity


class CelestialIdentityScanTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.source = self.root / CELESTIAL_ROOT
        self._write(
            "CelestialIds.java",
            "class CelestialIds { ResourceKey<Level> MOON_LEVEL; ResourceKey<Level> SPACE_LEVEL; }",
        )
        self._write(
            "persistence/CelestialSavedData.java",
            'class CelestialSavedData { ResourceLocation id; String schema = "schema_version"; int CURRENT_SCHEMA_VERSION; }',
        )
        self._write(
            "network/CelestialSnapshot.java",
            "record CelestialSnapshot(ResourceLocation id) {}",
        )
        self._write(
            "network/CelestialSnapshotCodec.java",
            "class CelestialSnapshotCodec { int n = CelestialCatalog.MAX_BODIES; int MAX_PACKET_BYTES; }",
        )

    def _write(self, relative: str, content: str) -> Path:
        target = self.source / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content + "\n", encoding="utf-8", newline="\n")
        return target

    def test_namespaced_identity_baseline_passes(self) -> None:
        self.assertEqual([], check_celestial_identity(self.root))

    def test_managed_migrator_may_own_the_schema_marker(self) -> None:
        self._write(
            "persistence/CelestialSavedData.java",
            "class CelestialSavedData { ResourceLocation id; "
            "int CURRENT_SCHEMA_VERSION; SavedDataSchemaMigrator migrator; "
            "Object type = ManagedSavedDataType.CELESTIAL; }",
        )

        self.assertEqual([], check_celestial_identity(self.root))

    def test_missing_local_and_managed_schema_identity_is_rejected(self) -> None:
        self._write(
            "persistence/CelestialSavedData.java",
            "class CelestialSavedData { ResourceLocation id; int CURRENT_SCHEMA_VERSION; }",
        )

        errors = check_celestial_identity(self.root)

        self.assertTrue(any("required schema identity" in error for error in errors), errors)

    def test_runtime_numeric_dimension_identity_is_rejected(self) -> None:
        self._write("service/BadRuntime.java", "class BadRuntime { int dimensionId; }")

        errors = check_celestial_identity(self.root)

        self.assertTrue(any("numeric dimension identity" in error for error in errors), errors)

    def test_dom_reference_is_rejected_even_in_legacy_adapter(self) -> None:
        self._write(
            "legacy/LegacyXmlParser.java",
            "class LegacyXmlParser { org.w3c.dom.Document document; }",
        )

        errors = check_celestial_identity(self.root)

        self.assertTrue(any("DOM reference" in error for error in errors), errors)

    def test_numeric_metadata_is_limited_to_report_only_legacy_files(self) -> None:
        self._write(
            "legacy/LegacyCelestialImporter.java",
            "class LegacyCelestialImporter { int numericDimensionId; }",
        )
        self.assertEqual([], check_celestial_identity(self.root))

        self._write(
            "legacy/RuntimeBridge.java",
            "class RuntimeBridge { int numericDimensionId; }",
        )
        errors = check_celestial_identity(self.root)

        self.assertTrue(any("report-only files" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
