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
        self.assertEqual(4, sum(len(cases) for cases in runner.FAMILIES.values()))
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

    def watch(self, directory, kind, process, prepare=lambda world: None, timeout=30):
        world = Path(directory) / "world"; (world / "data").mkdir(parents=True)
        prepare(world)
        run = types.SimpleNamespace(args=argparse.Namespace(startup_timeout=timeout), process=process)
        return runner.watch_and_kill(run, process, world, kind), run

    def test_the_watcher_kills_at_the_first_sight_of_the_upgrade_and_says_when_it_missed_it(self):
        done = PREFIX.format("INFO") + "[ARCE-BETA-1001] Beta world data check complete: managed=3, migrated=1, backup=b"
        with tempfile.TemporaryDirectory() as directory:
            process = Process()
            seen, run = self.watch(directory, "BACKUP", process, lambda world: (world / runner.BACKUPS).mkdir())
            self.assertEqual(("BACKUP_DIRECTORY", True, None), (seen, process.killed, run.process))
        with tempfile.TemporaryDirectory() as directory:
            process = Process()
            seen, _ = self.watch(directory, "COMMIT", process, lambda world: (world / "data/.x.migrating-1").write_bytes(b""))
            self.assertEqual(("STAGED_FILE", True), (seen, process.killed))
        with tempfile.TemporaryDirectory() as directory:
            # A backup directory alone is not the staged file.
            process = Process([done])
            seen, _ = self.watch(directory, "COMMIT", process, lambda world: (world / runner.BACKUPS).mkdir())
            self.assertEqual(("NOT_SEEN_BEFORE_COMPLETION", True), (seen, process.killed))
        for process in (Process(code=1), Process()):
            with tempfile.TemporaryDirectory() as directory, self.assertRaisesRegex(SmokeError, "before the upgrade was observed"):
                self.watch(directory, "BACKUP", process, timeout=0 if process.code is None else 30)

    def test_a_missed_upgrade_is_not_covered_and_a_family_with_it_is_not_a_pass(self):
        base = dict(backup=None, ledger_sha256="aa", digests=["d"], hashes={}, archive={})
        state = dict(files={}, migration=dict(backups={"b": {}}, staged=[]))

        def start(run, label, world, base, kill=False):
            return dict(label=label, migration=dict(migrated=0, backup="none"), state=state)

        def case(seen, ledger_sha):
            run = types.SimpleNamespace(session=Path("unused"), start=lambda *a, **k: Process(), process=None,
                                        capture=lambda world, label: dict(file=label))
            with patch.object(runner, "copy_world", lambda *a: None), patch.object(runner, "watch_and_kill", lambda *a: seen), \
                    patch.object(runner.smoke, "digest_file", lambda path: ledger_sha), patch.object(runner, "candidate_start", start), \
                    patch.object(runner, "migration_files", lambda world: dict(backups={}, staged=[])), \
                    patch.object(runner, "check_upgraded", lambda world, base: state), \
                    patch.object(runner, "read", lambda world: dict(ledger=dict())), \
                    patch.object(runner.oracle, "check_root2", lambda root, digests: None):
                return runner.upgrade_case(run, "UPGRADE_DURING_COMMIT", base)

        missed = case("NOT_SEEN_BEFORE_COMPLETION", "bb")
        self.assertEqual(("NOT_COVERED", "AFTER_COMMIT"), (missed["status"], missed["starts"][0]["landed"]))
        self.assertEqual("PASS", case("STAGED_FILE", "bb")["status"])
        # Landing before the commit leaves root 2, so the first restart has to migrate; this stand-in never does.
        before = case("STAGED_FILE", "aa")
        self.assertEqual("BEFORE_COMMIT", before["starts"][0]["landed"])
        self.assertEqual("FAILED", before["status"]); self.assertIn("expected 1", before["findings"][0])
        with patch.object(runner, "seed", lambda run: dict(base, ledger_sha256="aa")), \
                patch.object(runner, "upgrade_case", lambda run, name, base: dict(case=name, findings=[], status="NOT_COVERED" if "COMMIT" in name else "PASS")), \
                patch.object(runner.native, "write_json", lambda *a: None):
            run = types.SimpleNamespace(args=argparse.Namespace(case=None, family="root2-upgrade"), evidence=Path("unused"))
            self.assertEqual("FAILED", runner.family(run)["status"])
            run.args.case = ["OLD_JAR_REFUSAL"]
            with self.assertRaisesRegex(ValueError, "not in the requested family"): runner.family(run)


if __name__ == "__main__":
    unittest.main()
