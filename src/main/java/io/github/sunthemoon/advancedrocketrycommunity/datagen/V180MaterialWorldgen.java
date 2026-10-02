package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.OverworldOres;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.OverworldOres.Vein;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.SwitchPlacement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraftforge.common.world.ForgeBiomeModifiers;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The Overworld ores of ADR-063 section 4 as data ({@link OverworldOres}): one configured and one placed feature per
 * vein, each placement starting with the {@code overworld_ores} server switch, and one Forge biome modifier that adds
 * them to {@code #minecraft:is_overworld}.
 */
public final class V180MaterialWorldgen {
    private V180MaterialWorldgen() {
    }

    public static DatapackBuiltinEntriesProvider provider(PackOutput output,
                                                          CompletableFuture<HolderLookup.Provider> registries) {
        return new DatapackBuiltinEntriesProvider(output, registries, builder(), Set.of(ModIdentity.MOD_ID));
    }

    private static RegistrySetBuilder builder() {
        return new RegistrySetBuilder()
                .add(Registries.CONFIGURED_FEATURE, context -> {
                    TagMatchTest stone = new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES);
                    TagMatchTest deepslate = new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
                    for (Vein vein : OverworldOres.VEINS) {
                        Material material = vein.material();
                        context.register(vein.configured(), new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(
                                List.of(OreConfiguration.target(stone, MaterialContent.block(
                                                material.stoneOreId().orElseThrow()).defaultBlockState()),
                                        OreConfiguration.target(deepslate, MaterialContent.block(
                                                material.deepslateOreId().orElseThrow()).defaultBlockState())),
                                vein.size())));
                    }
                })
                .add(Registries.PLACED_FEATURE, context -> {
                    var features = context.lookup(Registries.CONFIGURED_FEATURE);
                    for (Vein vein : OverworldOres.VEINS) {
                        context.register(vein.placed(), new PlacedFeature(features.getOrThrow(vein.configured()),
                                List.of(SwitchPlacement.of(SwitchPlacement.OVERWORLD_ORES),
                                        CountPlacement.of(vein.count()),
                                        InSquarePlacement.spread(),
                                        HeightRangePlacement.uniform(VerticalAnchor.absolute(OverworldOres.MIN_Y),
                                                VerticalAnchor.absolute(OverworldOres.MAX_Y)),
                                        BiomeFilter.biome())));
                    }
                })
                .add(ForgeRegistries.Keys.BIOME_MODIFIERS, context -> {
                    var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
                    context.register(OverworldOres.BIOME_MODIFIER, new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                            context.lookup(Registries.BIOME).getOrThrow(BiomeTags.IS_OVERWORLD),
                            HolderSet.direct(OverworldOres.VEINS.stream()
                                    .map(vein -> placedFeatures.getOrThrow(vein.placed())).toList()),
                            GenerationStep.Decoration.UNDERGROUND_ORES));
                });
    }
}
