package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable mission snapshot (schema 2) with explicit, replay-safe terminal phases (ADR-050 §3). */
public record MissionState(
        int schemaVersion,
        UUID missionId,
        UUID satelliteId,
        UUID ownerId,
        ResourceLocation definitionId,
        MissionKind kind,
        ResourceLocation targetBodyId,
        Optional<UUID> instanceId,
        long seed,
        long startedAtLogicalTime,
        long completesAtLogicalTime,
        long startEpoch,
        MissionStatus status,
        OptionalLong readyAtLogicalTime,
        OptionalLong resolvedAtLogicalTime,
        String rewardVersion,
        Optional<MissionQuarantine> quarantine,
        MissionPayload payload
) {
    /** Reward version of a {@code data} mission started by v1.6 or later. */
    public static final String DATA_REWARD_VERSION = "data-v1";
    /** Reward version given to schema-1 missions by the root-3 migration (ADR-050 §3). */
    public static final String LEGACY_DATA_REWARD_VERSION = "legacy-data-v1";

    public MissionState {
        Objects.requireNonNull(missionId, "missionId");
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetBodyId, "targetBodyId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(readyAtLogicalTime, "readyAtLogicalTime");
        Objects.requireNonNull(resolvedAtLogicalTime, "resolvedAtLogicalTime");
        Objects.requireNonNull(rewardVersion, "rewardVersion");
        Objects.requireNonNull(quarantine, "quarantine");
        Objects.requireNonNull(payload, "payload");
        if (schemaVersion != SatelliteLimits.MISSION_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported mission schema " + schemaVersion);
        }
        if (startedAtLogicalTime < 0L || completesAtLogicalTime <= startedAtLogicalTime || startEpoch < 0L) {
            throw new IllegalArgumentException("Mission times are invalid");
        }
        if (payload.kind() != kind) {
            throw new IllegalArgumentException("Mission payload does not match its kind");
        }
        if ((kind == MissionKind.ASTEROID) != instanceId.isPresent()) {
            throw new IllegalArgumentException("Only asteroid missions name an instance");
        }
        if (rewardVersion.isEmpty() || rewardVersion.length() > SatelliteLimits.MAX_REWARD_VERSION_CHARS) {
            throw new IllegalArgumentException("Mission reward version is outside its bound");
        }
        if ((status == MissionStatus.QUARANTINED) != quarantine.isPresent()) {
            throw new IllegalArgumentException("Only a quarantined mission carries a quarantine");
        }
        if (status == MissionStatus.CLAIM_PENDING_DISCOVERY
                && !(payload instanceof MissionPayload.Data data && data.discoveryRequired())) {
            throw new IllegalArgumentException("Only data missions that need discovery can wait for it");
        }
        MissionStatus phase = quarantine.map(MissionQuarantine::previousStatus).orElse(status);
        validatePhase(phase, startedAtLogicalTime, completesAtLogicalTime, readyAtLogicalTime, resolvedAtLogicalTime);
    }

    /** Starts a {@code data} mission with no persisted epoch (tests and pure callers). */
    public static MissionState start(
            UUID missionId,
            UUID satelliteId,
            UUID ownerId,
            SatelliteDefinitionSnapshot definition,
            ResourceLocation targetBodyId,
            long logicalTime,
            boolean discoveryRequired
    ) {
        return start(missionId, satelliteId, ownerId, definition, targetBodyId, logicalTime, discoveryRequired, 0L);
    }

    /** Starts a {@code data} mission; its seed is unused and recorded as 0. */
    public static MissionState start(
            UUID missionId,
            UUID satelliteId,
            UUID ownerId,
            SatelliteDefinitionSnapshot definition,
            ResourceLocation targetBodyId,
            long logicalTime,
            boolean discoveryRequired,
            long startEpoch
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
                MissionKind.DATA,
                targetBodyId,
                Optional.empty(),
                0L,
                logicalTime,
                completion,
                startEpoch,
                MissionStatus.ACTIVE,
                OptionalLong.empty(),
                OptionalLong.empty(),
                DATA_REWARD_VERSION,
                Optional.empty(),
                new MissionPayload.Data(definition.researchYield(), definition.discoveryCost(), discoveryRequired)
        );
    }

    public int researchYield() {
        return data().researchYield();
    }

    public int discoveryCost() {
        return data().discoveryCost();
    }

    public boolean discoveryRequired() {
        return payload instanceof MissionPayload.Data data && data.discoveryRequired();
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
                discoveryRequired() ? MissionStatus.CLAIM_PENDING_DISCOVERY : MissionStatus.CLAIMED,
                readyAtLogicalTime,
                OptionalLong.of(logicalTime)
        );
    }

    public MissionState finishDiscovery() {
        requireStatus(MissionStatus.CLAIM_PENDING_DISCOVERY);
        return with(MissionStatus.CLAIMED, readyAtLogicalTime, resolvedAtLogicalTime);
    }

    public MissionState cancel(long logicalTime) {
        MissionStatus phase = phase();
        if (phase != MissionStatus.ACTIVE && phase != MissionStatus.READY) {
            throw new IllegalStateException("Only active or ready missions may be cancelled");
        }
        if (logicalTime < startedAtLogicalTime) {
            throw new IllegalArgumentException("Cancellation precedes mission start");
        }
        return new MissionState(schemaVersion, missionId, satelliteId, ownerId, definitionId, kind, targetBodyId,
                instanceId, seed, startedAtLogicalTime, completesAtLogicalTime, startEpoch, MissionStatus.CANCELLED,
                readyAtLogicalTime, OptionalLong.of(logicalTime), rewardVersion, Optional.empty(), payload);
    }

    /** The status that governs times and transitions: the previous status while quarantined. */
    public MissionStatus phase() {
        return quarantine.map(MissionQuarantine::previousStatus).orElse(status);
    }

    /** ADR-050 section 9: holds an unfinished mission for an operator; its times and payload are kept. */
    public MissionState quarantine(String reason, boolean receiptSeen) {
        if (!status.unfinished() || status == MissionStatus.QUARANTINED) {
            throw new IllegalStateException("Only an unfinished mission can be quarantined");
        }
        return new MissionState(schemaVersion, missionId, satelliteId, ownerId, definitionId, kind, targetBodyId,
                instanceId, seed, startedAtLogicalTime, completesAtLogicalTime, startEpoch, MissionStatus.QUARANTINED,
                readyAtLogicalTime, resolvedAtLogicalTime, rewardVersion,
                Optional.of(new MissionQuarantine(reason, status, receiptSeen)), payload);
    }

    /** Returns a quarantined mission to its previous status. */
    public MissionState releaseQuarantine() {
        requireStatus(MissionStatus.QUARANTINED);
        return new MissionState(schemaVersion, missionId, satelliteId, ownerId, definitionId, kind, targetBodyId,
                instanceId, seed, startedAtLogicalTime, completesAtLogicalTime, startEpoch, phase(),
                readyAtLogicalTime, resolvedAtLogicalTime, rewardVersion, Optional.empty(), payload);
    }

    public int netResearchCredit() {
        return researchYield() - (discoveryRequired() ? discoveryCost() : 0);
    }

    private MissionPayload.Data data() {
        if (payload instanceof MissionPayload.Data data) {
            return data;
        }
        throw new IllegalStateException("Mission " + missionId + " is not a data mission");
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
                kind,
                targetBodyId,
                instanceId,
                seed,
                startedAtLogicalTime,
                completesAtLogicalTime,
                startEpoch,
                nextStatus,
                nextReadyAt,
                nextResolvedAt,
                rewardVersion,
                quarantine,
                payload
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
            case QUARANTINED -> throw new IllegalArgumentException("Quarantine phase must use its previous status");
        }
    }
}
