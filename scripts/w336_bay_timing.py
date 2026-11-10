"""Vanilla game-time brackets around unchanged bay fixtures; intervals, never wall-time ticks."""
from __future__ import annotations

import re
import time

import run_v180_bay_forced_stop as bay
import w336_native as native


def tick_samples(lines):
    samples = [(i, int(match[1])) for i, line in enumerate(lines) if (match := native.GAMETIME.search(line))]
    native.require(len(samples) >= 2, "Missing vanilla game-time brackets")
    # Vanilla time query returns game time modulo Integer.MAX_VALUE.
    native.require(all(b[1] >= a[1] for a, b in zip(samples, samples[1:])), "Game-time wrap/reset inside one start")
    return samples


def bracket(lines, index):
    samples = tick_samples(lines)
    before = [tick for at, tick in samples if at < index]
    after = [tick for at, tick in samples if at > index]
    native.require(before and after, "Event has no enclosing vanilla game-time queries")
    return [before[-1], after[0]]


def elapsed(start, end):
    native.require(start[0] <= start[1] and end[0] <= end[1] and start[0] <= end[1], "Invalid tick intervals")
    return [max(0, end[0] - start[1]), end[1] - start[0]]


def observations(lines):
    samples = tick_samples(lines)
    events = []
    for index, line in enumerate(lines):
        if samples[0][0] < index < samples[-1][0] and any(marker in line for marker in ("ARCE_BAY_CLAIM ", "ARCE_BAY_PROOF_REFUSED ", "ARCE_BAY_RETIRE_REFUSED ",
               "ARCE_RELEASE_SRW_INJECTED ", "ARCE_SATELLITE_REGISTRY_", "ARCE_RELEASE_BAY_PAUSED ",
               "ARCE_BAY_RECEIPTS_WRITE_", "ARCE_PASSENGER_RECONNECT_EXPIRED ", "ARCE_RELEASE_BAY_EXIT_LOGIN ")):
            events.append(dict(line=index, ticks=bracket(lines, index), text=line.rstrip()))
    return dict(unit="server_game_ticks", arming_tick=samples[0][1], terminal_tick=samples[-1][1],
                window_ticks=samples[-1][1] - samples[0][1], events=events,
                uncertainty="Each transition lies between neighboring console queries; wall time is not converted to ticks.")


def proof_turns(lines, logicals, slots):
    native.require(len(logicals) >= 2 and slots >= 2, "D6 timing requires contention and a complete census")
    rows = []
    for logical in logicals:
        claims = [(i, m) for i, line in enumerate(lines) if (m := bay.CLAIM.search(line)) and m[2] == logical]
        ready = [i for i, m in claims if m[4] == "READY_SOURCE"]
        waiting = [i for i, m in claims if m[4] == "BAY_PROOF_TURN"]
        native.require(len(ready) == 1, "Claim did not reach exactly one READY_SOURCE")
        # The uncontended first holder need not emit BAY_PROOF_TURN; do not fabricate that transition.
        start = waiting[0] if waiting else next((i for i, m in claims if m[4] == "BAY_SOURCE_PROOF"), None)
        native.require(start is not None and start < ready[0], "Claim lacks a pre-ready D6 observation")
        duration = elapsed(bracket(lines, start), bracket(lines, ready[0]))
        service = next((i for i, m in claims if i >= start and m[4] == "BAY_SOURCE_PROOF"), start)
        service_ticks = elapsed(bracket(lines, service), bracket(lines, ready[0]))
        rows.append(dict(logical=logical, queued=bool(waiting), first=bracket(lines, start),
                         ready=bracket(lines, ready[0]), wait_through_ready_ticks=duration,
                         turn_ticks=service_ticks, ticks_per_census_slot=[value / slots for value in service_ticks],
                         census_slots=slots))
    native.require(any(row["queued"] for row in rows), "No concurrent claim waited for a D6 turn")
    return rows


