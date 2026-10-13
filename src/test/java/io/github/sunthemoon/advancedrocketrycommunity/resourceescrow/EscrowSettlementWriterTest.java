package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class EscrowSettlementWriterTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactAcknowledgementPublishesFrozenIntentButNeverReleaseOrOrdinaryRetirement() throws Exception {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var intent = confirmed(root); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        var called = new AtomicInteger();
        try (var writer = new EscrowLedgerWriter(file(), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(10);
            assertTrue(coordinator.beginSettlement(intent, committed -> { assertTrue(committed); called.incrementAndGet(); }));
            assertEquals(root, coordinator.ledger()); assertEquals(0, called.get());
            tickUntil(coordinator, () -> writer.idle(), 11);
            assertEquals(1, called.get()); assertEquals(intent.encode(), coordinator.ledger().records().get(0).settlement());
            assertTrue(coordinator.logicalLocked(op.guard().rocket())); assertTrue(coordinator.ledger().tombstones().isEmpty());
            assertFalse(coordinator.retire(Set.of(op.id()), Map.of(), ignored -> fail("Ordinary L2 must not settle")));
            var restored = CheckedSavedDataFile.readPayload(file(), ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow();
            assertEquals(coordinator.ledger(), EscrowCodec.decode(restored));
        }
    }

    @Test void unknownO1RetainsExactCandidateAndHasNoSuccessCallbackUntilResolution() throws Exception {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var intent = confirmed(root); var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        var fail = new AtomicBoolean(true); var called = new AtomicInteger();
        try (var writer = new EscrowLedgerWriter(file(), 0, (staged, target) -> {
            if (fail.get()) { throw new java.io.IOException("Injected unknown O1 replacement"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(10); assertTrue(coordinator.beginSettlement(intent, committed -> { assertTrue(committed); called.incrementAndGet(); }));
            try {
                tickUntil(coordinator, writer::unavailable, 11);
                assertEquals(root, coordinator.ledger()); assertEquals(0, called.get());
                assertTrue(coordinator.logicalLocked(op.guard().rocket()));
                assertEquals(intent.encode(), writer.capturedOperations().get(0).settlement());
            } finally { fail.set(false); tickUntil(coordinator, writer::idle, 211); }
            assertEquals(1, called.get()); assertEquals(1, coordinator.ledger().records().size());
        }
    }

    @Test void floorO1CaptureLocksReturningLogicalIdentityBeforeLedgerPublication() throws Exception {
        var floor = EscrowTestFixtures.floor(99); floor.putUUID("transfer_id", EscrowTestFixtures.id(800));
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(), List.of(floor), List.of());
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, 1, floor.getString("signature"), floor.getString("signature"));
        var observed = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(701), floor, EscrowTestFixtures.id(700), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 210, observed);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(file(), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(210); assertFalse(coordinator.logicalLocked(endpoint.logicalRocket()));
            assertTrue(coordinator.beginSettlement(intent, committed -> assertTrue(committed)));
            assertTrue(coordinator.logicalLocked(endpoint.logicalRocket())); assertTrue(coordinator.locked(endpoint.key()));
            assertTrue(data.ledger().records().isEmpty()); assertEquals(1, data.ledger().floors().size());
            tickUntil(coordinator, writer::idle, 211);
            assertEquals(1, data.ledger().records().size()); assertEquals(1, data.ledger().floors().size());
            assertTrue(data.ledger().tombstones().isEmpty());
        }
    }

    @Test void ordinarySchedulerNeverExecutesAnySettlingDecisionAndUnimplementedProofStaysFalse() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = confirmed(EscrowTestFixtures.ledger(op)).freeze(EscrowTestFixtures.ledger(op));
        var scheduler = new EscrowReconciliationScheduler(() -> root, (id, reason) -> fail("Settling transition"),
                (record, all, done) -> fail("Settling automatic driver")); scheduler.tick(0); scheduler.tick(2_000);
        var visits = new AtomicInteger();
        var unavailable = new EscrowReconciliationScheduler(() -> EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)),
                (id, reason) -> fail("Unimplemented reconciliation must not clear authority"), (record, all, done) -> {
                    visits.incrementAndGet(); done.accept(new EscrowReconciliationScheduler.Result(EscrowReason.RECONCILIATION_UNIMPLEMENTED, false));
                });
        for (int tick = 0; tick <= 20; tick++) { unavailable.tick(tick); }
        assertEquals(2, visits.get());
        assertThrows(IllegalArgumentException.class, () -> new EscrowReconciliationScheduler.Result(EscrowReason.RECONCILIATION_UNIMPLEMENTED, true));
    }

    private Path file() { return temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); }
    private static EscrowSettlementIntent confirmed(EscrowLedger root) {
        var observed = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, root.records().get(0).id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 10, observed);
        return preview.confirm(preview.actor(), preview.digest(), 11, observed);
    }
    private static void tickUntil(EscrowCoordinator coordinator, java.util.function.BooleanSupplier done, long start) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L; long tick = start;
        while (!done.getAsBoolean() && System.nanoTime() < deadline) { coordinator.tick(tick++); Thread.sleep(5); }
        assertTrue(done.getAsBoolean(), "Writer did not reach the checked boundary within the existing five-second test bound");
    }
}
