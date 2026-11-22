#!/usr/bin/env python3
"""Replay checked-store crash prefixes using recorded dedicated-server cuts.

Use a candidate with the gated storage recorder and an installed Forge server
template. Launch this long-running Windows campaign as a detached process; it
takes the machine-wide heavy-JVM lock separately for each case.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import copy
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import time
from types import SimpleNamespace
import uuid

import bay_integrated_evidence as evidence_tools
import run_v180_bay_forced_stop as bay
import run_v180_cargo_cuts as fluid
import run_v180_cargo_s4_s5_cuts as settlement
import run_v180_item_cuts as item
import run_v180_registry_writer_cuts as registry
import v180_crash_replay as replay

RUNNERS = dict(B=bay, F=fluid, S=settlement, I=item, R=registry)
# Design section 3, not a new selection for any existing campaign.
CASES = {
    "X01-01": ("B", "B1_MOVED", "B1_STAGED", "BAY_RECEIPTS", ()),
    "X01-02": ("B", "B3_MOVED", "B3_STAGED", "BAY_RECEIPTS", ("BAY_RECEIPTS", "SATELLITE_MISSIONS")),
    "X01-03": ("B", "B8_MOVED", "B8_STAGED", "BAY_RECEIPTS", ("BAY_RECEIPTS", "SATELLITE_MISSIONS")),
    "X01-04": ("F", "FC_LOAD_L1_REPLACED", "FC_LOAD_L1_BEFORE", "RESOURCE_ESCROW", ()),
    "X01-05": ("F", "FC_LOAD_L2_REPLACED", "FC_LOAD_K8", "RESOURCE_ESCROW", ("RESOURCE_ESCROW",)),
    "X01-06": ("F", "FC_UNLOAD_L2_REPLACED", "FC_UNLOAD_K8", "RESOURCE_ESCROW", ("RESOURCE_ESCROW",)),
    "X01-07": ("F", "FC_LOAD_C6_JOINT_READBACK", None, None, ("RESOURCE_ESCROW",)),
    "X01-08": ("I", "IC_LOAD_K2", "ITEM_EARLIER", "RESOURCE_ESCROW", ()),
    "X01-09": ("I", "IC_UNLOAD_K9", "ITEM_EARLIER", "RESOURCE_ESCROW", ("RESOURCE_ESCROW",)),
    "X01-10": ("S", "FF_N1", "FF_N1", "RESOURCE_ESCROW", ()),
    "X01-11": ("S", "FS_O3J_AFTER", "FS_O3J_BEFORE", "ROCKET_TRANSFERS", ("RESOURCE_ESCROW", "ROCKET_TRANSFERS")),
    "X01-12": ("R", "R3", "R2", "SATELLITE_MISSIONS", ()),
}
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")
LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm")
LIMIT = 100 * 1024**2
EVIDENCE_ROOT = Path("D:/GitHub/ARCE-Task-Evidence/x01-crash-replay")


def write_json(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def tree_size(path: Path) -> int:
    return sum(p.stat().st_size for p in path.rglob("*") if p.is_file())


def preflight() -> None:
    if os.name != "nt":
        raise replay.ReplayError("This campaign requires the specified Windows D: workspace")
    for drive in ("C:/", "D:/"):
        if shutil.disk_usage(drive).free < 10 * 1024**3:
            raise replay.ReplayError(f"Under 10 GiB free on {drive}")


@contextmanager
def machine_lock(path=LOCK, timeout=3 * 3600):
    """Atomic directory creation; only this invocation's finally can release it."""
    token = f"W370 pid={os.getpid()} {uuid.uuid4()}"
    deadline = time.monotonic() + timeout
    while True:
        try:
            path.mkdir()
            break
        except FileExistsError:
            if time.time() - path.stat().st_mtime > 3 * 3600 or time.monotonic() >= deadline:
                raise replay.ReplayError("Heavy-JVM lock stale or wait exhausted; not breaking it")
            time.sleep(60)
    owner = path / "owner.txt"
    try:
        owner.write_text(token, encoding="utf-8")
        yield token
    finally:
        if owner.is_file() and owner.read_text(encoding="utf-8-sig").strip() == token:
            owner.unlink(); path.rmdir()


