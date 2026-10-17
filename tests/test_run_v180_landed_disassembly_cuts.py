"""Fail-closed tests for the W85 landed-disassembly native cut inspector."""
import struct
import sys
import uuid
import zlib
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_landed_disassembly_cuts as runner


def state(landed=True, rockets=1, blocks=None, block_entities=()):
    return dict(records=[], landed=[{"transfer": "t"}] if landed else [], rockets=[{"uuid": "e"}] * rockets,
                blocks=blocks or {"0,80,0": "minecraft:air"}, block_entities=list(block_entities), files={})


def packed(palette_size: int, values: list[int]) -> list[int]:
    bits = max(4, (palette_size - 1).bit_length())
    per_long = 64 // bits
    longs = [0] * ((len(values) + per_long - 1) // per_long)
    for index, value in enumerate(values):
        longs[index // per_long] |= value << ((index % per_long) * bits)
    return [long - (1 << 64) if long >= 1 << 63 else long for long in longs]


class LandedDisassemblyCutsTest(unittest.TestCase):
    def test_uuid_int_arrays_decode_to_the_logged_form(self):
        value = uuid.UUID("01234567-89ab-cdef-fedc-ba9876543210")
        parts = [int.from_bytes(value.bytes[i:i + 4], "big", signed=True) for i in range(0, 16, 4)]
        self.assertEqual(str(value), runner.uuid_of(parts))
        self.assertEqual("x", runner.uuid_of("x"))
        self.assertIsNone(runner.uuid_of([1, 2, 3]))
        self.assertIsNone(runner.uuid_of(None))

    def test_block_states_decode_from_packed_palette_indices(self):
        palette = [{"Name": f"minecraft:b{i}"} for i in range(17)]
        values = [(index * 7) % 17 for index in range(4096)]
        chunk = {"sections": [{"Y": 5, "block_states": {"palette": palette, "data": packed(17, values)}}]}
        for x, y, z in ((0, 80, 0), (15, 95, 15), (3, 87, 9), (-1, 81, -16)):
            index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
            self.assertEqual(palette[values[index]]["Name"], runner.block_at(chunk, x, y, z))
        single = {"sections": [{"Y": -1, "block_states": {"palette": [{"Name": "minecraft:air"}]}}]}
        self.assertEqual("minecraft:air", runner.block_at(single, 4, -3, 4))
        self.assertEqual("<section-not-stored>", runner.block_at(single, 4, 64, 4))
        self.assertEqual("<chunk-not-stored>", runner.block_at(None, 0, 0, 0))

    def test_region_records_are_bounded_and_absent_slots_are_none(self):
        with TemporaryDirectory() as directory:
            region = Path(directory) / "r.0.0.mca"
            self.assertIsNone(runner.chunk_record(region, 0, 0))
            payload = bytes([10, 0, 0, 0])  # An empty root compound.
            compressed = zlib.compress(payload)
            header = bytearray(8192)
            header[4 * 33:4 * 33 + 4] = struct.pack(">I", (2 << 8) | 1)
            body = struct.pack(">IB", len(compressed) + 1, 2) + compressed
            region.write_bytes(bytes(header) + body + bytes(4096 - len(body)))
            self.assertEqual({}, runner.chunk_record(region, 1, 1))
            self.assertIsNone(runner.chunk_record(region, 0, 0))
            header[4 * 34:4 * 34 + 4] = struct.pack(">I", 1 << 8 | 1)
            region.write_bytes(bytes(header) + body + bytes(4096 - len(body)))
            with self.assertRaises(runner.smoke.SmokeError):
                runner.chunk_record(region, 2, 1)

    def test_expected_outcomes_refuse_restored_blocks_lost_or_duplicated_rockets(self):
        runner.check_expected("K1", state(), None)
        runner.check_expected("K2", state(landed=False, rockets=0), None)
        runner.check_expected("K4", state(blocks={"1,80,0": "minecraft:stone"}), "1,80,0")
        for case, bad in (("K1", state(landed=False)), ("K1", state(rockets=2)), ("K2", state(rockets=1)),
                          ("K5", state(landed=True)), ("K4", state(blocks={"2,80,0": "minecraft:stone"})),
                          ("K9", state(blocks={"0,80,0": "advancedrocketrycommunity:rocket_motor"})),
                          ("K10", state(block_entities=[{"id": "minecraft:chest"}]))):
            with self.assertRaises(runner.smoke.SmokeError, msg=case):
                runner.check_expected(case, bad, "1,80,0" if case == "K4" else None)

    def test_every_required_cut_is_planned_with_two_restarts_and_no_forced_flush(self):
        self.assertEqual({"K1", "K2", "K4", "K5", "K9", "K10"}, set(runner.CASES))
        self.assertEqual({"K10"}, {case for case, spec in runner.CASES.items() if spec["stop"] == "stop"})
        self.assertEqual({"K4"}, {case for case, spec in runner.CASES.items() if spec["obstruct"]})
        for case in ("K1", "K2", "K4", "K5"):
            self.assertEqual("LANDED_DISASSEMBLY:" + case, runner.CASES[case]["properties"][runner.PROBE])
        for case in ("K9", "K10"):
            self.assertEqual("LANDED_REMOVAL", runner.CASES[case]["properties"][runner.FAILURE])


if __name__ == "__main__":
    unittest.main()
