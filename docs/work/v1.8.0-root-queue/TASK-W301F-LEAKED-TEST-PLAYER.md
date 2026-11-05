# TASK W301F: W301's Overworld rocket fixtures leave a test player at the spawn

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w301`, branch `codex/v1.8.0-w301` at `5a9a35db0` (W301's delivery `1aabc9189` plus Root's integration commit with the shared head `9082ca899`); continue on it |
| Task it fixes | [TASK-W301](TASK-W301-SAFETY-RETURN-KICK.md); W301's record `docs/work/v1.8.0-root-queue/records/W301.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W301/ci/job-127091389410.log` (failing shard 3 of run 41979746353 at `5a9a35db0`) and `shared-77c62ef3b-shard3-127089643061.log` (the same shard passing on the shared head); W301's own local full run (three rejoin failures, in its record) |
| Record | append a section to `records/W301.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W301F/` |

## The failure

In the hosted run, batch `space_safety_return_rocket_blocked` logged in the fixture player `safetyRocket`, returned it
to the Overworld spawn arrival (`at=0, 200, 0`), and the log never shows `safetyRocket left the game`. A few batches
later `rocket_rejoin_commit_final` (`RocketRejoinCommitGameTests.unsafeImmediatelyBeforeUnguardingRelocatesAndRechecks`)
logged in at (-8.5, 64.0, 0.5), went `state=unsafe`, disconnected after ten seconds and failed with `Commit fixture
stalled: Final phase=1`. On the shared head the same test logged in at (6.5, 77.0, -6.5) and passed. W301's local full
run failed three rejoin GameTests the same way. Root's reading, not verified: the leaked player (or something else the
`space_safety_return_rocket_*` fixtures leave near the spawn, such as water that spread outside the restored cells)
makes the rejoin placement candidates near the spawn unsafe.

## What to do

1. Confirm the cause: run the W301 rocket batches followed by the rejoin batches (`rocket_rejoin_*`) in one GameTest
   server, under the heavy-JVM lock, and show the failure; then show what the fixtures leave behind (players, entities,
   blocks or fluids near the spawn).
2. Fix the fixtures so that every player they log in is logged out and every block, fluid and entity they change near
   the spawn is restored before the batch ends, whether the test passes or fails. Keep W301's assertions, timeouts
   and production code unchanged. If the cause is in production code (for example the safety return or the arrival
   choice), stop and report it with a failing test.
3. Rerun the same sequence and show the rejoin tests pass.

## Verification

The sequence above before and after; W301's focused batches; the full GameTest run with
`scripts/check_gametest_log.py` under the heavy-JVM lock; the whole unit suite if any class changes; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The rejoin code and its tests, the expected-log manifest, the ledger, the status file and QUEUE.
