import copy
import hashlib
import json
import os
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.validate_bootstrap_provenance import (
    APPROVED_RECORD_STATUS,
    DEFAULT_MANIFEST,
    EXPECTED_RECORD_PATH,
    PENDING_RECORD_STATUS,
    compute_review_content_sha256,
    validate_bootstrap_provenance,
)


class BootstrapProvenanceValidationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.source_root = Path(__file__).resolve().parents[1]
        self.document = json.loads(
            (self.source_root / DEFAULT_MANIFEST).read_text(encoding="utf-8")
        )
        self.manifest = self.root / DEFAULT_MANIFEST

        required_paths = {EXPECTED_RECORD_PATH}
        required_paths.update(
            component["license_copy_target"]
            for component in self.document["components"]
        )
        required_paths.update(target["path"] for target in self.document["targets"])
        required_paths.update(asset["path"] for asset in self.document["local_assets"])
        required_paths.update(
            asset["generator_path"]
            for asset in self.document["local_assets"]
            if asset["status"] == "GENERATED"
        )
        for relative in sorted(required_paths):
            destination = self.root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(self.source_root / relative, destination)

        self.write_manifest()

    @staticmethod
    def digest(content: bytes) -> str:
        return hashlib.sha256(content).hexdigest()

    def write_manifest(self) -> None:
        self.manifest.parent.mkdir(parents=True, exist_ok=True)
        self.manifest.write_text(
            json.dumps(self.document, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def validate(self) -> tuple[list[str], dict[str, int | str]]:
        return validate_bootstrap_provenance(repository_root=self.root)

    def find_target(self, path: str) -> dict[str, object]:
        return next(
            target for target in self.document["targets"] if target["path"] == path
        )

    def find_asset(self, path: str) -> dict[str, object]:
        return next(
            asset
            for asset in self.document["local_assets"]
            if asset["path"] == path
        )

    def approve_current_content(self) -> str:
        self.document["review"] = {
            "record_status": APPROVED_RECORD_STATUS,
            "reviewer": "license-reviewer",
            "reviewed_at": "2026-08-27",
            "final_status_after_review": APPROVED_RECORD_STATUS,
            "reviewed_audited_target_commit": self.document[
                "audited_target_commit"
            ],
            "reviewed_content_sha256": None,
        }
        for target in self.document["targets"]:
            target["status"] = APPROVED_RECORD_STATUS
            target["proposed_status_after_review"] = None

        record_path = self.root / EXPECTED_RECORD_PATH
        record = record_path.read_text(encoding="utf-8")
        replacements = {
            f"record_status: {PENDING_RECORD_STATUS}": (
                f"record_status: {APPROVED_RECORD_STATUS}"
            ),
            "reviewer: null": "reviewer: license-reviewer",
            "reviewed_at: null": "reviewed_at: 2026-08-27",
            "final_status_after_review: null": (
                f"final_status_after_review: {APPROVED_RECORD_STATUS}"
            ),
            "reviewed_audited_target_commit: null": (
                "reviewed_audited_target_commit: "
                f"{self.document['audited_target_commit']}"
            ),
        }
        for old, new in replacements.items():
            record = record.replace(old, new, 1)

        digest = compute_review_content_sha256(self.document, record)
        self.document["review"]["reviewed_content_sha256"] = digest
        record = record.replace(
            "reviewed_content_sha256: null",
            f"reviewed_content_sha256: {digest}",
            1,
        )
        record_path.write_text(record, encoding="utf-8")
        self.write_manifest()
        return digest

    def test_happy_pending_path_validates_all_required_entries(self) -> None:
        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(2, details["components"])
        self.assertEqual(11, details["targets"])
        self.assertEqual(2, details["local_assets"])
        self.assertEqual(PENDING_RECORD_STATUS, details["review_status"])
        self.assertRegex(details["review_content_sha256"], r"^[0-9a-f]{64}$")

    def test_changed_imported_target_is_rejected(self) -> None:
        (self.root / "build.gradle").write_text("tampered\n", encoding="utf-8")

        errors, _ = self.validate()

        self.assertTrue(
            any("SHA-256 mismatch for imported target build.gradle" in error for error in errors),
            errors,
        )

    def test_missing_imported_target_entry_is_rejected(self) -> None:
        removed = self.document["targets"].pop()
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("missing required imported targets" in error for error in errors), errors
        )
        self.assertTrue(any(removed["path"] in error for error in errors), errors)

    def test_unexpected_imported_target_entry_is_rejected(self) -> None:
        content = b"unexpected bootstrap input\n"
        extra_path = "docs/provenance/unexpected-bootstrap-input.txt"
        destination = self.root / extra_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(content)
        extra = copy.deepcopy(self.document["targets"][0])
        extra["path"] = extra_path
        extra["current_target_sha256"] = self.digest(content)
        self.document["targets"].append(extra)
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("unexpected imported targets" in error for error in errors), errors
        )
        self.assertTrue(any(extra_path in error for error in errors), errors)

    def test_duplicate_imported_target_entry_is_rejected(self) -> None:
        duplicate = copy.deepcopy(self.document["targets"][0])
        self.document["targets"].append(duplicate)
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("duplicate imported target path" in error for error in errors), errors
        )

    def test_unsafe_relative_path_is_rejected(self) -> None:
        self.document["targets"][0]["path"] = "../outside.txt"
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(any("unsafe path" in error for error in errors), errors)
        self.assertTrue(any("traversal" in error for error in errors), errors)

    def test_non_lowercase_source_hash_is_rejected(self) -> None:
        self.document["targets"][0]["source_sha256"] = self.document["targets"][
            0
        ]["source_sha256"].upper()
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("source_sha256 must be lowercase" in error for error in errors), errors
        )

    def test_wrong_component_license_is_rejected(self) -> None:
        self.document["components"][0]["license"] = "MIT"
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("component forge_mdk license must be LGPL-2.1-only" in error for error in errors),
            errors,
        )

    def test_changed_component_source_identity_is_rejected(self) -> None:
        self.document["components"][0]["source_commit"] = "0" * 40
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("component forge_mdk source_commit must be" in error for error in errors),
            errors,
        )

    def test_changed_exact_license_copy_is_rejected(self) -> None:
        license_path = self.document["components"][0]["license_copy_target"]
        with (self.root / license_path).open("ab") as stream:
            stream.write(b"tampered")

        errors, _ = self.validate()

        self.assertTrue(
            any("SHA-256 mismatch for component forge_mdk license copy" in error for error in errors),
            errors,
        )

    def test_changed_local_asset_is_rejected(self) -> None:
        logo = "src/main/resources/advancedrocketrycommunity.png"
        with (self.root / logo).open("ab") as stream:
            stream.write(b"tampered")

        errors, _ = self.validate()

        self.assertTrue(
            any(f"SHA-256 mismatch for local asset {logo}" in error for error in errors),
            errors,
        )

    def test_unlisted_text_resource_is_rejected(self) -> None:
        extra = self.root / "src/main/resources/assets/example/lang/en_us.json"
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_text('{"key":"value"}\n', encoding="utf-8")

        errors, _ = self.validate()

        self.assertTrue(
            any("resource files missing provenance entries" in error for error in errors),
            errors,
        )
        self.assertTrue(any(extra.relative_to(self.root).as_posix() in error for error in errors), errors)

    def test_excluded_datagen_cache_is_not_treated_as_a_source_resource(self) -> None:
        cache = self.root / "src/generated/resources/.cache/state"
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_text("implementation metadata\n", encoding="utf-8")

        errors, _ = self.validate()

        self.assertEqual([], errors)

    def test_symlinked_target_is_rejected(self) -> None:
        target = self.root / "build.gradle"
        replacement = self.root / "replacement.gradle"
        replacement.write_bytes(target.read_bytes())
        target.unlink()
        try:
            os.symlink(replacement, target)
        except OSError as exc:
            self.skipTest(f"symlinks are unavailable: {exc}")

        errors, _ = self.validate()

        self.assertTrue(any("must not use a symlink" in error for error in errors), errors)

    def test_pending_review_cannot_carry_reviewer_metadata(self) -> None:
        self.document["review"]["reviewer"] = "premature-reviewer"
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("pending review must have null approval metadata" in error for error in errors),
            errors,
        )

    def test_complete_approved_review_is_accepted(self) -> None:
        self.approve_current_content()

        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(APPROVED_RECORD_STATUS, details["review_status"])

    def test_approved_review_is_invalid_after_audited_commit_changes(self) -> None:
        self.approve_current_content()
        previous = self.document["audited_target_commit"]
        replacement = "0" * 40
        self.document["audited_target_commit"] = replacement
        record_path = self.root / EXPECTED_RECORD_PATH
        record = record_path.read_text(encoding="utf-8").replace(
            f"audited_target_commit: {previous}",
            f"audited_target_commit: {replacement}",
            1,
        )
        record_path.write_text(record, encoding="utf-8")
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("bound to a different audited_target_commit" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_review_is_invalid_after_manifest_target_changes(self) -> None:
        self.approve_current_content()
        self.document["targets"][0]["transformations"].append("post-review edit")
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_review_is_invalid_after_markdown_table_changes(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8")
            .replace(
                "Merged/reorganized relevant IDE, Gradle, Forge-run, Python, log, "
                "and crash-output exclusions",
                "Post-review table change",
                1,
            ),
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_review_is_invalid_after_target_hash_changes(self) -> None:
        self.approve_current_content()
        target = self.find_target("build.gradle")
        old_hash = target["current_target_sha256"]
        content = (self.root / "build.gradle").read_bytes() + b"post-review\n"
        new_hash = self.digest(content)
        (self.root / "build.gradle").write_bytes(content)
        target["current_target_sha256"] = new_hash
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                str(old_hash), new_hash, 1
            ),
            encoding="utf-8",
        )
        self.write_manifest()

        errors, _ = self.validate()

        self.assertFalse(
            any("SHA-256 mismatch for imported target build.gradle" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_review_requires_date_and_target_transition(self) -> None:
        self.document["review"] = {
            "record_status": APPROVED_RECORD_STATUS,
            "reviewer": "license-reviewer",
            "reviewed_at": None,
            "final_status_after_review": APPROVED_RECORD_STATUS,
            "reviewed_audited_target_commit": self.document[
                "audited_target_commit"
            ],
            "reviewed_content_sha256": "0" * 64,
        }
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("approved review requires a valid ISO reviewed_at date" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("status is inconsistent with review state" in error for error in errors),
            errors,
        )


if __name__ == "__main__":
    unittest.main()
