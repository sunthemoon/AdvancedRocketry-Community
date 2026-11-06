# TASK W311F: keep the landing occupancy discovery under test after W311

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w311f`, branch `codex/v1.8.0-w311f`, created by Root on W311's delivered head `723f89d7d` with the shared head merged in |
| Decision | Root decision R363 (QUEUE row W311F) |
| Earlier work | [TASK-W311](TASK-W311-SEATED-CONSOLE.md) and `records/W311.md` (on this branch) |
| Record | `docs/work/v1.8.0-root-queue/records/W311F.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W311F/` |

## Background

W311 sized the rocket entity's box from its structure (R359). The landing selector (`RocketLandingPadSelector`, around
line 353) refuses an arrival in two steps:

1. rockets whose entity box touches the arrival (the quick check);
2. the structure discovery (`NamedLandingPadOccupancy`), which W13 R1 added because a parked rocket's small box can miss
   an arrival that its structure covers.

Five occupancy GameTests (two in `NamedLandingPadOccupancyGameTests`, three in `LegacyLandingPadOccupancyGameTests`)
and one in `RocketArrivalFootprintGameTests` asserted that the occupant's entity box stays clear of the arrival, so that
step 2 does the refusing ("so the census would not be exercised"). W311's commit `5ac290cee` changed them to check the
type's base box instead, and the new box now covers the arrival, so step 1 refuses first and step 2 no longer runs in
those tests.

Step 2 still matters. On the server the box covers the structure, but vanilla's entity query visits sections only from 4
blocks below the searched box upward (W311 record, "Vanilla's entity-section margin"). A rocket is indexed in the section
of its base, so the quick check can miss a tall occupant whose base section lies below that margin. A non-operational
occupant also keeps the fixed box.

## What to do

1. Make the six tests exercise step 2 again, as W13 R1 intended, with their refusal assertions unchanged. For example,
   give the parked occupant the type's base box (test-only, after assembly, without moving it) and assert that the
   quick check misses it. Make each failure message say what it checks.
2. Keep W311's new path under test separately: one GameTest in which the quick check refuses a wide occupant through
   its structure-sized box and logs `ARCE_PAD_OCCUPANCY_REFUSED` naming it (W311's commit `373e3e274`).
3. Find out whether the quick check can miss a tall occupant because of the section margin, and whether step 2 then
   refuses it. Add a GameTest for that case if it can be built within a test's area; otherwise explain in the record.
4. Change no production code unless item 3 shows a real gap; if it does, stop and say so in the record first.

No saved field, packet or protocol change.

## Verification

- The six adapted tests, the new tests, and the `landing_pads*`, occupancy, arrival footprint and seated console batches.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock. If
  `BayPassengerExitGameTests.twoHookBypassesDrainInCaptureOrder` fails there, note it: W313 is fixing it in parallel.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges this branch, which brings W311 in
with it.

## Not in this task

The entity box itself, the seated pick fallback, the console notice, other readers of the box, the ledger, the status
file and QUEUE.
