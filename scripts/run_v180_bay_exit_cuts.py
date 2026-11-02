#!/usr/bin/env python3
"""W254 (W230-S3): native cuts of a held bay exit and the packaged mapped-observer smoke.

Each case starts a fresh flat world on the built, reobfuscated JAR. The first start arms the satellite registry writer's
STAGED pause, launches the bay forced-stop fixture with a facility-owned survival rider aboard (W254's gated
``arce bay release-test exit fixture``), and waits until the in-session INTENT hold gives the rider a checked exit:
the claim, its acknowledged INTENT, the exit lease and both mapped observers. After ``save-all flush`` (the rocket at
altitude and the seated rider on disk) the case drives one exit to its phase, the way a client does, and kills the
server there. Two restarts with ``bayRegistryBlocked=true`` then show B10's REPAIR hold with:

* no exit state (W230 R-043: live slots, refusals and the UUID/T history are RAM only);
* T still PREPARED with the rider's seat, and the rocket's own manifest seat unchanged;
* the receipt file byte-equal to the cut, one pending charge, both packages unchanged, no ERROR or FATAL;
* the same durable outcome as the control case, which makes no exit.

The exit itself must leave the loaded-chunk count, the ticket map, T, the guard digest, the rocket snapshot, its fuel
and manifest, the receipts and the pending charge as they were. X4 (one granted descent: exactly one cancelled FALL
attempt) and X7 (a same-Level teleport revokes the offer: the landing is not protected) are the packaged smoke.
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
import zipfile

import run_v180_bay_forced_stop as bay

smoke = bay.smoke
RIDER = "00000000-0000-4000-8000-000000254001"
KV = re.compile(r"(\w+)=(\S+)")
SRW_PAUSED = re.compile(r"ARCE_RELEASE_SRW_PAUSED point=STAGED")
EXIT_LINE = re.compile(r"ARCE_BAY_EXIT logical=(\S+) transfer=(\S+) player=(\S+) seat=(-?\d+) outcome=(\w+) code=(\w+) candidate=(-?\d+)")
EXPIRED = re.compile(rf"ARCE_PASSENGER_RECONNECT_EXPIRED player={RIDER}")
UNAVAILABLE = "ARCE_BAY_EXIT observer unavailable"
HELD_RECEIPT = ("INTENT", "NONE")
DEADLINE_TICKS = 200  # W230 section 4.2: the fixed first-exit deadline
LANDING_DROP = 10  # BayPassengerExitReleaseTest.LANDING_DROP
FALL_CASES = ("OFFER", "DESCENT", "CORRECTION", "LANDING", "EXPIRY", "TRUSTED", "REVOKED")
# phase: the exit's state at the kill; mode: sneak (vanilla dismount), input (the sneak input packet) or menu (held LEAVE).
# then: a stimulus the fixture applies at the END of the exit's own tick, before vanilla's tracker correction and before
# the server-side player tick first moves the rider. X2, X4, X5 and X7 need the offer to survive later ticks.
CASES = {
    "X0_CONTROL": dict(phase="NONE"),
    "X1_OFFER": dict(phase="OFFER", mode="input"),
    "X2_DESCENT": dict(phase="DESCENT", mode="sneak", steps=3),
    "X3_CORRECTION": dict(phase="CORRECTION", mode="sneak"),
    "X4_LANDING": dict(phase="LANDING", mode="sneak"),
    "X5_EXPIRY": dict(phase="EXPIRY", mode="sneak"),
    "X6_TRUSTED": dict(phase="TRUSTED", mode="sneak", then="move"),
    "X7_REVOKED": dict(phase="REVOKED", mode="sneak"),
    "X8_SAFE_MENU": dict(phase="SAFE", mode="menu"),
    "X9_FIRST_TICK": dict(phase="FIRST_TICK", mode="sneak", then="first", smoke="GRANTED"),
    "X10_FIRST_TICK_REVOKED": dict(phase="FIRST_TICK_REVOKED", mode="sneak", then="first-revoked", smoke="REVOKED"),
}
STARTS_PER_CASE = 3


def fields(line: str, label: str) -> dict:
    """The key=value fields after ``ARCE_RELEASE_BAY_EXIT_<label>``; a later duplicate key never hides an earlier one."""
    marker = f"ARCE_RELEASE_BAY_EXIT_{label} "
    at = line.find(marker)
    if at < 0:
        raise smoke.SmokeError(f"Not an exit {label} line: {line!r}")
    parsed: dict = {}
    for key, value in KV.findall(line[at + len(marker):]):
        if key in parsed:
            raise smoke.SmokeError(f"Duplicate field {key} in {label}: {line!r}")
        parsed[key] = value
    return parsed


def pair(value: str) -> tuple[str, str]:
    parts = value.split(">")
    if len(parts) != 2:
        raise smoke.SmokeError(f"Not a before>after pair: {value!r}")
    return parts[0], parts[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise smoke.SmokeError(message)


def check_left(left: dict, case: str, origin_y: int) -> None:
    """One exit through the actual adapter: detached, one own correction, nothing loaded, no store or seat changed."""
    spec = CASES[case]
    before, after = (int(value) for value in pair(left["counter"]))
    chunks = pair(left["chunks"])
    require(left["detached"] == "true", f"{case}: the exit did not detach: {left}")
    require(after == before + 1, f"{case}: expected exactly the exit's own correction: {left['counter']}")
    require(chunks[0] == chunks[1], f"{case}: the exit changed the loaded-chunk count: {left['chunks']}")
    require(left["authority"] == "same", f"{case}: the exit changed T, the guard, the rocket, a receipt or the charge")
    if spec["mode"] != "input":  # the input exit is measured across the rest of its tick; recorded, not asserted
        require(left["tickets"] == "same", f"{case}: the exit added or changed a ticket: {left['tickets']}")
    require(left["mounted"] == "false" and left["live"] == "1" and left["histories"] == "1", f"{case}: no live slot or history: {left}")
    require(left["seat"] == "0" and left["rocketSeat"] == "0", f"{case}: the exit changed a seat: {left}")
    if spec["phase"] == "SAFE":
        require(left["slot"] == "SAFE" and left["offer"] == "none" and left["history"] == "closed", f"{case}: not a safe exit: {left}")
        require(float(left["y"]) == float(origin_y), f"{case}: the safe exit did not stand on the checked pad: {left['y']}")
    else:
        require(left["slot"] == "NO_SAFE_EXIT" and left["offer"] == "live" and left["history"] == "issued",
                f"{case}: no granted descent: {left}")
        require(int(left["deadline"]) == int(left["first"]) + DEADLINE_TICKS, f"{case}: the deadline is not first + 200: {left}")
        require(float(left["y"]) == float(pair(left["riderY"])[0]), f"{case}: the fallback moved the rider: {left}")


def check_outcome_line(lines: list[str], fixture: dict, case: str) -> dict:
    """The service's own bounded INFO outcome for this exit: exactly one, with the expected key."""
    found = [match.groups() for line in lines for match in [EXIT_LINE.search(line)] if match and match.group(1) == fixture["logical"]]
    expected = ("safe", "SAFE") if CASES[case]["phase"] == "SAFE" else ("fall_offer", "NO_SAFE_EXIT")
    require(len(found) == 1, f"{case}: expected one ARCE_BAY_EXIT outcome line, found {found}")
    row = found[0]
    require(row[1] == fixture["transfer"] and row[2] == RIDER and row[3] == "0" and row[4:6] == expected,
            f"{case}: unexpected exit outcome: {row}")
    return dict(outcome=row[4], code=row[5], candidate=int(row[6]))


