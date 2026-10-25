# TASK W152-rereview-2: independent re-review of the W152G fix

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w152` at `59f08e179`: three W152G commits on `c35ab3bea` (executable source `a662d772d`). Read `git diff c35ab3bea..59f08e179` (8 files). Review worktree `D:/GitHub/arce-v180-w152-review`, a detached checkout of `59f08e179` |
| Task it answers | [TASK-W152G](TASK-W152G-ACTIVE-PROOF-PRIORITY.md), Root decision R109 |
| Earlier reviews | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152-review/REVIEW.md` and `D:/GitHub/ARCE-Task-Evidence/root-queue/W152-rereview/REVIEW.md` (W152-R3, with its probe in `probe-src/`) |
| Record | the "W152G" section of `docs/work/v1.8.0-root-queue/records/W152.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152G/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152-rereview-2/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W152-R3 is closed.** Rerun the earlier re-review's probe against the new code, or show that
   `SealedRecoveryProofSchedulingTest` covers it: 2, 3, 51 and 64 records, the ready record in each position, and the
   others held by CONFLICT and by UNLOADED. Every case seals, and the held records stay retained.
2. **No new starvation.**
   - A held record becomes eligible again at its own retry time, and is never removed or skipped for good.
   - The sealed attempt's reservation of all four ordinary attempt slots cannot block ordinary retirement for longer
     than one sealed attempt, which is at most 100 ticks.
   - Sealed attempts that wait for each other are still served in fair order.
3. **Plans wider than 21 slots.** The record says such a plan is never started and stays held. Find the largest plan
   that a supported rocket can produce within the assembly limits, and say whether it can exceed 21 slots.
   - If it can, say what happens to that rocket's resources.
   - Say whether the ordinary retirement path had the same limit before W152G.

   Root needs this to decide whether a risk-register entry is required.
4. **Nothing weakened.** No pre-existing test was deleted or loosened. The 256 inspections, the 100-tick deadline,
   the 200-tick retry, the native quotas, the pending bound, the protocol and the saved format are unchanged. The log
   checker is unchanged.
5. **Merge.** List the conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`,
   which now has W159). Root keeps both sides' guards.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the two new W152G test classes alone;
- then the W152F classes (`SealedRecoveryBudgetTest`, `SealedRecoveryAtomicBudgetTest`, `SealedRecoverySelectionTest`);
- then the `resourceescrow` and `rocket.flight.persistence` packages.

**Take the heavy-JVM lock for every Gradle run in this review**, with the atomic pattern and the 90-second hand-off;
W160 shares it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`,
`.gradle` and `logs` afterwards, and leave no generated folders outside your output folder.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W152-R4, ...), severity, file and line,
problem and suggested change; the answer to item 3; the commands you ran and their results; the SHA-256 of REVIEW.md
on the last line.