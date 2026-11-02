package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayPassengerExitCommittedMotionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static BayPassengerExitMotion.Sample sample(double x, double y, long tick) {
        return new BayPassengerExitMotion.Sample(new Vec3(x, y, 0), new Vec3(0, -.2, 0), tick, true, true);
    }
    private static BayPassengerExitMotion.Continuity descent() {
        return new BayPassengerExitMotion.Continuity(sample(0, 80, 100));
    }
    @Test void temporaryPhysicsAndRestoreUseTheSameCommittedBaseline() {
        var motion = descent();
        for (int tick = 101; tick < 110; tick++) {
            assertTrue(motion.observe(sample(0, 80, tick), true));
            var committed = motion.last;
            assertTrue(motion.observe(sample(0, 79.9216, tick), false));
            assertSame(committed, motion.last);
            assertEquals(0, motion.downward);
            assertTrue(motion.observe(sample(0, 80, tick), true));
        }
    }
    @Test void checkedDisplacementIsProjectedOntoCommittedTickUsage() {
        var motion = descent();
        assertTrue(motion.observe(sample(.5, 78, 101), true));
        for (int i = 0; i < 3; i++) { assertTrue(motion.observe(sample(1, 76, 101), false)); }
        assertEquals(.5, motion.horizontal);
        assertEquals(2, motion.downward);
        assertTrue(motion.observe(sample(1, 76, 101), true));
        assertEquals(1, motion.horizontal);
        assertEquals(4, motion.downward);
        assertFalse(motion.observe(sample(1.01, 75.99, 101), false));
    }
    @Test void checkCannotReplenishTheCumulativeCommittedAllowance() {
        var motion = descent();
        assertTrue(motion.observe(sample(.5, 78, 101), true));
        assertTrue(motion.observe(sample(.4, 77, 101), false));
        assertTrue(motion.observe(sample(0, 76, 101), true));
        assertFalse(motion.observe(sample(.01, 76, 101), true));
    }
    @Test void temporaryPositionNeverBecomesANewOriginOrTimestamp() {
        var motion = descent(); var baseline = motion.last;
        assertTrue(motion.observe(sample(.5, 78, 101), false));
        assertSame(baseline, motion.last);
        assertEquals(new Vec3(0, 80, 0), motion.origin);
        assertFalse(motion.observe(sample(.5, 78, 102), true));
        assertFalse(motion.observe(sample(.5, 78, 102), false));
        assertFalse(motion.observe(sample(0, 80, 99), false));
    }
    @Test void upwardCheckBelowTheOriginalCeilingStillRefuses() {
        var motion = descent();
        assertTrue(motion.observe(sample(0, 77, 101), true));
        assertFalse(motion.observe(sample(0, 77.01, 101), false));
        assertFalse(motion.observe(new BayPassengerExitMotion.Sample(new Vec3(0, 76, 0),
                new Vec3(0, .001, 0), 101, true, true), false));
    }
    @Test void checkOnlySamplesKeepTheHardMovementLimits() {
        assertTrue(descent().observe(sample(1, 76, 101), false));
        assertFalse(descent().observe(sample(1.00001, 79, 101), false));
        assertFalse(descent().observe(sample(0, 75.99999, 101), false));
        var motion = descent();
        for (int i = 1; i <= 8; i++) { assertTrue(motion.observe(sample(i, 80 - i, 100 + i), true)); }
        assertFalse(motion.observe(sample(8.01, 71, 109), false));
    }
    @Test void unknownNonfiniteAndUnsupportedChecksRefuse() {
        assertFalse(descent().observe(null, false));
        assertFalse(descent().observe(sample(Double.NaN, 79, 101), false));
        assertFalse(descent().observe(new BayPassengerExitMotion.Sample(new Vec3(0, 79, 0),
                new Vec3(0, Double.NaN, 0), 101, true, true), false));
        assertFalse(descent().observe(new BayPassengerExitMotion.Sample(new Vec3(0, 79, 0),
                Vec3.ZERO, 101, false, true), false));
    }
    @Test void failedCheckDoesNotMutateTheCommittedState() {
        var motion = descent(); var baseline = motion.last;
        assertFalse(motion.observe(sample(2, 75, 101), false));
        assertSame(baseline, motion.last);
        assertEquals(0, motion.horizontal);
        assertEquals(0, motion.downward);
    }
    @Test void temporaryCorrectionConsumesTheOneStepButDoesNotCommitMotion() {
        Object connection = new Object();
        var motion = descent(); var baseline = motion.last;
        var window = new BayPassengerExitMotion.CorrectionWindow(
                new BayPassengerExitMotion.Generation(connection, 10), motion, 300);
        var next = new BayPassengerExitMotion.Generation(connection, 11);
        assertTrue(window.observe(next, sample(.5, 79, 101), false));
        assertTrue(window.accepted);
        assertSame(baseline, motion.last);
        assertEquals(0, motion.horizontal);
        assertEquals(0, motion.downward);
        assertTrue(window.observe(next, sample(0, 80, 101), true));
        assertEquals(100, window.start);
        assertEquals(300, window.deadline);
        assertFalse(window.observe(new BayPassengerExitMotion.Generation(connection, 12), sample(0, 80, 101), false));
        assertTrue(window.closed);
    }
    @Test void failingTemporaryCorrectionPermanentlyClosesTheWindow() {
        Object connection = new Object();
        var window = new BayPassengerExitMotion.CorrectionWindow(
                new BayPassengerExitMotion.Generation(connection, 10), descent(), 300);
        assertFalse(window.observe(new BayPassengerExitMotion.Generation(connection, 11), sample(0, 80.01, 101), false));
        assertFalse(window.observe(new BayPassengerExitMotion.Generation(connection, 10), sample(0, 80, 101), true));
    }
    @Test void temporarySamplesCannotPostponeExpiryOrTheCorrectionWindow() {
        Object connection = new Object(); var generation = new BayPassengerExitMotion.Generation(connection, 10);
        var motion = descent(); var window = new BayPassengerExitMotion.CorrectionWindow(generation, motion, 103);
        for (int tick = 101; tick <= 102; tick++) {
            assertTrue(window.observe(generation, sample(0, 80, tick), true));
            assertTrue(window.observe(generation, sample(0, 79.9, tick), false));
        }
        assertFalse(window.observe(generation, sample(0, 80, 103), false));
        var late = new BayPassengerExitMotion.CorrectionWindow(generation, motion, 300);
        // Use a fresh initial tick: this window's permitted extra step still ends at start + 2.
        for (int tick = 103; tick <= 104; tick++) { assertTrue(late.observe(generation, sample(0, 80, tick), true)); }
        assertFalse(late.observe(new BayPassengerExitMotion.Generation(connection, 11), sample(0, 80, 105), false));
    }
}
