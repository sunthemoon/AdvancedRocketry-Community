package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player intent spacing in server ticks; runtime state, forgotten on logout and cleared with the server. */
final class IntentRateLimiter {
    private final Map<UUID, int[]> lastTicks = new HashMap<>();

    boolean allow(UUID player, boolean selection, int now, RegistryLimits limits) {
        int[] last = lastTicks.computeIfAbsent(player, key -> new int[] {Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2});
        int slot = selection ? 1 : 0;
        int interval = selection ? limits.selectionTicks() : limits.intentTicks();
        if (now - last[slot] < interval) {
            return false;
        }
        last[slot] = now;
        return true;
    }

    void forget(UUID player) {
        lastTicks.remove(player);
    }

    void clear() {
        lastTicks.clear();
    }
}
