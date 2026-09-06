"""CI metadata is exact, bounded and safe for the runner environment file."""

from __future__ import annotations

import contextlib
import hashlib
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

from scripts.ci_artifact_identity import (
    MAX_PROPERTIES_BYTES,
    ROOT,
    github_environment,
    main,
    read_identity,
)
from scripts.validate_repository import parse_workflow_jobs, validate_forge_workflow_text


PROPERTIES = (
    "minecraft_version=1.20.1\n"
    "mod_version=1.0.0-dev\n"
    "mod_artifact_id=advancedrocketry-community\n"
)


class ArtifactIdentityTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.properties = self.root / "gradle.properties"
        self.properties.write_text(PROPERTIES, encoding="utf-8", newline="\n")

    def test_exact_names_and_input_digest(self) -> None:
        identity = read_identity(self.properties)
        self.assertEqual("1.20.1-1.0.0-dev", identity["build_version"])
        self.assertEqual(
            "build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar",
            identity["artifact"],
        )
        self.assertEqual(str(identity["artifact"])[:-4] + "-sources.jar", identity["sources"])
        self.assertEqual(hashlib.sha256(self.properties.read_bytes()).hexdigest(),
                         identity["properties_sha256"])

    def test_version_transition_changes_all_outputs(self) -> None:
        for version in ("0.9.0-beta.1", "1.0.0-dev", "1.0.0-rc.1", "1.0.0"):
            with self.subTest(version=version):
                self.properties.write_text(PROPERTIES.replace("1.0.0-dev", version))
                identity = read_identity(self.properties)
                self.assertEqual("1.20.1-" + version, identity["build_version"])
                self.assertIn("-" + version + ".jar", identity["artifact"])
                self.assertIn("-" + version + "-sources.jar", identity["sources"])

    def test_repository_properties_are_supported(self) -> None:
        identity = read_identity(ROOT / "gradle.properties")
        self.assertEqual("advancedrocketry-community", identity["mod_artifact_id"])
        self.assertEqual(3, len(github_environment(identity).splitlines()))

    def test_unrelated_values_are_not_exported(self) -> None:
        self.properties.write_text(
            "# comment\n! comment\n" + PROPERTIES + "mod_description=First\\nSecond\n"
            "unrelated=$(echo danger)\n", encoding="utf-8"
        )
        environment = github_environment(read_identity(self.properties))
        self.assertNotIn("danger", environment)
        self.assertNotIn("description", environment)

    def test_canonical_crlf_and_leading_property_whitespace(self) -> None:
        self.properties.write_bytes(PROPERTIES.replace("=", " = \t").replace("\n", "\r\n").encode())
        self.assertEqual("1.20.1-1.0.0-dev", read_identity(self.properties)["build_version"])

    def test_rejects_ambiguous_or_unsafe_properties(self) -> None:
        cases = (
            PROPERTIES + "mod_version=1.0.0\n",
            PROPERTIES + "mod\\u005fversion=1.0.0\n",
            PROPERTIES + "mod_version:1.0.0\n",
            PROPERTIES + "description=continued\\\nmod_version=1.0.0\n",
            PROPERTIES.replace("mod_version=1.0.0-dev\n", ""),
            PROPERTIES.replace("1.0.0-dev", "$(touch unexpected)"),
            PROPERTIES.replace("1.0.0-dev", "1.0.0-dev "),
            PROPERTIES.replace("1.0.0-dev", "1.0.0;exit"),
            PROPERTIES.replace("1.0.0-dev", "1.0.0\rARCE_ARTIFACT=x"),
            PROPERTIES.replace("1.0.0-dev", "1.0.0-" + "x" * 100),
            PROPERTIES.replace("advancedrocketry-community", "../outside"),
            PROPERTIES.replace("advancedrocketry-community", "project*"),
            PROPERTIES.replace("1.20.1", "1.20.1/../../x"),
            "\ufeff" + PROPERTIES,
        )
        for content in cases:
            with self.subTest(content=content):
                self.properties.write_text(content, encoding="utf-8", newline="\n")
                with self.assertRaises(ValueError):
                    read_identity(self.properties)

    def test_rejects_oversized_or_invalid_utf8_input(self) -> None:
        for data in (b"#" * (MAX_PROPERTIES_BYTES + 1), b"\xff"):
            self.properties.write_bytes(data)
            with self.assertRaises(ValueError):
                read_identity(self.properties)

    def test_cli_manifest_and_environment_agree(self) -> None:
        manifest = self.root / "evidence" / "identity.json"
        environment = self.root / "github-env"
        environment.write_text("PREVIOUS=retained\n", encoding="utf-8")
        with contextlib.redirect_stdout(io.StringIO()) as output:
            code = main(["--properties", str(self.properties), "--manifest", str(manifest),
                         "--github-env", str(environment)])
        self.assertEqual(0, code)
        identity = json.loads(manifest.read_text(encoding="utf-8"))
        self.assertEqual(identity, json.loads(output.getvalue()))
        self.assertEqual("PREVIOUS=retained\n" + github_environment(identity),
                         environment.read_text(encoding="utf-8"))

    def test_invalid_input_does_not_write_either_output(self) -> None:
        self.properties.write_text(PROPERTIES.replace("1.0.0-dev", "bad value"))
        manifest = self.root / "manifest.json"
        environment = self.root / "github-env"
        environment.write_bytes(b"PREVIOUS=retained\n")
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, main(["--properties", str(self.properties), "--manifest", str(manifest),
                                      "--github-env", str(environment)]))
        self.assertFalse(manifest.exists())
        self.assertEqual(b"PREVIOUS=retained\n", environment.read_bytes())

    def test_cli_rejects_missing_environment_or_overlapping_files(self) -> None:
        for manifest, environment in (
            (self.root / "manifest.json", self.root / "missing-env"),
            (self.properties, self.properties),
            (self.root / "manifest.json", self.properties),
        ):
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1, main(["--properties", str(self.properties), "--manifest", str(manifest),
                                          "--github-env", str(environment)]))
        self.assertEqual(PROPERTIES.encode(), self.properties.read_bytes())


