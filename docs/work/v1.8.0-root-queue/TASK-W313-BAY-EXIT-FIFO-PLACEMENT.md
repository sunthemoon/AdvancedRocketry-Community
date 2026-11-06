# TASK W313: the bay exit FIFO GameTest in local full runs

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w313`, branch `codex/v1.8.0-w313`, created by Root from the shared head |
| Decision | Root decision R361 (QUEUE row W313) |
| Background | `BayPassengerExitGameTests.twoHookBypassesDrainInCaptureOrder` (batch `bay_exit_fifo`) failed in three local unsharded full runs and passed in every hosted shard so far. Earlier fixture placement fixes: [W304](records/W304.md), [W305](records/W305.md), [W307](records/W307.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W313.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W313/` |

## The failure

- Assertion: `Exact first winner changed` in `BayPassengerExitGameTestFixture.checkArbitration` (line 416). The first
  SAFE exit was candidate 29, after 29 probes, where the FIFO case expects candidate 5 after 5 probes.
- The result was `SAFE` at Overworld `(229.5, -56.0, 1464.5)`, with 1,305 reads, in all three runs.
- Runs and logs:
  - W309's full run on `13540d513`: `D:/GitHub/ARCE-Task-Evidence/root-queue/W309/verification/final-latest.log`;
  - both of W300F's full runs on `704025b39`: `D:/GitHub/ARCE-Task-Evidence/root-queue/W300F/final/` and
    `D:/GitHub/ARCE-Task-Evidence/root-queue/W300F/outbox-corrected-final/full-latest.log`.
- It passed in hosted runs 42213632920, 42217736476 and earlier shared runs, where the test sits at another place.
- Neither W309 nor W300F changes passenger-exit code. Both add GameTests, which move later batches.

## What to do

1. Reproduce at the failing placement: the local unsharded full run on this branch, or a filtered run that puts the
   batch at the same place, if you can show that it does. Say which.
2. Find what makes candidates 5 to 28 unsafe there: terrain, ambient water or lava, blocks or entities left by an earlier
   batch, unloaded chunks, or something else. Show the evidence.
3. Make the fixture independent of its placement, with the assertions, budgets and timeouts unchanged. For example, give
   it a recorded, restored exit area as W304 and W307 did; reuse `RocketRejoinFixturePad` if it fits. Check the
   sibling cases in `BayPassengerExitGameTests` that assert exact ordinals, and treat any with the same dependence the
   same way.
4. If the cause is in production code (the exit selector or its budget), stop and say so in the record before changing
   it.

## Verification

- The failing run before the change and the same run after it, at the same placement.
- The `bay_exit_*` batches and the other passenger-exit GameTests.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock; it must pass in full.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Passenger-exit behaviour, the exit selector's order and budgets, other fixtures' failures (report them), the ledger,
the status file and QUEUE.
