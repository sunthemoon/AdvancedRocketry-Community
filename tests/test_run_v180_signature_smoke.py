"""Synthetic, bounded negative checks; these are not historical/native server receipts."""
import ast
import copy
import hashlib
import json
import re
import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import zlib

from scripts import run_v180_signature_smoke as sig

PREFIX = "[12:34:56] [Server thread/INFO] [minecraft/MinecraftServer]: "
SAY = PREFIX + "[Server] "


def compound(**fields): return 10, fields
def stack(name="", count=0): return compound(**({"id": (8, name), "Count": (1, count)} if count else {}))


def synthetic_chunk():
    """Only codec/oracle shapes; omitted unrelated domain fields are not loadable fixtures."""
    entities, blocks = [], {}
    for row in range(7):
        for machine in range(3):
            pos = sig.position(row, machine)
            owner = str(__import__("uuid").UUID(int=1 + row * 3 + machine))
            process = compound(schema_version=(3, 1), state=(8, "waiting_energy"), resource_revision=(4, 4),
                definition_id=(8, sig.NS + sig.RECIPES[machine]), recipe_signature=(8, sig.LEGACY_HASHES[machine]),
                progress_ticks=(3, 5), consumed_energy=(4, 200 if machine == 1 else 100),
                last_applied_transaction=(8, ""), failure_code=(8, "needs_energy"), failure_subject=(8, "energy_input"))
            parent = {"id": (8, sig.BLOCK_IDS[machine]), "x": (3, pos[0]), "y": (3, pos[1]), "z": (3, pos[2]), sig.PROCESS: process}
            if machine < 2: parent["arce_multiblock"] = compound(machine_instance_id=(8, owner))
            if machine == 1:
                items = [stack("minecraft:iron_ingot", 2), stack("minecraft:redstone", 2)] + [stack() for _ in range(5)]
                parent["arce_precision_resources"] = compound(schema_version=(3, 1), machine_id=(8, owner), phase=(8, "active"), items=(9, (10, items)))
            if machine == 2:
                parent[sig.LEGACY] = compound(schema_version=(3, 1), inventory=compound(Size=(3, 4),
                    Items=(9, (10, [compound(Slot=(3, 0), id=(8, sig.NS + "empty_canister"), Count=(1, 2))]))),
                    fluid=compound(FluidName=(8, "minecraft:water"), Amount=(3, 1000)), energy=(3, 0), progress=(3, 5), active_recipe=(8, sig.NS + sig.RECIPES[machine]))
            entities.append((10, parent)); blocks[pos] = sig.BLOCK_IDS[machine]
            for index, port_pos in enumerate(sig.port_positions(row, machine)):
                role = ("item_input", "fluid_input", "energy_input", "item_output")[index] if machine == 0 else "item_input" if index < 5 else "item_output" if index < 7 else "energy_input"
                port_type = ("rolling_machine_" if machine == 0 else "precision_assembler_") + role + "_port"
                native = {"schema_version": (3, 1), "port_type": (8, port_type)}
                if role == "energy_input": native["energy"] = (3, 0)
                elif role == "fluid_input": native["fluid"] = compound(FluidName=(8, "minecraft:water"), Amount=(3, 500))
                else: native["item"] = stack("minecraft:iron_ingot", 2) if machine == 0 and index == 0 else stack()
                key = "arce_rolling_port" if machine == 0 else "arce_precision_port"
                entities.append(compound(id=(8, sig.NS + ("rolling_machine_port" if machine == 0 else "precision_assembler_port")),
                    x=(3, port_pos[0]), y=(3, port_pos[1]), z=(3, port_pos[2]), **{key: (10, native)}))
                blocks[port_pos] = sig.NS + port_type
    sections = []
    for section_y in range(8, 15):
        names = ["minecraft:air"] + sorted(set(blocks.values()))
        palette = [compound(Name=(8, name)) for name in names]
        bits = max(4, (len(palette) - 1).bit_length()); per_word = 64 // bits
        words = [0] * ((4096 + per_word - 1) // per_word)
        for pos, name in blocks.items():
            if pos[1] >> 4 == section_y:
                index = (pos[1] & 15) * 256 + (pos[2] & 15) * 16 + (pos[0] & 15)
                words[index // per_word] |= names.index(name) << ((index % per_word) * bits)
        words = [value if value < 2**63 else value - 2**64 for value in words]
        sections.append(compound(Y=(1, section_y), block_states=compound(palette=(9, (10, palette)), data=(12, words))))
    return compound(xPos=(3, 15), zPos=(3, 15), sections=(9, (10, sections)), block_entities=(9, (10, entities)))


def region_bytes(root, sectors=16):
    compressed = zlib.compress(sig.encode(root)); record = struct.pack(">I", len(compressed) + 1) + b"\x02" + compressed
    if len(record) > sectors * 4096: raise AssertionError("Synthetic allocation too small")
    header = bytearray(8192); index = 4 * (15 + 15 * 32); header[index:index + 4] = bytes([0, 0, 2, sectors])
    return bytes(header) + record + b"\0" * (sectors * 4096 - len(record))


def rows(selected=0):
    result = []
    for index, case in enumerate(sig.CASES):
        for machine in sig.MACHINES:
            refused = index in (0, 1, 4, 5, 6)
            result.append({"cell": case, "machine": machine, "marker": index in (2, 3, 6),
                "energy_simulated": 0 if refused else 1, "item_simulated": 0, "fluid_simulated": -1 if machine == "precision" else 0,
                "tag_generation": 1, "alternatives": [["minecraft:iron_ingot"]] if index in (2, 3) else [],
                "signature": "ab" * 32 if index in (2, 3) else "NOT_QUERIED",
                "status": ("INVALID_RECIPE" if machine == "electrolyzer" else "RECOVERY_REQUIRED") if index < 2 else "UNSUPPORTED_DATA" if refused else "WAITING_ENERGY",
                "failure": "NOT_EXPOSED" if machine == "electrolyzer" else "RECOVERY_DIVERGED" if refused else "NEEDS_ENERGY",
                "subject": "NOT_EXPOSED" if machine == "electrolyzer" else "signature_migration_unproven" if index < 2 else "",
                "progress": 5, "protected": refused})
    return [row for row in result if row["machine"] == sig.MACHINES[selected]]


def report_lines(values=None, machine=0):
    return [PREFIX + "ARCE_SIGNATURE_REPORT " + json.dumps(value, separators=(",", ":")) for value in (values or rows(machine))] + [PREFIX + "ARCE_SIGNATURE_REPORT_END machine=" + sig.MACHINES[machine]]


class ReportProcess:
    """Only synthetic console/barrier sequencing; never creates a server."""
    def __init__(self, values, clock, late=False):
        self.values, self.clock, self.late = values, clock, late
        self.lines, self.calls = [], 0
    def command(self, command):
        if command.startswith("arce signature release-test"):
            values = self.values[min(self.calls, len(self.values) - 1)]
            self.lines.extend(report_lines(values, sig.MACHINES.index(values[0]["machine"])))
            self.calls += 1
        else:
            self.lines.append(SAY + command.removeprefix("say "))
    def wait_for(self, regex, remaining, start_at):
        if self.late: self.clock[0] = 61
        if not any(regex.search(line) for line in self.lines[start_at:]):
            raise sig.server.SmokeError("No authoritative barrier")


def current_phase(name, machine):
    tree = ast.parse(Path(sig.__file__).read_bytes())
    main = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "main")
    function = next(node for node in main.body if isinstance(node, ast.FunctionDef) and node.name == name)
    module = ast.Module(body=[copy.deepcopy(function)], type_ignores=[])
    namespace = dict(sig.__dict__, machine=machine, args=type("Args", (), {"machine": sig.MACHINES[machine]})())
    exec(compile(ast.fix_missing_locations(module), "UNCHANGED_SOURCE_PHASE", "exec"), namespace)
    return namespace[name], function


class FinishProcess(ReportProcess):
    def __init__(self, values, clock):
        super().__init__(values, clock); self.power_calls = 0; self.reports_before_power = None
    def command(self, command):
        if command.endswith("power-current"):
            self.power_calls += 1; self.reports_before_power = self.calls
            self.lines.append(PREFIX + "ARCE_SIGNATURE_POWER_CURRENT_END machine=" + self.values[0][0]["machine"])
        else:
            super().command(command)


class SignatureSmokeTest(unittest.TestCase):
    def test_all_four_current_host_entries_keep_explicit_startup_handling(self):
        phases = {"current_partials": "ARCE_SIGNATURE_BEFORE_CURRENT",
                  "reload_report": "ARCE_SIGNATURE_RESTART1_BEFORE_RELOAD",
                  "final_report": "ARCE_SIGNATURE_FINAL"}
        for name, marker in phases.items():
            _, function = current_phase(name, 0)
            first = function.body[0]
            call = first.value if isinstance(first, (ast.Expr, ast.Assign)) else None
            self.assertIsInstance(call, ast.Call)
            self.assertEqual("wait_refusals", call.func.id)
            self.assertEqual(marker, call.args[2].value)
            self.assertEqual("machine", call.args[3].id)
        _, finish = current_phase("finish_current", 0)
        self.assertEqual("deadline", finish.body[0].targets[0].id)
        self.assertEqual("powered", finish.body[1].targets[0].id)
        self.assertFalse(finish.body[1].value.value)
        loop = next(node for node in finish.body if isinstance(node, ast.For))
        startup = next(node for node in loop.body if isinstance(node, ast.If)
                       and isinstance(node.test, ast.UnaryOp) and isinstance(node.test.operand, ast.Call))
        self.assertEqual("check_refusals", startup.test.operand.func.id)
        self.assertEqual("startup_pending", startup.test.operand.keywords[0].arg)
        self.assertIsInstance(startup.test.operand.keywords[0].value, ast.UnaryOp)
        self.assertEqual("powered", startup.test.operand.keywords[0].value.operand.id)

    def test_real_finish_phase_awaits_mixed_load_settled_before_ordinary_power(self):
        for machine in (0, 1):
            pending = rows(machine)
            for row in pending[:2]: row["subject"] = "signature_migration_pending"
            mixed = copy.deepcopy(pending); mixed[0]["subject"] = "signature_migration_unproven"
            settled = rows(machine); complete = copy.deepcopy(settled)
            for row in complete[2:4]: row["progress"] = 0
            clock = [0]; process = FinishProcess([pending, mixed, settled, complete], clock)
            commands = []; receipt = {}; finish, _ = current_phase("finish_current", machine)
            with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), patch.object(sig.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
                finish(process, commands, receipt)
            self.assertEqual((1, 3, 4), (process.power_calls, process.reports_before_power, process.calls))
            self.assertEqual(complete, receipt["reports"])
            self.assertEqual(1, sum(command.endswith("power-current") for command in commands))

    def test_real_finish_phase_does_not_power_on_unsafe_or_unknown_startup(self):
        for key, value in (("subject", "unknown_reason"), ("marker", True),
                           ("protected", False), ("energy_simulated", 1)):
            pending = rows(); pending[0]["subject"] = "signature_migration_pending"; pending[0].update({key: value})
            clock = [0]; process = FinishProcess([pending], clock)
            finish, _ = current_phase("finish_current", 0)
            with self.subTest(key=key), self.assertRaises(sig.server.SmokeError): finish(process, [], {})
            self.assertEqual((0, 1), (process.power_calls, process.calls))

    def test_real_finish_phase_never_powers_a_permanently_pending_startup(self):
        pending = rows()
        for row in pending[:2]: row["subject"] = "signature_migration_pending"
        clock = [0]; process = FinishProcess([pending], clock); finish, _ = current_phase("finish_current", 0)
        with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), patch.object(sig.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
            with self.assertRaises(sig.server.SmokeError): finish(process, [], {})
        self.assertEqual((0, 240, 60), (process.power_calls, process.calls, clock[0]))

    def test_finish_startup_power_completion_share_one60second240report_budget(self):
        pending = rows()
        for row in pending[:2]: row["subject"] = "signature_migration_pending"
        settled = rows(); clock = [0]
        process = FinishProcess([pending] * 239 + [settled], clock)
        finish, _ = current_phase("finish_current", 0); receipt = {}
        with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), patch.object(sig.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
            with self.assertRaises(sig.server.SmokeError): finish(process, [], receipt)
        self.assertEqual((240, 1, {}), (process.calls, process.power_calls, receipt))
        self.assertLessEqual(clock[0], 60)
        class LatePower(FinishProcess):
            def wait_for(self, regex, remaining, start_at):
                super().wait_for(regex, remaining, start_at)
                if "ARCE_SIGNATURE_POWERED" in regex.pattern: self.clock[0] = 61
        clock = [0]; process = LatePower([settled], clock)
        with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), self.assertRaises(sig.server.SmokeError):
            finish(process, [], {})
        self.assertEqual((1, 1), (process.calls, process.power_calls))

    def test_settled_refusal_requires_runtime_reason_and_failure_pair(self):
        for machine in (0, 1):
            sig.check_refusals(rows(machine))
            for subject, failure in (("signature_migration_pending", "RECOVERY_DIVERGED"),
                                     ("journal_progress_missing", "RECOVERY_DIVERGED"),
                                     ("signature_migration_unproven", "INVALID_RECIPE")):
                values = rows(machine); values[0].update(subject=subject, failure=failure)
                with self.subTest(machine=machine, subject=subject, failure=failure):
                    with self.assertRaises(sig.server.SmokeError): sig.check_refusals(values)

    def test_only_load_pending_is_transient_and_never_final_acceptance(self):
        for machine in (0, 1):
            values = rows(machine)
            values[0]["subject"] = "signature_migration_pending"
            self.assertFalse(sig.check_refusals(values, startup_pending=True))
            with self.assertRaises(sig.server.SmokeError): sig.check_refusals(values)
            values[0]["subject"] = "unknown_refusal"
            with self.assertRaises(sig.server.SmokeError): sig.check_refusals(values, startup_pending=True)
        self.assertTrue(sig.check_refusals(rows(2), startup_pending=True))

    def test_refusal_wait_checks_mixed_load_runtime_then_returns_exact_settled_rows(self):
        for machine in (0, 1):
            pending = rows(machine)
            for value in pending[:2]: value["subject"] = "signature_migration_pending"
            mixed = copy.deepcopy(pending); mixed[0]["subject"] = "signature_migration_unproven"
            settled = rows(machine); clock = [0]; commands = []
            process = ReportProcess([pending, mixed, settled], clock)
            with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), patch.object(sig.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
                self.assertEqual(settled, sig.wait_refusals(process, commands, "STAGE", machine))
            self.assertEqual(3, process.calls)
            self.assertEqual(3, sum(command.startswith("arce signature release-test") for command in commands))
            self.assertTrue(all(command.startswith(("arce signature release-test", "say STAGE_")) for command in commands))

    def test_startup_poll_does_not_hide_marker_capability_status_or_protection_loss(self):
        for key, value in (("marker", True), ("protected", False), ("energy_simulated", 1),
                           ("item_simulated", 1), ("fluid_simulated", 1), ("status", "IDLE"),
                           ("failure", "NONE")):
            values = rows(); values[0].update(subject="signature_migration_pending", **{key: value})
            process = ReportProcess([values], [0])
            with self.subTest(key=key), self.assertRaises(sig.server.SmokeError):
                sig.wait_refusals(process, [], "STAGE", 0)
            self.assertEqual(1, process.calls)

    def test_load_pending_times_out_under_original60seconds240probes(self):
        values = rows()
        for value in values[:2]: value["subject"] = "signature_migration_pending"
        clock = [0]; process = ReportProcess([values], clock)
        with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), patch.object(sig.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
            with self.assertRaises(sig.server.SmokeError): sig.wait_refusals(process, [], "STAGE", 0)
        self.assertEqual((60, 240), (sig.WAIT_SECONDS, sig.MAX_PROBES))
        self.assertEqual(240, process.calls); self.assertEqual(60, clock[0])

    def test_settled_report_after_original_deadline_is_not_accepted(self):
        clock = [0]; process = ReportProcess([rows()], clock, late=True)
        with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), self.assertRaises(sig.server.SmokeError):
            sig.wait_refusals(process, [], "STAGE", 0)

    def test_electrolyzer_completed_native_slots_match_shipped_adapter(self):
        source_path = Path(__file__).resolve().parents[1] / "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/electrolyzer/ElectrolyzerBlockEntity.java"
        source = Path("\\\\?\\" + str(source_path.absolute())).read_text(encoding="utf-8") if __import__("os").name == "nt" else source_path.read_text(encoding="utf-8")
        slots = {name: int(value) for name, value in re.findall(r"public static final int (SLOT_[A-Z_]+) = (\d+);", source)}
        self.assertEqual((0, 1, 2, 3, 4), tuple(slots[name] for name in ("SLOT_INPUT", "SLOT_CHARGE", "SLOT_HYDROGEN", "SLOT_OXYGEN", "SLOT_COUNT")))
        self.assertIn("inventory.setStackInSlot(SLOT_HYDROGEN, hydrogen.copy());", source)
        self.assertIn("inventory.setStackInSlot(SLOT_OXYGEN, oxygen.copy());", source)
        root = synthetic_chunk(); resources = sig.carrier(root, sig.position(3, 2))[sig.LEGACY][1]
        resources["fluid"] = compound(FluidName=(8, "minecraft:empty"), Amount=(3, 0))
        resources["inventory"] = compound(Size=(3, slots["SLOT_COUNT"]), Items=(9, (10, [
            compound(Slot=(3, slots[field]), id=(8, sig.NS + name), Count=(1, 1))
            for field, name in (("SLOT_HYDROGEN", "hydrogen_canister"), ("SLOT_OXYGEN", "oxygen_canister"))])))
        sig.saved_resources(root, 3, 2, completed=True)

    def test_electrolyzer_completion_wrong_slots_and_extra_items_are_refused(self):
        for slots in ((1, 2), (2, 1), (3, 2), (2, 2), (2, 3, 1), (2, 3, 0)):
            with self.subTest(slots=slots):
                root = synthetic_chunk(); resources = sig.carrier(root, sig.position(3, 2))[sig.LEGACY][1]
                resources["fluid"] = compound(FluidName=(8, "minecraft:empty"), Amount=(3, 0))
                names = ("hydrogen_canister", "oxygen_canister", "empty_canister")
                resources["inventory"] = compound(Size=(3, 4), Items=(9, (10, [
                    compound(Slot=(3, slot), id=(8, sig.NS + names[index]), Count=(1, 1))
                    for index, slot in enumerate(slots)])))
                with self.assertRaises(sig.server.SmokeError): sig.saved_resources(root, 3, 2, completed=True)

    def test_all490_mutation_cells_are_unique_fixed_loaded_and_bounded(self):
        cells = set().union(*(sig.footprint(row, machine) for row in range(7) for machine in range(3)))
        self.assertEqual(490, len(cells)); self.assertTrue(all((x >> 4, z >> 4) == (15, 15) and 128 <= y <= 226 for x, y, z in cells))
        for row, machine in [(-1, 0), (7, 0), (0, 3), (True, 0)]:
            with self.assertRaises(sig.server.SmokeError): sig.position(row, machine)

    def test_seed_only_declared_current_or_historical_rows(self):
        self.assertEqual(22, sum(len(sig.seed_commands((0, 1), machine)) for machine in range(3)))
        self.assertIn("energy set value 100", "\n".join(sig.seed_commands((2, 3), 0)))
        for rows in [(0, 2), (4, 5), (2,), tuple(range(7))]:
            with self.assertRaises(sig.server.SmokeError): sig.seed_commands(rows, 0)

    def test_native_all_widths_arrays_empty_list_subtypes_round_trip(self):
        root = compound(xPos=(3, 15), zPos=(3, 15), byte=(1, -1), short=(2, 320), integer=(3, 257), long=(4, 2**40),
            float=(5, 1.5), double=(6, 2.5), string=(8, "bounded"), bytes=(7, b"\0\xff"), ints=(11, [0, -1]), longs=(12, [2**40]), empty=(9, (10, [])))
        compressed = zlib.compress(sig.encode(root)); decoded, count = sig.decode(compressed)
        self.assertEqual(root, decoded); self.assertEqual(14, count)

    def test_wrong_item_count_width_or_unapproved_metadata_refused(self):
        for data in [{"id": (8, "minecraft:iron_ingot"), "Count": (3, 2)},
                     {"id": (8, "minecraft:iron_ingot"), "Count": (1, 2), "tag": compound(batch=(8, "extra"))}]:
            with self.assertRaises(sig.server.SmokeError): sig.item((10, data), "minecraft:iron_ingot", 2)

    def test_wrong_fluid_amount_width_or_metadata_refused(self):
        for data in [{"FluidName": (8, "minecraft:water"), "Amount": (4, 1000)},
                     {"FluidName": (8, "minecraft:water"), "Amount": (3, 1000), "Tag": compound(batch=(8, "extra"))}]:
            with self.assertRaises(sig.server.SmokeError): sig.fluid((10, data), 1000)
        sig.fluid(compound(FluidName=(8, "minecraft:empty"), Amount=(3, 0)), 0, True)

    def test_node_depth_collection_byte_bounds_are_not_relaxed(self):
        with self.assertRaises(sig.server.SmokeError): sig.encode(compound(children=(9, (10, [compound() for _ in range(4096)]))))
        deep = compound()
        for _ in range(17): deep = compound(child=deep)
        with self.assertRaises(sig.server.SmokeError): sig.encode(deep)
        with self.assertRaises(sig.server.SmokeError): sig.encode(compound(array=(11, [0] * 4097)))
        with self.assertRaises(sig.server.SmokeError): sig.decode(b"x" * (128 * 1024 + 1))

    def test_duplicate_trailing_rootname_and_invalid_list_refused(self):
        values = [b"\x0a\0\0\x03\0\x01a\0\0\0\x01\x03\0\x01a\0\0\0\x01\0",
                  sig.encode(compound(xPos=(3, 15), zPos=(3, 15))) + b"x",
                  b"\x0a\0\x01x\0", b"\x0a\0\0\x09\0\x01a\0\0\0\0\x01\0"]
        for value in values:
            with self.assertRaises(sig.server.SmokeError): sig.decode(zlib.compress(value))

    def test_compression_trailing_bytes_refused(self):
        with self.assertRaises(sig.server.SmokeError): sig.decode(zlib.compress(sig.encode(synthetic_chunk())) + b"x")

    def test_all_three_synthetic_partial_oracles_and_node_count(self):
        root = synthetic_chunk(); decoded, count = sig.decode(zlib.compress(sig.encode(root)))
        self.assertEqual(root, decoded); self.assertLessEqual(count, 4096)
        for machine in range(3): sig.partial(root, 0, machine)

    def test_old_marker_and_changed_clock_refused(self):
        for variant in range(2):
            root = synthetic_chunk(); parent = sig.carrier(root, sig.position(0, 0))
            if variant == 0: parent[sig.MARKER] = compound(schema_version=(3, 1))
            else: parent[sig.PROCESS][1]["consumed_energy"] = (4, 99)
            with self.assertRaises(sig.server.SmokeError): sig.partial(root, 0, 0)

    def test_injected_legacy_cut_retains_actual_partial_and_own_owner(self):
        root = synthetic_chunk(); total_allowed = 0
        for machine in range(3):
            candidate, allowed = sig.legacy_patch(root, machine); total_allowed += len(allowed)
            sig.retain_equal(root, candidate, (0, 2, 3))
            parent = sig.carrier(candidate, sig.position(1, machine))
            journal = parent[sig.JOURNAL][1]
            self.assertEqual((8, sig.owner(parent, 1, machine)), journal["machine_id"])
            self.assertEqual((8, "prepared"), journal["phase"])
            self.assertEqual(journal["before_fingerprint"], (8, sig.fingerprint(journal["before"])))
            self.assertNotIn(sig.MARKER, parent)
        self.assertEqual(48, total_allowed)

    def test_retention_rejects_root_resource_loss_and_scalar_width_change(self):
        root = synthetic_chunk(); candidate, _ = sig.legacy_patch(root, 0)
        after = copy.deepcopy(candidate); sig.carrier(after, sig.position(0, 0)).pop(sig.PROCESS)
        with self.assertRaises(sig.server.SmokeError): sig.retain_equal(candidate, after)
        after = copy.deepcopy(candidate); sig.carrier(after, sig.port_positions(0, 0)[2])["arce_rolling_port"][1]["energy"] = (4, 0)
        with self.assertRaises(sig.server.SmokeError): sig.retain_equal(candidate, after)

    def test_snapshot_order_and_fingerprint_identity(self):
        entries = [("fluid", "fluid_input", "minecraft:water", 500, 4000), ("item", "item_input", "minecraft:iron_ingot", 2, 64)]
        value = sig.snapshot(4, entries)
        self.assertEqual(value, sig.snapshot(4, list(reversed(entries))))
        self.assertNotEqual(sig.fingerprint(value), sig.fingerprint(sig.snapshot(5, entries)))
        with self.assertRaises(sig.server.SmokeError): sig.snapshot(4, entries * 2)

    def test_same_sector_patch_roundtrip_preserves_other_bytes_and_records(self):
        root = synthetic_chunk(); candidate, allowed = sig.legacy_patch(root, 0)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "region.mca"; path.write_bytes(region_bytes(root))
            before = path.read_bytes(); evidence = Path(directory) / "evidence"; evidence.mkdir()
            sig.patch_region(path, root, candidate, allowed, evidence)
            after = path.read_bytes(); self.assertEqual(before[:8192], after[:8192]); self.assertEqual(len(before), len(after))
            self.assertEqual(candidate, sig.decode(sig.regions._region_chunk(after, 15, 15))[0])
            self.assertEqual(before, (evidence / "region-before.mca").read_bytes())

    def test_patch_selects_maximum_zlib_compression_without_region_growth(self):
        root = synthetic_chunk(); candidate, allowed = sig.legacy_patch(root, 0)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "region.mca"
            before = region_bytes(root) + b"\xca" * 4096; path.write_bytes(before)
            evidence = Path(directory) / "evidence"; evidence.mkdir()
            native_compress = zlib.compress
            with patch.object(sig.zlib, "compress", wraps=native_compress) as compressor:
                sig.patch_region(path, root, candidate, allowed, evidence)
            self.assertEqual(1, compressor.call_count)
            self.assertEqual(2, len(compressor.call_args.args))
            self.assertEqual(sig.encode(candidate), compressor.call_args.args[0])
            self.assertEqual(9, compressor.call_args.args[1])
            after = path.read_bytes()
            index = 4 * (15 + 15 * 32)
            start = int.from_bytes(before[index:index + 3], "big") * 4096
            allocated = before[index + 3] * 4096
            self.assertEqual(before[:start], after[:start])
            self.assertEqual(before[start + allocated:], after[start + allocated:])
            self.assertEqual(len(before), len(after))
            self.assertEqual(candidate, sig.decode(sig.regions._region_chunk(after, 15, 15))[0])
            receipt = json.loads((evidence / "patch.json").read_bytes())
            self.assertLessEqual(receipt["record_bytes"], receipt["allocated_bytes"])

    def test_cut_still_refused_if_maximum_compression_exceeds_original_sectors(self):
        root = synthetic_chunk(); candidate, allowed = sig.legacy_patch(root, 0)
        parent = sig.carrier(candidate, sig.position(1, 0))
        blob = b"".join(hashlib.sha256(str(value).encode("ascii")).digest() for value in range(256))
        parent[sig.JOURNAL] = (10, {"fixture_blob_" + str(index): (7, blob[index * 2048:(index + 1) * 2048]) for index in range(4)})
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "region.mca"; before = region_bytes(root, sectors=1); path.write_bytes(before)
            self.assertGreater(len(zlib.compress(sig.encode(candidate), 9)) + 5, 4096)
            evidence = Path(directory) / "evidence"; evidence.mkdir()
            with self.assertRaisesRegex(sig.server.SmokeError, "original chunk sector allocation"):
                sig.patch_region(path, root, candidate, allowed, evidence)
            self.assertEqual(before, path.read_bytes())
            self.assertFalse((evidence / "region-after.mca").exists())
            self.assertFalse((evidence / "chunk-after.nbt.zlib").exists())
            self.assertFalse((evidence / "patch.json").exists())

    def test_patch_refuses_unknown_cell_and_unrelated_chunk_change(self):
        root = synthetic_chunk(); candidate, allowed = sig.legacy_patch(root, 0)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "region.mca"; path.write_bytes(region_bytes(root)); before = path.read_bytes()
            evidence = Path(directory) / "evidence"; evidence.mkdir()
            with self.assertRaises(sig.server.SmokeError): sig.patch_region(path, root, candidate, {(0, 0, 0)}, evidence)
            candidate[1]["DataVersion"] = (3, 1)
            with self.assertRaises(sig.server.SmokeError): sig.patch_region(path, root, candidate, allowed, evidence)
            self.assertEqual(before, path.read_bytes())

    def test_strict_console_reports_and_refusal_are_not_cap_presence(self):
        for machine in range(3):
            values = sig.check_reports(report_lines(machine=machine), machine); self.assertEqual(7, len(values)); sig.check_refusals(values)
        values = sig.check_reports(report_lines(), 0)
        values[0]["energy_simulated"] = 1
        with self.assertRaises(sig.server.SmokeError): sig.check_refusals(values)

    def test_missing_duplicate_out_of_order_and_post_end_rows_refused(self):
        for lines in [report_lines()[:-1], report_lines() + [PREFIX + "ARCE_SIGNATURE_REPORT_END machine=rolling"],
                      report_lines([rows()[1], rows()[0], *rows()[2:]]), report_lines() + [report_lines()[0]]]:
            with self.assertRaises(sig.server.SmokeError): sig.check_reports(lines, 0)

    def test_fake_nested_or_wrong_logger_marker_cannot_satisfy_report(self):
        for lines in [["[Other/INFO] [mod/Logger]: " + line for line in report_lines()],
                      [line.replace("minecraft/MinecraftServer", "mod/Logger") for line in report_lines()],
                      [line.replace("/INFO]", "/WARN]") for line in report_lines()]]:
            with self.assertRaises(sig.server.SmokeError): sig.check_reports(lines, 0)

    def test_duplicate_json_keys_and_boolean_numeric_values_refused(self):
        lines = report_lines(); lines[0] = lines[0].replace('"progress":5', '"progress":5,"progress":5')
        with self.assertRaises(sig.server.SmokeError): sig.check_reports(lines, 0)
        values = rows(); values[0]["energy_simulated"] = False
        with self.assertRaises(sig.server.SmokeError): sig.check_reports(report_lines(values), 0)

    def test_partial_reports_do_not_infer_future_status_from_marker(self):
        values = rows(); values[5]["status"] = "RECOVERY_REQUIRED"
        with self.assertRaises(sig.server.SmokeError): sig.check_refusals(values)

    def test_clean_stop_is_strict_and_not_crash(self):
        self.assertEqual("NOT_CLAIMED", sig.clean_stop([PREFIX + "Stopping server", PREFIX + "ThreadedAnvilChunkStorage: All dimensions are saved"])["crash_atomicity"])
        for lines in [[PREFIX + "Stopping server"], [PREFIX + "Stopping server"] * 2,
                      ["[mod/Logger]: " + PREFIX + "Stopping server", PREFIX + "ThreadedAnvilChunkStorage: All dimensions are saved"]]:
            with self.assertRaises(sig.server.SmokeError): sig.clean_stop(lines)

    def test_readiness_rejects_nested_full_and_late_barrier(self):
        class Fake:
            def __init__(self, prefix, late=False): self.lines, self.prefix, self.late = [], prefix, late
            def command(self, command):
                self.lines.append(self.prefix + ("ARCE_SIGNATURE_FULL_LOADED" if command.startswith("execute") else command[4:]))
            def wait_for(self, regex, remaining, start_at):
                if self.late: clock[0] = 61
                if not any(regex.search(line) for line in self.lines[start_at:]): raise sig.server.SmokeError("No authoritative barrier")
        clock = [0]
        with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), patch.object(sig.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)):
            with self.assertRaises(sig.server.SmokeError): sig.wait_full(Fake("[mod/INFO]: " + SAY), [])
            with self.assertRaises(sig.server.SmokeError): sig.wait_full(Fake(SAY, True), [])
            clock[0] = 0; sig.wait_full(Fake(SAY), [])

    def test_current_cut_signature_and_live_alternatives_preserved(self):
        root = synthetic_chunk(); values = [rows(machine) for machine in range(3)]
        for row in (2, 3):
            for machine in range(3):
                parent = sig.carrier(root, sig.position(row, machine)); parent[sig.PROCESS][1]["recipe_signature"] = (8, "ab" * 32)
                parent[sig.MARKER] = compound(schema_version=(3, 1), format=(8, "json_v1"), converted=(1, 0), recipe_id=(8, sig.NS + sig.RECIPES[machine]))
                values[machine][row]["alternatives"] = [["minecraft:iron_ingot"], ["minecraft:redstone"]] if machine == 1 else [[sig.NS + "empty_canister"]] if machine == 2 else [["minecraft:iron_ingot"]]
        for machine in range(3):
            candidate, allowed, transactions = sig.current_patch(root, values[machine], machine)
            self.assertEqual(1, len(allowed)); self.assertEqual(1, len(set(transactions)))
            self.assertEqual(sig.project(root, 2, machine), sig.project(candidate, 2, machine))
            self.assertEqual(sig.carrier(root, sig.position(3, machine))[sig.MARKER], sig.carrier(candidate, sig.position(3, machine))[sig.MARKER])

    def test_per_adapter_commands_and_mutations_do_not_cross_columns(self):
        root = synthetic_chunk()
        for machine in range(3):
            candidate, allowed = sig.legacy_patch(root, machine)
            for other in range(3):
                if other != machine:
                    sig.retain_equal(root, candidate, range(7), machines=(other,))
            known = {sig.position(row, machine) for row in range(7)} | set().union(*(set(sig.port_positions(row, machine)) for row in range(7)))
            self.assertTrue(allowed <= known)
            self.assertEqual(7, len(sig.prepare_commands(machine)))

    def test_wrong_adapter_end_and_foreign_report_rows_are_refused(self):
        for machine in range(3):
            with self.assertRaises(sig.server.SmokeError): sig.check_reports(report_lines(machine=(machine + 1) % 3), machine)
            lines = report_lines(machine=machine); lines[-1] = PREFIX + "ARCE_SIGNATURE_REPORT_END machine=" + sig.MACHINES[(machine + 1) % 3]
            with self.assertRaises(sig.server.SmokeError): sig.check_reports(lines, machine)

    def test_retained_journal_fingerprint_and_transaction_mutation_refused(self):
        root, _ = sig.legacy_patch(synthetic_chunk(), 0)
        for key in ("before_fingerprint", "transaction_id"):
            after = copy.deepcopy(root); sig.carrier(after, sig.position(1, 0))[sig.JOURNAL][1][key] = (8, "changed")
            with self.assertRaises(sig.server.SmokeError): sig.retain_equal(root, after, machines=(0,))


