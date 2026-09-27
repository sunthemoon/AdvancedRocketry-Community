package io.github.sunthemoon.advancedrocketrycommunity.api.satellite;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable defaults for a research/discovery mission, not executable reward code.
 * Server datapacks may override these defaults. Each started mission snapshots its
 * values; neither reload nor removal changes rewards already in flight.
 */
public record SatelliteMissionDefinition(int durationTicks, int researchYield, int discoveryCost,
                                         List<ResourceLocation> allowedTargets) {
    public SatelliteMissionDefinition {
        Objects.requireNonNull(allowedTargets, "allowedTargets");
        if (durationTicks < 20 || durationTicks > 72_000
                || researchYield < 1 || researchYield > 10_000
                || discoveryCost < 1 || discoveryCost > researchYield
                || allowedTargets.isEmpty() || allowedTargets.size() > 16) {
            throw new IllegalArgumentException("Satellite mission values exceed fixed bounds");
        }
        allowedTargets = List.copyOf(allowedTargets);
        if (new HashSet<>(allowedTargets).size() != allowedTargets.size()
                || allowedTargets.stream().anyMatch(id -> id.toString().length() > 128)) {
            throw new IllegalArgumentException("Satellite mission targets must be unique bounded IDs");
        }
    }
}
