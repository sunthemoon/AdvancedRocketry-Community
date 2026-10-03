package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.GeodeStructure;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.SurfaceWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.VolcanoPiece;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.VolcanoShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Venus structures from real generation points (C15b review R1-H1, R1-L1): the server generates a far square of
 * Venus with its structures and the geode check reads the saved blocks; the volcano check reads the noise ground.
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

    /**
     * C15bR1-L1: real volcano starts (the set's potential chunk in each of 64 regions of 16 x 16 chunks) never hang
     * over a cliff: no column of the cone has its ground more than the skirt below the base.
     */
    @GameTest(template = "empty", batch = "venus_volcano_relief", timeoutTicks = 200)
    public static void realVolcanoStartsNeverHangOverACliff(GameTestHelper helper) {
        ServerLevel venus = helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.VENUS));
        helper.assertTrue(venus != null, "Venus has no Level");
        StructureSet set = venus.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, ModIdentity.id("volcanoes")));
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) set.placement();
        Structure volcano = set.structures().get(0).structure().value();
        ChunkGenerator generator = venus.getChunkSource().getGenerator();
        long seed = venus.getChunkSource().getGeneratorState().getLevelSeed();
        int starts = 0;
        for (int rx = 0; rx < 8; rx++) {
            for (int rz = 0; rz < 8; rz++) {
                ChunkPos chunk = placement.getPotentialStructureChunk(seed, (100 + rx) * 16, (100 + rz) * 16);
                Optional<Structure.GenerationStub> stub = volcano.findValidGenerationPoint(new Structure.GenerationContext(
                        venus.registryAccess(), generator, generator.getBiomeSource(),
                        venus.getChunkSource().randomState(), venus.getServer().getStructureManager(), venus.getSeed(),
                        chunk, venus, volcano.biomes()::contains));
                if (stub.isEmpty()) {
                    continue;
                }
                starts++;
                BlockPos base = stub.get().position();
                VolcanoShape shape = ((VolcanoPiece) stub.get().getPiecesBuilder().build().pieces().get(0)).shape();
                int radius = shape.radius();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz >= radius * radius) {
                            continue;
                        }
                        int ground = generator.getFirstOccupiedHeight(base.getX() + dx, base.getZ() + dz,
                                Heightmap.Types.WORLD_SURFACE_WG, venus, venus.getChunkSource().randomState());
                        helper.assertTrue(ground >= base.getY() - VolcanoShape.SKIRT, "The volcano at " + base
                                + " hangs over ground at y " + ground + ", " + dx + "," + dz + " from its base");
                    }
                }
            }
        }
        helper.assertTrue(starts >= 8, "Only " + starts + " volcanoes started in 64 regions");
        helper.succeed();
    }
}
