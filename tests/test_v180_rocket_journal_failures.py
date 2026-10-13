from copy import deepcopy
import sys
from pathlib import Path
import struct
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_rocket_journal_failures as runner


def rocket():
    return {"UUID": [1, 2, 3, 4], "Pos": [384.5, 101.0, 384.5], "RocketEntityData": {
        "schema_version": 3, "assembly_transaction_id": [2, 3, 4, 5], "owner_id": [3, 4, 5, 6],
        "snapshot": {"payload": {"items": [17, 64]}},
        "flight_data": {"fuel": {"amount": 1000}, "state": "FUELED", "current_dimension": runner.EARTH,
                        "current_origin": [384, 101, 384], "passengers": {"assignments": []}},
        "resource_escrow": {"schema_version": 1, "resource_epoch": 0, "markers": []}}}


class RocketJournalFailuresTest(unittest.TestCase):
    def test_source_placement_and_passengers_are_not_rewritten(self):
        cases = []
        changed = rocket(); changed["Pos"] = [384.5, 102.0, 384.5]; cases.append(changed)
        changed = rocket(); changed["Passengers"] = [{"id": "minecraft:pig"}]; cases.append(changed)
        for key, value in (("current_dimension", "advancedrocketrycommunity:moon"), ("current_origin", [0, 100, 0]),
                           ("passengers", {"assignments": [{"seat": 0}]})):
            changed = rocket(); changed["RocketEntityData"]["flight_data"][key] = value; cases.append(changed)
        for changed in cases:
            with self.assertRaises(runner.smoke.SmokeError):
                runner.validate_authority([changed], {}, "ADMISSION", None)

    def test_saved_entity_in_another_level_is_not_a_source(self):
        with TemporaryDirectory() as directory:
            world = Path(directory)
            region = world / "dimensions/advancedrocketrycommunity/moon/entities/r.0.0.mca"
            region.parent.mkdir(parents=True)
            region.write_bytes(struct.pack(">I", 513) + bytes(8188))
            with patch.object(runner, "record", return_value={"Entities": [rocket()]}):
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.inspect(world, "ADMISSION", None)

    def test_only_empty_regions_have_no_header(self):
        with TemporaryDirectory() as directory:
            region = Path(directory) / "r.0.0.mca"
            region.write_bytes(b"")
            self.assertEqual([], runner.allocated_slots(region))
            for size in (1, 4095, 4096, 8191):
                region.write_bytes(bytes(size))
                with self.assertRaises(runner.smoke.SmokeError):
                    runner.allocated_slots(region)
            table = [0] * 1024
            table[3], table[1000] = 513, 769
            region.write_bytes(struct.pack(">1024I", *table) + bytes(4096))
            self.assertEqual([3, 1000], runner.allocated_slots(region))

    def test_admission_requires_the_old_fueled_authority(self):
        self.assertEqual(1000, runner.validate_authority([rocket()], {}, "ADMISSION", None)["fuel"]["amount"])
        for state in ("COUNTDOWN", "ASCENT", "TRANSIT", "LANDED"):
            changed = rocket(); changed["RocketEntityData"]["flight_data"]["state"] = state
            with self.assertRaises(runner.smoke.SmokeError):
                runner.validate_authority([changed], {}, "ADMISSION", None)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.validate_authority([rocket()], {"transfers": [{}]}, "ADMISSION", None)

    def test_advancement_requires_the_exact_prepared_source(self):
        op = {"phase": "PREPARED", "source_entity_id": rocket()["UUID"]}
        runner.validate_authority([rocket()], {"transfers": [op]}, "ADVANCEMENT", None)
        for changed in (dict(op, phase="DESTINATION_SPAWNED"), dict(op, destination_entity_id=[5, 6, 7, 8]),
                        dict(op, source_entity_id=[6, 7, 8, 9])):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.validate_authority([rocket()], {"transfers": [changed]}, "ADVANCEMENT", None)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.validate_authority([rocket()], {}, "ADVANCEMENT", None)

    def test_missing_or_duplicate_entities_fail(self):
        for entries in ([], [rocket(), rocket()]):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.validate_authority(entries, {}, "ADMISSION", None)

    def test_fuel_and_escrow_changes_fail(self):
        for field, value in (("schema_version", 2), ("resource_escrow", {"resource_epoch": 1})):
            changed = rocket(); changed["RocketEntityData"][field] = value
            with self.assertRaises(runner.smoke.SmokeError):
                runner.validate_authority([changed], {}, "ADMISSION", None)
        changed = rocket(); changed["RocketEntityData"]["flight_data"]["fuel"]["amount"] = 500
        with self.assertRaises(runner.smoke.SmokeError):
            runner.validate_authority([changed], {}, "ADMISSION", None)

    def test_restart_preserves_owner_logical_identity_snapshot_and_fuel(self):
        baseline = runner.validate_authority([rocket()], {}, "ADMISSION", None)
        for field in ("owner_id", "assembly_transaction_id", "snapshot"):
            changed = rocket(); changed["RocketEntityData"][field] = {"changed": True}
            with self.assertRaises(runner.smoke.SmokeError):
                runner.validate_authority([changed], {}, "ADMISSION", baseline)
        changed = rocket(); changed["UUID"] = [8, 9, 10, 11]
        with self.assertRaises(runner.smoke.SmokeError):
            runner.validate_authority([changed], {}, "ADMISSION", baseline)
        runner.validate_authority([deepcopy(rocket())], {}, "ADMISSION", baseline)

    def test_only_the_exact_intentional_error_streak_is_accepted(self):
        intended = "[Server thread/ERROR] " + runner.FAILURE
        runner.check_log([intended])
        for lines in ([], [intended, intended], ["[Server thread/ERROR] other"],
                      [intended, "[main/FATAL] crash"]):
            with self.assertRaises(runner.smoke.SmokeError): runner.check_log(lines)


if __name__ == "__main__":
    unittest.main()
