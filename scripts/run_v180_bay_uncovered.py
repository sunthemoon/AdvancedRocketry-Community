#!/usr/bin/env python3
"""W358 native bay gap cases, and W363's B17.3 cases. Local smokes are not candidate evidence.

Uses the forced-stop runner's candidate pin, installed-JAR checks, lock assertion,
bounded processes and disposable-world cleanup. No existing case is reselected.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import time
import uuid

import bay_integrated_evidence as evidence
import run_v180_bay_forced_stop as bay
import run_v180_bay_released_flight as released

CASES = {
    "W358_B6_BOUND": ("B6.8",),
    "W358_B10_INCOHERENT": ("B10.6",),
    "W358_B11_REARM": ("B11.8",),
    "W358_B11_ESCROW_MAJOR": ("B11.9",),
    "W358_B11_BAY_MAJOR": ("B11.9",),
    "W358_B11_UNKNOWN": ("B11.7",),
    "W358_CAP_OWNER": ("CAP.3",),
    # W363: a restart group whose end stays unready (the gated bayEndUnready seam), ahead of a healthy in-session
    # flight, and ahead of a healthy restart group.
    # W363: W358_B6_BOUND's flow; the reason is required in the launching session and, the runtime rejection map being
    # RAM only (W128), required absent after each restart. W358_B6_BOUND's own oracle is unchanged.
    "W363_B6_GOVERNED": ("B6.8",),
    "W363_B17_UNLOADED": ("B17.3",),
    "W363_B17_UNLOADED_RESTART": ("B17.3",),
}
# W363 (Root decision R451): W358_B6_BOUND is not a campaign tranche. Its oracle requires the runtime reason after a
# restart, which contradicts W128 (the rejection map is RAM only), and it never passed. W363_B6_GOVERNED is B6.8's
# evidence. The case and its fixture stay selectable with --case.
TRANCHES = {case: [case] for case in CASES if case != "W358_B6_BOUND"}
HEALTHY_ORIGIN = (40, 2, 8)  # two chunks east of the forced-stop fixture's origin
B6 = ("W358_B6_BOUND", "W363_B6_GOVERNED")
END_HELD = "ARCE_RELEASE_BAY_END_HELD transfer={transfer} logical={logical}"
CONTROL = "arce bay release-test w358 "
LOADER = re.compile(r"ARCE_W358_LOADER logical=\S+ action=\w+ .*|ARCE_W358_LOADER_WAIT logical=\S+")
WRITER = re.compile(r"ARCE_W358_WRITER .*")


def fields(line, marker):
    if marker not in line:
        raise bay.smoke.SmokeError("Missing probe marker: " + line)
    return dict(re.findall(r"(\w+)=(\S+)", line.split(marker, 1)[1]))


def require(condition, message):
    if not condition:
        raise bay.smoke.SmokeError(message)


def until(run, read, predicate, description):
    end = time.monotonic() + run.args.settle_timeout
    while True:
        value = read()
        if predicate(value):
            return value
        if time.monotonic() >= end:
            raise bay.smoke.SmokeError(f"{description}: {value}")
        time.sleep(0.5)


def idle(run, pending=None):
    return until(run, run.pending, lambda v: not v["work"] and (pending is None or v["pending"] == pending), "Store did not settle")


def owner_fill(run, count):
    line = run.send(CONTROL + f"owner-fill {count}", re.compile(r"ARCE_W358_FILL requested="))
    require(fields(line, "ARCE_W358_FILL ") == dict(requested=str(count), queued=str(count)), "Owner fill was refused: " + line)
    return idle(run)


def escrow_seed(run, count):
    line = run.send(CONTROL + f"escrow-seed {count}", re.compile(r"ARCE_W358_ESCROW_SEED "))
    value = fields(line, "ARCE_W358_ESCROW_SEED ")
    # The command verifies the checked file before attaching its real startup index. This is not live admission.
    require(value["count"] == str(count) and value["durable"] == "true", "Typed escrow seed failed: " + line)
    return value


def parked(run):
    run.send("forceload add 8 8", re.compile(r"[Mm]arked|already|forceload|chunk"))
    line = until(run, lambda: run.send("arce bay release-test fixture 8 2 8 parked",
                 re.compile(bay.FIXTURE.pattern + r"|ARCE_RELEASE_BAY_FIXTURE_WAIT")),
                 lambda v: "_WAIT" not in v, "Parked fixture never became ticking")
    fixture = bay.parse_fixture(line)
    require(fixture["launch"] == "PARKED" and fixture["packages"].count("+") == 1, "Bad parked fixture")
    return fixture


def loader(run, fixture, action):
    line = until(run, lambda: run.send(CONTROL + f"loader {fixture['logical']} {action}", LOADER),
                 lambda v: "_WAIT" not in v, "Loader journal never became ready")
    return fields(line, "ARCE_W358_LOADER ")


def check_refusal(value, reason):
    require(value["captured"] == "false" and value["status"] == reason and value["unchanged"] == "true",
            "Loader refusal captured or changed native state/counts: " + repr(value))


def writer(run):
    return fields(run.send(CONTROL + "writer status", WRITER), "ARCE_W358_WRITER ")


def check_full(value, escrow):
    require(value["pending"] == "64" and value["escrow"] == str(escrow)
            and value["bay"] == value["fluid"] == "SERVER_FULL", "Mixed consumer admission is not full: " + repr(value))


def check_unknown(value):
    check_full(value, 62)
    require(value["unknown"] == value["successor"] == "true" and value["captured"] == "1"
            and value["retained"] == "2", "Writer copies lost a slot or counted one twice: " + repr(value))


def launch(run, fixture):
    accepted = []
    def attempt():
        transfer = str(uuid.uuid4())
        line = run.send(CONTROL + f"launch {fixture['logical']} {transfer}", re.compile(r"ARCE_W358_LAUNCH "))
        result = fields(line, "ARCE_W358_LAUNCH ")["result"]
        require(result in ("SUCCESS", "JOURNAL_PENDING", "DESTINATION_LOADING"), "Launch fixture refused: " + line)
        if result != "DESTINATION_LOADING":
            accepted.append(transfer)
        return result
    # DESTINATION_LOADING is a completed refusal in the request ledger. A retry needs a fresh request ID.
    until(run, attempt, lambda v: v in ("SUCCESS", "JOURNAL_PENDING"), "Destination warm-up never completed")
    return accepted[0]


def unchanged(status, fixture):
    require(status["packages"] == fixture["packages"] and status["consumed"] == [None, None] and status["owned"] == 0,
            "Package, consumption or registry changed: " + repr(status))


def landed(run, fixture):
    return until(run, lambda: run.status(fixture["logical"]), lambda v: v["claim"] == "NONE" and v["state"] == "LANDED", "No landing")


def deployment(run, fixture):
    reports = []
    for satellite in (fixture["data"], fixture["solar"]):
        line = run.send(f"arce bay release-test deployment-status {satellite}", bay.DEPLOYMENT_STATUS)
        value = fields(line, "ARCE_RELEASE_BAY_DEPLOYMENT_STATUS ")
        reports.append(value)
    return reports


def frozen(run, fixture, op):
    samples = []
    for _ in range(5):
        status = run.status(fixture["logical"])
        unchanged(status, fixture)
        require(status["claim"] != "NONE" and status["state"] == "TRANSIT" and status["transfer"] == "PREPARED"
                and len(status["receipts"]) == 1 and status["receipts"][0]["op"] == op
                and status["receipts"][0]["phase"] == "REPAIR" and status["receipts"][0]["acknowledged"],
                "Incoherent authority escaped its hold: " + repr(status))
        require(released.flags(run, fixture["logical"]) == [[op, "REPAIR", "false", "true"]], "Incoherent receipt released")
        samples.append(status)
        time.sleep(1)
    return samples


def launch_at(run, origin):
    """The standard two-bay fixture flight from another origin, so two rockets share one world."""
    run.send(f"forceload add {origin[0]} {origin[2]}", re.compile(r"[Mm]arked|already|forceload|chunk"))
    line = until(run, lambda: run.send(f"arce bay release-test fixture {origin[0]} {origin[1]} {origin[2]}",
                 re.compile(bay.FIXTURE.pattern + r"|ARCE_RELEASE_BAY_FIXTURE_WAIT")),
                 lambda v: "_WAIT" not in v, "Healthy fixture origin never became ticking")
    fixture = bay.parse_fixture(line)
    require(fixture["launch"] in ("SUCCESS", "JOURNAL_PENDING") and fixture["packages"].count("+") == 1,
            "The healthy fixture did not launch with two packaged bays: " + repr(fixture))
    return fixture


def check_end_held(status, receipts):
    """The unloaded group: still at R2's END_LOADING wait, not held for repair, its durable receipts unchanged."""
    require(status["claim"] == "RESUME" and status["hold"] == "OWNED_WAIT" and status["reason"] == "END_LOADING"
            and status["transfer"] == "PREPARED" and status["receipts"] == receipts and len(receipts) == 1
            and receipts[0]["phase"] == "REGISTERED" and receipts[0]["acknowledged"]
            and all(value is None for value in status["consumed"]),
            "The unloaded group left its END_LOADING wait or changed: " + repr(status))


