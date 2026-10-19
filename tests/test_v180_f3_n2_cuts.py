"""Cut oracles reject missing/duplicate authority and distinguish retirement from loss."""
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_f3_n2_cuts as cuts


def ident(number):
    return [0, 0, 0, number]


class CutTests(unittest.TestCase):
    def fixture(self, case="F3_HELD", restart=0):
        flights, entities, transfers = [], [], []
        for number in (1, 2):
            logical, entity = str(uuid.UUID(int=number)), str(uuid.UUID(int=number + 10))
            flight = dict(logical=logical, entity=entity, fuel=1000, required_fuel=500,
                          source_position=[384.5 + 32 * (number - 1), 101.0, 384.5],
                          destination_position=[64.5 + 32 * number, 101.0, 64.5])
            flights.append(flight)
            unknown = case == "F3_UNKNOWN"
            state = "LANDED" if restart else "DESCENT"
            data = dict(schema_version=3, assembly_transaction_id=ident(number), owner_id=ident(30),
                        snapshot={"blocks": [{"id": "cargo", "items": [17]}]}, resource_escrow={"epoch": 0},
                        flight_data=dict(fuel={"amount": 500 if unknown else 1000, "committed_debits": [number] if unknown else []},
                                         passengers={"assignments": []}, current_dimension=cuts.MOON if unknown else cuts.EARTH,
                                         state=state if unknown else "FUELED" if restart else "COUNTDOWN"))
            entities.append(dict(region="entities/r.0.0.mca", slot=number, entity={
                "UUID": ident(number + 10), "RocketEntityData": data,
                "Pos": flight["destination_position"] if unknown else flight["source_position"]}))
            transfers.append(dict(logical_rocket_id=ident(number), phase="COMMITTED" if unknown else "PREPARED",
                                  destination_entity_id=ident(number + 10)))
        if case == "F3_HELD" and restart:
            transfers = []
        disk = dict(entities=entities, journal=dict(transfers=transfers,
            classic_first_moon_landing={"state": "DELIVERED" if case == "F3_UNKNOWN" and restart else "EMPTY"}))
        if case == "F3_HELD" and restart == 0:
            disk["pending"] = copy.deepcopy(disk["journal"])
            disk["pending"]["transfers"][1]["phase"] = "DESTINATION_SPAWNED"
        return flights, disk

    def test_held_prepared_records_recover_two_intact_sources(self):
        flights, initial = self.fixture()
        baseline = cuts.verify_ordinary(initial, flights, "F3_HELD", 0, None)
        for restart in (1, 2):
            _, disk = self.fixture(restart=restart)
            self.assertEqual(baseline, cuts.verify_ordinary(disk, flights, "F3_HELD", restart, baseline))

    def test_unknown_retains_committed_records_and_recovers_same_destinations(self):
        flights, initial = self.fixture("F3_UNKNOWN")
        baseline = cuts.verify_ordinary(initial, flights, "F3_UNKNOWN", 0, None)
        for restart in (1, 2):
            _, disk = self.fixture("F3_UNKNOWN", restart)
            self.assertEqual(baseline, cuts.verify_ordinary(disk, flights, "F3_UNKNOWN", restart, baseline))

    def test_missing_duplicate_and_foreign_entities_fail(self):
        flights, disk = self.fixture()
        for entities in (disk["entities"][:1], disk["entities"] + disk["entities"][:1]):
            broken = copy.deepcopy(disk); broken["entities"] = entities
            with self.assertRaises(cuts.smoke.SmokeError):
                cuts.verify_ordinary(broken, flights, "F3_HELD", 0, None)
        disk["entities"][0]["entity"]["RocketEntityData"]["assembly_transaction_id"] = ident(99)
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.selected(disk, flights)

    def test_missing_duplicate_and_advanced_records_fail(self):
        flights, disk = self.fixture()
        for records in ([], disk["journal"]["transfers"] * 2):
            broken = copy.deepcopy(disk); broken["journal"]["transfers"] = records
            with self.assertRaises(cuts.smoke.SmokeError):
                cuts.verify_ordinary(broken, flights, "F3_HELD", 0, None)
        disk["journal"]["transfers"][0]["phase"] = "DESTINATION_SPAWNED"
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.verify_ordinary(disk, flights, "F3_HELD", 0, None)

    def test_changed_resources_and_structure_fail_across_restarts(self):
        flights, initial = self.fixture()
        baseline = cuts.verify_ordinary(initial, flights, "F3_HELD", 0, None)
        for field, value in (("owner_id", ident(99)), ("snapshot", {"blocks": []}), ("resource_escrow", {"epoch": 1})):
            _, disk = self.fixture(restart=1)
            disk["entities"][0]["entity"]["RocketEntityData"][field] = value
            with self.assertRaises(cuts.smoke.SmokeError):
                cuts.verify_ordinary(disk, flights, "F3_HELD", 1, baseline)
        _, disk = self.fixture(restart=1)
        disk["entities"][0]["entity"]["RocketEntityData"]["flight_data"]["fuel"]["amount"] = 999
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.verify_ordinary(disk, flights, "F3_HELD", 1, baseline)

    def test_unknown_cannot_publish_election_or_landed_before_ack(self):
        flights, disk = self.fixture("F3_UNKNOWN")
        disk["journal"]["classic_first_moon_landing"]["state"] = "ELECTED"
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.verify_ordinary(disk, flights, "F3_UNKNOWN", 0, None)
        flights, disk = self.fixture("F3_UNKNOWN")
        disk["entities"][0]["entity"]["RocketEntityData"]["flight_data"]["state"] = "LANDED"
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.verify_ordinary(disk, flights, "F3_UNKNOWN", 0, None)

    def test_returned_source_must_be_stationary_at_origin(self):
        flights, disk = self.fixture(restart=1)
        disk["entities"][0]["entity"]["Pos"][1] += 48
        # Do not mutate the expected position through a shared fixture list.
        flights[0]["source_position"] = [384.5, 101.0, 384.5]
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.verify_ordinary(disk, flights, "F3_HELD", 1, None)

    def test_default_campaign_reports_unavailable_rows_not_passes(self):
        self.assertEqual(6, len(cuts.CASES))
        self.assertEqual({"N2_REFUSED"}, set(cuts.BLOCKED))
        self.assertEqual("RT_CANCEL_SEPARATE_0_READBACK_AFTER", cuts.GOVERNED)

    def test_save_waits_for_its_own_marker(self):
        from unittest.mock import Mock
        process = Mock(lines=["old save"])
        cuts.save(process)
        process.command.assert_called_once_with("save-all flush")
        process.wait_for.assert_called_once_with(cuts.smoke.SAVE_MARKER, 60, start_at=1)

    def test_hold_requires_the_actual_staged_successor(self):
        flights, disk = self.fixture()
        disk.pop("pending")
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.verify_ordinary(disk, flights, "F3_HELD", 0, None)

    def removal(self):
        flights, before = self.fixture("F3_UNKNOWN", 1)
        source = before["entities"][1]["entity"]
        source["RocketEntityData"]["flight_data"].update(state="ASCENT", current_dimension=cuts.EARTH)
        before["journal"]["transfers"][1]["phase"] = "PREPARED"
        row = dict(flights=flights, before_removal=copy.deepcopy(before))
        disk = copy.deepcopy(before)
        disk["journal"]["transfers"].pop()
        return row, disk

    def test_removal_preserves_both_rockets_and_only_retires_b(self):
        row, disk = self.removal()
        cuts.verify_removal(disk, row, 0)
        source = disk["entities"][1]["entity"]
        source["RocketEntityData"]["flight_data"]["state"] = "FUELED"
        source["Pos"] = row["flights"][1]["source_position"]
        cuts.verify_removal(disk, row, 1)
        disk["journal"]["transfers"] = []
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.verify_removal(disk, row, 1)

    def test_removal_cannot_silently_accept_an_orphaned_inflight_source(self):
        row, disk = self.removal()
        with self.assertRaisesRegex(cuts.smoke.SmokeError, "stationary source"):
            cuts.verify_removal(disk, row, 1)

    def returned(self, disk, row):
        source = disk["entities"][1]["entity"]
        source["RocketEntityData"]["flight_data"]["state"] = "FUELED"
        source["Pos"] = list(row["flights"][1]["source_position"])
        return disk

    def test_n3_cuts_keep_b_record_until_its_survivor_proof(self):
        row, removed = self.removal()
        held = copy.deepcopy(row["before_removal"])
        cuts.verify_retirement(held, row, 0, "N3_STORE")
        for case in ("N3_FORCE", "N3_REMOVAL"):
            with self.assertRaisesRegex(cuts.smoke.SmokeError, "stationary source"):
                cuts.verify_retirement(held, row, 0, case)
        cuts.verify_retirement(self.returned(copy.deepcopy(held), row), row, 0, "N3_REMOVAL")
        with self.assertRaisesRegex(cuts.smoke.SmokeError, "lost B's record"):
            cuts.verify_retirement(self.returned(copy.deepcopy(removed), row), row, 0, "N3_REMOVAL")

    def test_n3_restarts_retire_b_only_with_the_stationary_survivor(self):
        row, removed = self.removal()
        for case in cuts.N3_CUTS:
            cuts.verify_retirement(self.returned(copy.deepcopy(removed), row), row, 1, case)
            with self.assertRaisesRegex(cuts.smoke.SmokeError, "stationary source"):
                cuts.verify_retirement(removed, row, 2, case)
            with self.assertRaisesRegex(cuts.smoke.SmokeError, "did not retire"):
                cuts.verify_retirement(self.returned(copy.deepcopy(row["before_removal"]), row), row, 1, case)
        changed = self.returned(copy.deepcopy(removed), row)
        changed["entities"][1]["entity"]["RocketEntityData"]["flight_data"]["fuel"]["amount"] -= 100
        with self.assertRaisesRegex(cuts.smoke.SmokeError, "conserved"):
            cuts.verify_retirement(changed, row, 1, "N3_FORCE")
        duplicated = self.returned(copy.deepcopy(removed), row)
        duplicated["entities"].append(copy.deepcopy(duplicated["entities"][1]))
        with self.assertRaisesRegex(cuts.smoke.SmokeError, "duplicated"):
            cuts.verify_retirement(duplicated, row, 1, "N3_FORCE")

    def test_n3_cuts_are_opt_in_and_name_existing_boundaries(self):
        self.assertEqual({"BARRIER_STORE_BEFORE", "BARRIER_FORCE_BEFORE", "ORDINARY_REMOVAL_BEFORE"}, set(cuts.N3_CUTS.values()))
        self.assertFalse(set(cuts.N3_CUTS) & set(cuts.CASES))


if __name__ == "__main__":
    unittest.main()
