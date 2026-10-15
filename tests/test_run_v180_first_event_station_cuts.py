import copy
from pathlib import Path
import unittest
from unittest.mock import patch
from scripts import run_v180_first_event_station_cuts as cuts


class StationOracleTest(unittest.TestCase):
    def test_refused_warp_must_preserve_both_station_records_and_energy(self):
        baseline = {cuts.OUTBOX: {"schema_version": 1, "state": "EMPTY"}, "stations": [{"name": "first"}, {"name": "other"}],
                    "reservations": [], "warp_energy": [{"energy": 4_000_000}], "orbital_clock": {"logical_tick": 10}}
        with patch.object(cuts.moon, "read_root", return_value=baseline):
            self.assertEqual("EMPTY", cuts.inspect(Path("world"), "FW1", "EMPTY", False, None, baseline)["outbox"])
        for key in ("stations", "reservations", "warp_energy"):
            changed = copy.deepcopy(baseline)
            changed[key] = [{"mutated": True}]
            with self.subTest(key=key), patch.object(cuts.moon, "read_root", return_value=changed):
                with self.assertRaises(cuts.smoke.SmokeError):
                    cuts.inspect(Path("world"), "FW1", "EMPTY", False, None, baseline)

    def test_refused_candidate_and_backwards_clock_are_not_empty_authority(self):
        baseline = {cuts.OUTBOX: {"schema_version": 1, "state": "EMPTY"}, "stations": [], "reservations": [],
                    "warp_energy": [], "orbital_clock": {"logical_tick": 10}}
        for change in ({cuts.OUTBOX: {"schema_version": 1, "state": "EMPTY", "event": {}}},
                       {"orbital_clock": {"logical_tick": 9}}):
            changed = dict(baseline, **change)
            with patch.object(cuts.moon, "read_root", return_value=changed):
                with self.assertRaises(cuts.smoke.SmokeError):
                    cuts.inspect(Path("world"), "FW1", "EMPTY", False, None, baseline)


if __name__ == "__main__": unittest.main()
