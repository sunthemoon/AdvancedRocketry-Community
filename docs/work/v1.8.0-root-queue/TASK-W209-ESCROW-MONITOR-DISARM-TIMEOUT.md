# TASK W209: an escrow-monitor GameTest timed out once in a branch run

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w209` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w209` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W209.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W209/` |
| Logs | `D:/GitHub/ARCE-Task-Evidence/root-queue/W209/job-123748136740-run-40959470440.log` (the whole job log) and `monitor_governed_cutoff-excerpt.log` (the batch's lines) |
| Code | `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowMonitorGameTests.java` |

**Root decision R179.** In W206's branch run 40959470440 (at `24a076f9`, whose source under `src` equals that of the
green run at `53329dd7`), `EscrowMonitorGameTests.disarmingAfterPreparedKeepsTheAcceptedLaunchAndItsRealOutcome`
(batch `monitor_governed_cutoff`) failed with "Didn't succeed or fail within 2400 ticks" at 22:13:50 UTC. Every other
GameTest in the run passed, and the test has passed in earlier runs, so it is intermittent. The test covers an accepted
launch and its outcome, which is a persistence and authority area.

## What to do

1. **Find the cause** from the two logs and the code: what the test was waiting for when its 2,400 ticks ran out (the
   last stage or condition it logged, the batch's other tests and their timing, server load or tick pacing, chunk or
   entity loading), and whether the product or the test is wrong. Reproduce it locally under the heavy-JVM lock, with
   the batch repeated enough times to show the failure rate before and after any fix.
2. **A test-side cause** (a wait on a condition that can be met before the test starts watching it, an order the batch
   does not guarantee, an assumption about tick timing): fix the test, keeping every assertion, timeout and bound, and
   say in the record what it now waits for.
3. **A production cause** (a disarm after preparation that can leave the accepted launch without its outcome, or an
   outcome that is never reported): describe it in the record with the evidence, add a failing regression if one is
   cheap, and stop for Root without changing production code.

## Verification

- the changed tests alone; the whole unit suite;
- the `monitor_governed_cutoff` batch and the other escrow-monitor batches repeated under the heavy-JVM lock (state the
  count and the pass rate; at least ten runs of the failing batch after a fix).

Root's hosted run on the pushed branch stands in for a complete local GameTest.

## Not in this task

Other tests, other intermittent failures, production changes, budgets, timeouts, ADRs, status files and the ledger.
