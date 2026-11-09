import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_owner_cap as cap
import run_v180_cargo_s4_s5_cuts as fluid
import item_cuts_oracle as item
from test_cargo_s4_s5_cuts_runner import released_disk


class CargoW330RunnerTest(unittest.TestCase):
    def test_cap_controller_waits_for_its_fixture_marker_on_all_three_starts(self):
        with patch.object(cap.cuts, "CASES", cap.CASES):
            for case in cap.CASES:
                for restart in (0, 1, 2):
                    prefix = "ARCE_RELEASE_CARGO_CUT_RESULT" if restart else "ARCE_RELEASE_CARGO_CUT_PAUSED"
                    self.assertEqual(cap.cuts.boundary_marker(case, restart), prefix + " cut=" + case + " ")

    def test_load_quantities_roles_and_retirement_are_not_unload_evidence(self):
        for typed in (False, True):
            oracle = item if typed else fluid
            case = "IS_LOAD_PRUNE_AFTER" if typed else "FS_LOAD_PRUNE_AFTER"
            disk = released_disk()
            disk["rocket"]["cargo"] = 64 if typed else 1000
            disk["device"].update(amount=6 if typed else 4000, role="LOAD")
            if typed:
                disk["ledger"]["tombstones"][0]["survivors"][1].update(signature_kind="ITEM_BUFFER_V1", quantity=0)
            oracle.check_settlement(case, disk, True)
            for mutate in (lambda d: d["device"].update(role="UNLOAD"), lambda d: d["rocket"].update(cargo=0),
                           lambda d: d["device"].update(amount=5000), lambda d: d["rocket"].update(epoch=0),
                           lambda d: d["ledger"]["tombstones"][0].update(accounting_status="KNOWN")):
                bad = copy.deepcopy(disk); mutate(bad)
                with self.assertRaises(fluid.smoke.SmokeError): oracle.check_settlement(case, bad, True)

    def test_load_cases_keep_the_original_boundary_set(self):
        loads = {name for name in fluid.CASES if name.startswith("FS_LOAD_")}
        self.assertEqual(len(loads), 20)
        for name in loads:
            self.assertEqual(fluid.BOUNDARIES[name], fluid.BOUNDARIES[name.replace("FS_LOAD_", "FS_", 1)])
        self.assertEqual(sum(name.startswith("IS_LOAD_") for name in item.CASES), 13)
        self.assertEqual(fluid.CASES["FS_LOAD_VETO_DESTINATION_SAVED"]["final"][1],
                         dict(rocket=(1000, 1, 1, True), device=(5000, 0, 0)))

    @staticmethod
    def cap_disk(case, restarted):
        typed = "_ITEM_" in case; load = case.endswith("_LOAD")
        before = (60 if load else 10, 10 if load else 60) if typed else (0 if load else 5000, 5000 if load else 0)
        after = (64 if load else 6, 6 if load else 64) if typed else (1000 if load else 4000, 4000 if load else 1000)
        def rocket(owner, z, quantity, epoch):
            return dict(uuid=owner, logical=owner, owner=owner, pos=[68.5, 100.0, z], cargo=quantity,
                        epoch=epoch, markers=[], passengers=[], fuel=1000)
        def device(owner, quantity, epoch):
            return dict(id=owner, logical=owner, owner=owner, amount=quantity, epoch=epoch, markers=[], role="LOAD" if load else "UNLOAD")
        return dict(rockets=[rocket("A", 64.5, before[0], 0), rocket("B", 68.5, after[0] if restarted else before[0], int(restarted))],
                    device=device("A", before[1], 0), second=device("B", after[1] if restarted else before[1], int(restarted)), journal=0,
                    ledger=dict(records=[dict(op_id=n, owner="A", phase="REPAIR", reason="OTHER_STATE", guard=dict(rocket_id=n)) for n in range(16)],
                                issued_through=17 if restarted else 16, tombstones=[], floors=[], rocket_authorities=[], armed_flights=[]))

    def test_all_four_caps_require_refusal_and_one_independent_transfer_after_both_restarts(self):
        for case in cap.CASES:
            for restarted in (False, True):
                disk = self.cap_disk(case, restarted); cap.check(case, disk, restarted)
                for mutate in (lambda d: d["ledger"]["records"].pop(), lambda d: d["rockets"][0].update(epoch=1),
                               lambda d: d["device"].update(amount=9), lambda d: d["rockets"][1].update(owner="A")):
                    bad = copy.deepcopy(disk); mutate(bad)
                    with self.assertRaises(fluid.smoke.SmokeError): cap.check(case, bad, restarted)
                line = ("ARCE_RELEASE_CARGO_CUT_RESULT cut=" + case + " pending=16 refused=OWNER_PENDING_FULL independent_epoch=1" if restarted
                        else "ARCE_RELEASE_CARGO_CUT_PAUSED cut=" + case + " boundary=OWNER_PENDING_REFUSED")
                cap.observations([line], case, restarted)
                for lines in ([], [line, line], [line, "[thread/ERROR] error"]):
                    with self.assertRaises(fluid.smoke.SmokeError): cap.observations(lines, case, restarted)


if __name__ == "__main__":
    unittest.main()
