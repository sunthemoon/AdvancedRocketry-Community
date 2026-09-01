package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/** v0.6 community-authored model definition using existing project/vanilla textures. */
public final class FlightBlockStateProvider extends BlockStateProvider {
    public FlightBlockStateProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile model = models().cubeBottomTop(
                "fuel_loader",
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_side"),
                mcLoc("block/observer_top")
        );
        simpleBlock(ModBlocks.FUEL_LOADER.get(), model);
        simpleBlockItem(ModBlocks.FUEL_LOADER.get(), model);
    }
}
