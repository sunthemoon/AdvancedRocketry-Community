package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/** Manager-owned synchronous installation context: the complete inert root precedes registration/onLoad. */
final class SealedCarrierPlacement {
    private final Thread owner = Thread.currentThread();
    private ServerLevel level;
    private SealedRocketBlockEntity pending;

    boolean place(ServerLevel level, SealedRocketBlockEntity carrier) {
        owner();
        if (pending != null || this.level != null || carrier.hasLevel() || carrier.isRemoved()) {
            throw new IllegalStateException("Sealed placement requires one fresh detached carrier");
        }
        var pos = carrier.getBlockPos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.getBlockState(pos).isAir() || chunk.getBlockEntity(pos) != null) { return false; }
        this.level = level; pending = carrier;
        try {
            return level.setBlock(pos, ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                    && level.getBlockEntity(pos) == carrier && !carrier.isRemoved();
        } finally { pending = null; this.level = null; }
    }
    void onPlace(ServerLevel level, BlockPos pos) {
        owner();
        var carrier = pending;
        if (this.level != level || carrier == null || !carrier.getBlockPos().equals(pos)
                || !level.getBlockState(pos).is(ModBlocks.SEALED_ROCKET_BLOCK.get())) { return; }
        pending = null; // Reentrant registration cannot consume this installation a second time.
        level.setBlockEntity(carrier);
        carrier.setChanged();
    }
    void clear() { owner(); pending = null; level = null; }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed placement requires its owner thread"); }
    }
}
