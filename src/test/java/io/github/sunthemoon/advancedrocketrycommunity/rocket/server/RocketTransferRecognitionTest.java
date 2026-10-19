package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRecoveryFence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W11c leaf C-I (W11CD-DESIGN section 2.5), A0: the grouped recognition predicate over explicit fixtures. */
class RocketTransferRecognitionTest {
    private static final RocketPosition ORIGIN = new RocketPosition(64, 70, 64);
    private static final BooleanSupplier UNREAD = () -> fail("A neutral or structurally different rocket must not read the ledger");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactContentHashIsRecognizedWithoutReadingTheLedger() {
        for (var record : List.of(RocketTransferRecognitionFixtures.legacy(ORIGIN), RocketTransferRecognitionFixtures.governed(ORIGIN))) {
            var recorded = record.destinationSnapshot();
            var decoded = RocketSnapshotNbtCodec.decode(RocketSnapshotNbtCodec.encode(recorded)).snapshot().orElseThrow();
            for (long epoch : new long[] {0, 1, Long.MAX_VALUE}) {
                assertTrue(RocketTransferEntities.recognizes(recorded, recorded, UNREAD, epoch));
                assertTrue(RocketTransferEntities.recognizes(recorded, decoded, UNREAD, epoch));
            }
        }
    }

    @Test void changedCargoIsRecognizedOnlyForTheCurrentLedgerAndANonzeroEpoch() {
        for (var record : List.of(RocketTransferRecognitionFixtures.legacy(ORIGIN), RocketTransferRecognitionFixtures.governed(ORIGIN))) {
            var recorded = record.destinationSnapshot();
            var after = RocketTransferRecognitionFixtures.changed(recorded, RocketTransferRecognitionFixtures.Change.CARGO);
            assertEquals(recorded.snapshotId(), after.snapshotId());
            assertNotEquals(recorded.contentHash(), after.contentHash());
            assertEquals(recorded.structureDigest(), after.structureDigest());
            assertTrue(RocketTransferEntities.recognizes(recorded, after, () -> true, 1));
            assertTrue(RocketTransferEntities.recognizes(recorded, after, () -> true, Long.MAX_VALUE));
            // Neutral mismatch: epoch 0 keeps the exact-hash rule and never reads the ledger.
            assertFalse(RocketTransferEntities.recognizes(recorded, after, UNREAD, 0));
            // A foreign, absent or off-thread ledger binding is not current.
            assertFalse(RocketTransferEntities.recognizes(recorded, after, () -> false, 1));
        }
    }

    @Test void snapshotIdIsMandatoryInBothBranches() {
        var recorded = RocketTransferRecognitionFixtures.legacy(ORIGIN).destinationSnapshot();
        var sameContent = RocketTransferRecognitionFixtures.reissued(recorded);
        var sameStructure = RocketTransferRecognitionFixtures.reissued(
                RocketTransferRecognitionFixtures.changed(recorded, RocketTransferRecognitionFixtures.Change.CARGO));
        assertEquals(recorded.contentHash(), sameContent.contentHash());
        assertEquals(recorded.structureDigest(), sameStructure.structureDigest());
        for (long epoch : new long[] {0, 1, Long.MAX_VALUE}) {
            assertFalse(RocketTransferEntities.recognizes(recorded, sameContent, UNREAD, epoch));
            assertFalse(RocketTransferEntities.recognizes(recorded, sameStructure, UNREAD, epoch));
        }
    }

    @Test void changedGeometryStateAdapterOrPropulsionIsNeverRecognized() {
        for (var record : List.of(RocketTransferRecognitionFixtures.legacy(ORIGIN), RocketTransferRecognitionFixtures.governed(ORIGIN))) {
            var recorded = record.destinationSnapshot();
            for (var change : RocketTransferRecognitionFixtures.Change.values()) {
                if (change == RocketTransferRecognitionFixtures.Change.CARGO) { continue; }
                var changed = RocketTransferRecognitionFixtures.changed(recorded, change);
                assertEquals(recorded.snapshotId(), changed.snapshotId(), change.name());
                assertNotEquals(recorded.structureDigest(), changed.structureDigest(), change.name());
                assertFalse(RocketTransferEntities.recognizes(recorded, changed, UNREAD, 1), change.name());
            }
        }
    }

