# TASK W320: two flight GameTests that keep their station after a failure

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w320`, branch `codex/v1.8.0-w320`, created by Root from the shared head |
| Decision | Root decision R374 (QUEUE row W320) |
| Background | [W318](records/W318.md) added `GameTestStationCleanup` for five escrow GameTests and found two more tests that remove their station only when they pass: `RocketFlightGameTests` (around line 149) and `RocketFlightSecurityGameTests` (around line 167). A failure or timeout there leaves a station in the shared test world, which W315 showed can break an unrelated test later |
| Record | `docs/work/v1.8.0-root-queue/records/W320.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W320/` |

## What to do

1. Make both tests remove their station, notices and platform template on success, failure or timeout, reusing
   W318's `GameTestStationCleanup` (or its pattern) rather than adding another mechanism.
2. Show with a temporary forced failure (restored before you commit; say how in the record) that the station is removed
   after a failure.
3. The assertions, budgets and timeouts stay unchanged. Test code only.

## Verification

- The two tests' batches.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Production code, other tests, the ledger, the status file and QUEUE.
