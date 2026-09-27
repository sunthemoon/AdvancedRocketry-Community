# V130-COMP-01 independent contract review

## Findings
No unresolved contract defect identified against the inspected baseline source. This is a contract review, not a review or execution of the implementation that root is beginning after contract acceptance.

Two independently recommended clarifications were adopted in accepted ADR-026 lines 53–56:
- Seat-role blocks retain one snapshot anchor each; flight capacity remains min(seatCount, 16).
- Numerically valid high-mass definitions may still produce rockets that cannot satisfy the existing bounded route-fuel calculation.

Neither recommendation changes existing limits or approves a Gate.

## Reviewed identities and scope
- Source baseline: 180fb817edeba76aef4f6571a66caaef856602aa.
- Initially reviewed proposed ADR-026 and active V130-COMP-01 log entry.
- Re-read accepted contract ee7dce744fecc0e004b0ea3aadcc9e36ef02c6f8 after the two clarifications. Actual contract bytes are retained as ADR-026-reviewed.md; reviewed-source-identities.json binds source/contract identities.
- Read-only scope: API semantics, loading ownership/precedence, bounded scan/aggregate behavior, snapshot/flight compatibility, missing registration versus missing blocks, and finite verification requirements. No implementation review, runtime tests, source edits, remote/network, or upstream asset work.
- User-supplied untracked development-doc bundle was neither read nor changed.

