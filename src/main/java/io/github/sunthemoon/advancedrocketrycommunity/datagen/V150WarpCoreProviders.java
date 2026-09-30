package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * ADR-044 §7 warp core content (v1.5 DataGen): an original model composed from the existing
 * machine-casing textures and a referenced vanilla texture, a self-drop, and a recipe that follows
 * the machine progression (four casings, four advanced circuits, one data storage unit).
 */
public final class V150WarpCoreProviders {
    private V150WarpCoreProviders() {
    }

    public static final class Models extends BlockStateProvider {
        public Models(PackOutput output, ExistingFileHelper existingFiles) {
            super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        }

        @Override
        protected void registerStatesAndModels() {
            ModelFile model = models().cubeBottomTop(
                    "warp_core",
                    modLoc("block/machine_casing_front"),
                    modLoc("block/machine_casing_top"),
                    mcLoc("block/crying_obsidian")
            );
            simpleBlock(ModBlocks.WARP_CORE.get(), model);
            simpleBlockItem(ModBlocks.WARP_CORE.get(), model);
        }
    }

    public static LootTableProvider loot(PackOutput output) {
        return new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(WarpCoreLoot::new, LootContextParamSets.BLOCK)));
    }

    /** The plain block with no block-entity data: a carried core carries nothing. */
    private static final class WarpCoreLoot extends BlockLootSubProvider {
        private WarpCoreLoot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.WARP_CORE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(ModBlocks.WARP_CORE.get());
        }
    }

    public static final class Recipes extends RecipeProvider {
        public Recipes(PackOutput output) {
            super(output);
        }

        @Override
        protected void buildRecipes(Consumer<FinishedRecipe> output) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.WARP_CORE.get())
                    .pattern("MAM")
                    .pattern("ADA")
                    .pattern("MAM")
                    .define('M', ModItems.MACHINE_CASING.get())
                    .define('A', ModItems.ADVANCED_CIRCUIT.get())
                    .define('D', ModItems.DATA_STORAGE_UNIT.get())
                    .unlockedBy("has_data_storage_unit", has(ModItems.DATA_STORAGE_UNIT.get()))
                    .save(output);
        }
    }

    /**
     * Complete v1.5 copies of the two vanilla tool tags; they supersede the v1.2 copies (build.gradle
     * excludes those), so every earlier machine block keeps its entry.
     */
    public static final class ToolTags extends BlockTagsProvider {
        public ToolTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                         ExistingFileHelper existingFiles) {
            super(output, lookupProvider, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            Block[] blocks = {
                    ModBlocks.MACHINE_CASING.get(),
                    ModBlocks.SATELLITE_TERMINAL.get(),
                    ModBlocks.ROLLING_MACHINE.get(),
                    ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get(),
                    ModBlocks.WARP_CORE.get()
            };
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(blocks);
            tag(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL).add(blocks);
        }
    }
}
