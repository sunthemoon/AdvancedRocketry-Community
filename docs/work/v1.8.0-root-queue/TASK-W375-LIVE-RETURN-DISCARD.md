# TASK W375: diagnose and fix the intermittent "Live return launch preparation was discarded"

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w375`, branch `codex/v1.8.0-w375`, at the shared head |
| Decision | Root decision R471 (QUEUE row W375) |
| Record | `docs/work/v1.8.0-root-queue/records/W375.md` |
| Write scope | `resourceescrow/EscrowGovernedLiveReturnGameTests` and its support, the production code of the governed live return if the cause is there, their tests, and the record. No register, ADR, status file or QUEUE change. |

## Why

The GameTests of `EscrowGovernedLiveReturnGameTests` fail now and then with "Live return launch preparation was
discarded", two ticks after the rocket becomes active. On a re-run they pass. The status file keeps this as an open
item. It has recurred twice in a week, so it must be understood and fixed before candidate C4. The occurrences:

| Run | Test | Evidence |
|---|---|---|
| Hosted run 39421969414 at `dcebe3a8` (W116's merge) | `rejectedDestinationSpawnReturnsToF6WithPassenger` | QUEUE, the W116 row |
| W248's local full run, 2026-11-02 | `rejectedDestinationSpawnReturnsThroughSharedSlotWithPassenger` | [W248](records/W248.md), "Full GameTest result" |
| Branch run 43360946092, attempt 1, shard 3 | `countdownCancellationReachesReturnedF6WithPassenger` | `D:/GitHub/ARCE-Task-Evidence/root-queue/W358/hosted-43360946092-shard3.log` |
| Branch run 44109049691, attempt 1, shard 3 | `dismountAfterReturnCompletesCancelledF7AndF8WithoutReseating` | `D:/GitHub/ARCE-Task-Evidence/root-queue/W370/hosted/run44109049691-shard3.log` |

## What to do

1. **Find the cause.** Read the failing logs and the code path that discards the launch preparation. Establish which
   check refused, on which tick, and why it can pass on one run and fail on the next. Candidates are:
   - the server falling behind (the logs show "Can't keep up" warnings);
   - an asynchronous load;
   - batch neighbours;
   - an order that depends on timing.

   GameTest ticks are not throttled, which caused earlier flakes in this project.
2. **Reproduce it** deliberately if you can: repeat the affected batch, add artificial load, or run it beside its
   usual neighbours. Show the failure with the cause in view.
3. **Fix the cause.**
   - **The test's assumption is wrong** (it expects a state on a fixed tick that the code only promises eventually):
     wait for the promised condition instead.
   - **The production code discards a preparation that it should keep:** fix the production code, with a unit test or
     GameTest that fails without the fix.

   Raising a timeout or a tick budget, loosening an assertion or deleting a test is not a fix (AGENTS.md section 5).
   Say which kind the fix is. If it changes a saved form, a packet or a public ID, stop and report.
4. **Verify.** Run the affected batch repeatedly, the full unit suite, and the full GameTest server with the log checker
   under the heavy-JVM lock. Then run the class's batches many times in a row, enough to show the failure no longer
   appears where it appeared before.

## Rules

- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Heavy runs go under the lock.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives the cause with its evidence, the fix and its kind, the commands and results, and the repetition
  counts. Its last line, and the last line of your answer, is the path of `records/W375.md`.
