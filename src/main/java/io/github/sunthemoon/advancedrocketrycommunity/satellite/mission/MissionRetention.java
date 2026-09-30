package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-050 sections 5 and 7: incremental mission counters and the pruning queues. A finished record becomes
 * eligible 1,200 logical ticks after it resolved (a CLAIMED resource record only once its acknowledgement is
 * durable). Pruning runs while more than 1,536 finished records exist, or while an owner holds more than its
 * limit, at most 64 removals and 128 inspections per pass. QUARANTINED records are never pruned automatically,
 * and the newest CLAIMED data mission that needed discovery is kept per target body as discovery evidence.
 */
final class MissionRetention {
    static final long ELIGIBLE_AFTER_TICKS = 1_200L;
    static final int GLOBAL_FINISHED_THRESHOLD = 1_536;
    static final int MAX_REMOVALS_PER_PASS = 64;
    static final int MAX_INSPECTIONS_PER_PASS = 128;

    private static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::eligibleAt)
            .thenComparingLong(entry -> entry.missionId().getMostSignificantBits())
            .thenComparingLong(entry -> entry.missionId().getLeastSignificantBits());

    private int unfinished;
    private int finished;
    private final Map<UUID, Integer> unfinishedByOwner = new HashMap<>();
    private final Map<UUID, Integer> finishedByOwner = new HashMap<>();
    private final PriorityQueue<Entry> global = new PriorityQueue<>(ORDER);
    private final Map<UUID, PriorityQueue<Entry>> byOwner = new HashMap<>();
    private final Set<UUID> queued = new HashSet<>();
    private final Map<ResourceLocation, MissionState> evidence = new HashMap<>();

    /** Keeps counters, queues and evidence in step with one mission change; either side may be null. */
    void changed(MissionState previous, MissionState next, long saveEpoch) {
        if (previous != null) {
            count(previous, -1);
        }
        if (next != null) {
            count(next, 1);
            if (isEvidence(next)) {
                MissionState current = evidence.get(next.targetBodyId());
                if (current == null || newer(next, current)) {
                    evidence.put(next.targetBodyId(), next);
                    if (current != null && !current.missionId().equals(next.missionId())) {
                        enqueue(current, saveEpoch);
                    }
                }
            }
            enqueue(next, saveEpoch);
        } else if (previous != null && isEvidence(previous)
                && evidence.get(previous.targetBodyId()) != null
                && evidence.get(previous.targetBodyId()).missionId().equals(previous.missionId())) {
            evidence.remove(previous.targetBodyId());
        }
    }

    int unfinished() {
        return unfinished;
    }

    int unfinished(UUID owner) {
        return unfinishedByOwner.getOrDefault(owner, 0);
    }

    int finished() {
        return finished;
    }

    int finished(UUID owner) {
        return finishedByOwner.getOrDefault(owner, 0);
    }

    boolean exempt(MissionState mission) {
        MissionState current = isEvidence(mission) ? evidence.get(mission.targetBodyId()) : null;
        return current != null && current.missionId().equals(mission.missionId());
    }

    /**
     * One bounded pruning pass. {@code lookup} returns the current record; {@code remove} deletes it. Returns the
     * number of removed records.
     */
    int prune(long logicalTime, int finishedPerOwner, long saveEpoch, Function<UUID, MissionState> lookup,
              java.util.function.Consumer<MissionState> remove) {
        int removed = 0;
        int inspected = 0;
        for (UUID owner : Set.copyOf(finishedByOwner.keySet())) {
            PriorityQueue<Entry> queue = byOwner.get(owner);
            while (queue != null && finished(owner) > finishedPerOwner && removed < MAX_REMOVALS_PER_PASS
                    && inspected < MAX_INSPECTIONS_PER_PASS && !queue.isEmpty()) {
                Entry head = queue.peek();
                inspected++;
                if (head.eligibleAt() > logicalTime) {
                    break;
                }
                queue.remove();
                if (tryRemove(head, lookup, remove, saveEpoch, logicalTime)) {
                    removed++;
                }
            }
        }
        while (finished > GLOBAL_FINISHED_THRESHOLD && removed < MAX_REMOVALS_PER_PASS
                && inspected < MAX_INSPECTIONS_PER_PASS && !global.isEmpty()) {
            Entry head = global.peek();
            inspected++;
            if (head.eligibleAt() > logicalTime) {
                break;
            }
            global.remove();
            if (tryRemove(head, lookup, remove, saveEpoch, logicalTime)) {
                removed++;
            }
        }
        return removed;
    }

    private boolean tryRemove(Entry entry, Function<UUID, MissionState> lookup,
                              java.util.function.Consumer<MissionState> remove, long saveEpoch, long logicalTime) {
        MissionState mission = lookup.apply(entry.missionId());
        if (mission == null || !queued.contains(entry.missionId())) {
            return false;
        }
        long eligibleAt = eligibleAt(mission, saveEpoch);
        if (eligibleAt == Long.MAX_VALUE || exempt(mission)) {
            queued.remove(mission.missionId());
            return false;
        }
        if (eligibleAt > logicalTime) {
            return false;
        }
        queued.remove(mission.missionId());
        remove.accept(mission);
        return true;
    }

    /** Re-offers records whose eligibility depends on the save epoch (acknowledged resource claims). */
    void epochAdvanced(Iterable<MissionState> waiting, long saveEpoch) {
        for (MissionState mission : waiting) {
            enqueue(mission, saveEpoch);
        }
    }

    private void enqueue(MissionState mission, long saveEpoch) {
        long eligibleAt = eligibleAt(mission, saveEpoch);
        if (eligibleAt == Long.MAX_VALUE || exempt(mission) || !queued.add(mission.missionId())) {
            return;
        }
        Entry entry = new Entry(eligibleAt, mission.missionId());
        global.add(entry);
        byOwner.computeIfAbsent(mission.ownerId(), owner -> new PriorityQueue<>(ORDER)).add(entry);
    }

    /** The logical time a finished record may be pruned, or {@code Long.MAX_VALUE} while it may not. */
    static long eligibleAt(MissionState mission, long saveEpoch) {
        if (mission.status() != MissionStatus.CLAIMED && mission.status() != MissionStatus.CANCELLED) {
            return Long.MAX_VALUE;
        }
        if (mission.status() == MissionStatus.CLAIMED && mission.payload() instanceof MissionPayload.Resource resource
                && !(resource.acknowledged() && resource.ackEpoch().orElseThrow() < saveEpoch)) {
            return Long.MAX_VALUE;
        }
        return Math.addExact(mission.resolvedAtLogicalTime().orElseThrow(), ELIGIBLE_AFTER_TICKS);
    }

    private void count(MissionState mission, int delta) {
        if (mission.status().unfinished()) {
            unfinished += delta;
            unfinishedByOwner.merge(mission.ownerId(), delta, (a, b) -> a + b == 0 ? null : a + b);
        } else {
            finished += delta;
            finishedByOwner.merge(mission.ownerId(), delta, (a, b) -> a + b == 0 ? null : a + b);
        }
    }

    private static boolean isEvidence(MissionState mission) {
        return mission.kind() == MissionKind.DATA && mission.status() == MissionStatus.CLAIMED
                && mission.discoveryRequired();
    }

    private static boolean newer(MissionState candidate, MissionState current) {
        long left = candidate.resolvedAtLogicalTime().orElse(0L);
        long right = current.resolvedAtLogicalTime().orElse(0L);
        return left > right || left == right && ORDER.compare(new Entry(0L, candidate.missionId()),
                new Entry(0L, current.missionId())) > 0;
    }

    private record Entry(long eligibleAt, UUID missionId) {
        private Entry {
            Objects.requireNonNull(missionId, "missionId");
        }
    }
}
