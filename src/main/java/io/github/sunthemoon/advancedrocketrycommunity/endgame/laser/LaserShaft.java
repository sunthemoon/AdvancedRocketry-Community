package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/**
 * ADR-055 section 3 shaft geometry and layer classification, without a world. The footprint is the 3 × 3 column
 * centred on the marker, cells in the order {@code (mx − 1 + i mod 3, mz − 1 + i div 3)}; layers run from
 * {@code my − 1} down to {@code max(minBuildHeight + 1, my − maxDepth)}. The marker must sit at local x and z 2..13 of
 * its chunk, so every block a removal updates is in that chunk.
 */
public final class LaserShaft {
    public static final int CELLS = 9;
    public static final int MIN_LOCAL = 2;
    public static final int MAX_LOCAL = 13;

    private LaserShaft() {
    }

    public static boolean footprintInsideChunk(BlockPos marker) {
        int x = marker.getX() & 15;
        int z = marker.getZ() & 15;
        return x >= MIN_LOCAL && x <= MAX_LOCAL && z >= MIN_LOCAL && z <= MAX_LOCAL;
    }

    public static int firstLayer(BlockPos marker) {
        return marker.getY() - 1;
    }

    public static int floor(BlockPos marker, int minBuildHeight, int maxDepth) {
        return Math.max(minBuildHeight + 1, marker.getY() - maxDepth);
    }

    /** The nine cells of one layer, in classification order. */
    public static List<BlockPos> layer(BlockPos marker, int y) {
        List<BlockPos> cells = new ArrayList<>(CELLS);
        for (int i = 0; i < CELLS; i++) {
            cells.add(new BlockPos(marker.getX() - 1 + i % 3, y, marker.getZ() - 1 + i / 3));
        }
        return cells;
    }

    /** What a footprint cell is, before anything changes. */
    public enum Cell {
        AIR,
        /** A block entity, a destroy speed below 0, or the {@code laser_drill_immune} tag. */
        IMMUNE,
        /** A pure fluid ({@code LiquidBlock}); fluids are never deleted. */
        FLUID,
        BREAKABLE
    }

    /**
     * Classifies a whole layer before any change: the first cell in order that is immune or a fluid stops the layer
     * with {@code BLOCKED_IMMUNE} or {@code BLOCKED_FLUID}; otherwise {@code OK}, and the breakable cells go on to
     * their break events.
     */
    public static EndgameCode classify(List<Cell> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.size() != CELLS) {
            throw new IllegalArgumentException("A layer has nine cells");
        }
        for (Cell cell : cells) {
            if (cell == Cell.IMMUNE) {
                return EndgameCode.BLOCKED_IMMUNE;
            }
            if (cell == Cell.FLUID) {
                return EndgameCode.BLOCKED_FLUID;
            }
        }
        return EndgameCode.OK;
    }
}
