"""K12 cargo and queue fixtures on C6; no client, no product change and no NBT write.

Five fixtures come from C6's own gated release-test commands (the ones its campaign families
use) and from K04's sealed rockets: a bay proof-turn queue, the isolated wait-23 world, the
R-042 cargo cells, a mixed bay and cargo world, and the first-event measurement source.
Invoke one `run --fixture ... --stage ...` per WORKER-RULES lock hold. A source world is never
opened by a server: every stage runs a disposable copy. Windows figures are driver
qualifications, never REF evidence. The log readers are pure functions, so a REF driver can
use the same code on Debian.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.campaign_observe import raw_ticks
from scripts.campaign_prep import k16_core as common

OW, MOON = "minecraft:overworld", common.NS + ":moon"
OUTPUT = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K12"
K04 = common.EVIDENCE / "final-campaign/c8fa16e16/fixtures/K04"
LOCK = common.EVIDENCE / "locks/heavy-jvm"
LABEL = "WINDOWS_DRIVER_QUALIFICATION_NOT_REF"
PROGRESSION = "data/advancedrocketrycommunity_classic_progression.dat"
JOURNAL = "data/advancedrocketrycommunity_rocket_transfers.dat"
CELL_AREA = "0 0 111 31"  # run_v180_r042_native.FORCELOAD: the three R-042 cells
BAY_OWNER = "00000000-0000-4000-8000-000000128d01"  # BayReleaseTestCommands.OWNER
CELL_OWNERS = {name: f"00000000-0000-4000-8000-00000045400{n}" for n, name in enumerate("ABS", 1)}
KINDS = ("FUEL", "FLUID_LOAD", "FLUID_UNLOAD", "ITEM_LOAD", "ITEM_UNLOAD")
# (rocket, device) amounts before and after exactly one transfer (R042AdmissionProbe's fixture shapes).
BEFORE = dict(FUEL=(0, 1000), FLUID_LOAD=(0, 5000), FLUID_UNLOAD=(5000, 0), ITEM_LOAD=(60, 10), ITEM_UNLOAD=(10, 60))
AFTER = dict(FUEL=(1000, 0), FLUID_LOAD=(1000, 4000), FLUID_UNLOAD=(4000, 1000), ITEM_LOAD=(64, 6), ITEM_UNLOAD=(6, 64))

# C6's own bounds: (file under the Java root, constant, value). recheck_bounds reads the source again.
BOUNDS = {
    "pending_server": ("resourceescrow/EscrowLimits.java", "OPERATIONS", 64),
    "pending_rocket": ("resourceescrow/EscrowLimits.java", "PER_ROCKET", 8),
    "pending_owner": ("resourceescrow/EscrowLimits.java", "PER_OWNER", 16),
    "cargo_per_rocket": ("resourceescrow/EscrowSettlementCapacity.java", "CARGO_ADMISSION_PER_ROCKET", 1),
    "queue_capacity": ("resourceescrow/EscrowResourceQueue.java", "CAPACITY", 256),
    "queue_visits_per_tick": ("resourceescrow/EscrowResourceQueue.java", "VISITS_PER_TICK", 8),
    "queue_commits_per_tick": ("resourceescrow/EscrowResourceQueue.java", "COMMITS_PER_TICK", 64),
    "cell_capture_ticks": ("resourceescrow/R042AdmissionProbe.java", "CAPTURE_TICKS", 1200),
    "bays_per_rocket": ("rocket/bay/deployment/BayClaim.java", "BAYS_PER_ROCKET", 8),
    "claim_book": ("rocket/bay/deployment/BayClaimBook.java", "LIMIT", 256),
    "bay_steps_per_tick": ("rocket/bay/deployment/BayStepQueue.java", "STEPS_PER_TICK", 32),
    "census_columns_per_end": ("rocket/flight/RocketFlightLimits.java", "MAX_LANDING_CHUNKS", 16),
    "ticket_timeout_ticks": ("rocket/flight/RocketFlightLimits.java", "FLIGHT_TICKET_TIMEOUT_TICKS", 400),
    "first_event_retry_ticks": ("progression/classic/firstevent/ClassicFirstEventDelivery.java", "RETRY_TICKS", 200),
    "first_event_cursor_per_tick": ("progression/classic/firstevent/ClassicFirstEventDelivery.java", "CURSOR_PER_TICK", 16),
    "deferred_per_tick": ("progression/classic/firstevent/FirstEventGrantQueue.java", "PER_TICK", 16),
    "deferred_recheck_ticks": ("progression/classic/firstevent/FirstEventGrantQueue.java", "RECHECK_TICKS", 20),
}
CELLS_SOURCE = ("resourceescrow/R042AdmissionProbe.java", 'CELLS = List.of("A", "B", "S")')
FIXTURE_BAYS = 2  # BayReleaseTestCommands.fixture: one data bay and one solar bay per fixture rocket


def recheck_bounds(root=common.ROOT):
    """Every pinned bound still equals the constant in C6's source (src/main is frozen at C6)."""
    base = Path(root) / common.JAVA_ROOT
    for key, (file, name, value) in BOUNDS.items():
        match = re.search(rf"\b{name}\s*=\s*([0-9_]+)L?\s*[;,]", (base / file).read_text(encoding="utf-8"))
        if match is None or int(match[1].replace("_", "")) != value:
            raise ValueError(f"C6 bound differs: {key} ({file} {name})")
    if CELLS_SOURCE[1] not in (base / CELLS_SOURCE[0]).read_text(encoding="utf-8"):
        raise ValueError("C6's R-042 cell list differs")
    return {key: value for key, (_, _, value) in BOUNDS.items()}


def claim_origins(count, corner=True):
    """Fixture origins 64 blocks apart, far from spawn (run_v180_w336_timing's base).

    A corner origin (15, 15 in its column) makes the rocket's checked margin cross both
    column edges: four source columns, against W336's one or two.
    """
    offset = 15 if corner else 8
    return [[742000 + offset + 64 * index, 80, 742000 + offset] for index in range(count)]


def source_columns(origin):
    """BayCensusColumns for the fixture rocket's source end: blocks at x-1..x+1, margin two blocks."""
    x, _, z = origin
    return [[OW, cx, cz] for cx in range((x - 3) >> 4, ((x + 3) >> 4) + 1)
            for cz in range((z - 2) >> 4, ((z + 2) >> 4) + 1)]


def census_slots(record):
    """Each end's origin column, then both ends' rectangles, from a stopped journal record.

    The same arithmetic as scripts/w336_bay_timing.census_slots, kept here so the REF host needs
    no Windows-only runner module.
    """
    ends = [record[key] for key in ("source_snapshot", "destination_snapshot")]
    slots = []
    for end in ends:
        origin = end["source_origin"]
        slot = [end["source_dimension"], origin[0] >> 4, origin[2] >> 4]
        if slot not in slots:
            slots.append(slot)
    for end in ends:
        x, _, z = end["source_origin"]
        a, _, c, d, _, f = end["bounding_box"]
        columns = [[end["source_dimension"], cx, cz]
                   for cx in range((x + a - 2) >> 4, ((x + d + 2) >> 4) + 1)
                   for cz in range((z + c - 2) >> 4, ((z + f + 2) >> 4) + 1)]
        if not 1 <= len(columns) <= BOUNDS["census_columns_per_end"][2]:
            raise ValueError("Census end exceeds its production bound")
        slots.extend(slot for slot in columns if slot not in slots)
    return slots


