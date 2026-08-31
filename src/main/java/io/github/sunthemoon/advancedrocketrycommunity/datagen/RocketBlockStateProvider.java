package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

/** Community-authored model definitions that reference existing project/vanilla textures. */
public final class RocketBlockStateProvider extends BlockStateProvider {
    public RocketBlockStateProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerStatesAndModels() {
        register(
                ModBlocks.ROCKET_ASSEMBLER,
                models().cubeBottomTop(
                        "rocket_assembler",
                        modLoc("block/machine_casing_side"),
                        modLoc("block/machine_casing_side"),
                        mcLoc("block/observer_top")
                )
        );
        register(
                ModBlocks.ROCKET_MOTOR,
                models().cubeColumn(
                        "rocket_motor",
                        mcLoc("block/blast_furnace_side"),
                        mcLoc("block/furnace_top")
                )
        );
        register(
                ModBlocks.ROCKET_FUEL_TANK,
                models().cubeColumn(
                        "rocket_fuel_tank",
                        mcLoc("block/copper_block"),
                        mcLoc("block/iron_block")
                )
        );
        register(
                ModBlocks.ROCKET_SEAT,
                models().cubeBottomTop(
                        "rocket_seat",
                        mcLoc("block/dark_oak_planks"),
                        mcLoc("block/iron_block"),
                        mcLoc("block/dark_oak_planks")
                )
        );
        register(
                ModBlocks.GUIDANCE_COMPUTER,
                models().cubeBottomTop(
                        "guidance_computer",
                        modLoc("block/machine_casing_side"),
                        modLoc("block/machine_casing_side"),
                        mcLoc("block/redstone_lamp")
                )
        );
    }

    private void register(RegistryObject<Block> block, ModelFile model) {
        simpleBlock(block.get(), model);
        simpleBlockItem(block.get(), model);
    }
}