def retry_spacing(lines, marker):
    indices = [i for i, line in enumerate(lines) if marker in line]
    native.require(len(indices) >= 2, "Fewer than two retry observations")
    return [elapsed(bracket(lines, a), bracket(lines, b)) for a, b in zip(indices, indices[1:])]


def retry_probe_tick(lines, index):
    """Schedule the next fixture impulse from a logged retry, not a repeated stage-transition line."""
    match = re.search(r"ARCE_BAY_PROOF_REFUSED .* retry=(\d+)\s*$", lines[index])
    native.require(match is not None and 0 < int(match[1]) <= 200, "Missing bounded non-superseding D6 retry")
    # Begin before the earliest due tick. Bounded reload impulses span query/command-delivery uncertainty.
    return bracket(lines, index)[0] + int(match[1]) - 6


class TimedSession(bay.Session):
    """Reuse the existing case bodies and invariants, adding only vanilla console observations."""
    def __init__(self, args, session, evidence):
        super().__init__(args, session, evidence)
        self.timed_starts = []
        self.launches = []
        self.command_log = []
        self.hold_window = 0
        self.pause_window = 0

    def query_tick(self):
        self.command_log.append("time query gametime")
        return int(native.GAMETIME.search(super().send("time query gametime", native.GAMETIME))[1])

    def start(self, case, restart, properties):
        process = super().start(case, restart, properties)
        self.launches.append(dict(case=case, restart=restart, command=process.process.args, properties=properties,
                                  installed_jar=self.candidate["installed_jar"],
                                  installed_jar_sha256=self.candidate["installed_jar_sha256"]))
        self.query_tick()
        return process

    def send(self, value, marker, timeout=60):
        self.query_tick()
        if value == "arce bay release-test release" and self.pause_window:
            self.window(self.pause_window)
        self.command_log.append(value)
        answer = super().send(value, marker, timeout)
        self.query_tick()
        return answer

    def wait_after(self, marker, start, timeout):
        deadline = time.monotonic() + timeout
        while True:
            self.query_tick()
            found = next((line for line in self.process.lines[start:] if marker.search(line)), None)
            if found is not None:
                self.query_tick()
                return found
            native.require(time.monotonic() < deadline, "Timed out waiting for " + marker.pattern)
            time.sleep(0.1)

    def wait(self, marker, timeout):
        return self.wait_after(marker, 0, timeout)

    def window(self, ticks, logical=None):
        start = self.query_tick()
        while self.query_tick() - start < ticks:
            if logical:
                self.status(logical)
            time.sleep(0.1)
        return self.query_tick() - start

    def end(self, how, timeout):
        self.query_tick()
        # Windows on a held claim observe persistence, not an upper bound on the hold.
        if self.hold_window:
            self.window(self.hold_window)
        self.query_tick()
        lines = self.process.lines
        self.timed_starts.append(lines)
        return super().end(how, timeout)


def census_slots(record):
    """Mirror the documented bounded column arithmetic from the actual stopped journal snapshots."""
    slots = []
    ends = [record[key] for key in ("source_snapshot", "destination_snapshot")]
    for end in ends:
        origin = end["source_origin"]
        slot = (end["source_dimension"], origin[0] >> 4, origin[2] >> 4)
        if slot not in slots:
            slots.append(slot)
    for end in ends:
        x, _, z = end["source_origin"]
        a, _, c, d, _, f = end["bounding_box"]
        columns = [(end["source_dimension"], cx, cz)
                   for cx in range((x + a - 2) >> 4, ((x + d + 2) >> 4) + 1)
                   for cz in range((z + c - 2) >> 4, ((z + f + 2) >> 4) + 1)]
        native.require(1 <= len(columns) <= 16, "Census end exceeds its production bound")
        for slot in columns:
            if slot not in slots:
                slots.append(slot)
    native.require(len(slots) <= 34, "Census exceeds production slot bound")
    return slots
