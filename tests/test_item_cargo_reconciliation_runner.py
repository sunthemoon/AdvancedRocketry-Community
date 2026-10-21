import copy
from pathlib import Path
import struct
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from inspect_celestial_saved_data import NbtReader
import run_v180_cargo_reconciliation as runner
import test_cargo_reconciliation_runner as fluid_tests


def probe_stack(fixture_types: bool) -> bytes:
    """The W132 review's probe_oracle_nbt_types.py stack: the fixture's tag types, or b and Count as TAG_Int and
    ba as TAG_Int_Array with the same values."""
    def name(text):
        raw = text.encode("utf-8")
        return struct.pack(">H", len(raw)) + raw
    tag = (bytes([1]) + name("b") + struct.pack(">b", 7)) if fixture_types else (bytes([3]) + name("b") + struct.pack(">i", 7))
    tag += bytes([2]) + name("s") + struct.pack(">h", 7)
    tag += bytes([3]) + name("i") + struct.pack(">i", 7)
    tag += bytes([4]) + name("l") + struct.pack(">q", 7)
    if fixture_types:
        tag += bytes([7]) + name("ba") + struct.pack(">i", 2) + bytes([1, 2])
    else:
        tag += bytes([11]) + name("ba") + struct.pack(">i", 2) + struct.pack(">ii", 1, 2)
    tag += bytes([11]) + name("ia") + struct.pack(">i", 2) + struct.pack(">ii", 1, 2)
    tag += bytes([12]) + name("la") + struct.pack(">i", 2) + struct.pack(">qq", 1, 2)
    tag += bytes([0])
    body = bytes([8]) + name("id") + name("minecraft:stone")
    body += (bytes([1]) + name("Count") + struct.pack(">b", 10)) if fixture_types else (bytes([3]) + name("Count") + struct.pack(">i", 10))
    body += bytes([10]) + name("tag") + tag
    body += bytes([0])
    return bytes([10]) + name("") + body


class ItemCargoReconciliationRunnerTest(unittest.TestCase):
    inspect = fluid_tests.CargoReconciliationRunnerTest.inspect

    def sample(self, load, epochs, recovered=False):
        root, rocket, device = fluid_tests.CargoReconciliationRunnerTest().sample(load, epochs, recovered)
        def stack(count, slot=False):
            # As a TypedNbtReader decodes ItemCargoReconciliationFixtures.stack (W132-R3).
            result = dict(id="minecraft:stone", Count=runner.Byte(count),
                          tag=dict(b=runner.Byte(7), s=runner.Short(7), i=runner.Int(7), l=runner.Long(7),
                                   ba=runner.ByteArray([1, 2]), ia=runner.IntArray([1, 2]), la=runner.LongArray([1, 2])))
            if slot: result["Slot"] = runner.Byte(0)
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
                slot = lambda s: s[2]["arce_rocket_item_cargo"]["buffer"]["slots"][0]
                entry = lambda s: s[1]["RocketEntityData"]["snapshot"]["relative_blocks"][0]["block_entity"]["data"]["Items"][0]
                for mutate in (
                    lambda s: s[0].update(records=[]),
                    lambda s: s[2]["arce_rocket_item_cargo"]["buffer"]["slots"].pop(),
                    lambda s: slot(s)["tag"].update(b=8),
                    lambda s: slot(s).update(Count=32),
                    lambda s: s[1]["RocketEntityData"]["snapshot"]["relative_blocks"][0]["block_entity"]["data"]["Items"].append(
                        copy.deepcopy(s[1]["RocketEntityData"]["snapshot"]["relative_blocks"][0]["block_entity"]["data"]["Items"][0])),
                    # W132-R3: the same values under other NBT tag types.
                    lambda s: slot(s)["tag"].update(b=runner.Int(7)),
                    lambda s: slot(s)["tag"].update(l=runner.Int(7)),
                    lambda s: slot(s)["tag"].update(ba=runner.IntArray([1, 2])),
                    lambda s: slot(s)["tag"].update(la=[1, 2]),
                    lambda s: slot(s).update(Count=runner.Int(slot(s)["Count"])),
                    lambda s: entry(s).update(Slot=runner.Int(0)),
                    lambda s: entry(s)["tag"].update(s=runner.Byte(7))):
                    bad = copy.deepcopy(sample); mutate(bad)
                    with self.assertRaises(runner.smoke.SmokeError): self.inspect(bad, case)

    def test_typed_decode_refuses_the_review_probe_whose_tag_types_changed(self):
        typed = runner.TypedNbtReader(probe_stack(True)).read_root()
        changed = runner.TypedNbtReader(probe_stack(False)).read_root()
        # The untyped reader cannot tell them apart, which is why the review's probe was accepted.
        self.assertEqual(NbtReader(probe_stack(True)).read_root(), NbtReader(probe_stack(False)).read_root())
        self.assertEqual(typed, changed)
        self.assertFalse(runner.same_typed(typed, changed))
        self.assertEqual(10, runner.item_amount([typed, {}, {}, {}], True))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.item_amount([changed, {}, {}, {}], True)

    def test_item_cases_read_typed_and_fluid_cases_read_as_before(self):
        calls = []
        def record(path, **kwargs):
            calls.append(kwargs)
            return dict(Entities=[]) if "entities" in str(path) else dict(block_entities=[])
        with patch.object(runner, "ledger", return_value={}), patch.object(runner, "record", record):
            for case in ("IC_LOAD_PREPARING", "FC_LOAD_PREPARING"):
                with self.assertRaises(runner.smoke.SmokeError): runner.inspect(Path("unused-world"), case, False)
        self.assertEqual([dict(reader=runner.TypedNbtReader)] * 2 + [{}] * 2, calls)


if __name__ == "__main__":
    unittest.main()
