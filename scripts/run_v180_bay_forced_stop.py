#!/usr/bin/env python3
"""W27d1 native forced-stop cuts of the bay deployment coordinator (W27A-DESIGN section 9; W128 and its resumption).

Each case starts a fresh flat world, launches the gated fixture (a neutral, hand-fuelled Earth-to-Moon rocket with one
data bay and one solar bay whose owner lacks the solar research), cuts it, ends the first start by a kill, a normal stop,
a thrown tick exception or a landing, and restarts twice. The probe only pauses the receipt committer (staged-only or
replaced-before-callback), injects an unknown outcome, or pauses the transaction writer during D0's B14 request; it
never launches or registers anything itself. Expected outcomes (W27A section 4's restart rows for INTENT):

* nothing durable: T is unclaimed at restart and ordinary recovery leaves a stationary source on Earth;
* a durable INTENT: the first tick refuses decommission and terminal launch (B13), the restart row writes REJECTED
  (INTERRUPTED), the claim hands T to ordinary recovery, and B1_MOVED's relaunch re-arms the same operation;
* a durable REJECTED: no claim, ordinary recovery;
* every case: both packages byte-equal, no satellite registered, no ERROR or FATAL at a restart.
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
                    r"transfer=(\w+) rocket=(\S+) state=(\w+) y=(\S+) packages=(\S+)")
PENDING = re.compile(r"ARCE_RELEASE_BAY_PENDING pending=(\d+) retained=(\d+) work=(true|false)")
FILLED = re.compile(r"ARCE_RELEASE_BAY_FILLED requested=(\d+) queued=(\d+)")
FIRST_TICK = re.compile(r"ARCE_RELEASE_BAY_FIRST_TICK satellite=(\S+) tick=(\d+) decommission=(\w+) launch=(\w+)")
CLAIM = re.compile(r"ARCE_BAY_CLAIM transfer=(\S+) logical=(\S+) state=(\w+) reason=(\w+)")
TXW_PAUSED = re.compile(r"ARCE_RELEASE_TXW_PAUSED point=STAGED")
TXW_DONE = re.compile(r"Transaction writer cut command done|ARCE_RELEASE_TXW_(ARMED|MUTATED)")

# durable: the data satellite's receipt on disk after the first start; end: how the first start ends.
CASES = {
    "B1_STAGED": dict(cut="B1_STAGED", end="kill", durable="NONE"),
    "B1_MOVED": dict(cut="B1_MOVED", end="kill", durable="INTENT", relaunch=True),
    "B9_STAGED": dict(cut="B9_STAGED", end="kill", durable="INTENT"),
    "B9_MOVED": dict(cut="B9_MOVED", end="kill", durable="REJECTED"),
    "B12_UNKNOWN": dict(cut="B12_UNKNOWN", end="land", durable="REJECTED"),
    "B12_FAILED": dict(cut=None, end="land", durable="NONE", staging=True),
    "B14_T1": dict(cut=None, end="kill", durable="NONE", journal=True),
    "B18_INTENT": dict(cut="B18_INTENT", end="stop", durable="INTENT"),
    "B18_REJECTED": dict(cut="B18_REJECTED", end="stop", durable="REJECTED"),
    "B18_CRASH": dict(cut="B18_INTENT", end="crash", durable="INTENT"),
    "B11_FULL": dict(cut=None, end="land", durable="NONE", fill=64),
    "B17_FAIR": dict(cut=None, end="stop", durable="NONE", fill=56, fixture_restart=1),
}
INTERRUPTED = ("REJECTED", "INTERRUPTED")
UNAVAILABLE = ("REJECTED", "DEPLOYMENT_UNAVAILABLE")


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
    keys = ("logical", "claim", "hold", "reason", "receipts", "transfer", "rocket", "state", "y", "packages")
    status = dict(zip(keys, match.groups()))
    status["receipts"] = parse_receipts(status["receipts"])
    return status


def expected_receipt(case: str, restart: int, relaunched: bool = False) -> tuple[str, str] | None:
    """The data satellite's settled receipt (phase, reason) after this start, or None for no receipt."""
    spec = CASES[case]
    if relaunched:
        return UNAVAILABLE
    if spec.get("fixture_restart") == 1:
        return UNAVAILABLE if restart >= 1 else None
    durable = spec["durable"]
    if durable == "INTENT":
        return INTERRUPTED
    if durable == "REJECTED":
        return UNAVAILABLE
    return None


def settled(case: str, restart: int, status: dict, fixture: dict, relaunched: bool = False) -> bool:
    """No claim, the receipt resolved as expected, and the rocket at rest (landed, or stationary at its origin)."""
    if status["claim"] != "NONE":
        return False
    expected = expected_receipt(case, restart, relaunched)
    data = [receipt for receipt in status["receipts"] if receipt["satellite"] == fixture["data"]]
    if expected is None:
        if data:
            return False
    elif len(data) != 1 or (data[0]["phase"], data[0]["reason"]) != expected or not data[0]["acknowledged"]:
        return False
    landed = CASES[case]["end"] == "land" or relaunched or CASES[case].get("fixture_restart") == 1
    if landed:
        return status["state"] == "LANDED"
    return status["state"] == "FUELED" and status["transfer"] == "NONE" and float(status["y"]) == float(ORIGIN[1])