def check_landed(landed: dict, case: str, granted: bool) -> None:
    """A granted descent cancels exactly one FALL attempt on the verified landing; a revoked one cancels none."""
    health = tuple(float(value) for value in pair(landed["landingHealth"]))
    require(pair(landed["chunks"])[0] == pair(landed["chunks"])[1] and landed["tickets"] == "same",
            f"{case}: the landing loaded a chunk or changed a ticket: {landed}")
    require(landed["authority"] == "same" and landed["onGround"] == "true", f"{case}: landing changed authority or missed the pad: {landed}")
    require(float(landed["fall"]) > 3, f"{case}: the landing fall was too short to test: {landed['fall']}")
    if granted:
        require(landed["attacks"] == "[fall:true]" and landed["cancelled"] == "1" and health == (20.0, 20.0),
                f"{case}: the granted landing was not exactly one cancelled FALL attempt: {landed}")
    else:
        require(landed["attacks"] == "[fall:false]" and landed["cancelled"] == "0" and health[1] < health[0],
                f"{case}: the revoked landing was protected: {landed}")
    require(landed["offer"] in ("closed", "none"), f"{case}: a landing left a live offer: {landed}")


def check_closed(state: dict, case: str, left: dict) -> None:
    """After expiry, a trusted move, a teleport or a landing: no live offer, the history and its deadline unchanged."""
    require(state["offer"] != "live", f"{case}: the offer is still live: {state}")
    require(state["history"] == "issued" and state["deadline"] == left["deadline"] and state["first"] == left["first"],
            f"{case}: the UUID/T history was renewed or lost: {state}")
    require(state["seat"] == "0" and state["rocketSeat"] == "0" and state["mounted"] == "false", f"{case}: seat or mount changed: {state}")


