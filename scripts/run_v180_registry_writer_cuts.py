#!/usr/bin/env python3
"""W129bc native cuts of the off-thread satellite registry writer (ADR-074, design section 6.4).

Each case starts a fresh flat world, proves a baseline registry file, mutates the registry, cuts the writer, ends the
first start by a kill, a normal stop or a thrown tick exception, and restarts twice. R1 and R2 kill with a capture taken
or staged. R3 and R4 kill after a terminal launch's file moved, or after its commit was published but before the
terminal effect; the package must still be in the terminal and replay as IDEMPOTENT with no duplicate satellite. R5
mutates during the flight, publishes, and kills with the next capture held. R6 stops normally with a stale capture and a
waiting terminal launch before a logout-time mutation. R7 throws out of a tick (running=true, stopped=true, no stopping
event): a stale success then a covering capture, definite-failure exhaustion, UNKNOWN exhaustion, and a stalled write
interrupted by the watchdog, which must not be reported as a drain. DISCOVERY kills between the paid receipt's commit
and the celestial write; the restart's replay discovers once and the receipt is never lost or paid twice.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import uuid

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest
from run_v180_rocket_settlement_completion import require_lock

REGISTRY = "advancedrocketrycommunity_satellite_missions.dat"
STAGING = REGISTRY + ".arce-pending"
CELESTIAL = "advancedrocketrycommunity_celestial.dat"
MARS = "advancedrocketrycommunity:mars"
SHIM = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/persistence/RocketTransactionSavedData.java"
PREFIX = "arce satellite release-test registry-writer "
STATUS = re.compile(r"ARCE_RELEASE_SRW_STATUS sequence=(\d+) acknowledged=(\w+) outcome=(\w+) busy=(true|false) "
                    r"dirty=(true|false) epoch=(-?\d+) satellites=(\d+) missions=(\d+) terminal=(\w+) package=(\w+) "
                    r"result=(\w+) pending=(-?\d+) research=(\w+) discovered=(true|false) balance=(-?\d+)")
DONE = re.compile(r"Registry writer cut command done")
CLAIMED = re.compile(r"ARCE_RELEASE_SRW_DISCOVERY step=claim result=(\w+) sequence=(\d+)")
DRAINED = re.compile(r"ARCE_CHECKED_WRITER_DRAINED root=satellite_registry target=(\d+) committed=(\d+) attempts=(\d+)")
INCOMPLETE = re.compile(r"ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root=satellite_registry target=(\d+) committed=(\d+) "
                        r"attempts=(\d+) reason=(\w+)")
STREAK = re.compile(r"ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=(\w+) sequence=(\d+) epoch=(\d+)")
CRASH = "ARCE_RELEASE_SRW_CRASH"
CRASHED = re.compile(r"ARCE_RELEASE_SRW_CRASH sequence=(\d+) in_flight=(true|false)")
REQUESTED = re.compile(r"ARCE_RELEASE_SRW_REQUESTED result=(true|false) sequence=(\d+)")
INJECTED = "ARCE_RELEASE_SRW_INJECTED"
WATCHDOG = re.compile(r"Considering it to be crashed|A single server tick took")

# case: (fixture, cut point, cut mode, how the first start ends, satellites on disk after it, staging left after it)
CASES = {
    "R1": ("registry", "CAPTURE", "PAUSE", "kill", 0, False),
    "R2": ("registry", "STAGED", "PAUSE", "kill", 0, True),
    "R3": ("terminal", "MOVED", "PAUSE", "kill", 1, False),
    "R4": ("terminal", None, None, "kill", 1, False),
    "R5": ("flight", "STAGED", "PAUSE", "kill", 1, False),
    "R6": ("terminal", "STAGED", "PAUSE", "stop", 2, False),
    "R7_STALE": ("registry", "STAGED", "PAUSE", "crash", 2, False),
    "R7_FAILED": ("registry", "STAGED", "FAIL", "crash", 0, False),
    "R7_UNKNOWN": ("registry", "MOVED", "UNKNOWN", "crash", 2, False),
    "R7_INTERRUPTED": ("registry", "STAGED", "STALL", "crash", 0, True),
    "DISCOVERY": ("discovery", None, None, "kill", 1, False),
}
STARTS_PER_CASE = 3
MAX_STARTS = 60


def parse_status(line: str) -> dict:
    match = STATUS.search(line)
    if match is None:
        raise smoke.SmokeError(f"Not a status line: {line!r}")
    g = match.groups()
    return dict(sequence=int(g[0]), acknowledged=g[1], outcome=g[2], busy=g[3] == "true", dirty=g[4] == "true",
                epoch=int(g[5]), satellites=int(g[6]), missions=int(g[7]), terminal=g[8], package=g[9], result=g[10],
                pending=int(g[11]), research=g[12], discovered=g[13] == "true", balance=int(g[14]))


def uuid_of(value) -> str:
    """An NBT int-array UUID as a canonical string."""
    if not isinstance(value, list) or len(value) != 4:
        raise smoke.SmokeError(f"Not an NBT UUID: {value!r}")
    raw = b"".join(int(part & 0xFFFFFFFF).to_bytes(4, "big") for part in value)
    return str(uuid.UUID(bytes=raw))


def read_data(path: Path, limit: int = 16 * 1024 * 1024) -> dict:
    if path.stat().st_size > limit:
        raise smoke.SmokeError(f"Oversized SavedData fixture: {path}")
    with gzip.open(path, "rb") as stream:
        raw = stream.read(limit * 4 + 1)
    root = NbtReader(raw).read_root()
    if not isinstance(root, dict) or not isinstance(root.get("data"), dict):
        raise smoke.SmokeError(f"SavedData root has no data compound: {path}")
    return root["data"]


def registry_view(data: dict) -> dict:
    satellites, missions = data.get("satellites"), data.get("missions")
    if not isinstance(satellites, list) or not isinstance(missions, list) or not isinstance(data.get("save_epoch"), int):
        raise smoke.SmokeError("Not a current satellite registry root")
    ids = sorted(uuid_of(entry["satellite_id"]) for entry in satellites)
    if len(set(ids)) != len(ids):
        raise smoke.SmokeError("Duplicated satellites on disk")
    epoch = data["save_epoch"]
    stamps = [entry.get("start_epoch", 0) for entry in missions]
    if any(stamp > epoch for stamp in stamps):
        raise smoke.SmokeError(f"A mission's stamp is ahead of the file's durable epoch {epoch}: {stamps}")
    return dict(satellites=len(ids), ids=ids, save_epoch=epoch, missions=len(missions),
                statuses=sorted(str(entry.get("status")) for entry in missions))


def inspect(world: Path) -> dict:
    data_dir = world / "data"
    registry, staging, celestial = data_dir / REGISTRY, data_dir / STAGING, data_dir / CELESTIAL
    if not registry.is_file():
        raise smoke.SmokeError("The baseline registry file is missing")
    result = registry_view(read_data(registry))
    result.update(sha256=digest(registry), staging=staging.is_file())
    if staging.is_file():
        result["staging_satellites"] = registry_view(read_data(staging))["satellites"]
    bodies = []
    if celestial.is_file():
        bodies = sorted(str(body.get("id")) for body in read_data(celestial).get("bodies", []))
    result["mars_discovered"] = MARS in bodies
    return result


def check_first_start(case: str, lines: list[str], code: int | None) -> dict:
    fixture, point, mode, ending, satellites, staging = CASES[case]
    text = "".join(lines)
    drained = [match.groups() for match in DRAINED.finditer(text)]
    incomplete = [match.groups() for match in INCOMPLETE.finditer(text)]
    streaks = [match.groups() for match in STREAK.finditer(text)]
    stopping = "ARCE_RELEASE_SRW_STOPPING_EVENT" in text
    if "ARCE_CHECKED_WRITER_THROWABLE" in text:
        raise smoke.SmokeError("An unexpected writer Throwable was logged")
    if ending == "crash" and (CRASH not in text or stopping):
        raise smoke.SmokeError("The exception path either did not crash or raised a stopping event")
    if ending == "stop" and not stopping:
        raise smoke.SmokeError("The normal stop raised no stopping event")
    if case in ("R6", "R7_STALE"):
        if not drained or drained[0][2] != "2" or drained[0][0] != drained[0][1] or incomplete or streaks:
            raise smoke.SmokeError(f"The stale capture did not lead to one covering final capture: {drained} {incomplete}")
    elif case in ("R7_FAILED", "R7_UNKNOWN"):
        expected = "FAILED" if case == "R7_FAILED" else "UNKNOWN"
        # The coalesced request may already have made an injected attempt. One still in flight at the crash is the
        # stop path's first budgeted attempt, so the attempts injected after the crash complete the budget of three.
        crashed = CRASHED.search(text)
        after_crash = text[text.index(CRASH):].count(INJECTED)
        expected_after = 3 - (1 if crashed is not None and crashed.group(2) == "true" else 0)
        if (drained or len(incomplete) != 1 or incomplete[0][2:] != ("3", "BUDGET_EXHAUSTED")
                or [streak[0] for streak in streaks] != [expected] or after_crash != expected_after):
            raise smoke.SmokeError(f"Budget exhaustion was not reported exactly once with three attempts: "
                                   f"{incomplete} {streaks} {drained} injected_after_crash={after_crash}")
    elif case == "R7_INTERRUPTED":
        if drained or incomplete or not WATCHDOG.search(text):
            raise smoke.SmokeError("The watchdog-interrupted teardown was reported as a drain, or no watchdog fired")
    elif drained or incomplete:
        raise smoke.SmokeError("A killed server reported a drain")
    return dict(exit_code=code, drained=drained, incomplete=incomplete, streaks=streaks, stopping_event=stopping,
                watchdog=bool(WATCHDOG.search(text)))


def assert_pin(source: str, allow_shim: bool) -> str | None:
    """The committed source, optionally with exactly the uncommitted local compile shim; returns the shim's hash."""
    repo = Path(__file__).resolve().parents[1]
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    status = [line for line in subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True).splitlines()
              if line.strip()]
    allowed = [" M " + SHIM] if allow_shim else []
    if head != source or status != allowed:
        raise smoke.SmokeError(f"Native run requires the unchanged committed worktree: {status}")
    if not allow_shim:
        return None
    patch = subprocess.check_output(["git", "diff", "--", SHIM], cwd=repo)
    return hashlib.sha256(patch).hexdigest()


