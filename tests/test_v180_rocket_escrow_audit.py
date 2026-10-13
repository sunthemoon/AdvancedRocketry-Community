import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_rocket_escrow_audit as audit


class RocketAuditNativeOracleTest(unittest.TestCase):
    def setUp(self):
        self.wrapper = dict(schema_version=3, assembly_transaction_id=[1, 2, 3, 4],
                            flight_data=dict(fuel=dict(amount=0)),
                            resource_escrow=dict(schema_version=1, resource_epoch=0, markers=[]))
        self.entity = dict(UUID=[5, 6, 7, 8], Pos=[66.5, 100., 64.5], RocketEntityData=self.wrapper)
        self.root = dict(records=[dict(phase="REPAIR", reason="RELOCATED_ENDPOINT",
                                      destination=dict(identity=[5, 6, 7, 8], logical_rocket=[1, 2, 3, 4]))])
        self.source = dict(x=64, y=100, z=64, ForgeData=dict(arce_escrow_fixture=dict(amount=5000)))

    def inspect(self, repaired=True):
        with patch.object(audit, "ledger", return_value=self.root), patch.object(audit, "digest", return_value="a" * 64), \
                patch.object(audit, "record", side_effect=[dict(Entities=[self.entity]), dict(block_entities=[self.source])]):
            return audit.inspect(Path("world"), repaired)

    def test_real_rocket_and_exact_retained_pending_resources(self):
        self.assertEqual(5000, self.inspect()["source"])
        self.assertEqual(self.wrapper, self.inspect()["wrapper"])

    def test_pre_replace_cut_keeps_prepared_record(self):
        self.root["records"][0].update(phase="PREPARED", reason="NONE")
        self.inspect(False)
        with self.assertRaises(audit.smoke.SmokeError):
            self.inspect(True)

    def test_lost_operation_never_counts_as_repair(self):
        self.root["records"].clear()
        with self.assertRaises(audit.smoke.SmokeError):
            self.inspect()

    def test_changed_resource_or_epoch_is_rejected(self):
        original = copy.deepcopy(self.entity)
        for changed in ("fuel", "epoch", "pos", "identity"):
            self.entity = copy.deepcopy(original)
            if changed == "fuel":
                self.entity["RocketEntityData"]["flight_data"]["fuel"]["amount"] = 1000
            elif changed == "epoch":
                self.entity["RocketEntityData"]["resource_escrow"]["resource_epoch"] = 1
            elif changed == "pos":
                self.entity["Pos"][0] = 64.5
            else:
                self.entity["UUID"] = [0, 0, 0, 0]
            with self.assertRaises(audit.smoke.SmokeError):
                self.inspect()

    def test_changed_source_is_rejected(self):
        self.source["ForgeData"]["arce_escrow_fixture"]["amount"] = 4000
        with self.assertRaises(audit.smoke.SmokeError):
            self.inspect()


if __name__ == "__main__":
    unittest.main()
