package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

/** Pure display and edge rules. Display saturation never changes a flight quote or resource mutation. */
public final class RocketMonitorRules {
    public static final int HEIGHT = 0, FUEL = 1;
    private RocketMonitorRules() { }
    public static boolean rising(boolean previous, boolean signal) { return signal && !previous; }
    public static int milli(double value, int bound) {
        if (!Double.isFinite(value)) { return 0; }
        return (int) Math.max(-bound, Math.min(bound, Math.round(value * 1000.0)));
    }
    public static int comparator(int mode, double relativeHeight, int flightHeight, long amount, long capacity) {
        double ratio = mode == HEIGHT ? (flightHeight > 0 ? relativeHeight / flightHeight : 0)
                : (capacity > 0 ? (double) amount / capacity : 0);
        return Double.isFinite(ratio) ? (int) Math.max(0, Math.min(15, Math.floor(ratio * 15))) : 0;
    }
}
