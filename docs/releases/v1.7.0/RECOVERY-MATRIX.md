# v1.7 endgame recovery matrix (V170-REC-01)

Handoff copy (C13). It is evidence for the maintainer, **not a Gate approval**; the
candidate-bound copy is produced at acceptance. Earlier recovery evidence (rockets,
stations, satellites) is in the [v1.6.0 recovery map](../v1.6.0/RECOVERY-MATRIX.md) and
earlier maps, and is not repeated here.

Each row names the state or interruption, the outcome, the rule that decides it, and
where the outcome was observed:
- **Native** means a packaged dedicated server in the
  [C13 closure](../../work/v1.7.0-c13-closure/VERIFICATION.md). Its crash cuts turn saves
  off, reach one ledger state, persist only what the cut names (nothing, the chunks, or
  the endgame root), and halt the JVM (exit 75).
- **JUnit** and **GameTest** are automated tests in the full runs of the named packets.

## Endgame root and endpoint index (ADR-054 §9, §10)

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| v1.6 world at its first v1.7 start | Opens unchanged; no endgame file until the first endgame change | ADR-054 §14 | Native (`upgrade`) |
| Malformed or future endgame root | Kept byte for byte and rewritten unchanged; the server refuses to start; nothing endgame runs | ADR-054 §10 | JUnit `aBlockedRootIsPreservedAndRewrittenUnchanged` ([C11a](../../work/v1.7.0-c11a-framework/VERIFICATION.md)) |
| A barrier or coalesced write fails | Changes stay in memory and pending, reported once, retried; a bind whose write failed is taken back | ADR-054 §10; C12R-I5 | JUnit `EndgameServiceTest`; ([C12 review](../../work/v1.7.0-c12-review/VERIFICATION.md)) |
| A transfer names a destination whose tombstone was evicted | The root still loads; the record is `DESTINATION_MISSING` until a redirect or purge | C12R-H1 | JUnit `aRegistrationToAnEvictedDestinationKeepsTheRootLoadable`, `theRestoreRuleRefusesAnUnknownSourceOrPaidEndpoint` |
| An endpoint vanished without a break | `MISSING` after an aged absence; forget or retire settles it; a returning copy is frozen | ADR-054 §9 | JUnit `forgettingMissingRecordsIsTheOwnersOrAnOperatorsAndSettlesTheTombstone`, `retiringALostEndpointSettlesAtOnce`; GameTest `aTargetRegistersOnceSavedAndARemovedIdComesBackFrozen` |
| A removed ID comes back (old chunk, copy) | Frozen with its contents; never registers again | ADR-054 §9 | JUnit `retiredAndFrozenIdsNeverRegister`, `aCopysChunkSaveDoesNotPersistTheRegisteredSourcesEntry` |
| An endpoint is broken or replaced with cargo held | Claims settled from live state in the same tick, with a barrier flush when needed | ADR-054 §9.1 | JUnit `aRemovalSettlesClaimsFromLiveStateAndARetirementReturnsThem`, `deliveryRemovalSettlesOnce` |
| An endpoint's chunk held in memory below FULL (near forced chunks) | Left alone until its chunk is FULL again or unloads; operator actions do not find it | ADR-054 §12 (C13 harness finding) | JUnit `anEndpointWhoseChunkIsNotFullIsLeftAlone` |
| A stub whose destination chunk was saved only within 40 ticks of the acknowledgement | The chunk is kept dirty until a later save prunes the stub | ADR-054 §2 (C13 review F4) | JUnit `aStubWhoseOnlySaveCameTooEarlyKeepsItsChunkDirtyUntilPruned`; native `moved-unflushed` |
| A frozen incoming conflict whose record is delivered elsewhere and pruned | Stays frozen until a resolve; never moved into the buffer; audited once | ADR-054 §11 (C13 review R2-N3, R3) | JUnit `aRedirectConflictStaysFrozenAfterItsRecordIsPruned`, `aFrozenIncomingPayloadIsAuditedOnce` |

## Cargo ledger (ADR-054 §11): native crash cuts

Railgun pairs A, B and C and an elevator pair. Each cut launches 5 items with saves off,
drives the transfer to one state, persists only what the row names, and halts the JVM.
Where a row needs the ledger unflushed, a release-test hook holds the coalesced root
flush (which otherwise runs while saves are off). The root on disk is checked against
the row before the restart. The verify phase lets the transfer finish and checks:
- where the payload ended, and that it exists exactly once;
- that the source paid the launch energy exactly when its escrow was saved;
- the row's audit anomaly.

