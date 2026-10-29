# TASK W200-review: independent read-only review of the I6 admission pacing and encoding reuse

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w200` at `5592f0f79`, cut from `2256044ad`; the slice is `2256044ad..5592f0f79` (5 commits). Review worktree `D:/GitHub/arce-v180-w200-review`, a detached checkout of `5592f0f79` |
| Task it answers | [TASK-W200](TASK-W200-I6-RESIDUAL-COST.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W200.md` on the branch (the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W200/` |
| Hosted run | the run on `5592f0f79` (Root pushed the branch at 22:08); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W200-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W200 reports the admission burst down from 97.88 ms to 23.34 ms (`4bc9b0e0e` paces I6 admission preflight over server
ticks) and the write-back maximum from 287.81 ms to 166.67 ms (`77188e0d2` reuses the checked candidate encoding for
insertion). Both touch persistence and authorization paths.

## What to check

1. **`4bc9b0e0e`, admission pacing.** A deferred confirmation gains no destructive authority, refreshes no consent,
   skips no preflight, forces no chunk and does not reset the 200-tick request age; it revalidates identity,
   authorization and world state before use. Can a request now expire, be served twice, or be served against changed
   state? What does a player see while it waits?
2. **`77188e0d2`, encoding reuse.** The reused encoding is exactly the checked one, for the exact candidate, within
   W199's encodability witness. Can a stale or mismatched encoding be inserted, or the witness be bypassed?
3. **`d52cbf475`** ("task-local I6 batch evidence"): what it changes in the test harness, and that nothing is relaxed.
4. **The residual write-back spikes** (166.67 ms, over docs/17's 100 ms): where they go, and whether a further bounded
   remedy exists.
5. **Nothing weakened:** formats, bounds, budgets, quotas, timeouts, assertions and `RocketTransferService`.
6. **Risk text:** which sentences of R-035 these changes make wrong, for Root.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W198 (`codex/v1.8.0-w198`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes and the
`rocket.server`, `rocket.flight.persistence` and `rocket.sealed` packages; the W197, W199 and W200 Python tests.
Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build
or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W200-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
