package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketFlightDataTest {
    private static final UUID ROCKET = UUID.fromString("00000000-0000-0000-0000-000000000631");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000632");
    private static final UUID TRANSFER = UUID.fromString("00000000-0000-0000-0000-000000000633");
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(8, 80, 8);
    private static final RocketPosition MOON_ORIGIN = new RocketPosition(72, 80, 8);

    @Test
    void completeLifecyclePreservesIdentityAndDebitsOnlyAtDestinationAuthority() {
        RocketFlightData initial = initial();
        RocketFuelState full = initial.fuel().fill(1_000L).state();
        RocketFlightData fueled = initial.withFuel(full, 1L);
        RocketFlightPlan plan = RocketFlightPlanner.plan(
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(
                        4, 200L, 1_000L, 1_000L, 1, 1, 1, 0
                ),
                full,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                REQUEST,
                2L
        ).plan();

        RocketFlightData countdown = fueled.withPlan(plan).startCountdown(3L);
        RocketFlightData ascent = countdown.completeCountdown(63L);
        RocketFlightData transit = ascent.beginTransit(TRANSFER, 143L);
        RocketFuelState debited = transit.fuel().debit(TRANSFER, plan.requiredFuel()).state();
        RocketFlightData descent = transit.arriveAtDestination(
                debited,
                RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(),
                MOON_ORIGIN,
                164L
        );
        RocketFlightData landed = descent.land(244L);

        assertEquals(RocketFlightState.FUELED, fueled.state());
        assertEquals(RocketFlightState.COUNTDOWN, countdown.state());
        assertEquals(RocketFlightState.ASCENT, ascent.state());
        assertEquals(RocketFlightState.TRANSIT, transit.state());
        assertEquals(1_000L, transit.fuel().amount());
        assertEquals(RocketFlightState.DESCENT, descent.state());
        assertEquals(633L, descent.fuel().amount());
        assertEquals(RocketFlightState.LANDED, landed.state());
        assertEquals(ROCKET, landed.logicalRocketId());
        assertEquals(RocketFlightPlanner.MOON.bodyId(), landed.currentBody());
        assertEquals(MOON_ORIGIN, landed.currentOrigin());
        assertTrue(landed.plan().isEmpty());
        assertTrue(landed.activeTransferId().isEmpty());
    }

    @Test
    void cancellationKeepsTheServerPlanButRecoveryClearsIt() {
        RocketFlightData fueled = initial().withFuel(
                RocketFuelState.empty(1_000L).fill(1_000L).state(),
                1L
        );
        RocketFlightPlan plan = plan(fueled.fuel());
        RocketFlightData cancelled = fueled.withPlan(plan).startCountdown(2L).cancelCountdown(3L);
        RocketFlightData failed = cancelled.markFailed(4L);
        RocketFlightData recovered = failed.recover(true, 5L);

        assertEquals(RocketFlightState.FUELED, cancelled.state());
        assertEquals(plan, cancelled.plan().orElseThrow());
        assertEquals(RocketFlightState.FAILED_RECOVERABLE, failed.state());
        assertEquals(RocketFlightState.FUELED, recovered.state());
        assertTrue(recovered.plan().isEmpty());
        assertTrue(recovered.activeTransferId().isEmpty());
    }

    @Test
    void passengerUpdatesCannotChangeServerSeatCapacity() {
        RocketFlightData initial = initial();
        UUID passenger = UUID.fromString("00000000-0000-0000-0000-000000000634");
        RocketPassengerManifest assigned = initial.passengers().assign(passenger).orElseThrow();

        assertEquals(assigned, initial.withPassengers(assigned).passengers());
        assertThrows(
                IllegalArgumentException.class,
                () -> initial.withPassengers(RocketPassengerManifest.empty(2))
        );
    }

    @Test
    void persistentStateShapeRejectsMissingPlansTransfersAndWrongLocations() {
        RocketFlightData initial = initial();
        RocketFuelState full = initial.fuel().fill(1_000L).state();
        RocketFlightPlan plan = plan(full);

        assertThrows(
                IllegalArgumentException.class,
                () -> restore(RocketFlightState.COUNTDOWN, full, null, null,
                        RocketFlightPlanner.EARTH, EARTH_ORIGIN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> restore(RocketFlightState.TRANSIT, full, plan, null,
                        RocketFlightPlanner.EARTH, EARTH_ORIGIN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> restore(RocketFlightState.DESCENT, full, plan, TRANSFER,
                        RocketFlightPlanner.EARTH, EARTH_ORIGIN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> restore(RocketFlightState.FUELED, RocketFuelState.empty(1_000L), null, null,
                        RocketFlightPlanner.EARTH, EARTH_ORIGIN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> restore(RocketFlightState.DISASSEMBLED, full, null, null,
                        RocketFlightPlanner.EARTH, EARTH_ORIGIN)
        );
    }

    @Test
    void illegalRuntimeTransitionsFailWithoutPartialData() {
        RocketFlightData initial = initial();

        assertThrows(IllegalStateException.class, () -> initial.startCountdown(1L));
        assertThrows(IllegalStateException.class, () -> initial.completeCountdown(1L));
        assertThrows(IllegalStateException.class, () -> initial.beginTransit(TRANSFER, 1L));
        assertThrows(IllegalStateException.class, () -> initial.land(1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> initial.withFuel(RocketFuelState.empty(999L), 1L)
        );
    }

    private static RocketFlightData initial() {
        return RocketFlightData.initial(
                ROCKET,
                1_000L,
                1,
                RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(),
                EARTH_ORIGIN,
                0L
        );
    }

    private static RocketFlightPlan plan(RocketFuelState fuel) {
        return RocketFlightPlanner.plan(
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(
                        4, 200L, 1_000L, 1_000L, 1, 1, 1, 0
                ),
                fuel,
                RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON,
                REQUEST,
                1L
        ).plan();
    }

    private static RocketFlightData restore(
            RocketFlightState state,
            RocketFuelState fuel,
            RocketFlightPlan plan,
            UUID transfer,
            RocketTravelProfile location,
            RocketPosition origin
    ) {
        return RocketFlightData.restore(
                RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION,
                ROCKET,
                state,
                fuel,
                plan,
                RocketPassengerManifest.empty(1),
                location.bodyId(),
                location.dimensionId(),
                origin,
                0L,
                transfer
        );
    }
}
