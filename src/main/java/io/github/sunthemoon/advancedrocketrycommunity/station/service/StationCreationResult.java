package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import java.util.Optional;

public record StationCreationResult(StationCreationCode code, StationState value) {
    public StationCreationResult {
        Objects.requireNonNull(code, "code");
        if ((code == StationCreationCode.SUCCESS) != (value != null)) {
            throw new IllegalArgumentException("Station creation result/value mismatch");
        }
    }

    public static StationCreationResult success(StationState state) {
        return new StationCreationResult(
                StationCreationCode.SUCCESS,
                Objects.requireNonNull(state, "state")
        );
    }

    public static StationCreationResult failure(StationCreationCode code) {
        if (code == StationCreationCode.SUCCESS) {
            throw new IllegalArgumentException("Success requires station state");
        }
        return new StationCreationResult(code, null);
    }

    public boolean success() {
        return code == StationCreationCode.SUCCESS;
    }

    public Optional<StationState> station() {
        return Optional.ofNullable(value);
    }
}

