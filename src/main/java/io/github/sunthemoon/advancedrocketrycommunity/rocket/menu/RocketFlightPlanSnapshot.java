package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.UUID;

/** Atomic display target; never grants permission to cancel or launch a flight. */
public record RocketFlightPlanSnapshot(TravelTarget target) {
    public RocketFlightPlanSnapshot(RocketDestination destination, UUID stationId) {
        this(legacyTarget(destination, stationId));
    }

    public RocketDestination destination() {
        return target == null
                ? null
                : LegacyTravelTargetAdapter.toLegacy(target)
                .map(LegacyTravelTargetAdapter.LegacyDestination::destination)
                .orElse(null);
    }

    public UUID stationId() {
        return target instanceof TravelTarget.Station station ? station.instanceId() : null;
    }

    public static RocketFlightPlanSnapshot empty() {
        return new RocketFlightPlanSnapshot((TravelTarget) null);
    }

    private static TravelTarget legacyTarget(RocketDestination destination, UUID stationId) {
        if (destination == null) {
            if (stationId != null) {
                throw new IllegalArgumentException("An empty flight plan cannot carry a station UUID");
            }
            return null;
        }
        return LegacyTravelTargetAdapter.fromLegacy(destination, stationId);
    }
}
