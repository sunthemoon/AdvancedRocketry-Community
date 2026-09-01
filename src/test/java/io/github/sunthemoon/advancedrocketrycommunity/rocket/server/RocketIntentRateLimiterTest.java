package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketIntentRateLimiterTest {
    @Test
    void boundsEachPlayerAndAuditsOnlyTheFirstRejectedIntent() {
        RocketIntentRateLimiter limiter = new RocketIntentRateLimiter();
        UUID player = UUID.fromString("10000000-0000-0000-0000-000000000691");

        for (int index = 0; index < RocketFlightLimits.MAX_INTENTS_PER_WINDOW; index++) {
            assertEquals(RocketIntentRateLimiter.Decision.ALLOWED, limiter.check(player, 100L));
        }
        assertEquals(RocketIntentRateLimiter.Decision.REJECTED_AUDIT, limiter.check(player, 100L));
        assertEquals(RocketIntentRateLimiter.Decision.REJECTED_SILENT, limiter.check(player, 100L));
        assertEquals(
                RocketIntentRateLimiter.Decision.ALLOWED,
                limiter.check(player, 100L + RocketFlightLimits.INTENT_WINDOW_TICKS)
        );
    }

    @Test
    void boundsTrackedPlayersAndExpiresOldWindows() {
        RocketIntentRateLimiter limiter = new RocketIntentRateLimiter();
        for (int index = 0; index < RocketFlightLimits.MAX_TRACKED_INTENT_PLAYERS; index++) {
            assertEquals(
                    RocketIntentRateLimiter.Decision.ALLOWED,
                    limiter.check(new UUID(0L, index + 1L), 20L)
            );
        }
        assertEquals(RocketFlightLimits.MAX_TRACKED_INTENT_PLAYERS, limiter.trackedPlayers());
        assertEquals(
                RocketIntentRateLimiter.Decision.REJECTED_SILENT,
                limiter.check(new UUID(1L, 1L), 20L)
        );
        assertEquals(
                RocketIntentRateLimiter.Decision.ALLOWED,
                limiter.check(
                        new UUID(1L, 1L),
                        20L + RocketFlightLimits.INTENT_WINDOW_TICKS
                )
        );
        assertEquals(1, limiter.trackedPlayers());
    }

    @Test
    void clockRollbackStartsAFreshWindow() {
        RocketIntentRateLimiter limiter = new RocketIntentRateLimiter();
        UUID player = UUID.fromString("10000000-0000-0000-0000-000000000692");
        assertEquals(RocketIntentRateLimiter.Decision.ALLOWED, limiter.check(player, 100L));
        assertEquals(RocketIntentRateLimiter.Decision.ALLOWED, limiter.check(player, 50L));
    }
}
