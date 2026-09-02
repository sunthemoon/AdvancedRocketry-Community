package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable mission snapshot with explicit, replay-safe terminal phases. */
public record MissionState(
        int schemaVersion,
        UUID missionId,
        UUID satelliteId,
        UUID ownerId,
        ResourceLocation definitionId,
        ResourceLocation targetBodyId,
        long startedAtLogicalTime,
        long completesAtLogicalTime,
        int researchYield,
        int discoveryCost,
        boolean discoveryRequired,
        MissionStatus status,
        OptionalLong readyAtLogicalTime,
        OptionalLong resolvedAtLogicalTime
) {
    public MissionState {
        Objects.requireNonNull(missionId, "missionId");
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(targetBodyId, "targetBodyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(readyAtLogicalTime, "readyAtLogicalTime");
        Objects.requireNonNull(resolvedAtLogicalTime, "resolvedAtLogicalTime");
        if (schemaVersion != SatelliteLimits.MISSION_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported mission schema " + schemaVersion);
        }
        if (startedAtLogicalTime < 0L || completesAtLogicalTime <= startedAtLogicalTime) {
            throw new IllegalArgumentException("Mission times are invalid");
        }
        if (researchYield <= 0 || researchYield > SatelliteLimits.MAX_RESEARCH_PER_MISSION) {
            throw new IllegalArgumentException("Mission research yield is outside fixed bounds");
        }
        if (discoveryCost <= 0 || discoveryCost > researchYield) {
            throw new IllegalArgumentException("Mission discovery cost is invalid");
        }
        validatePhase(status, startedAtLogicalTime, completesAtLogicalTime,
                readyAtLogicalTime, resolvedAtLogicalTime);
    }

    public static MissionState start(
            UUID missionId,
            UUID satelliteId,
            UUID ownerId,
            SatelliteDefinitionSnapshot definition,
            ResourceLocation targetBodyId,
            long logicalTime,
            boolean discoveryRequired
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(targetBodyId, "targetBodyId");
        if (!definition.allowedTargets().contains(targetBodyId)) {
            throw new IllegalArgumentException("Target is not allowed by the satellite definition");
        }
        long completion = Math.addExact(logicalTime, definition.missionDurationTicks());
        return new MissionState(
                SatelliteLimits.MISSION_SCHEMA_VERSION,
                missionId,
                satelliteId,
                ownerId,
                definition.definitionId(),
                targetBodyId,
                logicalTime,
                completion,
                definition.researchYield(),
                definition.discoveryCost(),
                discoveryRequired,
                MissionStatus.ACTIVE,
                OptionalLong.empty(),
                OptionalLong.empty()
        );
    }

    public MissionState complete(long logicalTime) {
        requireStatus(MissionStatus.ACTIVE);
        if (logicalTime < completesAtLogicalTime) {
            throw new IllegalStateException("Mission deadline has not been reached");
        }
        return with(MissionStatus.READY, OptionalLong.of(logicalTime), OptionalLong.empty());
    }

    public MissionState beginClaim(long logicalTime) {
        requireStatus(MissionStatus.READY);
        if (logicalTime < readyAtLogicalTime.orElseThrow()) {
            throw new IllegalArgumentException("Claim time precedes mission readiness");
        }
        return with(
                discoveryRequired ? MissionStatus.CLAIM_PENDING_DISCOVERY : MissionStatus.CLAIMED,
                readyAtLogicalTime,
                OptionalLong.of(logicalTime)
        );
    }

    public MissionState finishDiscovery() {
        requireStatus(MissionStatus.CLAIM_PENDING_DISCOVERY);
        return with(MissionStatus.CLAIMED, readyAtLogicalTime, resolvedAtLogicalTime);
    }

    public MissionState cancel(long logicalTime) {
        if (status != MissionStatus.ACTIVE && status != MissionStatus.READY) {
            throw new IllegalStateException("Only active or ready missions may be cancelled");
        }
        if (logicalTime < startedAtLogicalTime) {
            throw new IllegalArgumentException("Cancellation precedes mission start");
        }
        return with(MissionStatus.CANCELLED, readyAtLogicalTime, OptionalLong.of(logicalTime));
    }

    public int netResearchCredit() {
        return researchYield - (discoveryRequired ? discoveryCost : 0);
    }

    private MissionState with(
            MissionStatus nextStatus,
            OptionalLong nextReadyAt,
            OptionalLong nextResolvedAt
    ) {
        return new MissionState(
                schemaVersion,
                missionId,
                satelliteId,
                ownerId,
                definitionId,
                targetBodyId,
                startedAtLogicalTime,
                completesAtLogicalTime,
                researchYield,
                discoveryCost,
                discoveryRequired,
                nextStatus,
                nextReadyAt,
                nextResolvedAt
        );
    }

    private void requireStatus(MissionStatus required) {
        if (status != required) {
            throw new IllegalStateException("Mission is " + status + ", expected " + required);
        }
    }

    private static void validatePhase(
            MissionStatus status,
            long startedAt,
            long completesAt,
            OptionalLong readyAt,
            OptionalLong resolvedAt
    ) {
        if (readyAt.isPresent() && readyAt.getAsLong() < completesAt) {
            throw new IllegalArgumentException("Mission readiness precedes its deadline");
        }
        if (resolvedAt.isPresent() && resolvedAt.getAsLong() < startedAt) {
            throw new IllegalArgumentException("Mission resolution precedes its start");
        }
        switch (status) {
            case ACTIVE -> {
                if (readyAt.isPresent() || resolvedAt.isPresent()) {
                    throw new IllegalArgumentException("Active mission cannot have terminal times");
                }
            }
            case READY -> {
                if (readyAt.isEmpty() || resolvedAt.isPresent()) {
                    throw new IllegalArgumentException("Ready mission has inconsistent times");
                }
            }
            case CLAIM_PENDING_DISCOVERY, CLAIMED -> {
                if (readyAt.isEmpty() || resolvedAt.isEmpty()
                        || resolvedAt.getAsLong() < readyAt.getAsLong()) {
                    throw new IllegalArgumentException("Claimed mission has inconsistent times");
                }
            }
            case CANCELLED -> {
                if (resolvedAt.isEmpty()) {
                    throw new IllegalArgumentException("Cancelled mission requires a resolution time");
                }
            }
        }
    }
}
