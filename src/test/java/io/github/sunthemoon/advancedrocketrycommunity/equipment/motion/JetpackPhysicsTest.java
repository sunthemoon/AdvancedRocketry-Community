package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.JetpackPhysics.Impulse;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.JetpackPhysics.Motion;
import org.junit.jupiter.api.Test;

class JetpackPhysicsTest {
    private static final Motion REST = new Motion(0, 0, 0);

    @Test void incrementIsPointZeroEightTimesTheBoundedForce() {
        assertEquals(0.08 * 1.3, JetpackPhysics.increment(1.3));
        assertEquals(0.08 * 0.05, JetpackPhysics.increment(0.05));
        assertEquals(0.32, JetpackPhysics.increment(4.0));
        for (double force : new double[] {0.049, 4.001, 0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> JetpackPhysics.increment(force), "force " + force);
        }
    }

    @Test void normalThrustAddsOneIncrementUpToTheVerticalCap() {
        assertEquals(new Impulse(0, 0.08 * 1.3, 0), JetpackPhysics.plan(JetpackMode.NORMAL, REST, 1.3, 0, false));
        Impulse nearCap = JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0, 0.55, 0), 1.3, 0, false);
        assertEquals(0.6 - 0.55, nearCap.y(), 1.0E-12);
        assertTrue(JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0, 0.6, 0), 4.0, 0, false).isZero());
        assertTrue(JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0, 0.9, 0), 4.0, 0, false).isZero());
        // Falling fast: one increment only, so gravity above the available thrust still wins.
        assertEquals(0.08 * 0.05, JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0, -2, 0), 0.05, 0, false).y());
    }

    @Test void hoverPullsTowardTheReferenceAndNeverPushesDown() {
        assertTrue(JetpackPhysics.plan(JetpackMode.HOVER, REST, 1.3, 0, false).isZero(), "at the reference and at rest");
        assertEquals(0.08 * 1.3, JetpackPhysics.plan(JetpackMode.HOVER, REST, 1.3, 2, false).y());
        assertEquals(0.25 * 0.2, JetpackPhysics.plan(JetpackMode.HOVER, REST, 4.0, 0.2, false).y(), 1.0E-12);
        assertEquals(0.0784, JetpackPhysics.plan(JetpackMode.HOVER, new Motion(0, -0.0784, 0), 1.3, 0, false).y(), 1.0E-12);
        assertTrue(JetpackPhysics.plan(JetpackMode.HOVER, REST, 1.3, -3, false).isZero(), "above the reference");
        assertEquals(0.6, JetpackPhysics.plan(JetpackMode.HOVER, new Motion(0, 0.3, 0), 4.0, 100, false).y() + 0.3, 1.0E-12);
    }

    @Test void speedAssistFollowsHorizontalMotionAndStopsAtItsCap() {
        Impulse assisted = JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0.3, 0, 0.4), 1.3, 0, true);
        double expected = 0.2 * 0.08 * 1.3;
        assertEquals(expected * 0.6, assisted.x(), 1.0E-12);
        assertEquals(expected * 0.8, assisted.z(), 1.0E-12);
        assertEquals(0, JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0.3, 0, 0.4), 1.3, 0, false).x());
        assertEquals(0, JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0, 0, 0), 1.3, 0, true).x(), "no direction");
        Impulse capped = JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0.79, 0.6, 0), 4.0, 0, true);
        assertEquals(0.8 - 0.79, capped.x(), 1.0E-12);
        assertTrue(JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(0.8, 0.6, 0), 4.0, 0, true).isZero());
    }

    @Test void applyAddsWithoutExceedingOrLoweringEitherCap() {
        assertEquals(new Motion(0, 0.104, 0), JetpackPhysics.apply(REST, new Impulse(0, 0.104, 0)));
        assertEquals(0.6, JetpackPhysics.apply(new Motion(0, 0.55, 0), new Impulse(0, 0.32, 0)).y());
        assertEquals(0.9, JetpackPhysics.apply(new Motion(0, 0.9, 0), new Impulse(0, 0.32, 0)).y(), "never lowered");
        Motion fast = JetpackPhysics.apply(new Motion(0.79, 0, 0), new Impulse(0.05, 0, 0));
        assertEquals(0.8, fast.horizontal(), 1.0E-12);
        Motion knocked = JetpackPhysics.apply(new Motion(1.5, 0, 0), new Impulse(0.05, 0, 0));
        assertEquals(1.5, knocked.horizontal(), 1.0E-12, "a faster existing speed is not reduced");
        assertEquals(REST, JetpackPhysics.apply(REST, new Impulse(0, 0.5, 0)), "out-of-bounds impulse ignored");
        assertEquals(REST, JetpackPhysics.apply(REST, new Impulse(0, -0.1, 0)), "never pushes down");
        assertEquals(REST, JetpackPhysics.apply(REST, new Impulse(Double.NaN, 0.1, 0)));
    }

    @Test void nonFiniteEstimatesPlanNothing() {
        assertTrue(JetpackPhysics.plan(JetpackMode.NORMAL, new Motion(Double.NaN, 0, 0), 1.3, 0, true).isZero());
        assertTrue(JetpackPhysics.plan(JetpackMode.HOVER, REST, 1.3, Double.POSITIVE_INFINITY, false).isZero());
    }

    @Test void everyPlannedImpulseFitsTheWireBounds() {
        for (double force : new double[] {0.05, 1.3, 4.0}) {
            for (JetpackMode mode : JetpackMode.values()) {
                Impulse impulse = JetpackPhysics.plan(mode, new Motion(0.5, -1, -0.5), force, 50, true);
                assertTrue(impulse.bounded(), mode + " " + force);
                assertTrue(JetpackImpulsePacket.of(impulse).impulse().bounded());
            }
        }
    }
}
