# V130-ATM-01 independent source review

Baseline HEAD e62346d768284d75581a3c84ca64f50d73f3ff7b plus actual uncommitted implementation, observed 2026-09-27. Root is sole source writer. File digests are in source-sha256.json; tracked-diff.patch captures the reviewed tracked delta. This is not a frozen final-artifact or test-execution report.

## Findings

No remaining concrete production finding in the reviewed scope.

One medium test-isolation issue was reported and corrected during review: the independent fixture initially used persistent setChunkForced and removed tickets only on success, risking leaked forced chunks or removing another fixture's ownership. Final inspected src/adapterTest/java/io/github/sunthemoon/arceadaptertest/AtmosphereBoundaryGameTests.java:29-30,62-63,91-94 uses per-vent BlockPos keyed region tickets, automatic 140-tick expiry beyond the 120-tick test timeout, and explicit success removal. This addresses persistent ticket leakage and ownership interference; failed fixture blocks may remain at their allocated test coordinates and are overwritten on a rerun.

## Source checks

- AtmosphereBoundaryRegistry.java:48-67,70-115,118-148: owner/thread/window/reentrancy checks precede registration; all block/state-capacity validation precedes callbacks; state compilation remains private until atomic commit; runtime exceptions/null results/returned timing overruns publish no partial claims. The frozen catalog copies state/enum values, retains no callback, and closing clears temporary registry storage. Same-JVM code and non-returning callbacks are not sandboxed, as ADR-024 states.
- ServerLevelVolumeWorldView.java:42-64 preserves height/unloaded guards and sealing-tag, built-in-door, permeable-tag priority before immutable lookup. No new scan callback/world load is introduced.
- AtmosphereLevelService.java:125-129 synchronously cancels affected scans before dirty-queue repair; VolumeScanCoordinator.java:180-189 also drops affected unconsumed results. Reload invalidation clears queue/cache/coordinator, preserving vent resources. Existing numerical scan/cache/queue/vent limits remain unchanged.
- AdvancedRocketryCommunity.java:202-229 constructs and installs atmosphere/life-support services only after common-setup registration and catalog freeze. Stop cleanup is null-safe. The former constructor-order contract discrepancy is removed.
- AtmosphereServerEvents.java:88-91 filters reload-wide OnDatapackSyncEvent (null player) and executes on its owning server; individual player sync does not clear authority. Cached Forge 47.4.10 source confirms null-player notification at reload-wide PlayerList sync. This avoids treating client tag packets as server authority updates.
- Public surface adds exactly four api.atmosphere types, API minor 1.2. Build allowlist totals ten API classes; classifier extracts actual reobfuscated host bytes. Existing six API signatures are unchanged. Isolated compilation and negative internal import fixtures are added without host source/output on the consumer classpath.

## Test source assessment

13 registry tests cover state-table behavior, registered DEFAULT, ownership/unknown IDs, conflicts, null/throw atomic retry, reentrancy, stale/frozen/off-thread handles, exact per-callback timing, accumulated timing, and provider/block/state capacity. The completed-result cancellation unit regression is meaningful. Finite host GameTests cover precedence/loaded guards, immediate cancellation behind a >256-position backlog, and cache/in-flight invalidation without oxygen/energy mutation. Delaying initial cross-dimension assertions by one tick does not remove the cancellation or authority assertions.

The API-only fixture registers a custom full-collision BlockState boundary, verifies one closed event, and drives the actual production vent through closed/open/closed/replaced states via ordinary UPDATE_ALL notifications. Its oracle is eventual vent LIT state, not a direct synchronous breathability query. The host reload test directly invokes the real event handler using a constructed reload-wide event; it does not by itself prove actual datapack/tag rebinding or /reload dispatch. Packaged verification should keep these distinctions and independently establish any stronger claims.

## Commands, results, limits

Executed git status/diff/read-only source inspection, cached Forge source ZIP inspection, and git diff --check (passed), plus Temp-only report/hash capture. No Gradle, Java, GameTest, server or gameplay command was run. Static @Test counts for the proposed focused classes are registry 13, coordinator 6, artifact 9, versions 9 (37 total); these are not executed test results.

Root's builds and later packaged script are outside this independent source-only evidence. API artifact bytes/publication, genuine reload behavior, restart/provider-absence behavior, physical-client behavior, and actual test execution remain unverified by this review. No full version Gate approval is implied.