def check_restart_state(state: dict, case: str, restart: int, logged_in: bool) -> None:
    """No exit state survives a restart; T and the rocket keep the rider's seat; a new login gets no live grant or history."""
    require(state["service"] == "true" and state["live"] == "0" and state["refusals"] == "0" and state["histories"] == "0",
            f"{case}/{restart}: exit state survived the restart: {state}")
    require(state["slot"] == "none" and state["offer"] == "none" and state["history"] == "none", f"{case}/{restart}: exit slot: {state}")
    require(state["transfer"] == "PREPARED" and state["seat"] == "0" and state["rocketSeat"] == "0",
            f"{case}/{restart}: the held seat was lost or changed: {state}")
    require(state["rider"] == ("online" if logged_in else "offline") and state["mounted"] == "false",
            f"{case}/{restart}: unexpected rider presence or mount: {state}")
    require(state["observer"] == ("true" if logged_in else "false") and state["landing"] == "true",
            f"{case}/{restart}: the counter or landing observer is unavailable: {state}")


def held(status: dict, fixture: dict) -> bool:
    """B10's REPAIR hold: the acknowledged INTENT unchanged, T PREPARED, the source locked at altitude, packages unchanged."""
    data = [receipt for receipt in status["receipts"] if receipt["satellite"] == fixture["data"]]
    return (status["claim"] == "OUTCOMES" and status["hold"] == "REPAIR" and status["reason"] == "REGISTRY_BLOCKED"
            and status["locked"] == "true" and len(data) == 1 and (data[0]["phase"], data[0]["reason"]) == HELD_RECEIPT
            and data[0]["acknowledged"] and not data[0]["registered"] and status["transfer"] == "PREPARED"
            and status["state"] == "TRANSIT" and float(status["y"]) == float(bay.ORIGIN[1] + bay.ALTITUDE)
            and status["packages"] == fixture["packages"] and status["owned"] == 0
            and all(receipt["satellite"] == fixture["data"] for receipt in status["receipts"]))


def check_cut_status(status: dict, fixture: dict, case: str) -> None:
    """At the cut: the in-session claim waits for D2's registry proof with T in TRANSIT, its acknowledged INTENT and both
    packages unchanged. D2's registration exists in RAM (owned 1) while its registry write is paused at STAGED."""
    data = [receipt for receipt in status["receipts"] if receipt["satellite"] == fixture["data"]]
    require(status["claim"] == "OUTCOMES" and status["reason"] == "BAY_REGISTRY_PROOF" and status["transfer"] == "PREPARED"
            and status["state"] == "TRANSIT" and float(status["y"]) == float(bay.ORIGIN[1] + bay.ALTITUDE)
            and status["locked"] == "true" and len(data) == 1 and (data[0]["phase"], data[0]["reason"]) == HELD_RECEIPT
            and data[0]["acknowledged"] and data[0]["registered"] and len(status["receipts"]) == 1
            and status["packages"] == fixture["packages"] and status["consumed"] == [None, None] and status["owned"] == 1,
            f"{case}: the in-session hold is not the expected INTENT hold: {status}")


def check_status_unchanged(before: dict, after: dict, case: str) -> None:
    """The exit and its phase changed nothing the bay status reports: claim, receipts, T, rocket, packages, lock."""
    require(before == after, f"{case}: the bay status changed across the exit: {before} -> {after}")


