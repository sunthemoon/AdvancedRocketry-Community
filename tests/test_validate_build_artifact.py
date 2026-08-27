import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from contextlib import redirect_stdout
from pathlib import Path

from scripts.validate_build_artifact import (
    DEFAULT_VERSION,
    PACKAGED_SOURCE_FILES,
    build_content_manifest,
    main,
    parse_manifest,
    serialize_content_manifest,
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
            **{
                packaged_path: source_path.read_bytes()
                for packaged_path, source_path in PACKAGED_SOURCE_FILES.items()
            },
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

    def test_content_manifest_reports_every_entry_in_sorted_order(self) -> None:
        contents = {
            "z-last.txt": b"last",
            "a-first.bin": b"\x00first\xff",
            "empty/": b"",
        }
        artifact = self.create_artifact(contents)

        manifest = build_content_manifest(artifact)

        with zipfile.ZipFile(artifact) as archive:
            archive_entries = {
                info.filename: archive.read(info) for info in archive.infolist()
            }
        reported_paths = [entry["path"] for entry in manifest["entries"]]
        self.assertEqual(sorted(archive_entries), reported_paths)
        self.assertEqual(len(archive_entries), manifest["entry_count"])
        self.assertEqual(1, manifest["schema_version"])
        self.assertEqual(artifact.name, manifest["artifact"])
        self.assertNotIn(str(self.root), serialize_content_manifest(manifest))

        entries_by_path = {entry["path"]: entry for entry in manifest["entries"]}
        for path, content in archive_entries.items():
            with self.subTest(path=path):
                self.assertEqual(len(content), entries_by_path[path]["size"])
                self.assertEqual(
                    hashlib.sha256(content).hexdigest(),
                    entries_by_path[path]["sha256"],
                )

    def test_cli_writes_byte_stable_content_manifest(self) -> None:
        artifact = self.create_artifact({"assets/example.txt": b"evidence"})
        first_output = self.root / "first" / "jar-content-manifest.json"
        second_output = self.root / "second" / "jar-content-manifest.json"

        first_stdout = io.StringIO()
        with redirect_stdout(first_stdout):
            first_result = main(
                [str(artifact), "--content-manifest", str(first_output)]
            )
        second_stdout = io.StringIO()
        with redirect_stdout(second_stdout):
            second_result = main(
                [str(artifact), "--content-manifest", str(second_output)]
            )

        self.assertEqual(0, first_result)
        self.assertEqual(0, second_result)
        self.assertEqual(first_output.read_bytes(), second_output.read_bytes())
        written_manifest = json.loads(first_output.read_text(encoding="utf-8"))
        self.assertEqual(
            hashlib.sha256(artifact.read_bytes()).hexdigest(),
            written_manifest["artifact_sha256"],
        )
        self.assertIn("[PASS] Content manifest:", first_stdout.getvalue())

    def test_cli_does_not_emit_manifest_for_rejected_artifact(self) -> None:
        artifact = self.create_artifact({"config/credentials.json": b"not-a-secret"})
        output = self.root / "jar-content-manifest.json"

        with redirect_stdout(io.StringIO()):
            result = main([str(artifact), "--content-manifest", str(output)])

        self.assertEqual(1, result)
        self.assertFalse(output.exists())

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

    def test_changed_third_party_license_is_rejected(self) -> None:
        artifact = self.create_artifact(
            {"META-INF/licenses/GRADLE-8.1.1-LICENSE.txt": b"changed"}
        )

        errors, _ = validate_artifact(artifact)

        self.assertTrue(
            any("does not match repository source" in error for error in errors)
        )


if __name__ == "__main__":
    unittest.main()