    /**
     * Recognition only adds rockets with a nonzero epoch, which the unchanged fence treats as protected: a changed
     * protected copy becomes visible to the duplicate veto, and a stale exact-hash copy cannot silently displace it.
     */
    @Test void protectedDuplicateVetoSeesTheChangedProtectedCopy() {
        var record = RocketTransferRecognitionFixtures.legacy(ORIGIN);
        var recorded = record.destinationSnapshot();
        var after = RocketTransferRecognitionFixtures.changed(recorded, RocketTransferRecognitionFixtures.Change.CARGO);
        UUID named = record.destinationEntityId().orElseThrow();
        var changed = new Candidate(named, after, true, 2);
        var staleNeutral = new Candidate(UUID.randomUUID(), recorded, false, 0);
        var staleProtected = new Candidate(UUID.randomUUID(), recorded, true, 1);
        var changedNeutral = new Candidate(UUID.randomUUID(), after, false, 0);
        var foreign = new Candidate(UUID.randomUUID(), after, false, 3);
        var keep = RocketTransferRecoveryAction.KEEP_DESTINATION;

        assertEquals(List.of(), elect(recorded, List.of(changedNeutral, foreign)),
                "Neutral or foreign changed copies are not this record's");
        assertNull(reason(null, record, keep, List.of(), elect(recorded, List.of(changed, staleNeutral, changedNeutral))),
                "The changed named authority is kept and the neutral stale copy is discardable");
        assertEquals("FLOOR_CONFLICT", reason(null, record, keep, List.of(), elect(recorded, List.of(staleNeutral, changed))),
                "An election preferring the stale exact-hash copy must not discard the changed protected copy");
        assertEquals("FLOOR_CONFLICT", reason(null, record, keep, List.of(), elect(recorded, List.of(changed, staleProtected))));
        for (var action : List.of(RocketTransferRecoveryAction.KEEP_SOURCE, RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE)) {
            assertEquals("FLOOR_CONFLICT", reason(null, record, action, List.of(), elect(recorded, List.of(changed))),
                    "A recognized protected destination is never a discarded candidate");
        }
        var credited = new EscrowRecoveryFence.Rocket(record.sourceEntityId(), 4, false, true);
        assertEquals("FLOOR_CONFLICT", reason(null, record, RocketTransferRecoveryAction.KEEP_SOURCE, List.of(credited), List.of()),
                "Source recovery overwrote a credited source");

        // A named floor still rejects a missing, wrong or behind keeper of the recognized copy.
        var kept = elect(recorded, List.of(changed));
        assertNull(reason(ledger(record, named, 2), record, keep, List.of(), kept));
        assertEquals("FLOOR_CONFLICT", reason(ledger(record, named, 3), record, keep, List.of(), kept));
        assertEquals("FLOOR_CONFLICT", reason(ledger(record, UUID.randomUUID(), 2), record, keep, List.of(), kept));
        assertEquals("FLOOR_CONFLICT", reason(ledger(record, named, 2), record, RocketTransferRecoveryAction.REBUILD_DESTINATION,
                List.of(), List.of()));
    }

