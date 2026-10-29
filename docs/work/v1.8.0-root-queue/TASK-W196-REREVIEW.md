# TASK W196-rereview: independent read-only review of W196's R168 fixes

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w196` at `e2f5a47b8`. The R168 fixes are `c61480208..e2f5a47b8` (5 commits). Review worktree `D:/GitHub/arce-v180-w196-rereview`, a detached checkout of `e2f5a47b8` |
| Task it answers | The R168 section of [TASK-W196](TASK-W196-W27D4-RELEASED-FLIGHT.md) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-review/REVIEW.md` (SHA-256 `07d50582…d613`, BLOCK), with its probe in `probes/` |
| Record | the "Review fixes (R168)" section of `docs/work/v1.8.0-root-queue/records/W196.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196/` |
| Hosted run | the run on `e2f5a47b8` (Root pushed the branch at 17:38); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W196-R1 is closed.** Rerun the previous review's probe: a refused acknowledged effect suspends delegation and
   schedules checked-phase reconciliation, and source removal waits until the passenger obligation finishes. Check the
   new tests with an assigned rider and an endpoint save or same-position write at both endpoints.
2. **W196-R2 is closed.** An observed physical or escrow conflict from before release authority exists stays latched in
   the bounded claim state, is told apart from an eligible registry or signature problem, survives an existing release
   flag, and is rechecked before and after recovery side effects and before delegation. Check the restart and
   delayed-end tests with the recorded authority present and absent.
3. **The other changes** (`90bc892e9`, the held member bookkeeping, and `5d42a7ca5`): what they change and why.
4. **The record's conservative holds**: which cases now hold for repair that a correct design could fly, and are they
   bounded and visible?
5. **Nothing weakened:** formats, bounds, budgets, quotas, timeouts and `RocketTransferService`; the new manifest
   entries match real holds.
6. **R-043.** List the sentences that W27d4 (both rounds) makes wrong or missing, for Root's update before merge.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes, the
previous review's probe, and the `rocket.bay`, `rocket.server` and `resourceescrow` packages; the harness's Python
tests. Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData,
full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W196-R11, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
