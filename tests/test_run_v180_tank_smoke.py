"""Exact native tank payload checks cannot pass on palette-only or duplicate carriers."""

import copy
import json
import struct
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from scripts import run_v180_tank_smoke as tanks


class FullChunkReadinessTests(unittest.TestCase):
    def test_force_load_ticket_does_not_substitute_for_full_readiness(self):
        class Process:
            lines = []
            attempts = 0

            def command(self, value):
                if value.startswith("execute"):
                    self.attempts += 1
                    if self.attempts == 2:
                        self.lines.append("[Server thread/INFO] [minecraft/MinecraftServer]: [Server] ARCE_TANK_FULL_LOADED")
                else:
                    self.lines.append("[Server thread/INFO] [minecraft/MinecraftServer]: [Server] " + value.removeprefix("say "))

            def wait_for(self, marker, timeout, start_at):
                self.last_timeout = timeout
                assert any(marker.search(line) for line in self.lines[start_at:])

        process, commands = Process(), []
        with patch.object(tanks.time, "monotonic", side_effect=[0, 0, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35]), \
                patch.object(tanks.time, "sleep"):
            tanks.wait_full_loaded(process, commands)
        self.assertEqual(2, process.attempts)
        self.assertEqual(4, len(commands))
        self.assertLess(process.last_timeout, 60)

    def test_unloaded_chunk_fails_at_original_total_deadline(self):
        class Process:
            lines = []

            def command(self, value):
                if value.startswith("say "):
                    self.lines.append("[Server thread/INFO] [minecraft/MinecraftServer]: [Server] " + value.removeprefix("say "))

            def wait_for(self, marker, timeout, start_at):
                assert timeout <= 60
                assert any(marker.search(line) for line in self.lines[start_at:])

        commands = []
        with patch.object(tanks.time, "monotonic", side_effect=[0, 0, 0, 0, 60, 60]), \
                patch.object(tanks.time, "sleep"), self.assertRaises(tanks.server.SmokeError):
            tanks.wait_full_loaded(Process(), commands)
        self.assertEqual(2, len(commands))

    def test_readiness_requires_authoritative_info_line(self):
        for line in ("ARCE_TANK_FULL_LOADED",
                     "[Server thread/ERROR] [minecraft/MinecraftServer]: [Server] ARCE_TANK_FULL_LOADED",
                     "[Server thread/INFO] [example/Other]: [Server] ARCE_TANK_FULL_LOADED",
                     "[Server thread/INFO] [other/example]: echoed [Server thread/INFO] [minecraft/MinecraftServer]: [Server] ARCE_TANK_FULL_LOADED"):
            self.assertIsNone(tanks.FULL_READY.search(line))
        self.assertIsNotNone(tanks.FULL_READY.search(
            "[21:03:08] [Server thread/INFO] [minecraft/MinecraftServer]: [Not Secure] [Server] ARCE_TANK_FULL_LOADED"))

    def test_ready_marker_after_original_deadline_is_refused(self):
        class Process:
            lines = []

            def command(self, value):
                if value.startswith("execute"):
                    self.lines.append("[Server thread/INFO] [minecraft/MinecraftServer]: [Server] ARCE_TANK_FULL_LOADED")
                else:
                    self.lines.append("[Server thread/INFO] [minecraft/MinecraftServer]: [Server] " + value.removeprefix("say "))

            def wait_for(self, marker, timeout, start_at):
                assert timeout <= 60
                assert any(marker.search(line) for line in self.lines[start_at:])

        with patch.object(tanks.time, "monotonic", side_effect=[0, 0, 0, 61]), \
                self.assertRaises(tanks.server.SmokeError):
            tanks.wait_full_loaded(Process(), [])