def plan():
    """The fixtures, with every queue size derived from BOUNDS and its reason."""
    owner, server = BOUNDS["pending_owner"][2], BOUNDS["pending_server"][2]
    claims = owner // FIXTURE_BAYS
    cells = dict(names=list(CELL_OWNERS), owners=CELL_OWNERS, kinds=list(KINDS), area=CELL_AREA,
                 operations=len(CELL_OWNERS) * len(KINDS),
                 size_reason="C6 packages three R-042 cells (A, B, S); each holds one rocket and one real device per "
                             "kind, so 12 cargo operations and 3 fuel fills can be requested together, under the "
                             f"pool of {server} and {owner} per owner (5 per cell owner).")
    mixed_claims = claims // 2
    return {
        "bay-queue": dict(rows=["J05", "J09"], parent=None, cut="B6_D5", claims=claim_origins(claims),
                          size_reason=f"The gated fixture has one fixed owner and {FIXTURE_BAYS} bays per rocket; "
                                      f"{owner} pending per owner allows {claims} claims. Corner origins give each "
                                      "claim four source columns. C6's TURNS families ran 2 and 3 claims of 2 and 3 slots."),
        "wait23": dict(rows=["J05"], parent=None, cut="B6_D5", claims=claim_origins(1, corner=False),
                       size_reason="One claim and nothing else: no player, no forced chunk, no device, no second rocket."),
        "cargo-cells": dict(rows=["J08", "J09"], parent="2048", cells=cells,
                            size_reason=cells["size_reason"]),
        "mixed": dict(rows=["J09", "J05"], parent="2048", cut="B6_D5", cells=cells, claims=claim_origins(mixed_claims),
                      size_reason=f"All three cells with {mixed_claims} bay claims: {mixed_claims * FIXTURE_BAYS} bay "
                                  f"receipts plus {cells['operations']} device operations stay under {server}."),
        "first-event": dict(rows=["J11"], parent="64",
                            size_reason="K04's fuelled 64-block rocket on a world with no progression root: the first "
                                        "Moon landing is the first write of the checked receipt barrier."),
    }


NOT_COVERED = {
    "J05 34-slot census and 64 claims": "The gated fixture rocket is three blocks wide with one fixed owner, so a census "
        "has at most four source columns and a queue at most eight claims. A wider census needs a real rocket with "
        "packaged bays, which needs a player to insert the packages (R638 forbids writing them).",
    "J05 waits 1 to 9, 13 to 16 and 18 to 22": "They are C6's ten timing profiles and forced-stop families (C6 report 9.2). "
        "They run unchanged on REF through scripts/run_v180_w336_timing.py; K12 adds no fixture for them.",
    "J08 steps 1 to 4": "The largest admitted and oversize typed fixtures and the 64 synthetic records are built by "
        "CargoNativeFixtures, a test-only class that C6's release JAR does not contain (K04 found the same).",
    "J09 step 4": "No packaged command starts an F1, W98 or sealed-disassembly capture against the same quota "
        "without a player or a second flight request.",
    "J10 geometry worlds": "The four checked-margin rockets in two terrain cohorts are not built. cold_columns "
        "qualifies a destination from the region files alone; the capacity cases need connected players.",
    "J11 participant": "The qualification launches through the gated hook with nobody aboard (participants=0). An "
        "eligible player's product-route launch belongs to the REF sitting.",
}

# ---- Log reading (pure) -----------------------------------------------------------------------------------------

GAMETIME = re.compile(r"\]: The time is (\d+)\s*$")
FIXTURE = re.compile(r"ARCE_RELEASE_BAY_FIXTURE logical=(\S+) transfer=(\S+) rocket=(\S+) data=(\S+) solar=(\S+) "
                     r"launch=(\w+) packages=(\S*)")
STATUS = re.compile(r"ARCE_RELEASE_BAY_STATUS logical=(\S+) claim=(\w+) hold=(\w+) reason=(\w+) receipts=\[([^\]]*)\] "
                    r"transfer=(\w+) rocket=(\S+) state=(\w+) y=(\S+) packages=(\S*) locked=(\w+) consumed=(\S+) "
                    r"owned=(-?\d+)")
PENDING = re.compile(r"ARCE_RELEASE_BAY_PENDING pending=(\d+) retained=(\d+) work=(true|false)")
CLAIM = re.compile(r"ARCE_BAY_CLAIM transfer=(\S+) logical=(\S+) state=(\w+) reason=(\w+)")
CELL = re.compile(r"ARCE_RELEASE_R042_CELL cell=(\w+) kind=(\w+) owner=(\S+) logical=(\S+) rocket=(\S+) device=(\S+)")
ATTEMPT = re.compile(r"ARCE_RELEASE_R042_ATTEMPT cell=(\w+) kind=(\w+) found=true status=(\w+) admitted=(true|false) "
                     r"unchanged=(true|false) pending=(\d+)>(\d+)")
REPORT = re.compile(r"ARCE_RELEASE_R042_REPORT cell=(\w+) kind=(\w+) found=true rocket=(-?\d+) device=(-?\d+) "
                    r"rocketEpoch=(\d+) deviceEpoch=(\d+) rocketMarkers=(\d+) deviceMarkers=(\d+) locked=(true|false) "
                    r"protected=(true|false) refusal=(\w+) watching=(true|false) logical=(\S+)")
GATE_CLOSED = re.compile(r"ARCE_RELEASE_R042_GATE_CLOSED cell=(\w+) kind=(\w+) reason=(\w+) tick=(\d+)")
ELECTED = re.compile(r"ARCE_FIRST_EVENT_ELECTED kind=first_moon_landing event=(\S+) transfer=(\S+) participants=(\d+)")
LANDED = re.compile(r"ARCE_TRANSFER_PHASE transfer=(\S+) .*event=landing_complete entity=(\S+) ")
JOURNAL_HELD = "ARCE_RELEASE_JOURNAL_STARTUP_HELD"
JOURNAL_RELEASED = "ARCE_RELEASE_JOURNAL_STARTUP_RELEASED"
REJECTED = ("Unknown block", "Unknown item", "Incorrect argument", "Expected whitespace", "does not have property",
            "Unknown or incomplete command")


def bracket(lines, index):
    """The vanilla game-time replies on either side of line `index`; the event lies between them."""
    before = after = None
    for at, line in enumerate(lines):
        match = GAMETIME.search(line)
        if match is None:
            continue
        if at < index:
            before = int(match[1])
        elif at > index:
            after = int(match[1])
            break
    if before is None or after is None or after < before:
        raise ValueError("Event has no enclosing game-time queries")
    return [before, after]


def elapsed(start, end):
    """[least, most] ticks from one bracketed event to a later one."""
    if not (start[0] <= start[1] and end[0] <= end[1] and start[0] <= end[1]):
        raise ValueError("Invalid tick brackets")
    return [max(0, end[0] - start[1]), end[1] - start[0]]


