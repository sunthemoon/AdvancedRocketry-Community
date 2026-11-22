"""Precondition gates, the conditional ESC, the lock label and the pause option. Fakes only."""
import json
import unittest
from unittest.mock import Mock, patch

from scripts.campaign_client.cells import load_cell
from scripts.campaign_client.cli import main
from scripts.campaign_client.gates import cursor_captured
from scripts.campaign_client.launch import prepare_client
from scripts.campaign_client.protocol import load_session, validate_request
from scripts.campaign_client.session import MachineLock, Session, lock_label, run_session
from test_campaign_client_support import FakeWindows, Fixture


class ScreenWindows(FakeWindows):
    """captured: the successive answers of cursor_captured; the last one repeats."""

    def __init__(self, captured):
        super().__init__()
        self.captured = list(captured)

    def cursor_captured(self, handle):
        self.events.append(("captured?", handle))
        return self.captured.pop(0) if len(self.captured) > 1 else self.captured[0]


class ScriptedServer:
    """replies: command -> list of reply lists, one per time the command is sent."""

    def __init__(self, replies=None):
        self.process = Mock(pid=123)
        self.process.poll.return_value = None
        self.count, self.lines, self.commands, self.closed = 0, [], [], False
        self.replies = replies or {}

    def __call__(self, *args, **kwargs):
        return self

    def emit(self, line):
        self.lines.append((self.count, line))
        self.count += 1

    def command(self, text):
        self.commands.append(text)
        queued = self.replies.get(text, [])
        for line in (queued.pop(0) if queued else []):
            self.emit(line)

    def wait_for(self, substring, timeout, sleep=None, start=0):
        if substring == "Done (":
            return "Done (1s)"
        for index, line in self.lines:
            if index >= start and substring in line:
                return line
        raise TimeoutError("Process log wait expired")

    def tail(self, count):
        return [line for _, line in self.lines[-count:]] if count else []

    def close(self, server=False):
        self.closed = True


def keys(backend):
    return [event[2] for event in backend.events if event[0] == "key" and event[3]]


