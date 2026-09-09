package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.LoadedPartBindingAccess;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.MultiblockPartBindingTarget;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loaded-only binding lookup for one server level. */
final class RollingMachineBindingAccess implements LoadedPartBindingAccess {
    private final ServerLevel level;

    RollingMachineBindingAccess(ServerLevel level) {
        this.level = level;
    }

    @Override
    public ResourceKey<Level> levelKey() {
        return level.dimension();
    }

    @Override
    public boolean isLoaded(BlockPos position) {
        return level.hasChunkAt(position);
    }

    @Override
    public Optional<MultiblockPartBindingTarget> loadedTarget(BlockPos position) {
        if (!level.hasChunkAt(position)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof RollingMachinePortBlockEntity port
                && !port.acceptsBindingMutations()) {
            return Optional.empty();
        }
        return blockEntity instanceof MultiblockPartBindingTarget target
                ? Optional.of(target)
                : Optional.empty();
    }
}
