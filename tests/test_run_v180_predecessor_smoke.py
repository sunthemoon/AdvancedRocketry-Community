"""The selected native-cut oracle rejects losses independently of its server driver."""
import copy
import sys
import unittest
from pathlib import Path
from uuid import UUID

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_predecessor_smoke as probe


class PredecessorOracleTest(unittest.TestCase):
    def setUp(self):
        self.flight = dict(logical_rocket_id=str(UUID(int=1)), transfer_id=str(UUID(int=2)),
                           source_entity=str(UUID(int=3)), destination_entity=str(UUID(int=4)))
        fuel = dict(amount=500, committed_debits=[[0, 0, 0, 2]])
        state = dict(state="LANDED", fuel=fuel, passengers=dict(assignments=[]))
        snapshot = dict(blocks=[dict(id="chest", items=[dict(id="diamond", count=17)])])
        transfer = dict(logical_rocket_id=[0, 0, 0, 1], transfer_id=[0, 0, 0, 2], phase="COMMITTED",
                        destination_flight=dict(fuel=copy.deepcopy(fuel)), destination_snapshot=copy.deepcopy(snapshot), owner_id=[0, 0, 0, 5])
        entity = dict(UUID=[0, 0, 0, 4], RocketEntityData=dict(assembly_transaction_id=[0, 0, 0, 1],
                      snapshot=snapshot, flight_data=state, owner_id=[0, 0, 0, 5]))
        self.disk = dict(journal=dict(transfers=[transfer]), entities=[dict(entity=entity)])

    def check(self):
        probe.verify(self.disk, self.flight, 500, 1, "TERMINAL")

    def test_unchanged_authority(self):
        self.check()

    def test_missing_or_duplicate_destination(self):
        self.disk["entities"].append(copy.deepcopy(self.disk["entities"][0]))
        with self.assertRaises(probe.smoke.SmokeError): self.check()
        self.disk["entities"].clear()
        with self.assertRaises(probe.smoke.SmokeError): self.check()

    def test_changed_resources_and_debits(self):
        data = self.disk["entities"][0]["entity"]["RocketEntityData"]
        for field, value in (("amount", 1000), ("committed_debits", [])):
            old = data["flight_data"]["fuel"][field]
            data["flight_data"]["fuel"][field] = value
            with self.assertRaises(probe.smoke.SmokeError): self.check()
            data["flight_data"]["fuel"][field] = old
        data["snapshot"]["blocks"][0]["items"][0]["count"] = 18
        with self.assertRaises(probe.smoke.SmokeError): self.check()

    def test_lost_predecessor(self):
        self.disk["journal"]["transfers"].clear()
        with self.assertRaises(probe.smoke.SmokeError): self.check()

    def test_descent_preimage_is_only_admissible_before_own_store(self):
        state = self.disk["entities"][0]["entity"]["RocketEntityData"]["flight_data"]
        state.update(state="DESCENT", active_transfer_id=[0, 0, 0, 2])
        self.disk["journal"]["transfers"][0]["destination_flight"] = copy.deepcopy(state)
        probe.verify(self.disk, self.flight, 500, 0, "STORE")
        with self.assertRaises(probe.smoke.SmokeError): self.check()
        with self.assertRaises(probe.smoke.SmokeError): probe.verify(self.disk, self.flight, 500, 0, "TERMINAL")

    def test_refuel_updates_the_whole_bank_exactly_once(self):
        data = self.disk["entities"][0]["entity"]["RocketEntityData"]
        old = self.disk["journal"]["transfers"][0]["destination_flight"]["fuel"]
        old["propulsion"] = dict(primary=dict(amount=500, branch="ABSTRACT"), revision=2)
        data["flight_data"]["fuel"] = copy.deepcopy(old)
        data["flight_data"]["fuel"].update(amount=1000)
        data["flight_data"]["fuel"]["propulsion"].update(primary=dict(amount=1000, branch="ABSTRACT"), revision=3)
        data["flight_data"]["state"] = "FUELED"
        probe.verify(self.disk, self.flight, 1000, 1, "MOVE")
        data["flight_data"]["fuel"]["propulsion"]["revision"] = 4
        with self.assertRaises(probe.smoke.SmokeError): probe.verify(self.disk, self.flight, 1000, 1, "MOVE")

    def test_pre_store_source_recovery_preserves_the_original_source_and_retires_t(self):
        entity = self.disk["entities"][0]["entity"]; data = entity["RocketEntityData"]
        entity["UUID"] = [0, 0, 0, 3]; data["flight_data"]["state"] = "FUELED"
        self.flight.update(source_recovery=True, predecessor=dict(owner_id=data["owner_id"],
            source_snapshot=copy.deepcopy(data["snapshot"]), source_flight=copy.deepcopy(data["flight_data"])))
        self.disk["journal"]["transfers"].clear()
        probe.verify(self.disk, self.flight, 500, 1, "STORE")
        data["flight_data"]["fuel"]["amount"] -= 1
        with self.assertRaises(probe.smoke.SmokeError): probe.verify(self.disk, self.flight, 500, 1, "STORE")


if __name__ == "__main__":
    unittest.main()
