package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * W304: a contained landing pad for the rejoin GameTests, so that their placement does not depend on the terrain.
 *
 * <p>The placement takes each candidate's height from the Overworld surface heightmap. A floor that a fixture builds
 * inside its GameTest structure, below the natural surface, is invisible to it: the terrain above the structure decides,
 * and where that is sea every pad-ring candidate stands on water and the placement falls back to the ring around the
 * world spawn, which other tests and other seeds change (W301F). This pad lies above every natural surface, over every
 * column of the pad ring and of its 3-by-3 safety envelopes, so it is their surface and no candidate reaches the spawn
 * ring.
 *
 * <ul>
 *   <li>Two stone layers: a fixture that removes a candidate's support (Pre, Final, Residual) leaves that column standing
 *       on the lower layer, not on the terrain below.</li>
 *   <li>Four air layers: a fixture that sets stone at a candidate's feet (Ack) raises that candidate by one block, and its
 *       envelope still lies inside the pad.</li>
 *   <li>Every cell is recorded before the first write and restored by {@link #close()}. A fixture closes its pad on success
 *       and on failure. Each batch's {@code AfterBatch} hook calls {@link #closeAbandoned()}, which restores a pad that a
 *       failed or timed-out test left open.</li>
 *   <li>Both writes use {@link #FLAGS}, so no neighbour is notified and nothing can flow. The columns are the test's own,
 *       and the pad refuses to start if any of their chunks is not already loaded, so it never loads a chunk.</li>
 * </ul>
 */
public final class RocketRejoinFixturePad implements AutoCloseable {
    /** The upper stone layer, above every natural surface. */
    public static final int TOP = 300;
    /** The feet of a candidate standing on the pad. */
    public static final int SURFACE = TOP + 1;
    /** The candidate ring's radius (4) plus the safety envelope's (1), around the batch footprint. */
    static final int MARGIN = 4 + 1;
    private static final int STONE_LAYERS = 2;
    private static final int AIR_LAYERS = 4;
    /** Bounds the pad: a footprint wider than a rocket fixture's is a fixture error. */
    private static final int MAX_SPAN = 32;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Pads not yet closed. GameTest batches run one at a time on the server thread, and AfterBatch empties this. */
    private static final List<RocketRejoinFixturePad> OPEN = new ArrayList<>();

    private final ServerLevel level;
    private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
    private final String bounds;
    private boolean closed;

    private RocketRejoinFixturePad(ServerLevel level, String bounds) {
        this.level = level; this.bounds = bounds;
    }

    /** Builds the pad under the candidate ring around a batch footprint, {@code min..max} in x and z. */
    public static RocketRejoinFixturePad around(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        int x0 = minX - MARGIN, x1 = maxX + MARGIN, z0 = minZ - MARGIN, z1 = maxZ + MARGIN;
        int y0 = TOP - STONE_LAYERS + 1, y1 = TOP + AIR_LAYERS;
        if (maxX < minX || maxZ < minZ || x1 - x0 >= MAX_SPAN || z1 - z0 >= MAX_SPAN
                || y0 < level.getMinBuildHeight() || y1 >= level.getMaxBuildHeight()) {
            throw new IllegalArgumentException("Rejoin fixture pad outside its bounds: x=" + minX + ".." + maxX + " z=" + minZ + ".." + maxZ);
        }
        var pad = new RocketRejoinFixturePad(level, "x=" + x0 + ".." + x1 + " z=" + z0 + ".." + z1 + " y=" + y0 + ".." + y1);
        // Preflight every column and cell before the first write.
        for (int x = x0; x <= x1; x++) { for (int z = z0; z <= z1; z++) {
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                throw new IllegalStateException("Rejoin fixture pad would load the chunk of column " + x + ", " + z);
            }
            for (int y = y0; y <= y1; y++) {
                var at = new BlockPos(x, y, z);
                // A block entity's data would not survive the restore.
                if (level.getBlockEntity(at) != null) { throw new IllegalStateException("Rejoin fixture pad would replace a block entity at " + at); }
                pad.original.put(at, level.getBlockState(at));
            }
        } }
        OPEN.add(pad);
        pad.original.keySet().forEach(at -> level.setBlock(at, (at.getY() <= TOP ? Blocks.STONE : Blocks.AIR).defaultBlockState(), FLAGS));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_REJOIN_FIXTURE_PAD installed {} cells={}", pad.bounds, pad.original.size());
        return pad;
    }

    /** For a batch's AfterBatch hook: restores every pad that a failed or timed-out test left open. */
    public static void closeAbandoned() {
        List.copyOf(OPEN).forEach(RocketRejoinFixturePad::close);
    }

    /** Restores every recorded cell, including the cells a test changed on the pad. Repeated calls do nothing. */
    @Override public void close() {
        if (closed) { return; }
        closed = true; OPEN.remove(this);
        original.forEach((at, state) -> level.setBlock(at, state, FLAGS));
        long unrestored = original.entrySet().stream().filter(e -> !level.getBlockState(e.getKey()).equals(e.getValue())).count();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_REJOIN_FIXTURE_PAD restored {} cells={} unrestored={}", bounds, original.size(), unrestored);
        if (unrestored != 0) { throw new IllegalStateException("Rejoin fixture pad left " + unrestored + " cells changed at " + bounds); }
    }
}
