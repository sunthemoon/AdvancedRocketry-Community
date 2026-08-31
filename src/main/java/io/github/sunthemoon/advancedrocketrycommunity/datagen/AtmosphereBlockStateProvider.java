package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlock;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class AtmosphereBlockStateProvider extends BlockStateProvider {
    public AtmosphereBlockStateProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile idle = models().cubeBottomTop(
                "oxygen_vent",
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_top")
        );
        ModelFile active = models().cubeBottomTop(
                "oxygen_vent_active",
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_side"),
                mcLoc("block/sea_lantern")
        );
        getVariantBuilder(ModBlocks.OXYGEN_VENT.get())
                .partialState().with(OxygenVentBlock.LIT, false)
                .modelForState().modelFile(idle).addModel()
                .partialState().with(OxygenVentBlock.LIT, true)
                .modelForState().modelFile(active).addModel();
        simpleBlockItem(ModBlocks.OXYGEN_VENT.get(), idle);
    }
}
