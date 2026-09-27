package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.PlanetarySurfaceResolver;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import net.minecraft.server.level.ServerLevel;

/** Metadata-only admission for quotes and new launches; committed recovery is separate. */
final class PlanetaryFlightAdmission {
    private PlanetaryFlightAdmission() {
    }

    static boolean allows(RocketEntity rocket, RocketFlightData flight, TravelTarget source,
            TravelTarget destination, CelestialCatalog catalog, StationRegistrySavedData stations) {
        if (!(rocket.level() instanceof ServerLevel live)
                || live.getServer().getLevel(live.dimension()) != live
                || !live.dimension().location().equals(flight.currentDimension())
                || flight.currentTarget().filter(source::equals).isEmpty()
                || rocket.snapshot().filter(snapshot -> snapshot.sourceDimension().equals(flight.currentDimension())).isEmpty()) {
            return false;
        }
        if (source instanceof TravelTarget.BodySurface surface) {
            if (!surface.bodyId().equals(flight.currentBody()) || PlanetarySurfaceResolver.find(catalog, live.dimension(), false)
                    .filter(body -> body.id().equals(surface.bodyId())).isEmpty()) {
                return false;
            }
        } else if (source instanceof TravelTarget.Station station) {
            if (!live.dimension().equals(CelestialIds.SPACE_LEVEL)) {
                return false;
            }
            var committed = stations.findAt(rocket.blockPosition().getX(), rocket.blockPosition().getZ())
                    .filter(state -> state.stationId().equals(station.instanceId()))
                    .filter(state -> catalog.get(state.orbitBody()).isPresent())
                    .filter(state -> flight.currentBody().equals(state.orbitBody())
                            || flight.currentBody().equals(CelestialIds.SPACE_ID));
            if (committed.isEmpty()) {
                return false;
            }
        } else {
            return false;
        }
        if (destination instanceof TravelTarget.BodySurface surface) {
            return catalog.get(surface.bodyId()).flatMap(body -> body.levelKey())
                    .filter(key -> live.getServer().getLevel(key) != null)
                    .flatMap(key -> PlanetarySurfaceResolver.find(catalog, key, true))
                    .filter(body -> body.id().equals(surface.bodyId())).isPresent();
        }
        if (destination instanceof TravelTarget.Station station) {
            return live.getServer().getLevel(CelestialIds.SPACE_LEVEL) != null
                    && stations.find(station.instanceId()).flatMap(state -> catalog.get(state.orbitBody()))
                            .filter(body -> body.capabilities().orbitable()).isPresent();
        }
        return false;
    }
}
