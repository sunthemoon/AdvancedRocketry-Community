#!/usr/bin/env python3
"""W373B: native accounting cuts (W357-DESIGN section 7.3). One forced stop and two restarts per case, on a pinned
candidate JAR, with the exact account oracle of accounting_cut_oracle. The caller holds the heavy-JVM lock.

Cargo cases run the gated cargo probes directly. Fuel loader cases call run_v180_loader_cuts.run_cut unchanged and
then apply the account oracle to the disks that runner decoded.

W373C: the fuel loader's operator settlement. The existing LF_S6_ operator cuts (O1 to O4) call
run_v180_loader_s6.run_cut unchanged, with its own log and raw oracle, and then the account oracle. The four
LF_S6_AC_ cases (pruning before and after its save, a known loss and a known gain) have no expectation in that
runner, so they run here with the loader's independent disk reader and the same account oracle.
"""
from __future__ import annotations

import copy
import json
from pathlib import Path
import re
import shutil

import accounting_cut_oracle as oracle
import run_dedicated_server_smoke as smoke
import run_v180_loader_cuts as loader
import run_v180_loader_s6 as loader_s6
import w336_native as native
from run_v180_escrow_forced_stop import LEDGER, digest, ledger, record
from run_v180_escrow_pre_f2 import journal
from run_v180_rocket_settlement_completion import slot_present

REPO = Path(__file__).resolve().parents[1]
# The inventory of native case producers recognises a runner by this literal property.
CASE_PROPERTY = "-Dadvancedrocketrycommunity.escrowCase="
CASE_KEY = CASE_PROPERTY.removeprefix("-Dadvancedrocketrycommunity.").removesuffix("=")
DEVICE_POS = [64, 100, 64]
ORDINARY = ("L1_BEFORE", "L1_REPLACED", "L1_ACK", "C6_JOINT_READBACK", "L2_REPLACED", "C7_ACKNOWLEDGED")
# The probe's checkpoint label at each ordinary cut, and at the two ambiguous-replacement cuts.
CHECKPOINTS = {"L1_BEFORE": "K1", "L1_REPLACED": "K2", "L1_ACK": "L1_ACK", "C6_JOINT_READBACK": "C6_JOINT_READBACK",
               "L2_REPLACED": "K9", "C7_ACKNOWLEDGED": "C7_ACKNOWLEDGED", "E4L1_UNKNOWN": "WRITE_UNKNOWN",
               "E4L2_UNKNOWN": "WRITE_UNKNOWN"}
SETTLEMENT = ("O1_BEFORE", "O1_AFTER", "AC_O1_ACK", "O3_BEFORE", "O3_AFTER", "AC_O3_ACK", "O4_BEFORE", "O4_AFTER",
              "AC_O4_ACK", "PRUNE_BEFORE", "PRUNE_AFTER")
# The settlement probe's boundary name at each cut.
BOUNDARIES = {"AC_O1_ACK": "O1_ACK", "AC_O3_ACK": "O3_ACK", "AC_O4_ACK": "O4_ACK", "AC_LOSS": "O1_AFTER",
              "AC_GAIN": "O1_AFTER", "AC_SUBSTITUTE": "O1_AFTER", "AC_READBACK": "READBACK_HELD",
              "ABANDON_ROCKET": "O1_AFTER", "ABANDON_DEVICE": "O1_AFTER"}
OUTCOMES = ("AC_LOSS", "AC_GAIN", "AC_SUBSTITUTE", "ABANDON_ROCKET", "ABANDON_DEVICE", "AC_READBACK")
LOADER_CUTS = tuple("LF_M_D2_" + name for name in ("C1_BEFORE", "C1_REPLACED", "C1_ACK", "C7_BEFORE", "C7_REPLACED", "C7_ACK"))
LOADER_OUTCOMES = ("LF_M_C2", "LF_M_C2_PARTIAL_REPLACED", "LF_M_E4L1_UNKNOWN", "LF_M_E4L2_UNKNOWN")
# W373C: the loader's operator settlement at O1, O2, O3 and O4, each before and after its write. These thirteen probe
# cases exist; the S6 loader runner keeps its unchanged oracle for them.
LOADER_SETTLEMENT = tuple("LF_S6_" + name for name in (
    "O1_BEFORE", "O1_REPLACED", "O1_ACK", "O2_BEFORE_0", "O2_AFTER_0", "O2_BEFORE_1", "O2_AFTER_1",
    "O3_BEFORE", "O3_REPLACED", "O3_ACK", "O4_BEFORE", "O4_REPLACED", "O4_ACK"))
