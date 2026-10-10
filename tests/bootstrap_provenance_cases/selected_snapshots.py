"""SelectedSnapshotCases for the bootstrap provenance facade."""


class SelectedSnapshotCases:
    def test_selected_commit_tip_is_explicit_and_rejects_later_target_drift(self) -> None:
        valid_selected = self.commit_current_fixture("valid selected tip")
        (self.root / "build.gradle").write_bytes(b"selected target drift\n")
        self.git("add", "build.gradle")
        self.git("commit", "--quiet", "-m", "drift after selected tip")
        drifted_head = self.git("rev-parse", "HEAD")

        valid_errors, _ = self.validate_selected(valid_selected)
        drift_errors, _ = self.validate_selected(drifted_head)

        self.assertEqual([], valid_errors)
        self.assertTrue(
            any("selected commit snapshot" in error for error in drift_errors),
            drift_errors,
        )

    def test_selected_commit_validation_rejects_unlisted_resource(self) -> None:
        extra = self.root / "src/main/resources/unlisted-selected.txt"
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_text("unlisted\n", encoding="utf-8")
        selected = self.commit_current_fixture("unlisted selected resource")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("resource files missing provenance entries" in error for error in errors),
            errors,
        )

    def test_selected_commit_preserves_raw_and_materialized_hash_distinction(self) -> None:
        target = self.find_target("gradlew.bat")
        materialized = target.get("worktree_materialized_sha256")
        self.assertIsInstance(materialized, str)
        target["audited_target_raw_blob_sha256"] = materialized
        self.write_manifest()
        selected = self.commit_current_fixture("invalid raw selected hash")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("raw Git blob SHA-256 mismatch" in error for error in errors),
            errors,
        )
