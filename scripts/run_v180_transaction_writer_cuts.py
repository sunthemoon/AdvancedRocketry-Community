#!/usr/bin/env python3
"""W129a native cuts of the off-thread rocket transaction journal writer (ADR-074, design section 6.4).

Each case starts a fresh flat world, proves a baseline file, mutates the journal with inert records in a dimension that
never loads, cuts the writer, ends the first start by a kill, a normal stop or a thrown tick exception, and restarts
twice. T1 to T3 kill the server with a request in flight, staged, or moved before publication. R6 stops normally with a
stale capture in flight before a logout-time mutation. R7 throws out of a tick (running=true, stopped=true, no stopping
event): a stale success then a covering capture, definite-failure exhaustion, UNKNOWN exhaustion, and a stalled write
interrupted by the watchdog, which must not be reported as a drain.
"""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import time
import uuid

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest
from run_v180_escrow_pre_f2 import assert_pin
from run_v180_rocket_settlement_completion import require_lock

JOURNAL = "advancedrocketrycommunity_rocket_transactions.dat"
STAGING = JOURNAL + ".arce-pending"
PREFIX = "arce rocket release-test transaction-writer "
STATUS = re.compile(r"ARCE_RELEASE_TXW_STATUS b14=(\w+) sequence=(\d+) acknowledged=(\w+) outcome=(\w+) "
                    r"busy=(true|false) dirty=(true|false) records=(\d+)")
DRAINED = re.compile(r"ARCE_CHECKED_WRITER_DRAINED root=rocket_transactions target=(\d+) committed=(\d+) attempts=(\d+)")
INCOMPLETE = re.compile(r"ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root=rocket_transactions target=(\d+) committed=(\d+) "
                        r"attempts=(\d+) reason=(\w+)")
STREAK = re.compile(r"ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=(\w+) sequence=(\d+) acknowledged=(\w+)")
WATCHDOG = re.compile(r"Considering it to be crashed|A single server tick took")

# case: (cut point, cut mode, how the first start ends, records on disk after it, staging left after it)
CASES = {
    "T1": ("CAPTURE", "PAUSE", "kill", 0, False),
    "T2": ("STAGED", "PAUSE", "kill", 0, True),
    "T3": ("MOVED", "PAUSE", "kill", 1, False),
    "R6": ("STAGED", "PAUSE", "stop", 2, False),
    "R7_STALE": ("STAGED", "PAUSE", "crash", 2, False),
    "R7_FAILED": ("STAGED", "FAIL", "crash", 0, False),
    "R7_UNKNOWN": ("MOVED", "UNKNOWN", "crash", 2, False),
    "R7_INTERRUPTED": ("STAGED", "STALL", "crash", 0, True),
}


def parse_status(line: str) -> dict:
    match = STATUS.search(line)
    if match is None:
        raise smoke.SmokeError(f"Not a status line: {line!r}")
    return dict(b14=match.group(1), sequence=int(match.group(2)), acknowledged=match.group(3), outcome=match.group(4),
                busy=match.group(5) == "true", dirty=match.group(6) == "true", records=int(match.group(7)))


def read_root(path: Path) -> dict:
    if path.stat().st_size > 16 * 1024 * 1024:
        raise smoke.SmokeError(f"Oversized journal fixture: {path}")
    with gzip.open(path, "rb") as stream:
        raw = stream.read(64 * 1024 * 1024 + 1)
    root = NbtReader(raw).read_root()
    if not isinstance(root, dict) or not isinstance(root.get("data"), dict):
        raise smoke.SmokeError(f"Journal root has no data compound: {path}")
    data = root["data"]
    if data.get("schema_version") != 2 or not isinstance(data.get("transactions"), list):
        raise smoke.SmokeError(f"Wrong transaction journal schema: {path}")
    return data


def inspect(world: Path) -> dict:
    data_dir = world / "data"
    journal, staging = data_dir / JOURNAL, data_dir / STAGING
    if not journal.is_file():
        raise smoke.SmokeError("The baseline journal file is missing")
    root = read_root(journal)
    ids = sorted(tuple(entry["transaction_id"]) for entry in root["transactions"])
    if len(set(ids)) != len(ids) or any(entry["dimension"] != "advancedrocketrycommunity:w129a_absent"
                                        for entry in root["transactions"]):
        raise smoke.SmokeError("Duplicated or foreign transaction records on disk")
    result = dict(records=len(ids), ids=[list(item) for item in ids], sha256=digest(journal), staging=staging.is_file())
    if staging.is_file():
        result["staging_records"] = len(read_root(staging)["transactions"])
    return result


