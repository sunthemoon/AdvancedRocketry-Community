import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import cargo_storage_oracle as oracle


class CargoStorageOracleTest(unittest.TestCase):
    def raw(self, external=False, restart=False):
        section = lambda epoch: dict(resource_epoch=epoch, markers=[])
        block = dict(block_entity=dict(adapter="advancedrocketrycommunity:pressurized_tank_v1",
                     data=dict(tank=dict(fluid=dict(FluidName="minecraft:water", Amount=1000)))))
        rocket = dict(owner_id="owner", assembly_transaction_id="logical", resource_escrow=section(1),
                      flight_data=dict(passengers=dict(assignments=[])), snapshot=dict(snapshot_id="snapshot",
                      relative_blocks=[block]))
        device = dict(owner="owner", device="device", binding=dict(logical_rocket="logical", physical_rocket="physical"),
                      resource_escrow=section(1), tank=dict(FluidName="minecraft:water", Amount=4000))
        return dict(ledger=dict(issued_through=1, records=[] if restart and not external else [dict(phase="PREPARED")], floors=[], tombstones=[]),
                    journal=dict(transfers=[]), records=dict(terrain=dict(external=False), entities=dict(external=external)),
                    entities=dict(Entities=[dict(UUID="physical", Pos=[68.5, 100.0, 64.5], RocketEntityData=rocket,
                                  ForgeData=dict(arce_w138_noise=dict(bytes=1048576, sha256="0" * 64)) if external else {})]),
                    terrain=dict(block_entities=[dict(x=64, y=100, z=64, arce_rocket_fluid_cargo=device)]))

    def inspect(self, raw, case, restart=False):
        with patch.object(oracle, "read", return_value=raw), patch.object(oracle.native, "digest", return_value="hash"):
            return oracle.inspect(Path("world"), case, restart)

    def test_own_store_and_newer_save_preserve_l1_then_retire_exactly_once(self):
        for case in ("FC_LOAD_E1_OWN_STORE", "FC_LOAD_E2_CAPTURE"):
            self.assertEqual(5000, self.inspect(self.raw(), case)["raw_total"])
            self.assertEqual([1, 1], self.inspect(self.raw(restart=True), case, True)["epochs"])
            with self.assertRaises(oracle.native.smoke.SmokeError):
                self.inspect(self.raw(), case, True)

    def test_external_record_retains_l1_after_both_restarts(self):
        case = "FC_LOAD_E2_ROCKET_EXTERNAL"
        for restart in (False, True):
            self.assertEqual(5000, self.inspect(self.raw(external=True), case, restart)["raw_total"])
            for mutate in (lambda raw: raw["ledger"].update(records=[]),
                           lambda raw: raw["records"]["entities"].update(external=False),
                           lambda raw: raw["entities"]["Entities"][0]["ForgeData"]["arce_w138_noise"].update(bytes=0)):
                raw = self.raw(external=True); mutate(raw)
                with self.assertRaises(oracle.native.smoke.SmokeError):
                    self.inspect(raw, case, restart)

    def test_mixed_raw_quantities_are_not_confused_with_spendable_authority(self):
        raw = self.raw(); rocket = raw["entities"]["Entities"][0]["RocketEntityData"]
        rocket["resource_escrow"]["resource_epoch"] = 0
        rocket["snapshot"]["relative_blocks"][0]["block_entity"]["data"]["tank"]["fluid"] = {}
        self.assertEqual(4000, self.inspect(raw, "FC_LOAD_E1_OWN_STORE")["raw_total"])
        with self.assertRaises(oracle.native.smoke.SmokeError):
            self.inspect(raw, "FC_LOAD_E1_OWN_STORE", True)
        changed = copy.deepcopy(raw); changed["terrain"]["block_entities"][0]["arce_rocket_fluid_cargo"]["tank"]["Amount"] = 5000
        with self.assertRaises(oracle.native.smoke.SmokeError):
            self.inspect(changed, "FC_LOAD_E1_OWN_STORE")

    def test_identity_and_epoch_corruption_are_not_hidden_by_equal_totals(self):
        for mutate in (lambda raw: raw["ledger"].update(issued_through=2),
                       lambda raw: raw["journal"].update(transfers=[{}]),
                       lambda raw: raw["entities"]["Entities"][0].update(UUID="foreign"),
                       lambda raw: raw["entities"]["Entities"][0]["RocketEntityData"]["resource_escrow"].update(resource_epoch=2)):
            raw = self.raw(); mutate(raw)
            with self.assertRaises(oracle.native.smoke.SmokeError):
                self.inspect(raw, "FC_LOAD_E1_OWN_STORE")


if __name__ == "__main__":
    unittest.main()
