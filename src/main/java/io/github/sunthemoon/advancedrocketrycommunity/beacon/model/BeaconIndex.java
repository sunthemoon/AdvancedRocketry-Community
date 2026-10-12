package io.github.sunthemoon.advancedrocketrycommunity.beacon.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * The bounded in-memory beacon index of ADR-065 section 7. Every admission keeps at most
 * {@link BeaconLimits#MAX_PER_OWNER} records per owner, {@link BeaconLimits#MAX_PER_BODY} per body,
 * {@link BeaconLimits#MAX_TOTAL} in all and an encoded root of at most {@link BeaconLimits#MAX_ROOT_BYTES}. Records are
 * also indexed by owner, so a finder lookup reads only the viewer's own records. A refused admission changes nothing.
 */
public final class BeaconIndex {
    /** The outcome of one admission. */
    public enum Admission {
        ADMITTED, UNCHANGED, OWNER_FULL, BODY_FULL, INDEX_FULL, BYTES_FULL, OWNER_MISMATCH;

        public boolean stored() {
            return this == ADMITTED || this == UNCHANGED;
        }
    }

    private final Map<UUID, BeaconRecord> records = new HashMap<>();
    private final Map<UUID, Set<UUID>> byOwner = new HashMap<>();
    private final Map<String, Integer> byBody = new HashMap<>();
    private final ToLongFunction<BeaconRecord> recordBytes;
    private final long rootOverhead;
    private long bytes;

    /**
     * @param recordBytes  the encoded size of one record in the root
     * @param rootOverhead the encoded size of an empty root
     */
    public BeaconIndex(ToLongFunction<BeaconRecord> recordBytes, long rootOverhead) {
        this.recordBytes = Objects.requireNonNull(recordBytes, "recordBytes");
        if (rootOverhead < 0 || rootOverhead > BeaconLimits.MAX_ROOT_BYTES) {
            throw new IllegalArgumentException("An empty root fits the root bound");
        }
        this.rootOverhead = rootOverhead;
        this.bytes = rootOverhead;
    }

    /** Inserts or replaces the record with the same beacon UUID, unless a bound or its owner forbids it. */
    public Admission put(BeaconRecord record) {
        Objects.requireNonNull(record, "record");
        BeaconRecord previous = records.get(record.beaconId());
        long size = recordBytes.applyAsLong(record);
        if (previous != null) {
            if (previous.equals(record)) {
                return Admission.UNCHANGED;
            }
            if (!previous.owner().equals(record.owner())) {
                return Admission.OWNER_MISMATCH;
            }
            if (!previous.body().equals(record.body()) && countForBody(record.body()) >= BeaconLimits.MAX_PER_BODY) {
                return Admission.BODY_FULL;
            }
            long replaced = bytes - recordBytes.applyAsLong(previous) + size;
            if (replaced > BeaconLimits.MAX_ROOT_BYTES) {
                return Admission.BYTES_FULL;
            }
            unlink(previous);
            link(record);
            bytes = replaced;
            return Admission.ADMITTED;
        }
        if (ownedCount(record.owner()) >= BeaconLimits.MAX_PER_OWNER) {
            return Admission.OWNER_FULL;
        }
        if (countForBody(record.body()) >= BeaconLimits.MAX_PER_BODY) {
            return Admission.BODY_FULL;
        }
        if (records.size() >= BeaconLimits.MAX_TOTAL) {
            return Admission.INDEX_FULL;
        }
        if (bytes + size > BeaconLimits.MAX_ROOT_BYTES) {
            return Admission.BYTES_FULL;
        }
        link(record);
        bytes += size;
        return Admission.ADMITTED;
    }

    /**
     * As {@link #put}, except that when a bound refuses a replacement at the same Level and position (a longer name or
     * a full body), the stored record still takes the new activity and generation. That keeps its size, owner and
     * body, so it always fits, and a disabled or unformed beacon is never left recorded as active. Returns the
     * admission of the full record.
     */
    public Admission update(BeaconRecord record) {
        Admission admission = put(record);
        BeaconRecord previous = records.get(record.beaconId());
        if (!admission.stored() && admission != Admission.OWNER_MISMATCH && previous != null
                && previous.at(record.level(), record.x(), record.y(), record.z())) {
            put(previous.withActivity(record.active(), record.generation()));
        }
        return admission;
    }

    public Optional<BeaconRecord> remove(UUID beaconId) {
        BeaconRecord removed = records.get(beaconId);
        if (removed == null) {
            return Optional.empty();
        }
        unlink(removed);
        bytes -= recordBytes.applyAsLong(removed);
        return Optional.of(removed);
    }

    public Optional<BeaconRecord> get(UUID beaconId) {
        return Optional.ofNullable(records.get(beaconId));
    }

    /** The owner's records, at most {@link BeaconLimits#MAX_PER_OWNER}, read without visiting other owners. */
    public List<BeaconRecord> ownedBy(UUID owner) {
        Set<UUID> owned = byOwner.get(owner);
        if (owned == null) {
            return List.of();
        }
        List<BeaconRecord> result = new ArrayList<>(owned.size());
        for (UUID beaconId : owned) {
            result.add(records.get(beaconId));
        }
        return Collections.unmodifiableList(result);
    }

    public int ownedCount(UUID owner) {
        Set<UUID> owned = byOwner.get(owner);
        return owned == null ? 0 : owned.size();
    }

    public int countForBody(String body) {
        return byBody.getOrDefault(body, 0);
    }

    public int size() {
        return records.size();
    }

    /** The encoded root size, including the empty-root overhead. */
    public long encodedBytes() {
        return bytes;
    }

    public long rootOverhead() {
        return rootOverhead;
    }

    /** Every record in canonical order (lowercase UUID string), for a deterministic encoding. */
    public List<BeaconRecord> records() {
        List<BeaconRecord> sorted = new ArrayList<>(records.values());
        sorted.sort(Comparator.comparing(record -> record.beaconId().toString()));
        return Collections.unmodifiableList(sorted);
    }

    public Collection<BeaconRecord> unordered() {
        return Collections.unmodifiableCollection(records.values());
    }

    private void link(BeaconRecord record) {
        records.put(record.beaconId(), record);
        byOwner.computeIfAbsent(record.owner(), ignored -> new LinkedHashSet<>()).add(record.beaconId());
        byBody.merge(record.body(), 1, Integer::sum);
    }

    private void unlink(BeaconRecord record) {
        records.remove(record.beaconId());
        Set<UUID> owned = byOwner.get(record.owner());
        if (owned != null) {
            owned.remove(record.beaconId());
            if (owned.isEmpty()) {
                byOwner.remove(record.owner());
            }
        }
        byBody.computeIfPresent(record.body(), (body, count) -> count == 1 ? null : count - 1);
    }
}
