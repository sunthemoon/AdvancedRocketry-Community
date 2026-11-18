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
        for fault in ("MOVE_ERROR", "MOVE_DENIED", "READ_ERROR", "READ_CORRUPT"):
            self.assertEqual(faults.Case("BAY_RECEIPTS", fault, 1).native, ("B", "B1_MOVED"))
        # W379F smoke 1: a move that fails after the rename commits the INTENT write without a retry, and the cut then
        # pauses the REGISTERED write. That is the kill image of the existing cut B3_MOVED, not of B1_MOVED.
        self.assertEqual(faults.Case("BAY_RECEIPTS", "MOVE_AFTER", 1).native, ("B", "B3_MOVED"))
        self.assertEqual(x01.bay.CASES["B3_MOVED"], dict(cut="B3_MOVED", end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED")))
        self.assertEqual({case.fault for case in faults.plan(["BAY_RECEIPTS"]) if case.native == ("B", "B3_MOVED") and not case.offline},
                         {"MOVE_AFTER"})
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
        self.assertEqual(self.classify(session(), restarts=((1, "FAIL"), (2, "PASS")), error="oracle"), faults.NOT_CAUGHT)
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

    def test_a_failed_check_of_the_faulted_start_is_neither_held_nor_a_residual(self):
        # W379 smoke 2, the journal: the hold is logged at ERROR, the runner refuses any ERROR line on the first start,
        # and both healthy restarts pass. The existing check is not relaxed, and the hold is not called a residual.
        hold = "[Server thread/ERROR]: ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority"
        observation = observed(session(store="ROCKET_TRANSFERS"), live(store="ROCKET_TRANSFERS"),
                               error="SmokeError: First contradictory cut: FS_O3J_AFTER: start 0: Native run logged ERROR or FATAL")
        observation["session_errors"] = [hold]
        result = faults.classify_live(observation)
        self.assertEqual(result["classification"], faults.NEEDS_REVIEW)
        self.assertIn("Native run logged ERROR or FATAL", result["reasons"][-1])
        self.assertIn(hold, result["evidence"])
        self.assertNotIn(faults.NEEDS_REVIEW, faults.CLASSES)
        # It needs a seen fault and two passing restarts; anything less keeps its class.
        self.assertEqual(self.classify(session("LOST_WRITE", unseen=True, retry=False), live("LOST_WRITE"), error="x"),
                         faults.NOT_CAUGHT)
        self.assertEqual(self.classify(session(), restarts=((1, "PASS"), (2, "FAIL")), error="x"), faults.NOT_CAUGHT)

    def test_a_case_without_a_failed_check_has_exactly_one_of_the_three_classes(self):
        for lines, kwargs in ((session(), {}), (session(), dict(restarts=((1, "FAIL"), (2, "FAIL")), error="x")),
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
        self.assertEqual(faults.status_of(rows[:1] + [dict(classification=faults.NEEDS_REVIEW)]), "INCOMPLETE")
        self.assertIn("| NEEDS_REVIEW | 0 |", faults.summary(dict(status="COMPLETE", results=rows[:1], uncovered=[])))
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


JOURNAL_HOLD = "[09:00:02] [Server thread/ERROR] [x/]: ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED retained dirty authority"
REGISTRY_HOLD = "[09:00:02] [Server thread/ERROR] [x/]: ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=FAILED sequence=7"
REFUSED = "Native run logged ERROR or FATAL"


def skipped(skip, count=1, store="SATELLITE_MISSIONS", fault="WRITE_ERROR", installed=True, passed=None, first=None):
    """The first start's log of a fault behind `skip` passed writes: the passed writes, the failed ones, one that moves."""
    lines = [f"[x] [Server thread/INFO]: {M} installed store={store} fault={fault} count={count}"
             + (f" skip={skip}" if installed else "")]
    for attempt in (range(1, skip + 1) if passed is None else passed):
        lines.append(f"{M} passed store={store} fault={fault} attempt={attempt} skip={skip} left={skip - attempt}")
        lines.append(f"{M} attempt store={store} attempt={attempt} result=moved moved=true faulted=false bytes=9 sha256=aa")
    first = skip + 1 if first is None else first
    for attempt in range(first, first + count):
        lines.append(f"{M} injected store={store} fault={fault} operation=write attempt={attempt} "
                     f"remaining={first + count - 1 - attempt} skip={skip}")
        lines.append(f"{M} attempt store={store} attempt={attempt} result=failed moved=false faulted=true bytes=0 sha256=bb")
    lines.append(f"{M} attempt store={store} attempt={first + count} result=moved moved=true faulted=false bytes=9 sha256=aa")
    return lines


def refused_start(accepted=(JOURNAL_HOLD,), unaccepted=(), filtered=None, findings=1, status="FAILED"):
    """The two results for a faulted start that the existing runner refused."""
    return dict(declared=list(faults.HOLD_MARKERS["ROCKET_TRANSFERS"]),
                runner=dict(status=status, error=REFUSED, findings=findings), accepted=list(accepted),
                unaccepted=list(unaccepted), unaccepted_count=len(unaccepted),
                filtered=filtered or dict(status="PASS", checks=["log observations", "disk oracle of the cut"]))


class SkipTests(unittest.TestCase):
    """Root decision R486, answer 2: the optional fourth field of the property."""

    def test_the_registry_cases_skip_the_runners_baseline_write_and_no_other_store_skips(self):
        cases = faults.plan()
        live = [case for case in cases if not case.offline]
        self.assertEqual({case.store for case in live if case.skip}, {"SATELLITE_MISSIONS"})
        self.assertEqual({case.skip for case in live if case.store == "SATELLITE_MISSIONS"}, {1})
        self.assertTrue(all(case.skip == 0 for case in cases if case.offline))
        registry = faults.Case("SATELLITE_MISSIONS", "WRITE_ERROR", 1, 1)
        self.assertIn(registry, cases)
        # The planned skip is not part of the identifier, and it is the fourth field of the property.
        self.assertEqual((registry.id, registry.spec, registry.native),
                         ("X02-SATELLITE_MISSIONS-WRITE_ERROR-1", "SATELLITE_MISSIONS:WRITE_ERROR:1:1", ("R", "R3")))
        self.assertEqual(registry.view(), dict(id=registry.id, store="SATELLITE_MISSIONS", fault="WRITE_ERROR", count=1, skip=1))
        # Without a skip the property is the three-field form of W379, unchanged.
        for case in live:
            self.assertEqual(case.spec.count(":"), 3 if case.skip else 2, case.id)
        self.assertEqual(faults.Case("ROCKET_TRANSFERS", "WRITE_ERROR", 4).view(),
                         dict(id="X02-ROCKET_TRANSFERS-WRITE_ERROR-4", store="ROCKET_TRANSFERS", fault="WRITE_ERROR", count=4))
        self.assertIn("baseline request", faults.DEFAULT_SKIP["SATELLITE_MISSIONS"][1])
        self.assertEqual(set(faults.DEFAULT_SKIP), {"SATELLITE_MISSIONS"})

    def test_another_skip_is_named_in_the_identifier(self):
        case = faults.by_id("X02-BAY_RECEIPTS-MOVE_ERROR-1-S2")
        self.assertEqual((case, case.spec, case.id), (faults.Case("BAY_RECEIPTS", "MOVE_ERROR", 1, 2), "BAY_RECEIPTS:MOVE_ERROR:1:2",
                                                      "X02-BAY_RECEIPTS-MOVE_ERROR-1-S2"))
        first = faults.by_id("X02-SATELLITE_MISSIONS-WRITE_ERROR-1-S0")
        self.assertEqual((first.skip, first.spec), (0, "SATELLITE_MISSIONS:WRITE_ERROR:1"))
        self.assertEqual(faults.by_id("X02-RESOURCE_ESCROW-FLUSH_ERROR-16-S48").skip, faults.MAX_SKIP)
        for case in (case, first):
            self.assertEqual(faults.by_id(case.id), case)
        # One identifier for each case, a bounded skip, and no skip for an offline fault.
        for bad in ("X02-SATELLITE_MISSIONS-WRITE_ERROR-1-S1", "X02-BAY_RECEIPTS-MOVE_ERROR-1-S0", "X02-BAY_RECEIPTS-MOVE_ERROR-1-S49",
                    "X02-BAY_RECEIPTS-MOVE_ERROR-1-S01", "X02-BAY_RECEIPTS-MOVE_ERROR-1-S", "X02-BAY_RECEIPTS-MOVE_ERROR-S2",
                    "X02-BAY_RECEIPTS-STALE-S1", "X02-BAY_RECEIPTS-MOVE_ERROR-1-s2"):
            with self.assertRaises(faults.FaultError, msg=bad):
                faults.by_id(bad)

    def test_the_skip_lines_are_read_and_the_three_field_lines_parse_as_before(self):
        report = faults.parse_markers(skipped(2, count=2) + [f"{M} closed store=SATELLITE_MISSIONS fault=WRITE_ERROR injected=2 "
                                                             "attempts=5 skip=2"])
        self.assertEqual(report["installed"], dict(store="SATELLITE_MISSIONS", fault="WRITE_ERROR", count=2, skip=2))
        self.assertEqual(report["passed"], [dict(attempt=1, left=1), dict(attempt=2, left=0)])
        self.assertEqual(report["injected"], [dict(operation="write", attempt=3, remaining=1), dict(operation="write", attempt=4, remaining=0)])
        self.assertEqual([entry["attempt"] for entry in report["attempts"]], [1, 2, 3, 4, 5])
        self.assertEqual(report["closed"], dict(injected=2, attempts=5, skip=2))
        self.assertEqual(report["malformed"], [])
        plain = faults.parse_markers(session())
        self.assertEqual((plain["installed"], plain["passed"]), (dict(store="BAY_RECEIPTS", fault="WRITE_ERROR", count=1), []))
        self.assertEqual(len(faults.parse_markers([f"{M} passed store=BAY_RECEIPTS attempt=x"])["malformed"]), 1)

    def classify(self, lines, skip=1, count=1, **kwargs):
        case = dict(id="X02-SATELLITE_MISSIONS-WRITE_ERROR-1", store="SATELLITE_MISSIONS", fault="WRITE_ERROR", count=count)
        return faults.classify_live(observed(lines, case | (dict(skip=skip) if skip else {}), **kwargs))

    def test_a_fault_behind_the_skipped_writes_is_classified_as_any_other(self):
        result = self.classify(skipped(1))
        self.assertEqual(result["classification"], faults.SEEN_HELD)
        self.assertEqual(result["evidence"][:2], ["passed attempt=1 skip=1", "injected operation=write attempt=2 remaining=0"])
        self.assertEqual(self.classify(skipped(3, count=4), skip=3, count=4)["classification"], faults.SEEN_HELD)
        self.assertEqual(self.classify(skipped(1), restarts=((1, "PASS"), (2, "FAIL")))["classification"], faults.NOT_CAUGHT)
        self.assertEqual(self.classify(skipped(1), restarts=(), error="Timed out")["classification"], faults.INCOMPLETE)

    def test_a_skip_that_the_injector_did_not_report_as_planned_is_not_a_result(self):
        # The installation without the skip, or with another one.
        self.assertEqual(self.classify(skipped(1, installed=False))["classification"], faults.NOT_RUN)
        self.assertEqual(self.classify(skipped(2), skip=1)["classification"], faults.NOT_RUN)
        self.assertEqual(self.classify(session(store="SATELLITE_MISSIONS"), skip=1)["classification"], faults.NOT_RUN)
        # A skip the case did not ask for.
        self.assertEqual(self.classify(skipped(1), skip=0)["classification"], faults.NOT_RUN)
        # The store wrote less often than the skip: nothing was injected.
        short = self.classify(skipped(2)[:3], skip=2)
        self.assertEqual(short["classification"], faults.NOT_RUN)
        self.assertIn("1 of 2 skipped writes happened", short["reasons"][0])
        # Passed writes that are not the first ones, a missing passed line, and a fault on a skipped write.
        self.assertEqual(self.classify(skipped(2, passed=(2, 3)), skip=2)["classification"], faults.NOT_RUN)
        self.assertEqual(self.classify(skipped(2, passed=(1,)), skip=2)["classification"], faults.NOT_RUN)
        self.assertEqual(self.classify(skipped(2, first=2), skip=2)["classification"], faults.NOT_RUN)


class NotApplicableTests(unittest.TestCase):
    """Root decision R486, answer 3: a case that cannot complete by construction is declared before the run."""

    def test_only_the_bay_case_without_a_second_receipt_write_is_declared(self):
        declared = [case for case in faults.plan() if case.not_applicable]
        self.assertEqual([case.id for case in declared], ["X02-BAY_RECEIPTS-WRITE_ERROR-4"])
        self.assertIn("no second receipt write follows", declared[0].not_applicable)
        # A count of 4 stays where the store's writer retries, and where the bay writer retries the exact candidate.
        for store in ("RESOURCE_ESCROW", "ROCKET_TRANSFERS", "SATELLITE_MISSIONS"):
            self.assertIsNone(faults.Case(store, "WRITE_ERROR", 4, faults.default_skip(store)).not_applicable, store)
        self.assertIsNone(faults.Case("BAY_RECEIPTS", "MOVE_ERROR", 4).not_applicable)
        self.assertIsNone(faults.Case("BAY_RECEIPTS", "WRITE_ERROR", 1).not_applicable)
        self.assertIsNone(faults.Case("BAY_RECEIPTS", "STALE").not_applicable)
        # A skip leaves the first receipt write healthy, and the failed-write outcome then never happens.
        self.assertTrue(faults.Case("BAY_RECEIPTS", "FLUSH_ERROR", 1, 1).not_applicable)

    def test_the_declarations_name_exactly_the_two_hold_markers_the_skip_and_the_declared_case(self):
        declared = faults.declarations(faults.plan())
        self.assertEqual(declared["hold_markers"], {"ROCKET_TRANSFERS": ["ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED"],
                                                    "SATELLITE_MISSIONS": ["ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED"]})
        self.assertIn("only after an ARCE_RELEASE_STORAGE_FAULT injected line", declared["hold_rule"])
        self.assertEqual(len(declared["skips"]), 1)
        skip = declared["skips"][0]
        self.assertEqual((skip["store"], skip["skip"], len(skip["cases"])), ("SATELLITE_MISSIONS", 1, len(faults.LIVE_FAULTS) + 1))
        self.assertEqual(skip["reason"], faults.DEFAULT_SKIP["SATELLITE_MISSIONS"][1])
        self.assertEqual(declared["not_applicable"], [dict(case="X02-BAY_RECEIPTS-WRITE_ERROR-4",
                                                           reason=faults.Case("BAY_RECEIPTS", "WRITE_ERROR", 4).not_applicable)])
        named = faults.declarations([faults.by_id("X02-BAY_RECEIPTS-MOVE_ERROR-1-S2")])
        self.assertEqual((named["skips"][0]["skip"], named["skips"][0]["reason"]), (2, "Named in the case identifier by the caller."))
        # A registry skip other than the planned one does not borrow the planned one's reason.
        other = faults.declarations([faults.by_id("X02-SATELLITE_MISSIONS-WRITE_ERROR-1-S3"), faults.by_id("X02-SATELLITE_MISSIONS-WRITE_ERROR-1")])
        self.assertEqual([(entry["skip"], entry["reason"].split(" ")[0]) for entry in other["skips"]], [(1, "Runner"), (3, "Named")])
        self.assertEqual(faults.declarations([faults.Case("BAY_RECEIPTS", "STALE")])["skips"], [])

    def test_a_declared_case_is_recorded_without_a_start_and_listed_as_uncovered_scope(self):
        with tempfile.TemporaryDirectory() as temp:
            case = faults.Case("BAY_RECEIPTS", "WRITE_ERROR", 4)
            evidence = Path(temp) / "evidence" / "cases" / case.id
            row = faults.declared_row(case, evidence)
            self.assertEqual((row["classification"], row["reasons"]), (faults.NOT_APPLICABLE, [case.not_applicable]))
            observation = json.loads((evidence / "observation.json").read_text(encoding="utf-8"))
            self.assertEqual((observation["declared_before_run"], observation["restarts"]), (True, []))
            self.assertEqual(json.loads((evidence / "commands.json").read_text(encoding="utf-8")), [])
        held = dict(case="X02-A", store="BAY_RECEIPTS", fault="WRITE_ERROR", design="F1", native="B/X02_D1_FAILED",
                    classification=faults.SEEN_HELD, reasons=["held"])
        late = dict(case="X02-C", store="BAY_RECEIPTS", fault="STALE", design="F7a", native="B/B3_MOVED",
                    classification=faults.NOT_APPLICABLE, reasons=["no earlier write"])
        manifest = dict(status="COMPLETE", results=[row, held, late], uncovered=["scope"], **faults.declarations([case]))
        # The status rule is the one of W379: a case without an outcome by construction does not make it INCOMPLETE.
        self.assertEqual(faults.status_of(manifest["results"]), "COMPLETE")
        self.assertEqual(faults.status_of(manifest["results"] + [dict(classification=faults.NEEDS_REVIEW)]), "INCOMPLETE")
        text = faults.summary(manifest)
        uncovered = text.split("## Uncovered", 1)[1]
        self.assertIn("- scope", uncovered)
        self.assertIn(f"- {case.id}: NOT_APPLICABLE, declared before the run. {case.not_applicable}", uncovered)
        self.assertIn("- X02-C: NOT_APPLICABLE. no earlier write", uncovered)
        self.assertIn("| NOT_APPLICABLE | 2 |", text)
        self.assertIn("- ROCKET_TRANSFERS: ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED", text)
        self.assertNotIn("## Skipped writes", text)
        self.assertIn("- SATELLITE_MISSIONS: the first 1 write(s) of the store pass before the faulted ones. Runner R",
                      faults.summary(dict(status="COMPLETE", results=[], uncovered=[], **faults.declarations(faults.plan()))))


class HoldLineTests(unittest.TestCase):
    """Root decision R486, answer 1: declared hold lines, in the faulted start only."""
    INJECTED = f"[09:00:01] [ARCE-rocket-journal/INFO] [x/]: {M} injected store=ROCKET_TRANSFERS fault=WRITE_ERROR operation=write attempt=1 remaining=0"

    def test_a_declared_line_is_accepted_only_at_error_for_its_store_and_after_the_injection(self):
        other = "[09:00:03] [Server thread/ERROR] [x/]: something else failed"
        fatal = "[09:00:03] [Server thread/FATAL] [x/]: ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED"
        plain = "[09:00:04] [Server thread/INFO] [x/]: ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED mentioned at INFO"
        lines = [JOURNAL_HOLD, self.INJECTED, JOURNAL_HOLD, REGISTRY_HOLD, other, fatal, plain, JOURNAL_HOLD]
        accepted, unaccepted, kept = faults.hold_lines(lines, "ROCKET_TRANSFERS")
        self.assertEqual(accepted, [JOURNAL_HOLD, JOURNAL_HOLD])
        # The line before the injection, the other store's marker, the unrelated line and the FATAL line stay.
        self.assertEqual(unaccepted, [JOURNAL_HOLD, REGISTRY_HOLD, other, fatal])
        self.assertEqual(kept, [JOURNAL_HOLD, self.INJECTED, REGISTRY_HOLD, other, fatal, plain])
        # Another store's injection opens nothing, and a store without declared markers accepts nothing.
        self.assertEqual(faults.hold_lines([self.INJECTED, REGISTRY_HOLD], "SATELLITE_MISSIONS")[0], [])
        self.assertEqual(faults.hold_lines([self.INJECTED.replace("ROCKET_TRANSFERS", "SATELLITE_MISSIONS"), REGISTRY_HOLD],
                                           "SATELLITE_MISSIONS")[0], [REGISTRY_HOLD])
        for store in ("BAY_RECEIPTS", "RESOURCE_ESCROW"):
            injected = self.INJECTED.replace("ROCKET_TRANSFERS", store)
            self.assertEqual(faults.hold_lines([injected, JOURNAL_HOLD, REGISTRY_HOLD], store)[0], [], store)
        # An ERROR line that only quotes an injection does not open the acceptance.
        quoted = self.INJECTED.replace("/INFO]", "/ERROR]")
        self.assertEqual(faults.hold_lines([quoted, JOURNAL_HOLD], "ROCKET_TRANSFERS")[:2], ([], [quoted, JOURNAL_HOLD]))
        self.assertEqual(sorted(faults.HOLD_MARKERS), ["ROCKET_TRANSFERS", "SATELLITE_MISSIONS"])
        self.assertEqual(sum(len(markers) for markers in faults.HOLD_MARKERS.values()), 2)

    def journal(self, first, restarts=((1, "PASS"), (2, "PASS")), error="SmokeError: First contradictory cut: FS_O3J_AFTER: start 0: " + REFUSED):
        observation = observed(session(store="ROCKET_TRANSFERS"), live(store="ROCKET_TRANSFERS"), restarts=restarts, error=error)
        observation.update(first_start=first, session_errors=list(first["accepted"]) + list(first["unaccepted"]))
        return faults.classify_live(observation)

    def test_a_start_refused_only_for_declared_hold_lines_is_held_and_both_results_are_kept(self):
        result = self.journal(refused_start())
        self.assertEqual(result["classification"], faults.SEEN_HELD)
        self.assertEqual(result["accepted_hold_lines"], [JOURNAL_HOLD])
        reason = result["reasons"][-1]
        self.assertIn("The existing runner's check of the faulted start failed (" + REFUSED + ")", reason)
        self.assertIn("without them passed: log observations, disk oracle of the cut", reason)
        self.assertEqual(self.journal(refused_start(accepted=(JOURNAL_HOLD, JOURNAL_HOLD)))["accepted_hold_lines"],
                         [JOURNAL_HOLD, JOURNAL_HOLD])

    def test_any_unaccepted_line_or_unproved_start_stays_for_a_reader(self):
        other = "[09:00:03] [Server thread/ERROR] [x/]: something else failed"
        for first in (refused_start(unaccepted=(other,)),
                      refused_start(accepted=()),
                      refused_start(filtered=dict(status="FAILED", error="SmokeError: wrong disk")),
                      refused_start(filtered=dict(status="NOT_EVALUATED", reason="Runner F has no first-start evaluation")),
                      refused_start(findings=2),
                      refused_start(status="PASS"),
                      refused_start(status=None, findings=None)):
            result = self.journal(first)
            self.assertEqual(result["classification"], faults.NEEDS_REVIEW, first)
            # The accepted lines are kept also when the case is not held.
            self.assertEqual(result["accepted_hold_lines"], first["accepted"])
        result = self.journal(refused_start(unaccepted=(other,)))
        self.assertIn(other, result["evidence"])
        self.assertIn("1 ERROR or FATAL line(s) of the faulted start are not declared hold lines", result["reasons"][-1])
        self.assertIn("NOT_EVALUATED (Runner F has no", self.journal(refused_start(filtered=dict(
            status="NOT_EVALUATED", reason="Runner F has no first-start evaluation")))["reasons"][-1])

    def test_a_hold_line_never_covers_a_restart_or_an_unseen_fault(self):
        # A failed restart keeps its effect whatever the faulted start showed.
        self.assertEqual(self.journal(refused_start(), restarts=((1, "PASS"), (2, "FAIL")))["classification"], faults.NOT_CAUGHT)
        self.assertEqual(self.journal(refused_start(), restarts=((1, "PASS"),))["classification"], faults.INCOMPLETE)
        unseen = observed(session("LOST_WRITE", store="ROCKET_TRANSFERS", unseen=True, retry=False),
                          live("LOST_WRITE", store="ROCKET_TRANSFERS"), error="x")
        unseen["first_start"] = refused_start()
        self.assertEqual(faults.classify_live(unseen)["classification"], faults.NOT_CAUGHT)

    def test_a_readback_fault_keeps_the_record_of_the_faulted_start(self):
        # The readback rule compares each faulted write with its retry; the record of the faulted start stays apart.
        lines = session("READ_ERROR", store="ROCKET_TRANSFERS", operation="move", moved=True, reads=1)
        observation = observed(lines, live("READ_ERROR", store="ROCKET_TRANSFERS"),
                               error="SmokeError: First contradictory cut: FS_O3J_AFTER: start 0: " + REFUSED)
        observation["first_start"] = refused_start()
        result = faults.classify_live(observation)
        self.assertEqual((result["classification"], result["accepted_hold_lines"]), (faults.SEEN_HELD, [JOURNAL_HOLD]))
        observation["first_start"] = refused_start(unaccepted=("[x] [Server thread/ERROR] [x/]: other",))
        result = faults.classify_live(observation)
        self.assertEqual((result["classification"], result["accepted_hold_lines"]), (faults.NEEDS_REVIEW, [JOURNAL_HOLD]))
        self.assertIn("1 ERROR or FATAL line(s)", result["reasons"][-1])

    def test_a_runner_that_passes_the_faulted_start_keeps_its_class_and_the_accepted_lines(self):
        # Runner R does not refuse an ERROR line of the first start: the registry case is held as before.
        first = dict(declared=list(faults.HOLD_MARKERS["SATELLITE_MISSIONS"]), runner=dict(status="PASS", error=None, findings=None),
                     accepted=[REGISTRY_HOLD], unaccepted=[], unaccepted_count=0,
                     filtered=dict(status="NOT_EVALUATED", reason="Runner R has no first-start evaluation in this harness"))
        case = dict(id="X02-SATELLITE_MISSIONS-WRITE_ERROR-1", store="SATELLITE_MISSIONS", fault="WRITE_ERROR", count=1, skip=1)
        observation = observed(skipped(1), case)
        observation["first_start"] = first
        result = faults.classify_live(observation)
        self.assertEqual((result["classification"], result["accepted_hold_lines"]), (faults.SEEN_HELD, [REGISTRY_HOLD]))


class FirstStartTests(unittest.TestCase):
    """This runner's own evaluation of a faulted start, by runner S's unedited functions."""
    CASE = faults.Case("ROCKET_TRANSFERS", "WRITE_ERROR", 1)
    NATIVE = "FS_O3J_AFTER"

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.world = Path(self.temp.name) / "first-world"; self.world.mkdir()
        self.runner = x01.RUNNERS["S"]
        boundary = self.runner.BOUNDARIES.get(self.NATIVE)
        self.cut = self.runner.marker(self.NATIVE, 0) + ("boundary=" + boundary if boundary else "paused")
        self.disk = dict(rockets=[dict(logical=[1, 2], owner=[3, 4])], device=dict(id=[5, 6]))
        self.inspected = []
        for name, stand_in in (("inspect", lambda world: self.inspected.append(world) or self.disk),
                               ("check", lambda case, disk, restarted: self.inspected.append((case, restarted)))):
            patcher = patch.object(self.runner, name, stand_in); patcher.start(); self.addCleanup(patcher.stop)

    def lines(self, *extra):
        return [line + "\n" for line in ("[09:00:00] [Server thread/INFO] [x/]: " + self.cut, HoldLineTests.INJECTED, *extra)]

    def result(self, status="FAILED", error=REFUSED, disks=None, **first):
        disks = disks or (self.disk, self.disk)
        starts = [dict(restart=0, forced_stop=True, raw=dict(file="raw.json"), status=status, error=error) | first]
        starts += [dict(restart=number, disk=disk, status="PASS") for number, disk in zip((1, 2), disks) if disk is not None]
        return dict(status="FAILED", cases=[dict(case=self.NATIVE, starts=starts, findings=["start 0: " + error] if error else [])])

    def evaluate(self, lines, result=None, world=True, case=None):
        return faults.evaluate_first_start(case or self.CASE, result or self.result(), lines, self.world if world else None)

    def test_the_runners_refusal_and_the_filtered_evaluation_are_both_recorded(self):
        first = self.evaluate(self.lines(JOURNAL_HOLD))
        self.assertEqual(first["runner"], dict(status="FAILED", error=REFUSED, findings=1))
        self.assertEqual(first["declared"], ["ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED"])
        self.assertEqual((first["accepted"], first["unaccepted"], first["unaccepted_count"]), ([JOURNAL_HOLD], [], 0))
        self.assertEqual(first["filtered"]["status"], "PASS")
        self.assertEqual(first["filtered"]["checks"][2:4], ["log observations", "disk oracle of the cut"])
        # The world of that start was inspected by the runner's own oracle, as a cut and not as a restart.
        self.assertEqual(self.inspected, [self.world, (self.NATIVE, False)])
        self.assertTrue(faults.first_start_held(first))
        # The runner's own check, unedited, still refuses the unfiltered log.
        with self.assertRaisesRegex(smoke.SmokeError, REFUSED):
            self.runner.observations(self.lines(JOURNAL_HOLD), self.NATIVE, 0)

    def test_the_runners_unedited_checks_still_refuse_the_filtered_start(self):
        other = "[09:00:03] [Server thread/ERROR] [x/]: something else failed"
        refused = self.evaluate(self.lines(JOURNAL_HOLD, other))
        self.assertEqual((refused["unaccepted"], refused["filtered"]["status"]), ([other], "FAILED"))
        self.assertIn(REFUSED, refused["filtered"]["error"])
        self.assertFalse(faults.first_start_held(refused))
        # A second cut observation, a start that did not end at its cut, undecoded files, and a changed identity.
        twice = self.evaluate(self.lines(JOURNAL_HOLD, "[09:00:05] [Server thread/INFO] [x/]: " + self.cut))
        self.assertIn("Missing or repeated cut observation", twice["filtered"]["error"])
        self.assertIn("Start execution", self.evaluate(self.lines(JOURNAL_HOLD), self.result(forced_stop=False))["filtered"]["error"])
        self.assertIn("Raw decoding", self.evaluate(self.lines(JOURNAL_HOLD), self.result(raw=dict(error="bad")))["filtered"]["error"])
        moved = dict(rockets=[dict(logical=[9, 9], owner=[3, 4])], device=dict(id=[5, 6]))
        for disks in ((self.disk, moved), (moved, self.disk), (self.disk, None)):
            changed = self.evaluate(self.lines(JOURNAL_HOLD), self.result(disks=disks))
            self.assertEqual(changed["filtered"]["status"], "FAILED", disks)
            self.assertIn("Restart changed the logical rocket, owner or device", changed["filtered"]["error"])
        with patch.object(self.runner, "check", side_effect=smoke.SmokeError("Ledger: wrong records")):
            self.assertIn("Ledger: wrong records", self.evaluate(self.lines(JOURNAL_HOLD))["filtered"]["error"])

    def test_nothing_is_evaluated_without_an_accepted_line_a_kept_world_or_a_runner_entry(self):
        clean = self.evaluate(self.lines(), self.result(status="PASS", error=None))
        self.assertEqual((clean["runner"]["status"], clean["accepted"], clean["filtered"]["status"]), ("PASS", [], "NOT_EVALUATED"))
        self.assertEqual(self.evaluate(self.lines(JOURNAL_HOLD), world=False)["filtered"],
                         dict(status="NOT_EVALUATED", reason="The world of the faulted start was not kept"))
        self.assertEqual(self.inspected, [])
        # Runner R reports a first start only when it passed, and has no evaluation here.
        registry = faults.Case("SATELLITE_MISSIONS", "WRITE_ERROR", 1, 1)
        injected = HoldLineTests.INJECTED.replace("ROCKET_TRANSFERS", "SATELLITE_MISSIONS") + "\n"
        first = self.evaluate([injected, REGISTRY_HOLD + "\n"], dict(cases=[dict(case="R3", first_start=dict(baseline={}))]), case=registry)
        self.assertEqual((first["runner"], first["accepted"]), (dict(status="PASS", error=None, findings=None), [REGISTRY_HOLD]))
        self.assertIn("Runner R has no first-start evaluation", first["filtered"]["reason"])
        self.assertEqual(self.evaluate([injected], dict(cases=[dict(case="R3")]), case=registry)["runner"]["status"], None)
        self.assertEqual(set(faults.FIRST_START_CHECKS), {"S"})

    def test_the_first_starts_log_is_read_as_the_process_captured_it(self):
        output = Path(self.temp.name) / "run"; output.mkdir()
        (output / "FS_O3J_AFTER-0.log").write_bytes(b"one\ntwo\x0cstill two\r\nthree")
        (output / "FS_O3J_AFTER-1.log").write_bytes(b"restart\n")
        self.assertEqual(faults.first_start_lines(output), ["one\n", "two\x0cstill two\r\n", "three"])


class LiveRunTests(unittest.TestCase):
    """run_live around a stand-in for runner S: the world of the faulted start is kept for the evaluation."""
    setUp, stand_in, live_case = RunnerTests.setUp, RunnerTests.stand_in, RunnerTests.live_case

    def test_a_journal_hold_is_evaluated_on_the_world_of_the_faulted_start(self):
        case = faults.Case("ROCKET_TRANSFERS", "WRITE_ERROR", 1)
        runner = x01.RUNNERS["S"]
        cut = runner.marker("FS_O3J_AFTER", 0) + "boundary=" + runner.BOUNDARIES.get("FS_O3J_AFTER", "x")
        first = session(store="ROCKET_TRANSFERS") + ["[09:00:00] [Server thread/INFO] [x/]: " + cut, JOURNAL_HOLD]
        disk = dict(rockets=[dict(logical=[1], owner=[2])], device=dict(id=[3]))
        seen = {}

        def run(arguments):
            arguments.evidence_dir.mkdir(parents=True)
            session_dir = self.root / "server"
            (session_dir / "world-FS_O3J_AFTER" / "data").mkdir(parents=True)
            (session_dir / "world-FS_O3J_AFTER" / "data" / "store.dat").write_bytes(b"at the cut")
            starts = []
            for number in (0, 1, 2):
                command = [sys.executable, "-c", "pass"]
                replay.trace_command(arguments, command, number)
                log = arguments.evidence_dir / f"FS_O3J_AFTER-{number}.log"
                process = smoke.CapturedProcess([sys.executable, "-c", "pass"], session_dir, log)
                process.finish(30); process.process.stdin.close(); process.process.stdout.close(); process.log_stream.close()
                log.write_text("\n".join(first if number == 0 else [READY]) + "\n", encoding="utf-8", newline="\n")
                # A restart rewrites the world; the copy for the evaluation must be the one of start 0.
                (session_dir / "world-FS_O3J_AFTER" / "data" / "store.dat").write_bytes(b"after restart %d" % number)
                starts.append(dict(restart=0, forced_stop=True, raw=dict(file="raw"), status="FAILED", error=REFUSED) if number == 0
                              else dict(restart=number, disk=disk, status="PASS"))
            report = dict(status="FAILED", cases=[dict(case="FS_O3J_AFTER", starts=starts, findings=["start 0: " + REFUSED])])
            (arguments.evidence_dir / "results.json").write_text(json.dumps(report), encoding="utf-8")
            raise smoke.SmokeError("First contradictory cut: FS_O3J_AFTER: start 0: " + REFUSED)

        def inspect(world):
            seen.update(world=world, content=(world / "data" / "store.dat").read_bytes())
            return disk

        with patch.object(runner, "inspect", inspect), patch.object(runner, "check", lambda *args: None):
            row, evidence = self.live_case(SimpleNamespace(run=run), case)
        self.assertEqual(seen["content"], b"at the cut")
        self.assertEqual(seen["world"].name, "first-world")
        self.assertEqual(row["classification"], faults.SEEN_HELD)
        observation = json.loads((evidence / "observation.json").read_text(encoding="utf-8"))
        self.assertEqual(observation["first_start"]["runner"], dict(status="FAILED", error=REFUSED, findings=1))
        self.assertEqual(observation["first_start"]["filtered"]["status"], "PASS")
        self.assertEqual(observation["first_start"]["accepted"], [JOURNAL_HOLD])
        self.assertIn("start 0: " + REFUSED, observation["error"])
        classification = json.loads((evidence / "classification.json").read_text(encoding="utf-8"))
        self.assertEqual(classification["accepted_hold_lines"], [JOURNAL_HOLD])
        # The existing runner's own result is kept as it wrote it.
        kept = json.loads((evidence / "runner-results.json").read_text(encoding="utf-8"))
        self.assertEqual((kept["status"], kept["cases"][0]["findings"]), ("FAILED", ["start 0: " + REFUSED]))

    def test_a_store_without_declared_markers_gets_no_evaluation_and_no_world_copy(self):
        case = faults.Case("BAY_RECEIPTS", "WRITE_ERROR", 1)
        module = self.stand_in(session() + ["[09:00:02] [Server thread/ERROR] [x/]: ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED"],
                               {1: [READY], 2: [READY]})
        row, evidence = self.live_case(module, case)
        first = json.loads((evidence / "observation.json").read_text(encoding="utf-8"))["first_start"]
        self.assertEqual((first["declared"], first["accepted"], first["unaccepted_count"]), ([], [], 1))
        self.assertEqual(first["filtered"]["status"], "NOT_EVALUATED")
        # Its effect is the one it had: the stand-in runner passed, as runner B does for a first start.
        self.assertEqual(row["classification"], faults.SEEN_HELD)


if __name__ == "__main__":
    unittest.main()
