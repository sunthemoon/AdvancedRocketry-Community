package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ADR-051 section 7: terminal ID → the resource missions bound to it, and → the claims it paid that are not yet
 * acknowledged. Without a receipt, only such a claim needs action (REMATERIALIZE); every other bound row is
 * "nothing", so a reconciliation pass covers these and the receipts (C9-H1). Derived, never persisted.
 */
final class TerminalIndex {
    private static final Comparator<UUID> ORDER = Comparator.comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final Map<UUID, Set<UUID>> bound = new HashMap<>();
    private final Map<UUID, Set<UUID>> awaiting = new HashMap<>();

    void changed(MissionState previous, MissionState next) {
        terminal(previous).ifPresent(terminal -> remove(bound, terminal, previous.missionId()));
        terminal(next).ifPresent(terminal -> bound.computeIfAbsent(terminal, key -> new HashSet<>())
                .add(next.missionId()));
        paidUnacknowledged(previous).ifPresent(terminal -> remove(awaiting, terminal, previous.missionId()));
        paidUnacknowledged(next).ifPresent(terminal -> awaiting.computeIfAbsent(terminal, key -> new HashSet<>())
                .add(next.missionId()));
    }

    /** CLAIMED missions paid at this terminal and not acknowledged, in ID order. */
    List<UUID> awaiting(UUID terminal) {
        return awaiting.getOrDefault(terminal, Set.of()).stream().sorted(ORDER).toList();
    }

    private static void remove(Map<UUID, Set<UUID>> index, UUID terminal, UUID missionId) {
        index.computeIfPresent(terminal, (key, missions) -> {
            missions.remove(missionId);
            return missions.isEmpty() ? null : missions;
        });
    }

    private static Optional<UUID> paidUnacknowledged(MissionState mission) {
        return mission != null && mission.status() == MissionStatus.CLAIMED
                && mission.payload() instanceof MissionPayload.Resource resource && !resource.acknowledged()
                ? resource.paidTerminal() : Optional.empty();
    }

    List<UUID> boundTo(UUID terminal) {
        return bound.getOrDefault(terminal, Set.of()).stream().sorted(ORDER).toList();
    }

    private static Optional<UUID> terminal(MissionState mission) {
        return mission != null && mission.payload() instanceof MissionPayload.Resource resource
                ? Optional.of(resource.boundTerminal()) : Optional.empty();
    }
}
