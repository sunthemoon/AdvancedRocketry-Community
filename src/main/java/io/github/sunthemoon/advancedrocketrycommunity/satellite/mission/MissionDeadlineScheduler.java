package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Deadline queue that inspects only bounded due entries rather than every mission. */
public final class MissionDeadlineScheduler {
    private static final Comparator<Entry> ORDER = Comparator
            .comparingLong(Entry::deadline)
            .thenComparingLong(entry -> entry.missionId().getMostSignificantBits())
            .thenComparingLong(entry -> entry.missionId().getLeastSignificantBits());

    private final PriorityQueue<Entry> queue = new PriorityQueue<>(ORDER);

    public void rebuild(Collection<MissionState> missions) {
        Objects.requireNonNull(missions, "missions");
        queue.clear();
        for (MissionState mission : missions) {
            if (mission.status() == MissionStatus.ACTIVE) {
                schedule(mission);
            }
        }
    }

    public void schedule(MissionState mission) {
        Objects.requireNonNull(mission, "mission");
        if (mission.status() != MissionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active missions can be scheduled");
        }
        if (queue.size() >= SatelliteLimits.MAX_ACTIVE_MISSIONS) {
            throw new IllegalStateException("Mission scheduler capacity reached");
        }
        queue.add(new Entry(mission.completesAtLogicalTime(), mission.missionId()));
    }

    public DrainResult drainDue(
            long logicalTime,
            int completionBudget,
            Function<UUID, Optional<MissionState>> lookup,
            Consumer<MissionState> completion
    ) {
        if (logicalTime < 0L) {
            throw new IllegalArgumentException("Logical time cannot be negative");
        }
        if (completionBudget <= 0 || completionBudget > SatelliteLimits.MAX_COMPLETIONS_PER_PASS) {
            throw new IllegalArgumentException("Completion budget is outside fixed bounds");
        }
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(completion, "completion");

        int inspections = 0;
        int completed = 0;
        int stale = 0;
        while (completed < completionBudget
                && inspections < SatelliteLimits.MAX_QUEUE_INSPECTIONS_PER_PASS) {
            Entry head = queue.peek();
            if (head == null) {
                break;
            }
            inspections++;
            if (head.deadline() > logicalTime) {
                break;
            }
            queue.remove();
            Optional<MissionState> current = lookup.apply(head.missionId());
            if (current.isEmpty()
                    || current.orElseThrow().status() != MissionStatus.ACTIVE
                    || current.orElseThrow().completesAtLogicalTime() != head.deadline()) {
                stale++;
                continue;
            }
            completion.accept(current.orElseThrow());
            completed++;
        }
        return new DrainResult(completed, inspections, stale, queue.size());
    }

    public int scheduledCount() {
        return queue.size();
    }

    public Optional<Long> earliestDeadline() {
        return Optional.ofNullable(queue.peek()).map(Entry::deadline);
    }

    private record Entry(long deadline, UUID missionId) {
    }

    public record DrainResult(
            int completed,
            int inspectedEntries,
            int staleEntries,
            int remainingScheduled
    ) {
    }
}
