import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_returns as runner


class ReturnOracleTest(unittest.TestCase):
    def fixture(self, cut, restarted):
        before = "_0_STORE_BEFORE" in cut and not restarted
        arrived = "RT_CANCEL_" in cut and "_0_STORE_BEFORE" in cut and restarted
        arm = dict(outcome="ARMED", serial=1, src="source", d="destination", l="logical", t="transfer", epoch=7)
        root = dict(armed_flights=[arm], issued_launch_serial=1, records=[], floors=[], tombstones=[], rocket_authorities=[], ledger_id="ledger")
        flight = dict(state="COUNTDOWN", state_started_game_time=10, plan={"target": "moon"},
                      fuel=dict(amount=1000, committed_debits=[dict(transaction_id="prior")]), passengers=dict(assignments=[]))
        dest_flight = copy.deepcopy(flight)
        dest_flight.update(state="DESCENT", state_started_game_time=170, active_transfer_id="transfer")
        dest_flight["fuel"] = dict(amount=500, committed_debits=[dict(transaction_id="prior"), dict(transaction_id="transfer")])
        pose = dict(motion=[0.0, 0.0, 0.0], yaw=0.0, pitch=0.0, no_gravity=1, on_ground=0, invulnerable=0)
        native = dict(armed=arm, source=dict(**pose, flight_changes=dict(state="FUELED", state_started_game_time=9), flight_absent=["plan"]),
                      destination=dict(**pose, flight_changes={}, flight_absent=[]))
        transfer = dict(schema_version=3, phase="COMMITTED" if arrived else "PREPARED", transfer_id="transfer",
                        source_entity_id="source", owner_id="owner", source_snapshot="source-snapshot", destination_snapshot="dest-snapshot",
                        source_flight=flight, destination_flight=dest_flight, native_transfer=native, created_at_game_time=10)
        if arrived: transfer["destination_entity_id"] = "destination"
        actual = copy.deepcopy(dest_flight if arrived else flight)
        actual.update(state="LANDED" if arrived else "FUELED", state_started_game_time=300)
        actual.pop("plan", None); actual.pop("active_transfer_id", None)
        y = 100.0
        if before:
            actual = copy.deepcopy(flight)
            if "RT_PAD_" in cut: actual.update(state="TRANSIT", active_transfer_id="transfer", state_started_game_time=150); y = 148.0
        entity = dict(id="advancedrocketrycommunity:rocket", UUID="destination" if arrived else "source", Pos=[(68.5 if "_SHARED_" in cut else 80.5) if arrived else 64.5, y, 64.5],
                      Motion=[0.0, 0.0, 0.0], Rotation=[0.0, 0.0], NoGravity=1,
                      RocketEntityData=dict(schema_version=3, assembly_transaction_id="logical", owner_id="owner",
                      snapshot="dest-snapshot" if arrived else "source-snapshot", flight_data=actual,
                      resource_escrow=dict(schema_version=1, ledger_id="ledger", resource_epoch=0 if arrived else 7, markers=[])))
        return root, transfer, None if arrived else entity, entity if arrived else None

    def test_all_36_boundaries_and_two_restarts_keep_frozen_authority(self):
        self.assertEqual(36, len(runner.CASES)); self.assertEqual(36, len(set(runner.CASES)))
        for cut in runner.CASES:
            with self.subTest(cut=cut):
                before = runner.require_state(*self.fixture(cut, False), cut, False)
                for _ in range(2): self.assertEqual(before, runner.require_state(*self.fixture(cut, True), cut, True))

    def test_returned_native_projection_mutations_are_refused(self):
        cut = "RT_PAD_SHARED_0_READBACK_AFTER"
        for field in ("fuel", "prior", "extra_debit", "manifest", "epoch", "logical", "owner", "snapshot", "physical", "pose", "motion",
                      "rotation", "gravity", "ground", "invulnerable", "passenger", "clock", "plan", "arm", "journal", "schema"):
            root, transfer, source, destination = self.fixture(cut, True)
            data = source["RocketEntityData"]; flight = data["flight_data"]
            if field == "fuel": flight["fuel"]["amount"] += 1
            if field == "prior": flight["fuel"]["committed_debits"] = []
            if field == "extra_debit": flight["fuel"]["committed_debits"] *= 2
            if field == "manifest": flight["passengers"]["assignments"] = ["foreign"]
            if field == "epoch": data["resource_escrow"]["resource_epoch"] = 0
            if field == "logical": data["assembly_transaction_id"] = "foreign"
            if field == "owner": data["owner_id"] = "foreign"
            if field == "snapshot": data["snapshot"] = "foreign"
            if field == "physical": source["UUID"] = "foreign"
            if field == "pose": source["Pos"][1] = 101.0
            if field == "motion": source["Motion"][0] = 1.0
            if field == "rotation": source["Rotation"][0] = 1.0
            if field == "gravity": source["NoGravity"] = 0
            if field == "ground": source["OnGround"] = 1
            if field == "invulnerable": source["Invulnerable"] = 1
            if field == "passenger": source["Passengers"] = [{}]
            if field == "clock": flight["state_started_game_time"] = 9
            if field == "plan": flight["plan"] = {"target": "moon"}
            if field == "arm": root["armed_flights"][0]["outcome"] = "CANCELLED"
            if field == "journal": transfer["phase"] = "COMMITTED"
            if field == "schema": data["schema_version"] = 2
            with self.subTest(field=field), self.assertRaises(runner.phases.smoke.SmokeError):
                runner.require_state(root, transfer, source, destination, cut, True)

    def test_absent_duplicate_or_unauthorized_forward_survivor_refuses(self):
        cut = "RT_CANCEL_SEPARATE_0_STORE_BEFORE"
        root, transfer, source, _ = self.fixture(cut, False)
        with self.assertRaises(runner.phases.smoke.SmokeError): runner.require_state(root, transfer, None, None, cut, False)
        with self.assertRaises(runner.phases.smoke.SmokeError): runner.require_state(root, transfer, source, source, cut, False)
        root, transfer, _, destination = self.fixture(cut, True)
        for forbidden in ("RT_CANCEL_SEPARATE_0_STORE_AFTER", "RT_PAD_SHARED_0_STORE_BEFORE"):
            with self.assertRaises(runner.phases.smoke.SmokeError): runner.require_state(root, transfer, None, destination, forbidden, True)

    def test_pre_store_progression_cannot_be_accepted_as_finished_return(self):
        cut = "RT_PAD_SEPARATE_0_STORE_BEFORE"
        with self.assertRaises(runner.phases.smoke.SmokeError): runner.require_state(*self.fixture(cut, False), cut, True)


if __name__ == "__main__": unittest.main()
