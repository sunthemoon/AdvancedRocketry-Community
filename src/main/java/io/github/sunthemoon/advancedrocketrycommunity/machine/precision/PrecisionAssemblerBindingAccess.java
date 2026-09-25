package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.LoadedPartBindingAccess;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.MultiblockPartBindingTarget;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Only Precision Assembler ports may participate in its loaded binding transaction. */
final class PrecisionAssemblerBindingAccess implements LoadedPartBindingAccess {
    private final ServerLevel level;

    PrecisionAssemblerBindingAccess(ServerLevel level) {
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
        return blockEntity instanceof PrecisionAssemblerPortBlockEntity port
                && port.acceptsBindingMutations()
                ? Optional.of(port)
                : Optional.empty();
    }
}