# W373C's gated cases, and the hook each one's forced stop reports.
LOADER_ACCOUNTING = {"LF_S6_AC_PRUNE_BEFORE": "PRUNE_BEFORE", "LF_S6_AC_PRUNE_AFTER": "PRUNE_AFTER",
                     "LF_S6_AC_LOSS": "K2", "LF_S6_AC_GAIN": "K2", "LF_S6_AC_SUBSTITUTE": "K2",
                     "LF_S6_AC_BANK_LOSS": "K2", "LF_S6_AC_BANK_GAIN": "K2",
                     "LF_S6_AC_PRUNE_SPLIT_TANK": "PRUNE_SPLIT_TANK", "LF_S6_AC_PRUNE_SPLIT_BANK": "PRUNE_SPLIT_BANK"}
# The readback case's first hold: the typed per-fluid check of the device slot refuses a vector that differs from the
# frozen one (EscrowCargoReadback, design section 6). Later attempts meet the live check's refusal instead.
TYPED_REFUSAL = "ACCOUNTING_MISMATCH"
LIVE_REFUSAL = "Frozen_settlement_endpoint_changed"


def _cargo(direction):
    load = direction == "LOAD"
    prefix = "FS_LOAD_" if load else "FS_"
    return ([f"FC_{direction}_{cut}" for cut in ORDINARY] + [prefix + cut for cut in SETTLEMENT])


FAMILIES = {
    "cargo-load": tuple(_cargo("LOAD")),
    "cargo-unload": tuple(_cargo("UNLOAD")),
    "loader": LOADER_CUTS + LOADER_SETTLEMENT + tuple(LOADER_ACCOUNTING),
    "outcomes": tuple(prefix + cut for cut in OUTCOMES for prefix in ("FS_", "FS_LOAD_"))
                + ("FC_LOAD_E4L1_UNKNOWN", "FC_LOAD_E4L2_UNKNOWN") + LOADER_OUTCOMES,
}
CASES = tuple(dict.fromkeys(case for cases in FAMILIES.values() for case in cases))


def spec(case):
    """How one probe case is run and which three states the oracle expects of it."""
    if case.startswith("LF_M_"):
        return dict(engine="loader")
    if case in LOADER_SETTLEMENT:
        return dict(engine="loader-s6")
    if case in LOADER_ACCOUNTING:
        state = oracle.loader_settlement(case)[2]
        tank = sum(state["source"]["fluids"].values())
        bank = sum(state["destination"]["fluids"].values())
        return dict(engine="loader-settlement", timeout=420,
                    paused=f"ARCE_LOADER_S6_O_PAUSED cut={case} hook={LOADER_ACCOUNTING[case]}",
                    result=f"ARCE_LOADER_S6_O_RESULT cut={case} tank={tank} bank={bank} epochs=1,1 records=0 receipts=1 "
                           "state=RELEASED accounting=KNOWN")
    if case.startswith("FC_"):
        load = case.startswith("FC_LOAD_")
        cut = case.removeprefix("FC_LOAD_" if load else "FC_UNLOAD_")
        return dict(engine="ordinary", load=load, cut=cut, states=oracle.ordinary(cut), timeout=240,
                    paused="ARCE_RELEASE_CARGO_PAUSED cut=" + case + " ", result="ARCE_RELEASE_CARGO_RESULT cut=" + case + " ",
                    boundary="checkpoint=" + CHECKPOINTS[cut] + " ")
    load = case.startswith("FS_LOAD_")
    cut = case.removeprefix("FS_LOAD_" if load else "FS_")
    return dict(engine="settlement", load=load, cut=cut, states=oracle.settlement(cut, load), timeout=420,
                paused="ARCE_RELEASE_CARGO_CUT_PAUSED cut=" + case + " ", result="ARCE_RELEASE_CARGO_CUT_RESULT cut=" + case + " ",
                boundary="boundary=" + BOUNDARIES.get(cut, cut))


def rockets(world):
    """Rocket entities of the fixture chunk [4,4] and, once a flight landed, the pad chunk [5,4]."""
    path = world / "entities/r.0.0.mca"
    found = []
    for slot in (132, 133):
        if slot_present(path, slot):
            found += [entry for entry in record(path, slot)["Entities"] if "RocketEntityData" in entry]
    return found


def fluid_store(fluid, section):
    """A raw store from a saved fluid and its escrow section; a tagged fluid is not one of this fixture's keys."""
    oracle.require(not fluid or isinstance(fluid.get("FluidName"), str) and "Tag" not in fluid and fluid.get("Amount", 0) > 0,
                   "A cargo store holds a malformed or tagged fluid")
    return dict(fluids={fluid["FluidName"]: fluid["Amount"]} if fluid else {}, epoch=section["resource_epoch"],
                markers=len(section["markers"]))


