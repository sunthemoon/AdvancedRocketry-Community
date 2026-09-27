package io.github.sunthemoon.advancedrocketrycommunity.compat.environment;

import io.github.sunthemoon.advancedrocketrycommunity.api.environment.AtmosphereProfile;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentQueries;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Internal lifetime boundary. Closing drops every reference to the owning server's state. */
public final class ServerEnvironmentQueries implements EnvironmentQueries, AutoCloseable {
    private volatile Binding binding;

    public ServerEnvironmentQueries(BooleanSupplier owningThread, Predicate<ResourceKey<Level>> levelAvailable,
                                    CelestialCatalogManager catalogs, StationRegistrySavedData stations) {
        Objects.requireNonNull(catalogs, "catalogs");
        Objects.requireNonNull(stations, "stations");
        binding = new Binding(Objects.requireNonNull(owningThread, "owningThread"),
                Objects.requireNonNull(levelAvailable, "levelAvailable"), catalogs, stations,
                new BodyContextResolver(catalogs, List.of(new StationRegionBodyContextResolver(stations::findAt))));
    }

    @Override
    public Optional<EnvironmentSnapshot> at(ResourceKey<Level> dimension, BlockPos position) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        Binding active = binding;
        if (active == null) {
            throw new IllegalStateException("Environment handle belongs to a stopped server");
        }
        if (!active.owningThread().getAsBoolean()) {
            throw new IllegalStateException("Environment queries require the owning server thread");
        }
        if (!active.levelAvailable().test(dimension)) {
            return Optional.empty();
        }
        CelestialCatalog catalog = active.catalogs().current().orElse(null);
        if (catalog == null) {
            return Optional.empty();
        }
        return active.contexts().resolve(new WorldLocation(dimension, position), catalog)
                .flatMap(context -> project(active, catalog, context));
    }

    private static Optional<EnvironmentSnapshot> project(Binding active, CelestialCatalog catalog, BodyContext context) {
        if (context.locus() == BodyContext.Locus.SURFACE) {
            return catalog.get(context.bodyId()).map(ServerEnvironmentQueries::surface);
        }
        if (context.locus() == BodyContext.Locus.ORBIT && context.instanceId().isPresent()) {
            return active.stations().find(context.instanceId().orElseThrow())
                    .filter(station -> station.orbitBody().equals(context.bodyId()))
                    .map(ServerEnvironmentQueries::station);
        }
        return Optional.empty();
    }

    private static EnvironmentSnapshot surface(CelestialBodyDefinition body) {
        var atmosphere = body.atmosphere();
        return new EnvironmentSnapshot(body.id(), EnvironmentSnapshot.Locus.SURFACE, Optional.empty(),
                body.gravityMultiplier(), atmosphere.pressure() == 0.0D,
                Optional.of(new AtmosphereProfile(atmosphere.pressure(), atmosphere.breathable(),
                        atmosphere.temperatureKelvin(), atmosphere.profile())));
    }

    private static EnvironmentSnapshot station(StationState station) {
        return new EnvironmentSnapshot(station.orbitBody(), EnvironmentSnapshot.Locus.STATION_ORBIT,
                Optional.of(station.stationId()), station.environment().gravityMilli() / 1_000.0D,
                station.environment().vacuum(), Optional.empty());
    }

    @Override
    public void close() {
        binding = null;
    }

    private record Binding(BooleanSupplier owningThread, Predicate<ResourceKey<Level>> levelAvailable,
                           CelestialCatalogManager catalogs, StationRegistrySavedData stations,
                           BodyContextResolver contexts) {
    }
}
