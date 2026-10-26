"""Fail-closed tests for the W129bc registry-writer native cut harness's pure checks."""
import gzip
import struct
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_registry_writer_cuts as runner


def nbt_string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def compound(fields):
    return b"".join(fields) + b"\x00"


def named(tag, name, payload):
    return bytes([tag]) + nbt_string(name) + payload


def compounds(name, entries):
    body = b"\x0a" + struct.pack(">i", len(entries)) if entries else b"\x00" + struct.pack(">i", 0)
    return named(0x09, name, body + b"".join(compound(entry) for entry in entries))


def saved_data(fields):
    return gzip.compress(b"\x0a" + nbt_string("") + named(0x0a, "data", compound(fields)) + b"\x00")


def registry_bytes(satellites, stamps, epoch, statuses=None):
    """{data: {save_epoch, satellites: [{satellite_id}], missions: [{start_epoch, status}]}}."""
    satellite_entries = [[named(0x0b, "satellite_id", struct.pack(">i", 4) + struct.pack(">4i", 0, 0, 0, index + 1))]
                         for index in range(satellites)]
    statuses = statuses or ["running"] * len(stamps)
    mission_entries = [[named(0x04, "start_epoch", struct.pack(">q", stamp)), named(0x08, "status", nbt_string(status))]
                       for stamp, status in zip(stamps, statuses)]
    return saved_data([named(0x04, "save_epoch", struct.pack(">q", epoch)), compounds("satellites", satellite_entries),
                       compounds("missions", mission_entries)])


def celestial_bytes(bodies):
    return saved_data([compounds("bodies", [[named(0x08, "id", nbt_string(body))] for body in bodies])])


STATUS = ("[12:00:00] [Server thread/INFO] [x/]: ARCE_RELEASE_SRW_STATUS sequence=7 acknowledged=5 outcome=COMMITTED "
          "busy=true dirty=true epoch=3 satellites=1 missions=2 terminal=present package=bound result=SUCCESS pending=1 "
          "research=CLAIM_PENDING_DISCOVERY discovered=false balance=20\n")


class StatusTests(unittest.TestCase):
    def test_status_line_parses_every_field(self):
        self.assertEqual(dict(sequence=7, acknowledged="5", outcome="COMMITTED", busy=True, dirty=True, epoch=3,
                              satellites=1, missions=2, terminal="present", package="bound", result="SUCCESS", pending=1,
                              research="CLAIM_PENDING_DISCOVERY", discovered=False, balance=20),
                         runner.parse_status(STATUS))

    def test_a_line_without_status_is_refused(self):
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_status("ARCE_RELEASE_SRW_REQUESTED result=true sequence=3")


class InspectTests(unittest.TestCase):
    def test_satellites_staging_stamps_and_discovery(self):
        with TemporaryDirectory() as raw:
            world = Path(raw); (world / "data").mkdir()
            (world / "data" / runner.REGISTRY).write_bytes(registry_bytes(2, [1, 2], 2))
            result = runner.inspect(world)
            self.assertEqual(2, result["satellites"]); self.assertFalse(result["staging"])
            self.assertFalse(result["mars_discovered"])
            (world / "data" / runner.STAGING).write_bytes(registry_bytes(1, [1], 1))
            (world / "data" / runner.CELESTIAL).write_bytes(celestial_bytes([runner.MARS]))
            result = runner.inspect(world)
            self.assertEqual(1, result["staging_satellites"]); self.assertTrue(result["mars_discovered"])

    def test_a_stamp_ahead_of_the_durable_epoch_is_refused(self):
        with TemporaryDirectory() as raw:
            world = Path(raw); (world / "data").mkdir()
            (world / "data" / runner.REGISTRY).write_bytes(registry_bytes(1, [3], 2))
            with self.assertRaises(runner.smoke.SmokeError):
                runner.inspect(world)

    def test_a_missing_baseline_file_is_refused(self):
        with TemporaryDirectory() as raw:
            (Path(raw) / "data").mkdir()
            with self.assertRaises(runner.smoke.SmokeError):
                runner.inspect(Path(raw))

    def test_uuids_decode_from_int_arrays(self):
        self.assertEqual("00000000-0000-0000-0000-000000000001", runner.uuid_of([0, 0, 0, 1]))
        self.assertEqual("ffffffff-0000-0000-0000-000000000000", runner.uuid_of([-1, 0, 0, 0]))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.uuid_of([1, 2])


