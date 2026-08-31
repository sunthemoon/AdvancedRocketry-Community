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

public final class ModLootTableProvider {
    private ModLootTableProvider() {
    }

    public static LootTableProvider create(PackOutput output) {
        return new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        ModBlockLoot::new,
                        LootContextParamSets.BLOCK
                ))
        );
    }

    private static final class ModBlockLoot extends BlockLootSubProvider {
        private ModBlockLoot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.ELECTROLYZER.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(ModBlocks.ELECTROLYZER.get());
        }
    }
}
