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

    @Test void admittedRootWithRetainedFloorsCanStillExhaustReservationAtFloorO1() throws Exception {
        // Build retained floors through ordinary admitted-fill retirement, not arbitrary padding.
        var base = EscrowTestFixtures.ledger();
        for (int i = 1; i <= 29; i++) {
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
        assertEquals(29, base.floors().size()); assertTrue(base.records().isEmpty());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base));
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); var io = new AwaitedLedgerIo();
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer)) {
            var accepted = new java.util.concurrent.atomic.AtomicInteger();
            for (int i = 30; i <= 92; i++) {
                coordinator.prepareTest(EscrowTestFixtures.operation(i), result -> { assertTrue(result); accepted.incrementAndGet(); });
            }
            assertTrue(coordinator.commitPreparations()); io.awaitWrites(); coordinator.tick(1);
            assertEquals(63, accepted.get()); var root = coordinator.ledger();
            assertEquals(63, root.records().size()); assertEquals(1, root.writerGeneration());
            assertEquals(63, coordinator.admissionAuthority().pendingCount());
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root));
            var floor = root.floors().get(0);
            var original = EscrowTestFixtures.operation(1, 10_001, 0).destination();
            var endpoint = new EscrowEndpoint(original.kind(), original.level(), original.identity(), original.logicalRocket(),
                    original.type(), original.x(), original.y(), original.z(), original.unit(), floor.getLong("resource_epoch"),
                    floor.getString("signature"), floor.getString("signature"));
            var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
            var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(30_000), floor,
                    EscrowTestFixtures.id(700), "operator", 10, observations);
            var intent = preview.confirm(preview.actor(), preview.digest(), 210, observations);
            var candidate = root.append(List.of(intent.floorOperation(coordinator.nextSequence(), 210)));
            assertDoesNotThrow(() -> EscrowFloors.preflight(candidate));
            assertEquals(candidate, EscrowCodec.decode(EscrowCodec.encode(candidate)));
            assertTrue(reservationBytes(candidate) > reservationBytes(root));
            var refused = assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(candidate));
            assertEquals("Settlement/flight root reservation exhausted", refused.getMessage());
            coordinator.tick(210);
            assertThrows(IllegalArgumentException.class, () -> coordinator.beginSettlement(intent, ignored -> fail()));
            assertEquals(root, coordinator.ledger()); assertEquals(93, coordinator.nextSequence());
            assertFalse(data.isDirty()); assertEquals(63, coordinator.admissionAuthority().pendingCount());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
            System.out.println("admitted-floor boundary operations=63 floors=29 before_reserved_bytes=" + reservationBytes(root)
                    + " floor_o1_reserved_bytes=" + reservationBytes(candidate) + " limit=" + EscrowLimits.ROOT_BYTES);
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
            assertThrows(IllegalArgumentException.class, () -> coordinator.beginSettlement(intent, ignored -> fail()));
            assertEquals(root, coordinator.ledger()); assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
        }
        System.out.println("combined reservation last-fitting floors=" + root.floors().size());
    }
}
