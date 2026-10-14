package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowSettlementCompletionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    @Test void checkedTombstonePrecedesReleaseAndOnlyTheFrozenPhysicalSurvivorUnlocks() throws Exception {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE));
        var intent = intent(root, false); var frozen = intent.freeze(root); var op = frozen.records().get(0);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(frozen)); var proofs = proofs(intent);
        try (var writer = new EscrowLedgerWriter(file(), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(10); var calls = new java.util.ArrayList<Boolean>();
            assertTrue(coordinator.settlementWrite(intent, proofs, false, () -> true, calls::add));
            assertTrue(coordinator.ledger().tombstones().isEmpty());
            until(coordinator, writer::idle, 11); assertEquals(List.of(true), calls);
            assertEquals(1, coordinator.ledger().records().size()); assertEquals(1, coordinator.ledger().tombstones().size());
            assertTrue(EscrowSettlementTombstones.identityLocked(coordinator.ledger(), op.guard().rocket(), op.destination().identity()));
            coordinator.tick(10_000);
            assertTrue(coordinator.settlementWrite(intent, proofs, true, () -> true, calls::add));
            assertEquals(1, coordinator.ledger().records().size()); until(coordinator, writer::idle, 10_001);
            assertEquals(List.of(true, true), calls); assertTrue(coordinator.ledger().records().isEmpty());
            assertFalse(EscrowSettlementTombstones.identityLocked(coordinator.ledger(), op.guard().rocket(), op.destination().identity()));
            assertTrue(EscrowSettlementTombstones.identityLocked(coordinator.ledger(), op.guard().rocket(), EscrowTestFixtures.id(900)));
            assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
        }
    }
    @Test void ramAbsenceOrWrongFrontierCannotAuthorizeAnyWrite() {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1).withReason(EscrowReason.MISSING_ENDPOINT));
        var intent = intent(root, true); var frozen = intent.freeze(root); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(frozen));
        try (var writer = new EscrowLedgerWriter(file(), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(10);
            assertThrows(IllegalArgumentException.class, () -> coordinator.settlementWrite(intent, Map.of(), false, () -> true, ignored -> fail()));
            var partial = proofs(intent); partial.remove(intent.observations().get(0).endpoint().key());
            assertThrows(IllegalArgumentException.class, () -> coordinator.settlementWrite(intent, partial, false, () -> true, ignored -> fail()));
            var wrong = proofs(intent); var key = intent.observations().get(0).endpoint().key(); var old = wrong.get(key);
            wrong.put(key, new EscrowBarrier.Proof(new EscrowPrefix.State(intent.ledger(), 9, EscrowTestFixtures.hash(1), List.of()), old.capture(), old.active(), old.lifetime()));
            assertThrows(IllegalArgumentException.class, () -> coordinator.settlementWrite(intent, wrong, false, () -> true, ignored -> fail()));
            assertEquals(frozen, coordinator.ledger());
        }
    }
    @Test void releasedNoTransferReceiptCannotAuthorizeAnEmptyHistoricalElection() {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1, 99, 1).withReason(EscrowReason.OTHER_STATE));
        var intent = intent(root, false); var frozen = intent.freeze(root);
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(frozen, intent), intent);
        var op = root.records().get(0); var logical = op.guard().rocket(); var returned = EscrowTestFixtures.id(994);
        assertNull(op.guard().transfer()); assertFalse(EscrowSettlementTombstones.logicalLocked(released, logical));
        assertTrue(EscrowRecoveryFence.known(released, logical, returned));
        assertEquals("TOMBSTONE", EscrowRecoveryFence.reason(released, logical, returned,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction.REBUILD_DESTINATION, List.of(), List.of()));
        assertTrue(EscrowRecoveryFence.transaction(released, logical, false, List.of()));
        var chosen = new EscrowRecoveryFence.Rocket(op.destination().identity(), 1, false, true);
        assertNull(EscrowRecoveryFence.reason(released, logical, returned,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction.KEEP_DESTINATION, List.of(), List.of(chosen)));
        assertFalse(EscrowRecoveryFence.transaction(released, logical, false, List.of(chosen)));
        assertFalse(EscrowRecoveryFence.known(released, EscrowTestFixtures.id(995), returned));
    }
    @Test void chosenUuidStillRejectsResourceRollbackAndConflictingSameEpochState() {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1, 99, 1).withReason(EscrowReason.OTHER_STATE));
        var intent = intent(root, false); var frozen = intent.freeze(root);
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(frozen, intent), intent);
        var observed = intent.observations().stream().filter(value -> value.endpoint().kind() == EscrowEndpoint.Kind.ENTITY).findFirst().orElseThrow();
        var endpoint = observed.endpoint(); var current = new RocketEscrowSection(root.id(), observed.state().epoch(), List.of());
        assertEquals(1, current.epoch()); assertEquals(1, EscrowSettlementTombstones.minimumEpoch(released, endpoint.logicalRocket(), endpoint.identity()));
        assertFalse(EscrowSettlementTombstones.stateLocked(released, endpoint.logicalRocket(), endpoint.identity(), current, observed.state().signature(), observed.quantity()));
        for (var stale : List.of(RocketEscrowSection.neutral(), new RocketEscrowSection(root.id(), 0, List.of()))) {
            assertTrue(EscrowSettlementTombstones.stateLocked(released, endpoint.logicalRocket(), endpoint.identity(), stale, observed.state().signature(), observed.quantity()));
        }
        assertTrue(EscrowSettlementTombstones.stateLocked(released, endpoint.logicalRocket(), endpoint.identity(), current, EscrowTestFixtures.hash(991), observed.quantity()));
        assertTrue(EscrowSettlementTombstones.stateLocked(released, endpoint.logicalRocket(), endpoint.identity(), current, observed.state().signature(), observed.quantity() + 1));
        assertFalse(EscrowSettlementTombstones.stateLocked(released, endpoint.logicalRocket(), endpoint.identity(), current.afterNativeResourceMutation(),
                EscrowTestFixtures.hash(991), observed.quantity() + 1));
        var stale = new EscrowRecoveryFence.Rocket(endpoint.identity(), 0, false, true);
        assertEquals("TOMBSTONE", EscrowRecoveryFence.reason(released, endpoint.logicalRocket(), EscrowTestFixtures.id(992),
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction.KEEP_DESTINATION, List.of(), List.of(stale)));
        assertTrue(EscrowRecoveryFence.transaction(released, endpoint.logicalRocket(), false, List.of(stale)));
    }
    @Test void invalidatedJointProofOrJournalLeaseBeforeReplacementRetainsTheCompleteIntent() throws Exception {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1, 99, 0).withReason(EscrowReason.OTHER_STATE), EscrowTestFixtures.operation(2, 99, 1));
        var intent = intent(root, false); var frozen = intent.freeze(root).generation(1);
        for (boolean invalidateProof : List.of(true, false)) {
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(frozen)); var proofs = proofs(intent); var lease = new AtomicBoolean(true);
            var calls = new java.util.ArrayList<Boolean>();
            try (var writer = new EscrowLedgerWriter(file(), 1, CheckedSavedDataFile::atomicMove, cut -> {
                if (invalidateProof) { proofs.values().iterator().next().capture().generation().incrementAndGet(); } else { lease.set(false); }
            }); var coordinator = new EscrowCoordinator(data, writer)) {
                coordinator.tick(10);
                assertTrue(coordinator.settlementWrite(intent, proofs, false, lease::get, calls::add)); until(coordinator, writer::idle, 11);
                assertEquals(List.of(false), calls); assertEquals(frozen, coordinator.ledger()); assertFalse(java.nio.file.Files.exists(file()));
            }
        }
    }
    @Test void missingReturningIdentityIsPermanentlyLockedAndTombstonesAreNotCollected() {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1).withReason(EscrowReason.MISSING_ENDPOINT));
        var intent = intent(root, true); var frozen = intent.freeze(root);
        var installed = EscrowSettlementTombstones.install(frozen, intent); var released = EscrowSettlementTombstones.release(installed, intent);
        assertEquals(1, released.tombstones().size()); assertTrue(released.records().isEmpty());
        assertEquals(released, EscrowCodec.decode(EscrowCodec.encode(released)));
        assertEquals(released.tombstones(), EscrowFloors.collect(released, List.of(), ignored -> true).tombstones());
        var entity = intent.observations().stream().filter(value -> value.endpoint().kind() == EscrowEndpoint.Kind.ENTITY).findFirst().orElseThrow();
        assertFalse(EscrowSettlementTombstones.identityLocked(released, entity.endpoint().logicalRocket(), entity.endpoint().identity()));
        assertTrue(EscrowSettlementTombstones.identityLocked(released, entity.endpoint().logicalRocket(), EscrowTestFixtures.id(901)));
    }
    @Test void receiptSchemaFlagsAccountingAndByteLimitAreCheckedWithoutTruncation() {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE)); var intent = intent(root, false);
        var tag = EscrowSettlementTombstones.create(root.records().get(0), intent);
        var changed = tag.copy(); changed.putInt("released", 1); assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(changed, true));
        var oversize = tag.copy(); oversize.putString("actor_name", "x".repeat(2_048));
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(oversize, true));
        var boundary = new CompoundTag(); boundary.putByteArray("padding", new byte[2_030]); // 18 bytes of array/root framing = 2,048.
        EscrowTags.bound(boundary, 2_048, 16, 128); boundary.putByteArray("padding", new byte[2_031]);
        assertThrows(IllegalArgumentException.class, () -> EscrowTags.bound(boundary, 2_048, 16, 128));
    }
    @Test void diskAbsenceCensusRejectsDuplicateRelocatedAndNestedIdentitiesAndHonours1024Limit() {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)); var endpoint = root.records().get(0).destination();
        var absent = new EscrowSettlementIntent.Observation(endpoint, null, false, 0); var disk = new CompoundTag();
        disk.putIntArray("Position", new int[] {endpoint.x(), endpoint.z()}); var entities = new ListTag(); disk.put("Entities", entities);
        for (int i = 0; i < 1_024; i++) { var object = new CompoundTag(); object.putString("id", "minecraft:pig"); entities.add(object); }
        assertTrue(EscrowSettlementDisk.validator(absent, root.id()).test(disk)); entities.add(new CompoundTag());
        assertFalse(EscrowSettlementDisk.validator(absent, root.id()).test(disk)); entities.clear();
        var copy = new CompoundTag(); copy.putString("id", endpoint.type()); copy.putUUID("UUID", EscrowTestFixtures.id(902));
        var wrapper = new CompoundTag(); wrapper.putUUID("assembly_transaction_id", endpoint.logicalRocket()); copy.put("RocketEntityData", wrapper); entities.add(copy);
        assertFalse(EscrowSettlementDisk.validator(absent, root.id()).test(disk));
        var parent = new CompoundTag(); parent.putString("id", "minecraft:pig"); var passengers = new ListTag(); passengers.add(copy); parent.put("Passengers", passengers);
        entities.clear(); entities.add(parent); assertFalse(EscrowSettlementDisk.validator(absent, root.id()).test(disk));
    }
    @Test void unknownTombstoneWriteKeepsExactCandidateAndAllLocksUntilCheckedResolution() throws Exception {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE)); var intent = intent(root, false);
        var frozen = intent.freeze(root); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(frozen));
        var fail = new AtomicBoolean(true); var calls = new java.util.ArrayList<Boolean>();
        try (var writer = new EscrowLedgerWriter(file(), 0, (staged, target) -> {
            if (fail.get()) { throw new java.io.IOException("Ambiguous settlement write"); } CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(10); assertTrue(coordinator.settlementWrite(intent, proofs(intent), false, () -> true, calls::add));
            until(coordinator, () -> data.isDirty(), 11);
            assertTrue(calls.isEmpty()); assertEquals(frozen, coordinator.ledger()); assertFalse(writer.idle());
            assertTrue(coordinator.logicalLocked(root.records().get(0).guard().rocket()));
            fail.set(false); until(coordinator, writer::idle, 1_000);
            assertEquals(List.of(true), calls); assertTrue(EscrowSettlementTombstones.installed(coordinator.ledger(), intent));
            assertEquals(1, coordinator.ledger().records().size());
        }
    }
    @Test void failedO4LeavesAcknowledgedTombstonesIntentAndFloorLocked() throws Exception {
        var floor = EscrowTestFixtures.floor(99); floor.putUUID("transfer_id", EscrowTestFixtures.id(800));
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(), List.of(floor), List.of());
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, 1, floor.getString("signature"), floor.getString("signature"));
        var observed = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(701), floor, EscrowTestFixtures.id(700), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 210, observed);
        var installed = EscrowSettlementTombstones.install(root.append(List.of(intent.floorOperation(2, 210))), intent).generation(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(installed)); var proofs = proofs(intent); var calls = new java.util.ArrayList<Boolean>();
        try (var writer = new EscrowLedgerWriter(file(), 1, CheckedSavedDataFile::atomicMove,
                cut -> proofs.values().forEach(proof -> proof.capture().generation().incrementAndGet())); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(10); assertTrue(coordinator.settlementWrite(intent, proofs, true, () -> true, calls::add)); until(coordinator, writer::idle, 11);
            assertEquals(List.of(false), calls); assertEquals(installed, coordinator.ledger());
            assertEquals(1, coordinator.ledger().floors().size()); assertFalse(coordinator.ledger().tombstones().get(0).getBoolean("released"));
            assertTrue(EscrowSettlementTombstones.identityLocked(coordinator.ledger(), endpoint.logicalRocket(), endpoint.identity()));
            var released = EscrowSettlementTombstones.release(installed, intent);
            assertTrue(released.floors().isEmpty()); assertTrue(released.records().isEmpty());
            assertTrue(EscrowSettlementTombstones.identityLocked(released, endpoint.logicalRocket(), endpoint.identity()));
            assertTrue(EscrowSettlementTombstones.retiredTransfer(released, endpoint.logicalRocket(), floor.getUUID("transfer_id")));
            assertEquals(released, EscrowCodec.decode(EscrowCodec.encode(released)));
        }
    }
    private Path file() { return directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); }
    private static EscrowSettlementIntent intent(EscrowLedger root, boolean missing) {
        var observed = EscrowSettlementIntentTest.observations(root, missing);
        var preview = EscrowSettlementIntent.preview(root, root.records().get(0).id(), missing ? EscrowSettlementIntent.Choice.ABANDON
                : EscrowSettlementIntent.Choice.ACCEPT_CURRENT, EscrowTestFixtures.id(700), "operator", 10, observed);
        return preview.confirm(preview.actor(), preview.digest(), missing ? 210 : 11, observed);
    }
    private Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs(EscrowSettlementIntent intent) {
        var result = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
        for (var observation : intent.observations()) {
            var frontier = observation.missing() ? new EscrowPrefix.State(intent.ledger(), 0, EscrowSettlementTombstones.observationDigest(observation), List.of()) : observation.state();
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory, new ChunkPos(0, 0),
                    new AtomicLong(1), 1, 0, () -> { }, () -> { });
            result.put(observation.endpoint().key(), new EscrowBarrier.Proof(frontier, capture, new AtomicBoolean(true), new AtomicBoolean(true)));
        }
        return result;
    }
    private static void until(EscrowCoordinator coordinator, java.util.function.BooleanSupplier done, long tick) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!done.getAsBoolean() && System.nanoTime() < deadline) { coordinator.tick(tick++); Thread.sleep(5); }
        assertTrue(done.getAsBoolean(), "Settlement writer did not resolve within five seconds");
    }
}
