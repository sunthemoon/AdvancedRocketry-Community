# TASK W130F: W130's review follow-ups (W130-R1, R3, R4 and R6) under Root decision R59

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W130](TASK-W130-D2-B2-RETIRE-BEFORE-FILL.md),
[records/W130.md](records/W130.md) and the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W130-review/REVIEW.md`
(SHA-256 `78ae2a47…ab7c32`) with its probe `probes/W130ReviewTicketProbeTest` first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w130f` (new; from the shared branch at the commit named in your prompt, which has W130 merged as `3bce8373`) |
| Branch | `codex/v1.8.0-w130f` (local commits only; Root merges) |
| Record | append a "W130F" section to `docs/work/v1.8.0-root-queue/records/W130.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W130F/` (keep W130's evidence unchanged) |

## Root decision R59 (2026-10-22)

W130-review approves with changes: the retirement is safe (T is removed only after the destination slot, the old
origin slot and every discarded copy's slot read back as the proof requires, and a fill with T is refused before
capture), and W130 merged as `3bce8373` because production admission is off, so nothing below is reachable by a player
yet. Before admission is enabled:

1. **W130-R1 (Medium):** one loader attempt creates an entry that re-tickets both ends every 200 ticks with no further
   demand, and three waits that never end by themselves are checked only after the ticket step: a search area over the
   limit (an ordinary 47 by 32 footprint reaches it), a Level with saving off, and unsupported entity storage. The
   entry's own refresh also keeps the landed rocket's column loaded, so it sustains itself after the player leaves (the
   review's probe: 61 ticket steps in 12,000 ticks after one demand). Check those three conditions before placing a
   ticket; for a wait found only after ticketing, stop ticketing after a small fixed number of attempts (state it) until
   a new demand; and make sure no entry keeps a column loaded through its own refresh alone. R52's limits (four starts
   per tick, one attempt per rocket per 200 ticks, the 400-tick expiry, never at startup, W98's backoff not reset)
   stay. Turn the review's probe into a regression test that bounds the ticket steps after one demand.
2. **W130-R3 (Low):** `RocketOrdinaryRetirement` grew from 498 to 540 lines; move the new pacing into a small class so
   it is under 500 again (AGENTS section 3.4).
3. **W130-R4 (Low):** the first attempt loads the old site's chunk synchronously inside the loader's fair-queue visit.
   Measure that cost and name it in the record; if it can start from the coordinator's own tick without changing the
   order of proof and admission, move it there.
4. **W130-R6 (Low):** in the record, correct "expiry remains the backstop" and "only from a loader attempt" for R1, and
   define or remove "OpenPet".

No saved format, schema, hash, bound, proof, lease, budget or timeout changes; no existing assertion is relaxed;
`RocketTransferService` stays under 800 lines. Stop and report if any of these seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker; then
W130's twelve native retirement cases again, two restarts each, all on committed source under the heavy-JVM lock with
the atomic pattern and the hand-off rule in WORKER-RULES, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it. Check C: and D: above 10 GiB and that
6 GiB of memory is free before each Gradle run or native start.

## Not in this task

W133F's launch checks, cargo (D4), enabling admission, ADRs, the risk register, status files and the ledger (Root).
