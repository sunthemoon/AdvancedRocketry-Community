package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.Level;

/** Canonical v0.3.0 definitions used only to generate the built-in data pack. */
public final class CelestialDefaults {
    private CelestialDefaults() {
    }

    public static List<CelestialBodyDefinition> definitions() {
        return List.of(
                new CelestialBodyDefinition(
                        CelestialIds.EARTH_ID,
                        Optional.empty(),
                        Level.OVERWORLD,
                        1.0D,
                        new AtmosphereDefinition(1.0D, true, 288.0D, ModIdentity.id("earth")),
                        new OrbitDefinition(0L, 0L, 0.0D),
                        ModIdentity.id("earth")
                ),
                new CelestialBodyDefinition(
                        CelestialIds.MOON_ID,
                        Optional.of(CelestialIds.EARTH_ID),
                        CelestialIds.MOON_LEVEL,
                        0.165D,
                        new AtmosphereDefinition(0.0D, false, 220.0D, ModIdentity.id("vacuum")),
                        new OrbitDefinition(384_400L, 2_360_591L, 5.145D),
                        ModIdentity.id("moon")
                ),
                new CelestialBodyDefinition(
                        CelestialIds.SPACE_ID,
                        Optional.of(CelestialIds.EARTH_ID),
                        CelestialIds.SPACE_LEVEL,
                        0.0D,
                        new AtmosphereDefinition(0.0D, false, 3.0D, ModIdentity.id("vacuum")),
                        new OrbitDefinition(1L, 1L, 0.0D),
                        ModIdentity.id("space")
                )
        );
    }
}
