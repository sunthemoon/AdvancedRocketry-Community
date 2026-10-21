"""R52 native oracle tests, independent of a running Forge server."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_retire_before_fill as cuts


def fixture(completed=False):
    section = {"schema_version": 1, "resource_epoch": int(completed), "markers": []}
    if completed:
        section["ledger_id"] = "ledger"
    root = dict(ledger_id="ledger", issued_through=1, records=[], floors=[], tombstones=[], armed_flights=[],
                rocket_authorities=[], issued_launch_serial=[]) if completed else None
    flight = dict(state="LANDED", passengers={"assignments": []}, fuel={"propulsion": {
        "primary": {"amount": int(completed) * 1000}, "oxidizer": {"amount": 0}}})
    rocket = dict(UUID="entity", Pos=[64.5, 100., 64.5], RocketEntityData=dict(flight_data=flight,
                  resource_escrow=copy.deepcopy(section), owner_id="owner", assembly_transaction_id="logical"))
    loader = dict(arce_fluid_fuel_loader=dict(owner="owner", device="device", binding=dict(physical_rocket="entity", logical_rocket="logical"),
                  tank={"FluidName": cuts.base.FUEL, "Amount": 5000 - int(completed) * 1000}, resource_escrow=copy.deepcopy(section)))
    return dict(rocket=rocket, loader=loader, ledger=root, source=[], transfers=[] if completed else [{}])


class RetirementCutOracleTest(unittest.TestCase):
    def test_pre_removal_has_no_ledger(self):
        for case in ("CAPTURE", "STORE", "REMOVE", "STALE", "TICKET", "HELD_COLUMN", "HELD_STORAGE"):
            with self.subTest(case=case):
                cuts.check(fixture(), case, 0)

    def test_checked_removal_precedes_binding(self):
        for case in ("MOVED", "UNKNOWN", "ACK"):
            state = fixture(); state["transfers"] = []
            cuts.check(state, case, 0)
            state["source"] = [{"id": cuts.base.ROCKET}]
            with self.assertRaises(cuts.smoke.SmokeError):
                cuts.check(state, case, 0)

    def test_both_restarts_require_exactly_one_completed_fill(self):
        for case in cuts.CUTS:
            for restart in (1, 2):
                with self.subTest(case=case, restart=restart):
                    cuts.check(fixture(not case.startswith("HELD_")), case, restart)

    def test_rejects_floor_and_resource_drift(self):
        for mutate in (lambda d: d["ledger"]["floors"].append({}),
                       lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"].update(amount=2000),
                       lambda d: d["loader"]["arce_fluid_fuel_loader"]["tank"].update(Amount=3000),
                       lambda d: d["transfers"].append({}),
                       lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=2)):
            state = fixture(True); mutate(state)
            with self.assertRaises(cuts.smoke.SmokeError):
                cuts.check(state, "K9", 1)

    def test_k2_requires_none_guard(self):
        state = fixture(); state["transfers"] = []
        state["ledger"] = fixture(True)["ledger"]
        state["ledger"]["records"] = [{"transfer_guard": {}}]
        cuts.check(state, "K2", 0)
        state["ledger"]["records"][0]["transfer_guard"]["transfer_id"] = "old"
        with self.assertRaises(cuts.smoke.SmokeError):
            cuts.check(state, "K2", 0)


if __name__ == "__main__":
    unittest.main()
