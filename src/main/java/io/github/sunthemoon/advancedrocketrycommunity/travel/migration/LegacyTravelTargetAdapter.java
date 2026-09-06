package io.github.sunthemoon.advancedrocketrycommunity.travel.migration;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Explicit compatibility boundary for v1.0 fixed destination identities. */
public final class LegacyTravelTargetAdapter {
    private LegacyTravelTargetAdapter() {
    }

    public static TravelTarget fromLegacy(RocketDestination destination, UUID stationId) {
        Objects.requireNonNull(destination, "destination");
        return switch (destination) {
            case EARTH -> requireNoStation(stationId, new TravelTarget.BodySurface(ModIdentity.id("earth")));
            case MOON -> requireNoStation(stationId, new TravelTarget.BodySurface(ModIdentity.id("moon")));
            case SPACE_STATION -> new TravelTarget.Station(Objects.requireNonNull(stationId, "stationId"));
        };
    }

    public static Optional<LegacyDestination> toLegacy(TravelTarget target) {
        Objects.requireNonNull(target, "target");
        if (target instanceof TravelTarget.BodySurface surface) {
            if (surface.bodyId().equals(ModIdentity.id("earth"))) {
                return Optional.of(new LegacyDestination(RocketDestination.EARTH, null));
            }
            if (surface.bodyId().equals(ModIdentity.id("moon"))) {
                return Optional.of(new LegacyDestination(RocketDestination.MOON, null));
            }
            return Optional.empty();
        }
        if (target instanceof TravelTarget.Station station) {
            return Optional.of(new LegacyDestination(RocketDestination.SPACE_STATION, station.instanceId()));
        }
        return Optional.empty();
    }

    private static TravelTarget requireNoStation(UUID stationId, TravelTarget target) {
        if (stationId != null) {
            throw new IllegalArgumentException("Body destination cannot carry a station UUID");
        }
        return target;
    }

    public record LegacyDestination(RocketDestination destination, UUID stationId) {
        public LegacyDestination {
            Objects.requireNonNull(destination, "destination");
            if ((destination == RocketDestination.SPACE_STATION) != (stationId != null)) {
                throw new IllegalArgumentException("Legacy station identity shape is invalid");
            }
        }
    }
}
