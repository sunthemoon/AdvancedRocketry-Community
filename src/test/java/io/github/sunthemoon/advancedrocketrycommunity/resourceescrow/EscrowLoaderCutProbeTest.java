package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowLoaderCutProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temporary;

    @Test void matrixSelectionDoesNotSelectHistoricalLoaderOrOtherProbes() {
        assertTrue(EscrowLoaderCutProbe.selected("LF_M_N0"));
        assertTrue(EscrowLoaderCutProbe.selected("LF_M_K9_BI"));
        for (String value : List.of("LF_C2_SOURCE_AFTER", "K2", "CR_C7_ACKNOWLEDGED", "", "LF_")) {
            assertFalse(EscrowLoaderCutProbe.selected(value));
        }
        assertFalse(EscrowLoaderCutProbe.selected(null));
    }

    @Test void baselineObservationsBracketTheSameNativeReplacementWithoutChangingAcknowledgement() throws Exception {
        Path file = temporary.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var root = EscrowLedger.empty(UUID.randomUUID()).generation(1);
        var events = new CopyOnWriteArrayList<String>();
        var replies = new CopyOnWriteArrayList<EscrowLedgerWriter.Completion>();
        try (var writer = new EscrowLedgerWriter(file, 0, (staged, target) -> {
            assertEquals(List.of("BASELINE_STAGED"), events);
            assertTrue(replies.isEmpty());
            CheckedSavedDataFile.atomicMove(staged, target);
            events.add("MOVE");
        }, name -> {
            assertTrue(replies.isEmpty());
            if (name.equals("BASELINE_REPLACED")) {
                assertEquals(root, EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
            }
            events.add(name);
        })) {
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(root, Set.of()), replies::add));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            for (long tick = 1; !writer.idle() && System.nanoTime() < deadline; tick++) {
                writer.tick(tick); Thread.sleep(1);
            }
            assertTrue(writer.idle());
            assertEquals(List.of("BASELINE_STAGED", "MOVE", "BASELINE_REPLACED"), events);
            assertEquals(1, replies.size());
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, replies.get(0).outcome());
            assertEquals(root, replies.get(0).snapshot().ledger());
            assertFalse(writer.unknown());
        }
    }
}
