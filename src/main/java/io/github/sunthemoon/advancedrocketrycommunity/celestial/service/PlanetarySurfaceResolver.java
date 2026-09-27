package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Catalog-only surface identity; never looks up or loads terrain. */
public final class PlanetarySurfaceResolver {
    private PlanetarySurfaceResolver() {
    }

    public static Optional<CelestialBodyDefinition> find(CelestialCatalog catalog, ResourceKey<Level> level, boolean arrival) {
        if (level.equals(CelestialIds.SPACE_LEVEL)) {
            return Optional.empty();
        }
        var candidates = catalog.candidatesForLevel(level);
        if (candidates.size() != 1) {
            return Optional.empty();
        }
        var body = candidates.get(0);
        return (!arrival || body.supportsSurfaceArrival()) ? Optional.of(body) : Optional.empty();
    }
}
