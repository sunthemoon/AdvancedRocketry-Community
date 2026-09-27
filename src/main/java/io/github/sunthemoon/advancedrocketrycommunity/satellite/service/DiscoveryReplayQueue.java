package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Lifecycle-owned, deduplicated replay with a bounded fair batch. */
final class DiscoveryReplayQueue {
    static final int MAX_PER_TICK = 8;
    private final LinkedHashSet<UUID> pending = new LinkedHashSet<>();

    void add(UUID id) {
        Objects.requireNonNull(id, "id");
        if (pending.size() >= SatelliteLimits.MAX_MISSIONS && !pending.contains(id)) {
            throw new IllegalStateException("Discovery replay exceeds the mission-record bound");
        }
        pending.add(id);
    }

    void remove(UUID id) { pending.remove(id); }
    void clear() { pending.clear(); }
    int size() { return pending.size(); }

    int drain(Predicate<UUID> completed) {
        int count = Math.min(MAX_PER_TICK, pending.size());
        for (int index = 0; index < count; index++) {
            var iterator = pending.iterator();
            UUID id = iterator.next();
            iterator.remove();
            boolean done = false;
            try { done = completed.test(id); }
            finally { if (!done) { pending.add(id); } }
        }
        return count;
    }
}
