package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketCatalogSync;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketCatalogRefreshLimiterTest {
    @Test
    void unchangedGenerationRetriesAfterDenialAndReopeningDoesNotBypassBudget() {
        var limiter = new RocketCatalogRefreshLimiter();
        UUID player = new UUID(0, 1);
        var sync = new RocketCatalogSync();
        assertTrue(sync.attempt(0, 1));
        assertTrue(limiter.acquire(player, 0));
        sync.delivered(1);
        assertFalse(sync.attempt(1, 1));
        sync.request();
        for (int tick = 1; tick <= 100; tick++) {
            assertEquals(tick % 20 == 0, sync.attempt(tick, 1));
            if (tick % 20 == 0) { assertEquals(tick == 100, limiter.acquire(player, tick)); }
        }
        sync.delivered(1);
        assertFalse(sync.attempt(120, 1));
        assertTrue(sync.attempt(120, 2));
        assertFalse(limiter.acquire(player, 120));
        var reopened = new RocketCatalogSync();
        assertTrue(reopened.attempt(121, 2));
        assertFalse(limiter.acquire(player, 121));
        assertTrue(reopened.attempt(201, 2));
        assertTrue(limiter.acquire(player, 201));
        reopened.delivered(2);
        assertFalse(reopened.attempt(222, 2));
    }

    @Test
    void trackedPlayersAreBoundedExpiredEntriesPrunedAndLifecycleClears() {
        var limiter = new RocketCatalogRefreshLimiter();
        for (int i = 0; i < RocketFlightLimits.MAX_TRACKED_INTENT_PLAYERS; i++) {
            assertTrue(limiter.acquire(new UUID(0, i), 10));
        }
        var extra = new UUID(1, 0);
        assertFalse(limiter.acquire(extra, 109));
        assertTrue(limiter.acquire(extra, 110));
        limiter.remove(extra);
        assertTrue(limiter.acquire(extra, 111));
        limiter.clear();
        assertTrue(limiter.acquire(extra, 112));
        assertTrue(limiter.acquire(extra, 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.acquire(extra, -1));
        assertFalse(new RocketCatalogSync().attempt(100, 0));
    }
}
