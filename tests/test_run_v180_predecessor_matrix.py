"""Independent negative tests for the I7 native census/resource oracle."""
import copy
import sys
import unittest
from unittest.mock import patch
from pathlib import Path
from uuid import UUID

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_predecessor_matrix as probe


class MatrixOracleTest(unittest.TestCase):
    def setUp(self):
        self.fuel = dict(amount=500, committed_debits=[[0, 0, 0, 2]], propulsion=dict(revision=1, primary=dict(amount=500)))
        source_fuel = dict(amount=1000, committed_debits=[], propulsion=dict(revision=0, primary=dict(amount=1000)))
        snapshot = dict(source_origin=[8, 100, 8], source_dimension="test:moon",
                        blocks=[dict(id="chest", items=[dict(id="diamond", count=17)])])
        self.old = dict(phase="COMMITTED", transfer_id=[0, 0, 0, 2], logical_rocket_id=[0, 0, 0, 1],
                        source_entity_id=[0, 0, 0, 3], destination_entity_id=[0, 0, 0, 4], owner_id=[0, 0, 0, 5],
                        source_snapshot=copy.deepcopy(snapshot), destination_snapshot=copy.deepcopy(snapshot),
                        source_flight=dict(fuel=source_fuel), destination_flight=dict(fuel=copy.deepcopy(self.fuel), passengers=dict(seat_capacity=1)))
        self.entity = dict(UUID=[0, 0, 0, 4], Pos=[8.5, 100, 8.5], RocketEntityData=dict(assembly_transaction_id=[0, 0, 0, 1],
                owner_id=[0, 0, 0, 5], snapshot=snapshot,
                flight_data=dict(state="LANDED", current_origin=[8, 100, 8], current_dimension="test:moon",
                                 logical_rocket_id=[0, 0, 0, 1], fuel=copy.deepcopy(self.fuel), passengers=dict(assignments=[], seat_capacity=1))))
        self.disk = dict(journal=dict(transfers=[copy.deepcopy(self.old)]), entities=[dict(entity=self.entity)])

    def check(self, restart=1, boundary="TERMINAL", survivor=4):
        return probe.inspect(self.disk, self.old, self.fuel, restart, boundary, str(UUID(int=survivor)))

    def test_current_destination_retained(self): self.check()

    def test_location_identity_and_seat_capacity_are_checked(self):
        for key, value in (("current_origin", [9, 100, 8]), ("current_dimension", "test:earth"),
                           ("logical_rocket_id", [0, 0, 0, 7])):
            flight = self.entity["RocketEntityData"]["flight_data"]
            original = flight[key]; flight[key] = value
            with self.assertRaises(probe.smoke.SmokeError): self.check()
            flight[key] = original
        self.entity["Pos"][0] += 1
        with self.assertRaises(probe.smoke.SmokeError): self.check()
        self.entity["Pos"][0] -= 1
        self.entity["RocketEntityData"]["flight_data"]["passengers"]["seat_capacity"] = 2
        with self.assertRaises(probe.smoke.SmokeError): self.check()

    def test_duplicate_and_missing_authority_fail(self):
        self.disk["entities"].append(copy.deepcopy(self.disk["entities"][0]))
        with self.assertRaises(probe.smoke.SmokeError): self.check()
        self.disk["entities"].clear()
        with self.assertRaises(probe.smoke.SmokeError): self.check()

    def test_every_resource_bank_and_debit_is_checked(self):
        for mutation in (lambda d: d["flight_data"]["fuel"].update(committed_debits=[]),
                         lambda d: d["flight_data"]["fuel"]["propulsion"].update(revision=2),
                         lambda d: d["snapshot"]["blocks"][0]["items"][0].update(count=18)):
            saved = copy.deepcopy(self.entity["RocketEntityData"])
            mutation(self.entity["RocketEntityData"])
            with self.assertRaises(probe.smoke.SmokeError): self.check()
            self.entity["RocketEntityData"] = saved

    def test_source_can_exist_before_its_store_but_not_after_exclusion(self):
        source = copy.deepcopy(self.entity); source["UUID"] = self.old["source_entity_id"]
        source["RocketEntityData"]["flight_data"]["fuel"] = copy.deepcopy(self.old["source_flight"]["fuel"])
        self.disk["entities"].append(dict(entity=source))
        self.check(0, "STORE_S")
        with self.assertRaises(probe.smoke.SmokeError): self.check(0, "TERMINAL")

    def test_successor_and_both_restart_absences_are_checked(self):
        successor = copy.deepcopy(self.old)
        successor.update(phase="PREPARED", transfer_id=[0, 0, 0, 6], source_entity_id=self.old["destination_entity_id"],
                         source_snapshot=self.old["destination_snapshot"], source_flight=dict(fuel=copy.deepcopy(self.fuel)))
        self.disk["journal"]["transfers"] = [successor]
        self.check(0, "MOVED")
        with self.assertRaises(probe.smoke.SmokeError): self.check(1, "MOVED")
        self.disk["journal"]["transfers"].clear()
        self.check(1, "MOVED"); self.check(2, "MOVED")

    def test_unknown_is_not_old_generation_or_loss_permission(self):
        with self.assertRaises(probe.smoke.SmokeError): self.check(0, "UNKNOWN")
        self.disk["journal"]["transfers"].clear()
        with self.assertRaises(probe.smoke.SmokeError): self.check(0, "UNKNOWN")

    def test_pre_store_source_fallback_is_unchanged(self):
        self.entity["UUID"] = self.old["source_entity_id"]
        self.entity["RocketEntityData"]["flight_data"].update(state="FUELED", fuel=self.old["source_flight"]["fuel"])
        self.disk["journal"]["transfers"].clear()
        self.check(1, "STORE_D", 3); self.check(2, "STORE_D", 3)
        with self.assertRaises(probe.smoke.SmokeError): self.check(1, "TERMINAL", 3)

    def test_current_manifest_and_active_transfer_cannot_be_erased_by_the_oracle(self):
        self.entity["RocketEntityData"]["flight_data"]["passengers"]["assignments"] = [dict(passenger=[0, 0, 0, 7])]
        with self.assertRaises(probe.smoke.SmokeError): self.check()
        self.entity["RocketEntityData"]["flight_data"]["passengers"]["assignments"] = []
        self.entity["RocketEntityData"]["flight_data"]["active_transfer_id"] = [0, 0, 0, 2]
        with self.assertRaises(probe.smoke.SmokeError): self.check()


class RestartObservationTest(unittest.TestCase):
    def test_waits_for_actual_landing_without_mutating_the_entity(self):
        reports = [dict(state="DESCENT", transfer="pending"), dict(state="LANDED", transfer="none")]
        with patch.object(probe.FlightHarness, "report", side_effect=reports), patch.object(probe.time, "sleep"):
            self.assertEqual(reports[-1], probe.observe_stationary(object(), "test:moon", "entity"))

    def test_unexpected_progression_and_expired_observation_fail(self):
        with patch.object(probe.FlightHarness, "report", return_value=dict(state="COUNTDOWN", transfer="new")):
            with self.assertRaises(probe.smoke.SmokeError): probe.observe_stationary(object(), "test:moon", "entity")
        with patch.object(probe.time, "monotonic", side_effect=[0, 121]):
            with self.assertRaises(probe.smoke.SmokeError): probe.observe_stationary(object(), "test:moon", "entity")


if __name__ == "__main__": unittest.main()
