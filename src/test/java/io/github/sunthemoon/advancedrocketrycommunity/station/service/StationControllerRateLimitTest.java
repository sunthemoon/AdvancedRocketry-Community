package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-065 section 11.2: one controller mutation per player per 10 ticks, with a map bounded by the window. */
final class StationControllerRateLimitTest {
    @Test
    void onePlayerActsOncePerTenTicksAndOthersAreIndependent() {
        StationControllerRateLimit limit = new StationControllerRateLimit();
        UUID first = new UUID(1, 1);
        UUID second = new UUID(1, 2);
        assertTrue(limit.admit(first, 100));
        assertFalse(limit.admit(first, 100));
        assertFalse(limit.admit(first, 109));
        assertTrue(limit.admit(second, 109));
        assertTrue(limit.admit(first, 110));
        assertFalse(limit.admit(second, 118));
        assertTrue(limit.admit(second, 119));
    }

    @Test
    void expiredEntriesAreDroppedAndForgetAndClearReset() {
        StationControllerRateLimit limit = new StationControllerRateLimit();
        for (int player = 0; player < 50; player++) {
            assertTrue(limit.admit(new UUID(2, player), 1_000));
        }
        assertEquals(50, limit.size());
        assertTrue(limit.admit(new UUID(3, 0), 1_010));
        assertEquals(1, limit.size(), "entries older than the window are dropped");
        limit.forget(new UUID(3, 0));
        assertTrue(limit.admit(new UUID(3, 0), 1_011));
        // A clock that went back (a new server) never blocks.
        assertTrue(limit.admit(new UUID(3, 1), 5));
        limit.clear();
        assertEquals(0, limit.size());
    }
}
