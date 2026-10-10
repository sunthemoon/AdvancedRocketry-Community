"""SelectedCommitCases for the bootstrap provenance facade."""
from scripts.validate_bootstrap_provenance import EXPECTED_NOTICE_PATH, EXPECTED_RECORD_PATH, PENDING_RECORD_STATUS


class SelectedCommitCases:
    def test_happy_pending_path_validates_all_required_entries(self) -> None:
        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(2, details["components"])
        self.assertEqual(11, details["targets"])
        self.assertEqual(2, details["local_assets"])
        self.assertEqual(PENDING_RECORD_STATUS, details["review_status"])
        self.assertRegex(details["review_content_sha256"], r"^[0-9a-f]{64}$")

    def test_forge_source_tree_materialization_evidence_is_exact(self) -> None:
        materialization = self.document["components"][0][
            "source_tree_materializations"
        ][0]
        materialization["source_raw_sha256"] = "0" * 64
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "component forge_mdk source_tree_materializations must be"
                in error
                for error in errors
            ),
            errors,
        )

    def test_selected_commit_validation_ignores_dirty_worktree_inputs(self) -> None:
        selected = self.commit_current_fixture()
        self.document["components"][0]["source_sha256"] = "0" * 64
        self.write_manifest()
        (self.root / EXPECTED_NOTICE_PATH).write_text(
            "dirty mutable notice\n", encoding="utf-8"
        )

        errors, details = self.validate_selected(selected)

        self.assertEqual([], errors)
        self.assertEqual(PENDING_RECORD_STATUS, details["review_status"])
        self.assertEqual(11, details["targets"])

    def test_selected_commit_validation_rejects_nonexistent_history(self) -> None:
        missing_commit = "0" * 40
        self.set_pending_commit_scope(import_commit=missing_commit)
        selected = self.commit_current_fixture("invalid selected history")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("import_commit does not exist as a local Git commit" in error for error in errors),
            errors,
        )

    def test_selected_commit_validation_rejects_wrong_component_source_hash(self) -> None:
        self.document["components"][0]["source_sha256"] = "0" * 64
        self.write_manifest()
        selected = self.commit_current_fixture("invalid selected source hash")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("component forge_mdk source_sha256 must be" in error for error in errors),
            errors,
        )

    def test_selected_commit_validation_rejects_wrong_target_source_evidence(self) -> None:
        target = self.find_target("build.gradle")
        target["source_path"] = "fabricated/safe/path"
        target["source_sha256"] = "0" * 64
        self.write_manifest()
        selected = self.commit_current_fixture("invalid selected target source")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("build.gradle source_path must be build.gradle" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("build.gradle source_sha256 must be" in error for error in errors),
            errors,
        )

    def test_selected_commit_materialization_ignores_ambient_info_attributes(self) -> None:
        selected = self.commit_current_fixture("valid selected attribute policy")
        info_attributes = self.root / ".git/info/attributes"
        info_attributes.parent.mkdir(parents=True, exist_ok=True)
        info_attributes.write_text(
            "gradlew.bat -text eol=lf\n", encoding="utf-8"
        )

        errors, _ = self.validate_selected(selected)

        self.assertEqual([], errors)

    def test_selected_commit_materialization_rejects_unknown_matched_attribute(
        self,
    ) -> None:
        attributes = self.root / ".gitattributes"
        attributes.write_text(
            attributes.read_text(encoding="utf-8")
            + "\ngradlew.bat filter=untrusted-materializer\n",
            encoding="utf-8",
            newline="\n",
        )
        selected = self.commit_current_fixture("unsupported selected attribute")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("unsupported matched attribute" in error for error in errors),
            errors,
        )

    def test_declared_commit_ids_must_be_exact_commit_objects(self) -> None:
        self.git(
            "tag",
            "-a",
            "annotated-import-alias",
            "-m",
            "annotated import alias",
            self.import_commit,
        )
        tag_object = self.git("rev-parse", "annotated-import-alias^{tag}")
        previous = self.document["import_commit"]
        self.document["import_commit"] = tag_object
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                f"import_commit: {previous}",
                f"import_commit: {tag_object}",
                1,
            ),
            encoding="utf-8",
            newline="\n",
        )
        self.write_manifest()
        selected = self.commit_current_fixture("annotated tag is not a commit identity")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any(
                "import_commit does not exist as a local Git commit" in error
                and "exact object type is 'tag'" in error
                for error in errors
            ),
            errors,
        )
