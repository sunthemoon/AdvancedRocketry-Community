package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Shared bounded primitives for data-pack and network-facing celestial data. */
public final class BoundedCelestialCodecs {
    public static final int MAX_RESOURCE_LOCATION_CHARS = 128;

    public static final Codec<ResourceLocation> RESOURCE_LOCATION = ResourceLocation.CODEC.flatXmap(
            BoundedCelestialCodecs::validateResourceLocation,
            BoundedCelestialCodecs::validateResourceLocation
    );

    public static final Codec<ResourceKey<Level>> LEVEL_KEY = RESOURCE_LOCATION.xmap(
            id -> ResourceKey.create(Registries.DIMENSION, id),
            ResourceKey::location
    );

    private BoundedCelestialCodecs() {
    }

    public static <T> Codec<T> validated(
            Codec<T> codec,
            Function<T, DataResult<T>> validator
    ) {
        return codec.flatXmap(validator, validator);
    }

    private static DataResult<ResourceLocation> validateResourceLocation(ResourceLocation value) {
        if (value.toString().length() > MAX_RESOURCE_LOCATION_CHARS) {
            return DataResult.error(() -> "Resource location exceeds 128 characters: " + value);
        }
        return DataResult.success(value);
    }
}
