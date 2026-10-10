from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
import hashlib
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


from tests.final_review_input_fixture import (
    FinalReviewInputFixtureMixin,
    GitFixture,
    REPOSITORY_ROOT,
    REQUIRED_BOOTSTRAP_TARGETS,
    SOURCE_TOOL,
    TOOL_PATH,
)


class FinalG0ReviewInputsTests(FinalReviewInputFixtureMixin, unittest.TestCase):
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
        self.assertEqual(2, report["schema_version"])
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
                "derivation": "EXACT_SOURCES_JAR_REPOSITORY_INPUTS",
                "repository_input_count": 8,
                "scope_kind": "DISTRIBUTABLE_SOURCE_RESOURCE_LEGAL",
                "sources_manifest_path": (
                    "docs/releases/v0.0.2/evidence/g0-mechanical/"
                    "sources-jar-manifest.json"
                ),
            },
            report["inventory_scope"],
        )
        self.assertEqual(
            {
                "ready_for_final_human_review": False,
                "record_status": "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING",
                "state": "PENDING_PREREQUISITE_OBSERVED_INPUTS_ONLY",
            },
            report["prerequisites"]["bootstrap_provenance_review"],
        )
        self.assertEqual(
            "STRICT_SELECTED_COMMIT_MANIFEST_SCHEMA_AND_COUNTS",
            report["jar_manifest_coverage"]["coverage_kind"],
        )
        self.assertEqual(
            8,
            report["jar_manifest_coverage"]["sources"][
                "repository_input_count"
            ],
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
            self.assertIsInstance(entry["sources_archive_path"], str)

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
                and change["new_path"] == "src/main/java/example/Example.java"
                and change["old_path"] == "src/main/java/example/Example.java"
                and change["status"] == "M"
                for change in report["history"]["path_changes"]
            )
        )

    def test_bound_manifest_schemas_and_repository_inputs_fail_closed(self) -> None:
        main_fixture = GitFixture(self)
        main_path = Path(
            "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json"
        )
        main_document = json.loads(
            (main_fixture.root / main_path).read_text(encoding="utf-8")
        )
        main_document.pop("entry_count")
        main_fixture._write_json(main_path, main_document)
        main_fixture._commit("malformed main manifest")
        main_fixture.selected_commit = main_fixture._git("rev-parse", "HEAD").strip()
        with self.assertRaisesRegex(
            main_fixture.tool.ReviewInputError, "main JAR content manifest fields"
        ):
            main_fixture.tool.generate(
                main_fixture.root,
                main_fixture.selected_commit,
                main_fixture.output("malformed-main"),
            )

        main_document["entry_count"] = 2
        main_fixture._write_json(main_path, main_document)
        main_fixture._commit("wrong main manifest entry count")
        main_fixture.selected_commit = main_fixture._git("rev-parse", "HEAD").strip()
        with self.assertRaisesRegex(
            main_fixture.tool.ReviewInputError,
            "entry_count does not match entries",
        ):
            main_fixture.tool.generate(
                main_fixture.root,
                main_fixture.selected_commit,
                main_fixture.output("wrong-main-count"),
            )

        main_document["entry_count"] = 1
        main_document["schema_version"] = True
        main_fixture._write_json(main_path, main_document)
        main_fixture._commit("boolean main manifest schema")
        main_fixture.selected_commit = main_fixture._git("rev-parse", "HEAD").strip()
        with self.assertRaisesRegex(
            main_fixture.tool.ReviewInputError, "schema_version must be 1"
        ):
            main_fixture.tool.generate(
                main_fixture.root,
                main_fixture.selected_commit,
                main_fixture.output("boolean-main-schema"),
            )

        sources_path = Path(
            "docs/releases/v0.0.2/evidence/g0-mechanical/"
            "sources-jar-manifest.json"
        )
        malformed_sources_fixture = GitFixture(self)
        malformed_sources_document = json.loads(
            (malformed_sources_fixture.root / sources_path).read_text(encoding="utf-8")
        )
        malformed_sources_document.pop("scope")
        malformed_sources_fixture._write_json(sources_path, malformed_sources_document)
        malformed_sources_fixture._commit("malformed sources manifest")
        malformed_sources_fixture.selected_commit = malformed_sources_fixture._git(
            "rev-parse", "HEAD"
        ).strip()
        with self.assertRaisesRegex(
            malformed_sources_fixture.tool.ReviewInputError,
            "sources JAR manifest fields",
        ):
            malformed_sources_fixture.tool.generate(
                malformed_sources_fixture.root,
                malformed_sources_fixture.selected_commit,
                malformed_sources_fixture.output("malformed-sources"),
            )

        wrong_sources_count_fixture = GitFixture(self)
        wrong_sources_count_document = json.loads(
            (wrong_sources_count_fixture.root / sources_path).read_text(encoding="utf-8")
        )
        wrong_sources_count_document["entry_count"] += 1
        wrong_sources_count_fixture._write_json(
            sources_path, wrong_sources_count_document
        )
        wrong_sources_count_fixture._commit("wrong sources manifest entry count")
        wrong_sources_count_fixture.selected_commit = wrong_sources_count_fixture._git(
            "rev-parse", "HEAD"
        ).strip()
        with self.assertRaisesRegex(
            wrong_sources_count_fixture.tool.ReviewInputError,
            "entry_count does not match entries",
        ):
            wrong_sources_count_fixture.tool.generate(
                wrong_sources_count_fixture.root,
                wrong_sources_count_fixture.selected_commit,
                wrong_sources_count_fixture.output("wrong-sources-count"),
            )

        omitted_fixture = GitFixture(self)
        omitted_document = json.loads(
            (omitted_fixture.root / sources_path).read_text(encoding="utf-8")
        )
        omitted_document["repository_inputs"].pop()
        omitted_document["repository_input_count"] -= 1
        omitted_fixture._write_json(sources_path, omitted_document)
        omitted_fixture._commit("omit repository input mapping")
        omitted_fixture.selected_commit = omitted_fixture._git(
            "rev-parse", "HEAD"
        ).strip()
        with self.assertRaisesRegex(
            omitted_fixture.tool.ReviewInputError,
            "does not map every sources-JAR file",
        ):
            omitted_fixture.tool.generate(
                omitted_fixture.root,
                omitted_fixture.selected_commit,
                omitted_fixture.output("omitted-input"),
            )

        extra_fixture = GitFixture(self)
        extra_document = json.loads(
            (extra_fixture.root / sources_path).read_text(encoding="utf-8")
        )
        missing_content = b"not present in selected Git\n"
        missing_entry = {
            "archive_path": "example/Missing.java",
            "repository_path": "src/main/java/example/Missing.java",
            "sha256": hashlib.sha256(missing_content).hexdigest(),
            "size": len(missing_content),
        }
        extra_document["repository_inputs"].append(missing_entry)
        extra_document["repository_input_count"] += 1
        extra_document["entries"].append(
            {
                "path": missing_entry["archive_path"],
                "sha256": missing_entry["sha256"],
                "size": missing_entry["size"],
            }
        )
        extra_document["entries"].sort(key=lambda entry: entry["path"])
        extra_document["entry_count"] += 1
        extra_fixture._write_json(sources_path, extra_document)
        extra_fixture._commit("add nonexistent repository input")
        extra_fixture.selected_commit = extra_fixture._git("rev-parse", "HEAD").strip()
        with self.assertRaisesRegex(
            extra_fixture.tool.ReviewInputError, "missing selected Git paths"
        ):
            extra_fixture.tool.generate(
                extra_fixture.root,
                extra_fixture.selected_commit,
                extra_fixture.output("extra-input"),
            )

    def test_full_range_history_records_cross_scope_rename_and_copy_lineage(self) -> None:
        fixture = GitFixture(self)
        renamed_origin = Path("legacy/RenamedOrigin.java")
        copied_origin = Path("legacy/CopiedOrigin.java")
        fixture._write(renamed_origin, b"class RenamedOrigin {}\n")
        fixture._write(copied_origin, b"class CopiedOrigin {}\n")
        fixture._commit("add out-of-scope origins")

        renamed_destination = Path("src/main/java/example/RenamedOrigin.java")
        renamed_destination.parent.mkdir(parents=True, exist_ok=True)
        (fixture.root / renamed_origin).replace(fixture.root / renamed_destination)
        copied_destination = Path("src/main/java/example/CopiedOrigin.java")
        shutil.copy2(fixture.root / copied_origin, fixture.root / copied_destination)
        fixture._refresh_manifests()
        fixture._commit("move and copy origins into distributable sources")
        fixture.selected_commit = fixture._git("rev-parse", "HEAD").strip()

        fixture.tool.generate(
            fixture.root, fixture.selected_commit, fixture.output("lineage")
        )
        report = fixture.report_json("lineage")
        lineage = {
            (
                relation["kind"],
                relation["source_path"],
                relation["destination_path"],
            )
            for relation in report["history"]["exact_blob_lineage"]
        }
        self.assertIn(
            (
                "EXACT_BLOB_RENAME_CANDIDATE",
                renamed_origin.as_posix(),
                renamed_destination.as_posix(),
            ),
            lineage,
        )
        self.assertIn(
            (
                "EXACT_BLOB_COPY_SOURCE",
                copied_origin.as_posix(),
                copied_destination.as_posix(),
            ),
            lineage,
        )
        changed_paths = {
            (change["status"], change["old_path"], change["new_path"])
            for change in report["history"]["path_changes"]
        }
        self.assertIn(("D", renamed_origin.as_posix(), None), changed_paths)
        self.assertIn(("A", None, renamed_destination.as_posix()), changed_paths)

    def test_repository_local_git_executable_is_rejected(self) -> None:
        fixture = GitFixture(self)
        fake_git = fixture.root / "tools/git"
        fake_git.parent.mkdir(parents=True)
        fake_git.write_bytes(b"fake git executable\n")
        with mock.patch.object(
            fixture.tool.shutil, "which", return_value=str(fake_git)
        ):
            with self.assertRaisesRegex(
                fixture.tool.ReviewInputError, "must not be contained in the repository"
            ):
                fixture.tool._git_executable(fixture.root)

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
