from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
import hashlib
import importlib.util
import io
import json
import os
import struct
import subprocess
import sys
import tempfile
import unittest
import uuid
import zlib
from pathlib import Path
from unittest import mock


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
TOOL_PATH = REPOSITORY_ROOT / "scripts/validate_v002_final_g0_review.py"
MODULE_NAME = "validate_v002_final_g0_review_test_" + uuid.uuid4().hex
SPEC = importlib.util.spec_from_file_location(MODULE_NAME, TOOL_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("cannot load final-G0 validator")
TOOL = importlib.util.module_from_spec(SPEC)
sys.modules[MODULE_NAME] = TOOL
SPEC.loader.exec_module(TOOL)


def _png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    return (
        struct.pack(">I", len(data))
        + chunk_type
        + data
        + struct.pack(">I", zlib.crc32(chunk_type + data) & 0xFFFFFFFF)
    )


def full_window_png(width: int = 640, height: int = 360) -> bytes:
    rows = b"".join(b"\0" + b"\x22\x44\x66" * width for _ in range(height))
    return (
        TOOL.PNG_SIGNATURE
        + _png_chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + _png_chunk(b"IDAT", zlib.compress(rows, 9))
        + _png_chunk(b"IEND", b"")
    )


class GitFixture:
    def __init__(self, testcase: unittest.TestCase) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        testcase.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self._git("init", "--quiet")
        self._git("config", "user.email", "final-g0@example.invalid")
        self._git("config", "user.name", "Final G0 Fixture")
        self._git("config", "core.autocrlf", "false")
        self.write("README.md", b"# Fixture\n\nUnofficial project.\n")
        self.write("src/main/java/example/Bootstrap.java", b"class Bootstrap {}\n")
        self.write("docs/provenance/record.md", b"fixture provenance\n")
        self.write("LICENSE", b"fixture license\n")
        self.write("NOTICE.md", b"fixture notice\n")
        self.write("THIRD-PARTY-NOTICES.md", b"fixture third party notice\n")
        self.commit("selected implementation")
        self.selected = self.rev_parse("HEAD")
        self.selected_tree = self.rev_parse(f"{self.selected}^{{tree}}")

    def _git(self, *arguments: str, input_bytes: bytes | None = None) -> bytes:
        environment = dict(os.environ)
        environment.update(
            {
                "GIT_AUTHOR_DATE": "2026-08-30T00:00:00+00:00",
                "GIT_COMMITTER_DATE": "2026-08-30T00:00:00+00:00",
            }
        )
        result = subprocess.run(
            ["git", "-C", str(self.root), *arguments],
            input=input_bytes,
            capture_output=True,
            check=False,
            env=environment,
        )
        if result.returncode != 0:
            raise AssertionError(
                f"git {' '.join(arguments)} failed: "
                + result.stderr.decode("utf-8", errors="replace")
            )
        return result.stdout

    def rev_parse(self, value: str) -> str:
        return self._git("rev-parse", value).decode("ascii").strip()

    def write(self, relative: str, content: bytes) -> None:
        path = self.root / Path(relative)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)

    def commit(self, message: str) -> str:
        self._git("add", "--all")
        self._git("commit", "--quiet", "-m", message)
        return self.rev_parse("HEAD")

    def stage(self, *paths: str) -> None:
        self._git("add", "--", *paths)

    def unrelated_commit(self) -> str:
        return self._git(
            "commit-tree",
            self.selected_tree,
            input_bytes=b"unrelated\n",
        ).decode("ascii").strip()

    def report(self, selected: str | None = None, tree: str | None = None) -> bytes:
        selected = selected or self.selected
        tree = tree or self.selected_tree
        document = {
            "base_commit": "1" * 40,
            "base_tree_oid": "2" * 40,
            "bindings": {
                "bootstrap_manifest": {
                    "path": "docs/provenance/v0.0.2-bootstrap-inputs.json"
                },
                "main_jar_content_manifest": {
                    "path": (
                        "docs/releases/v0.0.2/evidence/artifact/"
                        "jar-content-manifest.json"
                    )
                },
                "sources_jar_manifest": {
                    "path": (
                        "docs/releases/v0.0.2/evidence/g0-mechanical/"
                        "sources-jar-manifest.json"
                    )
                },
            },
            "bootstrap_manifest_coverage": {
                "coverage_kind": "BOUND_MANIFEST_DECLARED_IMPORTED_TARGETS",
                "required_build_gradle_target_paths": [],
                "targets": [],
            },
            "jar_manifest_coverage": {
                "coverage_kind": "STRICT_SELECTED_COMMIT_MANIFEST_SCHEMA_AND_COUNTS",
                "main": {},
                "sources": {},
            },
            "history": {
                "exact_blob_lineage": [],
                "path_changes": [],
                "range": f"{'1' * 40}..{selected}",
                "range_commit_oids": [],
                "scope_kind": "BOUNDED_FULL_REPOSITORY_RANGE",
            },
            "inventory": [],
            "inventory_scope": {
                "derivation": "EXACT_SOURCES_JAR_REPOSITORY_INPUTS",
                "repository_input_count": 0,
                "scope_kind": "DISTRIBUTABLE_SOURCE_RESOURCE_LEGAL",
                "sources_manifest_path": (
                    "docs/releases/v0.0.2/evidence/g0-mechanical/"
                    "sources-jar-manifest.json"
                ),
            },
            "prerequisites": {
                "bootstrap_provenance_review": dict(
                    TOOL.EXPECTED_APPROVED_PREREQUISITE
                )
            },
            "review_semantics": {
                "records_final_g0_human_decision": False,
                "result": "INPUTS_ONLY",
            },
            "schema_version": TOOL.FINAL_INPUT_REPORT_SCHEMA_VERSION,
            "scope_version": "v0.0.2",
            "selected_commit": selected,
            "selected_tree_oid": tree,
            "tool": {"path": TOOL.REVIEW_INPUT_TOOL_PATH},
        }
        return (json.dumps(document, indent=2, sort_keys=True) + "\n").encode("utf-8")

    def pending_document(self) -> dict[str, object]:
        return {
            "schema_version": TOOL.SCHEMA_VERSION,
            "record_kind": TOOL.RECORD_KIND,
            "record_semantics": dict(TOOL.EXPECTED_SEMANTICS),
            "final_g0_source_resource_review": {
                "outcome": TOOL.PENDING,
                "selected_implementation_commit": None,
                "selected_tree_oid": None,
                "review_inputs_report": None,
                "review_inputs_report_sha256": None,
                "reviewer": None,
                "reviewed_at": None,
                "findings": [],
            },
            "final_g0_readme_visual_review": {
                "outcome": TOOL.PENDING,
                "selected_commit": None,
                "selected_tree_oid": None,
                "screenshot_file": None,
                "screenshot_sha256": None,
                "reviewer": None,
                "reviewed_at": None,
                "findings": [],
            },
        }

    def markdown(self, document: dict[str, object]) -> bytes:
        body = json.dumps(document, indent=2, sort_keys=True)
        return (
            b"# Release evidence\n\n"
            + TOOL.START_MARKER
            + b"\n```json\n"
            + body.encode("utf-8")
            + b"\n```\n"
            + TOOL.END_MARKER
            + b"\n"
        )

    def write_record(self, document: dict[str, object]) -> None:
        self.write(TOOL.DEFAULT_RECORD_PATH.as_posix(), self.markdown(document))

    def bind_source(
        self,
        document: dict[str, object],
        report: bytes,
        *,
        outcome: str = TOOL.APPROVED,
        selected: str | None = None,
        tree: str | None = None,
        findings: list[str] | None = None,
    ) -> str:
        selected = selected or self.selected
        tree = tree or self.selected_tree
        path = (
            f"docs/releases/v0.0.2/evidence/g0-final/{selected}/"
            "final-g0-review-inputs.json"
        )
        self.write(path, report)
        record = document["final_g0_source_resource_review"]
        assert isinstance(record, dict)
        record.update(
            {
                "outcome": outcome,
                "selected_implementation_commit": selected,
                "selected_tree_oid": tree,
                "review_inputs_report": path,
                "review_inputs_report_sha256": hashlib.sha256(report).hexdigest(),
                "reviewer": "fixture-reviewer",
                "reviewed_at": "2026-08-30",
                "findings": findings or [],
            }
        )
        return path

    def bind_readme(
        self,
        document: dict[str, object],
        screenshot: bytes,
        *,
        outcome: str = TOOL.APPROVED,
        selected: str | None = None,
        tree: str | None = None,
        findings: list[str] | None = None,
    ) -> str:
        selected = selected or self.selected
        tree = tree or self.selected_tree
        path = (
            f"docs/releases/v0.0.2/evidence/g0-final/{selected}/"
            "readme-full-window.png"
        )
        self.write(path, screenshot)
        record = document["final_g0_readme_visual_review"]
        assert isinstance(record, dict)
        record.update(
            {
                "outcome": outcome,
                "selected_commit": selected,
                "selected_tree_oid": tree,
                "screenshot_file": path,
                "screenshot_sha256": hashlib.sha256(screenshot).hexdigest(),
                "reviewer": "fixture-reviewer",
                "reviewed_at": "2026-08-30",
                "findings": findings or [],
            }
        )
        return path


