# TASK W146-review: independent read-only review of W146 (cargo changes republish the rocket's visual state)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w146` at `f2799c0f` (base `260e9f48`). Review worktree `D:/GitHub/arce-v180-w146-review`, a detached checkout of `f2799c0f`; read `git diff 260e9f48..f2799c0f` |
| Task it answers | [TASK-W146](TASK-W146-CARGO-VISUAL-REPUBLICATION.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W146.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W146/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W146-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The trigger.** Every cargo change that alters the rocket's snapshot (load, unload, settlement, reconciliation
   roll-forward, disassembly and any other path that publishes a cargo endpoint) marks the visual state and republishes
   once; an unchanged snapshot and a refused operation send nothing. List each path you checked.
2. **Authority and limits.** The server stays authoritative; the packet carries no new field; existing size limits hold;
   republication happens on the server thread at the publication point, not before a write is acknowledged, so a client
   never sees a state that a crash would undo.
3. **Cost.** Republication is bounded per tick; W146's figures (mean 1.438 ms, maximum 3.411 ms for publication and send)
   are measured where they claim to be.
4. **Tests.** The connected-player GameTests assert exactly one updated-hash packet after a load and after an unload,
   none after a refusal, for fluid and item devices; they fail on the old code; nothing weakened.
5. **The reference cycles.** W146 reports two fluid-unloader cycles that did not retire in the reference harness; say
   whether W146's change could cause that, or whether it comes from the base (W146 predates W145's launch-queue fix).
6. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`, which now has W145);
   note shared files as a merge risk only.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: W146's changed unit classes alone, then the
packages they belong to. No GameTest, benchmark or server; no heavy-JVM lock. Check 6 GiB free memory first. Delete the
worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W146-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
