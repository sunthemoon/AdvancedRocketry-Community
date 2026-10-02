package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * ADR-054 section 7 budgets, measured rather than assumed: the endgame work of every server tick by place (each
 * system's block-entity tickers, the field lookups in the living-tick hook, the ledger and framework END pass, rides)
 * over the last 1,200 ticks, and every root flush with the ticks it happened in. Forge records vanilla's
 * {@code tickTimes} before END handlers run, so these numbers are the only view of the END work. A barrier flush that
 * a ticker or a command starts is counted in that place and also as a flush; the totals add the places only. Server
 * thread only; runtime state, cleared at server stop.
 */
public final class EndgameTimings {
    public static final int WINDOW = 1200;
    /** ADR-054 section 7: one flush at the reference root takes at most 60 ms. */
    public static final long FLUSH_BUDGET_NANOS = 60_000_000L;

    /** Where endgame work runs. */
    public enum Place {
        LASER_DRILL,
        RAILGUN,
        BLACK_HOLE_GENERATOR,
        GRAVITY_FIELD,
        SPACE_ELEVATOR,
        FIELD_LOOKUPS,
        /** The endpoint index: registrations, chunk observations, absences and tombstone housekeeping. */
        INDEX,
        /** The ledger passes of section 11: arrivals, payload drops, reconciliation. */
        LEDGER,
        RIDES,
        FLUSH
    }

    private final long[] current = new long[Place.values().length];
    private final long[][] window = new long[Place.values().length][WINDOW];
    private final boolean[] flushed = new boolean[WINDOW];
    private boolean flushedNow;
    private int cursor;
    private int filled;
    private long flushes;
    private long lastFlushNanos;
    private long maxFlushNanos;
    private long overBudgetFlushes;

    public void add(Place place, long nanos) {
        current[place.ordinal()] += Math.max(0L, nanos);
    }

    /**
     * A root flush took {@code nanos}; its tick is a flush tick (ADR-054 section 7, review R2-L3). The count, the last,
     * the maximum and the flushes over the reference-root budget cover the whole server run, not only the window
     * (review C13-F2).
     */
    public void flushed(long nanos) {
        long spent = Math.max(0L, nanos);
        add(Place.FLUSH, spent);
        flushedNow = true;
        flushes++;
        lastFlushNanos = spent;
        maxFlushNanos = Math.max(maxFlushNanos, spent);
        if (spent > FLUSH_BUDGET_NANOS) {
            overBudgetFlushes++;
        }
    }

    /** Closes a server tick; runs after every endgame END handler. */
    public void endTick() {
        for (Place place : Place.values()) {
            window[place.ordinal()][cursor] = current[place.ordinal()];
            current[place.ordinal()] = 0L;
        }
        flushed[cursor] = flushedNow;
        flushedNow = false;
        cursor = (cursor + 1) % WINDOW;
        filled = Math.min(WINDOW, filled + 1);
    }

    /** Mean, 99th percentile and maximum over the window, in microseconds. */
    public record Summary(String name, int ticks, double meanMicros, double p99Micros, double maxMicros) {
        public Summary {
            Objects.requireNonNull(name, "name");
        }

        public String line() {
            return String.format(Locale.ROOT, "%s ticks=%d mean_us=%.1f p99_us=%.1f max_us=%.1f", name, ticks,
                    meanMicros, p99Micros, maxMicros);
        }
    }

    public Summary summary(Place place) {
        long[] values = Arrays.copyOf(window[place.ordinal()], filled);
        return summarize(place.name().toLowerCase(Locale.ROOT), values);
    }

    /** All places but the flush, per tick; {@code withoutFlushTicks} leaves out the ticks that flushed the root. */
    public Summary total(boolean withoutFlushTicks) {
        long[] values = new long[filled];
        int count = 0;
        for (int tick = 0; tick < filled; tick++) {
            if (withoutFlushTicks && flushed[tick]) {
                continue;
            }
            long sum = 0L;
            for (Place place : Place.values()) {
                if (place != Place.FLUSH) {
                    sum += window[place.ordinal()][tick];
                }
            }
            values[count++] = sum;
        }
        return summarize(withoutFlushTicks ? "total_without_flush_ticks" : "total", Arrays.copyOf(values, count));
    }

    private static Summary summarize(String name, long[] nanos) {
        if (nanos.length == 0) {
            return new Summary(name, 0, 0.0D, 0.0D, 0.0D);
        }
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double sum = 0.0D;
        for (long value : sorted) {
            sum += value;
        }
        int p99 = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.99D) - 1);
        return new Summary(name, sorted.length, sum / sorted.length / 1000.0D, sorted[Math.max(0, p99)] / 1000.0D,
                sorted[sorted.length - 1] / 1000.0D);
    }

    /** One bounded report for {@code /arce endgame timing}: every place, the totals and the flushes since start. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        for (Place place : Place.values()) {
            lines.add(summary(place).line());
        }
        lines.add(total(false).line());
        lines.add(total(true).line());
        lines.add(String.format(Locale.ROOT, "flushes=%d last_ms=%.2f max_ms=%.2f over_60ms=%d", flushes,
                lastFlushNanos / 1e6D, maxFlushNanos / 1e6D, overBudgetFlushes));
        return lines;
    }

    public void clear() {
        for (long[] values : window) {
            Arrays.fill(values, 0L);
        }
        Arrays.fill(current, 0L);
        Arrays.fill(flushed, false);
        flushedNow = false;
        cursor = 0;
        filled = 0;
        flushes = 0L;
        lastFlushNanos = 0L;
        maxFlushNanos = 0L;
        overBudgetFlushes = 0L;
    }
}
