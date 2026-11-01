"""The narrow Gradle completion check shares the full checker's run reader."""

import contextlib
import io
import os
import unittest

from scripts.check_gametest_log import main
from tests.test_check_gametest_log import (
    AFTER_MIDNIGHT,
    BEFORE_MIDNIGHT,
    COMPLETE,
    CRASHED_RUN,
    SHUTDOWN,
    LogFolder,
    midnight_split,
    restamp,
    standard_log,
)


class CompletionOnlyTests(unittest.TestCase):
    def run_main(self, path):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = main([str(path), "--completion-only", "--manifest", "does-not-exist.json"])
        return code, out.getvalue(), err.getvalue()

    def test_split_with_marker_in_latest_passes(self):
        head, tail = midnight_split(standard_log())
        self.assertNotIn("required tests passed", head)
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", head)
        code, out, err = self.run_main(folder.write("latest.log", tail))
        self.assertEqual(0, code, err)
        self.assertIn("rebuilt from", out)
        self.assertIn("[PASS]", out)

    def test_split_with_marker_only_in_rolled_part_passes(self):
        text = standard_log()
        cut = text.index(SHUTDOWN)
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", restamp(text[:cut], BEFORE_MIDNIGHT))
        tail = restamp(text[cut:], AFTER_MIDNIGHT)
        self.assertNotIn("required tests passed", tail)
        code, out, err = self.run_main(folder.write("latest.log", tail))
        self.assertEqual(0, code, err)
        self.assertIn("rebuilt from", out)

    def test_crashed_run_cannot_use_fresh_startup_rolled_predecessor(self):
        # The previous run may have stopped after its summary but before shutdown.
        # Both startup-rolled archives are fresh; mtime cannot identify this run.
        for previous in (standard_log(), standard_log().split(SHUTDOWN)[0]):
            with self.subTest(previous_shutdown=SHUTDOWN in previous):
                folder = LogFolder(self)
                rolled = folder.write("2026-10-10-1.log.gz", restamp(previous, BEFORE_MIDNIGHT))
                latest = folder.write("latest.log", restamp(CRASHED_RUN, AFTER_MIDNIGHT))
                os.utime(rolled, (latest.stat().st_mtime + 1, latest.stat().st_mtime + 1))
                code, out, err = self.run_main(latest)
                self.assertEqual(1, code)
                self.assertNotIn("rebuilt", out)
                self.assertIn("completion marker is missing", err)

    def test_unsplit_run_keeps_marker_only_semantics(self):
        # Gradle never checked expectation counts or required a shutdown here.
        # Those remain the responsibility of the separate full checker.
        for text, expected in (
            (standard_log(), 0),
            (standard_log(refusals=1), 0),
            (standard_log().split(SHUTDOWN)[0], 0),
            (CRASHED_RUN, 1),
            (standard_log().replace(COMPLETE[1], ""), 1),
        ):
            with self.subTest(text=text[-100:]):
                code, out, err = self.run_main(LogFolder(self).write("latest.log", text))
                self.assertEqual(expected, code, err)
                self.assertNotIn("rebuilt", out)

    def test_split_without_this_runs_marker_fails(self):
        head, tail = midnight_split(standard_log().replace(COMPLETE[1], ""))
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", head)
        # An unrelated completed predecessor must not supply the marker either.
        folder.write("2026-10-10-2.log.gz", restamp(standard_log(), "10Oct2026 23:00:00.000"))
        code, out, err = self.run_main(folder.write("latest.log", tail))
        self.assertEqual(1, code)
        self.assertIn("rebuilt from", out)
        self.assertIn("completion marker is missing", err)

    def test_malformed_gaps_fail_even_with_a_marker_in_latest(self):
        for before, after in (
            (BEFORE_MIDNIGHT, "10Oct2026 23:59:59.000"),
            ("10Oct2026 23:30:00.000", AFTER_MIDNIGHT),
            (BEFORE_MIDNIGHT, "12Oct2026 00:00:01.000"),
        ):
            with self.subTest(before=before, after=after):
                head, tail = midnight_split(standard_log(), before, after)
                folder = LogFolder(self)
                folder.write("2026-10-10-1.log.gz", head)
                code, out, err = self.run_main(folder.write("latest.log", tail))
                self.assertEqual(1, code)
                self.assertNotIn("[PASS]", out)
                self.assertIn("cannot be rebuilt", err)

    def test_missing_or_ambiguous_rolled_part_fails(self):
        head, tail = midnight_split(standard_log())
        for copies in (0, 2):
            with self.subTest(copies=copies):
                folder = LogFolder(self)
                for number in range(copies):
                    folder.write(f"2026-10-10-{number + 1}.log.gz", head)
                code, out, err = self.run_main(folder.write("latest.log", tail))
                self.assertEqual(1, code)
                self.assertNotIn("[PASS]", out)
                self.assertIn("cannot be rebuilt", err)

    def test_completed_previous_run_is_not_a_missing_first_part(self):
        _, tail = midnight_split(standard_log())
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", restamp(standard_log(), BEFORE_MIDNIGHT))
        code, _, err = self.run_main(folder.write("latest.log", tail))
        self.assertEqual(1, code)
        self.assertIn("belongs to an earlier run", err)

    def test_unreadable_latest_log_is_an_input_error(self):
        folder = LogFolder(self)
        for name in ("missing.log", "latest.log"):
            with self.subTest(name=name):
                if name == "latest.log":
                    (folder.path / name).write_bytes(b"\xff")
                code, _, err = self.run_main(folder.path / name)
                self.assertEqual(2, code)
                self.assertIn("Cannot read GameTest log", err)

    def test_inventory_and_completion_only_are_mutually_exclusive(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
            main(["--inventory", "--completion-only"])
        self.assertEqual(2, error.exception.code)


if __name__ == "__main__":
    unittest.main()
