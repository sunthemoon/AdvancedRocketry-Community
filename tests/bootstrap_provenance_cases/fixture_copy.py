"""FixtureCopyCases for the bootstrap provenance facade."""
import copy
import os
from pathlib import Path


class FixtureCopyCases:
    def test_fixture_copy_preserves_raw_files_modes_and_history(self) -> None:
        template = Path(self._fixture_directory.name)
        expected = sorted(path.relative_to(template) for path in template.rglob("*") if path.is_file())
        observed = sorted(path.relative_to(self.root) for path in self.root.rglob("*") if path.is_file())
        self.assertEqual(expected, observed)
        for relative in expected:
            with self.subTest(path=relative.as_posix()):
                self.assertEqual((template / relative).read_bytes(), (self.root / relative).read_bytes())
                self.assertEqual((template / relative).stat().st_mode, (self.root / relative).stat().st_mode)
                self.assertFalse(os.path.samefile(template / relative, self.root / relative))
        self.assertEqual(self.scope_commit, self.git("rev-parse", "HEAD"))
        self.assertEqual(self.import_commit, self.git("rev-parse", "HEAD^"))
        self.assertEqual(self.pre_import_commit, self.git("rev-parse", "HEAD^^"))
        self.assertEqual("100755", self.tree_entry(self.scope_commit, "gradlew")[0])
        self.assertEqual("100644", self.tree_entry(self.import_commit, "gradlew")[0])
        self.assertEqual(self._fixture_document, self.document)
        self.assertIn("docs/provenance/v0.0.2-bootstrap-inputs.json", self.git("ls-files", "--others"))

    def test_fixture_copies_isolate_manifest_worktree_config_index_and_objects(self) -> None:
        other = type(self)()
        self.addCleanup(other.doCleanups)
        other.setUp()
        template = Path(self._fixture_directory.name)
        original_document = copy.deepcopy(other.document)
        original_target = (other.root / "build.gradle").read_bytes()
        original_index = (other.root / ".git/index").read_bytes()
        original_config = (other.root / ".git/config").read_bytes()
        tree = other.git("rev-parse", "HEAD^{tree}")
        relative_object = Path(".git/objects") / tree[:2] / tree[2:]
        original_object = (other.root / relative_object).read_bytes()

        self.document["targets"][0]["status"] = "isolated mutation"
        (self.root / "build.gradle").write_bytes(b"isolated worktree mutation\n")
        self.git("config", "test.fixture-isolation", "modified")
        self.git("add", "--", "build.gradle")
        self.git("commit", "--quiet", "-m", "isolated case mutation")
        (self.root / relative_object).chmod(0o600)
        (self.root / relative_object).write_bytes(b"isolated corrupt object\n")

        for root in (other.root, template):
            with self.subTest(root=str(root)):
                self.assertEqual(original_target, (root / "build.gradle").read_bytes())
                self.assertEqual(original_index, (root / ".git/index").read_bytes())
                self.assertEqual(original_config, (root / ".git/config").read_bytes())
                self.assertEqual(original_object, (root / relative_object).read_bytes())
        self.assertEqual(original_document, other.document)
        self.assertEqual(original_document, self._fixture_document)
        self.assertEqual(self.scope_commit, other.git("rev-parse", "HEAD"))
        self.assertNotEqual(self.scope_commit, self.git("rev-parse", "HEAD"))
