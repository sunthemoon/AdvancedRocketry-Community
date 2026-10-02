package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-054 section 7 "Barrier spacing" (review C12R-M3): the shared spacing, the station cooldown, unbinds, counts. */
final class BarrierSpacingTest {
    private static final UUID STATION = new UUID(0L, 1L);
    private static final UUID OTHER_STATION = new UUID(0L, 2L);

    @Test
    void requestsShareTheSpacingAndAStationItsCooldown() {
        BarrierSpacing spacing = new BarrierSpacing();
        assertTrue(spacing.admit(STATION, 1_000L));
        assertFalse(spacing.admit(OTHER_STATION, 1_019L), "inside the 20-tick spacing");
        assertFalse(spacing.admit(null, 1_019L), "an owner resolve shares the spacing");
        assertFalse(spacing.admit(STATION, 1_020L), "inside the station's 100-tick cooldown");
        assertTrue(spacing.admit(OTHER_STATION, 1_020L), "a refusal recorded nothing: the spacing still ran from 1,000");
        assertFalse(spacing.admit(STATION, 1_099L));
        assertTrue(spacing.admit(STATION, 1_100L), "the cooldown ends 100 ticks after the station's last request");
    }

    @Test
    void anUnbindCountsTowardTheSpacingButNotTheCooldown() {
        BarrierSpacing spacing = new BarrierSpacing();
        spacing.unbound(500L);
        assertFalse(spacing.admit(STATION, 519L), "the next bind waits for the unbind's spacing");
        assertTrue(spacing.admit(STATION, 520L), "an unbind starts no station cooldown");
        spacing.unbound(530L);
        spacing.unbound(510L);
        assertFalse(spacing.admit(OTHER_STATION, 549L), "an earlier unbind does not shorten the spacing");
        assertTrue(spacing.admit(OTHER_STATION, 550L));
    }

    @Test
    void atMostFiveStationsCoolAndExemptFlushesAreCounted() {
        BarrierSpacing spacing = new BarrierSpacing();
        for (int i = 0; i < 20; i++) {
            assertTrue(spacing.admit(new UUID(1L, i), 20L * i));
            assertTrue(spacing.coolingStations() <= 5, "stations cooling: " + spacing.coolingStations());
        }
        spacing.exemptFlush();
        spacing.exemptFlush();
        assertEquals(2L, spacing.exemptFlushes());
        spacing.clear();
        assertEquals(0L, spacing.exemptFlushes());
        assertEquals(0, spacing.coolingStations());
        assertTrue(spacing.admit(STATION, Long.MIN_VALUE / 4), "a cleared spacing admits at once");
    }
}
