#!/usr/bin/env python3
"""W332 native cases for R-042 (records/W332-DESIGN.md): NOT_LOADED and the held pending pool.

Each case starts a fresh flat world on the pinned candidate JAR, makes a first start (S0) and two restarts (S1, S2), and
checks each start's logs, console answers and stopped-world bytes:

* ``N_BAY``: the gated load-failure seam keeps the bay receipts NOT_LOADED for S1, with the audit's server-wide effects,
  and S2 recovers;
* ``L_LEDGER``: the same for the escrow ledger and W329's protective answers;
* ``U_BAY`` and ``U_LEDGER``: a reversible Windows delete-pending handle reaches UNKNOWN presence; startup refuses
  preserving world data except vanilla's monotonic LastPlayed timestamp and metadata rotation (R416), then two clean
  starts recover after the handle is released;
* ``P_SERVER`` and ``P_OWNER``: released and REPAIR receipts hold the shared pending pool over both restarts; fuel, the
  four cargo kinds and a bay deployment are refused with nothing changed, another owner is admitted, and no online
  command releases them.

Production admission stays off: every fixture is a gated release-test command, start property or harness-only OS handle.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import threading
import time
import uuid

import bay_integrated_evidence as evidence_tools
import loader_cut_nbt as nbt
import run_dedicated_server_smoke as smoke
import run_v180_bay_forced_stop as bay
import r042_windows_presence as presence
import run_w157_rejoin_startup as refusal_log
from run_v180_escrow_forced_stop import digest
from run_v180_escrow_pre_f2 import assert_pin

OWNER = "00000000-0000-4000-8000-000000128d01"  # BayReleaseTestCommands.OWNER: the bay fixture and plain rocket owner
P1 = "00000000-0000-4000-8000-000000332001"
P2 = "00000000-0000-4000-8000-000000332002"
SEED_OWNERS = tuple(f"00000000-0000-4000-8000-0000003320{n}" for n in (10, 11, 12, 13))
FILES = dict(receipts="advancedrocketrycommunity_bay_receipts.dat", ledger="advancedrocketrycommunity_resource_escrow.dat",
             transfers="advancedrocketrycommunity_rocket_transfers.dat",
             transactions="advancedrocketrycommunity_rocket_transactions.dat",
             registry="advancedrocketrycommunity_satellite_missions.dat")
PLAIN_AT = (24, 2, 8)
INSERT_AT = (40, 2, 8)
FORCELOAD = "forceload add 0 0 111 31"
KINDS = ("FUEL", "FLUID_LOAD", "FLUID_UNLOAD", "ITEM_LOAD", "ITEM_UNLOAD")
# (rocket, device) amounts of a cell before and after exactly one transfer (W330's and W95's fixture shapes).
BEFORE = dict(FUEL=(0, 1000), FLUID_LOAD=(0, 5000), FLUID_UNLOAD=(5000, 0), ITEM_LOAD=(60, 10), ITEM_UNLOAD=(10, 60))
AFTER = dict(FUEL=(1000, 0), FLUID_LOAD=(1000, 4000), FLUID_UNLOAD=(4000, 1000), ITEM_LOAD=(64, 6), ITEM_UNLOAD=(6, 64))
ADMITTED = ("READY", "PREPARING", "TRANSFERRING")

REPORTED = re.compile(r"R-042 probe reported")
WAIT = re.compile(r"ARCE_RELEASE_R042_WAIT ")
CELL = re.compile(r"ARCE_RELEASE_R042_CELL cell=(\w+) kind=(\w+) owner=(\S+) logical=(\S+) rocket=(\S+) device=(\S+)")
ATTEMPT = re.compile(r"ARCE_RELEASE_R042_ATTEMPT cell=(\w+) kind=(\w+) found=(true|false)(?: status=(\w+) admitted=(true|false) "
                     r"unchanged=(true|false) pending=(\d+)>(\d+) retained=(\d+)>(\d+) ledger=(\w+)>(\w+) logical=(\S+))?")
REPORT = re.compile(r"ARCE_RELEASE_R042_REPORT cell=(\w+) kind=(\w+) found=(true|false)(?: rocket=(-?\d+) device=(-?\d+) "
                    r"rocketEpoch=(\d+) deviceEpoch=(\d+) rocketMarkers=(\d+) deviceMarkers=(\d+) locked=(true|false) "
                    r"protected=(true|false) refusal=(\w+) watching=(true|false) logical=(\S+))?")
PLAIN = re.compile(r"ARCE_RELEASE_R042_PLAIN logical=(\S+) rocket=(\S+) owner=(\S+) fuel=(\d+)")
INSERTED = re.compile(r"R-042 insertion reported|ARCE_RELEASE_R042_WAIT ")
INSERT = re.compile(r"ARCE_RELEASE_R042_INSERT satellite=(\S+) blocked=(true|false) valid=(true|false) remainder=(\d+) held=(true|false)")
RIDER = re.compile(r"ARCE_RELEASE_R042_RIDER action=(\w+) logical=(\S+) rider=(\w+) mounted=(true|false) vehicle=(\S+) seat=(-?\d+) rocket=(\S+)")
LAUNCH = re.compile(r"ARCE_RELEASE_TEST_LAUNCH request=(\S+) entity=(\S+) logical=(\S+) source=\S+ destination=\S+ checkpoint=(\S+) code=(\w+)")
PAUSED = re.compile(r"ARCE_RELEASE_TEST_FLIGHT_PAUSED checkpoint=(\w+) transfer=(\S+) phase=(\w+) state=(\w+)")
SATELLITE = re.compile(r"ARCE_RELEASE_TEST_SATELLITE_LAUNCH satellite=(\S+) mission=\S+ owner=\S+ target=\S+ code=(\w+)"
                       r"|Release-test satellite launch rejected: (\w+)")
RETIRE = re.compile(r"message\.advancedrocketrycommunity\.bay_receipt\.(refused\.\w+|requested|retired|failed)")
ESCROW_STATUS = re.compile(r"escrow admission=\S+ (?:ledger=(\S+)|operations=)")
GAMETIME = re.compile(r"The time is (\d+)")
INJECTED = re.compile(r"ARCE_RELEASE_LOAD_FAILURE type=(\w+) injected=true")
INJECTION_REFUSED = re.compile(r"ARCE_RELEASE_LOAD_FAILURE refused")
VANILLA_LOAD_ERROR = re.compile(r"/ERROR\].*Error loading saved data: (\S+)")
BAY_NOT_LOADED = re.compile(r"ARCE_BAY_RECEIPTS_NOT_LOADED reason=(\w+) file=(\S+);")
BAY_INDEX = re.compile(r"ARCE_BAY_RECEIPTS_INDEX state=(\w+) receipts=(\d+)")
LEDGER_NOT_LOADED = re.compile(r"ARCE_ESCROW_LEDGER_NOT_LOADED reason=(\w+) file=(\S+);")
FENCED = re.compile(r"ARCE_ROCKET_RECOVERY_FENCED transaction=(\S+) ")
GATE_CLOSED = re.compile(r"ARCE_RELEASE_R042_GATE_CLOSED cell=(\w+) kind=(\w+) reason=(\w+)")
# W325's status line for a rocket without bays: its packages and consumed fields are empty (W332 smoke 1).
PLAIN_STATUS = re.compile(r"ARCE_RELEASE_BAY_STATUS logical=(\S+) claim=(\w+) hold=(\w+) reason=(\w+) receipts=\[([^\]]*)\] "
                          r"transfer=(\w+) rocket=(\S+) state=(\w+) y=(\S+) packages=(\S*) locked=(true|false) consumed=(\S*) owned=(-?\d+)")

# One family per case; rows name the TASK items (records/W332-DESIGN.md section 3).
CASES = {
    "U_BAY": dict(family="U", file="receipts", type="BAY_RECEIPTS", rows=("TASK 2: unknown bay presence",)),
    "U_LEDGER": dict(family="U", file="ledger", type="RESOURCE_ESCROW", rows=("TASK 2: unknown ledger presence",)),
    "N_BAY": dict(family="N", rows=("TASK 2: present but not loaded",)),
    "L_LEDGER": dict(family="L", rows=("TASK 3: the escrow ledger NOT_LOADED",)),
    "P_SERVER": dict(family="P", pending=64, refusal="ESCROW_FULL", bay_reason="DEPLOYMENT_CAPACITY", rows=("TASK 4: the server pool",)),
    "P_OWNER": dict(family="P", pending=16, refusal="OWNER_PENDING_FULL", bay_reason="OWNER_PENDING_FULL", other=True, rows=("TASK 4: per owner",)),
}
CASES.update(
    X_BAY=dict(family="N", presence_unknown=True, rows=("post-prestart UNKNOWN bay presence",)),
    X_LEDGER=dict(family="L", presence_unknown=True, rows=("post-prestart UNKNOWN ledger presence",)),
    R031_FUTURE=dict(family="R", rows=("future ledger root refuses world start",)),
    R031_INVALID=dict(family="R", rows=("invalid ledger root refuses world start",)))
SCRATCH = {"U": "U_BAY", "N": "N_BAY", "L": "L_LEDGER", "P": "P_SERVER"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise smoke.SmokeError(message)


# Seeds -----------------------------------------------------------------------------------------------------------------

def pool_rows(case: str) -> list[dict]:
    """The typed receipt rows of a P case: eight receipts per rocket, every row within the 64, 16 and 8 caps."""
    if case == "P_SERVER":
        kinds = [("REPAIR", "REGISTRY_SIGNATURE_CONFLICT", True)] * 2 + [("REJECTED", "INTERRUPTED", True)] * 2 \
            + [("REPAIR", "TRANSFER_AUTHORITY_CONFLICT", False)] * 4
        owners = [SEED_OWNERS[rocket // 2] for rocket in range(8)]
    elif case == "P_OWNER":
        kinds = [("REPAIR", "REGISTRY_SIGNATURE_CONFLICT", True), ("REPAIR", "TRANSFER_AUTHORITY_CONFLICT", False)]
        owners = [OWNER, OWNER]
    else:
        raise ValueError(f"No pool seed for {case}")
    rows = []
    for rocket, ((phase, reason, released), owner) in enumerate(zip(kinds, owners)):
        rows += [dict(rocket=rocket, bay=n, phase=phase, reason=reason, released=released, owner=owner) for n in range(8)]
    check_pool(rows)
    return rows


def check_pool(rows: list[dict]) -> None:
    """Every seeded row is pending (blocking or released) and the rows respect the real caps (64, 16 per owner, 8 per rocket)."""
    require(all(row["phase"] != "REJECTED" or row["released"] for row in rows), "A seeded row is not pending")
    require(len(rows) <= 64, "The seed exceeds 64 pending receipts")
    for key, cap in (("owner", 16), ("rocket", 8)):
        counts: dict = {}
        for row in rows:
            counts[row[key]] = counts.get(row[key], 0) + 1
        require(max(counts.values()) <= cap, f"The seed exceeds {cap} pending receipts per {key}")


def seed_pool(world: Path, rows: list[dict]) -> dict:
    """Write a schema-1 receipt file with these rows, in W325's typed seed format (bay_integrated_evidence)."""
    file = world / "data" / FILES["receipts"]
    if file.exists():
        raise FileExistsError(file)
    namespace = uuid.uuid4()
    hashed = hashlib.sha256(b"W332 held pending pool fixture").digest()

    def identity(kind: str, index: int):
        return nbt.uuid_tag(uuid.uuid5(namespace, f"{kind}-{index}"))

    def string(value: str):
        return nbt.Tag(nbt.STRING, value.encode("ascii"))
    receipts, satellites = [], []
    for index, row in enumerate(rows):
        satellites.append(str(uuid.uuid5(namespace, f"satellite-{index}")))
        receipts.append(nbt.Tag(nbt.COMPOUND, dict(
            schema_version=nbt.Tag(nbt.INT, 1), op=identity("op", index), seq=nbt.Tag(nbt.LONG, index + 1),
            rocket=identity("rocket", row["rocket"]), bay=nbt.Tag(nbt.INTS, [0, row["bay"], 0]),
            satellite=identity("satellite", index), owner=nbt.uuid_tag(uuid.UUID(row["owner"])),
            definition=string("advancedrocketrycommunity:data_satellite"), kind=string("data"),
            orbit=string("advancedrocketrycommunity:earth"), package_sig=nbt.Tag(nbt.BYTES, hashed),
            transfer=identity("transfer", row["rocket"]), src_before=nbt.Tag(nbt.BYTES, hashed),
            dst_before=nbt.Tag(nbt.BYTES, hashed), phase=string(row["phase"]), reason=string(row["reason"]),
            preexisting=nbt.Tag(nbt.BYTE, 0), released=nbt.Tag(nbt.BYTE, int(row["released"])),
            registry_epoch=nbt.Tag(nbt.LONG, 0), created_tick=nbt.Tag(nbt.LONG, 0), updated_tick=nbt.Tag(nbt.LONG, 0),
            attempts=nbt.Tag(nbt.INT, 0))))
    payload = nbt.Tag(nbt.COMPOUND, dict(schema_version=nbt.Tag(nbt.INT, 1),
        format_epoch=string("v1.8.0-bay-receipts"), store_id=nbt.uuid_tag(namespace),
        issued_through=nbt.Tag(nbt.LONG, len(rows)), writer_generation=nbt.Tag(nbt.LONG, 1),
        receipts=nbt.Tag(nbt.LIST, (nbt.COMPOUND, receipts))))
    root = nbt.Tag(nbt.COMPOUND, dict(data=payload, DataVersion=nbt.Tag(nbt.INT, 3465)))
    file.parent.mkdir(parents=True, exist_ok=True)
    nbt.write_gzip(file, b"", root)
    require(nbt.read_gzip(file)[1] == root, "The pool seed failed typed readback")
    return dict(count=len(rows), satellites=satellites, rows=rows, sha256=digest(file))


