#!/usr/bin/env python3
"""W27d1 and W27d2 native forced-stop cuts of the bay deployment coordinator (W27A-DESIGN section 9; W128, W170).

Each case starts a fresh flat world, launches the gated fixture (a neutral, hand-fuelled Earth-to-Moon rocket with one
data bay and a second bay: a solar package whose owner lacks its research, or for B4 a survey package), cuts it, ends
the first start by a kill, a normal stop, a thrown tick exception or a landing, and restarts twice. The probe only
pauses the receipt committer (staged-only or replaced-before-callback), injects an unknown outcome before or after the
move, pauses the transaction or registry writer, stalls the claim after D5, or makes D2 refuse (B9); it never launches
or registers anything itself. Expected outcomes (W27A section 4's restart rows, W170's one-copy resume):

* nothing durable: T is unclaimed at restart and ordinary recovery leaves a stationary source on Earth;
* a durable INTENT with X absent: the first tick refuses decommission and terminal launch (B13), the restart row
  writes REJECTED (INTERRUPTED), the claim hands T to ordinary recovery, and B1_MOVED's relaunch re-arms the same
  operation, which then registers and consumes the bay;
* a durable INTENT with X present (the registry committed): the restart promotes it, publishes REGISTERED, resumes
  the source at altitude and consumes, proves and retires; the rocket lands with the data bay consumed;
* a durable REGISTERED group: the restart resumes the source (present, or rebuilt with T's source UUID) and lands;
* a retired group (no receipt, T' durable): ordinary recovery leaves the consumed source stationary on Earth;
* a durable REJECTED: no claim, ordinary recovery;
* a durable INTENT whose restarts see a blocked registry (B10's hold half): the claim holds for offline repair;
* a clean stop while D0 waits for its B14 request (B14_STOP): no receipt is ever written, ordinary recovery;
* every case: the solar package byte-equal, each satellite registered at most once, no ERROR or FATAL at a restart.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import time
import uuid

import bay_integrated_evidence as evidence_tools
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_rocket_settlement_completion import require_lock
from run_v180_landed_disassembly_cuts import chunk_record, uuid_of

RECEIPTS = "advancedrocketrycommunity_bay_receipts.dat"
STAGING = RECEIPTS + ".arce-pending"
ORIGIN = (8, 2, 8)
FIXTURE = re.compile(r"ARCE_RELEASE_BAY_FIXTURE logical=(\S+) transfer=(\S+) rocket=(\S+) data=(\S+) solar=(\S+) "
                     r"launch=(\w+) packages=(\S+)")
STATUS = re.compile(r"ARCE_RELEASE_BAY_STATUS logical=(\S+) claim=(\w+) hold=(\w+) reason=(\w+) receipts=\[([^\]]*)\] "
                    r"transfer=(\w+) rocket=(\S+) state=(\w+) y=(\S+) packages=(\S+) locked=(true|false) consumed=(\S+) "
                    r"owned=(-?\d+)")
PENDING = re.compile(r"ARCE_RELEASE_BAY_PENDING pending=(\d+) retained=(\d+) work=(true|false)")
FILLED = re.compile(r"ARCE_RELEASE_BAY_FILLED requested=(\d+) queued=(\d+)")
FIRST_TICK = re.compile(r"ARCE_RELEASE_BAY_FIRST_TICK satellite=(\S+) tick=(\d+) decommission=(\w+) launch=(\w+)")
CLAIM = re.compile(r"ARCE_BAY_CLAIM transfer=(\S+) logical=(\S+) state=(\w+) reason=(\w+)")
STOPPED = re.compile(r"ARCE_BAY_STOPPED claims=(\d+) unresolved=(\d+)")
REGISTRY_BLOCKED = "ARCE_RELEASE_BAY_REGISTRY_BLOCKED session=true"
ALTITUDE = 48
ONLINE_RIDER = "00000000-0000-4000-8000-000000254001"
TXW_PAUSED = re.compile(r"ARCE_RELEASE_TXW_PAUSED point=STAGED")
TXW_DONE = re.compile(r"Transaction writer cut command done|ARCE_RELEASE_TXW_(ARMED|MUTATED)")
SRW_DONE = re.compile(r"Registry writer cut command done|ARCE_RELEASE_SRW_ARMED")
SRW_INJECTED = re.compile(r"ARCE_RELEASE_SRW_INJECTED point=\w+ mode=(FAIL|UNKNOWN)")
UNPROVED_FAILURES = 3  # BayClaim.REGISTRY_UNPROVED_FAILURES (W27A section 4's D3)
SAVED = re.compile(r"\bSaved the game\b", re.IGNORECASE)
# W170-R22 (B16) and W170-R23 (the held startup journal writer).
RETIRE_REFUSED = re.compile(r"ARCE_BAY_RETIRE_REFUSED transfer=(\S+) logical=(\S+) outcome=(\w+)")
B16_ACTIONS = {
    "nudge": ("arce bay release-test nudge {logical}", re.compile(r"ARCE_RELEASE_BAY_NUDGED logical=")),
    "save": ("save-all flush", SAVED),
    "copy": ("arce bay release-test copy {logical}", re.compile(r"ARCE_RELEASE_BAY_COPY logical=\S+ copy=\S+ added=true")),
    "reload": ("arce bay release-test reload {logical}", re.compile(r"ARCE_RELEASE_BAY_RELOADED logical=\S+ rocket=\S+ added=true")),
}
JOURNAL_HELD = re.compile(r"ARCE_RELEASE_JOURNAL_STARTUP_HELD")
JOURNAL_RELEASED = re.compile(r"ARCE_RELEASE_JOURNAL_STARTUP_RELEASED")
RESUME_TICKETS = re.compile(r"ARCE_BAY_RESUME_TICKETS transfer=(\S+) logical=(\S+) stage=(\w+)")
HELD_POLLS = 5

# durable: the data satellite's state on disk after the first start; end: how the first start ends; outcomes: the
# settled state of the first start (when it lands) and of each restart (W170). Outcome names are OUTCOMES' keys.
CASES = {
    "B1_STAGED": dict(cut="B1_STAGED", end="kill", durable="NONE", restarts=("ORDINARY", "ORDINARY")),
    "B1_MOVED": dict(cut="B1_MOVED", end="kill", durable="INTENT", relaunch=True, restarts=("INTERRUPTED", "INTERRUPTED")),
    "B9_STAGED": dict(cut="B9_STAGED", end="kill", durable="INTENT", refuse=True, restarts=("INTERRUPTED", "INTERRUPTED")),
    "B9_MOVED": dict(cut="B9_MOVED", end="kill", durable="REJECTED", refuse=True, restarts=("REFUSED", "REFUSED")),
    "B12_UNKNOWN": dict(cut="B12_UNKNOWN", end="land", durable="NONE", first="LANDED", restarts=("LANDED", "LANDED")),
    "B12_FAILED": dict(cut=None, end="land", durable="NONE", staging=True, first="LANDED_UNCONSUMED",
                       restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "B14_T1": dict(cut=None, end="kill", durable="NONE", journal=True, restarts=("ORDINARY", "ORDINARY")),
    "B18_INTENT": dict(cut="B18_INTENT", end="stop", durable="INTENT", restarts=("INTERRUPTED", "INTERRUPTED")),
    "B18_REJECTED": dict(cut="B18_REJECTED", end="stop", durable="REJECTED", refuse=True, restarts=("REFUSED", "REFUSED")),
    "B18_CRASH": dict(cut="B18_INTENT", end="crash", durable="INTENT", restarts=("INTERRUPTED", "INTERRUPTED")),
    "B11_FULL": dict(cut=None, end="land", durable="NONE", fill=64, first="LANDED_UNCONSUMED",
                     restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "B17_FAIR": dict(cut=None, end="stop", durable="NONE", fill=56, fixture_restart=1, restarts=("LANDED", "LANDED")),
    # W128R-R2: B10's INTENT hold half, and a clean stop while D0 waits for its B14 request.
    # A clean stop drains the paused INTENT and saves the source at altitude, so each restart can show it locked there.
    "B10_BLOCKED": dict(cut="B18_INTENT", end="stop", durable="INTENT", hold="REGISTRY_BLOCKED", restarts=("HELD", "HELD")),
    "B14_STOP": dict(cut=None, end="stop", durable="NONE", journal=True, at_stop=True, restarts=("ORDINARY", "ORDINARY")),
    # W170: the registry checkpoint (B2, B13), D4's REGISTERED generation (B3, B12, B15, B18), decommission after
    # registration (B4), the stall after D5 (B6), and D7's guarded removal (B8, B12, B15, B18).
    "B2_STAGED": dict(registry="STAGED PAUSE", end="kill", durable="INTENT", restarts=("INTERRUPTED", "INTERRUPTED")),
    "B2_MOVED": dict(registry="MOVED PAUSE", end="kill", durable="INTENT", restarts=("LANDED", "LANDED")),
    "B3_STAGED": dict(cut="B3_STAGED", end="kill", durable="INTENT", restarts=("LANDED", "LANDED")),
    "B3_MOVED": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED")),
    "B3_SAVED": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", save=True, restarts=("LANDED", "LANDED")),
    "B4_DECOMMISSION": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", survey=True, decommission=True,
                            restarts=("LANDED", "LANDED")),
    "B6_D5": dict(cut="B6_D5", end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED")),
    "B6_SAVED": dict(cut="B6_D5", end="kill", durable="REGISTERED", save=True, restarts=("LANDED", "LANDED")),
    "B8_STAGED": dict(cut="B8_STAGED", end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED")),
    "B8_MOVED": dict(cut="B8_MOVED", end="kill", durable="RETIRED", restarts=("CONSUMED_REST", "CONSUMED_REST")),
    "B12_D4": dict(cut="B12_D4", end="land", durable="NONE", first="LANDED", restarts=("LANDED", "LANDED")),
    "B12_D7": dict(cut="B12_D7", end="land", durable="NONE", first="LANDED", restarts=("LANDED", "LANDED")),
    "B13_FAILED": dict(registry="STAGED FAIL", end="land", durable="NONE", first="LANDED", unproved=True,
                       restarts=("LANDED", "LANDED")),
    "B13_UNKNOWN": dict(registry="MOVED UNKNOWN", end="land", durable="NONE", first="LANDED", unproved=True,
                        restarts=("LANDED", "LANDED")),
    "B15_D4": dict(cut="B15_D4", end="land", durable="NONE", first="LANDED", restarts=("LANDED", "LANDED")),
    "B15_D7": dict(cut="B15_D7", end="land", durable="NONE", first="LANDED", restarts=("LANDED", "LANDED")),
    "B18_D4": dict(cut="B3_MOVED", end="stop", durable="REGISTERED", restarts=("LANDED", "LANDED")),
    "B18_D7": dict(cut="B8_MOVED", end="stop", durable="RETIRED", restarts=("CONSUMED_REST", "CONSUMED_REST")),
    # W170-R22, B16: D7's guard pauses on the receipt writer's IO thread after the removal is staged; the source is then
    # moved one block and back, saved, joined by a copy of L (which leaves once the hold shows), or unloaded and reloaded.
    # The guard must refuse, the receipt file stay unchanged, and only a fresh D6 proof retire the group.
    "B16_MOTION": dict(cut="B16_GUARD", end="land", durable="NONE", first="LANDED", b16="nudge", restarts=("LANDED", "LANDED")),
    "B16_SAVE": dict(cut="B16_GUARD", end="land", durable="NONE", first="LANDED", b16="save", restarts=("LANDED", "LANDED")),
    "B16_JOIN": dict(cut="B16_GUARD", end="land", durable="NONE", first="LANDED", b16="copy", restarts=("LANDED", "LANDED")),
    "B16_LEAVE": dict(cut="B16_GUARD", end="land", durable="NONE", first="LANDED", b16="reload", restarts=("LANDED", "LANDED")),
    # W170-R23: B3's REGISTERED group restarts with the startup journal writer held; R1 and R2 wait for the release.
    "B3_JOURNAL_HELD": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", journal_hold=1, restarts=("LANDED", "LANDED")),
    # W185: four copies (before/after at both ends) and an offline assigned seat in each case.
    "W185_B3_PRESENT": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="both", save=True, restarts=("LANDED", "LANDED")),
    "W185_B3_ABSENT": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="absent", save=True, restarts=("LANDED", "LANDED")),
    "W185_B4_ABSENT": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="absent", save=True, survey=True,
                           decommission=True, restarts=("LANDED", "LANDED")),
    "W185_B5_SOURCE_AFTER": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="source-after", save=True, restarts=("LANDED", "LANDED")),
    "W185_B5_JOURNAL_AFTER": dict(cut="B6_D5", end="kill", durable="REGISTERED", copies="source-before", save=True, restarts=("LANDED", "LANDED")),
    "W185_B6_AFTER_ABSENT": dict(cut="B6_D5", end="kill", durable="REGISTERED", copies="absent", save=True, restarts=("LANDED", "LANDED")),
    "W185_B6_PROTECTED": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="protected", save=True,
                              copy_hold="PROTECTED_DUPLICATE", restarts=("COPY_HELD", "COPY_HELD")),
    "W185_B6_UNEXPECTED": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="unexpected", save=True,
                               copy_hold="UNEXPECTED_COPY", restarts=("COPY_HELD", "COPY_HELD")),
    "W185_B7_SAVED": dict(cut="B16_GUARD", end="kill", durable="REGISTERED", copies="both", save=True, restarts=("LANDED", "LANDED")),
    "W185_B7_COLD": dict(cut="B16_GUARD", end="kill", durable="REGISTERED", copies="both", save=True, cold=True,
                         restarts=("LANDED", "LANDED")),
}
# W170's second tranche; the first is W27d1's fourteen cases with their W27d2 outcomes, the third W170 R139's five (at
# most 60 starts each).
TRANCHES = {"A": ["B1_STAGED", "B1_MOVED", "B9_STAGED", "B9_MOVED", "B12_UNKNOWN", "B12_FAILED", "B14_T1", "B18_INTENT",
                  "B18_REJECTED", "B18_CRASH", "B11_FULL", "B17_FAIR", "B10_BLOCKED", "B14_STOP"],
            "B": ["B2_STAGED", "B2_MOVED", "B3_STAGED", "B3_MOVED", "B3_SAVED", "B4_DECOMMISSION", "B6_D5", "B6_SAVED",
                  "B8_STAGED", "B8_MOVED", "B12_D4", "B12_D7", "B13_FAILED", "B13_UNKNOWN", "B15_D4", "B15_D7", "B18_D4",
                  "B18_D7"],
            "C": ["B16_MOTION", "B16_SAVE", "B16_JOIN", "B16_LEAVE", "B3_JOURNAL_HELD"]}
TRANCHES["D"] = [case for case in CASES if case.startswith("W185_")]
# W325 composes existing C1 fixture controls; no new production hook is required.
CASES.update({
    "W325_B3_STAGED_PRESENT": dict(cut="B3_STAGED", end="kill", durable="INTENT", copies="both", save=True,
                                   restarts=("LANDED", "LANDED")),
    "W325_B3_STAGED_ABSENT": dict(cut="B3_STAGED", end="kill", durable="INTENT", copies="absent", save=True,
                                  restarts=("LANDED", "LANDED")),
    "W325_B4_LIVE_X_PRESENT_S": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="both", save=True,
                                     survey=True, owned=2, restarts=("LANDED", "LANDED")),
    "W325_B4_LIVE_X_ABSENT_S": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", copies="absent", save=True,
                                    survey=True, owned=2, restarts=("LANDED", "LANDED")),
    "W325_B8_STAGED_OFFLINE": dict(cut="B8_STAGED", end="kill", durable="REGISTERED", offline=True, save=True,
                                   restarts=("LANDED", "LANDED")),
    "W325_B8_MOVED_OFFLINE": dict(cut="B8_MOVED", end="kill", durable="RETIRED", offline=True, save=True,
                                  restarts=("CONSUMED_CREW_WAIT", "CONSUMED_CREW_WAIT")),
    "W325_B3_STAGED_ONLINE": dict(cut="B3_STAGED", end="kill", durable="INTENT", online=True, save=True,
                                  restarts=("LANDED", "LANDED")),
    "W325_B3_MOVED_ONLINE": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", online=True, save=True,
                                 restarts=("LANDED", "LANDED")),
    "W325_B8_STAGED_ONLINE": dict(cut="B8_STAGED", end="kill", durable="REGISTERED", online=True, save=True,
                                  restarts=("LANDED", "LANDED")),
    "W325_B8_MOVED_ONLINE": dict(cut="B8_MOVED", end="kill", durable="RETIRED", online=True, save=True,
                                 restarts=("CONSUMED_REST", "CONSUMED_REST")),
    "W325_OWNER_FULL": dict(cut=None, end="land", durable="NONE", fill=16,
                             fill_owner="00000000-0000-4000-8000-000000128d01", first="LANDED_UNCONSUMED",
                             restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "W325_OTHER_OWNER_ROOM": dict(cut=None, end="land", durable="NONE", fill=16, first="LANDED",
                                   restarts=("LANDED", "LANDED")),
    "W325_B3_STAGED_ONLINE_ABSENT": dict(cut="B3_STAGED", end="kill", durable="INTENT", online=True,
                                         copies="absent", save=True, restarts=("LANDED", "LANDED")),
    "W325_B3_MOVED_ONLINE_ABSENT": dict(cut="B3_MOVED", end="kill", durable="REGISTERED", online=True,
                                        copies="absent", save=True, restarts=("LANDED", "LANDED")),
})
TRANCHES["E"] = [case for case in CASES if case.startswith("W325_")]
CASES.update({
    "W325_RETAINED_GLOBAL": dict(cut=None, end="land", durable="NONE", fill=4096, fill_phase="REJECTED", pending_count=0,
                                  first="LANDED_UNCONSUMED", restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "W325_RETAINED_OWNER": dict(cut=None, end="land", durable="NONE", fill=128, fill_phase="REJECTED", pending_count=0,
                                 fill_owner="00000000-0000-4000-8000-000000128d01", first="LANDED_UNCONSUMED",
                                 restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "W325_REPAIR_FULL": dict(cut=None, end="land", durable="NONE", fill=64, fill_phase="REPAIR",
                              first="LANDED_UNCONSUMED", restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "W325_RELEASED_FULL": dict(cut=None, end="land", durable="NONE", fill=64, fill_phase="REPAIR", fill_released=True,
                                first="LANDED_UNCONSUMED", restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "W325_REPAIR_FAIR": dict(cut=None, end="stop", durable="NONE", fill=56, fill_phase="REPAIR", fixture_restart=1,
                              restarts=("LANDED", "LANDED")),
})
TRANCHES["F"] = [case for case in CASES if case.startswith("W325_") and case not in TRANCHES["E"]]
CASES.update({
    "W325_D4_FAILED": dict(cut=None, registry="STAGED PAUSE", group_failed="D4", end="land", durable="NONE",
                            first="LANDED", restarts=("LANDED", "LANDED")),
    "W325_D7_FAILED": dict(cut="B3_MOVED", group_failed="D7", end="land", durable="NONE",
                            first="LANDED", restarts=("LANDED", "LANDED")),
})
TRANCHES["G"] = ["W325_D4_FAILED", "W325_D7_FAILED"]
CASES["W325_B9_RETRY"] = dict(cut="B9_MOVED", end="kill", durable="REJECTED", refuse=True, relaunch=True,
                               restarts=("REFUSED", "REFUSED"))
TRANCHES["H"] = ["W325_B9_RETRY"]
INTERRUPTED = ("REJECTED", "INTERRUPTED")
REFUSED = ("REJECTED", "SERVER_ERROR")
HELD_INTENT = ("INTENT", "NONE")
# outcome: (data receipt, rest: ORIGIN or LANDED, data bay consumed, satellites the fixture owner holds)
OUTCOMES = {
    "ORDINARY": (None, "ORIGIN", False, 0),
    "INTERRUPTED": (INTERRUPTED, "ORIGIN", False, 0),
    "REFUSED": (REFUSED, "ORIGIN", False, 0),
    "LANDED": (None, "LANDED", True, 1),
    "LANDED_UNCONSUMED": (None, "LANDED", False, 0),
    "CONSUMED_REST": (None, "ORIGIN", True, 1),
    "CONSUMED_CREW_WAIT": (None, "CREW_WAIT", True, 1),
}


def parse_fixture(line: str) -> dict:
    match = FIXTURE.search(line)
    if match is None:
        raise smoke.SmokeError(f"Not a fixture line: {line!r}")
    keys = ("logical", "transfer", "rocket", "data", "solar", "launch", "packages")
    return dict(zip(keys, match.groups()))


def parse_receipts(value: str) -> list[dict]:
    receipts = []
    for entry in filter(None, value.split(",")):
        parts = entry.split(":")
        if len(parts) != 6:
            raise smoke.SmokeError(f"Malformed receipt entry: {entry!r}")
        receipts.append(dict(op=parts[0], satellite=parts[1], phase=parts[2], reason=parts[3],
                             acknowledged=parts[4] == "true", registered=parts[5] == "true"))
    return receipts


def parse_status(line: str) -> dict:
    match = STATUS.search(line)
    if match is None:
        raise smoke.SmokeError(f"Not a status line: {line!r}")
    keys = ("logical", "claim", "hold", "reason", "receipts", "transfer", "rocket", "state", "y", "packages", "locked",
            "consumed", "owned")
    status = dict(zip(keys, match.groups()))
    status["receipts"] = parse_receipts(status["receipts"])
    status["consumed"] = [None if value == "-" else value for value in status["consumed"].split("+")] \
        if status["consumed"] != "NONE" else []
    status["owned"] = int(status["owned"])
    return status


def outcome(case: str, restart: int, relaunched: bool = False) -> str:
    """The settled outcome after this start (0 is the first start of a landing case)."""
    spec = CASES[case]
    if relaunched:
        return "LANDED"
    if spec.get("fixture_restart") == 1 and restart == 0:
        return "ORDINARY"
    return spec["first"] if restart == 0 else spec["restarts"][restart - 1]


def expected_receipt(case: str, restart: int, relaunched: bool = False) -> tuple[str, str] | None:
    """The data satellite's settled receipt (phase, reason) after this start, or None for no receipt."""
    name = outcome(case, restart, relaunched)
    return ("REGISTERED", "NONE") if name == "COPY_HELD" else HELD_INTENT if name == "HELD" else OUTCOMES[name][0]