class ArtifactWorkflowTests(unittest.TestCase):
    PRODUCER = (
        "      - name: Resolve built artifact identity\n"
        "        run: >-\n"
        "          python scripts/ci_artifact_identity.py\n"
        "          --manifest build/release-evidence/build-identity.json\n"
        '          --github-env "${GITHUB_ENV}"\n\n'
    )

    def setUp(self) -> None:
        self.workflow = (ROOT / ".github/workflows/forge-bootstrap.yml").read_text(encoding="utf-8")

    def mutate_job(self, name: str, old: str, new: str) -> str:
        marker = f"  {name}:\n"
        before, separator, after = self.workflow.partition(marker)
        self.assertEqual(marker, separator)
        sections = re.split(r"\n(?=  [A-Za-z0-9_-]+:\n)", after, maxsplit=1)
        job = sections[0]
        remainder = "\n" + sections[1] if len(sections) == 2 else ""
        self.assertIn(old, job)
        return before + separator + job.replace(old, new, 1) + remainder

    def test_all_forge_jobs_resolve_and_audit_the_configured_artifact(self) -> None:
        for name, job in parse_workflow_jobs(self.workflow).items():
            with self.subTest(job=name):
                runs = "\n".join(step.fields.get("run", "") for step in job.steps)
                self.assertEqual(1, runs.count("scripts/ci_artifact_identity.py"))
                self.assertIn('"${ARCE_ARTIFACT}"', runs)
                self.assertIn('--expected-version "${ARCE_BUILD_VERSION}"', runs)
                self.assertNotIn("0.9.0-beta.1", runs)
        self.assertEqual([], validate_forge_workflow_text(self.workflow))

    def test_missing_disabled_duplicate_or_changed_producer_is_rejected(self) -> None:
        for job in parse_workflow_jobs(self.workflow):
            for replacement in (
                "", self.PRODUCER * 2,
                self.PRODUCER.replace("        run:", "        if: false\n        run:"),
                self.PRODUCER.replace("        run:", "        if: ${{ inputs.resolve }}\n        run:"),
                self.PRODUCER.replace("        run:", "        continue-on-error: true\n        run:"),
                self.PRODUCER.replace("${GITHUB_ENV}", "build/env.txt"),
                self.PRODUCER.replace("--manifest", "--properties historical.properties --manifest"),
            ):
                with self.subTest(job=job, replacement=replacement):
                    errors = validate_forge_workflow_text(self.mutate_job(job, self.PRODUCER, replacement))
                    self.assertIn(f"{job} one enabled exact build-identity producer", errors)

    def test_producer_before_clean_build_is_rejected(self) -> None:
        tampered = self.workflow.replace(self.PRODUCER, "")
        tampered = tampered.replace("      - name: Enable Gradle wrapper execution", self.PRODUCER +
                                    "      - name: Enable Gradle wrapper execution")
        errors = validate_forge_workflow_text(tampered)
        for job in parse_workflow_jobs(self.workflow):
            self.assertIn(f"{job} identity follows checkout, Python and clean build", errors)

    def test_consumer_before_identity_is_rejected(self) -> None:
        marker = "      - name: Upload"
        for job in parse_workflow_jobs(self.workflow):
            without = self.mutate_job(job, self.PRODUCER, "")
            before, separator, after = without.partition(f"  {job}:\n")
            tampered = before + separator + after.replace(marker, self.PRODUCER + marker, 1)
            self.assertIn(f"{job} artifact consumers follow the identity producer",
                          validate_forge_workflow_text(tampered))

    def test_step_environment_override_or_extra_writer_is_rejected(self) -> None:
        for job in parse_workflow_jobs(self.workflow):
            for addition in (
                "      - name: Override identity\n        run: echo test\n"
                "        env:\n          ARCE_ARTIFACT: historical.jar\n\n",
                "      - name: Override identity\n        run: echo ARCE_ARTIFACT=historical.jar >> $GITHUB_ENV\n\n",
            ):
                with self.subTest(job=job, addition=addition):
                    errors = validate_forge_workflow_text(
                        self.mutate_job(job, self.PRODUCER, self.PRODUCER + addition))
                    self.assertTrue(any("override" in error or "writer" in error for error in errors))

    def test_stale_or_wildcard_artifact_and_version_consumers_are_rejected(self) -> None:
        for job in parse_workflow_jobs(self.workflow):
            for old, new in (
                ('"${ARCE_ARTIFACT}"', "build/libs/advancedrocketry-community-1.20.1-0.9.0-beta.1.jar"),
                ('"${ARCE_ARTIFACT}"', "build/libs/*.jar"),
                ('"${ARCE_BUILD_VERSION}"', "1.20.1-0.9.0-beta.1"),
                ('"${ARCE_ARTIFACT}"', "${ARCE_ARTIFACT}"),
                ('"${ARCE_BUILD_VERSION}"', "${ARCE_BUILD_VERSION}"),
            ):
                with self.subTest(job=job, replacement=new):
                    self.assertTrue(validate_forge_workflow_text(self.mutate_job(job, old, new)))

    def test_upload_identity_reports_and_unconditional_execution_are_required(self) -> None:
        for job in parse_workflow_jobs(self.workflow):
            cases = [
                ("            ${{ env.ARCE_ARTIFACT }}\n", "            build/libs/*.jar\n"),
                ("            build/release-evidence/build-identity.json\n", ""),
                ("            build/test-results/test/\n", ""),
                ("        if: always()\n", "        if: success()\n"),
            ]
            directories = {
                "baseline": ("dedicated-server-smoke", "v090-migration-server-smoke", "v090-forced-stop"),
                "satellite-acceptance": ("v090-dedicated-server-smoke",),
                "latest-compatibility": (),
            }
            for directory in directories[job]:
                for suffix in ("*-full.txt", "logs/"):
                    cases.append((f"            build/{directory}/session/{suffix}\n", ""))
            for old, new in cases:
                with self.subTest(job=job, replacement=new):
                    self.assertIn(f"{job} unconditional upload of exact artifact, identity and test evidence",
                                  validate_forge_workflow_text(self.mutate_job(job, old, new)))

    def test_latest_upload_is_head_bound_and_audit_cannot_be_disabled(self) -> None:
        self.assertIn("latest exact head-bound artifact upload identity", validate_forge_workflow_text(
            self.mutate_job("latest-compatibility", "forge-47.4.23-${{ env.REVIEW_COMMIT }}",
                            "forge-47.4.23-${{ github.sha }}")))
        self.assertTrue(validate_forge_workflow_text(self.mutate_job(
            "latest-compatibility", "      - name: Audit the latest-lane distributable JAR",
            "      - name: Audit the latest-lane distributable JAR\n        if: false")))

    def test_no_clean_step_can_delete_the_identity_manifest(self) -> None:
        for job in parse_workflow_jobs(self.workflow):
            changed = self.PRODUCER + "      - name: Delete build outputs\n        run: ./gradlew clean\n\n"
            self.assertIn(f"{job} no clean after build identity manifest", validate_forge_workflow_text(
                self.mutate_job(job, self.PRODUCER, changed)))

    def test_historical_release_checks_remain_required(self) -> None:
        for version in ("v040", "v050", "v060", "v070", "v080", "v090"):
            command = f"python scripts/validate_{version}_release_evidence.py --require-approved"
            self.assertTrue(validate_forge_workflow_text(self.mutate_job("baseline", command, "echo skipped")))


if __name__ == "__main__":
    unittest.main()
