package io.github.sunthemoon.advancedrocketrycommunity.celestial.context;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Immutable query value; it carries no authority and never requests chunk loading. */
public record WorldLocation(ResourceKey<Level> levelKey, BlockPos position) {
    public WorldLocation {
        Objects.requireNonNull(levelKey, "levelKey");
        position = Objects.requireNonNull(position, "position").immutable();
    }
}
