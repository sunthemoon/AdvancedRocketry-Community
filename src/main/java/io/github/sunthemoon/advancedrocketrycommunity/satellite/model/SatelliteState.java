package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable logical satellite state; it has no entity, Level, or chunk identity. */
public record SatelliteState(
        int schemaVersion,
        UUID satelliteId,
        ResourceLocation definitionId,
        UUID ownerId,
        long launchedAtLogicalTime,
        SatelliteStatus status,
        Optional<UUID> currentMissionId
) {
    public SatelliteState {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currentMissionId, "currentMissionId");
        if (schemaVersion != SatelliteLimits.SATELLITE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported satellite schema " + schemaVersion);
        }
        if (launchedAtLogicalTime < 0L) {
            throw new IllegalArgumentException("Satellite launch time cannot be negative");
        }
        if (status != SatelliteStatus.OPERATIONAL && currentMissionId.isPresent()) {
            throw new IllegalArgumentException("Only operational satellites may retain a mission");
        }
    }

    public static SatelliteState launch(
            UUID satelliteId,
            ResourceLocation definitionId,
            UUID ownerId,
            long logicalTime
    ) {
        return new SatelliteState(
                SatelliteLimits.SATELLITE_SCHEMA_VERSION,
                satelliteId,
                definitionId,
                ownerId,
                logicalTime,
                SatelliteStatus.OPERATIONAL,
                Optional.empty()
        );
    }

    public SatelliteState startMission(UUID missionId) {
        Objects.requireNonNull(missionId, "missionId");
        if (status != SatelliteStatus.OPERATIONAL) {
            throw new IllegalStateException("Satellite is not operational");
        }
        if (currentMissionId.isPresent()) {
            throw new IllegalStateException("Satellite already has an unfinished mission");
        }
        return new SatelliteState(
                schemaVersion,
                satelliteId,
                definitionId,
                ownerId,
                launchedAtLogicalTime,
                status,
                Optional.of(missionId)
        );
    }

    public SatelliteState finishMission(UUID missionId) {
        if (!currentMissionId.filter(missionId::equals).isPresent()) {
            throw new IllegalStateException("Mission does not own this satellite");
        }
        return new SatelliteState(
                schemaVersion,
                satelliteId,
                definitionId,
                ownerId,
                launchedAtLogicalTime,
                status,
                Optional.empty()
        );
    }

    public SatelliteState requireRecovery() {
        return new SatelliteState(
                schemaVersion,
                satelliteId,
                definitionId,
                ownerId,
                launchedAtLogicalTime,
                SatelliteStatus.RECOVERY_REQUIRED,
                Optional.empty()
        );
    }

    public SatelliteState recover() {
        if (status != SatelliteStatus.RECOVERY_REQUIRED) {
            throw new IllegalStateException("Satellite does not require recovery");
        }
        return new SatelliteState(
                schemaVersion,
                satelliteId,
                definitionId,
                ownerId,
                launchedAtLogicalTime,
                SatelliteStatus.OPERATIONAL,
                Optional.empty()
        );
    }
}