# The harness session ---------------------------------------------------------------------------------------------------

class Run(bay.Session):
    """W325's session with the scratch property, error audit and the R-042 console helpers."""

    def start(self, case: str, restart: int, properties: dict[str, str]) -> smoke.CapturedProcess:
        if self.args.scratch:
            properties = dict(properties, w332Scratch=self.args.scratch)
        if CASES[case].get("presence_unknown") and restart == 1:
            properties = dict(properties, releaseTestPresenceFailure=properties["releaseTestLoadFailure"])
            self.presence_release = threading.Event()
            self.presence_errors = []
            log = self.evidence / f"{case}-{restart}.log"
            file = self.session / ("world-" + case) / "data" / FILES["receipts" if case == "X_BAY" else "ledger"]

            def hold():
                try:
                    deadline = time.monotonic() + self.args.startup_timeout
                    while not self.presence_release.wait(0.01):
                        if log.is_file() and "ARCE_RELEASE_PRESENCE_WAIT type=" in log.read_text(encoding="utf-8", errors="replace"):
                            with presence.delete_pending(file):
                                require(self.presence_release.wait(self.args.startup_timeout + self.args.exit_timeout + 300),
                                        "UNKNOWN handle lifetime exceeded its bound")
                            return
                        require(time.monotonic() < deadline, "No post-prestart presence rendezvous")
                except Exception as failure:
                    self.presence_errors.append(str(failure))
            self.presence_thread = threading.Thread(target=hold, name="w360-presence", daemon=True)
            self.presence_thread.start()
        try:
            return super().start(case, restart, properties)
        except Exception:
            self.release_presence()
            raise

    def release_presence(self):
        if getattr(self, "presence_thread", None) is not None:
            self.presence_release.set()
            self.presence_thread.join(10)
            require(not self.presence_thread.is_alive(), "Presence handle thread did not finish")
            self.presence_thread = None
            require(not self.presence_errors, "Presence handle failed: " + "; ".join(self.presence_errors))

    def probe_unknown(self, world: Path, filename: str, log: Path) -> str:
        evidence_tools.recheck_installed(self.candidate)
        text = presence.probe(self.args.java, self.candidate, self.session, world, filename, log)
        evidence_tools.recheck_installed(self.candidate)
        return text

    def start_refused(self, case: str) -> smoke.CapturedProcess:
        bay.require_lock(self.args.lock_owner)
        require(min(shutil.disk_usage(drive).free for drive in ("C:/", "D:/")) >= 10 * 1024**3,
                "Native refusal start: under 10 GiB free")
        command = bay._server_command(self.args.java)
        command[2] = "-Xmx4G"
        if self.args.scratch:
            command.insert(4, f"-Dadvancedrocketrycommunity.w332Scratch={self.args.scratch}")
        evidence_tools.recheck_installed(self.candidate)
        self.starts.append(dict(case=case, restart=1, installed_jar=self.candidate["installed_jar"],
                                installed_jar_sha256=self.candidate["installed_jar_sha256"], command=command))
        (self.evidence / "installed-starts.json").write_text(json.dumps(self.starts, indent=2), encoding="utf-8")
        self.process = smoke.CapturedProcess(command, self.session, self.evidence / f"{case}-1.log")
        return self.process

    def window(self, command: str, marker: re.Pattern[str] = REPORTED, timeout: float = 60.0) -> list[str]:
        assert self.process is not None
        start = len(self.process.lines)
        self.process.command(command)
        end = self.process.wait_for(marker, timeout, start_at=start)
        return self.process.lines[start:end + 1]

    def probe(self, command: str, timeout: float = 60.0) -> list[str]:
        """A probe command, repeated while its positions are not yet entity-ticking."""
        deadline = time.monotonic() + timeout
        while True:
            lines = self.window(command)
            if not any(WAIT.search(line) for line in lines):
                return lines
            require(time.monotonic() < deadline, f"The probe positions never became entity-ticking: {command}")
            time.sleep(1.0)

    def forceload(self) -> None:
        self.send(FORCELOAD, re.compile(r"[Mm]arked|already|forceload|chunk"))

    def plain_status(self, logical: str) -> dict:
        match = PLAIN_STATUS.search(self.send(f"arce bay release-test status {logical}", PLAIN_STATUS))
        keys = ("logical", "claim", "hold", "reason", "receipts", "transfer", "rocket", "state", "y", "packages", "locked",
                "consumed", "owned")
        return dict(zip(keys, match.groups()))

    def gametime(self) -> int:
        return int(GAMETIME.search(self.send("time query gametime", GAMETIME)).group(1))

    def cell(self, name: str, owner: str) -> dict:
        lines = self.probe(f"arce bay release-test r042 cell {name} {owner}")
        rows = {match[2]: dict(owner=match[3], logical=match[4], rocket=match[5], device=match[6])
                for line in lines for match in [CELL.search(line)] if match}
        require(sorted(rows) == sorted(KINDS) and all(row["owner"] == owner for row in rows.values()),
                f"Cell {name} was not built with five kinds of {owner}: {rows}")
        return rows

    def attempt(self, name: str, kinds: str = "ALL") -> dict:
        lines = self.probe(f"arce bay release-test r042 attempt {name} {kinds}")
        rows = {}
        for line in lines:
            match = ATTEMPT.search(line)
            if match:
                require(match[3] == "true", f"Cell {name} lost its {match[2]} fixture: {line.strip()}")
                rows[match[2]] = dict(status=match[4], admitted=match[5] == "true", unchanged=match[6] == "true",
                                      pending=(int(match[7]), int(match[8])), retained=(int(match[9]), int(match[10])),
                                      ledger=(match[11], match[12]), logical=match[13])
        require(sorted(rows) == sorted(KINDS if kinds == "ALL" else kinds.split(",")), f"Missing attempt lines: {rows}")
        return rows

    def report(self, name: str) -> dict:
        rows = {}
        for line in self.probe(f"arce bay release-test r042 report {name}"):
            match = REPORT.search(line)
            if match:
                require(match[3] == "true", f"Cell {name} lost its {match[2]} fixture: {line.strip()}")
                rows[match[2]] = dict(rocket=int(match[4]), device=int(match[5]), rocket_epoch=int(match[6]),
                                      device_epoch=int(match[7]), rocket_markers=int(match[8]), device_markers=int(match[9]),
                                      locked=match[10] == "true", protected=match[11] == "true", refusal=match[12],
                                      watching=match[13] == "true", logical=match[14])
        require(sorted(rows) == sorted(KINDS), f"Missing report lines: {rows}")
        return rows

    def await_completed(self, name: str, kinds=KINDS) -> dict:
        deadline = time.monotonic() + self.args.settle_timeout
        while True:
            rows = self.report(name)
            if all(completed(rows[kind], kind) for kind in kinds):
                return rows
            require(time.monotonic() < deadline, f"Cell {name} never completed {kinds}: {rows}")
            time.sleep(1.0)

    def plain(self) -> dict:
        deadline = time.monotonic() + 60
        while True:
            lines = self.window(f"arce bay release-test r042 plain {PLAIN_AT[0]} {PLAIN_AT[1]} {PLAIN_AT[2]}",
                                re.compile(r"Plain R-042 rocket added|ARCE_RELEASE_R042_WAIT"))
            match = next((PLAIN.search(line) for line in lines if PLAIN.search(line)), None)
            if match:
                return dict(logical=match[1], rocket=match[2], fuel=int(match[4]))
            require(time.monotonic() < deadline, "The plain rocket's origin never became entity-ticking")
            time.sleep(1.0)

    def launch(self, rocket: str, checkpoint: str) -> dict:
        match = LAUNCH.search(self.send(f"arce rocket release-test launch {rocket} moon {checkpoint}", LAUNCH))
        return dict(entity=match[2], logical=match[3], checkpoint=match[4], code=match[5])

    def satellite(self) -> dict:
        match = SATELLITE.search(self.send(f"arce satellite release-test launch {OWNER} earth", SATELLITE))
        return dict(satellite=match[1], code=match[2]) if match[2] else dict(satellite=None, code=match[3])

    def decommission(self, satellite: str) -> dict:
        match = bay.DECOMMISSIONED.search(self.send(f"arce bay release-test decommission {satellite}", bay.DECOMMISSIONED))
        return dict(result=match[2], present=match[3] == "true")

    def insert(self) -> dict:
        """The insertion probe answers with its own feedback line (W332 smoke 2), or a WAIT line before its chunk ticks."""
        deadline = time.monotonic() + 60
        while True:
            lines = self.window(f"arce bay release-test r042 insert {INSERT_AT[0]} {INSERT_AT[1]} {INSERT_AT[2]}", INSERTED)
            match = next((INSERT.search(line) for line in lines if INSERT.search(line)), None)
            if match:
                break
            require(time.monotonic() < deadline, "The insertion probe's position never became entity-ticking")
            time.sleep(1.0)
        return dict(satellite=match[1], blocked=match[2] == "true", valid=match[3] == "true", remainder=int(match[4]),
                    held=match[5] == "true")

    def retire(self, satellite: str) -> str:
        return RETIRE.search(self.send(f"arce bay receipt retire-rejected {satellite}", RETIRE)).group(1)

    def escrow_status(self) -> str:
        """``not-loaded`` or ``absent`` as the command prints them; ``present`` for a loaded ledger's operations line."""
        return ESCROW_STATUS.search(self.send("arce escrow status", ESCROW_STATUS)).group(1) or "present"

    def rider(self, action: str, logical: str) -> dict:
        match = RIDER.search(self.send(f"arce bay release-test r042 rider {action} {logical}", RIDER))
        return dict(rider=match[3], mounted=match[4] == "true", vehicle=match[5], seat=int(match[6]))

    def save(self) -> None:
        self.send("save-all flush", bay.SAVED, 120)

    def stop(self, case: str, restart: int, allowed: dict | None = None) -> dict:
        assert self.process is not None
        process = self.process
        try:
            code = self.end("stop", self.args.exit_timeout)
        finally:
            self.release_presence()
        require(code == 0, f"{case}/{restart}: the normal stop exited {code}")
        errors = audit_errors(process.lines, allowed or {})
        require(not errors, f"{case}/{restart}: unexpected ERROR/FATAL: {errors}")
        return dict(exit_code=code, lines=process.lines)


