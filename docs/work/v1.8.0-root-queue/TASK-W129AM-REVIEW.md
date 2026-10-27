# TASK W129aM-review: independent read-only review of W129a brought up to the shared head

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w129a` at `77ee8220`: the merge `50a5aea1` of shared head `594cf3852`, then the W129aM commits up to the tested source `415e361b` and the record. Read `git diff 594cf3852..77ee8220` (all of W129a as it would merge) and `git log 50a5aea1..77ee8220`. Review worktree `D:/GitHub/arce-v180-w129a-review`, a detached checkout of `77ee8220` |
| Tasks it answers | [TASK-W129aM](TASK-W129AM-MERGE-SHARED-HEAD.md), on the earlier [TASK-W129a](TASK-W129A-WRITER-CORE-TRANSACTION-JOURNAL.md), W129aF and the W129a re-review (QUEUE rows) |
| Design | [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) (accepted 2026-10-26, four conditions) and [records/W129-DESIGN.md](records/W129-DESIGN.md) revision 2 |
| Previous reviews | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129a-review/REVIEW.md` and `.../W129a-rereview/REVIEW.md` (APPROVE) |
| Record | `records/W129a.md` on the branch, W129aM section |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129aM/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129aM-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The merge resolution.**
   - N3-B2's `expectedEntry`, `canAppend`, `acknowledgedEntry` and `acknowledgedAbsent` keep their meaning on W129a's
     `acknowledged` and `outcome` fields.
   - The manifest and its test keep both sides' entries exactly.
2. **The caller audit is complete.** Find every use of the transaction journal's checkpoint, acknowledgement, absence
   query and `canAppend` on the merged tree, including N3-B1, N3-B2 and its follow-ups, W80 and the bay. Check each
   against the record's audit. For each, `WRITE_PENDING` must mean "not proved yet, wait": never proved, refused,
   held, released or retired, and no blocks, entities, fuel, U or T may change on it.
3. **The sealed journal port** (`ac870010`, `b86a28bc`):
   - the wait on `WRITE_PENDING`;
   - the restored rule that a COMMITTED result allows the next attempt on the next tick;
   - one capture per tick and 200 ticks after a failure.

   Look for a sequence where a sealed operation waits forever or runs an effect before its acknowledgement.
4. **Test transitions.** Each changed fixture or test is a named asynchronous-result transition, and no assertion is
   removed or weakened. That includes the two fixtures that still write and remove U within one tick, and `80e8aae3`'s
   `save(File)` transition. Also check that the tick-pacing change `5877876a` was fully reverted by `415e361b`.
5. **The await clock.** W129bc found that after an await, later server ticks must advance the registry writer's
   cadence again, and fixed that for the registry (`79f2653a` on `codex/v1.8.0-w129bc`). Check whether W129a's
   transaction writer has the same behaviour, and whether it can reach production or only test and release-test
   servers.
6. **ADR-074 conditions.** The lifecycle, stop path, drain, join and logging are unchanged from the approved W129a, or
   each change is justified. B2 was rerun at the bound. The native T1 to T3, R6 and R7 cuts and the eight W152 cases
   ran on the tested source.
7. **The record's open risk.** At the 64 MiB bound, a sealed C1 or C3 step can wait for one journal write longer than
   the 100-tick proof window, then logs DEADLINE and retries. Confirm that no effect runs early, and say whether this
   is an availability residual or a defect.
8. **Merge.** List conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), with
   W152J (`codex/v1.8.0-w152j` at `cf26f6df4`) and with W129bc (`codex/v1.8.0-w129bc`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the W129a classes, the W123 matrix, `SealedJournalWritePendingTest` and `SealedWorkSchedulingTest` alone;
- the `resourceescrow`, `rocket.server`, `rocket.sealed` and `rocket.persistence` packages.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; other workers
share it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle`
and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W129aM-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
