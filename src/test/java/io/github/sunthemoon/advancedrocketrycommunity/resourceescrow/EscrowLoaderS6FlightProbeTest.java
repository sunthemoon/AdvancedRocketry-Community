package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6FlightProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void stagedArmObservationHashesTheCompleteValidatedTupleWithoutMutation() {
        var root = new EscrowLedger(UUID.randomUUID(), 1, 3, List.of(), List.of(), List.of());
        var armed = EscrowFlightAuthority.arm(root, EscrowFlightAuthorityTest.launch(root, 1), 4).generation(4);
        var payload = EscrowCodec.encode(armed); var original = payload.copy();
        assertEquals(EscrowNativeEnvelope.digest(armed.armedFlights().get(0).encode()),
                EscrowLoaderS6FlightProbe.stagedArmDigest(payload));
        assertEquals(original, payload);
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6FlightProbe.stagedArmDigest(EscrowCodec.encode(root)));
        var extra = payload.copy(); extra.getList("armed_flights", 10).getCompound(0).putInt("extra", 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6FlightProbe.stagedArmDigest(extra));
        var generation = payload.copy(); generation.putLong("writer_generation", 5);
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6FlightProbe.stagedArmDigest(generation));
    }

    @Test void loaderFilledStationarySourceUsesTheProductLaunchPromotionWithoutChangingItsCapture() {
        var empty = RocketFlightData.initial(UUID.randomUUID(), 2_000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(64, 100, 64), 10);
        var fuel = empty.fuel().fill(1_000).state();
        var plan = new RocketFlightPlan(3, UUID.randomUUID(), empty.currentBody(), RocketFlightPlanner.MOON.bodyId(),
                empty.currentDimension(), empty.currentDimension(), 500, 20);
        for (var state : List.of(RocketFlightState.ASSEMBLED, RocketFlightState.LANDED, RocketFlightState.FUELED)) {
            var base = RocketFlightData.restore(empty.schemaVersion(), empty.logicalRocketId(), state, fuel, null,
                    empty.passengers(), empty.currentBody(), empty.currentDimension(), empty.currentOrigin(), 10, null);
            assertTrue(base.launchReady());
            if (state != RocketFlightState.FUELED) { assertThrows(IllegalStateException.class, () -> base.withPlan(plan)); }
            var countdown = EscrowLoaderS6FlightProbe.launchCountdown(base, plan, 20);
            assertEquals(base.withFuel(fuel, 20).withPlan(plan).startCountdown(20), countdown);
            assertEquals(RocketFlightState.COUNTDOWN, countdown.state());
            assertSame(fuel, countdown.fuel());
            assertEquals(state, base.state());
            assertTrue(base.plan().isEmpty());
            assertEquals(10, base.stateStartedGameTime());
        }
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6FlightProbe.launchCountdown(empty, plan, 20));
        var active = empty.withFuel(fuel, 20).withPlan(plan).startCountdown(20);
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6FlightProbe.launchCountdown(active, plan, 21));
    }

    @Test void exactSelectedBoundariesCannotInterceptThePrecedingLoaderFill() {
        for (var side : List.of("STORE_BEFORE", "STORE_AFTER", "FORCE_BEFORE", "FORCE_AFTER", "READBACK_BEFORE", "READBACK_AFTER")) {
            var cut = "LF_S6_F1_" + side;
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6FlightProbe.selected(false, cut));
            assertTrue(EscrowLoaderS6FlightProbe.boundary(cut, "BARRIER_" + side, true, false));
            assertFalse(EscrowLoaderS6FlightProbe.boundary(cut, "BARRIER_" + side, false, false));
            assertFalse(EscrowLoaderS6FlightProbe.boundary(cut, "BARRIER_" + side + "_OTHER", true, false));
            assertFalse(EscrowLoaderS6FlightProbe.selected(true, cut + "_OTHER"));
        }
        assertFalse(EscrowLoaderS6FlightProbe.selected(true, null));
        assertFalse(EscrowLoaderS6FlightProbe.selected(true, "LF_S6_F1_"));
    }

    @Test void armAndPreparedSelectionsUseTheirActualAcknowledgementAndWriterCallbacks() {
        for (var side : List.of("BEFORE", "REPLACED", "ACK")) {
            var arm = "LF_S6_F1_ARM_" + side;
            var hook = side.equals("BEFORE") ? "K8" : side.equals("REPLACED") ? "K9" : "F1_ARM_ACK";
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, arm));
            assertTrue(EscrowLoaderS6FlightProbe.boundary(arm, hook, false, true));
            assertFalse(EscrowLoaderS6FlightProbe.boundary(arm, hook, false, false));
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, "LF_S6_F2_" + side));
            assertFalse(EscrowLoaderS6FlightProbe.boundary("LF_S6_F2_" + side, "K9", false, true));
        }
        assertTrue(EscrowLoaderS6FlightProbe.boundary("LF_S6_F2_ACK", "F2_PREPARED_ACK", false, true));
        assertFalse(EscrowLoaderS6FlightProbe.boundary("LF_S6_F2_ACK", "F2_PREPARED_SUBMITTED", false, true));
    }
}
