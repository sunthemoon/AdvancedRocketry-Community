package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetShapes;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.config.WorldgenSwitches;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ExoplanetWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.SwitchPlacement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-063 section 6 with revision 6 (A1) on a running server: the Tau Ceti f and g Levels and their terrain bands,
 * the surfaces of new chunks, the C15c features' write bound, the lightwood sapling, the blocks' drops and light, and
 * each C15c server switch. Features are placed in far Tau Ceti areas, never in the shared test area.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExoplanetGameTests {
    private static final String WORLDS = "exoplanet_worlds";

    private ExoplanetGameTests() {
    }

    /**
     * Tau Ceti f: over a 48 × 48 grid of columns the ground lies between y 41 and 85 ({@code 63 + 22n}), and each
     * column's biome is its ground's band (ocean spires, marsh, deep swamp, alien forest), give or take two blocks at
     * a band edge; all four bands appear.
     */
    @GameTest(template = "empty", batch = WORLDS, timeoutTicks = 200)
    public static void tauCetiFRisesFromTheSeaInFourBiomeBands(GameTestHelper helper) {
        ServerLevel f = level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        ChunkGenerator generator = f.getChunkSource().getGenerator();
        RandomState random = f.getChunkSource().randomState();
        Map<ResourceKey<Biome>, Integer> seen = new HashMap<>();
        for (int i = 0; i < 48; i++) {
            for (int j = 0; j < 48; j++) {
                int x = 40_000 + i * 32;
                int z = -40_000 + j * 32;
                int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, f, random) - 1;
                helper.assertTrue(ground >= 41 && ground <= 85, "Tau Ceti f ground at y " + ground);
                ResourceKey<Biome> biome = biome(generator, random, x, ground, z);
                seen.merge(biome, 1, Integer::sum);
                boolean band = biome.equals(band(ground)) || biome.equals(band(ground - 2))
                        || biome.equals(band(ground + 2));
                helper.assertTrue(band, biome.location() + " over ground at y " + ground);
            }
        }
        helper.assertTrue(seen.keySet().containsAll(List.of(ExoplanetWorldgen.OCEAN_SPIRES, ExoplanetWorldgen.MARSH,
                ExoplanetWorldgen.DEEP_SWAMP, ExoplanetWorldgen.ALIEN_FOREST)), "Tau Ceti f biomes " + seen);
        helper.succeed();
    }

    /** Tau Ceti g: a plateau with its ground between y 88 and 104, the stormland and crystal chasms both present. */
    @GameTest(template = "empty", batch = WORLDS, timeoutTicks = 200)
    public static void tauCetiGIsAPlateauOfTwoPatchedBiomes(GameTestHelper helper) {
        ServerLevel g = level(helper, ExoplanetContent.TAU_CETI_G_LEVEL);
        ChunkGenerator generator = g.getChunkSource().getGenerator();
        RandomState random = g.getChunkSource().randomState();
        Map<ResourceKey<Biome>, Integer> seen = new HashMap<>();
        for (int i = 0; i < 48; i++) {
            for (int j = 0; j < 48; j++) {
                int x = -40_000 + i * 32;
                int z = 40_000 + j * 32;
                int ground = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, g, random) - 1;
                helper.assertTrue(ground >= 88 && ground <= 104, "Tau Ceti g ground at y " + ground);
                seen.merge(biome(generator, random, x, ground, z), 1, Integer::sum);
            }
        }
        helper.assertTrue(seen.keySet().equals(Set.of(ExoplanetWorldgen.STORMLAND, ExoplanetWorldgen.CRYSTAL_CHASMS)),
                "Tau Ceti g biomes " + seen);
        helper.succeed();
    }

    /** Both Levels run with their dimension types; the biomes carry their features; no structure set is tried. */
    @GameTest(template = "empty", batch = WORLDS, timeoutTicks = 20)
    public static void bothWorldsRunWithTheirBiomesAndFeatures(GameTestHelper helper) {
        for (ResourceKey<Level> key : List.of(ExoplanetContent.TAU_CETI_F_LEVEL, ExoplanetContent.TAU_CETI_G_LEVEL)) {
            ServerLevel level = level(helper, key);
            helper.assertTrue(!level.dimensionType().hasFixedTime() && level.dimensionType().hasSkyLight(),
                    key.location() + " has no day cycle");
            helper.assertTrue(level.getChunkSource().getGeneratorState().possibleStructureSets().stream()
                    .noneMatch(set -> set.unwrapKey().orElseThrow().location().getNamespace()
                            .equals(AdvancedRocketryCommunity.MOD_ID)), key.location() + " tries a structure set");
        }
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        Map<ResourceKey<Biome>, ResourceKey<PlacedFeature>> own = Map.of(
                ExoplanetWorldgen.ALIEN_FOREST, V180ExoplanetWorldgen.LIGHTWOOD_TREE_PLACED,
                ExoplanetWorldgen.DEEP_SWAMP, V180ExoplanetWorldgen.GIANT_SWAMP_TREE_PLACED,
                ExoplanetWorldgen.OCEAN_SPIRES, V180ExoplanetWorldgen.INVERTED_PILLAR_PLACED,
                ExoplanetWorldgen.CRYSTAL_CHASMS, V180ExoplanetWorldgen.CRYSTAL_CLUSTER_PLACED,
                ExoplanetWorldgen.STORMLAND, V180ExoplanetWorldgen.ELECTRIC_MUSHROOMS_PLACED);
        for (Map.Entry<ResourceKey<Biome>, ResourceKey<PlacedFeature>> entry : own.entrySet()) {
            helper.assertTrue(biomes.getOrThrow(entry.getKey()).getGenerationSettings().hasFeature(
                    placed.getOrThrow(entry.getValue())), entry.getKey().location() + " lacks " + entry.getValue().location());
        }
        helper.assertTrue(biomes.getOrThrow(ExoplanetWorldgen.STORMLAND).getGenerationSettings().hasFeature(
                placed.getOrThrow(V180ExoplanetWorldgen.STORMLAND_CHARRED_TREE)), "The stormland grows no charred trees");
        helper.assertTrue(biomes.getOrThrow(ExoplanetWorldgen.MARSH).getGenerationSettings().features().size()
                > GenerationStep.Decoration.VEGETAL_DECORATION.ordinal(), "The marsh has no vegetation");
        helper.succeed();
    }

    /**
     * New chunks wear their biome's surface (ADR-063 revision 6): gravel in the ocean spires, grass over dirt on dry
     * marsh, swamp and forest ground and dirt under water, grass in the stormland, snow in the crystal chasms. A
     * column's ground block may also be a feature's block, and at least four in five columns show the bare surface.
     */
    @GameTest(template = "empty", batch = "exoplanet_surfaces_f", timeoutTicks = 2_400)
    public static void newTauCetiFChunksWearTheirBiomesSurfaces(GameTestHelper helper) {
        ServerLevel f = level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BoundingBox area = new BoundingBox(48_000, 0, 48_000, 48_047, 255, 48_047);
        PlanetSurfaceGameTests.whenLoaded(helper, f, area, () -> surfaces(helper, f, area));
    }

    @GameTest(template = "empty", batch = "exoplanet_surfaces_g", timeoutTicks = 2_400)
    public static void newTauCetiGChunksWearTheirBiomesSurfaces(GameTestHelper helper) {
        ServerLevel g = level(helper, ExoplanetContent.TAU_CETI_G_LEVEL);
        BoundingBox area = new BoundingBox(-48_048, 0, 48_000, -48_001, 255, 48_047);
        PlanetSurfaceGameTests.whenLoaded(helper, g, area, () -> surfaces(helper, g, area));
    }

    private static void surfaces(GameTestHelper helper, ServerLevel level, BoundingBox area) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int columns = 0;
        int bare = 0;
        for (int x = area.minX(); x <= area.maxX(); x += 3) {
            for (int z = area.minZ(); z <= area.maxZ(); z += 3) {
                int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
                BlockPos position = new BlockPos(x, ground, z);
                BlockState state = level.getBlockState(position);
                ResourceKey<Biome> biome = level.getBiome(position).unwrapKey().orElseThrow();
                boolean wet = !level.getFluidState(position.above()).isEmpty();
                Block expected = surface(biome, wet);
                columns++;
                if (state.is(expected)) {
                    bare++;
                } else {
                    helper.assertTrue(featureBlock(state), biome.location() + " ground " + state + " at " + position
                            + ", expected " + expected);
                }
            }
        }
        helper.assertTrue(bare * 5 >= columns * 4, "Only " + bare + " of " + columns + " columns show their surface");
    }

    private static Block surface(ResourceKey<Biome> biome, boolean wet) {
        if (biome.equals(ExoplanetWorldgen.OCEAN_SPIRES)) {
            return Blocks.GRAVEL;
        }
        if (biome.equals(ExoplanetWorldgen.CRYSTAL_CHASMS)) {
            return Blocks.SNOW_BLOCK;
        }
        return wet ? Blocks.DIRT : Blocks.GRASS_BLOCK;
    }

    private static boolean featureBlock(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.is(Blocks.CLAY) || state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.COBBLESTONE)
                || ExoplanetBlocks.CRYSTALS.stream().anyMatch(crystal -> state.is(crystal.get()))
                || state.is(SurfaceContent.CHARCOAL_LOG.get());
    }

    /**
     * Each C15c feature, placed twice in a far Tau Ceti f area through a recording level, writes something and every
     * write lies within 12 blocks of its origin horizontally ({@link ExoplanetShapes#MAX_REACH}).
     */
    @GameTest(template = "empty", batch = "exoplanet_feature_bounds", timeoutTicks = 2_400)
    public static void eachTauCetiFeatureWritesWithinTwelveBlocks(GameTestHelper helper) {
        ServerLevel f = level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BlockPos base = new BlockPos(52_000, 150, 52_000);
        List<ResourceKey<ConfiguredFeature<?, ?>>> features = List.of(ExoplanetWorldgen.LIGHTWOOD_TREE,
                V180ExoplanetWorldgen.GIANT_SWAMP_TREE, V180ExoplanetWorldgen.INVERTED_PILLAR,
                V180ExoplanetWorldgen.CRYSTAL_CLUSTER);
        BoundingBox area = new BoundingBox(base.getX() - 16, 0, base.getZ() - 16, base.getX() + 4 * 48 + 16, 255,
                base.getZ() + 48 + 16);
        PlanetSurfaceGameTests.whenLoaded(helper, f, area, () -> {
            Registry<ConfiguredFeature<?, ?>> registry = f.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
            for (int index = 0; index < features.size(); index++) {
                for (int copy = 0; copy < 2; copy++) {
                    BlockPos origin = base.offset(index * 48, 0, copy * 48);
                    // A grass platform under the origin: trees need soil and the swamp tree a sturdy floor.
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            f.setBlock(origin.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(),
                                    Block.UPDATE_CLIENTS);
                        }
                    }
                    List<BlockPos> written = new ArrayList<>();
                    ConfiguredFeature<?, ?> feature = registry.getOrThrow(features.get(index));
                    boolean placed = place(feature, PlanetSurfaceGameTests.RecordingLevel.wrap(f, written), f, origin,
                            31L * index + copy);
                    helper.assertTrue(placed && !written.isEmpty(), features.get(index).location() + " placed nothing");
                    for (BlockPos write : written) {
                        helper.assertTrue(Math.abs(write.getX() - origin.getX()) <= ExoplanetShapes.MAX_REACH
                                        && Math.abs(write.getZ() - origin.getZ()) <= ExoplanetShapes.MAX_REACH,
                                features.get(index).location() + " wrote at " + write + " from " + origin);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <C extends FeatureConfiguration> boolean place(ConfiguredFeature<C, ?> feature,
            WorldGenLevel level, ServerLevel server, BlockPos origin, long seed) {
        return ((Feature<C>) feature.feature()).place(
                new FeaturePlaceContext<>(Optional.empty(), level, server.getChunkSource().getGenerator(),
                        RandomSource.create(seed), origin, feature.config()));
    }

    /**
     * The lightwood sapling grows the lightwood tree in two stages where it fits; where a block stands in the trunk's
     * way, the sapling stays (ADR-063 revision 6).
     */
    @GameTest(template = "empty", batch = "exoplanet_sapling", timeoutTicks = 2_400)
    public static void aLightwoodSaplingGrowsTheTreeWhereItFitsAndStaysWhereNot(GameTestHelper helper) {
        ServerLevel f = level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BlockPos free = new BlockPos(56_008, 160, 56_008);
        BlockPos blocked = free.offset(32, 0, 0);
        BoundingBox area = new BoundingBox(free.getX() - 16, 0, free.getZ() - 16, blocked.getX() + 16, 255,
                blocked.getZ() + 16);
        PlanetSurfaceGameTests.whenLoaded(helper, f, area, () -> {
            SaplingBlock sapling = (SaplingBlock) ExoplanetBlocks.LIGHTWOOD_SAPLING.get();
            for (BlockPos position : List.of(free, blocked)) {
                f.setBlock(position.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                f.setBlock(position, sapling.defaultBlockState(), Block.UPDATE_ALL);
            }
            f.setBlock(blocked.above(5), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            RandomSource random = RandomSource.create(5L);
            for (BlockPos position : List.of(free, blocked)) {
                sapling.advanceTree(f, position, f.getBlockState(position), random);
                helper.assertTrue(f.getBlockState(position).getValue(SaplingBlock.STAGE) == 1,
                        "The first growth step did not advance the stage at " + position);
                sapling.advanceTree(f, position, f.getBlockState(position), random);
            }
            helper.assertTrue(f.getBlockState(free).is(ExoplanetBlocks.LIGHTWOOD_LOG.get())
                    && f.getBlockState(free.above(19)).is(ExoplanetBlocks.LIGHTWOOD_LOG.get()),
                    "The sapling did not grow a lightwood tree of at least 20: " + f.getBlockState(free));
            helper.assertTrue(f.getBlockState(blocked).is(sapling), "A blocked sapling did not stay: "
                    + f.getBlockState(blocked));
        });
    }

    /**
     * Drops and light (ADR-063 revision 6): lightwood leaves give a sapling about one time in a hundred and nothing
     * else, themselves with shears or Silk Touch; crystals, the log, planks and the electric mushroom drop themselves;
     * the leaves give light 8, the planks 4 and the mushroom none; the mushroom stands only on a sturdy top face.
     */
    @GameTest(template = "rocket_test", batch = "exoplanet_blocks", timeoutTicks = 40)
    public static void theTauCetiBlocksDropAndGlowAsTheContractSays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockState leaves = ExoplanetBlocks.LIGHTWOOD_LEAVES.get().defaultBlockState();
        int saplings = 0;
        for (int i = 0; i < 3_000; i++) {
            for (ItemStack drop : Block.getDrops(leaves, level, position, null)) {
                helper.assertTrue(drop.is(ExoplanetBlocks.LIGHTWOOD_SAPLING.get().asItem()) && drop.getCount() == 1,
                        "Lightwood leaves dropped " + drop);
                saplings++;
            }
        }
        helper.assertTrue(saplings >= 10 && saplings <= 60, saplings + " saplings in 3,000 drops (about 30 expected)");
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        for (ItemStack tool : List.of(new ItemStack(Items.SHEARS), silk)) {
            List<ItemStack> drops = Block.getDrops(leaves, level, position, null, null, tool);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(leaves.getBlock().asItem()),
                    "With " + tool + " the leaves dropped " + drops);
        }
        List<Block> selfDropping = new ArrayList<>(List.of(ExoplanetBlocks.LIGHTWOOD_LOG.get(),
                ExoplanetBlocks.LIGHTWOOD_PLANKS.get(), ExoplanetBlocks.LIGHTWOOD_SAPLING.get(),
                ExoplanetBlocks.ELECTRIC_MUSHROOM.get()));
        ExoplanetBlocks.CRYSTALS.forEach(crystal -> selfDropping.add(crystal.get()));
        for (Block block : selfDropping) {
            List<ItemStack> drops = Block.getDrops(block.defaultBlockState(), level, position, null);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.asItem()), block + " dropped " + drops);
        }
        helper.assertTrue(leaves.getLightEmission() == 8, "Lightwood leaves give light " + leaves.getLightEmission());
        helper.assertTrue(ExoplanetBlocks.LIGHTWOOD_PLANKS.get().defaultBlockState().getLightEmission() == 4,
                "Lightwood planks give no light 4");
        BlockState mushroom = ExoplanetBlocks.ELECTRIC_MUSHROOM.get().defaultBlockState();
        helper.assertTrue(mushroom.getLightEmission() == 0, "The electric mushroom glows");
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 1, 6), Blocks.AIR);
        helper.assertTrue(mushroom.canSurvive(level, helper.absolutePos(new BlockPos(4, 2, 4))),
                "The electric mushroom cannot stand on stone");
        helper.assertTrue(!mushroom.canSurvive(level, helper.absolutePos(new BlockPos(6, 2, 6))),
                "The electric mushroom stands on air");
        helper.succeed();
    }

    /**
     * Each C15c placement starts with its server switch (the stormland charred trees with the C15b switch); with the
     * switch off it passes nothing on, with it on the origin.
     */
    @GameTest(template = "empty", batch = "exoplanet_switches", timeoutTicks = 20)
    public static void eachTauCetiFeatureFollowsItsServerSwitch(GameTestHelper helper) {
        ServerLevel f = level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        Registry<PlacedFeature> registry = f.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        Map<ResourceKey<PlacedFeature>, ForgeConfigSpec.BooleanValue> switches = Map.of(
                V180ExoplanetWorldgen.LIGHTWOOD_TREE_PLACED, CommonConfig.LIGHTWOOD_TREES_ENABLED,
                V180ExoplanetWorldgen.GIANT_SWAMP_TREE_PLACED, CommonConfig.SWAMP_TREES_ENABLED,
                V180ExoplanetWorldgen.INVERTED_PILLAR_PLACED, CommonConfig.INVERTED_PILLARS_ENABLED,
                V180ExoplanetWorldgen.CRYSTAL_CLUSTER_PLACED, CommonConfig.CRYSTAL_CLUSTERS_ENABLED,
                V180ExoplanetWorldgen.ELECTRIC_MUSHROOMS_PLACED, CommonConfig.ELECTRIC_MUSHROOMS_ENABLED,
                V180ExoplanetWorldgen.STORMLAND_CHARRED_TREE, CommonConfig.CHARRED_TREES_ENABLED);
        Map<ResourceKey<PlacedFeature>, String> names = Map.of(
                V180ExoplanetWorldgen.LIGHTWOOD_TREE_PLACED, WorldgenSwitches.LIGHTWOOD_TREES,
                V180ExoplanetWorldgen.GIANT_SWAMP_TREE_PLACED, WorldgenSwitches.SWAMP_TREES,
                V180ExoplanetWorldgen.INVERTED_PILLAR_PLACED, WorldgenSwitches.INVERTED_PILLARS,
                V180ExoplanetWorldgen.CRYSTAL_CLUSTER_PLACED, WorldgenSwitches.CRYSTAL_CLUSTERS,
                V180ExoplanetWorldgen.ELECTRIC_MUSHROOMS_PLACED, WorldgenSwitches.ELECTRIC_MUSHROOMS,
                V180ExoplanetWorldgen.STORMLAND_CHARRED_TREE, WorldgenSwitches.CHARRED_TREES);
        BlockPos origin = new BlockPos(60_000, 100, 60_000);
        for (Map.Entry<ResourceKey<PlacedFeature>, ForgeConfigSpec.BooleanValue> entry : switches.entrySet()) {
            PlacedFeature placed = registry.getOrThrow(entry.getKey());
            PlacementModifier first = placed.placement().get(0);
            helper.assertTrue(first instanceof SwitchPlacement placement
                            && placement.name().equals(names.get(entry.getKey())),
                    entry.getKey().location() + " does not start with its switch: " + first);
            PlacementContext context = new PlacementContext(f, f.getChunkSource().getGenerator(), Optional.of(placed));
            helper.assertTrue(first.getPositions(context, RandomSource.create(1L), origin).count() == 1,
                    entry.getKey().location() + " blocked while on");
            SwitchOverrides.set(entry.getValue(), false);
            try {
                helper.assertTrue(first.getPositions(context, RandomSource.create(1L), origin).count() == 0,
                        entry.getKey().location() + " ignored its switch");
            } finally {
                SwitchOverrides.clear(entry.getValue());
            }
        }
        helper.succeed();
    }

    /** The biome of Tau Ceti f's ground band ({@code n = (ground - 63) / 22}). */
    private static ResourceKey<Biome> band(int ground) {
        double n = (ground - V180ExoplanetWorldgen.F_SURFACE_MID) / (double) V180ExoplanetWorldgen.F_SURFACE_SPAN;
        return n < V180ExoplanetWorldgen.OCEAN_BELOW ? ExoplanetWorldgen.OCEAN_SPIRES
                : n < V180ExoplanetWorldgen.MARSH_BELOW ? ExoplanetWorldgen.MARSH
                : n < V180ExoplanetWorldgen.SWAMP_BELOW ? ExoplanetWorldgen.DEEP_SWAMP : ExoplanetWorldgen.ALIEN_FOREST;
    }

    private static ResourceKey<Biome> biome(ChunkGenerator generator, RandomState random, int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                QuartPos.fromBlock(z), random.sampler()).unwrapKey().orElseThrow();
    }

    private static ServerLevel level(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        helper.assertTrue(level != null, "Missing Level " + key.location());
        return level;
    }
}