def original_fixture_rows(machine):
    values = rows(machine)
    for row in values[2:4]:
        row["alternatives"] = [["minecraft:iron_ingot"], ["minecraft:redstone"]] if machine == 1 else [[sig.NS + "empty_canister"]] if machine == 2 else [["minecraft:iron_ingot"]]
    return values


def expanded_fixture_rows(machine):
    values = original_fixture_rows(machine)
    for row in values:
        row["tag_generation"] += 1
    if machine < 2:
        for row in values[2:4]: row["alternatives"][0] = ["minecraft:copper_ingot", "minecraft:iron_ingot"]
    if machine == 1:
        for row in values[2:4]: row["alternatives"][1] = ["minecraft:glowstone_dust", "minecraft:redstone"]
    return values


class LiveTagProcess(ReportProcess):
    """Synthetic report stream and real disposable pack files, never a native host."""
    def __init__(self, values, clock, runtime, events):
        super().__init__(values, clock)
        self.runtime, self.events = runtime, events

    def command(self, command):
        pack_exists = (self.runtime / "world/datapacks/arce-signature-tag-reload").exists()
        if command == "reload": self.events.append(("reload", pack_exists, self.calls))
        elif command.startswith("arce signature release-test"):
            self.events.append(("report", pack_exists, self.calls))
        super().command(command)


