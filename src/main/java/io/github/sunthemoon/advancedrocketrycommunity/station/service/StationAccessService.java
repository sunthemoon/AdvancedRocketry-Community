package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Centralized, client-independent station authorization policy. */
public final class StationAccessService {
    public boolean allowed(
            StationState station,
            UUID actorId,
            boolean operator,
            StationAccessAction action
    ) {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(action, "action");
        if (operator || station.ownerId().equals(actorId)) {
            return true;
        }
        return station.members().contains(actorId)
                && (action == StationAccessAction.VISIT || action == StationAccessAction.BUILD);
    }

    public List<StationState> accessibleDestinations(
            List<StationState> stations,
            UUID actorId,
            boolean operator
    ) {
        Objects.requireNonNull(stations, "stations");
        return stations.stream()
                .filter(station -> allowed(station, actorId, operator, StationAccessAction.VISIT))
                .sorted(Comparator.comparing(StationState::name)
                        .thenComparing(StationState::stationId))
                .limit(StationLimits.MAX_ACCESSIBLE_DESTINATIONS)
                .toList();
    }
}

