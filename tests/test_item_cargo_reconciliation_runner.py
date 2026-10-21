import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_reconciliation as runner
import test_cargo_reconciliation_runner as fluid_tests


class ItemCargoReconciliationRunnerTest(unittest.TestCase):
    inspect = fluid_tests.CargoReconciliationRunnerTest.inspect

    def sample(self, load, epochs, recovered=False):
        root, rocket, device = fluid_tests.CargoReconciliationRunnerTest().sample(load, epochs, recovered)
        def stack(count, slot=False):
            result = dict(id="minecraft:stone", Count=count,
                          tag=dict(b=7, s=7, i=7, l=7, ba=[1, 2], ia=[1, 2], la=[1, 2]))
            if slot: result["Slot"] = 0
            return result
        payload = rocket["RocketEntityData"]["snapshot"]["relative_blocks"][0]["block_entity"]
        payload.update(adapter="advancedrocketrycommunity:vanilla_container_v1",
                       data=dict(Items=[stack(60 + 4 * epochs[0] if load else 10 - 4 * epochs[0], True)]))
        model = device.pop("arce_rocket_fluid_cargo"); model.pop("tank")
        model["buffer"] = dict(slots=[stack(10 - 4 * epochs[1] if load else 60 + 4 * epochs[1]), {}, {}, {}])
        device["arce_rocket_item_cargo"] = model
        return root, rocket, device

    def test_all_cuts_include_both_item_directions(self):
        self.assertEqual(24, len(set(runner.ITEM_CASES)))
        for direction in ("LOAD", "UNLOAD"):
            for cut in runner.CUTS: self.assertIn(f"IC_{direction}_{cut}", runner.ITEM_CASES)

    def test_deterministic_mixed_native_frontiers_and_two_restart_oracle(self):
        for load in (True, False):
            for source in (True, False):
                case = f"IC_{'LOAD' if load else 'UNLOAD'}_{'SOURCE' if source else 'DESTINATION'}_ONLY_STORE"
                epochs = [0, 1] if load == source else [1, 0]
                sample = self.sample(load, epochs)
                result = self.inspect(sample, case)
                self.assertEqual(66 if source else 74, result["raw_total"])
                self.assertEqual("SOURCE" if source else "DESTINATION", result["persisted_side"])
                for wrong in ([0, 0], [1, 1], list(reversed(epochs))):
                    with self.assertRaises(runner.smoke.SmokeError):
                        self.inspect(self.sample(load, wrong), case)
                for _ in range(2):
                    self.assertEqual(70, self.inspect(self.sample(load, [1, 1], True), case, True)["raw_total"])
                for mutate in (
                    lambda s: s[0].update(records=[]),
                    lambda s: s[2]["arce_rocket_item_cargo"]["buffer"]["slots"].pop(),
                    lambda s: s[2]["arce_rocket_item_cargo"]["buffer"]["slots"][0]["tag"].update(b=8),
                    lambda s: s[2]["arce_rocket_item_cargo"]["buffer"]["slots"][0].update(Count=32),
                    lambda s: s[1]["RocketEntityData"]["snapshot"]["relative_blocks"][0]["block_entity"]["data"]["Items"].append(
                        copy.deepcopy(s[1]["RocketEntityData"]["snapshot"]["relative_blocks"][0]["block_entity"]["data"]["Items"][0]))):
                    bad = copy.deepcopy(sample); mutate(bad)
                    with self.assertRaises(runner.smoke.SmokeError): self.inspect(bad, case)


if __name__ == "__main__":
    unittest.main()
