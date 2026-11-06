# TASK W314: two weather and sky GameTests that depend on their placement

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w314`, branch `codex/v1.8.0-w314`, created by Root on W300F's integration commit `ac9877cfc` (the shared head `e27b69d93` with W300F merged in), with this TASK merged in |
| Decision | Root decision R362 (QUEUE row W314) |
| Background | W300F (`records/W300F.md` on this branch) adds six GameTests, which move later batches. Earlier fixture placement fixes: [W304](records/W304.md), [W305](records/W305.md), [W307](records/W307.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W314.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W314/` |

## The failures

Both attempts of run 42222808325 on `ac9877cfc` failed shard 1 in the same two tests, each alone in its batch. All other
shards passed, and both tests pass on the shared head. The shard 1 logs of both attempts are
`D:/GitHub/ARCE-Task-Evidence/root-queue/W300F/ci-run42222808325-attempt1-shard1.log` and `-attempt2-shard1.log`;
the shard's batch filter is on the line that starts `GameTest batch filter`.

1. `ResearchEnvironmentGameTests.dayRainCoveredSkyAndUnloadedCellsPauseWithoutPayment` (batch `research_environment`)
   failed with `Rain paid` (line 45). `ResearchService.observing` (line 178) reports `WEATHER` only where the lens's
   biome has precipitation. That matches vanilla, where no rain falls in a desert or savanna, so a placement in such a
   biome would explain the failure.
2. `SolarArrayGameTests.surfaceDayNightWeatherRoofAndOverflowUseNativeSamples` (batch `solar_array_surface`) failed with
   `Surface daytime array rate differs` (line 174): the formed array's daytime credit was not 126. The test waits for
   day and for the sky above one roof position; check whether every collector sees the sky at that placement
   (terrain, trees or leftovers overhead), or whether something else at that place changes the reading.

## What to do

1. Reproduce both at the failing placement: shard 1's batch list run locally on this branch, or the full run, if you can
   show that it places the tests the same way. Say which, and record the placement and the biome and blocks found.
2. Make both fixtures independent of their placement, with the assertions, budgets and timeouts unchanged. For example,
   give the research fixture a known biome with precipitation for its area and restore it afterwards (as vanilla's
   `fillbiome` command does, with the client view updated if needed), and clear and restore the space above the solar
   array, or build both on a recorded, restored area as W304 and W307 did.
3. Check the other GameTests that set the weather or day time or depend on sky exposure (for example
   `ClassicScaleGameTests`, the other `SolarArrayGameTests` and `ResearchEnvironmentGameTests` cases, and atmosphere or
   torch tests that read rain) for the same dependence, and treat any you find the same way, or list them in the record.
4. The production checks are correct as they are (no rain in a dry biome; no credit without sky). If you find a
   production defect instead, stop and say so in the record before changing it.

## Verification

- The failing run before the change and the same run after it, at the same placement.
- The `research_*` and `solar_array_*` batches.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock. If
  `BayPassengerExitGameTests.twoHookBypassesDrainInCaptureOrder` fails there, note it: W313 is fixing it in parallel.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges this branch, which brings W300F in
with it.

## Not in this task

The research and solar rules, W300F's code, the bay exit FIFO test (W313), the ledger, the status file and QUEUE.
