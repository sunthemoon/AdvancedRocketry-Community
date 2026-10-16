import copy
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_source_clock as runner


class SourceClockOracleTest(unittest.TestCase):
    def fixture(self, phase="ASCENT"):
        arm = dict(outcome="ARMED", serial=1, src="source", d="destination", l="logical", t="transfer", epoch=1)
        root = dict(armed_flights=[arm], issued_launch_serial=1, records=[], floors=[], tombstones=[], rocket_authorities=[], ledger_id="ledger")
        fuel = dict(amount=1000, committed_debits=[])
        passengers = dict(assignments=[dict(passenger="assigned")])
        snapshot = dict(content_hash="snapshot")
        flight = dict(schema_version=2, state=phase, fuel=fuel, passengers=passengers)
        if phase == "TRANSIT":
            flight["active_transfer_id"] = "transfer"
        data = dict(flight_data=flight, resource_escrow=dict(schema_version=1, ledger_id="ledger", resource_epoch=1, markers=[]),
                    assembly_transaction_id="logical", owner_id="owner", snapshot=snapshot)
        source = dict(UUID="source", RocketEntityData=data, Pos=[64.5, 148 if phase == "TRANSIT" else 100 if phase == "COUNTDOWN" else 124, 64.5])
        transfer = dict(schema_version=3, phase="PREPARED", transfer_id="transfer", source_snapshot=snapshot,
                        source_flight=dict(schema_version=2, fuel=fuel, passengers=passengers), destination_flight=dict(schema_version=2),
                        native_transfer=dict(schema_version=1, armed=arm.copy(), signature_kind="FUEL_VECTOR_V1"))
        return copy.deepcopy((root, transfer, source))

    def test_all_phase_cuts_require_exact_source_authority_and_keep_the_same_invariant(self):
        invariants = []
        self.assertEqual(runner.CASES, ("SC_COUNTDOWN", "SC_ASCENT", "SC_TRANSIT"))
        for cut in runner.CASES:
            phase = cut.removeprefix("SC_")
            invariants.append(runner.require_source(*self.fixture(phase), phase))
        self.assertTrue(all(value == invariants[0] for value in invariants))

    def test_refuses_debit_resource_wrapper_manifest_and_identity_changes(self):
        for kind in ("fuel", "debit", "epoch", "marker", "ledger", "manifest", "physical", "snapshot", "position"):
            root, transfer, source = self.fixture()
            data = source["RocketEntityData"]
            # Break shared fixture references so a source-only edit cannot silently alter the expected record.
            source = copy.deepcopy(source); data = source["RocketEntityData"]
            if kind == "fuel": data["flight_data"]["fuel"]["amount"] = 999
            elif kind == "debit": data["flight_data"]["fuel"]["committed_debits"].append("transfer")
            elif kind == "epoch": data["resource_escrow"]["resource_epoch"] = 0
            elif kind == "marker": data["resource_escrow"]["markers"].append("marker")
            elif kind == "ledger": data["resource_escrow"]["ledger_id"] = "foreign"
            elif kind == "manifest": data["flight_data"]["passengers"]["assignments"] = []
            elif kind == "physical": source["UUID"] = "another"
            elif kind == "snapshot": data["snapshot"]["content_hash"] = "changed"
            elif kind == "position": source["Pos"][0] += 1
            with self.subTest(kind=kind), self.assertRaises(runner.smoke.SmokeError):
                runner.require_source(root, transfer, source, "ASCENT")

    def test_refuses_terminal_or_missing_authority_and_incorrect_phase(self):
        for kind in ("terminal", "missing", "serial", "publication", "flight_phase", "active_transfer"):
            root, transfer, source = self.fixture("TRANSIT")
            if kind == "terminal": root["armed_flights"][0]["outcome"] = "HANDED_OFF"
            elif kind == "missing": root["armed_flights"] = []
            elif kind == "serial": root["issued_launch_serial"] = 2
            elif kind == "publication": transfer["phase"] = "DESTINATION_SPAWNED"
            elif kind == "flight_phase": source["RocketEntityData"]["flight_data"]["state"] = "ASCENT"
            elif kind == "active_transfer": source["RocketEntityData"]["flight_data"]["active_transfer_id"] = "other"
            with self.subTest(kind=kind), self.assertRaises(runner.smoke.SmokeError):
                runner.require_source(root, transfer, source, "TRANSIT")


if __name__ == "__main__":
    unittest.main()
