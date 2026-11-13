#!/usr/bin/env python3
"""Plan or run the bay/cargo matrix on one pinned candidate; never build a JAR.

Run real campaigns as detached processes, as required by WORKER-RULES.md. Each
invocation needs a new external evidence directory. --only is partial evidence,
not a substitute for rerunning the complete matrix after a production change.
G2-REUSE additionally requires --old-jar and --old-source-commit.
W336 downgrade separately requires --downgrade-old-jar and its --downgrade-old-jar-sha256.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import time
import uuid

import run_v180_bay_forced_stop as forced
import run_v180_bay_save_boundary as save_boundary
import run_v180_bay_uncovered as uncovered
import run_v180_bay_released_flight as released
import run_v180_bay_exit_cuts as exits
import run_v180_cargo_integrated as cargo
import run_v180_w336_timing as timing
import run_v180_w336_saturation as saturation
import run_v180_w336_downgrade as downgrade
import run_v180_r042_native as r042

REPO = Path(__file__).resolve().parents[1]
LOCK = Path(r"D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm")
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")
PRODUCTION_PATHS = cargo.PRODUCTION_PATHS
MIN_FREE = 10 * 1024 ** 3
LOCK_LIMIT = 3 * 3600
COOLDOWN = 90
SELF = "scripts/run_v180_integrated_campaign.py"
WRAPPER = "scripts/run_v180_cargo_integrated.py"


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def git(*arguments):
    return subprocess.run(["git", *arguments], cwd=REPO, capture_output=True, text=True, check=True).stdout.strip()


def resolve_commit(value):
    result = git("rev-parse", "--verify", "--end-of-options", value + "^{commit}")
    if not re.fullmatch(r"[0-9a-f]{40}", result):
        raise ValueError("Git did not resolve a full commit")
    return result


def family_specs():
    """Keep the original 34 families and W336 observations in order, then append R-042 cases, then W346's save-boundary
    family."""
    rows = []

    def bay(name, module, cases, arguments=()):
        rows.append(dict(name=name, runner="scripts/" + Path(module.__file__).name,
                         cases=list(cases), arguments=list(arguments), stop_on_failure=True))

    def tranche(name, key):
        cases = {**forced.TRANCHES, **forced.W331_TRANCHES}[key]
        bay(name, forced, cases, ("--tranche", key))

    def selected(name, cases):
        bay(name, forced, cases, [argument for case in cases for argument in ("--case", case)])

    tranche("forced-A", "A")
    selected("forced-A-retry", ["B11_FULL", "B17_FAIR", "B10_BLOCKED", "B14_STOP"])
    bay("released", released, released.CASES)
    bay("exit", exits, exits.CASES)
    tranche("forced-B", "B")
    selected("forced-CD", forced.TRANCHES["C"] + forced.TRANCHES["D"])
    tranche("forced-E", "E")
    selected("forced-E-retry", ["W325_B8_MOVED_OFFLINE", "W325_B3_STAGED_ONLINE", "W325_B3_MOVED_ONLINE",
                               "W325_B8_STAGED_ONLINE", "W325_B8_MOVED_ONLINE", "W325_OWNER_FULL",
                               "W325_OTHER_OWNER_ROOM", "W325_B3_STAGED_ONLINE_ABSENT", "W325_B3_MOVED_ONLINE_ABSENT"])
    tranche("forced-F", "F")
    tranche("forced-G", "G")
    selected("forced-GH-retry", ["W325_D7_FAILED", "W325_B9_RETRY"])
    tranche("forced-H", "H")
    for key in forced.W331_TRANCHES:
        tranche("forced-" + key.replace("_", "-"), key)
    inventory = cargo.inventory()
    for family in dict.fromkeys(row["family"] for row in inventory):
        cases = [row for row in inventory if row["family"] == family]
        runners = {row["runner"] for row in cases}
        if len(runners) != 1:
            raise ValueError("Cargo family has multiple underlying runners: " + family)
        rows.append(dict(name="cargo-" + family, runner=cases[0]["runner"], cargo_family=family,
                         cases=[row["case"] for row in cases], inventory=cases, arguments=["--family", family]))
        excluded = cargo.REUSE_EXCLUDED_CASES.get(family)
        if excluded:
            rows[-1]["excluded_cases"] = [dict(case=case, status="NOT_COVERED", reason=reason)
                                           for case, reason in excluded.items()]
    for case in timing.CASES:
        for claims in (2, 3) if case == "TURNS" else (None,):
            rows.append(dict(name="w336-timing-" + case + ("-" + str(claims) if claims else ""),
                             runner="scripts/" + Path(timing.__file__).name, w336_family="timing",
                             cases=[case], arguments=["--case", case] + (["--claims", str(claims)] if claims else [])))
    for kind in saturation.oracle.DEVICES:
        rows.append(dict(name="w336-saturation-" + kind, runner="scripts/" + Path(saturation.__file__).name,
                         w336_family="saturation", cases=[kind], arguments=["--kind", kind]))
    rows.append(dict(name="w336-downgrade", runner="scripts/" + Path(downgrade.__file__).name,
                     w336_family="downgrade", cases=["downgrade"], arguments=[]))
    for case in ("N_BAY", "L_LEDGER", "P_SERVER", "P_OWNER", "U_BAY", "U_LEDGER"):
        rows.append(dict(name="r042-" + case, runner="scripts/" + Path(r042.__file__).name,
                         r042_case=case, cases=[case], arguments=["--case", case]))
    # W346: B6.5 at the source-save boundary, a forced-stop family in its own runner.
    for key, cases in save_boundary.W346_TRANCHES.items():
        bay("forced-" + key.replace("_", "-"), save_boundary, cases, ("--tranche", key))
    for key, cases in uncovered.TRANCHES.items():
        bay("forced-" + key.replace("_", "-"), uncovered, cases, ("--tranche", key))
    return rows


def command_for(row, plan):
    cargo_family = row.get("cargo_family")
    script = [SELF, "--cargo-under-lock"] if cargo_family in cargo.REUSE else [WRAPPER if cargo_family else row["runner"]]
    command = [sys.executable, "-B", *script, plan["template"], "--jar", plan["jar"],
               "--session-dir", row["session"], "--evidence-dir", str(Path(row["output"]) / "native"),
               "--source-commit", plan["source_commit"], "--candidate-commit", plan["candidate_commit"],
               "--candidate-sha256" if cargo_family else "--jar-sha256", plan["jar_sha256"],
               "--java", plan["java"], "--lock-owner", row["lock_owner"], *row["arguments"]]
    if row.get("stop_on_failure"):
        command.append("--stop-on-failure")
    if cargo_family == "G2-REUSE":
        command.extend(["--old-jar", plan["old_jar"] or "<required-old-jar>",
                        "--old-source-commit", plan["old_source_commit"] or "<required-old-source-commit>",
                        "--start-counter", str(Path(row["output"]) / "start-counter.json")])
    if row.get("w336_family") == "downgrade":
        command.extend(["--old-jar", plan["downgrade_old_jar"] or "<required-downgrade-old-jar>"])
    return command


def make_plan(args):
    if not re.fullmatch(r"[0-9a-fA-F]{64}", args.jar_sha256):
        raise ValueError("--jar-sha256 must be 64 hexadecimal characters")
    if args.downgrade_old_jar_sha256 is not None and not re.fullmatch(r"[0-9a-fA-F]{64}", args.downgrade_old_jar_sha256):
        raise ValueError("--downgrade-old-jar-sha256 must be 64 hexadecimal characters")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,47}", args.suffix):
        raise ValueError("--suffix must be a short filename component")
    specs = family_specs()
    names = {row["name"] for row in specs}
    only = {name for group in args.only or [] for name in group.split(",")}
    if only - names:
        raise ValueError("Unknown --only families: " + ", ".join(sorted(only - names)))
    source = resolve_commit("HEAD")
    root = args.evidence_root.resolve()
    plan = dict(schema_version=1, mode="DRY_RUN" if args.dry_run else "NATIVE", created=utc_now(),
                source_commit=source, candidate_commit=resolve_commit(args.candidate_commit),
                jar=str(args.jar.resolve()), jar_sha256=args.jar_sha256.lower(), template=str(args.template.resolve()),
                evidence_root=str(root), java=args.java, suffix=args.suffix, partial=bool(only),
                old_jar=str(args.old_jar.resolve()) if args.old_jar else None,
                old_source_commit=resolve_commit(args.old_source_commit) if args.old_source_commit else None,
                downgrade_old_jar=str(args.downgrade_old_jar.resolve()) if args.downgrade_old_jar else None,
                downgrade_old_jar_sha256=args.downgrade_old_jar_sha256.lower() if args.downgrade_old_jar_sha256 else None,
                omitted_families=[row["name"] for row in specs if only and row["name"] not in only], families=[])
    campaign_id = uuid.uuid4().hex
    for spec in specs:
        if only and spec["name"] not in only:
            continue
        row = dict(spec)
        name = row["name"] + "-" + args.suffix
        row.update(output=str(root / name), session=str(TEMP if row.get("cargo_family") in cargo.REUSE
                   else TEMP / ("W337-" + name + "-" + campaign_id)),
                   lock_owner=f"W337 {name} pid={os.getpid()} {uuid.uuid4().hex} {utc_now()}")
        row["command"] = command_for(row, plan)
        plan["families"].append(row)
    plan["requirements"] = []
    if any(row.get("cargo_family") == "G2-REUSE" for row in plan["families"]):
        plan["requirements"].extend(flag for flag, value in (("--old-jar", plan["old_jar"]),
                                                            ("--old-source-commit", plan["old_source_commit"])) if not value)
    if any(row.get("w336_family") == "downgrade" for row in plan["families"]):
        plan["requirements"].extend(flag for flag, value in (("--downgrade-old-jar", plan["downgrade_old_jar"]),
                                                            ("--downgrade-old-jar-sha256", plan["downgrade_old_jar_sha256"])) if not value)
    return plan


class PreconditionError(ValueError):
    def __init__(self, message, checks):
        super().__init__(message)
        self.checks = checks


def preflight(plan):
    checks = dict(status="CHECKING", production_diff_exit=None)

    def require(condition, message):
        if not condition:
            checks["status"] = "REFUSED"
            raise PreconditionError(message, checks)

    checks["jar_sha256"] = digest(plan["jar"])
    require(checks["jar_sha256"] == plan["jar_sha256"], "Candidate JAR hash mismatch")
    if any(row.get("w336_family") == "downgrade" for row in plan["families"]):
        checks["downgrade_old_jar_sha256"] = digest(plan["downgrade_old_jar"])
        require(checks["downgrade_old_jar_sha256"] == plan["downgrade_old_jar_sha256"], "Downgrade old JAR hash mismatch")
    command = ["git", "diff", "--quiet", plan["candidate_commit"], "HEAD", "--", *PRODUCTION_PATHS]
    checks["production_diff_command"] = command
    result = subprocess.run(command, cwd=REPO, capture_output=True, text=True)
    checks["production_diff_exit"] = result.returncode
    checks["production_diff_stderr"] = result.stderr
    require(result.returncode == 0, "Production source differs from the candidate, or git diff failed")
    checks["head"] = git("rev-parse", "HEAD")
    checks["worktree_status"] = git("status", "--porcelain")
    require(checks["head"] == plan["source_commit"] and not checks["worktree_status"],
            "Source HEAD changed or worktree is dirty")
    checks["free_space"] = {drive: shutil.disk_usage(drive).free for drive in ("C:\\", "D:\\")}
    require(all(free > MIN_FREE for free in checks["free_space"].values()), "Need over 10 GiB free on both C: and D:")
    checks["status"] = "PASS"
    return checks


def is_link(path):
    info = path.lstat()
    return path.is_symlink() or bool(getattr(info, "st_file_attributes", 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT)


def atomic_mkdir(path):
    """Use cmd's atomic mkdir, with the fixed Windows backslash path."""
    result = subprocess.run(["cmd", "/c", "mkdir", str(path)], capture_output=True, text=True)
    if result.returncode and not path.exists():
        raise OSError("Atomic lock mkdir failed: " + result.stderr.strip())
    return result.returncode == 0


