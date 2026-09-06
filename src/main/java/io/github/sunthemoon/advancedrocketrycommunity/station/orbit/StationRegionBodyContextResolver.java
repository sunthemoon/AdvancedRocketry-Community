package io.github.sunthemoon.advancedrocketrycommunity.station.orbit;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolution;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.InstanceBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import java.util.Optional;

/** Constant-time shared-Space region resolver backed only by committed station state. */
public final class StationRegionBodyContextResolver implements InstanceBodyContextResolver {
    private final StationLookup stations;

    public StationRegionBodyContextResolver(StationLookup stations) {
        this.stations = Objects.requireNonNull(stations, "stations");
    }

    @Override
    public BodyContextResolution resolve(WorldLocation location) {
        if (!location.levelKey().equals(CelestialIds.SPACE_LEVEL)) {
            return BodyContextResolution.unhandled();
        }
        Optional<StationState> station = stations.findAt(
                location.position().getX(),
                location.position().getZ()
        );
        if (station.isEmpty()) {
            return BodyContextResolution.unresolved();
        }
        StationState state = station.orElseThrow();
        return BodyContextResolution.resolved(BodyContext.stationOrbit(
                state.orbitBody(),
                state.stationId()
        ));
    }

    @FunctionalInterface
    public interface StationLookup {
        Optional<StationState> findAt(int x, int z);
    }
}
