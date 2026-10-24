import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w158_release_cuts as runner


class ReleaseCutRunnerTests(unittest.TestCase):
    def test_gone_restarts_preserve_the_frozen_resource_witness(self):
        import copy
        disk = {"rockets": [], "journal": {"transfers": []}, "sidecar": {"next_sequence": 1,
                "batches": [{"resources": {"snapshot": "original snapshot", "fuel": "original vector"}}],
                "entries": [{"seat": "VERIFIED", "placement": "PENDING"}] * 2}}
        witness = runner.verify(disk, "H1", "C14", "seed", None)
        self.assertIsNotNone(witness)
        runner.verify(disk, "H1", "C14", "recover", witness)
        changed = copy.deepcopy(disk); changed["sidecar"]["batches"][0]["resources"]["fuel"] = "changed"
        with self.assertRaises(runner.smoke.SmokeError):
            runner.verify(changed, "H1", "C14", "check", witness)

    def test_explicit_deletion_names_the_existing_retirement_warning(self):
        prefix = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
        transfer = "00000000-0000-4000-8000-000000000001"
        lines = [prefix + f"ARCE_TRANSFER_RECOVERY transfer={transfer} phase=PREPARED source_count=1 destination_count=0 action=KEEP_SOURCE status=WAITING_FOR_PASSENGERS\n",
                 prefix + f"ARCE_REJOIN_RELEASE_HELD release={transfer} transfer={transfer} logical={transfer} state=GONE\n",
                 prefix + f"ARCE_TRANSFER_RETIREMENT_ABANDONED transfer={transfer} logical={transfer} site=RECOVERED_SOURCE reason=SURVIVOR_GONE\n"]
        runner.audit_log(lines, "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log(lines + [lines[-1]], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log(lines[:-1] + [lines[-1].replace("SURVIVOR_GONE", "OTHER")], "H1", "C14", "seed")

    def test_controlled_fixture_requires_one_exact_outbox_baseline_confirmation_hold(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED\n"
        runner.audit_log([line], "H1", "C6", "check", expected_outbox_holds=1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([], "H1", "C6", "check", expected_outbox_holds=1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line, line], "H1", "C6", "check", expected_outbox_holds=1)
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line.replace("SOURCE_UNCONFIRMED", "SOURCE_UNAVAILABLE")], "H1", "C6", "check", expected_outbox_holds=1)

    def test_native_startup_outbox_gate_is_not_widened(self):
        line = "[01:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED\n"
        runner.audit_log([], "H2", "C6", "check")
        runner.audit_log([line], "H2", "C6", "check")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([line, line], "H2", "C6", "check")

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
        retired = line + "ARCE_TRANSFER_RETIREMENT_ABANDONED transfer=00000000-0000-4000-8000-000000000002 logical=00000000-0000-4000-8000-000000000003 site=RECOVERED_SOURCE reason=SURVIVOR_GONE\n"
        runner.audit_log([baseline, retired, line + message], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, retired] + [line + message] * 2, "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, retired, line + message.replace("GONE", "CAPTURE_FAILED")], "H1", "C14", "seed")
        with self.assertRaises(runner.smoke.SmokeError):
            runner.audit_log([baseline, retired, line + message, line + "unrelated failure\n"], "H1", "C14", "seed")

    def test_campaign_omits_placement_but_requires_two_restarts_per_cut(self):
        self.assertEqual(3, len(runner.MODES))
        self.assertEqual({f"C{i}" for i in range(7)}, {c for h, c in runner.CASES if h == "H1" and int(c[1:]) < 7})
        self.assertFalse(any(c in {f"C{i}" for i in range(7, 12)} for _, c in runner.CASES))


if __name__ == "__main__":
    unittest.main()
