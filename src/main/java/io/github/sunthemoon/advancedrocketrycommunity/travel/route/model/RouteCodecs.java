package io.github.sunthemoon.advancedrocketrycommunity.travel.route.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

final class RouteCodecs {
    static final Codec<ResourceLocation> RESOURCE_LOCATION = ResourceLocation.CODEC.flatXmap(
            RouteCodecs::boundedLocation,
            RouteCodecs::boundedLocation
    );

    private RouteCodecs() {
    }

    private static DataResult<ResourceLocation> boundedLocation(ResourceLocation value) {
        if (value.toString().length() > RouteLimits.MAX_RESOURCE_LOCATION_CHARS) {
            return DataResult.error(() -> "Route resource location exceeds 128 characters");
        }
        return DataResult.success(value);
    }
}
