"""Fail-closed tests for the W27d1 bay forced-stop native harness's pure checks."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_forced_stop as runner

L = "11111111-1111-4111-8111-111111111111"
X = "22222222-2222-4222-8222-222222222222"
Y = "33333333-3333-4333-8333-333333333333"
OP = "44444444-4444-4444-8444-444444444444"
FIXTURE = dict(logical=L, transfer="t", rocket="r", data=X, solar=Y, launch="SUCCESS", packages="a" * 64 + "+" + "b" * 64)


def status_line(claim="NONE", receipts="", transfer="NONE", state="FUELED", y="2.0", packages=FIXTURE["packages"]):
    return (f"[12:00:00] [Server thread/INFO] [x/]: ARCE_RELEASE_BAY_STATUS logical={L} claim={claim} hold=NONE reason=NONE "
            f"receipts=[{receipts}] transfer={transfer} rocket=r2 state={state} y={y} packages={packages}\n")


class ParseTests(unittest.TestCase):
    def test_fixture_line_parses_every_field(self):
        line = (f"ARCE_RELEASE_BAY_FIXTURE logical={L} transfer=t rocket=r data={X} solar={Y} launch=SUCCESS "
                f"packages={FIXTURE['packages']}\n")
        self.assertEqual(FIXTURE, runner.parse_fixture(line))
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_fixture("ARCE_RELEASE_BAY_FIXTURE logical=x\n")

    def test_status_line_parses_receipts(self):
        status = runner.parse_status(status_line(receipts=f"{OP}:{X}:REJECTED:INTERRUPTED:true:false"))
        self.assertEqual("NONE", status["claim"])
        self.assertEqual([dict(op=OP, satellite=X, phase="REJECTED", reason="INTERRUPTED", acknowledged=True, registered=False)],
                         status["receipts"])
        with self.assertRaises(runner.smoke.SmokeError):
            runner.parse_receipts("a:b:c")


class ExpectationTests(unittest.TestCase):
    def test_every_case_names_a_known_cut_and_a_resolvable_receipt(self):
        cuts = {"B1_STAGED", "B1_MOVED", "B9_STAGED", "B9_MOVED", "B12_UNKNOWN", "B18_INTENT", "B18_REJECTED"}
        for case, spec in runner.CASES.items():
            self.assertTrue(spec["cut"] is None or spec["cut"] in cuts, case)
            self.assertIn(spec["end"], ("kill", "stop", "crash", "land"), case)
            self.assertIn(spec["durable"], ("NONE", "INTENT", "REJECTED"), case)
            for restart in (1, 2):
                self.assertIn(runner.expected_receipt(case, restart), (None, runner.INTERRUPTED, runner.UNAVAILABLE), case)

    def test_a_durable_intent_becomes_interrupted_and_a_relaunch_rearms(self):
        self.assertEqual(runner.INTERRUPTED, runner.expected_receipt("B1_MOVED", 1))
        self.assertEqual(runner.UNAVAILABLE, runner.expected_receipt("B1_MOVED", 2, relaunched=True))
        self.assertIsNone(runner.expected_receipt("B1_STAGED", 1))
        self.assertEqual(runner.UNAVAILABLE, runner.expected_receipt("B9_MOVED", 2))
        self.assertIsNone(runner.expected_receipt("B11_FULL", 1), "a full store refuses before INTENT")
        self.assertEqual(runner.UNAVAILABLE, runner.expected_receipt("B17_FAIR", 1))

    def test_settled_needs_no_claim_the_expected_receipt_and_a_stationary_source(self):
        interrupted = f"{OP}:{X}:REJECTED:INTERRUPTED:true:false"
        self.assertTrue(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(receipts=interrupted)), FIXTURE))
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(claim="OUTCOMES", receipts=interrupted)),
                                        FIXTURE), "a held claim is not settled")
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(
            receipts=f"{OP}:{X}:INTENT:NONE:true:false")), FIXTURE), "INTENT is not resolved")
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(
            receipts=f"{OP}:{X}:REJECTED:INTERRUPTED:false:false")), FIXTURE), "an unacknowledged outcome is not settled")
        self.assertFalse(runner.settled("B1_MOVED", 1, runner.parse_status(status_line(receipts=interrupted, state="TRANSIT",
                                                                                         y="50.0")), FIXTURE))
        self.assertFalse(runner.settled("B1_STAGED", 1, runner.parse_status(status_line(receipts=interrupted)), FIXTURE),
                         "a staged-only INTENT must leave no receipt")
        self.assertTrue(runner.settled("B12_UNKNOWN", 1, runner.parse_status(status_line(
            receipts=f"{OP}:{X}:REJECTED:DEPLOYMENT_UNAVAILABLE:true:false", transfer="COMMITTED", state="LANDED",
            y="70.0")), FIXTURE))

    def test_settled_invariants_refuse_changed_packages_registration_and_foreign_receipts(self):
        good = runner.parse_status(status_line())
        runner.check_settled("B1_STAGED", 1, good, FIXTURE)
        for status in (runner.parse_status(status_line(packages="c" * 64 + "+" + "b" * 64)),
                       runner.parse_status(status_line(receipts=f"{OP}:{X}:REJECTED:INTERRUPTED:true:true")),
                       runner.parse_status(status_line(receipts=f"{OP}:{Y}:REJECTED:RESEARCH_LOCKED:true:false")),
                       runner.parse_status(status_line(receipts=f"{OP}:{OP}:REJECTED:INTERRUPTED:true:false"))):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_settled("B1_STAGED", 1, status, FIXTURE)

    def test_the_first_tick_must_refuse_decommission_and_launch_of_a_durable_intent(self):
        good = [f"ARCE_RELEASE_BAY_FIRST_TICK satellite={X} tick=1 decommission=DEPLOYMENT_PENDING launch=DEPLOYMENT_PENDING\n"]
        self.assertEqual(1, runner.check_first_tick("B1_MOVED", good, FIXTURE)["tick"])
        self.assertIsNone(runner.check_first_tick("B1_STAGED", [], FIXTURE))
        for lines in ([], [good[0].replace("decommission=DEPLOYMENT_PENDING", "decommission=SUCCESS")], good * 2):
            with self.assertRaises(runner.smoke.SmokeError):
                runner.check_first_tick("B1_MOVED", lines, FIXTURE)


if __name__ == "__main__":
    unittest.main()
