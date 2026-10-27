package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** W27d1 A0: the runtime rejection map keeps at most 4,096 entries and evicts the oldest. */
final class BayRejectionsTest {
    private static UUID id(int n) { return new UUID(4_096, n); }

    @Test void theOldestRejectionIsEvictedAtTheBoundAndANewerOneOfTheSameSatelliteIsNewest() {
        var rejections = new BayRejections();
        UUID rocket = new UUID(1, 1);
        for (int n = 0; n < BayRejections.LIMIT; n++) { rejections.put(id(n), "BAY_CONFLICT", rocket, n); }
        assertEquals(4_096, rejections.size());
        rejections.put(id(0), "RECEIPT_PENDING", rocket, 5_000);
        rejections.put(id(9_999), "BAY_OWNER_MISMATCH", rocket, 5_001);
        assertEquals(4_096, rejections.size());
        assertEquals("RECEIPT_PENDING", rejections.get(id(0)).orElseThrow().reason(), "re-put moved it to the newest end");
        assertTrue(rejections.get(id(1)).isEmpty(), "the oldest remaining entry was evicted");
        assertEquals(5_001, rejections.get(id(9_999)).orElseThrow().tick());
        rejections.remove(id(0));
        assertTrue(rejections.get(id(0)).isEmpty());
        rejections.clear();
        assertEquals(0, rejections.size());
    }
}