def completed(row: dict, kind: str) -> bool:
    """Exactly one transfer of this kind settled: the after-amounts, both epochs advanced once, no marker, switch closed."""
    return ((row["rocket"], row["device"]) == AFTER[kind] and row["rocket_epoch"] == row["device_epoch"] == 1
            and row["rocket_markers"] == row["device_markers"] == 0 and not row["watching"])


def untouched(row: dict, kind: str) -> bool:
    return ((row["rocket"], row["device"]) == BEFORE[kind] and row["rocket_epoch"] == row["device_epoch"] == 0
            and row["rocket_markers"] == row["device_markers"] == 0)


def check_refused(attempts: dict, status: str | None, ledger: str | None = None) -> dict:
    """Every kind refused with nothing changed; with ``status``, every kind answered it."""
    for kind, row in attempts.items():
        require(not row["admitted"] and row["unchanged"] and row["status"] not in ADMITTED,
                f"{kind} was admitted or changed something: {row}")
        require(status is None or row["status"] == status, f"{kind} answered {row['status']}, expected {status}")
        require(row["pending"][0] == row["pending"][1] and row["retained"][0] == row["retained"][1], f"{kind} changed a count: {row}")
        require(ledger is None or row["ledger"] == (ledger, ledger), f"{kind} ledger {row['ledger']}, expected {ledger}")
    return {kind: row["status"] for kind, row in attempts.items()}


