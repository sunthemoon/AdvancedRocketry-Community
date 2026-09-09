package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** World-scoped controller key for a per-server dirty queue. */
public record MultiblockControllerKey(ResourceKey<Level> level, BlockPos position) {
    public MultiblockControllerKey {
        Objects.requireNonNull(level, "level");
        position = Objects.requireNonNull(position, "position").immutable();
    }
}
