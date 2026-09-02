package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Community-authored terminal model referencing existing project and vanilla textures. */
public final class SatelliteBlockStateProvider extends BlockStateProvider {
    public SatelliteBlockStateProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile model = models().cubeBottomTop(
                "satellite_terminal",
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_side"),
                mcLoc("block/daylight_detector_top")
        );
        simpleBlock(ModBlocks.SATELLITE_TERMINAL.get(), model);
        simpleBlockItem(ModBlocks.SATELLITE_TERMINAL.get(), model);
    }
}
