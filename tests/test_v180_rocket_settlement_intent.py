import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_rocket_settlement_intent as native


class SettlementIntentNativeOracleTest(unittest.TestCase):
    def setUp(self):
        self.cut = "SO_ACCEPT_AFTER"
        self.root = dict(ledger_id=[10, 11, 12, 13], floors=[], tombstones=[], records=[
            dict(op_id=[1, 2, 3, 4], kind="FLUID_PROPULSION_FILL", phase="SETTLING_ACCEPT", reason="OTHER_STATE",
                 destination=dict(identity=[5, 6, 7, 8], logical_rocket=[20, 21, 22, 23]))])
        self.intent = dict(choice="ACCEPT_CURRENT", ledger_id=self.root["ledger_id"], actor=native.ACTOR,
                           actor_name="Server", digest="a" * 64, preview_tick=10, confirmed_tick=11,
                           members=[dict(op_id=[1, 2, 3, 4])], observations=[
                               dict(endpoint=dict(kind="BLOCK_ENTITY"), missing=0, quantity=5000),
                               dict(endpoint=dict(kind="ENTITY"), missing=0, quantity=0)])
        self.root["records"][0]["settlement"] = self.intent
        self.wrapper = dict(schema_version=3, assembly_transaction_id=[20, 21, 22, 23],
                            flight_data=dict(fuel=dict(amount=0)),
                            resource_escrow=dict(schema_version=1, resource_epoch=0, markers=[]))
        self.rocket = dict(UUID=[5, 6, 7, 8], Pos=[64.5, 100., 64.5], RocketEntityData=self.wrapper)
        self.sources = [dict(ForgeData=dict(arce_escrow_fixture=dict(amount=5000)))]

    def inspect(self):
        with patch.object(native, "ledger", return_value=self.root), patch.object(native, "digest", return_value="a" * 64), \
                patch.object(native, "record", side_effect=[dict(Entities=[self.rocket]), dict(block_entities=self.sources)]):
            return native.inspect(Path("world"), self.cut)

    def test_frozen_intent_is_not_retirement_or_release(self):
        self.assertEqual(1, self.inspect()["source_count"])
        self.assertEqual(self.root, self.inspect()["ledger"])

    def test_before_replacement_has_no_decision(self):
        self.cut = "SO_ACCEPT_BEFORE"
        self.root["records"][0].update(phase="REPAIR")
        with self.assertRaises(native.smoke.SmokeError): self.inspect()
        del self.root["records"][0]["settlement"]
        self.inspect()

    def test_actor_digest_choice_and_member_are_checked(self):
        original = copy.deepcopy(self.intent)
        for key, value in (("actor", [0, 0, 0, 0]), ("digest", "b" * 65), ("choice", "REFUND"),
                           ("members", [dict(op_id=[9, 9, 9, 9])])):
            self.root["records"][0]["settlement"] = copy.deepcopy(original)
            self.root["records"][0]["settlement"][key] = value
            with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_loss_epoch_change_duplicate_or_wrong_position_are_rejected(self):
        original = copy.deepcopy(self.rocket)
        for changed in ("fuel", "epoch", "position", "identity"):
            self.rocket = copy.deepcopy(original)
            if changed == "fuel": self.rocket["RocketEntityData"]["flight_data"]["fuel"]["amount"] = 1000
            elif changed == "epoch": self.rocket["RocketEntityData"]["resource_escrow"]["resource_epoch"] = 1
            elif changed == "position": self.rocket["Pos"][0] = 66.5
            else: self.rocket["UUID"] = [0, 0, 0, 0]
            with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_record_loss_or_unproved_tombstones_are_rejected(self):
        self.root["tombstones"] = [dict(op_id=[1, 2, 3, 4])]
        with self.assertRaises(native.smoke.SmokeError): self.inspect()
        self.root["tombstones"] = []; self.root["records"] = []
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_abandon_requires_exact_missing_observations_and200_ticks(self):
        self.cut = "SO_ABANDON_AFTER"; self.sources = []
        self.root["records"][0]["phase"] = "SETTLING_ABANDON"
        self.intent.update(choice="ABANDON", confirmed_tick=210)
        self.intent["observations"][0].update(missing=1, quantity=0)
        self.inspect()
        self.intent["confirmed_tick"] = 209
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_connected_chain_needs_both_records_and_all_observations(self):
        self.cut = "SO_CHAIN_AFTER"
        other = copy.deepcopy(self.root["records"][0]); other["op_id"] = [2, 2, 3, 4]
        self.root["records"].append(other)
        self.intent["members"].append(dict(op_id=other["op_id"]))
        self.intent["observations"].append(dict(endpoint=dict(kind="BLOCK_ENTITY"), missing=0, quantity=5000))
        other["settlement"] = copy.deepcopy(self.intent); self.sources.append(copy.deepcopy(self.sources[0])); self.inspect()
        other["settlement"]["digest"] = "b" * 64
        with self.assertRaises(native.smoke.SmokeError): self.inspect()


if __name__ == "__main__":
    unittest.main()
