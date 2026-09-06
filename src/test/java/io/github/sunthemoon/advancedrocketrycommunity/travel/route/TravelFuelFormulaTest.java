package io.github.sunthemoon.advancedrocketrycommunity.travel.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.TravelFuelFormula;
import org.junit.jupiter.api.Test;

class TravelFuelFormulaTest {
    @Test
    void legacyEarthMoonAndStationQuotesRemainExact() {
        assertEquals(367L, quote(200L, 1_000, 165, 50L));
        assertEquals(325L, quote(200L, 1_000, 0, 25L));
        assertEquals(242L, quote(200L, 0, 165, 25L));
    }

    @Test
    void routeDistanceAddsDirectlyToTheServerQuote() {
        assertEquals(550L, quote(400L, 1_000, 1_000, 50L));
        assertEquals(725L, quote(400L, 1_000, 1_000, 225L));
    }

    @Test
    void invalidBoundsAndOverflowFailClosed() {
        assertTrue(TravelFuelFormula.calculate(-1L, 1_000, 165, 50L, 1_000L).error().isPresent());
        assertTrue(TravelFuelFormula.calculate(1L, 10_001, 165, 50L, 1_000L).error().isPresent());
        assertTrue(TravelFuelFormula.calculate(
                1L, 1_000, 165, TravelFuelFormula.MAX_ROUTE_PLAN_DISTANCE + 1L, 1_000L
        ).error().isPresent());
        assertTrue(TravelFuelFormula.calculate(
                Long.MAX_VALUE, 1_000, 165, 50L, Long.MAX_VALUE
        ).error().isPresent());
    }

    private static long quote(long mass, int sourceGravity, int destinationGravity, long distance) {
        return TravelFuelFormula.calculate(
                mass,
                sourceGravity,
                destinationGravity,
                distance,
                1_000_000L
        ).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }
}
