"""Campaign orchestration only: no real git, lock, disk probe, runner or Java."""
import argparse
from collections import Counter
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_integrated_campaign as campaign

SOURCE, CANDIDATE, HASH, OLD_HASH = "a" * 40, "b" * 40, "c" * 64, "d" * 64


def arguments(root, **changes):
    values = dict(candidate_commit=CANDIDATE, jar=Path("placeholder.jar"), jar_sha256=HASH,
                  template=Path("placeholder-server"), evidence_root=root / "evidence", only=None,
                  suffix="unit", dry_run=False, java="java17.exe", old_jar=None, old_source_commit=None,
                  downgrade_old_jar=None, downgrade_old_jar_sha256=None)
    values.update(changes)
    return argparse.Namespace(**values)


class PlanTest(unittest.TestCase):
    def test_every_native_w325_matrix_row_maps_to_a_selected_case(self):
        matrix = (campaign.REPO / "docs/work/v1.8.0-root-queue/records/W325-MATRIX.md").read_text(encoding="utf-8")
        matrix = matrix.split("## GameTest regression mappings")[0]
        mappings = re.findall(r"^\| (\S+) \| [^\n]*?\| ([FRX]) `([^`]+)`", matrix, re.MULTILINE)
        self.assertEqual(len(mappings), 88)
        rows = campaign.family_specs()
        scripts = dict(F="scripts/run_v180_bay_forced_stop.py", R="scripts/run_v180_bay_released_flight.py",
                       X="scripts/run_v180_bay_exit_cuts.py")
        for row, runner, case in mappings:
            self.assertTrue(any(spec["runner"] == scripts[runner] and case in spec["cases"] for spec in rows), row)
        self.assertEqual(set().union(*(set(row["cases"]) for row in rows if row["runner"] == scripts["F"])),
                         set(campaign.forced.CASES) | set(campaign.forced.W331_CASES))

    def test_w331_record_cases_and_groups_are_preserved_without_claiming_its_gaps(self):
        record = (campaign.REPO / "docs/work/v1.8.0-root-queue/records/W331.md").read_text(encoding="utf-8")
        mappings = re.findall(r"^\| (\S+) \| (Reached[^|]*|Partial[^|]*) \| F `([^`]+)`", record, re.MULTILINE)
        self.assertEqual(len(mappings), 19)
        families = {row["name"]: row for row in campaign.family_specs()}
        for tranche, cases in campaign.forced.W331_TRANCHES.items():
            row = families["forced-" + tranche.replace("_", "-")]
            self.assertEqual(row["cases"], cases)
            self.assertEqual(row["arguments"], ["--tranche", tranche])
        for row, result, case in mappings:
            self.assertIn(row, campaign.forced.W331_CASES[case]["rows"])
        self.assertIn(("B11.7", "Partial, NOT_COVERED", "W331_B11_UNION"), mappings)
        self.assertFalse(any("B6.8" in spec["rows"] for spec in campaign.forced.W331_CASES.values()))

    def test_w346_save_boundary_family_runs_its_own_runner_with_its_tranche_and_stops_on_failure(self):
        families = {row["name"]: row for row in campaign.family_specs()}
        row = families["forced-W346-R"]
        self.assertEqual(row["runner"], "scripts/run_v180_bay_save_boundary.py")
        self.assertEqual(row["cases"], ["W346_B6_SAVE_BOUNDARY"])
        self.assertEqual(row["arguments"], ["--tranche", "W346_R"])
        self.assertTrue(row["stop_on_failure"])
        self.assertEqual(campaign.save_boundary.W346_CASES["W346_B6_SAVE_BOUNDARY"]["rows"], ("B6.5",))
        self.assertFalse(set(row["cases"]) & (set(campaign.forced.CASES) | set(campaign.forced.W331_CASES)))
        with tempfile.TemporaryDirectory() as directory, patch.object(campaign, "resolve_commit", return_value=SOURCE):
            planned = next(item for item in campaign.make_plan(arguments(Path(directory)))["families"] if item["name"] == row["name"])
        command = planned["command"]
        self.assertEqual(command[2], "scripts/run_v180_bay_save_boundary.py")
        self.assertEqual(command[command.index("--jar-sha256") + 1], HASH)
        self.assertEqual(command[-3:], ["--tranche", "W346_R", "--stop-on-failure"])

    def test_c1_names_order_and_every_cargo_inventory_row_are_preserved(self):
        rows = campaign.family_specs()
        self.assertEqual([row["name"] for row in rows[:12]], [
            "forced-A", "forced-A-retry", "released", "exit", "forced-B", "forced-CD", "forced-E",
            "forced-E-retry", "forced-F", "forced-G", "forced-GH-retry", "forced-H"])
        inventory = [entry for row in rows if "inventory" in row for entry in row["inventory"]]
        self.assertEqual(sorted(inventory, key=lambda row: row["case"]),
                         sorted(campaign.cargo.inventory(), key=lambda row: row["case"]))
        # W355 excludes only W211 R203's unreachable ordinary F9 from campaign reuse.
        self.assertEqual(len(inventory), 284)
        self.assertEqual(Counter(entry["case"] for entry in inventory), Counter(entry["case"] for entry in campaign.cargo.inventory()))
        self.assertEqual(len({row["name"] for row in rows}), len(rows))
        # W339 adds 17 W336 families; W345 appends six R-042 cases after those 51, and W346 one save-boundary
        # family after them.
        self.assertEqual(len(rows), 58)

    def test_dry_run_plan_reports_ordinary_f9_as_not_covered_in_its_family(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(campaign, "resolve_commit", return_value=SOURCE), \
                patch.object(campaign, "preflight") as preflight, patch.object(campaign, "HeavyLock") as lock, \
                patch.object(campaign, "run_command") as run, redirect_stdout(io.StringIO()):
            args = arguments(Path(directory), dry_run=True)
            self.assertEqual(campaign.run_campaign(args), 0)
            plan = json.loads((args.evidence_root / "plan.json").read_text(encoding="utf-8"))
            self.assertEqual(len(plan["families"]), 58)
            row = next(item for item in plan["families"] if item["name"] == "cargo-B3-S6-REUSE")
            self.assertEqual(len(row["cases"]), 96)
            self.assertEqual(row["cases"], list(campaign.cargo.REUSE["B3-S6-REUSE"][1]))
            self.assertEqual(len(row["excluded_cases"]), 1)
            exclusion = row["excluded_cases"][0]
            self.assertEqual(exclusion["case"], "LF_S6_F9_RESPAWN")
            self.assertEqual(exclusion["status"], "NOT_COVERED")
            for phrase in ("structurally out of reach", "W211 R203", "not a pass"):
                self.assertIn(phrase, exclusion["reason"])
            self.assertFalse(any(item.get("excluded_cases") for item in plan["families"] if item is not row))
            preflight.assert_not_called()
            lock.assert_not_called()
            run.assert_not_called()

    def test_w336_coverage_and_order_append_every_case_and_kind_exactly(self):
        rows = campaign.family_specs()
        self.assertFalse(any("w336_family" in row for row in rows[:34]))
        timing = rows[34:44]
        saturation = rows[44:50]
        downgrade = rows[50:51]
        self.assertEqual([row["name"] for row in rows[57:]], ["forced-W346-R"])
        self.assertTrue(all(row["w336_family"] == "timing" for row in timing))
        self.assertEqual(Counter(case for row in timing for case in row["cases"]),
                         Counter(campaign.timing.CASES) + Counter(["TURNS"]))
        expected = [(case, str(claims)) if case == "TURNS" else (case, None)
                    for case in campaign.timing.CASES for claims in ((2, 3) if case == "TURNS" else (None,))]
        self.assertEqual([(row["arguments"][1], row["arguments"][3] if "--claims" in row["arguments"] else None)
                          for row in timing], expected)
        self.assertTrue(all(row["w336_family"] == "saturation" for row in saturation))
        self.assertEqual([row["arguments"] for row in saturation], [["--kind", kind] for kind in campaign.saturation.oracle.DEVICES])
        self.assertEqual(Counter(case for row in saturation for case in row["cases"]), Counter(campaign.saturation.oracle.DEVICES.keys()))
        self.assertEqual([row["name"] for row in downgrade], ["w336-downgrade"])
        self.assertEqual(downgrade[0]["w336_family"], "downgrade")

    def test_r042_coverage_and_order_append_every_case_exactly_once(self):
        rows = campaign.family_specs()
        self.assertFalse(any("r042_case" in row for row in rows[:51]))
        expected = ["N_BAY", "L_LEDGER", "P_SERVER", "P_OWNER", "U_BAY", "U_LEDGER"]
        # W346 appends its save-boundary family after these six (row 57).
        self.assertEqual([row["name"] for row in rows[51:57]], ["r042-" + case for case in expected])
        self.assertEqual(Counter(row["r042_case"] for row in rows[51:57]), Counter(campaign.r042.CASES.keys()))
        self.assertFalse(any("r042_case" in row for row in rows[57:]))
        for row, case in zip(rows[51:57], expected):
            self.assertEqual(row["runner"], "scripts/run_v180_r042_native.py")
            self.assertEqual(row["cases"], [case])
            self.assertEqual(row["arguments"], ["--case", case])
            self.assertFalse(row.get("stop_on_failure", False))

    def test_candidate_arguments_and_reuse_session_parent_match_runner_forms(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(campaign, "resolve_commit", side_effect=lambda ref: SOURCE if ref == "HEAD" else CANDIDATE):
            plan = campaign.make_plan(arguments(Path(directory)))
            for row in plan["families"]:
                command = row["command"]
                self.assertEqual(command[command.index("--source-commit") + 1], SOURCE)
                self.assertEqual(command[command.index("--candidate-commit") + 1], CANDIDATE)
                flag = "--candidate-sha256" if "cargo_family" in row else "--jar-sha256"
                self.assertEqual(command[command.index(flag) + 1], HASH)
                if row.get("cargo_family") in campaign.cargo.REUSE:
                    self.assertEqual(row["session"], str(campaign.TEMP))
                    self.assertIn("--cargo-under-lock", command)
                self.assertNotIn("--smoke", command)
                if row.get("stop_on_failure"):
                    self.assertIn("--stop-on-failure", command)
                else:
                    self.assertNotIn("--stop-on-failure", command)
            self.assertEqual(plan["requirements"], ["--old-jar", "--old-source-commit",
                                                   "--downgrade-old-jar", "--downgrade-old-jar-sha256"])

    def test_downgrade_requirements_are_selected_and_independent_of_g2(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(campaign, "resolve_commit", return_value=SOURCE):
            base = Path(directory)
            for only, expected in ((["released"], []), (["cargo-G2-REUSE"], ["--old-jar", "--old-source-commit"]),
                                   (["w336-downgrade"], ["--downgrade-old-jar", "--downgrade-old-jar-sha256"])):
                with self.subTest(only=only):
                    self.assertEqual(campaign.make_plan(arguments(base, only=only))["requirements"], expected)
            for changes, missing in (({}, ["--downgrade-old-jar", "--downgrade-old-jar-sha256"]),
                                     ({"downgrade_old_jar": Path("w176.jar")}, ["--downgrade-old-jar-sha256"]),
                                     ({"downgrade_old_jar_sha256": OLD_HASH}, ["--downgrade-old-jar"])):
                args = arguments(base, only=["w336-downgrade"], old_jar=Path("g2.jar"), old_source_commit=CANDIDATE, **changes)
                self.assertEqual(campaign.make_plan(args)["requirements"], missing)
                with patch.object(campaign, "preflight") as preflight, patch.object(campaign, "HeavyLock") as lock:
                    with self.assertRaisesRegex(ValueError, "explicit historical inputs"):
                        campaign.run_campaign(args)
                    preflight.assert_not_called()
                    lock.assert_not_called()
                self.assertFalse(args.evidence_root.exists())
            plan = campaign.make_plan(arguments(base, old_jar=Path("g2.jar"), old_source_commit=CANDIDATE,
                                               downgrade_old_jar=Path("w176.jar"), downgrade_old_jar_sha256=OLD_HASH.upper()))
            self.assertEqual(plan["requirements"], [])
            self.assertEqual(plan["downgrade_old_jar_sha256"], OLD_HASH)
            for row in plan["families"]:
                if row.get("cargo_family") == "G2-REUSE" or row.get("w336_family") == "downgrade":
                    path = "w176.jar" if row.get("w336_family") == "downgrade" else "g2.jar"
                    self.assertEqual(row["command"][row["command"].index("--old-jar") + 1], str(Path(path).resolve()))

    def test_bad_family_suffix_hash_and_missing_g2_inputs_refuse_before_execution(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(campaign, "resolve_commit", return_value=SOURCE), \
                patch.object(campaign, "run_command") as run, patch.object(campaign, "HeavyLock") as lock:
            base = Path(directory)
            for changes in ({"only": ["unknown"]}, {"suffix": "../escape"}, {"jar_sha256": "bad"},
                            {"downgrade_old_jar_sha256": "bad"}, {"downgrade_old_jar_sha256": ""}):
                with self.assertRaises(ValueError):
                    campaign.make_plan(arguments(base, **changes))
            with self.assertRaisesRegex(ValueError, "explicit historical inputs"):
                campaign.run_campaign(arguments(base))
            run.assert_not_called()
            lock.assert_not_called()
            self.assertFalse((base / "evidence").exists())

    def test_every_command_is_accepted_by_its_real_cli_parser_with_execution_faked(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(campaign, "resolve_commit", return_value=SOURCE):
            plan = campaign.make_plan(arguments(Path(directory), old_jar=Path("historical.jar"), old_source_commit=CANDIDATE,
                                               downgrade_old_jar=Path("w176.jar"), downgrade_old_jar_sha256=OLD_HASH))
            self.assertEqual(plan["requirements"], [])
            modules = {"scripts/" + Path(module.__file__).name: module for module in (
                campaign.forced, campaign.save_boundary, campaign.released, campaign.exits, campaign.cargo,
                campaign.timing, campaign.saturation, campaign.downgrade, campaign.r042)}
            self.assertEqual(len(plan["families"]), 58)
            for row in plan["families"]:
                command = row["command"]
                module = campaign.cargo if command[2] == campaign.SELF else modules[command[2]]
                fake = (patch.object(module.native, "execute", return_value=0) if "w336_family" in row
                        else patch.object(module, "run", return_value={"status": "PASS"}))
                with self.subTest(family=row["name"]), fake as run:
                    if command[2] == campaign.SELF:
                        self.assertEqual(campaign.main(command[3:]), 0)
                    else:
                        with patch.object(sys, "argv", command[2:]):
                            self.assertEqual(module.main(), 0)
                    parsed = run.call_args.args[0]
                    self.assertEqual(parsed.source_commit, SOURCE)
                    self.assertEqual(parsed.candidate_commit, SOURCE)
                    self.assertEqual(parsed.lock_owner, row["lock_owner"])
                    if "r042_case" in row:
                        self.assertEqual(parsed.case, row["cases"])
                        self.assertIsNone(parsed.scratch)
                        self.assertEqual((parsed.startup_timeout, parsed.exit_timeout, parsed.flight_timeout,
                                          parsed.settle_timeout), (240, 180, 120, 180))
                        self.assertEqual(parsed.jar_sha256, HASH)
                        self.assertNotIn("--stop-on-failure", command)
                    if "w336_family" in row:
                        self.assertFalse(parsed.smoke)
                        self.assertEqual((parsed.startup_timeout, parsed.exit_timeout), (240, 180))
                        self.assertNotIn("--stop-on-failure", command)
                    if row.get("w336_family") == "timing":
                        self.assertEqual(parsed.case, row["cases"][0])
                        self.assertEqual((parsed.window_ticks, parsed.ticket_timeout, parsed.flight_timeout, parsed.settle_timeout),
                                         (240, 120, 120, 180))
                        self.assertEqual(parsed.claims, int(row["arguments"][3]) if parsed.case == "TURNS" else 2)
                    if row.get("w336_family") == "saturation":
                        self.assertEqual(parsed.kind, row["cases"][0])
                    if row.get("w336_family") == "downgrade":
                        self.assertEqual(parsed.old_jar, Path("w176.jar").resolve())
                        self.assertEqual((parsed.flight_timeout, parsed.settle_timeout), (120, 180))
                    if row.get("cargo_family") == "G2-REUSE":
                        self.assertEqual(parsed.old_jar, Path("historical.jar").resolve())
                        self.assertEqual(parsed.old_source_commit, SOURCE)
                        self.assertEqual(parsed.start_counter, Path(row["output"]) / "start-counter.json")


class PreflightTest(unittest.TestCase):
    def setUp(self):
        self.plan = dict(jar="candidate.jar", jar_sha256=HASH, source_commit=SOURCE, candidate_commit=CANDIDATE, families=[])
        self.hash = patch.object(campaign, "digest", return_value=HASH).start()
        self.git = patch.object(campaign, "git", side_effect=[SOURCE, ""]).start()
        self.diff = patch.object(campaign.subprocess, "run", return_value=Mock(returncode=0, stderr="")).start()
        self.disk = patch.object(campaign.shutil, "disk_usage", return_value=Mock(free=campaign.MIN_FREE + 1)).start()
        self.addCleanup(patch.stopall)

    def test_pass_records_exact_production_paths_and_both_disks(self):
        result = campaign.preflight(self.plan)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["production_diff_exit"], 0)
        self.assertEqual(self.diff.call_args.args[0], ["git", "diff", "--quiet", CANDIDATE, "HEAD", "--", *campaign.PRODUCTION_PATHS])
        self.assertEqual([call.args[0] for call in self.disk.call_args_list], ["C:\\", "D:\\"])

    def test_hash_mismatch_refuses_before_git_or_disk(self):
        self.hash.return_value = "d" * 64
        with self.assertRaisesRegex(campaign.PreconditionError, "hash mismatch"):
            campaign.preflight(self.plan)
        self.diff.assert_not_called()
        self.disk.assert_not_called()

    def test_selected_downgrade_hash_is_verified_separately_before_git_or_disk(self):
        self.plan.update(families=[dict(w336_family="downgrade")], downgrade_old_jar="w176.jar", downgrade_old_jar_sha256=OLD_HASH)
        self.hash.side_effect = [HASH, OLD_HASH]
        result = campaign.preflight(self.plan)
        self.assertEqual(result["downgrade_old_jar_sha256"], OLD_HASH)
        self.assertEqual([call.args[0] for call in self.hash.call_args_list], ["candidate.jar", "w176.jar"])
        self.diff.reset_mock()
        self.disk.reset_mock()
        self.hash.side_effect = [HASH, "e" * 64]
        with self.assertRaisesRegex(campaign.PreconditionError, "Downgrade old JAR hash mismatch") as raised:
            campaign.preflight(self.plan)
        self.assertEqual(raised.exception.checks["downgrade_old_jar_sha256"], "e" * 64)
        self.diff.assert_not_called()
        self.disk.assert_not_called()

    def test_production_diff_and_git_errors_are_refusals(self):
        for code in (1, 128):
            self.diff.return_value.returncode = code
            with self.assertRaisesRegex(campaign.PreconditionError, "Production source") as raised:
                campaign.preflight(self.plan)
            self.assertEqual(raised.exception.checks["production_diff_exit"], code)
        self.disk.assert_not_called()

    def test_dirty_worktree_and_changed_head_refuse(self):
        for head, status in ((SOURCE, "?? untracked"), (SOURCE, " M scripts/x.py"), (CANDIDATE, "")):
            self.git.side_effect = [head, status]
            with self.assertRaisesRegex(campaign.PreconditionError, "HEAD changed or worktree is dirty"):
                campaign.preflight(self.plan)
        self.disk.assert_not_called()

    def test_low_or_exactly_ten_gib_on_either_disk_refuses(self):
        for values in ((0, campaign.MIN_FREE + 1), (campaign.MIN_FREE + 1, campaign.MIN_FREE),
                       (campaign.MIN_FREE, campaign.MIN_FREE + 1)):
            self.git.side_effect = [SOURCE, ""]
            self.disk.side_effect = [Mock(free=value) for value in values]
            with self.assertRaisesRegex(campaign.PreconditionError, "over 10 GiB"):
                campaign.preflight(self.plan)


class Clock:
    def __init__(self):
        self.now = 0
        self.sleeps = []

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.now += seconds


class LockTest(unittest.TestCase):
    def test_atomic_creation_uses_cmd_and_backslashes(self):
        with patch.object(campaign.subprocess, "run", return_value=Mock(returncode=0)) as run:
            self.assertTrue(campaign.atomic_mkdir(campaign.LOCK))
        self.assertEqual(run.call_args.args[0], ["cmd", "/c", "mkdir", r"D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm"])

    def test_release_never_removes_another_owner_or_foreign_contents(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lock"
            path.mkdir()
            marker = path / "owner.txt"
            marker.write_text("someone-else", encoding="utf-8")
            lock = campaign.HeavyLock(path)
            with self.assertRaisesRegex(ValueError, "owner changed"):
                lock.release("mine")
            self.assertEqual(marker.read_text(), "someone-else")
            marker.write_text("mine-extended")
            with self.assertRaises(ValueError):
                lock.release("mine")
            marker.write_text("mine")
            extra = path / "foreign.txt"
            extra.write_text("retain")
            with self.assertRaisesRegex(ValueError, "foreign contents"):
                lock.release("mine")
            self.assertEqual(extra.read_text(), "retain")
            self.assertEqual(marker.read_text(), "mine")

    def test_owned_lock_release_and_reacquisition_wait_at_least_ninety_seconds(self):
        with tempfile.TemporaryDirectory() as directory:
            clock = Clock()
            path = Path(directory) / "lock"
            def mkdir(target):
                target.mkdir()
                return True
            with patch.object(campaign, "atomic_mkdir", side_effect=mkdir), \
                    patch.object(campaign.time, "monotonic", side_effect=clock.monotonic), \
                    patch.object(campaign.time, "sleep", side_effect=clock.sleep):
                lock = campaign.HeavyLock(path)
                lock.acquire("one")
                self.assertEqual((path / "owner.txt").read_text().strip(), "one")
                lock.release("one")
                self.assertFalse(path.exists())
                clock.now += 10
                lock.acquire("two")
                self.assertEqual(clock.sleeps, [80])
                self.assertEqual(clock.now, 90)
                lock.release("two")

    def test_wait_limit_does_not_remove_foreign_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lock"
            path.mkdir()
            (path / "owner.txt").write_text("foreign")
            clock = Clock()
            with patch.object(campaign, "atomic_mkdir", return_value=False), \
                    patch.object(campaign.time, "monotonic", side_effect=clock.monotonic), \
                    patch.object(campaign.time, "sleep", side_effect=clock.sleep):
                with self.assertRaisesRegex(ValueError, "wait exceeded three hours"):
                    campaign.HeavyLock(path).acquire("mine")
            self.assertEqual(clock.now, campaign.LOCK_LIMIT)
            self.assertEqual((path / "owner.txt").read_text(), "foreign")

    def test_stale_lock_and_missing_parent_mkdir_errors_are_not_busy_loops(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "lock"
            path.mkdir()
            with patch.object(campaign.time, "time", return_value=path.stat().st_mtime + campaign.LOCK_LIMIT), \
                    patch.object(campaign, "atomic_mkdir") as mkdir:
                with self.assertRaisesRegex(ValueError, "over three hours old"):
                    campaign.HeavyLock(path).acquire("mine")
                mkdir.assert_not_called()
            missing = Path(directory) / "missing" / "lock"
            with patch.object(campaign.subprocess, "run", return_value=Mock(returncode=1, stderr="bad mkdir")):
                with self.assertRaisesRegex(OSError, "bad mkdir"):
                    campaign.atomic_mkdir(missing)


class ExecutionTest(unittest.TestCase):
    def setUp(self):
        self.resolve = patch.object(campaign, "resolve_commit", side_effect=lambda value: SOURCE if value == "HEAD" else CANDIDATE).start()
        self.addCleanup(patch.stopall)

    def test_failed_family_blocks_only_its_underlying_runner_and_updates_every_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory), only=["forced-A", "forced-A-retry", "released", "exit", "forced-B"])
            writes = []
            original = campaign.write_json
            def capture(path, value):
                if path.name == "campaign-unit.json":
                    writes.append(len(value["families"]))
                original(path, value)
            with patch.object(campaign, "preflight", return_value=dict(production_diff_exit=0)), \
                    patch.object(campaign, "HeavyLock") as lock, patch.object(campaign, "run_command", side_effect=[1, 0, 0]) as run, \
                    patch.object(campaign, "write_json", side_effect=capture), redirect_stdout(io.StringIO()):
                self.assertEqual(campaign.run_campaign(args), 1)
            summary = json.loads((args.evidence_root / "campaign-unit.json").read_text())
            self.assertEqual([row["status"] for row in summary["families"]], ["FAILED", "NOT_RUN", "PASS", "PASS", "NOT_RUN"])
            self.assertEqual(run.call_count, 3)
            self.assertEqual(lock.return_value.acquire.call_count, 3)
            self.assertEqual(lock.return_value.release.call_count, 3)
            self.assertEqual(writes, [0, 1, 2, 3, 4, 5, 5])
            self.assertEqual(json.loads((args.evidence_root / "campaign-unit.done").read_text())["status"], "FAILED")
            for row in summary["families"]:
                invocation = args.evidence_root / (row["name"] + "-unit") / "invocation.json"
                self.assertEqual(json.loads(invocation.read_text())["status"], row["status"])

    def test_cargo_dispatcher_does_not_block_independent_item_runner_after_fluid_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory), only=["cargo-C-S1", "cargo-C-S2", "cargo-D-S1"])
            with patch.object(campaign, "preflight", return_value=dict(production_diff_exit=0)), \
                    patch.object(campaign, "HeavyLock"), patch.object(campaign, "run_command", side_effect=[1, 0]) as run, \
                    redirect_stdout(io.StringIO()):
                self.assertEqual(campaign.run_campaign(args), 1)
            self.assertEqual([call.args[0]["name"] for call in run.call_args_list], ["cargo-C-S1", "cargo-D-S1"])

    def test_failed_timing_stops_later_timing_families_but_not_saturation(self):
        with tempfile.TemporaryDirectory() as directory:
            selected = campaign.family_specs()[34:37] + campaign.family_specs()[44:46]
            args = arguments(Path(directory), only=[row["name"] for row in selected])
            with patch.object(campaign, "preflight", return_value=dict(production_diff_exit=0)), \
                    patch.object(campaign, "HeavyLock"), patch.object(campaign, "run_command", side_effect=[1, 0, 0]) as run, \
                    redirect_stdout(io.StringIO()):
                self.assertEqual(campaign.run_campaign(args), 1)
            summary = json.loads((args.evidence_root / "campaign-unit.json").read_text())
            self.assertEqual([row["status"] for row in summary["families"]], ["FAILED", "NOT_RUN", "NOT_RUN", "PASS", "PASS"])
            self.assertEqual([call.args[0]["name"] for call in run.call_args_list], [selected[i]["name"] for i in (0, 3, 4)])

    def test_downgrade_keeps_failed_verdict_and_exit_code_without_blocking_other_runners(self):
        for exit_code in (0, 1):
            with self.subTest(exit_code=exit_code), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory), only=["w336-downgrade", "w336-timing-B12_D4", "w336-saturation-bay", "cargo-C-S1"],
                                 downgrade_old_jar=Path("w176.jar"), downgrade_old_jar_sha256=OLD_HASH)
                original = campaign.make_plan
                def downgrade_first(args):
                    plan = original(args)
                    # Put the normally last family first to observe failure isolation.
                    plan["families"].sort(key=lambda row: row.get("w336_family") != "downgrade")
                    return plan
                def execute(row):
                    if row.get("w336_family") == "downgrade":
                        native = Path(row["output"]) / "native"
                        native.mkdir()
                        (native / "results.json").write_text(json.dumps(dict(status="FAILED")), encoding="utf-8")
                        return exit_code
                    return 0
                with patch.object(campaign, "make_plan", side_effect=downgrade_first), \
                        patch.object(campaign, "preflight", return_value=dict(production_diff_exit=0)), \
                        patch.object(campaign, "HeavyLock"), patch.object(campaign, "run_command", side_effect=execute) as run, \
                        redirect_stdout(io.StringIO()):
                    self.assertEqual(campaign.run_campaign(args), 1)
                summary = json.loads((args.evidence_root / "campaign-unit.json").read_text())
                self.assertEqual(summary["status"], "FAILED")
                self.assertEqual([row["status"] for row in summary["families"]], ["FAILED", "PASS", "PASS", "PASS"])
                self.assertEqual(summary["families"][0]["exit_code"], exit_code)
                self.assertEqual(summary["families"][0]["runner_status"], "FAILED")
                self.assertEqual(run.call_count, 4)
                self.assertEqual(json.loads((args.evidence_root / "campaign-unit.done").read_text())["status"], "FAILED")

    def test_downgrade_hash_refusal_before_or_after_lock_never_starts_a_runner(self):
        for after_lock in (False, True):
            with self.subTest(after_lock=after_lock), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory), only=["w336-downgrade"],
                                 downgrade_old_jar=Path("w176.jar"), downgrade_old_jar_sha256=OLD_HASH)
                hashes = [HASH, OLD_HASH, HASH, "e" * 64] if after_lock else [HASH, "e" * 64]
                with patch.object(campaign, "digest", side_effect=hashes), \
                        patch.object(campaign, "git", side_effect=[SOURCE, ""]), \
                        patch.object(campaign.subprocess, "run", return_value=Mock(returncode=0, stderr="")), \
                        patch.object(campaign.shutil, "disk_usage", return_value=Mock(free=campaign.MIN_FREE + 1)), \
                        patch.object(campaign, "HeavyLock") as lock, patch.object(campaign, "run_command") as run, \
                        redirect_stdout(io.StringIO()):
                    self.assertEqual(campaign.run_campaign(args), 1)
                run.assert_not_called()
                self.assertEqual(lock.return_value.acquire.call_count, int(after_lock))
                self.assertEqual(lock.return_value.release.call_count, int(after_lock))
                row = json.loads(next(args.evidence_root.glob("*/invocation.json")).read_text())
                self.assertEqual(row["refusal"]["downgrade_old_jar_sha256"], "e" * 64)
                self.assertEqual(row["status"], "DRIVER_FAILED")

    def test_refusals_before_and_after_lock_never_start_a_runner(self):
        for after_lock in (False, True):
            with tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory), only=["forced-A"])
                refusal = campaign.PreconditionError("changed", dict(status="REFUSED", production_diff_exit=1))
                checks = [dict(production_diff_exit=0), refusal] if after_lock else [refusal]
                with patch.object(campaign, "preflight", side_effect=checks), patch.object(campaign, "HeavyLock") as lock, \
                        patch.object(campaign, "run_command") as run, redirect_stdout(io.StringIO()):
                    self.assertEqual(campaign.run_campaign(args), 1)
                run.assert_not_called()
                self.assertEqual(lock.return_value.acquire.call_count, int(after_lock))
                self.assertEqual(lock.return_value.release.call_count, int(after_lock))
                row = json.loads(next(args.evidence_root.glob("*/invocation.json")).read_text())
                self.assertEqual(row["production_diff_exit"], 1)
                self.assertEqual(row["status"], "DRIVER_FAILED")

    def test_runner_exception_releases_own_lock_but_never_deletes_a_server(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            session = root / "session-created-by-someone-else"
            session.mkdir()
            marker = session / "foreign.txt"
            marker.write_text("retain")
            args = arguments(root, only=["forced-A"])
            original = campaign.make_plan
            def plan_with_foreign_session(args):
                plan = original(args)
                plan["families"][0]["session"] = str(session)
                return plan
            with patch.object(campaign, "preflight", return_value=dict(production_diff_exit=0)), \
                    patch.object(campaign, "make_plan", side_effect=plan_with_foreign_session), \
                    patch.object(campaign, "HeavyLock") as lock, patch.object(campaign, "run_command", side_effect=OSError("runner failed")), \
                    patch.object(campaign.shutil, "rmtree") as delete, redirect_stdout(io.StringIO()):
                self.assertEqual(campaign.run_campaign(args), 1)
                delete.assert_not_called()
            lock.return_value.release.assert_called_once()
            self.assertEqual(marker.read_text(), "retain")
            cleanup = json.loads(next(args.evidence_root.glob("*/cleanup.json")).read_text())
            self.assertTrue(cleanup["session_exists"])
            self.assertFalse(cleanup["driver_deleted"])

    def test_lock_release_failure_is_not_reported_as_a_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory), only=["released"])
            with patch.object(campaign, "preflight", return_value=dict(production_diff_exit=0)), \
                    patch.object(campaign, "HeavyLock") as lock, patch.object(campaign, "run_command", return_value=0), redirect_stdout(io.StringIO()):
                lock.return_value.release.side_effect = ValueError("foreign owner")
                self.assertEqual(campaign.run_campaign(args), 1)
            row = json.loads(next(args.evidence_root.glob("*/invocation.json")).read_text())
            self.assertEqual(row["status"], "DRIVER_FAILED")
            self.assertFalse(row["lock_released"])

    def test_dry_run_with_nonexistent_jar_prints_and_writes_every_command_but_executes_nothing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "evidence"
            with patch.object(campaign, "preflight") as preflight, patch.object(campaign, "HeavyLock") as lock, \
                    patch.object(campaign, "run_command") as run, patch.object(campaign, "digest") as digest, \
                    patch.object(campaign.shutil, "disk_usage") as disk, patch.object(campaign.subprocess, "run") as process, \
                    patch.object(campaign.cargo, "main") as wrapper, redirect_stdout(io.StringIO()) as output:
                self.assertEqual(campaign.main(["--candidate-commit", "HEAD", "--jar", "missing.jar", "--jar-sha256", HASH,
                                               "--template", "missing-template", "--evidence-root", str(root), "--dry-run"]), 0)
                for fake in (preflight, lock, run, digest, disk, process, wrapper):
                    fake.assert_not_called()
            plan = json.loads((root / "plan.json").read_text())
            self.assertEqual(json.loads(output.getvalue()), plan)
            # W345 adds six R-042 families to W339's 51, and W346 one more, without executing any of them.
            self.assertEqual(len(plan["families"]), 58)
            self.assertEqual(len(list(root.glob("*/invocation.json"))), 58)
            self.assertEqual(json.loads((root / "campaign-attempt1.done").read_text())["status"], "DRY_RUN")
            self.assertTrue(all(row["command"] for row in plan["families"]))

    def test_only_cli_accepts_spaces_commas_and_multiple_occurrences(self):
        with tempfile.TemporaryDirectory() as directory, redirect_stdout(io.StringIO()):
            root = Path(directory) / "evidence"
            self.assertEqual(campaign.main(["--candidate-commit", "HEAD", "--jar", "missing.jar", "--jar-sha256", HASH,
                                           "--template", "missing", "--evidence-root", str(root), "--dry-run",
                                           "--only", "released,exit", "forced-B", "--only", "forced-A"]), 0)
            plan = json.loads((root / "plan.json").read_text())
            self.assertEqual([row["name"] for row in plan["families"]], ["forced-A", "released", "exit", "forced-B"])
            self.assertTrue(plan["partial"])

    def test_occupied_evidence_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sentinel = root / "retain.json"
            sentinel.write_text("existing evidence")
            with self.assertRaisesRegex(ValueError, "new and empty"):
                campaign.prepare_evidence(root)
            self.assertEqual(sentinel.read_text(), "existing evidence")


