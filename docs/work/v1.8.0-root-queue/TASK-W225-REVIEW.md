# TASK W225-review: independent read-only review of the sharded GameTest workflow

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w225` at `9c900c4c0`, cut from `aebf5782d`: `29af2687d` (scripts and tests), `44cc609c5` (record) and Root's `9c900c4c0` applying W225's workflow patch. Review worktree `D:/GitHub/arce-v180-w225-review`, a detached checkout of `9c900c4c0` |
| Task it answers | [TASK-W225](TASK-W225-CI-GAMETEST-SPLIT.md), Root decision R215, owner decision D18 |
| Record and audit | `docs/work/v1.8.0-root-queue/records/W225.md`; `D:/GitHub/ARCE-Task-Evidence/root-queue/W225/WORKFLOW-AUDIT.md` |
| Hosted run | 41185583579 on `9c900c4c0`, the first run of the sharded workflow; read its jobs and artifacts if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W225-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Nothing can pass that the old single job would have failed:** every batch in exactly one shard; the union equals
   the complete runtime inventory with no test missing or run twice; a failed, timed-out, cancelled, skipped or
   missing shard, a missing artifact or an incomplete log fails the `regression` job; `if: always()` cannot let a
   failure through; the log check keeps every pattern, count and maximum (host-lag still eight across the matrix).
2. **The same code is tested:** the shards run exactly the sealed build's runtime contents (no recompilation), the
   identity checks are real, and the release JAR audit still runs once.
3. **The observer** (`scripts/ci_gametest/`): it only sets the existing batch filter property and records results
   through a delegating reporter; it changes no scheduling, timeout, retry or failure output; it is loaded only in CI
   and never ships in the product JAR.
4. **The workflow's security and hygiene:** permissions, artifact handling, no token exposure, pinned actions as in
   the original, timeouts per job.
5. **The hosted run:** whether the shards ran the expected tests, how long each took, and any failure, especially one
   caused by a new batch placement (world position or ordering dependencies the audit warns about).

## Rerun

In your review worktree only: `python -B -m unittest discover -s tests -p test_ci_gametest_shards.py` and the related
GameTest-log Python tests; the shard and union scripts on the hosted run's artifacts if it has finished. No Gradle
GameTest, native case or server. Delete generated directories afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W225-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
