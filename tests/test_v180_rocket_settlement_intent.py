import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_rocket_settlement_intent as native


class SettlementIntentNativeOracleTest(unittest.TestCase):
    def test_retained_assertion_is_required_before_progress_on_first_restart(self):
        for cut in native.CASES:
            for restart in (1, 2):
                count = 2 if cut.startswith("SO_CHAIN_") else 1
                line = f"ARCE_RELEASE_SETTLEMENT_RETAINED cut={cut} records={count} intent={'frozen' if cut.endswith('_AFTER') else 'absent'} fuel=0 locked=true"
                native.require_retained([line], cut, restart)
                if restart == 1 or cut.endswith("_BEFORE"):
                    for invalid in ([], [line, line], [line.replace("fuel=0", "fuel=1000")]):
                        with self.assertRaises(native.smoke.SmokeError): native.require_retained(invalid, cut, restart)
                else: native.require_retained([], cut, restart)

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

    def test_eventual_release_only_adds_the_prescribed_binding_and_exact_receipts(self):
        import run_v180_rocket_settlement_completion as completion
        op = self.root["records"][0]
        op["destination"]["kind"] = "ENTITY"
        op["guard"] = dict(rocket_id=[20, 21, 22, 23])
        op["source"] = self.intent["observations"][0]["endpoint"]
        self.intent["observations"][1]["endpoint"] = dict(op["destination"])
        baseline = copy.deepcopy(self.inspect())
        receipt = dict(schema_version=3, op_id=op["op_id"], actor=native.ACTOR, actor_name="Server", choice="ACCEPT_CURRENT",
                       confirmed_tick=11, decision_digest="a" * 64, guards=[op["guard"]],
                       before_choice=copy.deepcopy(self.intent["observations"]), released=1, release_generation=10, accounting_status="UNKNOWN")
        self.root["records"] = []; self.root["tombstones"] = [receipt]
        self.wrapper["resource_escrow"]["ledger_id"] = self.root["ledger_id"]
        def check(cut="SO_ACCEPT_AFTER"):
            with patch.object(native, "ledger", return_value=self.root), patch.object(native, "digest", return_value="a" * 64), \
                    patch.object(completion, "journal", return_value=dict(transfers=[])), \
                    patch.object(native, "record", side_effect=[dict(Entities=[self.rocket]), dict(block_entities=self.sources)]):
                return native.inspect_released(Path("world"), cut, baseline)
        self.assertEqual(0, check()["fuel"])
        with self.assertRaises(native.smoke.SmokeError): check("SO_ACCEPT_BEFORE")
        self.wrapper["flight_data"]["fuel"]["amount"] = 1
        with self.assertRaises(native.smoke.SmokeError): check()
        self.wrapper["flight_data"]["fuel"]["amount"] = 0
        receipt["decision_digest"] = "b" * 64
        with self.assertRaises(native.smoke.SmokeError): check()


if __name__ == "__main__":
    unittest.main()
