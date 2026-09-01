package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;

public record RocketFlightTransition(
        RocketFlightState previous,
        RocketFlightEvent event,
        RocketFlightState next,
        boolean applied
) {
    public RocketFlightTransition {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(next, "next");
        if (!applied && previous != next) {
            throw new IllegalArgumentException("Rejected transition must retain its previous state");
        }
    }
}
