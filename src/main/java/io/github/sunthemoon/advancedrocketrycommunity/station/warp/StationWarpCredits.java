package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-044 §2 pending credits: energy accepted by warp cores on the server thread, held in memory
 * until the next fold. Bounded per station by the balance cap and a per-tick allowance, and in size
 * by the station limit. Not persisted: energy accepted but not yet folded is lost on a crash.
 */
public final class StationWarpCredits {
    private final Map<UUID, Integer> pending = new LinkedHashMap<>();
    private final Map<UUID, Integer> acceptedThisTick = new LinkedHashMap<>();
    private long tick = Long.MIN_VALUE;

    /**
     * Accepts up to {@code maxReceive} for a station whose folded balance is {@code balance}.
     * With {@code simulate} nothing changes and the allowance is not used.
     */
    public int offer(UUID stationId, int maxReceive, int balance, long nowTick, boolean simulate) {
        Objects.requireNonNull(stationId, "stationId");
        if (maxReceive <= 0 || balance < 0) {
            return 0;
        }
        if (nowTick != tick) {
            acceptedThisTick.clear();
            tick = nowTick;
        }
        Integer waiting = pending.get(stationId);
        if (waiting == null && pending.size() >= StationLimits.MAX_PENDING_WARP_CREDITS) {
            return 0;
        }
        long room = (long) StationLimits.MAX_WARP_ENERGY - balance - (waiting == null ? 0 : waiting);
        long allowance = StationLimits.WARP_CREDIT_PER_TICK - acceptedThisTick.getOrDefault(stationId, 0);
        int accepted = (int) Math.max(0L, Math.min(maxReceive, Math.min(room, allowance)));
        if (accepted > 0 && !simulate) {
            pending.merge(stationId, accepted, Integer::sum);
            acceptedThisTick.merge(stationId, accepted, Integer::sum);
        }
        return accepted;
    }

    public int pending(UUID stationId) {
        return pending.getOrDefault(Objects.requireNonNull(stationId, "stationId"), 0);
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /** Removes and returns every pending credit, for one fold. */
    public Map<UUID, Integer> drain() {
        Map<UUID, Integer> drained = new LinkedHashMap<>(pending);
        pending.clear();
        return drained;
    }

    public int size() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
        acceptedThisTick.clear();
        tick = Long.MIN_VALUE;
    }
}