def runner_args(args, scratch: Path, output: Path, case: str, owner: str):
    values = vars(args).copy()
    values.update(session_dir=scratch, evidence_dir=output, case=[case], lock_owner=owner,
                  allow_shim=False, dev=False, tranche=None, stop_on_failure=True,
                  replay_commands=[],
                  candidate_pin=dict(candidate_commit=args.candidate_commit, jar_sha256=args.jar_sha256))
    if getattr(args, "triage_of", None):
        # Pin the changed harness separately. candidate_pin still verifies that
        # its production tree is exactly the original candidate's source.
        values.update(source_commit=args.replay_harness_commit)
        values["candidate_pin"].update(source_commit=args.source_commit,
                                       harness_commit=args.replay_harness_commit)
    return SimpleNamespace(**values)


def read_result(output: Path) -> dict:
    path = output / "results.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def keep_logs(output: Path, evidence: Path, label: str) -> dict[int, list[str]]:
    markers = {}
    for path in sorted(output.glob("*.log")):
        raw = path.read_bytes()
        text = raw.decode("utf-8", errors="replace")
        selected = [line for line in text.splitlines() if "ARCE_" in line or "/ERROR]" in line or "/FATAL]" in line]
        number = int(path.stem.rsplit("-", 1)[1])
        markers.setdefault(number, []).extend(selected)
        name = f"{label}-{path.stem}.log.gz"
        target = evidence / "logs" / name
        target.parent.mkdir(exist_ok=True)
        target.write_bytes(gzip.compress(raw, mtime=0))
    return markers


def run_existing(module, args, output: Path) -> tuple[dict, str | None]:
    error = None
    try:
        module.run(args)
        bay.assert_pin(args.source_commit)
        evidence_tools.candidate_pin(args.jar, args.jar_sha256, args.candidate_commit)
    except Exception as failure:
        error = f"{type(failure).__name__}: {failure}"
    result = read_result(output)
    if not error and result.get("status") != "PASS":
        error = "Existing runner did not pass: " + str(result.get("error", result.get("status")))
    return result, error


def side_for(state: replay.State, recorded: str, old: str | None, store=None, target=None) -> str:
    sides = [d["kind"] for d in state.derivations if d["kind"] != "LATE-LOSS" and (store is None or d["store"] == store)]
    if target and any(target in d.get("targets", []) for d in state.derivations if d["kind"] == "LATE-LOSS"):
        return old or recorded
    return recorded if not sides or "S-NEW" in sides else old or recorded


def case_plan(trace: Path, kill: Path, case: str) -> list[replay.State]:
    _, _, _, store, late = CASES[case]
    writes = replay.read_trace(trace, kill)
    if store and not any(w.store == store and not w.complete and w.phase == "move" for w in writes):
        raise replay.ReplayError(f"{case}: no moved, unacknowledged {store} write recorded")
    if store is None and any(not w.complete for w in writes):
        raise replay.ReplayError(f"{case}: unexpected unfinished seam write at the barrier")
    # Start a continuous replay window at the last completed write of each named
    # late-loss store. This excludes fixture/bootstrap generations; every earlier
    # generation inside the window still maps to its window-start pre-image.
    starts = []
    for name in late:
        completed = [w.seq for w in writes if w.complete and w.store == name]
        if not completed:
            raise replay.ReplayError(f"{case}: missing completed {name} write in late-loss window")
        starts.append(max(completed))
    return replay.plan(trace, kill, set(late), min(starts, default=0))


def outcomes(case: str) -> dict:
    _, recorded, old, _, _ = CASES[case]
    value = dict(new_oracle=recorded, old_oracle=old, late_loss_oracles=[x for x in (recorded, old) if x],
                allowed_markers=[],
                rule="An existing oracle must pass on both starts; a marker alone never grants PASS.",
                protected_roots="Byte identity alone never substitutes for resource conservation.")
    if case == "X01-10":
        value.update(old_oracle="FF_N1 before creation: absent ledger, exact neutral endpoints, 5000 mB water, 1000 mB fuel",
                     rule="Old side uses check_before_creation on both saves; S-NEW keeps the unchanged FF_N1 oracle.")
    return value


