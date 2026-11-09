# W325: C1 bay requirement-to-evidence matrix

This matrix is committed before any W325 native run. Authority is [TASK-W325](../TASK-W325-BAY-INTEGRATED-EVIDENCE.md)
and [the owner's decision and R386/R387](OWNER-DECISIONS-20261109.md). It claims coverage, not results or acceptance.
The contract is [W27A section 9](W27A-DESIGN.md), [W127](W127-PLAN.md), [W230](W230-DESIGN.md),
[W231](W231.md), [W238](W238.md), [W254](W254.md), [W257](W257.md), [W262](W262.md) and [W196](W196.md).

C1 is hosted run 42499122646, production source `70dd5d43e7b24a19cf5c674fe745749a090662a2`, JAR SHA-256
`fa5ec1812995ce3cca9d74e447301c66b6d5c9117789c2daae71cac4598e4832`. Every native case uses that file and two
restarts. F means `scripts/run_v180_bay_forced_stop.py`, R means `scripts/run_v180_bay_released_flight.py`, and
X means `scripts/run_v180_bay_exit_cuts.py`. W325-prefixed cases below are planned test-side compositions of existing
fixture controls; their implementation and tests must be committed before use. An empty passenger manifest is not
an online-passenger test. A GameTest is not native restart evidence, nor V1/V2 evidence.

Write scope is this matrix, `W325.md`, and bay Python runners/oracles and their Python tests. Runtime drivers, raw
logs and results are external under `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/bay/`. No production,
generated resources, build scripts, status, ledger, queue, risk, ADR or other task record changes are permitted.

## Native requirements

Each row is a distinct named side/variant. Multiple rows can legitimately share one case; the final record reports
each row separately. NOT_COVERED means the indicated requirement is not established by the mapped nearby case.

| ID | Requirement and variant | Runner and case, or NOT_COVERED reason |
|---|---|---|
| B1.1 | INTENT staged-only | F `B1_STAGED` |
| B1.2 | INTENT replaced before callback, interrupted restart and same-op retry | F `B1_MOVED` |
| B2.1 | Registration in RAM, registry staged-only | F `B2_STAGED` |
| B2.2 | Registry replaced before callback, INTENT promotion | F `B2_MOVED` |
| B3.1 | REGISTERED staged-only, source not explicitly forced, no passengers | F `B3_STAGED` |
| B3.2 | REGISTERED replaced, source not explicitly forced, no passengers | F `B3_MOVED` |
| B3.3 | REGISTERED replaced, source explicitly forced, no passengers | F `B3_SAVED` |
| B3.4 | REGISTERED replaced, source present, offline seat, copies at both ends | F `W185_B3_PRESENT` |
| B3.5 | REGISTERED replaced, source absent, offline seat, copies at both ends | F `W185_B3_ABSENT` |
| B3.6 | REGISTERED staged-only, source present, offline seat | F `W325_B3_STAGED_PRESENT` |
| B3.7 | REGISTERED staged-only, source absent, offline seat | F `W325_B3_STAGED_ABSENT` |
| B3.8 | REGISTERED staged-only, source present, online passenger | NOT_COVERED: existing copy fixture creates an offline seat; no online rider variant at this cut. |
| B3.9 | REGISTERED staged-only, source absent, online passenger | NOT_COVERED: online rider/reconnect fixture unavailable at this cut. |
| B3.10 | REGISTERED replaced, source present, online passenger | NOT_COVERED: online rider/reconnect fixture unavailable at this cut. |
| B3.11 | REGISTERED replaced, source absent, online passenger | NOT_COVERED: online rider/reconnect fixture unavailable at this cut. |
| B3.12 | Restart waits for checked startup journal, then resumes | F `B3_JOURNAL_HELD` |
| B4.1 | Idle non-data X removed, source not explicitly forced, no passengers | F `B4_DECOMMISSION` |
| B4.2 | X absent, source absent, offline seat | F `W185_B4_ABSENT` |
| B4.3 | X absent, source present, offline seat | F `W325_B4_ABSENT_X_PRESENT_S` |
| B4.4 | X live, source present, offline seat | F `W325_B4_LIVE_X_PRESENT_S` |
| B4.5 | X live, source absent, offline seat | F `W325_B4_LIVE_X_ABSENT_S` |
| B4.6 | X absent, source present, online passenger | NOT_COVERED: no online non-data cut fixture. |
| B4.7 | X absent, source absent, online passenger | NOT_COVERED: no online non-data cut fixture. |
| B4.8 | X live, source present, online passenger | NOT_COVERED: no online non-data cut fixture. |
| B4.9 | X live, source absent, online passenger | NOT_COVERED: no online non-data cut fixture. |
| B5.1 | Mixed S'/T, neutral before/after copies at both ends, offline seat | F `W185_B5_SOURCE_AFTER` |
| B5.2 | Mixed S/T', neutral before/after copies at both ends, offline seat | F `W185_B5_JOURNAL_AFTER` |
| B5.3 | Boundary before either D5 image, offline seat | F `W185_B3_PRESENT` |
| B5.4 | Boundary after both D5 images, offline seat | F `W185_B7_SAVED` |
| B6.1 | Source not explicitly forced at D5 completion | F `B6_D5` |
| B6.2 | Source explicitly forced at D5 completion | F `B6_SAVED` |
| B6.3 | Source absent, original T, offline seat | F `W185_B3_ABSENT` |
| B6.4 | Source absent, consumed T', offline seat | F `W185_B6_AFTER_ABSENT` |
| B6.5 | Destination absent after D7, consumed reconstruction | NOT_COVERED: ordinary post-retirement cases prove consumed stationary source, not explicit destination deletion/reconstruction. |
| B6.6 | Protected duplicate | F `W185_B6_PROTECTED` |
| B6.7 | Conflicting/unrecognized duplicate | F `W185_B6_UNEXPECTED` |
| B6.8 | Non-neutral source admission refusal, byte-equal package and menu reason | NOT_COVERED natively: only existing GameTest `BayDeploymentGameTests.anEscrowBoundSourceIsRefusedWithBaySourceProtectedAndKeepsItsPackages`. |
| B7.1 | D6 complete before retirement, loaded source, offline seat | F `W185_B7_SAVED` |
| B7.2 | D6 complete before retirement, cold source chunk, offline seat | F `W185_B7_COLD` |
| B7.3 | Login while retirement queued, no settlement and new proofs | NOT_COVERED natively: `BayCopiesGameTests.loginAndCopiesDuringQueuedRetirementInvalidateItWithoutSettlement` covers only the in-process GameTest. |
| B8.1 | Retirement staged-only, no passengers | F `B8_STAGED` |
| B8.2 | Retirement replaced before callback, no passengers | F `B8_MOVED` |
| B8.3 | Retirement staged-only, offline seat | F `W325_B8_STAGED_OFFLINE` |
| B8.4 | Retirement replaced before callback, offline seat | F `W325_B8_MOVED_OFFLINE` |
| B8.5 | Retirement staged-only, online passenger | NOT_COVERED: no online rider variant at this cut. |
| B8.6 | Retirement replaced before callback, online passenger | NOT_COVERED: no online rider variant at this cut. |
| B8.7 | Partial multi-REGISTERED group retirement, before replacement | NOT_COVERED: packaged fixture has one accepted package and one rejected package, not a multi-REGISTERED group. |
| B8.8 | Partial multi-REGISTERED group retirement, after replacement | NOT_COVERED: no packaged partial-group fixture. |
| B9.1 | Registration refused after INTENT, REJECTED staged-only | F `B9_STAGED` |
| B9.2 | REJECTED replaced before callback | F `B9_MOVED` |
| B9.3 | Refused package later succeeds with the same operation | NOT_COVERED: `B1_MOVED` proves interrupted re-arm, not successful retry after the B9 registry refusal. |
| B10.1 | Blocked registry, unreleased INTENT hold | F `B10_BLOCKED` |
| B10.2 | Coherent blocked INTENT, release flag staged-only | R `B10_BLOCKED_STAGED` |
| B10.3 | Coherent blocked INTENT, release flag replaced | R `B10_BLOCKED_MOVED` |
| B10.4 | Coherent REPAIR, release flag staged-only | R `B10_REPAIR_STAGED` |
| B10.5 | Coherent REPAIR, release flag replaced | R `B10_REPAIR_MOVED` |
| B10.6 | Incoherent signature authority remains frozen | NOT_COVERED natively: released conflict GameTests exist; packaged release fixture seeds coherent authority only. |
| B11.1 | Mixed shared global pending saturation, 64/server | F `B11_FULL` (bay fill; mixed-consumer coverage is GameTest-only below). |
| B11.2 | Per-rocket mixed 7 escrow + 1 bay / 8 limit | NOT_COVERED natively: no mixed-authority packaged fixture. |
| B11.3 | Retained 4,096/server independently of pending | NOT_COVERED: no packaged retained-store saturation fixture. |
| B11.4 | Retained 128/owner independently of pending | NOT_COVERED: no packaged retained-owner saturation fixture. |
| B11.5 | REPAIR operations retain charges across restart | NOT_COVERED for saturation: B10 release cuts prove one retained operation, not full capacity. |
| B11.6 | Released operations retain saturated capacity across restart | NOT_COVERED: no saturated released-flight packaged fixture. |
| B11.7 | UNKNOWN/captured/successor copies counted once across restart | NOT_COVERED: no packaged admission-union fixture. |
| B11.8 | REJECTED re-arm refuses at full capacity and later reuses its op | NOT_COVERED: no packaged capacity/re-arm fixture. |
| B12.1 | D1 receipt definite failed-before-replace | F `B12_FAILED` |
| B12.2 | D1 receipt UNKNOWN, exact retry | F `B12_UNKNOWN` |
| B12.3 | D4 receipt UNKNOWN, exact retry | F `B12_D4` |
| B12.4 | D7 receipt UNKNOWN, exact retry | F `B12_D7` |
| B12.5 | D4 receipt definite failed-before-replace | NOT_COVERED: packaged cut injects UNKNOWN, not definite FAILED, at D4. |
| B12.6 | D7 receipt definite failed-before-replace | NOT_COVERED: packaged cut injects UNKNOWN, not definite FAILED, at D7. |
| B13.1 | Registry checkpoint definite FAILED, later successful checkpoint | F `B13_FAILED` |
| B13.2 | Registry checkpoint UNKNOWN, later successful checkpoint | F `B13_UNKNOWN` |
| B13.3 | Durable INTENT restart, first-tick launch/decommission fences | F `B2_STAGED` |
| B14.1 | Unproved transaction journal, stopped before checkpoint completes | F `B14_T1` |
| B14.2 | Stop drains journal request without D1 callback | F `B14_STOP` |
| B14.3 | Journal request fails with no assembly record | NOT_COVERED natively: existing B14 GameTests cover request refusal. |
| B14.4 | Actual stale assembly record remains at D0 | NOT_COVERED: no packaged stale-record fixture; an added unrelated journal mutation is not this case. |
| B15.1 | D4 receipt replaced then exception/readback | F `B15_D4` |
| B15.2 | D7 receipt replaced then exception/readback | F `B15_D7` |
| B15.3 | D1 receipt replaced then exception/readback | NOT_COVERED: D1's UNKNOWN-before-move cut is not replace-then-throw. |
| B15.4 | Registry replaced then exception/readback | NOT_COVERED natively: MOVED UNKNOWN injection does not prove a thrown post-replacement exception. |
| B15.5 | Transaction journal replaced then exception/readback | NOT_COVERED: no bay D0 post-replacement exception fixture. |
| B16.1 | Captured retirement invalidated by superseding source save | F `B16_SAVE` |
| B16.2 | Captured retirement invalidated by late duplicate join | F `B16_JOIN` |
| B16.3 | Captured retirement invalidated by source leave/reload | F `B16_LEAVE` |
| B16.4 | Captured retirement invalidated by source motion | F `B16_MOTION` |
| B17.1 | Unresolved work ahead of a healthy group, bounded progress | F `B17_FAIR` |
| B17.2 | REPAIR group ahead of healthy groups | NOT_COVERED natively: fill fixture is not a specifically checked REPAIR queue. |
| B17.3 | Unloaded group ahead of healthy groups | NOT_COVERED natively: no packaged multi-group end-loading fixture. |
| B17.4 | Partial D1, immutable group hashes | NOT_COVERED: no packaged partial multi-bay admission fixture. |
| B17.5 | Mixed REGISTERED/REJECTED/REPAIR group | NOT_COVERED: packaged fixture cannot seed this three-phase group. |
| B18.1 | Clean stop with active INTENT | F `B18_INTENT` |
| B18.2 | Clean stop with active REJECTED | F `B18_REJECTED` |
| B18.3 | Clean stop at REGISTERED replacement | F `B18_D4` |
| B18.4 | Clean stop at retirement replacement | F `B18_D7` |
| B18.5 | Crash teardown, no callback completion | F `B18_CRASH` |
| B18.6 | Clean stop with successor write queued | NOT_COVERED: no packaged stop/successor fixture. |
| B18.7 | Clean stop with unresolved UNKNOWN write | NOT_COVERED: UNKNOWN cases resolve before their stop; active UNKNOWN stop is distinct. |
| CAP.1 | 16 pending operations per owner, refusal before loader capture, other owners retain room | NOT_COVERED natively: existing `BayLoaderSaturationGameTests.sixteenOwnedBaysRefuseBeforeLoaderCaptureAndLeaveOtherOwnersCapacity`; no packaged per-owner command. |
| EXIT.0 | S3 control: held passenger, no exit | X `X0_CONTROL` |
| EXIT.1 | Sneak-input exit, live fall offer | X `X1_OFFER` |
| EXIT.2 | Ticked-connection descent, detached playerdata flushed | X `X2_DESCENT` |
| EXIT.3 | Correction window before tracker acknowledgement | X `X3_CORRECTION` |
| EXIT.4 | Real-client-timed landing, one protected FALL, exit flushed | X `X4_LANDING` |
| EXIT.5 | Fixed 200-tick expiry with ticked connection, exit flushed | X `X5_EXPIRY` |
| EXIT.6 | Trusted movement revokes the slot without remount | X `X6_TRUSTED` |
| EXIT.7 | Same-Level teleport revokes protection, later landing unprotected | X `X7_REVOKED` |
| EXIT.8 | Held-menu SAFE exit onto loaded pad, exit flushed | X `X8_SAFE_MENU` |
| EXIT.9 | Same-tick protected landing mapped-observer smoke | X `X9_FIRST_TICK` |
| EXIT.10 | Same-tick teleport-revoked landing mapped-observer smoke | X `X10_FIRST_TICK_REVOKED` |

## GameTest regression mappings

The following existing GameTests ran in hosted regression 42499122646 at the C1 source (1,802 total). A mapping
only credits the assertions actually present. Unit-only coverage is identified as NOT_COVERED for the requested
GameTest matrix; its existence does not turn it into a native or GameTest pass. GT paths start at
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`.

| ID | Named variant | Existing GameTest, or NOT_COVERED reason |
|---|---|---|
| F2.1 | DATA replay after removed definition; no recreated initial mission | `gametest/SatelliteRegistryProofGameTests.removedDefinitionReplayExtractsDataPackageWithoutRecreatingInitialMission` |
| F2.2 | Fresh DATA deterministic initial mission | NOT_COVERED as a dedicated GameTest; unit `SatelliteLaunchServiceTest.freshDataStartsAtomicDeterministicDiscoveryMissionAndRegistrationRevalidates` covers this assertion. |
| F2.3 | Cancel initial mission before D3/D4, then preexisting replay and INTENT promotion | NOT_COVERED: no dedicated GameTest for all three entry variants. |
| F2.4 | Claim initial mission before D3/D4, replay and promotion | NOT_COVERED: no dedicated GameTest for this cross-product. |
| F2.5 | Prune initial mission history before D3/D4, replay and promotion | NOT_COVERED: no dedicated GameTest for this cross-product. |
| F2.6 | Replace initial mission before D3/D4, replay and promotion | NOT_COVERED: no dedicated GameTest for this cross-product. |
| F2.7 | Later coalesced flush/autosave succeeds after failed checkpoint, replay and promotion | NOT_COVERED: writer tests and tick GameTests do not compose this entire scenario. |
| F2.8 | Changed owner/definition/kind conflicts | NOT_COVERED as the complete GameTest matrix; unit service identity checks exist. |
| F3.1 | Bay fills shared 64/server; loader refused, then capacity released | `resourceescrow/BayLoaderSaturationGameTests.bayIntentsFillingTheSharedPendingSlotsStopALoaderUntilTheyResolve` |
| F3.2 | 63 escrow + 1 bay, reverse consumers and 7 escrow + 1 bay per rocket | NOT_COVERED by a dedicated GameTest; authority unit fixtures cover mixed consumers. |
| F3.3 | Unique union over durable/captured/successor/UNKNOWN | NOT_COVERED by a dedicated GameTest; authority unit fixtures cover it. |
| F3.4 | 64 REPAIR/released operations retain capacity after restart | NOT_COVERED: no composed GameTest/restart fixture. |
| F3.5 | REJECTED re-arm full refusal and eventual same-op admission | NOT_COVERED by a dedicated GameTest; authority unit coverage is not native restart evidence. |
| F3.6 | Independent retained 4,096/128 and mixed-store startup | NOT_COVERED by a dedicated GameTest matrix; receipt/authority unit fixtures exist. |
| F3.7 | Failed/unknown retirement retains reservation | NOT_COVERED by a dedicated complete admission GameTest matrix. |
| F3.8 | 16/owner, unchanged refusal and room for other owners | `resourceescrow/BayLoaderSaturationGameTests.sixteenOwnedBaysRefuseBeforeLoaderCaptureAndLeaveOtherOwnersCapacity` |
| F4.1 | Matching DATA identity, removed definition, terminal extraction | `gametest/SatelliteRegistryProofGameTests.removedDefinitionReplayExtractsDataPackageWithoutRecreatingInitialMission` |
| F4.2 | Non-data terminal idempotent extraction | `gametest/SatelliteKindTerminalGameTests.terminalLaunchesAnIdleSurveySatelliteAndConsumesAReplay` |
| F4.3 | Removed primary/other components, changed stats/research, changed/removed/disallowed target | NOT_COVERED as a dedicated replay GameTest cross-product; `SatelliteLaunchServiceTest` covers early dependency bypass. |
| F4.4 | Global/owner satellite, mission, unfinished, account and byte quotas full | NOT_COVERED as a dedicated GameTest matrix; service unit quota tests exist. |
| F4.5 | Dirty matching X, failed persistence, exact adapter barrier | NOT_COVERED as the full replay-specific GameTest; registry tick tests check acknowledged terminal actions. |
| F4.6 | Replace-then-throw, exact frozen readback | NOT_COVERED as a terminal/shared-service parity GameTest. |
| F4.7 | Receipt phase launch/decommission fences | `gametest/SatelliteRegistryProofGameTests.terminalLaunchAndDecommissionWaitForExactReceiptPhases` |
| F4.8 | Conflict, unauthorized caller, blocked registry, other op fence | NOT_COVERED as the complete GameTest cross-product; service unit tests and phase-fence GameTest cover subsets. |
| F5.1 | Missing physical body, DATA and solar | `rocket/server/BayDeploymentMatrixGameTests.f5RefusesAMissingBodyForDataAndSolarBays` |
| F5.2 | Non-orbitable body, DATA and solar | `rocket/server/BayDeploymentMatrixGameTests.f5RefusesANonOrbitableBodyForDataAndSolarBays` |
| F5.3 | Legacy space, DATA and solar | `rocket/server/BayDeploymentMatrixGameTests.f5RefusesTheLegacySpaceBodyForDataAndSolarBays` |
| F5.4 | Captured source body recorded in DATA receipt | `rocket/server/BayDeploymentMatrixGameTests.theCapturedSourceBodyIsTheOneTheDataReceiptRecords` |
| F5.5 | Station orbit differs from destination and changes later, both kinds | NOT_COVERED: captured-body test is not this complete station-mutation matrix. |
| F5.6 | Undiscovered allowed DATA discovery vs non-data discovery gate | NOT_COVERED as a dedicated composed GameTest; service unit tests cover fresh-kind validation. |
| F5.7 | REGISTERED replay after physical catalog removal | NOT_COVERED by the terminal removed-definition test, which exercises a different boundary. |
| RT.1 | Coalesced/autosave requests publish on later ticks | `gametest/SatelliteRegistryTickGameTests.coalescedAndAutosaveRequestsArePublishedOnLaterTicks` |
| RT.2 | Launch/decommission wait for commit acknowledgement | `gametest/SatelliteRegistryTickGameTests.aTerminalLaunchAndDecommissionCompleteOnTicksAfterTheirCommits` |
| RT.3 | DATA action exposes its stored result only after commit | `gametest/SatelliteRegistryTickGameTests.aDataActionShowsItsStoredResultAfterItsCommit` |
| RT.4 | Clean schema-4 literal/save epoch unchanged | NOT_COVERED as a dedicated GameTest; registry checkpoint/epoch unit coverage exists. |
| RT.5 | Tagged origins, ground surveys, resources/accounts, blocked raw roots across success/FAILED/UNKNOWN, all four entries | NOT_COVERED as a GameTest cross-product; registry transport unit tests cover individual paths. |
| RT.6 | Replace-then-throw without duplicate epoch advance/lost flushPending | NOT_COVERED as a dedicated GameTest; writer unit coverage exists. |
| RT.7 | Obsolete callbacks and stale before/after hashes cannot obtain permit | NOT_COVERED as a dedicated GameTest; permit-scope unit coverage exists. |
| N2.1 | UNKNOWN then mission/claim/survey mutation, fresh checkpoint, reload | NOT_COVERED as a dedicated GameTest; `SatelliteRegistryEpochTest.freshResolutionCarriesMissionAcknowledgementAndGroundSurveyStampsAcrossReload` is a unit test. |
| N2.2 | Same, coalesced flush resolution | NOT_COVERED as a dedicated GameTest; same parameterized unit scenario. |
| N2.3 | Same, autosave resolution | NOT_COVERED as a dedicated GameTest; same parameterized unit scenario. |
| N2.4 | Definite FAILED retries fresh capture | NOT_COVERED as a dedicated GameTest; same unit scenario. |
| N2.5 | Second UNKNOWN skips another carried epoch | NOT_COVERED as a dedicated GameTest; epoch/writer unit scenarios exist. |
| N2.6 | All waiting consumers stay fenced before resolution; no rematerialized reward | NOT_COVERED as a complete consumer GameTest cross-product. |

## Execution and remaining-work rules

The final [W325 record](W325.md) will resolve every row as PASS, FAIL or NOT_COVERED, with attempt count and evidence.
Native source equivalence is checked at every invocation with
`git diff --quiet 70dd5d43e HEAD -- src/main src/generated build.gradle settings.gradle gradle.properties`.
Each invocation owns and releases the backslash heavy-JVM lock, with at least 90 seconds before reacquisition.
The driver must stop a family on a production failure, preserve the raw evidence, and continue independent families.
Harness failures retain their attempts and are corrected only test-side. No switch, timeout or assertion is relaxed.

Estimated effort categories for gaps: test-side composition of an existing control takes about 1-3 hours plus native
runtime; a new packaged Java fixture requires a separately authorized candidate and about 0.5-2 engineering days per
family, followed by the entire matrix on the new candidate. Missing GameTest cross-products take about 2-5 days in
aggregate, not a release estimate. Online-passenger cases additionally need the final campaign's V2 two real clients;
menu/visual behavior needs V1. Reference-node timing and soak remain separate final-campaign work, scheduled by the
owner. No owner risk decision, enabling decision, Gate or release is made by this matrix.
