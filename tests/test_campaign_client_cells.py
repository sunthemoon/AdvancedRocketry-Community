import copy
import os
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.campaign_client.cells import check_game, is_jei, load_cell, read_object
from scripts.campaign_client.evidence import sha256
from test_campaign_client_support import Fixture


class CellTests(Fixture):
    def test_both_forge_lanes(self):
        for forge in ("47.4.10", "47.4.23"):
            self.cell_spec.update(forge=forge, version_id=f"1.20.1-forge-{forge}")
            self.dump_cell()
            self.assertEqual(forge, load_cell(self.cell_path)["forge"])

    def test_jei_present_and_absent(self):
        jei = self.make_jar(self.root / "jei.jar", "jei")
        self.cell_spec.update(jei=True, jei_jar={"path": str(jei), "sha256": sha256(jei)})
        self.dump_cell()
        with self.assertRaises(ValueError):
            load_cell(self.cell_path)
        shutil.copyfile(jei, self.game / "mods/renamed.jar")
        self.assertTrue(load_cell(self.cell_path)["jei"])
        self.cell_spec.update(jei=False, jei_jar=None)
        self.dump_cell()
        with self.assertRaisesRegex(ValueError, "JEI present"):
            load_cell(self.cell_path)

    def test_wrong_jei_hash(self):
        jei = self.make_jar(self.root / "jei.jar", "jei")
        self.cell_spec.update(jei=True, jei_jar={"path": str(jei), "sha256": sha256(jei)})
        self.make_jar(self.game / "mods/jei.jar", "jei-different")
        self.dump_cell()
        with self.assertRaisesRegex(ValueError, "JEI SHA"):
            load_cell(self.cell_path)

    def test_wrong_candidate_each_location(self):
        for directory in (self.server, self.game):
            target = directory / "mods/candidate.jar"
            self.make_jar(target, "wrong-mod")
            with self.assertRaisesRegex(ValueError, "candidate SHA"):
                load_cell(self.cell_path)
            shutil.copyfile(self.jar, target)

    def test_declared_artifact_wrong_hash(self):
        self.cell_spec["candidate"]["sha256"] = "0" * 64
        self.dump_cell()
        with self.assertRaisesRegex(ValueError, "artifact hash"):
            load_cell(self.cell_path)

    def test_candidate_duplicate_refused(self):
        shutil.copyfile(self.jar, self.game / "mods/duplicate.jar")
        with self.assertRaises(ValueError):
            load_cell(self.cell_path)

    def test_attached_directory_is_checked_independently(self):
        cell = load_cell(self.cell_path)
        (self.game / "mods/candidate.jar").unlink()
        with self.assertRaises(ValueError):
            check_game(cell, self.game)

    def test_schema_and_version_mismatch(self):
        for key, value in (("schema_version", 2), ("jei", 1), ("version_id", "wrong"), ("forge", "47.4.22")):
            old = self.cell_spec[key]
            self.cell_spec[key] = value
            self.dump_cell()
            with self.subTest(key=key), self.assertRaises(ValueError):
                load_cell(self.cell_path)
            self.cell_spec[key] = old

    def test_json_bound_duplicates_and_nonfinite(self):
        for raw in (b" " * (256 * 1024 + 1), b'{"a":1,"a":2}', b'{"a":NaN}', b'[]'):
            self.cell_path.write_bytes(raw)
            with self.assertRaises(ValueError):
                read_object(self.cell_path)


