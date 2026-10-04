package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

/** Whole-mB arithmetic; reducing configured capacity never reduces an existing balance. */
public final class TankCapacity {
    public static final int BASE = 64_000;
    public static final double MIN_MULTIPLIER = 0.25;
    public static final double MAX_MULTIPLIER = 4.0;

    public static int fromMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException("Tank multiplier outside COMMON limits");
        }
        return (int) Math.floor(BASE * multiplier);
    }

    public static int accepted(int stored, int capacity, int offered, boolean compatible) {
        if (stored < 0 || capacity <= 0 || offered <= 0 || !compatible || stored >= capacity) {
            return 0;
        }
        return Math.min(offered, capacity - stored);
    }

    public static int drained(int stored, int requested) {
        return stored <= 0 || requested <= 0 ? 0 : Math.min(stored, requested);
    }

    private TankCapacity() { }
}