def check_deployed(status, owned):
    """A healthy group's end: landed, its data bay consumed, its receipts retired, the owner's satellites counted."""
    require(status["state"] == "LANDED" and status["claim"] == "NONE" and status["receipts"] == []
            and len(status["consumed"]) == 2 and status["consumed"][0] is not None and status["consumed"][1] is None
            and status["owned"] == owned, "A healthy group did not deploy and land: " + repr(status))


def await_end_held(run, fixture):
    line = run.wait(re.compile(re.escape(END_HELD.format(**fixture)) + r"\b"), run.args.settle_timeout)
    status = until(run, lambda: run.status(fixture["logical"]), lambda v: v["reason"] == "END_LOADING", "Seam did not hold")
    return line.strip(), status


def healthy_while_held(run, held, healthy, receipts, owned):
    """The healthy group must finish while every sample of the unloaded group shows it still waiting, unchanged."""
    samples, end = [], time.monotonic() + run.args.flight_timeout + run.args.settle_timeout
    while True:
        waiting = run.status(held["logical"]); check_end_held(waiting, receipts); samples.append(waiting)
        status = run.status(healthy["logical"])
        if status["state"] == "LANDED" and status["claim"] == "NONE":
            break
        require(time.monotonic() < end, "The healthy group made no bounded progress behind the unloaded group: "
                + repr(dict(healthy=status, unloaded=waiting)))
        time.sleep(1)
    check_deployed(status, owned)
    for _ in range(5):
        waiting = run.status(held["logical"]); check_end_held(waiting, receipts); samples.append(waiting); time.sleep(1)
    return dict(healthy=status, unloaded_samples=len(samples), unloaded_last=samples[-1])


