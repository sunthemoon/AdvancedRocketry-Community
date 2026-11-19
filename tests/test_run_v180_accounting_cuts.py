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
LOADER_PROBE = PROBE.with_name("EscrowLoaderS6SettlementProbe.java")


class AccountingCutsRunnerTest(unittest.TestCase):
    def test_families_hold_the_planned_cases_once(self):
        # W373C: the loader family grows from W373B's 6 cases by the 13 existing operator cuts and 4 gated cases.
        self.assertEqual({"cargo-load": 17, "cargo-unload": 17, "loader": 23, "outcomes": 18},
                         {name: len(cases) for name, cases in runner.FAMILIES.items()})
        self.assertEqual(75, len(runner.CASES))
        self.assertEqual(75, sum(len(cases) for cases in runner.FAMILIES.values()))
        self.assertEqual(runner.LOADER_CUTS, runner.FAMILIES["loader"][:6])
        self.assertEqual((13, 4), (len(runner.LOADER_SETTLEMENT), len(runner.LOADER_ACCOUNTING)))
        self.assertEqual(58, len([case for case in runner.CASES if not case.startswith(oracle.LOADER_SETTLEMENT_PREFIX)]))
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
                if plan["engine"] in ("loader-s6", "loader-settlement"):
                    self.assertIn('"' + case + '"', LOADER_PROBE.read_text(encoding="utf-8"))
                    self.assertEqual(3, len(oracle.loader_settlement(case)))
                    # The S6 runner already drives the thirteen operator cuts; it has no expectation for the new four.
                    self.assertEqual(plan["engine"] == "loader-s6", case in runner.loader_s6.oracle.OPERATOR)
                    self.assertEqual(plan["engine"] == "loader-s6", case in runner.loader_s6.CASES)
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

    def test_an_l2_cut_refuses_a_pruned_marker_on_either_endpoint_in_both_directions(self):
        """W373C, review finding 2: zero markers, or one-sided pruning, at L2_REPLACED and C7_ACKNOWLEDGED."""
        import run_v180_escrow_forced_stop as forced
        root = forced.ledger(REPO / "tests/fixtures/escrow_accounting/cargo-planned.dat"); root.update(records=[], fluid_accounts=[])

        def disk(load, device_markers, rocket_markers, epoch=1):
            device = dict(fluids={oracle.WATER: 4000 if load else 1000}, epoch=epoch, markers=device_markers)
            rocket = dict(fluids={oracle.WATER: 1000 if load else 4000}, epoch=epoch, markers=rocket_markers)
            return dict(ledger=root, device=device, rocket=rocket, journal=0)

        for direction in ("LOAD", "UNLOAD"):
            load = direction == "LOAD"
            for cut in ("L2_REPLACED", "C7_ACKNOWLEDGED"):
                plan = runner.spec(f"FC_{direction}_{cut}")
                with self.subTest(direction=direction, cut=cut):
                    self.assertEqual(0, runner.check_cargo(disk(load, 1, 1), plan, 0)["rows"])
                    for device, rocket in ((0, 0), (0, 1), (1, 0)):
                        # The message names the side that lost its marker, through the direction's own mapping;
                        # the source is checked first, so it is named when both sides lost theirs.
                        lost = "device" if device == 0 else "rocket"
                        side = "source" if device == rocket or (lost == "device") == load else "destination"
                        with self.assertRaisesRegex(SmokeError, f"Raw {side} marker count differs: 0, expected 1"):
                            runner.check_cargo(disk(load, device, rocket), plan, 0)
                    for start in (1, 2):
                        runner.check_cargo(disk(load, 0, 0), plan, start)
                        for device, rocket in ((1, 1), (0, 1), (1, 0)):
                            with self.assertRaisesRegex(SmokeError, "marker count differs: 1, expected 0"):
                                runner.check_cargo(disk(load, device, rocket), plan, start)
                    with self.assertRaisesRegex(SmokeError, "transfer journal"):
                        runner.check_cargo(dict(disk(load, 1, 1), journal=1), plan, 0)

    def loader_run(self, directory):
        root = Path(directory).resolve()
        (root / "session").mkdir(); (root / "evidence").mkdir()
        args = argparse.Namespace(lock_owner="owner", jar=root / "candidate.jar")
        patcher = patch.object(runner.native, "TEMP", root); patcher.start(); self.addCleanup(patcher.stop)
        return types.SimpleNamespace(args=args, session=root / "session", evidence=root / "evidence", pin=dict(jar_sha256="0" * 64),
                                     token="0123456789abcdef0123456789abcdef")

    def test_loader_cases_keep_the_loader_runner_and_add_the_account_oracle(self):
        calls = []

        def run_cut(args, case, evidence, owner, pins):
            calls.append((case, evidence.name, owner, args.session_parent.name, args.follow_ups, sorted(pins)))
            return dict(status="PASS", starts=[dict(restart=index, disk=dict(ledger=None, index=index)) for index in range(3)])

        with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader, "run_cut", run_cut), \
                patch.object(runner.oracle, "check_loader", lambda disk, case, restart: dict(checked=(case, restart, disk["index"]))):
            run = self.loader_run(directory); row = dict(starts=[], findings=[])
            runner.run_loader(run, "LF_M_D2_C1_ACK", row)
            # The loader's own sessions go to a short sibling of the session, never below its long campaign name.
            self.assertEqual([("LF_M_D2_C1_ACK", "LF_M_D2_C1_ACK", "owner", "AL-0123456789ab", False, ["harness", "hooks", "jar_sha256"])], calls)
            self.assertEqual([], row["findings"])
            self.assertEqual([("LF_M_D2_C1_ACK", index, index) for index in range(3)], [s["oracle"]["checked"] for s in row["starts"]])
            self.assertEqual([], row["loader_parent_left"])
            self.assertFalse((run.session.parent / "AL-0123456789ab").exists())
            # The longest loader path of a campaign stays inside Windows' limit.
            longest = (Path("D:/GitHub/ARCE-Task-Evidence/Temp") / "AL-0123456789ab" / ("LF_M_C2_PARTIAL_REPLACED-" + "0" * 32)
                       / "libraries/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412-mappings-merged.txt")
            self.assertLess(len(str(longest)), 248)

    def test_a_loader_session_left_behind_keeps_its_parent_and_is_reported(self):
        def run_cut(args, case, evidence, owner, pins):
            (args.session_parent / "LF_M_D2_C1_ACK-left").mkdir()
            return dict(status="PASS", starts=[dict(restart=index, disk=dict(ledger=None)) for index in range(3)])

        with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader, "run_cut", run_cut), \
                patch.object(runner.oracle, "check_loader", lambda disk, case, restart: {}):
            run = self.loader_run(directory); row = dict(starts=[], findings=[])
            runner.run_loader(run, "LF_M_D2_C1_ACK", row)
            self.assertEqual(["LF_M_D2_C1_ACK-left"], row["loader_parent_left"])
            self.assertTrue((run.session.parent / "AL-0123456789ab").is_dir())
            run.session = Path(directory).resolve() / "elsewhere" / "session"
            with self.assertRaisesRegex(ValueError, "Unsafe loader session parent"): runner.loader_parent(run)

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

    def test_loader_settlement_cuts_keep_the_s6_runner_and_add_the_account_oracle(self):
        """W373C: the thirteen existing operator cuts go through run_v180_loader_s6.run_cut, then the account oracle."""
        calls = []

        def run_cut(args, case, evidence, owner, pins):
            calls.append((case, evidence.name, owner, args.session_parent.name, sorted(pins), len(pins["hooks"]), len(pins["harness"])))
            return dict(status="PASS", findings=[], starts=[
                dict(restart=index, image=dict(read_status="KNOWN", native=dict(ledger=None, index=index))) for index in range(3)])

        with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader_s6, "run_cut", run_cut), \
                patch.object(runner.oracle, "check_loader_settlement", lambda disk, case, restart: dict(checked=(case, restart, disk["index"]))):
            run = self.loader_run(directory); row = dict(starts=[], findings=[])
            runner.run_loader_s6(run, "LF_S6_O3_REPLACED", row)
            self.assertEqual([("LF_S6_O3_REPLACED", "LF_S6_O3_REPLACED", "owner", "AL-0123456789ab", ["harness", "hooks", "jar_sha256"],
                               len(runner.loader_s6.HOOKS), len(runner.loader_s6.HARNESSES))], calls)
            self.assertEqual([], row["findings"])
            self.assertEqual([("LF_S6_O3_REPLACED", index, index) for index in range(3)], [s["oracle"]["checked"] for s in row["starts"]])
            self.assertFalse((run.session.parent / "AL-0123456789ab").exists())
        # An unreadable image is never a disk, and the S6 runner's own findings fail the case.
        self.assertIsNone(runner.s6_disk(dict(restart=0, image=dict(read_status="UNKNOWN", native=None))))
        self.assertIsNone(runner.s6_disk(dict(restart=0)))
        failed = dict(status="FAILED", findings=[dict(restart=1, kind="RAW", error="Settlement changed endpoint/ledger identity")],
                      starts=[dict(restart=0, image=dict(read_status="KNOWN", native=dict(ledger=None))),
                              dict(restart=1, image=dict(read_status="UNKNOWN", native=None, error="x"))])
        with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader_s6, "run_cut", lambda *a: failed), \
                patch.object(runner.oracle, "check_loader_settlement", lambda disk, case, restart: {}):
            row = dict(starts=[], findings=[])
            runner.run_loader_s6(self.loader_run(directory), "LF_S6_O1_ACK", row)
            self.assertEqual(3, len(row["findings"]))
            self.assertIn("loader S6 runner: ", row["findings"][0]); self.assertIn("Settlement changed endpoint", row["findings"][0])
            self.assertEqual(["start 1: The loader runner decoded no disk for this start", "The loader runner completed 2 of 3 starts"],
                             row["findings"][1:])

    def test_the_new_loader_cases_stop_at_their_own_hook_and_report_the_exact_released_state(self):
        plans = {case: runner.spec(case) for case in runner.LOADER_ACCOUNTING}
        self.assertEqual({"loader-settlement"}, {plan["engine"] for plan in plans.values()})
        self.assertEqual({"LF_S6_AC_PRUNE_BEFORE": 4000, "LF_S6_AC_PRUNE_AFTER": 4000, "LF_S6_AC_LOSS": 3900, "LF_S6_AC_GAIN": 4100},
                         {case: int(re.search(r"tank=(\d+) ", plan["result"])[1]) for case, plan in plans.items()})
        java = LOADER_PROBE.read_text(encoding="utf-8")
        self.assertIn('"ARCE_LOADER_S6_O_PAUSED cut={} hook={}"', java)
        self.assertIn('"ARCE_LOADER_S6_O_RESULT cut={} tank={} bank=1000 epochs=1,1 records={} receipts={} state={}{}"', java)
        plan = plans["LF_S6_AC_LOSS"]
        paused = "[12:00:00] [Server thread/INFO] [x/]: ARCE_LOADER_S6_O_PAUSED cut=LF_S6_AC_LOSS hook=K2"
        result = ("[12:00:00] [Server thread/INFO] [x/]: ARCE_LOADER_S6_O_RESULT cut=LF_S6_AC_LOSS tank=3900 bank=1000 epochs=1,1 "
                  "records=0 receipts=1 state=RELEASED accounting=KNOWN")
        self.assertEqual("ARCE_LOADER_S6_O_PAUSED cut=LF_S6_AC_LOSS hook=K2", runner.observe_loader(plan, 0, ["noise", paused + "\n"])["marker"])
        self.assertTrue(runner.observe_loader(plan, 2, [result])["marker"].endswith("accounting=KNOWN"))
        for start, lines, message in ((0, [], "Missing, repeated or different"), (0, [paused, paused], "Missing, repeated or different"),
                                      (0, [paused.replace("hook=K2", "hook=K1")], "Missing, repeated or different"),
                                      (0, [paused, paused.replace("hook=K2", "hook=O1_ACK")], "Missing, repeated or different"),
                                      (0, [paused, "[12:00:01] [Server thread/ERROR] [x/]: failed"], "ERROR or FATAL"),
                                      (1, [result.replace("tank=3900", "tank=4000")], "Missing, repeated or different"),
                                      (1, [result.replace("accounting=KNOWN", "accounting=UNKNOWN")], "Missing, repeated or different"),
                                      (1, [result.replace("records=0 receipts=1", "records=1 receipts=0")], "Missing, repeated or different"),
                                      (2, [result + " extra"], "Missing, repeated or different"),
                                      (2, [result, "[12:00:01] [Server thread/FATAL] [x/]: failed"], "ERROR or FATAL")):
            with self.subTest(start=start, lines=lines), self.assertRaisesRegex(SmokeError, message):
                runner.observe_loader(plan, start, lines)
        self.assertTrue(plans["LF_S6_AC_PRUNE_AFTER"]["paused"].endswith("cut=LF_S6_AC_PRUNE_AFTER hook=PRUNE_AFTER"))

    def native_run(self, directory, logs):
        """A stand-in for the native session: every start returns a process whose captured lines are `logs[start]`."""
        root = Path(directory).resolve(); (root / "session").mkdir(); (root / "evidence").mkdir()
        run = types.SimpleNamespace(session=root / "session", evidence=root / "evidence", process=None, started=[], stopped=[], captured=[])

        def start(label, world, properties):
            (run.session / world).mkdir(exist_ok=True)
            run.started.append((label, world, properties))
            killed = types.SimpleNamespace(kill=lambda: run.stopped.append("kill " + label))
            run.process = types.SimpleNamespace(lines=logs[len(run.started) - 1], process=killed, abort=lambda: None,
                                                wait_for=lambda pattern, timeout: run.waits.append((pattern.pattern, timeout)))
            return run.process

        def stop(label, audit=None):
            run.stopped.append("stop " + label); run.process = None

        run.waits = []; run.start = start; run.stop = stop
        run.capture = lambda world, label: run.captured.append(label) or dict(file=label)
        return run

    def test_a_new_loader_case_runs_three_starts_with_the_account_oracle_and_keeps_its_raw_states(self):
        case = "LF_S6_AC_GAIN"; plan = runner.spec(case)
        logs = [[plan["paused"]], [plan["result"]], [plan["result"]]]
        disks = iter([dict(ledger=dict(n=index), loaders=[], rocket={}) for index in range(3)])
        checked, progress = [], []
        with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader.oracle, "inspect", lambda world: next(disks)), \
                patch.object(runner.oracle, "check_loader_settlement", lambda disk, cut, start: checked.append((disk["ledger"]["n"], cut, start)) or {}), \
                patch.object(runner.oracle, "check_progress", lambda before, after: progress.append((before["n"], after["n"]))):
            run = self.native_run(directory, logs); row = dict(starts=[], findings=[])
            runner.run_loader_settlement(run, case, plan, row)
            self.assertEqual([], row["findings"])
            self.assertEqual([(case + "-0", "world-" + case, {"escrowScenario": "start", "escrowCase": case}),
                              (case + "-1", "world-" + case, {"escrowScenario": "recover", "escrowCase": case}),
                              (case + "-2", "world-" + case, {"escrowScenario": "recover", "escrowCase": case})], run.started)
            # One forced stop, then two restarts that end with an ordinary stop.
            self.assertEqual(["kill " + case + "-0", "stop " + case + "-1", "stop " + case + "-2"], run.stopped)
            self.assertEqual([(0, case, 0), (1, case, 1), (2, case, 2)], checked)
            self.assertEqual([(0, 1), (1, 2)], progress)
            self.assertEqual([True, None, None], [entry.get("forced_stop") for entry in row["starts"]])
            self.assertEqual([case + f"-{index}-raw.json" for index in range(3)], sorted(p.name for p in run.evidence.iterdir()))
            self.assertTrue(all(pattern.endswith(r"\s*$") and timeout == 420 for pattern, timeout in run.waits))
            self.assertFalse((run.session / ("world-" + case)).exists())

    def test_a_new_loader_case_reports_an_oracle_finding_and_still_runs_the_later_starts(self):
        case = "LF_S6_AC_PRUNE_BEFORE"; plan = runner.spec(case)
        logs = [[plan["paused"]], [plan["result"]], [plan["result"]]]

        def check(disk, cut, start):
            if start == 0: raise SmokeError("Raw source marker count differs: 0, expected 1")
            return {}

        with tempfile.TemporaryDirectory() as directory, patch.object(runner.loader.oracle, "inspect", lambda world: dict(ledger={})), \
                patch.object(runner.oracle, "check_loader_settlement", check), patch.object(runner.oracle, "check_progress", lambda *a: None):
            run = self.native_run(directory, logs); row = dict(starts=[], findings=[])
            runner.run_loader_settlement(run, case, plan, row)
            self.assertEqual(["start 0: Raw source marker count differs: 0, expected 1"], row["findings"])
            self.assertEqual(["FAILED", "PASS", "PASS"], [entry["status"] for entry in row["starts"]])
            self.assertEqual([case + "-0-failed-world"], run.captured)
        # A start that never reaches its marker stops the case and keeps the world.
        with tempfile.TemporaryDirectory() as directory:
            run = self.native_run(directory, logs); row = dict(starts=[], findings=[])
            run.waits = types.SimpleNamespace(append=lambda item: (_ for _ in ()).throw(SmokeError("Timed out waiting for server marker")))
            runner.run_loader_settlement(run, case, plan, row)
            self.assertEqual(1, len(row["starts"])); self.assertIn("Start execution: Timed out", row["findings"][0])
            self.assertEqual([case + "-0-failed-world"], run.captured)

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