def summary(status: dict, state: dict, pending: dict) -> dict:
    """The durable outcome of a restart without identities, for the comparison with the control case."""
    return dict(claim=status["claim"], hold=status["hold"], reason=status["reason"], transfer=status["transfer"],
                state=status["state"], y=status["y"], locked=status["locked"], owned=status["owned"],
                receipts=sorted((r["phase"], r["reason"], r["acknowledged"], r["registered"]) for r in status["receipts"]),
                seat=state["seat"], rocket_seat=state["rocketSeat"], live=state["live"], histories=state["histories"],
                pending=pending["pending"], retained=pending["retained"])


def compare_with_control(report_cases: list[dict]) -> dict:
    """Every exit case restarts to exactly the control's durable outcome: the exit and its cut changed nothing durable."""
    by_case = {row["case"]: row for row in report_cases if row.get("status") == "PASS"}
    control = by_case.get("X0_CONTROL")
    if control is None:
        return dict(status="NOT_RUN", reason="the control case did not pass")
    expected = [entry["summary"] for entry in control["restarts"]]
    differences = {case: [entry["summary"] for entry in row["restarts"]] for case, row in by_case.items()
                   if [entry["summary"] for entry in row["restarts"]] != expected}
    return dict(status="PASS" if not differences else "FAILED", control=expected, differences=differences,
                compared=sorted(by_case))


def jar_evidence(jar: Path) -> dict:
    """Hash, manifest and resource evidence of the tested JAR, and proof that it is the reobfuscated (SRG) build."""
    digest = lambda data: hashlib.sha256(data).hexdigest()
    prefix = "io/github/sunthemoon/advancedrocketrycommunity/rocket/server/"
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        classes = sorted(name for name in names if re.fullmatch(re.escape(prefix) + r"BayPassengerExit[\w$]*\.class", name))
        lang = sorted(name for name in names if re.fullmatch(r"assets/[\w.-]+/lang/(en_us|zh_cn)\.json", name)
                      and "bay_exit" in name)
        entries = {name: digest(archive.read(name)) for name in classes + lang + ["META-INF/MANIFEST.MF", "META-INF/mods.toml"]
                   if name in names}
        service = archive.read(prefix + "BayPassengerExitService.class") if prefix + "BayPassengerExitService.class" in names else b""
        motion = archive.read(prefix + "BayPassengerExitMotion.class") if prefix + "BayPassengerExitMotion.class" in names else b""
        landing = archive.read(prefix + "BayPassengerExitLanding.class") if prefix + "BayPassengerExitLanding.class" in names else b""
        keys = {}
        for name in lang:
            keys[name] = sorted(json.loads(archive.read(name).decode("utf-8")))
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") if "META-INF/MANIFEST.MF" in names else ""
    reobf = dict(service_teleport_srg=b"m_9774_" in service, service_teleport_named=b"teleport" in service,
                 motion_counter_srg=b"f_9767_" in motion,
                 landing_frames_srg=all(name in landing for name in (b"m_142535_", b"m_142072_", b"m_7840_", b"m_289599_", b"m_6469_")))
    return dict(path=str(jar), sha256=digest(jar.read_bytes()), size=jar.stat().st_size, entries=len(names),
                exit_classes=classes, lang=lang, lang_keys=keys, hashes=entries, manifest=manifest.splitlines(), reobf=reobf)


def check_jar(evidence: dict) -> None:
    reobf = evidence["reobf"]
    require(reobf["service_teleport_srg"] and not reobf["service_teleport_named"], "The JAR is not the reobfuscated build")
    require(reobf["motion_counter_srg"] and reobf["landing_frames_srg"], "The pinned observer names are missing from the JAR")
    require(len(evidence["lang"]) == 2 and all("message.advancedrocketrycommunity.bay_exit.fall_offer" in keys
                                               and "message.advancedrocketrycommunity.bay_exit.safe" in keys
                                               for keys in evidence["lang_keys"].values()),
            f"The bay exit language files are missing: {evidence['lang']}")


class Run(bay.Session):
    def exit(self, action: str, label: str, timeout: float = 60.0) -> dict:
        return fields(self.send(f"arce bay release-test exit {action}", re.compile(rf"ARCE_RELEASE_BAY_EXIT_{label} "), timeout), label)

    def state(self, logical: str) -> dict:
        return self.exit(f"state {logical}", "STATE")

    def poll(self, logical: str, done, what: str, timeout: float) -> dict:
        deadline = time.monotonic() + timeout
        while True:
            state = self.state(logical)
            if done(state):
                return state
            require(time.monotonic() < deadline, f"Timed out waiting for {what}: {state}")
            time.sleep(0.5)


