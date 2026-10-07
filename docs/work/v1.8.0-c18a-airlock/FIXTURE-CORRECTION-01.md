# Airlock native fixture follow-up

Date: 2026-10-07. Version: v1.8.0. Status: READY_FOR_REVIEW, uncommitted and
unexecuted. This changes only `AirlockDoorGameTests.java` and this record under
the existing [airlock task](TASK-01.md). Root owns integration and execution.

## Observed failures and source basis

Hosted source `61ae5001532c2a95c3393030be8f2d36521a446a`, run `37590839225`,
attempt 1, job `112691678476`, reports two airlock failures in the canonical
`_temp/arce-v180-regression/gametest.log`:

- Line 2795: `sixIronRecipeAndSeededNativeLowerHalfExplosionLoot` fails with
  "Finite native loot samples missed a decay outcome".
- Line 2796: `bothHalfCallbacksRevokeInstalledCachedInflightAndCompletedAirBeforeTick`
  fails with "Installed producer did not establish supplied chamber".

The retained [log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-source-ci-raw-20261007-01/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log)
was independently rehashed as
`758cc8d6135c217d3f85b5aee468b9363a8a239821a8847cb249be9270099cfe`.
Its [retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-source-ci-raw-20261007-01/RETRIEVAL-01.json)
binds that cohort and artifact. This follow-up does not recount or qualify the
whole build, unit, DataGen or GameTest cohort. All original failures remain
historical failed results, including the two unrelated required rocket failures.

The isolated correction branch uses fixed base
`f410d70d33cafc021afa5c9df5d528eda814cc70`. Its original airlock GameTest
source is unchanged from the failed cohort. No production or resource bytes
are changed by this follow-up.

## Two distinct changes

The loot fixture retains exactly 64 samples, radius 4, ordinary lower/upper
loot checks, same-seed reproducibility, exact item/count/tag checks and the
requirement to observe both kept and decayed outcomes. Its seed derivation now
matches the existing `StationLightGameTests` finite native-loot fixture:
`0x9E3779B97F4A7C15L * sample`. The original consecutive seeds did not establish
the required outcome coverage in this run. Spacing deterministic seeds is a
sampling correction, not a change to the loot table, random source or expected
outcomes. Neither the native first-draw cause nor the corrected outcome counts
have been established by local execution.

The initial installed-supply failure has no established production or fixture
cause. Its original assertion and 32 controlled service-tick bound remain.
Only its failure message gains fixed scalar details: mirror/phase, vent
status/oxygen/energy, existing coordinator outcome, existing indexed-volume
presence and service tracked/active/pending/dirty/inspection counters. The
existing read-only coordinator witness is obtained before that assertion rather
than after it. No world/native query, callback, scan, authority or new production
accessor is introduced. The small ASCII diagnostic has a finite length below
512 bytes even at the scalar types' widest spellings. This is an observation
change, not a claimed supply repair.

The seven GameTest methods, assertions, timeouts, fixture cells, loaded chunk,
installed runtime/service, native callbacks, backlog, in-flight/completed
witnesses, recovery checks and cleanup are otherwise unchanged. No runtime
replacement, reflected write, direct `markDirty`, outcome-forcing hook,
configuration change or deadline/inspection-budget increase is introduced.

## Verification and remaining obligations

Fresh static checks must bind both postimages and verify the exact inverse to
the fixed original GameTest, unchanged method/annotation/assertion predicates,
scope, finite seed/message bounds and whitespace. Results and pins are in the
worker's [external report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-fixture-correction-author-20261007-c16-9d42a8/REPORT-01.md).
Read-command locator failures remain attributed tool observations, not new
product failures or independently exported raw logs.

No Java, clean build, DataGen, native GameTest, server/client or restart was run
for these corrected bytes. Different-agent actual-source review and a new
exact-committed-source hosted replay are required. If initial supply still fails,
the new scalar message is evidence for the next narrowly justified action; it
does not waive the assertion or authorize a production fix. v1.8 Required
Gates, delivery, packaged restart and real client/GPU evidence remain open.
