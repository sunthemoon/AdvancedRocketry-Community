import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import cargo_s1_oracle as oracle
import run_v180_cargo_s1 as runner
from run_v180_cargo_reconciliation import Byte, Int, Long, IntArray, LongArray
from run_dedicated_server_smoke import SmokeError


def sample(items):
    ns = oracle.NS
    entries = [(ns + ":rocket_motor", (0, 0, 0)), (ns + ":rocket_seat", (0, 1, 0)),
               (ns + ":guidance_computer", (0, 2, 0)), (ns + ":rocket_fuel_tank", (-1, 0, 0)),
               ("minecraft:chest", (0, 0, -1)), ("minecraft:barrel", (0, 0, 1)), ("minecraft:chest", (2, 0, 0)),
               ("minecraft:iron_block" if items else ns + ":pressurized_tank", (1, 0, 0))]
    palette = [dict(id=name, properties={}) for name, _ in entries]
    blocks = [dict(palette=Int(i), position=IntArray(list(at))) for i, (_, at) in enumerate(entries)]
    for i in range(3):
        blocks[4 + i]["block_entity"] = dict(adapter=ns + ":vanilla_container_v1", data=dict(
            Items=[oracle.stack(17 + i, i * 13)], CustomName='{"text":"S1 retained cargo"}', Lock="S1 lock"))
    if not items:
        blocks[7]["block_entity"] = dict(adapter=ns + ":pressurized_tank_v1", data=dict(
            tank=dict(schema=Int(1), fluid=dict(FluidName="minecraft:water", Amount=Int(3456), Tag=oracle.typed_tag()))))
    rocket = dict(id=ns + ":rocket", UUID=oracle.uuid_nbt(50), Pos=[72.5, 100.0, 72.5], RocketEntityData=dict(
        owner_id=oracle.uuid_nbt(1), assembly_transaction_id=oracle.uuid_nbt(51),
        resource_escrow=dict(schema_version=Int(1), resource_epoch=Long(0), markers=[]),
        snapshot=dict(relative_blocks=blocks, block_palette=palette)))
    terrain = dict(xPos=Int(4), zPos=Int(4), block_entities=[], sections=[])
    ids = [ns + ":rocket_" + ("item" if items else "fluid") + suffix for suffix in ("_loader", "_unloader")]
    packed = [0] * 256
    for role in range(2):
        fixtures = [(72 + role * 2, 76, 10 + role, "valid", oracle.binding(rocket))]
        fixtures += [(64 + role * 5 + i, 79, 20 + role * 5 + i, variant, None)
                     for i, variant in enumerate(oracle.VARIANTS)]
        for x, z, identity, variant, binding in fixtures:
            be = dict(id=ids[role], x=Int(x), y=Int(100), z=Int(z))
            be["arce_rocket_" + ("item" if items else "fluid") + "_cargo"] = oracle.expected_root(items, role, identity, binding, variant)
            terrain["block_entities"].append(be)
            cell = (4 << 8) | ((z & 15) << 4) | (x & 15)
            packed[cell // 16] |= (role + 1) << (cell % 16 * 4)
    packed = [value if value < 1 << 63 else value - (1 << 64) for value in packed]
    terrain["sections"] = [dict(Y=Byte(6), block_states=dict(palette=[dict(Name="minecraft:air"), *[dict(Name=value) for value in ids]],
                                                          data=LongArray(packed)))]
    return rocket, terrain


class CargoS1OracleTest(unittest.TestCase):
    def test_both_leaf_fixtures_and_two_exact_restarts(self):
        for items in (False, True):
            rocket, terrain = sample(items)
            baseline = oracle.check_new(rocket, terrain, items)
            for _ in range(2):
                oracle.compare_exact(baseline, oracle.check_new(copy.deepcopy(rocket), copy.deepcopy(terrain), items))

    def test_item_equal_numeric_values_with_wrong_tag_types_are_rejected(self):
        for key in ("Count", "Slot", "b", "s", "i", "l", "ba", "ia", "la"):
            rocket, terrain = sample(True)
            entry = rocket["RocketEntityData"]["snapshot"]["relative_blocks"][4]["block_entity"]["data"]["Items"][0]
            target = entry if key in ("Count", "Slot") else entry["tag"]
            value = target[key]
            target[key] = IntArray(value) if isinstance(value, list) and type(value) is not IntArray else (
                LongArray(value) if isinstance(value, list) else Int(value) if type(value) is not Int else Long(value))
            with self.subTest(key=key), self.assertRaises(SmokeError):
                oracle.check_new(rocket, terrain, True)

    def test_tank_tag_type_and_amount_mutations_are_rejected(self):
        for mutation in (lambda root: root.update(Amount=Long(3456)), lambda root: root.update(Amount=Int(3457)),
                         lambda root: root["Tag"].update(b=Int(7)), lambda root: root.update(FluidName="minecraft:lava")):
            rocket, terrain = sample(False)
            mutation(rocket["RocketEntityData"]["snapshot"]["relative_blocks"][7]["block_entity"]["data"]["tank"]["fluid"])
            with self.assertRaises(SmokeError):
                oracle.check_new(rocket, terrain, False)

    def test_every_protected_root_is_compared_not_just_resource_totals(self):
        for items in (False, True):
            for index in range(12):
                rocket, terrain = sample(items)
                root = terrain["block_entities"][index]["arce_rocket_" + ("item" if items else "fluid") + "_cargo"]
                root["unknown"] = "mutation"
                with self.subTest(items=items, index=index), self.assertRaises(SmokeError):
                    oracle.check_new(rocket, terrain, items)

    def test_device_missing_duplicate_coordinate_and_block_mismatch_are_rejected(self):
        mutations = (lambda t: t["block_entities"].pop(), lambda t: t["block_entities"].append(copy.deepcopy(t["block_entities"][0])),
                     lambda t: t["block_entities"][0].update(x=Int(70)),
                     lambda t: t["sections"][0]["block_states"]["palette"][1].update(Name="minecraft:stone"))
        for mutation in mutations:
            rocket, terrain = sample(True); mutation(terrain)
            with self.assertRaises(SmokeError):
                oracle.check_new(rocket, terrain, True)

    def test_exact_restart_comparison_includes_all_snapshot_authority(self):
        before = oracle.check_new(*sample(True), True)
        after = copy.deepcopy(before); after["rocket"]["RocketEntityData"]["snapshot"]["extra"] = Int(1)
        with self.assertRaises(SmokeError):
            oracle.compare_exact(before, after)
        after = copy.deepcopy(before); after["rocket"]["UUID"] = LongArray(after["rocket"]["UUID"])
        with self.assertRaises(SmokeError):
            oracle.compare_exact(before, after)
        self.assertNotEqual(oracle.typed_tree(before), oracle.typed_tree(after))

    def test_old_snapshot_stacks_and_types_are_required(self):
        old = dict(id=oracle.NS + ":rocket", UUID=oracle.uuid_nbt(80), Pos=[256.5, 101.0, 256.5],
                   RocketEntityData=dict(snapshot=dict(relative_blocks=[{}, {}, {}, dict(block_entity=dict(
                       adapter=oracle.NS + ":vanilla_container_v1", data=dict(Items=[
                           dict(Slot=Byte(0), id="minecraft:diamond", Count=Byte(17)),
                           dict(Slot=Byte(26), id="minecraft:iron_ingot", Count=Byte(64))])))])))
        baseline = oracle.check_old([old], {}, False, None)
        oracle.check_old([copy.deepcopy(old)], {}, False, baseline)
        bad = copy.deepcopy(old)
        bad["RocketEntityData"]["snapshot"]["relative_blocks"][3]["block_entity"]["data"]["Items"][0]["Count"] = Int(17)
        with self.assertRaises(SmokeError):
            oracle.check_old([bad], {}, False, baseline)
        with self.assertRaises(SmokeError):
            oracle.check_old([old, old], {}, False, None)

    def test_result_requires_all_live_checks_once(self):
        line = "ARCE_W136_RESULT leaf=item stage=check snapshot=" + "a" * 64 + " blocks=8 protected=10 g2=true tags=true restore=true"
        self.assertTrue(oracle.check_result([line], "item", "check")["g2"])
        for lines in ([], [line, line], [line.replace("g2=true", "g2=false")], [line.replace("protected=10", "protected=9")],
                      [line.replace("restore=true", "restore=false")], [line.replace("leaf=item", "leaf=fluid")]):
            with self.assertRaises(SmokeError):
                oracle.check_result(lines, "item", "check")
        with self.assertRaises(SmokeError):
            oracle.check_result([line], "item", "check", "b" * 64)

    def test_native_warning_manifest_is_exact_and_rejects_other_findings(self):
        lines = ["[Server thread/WARN] [io.github.sunthemoon.advancedrocketrycommunity/]: "
                 f"ARCE_ITEM_CARGO_UNSUPPORTED pos={64 + role * 5 + i}, 100, 79 status={status} keepable=true"
                 for role in range(2) for i, status in ((0, "FUTURE"), (1, "INVALID"), (2, "INVALID"), (4, "UNREGISTERED_ITEM"))]
        self.assertEqual(8, runner.check_log(lines, "item", "check")["expected_protected_warnings"])
        for wrong in (lines[:-1], lines + [lines[0]], lines + ["[Server thread/ERROR] [test/]: unrelated error"],
                      lines + ["Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER"]):
            with self.assertRaises(SmokeError):
                runner.check_log(wrong, "item", "check")

    def test_jar_requires_old_new_boundary_and_production_tag(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / "fixture.jar"
            def write(new, values):
                with zipfile.ZipFile(jar, "w") as output:
                    output.writestr(f"data/{oracle.NS}/tags/blocks/rocket_movable.json", json.dumps(dict(values=values)))
                    if new:
                        output.writestr("io/github/sunthemoon/advancedrocketrycommunity/rocket/forge/PressurizedTankRocketAdapter.class", b"fixture")
            old = ["minecraft:chest", "minecraft:barrel", oracle.NS + ":rocket_motor"]
            write(False, old); oracle.check_jar(jar, True)
            with self.assertRaises(SmokeError): oracle.check_jar(jar, False)
            write(True, old + [oracle.NS + ":pressurized_tank"]); oracle.check_jar(jar, False)
            with self.assertRaises(SmokeError): oracle.check_jar(jar, True)
            write(True, old)
            with self.assertRaises(SmokeError): oracle.check_jar(jar, False)

    def test_startup_hold_requires_one_ordered_clear(self):
        hold = "[Server thread/WARN] [io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/]: " \
               "ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED"
        clear = hold.replace("/WARN]", "/INFO]").replace("ARCE_FIRST_EVENT_HELD", "ARCE_FIRST_EVENT_HOLD_CLEARED")
        self.assertEqual(1, runner.check_log([hold, clear], "fluid", "old-seed")["cleared_startup_holds"])
        for lines in ([hold], [clear, hold], [hold, hold, clear], [hold.replace("SOURCE_UNCONFIRMED", "OTHER"), clear]):
            with self.assertRaises(SmokeError): runner.check_log(lines, "fluid", "old-seed")


if __name__ == "__main__":
    unittest.main()
