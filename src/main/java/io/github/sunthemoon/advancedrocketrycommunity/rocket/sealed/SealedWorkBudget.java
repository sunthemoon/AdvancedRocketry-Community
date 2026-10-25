package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;

/** One manager-owned cursor allowance, shared by staging, matching, rollback, recovery and lazy discovery/unseal. */
public final class SealedWorkBudget {
    private final Thread owner = Thread.currentThread();
    private long tick = Long.MIN_VALUE;
    private int remaining;

    public int remaining() { owner(); return remaining; }

    public void beginTick(long tick) {
        owner();
        if (this.tick != tick) { this.tick = tick; remaining = RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK; }
    }
    public boolean take() {
        owner();
        if (remaining == 0) { return false; }
        remaining--; return true;
    }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed work requires its owner thread"); }
    }
}
