from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
SOURCE_TOOL = REPOSITORY_ROOT / "scripts/prepare_v002_final_g0_review_inputs.py"
TOOL_PATH = Path("scripts/prepare_v002_final_g0_review_inputs.py")

REQUIRED_BOOTSTRAP_TARGETS = (
    ".gitattributes",
    ".gitignore",
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradlew",
    "gradlew.bat",
)


class GitFixture:
    def __init__(
        self, testcase: unittest.TestCase, *, tool_after_base: bool = False
    ) -> None:
        self.testcase = testcase
        self.temporary = tempfile.TemporaryDirectory()
        testcase.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        seed = None if tool_after_base else getattr(
            type(testcase), "_final_input_repository_seed", None
        )
        if seed is None:
            self._build_repository(tool_after_base=tool_after_base)
        else:
            shutil.copytree(seed.root, self.root, dirs_exist_ok=True)
            self.base_commit = seed.base_commit
            self.selected_commit = seed.selected_commit
        self._load_module(testcase)

    def _build_repository(self, *, tool_after_base: bool) -> None:
        self._git("init", "--quiet")
        self._git("config", "user.email", "review-inputs@example.invalid")
        self._git("config", "user.name", "Review Inputs Test")
        self._git("config", "core.autocrlf", "false")

        if not tool_after_base:
            self._write(TOOL_PATH, SOURCE_TOOL.read_bytes())
        self._write(Path("LICENSE"), b"fixture project license\n")
        self._write(Path("NOTICE.md"), b"fixture notice\n")
        self._write(
            Path("THIRD-PARTY-NOTICES.md"), b"fixture third-party notice\n"
        )
        self._write(Path("src/main/java/example/Example.java"), b"class Example {}\n")
        self._write(Path("src/main/resources/example.txt"), b"resource\n")
        self._write(Path("src/generated/resources/generated.bin"), b"generated\x00")
        self._write(Path("docs/licenses/FORGE.txt"), b"forge license\n")

        for path in REQUIRED_BOOTSTRAP_TARGETS:
            content = b"bootstrap target\n"
            if path.endswith(".jar"):
                content = b"PK\x03\x04fixture"
            self._write(Path(path), content)

        bootstrap_manifest = {
            "review": {
                "final_status_after_review": None,
                "record_status": "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING",
                "reviewed_at": None,
                "reviewed_audited_target_commit": None,
                "reviewed_content_sha256": None,
                "reviewer": None,
            },
            "schema_version": 3,
            "scope_version": "v0.0.2",
            "targets": [
                {
                    "component": "fixture_bootstrap",
                    "path": path,
                    "status": "PENDING_HUMAN_REVIEW",
                }
                for path in REQUIRED_BOOTSTRAP_TARGETS
            ],
        }
        self._write_json(
            Path("docs/provenance/v0.0.2-bootstrap-inputs.json"),
            bootstrap_manifest,
        )
        self._refresh_manifests()
        self._commit("fixture base")
        self.base_commit = self._git("rev-parse", "HEAD").strip()

        if tool_after_base:
            source = SOURCE_TOOL.read_text(encoding="utf-8")
            configured = source.replace(
                'BASE_COMMIT = "86b9db01b1cb4c8b8f673590baf1dc185d1716b3"',
                f'BASE_COMMIT = "{self.base_commit}"',
                1,
            )
            if configured == source:
                raise AssertionError("fixture could not configure fixed base commit")
            self._write(TOOL_PATH, configured.encode("utf-8"))

        self._write(
            Path("src/main/java/example/Example.java"),
            b"class Example { int version = 2; }\n",
        )
        self._write(Path("docs/licenses/SECOND.txt"), b"second license\n")
        self._refresh_manifests()
        self._commit("fixture selected")
        self.selected_commit = self._git("rev-parse", "HEAD").strip()
        (self.root / "build").mkdir()

    def _load_module(self, testcase: unittest.TestCase) -> None:
        self.module_name = "v002_final_g0_" + uuid.uuid4().hex
        spec = importlib.util.spec_from_file_location(
            self.module_name, self.root / TOOL_PATH
        )
        if spec is None or spec.loader is None:
            raise AssertionError("cannot create fixture module spec")
        module = importlib.util.module_from_spec(spec)
        sys.modules[self.module_name] = module
        testcase.addCleanup(sys.modules.pop, self.module_name, None)
        spec.loader.exec_module(module)
        module.BASE_COMMIT = self.base_commit
        self.tool = module

    def _write(self, relative: Path, content: bytes) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)

    def _write_json(self, relative: Path, value: object) -> None:
        self._write(
            relative,
            (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8"),
        )

    @staticmethod
    def _sha256(content: bytes) -> str:
        return hashlib.sha256(content).hexdigest()

    def _refresh_manifests(self) -> None:
        repository_mappings: dict[str, Path] = {
            "META-INF/LICENSE": Path("LICENSE"),
            "META-INF/NOTICE.md": Path("NOTICE.md"),
            "META-INF/THIRD-PARTY-NOTICES.md": Path("THIRD-PARTY-NOTICES.md"),
        }
        for source_root, archive_prefix in (
            (Path("docs/licenses"), "META-INF/licenses"),
            (Path("src/main/java"), ""),
            (Path("src/main/resources"), ""),
            (Path("src/generated/resources"), ""),
        ):
            root = self.root / source_root
            if not root.exists():
                continue
            for source in sorted(path for path in root.rglob("*") if path.is_file()):
                relative = source.relative_to(root).as_posix()
                archive_path = (
                    f"{archive_prefix}/{relative}" if archive_prefix else relative
                )
                repository_mappings[archive_path] = source.relative_to(self.root)

        entries = []
        repository_inputs = []
        for archive_path, repository_path in sorted(repository_mappings.items()):
            content = (self.root / repository_path).read_bytes()
            digest = self._sha256(content)
            entries.append(
                {"path": archive_path, "sha256": digest, "size": len(content)}
            )
            repository_inputs.append(
                {
                    "archive_path": archive_path,
                    "repository_path": repository_path.as_posix(),
                    "sha256": digest,
                    "size": len(content),
                }
            )

        generated_content = b"Manifest-Version: 1.0\n"
        generated_digest = self._sha256(generated_content)
        entries.append(
            {
                "path": "META-INF/MANIFEST.MF",
                "sha256": generated_digest,
                "size": len(generated_content),
            }
        )
        entries.sort(key=lambda entry: entry["path"])
        binary_sha256 = "1" * 64
        sources_sha256 = "2" * 64
        self._write_json(
            Path(
                "docs/releases/v0.0.2/evidence/artifact/"
                "jar-content-manifest.json"
            ),
            {
                "artifact": "fixture.jar",
                "artifact_sha256": binary_sha256,
                "entries": [
                    {
                        "path": "META-INF/MANIFEST.MF",
                        "sha256": generated_digest,
                        "size": len(generated_content),
                    }
                ],
                "entry_count": 1,
                "schema_version": 1,
            },
        )
        license_paths = sorted(
            path
            for path in repository_mappings
            if path.startswith("META-INF/")
            and ("LICENSE" in path or "NOTICE" in path)
        )
        self._write_json(
            Path(
                "docs/releases/v0.0.2/evidence/g0-mechanical/"
                "sources-jar-manifest.json"
            ),
            {
                "artifact": "fixture-sources.jar",
                "artifact_sha256": sources_sha256,
                "entries": entries,
                "entry_count": len(entries),
                "generated_inputs": [
                    {
                        "archive_path": "META-INF/MANIFEST.MF",
                        "generator": "fixture manifest generator",
                        "sha256": generated_digest,
                        "size": len(generated_content),
                    }
                ],
                "license_notice_paths": license_paths,
                "paired_binary_artifact": "fixture.jar",
                "paired_binary_sha256": binary_sha256,
                "repository_input_count": len(repository_inputs),
                "repository_inputs": repository_inputs,
                "schema_version": 1,
                "scope": "Fixture mechanical packaging evidence only.",
            },
        )

    def _git(self, *arguments: str, input_bytes: bytes | None = None) -> str:
        environment = dict(os.environ)
        environment.update(
            {
                "GIT_AUTHOR_DATE": "2026-08-30T00:00:00+00:00",
                "GIT_COMMITTER_DATE": "2026-08-30T00:00:00+00:00",
            }
        )
        result = subprocess.run(
            ["git", "-C", str(self.root), *arguments],
            check=False,
            input=input_bytes,
            capture_output=True,
            env=environment,
        )
        if result.returncode != 0:
            raise AssertionError(
                f"git {' '.join(arguments)} failed: "
                + result.stderr.decode("utf-8", errors="replace")
            )
        return result.stdout.decode("ascii", errors="strict")

    def _commit(self, message: str) -> None:
        self._git("add", "--all")
        self._git("commit", "--quiet", "-m", message)

    def output(self, name: str) -> Path:
        return Path("build") / name

    def report_bytes(self, name: str) -> bytes:
        return (self.root / self.output(name) / self.tool.REPORT_NAME).read_bytes()

    def report_json(self, name: str) -> dict[str, object]:
        return json.loads(self.report_bytes(name).decode("utf-8"))

    def unrelated_commit(self) -> str:
        tree = self._git("rev-parse", f"{self.selected_commit}^{{tree}}").strip()
        return self._git("commit-tree", tree, input_bytes=b"unrelated\n").strip()

    def octopus_commit(self) -> str:
        tree = self._git("rev-parse", f"{self.selected_commit}^{{tree}}").strip()
        unrelated = self.unrelated_commit()
        return self._git(
            "commit-tree",
            tree,
            "-p",
            self.selected_commit,
            "-p",
            unrelated,
            input_bytes=b"octopus\n",
        ).strip()


class FinalReviewInputFixtureMixin:
    @classmethod
    def setUpClass(cls) -> None:
        super().setUpClass()
        seed = GitFixture.__new__(GitFixture)
        seed.temporary = tempfile.TemporaryDirectory()
        cls.addClassCleanup(seed.temporary.cleanup)
        seed.root = Path(seed.temporary.name)
        seed._build_repository(tool_after_base=False)
        cls._final_input_repository_seed = seed
