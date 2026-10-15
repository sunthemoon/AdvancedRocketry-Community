# TASK W27b-review: independent read-only review of the satellite bay block

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w27b-satellite-bay`, commits `e2848cc1` to `8073fe2c`, on top of `cf98fb0a` (the later merge `f4e4d272` only brings in the shared branch) |
| Task | `docs/work/v1.8.0-root-queue/TASK-W27B-SATELLITE-BAY-BLOCK.md` |
| Contract | `docs/work/v1.8.0-root-queue/records/W27A-DESIGN.md` section 6 and the W27b row of section 9; R-038 in `docs/11-RISK-REGISTER.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W27B.md` |
| Checkout | make a detached worktree of `8073fe2c` at `D:/GitHub/arce-v180-w27b-review` and remove it at the end |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W27B-review/REVIEW.md` (create the folder) |

This slice adds a persisted block-entity root and a save refusal (AGENTS.md section 13): it needs an
independent review before merge. The implementer was a Claude session; you are the other model.

## What to check

1. The schema-1 root codec against section 6: the field set, at most one of `package` or `consumed_op`,
   the 4,096-byte, depth-16, 256-node package bound, `Count` 1, refusal and quarantine with the data kept.
2. The package identity decoder: FUTURE and unknown-item packages kept raw, deployment and extraction
   refused.
3. The removal policy: a valid bay drops its package exactly once plus an empty bay on break and
   explosion; one shared extraction flag; no `BlockEntityTag` carrier; no owner restoration on
   placement; guarded roots refuse break, leave the explosion list and drop nothing.
4. The save refusal: used only where native serialization cannot keep the root; its scope (chunk, then
   Level after 256 coordinates) and logging match R-038; no exception is used as ordinary control flow.
5. The locks: the menu and the capability share one handler; the owner rule; the transaction claim; no
   live slot while captured; the 8-block menu distance; spectators refused.
6. The `satellite_bay_v1` adapter: exact capture, equal recapture on restore, the shared 128-payload
   budget, and the registry reservation.
7. Client and server separation: no client class reached from common code.
8. Tests: do they prove what the record claims, and do the two new log expectations have exact counts
   and real reasons?
9. Rerun the new unit tests in your worktree (`JAVA_HOME` `C:/Program Files/Java/jdk-17.0.7`). No
   GameTest or native run, no heavy-JVM lock.

## Output format

- A verdict: MERGE, MERGE-WITH-FOLLOWUP, FIX-FIRST or BLOCK.
- Findings, each with an ID, severity, file and line at `8073fe2c`, the failure scenario and a fix.
- The commands you ran and their results; the SHA-256 of REVIEW.md on the last line of your answer.
