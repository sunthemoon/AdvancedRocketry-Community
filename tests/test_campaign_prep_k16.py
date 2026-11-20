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
        self.assertEqual(len(requests), 108)
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
            (client / "mods/candidate.jar").write_bytes(b"candidate")
            (client / "options.txt").write_text("lang:en_us\nsoundCategory_master:0.0\n")
            cell = output / "cell.json"
            cell.write_bytes(k16.json_bytes(dict(client_game_dir=str(client),
                candidate=dict(path=str(client / "mods/candidate.jar")), mods=dict(client=[]))))
            before = k16.tree_hashes(world)
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
