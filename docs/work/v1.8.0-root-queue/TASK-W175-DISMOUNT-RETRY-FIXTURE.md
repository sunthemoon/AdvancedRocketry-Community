# TASK W175: the dismount retry-backoff fixture that turned a hosted regression red

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w175` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w175` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W175.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W175/` |
| Failed log | `D:/GitHub/ARCE-Task-Evidence/root-queue/ci-flakes/regression-dd080faa-job-122364090027.log` |

Hosted run 40553739287 at `dd080faa` failed one GameTest:
`EscrowGovernedLiveReturnGameTests.dismountBetweenCancelledCheckpointAndReplacementRefusesWrite` (batch
`escrow_cancelled_dismount_cut`), with "Definite F7 callback lost the first retry backoff". The same source passed on
run 40554256640.

`EscrowTerminalRetryCut.assertWriteFailure` asserts `history.failures() == 1 && history.nextTick() ==
server.getTickCount() + 100`. That is exact to the tick at which the fixture observes the failure, not the tick at
which the failure was recorded.

## What to do

1. **Confirm the cause.**
   - Find when the fixture calls `assertWriteFailure` relative to the definite F7 callback, and whether that callback
     can complete on a later tick. Async completions are published at the start of a tick.
   - Reproduce it, for example by delaying the observation by one tick in a local experiment that is not committed.
   - If the evidence points elsewhere, say so and fix what you find.
2. **Fix the fixture without loosening it.** Assert the backoff against the failure's own recorded tick: the
   history's failure count is 1, and `nextTick` equals the recorded failure tick plus `EscrowReason.backoff(1)`.
   - If the history does not record that tick, capture it in the fixture at the callback.
   - The production backoff, the 100-tick value and every other assertion stay as they are.
   - Look for the same exact-tick pattern in the other `EscrowTerminalRetryCut` callers, and fix those that match.
3. **No production change.** If the cause is in production, stop and report.

## Verification

- The batch `escrow_cancelled_dismount_cut` and the other batches that use `EscrowTerminalRetryCut`, three times
  each, under the heavy-JVM lock.
- The whole unit suite.

No full GameTest is needed: the hosted regression after merge covers it.

## Not in this task

Production code, other flaky tests (list them in the record), ADRs, the risk register, status files and the ledger.
