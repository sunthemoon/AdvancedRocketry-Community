package io.github.sunthemoon.advancedrocketrycommunity.station.model;

/** Minimal persisted v0.7 station environment profile. */
public record StationEnvironmentProfile(
        int gravityMilli,
        boolean vacuum,
        int solarAngleMilliDegrees
) {
    public static final StationEnvironmentProfile BASIC_SPACE = new StationEnvironmentProfile(
            0,
            true,
            270_000
    );

    public StationEnvironmentProfile {
        if (gravityMilli < 0 || gravityMilli > 10_000) {
            throw new IllegalArgumentException("Station gravity is outside the fixed bound");
        }
        if (solarAngleMilliDegrees < 0 || solarAngleMilliDegrees >= 360_000) {
            throw new IllegalArgumentException("Station solar angle is outside the fixed bound");
        }
    }
}

