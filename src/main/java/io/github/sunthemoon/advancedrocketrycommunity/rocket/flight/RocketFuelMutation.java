package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;

public record RocketFuelMutation(
        RocketFuelCode code,
        RocketFuelState state,
        long unitsChanged
) {
    public RocketFuelMutation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(state, "state");
        if (unitsChanged < 0L) {
            throw new IllegalArgumentException("Fuel mutation cannot report negative units");
        }
        if (code != RocketFuelCode.SUCCESS && unitsChanged != 0L) {
            throw new IllegalArgumentException("Failed fuel mutation cannot change units");
        }
    }

    public boolean success() {
        return code == RocketFuelCode.SUCCESS;
    }
}
