package io.github.sunthemoon.advancedrocketrycommunity.station.orbit;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** Constant-time ADR-041 resolver: one indexed region lookup and one catalog lookup, no world access. */
public final class StationOrbitEnvironmentResolver {
    private final CelestialCatalogManager catalogs;

    public StationOrbitEnvironmentResolver(CelestialCatalogManager catalogs) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
    }

    /** Empty outside the fixed Space Level, in gaps and while the registry is blocked. */
    public Optional<StationOrbitEnvironment> at(MinecraftServer server, ResourceKey<Level> level, int x, int z) {
        Objects.requireNonNull(server, "server");
        if (!CelestialIds.SPACE_LEVEL.equals(level)) {
            return Optional.empty();
        }
        return resolveAt(StationRegistrySavedData.get(server), catalogs.current(), level, x, z);
    }

    /** Pure lookup: Space only, one indexed region lookup (empty when blocked), then {@link #resolve}. */
    public static Optional<StationOrbitEnvironment> resolveAt(StationRegistrySavedData registry,
                                                              Optional<CelestialCatalog> catalog,
                                                              ResourceKey<Level> level, int x, int z) {
        Objects.requireNonNull(registry, "registry");
        if (!CelestialIds.SPACE_LEVEL.equals(level)) {
            return Optional.empty();
        }
        return registry.findAt(x, z).map(station -> resolve(station, catalog));
    }

    public static StationOrbitEnvironment resolve(StationState station, Optional<CelestialCatalog> catalog) {
        Objects.requireNonNull(station, "station");
        Objects.requireNonNull(catalog, "catalog");
        Optional<CelestialBodyDefinition> body = catalog.flatMap(value -> value.get(station.orbitBody()));
        // Orbit is above any atmosphere: use the orbited body's intensity, or shared Space's if it is missing.
        double solar = body.or(() -> catalog.flatMap(value -> value.get(CelestialIds.SPACE_ID)))
                .map(CelestialBodyDefinition::solarIntensity)
                .orElse(0.0D);
        double configured = station.environment().gravityMilli() / 1_000.0D;
        return new StationOrbitEnvironment(
                station.stationId(),
                station.name(),
                station.orbitBody(),
                body.isPresent(),
                configured,
                Math.min(configured, CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER),
                station.environment().vacuum(),
                solar,
                station.environment().solarAngleMilliDegrees() / 1_000.0D
        );
    }
}
