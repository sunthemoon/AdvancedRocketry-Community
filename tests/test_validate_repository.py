import hashlib
import io
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from scripts import validate_repository as repository_validator
from scripts.validate_repository import (
    APPROVED_RECORD_STATUS,
    COMMITTED_BUNDLE,
    GRADLE_WRAPPER_PATH,
    ROOT,
    Results,
    check_optional_v002_client_evidence,
    check_package_checksums,
    check_v002_final_g0_review,
    check_v002_g4_applicability,
    find_unlisted_v002_resources,
    is_audited_v001_evidence,
    is_approved_gradle_wrapper,
    is_approved_third_party_license,
    markdown_link_errors,
    normalize_link_target,
    parse_current_identity,
    read_bounded_bytes,
    repository_files,
    tracked_markdown_files,
    validate_v002_gate_status_text,
    validate_v010_gate_status_text,
    validate_forge_workflow_text,
    validate_repository_workflow_text,
)


def v010_gate_document(
    *,
    status: str = "IN_PROGRESS",
    overall: str = "IN_PROGRESS",
    g0: str = "IN_PROGRESS",
    g1: str = "IN_PROGRESS",
    g2: str = "IN_PROGRESS",
    g3: str = "IN_PROGRESS",
    g4: str = "NOT_STARTED",
    g8: str = "NOT_STARTED",
    g9: str = "NOT_STARTED",
    reviewer: str = "",
    reviewed_at: str = "",
) -> str:
    return f"""# GATE_STATUS

```yaml
version: v0.1.0
status: {status}
gates:
  G0: {g0}
  G1: {g1}
  G2: {g2}
  G3: {g3}
  G4: {g4}
  G5: NOT_APPLICABLE
  G6: NOT_APPLICABLE
  G7: NOT_APPLICABLE
  G8: {g8}
  G9: {g9}
overall: {overall}
human_approved_by: "{reviewer}"
human_approved_at: "{reviewed_at}"
```
"""


class RepositoryCliTests(unittest.TestCase):
    def test_help_runs_with_isolated_python(self) -> None:
        script = ROOT / "scripts" / "validate_repository.py"

        completed = subprocess.run(
            [sys.executable, "-I", "-S", str(script), "--help"],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="strict",
        )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("--require-approved-identity", completed.stdout)


class ResultsReportTests(unittest.TestCase):
    def test_pending_results_are_not_reported_as_passes_or_warnings(self) -> None:
        results = Results()
        results.passed("complete")
        results.pending_state("human evidence required")
        output = io.StringIO()

        with redirect_stdout(output):
            results.print_report()

        self.assertIn("[PASS] complete", output.getvalue())
        self.assertIn("[PENDING] human evidence required", output.getvalue())
        self.assertIn(
            "Summary: 1 passed, 1 pending, 0 warnings, 0 failed",
            output.getvalue(),
        )


class IdentityParsingTests(unittest.TestCase):
    def test_current_draft_is_not_confused_with_approved_example(self) -> None:
        document = '''
Example:
identity_status: "APPROVED"

当前值：

reviewed_by: ""
reviewed_at: ""
identity_status: "DRAFT"
'''

        self.assertEqual(("DRAFT", None, None), parse_current_identity(document))

    def test_current_approved_values_are_parsed(self) -> None:
        document = '''
当前值：

reviewed_by: "sunthemoon"
reviewed_at: "2026-08-26"
identity_status: "APPROVED"
'''

        self.assertEqual(
            ("APPROVED", "sunthemoon", "2026-08-26"),
            parse_current_identity(document),
        )


class MarkdownTargetTests(unittest.TestCase):
    def test_angle_brackets_and_url_encoding_are_normalized(self) -> None:
        self.assertEqual(
            "docs/My File.md#section",
            normalize_link_target("<docs/My%20File.md#section>"),
        )

    def test_optional_markdown_title_is_removed(self) -> None:
        self.assertEqual(
            "docs/file.md",
            normalize_link_target('docs/file.md "title"'),
        )