def evidence_target(args) -> dict | None:
    """Triage is a new sibling, never an append to either sealed run."""
    original = EVIDENCE_ROOT / args.candidate_commit
    triage = getattr(args, "triage_of", None)
    intended = original.with_name(original.name + "-triage") if triage else original
    evidence = args.evidence_dir.resolve()
    if evidence != intended.resolve() or evidence.exists():
        raise replay.ReplayError("Evidence must be a new candidate directory (or opt-in <SHA>-triage sibling)")
    if not triage:
        return None
    manifest = Path(triage)
    if manifest.resolve() != (original / "MANIFEST.json").resolve() or not manifest.is_file() or manifest.is_symlink():
        raise replay.ReplayError("Triage requires the original run's MANIFEST.json")
    raw = manifest.read_bytes()
    value = json.loads(raw)
    if any(value.get(k) != getattr(args, k) for k in ("source_commit", "candidate_commit", "jar_sha256")) or value.get("model") != replay.MODEL:
        raise replay.ReplayError("Triage pins/model differ from the original run")
    if value.get("status") not in ("PASS", "FAIL"):
        raise replay.ReplayError("Original run is not finished")
    return dict(manifest=str(manifest.resolve()), sha256=hashlib.sha256(raw).hexdigest())


def check_size(evidence: Path) -> None:
    if tree_size(evidence) > LIMIT:
        reduced = []
        for results in evidence.glob("cases/*/results.jsonl"):
            rows = [json.loads(line) for line in results.read_text(encoding="utf-8").splitlines()]
            passed = {r["state"] for r in rows if r["status"] == "PASS"}
            failed = {r["state"] for r in rows if r["status"] != "PASS"}
            for state in passed - failed:
                for path in (results.parent / "logs").glob(state + "-*.log.gz"):
                    lines = gzip.decompress(path.read_bytes()).splitlines(keepends=True)
                    selected = b"".join(line for line in lines if b"ARCE_" in line or b"/ERROR]" in line or b"/FATAL]" in line)
                    path.write_bytes(gzip.compress(selected, mtime=0))
                    reduced.append(path.relative_to(evidence).as_posix())
        if reduced:
            path = evidence / "LOG-REDUCTION.json"
            previous = json.loads(path.read_text()) if path.exists() else []
            write_json(path, sorted(set(previous + reduced)))
    if any(p.stat().st_size > 50 * 1024**2 for p in evidence.rglob("*") if p.is_file()):
        raise replay.OverBound("OVER_BOUND: evidence file exceeds 50 MiB")
    if tree_size(evidence) > LIMIT:
        raise replay.OverBound("OVER_BOUND: evidence exceeds 100 MiB; campaign stopped, no silent pruning")


