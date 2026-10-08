# Airlock next19: successor specification (fixture-only observation)

Date: 2026-10-08. Author: delegated Claude worker (not Root, reviewer or
approver). This is a proposal. No Java was written, built or run. Root decides
adoption under a separately registered source task.

## 1. Subject and fixed facts

- Subject: `bothHalfCallbacksRevokeInstalledCachedInflightAndCompletedAirBeforeTick`
  in `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/AirlockDoorGameTests.java`
  ("the test"). The base is `c7b99a2d`. The reviewer's CHECKS-01 records that its
  source tree equals packet 17's `e91ddc16`; this was not re-checked here.
- Packet 17 (`reserve-forward-integration17-root-20261008-01`) is sealed and
  unchanged. `NATIVE17-latest.log:2771` records the initial supply assertion
  (`:289-297`) failing for case 3 (`upperOnly=true phase=0`). Lines `:5740-5742`
  record 542 tests with one required failure. All earlier failures remain open.

## 2. What the evidence justifies and what it does not

Justified:

1. The service retained a terminal OPEN for this vent (`retainedOutcome=OPEN
   needsScan=false`). The scan task stops at the first OPEN (`VolumeScanTask.java:77-79`)
   and keeps terminal outcomes (`:57-58`). `AtmosphereLevelService` clears
   `needsScan` (`:362`), and scheduling skips such vents (`:326-327`). Without a
   dirty or reset event, the later supply passes do not classify again, so the
   32-pass budget gave in effect one classification.
2. The LATER (failure-time) sample shows the seed OPEN through the air branch,
   with `canSeeSky=true` and the seed below the heightmap value 182.

Not justified:

1. Which cell produced the scan-time OPEN. The seed-only bounds are the
   `VolumeBounds.single(seed)` fallback (`:365-366`), so they do not identify it.
2. The scan-time operands. The service builds its own view inside `tick()`
   (`:210-213`), and the test has no seam to record that view's returns.
3. Any cause, production defect or repair. Vanilla `canSeeSky` semantics are not
   verified here (no JAR access). `SolarGeneratorGameTests.java:255` waits for
   `!canSeeSky` after a roof is placed. That is consistent with lag, not proof.

A fixture sequencing change (ANALYSIS-18 C3) is therefore not justified now.
The smallest justified change is an observation.

## 3. Delegated returns, accounted

For the shell, `ServerLevelVolumeWorldView.observe` returns OPEN only via:

- R1: a cell outside the build height (`:43-44`);
- R2: a registered airlock delegated to `AirlockDoorBlock.observeBoundary`
  (`:51-53`). That method returns OPEN for an out-of-height position, a
  non-airlock state, an out-of-height counterpart or a mismatched pair
  (`AirlockDoorBlock.java:39,41,43,51`), and UNLOADED for an unloaded position or
  counterpart (`:40,44`);
- R3: air for which `isExposedToVacuum` is true, through native `canSeeSky`
  (`:92`) or `y >= MOTION_BLOCKING_NO_LEAVES` height (`:96-101`).

Catalog delegation (`:65-69`) returns only SEALED or TRAVERSABLE. An UNLOADED
return makes the task PENDING, not OPEN. In case 3 the seed's east neighbour is
the upper half (`:444,486-490`), so both R2 and R3 are reachable at scan time.

Worker returns: ANALYSIS-18 sections 2-3 stand, with reviewer F1 (C2 is an
entry sample) and F2 (the OPEN-path claim is limited to a loaded, in-height AIR
seed; see R2) applied. No finding is closed.

## 4. Proposed change W19 (observation only)

The prospective write scope is `AirlockDoorGameTests.java` only. No sample class
below is scan classification:

- ENTRY: once in `revokeCase`, before the vent install (`:275`).
- PRE: in the supply loop (`:284-286`), after the vent `serverTick` and before
  `service.tick()`. It is held in one overwritten local slot.
- POST: after the first `service.tick()` of this case whose
  `service.metrics().totalInspections()` grew. PRE, POST and the pass index then
  freeze, and sampling stops.
