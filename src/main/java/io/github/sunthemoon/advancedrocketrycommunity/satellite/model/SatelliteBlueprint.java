package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Snapshot of the components a satellite was built from and the stats derived at launch.
 * A legacy blueprint has no components: its stats are a label, not a claim about consumed items (ADR-049 §7).
 */
public record SatelliteBlueprint(List<ResourceLocation> components, boolean legacy, SatelliteStats stats) {
    public static final SatelliteBlueprint LEGACY_DATA = new SatelliteBlueprint(List.of(), true, SatelliteStats.LEGACY_DATA);

    public SatelliteBlueprint {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(stats, "stats");
        components = List.copyOf(components);
        if (legacy != components.isEmpty()) {
            throw new IllegalArgumentException("Only a legacy blueprint has no components");
        }
        if (components.size() > SatelliteLimits.MAX_BLUEPRINT_COMPONENTS) {
            throw new IllegalArgumentException("Blueprint has too many components");
        }
    }
}
