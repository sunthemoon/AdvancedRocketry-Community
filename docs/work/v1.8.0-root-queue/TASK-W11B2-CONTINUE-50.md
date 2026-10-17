# TASK W11b2, continuation 50: the flight fix review's N1, N3, N4 and N7

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c50` (new, at the integrated W11b2 head `2ed7f18e` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c50` (local commits only; Root pushes and merges) |
| Input | The flight fix review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-fix-review-flight/REVIEW.md` (SHA-256 `5a418f13550eb83b6834c4389eadb612a62f88107df8047ab5381290dc95a55a`), findings N1, N3, N4, N6 and N7 |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); R1 to R18, especially **R17 items 5 and 6** (`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/ROOT-DECISION-R17.md`, SHA-256 `c31254f82e023247b8d9d1df5a89d6919cec87bfce0635e5a7ad79f5e6e664c1`) and **R18** (`W11b2-C46/ROOT-DECISION-R18.md`); continuation 46's record `records/W11B2-C46.md` |
| Record | `records/W11B2-C50.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C50/` |

The review of continuation 46 returned MERGE-WITH-FOLLOWUP. Other work runs in parallel from the same head:
continuation 48 (`arce-v180-w11b2-c48`: the pre-F2 release-test probe and runner only), continuation 49
(`arce-v180-w11b2-c49`: `RocketJournalLeases`, `RocketTransferSavedData`'s lease publication, `EscrowFlightJournal` and
the journal checks in `EscrowGovernedTerminal`) and W89 (`arce-v180-w89`: the station authority matrix). Do not edit
their files; Root merges them all.

## What to do (one commit per item, each with its tests)

1. **N1 (R17 item 5).** Motion that the rocket's own tick normalizes is transient wherever a governed proof observes
   the source, not only in the executors' warm frames. Make `EscrowFlightSource.observe()` and F7's
   `EscrowGovernedHandoff` proof observation treat transient delta movement as "not now" without setting a sticky flag
   (the disk validators still compare the saved `Motion`, so a save taken with motion still refuses its proof). A
   persistent change of position or any other field still invalidates the capture exactly as today. If the return
   path also needs to recapture `state.source` after a failed observation, as the terminal does, do it and record why.
   Unit tests, and a GameTest that applies motion from an `onEachTick` callback (after the entity tick) while a
   `RETURN_*` proof is pending and requires the checked F7 CANCELLED and F8.
2. **N3 (R17 item 6).** Log and record the `PERSISTENT_PLACEMENT` hold only when the journal still holds that exact
   transfer and it classifies as ACTIVE, as the executors' `report(...)` does; drop the arrival and F3 bindings of a
   logical rocket when its flight completes (`releaseCompleted`), as F5's captures are dropped. Unit-test a landed,
   cleaned rocket that is moved afterwards: no WARN and no hold entry.
3. **N4.** The three test gaps: a dispatcher-level descent test with a counting envelope digest and a capture counter;
   a manifest-change case in `EscrowGovernedEnvelopeTest` (one assignment changed while the riders stay valid must
   compare unequal); a mounted-logout departure GameTest variant (the passenger is riding when `PlayerList.remove` runs).
4. **N7.** `EscrowDestinationProbe` and `EscrowPhaseProbe` compare the destination envelope with R18's governed
   comparison, not the raw envelope.
5. Run the whole unit suite after each item, and one full GameTest with the unchanged log checker at the end, after
   the heavy-JVM lock is free. Name any test that fails in that run; W89 is fixing the station authority matrix, so
   report that one but do not change it.

## Write scope and boundaries

`EscrowFlightSource`, the proof observation in `EscrowGovernedHandoff`, `EscrowGovernedReturn`, `EscrowGovernedFrame`,
`EscrowGovernedExecutorHolds`, `EscrowGovernedArrival`, `EscrowGovernedSourceFlight` and `EscrowGovernedPhaseTracking`
for the binding cleanup, `RocketGovernedSourceDispatcher` only for N3's route check, `EscrowDestinationProbe`,
`EscrowPhaseProbe`, their unit tests and GameTests (`EscrowGovernedSourceFlightGameTests`, `EscrowGovernedLiveReturnGameTests`
or a new GameTest class), and the record. Under R13 item 3, a narrow additive edit elsewhere is allowed when an item
requires it; record each one. No change to an acknowledgement order, a lease, a schema, the replay exclusion, the log
checker or its expectations, and no relaxed budget, limit, timeout or assertion. A new GameTest batch is fine; say so in
the record, because batch names change the run order. Stop only for a change outside these items: commit what you have
with your record, and report.

N6 (the arrival executor running twice per COMMITTED visit) is not changed here; the benchmark measures it.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C50/`. Other workers share
the heavy-JVM lock; wait for it as WORKER-RULES says, and never remove a lock you do not own. If the tool policy refuses
to let you remove your own lock, world or server copy, write the exact path in your record and final answer and
continue; Root removes it. If a GameTest run crosses midnight, check every rolled log part.

## Not in this task

The lease (continuation 49); the pre-F2 probe and runner (continuation 48); the station authority matrix (W89); the
in-memory owner-cancel return request that does not survive a restart (the review notes it predates continuation 46;
Root records it); the benchmark; production admission; ADRs, the risk register, the R-035 text, status files and the
ledger (Root).
