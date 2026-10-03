package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.GeodeStructure;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.SurfaceWorldgen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Venus structures generated for real (C15b review R1-H1): the server generates a far square of Venus with its
 * structures, and the checks read the saved blocks.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VenusStructureGameTests {
    /** 24 x 24 chunks (576), three by three of the geode set's 8-chunk spacing: nine geode attempts. */
    private static final int FIRST_CHUNK = 1_600;
    private static final int CHUNKS = 24;

    private VenusStructureGameTests() {
    }

    /**
     * No geode block lies within {@link GeodeStructure#COVER} blocks of the top of any column: no geode shell, no
     * geode ore and no cave air (Venus has neither ores nor caves of its own), on the cliffs too. At least three
     * geodes start in the square, and each is hollow at its centre.
     */
    @GameTest(template = "empty", batch = "venus_geode_cover", timeoutTicks = 6_000)
    public static void realVenusGeodesKeepTheirCoverOverEveryColumn(GameTestHelper helper) {
        ServerLevel venus = helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.VENUS));
        helper.assertTrue(venus != null, "Venus has no Level");
        int min = FIRST_CHUNK * 16;
        int max = (FIRST_CHUNK + CHUNKS) * 16 - 1;
        BoundingBox area = new BoundingBox(min, 0, min, max, 255, max);
        PlanetSurfaceGameTests.whenLoaded(helper, venus, area, () -> {
            List<BoundingBox> geodes = new ArrayList<>();
            for (int cx = FIRST_CHUNK; cx < FIRST_CHUNK + CHUNKS; cx++) {
                for (int cz = FIRST_CHUNK; cz < FIRST_CHUNK + CHUNKS; cz++) {
                    ChunkAccess chunk = venus.getChunk(cx, cz);
                    for (Map.Entry<Structure, StructureStart> start : chunk.getAllStarts().entrySet()) {
                        if (start.getKey().type() == SurfaceWorldgen.GEODE.get() && start.getValue().isValid()) {
                            geodes.add(start.getValue().getBoundingBox());
                        }
                    }
                }
            }
            helper.assertTrue(geodes.size() >= 3, "Only " + geodes.size() + " geodes started in the square");
            for (BoundingBox geode : geodes) {
                BlockPos centre = geode.getCenter();
                if (area.isInside(centre)) {
                    BlockState middle = venus.getBlockState(centre);
                    helper.assertTrue(middle.is(Blocks.CAVE_AIR), "The geode at " + centre + " is not hollow: " + middle);
                }
            }
            for (int x = min; x <= max; x++) {
                for (int z = min; z <= max; z++) {
                    int top = venus.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    for (int depth = 0; depth <= GeodeStructure.COVER; depth++) {
                        BlockPos position = new BlockPos(x, top - depth, z);
                        BlockState state = venus.getBlockState(position);
                        helper.assertTrue(!state.is(SurfaceContent.GEODE_SHELL.get()) && !state.is(Blocks.CAVE_AIR)
                                        && !state.is(SurfaceWorldgen.GEODE_ORES),
                                "A geode block " + depth + " below the top at " + position + ": " + state);
                    }
                }
            }
        });
    }
}
