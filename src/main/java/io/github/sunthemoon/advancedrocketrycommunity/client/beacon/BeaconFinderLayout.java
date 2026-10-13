package io.github.sunthemoon.advancedrocketrycommunity.client.beacon;

import java.util.OptionalInt;

/** Pure GUI-pixel placement below the boss bars actually rendered in this frame. */
public final class BeaconFinderLayout {
    public static final int DEFAULT_TOP = 3;
    public static final int BOSS_BAR_HEIGHT = 5;
    private static final int GAP = 3;
    private static final int ABOVE_TOP = 1;
    private static final int LABEL_OFFSET = 8;

    private BeaconFinderLayout() {
    }

    /** Exclusive lower edge of an uncancelled vanilla bar; its name is above the bar, not below it. */
    public static long includeBossBar(long bottom, int barY) {
        return Math.max(bottom, (long) barY + BOSS_BAR_HEIGHT);
    }

    /** No partial strip is drawn when the remaining viewport cannot hold its label and text shadow. */
    public static OptionalInt top(long bossBottom, int screenHeight, int fontHeight) {
        long top = bossBottom == 0 ? DEFAULT_TOP : Math.max(DEFAULT_TOP, bossBottom + GAP + ABOVE_TOP);
        long bottom = top + LABEL_OFFSET + fontHeight + 1L;
        return top <= Integer.MAX_VALUE && bottom <= screenHeight ? OptionalInt.of((int) top) : OptionalInt.empty();
    }
}
