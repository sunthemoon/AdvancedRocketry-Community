#!/usr/bin/env python3
"""X02: storage faults at the checked-writer seam, on recorded dedicated-server cases (W368 section 4).

A live case runs one existing native cut with the gated fault injector on its first start
(-Dadvancedrocketrycommunity.storageFault=<store>:<fault>:<count>[:<skip>]), then the runner's own two restarts with
storage healthy. An offline case records the same cut, damages one store file in a copy of the kill image, and
runs the same two restarts. Each completed case gets exactly one class: SEEN_HELD, CAUGHT_LATER or NOT_CAUGHT.
No oracle of an existing runner changes, and a NOT_CAUGHT case is reported as a residual. A case without an outcome
(NOT_RUN, INCOMPLETE, NOT_APPLICABLE) and a case whose faulted start failed an existing check although both healthy
restarts passed (NEEDS_REVIEW) have no class. NOT_RUN, INCOMPLETE and NEEDS_REVIEW make the campaign INCOMPLETE; a
case that the plan declares NOT_APPLICABLE before the run is listed as uncovered scope.

Only the frozen plan gives NOT_APPLICABLE (Root decision R491, answer 4). The plan is frozen in two steps, both
before the first case runs: the declarations that follow from the case itself, and a planning phase that records the
native cut of each offline group and declares, with the facts of that recording, the cases whose damage the kept
image cannot carry. An inability that shows only while a case runs is INCOMPLETE.

Model v4 targets L1 rather than fixture creation for the four ledger move/read faults, and O3 journal removal rather
than its two setup writes for the journal's four-write error. Their original bootstrap experiments remain available
through explicit -S0 identifiers. This changes the declared injection position only;
the classification rules, detection/hold markers, existing oracles, counts and timeouts are unchanged.

The manifest declares, before any case runs, the hold markers of each store (Root decision R486). A hold line at
ERROR is accepted only in the faulted start of that store and only after the injection. The existing runner's check
of that start is not edited: its result is recorded, and beside it this runner's own evaluation of the same start
with the accepted lines filtered.

Use a candidate with the injector and an installed Forge server template. Launch a long campaign as a detached
process on Windows; it takes the machine-wide heavy-JVM lock separately for each case.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import copy
from dataclasses import dataclass
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import threading
import time
import uuid

import bay_integrated_evidence as evidence_tools
import run_dedicated_server_smoke as smoke
import run_v180_bay_forced_stop as bay
import run_v180_crash_replay as x01
import v180_crash_replay as replay
import v180_fault_candidates as candidates
from campaign_observe.process import read_process

MODEL = "W368-X02-v4"
PROPERTY = "-Dadvancedrocketrycommunity.storageFault="
MARKER = "ARCE_RELEASE_STORAGE_FAULT"
SEEN_HELD, CAUGHT_LATER, NOT_CAUGHT = "SEEN_HELD", "CAUGHT_LATER", "NOT_CAUGHT"
CLASSES = (SEEN_HELD, CAUGHT_LATER, NOT_CAUGHT)
# A run that never produced an outcome has no class. It is reported and never counted as held.
NOT_RUN, INCOMPLETE, NOT_APPLICABLE = "NOT_RUN", "INCOMPLETE", "NOT_APPLICABLE"
# The fault was seen and the durable outcome passed, but an existing check of the faulted start failed. It is neither
# counted as held nor reported as a residual until a reader has decided.
NEEDS_REVIEW = "NEEDS_REVIEW"

# The four stores of Root decision R461 (Q5): ManagedSavedDataType name -> file under <world>/data.
STORES = {
    "RESOURCE_ESCROW": "advancedrocketrycommunity_resource_escrow.dat",
    "BAY_RECEIPTS": "advancedrocketrycommunity_bay_receipts.dat",
    "ROCKET_TRANSFERS": "advancedrocketrycommunity_rocket_transfers.dat",
    "SATELLITE_MISSIONS": "advancedrocketrycommunity_satellite_missions.dat",
}
# ReleaseTestStorageFault.Fault -> the row of W368-DESIGN section 4.
LIVE_FAULTS = {
    "WRITE_ERROR": "F1", "DISK_FULL": "F2", "DISK_FULL_OPEN": "F2", "FLUSH_ERROR": "F3", "MOVE_ERROR": "F4",
    "MOVE_DENIED": "F4", "MOVE_AFTER": "F4", "CLOSE_ERROR": "F5", "LOST_WRITE": "F6", "DELETE_ERROR": "F8",
    "READ_ERROR": "F9", "READ_CORRUPT": "F9",
}
# Faults whose injected move renames first; the two READ kinds also fail the readback that follows.
MOVED_FAULTS = ("MOVE_AFTER", "READ_ERROR", "READ_CORRUPT")
READ_FAULTS = ("READ_ERROR", "READ_CORRUPT")
OFFLINE_FAULTS = {"STALE": "F7a", "ZERO_LENGTH": "F7b", "ZEROED_TAIL": "F7c", "BIT_FLIP": "F10", "TRUNCATED": "F10"}
# Runner alias (run_v180_crash_replay.RUNNERS) and the existing cut. A live case needs the store's write inside the
# first start; an offline case needs a completed earlier write of the store before the cut, for STALE.
LIVE_NATIVE = {
    "RESOURCE_ESCROW": ("F", "FC_LOAD_L1_REPLACED"),
    "BAY_RECEIPTS": ("B", "B1_MOVED"),
    "ROCKET_TRANSFERS": ("S", "FS_O3J_AFTER"),
    "SATELLITE_MISSIONS": ("R", "R3"),
}
OFFLINE_NATIVE = {
    "RESOURCE_ESCROW": ("F", "FC_LOAD_L2_REPLACED"),
    "BAY_RECEIPTS": ("B", "B3_MOVED"),
    "ROCKET_TRANSFERS": ("S", "FS_O3J_AFTER"),
    "SATELLITE_MISSIONS": ("B", "B3_MOVED"),
}
# The bay receipt writer ends a generation whose write failed before the move: the deployment is cancelled and the
# rocket flies on with its satellite, so the cut of B1_MOVED is never reached (W379 smoke 1). For those faults the
# case is the outcome that the existing case B12_FAILED already checks (landed, nothing consumed, no receipt, on the
# first start and both restarts), with the injector as the cause of the failed write instead of B12_FAILED's blocked
# staging path.
BAY_DEFINITE = ("WRITE_ERROR", "DISK_FULL", "DISK_FULL_OPEN", "FLUSH_ERROR", "CLOSE_ERROR", "LOST_WRITE", "DELETE_ERROR")
BAY_FAILED_CASE = "X02_D1_FAILED"
# A receipt move that fails after the rename is resolved COMMITTED by the writer's readback, without a retry. The
# INTENT write is then complete before the cut of B1_MOVED is reached, the deployment goes on, and the armed cut
# pauses the next receipt write, the REGISTERED one (W379F smoke 1: the kill image had a REGISTERED receipt, and the
# restart landed where B1_MOVED expects an interrupted deployment). That image is the one of the existing cut
# B3_MOVED, so that case and its unchanged oracle are used. The two READ faults keep the outcome unknown, the writer
# retries the exact candidate, and B1_MOVED is reached.
BAY_COMMITTED = ("MOVE_AFTER",)
BAY_COMMITTED_CASE = "B3_MOVED"
# W384's native preflight: MOVE_AFTER throws inside the committer before R3's MOVED pause. The writer's readback
# resolves the registration COMMITTED, so there is no retry to reach that pause. R4 holds the terminal effect after
# publication instead; its unchanged checks require the same durable registration and idempotent package replay.
REGISTRY_COMMITTED_CASE = "R4"
# The default campaign: every live fault once, and the write error long enough to pass the three-failure limit.
DEFAULT_COUNTS = {fault: (1, 4) if fault == "WRITE_ERROR" else (1,) for fault in LIVE_FAULTS}
MAX_COUNT = 16
MAX_SKIP = 48
# Root decision R486, answer 1: the hold lines that a store logs at ERROR when a write is not acknowledged. Exactly
# these two are declared. A line is accepted only in the faulted start of its store and only after an injection.
HOLD_MARKERS = {
    "ROCKET_TRANSFERS": ("ARCE_TRANSFER_JOURNAL_UNACKNOWLEDGED",),
    "SATELLITE_MISSIONS": ("ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED",),
}
# Root decision R486, answer 2: the writes of the store that pass before the faulted ones, where a fault on the first
# write cannot reach the cut of the store's live native case.
DEFAULT_SKIP = {
    "SATELLITE_MISSIONS": (1, "Runner R's first registry write is its own baseline request (ARCE_RELEASE_SRW_REQUESTED "
                              "sequence=0), which nothing retries; a fault on it ends the case before the cut (W379 "
                              "smoke 2, write 1). Skipping one write puts the fault on the terminal launch's write, "
                              "which is the write that R3 cuts."),
}
# W446: these faults leave the probe's first (empty baseline) generation UNKNOWN.
# While it retries, the probe queues autosaves instead of creating its rocket.
# Fault the intended L1 write, as the registry cases already skip their baseline.
# The old bootstrap fault remains addressable with an explicit -S0 case ID.
LEDGER_BASELINE_FAULTS = ("MOVE_ERROR", "MOVE_DENIED", "READ_ERROR", "READ_CORRUPT")
CASE_SKIP = {("RESOURCE_ESCROW", fault): (1,
    "Runner F's first ledger write is its empty fixture baseline. An UNKNOWN baseline queues successor autosaves "
    "before the probe creates its rocket; no cargo cut exists to reach. Passing that write puts the fault on L1. "
    "W446's four -S1 native controls passed the unchanged oracles; -S0 retains the original bootstrap experiment.")
    for fault in LEDGER_BASELINE_FAULTS}
# Root decision R491, answer 1: the production lines that show a later start refusing the store. Exactly one is
# declared for each store, and the manifest lists them before any case runs. A line counts only at ERROR, in a restart
# of a case whose faulted store is that store. A release-test assertion, a crash wrapper, an unrelated service error or
# any other line never counts as detection.
DETECTION_MARKERS = {store: (f"[ARCE-BETA-2000] Beta world data check blocked startup; type={store};",) for store in STORES}
MAX_CASES = 96
TEMP = x01.TEMP
LOCK = x01.LOCK
EVIDENCE_ROOT = Path("D:/GitHub/ARCE-Task-Evidence")
LIMIT = 100 * 1024**2
EVIDENCE_LINES = 40
UNCOVERED = [*replay.UNCOVERED,
             "Seam stores other than the ledger, bay receipts, the transfer journal and the satellite registry",
             "A fault in a write that happens before the server has started (migration and the pre-start validators)",
             "A device that reports a flush as done and loses it; STALE, ZERO_LENGTH and ZEROED_TAIL only show the next start"]


class FaultError(ValueError):
    pass


def skip_default(store: str, fault: str | None = None, count: int = 0) -> tuple[int, str]:
    if (store, fault, count) == ("ROCKET_TRANSFERS", "WRITE_ERROR", 4):
        return (2, "Runner S creates an empty journal and its fixture transfer before the O3 removal under test. "
                "Four failures during setup allow ordinary recovery to consume the fixture before its cut. "
                "W446's -S2 native control faulted O3 removal and passed the unchanged oracles; -S0 and -S1 retain "
                "the original setup experiments.")
    return CASE_SKIP.get((store, fault), DEFAULT_SKIP.get(store, (0, "")))


def default_skip(store: str, fault: str | None = None, count: int = 0) -> int:
    return skip_default(store, fault, count)[0]


@dataclass(frozen=True)
class Case:
    store: str
    fault: str
    count: int = 0
    skip: int = 0

    @property
    def offline(self) -> bool:
        return self.fault in OFFLINE_FAULTS

    @property
    def id(self) -> str:
        """The skip is named only when it is not this case's planned one."""
        if self.offline:
            return f"X02-{self.store}-{self.fault}"
        return f"X02-{self.store}-{self.fault}-{self.count}" + ("" if self.skip == default_skip(self.store, self.fault, self.count) else f"-S{self.skip}")

    @property
    def spec(self) -> str:
        return f"{self.store}:{self.fault}:{self.count}" + (f":{self.skip}" if self.skip else "")

    @property
    def native(self) -> tuple[str, str]:
        if self.store == "BAY_RECEIPTS" and self.fault in BAY_DEFINITE:
            return "B", BAY_FAILED_CASE
        if self.store == "BAY_RECEIPTS" and self.fault in BAY_COMMITTED:
            return "B", BAY_COMMITTED_CASE
        if self.store == "SATELLITE_MISSIONS" and self.fault == "MOVE_AFTER":
            return "R", REGISTRY_COMMITTED_CASE
        return (OFFLINE_NATIVE if self.offline else LIVE_NATIVE)[self.store]

    @property
    def not_applicable(self) -> str | None:
        """The reason why this case cannot complete by construction, known before the run (R486, answer 3)."""
        if self.native == ("B", BAY_FAILED_CASE) and self.count + self.skip > 1:
            return ("The native case has one rocket. Its first receipt write that fails before the move cancels the "
                    "deployment, and no second receipt write follows, so a fault on a later write of the bay receipts "
                    "cannot be reached.")
        return None

    def view(self) -> dict:
        """The case as the observation and the manifest name it; the skip is listed only when there is one."""
        if self.offline:
            return dict(id=self.id, store=self.store, fault=self.fault)
        return dict(id=self.id, store=self.store, fault=self.fault, count=self.count) | (dict(skip=self.skip) if self.skip else {})


