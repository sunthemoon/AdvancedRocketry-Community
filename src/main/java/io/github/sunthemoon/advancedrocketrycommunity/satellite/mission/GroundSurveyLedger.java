package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Bounded derived indexes and one retention entry per job, never persisted. */
final class GroundSurveyLedger {
    static final Comparator<UUID> IDS = Comparator.comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);
    record Entry(long dueAt, UUID id) { }
    final TreeSet<Entry> queue = new TreeSet<>(Comparator.comparingLong(Entry::dueAt)
            .thenComparing(Entry::id, IDS));
    private final Map<UUID, Entry> queued = new HashMap<>();
    private final Map<UUID, Set<UUID>> owners = new HashMap<>();
    private final Map<UUID, Set<UUID>> devices = new HashMap<>();
    private final Map<UUID, Set<UUID>> processors = new HashMap<>();
    private final Map<UUID, Set<UUID>> instances = new HashMap<>();

    void changed(GroundSurveyJob before, GroundSurveyJob after, boolean instanceAbsent) {
        if (before != null) {
            index(before, false);
            unqueue(before.jobId());
        }
        if (after != null) {
            index(after, true);
            if (instanceAbsent && (after.phase() == GroundSurveyJob.Phase.CANCELLED
                    || after.phase() == GroundSurveyJob.Phase.PROCESSED)) {
                long resolved = after.resolvedAt().orElseThrow();
                if (resolved <= Long.MAX_VALUE - 1_200) {
                    Entry entry = new Entry(resolved + 1_200, after.jobId());
                    queued.put(after.jobId(), entry);
                    queue.add(entry);
                }
            }
        }
    }

    int owned(UUID owner) { return owners.getOrDefault(owner, Set.of()).size(); }
    Set<UUID> ownedIds(UUID owner) { return Set.copyOf(owners.getOrDefault(owner, Set.of())); }
    Set<UUID> forInstance(UUID instance) { return Set.copyOf(instances.getOrDefault(instance, Set.of())); }
    boolean deviceBusy(UUID device, UUID except) { return busy(devices, device, except); }
    boolean processorBusy(UUID processor, UUID except) { return busy(processors, processor, except); }

    private static boolean busy(Map<UUID, Set<UUID>> index, UUID key, UUID except) {
        return index.getOrDefault(key, Set.of()).stream().anyMatch(id -> !id.equals(except));
    }

    private void index(GroundSurveyJob job, boolean add) {
        change(owners, job.ownerId(), job.jobId(), add);
        change(instances, job.instanceId(), job.jobId(), add);
        if (job.effectivePhase() == GroundSurveyJob.Phase.ACTIVE) {
            change(devices, job.source().id(), job.jobId(), add);
        }
        if (job.analysis().state() == GroundSurveyJob.AnalysisState.RUNNING) {
            change(processors, job.analysis().claim().orElseThrow().processor().id(), job.jobId(), add);
        }
    }

    private static void change(Map<UUID, Set<UUID>> index, UUID key, UUID id, boolean add) {
        if (add) {
            index.computeIfAbsent(key, ignored -> new HashSet<>()).add(id);
        } else {
            index.computeIfPresent(key, (ignored, ids) -> {
                ids.remove(id);
                return ids.isEmpty() ? null : ids;
            });
        }
    }

    void unqueue(UUID id) {
        Entry entry = queued.remove(id);
        if (entry != null) { queue.remove(entry); }
    }
}
