package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

/**
 * One device's refill cadence: at most one attempt per 20 ticks, with no catch-up burst. Elapsed intervals never
 * accumulate, and a device that loads (after an unload or a restart) waits one full interval before its first attempt,
 * so an attempt just before a stop and one just after the restart cannot fall within 20 ticks of each other. The pacer
 * lives in the block entity and is not saved.
 */
public final class RefillPacer {
    public static final int INTERVAL_TICKS = 20;
    private long anchor;

    private RefillPacer(long anchor) { this.anchor = anchor; }

    public static RefillPacer loadedAt(long gameTime) {
        return new RefillPacer(checked(gameTime));
    }

    /** Starts an attempt when a full interval has passed since the last start or load; a clock that went back re-anchors. */
    public boolean tryStart(long gameTime) {
        checked(gameTime);
        if (gameTime < anchor) {
            anchor = gameTime;
            return false;
        }
        if (gameTime - anchor < INTERVAL_TICKS) { return false; }
        anchor = gameTime;
        return true;
    }

    private static long checked(long gameTime) {
        if (gameTime < 0) { throw new IllegalArgumentException("Game time must be nonnegative"); }
        return gameTime;
    }
}
