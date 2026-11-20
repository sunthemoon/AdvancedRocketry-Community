import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from scripts import prepare_campaign_cells as cells
from scripts.run_dedicated_server_smoke import SmokeError


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def snapshot(root):
    return {path.relative_to(root).as_posix(): path.read_bytes()
            for path in root.rglob("*") if path.is_file()}


class CampaignCellsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.runtime = self.base / "runtime"
        self.runtime.mkdir()
        self.output = self.base / "cells"
        self.candidate = self.base / "candidate.jar"
        self.candidate.write_bytes(b"candidate-one")
        self.expected = digest(self.candidate)
        (self.runtime / "jei.jar").write_bytes(b"fake-jei")
        self.write_json(self.runtime / "prepared.json", {
            "schema_version": 1, "forge": ["47.4.10", "47.4.23"], "jei": "15.56.0.205",
            "jei_sha256": digest(self.runtime / "jei.jar"), "asset_count": 0})
        # A launcher file is not an input. Malformed bytes would fail any JSON read.
        (self.runtime / "launcher_profiles.json").write_bytes(b"not a launcher input")
        for forge in ("47.4.10", "47.4.23"):
            version = f"1.20.1-forge-{forge}"
            self.write_json(self.runtime / "versions" / version / f"{version}.json",
                            {"id": version, "inheritsFrom": "1.20.1"})
            server = self.runtime / "servers" / forge
            args = server / cells.argument_name(forge)
            args.parent.mkdir(parents=True)
            args.write_bytes(f"fake arguments {forge}".encode())
            (server / "run.bat").write_bytes(b"do not run")
            (server / "user_jvm_args.txt").write_bytes(b"-Xmx1G")

    @staticmethod
    def write_json(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding="utf-8")

    def prepare(self, **kwargs):
        return cells.prepare_cells(self.runtime, self.candidate, self.expected, self.output, **kwargs)

    def make_link(self, link, target, *, junction=False):
        if junction:
            result = subprocess.run(["cmd.exe", "/c", "mklink", "/J", str(link), str(target)],
                                    capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        else:
            try:
                link.symlink_to(target, target_is_directory=target.is_dir())
            except OSError as exc:
                if os.name != "nt" or not target.is_dir():
                    self.skipTest(f"Symlink creation unavailable: {exc}")
                self.make_link(link, target, junction=True)
                return
        self.addCleanup(lambda: link.rmdir() if junction else link.unlink())

    def test_four_cells_fields_copies_and_all_hashes(self):
        before = snapshot(self.runtime)
        paths = self.prepare(accept_eula=True)
        self.assertEqual({"47.4.10-nojei", "47.4.10-jei", "47.4.23-nojei", "47.4.23-jei"},
                         {path.parent.name for path in paths})
        self.assertEqual(before, snapshot(self.runtime))
        self.assertEqual(self.expected, digest(self.candidate))
        for path in paths:
            with self.subTest(cell=path.parent.name):
                value = json.loads(path.read_text())
                cell = path.parent
                forge = cell.name.split("-")[0]
                jei = cell.name.endswith("-jei")
                self.assertEqual(1, value["schema_version"])
                self.assertEqual(forge, value["forge"])
                self.assertIs(jei, value["jei"])
                self.assertEqual(str(self.runtime), value["runtime_root"])
                self.assertEqual(str(cell / "server"), value["server_dir"])
                self.assertEqual(str(cell / "client"), value["client_game_dir"])
                self.assertEqual(f"1.20.1-forge-{forge}", value["version_id"])
                self.assertEqual({"path": str(cell / "client/mods/candidate.jar"), "sha256": self.expected},
                                 value["candidate"])
                self.assertEqual("eula=true\n", (cell / "server/eula.txt").read_text())
                options = (cell / "client/options.txt").read_text()
                for line in ("lang:en_us", "guiScale:3", "soundCategory_master:1.0", "fullscreen:false",
                             "overrideWidth:1280", "overrideHeight:720"):
                    self.assertIn(line, options.splitlines())
                self.assertEqual({"mods", "options.txt"}, {p.name for p in (cell / "client").iterdir()})
                for side in ("client", "server"):
                    expected_names = {"candidate.jar", "jei.jar"} if side == "client" and jei else {"candidate.jar"}
                    self.assertEqual(expected_names, {p.name for p in (cell / side / "mods").iterdir()})
                    self.assertEqual(expected_names, {Path(r["path"]).name for r in value["mods"][side]})
                    for record in value["mods"][side]:
                        self.assertTrue(Path(record["path"]).is_relative_to(cell / side / "mods"))
                        self.assertEqual(digest(Path(record["path"])), record["sha256"])
                args = value["server_forge_args"]
                self.assertEqual(str(cell / "server" / cells.argument_name(forge)), args["path"])
                self.assertEqual(digest(Path(args["path"])), args["sha256"])
                if jei:
                    self.assertEqual(str(cell / "client/mods/jei.jar"), value["jei_jar"]["path"])
                    self.assertEqual(digest(self.runtime / "jei.jar"), value["jei_jar"]["sha256"])
                else:
                    self.assertIsNone(value["jei_jar"])

    def test_eula_requires_explicit_flag(self):
        for path in self.prepare():
            self.assertFalse((path.parent / "server/eula.txt").exists())

    def test_wrong_candidate_hash_writes_nothing(self):
        with self.assertRaisesRegex(SmokeError, "SHA-256 mismatch"):
            cells.prepare_cells(self.runtime, self.candidate, "0" * 64, self.output)
        self.assertFalse(self.output.exists())

    def test_invalid_hash_or_reserved_filename(self):
        for expected in ("", "a" * 63, "g" * 64, None):
            with self.subTest(expected=expected), self.assertRaises(SmokeError):
                cells.prepare_cells(self.runtime, self.candidate, expected, self.output)
        candidate = self.base / "jei.jar"
        candidate.write_bytes(b"reserved")
        with self.assertRaisesRegex(SmokeError, "filename"):
            cells.prepare_cells(self.runtime, candidate, digest(candidate), self.output)

    def test_existing_last_cell_refuses_before_creating_other_cells(self):
        existing = self.output / "47.4.23-jei"
        existing.mkdir(parents=True)
        (existing / "keep").write_bytes(b"keep")
        before = snapshot(self.output)
        with self.assertRaisesRegex(SmokeError, "overwrite"):
            self.prepare()
        self.assertEqual(before, snapshot(self.output))
        self.assertEqual([existing], list(self.output.iterdir()))

    def test_replace_changes_only_candidates_and_manifests(self):
        paths = self.prepare(accept_eula=True)
        for path in paths:
            (path.parent / "server/world").mkdir()
            (path.parent / "server/world/level.dat").write_bytes(b"retained world")
            (path.parent / "client/options.txt").write_bytes(b"owner's settings")
        before = snapshot(self.output)
        later = self.base / "later-candidate.jar"
        later.write_bytes(b"candidate-two")
        cells.prepare_cells(self.runtime, later, digest(later), self.output, replace_candidate=True)
        after = snapshot(self.output)
        for relative, data in before.items():
            if relative.endswith(("mods/candidate.jar", "cell.json")):
                continue
            self.assertEqual(data, after[relative], relative)
        self.assertEqual(8, sum(key.endswith("mods/later-candidate.jar") for key in after))
        self.assertFalse(any(key.endswith("mods/candidate.jar") for key in after))
        for path in paths:
            value = json.loads(path.read_text())
            self.assertEqual(digest(later), value["candidate"]["sha256"])
            self.assertEqual(str(path.parent / "client/mods/later-candidate.jar"), value["candidate"]["path"])
        self.assertEqual(self.expected, digest(self.candidate))

    def test_replace_same_filename_and_does_not_accept_eula_retroactively(self):
        paths = self.prepare()
        self.candidate.write_bytes(b"new contents, same name")
        self.expected = digest(self.candidate)
        self.prepare(replace_candidate=True, accept_eula=True)
        for path in paths:
            self.assertEqual(self.expected, json.loads(path.read_text())["candidate"]["sha256"])
            self.assertFalse((path.parent / "server/eula.txt").exists())

    def test_replace_without_cells_refuses(self):
        with self.assertRaises(SmokeError):
            self.prepare(replace_candidate=True)
        self.assertFalse(self.output.exists())

    def test_replace_rejects_wrong_new_candidate_without_changes(self):
        self.prepare()
        before = snapshot(self.output)
        with self.assertRaisesRegex(SmokeError, "SHA-256 mismatch"):
            cells.prepare_cells(self.runtime, self.candidate, "0" * 64, self.output, replace_candidate=True)
        self.assertEqual(before, snapshot(self.output))

    def test_replace_preflights_every_cell_mod_hash_and_argument_hash(self):
        paths = self.prepare()
        last = paths[-1].parent
        targets = [last / "server/mods/candidate.jar", last / "client/mods/candidate.jar",
                   last / "client/mods/jei.jar", last / "server" / cells.argument_name("47.4.23")]
        for target in targets:
            with self.subTest(target=target):
                original = target.read_bytes()
                target.write_bytes(b"tampered")
                before = snapshot(self.output)
                with self.assertRaisesRegex(SmokeError, "SHA-256 mismatch"):
                    self.prepare(replace_candidate=True)
                self.assertEqual(before, snapshot(self.output))
                target.write_bytes(original)

    def test_replace_rejects_manifest_escape_without_writes(self):
        paths = self.prepare()
        value = json.loads(paths[-1].read_text())
        value["candidate"]["path"] = str(self.base / "candidate.jar")
        self.write_json(paths[-1], value)
        before = snapshot(self.output)
        with self.assertRaisesRegex(SmokeError, "manifest differs"):
            self.prepare(replace_candidate=True)
        self.assertEqual(before, snapshot(self.output))

    def test_unexpected_mods_and_partial_replacement_are_not_overwritten(self):
        paths = self.prepare()
        for relative in ("server/mods/extra.jar", "client/mods/extra.jar", "candidate-server.partial",
                         "candidate-client.partial", "cell.json.partial"):
            target = paths[-1].parent / relative
            target.write_bytes(b"keep")
            before = snapshot(self.output)
            with self.subTest(relative=relative), self.assertRaises(SmokeError):
                self.prepare(replace_candidate=True)
            self.assertEqual(before, snapshot(self.output))
            target.unlink()

    def test_runtime_links_are_refused_even_outside_copied_subtrees(self):
        target = self.base / "external"
        target.mkdir()
        self.make_link(self.runtime / "unrelated", target)
        with self.assertRaisesRegex(SmokeError, "Linked|Reparse"):
            self.prepare()
        self.assertFalse(self.output.exists())

    @unittest.skipUnless(os.name == "nt", "Windows junction test")
    def test_windows_runtime_junction_is_refused(self):
        target = self.base / "external"
        target.mkdir()
        self.make_link(self.runtime / "junction", target, junction=True)
        with self.assertRaisesRegex(SmokeError, "Linked|Reparse"):
            self.prepare()

    def test_output_ancestor_link_is_refused(self):
        target = self.base / "external"
        target.mkdir()
        self.make_link(self.base / "output-link", target)
        self.output = self.base / "output-link/cells"
        with self.assertRaisesRegex(SmokeError, "Linked|Reparse"):
            self.prepare()
        self.assertEqual([], list(target.iterdir()))

    def test_link_in_existing_cell_refuses_replacement(self):
        paths = self.prepare()
        target = self.base / "external"
        target.mkdir()
        self.make_link(paths[-1].parent / "server/linked", target)
        with self.assertRaisesRegex(SmokeError, "Linked|Reparse"):
            self.prepare(replace_candidate=True)
        self.assertEqual([], list(target.iterdir()))

    def test_output_and_source_overlap_is_refused(self):
        for output in (self.runtime, self.runtime / "cells", self.base):
            with self.subTest(output=output), self.assertRaisesRegex(SmokeError, "overlap"):
                cells.prepare_cells(self.runtime, self.candidate, self.expected, output)

    def test_wrong_prepared_identity_or_dependency_is_refused(self):
        path = self.runtime / "prepared.json"
        original = json.loads(path.read_text())
        for key, value in (("schema_version", 2), ("forge", ["47.4.22"]),
                           ("jei", "15.0.0"), ("jei_sha256", "0" * 64)):
            self.write_json(path, {**original, key: value})
            with self.subTest(key=key), self.assertRaises(SmokeError):
                self.prepare()
            self.assertFalse(self.output.exists())

    def test_dirty_template_and_wrong_forge_profile_are_refused(self):
        server = self.runtime / "servers/47.4.23"
        for name in ("world", "mods", "eula.txt", "server.properties"):
            target = server / name
            target.write_bytes(b"not an unused template")
            with self.subTest(name=name), self.assertRaisesRegex(SmokeError, "not unused"):
                self.prepare()
            target.unlink()
        path = self.runtime / "versions/1.20.1-forge-47.4.23/1.20.1-forge-47.4.23.json"
        self.write_json(path, {"id": "different", "inheritsFrom": "1.20.1"})
        with self.assertRaisesRegex(SmokeError, "profile"):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_cli_success_and_failure(self):
        args = [str(self.runtime), str(self.candidate), self.expected, str(self.output), "--accept-eula"]
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0, cells.main(args))
        self.assertIn("no server or client started", output.getvalue())
        with contextlib.redirect_stderr(io.StringIO()) as error:
            self.assertEqual(1, cells.main(args))
        self.assertIn("overwrite", error.getvalue())


if __name__ == "__main__":
    unittest.main()