def cargo_disk(world):
    """The ledger and both cargo endpoints, decoded from the stopped world's files only."""
    path = world / "data" / LEDGER
    root = ledger(path) if path.is_file() else None
    found = rockets(world)
    oracle.require(len(found) <= 1, "The world holds more than one cargo rocket")
    rocket = None
    if found:
        data = found[0]["RocketEntityData"]
        blocks = [block["block_entity"] for block in data["snapshot"]["relative_blocks"] if "block_entity" in block]
        oracle.require(len(blocks) == 1 and blocks[0]["adapter"] == "advancedrocketrycommunity:pressurized_tank_v1",
                       "The cargo rocket lost its one tank payload")
        rocket = fluid_store(blocks[0]["data"]["tank"]["fluid"], data["resource_escrow"])
        rocket.update(uuid=found[0]["UUID"], logical=data["assembly_transaction_id"])
    terrain = record(world / "region/r.0.0.mca")
    devices = [entry for entry in terrain["block_entities"] if [entry.get(k) for k in ("x", "y", "z")] == DEVICE_POS
               and "arce_rocket_fluid_cargo" in entry]
    device = None
    if devices:
        saved = devices[0]["arce_rocket_fluid_cargo"]
        device = fluid_store(saved["tank"], saved["resource_escrow"])
        device.update(id=saved["device"], role=saved["role"])
    return dict(ledger=root, rocket=rocket, device=device, journal=len(journal(world)["transfers"]),
                files={name: digest(world / name) for name in ("data/" + LEDGER, "region/r.0.0.mca", "entities/r.0.0.mca")
                       if (world / name).is_file()})


def errors(lines):
    return [line for line in lines if "/ERROR]" in line or "/FATAL]" in line]


def held_reasons(lines):
    """The settlement's reported holds, in order; the probe prints its view with underscores."""
    return [match[1].replace(" ", "_") for line in lines
            for match in [re.search(r"ARCE_ESCROW_SETTLEMENT_HELD digest=\S+ reason=(.+?) records=retained", line)] if match]


def observe(case, plan, start, lines):
    """Exactly one stop or result marker, at the planned boundary, and no ERROR or FATAL line."""
    marker = plan["result"] if start else plan["paused"]
    matches = [line for line in lines if marker in line]
    oracle.require(len(matches) == 1, "Missing or repeated cut observation: " + marker.strip())
    if not start:
        oracle.require(re.search(re.escape(plan["boundary"].rstrip()) + r"(?:\s|$)", matches[0]) is not None,
                       "The probe stopped at another boundary: " + matches[0].strip()[-120:])
    logged = errors(lines)
    oracle.require(not logged, "The native start logged ERROR or FATAL: " + (logged[0].strip()[:200] if logged else ""))
    result = dict(marker=matches[0].strip()[matches[0].index(marker):][:300])
    if plan["cut"] == "AC_READBACK":
        reasons = held_reasons(lines)
        result["held_reasons"] = reasons
        if start:
            oracle.require("state=HELD" in matches[0] and "receipts=0 locked=true" in matches[0],
                           "The restart did not report the retained hold")
        else:
            oracle.require(bool(reasons), "The settlement never reported its hold")
            oracle.require(reasons[0] == TYPED_REFUSAL, "The first hold is not the typed check of the stored device: " + reasons[0])
            oracle.require(set(reasons[1:]) <= {LIVE_REFUSAL}, "A later hold has an unexpected reason: " + ", ".join(reasons[1:]))
    if plan["cut"] in ("AC_LOSS", "AC_GAIN", "AC_SUBSTITUTE", "ABANDON_ROCKET", "ABANDON_DEVICE") and start:
        status = "UNKNOWN" if plan["cut"].startswith("ABANDON") else "KNOWN"
        oracle.require("state=RELEASED" in matches[0] and "accounting=" + status in matches[0],
                       "The restart did not report the released decision with its knowledge")
    return result


def check_cargo(disk, plan, start):
    """The account oracle on one decoded cargo state: the device is the source of a load, the rocket of an unload."""
    source, destination = (disk["device"], disk["rocket"]) if plan["load"] else (disk["rocket"], disk["device"])
    summary = oracle.check_state(disk["ledger"], source, destination, plan["states"][start])
    oracle.require(disk["journal"] == 0, "A cargo accounting cut left a transfer journal record")
    return summary