class Session:
    def __init__(self, args, session: Path, evidence: Path):
        self.args, self.session, self.evidence = args, session, evidence
        self.process: smoke.CapturedProcess | None = None
        self.starts = 0

    def start(self, case: str, restart: int) -> smoke.CapturedProcess:
        require_lock(self.args.lock_owner)
        if self.starts >= MAX_STARTS:
            raise smoke.SmokeError(f"Refusing a start past the {MAX_STARTS}-start limit")
        free = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
        if min(free.values()) < 10 * 1024**3:
            raise smoke.SmokeError("Native start refused: under 10 GiB free")
        self.starts += 1
        command = _server_command(self.args.java); command[2] = "-Xmx4G"
        self.process = smoke.CapturedProcess(command, self.session, self.evidence / f"{case}-{restart}.log")
        self.process.wait_for(smoke.READY_MARKER, self.args.startup_timeout)
        return self.process

    def send(self, value: str, marker: re.Pattern[str], timeout: float = 60.0) -> str:
        assert self.process is not None
        start = len(self.process.lines)
        self.process.command(PREFIX + value)
        return self.process.lines[self.process.wait_for(marker, timeout, start_at=start)]

    def vanilla(self, value: str, marker: re.Pattern[str], timeout: float = 120.0) -> str:
        assert self.process is not None
        start = len(self.process.lines)
        self.process.command(value)
        return self.process.lines[self.process.wait_for(marker, timeout, start_at=start)]

    def status(self) -> dict:
        return parse_status(self.send("status", STATUS))

    def await_status(self, predicate, what: str, timeout: float = 60.0) -> dict:
        deadline = time.monotonic() + timeout
        while True:
            status = self.status()
            if predicate(status):
                return status
            if time.monotonic() > deadline:
                raise smoke.SmokeError(f"Never reached {what}: {status}")
            time.sleep(0.5)

    def request(self) -> int:
        """A plain request; returns the mutation sequence it must cover."""
        match = REQUESTED.search(self.send("request", REQUESTED))
        if match.group(1) != "true":
            raise smoke.SmokeError("The registry refused a plain request")
        return int(match.group(2))

    def proved(self, sequence: int) -> dict:
        # The scheduler's clock may move the sequence on after the commit; coverage of the request is what counts.
        return self.await_status(lambda s: s["outcome"] == "COMMITTED" and s["acknowledged"] != "none"
                                 and int(s["acknowledged"]) >= sequence and not s["busy"],
                                 f"a published commit covering sequence {sequence}")

    def mark(self) -> int:
        assert self.process is not None
        return len(self.process.lines)

    def wait(self, marker: re.Pattern[str], start: int, timeout: float = 60.0) -> str:
        assert self.process is not None
        return self.process.lines[self.process.wait_for(marker, timeout, start_at=start)]

    def end(self, how: str, timeout: float) -> int | None:
        assert self.process is not None
        process, self.process = self.process, None
        if how == "kill":
            process.process.kill(); process.abort(); return None
        if how == "stop":
            process.command("stop")
        return process.finish(timeout)