def launch(run: Run) -> dict:
    run.send(f"forceload add {bay.ORIGIN[0]} {bay.ORIGIN[2]}", re.compile(r"[Mm]arked|already|forceload|chunk"))
    run.send("arce satellite release-test registry-writer arm STAGED PAUSE", bay.SRW_DONE)
    deadline = time.monotonic() + run.args.flight_timeout
    while True:
        line = run.send(f"arce bay release-test exit fixture {bay.ORIGIN[0]} {bay.ORIGIN[1]} {bay.ORIGIN[2]}",
                        re.compile(r"ARCE_RELEASE_BAY_EXIT_FIXTURE |ARCE_RELEASE_BAY_FIXTURE_WAIT\b"))
        if "ARCE_RELEASE_BAY_FIXTURE_WAIT" not in line:
            break
        require(time.monotonic() < deadline, "The fixture source did not become entity-ticking")
        time.sleep(1)
    rider = fields(line, "FIXTURE")
    assert run.process is not None
    fixture = bay.parse_fixture(next(line for line in reversed(run.process.lines) if bay.FIXTURE.search(line)))
    require(fixture["launch"] in ("SUCCESS", "JOURNAL_PENDING") and fixture["packages"].count("+") == 1,
            f"The fixture did not launch with two packaged bays: {fixture}")
    require(rider == dict(rider=RIDER, name="W254Rider", mounted="true", seat="0", mode="survival"),
            f"The survival rider did not board seat 0 before the launch: {rider}")
    return fixture


