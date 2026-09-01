package io.github.sunthemoon.advancedrocketrycommunity.station.forge;

import java.util.Objects;

public record StationPlatformResult(boolean success, int inspected, int changed, int chunksLoaded, String detail) {
    public StationPlatformResult {
        if (inspected < 0 || changed < 0 || chunksLoaded < 0) {
            throw new IllegalArgumentException("Station platform counters cannot be negative");
        }
        Objects.requireNonNull(detail, "detail");
    }

    public static StationPlatformResult success(int inspected, int changed, int chunksLoaded) {
        return new StationPlatformResult(true, inspected, changed, chunksLoaded, "generated");
    }

    public static StationPlatformResult failure(int inspected, int changed, int chunksLoaded, String detail) {
        return new StationPlatformResult(false, inspected, changed, chunksLoaded, detail);
    }
}

