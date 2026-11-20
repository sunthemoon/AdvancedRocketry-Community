"""Lock-yield orchestration with local fake locks and clocks; never start a JVM."""
import argparse
from contextlib import redirect_stderr, redirect_stdout
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_integrated_campaign as campaign


class FakeClock:
    def __init__(self):
        self.now = 0
        self.sleeps = []
        self.on_sleep = lambda seconds: None

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.now += seconds
        self.on_sleep(seconds)

    def utc_now(self):
        return datetime.fromtimestamp(self.now, timezone.utc).isoformat()


class FakeLock:
    def __init__(self):
        self.owner = None
        self.acquisitions = []

    def acquire(self, owner, wait=None):
        assert self.owner is None
        self.owner = owner
        self.acquisitions.append(owner)
        wait.update(seconds=0, owners=[])

    def release(self, owner):
        assert self.owner == owner
        self.owner = None


class CampaignYieldTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.clock = FakeClock()
        self.lock = FakeLock()
        self.lock_path = self.root / "fake-heavy-lock"
        self.local_lock = campaign.HeavyLock(self.lock_path)
        self.start_patch(campaign, "resolve_commit", return_value="a" * 40)
        self.start_patch(campaign, "preflight", return_value=dict(status="PASS", production_diff_exit=0))
        self.lock_factory = self.start_patch(campaign, "HeavyLock", return_value=self.lock)
        self.run = self.start_patch(campaign, "run_command", return_value=0)
        self.start_patch(campaign.time, "monotonic", side_effect=lambda: self.clock.now)
        self.start_patch(campaign.time, "sleep", side_effect=self.clock.sleep)
        self.start_patch(campaign, "utc_now", side_effect=self.clock.utc_now)
        self.start_patch(campaign, "atomic_mkdir", side_effect=self.atomic_mkdir)
        # Separate monotonic timeout testing from the lock's wall-clock stale check.
        self.start_patch(campaign.time, "time", return_value=0)

    def start_patch(self, target, name, **kwargs):
        patcher = patch.object(target, name, **kwargs)
        self.addCleanup(patcher.stop)
        return patcher.start()

    @staticmethod
    def atomic_mkdir(path):
        try:
            path.mkdir()
            return True
        except FileExistsError:
            return False

    def arguments(self, **changes):
        values = dict(candidate_commit="b" * 40, jar=Path("placeholder.jar"), jar_sha256="c" * 64,
                      template=Path("placeholder-server"), evidence_root=self.root / "evidence",
                      only=["forced-A", "released", "exit"], suffix="yield-unit", dry_run=False, java="fake-java",
                      old_jar=None, old_source_commit=None, downgrade_old_jar=None, downgrade_old_jar_sha256=None)
        values.update(changes)
        return argparse.Namespace(**values)

    def execute(self, **changes):
        args = self.arguments(**changes)
        with redirect_stdout(io.StringIO()):
            result = campaign.run_campaign(args)
        summary = json.loads((args.evidence_root / "campaign-yield-unit.json").read_text(encoding="utf-8"))
        plan = json.loads((args.evidence_root / "plan.json").read_text(encoding="utf-8"))
        for row in summary["families"]:
            invocation = args.evidence_root / (row["name"] + "-yield-unit") / "invocation.json"
            self.assertEqual(json.loads(invocation.read_text(encoding="utf-8")), row)
        return result, summary, plan

    def test_default_zero_adds_no_sleep_and_is_in_plan_and_each_identity(self):
        result, summary, plan = self.execute()
        self.assertEqual(result, 0)
        self.assertEqual(self.clock.sleeps, [])
        self.assertEqual(len(self.lock.acquisitions), 3)
        self.assertEqual(plan["yield_seconds"], 0)
        self.assertEqual(summary["yield_seconds"], 0)
        for row in summary["families"]:
            self.assertEqual(row["yield_seconds"], 0)
            self.assertNotIn("yield", row)

    def test_yield_sleeps_once_after_each_release_but_not_after_last_family(self):
        def during_sleep(seconds):
            self.assertIsNone(self.lock.owner)
            summary = json.loads((self.root / "evidence/campaign-yield-unit.json").read_text(encoding="utf-8"))
            row = summary["families"][-1]
            self.assertTrue(row["lock_released"])
            self.assertIn("started", row["yield"])
            self.assertNotIn("ended", row["yield"])
        self.clock.on_sleep = during_sleep
        result, summary, plan = self.execute(yield_seconds=120)
        self.assertEqual(result, 0)
        self.assertEqual(self.clock.sleeps, [120, 120])
        self.assertEqual(plan["yield_seconds"], 120)
        self.assertEqual(summary["yield_seconds"], 120)
        self.assertEqual([row["name"] for row in summary["families"]], ["forced-A", "released", "exit"])
        for index, row in enumerate(summary["families"]):
            self.assertEqual(row["yield_seconds"], 120)
            if index == 2:
                self.assertNotIn("yield", row)
            else:
                self.assertEqual(row["yield"], dict(started=datetime.fromtimestamp(index * 120, timezone.utc).isoformat(),
                                 ended=datetime.fromtimestamp((index + 1) * 120, timezone.utc).isoformat(),
                                 seconds=120, requested_seconds=120))

    def foreign_owner_during_yield(self, seconds):
        if seconds == 120:
            self.assertFalse(self.lock_path.exists())
            self.lock_path.mkdir()
            (self.lock_path / "owner.txt").write_text("W999 pid=123 token timestamp\n", encoding="utf-8-sig")

    def test_foreign_owner_taking_lock_during_yield_is_waited_for_and_recorded(self):
        self.lock_factory.return_value = self.local_lock
        def during_sleep(seconds):
            self.foreign_owner_during_yield(seconds)
            if self.clock.now == 240:
                (self.lock_path / "owner.txt").unlink()
                self.lock_path.rmdir()
        self.clock.on_sleep = during_sleep
        result, summary, _ = self.execute(yield_seconds=120, only=["forced-A", "released"])
        self.assertEqual(result, 0)
        self.assertEqual(self.clock.sleeps, [120, 60, 60])
        self.assertEqual(summary["families"][1]["lock_wait"], dict(
            started=datetime.fromtimestamp(120, timezone.utc).isoformat(),
            ended=datetime.fromtimestamp(240, timezone.utc).isoformat(),
            seconds=120, owners=["W999 pid=123 token timestamp"]))
        self.assertFalse(self.lock_path.exists())

    def test_foreign_wait_still_times_out_at_three_hours_and_retains_owner(self):
        self.assertEqual(campaign.LOCK_LIMIT, 10800)
        self.lock_factory.return_value = self.local_lock
        self.clock.on_sleep = self.foreign_owner_during_yield
        result, summary, _ = self.execute(yield_seconds=120, only=["forced-A", "released"])
        self.assertEqual(result, 1)
        row = summary["families"][1]
        self.assertEqual(row["status"], "DRIVER_FAILED")
        self.assertIn("wait exceeded three hours", row["error"])
        self.assertEqual(row["lock_wait"]["seconds"], 10800)
        self.assertEqual(row["lock_wait"]["owners"], ["W999 pid=123 token timestamp"])
        self.assertEqual(self.clock.sleeps, [120] + [60] * 180)
        self.assertEqual(self.run.call_count, 1)
        self.assertFalse(row["lock_acquired"])
        self.assertEqual((self.lock_path / "owner.txt").read_text(encoding="utf-8-sig"),
                         "W999 pid=123 token timestamp\n")

    def test_stale_lock_during_yield_is_refused_without_removal_or_runner(self):
        self.lock_factory.return_value = self.local_lock
        def during_sleep(seconds):
            self.foreign_owner_during_yield(seconds)
            self.start_patch(campaign.time, "time", return_value=self.lock_path.stat().st_mtime + 10800)
        self.clock.on_sleep = during_sleep
        result, summary, _ = self.execute(yield_seconds=120, only=["forced-A", "released"])
        self.assertEqual(result, 1)
        row = summary["families"][1]
        self.assertIn("over three hours old", row["error"])
        self.assertEqual(row["lock_wait"]["seconds"], 0)
        self.assertFalse(row["lock_acquired"])
        self.assertEqual(self.run.call_count, 1)
        self.assertEqual(self.clock.sleeps, [120])
        self.assertTrue((self.lock_path / "owner.txt").exists())

    def test_one_family_never_yields_even_at_maximum(self):
        result, summary, _ = self.execute(yield_seconds=600, only=["released"])
        self.assertEqual(result, 0)
        self.assertEqual(self.clock.sleeps, [])
        self.assertNotIn("yield", summary["families"][0])

    def test_dry_run_records_yield_without_sleep_or_lock(self):
        result, summary, plan = self.execute(yield_seconds=90, dry_run=True)
        self.assertEqual(result, 0)
        self.assertEqual(plan["yield_seconds"], 90)
        self.assertTrue(all(row["yield_seconds"] == 90 for row in summary["families"]))
        self.assertEqual(self.clock.sleeps, [])
        self.lock_factory.assert_not_called()
        self.run.assert_not_called()

    def test_yield_does_not_change_any_family_or_child_command(self):
        with patch.object(campaign.uuid, "uuid4", return_value=Mock(hex="fixed-token")):
            before = campaign.make_plan(self.arguments(only=None))
            after = campaign.make_plan(self.arguments(only=None, yield_seconds=120))
        self.assertEqual(before["families"], after["families"])
        self.assertEqual(before["requirements"], after["requirements"])

    def test_cli_parses_yield_and_rejects_out_of_range_or_non_integer(self):
        argv = ["--candidate-commit", "HEAD", "--jar", "missing.jar", "--jar-sha256", "c" * 64,
                "--template", "missing", "--evidence-root", str(self.root / "cli"), "--dry-run"]
        with patch.object(campaign, "run_campaign", return_value=0) as run:
            self.assertEqual(campaign.main(argv), 0)
            self.assertEqual(run.call_args.args[0].yield_seconds, 0)
            self.assertEqual(campaign.main(argv + ["--yield-seconds", "600"]), 0)
            self.assertEqual(run.call_args.args[0].yield_seconds, 600)
        for invalid in (-1, 601, 1.5):
            with self.subTest(value=invalid), self.assertRaisesRegex(ValueError, "integer from 0 to 600"):
                campaign.make_plan(self.arguments(yield_seconds=invalid))
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as failure:
            campaign.main(argv + ["--yield-seconds", "1.5"])
        self.assertEqual(failure.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
