package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class W151NativeProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void completionWaitsForBothRequiredPeriodicSaves() {
        assertFalse(W151NativeProbe.readyToValidate(false, true, 0));
        assertFalse(W151NativeProbe.readyToValidate(false, true, 1));
        assertTrue(W151NativeProbe.readyToValidate(false, true, 2));
    }

    @Test void savesDoNotReplaceTheProducerWindowOrReferenceTransfer() {
        assertFalse(W151NativeProbe.readyToValidate(true, true, 2));
        assertFalse(W151NativeProbe.readyToValidate(false, false, 2));
    }

    @Test void workloadWaitsForAnAcknowledgedAndReloadableBaseline() {
        var data = ResourceEscrowSavedData.create(); var io = new AwaitedLedgerIo();
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            writer.baseline(data.ledger());
            assertFalse(W151NativeProbe.baselineReady(data, coordinator));
            assertEquals(0, data.ledger().writerGeneration());
            io.tickUntil(coordinator::tick, 1, writer::idle);
            assertTrue(W151NativeProbe.baselineReady(data, coordinator));
            var recovered = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(file,
                    ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            assertEquals(data.ledger().id(), recovered.ledger().id());
            assertEquals(1, recovered.ledger().writerGeneration());
            assertTrue(recovered.ledger().records().isEmpty());
            assertTrue(writer.idle());
        }
    }
}
