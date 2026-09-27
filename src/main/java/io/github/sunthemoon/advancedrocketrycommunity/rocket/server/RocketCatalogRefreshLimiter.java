package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-lifetime full-catalog budget shared across a player's menu reopenings. */
final class RocketCatalogRefreshLimiter {
    private final Map<UUID, Long> sent = new HashMap<>();

    boolean acquire(UUID player, long now) {
        Objects.requireNonNull(player, "player");
        if (now < 0) { throw new IllegalArgumentException("Negative refresh time"); }
        Long before = sent.get(player);
        if (before != null && !expired(now, before)) { return false; }
        sent.entrySet().removeIf(entry -> expired(now, entry.getValue()));
        if (sent.size() >= RocketFlightLimits.MAX_TRACKED_INTENT_PLAYERS) { return false; }
        sent.put(player, now);
        return true;
    }

    private static boolean expired(long now, long before) {
        return now < before || now - before >= RocketNavigation.CATALOG_REFRESH_TICKS;
    }

    void remove(UUID player) { sent.remove(player); }
    void clear() { sent.clear(); }
}
