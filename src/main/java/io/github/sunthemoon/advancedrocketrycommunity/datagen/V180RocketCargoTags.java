package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Complete current copy: sealed v0.5 values in order, then the C-P cargo host. */
public final class V180RocketCargoTags extends BlockTagsProvider {
    public V180RocketCargoTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                              ExistingFileHelper existingFiles) {
        super(output, lookup, ModIdentity.MOD_ID, existingFiles);
    }
    @Override protected void addTags(HolderLookup.Provider provider) {
        tag(ModBlockTags.ROCKET_MOVABLE).replace(false).add(
                ModBlocks.MACHINE_CASING.get(), ModBlocks.ROCKET_MOTOR.get(), ModBlocks.ROCKET_FUEL_TANK.get(),
                ModBlocks.ROCKET_SEAT.get(), ModBlocks.GUIDANCE_COMPUTER.get(), Blocks.CHEST, Blocks.BARREL,
                Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK, Blocks.GLASS, ModBlocks.PRESSURIZED_TANK.get());
    }

    @Override public String getName() { return "v1.8 rocket cargo block tags"; }
}
