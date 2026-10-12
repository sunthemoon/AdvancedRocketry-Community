package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-066 section 5.2: at most ten accepted jetpack packets per player in any 20-tick window. Only accepted
 * packets are recorded, and entries are forgotten at logout and server stop, so the map is bounded by the players
 * online.
 */
public final class MotionRateLimiter {
    public static final int MAX_PACKETS = 10;
    public static final int WINDOW_TICKS = 20;
    private final Map<UUID, Window> windows = new HashMap<>();

    public boolean allow(UUID player, long now) {
        Window window = windows.computeIfAbsent(Objects.requireNonNull(player, "player"), ignored -> new Window());
        // The slot to overwrite holds the oldest of the last ten accepted ticks.
        long oldest = window.ticks[window.next];
        if (oldest != Long.MIN_VALUE && now - oldest < WINDOW_TICKS) { return false; }
        window.ticks[window.next] = now;
        window.next = (window.next + 1) % MAX_PACKETS;
        return true;
    }

    public void forget(UUID player) { windows.remove(player); }
    public void clear() { windows.clear(); }
    public int tracked() { return windows.size(); }

    private static final class Window {
        private final long[] ticks = new long[MAX_PACKETS];
        private int next;
        private Window() { Arrays.fill(ticks, Long.MIN_VALUE); }
    }
}
