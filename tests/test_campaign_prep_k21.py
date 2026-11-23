"""Offline checks for the copy-only K21 scene driver; no JVM or client."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.campaign_prep import k21_api_scenes as k21


class CampaignPrepK21Test(unittest.TestCase):
    def test_pins_match_campaign(self):
        self.assertEqual(k21.common.JAR_HASH, k21.chain.C6_HASH)
        self.assertEqual(len(k21.PACKS), 4)
        self.assertEqual(k21.OUTPUT.name, "K21")
        self.assertEqual(k21.FIXTURE.parent, k21.CANDIDATE)

    def test_layout_is_fresh_and_has_no_gate_verdict(self):
        for scene in k21.SCENES:
            layout = k21.plan(scene)
            self.assertIn("Fresh C6", layout["world_origin"])
            self.assertFalse(layout["required_gates_approved"])
            self.assertIn("zero", " ".join(layout["limitations"]))

    def test_component_layout_matches_v130(self):
        from scripts import run_v130_component_smoke as old
        self.assertEqual(k21.COMPONENTS, old.POSITIONS)
        commands = k21.build_commands(k21.plan("component"))
        self.assertIn("setblock 268 101 264 advancedrocketrycommunity:fuel_loader", commands)
        self.assertIn("arce rocket assemble 264 100 264", commands)
        self.assertNotIn("fluid_fuel_loader", "\n".join(commands))
        self.assertEqual(sum((3*127,41*9)), 750)
        self.assertIn("setblock 265 100 268 advancedrocketrycommunity:rocket_assembler", commands)

    def test_flight_layout_matches_v130(self):
        from scripts import run_v130_adapter_flight_smoke as old
        self.assertEqual(k21.FLIGHT, old.positions(dict(dimension=k21.OW, origin=list(old.ORIGIN))))
        commands = k21.build_commands(k21.plan("flight-recovery"))
        self.assertIn("item replace block 265 101 264 container.0 with minecraft:diamond 17", commands)
        self.assertIn("item replace block 265 101 264 container.1 with minecraft:iron_ingot 64", commands)

    def test_moon_layout_and_equipment_are_separate(self):
        from scripts import run_v130_atmosphere_boundary_smoke as old
        layout = k21.plan("moon")
        self.assertEqual(layout["positions"]["moon_vent"], list(old.POSITION))
        commands = k21.build_commands(layout)
        self.assertTrue(all(f"execute in {k21.MOON} run " in c for c in commands))
        self.assertIn("state_boundary[open=true]", " ".join(commands))
        self.assertNotEqual(layout["positions"]["suit_chest"], layout["positions"]["moon_vent"])

    def test_terminal_parameters_do_not_reassign_fixed_actor(self):
        layout = k21.plan("payload", (1024,120,1024), k21.OWNER)
        self.assertEqual(k21.build_commands(layout), ["arce_sat_fixture create 1024 120 1024"])
        with self.assertRaisesRegex(ValueError, "fixed owner"):
            k21.plan("payload", owner=k21.CONSOLE_OWNER)
        for position in ((4097,100,0),(0,-1,0),(0,251,0),(1.1,100,0)):
            with self.assertRaises(ValueError):
                k21.plan("payload", position)

    def test_generated_commands_never_mutate_nbt(self):
        for scene in k21.SCENES:
            for command in k21.build_commands(k21.plan(scene)):
                self.assertNotIn("data merge", command)
                self.assertNotIn("data modify", command)
                self.assertNotIn("{", command)
                self.assertNotIn("release-test", command)

    def test_console_rejects_native_nbt_mutations_before_send(self):
        console = k21.Console(None, [])
        for command in ("data merge block 1 2 3 {}", "execute in minecraft:overworld run data modify block 1 2 3 x set value 1",
                        "data remove entity @e x", "summon minecraft:armor_stand"):
            with self.assertRaisesRegex(ValueError, "NBT mutation forbidden"):
                console.all(command)
        self.assertEqual(console.commands, [])

    def test_client_connections_are_scripted_not_observed(self):
        steps = k21.client_steps()
        self.assertEqual(steps["status"], "SCRIPTED_ONLY_NOT_EXECUTED")
        self.assertEqual([r["case"] for r in steps["connections"]], ["installed","omitted"])
        self.assertEqual(steps["controls"]["provider"], "K18")
        self.assertEqual(steps["equipment"]["capacity"], 2000)
        self.assertEqual(steps["equipment"]["initial_oxygen"], 0)
        self.assertEqual(steps["equipment"]["interval_ticks"], 20)
        text = json.dumps(steps)
        for historical in ("999", "998", "1,997"):
            self.assertNotIn(historical, text)

    def test_native_readback_requires_exactly_one_value(self):
        self.assertEqual(k21.native_data(["[Server thread/INFO]: X has the following entity data: {x: 1}"]), "{x: 1}")
        for lines in ([], ["data: 1"], ["a data: 1", "b data: 2"]):
            with self.assertRaises(ValueError):
                k21.native_data(lines)

    def test_comparison_ignores_only_runtime_diagnostics(self):
        before = dict(rocket="full snapshot with opaque payload", managed=["generation 1"], packs=["pack"])
        self.assertEqual(k21.compare(before, dict(before, managed=["generation 2"]), "flight-recovery"), {"rocket":True})
        with self.assertRaisesRegex(ValueError, "differ"):
            k21.compare(before, dict(before, rocket="lost payload"), "flight-recovery")
        moon = dict(vent="root", equipment="stacks", atmosphere_sealing=True, atmosphere_permeable=False)
        with self.assertRaises(ValueError):
            k21.compare(moon, dict(moon, atmosphere_sealing=False), "moon")

    def test_content_witnesses_reject_wrong_numbers_or_lost_payload(self):
        component = dict(rocket='{fuel_capacity: 1500L, mass: 254L, thrust: 2400L, block_count: 5, '
                         'block_entity_count: 0, state: "ASSEMBLED", fuel: {amount: 0L, capacity: 1500L}}',
                         loader='{buffered_units: 0L}')
        k21.validate_readbacks(component, "component")
        with self.assertRaisesRegex(ValueError, "mass"):
            k21.validate_readbacks(dict(component, rocket=component["rocket"].replace("254L","253L")), "component")
        with self.assertRaisesRegex(ValueError, "unclaimed"):
            k21.validate_readbacks(dict(component, loader="{owner_id: [I; 0,0,0,5], buffered_units: 0L}"), "component")
        with self.assertRaisesRegex(ValueError, "payload"):
            k21.validate_readbacks(dict(terminal="{inventory: {}}"), "payload")
        with self.assertRaises(ValueError):
            k21.validate_readbacks(dict(vent="{oxygen_units: 0, energy: 0}", atmosphere_sealing=False), "moon")

    def test_copy_inventory_and_immutable_source(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "source"
            source.mkdir()
            (source / "level.dat").write_bytes(b"opaque native file")
            before = k21.fixture.file_inventory(source)
            self.assertEqual(k21.checked_copy(source, root / "copy"), before)
            self.assertEqual(k21.fixture.file_inventory(source), before)
            with self.assertRaises(ValueError):
                k21.checked_copy(source, root / "copy")

    def test_no_run_without_owned_lock_or_native_process(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "owner.txt").write_text("W999 pid=1 token")
            args = k21.parser().parse_args(["--scene","payload","--mode","build","--lock",str(root),
                                           "--lock-owner","W445 pid=1 mine","--template",str(root),"--java","java"])
            with patch.object(k21.server, "CapturedProcess") as process:
                with self.assertRaisesRegex(ValueError, "heavy-JVM lock"):
                    k21.run(args)
                process.assert_not_called()

    def test_unpinned_artifacts_refused(self):
        with tempfile.TemporaryDirectory() as temp:
            jar = Path(temp) / "other.jar"
            jar.write_bytes(b"not C6")
            with self.assertRaisesRegex(ValueError, "Unpinned artifact"):
                k21.identity(jar, jar, jar, Path(temp))

    def test_invalid_mode_is_rejected_before_output(self):
        args = k21.parser().parse_args(["--scene","moon","--mode","remove","--lock-owner","x",
                                       "--template","x","--java","java"])
        with self.assertRaisesRegex(ValueError, "two removal"):
            k21.run(args)
        args.mode = "changed-qualify"
        with self.assertRaisesRegex(ValueError, "payload-only"):
            k21.run(args)

    def test_qualification_names_cannot_replace_sources_or_escape(self):
        args = k21.parser().parse_args(["--scene","payload","--mode","changed-qualify","--lock-owner","x",
                                       "--template","x","--java","java"])
        for label in ("../escape", "source-world", "qualify", "qualify-"+"a"*41, "qualify-x/y"):
            args.qualification_name = label
            with self.assertRaisesRegex(ValueError, "qualification name"):
                k21.run(args)

    def test_r638_revision_preserves_vent_and_separates_supplies(self):
        layout = k21.plan("moon", moon_revision=2)
        self.assertEqual(layout["positions"]["moon_vent"], [264,220,264])
        self.assertEqual(layout["positions"]["moon_generator"], [263,220,264])
        self.assertNotEqual(layout["positions"]["moon_supply"], layout["positions"]["suit_chest"])
        commands = k21.build_commands(layout)
        self.assertEqual(len(commands), 9)
        self.assertIn("setblock 264 222 264 minecraft:air", "\n".join(commands))
        self.assertIn("fill 263 223 263 265 223 265 minecraft:iron_block", "\n".join(commands))
        self.assertTrue(all(c.startswith(f"execute in {k21.MOON} run ") for c in commands))
        self.assertTrue(any("combustion_generator" in c for c in commands))
        self.assertFalse(any("setblock 264 220 264" in c for c in commands))
        for item, count in (("coal",16),("oxygen_canister",3),("glass",4)):
            self.assertTrue(any(c.endswith(f":{item} {count}") for c in commands))
        for command in commands:
            self.assertNotIn("data ", command)
            self.assertNotIn("{", command)

    def test_r638_revision_bounds_are_checked_before_lock_or_output(self):
        for scene, revision in (("component",2),("moon",3),("moon",0)):
            with self.assertRaisesRegex(ValueError, "Moon revision"):
                k21.plan(scene, moon_revision=revision)
        for scene, mode in (("payload","build"),("component","qualify"),("moon","changed")):
            args = k21.parser().parse_args(["--scene",scene,"--mode",mode,"--moon-revision","2",
                "--lock-owner","x","--template","x","--java","java"])
            with self.assertRaisesRegex(ValueError, "Moon revision|payload-only"):
                k21.run(args)

    @staticmethod
    def revision_readbacks():
        return dict(moon_revision=2, vent='{schema_version: 2, scrubbers: {generation: "uuid", phase: 0, banks: []}, oxygen_units: 0, energy: 0}',
            equipment="unchanged suit stacks", atmosphere_sealing=True, atmosphere_permeable=False,
            generator="{schema: 1, duration: 0, fuel: {}, remaining: 0, energy: 0}",
            room_supply='[{Slot: 0b, id: "minecraft:coal", Count: 16b}, '
                        '{Slot: 1b, id: "advancedrocketrycommunity:oxygen_canister", Count: 3b}, '
                        '{Slot: 2b, id: "minecraft:glass", Count: 4b}]',
            geometry=dict(feet=True, head=True, roof=True, breach=True, boundary=True))

    def test_r638_witnesses_refuse_missing_supplies_or_mixed_roots(self):
        value = self.revision_readbacks()
        k21.validate_readbacks(value, "moon")
        for key, bad in (("vent", value["vent"].replace("schema_version: 2", "schema_version: 1")),
                         ("vent", value["vent"].replace("banks: []", "banks: [{}]")),
                         ("generator", value["generator"].replace("energy: 0", "energy: 1")),
                         ("room_supply", value["room_supply"].replace("16b", "15b")),
                         ("geometry", dict(value["geometry"], head=False)), ("atmosphere_permeable", True)):
            with self.subTest(key=key, bad=bad), self.assertRaises(ValueError):
                k21.validate_readbacks(dict(value, **{key:bad}), "moon")

    def test_r638_restart_comparison_covers_every_added_witness(self):
        value = self.revision_readbacks()
        self.assertEqual(len(k21.compare(value, value.copy(), "moon")), 8)
        for key in ("generator","room_supply","geometry","vent","equipment"):
            with self.subTest(key=key), self.assertRaises(ValueError):
                k21.compare(value, dict(value, **{key:"changed"}), "moon")

    def test_r638_readbacks_use_moved_boundary_and_capture_headroom(self):
        class Native:
            def __init__(self):
                self.commands = []
            def all(self, command):
                self.commands.append(command)
                if command == "arce beta data-report":
                    return ["operational=true"]
                if "data get" in command:
                    return ["block data: {native: 1}"]
                if " run say " in command:
                    return ["[Server] "+command.split(" run say ",1)[1]]
                return []
        native = Native()
        result = k21.readbacks(native, k21.plan("moon", moon_revision=2))
        self.assertEqual(result["generator"], "{native: 1}")
        self.assertTrue(all(result["geometry"].values()))
        self.assertIn(f"execute in {k21.MOON} if block 265 222 264 #{k21.NS}:atmosphere_sealing run say K21_TAG_atmosphere_sealing", native.commands)

    def test_r638_client_steps_are_actions_with_expected_readbacks_not_results(self):
        steps = k21.client_steps()
        self.assertEqual(steps["atmosphere"]["source"], "moon-r2/source-world")
        self.assertEqual(steps["atmosphere"]["status"], "SCRIPTED_ONLY_NOT_EXECUTED")
        for step in steps["atmosphere"]["steps"] + steps["component"]["claim_and_half_fill"]:
            self.assertTrue(step["action"])
            self.assertTrue(step["expected_readback"])
        room = json.dumps(steps["atmosphere"])
        for witness in ("BREATHABLE", "VACUUM", "scrubbers", "health/HUD", "break", "repairs", "reload"):
            self.assertIn(witness, room)
        loader = json.dumps(steps["component"]["claim_and_half_fill"])
        for witness in ("empty-handed", "slot_role=0", "127,254,381", "n=1..41", "fuel750", "Survival", "owner_id"):
            self.assertIn(witness, loader)

    def test_scripted_initial_target_is_in_range_but_not_connected_to_scene_blocks(self):
        component = k21.client_steps()["component"]
        x,y,z = component["initial_origin"]
        loader = k21.plan("component")["positions"]["component_loader"]
        self.assertLessEqual(sum((a-b)**2 for a,b in zip((x,y,z),loader)), 36)
        initial = [(a+x-264,b+y-101,c+z-264) for a,b,c in k21.COMPONENTS]
        existing = [(270,101,264), tuple(loader), (265,100,268)]
        existing += [(a+1,b,c+4) for a,b,c in k21.COMPONENTS]
        self.assertTrue(all(sum(abs(a-b) for a,b in zip(p,q)) > 1 for p in initial for q in existing))
        self.assertIn(f"at {x} {y} {z}", component["steps"][0])
        self.assertIn(f"x={x},y={y},z={z}", component["read_only_commands"][2])


if __name__ == "__main__":
    unittest.main()
