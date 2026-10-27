"""Fail-closed tests for the W129a transaction-writer native cut harness's pure checks."""
import gzip
import struct
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_transaction_writer_cuts as runner


def nbt_string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def journal_bytes(dimensions):
    """A gzip SavedData file: {data: {schema_version: 2, transactions: [{transaction_id, dimension}]}}."""
    entries = b""
    for index, dimension in enumerate(dimensions):
        entries += (b"\x0b" + nbt_string("transaction_id") + struct.pack(">i", 4) + struct.pack(">4i", 0, 0, 0, index + 1)
                    + b"\x08" + nbt_string("dimension") + nbt_string(dimension) + b"\x00")
    data = (b"\x03" + nbt_string("schema_version") + struct.pack(">i", 2)
            + b"\x09" + nbt_string("transactions") + (b"\x0a" if dimensions else b"\x00") + struct.pack(">i", len(dimensions))
            + entries + b"\x00")
    root = b"\x0a" + nbt_string("") + b"\x0a" + nbt_string("data") + data + b"\x00"
    return gzip.compress(root)


ABSENT = "advancedrocketrycommunity:w129a_absent"


class StatusTests(unittest.TestCase):
    def test_status_line_parses_every_field(self):
        line = ("[12:00:00] [Server thread/INFO] [x/]: ARCE_RELEASE_TXW_STATUS b14=WRITE_PENDING sequence=7 "
                "acknowledged=5 outcome=COMMITTED busy=true dirty=true records=1\n")
        self.assertEqual(dict(b14="WRITE_PENDING", sequence=7, acknowledged="5", outcome="COMMITTED", busy=True,
                              dirty=True, records=1), runner.parse_status(line))

    def test_a_line_without_status_is_refused(self):
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_status("ARCE_RELEASE_TXW_REQUESTED result=REQUESTED sequence=3")


class InspectTests(unittest.TestCase):
    def test_records_staging_and_foreign_dimensions(self):
        with TemporaryDirectory() as raw:
            world = Path(raw); (world / "data").mkdir()
            (world / "data" / runner.JOURNAL).write_bytes(journal_bytes([ABSENT, ABSENT]))
            result = runner.inspect(world)
            self.assertEqual(2, result["records"]); self.assertFalse(result["staging"])
            (world / "data" / runner.STAGING).write_bytes(journal_bytes([ABSENT]))
            self.assertEqual(1, runner.inspect(world)["staging_records"])
            (world / "data" / runner.JOURNAL).write_bytes(journal_bytes(["minecraft:overworld"]))
            with self.assertRaises(runner.smoke.SmokeError):
                runner.inspect(world)

    def test_a_missing_baseline_file_is_refused(self):
        with TemporaryDirectory() as raw:
            (Path(raw) / "data").mkdir()
            with self.assertRaises(runner.smoke.SmokeError):
                runner.inspect(Path(raw))


class FirstStartTests(unittest.TestCase):
    DRAIN = "ARCE_CHECKED_WRITER_DRAINED root=rocket_transactions target=9 committed=9 attempts=2\n"

    def test_stale_success_needs_one_covering_second_attempt(self):
        lines = ["ARCE_RELEASE_TXW_STOPPING_EVENT sequence=8\n", self.DRAIN]
        self.assertTrue(runner.check_first_start("R6", lines, 0)["stopping_event"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R6", [lines[0], self.DRAIN.replace("attempts=2", "attempts=1")], 0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_STALE", ["ARCE_RELEASE_TXW_CRASH sequence=9\n"] + lines, None)

    def test_exhaustion_is_one_warning_with_three_attempts_and_one_streak(self):
        lines = ["ARCE_RELEASE_TXW_CRASH sequence=4\n"] + ["ARCE_RELEASE_TXW_INJECTED point=STAGED mode=FAIL\n"] * 3 + [
            "ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=NOT_COMMITTED sequence=4 acknowledged=2\n",
            "ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root=rocket_transactions target=4 committed=2 attempts=3 "
            "reason=BUDGET_EXHAUSTED\n"]
        self.assertEqual(1, len(runner.check_first_start("R7_FAILED", lines, 0)["incomplete"]))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_FAILED", lines + [self.DRAIN], 0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_UNKNOWN", lines, 0)

    def test_interrupted_teardown_is_never_a_drain(self):
        lines = ["ARCE_RELEASE_TXW_CRASH sequence=4\n", "A single server tick took 10.00 seconds\n"]
        self.assertTrue(runner.check_first_start("R7_INTERRUPTED", lines, 1)["watchdog"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_INTERRUPTED", lines + [self.DRAIN], 1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_INTERRUPTED", lines[:1], 1)

    def test_kills_report_no_drain_and_writer_throwables_always_fail(self):
        self.assertIsNone(runner.check_first_start("T2", [], None)["exit_code"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("T3", [self.DRAIN], None)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("T1", ["ARCE_CHECKED_WRITER_THROWABLE root=rocket_transactions sequence=2\n"], None)

    def test_every_case_names_a_supported_cut(self):
        for case, (point, mode, ending, records, staging) in runner.CASES.items():
            self.assertIn(point, ("CAPTURE", "STAGED", "MOVED"), case)
            self.assertIn(ending, ("kill", "stop", "crash"), case)
            self.assertFalse(mode == "FAIL" and point != "STAGED" or mode == "UNKNOWN" and point != "MOVED", case)


if __name__ == "__main__":
    unittest.main()
