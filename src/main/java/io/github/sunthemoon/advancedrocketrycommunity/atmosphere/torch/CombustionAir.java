package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Whether one loaded cell sustains combustion, from the authoritative instrument reading the detector uses. Only a
 * breathable cell is combustible. An unavailable reading (an unloaded cell, or a Level with no mapped body) is
 * unmanaged: the rules neither convert nor suppress anything there.
 */
enum CombustionAir {
    COMBUSTIBLE, NONCOMBUSTIBLE, PENDING, UNMANAGED;

    static CombustionAir at(ServerLevel level, BlockPos pos) {
        return switch (AtmosphereAnalyzerRuntime.readAt(level, pos).state()) {
            case BREATHABLE -> COMBUSTIBLE;
            case NON_BREATHABLE -> NONCOMBUSTIBLE;
            case PENDING -> PENDING;
            case UNAVAILABLE, DISABLED -> UNMANAGED;
        };
    }

    /**
     * A block change dirties the supplied room around it, and its cells read vacuum until the rescan reaches them. A
     * noncombustible reading is final only with no dirty backlog and no scan other than those waiting on unloaded
     * chunks. FULL chunks can precede their block-entity tickers after loading, so an empty scan queue alone does
     * not establish vacuum in a roofed cell. Wait for native ticker readiness there. An exposed column already
     * establishes the ambient noncombustible reading, even without a ticking chunk or a nearby player.
     */
    static boolean settled(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        boolean ticking = chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                && level.shouldTickBlocksAt(ChunkPos.asLong(pos)) && level.areEntitiesLoaded(ChunkPos.asLong(pos));
        // Use current loaded geometry, not asynchronous sky light (which can shine through a sealed glass roof).
        boolean exposed = pos.getY() > chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                pos.getX() & 15, pos.getZ() & 15);
        if (!ticking && !exposed) {
            return false;
        }
        return AtmosphereRuntime.metrics(level)
                .map(metrics -> metrics.dirtyPositions() == 0 && metrics.activeScanTasks() <= metrics.pendingScanTasks())
                .orElse(false);
    }

    static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
