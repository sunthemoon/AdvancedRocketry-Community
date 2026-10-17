# TASK W11b2, continuation 47: R17 items 3 and 4 (per-rocket journal lease, batch re-capture, writer accounting) and review F8 to F10

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c47` (new, at the review tree `12f5a5b2` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c47` (local commits only; Root pushes and merges) |
| Input | Final review part B, `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-final-review-B/REVIEW.md` (SHA-256 `60581547d83db3bf70b1b1d3d17eecf140df73afcaaa74144f930e8d2b30b0bd`), read in full, especially F4, F5 and F8 to F10 |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), sections 5.3, 5.4 and 5.5; R1 to R16; and **R17** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/ROOT-DECISION-R17.md` (SHA-256 `c31254f82e023247b8d9d1df5a89d6919cec87bfce0635e5a7ad79f5e6e664c1`) |
| Record | `records/W11B2-C47.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C47/` |

The review tree `12f5a5b2` is the W11b2 branch at `3b35a8af` with the C41 branch's `a28004df` merged. Part B's verdict
is FIX-FIRST; Root answered its questions with R17. Continuation 46 works in parallel in
`D:/GitHub/arce-v180-w11b2-c46` on R17 items 1, 2, 5 and 6 (riders, passengers, transient observations, executor holds)
and review F3 and F7. Continuation 44 is fixing review part A's settlement findings on the main W11b2 branch. Root
merges them all.

## What to do (one commit per item, each with its tests)

1. **R17 item 3 (review F4):** the checked journal lease becomes per logical rocket, exactly as R17 states, for F1, F7,
   F8, the pre-F2 cancellation, settlement O3 and O4, and reconciliation C3 to C7. Unit tests: another rocket's record
   change does not invalidate L's lease; L's own change, an unacknowledged change for L, a record naming T or the
   serial, and a changed guard each do.
2. **R17 item 3, batches:** a coalesced flight generation in which one request's guard fails at capture is captured
   again without that request; only that request receives `false`, and the others proceed. Unit tests for a mixed batch.
3. **R17 item 4 (review F5):** a guard refusal before any IO is its own outcome and does not count towards the writer's
   unavailability; autosave is never skipped while the writer is unavailable. Unit tests: three consecutive guard
   refusals leave the writer available; an unavailable writer still attempts autosave.
4. **Review F8:** test R16's gate wiring: `ownsAllConnectedPlayers` over a real `PlayerList` with embedded
   connections, the `enableRcon` refusal, `place` refusing a foreign connection, and `pump` dropping stale ownership.
5. **Review F9:** an F2 callback-queue refusal after PREPARED is written reports JOURNAL_PENDING (or checks
   `canQueueEffect` before the put), not a discarded launch. Test it.
6. **Review F10:** retain the dispatch retry histories once per tick from one set of the journal's logical IDs, instead
   of once per classify call. Test the count.

## Write scope and boundaries

`RocketTransferSavedData`'s lease, `EscrowFlightJournal`, `EscrowFlightWrites`, `EscrowLedgerWriter`, the
coordinator's writer, lease and autosave paths, `EscrowGovernedDispatch`'s retention, `EscrowGovernedPreparation` for
F9, the lease calls in `EscrowFlightCheckpoint`, `EscrowGovernedLaunch`, `EscrowGovernedHandoff`,
`EscrowGovernedCleanup`, `EscrowPreF2Cancellation`, `EscrowSettlementRecovery` and the reconciliation checkpoint, the
release-test player facility for F8, their tests, and the record. Continuation 46 owns the source flight, destination,
arrival, phases, return, terminal and passenger logic and `EscrowTransferEnvelope`; where your lease change meets
those files, change only the lease call. Under R13 item 3, a narrow additive edit elsewhere is allowed when R17
requires it; record each one.

No change to an acknowledgement order, a proof at capture or replacement (apart from R17 item 3's lease scope), a
schema, the replay exclusion, the log checker or its expectations, and no relaxed budget, limit, timeout or assertion.
Stop only for a change outside R17: commit what you have with your record, and report. Keep the whole unit suite
passing after every item; run one full GameTest with the unchanged log checker at the end, and rerun the C0 to C8
reconciliation cuts (`scripts/run_v180_escrow_reconciliation.py`) once, because item 1 changes their lease.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C47/`. Other workers share
the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. If the tool policy
refuses to let you remove your own lock, world or server copy, write the exact path in your record and final answer and
continue; Root removes it. If a GameTest run crosses midnight, check every rolled log part.

## Not in this task

R17 items 1, 2, 5 and 6 and review F3 and F7 (continuation 46); the benchmark (continuation 45); review part A's fixes
(continuation 44); production admission; ADRs, the risk register, the R-035 text, status files and the ledger (Root).
