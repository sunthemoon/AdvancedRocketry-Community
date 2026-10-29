package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketCanonicalResidentsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void freshAndValidatedMutationsKeepTheWitnessWithoutAcknowledging() throws Exception {
        var data = new RocketTransferSavedData();
        assertTrue(proven(data));
        var record = RocketTransferSavedDataTest.record(); data.put(record);
        assertTrue(proven(data));
        var landed = RocketTransferSavedDataTest.committed(record); data.put(landed);
        assertTrue(proven(data));
        assertThrows(IllegalArgumentException.class, () -> data.put(record));
        assertTrue(proven(data));
        var replacement = RocketTransferSavedDataTest.record(UUID.randomUUID(), record.logicalRocketId(),
                UUID.randomUUID(), record.destinationSnapshot().sourceOrigin());
        data.replace(record.transferId(), replacement);
        assertTrue(proven(data));
        data.remove(replacement.transferId());
        assertTrue(proven(data));
        assertFalse(data.acknowledgedCurrent());
    }

    @Test void loadingDoesNotInheritAnotherInstancesWitnessButValidationCanProveIt() throws Exception {
        var data = loaded();
        assertFalse(proven(data));
        var candidate = RocketLoadedInsertionTest.candidate(); data.put(candidate);
        assertFalse(proven(data), "Checking a new record says nothing about loaded residents");
        data.remove(candidate.transferId());
        assertFalse(proven(data));
        assertThrows(IllegalArgumentException.class, () -> data.validateNewRecord(data.entries().get(0)));
        assertFalse(proven(data));
        data.validateNewRecord(candidate);
        assertTrue(proven(data), "The successful exact fallback proves the resident set");
        data.validateWriteBack(candidate);
        assertTrue(proven(data));
        assertFalse(data.acknowledgedCurrent());
    }

    @Test void aSuccessfulOrdinaryEncodeProvesOnlyEncodabilityAndReloadLosesIt() throws Exception {
        var data = loaded();
        var encoded = data.save(new CompoundTag());
        assertTrue(proven(data));
        assertFalse(data.acknowledgedCurrent());
        assertFalse(proven(RocketTransferSavedData.load(encoded)));
    }

    @Test void onlyCurrentSuccessfulWriterCompletionEstablishesAnUnprovenWitness() throws Exception {
        var data = loaded();
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
        try {
            assertTrue(data.requestWrite(file, policy));
            var resident = data.entries().get(0);
            data.put(RocketTransferSavedDataTest.committed(resident));
            data.drainForTests();
            assertEquals(RocketTransferSavedData.WriteOutcome.COMMITTED, data.checkedWriteResult().outcome());
            assertFalse(proven(data), "A stale completion must not certify the current resident set");
            assertFalse(data.acknowledgedCurrent());
            data.saveChecked(file, policy);
            assertTrue(proven(data));
            assertTrue(data.acknowledgedCurrent());
            data.put(RocketTransferSavedDataTest.committed(resident));
            assertTrue(proven(data), "Validated mutations keep the witness even when acknowledgement is stale");
            assertFalse(data.acknowledgedCurrent());
            data.validateWriteBack(RocketLoadedInsertionTest.candidate());
            assertTrue(proven(data));
        } finally { data.abort(); }
    }

    @Test void failedWriterCompletionDoesNotEstablishAWitness() throws Exception {
        var data = loaded();
        try {
            var policy = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS)
                    .withCommitter((staged, target) -> { throw new java.io.IOException("intentional pre-move failure"); });
            data.saveChecked(directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()), policy);
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, data.checkedWriteResult().outcome());
            assertFalse(proven(data));
            assertFalse(data.acknowledgedCurrent());
            data.validateWriteBack(RocketLoadedInsertionTest.candidate());
            assertTrue(proven(data), "Exact validation needs no IO acknowledgement");
            assertFalse(data.acknowledgedCurrent());
        } finally { data.abort(); }
    }

    private static RocketTransferSavedData loaded() {
        var original = new RocketTransferSavedData(); original.put(RocketTransferSavedDataTest.record());
        return RocketTransferSavedData.load(original.save(new CompoundTag()));
    }

    private static boolean proven(RocketTransferSavedData data) throws Exception {
        // Observe the derived cache only; never inject records or change lifecycle state.
        var field = RocketTransferSavedData.class.getDeclaredField("canonicalResidents");
        field.setAccessible(true); return field.getBoolean(data);
    }
}
