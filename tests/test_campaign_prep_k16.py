"""K16's offline contracts. Native/client acceptance is deliberately separate."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

from scripts.campaign_client.protocol import validate_request
from scripts.campaign_prep import k16_core as k16


class ViewportTest(unittest.TestCase):
    def test_electrolyzer_charge_uses_current_menu_not_historical_slot(self):
        self.assertEqual(k16.gui_point((1280, 720, 2), (176, 176), (28, 43))["pixel"], [520, 270])
        source = (k16.ROOT / k16.JAVA_ROOT / "machine/electrolyzer/ElectrolyzerMenu.java").read_text()
        self.assertIn("CHARGE_X = 20", source)
        self.assertIn("CHARGE_Y = 35", source)
        self.assertEqual(k16.LAYOUTS["electrolyzer"][2]["charge"], (28, 43))

    def test_known_flight_moon_button_all_cells(self):
        expected = ([750, 342], [804, 333], [1180, 504])
        for viewport, pixel in zip(k16.VIEWPORTS, expected):
            self.assertEqual(k16.gui_point(viewport, (248, 216), (179, 99))["pixel"], pixel)

    def test_clamp_scale_four_in_small_window(self):
        result = k16.gui_point((1280, 720, 4), (176, 222), (66, 83))
        self.assertEqual(result["effective_scale"], 3)

    def test_ceil_not_floor_gui_width(self):
        self.assertEqual(k16.gui_point((1280, 720, 3), (176, 222), (0, 0))["origin"], [125, 9])

    def test_java_truncation_for_oversize_panel(self):
        self.assertEqual(k16.gui_point((1280, 720, 3), (432, 243), (0, 0))["origin"], [-2, -1])

    def test_invalid_viewport(self):
        for viewport in ((0, 720, 2), (1280, -720, 2), (True, 720, 2), (1280, 720, 1.5)):
            with self.assertRaises(ValueError):
                k16.gui_point(viewport, (176, 222), (0, 0))

    def test_every_control_visible_without_clamping(self):
        controls = k16.control_manifest()
        for screen in controls["screens"].values():
            for cell in screen["viewports"].values():
                self.assertTrue(all(control["inside_viewport"] for control in cell.values()))
        self.assertEqual(len(controls["screens"]), len(k16.LAYOUTS))

    def test_known_source_layouts(self):
        checks = {
            "client/RailgunScreen.java": ("imageWidth = 176", "imageHeight = 222", "44, 76, 44"),
            "client/RocketFlightScreen.java": ("imageWidth = 248", "imageHeight = 216", "176, 204, 24"),
            "client/SatelliteTerminalScreen.java": ("imageWidth = 352", "imageHeight = 234", "52, 105, 46"),
            "client/SatelliteBuilderScreen.java": ("imageWidth = 224", "imageHeight = 202", "118, topPos + 64, 70, 16"),
            "client/equipment/SuitWorkstationScreen.java": ("PANEL_HEIGHT = 202", "56, topPos + 28, 64, 20"),
            "station/controller/WarpControllerMenu.java": ("KEY + \"confirm\", 62, 38, 52",),
        }
        for path, anchors in checks.items():
            text = (k16.ROOT / k16.JAVA_ROOT / path).read_text(encoding="utf-8")
            for anchor in anchors:
                with self.subTest(path=path, anchor=anchor):
                    self.assertIn(anchor, text)


class BatchTest(unittest.TestCase):
    def all_batches(self):
        requests, index = k16.batches()
        more, added = k16.recheck_batches(k16.recheck_manifest())
        return requests | more, index + added

    def test_wire_schema_and_request_sizes(self):
        requests, index = self.all_batches()
        self.assertEqual(set(requests), {entry["file"] for entry in index})
        # W443 (revision 2): core-rocket-fuel and core-suit add two requests per viewport to revision 1's 108.
        self.assertEqual(len(requests), 114)
        for raw in requests.values():
            self.assertLessEqual(len(raw), 256 * 1024)
            request = json.loads(raw)
            validate_request(request, "launch", [k16.PLAYER])
            validate_request(request, "attach", [k16.PLAYER])
            self.assertTrue(all(op["op"] not in ("stop", "close_client") for op in request["ops"]))

    def test_all_21_rechecks_in_each_viewport(self):
        rows = k16.recheck_manifest()
        requests, index = k16.recheck_batches(rows)
        self.assertEqual(len(rows), 21)
        self.assertEqual(len(requests), 63)
        for viewport in k16.VIEWPORTS:
            suffix = k16.viewport_id(viewport) + ".json"
            selected = [entry for entry in index if entry["file"].endswith(suffix)]
            self.assertEqual({entry["finding"] for entry in selected}, {row["finding"] for row in rows})
            self.assertTrue(all(entry["row"] == "J21" and entry["step"] for entry in selected))

    def test_every_step_named_and_not_executed(self):
        _, index = self.all_batches()
        for entry in index:
            self.assertTrue(entry["row"] and entry["step"] and entry["prerequisites"])
            self.assertFalse(entry["executed"])
        # A checkpoint is explicitly not a fully automated recheck.
        checkpoints = [entry for entry in index if entry.get("automation") == "checkpoint-only"]
        self.assertTrue(checkpoints)
        self.assertTrue(all("NOT an automated implementation" in entry["prerequisites"] for entry in checkpoints))

    def test_no_historical_regex_waits(self):
        requests, _ = self.all_batches()
        for raw in requests.values():
            for op in json.loads(raw)["ops"]:
                value = op.get("pattern", op.get("wait", ""))
                self.assertNotIn("|", value)
                self.assertNotIn(".*", value)

    def test_pause_is_integrated_only_and_has_no_chat_with_menu_open(self):
        requests, index = k16.batches()
        pause = [entry for entry in index if entry["row"] == "E07"]
        self.assertEqual(len(pause), 3)
        for entry in pause:
            self.assertEqual(entry["mode"], "singleplayer")
            self.assertIn("not opened to LAN", entry["prerequisites"])
            ops = json.loads(requests[entry["file"]])["ops"]
            first_escape = next(i for i, op in enumerate(ops) if op.get("key") == "ESC")
            self.assertFalse(any(op["op"] == "server" for op in ops[first_escape:]))

    def test_generation_is_pure_and_deterministic(self):
        self.assertEqual(self.all_batches(), self.all_batches())


class RouteGateTest(unittest.TestCase):
    """Revision 2 (W443): what the dedicated rehearsal of 2026-11-22 showed the core route lacked."""

    def core(self, name, viewport=(1280, 720, 3)):
        requests, _ = k16.batches()
        return json.loads(requests[f"batches/{name}-{k16.viewport_id(viewport)}.json"])["ops"]

    def test_core_route_lists_every_dedicated_core_request_once(self):
        _, index = k16.batches()
        for viewport in k16.VIEWPORTS:
            suffix = "-" + k16.viewport_id(viewport) + ".json"
            names = [entry["file"][len("batches/"):-len(suffix)] for entry in index
                     if entry["file"].endswith(suffix) and entry["mode"] == "dedicated"]
            self.assertEqual(sorted(names), sorted(k16.CORE_ROUTE))
        self.assertEqual(len(k16.CORE_ROUTE), 16)
        order = k16.CORE_ROUTE.index
        self.assertLess(order("core-rocket-assemble"), order("core-rocket-fuel"))
        self.assertLess(order("core-suit"), order("core-room"))
        self.assertLess(order("core-rocket-fuel"), order("core-flight-plan"))
        self.assertLess(order("core-flight-plan"), order("core-flight-observe"))

    def test_every_core_request_starts_from_a_checked_state_and_never_sends_a_bare_escape(self):
        for viewport in k16.VIEWPORTS:
            for name in k16.CORE_ROUTE:
                ops = self.core(name, viewport)
                with self.subTest(name=name, viewport=viewport):
                    self.assertEqual(ops[:3], k16.prelude())
                    self.assertEqual(ops[1], {"op": "close_screen"})
                    self.assertEqual(ops[2]["op"], "require")
                    self.assertFalse(any(op.get("key") == "ESC" for op in ops))

    def test_gui_clicks_follow_an_open_screen_gate(self):
        for name in k16.CORE_ROUTE:
            ops, gated = self.core(name), False
            for op in ops:
                if op["op"] == "require_screen" and op["open"]:
                    gated = True
                elif op["op"] == "close_screen":
                    gated = False
                elif op["op"] == "move" or (op["op"] == "click" and not op.get("right") and "hold" not in op):
                    self.assertTrue(gated, f"{name}: a control click without an open-screen gate")

    def test_room_is_supplied_by_use_actions_and_never_by_vent_nbt(self):
        ops = self.core("core-room")
        commands = [op.get("command", "") for op in ops]
        self.assertFalse(any("arce_oxygen_vent" in command and "merge" in command for command in commands))
        self.assertEqual(ops[3], k16.suit_ready())
        gates = [op["expect"] for op in ops if op["op"] == "require" and op["command"] == k16.ROOM_STATUS]
        self.assertEqual(gates, ["Atmosphere=BREATHABLE vents=1 providers=1", "Atmosphere=VACUUM",
                                 "Atmosphere=BREATHABLE vents=1 providers=1"])
        self.assertFalse(any("setblock" in command for command in commands))
        hold = [op for op in ops if op["op"] == "click" and "hold" in op]
        self.assertEqual(len(hold), 1)
        self.assertFalse(hold[0].get("right", False))
        # Each screenshot comes after the gate for the state it shows.
        kinds = [(op["op"], op.get("expect", op.get("name"))) for op in ops if op["op"] in ("require", "screenshot")]
        self.assertLess(kinds.index(("require", "Atmosphere=VACUUM")), kinds.index(("screenshot", "a02-room-open-1280x720-s3")))

    def test_world_places_the_vent_empty_and_a_glass_break_cell(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fixture.jar"
            PatternAndOutputTest.fixture_jar(self, path)
            with zipfile.ZipFile(path) as jar:
                commands, devices, _ = k16.core_world(jar)
        self.assertFalse(any("arce_oxygen_vent" in command for command in commands))
        self.assertIn(f"execute in {k16.MOON} run setblock 34 152 36 minecraft:glass", commands)
        self.assertIn(f"execute in {k16.MOON} run setblock 34 150 34 {k16.NS}:oxygen_vent", commands)
        self.assertTrue(any(device["name"] == "oxygen_vent" for device in devices))

    def test_no_request_writes_a_stale_vent_schema(self):
        requests, _ = k16.batches()
        more, _ = k16.recheck_batches(k16.recheck_manifest())
        for name, raw in (requests | more).items():
            for op in json.loads(raw)["ops"]:
                if "arce_oxygen_vent" in op.get("command", "") and "merge" in op["command"]:
                    self.assertNotIn("schema_version", op["command"], name)

    def test_vent_root_check(self):
        good = ['34, 150, 34 has the following block data: {x: 34, arce_oxygen_vent: {schema_version: 2, '
                'scrubbers: {generation: "g", phase: 0, banks: []}, oxygen_canisters: 0}}']
        self.assertTrue(k16.vent_root_current("oxygen_vent", good))
        self.assertFalse(k16.vent_root_current("oxygen_vent", [good[0].replace("schema_version: 2", "schema_version: 1")]))
        self.assertFalse(k16.vent_root_current("oxygen_vent", ["34, 150, 34 has the following block data: {x: 34}"]))
        self.assertTrue(k16.vent_root_current("pump", []))

    def test_player_stands_on_the_floor_to_aim_at_a_raised_controller(self):
        self.assertEqual(k16.look((42, 151, 20))["command"],
                         "execute in minecraft:overworld run tp ArceTester1 42.5 150 17.5 facing 42.5 149.88 20")
        # Floor-level devices keep revision 1's command byte for byte.
        self.assertEqual(k16.look((6, 150, 6))["command"],
                         "execute in minecraft:overworld run tp ArceTester1 6.5 150 3.5 facing 6.5 148.88 6")
        self.assertEqual(k16.look((6, 128, 6), k16.SPACE)["command"],
                         f"execute in {k16.SPACE} run tp ArceTester1 6.5 128 3.5 facing 6.5 126.88 6")
        self.assertIn(k16.look((42, 151, 20)), self.core("core-screen-lathe"))

    def test_aim_looks_from_the_eye(self):
        self.assertEqual(k16.aim((34.5, 151, 34.5), (34.5, 152, 36.5), k16.MOON)["command"],
                         f"execute in {k16.MOON} run tp ArceTester1 34.5 151 34.5 facing 34.5 150.38 36.5")

    def test_hover_points_lie_inside_the_screens_tooltip_rectangles(self):
        rectangles = {"rolling": ("RollingMachineScreen", (58, 27, 12, 43), "isHovering(58, 27, 12, 43"),
                      "terminal": ("SatelliteTerminalScreen", (181, 19, 32, 31), "isHovering(181, 19, 32, 31"),
                      "builder": ("SatelliteBuilderScreen", (197, 18, 18, 44), "isHovering(197, 18, 18, 44"),
                      "precision": ("PrecisionAssemblerScreen", (151, 34, 14, 37),
                                    "isHovering(151, ENERGY_GAUGE_Y, 14, ENERGY_GAUGE_HEIGHT")}
        for layout, (source, (x, y, width, height), anchor) in rectangles.items():
            text = (k16.ROOT / k16.JAVA_ROOT / "client" / (source + ".java")).read_text(encoding="utf-8")
            with self.subTest(layout=layout):
                self.assertIn(anchor, text)
                px, py = k16.LAYOUTS[layout][2]["energy"]
                self.assertTrue(x <= px < x + width and y <= py < y + height)
                moves = [op for op in self.core("core-screen-" + layout) if op["op"] == "move"]
                expected = k16.gui_point((1280, 720, 3), k16.LAYOUTS[layout][0], (px, py))["pixel"]
                self.assertEqual([[op["x"], op["y"]] for op in moves], [expected])
        precision = (k16.ROOT / k16.JAVA_ROOT / "client/PrecisionAssemblerScreen.java").read_text(encoding="utf-8")
        self.assertIn("ENERGY_GAUGE_Y = 34", precision)
        self.assertIn("ENERGY_GAUGE_HEIGHT = 37", precision)
        # The classic controller screen draws no tooltip, so the lathe request takes no hover capture.
        classic = (k16.ROOT / k16.JAVA_ROOT / "client/ClassicControllerScreen.java").read_text(encoding="utf-8")
        self.assertNotIn("renderTooltip", classic)
        lathe = self.core("core-screen-lathe")
        self.assertFalse(any(op["op"] == "move" or "hover" in op.get("name", "") for op in lathe))

    def test_rocket_is_validated_assembled_and_fuelled_behind_readbacks(self):
        ops = self.core("core-rocket-assemble")
        kinds = [(op["op"], op.get("key", op.get("expect"))) for op in ops]
        success = kinds.index(("require", 'block data: "SUCCESS"'))
        down, up = kinds.index(("keydown", "SHIFT")), kinds.index(("keyup", "SHIFT"), 3)
        assembled = kinds.index(("require", 'entity data: "ASSEMBLED"'))
        self.assertTrue(success < down < up < assembled)
        self.assertEqual([op["op"] for op in ops[down + 1:up]], ["click"])
        self.assertTrue(ops[down + 1]["right"])
        fuel = self.core("core-rocket-fuel")
        self.assertEqual(fuel[3]["expect"], "entity data: 2000L")
        self.assertIn("fuel.capacity", fuel[3]["command"])
        self.assertEqual(sum(1 for op in fuel if op["op"] == "click" and op.get("right")), 8)
        amount = [op for op in fuel if op["op"] == "require" and "fuel.amount" in op["command"]]
        self.assertEqual([op["expect"] for op in amount], ["entity data: 2000L"])
        source = (k16.ROOT / k16.JAVA_ROOT / "rocket/assembler/RocketAssemblerBlock.java").read_text(encoding="utf-8")
        self.assertIn("RocketRuntime.requestAssembler(serverPlayer, position, player.isShiftKeyDown())", source)

    def test_flight_requests_gate_on_fuel_suit_and_arrival(self):
        plan = self.core("core-flight-plan")
        self.assertEqual(plan[3]["expect"], "entity data: 2000L")
        self.assertEqual(plan[4], k16.suit_ready())
        launch = k16.click((1280, 720, 3), "flight", "launch")
        self.assertEqual(plan.count(launch), 1)
        self.assertEqual(plan[plan.index(launch) + 1]["expect"], 'entity data: "COUNTDOWN"')
        observe = self.core("core-flight-observe")
        # The rocket's own dimension comes first: the player can be on the Moon without having flown.
        self.assertIn("flight_data.current_dimension", observe[3]["command"])
        self.assertEqual(observe[3]["expect"], f'entity data: "{k16.MOON}"')
        self.assertIn(f"data get entity {k16.PLAYER} Dimension", observe[4]["command"])
        self.assertEqual(sum(1 for op in observe if op.get("key") == "F5"), 3)  # Back to first person.

    def test_station_request_keeps_the_first_target_and_names_the_refusal(self):
        ops = self.core("core-station")
        self.assertNotIn(k16.click((1280, 720, 3), "warp", "next"), ops)
        self.assertNotIn(k16.click((1280, 720, 3), "warp", "confirm"), ops)
        patterns = [op["pattern"] for op in ops if op["op"] == "expect_log"]
        self.assertEqual(patterns, ["action=request code=WARP_INSUFFICIENT_ENERGY",
                                    f"target={k16.CYGNUS} class=INTERSTELLAR"])
        _, index = k16.batches()
        entry = next(e for e in index if e["file"] == "batches/core-station-1280x720-s3.json")
        self.assertIn("refus", entry["step"])
        self.assertIn("K08B", entry["prerequisites"])

    def test_dimension_changes_wait_for_the_loading_screen_before_any_click(self):
        waited = dict(op="require_screen", open=False, timeout=20)
        for name in ("core-suit", "core-room", "core-flight-plan", "core-station", "core-railgun"):
            ops = self.core(name)
            first_click = next(i for i, op in enumerate(ops) if op["op"] == "click")
            with self.subTest(name=name):
                self.assertIn(waited, ops[:first_click])
                self.assertEqual(ops[ops.index(waited) - 1], {"op": "sleep", "seconds": 1.5})
        observe = self.core("core-flight-observe")
        first_f5 = next(i for i, op in enumerate(observe) if op.get("key") == "F5")
        self.assertEqual(observe[first_f5 - 1], waited)

    def test_gates_are_bounded(self):
        from scripts.campaign_client.gates import MAX_GATE_SECONDS, gate_seconds
        requests, _ = k16.batches()
        for raw in requests.values():
            for op in json.loads(raw)["ops"]:
                if op["op"] == "require":
                    self.assertLessEqual(gate_seconds(op), 60)
                    self.assertLessEqual(gate_seconds(op), MAX_GATE_SECONDS)


class PatternAndOutputTest(unittest.TestCase):
    def test_direct_cli_help_does_not_start_a_process_session(self):
        result = subprocess.run([sys.executable, "-B", str(k16.ROOT / "scripts/campaign_prep/k16_core.py"), "--help"],
                                capture_output=True, text=True, timeout=30, cwd=k16.ROOT)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("generate,build,singleplayer,seal", result.stdout)

    def fixture_jar(self, path):
        pattern = {"controller_anchor": {"x": 1, "y": 0, "z": 0}, "cells": [
            {"x": 1, "y": 0, "z": 0, "matcher": {"controller": True}},
            {"x": 0, "y": 0, "z": 0, "matcher": {"block": "minecraft:iron_block"}},
            {"x": 1, "y": 1, "z": 0, "matcher": {"optional": {"block": "minecraft:iron_block"}}}]}
        kinds = ("rolling_machine", "precision_assembler", "lathe", "cutting_machine", "crystallizer",
                 "chemical_reactor", "arc_furnace", "precision_laser_etcher", "observatory",
                 "astrobody_data_processor", "railgun", "elevator_anchor", "black_hole_generator", "orbital_laser_drill")
        with zipfile.ZipFile(path, "w") as jar:
            for kind in kinds:
                jar.writestr(f"data/{k16.NS}/machine_patterns/{kind}.json", json.dumps(pattern))
            jar.writestr(f"data/{k16.NS}/recipes/railgun.json", '{"result":{"item":"advancedrocketrycommunity:railgun"}}')

    def test_controller_last_optional_air_and_local_origin(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fixture.jar"
            self.fixture_jar(path)
            with zipfile.ZipFile(path) as jar:
                commands, cells = k16.pattern_commands(jar, "railgun", (76, 150, 76), k16.MOON)
            self.assertIn("75 150 76 minecraft:iron_block", commands[0])
            self.assertIn("76 151 76 minecraft:air", commands[1])
            self.assertIn("76 150 76 advancedrocketrycommunity:railgun[facing=north]", commands[-1])
            self.assertEqual(len(cells), 2)

    def test_black_hole_is_a_station_context_not_an_invented_dimension(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fixture.jar"
            self.fixture_jar(path)
            with zipfile.ZipFile(path) as jar:
                commands, devices, _ = k16.core_world(jar)
            self.assertFalse(any("execute in " + k16.CYGNUS in value for value in commands))
            blackhole = next(device for device in devices if device["name"] == "black_hole_generator")
            self.assertEqual(blackhole["dimension"], k16.SPACE)
            self.assertEqual(blackhole["pos"], (1044, 128, 20))
            self.assertTrue(any("admin create" in value and k16.CYGNUS in value for value in commands))

    def test_elevator_anchor_has_no_facing_property(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fixture.jar"
            self.fixture_jar(path)
            with zipfile.ZipFile(path) as jar:
                commands, _ = k16.pattern_commands(jar, "elevator_anchor", (60, 150, 76))
            self.assertEqual(commands[-1], "execute in minecraft:overworld run setblock "
                             "60 150 76 advancedrocketrycommunity:elevator_anchor")
        source = (k16.ROOT / k16.JAVA_ROOT / "endgame/elevator/ElevatorEndpointBlock.java").read_text()
        self.assertNotIn("FACING", source)

    def test_singleplayer_copy_changes_only_its_metadata(self):
        from scripts import loader_cut_nbt as nbt
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            world = output / "world"
            world.mkdir()
            root = nbt.Tag(nbt.COMPOUND, {"Data": nbt.Tag(nbt.COMPOUND, {
                "allowCommands": nbt.Tag(nbt.BYTE, 0), "GameType": nbt.Tag(nbt.INT, 0),
                "LevelName": nbt.Tag(nbt.STRING, b"world"), "RandomSeed": nbt.Tag(nbt.LONG, 20261006)})})
            nbt.write_gzip(world / "level.dat", b"", root)
            (world / "unrelated.dat").write_bytes(b"untouched")
            client = output / "source-client"
            (client / "mods").mkdir(parents=True)
            jar = client / "mods/candidate.jar"
            self.fixture_jar(jar)
            (client / "options.txt").write_text("lang:en_us\nsoundCategory_master:0.0\n")
            cell = output / "cell.json"
            cell.write_bytes(k16.json_bytes(dict(schema_version=1, forge="47.4.10", jei=False,
                jei_jar=None, runtime_root=str(output / "runtime"), server_dir=str(client),
                version_id="1.20.1-forge-47.4.10", client_game_dir=str(client),
                candidate=dict(path=str(jar), sha256=k16.file_hash(jar)), mods=dict(client=[]),
                server_forge_args=dict(path="audit-only", sha256="0" * 64))))
            before = k16.tree_hashes(world)
            with patch.object(k16, "JAR_HASH", k16.file_hash(jar)):
                report = k16.prepare_singleplayer(output, cell)
            self.assertEqual(before, k16.tree_hashes(world))
            self.assertTrue(report["client_source_unchanged"])
            self.assertFalse(report["launched"])
            save = output / "p9-client/saves/K16-P9"
            fields = nbt.plain(nbt.read_gzip(save / "level.dat")[1])["Data"]
            self.assertEqual(fields["allowCommands"], 1)
            self.assertEqual(fields["GameType"], 1)
            self.assertEqual(fields["RandomSeed"], 20261006)
            self.assertEqual((save / "unrelated.dat").read_bytes(), b"untouched")
            self.assertIn("soundCategory_master:1.0", (output / "p9-client/options.txt").read_text())
            self.assertEqual(len(report["sessions"]), 6)
            from scripts.campaign_client.cells import load_cell
            prepared = json.loads((output / "p9-cell.json").read_text())
            self.assertNotIn("mods", prepared)
            self.assertNotIn("server_forge_args", prepared)
            self.assertEqual(load_cell(output / "p9-cell.json")["candidate"]["sha256"], k16.file_hash(jar))
            with self.assertRaisesRegex(ValueError, "occupied P9"):
                k16.prepare_singleplayer(output, cell)

    def test_two_generations_byte_identical_and_refuse_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            jar = root / "fixture.jar"
            self.fixture_jar(jar)
            with patch.object(k16, "JAR_HASH", k16.file_hash(jar)):
                manifest = k16.generate(root / "one", jar)
                k16.generate(root / "two", jar)
                self.assertEqual(k16.tree_hashes(root / "one"), k16.tree_hashes(root / "two"))
                with self.assertRaises(FileExistsError):
                    k16.generate(root / "one", jar)
            self.assertFalse(manifest["client_runs"])
            self.assertEqual(manifest["revision"], 2)
            self.assertEqual(manifest["core_route"], list(k16.CORE_ROUTE))
            self.assertTrue(manifest["changes_from_revision_1"])
            self.assertFalse(manifest["required_gates_approved"])
            self.assertIn("fixture grants", manifest["grants"])

    def test_reject_wrong_jar_before_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            path = root / "wrong.jar"
            path.write_bytes(b"not C6")
            with self.assertRaisesRegex(ValueError, "pinned C6"):
                k16.generate(root / "out", path)
            self.assertFalse((root / "out").exists())

    def test_seal_lists_only_package_members_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "a.txt").write_bytes(b"a")
            k16.seal(root)
            self.assertEqual((root / "SHA256SUMS.txt").read_text(), hashlib.sha256(b"a").hexdigest() + "  a.txt\n")
            with self.assertRaisesRegex(ValueError, "overwrite sealed"):
                k16.seal(root)

    def test_offline_uuid_is_not_premium_identity(self):
        raw = bytearray(hashlib.md5(b"OfflinePlayer:ArceTester1").digest())
        raw[6] = (raw[6] & 15) | 48
        raw[8] = (raw[8] & 63) | 128
        self.assertEqual(str(k16.UUID(bytes=bytes(raw))), k16.OWNER)

    def test_failed_owned_process_cleanup_retains_lock_instruction(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            process = Mock()
            process.process.pid = 12345
            process.process.poll.return_value = None
            with patch.object(k16.subprocess, "run", side_effect=OSError("injected stop failure")):
                with self.assertRaisesRegex(OSError, "injected stop failure"):
                    k16.abort_owned(process, output)
            self.assertIn("pid=12345", (output / "retain-heavy-jvm-lock.txt").read_text())

    def test_dead_owned_process_does_not_get_killed_again(self):
        with tempfile.TemporaryDirectory() as temp:
            process = Mock()
            process.process.poll.return_value = 0
            with patch.object(k16.subprocess, "run") as run:
                k16.abort_owned(process, Path(temp))
            run.assert_not_called()
            process.abort.assert_called_once()


if __name__ == "__main__":
    unittest.main()
