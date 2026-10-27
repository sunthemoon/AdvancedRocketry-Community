package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Lifecycle-owned, deduplicated replay with a bounded fair batch. Each mission keeps the token of its paid receipt
 * from when it was first queued with one (ADR-074 item 6); a later add never replaces it with a newer sequence.
 */
final class DiscoveryReplayQueue {
    static final int MAX_PER_TICK = 8;
    private final LinkedHashSet<UUID> pending = new LinkedHashSet<>();
    private final Map<UUID, SatelliteRegistryProof.Token> receipts = new HashMap<>();

    void add(UUID id) {
        Objects.requireNonNull(id, "id");
        if (pending.size() >= SatelliteLimits.MAX_MISSIONS && !pending.contains(id)) {
            throw new IllegalStateException("Discovery replay exceeds the mission-record bound");
        }
        pending.add(id);
    }

    /** Queues the mission with the token of its paid receipt, keeping an earlier token if one exists. */
    void add(UUID id, SatelliteRegistryProof.Token receipt) {
        Objects.requireNonNull(receipt, "receipt");
        add(id);
        receipts.putIfAbsent(id, receipt);
    }

    /** The receipt's token; a mission queued without one is given {@code fresh} once, without re-queueing it. */
    SatelliteRegistryProof.Token receipt(UUID id, java.util.function.Supplier<SatelliteRegistryProof.Token> fresh) {
        return receipts.computeIfAbsent(id, ignored -> fresh.get());
    }
    void remove(UUID id) { pending.remove(id); receipts.remove(id); }
    void clear() { pending.clear(); receipts.clear(); }
    int size() { return pending.size(); }

    int drain(Predicate<UUID> completed) {
        int count = Math.min(MAX_PER_TICK, pending.size());
        for (int index = 0; index < count; index++) {
            var iterator = pending.iterator();
            UUID id = iterator.next();
            iterator.remove();
            boolean done = false;
            try { done = completed.test(id); }
            finally {
                if (!done) { pending.add(id); } else { receipts.remove(id); }
            }
        }
        return count;
    }
}
