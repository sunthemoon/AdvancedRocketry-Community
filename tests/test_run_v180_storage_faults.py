"""Synthetic results for the X02 storage-fault runner (W379); never launches Java."""
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_dedicated_server_smoke as smoke
import run_v180_crash_replay as x01
import run_v180_storage_faults as faults
import v180_crash_replay as replay

M = faults.MARKER
READY = '[00:00:01] [Server thread/INFO] [minecraft/DedicatedServer]: Done (1.0s)! For help, type "help"'


def live(fault="WRITE_ERROR", count=1, store="BAY_RECEIPTS"):
    return dict(id=f"X02-{store}-{fault}-{count}", store=store, fault=fault, count=count)


def session(fault="WRITE_ERROR", count=1, store="BAY_RECEIPTS", operation="write", moved=False, retry=True,
            retry_digest="aa", unseen=False, reads=0):
    """The first start's log of a fault that the writer survives: count failed writes, then one that moves."""
    lines = [f"[x] [Server thread/INFO]: {M} installed store={store} fault={fault} count={count}"]
    for attempt in range(1, count + 1):
        lines.append(f"{M} injected store={store} fault={fault} operation={operation} attempt={attempt} "
                     f"remaining={count - attempt}")
        if unseen:
            lines.append(f"{M} unseen store={store} fault={fault} operation=move attempt={attempt}")
            lines.append(f"{M} attempt store={store} attempt={attempt} result=moved moved=true faulted=true bytes=9 sha256=aa")
            continue
        result = "open-failed" if operation == "open" else "failed"
        lines.append(f"{M} attempt store={store} attempt={attempt} result={result} moved={str(moved).lower()} "
                     f"faulted=true bytes=9 sha256=aa")
    for _ in range(reads):
        lines.append(f"{M} injected store={store} fault={fault} operation=read attempt=0 remaining=0")
    if retry:
        lines.append(f"{M} attempt store={store} attempt={count + 1} result=moved moved=true faulted=false bytes=9 "
                     f"sha256={retry_digest}")
    return lines


def observed(lines, case=None, restarts=((1, "PASS"), (2, "PASS")), error=None, detected=()):
    return dict(case=case or live(), report=faults.parse_markers(lines), error=error, session_errors=[],
                restarts=[dict(restart=number, status=status, ready=True, detected=list(detected))
                          for number, status in restarts])


def offline(after=("d", "d"), statuses=("FAIL", "FAIL"), detected=("[main/ERROR]: refused",), error="oracle", **extra):
    return dict(case=dict(id="X02-BAY_RECEIPTS-BIT_FLIP", store="BAY_RECEIPTS", fault="BIT_FLIP"), damaged_sha256="d",
                error=error, restarts=[dict(restart=number, status=statuses[number - 1], ready=False,
                                            store_sha256=after[number - 1], detected=list(detected if number == 1 else ()))
                                       for number in (1, 2)], **extra)


