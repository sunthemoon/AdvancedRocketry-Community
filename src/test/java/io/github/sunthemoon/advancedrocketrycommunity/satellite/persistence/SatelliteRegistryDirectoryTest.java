package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SatelliteRegistryDirectoryTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void checkpointForcesAfterReplacementIncludingSameCallReadbackCommit() {
        for (boolean throwAfterReplace : List.of(false, true)) {
            var data = SatelliteMissionSavedData.create(0);
            var watch = data.watch(SatelliteRegistryProofTest.launch(data));
            var calls = new AtomicInteger();
            var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target);
                if (throwAfterReplace) { throw new IOException("after replace"); }
            });
            var result = data.checkpoint(temporary.resolve(TYPE.fileName()), List.of(watch), policy, directory -> {
                calls.incrementAndGet();
                assertEquals(temporary.toAbsolutePath(), directory);
                assertTrue(CheckedSavedDataFile.readPayload(directory.resolve(TYPE.fileName()), TYPE).isPresent());
            });
            assertTrue(result.proves(watch)); assertFalse(result.directoryForceFailed());
            assertEquals(1, calls.get()); assertFalse(data.isDirty());
        }
    }

    @Test void failedDirectoryForceIsReportedWithoutErasingTheS2ProofOrAdvancingTwice() {
        var data = SatelliteMissionSavedData.create(0);
        var watch = data.watch(SatelliteRegistryProofTest.launch(data));
        long epoch = data.saveEpoch();
        var result = data.checkpoint(temporary.resolve(TYPE.fileName()), List.of(watch), CheckedSavedDataPolicy.atomic(TYPE),
                directory -> { throw new IOException("directory force unavailable"); });
        assertTrue(result.proves(watch)); assertTrue(result.directoryForceFailed());
        assertEquals(epoch + 1, data.saveEpoch()); assertFalse(data.isDirty());
        data.flush(temporary.resolve(TYPE.fileName()));
        assertEquals(epoch + 1, data.saveEpoch());
    }

    @Test void definitelyFailedReplacementNeverForcesDirectory() {
        var data = SatelliteMissionSavedData.create(0);
        var watch = data.watch(SatelliteRegistryProofTest.launch(data));
        var failed = new CheckedSavedDataPolicy(TYPE, (a, b) -> { throw new IOException("before replace"); });
        var result = data.checkpoint(temporary.resolve(TYPE.fileName()), List.of(watch), failed,
                directory -> fail("Failed replacement forced a directory"));
        assertEquals(SatelliteRegistryProof.Outcome.FAILED, result.outcome());
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
    }
}
