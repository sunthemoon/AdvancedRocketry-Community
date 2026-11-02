package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * W262 (R288, W257-R2): the order a real client produces on a ticking connection. Each server tick commits the client's
 * move packet at PlayerTick START, checks vanilla's temporary step at PlayerTick END, and commits the restored position at
 * the server-tick END. A packet handled after that END, such as the landing packet, is checked before the next tick.
 */
final class BayPassengerExitRealClientMotionTest {
    private static final double PAD = 100;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static BayPassengerExitMotion.Sample sample(double y, double velocity, long tick) {
        return new BayPassengerExitMotion.Sample(new Vec3(0, y, 0), new Vec3(0, velocity, 0), tick, true, true);
    }

    /** A real client falling at {@code speed}: one packet a tick, the server's own step at the same speed, then the landing. */
    @ParameterizedTest @ValueSource(doubles = {2.01, 2.5, 3.0, 3.5, 3.9})
    void aFallAtThisSpeedKeepsItsOfferToTheLandingPacket(double speed) {
        long tick = 1_000;
        double y = PAD + 40;
        var motion = new BayPassengerExitMotion.Continuity(sample(y, 0, tick));
        while (y - speed > PAD + .25) {
            tick++;
            y -= speed;
            assertTrue(motion.observe(sample(y, -speed, tick), true), "PlayerTick START, tick " + tick);
            double physics = Math.max(PAD, y - speed);
            assertTrue(motion.observe(sample(physics, -speed, tick), false),
                    "PlayerTick END: vanilla's temporary step of " + speed + " on top of a committed packet of " + speed);
            assertTrue(motion.observe(sample(y, -speed, tick), true), "server-tick END, tick " + tick);
            assertEquals(speed, motion.downward, 1e-9, "Only committed samples spend the tick's allowance");
        }
        tick++;
        assertTrue(motion.observe(sample(PAD + .25, -speed, tick), true), "the last airborne packet, committed at START");
        assertTrue(motion.observe(sample(PAD, -.0784, tick), false), "vanilla's temporary step reaches the pad");
        assertTrue(motion.observe(sample(PAD + .25, -.0784, tick), true), "the restored position at the server-tick END");
        // The landing packet is handled after this END and before the next START: checked, not charged to this tick.
        assertTrue(motion.observe(sample(PAD, 0, tick), false), "the landing packet's callbacks");
    }

    @Test void theLandingPacketAfterTheServerEndIsNotChargedToThatTick() {
        var motion = new BayPassengerExitMotion.Continuity(sample(PAD + 4.15, 0, 10));
        assertTrue(motion.observe(sample(PAD + .25, -3.9, 11), true));
        assertTrue(motion.observe(sample(PAD + .25, -.0784, 11), true));
        assertTrue(motion.observe(sample(PAD, 0, 11), false), "3.9 committed plus the landing's 0.25 is not one move");
        assertEquals(3.9, motion.downward, 1e-9);
        assertEquals(PAD + .25, motion.last.position().y, 1e-9);
    }

    @Test void aCheckStillRefusesMoreThanTheHardLimitFromTheCommittedBaseline() {
        var motion = new BayPassengerExitMotion.Continuity(sample(PAD + 10, 0, 10));
        assertTrue(motion.observe(sample(PAD + 8, -2, 11), true));
        assertFalse(motion.observe(sample(PAD + 3.99, -2, 11), false), "4.01 below the committed baseline");
        assertFalse(motion.observe(new BayPassengerExitMotion.Sample(new Vec3(1.01, PAD + 7, 0), new Vec3(0, -2, 0), 11, true, true), false));
        assertFalse(motion.observe(sample(PAD + 8.01, -2, 11), false), "upward from the committed baseline");
        assertEquals(2, motion.downward, 1e-9);
        assertEquals(PAD + 8, motion.last.position().y, 1e-9);
    }
}