def check_first_start(case: str, lines: list[str], code: int | None) -> dict:
    point, mode, ending, records, staging = CASES[case]
    text = "".join(lines)
    drained = [match.groups() for match in DRAINED.finditer(text)]
    incomplete = [match.groups() for match in INCOMPLETE.finditer(text)]
    streaks = [match.groups() for match in STREAK.finditer(text)]
    stopping = "ARCE_RELEASE_TXW_STOPPING_EVENT" in text
    if "ARCE_CHECKED_WRITER_THROWABLE" in text:
        raise smoke.SmokeError("An unexpected writer Throwable was logged")
    if ending == "crash" and ("ARCE_RELEASE_TXW_CRASH" not in text or stopping):
        raise smoke.SmokeError("The exception path either did not crash or raised a stopping event")
    if ending == "stop" and not stopping:
        raise smoke.SmokeError("The normal stop raised no stopping event")
    if case in ("R6", "R7_STALE"):
        if not drained or drained[0][2] != "2" or drained[0][0] != drained[0][1] or incomplete or streaks:
            raise smoke.SmokeError(f"The stale capture did not lead to one covering final capture: {drained} {incomplete}")
    elif case in ("R7_FAILED", "R7_UNKNOWN"):
        expected = "NOT_COMMITTED" if case == "R7_FAILED" else "UNKNOWN"
        if (drained or len(incomplete) != 1 or incomplete[0][2:] != ("3", "BUDGET_EXHAUSTED")
                or [streak[0] for streak in streaks] != [expected]):
            raise smoke.SmokeError(f"Budget exhaustion was not reported exactly once: {incomplete} {streaks} {drained}")
        if text.count("ARCE_RELEASE_TXW_INJECTED") != 3:
            raise smoke.SmokeError("The shutdown budget did not make exactly three attempts")
    elif case == "R7_INTERRUPTED":
        if drained or incomplete or not WATCHDOG.search(text):
            raise smoke.SmokeError("The watchdog-interrupted teardown was reported as a drain, or no watchdog fired")
    elif drained or incomplete:
        raise smoke.SmokeError("A killed server reported a drain")
    return dict(exit_code=code, drained=drained, incomplete=incomplete, streaks=streaks, stopping_event=stopping,
                watchdog=bool(WATCHDOG.search(text)))


class Session:
    def __init__(self, args, session: Path, evidence: Path):
        self.args, self.session, self.evidence = args, session, evidence
        self.process: smoke.CapturedProcess | None = None

    def start(self, case: str, restart: int) -> smoke.CapturedProcess:
        require_lock(self.args.lock_owner)
        free = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
        if min(free.values()) < 10 * 1024**3:
            raise smoke.SmokeError("Native start refused: under 10 GiB free")
        command = _server_command(self.args.java); command[2] = "-Xmx4G"
        self.process = smoke.CapturedProcess(command, self.session, self.evidence / f"{case}-{restart}.log")
        self.process.wait_for(smoke.READY_MARKER, self.args.startup_timeout)
        return self.process

    def send(self, value: str, marker: re.Pattern[str], timeout: float = 60.0) -> str:
        assert self.process is not None
        start = len(self.process.lines)
        self.process.command(PREFIX + value)
        return self.process.lines[self.process.wait_for(marker, timeout, start_at=start)]

    def status(self) -> dict:
        return parse_status(self.send("status", STATUS))

    def await_status(self, b14: str, timeout: float = 60.0) -> dict:
        deadline = time.monotonic() + timeout
        while True:
            status = self.status()
            if status["b14"] == b14:
                return status
            if time.monotonic() > deadline:
                raise smoke.SmokeError(f"B14 never answered {b14}: {status}")
            time.sleep(0.5)

    def end(self, how: str, timeout: float) -> int | None:
        assert self.process is not None
        process, self.process = self.process, None
        if how == "kill":
            process.process.kill(); process.abort(); return None
        if how == "stop":
            process.command("stop")
        return process.finish(timeout)


