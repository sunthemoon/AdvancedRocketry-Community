package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
     * chunks.
     */
    static boolean settled(ServerLevel level) {
        return AtmosphereRuntime.metrics(level)
                .map(metrics -> metrics.dirtyPositions() == 0 && metrics.activeScanTasks() <= metrics.pendingScanTasks())
                .orElse(false);
    }

    static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