def plan(stores=None, faults=None, counts=None) -> list[Case]:
    """The cases of one campaign, in a fixed order; a refusal instead of a silent sample."""
    stores, faults = list(stores or STORES), list(faults or [*LIVE_FAULTS, *OFFLINE_FAULTS])
    for store in stores:
        if store not in STORES:
            raise FaultError(f"Unknown store: {store}")
    cases = []
    for store in stores:
        for fault in faults:
            if fault in OFFLINE_FAULTS:
                cases.append(Case(store, fault))
            elif fault in LIVE_FAULTS:
                for count in counts or DEFAULT_COUNTS[fault]:
                    if type(count) is not int or not 1 <= count <= MAX_COUNT:
                        raise FaultError(f"Count outside 1..{MAX_COUNT}: {count}")
                    cases.append(Case(store, fault, count, default_skip(store, fault, count)))
            else:
                raise FaultError(f"Unknown fault: {fault}")
    if len(set(cases)) != len(cases):
        raise FaultError("Duplicate cases")
    if len(cases) > MAX_CASES:
        raise FaultError(f"OVER_BOUND: {len(cases)} cases exceed {MAX_CASES}")
    return cases


def by_id(identifier: str) -> Case:
    match = re.fullmatch(r"X02-([A-Z_]+)-([A-Z_]+)(?:-([1-9][0-9]?))?(?:-S(0|[1-9][0-9]?))?", identifier)
    if match and match.group(1) in STORES:
        store, fault, count, skip = match.groups()
        if fault in OFFLINE_FAULTS and count is None and skip is None:
            return Case(store, fault)
        if fault in LIVE_FAULTS and count is not None and int(count) <= MAX_COUNT:
            case = Case(store, fault, int(count), default_skip(store, fault, int(count)) if skip is None else int(skip))
            # One identifier for each case: a suffix that repeats the store's planned skip is refused.
            if case.skip <= MAX_SKIP and case.id == identifier:
                return case
    raise FaultError(f"Unknown case: {identifier}")


# ---- The injector's lines ----------------------------------------------------------------------------------------

FIELD = re.compile(r"(\w+)=(\S+)")


def parse_markers(lines) -> dict:
    """The injector's report in one start's log. Malformed lines are kept and make the case unusable."""
    report = dict(installed=None, passed=[], injected=[], attempts=[], unseen=[], closed=None, invalid=[], malformed=[])
    for line in lines:
        at = line.find(MARKER)
        if at < 0:
            continue
        text = line[at:].strip()
        if text.startswith(MARKER + "_INVALID"):
            report["invalid"].append(text)
            continue
        words = text.split(" ", 2)
        fields = dict(FIELD.findall(words[2] if len(words) > 2 else ""))
        kind = words[1] if len(words) > 1 else ""
        try:
            # The three-field property logs no skip; its lines and their parsed form are the ones of W379.
            skip = dict(skip=int(fields["skip"])) if "skip" in fields else {}
            if kind == "installed":
                report["installed"] = dict(store=fields["store"], fault=fields["fault"], count=int(fields["count"])) | skip
            elif kind == "passed":
                report["passed"].append(dict(attempt=int(fields["attempt"]), left=int(fields["left"])))
            elif kind == "injected":
                report["injected"].append(dict(operation=fields["operation"], attempt=int(fields["attempt"]),
                                               remaining=int(fields["remaining"])))
            elif kind == "attempt":
                report["attempts"].append(dict(attempt=int(fields["attempt"]), result=fields["result"],
                                               moved=fields["moved"] == "true", faulted=fields["faulted"] == "true",
                                               bytes=int(fields["bytes"]), sha256=fields["sha256"]))
            elif kind == "unseen":
                report["unseen"].append(dict(operation=fields["operation"], attempt=int(fields["attempt"])))
            elif kind == "closed":
                report["closed"] = dict(injected=int(fields["injected"]), attempts=int(fields["attempts"])) | skip
            else:
                report["malformed"].append(text)
        except (KeyError, ValueError):
            report["malformed"].append(text)
    return report


def restarts_passed(restarts) -> bool:
    """Both healthy restarts ran and met the existing runner's own checks."""
    return (len(restarts) == 2 and [r.get("restart") for r in restarts] == [1, 2]
            and all(r.get("status") == "PASS" for r in restarts))


def is_error(line: str) -> bool:
    return "/ERROR]" in line or "/FATAL]" in line


