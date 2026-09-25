package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public final class V120MachineLootTableProvider {
    private V120MachineLootTableProvider() {
    }

    public static LootTableProvider create(PackOutput output) {
        return new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        MachineBlockLoot::new,
                        LootContextParamSets.BLOCK
                ))
        );
    }

    private static final class MachineBlockLoot extends BlockLootSubProvider {
        private static final List<Block> BLOCKS = List.of(
                ModBlocks.ROLLING_MACHINE.get(),
                ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get(),
                ModBlocks.PRECISION_ASSEMBLER.get(),
                ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get(),
                ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get(),
                ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get()
        );

        private MachineBlockLoot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            BLOCKS.forEach(this::dropSelf);
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return BLOCKS;
        }
    }
}