def run_case(args, case: str, evidence: Path, scratch: Path, owner: str) -> dict:
    alias, recorded, old, store, _ = CASES[case]
    module = RUNNERS[alias]
    evidence.mkdir(); scratch.mkdir()
    kill = scratch / "kill"
    trace = evidence / "trace"
    context = None

    def capture(world, row):
        nonlocal context
        replay.materialize(world, kill, replay.State("kill", {}))
        if alias in ("B", "R"):
            context = {key: copy.deepcopy(row[key]) for key in ("fixture", "first_start") if key in row}
        else:
            context = dict(starts=[dict(restart=0, disk=copy.deepcopy(row["starts"][0]["disk"]))], raw0=row["raw0"])
        write_json(evidence / "capture-context.json", context)

    start = runner_args(args, TEMP / ("w370-" + uuid.uuid4().hex), scratch / "record", recorded, owner)
    start.storage_trace, start.replay_capture = trace, capture
    result, error = run_existing(module, start, start.evidence_dir)
    commands = list(start.replay_commands)
    write_json(evidence / "commands.json", commands)
    markers = keep_logs(start.evidence_dir, evidence, "record")
    if error or context is None or any("ARCE_RELEASE_STORAGE_TRACE_INVALID" in line for group in markers.values() for line in group):
        raise replay.ReplayError(error or "Missing capture or invalid trace marker")
    states = case_plan(trace, kill, case)
    primary = next((w.target for w in reversed(replay.read_trace(trace, kill)) if w.store == store and not w.complete), None)
    def selected(state):
        return side_for(state, recorded, old, store, primary)
    # No replay server has been started. This is the immutable per-case state table.
    replay.write_table(evidence / "states.json", states, case, lambda s: outcomes(case) | dict(selected_oracle=selected(s)))
    check_size(args.evidence_dir)
    rows, errors = [], []
    for state in states:
        preflight()
        side = selected(state)
        expected = recorded if side == "ITEM_EARLIER" else side
        output = scratch / state.id
        restart = runner_args(args, TEMP / ("w370-" + uuid.uuid4().hex), output, expected, owner)
        restart.replay_world, restart.replay_state, restart.replay_context = kill, state, context
        restart.replay_old_item = side == "ITEM_EARLIER"
        restart.replay_old_registry = alias == "R" and side == "R2"
        restart.replay_before_creation = case == "X01-10" and any(
            d["kind"] in ("S-OLD", "S-EMPTY", "S-PREFIX", "S-TORN", "S-STAGED") for d in state.derivations)
        after_first = scratch / (state.id + "-after-first")

        def preserve(world):
            if world.is_dir():
                replay.materialize(world, after_first, replay.State("continuation", {}))

        if alias in ("B", "R"):
            restart.replay_preserve = preserve
        result, error = run_existing(module, restart, output)
        if error:
            errors.append(dict(state=state.id, error=error))
        commands.extend(dict(state=state.id, **c) for c in restart.replay_commands)
        write_json(evidence / "commands.json", commands)
        markers = keep_logs(output, evidence, state.id)
        entries = (result.get("cases") or [{}])[0]
        starts = entries.get("restarts", entries.get("starts", []))
        # B/R ordinarily stop at their first failed restart assertion. Preserve
        # that stopped image before their cleanup, then still exercise start 2.
        if alias in ("B", "R") and error and after_first.is_dir() and not any(c["restart"] == 2 for c in restart.replay_commands):
            second_output = scratch / (state.id + "-second")
            second = copy.copy(restart)
            second.session_dir = TEMP / ("w370-" + uuid.uuid4().hex)
            second.evidence_dir, second.replay_world = second_output, after_first
            second.replay_state = replay.State("continuation", {})
            second.replay_restart_numbers, second.replay_commands = (2,), []
            second.replay_preserve = None
            later, later_error = run_existing(module, second, second_output)
            commands.extend(dict(state=state.id, **c) for c in second.replay_commands)
            write_json(evidence / "commands.json", commands)
            later_entries = (later.get("cases") or [{}])[0]
            starts = list(starts) + later_entries.get("restarts", [])
            markers.update(keep_logs(second_output, evidence, state.id))
            if later_error:
                errors.append(dict(state=state.id, restart=2, error=later_error))
            if second_output.exists():
                shutil.rmtree(second_output)
        if after_first.exists():
            shutil.rmtree(after_first)
        for number in (1, 2):
            observed = next((x for x in starts if x.get("restart") == number), None)
            failed = observed is None or observed.get("status") == "FAILED"
            if observed is not None:
                # Temporary raw archives/decoded files are intentionally deleted. Do
                # not leave evidence references to those disposable paths.
                observed = {k: v for k, v in observed.items() if k not in ("raw", "native", "native_files")}
            row = dict(state=state.id, restart=number, status="FAIL" if failed else "PASS",
                       oracle=side, error=error, observation=observed,
                       markers=markers.get(number, []),
                       late_loss=any(d["kind"] == "LATE-LOSS" for d in state.derivations),
                       in_flight=any(d["kind"] != "LATE-LOSS" for d in state.derivations))
            if row["late_loss"] and not row["in_flight"] and row["status"] == "FAIL":
                row["classification"] = "Windows directory-flush residual; resource loss/duplication requires Root triage"
            rows.append(row)
            with (evidence / "results.jsonl").open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(row) + "\n")
        # Existing runners delete their owned server/world copies in their own finally.
        if output.exists():
            shutil.rmtree(output)
        check_size(args.evidence_dir)
    shutil.rmtree(kill)
    return dict(case=case, states=len(states), starts=len(commands), errors=errors,
                status="PASS" if not errors and all(r["status"] == "PASS" for r in rows) else "FAIL")