def hold_lines(lines, store: str) -> tuple[list[str], list[str], list[str]]:
    """A faulted start's log as (accepted hold lines, every other ERROR or FATAL line, the log without the accepted).

    A line is accepted only when it is at ERROR, carries a hold marker declared for the faulted store, and follows
    an injection of the injector into that store. Nothing is accepted for another store, at FATAL, or before the
    first injection.
    """
    accepted, other, kept, injected = [], [], [], False
    for line in lines:
        held = False
        if f"{MARKER} injected store={store} " in line and not is_error(line):
            injected = True
        elif is_error(line):
            held = injected and "/FATAL]" not in line and any(marker in line for marker in HOLD_MARKERS.get(store, ()))
            (accepted if held else other).append(line)
        if not held:
            kept.append(line)
    return accepted, other, kept


def production_lines(lines, store: str) -> list[str]:
    """The lines at ERROR that carry a detection marker declared for the store. Nothing else is detection."""
    return [line.strip()[:400] for line in lines
            if "/ERROR]" in line and any(marker in line for marker in DETECTION_MARKERS.get(store, ()))]


def detection(restarts, store: str) -> list[str]:
    """The declared production lines of the store in the restarts of one case.

    A row written by this runner lists them under `production`, read from the whole log. A row without that field
    (an observation kept by W379 or W379F) is read from its bounded `detected` lines.
    """
    return [line for entry in restarts
            for line in (entry["production"] if "production" in entry else production_lines(entry.get("detected", []), store))]


def store_observations(entry: dict) -> int:
    """How often the store file was observed after one start of an offline case.

    A row of this runner counts the callbacks that ran. A row kept by W379 or W379F has no count: its hash, when
    there is one, was written by a callback and is read as one observation, and a row without a hash as none.
    """
    if "store_observations" in entry:
        return entry["store_observations"] if type(entry["store_observations"]) is int else 0
    return 1 if entry.get("store_sha256") is not None else 0


def first_start_held(first: dict | None) -> bool:
    """The faulted start failed the existing runner's check only by declared hold lines.

    All of: the runner reported one finding, on start 0; at least one hold line was accepted and no other ERROR or
    FATAL line is left; and this runner's own evaluation of the start, without the accepted lines, passed.
    """
    if not first:
        return False
    runner, filtered = first.get("runner") or {}, first.get("filtered") or {}
    return (runner.get("status") == "FAILED" and runner.get("findings") == 1 and bool(first.get("accepted"))
            and not first.get("unaccepted") and filtered.get("status") == "PASS")


def classify_live(observation: dict) -> dict:
    """One class for a live case, from the injector's report, the logs and the existing runner's verdicts.

    SEEN_HELD needs all of: every faulted write ended as a failure inside the faulted session; no move followed a
    lost write; a readback fault was followed by a retry of the same bytes; the runner's first start and both
    healthy restarts passed. Anything weaker is never SEEN_HELD.

    Root decision R491, answer 2: an ERROR or FATAL line of the faulted start that this runner did not accept as a
    declared hold line leaves the case NEEDS_REVIEW before either favourable class (SEEN_HELD or CAUGHT_LATER), whether
    or not the existing runner refused that start. The existing runner's own result is kept beside the class.
    """
    case, report = observation["case"], observation["report"]
    reasons, evidence = [], []
    faulted_start = observation.get("first_start") or {}

    def verdict(value):
        result = dict(case=case["id"], classification=value, reasons=reasons, evidence=evidence[:EVIDENCE_LINES])
        if faulted_start:
            # Every accepted hold line is kept, whatever the class and outside the bound on the evidence lines.
            result["accepted_hold_lines"] = list(faulted_start.get("accepted", []))
            # The existing runner's own result for the faulted start, as it reported it; this class never replaces it.
            result["runner_first_start"] = dict(faulted_start.get("runner") or {})
            result["unaccepted_first_start_lines"] = unaccepted
        return result

    if faulted_start:
        unaccepted = max(int(faulted_start.get("unaccepted_count") or 0), len(faulted_start.get("unaccepted") or []))
    else:
        # An observation without this runner's reading of the faulted start (W379 kept such ones) accepted nothing, so
        # every ERROR or FATAL line of that start counts.
        unaccepted = len(observation.get("session_errors") or [])
    installed = report["installed"]
    skip = case.get("skip", 0)
    if report["invalid"] or report["malformed"]:
        reasons.append("The injector refused its property or wrote a line this runner cannot read.")
        evidence.extend(report["invalid"] + report["malformed"])
        return verdict(NOT_RUN)
    if installed != dict(store=case["store"], fault=case["fault"], count=case["count"]) | (dict(skip=skip) if skip else {}):
        reasons.append("The first start did not log the installation of this fault.")
        return verdict(NOT_RUN)
    injected = [entry for entry in report["injected"] if entry["operation"] not in ("read", "delete")]
    passed = [entry["attempt"] for entry in report.get("passed", [])]
    if passed != list(range(1, skip + 1))[:len(passed)] or (injected and len(passed) != skip):
        reasons.append(f"The injector passed writes {passed}, against a skip of {skip}.")
        return verdict(NOT_RUN)
    if not injected:
        reasons.append("No write of the store happened under the fault, so nothing was injected."
                       + (f" {len(passed)} of {skip} skipped writes happened." if skip else ""))
        return verdict(NOT_RUN)
    if any(entry["attempt"] <= skip for entry in injected):
        reasons.append("A skipped write was faulted.")
        return verdict(NOT_RUN)
    evidence.extend(f"passed attempt={attempt} skip={skip}" for attempt in passed)
    evidence.extend(f"injected operation={e['operation']} attempt={e['attempt']} remaining={e['remaining']}"
                    for e in report["injected"])
    restarts = observation["restarts"]
    if len(restarts) != 2:
        reasons.append("The native case did not reach its two healthy restarts: "
                       + str(observation.get("error") or "no restart result"))
        evidence.extend(observation.get("session_errors", []))
        return verdict(INCOMPLETE)
    ends = {entry["attempt"]: entry for entry in report["attempts"]}
    faulted = sorted({entry["attempt"] for entry in injected})
    seen = True
    for attempt in faulted:
        end = ends.get(attempt)
        if end is None or end["result"] not in ("failed", "open-failed"):
            seen = False
            reasons.append(f"Faulted write {attempt} did not end as a failure in the session.")
        elif end["moved"] != (case["fault"] in MOVED_FAULTS):
            seen = False
            reasons.append(f"Faulted write {attempt} ended with moved={end['moved']}, against the fault's definition.")
        else:
            evidence.append(f"attempt={attempt} result={end['result']} moved={end['moved']} sha256={end['sha256']}")
    if report["unseen"]:
        seen = False
        reasons.append("A write that lost its bytes passed the staging readback and reached the move.")
        evidence.extend(f"unseen operation={e['operation']} attempt={e['attempt']}" for e in report["unseen"])
    if case["fault"] in READ_FAULTS:
        reads = [entry for entry in report["injected"] if entry["operation"] == "read"]
        if len(reads) != len(faulted):
            seen = False
            reasons.append(f"{len(faulted)} moves were faulted but {len(reads)} readbacks failed.")
        for attempt in faulted:
            first, retry = ends.get(attempt), ends.get(attempt + 1)
            if first is None or retry is None or retry["sha256"] != first["sha256"] or retry["bytes"] != first["bytes"]:
                seen = False
                reasons.append(f"The write after faulted write {attempt} is missing or has different bytes.")
            else:
                evidence.append(f"attempt={attempt + 1} retried sha256={retry['sha256']}")
    if len(injected) != case["count"]:
        reasons.append(f"Only {len(injected)} of {case['count']} faults were used before the first start ended.")
        return verdict(INCOMPLETE)
    healthy = restarts_passed(restarts) and not observation.get("error")
    for entry in restarts:
        evidence.append(f"restart={entry.get('restart')} status={entry.get('status')}")
    later = [line for entry in restarts for line in entry.get("detected", [])]
    refused = detection(restarts, case["store"])
    if unaccepted and ((seen and restarts_passed(restarts)) or (not seen and not healthy and refused)):
        # Every path below this point that ends in SEEN_HELD or CAUGHT_LATER passes here first.
        runner, filtered = faulted_start.get("runner") or {}, faulted_start.get("filtered") or {}
        reasons.append(f"{unaccepted} ERROR or FATAL line(s) of the faulted start are not declared hold lines of the "
                       f"faulted store, so the case has no favourable class. The existing runner's own result for that "
                       f"start is kept apart: status={runner.get('status')}, error={runner.get('error')}; the evaluation "
                       f"without the accepted lines: {filtered.get('status')}"
                       + (f" ({filtered.get('error') or filtered.get('reason')})" if filtered.get("status") != "PASS" else "")
                       + ". The existing runner reported for the case: "
                       + (str(observation["error"]).split("\n", 1)[0][:300] if observation.get("error") else "no error") + ".")
        evidence.extend(faulted_start.get("unaccepted") or [])
        evidence.extend(line for line in observation.get("session_errors", []) if line not in evidence)
        return verdict(NEEDS_REVIEW)
    if seen and healthy:
        reasons.append("Every faulted write failed inside the session, and both healthy restarts met the existing oracle.")
        return verdict(SEEN_HELD)
    if seen and restarts_passed(restarts):
        # Both healthy restarts met the existing oracle, so the durable outcome is the expected one, but a check of
        # the faulted start itself failed (W379 smoke 2: the journal's hold is logged at ERROR, and the runner
        # refuses any ERROR line). That check is not edited and its result stays recorded.
        if first_start_held(faulted_start):
            # Root decision R486: the only ERROR lines of the faulted start are hold lines declared for this store
            # before the run, logged after the injection; without them the start met the runner's checks.
            reasons.append("Every faulted write failed inside the session, and both healthy restarts met the existing "
                           "oracle. The existing runner's check of the faulted start failed ("
                           + str(faulted_start["runner"].get("error")) + ") only on " + str(len(faulted_start["accepted"]))
                           + " declared hold line(s) of the faulted store; this runner's evaluation of that start "
                           "without them passed: " + ", ".join(faulted_start["filtered"].get("checks", [])) + ".")
            return verdict(SEEN_HELD)
        # Anything else is neither relaxed nor called a residual: a reader decides.
        reasons.append("The session saw the fault and both healthy restarts met the existing oracle, but an existing "
                       "check of the faulted start failed: " + str(observation.get("error")))
        if faulted_start:
            reasons.append(f"{faulted_start.get('unaccepted_count', len(faulted_start.get('unaccepted', [])))} ERROR or FATAL line(s) of the faulted start are not "
                           "declared hold lines; the evaluation without the accepted lines: "
                           + str((faulted_start.get("filtered") or {}).get("status"))
                           + (" (" + str(faulted_start["filtered"].get("error") or faulted_start["filtered"].get("reason")) + ")"
                              if (faulted_start.get("filtered") or {}).get("status") != "PASS" else "") + ".")
        evidence.extend(observation.get("session_errors", []))
        return verdict(NEEDS_REVIEW)
    if not healthy:
        reasons.append("An existing oracle failed after the fault: " + str(observation.get("error") or "restart status"))
        evidence.extend(later)
    if not seen and not healthy and refused:
        # The session missed the fault; a later start refused the store with its declared production line, and the
        # existing oracle did not accept the result.
        reasons.append(f"A later start refused the store with {len(refused)} declared production line(s), and no "
                       "existing oracle accepted the store.")
        return verdict(CAUGHT_LATER)
    if seen:
        reasons.append("The session saw the fault, but held-as-designed is not shown; recorded as a residual.")
    elif later:
        # R491, answer 1: a failed assertion of a release test, a crash wrapper or an unrelated error is not detection.
        reasons.append(f"The later starts logged {len(later)} ERROR or FATAL line(s), none of which is a production line "
                       "declared for the store; recorded as a residual.")
    else:
        reasons.append("Nothing reported the fault; recorded as a residual.")
    return verdict(NOT_CAUGHT)