def parse_status(line):
    match = STATUS.search(line)
    if match is None:
        raise ValueError("No bay status line")
    names = ("logical", "claim", "hold", "reason", "receipts", "transfer", "rocket", "state", "y", "packages", "locked",
             "consumed", "owned")
    status = dict(zip(names, match.groups()))
    status["receipts"] = sorted(filter(None, status["receipts"].split(",")))
    return status


def durable_status(status):
    """What a restart must keep. Receipt rows drop their fifth field, the session's own acknowledgement,
    which is false until the restarted session reads the store. The rocket, its state and the claim's
    stage depend on which chunks are loaded and on the session, so they are informational."""
    rows = []
    for row in status["receipts"]:
        parts = row.split(":")
        if len(parts) != 6:
            raise ValueError("Malformed receipt row: " + row)
        rows.append(":".join(parts[:4] + parts[5:]))
    return dict(logical=status["logical"], receipts=rows, transfer=status["transfer"], locked=status["locked"],
                owned=status["owned"])


def claim_waits(lines, logicals):
    """Raw D6 waits per claim (J05 waits 10 to 12) and the whole queue's wait; no bound is implied."""
    rows = []
    for logical in logicals:
        claims = [(i, m) for i, line in enumerate(lines) if (m := CLAIM.search(line)) and m[2] == logical]
        ready = [i for i, m in claims if m[4] == "READY_SOURCE"]
        waiting = [i for i, m in claims if m[4] == "BAY_PROOF_TURN"]
        proving = [i for i, m in claims if m[4] == "BAY_SOURCE_PROOF"]
        row = dict(logical=logical, queued=bool(waiting), finished=len(ready) == 1,
                   reasons=[m[4] for _, m in claims])
        first = waiting[0] if waiting else proving[0] if proving else None
        if first is not None:
            row["first"] = bracket(lines, first)
        if row["finished"] and first is not None and first < ready[0]:
            row["ready"] = bracket(lines, ready[0])
            row["wait_through_ready_ticks"] = elapsed(row["first"], row["ready"])
            service = next((i for i in proving if i >= first), first)
            row["turn_ticks"] = elapsed(bracket(lines, service), row["ready"])
        else:
            row["unfinished_reason"] = "no single READY_SOURCE after a D6 observation in this window"
        rows.append(row)
    done = [row for row in rows if "ready" in row]
    queue = None
    if done and len(done) == len(rows):
        queue = elapsed(min((row["first"] for row in rows), key=lambda b: b[0]),
                        max((row["ready"] for row in rows), key=lambda b: b[1]))
    return dict(unit="server_game_ticks", claims=rows, whole_queue_ticks=queue,
                order=[row["logical"] for row in sorted(done, key=lambda row: row["ready"][0])])


def parse_cells(lines):
    cells = {}
    for line in lines:
        if match := CELL.search(line):
            cells.setdefault(match[1], {})[match[2]] = dict(owner=match[3], logical=match[4], rocket=match[5], device=match[6])
    return cells


def parse_reports(lines):
    """The last report row of each cell and kind."""
    rows = {}
    for line in lines:
        if match := REPORT.search(line):
            rows.setdefault(match[1], {})[match[2]] = dict(
                rocket=int(match[3]), device=int(match[4]), rocket_epoch=int(match[5]), device_epoch=int(match[6]),
                rocket_markers=int(match[7]), device_markers=int(match[8]), locked=match[9] == "true",
                protected=match[10] == "true", refusal=match[11], watching=match[12] == "true", logical=match[13])
    return rows


def completed(row, kind):
    """Exactly one transfer settled: after-amounts, both epochs 1, no marker, switch closed."""
    return ((row["rocket"], row["device"]) == AFTER[kind] and row["rocket_epoch"] == row["device_epoch"] == 1
            and row["rocket_markers"] == row["device_markers"] == 0 and not row["watching"])


def untouched(row, kind):
    return ((row["rocket"], row["device"]) == BEFORE[kind] and row["rocket_epoch"] == row["device_epoch"] == 0
            and row["rocket_markers"] == row["device_markers"] == 0)


def cargo_cycles(lines):
    """Per device: the attempt, the capture (cargo kinds only) and the first report that shows it
    settled, as game-time brackets. Request to settled is the cycle; unfinished rows stay explicit."""
    rows = {}
    for index, line in enumerate(lines):
        if match := ATTEMPT.search(line):
            rows[(match[1], match[2])] = dict(cell=match[1], kind=match[2], status=match[3], admitted=match[4] == "true",
                                              pending=[int(match[6]), int(match[7])], requested=bracket(lines, index))
        elif match := GATE_CLOSED.search(line):
            row = rows.get((match[1], match[2]))
            if row is not None and "captured" not in row:
                row.update(captured=bracket(lines, index), capture_reason=match[3])
        elif match := REPORT.search(line):
            row = rows.get((match[1], match[2]))
            if row is not None and "settled" not in row and completed(parse_reports([line])[match[1]][match[2]], match[2]):
                row["settled"] = bracket(lines, index)
    for row in rows.values():
        if "settled" in row:
            row["request_to_settled_ticks"] = elapsed(row["requested"], row["settled"])
        else:
            row["unfinished_reason"] = "refused" if not row["admitted"] else "not settled inside this window"
    return dict(unit="server_game_ticks", devices=[rows[key] for key in sorted(rows)],
                note="A settled bracket's upper end is the first poll that saw it, so the cycle's upper figure "
                     "includes one report interval.")


def tick_offset(pairs):
    """Game time minus the server tick counter, as [least, most], from (before, raw tick, after) triples."""
    if not pairs:
        raise ValueError("No raw poll was bracketed")
    low = max(before - tick for before, tick, _ in pairs)
    high = min(after - tick for _, tick, after in pairs)
    if low > high + 2:
        raise ValueError("Raw polls disagree on the tick offset")
    return [min(low, high), max(low, high)]


def ticks_in_bracket(merged, offset, event):
    """The raw samples whose tick can hold an event bracketed in game time; never a fabricated sample."""
    first, last = event[0] - offset[1] - 1, event[1] - offset[0] + 1
    return [dict(tick=tick, ms=nanos / 1_000_000) for tick, nanos in merged["samples"] if first <= tick <= last]


def raw_summary(raw_lines, threshold_ms=500.0):
    polls = [raw_ticks.parse_raw_tick_reply(line) for line in raw_lines]
    summary = raw_ticks.summarize_raw_ticks(polls, threshold_ms)
    return polls, summary


def first_event_observation(lines, raw_lines, pairs):
    """J11 step 3: the election's bracket and the whole raw ticks that can contain it."""
    elected = [(i, m) for i, line in enumerate(lines) if (m := ELECTED.search(line))]
    if len(elected) != 1:
        raise ValueError("Expected exactly one first Moon landing election")
    index, match = elected[0]
    polls, summary = raw_summary(raw_lines)
    event, offset = bracket(lines, index), tick_offset(pairs)
    candidates = ticks_in_bracket(raw_ticks.merge_polls(polls), offset, event)
    return dict(event=match[1], transfer=match[2], participants=int(match[3]), election_game_ticks=event,
                tick_offset=offset, candidate_ticks=candidates,
                largest_candidate_ms=max((row["ms"] for row in candidates), default=None),
                basis="The first-event tick is one of the candidates; the bracket cannot name which. The receipt "
                      "barrier runs at that tick's END or a following one, inside the same candidates.",
                window=summary)


