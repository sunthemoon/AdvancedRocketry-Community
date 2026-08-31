package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Immutable display-only client view; it contains no authoritative world state. */
public record CelestialSnapshot(
        int schemaVersion,
        List<Entry> entries
) {
    public CelestialSnapshot {
        entries = List.copyOf(entries);
    }

    public record Entry(
            ResourceLocation bodyId,
            Optional<ResourceLocation> parentId,
            ResourceLocation levelId,
            double gravityMultiplier,
            boolean vacuum,
            boolean breathable,
            ResourceLocation atmosphereProfile,
            ResourceLocation visualProfile
    ) {
    }
}
