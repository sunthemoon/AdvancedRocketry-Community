package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The station case of ADR-054 section 3 at a device position, observed on the server thread: outside the Space
 * Level no station rule applies; in it, a device inside a committed region of an operational, unquarantined
 * registry belongs to that station; anywhere else in the Space Level is unavailable.
 */
public final class EndgameStations {
    private EndgameStations() {
    }

    public static At at(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return new At(EndgameAuthority.StationContext.outside(), Optional.empty());
        }
        StationRegistrySavedData stations = StationRegistrySavedData.get(level.getServer());
        if (!stations.updatesAvailable()) {
            return new At(EndgameAuthority.StationContext.unavailable(), Optional.empty());
        }
        Optional<StationState> station = stations.findAt(position.getX(), position.getZ());
        return station.map(state -> new At(EndgameAuthority.StationContext.committed(state.ownerId(), state.members()),
                        Optional.of(state)))
                .orElseGet(() -> new At(EndgameAuthority.StationContext.unavailable(), Optional.empty()));
    }

    /** An endpoint's body and that body's star system (ADR-043 root). */
    public record Body(ResourceLocation body, ResourceLocation system) {
        public Body {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(system, "system");
        }
    }

    /**
     * ADR-054 section 9 body context, derived live and never stored: the ADR-014 resolver over one captured catalog,
     * with the station-region resolver first (a station's current orbit body), else the Level's single candidate body.
     * It reads the registry and the catalog only, never a chunk; empty means {@code BODY_UNAVAILABLE}.
     */
    public static Optional<Body> body(MinecraftServer server, CelestialCatalogManager catalogs,
                                      CelestialCatalog catalog, ResourceLocation level, long pos) {
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        BodyContextResolver resolver = new BodyContextResolver(catalogs,
                List.of(new StationRegionBodyContextResolver(stations::findAt)));
        return resolver.resolve(new WorldLocation(ResourceKey.create(Registries.DIMENSION, level), BlockPos.of(pos)),
                        catalog)
                .flatMap(context -> catalog.systemOf(context.bodyId()).map(system -> new Body(context.bodyId(),
                        system)));
    }

    /** @param station the committed station at the position, if any */
    public record At(EndgameAuthority.StationContext context, Optional<StationState> station) {
        public At {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(station, "station");
        }
    }
}