class FirstStartTests(unittest.TestCase):
    DRAIN = "ARCE_CHECKED_WRITER_DRAINED root=satellite_registry target=9 committed=9 attempts=2\n"
    FAILED = ["ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=FAILED sequence=4 epoch=2 durable=1; retained dirty state\n",
              "ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root=satellite_registry target=5 committed=2 attempts=3 "
              "reason=BUDGET_EXHAUSTED\n"]

    def test_stale_success_needs_one_covering_second_attempt(self):
        lines = ["ARCE_RELEASE_SRW_STOPPING_EVENT sequence=8\n", self.DRAIN]
        self.assertTrue(runner.check_first_start("R6", lines, 0)["stopping_event"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R6", [lines[0], self.DRAIN.replace("attempts=2", "attempts=1")], 0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_STALE", ["ARCE_RELEASE_SRW_CRASH sequence=9 in_flight=true\n"] + lines, None)

    def test_exhaustion_counts_an_attempt_in_flight_at_the_crash(self):
        injected = "ARCE_RELEASE_SRW_INJECTED point=STAGED mode=FAIL\n"
        idle = ["ARCE_RELEASE_SRW_CRASH sequence=5 in_flight=false\n"] + [injected] * 3 + self.FAILED
        self.assertEqual(1, len(runner.check_first_start("R7_FAILED", idle, 0)["incomplete"]))
        flying = [injected, "ARCE_RELEASE_SRW_CRASH sequence=5 in_flight=true\n"] + [injected] * 2 + self.FAILED
        self.assertEqual(1, len(runner.check_first_start("R7_FAILED", flying, 0)["incomplete"]))
        with self.assertRaises(runner.smoke.SmokeError):  # an in-flight attempt plus three more is over budget
            runner.check_first_start("R7_FAILED", flying[:2] + [injected] * 3 + self.FAILED, 0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_FAILED", idle + [self.DRAIN], 0)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_UNKNOWN", idle, 0)

    def test_interrupted_teardown_is_never_a_drain(self):
        lines = ["ARCE_RELEASE_SRW_CRASH sequence=4 in_flight=true\n", "A single server tick took 10.00 seconds\n"]
        self.assertTrue(runner.check_first_start("R7_INTERRUPTED", lines, 1)["watchdog"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_INTERRUPTED", lines + [self.DRAIN], 1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_INTERRUPTED", lines[:1], 1)

    def test_kills_report_no_drain_and_writer_throwables_always_fail(self):
        self.assertIsNone(runner.check_first_start("R2", [], None)["exit_code"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R3", [self.DRAIN], None)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R1", ["ARCE_CHECKED_WRITER_THROWABLE root=satellite_registry sequence=2\n"], None)

    def test_a_crash_that_raised_a_stopping_event_fails(self):
        with self.assertRaises(runner.smoke.SmokeError):
            runner.check_first_start("R7_INTERRUPTED", ["ARCE_RELEASE_SRW_CRASH sequence=4 in_flight=true\n",
                                                        "ARCE_RELEASE_SRW_STOPPING_EVENT sequence=4\n",
                                                        "A single server tick took 10.00 seconds\n"], 1)


class PinTests(unittest.TestCase):
    def test_an_export_must_name_exactly_the_given_source(self):
        with TemporaryDirectory() as raw:
            repo = Path(raw); (repo / "scripts").mkdir()
            (repo / runner.EXPORT_MARKER).write_text("abc123+patch\n", encoding="ascii")
            original = runner.__file__
            try:
                runner.__file__ = str(repo / "scripts" / "run_v180_registry_writer_cuts.py")
                self.assertIsNone(runner.assert_pin("abc123+patch", False))
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.assert_pin("abc123", False)
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.assert_pin("abc123+patch", True)
            finally:
                runner.__file__ = original


class CaseTableTests(unittest.TestCase):
    def test_every_case_names_a_supported_cut_and_fits_the_start_limit(self):
        for case, (fixture, point, mode, ending, satellites, staging) in runner.CASES.items():
            self.assertIn(fixture, ("registry", "terminal", "flight", "discovery"), case)
            self.assertIn(point, (None, "CAPTURE", "STAGED", "MOVED"), case)
            self.assertIn(ending, ("kill", "stop", "crash"), case)
            self.assertFalse(mode == "FAIL" and point != "STAGED" or mode == "UNKNOWN" and point != "MOVED", case)
        self.assertLessEqual(len(runner.CASES) * runner.STARTS_PER_CASE, runner.MAX_STARTS)
        self.assertTrue({"R1", "R2", "R3", "R4", "R5", "R6", "R7_STALE", "R7_FAILED", "R7_UNKNOWN", "R7_INTERRUPTED",
                         "DISCOVERY"} <= set(runner.CASES))


if __name__ == "__main__":
    unittest.main()
