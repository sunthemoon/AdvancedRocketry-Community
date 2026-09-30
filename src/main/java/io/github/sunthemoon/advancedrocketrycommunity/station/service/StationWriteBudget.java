package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Final v1.5 review B9: a server-wide spacing between checked station writes. Each checked write
 * (expansion, gravity, warp commit) re-encodes, writes, forces and re-reads the whole registry on the
 * server thread, about 176 ms at 4,096 stations. Per-station cooldowns alone let many stations write
 * in quick succession, so after any checked write the next one waits
 * {@code floor(records * ticksPer100Stations / 100)} ticks, whichever station or action asks. With the
 * default of 3, a 4,096-station registry allows one such write about every 6 seconds (under 1 % of
 * ticks); registries under 34 stations are not spaced. 0 disables the bound.
 */
public final class StationWriteBudget {
    private final IntSupplier ticksPer100Stations;
    private long lastWriteTick;
    private boolean written;

    public StationWriteBudget(IntSupplier ticksPer100Stations) {
        this.ticksPer100Stations = Objects.requireNonNull(ticksPer100Stations, "ticksPer100Stations");
    }

    /** A budget that never spaces writes (tests and tools that build their own services). */
    public static StationWriteBudget unbounded() {
        return new StationWriteBudget(() -> 0);
    }

    public static long spacingTicks(int records, int ticksPer100Stations) {
        if (records <= 0 || ticksPer100Stations <= 0) {
            return 0L;
        }
        return (long) records * ticksPer100Stations / 100L;
    }

    public boolean ready(long nowTick, int records) {
        return remainingTicks(nowTick, records) == 0L;
    }

    public long remainingTicks(long nowTick, int records) {
        if (!written) {
            return 0L;
        }
        long spacing = spacingTicks(records, ticksPer100Stations.getAsInt());
        return Math.max(0L, lastWriteTick + spacing - nowTick);
    }

    /** Records a checked write attempt (committed or failed), which starts the spacing. */
    public void record(long nowTick) {
        lastWriteTick = nowTick;
        written = true;
    }

    public void clear() {
        written = false;
        lastWriteTick = 0L;
    }
}
