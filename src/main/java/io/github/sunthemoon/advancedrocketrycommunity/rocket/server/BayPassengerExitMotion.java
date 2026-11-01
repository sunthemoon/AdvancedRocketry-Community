package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.lang.reflect.Field;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Pinned read-only connection observation; no player/network authority is changed. */
final class BayPassengerExitMotion {
    record Generation(Object connection, int counter) { }
    record Sample(Vec3 position, Vec3 velocity, long tick, boolean ordinary, boolean airborne) { }
    private Field counter;
    private boolean available = true;

    BayPassengerExitMotion() {
        try { counter = ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class, "f_9767_"); }
        catch (RuntimeException failure) { unavailable(); }
    }
    private void unavailable() {
        if (available) {
            available = false;
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_BAY_EXIT observer unavailable; placement/grants disabled for this server session");
        }
    }
    Generation generation(ServerPlayer player) {
        if (!available || player.connection == null) { return null; }
        try { return new Generation(player.connection, counter.getInt(player.connection)); }
        catch (IllegalAccessException | RuntimeException failure) { unavailable(); return null; }
    }
    static boolean ownCorrection(Generation before, Generation after) {
        return before != null && after != null && before.connection == after.connection && after.counter == before.counter + 1;
    }
    /** R264: one early counter step, not a new descent or movement baseline. */
    static final class CorrectionWindow {
        final Continuity continuity;
        final long start, deadline;
        Generation current;
        boolean accepted, closed;
        CorrectionWindow(Generation initial, Continuity continuity, long deadline) {
            this.current = initial; this.continuity = continuity;
            this.start = continuity.last.tick; this.deadline = deadline;
        }
        boolean observe(Generation next, Sample sample) {
            if (closed || current == null || next == null || sample == null || sample.tick >= deadline
                    || current.connection != next.connection) { closed = true; return false; }
            boolean changed = current.counter != next.counter;
            if (changed && (accepted || sample.tick < start || sample.tick - start > 2 || !ownCorrection(current, next))) {
                closed = true; return false;
            }
            if (!continuity.observe(sample)) { closed = true; return false; }
            if (changed) { accepted = true; current = next; }
            return true;
        }
    }
    static Sample sample(ServerPlayer player, long tick) {
        return new Sample(player.position(), player.getDeltaMovement(), tick,
                !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying()
                        && !player.hasEffect(MobEffects.LEVITATION)
                        // Climb/bubble contact is checked only inside the precharged world snapshot.
                        && !player.isSwimming() && !player.isInWater() && !player.isInLava(), !player.onGround());
    }
    /** Accumulate displacement within a tick; multiple callbacks cannot replenish movement allowance. */
    static final class Continuity {
        final Vec3 origin;
        Sample last;
        double horizontal, downward;
        Continuity(Sample initial) { origin = initial.position; last = initial; }
        boolean observe(Sample next) {
            if (!finite(next.position) || !finite(next.velocity) || !next.ordinary || next.velocity.y > 1e-6
                    || next.tick < last.tick || next.tick - last.tick > 1
                    || next.position.y - last.position.y > 1e-6 || next.position.y > origin.y + 1
                    || Math.hypot(next.position.x - origin.x, next.position.z - origin.z) > 8) { return false; }
            if (next.tick != last.tick) { horizontal = 0; downward = 0; }
            horizontal += Math.hypot(next.position.x - last.position.x, next.position.z - last.position.z);
            downward += Math.max(0, last.position.y - next.position.y);
            if (horizontal > 1 || downward > 4) { return false; }
            last = next;
            return true;
        }
    }
    static boolean finite(Vec3 point) { return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z); }
}
