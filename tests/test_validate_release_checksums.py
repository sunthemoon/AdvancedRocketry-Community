import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from scripts.validate_release_checksums import validate_release_checksums


class ReleaseChecksumValidationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.release_dir = self.root / "docs/releases/v0.0.2"
        self.evidence_dir = self.release_dir / "evidence"
        self.manifest = self.evidence_dir / "artifact/jar-content-manifest.json"
        self.lifecycle = self.evidence_dir / "dedicated-server/first-start.txt"
        self.checksums = self.release_dir / "checksums.txt"
        self.artifact_name = (
            "advancedrocketry-community-1.20.1-0.0.2-dev.jar"
        )
        self.artifact = self.root / "build/libs" / self.artifact_name

        self.manifest.parent.mkdir(parents=True)
        self.lifecycle.parent.mkdir(parents=True)
        self.artifact.parent.mkdir(parents=True)
        self.lifecycle.write_text("server lifecycle evidence\n", encoding="utf-8")
        self.artifact.write_bytes(b"reproducible distributable")
        self.write_manifest()

        self.tracked_files = {
            "docs/releases/v0.0.2/checksums.txt",
            "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
            "docs/releases/v0.0.2/evidence/dedicated-server/first-start.txt",
        }

    @staticmethod
    def digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def write_manifest(self, *, include_artifact_sha256: bool = True) -> None:
        document: dict[str, object] = {
            "schema_version": 1,
            "artifact": self.artifact_name,
            "entry_count": 1,
            "entries": [
                {
                    "path": "pack.mcmeta",
                    "size": 2,
                    "sha256": hashlib.sha256(b"{}").hexdigest(),
                }
            ],
        }
        if include_artifact_sha256:
            document["artifact_sha256"] = self.digest(self.artifact)
        self.manifest.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def write_checksums(
        self,
        *,
        include_lifecycle: bool = True,
        extra_lines: tuple[str, ...] = (),
    ) -> None:
        lines = [
            "# v0.0.2 release evidence",
            "",
            f"{self.digest(self.manifest)}  "
            "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
        ]
        if include_lifecycle:
            lines.append(
                f"{self.digest(self.lifecycle)}  "
                "docs/releases/v0.0.2/evidence/dedicated-server/first-start.txt"
            )
        lines.extend(extra_lines)
        lines.append(f"{self.digest(self.artifact)}  {self.artifact_name}")
        self.checksums.parent.mkdir(parents=True, exist_ok=True)
        self.checksums.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def validate(self, *, artifact: Path | None = None) -> list[str]:
        errors, _ = validate_release_checksums(
            repository_root=self.root,
            artifact_path=artifact,
            tracked_files=self.tracked_files,
        )
        return errors

    def test_happy_path_supports_governance_and_real_artifact_modes(self) -> None:
        self.write_checksums()

        governance_errors, governance_details = validate_release_checksums(
            repository_root=self.root,
            tracked_files=self.tracked_files,
        )
        artifact_errors, artifact_details = validate_release_checksums(
            repository_root=self.root,
            artifact_path=self.artifact,
            tracked_files=self.tracked_files,
        )

        self.assertEqual([], governance_errors)
        self.assertFalse(governance_details["artifact_verified"])
        self.assertEqual([], artifact_errors)
        self.assertTrue(artifact_details["artifact_verified"])
        self.assertEqual(3, artifact_details["entries"])

    def test_tampered_committed_evidence_is_rejected(self) -> None:
        self.write_checksums()
        self.lifecycle.write_text("tampered evidence\n", encoding="utf-8")

        errors = self.validate()

        self.assertTrue(
            any("SHA-256 mismatch for committed file" in error for error in errors),
            errors,
        )

    def test_evidence_omitted_from_checksum_list_is_rejected(self) -> None:
        self.write_checksums(include_lifecycle=False)

        errors = self.validate()

        self.assertTrue(
            any("Evidence files omitted from checksum list" in error for error in errors),
            errors,
        )
        self.assertTrue(any("first-start.txt" in error for error in errors), errors)

    def test_unsafe_traversal_path_is_rejected(self) -> None:
        unsafe_line = f"{hashlib.sha256(b'outside').hexdigest()}  ../outside.txt"
        self.write_checksums(extra_lines=(unsafe_line,))

        errors = self.validate()

        self.assertTrue(any("unsafe path" in error for error in errors), errors)
        self.assertTrue(any("traversal" in error for error in errors), errors)

    def test_duplicate_path_is_rejected(self) -> None:
        duplicate = (
            f"{self.digest(self.lifecycle)}  "
            "docs/releases/v0.0.2/evidence/dedicated-server/first-start.txt"
        )
        self.write_checksums(extra_lines=(duplicate,))

        errors = self.validate()

        self.assertTrue(any("duplicate path" in error for error in errors), errors)

    def test_real_artifact_hash_mismatch_is_rejected(self) -> None:
        self.write_checksums()
        self.artifact.write_bytes(b"different distributable")

        errors = self.validate(artifact=self.artifact)

        self.assertTrue(
            any("Artifact SHA-256 does not match" in error for error in errors),
            errors,
        )

    def test_missing_artifact_metadata_is_rejected(self) -> None:
        self.write_manifest(include_artifact_sha256=False)
        self.write_checksums()

        errors = self.validate()

        self.assertTrue(
            any("missing lowercase artifact_sha256 metadata" in error for error in errors),
            errors,
        )


if __name__ == "__main__":
    unittest.main()
