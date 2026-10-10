"""GitObjectCases for the bootstrap provenance facade."""
import subprocess
import zlib
from pathlib import Path
from unittest.mock import patch
import scripts.validate_bootstrap_provenance as validator_module
from scripts.validate_bootstrap_provenance import validate_bootstrap_provenance_at_commit


class GitObjectCases:
    def test_parent_parser_uses_git_revision_parent_semantics(self) -> None:
        tree = self.git("rev-parse", f"{self.scope_commit}^{{tree}}")
        malformed = f"""tree {tree}
author Provenance Test <provenance-test@example.invalid> 1 +0000
parent {self.import_commit}
committer Provenance Test <provenance-test@example.invalid> 1 +0000

late parent header must not create ancestry
""".encode("ascii")
        synthetic = self.git_with_input(
            malformed,
            "hash-object",
            "--literally",
            "-t",
            "commit",
            "-w",
            "--stdin",
        )
        errors: list[str] = []

        parents = validator_module._git_commit_parents(
            self.root, synthetic, "synthetic malformed commit", errors
        )

        self.assertEqual([], errors)
        self.assertEqual([], parents)

    def test_exact_tree_lookup_rejects_duplicate_malformed_entries(self) -> None:
        blob = self.git("rev-parse", f"{self.scope_commit}:build.gradle")
        tree_content = (
            b"100644 build.gradle\0"
            + bytes.fromhex(blob)
            + b"100644 build.gradle\0"
            + bytes.fromhex(blob)
        )
        tree = self.git_with_input(
            tree_content,
            "hash-object",
            "--literally",
            "-t",
            "tree",
            "-w",
            "--stdin",
        )
        commit = self.git_with_input(
            b"duplicate exact tree path\n",
            "commit-tree",
            tree,
            "-p",
            self.scope_commit,
        )
        errors: list[str] = []

        valid, entry = validator_module._git_tree_entry(
            self.root,
            commit,
            "build.gradle",
            "duplicate exact tree lookup",
            errors,
        )

        self.assertFalse(valid)
        self.assertIsNone(entry)
        self.assertTrue(any("expected exactly one tree entry" in error for error in errors), errors)

    def test_selected_commit_validation_rejects_legacy_git_grafts(self) -> None:
        selected = self.commit_current_fixture("selected history before graft")
        grafts = self.root / ".git/info/grafts"
        grafts.parent.mkdir(parents=True, exist_ok=True)
        grafts.write_text(f"{selected}\n", encoding="ascii")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("forbids legacy Git info/grafts metadata" in error for error in errors),
            errors,
        )

    def test_unsafe_generator_path_never_reaches_git_history_commands(self) -> None:
        generated = next(
            asset
            for asset in self.document["local_assets"]
            if asset["status"] == "GENERATED"
        )
        generated["generator_path"] = "../outside-generator"
        self.write_manifest()
        selected = self.commit_current_fixture("unsafe selected generator path")

        with patch.object(
            validator_module,
            "_git_tree_entry",
            wraps=validator_module._git_tree_entry,
        ) as tree_entry:
            errors, _ = self.validate_selected(selected)

        self.assertTrue(any("generator is an unsafe path" in error for error in errors), errors)
        self.assertNotIn(
            "../outside-generator",
            [call.args[2] for call in tree_entry.call_args_list],
        )

    def test_provenance_paths_reject_windows_unsafe_names(self) -> None:
        for value in (
            "CON/generator.py",
            "CONIN$/generator.py",
            "CONOUT$.txt",
            "COM¹/generator.py",
            "LPT³.txt",
            ".Git/config",
            "a" * 256,
            "tools/bad-name./generator.py",
            "tools/bad:name/generator.py",
        ):
            with self.subTest(value=value):
                self.assertIsNotNone(validator_module.relative_path_error(value))

    def test_verified_git_object_rejects_oid_and_declared_size_forgery(self) -> None:
        oid = self.git("rev-parse", f"{self.scope_commit}:build.gradle")
        objects = Path(self.git("rev-parse", "--git-path", "objects"))
        if not objects.is_absolute():
            objects = self.root / objects
        loose = objects / oid[:2] / oid[2:]
        loose.parent.mkdir(parents=True, exist_ok=True)
        if loose.exists():
            loose.chmod(0o600)

        loose.write_bytes(zlib.compress(b"blob 4\0BBBB"))
        errors: list[str] = []
        content = validator_module._read_verified_git_object(
            self.root, oid, "blob", 1024, "corrupt blob", errors
        )
        self.assertIsNone(content)
        self.assertTrue(any("Git object identity mismatch" in error for error in errors), errors)

        loose.write_bytes(zlib.compress(b"blob 1\0" + b"A" * (1024 * 1024)))
        errors = []
        content = validator_module._read_verified_git_object(
            self.root, oid, "blob", 1024, "size-forged blob", errors
        )
        self.assertIsNone(content)
        self.assertTrue(any("undeclared bytes" in error for error in errors), errors)

    def test_ancestry_walk_has_a_hard_commit_bound(self) -> None:
        errors: list[str] = []
        with patch.object(validator_module, "MAX_GIT_ANCESTRY_COMMITS", 1):
            validator_module._validate_git_ancestor(
                self.root,
                self.import_commit,
                self.scope_commit,
                "bounded test ancestry",
                errors,
            )

        self.assertEqual(
            ["cannot verify bounded test ancestry: ancestry traversal exceeds 1 commits"],
            errors,
        )

        errors = []
        with patch.object(validator_module, "MAX_GIT_ANCESTRY_COMMITS", 1):
            validator_module._validate_git_ancestor(
                self.root,
                self.scope_commit,
                self.scope_commit,
                "reflexive ancestry",
                errors,
            )
        self.assertEqual([], errors)

    def test_worktree_resource_inventory_has_a_hard_file_bound(self) -> None:
        errors: list[str] = []
        with patch.object(validator_module, "MAX_SELECTED_RESOURCE_FILES", 1):
            validator_module._repository_resource_files(self.root, errors)

        self.assertTrue(
            any("worktree resource inventory exceeds 1 files" in error for error in errors),
            errors,
        )

    def test_markdown_yaml_cardinality_is_bounded(self) -> None:
        text = "```yaml\nstatus: one\n```\n```yaml\nstatus: two\n```\n"
        errors: list[str] = []
        with patch.object(validator_module, "MAX_MARKDOWN_YAML_FENCES", 1):
            validator_module._validate_record_yaml_structure(text, errors)

        self.assertEqual(
            ["provenance Markdown exceeds 1 YAML metadata blocks"], errors
        )

    def test_scoped_validation_reuses_transport_but_executes_every_object_check(self) -> None:
        launches = []
        original = subprocess.Popen

        def observe(*arguments, **keywords):
            if arguments[0][-2:] == ["cat-file", "--batch"]:
                launches.append(arguments[0])
            return original(*arguments, **keywords)

        with patch.object(validator_module.subprocess, "Popen", side_effect=observe), patch.object(
            validator_module, "_read_verified_git_object", wraps=validator_module._read_verified_git_object
        ) as read:
            errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(11, details["targets"])
        self.assertGreater(read.call_count, 100)
        self.assertLess(len(launches), read.call_count)
        self.assertIsNone(validator_module._git_object_scope.session)

    def test_public_result_contains_transport_failure_discovered_after_checks(self) -> None:
        original_close = validator_module._GitObjectSession.close

        def inject_terminal_failure(session):
            original_close(session)
            session._fail("injected terminal transport failure")

        with patch.object(validator_module._GitObjectSession, "close", inject_terminal_failure):
            errors, details = self.validate()

        self.assertIn("injected terminal transport failure", errors)
        self.assertEqual(11, details["targets"])
        self.assertIsNone(validator_module._git_object_scope.session)

    def test_selected_public_validation_restores_transport_after_early_failure(self) -> None:
        errors, _ = validate_bootstrap_provenance_at_commit(self.root, self.scope_commit)
        self.assertTrue(errors)
        self.assertIsNone(validator_module._git_object_scope.session)
        self.write_manifest()
        selected = self.commit_current_fixture("scoped valid selection after failure")
        errors, _ = self.validate_selected(selected)
        self.assertEqual([], errors)
        self.assertIsNone(validator_module._git_object_scope.session)
