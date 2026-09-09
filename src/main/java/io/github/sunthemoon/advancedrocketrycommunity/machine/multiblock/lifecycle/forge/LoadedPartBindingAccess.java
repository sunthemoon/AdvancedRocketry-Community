package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Loaded-only BlockEntity lookup boundary used by the binding transaction. */
public interface LoadedPartBindingAccess {
    ResourceKey<Level> levelKey();

    boolean isLoaded(BlockPos position);

    Optional<MultiblockPartBindingTarget> loadedTarget(BlockPos position);
}
