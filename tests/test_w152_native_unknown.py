"""Linux-safe exact log expectations and line-backed native-runner stage wiring."""
from contextlib import ExitStack
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w152_native as native

ERROR = ("[22:22:11] [Server thread/ERROR] [io.gi.su.ad.ro.pe.RocketTransactionSavedData/]: "
         "ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN sequence=2 acknowledged=none")
UNKNOWN = ("C1_UNKNOWN", "C3_UNKNOWN", "C5_UNKNOWN")


def events(cut):
    return [{"label": cut.replace("_UNKNOWN", "_WRITE_UNKNOWN")}]


class W152UnknownExpectationTest(unittest.TestCase):
    def test_default_still_rejects_every_unknown_error(self):
        for cut in UNKNOWN:
            with self.subTest(cut=cut), self.assertRaisesRegex(native.smoke.SmokeError, "ERROR/FATAL"):
                native.check_errors([ERROR], cut, events(cut))

    def test_opt_in_requires_exactly_one_error_and_one_matching_event(self):
        for cut in UNKNOWN:
            native.check_errors([ERROR], cut, events(cut), expect_unknown=True)
            native.check_errors([ERROR.replace("acknowledged=none", "acknowledged=1")], cut,
                                events(cut), expect_unknown=True)
            for lines, observed in (([], events(cut)), ([ERROR, ERROR], events(cut)),
                                    ([ERROR], []), ([ERROR], events(cut) * 2),
                                    ([ERROR], events("C9_UNKNOWN"))):
                with self.subTest(cut=cut, lines=lines, observed=observed), self.assertRaises(native.smoke.SmokeError):
                    native.check_errors(lines, cut, observed, expect_unknown=True)

    def test_full_line_and_logger_are_required(self):
        altered = [ERROR + " extra", ERROR + " ", "prefix " + ERROR,
                   ERROR.replace("UNKNOWN", "NOT_COMMITTED"), ERROR.replace("sequence=2", "sequence=-1"),
                   ERROR.replace("acknowledged=none", "acknowledged=unknown"),
                   ERROR.replace("Server thread", "Other thread"),
                   ERROR.replace("RocketTransactionSavedData", "OtherSavedData"),
                   ERROR.replace("/ERROR]", "/FATAL]")]
        for line in altered:
            with self.subTest(line=line), self.assertRaises(native.smoke.SmokeError):
                native.check_errors([line], "C1_UNKNOWN", events("C1_UNKNOWN"), expect_unknown=True)

    def test_other_errors_and_fatals_are_never_accepted(self):
        for extra in (ERROR.replace("UNKNOWN", "NOT_COMMITTED"), "[22:22:11] [Server thread/ERROR] unrelated",
                      "[22:22:11] [Server thread/FATAL] unrelated"):
            with self.subTest(extra=extra), self.assertRaises(native.smoke.SmokeError):
                native.check_errors([ERROR, extra], "C1_UNKNOWN", events("C1_UNKNOWN"), expect_unknown=True)

    def test_non_unknown_cuts_keep_the_original_error_policy(self):
        for cut in native.CUTS + native.CONSUMER_CUTS:
            if cut in UNKNOWN:
                continue
            native.check_errors([], cut, [], expect_unknown=True)
            with self.subTest(cut=cut), self.assertRaisesRegex(native.smoke.SmokeError, "ERROR/FATAL"):
                native.check_errors([ERROR], cut, events("C1_UNKNOWN"), expect_unknown=True)

    def test_cli_flag_is_opt_in(self):
        required = ["runner", "--source-server", "template", "--jar", "test.jar", "--source-commit", "commit",
                    "--evidence-dir", "out", "--java", "java", "--cut", "C1_UNKNOWN"]
        for flag, expected in (([], False), (["--expect-unknown"], True)):
            with patch.object(sys, "argv", required + flag), patch.object(native, "run", return_value={"status": "PASS"}) as run, \
                    patch("builtins.print"):
                self.assertEqual(0, native.main())
                self.assertIs(expected, run.call_args.args[0].expect_unknown)


