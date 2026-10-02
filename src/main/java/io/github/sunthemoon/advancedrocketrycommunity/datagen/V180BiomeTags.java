package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * The biome tags that place the C15b structures (ADR-063 section 5): craters on the two Moon biomes and on Mars,
 * volcanoes and geodes on both Venus biomes. Built over the registry provider's lookup, which holds the new biomes.
 */
public final class V180BiomeTags extends BiomeTagsProvider {
    public V180BiomeTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                         ExistingFileHelper existingFiles) {
        super(output, lookup, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(V180PlanetWorldgen.HAS_MOON_CRATER).add(V180PlanetWorldgen.REGOLITH_HIGHLANDS,
                V180PlanetWorldgen.REGOLITH_LOWLANDS);
        tag(V180PlanetWorldgen.HAS_MARS_CRATER).add(V180PlanetWorldgen.FERRIC_REGOLITH);
        tag(V180PlanetWorldgen.HAS_VOLCANO).add(V180PlanetWorldgen.VOLCANIC, V180PlanetWorldgen.VOLCANIC_LOWLANDS);
        tag(V180PlanetWorldgen.HAS_GEODE).add(V180PlanetWorldgen.VOLCANIC, V180PlanetWorldgen.VOLCANIC_LOWLANDS);
    }
}
