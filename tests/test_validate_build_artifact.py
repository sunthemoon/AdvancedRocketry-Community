import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.validate_build_artifact import (
    DEFAULT_VERSION,
    parse_manifest,
    validate_artifact,
)


class ManifestParsingTests(unittest.TestCase):
    def test_continuation_lines_are_joined(self) -> None:
        manifest = "Implementation-Title: Advanced Rocketry: Community\r\n Edition\r\n"

        self.assertEqual(
            "Advanced Rocketry: CommunityEdition",
            parse_manifest(manifest)["Implementation-Title"],
        )


class ArtifactValidationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)

    def create_artifact(self, extra_entries: dict[str, bytes] | None = None) -> Path:
        artifact = self.root / f"advancedrocketry-community-{DEFAULT_VERSION}.jar"
        entries = {
            "META-INF/MANIFEST.MF": (
                "Manifest-Version: 1.0\n"
                "Specification-Title: advancedrocketrycommunity\n"
                "Implementation-Title: Advanced Rocketry: Community Edition\n"
                f"Implementation-Version: {DEFAULT_VERSION}\n"
            ).encode(),
            "META-INF/LICENSE": b"MIT License\nPermission is hereby granted",
            "META-INF/NOTICE.md": b"NOT AN OFFICIAL MINECRAFT PRODUCT",
            "META-INF/mods.toml": (
                'modLoader="javafml"\n'
                'license="MIT"\n'
                'modId="advancedrocketrycommunity"\n'
                f'version="{DEFAULT_VERSION}"\n'
                'displayName="Advanced Rocketry: Community Edition"\n'
                'displayTest="MATCH_VERSION"\n'
                'features={java_version="[17,)"}\n'
                'versionRange="[47.4.10,48)"\n'
                'versionRange="[1.20.1,1.20.2)"\n'
            ).encode(),
            "pack.mcmeta": json.dumps(
                {
                    "pack": {
                        "description": "Advanced Rocketry: Community Edition resources",
                        "pack_format": 15,
                    }
                }
            ).encode(),
            "advancedrocketrycommunity.png": b"\x89PNG\r\n\x1a\n",
            "io/github/sunthemoon/advancedrocketrycommunity/AdvancedRocketryCommunity.class": b"class",
        }
        entries.update(extra_entries or {})
        with zipfile.ZipFile(artifact, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)
        return artifact

    def test_complete_artifact_passes(self) -> None:
        errors, details = validate_artifact(self.create_artifact())

        self.assertEqual([], errors)
        self.assertEqual(DEFAULT_VERSION, details["expected_version"])
        self.assertIn("sha256", details)

    def test_sensitive_file_is_rejected(self) -> None:
        artifact = self.create_artifact({"config/credentials.json": b"not-a-secret"})

        errors, _ = validate_artifact(artifact)

        self.assertTrue(any("Sensitive-looking" in error for error in errors))

    def test_environment_file_variant_is_rejected(self) -> None:
        artifact = self.create_artifact({"config/.env.production": b"MODE=prod"})

        errors, _ = validate_artifact(artifact)

        self.assertTrue(any("Sensitive-looking" in error for error in errors))

    def test_credential_like_content_is_rejected(self) -> None:
        artifact = self.create_artifact(
            {"assets/config.txt": b"-----BEGIN PRIVATE KEY-----"}
        )

        errors, _ = validate_artifact(artifact)

        self.assertTrue(any("Credential-like content" in error for error in errors))

    def test_parent_path_entry_is_rejected(self) -> None:
        artifact = self.create_artifact({"../outside.txt": b"unsafe"})

        errors, _ = validate_artifact(artifact)

        self.assertTrue(any("Unsafe archive paths" in error for error in errors))

    def test_unresolved_metadata_placeholder_is_rejected(self) -> None:
        artifact = self.create_artifact({"META-INF/mods.toml": b'version="${mod_version}"'})

        errors, _ = validate_artifact(artifact)

        self.assertTrue(any("unresolved" in error.lower() for error in errors))


if __name__ == "__main__":
    unittest.main()
