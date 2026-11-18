"""W373B's accounting cut runner: case tables, log observations and the loader delegation, without a native server."""
import argparse
from pathlib import Path
import re
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import accounting_cut_oracle as oracle
import run_v180_accounting_cuts as runner
import run_v180_cargo_cuts as cargo_cuts
import run_v180_cargo_s4_s5_cuts as settlement_cuts
from run_dedicated_server_smoke import SmokeError

REPO = Path(__file__).resolve().parents[1]
PROBE = REPO / "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoSettlementCuts.java"


class AccountingCutsRunnerTest(unittest.TestCase):
    def test_families_hold_the_planned_cases_once(self):
        self.assertEqual({"cargo-load": 17, "cargo-unload": 17, "loader": 6, "outcomes": 18},
                         {name: len(cases) for name, cases in runner.FAMILIES.items()})
        self.assertEqual(58, len(runner.CASES))
        self.assertEqual(58, sum(len(cases) for cases in runner.FAMILIES.values()))
        self.assertTrue(all(case.startswith(("FC_LOAD_", "FS_LOAD_")) for case in runner.FAMILIES["cargo-load"]))
        self.assertTrue(all(case.startswith(("FC_UNLOAD_", "FS_")) and "_LOAD_" not in case for case in runner.FAMILIES["cargo-unload"]))
        self.assertIn(runner.CASE_PROPERTY, (REPO / "scripts/run_v180_accounting_cuts.py").read_text(encoding="utf-8"))

    def test_every_case_names_an_existing_probe_case_and_boundary(self):
        java = PROBE.read_text(encoding="utf-8")
        declared = dict(re.findall(r'Map\.entry\("(FS_[A-Z0-9_]+)", new Cut\(Scenario\.[A-Z_]+, "([A-Z0-9_]+)"\)\)', java))
        for case in runner.CASES:
            plan = runner.spec(case)
            with self.subTest(case=case):
                if plan["engine"] == "loader":
                    self.assertIn(case, runner.loader.oracle.CASES)
                    continue
                self.assertEqual(3, len(plan["states"]))
                if plan["engine"] == "ordinary":
                    # The existing fluid cargo runner already drives this probe case at this checkpoint.
                    self.assertEqual(cargo_cuts.CASES[case]["checkpoint"] + " ", plan["boundary"].removeprefix("checkpoint="))
                    self.assertEqual(240, plan["timeout"])
                else:
                    unload = case.replace("FS_LOAD_", "FS_", 1)
                    self.assertEqual(declared[unload], plan["boundary"].removeprefix("boundary="))
                    if "_AC_" not in case:
                        self.assertEqual(settlement_cuts.BOUNDARIES[case], declared[unload])
                    self.assertEqual(420, plan["timeout"])
                self.assertEqual("_LOAD_" in case, plan["load"])

    def test_observation_requires_one_marker_at_the_planned_boundary_and_no_error(self):
        case = "FS_AC_O3_ACK"; plan = runner.spec(case)
        paused = "[12:00:00] [Server thread/INFO] [x/]: ARCE_RELEASE_CARGO_CUT_PAUSED cut=FS_AC_O3_ACK boundary=O3_ACK"
        self.assertIn("boundary=O3_ACK", runner.observe(case, plan, 0, ["noise", paused])["marker"])
        for lines, message in (([], "Missing or repeated"), ([paused, paused], "Missing or repeated"),
                               ([paused.replace("O3_ACK", "O3_AFTER").replace("cut=FS_AC_O3_AFTER", "cut=FS_AC_O3_ACK")], "another boundary"),
                               ([paused, "[12:00:01] [Server thread/ERROR] [x/]: failed"], "ERROR or FATAL"),
                               ([paused, "[12:00:01] [Server thread/FATAL] [x/]: failed"], "ERROR or FATAL")):
            with self.assertRaisesRegex(SmokeError, message): runner.observe(case, plan, 0, lines)
        # A longer boundary name is not the planned one.
        with self.assertRaisesRegex(SmokeError, "another boundary"):
            runner.observe("FS_O1_AFTER", runner.spec("FS_O1_AFTER"), 0,
                           ["ARCE_RELEASE_CARGO_CUT_PAUSED cut=FS_O1_AFTER boundary=O1_AFTER_X"])
        result = "ARCE_RELEASE_CARGO_CUT_RESULT cut=FS_AC_O3_ACK state=RELEASED rocket=4000 device=1000 receipts=1 accounting=KNOWN"
        runner.observe(case, plan, 1, [result])
        ordinary = runner.spec("FC_LOAD_L1_REPLACED")
        runner.observe("FC_LOAD_L1_REPLACED", ordinary, 0, ["ARCE_RELEASE_CARGO_PAUSED cut=FC_LOAD_L1_REPLACED checkpoint=K2 selected="])
        with self.assertRaisesRegex(SmokeError, "another boundary"):
            runner.observe("FC_LOAD_L1_REPLACED", ordinary, 0, ["ARCE_RELEASE_CARGO_PAUSED cut=FC_LOAD_L1_REPLACED checkpoint=K1 selected="])

    def test_signed_outcomes_must_report_their_knowledge_on_restart(self):
        for case, status in (("FS_AC_LOSS", "KNOWN"), ("FS_LOAD_AC_GAIN", "KNOWN"), ("FS_AC_SUBSTITUTE", "KNOWN"),
                             ("FS_ABANDON_ROCKET", "UNKNOWN"), ("FS_LOAD_ABANDON_DEVICE", "UNKNOWN")):
            plan = runner.spec(case)
            line = f"ARCE_RELEASE_CARGO_CUT_RESULT cut={case} state=RELEASED rocket=4000 device=900 receipts=1 accounting="
            runner.observe(case, plan, 2, [line + status])
            other = "UNKNOWN" if status == "KNOWN" else "KNOWN"
            with self.assertRaisesRegex(SmokeError, "knowledge"): runner.observe(case, plan, 2, [line + other])

    def test_the_readback_hold_must_come_from_the_store_and_persist(self):
        case = "FS_AC_READBACK"; plan = runner.spec(case)
        paused = "ARCE_RELEASE_CARGO_CUT_PAUSED cut=FS_AC_READBACK boundary=READBACK_HELD"
        held = "ARCE_ESCROW_SETTLEMENT_HELD digest=abc reason={} records=retained"
        observed = runner.observe(case, plan, 0, [held.format("ACCOUNTING_MISMATCH"), held.format("Frozen settlement endpoint changed"), paused])
        self.assertEqual(["ACCOUNTING_MISMATCH", "Frozen_settlement_endpoint_changed"], observed["held_reasons"])
        with self.assertRaisesRegex(SmokeError, "never reported"): runner.observe(case, plan, 0, [paused])
        # The live check alone, or a storage reason, is not the typed per-fluid refusal this case is about.
        for first in ("Frozen settlement endpoint changed", "BARRIER_UNSTABLE", "BARRIER_VETO"):
            with self.assertRaisesRegex(SmokeError, "not the typed check"):
                runner.observe(case, plan, 0, [held.format(first), paused])
        with self.assertRaisesRegex(SmokeError, "later hold"):
            runner.observe(case, plan, 0, [held.format("ACCOUNTING_MISMATCH"), held.format("BARRIER_IO"), paused])
        result = "ARCE_RELEASE_CARGO_CUT_RESULT cut=FS_AC_READBACK state=HELD reason=x rocket=4000 device=1000 receipts=0 locked=true"
        runner.observe(case, plan, 1, [result])
        with self.assertRaisesRegex(SmokeError, "retained hold"):
            runner.observe(case, plan, 1, [result.replace("state=HELD", "state=RELEASED")])

    def test_a_store_refuses_a_tagged_or_malformed_fluid(self):
        section = dict(resource_epoch=1, markers=[{}])
        self.assertEqual(dict(fluids={oracle.WATER: 1000}, epoch=1, markers=1),
                         runner.fluid_store(dict(FluidName=oracle.WATER, Amount=1000), section))
        self.assertEqual(dict(fluids={}, epoch=1, markers=1), runner.fluid_store({}, section))
        for fluid in (dict(FluidName=oracle.WATER, Amount=1000, Tag={}), dict(FluidName=oracle.WATER, Amount=0), dict(Amount=5)):
            with self.assertRaisesRegex(SmokeError, "malformed or tagged"): runner.fluid_store(fluid, section)

    def loader_run(self, directory):
        root = Path(directory)
        (root / "session").mkdir(); (root / "evidence").mkdir()
        args = argparse.Namespace(lock_owner="owner", jar=root / "candidate.jar")
        return types.SimpleNamespace(args=args, session=root / "session", evidence=root / "evidence", pin=dict(jar_sha256="0" * 64))

    def test_loader_cases_keep_the_loader_runner_and_add_the_account_oracle(self):
        calls = []

        def run_cut(args, case, evidence, owner, pins):
            calls.append((case, evidence.name, owner, args.session_parent.name, args.follow_ups, sorted(pins)))
            return dict(status="PASS", starts=[dict(restart=index, disk=dict(ledger=None, index=index)) for index in range(3)])

        with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader, "run_cut", run_cut), \
                patch.object(runner.oracle, "check_loader", lambda disk, case, restart: dict(checked=(case, restart, disk["index"]))):
            run = self.loader_run(directory); row = dict(starts=[], findings=[])
            runner.run_loader(run, "LF_M_D2_C1_ACK", row)
            self.assertEqual([("LF_M_D2_C1_ACK", "LF_M_D2_C1_ACK", "owner", "loader", False, ["harness", "hooks", "jar_sha256"])], calls)
            self.assertEqual([], row["findings"])
            self.assertEqual([("LF_M_D2_C1_ACK", index, index) for index in range(3)], [s["oracle"]["checked"] for s in row["starts"]])

    def test_a_loader_finding_an_oracle_finding_and_a_missing_start_all_fail_the_case(self):
        def failing(disk, case, restart):
            if restart == 1: raise SmokeError("Account baseline differs from the fixture")
            return {}

        for result, expected in ((dict(status="FAILED", error="Validation failed", starts=[dict(restart=i, disk=dict(ledger=None)) for i in range(3)]),
                                  ["loader runner: Validation failed", "start 1: Account baseline differs from the fixture"]),
                                 (dict(status="PASS", starts=[dict(restart=0, disk=dict(ledger=None))]), ["The loader runner completed 1 of 3 starts"]),
                                 (dict(status="PASS", starts=[dict(restart=0), dict(restart=1, disk=dict(ledger=None)), dict(restart=2, disk=dict(ledger=None))]),
                                  ["start 0: The loader runner decoded no disk for this start", "start 1: Account baseline differs from the fixture"])):
            with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader, "run_cut", lambda *a: result), \
                    patch.object(runner.oracle, "check_loader", failing):
                row = dict(starts=[], findings=[])
                runner.run_loader(self.loader_run(directory), "LF_M_D2_C7_ACK", row)
                self.assertEqual(expected, row["findings"])

    def test_a_family_reports_every_case_and_fails_on_any_finding(self):
        seen = []

        def cargo(run, case, plan, row):
            seen.append(case)
            if case == "FS_LOAD_AC_LOSS": row["findings"].append("start 0: Signed residual")

        with tempfile.TemporaryDirectory() as directory, patch.object(runner, "run_cargo", cargo), \
                patch.object(runner, "run_loader", lambda run, case, row: seen.append(case)):
            run = self.loader_run(directory); run.args.family = "outcomes"; run.args.case = None
            report = runner.family(run)
            self.assertEqual(list(runner.FAMILIES["outcomes"]), seen)
            self.assertEqual("FAILED", report["status"])
            self.assertEqual(["FS_LOAD_AC_LOSS"], [row["case"] for row in report["cases"] if row["status"] == "FAILED"])
            run.args.case = ["FS_AC_GAIN"]
            self.assertEqual(("PASS", True), (lambda r: (r["status"], r["partial"]))(runner.family(run)))
            run.args.case = ["LF_M_D2_C1_ACK"]
            with self.assertRaisesRegex(ValueError, "not in the requested family"): runner.family(run)


if __name__ == "__main__":
    unittest.main()