def receipt_set_matches(receipts: list[dict], expected: dict[str, tuple[str, str]], acknowledged: bool = True) -> bool:
    """Compare the complete group, including duplicates and released rows, not just its data member."""
    return (len(receipts) == len(expected) and {row["satellite"] for row in receipts} == set(expected)
            and all((row["phase"], row["reason"]) == expected[row["satellite"]]
                    and not row.get("released", False) and (not acknowledged or row["acknowledged"])
                    for row in receipts))


def data_receipt_set(fixture: dict, expected: tuple[str, str] | None) -> dict:
    return {} if expected is None else {fixture["data"]: expected}


def settled_disk(case: str, world: Path, fixture: dict, expected: tuple[str, str] | None) -> dict:
    rows = [row for row in evidence_tools.bay_receipts(world / "data" / RECEIPTS)
            if row["rocket"] == fixture["logical"]]
    if not receipt_set_matches(rows, data_receipt_set(fixture, expected), acknowledged=False):
        raise smoke.SmokeError(f"{case}: unexpected complete receipt set on disk: {rows}")
    return dict(receipts_naming_l=rows)


def check_retired_pending(case: str, pending: dict, expected: tuple[int, int] = (0, 0)) -> None:
    """The fixture's retired group releases its charge; unrelated seeded groups retain theirs."""
    if (pending["pending"], pending["retained"]) != expected or pending["work"]:
        raise smoke.SmokeError(f"{case}: retired group retained bay authority/reservations: {pending}, expected {expected}")


def settled(case: str, restart: int, status: dict, fixture: dict, relaunched: bool = False) -> bool:
    """No claim, the receipt resolved as expected, the rocket at rest (landed, or stationary at its origin) with the data
    bay consumed or not as expected; for a hold case, the claim held for offline repair, the INTENT unchanged and the
    rocket locked in TRANSIT at altitude."""
    name = outcome(case, restart, relaunched)
    if not receipt_set_matches(status["receipts"], data_receipt_set(fixture, expected_receipt(case, restart, relaunched))):
        return False
    if name == "COPY_HELD":
        data = [receipt for receipt in status["receipts"] if receipt["satellite"] == fixture["data"]]
        return (status["hold"] == "REPAIR" and status["reason"] == CASES[case]["copy_hold"]
                and status["locked"] == "true" and status["transfer"] == "PREPARED" and len(data) == 1
                and data[0]["phase"] == "REGISTERED" and data[0]["acknowledged"])
    if name == "HELD":
        data = [receipt for receipt in status["receipts"] if receipt["satellite"] == fixture["data"]]
        return (status["claim"] == "OUTCOMES" and status["hold"] == "REPAIR" and status["reason"] == CASES[case]["hold"]
                and status["locked"] == "true" and len(data) == 1 and (data[0]["phase"], data[0]["reason"]) == HELD_INTENT
                and data[0]["acknowledged"] and status["transfer"] == "PREPARED" and status["state"] == "TRANSIT"
                and float(status["y"]) == float(ORIGIN[1] + ALTITUDE))
    if status["claim"] != "NONE" or status["locked"] != "false":
        return False
    expected, rest, consumed, _ = OUTCOMES[name]
    data = [receipt for receipt in status["receipts"] if receipt["satellite"] == fixture["data"]]
    if expected is None:
        if data:
            return False
    elif len(data) != 1 or (data[0]["phase"], data[0]["reason"]) != expected or not data[0]["acknowledged"]:
        return False
    if len(status["consumed"]) != 2 or (status["consumed"][0] is not None) != consumed:
        return False
    if rest == "LANDED":
        return status["state"] == "LANDED"
    if rest == "CREW_WAIT":
        # W27A section 9/B8 permits stationary ordinary recovery after final retirement.
        # RocketTransferRecoveryService.settleRecoveredSource retains T for offline crew.
        return (status["state"] == "FUELED" and status["transfer"] == "PREPARED"
                and status["locked"] == "false" and float(status["y"]) == float(ORIGIN[1]))
    return status["state"] == "FUELED" and status["transfer"] == "NONE" and float(status["y"]) == float(ORIGIN[1])


