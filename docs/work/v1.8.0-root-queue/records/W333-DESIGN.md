# W333: missing bay GameTest rows

Authority is [TASK-W333](../TASK-W333-BAY-GAMETEST-ROWS.md), [WORKER-RULES](../WORKER-RULES.md),
[W325-MATRIX](W325-MATRIX.md), and [W27A section 9](W27A-DESIGN.md). This plan is committed before code.
The starting source is `0feed644a`; the assigned branch is `codex/v1.8.0-w333`.

## Scope and verification boundary

The write scope is new GameTest classes beside the existing bay, admission, registry and terminal tests,
this plan and `W333.md`, and, only if necessary, expectations for diagnostics emitted by these tests
in `scripts/gametest_expected_log.json` and its pinned-total unit test. Existing tests, assertions,
timeouts, production behavior, admission switches, native fixtures, status, queue, risk and build files
are not changed. No upstream files or assets are imported. Other workers have separate worktrees.

The tests use production services, codecs and checked files on a GameTest server. Detached stores use
the existing package-private constructors and IO injection interfaces, not a new production seam.
Live fixtures restore cached SavedData and any temporary runtime bridge in `finally`. File fixtures
own and delete their temporary directories. Restart rows use `load`, startup indexing, and the existing
`restartForTests` coordinator entry; they do not claim an actual server restart or native acceptance.

## Row plan

Names below are relative to `io.github.sunthemoon.advancedrocketrycommunity`. Each row starts `planned`.
The final record will distinguish complete rows from partial coverage and failures. Several variants
may share one method, but each variant must have its own fixture checks and assertions.

