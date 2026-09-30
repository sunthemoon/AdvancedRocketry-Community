package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-041 rate bound for repeatable checked station writes: at most one per station per
 * cooldown window. Bounded by the station limit; cleared at server stop.
 */
public final class StationWriteCooldown {
    private final long cooldownTicks;
    private final Map<UUID, Long> lastWrite = new LinkedHashMap<>();

    public StationWriteCooldown(long cooldownTicks) {
        if (cooldownTicks <= 0L) {
            throw new IllegalArgumentException("Cooldown must be positive");
        }
        this.cooldownTicks = cooldownTicks;
    }

    /** True when a write may start now; does not record it. */
    public boolean ready(UUID stationId, long nowTick) {
        Long previous = lastWrite.get(Objects.requireNonNull(stationId, "stationId"));
        return previous == null || nowTick < previous || nowTick - previous >= cooldownTicks;
    }

    /** Records a committed write; entries older than the window are dropped in insertion order. */
    public void record(UUID stationId, long nowTick) {
        Objects.requireNonNull(stationId, "stationId");
        lastWrite.remove(stationId);
        lastWrite.put(stationId, nowTick);
        Iterator<Long> entries = lastWrite.values().iterator();
        while (entries.hasNext()) {
            long time = entries.next();
            if (lastWrite.size() <= StationLimits.MAX_STATIONS && nowTick - time < cooldownTicks) {
                break;
            }
            if (lastWrite.size() == 1) {
                break;
            }
            entries.remove();
        }
    }

    public int size() {
        return lastWrite.size();
    }

    public void clear() {
        lastWrite.clear();
    }
}
