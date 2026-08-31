package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded replay guard. Authoritative world checks still run after process restart. */
public final class RocketOperationLedger {
    public static final int DEFAULT_CAPACITY = 4_096;

    public enum BeginResult {
        STARTED,
        REPLAYED,
        FULL
    }

    public enum Outcome {
        ACTIVE,
        SUCCEEDED,
        FAILED
    }

    private final int capacity;
    private final LinkedHashMap<UUID, Outcome> entries = new LinkedHashMap<>();

    public RocketOperationLedger() {
        this(DEFAULT_CAPACITY);
    }

    public RocketOperationLedger(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Ledger capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized BeginResult begin(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        if (entries.containsKey(operationId)) {
            return BeginResult.REPLAYED;
        }
        evictCompletedUntilSpace();
        if (entries.size() >= capacity) {
            return BeginResult.FULL;
        }
        entries.put(operationId, Outcome.ACTIVE);
        return BeginResult.STARTED;
    }

    public synchronized void finish(UUID operationId, boolean success) {
        if (entries.get(operationId) == Outcome.ACTIVE) {
            entries.put(operationId, success ? Outcome.SUCCEEDED : Outcome.FAILED);
        }
    }

    public synchronized Outcome outcome(UUID operationId) {
        return entries.get(operationId);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    private void evictCompletedUntilSpace() {
        Iterator<Map.Entry<UUID, Outcome>> iterator = entries.entrySet().iterator();
        while (entries.size() >= capacity && iterator.hasNext()) {
            if (iterator.next().getValue() != Outcome.ACTIVE) {
                iterator.remove();
            }
        }
    }
}
