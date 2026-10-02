package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The {@code transits} section of the endgame root (ADR-054 sections 10 and 11): at most 256 records, live ones and
 * stubs, in {@link TransitKey} order so encoding is deterministic. It only stores; the ledger decides every transition
 * with {@link TransitRules}. A mutation marks the root changed through the callback the root gives it.
 *
 * <p>Indexes kept with every mutation (C13 performance): the records naming each endpoint (as destination or paid
 * endpoint) and each source, the records in transit by arrival tick, and the acknowledged records still carrying their
 * payload. The ledger's per-tick passes read these instead of every record.
 */
public final class TransitTable {
    private static final Comparator<TransitRecord> BY_ARRIVAL = Comparator.comparingLong(TransitRecord::arriveAt)
            .thenComparing(TransitRecord::key, TransitKey.ORDER);

    private final Map<TransitKey, TransitRecord> records = new TreeMap<>(TransitKey.ORDER);
    private final Map<UUID, TreeSet<TransitKey>> byEndpoint = new HashMap<>();
    private final Map<UUID, TreeSet<TransitKey>> bySource = new HashMap<>();
    private final TreeSet<TransitRecord> inTransit = new TreeSet<>(BY_ARRIVAL);
    private final TreeSet<TransitKey> acknowledgedWithPayload = new TreeSet<>(TransitKey.ORDER);
    private final Runnable changed;

    public TransitTable(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    public Optional<TransitRecord> record(TransitKey key) {
        return Optional.ofNullable(records.get(key));
    }

    public Collection<TransitRecord> records() {
        return List.copyOf(records.values());
    }

    public int size() {
        return records.size();
    }

    public int stubs() {
        int count = 0;
        for (TransitRecord record : records.values()) {
            count += record.stub() ? 1 : 0;
        }
        return count;
    }

    /** Live (not stub) records of one owner: the per-owner limit counts these and the owner's outbox entries. */
    public int live(UUID owner) {
        int count = 0;
        for (TransitRecord record : records.values()) {
            count += !record.stub() && record.owner().equals(owner) ? 1 : 0;
        }
        return count;
    }

    /** Whether a record names this ID as source, destination or paid endpoint: its tombstone is pinned. */
    public boolean names(UUID id) {
        return byEndpoint.containsKey(id) || bySource.containsKey(id);
    }

    /** Records whose destination or paid endpoint is this endpoint, in key order. */
    public List<TransitRecord> forEndpoint(UUID id) {
        return resolve(byEndpoint.get(id));
    }

    /** Records registered by one source, in seq order. */
    public List<TransitRecord> fromSource(UUID source) {
        return resolve(bySource.get(source));
    }

    /** At most {@code limit} records in transit whose arrival tick has come, earliest first (then in key order). */
    public List<TransitRecord> due(long now, int limit) {
        List<TransitRecord> found = new ArrayList<>();
        for (TransitRecord record : inTransit) {
            if (record.arriveAt() > now || found.size() >= limit) {
                break;
            }
            found.add(record);
        }
        return found;
    }

    /** Acknowledged records that still carry their payload (not stubs yet), in key order. */
    public List<TransitRecord> acknowledgedWithPayload() {
        return resolve(acknowledgedWithPayload);
    }

    private List<TransitRecord> resolve(Set<TransitKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<TransitRecord> found = new ArrayList<>(keys.size());
        for (TransitKey key : keys) {
            found.add(records.get(key));
        }
        return found;
    }

    private void index(TransitRecord record) {
        TransitKey key = record.key();
        bySource.computeIfAbsent(key.source(), ignored -> new TreeSet<>(TransitKey.ORDER)).add(key);
        byEndpoint.computeIfAbsent(record.destination(), ignored -> new TreeSet<>(TransitKey.ORDER)).add(key);
        if (record.paidEndpoint() != null) {
            byEndpoint.computeIfAbsent(record.paidEndpoint(), ignored -> new TreeSet<>(TransitKey.ORDER)).add(key);
        }
        if (record.state() == TransitRecord.State.IN_TRANSIT) {
            inTransit.add(record);
        }
        if (record.acknowledged() && !record.stub()) {
            acknowledgedWithPayload.add(key);
        }
    }

    private void unindex(TransitRecord record) {
        TransitKey key = record.key();
        drop(bySource, key.source(), key);
        drop(byEndpoint, record.destination(), key);
        if (record.paidEndpoint() != null) {
            drop(byEndpoint, record.paidEndpoint(), key);
        }
        inTransit.remove(record);
        acknowledgedWithPayload.remove(key);
    }

    private static void drop(Map<UUID, TreeSet<TransitKey>> index, UUID id, TransitKey key) {
        TreeSet<TransitKey> keys = index.get(id);
        if (keys != null && keys.remove(key) && keys.isEmpty()) {
            index.remove(id);
        }
    }

    /** Section 10 accounting: every record, live or stub, at its worst case of 2.5 KiB. */
    public long accountedBytes() {
        return (long) records.size() * EndgameLimits.TRANSIT_RECORD_BYTES;
    }

    /** A new record; the ledger has checked the limits and the root admission. */
    public void add(TransitRecord record) {
        if (records.putIfAbsent(record.key(), record) != null) {
            throw new IllegalStateException("A transfer is registered twice: " + record.key());
        }
        index(record);
        changed.run();
    }

    /** A transition of an existing record. */
    public void replace(TransitRecord record) {
        TransitRecord previous = records.replace(record.key(), record);
        if (previous == null) {
            throw new IllegalStateException("No record to replace: " + record.key());
        }
        unindex(previous);
        index(record);
        changed.run();
    }

    public boolean remove(TransitKey key) {
        TransitRecord removed = records.remove(key);
        if (removed == null) {
            return false;
        }
        unindex(removed);
        changed.run();
        return true;
    }

    /** Codec only: a decoded record; duplicates and the bound are checked by the root. */
    public void restore(TransitRecord record) {
        if (records.putIfAbsent(record.key(), record) != null) {
            throw new IllegalArgumentException("A transfer appears twice in the root: " + record.key());
        }
        index(record);
        if (records.size() > EndgameLimits.MAX_TRANSIT_RECORDS) {
            throw new IllegalArgumentException("The transit section exceeds its fixed bound");
        }
    }
}