def check_settled(case: str, restart: int, status: dict, fixture: dict, relaunched: bool = False) -> None:
    """The invariants of every settled start: the second package byte-equal (consumed with its own op for B4), each
    satellite registered at most once by the bay, and only the fixture's satellites named."""
    name = outcome(case, restart, relaunched)
    if not settled(case, restart, status, fixture, relaunched):
        raise smoke.SmokeError(f"{case}/{restart}: incomplete receipt retirement or unsettled authority: {status}")
    if name == "COPY_HELD":
        if not settled(case, restart, status, fixture) or status["owned"] != 1:
            raise smoke.SmokeError(f"{case}/{restart}: copy repair changed the registration or authority: {status}")
        return
    expected = fixture["packages"].split("+")
    actual = status["packages"].split("+")
    consumed = name != "HELD" and OUTCOMES[name][2]
    if len(actual) != 2 or (actual[0] == expected[0]) == consumed:
        raise smoke.SmokeError(f"{case}/{restart}: the data package is {'not ' if consumed else ''}consumed: {status['packages']}")
    survey = CASES[case].get("survey", False)
    if survey:
        if consumed and (status["consumed"][1] is None or actual[1] == expected[1]):
            raise smoke.SmokeError(f"{case}/{restart}: the survey bay was not consumed: {status}")
    elif actual[1] != expected[1] or (status["consumed"] and status["consumed"][1] is not None):
        raise smoke.SmokeError(f"{case}/{restart}: the research-locked solar package changed: {status['packages']}")
    owned = 0 if name == "HELD" else CASES[case].get("owned", OUTCOMES[name][3])
    if status["owned"] != owned:
        raise smoke.SmokeError(f"{case}/{restart}: the fixture owner holds {status['owned']} satellites, expected {owned}")
    for receipt in status["receipts"]:
        if receipt["satellite"] not in (fixture["data"], fixture["solar"]):
            raise smoke.SmokeError(f"{case}/{restart}: a foreign receipt names the fixture rocket: {receipt}")
    if not survey and any(receipt["satellite"] == fixture["solar"] for receipt in status["receipts"]):
        raise smoke.SmokeError(f"{case}/{restart}: the research-locked solar bay got a receipt")


def check_first_tick(case: str, lines: list[str], fixture: dict) -> dict | None:
    """A durable INTENT refuses decommission and launch on the first tick (B13); B4 decommissions its REGISTERED survey
    satellite there, which the acknowledged REGISTERED receipt allows while its launch fence stays."""
    spec = CASES[case]
    if spec["durable"] != "INTENT" and not spec.get("decommission"):
        return None
    satellite = fixture["solar"] if spec.get("decommission") else fixture["data"]
    expected = ("SUCCESS", "DEPLOYMENT_PENDING") if spec.get("decommission") else ("DEPLOYMENT_PENDING", "DEPLOYMENT_PENDING")
    found = [match.groups() for line in lines for match in [FIRST_TICK.search(line)] if match]
    if len(found) != 1 or found[0][0] != satellite or found[0][2:] != expected:
        raise smoke.SmokeError(f"{case}: the first tick did not answer {expected} for {satellite}: {found}")
    return dict(satellite=found[0][0], tick=int(found[0][1]), decommission=found[0][2], launch=found[0][3])


class Session:
    def __init__(self, args, session: Path, evidence: Path):
        self.args, self.session, self.evidence = args, session, evidence
        self.process: smoke.CapturedProcess | None = None
        self.port = 0
        self.candidate = None
        self.starts = []

    def start(self, case: str, restart: int, properties: dict[str, str]) -> smoke.CapturedProcess:
        require_lock(self.args.lock_owner)
        free = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
        if min(free.values()) < 10 * 1024**3:
            raise smoke.SmokeError("Native start refused: under 10 GiB free")
        command = _server_command(self.args.java)
        command[2] = "-Xmx4G"
        for key, value in sorted(properties.items()):
            command.insert(4, f"-Dadvancedrocketrycommunity.{key}={value}")
        try:
            evidence_tools.recheck_installed(self.candidate)
        except evidence_tools.CandidateError as failure:
            raise smoke.SmokeError(str(failure)) from failure
        self.starts.append(dict(case=case, restart=restart, installed_jar=self.candidate["installed_jar"],
                                installed_jar_sha256=self.candidate["installed_jar_sha256"], command=command))
        (self.evidence / "installed-starts.json").write_text(json.dumps(self.starts, indent=2), encoding="utf-8")
        self.process = smoke.CapturedProcess(command, self.session, self.evidence / f"{case}-{restart}.log")
        self.process.wait_for(smoke.READY_MARKER, self.args.startup_timeout)
        return self.process

    def send(self, value: str, marker: re.Pattern[str], timeout: float = 60.0) -> str:
        assert self.process is not None
        start = len(self.process.lines)
        self.process.command(value)
        return self.process.lines[self.process.wait_for(marker, timeout, start_at=start)]

    def wait(self, marker: re.Pattern[str], timeout: float) -> str:
        assert self.process is not None
        return self.process.lines[self.process.wait_for(marker, timeout)]

    def wait_after(self, marker: re.Pattern[str], start: int, timeout: float) -> str:
        """The first line matching {marker} at or after line {start}."""
        assert self.process is not None
        return self.process.lines[self.process.wait_for(marker, timeout, start_at=start)]

    def status(self, logical: str) -> dict:
        return parse_status(self.send(f"arce bay release-test status {logical}", STATUS))

    def pending(self) -> dict:
        match = PENDING.search(self.send("arce bay release-test pending", PENDING))
        return dict(pending=int(match.group(1)), retained=int(match.group(2)), work=match.group(3) == "true")

    def await_settled(self, case: str, restart: int, fixture: dict, relaunched: bool = False) -> dict:
        deadline = time.monotonic() + self.args.settle_timeout
        while True:
            status = self.status(fixture["logical"])
            if settled(case, restart, status, fixture, relaunched):
                check_settled(case, restart, status, fixture, relaunched)
                return status
            if time.monotonic() > deadline:
                raise smoke.SmokeError(f"{case}/{restart}: never settled: {status}")
            time.sleep(1.0)

    def fill(self, count: int) -> dict:
        match = FILLED.search(self.send(f"arce bay release-test fill {count}", FILLED))
        if int(match.group(2)) != count:
            raise smoke.SmokeError(f"The capacity fill queued {match.group(2)} of {count}")
        deadline = time.monotonic() + 60
        while True:
            pending = self.pending()
            if not pending["work"]:
                return pending
            if time.monotonic() > deadline:
                raise smoke.SmokeError(f"The capacity fill was never acknowledged: {pending}")
            time.sleep(0.5)

    def end(self, how: str, timeout: float) -> int | None:
        assert self.process is not None
        process, self.process = self.process, None
        if how == "kill":
            process.process.kill(); process.abort(); code = None
        else:
            if how == "stop":
                process.command("stop")
            code = process.finish(timeout)
        if self.candidate is not None:
            try:
                evidence_tools.recheck_installed(self.candidate)
            except evidence_tools.CandidateError as failure:
                raise smoke.SmokeError(str(failure)) from failure
        return code


def launch_fixture(run: Session, survey: bool = False, online: bool = False) -> dict:
    if online and survey:
        raise smoke.SmokeError("C1's online rider fixture has no survey variant")
    run.send(f"forceload add {ORIGIN[0]} {ORIGIN[2]}", re.compile(r"[Mm]arked|already|forceload|chunk"))
    variant = " survey" if survey else ""
    command = "exit fixture" if online else "fixture"
    marker = r"ARCE_RELEASE_BAY_EXIT_FIXTURE .*" if online else FIXTURE.pattern
    deadline = time.monotonic() + run.args.flight_timeout
    while True:
        line = run.send(f"arce bay release-test {command} {ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]}{variant}",
                        re.compile(marker + r"|ARCE_RELEASE_BAY_FIXTURE_WAIT\b"))
        if "ARCE_RELEASE_BAY_FIXTURE_WAIT" not in line:
            if online:
                expected = f"rider={ONLINE_RIDER} name=W254Rider mounted=true seat=0 mode=survival"
                if expected not in line:
                    raise smoke.SmokeError(f"Survival rider did not board seat zero: {line}")
                fixture = parse_fixture(next(value for value in reversed(run.process.lines) if FIXTURE.search(value)))
                fixture["rider"] = ONLINE_RIDER
            else:
                fixture = parse_fixture(line)
            break
        if time.monotonic() > deadline:
            raise smoke.SmokeError("The fixture source did not become entity-ticking")
        time.sleep(1)
    if fixture["launch"] not in ("SUCCESS", "JOURNAL_PENDING") or fixture["packages"].count("+") != 1:
        raise smoke.SmokeError(f"The fixture did not launch with two packaged bays: {fixture}")
    return fixture


def online_state(run: Session, logical: str, at_cut: bool = False) -> dict:
    line = run.send(f"arce bay release-test exit state {logical}", re.compile(r"ARCE_RELEASE_BAY_EXIT_STATE "))
    state = {}
    for key, value in re.findall(r"(\w+)=(\S+)", line.split("ARCE_RELEASE_BAY_EXIT_STATE ", 1)[1]):
        if key in state:
            raise smoke.SmokeError(f"Repeated online-state field {key}")
        state[key] = value
    if (state.get("rider") != "online" or state.get("rocketSeat") != "0"
            or float(state.get("health", "0")) <= 0
            or at_cut and (state.get("mounted") != "true" or state.get("seat") != "0")):
        raise smoke.SmokeError(f"Online passenger or seat changed: {state}")
    return state


