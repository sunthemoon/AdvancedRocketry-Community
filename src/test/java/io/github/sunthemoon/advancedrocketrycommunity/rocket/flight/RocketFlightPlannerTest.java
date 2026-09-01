package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketFlightPlannerTest {
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000610");

    @Test
    void earthMoonAndMoonEarthUseTheSameDeterministicServerQuote() {
        RocketStats stats = validStats(1_000L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();

        RocketFlightPlanResult outward = RocketFlightPlanner.plan(
                stats,
                fuel,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                REQUEST,
                42L
        );
        RocketFlightPlanResult returning = RocketFlightPlanner.plan(
                stats,
                fuel,
                RocketFlightPlanner.MOON,
                RocketFlightPlanner.EARTH,
                REQUEST,
                43L
        );

        assertTrue(outward.success());
        assertTrue(returning.success());
        assertEquals(367L, outward.requiredFuel());
        assertEquals(367L, returning.requiredFuel());
        assertEquals(RocketFlightPlanner.MOON.bodyId(), outward.plan().destinationBody());
        assertEquals(RocketFlightPlanner.EARTH.bodyId(), returning.plan().destinationBody());
        assertEquals(42L, outward.plan().createdAtGameTime());
    }

    @Test
    void stationRoutesRequireAndBindOneServerSelectedStationUuid() {
        RocketStats stats = validStats(1_000L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        UUID stationId = UUID.fromString("00000000-0000-0000-0000-000000000700");

        RocketFlightPlanResult earthToStation = RocketFlightPlanner.plan(
                stats,
                fuel,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.SPACE_STATION,
                stationId,
                REQUEST,
                50L
        );
        RocketFlightPlanResult stationToMoon = RocketFlightPlanner.plan(
                stats,
                fuel,
                RocketFlightPlanner.SPACE_STATION,
                RocketFlightPlanner.MOON,
                null,
                REQUEST,
                51L
        );

        assertTrue(earthToStation.success());
        assertEquals(325L, earthToStation.requiredFuel());
        assertEquals(stationId, earthToStation.plan().destinationStation().orElseThrow());
        assertTrue(stationToMoon.success());
        assertEquals(242L, stationToMoon.requiredFuel());
        assertTrue(stationToMoon.plan().destinationStation().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RocketFlightPlanner.plan(
                stats,
                fuel,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.SPACE_STATION,
                null,
                REQUEST,
                52L
        ));
    }

    @Test
    void capacityAndCurrentFuelAreSeparateFailuresWithAReadableQuote() {
        RocketFlightPlanResult capacity = RocketFlightPlanner.plan(
                validStats(300L),
                RocketFuelState.empty(300L).fill(300L).state(),
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                REQUEST,
                0L
        );
        RocketFlightPlanResult amount = RocketFlightPlanner.plan(
                validStats(1_000L),
                RocketFuelState.empty(1_000L).fill(366L).state(),
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                REQUEST,
                0L
        );

        assertEquals(RocketFlightPlanCode.INSUFFICIENT_CAPACITY, capacity.code());
        assertEquals(367L, capacity.requiredFuel());
        assertEquals(RocketFlightPlanCode.INSUFFICIENT_FUEL, amount.code());
        assertEquals(367L, amount.requiredFuel());
    }

    @Test
    void serverStatsComponentsThrustAndFuelIdentityAreMandatory() {
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketStats missingGuidance = new RocketStats(3, 170L, 1_000L, 1_000L, 1, 1, 0, 0);
        RocketStats weak = new RocketStats(4, 2_000L, 1_000L, 1_000L, 1, 1, 1, 0);

        assertEquals(
                RocketFlightPlanCode.MISSING_FLIGHT_COMPONENTS,
                plan(missingGuidance, fuel).code()
        );
        assertEquals(RocketFlightPlanCode.INSUFFICIENT_THRUST, plan(weak, fuel).code());
        assertEquals(
                RocketFlightPlanCode.FUEL_STATE_MISMATCH,
                plan(validStats(900L), fuel).code()
        );
    }

    @Test
    void sameBodyAndUnlistedBodiesAreRejected() {
        RocketStats stats = validStats(1_000L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketTravelProfile space = new RocketTravelProfile(
                ModIdentity.id("space"),
                ModIdentity.id("space"),
                0,
                1
        );

        assertEquals(
                RocketFlightPlanCode.SAME_DESTINATION,
                RocketFlightPlanner.plan(
                        stats,
                        fuel,
                        RocketFlightPlanner.EARTH,
                        RocketFlightPlanner.EARTH,
                        REQUEST,
                        0L
                ).code()
        );
        assertEquals(
                RocketFlightPlanCode.UNSUPPORTED_ROUTE,
                RocketFlightPlanner.plan(
                        stats,
                        fuel,
                        RocketFlightPlanner.EARTH,
                        space,
                        REQUEST,
                        0L
                ).code()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RocketFlightPlanner.forBody(ModIdentity.id("mars"))
        );
    }

    @Test
    void arithmeticOverflowFailsClosedBeforeConstructingAPlan() {
        RocketStats extreme = new RocketStats(
                4,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                RocketFlightLimits.MAX_FUEL_CAPACITY,
                1,
                1,
                1,
                0
        );
        RocketFuelState fuel = RocketFuelState.empty(RocketFlightLimits.MAX_FUEL_CAPACITY)
                .fill(RocketFlightLimits.MAX_FUEL_CAPACITY)
                .state();

        assertEquals(RocketFlightPlanCode.ARITHMETIC_OVERFLOW, plan(extreme, fuel).code());
    }

    @Test
    void flightPlanRejectsNegativeTimeAndUnsupportedSchema() {
        RocketFlightPlan valid = plan(validStats(1_000L), RocketFuelState.empty(1_000L)
                .fill(1_000L)
                .state()).plan();
        assertThrows(
                IllegalArgumentException.class,
                () -> new RocketFlightPlan(
                        3,
                        valid.requestId(),
                        valid.sourceBody(),
                        valid.destinationBody(),
                        valid.sourceDimension(),
                        valid.destinationDimension(),
                        valid.requiredFuel(),
                        valid.createdAtGameTime()
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RocketFlightPlan(
                        RocketFlightPlan.SCHEMA_VERSION,
                        valid.requestId(),
                        valid.sourceBody(),
                        valid.destinationBody(),
                        valid.sourceDimension(),
                        valid.destinationDimension(),
                        valid.requiredFuel(),
                        -1L
                )
        );
    }

    private static RocketFlightPlanResult plan(RocketStats stats, RocketFuelState fuel) {
        return RocketFlightPlanner.plan(
                stats,
                fuel,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                REQUEST,
                0L
        );
    }

    private static RocketStats validStats(long capacity) {
        return new RocketStats(4, 200L, 1_000L, capacity, 1, 1, 1, 0);
    }
}
