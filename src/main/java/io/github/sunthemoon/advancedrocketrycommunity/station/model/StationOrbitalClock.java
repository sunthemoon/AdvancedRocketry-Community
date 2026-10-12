package io.github.sunthemoon.advancedrocketrycommunity.station.model;

/**
 * ADR-065 section 5.2.1: the restart-safe logical clock of the station orbital controls. It starts at the
 * saved {@code logical_tick} of its station registry root and advances once per logical-server END tick,
 * saturating at {@link Long#MAX_VALUE} ({@link #exhausted()}). It never reads game, day, wall or client
 * time, and it lives only as long as the registry that owns it.
 */
public final class StationOrbitalClock {
    public static final int SCHEMA_VERSION = 1;
    /** The persisted compound: an int, a long and their names, with room to spare. */
    public static final int MAX_NBT_BYTES = 128;

    private long tick;

    public StationOrbitalClock(long savedTick) {
        if (savedTick < 0L) {
            throw new IllegalArgumentException("Station orbital clock cannot be negative");
        }
        this.tick = savedTick;
    }

    public long current() {
        return tick;
    }

    /** One logical tick; at {@link Long#MAX_VALUE} the clock stays there and never wraps. */
    public void advance() {
        if (tick != Long.MAX_VALUE) {
            tick++;
        }
    }

    /** No further tick exists: new nonzero rates are refused, STOP still samples the frozen phases. */
    public boolean exhausted() {
        return tick == Long.MAX_VALUE;
    }
}
