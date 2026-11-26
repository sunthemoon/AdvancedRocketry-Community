"""Command reply matching with in-memory logs and a deterministic clock; no JVM or Win32 calls."""
from collections import deque
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

from scripts.campaign_client.gates import ExpectationFailed, PreconditionFailed
from scripts.campaign_client.processes import CapturedProcess
from scripts.campaign_client.session import Session


PREFIX = "[04:29:13] [Server thread/INFO] [minecraft/MinecraftServer]: "
UNKNOWN = PREFIX + "Unknown or incomplete command, see below for error"
ECHO = PREFIX + "...y W476_PAD<--[HERE]"
INCORRECT = PREFIX + "Incorrect argument for command"
REPLY = PREFIX + "[Server] W476_PAD"
COMMAND = "execute if block 700 152 250 advancedrocketrycommunity:landing_pad run say W476_PAD"


class FakeLog(CapturedProcess):
    """Use the real bounded log waiter without creating a process or a reader thread."""

    def __init__(self, replies=()):
        self.lines, self.count, self.error = deque(maxlen=10000), 0, None
        self.lock = threading.Lock()
        self.process = Mock()
        self.process.poll.return_value = None
        self.replies, self.commands = list(replies), []

    def emit(self, *lines):
        with self.lock:
            for line in lines:
                self.lines.append((self.count, line))
                self.count += 1

    def command(self, text):
        self.commands.append(text)
        if self.replies:
            self.emit(*self.replies.pop(0))


