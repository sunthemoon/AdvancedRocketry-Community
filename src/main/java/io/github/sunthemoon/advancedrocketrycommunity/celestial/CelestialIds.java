package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Stable logical-body and fixed-Level identities for the v0.3.0 slice. */
public final class CelestialIds {
    public static final ResourceLocation EARTH_ID = ModIdentity.id("earth");
    public static final ResourceLocation MOON_ID = ModIdentity.id("moon");
    public static final ResourceLocation SPACE_ID = ModIdentity.id("space");

    public static final ResourceKey<Level> MOON_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ModIdentity.id("moon")
    );
    public static final ResourceKey<Level> SPACE_LEVEL = ResourceKey.create(
            Registries.DIMENSION,
            ModIdentity.id("space")
    );

    private CelestialIds() {
    }
}
