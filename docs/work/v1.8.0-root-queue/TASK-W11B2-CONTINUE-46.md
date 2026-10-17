# TASK W11b2, continuation 46: R17 items 1, 2, 5 and 6, the descent rehash, and crewed evidence

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-43](TASK-W11B2-CONTINUE-43.md) and the documents they name first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c46` (new, at the review tree `12f5a5b2` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c46` (local commits only; Root pushes and merges) |
| Input | Final review part B, `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-final-review-B/REVIEW.md` (SHA-256 `60581547d83db3bf70b1b1d3d17eecf140df73afcaaa74144f930e8d2b30b0bd`), read in full |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), sections 5.2, 5.4 and 5.5; R1 to R16; and **R17** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/ROOT-DECISION-R17.md` (SHA-256 `c31254f82e023247b8d9d1df5a89d6919cec87bfce0635e5a7ad79f5e6e664c1`) |
| Record | `records/W11B2-C46.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/` |

The review tree `12f5a5b2` is the W11b2 branch at `3b35a8af` with the C41 branch's `a28004df` (continuations 41 to 43)
merged. Part B's verdict is FIX-FIRST; Root answered its questions with R17. Two other sessions work in parallel:
continuation 47 in `D:/GitHub/arce-v180-w11b2-c47` on R17 items 3 and 4 (the per-rocket journal lease, batch
re-capture and writer accounting) and review F8 to F10; continuation 45 in `D:/GitHub/arce-v180-w11b2-c41` on the
benchmark harness. Root merges all three.

## What to do (one commit per item, each with its tests)

1. **R17 item 1 (review F1):** live riders compared as `sourceComparison` does, in every source-phase check before
   F5 that the review lists.
2. **R17 item 2 (review F2):** (a) the arrival's `passengersPending` short-circuits only for an offline assigned
   passenger; (b) CANCELLED's passenger prerequisite as R17 states. HANDED_OFF is unchanged.
3. **R17 item 5 (review F6):** transient motion and per-tick plan positions set no sticky flag; a persistent position
   change at rest holds and logs one WARN per flight.
4. **R17 item 6:** a non-transient executor hold inside an ACTIVE route logs one WARN per (flight, reason) and does not
   refresh the flight tickets.
5. **Review F3 (no proof change):** cache the validated envelope expansion per immutable record instance (accessors
   return copies); keep F4's PRESENT work across the arrival executor's permitted movement; at COMMITTED, let F5 do
   its complete matching only when it is about to act. Add a unit test that counts digests per descent tick.
6. **Evidence (review F7 and R17's required tests):** the native S2 cuts with a facility-owned passenger mounted at F1
   (a source-phase restart and a PF_ARM_REPLACED restart, two restarts each, completing or cancelling by the exact
   checked outcome), and the GameTests R17 lists (dismount during ASCENT; dismount after LANDED and after a cancelled
   return, then F7 and F8; logout during the flight and login after landing).

## Write scope and boundaries

The governed source flight, source action and recovery, destination, arrival, phases, return, terminal and passenger
classes, `EscrowTransferEnvelope`, `RocketGovernedSourceDispatcher`, `RocketEntity` only where R17 item 5 needs it,
their probes, runners, tests and GameTests, and the record. Continuation 47 owns the journal lease
(`RocketTransferSavedData`, `EscrowFlightJournal`), `EscrowFlightWrites`, `EscrowLedgerWriter`, the coordinator's writer
paths and `EscrowGovernedDispatch`'s retention; do not edit those, and where your item meets the lease, leave the lease
call as it is. Under R13 item 3, a narrow additive edit elsewhere is allowed when R17 requires it; record each one.

No change to an acknowledgement order, a proof at capture or replacement (apart from R17 item 1's rider comparison),
a schema, the replay exclusion, the log checker or its expectations, and no relaxed budget, limit, timeout or
assertion. If a GameTest emits a new WARN, report the test and the line; Root decides the expectation. Stop only for a
change outside R17: commit what you have with your record, and report. Keep the whole unit suite passing after every
item; run one full GameTest with the unchanged log checker at the end, and the native cuts once.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/`. Other workers share
the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. If the tool policy
refuses to let you remove your own lock, world or server copy, write the exact path in your record and final answer and
continue; Root removes it. If a GameTest run crosses midnight, check every rolled log part.

## Not in this task

R17 items 3 and 4 and review F4, F5 and F8 to F10 (continuation 47); the benchmark (continuation 45); review part A's
fixes (continuation 44); production admission; ADRs, the risk register, the R-035 text, status files and the ledger
(Root).
