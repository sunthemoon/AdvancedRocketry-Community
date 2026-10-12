package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import java.util.HashMap;
import java.util.Map;

/** Shared cell accounting includes redstone neighbours and lifecycle/cleanup observations. */
public final class FieldWorkBudget {
    public static final int MAX_CELLS = 64;
    private final Map<java.util.UUID, Long> last = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private int cells;
    public boolean acquire(FieldIdentity.Key key, long now, int cost) {
        if (cost < 1 || cost > MAX_CELLS) { throw new IllegalArgumentException("Invalid cell cost"); }
        if (tick != now) { tick = now; cells = 0; }
        if (last.size() >= FieldCleanupRoot.MAX_ENTRIES * 2) {
            // Only the five-tick cooldown is authoritative. Old observations must not fill it forever.
            last.entrySet().removeIf(entry -> now - entry.getValue() >= 5);
        }
        Long previous = last.get(key.projector());
        if (cells + cost > MAX_CELLS || (previous != null && now - previous < 5)
                || (previous == null && last.size() >= FieldCleanupRoot.MAX_ENTRIES * 2)) { return false; }
        cells += cost;
        last.put(key.projector(), now);
        return true;
    }
    public int cells() { return cells; }
    void refund(int unused) {
        if (unused < 0 || unused > cells) { throw new IllegalArgumentException("Invalid unused cell reservation"); }
        cells -= unused;
    }
    public boolean hasCapacity(int cost, long now) { return (tick == now ? cells : 0) + cost <= MAX_CELLS; }
    public java.util.OptionalLong lastVisit(FieldIdentity.Key key) {
        Long value = last.get(key.projector());
        return value == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(value);
    }
    public void forget(FieldIdentity.Key key) { last.remove(key.projector()); }
}
