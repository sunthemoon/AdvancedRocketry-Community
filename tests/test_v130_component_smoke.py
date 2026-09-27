import copy
import unittest

from scripts import run_v130_component_smoke as runner


class ComponentSmokeTests(unittest.TestCase):
    def state(self, updated=False):
        number = 2 if updated else 1
        snapshot = {
            "schema_version": 1, "snapshot_id": f"00000000-0000-0000-0000-{number:012d}",
            "source_dimension": "minecraft:overworld", "source_origin": list(runner.ORIGIN),
            "bounding_box": [-1, 0, 0, 1, 2, 0], "content_hash": str(number) * 64,
            "mass_inputs": runner.expected_stats(updated), "passenger_anchors": [[0, 1, 0]],
            "block_palette": [{"id": name, "properties": {}} for name in runner.RELATIVE.values()],
            "relative_blocks": [{"position": list(pos), "palette": index} for index, pos in enumerate(runner.RELATIVE)],
        }
        data = {"schema_version": 2, "owner_id": [0, 0, 0, 5], "assembly_transaction_id": [0, 0, 1, number],
                "snapshot": snapshot, "flight_data": {"schema_version": 2, "state": "ASSEMBLED",
                "logical_rocket_id": [0, 0, 1, number], "fuel": {"capacity": 2000 if updated else 1500,
                "amount": 0, "committed_debits": []}, "passengers": {"seat_capacity": 1, "assignments": []}}}
        state = {"entities": [{"id": runner.ROCKET, "UUID": [0, 0, 0, number], "RocketEntityData": data}],
                 "blocks": {str(pos): {"Name": "minecraft:air"} for pos in runner.POSITIONS},
                 "block_entities": [{"id": runner.HOST + ":rocket_assembler", "x": 264, "y": 100, "z": 264}],
                 "journal": {"transactions": []}}
        state["blocks"][str(runner.ASSEMBLER)] = {"Name": runner.HOST + ":rocket_assembler"}
        return state, (snapshot["content_hash"], snapshot["snapshot_id"])

    def test_native_sequence_distinguishes_frozen_snapshots_from_reassembly(self):
        state, receipt = self.state()
        before = runner.validate_disk(state, "assemble", receipt, None)
        runner.validate_disk(state, "skipped", receipt, before)
        state, receipt = self.state(True)
        changed = runner.validate_disk(state, "updated", receipt, before)
        runner.validate_disk(state, "restart", receipt, changed)

    def test_stats_roles_capacity_anchors_blocks_and_entities_are_binding(self):
        mutations = [lambda s: s["entities"][0]["RocketEntityData"]["snapshot"]["mass_inputs"].update(mass=10),
                     lambda s: s["entities"][0]["RocketEntityData"]["flight_data"]["fuel"].update(capacity=1000),
                     lambda s: s["entities"][0]["RocketEntityData"]["snapshot"].update(passenger_anchors=[]),
                     lambda s: s["entities"][0]["RocketEntityData"]["snapshot"]["block_palette"][0].update(id="minecraft:stone"),
                     lambda s: s["blocks"][str(runner.ORIGIN)].update(Name="minecraft:diamond_block"),
                     lambda s: s["entities"].append(copy.deepcopy(s["entities"][0])),
                     lambda s: s["entities"].append({"id": "minecraft:item"}),
                     lambda s: s["block_entities"].append({"id": "minecraft:chest", "x": 264, "y": 101, "z": 264}),
                     lambda s: s["journal"]["transactions"].append({"phase": "EXTRACTING"})]
        for mutate in mutations:
            state, receipt = self.state()
            mutate(state)
            with self.assertRaises(runner.SmokeError):
                runner.validate_disk(state, "assemble", receipt, None)

    def test_restarts_require_previous_exact_authority(self):
        state, receipt = self.state()
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(state, "skipped", receipt, None)
        previous = copy.deepcopy(state["entities"][0]["RocketEntityData"])
        previous["flight_data"]["state_started_game_time"] = 27
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(state, "skipped", receipt, previous)

    def test_registration_receipt_is_exact_for_each_variant_and_version(self):
        lines = {"assemble": "Registered rocket components (variant standard, API 1.6, event 1)",
                 "skipped": "Skipped rocket components (event 1)",
                 "updated": "Registered rocket components (variant updated, API 1.6, event 1)"}
        for phase, line in lines.items():
            runner.validate_registration([line], phase)
            for invalid in ([], [line, line], [line.replace("event 1", "event 2")], [line + " extra"]):
                with self.assertRaises(runner.SmokeError):
                    runner.validate_registration(invalid, phase)
        with self.assertRaises(runner.SmokeError):
            runner.validate_registration([lines["assemble"].replace("API 1.6", "API 1.4")], "assemble")
        with self.assertRaises(runner.SmokeError):
            runner.validate_registration([lines["assemble"]], "updated")

    def test_unknown_phase_and_stale_assembly_receipt_reject(self):
        state, receipt = self.state()
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(state, "unknown", receipt, None)
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(state, "assemble", ("0" * 64, receipt[1]), None)


if __name__ == "__main__":
    unittest.main()
