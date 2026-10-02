package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.CraterStructure;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.GeodeStructure;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.SurfaceWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.VolcanoStructure;
import io.github.sunthemoon.advancedrocketrycommunity.config.WorldgenSwitches;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.SwitchPlacement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The Moon, Mars and Venus world data of ADR-063 section 5 (C15b) as registry bootstrap steps: five biomes with the
 * legacy top and filler blocks, the Moon's terrain noise and noise settings (surface y 12–36), the Moon and Mars ore
 * sets of section 4, the Venus charred trees, and the crater, volcano and geode structures with their sets.
 */
public final class V180PlanetWorldgen {
    public static final ResourceKey<Biome> REGOLITH_HIGHLANDS = key(Registries.BIOME, "regolith_highlands");
    public static final ResourceKey<Biome> REGOLITH_LOWLANDS = key(Registries.BIOME, "regolith_lowlands");
    public static final ResourceKey<Biome> FERRIC_REGOLITH = key(Registries.BIOME, "ferric_regolith");
    public static final ResourceKey<Biome> VOLCANIC = key(Registries.BIOME, "volcanic");
    public static final ResourceKey<Biome> VOLCANIC_LOWLANDS = key(Registries.BIOME, "volcanic_lowlands");
    public static final ResourceKey<NormalNoise.NoiseParameters> MOON_TERRAIN = key(Registries.NOISE, "moon_terrain");
    public static final ResourceKey<NoiseGeneratorSettings> MOON = key(Registries.NOISE_SETTINGS, "moon");

    /** Moon terrain bounds (ADR-063 section 5): lowlands y 12–20, highlands y 20–36. */
    public static final int MOON_SURFACE_MIN = 12;
    public static final int MOON_SURFACE_MID = 20;
    public static final int MOON_SURFACE_MAX = 36;
    public static final int CRATER_FLOOR_MIN = 5;
    public static final int MOON_RIM_MAX = 44;
    public static final int MARS_RIM_MAX = 250;
    public static final int PLANET_ORE_MIN_Y = 4;
    public static final int PLANET_ORE_MAX_Y = 40;

    public static final ResourceKey<ConfiguredFeature<?, ?>> CHARRED_TREE = key(Registries.CONFIGURED_FEATURE,
            "charred_tree");
    public static final ResourceKey<PlacedFeature> CHARRED_TREE_PLACED = key(Registries.PLACED_FEATURE,
            "charred_tree");
    public static final ResourceKey<Structure> MOON_CRATER = key(Registries.STRUCTURE, "moon_crater");
    public static final ResourceKey<Structure> MARS_CRATER = key(Registries.STRUCTURE, "mars_crater");
    public static final ResourceKey<Structure> VOLCANO = key(Registries.STRUCTURE, "volcano");
    public static final ResourceKey<Structure> GEODE = key(Registries.STRUCTURE, "geode");
    public static final TagKey<Biome> HAS_MOON_CRATER = biomeTag("has_structure/moon_crater");
    public static final TagKey<Biome> HAS_MARS_CRATER = biomeTag("has_structure/mars_crater");
    public static final TagKey<Biome> HAS_VOLCANO = biomeTag("has_structure/volcano");
    public static final TagKey<Biome> HAS_GEODE = biomeTag("has_structure/geode");

    /** One Moon or Mars ore: the body, ore block, veins per chunk and blocks per vein (ADR-063 section 4). */
    public record PlanetVein(String body, String ore, Block block, int count, int size) {
        public String featureName() {
            return body + "_" + ore + "_ore";
        }

        public ResourceKey<ConfiguredFeature<?, ?>> configured() {
            return key(Registries.CONFIGURED_FEATURE, featureName());
        }

        public ResourceKey<PlacedFeature> placed() {
            return key(Registries.PLACED_FEATURE, featureName());
        }
    }

    private V180PlanetWorldgen() {
    }

    /** The Moon and Mars veins; dilithium follows the legacy airless rule (10 on the Moon, 1 on Mars). */
    public static List<PlanetVein> veins() {
        List<PlanetVein> veins = new ArrayList<>();
        for (String body : List.of("moon", "mars")) {
            veins.add(new PlanetVein(body, "copper", Blocks.COPPER_ORE, 10, 6));
            veins.add(new PlanetVein(body, "tin", MaterialContent.block("tin_ore"), 10, 6));
            veins.add(new PlanetVein(body, "rutile", MaterialContent.block("rutile_ore"), 6, 6));
            veins.add(new PlanetVein(body, "aluminum", MaterialContent.block("aluminum_ore"), 1, 16));
            veins.add(new PlanetVein(body, "iridium", MaterialContent.block("iridium_ore"), 1, 16));
            veins.add(new PlanetVein(body, "dilithium", MaterialContent.block("dilithium_ore"),
                    body.equals("moon") ? 10 : 1, 16));
        }
        return veins;
    }

    public static void noise(BootstapContext<NormalNoise.NoiseParameters> context) {
        context.register(MOON_TERRAIN, new NormalNoise.NoiseParameters(-7, 1.0, 0.5, 0.25));
    }

