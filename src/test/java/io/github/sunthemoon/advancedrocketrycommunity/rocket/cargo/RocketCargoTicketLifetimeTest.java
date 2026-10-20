package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RocketCargoTicketLifetimeTest {
    @Test void noProgressExpiresAtFortyTicksNotBefore() {
        var lifetime = new RocketCargoTicketLifetime(100);
        for (long tick = 100; tick < 140; tick++) {
            lifetime.progress(tick, false, false);
            assertNull(lifetime.expired(tick));
        }
        assertEquals("no-progress-40-ticks", lifetime.expired(140));
    }

    @Test void actualProgressResetsOnlyThePreparingDeadline() {
        var lifetime = new RocketCargoTicketLifetime(0);
        lifetime.progress(39, true, false);
        assertNull(lifetime.expired(78));
        assertEquals("no-progress-40-ticks", lifetime.expired(79));
        lifetime = new RocketCargoTicketLifetime(0);
        lifetime.progress(39, true, true);
        for (long tick = 40; tick < 79; tick++) {
            lifetime.progress(tick, true, true);
            assertNull(lifetime.expired(tick));
        }
        assertEquals("ready-unpublished-40-ticks", lifetime.expired(79));
    }
}
