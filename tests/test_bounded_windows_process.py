"""Self-contained producers for the new Windows command observer."""
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import bounded_windows_process as observer


@unittest.skipUnless(os.name == "nt", "Windows handle/job protocol")
class BoundedWindowsProcessTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="bounded-observer-")
        self.directory = Path(self.temporary.name).resolve()
        self.addCleanup(self.cleanup_owned)
        self.stdout = self.directory / "stdout.bin"
        self.stderr = self.directory / "stderr.bin"

    def cleanup_owned(self):
        self.assertEqual(Path(tempfile.gettempdir()).resolve(), self.directory.parent)
        self.assertTrue(self.directory.name.startswith("bounded-observer-"))
        self.assertFalse(self.directory.is_symlink() or self.directory.is_junction())
        self.temporary.cleanup()

    def run_code(self, code, **options):
        return observer.observe([sys.executable, "-I", "-X", "utf8", "-B", "-c", code],
            cwd=self.directory, env=dict(os.environ), stdout_path=self.stdout,
            stderr_path=self.stderr, timeout=options.pop("timeout", 5), **options)

    def assert_completed(self, result):
        self.assertTrue(result["job_assigned_before_resume"])
        self.assertTrue(result["primary_thread_resumed"])
        self.assertTrue(result["capture_complete"], result)
        self.assertLessEqual(result["postdeadline_total_seconds"], result["postdeadline_limit_seconds"])
        self.assertFalse(result["errors"], result)

    def test_zero_exit_and_exact_small_dual_streams(self):
        result = self.run_code("import os; os.write(1,b'out'); os.write(2,b'err')")
        self.assert_completed(result)
        self.assertTrue(result["ok"], result)
        self.assertEqual("EXITED", result["outcome"])
        self.assertEqual(0, result["original_exit"])
        self.assertEqual(b"out", self.stdout.read_bytes())
        self.assertEqual(b"err", self.stderr.read_bytes())

    def test_nonzero_exit_is_not_successful_capture_acceptance(self):
        result = self.run_code("import os; os.write(2,b'failure'); raise SystemExit(7)")
        self.assert_completed(result)
        self.assertEqual(7, result["original_exit"])
        self.assertFalse(result["ok"])
        self.assertEqual(b"failure", self.stderr.read_bytes())

    def test_small_flushed_prefix_is_observed_before_original_timeout(self):
        result = self.run_code("import os,time; os.write(1,b'prefix'); os.write(2,b'error-prefix'); time.sleep(30)",
                               timeout=1)
        self.assert_completed(result)
        self.assertEqual("TIMEOUT", result["outcome"])
        self.assertIsNone(result["original_exit"])
        self.assertIsNotNone(result["postdeadline_owned_exit"])
        self.assertFalse(result["ok"])
        for stream in result["streams"].values():
            self.assertLess(stream["first_byte_elapsed_seconds"], 1)
        self.assertEqual(b"prefix", self.stdout.read_bytes())
        self.assertEqual(b"error-prefix", self.stderr.read_bytes())

    def test_output_overflow_is_bounded_prefix_not_success(self):
        result = self.run_code("import os,time; os.write(1,b'x'*20000); time.sleep(30)", output_cap=128)
        self.assertFalse(result["ok"])
        self.assertFalse(result["capture_complete"])
        self.assertTrue(result["streams"]["stdout"]["overflow"])
        self.assertEqual(129, result["streams"]["stdout"]["observed_bytes"])
        self.assertEqual(b"x" * 128, self.stdout.read_bytes())
        self.assertFalse(result["streams"]["stdout"]["reader_alive"])
        self.assertLessEqual(result["postdeadline_total_seconds"], 10)

    def test_parent_exit_closes_only_its_new_job_and_inherited_pipes(self):
        code = ("import subprocess,sys,os; "
                "subprocess.Popen([sys.executable,'-I','-B','-c','import time; time.sleep(30)']); "
                "os.write(1,b'parent-done')")
        result = self.run_code(code)
        self.assert_completed(result)
        self.assertEqual(0, result["original_exit"])
        self.assertTrue(result["ok"], result)
        self.assertEqual(b"parent-done", self.stdout.read_bytes())

    def test_missing_executable_publishes_launch_failure(self):
        result = observer.observe([str(self.directory / "missing.exe")], cwd=self.directory,
            env=dict(os.environ), stdout_path=self.stdout, stderr_path=self.stderr, timeout=1)
        self.assertEqual("LAUNCH_ERROR", result["outcome"])
        self.assertIsNone(result["pid"])
        self.assertFalse(result["primary_thread_resumed"])
        self.assertFalse(result["ok"])
        self.assertTrue(result["errors"])
        self.assertLessEqual(result["postdeadline_total_seconds"], 10)

    def test_failed_job_assignment_never_resumes_our_suspended_child(self):
        real_kernel = observer._kernel
        def failing_kernel():
            kernel = real_kernel()
            kernel.AssignProcessToJobObject = lambda *args: False
            return kernel
        with patch.object(observer, "_kernel", side_effect=failing_kernel):
            result = self.run_code("import os; os.write(1,b'must-not-run')")
        self.assertEqual("LAUNCH_ERROR", result["outcome"])
        self.assertIsNotNone(result["pid"])
        self.assertFalse(result["job_assigned_before_resume"])
        self.assertFalse(result["primary_thread_resumed"])
        self.assertFalse(result["ok"])
        self.assertEqual(b"", self.stdout.read_bytes())
        self.assertIsNotNone(result["postdeadline_owned_exit"])

    def test_existing_output_is_never_overwritten(self):
        self.stdout.write_bytes(b"existing")
        result = self.run_code("print('new')")
        self.assertEqual("LAUNCH_ERROR", result["outcome"])
        self.assertIsNone(result["pid"])
        self.assertEqual(b"existing", self.stdout.read_bytes())
        self.assertFalse(result["ok"])

    def test_second_output_failure_releases_first_file_without_launch(self):
        self.stderr.write_bytes(b"existing")
        with patch.object(observer._WindowsOwner, "launch") as launch:
            result = self.run_code("print('new')")
            launch.assert_not_called()
        self.assertEqual("LAUNCH_ERROR", result["outcome"])
        self.assertEqual(b"existing", self.stderr.read_bytes())
        renamed = self.directory / "closed-stdout.bin"
        self.stdout.rename(renamed)
        self.assertEqual(b"", renamed.read_bytes())

    def test_kernel_setup_failure_is_published_without_process(self):
        with patch.object(observer, "_kernel", side_effect=OSError("setup unavailable")):
            result = self.run_code("print('new')")
        self.assertEqual("LAUNCH_ERROR", result["outcome"])
        self.assertIsNone(result["pid"])
        self.assertFalse(result["ok"])
        self.assertTrue(any("setup unavailable" in error for error in result["errors"]))

    def test_interrupt_retires_new_job_before_propagation(self):
        real_owner, real_wait = observer._WindowsOwner, observer._winapi.WaitForSingleObject
        owners, waits = [], []
        def tracked_owner():
            owner = real_owner()
            owners.append(owner)
            return owner
        def interrupted_wait(*args):
            waits.append(args)
            if len(waits) == 1:
                raise KeyboardInterrupt()
            return real_wait(*args)
        with patch.object(observer, "_WindowsOwner", side_effect=tracked_owner), \
                patch.object(observer._winapi, "WaitForSingleObject", side_effect=interrupted_wait):
            with self.assertRaises(KeyboardInterrupt):
                self.run_code("import time; time.sleep(30)")
        self.assertEqual(1, len(owners))
        self.assertTrue(owners[0].assigned)
        self.assertIsNone(owners[0].job)
        self.assertIsNone(owners[0].process)
        self.assertIsNone(owners[0].thread)
        self.assertEqual([], owners[0].parent_fds)
        self.assertEqual({}, owners[0].readers)

    def test_invalid_inputs_fail_before_job_creation(self):
        defaults = dict(cwd=self.directory, env=dict(os.environ), stdout_path=self.stdout,
                        stderr_path=self.stderr, timeout=1)
        cases = (([], {}), (["python.exe"], {}), ([sys.executable], {"timeout": float("nan")}),
                 ([sys.executable], {"post_timeout": 11}), ([sys.executable], {"output_cap": 0}),
                 ([sys.executable], {"stderr_path": self.stdout}), ([sys.executable, "\0"], {}))
        with patch.object(observer, "_WindowsOwner") as creation:
            for argv, overrides in cases:
                with self.subTest(argv=argv, overrides=overrides), self.assertRaises(ValueError):
                    observer.observe(argv, **{**defaults, **overrides})
            creation.assert_not_called()
        self.assertFalse(self.stdout.exists())
        self.assertFalse(self.stderr.exists())


if __name__ == "__main__":
    unittest.main()
