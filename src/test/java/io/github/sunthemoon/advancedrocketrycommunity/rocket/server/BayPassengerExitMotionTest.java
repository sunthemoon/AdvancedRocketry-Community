package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayPassengerExitMotionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static BayPassengerExitMotion.Sample sample(double x, double y, long tick) {
        return new BayPassengerExitMotion.Sample(new Vec3(x, y, 0), new Vec3(0, -.2, 0), tick, true, true);
    }
    private static BayPassengerExitMotion.Continuity descent() { return new BayPassengerExitMotion.Continuity(sample(0, 80, 0)); }
    @Test void normalDescentAndSameTickObservationsDoNotRefreshAllowance() {
        var motion = descent();
        assertTrue(motion.observe(sample(.5, 78, 1)));
        assertTrue(motion.observe(sample(1, 76, 1)));
        assertFalse(motion.observe(sample(1.01, 75.9, 1)));
    }
    @Test void relativeUpwardRepositionBelowOriginalCeilingRevokes() {
        var motion = descent();
        assertTrue(motion.observe(sample(0, 77, 1)));
        assertFalse(motion.observe(sample(0, 77.01, 1)));
    }
    @Test void missedSampleAndOlderTickRefuse() {
        assertFalse(descent().observe(sample(0, 79, 2)));
        assertFalse(descent().observe(sample(0, 79, -1)));
    }
    @Test void downwardAndHorizontalPerTickLimitsAreHard() {
        assertTrue(descent().observe(sample(1, 76, 1)));
        assertFalse(descent().observe(sample(1.00001, 79, 1)));
        assertFalse(descent().observe(sample(0, 75.99999, 1)));
        var motion = descent();
        assertTrue(motion.observe(sample(0, 77, 1)));
        assertFalse(motion.observe(sample(0, 75, 1)));
    }
    @Test void upwardVelocityUnsupportedModesAndNonfiniteValuesRefuse() {
        assertFalse(descent().observe(new BayPassengerExitMotion.Sample(new Vec3(0, 79, 0), new Vec3(0, .000002, 0), 1, true, true)));
        assertFalse(descent().observe(new BayPassengerExitMotion.Sample(new Vec3(0, 79, 0), Vec3.ZERO, 1, false, true)));
        assertFalse(descent().observe(sample(Double.NaN, 79, 1)));
        assertFalse(descent().observe(new BayPassengerExitMotion.Sample(new Vec3(0, 79, 0), new Vec3(0, Double.NaN, 0), 1, true, true)));
    }
    @Test void horizontalRadiusIsIndependentOfPerTickLimit() {
        var motion = descent();
        for (int i = 1; i <= 8; i++) { assertTrue(motion.observe(sample(i, 80 - i, i))); }
        assertFalse(motion.observe(sample(8.1, 71, 9)));
    }
    @Test void onlyOneOwnCorrectionIncrementOnTheSameConnectionIsAccepted() {
        Object connection = new Object();
        var before = new BayPassengerExitMotion.Generation(connection, 123);
        assertTrue(BayPassengerExitMotion.ownCorrection(before, new BayPassengerExitMotion.Generation(connection, 124)));
        assertFalse(BayPassengerExitMotion.ownCorrection(before, new BayPassengerExitMotion.Generation(connection, 125)));
        assertFalse(BayPassengerExitMotion.ownCorrection(before, new BayPassengerExitMotion.Generation(new Object(), 124)));
        assertFalse(BayPassengerExitMotion.ownCorrection(before, null));
        assertFalse(BayPassengerExitMotion.ownCorrection(null, before));
    }
    @Test void unavailableConnectionOrObserverCannotSupplyAGeneration() {
        var player = io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.allocate(net.minecraft.server.level.ServerPlayer.class);
        var observer = new BayPassengerExitMotion();
        assertNull(observer.generation(player));
    }
    @Test void motionSamplingDoesNotInvokeBlockReadingEntityConveniences() {
        var player = io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.allocate(ProbePlayer.class);
        var observed = BayPassengerExitMotion.sample(player, 5);
        assertEquals(new Vec3(1, 80, 2), observed.position());
        assertTrue(observed.ordinary());
        assertTrue(observed.airborne());
    }
    /** No Level exists: block-reading conveniences must never be part of pure motion observation. */
    private static final class ProbePlayer extends net.minecraft.server.level.ServerPlayer {
        private ProbePlayer() { super(null, null, null); }
        @Override public Vec3 position() { return new Vec3(1, 80, 2); }
        @Override public Vec3 getDeltaMovement() { return new Vec3(0, -.2, 0); }
        @Override public boolean isPassenger() { return false; }
        @Override public net.minecraft.world.entity.player.Abilities getAbilities() { return new net.minecraft.world.entity.player.Abilities(); }
        @Override public boolean isFallFlying() { return false; }
        @Override public boolean hasEffect(net.minecraft.world.effect.MobEffect effect) { return false; }
        @Override public boolean isSwimming() { return false; }
        @Override public boolean isInWater() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean onGround() { return false; }
        @Override public boolean onClimbable() { throw new AssertionError("Uncharged climb world read"); }
        @Override public boolean isInWaterOrBubble() { throw new AssertionError("Uncharged bubble world read"); }
    }
}
