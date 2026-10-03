package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.mojang.datafixers.util.Pair;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.LandingGroundFilter;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.LandingGroundFloor;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.PatchBiomeSource;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import io.github.sunthemoon.advancedrocketrycommunity.config.WorldgenSwitches;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.SwitchPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.data.worldgen.features.VegetationFeatures;
import net.minecraft.data.worldgen.placement.AquaticPlacements;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.SurfaceWaterDepthFilter;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The Tau Ceti f and g world data of ADR-063 section 6 with revision 6 (C15c) as registry bootstrap steps: the two
 * terrain noises and noise settings, six biomes with their legacy surfaces and colours, and the C15c features with
 * their placements, each behind its server switch.
 */
public final class V180ExoplanetWorldgen {
    public static final ResourceKey<NormalNoise.NoiseParameters> TAU_CETI_F_TERRAIN = key(Registries.NOISE,
            "tau_ceti_f_terrain");
    public static final ResourceKey<NormalNoise.NoiseParameters> TAU_CETI_G_TERRAIN = key(Registries.NOISE,
            "tau_ceti_g_terrain");
    public static final ResourceKey<NoiseGeneratorSettings> TAU_CETI_F = key(Registries.NOISE_SETTINGS, "tau_ceti_f");
    public static final ResourceKey<NoiseGeneratorSettings> TAU_CETI_G = key(Registries.NOISE_SETTINGS, "tau_ceti_g");

    /** Tau Ceti f: water up to y 62 and the top block at {@code 63 + 22n}; the biome bands follow {@code n}. */
    public static final int SEA_LEVEL = 63;
    public static final int F_SURFACE_MID = 63;
    public static final int F_SURFACE_SPAN = 22;
    public static final double OCEAN_BELOW = -0.3D;
    public static final double MARSH_BELOW = -0.1D;
    public static final double SWAMP_BELOW = 0.1D;
    /**
     * Tau Ceti f's landing ground: {@code n} is at least 0.2 (alien forest, top block y 67) within the landing ground
     * radius and falls by one per 96 blocks beyond it, reaching the sea about 24 blocks farther out.
     */
    public static final double LANDING_FLOOR = 0.2D;
    public static final int LANDING_SLOPE = 96;
    /** Tau Ceti g: a plateau with its top block at {@code 96 + 8n}. */
    public static final int G_SURFACE_MID = 96;
    public static final int G_SURFACE_SPAN = 8;
    /** Tau Ceti g's patches: cells of 32 quarts and a salt of its own ("TAUCETIG"). */
    public static final int G_PATCH_CELL = 32;
    public static final long G_PATCH_SALT = 0x5441554345544947L;

    public static final ResourceKey<ConfiguredFeature<?, ?>> GIANT_SWAMP_TREE = key(Registries.CONFIGURED_FEATURE,
            "giant_swamp_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> INVERTED_PILLAR = key(Registries.CONFIGURED_FEATURE,
            "inverted_pillar");
    public static final ResourceKey<ConfiguredFeature<?, ?>> CRYSTAL_CLUSTER = key(Registries.CONFIGURED_FEATURE,
            "crystal_cluster");
    public static final ResourceKey<ConfiguredFeature<?, ?>> ELECTRIC_MUSHROOMS = key(Registries.CONFIGURED_FEATURE,
            "electric_mushrooms");
    public static final ResourceKey<PlacedFeature> LIGHTWOOD_TREE_PLACED = key(Registries.PLACED_FEATURE,
            "lightwood_tree");
    public static final ResourceKey<PlacedFeature> GIANT_SWAMP_TREE_PLACED = key(Registries.PLACED_FEATURE,
            "giant_swamp_tree");
    public static final ResourceKey<PlacedFeature> INVERTED_PILLAR_PLACED = key(Registries.PLACED_FEATURE,
            "inverted_pillar");
    public static final ResourceKey<PlacedFeature> CRYSTAL_CLUSTER_PLACED = key(Registries.PLACED_FEATURE,
            "crystal_cluster");
    public static final ResourceKey<PlacedFeature> ELECTRIC_MUSHROOMS_PLACED = key(Registries.PLACED_FEATURE,
            "electric_mushrooms");
    public static final ResourceKey<PlacedFeature> STORMLAND_CHARRED_TREE = key(Registries.PLACED_FEATURE,
            "stormland_charred_tree");
    /** The vanilla jungle grass patch, kept off the landing ground. */
    public static final ResourceKey<PlacedFeature> ALIEN_FOREST_GRASS = key(Registries.PLACED_FEATURE,
            "alien_forest_grass");

