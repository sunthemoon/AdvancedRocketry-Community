import copy
import re
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import run_v180_fluid_smoke as fluid


class FluidSmokeCaptureTests(unittest.TestCase):
    def fixture(self, current=True):
        items = [{"Slot": slot, "id": f"{fluid.NS}:{name}", "Count": 16,
                  "tag": {"marker": "legacy-kept" if slot < 3 else "new-nitrogen"}}
                 for slot, name in enumerate(fluid.CANISTERS[:4 if current else 3])]
        names = ["minecraft:lava"]
        if current:
            for slot, name in enumerate(("rocket_fuel_bucket", "enriched_lava_bucket"), 4):
                items.append({"Slot": slot, "id": f"{fluid.NS}:{name}", "Count": 1})
            names += [f"{fluid.NS}:rocket_fuel", f"{fluid.NS}:enriched_lava"]
        return {"block_entities": [{"id": "minecraft:chest", "x": 132, "y": 80, "z": 132, "Items": items}],
                "sections": [{"block_states": {"palette": [{"Name": name} for name in names]}}]}

    def capture(self, chunk, current=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "world/region").mkdir(parents=True)
            (root / "world/region/r.0.0.mca").write_bytes(b"fixture-region")
            with patch.object(fluid.regions, "_region_chunk", return_value=b"compressed-fixture"), \
                    patch.object(fluid.regions, "_decode_chunk", return_value=chunk):
                return fluid.capture(root, root, current)

    def test_complete_old_and_current_native_inventories_are_accepted(self):
        self.assertEqual(3, len(self.capture(self.fixture(False), False)))
        self.assertEqual(6, len(self.capture(self.fixture())))

    def test_missing_or_displaced_chest_is_rejected(self):
        chunk = self.fixture()
        chunk["block_entities"][0]["x"] = 133
        with self.assertRaises(RuntimeError):
            self.capture(chunk)

    def test_resource_loss_and_unexpected_item_payloads_are_rejected(self):
        for key, value in (("Count", 15), ("ForgeCaps", {"unknown": {}}), ("tag", {"marker": "changed"})):
            chunk = self.fixture()
            chunk["block_entities"][0]["Items"][1][key] = value
            with self.assertRaises(RuntimeError):
                self.capture(chunk)

    def test_missing_new_liquid_or_replaced_old_lava_is_rejected(self):
        for missing in ("minecraft:lava", f"{fluid.NS}:rocket_fuel", f"{fluid.NS}:enriched_lava"):
            chunk = self.fixture()
            palette = chunk["sections"][0]["block_states"]["palette"]
            palette[:] = [entry for entry in palette if entry["Name"] != missing]
            with self.assertRaises(RuntimeError):
                self.capture(chunk)

    def test_duplicate_and_extra_slots_are_rejected(self):
        for slot in (0, 6):
            chunk = self.fixture()
            extra = copy.deepcopy(chunk["block_entities"][0]["Items"][0])
            extra["Slot"] = slot
            chunk["block_entities"][0]["Items"].append(extra)
            with self.assertRaises(RuntimeError):
                self.capture(chunk)

    def test_legacy_lava_seed_and_queries_stay_inside_the_captured_chunk(self):
        # Guard the exact literal commands against a chunk-boundary regression.
        source = Path(fluid.__file__).read_text(encoding="utf-8")
        positions = re.findall(r'(?:setblock|execute if block) (\d+) 80 (\d+) minecraft:lava', source)
        self.assertEqual(3, len(positions))
        self.assertEqual(1, len(set(positions)))
        for x, z in positions:
            self.assertEqual((8, 8), (int(x) // 16, int(z) // 16))
            self.assertGreaterEqual(int(x) - 1, 128)
            self.assertLessEqual(int(x) + 1, 143)
            self.assertGreaterEqual(int(z) - 1, 128)
            self.assertLessEqual(int(z) + 1, 143)


if __name__ == "__main__":
    unittest.main()
