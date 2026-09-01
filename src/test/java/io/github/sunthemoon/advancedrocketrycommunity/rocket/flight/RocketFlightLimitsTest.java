package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RocketFlightLimitsTest {
    @Test
    void flightTicketExpiresAfterTheLongestNormalTransfer() {
        int transferTicks = RocketFlightLimits.COUNTDOWN_TICKS
                + RocketFlightLimits.ASCENT_TICKS
                + RocketFlightLimits.TRANSIT_TICKS
                + RocketFlightLimits.DESCENT_TICKS;

        assertTrue(RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS > transferTicks);
        assertTrue(RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS <= 400);
    }
}
