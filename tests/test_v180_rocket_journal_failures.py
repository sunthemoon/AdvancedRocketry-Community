from copy import deepcopy
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_rocket_journal_failures as runner


def rocket():
    return {"UUID": [1, 2, 3, 4], "RocketEntityData": {
        "schema_version": 3, "assembly_transaction_id": [2, 3, 4, 5], "owner_id": [3, 4, 5, 6],
        "snapshot": {"payload": {"items": [17, 64]}},
        "flight_data": {"fuel": {"amount": 1000}, "state": "FUELED"},
        "resource_escrow": {"schema_version": 1, "resource_epoch": 0, "markers": []}}}


class RocketJournalFailuresTest(unittest.TestCase):
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