def first_start(run: Session, case: str, world: Path) -> tuple[dict | None, dict]:
    spec = CASES[case]
    properties = {"bayCheckpoint": spec["cut"]} if spec.get("cut") else {}
    if spec.get("offline") or spec.get("copies") and not spec.get("online"):
        properties["bayOfflinePassenger"] = "true"
    if spec.get("refuse"):
        properties["bayRefuseRegistration"] = "true"
    observed: dict = {}
    if spec.get("fill"):
        observed["capacity_fixture"] = evidence_tools.seed_capacity_world(
            world, spec["fill"], spec.get("fill_owner"), spec.get("fill_phase", "INTENT"), spec.get("fill_released", False))
    process = run.start(case, 0, properties)
    if spec.get("staging"):
        # A directory at the staging path refuses the next receipt write before staging: FAILED_BEFORE_REPLACE.
        (world / "data" / STAGING).mkdir(parents=True)
    if spec.get("fill"):
        observed["filled"] = run.pending()
        if (observed["filled"]["pending"] != spec.get("pending_count", spec["fill"])
                or observed["filled"]["retained"] != spec["fill"]):
            raise smoke.SmokeError(f"Capacity fixture did not load its exact pending/retained union: {observed['filled']}")
    fixture = None
    if spec.get("fixture_restart") is None:
        if spec.get("journal"):
            run.send("arce rocket release-test transaction-writer arm STAGED PAUSE", TXW_DONE)
        if spec.get("registry"):
            run.send(f"arce satellite release-test registry-writer arm {spec['registry']}", SRW_DONE)
        fixture = launch_fixture(run, spec.get("survey", False), spec.get("online", False))
        if spec.get("journal"):
            run.send("arce rocket release-test transaction-writer add", TXW_DONE)
            run.wait(TXW_PAUSED, run.args.flight_timeout)
            run.wait(re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} .* reason=ASSEMBLY_JOURNAL_CHECKPOINT"), 30)
        elif spec.get("unproved"):
            # B13: the failing or unknown checkpoint repeats until the third failure's REGISTRY_UNPROVED; then a later
            # write succeeds (a one-shot pause replaces the failing cut and is released) and the group proceeds.
            run.wait(re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} .* reason=REGISTRY_UNPROVED"),
                     run.args.flight_timeout + 60)
            run.send("arce satellite release-test registry-writer arm STAGED PAUSE", SRW_DONE)
            run.wait(re.compile(r"ARCE_RELEASE_SRW_PAUSED point=STAGED"), 60)
            run.send("arce satellite release-test registry-writer release", SRW_DONE)
        elif spec.get("registry"):
            run.wait(re.compile(rf"ARCE_RELEASE_SRW_PAUSED point={spec['registry'].split()[0]}"), run.args.flight_timeout)
        elif spec.get("cut"):
            run.wait(re.compile(rf"ARCE_RELEASE_BAY_(PAUSED|INJECTED) cut={spec['cut']} op="), run.args.flight_timeout)
        if spec.get("group_failed"):
            observed["failed_group_write"] = failed_group_write(run, case, fixture, world)
        if spec.get("b16"):
            observed["b16"] = b16_guard(run, case, fixture, world)
        if spec["end"] == "land" or spec.get("staging"):
            observed["settled"] = run.await_settled(case, 0, fixture)
            if OUTCOMES[outcome(case, 0)][2]:
                observed["pending"] = run.pending()
                check_retired_pending(case, observed["pending"], (spec.get("pending_count", spec.get("fill", 0)), spec.get("fill", 0)))
            if spec.get("b16"):
                observed["fresh_proof"] = check_fresh_proof(case, process.lines, fixture)
        else:
            observed["at_cut"] = run.status(fixture["logical"])
            if observed["at_cut"]["claim"] == "NONE" or observed["at_cut"]["state"] != "TRANSIT":
                raise smoke.SmokeError(f"{case}: the cut did not hold T in TRANSIT: {observed['at_cut']}")
            if spec.get("online"):
                observed["online_at_cut"] = online_state(run, fixture["logical"], at_cut=True)
            if spec.get("copies"):
                deadline = time.monotonic() + run.args.settle_timeout
                while True:
                    line = run.send(f"arce bay release-test copies {fixture['logical']} {spec['copies']}",
                                    re.compile(r"ARCE_RELEASE_BAY_COPIES(?:_WAIT)? logical="))
                    if "_WAIT" not in line:
                        expected_seat = "passenger=false" if spec.get("online") else "passenger=true"
                        if expected_seat not in line:
                            raise smoke.SmokeError(f"{case}: copy fixture seat identity changed: {line}")
                        observed["copies"] = line.strip(); break
                    if time.monotonic() > deadline:
                        raise smoke.SmokeError(f"{case}: destination did not load for copy fixture")
                    time.sleep(1)
                census = run.send(f"arce bay release-test census {fixture['logical']}",
                                  re.compile(r"ARCE_RELEASE_BAY_CENSUS logical="))
                observed["copy_census"] = check_copy_census(census, fixture["logical"],
                    4 if spec["copies"] == "absent" else 5, assigned=not spec.get("online", False))
            if spec.get("save"):
                # The source entity is on disk at its current image before the end (B3 and B6 with S present).
                observed["saved"] = run.send("save-all flush", SAVED, 120)
            if spec.get("offline"):
                census = run.send(f"arce bay release-test census {fixture['logical']}",
                                  re.compile(r"ARCE_RELEASE_BAY_CENSUS logical="))
                observed["offline_census"] = check_copy_census(census, fixture["logical"], 1)
            if spec.get("cold"):
                run.send(f"forceload remove {ORIGIN[0]} {ORIGIN[2]}", re.compile(r"[Uu]nmarked|forceload|chunk"))
                observed["cold_forceloads_saved"] = run.send("save-all flush", SAVED, 120)
    if spec.get("staging"):
        (world / "data" / STAGING).rmdir()
    if spec["end"] == "crash":
        run.send("arce rocket release-test transaction-writer crash false", TXW_DONE)
    if spec.get("at_stop"):
        # The stopping event releases the paused write so the shutdown drain can finish; D0's step never runs again.
        run.send("arce rocket release-test transaction-writer at-stop", TXW_DONE)
    code = run.end("wait" if spec["end"] == "crash" else spec["end"] if spec["end"] != "land" else "stop",
                   run.args.exit_timeout)
    text = "".join(process.lines)
    errors = evidence_tools.unexpected_errors(process.lines, registry_injection=spec.get("unproved", False),
                                              crash_injection=spec["end"] == "crash")
    if errors:
        raise smoke.SmokeError(f"{case}: first start logged unexpected ERROR/FATAL: {errors}")
    if spec["end"] in ("stop", "land") and code != 0:
        raise smoke.SmokeError(f"{case}: the normal stop exited {code}")
    if spec["end"] == "stop" and spec.get("cut") and "ARCE_RELEASE_BAY_RELEASED" not in text:
        raise smoke.SmokeError(f"{case}: the clean stop did not release and drain the paused receipt write")
    observed.update(exit_code=code, claims=[match.groups() for match in CLAIM.finditer(text)])
    if spec.get("unproved"):
        observed["unproved"] = check_unproved(case, process.lines, fixture)
    if spec.get("at_stop"):
        observed["stopped"] = check_stopped_while_waiting(case, process.lines, fixture)
    return fixture, observed


def check_failed_group_write(status: dict, fixture: dict, step: str, before: str, after: str) -> None:
    if (before != after or status["transfer"] != "PREPARED" or status["state"] != "TRANSIT"
            or status["locked"] != "true" or status["owned"] != 1):
        raise smoke.SmokeError(f"{step}: definite receipt failure changed durable authority: {status}")
    if step == "D4" and (status["packages"] != fixture["packages"] or status["consumed"] != [None, None]):
        raise smoke.SmokeError(f"D4 consumed before its receipt acknowledgement: {status}")
    if step == "D7" and (status["consumed"][0] is None or status["packages"] == fixture["packages"]):
        raise smoke.SmokeError(f"D7 lost its consumed source: {status}")


def failed_group_write(run: Session, case: str, fixture: dict, world: Path) -> dict:
    """Block staging only after D1/D4 is acknowledged, then preserve and retry that boundary."""
    step = CASES[case]["group_failed"]
    receipts = world / "data" / RECEIPTS
    before = digest(receipts)
    staging = world / "data" / STAGING
    staging.mkdir()  # Must be absent: never replace or remove an existing fixture file.
    if step == "D4":
        run.send("arce satellite release-test registry-writer release", SRW_DONE)
        marker = re.compile(r"ARCE_BAY_RECEIPTS_UNAVAILABLE failures=3 generation=")
    else:
        run.send("arce bay release-test release", re.compile(r"ARCE_RELEASE_BAY_RELEASE_REQUESTED"))
        marker = re.compile(rf"ARCE_BAY_RETIRE_REFUSED transfer={fixture['transfer']} logical={fixture['logical']} outcome=FAILED_BEFORE_REPLACE")
    observed = run.wait(marker, run.args.flight_timeout)
    status = run.status(fixture["logical"])
    after = digest(receipts)
    check_failed_group_write(status, fixture, step, before, after)
    # This one authority file is immutable while staging is blocked. The full stopped-world
    # authority snapshot is captured normally after the first start and each restart.
    target = run.evidence / f"{case}-failed-receipt.dat"
    shutil.copy2(receipts, target)
    if digest(target) != before:
        raise smoke.SmokeError("Failed-receipt evidence changed during capture")
    staging.rmdir()
    return dict(step=step, marker=observed.strip(), status=status, receipt_sha256=before, receipt_file=target.name)


def b16_guard(run: Session, case: str, fixture: dict, world: Path) -> dict:
    """B16 (W170-R22) at D7's paused guard: the removal is captured (the store's RAM view, which the status line shows,
    already lacks the group's receipts) and staged, while the receipt file still holds them. The case's change to the
    proved source must make the guard refuse with the receipt file unchanged, and the store restore the REGISTERED
    receipt. A joining copy then holds the claim as DUPLICATE_PENDING until it leaves."""
    receipts = world / "data" / RECEIPTS
    paused = run.status(fixture["logical"])
    if (paused["claim"] != "RETIRE" or paused["state"] != "TRANSIT" or paused["transfer"] != "PREPARED"
            or not receipts.is_file()):
        raise smoke.SmokeError(f"{case}: the guard pause is not D7 with T in TRANSIT and a receipt file: {paused}")
    before = digest(receipts)
    command, marker = B16_ACTIONS[CASES[case]["b16"]]
    assert run.process is not None
    start = len(run.process.lines)
    run.send(command.format(logical=fixture["logical"]), marker, 120)
    run.send("arce bay release-test release", re.compile(r"ARCE_RELEASE_BAY_RELEASE_REQUESTED"))
    refused = RETIRE_REFUSED.search(run.wait_after(RETIRE_REFUSED, start, 60))
    if refused.group(1) != fixture["transfer"] or refused.group(3) != "GUARD_REFUSED":
        raise smoke.SmokeError(f"{case}: the paused guard did not refuse: {refused.group(0)}")
    # Observe the file and transition log separately; a fresh D7 may race this harness read (W170-R66).
    after = digest(receipts)
    unchanged = check_refused_file(case, run.process.lines, start, fixture, before, after)
    observed = dict(paused=paused["claim"], receipts=before, refused=refused.group(3), file_unchanged=unchanged)
    if CASES[case]["b16"] == "copy":
        observed["copy"] = "coordinator consumption and absence proof required; no harness discard"
    return observed


def check_refused_file(case: str, lines: list[str], start: int, fixture: dict, before: str, after: str) -> bool:
    """B16 (W170-R45) after the guard's refusal: the receipt file must be byte-equal, unless the fresh proof's own D7 has
    begun, that is, a BAY_RECEIPT_WRITE claim line of L after the BAY_SOURCE_PROOF line that follows the refusal. A proof
    line alone (the coordinator prints it at its first step after any refusal) never excuses a changed file."""
    at = next((index for index in range(start, len(lines)) if (match := RETIRE_REFUSED.search(lines[index]))
               and match.group(2) == fixture["logical"]), None)
    if at is None:
        raise smoke.SmokeError(f"{case}: no refused retirement of L after the pause")
    reasons = [match.group(4) for line in lines[at + 1:]
               if (match := CLAIM.search(line)) and match.group(2) == fixture["logical"]]
    proved = reasons.index("BAY_SOURCE_PROOF") if "BAY_SOURCE_PROOF" in reasons else None
    retiring = proved is not None and "BAY_RECEIPT_WRITE" in reasons[proved + 1:]
    if after != before and not retiring:
        raise smoke.SmokeError(f"{case}: the refused retirement changed the receipt file: {before} -> {after}")
    return after == before


def check_fresh_proof(case: str, lines: list[str], fixture: dict) -> dict:
    """After B16's refusal, D6 runs again (BAY_SOURCE_PROOF) before the one READY_SOURCE: no stale token retires."""
    refusals = [index for index, line in enumerate(lines) if (match := RETIRE_REFUSED.search(line)) and match.group(2) == fixture["logical"]]
    claims = [(index, match.group(3), match.group(4)) for index, line in enumerate(lines)
              if (match := CLAIM.search(line)) and match.group(2) == fixture["logical"]]
    if len(refusals) != 1:
        raise smoke.SmokeError(f"{case}: expected one refused retirement, found {len(refusals)}")
    later = [(state, reason) for index, state, reason in claims if index > refusals[0]]
    ready = [n for n, (state, _) in enumerate(later) if state == "READY_SOURCE"]
    if len(ready) != 1 or ("OWNED_WAIT", "BAY_SOURCE_PROOF") not in later[:ready[0]]:
        raise smoke.SmokeError(f"{case}: no fresh D6 proof before READY_SOURCE after the refusal: {later}")
    return dict(after_refusal=later)


def check_journal_hold(run: Session, case: str, fixture: dict) -> dict:
    """W170-R23 while the startup journal writer is held: the restart claim waits in RESUME for the checked T, with no
    source rebuilt or consumed and no ticket renewed; then the hold is released."""
    run.wait(JOURNAL_HELD, 60)
    polls = []
    for _ in range(HELD_POLLS):
        status = run.status(fixture["logical"])
        polls.append(dict(claim=status["claim"], reason=status["reason"], rocket=status["rocket"], consumed=status["consumed"]))
        if (status["claim"] != "RESUME" or status["reason"] != "TRANSFER_JOURNAL_PENDING" or status["transfer"] != "PREPARED"
                or any(op is not None for op in status["consumed"])):
            raise smoke.SmokeError(f"{case}: R1 or R2 acted before the checked T: {status}")
        time.sleep(1.0)
    assert run.process is not None
    if any((match := RESUME_TICKETS.search(line)) and match.group(2) == fixture["logical"] for line in run.process.lines):
        raise smoke.SmokeError(f"{case}: R1 renewed tickets while the startup writer was held")
    run.send("arce rocket release-test journal-hold-release", re.compile(r"\S"))
    run.wait(JOURNAL_RELEASED, 60)
    return dict(polls=polls)


def check_tickets_after_release(case: str, lines: list[str], fixture: dict) -> int:
    """R1's first renewal of the claim comes after the held startup write was released."""
    released = [index for index, line in enumerate(lines) if JOURNAL_RELEASED.search(line)]
    renewed = [index for index, line in enumerate(lines) if (match := RESUME_TICKETS.search(line)) and match.group(2) == fixture["logical"]]
    if len(released) != 1 or not renewed or renewed[0] < released[0]:
        raise smoke.SmokeError(f"{case}: R1's renewal did not follow the release: released={released} renewed={renewed[:1]}")
    return renewed[0] - released[0]


def check_unproved(case: str, lines: list[str], fixture: dict) -> dict:
    """B13: the claim reported REGISTRY_UNPROVED at the third injected failure, then retired its group: never a rejection."""
    reasons = [match.group(4) for line in lines for match in [CLAIM.search(line)] if match and match.group(2) == fixture["logical"]]
    if "REGISTRY_UNPROVED" not in reasons or reasons[-1] != "READY_SOURCE":
        raise smoke.SmokeError(f"{case}: expected REGISTRY_UNPROVED, then READY_SOURCE: {reasons}")
    if any("REJECTED" in line and fixture["data"] in line for line in lines):
        raise smoke.SmokeError(f"{case}: a RAM registration was rejected")
    # W170-R6: D3's threshold. The armed cut fails every write until the PAUSE re-arm, and each failure answers one request.
    first = next(index for index, line in enumerate(lines)
                 if (match := CLAIM.search(line)) and match.group(2) == fixture["logical"] and match.group(4) == "REGISTRY_UNPROVED")
    failures = sum(1 for line in lines[:first] if SRW_INJECTED.search(line))
    if failures != UNPROVED_FAILURES:
        raise smoke.SmokeError(f"{case}: REGISTRY_UNPROVED came after {failures} injected failures, not {UNPROVED_FAILURES}")
    return dict(reasons=reasons, failures=failures)


