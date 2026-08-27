import hashlib
import unittest

from scripts.validate_repository import (
    GRADLE_WRAPPER_PATH,
    ROOT,
    find_unlisted_v002_resources,
    is_audited_v001_evidence,
    is_approved_gradle_wrapper,
    is_approved_third_party_license,
    normalize_link_target,
    parse_current_identity,
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