def run_b17(run, case):
    """B17.3. First start: B3_MOVED's REGISTERED group U, then a hard stop. U restarts with its end held unready."""
    world = run.session / ("world-" + case)
    props = released.server_properties(run.port, world.name)
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
    row = dict(case=case, rows=CASES[case], first={}, restarts=[])
    try:
        unloaded, row["first"]["seed"] = bay.first_start(run, "B3_MOVED", world)
        row["fixture"] = unloaded
        row["cut_raw"] = raw(run, world, case, "cut")
        hold = dict(bayEndUnready=unloaded["logical"])
        start = 1
        if case == "W363_B17_UNLOADED_RESTART":
            # A second forced stop: the healthy rocket H reaches its own REGISTERED group while U is held, so the next
            # start has two restart groups, U first in receipt order.
            process = run.start(case, start, dict(hold, bayCheckpoint="B3_MOVED"))
            setup = dict(held=await_end_held(run, unloaded))
            receipts = setup["held"][1]["receipts"]
            healthy = launch_at(run, HEALTHY_ORIGIN)
            run.wait(re.compile(r"ARCE_RELEASE_BAY_PAUSED cut=B3_MOVED op="), run.args.flight_timeout)
            setup["healthy_at_cut"] = run.status(healthy["logical"])
            require(setup["healthy_at_cut"]["claim"] != "NONE" and setup["healthy_at_cut"]["state"] == "TRANSIT",
                    "The second cut did not hold the healthy rocket in TRANSIT")
            check_end_held(run.status(unloaded["logical"]), receipts)
            stop(run, process, kill=True)
            setup["raw"] = raw(run, world, case, "cut2")
            on_disk = evidence.bay_receipts(world / "data" / bay.RECEIPTS)
            require(sorted(r["rocket"] for r in on_disk if r["phase"] == "REGISTERED")
                    == sorted([unloaded["logical"], healthy["logical"]]), "Two REGISTERED restart groups are not on disk")
            require([r["rocket"] for r in on_disk][0] == unloaded["logical"], "The unloaded group is not first in receipt order")
            row["first"]["setup"] = setup; row["healthy"] = healthy; start = 2
        # Restart 1: U's end is held unready; the healthy group must still finish.
        process = run.start(case, start, hold)
        entry = dict(restart=1, held=await_end_held(run, unloaded))
        receipts = entry["held"][1]["receipts"]
        if case == "W363_B17_UNLOADED":
            healthy = launch_at(run, HEALTHY_ORIGIN); row["healthy"] = healthy
            lines = process.lines
            require(next(i for i, line in enumerate(lines) if "ARCE_RELEASE_BAY_END_HELD" in line)
                    < next(i for i, line in enumerate(lines) if healthy["logical"] in line),
                    "The unloaded group was not ahead of the healthy group")
        entry.update(healthy_while_held(run, unloaded, healthy, receipts, 2))
        run.send("save-all flush", bay.SAVED, 120)
        stop(run, process)
        entry["raw"] = raw(run, world, case, "1")
        row["restarts"].append(entry)
        # Restart 2: no hold; U resumes and deploys, and H is unchanged.
        process = run.start(case, start + 1, {})
        entry = dict(restart=2)
        entry["unloaded"] = until(run, lambda: run.status(unloaded["logical"]),
                                  lambda v: v["state"] == "LANDED" and v["claim"] == "NONE", "The released group never landed")
        check_deployed(entry["unloaded"], 2)
        entry["healthy"] = until(run, lambda: run.status(healthy["logical"]), lambda v: v["rocket"] != "NONE", "Healthy rocket absent")
        check_deployed(entry["healthy"], 2)
        require(entry["healthy"]["consumed"] == row["restarts"][0]["healthy"]["consumed"]
                and entry["healthy"]["packages"] == row["restarts"][0]["healthy"]["packages"], "The healthy rocket changed on restart 2")
        require(not any("ARCE_RELEASE_BAY_END_HELD" in line for line in process.lines), "The seam held without its property")
        stop(run, process)
        entry["raw"] = raw(run, world, case, "2")
        row["restarts"].append(entry)
    finally:
        # Keep what was observed even when the oracle fails.
        (run.evidence / f"{case}-observed.json").write_text(json.dumps(row, indent=2, default=str), encoding="utf-8")
    row["status"] = "PASS"
    return row


