"""ManifestResourceCases for the bootstrap provenance facade."""
import copy
import os


class ManifestResourceCases:
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
        extra["audited_target_raw_blob_sha256"] = self.digest(content)
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

        self.assertTrue(
            any("unsafe path" in error or "is unsafe:" in error for error in errors),
            errors,
        )
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
