package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** The immutable subset copied from a definition when a mission starts. */
public record SatelliteDefinitionSnapshot(
        ResourceLocation definitionId,
        int missionDurationTicks,
        int researchYield,
        int discoveryCost,
        List<ResourceLocation> allowedTargets
) {
    public SatelliteDefinitionSnapshot {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(allowedTargets, "allowedTargets");
        allowedTargets = List.copyOf(allowedTargets);
    }

    public static SatelliteDefinitionSnapshot from(SatelliteDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new SatelliteDefinitionSnapshot(
                definition.id(),
                definition.missionDurationTicks(),
                definition.researchYield(),
                definition.discoveryCost(),
                definition.allowedTargets()
        );
    }
}
