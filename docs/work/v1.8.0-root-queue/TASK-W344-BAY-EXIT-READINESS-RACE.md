# TASK W344: the bay exit fixture's readiness wait under fast GameTest ticks

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w344`, branch `codex/v1.8.0-w344`, created by Root from the shared head |
| Decision | Root decision R414 (QUEUE row W344) |
| Evidence | Shard 3 of W341's branch run 42723245823 at `7cb495115`, copied to `D:/GitHub/ARCE-Task-Evidence/root-queue/W344/run42723245823-shard3.log` |
| Record | `docs/work/v1.8.0-root-queue/records/W344.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W344/` |

## Why

`BayPassengerExitGameTests.earlierDelayedCaptureDoesNotPreemptTheImmediateExit` (batch `bay_exit_delayed_one_box`,
`timeoutTicks = 400`) failed with "Didn't succeed or fail within 400 ticks". The batch started at 00:07:21.56 and the
timeout came at 00:07:23.16: 400 ticks in 1.6 seconds of wall time. The fixture never logged
`ARCE_BAY_EXIT_AREA installed`, so `BayPassengerExitGameTestFixture.setup` was still returning early at its readiness
check (entities loaded and entity ticking for the 3x3 chunks around the origin). The branch changed only Python; the
same Java passed on the shared head (run 42723457801), so this is nondeterministic. Entity loading runs as a server
task, so a wait counted in GameTest ticks can expire before wall-clock loading finishes.

## What to do

1. **Establish the cause** from the log and the code: what the readiness check waits for, which part was missing, and
   why 400 ticks were not enough. Read how earlier readiness races in this repository were handled (search the
   records for W122 and for the GameTest tick pacing change) and follow that approach.
2. **Fix the fixture's wait without loosening the test**: the measured exit, its assertions and the ticket and
   loaded-chunk audits stay unchanged, and production code is not touched. Acceptable forms include waiting on the
   real loading condition in a way that is not starved by fast ticks, or keeping the readiness phase out of the
   measured budget; raising `timeoutTicks` alone is not a fix (AGENTS.md section 5).
3. **Check the siblings**: every bay exit GameTest that uses this fixture's setup, and other fixtures with the same
   readiness pattern. Change only those with the same weakness.
4. **A regression**: show the fixed wait still completes when readiness arrives late (for example by holding the
   readiness condition false for many ticks in a test-only way), and that the old code fails that scenario.

## Verification

- The bay exit batches alone, repeated (at least 20 runs of `bay_exit_delayed_one_box`), then the full GameTest run
  with `scripts/check_gametest_log.py`; any manifest addition names only lines your tests emit.
- `./gradlew build` with every unit test.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), per step, released between steps.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; other flaky tests; the status file, QUEUE and the risk register.
