package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class RollingMachineBlockTagsProvider extends BlockTagsProvider {
    public RollingMachineBlockTagsProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFiles
    ) {
        super(output, lookupProvider, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.MACHINE_CASING.get(),
                ModBlocks.SATELLITE_TERMINAL.get(),
                ModBlocks.ROLLING_MACHINE.get(),
                ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get()
        );
        tag(BlockTags.NEEDS_IRON_TOOL).add(
                ModBlocks.MACHINE_CASING.get(),
                ModBlocks.SATELLITE_TERMINAL.get(),
                ModBlocks.ROLLING_MACHINE.get(),
                ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get()
        );
    }
}
