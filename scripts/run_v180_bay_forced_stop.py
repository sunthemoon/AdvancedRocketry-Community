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

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_rocket_settlement_completion import require_lock

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
TXW_PAUSED = re.compile(r"ARCE_RELEASE_TXW_PAUSED point=STAGED")
TXW_DONE = re.compile(r"Transaction writer cut command done|ARCE_RELEASE_TXW_(ARMED|MUTATED)")
SRW_DONE = re.compile(r"Registry writer cut command done|ARCE_RELEASE_SRW_ARMED")
SRW_INJECTED = re.compile(r"ARCE_RELEASE_SRW_INJECTED point=\w+ mode=(FAIL|UNKNOWN)")
UNPROVED_FAILURES = 3  # BayClaim.REGISTRY_UNPROVED_FAILURES (W27A section 4's D3)
SAVED = re.compile(r"Saved the game|saved the game|Saving the game", re.IGNORECASE)
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
}
# W170's second tranche; the first is W27d1's fourteen cases with their W27d2 outcomes, the third W170 R139's five (at
# most 60 starts each).
TRANCHES = {"A": ["B1_STAGED", "B1_MOVED", "B9_STAGED", "B9_MOVED", "B12_UNKNOWN", "B12_FAILED", "B14_T1", "B18_INTENT",
                  "B18_REJECTED", "B18_CRASH", "B11_FULL", "B17_FAIR", "B10_BLOCKED", "B14_STOP"],
            "B": ["B2_STAGED", "B2_MOVED", "B3_STAGED", "B3_MOVED", "B3_SAVED", "B4_DECOMMISSION", "B6_D5", "B6_SAVED",
                  "B8_STAGED", "B8_MOVED", "B12_D4", "B12_D7", "B13_FAILED", "B13_UNKNOWN", "B15_D4", "B15_D7", "B18_D4",
                  "B18_D7"],
            "C": ["B16_MOTION", "B16_SAVE", "B16_JOIN", "B16_LEAVE", "B3_JOURNAL_HELD"]}
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
    return HELD_INTENT if name == "HELD" else OUTCOMES[name][0]


def settled(case: str, restart: int, status: dict, fixture: dict, relaunched: bool = False) -> bool:
    """No claim, the receipt resolved as expected, the rocket at rest (landed, or stationary at its origin) with the data
    bay consumed or not as expected; for a hold case, the claim held for offline repair, the INTENT unchanged and the
    rocket locked in TRANSIT at altitude."""
    name = outcome(case, restart, relaunched)
    if name == "HELD":
        data = [receipt for receipt in status["receipts"] if receipt["satellite"] == fixture["data"]]
        return (status["claim"] == "OUTCOMES" and status["hold"] == "REPAIR" and status["reason"] == CASES[case]["hold"]
                and status["locked"] == "true" and len(data) == 1 and (data[0]["phase"], data[0]["reason"]) == HELD_INTENT
                and data[0]["acknowledged"] and status["transfer"] == "PREPARED" and status["state"] == "TRANSIT"
                and float(status["y"]) == float(ORIGIN[1] + ALTITUDE))
    if status["claim"] != "NONE":
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
    return status["state"] == "FUELED" and status["transfer"] == "NONE" and float(status["y"]) == float(ORIGIN[1])


def check_settled(case: str, restart: int, status: dict, fixture: dict, relaunched: bool = False) -> None:
    """The invariants of every settled start: the second package byte-equal (consumed with its own op for B4), each
    satellite registered at most once by the bay, and only the fixture's satellites named."""
    name = outcome(case, restart, relaunched)
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
    owned = 0 if name == "HELD" else OUTCOMES[name][3]
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

    def start(self, case: str, restart: int, properties: dict[str, str]) -> smoke.CapturedProcess:
        require_lock(self.args.lock_owner)
        free = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
        if min(free.values()) < 10 * 1024**3:
            raise smoke.SmokeError("Native start refused: under 10 GiB free")
        command = _server_command(self.args.java)
        command[2] = "-Xmx4G"
        for key, value in sorted(properties.items()):
            command.insert(4, f"-Dadvancedrocketrycommunity.{key}={value}")
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
            process.process.kill(); process.abort(); return None
        if how == "stop":
            process.command("stop")
        return process.finish(timeout)


def launch_fixture(run: Session, survey: bool = False) -> dict:
    run.send(f"forceload add {ORIGIN[0]} {ORIGIN[2]}", re.compile(r"[Mm]arked|already|forceload|chunk"))
    variant = " survey" if survey else ""
    fixture = parse_fixture(run.send(f"arce bay release-test fixture {ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]}{variant}", FIXTURE))
    if fixture["launch"] not in ("SUCCESS", "JOURNAL_PENDING") or fixture["packages"].count("+") != 1:
        raise smoke.SmokeError(f"The fixture did not launch with two packaged bays: {fixture}")
    return fixture