def ticket_series(rows, end):
    """Wait 23: when the census columns were last seen loaded and first seen all unloaded after `end`."""
    loaded = [row for row in rows if any(row["loaded"])]
    clear = next((row for row in rows if not any(row["loaded"]) and row["ticks"][0] >= end[1]), None)
    result = dict(claim_end=end, polls=len(rows), last_loaded=loaded[-1]["ticks"] if loaded else None,
                  all_unloaded=clear["ticks"] if clear else None, raw_rows=rows)
    if clear is None:
        result["unfinished_reason"] = "columns still loaded when the window closed"
    else:
        result["end_to_unloaded_ticks"] = elapsed(end, clear["ticks"])
    return result


def cold_columns(world, dimension, columns):
    """J10 step 1 without a selector: each column's chunk record in the region header, read only.

    `new_terrain` has no record; `generated` has one. A forced column disqualifies both.
    """
    folder = Path(world) if dimension == OW else Path(world) / "dimensions" / dimension.replace(":", "/")
    rows = []
    for cx, cz in columns:
        region = folder / "region" / f"r.{cx >> 5}.{cz >> 5}.mca"
        present = False
        if region.is_file() and region.stat().st_size >= 4096:
            with region.open("rb") as stream:
                stream.seek(4 * ((cx & 31) + (cz & 31) * 32))
                present = stream.read(4) != b"\0\0\0\0"
        rows.append(dict(column=[cx, cz], cohort="generated" if present else "new_terrain"))
    return rows


def advancement_done(text, advancement):
    """The saved player advancement JSON's own answer for one advancement (J17's file oracle)."""
    entry = json.loads(text).get(advancement)
    return bool(entry) and entry.get("done") is True and bool(entry.get("criteria"))


# ---- J17: the fourteen recipes ------------------------------------------------------------------------------------

ADVANCEMENTS = dict(moon=common.NS + ":classic/one_small_step", station=common.NS + ":classic/first_station_warp")
DELIVERY = "io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.ClassicFirstEventDelivery"
STOPS = {
    "W_BASELINE": dict(thread="ARCE-rocket-journal (W)", where="RocketJournalFailurePolicy / CheckedSavedDataPolicy committer "
                       "call for the startup baseline generation, before the move",
                       how="Suspend only thread ARCE-rocket-journal at the committer's atomic move of the first generation "
                           "after `Done`; the server thread keeps ticking. Never queue save-all while W is suspended."),
    "S_GRANT": dict(thread="Server thread (S)", where=DELIVERY + ".grantNow line 486 (the return after TRIGGER.trigger at 485)",
                    how="Breakpoint at line 486 with the condition that `done(player, kind)` is true for A; all threads "
                        "stop at the hit. DELIVERED has not been requested: requestDelivered (line 316) runs only "
                        "after the cursor pass ends."),
    "S_DEFER": dict(thread="Server thread (S)", where=DELIVERY + ".onLogin line 437 (defer after grantNow returned false)",
                    how="Breakpoint at line 437 for A's UUID; record `queue.size(kind)` after stepping over it."),
    "S_LOGOUT": dict(thread="Server thread (S)", where=DELIVERY + ".onLogout line 444 (queue.removeConnection)",
                     how="Breakpoint at line 444 for A's UUID; step over it and record that `queue.size(kind)` fell."),
}
PLAYER_SAVE = dict(official="net.minecraft.server.players.PlayerList#saveAll()", runtime="PlayerList.m_11302_()V",
                   how="Evaluate `server.getPlayerList().m_11302_()` in the paused S frame; record its return and the "
                       "SHA-256 of world/advancements/<A>.json. The runtime name is the one C6's own classes call "
                       "(javap of RocketRejoinOverlapGameTests in the C6 JAR).")


def _recipe(variant, kind, stop, setup, action, restart1, restart2, save=None):
    return dict(variant=variant, kind=kind, advancement=ADVANCEMENTS[kind], stop=stop, setup=setup, action=action,
                player_save_before_stop=save, restart_1=restart1, restart_2=restart2)


def recipes():
    """J17's fourteen variants as external-stop recipes; none sets the first-event cut property."""
    t0 = "T0: retained real Moon event E and receipt R(E), winner legitimately disassembled, transfers=[]"
    held = "Restart 1 again suspends W at W_BASELINE; A logs in and is deferred; resume W; A is awarded in that session " \
           "with no reconnect and no new flight; save; the receipt and acknowledgement finish with zero records."
    stable = "Same E and R(E), A's saved criterion complete, B negative, no new winner, event or toast."
    rows = [
        _recipe("FM10a", "moon", "W_BASELINE", t0 + "; source ELECTED; A's advancement file absent.",
                "Start the server; suspend W at the first baseline move; stop the owned PID without resuming.", held, stable),
        _recipe("FM10b", "moon", "W_BASELINE", t0 + "; source ELECTED; A's file valid but without this criterion.",
                "As FM10a.", held + " Unrelated criteria in A's file are kept.", stable),
        _recipe("FM10c", "moon", "W_BASELINE", t0 + "; source DELIVERED; A's advancement file absent.",
                "As FM10a.", "The baseline confirms the retained source with zero records; the deferred login awards A "
                "with the same receipt.", stable),
        _recipe("FM10d", "moon", "W_BASELINE", t0 + "; source DELIVERED; A's file valid but without this criterion.",
                "As FM10a.", "As FM10c; the criterion itself is repaired, not only an unchanged outbox.", stable),
        _recipe("FX-Ma", "moon", "S_GRANT", "C-M: A aboard the one completing rocket, B watching without a seat; neither "
                "file has the criterion.", "A lands on the Moon by the product route; S stops at the grant.",
                "A is already complete at login; no second toast or winner; B negative.", stable, save=True),
        _recipe("FX-Mb", "moon", "S_GRANT", "As FX-Ma.", "As FX-Ma, and stop the PID without the player save.",
                "A's saved file lacks the criterion; the immediate or deferred login grants it.", stable, save=False),
        _recipe("FX-Wa", "station", "S_GRANT", "C-W: A eligible and present at the winning station, B outside it or "
                "without VISIT.", "The station's first warp completes by the product route; S stops at the grant, "
                "before the root-only DELIVERED.", "A complete at login; same E, fold energy and orbit; B negative.",
                stable, save=True),
        _recipe("FX-Wb", "station", "S_GRANT", "As FX-Wa.", "As FX-Wa, and stop the PID without the player save.",
                "The first restart grants the lagging A; no second warp or winner.", stable, save=False),
        _recipe("FX-Rm", "moon", "S_DEFER", "Retained DELIVERED Moon E and R(E); A's saved player is dead.",
                "A logs in dead; S stops at the deferral; stop the PID.",
                "A logs in dead again: no award while dead; A respawns and is granted in the same session within the "
                "queue bound (21 ticks for two players after readiness).", stable),
        _recipe("FX-Rw", "station", "S_DEFER", "Retained DELIVERED station event; A's saved player is dead.",
                "As FX-Rm.", "As FX-Rm for the station criterion.", stable),
        _recipe("FX-Sm", "moon", "S_DEFER", "Retained DELIVERED Moon event; A's saved game mode is spectator.",
                "A logs in as a spectator; S stops at the deferral; stop the PID.",
                "Spectator negative; the console sets A to survival and the grant follows within the queue bound.", stable),
        _recipe("FX-Sw", "station", "S_DEFER", "Retained DELIVERED station event; A's saved game mode is spectator.",
                "As FX-Sm.", "As FX-Sm for the station criterion.", stable),
        _recipe("FX-Dm", "moon", "W_BASELINE", "Retained Moon world; A's file valid but without this criterion.",
                "Suspend W at the baseline; A logs in (deferred) and disconnects; S passes S_LOGOUT; stop the PID "
                "without resuming W.", "The queue entry was removed and A's file is byte-identical; A reconnects, the "
                "baseline confirms and A is awarded.", stable),
        _recipe("FX-Dw", "station", "S_LOGOUT", "Retained station world; A's saved game mode is spectator, file without "
                "the criterion.", "A logs in as a spectator (deferred) and disconnects; S stops after removeConnection; "
                "stop the PID before any eligibility change.",
                "No offline grant; A's game mode is set to survival by the console after reconnecting and the grant follows.",
                stable),
    ]
    if len(rows) != 14 or len({row["variant"] for row in rows}) != 14:
        raise ValueError("J17 needs fourteen distinct variants")
    return rows


