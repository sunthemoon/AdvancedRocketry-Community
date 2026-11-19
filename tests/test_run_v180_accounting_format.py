"""W373B's format-boundary runner: log patterns, the migrator's leftovers, the watcher and R-044's verdict, without a JVM."""
import argparse
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_accounting_format as runner
from run_dedicated_server_smoke import SmokeError

REPO = Path(__file__).resolve().parents[1]
PREFIX = "[12:00:00] [Server thread/{}] [io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/]: "


class Process:
    """A stand-in for the captured server process: lines, a poll result, and a recorded kill."""
    def __init__(self, lines=(), code=None):
        self.lines = list(lines); self.code = code; self.killed = False
        self.process = types.SimpleNamespace(poll=lambda: self.code, kill=self.kill)

    def kill(self): self.killed = True
    def abort(self): self.aborted = True


class AccountingFormatRunnerTest(unittest.TestCase):
    def test_the_root2_reader_is_pinned_to_candidate_c3(self):
        report = (REPO / "docs/work/v1.8.0-root-queue/records/C3-SWITCH-REPORT.md").read_text(encoding="utf-8")
        self.assertIn("SHA-256\n  `" + runner.ROOT2_SHA256 + "`", report)
        self.assertIn("The commit is `" + runner.ROOT2_COMMIT + "`", report)
        self.assertEqual("FC_LOAD_L1_ACK", runner.SEED_CASE)
        self.assertEqual(5, sum(len(cases) for cases in runner.FAMILIES.values()))
        # Its cases are the runner's own labels; the only probe case it starts is the existing seed case.
        self.assertNotIn("-Dadvancedrocketrycommunity.escrowCase=",
                         (REPO / "scripts/run_v180_accounting_format.py").read_text(encoding="utf-8"))

    def test_the_migration_report_is_read_once_per_start(self):
        line = PREFIX.format("INFO") + "[ARCE-BETA-1001] Beta world data check complete: managed=3, migrated=1, backup=20261119T030000Z-managed"
        self.assertEqual(dict(code="ARCE-BETA-1001", managed=3, migrated=1, backup="20261119T030000Z-managed"), runner.migrated(["x", line]))
        self.assertEqual(0, runner.migrated([line.replace("migrated=1, backup=20261119T030000Z-managed", "migrated=0, backup=none")])["migrated"])
        self.assertIsNone(runner.migrated(["Done (1.0s)!"]))
        with self.assertRaisesRegex(ValueError, "more than once"): runner.migrated([line, line])

    def test_the_refusal_line_is_read_in_both_logged_forms_and_only_at_error_level(self):
        typed = PREFIX.format("ERROR") + ("[ARCE-BETA-2001] Beta world data check blocked startup; type=RESOURCE_ESCROW; reason="
                 "advancedrocketrycommunity_resource_escrow uses future schema 3. Keep the world stopped.")
        plain = PREFIX.format("ERROR") + "[ARCE-BETA-2001] Beta world data check blocked startup: advancedrocketrycommunity_rocket_transfers uses future schema 3. Keep"
        match = runner.REFUSAL.search(typed)
        self.assertEqual("ARCE-BETA-2001", match[1]); self.assertIn(runner.DESIGNED_REFUSAL, match[2])
        other = runner.REFUSAL.search(plain)
        self.assertIsNotNone(other); self.assertNotIn(runner.DESIGNED_REFUSAL, other[2])
        self.assertIsNone(runner.REFUSAL.search(typed.replace("/ERROR]", "/INFO]")))
        self.assertIsNone(runner.REFUSAL.search(PREFIX.format("ERROR") + "[ARCE-BETA-1001] Beta world data check complete: managed=1"))

    def test_r044_verdict_names_every_changed_file_and_is_never_waived(self):
        clean = dict(refused=True, designed_reason=True, reason="x", ledger_byte_equal=True, files=dict(changed=[], added=[], removed=[]))
        self.assertEqual([], runner.refusal_findings([clean, clean, clean]))
        rewritten = dict(clean, files=dict(changed=["level.dat"], added=["level.dat_old"], removed=[]))
        self.assertEqual(["old JAR start 1: the refusal changed world files: level.dat, level.dat_old"],
                         runner.refusal_findings([clean, rewritten]))
        self.assertEqual(["old JAR start 0: the world loaded instead of being refused"],
                         runner.refusal_findings([dict(clean, refused=False, designed_reason=False)]))
        self.assertEqual(["old JAR start 0: refused for another reason: journal"],
                         runner.refusal_findings([dict(clean, designed_reason=False, reason="journal")]))
        downgraded = dict(clean, ledger_byte_equal=False, files=dict(changed=["data/ledger.dat"], added=[], removed=["x"]))
        self.assertEqual(["old JAR start 0: the ledger file changed", "old JAR start 0: the refusal changed world files: data/ledger.dat, x"],
                         runner.refusal_findings([downgraded]))

    def test_migration_leftovers_and_world_copies_are_listed_from_files(self):
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory) / "world"; (world / "data").mkdir(parents=True)
            self.assertEqual(dict(backups={}, staged=[]), runner.migration_files(world))
            backup = world / runner.BACKUPS / "20261119T030000Z-managed"; backup.mkdir(parents=True)
            (backup / "ledger.dat").write_bytes(b"root2"); (backup / "manifest.json").write_bytes(b"{}")
            (world / "data" / ".advancedrocketrycommunity_resource_escrow.migrating-1234").write_bytes(b"x")
            (world / "data" / "other.dat").write_bytes(b"y"); (world / "session.lock").write_bytes(b"lock")
            found = runner.migration_files(world)
            self.assertEqual(["20261119T030000Z-managed"], list(found["backups"]))
            self.assertEqual(["ledger.dat", "manifest.json"], sorted(found["backups"]["20261119T030000Z-managed"]))
            self.assertEqual([".advancedrocketrycommunity_resource_escrow.migrating-1234"], found["staged"])
            copy = Path(directory) / "copy"
            runner.copy_world(world, copy)
            self.assertFalse((copy / "session.lock").exists())
            self.assertEqual(b"y", (copy / "data/other.dat").read_bytes())
            with self.assertRaisesRegex(ValueError, "Unsafe world copy"): runner.copy_world(world, copy)

    def test_settling_loads_both_endpoints_waits_and_asks_the_server_for_its_classification(self):
        calls = []; ticks = iter([1000, 1100, 1250, 1260])

        def send(value, marker=None, timeout=60):
            calls.append((value, getattr(marker, "pattern", marker)))
            return "[12:00:00] [Server thread/INFO]: Escrow operation 1 phase=PREPARED accounting=UNKNOWN_BASELINE\n"

        run = types.SimpleNamespace(load=lambda positions: calls.append(("load", positions)), tick=lambda: next(ticks), send=send)
        with patch.object(runner.time, "sleep", lambda seconds: None):
            hold = runner.settle(run, "00000000-0000-0000-0000-000000000001")
        self.assertEqual(("load", runner.FIXTURE), calls[0])
        self.assertEqual(("arce escrow inspect 00000000-0000-0000-0000-000000000001", runner.HELD.pattern), calls[1])
        self.assertEqual([("forceload remove 64 64", None), ("save-all flush", runner.native.SAVED.pattern)], calls[2:])
        self.assertEqual(260, hold["ticks"]); self.assertIn("accounting=UNKNOWN_BASELINE", hold["inspect"])
        self.assertIsNotNone(runner.HELD.search("x accounting=UNKNOWN_BASELINE"))
        for other in ("accounting=UNKNOWN", "accounting=UNKNOWN_BASELINE_X", "accounting=KNOWN"):
            self.assertIsNone(runner.HELD.search(other))
        stuck = types.SimpleNamespace(load=lambda positions: None, tick=lambda: 5, send=send)
        clock = iter([0, 0, 200])
        with patch.object(runner.time, "sleep", lambda seconds: None), patch.object(runner.time, "monotonic", lambda: next(clock)), \
                self.assertRaisesRegex(ValueError, "did not advance"):
            runner.settle(stuck, "x")

    def watch(self, directory, kind, process, prepare=lambda world: None, timeout=30, stale=lambda world: None):
        """`stale` runs before the names are recorded (an older attempt's leftovers); `prepare` after (this attempt's)."""
        world = Path(directory) / "world"; (world / "data").mkdir(parents=True)
        stale(world)
        before = runner.artifacts(world, kind)
        prepare(world)
        run = types.SimpleNamespace(args=argparse.Namespace(startup_timeout=timeout), process=process)
        return runner.watch_and_kill(run, process, world, kind, before), run

    DONE = PREFIX.format("INFO") + "[ARCE-BETA-1001] Beta world data check complete: managed=3, migrated=1, backup=b"
    READY = '[12:00:00] [Server thread/INFO] [minecraft/DedicatedServer]: Done (3.210s)! For help, type "help"'

    def test_the_watcher_kills_at_the_first_sight_of_the_upgrade_and_says_when_it_missed_it(self):
        with tempfile.TemporaryDirectory() as directory:
            process = Process()
            watched, run = self.watch(directory, "BACKUP", process, lambda world: (world / runner.BACKUPS).mkdir())
            self.assertEqual(("BACKUP_DIRECTORY", False, [runner.BACKUPS], True, None),
                             (watched["seen"], watched["completed"], watched["created"], process.killed, run.process))
        with tempfile.TemporaryDirectory() as directory:
            process = Process()
            watched, _ = self.watch(directory, "COMMIT", process, lambda world: (world / "data/.x.migrating-1").write_bytes(b""))
            self.assertEqual(("STAGED_FILE", False, [".x.migrating-1"], True),
                             (watched["seen"], watched["completed"], watched["created"], process.killed))
        with tempfile.TemporaryDirectory() as directory:
            # A backup directory alone is not the staged file.
            process = Process([self.DONE])
            watched, _ = self.watch(directory, "COMMIT", process, lambda world: (world / runner.BACKUPS).mkdir())
            self.assertEqual(("NOT_SEEN_BEFORE_COMPLETION", True, True), (watched["seen"], watched["completed"], process.killed))
        for process in (Process(code=1), Process()):
            with tempfile.TemporaryDirectory() as directory, self.assertRaisesRegex(SmokeError, "before the upgrade was observed"):
                self.watch(directory, "BACKUP", process, timeout=0 if process.code is None else 30)
        with self.assertRaisesRegex(ValueError, "Unknown upgrade watcher kind"): runner.artifacts(Path("unused"), "OTHER")

    def test_a_completed_migration_is_never_a_stop_during_it_whatever_artifact_exists(self):
        """W373C, review finding 1: completion is checked before the artifact, and again after the kill."""
        ready = '[12:00:00] [Server thread/INFO] [minecraft/DedicatedServer]: Done (3.210s)! For help, type "help"'
        self.assertIsNotNone(runner.smoke.READY_MARKER.search(ready))
        backup = lambda world: (world / runner.BACKUPS / "20261119T030000Z-managed").mkdir(parents=True)
        staged = lambda world: (world / "data/.advancedrocketrycommunity_resource_escrow.migrating-1").write_bytes(b"x")
        for line in (self.DONE, ready):
            # Completion with the backup directory that every successful migration leaves behind.
            with tempfile.TemporaryDirectory() as directory:
                process = Process([line])
                watched, _ = self.watch(directory, "BACKUP", process, backup)
                self.assertEqual(("NOT_SEEN_BEFORE_COMPLETION", True, [], True),
                                 (watched["seen"], watched["completed"], watched["created"], process.killed))
            # Completion with a staged file left by an earlier interrupted attempt.
            with tempfile.TemporaryDirectory() as directory:
                process = Process([line])
                watched, _ = self.watch(directory, "COMMIT", process, stale=staged)
                self.assertEqual(("NOT_SEEN_BEFORE_COMPLETION", True, [".advancedrocketrycommunity_resource_escrow.migrating-1"]),
                                 (watched["seen"], watched["completed"], watched["existed_before"]))
        # A stale artifact alone is never this attempt's: the watcher keeps waiting and then reports no observation.
        for kind, stale in (("BACKUP", backup), ("COMMIT", staged)):
            with tempfile.TemporaryDirectory() as directory, self.assertRaisesRegex(SmokeError, "before the upgrade was observed"):
                self.watch(directory, kind, Process(), stale=stale, timeout=0)
        # A new backup directory beside an older one, and a new staged file beside a stale one, are this attempt's.
        with tempfile.TemporaryDirectory() as directory:
            watched, _ = self.watch(directory, "BACKUP", Process(), stale=backup,
                                    prepare=lambda world: (world / runner.BACKUPS / "20261119T040000Z-managed").mkdir())
            self.assertEqual(("BACKUP_DIRECTORY", [runner.BACKUPS + "/20261119T040000Z-managed"], False),
                             (watched["seen"], watched["created"], watched["completed"]))
        with tempfile.TemporaryDirectory() as directory:
            watched, _ = self.watch(directory, "COMMIT", Process(), stale=staged,
                                    prepare=lambda world: (world / "data/.x.migrating-2").write_bytes(b""))
            self.assertEqual(("STAGED_FILE", [".x.migrating-2"]), (watched["seen"], watched["created"]))

        # The artifact is seen first and the completion line arrives while the process is being killed.
        class Late(Process):
            def kill(self):
                super().kill(); self.lines.append(AccountingFormatRunnerTest.DONE)

        with tempfile.TemporaryDirectory() as directory:
            process = Late()
            watched, _ = self.watch(directory, "BACKUP", process, backup)
            self.assertEqual(("BACKUP_DIRECTORY", True, True), (watched["seen"], watched["completed"], process.killed))

    def case(self, watched, ledger_sha, lines=(), root2=None, case="UPGRADE_DURING_COMMIT"):
        """One upgrade case with stand-ins for the native starts; `watched` is the watcher's report."""
        base = dict(backup=None, ledger_sha256="aa", digests=["d"], hashes={}, archive={})
        state = dict(files={}, migration=dict(backups={"b": {}}, staged=[]))
        start = lambda run, label, world, base, kill=False: dict(label=label, migration=dict(migrated=0, backup="none"), state=state)
        accept = lambda root, digests, source, destination: dict(source=source, destination=destination)
        with tempfile.TemporaryDirectory() as directory:
            run = types.SimpleNamespace(session=Path(directory) / "session", evidence=Path(directory), process=None,
                                        start=lambda *a, **k: Process(lines), capture=lambda world, label: dict(file=label))
            disk = dict(ledger=dict(root="2"), device=dict(fluids={"minecraft:water": 5000}, epoch=0, markers=0),
                        rocket=dict(fluids={}, epoch=0, markers=0))
            with patch.object(runner, "copy_world", lambda *a: None), patch.object(runner, "watch_and_kill", lambda *a: dict(watched)), \
                    patch.object(runner, "artifacts", lambda world, kind: set()), \
                    patch.object(runner.smoke, "digest_file", lambda path: "raw-sha" if Path(path).suffix == ".json" else ledger_sha), \
                    patch.object(runner, "candidate_start", start), \
                    patch.object(runner, "migration_files", lambda world: dict(backups={}, staged=[])), \
                    patch.object(runner, "check_upgraded", lambda world, base: state), \
                    patch.object(runner, "read", lambda world: disk), \
                    patch.object(runner.oracle, "check_root2", root2 or accept):
                row = runner.upgrade_case(run, case, base)
                row["raw_files"] = sorted(p.name for p in Path(directory).glob("*-raw.json"))
                return row

    def test_a_missed_upgrade_is_not_covered_and_a_family_with_it_is_not_a_pass(self):
        base = dict(backup=None, ledger_sha256="aa", digests=["d"], hashes={}, archive={})
        seen = lambda name, completed=False: dict(seen=name, created=[], existed_before=[], completed=completed)
        missed = self.case(seen("NOT_SEEN_BEFORE_COMPLETION", True), "bb")
        self.assertEqual(("NOT_COVERED", "AFTER_COMMIT"), (missed["status"], missed["starts"][0]["landed"]))
        self.assertEqual("PASS", self.case(seen("STAGED_FILE"), "bb")["status"])
        # Landing before the commit leaves root 2, so the first restart has to migrate; this stand-in never does.
        before = self.case(seen("STAGED_FILE"), "aa")
        self.assertEqual("BEFORE_COMMIT", before["starts"][0]["landed"])
        self.assertEqual("FAILED", before["status"]); self.assertIn("expected 1", before["findings"][0])
        with patch.object(runner, "seed", lambda run: dict(base, ledger_sha256="aa")), \
                patch.object(runner, "upgrade_case", lambda run, name, base: dict(case=name, findings=[], status="NOT_COVERED" if "COMMIT" in name else "PASS")), \
                patch.object(runner.native, "write_json", lambda *a: None):
            run = types.SimpleNamespace(args=argparse.Namespace(case=None, family="root2-upgrade"), evidence=Path("unused"))
            self.assertEqual("FAILED", runner.family(run)["status"])
            run.args.case = ["OLD_JAR_REFUSAL"]
            with self.assertRaisesRegex(ValueError, "not in the requested family"): runner.family(run)

    def test_a_completed_first_start_is_not_covered_in_both_during_cases(self):
        """W373C, review finding 1 at the case level: an observed artifact does not turn a completed migration into a cut."""
        seen = lambda name, completed: dict(seen=name, created=["x"], existed_before=[], completed=completed)
        for case, artifact in (("UPGRADE_DURING_BACKUP", "BACKUP_DIRECTORY"), ("UPGRADE_DURING_COMMIT", "STAGED_FILE")):
            with self.subTest(case=case):
                # The reviewer's reproduction: the artifact was seen, the root hash changed and every later state is valid.
                row = self.case(seen(artifact, True), "bb", case=case)
                self.assertEqual(("NOT_COVERED", "AFTER_COMMIT", True),
                                 (row["status"], row["starts"][0]["landed"], row["starts"][0]["completed"]))
                # The watcher's own looks missed it, but the captured log of the killed start holds the line.
                row = self.case(seen(artifact, False), "bb", lines=[self.DONE], case=case)
                self.assertEqual(("NOT_COVERED", True), (row["status"], row["starts"][0]["completed"]))
                # Completion with the ledger still root 2 cannot be a consistent start; it is not a pass either.
                row = self.case(seen(artifact, True), "aa", case=case)
                self.assertNotEqual("PASS", row["status"])
                # Without completion the same observation is a stop during the migration.
                row = self.case(seen(artifact, False), "bb", case=case)
                self.assertEqual(("PASS", False), (row["status"], row["starts"][0]["completed"]))

    def test_startup_watcher_requires_this_attempts_replacement_and_no_staged_file(self):
        def backup(world):
            target = world / runner.BACKUPS / "this-attempt"; target.mkdir(parents=True)
            (target / runner.LEDGER).write_bytes(b"old")
        with tempfile.TemporaryDirectory() as directory, patch.object(runner.cuts, "ledger", return_value=dict(schema_version=3)):
            watched, _ = self.watch(directory, "STARTUP", Process(), prepare=backup)
            self.assertEqual(("ROOT3_REPLACED", False), (watched["seen"], watched["completed"]))
        for schema, staged in ((2, False), (3, True)):
            with tempfile.TemporaryDirectory() as directory, patch.object(runner.cuts, "ledger", return_value=dict(schema_version=schema)):
                def prepare(world):
                    backup(world)
                    if staged: (world / "data/.ledger.migrating-this-attempt").write_bytes(b"staged")
                with self.assertRaisesRegex(SmokeError, "before the upgrade was observed"):
                    self.watch(directory, "STARTUP", Process(), prepare=prepare, timeout=0)
        with tempfile.TemporaryDirectory() as directory, patch.object(runner.cuts, "ledger", return_value=dict(schema_version=3)), \
                self.assertRaisesRegex(SmokeError, "before the upgrade was observed"):
            self.watch(directory, "STARTUP", Process(), stale=backup, timeout=0)
        # R527: a report no longer closes STARTUP, but ready still does. Both earlier cut kinds keep their boundary.
        for lines, ready, report_seen in (([], False, False), ([self.DONE], False, True),
                                          ([self.READY], True, False), ([self.DONE, self.READY], True, True)):
            with self.subTest(lines=lines), tempfile.TemporaryDirectory() as directory, \
                    patch.object(runner.cuts, "ledger", return_value=dict(schema_version=3)):
                watched, _ = self.watch(directory, "STARTUP", Process(lines), prepare=backup)
                self.assertEqual(("NOT_SEEN_BEFORE_COMPLETION" if ready else "ROOT3_REPLACED", ready, report_seen),
                                 (watched["seen"], watched["completed"], watched["report_seen"]))

    def test_startup_report_seen_is_frozen_before_kill_but_late_ready_still_closes_the_window(self):
        def backup(world):
            target = world / runner.BACKUPS / "this-attempt"; target.mkdir(parents=True)
            (target / runner.LEDGER).write_bytes(b"old")

        class LateReport(Process):
            def kill(self):
                super().kill(); self.lines.append(AccountingFormatRunnerTest.DONE)

        class LateReady(Process):
            def abort(self):
                super().abort(); self.lines.append(AccountingFormatRunnerTest.READY)

        for process, expected in ((LateReport(), (False, False)), (LateReady([self.DONE]), (True, True))):
            with tempfile.TemporaryDirectory() as directory, patch.object(runner.cuts, "ledger", return_value=dict(schema_version=3)):
                watched, _ = self.watch(directory, "STARTUP", process, prepare=backup)
                self.assertEqual(expected, (watched["report_seen"], watched["completed"]))
                self.assertTrue(process.killed)

    def test_startup_cut_keeps_exact_migrated_content_and_this_attempts_backup(self):
        import copy
        root2 = runner.cuts.ledger(REPO / "tests/fixtures/escrow_accounting/cargo-root2.dat")
        root2["records"][0]["phase"] = "PREPARED"; root2["record_digests"] = ["unchanged"]
        root3 = dict(copy.deepcopy(root2), schema_version=3, format_epoch=runner.accounting.EPOCHS[3], fluid_accounts=[])
        disk = dict(ledger=root3, device=runner.oracle.store(runner.oracle.WATER, 5000), rocket=runner.oracle.store(), files={})
        base = dict(digests=["unchanged"], ledger_sha256="old-sha")
        first = dict(created=[runner.BACKUPS + "/this-attempt"],
                     migration_files=dict(backups={"this-attempt": {runner.LEDGER: "old-sha"}}, staged=[]))
        self.assertTrue(runner.check_startup_cut(None, base, first, disk)["legacy"])
        with self.assertRaisesRegex(SmokeError, "not root 3"):
            runner.check_startup_cut(None, base, first, dict(disk, ledger=root2))
        for change, message in ((lambda d: d["ledger"].update(record_digests=["changed"]), "bytes changed"),
                                (lambda d: d["device"].update(fluids={runner.oracle.WATER: 4000}), "Raw source amounts")):
            bad = copy.deepcopy(disk); change(bad)
            with self.assertRaisesRegex(SmokeError, message): runner.check_startup_cut(None, base, first, bad)
        for change, message in ((lambda f: f.update(created=[runner.BACKUPS + "/old-attempt"]), "this attempt"),
                                (lambda f: f["migration_files"]["backups"]["this-attempt"].update({runner.LEDGER: "wrong"}), "this attempt"),
                                (lambda f: f["migration_files"].update(staged=[".ledger.migrating-this-attempt"]), "staged")):
            bad = copy.deepcopy(first); change(bad)
            with self.assertRaisesRegex(ValueError, message): runner.check_startup_cut(None, base, bad, disk)

    def test_startup_case_keeps_the_cut_snapshot_and_ready_is_not_coverage(self):
        watched = dict(seen="ROOT3_REPLACED", created=[runner.BACKUPS + "/this-attempt"], existed_before=[], completed=False, report_seen=False)
        checks = []
        with patch.object(runner, "check_startup_cut", lambda world, base, first, disk: checks.append((first, disk)) or dict(migration=dict(backups={}, staged=[]))):
            row = self.case(watched, "bb", case="UPGRADE_DURING_STARTUP")
            self.assertEqual("PASS", row["status"])
            self.assertEqual("AFTER_COMMIT", row["starts"][0]["landed"])
            self.assertEqual(["UPGRADE_DURING_STARTUP-0-raw.json"], row["raw_files"])
            self.assertEqual(1, len(checks))
            for start in row["starts"][1:]: self.assertEqual(0, start["migration"]["migrated"])
            for observed, lines in ((dict(watched, completed=True), ()), (watched, [self.READY]),
                                     (dict(watched, report_seen=True), [self.DONE, self.READY])):
                row = self.case(observed, "bb", lines=lines, case="UPGRADE_DURING_STARTUP")
                self.assertEqual("NOT_COVERED", row["status"])
                self.assertIn("ready line", row["reason"])
            # Report and no-report observations both remain covered before ready, with their exact boolean retained.
            for report_seen in (False, True):
                row = self.case(dict(watched, report_seen=report_seen), "bb", lines=[self.DONE] if report_seen else [], case="UPGRADE_DURING_STARTUP")
                self.assertEqual("PASS", row["status"])
                self.assertIs(row["starts"][0]["report_seen"], report_seen)
                self.assertFalse(row["starts"][0]["completed"])
                self.assertEqual([0, 0], [s["migration"]["migrated"] for s in row["starts"][1:]])
            # A root-2 cut cannot use two non-migrating restarts as proof of a post-replacement stop.
            self.assertEqual("FAILED", self.case(watched, "aa", case="UPGRADE_DURING_STARTUP")["status"])

    def test_the_before_commit_snapshot_is_checked_and_kept(self):
        """W373C, review finding 3: the decoded endpoints of the root-2 cut reach the oracle, and the raw file is kept."""
        seen = dict(seen="STAGED_FILE", created=["x"], existed_before=[], completed=False)
        calls = []

        def root2(root, digests, source, destination):
            calls.append((root, digests, source, destination))
            return dict(checked=True)

        row = self.case(seen, "aa", root2=root2)
        self.assertEqual([(dict(root="2"), ["d"], dict(fluids={"minecraft:water": 5000}, epoch=0, markers=0),
                           dict(fluids={}, epoch=0, markers=0))], calls)
        self.assertEqual(["UPGRADE_DURING_COMMIT-0-raw.json"], row["raw_files"])
        self.assertEqual(dict(file="UPGRADE_DURING_COMMIT-0-raw.json", sha256="raw-sha"), row["starts"][0]["raw"])
        self.assertEqual(dict(checked=True), row["starts"][0]["state"])

        def changed(root, digests, source, destination):
            # The same root-2 ledger with another endpoint amount: the real store check refuses it.
            return runner.oracle.check_store("source", dict(source, fluids={"minecraft:water": 4000}),
                                             runner.oracle.store(runner.oracle.WATER, 5000, 0, 0))

        row = self.case(seen, "aa", root2=changed)
        self.assertEqual("FAILED", row["status"]); self.assertIn("Raw source amounts differ", row["findings"][0])
        # The seed's own check passes its endpoints too.
        source = (REPO / "scripts/run_v180_accounting_format.py").read_text(encoding="utf-8")
        self.assertEqual(1, source.count('oracle.check_root2(disk["ledger"], digests, disk["device"], disk["rocket"])'))
        self.assertEqual(1, source.count('oracle.check_root2(disk["ledger"], base["digests"], disk["device"], disk["rocket"])'))
        self.assertEqual(2, source.count("oracle.check_root2("))


if __name__ == "__main__":
    unittest.main()
