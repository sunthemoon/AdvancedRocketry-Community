# TASK W145-review: independent read-only review of W145 (governed launches queued instead of discarded before ARMED capture)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w145` at `0c1af9e1` (fix `5271f3ef`). Review worktree `D:/GitHub/arce-v180-w145-review`, a detached checkout of `0c1af9e1`; read `git diff 260e9f48..0c1af9e1` |
| Task it answers | [TASK-W145](TASK-W145-F1-CAPTURE-NOT-ENQUEUED.md) |
| Background | [TASK-W139](TASK-W139-INTERMITTENT-DISCARDED-LAUNCHES.md) and W144's R85 analysis (`D:/GitHub/ARCE-Task-Evidence/root-queue/W144/r85-analysis/analysis.json`) |
| Record | `docs/work/v1.8.0-root-queue/records/W145.md` on the branch (the plan is at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W145/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W145-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The cause.** Does the record show, from observations, why the ARMED capture was not enqueued, and does the fix
   address that cause rather than mask it?
2. **No silent drop.** After the fix, is every governed launch either enqueued and acknowledged, or refused with a reason
   the caller reports? Can a launch now wait forever, be enqueued twice, or be acknowledged against a stale capture?
3. **Bounds and order.** Queueing flights while ordinary work drains: are the queue's size and per-tick work still
   bounded; does the existing order of the ARMED write, its acknowledgement and the F2 countdown hold; do W123's
   acknowledgement rules and the checkpoint comparison still apply? No saved format, schema, bound, lease, budget or
   timeout change.
4. **Restarts.** A save, a crash or a stop while a flight is queued but not yet captured: is the outcome the same as
   before for a launch that had not reached ARMED, with nothing half-written?
5. **Tests and evidence.** Do the seven unit regressions fail on the old code; does the native evidence show 0 of 8
   before and 8 of 8 after on the same load; do the loaded filtered runs cover W139's tests; nothing weakened.
6. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`); W146 (cargo visual
   republication) runs in parallel on `codex/v1.8.0-w146`; note shared files as a merge risk only.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: W145's changed unit classes alone, then the
`resourceescrow` and `rocket.server` packages. No GameTest, benchmark or server; no heavy-JVM lock. Check 6 GiB free
memory first. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W145-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