class GateTests(Fixture):
    def run_requests(self, backend, server, *requests):
        self.runs = getattr(self, "runs", 0) + 1
        self.session_spec["run_dir"] = str(self.root / f"run-{self.runs}")
        session = Session(load_session(self.dump_session()), load_cell(self.cell_path), backend)
        session.sleep = lambda seconds: session.check_time()
        with patch("scripts.campaign_client.session.CapturedProcess", server):
            session.setup()
            for index, ops in enumerate(requests):
                (session.run / "requests" / f"{index:02d}.json").write_text(
                    json.dumps({"id": f"r{index}", "ops": ops}), encoding="utf-8")
            (session.run / "requests" / "99.json").write_text(
                json.dumps({"id": "stop", "ops": [{"op": "stop"}]}), encoding="utf-8")
            session.loop()
            session.cleanup()
        records = [json.loads(line) for line in (session.run / "results.jsonl").read_text().splitlines()]
        return session, records

    def test_require_stops_the_request_before_its_actions(self):
        backend, server = ScreenWindows([True]), ScriptedServer({"data get block 1 2 3": [["no such block"]]})
        session, records = self.run_requests(backend, server, [
            {"op": "require", "command": "data get block 1 2 3", "expect": "energy: 40000", "timeout": 0.01},
            {"op": "key", "key": "E"}, {"op": "server", "command": "say acted"}])
        self.assertTrue(records[0]["error"].startswith("PRECONDITION_FAILED: "))
        self.assertIn("energy: 40000", records[0]["error"])
        self.assertEqual([], records[0]["outputs"])
        self.assertEqual([], keys(backend))
        self.assertEqual(["data get block 1 2 3"], server.commands)
        self.assertEqual(1, session.summary["precondition_failures"])
        self.assertEqual(1, session.summary["failed_requests"])
        self.assertIsNone(records[1]["error"])  # The stop request still runs.

    def test_require_met_continues_and_ignores_older_lines(self):
        server = ScriptedServer({"status": [["Atmosphere=VACUUM"], ["Atmosphere=BREATHABLE vents=1"]]})
        server.emit("Atmosphere=BREATHABLE vents=1 from an earlier request")
        session, records = self.run_requests(ScreenWindows([True]), server, [
            {"op": "require", "command": "status", "expect": "Atmosphere=BREATHABLE", "attempts": 3,
             "interval": 0.05, "timeout": 0.01}, {"op": "server", "command": "say acted"}])
        self.assertIsNone(records[0]["error"])
        self.assertEqual(2, records[0]["outputs"][0]["attempt"])
        self.assertEqual("MET", records[0]["outputs"][0]["precondition"])
        self.assertEqual(["status", "status", "say acted"], server.commands)

    def test_require_attempts_are_bounded(self):
        server = ScriptedServer()
        _, records = self.run_requests(ScreenWindows([True]), server, [
            {"op": "require", "command": "status", "expect": "never", "attempts": 4, "interval": 0.05,
             "timeout": 0.01}])
        self.assertEqual(["status"] * 4, server.commands)
        self.assertIn("4 attempt(s)", records[0]["error"])

    def test_gate_bounds_are_validated(self):
        def check(op):
            return validate_request({"id": "x", "ops": [op]}, "launch", ["TesterOne"])
        check({"op": "require", "command": "a", "expect": "b", "attempts": 60, "interval": 2, "timeout": 3})
        check({"op": "require_screen", "open": True})
        check({"op": "close_screen", "timeout": 30})
        check({"op": "expect_log", "pattern": "code=OK", "timeout": 300})
        for bad in ({"op": "require", "command": "a"}, {"op": "require", "command": "a", "expect": "b\nc"},
                    {"op": "require", "command": "a", "expect": "b", "attempts": 61},
                    {"op": "require", "command": "a", "expect": "b", "attempts": 60, "interval": 3, "timeout": 3},
                    {"op": "require", "command": "a", "expect": "b", "regex": True},
                    {"op": "require_screen"}, {"op": "require_screen", "open": 1},
                    {"op": "close_screen", "timeout": 31}, {"op": "expect_log", "pattern": ""},
                    {"op": "expect_log", "pattern": "a", "timeout": 301}):
            with self.assertRaises(ValueError, msg=bad):
                check(bad)

    def test_close_screen_sends_esc_only_to_an_open_screen(self):
        backend = ScreenWindows([True])  # Pointer captured: no screen is open.
        _, records = self.run_requests(backend, ScriptedServer(), [{"op": "close_screen"}])
        self.assertEqual({"screen_was_open": False, "closed": False},
                         {k: records[0]["outputs"][0][k] for k in ("screen_was_open", "closed")})
        self.assertEqual([], keys(backend))
        backend = ScreenWindows([False, True])  # A screen is open, and ESC closes it.
        _, records = self.run_requests(backend, ScriptedServer(), [{"op": "close_screen"}])
        self.assertTrue(records[0]["outputs"][0]["closed"])
        self.assertEqual([27], keys(backend))

    def test_close_screen_reports_a_screen_that_stays_open(self):
        backend = ScreenWindows([False])  # For example the death screen.
        with patch("scripts.campaign_client.input.time.monotonic", side_effect=iter(range(0, 100000, 2))):
            _, records = self.run_requests(backend, ScriptedServer(), [
                {"op": "close_screen", "timeout": 1}, {"op": "key", "key": "E"}])
        self.assertEqual("PRECONDITION_FAILED: the open screen did not close on ESC", records[0]["error"])
        self.assertEqual([27], keys(backend))  # One ESC, and the later key was not sent.

    def test_require_screen_gates_clicks(self):
        backend = ScreenWindows([True])
        with patch("scripts.campaign_client.input.time.monotonic", side_effect=iter(range(0, 100000, 2))):
            _, records = self.run_requests(backend, ScriptedServer(), [
                {"op": "require_screen", "open": True, "timeout": 1}, {"op": "click", "x": 5, "y": 5}])
        self.assertEqual("PRECONDITION_FAILED: a screen is not open", records[0]["error"])
        self.assertEqual([], [event for event in backend.events if event[0] == "post"])
        backend = ScreenWindows([True, False])  # The screen appears on the second look.
        _, records = self.run_requests(backend, ScriptedServer(), [
            {"op": "require_screen", "open": True}, {"op": "click", "x": 5, "y": 5}])
        self.assertIsNone(records[0]["error"])
        self.assertEqual(3, len([event for event in backend.events if event[0] == "post"]))

    def test_expect_log_reads_only_lines_since_the_request_began(self):
        server = ScriptedServer({"go": [["ARCE_STATION_WARP action=request code=WARP_INSUFFICIENT_ENERGY"]]})
        server.emit("ARCE_STATION_WARP action=request code=WARP_ISSUED")
        _, records = self.run_requests(ScreenWindows([True]), server,
            [{"op": "expect_log", "pattern": "code=WARP_ISSUED", "timeout": 0.01}, {"op": "key", "key": "E"}],
            [{"op": "server", "command": "go"},
             {"op": "expect_log", "pattern": "code=WARP_INSUFFICIENT_ENERGY", "timeout": 0.01}])
        self.assertTrue(records[0]["error"].startswith("EXPECTATION_FAILED: "))
        self.assertIsNone(records[1]["error"])
        self.assertEqual("MET", records[1]["outputs"][1]["expectation"])

    def test_status_reports_pointer_capture(self):
        _, records = self.run_requests(ScreenWindows([True]), ScriptedServer(), [{"op": "status"}])
        self.assertIs(True, records[0]["outputs"][0]["pointer_captured"])

    def test_pointer_capture_rectangles(self):
        desktop, client = (0, 0, 2560, 1440), (100, 120, 1380, 840)
        self.assertTrue(cursor_captured(client, client, desktop))
        self.assertFalse(cursor_captured(desktop, client, desktop))
        self.assertFalse(cursor_captured((0, 0, 1380, 840), client, desktop))
        # Several monitors: a full-screen window on one of them is still decidable.
        self.assertTrue(cursor_captured((0, 0, 1920, 1080), (0, 0, 1920, 1080), (-1920, 0, 1920, 1080)))
        with self.assertRaisesRegex(ValueError, "undecidable"):
            cursor_captured((0, 0, 1920, 1080), (0, 0, 1920, 1080), (0, 0, 1920, 1080))
        with self.assertRaises(ValueError):
            cursor_captured((0, 0, 0, 0), client, desktop)


