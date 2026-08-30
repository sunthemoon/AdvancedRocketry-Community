package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItemTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagsProvider.TagLookup<Block>> blockTags,
            ExistingFileHelper existingFileHelper
    ) {
        super(output, lookupProvider, blockTags, AdvancedRocketryCommunity.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        tag(ModItemTags.SILICON_WAFERS).add(ModItems.SILICON_WAFER.get());
        tag(ModItemTags.BASIC_CIRCUITS).add(ModItems.BASIC_CIRCUIT.get());
        tag(ModItemTags.ADVANCED_CIRCUITS).add(ModItems.ADVANCED_CIRCUIT.get());
        tag(ModItemTags.DATA_STORAGE_UNITS).add(ModItems.DATA_STORAGE_UNIT.get());
        tag(ModItemTags.MACHINE_CASINGS).add(ModItems.MACHINE_CASING.get());
    }
}