| Cut (ADR-054 §11 row) | On disk at the halt | Outcome after the restart | Evidence |
|---|---|---|---|
| Escrow not saved | No record | Back in the source's input; nothing paid | Native `escrow-unsaved` |
| Escrow saved, not registered | No record | Registered after the restart; delivered once | Native `escrow-saved` |
| Registered, ledger not flushed (flush held) | No record | Registered again with the same `(S, seq)`; delivered once | Native `registered-unflushed` |
| Ledger flushed, release not saved | Record, not claimed | Entry dropped; delivered once | Native `registered-flushed` |
| Claimed, nothing saved (flush held) | Record, not claimed | Claimed again; delivered once | Native `claimed-unsaved` |
| Claim: ledger flushed, D not | Record `CLAIMED` | `REMATERIALIZED` once | Native `claimed-root-saved` |
| Claim: D saved, ledger not (flush held) | Record not claimed; receipt in D's chunk | `CLAIM_RECOVERED`; delivered once | Native `claimed-chunk-saved` |
| Moved, acknowledgement not flushed | Record `CLAIMED`, not acknowledged | Acknowledged again; delivered once; the stub is pruned | Native `moved-unflushed` |
| Moved, acknowledgement flushed, D's chunk not saved after the move | Record acknowledged | D moves its saved incoming copy once; the stub is pruned | Native `ack-flushed` |
| D removed (non-player), removal flushed, D's chunk not saved | Record `ARRIVED`; D's ID retired | D restored `ENDPOINT_RETIRED` and frozen with its incoming copy; an operator redirect delivers once to the source; `endpoint resolve` destroys the frozen copy (`INCOMING_DESTROYED`) | Native `removed-unsaved` (pair A) |
| D removed, removal saved | Record `ARRIVED`; D's ID retired | `DESTINATION_MISSING`; an operator redirect delivers once to the source | Native `removed-saved` (pair C) |
| S removed after its record was durable, removal flushed, S's chunk not saved | Record durable; S's ID retired | S restored `ENDPOINT_RETIRED` and frozen with its entry; the record delivers once; `endpoint resolve` discards the entry (`OUTBOX_DISCARDED`) | Native `source-removed-unsaved` (pair B) |
| Record pruned while S was unloaded with the entry saved | — | When S loads again its entry is dropped (`OUTBOX_STALE_DROPPED`); delivered once | Native `source-unloaded` (pair B, no halt) |
| Elevator cargo, anchor to terminal across Levels: claim with the ledger flushed, terminal not | Record `CLAIMED` | `REMATERIALIZED` once in space | Native `elevator-cargo` |
| Every reachable ordering | — | One payment per transfer | JUnit `transitNamedCutsMatchTheReferenceVectors`, `destinationRecoveryRowsMatchTheReferenceModel` ([C12b](../../work/v1.7.0-c12b-transit-ledger/VERIFICATION.md)) |
| **Residual:** chunk write lost after its save event, acknowledgement flushed | — | Lost, never duplicated | JUnit `transitLostWriteResidualIsDetected`, `deliveryLostWriteResidual` |

Rows 6 and 10 of the ADR-054 §11 table describe the restored endpoint differently from
§9.1; see [KNOWN-ISSUES](KNOWN-ISSUES.md). The other residual rows (a lost asynchronous
write, an operator retirement of an unloaded chunk) need faults the native harness cannot
make; the reference vectors and C12b's fault injection cover them.

## Elevator rides (ADR-059 S2): native forced stops

The rider rides up and down once with saves on before the cuts, so its player file
exists at every cut.

| Cut | Outcome after the restart | Evidence |
|---|---|---|
| During the 100-tick countdown, nothing saved after the request | Neither the ride nor its charge: the departure's energy and the rider's saved Level unchanged; the next ride commits and is charged once | Native `ride-countdown` |
| Right after a commit, nothing saved after the request | Neither: both undone together; the next ride commits and is charged once | Native `ride-commit` |
| Right after a commit, saved (`save-all`) before the halt | Both: the rider's saved Level is the arrival and the departure is charged once | Native `ride-commit-saved` |
| A server stop with a ride pending | Ticket released, ride forgotten | GameTest `membersRideUpAndDownStrangersDoNotAndNoTicketRemains` |

## Laser drill and other devices

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| A crash inside a physical layer (up to seven events) | One payment per layer, never two | ADR-055 | JUnit `everyCrashCutUpToSevenEventsSettlesToOnePaymentPerLayer`; GameTest `debtAndCreditSettleOnTheEngine` |
| Malformed device roots | Quarantined, resaved unchanged, inert | ADR-054 §2 | GameTest `malformedRootsAreQuarantinedResavedUnchangedAndInert` |
| A system switched off | New work refused; settlement, unbind and arrivals continue | ADR-054 §1 | GameTests (`restoreSwitch` batches), JUnit `settlementIntentsWorkWhileTheSystemIsDisabled` |
| Black-hole generator fuel and energy | In one block entity, saved together | ADR-054 §8 same-store rule | GameTest `aGeneratorBurnsOnlyAtASingularityPausesWhenFullAndPushes` |
