"""W196 native acceptance checks reject changed packages, lost locks and retired or registered receipts."""
from copy import deepcopy
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_released_flight as runner


class ReleasedFlightChecks(unittest.TestCase):
    def setUp(self):
        self.fixture = dict(logical="l", rocket="source", data="data", packages="a+b")
        self.status = dict(state="LANDED", transfer="COMMITTED", locked="true", packages="a+b", consumed=[None, None],
                           owned=0, rocket="destination", receipts=[dict(op="op", phase="REPAIR", satellite="data",
                                                                        acknowledged=True, registered=False)])

    def test_four_cases_each_have_two_restart_checks(self):
        self.assertEqual({"B10_BLOCKED_STAGED", "B10_BLOCKED_MOVED", "B10_REPAIR_STAGED", "B10_REPAIR_MOVED"}, set(runner.CASES))
        self.assertLessEqual(len(runner.CASES) * 4, 60)

    def test_exact_landed_authority_passes(self):
        runner.check_landed(self.status, self.fixture, "REPAIR", "op")

    def test_changed_flight_or_payload_or_lock_refuses(self):
        for key, value in dict(state="TRANSIT", transfer="NONE", locked="false", packages="c+b", consumed=["op", None],
                               owned=1, rocket="source").items():
            with self.subTest(key=key):
                status = deepcopy(self.status); status[key] = value
                with self.assertRaises(runner.bay.smoke.SmokeError):
                    runner.check_landed(status, self.fixture, "REPAIR", "op")

    def test_receipt_change_retirement_and_duplicate_refuse(self):
        for rows in ([], self.status["receipts"] * 2):
            status = deepcopy(self.status); status["receipts"] = rows
            with self.assertRaises(runner.bay.smoke.SmokeError):
                runner.check_landed(status, self.fixture, "REPAIR", "op")
        for key, value in dict(op="other", phase="REJECTED", satellite="other", acknowledged=False, registered=True).items():
            with self.subTest(key=key):
                status = deepcopy(self.status); status["receipts"][0][key] = value
                with self.assertRaises(runner.bay.smoke.SmokeError):
                    runner.check_landed(status, self.fixture, "REPAIR", "op")

    def test_flags_require_the_requested_logical_and_all_four_fields(self):
        run = Mock()
        run.send.return_value = "ARCE_RELEASE_BAY_FLAGS logical=l rows=[op:REPAIR:true:false]"
        self.assertEqual([["op", "REPAIR", "true", "false"]], runner.flags(run, "l"))
        for line in ("ARCE_RELEASE_BAY_FLAGS logical=wrong rows=[op:REPAIR:true:false]",
                     "ARCE_RELEASE_BAY_FLAGS logical=l rows=[op:REPAIR:true]"):
            run.send.return_value = line
            with self.assertRaises(runner.bay.smoke.SmokeError):
                runner.flags(run, "l")


if __name__ == "__main__":
    unittest.main()