def run_cargo(run, case, plan, row):
    """The three starts of one cargo case. A failed start stops the case; an oracle finding does not hide later starts."""
    world = run.session / ("world-" + case)
    roots = []
    for start in range(3):
        entry = dict(start=start); row["starts"].append(entry)
        process = run.start(f"{case}-{start}", world=world.name,
                            properties={"escrowScenario": "recover" if start else "start", CASE_KEY: case})
        try:
            process.wait_for(re.compile(re.escape(plan["result"] if start else plan["paused"])), plan["timeout"])
            if start:
                run.stop(f"{case}-{start}", audit=lambda lines: None)
            else:
                process.process.kill(); process.abort(); run.process = None; entry["forced_stop"] = True
        except (OSError, ValueError, smoke.SmokeError) as failure:
            if run.process is not None:
                run.process.abort(); run.process = None
            entry.update(status="FAILED", error="Start execution: " + str(failure))
            row["findings"].append(f"start {start}: {entry['error']}")
            entry["native_files"] = run.capture(world, f"{case}-{start}-failed-world") if world.is_dir() else None
            return
        try:
            disk = cargo_disk(world)
            raw = run.evidence / f"{case}-{start}-raw.json"
            raw.write_text(json.dumps(disk, indent=1), encoding="utf-8")
            entry["raw"] = dict(file=raw.name, sha256=digest(raw))
            entry["observation"] = observe(case, plan, start, process.lines)
            entry["oracle"] = check_cargo(disk, plan, start)
            if roots and roots[-1] is not None and disk["ledger"] is not None:
                oracle.check_progress(roots[-1], disk["ledger"])
            roots.append(disk["ledger"])
            entry["status"] = "PASS"
        except (OSError, ValueError, KeyError, TypeError, IndexError, smoke.SmokeError) as failure:
            entry.update(status="FAILED", error=str(failure)); row["findings"].append(f"start {start}: {failure}")
            entry["native_files"] = run.capture(world, f"{case}-{start}-failed-world")
            roots.append(None)
    shutil.rmtree(world)


def loader_parent(run):
    """A short sibling of the session for the loader runner's own sessions. Nested under the campaign's session
    name, the copied Forge libraries exceed Windows' 260-character path limit."""
    parent = run.session.parent / ("AL-" + run.token[:12])
    native.require(parent.parent == native.TEMP.resolve() and not smoke.is_link_or_junction(parent), "Unsafe loader session parent")
    parent.mkdir(exist_ok=True)
    return parent


def delegate(run, case, row, run_cut, hooks, harness, label, disk_of, check):
    """An unchanged loader runner and its oracle first, then the account oracle on the disks that runner decoded."""
    args = copy.copy(run.args)
    args.session_parent = loader_parent(run)
    args.follow_ups = False
    pins = dict(run.pin, hooks={p: digest(REPO / p) for p in hooks}, harness={p: digest(REPO / p) for p in harness})
    result = run_cut(args, case, run.evidence / case, args.lock_owner, pins)
    row["loader_status"] = result["status"]
    if result["status"] != "PASS":
        row["findings"].append(label + ": " + str(result.get("error", result.get("reason", result.get("findings") or result["status"])))[:600])
    previous = None
    for start in result["starts"]:
        entry = dict(start=start["restart"]); row["starts"].append(entry)
        try:
            disk = disk_of(start)
            oracle.require(isinstance(disk, dict), "The loader runner decoded no disk for this start")
            entry["oracle"] = check(disk, case, start["restart"])
            root = disk["ledger"]
            if previous is not None and root is not None:
                oracle.check_progress(previous, root)
            previous = root
            entry["status"] = "PASS"
        except (ValueError, KeyError, TypeError, IndexError, smoke.SmokeError) as failure:
            entry.update(status="FAILED", error=str(failure)); row["findings"].append(f"start {start['restart']}: {failure}")
    if len(result["starts"]) != 3:
        row["findings"].append(f"The loader runner completed {len(result['starts'])} of 3 starts")
    # The loader runner removes its own session; this parent is removed only when nothing is left in it.
    row["loader_parent_left"] = sorted(p.name for p in args.session_parent.iterdir())
    if not row["loader_parent_left"]:
        args.session_parent.rmdir()


def run_loader(run, case, row):
    """L1, L2, the chain and the ambiguous replacement, through run_v180_loader_cuts."""
    delegate(run, case, row, loader.run_cut, loader.HOOK_FILES, loader.HARNESS_FILES, "loader runner",
             lambda start: start.get("disk"), oracle.check_loader)


def s6_disk(start):
    """The S6 runner's independently decoded image of one start; an unreadable image is never a disk."""
    image = start.get("image") or {}
    return image.get("native") if image.get("read_status") == "KNOWN" else None