def classify_offline(observation: dict) -> dict:
    """One class for an offline case: a damaged or stale store file meets two starts.

    CAUGHT_LATER needs a start that refuses the store with a production line declared for it before the run
    (DETECTION_MARKERS), and either the file is still byte-identical after both starts (the server held), or the
    file changed and the existing oracle passed on both starts (a repair the existing oracle accepts). Any other
    ERROR or FATAL line is kept as evidence and is never detection: a case with a failed oracle and no declared
    production line is NOT_CAUGHT, a residual. So is a file that a start accepts or replaces without the above. An
    offline fault is never seen by a running session, so SEEN_HELD does not occur.

    Root decision R491, answer 3: the store file must have been observed exactly once after each of the two starts.
    A start without an observation is NOT_RUN, because the instrumentation missed it and nothing is known about the
    file; more than one observation of a start is INCOMPLETE. A file that an observation found absent is an
    observation, and it differs from the damaged file.
    """
    case = observation["case"]
    reasons, evidence = [], []

    def verdict(value):
        return dict(case=case["id"], classification=value, reasons=reasons, evidence=evidence[:EVIDENCE_LINES])

    refused = observation.get("injection_refused") or observation.get("not_applicable")
    if refused:
        # R491, answer 4: NOT_APPLICABLE comes only from the frozen plan (declared_row). This case reached its run.
        reasons.append("The damage could not be made while the case ran, and the frozen plan did not declare the case: "
                       + str(refused))
        return verdict(INCOMPLETE)
    restarts = observation["restarts"]
    if len(restarts) != 2 or [r.get("restart") for r in restarts] != [1, 2]:
        reasons.append("The damaged copy was not started twice: " + str(observation.get("error") or "no result"))
        return verdict(INCOMPLETE)
    counts = [store_observations(entry) for entry in restarts]
    if 0 in counts:
        reasons.append("The store file was not observed after start(s) "
                       + ", ".join(str(entry.get("restart")) for entry, count in zip(restarts, counts) if count == 0)
                       + ": the callback that hashes it when the server process has ended did not run, so the "
                         "instrumentation missed the start and nothing is known about the file.")
        return verdict(NOT_RUN)
    if counts != [1, 1]:
        reasons.append(f"The store file was observed {counts} times after the two starts, against exactly once each.")
        return verdict(INCOMPLETE)
    damaged = observation["damaged_sha256"]
    after = [entry.get("store_sha256") for entry in restarts]
    replaced = any(value != damaged for value in after)
    logged = [line for entry in restarts for line in entry.get("detected", [])]
    detected = detection(restarts, case["store"])
    passed = restarts_passed(restarts) and not observation.get("error")
    evidence.append(f"damaged sha256={damaged}")
    for entry in restarts:
        evidence.append(f"restart={entry.get('restart')} status={entry.get('status')} ready={entry.get('ready')} "
                        f"store_sha256={entry.get('store_sha256')}")
    # The declared production lines come first, so that the bound on the evidence never drops them.
    evidence.extend(f"production line: {line}" for line in detected)
    evidence.extend(line for line in logged if line not in detected)
    if not detected and logged:
        outcome = ("the existing oracle passed" if passed else "the existing oracle failed: "
                   + str(observation.get("error") or "restart status").split("\n", 1)[0][:300])
        reasons.append(f"The starts logged {len(logged)} ERROR or FATAL line(s), none of which is a production line "
                       f"declared for the store, and {outcome}. An assertion of a release test, a crash wrapper or an "
                       "unrelated error is not detection; the file was "
                       + ("rewritten" if replaced else "left byte-identical") + ". Recorded as a residual.")
        return verdict(NOT_CAUGHT)
    if not detected:
        reasons.append("No start reported the file; it was accepted" + (" and rewritten." if replaced else "."))
        return verdict(NOT_CAUGHT)
    if not replaced:
        reasons.append("A start refused the file with a declared production line, and it is byte-identical after both starts.")
        return verdict(CAUGHT_LATER)
    if passed:
        reasons.append("A start reported the file with a declared production line and replaced it; the existing oracle "
                       "passed on both starts.")
        return verdict(CAUGHT_LATER)
    reasons.append("A start reported the file with a declared production line but replaced it, and the existing oracle "
                   "does not accept the result.")
    return verdict(NOT_CAUGHT)


# ---- The offline damage ------------------------------------------------------------------------------------------

