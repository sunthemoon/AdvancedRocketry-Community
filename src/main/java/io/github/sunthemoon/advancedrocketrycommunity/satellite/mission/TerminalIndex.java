package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** ADR-051 section 7: terminal ID → the resource missions bound to it; derived, never persisted. */
final class TerminalIndex {
    private static final Comparator<UUID> ORDER = Comparator.comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final Map<UUID, Set<UUID>> bound = new HashMap<>();

    void changed(MissionState previous, MissionState next) {
        terminal(previous).ifPresent(terminal -> bound.computeIfPresent(terminal, (key, missions) -> {
            missions.remove(previous.missionId());
            return missions.isEmpty() ? null : missions;
        }));
        terminal(next).ifPresent(terminal -> bound.computeIfAbsent(terminal, key -> new HashSet<>())
                .add(next.missionId()));
    }

    List<UUID> boundTo(UUID terminal) {
        return bound.getOrDefault(terminal, Set.of()).stream().sorted(ORDER).toList();
    }

    private static Optional<UUID> terminal(MissionState mission) {
        return mission != null && mission.payload() instanceof MissionPayload.Resource resource
                ? Optional.of(resource.boundTerminal()) : Optional.empty();
    }
}
