import copy
import unittest

from scripts import run_v130_suit_equipment_smoke as runner


class SuitEquipmentSmokeTests(unittest.TestCase):
    def state(self, phase):
        return {"block": {"Name": "minecraft:chest", "Properties": {
            "facing": "north", "type": "single", "waterlogged": "false"}}, "items": runner.expected_items(phase)}

    def test_registration_is_exactly_once_at_the_expected_version(self):
        runner.validate_registration([runner.REGISTERED], False)
        runner.validate_registration([runner.SKIPPED], True)
        for lines, skipped in (([], False), ([runner.REGISTERED] * 2, False),
                               ([runner.REGISTERED, runner.SKIPPED], False), ([runner.REGISTERED], True),
                               ([runner.SKIPPED], False), ([runner.REGISTERED.replace("1.3", "1.2")], False)):
            with self.subTest(lines=lines), self.assertRaises(runner.SmokeError):
                runner.validate_registration(lines, skipped)

    def test_expected_restart_sequence_conserves_oxygen_and_canisters(self):
        previous = None
        for phase in runner.PHASES:
            state = self.state(phase)
            runner.validate_disk(state, phase, previous)
            units = state["items"][0]["tag"]["arce_suit_provider"]["data"]["oxygen"]
            full = sum(item["Count"] for item in state["items"] if item["id"] == runner.HOST + ":oxygen_canister")
            used = {"setup": 1, "restart": 2, "skipped": 2, "restored": 3}[phase]
            self.assertEqual(2000, units + full * 1000 + used)
            self.assertEqual(2, sum(item["Count"] for item in state["items"] if item["Slot"] != 0))
            previous = state

    def test_item_count_identity_metadata_and_payload_changes_reject(self):
        mutations = [lambda state: state["items"][0].update(Count=2),
                     lambda state: state["items"][0].update(id="minecraft:diamond_chestplate"),
                     lambda state: state["items"][0]["tag"].update(fixture_marker="changed"),
                     lambda state: state["items"][0]["tag"]["arce_suit_provider"].update(payload_version=2),
                     lambda state: state["items"][0]["tag"]["arce_suit_provider"].update(provider="wrong:suit"),
                     lambda state: state["items"][0]["tag"]["arce_suit_provider"]["data"].update(oxygen=999),
                     lambda state: state["items"][1].update(Count=2),
                     lambda state: state["items"].append(copy.deepcopy(state["items"][0])),
                     lambda state: state["block"].update(Name="minecraft:barrel")]
        for mutation in mutations:
            state = self.state("restart")
            mutation(state)
            with self.assertRaises(runner.SmokeError):
                runner.validate_disk(state, "restart", self.state("setup"))

    def test_skipped_provider_needs_exact_previous_authority(self):
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(self.state("skipped"), "skipped", None)
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(self.state("skipped"), "skipped", self.state("setup"))

    def test_unrecognized_phases_do_not_become_new_expectations(self):
        with self.assertRaises(runner.SmokeError):
            runner.expected_items("unknown")


if __name__ == "__main__":
    unittest.main()
