# v1.6 satellite, mission and delivery recovery matrix (V160-REC-01)

Handoff copy (C9). It is evidence for the maintainer, **not a Gate approval**; the
candidate-bound copy is produced at acceptance. Earlier `data` mission, discovery and
research recovery evidence is in the [v1.0.0 recovery map](../v1.0.0/RECOVERY-MATRIX.md)
and is not repeated here.

Each row names the state, what an interruption leaves, the rule that decides recovery,
and where the outcome was observed:
- **Native** means a packaged dedicated server in the [C9 closure](../../work/v1.6.0-c9-closure/VERIFICATION.md).
  Its crash cuts halt the JVM (exit 75) right after one store became durable and before
  the other.
- **JUnit** and **GameTest** are automated tests in the full runs of the named packet.

## Registry (ADR-050)

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| v1.5 world (registry root 2) at first start | Migrated before start to root 3 with a backup; every mission, satellite and account kept; a restart does not migrate again | ADR-050 §10 pre-start migration | Native (`upgrade`: `[ARCE-BETA-1002] … migrated=1, backup=…`; `backlog`: `migrated=0`); JUnit `SatelliteRootThreeTest` ([C7a](../../work/v1.6.0-c7a-model/VERIFICATION.md)) |
| Root over the count limits (legacy or retained) | Loads and keeps its records; admission refuses new records until retention drains it | ADR-050 §6, §10 | JUnit `SatelliteRootThreeTest` (8,192 missions) ([C8a-1](../../work/v1.6.0-c8a-scheduler/VERIFICATION.md)) |
| Broken references at load (missing satellite, owner mismatch, unbound, instance mismatch) | Mission QUARANTINED, satellite RECOVERY_REQUIRED or instance QUARANTINED; nothing deleted, completed or paid; operator release, cancel or recover | ADR-050 §9 | JUnit `RegistryLifecycleTest`; GameTest lifecycle batch ([C8a-1](../../work/v1.6.0-c8a-scheduler/VERIFICATION.md)) |
| Future root, unknown kind or over-bound file | Whole registry blocked and preserved; resource actions refused; terminals keep their receipts | ADR-050 §9 | GameTests in the blocked batch ([C7](../../work/v1.6.0-c7-close/VERIFICATION.md), [C8b](../../work/v1.6.0-c8b-delivery/VERIFICATION.md)); JUnit `DeliveryCrashCutTest.aBlockedRegistryChangesNothingAndKeepsTheReceipt` |
| Changes waiting for the coalesced flush when the process dies | Lost changes are repeated after the restart (completions, pruning), or recovered by reconciliation (claims) | ADR-050 §2 | JUnit `SatelliteWritePolicyTest`; native crash cuts below |
| 1,000 unfinished missions due at once (a long offline period) | Drained 32 per pass, in order, with no lost or double completion | ADR-050 §5 | Native (`backlog`); JUnit `RegistryLifecycleTest` |
| A failed coalesced flush | Stays pending, reported once, retried | ADR-050 §2 | GameTest flush-failure batch ([C7](../../work/v1.6.0-c7-close/VERIFICATION.md)) |

## Resource missions and delivery (ADR-051 §11)

| Cut | State after restart | Outcome | Evidence |
|---|---|---|---|
| Claim persisted in neither store | READY, no receipt | Claimed again; paid once | JUnit `DeliveryCrashCutTest.aClaimInNeitherStoreIsClaimedAgain` |
| Chunk saved, registry not (A) | READY + persisted receipt | `CLAIM_RECOVERED`: CLAIMED, instance DEPLETED, no items; then acknowledged and released | Native cut A; JUnit `aChunkAheadOfTheRegistryRecoversTheClaimWithoutItems` |
| Registry flushed, chunk not (B) | CLAIMED, not acknowledged, no receipt | Rematerialized once, at the paying terminal only; then acknowledged and released | Native cut B; JUnit `aRegistryAheadOfTheChunkRematerializesOnce` |
| Both saved (D) | CLAIMED + receipt | Acknowledged after the chunk tag is seen; receipt dropped after the next flush | Native cut D; JUnit `bothStoresSavedAcknowledgeThenReleaseTheReceipt`; GameTest `surveyAsteroidClaimWithdrawAndAcknowledge` |
| Registry reverted, then an operator cancel before the terminal loads (C) | CANCELLED + receipt | Paid once; instance QUARANTINED; `PAID_THEN_CANCELLED` reported once; the receipt is kept | Native cut C; JUnit `anOperatorCancelOfARevertedClaimHoldsTheInstance` |
| Registry reverted, operator rebind, old terminal returns first | CLAIMED, paid at the old terminal, bound back (`REBIND_CONFLICT`, barrier flush) | Paid once | JUnit `ResourceMissionsTest.everyRebindOrderingPaysAndHoldsAsTheVectorsSay` (64 orderings) |
| **Residual:** registry reverted, operator rebind, new terminal claims first | CLAIMED at the new terminal; the old one holds a receipt | Paid twice; `REBIND_DOUBLE_PAY` reported once (the operator decision stated in ADR-051 §9) | The same 64 orderings: exactly 21 pay twice |
| **Residual:** chunk write lost after `ChunkDataEvent.Save` and after the acknowledgement was flushed | Acknowledged, no receipt | Lost, never duplicated | JUnit `aLostChunkWriteAfterADurableAcknowledgementLosesButNeverDuplicates` |
| Terminal taken as an item and placed again | Its ID, buffer and receipts travel with the raw root; nothing is persisted until a chunk tag holds them | ADR-051 §5 | JUnit `TerminalDeliveryTest.theSectionRoundTripsAndEverythingReadStartsUnpersisted` |
| Terminal root schema 1 at first load | Loaded with a new, unpersisted ID and empty sections | ADR-051 §5 | GameTest `terminalRootsUpgradeToSchemaTwoAndQuarantineABadDeliverySection`; native (setup terminals, schema 2 on disk) |
| Terminal root outside the delivery bounds | Quarantined and preserved exactly | ADR-051 §5, ADR-029 | The same GameTest; JUnit `theDecoderRejectsEveryOutOfBoundSection` |
