# TASK W74: the intermittent six-host natural-beds GameTest

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w74-natural-beds-flake` (from the shared branch) |
| Branch | `codex/v1.8.0-w74-natural-beds-flake` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W74.md` (new) |
| Evidence | Hosted run 38718568565, first attempt, at `99053df7`: the job log is `D:/GitHub/ARCE-Task-Evidence/root-queue/ci/38718568565/job.log` (line 11953: "sixnativehostssleepkeepabsenceandtuplesandrefuselate failed! Didn't succeed or fail within 60 ticks"). The rerun passed. |

`NaturalBedGameTests.sixNativeHostsSleepKeepAbsenceAndTuplesAndRefuseLate`
(`gametest/NaturalBedGameTests.java` line 69, batch `natural_beds_matrix`, `timeoutTicks = 60`) walks six
off-world Levels: the Moon, space, Mars, Venus and Tau Ceti f and g.

## What to do

1. **Find why it can run out of its 60 ticks.**
   - Read the test, `withRoomSteps`, and how each host's room and chunks are prepared.
   - Compare with the 2026-10-10 root cause: GameTest ticks run unthrottled against asynchronous chunk
     loading. `GameTestTickPacer` is the accepted fix, and W64 used it for the etcher unload wait.
   - Reproduce it under load if you can: run the batch alone several times while another JVM is busy.
2. **Fix the cause.**
   - For example, wait for each host's chunks through the pacer before the step that needs them.
   - Do not raise `timeoutTicks`, weaken an assertion, or split the test to hide the cost (AGENTS.md
     section 5).
   - If the only honest fix is a different test structure, keep every assertion, and say why in the
     record.
3. Say what you tried, and what each run showed.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- Production behaviour, workflows, status files, the ledger and ADRs.
