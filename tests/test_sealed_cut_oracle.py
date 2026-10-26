"""Resource/election checks for the sealed native-cut reader, without starting Minecraft."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


class SealedCutOracleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(__file__).resolve().parents[1] / "scripts/sealed_cut_oracle.py"
        spec = importlib.util.spec_from_file_location("sealed_cut_oracle", path)
        cls.oracle = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.oracle)

    @staticmethod
    def item(label="original", count=7):
        return {"id": "minecraft:diamond", "Count": count, "tag": {"W152": label}}

    def test_item_metadata_is_not_a_second_inventory(self):
        item = self.item(); item["tag"]["nested"] = self.item(count=99)
        self.assertEqual({"original": 7}, self.oracle.inventory({"Items": [item]}))

    def test_item_count_and_label_are_validated(self):
        for count in (-1, 0, 65, True):
            with self.assertRaises(ValueError):
                self.oracle.inventory(self.item(count=count))

    def test_fluid_payload_is_counted_once_with_its_native_amount(self):
        fluid = {"FluidName": "minecraft:water", "Amount": 1000, "Tag": {"W152": "original-fluid"}}
        self.assertEqual({"original-fluid": 1000}, self.oracle.inventory({"tank": fluid}))
        fluid["Amount"] = -1
        with self.assertRaises(ValueError):
            self.oracle.inventory(fluid)

    def test_pre_election_d_selects_d_without_counting_sealed_shadows_twice(self):
        observation = self.oracle.elect("LOCKED", [self.item()], [self.item()], [], [])
        self.assertEqual("ENTITY", observation["authority"])
        self.assertEqual({"original": 7}, observation["effective"])
        self.assertEqual({"original": 7}, observation["sealed_shadow"])

    def test_elected_u_keeps_c_and_never_counts_d_shadow_as_a_second_resource(self):
        observation = self.oracle.elect("RESTORED", [self.item()], [self.item()], [], [])
        self.assertEqual("BLOCKS", observation["authority"])
        self.assertEqual({"original": 7}, observation["effective"])

    def test_missing_d_fallback_keeps_the_full_u_snapshot_authority(self):
        observation = self.oracle.elect("LOCKED", [], [], [], [], retained=self.item())
        self.assertEqual("RETAINED_SNAPSHOT", observation["authority"])
        self.assertEqual({"original": 7}, observation["effective"])

    def test_checked_absence_counts_mixed_c_n_and_independent_recipients(self):
        observation = self.oracle.elect(None, [], [self.item(count=3)], [self.item(count=2)], [self.item(count=2), self.item("foreign", 3)])
        self.assertEqual({"original": 7, "foreign": 3}, observation["effective"])

    def test_native_inventory_with_u_or_competing_unretained_d_is_not_accepted(self):
        for phase, entities, sealed, native in (("RESTORED", [], [], [self.item()]),
                ("LOCKED", [self.item()], [], [self.item()]), (None, [self.item()], [self.item()], [])):
            with self.assertRaises(ValueError):
                self.oracle.elect(phase, entities, sealed, native, [])

    def test_cut_inventory_does_not_hide_a_duplicated_foreign_resource(self):
        observation = self.oracle.elect(None, [], [], [self.item(), self.item("foreign", 3)], [self.item("foreign", 3)])
        self.assertEqual(6, observation["effective"]["foreign"])
        with self.assertRaises(ValueError):
            self.oracle.require_inventory(observation, {"original": 7, "foreign": 3})


if __name__ == "__main__":
    unittest.main()