def first_start(run: Session, case: str) -> dict:
    point, mode, ending, records, staging = CASES[case]
    process = run.start(case, 0)
    run.send("request", re.compile(r"ARCE_RELEASE_TXW_REQUESTED result=REQUESTED"))
    baseline = run.await_status("PROVED")
    if baseline["records"] != 0 or baseline["acknowledged"] != str(baseline["sequence"]):
        raise smoke.SmokeError(f"Wrong baseline proof: {baseline}")
    run.send("add", re.compile(r"ARCE_RELEASE_TXW_MUTATED .* records=1"))
    run.send(f"arm {point} {mode}", re.compile(rf"ARCE_RELEASE_TXW_ARMED point={point} mode={mode}"))
    observed = dict(baseline=baseline)
    if mode in ("PAUSE", "STALL"):
        run.send("request", re.compile(rf"ARCE_RELEASE_TXW_PAUSED point={point} mode={mode}"))
        observed["in_flight"] = run.status()
        if observed["in_flight"]["b14"] != "WRITE_PENDING" or not observed["in_flight"]["busy"]:
            raise smoke.SmokeError(f"A write in flight was not WRITE_PENDING: {observed['in_flight']}")
    if ending == "stop":
        run.send("at-stop", re.compile(r"Transaction writer cut command done"))
    if ending == "crash":
        run.send(f"crash {'true' if mode == 'PAUSE' else 'false'}", re.compile(r"Transaction writer cut command done"))
    code = run.end(ending if ending != "crash" else "wait", run.args.exit_timeout)
    observed.update(check_first_start(case, process.lines, code))
    if ending == "stop" and code != 0:
        raise smoke.SmokeError(f"The normal stop exited {code}")
    return observed


def run_case(run: Session, case: str) -> dict:
    point, mode, ending, records, staging = CASES[case]
    world = run.session / ("world-" + case)
    properties = smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat",
                       "max-tick-time": "10000" if case == "R7_INTERRUPTED" else "60000",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                   encoding="ascii")
    row = dict(case=case, cut=dict(point=point, mode=mode, ending=ending))
    row["first_start"] = first_start(run, case)
    cut_disk = inspect(world)
    if cut_disk["records"] != records or cut_disk["staging"] != staging:
        raise smoke.SmokeError(f"{case}: wrong file after the cut: {cut_disk}")
    row["cut_disk"] = cut_disk; row["restarts"] = []
    for restart in (1, 2):
        process = run.start(case, restart)
        loaded = run.status()
        if (loaded["b14"] != "WRITE_REQUIRED" or loaded["acknowledged"] != "none" or loaded["outcome"] != "UNACKNOWLEDGED"
                or loaded["records"] != records or loaded["busy"] or loaded["dirty"]):
            raise smoke.SmokeError(f"{case}/{restart}: a new session did not start from WRITE_REQUIRED: {loaded}")
        entry = dict(restart=restart, loaded=loaded)
        if restart == 1:
            # A fresh write proves again and replaces any staging file left by the cut.
            run.send("request", re.compile(r"ARCE_RELEASE_TXW_REQUESTED result=REQUESTED"))
            entry["proved"] = run.await_status("PROVED")
        code = run.end("stop", run.args.exit_timeout)
        if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
            raise smoke.SmokeError(f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
        disk = inspect(world)
        if disk["ids"] != cut_disk["ids"] or disk["staging"]:
            raise smoke.SmokeError(f"{case}/{restart}: a restart changed or duplicated the records: {disk}")
        if restart == 2 and disk["sha256"] != row["restarts"][0]["disk"]["sha256"]:
            raise smoke.SmokeError(f"{case}: a clean loaded journal was rewritten at its stop")
        entry.update(exit_code=code, disk=disk); row["restarts"].append(entry)
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
    marker = session / ".arce-w129a-transaction-cuts-owned"
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
            report["cases"].append(run_case(runner, case))
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(f"{case}: PASS (cut, two restarts)", flush=True)
        report.pop("in_progress", None); assert_pin(args.source_commit); report["status"] = "PASS"
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
    args = parser.parse_args()
    try:
        run(args); return 0
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
