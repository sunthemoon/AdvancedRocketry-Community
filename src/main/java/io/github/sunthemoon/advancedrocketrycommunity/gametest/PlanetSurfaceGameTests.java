package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.CraterPiece;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.CraterShape;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.GeodePiece;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.GeodeShape;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.GeodeStructure;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.PieceSchema;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.SurfaceWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.VolcanoPiece;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.VolcanoShape;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180PlanetWorldgen;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.common.Tags;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-063 section 5 and 9 (A1) on a running server: the Moon terrain bound for two seeds, the surfaces and biomes of
 * the Moon, Mars and Venus, and the write bounds of the crater, volcano, geode and charred tree generators. Each
 * structure test places its piece by hand far from the spawn area, one chunk at a time, and compares every block
 * around it before and after.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetSurfaceGameTests {
    private static final String BATCH = "planet_surfaces";
    /** A second seed for the terrain bound, besides the world's own. */
    private static final long OTHER_SEED = 0x6C1A_8E5F_0B27_D341L;
    /**
     * Vanilla's surface depth is {@code (int) (noise × 2.75 + 3 + random × 0.25)} with the surface noise within
     * about ±2.5, so at most about 10 filler blocks lie under the top; a crater rim stacks up to 8 more
     * ({@link CraterShape}). This bound covers both with a margin.
     */
    private static final int MAX_FILLER = 20;
    private static final TicketType<ChunkPos> LOAD_TICKET = TicketType.create("arce_gametest_planet_load",
            Comparator.comparingLong(ChunkPos::toLong));

    private PlanetSurfaceGameTests() {
    }

    /**
     * No Moon surface above y 36 (so nothing generated reaches y 63, craters included: their rims stop at y 44) and
     * none below y 12, over a 64 × 64 grid of columns for two seeds; both biomes appear, each on its own height band.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void moonSurfaceStaysBetweenY12And36ForTwoSeeds(GameTestHelper helper) {
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) moon.getChunkSource().getGenerator();
        for (long seed : new long[] {moon.getSeed(), OTHER_SEED}) {
            RandomState state = RandomState.create(generator.generatorSettings().value(),
                    moon.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            int lowlands = 0;
            int highlands = 0;
            for (int i = 0; i < 64; i++) {
                for (int j = 0; j < 64; j++) {
                    int x = -3_000 + i * 97;
                    int z = 1_500 + j * 89;
                    int top = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, moon, state) - 1;
                    helper.assertTrue(top >= V180PlanetWorldgen.MOON_SURFACE_MIN
                                    && top <= V180PlanetWorldgen.MOON_SURFACE_MAX,
                            "Moon surface y " + top + " at " + x + "," + z + " for seed " + seed);
                    ResourceKey<Biome> biome = generator.getBiomeSource().getNoiseBiome(x >> 2, top >> 2, z >> 2,
                            state.sampler()).unwrapKey().orElseThrow();
                    if (top <= 17) {
                        helper.assertTrue(biome.equals(V180PlanetWorldgen.REGOLITH_LOWLANDS), "Low ground in " + biome);
                        lowlands++;
                    } else if (top >= 23) {
                        helper.assertTrue(biome.equals(V180PlanetWorldgen.REGOLITH_HIGHLANDS), "High ground in " + biome);
                        highlands++;
                    }
                }
            }
            helper.assertTrue(lowlands > 100 && highlands > 100,
                    "Both Moon biomes should be common: " + lowlands + " / " + highlands);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void newChunksUseTheNewSurfacesAndBiomes(GameTestHelper helper) {
        check(helper, CelestialIds.MOON_LEVEL, new BlockPos(4_096, 0, 4_096),
                Set.of(SurfaceContent.MOON_TURF.get(), SurfaceContent.DARK_MOON_TURF.get()), Blocks.STONE,
                Set.of(V180PlanetWorldgen.REGOLITH_HIGHLANDS, V180PlanetWorldgen.REGOLITH_LOWLANDS));
        check(helper, PlanetaryContent.level(PlanetaryContent.MARS), new BlockPos(4_096, 0, 4_096),
                Set.of(SurfaceContent.FERRIC_SAND.get()), Blocks.RED_SANDSTONE,
                Set.of(V180PlanetWorldgen.FERRIC_REGOLITH));
        check(helper, PlanetaryContent.level(PlanetaryContent.VENUS), new BlockPos(4_096, 0, 4_096),
                Set.of(Blocks.BASALT), Blocks.BASALT,
                Set.of(V180PlanetWorldgen.VOLCANIC, V180PlanetWorldgen.VOLCANIC_LOWLANDS));
        helper.succeed();
    }

    /** A crater of the largest radius on the Moon: each chunk call writes only its own chunk; floor y ≥ 5, rim ≤ 44. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void aMoonCraterWritesOnlyItsChunkWithinItsBounds(GameTestHelper helper) {
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        BlockPos centre = surface(moon, -20_008, 20_008);
        CraterShape shape = new CraterShape(48, 15, 6, new int[] {5, 5, 5, 5});
        CraterPiece piece = new CraterPiece(centre, shape, V180PlanetWorldgen.CRATER_FLOOR_MIN,
                V180PlanetWorldgen.MOON_RIM_MAX);
        whenLoaded(helper, moon, piece.getBoundingBox(), () -> {
            placeChunkByChunk(helper, moon, piece, centre, shape.reach(), 0, 64);
            int highest = 0;
            int lowestFloor = Integer.MAX_VALUE;
            for (int dx = -shape.reach(); dx <= shape.reach(); dx += 2) {
                for (int dz = -shape.reach(); dz <= shape.reach(); dz += 2) {
                    int top = moon.getHeight(Heightmap.Types.WORLD_SURFACE, centre.getX() + dx, centre.getZ() + dz) - 1;
                    highest = Math.max(highest, top);
                    if (shape.inBowl(dx, dz)) {
                        lowestFloor = Math.min(lowestFloor, top);
                    }
                }
            }
            helper.assertTrue(highest <= V180PlanetWorldgen.MOON_RIM_MAX, "A crater rim reached y " + highest);
            helper.assertTrue(lowestFloor >= V180PlanetWorldgen.CRATER_FLOOR_MIN, "A crater floor reached y " + lowestFloor);
            helper.assertTrue(lowestFloor < centre.getY(), "The crater dug nothing");
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void aVolcanoWritesOnlyItsChunkWithinRadius32AndHeight48(GameTestHelper helper) {
        ServerLevel venus = level(helper, PlanetaryContent.level(PlanetaryContent.VENUS));
        BlockPos base = surface(venus, 20_008, -20_008);
        VolcanoShape shape = new VolcanoShape(VolcanoShape.MAX_RADIUS, VolcanoShape.MAX_HEIGHT);
        VolcanoPiece piece = new VolcanoPiece(base, shape);
        whenLoaded(helper, venus, piece.getBoundingBox(), () -> {
            placeChunkByChunk(helper, venus, piece, base, shape.radius(), base.getY() - VolcanoShape.SKIRT,
                    base.getY() + shape.height() + 2);
            int top = venus.getHeight(Heightmap.Types.WORLD_SURFACE, base.getX(), base.getZ()) - 1;
            helper.assertTrue(top > base.getY() && top <= base.getY() + VolcanoShape.MAX_HEIGHT + 1,
                    "Volcano summit at y " + top + " over a base at " + base.getY());
            helper.assertTrue(venus.getBlockState(base.above(shape.coneRise(0) + 1)).is(Blocks.LAVA), "No crater lava pool");
            helper.assertTrue(venus.getBlockState(base.below(2)).is(Blocks.LAVA), "No lava core");
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 2_400)
    public static void aGeodeWritesOnlyItsChunkWithinRadius24BelowTheSurface(GameTestHelper helper) {
        ServerLevel venus = level(helper, PlanetaryContent.level(PlanetaryContent.VENUS));
        BlockPos ground = surface(venus, 24_008, -24_008);
        GeodeShape shape = new GeodeShape(GeodeShape.MAX_RADIUS);
        BlockPos centre = ground.below(4 + shape.verticalReach());
        GeodePiece piece = new GeodePiece(centre, shape, 7L);
        whenLoaded(helper, venus, piece.getBoundingBox(), () -> {
            // Low ground over part of the geode: a pit dug below where the roof would be. No column, the pit's
            // included, may get a geode block within GeodeStructure.COVER blocks of its own ground.
            BlockPos pit = centre.offset(8, 0, 0);
            int pitFloor = centre.getY() + shape.halfHeight(8, 0) - 2;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = pitFloor + 1; y <= ground.getY() + 8; y++) {
                        venus.setBlock(new BlockPos(pit.getX() + dx, y, pit.getZ() + dz), Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS);
                    }
                }
            }
            BoundingBox box = piece.getBoundingBox();
            Map<Long, Integer> groundBefore = new HashMap<>();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    groundBefore.put(ChunkPos.asLong(x, z), venus.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                }
            }
            List<BlockPos> writes = placeChunkByChunk(helper, venus, piece, centre, shape.radius(),
                    centre.getY() - shape.verticalReach() - 1, ground.getY() + 2);
            for (BlockPos write : writes) {
                int cover = groundBefore.get(ChunkPos.asLong(write.getX(), write.getZ())) - write.getY();
                helper.assertTrue(cover >= GeodeStructure.COVER, "A geode write " + cover + " blocks under the ground at "
                        + write);
            }
            helper.assertTrue(writes.stream().anyMatch(write -> write.getX() == pit.getX() && write.getZ() == pit.getZ()),
                    "The geode left out the column under the pit");
            helper.assertTrue(venus.getBlockState(centre).isAir(), "The geode is not hollow");
            helper.assertTrue(venus.getBlockState(centre.above(shape.halfHeight(0, 0))).is(SurfaceContent.GEODE_SHELL.get()),
                    "No geode shell roof");
            helper.assertTrue(venus.getHeight(Heightmap.Types.WORLD_SURFACE, centre.getX(), centre.getZ()) - 1
                    == ground.getY(), "The geode broke the surface");
        });
    }

    /** A charred tree: a trunk of six to eight logs, at most one stub branch, nothing beyond one block sideways. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void aCharredTreeStaysWithinItsReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.BASALT);
            }
        }
        BlockPos origin = helper.absolutePos(new BlockPos(7, 2, 7));
        for (long seed = 1; seed <= 12; seed++) {
            for (int y = 2; y <= 14; y++) {
                for (int x = 4; x <= 10; x++) {
                    for (int z = 4; z <= 10; z++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
            boolean placed = SurfaceWorldgen.CHARRED_TREE.get().place(new FeaturePlaceContext<>(Optional.empty(), level,
                    level.getChunkSource().getGenerator(), RandomSource.create(seed), origin, FeatureConfiguration.NONE));
            helper.assertTrue(placed, "The tree did not grow on basalt");
            int trunk = 0;
            for (int y = 2; y <= 14; y++) {
                for (int x = 4; x <= 10; x++) {
                    for (int z = 4; z <= 10; z++) {
                        BlockPos position = helper.absolutePos(new BlockPos(x, y, z));
                        if (level.getBlockState(position).is(SurfaceContent.CHARCOAL_LOG.get())) {
                            helper.assertTrue(Math.abs(x - 7) <= 1 && Math.abs(z - 7) <= 1 && y - 2 < 8,
                                    "A log beyond the tree's reach at " + x + "," + y + "," + z);
                            trunk += x == 7 && z == 7 ? 1 : 0;
                        }
                    }
                }
            }
            helper.assertTrue(trunk >= 6 && trunk <= 8, "Trunk of " + trunk);
        }
        helper.succeed();
    }

    /**
     * A saved crater, volcano or geode piece loads back to the same piece, so a start saved before a restart finishes
     * the same way after it (ADR-063 section 5); a saved form without the current schema is refused.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void savedPiecesLoadBackAndRefuseAnotherSchema(GameTestHelper helper) {
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(helper.getLevel());
        List<StructurePiece> pieces = List.of(
                new CraterPiece(new BlockPos(100, 30, -100), new CraterShape(20, 6, 3, new int[] {1, 2, 3, 4}),
                        V180PlanetWorldgen.CRATER_FLOOR_MIN, V180PlanetWorldgen.MOON_RIM_MAX),
                new VolcanoPiece(new BlockPos(-50, 70, 50), new VolcanoShape(24, 30)),
                new GeodePiece(new BlockPos(10, 20, 10), new GeodeShape(18), 99L));
        for (StructurePiece piece : pieces) {
            String name = piece.getClass().getSimpleName();
            CompoundTag saved = piece.createTag(context);
            helper.assertTrue(saved.getInt(PieceSchema.KEY) == PieceSchema.CURRENT, name + " saved no schema");
            StructurePiece loaded = piece.getType().load(context, saved);
            helper.assertTrue(loaded.getClass() == piece.getClass() && loaded.createTag(context).equals(saved),
                    name + " did not load back to the same piece");
            for (int schema : new int[] {0, PieceSchema.CURRENT + 1}) {
                CompoundTag other = saved.copy();
                other.putInt(PieceSchema.KEY, schema);
                try {
                    piece.getType().load(context, other);
                    helper.fail(name + " loaded schema " + schema);
                } catch (IllegalArgumentException expected) {
                    // Vanilla drops a start whose piece fails to load, with a logged error.
                }
            }
        }
        helper.succeed();
    }

    /** The developer platform still sits in open sky over the highest highlands (ADR-063 section 5). */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void theDeveloperPlatformStaysInOpenSkyOverTheHighestHighlands(GameTestHelper helper) {
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
        moon.getChunkAt(pad);
        MoonPadArea.shape(moon, 3, V180PlanetWorldgen.MOON_SURFACE_MAX);
        SafeCelestialTravel.Destination destination = new SafeCelestialTravel().prepare(moon, CelestialIds.MOON_ID);
        helper.assertTrue(destination.y() == pad.getY() && destination.x() == pad.getX() + 0.5D
                && destination.z() == pad.getZ() + 0.5D, "The platform moved: " + destination);
        helper.assertTrue(moon.getBlockState(pad.below()).is(Blocks.SEA_LANTERN), "No platform floor at y 79");
        for (int y = V180PlanetWorldgen.MOON_SURFACE_MAX + 1; y < pad.getY() - 1; y++) {
            helper.assertTrue(moon.getBlockState(new BlockPos(pad.getX(), y, pad.getZ())).isAir(),
                    "Terrain between the highlands and the platform at y " + y);
        }
        helper.assertTrue(moon.canSeeSky(pad.above(SafeCelestialTravel.CLEARANCE)), "The platform is covered");
        // Leave the shared Moon pad area as other tests expect it: no platform.
        MoonPadArea.shape(moon, 3, V180PlanetWorldgen.MOON_SURFACE_MAX);
        helper.succeed();
    }

    /**
     * Requests every chunk a piece's box touches with a ticket and waits until all are loaded, so the generation is
     * spread over ticks instead of stalling one tick (and every test running beside it); then runs the check, lets the
     * chunks go and succeeds.
     */
    private static void whenLoaded(GameTestHelper helper, ServerLevel level, BoundingBox box, Runnable check) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                chunks.add(chunk);
                level.getChunkSource().addRegionTicket(LOAD_TICKET, chunk, 1, chunk);
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(chunks.stream().allMatch(chunk ->
                        level.getChunkSource().hasChunk(chunk.x, chunk.z)), "Chunks still loading"))
                .thenExecute(check)
                .thenExecute(() -> chunks.forEach(chunk ->
                        level.getChunkSource().removeRegionTicket(LOAD_TICKET, chunk, 1, chunk)))
                .thenSucceed();
    }

    private static ServerLevel level(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        helper.assertTrue(level != null, "Missing Level " + key.location());
        return level;
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunkAt(new BlockPos(x, 0, z));
        return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
    }

    private static void check(GameTestHelper helper, ResourceKey<Level> key, BlockPos start,
                              Set<Block> tops, Block rock, Set<ResourceKey<Biome>> biomes) {
        ServerLevel level = level(helper, key);
        for (int offset = 0; offset < 4; offset++) {
            int x = start.getX() + offset * 40;
            int z = start.getZ();
            BlockPos top = surface(level, x, z);
            BlockState state = level.getBlockState(top);
            helper.assertTrue(tops.stream().anyMatch(state::is) || state.is(Blocks.LAVA),
                    key.location() + " top block " + state + " at " + top);
            // The filler (the top blocks again: the column's surface depth, plus a crater rim) is at most
            // MAX_FILLER blocks deep; below it lies the rock, or an ore vein, a geode or lava within it.
            BlockPos under = top.below();
            int filler = 0;
            while (filler < MAX_FILLER && tops.stream().anyMatch(level.getBlockState(under)::is)) {
                under = under.below();
                filler++;
            }
            BlockState below = level.getBlockState(under);
            helper.assertTrue(below.is(rock) || below.is(Tags.Blocks.ORES) || below.is(SurfaceContent.GEODE_SHELL.get())
                            || below.isAir() || !below.getFluidState().isEmpty(),
                    key.location() + " rock " + below + " at " + under + " below " + filler + " filler under " + top);
            ResourceKey<Biome> biome = level.getBiome(top).unwrapKey().orElseThrow();
            helper.assertTrue(biomes.contains(biome), key.location() + " biome " + biome.location());
        }
    }

    /**
     * Places a piece in every chunk its box touches, one chunk box at a time, through a level that records every
     * block write, and checks that each call wrote only inside its own chunk and the piece's box, and that the box
     * lies within {@code reach} of the centre. Returns every position written.
     */
    private static List<BlockPos> placeChunkByChunk(GameTestHelper helper, ServerLevel level, StructurePiece piece,
                                                    BlockPos centre, int reach, int minY, int maxY) {
        BoundingBox pieceBox = piece.getBoundingBox();
        helper.assertTrue(pieceBox.minX() >= centre.getX() - reach && pieceBox.maxX() <= centre.getX() + reach
                        && pieceBox.minZ() >= centre.getZ() - reach && pieceBox.maxZ() <= centre.getZ() + reach
                        && pieceBox.minY() >= minY && pieceBox.maxY() <= maxY,
                "The piece box exceeds its bounds: " + pieceBox);
        List<BlockPos> writes = new ArrayList<>();
        for (int cx = pieceBox.minX() >> 4; cx <= pieceBox.maxX() >> 4; cx++) {
            for (int cz = pieceBox.minZ() >> 4; cz <= pieceBox.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        for (int cx = pieceBox.minX() >> 4; cx <= pieceBox.maxX() >> 4; cx++) {
            for (int cz = pieceBox.minZ() >> 4; cz <= pieceBox.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(),
                        chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
                List<BlockPos> written = new ArrayList<>();
                piece.postProcess(RecordingLevel.wrap(level, written), level.structureManager(),
                        level.getChunkSource().getGenerator(), RandomSource.create(1L), chunkBox, chunk, centre);
                for (BlockPos position : written) {
                    helper.assertTrue(chunkBox.isInside(position) && pieceBox.isInside(position),
                            "A write outside chunk " + chunk + " or the piece box at " + position);
                }
                writes.addAll(written);
            }
        }
        helper.assertTrue(!writes.isEmpty(), "The piece wrote nothing");
        return writes;
    }

    /** A {@link WorldGenLevel} view of a server level that records the position of every block write. */
    private static final class RecordingLevel implements InvocationHandler {
        private final ServerLevel level;
        private final List<BlockPos> written;

        private RecordingLevel(ServerLevel level, List<BlockPos> written) {
            this.level = level;
            this.written = written;
        }

        static WorldGenLevel wrap(ServerLevel level, List<BlockPos> written) {
            return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[] {WorldGenLevel.class}, new RecordingLevel(level, written));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getName().equals("setBlock") && arguments != null && arguments.length > 0
                    && arguments[0] instanceof BlockPos position) {
                written.add(position.immutable());
            }
            try {
                return method.invoke(level, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
