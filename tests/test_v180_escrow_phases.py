import unittest

import test_v180_escrow_destination as fixtures
import run_v180_escrow_phases as runner


class PhaseOracleTest(unittest.TestCase):
    def test_every_cut_and_two_restarts_keep_frozen_authority(self):
        for cut in runner.CASES:
            root, transfer, source, destination = fixtures.DestinationOracleTest().fixture()
            transfer["phase"] = runner.PHASES[cut]
            before = runner.require_state(root, transfer, None if cut == "FP_ABSENT_SOURCE" else source, None, cut, False)
            transfer["phase"] = "COMMITTED"
            for _ in range(2):
                self.assertEqual(before, runner.require_state(root, transfer, None, destination, cut, True))

    def test_missing_source_before_removal_and_missing_destination_after_restart_are_refused(self):
        root, transfer, source, destination = fixtures.DestinationOracleTest().fixture()
        transfer["phase"] = "PASSENGERS_TRANSFERRED"
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(root, transfer, None, None, "FP_PASSENGERS_ACK", False)
        transfer["phase"] = "COMMITTED"
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(root, transfer, None, None, "FP_COMMITTED", True)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(root, transfer, source, destination, "FP_COMMITTED", True)

    def test_mutation_repeated_debit_and_premature_release_are_refused(self):
        for fault in ("terminal", "epoch", "fuel", "manifest", "identity", "debit"):
            root, transfer, _, destination = fixtures.DestinationOracleTest().fixture(); transfer["phase"] = "COMMITTED"
            data = destination["RocketEntityData"]
            if fault == "terminal": root["armed_flights"][0]["outcome"] = "HANDED_OFF"
            if fault == "epoch": data["resource_escrow"]["resource_epoch"] = 1
            if fault == "fuel": data["flight_data"]["fuel"]["amount"] = 1000
            if fault == "manifest": data["flight_data"]["passengers"] = {}
            if fault == "identity": destination["UUID"] = "source"
            if fault == "debit": data["flight_data"]["fuel"]["committed_debits"].append(dict(transaction_id="transfer"))
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_state(root, transfer, None, destination, "FP_COMMITTED", True)


if __name__ == "__main__":
    unittest.main()
