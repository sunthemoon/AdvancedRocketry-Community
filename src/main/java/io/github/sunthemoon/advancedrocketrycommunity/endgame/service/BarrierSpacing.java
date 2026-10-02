package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * ADR-054 section 7 "Barrier spacing", runtime state on the server thread (review C12R-M3). Player-triggered barrier
 * flushes that grow or change state (elevator binds, owner redirects, owner resolves) share one server-wide spacing of
 * 20 ticks, and those that concern a station (binds, elevator redirects) a per-station cooldown of 100 ticks; a
 * request inside either is refused before anything changes. An unbind is never refused, but counts toward the spacing
 * of the next request. Every other barrier flush (unbinds, removal settlements, operator actions) is exempt and
 * counted for {@code /arce endgame status}.
 */
public final class BarrierSpacing {
    private final Map<UUID, Long> stationLast = new HashMap<>();
    private long lastPlayerBarrier = Long.MIN_VALUE / 2;
    private long exemptFlushes;

    /**
     * Admits a spaced request and records it, or refuses it ({@code false}) without recording anything: inside 20
     * ticks of the last spaced request or unbind, or inside 100 ticks of the last request for {@code station}.
     */
    public boolean admit(@Nullable UUID station, long now) {
        if (now - lastPlayerBarrier < EndgameLimits.BARRIER_SPACING_TICKS) {
            return false;
        }
        stationLast.values().removeIf(at -> now - at >= EndgameLimits.BARRIER_STATION_COOLDOWN_TICKS);
        if (station != null && stationLast.containsKey(station)) {
            return false;
        }
        lastPlayerBarrier = now;
        if (station != null) {
            stationLast.put(station, now);
        }
        return true;
    }

    /** An unbind: never refused, it counts toward the spacing of the next spaced request (review R2-L10). */
    public void unbound(long now) {
        lastPlayerBarrier = Math.max(lastPlayerBarrier, now);
    }

    /** A barrier flush that was not a spaced player request. */
    void exemptFlush() {
        exemptFlushes++;
    }

    public long exemptFlushes() {
        return exemptFlushes;
    }

    /** Stations inside their cooldown; at most one per 20 ticks enters it, so at most five. */
    int coolingStations() {
        return stationLast.size();
    }

    void clear() {
        stationLast.clear();
        lastPlayerBarrier = Long.MIN_VALUE / 2;
        exemptFlushes = 0;
    }
}
