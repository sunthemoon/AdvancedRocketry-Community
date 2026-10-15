"""Case-local repositories and helpers for bootstrap provenance scenarios."""
import copy
import hashlib
import io
import json
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path
from scripts.validate_bootstrap_provenance import APPROVED_RECORD_STATUS, DEFAULT_MANIFEST, EXPECTED_NOTICE_PATH, EXPECTED_RECORD_PATH, PENDING_RECORD_STATUS, compute_review_content_sha256, validate_bootstrap_provenance, validate_bootstrap_provenance_at_commit


class BootstrapProvenanceFixtureMixin:
    @classmethod
    def setUpClass(cls) -> None:
        cls._fixture_directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls._fixture_directory.cleanup)
        fixture = cls()
        fixture.root = Path(cls._fixture_directory.name)
        fixture._build_fixture()
        cls._fixture_document = fixture.document
        cls._fixture_commits = (
            fixture.pre_import_commit, fixture.import_commit, fixture.scope_commit
        )

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        # Copy raw files and the entire object database; cases never share mutations.
        shutil.copytree(self._fixture_directory.name, self.root, dirs_exist_ok=True)
        self.source_root = Path(__file__).resolve().parents[1]
        self.manifest = self.root / DEFAULT_MANIFEST
        self.document = copy.deepcopy(self._fixture_document)
        self.pre_import_commit, self.import_commit, self.scope_commit = self._fixture_commits

    def _build_fixture(self) -> None:
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