def first_start(run: Session, case: str) -> dict:
    fixture, point, mode, ending, satellites, staging = CASES[case]
    process = run.start(case, 0)
    baseline = run.proved(run.request())
    if baseline["satellites"] != 0:
        raise smoke.SmokeError(f"Wrong baseline: {baseline}")
    observed = dict(baseline=baseline)
    if fixture == "terminal":
        run.send("terminal-place", re.compile(r"ARCE_RELEASE_SRW_TERMINAL placed .*package=bound"))
        # The chunk with the bound package is on disk before the launch, as after any earlier autosave.
        run.vanilla("save-all flush", re.compile(r"Saved the game"))
    if fixture == "discovery":
        run.send("discovery-launch", re.compile(r"ARCE_RELEASE_SRW_DISCOVERY step=launch result=SUCCESS"))
        run.send("discovery-ready", re.compile(r"ARCE_RELEASE_SRW_DISCOVERY step=ready .*research=READY"))
        run.send("hold-discovery true", DONE)
        claim = CLAIMED.search(run.send("discovery-claim", CLAIMED))
        if claim.group(1) != "PENDING_DISCOVERY":
            raise smoke.SmokeError(f"The paid claim did not wait for its receipt's commit: {claim.group(0)}")
        receipt = int(claim.group(2))
        observed["receipt"] = run.await_status(lambda s: s["research"] == "CLAIM_PENDING_DISCOVERY" and s["balance"] > 0
                                               and s["outcome"] == "COMMITTED" and s["acknowledged"] != "none"
                                               and int(s["acknowledged"]) >= receipt and not s["discovered"],
                                               "the paid receipt's published commit without its discovery")
        observed.update(check_first_start(case, process.lines, run.end("kill", run.args.exit_timeout)))
        return observed
    armed = run.mark()
    if point is not None:
        run.send(f"arm {point} {mode}", re.compile(rf"ARCE_RELEASE_SRW_ARMED point={point} mode={mode}"))
    if fixture == "terminal":
        if point is None:
            run.send("hold-effects true", DONE)
        line = run.send("terminal-launch", re.compile(r"ARCE_RELEASE_SRW_TERMINAL handled="))
        if "handled=true" not in line or "pending=1" not in line or "package=bound" not in line:
            raise smoke.SmokeError(f"The terminal launch did not wait with its package: {line}")
        if point is None:
            observed["published"] = run.await_status(lambda s: s["outcome"] == "COMMITTED" and s["satellites"] == 1
                                                     and s["pending"] == 1 and s["package"] == "bound",
                                                     "a published registration with its terminal effect held")
    else:
        run.send("add", re.compile(r"ARCE_RELEASE_SRW_MUTATED result=SUCCESS .* satellites=1"))
    if mode in ("PAUSE", "STALL"):
        if fixture != "terminal":
            run.send("request", REQUESTED)  # the coalesced request may already have started the attempt
        run.wait(re.compile(rf"ARCE_RELEASE_SRW_PAUSED point={point} mode={mode}"), armed)
        observed["in_flight"] = run.status()
        if not observed["in_flight"]["busy"]:
            raise smoke.SmokeError(f"A write in flight was not busy: {observed['in_flight']}")
    if fixture == "flight":
        # R5: a mutation during the flight, then the commit, with the next capture held before it encodes.
        run.send("add", re.compile(r"ARCE_RELEASE_SRW_MUTATED result=SUCCESS .* satellites=2"))
        held = run.mark()
        run.send("arm CAPTURE PAUSE", re.compile(r"ARCE_RELEASE_SRW_ARMED point=CAPTURE mode=PAUSE"))
        run.send("release", DONE)
        observed["published"] = run.await_status(lambda s: s["outcome"] == "COMMITTED" and s["acknowledged"] != "none"
                                                 and int(s["acknowledged"]) < s["sequence"] and s["dirty"],
                                                 "the older commit with the in-flight mutation still dirty")
        run.send("request", REQUESTED)  # the coalesced request may already have started the held capture
        run.wait(re.compile(r"ARCE_RELEASE_SRW_PAUSED point=CAPTURE mode=PAUSE"), held)
    if ending == "stop":
        run.send("at-stop", DONE)
    if ending == "crash":
        run.send(f"crash {'true' if mode == 'PAUSE' else 'false'}", DONE)
    code = run.end(ending if ending != "crash" else "wait", run.args.exit_timeout)
    observed.update(check_first_start(case, process.lines, code))
    if ending == "stop" and code != 0:
        raise smoke.SmokeError(f"The normal stop exited {code}")
    return observed


