package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile casing = models().orientableWithBottom(
                "machine_casing",
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_front"),
                modLoc("block/machine_casing_top"),
                modLoc("block/machine_casing_top")
        );
        horizontalBlock(ModBlocks.MACHINE_CASING.get(), casing);
        simpleBlockItem(ModBlocks.MACHINE_CASING.get(), casing);
    }
}