    @Test void governedRecordsStayRoutedAndHeldWhenTheirDestinationIsRecognized() {
        var record = RocketTransferRecognitionFixtures.governed(ORIGIN);
        var recorded = record.destinationSnapshot();
        var after = RocketTransferRecognitionFixtures.changed(recorded, RocketTransferRecognitionFixtures.Change.CARGO);
        assertTrue(record.nativeTransfer().isPresent());
        assertTrue(RocketTransferEntities.recognizes(recorded, after, () -> true, 1), "The fixture must be recognized");
        var arm = record.nativeTransfer().orElseThrow().armed();
        var armed = new EscrowLedger(arm.ledger(), 0, 1, List.of(), List.of(), List.of(), 1, List.of(), List.of(arm));
        var recognized = List.of(new EscrowRecoveryFence.Rocket(arm.destination(), 1, false, true));
        for (var action : RocketTransferRecoveryAction.values()) {
            assertEquals("ARMED_AUTHORITY_REQUIRED", EscrowRecoveryFence.reason(armed, record.logicalRocketId(),
                    record.transferId(), action, List.of(), recognized), action.name());
        }
        // Missing ARMED authority: classification, permit and the final action guard refuse before any match list.
        var journal = new RocketTransferSavedData(); journal.put(record);
        var fences = new RocketTransferFences(server -> { fail("Legacy ledger election must not inspect record 3"); return null; });
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        live.add(record.transferId()); settled.add(record.transferId());
        fences.classify(null, journal, live, settled);
        assertEquals("GOVERNED_ACTION_REQUIRED", fences.reason(record.transferId()));
        assertTrue(live.isEmpty() && settled.isEmpty());
        assertNull(RocketTransferRecoveryService.step(List.of(record), live, settled, fences.ids(), 0,
                candidate -> { fail("A governed record entered legacy readiness"); return true; }).candidate());
        assertGovernedHeld(fences, journal, record, live, settled);
        // Terminal authority routes to cleanup only, never legacy recovery.
        fences.classifyGoverned(record, new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.CLEANUP, null), live, settled);
        assertEquals(Set.of(record.transferId()), live);
        assertNull(RocketTransferRecoveryService.step(List.of(record), live, settled, fences.ids(), 0,
                candidate -> { fail("Terminal cleanup entered legacy readiness"); return true; }).candidate());
        assertGovernedHeld(fences, journal, record, live, settled);
    }

    private static void assertGovernedHeld(RocketTransferFences fences, RocketTransferSavedData journal, RocketTransferRecord record,
                                           Set<UUID> live, Set<UUID> settled) {
        assertFalse(fences.permit(null, journal, record, live, settled));
        for (var action : RocketTransferRecoveryAction.values()) {
            assertFalse(fences.actionAllowed(null, journal, record, action, List.of(), List.of(), live, settled), action.name());
        }
    }

    /** A loaded rocket of the record's owner and logical rocket, as the entity adapter sees it. */
    private record Candidate(UUID identity, RocketStructureSnapshot snapshot, boolean currentLedger, long epoch) { }

    /** The recovery match list in election order, through the production predicate. */
    private static List<EscrowRecoveryFence.Rocket> elect(RocketStructureSnapshot recorded, List<Candidate> ordered) {
        return ordered.stream()
                .filter(candidate -> RocketTransferEntities.recognizes(recorded, candidate.snapshot(), candidate::currentLedger,
                        candidate.epoch()))
                .map(candidate -> new EscrowRecoveryFence.Rocket(candidate.identity(), candidate.epoch(), false, true)).toList();
    }

    private static String reason(EscrowLedger ledger, RocketTransferRecord record, RocketTransferRecoveryAction action,
                                 List<EscrowRecoveryFence.Rocket> sources, List<EscrowRecoveryFence.Rocket> destinations) {
        return EscrowRecoveryFence.reason(ledger, record.logicalRocketId(), record.transferId(), action, sources, destinations);
    }

    private static EscrowLedger ledger(RocketTransferRecord record, UUID entity, long epoch) {
        var floor = new CompoundTag(); floor.putInt("schema_version", 2); floor.putUUID("rocket_id", record.logicalRocketId());
        floor.putUUID("transfer_id", record.transferId()); floor.putLong("journal_generation", 1);
        floor.putString("journal_digest", record.checksum()); floor.putUUID("entity_id", entity);
        floor.putLong("resource_epoch", epoch); floor.putString("signature_kind", "FUEL_VECTOR_V1");
        floor.putString("invariant_digest", record.checksum()); floor.putString("signature", record.checksum());
        floor.putLong("highest_seq", 1);
        return new EscrowLedger(UUID.randomUUID(), 1, 1, List.of(), List.of(floor), List.of());
    }
}