def run(args) -> dict:
    preflight()
    if not re.fullmatch(r"[0-9a-f]{40}", args.candidate_commit):
        raise replay.ReplayError("Candidate commit must be a full SHA-1")
    original = evidence_target(args)
    pin = evidence_tools.candidate_pin(args.jar, args.jar_sha256, args.candidate_commit)
    args.replay_harness_commit = pin["harness_commit"]
    bay.assert_pin(args.replay_harness_commit if original else args.source_commit)
    evidence = args.evidence_dir.resolve()
    if evidence.drive.upper() != "D:":
        raise replay.ReplayError("Evidence must be a new x01-crash-replay/<candidate SHA> directory on D:")
    if (not TEMP.is_dir() or TEMP.resolve().drive.upper() != "D:" or TEMP.is_symlink()
            or getattr(TEMP, "is_junction", lambda: False)()):
        raise replay.ReplayError("Unsafe/missing D: temporary root")
    libraries = args.source_server / "libraries"
    if not libraries.is_dir() or any(p.is_symlink() or getattr(p, "is_junction", lambda: False)()
                                     for p in [libraries, *libraries.rglob("*")]):
        raise replay.ReplayError("Unsafe/missing Forge libraries")
    cases = args.case or list(CASES)
    if len(cases) > 12 or len(set(cases)) != len(cases):
        raise replay.OverBound("OVER_BOUND: at most twelve distinct cases")
    scratch = TEMP / ("w370-campaign-" + uuid.uuid4().hex)
    evidence.mkdir(parents=True); scratch.mkdir()
    manifest = dict(model=replay.MODEL, source_commit=args.source_commit, candidate_commit=args.candidate_commit,
                    jar_sha256=args.jar_sha256, cases=cases, uncovered=replay.UNCOVERED,
                    commands=[], results=[], status="IN_PROGRESS")
    if original:
        manifest.update(triage_of=original, harness_commit=args.replay_harness_commit, production_pin=pin,
                        source_server=str(args.source_server.resolve()))
    write_json(evidence / "MANIFEST.json", manifest)
    try:
        for index, case in enumerate(cases):
            if index:
                time.sleep(90)
            preflight()
            case_dir = evidence / "cases"
            case_dir.mkdir(exist_ok=True)
            with machine_lock() as owner:
                try:
                    row = run_case(args, case, case_dir / case, scratch / case, owner)
                except replay.OverBound as failure:
                    target = case_dir / case
                    target.mkdir(exist_ok=True)
                    # Never overwrite a table once replay starts have used it.
                    write_json(target / ("OVER_BOUND.json" if (target / "states.json").exists() else "states.json"),
                               dict(status="OVER_BOUND", error=str(failure)))
                    row = dict(case=case, status="OVER_BOUND", error=str(failure))
                except replay.ReplayError as failure:
                    target = case_dir / case
                    target.mkdir(exist_ok=True)
                    if not (target / "states.json").exists():
                        write_json(target / "states.json", dict(status="REFUSED", error=str(failure)))
                    row = dict(case=case, status="FAIL", error=str(failure))
                finally:
                    if (scratch / case).exists():
                        shutil.rmtree(scratch / case)
            manifest["results"].append(row)
            write_json(evidence / "MANIFEST.json", manifest)
        manifest["status"] = "PASS" if all(r["status"] == "PASS" for r in manifest["results"]) else "FAIL"
        return manifest
    except Exception as failure:
        manifest.update(status="FAIL", error=str(failure))
        raise
    finally:
        shutil.rmtree(scratch)
        # Include actual JVM commands retained by each runner, not a reconstructed command.
        for path in evidence.glob("cases/*/commands.json"):
            manifest["commands"].extend(json.loads(path.read_text(encoding="utf-8")))
        write_json(evidence / "MANIFEST.json", manifest)
        summary = ["# X01 crash replay", "", "Status: " + manifest["status"], "", "| Case | Status | States |", "|---|---|---|"]
        summary.extend(f"| {r['case']} | {r['status']} | {r.get('states', 0)} |" for r in manifest["results"])
        summary.extend(["", "## Uncovered", *["- " + x for x in replay.UNCOVERED]])
        if (evidence / "LOG-REDUCTION.json").exists():
            summary.extend(["", "Passing logs reduced to markers to meet the evidence budget; see LOG-REDUCTION.json."])
        (evidence / "SUMMARY.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
        sums = [f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(evidence).as_posix()}"
                for p in sorted(evidence.rglob("*")) if p.is_file() and p.name != "SHA256SUMS.txt"]
        (evidence / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="ascii")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    for key in ("source-commit", "candidate-commit", "jar-sha256", "java"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--case", action="append", choices=CASES)
    parser.add_argument("--triage-of", type=Path, help="Original MANIFEST.json; permits only a new <SHA>-triage sibling")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--exit-timeout", type=float, default=180)
    parser.add_argument("--flight-timeout", type=float, default=120)
    parser.add_argument("--settle-timeout", type=float, default=180)
    try:
        return 0 if run(parser.parse_args())["status"] == "PASS" else 1
    except (OSError, ValueError, bay.smoke.SmokeError, evidence_tools.CandidateError) as failure:
        print("FAIL: " + str(failure)); return 1


if __name__ == "__main__":
    raise SystemExit(main())
