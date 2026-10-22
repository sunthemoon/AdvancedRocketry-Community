# TASK W137: a failed settlement-idle GameTest leaks Moon-pad tickets and times out the warm-up tests after it

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) and W134-R6 in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W134-review/REVIEW.md` (SHA-256 `61b0aed2…5c2d9a0d`) first. You are a new
session. This is a parallel bug: take it only in a slot that does not delay progress tasks.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w137` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w137` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W137.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W137/` |

## The defect

W134's complete GameTest run timed out `RocketSettlementIdleGameTests.ordinaryL2WithoutReceiptCompletesTwoPassengerFlights`,
then the warm-up tests `theCapAndThePerPlayerLimitRefuseColdDestinationsAndEveryWarmupEnds` and
`aWarmupKeepsItsPadThroughReservationChurnAndHandsOverAfterAPlayerRelaunch`; all three passed in a filtered run, and
hosted run 39464852370 at `53e9fa1a` failed with the same shape. W134-review traced a plausible cascade:

- `RocketSettlementIdleGameTests.Scenario` adds `FLIGHT_FIXTURE` region tickets on the source and Moon-pad election
  margins and removes them only on success; the class has no `@AfterBatch`.
- Its `flight()` waits while `journal.find(launch)` is empty, so a discarded launch becomes a timeout instead of a
  failure with a reason. In W134's run the L2 launch's warm-up bound and was released with `LAUNCH_ENDED` 17 ticks
  later, with no `ARCE_TRANSFER_PREPARE` line: the discard came after F1's ARMED write, from F1's boundary check or F2's
  countdown, which log nothing.
- The warm-up tests wait without a bound of their own until their predicted Moon pads are cold
  (`EscrowWarmupFixtures.cold`), and a pad column held by a leaked ticket is never cold.

## What to do

1. Confirm or refute the cascade with a GameTest: make the settlement-idle scenario fail (for example through a
   release-test hook that discards its launch) and show whether the warm-up tests then time out.
2. Give `RocketSettlementIdleGameTests` a cleanup that runs on failure as well as success (`@AfterBatch` or a `close()`
   called from it), removing its `FLIGHT_FIXTURE` and `START` tickets, its passenger and its rockets.
3. Make `flight()` fail at once with the launch's discard reason instead of waiting for a record.
4. Make the warm-up tests fail with a clear reason when a predicted pad is not cold at the start (a precondition), so a
   leak elsewhere shows as its own failure instead of a timeout.
5. Find why the L2 launch was discarded under load. If production discards a launch after F1's ARMED write without any
   log line, propose one INFO line with the reason in the record (do not add it here unless it is test-only).

No timeout, tick budget, assertion or production behaviour changes; test code and fixtures only. A new WARN or ERROR that
a GameTest drives needs its own exact manifest entry.

## Verification

Each changed test class alone; the reproduction in step 1 before and after the fix; then one full GameTest with the
unchanged log checker, under the heavy-JVM lock with the atomic pattern and the hand-off rule in WORKER-RULES, releasing
it and deleting `build/gametest/world` in the `finally` of the same detached script that took it. Check C: and D: above
10 GiB and that 6 GiB of memory is free before each Gradle run.

## Not in this task

Production code, other GameTest classes, status files and the ledger (Root).
