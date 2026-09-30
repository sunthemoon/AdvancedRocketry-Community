package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-049 section 10: 10 ticks between state-changing intents, 2 between selections, per player. */
final class IntentRateLimiterTest {
    @Test
    void intentsAndSelectionsAreSpacedSeparatelyPerPlayer() {
        IntentRateLimiter limiter = new IntentRateLimiter();
        UUID player = UUID.randomUUID();
        RegistryLimits limits = RegistryLimits.DEFAULTS;
        assertTrue(limiter.allow(player, false, 100, limits));
        assertFalse(limiter.allow(player, false, 109, limits));
        assertTrue(limiter.allow(player, true, 109, limits), "selections have their own spacing");
        assertFalse(limiter.allow(player, true, 110, limits));
        assertTrue(limiter.allow(player, true, 111, limits));
        assertTrue(limiter.allow(player, false, 110, limits));
        assertTrue(limiter.allow(UUID.randomUUID(), false, 110, limits), "another player is not limited");
        limiter.forget(player);
        assertTrue(limiter.allow(player, false, 111, limits), "a logged-out player starts fresh");
        RegistryLimits slower = new RegistryLimits(1_024, 64, 128, 3_072, 4_096, 256, 2_048, 16, 40, 20);
        assertTrue(limiter.allow(player, true, 200, slower));
        assertFalse(limiter.allow(player, true, 219, slower));
    }
}