def raw(run, world, case, label):
    return evidence.snapshot_world(world, run.evidence / f"{case}-{label}-raw")


def stop(run, process, kill=False):
    code = run.end("kill" if kill else "stop", run.args.exit_timeout)
    require(kill or code == 0, f"Server exited {code}")
    require(not evidence.unexpected_errors(process.lines), "Unexpected server ERROR/FATAL")
    return code


def run_case(run, case):
    world = run.session / ("world-" + case)
    props = released.server_properties(run.port, world.name)
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
    row = dict(case=case, rows=CASES[case], first={}, restarts=[])
    if case == "W358_B11_BAY_MAJOR":
        row["seed"] = evidence.seed_capacity_world(world, 63)
    if case == "W358_CAP_OWNER":
        row["seed"] = evidence.seed_capacity_world(world, 16, "00000000-0000-4000-8000-000000128d01")
    if case == "W358_B10_INCOHERENT":
        fixture, row["seed"] = bay.first_start(run, "B18_INTENT", world)
        process = run.start(case, 0, dict(bayRegistryBlocked="true"))
        op = run.status(fixture["logical"])["receipts"][0]["op"]
        run.send(f"arce bay release-test seed-repair {fixture['logical']} incoherent",
                 re.compile(r"ARCE_RELEASE_BAY_REPAIR logical=\S+ outcome=COMMITTED"))
        released.ready(run, fixture["logical"])
        row["first"]["frozen"] = frozen(run, fixture, op)
    elif case == "W358_B11_REARM":
        process = run.start(case, 0, dict(bayCheckpoint="B9_MOVED", bayRefuseRegistration="true"))
        fixture = bay.launch_fixture(run)
        run.wait(re.compile(r"ARCE_RELEASE_BAY_PAUSED cut=B9_MOVED op="), run.args.flight_timeout)
        op = run.status(fixture["logical"])["receipts"][0]["op"]
    elif case == "W358_B11_ESCROW_MAJOR":
        process = run.start(case, 0, dict(bayCheckpoint="B1_MOVED"))
        row["first"]["escrow"] = escrow_seed(run, 63)
        fixture = bay.launch_fixture(run)
        run.wait(re.compile(r"ARCE_RELEASE_BAY_PAUSED cut=B1_MOVED op="), run.args.flight_timeout)
        row["first"]["full"] = writer(run); check_full(row["first"]["full"], 63)
    else:
        process = run.start(case, 0, {})
        if case == "W358_B11_BAY_MAJOR":
            row["first"]["escrow"] = escrow_seed(run, 1)
            row["first"]["full"] = writer(run); check_full(row["first"]["full"], 1)
        elif case == "W358_B11_UNKNOWN":
            row["first"]["escrow"] = escrow_seed(run, 62)
            owner_fill(run, 1)
            run.send(CONTROL + "writer arm", WRITER)
            until(run, lambda: writer(run), lambda v: v["unknown"] == "true", "UNKNOWN never held")
            filled = bay.FILLED.search(run.send("arce bay release-test fill 1", bay.FILLED))
            require(filled.groups() == ("1", "1"), "UNKNOWN successor not queued")
            row["first"]["unknown"] = []
            # Cross a normal 200-tick retry; UNKNOWN, captured and successor must coexist throughout.
            for _ in range(13):
                value = writer(run); check_unknown(value)
                row["first"]["unknown"].append(value); time.sleep(1)
        fixture = parked(run)
        row["first"]["loader_created"] = loader(run, fixture, "create")
        if case in ("W358_CAP_OWNER", "W358_B11_BAY_MAJOR", "W358_B11_UNKNOWN"):
            value = loader(run, fixture, "visit"); check_refusal(value, "OWNER_PENDING_FULL" if case == "W358_CAP_OWNER" else "ESCROW_FULL")
            row["first"]["loader_refused"] = value
        if case in B6:
            value = loader(run, fixture, "visit")
            require(value["captured"] == "true" and value["status"] == "PREPARING", "Real loader fill did not start")
            bound = until(run, lambda: loader(run, fixture, "status"), lambda v: v["ledger"] != "null" and v["epoch"] == "1"
                          and v["markers"] == v["records"] == v["tank"] == "0" and v["idle"] == "true", "Real fill did not retire")
            row["first"]["bound"] = bound
            launch(run, fixture)
            unchanged(landed(run, fixture), fixture)
            row["first"]["menu"] = deployment(run, fixture)
            if case == "W363_B6_GOVERNED":
                row["first"]["refused"] = run.wait(re.compile(r"ARCE_BAY_GOVERNED_REFUSED transfer=\S+ logical="
                    + fixture["logical"] + r" reason=BAY_SOURCE_PROTECTED bays=2\b"), 10).strip()
                require(not any(bay.CLAIM.search(line) and fixture["logical"] in line for line in process.lines),
                        "The governed refusal started a bay claim")
    row["fixture"] = fixture
    # Paused writer cases already have their intended durable target; do not invoke a drain before their hard kill.
    if case not in ("W358_B11_REARM", "W358_B11_ESCROW_MAJOR"):
        run.send("save-all flush", bay.SAVED, 120)
    stop(run, process, kill=True)
    row["cut_raw"] = raw(run, world, case, "cut")
    before_repair = [r for r in evidence.bay_receipts(world / "data" / bay.RECEIPTS) if r["rocket"] == fixture["logical"]]
    if case == "W358_B10_INCOHERENT":
        require(len(before_repair) == 1 and before_repair[0]["phase"] == "REPAIR" and not before_repair[0]["released"], "No frozen REPAIR on disk")
    for restart in (1, 2):
        process = run.start(case, restart, {})
        entry = dict(restart=restart)
        if case == "W358_B6_BOUND":
            # W363: this probe throws once the landed flight's record is retired, so the wait can time out. The new
            # case does not use it; its landed rocket is awaited below.
            released.ready(run, fixture["logical"])
        if case == "W358_B10_INCOHERENT":
            released.ready(run, fixture["logical"])
            entry["frozen"] = frozen(run, fixture, op)
        elif case == "W358_B11_REARM" and restart == 1:
            entry["rejected"] = run.await_settled("W325_B9_RETRY", 1, fixture)
            require(entry["rejected"]["receipts"][0]["op"] == op, "Rejected op changed at restart")
            owner_fill(run, 16)
            entry["full_admission"] = writer(run)
            require(entry["full_admission"]["pending"] == "16" and entry["full_admission"]["owner"] == "OWNER_PENDING_FULL",
                    "Rearm owner capacity was not full: " + repr(entry["full_admission"]))
            launch(run, fixture)
            entry["full_relaunch"] = landed(run, fixture); unchanged(entry["full_relaunch"], fixture)
            require(entry["full_relaunch"]["receipts"] == entry["rejected"]["receipts"], "Refused rearm changed its receipt")
            # Existing REJECTED receipt reason has menu priority; the authority above is the admission refusal.
            run.send(CONTROL + "unfill", re.compile(r"ARCE_W358_UNFILL requested=16 queued=16")); idle(run, 0)
            launch(run, fixture)
            entry["reused"] = until(run, lambda: run.status(fixture["logical"]),
                                     lambda v: v["state"] == "LANDED" and v["claim"] == "NONE" and v["owned"] == 1, "Rearm never consumed")
            require(entry["reused"]["consumed"] == [op, None], "Successful rearm did not reuse rejected operation")
        elif case == "W358_B11_REARM":
            entry["status"] = until(run, lambda: run.status(fixture["logical"]), lambda v: v["rocket"] != "NONE", "Rocket absent")
            require(entry["status"]["consumed"] == [op, None] and entry["status"]["owned"] == 1, "Rearm duplicated/lost identity on restart 2")
        elif case == "W358_B11_ESCROW_MAJOR":
            entry["status"] = run.await_settled("B1_MOVED", 1, fixture)
            require(run.pending()["pending"] == 63, "Rejected bay did not release its exact slot")
        else:
            entry["status"] = until(run, lambda: run.status(fixture["logical"]), lambda v: v["rocket"] != "NONE", "Rocket absent")
            unchanged(entry["status"], fixture)
            if case in B6:
                entry["menu"] = deployment(run, fixture)
            elif case == "W358_CAP_OWNER":
                entry["loader"] = loader(run, fixture, "visit"); check_refusal(entry["loader"], "OWNER_PENDING_FULL")
            elif case == "W358_B11_BAY_MAJOR":
                entry["full"] = writer(run); check_full(entry["full"], 1)
            elif case == "W358_B11_UNKNOWN":
                idle(run, 63)
                require(writer(run)["escrow"] == "62", "Restart lost escrow reservation")
        stop(run, process)
        entry["raw"] = raw(run, world, case, str(restart))
        if case == "W358_B10_INCOHERENT":
            require(evidence.bay_receipts(world / "data" / bay.RECEIPTS) == before_repair, "Frozen signature authority changed on disk")
        row["restarts"].append(entry)
    if case == "W358_B6_BOUND":
        # Preserve the actual governed-flight observation even when the requested legacy D0 is unreachable.
        (run.evidence / f"{case}-observed.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        reports = row["first"]["menu"] + [menu for entry in row["restarts"] for menu in entry["menu"]]
        require(all(value["reason"] == "BAY_SOURCE_PROTECTED" for value in reports),
                "B6.8 was not reached by the real bound flight; see the observed report")
    if case == "W363_B6_GOVERNED":
        (run.evidence / f"{case}-observed.json").write_text(json.dumps(row, indent=2), encoding="utf-8")
        check_governed_menus(row["first"]["menu"], [entry["menu"] for entry in row["restarts"]])
    row["status"] = "PASS"
    return row


def check_governed_menus(first, restarts):
    """D0's refusal in the launching session; no receipt and no remembered reason after a restart (RAM only)."""
    refused = dict(phase="NONE", reason="BAY_SOURCE_PROTECTED")
    require(len(first) == 2 and all(dict(phase=v["phase"], reason=v["reason"]) == refused for v in first),
            "The governed flight's bays do not show BAY_SOURCE_PROTECTED: " + repr(first))
    require(len(restarts) == 2 and all(len(menu) == 2 and all(v["phase"] == v["reason"] == "NONE" for v in menu)
            for menu in restarts), "A restart shows a receipt or a remembered runtime reason: " + repr(restarts))


def run(args):
    return bay.run(args, {case: run_b17 if case.startswith("W363_B17_") else run_case for case in CASES}, TRANCHES)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); bay.add_candidate_arguments(parser)
    parser.add_argument("--java", required=True); parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--tranche", choices=list(TRANCHES))
    parser.add_argument("--stop-on-failure", action="store_true")
    for key, default in (("startup-timeout", 240), ("exit-timeout", 180), ("flight-timeout", 120), ("settle-timeout", 180)):
        parser.add_argument("--" + key, type=float, default=default)
    args = parser.parse_args()
    if not args.case and not args.tranche:
        args.case = list(CASES)
    try:
        return 0 if run(args)["status"] == "PASS" else 1
    except (OSError, bay.smoke.SmokeError, evidence.CandidateError) as failure:
        print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
