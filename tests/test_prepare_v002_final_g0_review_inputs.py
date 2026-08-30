from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest import mock


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
        self._write_json(
            Path(
                "docs/releases/v0.0.2/evidence/artifact/"
                "jar-content-manifest.json"
            ),
            {"artifact": "fixture.jar", "entries": []},
        )
        self._write_json(
            Path(
                "docs/releases/v0.0.2/evidence/g0-mechanical/"
                "sources-jar-manifest.json"
            ),
            {"artifact": "fixture-sources.jar", "entries": []},
        )
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
        self._commit("fixture selected")
        self.selected_commit = self._git("rev-parse", "HEAD").strip()
        (self.root / "build").mkdir()

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


class FinalG0ReviewInputsTests(unittest.TestCase):
    def test_generate_and_verify_subcommands_share_one_canonical_report(self) -> None:
        fixture = GitFixture(self)
        output = fixture.root / fixture.output("subcommands")
        stdout = io.StringIO()
        stderr = io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            generated = fixture.tool.main(
                [
                    "generate",
                    "--repository-root",
                    str(fixture.root),
                    "--commit",
                    fixture.selected_commit,
                    "--output",
                    str(output),
                ]
            )
            verified = fixture.tool.main(
                [
                    "verify",
                    "--repository-root",
                    str(fixture.root),
                    "--commit",
                    fixture.selected_commit,
                    "--output",
                    str(output),
                ]
            )
        self.assertEqual((0, 0), (generated, verified))
        self.assertEqual("", stderr.getvalue())
        self.assertEqual(2, stdout.getvalue().count("[PASS]"))
        self.assertEqual(2, stdout.getvalue().count("records no human decision"))

    def test_generate_is_deterministic_and_records_complete_bound_inputs(self) -> None:
        fixture = GitFixture(self)
        selected_a, digest_a = fixture.tool.generate(
            fixture.root, "HEAD", fixture.output("first")
        )
        selected_b, digest_b = fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("second")
        )

        self.assertEqual(fixture.selected_commit, selected_a)
        self.assertEqual(selected_a, selected_b)
        self.assertEqual(digest_a, digest_b)
        self.assertEqual(fixture.report_bytes("first"), fixture.report_bytes("second"))
        verified_commit, verified_digest = fixture.tool.verify(
            fixture.root, fixture.selected_commit, fixture.output("first")
        )
        self.assertEqual((selected_a, digest_a), (verified_commit, verified_digest))

        report = fixture.report_json("first")
        self.assertEqual(1, report["schema_version"])
        self.assertEqual(fixture.base_commit, report["base_commit"])
        self.assertEqual(fixture.selected_commit, report["selected_commit"])
        self.assertRegex(report["base_tree_oid"], r"^[0-9a-f]{40}$")
        self.assertRegex(report["selected_tree_oid"], r"^[0-9a-f]{40}$")
        self.assertEqual(
            {
                "records_final_g0_human_decision": False,
                "result": "INPUTS_ONLY",
            },
            report["review_semantics"],
        )
        self.assertEqual(
            {
                "exact_paths": ["LICENSE", "NOTICE.md", "THIRD-PARTY-NOTICES.md"],
                "recursive_prefixes": [
                    "src/main/java",
                    "src/main/resources",
                    "src/generated/resources",
                    "docs/licenses",
                ],
                "scope_kind": "DISTRIBUTABLE_SOURCE_RESOURCE_LEGAL",
            },
            report["inventory_scope"],
        )

        inventory = {entry["path"]: entry for entry in report["inventory"]}
        self.assertIn("src/main/java/example/Example.java", inventory)
        self.assertIn("src/main/resources/example.txt", inventory)
        self.assertIn("src/generated/resources/generated.bin", inventory)
        self.assertIn("docs/licenses/FORGE.txt", inventory)
        self.assertIn("LICENSE", inventory)
        self.assertIn("NOTICE.md", inventory)
        self.assertIn("THIRD-PARTY-NOTICES.md", inventory)
        for entry in inventory.values():
            self.assertEqual("blob", entry["object_type"])
            self.assertRegex(entry["oid"], r"^[0-9a-f]{40}$")
            self.assertRegex(entry["raw_blob_sha256"], r"^[0-9a-f]{64}$")

        bindings = report["bindings"]
        self.assertEqual(
            {
                "bootstrap_manifest",
                "main_jar_content_manifest",
                "sources_jar_manifest",
            },
            set(bindings),
        )
        declared_targets = {
            target["path"]
            for target in report["bootstrap_manifest_coverage"]["targets"]
        }
        self.assertEqual(set(REQUIRED_BOOTSTRAP_TARGETS), declared_targets)
        self.assertEqual(
            set(REQUIRED_BOOTSTRAP_TARGETS),
            set(
                report["bootstrap_manifest_coverage"][
                    "required_build_gradle_target_paths"
                ]
            ),
        )
        self.assertEqual(
            [fixture.selected_commit], report["history"]["range_commit_oids"]
        )
        self.assertTrue(
            any(
                change["commit"] == fixture.selected_commit
                and change["path"] == "src/main/java/example/Example.java"
                and change["status"] == "M"
                for change in report["history"]["path_changes"]
            )
        )

    def test_dirty_checkout_is_ignored_for_all_authoritative_inputs(self) -> None:
        fixture = GitFixture(self)
        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("clean")
        )

        (fixture.root / "LICENSE").write_text("dirty license\n", encoding="utf-8")
        (fixture.root / "src/main/java/example/Example.java").write_text(
            "dirty source\n", encoding="utf-8"
        )
        (
            fixture.root
            / "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json"
        ).write_text("{\"dirty\": true}\n", encoding="utf-8")
        (fixture.root / "untracked.txt").write_text("ignored\n", encoding="utf-8")

        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("dirty")
        )
        self.assertEqual(fixture.report_bytes("clean"), fixture.report_bytes("dirty"))
        fixture.tool.verify(
            fixture.root, fixture.selected_commit, fixture.output("dirty")
        )

    def test_verify_rejects_mutated_extra_and_hardlinked_output(self) -> None:
        fixture = GitFixture(self)
        error = fixture.tool.ReviewInputError

        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("mutated")
        )
        report_path = (
            fixture.root / fixture.output("mutated") / fixture.tool.REPORT_NAME
        )
        report_path.write_bytes(report_path.read_bytes() + b" ")
        with self.assertRaisesRegex(error, "does not exactly match"):
            fixture.tool.verify(
                fixture.root, fixture.selected_commit, fixture.output("mutated")
            )

        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("extra")
        )
        (fixture.root / fixture.output("extra") / "unexpected.txt").write_text(
            "extra", encoding="utf-8"
        )
        with self.assertRaisesRegex(error, "exactly"):
            fixture.tool.verify(
                fixture.root, fixture.selected_commit, fixture.output("extra")
            )

        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("linked")
        )
        linked_report = fixture.root / fixture.output("linked") / fixture.tool.REPORT_NAME
        hardlink = fixture.root / "build/report-hardlink.json"
        try:
            os.link(linked_report, hardlink)
        except OSError as exc:
            self.skipTest(f"filesystem does not support test hard links: {exc}")
        with self.assertRaisesRegex(error, "unlinked ordinary file"):
            fixture.tool.verify(
                fixture.root, fixture.selected_commit, fixture.output("linked")
            )

    def test_generate_rejects_existing_outside_and_traversal_outputs(self) -> None:
        fixture = GitFixture(self)
        error = fixture.tool.ReviewInputError
        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("once")
        )
        with self.assertRaisesRegex(error, "create-once"):
            fixture.tool.generate(
                fixture.root, fixture.selected_commit, fixture.output("once")
            )
        with self.assertRaisesRegex(error, "below repository build"):
            fixture.tool.generate(
                fixture.root, fixture.selected_commit, Path("outside")
            )
        with self.assertRaisesRegex(error, "traversal"):
            fixture.tool.generate(
                fixture.root,
                fixture.selected_commit,
                Path("build") / ".." / "outside",
            )

    def test_verify_rejects_symlink_or_reparse_output_directory(self) -> None:
        fixture = GitFixture(self)
        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("real-output")
        )
        real_output = fixture.root / fixture.output("real-output")
        linked_output = fixture.root / fixture.output("linked-output")
        try:
            os.symlink(real_output, linked_output, target_is_directory=True)
        except OSError:
            original = fixture.tool._is_reparse_point

            def simulate_reparse(path: Path, status: os.stat_result | None = None) -> bool:
                if Path(path) == real_output:
                    return True
                return original(path, status)

            with mock.patch.object(
                fixture.tool, "_is_reparse_point", side_effect=simulate_reparse
            ):
                with self.assertRaisesRegex(
                    fixture.tool.ReviewInputError, "ordinary directory"
                ):
                    fixture.tool.verify(
                        fixture.root,
                        fixture.selected_commit,
                        fixture.output("real-output"),
                    )
        else:
            with self.assertRaisesRegex(
                fixture.tool.ReviewInputError, "ordinary directory"
            ):
                fixture.tool.verify(
                    fixture.root,
                    fixture.selected_commit,
                    fixture.output("linked-output"),
                )

    def test_wrong_object_and_nonancestor_commit_are_rejected(self) -> None:
        fixture = GitFixture(self)
        error = fixture.tool.ReviewInputError
        with self.assertRaises(error):
            fixture.tool.generate(
                fixture.root, "0" * 40, fixture.output("missing-commit")
            )
        tree_oid = fixture._git(
            "rev-parse", f"{fixture.selected_commit}^{{tree}}"
        ).strip()
        with self.assertRaises(error):
            fixture.tool.generate(fixture.root, tree_oid, fixture.output("tree"))

        unrelated = fixture.unrelated_commit()
        with self.assertRaisesRegex(error, "not an ancestor"):
            fixture.tool.generate(
                fixture.root, unrelated, fixture.output("unrelated")
            )

    def test_legacy_grafts_are_rejected_before_ancestry_or_history(self) -> None:
        fixture = GitFixture(self)
        git_common = fixture._git(
            "rev-parse", "--path-format=absolute", "--git-common-dir"
        ).strip()
        grafts = Path(git_common) / "info/grafts"
        grafts.parent.mkdir(parents=True, exist_ok=True)
        grafts.write_text(
            f"{fixture.selected_commit} {fixture.base_commit}\n", encoding="ascii"
        )
        with self.assertRaisesRegex(
            fixture.tool.ReviewInputError, "legacy Git grafts"
        ):
            fixture.tool.generate(
                fixture.root, fixture.selected_commit, fixture.output("grafted")
            )

    def test_git_command_output_bound_is_enforced_while_streaming(self) -> None:
        fixture = GitFixture(self)
        with self.assertRaisesRegex(
            fixture.tool.ReviewInputError, "output exceeds the configured bound"
        ):
            fixture.tool._run_git(
                fixture.root,
                ["show", f"{fixture.selected_commit}:{fixture.tool.SCRIPT_PATH}"],
                max_output=128,
            )

    def test_history_parent_edges_are_bounded_before_diff_processes(self) -> None:
        fixture = GitFixture(self)
        selected = fixture.octopus_commit()
        fixture.tool.MAX_PARENTS_PER_COMMIT = 1
        with self.assertRaisesRegex(
            fixture.tool.ReviewInputError, "parent-count bound"
        ):
            fixture.tool.generate(
                fixture.root, selected, fixture.output("too-many-parents")
            )

        fixture.tool.MAX_PARENTS_PER_COMMIT = 64
        fixture.tool.MAX_HISTORY_PARENT_EDGES = 0
        with self.assertRaisesRegex(
            fixture.tool.ReviewInputError, "aggregate parent-edge bound"
        ):
            fixture.tool.generate(
                fixture.root, fixture.selected_commit, fixture.output("too-many-edges")
            )

    def test_shallow_history_metadata_is_rejected(self) -> None:
        fixture = GitFixture(self)
        shallow = Path(
            fixture._git(
                "rev-parse", "--path-format=absolute", "--git-common-dir"
            ).strip()
        ) / "shallow"
        shallow.write_text(f"{fixture.base_commit}\n", encoding="ascii")
        with self.assertRaisesRegex(
            fixture.tool.ReviewInputError, "shallow Git history"
        ):
            fixture.tool.generate(
                fixture.root, fixture.selected_commit, fixture.output("shallow")
            )

    def test_nested_output_parent_link_or_reparse_is_rejected(self) -> None:
        fixture = GitFixture(self)
        real_parent = fixture.root / "build/real-parent"
        linked_parent = fixture.root / "build/linked-parent"
        real_parent.mkdir()
        try:
            os.symlink(real_parent, linked_parent, target_is_directory=True)
        except OSError:
            linked_parent.mkdir()
            original = fixture.tool._is_reparse_point

            def simulate_reparse(path: Path, status: os.stat_result | None = None) -> bool:
                if Path(path) == linked_parent:
                    return True
                return original(path, status)

            with mock.patch.object(
                fixture.tool, "_is_reparse_point", side_effect=simulate_reparse
            ):
                with self.assertRaisesRegex(
                    fixture.tool.ReviewInputError, "ordinary directory"
                ):
                    fixture.tool.generate(
                        fixture.root,
                        fixture.selected_commit,
                        Path("build/linked-parent/report"),
                    )
        else:
            with self.assertRaisesRegex(
                fixture.tool.ReviewInputError, "ordinary directory"
            ):
                fixture.tool.generate(
                    fixture.root,
                    fixture.selected_commit,
                    Path("build/linked-parent/report"),
                )

    def test_runtime_script_must_match_selected_commit(self) -> None:
        fixture = GitFixture(self)
        with (fixture.root / TOOL_PATH).open("ab") as stream:
            stream.write(b"\n# dirty runtime tool\n")
        with self.assertRaisesRegex(
            fixture.tool.ReviewInputError, "tool bytes do not match"
        ):
            fixture.tool.generate(
                fixture.root, fixture.selected_commit, fixture.output("runtime-mismatch")
            )

    def test_bootstrap_target_coverage_is_required_and_commit_bound(self) -> None:
        fixture = GitFixture(self)
        manifest_path = fixture.root / "docs/provenance/v0.0.2-bootstrap-inputs.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["targets"] = manifest["targets"][:-1]
        manifest_path.write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

        # Dirty manifest edits are ignored because the selected Git blob is authoritative.
        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("bound-manifest")
        )
        report = fixture.report_json("bound-manifest")
        self.assertEqual(
            set(REQUIRED_BOOTSTRAP_TARGETS),
            {
                target["path"]
                for target in report["bootstrap_manifest_coverage"]["targets"]
            },
        )

    def test_nonisolated_cli_fails_before_processing_repository_inputs(self) -> None:
        fixture = GitFixture(self)
        result = subprocess.run(
            [
                sys.executable,
                str(fixture.root / TOOL_PATH),
                "generate",
                "--repository-root",
                str(fixture.root),
                "--commit",
                fixture.selected_commit,
                "--output",
                str(fixture.root / fixture.output("cli")),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(2, result.returncode)
        self.assertIn("python -I -S", result.stderr)
        self.assertFalse((fixture.root / fixture.output("cli")).exists())

    def test_isolated_cli_generate_and_verify_succeed_from_selected_tool(self) -> None:
        fixture = GitFixture(self, tool_after_base=True)
        output = fixture.root / fixture.output("isolated-cli")
        common = [
            sys.executable,
            "-I",
            "-S",
            str(fixture.root / TOOL_PATH),
        ]
        generate_result = subprocess.run(
            [
                *common,
                "generate",
                "--repository-root",
                str(fixture.root),
                "--commit",
                fixture.selected_commit,
                "--output",
                str(output),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, generate_result.returncode, generate_result.stderr)
        self.assertIn("[PASS] generated", generate_result.stdout)
        verify_result = subprocess.run(
            [
                *common,
                "verify",
                "--repository-root",
                str(fixture.root),
                "--commit",
                fixture.selected_commit,
                "--output",
                str(output),
            ],
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, verify_result.returncode, verify_result.stderr)
        self.assertIn("[PASS] verified", verify_result.stdout)


if __name__ == "__main__":
    unittest.main()
