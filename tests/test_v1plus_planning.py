import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.validate_v1plus_planning import ROOT, SOURCE_RECORD, VERSION_DOCS, validate


class PlanningValidationTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        # Copy canonical documents only, not the user's untracked input package.
        for folder in ("docs", "codex-prompts"):
            for source in (ROOT / folder).rglob("*.md"):
                target = self.root / source.relative_to(ROOT)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
        for source in ROOT.glob("*.md"):
            shutil.copyfile(source, self.root / source.name)
        shutil.copyfile(ROOT / SOURCE_RECORD, self.root / SOURCE_RECORD)
        manifest = json.loads((ROOT / SOURCE_RECORD).read_text(encoding="utf-8"))
        for row in manifest["inputs"]:
            for relative in row["targets"]:
                target = self.root / relative
                if not target.exists():
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(ROOT / relative, target)
        # Entry points also link outside the canonical document directories.
        for relative in ("LICENSE", "compat-test-mod/README.md"):
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
        (self.root / "scripts/validate_v1plus_planning.py").touch()
        # Mutations below use a synthetic draft, independent of the active milestone.
        version = self.root / "docs/versions" / VERSION_DOCS[0]
        text = version.read_text(encoding="utf-8")
        text = re.sub(r"^status: .*?$", "status: PLANNED", text, flags=re.MULTILINE)
        for field in ("human_approved_by", "human_approved_at"):
            text = re.sub(rf"^{field}: .*?$", f'{field}: ""', text, flags=re.MULTILINE)
        version.write_text(text, encoding="utf-8")

    def edit_version(self, old: str, new: str) -> None:
        target = self.root / "docs/versions" / VERSION_DOCS[0]
        text = target.read_text(encoding="utf-8")
        self.assertIn(old, text)
        target.write_text(text.replace(old, new), encoding="utf-8")

    def test_repository_plans_without_original_package(self) -> None:
        self.assertEqual([], validate(self.root))

    def test_missing_version(self) -> None:
        (self.root / "docs/versions" / VERSION_DOCS[-1]).unlink()
        self.assertTrue(any("v2.0.0" in error for error in validate(self.root)))

    def test_empty_gates(self) -> None:
        self.edit_version("required_gates: [G0, G1, G2, G3, G4, G5, G6, G7, G8, G9]", "required_gates: []")
        self.assertTrue(any("Required Gate" in error for error in validate(self.root)))

    def test_in_progress_milestone_remains_valid_without_approval(self) -> None:
        self.edit_version("status: PLANNED", "status: IN_PROGRESS")
        self.assertEqual([], validate(self.root))

    def test_duplicate_gate(self) -> None:
        self.edit_version("G8, G9]", "G8, G8]")
        self.assertTrue(any("Required Gate" in error for error in validate(self.root)))

    def test_duplicate_status_field(self) -> None:
        self.edit_version("status: PLANNED", "status: PASSED\nstatus: PLANNED")
        self.assertTrue(any("duplicate status field" in error for error in validate(self.root)))

    def test_phase_is_not_version_status(self) -> None:
        self.edit_version("status: PLANNED", "status: CONTRACT_FROZEN")
        self.assertTrue(any("identity/status" in error for error in validate(self.root)))

    def test_unapproved_pass_is_rejected(self) -> None:
        self.edit_version("status: PLANNED", "status: PASSED")
        self.assertTrue(any("human_approved" in error for error in validate(self.root)))

    def test_missing_required_command(self) -> None:
        self.edit_version("./gradlew runGameTestServer", "./gradlew help")
        self.assertTrue(any("missing required command" in error for error in validate(self.root)))

    def test_broken_link(self) -> None:
        with (self.root / "README.md").open("a", encoding="utf-8") as stream:
            stream.write("\n[Missing](docs/not-present.md)\n")
        self.assertTrue(any("broken local link" in error for error in validate(self.root)))

    def test_missing_linked_compatibility_guide_is_rejected(self) -> None:
        (self.root / "compat-test-mod/README.md").unlink()
        self.assertTrue(any("broken local link compat-test-mod/README.md" in error
                            for error in validate(self.root)))

    def test_unclosed_fence(self) -> None:
        with (self.root / "README.md").open("a", encoding="utf-8") as stream:
            stream.write("\n~~~text\n")
        self.assertTrue(any("unclosed Markdown fence" in error for error in validate(self.root)))

    def test_source_path_escape(self) -> None:
        target = self.root / SOURCE_RECORD
        data = json.loads(target.read_text(encoding="utf-8"))
        data["inputs"][0]["source_path"] = "../outside.md"
        target.write_text(json.dumps(data), encoding="utf-8")
        self.assertTrue(any("invalid or duplicate source" in error for error in validate(self.root)))

    def test_original_package_size_and_hash_mismatches(self) -> None:
        package = self.root / "input-package"
        data = json.loads((self.root / SOURCE_RECORD).read_text(encoding="utf-8"))
        for row in data["inputs"]:
            source = package / row["source_path"]
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_bytes(b"x" * row["source_bytes"])
        errors = validate(self.root, package)
        self.assertTrue(any("hash mismatch" in error for error in errors))
        (package / data["inputs"][0]["source_path"]).write_bytes(b"changed")
        self.assertTrue(any("size mismatch" in error for error in validate(self.root, package)))

    def test_original_package_extra_file(self) -> None:
        package = self.root / "input-package"
        package.mkdir()
        (package / "unexpected.md").write_text("extra", encoding="utf-8")
        self.assertTrue(any("file set differs" in error for error in validate(self.root, package)))


if __name__ == "__main__":
    unittest.main()
