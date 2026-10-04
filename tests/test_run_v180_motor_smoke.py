"""Bounded exact-cell evidence checks for the separate motor native harness."""

import copy
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from scripts import run_v180_motor_smoke as motors


def fixture(current=True):
    palette = [{"Name": "minecraft:air"}, {"Name": "minecraft:chest", "Properties": {
        "facing": "north", "type": "single", "waterlogged": "false"}},
        {"Name": "advancedrocketrycommunity:endgame_casing"}]
    palette += [{"Name": "advancedrocketrycommunity:" + name} for name in motors.MOTORS]
    data = [0] * 256

    def set_cell(position, index):
        x, y, z = position
        cell = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        data[cell // 16] |= index << ((cell % 16) * 4)

    set_cell(motors.CHEST, 1)
    set_cell(motors.CASING, 2)
    if current:
        for offset in range(4):
            set_cell((134 + offset, 80, 132), 3 + offset)
    return {"xPos": 8, "zPos": 8, "sections": [{"Y": 5, "block_states": {"palette": palette, "data": data}}],
            "block_entities": [{"id": "minecraft:chest", "x": 132, "y": 80, "z": 132,
                                "Items": list(motors.expected_items(current).values())}]}


class MotorNativeEvidenceTest(unittest.TestCase):
    def test_exact_coordinates_and_whole_inventory_are_checked(self):
        chunk = fixture()
        before = copy.deepcopy(chunk)
        result = motors.check_chunk(chunk, True)
        self.assertEqual(result["items"], motors.expected_items(True))
        self.assertEqual(chunk, before)
        self.assertEqual(len(result["blocks"]), 5)
        self.assertEqual(motors.check_chunk(fixture(False), False)["items"], motors.expected_items(False))

    def test_palette_presence_cannot_substitute_for_the_saved_cell(self):
        chunk = fixture()
        chunk["sections"][0]["block_states"]["data"] = [0] * 256
        with self.assertRaisesRegex(RuntimeError, "chest state"):
            motors.check_chunk(chunk, True)
        with self.assertRaisesRegex(RuntimeError, "Motor block differs"):
            motors.check_chunk(fixture(False) | {"block_entities": fixture()["block_entities"]}, True)

    def test_id_count_metadata_and_duplicate_slots_are_not_weakened(self):
        for mutate in (lambda item: item.update(Count=2), lambda item: item.update(id="minecraft:stone"),
                       lambda item: item["tag"].update(marker="changed")):
            chunk = fixture()
            mutate(chunk["block_entities"][0]["Items"][1])
            with self.assertRaisesRegex(RuntimeError, "item ID, count or metadata"):
                motors.check_chunk(chunk, True)
        chunk = fixture()
        chunk["block_entities"][0]["Items"].append(copy.deepcopy(chunk["block_entities"][0]["Items"][0]))
        with self.assertRaisesRegex(RuntimeError, "Duplicate"):
            motors.check_chunk(chunk, True)

    def test_wrong_chunk_and_short_packed_arrays_fail_closed(self):
        chunk = fixture()
        chunk["xPos"] = 9
        with self.assertRaisesRegex(RuntimeError, "exact chunk"):
            motors.block_at(chunk, motors.CASING)
        chunk = fixture()
        chunk["sections"][0]["block_states"]["data"].pop()
        with self.assertRaisesRegex(RuntimeError, "wrong size"):
            motors.block_at(chunk, motors.CASING)
        with self.assertRaisesRegex(RuntimeError, "outside"):
            motors.block_at(fixture(), (256, 80, 132))

    def test_single_palette_and_nonspanning_five_bit_words(self):
        chunk = {"xPos": 8, "zPos": 8, "sections": [{"Y": 5, "block_states": {
            "palette": [{"Name": "minecraft:stone"}]}}]}
        self.assertEqual(motors.block_at(chunk, motors.CASING), {"Name": "minecraft:stone"})
        palette = [{"Name": "test:block_" + str(index)} for index in range(17)]
        data = [0] * ((4096 + 11) // 12)
        cell = (4 << 4) | 5
        data[cell // 12] = 16 << ((cell % 12) * 5)
        chunk["sections"][0]["block_states"] = {"palette": palette, "data": data}
        self.assertEqual(motors.block_at(chunk, motors.CASING), {"Name": "test:block_16"})

    def test_forceload_markers_bind_the_fixed_chunk_and_allow_idempotent_add(self):
        self.assertIsNotNone(motors.FORCELOAD_RESULT.search("No chunks were marked for force loading"))
        self.assertIsNotNone(motors.FORCELOAD_RESULT.search("Marked chunk [8, 8] in minecraft:overworld to be force loaded"))
        self.assertIsNotNone(motors.FORCELOAD_QUERY.search("Chunk at [8, 8] in minecraft:overworld is marked for force loading"))
        self.assertIsNone(motors.FORCELOAD_QUERY.search("Chunk at [8, 80] in minecraft:overworld is marked for force loading"))

    def test_artifact_identity_is_bound_and_link_inputs_are_refused(self):
        with tempfile.TemporaryDirectory(prefix="arce-motor-unit-") as directory:
            path = Path(directory) / "host.jar"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("META-INF/mods.toml", 'modId="advancedrocketrycommunity"\nversion="1.20.1-1.8.0-dev"\n')
            before = path.read_bytes()
            identity = motors.artifact(path, "1.20.1-1.8.0-dev")
            self.assertEqual(identity["bytes"], len(before))
            self.assertEqual(path.read_bytes(), before)
            with self.assertRaisesRegex(RuntimeError, "identity/version"):
                motors.artifact(path, "1.20.1-1.7.0-dev")
            with patch.object(motors.server, "is_link_or_junction", return_value=True):
                with self.assertRaisesRegex(RuntimeError, "regular file"):
                    motors.artifact(path, "1.20.1-1.8.0-dev")


if __name__ == "__main__":
    unittest.main()
