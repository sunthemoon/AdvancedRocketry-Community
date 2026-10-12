package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-065 section 11.2: at most one controller-screen mutation per player per
 * {@link StationLimits#CONTROLLER_ACTION_TICKS} ticks. Entries older than the window are dropped, so the map holds
 * at most the players who acted within it; it is server-thread only and cleared on logout and stop.
 */
public final class StationControllerRateLimit {
    private final Map<UUID, Long> lastAction = new LinkedHashMap<>();

    /** True, and recorded, when the player may act now. */
    public boolean admit(UUID playerId, long nowTick) {
        Objects.requireNonNull(playerId, "playerId");
        Iterator<Long> entries = lastAction.values().iterator();
        while (entries.hasNext()) {
            long time = entries.next();
            if (nowTick >= time && nowTick - time < StationLimits.CONTROLLER_ACTION_TICKS) {
                break;
            }
            entries.remove();
        }
        if (lastAction.containsKey(playerId)) {
            return false;
        }
        lastAction.put(playerId, nowTick);
        return true;
    }

    public void forget(UUID playerId) {
        lastAction.remove(playerId);
    }

    public int size() {
        return lastAction.size();
    }

    public void clear() {
        lastAction.clear();
    }
}