def first_start(run: Session, case: str, world: Path) -> tuple[dict | None, dict]:
    spec = CASES[case]
    properties = {"bayCheckpoint": spec["cut"]} if spec.get("cut") else {}
    if spec.get("refuse"):
        properties["bayRefuseRegistration"] = "true"
    process = run.start(case, 0, properties)
    observed: dict = {}
    if spec.get("staging"):
        # A directory at the staging path refuses the next receipt write before staging: FAILED_BEFORE_REPLACE.
        (world / "data" / STAGING).mkdir(parents=True)
    if spec.get("fill"):
        observed["filled"] = run.fill(spec["fill"])
    fixture = None
    if spec.get("fixture_restart") is None:
        if spec.get("journal"):
            run.send("arce rocket release-test transaction-writer arm STAGED PAUSE", TXW_DONE)
        if spec.get("registry"):
            run.send(f"arce satellite release-test registry-writer arm {spec['registry']}", SRW_DONE)
        fixture = launch_fixture(run, spec.get("survey", False))
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
        if spec.get("b16"):
            observed["b16"] = b16_guard(run, case, fixture, world)
        if spec["end"] == "land" or spec.get("staging"):
            observed["settled"] = run.await_settled(case, 0, fixture)
            if spec.get("b16"):
                observed["fresh_proof"] = check_fresh_proof(case, process.lines, fixture)
        else:
            observed["at_cut"] = run.status(fixture["logical"])
            if observed["at_cut"]["claim"] == "NONE" or observed["at_cut"]["state"] != "TRANSIT":
                raise smoke.SmokeError(f"{case}: the cut did not hold T in TRANSIT: {observed['at_cut']}")
            if spec.get("save"):
                # The source entity is on disk at its current image before the end (B3 and B6 with S present).
                observed["saved"] = run.send("save-all flush", SAVED, 120)
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
    # The file is read before the log: the server logs a fresh D7's claim line before its writer can change the file.
    after = digest(receipts)
    unchanged = check_refused_file(case, run.process.lines, start, fixture, before, after)
    observed = dict(paused=paused["claim"], receipts=before, refused=refused.group(3), file_unchanged=unchanged)
    if CASES[case]["b16"] == "copy":
        run.wait_after(re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} .* reason=DUPLICATE_PENDING"), start, 60)
        run.send(f"arce bay release-test discard-copy {fixture['logical']}", re.compile(r"ARCE_RELEASE_BAY_COPY_DISCARDED .* count=1"))
        observed["copy"] = "DUPLICATE_PENDING, then discarded"
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
    spec = CASES[case]
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
        if fixture is None and spec.get("fixture_restart") == restart:
            fixture = launch_fixture(run)
        if fixture is not None:
            if held:
                entry["journal_hold"] = check_journal_hold(run, case, fixture)
            entry["settled"] = run.await_settled(case, restart, fixture)
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
        if spec.get("fill") and entry["pending"]["pending"] != spec["fill"]:
            raise smoke.SmokeError(f"{case}/{restart}: the filled capacity changed: {entry['pending']}")
        code = run.end("stop", run.args.exit_timeout)
        if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
            raise smoke.SmokeError(f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
        entry.update(exit_code=code, receipts=digest(receipts) if receipts.is_file() else None)
        if spec.get("hold"):
            entry["held"] = check_held_restart(case, restart, process.lines, fixture, row["cut_disk"]["receipts"],
                                               entry["receipts"], entry["pending"])
        row["restarts"].append(entry)
    row["fixture"] = fixture
    row["status"] = "PASS"
    return row


def run(args) -> dict:
    assert_pin(args.source_commit)
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
                  lock_owner=args.lock_owner, tranche=args.tranche)
    runner = Session(args, session, evidence)
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        runner.port = smoke.allocate_port()
        smoke.write_server_configuration(session, runner.port, True)
        for case in args.case or (TRANCHES[args.tranche] if args.tranche else list(CASES)):
            report["in_progress"] = case
            try:
                report["cases"].append(run_case(runner, case))
                print(f"{case}: PASS (cut, two restarts)", flush=True)
            except smoke.SmokeError as failure:
                if runner.process is not None:
                    runner.process.abort(); runner.process = None
                report["cases"].append(dict(case=case, status="FAILED", error=str(failure)))
                print(f"{case}: FAIL {failure}", flush=True)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            shutil.rmtree(session / ("world-" + case), ignore_errors=True)
        report.pop("in_progress", None); assert_pin(args.source_commit)
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
    parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--tranche", choices=sorted(TRANCHES))
    parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--exit-timeout", type=float, default=180)
    parser.add_argument("--flight-timeout", type=float, default=120)
    parser.add_argument("--settle-timeout", type=float, default=180)
    args = parser.parse_args()
    try:
        report = run(args)
        return 0 if report["status"] == "PASS" else 1
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