class ReuseAdapterTest(unittest.TestCase):
    def test_already_locked_reuse_refuses_the_wrappers_optional_owner_before_any_effect(self):
        for owner in (None, ""):
            args = argparse.Namespace(family="B3-S1-REUSE", lock_owner=owner)
            with patch.object(campaign.cargo.fluid.native, "require_lock") as require, \
                    patch.object(campaign.cargo, "verify_candidate") as candidate, \
                    patch.object(campaign.cargo.loader, "run_cut") as cut:
                with self.assertRaisesRegex(ValueError, "nonempty lock owner"):
                    campaign.run_reuse_under_lock(args, {})
                require.assert_not_called()
                candidate.assert_not_called()
                cut.assert_not_called()

    def test_reuse_keeps_exact_cut_pins_owner_and_first_contradiction_without_nested_lock(self):
        for family, (module, cases) in campaign.cargo.REUSE.items():
            with tempfile.TemporaryDirectory() as directory:
                args = argparse.Namespace(family=family, case=list(cases[:2]), session_dir=Path(directory),
                    evidence_dir=Path(directory) / "evidence", jar=Path("candidate.jar"), source_commit=SOURCE,
                    candidate_commit=CANDIDATE, candidate_sha256=HASH, lock_owner="campaign-owner")
                with patch.object(campaign.cargo.fluid.native, "require_lock") as require, \
                        patch.object(campaign.cargo.fluid.native, "digest", return_value=HASH), \
                        patch.object(campaign.cargo, "verify_candidate") as verify, \
                        patch.object(campaign.cargo.loader, "heavy_lock") as nested, \
                        patch.object(module, "run_cut", return_value={"status": "FAILED"}) as cut:
                    with self.assertRaisesRegex(ValueError, "First contradictory"):
                        campaign.run_reuse_under_lock(args, {"candidate_commit": CANDIDATE, "jar_sha256": HASH})
                nested.assert_not_called()
                require.assert_called_with("campaign-owner")
                verify.assert_called_once_with(args.jar, SOURCE, CANDIDATE, HASH)
                cut.assert_called_once()
                call = cut.call_args.args
                self.assertEqual(call[1], cases[0])
                self.assertEqual(call[3], "campaign-owner")
                self.assertEqual(call[4]["candidate_commit"], CANDIDATE)
                self.assertTrue(call[4]["hooks"])
                self.assertTrue(call[4]["harness"])
                self.assertEqual(json.loads((args.evidence_dir / "results.json").read_text())["status"], "FAILED")

    def test_successful_reuse_keeps_both_cases_checks_pins_and_original_between_cut_delay(self):
        with tempfile.TemporaryDirectory() as directory:
            family = "B3-S1-REUSE"
            module, cases = campaign.cargo.REUSE[family]
            args = argparse.Namespace(family=family, case=list(cases[:2]), session_dir=Path(directory),
                evidence_dir=Path(directory) / "evidence", jar=Path("candidate.jar"), source_commit=SOURCE,
                candidate_commit=CANDIDATE, candidate_sha256=HASH, lock_owner="campaign-owner")
            with patch.object(campaign.cargo.fluid.native, "require_lock"), \
                    patch.object(campaign.cargo.fluid.native, "digest", return_value=HASH), \
                    patch.object(campaign.cargo, "verify_candidate") as verify, \
                    patch.object(campaign.cargo.loader, "heavy_lock") as nested, \
                    patch.object(campaign.time, "sleep") as sleep, \
                    patch.object(module, "run_cut", side_effect=lambda args, case, evidence, owner, pins: dict(case=case, status="PASS")) as cut:
                campaign.run_reuse_under_lock(args, {"candidate_commit": CANDIDATE, "jar_sha256": HASH})
            nested.assert_not_called()
            sleep.assert_called_once_with(90)
            self.assertEqual(verify.call_count, 2)
            self.assertEqual([call.args[1] for call in cut.call_args_list], list(cases[:2]))
            self.assertFalse(args.follow_ups)
            self.assertFalse(args.diagnose_unload)
            report = json.loads((args.evidence_dir / "results.json").read_text())
            self.assertEqual(report["status"], "PASS_SELECTED")
            self.assertEqual(len(report["cases"]), 2)

    def test_child_dispatch_forwards_unchanged_wrapper_cli_and_restores_function(self):
        original = campaign.cargo.run_loader
        arguments = sys.argv
        def wrapper():
            self.assertEqual(sys.argv, [campaign.WRAPPER, "template", "--family", "B3-S1-REUSE"])
            self.assertIs(campaign.cargo.run_loader, campaign.run_reuse_under_lock)
            raise ValueError("wrapper failed")
        with patch.object(campaign.cargo, "main", side_effect=wrapper):
            with self.assertRaisesRegex(ValueError, "wrapper failed"):
                campaign.main(["--cargo-under-lock", "template", "--family", "B3-S1-REUSE"])
        self.assertIs(campaign.cargo.run_loader, original)
        self.assertIs(sys.argv, arguments)


if __name__ == "__main__":
    unittest.main()