class PlanTests(unittest.TestCase):
    def test_the_default_campaign_covers_every_fault_on_the_four_stores(self):
        cases = faults.plan()
        self.assertEqual(len(cases), 4 * (len(faults.LIVE_FAULTS) + 1 + len(faults.OFFLINE_FAULTS)))
        self.assertEqual(len({case.id for case in cases}), len(cases))
        self.assertEqual(set(faults.STORES), {case.store for case in cases})
        self.assertEqual({"F1", "F2", "F3", "F4", "F5", "F6", "F8", "F9"}, set(faults.LIVE_FAULTS.values()))
        self.assertEqual({"F7a", "F7b", "F7c", "F10"}, set(faults.OFFLINE_FAULTS.values()))
        self.assertIn(faults.Case("RESOURCE_ESCROW", "WRITE_ERROR", 4), cases)
        self.assertEqual(faults.Case("BAY_RECEIPTS", "MOVE_DENIED", 1).spec, "BAY_RECEIPTS:MOVE_DENIED:1")

    def test_every_case_names_an_existing_cut_of_an_existing_runner(self):
        for case in faults.plan():
            alias, native = case.native
            self.assertIn(alias, x01.RUNNERS, case.id)
            self.assertIn(native, faults.runner_of(case).CASES, case.id)
        self.assertEqual(set(faults.STORES.values()) - set(replay.SEAM_NAMES), set())

    def test_bay_writes_that_fail_before_the_move_use_the_outcome_of_the_existing_failed_write_case(self):
        before = dict(x01.bay.CASES)
        definite = [case for case in faults.plan(["BAY_RECEIPTS"]) if case.native == ("B", faults.BAY_FAILED_CASE)]
        self.assertEqual({case.fault for case in definite}, set(faults.BAY_DEFINITE))
        for fault in ("MOVE_ERROR", "MOVE_DENIED", "MOVE_AFTER", "READ_ERROR", "READ_CORRUPT"):
            self.assertEqual(faults.Case("BAY_RECEIPTS", fault, 1).native, ("B", "B1_MOVED"))
        self.assertEqual(faults.Case("RESOURCE_ESCROW", "WRITE_ERROR", 1).native, ("F", "FC_LOAD_L1_REPLACED"))
        spec = faults.BayFailedWrite.CASES[faults.BAY_FAILED_CASE]
        self.assertEqual(spec, dict(cut=None, end="land", durable="NONE", first="LANDED_UNCONSUMED",
                                    restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")))
        self.assertEqual({**spec, "staging": True}, x01.bay.CASES["B12_FAILED"])
        self.assertNotIn(faults.BAY_FAILED_CASE, x01.bay.CASES)
        seen = {}

        def run_case(runner, name):
            seen.update(spec=dict(x01.bay.CASES[name]), runner=runner)
            return dict(case=name, status="PASS")

        def run(args, extra_cases):
            return dict(status="PASS", cases=[extra_cases[faults.BAY_FAILED_CASE]("session", faults.BAY_FAILED_CASE)])

        with patch.object(x01.bay, "run", run), patch.object(x01.bay, "run_case", run_case):
            report = faults.BayFailedWrite.run(SimpleNamespace())
        self.assertEqual(report["cases"], [dict(case=faults.BAY_FAILED_CASE, status="PASS")])
        self.assertEqual(seen, dict(spec=spec, runner="session"))
        # The case exists in runner B only while it runs, also when it fails; no case of runner B changed.
        with patch.object(x01.bay, "run", run), patch.object(x01.bay, "run_case", side_effect=smoke.SmokeError("x")):
            with self.assertRaises(smoke.SmokeError):
                faults.BayFailedWrite.run(SimpleNamespace())
        self.assertEqual(x01.bay.CASES, before)

    def test_identifiers_round_trip_and_unknown_ones_are_refused(self):
        for case in faults.plan():
            self.assertEqual(faults.by_id(case.id), case)
        self.assertEqual(faults.by_id("X02-BAY_RECEIPTS-FLUSH_ERROR-16").count, 16)
        for bad in ("X02-BAY_RECEIPTS-FLUSH_ERROR", "X02-BAY_RECEIPTS-FLUSH_ERROR-17", "X02-BAY_RECEIPTS-FLUSH_ERROR-0",
                    "X02-STATIONS-FLUSH_ERROR-1", "X02-BAY_RECEIPTS-STALE-1", "X02-BAY_RECEIPTS-POWER-1", "B1_MOVED"):
            with self.assertRaises(faults.FaultError, msg=bad):
                faults.by_id(bad)

    def test_selections_are_refused_instead_of_sampled(self):
        self.assertEqual([c.id for c in faults.plan(["BAY_RECEIPTS"], ["CLOSE_ERROR", "STALE"], [2])],
                         ["X02-BAY_RECEIPTS-CLOSE_ERROR-2", "X02-BAY_RECEIPTS-STALE"])
        for arguments in ((["STATIONS"], None, None), (None, ["POWER"], None), (None, ["WRITE_ERROR"], [0]),
                          (None, ["WRITE_ERROR"], [17]), (None, ["WRITE_ERROR"], [1, 1]),
                          (None, list(faults.LIVE_FAULTS), list(range(1, 4)))):
            with self.assertRaises(faults.FaultError, msg=str(arguments)):
                faults.plan(*arguments)

    def test_offline_cases_that_share_a_recording_take_the_lock_once(self):
        cases = faults.plan(["BAY_RECEIPTS", "SATELLITE_MISSIONS"], ["WRITE_ERROR", "STALE", "BIT_FLIP"], [1])
        grouped = faults.groups(cases)
        self.assertEqual([[c.id for c in group] for group in grouped], [
            ["X02-BAY_RECEIPTS-WRITE_ERROR-1"],
            ["X02-BAY_RECEIPTS-STALE", "X02-BAY_RECEIPTS-BIT_FLIP", "X02-SATELLITE_MISSIONS-STALE",
             "X02-SATELLITE_MISSIONS-BIT_FLIP"],
            ["X02-SATELLITE_MISSIONS-WRITE_ERROR-1"]])


class MarkerTests(unittest.TestCase):
    def test_the_injector_lines_are_read_from_a_server_log(self):
        report = faults.parse_markers(session(count=2) + [f"{M} closed store=BAY_RECEIPTS fault=WRITE_ERROR injected=2 attempts=3",
                                                          "[x] [Server thread/INFO]: unrelated"])
        self.assertEqual(report["installed"], dict(store="BAY_RECEIPTS", fault="WRITE_ERROR", count=2))
        self.assertEqual([e["attempt"] for e in report["injected"]], [1, 2])
        self.assertEqual(report["injected"][1], dict(operation="write", attempt=2, remaining=0))
        self.assertEqual(report["attempts"][2], dict(attempt=3, result="moved", moved=True, faulted=False, bytes=9, sha256="aa"))
        self.assertEqual(report["closed"], dict(injected=2, attempts=3))
        self.assertEqual((report["unseen"], report["invalid"], report["malformed"]), ([], [], []))

    def test_refusals_and_unreadable_lines_are_kept_apart(self):
        report = faults.parse_markers([f"[x] [Server thread/ERROR]: {M}_INVALID reason=malformed-fault",
                                       f"{M} injected store=BAY_RECEIPTS fault=WRITE_ERROR operation=write",
                                       f"{M} surprise store=BAY_RECEIPTS", f"{M} attempt attempt=x"])
        self.assertEqual(report["invalid"], [f"{M}_INVALID reason=malformed-fault"])
        self.assertEqual(len(report["malformed"]), 3)
        self.assertIsNone(report["installed"])


class LiveClassificationTests(unittest.TestCase):
    def classify(self, *args, **kwargs):
        return faults.classify_live(observed(*args, **kwargs))["classification"]

    def test_a_fault_that_fails_in_the_session_and_passes_both_restarts_is_seen_and_held(self):
        self.assertEqual(self.classify(session()), faults.SEEN_HELD)
        self.assertEqual(self.classify(session(count=4), live(count=4)), faults.SEEN_HELD)
        self.assertEqual(self.classify(session("DISK_FULL_OPEN", operation="open"), live("DISK_FULL_OPEN")), faults.SEEN_HELD)
        self.assertEqual(self.classify(session("MOVE_AFTER", operation="move", moved=True, retry_digest="bb"),
                                       live("MOVE_AFTER")), faults.SEEN_HELD)
        self.assertEqual(self.classify(session("LOST_WRITE"), live("LOST_WRITE")), faults.SEEN_HELD)
        result = faults.classify_live(observed(session()))
        self.assertIn("injected operation=write attempt=1 remaining=0", result["evidence"])
        self.assertIn("restart=2 status=PASS", result["evidence"])

    def test_a_readback_fault_needs_the_failed_read_and_a_retry_of_the_same_bytes(self):
        case = live("READ_ERROR")
        good = session("READ_ERROR", operation="move", moved=True, reads=1)
        self.assertEqual(self.classify(good, case), faults.SEEN_HELD)
        self.assertEqual(self.classify(session("READ_ERROR", operation="move", moved=True, reads=1, retry_digest="bb"), case),
                         faults.NOT_CAUGHT)
        self.assertEqual(self.classify(session("READ_ERROR", operation="move", moved=True, reads=0), case), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(session("READ_ERROR", operation="move", moved=True, reads=1, retry=False), case),
                         faults.NOT_CAUGHT)

    def test_a_missing_installation_or_injection_is_not_a_result(self):
        self.assertEqual(self.classify([]), faults.NOT_RUN)
        self.assertEqual(self.classify(session()[:1]), faults.NOT_RUN)
        self.assertEqual(self.classify(session(store="RESOURCE_ESCROW")), faults.NOT_RUN)
        self.assertEqual(self.classify(session(count=2)), faults.NOT_RUN)
        self.assertEqual(self.classify(session() + [f"{M}_INVALID reason=malformed-count"]), faults.NOT_RUN)
        self.assertEqual(self.classify(session() + [f"{M} surprise"]), faults.NOT_RUN)

    def test_a_case_without_two_restarts_or_with_unused_faults_is_incomplete(self):
        self.assertEqual(self.classify(session(), restarts=(), error="Timed out"), faults.INCOMPLETE)
        self.assertEqual(self.classify(session(), restarts=((1, "PASS"),)), faults.INCOMPLETE)
        self.assertEqual(self.classify(session(count=2), live(count=4)), faults.NOT_RUN)
        partial = session(count=4)
        partial = [line for line in partial if "attempt=3 " not in line and "attempt=4 " not in line and "attempt=5 " not in line]
        self.assertEqual(self.classify(partial, live(count=4)), faults.INCOMPLETE)

    def test_a_seen_fault_with_a_failed_oracle_is_a_residual_and_never_held(self):
        self.assertEqual(self.classify(session(), restarts=((1, "PASS"), (2, "FAIL"))), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(session(), error="Existing runner did not pass"), faults.NOT_CAUGHT)
        # Errors in a later log do not turn a seen fault with a failed oracle into a hold.
        result = faults.classify_live(observed(session(), restarts=((1, "FAIL"), (2, "FAIL")), error="oracle",
                                               detected=["[Server thread/ERROR]: receipts unreadable"]))
        self.assertEqual(result["classification"], faults.NOT_CAUGHT)
        self.assertIn("[Server thread/ERROR]: receipts unreadable", result["evidence"])

    def test_a_write_that_did_not_fail_in_the_session_is_never_seen_and_held(self):
        case = live("LOST_WRITE")
        unseen = session("LOST_WRITE", unseen=True, retry=False)
        self.assertEqual(self.classify(unseen, case), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(unseen, case, restarts=((1, "FAIL"), (2, "FAIL")), error="oracle",
                                       detected=["[main/ERROR]: receipts refused"]), faults.CAUGHT_LATER)
        self.assertEqual(self.classify(unseen, case, restarts=((1, "FAIL"), (2, "FAIL")), error="oracle"), faults.NOT_CAUGHT)
        # A faulted write without an end line, and a move flag against the fault's definition.
        self.assertEqual(self.classify([line for line in session() if "result=failed" not in line]), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(session(moved=True)), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(session("MOVE_AFTER", operation="move", moved=False), live("MOVE_AFTER")),
                         faults.NOT_CAUGHT)

    def test_every_completed_case_has_exactly_one_of_the_three_classes(self):
        for lines, kwargs in ((session(), {}), (session(), dict(error="x")),
                              (session("LOST_WRITE", unseen=True), dict(case=live("LOST_WRITE")))):
            self.assertIn(faults.classify_live(observed(lines, **kwargs))["classification"], faults.CLASSES)


class OfflineClassificationTests(unittest.TestCase):
    def classify(self, **kwargs):
        return faults.classify_offline(offline(**kwargs))["classification"]

    def test_a_reported_file_that_stays_byte_identical_is_caught_later(self):
        result = faults.classify_offline(offline())
        self.assertEqual(result["classification"], faults.CAUGHT_LATER)
        self.assertIn("damaged sha256=d", result["evidence"])
        self.assertIn("[main/ERROR]: refused", result["evidence"])

    def test_an_accepted_or_replaced_file_is_not_caught(self):
        self.assertEqual(self.classify(detected=()), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(detected=(), statuses=("PASS", "PASS"), error=None), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(detected=(), after=("d", "fresh")), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(after=("fresh", "fresh")), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(after=("d", None)), faults.NOT_CAUGHT)

    def test_a_reported_repair_counts_only_when_the_existing_oracle_passes_twice(self):
        self.assertEqual(self.classify(after=("r", "r"), statuses=("PASS", "PASS"), error=None), faults.CAUGHT_LATER)
        self.assertEqual(self.classify(after=("r", "r"), statuses=("PASS", "FAIL"), error=None), faults.NOT_CAUGHT)
        self.assertEqual(self.classify(after=("r", "r"), statuses=("PASS", "PASS"), error="pin"), faults.NOT_CAUGHT)

    def test_a_copy_that_was_not_started_twice_has_no_class(self):
        one = offline()
        one["restarts"].pop()
        self.assertEqual(faults.classify_offline(one)["classification"], faults.INCOMPLETE)
        self.assertEqual(faults.classify_offline(dict(case=offline()["case"], restarts=[], not_applicable="no earlier write")),
                         dict(case="X02-BAY_RECEIPTS-BIT_FLIP", classification=faults.NOT_APPLICABLE,
                              reasons=["no earlier write"], evidence=[]))
        self.assertNotIn(faults.SEEN_HELD, {faults.classify_offline(offline(after=a, detected=d))["classification"]
                                            for a in (("d", "d"), ("r", "r")) for d in ((), ("e",))})


class DamageTests(unittest.TestCase):
    DATA = bytes(range(256)) * 40

    def test_each_offline_fault_changes_the_file_as_the_design_says(self):
        data = self.DATA
        self.assertEqual(faults.damage("ZERO_LENGTH", data, None, False), (b"", None))
        tail, _ = faults.damage("ZEROED_TAIL", data, None, False)
        self.assertEqual((len(tail), tail[:-4096], tail[-4096:]), (len(data), data[:-4096], bytes(4096)))
        flipped, _ = faults.damage("BIT_FLIP", data, None, False)
        self.assertEqual([i for i in range(len(data)) if flipped[i] != data[i]], [len(data) // 2])
        self.assertEqual(flipped[len(data) // 2] ^ data[len(data) // 2], 1)
        self.assertEqual(faults.damage("TRUNCATED", data, None, False), (data[:len(data) // 2], None))
        self.assertEqual(faults.damage("STALE", data, b"earlier", True), (b"earlier", None))
        short, _ = faults.damage("ZEROED_TAIL", b"abcdef", None, False)
        self.assertEqual(short, b"abc\0\0\0")

    def test_a_case_the_recording_cannot_carry_is_refused_with_a_reason(self):
        for arguments in (("STALE", self.DATA, None, False), ("STALE", self.DATA, None, True),
                          ("STALE", self.DATA, self.DATA, True), ("BIT_FLIP", None, None, False),
                          ("TRUNCATED", b"x", None, False)):
            content, reason = faults.damage(*arguments)
            self.assertIsNone(content, arguments[0])
            self.assertTrue(reason)
        with self.assertRaises(faults.FaultError):
            faults.damage("POWER", self.DATA, None, False)

    def test_the_earlier_generation_is_the_content_before_the_last_completed_write(self):
        writes = [replay.Write(0, "t", "BAY_RECEIPTS", "data/a", None, b"one", complete=True),
                  replay.Write(7, "t", "BAY_RECEIPTS", "data/a", b"one", b"two", complete=True),
                  replay.Write(14, "t", "BAY_RECEIPTS", "data/a", b"two", b"three", complete=False),
                  replay.Write(21, "u", "RESOURCE_ESCROW", "data/b", None, b"x", complete=False)]
        self.assertEqual(faults.earlier_generation(writes, "BAY_RECEIPTS"), (b"one", True))
        self.assertEqual(faults.earlier_generation(writes, "RESOURCE_ESCROW"), (None, False))


class HookTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_the_property_reaches_only_the_first_start_and_the_hooks_are_put_back(self):
        command_before, process_before = replay.trace_command, smoke.CapturedProcess
        args = SimpleNamespace(storage_fault="BAY_RECEIPTS:WRITE_ERROR:1", replay_commands=[])
        plain = SimpleNamespace(replay_commands=[])
        with faults.hooks():
            self.assertIsNot(replay.trace_command, command_before)
            commands = [["java", "-Xmx4G", "@args"] for _ in range(3)]
            for restart, command in enumerate(commands):
                replay.trace_command(args, command, restart)
            untouched = ["java", "@args"]
            replay.trace_command(plain, untouched, 0)
        self.assertEqual(commands[0], ["java", faults.PROPERTY + "BAY_RECEIPTS:WRITE_ERROR:1", "-Xmx4G", "@args"])
        self.assertEqual(commands[1:], [["java", "-Xmx4G", "@args"]] * 2)
        self.assertEqual(untouched, ["java", "@args"])
        # The existing recorder of commands still sees every start, with the property on the first.
        self.assertEqual([entry["restart"] for entry in args.replay_commands], [0, 1, 2])
        self.assertIn(faults.PROPERTY + "BAY_RECEIPTS:WRITE_ERROR:1", args.replay_commands[0]["command"])
        self.assertIs(replay.trace_command, command_before)
        self.assertIs(smoke.CapturedProcess, process_before)
        with self.assertRaises(RuntimeError):
            with faults.hooks():
                raise RuntimeError("runner failed")
        self.assertIs(replay.trace_command, command_before)
        self.assertIs(smoke.CapturedProcess, process_before)

    def test_the_observer_runs_once_after_each_process_has_ended(self):
        seen = []
        world = self.root / "world-CASE" / "data"
        world.mkdir(parents=True)
        (world / "store.dat").write_bytes(b"after")
        with faults.hooks(lambda cwd, log: seen.append((faults.start_number(log), faults.sha(cwd / "world-CASE" / "data" / "store.dat")))):
            for restart, ending in ((1, "finish"), (2, "abort")):
                process = smoke.CapturedProcess([sys.executable, "-c", "print('ready')"], self.root,
                                                self.root / f"CASE-{restart}.log")
                if ending == "finish":
                    self.assertEqual(process.finish(30), 0)
                process.abort()
                process.abort()
                process.process.stdin.close(); process.process.stdout.close()
        self.assertEqual(seen, [(1, replay.sha(b"after")), (2, replay.sha(b"after"))])
        self.assertIsNone(faults.sha(self.root / "absent"))
        self.assertIsNone(faults.start_number(Path("server.log")))


class RunnerTests(unittest.TestCase):
    """The campaign around a stand-in for an existing runner: evidence, classes and cleanup, with no server."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.scratch = self.root / "Temp"; self.scratch.mkdir()
        self.args = SimpleNamespace(source_server=self.root, jar=self.root / "mod.jar", java="java", source_commit="s" * 40,
                                    candidate_commit="c" * 40, jar_sha256="j" * 64, startup_timeout=1, exit_timeout=1,
                                    flight_timeout=1, settle_timeout=1)
        patcher = patch.object(faults, "TEMP", self.scratch); patcher.start(); self.addCleanup(patcher.stop)

    def stand_in(self, first, restarts, fail=None):
        """A runner whose run() writes logs and results as the existing ones do, through the hooked start path."""
        calls = []

        def run(arguments):
            arguments.evidence_dir.mkdir(parents=True)
            case = arguments.case[0]
            command = ["java", "@args"]
            replay.trace_command(arguments, command, 0)
            calls.append(command)
            (arguments.evidence_dir / f"{case}-0.log").write_text("\n".join(first) + "\n", encoding="utf-8")
            listed = []
            for number, lines in restarts.items():
                replay.trace_command(arguments, ["java", "@args"], number)
                (arguments.evidence_dir / f"{case}-{number}.log").write_text("\n".join(lines) + "\n", encoding="utf-8")
                if fail != number:
                    listed.append(dict(restart=number))
            report = dict(status="FAILED" if fail else "PASS", cases=[dict(case=case, restarts=listed)])
            (arguments.evidence_dir / "results.json").write_text(json.dumps(report), encoding="utf-8")
            if fail:
                raise smoke.SmokeError(f"{case}/{fail}: the restart failed")
            return report

        return SimpleNamespace(run=run, calls=calls)

    def live_case(self, module, case):
        evidence = self.root / "evidence" / "cases" / case.id
        with patch.object(faults, "runner_of", lambda selected: module), patch.object(x01.bay, "assert_pin"), \
                patch.object(x01.evidence_tools, "candidate_pin"):
            row = faults.run_live(self.args, case, evidence, self.root / "work", "owner")
        return row, evidence

    def test_a_live_case_keeps_its_evidence_and_is_classified_from_the_logs(self):
        case = faults.Case("BAY_RECEIPTS", "WRITE_ERROR", 1)
        module = self.stand_in(session(), {1: [READY], 2: [READY]})
        row, evidence = self.live_case(module, case)
        self.assertEqual(module.calls[0], ["java", faults.PROPERTY + "BAY_RECEIPTS:WRITE_ERROR:1", "@args"])
        self.assertEqual((row["classification"], row["design"], row["native"]), (faults.SEEN_HELD, "F1", "B/X02_D1_FAILED"))
        observation = json.loads((evidence / "observation.json").read_text(encoding="utf-8"))
        self.assertEqual([r["status"] for r in observation["restarts"]], ["PASS", "PASS"])
        self.assertTrue(all(r["ready"] for r in observation["restarts"]))
        self.assertEqual(json.loads((evidence / "classification.json").read_text(encoding="utf-8"))["classification"],
                         faults.SEEN_HELD)
        commands = json.loads((evidence / "commands.json").read_text(encoding="utf-8"))
        self.assertEqual([c["restart"] for c in commands], [0, 1, 2])
        self.assertEqual(sorted(p.name for p in (evidence / "logs").iterdir()),
                         ["live-X02_D1_FAILED-0.log.gz", "live-X02_D1_FAILED-1.log.gz", "live-X02_D1_FAILED-2.log.gz"])
        self.assertTrue((evidence / "runner-results.json").is_file())
        results = (evidence.parent.parent / "results.jsonl").read_text(encoding="utf-8").splitlines()
        self.assertEqual(json.loads(results[0])["case"], case.id)

    def test_a_failed_existing_oracle_is_never_reported_as_held(self):
        case = faults.Case("BAY_RECEIPTS", "WRITE_ERROR", 1)
        module = self.stand_in(session(), {1: [READY], 2: ["[Server thread/ERROR]: lost receipt"]}, fail=2)
        row, evidence = self.live_case(module, case)
        self.assertEqual(row["classification"], faults.NOT_CAUGHT)
        observation = json.loads((evidence / "observation.json").read_text(encoding="utf-8"))
        self.assertEqual([r["status"] for r in observation["restarts"]], ["PASS", "FAIL"])
        self.assertEqual(observation["restarts"][1]["detected"], ["[Server thread/ERROR]: lost receipt"])
        self.assertIn("X02_D1_FAILED/2: the restart failed", observation["error"])

    def test_a_first_start_that_never_restarts_is_incomplete(self):
        case = faults.Case("BAY_RECEIPTS", "WRITE_ERROR", 4)
        module = self.stand_in(session(count=4, retry=False), {}, fail=1)
        row, _ = self.live_case(module, case)
        self.assertEqual(row["classification"], faults.INCOMPLETE)

    def test_the_summary_counts_each_class_and_lists_residuals_and_the_uncovered_scope(self):
        rows = [dict(case="X02-A", store="BAY_RECEIPTS", fault="WRITE_ERROR", design="F1", native="B/B1_MOVED",
                     classification=faults.SEEN_HELD, reasons=["held"]),
                dict(case="X02-B", store="RESOURCE_ESCROW", fault="STALE", design="F7a", native="F/X",
                     classification=faults.NOT_CAUGHT, reasons=["accepted | stale"])]
        self.assertEqual(faults.status_of(rows), "COMPLETE_WITH_RESIDUALS")
        self.assertEqual(faults.status_of(rows[:1]), "COMPLETE")
        self.assertEqual(faults.status_of(rows + [dict(classification=faults.INCOMPLETE)]), "INCOMPLETE")
        self.assertEqual(faults.status_of([dict(classification=faults.NOT_APPLICABLE)]), "COMPLETE")
        text = faults.summary(dict(status="COMPLETE_WITH_RESIDUALS", results=rows, uncovered=faults.UNCOVERED))
        self.assertIn("| SEEN_HELD | 1 |", text)
        self.assertIn("| NOT_CAUGHT | 1 |", text)
        self.assertIn("- X02-B (RESOURCE_ESCROW): accepted | stale", text)
        self.assertIn("| X02-B | F7a | F/X | NOT_CAUGHT | accepted / stale |", text)
        for item in faults.UNCOVERED:
            self.assertIn("- " + item, text)

    def test_the_lock_is_released_only_by_its_owner_and_a_stale_lock_is_not_broken(self):
        lock = self.root / "heavy-jvm"
        with faults.machine_lock(lock) as owner:
            self.assertEqual((lock / "owner.txt").read_text(encoding="utf-8"), owner)
            self.assertTrue(owner.startswith("W379-X02 pid="))
        self.assertFalse(lock.exists())
        with faults.machine_lock(lock):
            (lock / "owner.txt").write_text("another worker", encoding="utf-8")
        self.assertTrue(lock.exists())
        with patch.object(faults.time, "sleep"), self.assertRaises(faults.FaultError):
            with faults.machine_lock(lock, timeout=0):
                self.fail("A foreign lock was taken")
        self.assertEqual((lock / "owner.txt").read_text(encoding="utf-8"), "another worker")

    def test_the_evidence_bound_stops_the_campaign(self):
        evidence = self.root / "bounded"; evidence.mkdir()
        (evidence / "a.log").write_bytes(b"x" * 10)
        faults.check_size(evidence)
        with patch.object(faults, "LIMIT", 5), self.assertRaises(replay.OverBound):
            faults.check_size(evidence)


if __name__ == "__main__":
    unittest.main()
