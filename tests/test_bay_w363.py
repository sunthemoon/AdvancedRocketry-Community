"""W363's B17.3 oracles and campaign dispatch; no JVM."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_uncovered as runner
import run_v180_integrated_campaign as campaign

RECEIPTS = [dict(op="op", satellite="s", phase="REGISTERED", reason="NONE", acknowledged=True, registered=True)]
HELD = dict(claim="RESUME", hold="OWNED_WAIT", reason="END_LOADING", transfer="PREPARED", receipts=RECEIPTS, consumed=[])
DEPLOYED = dict(state="LANDED", claim="NONE", receipts=[], consumed=["op2", None], owned=2, packages="a+b", rocket="r")


class Oracles(unittest.TestCase):
    def test_unloaded_group_must_stay_at_its_wait_with_unchanged_receipts(self):
        runner.check_end_held(HELD, RECEIPTS)
        runner.check_end_held(dict(HELD, consumed=[None, None]), RECEIPTS)
        changed = [dict(RECEIPTS[0], op="other")]
        for key, value in (("claim", "CONSUME"), ("claim", "NONE"), ("hold", "REPAIR"), ("reason", "NONE"),
                           ("transfer", "COMMITTED"), ("receipts", changed), ("receipts", []), ("consumed", ["op", None])):
            with self.subTest(key=key, value=value), self.assertRaises(runner.bay.smoke.SmokeError):
                runner.check_end_held(dict(HELD, **{key: value}), RECEIPTS)
        for receipts in ([dict(RECEIPTS[0], phase="INTENT")], [dict(RECEIPTS[0], acknowledged=False)], RECEIPTS * 2):
            with self.subTest(receipts=receipts), self.assertRaises(runner.bay.smoke.SmokeError):
                runner.check_end_held(dict(HELD, receipts=receipts), receipts)

    def test_healthy_group_must_land_consume_retire_and_register(self):
        runner.check_deployed(DEPLOYED, 2)
        for key, value in (("state", "TRANSIT"), ("claim", "RESUME"), ("receipts", RECEIPTS), ("consumed", [None, None]),
                           ("consumed", ["op2", "op3"]), ("owned", 1)):
            with self.subTest(key=key, value=value), self.assertRaises(runner.bay.smoke.SmokeError):
                runner.check_deployed(dict(DEPLOYED, **{key: value}), 2)

    def session(self, healthy, unloaded=HELD):
        class Session:
            class args:
                flight_timeout = 0
                settle_timeout = 0
            def status(self, logical):
                return unloaded if logical == "U" else healthy
        return Session()

    def test_a_healthy_group_that_never_lands_fails_as_unbounded(self):
        stuck = dict(DEPLOYED, state="NONE", claim="RESUME", receipts=RECEIPTS, consumed=[])
        with patch.object(runner.time, "sleep"), self.assertRaises(runner.bay.smoke.SmokeError) as failure:
            runner.healthy_while_held(self.session(stuck), dict(logical="U"), dict(logical="H"), RECEIPTS, 2)
        self.assertIn("no bounded progress", str(failure.exception))

    def test_a_healthy_landing_passes_only_while_the_unloaded_group_still_waits(self):
        with patch.object(runner.time, "sleep"):
            result = runner.healthy_while_held(self.session(DEPLOYED), dict(logical="U"), dict(logical="H"), RECEIPTS, 2)
            self.assertEqual(6, result["unloaded_samples"])
            with self.assertRaises(runner.bay.smoke.SmokeError):
                runner.healthy_while_held(self.session(DEPLOYED, dict(HELD, claim="CONSUME")), dict(logical="U"),
                                          dict(logical="H"), RECEIPTS, 2)

    def test_both_cases_are_campaign_families_of_the_shared_runner(self):
        rows = {row["name"]: row for row in campaign.family_specs()}
        for case in ("W363_B17_UNLOADED", "W363_B17_UNLOADED_RESTART"):
            self.assertEqual(("B17.3",), runner.CASES[case])
            row = rows["forced-" + case.replace("_", "-")]
            self.assertEqual([case], row["cases"])
            self.assertEqual("scripts/run_v180_bay_uncovered.py", row["runner"])
            self.assertEqual(["--tranche", case], row["arguments"])
        # The existing W358 tranche names are unchanged by the simplified key expression.
        self.assertEqual([case for case in runner.CASES], list(runner.TRANCHES))


if __name__ == "__main__":
    unittest.main()
