"""Synthetic halt/thread oracles only; actual Moon cuts are recorded separately."""
import unittest
import hashlib
from pathlib import Path
import tempfile
import uuid
from unittest.mock import patch

from scripts import run_v180_first_event_moon_cuts as moon


class MoonCutBoundaryTest(unittest.TestCase):
    @staticmethod
    def process(case, thread, event="null"):
        class Process:
            lines = [f"ARCE_RELEASE_TEST first_event_cut id={case} boundary={moon.CASES[case][0]} "
                     f"event={event} thread={thread}"] * 2

            @staticmethod
            def finish(timeout):
                return 75
        return Process()

    def test_writer_force_cut_requires_the_writer_thread_and_deduplicates_its_marker(self):
        entry = moon.halted("FM2b", self.process("FM2b", "ARCE-rocket-journal"), 1)
        self.assertEqual("ARCE-rocket-journal", entry["thread"])
        self.assertEqual("null", entry["event"])
        with self.assertRaises(moon.smoke.SmokeError):
            moon.halted("FM2b", self.process("FM2b", "Server thread"), 1)

    def test_unknown_publication_cut_requires_the_server_thread(self):
        event = "00000000-0000-4000-8000-000000000001"
        entry = moon.halted("FM6a", self.process("FM6a", "Server thread", event), 1)
        self.assertEqual(event, entry["event"])
        with self.assertRaises(moon.smoke.SmokeError):
            moon.halted("FM6a", self.process("FM6a", "ARCE-rocket-journal", event), 1)

    def test_all_writer_boundaries_require_the_writer_thread(self):
        for case in ("FM1", "FM2a", "FM2b"):
            with self.subTest(case=case):
                self.assertEqual("ARCE-rocket-journal", moon.halted(case, self.process(case, "ARCE-rocket-journal"), 1)["thread"])
                with self.assertRaises(moon.smoke.SmokeError):
                    moon.halted(case, self.process(case, "Server thread"), 1)

    def test_participant_only_recovery_never_sets_a_cut(self):
        command = moon.command("java", None)
        self.assertIn("-Darce.releaseTest.firstEventParticipants=true", command)
        self.assertFalse(any("firstEventCut=" in arg for arg in command))


class MoonRootOracleTest(unittest.TestCase):
    def test_released_or_superseded_reservation_never_replaces_the_root_event(self):
        event_id = uuid.UUID("00000000-0000-4000-8000-000000000044")
        event = {"kind": "first_moon_landing", "event_id": moon.uuid_ints(event_id),
                 "participants": moon.uuid_ints(moon.P1) + moon.uuid_ints(moon.P2), "transfer_id": [1, 2, 3, 4]}
        event["fingerprint"] = hashlib.sha256(b"first_moon_landing\0" + event_id.bytes + (2).to_bytes(4, "big")
                                              + moon.P1.bytes + moon.P2.bytes).hexdigest()
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder)
            (world / "data").mkdir()
            (world / "data" / moon.JOURNAL).write_bytes(b"journal")
            journal = {moon.OUTBOX: {"state": "ELECTED", "event": event}, "transfers": []}
            with patch.object(moon, "read_root", return_value=journal):
                self.assertEqual(0, moon.inspect(world, "FM12a", str(event_id), "ELECTED", False)["transfers"])
                journal["transfers"] = [{"transfer_id": [4, 3, 2, 1], "phase": "PREPARED",
                                          "destination_snapshot": {"source_dimension": moon.EARTH}}]
                self.assertEqual(1, moon.inspect(world, "FM12b", str(event_id), "ELECTED", False)["transfers"])
                journal["transfers"][0]["destination_snapshot"]["source_dimension"] = moon.MOON
                with self.assertRaises(moon.smoke.SmokeError):
                    moon.inspect(world, "FM12b", str(event_id), "ELECTED", False)

    def test_empty_source_rejects_a_receipt_and_requires_the_committed_record(self):
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder)
            (world / "data").mkdir()
            (world / "data" / moon.JOURNAL).write_bytes(b"journal")
            journal = {moon.OUTBOX: {"schema_version": 1, "state": "EMPTY"}, "transfers": [{"phase": "COMMITTED"}]}
            with patch.object(moon, "read_root", return_value=journal):
                self.assertEqual("EMPTY", moon.inspect(world, "FM1", None, "EMPTY", False)["outbox"])
                with self.assertRaises(moon.smoke.SmokeError):
                    moon.inspect(world, "FM1", "an uncommitted candidate", "EMPTY", False)
                journal["transfers"] = []
                with self.assertRaises(moon.smoke.SmokeError):
                    moon.inspect(world, "FM1", None, "EMPTY", False)
            journal["transfers"] = [{"phase": "COMMITTED"}]
            (world / "data" / moon.PROGRESSION).write_bytes(b"progression")
            with patch.object(moon, "read_root", side_effect=[journal, {moon.MOON_RECEIPT: {"occurred": 1}}]):
                with self.assertRaises(moon.smoke.SmokeError):
                    moon.inspect(world, "FM1", None, "EMPTY", False)

    def test_fingerprint_is_recomputed_not_just_compared_between_roots(self):
        event_id = uuid.UUID("00000000-0000-4000-8000-000000000044")
        event = {"kind": "first_moon_landing", "event_id": moon.uuid_ints(event_id),
                 "participants": moon.uuid_ints(moon.P1) + moon.uuid_ints(moon.P2), "transfer_id": [1, 2, 3, 4]}
        event["fingerprint"] = hashlib.sha256(b"first_moon_landing\0" + event_id.bytes + (2).to_bytes(4, "big")
                                              + moon.P1.bytes + moon.P2.bytes).hexdigest()
        journal = {moon.OUTBOX: {"state": "ELECTED", "event": event},
                   "transfers": [{"transfer_id": event["transfer_id"], "phase": "COMMITTED"}]}
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder)
            (world / "data").mkdir()
            (world / "data" / moon.JOURNAL).write_bytes(b"journal")
            with patch.object(moon, "read_root", return_value=journal):
                self.assertEqual(event, moon.inspect(world, "FM2a", str(event_id), "ELECTED", False)["event"])
                event["fingerprint"] = "0" * 64
                with self.assertRaisesRegex(moon.smoke.SmokeError, "fingerprint"):
                    moon.inspect(world, "FM2a", str(event_id), "ELECTED", False)

    def test_snapshot_preserves_raw_roots_and_does_not_claim_an_award(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            world, evidence = root / "world", root / "evidence"
            (world / "data").mkdir(parents=True)
            evidence.mkdir()
            raw = b"the raw journal bytes"
            (world / "data" / moon.JOURNAL).write_bytes(raw)
            result = moon.snapshot(world, evidence, "FM1", 0)
            self.assertEqual(raw, (evidence / "FM1-start-0-roots" / moon.JOURNAL).read_bytes())
            self.assertEqual("N", result["award_oracle"])
            self.assertEqual([], result["advancement_files"])
            (world / "advancements").mkdir()
            (world / "advancements" / "player.json").write_text("{}")
            with self.assertRaisesRegex(moon.smoke.SmokeError, "advancement files"):
                moon.snapshot(world, evidence, "FM1", 1)


if __name__ == "__main__":
    unittest.main()
