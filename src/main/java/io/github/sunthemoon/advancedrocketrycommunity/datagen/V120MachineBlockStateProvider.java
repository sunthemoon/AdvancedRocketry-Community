package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Community-authored placeholder models composed from project and vanilla textures. */
public final class V120MachineBlockStateProvider extends BlockStateProvider {
    public V120MachineBlockStateProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile controller = models().orientableWithBottom(
                "rolling_machine",
                modLoc("block/machine_casing_side"),
                mcLoc("block/piston_top"),
                modLoc("block/machine_casing_top"),
                modLoc("block/machine_casing_top")
        );
        horizontalBlock(ModBlocks.ROLLING_MACHINE.get(), controller);
        simpleBlockItem(ModBlocks.ROLLING_MACHINE.get(), controller);

        port(
                "rolling_machine_item_input_port",
                ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                mcLoc("block/lime_concrete")
        );
        port(
                "rolling_machine_fluid_input_port",
                ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                mcLoc("block/light_blue_concrete")
        );
        port(
                "rolling_machine_energy_input_port",
                ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                mcLoc("block/redstone_block")
        );
        port(
                "rolling_machine_item_output_port",
                ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get(),
                mcLoc("block/orange_concrete")
        );

        ModelFile precisionController = models().orientableWithBottom(
                "precision_assembler",
                modLoc("block/machine_casing_side"),
                mcLoc("block/crafting_table_front"),
                modLoc("block/machine_casing_top"),
                modLoc("block/machine_casing_top")
        );
        horizontalBlock(ModBlocks.PRECISION_ASSEMBLER.get(), precisionController);
        simpleBlockItem(ModBlocks.PRECISION_ASSEMBLER.get(), precisionController);
        port("precision_assembler_item_input_port",
                ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get(), mcLoc("block/lime_concrete"));
        port("precision_assembler_item_output_port",
                ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get(), mcLoc("block/orange_concrete"));
        port("precision_assembler_energy_input_port",
                ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get(), mcLoc("block/redstone_block"));
    }

    private void port(String name, net.minecraft.world.level.block.Block block, ResourceLocation top) {
        ModelFile model = models().cubeBottomTop(
                name,
                modLoc("block/machine_casing_side"),
                modLoc("block/machine_casing_top"),
                top
        );
        simpleBlock(block, model);
        simpleBlockItem(block, model);
    }
}
