import io
import json
import time
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from scripts.campaign_client.cells import load_cell
from scripts.campaign_client.cli import main, submit
from scripts.campaign_client.launch import prepare_client
from scripts.campaign_client.processes import CapturedProcess, ProcessJob
from scripts.campaign_client.protocol import load_session
from scripts.campaign_client.session import MachineLock, Session, run_session
from test_campaign_client_support import FakeWindows, Fixture


class FakeProcess:
    def __init__(self, command=None, cwd=None, log=None, console=False):
        self.process = Mock(pid=123)
        self.process.poll.return_value = None
        self.count = 0
        self.job = Mock()
        self.closed = False

    def wait_for(self, *args, **kwargs):
        return "Done (1s)"

    def tail(self, count):
        return ["test --accessToken secret"]

    def command(self, text):
        self.count += 1

    def close(self, server=False):
        self.closed = True


class SessionTests(Fixture):
    def session(self):
        return Session(load_session(self.dump_session()), load_cell(self.cell_path), FakeWindows())

    def request(self, session, name, ops):
        (session.run / "requests" / (name + ".json")).write_text(json.dumps({"id": name, "ops": ops}), encoding="utf-8")

    def test_attach_end_to_end_fake_owner_never_closed(self):
        session = self.session()
        owned = []
        def start(*args, **kwargs):
            proc = FakeProcess(*args, **kwargs)
            owned.append(proc)
            return proc
        with patch("scripts.campaign_client.session.CapturedProcess", side_effect=start):
            session.setup()
            self.request(session, "01-close", [{"op": "text", "text": "do not type"}, {"op": "close_client"}])
            self.request(session, "02-stop", [{"op": "status"}, {"op": "stop"}])
            session.loop()
            session.cleanup()
        self.assertEqual(1, len(owned))  # Only the server, never an attached client.
        self.assertTrue(owned[0].closed)
        self.assertEqual([], session.backend.events)
        self.assertFalse((session.run / "server").exists())
        self.assertTrue(self.game.exists())
        self.assertTrue((self.game / "logs/latest.log").exists())
        self.assertEqual(1, session.summary["failed_requests"])
        attach = json.loads((session.run / "attach.json").read_text())
        self.assertEqual(123, attach["clients"][0]["process"]["pid"])
        self.assertNotIn("secret", (session.run / "results.jsonl").read_text())

    def test_batch_preflight_identity_before_any_input_or_server_command(self):
        session = self.session()
        with patch("scripts.campaign_client.session.CapturedProcess", FakeProcess):
            session.setup()
            session.backend.front = 789
            self.request(session, "01-invalid", [{"op": "server", "command": "list"}, {"op": "key", "key": "T"}])
            self.request(session, "02-stop", [{"op": "stop"}])
            session.loop()
            self.assertEqual(0, session.server.count)
            self.assertEqual([], session.backend.events)
            session.cleanup()

    def test_invalid_attach_does_not_start_server(self):
        session = self.session()
        session.backend.found = []
        with patch("scripts.campaign_client.session.CapturedProcess") as start:
            with self.assertRaises(ValueError):
                session.execute()
            start.assert_not_called()
        self.assertTrue(self.game.exists())
        self.assertEqual("FAIL", session.summary["status"])

    def test_existing_run_cannot_be_overwritten_or_cleaned(self):
        session = self.session()
        (session.run / "evidence").mkdir(parents=True)
        summary = session.run / "summary.json"
        summary.write_text("owner existing evidence", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            session.execute()
        self.assertEqual("owner existing evidence", summary.read_text())

    def test_deadline_halts_actions(self):
        session = self.session()
        session.deadline = time.monotonic() - 1
        with self.assertRaises(TimeoutError):
            session.perform({"op": "sleep", "seconds": 0})
        with self.assertRaises(TimeoutError):
            session.sleep(0)

    def test_server_start_failure_cleans_partial_copy(self):
        session = self.session()
        with patch("scripts.campaign_client.session.CapturedProcess", side_effect=RuntimeError("synthetic startup failure")):
            # server_command needs the prepared argument file before our fake spawn can fail.
            arg = self.server / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
            arg.parent.mkdir(parents=True, exist_ok=True)
            arg.write_text("fixture", encoding="utf-8")
            with self.assertRaises(RuntimeError):
                session.execute()
        self.assertFalse((session.run / "server").exists())
        self.assertTrue(self.server.exists())

    def test_machine_lock_owner_and_busy_refusal(self):
        path = self.root / "heavy-jvm"
        with MachineLock(path):
            with self.assertRaises(FileExistsError):
                with MachineLock(path):
                    self.fail("Busy lock acquired")
        self.assertFalse(path.exists())
        held = MachineLock(path)
        with held:
            held.retain = True
        self.assertTrue(path.exists())
        held.retain = False
        held.__exit__()

    def test_machine_lock_changed_owner_not_removed(self):
        path = self.root / "heavy-jvm"
        lock = MachineLock(path)
        lock.__enter__()
        (path / "owner.txt").write_text("someone else", encoding="utf-8")
        with self.assertRaises(RuntimeError):
            lock.__exit__()
        self.assertTrue(path.exists())

    def test_partial_launch_failure_stops_all_started_trees(self):
        self.session_spec["mode"] = "launch"
        self.settings["online-mode"] = False
        client = {k: self.attach_spec[k] for k in ("player_name", "language", "gui_scale", "window_size")}
        self.session_spec["clients"] = [client, dict(client, player_name="TesterTwo")]
        session = self.session()
        server, first_client = FakeProcess(), FakeProcess()
        def prepare(cell, spec, game, *args):
            game.mkdir()
            return ["fake-java"]
        with patch("scripts.campaign_client.session.prepare_client", side_effect=prepare), \
                patch("scripts.campaign_client.session.CapturedProcess", side_effect=[server, first_client, RuntimeError("startup")]):
            with self.assertRaises(RuntimeError):
                session.execute()
        self.assertTrue(server.closed)
        self.assertTrue(first_client.closed)
        for directory in session.temporary:
            self.assertFalse(directory.exists())

    def test_cleanup_failure_retains_machine_lock(self):
        fake = Mock()
        fake.summary = {"cleanup_errors": ["Owned process: TimeoutError"]}
        fake.execute.return_value = 1
        lock = self.root / "heavy-jvm"
        with patch("scripts.campaign_client.session.require_windows"), \
                patch("scripts.campaign_client.session.shutil.disk_usage", return_value=Mock(free=100 * 1024**3)), \
                patch("scripts.campaign_client.windows.Windows", return_value=FakeWindows()), \
                patch("scripts.campaign_client.session.Session", return_value=fake):
            self.assertEqual(1, run_session(self.dump_session(), lock))
        self.assertTrue(lock.exists())
        self.assertTrue(fake.summary["machine_lock_retained"])

    def test_job_assigns_suspended_child_and_waits_for_tree_exit(self):
        kernel = Mock()
        kernel.CreateJobObjectW.return_value = 77
        kernel.CreateToolhelp32Snapshot.return_value = 88
        kernel.OpenThread.return_value = 99
        kernel.ResumeThread.return_value = 1
        def first(snapshot, pointer):
            pointer._obj.pid = 123
            pointer._obj.tid = 456
            return 1
        kernel.Thread32First.side_effect = first
        # create=True: ctypes has no WinDLL off Windows, and the fake kernel replaces it everywhere.
        with patch("scripts.campaign_client.processes.C.WinDLL", return_value=kernel, create=True):
            job = ProcessJob()
            job.assign_resume(Mock(pid=123, _handle=100))
            kernel.AssignProcessToJobObject.assert_called_once()
            kernel.ResumeThread.assert_called_once_with(99)
            job.close()
            kernel.TerminateJobObject.assert_called_once_with(77, 1)
            kernel.QueryInformationJobObject.assert_called_once()
            self.assertIsNone(job.handle)

    def test_live_sessions_refuse_other_systems(self):
        from scripts.campaign_client import processes
        with patch("scripts.campaign_client.processes.os.name", "posix"):
            with self.assertRaisesRegex(ValueError, "require Windows"):
                processes.require_windows()
        with patch("scripts.campaign_client.processes.os.name", "nt"):
            processes.require_windows()
        self.assertEqual(0x4, processes.CREATE_SUSPENDED)
        self.assertEqual(0x200, processes.CREATE_NEW_PROCESS_GROUP)

    def test_launch_commands_use_reviewed_helper_and_singleplayer(self):
        cell = load_cell(self.cell_path)
        spec = {k: self.attach_spec[k] for k in ("player_name", "language", "gui_scale", "window_size")}
        args = ["java.exe", "--width", "960", "--height", "540", "--quickPlayMultiplayer", "127.0.0.1:25565"]
        with patch("scripts.v100_compatibility_runtime.client_command", side_effect=lambda *a: list(args)) as helper:
            command = prepare_client(cell, spec, self.root / "disposable-1", 25565, "java.exe")
            self.assertEqual("1280", command[command.index("--width") + 1])
            self.assertEqual("720", command[command.index("--height") + 1])
            helper.assert_called_once_with(cell["runtime_root"], self.root / "disposable-1", "47.4.10", "TesterOne", 25565, "java.exe")
            command = prepare_client(cell, spec, self.root / "disposable-2", 25565, "java.exe", singleplayer=True)
            self.assertNotIn("--quickPlayMultiplayer", command)
        self.assertTrue((self.root / "disposable-1/mods/candidate.jar").exists())
        self.assertTrue(self.game.exists())

    def test_streamed_process_logs_redacted_and_job_closed(self):
        proc = Mock()
        proc.pid = 123
        proc.stdout = io.StringIO('test --accessToken secret\n{"clientId":"secret"}\n')
        proc.stdin = io.StringIO()
        proc.poll.return_value = None
        proc.returncode = 0
        with patch("scripts.campaign_client.processes.require_windows"), \
                patch("scripts.campaign_client.processes.ProcessJob") as job, \
                patch("scripts.campaign_client.processes.subprocess.Popen", return_value=proc):
            captured = CapturedProcess(["fake-java"], self.root, self.root / "captured.log", console=True)
            captured.reader.join(2)
            self.assertNotIn("secret", (self.root / "captured.log").read_text())
            self.assertIn("REDACTED", captured.wait_for("test", 1, lambda _: None))
            captured.close(server=True)
            job.return_value.assign_resume.assert_called_once_with(proc)
            job.return_value.close.assert_called_once()
            proc.wait.assert_called()

    def test_cli_check_never_starts_process(self):
        with patch("subprocess.Popen") as spawn:
            self.assertEqual(0, main(["check", str(self.dump_session())]))
            spawn.assert_not_called()

    def test_request_helper_file_drop_protocol(self):
        run = self.root / "request-run"
        (run / "requests").mkdir(parents=True)
        def reply(_):
            requests = list((run / "requests").glob("*.json"))
            self.assertEqual(1, len(requests))
            data = json.loads(requests[0].read_text())
            (run / "results.jsonl").write_text(json.dumps({"id": data["id"], "outputs": [], "error": None}) + "\n")
        with patch("scripts.campaign_client.cli.time.sleep", side_effect=reply):
            self.assertEqual(0, submit(run, '[{"op":"stop"}]', "attach", ["TesterOne"], 1))
        self.assertEqual([], list((run / "requests").glob("*.tmp")))


if __name__ == "__main__":
    unittest.main()
