package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-050 section 7 byte budgets per registry section. Each admitted record reserves the largest size it can
 * reach in its lifecycle; the reservation is released only when the record is removed. Restored records are
 * always reserved, so a root above its budget loads and admits nothing until retention frees space.
 */
final class StorageBudget {
    enum Section {
        SATELLITES(4L * 1024L * 1024L),
        MISSIONS(6L * 1024L * 1024L),
        INSTANCES(2L * 1024L * 1024L),
        ACCOUNTS(1024L * 1024L);

        final long bytes;

        Section(long bytes) {
            this.bytes = bytes;
        }
    }

    private final Map<Section, Map<UUID, Integer>> reservations = new EnumMap<>(Section.class);
    private final Map<Section, Long> reserved = new EnumMap<>(Section.class);

    StorageBudget() {
        for (Section section : Section.values()) {
            reservations.put(section, new HashMap<>());
            reserved.put(section, 0L);
        }
    }

    /** Whether admitting a record of this lifecycle size keeps the section within its budget. */
    boolean fits(Section section, int bytes) {
        return reserved.get(section) + bytes <= section.bytes;
    }

    void reserve(Section section, UUID id, int bytes) {
        Objects.requireNonNull(id, "id");
        if (bytes < 0) {
            throw new IllegalArgumentException("A reservation cannot be negative");
        }
        Integer previous = reservations.get(section).put(id, bytes);
        reserved.merge(section, (long) bytes - (previous == null ? 0 : previous), Long::sum);
    }

    void release(Section section, UUID id) {
        Integer previous = reservations.get(section).remove(id);
        if (previous != null) {
            reserved.merge(section, (long) -previous, Long::sum);
        }
    }

    boolean reservedFor(Section section, UUID id) {
        return reservations.get(section).containsKey(id);
    }

    long reserved(Section section) {
        return reserved.get(section);
    }
}
