"""W358 controls, capacity/byte-preservation oracles and pinned campaign dispatch; no JVM."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_uncovered as runner
import run_v180_integrated_campaign as campaign
from test_run_v180_integrated_campaign import arguments, SOURCE, HASH


class Oracles(unittest.TestCase):
    def test_destination_warmup_retries_with_new_request_identity(self):
        class Session:
            class args:
                settle_timeout = 2
            def __init__(self):
                self.requests = []
            def send(self, command, pattern):
                request = command.split()[-1]; self.requests.append(request)
                return "ARCE_W358_LAUNCH logical=L transfer=" + request + " result=" + ("DESTINATION_LOADING" if len(self.requests) == 1 else "SUCCESS")
        run = Session()
        with patch.object(runner.time, "sleep"):
            accepted = runner.launch(run, dict(logical="L"))
        self.assertEqual(2, len(set(run.requests)))
        self.assertEqual(run.requests[1], accepted)

    def test_loader_refusal_requires_no_native_capture_and_no_mutation(self):
        row = dict(captured="false", status="OWNER_PENDING_FULL", unchanged="true")
        runner.check_refusal(row, "OWNER_PENDING_FULL")
        for key, value in (("captured", "true"), ("status", "PREPARING"), ("unchanged", "false")):
            with self.subTest(key=key), self.assertRaises(runner.bay.smoke.SmokeError):
                runner.check_refusal(dict(row, **{key: value}), "OWNER_PENDING_FULL")

    def test_both_admission_consumers_must_refuse_the_exact_mixed_union(self):
        for escrow in (1, 63):
            row = dict(pending="64", escrow=str(escrow), bay="SERVER_FULL", fluid="SERVER_FULL")
            runner.check_full(row, escrow)
            for key, value in (("pending", "65"), ("pending", "63"), ("escrow", "0"), ("bay", "RESERVED"), ("fluid", "RESERVED")):
                with self.subTest(escrow=escrow, key=key), self.assertRaises(runner.bay.smoke.SmokeError):
                    runner.check_full(dict(row, **{key: value}), escrow)

    def test_unknown_requires_captured_unknown_successor_and_two_distinct_retained_ops(self):
        row = dict(pending="64", escrow="62", bay="SERVER_FULL", fluid="SERVER_FULL", unknown="true", captured="1",
                   successor="true", retained="2")
        runner.check_unknown(row)
        for key, value in (("unknown", "false"), ("successor", "false"), ("captured", "0"), ("retained", "3")):
            with self.subTest(key=key), self.assertRaises(runner.bay.smoke.SmokeError):
                runner.check_unknown(dict(row, **{key: value}))

    def test_unconsumed_requires_both_original_signatures_no_consumption_and_no_registration(self):
        fixture = dict(packages="a+b")
        row = dict(packages="a+b", consumed=[None, None], owned=0)
        runner.unchanged(row, fixture)
        for key, value in (("packages", "a+c"), ("consumed", ["op", None]), ("owned", 1)):
            with self.subTest(key=key), self.assertRaises(runner.bay.smoke.SmokeError):
                runner.unchanged(dict(row, **{key: value}), fixture)

    def test_frozen_refuses_landing_release_changed_packages_phase_or_operation(self):
        fixture = dict(logical="L", packages="a+b")
        row = dict(packages="a+b", consumed=[None, None], owned=0, claim="OUTCOMES", state="TRANSIT", transfer="PREPARED",
                   receipts=[dict(op="op", phase="REPAIR", acknowledged=True)])
        class Session:
            def status(self, logical):
                return self.row
            def send(self, *args):
                return "ARCE_RELEASE_BAY_FLAGS logical=L rows=[op:REPAIR:false:true]"
        run = Session(); run.row = row
        with patch.object(runner.time, "sleep"):
            self.assertEqual(5, len(runner.frozen(run, fixture, "op")))
            for key, value in (("state", "LANDED"), ("transfer", "COMMITTED"), ("claim", "NONE"),
                               ("receipts", [dict(op="other", phase="REPAIR", acknowledged=True)]),
                               ("receipts", [dict(op="op", phase="REGISTERED", acknowledged=True)])):
                run.row = dict(row, **{key: value})
                with self.subTest(key=key), self.assertRaises(runner.bay.smoke.SmokeError):
                    runner.frozen(run, fixture, "op")

    def test_new_families_cover_only_the_implemented_rows_and_keep_existing_selections(self):
        rows = {r["name"]: r for r in campaign.family_specs()}
        self.assertEqual({"B6.8", "B10.6", "B11.8", "B11.9", "B11.7", "CAP.3", "B17.3"}, set(sum((list(v) for v in runner.CASES.values()), [])))
        for key, cases in runner.TRANCHES.items():
            row = rows["forced-" + key.replace("_", "-")]
            self.assertEqual(cases, row["cases"])
            self.assertEqual("scripts/run_v180_bay_uncovered.py", row["runner"])
            self.assertTrue(row["stop_on_failure"])
            self.assertEqual(["--tranche", key], row["arguments"])
        for key, cases in campaign.forced.W331_TRANCHES.items():
            self.assertEqual(cases, rows["forced-" + key.replace("_", "-")]["cases"])

    def test_every_new_family_passes_candidate_and_installed_jar_pins_to_the_shared_runner(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(campaign, "resolve_commit", return_value=SOURCE):
            plan = campaign.make_plan(arguments(Path(directory)))
        rows = [r for r in plan["families"] if r["name"].startswith("forced-W358-")]
        self.assertEqual(7, len(rows))
        for row in rows:
            command = row["command"]
            self.assertEqual(SOURCE, command[command.index("--candidate-commit") + 1])
            self.assertEqual(HASH, command[command.index("--jar-sha256") + 1])
            self.assertEqual(SOURCE, command[command.index("--source-commit") + 1])
            self.assertEqual("--stop-on-failure", command[-1])


if __name__ == "__main__":
    unittest.main()
