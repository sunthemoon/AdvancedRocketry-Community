"""Capacity checks are tested without launching Java or changing real disks."""

import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
from types import SimpleNamespace
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/check_v180_ci_host.py"
SPEC = importlib.util.spec_from_file_location("v180_ci_host", SCRIPT)
host = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(host)


class HostCapacityTests(unittest.TestCase):
    def inspect(self, free=host.MIN_FREE_BYTES, uid=1001, platform="linux"):
        with patch.object(host.sys, "platform", platform), \
                patch.object(host.os, "getuid", return_value=uid, create=True), \
                patch.object(host, "existing_parent", side_effect=lambda path: path), \
                patch.object(host.shutil, "disk_usage", return_value=SimpleNamespace(free=free)):
            return host.inspect_host(Path("checkout"))

    def test_exact_floor_is_accepted(self):
        result = self.inspect()
        self.assertEqual("PASS", result["result"])
        self.assertEqual(3, len(result["filesystems"]))

    def test_one_byte_below_floor_refuses_each_execution_filesystem(self):
        result = self.inspect(free=host.MIN_FREE_BYTES - 1)
        self.assertEqual("FAIL", result["result"])
        self.assertEqual(3, len(result["errors"]))

    def test_root_is_rejected_even_with_capacity(self):
        self.assertEqual("FAIL", self.inspect(uid=0)["result"])

    def test_missing_uid_is_rejected(self):
        self.assertEqual("FAIL", self.inspect(uid=None)["result"])

    def test_windows_is_not_a_linux_host(self):
        self.assertEqual("FAIL", self.inspect(platform="win32")["result"])

    def test_one_small_filesystem_rejects_the_host(self):
        with patch.object(host.sys, "platform", "linux"), \
                patch.object(host.os, "getuid", return_value=1001, create=True), \
                patch.object(host, "existing_parent", side_effect=lambda path: path), \
                patch.object(host.shutil, "disk_usage", side_effect=[
                    SimpleNamespace(free=host.MIN_FREE_BYTES),
                    SimpleNamespace(free=host.MIN_FREE_BYTES - 1),
                    SimpleNamespace(free=host.MIN_FREE_BYTES)]):
            result = host.inspect_host(Path("checkout"))
        self.assertEqual(["temporary_and_evidence has less than 10 GB free"], result["errors"])

    def test_disk_error_is_not_a_success(self):
        with patch.object(host.shutil, "disk_usage", side_effect=OSError("unavailable")):
            result = host.inspect_host(SCRIPT.parent)
        self.assertEqual("FAIL", result["result"])
        self.assertEqual(3, sum("Cannot inspect" in error for error in result["errors"]))

    def test_configured_cache_and_temp_are_sampled(self):
        with patch.dict(os.environ, {"GRADLE_USER_HOME": "gradle-cache", "RUNNER_TEMP": "evidence-temp"}):
            result = self.inspect()
        rows = {row["purpose"]: row for row in result["filesystems"]}
        self.assertEqual(str(Path("gradle-cache").resolve()), rows["gradle_cache"]["location"])
        self.assertEqual(str(Path("evidence-temp").resolve()), rows["temporary_and_evidence"]["location"])

    def test_nonexistent_output_uses_existing_ancestor_without_creation(self):
        missing = SCRIPT.parent / "__v180_ci_test_absent__" / "child"
        self.assertFalse(missing.exists())
        self.assertEqual(SCRIPT.parent.resolve(), host.existing_parent(missing))
        self.assertFalse(missing.exists())

    def test_file_cannot_be_a_directory_location(self):
        with self.assertRaises(ValueError):
            host.existing_parent(SCRIPT)

    def test_cli_returns_failure_and_structured_observations(self):
        output = io.StringIO()
        with patch.object(host, "inspect_host", return_value={"errors": ["refused"], "result": "FAIL"}), \
                contextlib.redirect_stdout(output):
            self.assertEqual(1, host.main([]))
        self.assertEqual("FAIL", json.loads(output.getvalue())["result"])

    def test_cli_returns_success_for_valid_observations(self):
        with patch.object(host, "inspect_host", return_value={"errors": [], "result": "PASS"}), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, host.main([]))


class WorkflowWiringTests(unittest.TestCase):
    def setUp(self):
        self.workflow = (SCRIPT.parent.parent / ".github/workflows/v180-development.yml").read_text(encoding="utf-8")

    def test_clean_build_disables_task_output_cache(self):
        command = next(line.strip() for line in self.workflow.splitlines()
                       if line.strip().startswith("./gradlew clean build "))
        self.assertIn("--no-build-cache", command.split())

    def test_runtime_temporary_directories_match_sampled_runner_temp(self):
        first_run = re.search(r"      - name: Verify checkout and execution host before setup\n(.*?)"
                              r"      - name: Set up Java 17\n", self.workflow, re.DOTALL).group(1)
        env = re.search(r"        env:\n(.*?)        run: \|\n", first_run, re.DOTALL).group(1)
        for key in ("TMPDIR", "TMP", "TEMP"):
            self.assertIn(f"          {key}: ${{{{ runner.temp }}}}\n", env)
        self.assertIn("          JAVA_TOOL_OPTIONS: -Djava.io.tmpdir=${{ runner.temp }}\n", env)
        for key in ("EVIDENCE_DIR", "TMPDIR", "TMP", "TEMP", "JAVA_TOOL_OPTIONS"):
            self.assertIn(f'"{key}=${key}"', first_run)
        self.assertIn('>> "$GITHUB_ENV"', first_run)

    def test_job_environment_does_not_use_unavailable_runner_context(self):
        env = re.search(r"    env:\n(.*?)    defaults:\n", self.workflow, re.DOTALL).group(1)
        self.assertNotIn("runner.", env)

    def test_failure_upload_is_bounded_without_checkout_or_preflight_exports(self):
        upload = re.search(r"      - name: Upload raw regression evidence, including failed runs\n(.*?)"
                           r"      - name: Upload the single external build artifact\n",
                           self.workflow, re.DOTALL).group(1)
        self.assertIn("        if: always()\n", upload)
        paths = re.search(r"          path: \|\n(.*)", upload, re.DOTALL).group(1)
        self.assertEqual([
            "${{ runner.temp }}/arce-v180-regression/",
            "build/reports/tests/test/",
            "build/test-results/test/",
            "build/gametest/logs/",
        ], [line.strip() for line in paths.splitlines() if line.strip()])


if __name__ == "__main__":
    unittest.main()
