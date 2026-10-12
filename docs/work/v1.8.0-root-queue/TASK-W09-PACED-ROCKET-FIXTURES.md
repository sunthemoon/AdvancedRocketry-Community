# TASK W09: pace the rocket test fixtures for entity readiness

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w09-paced-fixtures` |
| Branch | `codex/v1.8.0-w09-paced-fixtures` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W09.md` |

## Why

Two required GameTests fail intermittently in hosted CI. In both, a fixed tick count
waits for asynchronous chunk or entity loading:

1. **Run 38206390869, attempt 1, on the shared branch at `ebf4c07b`.**
   `RocketNavigationGameTests.catalogServiceSharesReopenBudgetAndRecoversLatestGeneration`
   failed with "Flight test assembly did not spawn a rocket".
   - `RocketFlightGameTestFixtures.assembleFueledRocket` assembles one tick after the test
     starts.
   - The assembly succeeds, but `level.getEntity(id)` returns null. The spawn chunk's
     entity section is not loaded yet, so the new entity is not visible.
2. **Run 38084795112.** `PlanetaryWorldGameTests.earthMarsVenusEarthKeepsOneRocketAndExactFuelDebits`
   checks each leg after a fixed `runAfterDelay(270)`. The Venus leg's destination had
   `entities_loaded=false` and sat in `WAIT_ENTITY_READY` for 110 ticks.

`GameTestServer` never sleeps, so ticks run far faster than background loading. The
cause, and the existing fix pattern, are recorded in
`docs/work/v1.8.0-gametest-pacing/`: `gametest/GameTestTickPacer`, as used by
`TauCetiPathGameTests` and `RocketDestinationReadinessGameTests`.

## Scope

1. **`RocketFlightGameTestFixtures`:**
   - Before assembly, wait in paced ticks (`GameTestTickPacer`) until the origin chunk's
     entities are loaded (`ServerLevel.areEntitiesLoaded(chunkPos.toLong())` or the
     equivalent).
   - After assembly, if the entity is not yet visible, wait the same way until it is.
   - Keep a bounded number of waiting ticks inside each caller's existing `timeoutTicks`.
     Do not raise any timeout.
   - Assembly is currently synchronous. If it has to become a continuation, adapt each
     caller, and make sure no assertion is dropped.
2. **`PlanetaryWorldGameTests`:** replace the fixed per-leg `runAfterDelay(270)` with a
   paced wait for the leg's landing: the flight state reaches LANDED at the requested body,
   or the existing failure diagnostics fire. The overall `timeoutTicks` stays the same.
3. Search the gametest package for other tests that assemble with this fixture, or that
   wait a fixed number of ticks for a rocket transfer. Pace them the same way. List each
   one in your record.

## Rules for this task

- This changes test code only. Production code stays the same.
- Never weaken an assertion, remove a check or raise a timeout.
- If a wait cannot fit inside the existing timeout, record it and leave it failing visibly.

## Tests and commands

- The whole unit suite once.
- `runData` and `runGameTestServer` (under the lock), then the log checker. Report the
  GameTest totals.
- Run `runGameTestServer` a second time and report both totals, because these failures
  are intermittent.

## Not in this task

- Production rocket code.
- The vanilla IOWorker race (W08).
- Status files and the ledger.
