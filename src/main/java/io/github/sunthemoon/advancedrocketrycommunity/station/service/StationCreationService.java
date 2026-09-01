package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Reserve-generate-commit station creation transaction with exact rollback. */
public final class StationCreationService {
    private final StationPlatformGenerator platforms;

    public StationCreationService(StationPlatformGenerator platforms) {
        this.platforms = Objects.requireNonNull(platforms, "platforms");
    }

    public StationCreationResult create(
            MinecraftServer server,
            UUID ownerId,
            String name,
            ResourceLocation orbitBody,
            boolean enforceOwnerLimit
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(orbitBody, "orbitBody");
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        if (!data.operational()) {
            return StationCreationResult.failure(StationCreationCode.REGISTRY_BLOCKED);
        }
        if (enforceOwnerLimit && data.ownedBy(ownerId) >= StationLimits.MAX_OWNED_STATIONS) {
            return StationCreationResult.failure(StationCreationCode.OWNER_LIMIT_REACHED);
        }
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        if (space == null) {
            return StationCreationResult.failure(StationCreationCode.SPACE_UNAVAILABLE);
        }

        UUID stationId = UUID.randomUUID();
        StationReservation reservation;
        try {
            reservation = data.reserve(stationId, ownerId, name, orbitBody, space.getGameTime());
            data.flush(server);
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_RESERVE_FAILED station={} owner={}", stationId, ownerId, exception
            );
            return StationCreationResult.failure(StationCreationCode.REGION_UNAVAILABLE);
        }

        StationPlatformResult platform = platforms.generate(space, reservation);
        if (!platform.success()) {
            data.release(stationId);
            data.flush(server);
            audit("creation_rolled_back", reservation, platform);
            return StationCreationResult.failure(StationCreationCode.PLATFORM_BLOCKED);
        }
        try {
            StationState state = data.commit(stationId);
            data.flush(server);
            audit("creation_committed", reservation, platform);
            return StationCreationResult.success(state);
        } catch (RuntimeException exception) {
            StationPlatformResult rollback = platforms.removeTemplate(space, reservation.cell());
            data.release(stationId);
            try {
                data.flush(server);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_COMMIT_FAILED station={} owner={} rollback_blocks={}",
                    stationId,
                    ownerId,
                    rollback.changed(),
                    exception
            );
            return StationCreationResult.failure(StationCreationCode.PERSISTENCE_FAILED);
        }
    }

    public int recoverReservations(MinecraftServer server) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        if (!data.operational() || space == null) {
            return 0;
        }
        int recovered = 0;
        for (StationReservation reservation : data.reservations()) {
            StationPlatformResult rollback = platforms.removeTemplate(space, reservation.cell());
            if (data.release(reservation.stationId())) {
                recovered++;
                audit("reservation_recovered", reservation, rollback);
            }
        }
        if (recovered > 0) {
            data.flush(server);
        }
        return recovered;
    }

    private static void audit(
            String action,
            StationReservation reservation,
            StationPlatformResult platform
    ) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_TRANSACTION action={} station={} owner={} cell={},{} region={},{},{},{} "
                        + "inspected={} changed={} chunks={} detail={}",
                action,
                reservation.stationId(),
                reservation.ownerId(),
                reservation.cell().x(),
                reservation.cell().z(),
                reservation.region().minimumX(),
                reservation.region().minimumZ(),
                reservation.region().maximumX(),
                reservation.region().maximumZ(),
                platform.inspected(),
                platform.changed(),
                platform.chunksLoaded(),
                platform.detail()
        );
    }
}

