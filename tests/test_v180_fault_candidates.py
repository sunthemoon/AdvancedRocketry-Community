"""The opt-in candidate keeper of the X02 storage-fault runner (W450); never launches Java."""
import hashlib
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
import v180_fault_candidates as candidates

M = faults.MARKER
STORE = "SATELLITE_MISSIONS"
FILE = faults.STORES[STORE]
READY = '[00:00:01] [Server thread/INFO] [minecraft/DedicatedServer]: Done (1.0s)! For help, type "help"'


def digest(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def attempt(number, content, result="moved", moved=True, faulted=False, store=STORE):
    return (f"[06:05:10] [ARCE-satellite-registry/INFO] [x/]: {M} attempt store={store} attempt={number} result={result} "
            f"moved={str(moved).lower()} faulted={str(faulted).lower()} bytes={len(content)} sha256={digest(content)}\n")


# A stand-in for the faulted start: each write replaces the store file and prints the injector's line, and the next
# one waits until the keeper has kept the last, as the registry's retry waits for its cadence.
CHILD = r"""
import hashlib, pathlib, sys, time
world, kept = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
world.mkdir(parents=True)
print("[x] [Server thread/INFO]: ARCE_RELEASE_STORAGE_FAULT installed store=SATELLITE_MISSIONS fault=READ_ERROR count=1 skip=1", flush=True)
for number, content, result, faulted in ((1, b"baseline", "moved", "false"), (2, b"faulted candidate", "failed", "true"),
                                         (3, b"retried candidate", "moved", "false")):
    if number == 1:
        print("[x] [io/INFO]: ARCE_RELEASE_STORAGE_FAULT passed store=SATELLITE_MISSIONS fault=READ_ERROR attempt=1 skip=1 left=0", flush=True)
    (world / sys.argv[3]).write_bytes(content)
    if number == 2:
        print("[x] [io/INFO]: ARCE_RELEASE_STORAGE_FAULT injected store=SATELLITE_MISSIONS fault=READ_ERROR operation=move attempt=2 remaining=0 skip=1", flush=True)
        print("[x] [io/INFO]: ARCE_RELEASE_STORAGE_FAULT injected store=SATELLITE_MISSIONS fault=READ_ERROR operation=read attempt=0 remaining=0", flush=True)
    print("[x] [io/INFO]: ARCE_RELEASE_STORAGE_FAULT attempt store=SATELLITE_MISSIONS attempt=%d result=%s moved=true "
          "faulted=%s bytes=%d sha256=%s" % (number, result, faulted, len(content), hashlib.sha256(content).hexdigest()), flush=True)
    deadline = time.monotonic() + 30
    while not (kept / ("attempt-%d.dat" % number)).is_file():
        if time.monotonic() > deadline:
            sys.exit(3)
        time.sleep(0.01)
"""


class KeeperTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.store = self.root / "world" / "data" / FILE
        self.store.parent.mkdir(parents=True)
        self.keeper = candidates.Keeper(STORE, lambda cwd: cwd / "world" / "data" / FILE, self.root / "kept")

    def test_a_moved_attempt_is_kept_only_with_the_bytes_of_its_line(self):
        self.store.write_bytes(b"faulted candidate")
        self.keeper.line(self.root, attempt(2, b"faulted candidate", "failed", faulted=True))
        self.store.write_bytes(b"retried candidate")
        self.keeper.line(self.root, attempt(3, b"retried candidate"))
        report = self.keeper.report()
        self.assertEqual([(row["attempt"], row["kept"]) for row in report["attempts"]],
                         [(2, "attempt-2.dat"), (3, "attempt-3.dat")])
        self.assertEqual((self.root / "kept" / "attempt-2.dat").read_bytes(), b"faulted candidate")
        self.assertEqual((self.root / "kept" / "attempt-3.dat").read_bytes(), b"retried candidate")
        self.assertEqual(report["attempts"][0]["read_sha256"], report["attempts"][0]["sha256"])
        self.assertEqual((report["store"], report["directory"], report["unfaulted_failures"]), (STORE, "kept", []))

    def test_a_file_that_is_not_the_candidate_of_the_line_is_listed_and_never_kept(self):
        self.store.write_bytes(b"a later write")
        self.keeper.line(self.root, attempt(2, b"faulted candidate", "failed", faulted=True))
        # The same size with other bytes is not the candidate either.
        self.store.write_bytes(b"faulted candidatE")
        self.keeper.line(self.root, attempt(3, b"faulted candidate"))
        rows = self.keeper.report()["attempts"]
        self.assertEqual([row["kept"] for row in rows], [None, None])
        self.assertEqual(rows[0]["read_sha256"], digest(b"a later write"))
        self.assertNotEqual(rows[1]["read_sha256"], rows[1]["sha256"])
        self.assertFalse((self.root / "kept").exists())

    def test_an_attempt_that_was_not_moved_is_listed_without_a_read(self):
        self.store.write_bytes(b"old")
        self.keeper.line(self.root, attempt(1, b"old", "failed", moved=False, faulted=True))
        self.keeper.line(self.root, attempt(2, b"old", "failed", moved=False))
        report = self.keeper.report()
        self.assertEqual([("read_sha256" in row, row["kept"]) for row in report["attempts"]], [(False, None)] * 2)
        # A failure that no fault asked for is named, so that a run the read disturbed shows.
        self.assertEqual(report["unfaulted_failures"], [2])

    def test_other_stores_other_lines_and_an_unreadable_file_keep_nothing(self):
        self.keeper.line(self.root, attempt(1, b"x", store="BAY_RECEIPTS"))
        self.keeper.line(self.root, f"{M} injected store={STORE} fault=READ_ERROR operation=move attempt=2 remaining=0\n")
        self.keeper.line(self.root, READY)
        self.assertEqual(self.keeper.report()["attempts"], [])
        self.keeper.line(self.root, attempt(1, b"absent"))
        self.store.write_bytes(b"12345")
        with patch.object(candidates, "MAX_BYTES", 4):
            self.keeper.line(self.root, attempt(2, b"12345"))
        with patch.object(Path, "read_bytes", side_effect=PermissionError("held")):
            self.keeper.line(self.root, attempt(3, b"12345"))
        rows = self.keeper.report()["attempts"]
        self.assertEqual([row["kept"] for row in rows], [None, None, None])
        self.assertEqual(rows[0]["read_error"], "The store file is absent")
        self.assertIn("exceeds 4 bytes", rows[1]["read_error"])
        self.assertIn("PermissionError", rows[2]["read_error"])

    def test_the_number_of_listed_attempts_is_bounded(self):
        self.store.write_bytes(b"x")
        for number in range(1, candidates.MAX_ATTEMPTS + 5):
            self.keeper.line(self.root, attempt(number, b"x"))
        self.assertEqual(len(self.keeper.report()["attempts"]), candidates.MAX_ATTEMPTS)


class HookTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def child(self, log):
        return smoke.CapturedProcess([sys.executable, "-c", CHILD, str(self.root / "world-R3" / "data"),
                                      str(self.root / "kept"), FILE], self.root, self.root / log)

    def test_the_keeper_follows_the_first_start_as_its_lines_arrive(self):
        keeper = candidates.Keeper(STORE, lambda cwd: cwd / "world-R3" / "data" / FILE, self.root / "kept")
        process_before = smoke.CapturedProcess
        with faults.hooks(None, keeper):
            process = self.child("R3-0.log")
            self.assertEqual(process.finish(60), 0)
            process.process.stdin.close(); process.process.stdout.close()
        self.assertIs(smoke.CapturedProcess, process_before)
        rows = keeper.report()["attempts"]
        self.assertEqual([(row["attempt"], row["result"], row["faulted"], row["kept"]) for row in rows],
                         [(1, "moved", False, "attempt-1.dat"), (2, "failed", True, "attempt-2.dat"),
                          (3, "moved", False, "attempt-3.dat")])
        # The faulted candidate was replaced on disk by the retry, and is kept all the same.
        self.assertEqual((self.root / "kept" / "attempt-2.dat").read_bytes(), b"faulted candidate")
        self.assertEqual((self.root / "world-R3" / "data" / FILE).read_bytes(), b"retried candidate")

    def test_a_restart_and_a_run_without_the_keeper_are_not_followed(self):
        keeper = candidates.Keeper(STORE, lambda cwd: cwd / "world-R3" / "data" / FILE, self.root / "kept")
        with faults.hooks(None, keeper):
            process = self.child("R3-1.log")
            # Nothing keeps the candidates of a restart, so the stand-in gives up its wait.
            process.process.kill(); process.abort()
            process.process.stdin.close(); process.process.stdout.close()
        self.assertEqual(keeper.report()["attempts"], [])
        with faults.hooks():
            process = smoke.CapturedProcess([sys.executable, "-c", "print('ready')"], self.root, self.root / "R3-0.log")
            self.assertEqual(process.finish(30), 0)
            self.assertIsNone(process._arce_watcher)
            process.process.stdin.close(); process.process.stdout.close()
        self.assertFalse((self.root / "kept").exists())


class RunnerTests(unittest.TestCase):
    """run_live around a stand-in for runner R: the option adds files beside the observation and changes nothing else."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.scratch = self.root / "Temp"; self.scratch.mkdir()
        patcher = patch.object(faults, "TEMP", self.scratch); patcher.start(); self.addCleanup(patcher.stop)

    def arguments(self, **more):
        return SimpleNamespace(source_server=self.root, jar=self.root / "mod.jar", java="java", source_commit="s" * 40,
                               candidate_commit="c" * 40, jar_sha256="j" * 64, startup_timeout=1, exit_timeout=1,
                               flight_timeout=1, settle_timeout=1, **more)

    def stand_in(self):
        def run(arguments):
            arguments.evidence_dir.mkdir(parents=True)
            arguments.session_dir.mkdir(parents=True)
            case = arguments.case[0]
            command = [sys.executable, "-c", CHILD, str(arguments.session_dir / ("world-" + case) / "data"),
                       str(arguments.kept), FILE]
            replay.trace_command(arguments, command, 0)
            # The property is on the command as on a server's; the stand-in ignores it.
            process = smoke.CapturedProcess([part for part in command if not part.startswith(faults.PROPERTY)],
                                            arguments.session_dir, arguments.evidence_dir / f"{case}-0.log")
            try:
                code = process.finish(60)
            finally:
                process.process.stdin.close(); process.process.stdout.close()
            listed = []
            for number in (1, 2):
                replay.trace_command(arguments, ["java", "@args"], number)
                (arguments.evidence_dir / f"{case}-{number}.log").write_text(READY + "\n", encoding="utf-8")
                listed.append(dict(restart=number))
            report = dict(status="PASS", cases=[dict(case=case, first_start=dict(exit_code=code), restarts=listed)])
            (arguments.evidence_dir / "results.json").write_text(json.dumps(report), encoding="utf-8")
            return report

        return SimpleNamespace(run=run)

    def live_case(self, name, **more):
        case = faults.by_id("X02-SATELLITE_MISSIONS-READ_ERROR-1")
        evidence = self.root / name / "cases" / case.id
        # Without the option nothing keeps a candidate, so the stand-in is told a directory that the test fills.
        args = self.arguments(kept=evidence / "candidates", **more)
        with patch.object(faults, "runner_of", lambda selected: self.stand_in()), patch.object(x01.bay, "assert_pin"), \
                patch.object(x01.evidence_tools, "candidate_pin"):
            row = faults.run_live(args, case, evidence, self.root / name / "work", "owner")
        return row, evidence

    def test_the_option_keeps_both_candidates_beside_an_unchanged_observation_and_class(self):
        row, evidence = self.live_case("kept", keep_candidates=True)
        report = json.loads((evidence / "candidates.json").read_text(encoding="utf-8"))
        self.assertEqual([(entry["attempt"], entry["kept"]) for entry in report["attempts"]],
                         [(1, "attempt-1.dat"), (2, "attempt-2.dat"), (3, "attempt-3.dat")])
        for entry in report["attempts"]:
            self.assertEqual(digest((evidence / "candidates" / entry["kept"]).read_bytes()), entry["sha256"])
        observation = json.loads((evidence / "observation.json").read_text(encoding="utf-8"))
        self.assertNotIn("candidates", observation)
        # The retry has other bytes, and the exact-byte rule still decides: the kept files change no class.
        self.assertEqual(row["classification"], faults.NOT_CAUGHT)
        self.assertIn("The write after faulted write 2 is missing or has different bytes.", row["reasons"])

    def test_without_the_option_nothing_is_read_or_written(self):
        case = faults.by_id("X02-SATELLITE_MISSIONS-READ_ERROR-1")
        evidence = self.root / "plain" / "cases" / case.id
        seen = []

        def run(arguments):
            arguments.evidence_dir.mkdir(parents=True)
            lines = [f"[x] [Server thread/INFO]: {M} installed store={STORE} fault=READ_ERROR count=1 skip=1",
                     attempt(2, b"faulted", "failed", faulted=True).strip()]
            (arguments.evidence_dir / "R3-0.log").write_text("\n".join(lines) + "\n", encoding="utf-8")
            (arguments.evidence_dir / "results.json").write_text(json.dumps(dict(status="PASS", cases=[{}])), encoding="utf-8")
            return {}

        with patch.object(faults, "runner_of", lambda selected: SimpleNamespace(run=run)), patch.object(x01.bay, "assert_pin"), \
                patch.object(x01.evidence_tools, "candidate_pin"), \
                patch.object(candidates.Keeper, "line", lambda *arguments: seen.append(arguments)), \
                patch.object(faults, "hooks", wraps=faults.hooks) as hooked:
            faults.run_live(self.arguments(), case, evidence, self.root / "plain" / "work", "owner")
        self.assertIsNone(hooked.call_args.args[1])
        self.assertEqual(seen, [])
        self.assertEqual(sorted(path.name for path in evidence.iterdir()),
                         ["classification.json", "commands.json", "logs", "observation.json", "runner-results.json"])


class OptionTests(unittest.TestCase):
    BASE = ["run_v180_storage_faults.py", "server", "--jar", "mod.jar", "--evidence-dir", "evidence", "--source-commit", "s",
            "--candidate-commit", "c", "--jar-sha256", "j", "--java", "java"]

    def parsed(self, *more):
        with patch.object(sys, "argv", [*self.BASE, *more]), patch("builtins.print"), \
                patch.object(faults, "run", return_value=dict(status="COMPLETE")) as run:
            self.assertEqual(faults.main(), 0)
        return run.call_args.args[0]

    def test_the_option_is_off_unless_named_and_no_other_default_moves(self):
        plain, kept = vars(self.parsed()), vars(self.parsed("--keep-candidates"))
        self.assertIs(plain.pop("keep_candidates"), False)
        self.assertIs(kept.pop("keep_candidates"), True)
        self.assertEqual(plain, kept)
        self.assertEqual((plain["cooldown"], plain["startup_timeout"], plain["exit_timeout"], plain["flight_timeout"],
                          plain["settle_timeout"]), (90, 240, 180, 120, 180))

    def test_the_manifest_names_the_option_only_when_it_is_used(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            for label, more in (("plain", {}), ("kept", dict(keep_candidates=True))):
                evidence, scratch = root / label / "evidence", root / label / "scratch"
                (evidence / "cases").mkdir(parents=True); scratch.mkdir()
                args = SimpleNamespace(source_commit="s" * 40, candidate_commit="c" * 40, jar_sha256="j" * 64, cooldown=0, **more)
                manifest = faults.campaign(args, [], evidence, scratch)
                self.assertEqual("keep_candidates" in manifest, bool(more), label)
                self.assertEqual(manifest["status"], "COMPLETE")


if __name__ == "__main__":
    unittest.main()
