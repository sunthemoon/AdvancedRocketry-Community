"""Conservation and capture boundaries for the real Beta-world upgrade harness."""

import copy
import gzip
import socket
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import inspect_v100_world_upgrade as inspect
from scripts import run_v100_beta_world_upgrade as runner
from scripts import v100_beta_world_fixture as fixture
from scripts.run_v090_migration_server_smoke import _saved_data_bytes


def machine(*, running=True, completed=0, progress=40):
    inputs, water, energy = (8, 4000, 20000) if running else (2, 1000, 800)
    items = []
    for slot, name, count in ((0, "empty", inputs - 2 * completed), (2, "hydrogen", completed), (3, "oxygen", completed)):
        if count:
            items.append({"Slot": slot, "id": f"advancedrocketrycommunity:{name}_canister", "Count": count})
    result = {"schema_version": 1, "inventory": {"Size": 4, "Items": items},
              "fluid": {"FluidName": "minecraft:water", "Amount": water - completed * 1000},
              "progress": progress, "energy": energy - 20 * (100 * completed + progress)}
    if progress:
        result["active_recipe"] = "advancedrocketrycommunity:electrolyzer_water"
    return result


def world():
    roots = {key: {"schema_version": 2, "format_epoch": "v0.9.0-beta"} for key in inspect.MANAGED}
    roots["observed"] = {
        "schema": 1, "game_time": 100, "paused": machine(running=False), "running": machine(),
        "earth_clear": 1, "moon_clear": 1, "no_drops": 1,
        "platform": {f"p{i}": 1 for i in range(289)}, "room": {f"p{i}": 1 for i in range(26)},
        "vent_lit": 1, "vent": {"schema_version": 1, "oxygen_canisters": 0, "empty_canisters": 0,
                                 "oxygen_units": 3999, "energy": 39500, "oxygen_phase": 5},
        "rockets": [{"UUID": [0, 0, 0, n], "Pos": [0., 100., 0.], "observed_type": "advancedrocketrycommunity:rocket",
                     "observed_dimension": dimension, "RocketEntityData": {"schema_version": 1, "fuel": 600}}
                    for n, dimension in enumerate(("minecraft:overworld", "advancedrocketrycommunity:moon"), 1)],
    }
    roots["satellite_missions"].update(clock={"logical_game_time": 100, "last_observed_game_time": 100},
        satellites=[], research_accounts=[{"balance": 25}], missions=[{
            "mission_id": [0, 0, 0, 3], "status": "active", "completes_at": 120, "owner_id": [0, 0, 0, 4]}])
    return roots


