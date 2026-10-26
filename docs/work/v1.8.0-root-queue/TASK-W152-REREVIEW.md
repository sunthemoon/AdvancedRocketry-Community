# TASK W152-rereview: independent re-review of the W152F fixes

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w152` at `c35ab3bea`: seven W152F commits on `ca8cfa98`. Read `git diff ca8cfa98..c35ab3bea` (16 files); the tested production source is `29f4c4802`. Review worktree `D:/GitHub/arce-v180-w152-review`, a detached checkout of `c35ab3bea` |
| Task it answers | [TASK-W152F](TASK-W152F-SCHEDULING-AND-FAILURE-LATCH.md), Root decision R107 |
| Earlier review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152-review/REVIEW.md` (BLOCK: W152-R1 and W152-R2). Its harness and probes are in the same folder |
| Record | the "W152F" section of `docs/work/v1.8.0-root-queue/records/W152.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152F/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W152-R1 is closed.** No record can starve another: a record held by an unloaded or foreign footprint does not
   block a ready one, with lazy discovery enabled. Rerun the earlier review's `RecoveryBudgetProbe` scenario against
   the new code, or show that a repository test now covers it. The 256 budget and every timeout are unchanged, and no
   held record is discarded or skipped. Check the record's claim that the 240-visit entity census keeps its full
   allowance.
2. **W152-R2 is closed.** Each terminal preparation failure calls the adapter exactly once per carrier load, including
   direct attempts, and is latched in RAM before any second call. A reload allows one new attempt and one WARN.
   Transient holds keep their 200-tick retry. C's original root, the discarded target's removal and emptying (R96),
   and the one log line are kept. There is no new saved schema.
3. **`29f4c4802`.** It leaves a carrier whose active recovery owns the reservation to that recovery. Check that this
   cannot leave a carrier sealed forever: when the recovery ends or is abandoned, the carrier must become eligible
   again. Check that it skips no recovery record and shortens no retry.
4. **Nothing weakened.** No pre-existing test was deleted or loosened. The unit test removed in `8b53081a1` must be one
   that W152F itself added in `2d26f8069`. No budget, bound, lease, timeout or protocol changed. The log checker is
   unchanged, and new log lines have exact entries in `scripts/gametest_expected_log.json`.
5. **The idle cost.** Check the method behind the record's idle-discovery measurement (0.0532 ms mean, 0.0761 ms P95
   per tick), and say whether it supports leaving the loaded sweep as it is.
6. **Merge** with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`): list the conflicts.
   Root resolves them and keeps both sides' guards in `RocketEntity.canAddPassenger` and `RocketTransferService`.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the W152F test classes alone;
- then the `resourceescrow` and `rocket.flight.persistence` packages.

**Take the heavy-JVM lock for every Gradle run in this review**, with the atomic pattern and the 90-second hand-off;
W159F shares it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`,
`.gradle` and `logs` afterwards, and leave no generated folders outside your output folder.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W152-R3, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.