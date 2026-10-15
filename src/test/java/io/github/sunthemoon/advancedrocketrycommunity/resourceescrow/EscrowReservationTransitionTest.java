package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Diagnostic for the combined-reservation boundary; a repair reason must remain persistable. */
class EscrowReservationTransitionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void admittedOperationsCanPersistTheirStickyRepairReasons() throws Exception {
        var admitted = EscrowTestFixtures.ledger();
        for (int i = 1; i <= EscrowLimits.OPERATIONS; i++) {
            var operation = EscrowTestFixtures.operation(i);
            var candidate = admitted.append(List.of(operation));
            EscrowSettlementCapacity.requireAdmission(candidate, operation, null);
            try { EscrowSettlementCapacity.preflight(candidate, operation.id()); }
            catch (IllegalArgumentException full) { break; }
            admitted = candidate;
        }
        assertFalse(admitted.records().isEmpty());
        var repaired = admitted.replace(admitted.records().stream().map(op -> op.withReason(EscrowReason.RELOCATED_ENDPOINT)).toList(),
                admitted.floors(), admitted.tombstones());
        int before = RocketNbtSize.uncompressedBytes(EscrowCodec.encode(admitted));
        int after = RocketNbtSize.uncompressedBytes(EscrowCodec.encode(repaired));
        assertTrue(after > before, "Persisting the longer repair reason grows this root");
        String reservation;
        try { EscrowSettlementCapacity.preflight(repaired); reservation = "ACCEPTED"; }
        catch (IllegalArgumentException full) { reservation = "REFUSED: " + full.getMessage(); }
        System.out.println("repair-reservation operations=" + admitted.records().size() + " before_bytes=" + before
                + " after_bytes=" + after + " combined=" + reservation);

        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger()));
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        try (var writer = new EscrowLedgerWriter(file, 0); var coordinator = new EscrowCoordinator(data, writer)) {
            var acknowledgements = new java.util.concurrent.atomic.AtomicInteger();
            admitted.records().forEach(op -> coordinator.prepareTest(op, accepted -> {
                assertTrue(accepted); acknowledgements.incrementAndGet();
            }));
            assertTrue(coordinator.commitPreparations());
            awaitGeneration(coordinator, 0, 1);
            assertEquals(admitted.records().size(), acknowledgements.get());
            assertEquals(admitted.records(), coordinator.ledger().records());
            admitted.records().forEach(op -> coordinator.transition(op.id(), EscrowReason.RELOCATED_ENDPOINT));
            coordinator.tick(10);
            coordinator.autosave();
            awaitGeneration(coordinator, 1, 11);
            assertEquals(repaired.records(), coordinator.ledger().records());
            assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
        }
    }

    private static void awaitGeneration(EscrowCoordinator coordinator, long previous, long tick) throws InterruptedException {
        for (int i = 0; i < 1_000 && coordinator.ledger().writerGeneration() == previous; i++) {
            coordinator.tick(tick); Thread.sleep(2);
        }
        assertTrue(coordinator.ledger().writerGeneration() > previous);
    }
}
