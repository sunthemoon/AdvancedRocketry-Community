#!/usr/bin/env python3
"""Measure bay native waits with vanilla game-time brackets on a supplied, pinned JAR."""
from __future__ import annotations

import json
import re
import time

import loader_cut_nbt as nbt
import run_v180_bay_forced_stop as bay
import w336_bay_timing as timing
import w336_native as native

CASES = ("B12_D4", "B12_D7", "B13_FAILED", "B13_UNKNOWN", "B16_MOTION", "B10_BLOCKED", "RECONNECT", "TURNS")


def configure(run, world):
    properties = native.smoke.expected_server_properties(run.port, True)
    properties.update({"level-name": world, "level-type": "minecraft:flat", "view-distance": "2", "simulation-distance": "2",
                       "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}],
                                                         "biome": "minecraft:plains"})})
    (run.session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(properties.items())), encoding="ascii")


def reconnect(lines):
    login = [i for i, line in enumerate(lines) if "ARCE_RELEASE_BAY_EXIT_LOGIN " in line]
    expired = [i for i, line in enumerate(lines) if "ARCE_PASSENGER_RECONNECT_EXPIRED " in line]
    native.require(len(login) == len(expired) == 1, "Missing/repeated reconnect login or expiry")
    interval = timing.elapsed(timing.bracket(lines, login[0]), timing.bracket(lines, expired[0]))
    native.require(interval[0] <= 200 <= interval[1], "Reconnect expiry does not bracket 200 ticks")
    return dict(login=timing.bracket(lines, login[0]), expired=timing.bracket(lines, expired[0]), elapsed_ticks=interval)


def standard(run, session):
    case = run.args.case
    if case == "RECONNECT":
        case = "W336_RECONNECT"
        # Compose the existing blocked-restart and online-login fixtures. Their assertions stay intact.
        bay.CASES[case] = dict(bay.CASES["B10_BLOCKED"], online=True)
    session.hold_window = run.args.window_ticks if case in ("B10_BLOCKED", "W336_RECONNECT") else 0
    session.pause_window = run.args.window_ticks if case == "B16_MOTION" else 0
    result = bay.run_case(session, case)
    measured = [timing.observations(lines) for lines in session.timed_starts]
    if case == "W336_RECONNECT":
        result["reconnect_ticks"] = [reconnect(lines) for lines in session.timed_starts[1:]]
    if case.startswith("B13"):
        result["registry_retry_ticks"] = timing.retry_spacing(session.timed_starts[0], "ARCE_RELEASE_SRW_INJECTED ")
    return dict(case=result, measured_starts=measured, upper_bound="NOT_ESTABLISHED")


def fixture(session, at):
    session.send(f"forceload add {at[0]} {at[2]}", re.compile("[Mm]arked|already|forceload|chunk"))
    deadline = time.monotonic() + session.args.flight_timeout
    while True:
        line = session.send("arce bay release-test fixture " + " ".join(map(str, at)),
                            re.compile(bay.FIXTURE.pattern + "|ARCE_RELEASE_BAY_FIXTURE_WAIT"))
        if bay.FIXTURE.search(line):
            result = bay.parse_fixture(line)
            native.require(result["launch"] in ("SUCCESS", "JOURNAL_PENDING"), "Gated fixture launch refused")
            return result
        native.require(time.monotonic() < deadline, "Timing source did not become entity-ticking")
        time.sleep(0.25)


def poll_tickets(run, session, slots):
    """Observe every census column once per second; unrelated tickets may outlive a claim."""
    rows = []
    deadline = time.monotonic() + run.args.ticket_timeout
    while True:
        before = session.query_tick()
        run.process = session.process
        try:
            loaded = [run.loaded((x * 16, 80, z * 16), dimension) for dimension, x, z in slots]
        finally:
            run.process = None
        after = session.query_tick()
        rows.append(dict(ticks=[before, after], loaded=loaded))
        if not any(loaded):
            return dict(columns=slots, polls=rows, all_unloaded=True)
        native.require(time.monotonic() < deadline, "Census columns still loaded in the ticket observation window")
        time.sleep(1)


