import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts import run_v100_compatibility_matrix as matrix
from scripts import v100_compatibility_runtime as runtime
from scripts.run_dedicated_server_smoke import SmokeError


def log(jei=True, forge="47.4.10", name="V100Jei10", port=25614, version="1.20.1-1.0.0-dev"):
    return [f"[12:00:00] [Render thread/INFO] [advancedrocketrycommunity/Test]: {value}\n"
            for value in matrix.client_markers(forge, jei, name, port, version)]


def audit(lines, jei=True):
    return matrix.audit_client(lines, "47.4.10", jei, "V100Jei10", 25614, "1.20.1-1.0.0-dev")


class CompatibilityLogTests(unittest.TestCase):
    def test_native_packaged_forge_line_includes_exact_mcp_receipt(self):
        observed = "[21:08:23] [modloading-worker-0/INFO] [ne.mi.co.ForgeMod/FORGEMOD]: Forge mod loading, version 47.4.10, for MC 1.20.1 with MCP 20230612.114412"
        self.assertIsNotNone(matrix.marker(matrix.forge_message("47.4.10")).search(observed))
        for changed in (observed.replace("114412", "114413"), observed.replace("47.4.10", "47.4.23"),
                        observed.replace(": Forge mod", ": [Server] Forge mod")):
            self.assertIsNone(matrix.marker(matrix.forge_message("47.4.10")).search(changed))

    def test_all_four_cells_are_distinct_exact_combinations(self):
        self.assertEqual(4, len(matrix.CELLS))
        self.assertEqual({("47.4.10", True), ("47.4.10", False), ("47.4.23", True), ("47.4.23", False)},
                         {(forge, jei) for forge, jei, _ in matrix.CELLS})
        for forge, jei, username in matrix.CELLS:
            result = matrix.audit_client(log(jei, forge, username), forge, jei, username, 25614, "1.20.1-1.0.0-dev")
            self.assertEqual(1 if jei else None, result["jei_synchronized_recipe_count"])

    def test_each_missing_receipt_fails(self):
        values = log()
        for index in range(len(values)):
            with self.subTest(index=index), self.assertRaises(SmokeError):
                audit(values[:index] + values[index + 1:])

    def test_echo_is_not_a_native_receipt(self):
        values = log()
        values[0] = values[0].replace(": Setting user:", ": [Server] Setting user:")
        with self.assertRaisesRegex(SmokeError, "native log receipt"):
            audit(values)

    def test_wrong_version_or_forge_or_destination_is_rejected(self):
        for old, new in (("1.20.1-1.0.0-dev", "1.20.1-0.9.0-beta.1"), ("47.4.10", "47.4.23"), ("25614", "25615")):
            with self.subTest(old=old), self.assertRaises(SmokeError):
                audit([line.replace(old, new) for line in log()])

    def test_recipe_requires_post_connection_sync(self):
        values = log()
        recipe = values.pop()
        values.insert(0, recipe)
        with self.assertRaisesRegex(SmokeError, "after server connection"):
            audit(values)

    def test_connection_order_is_checked(self):
        values = log()
        values[3], values[4] = values[4], values[3]
        with self.assertRaisesRegex(SmokeError, "out of order"):
            audit(values)

    def test_conflicting_jei_detection_fails(self):
        with self.assertRaisesRegex(SmokeError, "conflicting"):
            audit(log() + [log(False)[5]])

    def test_project_errors_and_linkage_are_blocking(self):
        for value in ("Unknown recipe category", "NoClassDefFoundError", "ClassNotFoundException",
                      "Attempted to load class net/minecraft/client", "[Render thread/ERROR] advancedrocketrycommunity failure",
                      "[Render thread/ERROR] [io.gi.su.ad.client/Test]: example"):
            with self.subTest(value=value), self.assertRaises(SmokeError):
                audit(log() + [value])

    def test_external_errors_require_investigation(self):
        with self.assertRaisesRegex(SmokeError, "example service failure"):
            audit(log() + ["[Render thread/ERROR] [external/Test]: example service failure"])

    def test_actual_renderer_is_recorded_without_a_prefilled_device(self):
        result = audit(log() + ["[Render thread/INFO] [forge/Test]: GL info: Renderer: measured device"])
        self.assertEqual(0, result["log_audit"]["error_count"])
        self.assertEqual([], result["external_error_or_fatal_lines"])
        self.assertIn("measured device", result["renderer_lines"][0])


