"""Focused evidence-check tests; no Java or packaged server is started."""

import copy
import hashlib
import json
import tempfile
import struct
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import run_v180_pump_smoke as pumps


def native_fixture(phase="checkpoint"):
    palettes = {}
    words = {}
    def cell(position, state):
        y = position[1] >> 4
        palette = palettes.setdefault(y, [{"Name": "minecraft:air"}])
        if state not in palette:
            palette.append(state)
        index = palette.index(state)
        data = words.setdefault(y, [0] * 256)
        offset = ((position[1] & 15) << 8) | ((position[2] & 15) << 4) | (position[0] & 15)
        data[offset // 16] |= index << ((offset % 16) * 4)
    entities = []
    for index, position in enumerate(pumps.POSITIONS):
        cell(position, {"Name": pumps.NS + ":pump"})
        entities.append({"id": pumps.NS + ":pump", "x": position[0], "y": position[1], "z": position[2],
                         pumps.ROOT: pumps.expected_root(index, phase)})
    cell(pumps.INTAKE, {"Name": "minecraft:lava", "Properties": {"level": "1"}})
    cell(pumps.SOURCE, {"Name": "minecraft:lava", "Properties": {"level": "0" if phase == "checkpoint" else "2"}})
    sections = [{"Y": y, "block_states": {"palette": palette, "data": words[y]}} for y, palette in palettes.items()]
    return {"xPos": 13, "zPos": 13, "sections": sections, "block_entities": entities}


def reports(phase="terminal"):
    result = []
    for index, name in enumerate(pumps.NAMES):
        refused = index in (1, 2, 3, 4)
        root = pumps.expected_root(index, phase)
        row = {"phase": phase, "cell": name, "repair": refused,
               "fluid_cap": not refused, "energy_cap": not refused,
               "energy": 0 if refused else root["energy"],
               "amount": 0 if refused else root["fluid"].get("Amount", 0), "cooldown": 0,
               "status": "REPAIR_REQUIRED" if refused else "NO_OWNER" if index != 6
               else "SEARCHING" if phase == "checkpoint" else "NO_ENERGY"}
        result.append(console("ARCE_PUMP_REPORT " + json.dumps(row)))
    result.append(console("ARCE_PUMP_REPORT_END phase=" + phase))
    return result


def console(message, say=False):
    return "[21:03:08] [Server thread/INFO] [minecraft/MinecraftServer]: " + ("[Server] " if say else "") + message


def nbt_payload(node):
    kind, value = node
    if kind in (1, 2, 3, 4):
        return struct.pack({1: ">b", 2: ">h", 3: ">i", 4: ">q"}[kind], value)
    if kind == 8:
        encoded = value.encode("utf-8")
        return struct.pack(">H", len(encoded)) + encoded
    if kind == 11:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">i", item) for item in value)
    if kind == 9:
        child_kind = value[0][0] if value else 0
        assert all(child[0] == child_kind for child in value)
        return bytes([child_kind]) + struct.pack(">i", len(value)) + b"".join(nbt_payload(child) for child in value)
    if kind == 10:
        return b"".join(bytes([child[0]]) + struct.pack(">H", len(name.encode("utf-8"))) + name.encode("utf-8")
                        + nbt_payload(child) for name, child in value.items()) + b"\x00"
    raise AssertionError("Unknown test-only tag kind")


def native_schema_fixture(phase="checkpoint"):
    entities = []
    for index, position in enumerate(pumps.POSITIONS):
        entities.append((10, {"id": (8, pumps.NS + ":pump"),
                              "x": (3, position[0]), "y": (3, position[1]), "z": (3, position[2]),
                              pumps.ROOT: pumps.expected_typed(pumps.expected_root(index, phase))}))
    return 10, {"xPos": (3, 13), "zPos": (3, 13), "block_entities": (9, entities)}


def compressed_native(node):
    return pumps.zlib.compress(b"\x0a\x00\x00" + nbt_payload(node))


