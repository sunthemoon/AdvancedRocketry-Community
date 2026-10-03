package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import com.mojang.serialization.Codec;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The C15c world features of ADR-063 section 6 with revision 6 and the keys runtime code needs: the lightwood tree
 * (also grown by its sapling), the giant swamp tree, the inverted pillar and the crystal cluster, the six biomes, and
 * the landing ground's floor and filter.
 */
public final class ExoplanetWorldgen {
    /**
     * The landing ground around each Tau Ceti world's origin. The fixed landing pads are centred on chunk corners up
     * to 64 blocks out along each axis, a rocket's footprint (at most 16 chunks: 64 blocks wide) reaches 32 blocks
     * past its pad's centre along each axis, so its farthest corner lies about 136 blocks out, and a feature writes
     * up to 12 blocks from where it starts: no feature starts inside, and Tau Ceti f's ground there stays dry.
     */
    public static final int LANDING_GROUND_RADIUS = 160;

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES,
            ModIdentity.MOD_ID);
    public static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, ModIdentity.MOD_ID);
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(
            Registries.PLACEMENT_MODIFIER_TYPE, ModIdentity.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> LIGHTWOOD_TREE_FEATURE = FEATURES.register(
            "lightwood_tree", () -> new LightwoodTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> GIANT_SWAMP_TREE_FEATURE = FEATURES.register(
            "giant_swamp_tree", () -> new GiantSwampTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> INVERTED_PILLAR_FEATURE = FEATURES.register(
            "inverted_pillar", () -> new InvertedPillarFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CRYSTAL_CLUSTER_FEATURE = FEATURES.register(
            "crystal_cluster", () -> new CrystalClusterFeature(NoneFeatureConfiguration.CODEC));

    public static final RegistryObject<Codec<? extends DensityFunction>> LANDING_GROUND_FLOOR =
            DENSITY_FUNCTION_TYPES.register("landing_ground_floor", LandingGroundFloor.CODEC::codec);
    public static final RegistryObject<PlacementModifierType<LandingGroundFilter>> LANDING_GROUND_FILTER =
            PLACEMENT_MODIFIERS.register("landing_ground", () -> () -> LandingGroundFilter.CODEC);

    /** The configured lightwood tree, placed by worldgen and grown by the lightwood sapling. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> LIGHTWOOD_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE, ModIdentity.id("lightwood_tree"));

    public static final ResourceKey<Biome> ALIEN_FOREST = biome("alien_forest");
    public static final ResourceKey<Biome> MARSH = biome("marsh");
    public static final ResourceKey<Biome> DEEP_SWAMP = biome("deep_swamp");
    public static final ResourceKey<Biome> OCEAN_SPIRES = biome("ocean_spires");
    public static final ResourceKey<Biome> STORMLAND = biome("stormland");
    public static final ResourceKey<Biome> CRYSTAL_CHASMS = biome("crystal_chasms");

    private ExoplanetWorldgen() {
    }

    private static ResourceKey<Biome> biome(String path) {
        return ResourceKey.create(Registries.BIOME, ModIdentity.id(path));
    }

    public static void register(IEventBus modBus) {
        FEATURES.register(modBus);
        DENSITY_FUNCTION_TYPES.register(modBus);
        PLACEMENT_MODIFIERS.register(modBus);
    }
}
