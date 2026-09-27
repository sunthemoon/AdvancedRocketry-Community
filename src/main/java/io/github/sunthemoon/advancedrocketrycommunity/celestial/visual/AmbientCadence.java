package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

/** Session rate budget, deliberately not reset by profile/world/resource transitions. */
public final class AmbientCadence {
    public static final int INTERVAL = 400;
    private int remaining = INTERVAL;

    public boolean tick(boolean eligible, boolean playing) {
        if (remaining > 0) {
            remaining--;
        }
        if (remaining == 0 && eligible && !playing) {
            remaining = INTERVAL;
            return true;
        }
        return false;
    }
}
