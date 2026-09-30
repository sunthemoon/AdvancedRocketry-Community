package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import java.util.Optional;

/** Management outcome; the station is the observed state on failure, the published state on success. */
public record StationManagementResult(StationManagementCode code, Optional<StationState> station) {
    public StationManagementResult {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(station, "station");
    }

    static StationManagementResult of(StationManagementCode code, StationState station) {
        return new StationManagementResult(code, Optional.ofNullable(station));
    }

    static StationManagementResult failure(StationManagementCode code) {
        return new StationManagementResult(code, Optional.empty());
    }
}