class LockLabelTests(Fixture):
    def test_owner_names_the_session_and_keeps_the_pid_field(self):
        path = self.root / "heavy-jvm"
        with MachineLock(path, "W443-p9-v2-dedicated-01"):
            owner = (path / "owner.txt").read_text(encoding="utf-8")
        self.assertRegex(owner, r"^W443-p9-v2-dedicated-01 pid=\d+ [0-9a-f]{32} ")
        self.assertNotIn("W399", owner)
        with MachineLock(path):
            self.assertTrue((path / "owner.txt").read_text(encoding="utf-8").startswith("campaign-session pid="))

    def test_label_alphabet(self):
        for bad in ("", "two words", "pid=1", "x" * 65, None):
            with self.assertRaises(ValueError):
                lock_label(bad)

    def test_run_session_labels_the_lock_from_the_run_directory(self):
        seen = {}
        fake = Mock()
        fake.summary = {"cleanup_errors": []}
        def execute():
            seen["owner"] = (lock / "owner.txt").read_text(encoding="utf-8")
            return 0
        fake.execute.side_effect = execute
        lock = self.root / "heavy-jvm"
        self.session_spec["run_dir"] = str(self.root / "dedicated 01")
        for label, expected in ((None, "campaign-dedicated-01 pid="), ("W443-round", "W443-round pid=")):
            with patch("scripts.campaign_client.session.require_windows"), \
                    patch("scripts.campaign_client.session.shutil.disk_usage", return_value=Mock(free=100 * 1024**3)), \
                    patch("scripts.campaign_client.windows.Windows", return_value=FakeWindows()), \
                    patch("scripts.campaign_client.session.Session", return_value=fake):
                self.assertEqual(0, run_session(self.dump_session(), lock, label))
            self.assertTrue(seen["owner"].startswith(expected), seen["owner"])
            self.assertFalse(lock.exists())

    def test_cli_passes_the_label(self):
        with patch("scripts.campaign_client.session.run_session", return_value=0) as run:
            self.assertEqual(0, main(["session", str(self.dump_session()), "--jvm-lock", str(self.root / "lock"),
                                      "--lock-label", "W443-round"]))
        self.assertEqual("W443-round", run.call_args.args[2])


class PauseOptionTests(Fixture):
    def launch_spec(self, **extra):
        self.session_spec["mode"] = "launch"
        self.settings["online-mode"] = False
        client = {k: self.attach_spec[k] for k in ("player_name", "language", "gui_scale", "window_size")}
        self.session_spec["clients"] = [dict(client, **extra)]
        return load_session(self.dump_session())["clients"][0]

    def options(self, spec, name):
        with patch("scripts.v100_compatibility_runtime.client_command",
                   side_effect=lambda *a: ["java.exe", "--width", "1", "--height", "1"]):
            prepare_client(load_cell(self.cell_path), spec, self.root / name, 25565, "java.exe")
        return (self.root / name / "options.txt").read_text(encoding="utf-8").splitlines()

    def test_default_stays_false_and_the_session_can_ask_for_true(self):
        self.assertIn("pauseOnLostFocus:false", self.options(self.launch_spec(), "default"))
        self.assertIn("pauseOnLostFocus:true", self.options(self.launch_spec(pause_on_lost_focus=True), "asked"))
        self.assertIn("pauseOnLostFocus:false", self.options(self.launch_spec(pause_on_lost_focus=False), "refused"))

    def test_option_is_a_boolean_and_launch_only(self):
        with self.assertRaises(ValueError):
            self.launch_spec(pause_on_lost_focus="true")
        self.session_spec["mode"] = "attach"
        self.session_spec["clients"] = [dict(self.attach_spec, game_dir=str(self.game), pause_on_lost_focus=True)]
        with self.assertRaises(ValueError):
            load_session(self.dump_session())


if __name__ == "__main__":
    unittest.main()
