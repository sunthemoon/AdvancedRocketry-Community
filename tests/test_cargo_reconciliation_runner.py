import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_reconciliation as runner


class CargoReconciliationRunnerTest(unittest.TestCase):
    def sample(self, load, epochs, recovered=False):
        rocket_epoch, device_epoch = epochs
        root = dict(issued_through=1, records=[] if recovered else [dict(phase="PREPARED", seq=1, op_id="op")],
                    floors=[], tombstones=[])
        section = lambda epoch, role: dict(resource_epoch=epoch, markers=[] if recovered or not epoch else
                                          [dict(op_id="op", seq=1, role=role, epoch_after=1)])
        fluid = dict(Amount=1000 * rocket_epoch if load else 5000 - 1000 * rocket_epoch)
        payload = dict(adapter="advancedrocketrycommunity:pressurized_tank_v1", data=dict(tank=dict(fluid=fluid)))
        rocket = dict(UUID="physical", Pos=[68.5, 100.0, 64.5], RocketEntityData=dict(
            owner_id="owner", assembly_transaction_id="logical", resource_escrow=section(rocket_epoch, "DESTINATION" if load else "SOURCE"),
            flight_data=dict(passengers=dict(assignments=[])), snapshot=dict(snapshot_id="snapshot", relative_blocks=[dict(block_entity=payload)])))
        device = dict(x=64, y=100, z=64, arce_rocket_fluid_cargo=dict(owner="owner", device="device",
            binding=dict(logical_rocket="logical", physical_rocket="physical"),
            tank=dict(Amount=5000 - 1000 * device_epoch if load else 1000 * device_epoch),
            resource_escrow=section(device_epoch, "SOURCE" if load else "DESTINATION")))
        return root, rocket, device

    def inspect(self, sample, case, restarted=False):
        root, rocket, device = sample
        with patch.object(runner, "ledger", return_value=root), patch.object(runner, "journal", return_value=dict(transfers=[])), \
                patch.object(runner, "digest", return_value="hash"), patch.object(runner, "record", side_effect=[
                    dict(Entities=[rocket]), dict(block_entities=[device])]):
            return runner.inspect(Path("unused-world"), case, restarted)

    def test_both_explicit_store_orders_are_declared_in_each_direction(self):
        self.assertEqual(len(runner.CASES), len(set(runner.CASES)))
        for direction in ("LOAD", "UNLOAD"):
            for side in ("SOURCE", "DESTINATION"):
                self.assertIn(f"FC_{direction}_{side}_ONLY_STORE", runner.CASES)

    def test_selected_side_epochs_amounts_and_prepared_liability_are_required(self):
        for load in (True, False):
            for source in (True, False):
                epochs = [0, 1] if load == source else [1, 0]
                case = f"FC_{'LOAD' if load else 'UNLOAD'}_{'SOURCE' if source else 'DESTINATION'}_ONLY_STORE"
                with self.subTest(case=case):
                    sample = self.sample(load, epochs)
                    result = self.inspect(sample, case)
                    self.assertEqual(4000 if source else 6000, result["raw_total"])
                    self.assertEqual(epochs, result["epochs"])
                    self.assertEqual("SOURCE" if source else "DESTINATION", result["persisted_side"])
                    for wrong in ([1, 0] if epochs == [0, 1] else [0, 1], [0, 0], [1, 1]):
                        with self.assertRaises(runner.smoke.SmokeError):
                            self.inspect(self.sample(load, wrong), case)
                    for mutate in (
                            lambda s: s[0].update(records=[]),
                            lambda s: s[0]["records"][0].update(phase="REPAIR"),
                            lambda s: s[0].update(issued_through=2),
                            lambda s: s[2]["arce_rocket_fluid_cargo"]["tank"].update(Amount=1234),
                            lambda s: s[1]["RocketEntityData"]["resource_escrow"].update(markers=[] if epochs[0] else [dict(op_id="foreign")]),
                            lambda s: s[2]["arce_rocket_fluid_cargo"]["resource_escrow"].update(markers=[] if epochs[1] else [dict(op_id="foreign")])):
                        bad = copy.deepcopy(sample); mutate(bad)
                        with self.assertRaises(runner.smoke.SmokeError):
                            self.inspect(bad, case)

    def test_each_restart_requires_one_transfer_conservation_retirement_and_pruning(self):
        for load in (True, False):
            for side in ("SOURCE", "DESTINATION"):
                case = f"FC_{'LOAD' if load else 'UNLOAD'}_{side}_ONLY_STORE"
                sample = self.sample(load, [1, 1], True)
                for _ in range(2):
                    self.assertEqual(5000, self.inspect(sample, case, True)["raw_total"])
                for mutate in (
                        lambda s: s[0].update(issued_through=2),
                        lambda s: s[0].update(records=[dict(phase="PREPARED")]),
                        lambda s: s[1]["RocketEntityData"]["resource_escrow"].update(resource_epoch=2),
                        lambda s: s[1]["RocketEntityData"]["resource_escrow"].update(markers=[dict(op_id="op")]),
                        lambda s: s[2]["arce_rocket_fluid_cargo"]["resource_escrow"].update(markers=[dict(op_id="op")]),
                        lambda s: s[2]["arce_rocket_fluid_cargo"]["tank"].update(Amount=9999)):
                    bad = copy.deepcopy(sample); mutate(bad)
                    with self.assertRaises(runner.smoke.SmokeError):
                        self.inspect(bad, case, True)


if __name__ == "__main__":
    unittest.main()
