package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Recorded, sealed space for tests whose exact exit ordinals require empty local and anchor windows. */
final class BayPassengerExitGameTestArea implements AutoCloseable {
    // The anchor ring's collar reaches radius 2; the local/gantry windows and their setup reach radius 4.
    private static final int RADIUS = 5;
    // The lowest candidate's collar reaches -3. The existing fixture clears through +54 above its anchor.
    private static final int BOTTOM = -3;
    private static final int TOP = 55;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final ServerLevel level;
    private final BlockPos origin;
    private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
    private boolean closed;

    private BayPassengerExitGameTestArea(ServerLevel level, BlockPos origin) {
        this.level = level; this.origin = origin.immutable();
    }

    static BayPassengerExitGameTestArea prepare(ServerLevel level, BlockPos origin) {
        var area = new BayPassengerExitGameTestArea(level, origin);
        if (origin.getY() + BOTTOM < level.getMinBuildHeight() || origin.getY() + TOP >= level.getMaxBuildHeight()) {
            throw new IllegalArgumentException("Bay exit fixture area exceeds build height at " + origin);
        }
        // Validate and record the entire bounded area before changing any cell. Never request a chunk load.
        for (int x = -RADIUS; x <= RADIUS; x++) { for (int z = -RADIUS; z <= RADIUS; z++) {
            var chunk = level.getChunkSource().getChunkNow((origin.getX() + x) >> 4, (origin.getZ() + z) >> 4);
            if (chunk == null) { throw new IllegalStateException("Bay exit fixture area would load a column at " + origin.offset(x, 0, z)); }
            for (int y = BOTTOM; y <= TOP; y++) {
                var at = origin.offset(x, y, z);
                if (level.getBlockEntity(at) != null) { throw new IllegalStateException("Bay exit fixture area would replace a block entity at " + at); }
                area.original.put(at, chunk.getBlockState(at));
            }
        } }
        // Finish the shell before clearing the interior, so already-scheduled fluid ticks cannot refill the windows.
        try {
            area.original.keySet().stream().filter(area::shell)
                    .forEach(at -> level.setBlock(at, Blocks.GLASS.defaultBlockState(), FLAGS));
            area.original.keySet().stream().filter(at -> !area.shell(at))
                    .forEach(at -> level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS));
        } catch (RuntimeException | Error failure) {
            area.close();
            throw failure;
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_BAY_EXIT_AREA installed origin={} cells={}", origin, area.original.size());
        return area;
    }

    private boolean shell(BlockPos at) {
        return Math.abs(at.getX() - origin.getX()) == RADIUS || Math.abs(at.getZ() - origin.getZ()) == RADIUS
                || at.getY() == origin.getY() + BOTTOM || at.getY() == origin.getY() + TOP;
    }

    @Override public void close() {
        if (closed) { return; }
        closed = true;
        original.forEach((at, state) -> level.setBlock(at, state, FLAGS));
        long unrestored = original.entrySet().stream().filter(entry -> !level.getBlockState(entry.getKey()).equals(entry.getValue())).count();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_BAY_EXIT_AREA restored origin={} cells={} unrestored={}", origin, original.size(), unrestored);
        if (unrestored != 0) { throw new IllegalStateException("Bay exit fixture area left " + unrestored + " cells changed at " + origin); }
    }
}
