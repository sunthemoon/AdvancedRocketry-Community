package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedSourceFlightTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void ascentIsMonotonicAndClampedAtEveryTickIncludingTheLimit() {
        double previous = 80;
        for (int tick = 0; tick <= RocketFlightLimits.ASCENT_TICKS + 1; tick++) {
            double height = EscrowGovernedSourceFlight.ascentHeight(80, 500, 500 + tick);
            assertTrue(height >= previous && height <= 80 + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS);
            assertTrue(height - previous <= (double) RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS / RocketFlightLimits.ASCENT_TICKS + 1e-12);
            previous = height;
        }
        assertEquals(80 + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS, previous);
    }

    @Test void extremeClocksClampWithoutIntegerOverflow() {
        assertEquals(128, EscrowGovernedSourceFlight.ascentHeight(80, 0, Long.MAX_VALUE));
        assertEquals(80, EscrowGovernedSourceFlight.ascentHeight(80, Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals((double) Integer.MAX_VALUE + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                EscrowGovernedSourceFlight.ascentHeight(Integer.MAX_VALUE, 0, Long.MAX_VALUE));
    }

    @Test void invalidClockCannotAuthorizeMovement() {
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceFlight.ascentHeight(80, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceFlight.ascentHeight(80, 1, 0));
    }
}
