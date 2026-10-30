"""Inventory completeness and value transformations, without starting native servers."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import inventory_escrow_probe_cases as inventory


class EscrowProbeInventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows, cls.coverage = inventory.inventory()

    def test_committed_inventory_matches_all_reviewed_producers_and_test_tokens(self):
        self.assertEqual(inventory.TARGET.read_text(encoding="utf-8"), inventory.render(self.rows))
        self.assertEqual(585, len(self.rows))
        self.assertEqual(23, len(self.coverage["direct"]))
        self.assertEqual(4, len(self.coverage["delegated"]))
        self.assertEqual(33, len(self.coverage["tests"]))

    def test_s6_producer_and_related_tests_are_registered(self):
        self.assertIn("run_v180_loader_s6", self.coverage["direct"])
        self.assertIn("scripts/run_v180_loader_s6.py", self.rows["LF_S6_E3_CACHE_CLOSE"])
        self.assertIn("tests/test_loader_s6_oracle.py", self.coverage["tests"])

    def test_transformed_values_are_emitted_names_not_runner_labels(self):
        origin = "scripts/run_v180_item_cuts.py"
        self.assertNotIn(origin, self.rows.get("IC_PARTIAL_APPLY", set()))
        self.assertNotIn(origin, self.rows.get("IC_LOAD_K2", set()))
        self.assertIn(origin, self.rows["IC_LOAD_L1_REPLACED"])
        self.assertIn(origin, self.rows["IC_LOAD_C2_DESTINATION_AFTER"])
        self.assertIn("scripts/run_v180_fill_countdown.py", self.rows["LF_M_W133F_ASSEMBLED"])
        self.assertIn("scripts/run_v180_retire_before_fill.py", self.rows["LF_M_R52_CAPTURE"])
        self.assertIn("scripts/run_v180_f3_n2_cuts.py", self.rows["RT_CANCEL_SEPARATE_0_READBACK_AFTER"])

    def test_delegated_and_imported_case_domains_are_retained(self):
        for case, origin in (("FD_DESCENT", "escrow_arrival"), ("FT_HANDOFF_BEFORE", "escrow_terminal"),
                             ("RT_CANCEL_SHARED_0_STORE_BEFORE", "escrow_returns"), ("SR_HELD_B__O4_AFTER", "escrow_pruning"),
                             ("LF_M_E1_OWN_STORE", "loader_cuts")):
            self.assertIn("scripts/run_v180_" + origin + ".py", self.rows[case])
        items = {name for name in self.rows if name.startswith(("IS_", "IR_"))}
        self.assertEqual(18, len(items))


if __name__ == "__main__":
    unittest.main()
