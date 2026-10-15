package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowCombinedReservationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    static EscrowLedger retainedFloors(int count) {
        // Build retained floors through ordinary admitted-fill retirement, not arbitrary padding.
        var base = EscrowTestFixtures.ledger();
        for (int i = 1; i <= count; i++) {
            var original = EscrowTestFixtures.operation(i, 10_000 + i, 0);
            var op = new EscrowOperation(original.id(), original.sequence(), original.owner(), original.createdTick(),
                    original.phase(), original.reason(), original.payload(), original.payloadHash(), original.source(), original.destination(),
                    new EscrowGuard(original.guard().rocket(), EscrowTestFixtures.id(20_000 + i), 1, original.guard().journalDigest()));
            var admitted = base.append(List.of(op));
            EscrowSettlementCapacity.requireAdmission(admitted, op, null);
            EscrowSettlementCapacity.preflight(admitted, op.id());
            var frontier = new EscrowPrefix.State(base.id(), 1, op.destination().afterSignature(),
                    List.of(new EscrowPrefix.Link(op, op.destination(), "DESTINATION").marker()));
            base = EscrowFloors.retire(admitted, java.util.Set.of(op.id()), java.util.Map.of(op.destination().key(), frontier));
            EscrowSettlementCapacity.preflight(base);
        }
        assertEquals(count, base.floors().size()); assertTrue(base.records().isEmpty());
        return base;
    }

    @Test void admittedRootKeepsOneFloorRetirementAndRefusesConcurrentWorkWithoutMutation() throws Exception {
        assertRetirementHeadroom(false);
        // Settled fills cost less but still occupy operation slots. Reach the count bound
        // through real admissions/O1 writes to prove a second concurrent retirement can be full.
        assertRetirementHeadroom(true);
    }

    private void assertRetirementHeadroom(boolean settleFills) throws Exception {
        var base = retainedFloors(29);
        int capacity = settleFills ? 63 : 61;
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base));
        var file = directory.resolve("settled-" + settleFills).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        java.nio.file.Files.createDirectories(file.getParent());
        var io = new AwaitedLedgerIo();
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer)) {
            var accepted = new java.util.concurrent.atomic.AtomicInteger();
            long tick = 0;
            for (int i = 30; i < 30 + capacity; i++) {
                var op = EscrowTestFixtures.operation(i);
                coordinator.prepareTest(op, result -> { assertTrue(result); accepted.incrementAndGet(); });
                if (settleFills) {
                    assertTrue(coordinator.commitPreparations()); acknowledge(io, coordinator, ++tick);
                    coordinator.transition(op.id(), EscrowReason.OTHER_STATE);
                    coordinator.tick(tick += 10); coordinator.autosave(); acknowledge(io, coordinator, ++tick);
                    var root = coordinator.ledger();
                    var observations = EscrowSettlementIntentTest.observations(EscrowTestFixtures.ledger(op), false);
                    var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                            EscrowTestFixtures.id(700), "operator", tick, observations);
                    var results = new ArrayList<Boolean>();
                    coordinator.tick(tick += 10);
                    assertTrue(coordinator.beginSettlement(preview.confirm(preview.actor(), preview.digest(), tick, observations), results::add));
                    acknowledge(io, coordinator, ++tick); assertEquals(List.of(true), results);
                    coordinator.tick(tick += 10);
                }
            }
            assertThrows(IllegalArgumentException.class,
                    () -> coordinator.prepareTest(EscrowTestFixtures.operation(30 + capacity), ignored -> fail()));
            if (!settleFills) { assertTrue(coordinator.commitPreparations()); acknowledge(io, coordinator, ++tick); }
            assertEquals(capacity, accepted.get()); var root = coordinator.ledger();
            assertEquals(capacity, root.records().size());
            assertEquals(capacity, coordinator.admissionAuthority().pendingCount());
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root));
            tick += 210; coordinator.tick(tick);
            var intent = floorIntent(root, 0, tick); var second = floorIntent(root, 1, tick);
            var candidate = root.append(List.of(intent.floorOperation(coordinator.nextSequence(), tick)));
            assertDoesNotThrow(() -> EscrowFloors.preflight(candidate));
            assertEquals(candidate, EscrowCodec.decode(EscrowCodec.encode(candidate)));
            assertTrue(reservationBytes(candidate) > reservationBytes(root));
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(candidate));
            var results = new ArrayList<Boolean>();
            assertTrue(coordinator.beginSettlement(intent, results::add));
            assertTrue(results.isEmpty()); assertEquals(root, coordinator.ledger());
            long next = coordinator.nextSequence(); var captured = writer.capturedOperations();
            assertFalse(coordinator.beginSettlement(second, ignored -> fail()), "A concurrent checked write waits without mutation");
            assertEquals(next, coordinator.nextSequence()); assertEquals(captured, writer.capturedOperations());
            acknowledge(io, coordinator, ++tick); assertEquals(List.of(true), results);
            var acknowledged = coordinator.ledger();
            assertEquals(candidate.records(), acknowledged.records());
            assertEquals(root.writerGeneration() + 1, acknowledged.writerGeneration());
            assertEquals(acknowledged, EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
            assertFalse(data.isDirty()); assertEquals(capacity + 1, coordinator.admissionAuthority().pendingCount());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
            if (settleFills) {
                coordinator.tick(tick += 10);
                var refused = assertThrows(IllegalArgumentException.class, () -> coordinator.beginSettlement(second, ignored -> fail()));
                assertEquals(EscrowSettlementCapacity.FLOOR_FULL, refused.getMessage());
                assertEquals(acknowledged, coordinator.ledger()); assertEquals(next, coordinator.nextSequence());
                assertFalse(data.isDirty()); assertEquals(capacity + 1, coordinator.admissionAuthority().pendingCount());
                assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
            }
            System.out.println("admitted-floor boundary operations=" + capacity + " settled=" + settleFills + " floors=29 before_reserved_bytes=" + reservationBytes(root)
                    + " floor_o1_reserved_bytes=" + reservationBytes(candidate) + " limit=" + EscrowLimits.ROOT_BYTES);
        }
    }

    private static void acknowledge(AwaitedLedgerIo io, EscrowCoordinator coordinator, long tick) {
        io.awaitWrites(); coordinator.tick(tick);
    }
    private static EscrowSettlementIntent floorIntent(EscrowLedger root, int index, long tick) {
        var floor = root.floors().get(index);
        var original = EscrowTestFixtures.operation(index + 1, 10_001 + index, 0).destination();
        var endpoint = new EscrowEndpoint(original.kind(), original.level(), original.identity(), original.logicalRocket(),
                original.type(), original.x(), original.y(), original.z(), original.unit(), floor.getLong("resource_epoch"),
                floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(30_000 + index), floor,
                EscrowTestFixtures.id(700), "operator", tick - 200, observations);
        return preview.confirm(preview.actor(), preview.digest(), tick, observations);
    }

    @Test void fillAdmissionKeepsOneReceiptSlotEvenWithoutRetainedFloors() {
        var receipts = java.util.stream.IntStream.range(0, 62).mapToObj(EscrowTestFixtures::receipt).toList();
        var root = EscrowTestFixtures.ledger().replace(List.of(), List.of(), receipts);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(directory.resolve("receipt-capacity"), 0);
                var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.prepareTest(EscrowTestFixtures.operation(1), ignored -> { });
            assertEquals(1, coordinator.admissionAuthority().pendingCount());
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> coordinator.prepareTest(EscrowTestFixtures.operation(2), ignored -> fail()));
            assertEquals("Settlement/flight root reservation exhausted", refused.getMessage());
            assertEquals(root, coordinator.ledger()); assertEquals(2, coordinator.nextSequence());
            assertEquals(1, coordinator.admissionAuthority().pendingCount());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
        }
    }

    /** Diagnostic arithmetic for this receipt-free fixture, using actual encoded operation/root sizes. */
    private static long reservationBytes(EscrowLedger root) {
        long bytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.encode(root))
                + EscrowFloors.reservedGrowth(root) + 128 * 1_024L;
        for (var op : root.records()) {
            bytes += 2_048;
            if (op.settlement().isEmpty()) {
                bytes += EscrowLimits.OP_BYTES
                        - io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.operation(op));
            }
        }
        return bytes;
    }

    @Test void floorO1CannotConsumeOtherComponentsReceiptOrFlightReservations() {
        var repairs = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 63; i++) { repairs.add(EscrowTestFixtures.operation(i).withReason(EscrowReason.RELOCATED_ENDPOINT)); }
        var floors = new ArrayList<CompoundTag>(); EscrowLedger lastFitting = null;
        for (int count = 1; count <= 64; count++) {
            var floor = EscrowTestFixtures.floor(10_000 + count); floor.putUUID("transfer_id", EscrowTestFixtures.id(20_000 + count)); floors.add(floor);
            var root = new EscrowLedger(EscrowTestFixtures.id(500), 63, 0, repairs, floors, List.of());
            try { EscrowSettlementCapacity.preflight(root); lastFitting = root; }
            catch (IllegalArgumentException full) { break; }
        }
        assertNotNull(lastFitting); var root = lastFitting;
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root));
        var floor = root.floors().get(0);
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "advancedrocketrycommunity:rocket", 4, 0, 4, EscrowEndpoint.Unit.PRIMARY, 1, floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(30_000), floor, EscrowTestFixtures.id(700), "operator", 10, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 210, observations);
        var candidate = root.append(List.of(intent.floorOperation(64, 210)));
        assertDoesNotThrow(() -> EscrowFloors.preflight(candidate));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(candidate));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(directory.resolve("refused"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(210);
            var refused = assertThrows(IllegalArgumentException.class, () -> coordinator.beginSettlement(intent, ignored -> fail()));
            assertEquals("Escrow is full; retry floor retirement after other settlements or pruning.", refused.getMessage());
            assertEquals(root, coordinator.ledger()); assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
        }
        System.out.println("combined reservation last-fitting floors=" + root.floors().size());
    }
}
