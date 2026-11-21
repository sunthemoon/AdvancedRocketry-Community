"""K05 offline contracts. No native installation or Windows host is required."""
from collections import Counter
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts.campaign_prep import k05_machines as k


def archive_bytes():
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        base = k.common.ROOT / "src/generated/v1.8/resources/data" / k.NS
        for kind in k.KINDS:
            archive.writestr(f"data/{k.NS}/machine_patterns/{kind}.json", (base / "machine_patterns" / (kind + ".json")).read_bytes())
        for recipe in k.RECIPES:
            archive.writestr(f"data/{k.NS}/recipes/{recipe}.json", (base / "recipes" / (recipe + ".json")).read_bytes())
    return stream.getvalue()


class LayoutTest(unittest.TestCase):
    def setUp(self):
        self.jar = zipfile.ZipFile(io.BytesIO(archive_bytes()))
        self.addCleanup(self.jar.close)

    def test_full_layout_and_w70_coordinates(self):
        scene = k.layout(self.jar, "b144-idle")
        self.assertEqual(len(scene["machines"]), 144)
        self.assertEqual(len(scene["chunks"]), 36)
        self.assertEqual(Counter(m["kind"] for m in scene["machines"]), dict.fromkeys(k.KINDS, 16))
        for m in scene["machines"]:
            kind, copy = k.KINDS.index(m["kind"]), m["copy"]
            cell = kind * 4 + copy // 4
            self.assertEqual(m["chunk"], [1600 + cell % 6, 1600 + cell // 6])
            self.assertEqual(m["pos"], [m["chunk"][0]*16+3+copy%2*8, 308, m["chunk"][1]*16+2+copy//2%2*8])

    def test_round_robin_first_hundred(self):
        full = k.layout(self.jar, "b144-idle")["machines"]
        first = k.layout(self.jar, "a100-idle")["machines"]
        self.assertEqual(first, full[:100])
        self.assertEqual(Counter(m["kind"] for m in first), {kind: 12 if i == 0 else 11 for i, kind in enumerate(k.KINDS)})

    def test_half_active_is_exact_w70_selection(self):
        scene = k.layout(self.jar, "b144-half-active")
        active = [m for m in scene["machines"] if m["active_selection"]]
        self.assertEqual(len(active), 72)
        self.assertEqual(Counter(m["kind"] for m in active), dict.fromkeys(k.KINDS, 8))
        self.assertTrue(all(m["copy"] % 2 == 0 for m in active))
        self.assertEqual(len(k.variant_plan(scene)["selected"]), 72)
        self.assertEqual(scene["preparation_status"], "PLAN_ONLY_NOT_BUILT")

    def test_no_overlaps_and_exact_chunk_footprints(self):
        seen = set()
        for m in k.layout(self.jar, "b144-idle")["machines"]:
            for cell in m["cells"]:
                at = tuple(cell["pos"])
                self.assertNotIn(at, seen)
                seen.add(at)
                self.assertEqual([at[0]//16, at[2]//16], m["chunk"])

    def test_w70_optional_hatch_overrides(self):
        scene = k.layout(self.jar, "b144-idle")
        arc = next(m for m in scene["machines"] if m["kind"] == "arc_furnace")
        cells = {tuple(c["local"]): c for c in arc["cells"]}
        self.assertEqual(cells[1, 1, 0]["role"], "item_input")
        self.assertEqual(cells[3, 1, 0]["role"], "item_output")
        obs = next(m for m in scene["machines"] if m["kind"] == "observatory")
        self.assertEqual(next(c for c in obs["cells"] if c["local"] == [1, 0, 0])["role"], "energy_input")

    def test_controller_last_and_research_identity_granted_explicitly(self):
        scene = k.layout(self.jar, "a100-idle")
        commands = k.build_commands(scene)
        self.assertIn("fill -8 63 -8 8 63 8 minecraft:smooth_stone", commands)
        for m in scene["machines"]:
            controller = [s for s in commands if s.startswith(f"setblock {k.pos(m['pos'])} ")]
            self.assertEqual(len(controller), 1)
            indices = [commands.index(s) for c in m["cells"] for s in commands if s.startswith(f"setblock {k.pos(c['pos'])} ")]
            self.assertEqual(commands.index(controller[0]), max(indices))
            if m["owner"]:
                self.assertIn("owner_id:[I;", controller[0])
                self.assertIn(f"home_pos:{k.blockpos_long(m['pos'])}L", controller[0])
        self.assertFalse(any("GameTest" in c or "research-test setup" in c or "job set" in c for c in commands))

    def test_two_owner_parameters_not_fake_ownership_on_classic(self):
        owners = ("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222")
        scene = k.layout(self.jar, "two-owners", owners)
        research = [m for m in scene["machines"] if m["owner"]]
        self.assertEqual({m["owner"] for m in research}, set(owners))
        self.assertEqual(len(research), 32)
        self.assertTrue(all(m["owner"] is None for m in scene["machines"] if m["kind"] in k.KINDS[:7]))
        for invalid in ((owners[0], owners[0]), ("invalid", owners[1]), (owners[0],)):
            with self.assertRaises(ValueError):
                k.layout(self.jar, "two-owners", invalid)

    def test_damage_modes_are_independent_unexecuted_plans(self):
        for mode in ("structure", "controller", "explosion", "piston"):
            plan = k.variant_plan(k.layout(self.jar, "damage-" + mode))
            self.assertFalse(plan["executed"])
            self.assertEqual(len(plan["targets"]), 9)
            self.assertTrue(all(t["outcome"] == "NOT_OBSERVED" and t["drops"] is None for t in plan["targets"]))
            if mode == "piston":
                self.assertTrue(all(t["proposed_command"] is None for t in plan["targets"]))

    def test_two_unseen_observers_each_scene(self):
        for name in k.SCENES:
            scene = k.layout(self.jar, name)
            self.assertEqual(len(scene["observer_standpoints"]), 2)
            self.assertEqual([v["requested_gui_scale"] for v in scene["viewport_cells"]], [2, 3])
            self.assertIn("NOT_SEEN_ON_CLIENT", scene["views_status"])
            self.assertTrue(all(-90 <= view["pitch"] <= 90 for view in scene["observer_standpoints"]))

    def test_unknown_scene_refused(self):
        with self.assertRaises(ValueError):
            k.layout(self.jar, "absent")


class ReadbackTest(unittest.TestCase):
    def test_snbt_compound_order_and_numeric_types(self):
        a = k.parse_snbt('{b:[I;1,-2],a:{x:0L,s:"minecraft:air"}}')
        b = k.parse_snbt('{a:{s:"minecraft:air",x:0L},b:[I;1,-2]}')
        self.assertEqual(a, b)
        self.assertNotEqual(k.parse_snbt("0L"), k.parse_snbt("0"))
        self.assertNotEqual(k.parse_snbt("[I;1]"), k.parse_snbt("[1]"))

    def test_snbt_refuses_invalid_frames(self):
        for raw in ("", "{", "[", "{a:1,a:2}", "{a:1 b:2}", "[1 2]", "{}{}", "}", "["*34+"0"+"]"*34, "x"*262145):
            with self.subTest(raw=raw[:30]), self.assertRaises(ValueError):
                k.parse_snbt(raw)

    def test_snbt_strings_escaping_and_empty_lists(self):
        self.assertEqual(k.parse_snbt('{x:"escaped \\" quote",y:[],z:{}}')["y"], {"array_type": "list", "values": []})

    def test_native_idle_ledger_must_come_from_server(self):
        class Console:
            def nbt(self, at):
                return k.parse_snbt('{arce_classic_machine:{structure:{formation_state:"FORMED"},job:{},batch_ordinal:0L},arce_classic_resources:{banks:[]}}')
        machine = dict(pos=[0, 0, 0], id="lathe-00", cells=[], owner=None)
        self.assertEqual(k.idle_ledger(Console(), machine)["machine"], "lathe-00")
        class Invalid(Console):
            def nbt(self, at):
                value = super().nbt(at)
                value["arce_classic_machine"]["structure"]["formation_state"] = '"UNFORMED"'
                return value
        with self.assertRaisesRegex(ValueError, "Not formed/idle"):
            k.idle_ledger(Invalid(), machine)

    def test_nonidle_inputs_rejected(self):
        class Console:
            def nbt(self, at):
                return k.parse_snbt('{arce_classic_machine:{structure:{formation_state:"FORMED"},job:{},batch_ordinal:0L},arce_classic_resources:{banks:[{items:[{id:"minecraft:iron_ingot",Count:1b}]}]}}')
        with self.assertRaisesRegex(ValueError, "resources"):
            k.idle_ledger(Console(), dict(pos=[0, 0, 0], id="lathe-00", cells=[], owner=None))

    def test_no_native_substitution_for_unsupported_variants(self):
        for scene in set(k.SCENES) - set(k.RUNNABLE):
            with self.assertRaisesRegex(ValueError, "no native implementation"):
                k.run(Path("unused"), scene, "build", None, None, "")

    def test_native_requires_own_lock(self):
        with self.assertRaisesRegex(ValueError, "ownership"):
            k.run(Path("unused"), "a100-idle", "build", None, None, "")


class GenerationTest(unittest.TestCase):
    def test_declared_limits_distinguish_projection_from_admission(self):
        fields = k.declared_fields()
        classic = {f["field"]: f for f in fields["fields"]}
        self.assertEqual(classic["classic.progress"]["at_limit"], 72000)
        self.assertEqual(classic["classic.energy"]["one_past"], 640001)
        self.assertIsNone(classic["classic.title"]["at_limit"])
        self.assertIn("not a C2S setter", classic["classic.energy"]["mechanism"])
        d = fields["distance"]
        centre = [v + .5 for v in d["example_controller"]]
        squared = lambda p: sum((a-b)**2 for a, b in zip(p, centre))
        self.assertLessEqual(squared(d["inside"]), 64)
        self.assertGreater(squared(d["outside"]), 64)
        self.assertFalse(d["intent_sent"])

    def test_generation_deterministic_immutable_and_partial(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            jar = root / "test.jar"
            jar.write_bytes(archive_bytes())
            with patch.object(k.common, "JAR_HASH", k.common.file_hash(jar)):
                for name in ("one", "two"):
                    manifest = k.generate(root / name, jar)
                    self.assertEqual(manifest["status"], "PARTIAL_PREPARATION_NOT_CAMPAIGN_ACCEPTANCE")
                    self.assertEqual(len(manifest["recipes"]), 7)
                self.assertEqual(k.common.tree_hashes(root / "one"), k.common.tree_hashes(root / "two"))
                with self.assertRaises(FileExistsError):
                    k.generate(root / "one", jar)
            self.assertFalse(any((root / "one").rglob("world")))
            self.assertEqual(len(list((root / "one").glob("*/layout.json"))), 10)

    def test_wrong_candidate_fails_before_output_creation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            jar = root / "bad.jar"
            jar.write_bytes(b"bad")
            with self.assertRaisesRegex(ValueError, "Pinned C6"):
                k.generate(root / "out", jar)
            self.assertFalse((root / "out").exists())

    def test_signed_blockpos_packing(self):
        self.assertEqual(k.blockpos_long([0, 0, 0]), 0)
        self.assertEqual(k.blockpos_long([-1, -1, -1]), -1)


if __name__ == "__main__":
    unittest.main()