def check_stopped_while_waiting(case: str, lines: list[str], fixture: dict) -> dict:
    """The coordinator latched with the waiting claim unresolved and logged no claim transition afterwards."""
    stops = [index for index, line in enumerate(lines) if STOPPED.search(line)]
    if len(stops) != 1:
        raise smoke.SmokeError(f"{case}: expected one coordinator stop line, found {len(stops)}")
    claims, unresolved = (int(value) for value in STOPPED.search(lines[stops[0]]).groups())
    if claims != 1 or unresolved != 1:
        raise smoke.SmokeError(f"{case}: the stop did not find the waiting claim: claims={claims} unresolved={unresolved}")
    later = [line for line in lines[stops[0] + 1:] if CLAIM.search(line) or "ARCE_RELEASE_BAY_" in line]
    if later:
        raise smoke.SmokeError(f"{case}: the coordinator acted after it stopped: {later}")
    waiting = [match.groups() for line in lines[:stops[0]] for match in [CLAIM.search(line)] if match]
    if not waiting or waiting[-1][0] != fixture["transfer"] or waiting[-1][3] != "ASSEMBLY_JOURNAL_CHECKPOINT":
        raise smoke.SmokeError(f"{case}: D0 was not waiting for its B14 request at the stop: {waiting[-1:]}")
    return dict(claims=claims, unresolved=unresolved, last=list(waiting[-1]))


def check_held_restart(case: str, restart: int, lines: list[str], fixture: dict, before: str | None, after: str | None,
                       pending: dict) -> dict:
    """B10's hold half at one restart: the seam ran, one REPAIR transition, no receipt write, the pending charge kept."""
    hold = CASES[case]["hold"]
    if not any(REGISTRY_BLOCKED in line for line in lines):
        raise smoke.SmokeError(f"{case}/{restart}: the blocked-registry seam did not run")
    holds = [match.groups() for line in lines for match in [CLAIM.search(line)]
             if match and match.group(2) == fixture["logical"] and match.group(3) == "REPAIR"]
    if holds != [(fixture["transfer"], fixture["logical"], "REPAIR", hold)]:
        raise smoke.SmokeError(f"{case}/{restart}: expected one REPAIR {hold} transition: {holds}")
    if before is None or before != after:
        raise smoke.SmokeError(f"{case}/{restart}: the held INTENT's receipt file changed: {before} -> {after}")
    if pending["pending"] != 1:
        raise smoke.SmokeError(f"{case}/{restart}: the held INTENT's pending charge changed: {pending}")
    return dict(transitions=len(holds))


def first_tick_satellite(case: str, fixture: dict) -> str | None:
    spec = CASES[case]
    if spec.get("decommission"):
        return fixture["solar"]
    return fixture["data"] if spec["durable"] == "INTENT" else None