def validate_exact(
    fixture: GitFixture,
    record_commit: str,
    report: bytes | None = None,
    *,
    provenance_approved: bool = True,
) -> tuple[list[str], dict[str, object]]:
    provenance = (
        None
        if provenance_approved
        else TOOL.FinalG0ReviewError(
            "selected implementation bootstrap provenance is not THIRD_PARTY_APPROVED"
        )
    )
    with mock.patch.object(
        TOOL,
        "_reconstruct_source_report",
        return_value=report if report is not None else fixture.report(),
    ), mock.patch.object(
        TOOL,
        "_require_approved_bootstrap_provenance",
        side_effect=provenance,
    ):
        return TOOL.validate_final_g0_review_records_at_commit(
            fixture.root, record_commit
        )


class PendingAndSchemaTests(unittest.TestCase):
    def test_pending_records_validate_in_worktree_and_exact_commit_modes(self) -> None:
        fixture = GitFixture(self)
        fixture.write_record(fixture.pending_document())
        worktree_errors, worktree_details = TOOL.validate_final_g0_review_records(
            fixture.root
        )
        record_commit = fixture.commit("record pending reviews")
        exact_errors, exact_details = TOOL.validate_final_g0_review_records_at_commit(
            fixture.root, record_commit
        )

        self.assertEqual([], worktree_errors)
        self.assertEqual([], exact_errors)
        self.assertEqual(TOOL.PENDING, worktree_details["source_review_outcome"])
        self.assertEqual(TOOL.PENDING, exact_details["readme_review_outcome"])
        self.assertEqual("INPUTS_ONLY", exact_details["validation_semantics"])
        self.assertEqual("NOT_COMPUTED", exact_details["gate_decision"])

    def test_pending_record_rejects_non_null_binding_and_findings(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        source = document["final_g0_source_resource_review"]
        assert isinstance(source, dict)
        source["reviewer"] = "not-allowed"
        source["findings"] = ["also not allowed"]
        fixture.write_record(document)

        errors, _ = TOOL.validate_final_g0_review_records(fixture.root)

        self.assertTrue(errors)
        self.assertIn("pending source review", errors[0])

    def test_duplicate_json_key_is_rejected(self) -> None:
        fixture = GitFixture(self)
        raw = (
            b"# Release\n"
            + TOOL.START_MARKER
            + b'\n```json\n{"schema_version":1,"schema_version":1}\n```\n'
            + TOOL.END_MARKER
            + b"\n"
        )
        fixture.write(TOOL.DEFAULT_RECORD_PATH.as_posix(), raw)

        errors, _ = TOOL.validate_final_g0_review_records(fixture.root)

        self.assertTrue(errors)
        self.assertIn("duplicate JSON key", errors[0])

    def test_duplicate_marker_pair_and_unknown_field_are_rejected(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        markdown = fixture.markdown(document)
        fixture.write(
            TOOL.DEFAULT_RECORD_PATH.as_posix(),
            markdown + fixture.markdown(document),
        )
        marker_errors, _ = TOOL.validate_final_g0_review_records(fixture.root)
        document["unexpected"] = True
        fixture.write_record(document)
        field_errors, _ = TOOL.validate_final_g0_review_records(fixture.root)

        self.assertIn("exactly one final-G0 marker pair", marker_errors[0])
        self.assertIn("unexpected=['unexpected']", field_errors[0])

    def test_invalid_outcome_date_and_findings_rules_are_rejected(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        report = fixture.report()
        fixture.bind_source(document, report, outcome=TOOL.CHANGES_REQUIRED)
        source = document["final_g0_source_resource_review"]
        assert isinstance(source, dict)
        source["reviewed_at"] = "2026-02-30"
        fixture.write_record(document)
        date_errors, _ = TOOL.validate_final_g0_review_records(fixture.root)
        source["reviewed_at"] = "2026-08-30"
        fixture.write_record(document)
        finding_errors, _ = TOOL.validate_final_g0_review_records(fixture.root)

        self.assertIn("not a real date", date_errors[0])
        self.assertIn("at least one finding", finding_errors[0])


class BoundReviewTests(unittest.TestCase):
    def test_both_approved_records_pass_exact_git_validation(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        report = fixture.report()
        screenshot = full_window_png()
        fixture.bind_source(document, report)
        fixture.bind_readme(document, screenshot)
        fixture.write_record(document)
        record_commit = fixture.commit("record approved final reviews")

        errors, details = validate_exact(fixture, record_commit, report)

        self.assertEqual([], errors)
        self.assertEqual(TOOL.APPROVED, details["source_review_outcome"])
        self.assertEqual(TOOL.APPROVED, details["readme_review_outcome"])
        self.assertEqual("PASS", details["readme_png_structural_validation"])
        self.assertEqual("HUMAN_ONLY", details["readme_visible_pixel_judgment"])
        self.assertEqual("NOT_COMPUTED", details["gate_decision"])

    def test_changes_required_records_are_valid_only_with_findings(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        report = fixture.report()
        fixture.bind_source(
            document,
            report,
            outcome=TOOL.CHANGES_REQUIRED,
            findings=["Document the observed source mismatch."],
        )
        fixture.bind_readme(
            document,
            full_window_png(),
            outcome=TOOL.CHANGES_REQUIRED,
            findings=["Capture the complete browser window."],
        )
        fixture.write_record(document)
        record_commit = fixture.commit("record requested changes")

        errors, details = validate_exact(fixture, record_commit, report)

        self.assertEqual([], errors)
        self.assertEqual(TOOL.CHANGES_REQUIRED, details["source_review_outcome"])
        self.assertEqual(TOOL.CHANGES_REQUIRED, details["readme_review_outcome"])

    def test_worktree_candidate_requires_and_accepts_index_identical_evidence(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        report = fixture.report()
        screenshot = full_window_png()
        report_path = fixture.bind_source(document, report)
        screenshot_path = fixture.bind_readme(document, screenshot)
        fixture.write_record(document)
        fixture.stage(report_path, screenshot_path)

        with mock.patch.object(
            TOOL, "_reconstruct_source_report", return_value=report
        ), mock.patch.object(TOOL, "_require_approved_bootstrap_provenance"):
            errors, details = TOOL.validate_final_g0_review_records(fixture.root)

        self.assertEqual([], errors)
        self.assertEqual("WORKTREE", details["binding_mode"])

        fixture.write(report_path, report + b" ")
        with mock.patch.object(
            TOOL, "_reconstruct_source_report", return_value=report
        ), mock.patch.object(TOOL, "_require_approved_bootstrap_provenance"):
            changed_errors, _ = TOOL.validate_final_g0_review_records(fixture.root)
        self.assertIn("worktree bytes must exactly match the index", changed_errors[0])

    def test_source_report_content_and_recorded_hash_tampering_fail(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        document = fixture.pending_document()
        fixture.bind_source(document, report + b"tampered")
        source = document["final_g0_source_resource_review"]
        assert isinstance(source, dict)
        fixture.write_record(document)
        record_commit = fixture.commit("record tampered report")

        content_errors, _ = validate_exact(fixture, record_commit, report)
        self.assertIn("do not match exact reconstruction", content_errors[0])

        fixture._git("reset", "--hard", fixture.selected)
        document = fixture.pending_document()
        fixture.bind_source(document, report)
        source = document["final_g0_source_resource_review"]
        assert isinstance(source, dict)
        source["review_inputs_report_sha256"] = "0" * 64
        fixture.write_record(document)
        hash_commit = fixture.commit("record wrong report hash")
        hash_errors, _ = validate_exact(fixture, hash_commit, report)
        self.assertIn("bytes/hash do not match", hash_errors[0])

    def test_selected_tree_binding_must_match_the_selected_commit(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        document = fixture.pending_document()
        fixture.bind_source(document, report)
        source = document["final_g0_source_resource_review"]
        assert isinstance(source, dict)
        source["selected_tree_oid"] = "0" * 40
        fixture.write_record(document)
        record_commit = fixture.commit("record wrong selected tree")

        errors, _ = validate_exact(fixture, record_commit, report)

        self.assertIn("does not match the selected commit tree", errors[0])

    def test_exact_record_api_ignores_later_worktree_tampering(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        report = fixture.report()
        screenshot = full_window_png()
        report_path = fixture.bind_source(document, report)
        screenshot_path = fixture.bind_readme(document, screenshot)
        fixture.write_record(document)
        record_commit = fixture.commit("record exact immutable reviews")
        fixture.write(report_path, b"uncommitted report tampering\n")
        fixture.write(screenshot_path, b"uncommitted screenshot tampering\n")
        fixture.write_record(fixture.pending_document())

        errors, details = validate_exact(fixture, record_commit, report)

        self.assertEqual([], errors)
        self.assertEqual("EXACT_RECORD_COMMIT", details["binding_mode"])

    def test_source_requires_canonical_path_and_approved_bootstrap_provenance(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        document = fixture.pending_document()
        fixture.bind_source(document, report)
        source = document["final_g0_source_resource_review"]
        assert isinstance(source, dict)
        source["review_inputs_report"] = "docs/releases/v0.0.2/evidence/report.json"
        fixture.write_record(document)
        bad_path_commit = fixture.commit("record bad report path")
        path_errors, _ = validate_exact(fixture, bad_path_commit, report)
        self.assertIn("canonical commit-named path", path_errors[0])

        fixture._git("reset", "--hard", fixture.selected)
        document = fixture.pending_document()
        fixture.bind_source(document, report)
        fixture.write_record(document)
        record_commit = fixture.commit("record pending provenance")
        provenance_errors, _ = validate_exact(
            fixture, record_commit, report, provenance_approved=False
        )
        self.assertIn("not THIRD_PARTY_APPROVED", provenance_errors[0])

    def test_source_report_requires_schema_two_and_approved_ready_prerequisite(self) -> None:
        for case, mutate, expected in (
            (
                "schema",
                lambda value: value.__setitem__("schema_version", 1),
                "schema_version must be 2",
            ),
            (
                "prerequisite",
                lambda value: value["prerequisites"].__setitem__(
                    "bootstrap_provenance_review",
                    {
                        "ready_for_final_human_review": False,
                        "record_status": "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING",
                        "state": "PENDING_PREREQUISITE_OBSERVED_INPUTS_ONLY",
                    },
                ),
                "approved/ready bootstrap provenance prerequisite",
            ),
        ):
            with self.subTest(case=case):
                fixture = GitFixture(self)
                report_document = json.loads(fixture.report().decode("utf-8"))
                mutate(report_document)
                report = (
                    json.dumps(report_document, indent=2, sort_keys=True) + "\n"
                ).encode("utf-8")
                document = fixture.pending_document()
                fixture.bind_source(document, report)
                fixture.write_record(document)
                record_commit = fixture.commit("record invalid report schema")

                errors, _ = validate_exact(fixture, record_commit, report)

                self.assertIn(expected, errors[0])

    def test_png_hash_crc_dimensions_and_privacy_metadata_are_structural_failures(self) -> None:
        for case, screenshot, expected in (
            ("truncated", full_window_png()[:-1], "PNG"),
            ("small", full_window_png(320, 200), "dimensions"),
            (
                "metadata",
                TOOL.PNG_SIGNATURE
                + _png_chunk(b"IHDR", struct.pack(">IIBBBBB", 640, 360, 8, 2, 0, 0, 0))
                + _png_chunk(b"tEXt", b"private=fixture")
                + _png_chunk(
                    b"IDAT",
                    zlib.compress(b"".join(b"\0" + b"\0\0\0" * 640 for _ in range(360))),
                )
                + _png_chunk(b"IEND", b""),
                "privacy-bearing",
            ),
        ):
            with self.subTest(case=case):
                fixture = GitFixture(self)
                document = fixture.pending_document()
                fixture.bind_readme(document, screenshot)
                fixture.write_record(document)
                record_commit = fixture.commit("record invalid screenshot")
                errors, _ = validate_exact(fixture, record_commit)
                self.assertTrue(errors)
                self.assertIn(expected, errors[0])

    def test_screenshot_hash_tampering_fails_before_pixel_judgment(self) -> None:
        fixture = GitFixture(self)
        document = fixture.pending_document()
        fixture.bind_readme(document, full_window_png())
        readme = document["final_g0_readme_visual_review"]
        assert isinstance(readme, dict)
        readme["screenshot_sha256"] = "f" * 64
        fixture.write_record(document)
        record_commit = fixture.commit("record wrong screenshot hash")

        errors, details = validate_exact(fixture, record_commit)

        self.assertIn("SHA-256 does not match", errors[0])
        self.assertEqual("HUMAN_ONLY", details["readme_visible_pixel_judgment"])


class AncestryAndInvalidationTests(unittest.TestCase):
    def test_unrelated_selected_commit_is_rejected(self) -> None:
        fixture = GitFixture(self)
        unrelated = fixture.unrelated_commit()
        unrelated_tree = fixture.rev_parse(f"{unrelated}^{{tree}}")
        report = fixture.report(unrelated, unrelated_tree)
        document = fixture.pending_document()
        fixture.bind_source(
            document,
            report,
            selected=unrelated,
            tree=unrelated_tree,
        )
        fixture.write_record(document)
        record_commit = fixture.commit("record unrelated review")

        errors, _ = validate_exact(fixture, record_commit, report)

        self.assertIn("not an ancestor", errors[0])

    def test_source_change_between_selected_and_record_invalidates_review(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        fixture.write(
            "src/main/java/example/Bootstrap.java", b"class Bootstrap { int changed; }\n"
        )
        fixture.commit("change distributable source")
        document = fixture.pending_document()
        fixture.bind_source(document, report)
        fixture.write_record(document)
        record_commit = fixture.commit("record stale source review")

        errors, _ = validate_exact(fixture, record_commit, report)

        self.assertIn("source review was invalidated", errors[0])
        self.assertIn("src/main/java/example/Bootstrap.java", errors[0])

    def test_build_logic_and_validator_changes_invalidate_source_review(self) -> None:
        for path in (
            "buildSrc/src/main/groovy/ArtifactMutator.groovy",
            "scripts/validate_v002_final_g0_review.py",
        ):
            with self.subTest(path=path):
                fixture = GitFixture(self)
                report = fixture.report()
                fixture.write(path, b"changed after selected review input\n")
                fixture.commit("change build or evidence validation input")
                document = fixture.pending_document()
                fixture.bind_source(document, report)
                fixture.write_record(document)
                record_commit = fixture.commit("record stale source review")

                errors, _ = validate_exact(fixture, record_commit, report)

                self.assertIn("source review was invalidated", errors[0])
                self.assertIn(path, errors[0])

    def test_source_review_allows_canonical_post_review_outputs(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        document = fixture.pending_document()
        fixture.bind_source(document, report)
        for path in (
            "CHANGELOG.md",
            "docs/decisions/ADR-005-V0.0.2-G4-APPLICABILITY.md",
            "docs/releases/v0.0.2/INSTALLATION.md",
            "docs/releases/v0.0.2/KNOWN-ISSUES.md",
            "docs/releases/v0.0.2/MANUAL-TEST.md",
            "docs/releases/v0.0.2/TEST-REPORT.md",
            "docs/releases/v0.0.2/checksums.txt",
            "docs/status/CURRENT_VERSION.md",
            "docs/status/GATE_STATUS.md",
            "docs/versions/V0.0.2-FORGE-BOOTSTRAP.md",
            "docs/work/v0.0.2-implementation-log.md",
            "docs/work/v0.0.2-test-machine-handoff.md",
            f"docs/releases/v0.0.2/evidence/g0-final/{fixture.selected}/review-notes.txt",
            "docs/releases/v0.0.2/evidence/client/evidence.json",
        ):
            fixture.write(path, f"fixture post-review output: {path}\n".encode())
        fixture.write_record(document)
        record_commit = fixture.commit("record canonical source review outputs")

        errors, details = validate_exact(fixture, record_commit, report)

        self.assertEqual([], errors)
        self.assertEqual(TOOL.APPROVED, details["source_review_outcome"])

    def test_source_review_rejects_noncanonical_evidence_mutations(self) -> None:
        for path in (
            "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
            "docs/releases/v0.0.2/evidence/g0-mechanical/main-jar-manifest.json",
            "docs/releases/v0.0.2/evidence/dedicated-server/first-run.txt",
        ):
            with self.subTest(path=path):
                fixture = GitFixture(self)
                report = fixture.report()
                fixture.write(path, b"changed noncanonical evidence input\n")
                fixture.commit("change protected evidence input")
                document = fixture.pending_document()
                fixture.bind_source(document, report)
                fixture.write_record(document)
                record_commit = fixture.commit("record stale source review")

                errors, _ = validate_exact(fixture, record_commit, report)

                self.assertIn("source review was invalidated", errors[0])
                self.assertIn(path, errors[0])

    def test_provenance_change_between_selected_and_record_invalidates_review(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        fixture.write("docs/provenance/record.md", b"changed provenance\n")
        fixture.commit("change provenance")
        document = fixture.pending_document()
        fixture.bind_source(document, report)
        fixture.write_record(document)
        record_commit = fixture.commit("record stale provenance review")

        errors, _ = validate_exact(fixture, record_commit, report)

        self.assertIn("source review was invalidated", errors[0])
        self.assertIn("docs/provenance/record.md", errors[0])

    def test_report_derived_inventory_path_is_also_invalidating(self) -> None:
        fixture = GitFixture(self)
        fixture.write("custom/distributable-input.txt", b"selected bytes\n")
        fixture.commit("add report-derived distributable input")
        selected = fixture.rev_parse("HEAD")
        tree = fixture.rev_parse(f"{selected}^{{tree}}")
        report_document = json.loads(fixture.report(selected, tree).decode("utf-8"))
        report_document["inventory"] = [{"path": "custom/distributable-input.txt"}]
        report_document["inventory_scope"]["repository_input_count"] = 1
        report = (json.dumps(report_document, indent=2, sort_keys=True) + "\n").encode(
            "utf-8"
        )
        fixture.write("custom/distributable-input.txt", b"changed after review\n")
        fixture.commit("change report-derived distributable input")
        document = fixture.pending_document()
        fixture.bind_source(document, report, selected=selected, tree=tree)
        fixture.write_record(document)
        record_commit = fixture.commit("record stale dynamic-inventory review")

        errors, _ = validate_exact(fixture, record_commit, report)

        self.assertIn("source review was invalidated", errors[0])
        self.assertIn("custom/distributable-input.txt", errors[0])

    def test_readme_change_between_capture_commit_and_record_invalidates_review(self) -> None:
        fixture = GitFixture(self)
        fixture.write("README.md", b"# Changed after capture\n")
        fixture.commit("change README after capture")
        document = fixture.pending_document()
        fixture.bind_readme(document, full_window_png())
        fixture.write_record(document)
        record_commit = fixture.commit("record stale README review")

        errors, _ = validate_exact(fixture, record_commit)

        self.assertIn("README visual review was invalidated", errors[0])
        self.assertIn("README.md", errors[0])

    def test_readme_visual_scope_ignores_unrelated_repository_changes(self) -> None:
        fixture = GitFixture(self)
        fixture.write(
            "buildSrc/src/main/groovy/ArtifactMutator.groovy",
            b"unrelated to README pixels\n",
        )
        fixture.commit("change non-README repository input")
        document = fixture.pending_document()
        fixture.bind_readme(document, full_window_png())
        fixture.write_record(document)
        record_commit = fixture.commit("record README-only review")

        errors, details = validate_exact(fixture, record_commit)

        self.assertEqual([], errors)
        self.assertEqual(TOOL.PENDING, details["source_review_outcome"])
        self.assertEqual(TOOL.APPROVED, details["readme_review_outcome"])

    def test_worktree_rejects_uncommitted_invalidating_source_or_readme(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        document = fixture.pending_document()
        report_path = fixture.bind_source(document, report)
        screenshot_path = fixture.bind_readme(document, full_window_png())
        fixture.write_record(document)
        fixture.stage(report_path, screenshot_path)
        fixture.write("README.md", b"# Uncommitted README change\n")
        fixture.write("src/main/java/example/Bootstrap.java", b"class Changed {}\n")

        with mock.patch.object(
            TOOL, "_reconstruct_source_report", return_value=report
        ), mock.patch.object(TOOL, "_require_approved_bootstrap_provenance"):
            errors, _ = TOOL.validate_final_g0_review_records(fixture.root)

        self.assertTrue(errors)
        self.assertIn("uncommitted or staged invalidating paths", errors[0])

    def test_worktree_rejects_uncommitted_build_logic_change(self) -> None:
        fixture = GitFixture(self)
        report = fixture.report()
        document = fixture.pending_document()
        report_path = fixture.bind_source(document, report)
        fixture.write_record(document)
        fixture.stage(report_path)
        changed_path = "buildSrc/src/main/groovy/ArtifactMutator.groovy"
        fixture.write(changed_path, b"uncommitted build mutation\n")

        with mock.patch.object(
            TOOL, "_reconstruct_source_report", return_value=report
        ), mock.patch.object(TOOL, "_require_approved_bootstrap_provenance"):
            errors, _ = TOOL.validate_final_g0_review_records(fixture.root)

        self.assertIn("uncommitted or staged invalidating paths", errors[0])
        self.assertIn(changed_path, errors[0])


class CliBoundaryTests(unittest.TestCase):
    def test_cli_reports_inputs_only_without_computing_gate_decision(self) -> None:
        fixture = GitFixture(self)
        fixture.write_record(fixture.pending_document())
        stdout = io.StringIO()
        stderr = io.StringIO()

        with redirect_stdout(stdout), redirect_stderr(stderr):
            result = TOOL.main(["--repository-root", str(fixture.root)])

        self.assertEqual(0, result)
        self.assertEqual("", stderr.getvalue())
        self.assertIn("INPUTS_ONLY", stdout.getvalue())
        self.assertIn("remain human decisions", stdout.getvalue())

    def test_git_command_rejects_repo_local_executable_and_disables_accelerators(self) -> None:
        fixture = GitFixture(self)
        fake_git = fixture.root / "fake-git.exe"
        fake_git.write_bytes(b"not executable\n")
        with mock.patch.object(TOOL.shutil, "which", return_value=str(fake_git)):
            with self.assertRaisesRegex(
                TOOL.FinalG0ReviewError, "must not be contained in the repository"
            ):
                TOOL._git_executable(fixture.root)

        real_git = TOOL._git_executable(fixture.root)
        with mock.patch.object(TOOL, "_git_executable", return_value=real_git):
            command = TOOL._git_command(fixture.root, ["status", "--porcelain"])
        joined = "\0".join(command)
        self.assertIn("core.commitGraph=false", joined)
        self.assertIn("core.fsmonitor=false", joined)
        self.assertIn("core.untrackedCache=false", joined)
        self.assertIn("diff.external=", joined)


if __name__ == "__main__":
    unittest.main()