def check_admitted(attempts: dict) -> dict:
    for kind, row in attempts.items():
        require(row["admitted"] and row["status"] in ADMITTED + ("PREPARING",), f"{kind} was not admitted: {row}")
    return {kind: row["status"] for kind, row in attempts.items()}


def audit_errors(lines: list[str], allowed: dict) -> list[str]:
    """Every ERROR/FATAL line except the allowed patterns, each allowed exactly as many times as given."""
    counts = {pattern: 0 for pattern in allowed}
    unexpected = []
    for line in lines:
        if "/ERROR]" not in line and "/FATAL]" not in line:
            continue
        matched = next((pattern for pattern in allowed if pattern.search(line)), None)
        if matched is None:
            unexpected.append(line.rstrip())
        else:
            counts[matched] += 1
    unexpected += [f"expected {limit} x {pattern.pattern}, found {counts[pattern]}"
                   for pattern, limit in allowed.items() if counts[pattern] != limit]
    return unexpected


def matches(lines: list[str], pattern: re.Pattern[str]) -> list[tuple]:
    return [match.groups() for line in lines for match in [pattern.search(line)] if match]


def disk(world: Path) -> dict:
    data = world / "data"
    return {name: digest(data / file) if (data / file).is_file() else None for name, file in FILES.items()}


def flight_hold(run: Run, case: str, logical: str) -> dict:
    """Two status reads at least 600 game ticks apart: no claim, T PREPARED, the rocket in TRANSIT at one altitude, locked."""
    first, start = run.plain_status(logical), run.gametime()
    deadline = time.monotonic() + 180
    while run.gametime() - start < 600:
        require(time.monotonic() < deadline, f"{case}: 600 game ticks did not pass within 180 seconds")
        time.sleep(2.0)
    second, end = run.plain_status(logical), run.gametime()
    for status in (first, second):
        require(status["claim"] == "NONE" and status["transfer"] == "PREPARED" and status["state"] == "TRANSIT"
                and status["locked"] == "true", f"{case}: the ordinary flight moved or was claimed: {status}")
    require(first["y"] == second["y"] and first["rocket"] == second["rocket"], f"{case}: the ordinary flight advanced: {first} {second}")
    return dict(ticks=end - start, first=first, second=second)


def await_rest(run: Run, case: str, logical: str) -> dict:
    deadline = time.monotonic() + run.args.settle_timeout
    while True:
        status = run.plain_status(logical)
        if status["claim"] == "NONE" and status["transfer"] == "NONE" and status["state"] in ("FUELED", "LANDED"):
            return status
        require(time.monotonic() < deadline, f"{case}: the ordinary flight never came to rest: {status}")
        time.sleep(1.0)


def bay_refused(run: Run, case: str) -> dict:
    """A W325 fixture flight whose bays are refused: it lands, no bay consumed, no receipt naming its rocket."""
    fixture = bay.launch_fixture(run)
    deadline = time.monotonic() + run.args.flight_timeout + run.args.settle_timeout
    while True:
        status = run.status(fixture["logical"])
        if status["claim"] == "NONE" and status["state"] == "LANDED":
            break
        require(time.monotonic() < deadline, f"{case}: the bay fixture never landed: {status}")
        time.sleep(1.0)
    require(status["consumed"] == [None, None] and not status["receipts"] and status["packages"] == fixture["packages"],
            f"{case}: a refused deployment consumed a bay or wrote a receipt: {status}")
    shown = bay.DEPLOYMENT_STATUS.search(run.send(f"arce bay release-test deployment-status {fixture['data']}", bay.DEPLOYMENT_STATUS))
    menu = dict(phase=shown[2], reason=shown[3])
    check_bay_menu(case, menu)
    return dict(logical=fixture["logical"], status=status, menu=menu)


def check_bay_menu(case: str, menu: dict) -> None:
    expected = dict(phase="NONE", reason=CASES[case]["bay_reason"])
    require(menu == expected, f"{case}: the data bay answered {menu}, expected {expected}")


def new_world(run: Run, case: str) -> Path:
    properties = smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())),
                                                   encoding="ascii")
    return run.session / ("world-" + case)


def snapshot(run: Run, world: Path, case: str, label: str) -> dict:
    return evidence_tools.snapshot_world(world, run.evidence / f"{case}-{label}-raw")


# Family U --------------------------------------------------------------------------------------------------------------

UNKNOWN_REFUSAL = re.compile(r"\[ARCE-BETA-2000\] Beta world data check blocked startup;")
STORAGE_HINT = "Fix storage access to the world folder and restart; never delete, move or restore single files. If repair is still needed after that:"


def world_manifest(world: Path) -> dict:
    files = sorted(world.rglob("*"))
    require(len(files) <= 100000, "World manifest exceeds the fixture bound")
    require(not any(smoke.is_link_or_junction(file) for file in files), "World manifest contains a link")
    return {file.relative_to(world).as_posix(): digest(file) for file in files if file.is_file()}


def metadata_differences(before: nbt.Tag | None, after: nbt.Tag | None, path: str = "") -> list[dict]:
    """Typed decoded differences, retaining scalar bits (including signed zero) and list element types."""
    if before is not None and after is not None and before.type == after.type == nbt.COMPOUND:
        return [difference for name in sorted(set(before.value) | set(after.value))
                for difference in metadata_differences(before.value.get(name), after.value.get(name),
                                                       path + "/" + name.replace("~", "~0").replace("/", "~1"))]
    if before is not None and after is not None and before.type == after.type == nbt.LIST and before.value[0] == after.value[0]:
        left, right = before.value[1], after.value[1]
        return [difference for index in range(max(len(left), len(right)))
                for difference in metadata_differences(left[index] if index < len(left) else None,
                                                       right[index] if index < len(right) else None, f"{path}/{index}")]

    def encoded(tag):
        return nbt.write(b"", nbt.Tag(nbt.COMPOUND, dict(value=tag))) if tag is not None else None

    def summary(tag):
        if tag is None:
            return None
        value = tag.value if tag.type in (nbt.BYTE, nbt.SHORT, nbt.INT, nbt.LONG) else repr(tag.value)[:512]
        return dict(type=tag.type, value=value, sha256=hashlib.sha256(encoded(tag)).hexdigest())

    return [] if encoded(before) == encoded(after) else [dict(path=path or "/", before=summary(before), after=summary(after))]


def unknown_world_check(world: Path, before: dict, after: dict, baseline: tuple[bytes, nbt.Tag]) -> dict:
    """R416: exact non-metadata bytes, decoded level.dat except monotonic LONG LastPlayed, exact old-file rotation."""
    metadata = {"level.dat", "level.dat_old"}
    changed = [name for name in sorted(set(before) | set(after)) if before.get(name) != after.get(name)]
    errors = [f"Non-metadata file changed, added or removed: {name}" for name in changed if name not in metadata]
    differences = []
    last_played = None
    try:
        name, root = nbt.read_gzip(world / "level.dat")
        if name != baseline[0]:
            errors.append(f"level.dat root name changed: {baseline[0]!r} -> {name!r}")
        differences = metadata_differences(baseline[1], root)
        errors.extend(f"level.dat tag changed: {row['path']}" for row in differences if row["path"] != "/Data/LastPlayed")
        old_time = nbt.compound(baseline[1], "Data", "LastPlayed")
        new_time = nbt.compound(root, "Data", "LastPlayed")
        last_played = dict(before=old_time.value if old_time.type == nbt.LONG else repr(old_time.value)[:512],
                           after=new_time.value if new_time.type == nbt.LONG else repr(new_time.value)[:512],
                           before_type=old_time.type, after_type=new_time.type)
        if old_time.type != nbt.LONG or new_time.type != nbt.LONG or new_time.value < old_time.value:
            errors.append("/Data/LastPlayed must remain a LONG not smaller than S0")
    except (OSError, nbt.NbtError) as failure:
        errors.append(f"level.dat could not be compared: {failure}")
    if "level.dat" not in before or "level.dat" not in after:
        errors.append("level.dat must exist at S0 and after refusal")
    old_allowed = {before[name] for name in metadata if name in before}
    if "level.dat_old" not in after or after["level.dat_old"] not in old_allowed:
        errors.append("level.dat_old is neither S0 level.dat nor S0 level.dat_old")
    return dict(status="FAILED" if errors else "PASS", errors=errors, changed_files=changed,
                whole_world_byte_identical=before == after, decoded_differences=differences, last_played=last_played,
                level_dat_old_sha256=after.get("level.dat_old"), allowed_old_sha256=sorted(old_allowed))


