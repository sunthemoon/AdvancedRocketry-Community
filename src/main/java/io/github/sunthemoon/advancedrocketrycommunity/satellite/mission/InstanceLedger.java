package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * ADR-051 section 2 bookkeeping for asteroid instances: live counts per owner and the expiry queue. An AVAILABLE
 * instance expires at its {@code expires_at}; a DEPLETED or EXPIRED one is removed 1,200 ticks after it. A pass
 * makes at most 16 changes and 32 inspections (ADR-050 section 5).
 */
final class InstanceLedger {
    static final long REMOVE_AFTER_TICKS = 1_200L;
    static final int MAX_CHANGES_PER_PASS = 16;
    static final int MAX_INSPECTIONS_PER_PASS = 32;

    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::dueAt)
            .thenComparingLong(entry -> entry.instanceId().getMostSignificantBits())
            .thenComparingLong(entry -> entry.instanceId().getLeastSignificantBits());

    private static final Comparator<UUID> ID_ORDER = Comparator.comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final Map<UUID, Integer> liveByOwner = new HashMap<>();
    /** Every record of an owner, whatever its state; small (live instances are limited per owner). */
    private final Map<UUID, Set<UUID>> byOwner = new HashMap<>();
    private final PriorityQueue<Entry> queue = new PriorityQueue<>(ORDER);

    void changed(AsteroidInstance previous, AsteroidInstance next) {
        if (previous != null && previous.state().live()) {
            liveByOwner.merge(previous.ownerId(), -1, (a, b) -> a + b == 0 ? null : a + b);
        }
        if (previous != null && next == null) {
            byOwner.computeIfPresent(previous.ownerId(), (owner, ids) -> {
                ids.remove(previous.instanceId());
                return ids.isEmpty() ? null : ids;
            });
        }
        if (next != null) {
            byOwner.computeIfAbsent(next.ownerId(), owner -> new HashSet<>()).add(next.instanceId());
            if (next.state().live()) {
                liveByOwner.merge(next.ownerId(), 1, Integer::sum);
            }
            long due = dueAt(next);
            if (due != Long.MAX_VALUE) {
                queue.add(new Entry(due, next.instanceId()));
            }
        }
    }

    int live(UUID owner) {
        return liveByOwner.getOrDefault(owner, 0);
    }

    /** The owner's instance IDs in ID order. */
    List<UUID> owned(UUID owner) {
        return byOwner.getOrDefault(owner, Set.of()).stream().sorted(ID_ORDER).toList();
    }

    /**
     * One bounded pass. {@code lookup} returns the current record; {@code expire} stores the EXPIRED record and
     * {@code remove} deletes a spent one. Stale entries (the record changed or is gone) are skipped.
     */
    int pass(long logicalTime, Function<UUID, AsteroidInstance> lookup, Consumer<AsteroidInstance> expire,
             Consumer<AsteroidInstance> remove) {
        int changes = 0;
        int inspections = 0;
        while (changes < MAX_CHANGES_PER_PASS && inspections < MAX_INSPECTIONS_PER_PASS && !queue.isEmpty()) {
            Entry head = queue.peek();
            inspections++;
            if (head.dueAt() > logicalTime) {
                break;
            }
            queue.remove();
            AsteroidInstance instance = lookup.apply(head.instanceId());
            if (instance == null || dueAt(instance) != head.dueAt()) {
                continue;
            }
            if (instance.state() == InstanceState.AVAILABLE) {
                expire.accept(expired(instance));
            } else {
                remove.accept(instance);
            }
            changes++;
        }
        return changes;
    }

    static AsteroidInstance expired(AsteroidInstance instance) {
        return new AsteroidInstance(instance.schemaVersion(), instance.instanceId(), instance.ownerId(), instance.system(),
                instance.asteroidType(), instance.tableVersion(), instance.candidateFingerprint(), instance.seed(),
                instance.yield(), instance.createdAt(), instance.expiresAt(), InstanceState.EXPIRED,
                instance.sourceMission(), java.util.Optional.empty());
    }

    /** When the queue next acts on an instance, or {@code Long.MAX_VALUE} while it waits for a mission. */
    static long dueAt(AsteroidInstance instance) {
        return switch (instance.state()) {
            case AVAILABLE -> instance.expiresAt().orElseThrow();
            case DEPLETED, EXPIRED -> instance.expiresAt().isPresent()
                    ? Math.addExact(instance.expiresAt().getAsLong(), REMOVE_AFTER_TICKS) : Long.MAX_VALUE;
            case PENDING, ALLOCATED, QUARANTINED -> Long.MAX_VALUE;
        };
    }

    private record Entry(long dueAt, UUID instanceId) {
    }
}
