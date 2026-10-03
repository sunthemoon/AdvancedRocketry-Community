package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.config.WorldgenSwitches;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180PlanetWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180PlanetWorldgen.PlanetVein;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.SwitchPlacement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-063 sections 4 and 5 (A1) on a running server: the Moon and Mars ore sets in their biomes (iridium on both,
 * Moon dilithium at the airless count), each vein in a test chunk, each C15b server switch, and the surface blocks'
 * drops. Nothing here loads a planet chunk: placements and generation points are computed from the generators, as
 * worldgen does.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetFeatureGameTests {
    private static final String BATCH = "planet_features";
    /** A vein of at most 16 blocks writes within this many blocks of its origin. */
    private static final int VEIN_REACH = 5;

    private PlanetFeatureGameTests() {
    }

    /** The Moon biomes carry the six Moon veins, Mars the six Mars veins; Venus carries no ore and the charred tree. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void eachBodyCarriesItsOreSetWithIridium(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
        Registry<PlacedFeature> placed = registries.registryOrThrow(Registries.PLACED_FEATURE);
        int ores = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        int vegetal = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        List<PlanetVein> veins = V180PlanetWorldgen.veins();
        helper.assertTrue(veins.stream().filter(vein -> vein.ore().equals("iridium")).count() == 2,
                "Iridium is not placed on both the Moon and Mars");
        helper.assertTrue(veins.stream().anyMatch(vein -> vein.featureName().equals("moon_dilithium_ore")
                && vein.count() == 10 && vein.size() == 16), "Moon dilithium is not at the airless count");
        for (ResourceKey<Biome> key : List.of(V180PlanetWorldgen.REGOLITH_HIGHLANDS,
                V180PlanetWorldgen.REGOLITH_LOWLANDS, V180PlanetWorldgen.FERRIC_REGOLITH,
                V180PlanetWorldgen.VOLCANIC, V180PlanetWorldgen.VOLCANIC_LOWLANDS)) {
            Biome biome = biomes.getOrThrow(key);
            String body = key == V180PlanetWorldgen.FERRIC_REGOLITH ? "mars"
                    : key == V180PlanetWorldgen.VOLCANIC || key == V180PlanetWorldgen.VOLCANIC_LOWLANDS ? "venus"
                    : "moon";
            List<HolderSet<PlacedFeature>> steps = biome.getGenerationSettings().features();
            HolderSet<PlacedFeature> step = steps.size() > ores ? steps.get(ores) : HolderSet.<PlacedFeature>direct();
            for (PlanetVein vein : veins) {
                boolean carried = step.contains(placed.getHolderOrThrow(vein.placed()));
                helper.assertTrue(carried == vein.body().equals(body), key.location() + (carried ? " carries "
                        : " lacks ") + vein.featureName());
            }
            helper.assertTrue(step.size() == (body.equals("venus") ? 0 : 6), key.location() + " has " + step.size()
                    + " ore features");
            boolean tree = steps.size() > vegetal
                    && steps.get(vegetal).contains(placed.getHolderOrThrow(V180PlanetWorldgen.CHARRED_TREE_PLACED));
            helper.assertTrue(tree == (key == V180PlanetWorldgen.VOLCANIC), key.location()
                    + (tree ? " grows" : " lacks") + " charred trees");
        }
        helper.succeed();
    }

    /**
     * Each Moon vein replaces only stone and each Mars vein only red sandstone (the bodies' base blocks), with its
     * own ore and near its origin.
     */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 40)
    public static void eachPlanetVeinReplacesOnlyItsBodysRockNearItsOrigin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(7, 7, 7));
        Registry<ConfiguredFeature<?, ?>> features = level.registryAccess().registryOrThrow(
                Registries.CONFIGURED_FEATURE);
        for (PlanetVein vein : V180PlanetWorldgen.veins()) {
            Block rock = vein.body().equals("moon") ? Blocks.STONE : Blocks.RED_SANDSTONE;
            // The other body's rock fills the lower half: a vein must leave it alone.
            Block other = vein.body().equals("moon") ? Blocks.RED_SANDSTONE : Blocks.STONE;
            ConfiguredFeature<?, ?> feature = features.getOrThrow(vein.configured());
            int placed = 0;
            for (long seed = 1; seed <= 8 && placed == 0; seed++) {
                fill(level, origin, rock, other);
                feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), origin);
                placed = count(helper, level, origin, vein, rock, other);
            }
            helper.assertTrue(placed > 0 && placed <= vein.size() * 2, vein.featureName() + " placed " + placed);
        }
        fill(level, origin, Blocks.STONE, Blocks.STONE);
        helper.succeed();
    }

    /**
     * On the Moon and Mars each vein gives its count of positions, all in the origin chunk between y 4 and 40; with
     * {@code planet_ores} off it gives none. The biome filter reads the bodies' own biome sources.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void planetVeinsStayInTheirChunkAndFollowTheServerSwitch(GameTestHelper helper) {
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        ServerLevel mars = level(helper, PlanetaryContent.level(PlanetaryContent.MARS));
        Registry<PlacedFeature> registry = moon.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        BlockPos origin = new ChunkPos(-1_875, 1_875).getWorldPosition();
        for (PlanetVein vein : V180PlanetWorldgen.veins()) {
            ServerLevel level = vein.body().equals("moon") ? moon : mars;
            PlacedFeature placed = registry.getOrThrow(vein.placed());
            List<BlockPos> positions = positions(level, placed, origin);
            helper.assertTrue(positions.size() == vein.count(),
                    vein.featureName() + " gave " + positions.size() + " positions, not " + vein.count());
            for (BlockPos position : positions) {
                helper.assertTrue(new ChunkPos(position).equals(new ChunkPos(origin))
                                && position.getY() >= V180PlanetWorldgen.PLANET_ORE_MIN_Y
                                && position.getY() <= V180PlanetWorldgen.PLANET_ORE_MAX_Y,
                        vein.featureName() + " left its chunk or height range at " + position);
            }
        }
        withSwitchOff(CommonConfig.PLANET_ORES_ENABLED, () -> {
            for (PlanetVein vein : V180PlanetWorldgen.veins()) {
                ServerLevel level = vein.body().equals("moon") ? moon : mars;
                helper.assertTrue(positions(level, registry.getOrThrow(vein.placed()), origin).isEmpty(),
                        vein.featureName() + " ignored the switch");
            }
        });
        helper.succeed();
    }

    /**
     * Craters (Moon and Mars), volcanoes and geodes (Venus) find generation points with their switch on and none
     * with it off, in the same chunk; a disabled structure therefore starts nowhere.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void eachStructureSwitchStopsItsStarts(GameTestHelper helper) {
        ServerLevel moon = level(helper, CelestialIds.MOON_LEVEL);
        ServerLevel mars = level(helper, PlanetaryContent.level(PlanetaryContent.MARS));
        ServerLevel venus = level(helper, PlanetaryContent.level(PlanetaryContent.VENUS));
        switchStopsStarts(helper, moon, V180PlanetWorldgen.MOON_CRATER, CommonConfig.CRATERS_ENABLED);
        switchStopsStarts(helper, mars, V180PlanetWorldgen.MARS_CRATER, CommonConfig.CRATERS_ENABLED);
        switchStopsStarts(helper, venus, V180PlanetWorldgen.VOLCANO, CommonConfig.VOLCANOES_ENABLED);
        switchStopsStarts(helper, venus, V180PlanetWorldgen.GEODE, CommonConfig.GEODES_ENABLED);
        helper.succeed();
    }

    /**
     * Each body's generator lists exactly its own structure sets, so worldgen tries them: the structures' biome tags
     * meet the bodies' biome sources. (A generation point alone does not show this; vanilla only tries the sets of a
     * generator's structure state.)
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void eachBodysGeneratorTriesItsOwnStructureSets(GameTestHelper helper) {
        Set<String> ours = Set.of("moon_craters", "mars_craters", "volcanoes", "geodes");
        Map<ResourceKey<Level>, Set<String>> expected = Map.of(
                CelestialIds.MOON_LEVEL, Set.of("moon_craters"),
                PlanetaryContent.level(PlanetaryContent.MARS), Set.of("mars_craters"),
                PlanetaryContent.level(PlanetaryContent.VENUS), Set.of("volcanoes", "geodes"));
        for (Map.Entry<ResourceKey<Level>, Set<String>> body : expected.entrySet()) {
            ServerLevel level = level(helper, body.getKey());
            Set<String> tried = level.getChunkSource().getGeneratorState().possibleStructureSets().stream()
                    .map(set -> set.unwrapKey().orElseThrow().location())
                    .filter(id -> id.getNamespace().equals(AdvancedRocketryCommunity.MOD_ID))
                    .map(ResourceLocation::getPath).filter(ours::contains).collect(Collectors.toSet());
            helper.assertTrue(tried.equals(body.getValue()), body.getKey().location() + " tries " + tried
                    + ", not " + body.getValue());
        }
        helper.succeed();
    }

    /** The charred tree placement starts with the {@code charred_trees} switch, which passes nothing when off. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void theCharredTreeFollowsItsServerSwitch(GameTestHelper helper) {
        ServerLevel venus = level(helper, PlanetaryContent.level(PlanetaryContent.VENUS));
        PlacedFeature tree = venus.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(V180PlanetWorldgen.CHARRED_TREE_PLACED);
        PlacementModifier first = tree.placement().get(0);
        helper.assertTrue(first instanceof SwitchPlacement placement
                        && placement.name().equals(WorldgenSwitches.CHARRED_TREES),
                "The charred tree placement does not start with its switch: " + first);
        PlacementContext context = new PlacementContext(venus, venus.getChunkSource().getGenerator(),
                Optional.of(tree));
        BlockPos origin = new ChunkPos(1_875, -1_875).getWorldPosition();
        helper.assertTrue(first.getPositions(context, RandomSource.create(1L), origin).count() == 1,
                "The switch blocked the tree while on");
        withSwitchOff(CommonConfig.CHARRED_TREES_ENABLED, () -> {
            for (long seed = 1; seed <= 64; seed++) {
                helper.assertTrue(positions(venus, tree, origin, seed).isEmpty(), "A charred tree ignored the switch");
            }
        });
        helper.succeed();
    }

    /**
     * The charcoal log drops one charcoal (itself with Silk Touch) and does not burn, as the legacy log; the geode
     * shell drops itself only for an iron pickaxe or better (legacy: the jackhammer at level 2).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void theCharcoalLogDropsCharcoalAndTheGeodeShellNeedsAnIronPickaxe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        BlockState log = SurfaceContent.CHARCOAL_LOG.get().defaultBlockState();
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        List<ItemStack> drops = Block.getDrops(log, level, position, null, null, axe);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.CHARCOAL) && drops.get(0).getCount() == 1,
                "The charcoal log dropped " + drops);
        axe.enchant(Enchantments.SILK_TOUCH, 1);
        drops = Block.getDrops(log, level, position, null, null, axe);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(SurfaceContent.CHARCOAL_LOG.get().asItem()),
                "With Silk Touch the charcoal log dropped " + drops);
        // It does not burn, as the legacy log (C15bR1-L2): fire neither spreads to it nor consumes it, and lava does
        // not light it.
        for (Direction face : Direction.values()) {
            helper.assertTrue(!log.isFlammable(level, position, face) && log.getFlammability(level, position, face) == 0
                    && log.getFireSpreadSpeed(level, position, face) == 0, "The charcoal log burns from " + face);
        }
        helper.assertTrue(!log.ignitedByLava(), "Lava lights the charcoal log");
        BlockState shell = SurfaceContent.GEODE_SHELL.get().defaultBlockState();
        helper.assertTrue(shell.requiresCorrectToolForDrops()
                        && !new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(shell)
                        && new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(shell),
                "The geode shell does not need an iron pickaxe");
        drops = Block.getDrops(shell, level, position, null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(SurfaceContent.GEODE_SHELL.get().asItem()),
                "The geode shell dropped " + drops);
        helper.succeed();
    }

    private static void switchStopsStarts(GameTestHelper helper, ServerLevel level, ResourceKey<Structure> key,
                                          ForgeConfigSpec.BooleanValue value) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(key);
        ChunkPos found = null;
        for (int i = 0; i < 64 && found == null; i++) {
            ChunkPos chunk = new ChunkPos(2_500 + i * 3, -2_500 - i * 5);
            if (structure.findValidGenerationPoint(context(level, structure, chunk)).isPresent()) {
                found = chunk;
            }
        }
        helper.assertTrue(found != null, key.location() + " found no generation point in 64 chunks");
        ChunkPos chunk = found;
        withSwitchOff(value, () -> helper.assertTrue(structure.findValidGenerationPoint(context(level, structure, chunk))
                .isEmpty(), key.location() + " ignored its switch"));
        helper.assertTrue(structure.findValidGenerationPoint(context(level, structure, chunk)).isPresent(),
                key.location() + " did not come back with its switch");
    }

    private static Structure.GenerationContext context(ServerLevel level, Structure structure, ChunkPos chunk) {
        var generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getServer().getStructureManager(), level.getSeed(), chunk,
                level, structure.biomes()::contains);
    }

    /**
     * Holds a switch off in memory for the duration of a check and always releases it; a config write would race
     * Forge's file reload (C15bR1-M4).
     */
    private static void withSwitchOff(ForgeConfigSpec.BooleanValue value, Runnable check) {
        SwitchOverrides.set(value, false);
        try {
            check.run();
        } finally {
            SwitchOverrides.clear(value);
        }
    }

    private static List<BlockPos> positions(ServerLevel level, PlacedFeature placed, BlockPos origin) {
        return positions(level, placed, origin, 42L);
    }

    /** The positions a placed feature would use at an origin, through its modifiers in order (as vanilla does). */
    private static List<BlockPos> positions(ServerLevel level, PlacedFeature placed, BlockPos origin, long seed) {
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(),
                Optional.of(placed));
        RandomSource random = RandomSource.create(seed);
        Stream<BlockPos> stream = Stream.of(origin);
        for (PlacementModifier modifier : placed.placement()) {
            stream = stream.flatMap(position -> modifier.getPositions(context, random, position));
        }
        return stream.toList();
    }

    private static void fill(ServerLevel level, BlockPos origin, Block upper, Block lower) {
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            level.setBlock(position, (position.getY() < origin.getY() ? lower : upper).defaultBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    private static int count(GameTestHelper helper, ServerLevel level, BlockPos origin, PlanetVein vein, Block rock,
                             Block other) {
        int count = 0;
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            BlockState state = level.getBlockState(position);
            Block expected = position.getY() < origin.getY() ? other : rock;
            if (state.is(vein.block())) {
                helper.assertTrue(expected == rock, vein.featureName() + " replaced " + other + " at " + position);
                helper.assertTrue(Math.abs(position.getX() - origin.getX()) <= VEIN_REACH
                                && Math.abs(position.getY() - origin.getY()) <= VEIN_REACH
                                && Math.abs(position.getZ() - origin.getZ()) <= VEIN_REACH,
                        vein.featureName() + " reached " + position + " from " + origin);
                count++;
            } else {
                helper.assertTrue(state.is(expected), vein.featureName() + " wrote " + state + " at " + position);
            }
        }
        return count;
    }

    private static ServerLevel level(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        helper.assertTrue(level != null, "Missing Level " + key.location());
        return level;
    }
}