class LiveTagExpansionTest(unittest.TestCase):
    def invoke_reload(self, machine, values, *, original=None, install_delay=0, late=False):
        """Execute the unchanged nested phase AST; supply only its enclosing fixture variables."""
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory); runtime = work / "server"; runtime.mkdir()
            clock, events = [0], []
            process = LiveTagProcess(values, clock, runtime, events)
            if late: process.late = True
            reload, _ = current_phase("reload_report", machine)
            original = original_fixture_rows(machine) if original is None else original
            reload.__globals__.update(runtime=runtime, work=work,
                signatures=[original[2]["signature"]], current_receipt={"reports": original})
            commands, receipt = [], {}
            installer = sig.install_tag_pack
            def install(path):
                events.append(("install", process.calls))
                result = installer(path); clock[0] += install_delay
                return result
            error = None
            with patch.object(sig.time, "monotonic", side_effect=lambda: clock[0]), patch.object(sig.time, "sleep", side_effect=lambda seconds: clock.__setitem__(0, clock[0] + seconds)), patch.object(sig, "install_tag_pack", side_effect=install):
                # The phase namespace has the original function binding, so supply the same instrumented installer.
                reload.__globals__["install_tag_pack"] = sig.install_tag_pack
                try: reload(process, commands, receipt)
                except sig.server.SmokeError as exception: error = str(exception)
            pack = runtime / "world/datapacks/arce-signature-tag-reload"
            files = {str(path.relative_to(pack)): path.read_bytes() for path in pack.rglob("*") if path.is_file()} if pack.exists() else {}
            pack_record = json.loads((work / "tag-only-pack.json").read_text(encoding="utf-8")) if (work / "tag-only-pack.json").exists() else None
            return {"error": error, "events": events, "commands": commands, "receipt": receipt,
                    "reports": process.calls, "clock": clock[0], "files": files, "pack_record": pack_record}

    def test_pack_installation_occurs_only_in_live_reload_phase(self):
        tree = ast.parse(Path(sig.__file__).read_bytes())
        main = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "main")
        nested = next(node for node in main.body if isinstance(node, ast.FunctionDef) and node.name == "reload_report")
        def installations(nodes):
            return [node for parent in nodes for node in ast.walk(parent) if isinstance(node, ast.Call)
                    and isinstance(node.func, ast.Name) and node.func.id == "install_tag_pack"]
        self.assertEqual(1, len(installations([nested])))
        self.assertEqual([], installations([node for node in main.body if node is not nested]))

    def test_original_report_install_reload_and_new_alternatives_are_same_phase_order(self):
        for machine in range(3):
            before, after = original_fixture_rows(machine), expanded_fixture_rows(machine)
            result = self.invoke_reload(machine, [before, after])
            with self.subTest(machine=machine):
                self.assertIsNone(result["error"])
                self.assertEqual([("report", False, 0), ("install", 1), ("reload", True, 1), ("report", True, 1)], result["events"])
                self.assertEqual(before, result["receipt"]["reports_before_reload"])
                self.assertEqual(after, result["receipt"]["reports"])
                self.assertEqual("new_tag_alternatives" if machine < 2 else "literal_item_generation_control", result["receipt"]["tag_reload_applicability"])
                self.assertEqual(3, len(result["files"]))
                self.assertEqual(result["pack_record"], result["receipt"]["live_tag_pack"])
                self.assertTrue(all(hashlib.sha256(result["files"][path]).hexdigest() == value for path, value in result["pack_record"].items()))
                for path, value in (("data/forge/tags/items/ingots/iron.json", "minecraft:copper_ingot"),
                                    ("data/forge/tags/items/dusts/redstone.json", "minecraft:glowstone_dust")):
                    self.assertEqual({"replace": False, "values": [value]}, json.loads(result["files"][str(Path(path))]))

    def test_preexpanded_table_refuses_before_install_or_reload(self):
        for machine in (0, 1):
            before = expanded_fixture_rows(machine); after = copy.deepcopy(before)
            for row in after: row["tag_generation"] += 1
            result = self.invoke_reload(machine, [before, after])
            with self.subTest(machine=machine):
                self.assertIsNotNone(result["error"])
                self.assertEqual([("report", False, 0)], result["events"])
                self.assertNotIn("reload", result["commands"])
                self.assertFalse(result["files"])

    def test_generation_only_change_never_proves_a_new_tag_alternative(self):
        for machine in (0, 1):
            before = original_fixture_rows(machine); unchanged = copy.deepcopy(before)
            for row in unchanged: row["tag_generation"] += 1
            result = self.invoke_reload(machine, [before, unchanged])
            self.assertIsNotNone(result["error"])
            self.assertNotIn("reports", result["receipt"])

    def test_exact_delta_rejects_loss_extra_alternatives_and_current_state_changes(self):
        mutations = [("alternatives", [["minecraft:copper_ingot"]]),
                     ("alternatives", [["minecraft:copper_ingot", "minecraft:gold_ingot", "minecraft:iron_ingot"]]),
                     ("signature", "cd" * 32), ("progress", 6), ("marker", False),
                     ("protected", True), ("status", "RECOVERY_REQUIRED"), ("energy_simulated", 0)]
        for key, value in mutations:
            after = expanded_fixture_rows(0); after[2][key] = value
            result = self.invoke_reload(0, [original_fixture_rows(0), after])
            with self.subTest(key=key, value=value):
                self.assertIsNotNone(result["error"])
                self.assertNotIn("reports", result["receipt"])
        before = original_fixture_rows(0); before[1]["tag_generation"] = 2
        result = self.invoke_reload(0, [before, expanded_fixture_rows(0)])
        self.assertIsNotNone(result["error"])

    def test_legacy_protection_and_unproven_refusals_remain_required_during_reload(self):
        for key, value in (("marker", True), ("protected", False), ("energy_simulated", 1),
                           ("subject", "signature_migration_pending"), ("subject", "other")):
            after = expanded_fixture_rows(0); after[0][key] = value
            result = self.invoke_reload(0, [original_fixture_rows(0), after])
            with self.subTest(key=key, value=value):
                self.assertIsNotNone(result["error"])
                self.assertNotIn("reports", result["receipt"])

    def test_installation_and_reload_keep_original_deadline_and240report_limit(self):
        before = original_fixture_rows(0)
        result = self.invoke_reload(0, [before, expanded_fixture_rows(0)], install_delay=60)
        self.assertIsNotNone(result["error"])
        self.assertNotIn("reload", result["commands"])
        self.assertEqual(1, result["reports"])
        result = self.invoke_reload(0, [before] * 241)
        self.assertIsNotNone(result["error"])
        self.assertEqual((241, 60), (result["reports"], result["clock"]))  # One original startup report plus240 reload reports.
        self.assertNotIn("reports", result["receipt"])
        result = self.invoke_reload(0, [before, expanded_fixture_rows(0)], late=True)
        self.assertIsNotNone(result["error"])
        self.assertNotIn("reload", result["commands"])

    def test_literal_item_adapter_is_only_unchanged_recipe_and_generation_control(self):
        before, after = original_fixture_rows(2), expanded_fixture_rows(2)
        self.assertEqual(before[2]["alternatives"], after[2]["alternatives"])
        result = self.invoke_reload(2, [before, after])
        self.assertIsNone(result["error"])
        self.assertEqual("literal_item_generation_control", result["receipt"]["tag_reload_applicability"])
        changed = copy.deepcopy(after); changed[2]["alternatives"][0].append("minecraft:copper_ingot")
        self.assertIsNotNone(self.invoke_reload(2, [before, changed])["error"])


if __name__ == "__main__": unittest.main()
