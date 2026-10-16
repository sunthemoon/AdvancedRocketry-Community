import copy
import unittest

import test_v180_escrow_destination as fixtures
import run_v180_escrow_arrival as runner


class ArrivalOracleTest(unittest.TestCase):
    def fixture(self, landed=False):
        root, transfer, source, destination = fixtures.DestinationOracleTest().fixture()
        transfer["phase"] = "COMMITTED"
        frozen = transfer["destination_flight"]
        frozen.update(state_started_game_time=161, plan={"request_id": "transfer"}, active_transfer_id="transfer")
        flight = copy.deepcopy(frozen)
        flight["state_started_game_time"] = 500
        if landed:
            flight.update(state="LANDED", state_started_game_time=580)
            flight.pop("plan"); flight.pop("active_transfer_id")
        destination["RocketEntityData"]["flight_data"] = flight
        destination["Pos"][1] = 100.0 if landed else 124.0
        return root, transfer, source, destination

    def test_both_cuts_and_two_restarts_retain_the_exact_frozen_authority(self):
        for cut in runner.CASES:
            root, transfer, _, destination = self.fixture(cut == "FD_LANDED")
            invariant = runner.require_state(root, transfer, None, destination, cut, False)
            for _ in range(2):
                root, transfer, _, destination = self.fixture(True)
                self.assertEqual(invariant, runner.require_state(root, transfer, None, destination, cut, True))

    def test_changed_credit_assignment_wrapper_pose_and_unfinished_restart_refuse(self):
        for fault in ("fuel", "manifest", "epoch", "position", "clock", "plan", "debit", "terminal", "descent"):
            root, transfer, _, destination = self.fixture(True)
            data = destination["RocketEntityData"]
            if fault == "fuel": data["flight_data"]["fuel"]["amount"] += 1
            if fault == "manifest": data["flight_data"]["passengers"] = {}
            if fault == "epoch": data["resource_escrow"]["resource_epoch"] = 1
            if fault == "position": destination["Pos"][1] = 101.0
            if fault == "clock": data["flight_data"]["state_started_game_time"] = 1
            if fault == "plan": data["flight_data"]["plan"] = {}
            if fault == "debit": data["flight_data"]["fuel"]["committed_debits"] *= 2
            if fault == "terminal": root["armed_flights"][0]["outcome"] = "HANDED_OFF"
            if fault == "descent": data["flight_data"]["state"] = "DESCENT"
            with self.subTest(fault=fault), self.assertRaises(runner.phases.smoke.SmokeError):
                runner.require_state(root, transfer, None, destination, "FD_LANDED", True)

    def test_missing_destination_and_resurrected_source_refuse(self):
        root, transfer, source, destination = self.fixture(True)
        for from_entity, to_entity in ((source, destination), (None, None)):
            with self.assertRaises(runner.phases.smoke.SmokeError):
                runner.require_state(root, transfer, from_entity, to_entity, "FD_LANDED", True)


if __name__ == "__main__":
    unittest.main()
