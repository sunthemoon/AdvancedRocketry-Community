"""Synthetic cell-check tests: no JVMs or production locks are touched."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts.campaign_prep import cell_check as check


class CellCheckTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()

    def make_cell(self, jei=False):
        runtime, cell = self.root / "runtime", self.root / check.prep.cell_name("47.4.10", jei)
        runtime.mkdir()
        for target, mod in ((runtime / "candidate.jar", "advancedrocketrycommunity"),
                            (runtime / "jei.jar", "jei")):
            with zipfile.ZipFile(target, "w") as archive:
                archive.writestr("META-INF/mods.toml", f'modId="{mod}"\n')
        for side in ("server", "client"):
            (cell / side / "mods").mkdir(parents=True)
            check.shutil.copy2(runtime / "candidate.jar", cell / side / "mods/candidate.jar")
        if jei:
            check.shutil.copy2(runtime / "jei.jar", cell / "client/mods/jei.jar")
        for directory in (runtime / "servers/47.4.10", cell / "server"):
            args = directory / check.prep.argument_name("47.4.10")
            args.parent.mkdir(parents=True)
            args.write_text("official arguments", encoding="utf-8")
        (cell / "server/eula.txt").write_text("eula=true\n", encoding="utf-8")
        digest = check.prep.digest_file(runtime / "candidate.jar")
        value = check.prep.manifest(runtime, cell, "47.4.10", jei, "1.20.1-forge-47.4.10", "candidate.jar", digest)
        manifest = cell / "cell.json"
        check.write_json(manifest, value)
        return manifest, digest

    def test_preflight_checks_both_flavours_without_edits(self):
        for jei in (False, True):
            with self.subTest(jei=jei):
                # Each flavour needs an independent runtime fixture.
                with tempfile.TemporaryDirectory(dir=self.root) as directory:
                    original = self.root
                    self.root = Path(directory)
                    manifest, digest = self.make_cell(jei)
                    before = check.tree_hashes(self.root)
                    self.assertIs(check.validate_cell(manifest, digest)["jei"], jei)
                    self.assertEqual(before, check.tree_hashes(self.root))
                    self.root = original

    def test_preflight_rejects_wrong_candidate(self):
        manifest, _ = self.make_cell()
        with self.assertRaisesRegex(ValueError, "requested candidate"):
            check.validate_cell(manifest, "0" * 64)

    def test_preflight_rejects_server_tamper(self):
        manifest, digest = self.make_cell()
        (manifest.parent / "server/mods/candidate.jar").write_bytes(b"changed")
        with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
            check.validate_cell(manifest, digest)

    def test_preflight_rejects_renamed_jei_in_nojei(self):
        manifest, digest = self.make_cell()
        value = json.loads(manifest.read_text())
        # A candidate whose metadata lies about its identity cannot smuggle JEI server-side.
        for side in ("client", "server"):
            check.shutil.copy2(self.root / "runtime/jei.jar", manifest.parent / side / "mods/candidate.jar")
        digest = check.prep.digest_file(self.root / "runtime/jei.jar")
        value = check.prep.manifest(self.root / "runtime", manifest.parent, "47.4.10", False,
                                    "1.20.1-forge-47.4.10", "candidate.jar", digest)
        check.write_json(manifest, value)
        with self.assertRaisesRegex(ValueError, "JEI metadata"):
            check.validate_cell(manifest, digest)

    def test_preflight_rejects_forge_args_tamper(self):
        manifest, digest = self.make_cell()
        (manifest.parent / "server" / check.prep.argument_name("47.4.10")).write_text("changed")
        with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
            check.validate_cell(manifest, digest)

    def test_lock_requires_exact_token_boundary(self):
        lock = self.root / "lock"
        lock.mkdir()
        (lock / "owner.txt").write_text("W425 pid=12 token-other 2026-11-21\n")
        with patch.object(check, "LOCK", lock):
            with self.assertRaisesRegex(ValueError, "does not own"):
                check.check_lock("W425 pid=12 token")
            check.check_lock("W425 pid=12 token-other")

    def test_disk_refuses_low_space(self):
        with patch.object(check.shutil, "disk_usage") as disk:
            disk.return_value.free = 9 * 1024**3
            with self.assertRaisesRegex(ValueError, "10 GiB"):
                check.disk_check(self.root)

    @staticmethod
    def reply(ticks):
        return f"{check.PREFIX}tick={ticks[-1] + 1} entries={len(ticks)} data=" + ",".join(f"{t}:1000000" for t in ticks)

    def test_raw_overlap_and_gap_are_independent(self):
        result = check.analyze_polls([self.reply([1, 2, 3]), self.reply([3, 4, 5])])
        self.assertEqual(5, result["tick_count"])
        self.assertEqual([1], result["consecutive_overlap_ticks"])
        self.assertTrue(result["all_consecutive_polls_overlap"])
        self.assertEqual(0, result["missing_tick_count"])
        result = check.analyze_polls([self.reply([1, 2]), self.reply([4, 5])])
        self.assertFalse(result["all_consecutive_polls_overlap"])
        self.assertEqual(1, result["missing_tick_count"])

    def test_raw_empty_is_not_success(self):
        result = check.analyze_polls([])
        self.assertFalse(result["all_consecutive_polls_overlap"])
        self.assertEqual(0, result["tick_count"])

    def test_raw_conflict_refused(self):
        with self.assertRaisesRegex(ValueError, "Conflicting"):
            check.analyze_polls([self.reply([1, 2]), self.reply([2, 3]).replace("2:1000000", "2:9")])

    def audit(self, lines):
        log = self.root / "latest.log"
        log.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return check.audit_log(log)

    def test_errors_and_unknown_warnings_retained(self):
        result = self.audit(["[12:00:00] [Server thread/ERROR] [test/]: bad",
                             "[12:00:01] [Server thread/WARN] [test/]: unknown"])
        self.assertEqual(1, len(result["errors_and_fatals"]))
        self.assertEqual(1, len(result["unexpected_warnings"]))
        self.assertEqual(1, len(result["native_findings"]))

    def test_explicit_offline_and_known_forge_warning(self):
        lines = [f"[12:00:00] [Server thread/WARN] [net.minecraft.server.dedicated.DedicatedServer/]: {s}"
                 for s in sorted(check.OFFLINE_WARNINGS)]
        lines.append("[12:00:00] [main/WARN] [net.minecraftforge.fml.loading.moddiscovery.ModFileParser/LOADING]: "
                     "Mod file C:/runtime/fmlcore-1.20.1-47.4.10.jar is missing mods.toml file")
        result = self.audit(lines)
        self.assertEqual([], result["unexpected_warnings"])
        self.assertEqual([], result["native_findings"])

    def test_packaged_missing_refmap_is_not_excused_as_userdev(self):
        result = self.audit(["[12:00:00] [main/WARN] [mixin/]: Reference map 'advancedrocketrycommunity.refmap.json' "
                             "for advancedrocketrycommunity.mixins.json could not be read. "
                             "If this is a development environment you can ignore this message"])
        self.assertEqual(1, len(result["unexpected_warnings"]))

    def test_native_hold_count_not_weakened(self):
        line = "[12:00:00] [Server thread/WARN] [io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/]: " \
               "ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED"
        self.assertEqual([], self.audit([line])["native_findings"])
        self.assertEqual(1, len(self.audit([line, line])["native_findings"]))

    def test_world_identity_excludes_changing_time_but_retains_seed(self):
        nbt = check.nbt
        data = {"LevelName": nbt.Tag(nbt.STRING, b"world"),
                "WorldGenSettings": nbt.Tag(nbt.COMPOUND, {"seed": nbt.Tag(nbt.LONG, 425)}),
                **{key: nbt.Tag(nbt.INT, value) for key, value in
                   (("SpawnX", 0), ("SpawnY", 64), ("SpawnZ", 0), ("DataVersion", 3465))},
                "Time": nbt.Tag(nbt.LONG, 3600)}
        root = nbt.Tag(nbt.COMPOUND, {"Data": nbt.Tag(nbt.COMPOUND, data)})
        target = self.root / "level.dat"
        nbt.write_gzip(target, b"", root)
        first = check.world_identity(target)
        data["Time"] = nbt.Tag(nbt.LONG, 3700)
        nbt.write_gzip(target, b"", root)
        second = check.world_identity(target)
        self.assertEqual(first["identity"], second["identity"])
        self.assertGreater(second["time"], first["time"])
        data["WorldGenSettings"].value["seed"] = nbt.Tag(nbt.LONG, 426)
        nbt.write_gzip(target, b"", root)
        self.assertNotEqual(first["identity"], check.world_identity(target)["identity"])


if __name__ == "__main__":
    unittest.main()