def damage(kind: str, current: bytes | None, earlier: bytes | None, has_earlier: bool) -> tuple[bytes | None, str | None]:
    """The store file of one offline case, or (None, reason) when the recorded case cannot carry it.

    `current` is the file in the kill image; `earlier` is the content before the last completed recorded write of
    the store (None when the file did not exist then) and `has_earlier` says whether such a write was recorded.
    """
    if kind == "STALE":
        if not has_earlier:
            return None, "The recording has no completed earlier write of this store."
        if earlier is None:
            return None, "The earlier generation is an absent file; an absent root is a different case."
        if earlier == current:
            return None, "The earlier generation equals the current file."
        return earlier, None
    if current is None:
        return None, "The store file is absent in the kill image."
    if len(current) < 2:
        return None, "The store file is too short to damage."
    if kind == "ZERO_LENGTH":
        return b"", None
    if kind == "ZEROED_TAIL":
        tail = min(4096, len(current) // 2)
        return current[:-tail] + bytes(tail), None
    if kind == "BIT_FLIP":
        middle = len(current) // 2
        return current[:middle] + bytes([current[middle] ^ 0x01]) + current[middle + 1:], None
    if kind == "TRUNCATED":
        return current[:len(current) // 2], None
    raise FaultError(f"Unknown offline fault: {kind}")


def earlier_generation(writes, store: str) -> tuple[bytes | None, bool]:
    """The content before the last completed recorded write of the store."""
    completed = [write for write in writes if write.store == store and write.complete]
    return (completed[-1].old, True) if completed else (None, False)


# ---- Hooks into the existing runners -----------------------------------------------------------------------------

class BayFailedWrite:
    """Runner B with one more case, through the extension point that W346 gave `run` (`extra_cases`).

    The case is B12_FAILED without its `staging` flag: the same flight, the same settled outcome on the first start
    and on both restarts, and the same checks of `run_case`. No case or expectation of runner B changes.
    """
    CASES = {BAY_FAILED_CASE: {key: value for key, value in bay.CASES["B12_FAILED"].items() if key != "staging"}}

    @staticmethod
    def run(args) -> dict:
        def case(runner, name):
            if name in bay.CASES:
                raise FaultError("Runner B already has a case named " + name)
            bay.CASES[name] = dict(BayFailedWrite.CASES[name])
            try:
                return bay.run_case(runner, name)
            finally:
                del bay.CASES[name]

        return bay.run(args, extra_cases={BAY_FAILED_CASE: case})


def runner_of(case: Case):
    alias, native = case.native
    return BayFailedWrite if native == BAY_FAILED_CASE else x01.RUNNERS[alias]


def sha(path: Path) -> str | None:
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


@contextmanager
def hooks(observer=None, keeper=None):
    """While active: the first start of an args object that names `storage_fault` gets the property, and `observer`
    is called after each server process has ended. The existing runners are not edited; the two names they look up
    at call time (`v180_crash_replay.trace_command`, `run_dedicated_server_smoke.CapturedProcess`) are replaced and
    put back.

    `keeper` (W450, only with --keep-candidates) is given each line of the first start's log as it arrives, on a
    thread of its own that ends with the process's output."""
    original_command, original_process = replay.trace_command, smoke.CapturedProcess

    def command_hook(args, command, restart):
        fault = getattr(args, "storage_fault", None)
        if fault is not None and restart == 0:
            command.insert(1, PROPERTY + fault)
        original_command(args, command, restart)

    class Observed(original_process):
        def __init__(self, command, cwd, log_path):
            self._arce_cwd, self._arce_log, self._arce_reported = Path(cwd), Path(log_path), False
            super().__init__(command, cwd, log_path)
            self._arce_watcher = None
            if keeper is not None and start_number(self._arce_log) == 0:
                self._arce_watcher = threading.Thread(target=self._arce_watch, daemon=True)
                self._arce_watcher.start()

        def _arce_watch(self):
            seen = 0
            while True:
                with self.condition:
                    while seen >= len(self.lines):
                        if not self.reader.is_alive():
                            return
                        self.condition.wait(0.2)
                    fresh = self.lines[seen:]
                    seen = len(self.lines)
                for line in fresh:
                    keeper.line(self._arce_cwd, line)

        def _arce_ended(self):
            if self._arce_watcher is not None and self.process.poll() is not None:
                # The output has ended with the process; the keeper has then seen every line before its report is read.
                self._arce_watcher.join(30)
            if observer is not None and not self._arce_reported and self.process.poll() is not None:
                self._arce_reported = True
                observer(self._arce_cwd, self._arce_log)

        def finish(self, timeout: float = 90.0) -> int:
            try:
                return super().finish(timeout)
            finally:
                self._arce_ended()

        def abort(self) -> None:
            try:
                super().abort()
            finally:
                self._arce_ended()

    replay.trace_command, smoke.CapturedProcess = command_hook, Observed
    try:
        yield
    finally:
        replay.trace_command, smoke.CapturedProcess = original_command, original_process


def start_number(log: Path) -> int | None:
    match = re.fullmatch(r".+-(\d+)", log.stem)
    return int(match.group(1)) if match else None


def detected_lines(lines) -> list[str]:
    return [line.strip()[:400] for line in lines if "/ERROR]" in line or "/FATAL]" in line][:EVIDENCE_LINES]


def read_logs(output: Path) -> dict[int, list[str]]:
    logs = {}
    for path in sorted(output.glob("*.log")):
        number = start_number(path)
        if number is not None:
            logs.setdefault(number, []).extend(path.read_text(encoding="utf-8", errors="replace").splitlines())
    return logs


def restart_rows(result: dict, logs: dict[int, list[str]], hashes: dict[int, list[str | None]] | None = None,
                 store: str | None = None) -> list[dict]:
    """The existing runner's verdict on each restart it reached, with what this harness saw beside it.

    `hashes` lists, for each start, the store file's hash at every observation after its process had ended (None for
    an absent file). The row keeps the number of observations apart from the hash: no observation and an observed
    absent file both have no hash.
    """
    entries = (result.get("cases") or [{}])[0]
    listed = entries.get("restarts", entries.get("starts", []))
    rows = []
    for number in (1, 2):
        observed = next((entry for entry in listed if entry.get("restart") == number), None)
        if observed is None and number not in logs:
            continue
        lines = logs.get(number, [])
        row = dict(restart=number,
                   status="PASS" if observed is not None and observed.get("status") != "FAILED" else "FAIL",
                   ready=any(smoke.READY_MARKER.search(line) for line in lines), detected=detected_lines(lines))
        if store is not None:
            row["production"] = production_lines(lines, store)[:EVIDENCE_LINES]
        if observed is not None and observed.get("error"):
            row["error"] = str(observed["error"])[:400]
        if hashes is not None:
            seen = hashes.get(number, [])
            row["store_observations"] = len(seen)
            row["store_sha256"] = seen[-1] if seen else None
        rows.append(row)
    return rows


# ---- The faulted start, evaluated by this runner (Root decision R486, answer 1) -----------------------------------

def settlement_first_start(native: str, lines: list[str], world: Path, entry: dict) -> list[str]:
    """Runner S's checks of a first start, by its own unedited functions and in its order, on the given lines.

    These are the checks that run_v180_cargo_s4_s5_cuts.run makes after a start. It stops at the first failed one,
    so after "Native run logged ERROR or FATAL" it has not inspected the world of that start and has not compared
    the restarts with it. Both are done here, on a copy of the world taken when the start's process had ended.
    """
    runner = x01.RUNNERS["S"]
    starts = {start.get("restart"): start for start in entry.get("starts", [])}
    first = starts.get(0, {})
    runner.require(first.get("forced_stop") is True, "Start execution: the faulted start did not end at its cut")
    runner.require("raw" in first and "error" not in first["raw"], "Raw decoding: " + str(first.get("raw", {}).get("error")))
    runner.observations(lines, native, 0)
    disk = runner.inspect(world)
    runner.check(native, disk, False)
    cut = json.loads(json.dumps(dict(rockets=disk["rockets"], device=disk["device"])))
    for number in (1, 2):
        later = starts.get(number, {}).get("disk")
        runner.require(later is not None and runner.same_identity(cut, later),
                       "Restart changed the logical rocket, owner or device")
    return ["start execution", "raw decoding", "log observations", "disk oracle of the cut", "identity against both restarts"]


# Runner alias -> its first-start checks. A runner without an entry cannot be evaluated here, and a faulted start
# that it refused stays NEEDS_REVIEW. Runner R refuses no ERROR line of a first start. Runner B refuses the lines
# outside its own list (bay_integrated_evidence.unexpected_errors) and then ends the case before any restart, so
# such a case is INCOMPLETE; the bay receipts have no declared hold marker.
FIRST_START_CHECKS = {"S": settlement_first_start}


def first_start_lines(output: Path) -> list[str]:
    """The first start's log as the runner's process captured it: split at line feeds only, with the line ends."""
    lines = []
    for path in sorted(output.glob("*.log")):
        if start_number(path) == 0:
            with path.open(encoding="utf-8", errors="replace", newline="\n") as stream:
                lines.extend(stream)
    return lines


def evaluate_first_start(case: Case, result: dict, lines: list[str], world: Path | None) -> dict:
    """Both results for the faulted start: the existing runner's own, as it reported it, and this runner's evaluation
    of the same start without the accepted hold lines. Neither replaces the other."""
    alias, native = case.native
    entry = (result.get("cases") or [{}])[0]
    accepted, other, kept = hold_lines(lines, case.store)
    reported = [start for start in entry.get("starts", []) if start.get("restart") == 0]
    if reported:  # runners F, I and S report each start
        runner = dict(status=reported[0].get("status"), error=reported[0].get("error"), findings=len(entry.get("findings", [])))
    else:  # runners B and R report a first start only when it met their checks
        runner = dict(status="PASS" if "first_start" in entry else None, error=None, findings=None)
    check = FIRST_START_CHECKS.get(alias)
    if not accepted:
        filtered = dict(status="NOT_EVALUATED", reason="No hold line was accepted, so nothing was filtered")
    elif check is None:
        filtered = dict(status="NOT_EVALUATED", reason=f"Runner {alias} has no first-start evaluation in this harness")
    elif world is None or not world.is_dir():
        filtered = dict(status="NOT_EVALUATED", reason="The world of the faulted start was not kept")
    else:
        try:
            filtered = dict(status="PASS", checks=check(native, kept, world, entry))
        except (OSError, ValueError, KeyError, TypeError, smoke.SmokeError) as failure:
            filtered = dict(status="FAILED", error=f"{type(failure).__name__}: {failure}"[:400])
    return dict(declared=list(HOLD_MARKERS.get(case.store, ())), runner=runner,
                accepted=[line.strip()[:400] for line in accepted],
                unaccepted=[line.strip()[:400] for line in other][:EVIDENCE_LINES], unaccepted_count=len(other),
                filtered=filtered)


def write_case(evidence: Path, case: Case, observation: dict, classification: dict, commands: list) -> dict:
    x01.write_json(evidence / "observation.json", observation)
    x01.write_json(evidence / "classification.json", classification)
    x01.write_json(evidence / "commands.json", commands)
    row = dict(case=case.id, store=case.store, fault=case.fault, design=(OFFLINE_FAULTS | LIVE_FAULTS)[case.fault],
               native="/".join(case.native), classification=classification["classification"],
               reasons=classification["reasons"]) | (dict(skip=case.skip) if case.skip else {})
    with (evidence.parent.parent / "results.jsonl").open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(row) + "\n")
    return row


def check_size(evidence: Path) -> None:
    files = [path for path in evidence.rglob("*") if path.is_file()]
    if any(path.stat().st_size > 50 * 1024**2 for path in files) or sum(path.stat().st_size for path in files) > LIMIT:
        raise replay.OverBound("OVER_BOUND: the evidence exceeds 50 MiB in one file or 100 MiB in total")


def seal(evidence: Path, manifest: dict) -> None:
    """The manifest, the summary and the checksum list of the package as it is now.

    Root decision R491, answer 5: the three files are part of the package, so they are written before every check of
    its size, and the last check is made on the finished package.
    """
    x01.write_json(evidence / "MANIFEST.json", manifest)
    (evidence / "SUMMARY.md").write_text(summary(manifest), encoding="utf-8", newline="\n")
    sums = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(evidence).as_posix()}"
            for path in sorted(evidence.rglob("*")) if path.is_file() and path.name != "SHA256SUMS.txt"]
    (evidence / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="ascii", newline="\n")


