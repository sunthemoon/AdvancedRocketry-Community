import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w158_release_cuts as runner


class ReleaseCutRunnerTests(unittest.TestCase):
    def test_restart_has_one_exact_outbox_baseline_confirmation_hold(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED\n"
        runner.audit_log([line], "H1", "C6", "check")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([], "H1", "C6", "check")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line, line], "H1", "C6", "check")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line.replace("SOURCE_UNCONFIRMED", "SOURCE_UNAVAILABLE")], "H1", "C6", "check")

    def test_source_hold_has_one_exact_existing_recovery_warning(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_TRANSFER_RECOVERY transfer=00000000-0000-4000-8000-000000000001 phase=PREPARED source_count=1 destination_count=0 action=KEEP_SOURCE status=WAITING_FOR_PASSENGERS\n"
        runner.audit_log([line], "H1", "C0", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line, line], "H1", "C0", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line.replace("KEEP_SOURCE", "REBUILD_SOURCE")], "H1", "C0", "seed")

    def test_exact_gone_line_does_not_hide_another_warning_or_a_duplicate(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
        message = "ARCE_REJOIN_RELEASE_HELD release=00000000-0000-4000-8000-000000000001 transfer=00000000-0000-4000-8000-000000000002 logical=00000000-0000-4000-8000-000000000003 state=GONE\n"
        baseline = line + "ARCE_TRANSFER_RECOVERY transfer=00000000-0000-4000-8000-000000000002 phase=PREPARED source_count=1 destination_count=0 action=KEEP_SOURCE status=WAITING_FOR_PASSENGERS\n"
        runner.audit_log([baseline, line + message], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline] + [line + message] * 2, "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, line + message.replace("GONE", "CAPTURE_FAILED")], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, line + message, line + "unrelated failure\n"], "H1", "C14", "seed")

    def test_campaign_omits_placement_but_requires_two_restarts_per_cut(self):
        self.assertEqual(3, len(runner.MODES))
        self.assertEqual({f"C{i}" for i in range(7)}, {c for h, c in runner.CASES if h == "H1" and int(c[1:]) < 7})
        self.assertFalse(any(c in {f"C{i}" for i in range(7, 12)} for _, c in runner.CASES))


if __name__ == "__main__":
    unittest.main()
