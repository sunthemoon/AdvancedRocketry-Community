package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Moon's first fixed pad (around (8, 80, 8)) is shared by every test that lands there or prepares the developer
 * platform. Each such test first gives the area a known shape: moon turf from y 5 up to a chosen top, open air from
 * there to y 100, so no test depends on what another left behind.
 */
final class MoonPadArea {
    /** Clear above a rocket arriving at y 80. */
    private static final int CLEAR_TO = 100;

    private MoonPadArea() {
    }

    /** Fills a square of the given reach around the pad with turf up to {@code top} and clears everything above. */
    static void shape(ServerLevel moon, int reach, int top) {
        BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
        BlockState turf = SurfaceContent.MOON_TURF.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int y = 5; y <= CLEAR_TO; y++) {
                    moon.setBlock(position.set(pad.getX() + dx, y, pad.getZ() + dz), y <= top ? turf : air,
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
