package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** A logical asteroid with no world region or coordinates (ADR-051 §1–§2). */
public record AsteroidInstance(
        int schemaVersion,
        UUID instanceId,
        UUID ownerId,
        ResourceLocation system,
        ResourceLocation asteroidType,
        String tableVersion,
        String candidateFingerprint,
        long seed,
        List<RewardEntry> yield,
        long createdAt,
        OptionalLong expiresAt,
        InstanceState state,
        UUID sourceMission,
        Optional<UUID> allocatedMission
) {
    public AsteroidInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(asteroidType, "asteroidType");
        Objects.requireNonNull(tableVersion, "tableVersion");
        Objects.requireNonNull(candidateFingerprint, "candidateFingerprint");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(sourceMission, "sourceMission");
        Objects.requireNonNull(allocatedMission, "allocatedMission");
        if (schemaVersion != SatelliteLimits.INSTANCE_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported asteroid instance schema " + schemaVersion);
        }
        if (!MissionPayload.HEX16.matcher(tableVersion).matches()
                || !MissionPayload.HEX16.matcher(candidateFingerprint).matches()) {
            throw new IllegalArgumentException("Asteroid instance versions must be 16 hex characters");
        }
        yield = RewardEntry.validated(yield, SatelliteLimits.MAX_REWARD_ENTRIES, SatelliteLimits.MAX_YIELD_ITEMS);
        if (createdAt < 0L || (expiresAt.isPresent() && expiresAt.getAsLong() < createdAt)) {
            throw new IllegalArgumentException("Asteroid instance times are invalid");
        }
        switch (state) {
            case PENDING -> {
                if (expiresAt.isPresent() || allocatedMission.isPresent()) {
                    throw new IllegalArgumentException("A pending instance has no expiry or allocation");
                }
            }
            case AVAILABLE, EXPIRED -> {
                if (expiresAt.isEmpty() || allocatedMission.isPresent()) {
                    throw new IllegalArgumentException("An available or expired instance has an expiry and no allocation");
                }
            }
            case ALLOCATED -> {
                if (expiresAt.isEmpty() || allocatedMission.isEmpty()) {
                    throw new IllegalArgumentException("An allocated instance has an expiry and an allocation");
                }
            }
            case DEPLETED, QUARANTINED -> {
                // Retained for audit; no further field constraint.
            }
        }
    }
}
