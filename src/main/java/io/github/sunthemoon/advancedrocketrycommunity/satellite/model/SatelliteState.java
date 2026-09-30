package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable logical satellite state (schema 2); it has no entity, Level, or chunk identity. */
public record SatelliteState(
        int schemaVersion,
        UUID satelliteId,
        ResourceLocation definitionId,
        UUID ownerId,
        long launchedAtLogicalTime,
        SatelliteStatus status,
        Optional<UUID> currentMissionId,
        SatelliteKind kind,
        Optional<ResourceLocation> orbitBody,
        SatelliteBlueprint blueprint,
        SatelliteKindState kindState
) {
    public SatelliteState {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currentMissionId, "currentMissionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(orbitBody, "orbitBody");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(kindState, "kindState");
        if (schemaVersion != SatelliteLimits.SATELLITE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported satellite schema " + schemaVersion);
        }
        if (launchedAtLogicalTime < 0L) {
            throw new IllegalArgumentException("Satellite launch time cannot be negative");
        }
        if (status != SatelliteStatus.OPERATIONAL && currentMissionId.isPresent()) {
            throw new IllegalArgumentException("Only operational satellites may retain a mission");
        }
        if (kindState.kind() != kind) {
            throw new IllegalArgumentException("Satellite kind state does not match its kind");
        }
        if ((kind == SatelliteKind.DATA) == orbitBody.isPresent()) {
            throw new IllegalArgumentException("Only non-data satellites have an orbit body");
        }
        if (blueprint.legacy() && kind != SatelliteKind.DATA) {
            throw new IllegalArgumentException("Only data satellites may carry the legacy blueprint");
        }
    }

    /** A {@code data} satellite from the terminal's fixed recipe or an ADR-029 payload. */
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
                Optional.empty(),
                SatelliteKind.DATA,
                Optional.empty(),
                SatelliteBlueprint.LEGACY_DATA,
                new SatelliteKindState.Plain(SatelliteKind.DATA)
        );
    }

    /** A non-{@code data} satellite, launched idle into a fixed orbit body (ADR-049 §6). */
    public static SatelliteState launchIdle(
            UUID satelliteId,
            ResourceLocation definitionId,
            UUID ownerId,
            long logicalTime,
            ResourceLocation orbitBody,
            SatelliteBlueprint blueprint,
            SatelliteKindState kindState
    ) {
        Objects.requireNonNull(kindState, "kindState");
        if (kindState.kind() == SatelliteKind.DATA) {
            throw new IllegalArgumentException("Data satellites use launch()");
        }
        return new SatelliteState(
                SatelliteLimits.SATELLITE_SCHEMA_VERSION,
                satelliteId,
                definitionId,
                ownerId,
                logicalTime,
                SatelliteStatus.OPERATIONAL,
                Optional.empty(),
                kindState.kind(),
                Optional.of(Objects.requireNonNull(orbitBody, "orbitBody")),
                blueprint,
                kindState
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
        return with(status, Optional.of(missionId), kindState);
    }

    public SatelliteState finishMission(UUID missionId) {
        if (!currentMissionId.filter(missionId::equals).isPresent()) {
            throw new IllegalStateException("Mission does not own this satellite");
        }
        return with(status, Optional.empty(), kindState);
    }

    public SatelliteState requireRecovery() {
        return with(SatelliteStatus.RECOVERY_REQUIRED, Optional.empty(), kindState);
    }

    public SatelliteState recover() {
        if (status != SatelliteStatus.RECOVERY_REQUIRED) {
            throw new IllegalStateException("Satellite does not require recovery");
        }
        return with(SatelliteStatus.OPERATIONAL, Optional.empty(), kindState);
    }

    public SatelliteState withKindState(SatelliteKindState next) {
        Objects.requireNonNull(next, "next");
        return with(status, currentMissionId, next);
    }

    private SatelliteState with(SatelliteStatus nextStatus, Optional<UUID> nextMission, SatelliteKindState nextState) {
        return new SatelliteState(
                schemaVersion,
                satelliteId,
                definitionId,
                ownerId,
                launchedAtLogicalTime,
                nextStatus,
                nextMission,
                kind,
                orbitBody,
                blueprint,
                nextState
        );
    }
}
