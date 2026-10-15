import copy
import hashlib
from pathlib import Path
import unittest
import uuid
from unittest.mock import Mock, patch
from scripts import run_v180_first_event_paired_cuts as cuts
from scripts.inspect_celestial_saved_data import NbtReader, NbtError


class PairedOracleTest(unittest.TestCase):
    def roots(self):
        def event(kind, value):
            ident = uuid.UUID(int=value)
            fingerprint = hashlib.sha256(kind.encode() + b"\0" + ident.bytes + (2).to_bytes(4, "big")
                                         + cuts.moon.P1.bytes + cuts.moon.P2.bytes).hexdigest()
            return dict(event_id=cuts.moon.uuid_ints(ident), participants=cuts.moon.uuid_ints(cuts.moon.P1)
                        + cuts.moon.uuid_ints(cuts.moon.P2), fingerprint=fingerprint)
        return {cuts.moon.JOURNAL: {cuts.moon.OUTBOX: {"schema_version": 1, "state": "DELIVERED", "event": event("first_moon_landing", 44)}, "transfers": []},
                cuts.station.STATIONS: {cuts.station.OUTBOX: {"schema_version": 1, "state": "DELIVERED", "event": event("first_station_warp", 45)},
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

    def test_source_capture_requires_the_exact_participants_and_fingerprint(self):
        ident = uuid.UUID(int=44)
        kind = "first_moon_landing"
        fingerprint = hashlib.sha256(kind.encode() + b"\0" + ident.bytes + (2).to_bytes(4, "big")
                                     + cuts.moon.P1.bytes + cuts.moon.P2.bytes).hexdigest()
        event = dict(event_id=cuts.moon.uuid_ints(ident), participants=cuts.moon.uuid_ints(cuts.moon.P1)
                     + cuts.moon.uuid_ints(cuts.moon.P2), fingerprint=fingerprint)
        cuts.captured_event(kind, event)
        event["fingerprint"] = "0" * 64
        with self.assertRaises(cuts.smoke.SmokeError): cuts.captured_event(kind, event)
        event["fingerprint"] = fingerprint
        event["participants"] = cuts.moon.uuid_ints(cuts.moon.P2) + cuts.moon.uuid_ints(cuts.moon.P1)
        with self.assertRaises(cuts.smoke.SmokeError): cuts.captured_event(kind, event)

    def test_warp_preparation_waits_for_the_actual_recovered_committed_reservations(self):
        process = Mock()
        record = dict(transfer_id=cuts.moon.uuid_ints(uuid.UUID(int=44)), phase="COMMITTED")
        with patch.object(cuts.moon, "delivered"), patch.object(cuts.moon, "landed"), \
                patch.object(cuts.moon, "read_root", return_value={"transfers": [record]}):
            cuts.ready_to_warp("fixture", process, Path("world"), 120)
            self.assertIn("phase=COMMITTED", process.wait_for.call_args.args[0].pattern)
            record["phase"] = "PREPARED"
            with self.assertRaises(cuts.smoke.SmokeError): cuts.ready_to_warp("fixture", process, Path("world"), 120)

    def test_restart_holds_are_observed_after_startup_proof_and_the_service_step(self):
        def line(kind, source, receipt, hold):
            return f"kind={kind} source={source} event=none receipt={receipt} hold={hold}"
        temporary = [line("first_moon_landing", "SOURCE_UNCONFIRMED:UNCONFIRMED", "ABSENT", "none"),
                     line("first_station_warp", "KNOWN:DELIVERED", "ABSENT", "RECEIPT_MISSING")]
        settled = [line(kind, "KNOWN:DELIVERED", "ABSENT", "RECEIPT_MISSING")
                   for kind in ("first_moon_landing", "first_station_warp")]
        with patch.object(cuts, "statuses", side_effect=[temporary, settled]), patch.object(cuts.time, "sleep"):
            self.assertEqual(settled, cuts.await_holds(Mock(), ("RECEIPT_MISSING", "RECEIPT_MISSING"), 120))


if __name__ == "__main__": unittest.main()