## Source-grounded conclusions
1. The three immutable/registration types and API minor 1.4 are consistent with [ADR-021 lines 54–72](../../../../decisions/ADR-021-PUBLIC-API-VERSION-POLICY.md#L54). Preserve the existing thirteen exports and add exactly three, with no mandatory additions to existing provider interfaces. No schema or channel bump is needed merely for additive Java API types.

2. The proposed static multi-role values fit [RocketBlockMetrics lines 3–24](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/stats/RocketBlockMetrics.java#L3) and [RocketStatsCalculator lines 30–50](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/stats/RocketStatsCalculator.java#L30). Each boolean currently contributes independently; a seat creates one anchor at [RocketStructureScanTask lines 272–280](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/scan/RocketStructureScanTask.java#L272). The old tag resolver remains exclusive and throws on multiple tags, at [RocketForgeMetrics lines 11–32](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/forge/RocketForgeMetrics.java#L11). Explicit-definition precedence must be selected before that fallback, not implemented by weakening it.

3. Loaded, air, forbidden, movable and BlockEntity checks already occur before metric resolution at [ServerLevelRocketScanWorld lines 42–74](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/forge/ServerLevelRocketScanWorld.java#L42). Definitions must not shortcut those checks. Injecting an immutable catalog into the existing manager/assembler/scan chain fits this ownership; the accepted contract does not require or authorize a mutable global registry.

4. Pre-extraction aggregate fuel rejection is a **new implementation obligation**, not existing coverage. [RocketStatsValidator lines 12–33](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/validation/RocketStatsValidator.java#L12) has only component/thrust checks. Baseline rejects capacity outside 0..2,048,000 in [RocketFuelState lines 16–18](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketFuelState.java#L16), reached during entity initialization after transaction extraction ([RocketAssemblyTransaction lines 146–188](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/transaction/RocketAssemblyTransaction.java#L146)). The proposed scan validation should fail before a successful snapshot is sent for assembly, with the declared FUEL_CAPACITY_EXCEEDED diagnostic and no extraction/journal side effects. Existing baseline enum does not yet contain that code.

5. Snapshot semantics are implementable without a definition-ID foreign key or save rewrite. [RocketSnapshotHasher lines 55–66](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketSnapshotHasher.java#L55) hashes anchors and all stats; [RocketSnapshotNbtCodec lines 329–359](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/persistence/RocketSnapshotNbtCodec.java#L329) reads saved anchors/totals directly rather than querying current tags. [RocketStructureSnapshot lines 102–134](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketStructureSnapshot.java#L102) binds seat/anchor and block counts. [RocketEntity lines 425–436](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/entity/RocketEntity.java#L425) binds saved fuel/passenger capacities to that snapshot; lines 472–473 clamp passenger capacity to sixteen.

6. Captured mass is actually used by both planners; required fuel above the existing route cap safely fails ([RocketTargetFlightPlanner lines 56–87](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketTargetFlightPlanner.java#L56), [TravelFuelFormula lines 36–49](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/travel/route/service/TravelFuelFormula.java#L36)). At 2,048 blocks, the proposed maxima produce mass/thrust <=2,048,000,000 and pre-rejection capacity <=4,194,304,000, safely within long arithmetic; valid input does not imply a viable launch.

7. Missing component registration can leave an assembled numeric snapshot operational, but that is distinct from removing actual blocks/adapters. [RocketBlockStateAdapter lines 29–48](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/forge/RocketBlockStateAdapter.java#L29) refuses unavailable/changed states; [ServerLevelRocketTransactionWorld lines 75–84](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/forge/ServerLevelRocketTransactionWorld.java#L75) still requires movable/non-forbidden states and available payload adapters. ADR-026 preserves those boundaries and conditions downgrade on loader/API compatibility.

8. The active [implementation-log entry](../../../v1.3.0-implementation-log.md#L114) separates contract/production/fixture work and keeps custom fuel consumption, remainder handling and loader migration in COMP-02. This stays within ADR-020's development exception and does not convert inherited/full Gates into passes.

## Minimum useful implementation verification
These are recommendations/acceptance checks, not executed results:
- Numeric constructor edges; multi-role block and structural override; non-engine/nonzero-thrust rejection; foreign block IDs and owner-bound definition IDs; unknown/all air variants/reserved claims; atomic duplicate failures; mutable input copying; exact count limits and owner/thread/close/reentrancy.
- Both 2,048,000 capacity and 2,048,001 aggregate capacity in otherwise valid real scans; for rejection assert unchanged source blocks/payloads, no spawned rocket and no newly retained assembly journal. Exercise pure validator and production scan integration.
- Explicit definition over a conflicting legacy role tag, while unregistered tag conflicts still fail. Mixed built-ins, all states of a mapped block, forbidden/non-movable/unsupported/unloaded checks remain unchanged. Seventeen seat blocks should retain seventeen anchors but only sixteen flight seats.
- Captured mass changes a real fuel quote; high-mass route rejection stays finite. Include failed/missing thrust/components instead of proving only stat serialization.
- Native packaged snapshot/stats/anchors/hash and flight capacity after restart with registration absent and changed; disassembly restores block states, then real reassembly applies the current definition/fallback. Do not synthesize saved totals or silently register fallback definitions in the omitted-provider phase.
- API-only independent compilation and packaging: exactly sixteen exported classes, byte identity with main, no host implementation imports, no fixture leakage.
- No long soak or full power-loss/client campaign is necessary to review this contract; those remain separate unexecuted acceptance scopes.

## Open decisions / limits
No unresolved contract decision required before this bounded implementation. The exact fixture blocks, service constructor wiring and test layout are implementation choices so long as the accepted observations above remain testable. Existing fuel-disposal consent remains authoritative; COMP-01 does not change fuel resource semantics.

No runtime implementation was reviewed or tested. No Gradle/JUnit/GameTest, packaged process, client, native persistence replay or external browsing occurred in this contract review. Source inspections do not establish the planned behavior has been delivered or that any Required Gate is satisfied.

## Actual inspection commands
- git status --short; git rev-parse HEAD; git diff -- docs/work/v1.3.0-implementation-log.md; git show --stat --oneline ee7dce7; git diff --check (exit 0).
- Numbered Get-Content reads of ADRs, active plan, governance/current version and the referenced Java sources; rg searches for metric/capacity/seat/codec/diagnostic use and existing tests.
- Read-only git show of baseline source/accepted contract for the accompanying SHA-256 inventory. Python -B created only this unique Temp report directory.
- Exploratory PowerShell brace expansion and two guessed filename/glob searches produced parser/missing-path errors; actual filenames were enumerated and read afterwards. No failed runtime test was hidden or reclassified.

## Scoped recommendation
The accepted contract is coherent with the inspected source and policy boundaries. Proceed with the finite implementation and independent actual-diff/key-test review; this report gives no full Gate or release approval.
