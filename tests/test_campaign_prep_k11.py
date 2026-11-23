"""Linux-safe K11 planning/integrity checks; these never start Minecraft."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.campaign_prep import k11_world_kit as kit
from scripts.campaign_client.protocol import load_session


class K11Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def sealed(self, path):
        path.mkdir()
        (path / "world").mkdir()
        (path / "world/level.dat").write_bytes(b"synthetic-world-not-nbt")
        kit.common.seal(path)
        return path

    def generated(self):
        parent = self.sealed(self.root / "parent")
        output = self.root / "kit"
        kit.generate(output, parent)
        return output, parent

    def test_marker_exact(self):
        self.assertEqual(32, len(kit.MARKER))
        self.assertEqual("e67124e2a697a32ccdafc384e672362f81c732e5e01872012a2e22c6e4590f98",
                         kit.common.digest(kit.MARKER))

    def test_parent_verified_and_unchanged(self):
        output, parent = self.generated()
        before = kit.common.tree_hashes(parent)
        plan = json.loads((output / "plan.json").read_text())
        self.assertEqual(kit.verify_seal(parent), plan["parent"])
        self.assertEqual(kit.common.CANDIDATE, plan["candidate_commit"])
        self.assertEqual(before, kit.common.tree_hashes(parent))

    def test_generate_refuses_existing_output(self):
        output, parent = self.generated()
        before = kit.common.tree_hashes(output)
        with self.assertRaises(FileExistsError):
            kit.generate(output, parent)
        self.assertEqual(before, kit.common.tree_hashes(output))

    def test_generation_cannot_write_inside_sealed_parent(self):
        parent = self.sealed(self.root / "parent")
        before = kit.common.tree_hashes(parent)
        with self.assertRaisesRegex(ValueError, "overlaps"):
            kit.generate(parent / "new-kit", parent)
        self.assertEqual(before, kit.common.tree_hashes(parent))

    def test_changed_parent_rejected_before_output(self):
        parent = self.sealed(self.root / "parent")
        (parent / "world/level.dat").write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "mismatched"):
            kit.generate(self.root / "out", parent)
        self.assertFalse((self.root / "out").exists())

    def test_unlisted_parent_member_rejected(self):
        parent = self.sealed(self.root / "parent")
        (parent / "extra").write_bytes(b"not listed")
        with self.assertRaises(ValueError):
            kit.verify_seal(parent)

    def test_checksum_traversal_absolute_duplicate_and_self_rejected(self):
        parent = self.sealed(self.root / "parent")
        path = parent / "SHA256SUMS.txt"
        original = path.read_text()
        for name in ("../other", "/absolute", "C:/outside", "world\\level.dat", "./world/level.dat", "SHA256SUMS.txt"):
            with self.subTest(name=name):
                path.write_text("0" * 64 + "  " + name + "\n")
                with self.assertRaises(ValueError):
                    kit.verify_seal(parent)
        path.write_text(original + original)
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            kit.verify_seal(parent)

    def test_malformed_and_empty_seal_rejected(self):
        parent = self.sealed(self.root / "parent")
        for value in ("", "incorrect\n", "f" * 64 + " world/level.dat\n"):
            (parent / "SHA256SUMS.txt").write_text(value)
            with self.assertRaises(ValueError):
                kit.verify_seal(parent)

    def test_missing_parent_member_rejected(self):
        parent = self.sealed(self.root / "parent")
        (parent / "world/level.dat").unlink()
        with self.assertRaises(ValueError):
            kit.verify_seal(parent)

    def test_properties_are_separate(self):
        ids = kit.NATIVE_IDS
        marked = kit.properties("marked", ids)
        self.assertEqual({kit.PREFIX + "releaseTestHooks", kit.PREFIX + "bayV2CampaignPlayers"}, set(marked))
        self.assertEqual("true", kit.properties("fuel", ids)[kit.PREFIX + "escrowLoaderAdmission"])
        self.assertNotIn(kit.PREFIX + "escrowLoaderAdmission", kit.properties("intent", ids))
        self.assertEqual("B18_INTENT", kit.properties("intent", ids)[kit.PREFIX + "bayCheckpoint"])
        self.assertEqual({}, kit.properties("nohook", ids))

    def test_bad_uuid_selection_rejected(self):
        for values in ([], [kit.NATIVE_IDS[0]], kit.NATIVE_IDS * 2,
                       [kit.NATIVE_IDS[0]] * 2, ["bad", kit.NATIVE_IDS[1]],
                       [" " + kit.NATIVE_IDS[0], kit.NATIVE_IDS[1]]):
            with self.subTest(values=values), self.assertRaises(ValueError):
                kit.properties("marked", values)

    def test_no_production_property(self):
        for mode in ("marked", "fuel", "intent", "nohook"):
            self.assertFalse(any("production" in k.lower() for k in kit.properties(mode, kit.NATIVE_IDS)))
        with self.assertRaises(ValueError):
            kit.properties("invented", kit.NATIVE_IDS)

    def test_geometry_has_two_distinct_seats_and_separated_scenes(self):
        scenes = kit.geometry()
        self.assertEqual(["safe", "fall", "fuel-A", "fuel-B"], [s["name"] for s in scenes])
        all_cells = []
        for scene in scenes:
            self.assertEqual(2, len({tuple(p) for p in scene["seat_blocks"]}))
            self.assertEqual(scene["origin"][1] + 48, scene["hold_origin"][1])
            self.assertIn("UNASSEMBLED", scene["state"])
            self.assertIn("Read authoritative", scene["seat_indices"])
            all_cells += [tuple(c["pos"]) for c in scene["cells"]]
        self.assertEqual(len(all_cells), len(set(all_cells)))
        self.assertEqual("<B_UUID>", scenes[-1]["owner"])

    def test_pad_is_one_below_origin_fall_floor_is_not_anchor(self):
        for scene in kit.geometry():
            self.assertEqual(3, scene["safe_pad"][1][0] - scene["safe_pad"][0][0] + 1)
            self.assertEqual(149, scene["safe_pad"][0][1])
            self.assertEqual(188, scene["fall_floor"][0][1])
            self.assertGreater(abs(scene["fall_floor"][0][1] + 1 - scene["origin"][1]), 1)

    def test_all_commands_are_plain_block_placement_without_snbt(self):
        commands = kit.build_commands(kit.geometry())
        self.assertGreater(len(commands), 35)
        for command in commands:
            self.assertIn(command.split()[0], ("fill", "setblock"))
            self.assertNotIn("{", command)
            self.assertNotIn("data ", command)
            self.assertNotIn("summon", command)
        self.assertIn("fill 319 188 255 321 188 257 minecraft:stone", commands)
        self.assertNotIn("fill 319 149 255 321 149 257 minecraft:stone", commands)

    def test_21_dispositions_are_explicit_and_native_named(self):
        rows = kit.variant_table()
        self.assertEqual(21, len(rows))
        self.assertEqual(21, len({r["variant"] for r in rows}))
        self.assertEqual(10, len([r for r in rows if r["variant"].startswith("B")]))
        for row in rows:
            self.assertEqual("synthetic-only", row["disposition"])
            self.assertEqual("NOT_COVERED", row["v2_status"])
            self.assertTrue(row["native_case"])
            self.assertTrue(row["reason"])
        self.assertIn("200 ticks", rows[15]["reason"])
        self.assertEqual("W331_B4_GONE_X_S_PRESENT_ONLINE", rows[4]["native_case"])

    def test_recipe_rows_and_ownership_boundaries(self):
        rows = kit.recipes()
        self.assertEqual({f"J{i:02}" for i in (1, 2, 3, 4, 23, 24, 25, 26, 27, 28, 29)}, set(rows))
        self.assertIn("NOT_COVERED", rows["J03"][0])
        self.assertIn('"not run; covered natively by X5"', " ".join(rows["J02"]))
        self.assertIn(kit.FIXTURE_OWNER, " ".join(rows["J01"]))
        self.assertIn("never receives A's private menu", " ".join(rows["J23"]))
        self.assertIn("arce rocket release-seat", " ".join(rows["J28"]))
        self.assertIn("fixture-owned endpoints", " ".join(rows["J24"]))

    def test_templates_fail_closed_until_bound(self):
        output, _ = self.generated()
        for mode in ("marked", "intent", "fuel", "nohook"):
            with self.assertRaises(ValueError):
                load_session(output / (mode + "-session.template.json"))

    def test_readbacks_require_positive_receipts_and_unique_native_root(self):
        console = unittest.mock.Mock()
        console.send.return_value = ["[Server] unrelated"]
        with self.assertRaisesRegex(ValueError, "Geometry mismatch"):
            kit.readbacks(console, kit.geometry())

    def test_nohook_refusal_is_observed_without_success_only_console(self):
        class Process:
            def __init__(self):
                self.lines = []

            def command(self, command):
                if command.startswith("arce"):
                    self.lines.append("Unknown or incomplete command, see below for error")
                else:
                    self.lines.append("[Server] K11_REFUSAL_BARRIER")

            def wait_for(self, pattern, timeout, start_at):
                if not any(pattern.search(v) for v in self.lines[start_at:]):
                    raise AssertionError("No barrier")
        console = kit.Console(Process())
        lines = kit.nohook_refusal(console)
        self.assertIn("Unknown or incomplete command", lines[0])
        self.assertEqual(2, len(console.commands))

    def test_missing_nohook_refusal_fails(self):
        console = unittest.mock.Mock()
        console.commands = []
        console.process.lines = []
        with self.assertRaisesRegex(ValueError, "did not refuse"):
            kit.nohook_refusal(console)

    def test_refusals_are_individually_dispositioned(self):
        rows = kit.fuel_refusals()
        self.assertEqual(18, len(rows))
        self.assertEqual(18, len({r["status"] for r in rows}))
        for row in rows:
            self.assertEqual("NOT_COVERED", row["qualification"])
            self.assertTrue(row["recipe"])
        self.assertIn("R638", next(r for r in rows if r["status"] == "INVALID_DATA")["recipe"])

    def test_bound_sessions_are_w399_compatible_and_do_not_edit_source(self):
        output, _ = self.generated()
        source = output / "source"
        source.mkdir()
        (source / "world").mkdir()
        (source / "world" / kit.MARKER_NAME).write_bytes(kit.MARKER)
        kit.common.seal(source)
        server = self.root / "cell-server"
        server.mkdir()
        (server / "template").write_bytes(b"immutable")
        cell_path = self.root / "cell.json"
        cell = dict(schema_version=1, forge="47.4.10", jei=False, runtime_root=str(self.root / "runtime"),
                    server_dir=str(server), client_game_dir=str(self.root / "client"), version_id="synthetic",
                    candidate=dict(path="synthetic.jar", sha256=kit.common.JAR_HASH), jei_jar=None)
        kit.write_json(cell_path, cell)
        accounts = [dict(name="Player" + str(i), uuid=v, pid=100 + i, game_dir=str(self.root / ("client" + str(i))))
                    for i, v in enumerate(kit.NATIVE_IDS)]
        before = kit.common.tree_hashes(source)
        for mode in ("marked", "intent", "fuel", "nohook"):
            target = self.root / (mode + "-bound")
            spec = kit.bind_session(output, target, mode, accounts, cell_path, Path("java"))
            loaded = load_session(target / "session.json")
            self.assertEqual("attach", loaded["mode"])
            self.assertTrue(loaded["server"]["online-mode"])
            self.assertEqual([], loaded["server"]["ops"])
            self.assertEqual(2, len(loaded["server"]["whitelist"]))
            self.assertEqual(kit.properties(mode, kit.NATIVE_IDS), spec["server"]["jvm_properties"])
            self.assertEqual(kit.MARKER, (target / "server-template/world" / kit.MARKER_NAME).read_bytes())
        self.assertEqual(before, kit.common.tree_hashes(source))
        self.assertEqual(b"immutable", (server / "template").read_bytes())

    def test_binding_rejects_credentials_and_duplicate_accounts(self):
        output, _ = self.generated()
        source = self.sealed(output / "source")
        self.assertTrue(source.exists())
        with self.assertRaises(ValueError):
            kit.bind_session(output, self.root / "bound", "marked", [{"accessToken": "never"}], Path("cell"), Path("java"))
        self.assertFalse((self.root / "bound").exists())

    def test_native_run_requires_owned_lock_before_start(self):
        with patch.object(kit, "LOCK", self.root), patch.object(kit, "Console") as console:
            (self.root / "owner.txt").write_text("OTHER pid=123")
            with self.assertRaisesRegex(ValueError, "lock required"):
                kit.run(self.root, "build", Path("cell"), Path("java"), "W453 pid=123 token")
            console.assert_not_called()

    def test_probe_inventory_covers_every_source_block(self):
        scenes = kit.geometry()
        checks = kit.probes(scenes)
        self.assertEqual(sum(len(s["cells"]) for s in scenes) + 7, len(checks))
        self.assertEqual(len(checks), len({label for _, label in checks}))

    def test_output_cannot_contain_or_be_inside_a_protected_input(self):
        for target, source in ((self.root / "source/child", self.root / "source"),
                               (self.root / "parent", self.root / "parent/child")):
            with self.subTest(target=target), self.assertRaisesRegex(ValueError, "overlaps"):
                kit.disjoint_output(target, source)


if __name__ == "__main__":
    unittest.main()
