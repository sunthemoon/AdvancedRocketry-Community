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

public final class RocketLootTableProvider {
    private RocketLootTableProvider() {
    }

    public static LootTableProvider create(PackOutput output) {
        return new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(
                        RocketBlockLoot::new,
                        LootContextParamSets.BLOCK
                ))
        );
    }

    private static final class RocketBlockLoot extends BlockLootSubProvider {
        private static final List<Block> BLOCKS = List.of(
                ModBlocks.ROCKET_ASSEMBLER.get(),
                ModBlocks.ROCKET_MOTOR.get(),
                ModBlocks.ROCKET_FUEL_TANK.get(),
                ModBlocks.ROCKET_SEAT.get(),
                ModBlocks.GUIDANCE_COMPUTER.get()
        );

        private RocketBlockLoot() {
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