    public static void configured(BootstapContext<ConfiguredFeature<?, ?>> context) {
        RuleTest moonRock = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
        RuleTest marsRock = new BlockMatchTest(Blocks.RED_SANDSTONE);
        for (PlanetVein vein : veins()) {
            RuleTest target = vein.body().equals("moon") ? moonRock : marsRock;
            context.register(vein.configured(), new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(
                    target, vein.block().defaultBlockState(), vein.size())));
        }
        context.register(CHARRED_TREE, new ConfiguredFeature<NoneFeatureConfiguration, Feature<NoneFeatureConfiguration>>(
                SurfaceWorldgen.CHARRED_TREE.get(), FeatureConfiguration.NONE));
    }

    public static void placed(BootstapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> features = context.lookup(Registries.CONFIGURED_FEATURE);
        for (PlanetVein vein : veins()) {
            context.register(vein.placed(), new PlacedFeature(features.getOrThrow(vein.configured()), List.of(
                    SwitchPlacement.of(WorldgenSwitches.PLANET_ORES),
                    CountPlacement.of(vein.count()),
                    InSquarePlacement.spread(),
                    HeightRangePlacement.uniform(VerticalAnchor.absolute(PLANET_ORE_MIN_Y),
                            VerticalAnchor.absolute(PLANET_ORE_MAX_Y)),
                    BiomeFilter.biome())));
        }
        // Legacy: no trees per chunk, plus the decorator's one-in-ten extra tree.
        context.register(CHARRED_TREE_PLACED, new PlacedFeature(features.getOrThrow(CHARRED_TREE), List.<PlacementModifier>of(
                SwitchPlacement.of(WorldgenSwitches.CHARRED_TREES),
                RarityFilter.onAverageOnceEvery(10),
                InSquarePlacement.spread(),
                HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG),
                BiomeFilter.biome())));
    }

    public static void biomes(BootstapContext<Biome> context) {
        HolderGetter<PlacedFeature> placed = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<ConfiguredWorldCarver<?>> carvers = context.lookup(Registries.CONFIGURED_CARVER);
        context.register(REGOLITH_HIGHLANDS, biome(0.3F, 0x101014, 0x000000, ores(placed, carvers, "moon")));
        context.register(REGOLITH_LOWLANDS, biome(0.3F, 0x101014, 0x000000, ores(placed, carvers, "moon")));
        context.register(FERRIC_REGOLITH, biome(0.9F, 0xBE8C74, 0x9C746A, ores(placed, carvers, "mars")));
        BiomeGenerationSettings.Builder volcanic = new BiomeGenerationSettings.Builder(placed, carvers);
        volcanic.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, placed.getOrThrow(CHARRED_TREE_PLACED));
        context.register(VOLCANIC, biome(1.0F, 0xBCAA62, 0xA59B65, volcanic));
        context.register(VOLCANIC_LOWLANDS, biome(1.0F, 0xBCAA62, 0xA59B65,
                new BiomeGenerationSettings.Builder(placed, carvers)));
    }

    private static BiomeGenerationSettings.Builder ores(HolderGetter<PlacedFeature> placed,
                                                        HolderGetter<ConfiguredWorldCarver<?>> carvers, String body) {
        BiomeGenerationSettings.Builder settings = new BiomeGenerationSettings.Builder(placed, carvers);
        for (PlanetVein vein : veins()) {
            if (vein.body().equals(body)) {
                settings.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, placed.getOrThrow(vein.placed()));
            }
        }
        return settings;
    }

    private static Biome biome(float temperature, int fog, int sky, BiomeGenerationSettings.Builder generation) {
        return new Biome.BiomeBuilder().hasPrecipitation(false).temperature(temperature).downfall(0)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(fog).skyColor(sky)
                        .waterColor(0x625B46).waterFogColor(0x38352B).build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(generation.build()).build();
    }

    public static void structures(BootstapContext<Structure> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        context.register(MOON_CRATER, new CraterStructure(settings(biomes, HAS_MOON_CRATER,
                GenerationStep.Decoration.RAW_GENERATION), 8, 48, CRATER_FLOOR_MIN, MOON_RIM_MAX));
        context.register(MARS_CRATER, new CraterStructure(settings(biomes, HAS_MARS_CRATER,
                GenerationStep.Decoration.RAW_GENERATION), 8, 48, CRATER_FLOOR_MIN, MARS_RIM_MAX));
        context.register(VOLCANO, new VolcanoStructure(settings(biomes, HAS_VOLCANO,
                GenerationStep.Decoration.SURFACE_STRUCTURES)));
        context.register(GEODE, new GeodeStructure(settings(biomes, HAS_GEODE,
                GenerationStep.Decoration.UNDERGROUND_STRUCTURES)));
    }

    private static Structure.StructureSettings settings(HolderGetter<Biome> biomes, TagKey<Biome> tag,
                                                        GenerationStep.Decoration step) {
        return new Structure.StructureSettings(biomes.getOrThrow(tag), Map.of(), step, TerrainAdjustment.NONE);
    }

    public static void structureSets(BootstapContext<StructureSet> context) {
        HolderGetter<Structure> structures = context.lookup(Registries.STRUCTURE);
        context.register(key(Registries.STRUCTURE_SET, "moon_craters"), new StructureSet(
                structures.getOrThrow(MOON_CRATER), new RandomSpreadStructurePlacement(6, 3, RandomSpreadType.LINEAR,
                        180_501)));
        context.register(key(Registries.STRUCTURE_SET, "mars_craters"), new StructureSet(
                structures.getOrThrow(MARS_CRATER), new RandomSpreadStructurePlacement(6, 3, RandomSpreadType.LINEAR,
                        180_502)));
        context.register(key(Registries.STRUCTURE_SET, "volcanoes"), new StructureSet(
                structures.getOrThrow(VOLCANO), new RandomSpreadStructurePlacement(16, 8, RandomSpreadType.LINEAR,
                        180_503)));
        context.register(key(Registries.STRUCTURE_SET, "geodes"), new StructureSet(
                structures.getOrThrow(GEODE), new RandomSpreadStructurePlacement(8, 4, RandomSpreadType.LINEAR,
                        180_504)));
    }

    /**
     * The Moon: a two-biome noise Level of low relief. One 2D noise {@code n} in [-1, 1] sets the top block at
     * {@code 20 + 12n + 4|n|} (y 12–20 where n < 0, the lowlands; y 20–36 where n ≥ 0, the highlands) and is also the
     * continentalness the biome source reads, so the lowland biome lies exactly on the low ground.
     */
    public static void noiseSettings(BootstapContext<NoiseGeneratorSettings> context) {
        DensityFunction n = DensityFunctions.noise(context.lookup(Registries.NOISE).getOrThrow(MOON_TERRAIN), 1.0, 0.0)
                .clamp(-1.0, 1.0);
        // The density is positive below S, so the top block is S - 1: S = 21 + 12n + 4|n| puts it at y 12-36.
        DensityFunction surface = DensityFunctions.add(DensityFunctions.constant(MOON_SURFACE_MID + 1),
                DensityFunctions.add(DensityFunctions.mul(DensityFunctions.constant(12), n),
                        DensityFunctions.mul(DensityFunctions.constant(4), n.abs())));
        DensityFunction height = DensityFunctions.yClampedGradient(0, 256, 0, 256);
        DensityFunction density = DensityFunctions.mul(DensityFunctions.constant(0.125),
                DensityFunctions.add(surface, DensityFunctions.mul(DensityFunctions.constant(-1), height)));
        DensityFunction zero = DensityFunctions.zero();
        NoiseRouter router = new NoiseRouter(zero, zero, zero, zero, zero, zero, n, zero, zero, zero,
                density, DensityFunctions.interpolated(density), zero, zero, zero);
        context.register(MOON, new NoiseGeneratorSettings(NoiseSettings.create(0, 256, 1, 2),
                Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), router, moonSurface(), List.of(), 0,
                true, false, false, false));
    }

    static SurfaceRules.RuleSource bedrock(String name) {
        return SurfaceRules.ifTrue(SurfaceRules.verticalGradient(ModIdentity.MOD_ID + ":" + name,
                VerticalAnchor.absolute(0), VerticalAnchor.absolute(4)),
                SurfaceRules.state(Blocks.BEDROCK.defaultBlockState()));
    }

    /** Legacy top and filler blocks are the turf itself, over vanilla stone. */
    static SurfaceRules.RuleSource moonSurface() {
        SurfaceRules.RuleSource light = SurfaceRules.state(SurfaceContent.MOON_TURF.get().defaultBlockState());
        SurfaceRules.RuleSource dark = SurfaceRules.state(SurfaceContent.DARK_MOON_TURF.get().defaultBlockState());
        return SurfaceRules.sequence(bedrock("moon_bedrock"),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(REGOLITH_LOWLANDS), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, dark),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dark))),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(REGOLITH_HIGHLANDS), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, light),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, light))));
    }

    /** Mars: ferric sand as top and filler over the v1.4 base block, red sandstone. */
    static SurfaceRules.RuleSource marsSurface() {
        SurfaceRules.RuleSource sand = SurfaceRules.state(SurfaceContent.FERRIC_SAND.get().defaultBlockState());
        return SurfaceRules.sequence(bedrock("planetary_bedrock"),
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, sand),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, sand),
                SurfaceRules.state(Blocks.RED_SANDSTONE.defaultBlockState()));
    }

    /** Venus: basalt top and filler, the v1.4 base block. */
    static SurfaceRules.RuleSource venusSurface() {
        return SurfaceRules.sequence(bedrock("planetary_bedrock"), SurfaceRules.state(Blocks.BASALT.defaultBlockState()));
    }

    private static TagKey<Biome> biomeTag(String path) {
        return TagKey.create(Registries.BIOME, ModIdentity.id(path));
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return ResourceKey.create(registry, ModIdentity.id(path));
    }
}