class HeavyLock:
    def __init__(self, path=LOCK):
        self.path = path
        self.released_at = None

    def acquire(self, owner):
        if self.released_at is not None:
            remaining = COOLDOWN - (time.monotonic() - self.released_at)
            if remaining > 0:
                time.sleep(remaining)
        deadline = time.monotonic() + LOCK_LIMIT
        while True:
            if time.monotonic() >= deadline:
                raise ValueError("Heavy-JVM lock wait exceeded three hours; not removed")
            if self.path.exists():
                if is_link(self.path) or time.time() - self.path.stat().st_mtime >= LOCK_LIMIT:
                    raise ValueError("Heavy-JVM lock is linked or over three hours old; not removed")
            if atomic_mkdir(self.path):
                with (self.path / "owner.txt").open("x", encoding="utf-8") as stream:
                    stream.write(owner + "\n")
                return
            time.sleep(min(60, max(0, deadline - time.monotonic())))

    def release(self, owner):
        # No recursive delete: foreign files, junctions and changed owners remain intact.
        marker = self.path / "owner.txt"
        if is_link(self.path) or is_link(marker) or marker.read_text(encoding="utf-8-sig").strip() != owner:
            raise ValueError("Heavy-JVM lock owner changed; not removed")
        if set(self.path.iterdir()) != {marker}:
            raise ValueError("Heavy-JVM lock has foreign contents; not removed")
        marker.unlink()
        self.path.rmdir()
        self.released_at = time.monotonic()


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def prepare_evidence(root):
    for path in (root, *root.parents):
        if path.exists() and is_link(path):
            raise ValueError("Evidence directory must not traverse links or junctions")
    if root == REPO or REPO in root.parents:
        raise ValueError("Evidence must be outside the committed worktree")
    if root.exists() and (not root.is_dir() or any(root.iterdir())):
        raise ValueError("Evidence directory must be new and empty")
    root.mkdir(parents=True, exist_ok=True)


