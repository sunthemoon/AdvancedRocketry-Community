# TASK W378: diagnose and fix the intermittent concurrent-flight fixture wait

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w378`, branch `codex/v1.8.0-w378`, at the shared head |
| Decision | Root decision R480 (QUEUE row W378) |
| Record | `docs/work/v1.8.0-root-queue/records/W378.md` |
| Write scope | The GameTest class that holds `pendingSpawnDoesNotPauseAnotherCountdownAscentOrTransit` and its support, the production code of the paths it covers if the cause is there, their tests, and the record. No register, ADR, status file or QUEUE change. |

## Why

Attempt 1 of W370's branch run 44109049691 failed `pendingSpawnDoesNotPauseAnotherCountdownAscentOrTransit` in shard 2
with "Concurrent-flight fixture exceeded its bounded wait". The same game code passed on the shared branch, and
attempt 2 passed. The status file keeps it as an open item. W375 found that another intermittent failure
(`EscrowGovernedLiveReturnGameTests`) came from the periodic autosave every 6,000 ticks revoking a proof. Its record
[W375](records/W375.md) explains the mechanism and the fixture convention it used.

The shard log is `D:/GitHub/ARCE-Task-Evidence/root-queue/W370/hosted/run44109049691-shard2.log`.

## What to do

1. **Find the cause.** Read the log around the failure and the code path the fixture waits on. Check first whether
   the failure brackets an autosave tick (a multiple of 6,000). Establish which condition never arrived within the
   bound, and why.
2. **Reproduce it** if you can, for example with a deliberate save at the suspected point, or by repetition.
3. **Fix the cause.**
   - **A wrong test assumption:** wait for the promised condition, or outside the autosave window as W375 did.
   - **A production defect:** fix it, with a test that fails without the fix.

   Raising a timeout or a bounded wait, loosening an assertion or deleting a test is not a fix (AGENTS.md section 5).
4. **Verify.** Repeat the affected batch many times across autosaves, then run the full unit suite and the full
   GameTest server with the log checker under the heavy-JVM lock.

## Rules

- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Heavy runs go under the lock.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives the cause with its evidence, the fix and its kind, the commands and results, and the repetition
  counts. Its last line, and the last line of your answer, is the path of `records/W378.md`.
