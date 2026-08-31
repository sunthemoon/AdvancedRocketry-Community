package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
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
        ModelFile electrolyzerIdle = models().orientableWithBottom(
                "electrolyzer",
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_front"),
                modLoc("block/machine_casing_top"),
                modLoc("block/machine_casing_top")
        );
        ModelFile electrolyzerActive = models().orientableWithBottom(
                "electrolyzer_active",
                modLoc("block/machine_casing_side"),
                mcLoc("block/sea_lantern"),
                modLoc("block/machine_casing_top"),
                modLoc("block/machine_casing_top")
        );
        horizontalBlock(
                ModBlocks.ELECTROLYZER.get(),
                state -> state.getValue(ElectrolyzerBlock.LIT) ? electrolyzerActive : electrolyzerIdle
        );
        simpleBlockItem(ModBlocks.ELECTROLYZER.get(), electrolyzerIdle);
    }
}
