package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;

public final class ClassicRemovalPolicy {
    public static boolean blocks(BlockGetter level, BlockPos position) {
        var blockEntity = level instanceof ServerLevel server ? loadedEntity(server, position) : level.getBlockEntity(position);
        if (level instanceof ServerLevel server
                && server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) { return true; }
        return blockEntity instanceof ClassicHatchBlockEntity hatch ? hatch.blocksRemoval()
                : blockEntity instanceof ClassicControllerBlockEntity controller && controller.blocksRemoval();
    }
    private static net.minecraft.world.level.block.entity.BlockEntity loadedEntity(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(position);
    }
    private ClassicRemovalPolicy() { }
}
