"""Pure synthetic baseline identity/launch tests, including on Linux; no live processes."""
import copy
import hashlib
import io
import json
import shutil
import unittest
import zipfile
from dataclasses import asdict
from unittest.mock import Mock, patch

from scripts import loader_cut_nbt as nbt
from scripts.campaign_client import baseline as b
from scripts.campaign_client.baseline_launch import prepare_baseline
from scripts.campaign_client.baseline_world import create_world
from scripts.campaign_client.cells import load_cell
from scripts.campaign_client.cli import main
from scripts.campaign_client.evidence import sha256
from scripts.campaign_client.protocol import load_session
from scripts.campaign_client.session import Session, run_session
from scripts.v100_compatibility_runtime import SmokeError
from test_campaign_client_support import FakeWindows, Fixture
from test_campaign_client_session import FakeProcess


class BaselineTests(Fixture):
    def setUp(self):
        super().setUp()
        self.base = self.root / "baseline-profile"
        self.runtime = self.base / "runtime"
        self.template = self.base / "client"
        for name in b.EMPTY_DIRS:
            (self.template / name).mkdir(parents=True)
        self.libs, self.assets = self.root / "libraries", self.root / "assets"
        self.libs.mkdir()
        (self.assets / "indexes").mkdir(parents=True)
        index = self.assets / "indexes/5.json"
        index.write_text('{"objects":{}}', encoding="utf-8")
        lib = self.libs / "test.jar"
        lib.write_bytes(b"test library")
        self.vanilla = {"id": "1.20.1", "mainClass": "net.minecraft.client.main.Main",
            "assetIndex": {"id": "5", "size": index.stat().st_size, "sha1": self.sha1(index)},
            "libraries": [{"name": "test:test:1", "downloads": {"artifact": {
                "path": "test.jar", "size": lib.stat().st_size, "sha1": self.sha1(lib)}}}],
            "arguments": {"jvm": ["-cp", "${classpath}", "-Djava.library.path=${natives_directory}"],
                          "game": ["--username", "${auth_player_name}", "--gameDir", "${game_directory}",
                                   "--accessToken", "${auth_access_token}", "--uuid", "${auth_uuid}"]},
            "downloads": {"client": {"sha1": hashlib.sha1(b"client").hexdigest(), "size": 6}}}
        version = self.runtime / "versions/1.20.1"
        version.mkdir(parents=True)
        (version / "1.20.1.jar").write_bytes(b"client")
        (version / "1.20.1.json").write_text(json.dumps(self.vanilla), encoding="utf-8")
        self.pins = {f.relative_to(self.runtime).as_posix(): sha256(f) for f in version.iterdir()}
        self.enterContext(patch.dict(b.VERSION_FILES, self.pins, clear=True))
        self.manifest = self.base / "baseline.json"
        self.raw = {"schema_version": 1, "kind": "baseline", "profile": b.VANILLA,
            "runtime_root": "runtime", "libraries_root": str(self.libs), "assets_root": str(self.assets),
            "client_game_dir": "client", "version_files": [self.record(self.runtime / p) for p in self.pins],
            "mods": {"path": "client/mods", "entries": [], "sha256": b.EMPTY_MODS_SHA256}}
        self.dump_baseline()
        client = {key: self.attach_spec[key] for key in ("player_name", "language", "gui_scale", "window_size")}
        self.settings.update({"online-mode": False, "white-list": False, "enforce-whitelist": False})
        self.session_spec.update(mode="launch", launch_kind="baseline", singleplayer=True,
                                 cell=str(self.manifest), clients=[client])

    @staticmethod
    def sha1(path):
        return hashlib.sha1(path.read_bytes()).hexdigest()

    def record(self, path):
        return {"path": path.relative_to(self.base).as_posix(), "sha256": sha256(path)}

    def dump_baseline(self):
        self.manifest.write_text(json.dumps(self.raw), encoding="utf-8")

    def forge(self):
        patched = {"id": "1.20.1-forge-47.4.10", "inheritsFrom": "1.20.1",
            "mainClass": "cpw.mods.bootstraplauncher.BootstrapLauncher", "libraries": [],
            "arguments": {"jvm": [], "game": ["--launchTarget", "forgeclient", "--fml.forgeVersion", "47.4.10"]}}
        path = self.runtime / b.FORGE_VERSION
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps(patched), encoding="utf-8")
        installer = self.runtime / "forge-47.4.10-installer.jar"
        outputs = {}
        for relative, key in (
            ("net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-client.jar", "PATCHED_SHA"),
            ("net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-extra.jar", "MC_EXTRA_SHA")):
            file = self.libs / relative
            file.parent.mkdir(parents=True)
            file.write_bytes(key.encode("ascii"))
            outputs[key] = {"client": "'" + self.sha1(file) + "'"}
        with zipfile.ZipFile(installer, "w") as archive:
            archive.writestr("version.json", json.dumps(patched))
            archive.writestr("install_profile.json", json.dumps({"libraries": [], "data": outputs}))
        self.enterContext(patch.object(b, "FORGE_PROFILE_SHA256", sha256(path)))
        self.enterContext(patch("scripts.v100_compatibility_runtime.MINECRAFT_PROFILE_SHA1",
                                self.sha1(self.runtime / "versions/1.20.1/1.20.1.json")))
        self.enterContext(patch.dict("scripts.v100_compatibility_runtime.FORGE_HASHES",
                                     {"47.4.10": self.sha1(installer)}, clear=True))
        self.raw["profile"] = b.FORGE
        self.raw["version_files"] += [self.record(path), self.record(installer)]
        self.dump_baseline()

    def test_vanilla_identity_is_candidate_free_and_hash_pinned(self):
        cell = b.load_baseline(self.manifest)
        self.assertIsNone(cell["candidate"])
        self.assertIsNone(cell["forge"])
        self.assertFalse(cell["jei"])
        self.assertEqual("1.20.1", cell["version_id"])
        identity = cell["baseline_identity"]
        self.assertEqual(sha256(self.manifest), identity["manifest_sha256"])
        self.assertEqual(self.raw["mods"], identity["mods"])
        self.assertEqual(set(self.pins.values()), {r["sha256"] for r in identity["version_files"]})

    def test_empty_forge_identity_checks_installer_and_version(self):
        self.forge()
        cell = b.load_baseline(self.manifest)
        self.assertEqual("47.4.10", cell["forge"])
        self.assertIsNone(cell["candidate"])
        self.assertFalse(cell["jei"])
        self.assertEqual(4, len(cell["baseline_identity"]["version_files"]))

    def test_unapproved_profiles_and_candidate_fields_are_refused(self):
        for key, value in (("profile", "FORGE_47_4_22_EMPTY"), ("profile", "FORGE_47_4_23_EMPTY"),
                           ("profile", "FORGE_47_4_10_JEI_EMPTY"), ("profile", "C6"),
                           ("schema_version", True), ("schema_version", 2), ("kind", "candidate"),
                           ("candidate", None), ("jei", False), ("unexpected", True)):
            with self.subTest(key=key, value=value):
                original = copy.deepcopy(self.raw)
                self.raw[key] = value
                self.dump_baseline()
                with self.assertRaises(ValueError):
                    b.load_baseline(self.manifest)
                self.raw = original

    def test_missing_and_malformed_manifest_fields_are_refused(self):
        original = copy.deepcopy(self.raw)
        for key in original:
            with self.subTest(key=key):
                self.raw = {k: v for k, v in original.items() if k != key}
                self.dump_baseline()
                with self.assertRaises(ValueError):
                    b.load_baseline(self.manifest)

    def test_any_nonempty_template_directory_is_refused(self):
        for directory in b.EMPTY_DIRS:
            for name in ("candidate.jar", "renamed-jei.jar", ".hidden", "not-a-jar.txt"):
                with self.subTest(directory=directory, name=name):
                    file = self.template / directory / name
                    file.write_bytes(b"unexpected")
                    with self.assertRaisesRegex(ValueError, "must be empty"):
                        b.load_baseline(self.manifest)
                    file.unlink()
        subdir = self.template / "mods/empty-child"
        subdir.mkdir()
        with self.assertRaisesRegex(ValueError, "must be empty"):
            b.load_baseline(self.manifest)

    def test_missing_mods_and_owner_files_are_refused(self):
        (self.template / "mods").rmdir()
        with self.assertRaises(ValueError):
            b.load_baseline(self.manifest)
        (self.template / "mods").mkdir()
        (self.template / "launcher_accounts.json").write_text("{}")
        with self.assertRaises(ValueError):
            b.load_baseline(self.manifest)

    def test_mods_identity_cannot_be_relabelled(self):
        original = copy.deepcopy(self.raw)
        for value in ({}, None, {"path": "client/mods", "entries": ["a.jar"], "sha256": b.EMPTY_MODS_SHA256},
                      dict(original["mods"], sha256="0" * 64), dict(original["mods"], path="client/saves")):
            self.raw["mods"] = value
            self.dump_baseline()
            with self.subTest(value=value), self.assertRaises(ValueError):
                b.load_baseline(self.manifest)

    def test_version_change_refused_even_with_updated_declared_hash(self):
        for entry in self.raw["version_files"]:
            file = self.base / entry["path"]
            original, digest = file.read_bytes(), entry["sha256"]
            for update in (False, True):
                with self.subTest(file=file.name, update=update):
                    file.write_bytes(b"different version")
                    if update:
                        entry["sha256"] = sha256(file)
                    self.dump_baseline()
                    with self.assertRaisesRegex(ValueError, "hash mismatch"):
                        b.load_baseline(self.manifest)
            file.write_bytes(original)
            entry["sha256"] = digest

    def test_duplicate_missing_extra_or_misplaced_version_files_refused(self):
        original = copy.deepcopy(self.raw["version_files"])
        for entries in (original[:1], original * 2, original[:1] * 2,
                        [dict(original[0], path="../other.json"), original[1]], None):
            self.raw["version_files"] = entries
            self.dump_baseline()
            with self.subTest(entries=entries), self.assertRaises(ValueError):
                b.load_baseline(self.manifest)

    def test_undeclared_runtime_files_and_directories_are_refused(self):
        for name in ("launcher_profiles.json", "accounts.json", "extra.jar"):
            file = self.runtime / name
            file.write_bytes(b"unexpected")
            with self.assertRaisesRegex(ValueError, "Unexpected baseline runtime"):
                b.load_baseline(self.manifest)
            file.unlink()
        (self.runtime / "extra-version").mkdir()
        with self.assertRaisesRegex(ValueError, "runtime directory"):
            b.load_baseline(self.manifest)

    def test_template_and_versions_cannot_escape_profile(self):
        for key in ("runtime_root", "client_game_dir"):
            original = self.raw[key]
            self.raw[key] = str(self.root)
            self.dump_baseline()
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, "inside the profile"):
                b.load_baseline(self.manifest)
            self.raw[key] = original

    def test_symlinked_empty_mods_is_refused(self):
        mods = self.template / "mods"
        mods.rmdir()
        target = self.base / "other-mods"
        target.mkdir()
        try:
            mods.symlink_to(target, target_is_directory=True)
        except (OSError, NotImplementedError):
            self.skipTest("Symlinks unavailable")
        with self.assertRaisesRegex(SmokeError, "Linked"):
            b.load_baseline(self.manifest)

    def test_symlinked_declared_version_file_is_refused(self):
        file = self.runtime / "versions/1.20.1/1.20.1.jar"
        target = self.base / "other.jar"
        shutil.move(file, target)
        try:
            file.symlink_to(target)
        except (OSError, NotImplementedError):
            self.skipTest("Symlinks unavailable")
        with self.assertRaisesRegex(SmokeError, "Linked"):
            b.load_baseline(self.manifest)

    def test_installer_tampering_not_authorized_by_manifest(self):
        self.forge()
        installer = self.runtime / "forge-47.4.10-installer.jar"
        installer.write_bytes(b"changed installer")
        self.raw["version_files"][-1] = self.record(installer)
        self.dump_baseline()
        with self.assertRaisesRegex(SmokeError, "SHA-1 mismatch"):
            b.load_baseline(self.manifest)

    def test_shared_library_and_asset_index_tampering_refused(self):
        for file in (self.libs / "test.jar", self.assets / "indexes/5.json"):
            original = file.read_bytes()
            file.write_bytes(b"wrong")
            with self.subTest(file=file), self.assertRaises(SmokeError):
                b.load_baseline(self.manifest)
            file.write_bytes(original)

    def test_missing_or_corrupt_forge_processed_output_is_refused(self):
        self.forge()
        file = self.libs / "net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-client.jar"
        original = file.read_bytes()
        file.unlink()
        with self.assertRaisesRegex(SmokeError, "missing"):
            b.load_baseline(self.manifest)
        file.write_bytes(b"wrong output")
        with self.assertRaises(SmokeError):
            b.load_baseline(self.manifest)
        file.write_bytes(original)
        self.assertEqual(2, len(b.load_baseline(self.manifest)["baseline_identity"]["forge_runtime_files"]))

    def test_baseline_session_is_only_singleplayer_launch(self):
        self.assertEqual("baseline", load_session(self.dump_session())["launch_kind"])
        original = copy.deepcopy(self.session_spec)
        for key, value in (("singleplayer", False), ("mode", "attach"), ("launch_kind", "empty"),
                           ("launch_kind", None), ("launch_kind", 1)):
            self.session_spec = copy.deepcopy(original)
            self.session_spec[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                load_session(self.dump_session())

    def test_baseline_and_candidate_manifests_are_not_interchangeable(self):
        with self.assertRaises(ValueError):
            load_cell(self.manifest)
        with self.assertRaises(ValueError):
            b.load_baseline(self.cell_path)
        self.session_spec.pop("launch_kind")
        with patch("sys.stderr", new_callable=io.StringIO):
            self.assertEqual(1, main(["check", str(self.dump_session())]))

    def test_check_is_offline_and_does_not_probe_or_spawn(self):
        with patch("subprocess.Popen") as spawn, patch("scripts.campaign_client.session.require_windows") as probe, \
                patch("sys.stdout", new_callable=io.StringIO):
            self.assertEqual(0, main(["check", str(self.dump_session())]))
        spawn.assert_not_called()
        probe.assert_not_called()
        self.assertFalse((self.root / "run").exists())

    def test_invalid_profile_refused_before_platform_lock_or_spawn(self):
        (self.template / "mods/anything").write_bytes(b"bad")
        with patch("scripts.campaign_client.session.require_windows") as probe, \
                patch("scripts.campaign_client.session.CapturedProcess") as spawn:
            with self.assertRaises(ValueError):
                run_session(self.dump_session(), self.root / "lock")
        probe.assert_not_called()
        spawn.assert_not_called()
        self.assertFalse((self.root / "lock").exists())

    def test_new_world_contains_only_vanilla_metadata_and_no_imported_chunks(self):
        game = self.root / "new-world"
        game.mkdir()
        world = create_world(game)
        data = nbt.plain(nbt.read_gzip(world / "level.dat")[1])["Data"]
        self.assertEqual(["level.dat"], [p.name for p in world.iterdir()])
        self.assertEqual(3465, data["DataVersion"])
        self.assertEqual(["vanilla"], data["DataPacks"]["Enabled"])
        dimensions = data["WorldGenSettings"]["dimensions"]
        self.assertEqual({"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}, set(dimensions))
        self.assertEqual("minecraft:flat", dimensions["minecraft:overworld"]["generator"]["type"])
        self.assertEqual(0, data["WorldGenSettings"]["seed"])
        with self.assertRaises(FileExistsError):
            create_world(game)

    def test_both_commands_quickplay_fresh_world_without_candidate_or_account(self):
        for forge in (False, True):
            if forge:
                self.forge()
            cell = b.load_baseline(self.manifest)
            spec = load_session(self.dump_session())["clients"][0]
            game = self.root / ("forge-run" if forge else "vanilla-run")
            with patch("subprocess.Popen") as spawn:
                command = prepare_baseline(cell, spec, game, "java.exe")
            spawn.assert_not_called()
            self.assertEqual("baseline", command[command.index("--quickPlaySingleplayer") + 1])
            self.assertNotIn("--quickPlayMultiplayer", command)
            self.assertEqual("0", command[command.index("--accessToken") + 1])
            self.assertEqual("1280", command[command.index("--width") + 1])
            self.assertIn("cpw.mods.bootstraplauncher.BootstrapLauncher" if forge else "net.minecraft.client.main.Main", command)
            self.assertTrue((game / "saves/baseline/level.dat").is_file())
            self.assertEqual([], list((game / "mods").iterdir()))
            self.assertEqual([], list((self.template / "saves").iterdir()))
            self.assertNotIn("candidate", " ".join(command))
            self.assertIn("pauseOnLostFocus:false", (game / "options.txt").read_text())
            with self.assertRaises(FileExistsError):
                prepare_baseline(cell, spec, game, "java.exe")

    def test_recheck_refuses_input_change_before_any_output(self):
        cell = b.load_baseline(self.manifest)
        (self.template / "mods/late.jar").write_bytes(b"late")
        game = self.root / "not-created"
        with self.assertRaises(ValueError):
            prepare_baseline(cell, self.session_spec["clients"][0], game, "java.exe")
        self.assertFalse(game.exists())

    def add_native(self, entries):
        file = self.libs / "test-natives-windows.jar"
        with zipfile.ZipFile(file, "w") as archive:
            for name, data in entries:
                archive.writestr(name, data)
        self.vanilla["libraries"].append({"name": "test:native:1", "downloads": {"artifact": {
            "path": file.name, "sha1": self.sha1(file), "size": file.stat().st_size}}})
        version = self.runtime / "versions/1.20.1/1.20.1.json"
        version.write_text(json.dumps(self.vanilla), encoding="utf-8")
        b.VERSION_FILES[version.relative_to(self.runtime).as_posix()] = sha256(version)
        for entry in self.raw["version_files"]:
            if entry["path"].endswith(".json"):
                entry["sha256"] = sha256(version)
        self.dump_baseline()

    def test_native_extraction_accepts_only_bounded_dll_paths(self):
        self.add_native([("windows/x64/org/lwjgl/glfw/glfw.dll", b"dll bytes"), ("META-INF/README", b"ignored")])
        game = self.root / "native-game"
        prepare_baseline(b.load_baseline(self.manifest), self.session_spec["clients"][0], game, "java.exe")
        self.assertEqual(b"dll bytes", (game / "natives/glfw.dll").read_bytes())
        self.assertEqual(["glfw.dll"], [p.name for p in (game / "natives").iterdir()])

    def test_native_path_traversal_is_refused(self):
        self.add_native([("../escape.dll", b"dll")])
        with self.assertRaisesRegex(ValueError, "Unexpected native"):
            prepare_baseline(b.load_baseline(self.manifest), self.session_spec["clients"][0],
                             self.root / "native-game", "java.exe")
        self.assertFalse((self.root / "escape.dll").exists())

    def test_oversized_native_entry_count_is_refused(self):
        self.add_native([(f"entry-{i}", b"x") for i in range(257)])
        with self.assertRaisesRegex(ValueError, "entry count"):
            prepare_baseline(b.load_baseline(self.manifest), self.session_spec["clients"][0],
                             self.root / "native-game", "java.exe")

    def test_baseline_run_cannot_overlap_shared_dependencies(self):
        for source in (self.libs, self.assets, self.runtime, self.template):
            self.session_spec["run_dir"] = str(source / "run")
            spec = load_session(self.dump_session())
            session = Session(spec, b.load_baseline(self.manifest), FakeWindows())
            with self.subTest(source=source), patch("scripts.campaign_client.session.CapturedProcess") as spawn:
                with self.assertRaisesRegex(ValueError, "overlap"):
                    session.setup()
                spawn.assert_not_called()

    def test_recheck_refuses_changed_manifest_even_when_still_valid(self):
        cell = b.load_baseline(self.manifest)
        self.manifest.write_text(json.dumps(self.raw, indent=2), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "identity changed"):
            prepare_baseline(cell, self.session_spec["clients"][0], self.root / "not-created", "java.exe")

    def test_ready_receipt_process_binding_and_cleanup_match_candidate_lifecycle(self):
        spec = load_session(self.dump_session())
        cell = b.load_baseline(self.manifest)
        backend, proc = FakeWindows(), FakeProcess()
        session = Session(spec, cell, backend)
        with patch("scripts.campaign_client.session.CapturedProcess", return_value=proc) as spawn:
            session.setup()
            summary = json.loads((session.run / "summary.json").read_text())
            self.assertEqual("READY", summary["status"])
            self.assertEqual("launch", summary["mode"])
            self.assertIsNone(summary["candidate_sha256"])
            self.assertIsNone(summary["forge"])
            receipt = summary["clients"]["TesterOne"]
            self.assertEqual((123, 456), (receipt["pid"], receipt["window"]))
            self.assertEqual(asdict(backend.identity), receipt["process"])
            self.assertEqual(sha256(self.manifest), summary["baseline_identity"]["manifest_sha256"])
            self.assertIn("launched_at", receipt)
            self.assertEqual(1, spawn.call_count)
            self.assertIsNone(session.server)
            session.clients["TesterOne"]["input"].verify()
            backend.identity = None
            with self.assertRaises(ValueError):
                session.clients["TesterOne"]["input"].verify()
            backend.identity = FakeWindows().identity
            session.cleanup()
        self.assertTrue(proc.closed)
        self.assertFalse((session.run / "client-TesterOne").exists())
        self.assertTrue(self.template.is_dir())
        self.assertTrue(self.runtime.is_dir())

    def test_startup_failure_cleans_only_disposable_copy(self):
        session = Session(load_session(self.dump_session()), b.load_baseline(self.manifest), FakeWindows())
        with patch("scripts.campaign_client.session.CapturedProcess", side_effect=RuntimeError("synthetic")):
            with self.assertRaises(RuntimeError):
                session.execute()
        self.assertEqual("FAIL", session.summary["status"])
        self.assertFalse((session.run / "client-TesterOne").exists())
        b.check_empty_game(self.template)


if __name__ == "__main__":
    unittest.main()