def check_restart(case: str, restart: int, loaded: dict, cut_disk: dict) -> None:
    fixture, point, mode, ending, satellites, staging = CASES[case]
    if loaded["acknowledged"] == "none" or loaded["satellites"] != satellites:
        raise smoke.SmokeError(f"{case}/{restart}: the new session did not load the file as acknowledged: {loaded}")
    if fixture == "terminal" and loaded["terminal"] != "present":
        raise smoke.SmokeError(f"{case}/{restart}: the terminal is missing: {loaded}")
    if fixture == "terminal" and restart == 1 and loaded["package"] != "bound":
        raise smoke.SmokeError(f"{case}/1: the package left without a published acknowledgement: {loaded}")
    if fixture == "terminal" and restart == 2 and loaded["package"] != "empty":
        raise smoke.SmokeError(f"{case}/2: the replayed package came back: {loaded}")
    if fixture == "discovery" and restart == 2 and not (loaded["discovered"] and loaded["research"] == "CLAIMED"):
        raise smoke.SmokeError(f"{case}/2: the replayed discovery did not stay: {loaded}")


def run_case(run: Session, case: str) -> dict:
    fixture, point, mode, ending, satellites, staging = CASES[case]
    world = run.session / ("world-" + case)
    properties = smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat",
                       "max-tick-time": "10000" if case == "R7_INTERRUPTED" else "60000",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                   encoding="ascii")
    row = dict(case=case, cut=dict(fixture=fixture, point=point, mode=mode, ending=ending))
    row["first_start"] = first_start(run, case)
    cut_disk = inspect(world)
    if cut_disk["satellites"] != satellites or cut_disk["staging"] != staging or cut_disk["mars_discovered"]:
        raise smoke.SmokeError(f"{case}: wrong files after the cut: {cut_disk}")
    if fixture == "discovery" and "claim_pending_discovery" not in cut_disk["statuses"]:
        raise smoke.SmokeError(f"{case}: the paid receipt is not in the registry file: {cut_disk}")
    row["cut_disk"] = cut_disk; row["restarts"] = []
    balance = row["first_start"].get("receipt", {}).get("balance")
    for restart in (1, 2):
        process = run.start(case, restart)
        if fixture == "discovery" and restart == 1:
            loaded = run.await_status(lambda s: s["discovered"] and s["research"] == "CLAIMED",
                                      "the replayed discovery of the committed receipt")
        else:
            loaded = run.status()
        check_restart(case, restart, loaded, cut_disk)
        if fixture == "discovery" and loaded["balance"] != balance:
            raise smoke.SmokeError(f"{case}/{restart}: the paid receipt was lost or paid again: {loaded} {balance}")
        entry = dict(restart=restart, loaded=loaded)
        if restart == 1:
            if fixture == "terminal":
                line = run.send("terminal-launch", re.compile(r"ARCE_RELEASE_SRW_TERMINAL handled="))
                if "handled=true" not in line:
                    raise smoke.SmokeError(f"{case}/1: the replay press was refused: {line}")
                entry["replay"] = line.strip()
                # The package leaves at once when the loaded file already proves it, else after the next commit.
                entry["replayed"] = run.await_status(lambda s: s["package"] == "empty" and s["result"] == "IDEMPOTENT"
                                                     and s["pending"] == 0, "the package's IDEMPOTENT replay")
                if entry["replayed"]["satellites"] != satellites:
                    raise smoke.SmokeError(f"{case}/1: the replay duplicated a satellite: {entry['replayed']}")
            # A fresh write proves again and replaces any staging file left by the cut.
            entry["proved"] = run.proved(run.request())
            if entry["proved"]["satellites"] != satellites:
                raise smoke.SmokeError(f"{case}/1: a replay duplicated or lost a satellite: {entry['proved']}")
        code = run.end("stop", run.args.exit_timeout)
        if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
            raise smoke.SmokeError(f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
        disk = inspect(world)
        if disk["ids"] != cut_disk["ids"] or disk["staging"]:
            raise smoke.SmokeError(f"{case}/{restart}: a restart changed or duplicated the satellites: {disk}")
        if fixture == "discovery" and not disk["mars_discovered"]:
            raise smoke.SmokeError(f"{case}/{restart}: the discovery is not in the celestial file: {disk}")
        entry.update(exit_code=code, disk=disk); row["restarts"].append(entry)
    row["status"] = "PASS"
    return row


def run(args) -> dict:
    shim = assert_pin(args.source_commit, args.allow_shim)
    cases = args.case or list(CASES)
    if len(cases) * STARTS_PER_CASE > MAX_STARTS:
        raise smoke.SmokeError(f"{len(cases)} cases need more than {MAX_STARTS} counted starts")
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
    marker = session / ".arce-w129bc-registry-cuts-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, compile_shim_diff_sha256=shim,
                  jar_sha256=digest(args.jar), cases=[], lock_owner=args.lock_owner)
    runner = Session(args, session, evidence)
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        runner.port = smoke.allocate_port()
        smoke.write_server_configuration(session, runner.port, True)
        for case in cases:
            report["in_progress"] = case
            report["cases"].append(run_case(runner, case))
            report["starts"] = runner.starts
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(f"{case}: PASS (cut, two restarts)", flush=True)
        report.pop("in_progress", None); assert_pin(args.source_commit, args.allow_shim); report["status"] = "PASS"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        report["starts"] = runner.starts
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
    parser.add_argument("--allow-shim", action="store_true",
                        help="Allow exactly the uncommitted local compile shim; its diff hash is recorded.")
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
