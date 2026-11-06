# TASK W315: the sleep-in-vacuum GameTest after the batch reshuffle

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w315`, branch `codex/v1.8.0-w315`, created by Root from the shared head |
| Decision | Root decision R366 (QUEUE row W315) |
| Background | The shared branch now has more than 768 distinct GameTest batch names, so every batch moved (see [W314](records/W314.md) for the last placement fix). Earlier placement fixes: [W304](records/W304.md), [W307](records/W307.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W315.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W315/` |

## The failure

`SleepRespawnGameTests.sleepIsDeniedInVacuumOnAPlanetAndInSpace` (`defaultBatch`) failed with `The vacuum fixture lies
inside a station` (line 97) in two local unsharded full runs:

- W314's run: `D:/GitHub/ARCE-Task-Evidence/root-queue/W314/full/latest.log`;
- W311F's run: its full-run log under `D:/GitHub/ARCE-Task-Evidence/root-queue/W311F/`.

It passed in W314's focused default-batch run and in hosted runs 42275141335 and earlier.

The test loops over the Moon and the Space Level. For the Moon it uses the test's own column without
`outsideEveryStation`, but it still asserts `StationRegistrySavedData.findAt(x, z)` is empty, and station regions are
Space Level coordinates. A station left at that x and z by another test would fail the Moon iteration. The Space
iteration uses `outsideEveryStation(..., 3)`. Confirm which iteration failed and why.

## What to do

1. Reproduce at the failing placement: the local unsharded full run on this branch, or a filtered run that places the
   test the same way, if you can show that it does. Record the head position, the iteration and the station found.
2. Make the fixture independent of its placement and of stations that other tests leave, with the behaviour assertions
   (the `NOT_POSSIBLE_HERE` refusal, the action-bar explanation and the unset respawn point) unchanged. The station
   precondition must check what matters for each Level: a Space position outside every station region, chosen by a
   bounded search. Say in the record why the Moon iteration does or does not need it.
3. Check the sibling tests in `SleepRespawnGameTests` and `NonPlayerAtmosphereGameTests` that call `outsideEveryStation`
   or assume no station at their column, and treat any with the same dependence the same way, or list them.
4. If a station that should have been removed is left by another test, name the test in the record; do not change it
   here unless the fix is a missing cleanup in that test's own `finally`, which you may add.

## Verification

- The failing run before the change and the same run after it, at the same placement.
- The `defaultBatch` sleep tests and the `sleep_rooms` batch.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock; it must pass in full, or name
  any other failure for Root.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Sleep and respawn rules, station regions, the ledger, the status file and QUEUE.