# ---- Running ------------------------------------------------------------------------------------------------------

def run_live(args, case: Case, evidence: Path, scratch: Path, owner: str) -> dict:
    alias, native = case.native
    evidence.mkdir(parents=True); scratch.mkdir(parents=True)
    output = scratch / "run"
    arguments = x01.runner_args(args, TEMP / ("w379-" + uuid.uuid4().hex), output, native, owner)
    arguments.storage_fault = case.spec
    first_world = scratch / "first-world"

    def observer(cwd: Path, log: Path):
        # The world of the faulted start, for this runner's own evaluation of it. A failed copy disturbs nothing:
        # the evaluation is then NOT_EVALUATED and the case keeps NEEDS_REVIEW.
        if (case.store in HOLD_MARKERS and alias in FIRST_START_CHECKS and start_number(log) == 0
                and not first_world.exists()):
            try:
                if (cwd / ("world-" + native)).is_dir():
                    replay.materialize(cwd / ("world-" + native), first_world, replay.State("first-start", {}))
            except (OSError, ValueError):
                pass

    keeper = None
    if getattr(args, "keep_candidates", False):
        keeper = candidates.Keeper(case.store, lambda cwd: cwd / ("world-" + native) / "data" / STORES[case.store],
                                   evidence / "candidates")
    with hooks(observer, keeper):
        result, error = x01.run_existing(runner_of(case), arguments, output)
    logs = read_logs(output)
    x01.keep_logs(output, evidence, "live")
    if keeper is not None:
        # Beside the observation, which stays as it is without the option; the class does not read this file.
        x01.write_json(evidence / "candidates.json", keeper.report())
    if (output / "results.json").is_file():
        shutil.copyfile(output / "results.json", evidence / "runner-results.json")
    first = logs.get(0, [])
    observation = dict(case=case.view(), native=dict(runner=alias, case=native), report=parse_markers(first), error=error,
                       session_errors=detected_lines(first), restarts=restart_rows(result, logs, store=case.store),
                       first_start=evaluate_first_start(case, result, first_start_lines(output), first_world))
    return write_case(evidence, case, observation, classify_live(observation), list(arguments.replay_commands))


def record(args, alias: str, native: str, evidence: Path, scratch: Path, owner: str):
    """The existing cut with the storage trace, kept as a kill image (the capture of slice S2)."""
    kill, trace = scratch / "kill", evidence / "trace"
    context = {}

    def capture(world, row):
        replay.materialize(world, kill, replay.State("kill", {}))
        if alias in ("B", "R"):
            context.update({key: copy.deepcopy(row[key]) for key in ("fixture", "first_start") if key in row})
        else:
            context.update(starts=[dict(restart=0, disk=copy.deepcopy(row["starts"][0]["disk"]))], raw0=row["raw0"])

    output = scratch / "record"
    arguments = x01.runner_args(args, TEMP / ("w379-" + uuid.uuid4().hex), output, native, owner)
    arguments.storage_trace, arguments.replay_capture = trace, capture
    result, error = x01.run_existing(x01.RUNNERS[alias], arguments, output)
    markers = x01.keep_logs(output, evidence, "record")
    x01.write_json(evidence / "record-commands.json", list(arguments.replay_commands))
    if error or not context or any("ARCE_RELEASE_STORAGE_TRACE_INVALID" in line for group in markers.values() for line in group):
        raise FaultError(error or "Missing capture or invalid trace marker")
    x01.write_json(evidence / "capture-context.json", context)
    return kill, trace, context


def start_twice(args, alias: str, native: str, kill: Path, state, context: dict, scratch: Path, owner: str,
                evidence: Path, store: str):
    """Both restarts of the existing runner on one damaged copy; the store file is hashed after each process ends.

    Every call of the observer is kept, so that a start whose process did not go through the replaced process class
    (no call) is told apart from a start that left no file (one call, no hash).
    """
    hashes, commands, errors = {}, [], []

    def observer(cwd: Path, log: Path):
        number = start_number(log)
        if number is not None:
            hashes.setdefault(number, []).append(sha(cwd / ("world-" + native) / "data" / STORES[store]))

    module = x01.RUNNERS[alias]
    output, after_first = scratch / "restart", scratch / "after-first"
    arguments = x01.runner_args(args, TEMP / ("w379-" + uuid.uuid4().hex), output, native, owner)
    arguments.replay_world, arguments.replay_state, arguments.replay_context = kill, state, context
    arguments.replay_old_item = arguments.replay_old_registry = False

    def preserve(world):
        if world.is_dir():
            replay.materialize(world, after_first, replay.State("continuation", {}))

    if alias in ("B", "R"):
        arguments.replay_preserve = preserve
    with hooks(observer):
        result, error = x01.run_existing(module, arguments, output)
        commands.extend(arguments.replay_commands)
        logs = read_logs(output)
        x01.keep_logs(output, evidence, "damaged")
        if error:
            errors.append(error)
        # Runners B and R stop at their first failed restart. The stopped image is started a second time, as the
        # X01 replay does, so that a refusal is seen twice.
        if alias in ("B", "R") and error and after_first.is_dir() and 2 not in logs:
            second_output = scratch / "restart-second"
            second = copy.copy(arguments)
            second.session_dir = TEMP / ("w379-" + uuid.uuid4().hex)
            second.evidence_dir, second.replay_world = second_output, after_first
            second.replay_state = replay.State("continuation", {})
            second.replay_restart_numbers, second.replay_commands, second.replay_preserve = (2,), [], None
            later, later_error = x01.run_existing(module, second, second_output)
            commands.extend(second.replay_commands)
            logs.update(read_logs(second_output))
            x01.keep_logs(second_output, evidence, "damaged")
            if later_error:
                errors.append(later_error)
            listed = result.setdefault("cases", [])
            if not listed:
                listed.append({})
            listed[0].setdefault("restarts", []).extend((later.get("cases") or [{}])[0].get("restarts", []))
    return restart_rows(result, logs, hashes, store), "; ".join(errors) or None, commands


def prepared(case: Case, kill: Path, writes) -> tuple[bytes | None, str | None, dict]:
    """The damaged store file of one offline case on a kept kill image, or the reason why the image cannot carry it,
    with the facts of the recording that decide it."""
    current = replay.read_optional(kill, "data/" + STORES[case.store])
    earlier, has_earlier = earlier_generation(writes, case.store)
    content, refusal = damage(case.fault, current, earlier, has_earlier)
    facts = dict(original_sha256=replay.sha(current),
                 completed_writes=sum(1 for write in writes if write.store == case.store and write.complete),
                 earlier_generation=("no completed write recorded" if not has_earlier else
                                     "absent file" if earlier is None else replay.sha(earlier)))
    return content, refusal, facts


def plan_recording(args, cases: list[Case], evidence_root: Path, scratch: Path, owner: str) -> dict:
    """The planning phase of one offline group (Root decision R491, answer 4).

    It records the native cut once and keeps the kill image for the group's cases. For each case it then decides, on
    that image and its trace, whether the damage can be made. A case that cannot carry its damage is declared here,
    with the facts that decide it, and the campaign writes the declaration into the manifest before any case runs.
    No case is started in this phase.
    """
    alias, native = cases[0].native
    scratch.mkdir(parents=True)
    group = evidence_root / ("record-" + alias + "-" + native)
    group.mkdir(parents=True)
    recording = dict(name=group.name, runner=alias, case=native, cases=[case.id for case in cases], declared=[], error=None)
    try:
        kill, trace, context = record(args, alias, native, group, scratch, owner)
        writes = replay.read_trace(trace, kill)
    except (FaultError, replay.ReplayError) as failure:
        recording["error"] = "Recording failed: " + str(failure)
        return recording
    recording.update(kill=kill, context=context, writes=writes, trace_ops_sha256=sha(trace / "ops.jsonl"))
    for case in cases:
        _, refusal, facts = prepared(case, kill, writes)
        if refusal:
            recording["declared"].append(dict(case=case.id, reason=refusal, planning=dict(
                recording=group.name, trace_ops_sha256=recording["trace_ops_sha256"], **facts)))
    return recording


