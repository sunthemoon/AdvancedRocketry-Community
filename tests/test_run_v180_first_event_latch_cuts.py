"""The latch campaign's raw-root oracle, not a native pass claim."""
import unittest
from pathlib import Path
from unittest.mock import patch
from scripts import run_v180_first_event_latch_cuts as cuts


class LatchRootOracleTest(unittest.TestCase):
    def roots(self, event, transfers=None):
        return [{cuts.moon.OUTBOX: {"state": "DELIVERED", "event": event}, "transfers": transfers or []},
                {cuts.moon.MOON_RECEIPT: {"occurred": 1, "event": event}, cuts.moon.WARP_RECEIPT: {"occurred": 0}}]

    def test_fp5_accepts_the_root_event_without_a_transfer_record(self):
        event = {"event_id": [1, 2, 3, 4]}
        with patch.object(cuts.moon, "read_root", side_effect=self.roots(event)):
            self.assertEqual([], cuts.inspect(Path("world"), "FP5", event)["transfers"])

    def test_fp5_rejects_new_records_or_a_different_valid_event(self):
        event = {"event_id": [1, 2, 3, 4]}
        with patch.object(cuts.moon, "read_root", side_effect=self.roots(event, [{"phase": "COMMITTED"}])):
            with self.assertRaisesRegex(cuts.smoke.SmokeError, "zero-record"):
                cuts.inspect(Path("world"), "FP5", event)
        with patch.object(cuts.moon, "read_root", side_effect=self.roots({"event_id": [4, 3, 2, 1]})):
            with self.assertRaisesRegex(cuts.smoke.SmokeError, "exact DELIVERED"):
                cuts.inspect(Path("world"), "FP5", event)

    def test_matching_source_does_not_excuse_a_different_receipt(self):
        event = {"event_id": [1, 2, 3, 4]}
        roots = self.roots(event)
        roots[1][cuts.moon.MOON_RECEIPT]["event"] = {"event_id": [4, 3, 2, 1]}
        with patch.object(cuts.moon, "read_root", side_effect=roots):
            with self.assertRaisesRegex(cuts.smoke.SmokeError, "receipt"):
                cuts.inspect(Path("world"), "FP5", event)


if __name__ == "__main__":
    unittest.main()
