package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * ADR-051 section 5: what {@code ChunkDataEvent.Load} saw on disk for each terminal, until the loaded terminal
 * consumes it. The event may run off the main thread, so access is synchronized. Bounded: the oldest entry is
 * dropped first, which only delays persistence until the next chunk save. Cleared when the server stops.
 */
public final class TerminalObservations {
    public static final int MAX_ENTRIES = 4_096;

    public record Observation(BlockPos pos, Set<UUID> receipts) {
        public Observation {
            pos = pos.immutable();
            receipts = Set.copyOf(receipts);
        }
    }

    private final Map<UUID, Observation> entries = new LinkedHashMap<>();

    public synchronized void record(UUID terminal, BlockPos pos, Set<UUID> receipts) {
        entries.remove(terminal);
        entries.put(terminal, new Observation(pos, receipts));
        Iterator<UUID> oldest = entries.keySet().iterator();
        while (entries.size() > MAX_ENTRIES) {
            oldest.next();
            oldest.remove();
        }
    }

    /** The observation for this terminal at this position, removed once read. */
    public synchronized Optional<Observation> consume(UUID terminal, BlockPos pos) {
        Observation observation = entries.get(terminal);
        if (observation == null || !observation.pos().equals(pos)) {
            return Optional.empty();
        }
        entries.remove(terminal);
        return Optional.of(observation);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
