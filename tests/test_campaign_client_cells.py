import shutil
import unittest

from scripts.campaign_client.cells import check_game, load_cell, read_object
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


if __name__ == "__main__":
    unittest.main()
