package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One test-owned, bounded observation history; no Minecraft objects, saved tags or global tracker. */
final class ChunkSaveWatchState {
    static final int MAX_WATCHED_CHUNKS = 2;
    // Both callers finish within 900 ticks. Duplicate saves in one tick do not change their existential queries.
    static final int MAX_SAVED_TICKS_PER_CHUNK = 2048;

    private final Runnable detach;
    private final Map<Long, Set<Long>> saves = new LinkedHashMap<>();
    private boolean closed;
    private IllegalStateException overflow;

    ChunkSaveWatchState(Runnable detach, long... chunks) {
        this.detach = Objects.requireNonNull(detach, "detach");
        if (chunks.length == 0 || chunks.length > MAX_WATCHED_CHUNKS) {
            throw new IllegalArgumentException("The fixture must watch one or two chunks");
        }
        for (long chunk : chunks) {
            saves.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>());
        }
    }

    synchronized boolean isClosed() { return closed; }

    synchronized void record(long chunk, long tick) {
        if (closed) { return; }
        Set<Long> ticks = saves.get(chunk);
        if (ticks == null || ticks.contains(tick)) { return; }
        if (ticks.size() == MAX_SAVED_TICKS_PER_CHUNK) {
            overflow = new IllegalStateException("Save watcher history exceeded " + MAX_SAVED_TICKS_PER_CHUNK
                    + " distinct ticks for chunk " + chunk);
            // Do not throw from a save callback or silently answer from an incomplete history.
            close(overflow);
            return;
        }
        ticks.add(tick);
    }

    synchronized boolean between(long chunk, long from, long to) {
        return ticks(chunk).stream().anyMatch(tick -> tick >= from && tick <= to);
    }

    synchronized boolean any(long chunk) { return !ticks(chunk).isEmpty(); }

    private Set<Long> ticks(long chunk) {
        if (overflow != null) { throw overflow; }
        Set<Long> ticks = saves.get(chunk);
        if (ticks == null) { throw new IllegalArgumentException("The fixture does not watch chunk " + chunk); }
        return ticks;
    }

    synchronized void stop() {
        if (overflow != null) {
            close(overflow);
            throw overflow;
        }
        close(null);
    }

    synchronized void close(Throwable primary) {
        if (closed) { return; }
        closed = true; // An already-dispatched save cannot add observations after terminal cleanup.
        try {
            detach.run();
        } catch (RuntimeException | Error failure) {
            if (primary == null) { throw failure; }
            if (primary != failure) { primary.addSuppressed(failure); }
        }
    }

    @Override public synchronized String toString() {
        return saves + (overflow == null ? "" : " (history limit exceeded)");
    }
}
