package io.github.sunthemoon.advancedrocketrycommunity;

import net.minecraft.resources.ResourceLocation;

/** Stable project identity shared by bootstrap code and build-time tests. */
public final class ModIdentity {
    public static final String MOD_ID = "advancedrocketrycommunity";
    public static final String DISPLAY_NAME = "Advanced Rocketry: Community Edition";

    private ModIdentity() {
    }

    public static ResourceLocation id(String path) {
        ResourceLocation location = ResourceLocation.tryBuild(MOD_ID, path);
        if (location == null) {
            throw new IllegalArgumentException("Invalid project resource path: " + path);
        }
        return location;
    }
}