class WorldProjectionTests(unittest.TestCase):
    def test_duplicate_blocks_drops_and_missing_geometry_are_rejected(self):
        for key in ("earth_clear", "moon_clear", "no_drops", "platform", "room"):
            after = world()
            after["observed"][key] = 0
            with self.assertRaises(inspect.SmokeError):
                inspect.compare_worlds(world(), after)

    def test_identical_state_is_conserved(self):
        result = inspect.compare_worlds(world(), world())
        self.assertEqual(result["additional_processing_ticks"], 0)
        self.assertEqual(len(result["rocket_ids"]), 2)

    def test_processing_and_due_mission_are_accounted(self):
        before = world()
        after = copy.deepcopy(before)
        after["observed"]["running"] = machine(completed=1, progress=10)
        after["observed"]["game_time"] = 170
        after["observed"]["vent"].update(energy=39100, oxygen_units=3998)
        data = after["satellite_missions"]
        data["clock"] = {"logical_game_time": 170, "last_observed_game_time": 170}
        data["missions"][0].update(status="ready", ready_at=120)
        result = inspect.compare_worlds(before, after)
        self.assertEqual(result["additional_processing_ticks"], 70)
        self.assertEqual(len(result["missions_became_ready"]), 1)

    def test_machine_recipe_completion_is_exact(self):
        result = inspect.machine_ledger(machine(completed=4, progress=0), running=True)
        self.assertEqual((result["completed"], result["water"], result["energy"]), (4, 0, 12000))

    def test_machine_resource_and_progress_corruption_are_rejected(self):
        for field, value in (("energy", 19999), ("progress", 0), ("schema_version", 2), ("active_recipe", "arce:wrong")):
            with self.subTest(field=field):
                data = machine()
                data[field] = value
                with self.assertRaises(inspect.SmokeError):
                    inspect.machine_ledger(data, running=True)

    def test_machine_extra_or_duplicate_items_are_rejected(self):
        for slot in (0, 1):
            data = machine()
            data["inventory"]["Items"].append({"Slot": slot, "id": "minecraft:diamond", "Count": 1})
            with self.assertRaises(inspect.SmokeError):
                inspect.machine_ledger(data, running=True)

    def test_paused_machine_requires_real_forty_tick_progress(self):
        for progress in (0, 39, 41):
            with self.assertRaises(inspect.SmokeError):
                inspect.machine_ledger(machine(running=False, progress=progress), running=False)

    def test_vent_phase_loss_is_not_ignored(self):
        before, after = world(), world()
        after["observed"]["vent"]["oxygen_phase"] = 0
        with self.assertRaisesRegex(inspect.SmokeError, "phase/energy"):
            inspect.compare_worlds(before, after)

    def test_vent_extra_resources_or_dark_room_are_rejected(self):
        for field, value in (("energy", 39600), ("oxygen_units", 4000)):
            after = world()
            after["observed"]["vent"][field] = value
            with self.assertRaises(inspect.SmokeError):
                inspect.compare_worlds(world(), after)
        after["observed"]["vent_lit"] = 0
        with self.assertRaises(inspect.SmokeError):
            inspect.compare_worlds(world(), after)

    def test_rocket_count_identity_position_and_payload_are_compared(self):
        for mutate in (lambda a: a["observed"]["rockets"].pop(),
                       lambda a: a["observed"]["rockets"][0]["UUID"].__setitem__(3, 99),
                       lambda a: a["observed"]["rockets"][0]["Pos"].__setitem__(0, 1.),
                       lambda a: a["observed"]["rockets"][0]["RocketEntityData"].update(fuel=601)):
            after = world()
            mutate(after)
            with self.assertRaises(inspect.SmokeError):
                inspect.compare_worlds(world(), after)

    def test_station_and_research_drift_are_rejected(self):
        for key in ("stations", "celestial", "rocket_transactions", "rocket_transfers"):
            after = world()
            after[key]["unexpected"] = 1
            with self.assertRaises(inspect.SmokeError):
                inspect.compare_worlds(world(), after)
        after = world()
        after["satellite_missions"]["research_accounts"][0]["balance"] += 1
        with self.assertRaises(inspect.SmokeError):
            inspect.compare_worlds(world(), after)

    def test_premature_ready_claim_or_owner_changes_are_rejected(self):
        for change in ({"status": "ready", "ready_at": 119}, {"status": "claimed"}, {"owner_id": [0, 0, 0, 5]}):
            after = world()
            after["satellite_missions"]["missions"][0].update(change)
            with self.assertRaises(inspect.SmokeError):
                inspect.compare_worlds(world(), after)

    def test_duplicate_and_invalid_uuids_are_rejected(self):
        for value in ([], [0, 0, 0, 2**31], [0, 0, 0, True], "not-a-uuid"):
            with self.assertRaises(inspect.SmokeError):
                inspect.nbt_uuid(value)
        with self.assertRaises(inspect.SmokeError):
            inspect.indexed([{"id": [0, 0, 0, 1]}] * 2, "id")


