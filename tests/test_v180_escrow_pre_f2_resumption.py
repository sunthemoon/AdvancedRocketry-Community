import copy
import unittest
from pathlib import Path
from unittest.mock import patch

import test_v180_escrow_arrival as fixtures
import run_v180_escrow_pre_f2 as runner


class PreF2ResumptionTest(unittest.TestCase):
    def fixture(self):
        root, transfer, source, destination = fixtures.ArrivalOracleTest().fixture(True)
        transfer["logical_rocket_id"] = "logical"
        transfer["native_transfer"].update(schema_version=1, signature_kind="FUEL_VECTOR_V1")
        for flight in (transfer["source_flight"], transfer["destination_flight"],
                       source["RocketEntityData"]["flight_data"], destination["RocketEntityData"]["flight_data"]):
            flight["schema_version"] = 2
        source["Pos"] = [64.5, 100., 64.5]
        source["RocketEntityData"]["flight_data"].update(state="FUELED")
        source["RocketEntityData"]["flight_data"].pop("active_transfer_id")
        for entity in (source, destination):
            entity.update(id="advancedrocketrycommunity:rocket", Motion=[0., 0., 0.], Rotation=[0., 0.],
                          NoGravity=1, OnGround=0, Invulnerable=0)
        original = copy.deepcopy(transfer)
        original.update(phase="PREPARED", checksum="before")
        original.pop("destination_entity_id")
        transfer["checksum"] = "after"
        before = dict(ledger=copy.deepcopy(root), journal=[original], source=runner.source_projection(source))
        return root, [transfer], [], [destination], before

    def test_three_governed_cuts_and_both_restarts_accept_exact_stable_survivor(self):
        for cut in runner.CASES:
            if not runner.governed_transfer(cut):
                continue
            args = self.fixture()
            runner.require_governed_record(args[4]["journal"][0], args[0]["armed_flights"][0])
            expected = runner.require_resumed(*args)
            self.assertEqual(expected, runner.require_resumed(*copy.deepcopy(args)))
            root, transfers, _, destinations, before = args
            with patch.object(runner, "ledger", return_value=root), \
                    patch.object(runner, "journal", return_value={"transfers": transfers}), \
                    patch.object(runner, "slot_present", return_value=True), \
                    patch.object(runner, "record", side_effect=lambda path, slot=132: {"Entities": destinations if slot == 133 else []}), \
                    patch.object(runner, "digest", return_value="file-hash"):
                self.assertEqual(expected, runner.inspect(Path("world"), cut, True, resumed_from=before)["survivor"])
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.inspect(Path("world"), cut, True)

    def test_refuses_cancelled_arm_new_serial_second_arm_and_other_authority(self):
        for fault in ("cancelled", "serial", "arm_serial", "second_arm", "records", "floors", "tombstones", "rocket_authorities", "ledger"):
            args = self.fixture(); root = args[0]
            if fault == "cancelled": root["armed_flights"][0]["outcome"] = "CANCELLED"
            elif fault == "serial": root["issued_launch_serial"] = 2
            elif fault == "arm_serial": root["armed_flights"][0]["serial"] = 2
            elif fault == "second_arm": root["armed_flights"] *= 2
            elif fault == "ledger": root["ledger_id"] = "other"
            else: root[fault].append({"unexpected": True})
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_resumed(*args)

    def test_refuses_missing_different_or_mutated_t(self):
        for fault in ("missing", "duplicate", "different", "source_fuel", "source_manifest", "source_snapshot", "phase", "destination"):
            args = self.fixture(); transfers = args[1]; transfer = transfers[0]
            if fault == "missing": transfers.clear()
            elif fault == "duplicate": transfers *= 2
            elif fault == "different": transfer["transfer_id"] = "other"
            elif fault == "source_fuel": transfer["source_flight"]["fuel"]["amount"] -= 1
            elif fault == "source_manifest": transfer["source_flight"]["passengers"] = {}
            elif fault == "source_snapshot": transfer["source_snapshot"] = "other"
            elif fault == "phase": transfer["phase"] = "PREPARED"
            elif fault == "destination": transfer["destination_entity_id"] = "other"
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_resumed(*args)

    def test_refuses_changed_checkpoint_source_invariants(self):
        for fault in ("physical", "logical", "owner", "snapshot", "fuel", "manifest"):
            args = self.fixture(); source = args[4]["source"]; data = source["RocketEntityData"]
            if fault == "physical": source["UUID"] = "other"
            elif fault == "logical": data["assembly_transaction_id"] = "other"
            elif fault == "owner": data["owner_id"] = "other"
            elif fault == "snapshot": data["snapshot"] = "other"
            elif fault == "fuel": data["flight_data"]["fuel"]["amount"] -= 1
            elif fault == "manifest": data["flight_data"]["passengers"] = {}
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_resumed(*args)

    def test_refuses_lost_resources_or_changed_landed_survivor(self):
        for fault in ("fuel", "debits", "manifest", "wrapper", "pose", "motion", "clock", "plan", "state", "rotation", "owner", "extension"):
            args = self.fixture(); entity = args[3][0]; data = entity["RocketEntityData"]; flight = data["flight_data"]
            if fault == "fuel": flight["fuel"]["amount"] = 499
            elif fault == "debits": flight["fuel"]["committed_debits"] = []
            elif fault == "manifest": flight["passengers"] = {}
            elif fault == "wrapper": data["resource_escrow"]["resource_epoch"] = 1
            elif fault == "pose": entity["Pos"][1] += 1
            elif fault == "motion": entity["Motion"][0] += 1
            elif fault == "clock": flight["state_started_game_time"] = 1
            elif fault == "plan": flight["plan"] = {}
            elif fault == "state": flight["state"] = "DESCENT"
            elif fault == "rotation": entity["Rotation"][0] += 1
            elif fault == "owner": data["owner_id"] = "other"
            elif fault == "extension": data["unexpected"] = True
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_resumed(*args)

    def test_refuses_duplicate_logical_rocket_or_missing_survivor(self):
        for fault in ("source", "second_source", "duplicate_destination", "missing_destination"):
            args = self.fixture()
            if fault == "source": args[2].append(copy.deepcopy(args[4]["source"]))
            elif fault == "second_source": args[2].extend([copy.deepcopy(args[4]["source"])] * 2)
            elif fault == "duplicate_destination": args[3].append(copy.deepcopy(args[3][0]))
            else: args[3].clear()
            with self.subTest(fault=fault), self.assertRaises(runner.smoke.SmokeError):
                runner.require_resumed(*args)


if __name__ == "__main__":
    unittest.main()
