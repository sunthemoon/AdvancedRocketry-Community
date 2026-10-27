import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import fuel_loader_s1_oracle as oracle
import run_v180_fuel_loader_s1 as runner
from cargo_s1_oracle import Byte, Int, Long, IntArray, LongArray
from run_dedicated_server_smoke import SmokeError


def terrain_at(x_chunk, z_chunk, entities, ids):
    packed = [0] * 256
    for be in entities:
        cell = ((be["y"] & 15) << 8) | ((be["z"] & 15) << 4) | (be["x"] & 15)
        packed[cell // 16] |= (ids.index(be["id"]) + 1) << (cell % 16 * 4)
    return dict(xPos=Int(x_chunk), zPos=Int(z_chunk), block_entities=entities,
                sections=[dict(Y=Byte(6), block_states=dict(palette=[dict(Name="minecraft:air"), *[dict(Name=i) for i in ids]],
                    data=LongArray(v if v < 1 << 63 else v - (1 << 64) for v in packed)))])


def new_sample():
    ns = oracle.NS
    rocket = dict(id=ns + ":rocket", UUID=oracle.uuid_nbt(50), Pos=[72.5, 100.0, 72.5], RocketEntityData=dict(
        schema_version=Int(3), owner_id=oracle.uuid_nbt(1), assembly_transaction_id=oracle.uuid_nbt(51),
        resource_escrow=oracle.neutral(), snapshot=dict(
            relative_blocks=[dict(palette=Int(i)) for i in range(4)],
            block_palette=[dict(id=ns + ":" + value) for value in ("rocket_motor", "rocket_seat", "guidance_computer", "rocket_fuel_tank")])) )
    entities = []
    for x, z, identity, variant, binding in [(72, 76, 10, "valid", oracle.native.binding(rocket)),
            *[(64 + i, 79, 20 + i, variant, None) for i, variant in enumerate(oracle.VARIANTS)]]:
        be = dict(id=ns + ":fluid_fuel_loader", x=Int(x), y=Int(100), z=Int(z))
        be[oracle.KEY] = oracle.expected_root(identity, binding, variant); entities.append(be)
    return rocket, terrain_at(4, 4, entities, [ns + ":fluid_fuel_loader"])


def old_sample():
    ns = oracle.NS
    rocket = dict(id=ns + ":rocket", UUID=oracle.uuid_nbt(50), Pos=[256.5, 101.0, 256.5], RocketEntityData=dict(
        schema_version=Int(2), snapshot=dict(relative_blocks=[{}, {}, {}, dict(block_entity=dict(
            adapter=ns + ":vanilla_container_v1", data=dict(Items=[dict(Slot=Byte(0), id="minecraft:diamond", Count=Byte(17)),
                                                               dict(Slot=Byte(26), id="minecraft:iron_ingot", Count=Byte(64))])))])))
    be = dict(id=ns + ":fuel_loader", x=Int(260), y=Int(101), z=Int(256)); be[oracle.OLD_KEY] = oracle.old_root()
    return rocket, terrain_at(16, 16, [be], [ns + ":fuel_loader"])


class FuelLoaderS1OracleTest(unittest.TestCase):
    def test_valid_and_all_protected_roots_survive_two_exact_restarts(self):
        rocket, terrain = new_sample(); before = oracle.check_new(rocket, terrain)
        for _ in range(2):
            oracle.native.compare_exact(before, oracle.check_new(copy.deepcopy(rocket), copy.deepcopy(terrain)))

    def test_every_root_is_required_exactly_including_work_limit_and_types(self):
        rocket, terrain = new_sample()
        for i in range(6):
            root = terrain["block_entities"][i][oracle.KEY]; root["unexpected"] = Int(1)
            with self.subTest(i=i), self.assertRaises(SmokeError): oracle.check_new(rocket, terrain)
            del root["unexpected"]
        terrain["block_entities"][0][oracle.KEY]["tank"]["Tag"]["b"] = Int(7)
        with self.assertRaises(SmokeError): oracle.check_new(rocket, terrain)

    def test_work_budget_raw_array_mutation_is_not_hidden_by_evidence_compaction(self):
        rocket, terrain = new_sample()
        terrain["block_entities"][4][oracle.KEY]["opaque"][-1] = 1
        with self.assertRaises(SmokeError): oracle.check_new(rocket, terrain)

    def test_missing_duplicate_coordinate_and_block_mismatch_fail(self):
        for mutate in (lambda t: t["block_entities"].pop(),
                       lambda t: t["block_entities"].append(copy.deepcopy(t["block_entities"][0])),
                       lambda t: t["block_entities"][0].update(x=Int(71)),
                       lambda t: t["sections"][0]["block_states"]["palette"][1].update(Name="minecraft:stone")):
            rocket, terrain = new_sample(); mutate(terrain)
            with self.assertRaises(SmokeError): oracle.check_new(rocket, terrain)

    def test_schema_two_upgrade_adds_only_neutral_escrow_then_downgrade_retains_three(self):
        rocket, terrain = old_sample(); before = oracle.check_old(rocket, terrain, 2)
        rocket["RocketEntityData"].update(schema_version=Int(3), resource_escrow=oracle.neutral())
        upgraded = oracle.check_old(rocket, terrain, 3, before)
        for _ in range(4): oracle.check_old(copy.deepcopy(rocket), copy.deepcopy(terrain), 3, upgraded)
        rocket["RocketEntityData"]["resource_escrow"]["resource_epoch"] = Long(1)
        with self.assertRaises(SmokeError): oracle.check_old(rocket, terrain, 3, before)

    def test_old_loader_output_root_and_snapshot_are_not_only_counted(self):
        for change in (lambda r, t: t["block_entities"][0][oracle.OLD_KEY]["item"].update(Count=Int(1)),
                       lambda r, t: t["block_entities"][0][oracle.OLD_KEY].update(buffered_units=Long(1)),
                       lambda r, t: r["RocketEntityData"]["snapshot"]["relative_blocks"][3]["block_entity"]["data"]["Items"][0].update(Count=Byte(18))):
            rocket, terrain = old_sample(); change(rocket, terrain)
            with self.assertRaises(SmokeError): oracle.check_old(rocket, terrain, 2)

    def test_compact_evidence_keeps_array_type_length_and_digest(self):
        array = oracle.ByteArray([0] * 2_097_153); compact = oracle.evidence_tree(array)
        self.assertEqual("ByteArray", compact["type"]); self.assertEqual(len(array), compact["length"])
        self.assertLess(len(json.dumps(compact)), 200)
        array[-1] = 1; self.assertNotEqual(compact["sha256"], oracle.evidence_tree(array)["sha256"])

    def test_live_result_requires_every_claim_once(self):
        line = "ARCE_W176_RESULT stage=check snapshot=" + "a" * 64 + " schema=3 protected=5 g2=true old_loader=false"
        self.assertTrue(oracle.check_result([line], "check")["g2"])
        for lines in ([], [line, line], [line.replace("g2=true", "g2=false")], [line.replace("protected=5", "protected=4")]):
            with self.assertRaises(SmokeError): oracle.check_result(lines, "check")
        with self.assertRaises(SmokeError): oracle.check_result([line], "check", "b" * 64)

    def test_exact_warn_manifest_rejects_missing_repeated_foreign_and_error_lines(self):
        lines = ["[Server thread/WARN] [io.github.sunthemoon.advancedrocketrycommunity/]: "
                 f"ARCE_FLUID_FUEL_LOADER_UNSUPPORTED pos={64 + i}, 100, 79 status={status}"
                 for i, status in enumerate(("FUTURE", "INVALID", "INVALID", "INVALID", "UNREGISTERED_FLUID"))]
        self.assertEqual(5, runner.check_log(lines, "check")["expected_warnings"])
        for wrong in (lines[:-1], lines + [lines[0]], lines + ["[Server thread/ERROR] [test/]: error"],
                      lines + ["Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER"]):
            with self.assertRaises(SmokeError): runner.check_log(wrong, "check")
        with self.assertRaises(SmokeError): runner.check_log(lines, "old-check")

    def test_old_and_new_jar_boundaries_are_required(self):
        prefix = "io/github/sunthemoon/advancedrocketrycommunity/rocket/"
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "fixture.jar"
            for old in (True, False):
                with zipfile.ZipFile(path, "w") as jar:
                    jar.writestr(prefix + "fuel/FuelLoaderBlockEntity.class", b"fixture")
                    if not old:
                        jar.writestr(prefix + "fuel/loader/FluidFuelLoaderBlockEntity.class", b"fixture")
                        jar.writestr(prefix + "command/FuelLoaderS1ReleaseProbe.class", b"fixture")
                oracle.check_jar(path, old)
                with self.assertRaises(SmokeError): oracle.check_jar(path, not old)

    def test_attempt_counter_counts_failures_and_refuses_start_61(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "starts.json"
            for i in range(60): self.assertEqual(i + 1, runner.count_start(path, dict(stage="attempt")))
            with self.assertRaises(SmokeError): runner.count_start(path, {})
            self.assertEqual(60, len(json.loads(path.read_text())))
        self.assertEqual(9, len(runner.STAGES))
        self.assertEqual(3, runner.STAGES.count("downgrade"))
        self.assertEqual(2, runner.STAGES.count("check"))
        self.assertEqual(2, runner.STAGES.count("old-check"))


if __name__ == "__main__":
    unittest.main()
