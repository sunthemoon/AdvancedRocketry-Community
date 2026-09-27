package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalConditions;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Constant-time view of the active catalog's gravity and atmosphere profile. */
public final class CelestialEnvironmentService {
    private final CelestialCatalogManager catalogs;

    public CelestialEnvironmentService(CelestialCatalogManager catalogs) {
        this.catalogs = catalogs;
    }

    public Optional<EnvironmentProfile> forLevel(ResourceKey<Level> levelKey) {
        return catalogs.current()
                .flatMap(catalog -> catalog.forLevel(levelKey))
                .map(definition -> new EnvironmentProfile(
                        definition.id(),
                        definition.gravityMultiplier(),
                        definition.atmosphere().pressure() == 0.0D,
                        definition.atmosphere().breathable(),
                        definition.atmosphere().profile()
                ));
    }

    public EnvironmentalConditions surfaceExposure(ResourceKey<Level> levelKey) {
        if (levelKey.equals(CelestialIds.SPACE_LEVEL)) {
            return EnvironmentalConditions.DISABLED;
        }
        return catalogs.current().flatMap(catalog -> catalog.forLevel(levelKey))
                .map(body -> new EnvironmentalConditions(body.environmentEffects(), body.atmosphere().pressure(),
                        body.atmosphere().temperatureKelvin(), body.solarIntensity()))
                .orElse(EnvironmentalConditions.DISABLED);
    }

    public record EnvironmentProfile(
            ResourceLocation bodyId,
            double gravityMultiplier,
            boolean vacuum,
            boolean breathable,
            ResourceLocation atmosphereProfile
    ) {
    }
}
