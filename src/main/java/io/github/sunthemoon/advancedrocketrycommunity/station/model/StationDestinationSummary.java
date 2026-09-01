package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import java.util.Objects;
import java.util.UUID;

/** Bounded display-only station entry; the server resolves authority again at launch. */
public record StationDestinationSummary(UUID stationId, String name) {
    public StationDestinationSummary {
        Objects.requireNonNull(stationId, "stationId");
        name = StationState.requireName(name);
    }

    public static StationDestinationSummary from(StationState state) {
        return new StationDestinationSummary(state.stationId(), state.name());
    }
}

