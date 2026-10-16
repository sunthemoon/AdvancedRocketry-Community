import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_destination as runner


class DestinationOracleTest(unittest.TestCase):
    def fixture(self):
        arm = dict(outcome="ARMED", serial=1, src="source", d="destination", l="logical", t="transfer", epoch=1)
        root = dict(armed_flights=[arm], issued_launch_serial=1, records=[], floors=[], tombstones=[], rocket_authorities=[], ledger_id="ledger")
        passengers = dict(assignments=[dict(passenger="assigned")])
        source_flight = dict(state="TRANSIT", active_transfer_id="transfer", fuel=dict(amount=1000, committed_debits=[]), passengers=passengers)
        destination_flight = dict(state="DESCENT", fuel=dict(amount=500, committed_debits=[dict(transaction_id="transfer")]), passengers=passengers)
        transfer = dict(schema_version=3, phase="DESTINATION_SPAWNED", transfer_id="transfer", source_entity_id="source",
                        destination_entity_id="destination", owner_id="owner", source_snapshot="source_snapshot",
                        destination_snapshot="destination_snapshot", source_flight=source_flight, destination_flight=destination_flight,
                        native_transfer=dict(armed=arm))
        source = dict(UUID="source", Pos=[64.5, 148.0, 64.5], RocketEntityData=dict(assembly_transaction_id="logical", owner_id="owner", snapshot="source_snapshot",
                      flight_data=source_flight, resource_escrow=dict(schema_version=1, ledger_id="ledger", resource_epoch=1, markers=[])))
        destination = dict(UUID="destination", Pos=[80.5, 148.0, 64.5], Motion=[0.0, 0.0, 0.0],
                           RocketEntityData=dict(assembly_transaction_id="logical", owner_id="owner", snapshot="destination_snapshot",
                           flight_data=destination_flight, resource_escrow=dict(schema_version=1, ledger_id="ledger", resource_epoch=0, markers=[])))
        return tuple(copy.deepcopy(value) for value in (root, transfer, source, destination))

    def test_cuts_and_two_persisted_restarts_keep_the_same_invariant(self):
        self.assertEqual(("DP_ACK", "DP_PUBLICATION", "DP_STORED"), runner.CASES)
        root, transfer, source, destination = self.fixture()
        before = runner.require_state(root, transfer, source, None, False)
        self.assertEqual(before, runner.require_state(root, transfer, source, destination, True))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_state(root, transfer, source, None, True)

    def test_refuses_source_credit_and_authority_changes(self):
        for fault in ("epoch", "fuel", "manifest", "source", "terminal", "phase", "serial", "position", "source_phase"):
            root, transfer, source, destination = self.fixture()
            data = source["RocketEntityData"]
            if fault == "epoch": data["resource_escrow"]["resource_epoch"] = 0
            elif fault == "fuel": data["flight_data"]["fuel"]["amount"] = 999
            elif fault == "manifest": data["flight_data"]["passengers"] = {}
            elif fault == "source": source["UUID"] = "another"
            elif fault == "terminal": root["armed_flights"][0]["outcome"] = "HANDED_OFF"
            elif fault == "phase": transfer["phase"] = "PREPARED"
            elif fault == "serial": root["issued_launch_serial"] = 2
            elif fault == "position": source["Pos"][0] += 1
            elif fault == "source_phase": data["flight_data"]["state"] = "LANDED"
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_state(root, transfer, source, destination, True)

    def test_refuses_duplicate_debit_foreign_wrapper_and_destination_mutation(self):
        for fault in ("debit", "debit_shape", "epoch", "fuel", "snapshot", "physical", "position", "passenger"):
            root, transfer, source, destination = self.fixture()
            data = destination["RocketEntityData"]
            if fault == "debit": data["flight_data"]["fuel"]["committed_debits"].append(dict(transaction_id="transfer"))
            elif fault == "debit_shape": data["flight_data"]["fuel"]["committed_debits"] = ["transfer"]
            elif fault == "epoch": data["resource_escrow"]["resource_epoch"] = 1
            elif fault == "fuel": data["flight_data"]["fuel"]["amount"] = 1000
            elif fault == "snapshot": data["snapshot"] = "wrong"
            elif fault == "physical": destination["UUID"] = "source"
            elif fault == "position": destination["Pos"][1] = 100
            elif fault == "passenger": destination["Passengers"] = [{}]
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_state(root, transfer, source, destination, True)


if __name__ == "__main__":
    unittest.main()