def award_probes(player_a, player_b, kind):
    """The actual-award oracle: live AdvancementProgress through the vanilla selector, then the saved JSON."""
    advancement = ADVANCEMENTS[kind]
    live = "execute if entity @a[name={0},advancements={{{1}=true}}] run say K12_AWARD_{0}_DONE"
    return dict(advancement=advancement,
                live=[live.format(player_a, advancement), live.format(player_b, advancement)],
                expect_live={player_a: "the say line appears", player_b: "no say line; `Test failed` instead"},
                save="save-all flush (through the console, only while S is running)",
                files="world/advancements/<uuid>.json of A and B: advancement_done() true for A and false for B; "
                      "record each file's SHA-256 before the stop and after both restarts",
                source="arce progression status: the kind's source, event and receipt lines, unchanged across restarts",
                forbidden=["advancement grant", "any injected participant list", "arce.releaseTest.firstEventCut"])


def procedure():
    """The REF and campaign steps each fixture serves, with the commands the drivers send."""
    return dict(
        label="Every figure made on this PC is " + LABEL + ".",
        comparison="C10: the same sealed copy, back to back, baseline twice; two discarded warm-up minutes and ten "
                   "measured minutes for standard samples. Spread is a trigger for Root's review, not a limit.",
        ref_start="java -Xmx4G -Dadvancedrocketrycommunity.releaseTestHooks=true @libraries/net/minecraftforge/forge/"
                  "1.20.1-47.4.10/unix_args.txt nogui, with the journal hold properties where a step says so",
        journal_hold=["-Dadvancedrocketrycommunity.rocketJournalFailure=HOLD",
                      "-Dadvancedrocketrycommunity.rocketJournalHoldStartup=true"],
        raw_ticks=dict(command=raw_ticks.RAW_TICK_COMMAND, cadence="at least once per second (the reply holds 100 ticks)",
                       pairing="one `time query gametime` before and after each poll gives the game-time offset"),
        steps={
            "bay-queue": ["Start a copy with the journal hold; wait for " + JOURNAL_HELD + ".",
                          "Arm the raw-tick polls; send `arce rocket release-test journal-hold-release`.",
                          "Poll game time until every claim logs READY_SOURCE; claim_waits gives waits 10 to 12 and 17.",
                          "Read every claim's status and stop. Unfinished claims stay listed as unfinished."],
            "wait23": ["As bay-queue for its one claim.",
                       "Then poll `execute in <dimension> if loaded <x> 80 <z>` for every census column once per "
                       "second until all are unloaded; ticket_series keeps every row.",
                       "C36: the wait is reported raw; this world has no other ticket owner by construction."],
            "cargo-cells": ["Start a copy; `forceload add " + CELL_AREA + "`.",
                            "Send `arce bay release-test r042 attempt <cell> ALL` for A, B and S back to back.",
                            "Poll `arce bay release-test r042 report <cell>` with game-time queries; cargo_cycles "
                            "gives each device's request to settled time."],
            "mixed": ["Start a copy with the journal hold; forceload the cell area; release the hold; send the three "
                      "attempts at once; observe both timelines as above (J09 step 3, J05 waits 6 and 14 as far as "
                      "cargo competes)."],
            "first-event": ["Start a copy; load the rocket's columns; record `arce progression status`.",
                            "Launch by the product route with the eligible player aboard (REF), or, for the driver "
                            "qualification only, `arce rocket release-test launch <entity> MOON`.",
                            "Poll raw ticks and game time through " + ELECTED.pattern.split(" ")[0] + " and the "
                            "landing; first_event_observation names the candidate ticks.",
                            "Stop, hash " + PROGRESSION + ", and reopen the same world twice (J11 step 5)."],
        })


# ---- Native runs ---------------------------------------------------------------------------------------------------


class Console:
    def __init__(self, process):
        self.process, self.commands, self.raw, self.pairs = process, [], [], []

    def send(self, command):
        start = len(self.process.lines)
        self.commands.append(command)
        self.process.command(command)
        token = f"K12_BARRIER_{len(self.commands)}"
        self.process.command("say " + token)
        self.process.wait_for(re.compile(r"\[Server\] " + token + r"\b"), 60, start_at=start)
        lines = self.process.lines[start:]
        if any(marker in line for line in lines for marker in REJECTED):
            raise ValueError("Rejected console command: " + command)
        return lines

    def retry(self, command, pattern, timeout=120):
        deadline = time.monotonic() + timeout
        while True:
            lines = self.send(command)
            found = [line for line in lines if pattern.search(line)]
            if found:
                return found
            if time.monotonic() >= deadline:
                raise ValueError("No answer before the deadline: " + command)
            time.sleep(0.25)

    def tick(self):
        start = len(self.process.lines)
        self.process.command("time query gametime")
        return int(GAMETIME.search(self.process.lines[self.process.wait_for(GAMETIME, 60, start_at=start)])[1])

    def poll_raw(self):
        before, start = self.tick(), len(self.process.lines)
        self.process.command(raw_ticks.RAW_TICK_COMMAND)
        line = self.process.lines[self.process.wait_for(re.compile(re.escape(raw_ticks.PREFIX)), 60, start_at=start)]
        self.raw.append(line.rstrip("\n"))
        self.pairs.append([before, raw_ticks.parse_raw_tick_reply(line).tick, self.tick()])

    def observe(self, done, limit, each=None):
        """Game time every pass and raw ticks every second, until done() or the labelled limit."""
        deadline, next_raw, finished = time.monotonic() + limit, 0.0, False
        while not finished and time.monotonic() < deadline:
            if time.monotonic() >= next_raw:
                self.poll_raw()
                next_raw = time.monotonic() + 1.0
            if each is not None:
                each()
            self.tick()
            finished = done()
            time.sleep(0.05)
        self.poll_raw()
        return finished

    def loaded(self, dimension, cx, cz):
        token = f"K12_LOADED_{len(self.commands)}"
        return any(f"[Server] {token}" in line
                   for line in self.send(f"execute in {dimension} if loaded {cx * 16} 80 {cz * 16} run say {token}"))