def phase(run: Run, case: str, fixture: dict) -> dict:
    """Drive the case's exit to its phase; returns what was observed. The caller kills the server afterwards."""
    spec, logical, observed = CASES[case], fixture["logical"], {}
    if spec["phase"] == "SAFE":
        observed["pad"] = run.exit(f"pad {logical} origin", "PAD")
    if spec["phase"] == "CORRECTION":
        left = run.exit(f"leave {logical} sneak freeze", "LEFT")
        assert run.process is not None
        frozen = fields(run.wait_after(re.compile(r"ARCE_RELEASE_BAY_EXIT_FROZEN "), 0, 30), "FROZEN")
        check_left(left, case, bay.ORIGIN[1])
        require(frozen["window"] == "open" and frozen["accepted"] == "false" and int(frozen["counter"]) == int(pair(left["counter"])[1]),
                f"{case}: not frozen inside the correction window before vanilla's step: {frozen}")
        return dict(left=left, frozen=frozen)
    assert run.process is not None
    lines = run.process.lines
    start = len(lines)
    left = run.exit(f"leave {logical} {spec['mode']}" + (f" {spec['then']}" if "then" in spec else ""), "LEFT")
    observed["left"] = left
    check_left(left, case, bay.ORIGIN[1])
    if spec["phase"] in ("FIRST_TICK", "FIRST_TICK_REVOKED"):
        granted = spec["phase"] == "FIRST_TICK"
        landed = fields(run.wait_after(re.compile(r"ARCE_RELEASE_BAY_EXIT_LANDED "), start, 30), "LANDED")
        observed["landed"] = landed
        require(landed["tick"] == left["tick"], f"{case}: the landing was not in the exit's own tick: {landed['tick']} {left['tick']}")
        check_landed(landed, case, granted)
        observed["closing"] = await_closing(run, logical, case, int(landed["tick"]), "the landing")
        if not granted:
            teleported = fields(run.wait_after(re.compile(r"ARCE_RELEASE_BAY_EXIT_TELEPORTED "), start, 30), "TELEPORTED")
            observed["teleported"] = teleported
            exit_counter = int(pair(left["counter"])[1])
            require(pair(teleported["counter"]) == (str(exit_counter), str(exit_counter + 2)),
                    f"{case}: expected two teleport counter steps after the exit's own: {teleported}")
            closing = observed["closing"]
            require(closing["window"].split(":")[0] == str(exit_counter) and closing["window"].endswith(":true")
                    and int(closing["counter"]) == exit_counter + 2,
                    f"{case}: the counter observer did not revoke the two-step change: {closing}")
        observed["after"] = run.poll(logical, lambda state: state["live"] == "0", "the landing's revocation", 10)
        check_closed(observed["after"], case, left)
    elif spec["phase"] == "DESCENT":
        descent = run.exit(f"descend {logical} {spec['steps']}", "DESCENT", 30)
        observed["descent"] = descent
        require(descent["stopped"] == "steps" and descent["sent"] == str(spec["steps"]) and descent["airborne"] == "true"
                and descent["offer"] == "live", f"{case}: the descent did not stay a live airborne offer: {descent} {closings(lines, logical)}")
        require(float(descent["y"]) <= float(left["y"]) - 3 * spec["steps"] + 1e-6, f"{case}: the rider did not descend: {descent}")
    elif spec["phase"] == "LANDING":
        observed["pad"] = run.exit(f"pad {logical} below", "PAD")
        run.exit(f"descend {logical} 0", "DESCEND")
        landed = fields(run.wait(re.compile(r"ARCE_RELEASE_BAY_EXIT_LANDED "), 30), "LANDED")
        observed["landed"] = landed
        check_landed(landed, case, True)
        observed["closing"] = await_closing(run, logical, case, int(landed["tick"]), "the landing")
        observed["after"] = run.poll(logical, lambda state: state["live"] == "0", "the landing's revocation", 10)
        check_closed(observed["after"], case, left)
    elif spec["phase"] == "EXPIRY":
        observed["after"] = run.poll(logical, lambda state: state["live"] == "0", "the deadline's revocation", 30)
        observed["closing"] = await_closing(run, logical, case, int(left["deadline"]), "the deadline")
        check_closed(observed["after"], case, left)
    elif spec["phase"] == "TRUSTED":
        # The move runs at the END of the exit's own tick, while the offer is live (LEFT shows it live).
        moved = fields(run.wait_after(re.compile(r"ARCE_RELEASE_BAY_EXIT_MOVED "), start, 30), "MOVED")
        observed["moved"] = moved
        require(moved["tick"] == left["tick"], f"{case}: the trusted move was not in the exit's own tick: {moved}")
        require(moved["remounted"] == "false" and moved["offer"] != "live" and moved["live"] == "0",
                f"{case}: the trusted move did not revoke before its refusal: {moved}")
        observed["closing"] = await_closing(run, logical, case, int(moved["tick"]), "the trusted move")
        require(observed["closing"]["slot"] == "removed", f"{case}: the move did not remove the live slot: {observed['closing']}")
        check_closed(moved, case, left)
    elif spec["phase"] == "REVOKED":
        time.sleep(0.5)  # beyond the correction window (start + 2 ticks): the teleport is a later counter step
        observed["before"] = require_live(run, logical, case)
        require(int(observed["before"]["tick"]) > int(left["first"]) + 2, f"{case}: still inside the correction window")
        teleported = run.exit(f"teleport {logical}", "TELEPORTED")
        observed["teleported"] = teleported
        before, after = (int(value) for value in pair(teleported["counter"]))
        require(after == before + 1, f"{case}: the teleport did not step the counter once: {teleported}")
        observed["revoked"] = run.poll(logical, lambda state: state["live"] == "0", "the teleport's revocation", 10)
        observed["closing"] = await_closing(run, logical, case, int(teleported["tick"]), "the teleport")
        check_closed(observed["revoked"], case, left)
        observed["pad"] = run.exit(f"pad {logical} below", "PAD")
        run.exit(f"descend {logical} 0", "DESCEND")
        landed = fields(run.wait(re.compile(r"ARCE_RELEASE_BAY_EXIT_LANDED "), 30), "LANDED")
        observed["landed"] = landed
        check_landed(landed, case, False)
    elif spec["phase"] == "OFFER":
        observed["after"] = require_live(run, logical, case)
    observed["trace"] = [line.split("ARCE_RELEASE_BAY_EXIT_TRACE ", 1)[1].strip() for line in lines if "ARCE_RELEASE_BAY_EXIT_TRACE " in line]
    observed["closings"] = closings(lines, logical)
    return observed


def closings(lines: list[str], logical: str) -> list[dict]:
    """The fixture's read-only reports of the exit service ending the tracked offer, with the observation that ended it."""
    return [fields(line, "CLOSED") for line in lines if "ARCE_RELEASE_BAY_EXIT_CLOSED " in line and f"logical={logical} " in line]


