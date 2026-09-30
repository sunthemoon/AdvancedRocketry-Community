# v1.5.0 requirement map (development)

Maps the [v1.5 plan](../../versions/V1.5.0-ORBITAL-STATION-WARP.md) checklists
(sections 9-15) to the retained development evidence. "Open" means no evidence yet.
Nothing here is candidate-bound, and nothing is a Gate approval.

Paths are under `docs/work/` unless stated otherwise.

## Section 9: automated tests

| Plan item | Evidence | State |
|---|---|---|
| Region allocation, overlap, boundaries, deletion and reuse | `StationRegistryModelTest`, `StationRegionBoundaryTest` ([station schema](../../work/v1.5.0-station-schema/VERIFICATION.md)) | Automated |
| Permission matrix | A1: `StationPermissionMatrixGameTests`, 345/345 cells ([report](../../work/v1.5.0-elevator-ui/PERMISSION-MATRIX.md)); S1 native, 24/24 ([report](../../work/v1.5.0-mig-native/PERMISSION-S1.md)) | Automated and native; V2 open |
| Orbit environment | [Orbit environment](../../work/v1.5.0-orbit-environment/VERIFICATION.md) | Automated |
| WarpState legal and illegal transitions | `StationWarpStateTest`, `StationWarpRelocationTest` ([warp schema](../../work/v1.5.0-warp-schema/VERIFICATION.md), [warp core](../../work/v1.5.0-warp-core/VERIFICATION.md)) | Automated |
| Crash recovery at every journal stage | Crash-cut JUnit (`StationWarpRelocationTest`); transfer recovery decision tests; [recovery matrix](RECOVERY-MATRIX.md) | Automated; native for a subset |
| Resources and energy charged once | `StationWarpRelocationTest`; native concurrent warps and the commit killed after its line ([MIG native](../../work/v1.5.0-mig-native/VERIFICATION.md)) | Automated and native |
| Multi-star routes and discovery requirements | [Star systems](../../work/v1.5.0-star-systems/VERIFICATION.md) | Automated |
| v1.4 station fixture migration | Native: [STATION-04](../../work/v1.5.0-station-native/VERIFICATION.md), [MIG-01a](../../work/v1.5.0-mig-missing-orbit/VERIFICATION.md), [MIG-01b/02](../../work/v1.5.0-mig-native/VERIFICATION.md), [closure](../../work/v1.5.0-closure/VERIFICATION.md) (missing body kept through a save, restart and return; gravity through a restart) | Native |

## Section 10: dedicated server, restart and multiplayer

| Plan item | Evidence | State |
|---|---|---|
| Concurrent records for 10+ stations; chunk unload | `tenChargingStationsDirtyTheRegistryOnlyOncePerFoldWindow`; `StationCheckedUpdateScaleTest` at 10, 100 and 4,096 stations ([WARP-05](../../work/v1.5.0-warp-native/VERIFICATION.md)); server-wide write spacing, `checkedWritesShareOneServerWideSpacing` ([closure](../../work/v1.5.0-closure/VERIFICATION.md)) | Automated; no native load test |
| Two stations warping at once to different targets | Native `concurrent` phase ([MIG native](../../work/v1.5.0-mig-native/VERIFICATION.md)); `countdownsDueTogetherCommitOnePerTick` | Native |
| Owner disconnect and reconnect; passengers online and offline | `countdownSurvivesOwnerLogoutAndTheRocketRuleIsWired` | Automated; real clients open |
| Docked rocket saved and restored across a warp | Native R1 through upgrade, warp, kills and restarts ([MIG native](../../work/v1.5.0-mig-native/VERIFICATION.md)) | Native |
| Catalog reload or target removal aborts safely | `aTargetRemovedDuringTheCountdownAborts`, `aRepricedCountdownAborts`; native missing orbit body ([MIG-01a](../../work/v1.5.0-mig-missing-orbit/VERIFICATION.md), [closure](../../work/v1.5.0-closure/VERIFICATION.md)) | Automated and native |
| Non-member requests refused | A1 matrix and S1 native | Automated and native |