def turns(run, session):
    results = []
    for offset in (8, 15):
        world = run.session / f"world-turns-{offset}"
        configure(run, world.name)
        session.start(f"turns-{offset}", 0, {"bayCheckpoint": "B6_D5"})
        fixtures = [fixture(session, (742000 + offset + 64 * index, 80, 742008)) for index in range(run.args.claims)]
        for row in fixtures:
            deadline = time.monotonic() + run.args.flight_timeout
            while session.status(row["logical"])["claim"] != "PROVE":
                native.require(time.monotonic() < deadline, "Not every claim reached the D6 gate")
                time.sleep(0.1)
        session.send("forceload remove all", re.compile("[Uu]nmarked|No chunks"))
        session.send("save-all flush", native.SAVED, 180)
        session.end("stop", run.args.exit_timeout)
        data = nbt.plain(nbt.read_gzip(world / "data/advancedrocketrycommunity_rocket_transfers.dat")[1])["data"]
        records = data["transfers"]
        native.require(len(records) == run.args.claims, "D6 seed has a different number of transfers")
        sizes = {len(timing.census_slots(row)) for row in records}
        native.require(len(sizes) == 1, "Claims do not share one census size")
        slots = [column for row in records for column in timing.census_slots(row)]
        seed = run.capture(world, f"turns-{offset}-seed")
        session.start(f"turns-{offset}", 1, dict(rocketJournalFailure="HOLD", rocketJournalHoldStartup="true"))
        session.wait(bay.JOURNAL_HELD, 60)
        session.query_tick()
        session.send("arce rocket release-test journal-hold-release", bay.JOURNAL_RELEASED)
        for row in fixtures:
            session.wait(re.compile(rf"ARCE_BAY_CLAIM .*logical={row['logical']} state=READY_SOURCE"), run.args.settle_timeout)
        proof = timing.proof_turns(session.process.lines, [row["logical"] for row in fixtures], sizes.pop())
        tickets = poll_tickets(run, session, list(dict.fromkeys(slots)))
        session.end("stop", run.args.exit_timeout)
        # The second restart is independent of the proof run and checks settled package/receipt invariants.
        session.start(f"turns-{offset}", 2, {})
        settled = [session.await_settled("B6_D5", 2, row) for row in fixtures]
        session.end("stop", run.args.exit_timeout)
        results.append(dict(offset=offset, seed=seed, proof=proof, tickets=tickets, restart2=settled,
                            raw=run.capture(world, f"turns-{offset}-restart2")))
    native.require(len({row["proof"][0]["census_slots"] for row in results}) == 2, "Two census sizes were not exercised")
    return dict(turns=results, measured_starts=[timing.observations(lines) for lines in session.timed_starts])


def family(run):
    args = run.args
    session = timing.TimedSession(args, run.session, run.evidence)
    session.port = run.port
    try:
        return turns(run, session) if args.case == "TURNS" else standard(run, session)
    finally:
        # Native's single owning finally terminates a failed adapter process before cleanup.
        run.process = session.process


def main():
    parser = native.parser(__doc__)
    parser.add_argument("--case", choices=CASES, required=True)
    parser.add_argument("--claims", type=int, choices=range(2, 5), default=2)
    parser.add_argument("--window-ticks", type=int, default=240)
    parser.add_argument("--ticket-timeout", type=float, default=120)
    parser.add_argument("--flight-timeout", type=float, default=120)
    parser.add_argument("--settle-timeout", type=float, default=180)
    args = parser.parse_args()
    native.require(args.window_ticks >= 240, "Observation window must include reconnect expiry")
    return native.execute(args, family)


if __name__ == "__main__":
    raise SystemExit(main())
