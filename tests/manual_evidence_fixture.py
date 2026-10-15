"""Private initial repository seeds with independent raw per-case copies."""
import hashlib
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

from scripts.collect_v002_manual_evidence import CONTENT_MANIFEST


def _build_repository_fixture(root: Path, artifact_name: str) -> str:
    (root / ".gitattributes").write_bytes(b"* text eol=lf\n")
    (root / "README.md").write_bytes(
        b"# Test repository\n\nBound manual evidence fixture.\n"
    )
    (root / ".gitignore").write_bytes(b"/build/\n")
    artifact_hash = hashlib.sha256(b"final-distributable-v002").hexdigest()
    manifest = root / CONTENT_MANIFEST
    manifest.parent.mkdir(parents=True)
    manifest.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "artifact": artifact_name,
                "artifact_sha256": artifact_hash,
                "entry_count": 0,
                "entries": [],
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    subprocess.run(["git", "init", "-q"], cwd=root, check=True)
    subprocess.run(
        [
            "git",
            "add",
            ".gitattributes",
            ".gitignore",
            "README.md",
            CONTENT_MANIFEST.as_posix(),
        ],
        cwd=root,
        check=True,
    )
    subprocess.run(
        [
            "git",
            "-c",
            "user.name=Evidence Fixture",
            "-c",
            "user.email=evidence@example.invalid",
            "commit",
            "-q",
            "-m",
            "test fixture",
        ],
        cwd=root,
        check=True,
    )
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


class ManualRepositoryFixtureMixin:
    """Use before unittest.TestCase; no test or validation outcomes are cached."""

    @classmethod
    def setUpClass(cls) -> None:
        super().setUpClass()
        cls._repository_fixture = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls._repository_fixture.cleanup)
        cls._repository_fixture_commit = _build_repository_fixture(
            Path(cls._repository_fixture.name), cls.artifact_name
        )

    def copy_repository_fixture(self, root: Path) -> str:
        # Copy the whole object database; cases never share mutable Git/files.
        shutil.copytree(self._repository_fixture.name, root, dirs_exist_ok=True)
        return self._repository_fixture_commit
