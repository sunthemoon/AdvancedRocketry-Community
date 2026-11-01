package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayPassengerExitCorrectionWindowTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private final Object connection = new Object();
    private BayPassengerExitMotion.Generation generation(int counter) {
        return new BayPassengerExitMotion.Generation(connection, counter);
    }
    private static BayPassengerExitMotion.Sample sample(double x, double y, long tick) {
        return new BayPassengerExitMotion.Sample(new Vec3(x, y, 0), new Vec3(0, -.2, 0), tick, true, true);
    }
    private BayPassengerExitMotion.CorrectionWindow window(long deadline) {
        return new BayPassengerExitMotion.CorrectionWindow(generation(10),
                new BayPassengerExitMotion.Continuity(sample(0, 80, 100)), deadline);
    }
    @Test void oneExtraStepIsAcceptedAtEachEarlyBoundary() {
        for (long tick = 100; tick <= 102; tick++) {
            var state = window(300);
            if (tick == 102) { assertTrue(state.observe(generation(10), sample(0, 80, 101))); }
            assertTrue(state.observe(generation(11), sample(0, 80, tick)));
            assertTrue(state.accepted);
            assertTrue(state.observe(generation(11), sample(0, 80, tick)));
        }
    }
    @Test void continuousResultMayMoveButDoesNotRebaselineOrReplenishAllowance() {
        var state = window(300);
        assertTrue(state.observe(generation(10), sample(.5, 78, 101)));
        assertTrue(state.observe(generation(11), sample(1, 76, 101)));
        assertEquals(new Vec3(0, 80, 0), state.continuity.origin);
        assertEquals(1, state.continuity.horizontal);
        assertEquals(4, state.continuity.downward);
        assertEquals(100, state.start);
        assertEquals(300, state.deadline);
        assertFalse(state.observe(generation(11), sample(1.01, 75.99, 101)));
    }
    @Test void secondExtraCounterStepPermanentlyRevokes() {
        var state = window(300);
        assertTrue(state.observe(generation(11), sample(0, 79, 101)));
        assertFalse(state.observe(generation(12), sample(0, 78, 102)));
        assertFalse(state.observe(generation(11), sample(0, 78, 102)));
    }
    @Test void firstExtraStepAfterTheWindowRevokesEvenWithContinuousMovement() {
        var state = window(300);
        assertTrue(state.observe(generation(10), sample(0, 79, 101)));
        assertTrue(state.observe(generation(10), sample(0, 78, 102)));
        assertFalse(state.observe(generation(11), sample(0, 77, 103)));
    }
    @Test void differentConnectionUnknownObservationAndNonUnitStepsRevoke() {
        assertFalse(window(300).observe(new BayPassengerExitMotion.Generation(new Object(), 11), sample(0, 80, 101)));
        assertFalse(window(300).observe(null, sample(0, 80, 101)));
        assertFalse(window(300).observe(generation(11), null));
        assertFalse(window(300).observe(generation(11), new BayPassengerExitMotion.Sample(null, Vec3.ZERO, 101, true, true)));
        assertFalse(window(300).observe(generation(12), sample(0, 80, 101)));
        assertFalse(window(300).observe(generation(9), sample(0, 80, 101)));
        var missing = new BayPassengerExitMotion.CorrectionWindow(null,
                new BayPassengerExitMotion.Continuity(sample(0, 80, 100)), 300);
        assertFalse(missing.observe(generation(11), sample(0, 80, 101)));
    }
    @Test void immutableDeadlineIsStrictIncludingForUnchangedGeneration() {
        assertTrue(window(102).observe(generation(11), sample(0, 79, 101)));
        var state = window(102);
        assertTrue(state.observe(generation(10), sample(0, 79, 101)));
        assertFalse(state.observe(generation(11), sample(0, 78, 102)));
        assertFalse(window(100).observe(generation(10), sample(0, 80, 100)));
    }
    @Test void earlyStepCannotHideAnyContinuityFailure() {
        assertFalse(window(300).observe(generation(11), sample(0, 80.01, 101)));
        assertFalse(window(300).observe(generation(11), sample(1.01, 79, 101)));
        assertFalse(window(300).observe(generation(11), sample(0, 75.99, 101)));
        assertFalse(window(300).observe(generation(11), sample(0, 79, 102)));
        assertFalse(window(300).observe(generation(11), sample(0, 80, 99)));
        assertFalse(window(300).observe(generation(11), sample(Double.NaN, 79, 101)));
        assertFalse(window(300).observe(generation(11), new BayPassengerExitMotion.Sample(new Vec3(0, 79, 0), Vec3.ZERO, 101, false, true)));
        assertFalse(window(300).observe(generation(11), new BayPassengerExitMotion.Sample(new Vec3(0, 79, 0), new Vec3(0, .01, 0), 101, true, true)));
    }
    @Test void acceptedStepPreservesTheOriginalRadiusForTheRestOfTheDescent() {
        var state = window(300);
        for (int i = 1; i <= 8; i++) { assertTrue(state.observe(generation(11), sample(i, 80 - i, 100 + i))); }
        assertFalse(state.observe(generation(11), sample(8.01, 71, 109)));
    }
}
