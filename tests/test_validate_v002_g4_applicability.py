from __future__ import annotations

import copy
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from unittest.mock import patch

from scripts import validate_v002_g4_applicability as MODULE

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "validate_v002_g4_applicability.py"


def blocks(metadata: dict, acceptance: dict) -> str:
    return (
        "# ADR fixture\n\n```json\n"
        + json.dumps(metadata, indent=2, sort_keys=True)
        + "\n```\n\n```json\n"
        + json.dumps(acceptance, indent=2, sort_keys=True)
        + "\n```\n"
    )


class G4ApplicabilityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.adr_text = (ROOT / MODULE.ADR_PATH).read_text(encoding="utf-8")

    @staticmethod
    def pending_records() -> tuple[dict, dict]:
        metadata = {
            "status": "PROPOSED",
            "date": "2026-08-27",
            "deciders": [],
            "owner": "sunthemoon",
            "target_version": "v0.0.2",
            "expires": "v0.1.0",
            "recovery_condition": "Define and review the required additional test.",
            "automated_failure_reminder": "Pending reviews keep G4 open.",
            "supersedes": "",
        }
        acceptance = {
            case_id: {
                "decision": "PENDING",
                "reviewed_by": "",
                "reviewed_at": "",
            }
            for case_id in MODULE.CASE_IDS
        }
        acceptance["final_status"] = "PROPOSED"
        return metadata, acceptance

    def validate(self, metadata: dict | None = None, acceptance: dict | None = None):
        pending_metadata, pending_acceptance = self.pending_records()
        return MODULE.validate_adr_text(
            blocks(
                copy.deepcopy(pending_metadata if metadata is None else metadata),
                copy.deepcopy(pending_acceptance if acceptance is None else acceptance),
            )
        )

    def accepted_records(self) -> tuple[dict, dict, dict]:
        metadata, acceptance = self.pending_records()
        metadata["status"] = "ACCEPTED"
        metadata["deciders"] = ["sunthemoon"]
        for case_id in MODULE.CASE_IDS:
            acceptance[case_id] = {
                "decision": "ACCEPT_NOT_APPLICABLE",
                "reviewed_by": "sunthemoon",
                "reviewed_at": "2026-08-30",
            }
        acceptance["final_status"] = "ACCEPTED"
        bundle = {
            "review_readiness": {"status": "READY_FOR_HUMAN_GATE_REVIEW"},
            "applicability_reviews": {
                case_id: {
                    **acceptance[case_id],
                    "proposed_status": "NOT_APPLICABLE",
                    "rationale": "fixture",
                    "notes": "reviewed",
                }
                for case_id in MODULE.CASE_IDS
            },
        }
        return metadata, acceptance, bundle

    def test_current_adr_is_valid_in_its_recorded_lifecycle_state(self) -> None:
        errors, details = MODULE.validate_adr_text(self.adr_text)
        self.assertEqual([], errors)
        self.assertIn(details["status"], MODULE.ADR_STATUSES)

    def test_synthetic_pending_state_is_valid(self) -> None:
        errors, details = self.validate()
        self.assertEqual([], errors)
        self.assertEqual("PROPOSED", details["status"])

    def test_proposed_state_allows_an_authorized_partial_review(self) -> None:
        metadata, acceptance = self.pending_records()
        metadata["deciders"] = ["sunthemoon"]
        acceptance[MODULE.CASE_IDS[0]] = {
            "decision": "REQUIRE_ADDITIONAL_TEST",
            "reviewed_by": "sunthemoon",
            "reviewed_at": "2026-08-30",
        }

        errors, details = self.validate(metadata, acceptance)

        self.assertEqual([], errors)
        self.assertEqual("PROPOSED", details["status"])

    def test_duplicate_json_key_is_rejected(self) -> None:
        metadata, acceptance = self.pending_records()
        text = blocks(metadata, acceptance).replace(
            '  "status": "PROPOSED",',
            '  "status": "PROPOSED",\n  "status": "ACCEPTED",',
            1,
        )
        errors, _ = MODULE.validate_adr_text(text)
        self.assertTrue(any("duplicate JSON key" in item for item in errors))

    def test_missing_or_extra_block_is_rejected(self) -> None:
        errors, _ = MODULE.validate_adr_text("# no records\n")
        self.assertIn(
            "ADR-005 must contain exactly two machine-readable JSON blocks", errors
        )

    def test_metadata_status_rejects_nonstring_json_values(self) -> None:
        for value in ([], {}, None, True, 1, 1.5):
            with self.subTest(value=value):
                metadata, acceptance = self.pending_records()
                metadata["status"] = value
                errors, _ = self.validate(metadata, acceptance)
                self.assertTrue(any("metadata status is invalid" in item for item in errors), errors)

    def test_case_decision_rejects_nonstring_json_values(self) -> None:
        for case_id in MODULE.CASE_IDS:
            for value in ([], {}, None, True, 1, 1.5):
                with self.subTest(case_id=case_id, value=value):
                    metadata, acceptance = self.pending_records()
                    acceptance[case_id]["decision"] = value
                    errors, _ = self.validate(metadata, acceptance)
                    self.assertIn(f"ADR acceptance {case_id} decision is invalid", errors)

    def test_parser_recursion_in_either_record_is_a_validation_error(self) -> None:
        metadata, acceptance = self.pending_records()
        deep_record = '{"nested":' + '[' * 3000 + '0' + ']' * 3000 + '}'
        for position in (0, 1):
            with self.subTest(position=position):
                payloads = [json.dumps(metadata), json.dumps(acceptance)]
                payloads[position] = deep_record
                text = '\n'.join(f'```json\n{payload}\n```' for payload in payloads)
                errors, details = MODULE.validate_adr_text(text)
                self.assertTrue(any(item.startswith("invalid ADR ") for item in errors), errors)
                self.assertEqual({}, details)

    def test_pending_decision_rejects_reviewer_metadata(self) -> None:
        _, acceptance = self.pending_records()
        acceptance[MODULE.CASE_IDS[0]]["reviewed_by"] = "someone"
        errors, _ = self.validate(acceptance=acceptance)
        self.assertTrue(any("pending decision" in item for item in errors))

    def test_nonpending_decision_requires_valid_reviewer_and_date(self) -> None:
        _, acceptance = self.pending_records()
        acceptance[MODULE.CASE_IDS[0]]["decision"] = "ACCEPT_NOT_APPLICABLE"
        errors, _ = self.validate(acceptance=acceptance)
        self.assertTrue(any("requires reviewer and date" in item for item in errors))

    def test_deciders_must_match_recorded_reviewers(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        metadata["deciders"] = []
        errors, _ = self.validate(metadata, acceptance)
        self.assertTrue(any("deciders differ" in item for item in errors))

    def test_unauthorized_case_reviewer_and_decider_are_rejected(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        metadata["deciders"] = ["unauthorized-reviewer"]
        for case_id in MODULE.CASE_IDS:
            acceptance[case_id]["reviewed_by"] = "unauthorized-reviewer"

        errors, _ = self.validate(metadata, acceptance)

        self.assertTrue(any("deciders are not authorized" in item for item in errors))
        self.assertTrue(any("reviewer is not authorized" in item for item in errors))

    def test_accepted_requires_every_proposal_to_be_accepted(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        acceptance[MODULE.CASE_IDS[0]]["decision"] = "REQUIRE_ADDITIONAL_TEST"
        errors, _ = self.validate(metadata, acceptance)
        self.assertTrue(any("ACCEPT_NOT_APPLICABLE for every case" in item for item in errors))

    def test_accepted_adr_requires_canonical_bundle(self) -> None:
        metadata, acceptance, _ = self.accepted_records()
        errors, details = self.validate(metadata, acceptance)
        self.assertEqual([], errors)
        self.assertIn(
            "ADR-005 cannot be ACCEPTED without canonical client evidence",
            MODULE.cross_check_bundle_record(details, None),
        )

    def test_matching_accepted_bundle_passes(self) -> None:
        metadata, acceptance, bundle = self.accepted_records()
        errors, details = self.validate(metadata, acceptance)
        self.assertEqual([], errors)
        self.assertEqual([], MODULE.cross_check_bundle_record(details, bundle))

    def test_bundle_decision_or_reviewer_mismatch_is_rejected(self) -> None:
        metadata, acceptance, bundle = self.accepted_records()
        errors, details = self.validate(metadata, acceptance)
        self.assertEqual([], errors)
        bundle["applicability_reviews"][MODULE.CASE_IDS[0]]["reviewed_by"] = "other"
        observed = MODULE.cross_check_bundle_record(details, bundle)
        self.assertTrue(any("reviewed_by" in item for item in observed))

    def test_bundle_requires_accepted_adr_and_readiness(self) -> None:
        errors, details = self.validate()
        self.assertEqual([], errors)
        _, acceptance = self.pending_records()
        bundle = {
            "review_readiness": {"status": "INCOMPLETE"},
            "applicability_reviews": {
                case_id: copy.deepcopy(acceptance[case_id])
                for case_id in MODULE.CASE_IDS
            },
        }
        observed = MODULE.cross_check_bundle_record(details, bundle)
        self.assertTrue(any("status ACCEPTED" in item for item in observed))
        self.assertTrue(any("not READY_FOR_HUMAN_GATE_REVIEW" in item for item in observed))


class G4ApplicabilityTreeTests(unittest.TestCase):
    """R32-03: drive the file wrapper, its reader and main() on temporary trees."""

    PENDING_LINE = (
        "[PENDING] ADR-005 is structurally valid and remains PROPOSED; "
        "G4 is not approved\n"
    )
    NO_EVIDENCE = "ADR-005 cannot be ACCEPTED without canonical client evidence"
    LINKED = (
        "cannot safely read ADR-005: ADR-005 path must not contain a symlink, "
        "junction, or reparse point"
    )

    def setUp(self) -> None:
        self.fresh_tree()

    def fresh_tree(self) -> None:
        self.root = self.temporary_directory()
        self.adr = self.root / MODULE.ADR_PATH
        self.bundle = self.root / MODULE.COMMITTED_BUNDLE

    def temporary_directory(self) -> Path:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        return Path(temporary.name)

    @staticmethod
    def adr_bytes(status: str = "PROPOSED") -> bytes:
        metadata, acceptance = G4ApplicabilityTests.pending_records()
        if status == "ACCEPTED":
            metadata["status"] = "ACCEPTED"
            metadata["deciders"] = ["sunthemoon"]
            for case_id in MODULE.CASE_IDS:
                acceptance[case_id] = {
                    "decision": "ACCEPT_NOT_APPLICABLE",
                    "reviewed_by": "sunthemoon",
                    "reviewed_at": "2026-08-30",
                }
            acceptance["final_status"] = "ACCEPTED"
        return blocks(metadata, acceptance).encode("utf-8")

    def write_adr(self, payload: bytes) -> None:
        self.adr.parent.mkdir(parents=True, exist_ok=True)
        self.adr.write_bytes(payload)

    def validate(self):
        return MODULE.validate_v002_g4_applicability(self.root)

    def run_main(self, root: Path | None = None) -> tuple[int, str, str]:
        output = io.StringIO()
        error_output = io.StringIO()
        with redirect_stdout(output), redirect_stderr(error_output):
            code = MODULE.main(["--repository-root", str(self.root if root is None else root)])
        return code, output.getvalue(), error_output.getvalue()

    def assert_unreadable(self, errors: list[str], details: dict, detail: str) -> None:
        self.assertEqual(1, len(errors), errors)
        self.assertTrue(
            errors[0].startswith(f"cannot safely read ADR-005: {detail}"), errors
        )
        self.assertEqual({}, details)

    def test_proposed_adr_without_a_bundle_is_valid(self) -> None:
        self.write_adr(self.adr_bytes())

        errors, details = self.validate()

        self.assertEqual([], errors)
        self.assertEqual("PROPOSED", details["status"])
        self.assertEqual([], details["deciders"])
        self.assertEqual(set(MODULE.CASE_IDS), set(details["cases"]))
        self.assertIsNone(details["canonical_bundle"])

    def test_missing_adr_file_is_a_read_error(self) -> None:
        errors, details = self.validate()
        self.assert_unreadable(errors, details, "")
        self.adr.mkdir(parents=True)
        errors, details = self.validate()
        self.assertEqual(
            ["cannot safely read ADR-005: ADR-005 must be an ordinary file"], errors
        )
        self.assertEqual({}, details)

    def test_oversized_adr_file_is_a_read_error(self) -> None:
        self.write_adr(b"#" * (MODULE.MAX_ADR_BYTES + 1))
        errors, details = self.validate()
        self.assertEqual(
            [f"cannot safely read ADR-005: ADR-005 exceeds {MODULE.MAX_ADR_BYTES} bytes"],
            errors,
        )
        self.assertEqual({}, details)

        payload = self.adr_bytes()
        self.write_adr(payload)
        self.assertEqual(payload, MODULE._read_stable_file(self.adr, len(payload), "ADR-005"))
        with self.assertRaisesRegex(ValueError, f"ADR-005 exceeds {len(payload) - 1} bytes"):
            MODULE._read_stable_file(self.adr, len(payload) - 1, "ADR-005")
        with patch.object(MODULE, "MAX_ADR_BYTES", len(payload)):
            self.assertEqual([], self.validate()[0])
        with patch.object(MODULE, "MAX_ADR_BYTES", len(payload) - 1):
            self.assert_unreadable(*self.validate(), f"ADR-005 exceeds {len(payload) - 1} bytes")

    def test_non_utf8_adr_file_is_a_read_error(self) -> None:
        self.write_adr(self.adr_bytes().replace(b"# ADR fixture", b"# ADR \xff fixture"))
        errors, details = self.validate()
        self.assert_unreadable(errors, details, "'utf-8' codec can't decode byte 0xff")

    def test_adr_file_that_changes_while_read_is_rejected(self) -> None:
        self.write_adr(self.adr_bytes())
        real_fstat = os.fstat

        def grown_fstat(descriptor: int) -> os.stat_result:
            observed = real_fstat(descriptor)
            values = list(observed)
            values[6] = observed.st_size + 1
            return os.stat_result(values)

        with patch.object(MODULE.os, "fstat", grown_fstat):
            errors, details = self.validate()
        self.assert_unreadable(errors, details, "ADR-005 changed while it was read")

    def test_symlinked_adr_file_is_rejected(self) -> None:
        target = self.temporary_directory() / "ADR-005.md"
        target.write_bytes(self.adr_bytes())
        self.adr.parent.mkdir(parents=True)
        try:
            self.adr.symlink_to(target)
        except OSError as exc:
            self.skipTest(f"file symlinks are unavailable: {exc}")
        try:
            errors, details = self.validate()
            # The reader keeps its own final-component guard.
            with self.assertRaisesRegex(ValueError, "ADR-005 must be an ordinary file"):
                MODULE._read_stable_file(self.adr, MODULE.MAX_ADR_BYTES, "ADR-005")
        finally:
            self.adr.unlink()
        self.assertEqual([self.LINKED], errors)
        self.assertEqual({}, details)
        self.assertEqual(self.adr_bytes(), target.read_bytes())

    def test_adr_reached_through_a_directory_symlink_is_rejected(self) -> None:
        outside = self.temporary_directory()
        (outside / MODULE.ADR_PATH.name).write_bytes(self.adr_bytes())
        decisions = self.adr.parent
        decisions.parent.mkdir(parents=True)
        try:
            decisions.symlink_to(outside, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f"directory symlinks are unavailable: {exc}")
        try:
            errors, details = self.validate()
        finally:
            decisions.unlink()
        self.assertEqual([self.LINKED], errors)
        self.assertEqual({}, details)
        self.assertTrue((outside / MODULE.ADR_PATH.name).is_file())

    @unittest.skipUnless(os.name == "nt", "Windows junction coverage")
    def test_adr_reached_through_a_windows_junction_is_rejected(self) -> None:
        outside = self.temporary_directory()
        (outside / MODULE.ADR_PATH.name).write_bytes(self.adr_bytes())
        decisions = self.adr.parent
        decisions.parent.mkdir(parents=True)
        result = subprocess.run(
            [
                str(Path(os.environ["SystemRoot"]) / "System32" / "cmd.exe"),
                "/d",
                "/c",
                "mklink",
                "/J",
                str(decisions),
                str(outside),
            ],
            capture_output=True,
            timeout=30,
        )
        if result.returncode != 0:
            self.skipTest(f"junction creation is unavailable: {result.stderr!r}")
        try:
            self.assertTrue(decisions.lstat().st_file_attributes & MODULE.FILE_ATTRIBUTE_REPARSE_POINT)
            errors, details = self.validate()
        finally:
            # Remove only this owned junction, never its target contents.
            decisions.rmdir()
        self.assertEqual([self.LINKED], errors)
        self.assertEqual({}, details)
        self.assertTrue((outside / MODULE.ADR_PATH.name).is_file())

    def test_absent_bundle_is_allowed_only_for_a_proposed_adr(self) -> None:
        self.write_adr(self.adr_bytes("ACCEPTED"))

        errors, details = self.validate()

        self.assertEqual([self.NO_EVIDENCE], errors)
        self.assertEqual("ACCEPTED", details["status"])
        self.assertIsNone(details["canonical_bundle"])

    def test_invalid_bundle_is_reported_and_not_bound(self) -> None:
        def empty_directory() -> None:
            self.bundle.mkdir(parents=True)

        def malformed_record() -> None:
            self.bundle.mkdir(parents=True)
            (self.bundle / MODULE.RECORD_NAME).write_bytes(b"{")

        def plain_file() -> None:
            self.bundle.parent.mkdir(parents=True)
            self.bundle.write_bytes(b"not a bundle\n")

        for label, make_bundle in (
            ("empty directory", empty_directory),
            ("malformed record", malformed_record),
            ("plain file", plain_file),
        ):
            for status, expected_tail in (("PROPOSED", []), ("ACCEPTED", [self.NO_EVIDENCE])):
                with self.subTest(bundle=label, status=status):
                    self.fresh_tree()
                    self.write_adr(self.adr_bytes(status))
                    make_bundle()

                    errors, details = self.validate()

                    self.assertEqual(1 + len(expected_tail), len(errors), errors)
                    self.assertTrue(
                        errors[0].startswith("canonical client evidence is invalid: "),
                        errors,
                    )
                    self.assertEqual(expected_tail, errors[1:])
                    self.assertEqual(status, details["status"])
                    self.assertIsNone(details["canonical_bundle"])

    def test_bundle_that_cannot_be_inspected_is_an_error(self) -> None:
        self.write_adr(self.adr_bytes())
        bundle = self.root.resolve() / MODULE.COMMITTED_BUNDLE
        real_lstat = Path.lstat

        def guarded_lstat(path: Path, *arguments, **keywords):
            if path == bundle:
                raise PermissionError(13, "denied by test", str(path))
            return real_lstat(path, *arguments, **keywords)

        with patch.object(Path, "lstat", guarded_lstat):
            errors, details = self.validate()

        self.assertEqual(1, len(errors), errors)
        self.assertTrue(
            errors[0].startswith("cannot safely inspect canonical client evidence: "),
            errors,
        )
        self.assertIn("denied by test", errors[0])
        self.assertEqual("PROPOSED", details["status"])
        self.assertNotIn("canonical_bundle", details)

    def test_adr_errors_stop_before_the_bundle_is_read(self) -> None:
        self.write_adr(b"# ADR without records\n")
        self.bundle.mkdir(parents=True)

        errors, details = self.validate()

        self.assertEqual(
            ["ADR-005 must contain exactly two machine-readable JSON blocks"], errors
        )
        self.assertEqual({}, details)

    def test_main_reports_a_passing_tree_and_returns_zero(self) -> None:
        self.write_adr(self.adr_bytes())
        self.assertEqual((0, self.PENDING_LINE, ""), self.run_main())

    def test_main_reports_each_error_of_a_failing_tree_and_returns_one(self) -> None:
        code, output, error_output = self.run_main()
        self.assertEqual(1, code)
        self.assertEqual("", output)
        self.assertEqual(1, len(error_output.splitlines()), error_output)
        self.assertTrue(error_output.startswith("[FAIL] cannot safely read ADR-005: "))

        self.write_adr(self.adr_bytes("ACCEPTED"))
        self.bundle.mkdir(parents=True)
        code, output, error_output = self.run_main()
        self.assertEqual(1, code)
        self.assertEqual("", output)
        lines = error_output.splitlines()
        self.assertEqual(2, len(lines), error_output)
        self.assertTrue(lines[0].startswith("[FAIL] canonical client evidence is invalid: "))
        self.assertEqual(f"[FAIL] {self.NO_EVIDENCE}", lines[1])

    def test_main_reports_the_accepted_repository_record(self) -> None:
        errors, details = MODULE.validate_v002_g4_applicability(ROOT)
        self.assertEqual([], errors)
        self.assertEqual("ACCEPTED", details["status"])
        self.assertEqual(
            (MODULE.COMMITTED_BUNDLE / MODULE.RECORD_NAME).as_posix(),
            details["canonical_bundle"],
        )
        self.assertEqual(
            (0, "[PASS] ADR-005 is ACCEPTED and matches canonical client evidence\n", ""),
            self.run_main(ROOT),
        )

    def test_isolated_script_exit_codes(self) -> None:
        self.write_adr(self.adr_bytes())
        failing = self.temporary_directory()
        for root, expected_code, expected_output, expected_error in (
            (self.root, 0, self.PENDING_LINE, ""),
            (failing, 1, "", "[FAIL] cannot safely read ADR-005: "),
        ):
            with self.subTest(root=root.name, code=expected_code):
                result = subprocess.run(
                    [sys.executable, "-B", "-I", "-S", str(SCRIPT), "--repository-root", str(root)],
                    capture_output=True,
                    text=True,
                    timeout=120,
                )
                self.assertEqual(expected_code, result.returncode, result.stderr)
                self.assertEqual(expected_output, result.stdout.replace("\r\n", "\n"))
                self.assertTrue(result.stderr.startswith(expected_error), result.stderr)
                if not expected_error:
                    self.assertEqual("", result.stderr)


if __name__ == "__main__":
    unittest.main()
