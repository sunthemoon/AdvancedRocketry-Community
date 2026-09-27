package io.github.sunthemoon.advancedrocketrycommunity.travel.migration;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Pure two-phase v1.0 location/plan identity migrator. */
public final class LegacyFlightTargetMigrator {
    private final CelestialCatalogManager catalogs;
    private final BodyContextResolver contexts;

    public LegacyFlightTargetMigrator(
            CelestialCatalogManager catalogs,
            BodyContextResolver contexts
    ) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    public TravelTargetMigrationResult currentLocation(
            ResourceLocation legacyBody,
            ResourceLocation legacyDimension,
            WorldLocation location
    ) {
        Objects.requireNonNull(legacyBody, "legacyBody");
        Objects.requireNonNull(legacyDimension, "legacyDimension");
        Objects.requireNonNull(location, "location");
        if (!location.levelKey().location().equals(legacyDimension)) {
            return TravelTargetMigrationResult.blocked(
                    TravelTargetMigrationCode.UNKNOWN_LEGACY_LOCATION
            );
        }

        Optional<BodyContext> resolved = contexts.resolve(location);
        if (resolved.isEmpty()) {
            return TravelTargetMigrationResult.blocked(
                    legacyDimension.equals(CelestialIds.SPACE_LEVEL.location())
                            ? TravelTargetMigrationCode.UNRESOLVED_SHARED_LEVEL
                            : TravelTargetMigrationCode.UNKNOWN_LEGACY_LOCATION
            );
        }
        BodyContext context = resolved.orElseThrow();
        boolean sharedSpaceAlias = legacyDimension.equals(CelestialIds.SPACE_LEVEL.location())
                && legacyBody.equals(CelestialIds.SPACE_ID);
        if (!sharedSpaceAlias && !legacyBody.equals(context.bodyId())) {
            return TravelTargetMigrationResult.blocked(
                    TravelTargetMigrationCode.UNKNOWN_LEGACY_LOCATION
            );
        }
        return TravelTargetMigrationResult.migrated(toTarget(context));
    }

    public TravelTargetMigrationResult destination(
            ResourceLocation legacyBody,
            ResourceLocation legacyDimension,
            UUID stationId
    ) {
        Objects.requireNonNull(legacyBody, "legacyBody");
        Objects.requireNonNull(legacyDimension, "legacyDimension");
        boolean sharedSpace = legacyBody.equals(CelestialIds.SPACE_ID)
                && legacyDimension.equals(CelestialIds.SPACE_LEVEL.location());
        if (stationId != null) {
            return sharedSpace
                    ? migrateStation(stationId)
                    : TravelTargetMigrationResult.blocked(
                            TravelTargetMigrationCode.INVALID_LEGACY_STATION_TARGET
                    );
        }
        if (sharedSpace) {
            return TravelTargetMigrationResult.blocked(
                    TravelTargetMigrationCode.INVALID_LEGACY_STATION_TARGET
            );
        }

        Optional<CelestialBodyDefinition> definition = catalogs.current()
                .flatMap(catalog -> catalog.get(legacyBody));
        if (definition.isEmpty()
                || definition.orElseThrow().levelKey()
                        .filter(level -> level.location().equals(legacyDimension)).isEmpty()) {
            return TravelTargetMigrationResult.blocked(
                    TravelTargetMigrationCode.UNKNOWN_DESTINATION_BODY
            );
        }
        return TravelTargetMigrationResult.migrated(new TravelTarget.BodySurface(legacyBody));
    }

    private static TravelTargetMigrationResult migrateStation(UUID stationId) {
        try {
            return TravelTargetMigrationResult.migrated(new TravelTarget.Station(stationId));
        } catch (IllegalArgumentException exception) {
            return TravelTargetMigrationResult.blocked(
                    TravelTargetMigrationCode.INVALID_LEGACY_STATION_TARGET
            );
        }
    }

    private static TravelTarget toTarget(BodyContext context) {
        return switch (context.locus()) {
            case SURFACE -> new TravelTarget.BodySurface(context.bodyId());
            case ORBIT -> context.instanceId()
                    .<TravelTarget>map(TravelTarget.Station::new)
                    .orElseGet(() -> new TravelTarget.Orbit(context.bodyId()));
            case MISSION -> new TravelTarget.Mission(context.instanceId().orElseThrow());
        };
    }
}