    private V180ExoplanetWorldgen() {
    }

    public static void noise(BootstapContext<NormalNoise.NoiseParameters> context) {
        context.register(TAU_CETI_F_TERRAIN, new NormalNoise.NoiseParameters(-8, 1.0, 0.5, 0.25));
        context.register(TAU_CETI_G_TERRAIN, new NormalNoise.NoiseParameters(-7, 1.0, 0.5, 0.25));
    }

    public static void configured(BootstapContext<ConfiguredFeature<?, ?>> context) {
        context.register(ExoplanetWorldgen.LIGHTWOOD_TREE, none(ExoplanetWorldgen.LIGHTWOOD_TREE_FEATURE.get()));
        context.register(GIANT_SWAMP_TREE, none(ExoplanetWorldgen.GIANT_SWAMP_TREE_FEATURE.get()));
        context.register(INVERTED_PILLAR, none(ExoplanetWorldgen.INVERTED_PILLAR_FEATURE.get()));
        context.register(CRYSTAL_CLUSTER, none(ExoplanetWorldgen.CRYSTAL_CLUSTER_FEATURE.get()));
        // Legacy: 64 tries within 8 blocks sideways and 4 up or down, where the mushroom can stand.
        context.register(ELECTRIC_MUSHROOMS, new ConfiguredFeature<>(Feature.RANDOM_PATCH,
                FeatureUtils.simpleRandomPatchConfiguration(64, PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK,
                        new SimpleBlockConfiguration(BlockStateProvider.simple(
                                ExoplanetBlocks.ELECTRIC_MUSHROOM.get()))))));
    }

    private static ConfiguredFeature<NoneFeatureConfiguration, Feature<NoneFeatureConfiguration>> none(
            Feature<NoneFeatureConfiguration> feature) {
        return new ConfiguredFeature<>(feature, FeatureConfiguration.NONE);
    }

    /**
     * Every Tau Ceti feature passes its server switch, then its legacy numbers, and starts outside the landing ground
     * (revision 6).
     */
    public static void placed(BootstapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> features = context.lookup(Registries.CONFIGURED_FEATURE);
        LandingGroundFilter landing = new LandingGroundFilter(ExoplanetWorldgen.LANDING_GROUND_RADIUS);
        context.register(LIGHTWOOD_TREE_PLACED, new PlacedFeature(features.getOrThrow(ExoplanetWorldgen.LIGHTWOOD_TREE),
                List.of(SwitchPlacement.of(WorldgenSwitches.LIGHTWOOD_TREES), RarityFilter.onAverageOnceEvery(20),
                        InSquarePlacement.spread(), HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG),
                        SurfaceWaterDepthFilter.forMaxDepth(0), landing, BiomeFilter.biome())));
        context.register(GIANT_SWAMP_TREE_PLACED, new PlacedFeature(features.getOrThrow(GIANT_SWAMP_TREE),
                List.of(SwitchPlacement.of(WorldgenSwitches.SWAMP_TREES), RarityFilter.onAverageOnceEvery(100),
                        InSquarePlacement.spread(), HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG),
                        SurfaceWaterDepthFilter.forMaxDepth(3), landing, BiomeFilter.biome())));
        // Legacy 7 in 16 chunks; about one in two here.
        context.register(INVERTED_PILLAR_PLACED, new PlacedFeature(features.getOrThrow(INVERTED_PILLAR),
                List.of(SwitchPlacement.of(WorldgenSwitches.INVERTED_PILLARS), RarityFilter.onAverageOnceEvery(2),
                        InSquarePlacement.spread(), HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG),
                        landing, BiomeFilter.biome())));
        context.register(CRYSTAL_CLUSTER_PLACED, new PlacedFeature(features.getOrThrow(CRYSTAL_CLUSTER),
                List.of(SwitchPlacement.of(WorldgenSwitches.CRYSTAL_CLUSTERS), RarityFilter.onAverageOnceEvery(36),
                        InSquarePlacement.spread(), HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG),
                        landing, BiomeFilter.biome())));
        context.register(ELECTRIC_MUSHROOMS_PLACED, new PlacedFeature(features.getOrThrow(ELECTRIC_MUSHROOMS),
                List.of(SwitchPlacement.of(WorldgenSwitches.ELECTRIC_MUSHROOMS), InSquarePlacement.spread(),
                        HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG), landing, BiomeFilter.biome())));
        // Legacy: six charred trees per stormland chunk, with the C15b tree and its switch.
        context.register(STORMLAND_CHARRED_TREE, new PlacedFeature(features.getOrThrow(V180PlanetWorldgen.CHARRED_TREE),
                List.<PlacementModifier>of(SwitchPlacement.of(WorldgenSwitches.CHARRED_TREES), CountPlacement.of(6),
                        InSquarePlacement.spread(), HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG),
                        landing, BiomeFilter.biome())));
        // Vanilla's jungle grass patch with its own placement (25 per chunk on the surface), off the landing ground.
        context.register(ALIEN_FOREST_GRASS, new PlacedFeature(features.getOrThrow(VegetationFeatures.PATCH_GRASS_JUNGLE),
                List.of(CountPlacement.of(25), InSquarePlacement.spread(),
                        HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG), landing, BiomeFilter.biome())));
    }

    public static void biomes(BootstapContext<Biome> context) {
        HolderGetter<PlacedFeature> placed = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
        GenerationStep.Decoration vegetal = GenerationStep.Decoration.VEGETAL_DECORATION;
        GenerationStep.Decoration local = GenerationStep.Decoration.LOCAL_MODIFICATIONS;

        BiomeGenerationSettings.Builder forest = new BiomeGenerationSettings.Builder(placed, carvers);
        forest.addFeature(vegetal, placed.getOrThrow(LIGHTWOOD_TREE_PLACED));
        forest.addFeature(vegetal, placed.getOrThrow(ALIEN_FOREST_GRASS));
        context.register(ExoplanetWorldgen.ALIEN_FOREST, biome(0.5F, 0.5F, new BiomeSpecialEffects.Builder()
                .fogColor(0xC0D8FF).skyColor(0x5FA8C8).waterColor(0x8888FF).waterFogColor(0x050533)
                .grassColorOverride(0x7777FF).foliageColorOverride(0x55FFE1), forest));

        BiomeGenerationSettings.Builder marsh = new BiomeGenerationSettings.Builder(placed, carvers);
        marsh.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, placed.getOrThrow(MiscOverworldPlacements.DISK_CLAY));
        marsh.addFeature(vegetal, placed.getOrThrow(VegetationPlacements.PATCH_WATERLILY));
        context.register(ExoplanetWorldgen.MARSH, biome(0.5F, 0.5F, new BiomeSpecialEffects.Builder()
                .fogColor(0xC0D8FF).skyColor(0x5FA8C8).waterColor(0x3F76E4).waterFogColor(0x050533), marsh));

        BiomeGenerationSettings.Builder swamp = new BiomeGenerationSettings.Builder(placed, carvers);
        swamp.addFeature(vegetal, placed.getOrThrow(GIANT_SWAMP_TREE_PLACED));
        for (ResourceKey<PlacedFeature> vanilla : List.of(VegetationPlacements.TREES_SWAMP,
                VegetationPlacements.FLOWER_SWAMP, VegetationPlacements.PATCH_GRASS_NORMAL,
                VegetationPlacements.BROWN_MUSHROOM_SWAMP, VegetationPlacements.RED_MUSHROOM_SWAMP,
                VegetationPlacements.PATCH_SUGAR_CANE_SWAMP, VegetationPlacements.PATCH_WATERLILY)) {
            swamp.addFeature(vegetal, placed.getOrThrow(vanilla));
        }
        context.register(ExoplanetWorldgen.DEEP_SWAMP, biome(0.9F, 0.9F, new BiomeSpecialEffects.Builder()
                .fogColor(0xA0B8A0).skyColor(0x203020).waterColor(0xE0FFAE).waterFogColor(0x232317), swamp));

        BiomeGenerationSettings.Builder spires = new BiomeGenerationSettings.Builder(placed, carvers);
        spires.addFeature(local, placed.getOrThrow(INVERTED_PILLAR_PLACED));
        spires.addFeature(vegetal, placed.getOrThrow(AquaticPlacements.SEAGRASS_NORMAL));
        context.register(ExoplanetWorldgen.OCEAN_SPIRES, biome(0.5F, 0.5F, new BiomeSpecialEffects.Builder()
                .fogColor(0xC0D8FF).skyColor(0x5FA8C8).waterColor(0x3F76E4).waterFogColor(0x050533), spires));

        BiomeGenerationSettings.Builder storm = new BiomeGenerationSettings.Builder(placed, carvers);
        storm.addFeature(vegetal, placed.getOrThrow(STORMLAND_CHARRED_TREE));
        storm.addFeature(vegetal, placed.getOrThrow(ELECTRIC_MUSHROOMS_PLACED));
        context.register(ExoplanetWorldgen.STORMLAND, biome(0.9F, 0.9F, new BiomeSpecialEffects.Builder()
                .fogColor(0x2A2A2E).skyColor(0x202020).waterColor(0x3F76E4).waterFogColor(0x050533)
                .grassColorOverride(0x202020).foliageColorOverride(0x202020), storm));

        BiomeGenerationSettings.Builder chasms = new BiomeGenerationSettings.Builder(placed, carvers);
        chasms.addFeature(local, placed.getOrThrow(CRYSTAL_CLUSTER_PLACED));
        context.register(ExoplanetWorldgen.CRYSTAL_CHASMS, biome(0.1F, 0.2F, new BiomeSpecialEffects.Builder()
                .fogColor(0x2A2A2E).skyColor(0x202020).waterColor(0x3D57D6).waterFogColor(0x050533), chasms));
    }

    /** Rain (snow when cold), legacy temperature and downfall, and nothing spawns (revision 6). */
    private static Biome biome(float temperature, float downfall, BiomeSpecialEffects.Builder effects,
                               BiomeGenerationSettings.Builder generation) {
        return new Biome.BiomeBuilder().hasPrecipitation(true).temperature(temperature).downfall(downfall)
                .specialEffects(effects.build()).mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(generation.build()).build();
    }

    /**
     * Both Levels have low relief from one 2D noise {@code n}, as the Moon: the density is positive below
     * {@code S = mid + 1 + span n}, so the top block lies at {@code mid + span n}. On f the same noise is the
     * continentalness the multi-noise source reads, so the biome bands follow the ground, and {@code n} is raised to
     * the landing ground floor (revision 6), so the fixed landing pads stand on dry alien forest.
     */
    public static void noiseSettings(BootstapContext<NoiseGeneratorSettings> context) {
        HolderGetter<NormalNoise.NoiseParameters> noises = context.lookup(Registries.NOISE);
        context.register(TAU_CETI_F, new NoiseGeneratorSettings(NoiseSettings.create(0, 256, 1, 2),
                Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                router(DensityFunctions.max(ground(noises.getOrThrow(TAU_CETI_F_TERRAIN)), new LandingGroundFloor(
                        ExoplanetWorldgen.LANDING_GROUND_RADIUS, LANDING_SLOPE, LANDING_FLOOR)), F_SURFACE_MID,
                        F_SURFACE_SPAN), fSurface(), List.of(),
                SEA_LEVEL, true, false, false, false));
        context.register(TAU_CETI_G, new NoiseGeneratorSettings(NoiseSettings.create(0, 256, 1, 2),
                Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(),
                router(ground(noises.getOrThrow(TAU_CETI_G_TERRAIN)), G_SURFACE_MID, G_SURFACE_SPAN), gSurface(),
                List.of(),
                0, true, false, false, false));
    }

    private static DensityFunction ground(Holder<NormalNoise.NoiseParameters> noise) {
        return DensityFunctions.noise(noise, 1.0, 0.0).clamp(-1.0, 1.0);
    }

    private static NoiseRouter router(DensityFunction n, int mid, int span) {
        DensityFunction surface = DensityFunctions.add(DensityFunctions.constant(mid + 1),
                DensityFunctions.mul(DensityFunctions.constant(span), n));
        DensityFunction height = DensityFunctions.yClampedGradient(0, 256, 0, 256);
        DensityFunction density = DensityFunctions.mul(DensityFunctions.constant(0.125),
                DensityFunctions.add(surface, DensityFunctions.mul(DensityFunctions.constant(-1), height)));
        DensityFunction zero = DensityFunctions.zero();
        return new NoiseRouter(zero, zero, zero, zero, zero, zero, n, zero, zero, zero,
                density, DensityFunctions.interpolated(density), zero, zero, zero);
    }

    /** Grass on dry ground, dirt under water and below the top; gravel in the ocean spires. */
    static SurfaceRules.RuleSource fSurface() {
        SurfaceRules.RuleSource gravel = SurfaceRules.state(Blocks.GRAVEL.defaultBlockState());
        return SurfaceRules.sequence(V180PlanetWorldgen.bedrock("tau_ceti_bedrock"),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(ExoplanetWorldgen.OCEAN_SPIRES), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, gravel),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, gravel))),
                soil());
    }

    /** Snow block over packed ice in the crystal chasms; grass over dirt in the stormland. */
    static SurfaceRules.RuleSource gSurface() {
        return SurfaceRules.sequence(V180PlanetWorldgen.bedrock("tau_ceti_bedrock"),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(ExoplanetWorldgen.CRYSTAL_CHASMS), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                                SurfaceRules.state(Blocks.SNOW_BLOCK.defaultBlockState())),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR,
                                SurfaceRules.state(Blocks.PACKED_ICE.defaultBlockState())))),
                soil());
    }

    private static SurfaceRules.RuleSource soil() {
        SurfaceRules.RuleSource dirt = SurfaceRules.state(Blocks.DIRT.defaultBlockState());
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.waterBlockCheck(-1, 0),
                                SurfaceRules.state(Blocks.GRASS_BLOCK.defaultBlockState())),
                        dirt)),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dirt));
    }

    /** As Mars's: skylight, a day cycle, heights 0-256 and the planetary sky effects. */
    public static void dimensionTypes(BootstapContext<DimensionType> context) {
        for (ResourceLocation id : List.of(ExoplanetContent.TAU_CETI_F, ExoplanetContent.TAU_CETI_G)) {
            context.register(ResourceKey.create(Registries.DIMENSION_TYPE, id), new DimensionType(OptionalLong.empty(),
                    true, false, false, false, 1, true, false, 0, 256, 256, BlockTags.INFINIBURN_OVERWORLD,
                    SkyProfiles.SURFACE_EFFECTS, 0, new DimensionType.MonsterSettings(false, false,
                    ConstantInt.of(0), 0)));
        }
    }

    /**
     * Tau Ceti f reads the four biomes from continentalness (its terrain noise); Tau Ceti g lays its two biomes in
     * irregular patches with their own salt.
     */
    public static void levels(BootstapContext<LevelStem> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<DimensionType> types = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> settings = context.lookup(Registries.NOISE_SETTINGS);
        Climate.Parameter any = Climate.Parameter.span(-1.0F, 1.0F);
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> bands = new ArrayList<>();
        double[][] ranges = {{-1.0D, OCEAN_BELOW}, {OCEAN_BELOW, MARSH_BELOW}, {MARSH_BELOW, SWAMP_BELOW},
                {SWAMP_BELOW, 1.0D}};
        List<ResourceKey<Biome>> order = List.of(ExoplanetWorldgen.OCEAN_SPIRES, ExoplanetWorldgen.MARSH,
                ExoplanetWorldgen.DEEP_SWAMP, ExoplanetWorldgen.ALIEN_FOREST);
        for (int band = 0; band < ranges.length; band++) {
            bands.add(Pair.of(Climate.parameters(any, any, Climate.Parameter.span((float) ranges[band][0],
                    (float) ranges[band][1]), any, Climate.Parameter.point(0.0F), any, 0.0F),
                    biomes.getOrThrow(order.get(band))));
        }
        context.register(ResourceKey.create(Registries.LEVEL_STEM, ExoplanetContent.TAU_CETI_F), new LevelStem(
                types.getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, ExoplanetContent.TAU_CETI_F)),
                new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(bands)),
                        settings.getOrThrow(TAU_CETI_F))));
        context.register(ResourceKey.create(Registries.LEVEL_STEM, ExoplanetContent.TAU_CETI_G), new LevelStem(
                types.getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, ExoplanetContent.TAU_CETI_G)),
                new NoiseBasedChunkGenerator(new PatchBiomeSource(List.of(biomes.getOrThrow(ExoplanetWorldgen.STORMLAND),
                        biomes.getOrThrow(ExoplanetWorldgen.CRYSTAL_CHASMS)), G_PATCH_CELL, G_PATCH_SALT),
                        settings.getOrThrow(TAU_CETI_G))));
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return ResourceKey.create(registry, ModIdentity.id(path));
    }
}