class PumpNativeEvidenceTests(unittest.TestCase):
    def test_fixed_geometry_reach_and_fixture_owner(self):
        self.assertEqual((13, 13), pumps.CHUNK)
        self.assertEqual([(210, 200, 212), (212, 200, 212), (214, 200, 212), (216, 200, 212),
                          (218, 200, 212), (220, 200, 212), (216, 200, 216)], list(pumps.POSITIONS))
        self.assertEqual(64, pumps.POSITIONS[-1][1] - pumps.INTAKE[1])
        self.assertTrue(all((x >> 4, z >> 4) == pumps.CHUNK for x, _, z in (*pumps.POSITIONS, pumps.INTAKE, pumps.SOURCE)))
        self.assertEqual([402653184, 0, 0, 6], pumps.OWNER)

    def test_exact_supported_refused_and_search_roots_at_native_coordinates(self):
        original_chunk = pumps.motors.CHUNK
        for phase in ("checkpoint", "terminal"):
            fixture = native_fixture(phase)
            saved = copy.deepcopy(fixture)
            result = pumps.check_chunk(fixture, phase)
            self.assertEqual(set(pumps.NAMES), set(result))
            self.assertEqual(16_001, result["overflow"]["fluid"]["Amount"])
            self.assertEqual("opaque-pump-root", result["unsupported"])
            self.assertEqual(fixture, saved)
            self.assertEqual(original_chunk, pumps.motors.CHUNK)

    def test_palette_presence_alone_does_not_prove_an_exact_pump(self):
        fixture = native_fixture()
        fixture["sections"][0]["block_states"]["data"] = [0] * 256
        with self.assertRaises(pumps.server.SmokeError):
            pumps.check_chunk(fixture, "checkpoint")

    def test_wrong_chunk_duplicate_native_carriers_and_identity_are_rejected(self):
        for change in (lambda c: c.update(xPos=12),
                       lambda c: c["block_entities"].append(copy.deepcopy(c["block_entities"][0])),
                       lambda c: c["block_entities"][0].update(id="minecraft:chest"),
                       lambda c: c["block_entities"][0].update(x=211)):
            fixture = native_fixture(); change(fixture)
            with self.assertRaises(pumps.server.SmokeError):
                pumps.check_chunk(fixture, "checkpoint")

    def test_each_schema_resource_owner_metadata_and_frontier_change_is_rejected(self):
        for index, mutate in ((0, lambda r: r.update(energy=0)),
                              (0, lambda r: r["fluid"].update(Amount=12_344)),
                              (1, lambda r: r["fluid"].update(Amount=16_000)),
                              (2, lambda r: r.update(schema=1)),
                              (2, lambda r: r.pop("extension")),
                              (3, lambda r: r["fluid"].update(Amount=0)),
                              (5, lambda r: r["fluid"]["Tag"].update(batch="changed")),
                              (6, lambda r: r["owner"].__setitem__(3, 7)),
                              (6, lambda r: r.update(frontier=[])),
                              (6, lambda r: r.update(cooldown=1)),
                              (6, lambda r: r.update(schema=True))):
            fixture = native_fixture(); mutate(fixture["block_entities"][index][pumps.ROOT])
            with self.subTest(index=index), self.assertRaises(pumps.server.SmokeError):
                pumps.check_chunk(fixture, "checkpoint")

    def test_opaque_unsupported_root_cannot_be_replaced_by_an_empty_compound(self):
        fixture = native_fixture(); fixture["block_entities"][4][pumps.ROOT] = {}
        with self.assertRaises(pumps.server.SmokeError):
            pumps.check_chunk(fixture, "checkpoint")

    def test_source_consumption_is_phase_specific_not_arbitrary_block_disappearance(self):
        source = {"Name": "minecraft:lava", "Properties": {"level": "0"}}
        self.assertTrue(pumps.source_state_valid(source, "checkpoint"))
        self.assertFalse(pumps.source_state_valid(source, "terminal"))
        self.assertTrue(pumps.source_state_valid({"Name": "minecraft:air"}, "terminal"))
        self.assertTrue(pumps.source_state_valid({"Name": "minecraft:lava", "Properties": {"level": "1"}}, "terminal"))
        for value in ({"Name": "minecraft:stone"}, {"Name": "minecraft:water", "Properties": {"level": "1"}},
                      {"Name": "minecraft:lava", "Properties": {"level": "16"}}):
            self.assertFalse(pumps.source_state_valid(value, "terminal"))

    def test_console_typed_reports_bind_capability_refusal_and_resources(self):
        self.assertEqual(7, len(pumps.check_reports(reports(), "terminal")))
        self.assertEqual(7, len(pumps.check_reports(reports("checkpoint"), "checkpoint")))
        for mutate in (lambda r: r.__setitem__(1, r[1].replace('"repair": true', '"repair": false')),
                       lambda r: r.__setitem__(1, r[1].replace('"energy_cap": false', '"energy_cap": true')),
                       lambda r: r.__setitem__(6, r[6].replace('"amount": 1000', '"amount": 0')),
                       lambda r: r.__setitem__(6, r[6].replace('"cooldown": 0', '"cooldown": 1'))):
            rows = reports(); mutate(rows)
            with self.assertRaises(pumps.server.SmokeError):
                pumps.check_reports(rows, "terminal")

    def test_duplicate_project_logger_wrong_phase_or_missing_end_is_not_evidence(self):
        for rows in (reports()[:-1], reports() + [reports()[0]], reports()[::-1],
                     [row.replace("[minecraft/MinecraftServer]", "[advancedrocketrycommunity]") for row in reports()],
                     reports("checkpoint")):
            with self.assertRaises(pumps.server.SmokeError):
                pumps.check_reports(rows, "terminal")

    def test_artifact_requires_explicit_matching_frozen_hash(self):
        expected = "a" * 64
        with patch.object(pumps.motors, "artifact", return_value={"sha256": expected}) as artifact:
            self.assertEqual(expected, pumps.bind_artifact(Path("fixture.jar"), expected)["sha256"])
            with self.assertRaises(pumps.server.SmokeError):
                pumps.bind_artifact(Path("fixture.jar"), "b" * 64)
            with self.assertRaises(pumps.server.SmokeError):
                pumps.bind_artifact(Path("fixture.jar"), "missing")
            self.assertEqual(2, artifact.call_count)

    def test_report_json_byte_field_duplicate_key_and_numeric_type_bounds(self):
        row = reports()[0]
        variants = [row.replace('"energy": 7654', '"energy": true'),
                    row.replace('"energy": 7654', '"energy": 7654.0'),
                    row.replace('"energy": 7654', '"energy": NaN'),
                    row.replace('"energy": 7654', '"energy": 7654,"energy":7654'),
                    row.replace('"status": "NO_OWNER"', '"status":"NO_OWNER","extra":1'),
                    console("ARCE_PUMP_REPORT {not-json}"), console("ARCE_PUMP_REPORT []"),
                    console('ARCE_PUMP_REPORT {"extra":"' + "x" * 4096 + '"}'),
                    console('ARCE_PUMP_REPORT {"extra":"' + "\u754c" * 1366 + '"}')]
        for variant in variants:
            with self.subTest(variant=variant[:120]), self.assertRaises(pumps.server.SmokeError):
                pumps.check_reports([variant] + reports()[1:], "terminal")

    def test_report_end_sequence_and_whole_line_are_strict(self):
        for lines in (reports() + [reports()[-1]], [reports()[-1]] + reports(),
                      reports() + [console("ARCE_PUMP_REPORT_WAIT")],
                      [line + " extra" for line in reports()],
                      ["echo " + line for line in reports()],
                      [line.replace("Server thread/INFO", "Server thread/ERROR") for line in reports()],
                      [line.replace("Server thread/INFO", "Worker-Main-1/INFO") for line in reports()]):
            with self.assertRaises(pumps.server.SmokeError):
                pumps.check_reports(lines, "terminal")

    def test_native_schema_preserves_integer_widths_uuid_array_and_no_frontier(self):
        for phase in ("checkpoint", "terminal"):
            self.assertEqual(7, len(pumps.check_native_schema(compressed_native(native_schema_fixture(phase)), phase)))
        for index, field, replacement in ((0, "schema", (1, 1)), (0, "energy", (4, 7654)),
                                           (6, "owner", (9, [(3, number) for number in pumps.OWNER])),
                                           (6, "frontier", (9, [])), (0, "cooldown", (1, 0))):
            fixture = native_schema_fixture()
            fixture[1]["block_entities"][1][index][1][pumps.ROOT][1][field] = replacement
            with self.subTest(index=index, field=field), self.assertRaises(pumps.server.SmokeError):
                pumps.check_native_schema(compressed_native(fixture), "checkpoint")
        fixture = native_schema_fixture()
        fixture[1]["block_entities"][1][1][1][pumps.ROOT][1]["fluid"][1]["Amount"] = (4, 16001)
        with self.assertRaises(pumps.server.SmokeError):
            pumps.check_native_schema(compressed_native(fixture), "checkpoint")

    def test_native_schema_wrong_carriers_compression_trailing_and_duplicate_keys_refused(self):
        fixture = native_schema_fixture()
        fixture[1]["block_entities"][1].append(copy.deepcopy(fixture[1]["block_entities"][1][0]))
        with self.assertRaises(pumps.server.SmokeError):
            pumps.check_native_schema(compressed_native(fixture), "checkpoint")
        with self.assertRaises(pumps.server.SmokeError):
            pumps.check_native_schema(compressed_native(native_schema_fixture()) + b"trailing", "checkpoint")
        with self.assertRaises(pumps.server.SmokeError):
            pumps.check_native_schema(bytes(pumps.regions.MAX_COMPRESSED_CHUNK + 1), "checkpoint")
        with self.assertRaises(pumps.server.SmokeError):
            pumps.check_native_schema(pumps.zlib.compress(bytes(pumps.regions.MAX_EXPANDED_CHUNK + 1)), "checkpoint")
        data = b"\x0a\x00\x00" + b"\x03\x00\x01x\x00\x00\x00\x01" * 2 + b"\x00"
        with self.assertRaises(ValueError):
            pumps.check_native_schema(pumps.zlib.compress(data), "checkpoint")

    def test_clean_stop_requires_genuine_stop_then_completed_dimension_save(self):
        stopping = console("Stopping server")
        saved = console("ThreadedAnvilChunkStorage: All dimensions are saved")
        self.assertEqual({"stopping_line": 2, "saved_after_stop_line": 3}, pumps.check_clean_stop([saved, stopping, saved]))
        for lines in ([], [saved], [stopping], [saved, stopping], [stopping, stopping, saved],
                      ["echo " + stopping, saved], [stopping, saved.replace("INFO", "ERROR")]):
            with self.assertRaises(pumps.server.SmokeError):
                pumps.check_clean_stop(lines)


