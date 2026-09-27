package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Definition capabilities; permission and live destination checks remain server-owned. */
public record CelestialCapabilities(boolean landable, boolean orbitable, boolean gasGiant) {
    public CelestialCapabilities {
        if (landable && gasGiant) {
            throw new IllegalArgumentException("A gas giant cannot be landable");
        }
    }

    public static CelestialCapabilities legacy(ResourceKey<Level> level) {
        return new CelestialCapabilities(!CelestialIds.SPACE_LEVEL.equals(level), true, false);
    }
}