def check_unknown_probe(text: str, type_name: str) -> None:
    for expected in ("VANILLA_VALIDATE=[]", "ACTUAL_ARCE_PRESENCE=UNKNOWN", "PRESENCE_UNKNOWN=true",
                     f"DATA_TYPE=Optional[{type_name}]", "DIAGNOSTIC=ARCE-BETA-2000"):
        require(expected in text.splitlines(), f"UNKNOWN-presence probe missing {expected}")


def check_unknown_refusal(lines: list[str], case: str, session: Path | None = None) -> dict:
    spec = CASES[case]
    refused = [line.strip() for line in lines if UNKNOWN_REFUSAL.search(line)]
    require(len(refused) == 1, f"{case}/1: expected one ARCE-BETA-2000 refusal: {refused}")
    line = refused[0]
    reason = f"type={spec['type']}; reason={FILES[spec['file']][:-4]} presence is unknown:"
    require(reason in line and ". " + STORAGE_HINT + " Keep the world stopped." in line,
            f"{case}/1: wrong type, reason or storage-first hint: {line}")
    require(not any(smoke.READY_MARKER.search(line) for line in lines) and not matches(lines, BAY_INDEX),
            f"{case}/1: refused startup reached world loading")
    if session is None:
        errors = audit_errors(lines, {UNKNOWN_REFUSAL: 1})
        require(not errors, f"{case}/1: unexpected ERROR/FATAL: {errors}")
        audit = None
    else:
        audit = audit_unknown_start(lines, case, session)
    return dict(diagnostic="ARCE-BETA-2000", type=spec["type"], storage_hint_first=True, line=line, audit=audit)


def audit_unknown_start(lines: list[str], case: str, session: Path, root_refusal: dict | None = None) -> dict:
    """Exact pinned Forge refusal propagation, including exception identities (same shutdown boundary as W157)."""
    spec = CASES[case] if root_refusal is None else dict(file="ledger", type="RESOURCE_ESCROW")
    reason = FILES[spec["file"]][:-4] + " presence is unknown: its file status check failed, so its absence is not proven"
    exception = "io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException: " + reason
    message = (f"[ARCE-BETA-2000] Beta world data check blocked startup; type={spec['type']}; reason={reason}. "
               + STORAGE_HINT + " Keep the world stopped. Escrow recovery requires a consistent whole-world backup "
               "including all terrain, entities, POI, journals and player data; never restore or delete only the escrow file.")
    causes = (exception,)
    if root_refusal is not None:
        reason = root_refusal["reason"]
        exception = "io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException: " + reason
        causes = (exception,) + tuple(root_refusal["causes"])
        message = (f"[{root_refusal['diagnostic']}] Beta world data check blocked startup; type=RESOURCE_ESCROW; reason={reason}. "
                   "Keep the world stopped. Escrow recovery requires a consistent whole-world backup including all terrain, "
                   "entities, POI, journals and player data; never restore or delete only the escrow file.")
    expected = [
        (refusal_log.PROJECT_LOGGERS, "ERROR", re.escape(message), ()),
        (("ne.mi.ev.EventBus/EVENTBUS", "net.minecraftforge.eventbus.EventBus/EVENTBUS"), "ERROR",
         re.escape("Exception caught during firing event: " + reason), causes),
        (("minecraft/MinecraftServer",), "ERROR", re.escape("Encountered an unexpected exception"), causes),
        (("ne.mi.co.ForgeMod/", "net.minecraftforge.common.ForgeMod/"), "FATAL",
         r"Preparing crash report with UUID [0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", ()),
        (("minecraft/MinecraftServer",), "ERROR", re.escape("This crash report has been saved to: " + session.as_posix())
         + r"/(?:\./)?crash-reports/crash-\d{4}-\d{2}-\d{2}_\d{2}\.\d{2}\.\d{2}-server\.txt", ()),
        (("minecraft/MinecraftServer",), "ERROR", re.escape("Exception stopping the server"), (refusal_log.VANILLA_NPE,)),
    ]
    counts = [0] * len(expected)
    headers = [(i, match) for i, line in enumerate(lines) if (match := refusal_log.HEADER.fullmatch(line.rstrip()))]
    for position, (start, header) in enumerate(headers):
        if header["level"] not in ("ERROR", "FATAL"):
            continue
        end = headers[position + 1][0] if position + 1 < len(headers) else len(lines)
        body = [line.strip() for line in lines[start + 1:end]]
        found = False
        for index, (loggers, level, pattern, causes) in enumerate(expected):
            text = header["message"].replace("\\", "/") if index == 4 else header["message"]
            if header["thread"] == "Server thread" and header["logger"] in loggers and header["level"] == level and re.fullmatch(pattern, text):
                require(tuple(line for line in body if refusal_log.EXCEPTION.fullmatch(line)) == causes,
                        f"{case}/1: unexpected exception under diagnostic {index}")
                if index == 5:
                    frames = [line for line in body if line.startswith("at ")]
                    require(bool(frames) and re.fullmatch(re.escape(refusal_log.VANILLA_FRAME) + r"(?: ~\[[^\r\n]+\] \{[^\r\n]*\})?", frames[0])
                            and not any("advancedrocketrycommunity" in line.lower() for line in body),
                            f"{case}/1: unexpected shutdown origin")
                counts[index] += 1
                found = True
                break
        require(found, f"{case}/1: unexpected ERROR/FATAL: {lines[start].strip()}")
    require(counts == [1] * len(expected), f"{case}/1: refusal propagation counts differ: {counts}")
    # Reject malformed ERROR/FATAL lines that the structured header parser did not recognize.
    parsed = {i for i, _ in headers}
    require(not any(("/ERROR]" in line or "/FATAL]" in line) and i not in parsed for i, line in enumerate(lines)),
            f"{case}/1: unparsed ERROR/FATAL")
    return dict(expected_counts=[1] * len(expected), observed_counts=counts)