def fixture(packaged=False):
    palette = [{"Name": "minecraft:air"}, {"Name": "advancedrocketrycommunity:pressurized_tank"}]
    data = [0] * 256
    entities = []
    for index in range(5):
        if index == 0 and packaged:
            continue
        x, y, z = 180 + index * 2, 180, 180
        cell = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        data[cell // 16] |= 1 << ((cell % 16) * 4)
        entities.append({"id": "advancedrocketrycommunity:pressurized_tank", "x": x, "y": y, "z": z,
                         tanks.ROOT: tanks.expected_root(index)})
    return {"xPos": 11, "zPos": 11, "sections": [{"Y": 11, "block_states": {"palette": palette, "data": data}}],
            "block_entities": entities}


def item_fixture():
    return {"Position": [11, 11], "Entities": [{"id": "minecraft:item", "Pos": [180.5, 180.0, 180.5],
            "Item": {"id": "advancedrocketrycommunity:pressurized_tank", "Count": 1,
            "tag": {tanks.ROOT: tanks.expected_root(0)}}}]}


def compound_bytes(value):
    """Minimal test-only NBT writer for this fixture's compounds and signed palette words."""
    result = bytearray()
    for name, child in value.items():
        encoded = name.encode("utf-8")
        header = struct.pack(">H", len(encoded)) + encoded
        if isinstance(child, str):
            text = child.encode("utf-8")
            result += b"\x08" + header + struct.pack(">H", len(text)) + text
        elif isinstance(child, int):
            result += b"\x03" + header + struct.pack(">i", child)
        elif isinstance(child, dict):
            result += b"\x0a" + header + compound_bytes(child)
        elif isinstance(child, list) and name == "data":
            result += b"\x0c" + header + struct.pack(">i", len(child))
            result += b"".join(struct.pack(">q", word) for word in child)
        elif isinstance(child, list) and all(isinstance(item, dict) for item in child):
            result += b"\x09" + header + b"\x0a" + struct.pack(">i", len(child))
            result += b"".join(compound_bytes(item) for item in child)
        else:
            raise AssertionError("Unsupported test fixture type")
    return bytes(result) + b"\x00"


def region_bytes(chunk):
    compressed = tanks.zlib.compress(b"\x0a\x00\x00" + compound_bytes(chunk))
    body = struct.pack(">I", len(compressed) + 1) + b"\x02" + compressed
    header = bytearray(8_192)
    index = (11 + 11 * 32) * 4
    header[index:index + 4] = b"\x00\x00\x02\x04"
    return bytes(header) + body + bytes(16_384 - len(body))


class TankNativeEvidenceTest(unittest.TestCase):
    def test_exact_roots_and_coordinates_without_mutating_decoder_globals(self):
        chunk = fixture()
        before = copy.deepcopy(chunk)
        result = tanks.check_chunk(chunk)
        self.assertEqual(set(result), set(tanks.NAMES))
        self.assertEqual(chunk, before)
        self.assertEqual(tanks.motors.CHUNK, (8, 8))
        self.assertEqual(set(tanks.check_chunk(fixture(True), True)), set(tanks.NAMES[1:]))

    def test_palette_presence_and_wrong_chunk_do_not_establish_identity(self):
        chunk = fixture()
        chunk["sections"][0]["block_states"]["data"] = [0] * 256
        with self.assertRaisesRegex(RuntimeError, "exact saved coordinate"):
            tanks.check_chunk(chunk)
        chunk = fixture()
        chunk["xPos"] = 8
        with self.assertRaisesRegex(RuntimeError, "chunk identity"):
            tanks.check_chunk(chunk)
        with self.assertRaisesRegex(RuntimeError, "outside"):
            tanks.block_at(fixture(), (200, 180, 180))

    def test_every_schema_amount_and_metadata_is_exact(self):
        for index in range(5):
            chunk = fixture()
            chunk["block_entities"][index][tanks.ROOT]["fluid"]["Amount"] += 1
            with self.assertRaisesRegex(RuntimeError, "payload/schema/metadata"):
                tanks.check_chunk(chunk)
        for index, mutate in ((2, lambda root: root["fluid"]["Tag"].update(batch="changed")),
                              (3, lambda root: root.update(schema=1)),
                              (3, lambda root: root.pop("extension"))):
            chunk = fixture()
            mutate(chunk["block_entities"][index][tanks.ROOT])
            with self.assertRaisesRegex(RuntimeError, "payload/schema/metadata"):
                tanks.check_chunk(chunk)

    def test_duplicate_or_second_block_carrier_is_rejected(self):
        chunk = fixture()
        chunk["block_entities"].append(copy.deepcopy(chunk["block_entities"][0]))
        with self.assertRaisesRegex(RuntimeError, "duplicated"):
            tanks.check_chunk(chunk)
        with self.assertRaisesRegex(RuntimeError, "second native tank carrier"):
            tanks.check_chunk(fixture(), True)

    def test_dropped_item_has_one_exact_payload_and_local_entity(self):
        entities = item_fixture()
        before = copy.deepcopy(entities)
        self.assertEqual(tanks.check_item(entities), entities["Entities"][0]["Item"])
        self.assertEqual(entities, before)
        for mutate in (lambda value: value["Item"].update(Count=2),
                       lambda value: value["Item"]["tag"].update(BlockEntityTag={}),
                       lambda value: value["Item"]["tag"][tanks.ROOT]["fluid"].update(Amount=12_345),
                       lambda value: value.update(Pos=[184.5, 180.0, 180.5])):
            invalid = item_fixture()
            mutate(invalid["Entities"][0])
            with self.assertRaises(RuntimeError):
                tanks.check_item(invalid)
        entities["Entities"].append(copy.deepcopy(entities["Entities"][0]))
        with self.assertRaisesRegex(RuntimeError, "one tank Item"):
            tanks.check_item(entities)

    def test_entity_decoder_rejects_trailing_or_oversized_compression(self):
        with self.assertRaisesRegex(RuntimeError, "Oversized compressed"):
            tanks.decode_entities(bytes(tanks.regions.MAX_COMPRESSED_CHUNK + 1))
        with self.assertRaisesRegex(RuntimeError, "Invalid/oversized"):
            tanks.decode_entities(tanks.zlib.compress(b"x") + b"trailing")

    def test_capacity_edit_changes_one_default_without_absorbing_the_next_line(self):
        with tempfile.TemporaryDirectory(prefix="arce-tank-unit-") as directory:
            runtime = Path(directory) / "server"
            (runtime / "config").mkdir(parents=True)
            evidence = Path(directory) / "evidence"
            evidence.mkdir()
            config = runtime / "config/advancedrocketrycommunity-common.toml"
            before = b"[machines]\r\n\ttankCapacityMultiplier = 1.0\r\n\n  # retain comment\r\nother = true\r\n"
            config.write_bytes(before)
            tanks.lower_capacity(runtime, evidence)
            self.assertEqual((evidence / "common-before.toml").read_bytes(), before)
            self.assertEqual(config.read_bytes(), before.replace(b"1.0\r\n", b"0.25\n"))
            with self.assertRaisesRegex(RuntimeError, "one existing exact default"):
                tanks.lower_capacity(runtime, evidence)

    def test_report_waits_for_final_feedback_not_the_first_duplicate_logger_row(self):
        self.assertIsNone(tanks.REPORT_END.search("[ARCE/]: ARCE_TANK_REPORT cell=corrupt repair=true"))
        self.assertIsNotNone(tanks.REPORT_END.search(
            "[Server thread/INFO] [minecraft/MinecraftServer]: ARCE_TANK_REPORT cell=corrupt carrier=be"))

    def test_oversized_patch_is_one_native_future_root_with_backup_and_unchanged_header(self):
        with tempfile.TemporaryDirectory(prefix="arce-tank-unit-") as directory:
            root = Path(directory)
            path = root / "server/world/region/r.0.0.mca"
            path.parent.mkdir(parents=True)
            evidence = root / "evidence"
            evidence.mkdir()
            before = region_bytes(fixture(True))
            path.write_bytes(before)
            patched = tanks.patch_oversized(root / "server", evidence)
            self.assertEqual(before, (evidence / "region-before-patch.mca").read_bytes())
            self.assertEqual(before[:8_192], path.read_bytes()[:8_192])
            self.assertEqual(8_192, json.loads((evidence / "patch.json").read_text())["array_bytes"])
            decoded = tanks.regions._decode_chunk(patched, 11, 11)
            self.assertEqual({"part0": [0] * 4_096, "part1": [0] * 4_096},
                             decoded["block_entities"][2][tanks.ROOT]["extension"])
            self.assertEqual({"Name": "minecraft:air"}, tanks.block_at(decoded, tanks.SAVE_MARKER_POSITION))

    def test_wrong_or_duplicate_marker_refuses_patch_before_native_file_mutation(self):
        for change in (lambda chunk: chunk["block_entities"][2][tanks.ROOT].update(extension="wrong"),
                       lambda chunk: chunk["block_entities"][0][tanks.ROOT].update(extension="retain-verbatim")):
            with tempfile.TemporaryDirectory(prefix="arce-tank-unit-") as directory:
                root = Path(directory)
                path = root / "server/world/region/r.0.0.mca"
                path.parent.mkdir(parents=True)
                evidence = root / "evidence"
                evidence.mkdir()
                chunk = fixture(True)
                change(chunk)
                before = region_bytes(chunk)
                path.write_bytes(before)
                with self.assertRaises(RuntimeError):
                    tanks.patch_oversized(root / "server", evidence)
                self.assertEqual(before, path.read_bytes())
                self.assertFalse((evidence / "region-before-patch.mca").exists())

    def test_native_refusal_log_requires_both_guard_and_exact_chunk_and_rejects_other_errors(self):
        valid = ["[Server thread/ERROR] [net.minecraftforge/]: Exception caught during firing event: " + tanks.SAVE_REFUSAL,
                 "[Server thread/ERROR] [minecraft/ChunkMap]: Failed to save chunk 11,11"]
        self.assertEqual(valid, tanks.audit_save_refusal(valid))
        for lines in ([], valid[1:], valid[:1] + [valid[1].replace("11,11", "11,110")],
                      valid + ["[Server thread/ERROR] [other/]: unrelated error"]):
            with self.assertRaises(RuntimeError):
                tanks.audit_save_refusal(lines)

    def test_guard_event_error_without_exact_chunk_save_error_is_insufficient(self):
        with self.assertRaises(RuntimeError):
            tanks.audit_save_refusal([
                "[Server thread/ERROR] [net.minecraftforge/]: Exception caught during firing event: " + tanks.SAVE_REFUSAL])


if __name__ == "__main__":
    unittest.main()
