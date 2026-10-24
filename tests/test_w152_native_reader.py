import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))


class W152NativeReaderTest(unittest.TestCase):
    def test_entity_projection_keeps_non_inventory_identity_for_restart_comparison(self):
        from run_w152_native import inspect
        data = {"owner": "native-owner", "snapshot": {"items": [{"id": "minecraft:diamond", "Count": 14, "tag": {"W152": "original"}}]}}
        entity = {"id": "advancedrocketrycommunity:rocket", "UUID": [1, 2, 3, 4], "Pos": [15.5, 100., 15.5], "RocketEntityData": data}
        def image(path, x, z):
            return {"Entities": [entity]} if path.parent.name == "entities" and (x, z) == (0, 0) else None
        with patch("run_w152_native.chunk_record", side_effect=image):
            result = inspect(Path("missing-world"))
        self.assertEqual(result["entity_data"][0]["RocketEntityData"], data)

    def test_missing_baseline_is_not_conservation(self):
        from run_w152_native import inspect
        with patch("run_w152_native.chunk_record", return_value=None):
            with self.assertRaisesRegex(ValueError, "conservation"):
                inspect(Path("missing-world"))

    def test_cut_table_covers_both_journal_replacements_and_native_forcing(self):
        from run_w152_native import CUTS
        for boundary in ("C1_MOVE_BEFORE", "C1_MOVE_AFTER", "C3_MOVE_AFTER", "C5_MOVE_AFTER", "C3_UNKNOWN", "C5_UNKNOWN",
                         "PARTIAL_C_SAVED", "SEALED:TERRAIN:BARRIER_FORCE_AFTER", "ROLLBACK_ERASE_AFTER", "UNSEAL_PUBLISH_AFTER"):
            self.assertIn(boundary, CUTS)

    def test_stability_comparison_preserves_resources_but_not_evidence_hashes(self):
        from run_w152_native import comparable
        self.assertEqual(comparable(dict(authority="ENTITY", effective={"original": 14}, blocks={"p": "air"},
                                         entities=["id"], files={"region": "a"})),
                         comparable(dict(authority="ENTITY", effective={"original": 14}, blocks={"p": "air"},
                                         entities=["id"], files={"region": "b"})))


if __name__ == "__main__":
    unittest.main()
