package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Data-pack definition whose values are snapshotted into each new mission. */
public record SatelliteDefinition(
        int schemaVersion,
        ResourceLocation id,
        int missionDurationTicks,
        int researchYield,
        int discoveryCost,
        List<ResourceLocation> allowedTargets
) {
    private static final Codec<List<ResourceLocation>> TARGETS_CODEC =
            BoundedCelestialCodecs.RESOURCE_LOCATION.listOf().flatXmap(
                    SatelliteDefinition::validateTargets,
                    SatelliteDefinition::validateTargets
            );

    private static final Codec<SatelliteDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(
                    SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                    SatelliteLimits.DEFINITION_SCHEMA_VERSION
            ).fieldOf("schema_version").forGetter(SatelliteDefinition::schemaVersion),
            BoundedCelestialCodecs.RESOURCE_LOCATION
                    .fieldOf("id")
                    .forGetter(SatelliteDefinition::id),
            Codec.intRange(
                    SatelliteLimits.MIN_MISSION_DURATION_TICKS,
                    SatelliteLimits.MAX_MISSION_DURATION_TICKS
            ).fieldOf("mission_duration_ticks").forGetter(SatelliteDefinition::missionDurationTicks),
            Codec.intRange(1, SatelliteLimits.MAX_RESEARCH_PER_MISSION)
                    .fieldOf("research_yield")
                    .forGetter(SatelliteDefinition::researchYield),
            Codec.intRange(1, SatelliteLimits.MAX_RESEARCH_PER_MISSION)
                    .fieldOf("discovery_cost")
                    .forGetter(SatelliteDefinition::discoveryCost),
            TARGETS_CODEC.fieldOf("allowed_targets").forGetter(SatelliteDefinition::allowedTargets)
    ).apply(instance, SatelliteDefinition::new));

    public static final Codec<SatelliteDefinition> CODEC = BoundedCelestialCodecs.validated(
            RAW_CODEC,
            SatelliteDefinition::validate
    );

    public SatelliteDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(allowedTargets, "allowedTargets");
        allowedTargets = List.copyOf(allowedTargets);
        if (schemaVersion != SatelliteLimits.DEFINITION_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported satellite definition schema " + schemaVersion);
        }
        if (missionDurationTicks < SatelliteLimits.MIN_MISSION_DURATION_TICKS
                || missionDurationTicks > SatelliteLimits.MAX_MISSION_DURATION_TICKS) {
            throw new IllegalArgumentException("Mission duration is outside the fixed bounds");
        }
        if (researchYield <= 0 || researchYield > SatelliteLimits.MAX_RESEARCH_PER_MISSION) {
            throw new IllegalArgumentException("Research yield is outside the fixed bounds");
        }
        if (discoveryCost <= 0 || discoveryCost > SatelliteLimits.MAX_RESEARCH_PER_MISSION) {
            throw new IllegalArgumentException("Discovery cost is outside the fixed bounds");
        }
        if (allowedTargets.isEmpty()
                || allowedTargets.size() > SatelliteLimits.MAX_TARGETS_PER_DEFINITION
                || new HashSet<>(allowedTargets).size() != allowedTargets.size()) {
            throw new IllegalArgumentException("Allowed targets must be non-empty, unique, and bounded");
        }
        if (researchYield < discoveryCost) {
            throw new IllegalArgumentException("Research yield cannot be lower than discovery cost");
        }
    }

    public boolean allows(ResourceLocation targetBodyId) {
        return allowedTargets.contains(Objects.requireNonNull(targetBodyId, "targetBodyId"));
    }

    private static DataResult<List<ResourceLocation>> validateTargets(List<ResourceLocation> targets) {
        if (targets.isEmpty()) {
            return DataResult.error(() -> "Satellite definition must allow at least one target");
        }
        if (targets.size() > SatelliteLimits.MAX_TARGETS_PER_DEFINITION) {
            return DataResult.error(() -> "Satellite definition exceeds the target limit");
        }
        if (new HashSet<>(targets).size() != targets.size()) {
            return DataResult.error(() -> "Satellite definition contains duplicate targets");
        }
        return DataResult.success(List.copyOf(targets));
    }

    private static DataResult<SatelliteDefinition> validate(SatelliteDefinition definition) {
        if (definition.researchYield < definition.discoveryCost) {
            return DataResult.error(() -> "Research yield cannot be lower than discovery cost");
        }
        return DataResult.success(definition);
    }
}