| Row | Class.method | Fixture and distinguishing assertions | Restart/reload |
|---|---|---|---|
| F2.2 | `rocket.server.BayMissionRowGameTests.freshDataHasOneDeterministicInitialMission` | Live DATA bay; initial mission ID, satellite, owner, definition and captured body all match; exactly one registry entry and mission; own operation consumes the bay. | No. |
| F2.3 | `rocket.server.BayMissionRowGameTests.cancelledInitialMissionDoesNotBlockProofReplayOrPromotion` | Cancel initial mission before D3/D4, preexisting replay, and INTENT promotion; snapshot missions after mutation and require equality after registration/proof. | Promotion uses `restartForTests`. |
| F2.4 | `rocket.server.BayMissionRowGameTests.claimedInitialMissionDoesNotBlockProofReplayOrPromotion` | Complete and claim initial mission in the same three entry variants; no extra reward, mission or satellite. | Same seam. |
| F2.5 | `rocket.server.BayMissionRowGameTests.prunedInitialMissionDoesNotBlockProofReplayOrPromotion` | Make initial history eligible and prune it using lowered fixture retention; assert initial is actually absent before proof and stays absent. | Same seam. |
| F2.6 | `rocket.server.BayMissionRowGameTests.replacedInitialMissionDoesNotBlockProofReplayOrPromotion` | Cancel initial and start a different mission; require the successor remains current and unchanged through all three entries. | Same seam. |
| F2.7 | `satellite.persistence.SatelliteReplayBarrierRowGameTests.failedCheckpointThenLaterWriteStillProvesReplay` | Failed checkpoint followed by coalesced request and autosave; prove exact identity, no recreated initial mission, and promotion proof independent of history. | Checked-file reload; coordinator cross-product is separately credited only if reached. |
| F2.8 | `satellite.service.SatelliteReplayRowGameTests.identityConflictsPrecedeReplay` | Every kind; changed owner, definition and kind, caller authorization; exact result and unchanged registry/package. | No. |
| F3.2 | `resourceescrow.BayAdmissionRowGameTests.mixedConsumersShareGlobalAndRocketLimits` | Real ledger and bay delegates share one authority; 63+1 in both directions, both consumers refuse next; 7+1 shares eight per rocket. | No. |
| F3.3 | `resourceescrow.BayAdmissionRowGameTests.writerCopiesChargeOnePendingSlot` | Durable receipt, captured UNKNOWN and queued successor; count remains 64, duplicate is ALREADY_HELD, next consumer refuses. | No. |
| F3.4 | `resourceescrow.BayAdmissionRowGameTests.repairAndReleasedReceiptsStayFullAfterReload` | Checked-file reload of 63 bay REPAIR/released receipts plus one escrow REPAIR; both consumers refuse before the first admission. | Real files, fresh authority and startup indexing. |
| F3.5 | `resourceescrow.BayAdmissionRowGameTests.rejectedRearmWaitsForExactCapacityRelease` | Reject one, reuse slot, attempt same-op rearm at full capacity; byte-equal op/key/package; resolve replacement and then rearm original op. | No. |
| F3.6 | `resourceescrow.BayAdmissionRowGameTests.retainedLimitsAreIndependentAndStartupIndexesBothStores` | Historical valid 4,096/global and 128/owner REJECTED roots with zero pending; refuse correct retention bound; mixed-store startup indexes pending before admission. | Real files and fresh startup delegates. |
| F3.7 | `resourceescrow.BayAdmissionRowGameTests.failedAndUnknownRetirementKeepReservations` | Full shared pool, guarded retirement FAILED/UNKNOWN; both consumers remain refused until exact successful removal. | No. |
| F4.3 | `satellite.service.SatelliteReplayRowGameTests.replayBypassesChangedDependenciesAtTerminalAndService` | Every kind across removed definition/primary/other component, changed stats/research and changed/removed/disallowed target; service IDEMPOTENT without mutations and actual terminal extraction. | No. |
| F4.4 | `satellite.service.SatelliteReplayRowGameTests.replayBypassesFullQuotasAtTerminalAndService` | Individually full global/owner satellite, mission, unfinished, account and byte limits; fresh control refuses while matching replay still extracts without admission. | Loaded bounded historical fixtures for accounts/bytes. |
| F4.5 | `satellite.persistence.SatelliteReplayBarrierRowGameTests.dirtyReplayKeepsPackageUntilExactAcknowledgement` | Dirty matching identity, injected failed write, terminal pending entry; package retained through failure and consumed only when exact watch is proved. | Checked-file readback. |
| F4.6 | `satellite.persistence.SatelliteReplayBarrierRowGameTests.replaceThenThrowCompletesReplayOnceFromFrozenReadback` | Move staged file then throw; readback proves frozen identity, one epoch advance, one extraction, unchanged mission/account. | Checked-file reload. |
| F4.8 | `satellite.service.SatelliteReplayRowGameTests.identityConflictsPrecedeReplay` and barrier fixture | Conflict, wrong caller, blocked root and other-operation INTENT; shared result agrees with terminal refusal; package/registry unchanged. | Future root via existing `load`. |
| F5.5 | `rocket.server.BayMissionRowGameTests.capturedOrbitSurvivesLaterStationChangeForBothKinds` | Station orbit differs from destination and later changes; actual captured plan remains authoritative for DATA receipt/mission and solar receipt/orbit. | No. If existing station fixture cannot reach both kinds, leave the untested variant NOT_COVERED. |
| F5.6 | `satellite.service.SatelliteReplayRowGameTests.undiscoveredAllowedBodyAdmitsDataButNotNonData` | Known orbitable discovery-required body; DATA starts discovery, non-data refuses before research, with unchanged package/registry. | No. |
| F5.7 | `rocket.server.BayMissionRowGameTests.registeredReplayIgnoresLaterPhysicalCatalogRemoval` | REGISTERED receipt, captured body subsequently absent; restart claim consumes without repeating fresh physical validation or registering a second X. | Existing restart dispatch seam. |
| RT.4 | `satellite.persistence.SatelliteTransportRowGameTests.cleanSchemaFourReloadIsAnObservation` | Clean current root; literal format epoch, save epoch, file bytes and mutation sequence unchanged by proof/flush. | Checked-file reload. |
| RT.5 | `satellite.persistence.SatelliteTransportRowGameTests.allWriteEntriesPreserveTaggedAndBlockedRoots` | Barrier/coalesced/autosave/stop, success/FAILED/UNKNOWN; mission/ground origin tags, survey, resources/accounts and blocked raw roots preserved. | Checked-file reload per variant. |
| RT.6 | `satellite.persistence.SatelliteTransportRowGameTests.replaceThenThrowAdvancesOnceAndKeepsLaterPendingMutation` | Replace-then-throw readback, one epoch; mutate owner state after capture, require later change stays dirty/flushPending until successor commit. | Checked-file reload. |
| RT.7 | `satellite.persistence.SatelliteTransportRowGameTests.obsoleteSessionProofCannotCoverCurrentWatch` plus bay authority fixture | Old-session callbacks/watches cannot cover current identity; stale source/destination before/after hashes cannot authorize consumption. Full coordinator-permit credit requires an actual permit assertion, not just journal refusal; otherwise explicitly NOT_COVERED. | New registry session. |
| N2.1 | `satellite.persistence.SatelliteEpochRowGameTests.unknownCheckpointResolvesFresh` | UNKNOWN, then mission start/delivery acknowledgement/ground survey mutations; fresh checkpoint includes them, skips carried epoch, reload cannot rematerialize reward. | Checked-file reload. |
| N2.2 | `satellite.persistence.SatelliteEpochRowGameTests.unknownCoalescedWriteResolvesFresh` | Same fixture; resolve through coalesced request. | Same. |
| N2.3 | `satellite.persistence.SatelliteEpochRowGameTests.unknownAutosaveResolvesFresh` | Same fixture; resolve through SavedData save entry. | Same. |
| N2.4 | `satellite.persistence.SatelliteEpochRowGameTests.failedCheckpointRetriesFresh` | Definite failure, same later mutations; fresh capture advances once rather than reusing old bytes. | Same. |
| N2.5 | `satellite.persistence.SatelliteEpochRowGameTests.secondUnknownSkipsAnotherEpoch` | Two UNKNOWN captures with intervening mutation; carried epochs strictly increase, resolution skips both. | Same. |
| N2.6 | `satellite.persistence.SatelliteEpochRowGameTests.waitingConsumersRemainFencedUntilResolution` | Before commit: claim waits, receipt drop waits, survey/analysis wait, pending ack cannot prune. After reload: no missing-mission advance or duplicate delivery. | Same. |

