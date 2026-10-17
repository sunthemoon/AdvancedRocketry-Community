package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedFrameTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void tickZeroedMotionNeverCreatesAStickyObservationDifference() {
        var permitted = EscrowGovernedEnvelope.comparison(GovernedTransferFixture.create().source());
        for (var state : RocketFlightState.values()) {
            var observed = permitted.copy(); observed.put("motion", GovernedTransferFixture.vector(0.125, -0.4, 0.5));
            assertTrue(EscrowGovernedFrame.matches(permitted, observed, state));
            assertFalse(EscrowGovernedFrame.persistentPlacement(permitted, observed, state));
            assertNotEquals(permitted, observed, "Observation must not mutate native data");
        }
    }

    @Test void liveEnvelopeMotionUsesTheExecutorsTransientRule() {
        var observed = EscrowGovernedEnvelope.comparison(GovernedTransferFixture.create().source());
        assertFalse(EscrowGovernedFrame.transientMotion(observed));
        for (var motion : java.util.List.of(GovernedTransferFixture.vector(0.125, 0, 0), GovernedTransferFixture.vector(0, -0.4, 0),
                GovernedTransferFixture.vector(0, 0, 1e-9))) {
            var moving = observed.copy(); moving.put("motion", motion);
            var vector = new net.minecraft.world.phys.Vec3(motion.getDouble(0), motion.getDouble(1), motion.getDouble(2));
            assertTrue(EscrowGovernedFrame.transientMotion(moving));
            assertEquals(EscrowGovernedFrame.transientMotion(vector), EscrowGovernedFrame.transientMotion(moving));
        }
        var missing = observed.copy(); missing.remove("motion");
        assertFalse(EscrowGovernedFrame.transientMotion(missing), "A malformed vector must mismatch, not wait");
        assertFalse(EscrowGovernedFrame.transientMotion(net.minecraft.world.phys.Vec3.ZERO));
    }

    @Test void onlyMovingPhasesNormalizeThePoseTheirPlanOverwrites() {
        var permitted = EscrowGovernedEnvelope.comparison(GovernedTransferFixture.create().source());
        var observed = permitted.copy(); observed.put("pos", GovernedTransferFixture.vector(25, 110, 9));
        observed.putIntArray("chunk", new int[] {1, 0});
        for (var state : RocketFlightState.values()) {
            boolean moving = state == RocketFlightState.ASCENT || state == RocketFlightState.DESCENT;
            assertEquals(moving, EscrowGovernedFrame.matches(permitted, observed, state), state.name());
            assertEquals(!moving, EscrowGovernedFrame.persistentPlacement(permitted, observed, state), state.name());
        }
    }

    @Test void normalizationDoesNotHidePersistentFlagsIdentitiesResourcesOrAssignments() {
        var permitted = EscrowGovernedEnvelope.comparison(GovernedTransferFixture.create().source());
        for (var state : java.util.List.of(RocketFlightState.ASCENT, RocketFlightState.DESCENT, RocketFlightState.LANDED)) {
            for (var key : permitted.getAllKeys()) {
                if (java.util.Set.of("pos", "chunk", "motion").contains(key)) { continue; }
                var observed = permitted.copy(); observed.remove(key);
                assertFalse(EscrowGovernedFrame.matches(permitted, observed, state), key);
            }
        }
    }
}
