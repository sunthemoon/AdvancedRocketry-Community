import copy
from pathlib import Path
import unittest
from unittest.mock import patch
from scripts import run_v180_first_event_paired_cuts as cuts
from scripts.inspect_celestial_saved_data import NbtReader, NbtError


class PairedOracleTest(unittest.TestCase):
    def roots(self):
        return {cuts.moon.JOURNAL: {cuts.moon.OUTBOX: {"schema_version": 1, "state": "DELIVERED", "event": {"id": "moon"}}, "transfers": []},
                cuts.station.STATIONS: {cuts.station.OUTBOX: {"schema_version": 1, "state": "DELIVERED", "event": {"id": "warp"}},
                                         "stations": [], "reservations": [], "orbital_clock": {"logical_tick": 1}},
                cuts.moon.PROGRESSION: None}

    def test_a_missing_receipt_hold_cannot_create_the_file_or_change_either_source(self):
        expected = self.roots()
        with patch.object(cuts, "roots", return_value=expected):
            self.assertFalse(cuts.check("FP2", Path("world"), expected)["progression_present"])
        actual = copy.deepcopy(expected); actual[cuts.moon.PROGRESSION] = {"created": True}
        with patch.object(cuts, "roots", return_value=actual):
            with self.assertRaises(cuts.smoke.SmokeError): cuts.check("FP2", Path("world"), expected)

    def test_runtime_unavailability_does_not_authorize_a_different_root(self):
        expected = self.roots(); actual = copy.deepcopy(expected)
        actual[cuts.moon.JOURNAL][cuts.moon.OUTBOX]["state"] = "EMPTY"
        with patch.object(cuts, "roots", return_value=actual):
            with self.assertRaises(cuts.smoke.SmokeError): cuts.check("FP10", Path("world"), expected)

    def test_root_time_can_advance_but_event_time_or_authority_cannot_change(self):
        expected = self.roots(); actual = copy.deepcopy(expected)
        actual[cuts.station.STATIONS]["orbital_clock"]["logical_tick"] += 1
        self.assertTrue(cuts.same_authority(actual, expected))
        actual[cuts.station.STATIONS][cuts.station.OUTBOX]["event"]["orbital_tick"] = 2
        self.assertFalse(cuts.same_authority(actual, expected))

    def test_root_time_cannot_roll_back_and_other_clock_fields_remain_exact(self):
        expected = self.roots(); actual = copy.deepcopy(expected)
        actual[cuts.station.STATIONS]["orbital_clock"]["logical_tick"] = 0
        self.assertFalse(cuts.same_authority(actual, expected))
        actual = copy.deepcopy(expected)
        actual[cuts.station.STATIONS]["orbital_clock"]["schema_version"] = 2
        self.assertFalse(cuts.same_authority(actual, expected))

    def test_celestial_reader_default_budget_stays_unchanged_and_large_fixture_budget_is_bounded(self):
        payload = b"\x0a\0\0\0"
        self.assertEqual({}, NbtReader(payload).read_root())
        for count in (0, 1_048_577):
            with self.assertRaises(NbtError): NbtReader(payload, max_tags=count)
        self.assertEqual(4096, NbtReader(payload).max_tags)


if __name__ == "__main__": unittest.main()