## Section 11: manual and visual

| Plan item | Evidence | State |
|---|---|---|
| Orbited body, star, sun and gravity cues are correct | `OrbitalSkyTest`, `SkySelectionTest`, `StationSkyContextGameTests` ([orbit sky](../../work/v1.5.0-orbit-sky/VERIFICATION.md)) | Automated only; V0 not run on this host; V1 open |
| Warp countdown, status and failure feedback | GameTests capture the replies (warp core, UI-02) | Automated; V1 open |
| Stations never mix visuals or data | A1 matrix; two-station sky GameTest | Automated; V2 open |
| A real client watches its cache and position before and after a warp | none | Open (V1) |
| GUI scale and multiplayer sync | Not applicable to GUI scale (no screen, ADR-046) | Multiplayer V2 open |

## Section 12: security and abuse

| Plan item | Evidence | State |
|---|---|---|
| Forged station and target IDs refused | A1 matrix; forged/stale table in the [A1 report](../../work/v1.5.0-elevator-ui/PERMISSION-MATRIX.md); elevator validator tests | Automated |
| Non-owners cannot warp, delete or change permissions | A1 matrix; S1 native | Automated and native |
| Warp never accepts client energy or cost | ADR-046 UI-01 (commands only; no packet); `NetworkProtocolPinTest` | Automated |
| Region coordinates never load arbitrary chunks | Per-cell chunk checks in the matrix; elevator GameTest; sky GameTest; probe chunk checks in native runs | Automated and native |
| No dangerous orbital-weapon or elevator write interface | ADR-045: read-only, operator-only elevator check | Automated |

## Section 13: performance and resource budgets

| Plan item | Evidence | State |
|---|---|---|
| Station environment queries never scan all stations | Indexed `findAt` (ORBIT-04 scale measurement) | Automated measurement |
| Warp preparation spread over ticks, with limits | One commit per tick; caps of 128 confirmations and 64 countdowns | Automated |
| Multi-station SavedData not fully dirtied every tick | WARP-05 fold-window test | Automated |
| 10- and 100-station synthetic benchmark | `StationCheckedUpdateScaleTest` (10, 100, 4,096) | Automated measurement; reference hardware open |
| Real client releases its station sky cache | none | Open (V1) |

## Section 14: pass criteria

| Criterion | State |
|---|---|
| Stations can orbit any data body | Automated (ADR-041/043) |
| A complete warp between at least two star systems | GameTest `interstellarWarpCostsMoreWarnsAboutRoutesAndChangesTheContext`; native interstellar warp not run |
| Every transaction stage survives a forced stop without loss or duplication | Partly: see the recovery matrix and its known gaps |
| Permissions and energy are conserved | Automated and native |
| Old stations upgrade losslessly | Native |
| Zero Critical/High findings | Development only: the final independent review of every v1.5 slice (areas A and B) found no Critical or High issue; all Medium findings are fixed ([closure](../../work/v1.5.0-closure/VERIFICATION.md)). The candidate-bound audit is ACC-03 |

## Section 15: evidence to archive

| Item | Location |
|---|---|
| Station and warp ADRs | ADR-039 to ADR-047 |
| Recovery matrix | [RECOVERY-MATRIX](RECOVERY-MATRIX.md) (corrected handoff copy of the [C3 copy](../../work/v1.5.0-mig-native/RECOVERY-MATRIX.md)) |
| Migration report | [STATION-04](../../work/v1.5.0-station-native/VERIFICATION.md), [MIG-01a](../../work/v1.5.0-mig-missing-orbit/VERIFICATION.md), [MIG native](../../work/v1.5.0-mig-native/VERIFICATION.md) |
| Multiplayer permission report | [A1](../../work/v1.5.0-elevator-ui/PERMISSION-MATRIX.md), [S1](../../work/v1.5.0-mig-native/PERMISSION-S1.md); V2 open |
| Final independent reviews | `independent-review-final-a.zip` and `independent-review-final-b.zip` in the [closure packet](../../work/v1.5.0-closure/VERIFICATION.md) |
