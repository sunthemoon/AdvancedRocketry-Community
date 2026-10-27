# TASK W128R-rereview: independent read-only re-review of the W27d1 R126 continuation

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w128r`. The continuation's own work is `3a825bcf0..660b0e468`: its merge of shared head `3a8893a01` is `3a825bcf0`, with conflicts resolved in `SatelliteMissionSavedData` and `SatelliteManager`. Root then merged shared head `dd080faa3` as `36d568db0`, with no conflicts, for a hosted regression. Review worktree `D:/GitHub/arce-v180-w128r-rereview`, a detached checkout of `36d568db0` |
| Task it answers | The R126 section of [TASK-W128](TASK-W128-W27D1-COORDINATOR-ADMISSION.md) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-review/REVIEW.md` (SHA-256 `eed6900f…5239`, BLOCK on W128R-R1 to R4) |
| Record | the "Continuation (R126)" section of `docs/work/v1.8.0-root-queue/records/W128.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R/continuation/` |
| Risk draft | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-review/input/R-042-draft-2.md`, Root's draft corrected from the previous review's audit |
| Hosted regression | run 40554256640 on `36d568db0`. Read its result with the job's raw log, or ask Root in your answer if it has not finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W128R-R1 is closed.**
   - The first-tick hook passes the complete `EscrowTestEndpoints` gate before it parses the UUID or touches the
     registry.
   - Check the negative tests (RCON enabled, a player who is not test-owned) and the command refusal test.
   - Check every new seam in `f1e978777` (restart rows, the blocked registry, the refused request) the same way: none
     may be reachable on a production server, and none may change production state outside the full gate.
2. **W128R-R2 is closed.** For each case listed in the R126 section, find the test and check that it asserts real
   receipt, index, claim, registry and package state:
   - B10 natively with two restarts, and the REGISTRY_BLOCKED and REPAIR GameTests;
   - section 8.1 tests 1, 2, 3 and 6;
   - the TRANSIT rows and the vanilla dismount;
   - loader saturation and the N5 `/kill` leak;
   - NOT_LOADED, F5, and B14 on the live coordinator.

   The record says the REPAIR row has no native test and B14's stop has no GameTest. Say whether that leaves a gap.
3. **W128R-R3.** Audit `R-042-draft-2.md` against the code, as the previous review audited the first draft.
4. **W128R-R4.** Check both record corrections.
5. **The merges.** In `3a825bcf0`, check that `launch` and `launchIdle` keep W129bc's `mutation(...)` bookkeeping and
   the launch fence that ignores permits, and that both sides' accessors survive. Check that `36d568db0` changed
   nothing outside the shared head's own files.
6. **Nothing weakened.** No format, bound, budget, quota, timeout or earlier assertion changed. The three new WARN
   manifest entries must be exact.
7. **The full GameTest.** Locally, verify-1 passed every test and failed the checker on host-lag lines (15 against
   8). verify-2 had one timeout and 22 such lines. The record compares six early batches:
   - the shared head: 9 lines;
   - this branch: 10;
   - this branch with the new matrix batch first: 16, and two proof-dependent tests failed.

   Using the hosted run and those logs, judge whether the slice adds tick load that can fail existing tests.
8. **Merge conflicts** with W164 (`codex/v1.8.0-w164`), especially `RocketManager.tick`.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the new and touched unit classes (`BayReleaseTestGateTest`, `BayReceiptsNotLoadedHoldTest`, and the bay deployment
  unit classes);
- the `resourceescrow`, `rocket.bay` and `satellite` packages;
- the harness's Python tests.

Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, runData, full build or server.
Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W128R-R5, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