def check_closing(lines: list[str], logical: str, case: str, earliest: int, stimulus: str) -> dict:
    """Exactly one end of the offer, and not before its named stimulus: nothing else ended it first."""
    found = closings(lines, logical)
    require(len(found) == 1, f"{case}: expected one end of the offer, found {found}")
    require(int(found[0]["tick"]) >= earliest, f"{case}: the offer ended at tick {found[0]['tick']}, before {stimulus} at {earliest}: {found[0]}")
    return found[0]


def await_closing(run: Run, logical: str, case: str, earliest: int, stimulus: str) -> dict:
    """The fixture reports the end at the exit service's next observation point; wait for it, then check it."""
    assert run.process is not None
    run.wait_after(re.compile(rf"ARCE_RELEASE_BAY_EXIT_CLOSED logical={re.escape(logical)} "), 0, 15)
    return check_closing(run.process.lines, logical, case, earliest, stimulus)


def require_live(run: Run, logical: str, case: str) -> dict:
    """The offer must still be live when the case applies its named stimulus or cut."""
    assert run.process is not None
    state = run.state(logical)
    require(state["offer"] == "live" and state["live"] == "1",
            f"{case}: the offer ended before the named stimulus: {state} {closings(run.process.lines, logical)}")
    return state


def first_start(run: Run, case: str, world: Path) -> tuple[dict, dict]:
    process = run.start(case, 0, {})
    observed: dict = {}
    fixture = launch(run)
    run.wait(SRW_PAUSED, run.args.flight_timeout)
    deadline = time.monotonic() + run.args.settle_timeout
    while True:
        ready = fields(run.send(f"arce bay release-test exit ready {fixture['logical']}", re.compile(r"ARCE_RELEASE_BAY_EXIT_READY ")),
                       "READY")
        if ready["ready"] == "true":
            break
        require(time.monotonic() < deadline, f"{case}: the held exit never became ready: {ready}")
        time.sleep(1)
    observed["ready"] = ready
    observed["saved"] = run.send("save-all flush", bay.SAVED, 120).strip()
    observed["baseline"] = run.state(fixture["logical"])
    observed["at_cut_status"] = run.status(fixture["logical"])
    check_cut_status(observed["at_cut_status"], fixture, case)
    observed["pending"] = run.pending()
    require(observed["pending"]["pending"] == 1, f"{case}: the INTENT hold does not hold one charge: {observed['pending']}")
    if CASES[case]["phase"] != "NONE":
        observed.update(phase(run, case, fixture))
        observed["outcome"] = check_outcome_line(process.lines, fixture, case)
    if CASES[case]["phase"] != "CORRECTION":  # a frozen server answers nothing more
        observed["at_cut"] = run.state(fixture["logical"])
        observed["at_cut_status_after"] = run.status(fixture["logical"])
        check_status_unchanged(observed["at_cut_status"], observed["at_cut_status_after"], case)
    require(not any(UNAVAILABLE in line for line in process.lines), f"{case}: a pinned observer was unavailable")
    run.end("kill", run.args.exit_timeout)
    return fixture, observed


def run_case(run: Run, case: str) -> dict:
    world = run.session / ("world-" + case)
    properties = smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": "world-" + case, "level-type": "minecraft:flat",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")
    row = dict(case=case, spec=CASES[case])
    fixture, row["first_start"] = first_start(run, case, world)
    row["fixture"] = fixture
    receipts = world / "data" / bay.RECEIPTS
    require(receipts.is_file(), f"{case}: no receipt file at the cut")
    row["cut_disk"] = dict(receipts=bay.digest(receipts))
    row["restarts"] = []
    for restart in (1, 2):
        process = run.start(case, restart, {"bayRegistryBlocked": "true"})
        deadline = time.monotonic() + run.args.settle_timeout
        while True:
            status = run.status(fixture["logical"])
            if held(status, fixture):
                break
            require(time.monotonic() < deadline, f"{case}/{restart}: never reached the REPAIR hold: {status}")
            time.sleep(1)
        entry = dict(restart=restart, status=status)
        entry["state"] = run.state(fixture["logical"])
        check_restart_state(entry["state"], case, restart, False)
        if restart == 1:
            entry["login"] = run.exit(f"login {fixture['logical']}", "LOGIN")
            check_restart_state(entry["login"], case, restart, True)
            assert run.process is not None
            try:  # the existing held-login queue keeps the seat and expires after 200 ticks; recorded, not required
                entry["login_expired"] = run.wait_after(EXPIRED, 0, 20).strip()
            except smoke.SmokeError:
                entry["login_expired"] = None
            entry["after_login"] = run.state(fixture["logical"])
            check_restart_state(entry["after_login"], case, restart, True)
        entry["pending"] = run.pending()
        require(entry["pending"]["pending"] == 1, f"{case}/{restart}: the pending charge changed: {entry['pending']}")
        entry["summary"] = summary(status, entry["state"], entry["pending"])
        code = run.end("stop", run.args.exit_timeout)
        require(code == 0 and not any("/ERROR]" in line or "/FATAL]" in line for line in process.lines),
                f"{case}/{restart}: the restart failed or logged ERROR/FATAL")
        require(not any(EXIT_LINE.search(line) for line in process.lines), f"{case}/{restart}: a restart reported an exit")
        entry.update(exit_code=code, receipts=bay.digest(receipts))
        require(entry["receipts"] == row["cut_disk"]["receipts"], f"{case}/{restart}: the held receipt file changed")
        row["restarts"].append(entry)
    row["status"] = "PASS"
    return row


