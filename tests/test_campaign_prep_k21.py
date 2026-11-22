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


if __name__ == "__main__":
    unittest.main()
