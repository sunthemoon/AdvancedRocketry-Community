package io.github.sunthemoon.advancedrocketrycommunity.equipment.laser;

import java.util.Objects;

/** One target and one independently retained cooldown; no Minecraft or world references. */
public final class LaserProgress<T> {
    public static final int BREAK_TICKS = 20;
    public static final int COOLDOWN_TICKS = 5;
    private T target;
    private int progress;
    private long lastTick = Long.MIN_VALUE;
    private long cooldownThrough = Long.MIN_VALUE;

    public boolean tick(T candidate, long tick, int required) {
        if (required < 1 || required > BREAK_TICKS) { throw new IllegalArgumentException("Invalid work threshold"); }
        if (tick <= lastTick) { return false; }
        boolean continuous = lastTick != Long.MIN_VALUE && tick == lastTick + 1;
        lastTick = tick;
        if (tick <= cooldownThrough) { reset(); return false; }
        if (candidate == null) { reset(); return false; }
        if (!continuous || !Objects.equals(target, candidate)) { progress = 0; }
        target = candidate;
        return ++progress >= required;
    }

    /** Called once per attempted effect, even when protection refuses it; no delayed reward is queued. */
    public void attempted(long tick) {
        reset();
        cooldownThrough = tick + COOLDOWN_TICKS;
    }

    /** Use end or an identity change resets work, never bypasses the five-tick cooldown. */
    public void reset() { target = null; progress = 0; }
    public int progress() { return progress; }
    public boolean cooling(long tick) { return tick <= cooldownThrough; }
}
