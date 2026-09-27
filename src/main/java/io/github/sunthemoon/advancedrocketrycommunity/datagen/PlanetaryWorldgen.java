package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Original terrain rules exported as vanilla startup registry data, not runtime registration. */
public final class PlanetaryWorldgen {
    private static final List<ResourceLocation> SURFACES = List.of(PlanetaryContent.MARS, PlanetaryContent.VENUS);

    private PlanetaryWorldgen() {
    }

    public static RegistrySetBuilder builder() {
        return new RegistrySetBuilder()
                .add(Registries.NOISE, context -> {
                    context.register(noiseKey(PlanetaryContent.MARS), new NormalNoise.NoiseParameters(-6, 1, 0.5));
                    context.register(noiseKey(PlanetaryContent.VENUS), new NormalNoise.NoiseParameters(-5, 1, 0.75, 0.5));
                })
                .add(Registries.BIOME, context -> SURFACES.forEach(id -> context.register(key(Registries.BIOME, id), biome(id))))
                .add(Registries.DIMENSION_TYPE, context -> SURFACES.forEach(id -> context.register(
                        key(Registries.DIMENSION_TYPE, id), new DimensionType(OptionalLong.empty(),
                                true, false, false, false, 1, true, false, 0, 256, 256,
                                BlockTags.INFINIBURN_OVERWORLD, SkyProfiles.SURFACE_EFFECTS,
                                0, new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)))))
                .add(Registries.NOISE_SETTINGS, context -> SURFACES.forEach(id -> context.register(
                        key(Registries.NOISE_SETTINGS, id), terrain(context, id))))
                .add(Registries.LEVEL_STEM, context -> SURFACES.forEach(id -> context.register(
                        key(Registries.LEVEL_STEM, id), new LevelStem(
                                context.lookup(Registries.DIMENSION_TYPE).getOrThrow(key(Registries.DIMENSION_TYPE, id)),
                                new NoiseBasedChunkGenerator(new FixedBiomeSource(
                                        context.lookup(Registries.BIOME).getOrThrow(key(Registries.BIOME, id))),
                                        context.lookup(Registries.NOISE_SETTINGS).getOrThrow(key(Registries.NOISE_SETTINGS, id)))))));
    }

    private static Biome biome(ResourceLocation id) {
        boolean mars = id.equals(PlanetaryContent.MARS);
        return new Biome.BiomeBuilder().hasPrecipitation(false).temperature(mars ? 0.1F : 2.0F).downfall(0)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(mars ? 0xBE8C74 : 0xBCAA62).skyColor(mars ? 0x9C746A : 0xA59B65)
                        .waterColor(0x625B46).waterFogColor(0x38352B).build())
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .generationSettings(new BiomeGenerationSettings.PlainBuilder().build()).build();
    }

    private static NoiseGeneratorSettings terrain(BootstapContext<NoiseGeneratorSettings> context, ResourceLocation id) {
        boolean mars = id.equals(PlanetaryContent.MARS);
        var noise = DensityFunctions.noise(context.lookup(Registries.NOISE).getOrThrow(noiseKey(id)), 1, 0);
        var relief = mars ? DensityFunctions.mul(DensityFunctions.constant(0.45), noise)
                : DensityFunctions.mul(DensityFunctions.constant(1.1), noise.abs());
        var density = DensityFunctions.add(DensityFunctions.yClampedGradient(24, mars ? 112 : 152, 1, -1), relief);
        var zero = DensityFunctions.zero();
        var router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                density, DensityFunctions.interpolated(density), zero, zero, zero);
        var rock = (mars ? Blocks.RED_SANDSTONE : Blocks.BASALT).defaultBlockState();
        var top = (mars ? Blocks.RED_SAND : Blocks.YELLOW_TERRACOTTA).defaultBlockState();
        var surface = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.verticalGradient("advancedrocketrycommunity:planetary_bedrock",
                        VerticalAnchor.absolute(0), VerticalAnchor.absolute(4)), SurfaceRules.state(Blocks.BEDROCK.defaultBlockState())),
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.state(top)), SurfaceRules.state(rock));
        return new NoiseGeneratorSettings(NoiseSettings.create(0, 256, 1, 2), rock,
                Blocks.AIR.defaultBlockState(), router, surface, List.of(), 0, true, false, false, false);
    }

    private static ResourceKey<NormalNoise.NoiseParameters> noiseKey(ResourceLocation id) {
        return key(Registries.NOISE, ModIdentity.id(id.getPath() + "_terrain"));
    }

    private static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        return ResourceKey.create(registry, id);
    }
}
