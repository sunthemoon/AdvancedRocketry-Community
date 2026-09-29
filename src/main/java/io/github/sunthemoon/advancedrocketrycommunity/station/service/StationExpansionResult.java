package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import java.util.Optional;

/** Expansion outcome; the station is the observed state when issued, the published state when expanded. */
public record StationExpansionResult(StationExpansionCode code, Optional<StationState> station) {
    public StationExpansionResult {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(station, "station");
    }

    static StationExpansionResult of(StationExpansionCode code, StationState station) {
        return new StationExpansionResult(code, Optional.ofNullable(station));
    }

    static StationExpansionResult failure(StationExpansionCode code) {
        return new StationExpansionResult(code, Optional.empty());
    }
}
