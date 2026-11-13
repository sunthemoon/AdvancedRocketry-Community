package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BayUncoveredSeedTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void typedSeedsRoundTripCheckedFilesWithoutPretendingToAdmitLiveWork() throws Exception {
        for (int count : new int[] {1, 62, 63}) {
            var ledger = BayUncoveredReleaseTestCommands.seedLedger(count);
            assertEquals(count, ledger.records().size());
            assertEquals(1, ledger.writerGeneration());
            assertEquals(count, ledger.records().stream().map(EscrowOperation::id).distinct().count());
            ledger.records().forEach(op -> assertEquals(EscrowOperation.Phase.PREPARED, op.phase()));
            var folder = java.nio.file.Files.createDirectory(directory.resolve(Integer.toString(count)));
            var file = folder.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            CheckedSavedDataFile.replace(file, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(ledger));
            assertEquals(ledger, EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
        }
    }

    @Test void seedCountCannotExceedItsDeclaredBound() {
        assertThrows(IllegalArgumentException.class, () -> BayUncoveredReleaseTestCommands.seedLedger(0));
        assertThrows(IllegalArgumentException.class, () -> BayUncoveredReleaseTestCommands.seedLedger(64));
    }
}
