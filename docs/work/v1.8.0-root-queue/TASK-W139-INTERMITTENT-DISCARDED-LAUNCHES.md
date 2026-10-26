# TASK W139: intermittent discarded launches in complete GameTest runs (critical path)

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), the W134-R6 section of
`D:/GitHub/ARCE-Task-Evidence/root-queue/W134-review/REVIEW.md`, [records/W133.md](records/W133.md)'s W133F section (the
gated launch traces) and [TASK-W137](TASK-W137-SETTLEMENT-IDLE-CLEANUP-CASCADE.md) first. You are a new session. Full
GameTest runs on the shared branch now fail intermittently in launch tests, which makes every merge's regression and
every worker's verification unreliable, so this runs as a temporary fourth implementer.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w139` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w139` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W139.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W139/` |

## The occurrences

Each test below passed in other complete runs and in filtered reruns; none was changed by the merge it failed on.

| Run | Commit | Failed tests and messages |
|---|---|---|
| Hosted 39464852370 (first attempt) | `53e9fa1a` | `RocketSettlementIdleGameTests.acknowledgedSettlementRestoresMountRefuelAndLaunch` ("F7 cut published the wrong terminal outcome for NONE") and three governed warm-up tests out of ticks |
| Local, W134's complete run | `3258d606` | `ordinaryL2WithoutReceiptCompletesTwoPassengerFlights` (its L2 launch's warm-up bound, then released `LAUNCH_ENDED` 17 ticks later with no `ARCE_TRANSFER_PREPARE`: discarded after F1's ARMED write), then two warm-up tests out of ticks (W137's cascade) |
| Hosted 39736717569 | `367a84ff` | `EscrowGovernedMarginGameTests.borderCrossingMarginsLandOnAnEmptyMoonAndARestartReticketsTheSameColumns` ("refused: DESTINATION_LOADING"; a fresh request replaced a READY warm-up, `reason=REPLACED`, instead of binding it) and `RocketPassengerContinuedUseGameTests.logoutImmediatelyCancelsOnlyItsPendingReconnect` ("No value present"; its Moon flight failed back, `ARCE_TRANSFER_RETIREMENT_WAIT site=FAIL_BACK reason=CREW_WAITING`) |
| Hosted 39737594848 | `2426f00d` (same batch order as `367a84ff`, which passed both Moon tests this time) | `EscrowGovernedSourceFlightGameTests.restoredAscentAheadOfLevelClockResumesWithoutResourceChanges` ("Source preparation was discarded", two seconds after its rocket became active) |

Fetch the hosted job logs through the run IDs (the raw evidence artifact of each run also has the complete
`latest.log`). Several paths discard a prepared launch without any log line (for example F1's boundary check and F2's
countdown in `EscrowGovernedPreparation.begin`).

## What to do

1. **Make every discard observable.** You may add one INFO line naming the reason at each production site that
   discards a prepared or governed launch, a warm-up binding or a source preparation without logging today (INFO only,
   no behaviour change, no new WARN or ERROR), and enable W133F's gated launch traces in your runs.
2. **Reproduce** under load: run the affected batches (`escrow_governed_source_clock`, `machine_menu_channel`,
   `passenger_logout_cleanup`, the settlement-idle batch) repeatedly, with and without parallel CPU load, until each
   failure shows its reason; also run complete GameTests if needed.
3. **Find the cause** of each discard (a wall-clock or tick race, a shared fixture or Level state between batches, a
   ticket or pad left by an earlier test, a warm-up prediction that changes between requests, or a production race).
4. **Fix** it deterministically: in test fixtures when the cause is test isolation or timing assumptions; if the cause is
   a production race that a player could hit, stop and report it with the evidence for Root's decision before changing
   production behaviour. Coordinate with W137 (the settlement-idle cleanup), which may run in parallel: do not edit
   `RocketSettlementIdleGameTests`.

No timeout, tick budget, assertion or retry-count changes (AGENTS section 5); no behaviour change in production without
Root's decision.

## Verification

Before and after: the repeated filtered runs (report the counts, for example 0 of 30 failing after); each changed test
class alone; then one complete GameTest with the unchanged log checker on committed source. Heavy runs under the
heavy-JVM lock with the atomic pattern and the hand-off rule in WORKER-RULES (take the lock per batch of runs, not for
the whole task), releasing it and deleting `build/gametest/world` in the `finally` of the same detached script that
took it. Check C: and D: above 10 GiB and that 6 GiB of memory is free before each Gradle run.

## Not in this task

`RocketSettlementIdleGameTests` (W137), status files and the ledger (Root).
