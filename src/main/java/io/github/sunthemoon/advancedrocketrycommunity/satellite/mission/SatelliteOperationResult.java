package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.Objects;
import java.util.Optional;

/** Bounded operation result; detail strings are produced only by trusted server code. */
public record SatelliteOperationResult(
        SatelliteOperationCode code,
        boolean changed,
        Optional<SatelliteState> satellite,
        Optional<MissionState> mission,
        int researchBalance
) {
    public SatelliteOperationResult {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(satellite, "satellite");
        Objects.requireNonNull(mission, "mission");
        if (researchBalance < 0) {
            throw new IllegalArgumentException("Research balance cannot be negative");
        }
    }

    public boolean success() {
        return code == SatelliteOperationCode.SUCCESS
                || code == SatelliteOperationCode.IDEMPOTENT
                || code == SatelliteOperationCode.PENDING_DISCOVERY
                || code == SatelliteOperationCode.ALREADY_CLAIMED;
    }
}
