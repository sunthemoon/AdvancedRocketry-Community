package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** ServerLevel adapter that never asks Minecraft to load a missing chunk. */
public final class ServerLevelPartBindingAccess implements LoadedPartBindingAccess {
    private final ServerLevel level;

    public ServerLevelPartBindingAccess(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
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
        return level.getBlockEntity(position) instanceof MultiblockPartBindingTarget target
                ? Optional.of(target)
                : Optional.empty();
    }
}
