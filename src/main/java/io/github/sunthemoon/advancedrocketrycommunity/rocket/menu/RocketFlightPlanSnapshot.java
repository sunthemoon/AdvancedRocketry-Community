package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import java.util.UUID;

/** Atomic display target; never grants permission to cancel or launch a flight. */
public record RocketFlightPlanSnapshot(RocketDestination destination, UUID stationId) {
    public RocketFlightPlanSnapshot {
        if ((destination == RocketDestination.SPACE_STATION) != (stationId != null)) {
            throw new IllegalArgumentException("Only a station route must include a station UUID");
        }
    }

    public static RocketFlightPlanSnapshot empty() {
        return new RocketFlightPlanSnapshot(null, null);
    }
}