def run_command(row):
    environment = dict(os.environ)
    environment.pop("JAVA_TOOL_OPTIONS", None)
    with (Path(row["output"]) / "runner.log").open("w", encoding="utf-8") as log:
        return subprocess.run(row["command"], cwd=REPO, env=environment, stdout=log, stderr=subprocess.STDOUT).returncode


def run_campaign(args):
    plan = make_plan(args)
    root = Path(plan["evidence_root"])
    if not args.dry_run and plan["requirements"]:
        raise ValueError("Selected families require explicit historical inputs before any native start: "
                         + ", ".join(plan["requirements"]))
    # Validate the caller's spelling too: resolve() alone would hide junctions.
    prepare_evidence(args.evidence_root.absolute())
    write_json(root / "plan.json", plan)
    summary_path = root / ("campaign-" + args.suffix + ".json")
    summary = dict(schema_version=1, status="DRY_RUN" if args.dry_run else "IN_PROGRESS",
                   source_commit=plan["source_commit"], candidate_commit=plan["candidate_commit"],
                   jar_sha256=plan["jar_sha256"], partial=plan["partial"], families=[])
    write_json(summary_path, summary)
    lock = None if args.dry_run else HeavyLock()
    blocked = {}
    for family in plan["families"]:
        output = Path(family["output"])
        output.mkdir()
        row = dict(name=family["name"], runner=family["runner"], command=family["command"],
                   source_commit=plan["source_commit"], candidate_commit=plan["candidate_commit"],
                   jar_sha256=plan["jar_sha256"], production_diff_exit=None, lock_owner=family["lock_owner"],
                   lock_acquired=False, status="PLANNED", started=utc_now())
        invocation = output / "invocation.json"
        write_json(invocation, row)
        if args.dry_run:
            row.update(status="DRY_RUN", reason="No preflight, lock, runner or Java start")
        elif family["runner"] in blocked:
            row.update(status="NOT_RUN", reason="Runner stopped after failed family: " + blocked[family["runner"]])
        else:
            taken = False
            try:
                row["before_lock"] = preflight(plan)
                row["production_diff_exit"] = row["before_lock"]["production_diff_exit"]
                lock.acquire(row["lock_owner"])
                taken = True
                row["lock_acquired"] = True
                # Recheck after a possibly three-hour wait, before starting the child.
                row["after_lock"] = preflight(plan)
                row["status"] = "RUNNING"
                write_json(invocation, row)
                row["exit_code"] = run_command(family)
                row["status"] = "PASS" if row["exit_code"] == 0 else "FAILED"
                if family.get("w336_family") == "downgrade":
                    # Preserve the historical whole-world verdict, not an expected-failure pass.
                    report = output / "native/results.json"
                    row["runner_status"] = json.loads(report.read_text(encoding="utf-8"))["status"] if report.is_file() else None
                    if row["runner_status"] != "PASS":
                        row["status"] = "FAILED"
                row["after_runner"] = preflight(plan)
            except Exception as failure:
                row.update(status="DRIVER_FAILED", error=str(failure))
                if isinstance(failure, PreconditionError):
                    row["refusal"] = failure.checks
                    row["production_diff_exit"] = failure.checks["production_diff_exit"]
            finally:
                if taken:
                    try:
                        lock.release(row["lock_owner"])
                        row["lock_released"] = True
                    except Exception as failure:
                        row.update(status="DRIVER_FAILED", lock_release_error=str(failure), lock_released=False)
                # Observation only. Cleanup belongs exclusively to each runner's ownership checks.
                write_json(output / "cleanup.json", dict(session=family["session"],
                           session_exists=Path(family["session"]).exists(), driver_deleted=False,
                           runner_cleanup_report="native/results.json", ended=utc_now()))
            if row["status"] != "PASS":
                blocked[family["runner"]] = family["name"]
        row["ended"] = utc_now()
        write_json(invocation, row)
        summary["families"].append(row)
        write_json(summary_path, summary)
    if not args.dry_run:
        summary["status"] = ("PASS_SELECTED" if plan["partial"] else "PASS") if all(
            row["status"] == "PASS" for row in summary["families"]) else "FAILED"
    summary["ended"] = utc_now()
    write_json(summary_path, summary)
    write_json(root / ("campaign-" + args.suffix + ".done"), dict(status=summary["status"], ended=summary["ended"]))
    print(json.dumps(plan if args.dry_run else summary, indent=2))
    return 1 if summary["status"] == "FAILED" else 0