def seen(console, pattern):
    return [line for line in console.process.lines if pattern.search(line)]


def launch_claims(console, origins):
    fixtures = []
    for origin in origins:
        console.send(f"forceload add {origin[0]} {origin[2]}")
        line = console.retry("arce bay release-test fixture " + " ".join(map(str, origin)), FIXTURE)[0]
        row = dict(zip(("logical", "transfer", "rocket", "data", "solar", "launch", "packages"), FIXTURE.search(line).groups()))
        if row["launch"] not in ("SUCCESS", "JOURNAL_PENDING"):
            raise ValueError("Gated fixture launch refused: " + row["launch"])
        fixtures.append(dict(row, origin=origin, source_columns=source_columns(origin)))
    deadline = time.monotonic() + 300
    while True:
        statuses = [status(console, row["logical"]) for row in fixtures]
        if all(item["claim"] == "PROVE" for item in statuses):
            return fixtures, statuses
        if time.monotonic() >= deadline:
            raise ValueError("Not every claim reached the D6 gate: " + json.dumps(statuses))
        time.sleep(0.5)


def status(console, logical):
    return parse_status(console.retry(f"arce bay release-test status {logical}", STATUS, 30)[0])


def pending(console):
    match = PENDING.search(console.retry("arce bay release-test pending", PENDING, 30)[0])
    return dict(pending=int(match[1]), retained=int(match[2]), work=match[3] == "true")


def build_cells(console, cells):
    console.send("forceload add " + cells["area"])
    built = {}
    for name in cells["names"]:
        lines = console.retry(f"arce bay release-test r042 cell {name} {cells['owners'][name]}", CELL)
        built[name] = parse_cells(lines)[name]
        if sorted(built[name]) != sorted(KINDS):
            raise ValueError("Cell was not built with five kinds: " + name)
    return built


def report_cells(console, names):
    rows = {}
    for name in names:
        rows.update(parse_reports(console.retry(f"arce bay release-test r042 report {name}", REPORT, 60)))
    if any(sorted(rows.get(name, {})) != sorted(KINDS) for name in names):
        raise ValueError("Missing R-042 report rows")
    return rows


def parent_rocket(console, scene, load=True):
    """K04's sealed rocket, read with the report it was sealed with."""
    from scripts.campaign_prep import k04_rockets as k04
    sealed = json.loads((K04 / scene / "readback.json").read_text(encoding="ascii"))["rockets"][0]
    if load:
        k04.load_chunks(console, dict(rockets=[dict(origin=[sealed[k] for k in ("x", "y", "z")])]), (OW,))
    report = k04.parse_report(console.send(f"arce rocket release-test report {sealed['entity']}"))
    keys = ("entity", "logical", "snapshot", "dimension", "state", "fuel", "capacity", "passengers", "transfer",
            "x", "y", "z", "blocks")
    if any(report[key] != sealed[key] for key in keys):
        raise ValueError("K04's rocket differs from its sealed readback")
    return {key: report[key] for key in keys}


def journal(world):
    from scripts import loader_cut_nbt as nbt
    return nbt.plain(nbt.read_gzip(world / JOURNAL)[1])["data"]["transfers"]


def forced(world):
    """Every dimension's persistent vanilla force-load list; a sealed source keeps none."""
    from scripts import loader_cut_nbt as nbt
    found = {}
    for file in sorted(world.rglob("chunks.dat")):
        rows = nbt.plain(nbt.read_gzip(file)[1])["data"]["Forced"]
        if len(rows):
            found[file.relative_to(world).as_posix()] = [int(value) for value in rows]
    return found


def stage_build(console, spec, result, name):
    if name == "first-event":
        raise ValueError("first-event is K04's world unchanged; use the adopt action")
    if spec.get("parent"):
        result["parent_rocket"] = parent_rocket(console, spec["parent"])
    if "cells" in spec:
        result["cells"] = build_cells(console, spec["cells"])
        result["cell_reports"] = report_cells(console, spec["cells"]["names"])
        if not all(untouched(row, kind) for cell in result["cell_reports"].values() for kind, row in cell.items()):
            raise ValueError("A new cell is not in its before-state")
    if "claims" in spec:
        result["claims"], result["statuses"] = launch_claims(console, spec["claims"])
    result["pending"] = pending(console)


def stage_readback(console, spec, result, name, built):
    if spec.get("cut"):
        console.process.wait_for(re.compile(JOURNAL_HELD), 120)
    if spec.get("parent"):
        result["parent_rocket"] = parent_rocket(console, spec["parent"])
        if name != "first-event" and result["parent_rocket"] != built["parent_rocket"]:
            raise ValueError("Restart changed K04's rocket")
    if name == "first-event":
        result["progression_status"] = [line.rstrip() for line in console.send("arce progression status")]
    if "cells" in spec:
        console.send("forceload add " + spec["cells"]["area"])
        result["cell_reports"] = report_cells(console, spec["cells"]["names"])
        if result["cell_reports"] != built["cell_reports"]:
            raise ValueError("Restart changed the cell readbacks")
    if "claims" in spec:
        result["statuses"] = [status(console, row["logical"]) for row in built["claims"]]
        before = [durable_status(item) for item in built["statuses"]]
        if [durable_status(item) for item in result["statuses"]] != before:
            raise ValueError("Restart changed the claims' durable readbacks")
    result["pending"] = pending(console)
    if name != "first-event" and result["pending"] != built["pending"]:
        raise ValueError("Restart changed the pending pool")


