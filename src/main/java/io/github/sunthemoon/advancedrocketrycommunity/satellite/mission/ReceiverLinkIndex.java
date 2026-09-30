package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** ADR-049 section 9: receiver → linked solar satellites, kept in step with every satellite change. */
final class ReceiverLinkIndex {
    private static final Comparator<UUID> ORDER = Comparator.comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final Map<UUID, Set<UUID>> links = new HashMap<>();

    /** Either side may be null (a new or removed satellite). */
    void update(SatelliteState previous, SatelliteState next) {
        receiver(previous).ifPresent(receiver -> links.computeIfPresent(receiver, (key, linked) -> {
            linked.remove(previous.satelliteId());
            return linked.isEmpty() ? null : linked;
        }));
        receiver(next).ifPresent(receiver -> links.computeIfAbsent(receiver, key -> new HashSet<>()).add(next.satelliteId()));
    }

    List<UUID> linkedTo(UUID receiverId) {
        return links.getOrDefault(Objects.requireNonNull(receiverId, "receiverId"), Set.of()).stream().sorted(ORDER).toList();
    }

    private static Optional<UUID> receiver(SatelliteState state) {
        return state != null && state.kindState() instanceof SatelliteKindState.Solar solar
                ? solar.receiver() : Optional.empty();
    }
}
