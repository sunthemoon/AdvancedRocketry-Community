import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w158_release_cuts as runner


class ReleaseCutRunnerTests(unittest.TestCase):
    def test_recovery_kill_is_independent_and_second_restart_is_clean(self):
        self.assertEqual("C5", runner.stop_boundary("C5", "seed", "B1_CAPTURE"))
        self.assertEqual("B1_CAPTURE", runner.stop_boundary("C5", "recover", "B1_CAPTURE"))
        self.assertEqual("S2_PENDING", runner.stop_boundary("C2", "recover", "S2_PENDING"))
        self.assertEqual("", runner.stop_boundary("C5", "check", "B1_CAPTURE"))
        self.assertEqual("", runner.stop_boundary("C5", "recover", ""))

    @staticmethod
    def pending_seed():
        return {"rockets": [{"entity": {"UUID": [1, 2, 3, 4], "RocketEntityData": {
            "snapshot": {"items": [42]}, "flight_data": {"fuel": {"amount": 1000, "debits": []},
            "passengers": {"assignments": []}}}}}], "journal": {"transfers": [], "outbox": {"state": "EMPTY"}},
            "sidecar": {"next_sequence": 1, "batches": [{"slots": ["source", "destination"]}],
                        "entries": [{"seat": "PENDING", "placement": "PENDING"}] * 2}}

    def test_absent_t_recovery_cuts_require_pending_seats_and_preserve_resources(self):
        import copy
        seed = self.pending_seed()
        baseline = runner.verify(seed, "H1", "C5", "seed", None)
        for boundary in ("B1_CAPTURE", "S2_PENDING"):
            runner.verify_recovery(seed, "H1", "C5", boundary, baseline, seed)
            for change in ("fuel", "seats", "sidecar", "journal"):
                changed = copy.deepcopy(seed)
                data = changed["rockets"][0]["entity"]["RocketEntityData"]
                if change == "fuel":
                    data["flight_data"]["fuel"]["debits"].append("new")
                elif change == "seats":
                    data["flight_data"]["passengers"]["assignments"].append({"player": "revived", "seat": 0})
                elif change == "sidecar":
                    changed["sidecar"]["entries"][0]["seat"] = "VERIFIED"
                else:
                    changed["journal"]["transfers"].append({"resurrected": True})
                with self.assertRaises(runner.smoke.SmokeError, msg=(boundary, change)):
                    runner.verify_recovery(changed, "H1", "C5", boundary, baseline, seed)
        verified = copy.deepcopy(seed)
        for entry in verified["sidecar"]["entries"]:
            entry["seat"] = "VERIFIED"
        runner.verify_recovery(verified, "H1", "C5", "C6", baseline, seed)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.verify_recovery(seed, "H1", "C6", "B1_CAPTURE", baseline, seed)

    def test_frozen_census_and_outbox_cannot_change_during_recovery(self):
        import copy
        seed = self.pending_seed()
        before = runner.preserved_stores(seed, None)
        for key in ("slots", "outbox"):
            changed = copy.deepcopy(seed)
            if key == "slots":
                changed["sidecar"]["batches"][0]["slots"] = ["source"]
            else:
                changed["journal"]["outbox"]["state"] = "DELIVERED"
            with self.assertRaises(runner.smoke.SmokeError):
                runner.preserved_stores(changed, before)

    def test_exact_census_rejects_a_wrong_surviving_player_with_the_same_count(self):
        def assignment(player, seat):
            return {"passenger_id": [22544384, 16384, -2147483648, player], "seat_index": seat}
        flight = {"passengers": {"assignments": [assignment(1, 0)]}}
        disk = {"rockets": [], "sidecar": None, "journal": {"transfers": [
            {"source_flight": flight, "destination_flight": flight}]}}
        runner.verify_seat_census(disk, "H2", "C5", "recover", "S2_PENDING")
        flight["passengers"]["assignments"] = [assignment(2, 0)]
        with self.assertRaises(runner.smoke.SmokeError):
            runner.verify_seat_census(disk, "H2", "C5", "recover", "S2_PENDING")

    def test_conflict_cut_keeps_t_but_has_already_saved_the_filtered_authority(self):
        assignments = [{"passenger_id": [22544384, 16384, -2147483648, i + 1], "seat_index": i} for i in range(2)]
        original = {"passengers": {"assignments": assignments}}
        clean = {"passengers": {"assignments": []}}
        disk = {"sidecar": None, "rockets": [{"entity": {"RocketEntityData": {"flight_data": clean}}}],
                "journal": {"transfers": [{"source_flight": original, "destination_flight": original}]}}
        runner.verify_seat_census(disk, "H1", "C16", "seed")
        clean["passengers"]["assignments"] = assignments
        with self.assertRaises(runner.smoke.SmokeError):
            runner.verify_seat_census(disk, "H1", "C16", "seed")

    def test_gone_restarts_preserve_the_frozen_resource_witness(self):
        import copy
        disk = {"rockets": [], "journal": {"transfers": []}, "sidecar": {"next_sequence": 1,
                "batches": [{"resources": {"snapshot": "original snapshot", "fuel": "original vector"}}],
                "entries": [{"seat": "VERIFIED", "placement": "PENDING"}] * 2}}
        witness = runner.verify(disk, "H1", "C14", "seed", None)
        self.assertIsNotNone(witness)
        runner.verify(disk, "H1", "C14", "recover", witness)
        changed = copy.deepcopy(disk); changed["sidecar"]["batches"][0]["resources"]["fuel"] = "changed"
        with self.assertRaises(runner.smoke.SmokeError):
            runner.verify(changed, "H1", "C14", "check", witness)

    def test_explicit_deletion_names_the_existing_retirement_warning(self):
        prefix = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
        transfer = "00000000-0000-4000-8000-000000000001"
        lines = [prefix + f"ARCE_TRANSFER_RECOVERY transfer={transfer} phase=PREPARED source_count=1 destination_count=0 action=KEEP_SOURCE status=WAITING_FOR_PASSENGERS\n",
                 prefix + f"ARCE_REJOIN_RELEASE_HELD release={transfer} transfer={transfer} logical={transfer} state=GONE\n",
                 prefix + f"ARCE_TRANSFER_RETIREMENT_ABANDONED transfer={transfer} logical={transfer} site=RECOVERED_SOURCE reason=SURVIVOR_GONE\n"]
        runner.audit_log(lines, "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log(lines + [lines[-1]], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log(lines[:-1] + [lines[-1].replace("SURVIVOR_GONE", "OTHER")], "H1", "C14", "seed")

    def test_controlled_fixture_requires_one_exact_outbox_baseline_confirmation_hold(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED\n"
        runner.audit_log([line], "H1", "C6", "check", expected_outbox_holds=1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([], "H1", "C6", "check", expected_outbox_holds=1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line, line], "H1", "C6", "check", expected_outbox_holds=1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line.replace("SOURCE_UNCONFIRMED", "SOURCE_UNAVAILABLE")], "H1", "C6", "check", expected_outbox_holds=1)

    def test_native_startup_outbox_gate_is_not_widened(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED\n"
        runner.audit_log([], "H2", "C6", "check")
        runner.audit_log([line], "H2", "C6", "check")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line, line], "H2", "C6", "check")

    def test_source_hold_has_one_exact_existing_recovery_warning(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_TRANSFER_RECOVERY transfer=00000000-0000-4000-8000-000000000001 phase=PREPARED source_count=1 destination_count=0 action=KEEP_SOURCE status=WAITING_FOR_PASSENGERS\n"
        runner.audit_log([line], "H1", "C0", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line, line], "H1", "C0", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line.replace("KEEP_SOURCE", "REBUILD_SOURCE")], "H1", "C0", "seed")

    def test_exact_gone_line_does_not_hide_another_warning_or_a_duplicate(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
        message = "ARCE_REJOIN_RELEASE_HELD release=00000000-0000-4000-8000-000000000001 transfer=00000000-0000-4000-8000-000000000002 logical=00000000-0000-4000-8000-000000000003 state=GONE\n"
        baseline = line + "ARCE_TRANSFER_RECOVERY transfer=00000000-0000-4000-8000-000000000002 phase=PREPARED source_count=1 destination_count=0 action=KEEP_SOURCE status=WAITING_FOR_PASSENGERS\n"
        retired = line + "ARCE_TRANSFER_RETIREMENT_ABANDONED transfer=00000000-0000-4000-8000-000000000002 logical=00000000-0000-4000-8000-000000000003 site=RECOVERED_SOURCE reason=SURVIVOR_GONE\n"
        runner.audit_log([baseline, retired, line + message], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, retired] + [line + message] * 2, "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, retired, line + message.replace("GONE", "CAPTURE_FAILED")], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, retired, line + message, line + "unrelated failure\n"], "H1", "C14", "seed")

    def test_campaign_omits_placement_but_requires_two_restarts_per_cut(self):
        self.assertEqual(3, len(runner.MODES))
        self.assertEqual({f"C{i}" for i in range(7)}, {c for h, c in runner.CASES if h == "H1" and int(c[1:]) < 7})
        self.assertFalse(any(c in {f"C{i}" for i in range(7, 12)} for _, c in runner.CASES))


if __name__ == "__main__":
    unittest.main()
