import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_rocket_settlement_completion as native


class SettlementCompletionOracleTest(unittest.TestCase):
    def setUp(self):
        endpoint = dict(kind="ENTITY", identity=[1, 2, 3, 4])
        self.root = dict(ledger_id=[10, 11, 12, 13], records=[], floors=[], tombstones=[
            dict(schema_version=2, released=1, actor=native.ACTOR, actor_name="Server", decision_digest="a" * 64,
                 survivors=[dict(endpoint=endpoint)])])
        self.wrapper = dict(schema_version=3, flight_data=dict(fuel=dict(amount=1000)),
                            resource_escrow=dict(resource_epoch=1, ledger_id=self.root["ledger_id"], markers=[]))
        self.rocket = dict(UUID=endpoint["identity"], Pos=[64.5, 100., 64.5], RocketEntityData=self.wrapper)
        self.sources = [dict(ForgeData=dict(arce_escrow_fixture=dict(amount=4000)))]
        self.transfers = []

    def inspect(self, cut="SR_ACCEPT__O4_AFTER", restarted=True):
        with patch.object(native, "ledger", return_value=self.root), patch.object(native, "digest", return_value="a" * 64), \
                patch.object(native, "journal", return_value=dict(transfers=self.transfers)), \
                patch.object(native, "slot_present", return_value=True), \
                patch.object(native, "record", side_effect=[dict(Entities=[self.rocket]), dict(block_entities=self.sources)]):
            return native.inspect(Path("world"), cut, restarted=restarted)

    def test_released_resources_and_epoch_are_intact(self):
        self.assertEqual(5000, self.inspect()["raw_total"])

    def test_unacknowledged_or_lost_receipt_is_rejected(self):
        self.root["tombstones"][0]["released"] = 0
        with self.assertRaises(native.smoke.SmokeError): self.inspect()
        self.root["tombstones"] = []
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_actor_survivor_identity_and_epoch_are_checked(self):
        original = copy.deepcopy(self.root)
        for field, value in (("actor", [0, 0, 0, 0]), ("actor_name", "someone"), ("decision_digest", "a" * 65)):
            self.root = copy.deepcopy(original); self.root["tombstones"][0][field] = value
            with self.assertRaises(native.smoke.SmokeError): self.inspect()
        self.root = original; self.rocket["UUID"] = [0, 0, 0, 0]
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_wrong_resource_amount_retained_markers_or_removed_epoch_are_rejected(self):
        original = copy.deepcopy(self.wrapper)
        for field, value in (("resource_epoch", 0), ("markers", [dict(op_id=[1, 1, 1, 1])])):
            self.wrapper = copy.deepcopy(original); self.rocket["RocketEntityData"] = self.wrapper
            self.wrapper["resource_escrow"][field] = value
            with self.assertRaises(native.smoke.SmokeError): self.inspect()
        self.wrapper = original; self.rocket["RocketEntityData"] = self.wrapper; self.wrapper["flight_data"]["fuel"]["amount"] = 2000
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_matching_journal_cannot_remain_after_release(self):
        self.transfers = [dict(transfer_id=[9, 9, 9, 9])]
        with self.assertRaises(native.smoke.SmokeError): self.inspect()

    def test_matrix_covers_every_acknowledgement_and_every_choice_veto(self):
        for choice in native.CHOICES:
            for phase in ("O3", "O4"):
                for side in ("BEFORE", "AFTER"):
                    self.assertIn(f"SR_{choice}__{phase}_{side}", native.CASES)
            for index in range(3 if choice.startswith("CHAIN") else 2):
                for side in ("BEFORE", "AFTER"):
                    self.assertIn(f"SR_{choice}__O2_{side}_{index}", native.CASES)
            self.assertIn(f"SR_{choice}__VETO", native.CASES)
        self.assertIn("SR_FLOOR__O3J_BEFORE", native.CASES)
        self.assertIn("SR_FLOOR__O3J_AFTER", native.CASES)
        self.assertEqual(len(native.CASES), len(set(native.CASES)))


if __name__ == "__main__":
    unittest.main()
