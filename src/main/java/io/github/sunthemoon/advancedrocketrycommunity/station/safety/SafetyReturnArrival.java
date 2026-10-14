package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * ADR-066 section 3.3: one loaded-cell safety probe of a station arrival, the feet cell above the landing pad. Step 0
 * is the floor below the feet, step 1 the feet and step 2 the head. A probe reads only a chunk that
 * {@code getChunkNow} already holds, through that chunk, so an unloaded arrival is skipped without any world access
 * that loads a chunk, and nothing adds a ticket.
 */
public final class SafetyReturnArrival {
    private SafetyReturnArrival() {
    }

    /** True when the probed cell is loaded and safe. */
    public static boolean probe(ServerLevel level, SafetyReturnQuery.Target target, int step) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(target, "target");
        if (step < 0 || step >= SafetyReturnQuery.PROBE_STEPS) {
            throw new IllegalArgumentException("Unknown probe step " + step);
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(target.x() >> 4, target.z() >> 4);
        if (chunk == null) {
            return false;
        }
        int y = target.y() - 1 + step;
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos cell = new BlockPos(target.x(), y, target.z());
        if (!level.getWorldBorder().isWithinBounds(cell)) {
            return false;
        }
        BlockState state = chunk.getBlockState(cell);
        if (!state.getFluidState().isEmpty() || hazard(state)) {
            return false;
        }
        return step == 0
                ? state.isFaceSturdy(chunk, cell, Direction.UP)
                : state.getCollisionShape(chunk, cell).isEmpty();
    }

    /** Blocks that hurt, trap or carry away a player standing in or on them. */
    static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.PORTALS)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB) || state.is(Blocks.CACTUS);
    }
}