def run_case(run: Session, case: str) -> dict:
    global ORIGIN
    spec = CASES[case]
    ORIGIN = (742_008, 80, 742_008) if spec.get("cold") else (8, 2, 8)
    world = run.session / ("world-" + case)
    properties = smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                   encoding="ascii")
    row = dict(case=case, spec=spec)
    fixture, row["first_start"] = first_start(run, case, world)
    receipts = world / "data" / RECEIPTS
    row["cut_disk"] = dict(receipts=digest(receipts) if receipts.is_file() else None,
                           staging=(world / "data" / STAGING).exists())
    if spec["end"] == "land" and fixture is not None:
        row["cut_disk"]["oracles"] = settled_disk(case, world, fixture, expected_receipt(case, 0))
    row["cut_disk"]["raw"] = evidence_tools.snapshot_world(world, run.evidence / f"{case}-cut-raw")
    if spec.get("copies"):
        row["cut_disk"]["copies"] = inspect_copy_disk(world, fixture["logical"], row["first_start"]["copy_census"],
            passenger=ONLINE_RIDER if spec.get("online") else "00000000-0000-4000-8000-000000185001")
    row["restarts"] = []
    for restart in (1, 2):
        hooked = first_tick_satellite(case, fixture) if fixture is not None and restart == 1 else None
        properties = {"bayFirstTick": hooked} if hooked else {}
        if spec.get("hold") == "REGISTRY_BLOCKED":
            properties["bayRegistryBlocked"] = "true"
        held = spec.get("journal_hold") == restart
        if held:  # W170-R23: the journal's gated HOLD mode, with its first (startup) replacement held once
            properties.update(rocketJournalFailure="HOLD", rocketJournalHoldStartup="true")
        process = run.start(case, restart, properties)
        entry = dict(restart=restart)
        if spec.get("online"):
            entry["login"] = run.send(f"arce bay release-test exit login {fixture['logical']}",
                                      re.compile(r"ARCE_RELEASE_BAY_EXIT_LOGIN "))
        if fixture is None and spec.get("fixture_restart") == restart:
            fixture = launch_fixture(run)
        if fixture is not None:
            if held:
                entry["journal_hold"] = check_journal_hold(run, case, fixture)
            entry["settled"] = run.await_settled(case, restart, fixture)
            if outcome(case, restart) == "CONSUMED_CREW_WAIT":
                entry["crew_wait"] = run.wait(re.compile(
                    rf"ARCE_TRANSFER_RETIREMENT_WAIT transfer={fixture['transfer']} .* reason=CREW_WAITING"), 30).strip()
            if spec.get("online"):
                entry["online_settled"] = online_state(run, fixture["logical"])
            if spec.get("copies") or spec.get("offline"):
                census = run.send(f"arce bay release-test census {fixture['logical']}",
                                  re.compile(r"ARCE_RELEASE_BAY_CENSUS logical="))
                expected_count = 5 if spec.get("copy_hold") else 1
                entry["census"] = check_copy_census(census, fixture["logical"], expected_count,
                                                    assigned=not spec.get("online", False))
                if spec.get("cold") and restart == 1 and not any(
                        (match := CLAIM.search(line)) and match.group(2) == fixture["logical"] and match.group(4) == "END_LOADING"
                        for line in process.lines):
                    raise smoke.SmokeError(f"{case}: cold startup did not wait for end loading")
            if held:
                entry["journal_hold"]["tickets_after_release"] = check_tickets_after_release(case, process.lines, fixture)
            if hooked:
                entry["first_tick"] = check_first_tick(case, process.lines, fixture)
            if restart == 2 and spec.get("relaunch"):
                op = [receipt["op"] for receipt in entry["settled"]["receipts"] if receipt["satellite"] == fixture["data"]]
                run.send(f"arce rocket release-test launch {entry['settled']['rocket']} moon", re.compile(r"\S"))
                relaunched = run.await_settled(case, restart, fixture, relaunched=True)
                if len(op) != 1 or relaunched["consumed"][0] != op[0]:
                    raise smoke.SmokeError(f"{case}: the relaunch did not re-arm and consume with the same operation: "
                                           f"{op} {relaunched['consumed']}")
                entry["relaunched"] = relaunched
        entry["pending"] = run.pending()
        if fixture is not None and (outcome(case, restart, "relaunched" in entry) in
                                    ("LANDED", "CONSUMED_REST", "CONSUMED_CREW_WAIT")):
            check_retired_pending(case, entry["pending"], (spec.get("pending_count", spec.get("fill", 0)), spec.get("fill", 0)))
        if outcome(case, restart) == "CONSUMED_CREW_WAIT" and entry["pending"] != dict(pending=0, retained=0, work=False):
            raise smoke.SmokeError(f"{case}/{restart}: retired crew wait retained bay authority: {entry['pending']}")
        if spec.get("fill") and (entry["pending"]["pending"] != spec.get("pending_count", spec["fill"])
                                 or entry["pending"]["retained"] != spec["fill"]):
            raise smoke.SmokeError(f"{case}/{restart}: the filled capacity changed: {entry['pending']}")
        code = run.end("stop", run.args.exit_timeout)
        if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
            raise smoke.SmokeError(f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
        entry.update(exit_code=code, receipts=digest(receipts) if receipts.is_file() else None)
        if fixture is not None:
            entry["oracles"] = settled_disk(case, world, fixture, expected_receipt(case, restart, "relaunched" in entry))
        if spec.get("copy_hold") and entry["receipts"] != row["cut_disk"]["receipts"]:
            raise smoke.SmokeError(f"{case}/{restart}: repair changed the receipt file")
        if spec.get("hold"):
            entry["held"] = check_held_restart(case, restart, process.lines, fixture, row["cut_disk"]["receipts"],
                                               entry["receipts"], entry["pending"])
        row["restarts"].append(entry)
        entry["raw"] = evidence_tools.snapshot_world(world, run.evidence / f"{case}-restart-{restart}-raw")
    row["fixture"] = fixture
    row["status"] = "PASS"
    return row


def check_copy_census(line: str, logical: str, expected_count: int, assigned: bool = True) -> dict:
    match = re.search(r"ARCE_RELEASE_BAY_CENSUS logical=(\S+) count=(\d+) transfer=(\w+) assigned=(true|false) rows=\[([^\]]*)\]", line)
    if not match or match[1] != logical:
        raise smoke.SmokeError(f"Invalid copy census: {line}")
    rows = match[5].split(",") if match[5] else []
    if (int(match[2]) != expected_count or len(rows) != expected_count
            or any(not row.endswith(":" + str(assigned).lower()) for row in rows)
            or len({row.split(":")[0] for row in rows}) != len(rows)
            or match[3] != "NONE" and match[4] != str(assigned).lower()):
        raise smoke.SmokeError(f"Duplicate count or offline seat changed: {line}")
    for row in rows:
        copy_row(row)
    return dict(count=int(match[2]), transfer=match[3], assigned=match[4] == "true", rows=rows)


def copy_row(row: str) -> dict:
    parts = row.split(":")
    if len(parts) != 9 or not all(re.fullmatch(r"-?\d+", parts[i]) for i in (4, 5, 7)):
        raise smoke.SmokeError(f"Invalid copy row: {row}")
    return dict(uuid=parts[0], dimension=":".join(parts[1:3]), hash=parts[3], x=int(parts[4]), z=int(parts[5]),
                ledger=parts[6], epoch=int(parts[7]))


def inspect_copy_disk(world: Path, logical: str, census: dict,
                      passenger: str = "00000000-0000-4000-8000-000000185001") -> list[dict]:
    """After the kill, prove the actual fixture images were saved before testing their restart disposition."""
    expected = {copy["uuid"]: copy for copy in map(copy_row, census["rows"])}
    dimensions = {"minecraft:overworld": world, "advancedrocketrycommunity:moon": world / "dimensions/advancedrocketrycommunity/moon"}
    found = []
    for dimension, x, z in sorted({(row["dimension"], row["x"], row["z"]) for row in expected.values()}):
        if dimension not in dimensions:
            raise smoke.SmokeError(f"Unexpected fixture dimension: {dimension}")
        path = dimensions[dimension] / "entities" / f"r.{x >> 5}.{z >> 5}.mca"
        root = chunk_record(path, x, z)
        if root is None or root.get("Position") != [x, z] or not isinstance(root.get("Entities"), list):
            raise smoke.SmokeError(f"Fixture entity slot was not saved: {dimension} {x} {z}")
        for entity in root["Entities"]:
            data = entity.get("RocketEntityData", {})
            if uuid_of(data.get("assembly_transaction_id")) != logical:
                continue
            identity = uuid_of(entity.get("UUID"))
            wrapper = data.get("resource_escrow", {})
            actual = dict(uuid=identity, dimension=dimension, x=x, z=z, hash=data.get("snapshot", {}).get("content_hash"),
                          ledger=uuid_of(wrapper.get("ledger_id")) or "NONE", epoch=wrapper.get("resource_epoch"))
            seats = data.get("flight_data", {}).get("passengers", {}).get("assignments", [])
            if actual != expected.get(identity) or not any(uuid_of(seat.get("passenger_id"))
                    == passenger for seat in seats):
                raise smoke.SmokeError(f"Saved copy differs from the observed fixture: {actual}")
            found.append(actual)
    if len(found) != len(expected) or {row["uuid"] for row in found} != set(expected):
        raise smoke.SmokeError(f"Copies absent/duplicated on disk: expected={list(expected)} actual={found}")
    return found


# W331 (records/W331-DESIGN.md): the native rows W325 left NOT_COVERED, as their own cases. They live outside CASES and
# TRANCHES, whose contract W325's tests pin, and run through run_w331_case, which reuses the helpers above unchanged.
REGISTRY_FILE = "advancedrocketrycommunity_satellite_missions.dat"
JOURNAL_FILE = "advancedrocketrycommunity_rocket_transactions.dat"
OFFLINE_PASSENGER = "00000000-0000-4000-8000-000000185001"
MOON_ENTITIES = Path("dimensions/advancedrocketrycommunity/moon/entities")
TRIO = re.compile(r"ARCE_RELEASE_BAY_TRIO logical=(\S+) data=(\S+) survey=(\S+) solar=(\S+)")
DECOMMISSIONED = re.compile(r"ARCE_RELEASE_BAY_DECOMMISSIONED satellite=(\S+) result=(\w+) present=(true|false) "
                            r"flushed=(true|false) sequence=(\d+)")
DEPLOYMENT_STATUS = re.compile(r"ARCE_RELEASE_BAY_DEPLOYMENT_STATUS satellite=(\S+) phase=(\w+) reason=(\w+)")
STALE = re.compile(r"ARCE_RELEASE_BAY_STALE_ASSEMBLY logical=(\S+) snapshot=(\S+) records=(\d+) sequence=(\d+)")
TXW_INJECTED = re.compile(r"ARCE_RELEASE_TXW_INJECTED point=(\w+) mode=(\w+)")
SRW_MOVED_UNKNOWN = re.compile(r"ARCE_RELEASE_SRW_INJECTED point=MOVED mode=UNKNOWN")
EXIT_LINE = re.compile(r"ARCE_RELEASE_BAY_EXIT_(LOGIN|STATE) logical=(\S+) ")
COPY_ADDED = re.compile(r"ARCE_RELEASE_BAY_COPY logical=\S+ copy=\S+ added=true")
ROLES = dict(standard=("data", "solar"), offline_rider=("data", "solar"),
             survey=("data", "survey"), online_survey=("data", "survey"), trio=("data", "survey", "solar"))
W331_CASES = {
    # Family T, the transaction journal at D0 (section 3.1).
    "W331_B14_FAILED": dict(rows=("B14.3",), fixture="standard", journal_arm="STAGED FAIL", reject="ASSEMBLY_JOURNAL_UNPROVED",
                            injected=("STAGED", "FAIL", 1), journal_at_reject="unchanged", end="land", durable="NONE",
                            first="LANDED_UNCONSUMED", restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "W331_B14_STALE": dict(rows=("B14.4",), fixture="standard", journal_arm="STAGED PAUSE", stale=True,
                           reject="ASSEMBLY_JOURNAL_PENDING", end="land", durable="NONE", first="LANDED_UNCONSUMED",
                           restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    "W331_B15_JOURNAL": dict(rows=("B15.5",), fixture="standard", journal_arm="MOVED UNKNOWN", reject="ASSEMBLY_JOURNAL_UNPROVED",
                             injected=("MOVED", "UNKNOWN", 1), journal_at_reject="replaced", end="land", durable="NONE",
                             first="LANDED_UNCONSUMED", restarts=("LANDED_UNCONSUMED", "LANDED_UNCONSUMED")),
    # Family W, replace-then-throw and the clean stop (section 3.2).
    "W331_B15_D1": dict(rows=("B15.3",), fixture="standard", cut="B15_D1", end="land", durable="NONE", first="LANDED",
                        restarts=("LANDED", "LANDED")),
    "W331_B15_REGISTRY": dict(rows=("B15.4",), fixture="standard", registry_arm="MOVED UNKNOWN", end="kill", durable="INTENT",
                              registry_names_x=True, restarts=("LANDED", "LANDED")),
    "W331_B18_SUCCESSOR": dict(rows=("B18.6",), fixture="standard", cut="B18_INTENT", successor=True, end="stop",
                               durable="INTENT", restarts=("INTERRUPTED", "INTERRUPTED"), pending=(1, 2)),
    "W331_B18_UNKNOWN": dict(rows=("B18.7",), fixture="standard", registry_arm="MOVED UNKNOWN", unproved=True, end="stop",
                             durable="INTENT", registry_names_x=True, restarts=("LANDED", "LANDED")),
    # Family P (section 3.3) was removed after smoke 2: an unbound wrapper with a non-zero epoch is invalid in the escrow
    # model, so B6.8 needs a ledger-bound source from a real loader fill (W331-DESIGN section 5).
    # Family D, X absent on the initial disk and the online non-data rider (section 3.4).
    "W331_B4_GONE_X_S_PRESENT": dict(rows=("B4.3",), fixture="survey", cut="B6_D5", copies="source-before", predecommission=True,
                                     end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED"), owned=1),
    "W331_B4_GONE_X_S_ABSENT": dict(rows=("B4.10",), fixture="survey", cut="B6_D5", copies="absent", predecommission=True,
                                    end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED"), owned=1),
    "W331_B4_GONE_X_S_PRESENT_ONLINE": dict(rows=("B4.6",), fixture="online_survey", cut="B6_D5", copies="source-before",
                                            predecommission=True, end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED"),
                                            owned=1),
    "W331_B4_GONE_X_S_ABSENT_ONLINE": dict(rows=("B4.7",), fixture="online_survey", cut="B6_D5", copies="absent",
                                           predecommission=True, end="kill", durable="REGISTERED", restarts=("LANDED", "LANDED"),
                                           owned=1),
    "W331_B4_LIVE_X_S_PRESENT_ONLINE": dict(rows=("B4.8",), fixture="online_survey", cut="B3_MOVED", copies="both", end="kill",
                                            durable="REGISTERED", restarts=("LANDED", "LANDED"), owned=2),
    "W331_B4_LIVE_X_S_ABSENT_ONLINE": dict(rows=("B4.9",), fixture="online_survey", cut="B3_MOVED", copies="absent", end="kill",
                                           durable="REGISTERED", restarts=("LANDED", "LANDED"), owned=2),
    # Family G, a multi-REGISTERED partial group (section 3.5).
    "W331_B8_TRIO_STAGED": dict(rows=("B8.7",), fixture="trio", cut="B8_STAGED", end="kill", durable="REGISTERED",
                                restarts=("LANDED", "LANDED"), owned=2, disk_registered=2),
    "W331_B8_TRIO_MOVED": dict(rows=("B8.8",), fixture="trio", cut="B8_MOVED", end="kill", durable="RETIRED",
                               restarts=("CONSUMED_REST", "CONSUMED_REST"), owned=2, disk_registered=0),
    "W331_B17_PARTIAL_D1": dict(rows=("B17.4",), fixture="trio", cut="B1_MOVED", end="kill", durable="INTENT",
                                restarts=("INTERRUPTED", "INTERRUPTED"), immutable=True),
    # Family R, the destination absent after D7 (section 3.6). Smoke 3: D6's barrier proof made S' durable, so ordinary
    # recovery keeps that consumed source (KEEP_SOURCE), as W27A's B8 row allows, instead of rebuilding the destination.
    "W331_B6_DESTINATION_ABSENT": dict(rows=("B6.5",), fixture="standard", end="land-kill", durable="NONE", first="LANDED",
                                       restarts=("CONSUMED_REST", "CONSUMED_REST")),
    # Family L, a login while retirement is queued (section 3.7).
    "W331_B7_LOGIN_QUEUED": dict(rows=("B7.3",), fixture="offline_rider", cut="B16_GUARD", login=True, end="land",
                                 durable="NONE", first="LANDED", restarts=("LANDED", "LANDED")),
    # Family U, the admission union's captured and successor copies (section 3.8; part of B11.7).
    "W331_B11_UNION": dict(rows=("B11.7",), fixture="standard", cut="B3_STAGED", successor=True, union=(2, 2), end="kill",
                           durable="INTENT", restarts=("LANDED", "LANDED"), pending=(0, 0)),
}
W331_TRANCHES = {
    "W331_T": ["W331_B14_FAILED", "W331_B14_STALE", "W331_B15_JOURNAL"],
    "W331_W": ["W331_B15_D1", "W331_B15_REGISTRY", "W331_B18_SUCCESSOR", "W331_B18_UNKNOWN"],
    "W331_D": [case for case in W331_CASES if case.startswith("W331_B4_")],
    "W331_G": ["W331_B8_TRIO_STAGED", "W331_B8_TRIO_MOVED", "W331_B17_PARTIAL_D1"],
    "W331_R": ["W331_B6_DESTINATION_ABSENT"],
    "W331_L": ["W331_B7_LOGIN_QUEUED"],
    "W331_U": ["W331_B11_UNION"],
}


def w331_outcome(spec: dict, restart: int) -> str:
    return spec["first"] if restart == 0 else spec["restarts"][restart - 1]


def w331_settled(spec: dict, name: str, status: dict, fixture: dict) -> bool:
    """settled() for any layout: the complete receipt set, unlocked rocket and no claim."""
    if status["claim"] != "NONE" or status["locked"] != "false":
        return False
    expected, rest, consumed, _ = OUTCOMES[name]
    if not receipt_set_matches(status["receipts"], data_receipt_set(fixture, expected)):
        return False
    if len(status["consumed"]) != len(fixture["roles"]) or (status["consumed"][0] is not None) != consumed:
        return False
    if rest == "LANDED":
        return status["state"] == "LANDED"
    return status["state"] == "FUELED" and status["transfer"] == "NONE" and float(status["y"]) == float(ORIGIN[1])


def w331_check(case: str, spec: dict, name: str, status: dict, fixture: dict) -> None:
    """check_settled() for any layout: registering bays consumed exactly when the outcome consumes, the research-locked
    solar bay always byte-equal with no receipt, the owner's satellites, and no foreign or premature receipt."""
    if not w331_settled(spec, name, status, fixture):
        raise smoke.SmokeError(f"{case}: incomplete receipt retirement or unsettled authority: {status}")
    expected, actual = fixture["packages"].split("+"), status["packages"].split("+")
    roles, consumed = fixture["roles"], OUTCOMES[name][2]
    if len(actual) != len(roles) or len(status["consumed"]) != len(roles):
        raise smoke.SmokeError(f"{case}: the rocket does not hold its {len(roles)} bays: {status}")
    for index, role in enumerate(roles):
        changed, op = actual[index] != expected[index], status["consumed"][index]
        if role == "solar" or not consumed:
            if changed or op is not None:
                raise smoke.SmokeError(f"{case}: the {role} bay changed: {status['packages']} {status['consumed']}")
        elif not changed or op is None:
            raise smoke.SmokeError(f"{case}: the {role} bay was not consumed: {status}")
    owned = spec.get("owned", OUTCOMES[name][3]) if consumed else 0
    if status["owned"] != owned:
        raise smoke.SmokeError(f"{case}: the fixture owner holds {status['owned']} satellites, expected {owned}")
    satellites = [value for value in fixture["satellites"] if value]
    for receipt in status["receipts"]:
        if receipt["satellite"] not in satellites:
            raise smoke.SmokeError(f"{case}: a foreign receipt names the fixture rocket: {receipt}")
        role = roles[satellites.index(receipt["satellite"])]
        if role == "solar" or not consumed and role != "data":
            raise smoke.SmokeError(f"{case}: the {role} bay got a receipt: {receipt}")


def w331_await(run: Session, case: str, restart: int, fixture: dict) -> dict:
    spec, name = W331_CASES[case], w331_outcome(W331_CASES[case], restart)
    deadline = time.monotonic() + run.args.settle_timeout
    while True:
        status = run.status(fixture["logical"])
        if w331_settled(spec, name, status, fixture):
            w331_check(case, spec, name, status, fixture)
            return status
        if time.monotonic() > deadline:
            raise smoke.SmokeError(f"{case}/{restart}: never settled as {name}: {status}")
        time.sleep(1.0)


def w331_launch(run: Session, kind: str) -> dict:
    """The fixture of one layout; exit variants print the bay FIXTURE line before their own line."""
    run.send(f"forceload add {ORIGIN[0]} {ORIGIN[2]}", re.compile(r"[Mm]arked|already|forceload|chunk"))
    exit_kind = kind in ("online_survey", "offline_rider")
    suffix = {"survey": " survey", "trio": " trio", "online_survey": " survey", "offline_rider": " offline"}.get(kind, "")
    marker = {"online_survey": r"ARCE_RELEASE_BAY_EXIT_FIXTURE .*",
              "offline_rider": r"ARCE_RELEASE_BAY_EXIT_OFFLINE_FIXTURE .*"}.get(kind, FIXTURE.pattern)
    assert run.process is not None
    deadline = time.monotonic() + run.args.flight_timeout
    while True:
        start = len(run.process.lines)
        line = run.send(f"arce bay release-test {'exit fixture' if exit_kind else 'fixture'} {ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]}"
                        f"{suffix}", re.compile(marker + r"|ARCE_RELEASE_BAY_FIXTURE_WAIT\b"))
        if "ARCE_RELEASE_BAY_FIXTURE_WAIT" not in line:
            break
        if time.monotonic() > deadline:
            raise smoke.SmokeError("The fixture source did not become entity-ticking")
        time.sleep(1)
    lines = run.process.lines[start:]
    fixture = parse_fixture(next(value for value in reversed(lines) if FIXTURE.search(value)))
    if kind == "online_survey" and f"rider={ONLINE_RIDER} name=W254Rider mounted=true seat=0 mode=survival" not in line:
        raise smoke.SmokeError(f"Survival rider did not board seat zero: {line}")
    if kind == "offline_rider" and f"rider={ONLINE_RIDER} online=false" not in line:
        raise smoke.SmokeError(f"The offline rider is online: {line}")
    if exit_kind:
        fixture["rider"] = ONLINE_RIDER
    fixture["roles"] = ROLES[kind]
    fixture["satellites"] = [fixture["data"], fixture["solar"]]
    if kind == "trio":
        trio = TRIO.search(next(value for value in lines if TRIO.search(value)))
        if trio[1] != fixture["logical"] or trio[2] != fixture["data"] or trio[3] != fixture["solar"]:
            raise smoke.SmokeError(f"The trio line names other bays: {trio[0]}")
        fixture["satellites"].append(trio[4])
    if fixture["launch"] not in ("SUCCESS", "JOURNAL_PENDING") or fixture["packages"].count("+") != len(fixture["roles"]) - 1:
        raise smoke.SmokeError(f"The fixture did not launch with its {len(fixture['roles'])} bays: {fixture}")
    return fixture


def exit_fields(line: str) -> dict:
    fields = {}
    for key, value in re.findall(r"(\w+)=(\S+)", line.split(" logical=", 1)[1]):
        if key in fields:
            raise smoke.SmokeError(f"Repeated exit field {key}")
        fields[key] = value
    return fields


def entities_naming(world: Path, logical: str, directory: Path = MOON_ENTITIES) -> list[str]:
    """Every saved entity naming L in one dimension's entity regions (bounded: each stored chunk of each region file)."""
    found = []
    folder = world / directory
    for region in sorted(folder.glob("r.*.*.mca")) if folder.is_dir() else []:
        parts = region.name.split(".")
        region_x, region_z = int(parts[1]), int(parts[2])
        for slot in range(1024):
            root = chunk_record(region, region_x * 32 + slot % 32, region_z * 32 + slot // 32)
            for entity in (root or {}).get("Entities", []):
                if uuid_of(entity.get("RocketEntityData", {}).get("assembly_transaction_id")) == logical:
                    found.append(uuid_of(entity.get("UUID")))
    return found


def w331_rejection(run: Session, case: str, fixture: dict, start: int, data: Path, before: str | None) -> dict:
    """Family T and P: D0 rejects every bay of L with the case's reason, before any receipt names L.

    D0's reason is recorded on each bay member and remembered by the session (``BayTransferRecovery.reject``), which is
    what the bay menu shows; the claim line itself only reports the claim's wait reasons and its READY_SOURCE handoff.
    """
    spec = W331_CASES[case]
    reason = spec["reject"]
    marker = re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} logical={fixture['logical']} state=READY_SOURCE\b")
    run.wait_after(marker, start, run.args.flight_timeout + 60)
    assert run.process is not None
    lines = run.process.lines
    at = next(index for index in range(start, len(lines)) if marker.search(lines[index]))
    reasons = [match.group(4) for line in lines[start:at + 1] if (match := CLAIM.search(line))
               and match.group(2) == fixture["logical"]]
    if spec.get("journal_arm") and "ASSEMBLY_JOURNAL_CHECKPOINT" not in reasons:
        raise smoke.SmokeError(f"{case}: D0 never requested its journal checkpoint: {reasons}")
    observed = dict(reasons=reasons, menu=[])
    for satellite in fixture["satellites"]:
        shown = DEPLOYMENT_STATUS.search(run.send(f"arce bay release-test deployment-status {satellite}", DEPLOYMENT_STATUS))
        if shown.groups() != (satellite, "NONE", reason):
            raise smoke.SmokeError(f"{case}: the bay of {satellite} does not show D0's {reason}: {shown[0]}")
        observed["menu"].append(shown[0])
    if spec.get("injected"):
        # The injected write must be D0's own requested checkpoint: between its request and the handoff (W331 smoke 2).
        point, mode, minimum = spec["injected"]
        requested = next(index for index in range(start, at) if (match := CLAIM.search(lines[index]))
                         and match.group(2) == fixture["logical"] and match.group(4) == "ASSEMBLY_JOURNAL_CHECKPOINT")
        injected = [match.groups() for line in lines[requested:at] if (match := TXW_INJECTED.search(line))]
        everywhere = [match.groups() for line in lines[start:at] if (match := TXW_INJECTED.search(line))]
        if len(injected) < minimum or any(row != (point, mode) for row in everywhere):
            raise smoke.SmokeError(f"{case}: expected at least {minimum} {point} {mode} injection of D0's requested "
                                   f"write: {injected} of {everywhere}")
        observed["injected"] = dict(after_request=len(injected), total=len(everywhere))
    if spec.get("journal_at_reject"):
        now = digest(data / JOURNAL_FILE) if (data / JOURNAL_FILE).is_file() else None
        if (now == before) != (spec["journal_at_reject"] == "unchanged"):
            raise smoke.SmokeError(f"{case}: the journal file is not {spec['journal_at_reject']}: {before} -> {now}")
        observed["journal"] = dict(before=before, at_rejection=now)
    if any(receipt["rocket"] == fixture["logical"] for receipt in evidence_tools.bay_receipts(data / RECEIPTS)):
        raise smoke.SmokeError(f"{case}: a receipt names L after the D0 rejection")
    status = run.status(fixture["logical"])
    if status["receipts"] or status["packages"] != fixture["packages"]:
        raise smoke.SmokeError(f"{case}: the rejection changed a package or wrote a receipt: {status}")
    observed["status"] = status
    return observed


def w331_login(run: Session, case: str, fixture: dict, world: Path) -> dict:
    """Family L at D7's paused guard: the assigned offline rider logs in, a late source copy joins, the guard releases."""
    assert run.process is not None
    paused = run.status(fixture["logical"])
    if paused["claim"] != "RETIRE" or paused["state"] != "TRANSIT" or paused["transfer"] != "PREPARED":
        raise smoke.SmokeError(f"{case}: the guard pause is not D7 with T in TRANSIT: {paused}")
    start = len(run.process.lines)
    login = exit_fields(run.send(f"arce bay release-test exit login {fixture['logical']}", EXIT_LINE))
    if (login.get("rider") != "online" or login.get("mounted") != "false" or login.get("transfer") != "PREPARED"
            or login.get("seat") != "0" or login.get("rocketSeat") != "0" or login.get("claim") != "RETIRE"):
        raise smoke.SmokeError(f"{case}: the held login was routed, settled or cleared the seat: {login}")
    copy = run.send(f"arce bay release-test copy {fixture['logical']}", COPY_ADDED).strip()
    run.send("arce bay release-test release", re.compile(r"ARCE_RELEASE_BAY_RELEASE_REQUESTED"))
    refused = RETIRE_REFUSED.search(run.wait_after(RETIRE_REFUSED, start, 60))
    if refused.group(2) != fixture["logical"] or refused.group(3) != "GUARD_REFUSED":
        raise smoke.SmokeError(f"{case}: the paused guard did not refuse after the login and the copy: {refused.group(0)}")
    return dict(paused=paused["claim"], login=login, copy=copy, refused=refused.group(3))


def w331_first_start(run: Session, case: str, world: Path) -> tuple[dict, dict]:
    spec = W331_CASES[case]
    online = spec["fixture"] == "online_survey"
    properties = {"bayCheckpoint": spec["cut"]} if spec.get("cut") else {}
    if spec.get("copies") and not online:
        properties["bayOfflinePassenger"] = "true"
    data = world / "data"
    observed: dict = {}
    process = run.start(case, 0, properties)
    before = None
    if spec.get("journal_arm"):
        run.send(f"arce rocket release-test transaction-writer arm {spec['journal_arm']}", TXW_DONE)
    if spec.get("registry_arm"):
        run.send(f"arce satellite release-test registry-writer arm {spec['registry_arm']}", SRW_DONE)
    start = len(process.lines)
    fixture = w331_launch(run, spec["fixture"])
    if spec.get("journal_arm"):
        # The journal bytes just before the inert record that makes D0 request its checkpoint.
        before = digest(data / JOURNAL_FILE) if (data / JOURNAL_FILE).is_file() else None
        run.send("arce rocket release-test transaction-writer add", TXW_DONE)
        observed["journal_before_add"] = before
    if spec.get("stale"):
        run.wait(TXW_PAUSED, run.args.flight_timeout)
        run.wait(re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} .* reason=ASSEMBLY_JOURNAL_CHECKPOINT"), 30)
        stale = STALE.search(run.send(f"arce bay release-test stale-assembly {fixture['logical']}", STALE))
        if stale[1] != fixture["logical"]:
            raise smoke.SmokeError(f"{case}: the stale record names another rocket: {stale[0]}")
        observed["stale"] = stale[0]
    if spec.get("reject"):
        observed["rejection"] = w331_rejection(run, case, fixture, start, data, before)
        if spec.get("journal_arm"):
            # A one-shot PAUSE replaces a FAIL or UNKNOWN cut; releasing it at once lets every later write pass.
            if spec["journal_arm"] != "STAGED PAUSE":
                run.send("arce rocket release-test transaction-writer arm STAGED PAUSE", TXW_DONE)
            run.send("arce rocket release-test transaction-writer release", TXW_DONE)
    elif spec.get("registry_arm"):
        run.wait_after(SRW_MOVED_UNKNOWN, start, run.args.flight_timeout)
        if spec.get("unproved"):
            run.wait_after(re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} .* reason=REGISTRY_UNPROVED"), start,
                           run.args.flight_timeout + 60)
    elif spec.get("cut"):
        run.wait(re.compile(rf"ARCE_RELEASE_BAY_(PAUSED|INJECTED) cut={spec['cut']} op="), run.args.flight_timeout)
    if spec.get("successor"):
        filled = FILLED.search(run.send("arce bay release-test fill 1", FILLED))
        if filled.groups() != ("1", "1"):
            raise smoke.SmokeError(f"{case}: the successor receipt was not queued behind the paused write: {filled[0]}")
        observed["successor"] = filled[0]
        if spec.get("union"):
            observed["union"] = run.pending()
            if (observed["union"]["pending"], observed["union"]["retained"]) != spec["union"]:
                raise smoke.SmokeError(f"{case}: the union counted an op twice or lost one: {observed['union']}")
    if spec.get("login"):
        observed["login"] = w331_login(run, case, fixture, world)
    if spec["end"] in ("land", "land-kill"):
        observed["settled"] = w331_await(run, case, 0, fixture)
        if OUTCOMES[spec["first"]][2]:
            observed["pending"] = run.pending()
            check_retired_pending(case, observed["pending"])
        if spec.get("login"):
            observed["fresh_proof"] = check_fresh_proof(case, process.lines, fixture)
            # Smoke 3: after the claim's READY_SOURCE handoff, the normal acknowledged transfer carries the assigned online
            # passenger to the destination (W27A section 4.1: "or the destination after normal acknowledged transfer").
            after = exit_fields(run.send(f"arce bay release-test exit state {fixture['logical']}", EXIT_LINE))
            if (after.get("rider") != "online" or after.get("mounted") != "true" or after.get("transfer") != "COMMITTED"
                    or after.get("rocketSeat") != "0"):
                raise smoke.SmokeError(f"{case}: the assigned passenger did not arrive by the normal transfer: {after}")
            observed["rider_after"] = after
        if spec["end"] == "land-kill":
            if observed["settled"]["transfer"] != "COMMITTED":
                raise smoke.SmokeError(f"{case}: T is not COMMITTED at the landing: {observed['settled']}")
    else:
        observed["at_cut"] = run.status(fixture["logical"])
        if observed["at_cut"]["claim"] == "NONE" or observed["at_cut"]["state"] != "TRANSIT":
            raise smoke.SmokeError(f"{case}: the cut did not hold T in TRANSIT: {observed['at_cut']}")
        if spec.get("registry_arm") and (len(observed["at_cut"]["receipts"]) != 1
                                         or observed["at_cut"]["receipts"][0]["phase"] != "INTENT"
                                         or not observed["at_cut"]["receipts"][0]["acknowledged"]):
            raise smoke.SmokeError(f"{case}: the unresolved registry write moved the INTENT: {observed['at_cut']}")
        if online:
            observed["online_at_cut"] = online_state(run, fixture["logical"], at_cut=True)
        if spec.get("copies"):
            deadline = time.monotonic() + run.args.settle_timeout
            while True:
                line = run.send(f"arce bay release-test copies {fixture['logical']} {spec['copies']}",
                                re.compile(r"ARCE_RELEASE_BAY_COPIES(?:_WAIT)? logical="))
                if "_WAIT" not in line:
                    if ("passenger=false" if online else "passenger=true") not in line:
                        raise smoke.SmokeError(f"{case}: copy fixture seat identity changed: {line}")
                    observed["copies"] = line.strip(); break
                if time.monotonic() > deadline:
                    raise smoke.SmokeError(f"{case}: destination did not load for copy fixture")
                time.sleep(1)
            census = run.send(f"arce bay release-test census {fixture['logical']}", re.compile(r"ARCE_RELEASE_BAY_CENSUS logical="))
            observed["copy_census"] = check_copy_census(census, fixture["logical"], 4 if spec["copies"] == "absent" else 5,
                                                        assigned=not online)
        if spec.get("predecommission"):
            gone = DECOMMISSIONED.search(run.send(f"arce bay release-test decommission {fixture['solar']}", DECOMMISSIONED))
            if gone.groups()[:4] != (fixture["solar"], "SUCCESS", "false", "true"):
                raise smoke.SmokeError(f"{case}: X was not decommissioned durably before the end: {gone[0]}")
            observed["decommissioned"] = gone[0]
        if spec.get("copies"):
            observed["saved"] = run.send("save-all flush", SAVED, 120)
    code = run.end("kill" if spec["end"] in ("kill", "land-kill") else "stop", run.args.exit_timeout)
    errors = evidence_tools.unexpected_errors(process.lines, registry_injection=bool(spec.get("registry_arm")),
                                              journal_injection=spec.get("journal_arm") in ("STAGED FAIL", "MOVED UNKNOWN"))
    if errors:
        raise smoke.SmokeError(f"{case}: first start logged unexpected ERROR/FATAL: {errors}")
    if spec["end"] in ("stop", "land") and code != 0:
        raise smoke.SmokeError(f"{case}: the normal stop exited {code}")
    if spec["end"] == "stop":
        observed["stopped"] = w331_stopped(case, process.lines, fixture)
    observed.update(exit_code=code, claims=[match.groups() for match in CLAIM.finditer("".join(process.lines))])
    return fixture, observed


def w331_stopped(case: str, lines: list[str], fixture: dict) -> dict:
    """Family W's clean stops: one coordinator stop with L unresolved, no claim transition after it, and for a paused
    receipt write its release by the stop."""
    stops = [index for index, line in enumerate(lines) if STOPPED.search(line)]
    if len(stops) != 1:
        raise smoke.SmokeError(f"{case}: expected one coordinator stop line, found {len(stops)}")
    claims, unresolved = (int(value) for value in STOPPED.search(lines[stops[0]]).groups())
    if claims < 1 or unresolved < 1:
        raise smoke.SmokeError(f"{case}: the stop did not find L unresolved: claims={claims} unresolved={unresolved}")
    later = [line.strip() for line in lines[stops[0] + 1:] if CLAIM.search(line)]
    if later:
        raise smoke.SmokeError(f"{case}: the coordinator acted after it stopped: {later}")
    if W331_CASES[case].get("cut") and not any("ARCE_RELEASE_BAY_RELEASED" in line for line in lines):
        raise smoke.SmokeError(f"{case}: the clean stop did not release and drain the paused receipt write")
    return dict(claims=claims, unresolved=unresolved)


def w331_disk(case: str, world: Path, fixture: dict, restart: int, cut: dict | None = None) -> dict:
    """The raw oracles of one stopped world (W331-DESIGN section 3): receipts, registry, journal and Moon entities."""
    spec = W331_CASES[case]
    data = world / "data"
    receipts = [row for row in evidence_tools.bay_receipts(data / RECEIPTS)]
    mine = [row for row in receipts if row["rocket"] == fixture["logical"]]
    satellites = evidence_tools.registry_satellites(data / REGISTRY_FILE)
    observed = dict(receipts=len(receipts), receipts_naming_l=[(row["satellite"], row["phase"]) for row in mine],
                    registry=len(satellites))
    if restart == 0:
        if spec.get("reject") and mine:
            raise smoke.SmokeError(f"{case}: a receipt names L on disk after the D0 rejection: {mine}")
        if spec.get("successor") and spec["end"] == "stop":
            phases = sorted(row["phase"] for row in receipts)
            if len(mine) != 1 or len(receipts) != 2 or phases != ["INTENT", "INTENT"]:
                raise smoke.SmokeError(f"{case}: the stop did not drain both INTENT writes: {receipts}")
        if "disk_registered" in spec and [row["phase"] for row in mine] != ["REGISTERED"] * spec["disk_registered"]:
            raise smoke.SmokeError(f"{case}: expected {spec['disk_registered']} REGISTERED receipts of L on disk: {mine}")
        if spec.get("immutable"):
            if len(mine) != 1 or mine[0]["phase"] != "INTENT" or mine[0]["satellite"] != fixture["data"]:
                raise smoke.SmokeError(f"{case}: partial D1 is not exactly the first bay's INTENT: {mine}")
            observed["intent"] = mine[0]
        if spec["end"] == "land-kill":
            if mine or entities_naming(world, fixture["logical"]):
                raise smoke.SmokeError(f"{case}: the destination or a receipt of L is on disk at the kill")
            # D6's barrier proof wrote the consumed source S' to its entity chunk; the transfer removed it only in RAM.
            sources = entities_naming(world, fixture["logical"], Path("entities"))
            if sources != [fixture["rocket"]]:
                raise smoke.SmokeError(f"{case}: expected exactly the durable consumed source of L on disk: {sources}")
            observed.update(destination_absent=True, durable_source=sources[0])
    else:
        settled_disk(f"{case}/{restart}", world, fixture, OUTCOMES[w331_outcome(spec, restart)][0])
        if spec.get("immutable"):
            if len(mine) != 1 or (mine[0]["phase"], mine[0]["reason"]) != INTERRUPTED:
                raise smoke.SmokeError(f"{case}/{restart}: the interrupted receipt changed: {mine}")
            for key in ("op", "satellite", "src_before", "dst_before", "package_sig"):
                if mine[0][key] != cut["intent"][key]:
                    raise smoke.SmokeError(f"{case}/{restart}: the group's {key} changed after the partial D1")
        if spec["end"] == "land-kill":
            moon, earth = entities_naming(world, fixture["logical"]), entities_naming(world, fixture["logical"], Path("entities"))
            if moon or earth != [fixture["rocket"]]:
                raise smoke.SmokeError(f"{case}/{restart}: expected only the kept consumed source of L: moon={moon} earth={earth}")
            observed["survivor"] = earth[0]
    if spec.get("registry_names_x") and satellites.count(fixture["data"]) != 1:
        raise smoke.SmokeError(f"{case}/{restart}: the registry file does not name X exactly once: {satellites}")
    if spec.get("predecommission") and fixture["solar"] in satellites:
        raise smoke.SmokeError(f"{case}/{restart}: the decommissioned X is on disk: {satellites}")
    if spec.get("stale") and not evidence_tools.raw_names(data / JOURNAL_FILE, fixture["logical"]):
        raise smoke.SmokeError(f"{case}/{restart}: the stale assembly record of L is not in the journal file")
    if restart == 0:
        if spec["durable"] == "REGISTERED":
            expected = {satellite: ("REGISTERED", "NONE") for satellite, role in zip(fixture["satellites"], fixture["roles"])
                        if role != "solar"}
        else:
            expected = data_receipt_set(fixture, HELD_INTENT if spec["durable"] == "INTENT" else None)
        if not receipt_set_matches(mine, expected, acknowledged=False):
            raise smoke.SmokeError(f"{case}: unexpected complete receipt set on cut disk: {mine}")
    return observed


def run_w331_case(run: Session, case: str) -> dict:
    global ORIGIN
    spec = W331_CASES[case]
    ORIGIN = (8, 2, 8)
    online = spec["fixture"] == "online_survey"
    world = run.session / ("world-" + case)
    properties = smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                   encoding="ascii")
    row = dict(case=case, rows=list(spec["rows"]), spec=spec)
    fixture, row["first_start"] = w331_first_start(run, case, world)
    receipts = world / "data" / RECEIPTS
    row["cut_disk"] = dict(receipts=digest(receipts) if receipts.is_file() else None,
                           oracles=w331_disk(case, world, fixture, 0))
    row["cut_disk"]["raw"] = evidence_tools.snapshot_world(world, run.evidence / f"{case}-cut-raw")
    if spec.get("copies"):
        row["cut_disk"]["copies"] = inspect_copy_disk(world, fixture["logical"], row["first_start"]["copy_census"],
                                                      passenger=ONLINE_RIDER if online else OFFLINE_PASSENGER)
    row["restarts"] = []
    for restart in (1, 2):
        hooked = fixture["data"] if restart == 1 and spec["durable"] == "INTENT" else None
        process = run.start(case, restart, {"bayFirstTick": hooked} if hooked else {})
        entry = dict(restart=restart)
        if online:
            entry["login"] = run.send(f"arce bay release-test exit login {fixture['logical']}",
                                      re.compile(r"ARCE_RELEASE_BAY_EXIT_LOGIN "))
        entry["settled"] = w331_await(run, case, restart, fixture)
        if spec["end"] == "land-kill":
            # Ordinary recovery keeps the durable consumed source S' (T's source UUID), with the landing's consumed op.
            landed = row["first_start"]["settled"]
            if entry["settled"]["rocket"] != fixture["rocket"] or entry["settled"]["consumed"] != landed["consumed"]:
                raise smoke.SmokeError(f"{case}/{restart}: recovery did not keep the consumed source of L: "
                                       f"{entry['settled']} {landed}")
        if online:
            entry["online_settled"] = online_state(run, fixture["logical"])
        if spec.get("copies"):
            census = run.send(f"arce bay release-test census {fixture['logical']}", re.compile(r"ARCE_RELEASE_BAY_CENSUS logical="))
            entry["census"] = check_copy_census(census, fixture["logical"], 1, assigned=not online)
        if hooked:
            found = [match.groups() for line in process.lines for match in [FIRST_TICK.search(line)] if match]
            if len(found) != 1 or found[0][0] != hooked or found[0][2:] != ("DEPLOYMENT_PENDING", "DEPLOYMENT_PENDING"):
                raise smoke.SmokeError(f"{case}: the first tick did not fence the durable INTENT: {found}")
            entry["first_tick"] = found[0]
        entry["pending"] = run.pending()
        if OUTCOMES[w331_outcome(spec, restart)][2]:
            check_retired_pending(case, entry["pending"])
        if "pending" in spec and (entry["pending"]["pending"], entry["pending"]["retained"]) != spec["pending"]:
            raise smoke.SmokeError(f"{case}/{restart}: pending/retained {entry['pending']}, expected {spec['pending']}")
        code = run.end("stop", run.args.exit_timeout)
        if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
            raise smoke.SmokeError(f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
        entry.update(exit_code=code, receipts=digest(receipts) if receipts.is_file() else None,
                     oracles=w331_disk(case, world, fixture, restart, row["cut_disk"]["oracles"]))
        row["restarts"].append(entry)
        entry["raw"] = evidence_tools.snapshot_world(world, run.evidence / f"{case}-restart-{restart}-raw")
    row["fixture"] = fixture
    row["status"] = "PASS"
    return row


def check_candidate(args) -> dict:
    """W331: the candidate JAR, its SHA-256 and its production source commit, checked before Java starts."""
    try:
        return evidence_tools.candidate_pin(args.jar, args.jar_sha256, args.candidate_commit)
    except evidence_tools.CandidateError as failure:
        raise smoke.SmokeError(str(failure)) from failure


def recheck_candidate(candidate: dict) -> None:
    try:
        evidence_tools.recheck_candidate(candidate)
    except evidence_tools.CandidateError as failure:
        raise smoke.SmokeError(str(failure)) from failure


def add_candidate_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--jar-sha256", required=True, help="The candidate JAR's SHA-256; refused when the file differs")
    parser.add_argument("--candidate-commit", required=True,
                        help="The candidate's production source; refused unless HEAD has the same production files")


def run(args) -> dict:
    assert_pin(args.source_commit)
    candidate = check_candidate(args)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the disposable directory")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or smoke.is_link_or_junction(libraries) or any(
            smoke.is_link_or_junction(path) for path in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe or missing Forge libraries")
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w170-bay-cuts-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  lock_owner=args.lock_owner, tranche=args.tranche, candidate=candidate)
    runner = Session(args, session, evidence)
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        candidate.update(evidence_tools.install_candidate(args.jar, session / "mods" / args.jar.name, args.jar_sha256))
        runner.candidate = candidate
        runner.port = smoke.allocate_port()
        smoke.write_server_configuration(session, runner.port, True)
        tranches = {**TRANCHES, **W331_TRANCHES}
        for case in args.case or (tranches[args.tranche] if args.tranche else list(CASES)):
            report["in_progress"] = case
            try:
                report["cases"].append(run_w331_case(runner, case) if case in W331_CASES else run_case(runner, case))
                print(f"{case}: PASS (cut, two restarts)", flush=True)
            except smoke.SmokeError as failure:
                if runner.process is not None:
                    runner.process.abort(); runner.process = None
                report["cases"].append(dict(case=case, status="FAILED", error=str(failure)))
                report["cases"][-1]["raw"] = evidence_tools.snapshot_world(
                    session / ("world-" + case), evidence / f"{case}-failure-raw")
                print(f"{case}: FAIL {failure}", flush=True)
            report["cases"][-1]["observed_ticks"] = evidence_tools.case_observations(evidence, case)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            shutil.rmtree(session / ("world-" + case), ignore_errors=True)
            if report["cases"][-1]["status"] != "PASS" and getattr(args, "stop_on_failure", False):
                break
        report.pop("in_progress", None); assert_pin(args.source_commit); recheck_candidate(candidate)
        report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["cases"]) else "FAILED"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if runner.process is not None:
            runner.process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if (session.parent == intended and not smoke.is_link_or_junction(session) and marker.is_file()
                and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    add_candidate_arguments(parser)
    parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES) + list(W331_CASES))
    parser.add_argument("--tranche", choices=sorted(TRANCHES) + sorted(W331_TRANCHES))
    parser.add_argument("--stop-on-failure", action="store_true", help="Preserve the failed case and stop this family")
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--exit-timeout", type=float, default=180)
    parser.add_argument("--flight-timeout", type=float, default=120)
    parser.add_argument("--settle-timeout", type=float, default=180)
    args = parser.parse_args()
    try:
        report = run(args)
        return 0 if report["status"] == "PASS" else 1
    except (OSError, smoke.SmokeError, evidence_tools.CandidateError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
