"""Integrated cut mapping and independent native-oracle regression tests."""
import copy
import json
from pathlib import Path
import sys
import unittest
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import w198_terrain as terrain
from run_w152_native import native_case_plan


class W198TerrainTest(unittest.TestCase):
    def test_inventory_is_not_folded_and_every_row_has_four_starts(self):
        plan = terrain.plan()
        self.assertEqual(89, len(plan))
        self.assertEqual(native_case_plan(), [row["input"] for row in plan])
        self.assertEqual(356, sum(row["starts"] for row in plan))
        self.assertEqual([60, 60, 60, 60, 60, 56],
                         [sum(row["starts"] for row in plan if row["tranche"] == n) for n in range(1, 7)])
        self.assertEqual(89, len({row["id"] for row in plan}))

    def test_tranche_counts_attempts_before_launch_and_never_retries_twice_failed_row(self):
        terrain.check_launch([], "001", 1, "start")
        with self.assertRaisesRegex(ValueError, "60"):
            terrain.check_launch([{}] * 60, "001", 1, "start")
        with self.assertRaisesRegex(ValueError, "two"):
            terrain.check_attempt([dict(row="001", status="FAIL")] * 2, "001")

    def test_exact_predecessor_not_any_transfer_with_the_same_id(self):
        old = {"transfer_id": [1, 2, 3, 4], "snapshot": {"full": "image"}}
        root = dict(schema_version=3, format_epoch="v1.8.0-first-moon-landing",
                    transfers=[old], classic_first_moon_landing=dict(schema_version=1, state="EMPTY"))
        terrain.check_transfer(root, old, True)
        changed = copy.deepcopy(root); changed["transfers"][0]["snapshot"]["full"] = "other"
        with self.assertRaisesRegex(ValueError, "exact T"):
            terrain.check_transfer(changed, old, True)
        with self.assertRaisesRegex(ValueError, "exact T"):
            terrain.check_transfer(root, old, False)

    def test_nested_source_and_foreign_player_are_not_invisible(self):
        nested = {"id": "minecraft:pig", "Passengers": [{"id": "rocket", "RocketEntityData": {"owner": 1}}]}
        with self.assertRaisesRegex(ValueError, "Nested rocket"):
            terrain.rocket_census([nested])
        with self.assertRaisesRegex(ValueError, "player"):
            terrain.rocket_census([dict(id="minecraft:player")])

    def test_entity_oracle_compares_complete_data_including_fuel_and_manifest(self):
        entity = dict(id="rocket", UUID=[1, 2, 3, 4], Pos=[15.5, 100., 15.5],
                      RocketEntityData=dict(snapshot={"content": ["all"]},
                                           flight_data=dict(fuel={"debits": ["paid"], "amount": 1000}, passengers={})))
        terrain.check_entity(entity, entity)
        for field in ("snapshot", "flight_data"):
            changed = copy.deepcopy(entity); changed["RocketEntityData"][field] = {}
            with self.assertRaisesRegex(ValueError, "D1"):
                terrain.check_entity(changed, entity)

    def test_shared_fixture_integrated_mode_uses_installed_disassembly_not_direct_b2(self):
        source = Path(__file__).resolve().parents[1] / "src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server"
        helper = (source / "W152NativeI6.java").read_text()
        self.assertIn("manager.disassembleForReleaseTest", helper)
        self.assertNotIn("beginSealedForReleaseTest", helper)
        self.assertIn("w198Integrated", (source / "W152NativeProbe.java").read_text())

    def test_commands_keep_integrated_path_and_two_unarmed_stable_restarts(self):
        from run_w198_terrain import command_for
        row = terrain.plan()[7]; args = SimpleNamespace(java="java", source_commit="commit")
        for stage, cut in (("start", row["input"]["cut"]), ("recovery-cut", row["input"]["recovery"]),
                           ("recover1", "NONE"), ("recover2", "NONE")):
            command, actual = command_for(args, row, stage)
            self.assertEqual(cut, actual)
            self.assertIn("-Dadvancedrocketrycommunity.w198Integrated=true", command)
            self.assertIn("-Dadvancedrocketrycommunity.w152Cut=" + cut, command)

    def test_phase_is_file_election_not_callback_acknowledgement(self):
        for cut in ("C1_RETAIN_AFTER", "C1_MOVE_BEFORE", "PREPARE_PROOF_CURRENT"):
            self.assertIsNone(terrain.phase_at_cut(cut))
        for cut in ("C1_MOVE_AFTER", "C1_UNKNOWN", "C2_STAGE_AFTER", "C3_MOVE_BEFORE"):
            self.assertEqual("LOCKED", terrain.phase_at_cut(cut))
        for cut in ("C3_MOVE_AFTER", "C3_UNKNOWN", "C5_MOVE_BEFORE"):
            self.assertEqual("RESTORED", terrain.phase_at_cut(cut))
        for cut in ("C5_MOVE_AFTER", "C5_UNKNOWN", "UNSEAL_PREPARE_FAILED"):
            self.assertIsNone(terrain.phase_at_cut(cut))

    def test_native_host_projection_keeps_all_payload_fields(self):
        cases = [("vanilla_container_v1", {"Items": [{"Count": 7}], "CustomName": "name"},
                  {"Items": [{"Count": 7}], "CustomName": "name"}),
                 ("pressurized_tank_v1", {"arce_pressurized_tank": {"schema": 1, "fluid": {"Amount": 1000}}},
                  {"tank": {"schema": 1, "fluid": {"Amount": 1000}}}),
                 ("satellite_bay_v1", {"arce_satellite_bay": {"owner": "owner", "package": {"identity": 9}}},
                  {"owner": "owner", "package": {"identity": 9}})]
        for adapter, native, payload in cases:
            self.assertEqual(payload, terrain.native_payload(native, {"adapter": "mod:" + adapter}))
        with self.assertRaisesRegex(ValueError, "Unmapped"):
            terrain.native_payload({}, {"adapter": "mod:unknown"})


if __name__ == "__main__":
    unittest.main()
