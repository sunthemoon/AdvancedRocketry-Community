package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class RocketTransactionRecoveryReadinessTest {
    @Test
    void loadedBlocksDoNotMeanPersistedEntityAbsence() {
        AtomicBoolean entitiesLoaded = new AtomicBoolean(false);
        RocketPosition origin = new RocketPosition(384, 101, 384);
        assertFalse(RocketTransactionRecoveryService.readyForRecovery(true, origin, key -> entitiesLoaded.get()));
        entitiesLoaded.set(true);
        assertTrue(RocketTransactionRecoveryService.readyForRecovery(true, origin, key -> entitiesLoaded.get()));
        entitiesLoaded.set(false);
        assertFalse(RocketTransactionRecoveryService.readyForRecovery(true, origin, key -> entitiesLoaded.get()));
    }

    @Test
    void unloadedBlockRegionNeverQueriesOrLoadsEntityChunks() {
        assertFalse(RocketTransactionRecoveryService.readyForRecovery(
                false, new RocketPosition(384, 101, 384), key -> {
                    throw new AssertionError("An unloaded block region must remain deferred");
                }));
    }

    @ParameterizedTest
    @CsvSource({
            "384,384,24,24", "383,383,23,23", "0,0,0,0", "15,16,0,1",
            "-1,-1,-1,-1", "-16,-17,-1,-2", "-17,16,-2,1",
            "2147483647,-2147483648,134217727,-134217728"
    })
    void queriesOnlyTheExactAnchoredEntityChunk(int x, int z, int chunkX, int chunkZ) {
        List<Long> queries = new ArrayList<>();
        assertTrue(RocketTransactionRecoveryService.readyForRecovery(
                true, new RocketPosition(x, 101, z), key -> {
                    queries.add(key);
                    return true;
                }));
        long expected = (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
        assertEquals(List.of(expected), queries);
    }
}