class ExtendedCellTests(Fixture):
    def setUp(self):
        super().setUp()
        self.args = self.server / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        self.cell_spec["candidate"] = self.file_record(self.game / "mods/candidate.jar")
        self.cell_spec["mods"] = {
            side: [self.file_record(game / "mods/candidate.jar")]
            for side, game in (("server", self.server), ("client", self.game))}
        self.cell_spec["server_forge_args"] = self.file_record(self.args)
        self.dump_cell()

    @staticmethod
    def file_record(path):
        return {"path": str(path), "sha256": sha256(path)}

    def test_extended_manifest_loads_and_resolves_records(self):
        cell = load_cell(self.cell_path)
        self.assertEqual(self.args.resolve(), cell["server_forge_args"]["path"])
        for side, game in (("server", self.server), ("client", self.game)):
            self.assertEqual((game / "mods/candidate.jar").resolve(), cell["mods"][side][0]["path"])

    def test_both_extended_forge_and_jei_lanes(self):
        jei = self.make_jar(self.game / "mods/recipe-viewer.jar", "jei")
        original = copy.deepcopy(self.cell_spec)
        for forge in ("47.4.10", "47.4.23"):
            for enabled in (False, True):
                with self.subTest(forge=forge, jei=enabled):
                    self.cell_spec = copy.deepcopy(original)
                    self.cell_spec.update(forge=forge, version_id=f"1.20.1-forge-{forge}", jei=enabled)
                    args = self.server / f"libraries/net/minecraftforge/forge/1.20.1-{forge}/win_args.txt"
                    args.parent.mkdir(parents=True, exist_ok=True)
                    args.write_text("fixture", encoding="utf-8")
                    self.cell_spec["server_forge_args"] = self.file_record(args)
                    if enabled:
                        self.make_jar(jei, "jei")
                        self.cell_spec["jei_jar"] = self.file_record(jei)
                        self.cell_spec["mods"]["client"].append(self.file_record(jei))
                    elif jei.exists():
                        jei.unlink()
                    self.dump_cell()
                    cell = load_cell(self.cell_path)
                    self.assertEqual(forge, cell["forge"])
                    self.assertEqual(enabled, cell["jei"])

    def test_relative_paths_and_uppercase_hashes(self):
        for record in (self.cell_spec["candidate"], self.cell_spec["server_forge_args"],
                       *self.cell_spec["mods"]["server"], *self.cell_spec["mods"]["client"]):
            record["path"] = Path(record["path"]).relative_to(self.root).as_posix()
            record["sha256"] = record["sha256"].upper()
        self.dump_cell()
        cell = load_cell(self.cell_path)
        self.assertEqual(self.args.resolve(), cell["server_forge_args"]["path"])
        self.assertEqual(sha256(self.args), cell["server_forge_args"]["sha256"])

    def test_unknown_incomplete_or_wrongly_typed_schema_refused(self):
        original = copy.deepcopy(self.cell_spec)
        cases = [dict(original, schema_version=v) for v in (True, "1", 2, None)]
        cases.append(dict(original, unexpected=True))
        cases.extend({k: v for k, v in original.items() if k != missing}
                     for missing in ("mods", "server_forge_args", "candidate"))
        for value in cases:
            with self.subTest(value=value):
                self.cell_spec = value
                self.dump_cell()
                with self.assertRaises(ValueError):
                    load_cell(self.cell_path)

    def test_malformed_mod_lists_refused(self):
        for value in (None, [], {}, {"server": []}, {"server": [], "client": []},
                      {"server": {}, "client": []}, {"server": [None], "client": []},
                      {"server": self.cell_spec["mods"]["server"] * 257,
                       "client": self.cell_spec["mods"]["client"]}):
            with self.subTest(value=value):
                old = self.cell_spec["mods"]
                self.cell_spec["mods"] = value
                self.dump_cell()
                with self.assertRaises(ValueError):
                    load_cell(self.cell_path)
                self.cell_spec["mods"] = old

    def test_malformed_artifact_records_refused(self):
        original = copy.deepcopy(self.cell_spec)
        for value in (None, [], {}, {"path": "a"}, {"path": "a", "sha256": 1},
                      {"path": "a", "sha256": "invalid"},
                      {"path": None, "sha256": "0" * 64},
                      dict(self.file_record(self.args), unexpected=True)):
            for location in ("server_forge_args", "server", "client"):
                with self.subTest(location=location, value=value):
                    self.cell_spec = copy.deepcopy(original)
                    if location == "server_forge_args":
                        self.cell_spec[location] = value
                    else:
                        self.cell_spec["mods"][location] = [value]
                    self.dump_cell()
                    with self.assertRaises(ValueError):
                        load_cell(self.cell_path)

    def test_missing_or_changed_listed_mod_refused(self):
        for side, game in (("server", self.server), ("client", self.game)):
            target = game / "mods/candidate.jar"
            for change in ("missing", "hash"):
                with self.subTest(side=side, change=change):
                    if change == "missing":
                        target.unlink()
                    else:
                        target.write_bytes(b"changed")
                    with self.assertRaises(ValueError):
                        load_cell(self.cell_path)
                    shutil.copyfile(self.jar, target)

    def test_additional_mod_hash_is_checked(self):
        extra = self.make_jar(self.game / "mods/extra.jar", "extra")
        self.cell_spec["mods"]["client"].append(self.file_record(extra))
        self.dump_cell()
        self.assertEqual(2, len(load_cell(self.cell_path)["mods"]["client"]))
        extra.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "artifact hash"):
            load_cell(self.cell_path)

    def test_extended_candidate_and_jei_must_be_listed_client_files(self):
        self.cell_spec["candidate"] = self.file_record(self.jar)
        self.dump_cell()
        with self.assertRaisesRegex(ValueError, "missing from cell mods"):
            load_cell(self.cell_path)
        self.cell_spec["candidate"] = self.file_record(self.game / "mods/candidate.jar")
        jei = self.make_jar(self.root / "unlisted-jei.jar", "jei")
        self.cell_spec.update(jei=True, jei_jar=self.file_record(jei))
        self.dump_cell()
        with self.assertRaisesRegex(ValueError, "missing from cell mods"):
            load_cell(self.cell_path)

    def test_missing_or_changed_forge_arguments_refused(self):
        self.args.unlink()
        with self.assertRaises(ValueError):
            load_cell(self.cell_path)
        self.args.write_text("changed", encoding="utf-8")
        with self.assertRaises(ValueError):
            load_cell(self.cell_path)

    def test_other_file_cannot_replace_selected_forge_arguments(self):
        for path in (self.jar, self.server / "libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt"):
            with self.subTest(path=path):
                path.parent.mkdir(parents=True, exist_ok=True)
                if not path.exists():
                    path.write_text("fixture", encoding="utf-8")
                self.cell_spec["server_forge_args"] = self.file_record(path)
                self.dump_cell()
                with self.assertRaises(ValueError):
                    load_cell(self.cell_path)

    def test_duplicate_or_unlisted_mod_refused(self):
        for side, game in (("server", self.server), ("client", self.game)):
            with self.subTest(side=side, change="duplicate record"):
                self.cell_spec["mods"][side] *= 2
                self.dump_cell()
                with self.assertRaises(ValueError):
                    load_cell(self.cell_path)
                self.cell_spec["mods"][side] = self.cell_spec["mods"][side][:1]
                self.dump_cell()
            with self.subTest(side=side, change="unlisted jar"):
                extra = self.make_jar(game / "mods/extra.jar", "extra")
                with self.assertRaises(ValueError):
                    load_cell(self.cell_path)
                extra.unlink()

    def test_mod_cannot_be_listed_under_wrong_side_or_directory(self):
        for path in (self.jar, self.game / "mods/candidate.jar"):
            with self.subTest(path=path):
                self.cell_spec["mods"]["server"] = [self.file_record(path)]
                self.dump_cell()
                with self.assertRaises(ValueError):
                    load_cell(self.cell_path)

    def test_unlisted_mod_alias_refused(self):
        extra = self.make_jar(self.game / "mods/extra.jar", "extra")
        self.cell_spec["mods"]["client"].append(self.file_record(extra))
        self.dump_cell()
        self.assertEqual(2, len(load_cell(self.cell_path)["mods"]["client"]))
        try:
            (self.game / "mods/alias.jar").symlink_to(extra)
        except (OSError, NotImplementedError):
            self.skipTest("Symlinks are unavailable on this host")
        with self.assertRaisesRegex(ValueError, "mod list does not match"):
            load_cell(self.cell_path)

    def test_artifacts_and_directories_must_stay_in_cell(self):
        original = copy.deepcopy(self.cell_spec)
        with tempfile.TemporaryDirectory(prefix="w428-outside-") as temp:
            outside = Path(temp)
            shutil.copytree(self.server, outside / "server")
            shutil.copytree(self.game, outside / "client")
            jar = outside / "client/mods/candidate.jar"
            args = outside / "server/libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
            for key in ("server_dir", "client_game_dir", "candidate", "mods", "server_forge_args"):
                for relative in (False, True):
                    with self.subTest(key=key, relative=relative):
                        self.cell_spec = copy.deepcopy(original)
                        path = {"server_dir": outside / "server", "client_game_dir": outside / "client",
                                "server_forge_args": args}.get(key, jar)
                        record = self.file_record(path) if path.is_file() else None
                        value = os.path.relpath(path, self.root) if relative else str(path)
                        if key in ("server_dir", "client_game_dir"):
                            self.cell_spec[key] = value
                        elif key == "mods":
                            self.cell_spec[key]["client"] = [dict(record, path=value)]
                        else:
                            self.cell_spec[key] = dict(record, path=value)
                        self.dump_cell()
                        with self.assertRaisesRegex(ValueError, "inside the cell"):
                            load_cell(self.cell_path)

    def test_symlink_escape_refused(self):
        link = self.game / "mods/linked.jar"
        with tempfile.TemporaryDirectory(prefix="w428-outside-") as temp:
            target = self.make_jar(Path(temp) / "outside.jar", "outside")
            try:
                link.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("Symlinks are unavailable on this host")
            self.cell_spec["mods"]["client"].append(self.file_record(link))
            self.dump_cell()
            with self.assertRaisesRegex(ValueError, "inside the cell"):
                load_cell(self.cell_path)


