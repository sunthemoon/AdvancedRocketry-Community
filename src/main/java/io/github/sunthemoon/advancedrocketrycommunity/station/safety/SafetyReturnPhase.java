package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

/**
 * ADR-066 section 3.3: one online player's adrift phase. After {@link #ADRIFT_TICKS} consecutive adrift ticks the
 * player is eligible, and an attempt starts at most once per {@link #ATTEMPT_SPACING_TICKS}. A tick that is not adrift
 * clears the phase, as do a Level change, logout, a vehicle and the return itself; the last attempt's time survives a
 * clear, so clearing never shortens the spacing. Pure; the caller owns one per player.
 */
public final class SafetyReturnPhase {
    public static final int ADRIFT_TICKS = 100;
    public static final long ATTEMPT_SPACING_TICKS = 200L;
    private static final long NEVER = Long.MIN_VALUE;

    private int adrift;
    private long lastAttempt = NEVER;

    /** One player tick at server tick {@code now}; true when an attempt may start now. */
    public boolean tick(boolean adriftNow, long now) {
        return tick(adriftNow, now, ADRIFT_TICKS);
    }

    /** R328 uses an earlier threshold inside a zero-gravity station; attempt spacing is unchanged. */
    public boolean tick(boolean adriftNow, long now, int requiredTicks) {
        if (requiredTicks < 1 || requiredTicks > ADRIFT_TICKS) {
            throw new IllegalArgumentException("Adrift threshold outside the safety-return bound");
        }
        if (!adriftNow) {
            adrift = 0;
            return false;
        }
        if (adrift < ADRIFT_TICKS) {
            adrift++;
        }
        return adrift >= requiredTicks && spaced(now);
    }

    /** Records an admitted attempt. */
    public void attempted(long now) {
        lastAttempt = now;
    }

    /** Ends the adrift phase. */
    public void clear() {
        adrift = 0;
    }

    public int adriftTicks() {
        return adrift;
    }

    private boolean spaced(long now) {
        // A clock that moved backwards (a new server session) never blocks the next attempt forever.
        return lastAttempt == NEVER || now < lastAttempt || now - lastAttempt >= ATTEMPT_SPACING_TICKS;
    }
}
