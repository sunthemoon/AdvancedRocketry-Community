import copy
import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts import run_v130_fuel_smoke as runner


class FuelSmokeTests(unittest.TestCase):
    def state(self, phase):
        count = 1 if phase == "buffered" else 2
        state = {"entities": [], "block_entities": [], "blocks": {}, "journal": {"transactions": []}}
        receipts = []
        for index in range(count):
            snapshot = {"content_hash": str(index + 1) * 64, "source_origin": list(runner.ORIGINS[index]),
                        "mass_inputs": runner.components.expected_stats(False)}
            data = {"schema_version": 2, "owner_id": runner.OWNER.copy(), "snapshot": snapshot,
                    "assembly_transaction_id": [0, 0, 1, index], "flight_data": {"schema_version": 2,
                    "logical_rocket_id": [0, 0, 1, index], "state": "FUELED", "fuel": {
                    "capacity": 1500, "amount": 1500 if index == 0 else 198 if phase == "skipped" else 499, "committed_debits": []}}}
            identity = [0, 0, 0, index + 1]
            state["entities"].append({"id": runner.ROCKET, "UUID": identity, "RocketEntityData": data})
            receipts.append((snapshot["content_hash"], runner.recovery.uuid_from_nbt(identity)))
            state["block_entities"].append({"id": runner.HOST + ":rocket_assembler"})
            for offset in runner.components.RELATIVE:
                pos = tuple(a + b for a, b in zip(runner.ORIGINS[index], offset))
                state["blocks"][str(pos)] = {"Name": "minecraft:air"}
        for pos, root in zip(runner.LOADERS, runner.expected_roots(phase)):
            state["block_entities"].append({"id": runner.HOST + ":fuel_loader", "x": pos[0], "y": pos[1], "z": pos[2], runner.KEY: root})
        state["block_entities"].extend([{"id": "minecraft:hopper", "Items": []} for _ in range(2)])
        return state, receipts

    def test_four_native_phases_bind_old_batch_missing_input_and_changed_definition(self):
        previous = None
        for phase in runner.PHASES:
            state, receipts = self.state(phase)
            previous = runner.validate_disk(state, phase, receipts, previous)

    def test_units_remainders_metadata_owner_and_legacy_migration_are_binding(self):
        for phase in runner.PHASES:
            state, receipts = self.state(phase)
            previous = None if phase == "buffered" else {"rockets": copy.deepcopy([e["RocketEntityData"] for e in state["entities"]])}
            for field, value in (("schema_version", 1), ("slot_role", 5), ("buffered_units", 74), ("owner_id", [0, 0, 0, 4]), ("item", {"id": "minecraft:diamond", "Count": 1})):
                broken = copy.deepcopy(state)
                next(be for be in broken["block_entities"] if runner.KEY in be)[runner.KEY][field] = value
                with self.assertRaises(runner.SmokeError): runner.validate_disk(broken, phase, receipts, previous)
        for field in ("definition", "total_units", "remainder"):
            state, receipts = self.state("buffered")
            batch = next(be for be in state["block_entities"] if runner.KEY in be)[runner.KEY]["batch"]
            batch[field] = "wrong"
            with self.assertRaises(runner.SmokeError): runner.validate_disk(state, "buffered", receipts, None)

    def test_duplicate_inputs_entities_loose_items_and_unfinished_journal_reject(self):
        changes = [lambda s: s["entities"].append(copy.deepcopy(s["entities"][0])),
                   lambda s: s["entities"].append({"id": "minecraft:item"}),
                   lambda s: s["block_entities"][-1]["Items"].append(runner.native_item("minecraft:charcoal")),
                   lambda s: s["entities"][0]["RocketEntityData"]["flight_data"]["fuel"].update(amount=1499),
                   lambda s: s["journal"]["transactions"].append({"phase": "EXTRACTING"})]
        for change in changes:
            state, receipts = self.state("buffered"); change(state)
            with self.assertRaises(runner.SmokeError): runner.validate_disk(state, "buffered", receipts, None)

    def test_restarts_require_previous_exact_authority(self):
        state, receipts = self.state("restart")
        with self.assertRaises(runner.SmokeError): runner.validate_disk(state, "restart", receipts, None)
        previous = {"rockets": copy.deepcopy([e["RocketEntityData"] for e in state["entities"]])}
        previous["rockets"][1]["flight_data"]["state_started_game_time"] = 29
        with self.assertRaises(runner.SmokeError): runner.validate_disk(state, "restart", receipts, previous)

    def test_registration_and_phase_selection_are_exact(self):
        for phase in runner.PHASES:
            variant = "updated" if phase in ("updated", "restart") else "standard"
            line = "Skipped rocket fuels (event 1)" if phase == "skipped" else f"Registered rocket fuels (variant {variant}, API 1.7, event 1)"
            runner.validate_registration([line], phase)
            for invalid in ([], [line, line], [line + " extra"], [line.replace("event 1", "event 2")]):
                with self.assertRaises(runner.SmokeError): runner.validate_registration(invalid, phase)
        with self.assertRaises(runner.SmokeError): runner.expected_roots("unknown")

    def test_invalid_java_reports_original_failure_before_creating_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "not_created"
            argv = ["fuel-smoke", "server", "--host-jar", "host.jar", "--fixture-jar", "fixture.jar",
                    "--evidence-dir", str(output), "--java", "invalid-java", "--accept-eula"]
            error = io.StringIO()
            with patch("sys.argv", argv), patch.object(runner.recovery, "validate_inputs", return_value=(Path(directory), output, [], Path("args"))), \
                    patch.object(runner.server, "resolve_java", side_effect=runner.SmokeError("invalid Java selection")), \
                    contextlib.redirect_stderr(error):
                self.assertEqual(1, runner.main())
            self.assertIn("invalid Java selection", error.getvalue())
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