class CompatibilityRuntimeTests(unittest.TestCase):
    def test_native_logs_are_preserved_without_overwriting_previous_attempts(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game, output = root / "game", root / "evidence"
            (game / "logs").mkdir(parents=True)
            output.mkdir()
            (game / "logs/debug.log").write_text("native handshake diagnostic")
            records = matrix.archive_native_logs(game, output, "client")
            self.assertEqual(1, len(records))
            self.assertEqual("native handshake diagnostic", (output / records[0]["file"]).read_text())
            with self.assertRaises(SmokeError):
                matrix.archive_native_logs(game, output, "client")

    def test_diagnostic_options_respect_native_simulation_distance_minimum(self):
        options = dict(line.split(":", 1) for line in matrix.CLIENT_OPTIONS.splitlines())
        self.assertEqual("5", options["simulationDistance"])
        self.assertEqual("false", options["fullscreen"])
        self.assertEqual("2", options["guiScale"])

    def test_only_visible_glfw_window_owned_by_child_pid_can_be_closed(self):
        windows = [{"pid": 10, "handle": 100, "class": "GLFW30", "visible": False},
                   {"pid": 10, "handle": 101, "class": "GLFW30", "visible": True},
                   {"pid": 20, "handle": 200, "class": "GLFW30", "visible": True},
                   {"pid": 10, "handle": 300, "class": "Other", "visible": True}]
        self.assertEqual(101, matrix.select_owned_window(windows, 10))
        for values in ([], [windows[0]], [windows[2]], windows + [dict(windows[1], handle=102)]):
            with self.assertRaises(SmokeError):
                matrix.select_owned_window(values, 10)

    def test_runtime_relative_paths_cannot_escape(self):
        with tempfile.TemporaryDirectory() as temp:
            for value in ("", "../escape", "/absolute", "C:/drive", "nested\\escape"):
                with self.subTest(value=value), self.assertRaises(SmokeError):
                    runtime.child(Path(temp), value)

    def test_existing_download_must_still_match_hash_and_size(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "library.jar"
            path.write_bytes(b"test")
            digest = hashlib.sha1(b"test").hexdigest()
            self.assertEqual(path, runtime.download("https://libraries.minecraft.net/test.jar", path, digest, 4))
            for checksum, size in (("0" * 40, 4), (digest, 3)):
                with self.assertRaises(SmokeError):
                    runtime.download("https://libraries.minecraft.net/test.jar", path, checksum, size)

    def test_untrusted_download_sources_and_bad_metadata_are_rejected_before_execution(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(runtime.subprocess, "run") as run:
            target = Path(temp) / "file"
            for url in ("http://libraries.minecraft.net/file", "https://example.org/file",
                        "https://user@libraries.minecraft.net/file", "https://libraries.minecraft.net/file?token=x"):
                with self.assertRaises(SmokeError):
                    runtime.download(url, target)
            for digest, size in (("wrong", 5), ("0" * 40, 0), ("0" * 40, runtime.MAX_FILE + 1), ("0" * 40, True)):
                with self.assertRaises(SmokeError):
                    runtime.download("https://libraries.minecraft.net/file", target, digest, size)
            run.assert_not_called()

    def test_launcher_rules_select_windows_x64_without_optional_account_features(self):
        self.assertTrue(runtime.allowed(None))
        self.assertFalse(runtime.allowed([]))
        self.assertTrue(runtime.allowed([{"action": "allow", "os": {"name": "windows"}}]))
        for scope in ({"os": {"name": "linux"}}, {"os": {"arch": "x86"}}, {"features": {"is_demo_user": True}}):
            self.assertFalse(runtime.allowed([{"action": "allow", **scope}]))
        with self.assertRaises(SmokeError):
            runtime.allowed([{"action": "ignore"}])

    def test_forge_library_replaces_same_coordinate_but_preserves_native_classifier(self):
        def library(name, path):
            return {"name": name, "downloads": {"artifact": {"path": path}}}
        vanilla = {"libraries": [library("org:test:1", "old"), library("org:test:1:natives-windows", "native")]}
        forge = {"libraries": [library("org:test:2", "new")]}
        self.assertEqual([{"path": "new"}, {"path": "native"}], runtime.libraries(vanilla, forge))

    def test_argument_expansion_rejects_missing_values_or_newlines(self):
        self.assertEqual(["--name", "Example"], runtime.expand(["--name", "${username}"], {"username": "Example"}))
        for value in ("${missing}", "bad\nvalue", "bad\0value"):
            with self.assertRaises(SmokeError):
                runtime.expand([value], {})

    def test_argument_file_quotes_spaces_and_cannot_inject_another_line(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "client.args"
            matrix.argument_file(target, ["-Dpath=C:\\Program Files\\Java", "Example"])
            self.assertIn('"-Dpath=C:\\\\Program Files\\\\Java"', target.read_text())
            with self.assertRaises(SmokeError):
                matrix.argument_file(Path(temp) / "bad.args", ["x\n-eval"])

    def test_mod_inventory_rejects_changes_or_extra_dependencies(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game = root / "game"
            game.mkdir()
            artifact = root / "arce.jar"
            artifact.write_bytes(b"arce")
            jei = root / "jei-original.jar"
            jei.write_bytes(b"jei")
            installed = matrix.install_mods(game, artifact, jei)
            self.assertEqual(installed, matrix.check_mods(game, artifact, jei))
            (game / "mods/arce.jar").write_bytes(b"drift")
            with self.assertRaises(SmokeError):
                matrix.check_mods(game, artifact, jei)
            (game / "mods/arce.jar").write_bytes(b"arce")
            (game / "mods/extra.jar").write_bytes(b"extra")
            with self.assertRaises(SmokeError):
                matrix.check_mods(game, artifact, jei)

    def test_runtime_json_is_bounded_and_requires_object(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "metadata.json"
            for value in ("[]", "", "x" * (4 * 1024 * 1024 + 1)):
                path.write_text(value)
                with self.assertRaises(SmokeError):
                    runtime.read_json(path)

    def test_profile_rejects_userdev_or_unpinned_version(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            vanilla = root / "versions/1.20.1/1.20.1.json"
            patched = root / "versions/1.20.1-forge-47.4.10/1.20.1-forge-47.4.10.json"
            vanilla.parent.mkdir(parents=True)
            patched.parent.mkdir(parents=True)
            vanilla.write_text(json.dumps({"id": "1.20.1"}))
            patched.write_text(json.dumps({"id": "1.20.1-forge-47.4.10", "inheritsFrom": "1.20.1",
                                           "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher",
                                           "arguments": {"game": ["--launchTarget", "forgeclientuserdev"]}}))
            for forge in ("47.4.10", "47.4.22"):
                with self.assertRaises(SmokeError):
                    runtime.profile(root, forge)

    def client_fixture(self, root):
        vanilla = {"libraries": [], "assetIndex": {"id": "5"}, "arguments": {
            "jvm": ["-cp", "${classpath}", "-Djava.library.path=${natives_directory}"],
            "game": ["--username", "${auth_player_name}", "--accessToken", "${auth_access_token}"]}}
        for name, entry, data in (("test-natives-windows.jar", "windows/x64/org/lwjgl/test/test.dll", b"x64"),
                                  ("test-natives-windows-x86.jar", "test.dll", b"x86"),
                                  ("jna.jar", "nested/other.dll", b"self-extracting")):
            path = root / "libraries" / name
            path.parent.mkdir(exist_ok=True)
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr(entry, data)
            vanilla["libraries"].append({"name": f"test:{name}:1", "downloads": {"artifact": {
                "path": name, "sha1": hashlib.sha1(path.read_bytes()).hexdigest(), "size": path.stat().st_size}}})
        jar = root / "versions/1.20.1/1.20.1.jar"
        jar.parent.mkdir(parents=True)
        jar.write_bytes(b"minecraft")
        vanilla["downloads"] = {"client": {"sha1": hashlib.sha1(jar.read_bytes()).hexdigest(), "size": jar.stat().st_size}}
        patched = {"id": "1.20.1-forge-47.4.10", "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher",
                   "libraries": [], "arguments": {"jvm": [], "game": ["--launchTarget", "forgeclient"]}}
        game = root / "game"
        game.mkdir()
        return vanilla, patched, game

    def test_production_command_uses_dummy_session_and_only_extracts_x64_natives(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            vanilla, forge, game = self.client_fixture(root)
            with patch.object(runtime, "profile", return_value=(vanilla, forge)):
                args = runtime.client_command(root, game, "47.4.10", "Example", 25614, "java")
            self.assertEqual("forgeclient", args[args.index("--launchTarget") + 1])
            self.assertEqual("0", args[args.index("--accessToken") + 1])
            self.assertEqual("127.0.0.1:25614", args[-1])
            classpath = args[args.index("-cp") + 1]
            self.assertIn(str(game / "1.20.1-forge-47.4.10.jar"), classpath)
            self.assertNotIn(str(root / "versions/1.20.1/1.20.1.jar"), classpath)
            self.assertEqual(b"minecraft", (game / "1.20.1-forge-47.4.10.jar").read_bytes())
            self.assertEqual(b"x64", (game / "natives/test.dll").read_bytes())
            self.assertEqual(["test.dll"], [p.name for p in (game / "natives").iterdir()])

    def test_native_or_classpath_hash_drift_prevents_launch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            vanilla, forge, game = self.client_fixture(root)
            (root / "libraries/test-natives-windows.jar").write_bytes(b"changed")
            with patch.object(runtime, "profile", return_value=(vanilla, forge)), self.assertRaises(SmokeError):
                runtime.client_command(root, game, "47.4.10", "Example", 25614, "java")

    def test_native_subdirectory_is_not_extracted(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            vanilla, forge, game = self.client_fixture(root)
            path = root / "libraries/test-natives-windows.jar"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("../outside.dll", b"escape")
            vanilla["libraries"][0]["downloads"]["artifact"].update(
                {"sha1": hashlib.sha1(path.read_bytes()).hexdigest(), "size": path.stat().st_size})
            with patch.object(runtime, "profile", return_value=(vanilla, forge)), self.assertRaises(SmokeError):
                runtime.client_command(root, game, "47.4.10", "Example", 25614, "java")
            self.assertFalse((game / "outside.dll").exists())


if __name__ == "__main__":
    unittest.main()
