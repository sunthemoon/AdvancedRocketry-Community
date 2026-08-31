package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class AtmosphereBlockTagsProvider extends BlockTagsProvider {
    public AtmosphereBlockTagsProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFiles
    ) {
        super(output, lookupProvider, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        tag(ModBlockTags.ATMOSPHERE_SEALING).add(
                ModBlocks.MACHINE_CASING.get(),
                ModBlocks.ELECTROLYZER.get(),
                ModBlocks.OXYGEN_VENT.get()
        );
        tag(ModBlockTags.ATMOSPHERE_PERMEABLE).add(
                Blocks.IRON_BARS,
                Blocks.GLASS_PANE,
                Blocks.OAK_FENCE
        );
    }
}