def run_offline(args, cases: list[Case], evidence_root: Path, recording: dict, scratch: Path, owner: str) -> list[dict]:
    """Each offline fault of the group on its own copy of the kill image that the planning phase kept."""
    alias, native = cases[0].native
    scratch.mkdir(parents=True)
    rows = []
    if recording.get("error"):
        for case in cases:
            target = evidence_root / case.id
            target.mkdir(parents=True)
            observation = dict(case=dict(id=case.id, store=case.store, fault=case.fault), restarts=[],
                               native=dict(runner=alias, case=native), error=recording["error"])
            rows.append(write_case(target, case, observation, classify_offline(observation), []))
        return rows
    kill, context = recording["kill"], recording["context"]
    for case in cases:
        x01.preflight()
        target = evidence_root / case.id
        target.mkdir(parents=True)
        name = "data/" + STORES[case.store]
        content, refusal, facts = prepared(case, kill, recording["writes"])
        observation = dict(case=dict(id=case.id, store=case.store, fault=case.fault),
                           native=dict(runner=alias, case=native), recording=recording["name"],
                           original_sha256=facts["original_sha256"])
        commands = []
        if refusal:
            # The planning phase could make this damage, or the case would have been declared. Nothing is started.
            observation.update(injection_refused=refusal, restarts=[])
        else:
            observation["damaged_sha256"] = replay.sha(content)
            state = replay.State(case.id, {name: content, name + replay.PENDING: None})
            restarts, error, commands = start_twice(args, alias, native, kill, state, context, scratch / case.id, owner,
                                                    target, case.store)
            observation.update(restarts=restarts, error=error)
            if (scratch / case.id).exists():
                shutil.rmtree(scratch / case.id)
        rows.append(write_case(target, case, observation, classify_offline(observation), commands))
    return rows


@contextmanager
def machine_lock(path=None, timeout=3 * 3600):
    """Atomic directory creation; only this invocation's finally releases it, and a stale lock is never broken."""
    path = path or LOCK
    token = f"W379-X02 pid={os.getpid()} {uuid.uuid4()}"
    deadline = time.monotonic() + timeout
    while True:
        try:
            path.mkdir()
            break
        except FileExistsError:
            old = time.time() - path.stat().st_mtime > 3 * 3600
            if (old and not lock_owner_running(path)) or time.monotonic() >= deadline:
                raise FaultError("Heavy-JVM lock stale or wait exhausted; not breaking it")
            time.sleep(60)
    owner = path / "owner.txt"
    try:
        owner.write_text(token, encoding="utf-8")
        yield token
    finally:
        if owner.is_file() and owner.read_text(encoding="utf-8-sig").strip() == token:
            owner.unlink(); path.rmdir()


def lock_owner_running(path: Path) -> bool:
    """R607: age alone does not make a live campaign's lock stale. Never remove a foreign lock."""
    try:
        owner = (path / "owner.txt").read_text(encoding="utf-8-sig")
    except FileNotFoundError:
        return False
    match = re.search(r"\bpid=([1-9][0-9]*)\b", owner)
    if not match:
        return False
    try:
        read_process(int(match[1]))
    except (OSError, ValueError):
        # An inaccessible or vanished process is not evidence that it is safe to
        # take the lock. Stop with the lock intact for Root to inspect.
        raise FaultError("Cannot verify the old heavy-JVM lock owner's process; not breaking it")
    return True


def groups(cases: list[Case]) -> list[list[Case]]:
    """One lock hold each: a live case alone, and the offline cases that share one recording together."""
    result, offline = [], {}
    for case in cases:
        if case.offline:
            if case.native not in offline:
                offline[case.native] = []
                result.append(offline[case.native])
            offline[case.native].append(case)
        else:
            result.append([case])
    return result


def summary(manifest: dict) -> str:
    rows = manifest["results"]
    lines = ["# X02 storage faults", "", "Status: " + manifest["status"], "",
             "| Class | Cases |", "|---|---|"]
    for name in (*CLASSES, NEEDS_REVIEW, INCOMPLETE, NOT_RUN, NOT_APPLICABLE):
        lines.append(f"| {name} | {sum(1 for row in rows if row['classification'] == name)} |")
    lines += ["", "| Case | Design | Native case | Class | Reason |", "|---|---|---|---|---|"]
    for row in rows:
        lines.append(f"| {row['case']} | {row['design']} | {row['native']} | {row['classification']} | "
                     f"{' '.join(row['reasons']).replace('|', '/')} |")
    residual = [row for row in rows if row["classification"] == NOT_CAUGHT]
    lines += ["", "## Residuals", ""]
    lines += [f"- {row['case']} ({row['store']}): {' '.join(row['reasons'])}" for row in residual] or ["None recorded."]
    lines += ["", "A class is read from the logs and the existing oracles; no oracle was changed. SEEN_HELD is not a",
              "proof of power-loss safety."]
    if manifest.get("detection_markers"):
        lines += ["", "## Declared detection markers", "",
                  "Declared before any case ran. CAUGHT_LATER needs one of these lines, at ERROR, in a restart of a case",
                  "that faulted the store. No other ERROR or FATAL line counts as detection.", ""]
        lines += [f"- {store}: {', '.join(markers)}" for store, markers in manifest["detection_markers"].items()]
    if manifest.get("hold_markers"):
        lines += ["", "## Declared hold markers", "",
                  "Declared before any case ran. A line is accepted only at ERROR, in the faulted start of its store and",
                  "after the injection; the existing runner's own result for that start is kept beside this runner's.", ""]
        lines += [f"- {store}: {', '.join(markers)}" for store, markers in manifest["hold_markers"].items()]
    if manifest.get("skips"):
        lines += ["", "## Skipped writes", ""]
        lines += [f"- {entry['store']}: the first {entry['skip']} write(s) of the store pass before the faulted ones. "
                  f"{entry['reason']}" for entry in manifest["skips"]]
    declared = {entry["case"] for entry in manifest.get("not_applicable", [])}
    lines += ["", "## Uncovered", "", *["- " + item for item in manifest["uncovered"]]]
    # A case without an outcome by construction is scope that this campaign did not cover.
    lines += [f"- {row['case']}: NOT_APPLICABLE" + (", declared before the run. " if row["case"] in declared else ". ")
              + " ".join(row["reasons"]) for row in rows if row["classification"] == NOT_APPLICABLE]
    if manifest.get("planning"):
        lines += ["", "## Planning phase", "",
                  "Before any case ran, the native cut of each offline group was recorded, and a case whose damage the",
                  "kept image cannot carry was declared NOT_APPLICABLE with the facts of that recording.", ""]
        lines += [f"- {entry['name']}: " + (entry["error"] or (f"trace ops.jsonl sha256={entry.get('trace_ops_sha256')}; declared: "
                                                               + (", ".join(entry["declared"]) or "none")))
                  for entry in manifest["planning"]]
    return "\n".join(lines) + "\n"


def declarations(cases: list[Case]) -> dict:
    """What the manifest declares before any case runs: the hold markers, the skips and the cases without an outcome."""
    live = [case for case in cases if not case.offline and case.skip]

    def reason(case):
        planned, explanation = skip_default(case.store, case.fault, case.count)
        return explanation if case.skip == planned else "Named in the case identifier by the caller."

    skips = [dict(store=store, skip=skip, cases=[case.id for case in live if (case.store, case.skip) == (store, skip)],
                  reason=" ".join(dict.fromkeys(reason(case) for case in live if (case.store, case.skip) == (store, skip))))
             for store, skip in sorted({(case.store, case.skip) for case in live})]
    return dict(detection_markers={store: list(markers) for store, markers in DETECTION_MARKERS.items()},
                detection_rule="A later start has detected a fault only by a line at ERROR that carries a marker declared "
                               "for the faulted store. A failed assertion of a release test, a crash report, an "
                               "unrelated error or any undeclared line is evidence and never detection; a case with a "
                               "failed oracle and no declared line is NOT_CAUGHT.",
                hold_markers={store: list(markers) for store, markers in HOLD_MARKERS.items()},
                hold_rule="A line at ERROR that carries a marker declared for the faulted store is accepted only in the "
                          "faulted start and only after an ARCE_RELEASE_STORAGE_FAULT injected line for that store. Any "
                          "other ERROR or FATAL line, and every such line of a restart, keeps its effect.",
                skips=skips,
                not_applicable=[dict(case=case.id, reason=case.not_applicable) for case in cases if case.not_applicable])


def declared_row(case: Case, evidence: Path, declaration: dict | None = None) -> dict:
    """A case that the frozen plan declares NOT_APPLICABLE: recorded with its reason, and no server is started.

    `declaration` is the case's entry in the manifest; without one, the declaration is the one of the case itself.
    """
    evidence.mkdir(parents=True)
    alias, native = case.native
    declaration = declaration or dict(case=case.id, reason=case.not_applicable)
    observation = dict(case=case.view(), native=dict(runner=alias, case=native), declared_before_run=True,
                       not_applicable=declaration["reason"], restarts=[])
    if "planning" in declaration:
        observation["planning"] = declaration["planning"]
    classification = dict(case=case.id, classification=NOT_APPLICABLE, reasons=[declaration["reason"]], evidence=[])
    return write_case(evidence, case, observation, classification, [])


