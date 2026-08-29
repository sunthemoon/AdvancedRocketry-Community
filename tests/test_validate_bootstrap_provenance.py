import copy
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

import scripts.validate_bootstrap_provenance as validator_module
from scripts.validate_bootstrap_provenance import (
    APPROVED_RECORD_STATUS,
    DEFAULT_MANIFEST,
    EXPECTED_NOTICE_PATH,
    EXPECTED_RECORD_PATH,
    PENDING_RECORD_STATUS,
    REVIEW_DIGEST_DOMAIN,
    compute_review_content_sha256,
    validate_bootstrap_provenance,
    validate_bootstrap_provenance_at_commit,
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

        required_paths: set[str] = set()
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

        original_import = self.document["import_commit"]
        target_paths = [target["path"] for target in self.document["targets"]]
        current_target_bytes = {
            path: (self.root / path).read_bytes() for path in target_paths
        }
        archive = subprocess.check_output(
            [
                "git",
                "-C",
                str(self.source_root),
                "archive",
                "--format=tar",
                original_import,
                "--",
                *target_paths,
            ]
        )
        with tarfile.open(fileobj=io.BytesIO(archive)) as import_tree:
            for path in target_paths:
                member = import_tree.extractfile(path)
                assert member is not None
                (self.root / path).write_bytes(member.read())

        self.git("init", "--quiet")
        self.git("config", "user.name", "Provenance Test")
        self.git("config", "user.email", "provenance-test@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.git("config", "core.filemode", "true")
        self.git("commit", "--allow-empty", "--quiet", "-m", "pre-import")
        self.pre_import_commit = self.git("rev-parse", "HEAD")
        self.git("add", "--all")
        self.git("update-index", "--chmod=-x", "--", "gradlew")
        self.git("commit", "--quiet", "-m", "import bootstrap inputs")
        self.import_commit = self.git("rev-parse", "HEAD")

        for path in target_paths:
            (self.root / path).write_bytes(current_target_bytes[path])
        self.git("add", "--all")
        self.git("update-index", "--chmod=+x", "--", "gradlew")
        self.git("commit", "--quiet", "-m", "audit current bootstrap inputs")
        self.scope_commit = self.git("rev-parse", "HEAD")

        self.document["import_commit"] = self.import_commit
        self.document["audited_target_commit"] = self.scope_commit
        for asset in self.document["local_assets"]:
            asset["introduced_commit"] = self.import_commit
            self.set_asset_snapshot_metadata(
                asset, "introduced", self.import_commit
            )
            self.set_asset_snapshot_metadata(asset, "audited", self.scope_commit)
        for target in self.document["targets"]:
            path = target["path"]
            target.pop("import_target_sha256", None)
            target.pop("current_target_sha256", None)
            target.pop("worktree_materialized_sha256", None)
            self.set_target_snapshot_metadata(target, "import", self.import_commit)
            self.set_target_snapshot_metadata(target, "audited", self.scope_commit)
            audited_raw_hash = target["audited_target_raw_blob_sha256"]
            worktree_hash = self.digest(current_target_bytes[path])
            if worktree_hash != audited_raw_hash:
                target["worktree_materialized_sha256"] = worktree_hash

        self.reset_to_pending_review()
        self.write_review_documents(approved=False)
        self.write_manifest()

    @staticmethod
    def digest(content: bytes) -> str:
        return hashlib.sha256(content).hexdigest()

    def git(self, *arguments: str) -> str:
        result = subprocess.run(
            ["git", "-C", str(self.root), *arguments],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        return result.stdout.strip()

    def git_with_input(self, content: bytes, *arguments: str) -> str:
        result = subprocess.run(
            ["git", "-C", str(self.root), *arguments],
            check=True,
            input=content,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        return result.stdout.decode("ascii", errors="strict").strip()

    def tree_entry(self, commit: str, path: str) -> tuple[str, str, str]:
        output = self.git("ls-tree", commit, "--", path)
        metadata, observed_path = output.split("\t", 1)
        self.assertEqual(path, observed_path)
        mode, object_type, object_id = metadata.split(" ", 2)
        return mode, object_type, object_id

    def git_blob_sha256(self, commit: str, path: str) -> str:
        content = subprocess.check_output(
            ["git", "-C", str(self.root), "cat-file", "blob", f"{commit}:{path}"]
        )
        return self.digest(content)

    def set_target_snapshot_metadata(
        self,
        target: dict[str, object],
        snapshot: str,
        commit: str,
    ) -> None:
        path = str(target["path"])
        mode, object_type, object_id = self.tree_entry(commit, path)
        target[f"{snapshot}_target_git_mode"] = mode
        target[f"{snapshot}_target_git_object_type"] = object_type
        target[f"{snapshot}_target_git_blob_oid"] = object_id
        target[f"{snapshot}_target_raw_blob_sha256"] = self.git_blob_sha256(
            commit, path
        )

    def set_asset_snapshot_metadata(
        self,
        asset: dict[str, object],
        snapshot: str,
        commit: str,
    ) -> None:
        path = str(asset["path"])
        mode, object_type, object_id = self.tree_entry(commit, path)
        asset[f"{snapshot}_git_mode"] = mode
        asset[f"{snapshot}_git_object_type"] = object_type
        asset[f"{snapshot}_git_blob_oid"] = object_id
        asset[f"{snapshot}_raw_blob_sha256"] = self.git_blob_sha256(commit, path)

    def commit_with_root_tree_entry(
        self,
        parent: str,
        path: str,
        mode: str,
        object_type: str,
        object_id: str,
    ) -> str:
        self.assertNotIn("/", path)
        lines = self.git("ls-tree", parent).splitlines()
        replacement = f"{mode} {object_type} {object_id}\t{path}"
        replaced = False
        for index, line in enumerate(lines):
            if line.endswith(f"\t{path}"):
                lines[index] = replacement
                replaced = True
                break
        self.assertTrue(replaced, path)
        tree = self.git_with_input(
            ("\n".join(lines) + "\n").encode("utf-8"), "mktree"
        )
        commit = self.git_with_input(
            b"synthetic tree entry\n", "commit-tree", tree, "-p", parent
        )
        self.git("update-ref", "HEAD", commit)
        return commit

    @staticmethod
    def read_utf8(path: Path) -> str:
        return path.read_bytes().decode("utf-8", errors="strict")

    @staticmethod
    def write_utf8(path: Path, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content.encode("utf-8"))

    def write_manifest(self) -> None:
        self.manifest.parent.mkdir(parents=True, exist_ok=True)
        self.manifest.write_text(
            json.dumps(self.document, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def validate(self) -> tuple[list[str], dict[str, int | str]]:
        return validate_bootstrap_provenance(repository_root=self.root)

    def run_cli(self, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(
                    self.source_root
                    / "scripts"
                    / "validate_bootstrap_provenance.py"
                ),
                "--repository-root",
                str(self.root),
                *arguments,
            ],
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            encoding="utf-8",
            timeout=120,
        )

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

    def reset_to_pending_review(self) -> None:
        self.document["review"] = {
            "record_status": PENDING_RECORD_STATUS,
            "reviewer": None,
            "reviewed_at": None,
            "final_status_after_review": None,
            "reviewed_audited_target_commit": None,
            "reviewed_content_sha256": None,
        }
        for target in self.document["targets"]:
            target["status"] = "PENDING_HUMAN_REVIEW"
            target["proposed_status_after_review"] = APPROVED_RECORD_STATUS

    def render_record(self, approved: bool, digest: str | None = None) -> bytes:
        reviewer = "license-reviewer" if approved else "null"
        reviewed_at = "2026-08-27" if approved else "null"
        record_status = APPROVED_RECORD_STATUS if approved else PENDING_RECORD_STATUS
        final_status = APPROVED_RECORD_STATUS if approved else "null"
        reviewed_commit = self.document["audited_target_commit"] if approved else "null"
        target_status = APPROVED_RECORD_STATUS if approved else "PENDING_HUMAN_REVIEW"
        proposed_status = "null" if approved else APPROVED_RECORD_STATUS
        reviewed_digest = digest if digest is not None else "null"
        checklist = "x" if approved else " "
        decision = "complete" if approved else "awaiting human review"
        return f"""# Synthetic bootstrap provenance record

```yaml
record_version: {self.document['schema_version']}
scope_version: {self.document['scope_version']}
import_commit: {self.document['import_commit']}
audited_target_commit: {self.document['audited_target_commit']}
record_status: {record_status}
reviewer: {reviewer}
reviewed_at: {reviewed_at}
final_status_after_review: {final_status}
reviewed_audited_target_commit: {reviewed_commit}
reviewed_content_sha256: {reviewed_digest}
```

The provenance decision is {decision}.

| Scope | Result |
| --- | --- |
| imported targets | baseline |

## Forge target review fields

```yaml
status: {target_status}
proposed_status_after_review: {proposed_status}
reviewer: {reviewer}
reviewed_at: {reviewed_at}
```

## Gradle target review fields

```yaml
status: {target_status}
proposed_status_after_review: {proposed_status}
reviewer: {reviewer}
reviewed_at: {reviewed_at}
```

- [{checklist}] Human reviewer confirms the provenance decision.
""".encode("utf-8")

    @staticmethod
    def render_notice(approved: bool) -> bytes:
        status = APPROVED_RECORD_STATUS if approved else "PENDING_HUMAN_REVIEW"
        reviewer = "license-reviewer" if approved else "null"
        reviewed_at = "2026-08-27" if approved else "null"
        decision = "complete" if approved else "awaiting human review"
        return f"""# Synthetic third-party notices

```yaml
status: {status}
reviewer: {reviewer}
reviewed_at: {reviewed_at}
```

The notice decision is {decision}.
""".encode("utf-8")

    def write_review_documents(
        self, approved: bool, digest: str | None = None
    ) -> tuple[bytes, bytes]:
        record = self.render_record(approved, digest)
        notice = self.render_notice(approved)
        record_path = self.root / EXPECTED_RECORD_PATH
        notice_path = self.root / EXPECTED_NOTICE_PATH
        record_path.parent.mkdir(parents=True, exist_ok=True)
        record_path.write_bytes(record)
        notice_path.write_bytes(notice)
        return record, notice

    def set_pending_commit_scope(
        self,
        import_commit: str | None = None,
        audited_commit: str | None = None,
    ) -> None:
        if import_commit is not None:
            self.document["import_commit"] = import_commit
        if audited_commit is not None:
            self.document["audited_target_commit"] = audited_commit
        self.write_review_documents(approved=False)
        self.write_manifest()

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

        record, notice = self.write_review_documents(approved=True)
        digest = compute_review_content_sha256(
            self.document,
            record,
            notice,
        )
        self.document["review"]["reviewed_content_sha256"] = digest
        self.write_review_documents(approved=True, digest=digest)
        self.write_manifest()
        return digest

    def commit_current_fixture(self, message: str = "selected validation tip") -> str:
        self.git("add", "--all")
        self.git("update-index", "--chmod=+x", "--", "gradlew")
        self.git("commit", "--quiet", "-m", message)
        return self.git("rev-parse", "HEAD")

    def validate_selected(
        self, selected_commit: str
    ) -> tuple[list[str], dict[str, int | str]]:
        return validate_bootstrap_provenance_at_commit(
            repository_root=self.root,
            selected_commit=selected_commit,
        )

    def test_happy_pending_path_validates_all_required_entries(self) -> None:
        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(2, details["components"])
        self.assertEqual(11, details["targets"])
        self.assertEqual(2, details["local_assets"])
        self.assertEqual(PENDING_RECORD_STATUS, details["review_status"])
        self.assertRegex(details["review_content_sha256"], r"^[0-9a-f]{64}$")

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

    def test_selected_commit_validation_rejects_deep_json_without_traceback(self) -> None:
        nested: object = "leaf"
        for _ in range(80):
            nested = [nested]
        self.document["unexpected_deep_value"] = nested
        self.write_manifest()
        selected = self.commit_current_fixture("deep selected manifest")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(any("exceeds JSON depth" in error for error in errors), errors)

    def test_selected_commit_validation_rejects_nonfinite_json(self) -> None:
        self.document["unexpected_nonfinite_value"] = float("nan")
        self.write_manifest()
        selected = self.commit_current_fixture("nonfinite selected manifest")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(any("non-finite JSON number is forbidden" in error for error in errors), errors)

    def test_selected_commit_validation_rejects_overflowing_json_number(self) -> None:
        encoded = json.dumps(self.document, indent=2, sort_keys=True)
        self.manifest.write_text(
            encoded[:-1] + ',\n  "unexpected_overflow": 1e9999\n}\n',
            encoding="utf-8",
            newline="\n",
        )
        selected = self.commit_current_fixture("overflowing selected JSON number")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("contains a non-finite JSON number" in error for error in errors),
            errors,
        )

    def test_selected_commit_array_cardinality_is_strict_and_bounded(self) -> None:
        original = copy.deepcopy(self.document["targets"][0])
        self.document["targets"].extend(copy.deepcopy(original) for _ in range(1_000))
        self.write_manifest()
        selected = self.commit_current_fixture("oversized target inventory")

        errors, details = self.validate_selected(selected)

        self.assertTrue(
            any("targets must contain exactly 11 entries" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("duplicate imported target path" in error for error in errors),
            errors,
        )
        self.assertLess(len(errors), 20, errors)
        self.assertEqual(11, details["targets"])

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

    def test_cli_default_separates_mechanical_pass_from_human_pending(self) -> None:
        result = self.run_cli()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(
            "[PASS] Bootstrap provenance mechanical validation:", result.stdout
        )
        self.assertIn(
            f"[PENDING] Human provenance review: {PENDING_RECORD_STATUS}",
            result.stdout,
        )
        self.assertIn("mechanical validation does not approve G0", result.stdout)
        self.assertNotIn("reviewed_content_sha256:", result.stdout)
        self.assertNotIn("[PASS] Provenance review metadata", result.stdout)
        self.assertEqual("", result.stderr)

    def test_cli_diagnostic_pending_digest_is_explicitly_nonapproval(self) -> None:
        _, details = self.validate()

        result = self.run_cli("--diagnostic-pending-digest")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(
            "[DIAGNOSTIC] pending_review_content_sha256: "
            f"{details['review_content_sha256']}",
            result.stdout,
        )
        self.assertIn("pending content only", result.stdout)
        self.assertIn("must not be copied into approval metadata", result.stdout)
        self.assertIn("[PENDING] Human provenance review:", result.stdout)
        self.assertNotIn("approval_candidate_reviewed_content_sha256", result.stdout)

        self.approve_current_content()
        approved_result = self.run_cli("--diagnostic-pending-digest")

        self.assertEqual(1, approved_result.returncode, approved_result.stdout)
        self.assertIn(
            "--diagnostic-pending-digest requires a valid pending review",
            approved_result.stdout,
        )
        self.assertNotIn("pending_review_content_sha256:", approved_result.stdout)

    def test_cli_approval_candidate_mode_accepts_only_missing_final_digest(
        self,
    ) -> None:
        pending_result = self.run_cli("--prepare-approval-digest")
        self.assertEqual(1, pending_result.returncode, pending_result.stdout)
        self.assertIn(
            "requires an otherwise-valid THIRD_PARTY_APPROVED candidate",
            pending_result.stdout,
        )
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256", pending_result.stdout
        )

        self.approve_current_content()
        self.document["review"]["reviewed_content_sha256"] = None
        self.write_review_documents(approved=True)
        self.write_manifest()
        protected_paths = (
            self.manifest,
            self.root / EXPECTED_RECORD_PATH,
            self.root / EXPECTED_NOTICE_PATH,
        )
        before = {path: path.read_bytes() for path in protected_paths}

        candidate_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(
            0,
            candidate_result.returncode,
            candidate_result.stdout + candidate_result.stderr,
        )
        self.assertRegex(
            candidate_result.stdout,
            r"\[CANDIDATE\] approval_candidate_reviewed_content_sha256: "
            r"[0-9a-f]{64}",
        )
        self.assertIn("changes no files and records no approval", candidate_result.stdout)
        self.assertEqual(before, {path: path.read_bytes() for path in protected_paths})

        self.document["review"]["reviewed_at"] = "not-an-iso-date"
        self.write_manifest()
        invalid_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(1, invalid_result.returncode, invalid_result.stdout)
        self.assertIn(
            "approved review requires a valid ISO reviewed_at date",
            invalid_result.stdout,
        )
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256", invalid_result.stdout
        )

        self.document["review"]["reviewed_at"] = "2026-08-27"
        self.document["review"]["reviewed_content_sha256"] = "not-a-digest"
        self.write_review_documents(approved=True, digest="not-a-digest")
        self.write_manifest()
        malformed_digest_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(
            1, malformed_digest_result.returncode, malformed_digest_result.stdout
        )
        self.assertIn(
            "approved review reviewed_content_sha256 must be lowercase",
            malformed_digest_result.stdout,
        )
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256",
            malformed_digest_result.stdout,
        )

        self.approve_current_content()
        already_bound_result = self.run_cli("--prepare-approval-digest")

        self.assertEqual(1, already_bound_result.returncode, already_bound_result.stdout)
        self.assertNotIn(
            "approval_candidate_reviewed_content_sha256", already_bound_result.stdout
        )

    def test_cli_require_approved_review_blocks_pending_then_accepts_bound_review(
        self,
    ) -> None:
        pending_result = self.run_cli("--require-approved-review")

        self.assertEqual(1, pending_result.returncode, pending_result.stdout)
        self.assertIn("[PENDING] Human provenance review:", pending_result.stdout)
        self.assertIn(
            "[FAIL] --require-approved-review requires a valid, digest-bound",
            pending_result.stdout,
        )

        self.approve_current_content()
        approved_result = self.run_cli("--require-approved-review")

        self.assertEqual(
            0,
            approved_result.returncode,
            approved_result.stdout + approved_result.stderr,
        )
        self.assertIn(
            "[PASS] Recorded provenance review is mechanically consistent and "
            "digest-bound: THIRD_PARTY_APPROVED",
            approved_result.stdout,
        )
        self.assertNotIn("[PENDING]", approved_result.stdout)

    def test_cli_retires_ambiguous_print_review_digest_option(self) -> None:
        result = self.run_cli("--print-review-digest")

        self.assertEqual(2, result.returncode, result.stdout)
        self.assertIn("--print-review-digest is retired", result.stdout)
        self.assertIn("--diagnostic-pending-digest", result.stdout)
        self.assertIn("--prepare-approval-digest", result.stdout)
        self.assertNotIn("reviewed_content_sha256:", result.stdout)

    def test_schema_version_two_is_rejected_after_git_snapshot_contract_change(
        self,
    ) -> None:
        self.document["schema_version"] = 2
        self.write_review_documents(approved=False)
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(any("schema_version must be integer 3" in e for e in errors))

    def test_schema_three_review_digest_domain_is_stable(self) -> None:
        self.assertEqual(
            b"arce-v0.0.2-bootstrap-provenance-review-v3\0",
            REVIEW_DIGEST_DOMAIN,
        )
        document = {
            "schema_version": 3,
            "scope_version": "v0.0.2",
            "review": {
                "record_status": PENDING_RECORD_STATUS,
                "reviewer": None,
                "reviewed_at": None,
                "final_status_after_review": None,
                "reviewed_audited_target_commit": None,
                "reviewed_content_sha256": None,
            },
        }
        record = b"""# deterministic digest fixture

```yaml
record_status: EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING
reviewer: null
reviewed_at: null
final_status_after_review: null
reviewed_audited_target_commit: null
reviewed_content_sha256: null
```
"""
        notice = b"# deterministic notice fixture\n"

        self.assertEqual(
            "65ce600cadbb390814a7f00454eb5891a43f29fa1f5c5545ac23e3a47405efcc",
            compute_review_content_sha256(document, record, notice),
        )

    def test_duplicate_json_key_is_rejected_instead_of_being_shadowed(self) -> None:
        content = self.manifest.read_bytes()
        self.assertTrue(content.startswith(b"{"))
        self.manifest.write_bytes(
            b'{\n  "schema_version": 999,' + content[len(b"{") :]
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("duplicate JSON key: schema_version" in error for error in errors),
            errors,
        )

    def test_pending_record_rejects_duplicate_reserved_metadata(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record = record_path.read_bytes()
        marker = b"reviewed_content_sha256: null\n```"
        self.assertIn(marker, record)
        record_path.write_bytes(
            record.replace(
                marker,
                b"reviewed_content_sha256: null\n"
                b"record_status: THIRD_PARTY_APPROVED\n"
                b"reviewer: forged-reviewer\n"
                b"reviewed_at: 2099-01-01\n```",
                1,
            )
        )

        errors, _ = self.validate()

        for field in ("record_status", "reviewer", "reviewed_at"):
            self.assertTrue(
                any(
                    f"initial provenance YAML {field} must occur exactly once"
                    in error
                    for error in errors
                ),
                errors,
            )

    def test_approved_record_rejects_reserved_metadata_in_later_yaml_block(
        self,
    ) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        with record_path.open("ab") as stream:
            stream.write(
                b"\n```yaml\n"
                b"final_status_after_review: REJECTED\n"
                b"reviewed_audited_target_commit: " + b"0" * 40 + b"\n"
                b"```\n"
            )

        errors, _ = self.validate()

        for field in (
            "final_status_after_review",
            "reviewed_audited_target_commit",
        ):
            self.assertTrue(
                any(
                    f"reserved provenance YAML field {field} must occur only "
                    "in the initial metadata block" in error
                    for error in errors
                ),
                errors,
            )

    def test_pending_fixture_can_be_rebuilt_from_approved_machine_state(self) -> None:
        self.document["review"] = {
            "record_status": APPROVED_RECORD_STATUS,
            "reviewer": "prior-reviewer",
            "reviewed_at": "2026-08-27",
            "final_status_after_review": APPROVED_RECORD_STATUS,
            "reviewed_audited_target_commit": self.document[
                "audited_target_commit"
            ],
            "reviewed_content_sha256": "0" * 64,
        }
        for target in self.document["targets"]:
            target["status"] = APPROVED_RECORD_STATUS
            target["proposed_status_after_review"] = None

        self.reset_to_pending_review()
        self.write_review_documents(approved=False)
        self.write_manifest()
        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(PENDING_RECORD_STATUS, details["review_status"])

    def test_missing_third_party_notice_is_rejected(self) -> None:
        (self.root / EXPECTED_NOTICE_PATH).unlink()

        errors, _ = self.validate()

        self.assertTrue(
            any("third-party notice does not exist" in error for error in errors),
            errors,
        )

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

    def test_pending_review_cannot_carry_reviewer_metadata(self) -> None:
        self.document["review"]["reviewer"] = "premature-reviewer"
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any("pending review must have null approval metadata" in error for error in errors),
            errors,
        )

    def test_pending_review_rejects_nonpending_notice_review_block(self) -> None:
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice = notice_path.read_text(encoding="utf-8")
        notice = notice.replace(
            "status: PENDING_HUMAN_REVIEW",
            f"status: {APPROVED_RECORD_STATUS}",
        )
        notice = notice.replace("reviewer: null", "reviewer: premature-reviewer")
        notice = notice.replace("reviewed_at: null", "reviewed_at: 2026-08-28")
        notice_path.write_text(notice, encoding="utf-8")

        errors, _ = self.validate()

        for field in ("status", "reviewer", "reviewed_at"):
            self.assertTrue(
                any(
                    f"pending third-party notice {field} must occur exactly once"
                    in error
                    for error in errors
                ),
                errors,
            )

    def test_pending_review_rejects_approved_provenance_target_block(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                "status: PENDING_HUMAN_REVIEW",
                f"status: {APPROVED_RECORD_STATUS}",
                1,
            ),
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "pending provenance Markdown target status fields contradict"
                in error
                for error in errors
            ),
            errors,
        )

    def test_pending_review_rejects_target_reviewer_and_date(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record = self.read_utf8(record_path)
        target_block = """status: PENDING_HUMAN_REVIEW
proposed_status_after_review: THIRD_PARTY_APPROVED
reviewer: null
reviewed_at: null"""
        changed_block = """status: PENDING_HUMAN_REVIEW
proposed_status_after_review: THIRD_PARTY_APPROVED
reviewer: premature-reviewer
reviewed_at: 2026-08-28"""
        self.assertIn(target_block, record)
        self.write_utf8(record_path, record.replace(target_block, changed_block, 1))

        errors, _ = self.validate()

        for field in ("reviewer", "reviewed_at"):
            self.assertTrue(
                any(
                    f"pending provenance Markdown target {field} fields contradict"
                    in error
                    for error in errors
                ),
                errors,
            )

    def test_complete_approved_review_is_accepted(self) -> None:
        self.approve_current_content()

        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual(APPROVED_RECORD_STATUS, details["review_status"])

    def test_approved_review_is_invalid_after_notice_changes(self) -> None:
        self.approve_current_content()
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_text(
            notice_path.read_text(encoding="utf-8") + "\nPost-review notice change.\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "content digest does not match the current manifest, provenance "
                "record, and third-party notice"
                in error
                for error in errors
            ),
            errors,
        )

    def test_approved_review_rejects_residual_record_pending_status(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8")
            + "\nResidual status: PENDING_HUMAN_REVIEW\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "provenance Markdown still contains pending provenance status"
                in error
                for error in errors
            ),
            errors,
        )

    def test_approved_review_rejects_residual_notice_pending_status(self) -> None:
        self.approve_current_content()
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_text(
            notice_path.read_text(encoding="utf-8")
            + "\nResidual status: PENDING_HUMAN_REVIEW\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "third-party notice still contains pending provenance status"
                in error
                for error in errors
            ),
            errors,
        )

    def test_approved_review_rejects_unchecked_approval_checklist(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8")
            + "\n- [ ] Human reviewer confirms the approval.\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("unchecked checklist item" in error for error in errors),
            errors,
        )

    def test_approved_review_rejects_pending_approval_prose(self) -> None:
        self.approve_current_content()
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_text(
            notice_path.read_text(encoding="utf-8")
            + "\nFinal legal approval remains pending.\n",
            encoding="utf-8",
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("still contains pending approval prose" in error for error in errors),
            errors,
        )

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
        self.write_utf8(
            record_path,
            self.read_utf8(record_path).replace(
                "| imported targets | baseline |",
                "| imported targets | post-review change |",
                1,
            ),
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_approved_digest_detects_record_line_ending_changes(self) -> None:
        self.approve_current_content()
        record_path = self.root / EXPECTED_RECORD_PATH
        original = record_path.read_bytes()
        self.assertIn(b"\n", original)
        self.assertNotIn(b"\r\n", original)
        record_path.write_bytes(original.replace(b"\n", b"\r\n"))

        errors, _ = self.validate()

        self.assertTrue(
            any("content digest does not match" in error for error in errors), errors
        )

    def test_invalid_utf8_provenance_record_is_rejected(self) -> None:
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_bytes(record_path.read_bytes() + b"\xff")

        errors, _ = self.validate()

        self.assertTrue(
            any("Cannot read provenance Markdown record" in error for error in errors),
            errors,
        )

    def test_invalid_utf8_third_party_notice_is_rejected(self) -> None:
        notice_path = self.root / EXPECTED_NOTICE_PATH
        notice_path.write_bytes(notice_path.read_bytes() + b"\xff")

        errors, _ = self.validate()

        self.assertTrue(
            any("Cannot read third-party notice" in error for error in errors),
            errors,
        )

    def test_approved_review_is_invalid_after_target_hash_changes(self) -> None:
        self.approve_current_content()
        target = self.find_target("build.gradle")
        old_hash = target["audited_target_raw_blob_sha256"]
        content = (self.root / "build.gradle").read_bytes() + b"post-review\n"
        new_hash = self.digest(content)
        (self.root / "build.gradle").write_bytes(content)
        target["audited_target_raw_blob_sha256"] = new_hash
        record_path = self.root / EXPECTED_RECORD_PATH
        record_path.write_text(
            record_path.read_text(encoding="utf-8").replace(
                str(old_hash), new_hash, 1
            ),
            encoding="utf-8",
        )
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(
            any(
                "imported target build.gradle audited snapshot" in error
                for error in errors
            ),
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


class RealBootstrapProvenanceValidationTests(unittest.TestCase):
    def test_real_repository_validates_without_presuming_review_state(self) -> None:
        repository_root = Path(__file__).resolve().parents[1]

        errors, details = validate_bootstrap_provenance(repository_root=repository_root)

        self.assertEqual([], errors)
        self.assertRegex(details["review_content_sha256"], r"^[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()
