import hashlib
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.validate_repository import (
    APPROVED_RECORD_STATUS,
    COMMITTED_BUNDLE,
    GRADLE_WRAPPER_PATH,
    ROOT,
    Results,
    check_optional_v002_client_evidence,
    find_unlisted_v002_resources,
    is_audited_v001_evidence,
    is_approved_gradle_wrapper,
    is_approved_third_party_license,
    markdown_link_errors,
    normalize_link_target,
    parse_current_identity,
    tracked_markdown_files,
    validate_forge_workflow_text,
    validate_repository_workflow_text,
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
            self.assertEqual(1, len(results.passes))
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
            validate_client_bundle.assert_called_once_with(bundle, repository_root=root)


class WorkflowStructureTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repository_workflow = (
            ROOT / ".github/workflows/repository-docs.yml"
        ).read_text(encoding="utf-8")
        self.forge_workflow = (
            ROOT / ".github/workflows/forge-bootstrap.yml"
        ).read_text(encoding="utf-8")

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

    def test_g0_review_packet_commands_are_required(self) -> None:
        commands = (
            (
                "          python -I -S -c \"from pathlib import Path; "
                "Path('build').mkdir(exist_ok=True)\""
            ),
            (
                "          python -I -S scripts/prepare_v002_g0_review_packet.py generate "
                '--commit "$GITHUB_SHA" --output build/v0.0.2-g0-review-packet'
            ),
            (
                "          python -I -S scripts/prepare_v002_g0_review_packet.py verify "
                '--commit "$GITHUB_SHA" --packet build/v0.0.2-g0-review-packet'
            ),
        )

        for command in commands:
            with self.subTest(command=command):
                tampered = self.repository_workflow.replace(command, "          true", 1)

                errors = validate_repository_workflow_text(tampered)

                self.assertIn(
                    "exact isolated G0 review-packet "
                    "setup/generate/verify command sequence",
                    errors,
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
            (
                "upload action version",
                "uses: actions/upload-artifact@v7",
                "uses: actions/upload-artifact@v6",
                "exact enabled action contract actions/upload-artifact@v7",
            ),
            (
                "artifact name",
                "name: v0.0.2-g0-review-packet-${{ github.sha }}",
                "name: v0.0.2-g0-review-packet",
                "exact enabled action contract actions/upload-artifact@v7",
            ),
            (
                "missing artifact behavior",
                "if-no-files-found: error",
                "if-no-files-found: warn",
                "exact enabled action contract actions/upload-artifact@v7",
            ),
            (
                "hidden files",
                "include-hidden-files: true",
                "include-hidden-files: false",
                "exact enabled action contract actions/upload-artifact@v7",
            ),
            (
                "artifact path",
                "path: build/v0.0.2-g0-review-packet/",
                "path: build/",
                "exact enabled action contract actions/upload-artifact@v7",
            ),
        )

        for name, original, replacement, expected in cases:
            with self.subTest(name=name):
                self.assertIn(original, self.repository_workflow)
                tampered = self.repository_workflow.replace(original, replacement, 1)

                errors = validate_repository_workflow_text(tampered)

                self.assertIn(expected, errors)

    def test_repository_workflow_rejects_additional_upload_inputs(self) -> None:
        tampered = self.repository_workflow.replace(
            "          include-hidden-files: true",
            "          include-hidden-files: true\n          retention-days: 7",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "exact enabled action contract actions/upload-artifact@v7", errors
        )

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

    def test_g0_review_packet_sequence_requires_python_isolation(self) -> None:
        tampered = self.repository_workflow.replace(
            "python -I -S scripts/prepare_v002_g0_review_packet.py generate",
            "python -S scripts/prepare_v002_g0_review_packet.py generate",
            1,
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "exact isolated G0 review-packet setup/generate/verify command sequence",
            errors,
        )

    def test_g0_review_packet_sequence_rejects_an_extra_command(self) -> None:
        generate = (
            "          python -I -S scripts/prepare_v002_g0_review_packet.py generate "
            '--commit "$GITHUB_SHA" --output build/v0.0.2-g0-review-packet'
        )
        tampered = self.repository_workflow.replace(
            generate, generate + "\n          python --version", 1
        )

        errors = validate_repository_workflow_text(tampered)

        self.assertIn(
            "exact isolated G0 review-packet setup/generate/verify command sequence",
            errors,
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
