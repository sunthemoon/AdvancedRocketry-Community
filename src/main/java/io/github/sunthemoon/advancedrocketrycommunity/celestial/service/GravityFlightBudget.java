package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

/** R328: finite extra vanilla floating ticks, never a flight permission or a counter reset. Pure and transient. */
public final class GravityFlightBudget {
    public static final int VANILLA_TICKS = 80;
    /** Forty seconds: covers the 0.10 g field minimum without granting arbitrarily long flight near zero g. */
    public static final int MAX_TICKS = 800;
    private int granted;

    public static int allowance(double factor) {
        if (!Double.isFinite(factor) || factor < 0.0D || factor >= 1.0D) { return VANILLA_TICKS; }
        return factor == 0.0D ? MAX_TICKS : (int) Math.min(MAX_TICKS, Math.ceil(VANILLA_TICKS / factor));
    }

    /**
     * Hold the native counter at 79 just before its next increment, spending exactly one extra tick each time.
     * Vanilla still observes a positive counter and performs the disconnect itself when the budget runs out.
     * Native resets (landing, sufficiently fast descent, paid suit flight) start a fresh phase.
     */
    public int adjust(int nativeCounter, double factor) {
        if (nativeCounter <= 0) { granted = 0; }
        if (nativeCounter == VANILLA_TICKS && granted < allowance(factor) - VANILLA_TICKS) {
            granted++;
            return nativeCounter - 1;
        }
        return nativeCounter;
    }
}
