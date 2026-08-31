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

public final class RocketBlockTagsProvider extends BlockTagsProvider {
    public RocketBlockTagsProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFiles
    ) {
        super(output, lookupProvider, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        tag(ModBlockTags.ROCKET_MOVABLE).add(
                ModBlocks.MACHINE_CASING.get(),
                ModBlocks.ROCKET_MOTOR.get(),
                ModBlocks.ROCKET_FUEL_TANK.get(),
                ModBlocks.ROCKET_SEAT.get(),
                ModBlocks.GUIDANCE_COMPUTER.get(),
                Blocks.CHEST,
                Blocks.BARREL,
                Blocks.IRON_BLOCK,
                Blocks.COPPER_BLOCK,
                Blocks.GLASS
        );
        tag(ModBlockTags.ROCKET_FORBIDDEN).add(
                Blocks.COMMAND_BLOCK,
                Blocks.CHAIN_COMMAND_BLOCK,
                Blocks.REPEATING_COMMAND_BLOCK,
                Blocks.STRUCTURE_BLOCK,
                Blocks.JIGSAW,
                Blocks.SPAWNER,
                Blocks.END_PORTAL,
                Blocks.END_PORTAL_FRAME,
                Blocks.NETHER_PORTAL,
                Blocks.MOVING_PISTON,
                Blocks.PISTON_HEAD
        );
        tag(ModBlockTags.ROCKET_ENGINES).add(ModBlocks.ROCKET_MOTOR.get());
        tag(ModBlockTags.ROCKET_FUEL_TANKS).add(ModBlocks.ROCKET_FUEL_TANK.get());
        tag(ModBlockTags.ROCKET_SEATS).add(ModBlocks.ROCKET_SEAT.get());
        tag(ModBlockTags.ROCKET_GUIDANCE).add(ModBlocks.GUIDANCE_COMPUTER.get());
    }
}
