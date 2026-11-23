"""Linux-safe K14 plans, boundaries and read-only state projections."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts.campaign_prep import k14_scenes as k14


class PatternJar:
    def read(self, name):
        kind = Path(name).stem
        cells = [dict(x=2, y=1, z=0, matcher={"controller": True}),
                 dict(x=1, y=0, z=0, matcher={"tag": k14.NS + ":observatory_shell_parts"}),
                 dict(x=2, y=3, z=1, matcher={"block": "minecraft:glass"})]
        return json.dumps(dict(controller_anchor=dict(x=2, y=1, z=0), cells=cells,
                               id=k14.NS + ":" + kind)).encode()


class K14Tests(unittest.TestCase):
    def test_rotation_four_turns_and_nonzero_anchor(self):
        self.assertEqual(k14.rotate([3, 4, 5], 1), [-5, 4, 3])
        self.assertEqual(k14.rotate([3, 4, 5], 4), [3, 4, 5])
        for turn in range(4):
            model = k14.pattern(PatternJar(), "observatory", [50, 180, -30], turn)
            self.assertIn(dict(pos=[50, 180, -30], block=k14.NS + ":observatory[facing=" + k14.FACINGS[turn] + "]"), model["cells"])
            delta = k14.rotate([-1, -1, 0], turn)
            self.assertEqual(model["power_plugs"], [[50 + delta[0], 180 + delta[1], -30 + delta[2]]])
            self.assertEqual(len(model["glass"]), 1)

    def test_elevator_is_canonical_north_without_facing_property(self):
        layout = k14.core_layout(PatternJar())
        anchors = [d for d in layout["devices"] if d["kind"] == "elevator_anchor"]
        self.assertEqual(len(anchors), 1)
        self.assertEqual(anchors[0]["cells"][-1]["block"], k14.NS + ":elevator_anchor")

    def test_required_four_facing_inventory(self):
        layout = k14.core_layout(PatternJar())
        for kind in ("observatory", "astrobody_data_processor", "precision_assembler", "rolling_machine",
                     "precision_laser_etcher", "railgun", "black_hole_generator", "orbital_laser_drill"):
            self.assertEqual([d["facing"] for d in layout["devices"] if d["kind"] == kind], list(k14.FACINGS))

    def test_rocket_top_seat_and_section_precondition_are_physical(self):
        layout = k14.core_layout(PatternJar())
        self.assertEqual({r["mode"] for r in layout["rockets"]}, {"survival", "creative"})
        for rocket in layout["rockets"]:
            self.assertEqual(rocket["seats"], [[0, 9, 0]])
            self.assertEqual(rocket["origin"][1] % 16, 12)
            self.assertEqual(rocket["blocks"][-1]["pos"][1] - rocket["origin"][1], 9)
            self.assertEqual(rocket["blocks"][-1]["block"], k14.NS + ":rocket_seat")
            self.assertEqual(len(rocket["blocks"]), 10)

    def test_generated_commands_do_not_inject_nbt_fe_owners_or_load_tickets(self):
        commands = k14.commands_for(k14.core_layout(PatternJar()))
        for command in commands:
            k14.check_command(command)
            self.assertNotIn("data modify", command)
            self.assertNotIn("forceload", command)
        self.assertTrue(any("minecraft:hopper[facing=down]" in c for c in commands))
        self.assertTrue(any("container.0 with minecraft:coal 1" in c for c in commands))

    def test_power_supplies_do_not_replace_pattern_cells(self):
        layout = k14.core_layout(PatternJar())
        k14.commands_for(layout)
        for device in layout["devices"]:
            pattern_cells = {tuple(cell["pos"]) for cell in device["cells"]}
            for source in device.get("power_sources", []):
                self.assertNotIn(tuple(source["pos"]), pattern_cells)
                self.assertNotIn(tuple(source["hopper"]), pattern_cells)

    def test_power_geometry_on_the_real_tracked_research_patterns(self):
        class TrackedJar:
            def read(self, name):
                return (k14.common.ROOT / "src/generated/v1.8/resources" / name).read_bytes()
        for kind in ("observatory", "astrobody_data_processor"):
            for turn in range(4):
                device = k14.pattern(TrackedJar(), kind, [20, 181, 20], turn)
                k14.commands_for(dict(devices=[device], rockets=[]))
                occupied = {tuple(cell["pos"]) for cell in device["cells"]}
                for source in device["power_sources"]:
                    self.assertNotIn(tuple(source["pos"]), occupied)
                    self.assertNotIn(tuple(source["hopper"]), occupied)
                    occupied.update((tuple(source["pos"]), tuple(source["hopper"])))
                # A lower hopper must not siphon the other generator's coal.
                for source in device["power_sources"]:
                    above = [source["hopper"][0], source["hopper"][1] + 1, source["hopper"][2]]
                    self.assertNotIn(above, [s["pos"] for s in device["power_sources"]])
                self.assertEqual(len(device["power_sources"]), 2 if kind == "observatory" else 1)

    def test_research_controller_sites_require_real_player_placement(self):
        layout = k14.core_layout(PatternJar())
        commands = k14.commands_for(layout)
        for device in layout["devices"]:
            if device["kind"] in ("observatory", "astrobody_data_processor"):
                self.assertIn("setblock " + k14.rockets.pos(device["pos"]) + " minecraft:air", commands)
                self.assertNotIn("setblock " + k14.rockets.pos(device["pos"]) + " " + k14.NS + ":" + device["kind"]
                                 + "[facing=" + device["facing"] + "]", commands)

    def test_forbidden_commands_rejected_even_inside_execute(self):
        for command in ("execute in minecraft:overworld run forceload add 0 0", "execute run data modify block 0 0 0 energy set value 1",
                        "setblock 0 0 0 minecraft:chest{Items:[]}", "execute run summon minecraft:pig", "spawnpoint A",
                        "arce research-test setup", "arce satellite release-test setup", "data merge entity A {}"):
            with self.subTest(command=command), self.assertRaises(ValueError):
                k14.check_command(command)

    def test_exact_checklist_step_counts_and_no_observations_fabricated(self):
        rows = k14.client_steps(k14.core_layout(PatternJar()))
        self.assertEqual(len(rows), 39)
        for name, count in (("I07", 7), ("J18", 14), ("J19", 11), ("J20", 7)):
            self.assertEqual(sum(key.startswith(name + ".") for key in rows), count)
        for row in rows.values():
            self.assertEqual(row["status"], "NOT_COVERED")
            for key in ("precondition", "action", "expected_readback", "reason", "starting_copy"):
                self.assertTrue(row[key])
        self.assertEqual(rows["J18.05"]["starting_copy"], "prior-sleep/world")

    def test_client_steps_retain_critical_controls(self):
        rows = k14.client_steps({})
        self.assertIn("B1/R1", rows["J18.10"]["precondition"])
        self.assertIn("101", rows["J18.07"]["action"])
        self.assertIn("4000", rows["J19.08"]["expected_readback"])
        self.assertIn("120", rows["J19.08"]["expected_readback"])
        self.assertIn("normally; never edit", rows["J19.08"]["expected_readback"])
        self.assertIn("SYSTEM_CHANGED", rows["J19.10"]["expected_readback"])
        self.assertIn("unloaded", rows["J20.06"]["precondition"])
        self.assertIn("NOT_COVERED", rows["I07.07"]["expected_readback"])

    def test_lock_token_rejects_other_worker_and_prefix_collision(self):
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory)
            (lock / "owner.txt").write_text("W449 pid=12 abcdef 2026-11-23\n")
            k14.require_lock("W449 pid=12 abcdef", lock)
            for token in ("W434 pid=12 abcdef", "W449 pid=12 abcde", "W449 pid=12", ""):
                with self.assertRaises(ValueError):
                    k14.require_lock(token, lock)

    def test_saved_angle_raw_bits_and_field_absence(self):
        from scripts import v170_world_fixture as fixture
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory)
            (world / "playerdata").mkdir()
            (world / "playerdata/a.dat").touch()
            def read(path):
                return {"Data": {"WorldGenSettings": {"dimensions": {}}}} if path.name == "level.dat" else {
                    "SpawnAngle": -0.0, "SpawnForced": 1, "SpawnDimension": k14.MOON,
                    "ForgeData": {"PlayerPersisted": {"respawn_backup": {"marker": "retained"}}}}
            with patch.object(fixture, "read_nbt", side_effect=read):
                state = k14.disk_state(world)
            row = state["players"]["a.dat"]
            self.assertEqual(row["spawn"]["SpawnAngle_raw_float32"], "80000000")
            self.assertNotIn("SpawnX", row["spawn"])
            self.assertEqual(row["forge_persisted"]["respawn_backup"]["marker"], "retained")

    def test_no_player_inventory_does_not_become_sleep_absence_test(self):
        from scripts import v170_world_fixture as fixture
        with tempfile.TemporaryDirectory() as directory, patch.object(fixture, "read_nbt", return_value={"Data": {}}):
            state = k14.disk_state(Path(directory))
        self.assertEqual(state["players"], {})
        self.assertIn("not an absent-point bed test", state["player_coverage"])

    def test_parent_verification_rejects_added_unsealed_world_file(self):
        with tempfile.TemporaryDirectory() as directory:
            parent = Path(directory)
            (parent / "world").mkdir()
            (parent / "world/level.dat").write_bytes(b"a")
            (parent / "SHA256SUMS.txt").write_text(k14.common.digest(b"a") + "  world/level.dat\n")
            k14.verify_parent(parent, "world")
            (parent / "world/extra.dat").write_bytes(b"b")
            with self.assertRaises(ValueError):
                k14.verify_parent(parent, "world")

    def test_seal_requires_every_native_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            (output / "core-scenes").mkdir()
            with self.assertRaises(FileNotFoundError):
                k14.seal(output, "core-scenes")

    def test_comparison_retains_identity_fuel_origin_and_saved_nbt(self):
        report = {k: k for k in ("entity", "logical", "snapshot", "state", "fuel", "capacity", "blocks", "x", "y", "z")}
        state = dict(devices=[dict(kind="observatory", pos=[1, 2, 3], facing="north", nbt={"id": "native"})], rockets=[report])
        changed = copy.deepcopy(state)
        changed["rockets"][0]["y"] = 189
        self.assertNotEqual(k14.comparable(state), k14.comparable(changed))
        changed = copy.deepcopy(state)
        changed["devices"][0]["nbt"]["id"] = "other"
        self.assertNotEqual(k14.comparable(state), k14.comparable(changed))

    def test_each_kernel_formation_root_is_read_without_guessing(self):
        self.assertEqual(k14.kernel_formation({"arce_classic_machine": {"structure": {"formation_state": '"FORMED"'}}}), '"FORMED"')
        self.assertEqual(k14.kernel_formation({"arce_multiblock": {"formation_state": '"FORMED"'}}), '"FORMED"')
        self.assertEqual(k14.kernel_formation({"arce_multiblock": {"formation_state": '"UNFORMED"'}}), '"UNFORMED"')
        self.assertIsNone(k14.kernel_formation({"endgame": {"energy": "0"}}))

    def test_endgame_live_formation_requires_the_actual_field(self):
        self.assertTrue(k14.endgame_formed("railgun", ["last=OK structure=OK destination=-"]))
        self.assertFalse(k14.endgame_formed("railgun", ["last=OK structure=UNFORMED"]))
        self.assertTrue(k14.endgame_formed("elevator_anchor", ["placement=OK pair=NOT_BOUND"]))
        self.assertFalse(k14.endgame_formed("elevator_anchor", ["placement=WRONG_LEVEL last=OK"]))

    def test_frozen_recipe_is_taken_from_the_sealed_member(self):
        recipe = k14.frozen_recipe()
        self.assertEqual(recipe["status"], "NOT_COVERED")
        self.assertIn("indeterminate", recipe["V06"])
        self.assertIn("cancellation", recipe["B1_R1"])
        with zipfile.ZipFile(k14.common.ROOT / k14.FREEZE) as archive:
            self.assertEqual(k14.common.digest(archive.read(recipe["member"])), recipe["member_sha256"])


if __name__ == "__main__":
    unittest.main()
