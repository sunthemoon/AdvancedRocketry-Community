#!/usr/bin/env python3
"""W332 native cases for R-042 (records/W332-DESIGN.md): NOT_LOADED and the held pending pool.

Each case starts a fresh flat world on the pinned candidate JAR, makes a first start (S0) and two restarts (S1, S2), and
checks each start's logs, console answers and stopped-world bytes:

* ``N_BAY``: the gated load-failure seam keeps the bay receipts NOT_LOADED for S1, with the audit's server-wide effects,
  and S2 recovers;
* ``L_LEDGER``: the same for the escrow ledger and W329's protective answers;
* ``P_SERVER`` and ``P_OWNER``: released and REPAIR receipts hold the shared pending pool over both restarts; fuel, the
  four cargo kinds and a bay deployment are refused with nothing changed, another owner is admitted, and no online
  command releases them.

Production admission stays off: every fixture is a gated release-test command or start property. W328's refusal of an
unknown presence (TASK item 2, first trigger) is not a case here: this host cannot make a managed file's status check fail
before vanilla's own world-folder walk refuses the start (records/W332-DESIGN.md, section 6).
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import time
import uuid

import bay_integrated_evidence as evidence_tools
import loader_cut_nbt as nbt
import run_dedicated_server_smoke as smoke
import run_v180_bay_forced_stop as bay
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
    "N_BAY": dict(family="N", rows=("TASK 2: present but not loaded",)),
    "L_LEDGER": dict(family="L", rows=("TASK 3: the escrow ledger NOT_LOADED",)),
    "P_SERVER": dict(family="P", pending=64, refusal="ESCROW_FULL", rows=("TASK 4: the server pool",)),
    "P_OWNER": dict(family="P", pending=16, refusal="OWNER_PENDING_FULL", other=True, rows=("TASK 4: per owner",)),
}
SCRATCH = {"N": "N_BAY", "L": "L_LEDGER", "P": "P_SERVER"}


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
        return super().start(case, restart, properties)

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
        code = self.end("stop", self.args.exit_timeout)
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
    return dict(logical=fixture["logical"], status=status, menu=dict(phase=shown[2], reason=shown[3]))


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


# Family N --------------------------------------------------------------------------------------------------------------

def check_injected(case: str, lines: list[str], type_name: str, data_name: str) -> dict:
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
    require(entry["warn"] == [("LOAD_FAILED", FILES["receipts"])] and entry["index"] == [("NOT_LOADED", "0")],
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
    require(entry["warn"] == [("LOAD_FAILED", FILES["ledger"])], f"{case}/1: expected one LOAD_FAILED ledger WARN: {entry['warn']}")
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
    require(entry["disk"]["ledger"] != ledger0, f"{case}/2: the admitted transfers did not change the ledger")
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


RUNNERS = dict(N=run_n, L=run_l, P=run_p)


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
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        runner.port = smoke.allocate_port()
        smoke.write_server_configuration(session, runner.port, True)
        for case in cases:
            report["in_progress"] = case
            try:
                result = RUNNERS[CASES[case]["family"]](runner, case)
                result["status"] = "PASS"
                report["cases"].append(result)
                print(f"{case}: PASS (S0 and two restarts)", flush=True)
            except smoke.SmokeError as failure:
                if runner.process is not None:
                    runner.process.abort(); runner.process = None
                report["cases"].append(dict(case=case, status="FAILED", error=str(failure)))
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
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
