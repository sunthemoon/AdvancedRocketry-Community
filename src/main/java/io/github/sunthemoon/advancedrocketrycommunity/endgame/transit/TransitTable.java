package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The {@code transits} section of the endgame root (ADR-054 sections 10 and 11): at most 256 records, live ones and
 * stubs, in {@link TransitKey} order so encoding is deterministic. It only stores; the ledger decides every transition
 * with {@link TransitRules}. A mutation marks the root changed through the callback the root gives it.
 */
public final class TransitTable {
    private final Map<TransitKey, TransitRecord> records = new TreeMap<>(TransitKey.ORDER);
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
        for (TransitRecord record : records.values()) {
            if (record.names(id)) {
                return true;
            }
        }
        return false;
    }

    /** Records whose destination or paid endpoint is this endpoint, in key order. */
    public List<TransitRecord> forEndpoint(UUID id) {
        List<TransitRecord> found = new ArrayList<>();
        for (TransitRecord record : records.values()) {
            if (record.destination().equals(id) || id.equals(record.paidEndpoint())) {
                found.add(record);
            }
        }
        return found;
    }

    /** Records registered by one source, in seq order. */
    public List<TransitRecord> fromSource(UUID source) {
        List<TransitRecord> found = new ArrayList<>();
        for (TransitRecord record : records.values()) {
            if (record.key().source().equals(source)) {
                found.add(record);
            }
        }
        return found;
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
        changed.run();
    }

    /** A transition of an existing record. */
    public void replace(TransitRecord record) {
        if (records.replace(record.key(), record) == null) {
            throw new IllegalStateException("No record to replace: " + record.key());
        }
        changed.run();
    }

    public boolean remove(TransitKey key) {
        if (records.remove(key) == null) {
            return false;
        }
        changed.run();
        return true;
    }

    /** Codec only: a decoded record; duplicates and the bound are checked by the root. */
    public void restore(TransitRecord record) {
        if (records.putIfAbsent(record.key(), record) != null) {
            throw new IllegalArgumentException("A transfer appears twice in the root: " + record.key());
        }
        if (records.size() > EndgameLimits.MAX_TRANSIT_RECORDS) {
            throw new IllegalArgumentException("The transit section exceeds its fixed bound");
        }
    }
}