class JeiDeclarationTests(Fixture):
    def setUp(self):
        super().setUp()
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("META-INF/mods.toml", '''[[mods]]
modId = "advancedrocketrycommunity"
[[dependencies.advancedrocketrycommunity]]
modId = "jei"
mandatory = false
''')
        for game in (self.server, self.game):
            shutil.copyfile(self.jar, game / "mods/candidate.jar")
        self.cell_spec["candidate"]["sha256"] = sha256(self.jar)
        self.dump_cell()

    def test_optional_dependency_is_not_installed_jei(self):
        self.assertFalse(is_jei(self.jar))
        self.assertFalse(load_cell(self.cell_path)["jei"])

    def test_optional_dependency_alongside_real_renamed_jei(self):
        jei = self.make_jar(self.game / "mods/recipe-viewer.jar", "jei")
        self.cell_spec.update(jei=True, jei_jar={"path": str(jei), "sha256": sha256(jei)})
        self.dump_cell()
        self.assertTrue(is_jei(jei))
        self.assertTrue(load_cell(self.cell_path)["jei"])

    def test_real_jei_still_refused_in_nojei_cell(self):
        self.make_jar(self.game / "mods/recipe-viewer.jar", "jei")
        with self.assertRaisesRegex(ValueError, "JEI present"):
            load_cell(self.cell_path)

    def test_filename_is_not_a_mod_declaration(self):
        path = self.make_jar(self.root / "jei-helper.jar", "helper")
        self.assertFalse(is_jei(path))
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("README", "No mods declared")
        self.assertFalse(is_jei(path))

    def test_multiple_mods_inline_tables_and_single_quotes(self):
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("META-INF/mods.toml", "mods = [{modId = 'helper'}, {modId = 'jei'}]\n")
        self.assertTrue(is_jei(self.jar))

    def test_malformed_or_oversized_metadata_refused(self):
        for text in ("not toml", "[[dependencies.helper]]\nmodId='jei'", "mods=[]",
                     "mods=[{}]", "mods=[{modId=1}]", "mods=[{modId=[]}]", "mods=[{modId=''}]",
                     "[[mods]]\nmodId='helper'\n" * 257, "#" * (1024 * 1024 + 1)):
            with self.subTest(text=text[:60]):
                with zipfile.ZipFile(self.jar, "w") as archive:
                    archive.writestr("META-INF/mods.toml", text)
                with self.assertRaises(ValueError):
                    is_jei(self.jar)


REAL_CELLS = Path("D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells")


@unittest.skipUnless(REAL_CELLS.is_dir(), "Local W400 campaign cells are absent")
class RealCellTests(unittest.TestCase):
    def test_four_prepared_manifests(self):
        for forge in ("47.4.10", "47.4.23"):
            for jei in (False, True):
                name = f"{forge}-{'jei' if jei else 'nojei'}"
                with self.subTest(cell=name):
                    cell = load_cell(REAL_CELLS / name / "cell.json")
                    self.assertEqual(forge, cell["forge"])
                    self.assertEqual(jei, cell["jei"])
                    self.assertEqual(1, len(cell["mods"]["server"]))
                    self.assertEqual(1 + int(jei), len(cell["mods"]["client"]))
                    self.assertEqual(sha256(cell["server_forge_args"]["path"]),
                                     cell["server_forge_args"]["sha256"])


if __name__ == "__main__":
    unittest.main()