def check_settled(case: str, restart: int, status: dict, fixture: dict) -> None:
    """The invariants of every settled start: packages byte-equal, no registration, only the fixture's satellites."""
    if status["packages"] != fixture["packages"]:
        raise smoke.SmokeError(f"{case}/{restart}: the rocket's packages changed: {status['packages']}")
    for receipt in status["receipts"]:
        if receipt["registered"]:
            raise smoke.SmokeError(f"{case}/{restart}: W27d1 registered a satellite: {receipt}")
        if receipt["satellite"] not in (fixture["data"], fixture["solar"]):
            raise smoke.SmokeError(f"{case}/{restart}: a foreign receipt names the fixture rocket: {receipt}")
    if any(receipt["satellite"] == fixture["solar"] for receipt in status["receipts"]):
        raise smoke.SmokeError(f"{case}/{restart}: the research-locked solar bay got a receipt")


def check_first_tick(case: str, lines: list[str], fixture: dict) -> dict | None:
    if CASES[case]["durable"] != "INTENT":
        return None
    found = [match.groups() for line in lines for match in [FIRST_TICK.search(line)] if match]
    if len(found) != 1 or found[0][0] != fixture["data"] or found[0][2:] != ("DEPLOYMENT_PENDING", "DEPLOYMENT_PENDING"):
        raise smoke.SmokeError(f"{case}: the first tick did not refuse decommission and launch of a durable INTENT: {found}")
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
                check_settled(case, restart, status, fixture)
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


def launch_fixture(run: Session) -> dict:
    run.send(f"forceload add {ORIGIN[0]} {ORIGIN[2]}", re.compile(r"[Mm]arked|already|forceload|chunk"))
    fixture = parse_fixture(run.send(f"arce bay release-test fixture {ORIGIN[0]} {ORIGIN[1]} {ORIGIN[2]}", FIXTURE))
    if fixture["launch"] not in ("SUCCESS", "JOURNAL_PENDING") or fixture["packages"].count("+") != 1:
        raise smoke.SmokeError(f"The fixture did not launch with two packaged bays: {fixture}")
    return fixture


def first_start(run: Session, case: str, world: Path) -> tuple[dict | None, dict]:
    spec = CASES[case]
    properties = {"bayCheckpoint": spec["cut"]} if spec["cut"] else {}
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
        fixture = launch_fixture(run)
        if spec.get("journal"):
            run.send("arce rocket release-test transaction-writer add", TXW_DONE)
            run.wait(TXW_PAUSED, run.args.flight_timeout)
            run.wait(re.compile(rf"ARCE_BAY_CLAIM transfer={fixture['transfer']} .* reason=ASSEMBLY_JOURNAL_CHECKPOINT"), 30)
        elif spec["cut"]:
            run.wait(re.compile(rf"ARCE_RELEASE_BAY_(PAUSED|INJECTED) cut={spec['cut']} op="), run.args.flight_timeout)
        if spec["end"] == "land" or spec.get("staging"):
            observed["settled"] = run.await_settled(case, 0, fixture)
        else:
            observed["at_cut"] = run.status(fixture["logical"])
            if observed["at_cut"]["claim"] == "NONE" or observed["at_cut"]["state"] != "TRANSIT":
                raise smoke.SmokeError(f"{case}: the cut did not hold T in TRANSIT: {observed['at_cut']}")
    if spec.get("staging"):
        (world / "data" / STAGING).rmdir()
    if spec["end"] == "crash":
        run.send("arce rocket release-test transaction-writer crash false", TXW_DONE)
    code = run.end("wait" if spec["end"] == "crash" else spec["end"] if spec["end"] != "land" else "stop",
                   run.args.exit_timeout)
    text = "".join(process.lines)
    if spec["end"] in ("stop", "land") and code != 0:
        raise smoke.SmokeError(f"{case}: the normal stop exited {code}")
    if spec["end"] == "stop" and spec["cut"] and "ARCE_RELEASE_BAY_RELEASED" not in text:
        raise smoke.SmokeError(f"{case}: the clean stop did not release and drain the paused receipt write")
    observed.update(exit_code=code, claims=[match.groups() for match in CLAIM.finditer(text)])
    return fixture, observed


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
        intent = fixture is not None and spec["durable"] == "INTENT" and restart == 1
        process = run.start(case, restart, {"bayFirstTick": fixture["data"]} if intent else {})
        entry = dict(restart=restart)
        if fixture is None and spec.get("fixture_restart") == restart:
            fixture = launch_fixture(run)
        if fixture is not None:
            entry["settled"] = run.await_settled(case, restart, fixture)
            if intent:
                entry["first_tick"] = check_first_tick(case, process.lines, fixture)
            if restart == 2 and spec.get("relaunch"):
                op = [receipt["op"] for receipt in entry["settled"]["receipts"] if receipt["satellite"] == fixture["data"]]
                run.send(f"arce rocket release-test launch {entry['settled']['rocket']} moon", re.compile(r"\S"))
                relaunched = run.await_settled(case, restart, fixture, relaunched=True)
                again = [receipt["op"] for receipt in relaunched["receipts"] if receipt["satellite"] == fixture["data"]]
                if op != again:
                    raise smoke.SmokeError(f"{case}: the relaunch did not re-arm the same operation: {op} {again}")
                entry["relaunched"] = relaunched
        entry["pending"] = run.pending()
        if spec.get("fill") and entry["pending"]["pending"] != spec["fill"]:
            raise smoke.SmokeError(f"{case}/{restart}: the filled capacity changed: {entry['pending']}")
        code = run.end("stop", run.args.exit_timeout)
        if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
            raise smoke.SmokeError(f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
        entry.update(exit_code=code, receipts=digest(receipts) if receipts.is_file() else None)
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
    marker = session / ".arce-w128r-bay-cuts-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  lock_owner=args.lock_owner)
    runner = Session(args, session, evidence)
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        runner.port = smoke.allocate_port()
        smoke.write_server_configuration(session, runner.port, True)
        for case in args.case or list(CASES):
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