class MarkdownLinkInventoryTests(unittest.TestCase):
    def initialize_repository(self, root: Path) -> None:
        subprocess.run(
            ["git", "init", "--quiet"],
            cwd=root,
            check=True,
            capture_output=True,
        )

    def add(self, root: Path, *paths: str) -> None:
        subprocess.run(
            ["git", "add", "--", *paths],
            cwd=root,
            check=True,
            capture_output=True,
        )

    def test_ignored_packet_markdown_is_not_authoritative(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.initialize_repository(root)
            (root / ".gitignore").write_text("build/\n", encoding="utf-8")
            (root / "docs").mkdir()
            (root / "docs/target.md").write_text("# Target\n", encoding="utf-8")
            (root / "README.md").write_text(
                "[tracked target](docs/target.md)\n", encoding="utf-8"
            )
            packet = root / "build/v0.0.2-g0-review-packet/files"
            packet.mkdir(parents=True)
            (packet / "README.md").write_text(
                "[ignored broken target](missing.md)\n", encoding="utf-8"
            )
            self.add(root, ".gitignore", "README.md", "docs/target.md")

            inventory = tracked_markdown_files(root)
            relative = [path.relative_to(root).as_posix() for path in inventory]
            errors, checked = markdown_link_errors(root, inventory)

            self.assertEqual(["README.md", "docs/target.md"], relative)
            self.assertEqual([], errors)
            self.assertEqual(1, checked)

    def test_tracked_broken_markdown_link_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.initialize_repository(root)
            (root / "docs").mkdir()
            (root / "docs/tracked.md").write_text(
                "[missing](missing.md)\n", encoding="utf-8"
            )
            self.add(root, "docs/tracked.md")

            inventory = tracked_markdown_files(root)
            errors, checked = markdown_link_errors(root, inventory)

            self.assertEqual(["docs/tracked.md:1 -> missing.md"], errors)
            self.assertEqual(1, checked)

    def test_pathspec_environment_cannot_disable_markdown_inventory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.initialize_repository(root)
            (root / "tracked.md").write_text("# Tracked\n", encoding="utf-8")
            self.add(root, "tracked.md")

            with patch.dict(os.environ, {"GIT_LITERAL_PATHSPECS": "1"}):
                inventory = tracked_markdown_files(root)

            self.assertEqual(
                ["tracked.md"],
                [path.relative_to(root).as_posix() for path in inventory],
            )

    def test_uppercase_markdown_extension_is_included(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.initialize_repository(root)
            (root / "UPPER.MD").write_text(
                "[missing](missing.md)\n", encoding="utf-8"
            )
            self.add(root, "UPPER.MD")

            inventory = tracked_markdown_files(root)
            errors, checked = markdown_link_errors(root, inventory)

            self.assertEqual(["UPPER.MD:1 -> missing.md"], errors)
            self.assertEqual(1, checked)

    def test_markdown_link_and_error_counts_are_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "links.md"
            path.write_text("[x](missing)\n" * 10, encoding="utf-8")

            with patch("scripts.validate_repository.MAX_MARKDOWN_LINKS", 3):
                link_errors, checked = markdown_link_errors(root, [path])
            self.assertEqual(3, checked)
            self.assertEqual(4, len(link_errors))
            self.assertIn("link-count limit", link_errors[-1])

            with patch("scripts.validate_repository.MAX_MARKDOWN_ERRORS", 2):
                bounded_errors, checked = markdown_link_errors(root, [path])
            self.assertEqual(3, checked)
            self.assertEqual(3, len(bounded_errors))
            self.assertIn("stopped after 2 errors", bounded_errors[-1])

    def test_markdown_aggregate_bytes_and_lines_are_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "prose.md"
            path.write_text("a\nb\nc\n", encoding="utf-8")

            with patch("scripts.validate_repository.MAX_MARKDOWN_TOTAL_BYTES", 4):
                byte_errors, checked = markdown_link_errors(root, [path])
            self.assertEqual(0, checked)
            self.assertIn("aggregate byte limit", byte_errors[0])

            with patch("scripts.validate_repository.MAX_MARKDOWN_LINES_PER_FILE", 2):
                line_errors, checked = markdown_link_errors(root, [path])
            self.assertEqual(0, checked)
            self.assertIn("line-count limit", line_errors[0])


class BoundedRepositoryInputTests(unittest.TestCase):
    def initialize_repository(self, root: Path) -> None:
        subprocess.run(
            ["git", "init", "--quiet"],
            cwd=root,
            check=True,
            capture_output=True,
        )

    def test_repository_inventory_includes_tracked_and_nonignored_untracked_files(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.initialize_repository(root)
            (root / ".gitignore").write_text("ignored.txt\n", encoding="utf-8")
            (root / "tracked.txt").write_text("tracked\n", encoding="utf-8")
            (root / "untracked.txt").write_text("untracked\n", encoding="utf-8")
            (root / "ignored.txt").write_text("ignored\n", encoding="utf-8")
            subprocess.run(
                ["git", "add", "--", ".gitignore", "tracked.txt"],
                cwd=root,
                check=True,
                capture_output=True,
            )

            inventory = {
                path.relative_to(root).as_posix() for path in repository_files(root)
            }

            self.assertEqual(
                {".gitignore", "tracked.txt", "untracked.txt"}, inventory
            )

    def test_repository_inventory_rejects_output_over_the_byte_limit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.initialize_repository(root)
            (root / "tracked.txt").write_text("tracked\n", encoding="utf-8")
            subprocess.run(
                ["git", "add", "--", "tracked.txt"],
                cwd=root,
                check=True,
                capture_output=True,
            )

            with patch(
                "scripts.validate_repository.MAX_REPOSITORY_INVENTORY_BYTES", 1
            ):
                with self.assertRaisesRegex(ValueError, "exceeds the byte limit"):
                    repository_files(root)

    def test_repository_local_git_candidate_is_rejected_before_execution(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fake_git = root / "git.exe"
            fake_git.write_bytes(b"not an executable")

            with (
                patch(
                    "scripts.validate_repository.shutil.which",
                    return_value=str(fake_git),
                ),
                patch("scripts.validate_repository.subprocess.Popen") as popen,
            ):
                with self.assertRaisesRegex(ValueError, "contained in the repository"):
                    repository_files(root)

            popen.assert_not_called()

    def test_bounded_binary_read_rejects_oversized_input_before_full_read(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "oversized.bin"
            path.write_bytes(b"12345")

            with self.assertRaisesRegex(ValueError, "exceeds 4 bytes"):
                read_bounded_bytes(
                    path, 4, "test input", trusted_root=root
                )

    def test_bounded_binary_read_rejects_linked_parent_component(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            linked = root / "linked"
            linked.mkdir()
            path = linked / "input.txt"
            path.write_text("external fixture\n", encoding="utf-8")

            with patch(
                "scripts.validate_repository._is_link",
                side_effect=lambda candidate: candidate == linked,
            ):
                with self.assertRaisesRegex(ValueError, "path must not contain"):
                    read_bounded_bytes(
                        path,
                        1024,
                        "test input",
                        trusted_root=root,
                    )


class PlanningPackageChecksumTests(unittest.TestCase):
    def test_empty_manifest_cannot_pass_as_zero_verified_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "unlisted.txt").write_text("unlisted\n", encoding="utf-8")
            (root / "PACKAGE-SHA256SUMS.txt").write_text("\n", encoding="utf-8")
            results = Results()

            check_package_checksums(root, results)

            self.assertEqual([], results.passes)
            self.assertEqual(
                ["Planning package checksum list contains no entries"],
                results.failures,
            )

    def test_valid_bounded_package_manifest_passes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            payload = b"package fixture\n"
            (root / "payload.txt").write_bytes(payload)
            (root / "PACKAGE-SHA256SUMS.txt").write_text(
                f"{hashlib.sha256(payload).hexdigest()}  payload.txt\n",
                encoding="utf-8",
            )
            results = Results()

            check_package_checksums(root, results)

            self.assertEqual([], results.failures)
            self.assertEqual(1, len(results.passes))

    def test_package_manifest_rejects_parent_traversal(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            root = parent / "package"
            root.mkdir()
            outside = parent / "outside.txt"
            outside.write_text("outside\n", encoding="utf-8")
            (root / "PACKAGE-SHA256SUMS.txt").write_text(
                f"{hashlib.sha256(outside.read_bytes()).hexdigest()}  ../outside.txt\n",
                encoding="utf-8",
            )
            results = Results()

            check_package_checksums(root, results)

            self.assertTrue(
                any("unsafe package path" in failure for failure in results.failures),
                results.failures,
            )

    def test_package_manifest_and_target_sizes_are_bounded(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            payload = b"12345"
            (root / "payload.bin").write_bytes(payload)
            manifest = root / "PACKAGE-SHA256SUMS.txt"
            manifest.write_text(
                f"{hashlib.sha256(payload).hexdigest()}  payload.bin\n",
                encoding="utf-8",
            )

            manifest_results = Results()
            with patch(
                "scripts.validate_repository.MAX_PACKAGE_CHECKSUM_LIST_BYTES", 4
            ):
                check_package_checksums(root, manifest_results)
            self.assertTrue(
                any("exceeds 4 bytes" in failure for failure in manifest_results.failures),
                manifest_results.failures,
            )

            target_results = Results()
            with patch("scripts.validate_repository.MAX_PACKAGE_FILE_BYTES", 4):
                check_package_checksums(root, target_results)
            self.assertTrue(
                any("exceeds 4 bytes" in failure for failure in target_results.failures),
                target_results.failures,
            )

            aggregate_results = Results()
            with patch("scripts.validate_repository.MAX_PACKAGE_TOTAL_BYTES", 4):
                check_package_checksums(root, aggregate_results)
            self.assertTrue(
                any(
                    "aggregate byte limit" in failure
                    for failure in aggregate_results.failures
                ),
                aggregate_results.failures,
            )

    def test_package_target_replacement_cannot_bypass_aggregate_limit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / "payload.bin"
            path.write_bytes(b"1")
            replacement = b"12345"
            (root / "PACKAGE-SHA256SUMS.txt").write_text(
                f"{hashlib.sha256(replacement).hexdigest()}  payload.bin\n",
                encoding="utf-8",
            )
            original_inspect = repository_validator._inspect_bounded_file

            def replace_after_inspection(*args, **kwargs):
                inspected = original_inspect(*args, **kwargs)
                if Path(args[0]).name == "payload.bin":
                    path.write_bytes(replacement)
                return inspected

            results = Results()
            with (
                patch("scripts.validate_repository.MAX_PACKAGE_TOTAL_BYTES", 4),
                patch(
                    "scripts.validate_repository._inspect_bounded_file",
                    side_effect=replace_after_inspection,
                ),
            ):
                check_package_checksums(root, results)

            self.assertEqual([], results.passes)
            self.assertTrue(
                any("aggregate byte limit" in failure for failure in results.failures),
                results.failures,
            )


class EvidenceAssetTests(unittest.TestCase):
    def setUp(self) -> None:
        self.relative = "docs/releases/v0.0.1/evidence/github-home-authenticated.jpg"
        self.content = b"\xff\xd8\xffauthenticated evidence"
        digest = hashlib.sha256(self.content).hexdigest()
        self.index = (
            "| [Repository homepage](github-home-authenticated.jpg) | evidence | "
            f"`{digest}` |"
        )

    def test_indexed_jpeg_with_matching_hash_is_allowed(self) -> None:
        self.assertTrue(
            is_audited_v001_evidence(self.relative, self.content, self.index)
        )

    def test_changed_evidence_content_is_rejected(self) -> None:
        self.assertFalse(
            is_audited_v001_evidence(
                self.relative, self.content + b"changed", self.index
            )
        )

    def test_image_outside_release_evidence_directory_is_rejected(self) -> None:
        self.assertFalse(
            is_audited_v001_evidence(
                "src/main/resources/texture.jpg", self.content, self.index
            )
        )


class ApprovedBinaryTests(unittest.TestCase):
    def test_pinned_gradle_wrapper_is_allowed(self) -> None:
        content = (ROOT / GRADLE_WRAPPER_PATH).read_bytes()

        self.assertTrue(is_approved_gradle_wrapper(GRADLE_WRAPPER_PATH, content))

    def test_other_jar_path_is_rejected(self) -> None:
        content = (ROOT / GRADLE_WRAPPER_PATH).read_bytes()

        self.assertFalse(is_approved_gradle_wrapper("libs/dependency.jar", content))

    def test_changed_wrapper_is_rejected(self) -> None:
        content = (ROOT / GRADLE_WRAPPER_PATH).read_bytes()

        self.assertFalse(
            is_approved_gradle_wrapper(GRADLE_WRAPPER_PATH, content + b"changed")
        )


class ApprovedThirdPartyLicenseTests(unittest.TestCase):
    def test_pinned_license_copies_are_allowed(self) -> None:
        for relative in (
            "docs/licenses/GRADLE-8.1.1-LICENSE.txt",
            "docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt",
        ):
            with self.subTest(relative=relative):
                self.assertTrue(
                    is_approved_third_party_license(
                        relative, (ROOT / relative).read_bytes()
                    )
                )

    def test_changed_license_copy_is_rejected(self) -> None:
        relative = "docs/licenses/GRADLE-8.1.1-LICENSE.txt"

        self.assertFalse(
            is_approved_third_party_license(
                relative, (ROOT / relative).read_bytes() + b"changed"
            )
        )

    def test_unlisted_license_path_is_rejected(self) -> None:
        self.assertFalse(is_approved_third_party_license("docs/licenses/other.txt", b""))


class V010GateStatusTests(unittest.TestCase):
    def test_honest_in_progress_status_accepts_pending_provenance(self) -> None:
        self.assertEqual(
            [],
            validate_v010_gate_status_text(
                v010_gate_document(),
                asset_details={
                    "review_status": "PENDING_HUMAN_REVIEW",
                    "resource_count": 37,
                },
            ),
        )

    def test_g0_pass_requires_approved_provenance(self) -> None:
        errors = validate_v010_gate_status_text(
            v010_gate_document(g0="PASS"),
            asset_details={
                "review_status": "PENDING_HUMAN_REVIEW",
                "resource_count": 37,
            },
        )
        self.assertTrue(any("G0 cannot be PASS" in error for error in errors))

    def test_passed_status_requires_all_gates_and_owner_approval(self) -> None:
        errors = validate_v010_gate_status_text(
            v010_gate_document(
                status="PASSED",
                overall="PASSED",
                g0="PASS",
                g1="PASS",
                g2="PASS",
                g3="PASS",
                g4="PASS",
                g8="PASS",
                g9="PASS",
            ),
            asset_details={"review_status": "APPROVED", "resource_count": 37},
        )
        self.assertTrue(any("G8 cannot be PASS" in error for error in errors))
        self.assertTrue(any("human approval" in error for error in errors))


class V002ResourceInventoryTests(unittest.TestCase):
    def test_all_current_text_and_binary_resources_are_allowlisted(self) -> None:
        paths = [
            path.relative_to(ROOT).as_posix()
            for resource_root in (
                ROOT / "src/main/resources",
                ROOT / "src/generated/resources",
            )
            for path in resource_root.rglob("*")
            if path.is_file()
        ]

        self.assertEqual([], find_unlisted_v002_resources(paths))

    def test_unlisted_json_resource_is_rejected_regardless_of_extension(self) -> None:
        path = "src/main/resources/assets/example/lang/en_us.json"

        self.assertEqual([path], find_unlisted_v002_resources([path]))

    def test_mdk_resource_targets_are_part_of_the_allowlist(self) -> None:
        self.assertEqual(
            [],
            find_unlisted_v002_resources(
                [
                    "src/main/resources/META-INF/mods.toml",
                    "src/main/resources/pack.mcmeta",
                ]
            ),
        )

    def test_excluded_datagen_cache_is_not_a_distributable_resource(self) -> None:
        self.assertEqual(
            [],
            find_unlisted_v002_resources(
                ["src/generated/resources/.cache/datagen-state"]
            ),
        )


class ClientEvidenceProvenanceTests(unittest.TestCase):
    def test_pending_provenance_without_bundle_remains_acceptable(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            results = Results()
            with (
                patch("scripts.validate_repository.ROOT", root),
                patch(
                    "scripts.validate_repository.validate_bootstrap_provenance"
                ) as validate_provenance,
            ):
                check_optional_v002_client_evidence(results)

            self.assertEqual([], results.failures)
            self.assertEqual([], results.passes)
            self.assertEqual(1, len(results.pending))
            validate_provenance.assert_not_called()

    def test_broken_reparse_bundle_path_is_rejected_instead_of_treated_as_absent(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = root / COMMITTED_BUNDLE
            results = Results()
            with (
                patch("scripts.validate_repository.ROOT", root),
                patch.object(type(bundle), "lstat", return_value=object()),
                patch("scripts.validate_repository._is_link", return_value=True),
                patch(
                    "scripts.validate_repository.validate_bootstrap_provenance"
                ) as validate_provenance,
                patch(
                    "scripts.validate_repository.validate_bundle"
                ) as validate_client_bundle,
            ):
                check_optional_v002_client_evidence(results)

            self.assertTrue(
                any("reparse point" in failure for failure in results.failures),
                results.failures,
            )
            self.assertEqual([], results.passes)
            validate_provenance.assert_not_called()
            validate_client_bundle.assert_not_called()

    def test_canonical_bundle_lstat_error_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = root / COMMITTED_BUNDLE
            results = Results()
            with (
                patch("scripts.validate_repository.ROOT", root),
                patch.object(
                    type(bundle),
                    "lstat",
                    side_effect=OSError("inspection denied"),
                ),
                patch(
                    "scripts.validate_repository.validate_bootstrap_provenance"
                ) as validate_provenance,
            ):
                check_optional_v002_client_evidence(results)

            self.assertTrue(
                any("Cannot safely inspect" in failure for failure in results.failures),
                results.failures,
            )
            self.assertEqual([], results.passes)
            validate_provenance.assert_not_called()

    def test_bundle_is_rejected_until_provenance_is_approved(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / COMMITTED_BUNDLE).mkdir(parents=True)
            results = Results()
            with (
                patch("scripts.validate_repository.ROOT", root),
                patch(
                    "scripts.validate_repository.validate_bootstrap_provenance",
                    return_value=(
                        [],
                        {
                            "review_status": (
                                "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING"
                            )
                        },
                    ),
                ),
                patch(
                    "scripts.validate_repository.validate_bundle"
                ) as validate_client_bundle,
            ):
                check_optional_v002_client_evidence(results)

            self.assertTrue(
                any(APPROVED_RECORD_STATUS in failure for failure in results.failures),
                results.failures,
            )
            validate_client_bundle.assert_not_called()

    def test_approved_provenance_allows_bundle_readiness_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = root / COMMITTED_BUNDLE
            bundle.mkdir(parents=True)
            results = Results()
            with (
                patch("scripts.validate_repository.ROOT", root),
                patch(
                    "scripts.validate_repository.validate_bootstrap_provenance",
                    return_value=([], {"review_status": APPROVED_RECORD_STATUS}),
                ),
                patch(
                    "scripts.validate_repository.validate_bundle",
                    return_value=(
                        [],
                        {"review_readiness": {"status": "READY_FOR_HUMAN_GATE_REVIEW"}},
                    ),
                ) as validate_client_bundle,
            ):
                check_optional_v002_client_evidence(results)

            self.assertEqual([], results.failures)
            self.assertEqual(1, len(results.passes))
            validate_client_bundle.assert_called_once_with(
                bundle,
                repository_root=root,
                require_acceptance_ready=True,
            )

    def test_incomplete_canonical_bundle_is_rejected_defensively(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bundle = root / COMMITTED_BUNDLE
            bundle.mkdir(parents=True)
            results = Results()
            with (
                patch("scripts.validate_repository.ROOT", root),
                patch(
                    "scripts.validate_repository.validate_bootstrap_provenance",
                    return_value=([], {"review_status": APPROVED_RECORD_STATUS}),
                ),
                patch(
                    "scripts.validate_repository.validate_bundle",
                    return_value=(
                        [],
                        {"review_readiness": {"status": "INCOMPLETE"}},
                    ),
                ) as validate_client_bundle,
            ):
                check_optional_v002_client_evidence(results)

            self.assertTrue(
                any("not acceptance-ready" in failure for failure in results.failures),
                results.failures,
            )
            validate_client_bundle.assert_called_once_with(
                bundle,
                repository_root=root,
                require_acceptance_ready=True,
            )


class G4ApplicabilityRepositoryCheckTests(unittest.TestCase):
    def test_invalid_g4_record_fails_repository_validation(self) -> None:
        results = Results()
        with patch(
            "scripts.validate_repository.validate_v002_g4_applicability",
            return_value=(["invalid decision"], {}),
        ):
            check_v002_g4_applicability(results)

        self.assertEqual([], results.passes)
        self.assertEqual(
            ["v0.0.2 G4 applicability errors: invalid decision"],
            results.failures,
        )

    def test_valid_proposed_record_is_reported_as_unproven(self) -> None:
        results = Results()
        with patch(
            "scripts.validate_repository.validate_v002_g4_applicability",
            return_value=(
                [],
                {"status": "PROPOSED", "canonical_bundle": None},
            ),
        ):
            check_v002_g4_applicability(results)

        self.assertEqual([], results.failures)
        self.assertEqual([], results.passes)
        self.assertEqual(1, len(results.pending))
        self.assertIn("remains PROPOSED", results.pending[0])

    def test_accepted_record_matching_bundle_is_reported_as_accepted(self) -> None:
        results = Results()
        with patch(
            "scripts.validate_repository.validate_v002_g4_applicability",
            return_value=(
                [],
                {
                    "status": "ACCEPTED",
                    "canonical_bundle": "docs/releases/v0.0.2/evidence/client",
                },
            ),
        ):
            check_v002_g4_applicability(results)

        self.assertEqual([], results.failures)
        self.assertEqual(1, len(results.passes))
        self.assertIn("is ACCEPTED", results.passes[0])


class FinalG0RepositoryCheckTests(unittest.TestCase):
    def test_invalid_final_g0_record_fails_repository_validation(self) -> None:
        results = Results()
        with patch(
            "scripts.validate_repository._validate_v002_final_g0_history",
            return_value=(["bad binding"], {}),
        ):
            check_v002_final_g0_review(results)

        self.assertEqual([], results.passes)
        self.assertEqual(
            ["v0.0.2 final-G0 review record errors: bad binding"],
            results.failures,
        )

    def test_pending_final_g0_record_is_valid_without_gate_conclusion(self) -> None:
        results = Results()
        with patch(
            "scripts.validate_repository._validate_v002_final_g0_history",
            return_value=(
                [],
                {
                    "source_review_outcome": "PENDING_HUMAN_REVIEW",
                    "readme_review_outcome": "PENDING_HUMAN_REVIEW",
                },
            ),
        ):
            check_v002_final_g0_review(results)

        self.assertEqual([], results.failures)
        self.assertEqual([], results.passes)
        self.assertEqual(1, len(results.pending))
        self.assertIn("Gate not computed", results.pending[0])
        self.assertIn("source=PENDING_HUMAN_REVIEW", results.pending[0])

    def test_approved_records_still_do_not_compute_gate(self) -> None:
        results = Results()
        with patch(
            "scripts.validate_repository._validate_v002_final_g0_history",
            return_value=(
                [],
                {
                    "source_review_outcome": "APPROVED",
                    "readme_review_outcome": "APPROVED",
                },
            ),
        ):
            check_v002_final_g0_review(results)

        self.assertEqual([], results.failures)
        self.assertIn("Gate not computed", results.passes[0])


class V002GateStatusTests(unittest.TestCase):
    def document(
        self,
        *,
        status: str = "IN_PROGRESS",
        overall: str = "IN_PROGRESS",
        g0: str = "IN_PROGRESS",
        g4: str = "IN_PROGRESS",
        g5: str = "NOT_APPLICABLE",
        g6: str = "NOT_APPLICABLE",
        g7: str = "NOT_APPLICABLE",
        g8: str = "NOT_STARTED",
        g9: str = "IN_PROGRESS",
        reviewer: str = "",
        reviewed_at: str = "",
    ) -> str:
        return f'''# GATE_STATUS

```yaml
version: v0.0.2
status: {status}
gates:
  G0: {g0}
  G1: PASS
  G2: PASS
  G3: PASS
  G4: {g4}
  G5: {g5}
  G6: {g6}
  G7: {g7}
  G8: {g8}
  G9: {g9}
overall: {overall}
human_approved_by: "{reviewer}"
human_approved_at: "{reviewed_at}"
```
'''

    @staticmethod
    def pending_g0() -> dict[str, object]:
        return {
            "source_review_outcome": "PENDING_HUMAN_REVIEW",
            "readme_review_outcome": "PENDING_HUMAN_REVIEW",
        }

    @staticmethod
    def approved_g0() -> dict[str, object]:
        return {
            "source_review_outcome": "APPROVED",
            "readme_review_outcome": "APPROVED",
        }

    @staticmethod
    def proposed_g4() -> dict[str, object]:
        return {"status": "PROPOSED", "canonical_bundle": None}

    @staticmethod
    def accepted_g4() -> dict[str, object]:
        return {
            "status": "ACCEPTED",
            "canonical_bundle": "docs/releases/v0.0.2/evidence/client/evidence.json",
        }

    def test_honest_in_progress_status_is_valid(self) -> None:
        self.assertEqual(
            [],
            validate_v002_gate_status_text(
                self.document(),
                final_g0_details=self.pending_g0(),
                g4_details=self.proposed_g4(),
            ),
        )

    def test_g0_pass_requires_both_human_reviews(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g0="PASS"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(any("G0 cannot be PASS" in error for error in errors), errors)

    def test_g4_pass_requires_accepted_adr_and_canonical_bundle(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g4="PASS"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(any("G4 cannot be PASS" in error for error in errors), errors)

    def test_g8_pass_requires_canonical_valid_bundle(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g8="PASS"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(
            any("G8 cannot be READY_FOR_HUMAN_REVIEW or PASS" in error for error in errors),
            errors,
        )

    def test_g8_ready_for_review_also_requires_canonical_valid_bundle(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g8="READY_FOR_HUMAN_REVIEW"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(
            any("G8 cannot be READY_FOR_HUMAN_REVIEW or PASS" in error for error in errors),
            errors,
        )

    def test_mechanically_ready_bundle_does_not_make_g8_pass(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g4="PASS", g8="PASS"),
            final_g0_details=self.pending_g0(),
            g4_details=self.accepted_g4(),
        )

        self.assertTrue(
            any("mechanical readiness alone" in error for error in errors), errors
        )

    def test_g9_pass_requires_human_identity_and_timestamp(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g9="PASS"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(any("G9 cannot be PASS" in error for error in errors), errors)

    def test_passed_version_requires_all_required_gates(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(
                status="PASSED",
                overall="PASSED",
                g0="PASS",
                g4="PASS",
                g8="NOT_STARTED",
                g9="PASS",
                reviewer="sunthemoon",
                reviewed_at="2026-08-30",
            ),
            final_g0_details=self.approved_g0(),
            g4_details=self.accepted_g4(),
        )

        self.assertTrue(
            any("Required Gates are unresolved: G8" in error for error in errors),
            errors,
        )

    def test_fully_bound_passed_status_is_valid(self) -> None:
        self.assertEqual(
            [],
            validate_v002_gate_status_text(
                self.document(
                    status="PASSED",
                    overall="PASSED",
                    g0="PASS",
                    g4="PASS",
                    g8="PASS",
                    g9="PASS",
                    reviewer="sunthemoon",
                    reviewed_at="2026-08-30",
                ),
                final_g0_details=self.approved_g0(),
                g4_details=self.accepted_g4(),
            ),
        )

    def test_duplicate_yaml_block_cannot_hide_the_authoritative_status(self) -> None:
        text = self.document() + "\n```yaml\nstatus: PASSED\n```\n"

        errors = validate_v002_gate_status_text(
            text,
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertEqual(
            ["GATE_STATUS.md must contain exactly one YAML status block"], errors
        )

    def test_gate_status_error_flood_is_rejected_with_bounded_output(self) -> None:
        text = "# GATE_STATUS\n\n```yaml\ngates:\n" + "  invalid\n" * 1000 + "```\n"

        errors = validate_v002_gate_status_text(
            text,
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertEqual(
            ["GATE_STATUS YAML exceeds the status-block line limit"], errors
        )

    def test_alternate_success_wording_is_rejected(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(status="APPROVED", overall="COMPLETE"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(any("unsupported version status" in error for error in errors))
        self.assertTrue(any("unsupported overall status" in error for error in errors))

    def test_whitespace_human_identity_does_not_satisfy_g9(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g9="PASS", reviewer=" ", reviewed_at="2026-08-30"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(any("human approval fields" in error for error in errors), errors)
        self.assertTrue(any("G9 cannot be PASS" in error for error in errors), errors)

    def test_invalid_calendar_or_clock_values_do_not_satisfy_g9(self) -> None:
        for value in (
            "2026-99-99",
            "2026-02-30",
            "2026-08-30T99:99:99Z",
            "2026-08-30T12:00:00+99:99",
        ):
            with self.subTest(value=value):
                errors = validate_v002_gate_status_text(
                    self.document(
                        g9="PASS",
                        reviewer="sunthemoon",
                        reviewed_at=value,
                    ),
                    final_g0_details=self.pending_g0(),
                    g4_details=self.proposed_g4(),
                )

                self.assertTrue(
                    any("human approval fields" in error for error in errors), errors
                )
                self.assertTrue(
                    any("G9 cannot be PASS" in error for error in errors), errors
                )

    def test_unapproved_machine_identity_cannot_authorize_release_gates(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(
                g9="PASS",
                reviewer="codex",
                reviewed_at="2026-08-30",
            ),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(any("authorized reviewer" in error for error in errors), errors)
        self.assertTrue(any("G9 cannot be PASS" in error for error in errors), errors)

    def test_required_gates_cannot_be_waived_while_version_is_in_progress(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(
                g0="NOT_APPLICABLE",
                g4="NOT_APPLICABLE",
                g8="NOT_APPLICABLE",
                g9="NOT_APPLICABLE",
            ),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(
            any(
                "Required Gates cannot be NOT_APPLICABLE: G0, G4, G8, G9" in error
                for error in errors
            ),
            errors,
        )

    def test_out_of_scope_gates_cannot_be_reactivated_or_left_unresolved(self) -> None:
        errors = validate_v002_gate_status_text(
            self.document(g5="BLOCKED", g6="IN_PROGRESS", g7="NOT_STARTED"),
            final_g0_details=self.pending_g0(),
            g4_details=self.proposed_g4(),
        )

        self.assertTrue(
            any(
                "out-of-scope Gates must remain NOT_APPLICABLE: G5, G6, G7" in error
                for error in errors
            ),
            errors,
        )


class WorkflowStructureTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository_workflow = (
            ROOT / ".github/workflows/repository-docs.yml"
        ).read_text(encoding="utf-8")
        self.forge_workflow = (
            ROOT / ".github/workflows/forge-bootstrap.yml"
        ).read_text(encoding="utf-8")

    def mutate_forge_job(
        self, job_id: str, original: str, replacement: str
    ) -> str:
        marker = f"  {job_id}:"
        before, separator, job_and_after = self.forge_workflow.partition(marker)
        self.assertEqual(marker, separator)
        self.assertIn(original, job_and_after)
        return before + separator + job_and_after.replace(original, replacement, 1)

    def test_current_workflows_have_required_enabled_steps(self) -> None:
        self.assertEqual(
            [], validate_repository_workflow_text(self.repository_workflow)
        )
        self.assertEqual([], validate_forge_workflow_text(self.forge_workflow))

    def test_commented_validator_command_does_not_satisfy_requirement(self) -> None:
        tampered = self.repository_workflow.replace(
            "        run: python scripts/validate_bootstrap_provenance.py",
            "        # run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_retired_g0_review_packet_commands_are_rejected(self) -> None:
        self.assertNotIn(
            "prepare_v002_g0_review_packet.py", self.repository_workflow
        )
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Recreate retired packet\n"
            "        run: python -I -S scripts/prepare_v002_g0_review_packet.py "
            "generate --commit \"$REVIEW_COMMIT\" --output build/packet\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "no retired v0.0.2 review-input generation at the current head", errors
        )

    def test_retired_final_g0_review_input_commands_are_rejected(self) -> None:
        self.assertNotIn(
            "prepare_v002_final_g0_review_inputs.py", self.repository_workflow
        )
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Recreate retired final review inputs\n"
            "        run: python -I -S scripts/prepare_v002_final_g0_review_inputs.py "
            "generate --commit \"$REVIEW_COMMIT\" --output build/final-inputs\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "no retired v0.0.2 review-input generation at the current head", errors
        )

    def test_repository_workflow_action_contract_mutations_are_rejected(self) -> None:
        cases = (
            (
                "checkout action version",
                "uses: actions/checkout@v7",
                "uses: actions/checkout@v6",
                "exact enabled action contract actions/checkout@v7",
            ),
            (
                "checkout history",
                "fetch-depth: 0",
                "fetch-depth: 1",
                "exact enabled action contract actions/checkout@v7",
            ),
            (
                "checkout credentials",
                "persist-credentials: false",
                "persist-credentials: true",
                "exact enabled action contract actions/checkout@v7",
            ),
            (
                "checkout review commit",
                "ref: ${{ env.REVIEW_COMMIT }}",
                "ref: ${{ github.sha }}",
                "exact enabled action contract actions/checkout@v7",
            ),
            (
                "setup-python action version",
                "uses: actions/setup-python@v7",
                "uses: actions/setup-python@v6",
                "exact enabled action contract actions/setup-python@v7",
            ),
            (
                "python version",
                'python-version: "3.12"',
                'python-version: "3.11"',
                "exact enabled action contract actions/setup-python@v7",
            ),
        )

        for name, original, replacement, expected in cases:
            with self.subTest(name=name):
                self.assertIn(original, self.repository_workflow)
                tampered = self.repository_workflow.replace(original, replacement, 1)

                errors = validate_repository_workflow_text(tampered)

                self.assertIn(expected, errors)

    def test_repository_workflow_rejects_additional_upload_inputs(self) -> None:
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Stale evidence upload\n"
            "        uses: actions/upload-artifact@v7\n"
            "        with:\n"
            "          name: stale\n"
            "          path: build/stale/\n"
            "          retention-days: 7\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn("no governance artifact uploads after v0.0.2 archival", errors)

    def test_repository_workflow_rejects_an_additional_upload_step(self) -> None:
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Unexpected upload\n"
            "        uses: actions/upload-artifact@v6\n"
            "        with:\n"
            "          name: unexpected\n"
            "          path: build/\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn("no governance artifact uploads after v0.0.2 archival", errors)

    def test_repository_workflow_permissions_are_read_only(self) -> None:
        mutations = (
            ("  contents: read", "  contents: write"),
            ("  contents: read", "  contents: read\n  actions: write"),
            (
                "    runs-on: ubuntu-latest",
                "    runs-on: ubuntu-latest\n    permissions: write-all",
            ),
        )

        for original, replacement in mutations:
            with self.subTest(replacement=replacement):
                tampered = self.repository_workflow.replace(original, replacement, 1)

                errors = validate_repository_workflow_text(tampered)

                self.assertTrue(
                    any("permissions" in error for error in errors), errors
                )

    def test_repository_workflow_binds_review_inputs_to_head_or_push_commit(self) -> None:
        original = (
            "REVIEW_COMMIT: ${{ github.event.pull_request.head.sha || github.sha }}"
        )
        for replacement in (
            "",
            "REVIEW_COMMIT: ${{ github.sha }}",
            "REVIEW_COMMIT: ${{ github.event.pull_request.head.ref }}",
        ):
            with self.subTest(replacement=replacement):
                tampered = self.repository_workflow.replace(original, replacement, 1)

                errors = validate_repository_workflow_text(tampered)

                self.assertIn("exact immutable review-commit job environment", errors)

    def test_repository_workflow_rejects_missing_checkout_ref(self) -> None:
        tampered = self.repository_workflow.replace(
            "          ref: ${{ env.REVIEW_COMMIT }}\n", "", 1
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "exact enabled action contract actions/checkout@v7", errors
        )

    def test_forge_jobs_bind_exact_review_commit_environments(self) -> None:
        original = (
            "      REVIEW_COMMIT: "
            "${{ github.event.pull_request.head.sha || github.sha }}\n"
        )
        cases = (
            (
                "baseline",
                "",
                "baseline exact immutable review-commit job environment",
            ),
            (
                "baseline",
                "      REVIEW_COMMIT: ${{ github.sha }}\n",
                "baseline exact immutable review-commit job environment",
            ),
            (
                "latest-compatibility",
                "",
                "latest exact immutable review-commit and Forge 47.4.23 job environment",
            ),
            (
                "latest-compatibility",
                "      REVIEW_COMMIT: ${{ github.sha }}\n",
                "latest exact immutable review-commit and Forge 47.4.23 job environment",
            ),
        )
        for job_id, replacement, expected in cases:
            with self.subTest(job_id=job_id, replacement=replacement):
                tampered = self.mutate_forge_job(job_id, original, replacement)

                errors = validate_forge_workflow_text(tampered)

                self.assertIn(expected, errors)

    def test_forge_jobs_bind_checkout_to_review_commit(self) -> None:
        original = "          ref: ${{ env.REVIEW_COMMIT }}\n"
        cases = (
            (
                "baseline",
                "",
                "baseline exact head-bound checkout action contract",
            ),
            (
                "baseline",
                "          ref: ${{ github.sha }}\n",
                "baseline exact head-bound checkout action contract",
            ),
            (
                "latest-compatibility",
                "",
                "latest exact head-bound checkout action contract",
            ),
            (
                "latest-compatibility",
                "          ref: ${{ github.sha }}\n",
                "latest exact head-bound checkout action contract",
            ),
        )
        for job_id, replacement, expected in cases:
            with self.subTest(job_id=job_id, replacement=replacement):
                tampered = self.mutate_forge_job(job_id, original, replacement)

                errors = validate_forge_workflow_text(tampered)

                self.assertIn(expected, errors)

    def test_forge_artifact_identity_is_exact_head_bound(self) -> None:
        original = "          name: forge-47.4.10-${{ env.REVIEW_COMMIT }}\n"
        for replacement in (
            "",
            "          name: forge-47.4.10-${{ github.sha }}\n",
        ):
            with self.subTest(replacement=replacement):
                tampered = self.mutate_forge_job(
                    "baseline", original, replacement
                )

                errors = validate_forge_workflow_text(tampered)

                self.assertIn(
                    "baseline exact head-bound artifact upload identity", errors
                )

    def test_g0_review_packet_sequence_requires_python_isolation(self) -> None:
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Retired nonisolated packet\n"
            "        run: python -S scripts/prepare_v002_g0_review_packet.py "
            "generate --commit \"$REVIEW_COMMIT\" --output build/packet\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "no retired v0.0.2 review-input generation at the current head", errors
        )

    def test_g0_review_packet_sequence_rejects_an_extra_command(self) -> None:
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Retired packet plus extra command\n"
            "        run: |\n"
            "          python -I -S scripts/prepare_v002_g0_review_packet.py "
            "generate --commit \"$REVIEW_COMMIT\" --output build/packet\n"
            "          python --version\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "no retired v0.0.2 review-input generation at the current head", errors
        )

    def test_final_g0_review_input_sequence_requires_python_isolation(self) -> None:
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Retired nonisolated final inputs\n"
            "        run: python -S scripts/prepare_v002_final_g0_review_inputs.py "
            "generate --commit \"$REVIEW_COMMIT\" --output build/final-inputs\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "no retired v0.0.2 review-input generation at the current head", errors
        )

    def test_final_g0_review_input_sequence_rejects_an_extra_command(self) -> None:
        marker = "      - name: Enforce governance baseline"
        tampered = self.repository_workflow.replace(
            marker,
            "      - name: Retired final inputs plus extra command\n"
            "        run: |\n"
            "          python -I -S scripts/prepare_v002_final_g0_review_inputs.py "
            "generate --commit \"$REVIEW_COMMIT\" --output build/final-inputs\n"
            "          python --version\n\n"
            + marker,
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "no retired v0.0.2 review-input generation at the current head", errors
        )

    def test_if_false_validator_step_does_not_satisfy_requirement(self) -> None:
        tampered = self.repository_workflow.replace(
            "      - name: Validate bootstrap provenance allowlist\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            "      - name: Validate bootstrap provenance allowlist\n"
            "        if: false\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_disabled_forge_provenance_step_is_rejected(self) -> None:
        tampered = self.forge_workflow.replace(
            "      - name: Validate bootstrap provenance allowlist\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            "      - name: Validate bootstrap provenance allowlist\n"
            "        if: ${{ false }}\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_forge_workflow_text(tampered)

        self.assertIn(
            "baseline enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_command_text_in_step_name_is_not_treated_as_run(self) -> None:
        tampered = self.repository_workflow.replace(
            "        run: python scripts/validate_bootstrap_provenance.py",
            "        name: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_truthy_continue_on_error_rejects_required_step(self) -> None:
        for value in ("true", "yes", "on", "1", "${{ true }}"):
            with self.subTest(value=value):
                tampered = self.repository_workflow.replace(
                    "      - name: Validate bootstrap provenance allowlist\n"
                    "        run: python scripts/validate_bootstrap_provenance.py",
                    "      - name: Validate bootstrap provenance allowlist\n"
                    f"        continue-on-error: {value}\n"
                    "        run: python scripts/validate_bootstrap_provenance.py",
                    1,
                )

                errors = validate_repository_workflow_text(tampered)

                self.assertIn(
                    "enabled run command python scripts/validate_bootstrap_provenance.py",
                    errors,
                )

    def test_pre_colon_whitespace_cannot_hide_step_continue_on_error(self) -> None:
        tampered = self.repository_workflow.replace(
            "      - name: Validate bootstrap provenance allowlist\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            "      - name: Validate bootstrap provenance allowlist\n"
            "        continue-on-error : true\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_quoted_key_cannot_hide_step_continue_on_error(self) -> None:
        for key in ("'continue-on-error'", '"continue-on-error"'):
            with self.subTest(key=key):
                tampered = self.repository_workflow.replace(
                    "      - name: Validate bootstrap provenance allowlist\n"
                    "        run: python scripts/validate_bootstrap_provenance.py",
                    "      - name: Validate bootstrap provenance allowlist\n"
                    f"        {key} : true\n"
                    "        run: python scripts/validate_bootstrap_provenance.py",
                    1,
                )

                errors = validate_repository_workflow_text(tampered)

                self.assertIn(
                    "enabled run command python scripts/validate_bootstrap_provenance.py",
                    errors,
                )

    def test_unsupported_escaped_step_key_is_rejected_conservatively(self) -> None:
        tampered = self.repository_workflow.replace(
            "      - name: Validate bootstrap provenance allowlist\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            "      - name: Validate bootstrap provenance allowlist\n"
            '        "continue\\u002don\\u002derror": true\n'
            "        run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_dynamic_continue_on_error_rejects_required_step(self) -> None:
        tampered = self.repository_workflow.replace(
            "      - name: Validate bootstrap provenance allowlist\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            "      - name: Validate bootstrap provenance allowlist\n"
            "        continue-on-error: ${{ matrix.allow_failure }}\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_false_continue_on_error_keeps_required_step_blocking(self) -> None:
        tampered = self.repository_workflow.replace(
            "      - name: Validate bootstrap provenance allowlist\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            "      - name: Validate bootstrap provenance allowlist\n"
            "        continue-on-error: ${{ false }}\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        self.assertEqual([], validate_repository_workflow_text(tampered))

    def test_truthy_continue_on_error_rejects_required_job(self) -> None:
        tampered = self.repository_workflow.replace(
            "    runs-on: ubuntu-latest",
            "    runs-on: ubuntu-latest\n    continue-on-error: on",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn("enabled blocking validate-repository-docs job", errors)

    def test_pre_colon_whitespace_cannot_hide_job_continue_on_error(self) -> None:
        tampered = self.repository_workflow.replace(
            "    runs-on: ubuntu-latest",
            "    runs-on: ubuntu-latest\n    continue-on-error : true",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn("enabled blocking validate-repository-docs job", errors)

    def test_quoted_key_cannot_hide_job_continue_on_error(self) -> None:
        for key in ("'continue-on-error'", '"continue-on-error"'):
            with self.subTest(key=key):
                tampered = self.repository_workflow.replace(
                    "    runs-on: ubuntu-latest",
                    f"    runs-on: ubuntu-latest\n    {key} : true",
                    1,
                )

                errors = validate_repository_workflow_text(tampered)

                self.assertIn("enabled blocking validate-repository-docs job", errors)

    def test_dynamic_continue_on_error_rejects_required_job(self) -> None:
        tampered = self.repository_workflow.replace(
            "    runs-on: ubuntu-latest",
            "    runs-on: ubuntu-latest\n"
            "    continue-on-error: ${{ inputs.allow_failure }}",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn("enabled blocking validate-repository-docs job", errors)

    def test_truthy_continue_on_error_rejects_baseline_job(self) -> None:
        tampered = self.forge_workflow.replace(
            "    name: Forge 47.4.10 baseline",
            "    name: Forge 47.4.10 baseline\n    continue-on-error: 1",
            1,
        )

        errors = validate_forge_workflow_text(tampered)

        self.assertIn("enabled blocking baseline job", errors)

    def test_truthy_continue_on_error_rejects_baseline_step(self) -> None:
        tampered = self.forge_workflow.replace(
            "      - name: Validate bootstrap provenance allowlist\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            "      - name: Validate bootstrap provenance allowlist\n"
            "        continue-on-error: yes\n"
            "        run: python scripts/validate_bootstrap_provenance.py",
            1,
        )

        errors = validate_forge_workflow_text(tampered)

        self.assertIn(
            "baseline enabled run command python scripts/validate_bootstrap_provenance.py",
            errors,
        )

    def test_latest_advisory_job_still_requires_blocking_build_step(self) -> None:
        tampered = self.forge_workflow.replace(
            "      - name: Compile and test against latest Forge lane\n"
            "        run: ./gradlew clean build --no-daemon --stacktrace",
            "      - name: Compile and test against latest Forge lane\n"
            "        continue-on-error: true\n"
            "        run: ./gradlew clean build --no-daemon --stacktrace",
            1,
        )

        errors = validate_forge_workflow_text(tampered)

        self.assertIn(
            "latest enabled run command ./gradlew clean build --no-daemon --stacktrace",
            errors,
        )


if __name__ == "__main__":
    unittest.main()
