# TASK W151-review: independent read-only review of W151 (W98-R1, the shared-slot ordinary capture exclusion)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w151` at `be3200c3` (base `84262cfb`; tested source `116e0035`). Review worktree `D:/GitHub/arce-v180-w151-review`, a detached checkout of `be3200c3`; read `git diff 84262cfb..be3200c3` |
| Task it answers | [TASK-W151](TASK-W151-W98-R1-IMPLEMENTATION.md), with Root decisions R88 and R90 (QUEUE) |
| Design | [records/W149-DESIGN.md](records/W149-DESIGN.md) and its review (W149-R1, R2) |
| Record | `docs/work/v1.8.0-root-queue/records/W151.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W151/`, `W151-followup/` and `W151-R90/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W151-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Both proofs sound.** Generation-scoped capture exclusion: no governed proof accepted over a newer save it did not
   see; no ordinary census completed from a stale capture; a genuine save still invalidates a proof.
2. **The bound.** The governed proof completes within the stated bound under continuous ordinary captures, with the
   batch-wide dependencies counted (W149-R1); the P3 native evidence (cycles of 26, 20 and 20 ticks against 1,860;
   the 4,000-tick ceiling unchanged) measures what it claims; R90's harness re-request is what a player would do and
   does not hide a product defect.
3. **The ordinary wait.** A deferred ordinary capture may wait indefinitely by design (R-035); check it cannot block
   W98's retirements, W130F's fill retirements or W150's I7 proof beyond what the design states, and what happens when a
   fill demand expires before the pin releases (W149-R2).
4. **Constraints and tests.** No saved format, schema, bound, lease, budget, timeout or protocol change; no server-thread
   block; per-tick work bounded; new classes under 500 lines; nothing weakened; tests fail on the old code; each new log
   line has its manifest entry; the two intermittent GameTests the worker rate-checked are not made more likely.
5. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and with W150
   (`codex/v1.8.0-w150`, N3-B1, which touches the retirement coordinator): list the shared files and any conflict.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: W151's new and changed unit classes alone,
then the `resourceescrow` and `rocket.server` packages; the native runner's Python tests. No GameTest, benchmark or
server; no heavy-JVM lock. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W151-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