class W152UnknownStageTest(unittest.TestCase):
    def run_lines(self, cut, recovery, *, opt_in=True, alter=None):
        with tempfile.TemporaryDirectory(prefix="w461-lines-") as temporary:
            root = Path(temporary)
            (root / "temp").mkdir(); (root / "scripts").mkdir(); (root / "template/libraries").mkdir(parents=True)
            (root / ".w152-export-source").write_text("commit", encoding="ascii")
            (root / "lock-owner").write_text("owned-token", encoding="ascii")
            jar = root / "test.jar"; jar.write_bytes(b"synthetic")
            stages = ["start", "recovery-cut", "recover1", "recover2"]
            for stage in stages:
                selected = cut if stage == "start" else recovery if stage == "recovery-cut" else "NONE"
                lines = [ERROR] if selected in UNKNOWN else []
                observed = events(selected) if selected in UNKNOWN else []
                if alter:
                    lines, observed = alter(stage, lines, observed)
                marker = "W152_NATIVE_DONE status=STABLE" if selected == "NONE" else "W152_NATIVE_CUT boundary=" + selected
                (root / (stage + ".lines")).write_text("\n".join(lines + [marker]) + "\n", encoding="utf-8")
                (root / (stage + "-events.json")).write_text(json.dumps(observed), encoding="utf-8")
            started = []

            class LineProcess:
                def __init__(self, command, cwd, log_path):
                    stage = next(part.split("=", 1)[1] for part in command if part.startswith("-Dadvancedrocketrycommunity.w152Stage="))
                    selected = next(part.split("=", 1)[1] for part in command if part.startswith("-Dadvancedrocketrycommunity.w152Cut="))
                    started.append(stage)
                    text = (root / (stage + ".lines")).read_text(encoding="utf-8")
                    self.lines = text.splitlines(); log_path.write_text(text, encoding="utf-8")
                    self.stopped = False; self.process = SimpleNamespace(poll=lambda: 0 if self.stopped else None)
                    (cwd / "world").mkdir(exist_ok=True)
                    (cwd / "world/w152-native.json").write_text(json.dumps(dict(
                        status="STABLE" if selected == "NONE" else "CUT",
                        events=json.loads((root / (stage + "-events.json")).read_text(encoding="utf-8")))), encoding="utf-8")

                def wait_for(self, marker, timeout):
                    return next(i for i, line in enumerate(self.lines) if marker.search(line))

                def abort(self):
                    self.stopped = True

                def command(self, text):
                    if text != "stop":
                        raise AssertionError(text)

                def finish(self):
                    self.stopped = True
                    return 0

            real_path = Path

            def local_path(value):
                return {"D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt": root / "lock-owner",
                        "D:/GitHub/ARCE-Task-Evidence/Temp": root / "temp"}.get(str(value), real_path(value))

            args = SimpleNamespace(source_commit="commit", source_export=True, source_server=root / "template",
                                   jar=jar, evidence_dir=root / "out", java="java", cut=cut,
                                   recovery_cut=recovery, initial_d="SAVED", payload_hosts="CONTAINERS")
            if opt_in is not None:
                args.expect_unknown = opt_in
            state = dict(phase=None, sealed=[], authority="ENTITY", files={})
            with ExitStack() as stack:
                stack.enter_context(patch.object(native, "__file__", str(root / "scripts/run_w152_native.py")))
                stack.enter_context(patch.object(native, "Path", side_effect=local_path))
                stack.enter_context(patch.dict(native.os.environ, W152_LOCK_TOKEN="owned-token"))
                stack.enter_context(patch.object(native.smoke, "CapturedProcess", LineProcess))
                stack.enter_context(patch.object(native.shutil, "disk_usage", return_value=SimpleNamespace(free=20 * 1024**3)))
                stack.enter_context(patch.object(native, "inspect", return_value=state))
                result = native.run(args)
            self.assertEqual([], list((root / "temp").iterdir()))
            return result, started

    def test_initial_and_recovery_unknowns_complete_both_stable_restarts(self):
        for cut, recovery in (("C1_UNKNOWN", "RECOVERY_LOADED"), ("C3_UNKNOWN", "RECOVERY_LOADED"),
                              ("C5_UNKNOWN", "RECOVERY_LOADED"), ("PARTIAL_C_SAVED", "C5_UNKNOWN")):
            result, started = self.run_lines(cut, recovery)
            self.assertEqual("PASS", result["status"], result)
            self.assertEqual(["start", "recovery-cut", "recover1", "recover2"], started)
            self.assertEqual(["CUT", "CUT", "STABLE", "STABLE"], [row["status"] for row in result["stages"]])

    def test_absent_or_disabled_flag_keeps_default_rejection(self):
        for opt_in in (None, False):
            result, started = self.run_lines("C1_UNKNOWN", "RECOVERY_LOADED", opt_in=opt_in)
            self.assertEqual("FAIL", result["status"])
            self.assertIn("ERROR/FATAL", result["error"])
            self.assertEqual(["start"], started)
            self.assertEqual([], result["stages"])

    def test_controls_still_complete_with_flag(self):
        for cut in ("C1_MOVE_AFTER", "C5_ACK"):
            result, started = self.run_lines(cut, "RECOVERY_LOADED")
            self.assertEqual("PASS", result["status"], result)
            self.assertEqual(4, len(started))

    def test_unknown_allowance_does_not_leak_to_stable_restart(self):
        result, started = self.run_lines("C1_UNKNOWN", "RECOVERY_LOADED", alter=lambda stage, lines, observed:
                                        ([ERROR], events("C1_UNKNOWN")) if stage == "recover1" else (lines, observed))
        self.assertEqual("FAIL", result["status"])
        self.assertIn("ERROR/FATAL", result["error"])
        self.assertEqual(["start", "recovery-cut", "recover1"], started)
        self.assertEqual(2, len(result["stages"]))

    def test_recovery_requires_its_own_matching_event(self):
        result, started = self.run_lines("PARTIAL_C_SAVED", "C5_UNKNOWN", alter=lambda stage, lines, observed:
                                        (lines, events("C1_UNKNOWN")) if stage == "recovery-cut" else (lines, observed))
        self.assertEqual("FAIL", result["status"])
        self.assertIn("UNKNOWN error/observation", result["error"])
        self.assertEqual(["start", "recovery-cut"], started)
        self.assertEqual(1, len(result["stages"]))


if __name__ == "__main__":
    unittest.main()