def run_reuse_under_lock(args, pin):
    """Cargo's existing reuse orchestration, but the parent already owns the lock.

    Only lock/session orchestration differs from cargo.run_loader: do not nest a
    lock or substitute its owner. Existing run_cut code, pins and oracles stay intact.
    """
    # The ordinary wrapper permits no owner for reuse because it takes its own
    # lock. Its private campaign dispatch must not inherit that permission.
    if not args.lock_owner:
        raise ValueError("Cargo reuse under the campaign lock requires a nonempty lock owner")
    runner = cargo.REUSE[args.family][0]
    args.session_parent = args.session_dir.resolve()
    if not args.session_parent.is_dir() or is_link(args.session_parent) or args.evidence_dir.exists():
        raise ValueError("Loader reuse requires an existing non-linked session parent and new evidence")
    cargo.fluid.native.require_lock(args.lock_owner)
    args.evidence_dir.mkdir(parents=True)
    args.follow_ups = False
    args.require_initial_prefix = None
    args.diagnose_unload = False
    pins = dict(pin, hooks={p: cargo.fluid.native.digest(REPO / p) for p in (
        runner.HOOK_FILES if runner is cargo.loader else runner.HOOKS)},
        harness={p: cargo.fluid.native.digest(REPO / p) for p in (
            runner.HARNESS_FILES if runner is cargo.loader else runner.HARNESSES)})
    report = dict(status="IN_PROGRESS", pins=pins, cases=[], lock_owner=args.lock_owner)
    try:
        for index, case in enumerate(args.case):
            if index:
                time.sleep(90)
            cargo.fluid.native.require_lock(args.lock_owner)
            cargo.verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.candidate_sha256)
            row = runner.run_cut(args, case, args.evidence_dir / case, args.lock_owner, pins)
            report["cases"].append(row)
            if row["status"] != "PASS":
                raise ValueError("First contradictory shared-infrastructure cut: " + case)
        report["status"] = "PASS_SELECTED"
    except Exception:
        report["status"] = "FAILED"
        raise
    finally:
        write_json(args.evidence_dir / "results.json", report)


