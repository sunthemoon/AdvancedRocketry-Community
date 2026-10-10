"""HistoryCases for the bootstrap provenance facade."""
from pathlib import Path
from scripts.validate_bootstrap_provenance import EXPECTED_RECORD_PATH


class HistoryCases:
    def test_nonexistent_audited_commit_is_rejected(self) -> None:
        missing_commit = "0" * 40
        previous = self.document["audited_target_commit"]
        self.document["audited_target_commit"] = missing_commit
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                f"audited_target_commit: {previous}",
                f"audited_target_commit: {missing_commit}",
                1,
            ),
            encoding="utf-8",
        )
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("audited_target_commit does not exist" in error for error in errors),
            errors,
        )

    def test_unrelated_audited_commit_is_rejected(self) -> None:
        tree = self.git("rev-parse", "HEAD^{tree}")
        unrelated = self.git("commit-tree", tree, "-m", "unrelated audit")
        previous = self.document["audited_target_commit"]
        self.document["audited_target_commit"] = unrelated
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                f"audited_target_commit: {previous}",
                f"audited_target_commit: {unrelated}",
                1,
            ),
            encoding="utf-8",
        )
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("import_commit -> audited_target_commit" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("audited_target_commit -> HEAD" in error for error in errors),
            errors,
        )

    def test_shallow_repository_is_rejected_before_history_validation(self) -> None:
        git_directory = Path(self.git("rev-parse", "--git-dir"))
        if not git_directory.is_absolute():
            git_directory = self.root / git_directory
        (git_directory / "shallow").write_text(
            self.scope_commit + "\n", encoding="ascii"
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("requires a complete, non-shallow Git history" in error for error in errors),
            errors,
        )

    def test_import_commit_without_declared_target_content_is_rejected(self) -> None:
        previous = self.document["import_commit"]
        self.document["import_commit"] = self.pre_import_commit
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                f"import_commit: {previous}",
                f"import_commit: {self.pre_import_commit}",
                1,
            ),
            encoding="utf-8",
        )
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "imported target build.gradle import snapshot is missing"
                in error
                for error in errors
            ),
            errors,
        )

    def test_introduced_commit_without_local_asset_is_rejected(self) -> None:
        asset = self.find_asset("src/main/resources/advancedrocketrycommunity.png")
        asset["introduced_commit"] = self.pre_import_commit
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "local asset src/main/resources/advancedrocketrycommunity.png "
                "introduction snapshot is missing"
                in error
                for error in errors
            ),
            errors,
        )

    def test_import_commit_must_change_each_target_from_a_parent(self) -> None:
        self.git("commit", "--allow-empty", "--quiet", "-m", "late declaration")
        late_commit = self.git("rev-parse", "HEAD")
        for target in self.document["targets"]:
            for suffix in (
                "git_mode",
                "git_object_type",
                "git_blob_oid",
                "raw_blob_sha256",
            ):
                target[f"import_target_{suffix}"] = target[
                    f"audited_target_{suffix}"
                ]
        self.set_pending_commit_scope(late_commit, late_commit)

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "import_commit for imported target build.gradle" in error
                and "does not add or change" in error
                for error in errors
            ),
            errors,
        )

    def test_introduced_commit_must_change_local_asset_from_a_parent(self) -> None:
        asset_path = "src/main/resources/advancedrocketrycommunity.png"
        self.find_asset(asset_path)["introduced_commit"] = self.scope_commit
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                f"introduced_commit for local asset {asset_path}" in error
                and "unchanged in parent" in error
                for error in errors
            ),
            errors,
        )

    def test_root_import_and_asset_introduction_are_supported(self) -> None:
        import_tree = self.git("rev-parse", f"{self.import_commit}^{{tree}}")
        root_import = self.git_with_input(
            b"root import\n", "commit-tree", import_tree
        )
        audited_tree = self.git("rev-parse", f"{self.scope_commit}^{{tree}}")
        audited_commit = self.git_with_input(
            b"root-line audit\n", "commit-tree", audited_tree, "-p", root_import
        )
        self.git("update-ref", "HEAD", audited_commit)
        for asset in self.document["local_assets"]:
            asset["introduced_commit"] = root_import
        self.set_pending_commit_scope(root_import, audited_commit)

        errors, _ = self.validate()

        self.assertEqual([], errors)

    def test_merge_import_cannot_use_an_older_second_parent_as_change_proof(
        self,
    ) -> None:
        import_tree = self.git("rev-parse", f"{self.import_commit}^{{tree}}")
        merge_import = self.git_with_input(
            b"merge import\n",
            "commit-tree",
            import_tree,
            "-p",
            self.import_commit,
            "-p",
            self.pre_import_commit,
        )
        audited_tree = self.git("rev-parse", f"{self.scope_commit}^{{tree}}")
        audited_commit = self.git_with_input(
            b"merge audit\n", "commit-tree", audited_tree, "-p", merge_import
        )
        self.git("update-ref", "HEAD", audited_commit)
        for asset in self.document["local_assets"]:
            asset["introduced_commit"] = merge_import
        self.set_pending_commit_scope(merge_import, audited_commit)

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "import_commit for imported target build.gradle" in error
                and "first parent" in error
                for error in errors
            ),
            errors,
        )
        self.assertTrue(
            any(
                "introduced_commit for local asset "
                "src/main/resources/advancedrocketrycommunity.png" in error
                and "unchanged in parent" in error
                for error in errors
            ),
            errors,
        )

    def test_merge_import_may_match_the_import_branch_but_changes_first_parent(
        self,
    ) -> None:
        import_tree = self.git("rev-parse", f"{self.import_commit}^{{tree}}")
        merge_import = self.git_with_input(
            b"merge import\n",
            "commit-tree",
            import_tree,
            "-p",
            self.pre_import_commit,
            "-p",
            self.import_commit,
        )
        audited_tree = self.git("rev-parse", f"{self.scope_commit}^{{tree}}")
        audited_commit = self.git_with_input(
            b"merge audit\n", "commit-tree", audited_tree, "-p", merge_import
        )
        self.git("update-ref", "HEAD", audited_commit)
        self.set_pending_commit_scope(merge_import, audited_commit)

        errors, _ = self.validate()

        self.assertEqual([], errors)
