"""Portable tests: native JVM execution is deliberately outside governance."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock

from scripts.campaign_prep import k04_rockets as k04


class K04Tests(unittest.TestCase):
    def test_exact_proposed_sizes_and_unique_positions(self):
        for n in k04.SIZES:
            r = k04.rocket(n, (256, 100, 256))
            self.assertEqual(n, len(r["blocks"]))
            self.assertEqual(n, len({tuple(b["pos"]) for b in r["blocks"]}))
            self.assertEqual(1, sum(b["block"].endswith(":rocket_seat") for b in r["blocks"]))
            self.assertEqual(1, sum(b["block"].endswith(":guidance_computer") for b in r["blocks"]))

    def test_proposed_structure_is_six_connected(self):
        for n in k04.SIZES:
            todo = {tuple(b["pos"]) for b in k04.rocket(n, (256, 100, 256))["blocks"]}
            pending = [todo.pop()]
            while pending:
                at = pending.pop()
                for axis in range(3):
                    for delta in (-1, 1):
                        near = list(at)
                        near[axis] += delta
                        near = tuple(near)
                        if near in todo:
                            todo.remove(near)
                            pending.append(near)
            self.assertFalse(todo)

    def test_fill_commands_reproduce_explicit_inventory(self):
        for name in ("64", "512", "2048", "multi"):
            layout = k04.scene(name)
            actual = {}
            for command in k04.build_commands(layout):
                fields = command.split()
                if fields[0] == "fill":
                    x, y, z, xx, yy, zz = map(int, fields[1:7])
                    for a in range(x, xx + 1):
                        for b in range(y, yy + 1):
                            for c in range(z, zz + 1):
                                actual[(a, b, c)] = fields[7]
                elif fields[0] == "setblock":
                    actual[tuple(map(int, fields[1:4]))] = fields[4]
            for r in layout["rockets"]:
                for block in r["blocks"]:
                    self.assertEqual(block["block"], actual[tuple(block["pos"])])

    def test_multi_rockets_do_not_touch(self):
        rockets = k04.scene("multi")["rockets"]
        positions = [{tuple(b["pos"]) for b in r["blocks"]} for r in rockets]
        for i, first in enumerate(positions):
            for second in positions[i+1:]:
                self.assertTrue(first.isdisjoint(second))
                self.assertGreater(min(p[0] for p in second) - max(p[0] for p in first), 16)

    def test_generator_is_deterministic(self):
        with tempfile.TemporaryDirectory() as temp:
            a, b = Path(temp) / "a", Path(temp) / "b"
            k04.generate(a)
            k04.generate(b)
            self.assertEqual(k04.common.tree_hashes(a), k04.common.tree_hashes(b))

    def test_generate_never_overwrites(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(FileExistsError):
                k04.generate(Path(temp))

    def test_boundary_is_not_misrepresented_as_legal(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "package"
            k04.generate(root)
            value = json.loads((root / "payload-boundary.json").read_text())
            self.assertEqual("NOT_COVERED", value["status"])
            self.assertEqual((2048, 128, 1048576), (value["expected_blocks"], value["payloads"], value["aggregate_bound"]))
            self.assertFalse((root / "payload-boundary" / "world").exists())

    def test_first_use_commands_never_launch_or_edit_rocket_nbt(self):
        for n in k04.SIZES:
            commands = "\n".join(k04.build_commands(k04.scene(str(n))))
            self.assertNotIn("launch", commands)
            self.assertNotIn("data merge entity", commands)
            self.assertNotIn("summon", commands)

    def test_viewpoints_are_explicit_unverified_proposals(self):
        for name in ("64", "512", "2048", "multi"):
            value = k04.scene(name)
            self.assertIn("NOT_VISUALLY_VERIFIED", value["camera_status"])
            self.assertEqual({"x", "y", "z", "yaw", "pitch"}, set(value["overview"]))
            self.assertEqual(1280, value["viewport"]["width"])
            for r in value["rockets"]:
                self.assertEqual({"x", "y", "z", "yaw", "pitch"}, set(r["observer"]))

    def test_native_report_parser(self):
        line = ("ARCE_RELEASE_TEST_FLIGHT_REPORT entity=00000000-0000-0000-0000-000000000001 "
                "logical=00000000-0000-0000-0000-000000000002 snapshot=" + "a"*64 +
                " dimension=minecraft:overworld state=FUELED fuel=16000 capacity=16000 passengers=0 "
                "transfer=none origin=256,100,256 blocks=64")
        result = k04.parse_report([line])
        self.assertEqual(64, result["blocks"])
        self.assertEqual(16000, result["fuel"])
        self.assertEqual("FUELED", result["state"])
        with self.assertRaisesRegex(ValueError, "No native"):
            k04.parse_report(["generator says legal"])

    def test_readback_rejects_wrong_count_fuel_state_seats(self):
        base = dict(blocks=64, fuel=16000, capacity=16000, state="FUELED", passengers=0, transfer="none")
        from unittest.mock import patch
        for key, value in (("blocks", 63), ("fuel", 15999), ("capacity", 15000), ("state", "ASSEMBLED"),
                           ("passengers", 1), ("transfer", "active")):
            report = dict(base, **{key: value})
            with patch.object(k04, "parse_report", return_value=report):
                with self.assertRaises(ValueError):
                    k04.read_rocket(Mock(), "uuid", k04.rocket(64, (256, 100, 256)))
        with patch.object(k04, "parse_report", return_value=base):
            console = Mock()
            console.send.return_value = ["[]"]
            with self.assertRaisesRegex(ValueError, "seat"):
                k04.read_rocket(console, "uuid", k04.rocket(64, (256, 100, 256)))

    def test_native_fuel_uses_cells_and_checks_consumption(self):
        console = Mock()
        k04.fuel(console, k04.rocket(64, (256, 100, 256)), "uuid")
        self.assertEqual(32, console.until.call_count)
        commands = [c.args[0] for c in console.send.call_args_list]
        self.assertEqual(32, sum("rocket_fuel_cell" in c for c in commands))
        self.assertFalse(any("data merge entity" in c or "release-test refuel" in c for c in commands))

    def test_seal_refuses_unqualified_or_changed_world(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "world").mkdir()
            (root / "world" / "test").write_bytes(b"world")
            for stage, status in (("build", "BUILT"), ("readback", "MATCHED")):
                (root / (stage + ".json")).write_bytes(k04.common.json_bytes(dict(
                    status=status, cell_unchanged=True, source_world_unchanged=True,
                    world_files=k04.common.tree_hashes(root / "world"))))
            (root / "world" / "test").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "world changed"):
                k04.seal(root)

    def test_seal_is_exact_and_immutable(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "world").mkdir()
            (root / "world" / "test").write_bytes(b"world")
            for stage, status in (("build", "BUILT"), ("readback", "MATCHED")):
                (root / (stage + ".json")).write_bytes(k04.common.json_bytes(dict(
                    status=status, cell_unchanged=True, source_world_unchanged=True,
                    world_files=k04.common.tree_hashes(root / "world"))))
            inventory = k04.common.tree_hashes(root)
            k04.seal(root)
            for line in (root / "SHA256SUMS.txt").read_text().splitlines():
                h, p = line.split("  ", 1)
                self.assertEqual(inventory.pop(p), h)
            self.assertFalse(inventory)
            with self.assertRaisesRegex(ValueError, "overwrite"):
                k04.seal(root)

    def test_console_rejects_native_command_error(self):
        process = Mock()
        process.lines = ["Unknown block"]
        process.wait_for.side_effect = lambda *a, **k: process.lines.append("Unknown block")
        with self.assertRaisesRegex(ValueError, "Rejected"):
            k04.Console(process).send("setblock bad")

    def test_direct_invocation_help(self):
        result = subprocess.run([sys.executable, "-B", str(Path(k04.__file__)), "--help"],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("generate", result.stdout)


if __name__ == "__main__":
    unittest.main()