- LATER: the existing `initialSupplyDiagnostic`, unchanged.

A private static helper builds each sample, up to 640 characters (otherwise
`OVERSIZED`). It records the game time, `trackedVents`, `view.observe` for the
seed and its six neighbours, and, for the seed and `cell.above()`, `canSeeSky`,
`getBrightness(LightLayer.SKY, pos)` and the MOTION_BLOCKING_NO_LEAVES height.
The view is built as at `:347-350`.

Existing callable APIs, each with in-repository use:

- `ServerLevelVolumeWorldView(ServerLevel, boolean, AtmosphereBoundaryCatalog)`
  and `observe` (public, `ServerLevelVolumeWorldView.java:33,41`);
- `canSeeSky` (test `:398`); `getHeight(Heightmap.Types, x, z)` (`:401`);
  `getChunkNow`, `hasChunkAt` and `isOutsideBuildHeight` (`:354-384`);
- `getBrightness(LightLayer.SKY, pos)` (`PlanetaryExposureGameTests.java:217`,
  `SolarGeneratorGameTests.java:449`; import as in `StationLightGameTests.java:30`);
- `service.metrics()` (`:288`; `AtmosphereLevelService.java:255`);
- guarded reflective reads of `exposedSkyIsOpen`, `climateControlRequired` and
  `boundaries`, as at `:347-349`.

Excluded: any light-engine pending-work query. It has no in-repository use, and
its 1.20.1 name and semantics are unverified. That proof is required first.

Output: only when `supplied` is false, append ` witness19 entry=... pre[i]=...
post[i]=...` (or `post=NO_INSPECTING_PASS`) after the current diagnostic. The
total stays within 2,048 characters. Nothing is logged on success.

## 5. Preserved behaviour (must hold in the diff)

- Seven registered subjects. The six cases keep order 0-5 and
  `runAfterDelay(index + 1)`. Geometry, canister, energy, the installed
  manager/catalog and native callbacks are unchanged.
- `timeoutTicks = 40`, the 32-pass initial and recovery loops, the 8-pass
  no-republication loop, the dirty/scan budgets, and every assertion predicate and
  message prefix are unchanged. There is no retry, added tick, new callback or
  breathable substitute. In the supply phase there are no `schedule`, `tick`,
  flush or reset calls on the coordinator, service, vents or index.
- The witness owns no cell, ticket, listener or static state, and never calls
  `helper.assert*`. Every witness exception (`ReflectiveOperationException`,
  `RuntimeException`, `AssertionError`, `LinkageError`) becomes `UNAVAILABLE`
  text. It therefore cannot replace the primary assertion or pre-empt
  `Fixture.close` or `InstalledCases.close`. Those two remain the only owners of
  success, failure and timeout cleanup.

## 6. Limits of W19

- Non-atomicity: ENTRY, PRE, POST and the scan are separate moments, and reads
  within one sample are not atomic. Light data may change off the server thread
  (unverified). Equal PRE and POST values do not exclude an intervening change
  and return (reviewer F1).
- Unqualified native cache effects: the getters may touch native caches (chunk
  lookup, light sections). Their side-effect freedom is not verified. The scan
  already reads `canSeeSky` and `getHeight`; `getBrightness` is new on this path.
- Sampling also runs in passing cases: normally one ENTRY, one PRE and one POST
  per case, and at most 32 PRE samples. These are main-thread reads outside the
  service budget.
- The file grows from 574 lines to an estimated 610-620. That requires the
  AGENTS 3.4 over-500 responsibility check (no ADR is needed below 800).

## 7. Use of the result

W19 selects no cause and no repair. A later sequencing change or production
witness needs separate registration, an owner decision, confirmed vanilla
`canSeeSky` semantics, a committed-SHA failing cohort with PRE seed OPEN via
R3, and an answer to ANALYSIS-18 C4. True scan-time operands need a production
or scanner seam, which is outside this task.