def stage_qualify(console, spec, result, name, built):
    result.update(label=LABEL, limit_seconds=180)
    if name == "first-event":
        rocket = parent_rocket(console, spec["parent"])
        from scripts.campaign_prep import k04_rockets as k04
        k04.load_chunks(console, dict(rockets=[dict(origin=[rocket[k] for k in ("x", "y", "z")])]), (MOON,))
        result["progression_before"] = [line.rstrip() for line in console.send("arce progression status")]
        console.poll_raw()
        console.send(f"execute in {OW} run arce rocket release-test launch {rocket['entity']} MOON")
        result["finished"] = console.observe(lambda: bool(seen(console, ELECTED)) and bool(seen(console, LANDED)), 120)
        console.observe(lambda: False, 5)  # the receipt barrier and DELIVERED follow the election
        result["progression_after"] = [line.rstrip() for line in console.send("arce progression status")]
        result["first_event"] = first_event_observation(console.process.lines, console.raw, console.pairs)
        return
    if spec.get("cut"):
        console.process.wait_for(re.compile(JOURNAL_HELD), 120)
    names = spec["cells"]["names"] if "cells" in spec else []
    if names:
        console.send("forceload add " + spec["cells"]["area"])
        report_cells(console, names)
    logicals = [row["logical"] for row in built.get("claims", [])]
    console.poll_raw()
    if spec.get("cut"):
        console.send("arce rocket release-test journal-hold-release")
    for cell in names:
        console.retry(f"arce bay release-test r042 attempt {cell} ALL", ATTEMPT, 120)

    def each():
        for cell in names:
            console.send(f"arce bay release-test r042 report {cell}")

    def done():
        ready = {match[2] for line in console.process.lines if (match := CLAIM.search(line)) and match[4] == "READY_SOURCE"}
        cells = parse_reports(console.process.lines)
        return set(logicals) <= ready and all(completed(cells[cell][kind], kind) for cell in names for kind in KINDS)

    result["finished"] = console.observe(done, result["limit_seconds"], each)
    if logicals:
        result["claim_waits"] = claim_waits(console.process.lines, logicals)
        result["statuses"] = [status(console, logical) for logical in logicals]
    if names:
        result["cargo_cycles"] = cargo_cycles(console.process.lines)
        result["cell_reports"] = report_cells(console, names)
    if name == "wait23":
        ends = [i for i, line in enumerate(console.process.lines) if (m := CLAIM.search(line)) and m[2] == logicals[0]]
        columns = result["columns"] = built["census_slots"][0]["slots"]
        rows, deadline = [], time.monotonic() + 150
        while time.monotonic() < deadline:
            before = console.tick()
            loaded = [console.loaded(*column) for column in columns]
            rows.append(dict(ticks=[before, console.tick()], loaded=loaded))
            if not any(loaded):
                break
            time.sleep(1)
        result["ticket_expiry"] = ticket_series(rows, bracket(console.process.lines, ends[-1]))
    result["raw_ticks"] = raw_summary(console.raw)[1]
    result["tick_offset"] = tick_offset(console.pairs)


