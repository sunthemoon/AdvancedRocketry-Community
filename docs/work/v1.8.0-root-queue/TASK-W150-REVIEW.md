# TASK W150-review: independent read-only review of W150 (N3-B1, the predecessor-aware terminal proof and I7)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w150` at `d4edf32f` (base `0617c3e0`). Review worktree `D:/GitHub/arce-v180-w150-review`, a detached checkout of `d4edf32f`; read `git diff 0617c3e0..d4edf32f` |
| Task it answers | [TASK-W150](TASK-W150-N3-B1-PREDECESSOR-PROOF.md), with Root decisions R87 and R89 (QUEUE) |
| Design | [records/W148-DESIGN.md](records/W148-DESIGN.md) (sections 1, 2, 4, 5 and the N3-B1 row of section 7) and its review |
| Record | `docs/work/v1.8.0-root-queue/records/W150.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W150/` (`R89-EVIDENCE-INDEX.json`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W150-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **I7 closed.** T is kept until the destination (D1) and exclusion proof, then replaced exactly once; no order of
   save, crash or restart leaves a rocket with neither T nor a proven single copy, or lets an old end act after it lost
   its exclusion authority.
2. **R89, the relaunch.** A relaunch whose destination unloaded tickets its proof columns within the unchanged caps
   (three ticket steps per demand, 200-tick spacing, four starts per tick), proves and proceeds; it refuses only after
   the demand is spent, with the existing reason, and never holds forever. Can a player force chunk loading at a chosen
   position or exceed the caps through it?
3. **R87's answers kept.** Unsupported destination storage refuses instead of R34's R5(c) fallback; W98's census and
   backoff, W130F's caps and the source fallback elsewhere unchanged; no global journal hook, cargo or escrow admission,
   or terrain feature (N3-B2).
4. **Constraints and tests.** No saved format, schema, bound, lease, budget, timeout or protocol change; new classes under
   500 lines; nothing weakened; the red-green relaunch test fails on the pre-R89 head; the 29-case native matrix covers
   B-PRE, B-REPLACE and B-HOLDS with two restarts each; each new log line has its manifest entry.
5. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`); W151 (W98-R1) runs in
   parallel on `codex/v1.8.0-w151`; note shared files as a merge risk only.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: W150's new and changed unit classes alone,
then the `rocket.server` and `rocket.flight.persistence` packages; the native oracle's Python tests. No GameTest,
benchmark or server; no heavy-JVM lock. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle` and
`logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W150-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
