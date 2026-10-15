"""TreeMetadataCases for the bootstrap provenance facade."""


class TreeMetadataCases:
    def test_target_requires_all_git_tree_metadata_fields(self) -> None:
        target = self.find_target("build.gradle")
        for field in (
            "import_target_git_mode",
            "import_target_git_object_type",
            "import_target_git_blob_oid",
            "import_target_raw_blob_sha256",
            "audited_target_git_mode",
            "audited_target_git_object_type",
            "audited_target_git_blob_oid",
            "audited_target_raw_blob_sha256",
        ):
            with self.subTest(field=field):
                original = target.pop(field)
                self.write_manifest()
                errors, _ = self.validate()
                self.assertTrue(any(field in error for error in errors), errors)
                target[field] = original

    def test_local_asset_requires_all_git_tree_metadata_fields(self) -> None:
        asset = self.find_asset("src/main/resources/advancedrocketrycommunity.png")
        for field in (
            "introduced_git_mode",
            "introduced_git_object_type",
            "introduced_git_blob_oid",
            "introduced_raw_blob_sha256",
            "audited_git_mode",
            "audited_git_object_type",
            "audited_git_blob_oid",
            "audited_raw_blob_sha256",
        ):
            with self.subTest(field=field):
                original = asset.pop(field)
                self.write_manifest()
                errors, _ = self.validate()
                self.assertTrue(any(field in error for error in errors), errors)
                asset[field] = original

    def test_declared_tree_object_type_is_rejected(self) -> None:
        target = self.find_target("build.gradle")
        target["audited_target_git_object_type"] = "tree"
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "audited_target_git_object_type must be blob" in error
                for error in errors
            ),
            errors,
        )

    def test_audited_git_mode_drift_is_rejected(self) -> None:
        self.git("update-index", "--chmod=-x", "--", "gradlew")
        self.git("commit", "--quiet", "-m", "remove wrapper executable mode")
        drift_commit = self.git("rev-parse", "HEAD")
        self.set_pending_commit_scope(audited_commit=drift_commit)

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "Git mode mismatch for imported target gradlew audited snapshot"
                in error
                for error in errors
            ),
            errors,
        )

    def test_post_audit_target_mode_drift_at_head_is_rejected(self) -> None:
        self.git("update-index", "--chmod=+x", "--", "build.gradle")
        self.git("commit", "--quiet", "-m", "post-audit target mode drift")

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "Git mode mismatch for imported target build.gradle HEAD snapshot"
                in error
                for error in errors
            ),
            errors,
        )

    def test_post_audit_local_asset_mode_drift_at_head_is_rejected(self) -> None:
        asset_path = "src/main/resources/advancedrocketrycommunity.png"
        self.git("update-index", "--chmod=+x", "--", asset_path)
        self.git("commit", "--quiet", "-m", "post-audit asset mode drift")

        errors, _ = self.validate()

        self.assertTrue(
            any(
                f"Git mode mismatch for local asset {asset_path} HEAD snapshot"
                in error
                for error in errors
            ),
            errors,
        )

    def test_materialized_hash_cannot_substitute_for_raw_git_blob_hash(self) -> None:
        target = self.find_target("gradlew.bat")
        materialized_hash = target["worktree_materialized_sha256"]
        self.assertNotEqual(
            materialized_hash, target["audited_target_raw_blob_sha256"]
        )
        target["audited_target_raw_blob_sha256"] = materialized_hash
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "raw Git blob SHA-256 mismatch for imported target gradlew.bat "
                "audited snapshot" in error
                for error in errors
            ),
            errors,
        )

    def test_redundant_worktree_materialized_hash_is_rejected(self) -> None:
        target = self.find_target("build.gradle")
        target["worktree_materialized_sha256"] = target[
            "audited_target_raw_blob_sha256"
        ]
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "worktree_materialized_sha256 is unnecessary" in error
                for error in errors
            ),
            errors,
        )

    def test_audited_symlink_tree_entry_is_rejected(self) -> None:
        link_object = self.git_with_input(
            b"elsewhere.gradle\n", "hash-object", "-w", "--stdin"
        )
        self.git(
            "update-index",
            "--add",
            "--cacheinfo",
            f"120000,{link_object},build.gradle",
        )
        self.git("commit", "--quiet", "-m", "replace target with symlink")
        symlink_commit = self.git("rev-parse", "HEAD")
        self.set_pending_commit_scope(audited_commit=symlink_commit)

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "imported target build.gradle audited snapshot must be a regular "
                "Git blob" in error
                and "mode=120000" in error
                for error in errors
            ),
            errors,
        )

    def test_audited_tree_entry_is_rejected(self) -> None:
        empty_tree = self.git_with_input(b"", "mktree")
        tree_commit = self.commit_with_root_tree_entry(
            self.scope_commit,
            "build.gradle",
            "040000",
            "tree",
            empty_tree,
        )
        self.set_pending_commit_scope(audited_commit=tree_commit)

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "imported target build.gradle audited snapshot must be a regular "
                "Git blob" in error
                and "type=tree" in error
                for error in errors
            ),
            errors,
        )