def cargo_under_lock(argv):
    original, arguments = cargo.run_loader, sys.argv
    try:
        cargo.run_loader = run_reuse_under_lock
        sys.argv = [WRAPPER, *argv]
        return cargo.main()
    finally:
        cargo.run_loader, sys.argv = original, arguments


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv and argv[0] == "--cargo-under-lock":
        return cargo_under_lock(argv[1:])
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-commit", required=True)
    for flag in ("jar", "template", "evidence-root"):
        parser.add_argument("--" + flag, type=Path, required=True)
    parser.add_argument("--jar-sha256", required=True)
    parser.add_argument("--only", action="append", nargs="+", help="Family names (space or comma separated); partial evidence")
    parser.add_argument("--suffix", default="attempt1")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--java", default=str(Path(os.environ.get("JAVA_HOME", "C:/Program Files/Java/jdk-17.0.7")) / "bin/java.exe"))
    parser.add_argument("--old-jar", type=Path, help="Explicit historical G2 JAR; never guessed")
    parser.add_argument("--old-source-commit", help="Historical G2 JAR's source commit")
    parser.add_argument("--downgrade-old-jar", type=Path, help="W176 historical JAR for W336; separate from G2")
    parser.add_argument("--downgrade-old-jar-sha256", help="SHA-256 of the W176 historical JAR")
    args = parser.parse_args(argv)
    if args.only:
        args.only = [name for group in args.only for name in group]
    try:
        return run_campaign(args)
    except (OSError, ValueError, subprocess.CalledProcessError) as failure:
        print("FAIL: " + str(failure), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