class CaptureBoundaryTests(unittest.TestCase):
    def test_reused_archive_is_bound_and_retained_without_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, output = root / "source", root / "output"
            source.mkdir()
            output.mkdir()
            path = source / "original-beta-world.zip"
            path.write_bytes(b"archive contract")
            retained = {"status": "PASS", "artifact_sha256": runner.BETA_SHA256,
                        "original_world_archive": {"file": path.name, "bytes": path.stat().st_size,
                                                   "sha256": inspect.digest_file(path)}}
            runner.retain_fixture_archive(source / "summary.json", retained, output)
            self.assertEqual((output / path.name).read_bytes(), path.read_bytes())
            with self.assertRaises(inspect.SmokeError):
                runner.retain_fixture_archive(source / "summary.json", retained, output)
            retained["original_world_archive"]["sha256"] = "0" * 64
            with self.assertRaises(inspect.SmokeError):
                runner.retain_fixture_archive(source / "summary.json", retained, output)
            retained["original_world_archive"]["file"] = "../outside.zip"
            with self.assertRaises(inspect.SmokeError):
                runner.retain_fixture_archive(source / "summary.json", retained, output)

    def test_live_loopback_port_is_rejected_without_stopping_its_owner(self):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.bind(("127.0.0.1", 0))
            listener.listen(1)
            with self.assertRaises(inspect.SmokeError):
                runner.require_stopped_port(listener.getsockname()[1])
            self.assertGreaterEqual(listener.fileno(), 0)

    def test_all_platform_and_room_blocks_are_individually_observed(self):
        commands = fixture.material_checks()
        self.assertEqual(sum("state.platform.p" in command for command in commands), 289)
        self.assertEqual(sum("state.room.p" in command for command in commands), 26)
        self.assertEqual(sum("minecraft:yellow_concrete" in command for command in commands), 4)
        self.assertEqual(sum("minecraft:sea_lantern" in command for command in commands), 1)
        self.assertTrue(all("setblock" not in command and "fill " not in command for command in commands))

    def test_native_capture_copies_full_data_and_stops_in_one_function(self):
        functions = fixture.capture_functions()
        self.assertEqual(functions["capture"][-4], "say " + fixture.CAPTURED)
        self.assertTrue(functions["capture"][-3].endswith("state.flush_success byte 1 run save-all flush"))
        self.assertIn("flush_success:1b", functions["capture"][-2])
        self.assertEqual(functions["capture"][-1], "stop")
        self.assertTrue(any("scratch set from entity @s" in command for command in functions["rocket"]))
        for commands in functions.values():
            for command in commands:
                self.assertNotIn("data merge block", command)
                self.assertNotIn("kill ", command)
                self.assertNotIn("data modify entity", command)

    def test_chat_marker_cannot_match_echo_or_embedded_text(self):
        marker = fixture.chat("PROOF")
        self.assertIsNotNone(marker.search("[Server thread/INFO] [minecraft/MinecraftServer]: [Server] PROOF\n"))
        for text in ("> say PROOF", "[Server] PROOF extra", "PROOF", "executing say PROOF"):
            self.assertIsNone(marker.search(text))

    def test_second_rocket_setup_never_deletes_entities(self):
        harness = fixture.WorldHarness(java="java", server=Path("unused"), port=25612, expected_version=runner.BETA, startup_timeout=1)
        process = Mock()
        with patch.object(fixture, "wait_condition"):
            harness.configure_rocket(process)
        commands = [call.args[0] for call in process.command.call_args_list]
        self.assertFalse(any("kill" in command for command in commands))
        self.assertEqual(sum("setblock" in command for command in commands), 6)

    def test_room_bounds_cover_all_wall_chunks_before_placement(self):
        process = Mock()
        with patch.object(fixture, "wait_condition") as wait:
            fixture.room_setup(process)
        commands = [call.args[0] for call in process.command.call_args_list]
        self.assertIn("forceload add 63 63 65 65", commands[0])
        self.assertIn("if loaded 63 100 63 if loaded 65 102 65", wait.call_args.args[1])
        self.assertIn("fill 63 100 63 65 102 65", commands[1])

    def test_startup_failure_stops_only_owned_process(self):
        process = Mock()
        process.wait_for.side_effect = inspect.SmokeError("startup failure")
        harness = fixture.WorldHarness(java="java", server=Path("unused"), port=25612, expected_version=runner.BETA, startup_timeout=1)
        with patch.object(fixture.server, "CapturedProcess", return_value=process), self.assertRaises(inspect.SmokeError):
            harness.start("failed")
        process.abort.assert_called_once()

    def test_paths_cannot_overlap_or_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            for work, output in ((root / "source/child", root / "evidence"), (root / "work", root / "work")):
                with self.assertRaises(inspect.SmokeError):
                    runner.check_paths(root / "source", work, output)
            (root / "work").mkdir()
            with self.assertRaises(inspect.SmokeError):
                runner.check_paths(root / "source", root / "work", root / "output")

    def test_pack_is_private_and_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "server.properties").write_text("online-mode=true\nfunction-permission-level=2\n", encoding="utf-8")
            fixture.install_capture_pack(root)
            text = (root / "server.properties").read_text(encoding="utf-8")
            self.assertIn("online-mode=true", text)
            self.assertEqual(text.count("function-permission-level="), 1)
            self.assertIn("function-permission-level=4", text)
            with self.assertRaises(FileExistsError):
                fixture.install_capture_pack(root)

    def test_original_archive_and_manifest_detect_changed_and_added_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            world_path, output = root / "world", root / "evidence"
            world_path.mkdir()
            output.mkdir()
            (world_path / "level.dat").write_bytes(b"fixture")
            records = inspect.manifest(world_path)
            archived = runner.archive_world(world_path, output, records)
            self.assertTrue((output / archived["file"]).is_file())
            self.assertEqual(records, inspect.manifest(world_path))
            (world_path / "extra").write_bytes(b"new")
            self.assertNotEqual(records, inspect.manifest(world_path))
            (world_path / "level.dat").write_bytes(b"changed")
            self.assertNotEqual(records[0]["sha256"], next(item["sha256"] for item in inspect.manifest(world_path) if item["file"] == "level.dat"))

    def test_nbt_reader_checks_version_corruption_and_expanded_bound(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.dat"
            path.write_bytes(_saved_data_bytes(()))
            self.assertEqual(inspect.read_nbt(path), {"schema_version": 1})
            for payload in (b"invalid gzip", gzip.compress(b"x" * (4 * 1024**2 + 1))):
                path.write_bytes(payload)
                with self.assertRaises((OSError, ValueError, inspect.SmokeError)):
                    inspect.read_nbt(path)


if __name__ == "__main__":
    unittest.main()