class ReplyTests(unittest.TestCase):
    def setUp(self):
        self.clock, self.sleeps = 0.0, []
        self.timer = patch("scripts.campaign_client.session.time.monotonic", side_effect=lambda: self.clock)
        self.timer.start()
        self.addCleanup(self.timer.stop)

    def session(self, replies=(), integrated=False):
        source = FakeLog(replies)
        session = Session({"run_dir": Path("unused"), "time_limit_seconds": 300,
                           "mode": "launch", "singleplayer": integrated},
                          {"forge": "fake", "jei": None, "version_id": "fake",
                           "candidate": {"sha256": "fake"}}, Mock())
        inputs = Mock()
        if integrated:
            inputs.key.side_effect = lambda key: source.command(COMMAND) if key == "ENTER" else None
        session.server = None if integrated else source
        session.clients = {"Tester": {"process": source if integrated else None, "input": inputs}}

        def sleep(seconds):
            session.check_time()
            self.sleeps.append(seconds)
            self.clock += seconds
            session.check_time()

        session.sleep = sleep
        return session, source

    def op(self, kind, expected="W476_PAD", **extra):
        if kind == "require":
            return dict(op=kind, command=COMMAND, expect=expected, timeout=1, **extra)
        if kind == "server":
            return dict(op=kind, command=COMMAND, wait=expected, timeout=1, **extra)
        return dict(op=kind, pattern=expected, timeout=1, **extra)

    def perform(self, session, source, kind, expected="W476_PAD", **extra):
        if kind == "expect_log" and source.replies:
            source.emit(*source.replies.pop(0))
        return session.perform(self.op(kind, expected, **extra))

    def test_normal_say_reply_for_each_operation_and_log_source(self):
        for integrated in (False, True):
            for kind in ("require", "server", "expect_log"):
                with self.subTest(integrated=integrated, kind=kind):
                    session, source = self.session([[REPLY]], integrated)
                    result = self.perform(session, source, kind)
                    self.assertEqual(REPLY, result["line" if kind == "expect_log" else "receipt"])

    def test_i07_rejection_fails_all_attempts_without_waiting_for_timeouts(self):
        for integrated in (False, True):
            with self.subTest(integrated=integrated):
                self.clock, self.sleeps = 0.0, []
                session, source = self.session([[UNKNOWN, ECHO]] * 3, integrated)
                with self.assertRaisesRegex(PreconditionFailed, "Unknown or incomplete command") as error:
                    session.perform(self.op("require", attempts=3, interval=0.5))
                self.assertIn("3 attempt(s)", str(error.exception))
                self.assertEqual([COMMAND] * 3, source.commands)
                self.assertEqual([0.5, 0.5], self.sleeps)
                self.assertEqual(1.0, self.clock)

    def test_each_rejection_form_cannot_satisfy_any_operation(self):
        for rejection, expected in ((UNKNOWN, "Unknown or incomplete command"),
                                    (ECHO, "W476_PAD"), (INCORRECT, "Incorrect argument for command")):
            for kind, error in (("require", PreconditionFailed), ("server", TimeoutError),
                                ("expect_log", ExpectationFailed)):
                with self.subTest(rejection=rejection, kind=kind):
                    session, source = self.session([[rejection]])
                    with self.assertRaises(error):
                        self.perform(session, source, kind, expected)

    def test_require_retries_after_rejection_and_ignores_previous_attempt_lines(self):
        session, source = self.session([[UNKNOWN, ECHO], [REPLY]])
        result = session.perform(self.op("require", attempts=3, interval=0.5))
        self.assertEqual({"precondition": "MET", "attempt": 2, "receipt": REPLY}, result)
        self.assertEqual([COMMAND, COMMAND], source.commands)
        self.assertEqual([0.5], self.sleeps)

    def test_require_rejects_a_standalone_error_without_the_expected_text_immediately(self):
        for rejection in (UNKNOWN, INCORRECT):
            with self.subTest(rejection=rejection):
                self.sleeps = []
                session, source = self.session([[rejection]])
                with self.assertRaises(PreconditionFailed) as error:
                    session.perform(self.op("require"))
                self.assertIn(rejection, str(error.exception))
                self.assertEqual([], self.sleeps)

    def test_require_does_not_accept_a_reply_after_a_rejection_in_the_same_attempt(self):
        session, source = self.session([[UNKNOWN, ECHO, REPLY]])
        with self.assertRaisesRegex(PreconditionFailed, "Unknown or incomplete command"):
            session.perform(self.op("require"))
        self.assertEqual([], self.sleeps)

    def test_waits_skip_rejection_echo_and_accept_a_later_real_reply(self):
        for integrated in (False, True):
            for kind in ("server", "expect_log"):
                with self.subTest(integrated=integrated, kind=kind):
                    session, source = self.session([[UNKNOWN, ECHO]], integrated)
                    sleep = session.sleep

                    def emit_reply(seconds):
                        sleep(seconds)
                        source.emit(REPLY)

                    session.sleep = emit_reply
                    result = self.perform(session, source, kind)
                    self.assertEqual(REPLY, result["line" if kind == "expect_log" else "receipt"])

    def test_rejection_filter_does_not_restart_a_wait_timeout(self):
        for kind in ("server", "expect_log"):
            with self.subTest(kind=kind):
                self.clock, self.sleeps = 0.0, []
                session, source = self.session([[UNKNOWN, ECHO]])
                sleep = session.sleep

                def emit_echo(seconds):
                    sleep(seconds)
                    source.emit(ECHO)

                session.sleep = emit_echo
                with self.assertRaises(TimeoutError if kind == "server" else ExpectationFailed):
                    self.perform(session, source, kind)
                self.assertTrue(all(seconds == 0.1 for seconds in self.sleeps))
                self.assertGreaterEqual(self.clock, 1)
                self.assertLess(self.clock, 1.11)

    def test_a_real_reply_before_a_later_rejection_still_matches(self):
        session, source = self.session([[REPLY, UNKNOWN, ECHO]])
        self.assertEqual(REPLY, session.perform(self.op("require"))["receipt"])

    def test_require_ignores_old_rejections_and_old_matching_replies(self):
        session, source = self.session([[REPLY]])
        source.emit(UNKNOWN, ECHO, REPLY)
        self.assertEqual(REPLY, session.perform(self.op("require"))["receipt"])
        self.assertEqual([COMMAND], source.commands)

    def test_expect_log_keeps_the_request_start_boundary(self):
        session, source = self.session()
        source.emit(REPLY)
        session.request_start = source.count
        source.emit(UNKNOWN, ECHO)
        with self.assertRaises(ExpectationFailed):
            session.perform(self.op("expect_log"))

    def test_raw_process_and_client_waits_keep_their_existing_matching(self):
        session, source = self.session(integrated=True)
        source.emit(ECHO)
        self.assertEqual(ECHO, source.wait_for("W476_PAD", 1, session.sleep))
        self.assertEqual({"line": ECHO}, session.perform({"op": "client_wait", "pattern": "W476_PAD"}))

    def test_capture_failure_and_process_exit_are_not_hidden(self):
        session, source = self.session()
        source.error = "synthetic capture failure"
        with self.assertRaisesRegex(ValueError, "capture failed"):
            session.perform(self.op("expect_log"))
        source.error = None
        source.process.poll.return_value = 1
        with self.assertRaisesRegex(ValueError, "exited while waiting"):
            session.perform(self.op("expect_log"))

    def test_session_deadline_is_not_retried_as_a_command_rejection(self):
        session, source = self.session()
        session.deadline = 0.05
        with self.assertRaisesRegex(TimeoutError, "Session time limit reached"):
            session.perform(self.op("require", attempts=3, interval=0.5))
        self.assertEqual([COMMAND], source.commands)

    def test_rejection_is_reported_even_if_the_following_attempt_times_out(self):
        session, source = self.session([[INCORRECT], []])
        with self.assertRaisesRegex(PreconditionFailed, "Incorrect argument for command"):
            session.perform(self.op("require", attempts=2, interval=0.5))
        self.assertEqual([COMMAND, COMMAND], source.commands)

    def test_attached_log_wait_filters_rejections_too(self):
        with tempfile.TemporaryDirectory() as directory:
            game = Path(directory)
            (game / "logs").mkdir()
            (game / "logs/latest.log").write_text(UNKNOWN + "\n" + ECHO + "\n" + REPLY + "\n", encoding="utf-8")
            session, _ = self.session(integrated=True)
            session.clients["Tester"].update(process=None, game=game, attached=Mock())
            self.assertEqual(REPLY, session.perform(self.op("expect_log"))["line"])


if __name__ == "__main__":
    unittest.main()