def session_root(root: Path) -> Path:
    """An explicit existing, non-linked parent confines this run's disposable server and its cleanup."""
    if any(smoke.is_link_or_junction(path) for path in [root, *root.parents]) or not root.is_dir():
        raise smoke.SmokeError("Missing or linked disposable session root")
    return root.resolve()


def run(args) -> dict:
    bay.assert_pin(args.source_commit)
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    intended = session_root(args.session_root)
    if session.parent != intended or session.exists() or smoke.is_link_or_junction(session):
        raise smoke.SmokeError("Unsafe or occupied disposable native directory")
    if evidence.exists() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Evidence must be new and outside the disposable directory")
    libraries = args.source_server.resolve() / "libraries"
    if not libraries.is_dir() or any(smoke.is_link_or_junction(path) for path in [libraries, *libraries.rglob("*")]):
        raise smoke.SmokeError("Missing or unsafe Forge libraries")
    cases = args.case or list(CASES)
    if len(cases) * STARTS_PER_CASE > 60:
        raise smoke.SmokeError("Tranche exceeds 60 counted starts")
    jar = jar_evidence(args.jar)
    check_jar(jar)
    session.mkdir(); evidence.mkdir(parents=True)
    token = uuid.uuid4().hex
    marker = session / ".arce-w254-owned"
    marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar=jar, lock_owner=args.lock_owner,
                  counted_starts_planned=len(cases) * STARTS_PER_CASE, cases=[])
    runner = Run(args, session, evidence)
    try:
        shutil.copytree(libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        runner.port = smoke.allocate_port()
        smoke.write_server_configuration(session, runner.port, True)
        for case in cases:
            report["in_progress"] = case
            try:
                report["cases"].append(run_case(runner, case))
                print(f"{case}: PASS (exit cut, two restarts)", flush=True)
            except smoke.SmokeError as failure:
                if runner.process is not None:
                    runner.process.abort(); runner.process = None
                report["cases"].append(dict(case=case, status="FAILED", error=str(failure)))
                print(f"{case}: FAIL {failure}", flush=True)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            shutil.rmtree(session / ("world-" + case), ignore_errors=True)
        report.pop("in_progress", None)
        bay.assert_pin(args.source_commit)
        report["control"] = compare_with_control(report["cases"])
        passed = all(row["status"] == "PASS" for row in report["cases"])
        report["status"] = "PASS" if passed and report["control"]["status"] != "FAILED" else "FAILED"
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
    parser.add_argument("--session-root", type=Path, default=Path("D:/GitHub/ARCE-Task-Evidence/Temp"),
                        help="Existing non-linked parent for the new disposable session directory")
    for key in ("source-commit", "java", "lock-owner"):
        parser.add_argument("--" + key, required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    for key, default in (("startup-timeout", 240), ("exit-timeout", 180), ("flight-timeout", 120), ("settle-timeout", 180)):
        parser.add_argument("--" + key, type=float, default=default)
    args = parser.parse_args()
    try:
        return 0 if run(args)["status"] == "PASS" else 1
    except (OSError, smoke.SmokeError) as failure:
        print(f"FAIL: {failure}", flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
