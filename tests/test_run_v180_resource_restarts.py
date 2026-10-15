"""Fail-closed tests for the W63 native saved-state inspector."""
import copy
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_resource_restarts as runner


def disk(case="F2"):
    fuel = {"propulsion": {"primary": {"amount": 0 if case == "F1" else 2000},
                           "oxidizer": {"amount": 0 if case == "F1" else 1000}}}
    entity = {"UUID": [1, 2, 3, 4], "RocketEntityData": {
        "assembly_transaction_id": [5, 6, 7, 8], "flight_data": {"fuel": fuel}}}
    entry = {"phase": "COMMITTED" if case == "F1" else "PREPARED", "logical_rocket_id": [5, 6, 7, 8],
             "source_entity_id": [1, 2, 3, 4], "destination_entity_id": [1, 2, 3, 4],
             "source_flight": {"fuel": copy.deepcopy(fuel)}}
    return {"rockets": [{"entity": entity}], "journal": {"transfers": [entry]},
            "owner_inventory": [{"id": "advancedrocketrycommunity:rocket_fuel_bucket", "Count": 2}]
                if case == "F1" else [{"id": "advancedrocketrycommunity:hydrogen_canister", "Count": 1}],
            "loader": [{"arce_fuel_loader": {"buffered_units": 0, "slot_role": 1,
                         "item": {"id": "advancedrocketrycommunity:rocket_fuel_cell", "Count": 1}}}]}


class NativeResourceRestartInspectorTest(unittest.TestCase):
    def test_empty_native_region_has_no_entities(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r.0.0.mca"
            path.write_bytes(b"")
            self.assertEqual(runner.entity_slots(path), [])
            path.write_bytes(bytes(4096))
            self.assertEqual(runner.entity_slots(path), [])

    def test_partial_region_header_is_not_treated_as_empty(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r.0.0.mca"
            path.write_bytes(bytes(4095))
            with self.assertRaisesRegex(runner.smoke.SmokeError, "Truncated"):
                runner.entity_slots(path)

    def test_matrix_includes_three_offline_restarts_and_separate_logins(self):
        self.assertEqual(runner.CASES["F2"].count("check"), 3)
        self.assertEqual(runner.CASES["F2_TWO"][-2:], ("reconnect-first", "reconnect-second"))

    def test_arrival_requires_both_withdrawn_buckets(self):
        data = disk("F1")
        self.assertEqual(runner.verify_disk(data, "F1", "check")["vector"], [0, 0])
        data["owner_inventory"][0]["Count"] = 1
        with self.assertRaisesRegex(runner.smoke.SmokeError, "bucket count"):
            runner.verify_disk(data, "F1", "check")

    def test_rejects_duplicate_or_missing_entity(self):
        for count in (0, 2):
            data = disk()
            data["rockets"] *= count
            with self.assertRaisesRegex(runner.smoke.SmokeError, "rocket count"):
                runner.verify_disk(data, "F2", "check")

    def test_source_checks_full_vector_not_just_amount(self):
        data = disk()
        self.assertEqual(runner.verify_disk(data, "F2", "check")["vector"], [2000, 1000])
        data["journal"]["transfers"][0]["source_flight"]["fuel"]["revision"] = 17
        with self.assertRaisesRegex(runner.smoke.SmokeError, "full source fuel vector"):
            runner.verify_disk(data, "F2", "check")

    def test_pending_loader_cannot_buffer_select_or_consume(self):
        for field, value in (("buffered_units", 25), ("target_rocket_id", [1, 2, 3, 4]), ("item", {})):
            data = disk()
            data["loader"][0]["arce_fuel_loader"][field] = value
            with self.assertRaisesRegex(runner.smoke.SmokeError, "pending loader"):
                runner.verify_disk(data, "F2", "check")

    def test_offline_restart_rejects_changed_player_fuel(self):
        for count in (0, 2):
            data = disk()
            data["owner_inventory"][0]["Count"] = count
            with self.assertRaisesRegex(runner.smoke.SmokeError, "fuel is not conserved"):
                runner.verify_disk(data, "F2", "check")

    def test_reconnect_conserves_old_canister_and_new_withdrawal(self):
        data = disk()
        data["journal"]["transfers"] = []
        data["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"]["amount"] = 1000
        with self.assertRaisesRegex(runner.smoke.SmokeError, "fuel is not conserved"):
            runner.verify_disk(data, "F2", "reconnect-first")
        data["owner_inventory"][0]["Count"] = 2
        self.assertEqual(runner.verify_disk(data, "F2", "reconnect-first")["vector"], [1000, 1000])

    def test_rejects_changed_identity_and_bank(self):
        for field in ("identity", "bank"):
            data = disk()
            entity = data["rockets"][0]["entity"]
            if field == "identity":
                entity["UUID"][0] = 77
            else:
                entity["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"]["amount"] = 3000
            with self.assertRaises(runner.smoke.SmokeError):
                runner.verify_disk(data, "F2", "check")

    def test_disassembly_requires_retired_journal(self):
        data = disk("F1"); data["rockets"] = []
        with self.assertRaisesRegex(runner.smoke.SmokeError, "journal count"):
            runner.verify_disk(data, "F1", "empty")
        data["journal"]["transfers"] = []
        self.assertEqual(runner.verify_disk(data, "F1", "empty")["rockets"], 0)


if __name__ == "__main__":
    unittest.main()
