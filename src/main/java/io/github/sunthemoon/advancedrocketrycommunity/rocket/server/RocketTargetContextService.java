package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyFlightTargetMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Resolves runtime body context and performs contextual v1.0 target migration. */
final class RocketTargetContextService {
    private final CelestialCatalogManager celestialCatalogs;

    RocketTargetContextService(CelestialCatalogManager celestialCatalogs) {
        this.celestialCatalogs = celestialCatalogs;
    }

    Optional<TravelTarget> resolveCurrentTarget(ServerLevel level, BlockPos position) {
        if (celestialCatalogs == null) {
            return Optional.empty();
        }
        return contextResolver(level.getServer()).resolve(new WorldLocation(level.dimension(), position))
                .map(RocketTargetContextService::targetForContext);
    }

    Optional<RocketFlightData> migrateLegacyFlightData(ServerLevel level, RocketFlightData legacy) {
        if (celestialCatalogs == null || legacy.schemaVersion() != 1) {
            return Optional.empty();
        }
        LegacyFlightTargetMigrator migrator = new LegacyFlightTargetMigrator(
                celestialCatalogs,
                contextResolver(level.getServer())
        );
        return migrator.currentLocation(
                        legacy.currentBody(),
                        legacy.currentDimension(),
                        new WorldLocation(level.dimension(), new BlockPos(
                                legacy.currentOrigin().x(),
                                legacy.currentOrigin().y(),
                                legacy.currentOrigin().z()
                        ))
                )
                .target()
                .map(legacy::withMigratedCurrentTarget);
    }

    Optional<RocketTransferRecord> migrateCommittedLegacyTransfer(
            MinecraftServer server,
            RocketTransferRecord legacy
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(legacy, "legacy");
        if (celestialCatalogs == null
                || legacy.schemaVersion() != 1
                || legacy.phase() != RocketTransferPhase.COMMITTED) {
            return Optional.empty();
        }
        LegacyFlightTargetMigrator migrator = new LegacyFlightTargetMigrator(
                celestialCatalogs,
                contextResolver(server)
        );
        Optional<TravelTarget> sourceTarget = migrateRecordLocation(migrator, legacy.sourceFlightData());
        Optional<TravelTarget> destinationTarget = migrateRecordLocation(migrator, legacy.destinationFlightData());
        Optional<TravelTarget> plannedTarget = legacy.destinationFlightData().plan()
                .flatMap(plan -> migrator.destination(
                        plan.destinationBody(),
                        plan.destinationDimension(),
                        plan.destinationStation().orElse(null)
                ).target());
        if (sourceTarget.isEmpty()
                || destinationTarget.isEmpty()
                || plannedTarget.isEmpty()
                || !destinationTarget.equals(plannedTarget)) {
            return Optional.empty();
        }
        return Optional.of(legacy.migrateTargets(
                sourceTarget.orElseThrow(),
                destinationTarget.orElseThrow()
        ));
    }

    private static Optional<TravelTarget> migrateRecordLocation(
            LegacyFlightTargetMigrator migrator,
            RocketFlightData flight
    ) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, flight.currentDimension());
        RocketPosition origin = flight.currentOrigin();
        return migrator.currentLocation(
                flight.currentBody(),
                flight.currentDimension(),
                new WorldLocation(levelKey, new BlockPos(origin.x(), origin.y(), origin.z()))
        ).target();
    }

    private BodyContextResolver contextResolver(MinecraftServer server) {
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        return new BodyContextResolver(
                celestialCatalogs,
                List.of(new StationRegionBodyContextResolver((x, z) -> stations.findAt(x, z)
                        .filter(station -> celestialCatalogs.current()
                                .flatMap(catalog -> catalog.get(station.orbitBody()))
                                .isPresent())))
        );
    }

    private static TravelTarget targetForContext(BodyContext context) {
        return switch (context.locus()) {
            case SURFACE -> new TravelTarget.BodySurface(context.bodyId());
            case ORBIT -> context.instanceId()
                    .<TravelTarget>map(TravelTarget.Station::new)
                    .orElseGet(() -> new TravelTarget.Orbit(context.bodyId()));
            case MISSION -> new TravelTarget.Mission(context.instanceId().orElseThrow());
        };
    }
}