def status_of(rows: list[dict], declared=()) -> str:
    """COMPLETE when every case has a class or was declared not applicable; a residual is a finding, not a harness
    failure. `declared` names the cases of the frozen plan's NOT_APPLICABLE declarations: a NOT_APPLICABLE row of any
    other case has no declaration behind it and makes the campaign INCOMPLETE (Root decision R491, answer 4)."""
    if any(row["classification"] in (INCOMPLETE, NOT_RUN, NEEDS_REVIEW) for row in rows):
        return "INCOMPLETE"
    if any(row["classification"] == NOT_APPLICABLE and row.get("case") not in declared for row in rows):
        return "INCOMPLETE"
    return "COMPLETE_WITH_RESIDUALS" if any(row["classification"] == NOT_CAUGHT for row in rows) else "COMPLETE"


def run(args) -> dict:
    x01.preflight()
    if not re.fullmatch(r"[0-9a-f]{40}", args.candidate_commit):
        raise FaultError("Candidate commit must be a full SHA-1")
    cases = [by_id(identifier) for identifier in args.case] if args.case else plan(args.store, args.fault, args.count)
    if len(set(cases)) != len(cases) or len(cases) > MAX_CASES:
        raise FaultError(f"Cases must be distinct and at most {MAX_CASES}")
    bay.assert_pin(args.source_commit)
    evidence_tools.candidate_pin(args.jar, args.jar_sha256, args.candidate_commit)
    evidence = args.evidence_dir.resolve()
    if (evidence.exists() or evidence.drive.upper() != "D:" or EVIDENCE_ROOT.resolve() not in evidence.parents
            or TEMP.resolve() in [evidence, *evidence.parents]):
        raise FaultError("Evidence must be a new directory under D:/GitHub/ARCE-Task-Evidence, outside Temp")
    if (not TEMP.is_dir() or TEMP.resolve().drive.upper() != "D:" or TEMP.is_symlink()
            or getattr(TEMP, "is_junction", lambda: False)()):
        raise FaultError("Unsafe or missing D: temporary root")
    libraries = args.source_server / "libraries"
    if not libraries.is_dir() or any(path.is_symlink() or getattr(path, "is_junction", lambda: False)()
                                     for path in [libraries, *libraries.rglob("*")]):
        raise FaultError("Unsafe or missing Forge libraries")
    scratch = TEMP / ("w379-campaign-" + uuid.uuid4().hex)
    evidence.mkdir(parents=True); scratch.mkdir()
    (evidence / "cases").mkdir()
    return campaign(args, cases, evidence, scratch)


def campaign(args, cases: list[Case], evidence: Path, scratch: Path) -> dict:
    """The whole campaign on a prepared evidence directory and scratch directory: the planning phase, the frozen
    plan, every case, and the sealed package."""
    manifest = dict(model=MODEL, source_commit=args.source_commit, candidate_commit=args.candidate_commit,
                    jar_sha256=args.jar_sha256, cases=[case.id for case in cases], **declarations(cases),
                    planning=[], phase="PLANNING", uncovered=UNCOVERED, results=[], status="IN_PROGRESS")
    if getattr(args, "keep_candidates", False):
        # Named only when asked for, so that a manifest without the option is the one it always was.
        manifest["keep_candidates"] = True
    # The declarations that follow from the cases themselves are on disk before anything is started.
    x01.write_json(evidence / "MANIFEST.json", manifest)
    holds = 0

    @contextmanager
    def hold():
        """One hold of the heavy-JVM lock, after the cooldown that follows the hold before it."""
        nonlocal holds
        if holds:
            time.sleep(args.cooldown)
        holds += 1
        x01.preflight()
        with machine_lock() as owner:
            yield owner

    try:
        # The planning phase: one recording for each offline group. It starts no case.
        static = {entry["case"] for entry in manifest["not_applicable"]}
        recordings = {}
        for index, group in enumerate(group for group in groups([case for case in cases if case.id not in static])
                                      if group[0].offline):
            with hold() as owner:
                recording = plan_recording(args, group, evidence / "cases", scratch / f"plan-{index:02d}", owner)
            recordings[group[0].native] = recording
            manifest["planning"].append({key: recording[key] for key in ("name", "runner", "case", "cases", "error",
                                                                         "trace_ops_sha256") if key in recording}
                                        | dict(declared=[entry["case"] for entry in recording["declared"]]))
            manifest["not_applicable"].extend(recording["declared"])
            seal(evidence, manifest)
            check_size(evidence)
        # The plan is frozen: every NOT_APPLICABLE declaration is on disk before the first case runs, and from here on
        # nothing is added to it.
        manifest["phase"] = "CASES"
        x01.write_json(evidence / "MANIFEST.json", manifest)
        declared = {entry["case"]: entry for entry in manifest["not_applicable"]}
        for case in cases:
            if case.id in declared:
                manifest["results"].append(declared_row(case, evidence / "cases" / case.id, declared[case.id]))
        x01.write_json(evidence / "MANIFEST.json", manifest)
        for index, group in enumerate(groups([case for case in cases if case.id not in declared])):
            work = scratch / f"group-{index:02d}"
            with hold() as owner:
                try:
                    if group[0].offline:
                        rows = run_offline(args, group, evidence / "cases", recordings[group[0].native], work, owner)
                    else:
                        rows = [run_live(args, group[0], evidence / "cases" / group[0].id, work, owner)]
                finally:
                    if work.exists():
                        shutil.rmtree(work)
            manifest["results"].extend(rows)
            # The package is sealed as it is now, so that the summary and the checksum list count against the bound.
            seal(evidence, manifest)
            check_size(evidence)
        manifest["status"] = status_of(manifest["results"], set(declared))
    except Exception as failure:
        manifest.update(status="FAILED", error=f"{type(failure).__name__}: {failure}")
        raise
    finally:
        if scratch.exists():
            shutil.rmtree(scratch)
        seal(evidence, manifest)
        try:
            # The finished package, with its final manifest, summary and checksum list.
            check_size(evidence)
        except replay.OverBound as failure:
            # The result is not a successful one. It is written into the package, which is sealed again; the status
            # that the campaign had reached is kept beside it.
            manifest.update(status="OVER_BOUND", status_before_bound=manifest["status"], over_bound=str(failure))
            seal(evidence, manifest)
    if manifest["status"] == "OVER_BOUND":
        raise replay.OverBound(manifest["over_bound"])
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source_server", type=Path, nargs="?")
    parser.add_argument("--list", action="store_true", help="Print the cases of the selection and start nothing")
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--evidence-dir", type=Path,
                        help="New directory; the campaign uses D:/GitHub/ARCE-Task-Evidence/x02-storage-faults/<candidate>")
    for key in ("source-commit", "candidate-commit", "jar-sha256", "java"):
        parser.add_argument("--" + key)
    parser.add_argument("--case", action="append", help="An exact case identifier, as --list prints it")
    parser.add_argument("--store", action="append", choices=STORES)
    parser.add_argument("--fault", action="append", choices=[*LIVE_FAULTS, *OFFLINE_FAULTS])
    parser.add_argument("--count", action="append", type=int, help="For live faults; the default is 1, and 1 and 4 for WRITE_ERROR")
    parser.add_argument("--keep-candidates", action="store_true",
                        help="Live cases only: keep the store file after each moved write of the faulted start, when "
                             "its SHA-256 is the one the injector logged (W450). Off by default; no class reads it")
    parser.add_argument("--cooldown", type=float, default=90, help="Seconds between two holds of the heavy-JVM lock")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--exit-timeout", type=float, default=180)
    parser.add_argument("--flight-timeout", type=float, default=120)
    parser.add_argument("--settle-timeout", type=float, default=180)
    args = parser.parse_args()
    try:
        if args.list:
            for case in ([by_id(identifier) for identifier in args.case] if args.case
                         else plan(args.store, args.fault, args.count)):
                print(f"{case.id}  {(OFFLINE_FAULTS | LIVE_FAULTS)[case.fault]}  {'/'.join(case.native)}"
                      + (f"  skip={case.skip}" if case.skip else "") + ("  NOT_APPLICABLE" if case.not_applicable else ""))
            return 0
        missing = [key for key in ("source_server", "jar", "evidence_dir", "source_commit", "candidate_commit",
                                   "jar_sha256", "java") if getattr(args, key) is None]
        if missing:
            parser.error("missing: " + ", ".join(missing))
        if args.cooldown < 90:
            parser.error("--cooldown must be at least 90 seconds (WORKER-RULES)")
        manifest = run(args)
        print("X02: " + manifest["status"])
        return 0 if manifest["status"] == "COMPLETE" else 1
    except (OSError, ValueError, smoke.SmokeError, evidence_tools.CandidateError) as failure:
        print("FAIL: " + str(failure)); return 1


if __name__ == "__main__":
    raise SystemExit(main())