def run_u(run: Run, case: str) -> dict:
    spec = CASES[case]
    world = new_world(run, case)
    row = dict(case=case, rows=list(spec["rows"]), starts=[])
    if case == "U_BAY":
        row["seed"] = evidence_tools.seed_capacity_world(world, 2, phase="REJECTED")
    run.start(case, 0, {})
    if case == "U_LEDGER":
        run.forceload()
        run.cell("S", P2)
        check_admitted(run.attempt("S", "FUEL"))
        run.await_completed("S", ("FUEL",))
        run.save()
    stopped = run.stop(case, 0)
    require(not matches(stopped["lines"], BAY_NOT_LOADED) and not matches(stopped["lines"], LEDGER_NOT_LOADED),
            f"{case}/0: NOT_LOADED before the trigger")
    if case == "U_BAY":
        require(matches(stopped["lines"], BAY_INDEX) == [("LOADED", "2")], f"{case}/0: seed did not load")
    file = world / "data" / FILES[spec["file"]]
    before = world_manifest(world)
    require(file.relative_to(world).as_posix() in before, f"{case}/0: no fixture file")
    baseline = nbt.read_gzip(world / "level.dat")
    for name in ("level.dat", "level.dat_old"):
        if name in before:
            shutil.copy2(world / name, run.evidence / f"{case}-S0-{name}")
    row["starts"].append(dict(start=0, disk=disk(world)))
    (run.evidence / f"{case}-before.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
    entry = dict(start=1)
    try:
        with presence.delete_pending(file) as held:
            entry["handle"] = held
            probe_text = run.probe_unknown(world, file.name, run.evidence / f"{case}-presence.log")
            check_unknown_probe(probe_text, spec["type"])
            entry["isPresenceUnknown"] = True
            process = run.start_refused(case)
            try:
                # This start must stop itself before READY, not be made to stop by a console command.
                process.wait_for(re.compile(UNKNOWN_REFUSAL.pattern + r'|Done \(.*For help, type "help"'), run.args.startup_timeout)
                require(not any(smoke.READY_MARKER.search(line) for line in process.lines), f"{case}/1: startup was not refused")
                entry["exit_code"] = process.finish(run.args.startup_timeout)
                evidence_tools.recheck_installed(run.candidate)
                # The pinned Forge/vanilla pre-Level refusal reports its crash and exits with code 0 (also W157).
                require(entry["exit_code"] == 0, f"{case}/1: abnormal process exit {entry['exit_code']}")
                entry["refusal"] = check_unknown_refusal(process.lines, case, run.session)
            finally:
                process.abort()
                run.process = None
    finally:
        # Capture this even when the native oracle fails; the handle context has already restored the disposition.
        after = world_manifest(world)
        entry["world_preservation"] = unknown_world_check(world, before, after, baseline)
        (run.evidence / f"{case}-metadata-comparison.json").write_text(
            json.dumps(entry["world_preservation"], indent=2), encoding="utf-8")
        (run.evidence / f"{case}-after.json").write_text(json.dumps(after, indent=2), encoding="utf-8")
        (run.evidence / f"{case}-restoration.json").write_text(json.dumps(dict(
            file=str(file), sha256=digest(file), unchanged=before == after,
            protected_data_unchanged=entry["world_preservation"]["status"] == "PASS"), indent=2), encoding="utf-8")
    entry["world_unchanged"] = before == after
    entry["changed_files"] = [name for name in sorted(set(before) | set(after)) if before.get(name) != after.get(name)]
    entry["raw"] = snapshot(run, world, case, "1")
    row["starts"].append(entry)
    # A recovery start AND a second clean restart, in addition to the refused restart.
    for restart in (2, 3):
        run.start(case, restart, {})
        entry = dict(start=restart)
        if case == "U_LEDGER":
            require(run.escrow_status() == "present", f"{case}/{restart}: ledger did not load")
            entry["fill"] = run.report("S")["FUEL"]
            require(completed(entry["fill"], "FUEL"), f"{case}/{restart}: earlier fill changed")
        stopped = run.stop(case, restart)
        require(not matches(stopped["lines"], BAY_NOT_LOADED) and not matches(stopped["lines"], LEDGER_NOT_LOADED)
                and not any(UNKNOWN_REFUSAL.search(line) for line in stopped["lines"]), f"{case}/{restart}: not a clean start")
        if case == "U_BAY":
            require(matches(stopped["lines"], BAY_INDEX) == [("LOADED", "2")], f"{case}/{restart}: receipts did not load")
        require(digest(file) == before[file.relative_to(world).as_posix()], f"{case}/{restart}: fixture bytes changed")
        entry["disk"] = disk(world)
        row["starts"].append(entry)
    # Keep both recovery observations even when the protected-world comparison fails.
    (run.evidence / f"{case}-observations.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
    preservation = row["starts"][1]["world_preservation"]
    require(preservation["status"] == "PASS", f"{case}/1: world data changed: {preservation['errors']}")
    return row


# Family N --------------------------------------------------------------------------------------------------------------

def check_injected(case: str, lines: list[str], type_name: str, data_name: str) -> dict:
    if CASES[case].get("presence_unknown"):
        waiting = [i for i, line in enumerate(lines) if f"ARCE_RELEASE_PRESENCE_WAIT type={type_name}" in line]
        unknown = [i for i, line in enumerate(lines) if f"ARCE_RELEASE_PRESENCE_UNKNOWN type={type_name}" in line]
        require(len(waiting) == len(unknown) == 1 and waiting[0] < unknown[0], "Missing ordered presence rendezvous")
        require(not any(UNKNOWN_REFUSAL.search(line) for line in lines), "Presence failed before the pre-start check finished")
    injected = matches(lines, INJECTED)
    require(injected == [(type_name,)] and not matches(lines, INJECTION_REFUSED), f"{case}: the seam did not inject once: {injected}")
    require(matches(lines, VANILLA_LOAD_ERROR) == [(data_name,)], f"{case}: vanilla's load ERROR is not exactly one for {data_name}")
    return dict(injected=injected[0][0])


def run_n(run: Run, case: str) -> dict:
    world = new_world(run, case)
    row = dict(case=case, rows=list(CASES[case]["rows"]), seed=evidence_tools.seed_capacity_world(world, 2, phase="REJECTED"),
               starts=[])
    seeded = evidence_tools.bay_receipts(world / "data" / FILES["receipts"])
    stale = str(uuid.uuid4())
    # S0: the fixtures, nothing attempted.
    run.start(case, 0, {})
    entry = dict(start=0)
    run.forceload()
    entry["cell"] = run.cell("A", P1)
    plain = run.plain()
    entry["plain"] = plain
    entry["launch"] = run.launch(plain["rocket"], "transit_prepared")
    require(entry["launch"]["code"] in ("SUCCESS", "JOURNAL_PENDING"), f"{case}/0: the plain flight did not launch: {entry['launch']}")
    paused = PAUSED.search(run.wait(PAUSED, run.args.flight_timeout))
    require(paused[1] == "TRANSIT_PREPARED", f"{case}/0: the plain flight paused at {paused[1]}")
    entry["at_pause"] = run.plain_status(plain["logical"])
    require(entry["at_pause"]["transfer"] == "PREPARED" and entry["at_pause"]["state"] == "TRANSIT", f"{case}/0: {entry['at_pause']}")
    entry["satellite"] = run.satellite()
    require(entry["satellite"]["code"] == "SUCCESS", f"{case}/0: the satellite launch failed: {entry['satellite']}")
    entry["stale"] = bay.STALE.search(run.send(f"arce bay release-test stale-assembly {stale}", bay.STALE))[0]
    run.save()
    stopped = run.stop(case, 0)
    require(matches(stopped["lines"], BAY_INDEX) == [("LOADED", "2")], f"{case}/0: the receipts did not load at S0")
    data = world / "data"
    require(evidence_tools.raw_names(data / FILES["transfers"], plain["logical"]), f"{case}/0: the transfer journal does not name L")
    require(evidence_tools.raw_names(data / FILES["transactions"], stale), f"{case}/0: the transaction journal does not name A")
    require(entry["satellite"]["satellite"] in evidence_tools.registry_satellites(data / FILES["registry"]), f"{case}/0: X not on disk")
    entry.update(disk=disk(world), raw=snapshot(run, world, case, "0"))
    receipts0 = entry["disk"]["receipts"]
    row["starts"].append(entry)
    # S1: the receipt file present but not loaded.
    process = run.start(case, 1, {"releaseTestLoadFailure": "BAY_RECEIPTS"})
    entry = dict(start=1)
    entry["flight"] = flight_hold(run, case, plain["logical"])
    entry["satellite_launch"] = run.satellite()
    require(entry["satellite_launch"]["code"] == "DEPLOYMENT_PENDING", f"{case}/1: {entry['satellite_launch']}")
    entry["decommission"] = run.decommission(row["starts"][0]["satellite"]["satellite"])
    require(entry["decommission"] == dict(result="DEPLOYMENT_PENDING", present=True), f"{case}/1: {entry['decommission']}")
    entry["insert"] = run.insert()
    require(entry["insert"]["blocked"] and not entry["insert"]["valid"] and entry["insert"]["remainder"] == 1
            and not entry["insert"]["held"], f"{case}/1: the package entered the bay: {entry['insert']}")
    entry["attempts"] = check_refused(run.attempt("A"), None, "absent")
    entry["report"] = run.report("A")
    require(all(untouched(entry["report"][kind], kind) for kind in KINDS), f"{case}/1: a cell changed: {entry['report']}")
    entry["retire"] = run.retire(seeded[0]["satellite"])
    require(entry["retire"] == "refused.not_loaded", f"{case}/1: retire-rejected answered {entry['retire']}")
    entry["pending"] = run.pending()
    stopped = run.stop(case, 1, {VANILLA_LOAD_ERROR: 1})
    lines = stopped["lines"]
    entry["injection"] = check_injected(case, lines, "BAY_RECEIPTS", "advancedrocketrycommunity_bay_receipts")
    entry["warn"] = matches(lines, BAY_NOT_LOADED)
    entry["index"] = matches(lines, BAY_INDEX)
    reason = "PRESENCE_UNKNOWN" if CASES[case].get("presence_unknown") else "LOAD_FAILED"
    require(entry["warn"] == [(reason, FILES["receipts"])] and entry["index"] == [("NOT_LOADED", "0")],
            f"{case}/1: expected one LOAD_FAILED WARN and one NOT_LOADED INFO: {entry['warn']} {entry['index']}")
    entry["fenced"] = matches(lines, FENCED)
    require(entry["fenced"] == [(stale,)], f"{case}/1: assembly recovery was not fenced once for A: {entry['fenced']}")
    entry["disk"] = disk(world)
    require(entry["disk"]["receipts"] == receipts0 and entry["disk"]["ledger"] is None, f"{case}/1: the receipts or ledger changed")
    require(evidence_tools.raw_names(data / FILES["transfers"], plain["logical"])
            and evidence_tools.raw_names(data / FILES["transactions"], stale), f"{case}/1: a journal record was lost")
    entry["raw"] = snapshot(run, world, case, "1")
    row["starts"].append(entry)
    # S2: recovery by a restart that loads the file.
    run.start(case, 2, {})
    entry = dict(start=2)
    entry["flight"] = await_rest(run, case, plain["logical"])
    entry["satellite_launch"] = run.satellite()
    require(entry["satellite_launch"]["code"] == "SUCCESS", f"{case}/2: {entry['satellite_launch']}")
    entry["decommission"] = run.decommission(row["starts"][0]["satellite"]["satellite"])
    # Smoke 3: X keeps its release-test mission, so without the deployment fence the registry's own mission check answers
    # (MISSION_BUSY, X kept); an idle X would be removed (SUCCESS). Either way the fence no longer answers.
    require(entry["decommission"] in (dict(result="SUCCESS", present=False), dict(result="MISSION_BUSY", present=True)),
            f"{case}/2: the decommission is still fenced or failed otherwise: {entry['decommission']}")
    entry["insert"] = run.insert()
    require(not entry["insert"]["blocked"] and entry["insert"]["valid"] and entry["insert"]["remainder"] == 0
            and entry["insert"]["held"], f"{case}/2: the package did not enter the bay: {entry['insert']}")
    entry["attempts"] = check_admitted(run.attempt("A"))
    entry["completed"] = run.await_completed("A")
    stopped = run.stop(case, 2)
    lines = stopped["lines"]
    require(matches(lines, BAY_INDEX) == [("LOADED", "2")] and not matches(lines, BAY_NOT_LOADED) and not matches(lines, FENCED),
            f"{case}/2: the receipts did not load cleanly or A was fenced")
    entry["disk"] = disk(world)
    require(not evidence_tools.raw_names(data / FILES["transfers"], plain["logical"]), f"{case}/2: T of L is still journaled")
    require(entry["disk"]["ledger"] is not None, f"{case}/2: the admitted transfers wrote no ledger")
    entry["raw"] = snapshot(run, world, case, "2")
    row["starts"].append(entry)
    return row


# Family L --------------------------------------------------------------------------------------------------------------

def run_l(run: Run, case: str) -> dict:
    world = new_world(run, case)
    row = dict(case=case, rows=list(CASES[case]["rows"]), starts=[])
    run.start(case, 0, {})
    entry = dict(start=0)
    run.forceload()
    entry["cell_s"] = run.cell("S", P2)
    entry["cell_a"] = run.cell("A", P1)
    check_admitted(run.attempt("S", "FUEL"))
    entry["fill"] = run.await_completed("S", ("FUEL",))["FUEL"]
    plain = run.plain()
    entry["plain"] = plain
    entry["board"] = run.rider("board", plain["logical"])
    require(entry["board"]["mounted"] and entry["board"]["seat"] == 0, f"{case}/0: the rider did not board: {entry['board']}")
    run.save()
    stopped = run.stop(case, 0)
    require(not matches(stopped["lines"], LEDGER_NOT_LOADED), f"{case}/0: the ledger was NOT_LOADED at S0")
    entry.update(disk=disk(world), raw=snapshot(run, world, case, "0"))
    ledger0 = entry["disk"]["ledger"]
    require(ledger0 is not None, f"{case}/0: no ledger file")
    row["starts"].append(entry)
    # S1: the ledger present but not loaded.
    run.start(case, 1, {"releaseTestLoadFailure": "RESOURCE_ESCROW"})
    entry = dict(start=1)
    entry["escrow_status"] = run.escrow_status()
    require(entry["escrow_status"] == "not-loaded", f"{case}/1: escrow status ledger={entry['escrow_status']}")
    entry["launch"] = run.launch(plain["rocket"], "countdown")
    require(entry["launch"]["code"] not in ("SUCCESS", "JOURNAL_PENDING"), f"{case}/1: the launch was admitted: {entry['launch']}")
    entry["attempts"] = check_refused(run.attempt("A"), "ESCROW_PENDING", "absent")
    entry["report"] = run.report("A")
    for kind in KINDS:
        held = entry["report"][kind]
        require(untouched(held, kind) and held["locked"] and held["protected"] and held["refusal"] == "ESCROW_PENDING",
                f"{case}/1: the {kind} device is not held: {held}")
    entry["fill"] = run.report("S")["FUEL"]
    require((entry["fill"]["rocket"], entry["fill"]["device"]) == AFTER["FUEL"], f"{case}/1: the earlier fill changed: {entry['fill']}")
    entry["login"] = run.rider("login", plain["logical"])
    if entry["login"]["mounted"]:
        run.rider("sneak", plain["logical"])
        deadline = time.monotonic() + 30
        while True:
            entry["dismount"] = run.rider("state", plain["logical"])
            if not entry["dismount"]["mounted"]:
                break
            require(time.monotonic() < deadline, f"{case}/1: the vanilla sneak did not dismount the rider")
            time.sleep(0.5)
        entry["dismount_row"] = "PASS"
    else:
        entry["dismount_row"] = "NOT_COVERED: the login did not seat the rider under the hold"
    stopped = run.stop(case, 1, {VANILLA_LOAD_ERROR: 1})
    lines = stopped["lines"]
    entry["injection"] = check_injected(case, lines, "RESOURCE_ESCROW", "advancedrocketrycommunity_resource_escrow")
    entry["warn"] = matches(lines, LEDGER_NOT_LOADED)
    reason = "PRESENCE_UNKNOWN" if CASES[case].get("presence_unknown") else "LOAD_FAILED"
    require(entry["warn"] == [(reason, FILES["ledger"])], f"{case}/1: expected one {reason} ledger WARN: {entry['warn']}")
    require(not matches(lines, BAY_NOT_LOADED), f"{case}/1: the bay receipts were NOT_LOADED too")
    entry["disk"] = disk(world)
    require(entry["disk"]["ledger"] == ledger0, f"{case}/1: the ledger file changed under the hold")
    entry["raw"] = snapshot(run, world, case, "1")
    row["starts"].append(entry)
    # S2: recovery by a restart that loads the ledger.
    run.start(case, 2, {})
    entry = dict(start=2)
    entry["escrow_status"] = run.escrow_status()
    require(entry["escrow_status"] == "present", f"{case}/2: escrow status ledger={entry['escrow_status']}")
    entry["attempts"] = check_admitted(run.attempt("A"))
    entry["completed"] = run.await_completed("A")
    entry["launch"] = run.launch(plain["rocket"], "countdown")
    require(entry["launch"]["code"] in ("SUCCESS", "JOURNAL_PENDING"), f"{case}/2: the launch was refused: {entry['launch']}")
    stopped = run.stop(case, 2)
    require(not matches(stopped["lines"], LEDGER_NOT_LOADED), f"{case}/2: the ledger was NOT_LOADED")
    entry["disk"] = disk(world)
    require(entry["disk"]["ledger"] is not None and entry["disk"]["ledger"] != ledger0,
            f"{case}/2: the admitted transfers did not leave a present, changed ledger")
    entry["raw"] = snapshot(run, world, case, "2")
    row["starts"].append(entry)
    return row


# Family P --------------------------------------------------------------------------------------------------------------

def run_p(run: Run, case: str) -> dict:
    spec = CASES[case]
    world = new_world(run, case)
    seed = seed_pool(world, pool_rows(case))
    row = dict(case=case, rows=list(spec["rows"]), seed={k: v for k, v in seed.items() if k != "satellites"}, starts=[])
    cell_owner = OWNER if case == "P_OWNER" else P1
    by_kind = {}
    for satellite, seeded in zip(seed["satellites"], seed["rows"]):
        by_kind.setdefault((seeded["phase"], seeded["released"]), satellite)
    for restart in (0, 1, 2):
        run.start(case, restart, {})
        entry = dict(start=restart)
        if restart == 0:
            run.forceload()
            entry["cell_a"] = run.cell("A", cell_owner)
            if spec.get("other"):
                entry["cell_b"] = run.cell("B", P2)
        entry["pending"] = run.pending()
        require((entry["pending"]["pending"], entry["pending"]["retained"]) == (spec["pending"], spec["pending"]),
                f"{case}/{restart}: the pool is not held: {entry['pending']}")
        entry["attempts"] = check_refused(run.attempt("A"), spec["refusal"], "absent")
        entry["report"] = run.report("A")
        require(all(untouched(entry["report"][kind], kind) for kind in KINDS), f"{case}/{restart}: a refused cell changed")
        entry["bay"] = bay_refused(run, case)
        check_bay_menu(case, entry["bay"]["menu"])
        entry["retire"] = {f"{phase}{'_RELEASED' if released else ''}": run.retire(satellite)
                           for (phase, released), satellite in sorted(by_kind.items())}
        for name, answer in entry["retire"].items():
            expected = "refused.released" if name == "REJECTED_RELEASED" else "refused.not_rejected"
            require(answer == expected, f"{case}/{restart}: retire-rejected of a {name} receipt answered {answer}")
        if spec.get("other") and restart == 2:
            entry["other_attempts"] = check_admitted(run.attempt("B"))
            entry["other_completed"] = run.await_completed("B")
            entry["pending_after"] = run.pending()
            require(entry["pending_after"]["pending"] == spec["pending"], f"{case}/2: the other owner's transfers changed the pool")
        entry["pending_end"] = run.pending()
        require(entry["pending_end"]["pending"] == spec["pending"], f"{case}/{restart}: the pool changed: {entry['pending_end']}")
        run.stop(case, restart)
        entry["disk"] = disk(world)
        require(entry["disk"]["receipts"] == seed["sha256"], f"{case}/{restart}: the receipt file changed")
        require((entry["disk"]["ledger"] is None) == (not (spec.get("other") and restart == 2)),
                f"{case}/{restart}: the ledger file is {'absent' if entry['disk']['ledger'] is None else 'present'}")
        entry["raw"] = snapshot(run, world, case, str(restart))
        row["starts"].append(entry)
    return row


def seed_bad_ledger(file: Path, future: bool) -> dict:
    """The native ledger was emitted by the code's current encoder; advance its supported version, never pin root 2/3."""
    name, root = nbt.read_gzip(file)
    payload = nbt.compound(root, "data")
    supported = nbt.compound(payload, "schema_version")
    require(supported.type == nbt.INT and supported.value > 0, "Native ledger did not emit its supported root version")
    current = supported.value
    if future:
        payload.value["schema_version"] = nbt.Tag(nbt.INT, current + 1)
    else:
        payload.value["issued_through"] = nbt.Tag(nbt.LONG, -1)
    nbt.write_gzip(file, name, root)
    require(nbt.read_gzip(file) == (name, root), "Invalid-root seed typed readback differs")
    reason = FILES["ledger"][:-4] + (f" uses future schema {current + 1}" if future else " failed semantic validation")
    return dict(supported_version=current, seeded_version=current + 1 if future else current, sha256=digest(file),
                reason=reason, diagnostic="ARCE-BETA-2001" if future else "ARCE-BETA-2000",
                causes=[] if future else ["Caused by: java.lang.IllegalArgumentException: Invalid ledger counters"])


def run_r031(run: Run, case: str) -> dict:
    world = new_world(run, case)
    row = dict(case=case, rows=list(CASES[case]["rows"]), starts=[])
    run.start(case, 0, {})
    run.forceload(); run.cell("S", P2)
    check_admitted(run.attempt("S", "FUEL")); run.await_completed("S", ("FUEL",)); run.save()
    run.stop(case, 0)
    file = world / "data" / FILES["ledger"]
    good = file.read_bytes()
    row["seed"] = seed_bad_ledger(file, case == "R031_FUTURE")
    snapshot(run, world, case, "seed")
    before = world_manifest(world); baseline = nbt.read_gzip(world / "level.dat")
    for name in ("level.dat", "level.dat_old"):
        if name in before: shutil.copy2(world / name, run.evidence / f"{case}-S0-{name}")
    (run.evidence / f"{case}-before.json").write_text(json.dumps(before, indent=2), encoding="utf-8")
    process = run.start_refused(case)
    try:
        process.wait_for(re.compile(r"Beta world data check blocked startup|Done \(.*For help"), run.args.startup_timeout)
        require(not any(smoke.READY_MARKER.search(line) for line in process.lines), "Invalid ledger reached READY")
        code = process.finish(run.args.startup_timeout)
        require(code == 0, "Invalid-ledger refusal exited abnormally")
        require(not matches(process.lines, BAY_INDEX), "Invalid ledger reached world loading")
        audit = audit_unknown_start(process.lines, case, run.session, row["seed"])
    finally:
        process.abort(); run.process = None
    after = world_manifest(world)
    preservation = unknown_world_check(world, before, after, baseline)
    row["starts"].append(dict(start=1, exit_code=code, audit=audit, world_preservation=preservation))
    (run.evidence / f"{case}-after.json").write_text(json.dumps(after, indent=2), encoding="utf-8")
    (run.evidence / f"{case}-metadata-comparison.json").write_text(json.dumps(preservation, indent=2), encoding="utf-8")
    require(preservation["status"] == "PASS", "R-031 changed protected world bytes: " + str(preservation["errors"]))
    snapshot(run, world, case, "refused")
    # Offline fixture restoration is explicitly not an operator repair procedure. Both clean restarts load the original.
    file.write_bytes(good)
    for restart in (2, 3):
        run.start(case, restart, {})
        require(run.escrow_status() == "present", "Restored ledger did not load")
        require(completed(run.report("S")["FUEL"], "FUEL"), "Restored fixture changed resource totals")
        run.stop(case, restart)
        require(file.read_bytes() == good, "A clean restart changed the restored ledger")
        row["starts"].append(dict(start=restart, ledger_sha256=digest(file)))
    return row


RUNNERS = dict(U=run_u, N=run_n, L=run_l, P=run_p, R=run_r031)


def run(args) -> dict:
    assert_pin(args.source_commit)
    candidate = bay.check_candidate(args)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the disposable directory")
    if args.scratch and (args.case or [SCRATCH[args.scratch]]) != [SCRATCH[args.scratch]]:
        raise smoke.SmokeError(f"A scratch run of family {args.scratch} runs only {SCRATCH[args.scratch]}")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or smoke.is_link_or_junction(libraries) or any(
            smoke.is_link_or_junction(path) for path in libraries.rglob("*")):
        raise smoke.SmokeError("Unsafe or missing Forge libraries")
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w332-r042-owned"
    marker.write_text(token, encoding="ascii")
    cases = args.case or ([SCRATCH[args.scratch]] if args.scratch else list(CASES))
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[],
                  lock_owner=args.lock_owner, candidate=candidate, scratch=args.scratch,
                  negative_control=bool(args.scratch))
    runner = Run(args, session, evidence)
    bay.ORIGIN = (8, 2, 8)
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir()
        candidate.update(evidence_tools.install_candidate(args.jar, session / "mods" / args.jar.name, args.jar_sha256))
        runner.candidate = candidate
        runner.port = smoke.allocate_port()
        smoke.write_server_configuration(session, runner.port, True)
        for case in cases:
            report["in_progress"] = case
            try:
                result = RUNNERS[CASES[case]["family"]](runner, case)
                result["status"] = "PASS"
                report["cases"].append(result)
                print(f"{case}: PASS (S0 and two restarts)", flush=True)
            except (smoke.SmokeError, OSError) as failure:
                if runner.process is not None:
                    runner.process.abort(); runner.process = None
                report["cases"].append(dict(case=case, status="FAILED", error=str(failure)))
                if CASES[case]["family"] == "U":
                    report["cases"][-1]["coverage"] = "NOT_COVERED"
                world = session / ("world-" + case)
                if world.is_dir():
                    report["cases"][-1]["raw"] = evidence_tools.snapshot_world(world, evidence / f"{case}-failure-raw")
                print(f"{case}: FAIL {failure}", flush=True)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            shutil.rmtree(session / ("world-" + case), ignore_errors=True)
        report.pop("in_progress", None); assert_pin(args.source_commit); bay.recheck_candidate(candidate)
        report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["cases"]) else "FAILED"
        return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if runner.process is not None:
            runner.process.abort()
        runner.release_presence()
        (evidence / "results.json").write_text(json.dumps(report, indent=2, default=str), encoding="utf-8")
        if (session.parent == intended and not smoke.is_link_or_junction(session) and marker.is_file()
                and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session, ignore_errors=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    bay.add_candidate_arguments(parser)
    parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--scratch", choices=sorted(SCRATCH),
                        help="Negative control: pass w332Scratch=<family> to a scratch JAR; the family's case must fail")
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