## Work and verification

- [ ] `W333-PLAN` Commit this plan before code; status: ready.
- [ ] `W333-TESTS` Implement row fixtures and assertions; status: planned. Keep new batches few and unchanged existing tests intact.
- [ ] `W333-NEGATIVE` For F2/F3/F4/F5/RT/N2, disable one relevant protection in an external scratch copy, run its new GameTest, retain failing assertion/log and patch, then delete the scratch copy; status: planned.
- [ ] `W333-TARGETED` Compile and run all new batches under the heavy-JVM lock; status: planned.
- [ ] `W333-REGRESSION` Full offline build/unit suite, full GameTest run and log checker, and strict repository validation; status: planned.
- [ ] `W333-RECORD` Record committed source, per-row results, commands, hashes, remaining gaps and risks, and commit without pushing; status: planned.

Every heavy step checks C: and D: free space (at least 10 GB), acquires the backslash machine lock in
one detached PowerShell script, releases its own token in `finally`, and deletes its own generated
GameTest world there. Between acquisitions the runner waits at least 90 seconds. The full suite is
required because batch HashMap order can change. New warnings may receive exact expectations only
when their fixture and reason are identified; unrelated failures stay unchanged and are reported.

Evidence is kept outside Git under `D:/GitHub/ARCE-Task-Evidence/root-queue/W333/`.
No Gate, version, production enabling, native restart, V1/V2 or performance acceptance is claimed.