def run(output, name, stage, cell_path, java, lock_owner):
    """One server run. The caller holds the heavy-JVM lock with the detached WORKER-RULES loop."""
    from scripts import run_dedicated_server_smoke as smoke
    from scripts.campaign_prep import k04_rockets as k04
    if not lock_owner.startswith("W454 pid=") or not (LOCK / "owner.txt").read_text(encoding="utf-8-sig").startswith(lock_owner):
        raise ValueError("W454 heavy-JVM ownership required")
    if min(shutil.disk_usage(p).free for p in ("C:/", "D:/")) < 10 * 1024**3:
        raise ValueError("10 GiB required on C: and D:")
    spec, folder = plan()[name], output / name
    target = output / "qualification" / name if stage == "qualify" else folder
    if stage != "qualify" and (folder / "SHA256SUMS.txt").exists():
        raise ValueError("A sealed fixture is immutable")
    if (target / (stage + ".json")).exists():
        raise ValueError("This stage already has a receipt")
    target.mkdir(parents=True, exist_ok=True)
    cell = json.loads(cell_path.read_text(encoding="utf-8-sig"))
    source = Path(cell["server_dir"])
    source_before = common.tree_hashes(source)
    k04.validate_candidate(cell, Path(cell["mods"]["server"][0]["path"]))
    source_world = folder / "world"
    if stage == "build" and source_world.exists():
        raise ValueError("Never overwrite a source world")
    parent = K04 / spec["parent"] if spec.get("parent") else None
    if parent is not None:
        verify_sealed(parent)
    world_from = parent / "world" if stage == "build" and parent is not None else None if stage == "build" else source_world
    before_world = common.tree_hashes(world_from) if world_from is not None else None
    built = json.loads((folder / "build.json").read_text(encoding="ascii")) if stage != "build" else None
    copy = output / "_run" / f"{name}-{stage}"
    if copy.exists():
        raise ValueError("Occupied disposable server")
    copy.parent.mkdir(exist_ok=True)
    properties = []
    if stage == "build" and spec.get("cut"):
        properties = ["-Dadvancedrocketrycommunity.bayCheckpoint=" + spec["cut"]]
    elif spec.get("cut"):
        properties = procedure()["journal_hold"]
    process = console = None
    result = dict(schema_version=1, status="FAILED", fixture=name, stage=stage, rows=spec["rows"],
                  candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH, client_started=False,
                  generator_commit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=common.ROOT, text=True).strip(),
                  generator_sha256=common.file_hash(Path(__file__)), started_utc=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    try:
        shutil.copytree(source, copy, ignore=shutil.ignore_patterns("world", "logs", "crash-reports"))
        k04.validate_candidate(cell, next((copy / "mods").glob("*.jar")))
        if world_from is not None:
            shutil.copytree(world_from, copy / "world", ignore=shutil.ignore_patterns("session.lock"))
        props = smoke.expected_server_properties(smoke.allocate_port(), True)
        props.update({"level-name": "world", "level-seed": "20261006", "level-type": "minecraft:flat",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"}),
                      "view-distance": "2", "simulation-distance": "2", "spawn-protection": "0", "max-tick-time": "60000"})
        (copy / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
        args = copy.resolve() / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        command = [str(java), "-Xms512M", "-Xmx4G", "-Djava.net.preferIPv4Stack=true",
                   "-Dadvancedrocketrycommunity.releaseTestHooks=true", *properties, "@" + str(args), "nogui"]
        result.update(command=command, properties=props)
        process = smoke.CapturedProcess(command, copy, target / (stage + "-console.log"))
        process.wait_for(smoke.READY_MARKER, 240)
        console = Console(process)
        console.tick()
        if stage == "build":
            stage_build(console, spec, result, name)
        elif stage == "readback":
            stage_readback(console, spec, result, name, built)
        else:
            stage_qualify(console, spec, result, name, built)
        for dimension in (OW, MOON):
            console.send(f"execute in {dimension} run forceload remove all")
        console.send("save-all flush")
        console.commands.append("stop")
        process.command("stop")
        code = process.finish(180)
        process = None
        if code != 0:
            raise ValueError(f"Native exit {code}")
        log = (copy / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
        if "/ERROR]" in log or "/FATAL]" in log:
            raise ValueError("Native ERROR/FATAL; the console log is retained")
        stopped = copy / "world"
        if forced(stopped):
            raise ValueError("The stopped world keeps forced chunks: " + json.dumps(forced(stopped)))
        if "claims" in spec and stage == "build":
            records = journal(stopped)
            if len(records) != len(spec["claims"]):
                raise ValueError("The journal holds a different number of transfers")
            result["census_slots"] = [dict(source_origin=list(record["source_snapshot"]["source_origin"]),
                                            slots=census_slots(record)) for record in records]
        if name == "first-event" and stage == "qualify":
            file = stopped / PROGRESSION
            result["progression_file"] = dict(present=file.is_file(), sha256=common.file_hash(file) if file.is_file() else None)
        if stage == "build":
            shutil.copytree(stopped, source_world, ignore=shutil.ignore_patterns("session.lock"))
            result["world_files"] = common.tree_hashes(source_world)
        result.update(status={"build": "BUILT", "readback": "MATCHED", "qualify": "QUALIFIED"}[stage], exit_code=code)
    except BaseException as failure:
        result["error"] = f"{type(failure).__name__}: {failure}"
        raise
    finally:
        if process is not None:
            common.abort_owned(process, target)
        if console is not None:
            result.update(commands=console.commands, raw_tick_replies=console.raw, raw_tick_pairs=console.pairs)
        result["cell_unchanged"] = common.tree_hashes(source) == source_before
        result["source_world_unchanged"] = before_world is None or common.tree_hashes(world_from) == before_world
        result["ended_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        (target / (stage + ".json")).write_bytes(common.json_bytes(result))
        if copy.exists():
            if copy.resolve().parent != (output / "_run").resolve():
                raise ValueError("Unsafe cleanup target")
            shutil.rmtree(copy)
    if not result["cell_unchanged"] or not result["source_world_unchanged"]:
        raise ValueError("Immutable input changed")
    return result


# ---- Sources, manifests and seals -------------------------------------------------------------------------------------


def verify_sealed(folder):
    """A parent's SHA256SUMS.txt names every file beside it and every hash matches."""
    listed = {}
    for line in (folder / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines():
        digest, path = line.split("  ", 1)
        listed[path] = digest
    actual = common.tree_hashes(folder)
    actual.pop("SHA256SUMS.txt", None)
    if listed != actual:
        raise ValueError("Parent differs from its checksum list: " + folder.name)
    return common.file_hash(folder / "SHA256SUMS.txt")


def adopt(output, name="first-event"):
    """The first-event source is K04's sealed world, copied unchanged and checked against its list."""
    spec, folder = plan()[name], output / name
    parent = K04 / spec["parent"]
    checksum = verify_sealed(parent)
    if (parent / "world" / PROGRESSION).exists():
        raise ValueError("The parent already holds a progression root")
    folder.mkdir(parents=True, exist_ok=False)
    shutil.copytree(parent / "world", folder / "world")
    files = common.tree_hashes(folder / "world")
    if files != common.tree_hashes(parent / "world"):
        raise ValueError("The copy differs from its parent")
    sealed = json.loads((parent / "readback.json").read_text(encoding="ascii"))["rockets"][0]
    moon = cold_columns(folder / "world", MOON, [[cx, cz] for cx in range(-2, 19) for cz in range(-2, 19)])
    result = dict(schema_version=1, status="BUILT", fixture=name, stage="build", rows=spec["rows"],
                  candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH, client_started=False,
                  commands=[], adopted_from=dict(scene=spec["parent"], checksum_list_sha256=checksum),
                  parent_rocket={k: sealed[k] for k in ("entity", "logical", "snapshot", "dimension", "state", "fuel",
                                                        "capacity", "passengers", "transfer", "x", "y", "z", "blocks")},
                  pending=None, progression_root_present=False, world_files=files,
                  moon_generated_columns=[row["column"] for row in moon if row["cohort"] == "generated"],
                  cell_unchanged=True, source_world_unchanged=True)
    (folder / "build.json").write_bytes(common.json_bytes(result))
    return result


def seal(output, name):
    folder, spec = output / name, plan()[name]
    if (folder / "SHA256SUMS.txt").exists():
        raise ValueError("Never overwrite a seal")
    receipts = {}
    for stage, wanted in (("build", "BUILT"), ("readback", "MATCHED")):
        receipt = receipts[stage] = json.loads((folder / (stage + ".json")).read_text(encoding="ascii"))
        if receipt["status"] != wanted or not receipt["cell_unchanged"] or not receipt["source_world_unchanged"]:
            raise ValueError("Unqualified fixture")
    if receipts["build"]["world_files"] != common.tree_hashes(folder / "world"):
        raise ValueError("Source world changed since its receipt")
    parents = []
    if spec.get("parent"):
        parents.append(dict(scene="K04/" + spec["parent"], checksum_list_sha256=verify_sealed(K04 / spec["parent"])))
    manifest = dict(schema_version=1, fixture=name, rows=spec["rows"], candidate_commit=common.CANDIDATE,
                    jar_sha256=common.JAR_HASH, bounds=recheck_bounds(), plan=spec, parents=parents,
                    composition=dict(claims=receipts["build"].get("claims"), census_slots=receipts["build"].get("census_slots"),
                                     cells=receipts["build"].get("cells"), parent_rocket=receipts["build"].get("parent_rocket"),
                                     pending=receipts["build"].get("pending")),
                    commands=dict(build=receipts["build"]["commands"], readback=receipts["readback"]["commands"]),
                    readbacks=dict(build={k: receipts["build"].get(k) for k in ("statuses", "cell_reports", "pending")},
                                   readback={k: receipts["readback"].get(k) for k in ("statuses", "cell_reports", "pending", "progression_status")},
                                   verdict="MATCHED"),
                    procedure=procedure(), not_covered=NOT_COVERED, inventory_before_manifest=common.tree_hashes(folder))
    (folder / "manifest.json").write_bytes(common.json_bytes(manifest))
    inventory = common.tree_hashes(folder)
    (folder / "SHA256SUMS.txt").write_text("".join(f"{h}  {p}\n" for p, h in inventory.items()), encoding="ascii")


def write_recipes(output):
    folder = output / "j17-recipes"
    folder.mkdir(parents=True, exist_ok=False)
    document = dict(schema_version=1, rows=["J17"], candidate_commit=common.CANDIDATE, jar_sha256=common.JAR_HASH,
                    rule="C39: no first-event H cut with a connected player; X stops are external stops of the owned "
                         "PID only. No recipe sets arce.releaseTest.firstEventCut.",
                    debugger="Start the owned server with -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,"
                             "address=127.0.0.1:<port> and attach jdb from the same host; record the PID from the "
                             "harness's own process handle. C6's classes carry line tables.",
                    stops=STOPS, player_save=PLAYER_SAVE, recipes=recipes(), bounds=recheck_bounds(),
                    award_probes={kind: award_probes("<A>", "<B>", kind) for kind in ADVANCEMENTS},
                    status="WRITTEN_NOT_RUN: no client and no debugger was started by K12.")
    (folder / "recipes.json").write_bytes(common.json_bytes(document))
    inventory = common.tree_hashes(folder)
    (folder / "SHA256SUMS.txt").write_text("".join(f"{h}  {p}\n" for p, h in inventory.items()), encoding="ascii")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    for action in ("plan", "adopt", "run", "seal", "recipes"):
        p = sub.add_parser(action)
        p.add_argument("--output", type=Path, default=OUTPUT)
        if action in ("run", "seal"):
            p.add_argument("--fixture", required=True, choices=sorted(plan()))
        if action == "run":
            p.add_argument("--stage", required=True, choices=("build", "readback", "qualify"))
            p.add_argument("--cell", type=Path, default=common.CELL)
            p.add_argument("--java", type=Path, default=Path("C:/Program Files/Java/jdk-17.0.7/bin/java.exe"))
            p.add_argument("--lock-owner", required=True)
    args = parser.parse_args()
    if args.action == "plan":
        print(json.dumps(dict(bounds=recheck_bounds(), fixtures=plan(), not_covered=NOT_COVERED), indent=2))
    elif args.action == "adopt":
        adopt(args.output)
    elif args.action == "run":
        run(args.output, args.fixture, args.stage, args.cell, args.java, args.lock_owner)
    elif args.action == "seal":
        seal(args.output, args.fixture)
    else:
        write_recipes(args.output)


if __name__ == "__main__":
    main()
