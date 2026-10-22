package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowNativeEnvelope;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W133 (R55): one launch-readiness predicate, and the ordinary launch gate's promotion of a ready stationary rocket. */
class RocketLaunchReadinessTest {
    private static final UUID ROCKET = UUID.fromString("00000000-0000-0000-0000-000000001331");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000001332");
    private static final RocketPosition ORIGIN = new RocketPosition(8, 80, 8);
    private static final RocketStats STATS = new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void assembledRocketFilledThroughEscrowPromotesAndContinuesToCountdown() {
        var assembled = initial(1_000L);
        // EscrowReconciliationChange builds the fill's after flight this way: the same state, only new fuel.
        var filled = EscrowNativeEnvelope.fuelOnly(assembled, assembled.fuel().fill(1_000L).state());
        assertEquals(RocketFlightState.ASSEMBLED, filled.state());
        assertTrue(filled.launchReady());
        var source = RocketFlightService.launchSource(filled, 50L).orElseThrow();
        // Exactly the ordinary fuel change's promotion, at the launch's tick; nothing else about the flight changes.
        assertEquals(filled.withFuel(filled.fuel(), 50L), source);
        assertEquals(RocketFlightState.FUELED, source.state());
        assertEquals(50L, source.stateStartedGameTime());
        assertEquals(filled.fuel(), source.fuel());
        assertTrue(source.plan().isEmpty() && source.activeTransferId().isEmpty());
        assertEquals(filled.currentTarget(), source.currentTarget());
        assertEquals(filled.passengers(), source.passengers());
        var plan = RocketFlightPlanner.plan(STATS, source.fuel(), RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON,
                REQUEST, 50L).plan();
        var countdown = source.withPlan(plan).startCountdown(50L);
        assertEquals(RocketFlightState.COUNTDOWN, countdown.state());
        var cancelled = countdown.cancelCountdown(51L);
        assertEquals(RocketFlightState.FUELED, cancelled.state());
        assertEquals(filled.fuel(), cancelled.fuel());
    }

    @Test void nativeEscrowFillOfAnAssembledRocketIsLaunchReady() {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        // RocketEntity's assembly: an empty native bank keeps the rocket ASSEMBLED.
        var assembled = initial(1_000L).withFuel(RocketFuelState.empty(descriptor), 10L);
        assertEquals(RocketFlightState.ASSEMBLED, assembled.state());
        assertFalse(assembled.launchReady());
        var filled = EscrowNativeEnvelope.fuelOnly(assembled,
                assembled.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000));
        assertEquals(RocketFlightState.ASSEMBLED, filled.state());
        assertTrue(filled.launchReady());
        assertEquals(filled.withFuel(filled.fuel(), 60L), RocketFlightService.launchSource(filled, 60L).orElseThrow());
    }

    @Test void emptyAssembledRocketIsStillRefused() {
        var assembled = initial(1_000L);
        assertFalse(assembled.launchReady());
        assertTrue(RocketFlightService.launchSource(assembled, 50L).isEmpty());
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var nativeEmpty = assembled.withFuel(RocketFuelState.empty(descriptor), 10L);
        assertTrue(RocketFlightService.launchSource(nativeEmpty, 50L).isEmpty());
    }

    @Test void rocketThatRequiresNoFuelBehavesAsBefore() {
        var waiver = RocketPropulsion.Settings.capture(1, 1, 1, true, false, true);
        var fuel = RocketFuelState.empty(new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1_000, 1_000, 1, 1, waiver));
        // Assembly promotes a waived rocket to FUELED at once, so its launch source is the unchanged flight.
        var assembled = initial(1_000L).withFuel(fuel, 10L);
        assertEquals(RocketFlightState.FUELED, assembled.state());
        assertTrue(assembled.launchReady());
        assertSame(assembled, RocketFlightService.launchSource(assembled, 50L).orElseThrow());
        // A waived LANDED rocket is promoted as before.
        var landed = restore(RocketFlightState.LANDED, fuel);
        assertEquals(landed.withFuel(fuel, 50L), RocketFlightService.launchSource(landed, 50L).orElseThrow());
    }

    @Test void restoredWaivedAssembledRocketIsLaunchReadyLikeALandedOne() {
        var waiver = RocketPropulsion.Settings.capture(1, 1, 1, true, false, true);
        var fuel = RocketFuelState.empty(new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1_000, 1_000, 1, 1, waiver));
        // R58 accepts R55's change for this shape, which is not reachable through assembly.
        var restoredAssembled = restore(RocketFlightState.ASSEMBLED, fuel);
        assertTrue(restoredAssembled.launchReady());
        assertEquals(restoredAssembled.withFuel(fuel, 50L), RocketFlightService.launchSource(restoredAssembled, 50L).orElseThrow());
    }

    @Test void fueledAndLandedRocketsAreUnchanged() {
        var full = initial(1_000L).fuel().fill(1_000L).state();
        var fueled = initial(1_000L).withFuel(full, 5L);
        assertSame(fueled, RocketFlightService.launchSource(fueled, 50L).orElseThrow());
        var plan = RocketFlightPlanner.plan(STATS, full, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, REQUEST, 6L).plan();
        var cancelled = fueled.withPlan(plan).startCountdown(6L).cancelCountdown(7L);
        assertSame(cancelled, RocketFlightService.launchSource(cancelled, 50L).orElseThrow());
        var landed = restore(RocketFlightState.LANDED, full);
        assertTrue(landed.launchReady());
        assertEquals(landed.withFuel(full, 50L), RocketFlightService.launchSource(landed, 50L).orElseThrow());
        var landedEmpty = restore(RocketFlightState.LANDED, RocketFuelState.empty(1_000L));
        assertFalse(landedEmpty.launchReady());
        assertTrue(RocketFlightService.launchSource(landedEmpty, 50L).isEmpty());
    }

    @Test void movingAndFailedRocketsAreNeverLaunchReady() {
        var full = initial(1_000L).fuel().fill(1_000L).state();
        var plan = RocketFlightPlanner.plan(STATS, full, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, REQUEST, 6L).plan();
        var countdown = initial(1_000L).withFuel(full, 5L).withPlan(plan).startCountdown(6L);
        var ascent = countdown.completeCountdown(70L);
        var failed = EscrowNativeEnvelope.fuelOnly(initial(1_000L), full).markFailed(8L);
        for (var flight : new RocketFlightData[] {countdown, ascent, failed}) {
            assertFalse(flight.launchReady(), flight.state().name());
            assertTrue(RocketFlightService.launchSource(flight, 80L).isEmpty(), flight.state().name());
        }
        for (var state : RocketFlightState.values()) {
            boolean expected = state == RocketFlightState.FUELED
                    || state == RocketFlightState.ASSEMBLED || state == RocketFlightState.LANDED;
            assertEquals(expected, RocketFlightData.launchReady(state, full), state.name());
            assertEquals(state == RocketFlightState.FUELED, RocketFlightData.launchReady(state, RocketFuelState.empty(1_000L)), state.name());
        }
    }

    private static RocketFlightData initial(long capacity) {
        return RocketFlightData.initial(ROCKET, capacity, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), ORIGIN, 1L);
    }

    private static RocketFlightData restore(RocketFlightState state, RocketFuelState fuel) {
        return RocketFlightData.restore(RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION, ROCKET, state, fuel, null,
                RocketPassengerManifest.empty(1), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(),
                ORIGIN, 1L, null, new TravelTarget.BodySurface(RocketFlightPlanner.EARTH.bodyId()));
    }
}