def run_loader_s6(run, case, row):
    """The loader's operator settlement at O1 to O4, through run_v180_loader_s6 and its unchanged oracle."""
    delegate(run, case, row, loader_s6.run_cut, loader_s6.HOOKS, loader_s6.HARNESSES, "loader S6 runner",
             s6_disk, oracle.check_loader_settlement)


def observe_loader(plan, start, lines):
    """Exactly one stop or result line that ends with the exact expected text, and no ERROR or FATAL line."""
    marker = plan["result"] if start else plan["paused"]
    matches = [line.strip() for line in lines if line.rstrip().endswith(marker)]
    label = "ARCE_LOADER_S6_O_" + ("RESULT" if start else "PAUSED")
    oracle.require(len(matches) == 1 and sum(label in line for line in lines) == 1,
                   "Missing, repeated or different loader settlement observation: " + marker)
    logged = errors(lines)
    oracle.require(not logged, "The native start logged ERROR or FATAL: " + (logged[0].strip()[:200] if logged else ""))
    return dict(marker=matches[0][matches[0].index(label):][:300])


def run_loader_settlement(run, case, plan, row):
    """The three starts of one LF_S6_AC_ case: the loader's own disk reader, then the exact account oracle."""
    world = run.session / ("world-" + case)
    previous = None
    for start in range(3):
        entry = dict(start=start); row["starts"].append(entry)
        process = run.start(f"{case}-{start}", world=world.name,
                            properties={"escrowScenario": "recover" if start else "start", CASE_KEY: case})
        try:
            process.wait_for(re.compile(re.escape(plan["result"] if start else plan["paused"]) + r"\s*$"), plan["timeout"])
            if start:
                run.stop(f"{case}-{start}", audit=lambda lines: None)
            else:
                process.process.kill(); process.abort(); run.process = None; entry["forced_stop"] = True
        except (OSError, ValueError, smoke.SmokeError) as failure:
            if run.process is not None:
                run.process.abort(); run.process = None
            entry.update(status="FAILED", error="Start execution: " + str(failure))
            row["findings"].append(f"start {start}: {entry['error']}")
            entry["native_files"] = run.capture(world, f"{case}-{start}-failed-world") if world.is_dir() else None
            return
        try:
            disk = loader.oracle.inspect(world)
            raw = run.evidence / f"{case}-{start}-raw.json"
            raw.write_text(json.dumps(disk, indent=1), encoding="utf-8")
            entry["raw"] = dict(file=raw.name, sha256=digest(raw))
            entry["observation"] = observe_loader(plan, start, process.lines)
            entry["oracle"] = oracle.check_loader_settlement(disk, case, start)
            if previous is not None:
                oracle.check_progress(previous, disk["ledger"])
            previous = disk["ledger"]
            entry["status"] = "PASS"
        except (OSError, ValueError, KeyError, TypeError, IndexError, smoke.SmokeError) as failure:
            entry.update(status="FAILED", error=str(failure)); row["findings"].append(f"start {start}: {failure}")
            entry["native_files"] = run.capture(world, f"{case}-{start}-failed-world")
            previous = None
    shutil.rmtree(world)


def family(run):
    cases = run.args.case or FAMILIES[run.args.family]
    native.require(set(cases) <= set(FAMILIES[run.args.family]), "A selected case is not in the requested family")
    report = dict(family=run.args.family, cases=[], partial=bool(run.args.case))
    for case in cases:
        plan = spec(case)
        row = dict(case=case, engine=plan["engine"], starts=[], findings=[]); report["cases"].append(row)
        if plan["engine"] == "loader":
            run_loader(run, case, row)
        elif plan["engine"] == "loader-s6":
            run_loader_s6(run, case, row)
        elif plan["engine"] == "loader-settlement":
            run_loader_settlement(run, case, plan, row)
        else:
            row.update(cut=plan["cut"], direction="LOAD" if plan["load"] else "UNLOAD")
            run_cargo(run, case, plan, row)
        row["status"] = "FAILED" if row["findings"] else "PASS"
        native.write_json(run.evidence / "results.json", dict(status="IN_PROGRESS", **report))
        print(f"{case}: {row['status']}" + ("" if not row["findings"] else " " + "; ".join(row["findings"])[:400]), flush=True)
    # A production defect stops its own case only; every other case still ran and is reported.
    report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["cases"]) else "FAILED"
    return report


def main():
    parser = native.parser(__doc__)
    parser.add_argument("--family", required=True, choices=FAMILIES)
    parser.add_argument("--case", action="append", choices=CASES, help="Selected cases of the family; partial evidence")
    return native.execute(parser.parse_args(), family)


if __name__ == "__main__":
    raise SystemExit(main())
