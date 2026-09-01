package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;
import java.util.UUID;

public record RocketPassengerSeat(UUID passengerId, int seatIndex) {
    public RocketPassengerSeat {
        Objects.requireNonNull(passengerId, "passengerId");
        if (seatIndex < 0 || seatIndex >= RocketFlightLimits.MAX_PASSENGERS) {
            throw new IllegalArgumentException("Passenger seat index is outside the fixed limit");
        }
    }
}
