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


if __name__ == "__main__":
    unittest.main()