class PumpReadinessTests(unittest.TestCase):
    def test_ticket_or_stale_marker_does_not_substitute_for_fresh_full_barrier(self):
        class Process:
            def __init__(self): self.lines = [console("ARCE_PUMP_FULL_LOADED", True)]; self.attempts = 0
            def command(self, value):
                if value.startswith("execute"):
                    self.attempts += 1
                    if self.attempts == 2: self.lines.append(console("ARCE_PUMP_FULL_LOADED", True))
                else: self.lines.append(console(value.removeprefix("say "), True))
            def wait_for(self, marker, timeout, start_at):
                self.timeout = timeout
                self.assert_seen = any(marker.search(line) for line in self.lines[start_at:])
                assert self.assert_seen
        process, commands = Process(), []
        with patch.object(pumps.time, "monotonic", side_effect=[0, 0, .1, .15, .2, .25, .3, .35]), patch.object(pumps.time, "sleep"):
            pumps.wait_full_loaded(process, commands)
        self.assertEqual(2, process.attempts); self.assertEqual(4, len(commands)); self.assertLess(process.timeout, 60)

    def test_unloaded_chunk_uses_one_original_total_deadline(self):
        class Process:
            def __init__(self): self.lines = []
            def command(self, value):
                if value.startswith("say "): self.lines.append(console(value.removeprefix("say "), True))
            def wait_for(self, marker, timeout, start_at): assert timeout <= 60
        commands = []
        with patch.object(pumps.time, "monotonic", side_effect=[0, 0, 0, 0, 60, 60]), patch.object(pumps.time, "sleep"), self.assertRaises(pumps.server.SmokeError):
            pumps.wait_full_loaded(Process(), commands)
        self.assertEqual(2, len(commands))

    def test_terminal_wait_requires_actual_hook_response_not_only_barrier(self):
        class Process:
            def __init__(self): self.lines = []
            def command(self, value):
                if value.startswith("say "): self.lines.append(console(value.removeprefix("say "), True))
            def wait_for(self, marker, timeout, start_at): pass
        with patch.object(pumps.time, "monotonic", return_value=0), self.assertRaises(pumps.server.SmokeError):
            pumps.wait_terminal(Process(), [])

    def test_terminal_retry_accepts_one_complete_current_report_after_wait(self):
        class Process:
            def __init__(self): self.lines = []; self.attempts = 0
            def command(self, value):
                if value.startswith("arce"):
                    self.attempts += 1
                    self.lines.extend(reports() if self.attempts == 2 else [console("ARCE_PUMP_REPORT_WAIT")])
                else: self.lines.append(console(value.removeprefix("say "), True))
            def wait_for(self, marker, timeout, start_at): assert any(marker.search(line) for line in self.lines[start_at:])
        with patch.object(pumps.time, "monotonic", side_effect=[0, 0, .1, .15, .2, .25, .3, .35]), patch.object(pumps.time, "sleep"):
            self.assertEqual(7, len(pumps.wait_terminal(Process(), [])))

    def test_authoritative_optional_timestamp_and_security_prefix(self):
        for prefix in ("[Server thread/INFO] [minecraft/MinecraftServer]: [Server] ",
                       "[21:03:08] [Server thread/INFO] [minecraft/MinecraftServer]: [Not Secure] [Server] "):
            self.assertIsNotNone(pumps.FULL_READY.search(prefix + "ARCE_PUMP_FULL_LOADED"))
        for line in ("ARCE_PUMP_FULL_LOADED", "[Server] ARCE_PUMP_FULL_LOADED", "echo " + console("ARCE_PUMP_FULL_LOADED", True),
                     console("ARCE_PUMP_FULL_LOADED trailing", True), console("ARCE_PUMP_FULL_LOADED", True).replace("INFO", "ERROR"),
                     console("ARCE_PUMP_FULL_LOADED", True).replace("Server thread", "Worker-Main-1"),
                     console("ARCE_PUMP_FULL_LOADED", True).replace("MinecraftServer", "OtherLogger"),
                     console("ARCE_PUMP_FULL_LOADED", True).replace("[21:03:08]", "[21:3:08]")):
            self.assertIsNone(pumps.FULL_READY.search(line))

    def test_late_wake_rejected_even_when_valid_marker_or_complete_reports_arrived(self):
        class Process:
            def __init__(self, report): self.lines = []; self.report = report; self.waited = False
            def command(self, value):
                if value.startswith("say "): self.lines.append(console(value.removeprefix("say "), True))
                elif self.report: self.lines.extend(reports())
                else: self.lines.append(console("ARCE_PUMP_FULL_LOADED", True))
            def wait_for(self, marker, timeout, start_at):
                self.waited = True
                assert any(marker.search(line) for line in self.lines[start_at:])
        for method, report in ((pumps.wait_full_loaded, False), (pumps.wait_terminal, True)):
            process = Process(report)
            with patch.object(pumps.time, "monotonic", side_effect=[0, 0, 0, 61]), self.assertRaises(pumps.server.SmokeError):
                method(process, [])
            self.assertTrue(process.waited)

    def test_deadline_before_wait_and_maximum_barriers_cannot_be_bypassed(self):
        class Process:
            def __init__(self, report): self.lines = []; self.report = report; self.waits = 0
            def command(self, value):
                if value.startswith("say "): self.lines.append(console(value.removeprefix("say "), True))
                elif self.report: self.lines.append(console("ARCE_PUMP_REPORT_WAIT"))
            def wait_for(self, marker, timeout, start_at):
                self.waits += 1
                assert any(marker.search(line) for line in self.lines[start_at:])
        for method, report in ((pumps.wait_full_loaded, False), (pumps.wait_terminal, True)):
            process, commands = Process(report), []
            with patch.object(pumps.time, "monotonic", side_effect=[0, 0, 60]), self.assertRaises(pumps.server.SmokeError):
                method(process, commands)
            self.assertEqual(0, process.waits)
            process, commands = Process(report), []
            with patch.object(pumps.time, "monotonic", return_value=0), patch.object(pumps.time, "sleep"), self.assertRaises(pumps.server.SmokeError):
                method(process, commands)
            self.assertEqual(240, process.waits)
            self.assertEqual(480, len(commands))
            self.assertEqual(240, len(set(commands[1::2])))
            for timeout in (0, -1, 61):
                with self.assertRaises(pumps.server.SmokeError):
                    method(process, [], timeout)

    def test_terminal_wait_cannot_mix_partial_duplicate_or_malformed_report_with_wait(self):
        class Process:
            def __init__(self, response): self.lines = []; self.response = response
            def command(self, value):
                self.lines.extend([console(value.removeprefix("say "), True)] if value.startswith("say ") else self.response)
            def wait_for(self, marker, timeout, start_at): assert any(marker.search(line) for line in self.lines[start_at:])
        for response in ([reports()[0], console("ARCE_PUMP_REPORT_WAIT")], [console("ARCE_PUMP_REPORT_WAIT")] * 2,
                         [console("ARCE_PUMP_REPORT_WAIT code=unknown")], [console("ARCE_PUMP_REPORT {not-json}")]):
            with patch.object(pumps.time, "monotonic", return_value=0), self.assertRaises(pumps.server.SmokeError):
                pumps.wait_terminal(Process(response), [])

    def test_full_marker_after_barrier_is_not_evidence_for_that_probe(self):
        class Process:
            def __init__(self): self.lines = []
            def command(self, value):
                if value.startswith("say "):
                    self.lines.extend([console(value.removeprefix("say "), True), console("ARCE_PUMP_FULL_LOADED", True)])
            def wait_for(self, marker, timeout, start_at): assert any(marker.search(line) for line in self.lines[start_at:])
        with patch.object(pumps.time, "monotonic", side_effect=[0, 0, 0, 0, 60, 60]), \
                patch.object(pumps.time, "sleep"), self.assertRaises(pumps.server.SmokeError):
            pumps.wait_full_loaded(Process(), [])

    def test_duplicate_or_non_console_barrier_is_refused(self):
        class Process:
            def __init__(self, lines): self.lines = lines
        marker = console("ARCE_PUMP_LOAD_END_0", True)
        self.assertEqual([marker], pumps.barrier_lines(Process([marker]), 0, "ARCE_PUMP_LOAD_END_0"))
        for lines in ([marker, marker], ["echo " + marker], [marker.replace("INFO", "ERROR")], ["ARCE_PUMP_LOAD_END_0"]):
            with self.assertRaises(pumps.server.SmokeError):
                pumps.barrier_lines(Process(lines), 0, "ARCE_PUMP_LOAD_END_0")


if __name__ == "__main__":
    unittest.main()
