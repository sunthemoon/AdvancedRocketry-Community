package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * ADR-054 section 16 v1.7 DataGen for the shared endgame content. Models reference existing project textures and
 * vanilla models (no texture bytes are copied); the recipes follow the contract's ingredient lists.
 */
public final class V170EndgameProviders {
    private V170EndgameProviders() {
    }

    public static final class Models extends BlockStateProvider {
        public Models(PackOutput output, ExistingFileHelper existingFiles) {
            super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        }

        @Override
        protected void registerStatesAndModels() {
            ModelFile casing = models().cubeColumn("endgame_casing", modLoc("block/machine_casing_side"),
                    mcLoc("block/obsidian"));
            simpleBlock(ModBlocks.ENDGAME_CASING.get(), casing);
            simpleBlockItem(ModBlocks.ENDGAME_CASING.get(), casing);
            ModelFile drill = models().orientable("orbital_laser_drill", modLoc("block/machine_casing_side"),
                    modLoc("block/machine_casing_front"), mcLoc("block/obsidian"));
            horizontalBlock(ModBlocks.ORBITAL_LASER_DRILL.get(), drill);
            simpleBlockItem(ModBlocks.ORBITAL_LASER_DRILL.get(), drill);
            ModelFile target = models().cubeBottomTop("laser_target", modLoc("block/machine_casing_side"),
                    mcLoc("block/obsidian"), modLoc("block/machine_casing_top"));
            simpleBlock(ModBlocks.LASER_TARGET.get(), target);
            simpleBlockItem(ModBlocks.LASER_TARGET.get(), target);
            itemModels().withExistingParent("laser_lens", mcLoc("item/amethyst_shard"));
        }
    }

    public static LootTableProvider loot(PackOutput output) {
        return new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(EndgameBlockLoot::new, LootContextParamSets.BLOCK)));
    }

    private static final class EndgameBlockLoot extends BlockLootSubProvider {
        private EndgameBlockLoot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.ENDGAME_CASING.get());
            dropSelf(ModBlocks.ORBITAL_LASER_DRILL.get());
            dropSelf(ModBlocks.LASER_TARGET.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(ModBlocks.ENDGAME_CASING.get(), ModBlocks.ORBITAL_LASER_DRILL.get(),
                    ModBlocks.LASER_TARGET.get());
        }
    }

    /** Recipe IDs equal the output IDs (section 16). */
    public static final class Recipes extends RecipeProvider {
        public Recipes(PackOutput output) {
            super(output);
        }

        @Override
        protected void buildRecipes(Consumer<FinishedRecipe> output) {
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ENDGAME_CASING.get())
                    .pattern("IOI").pattern("OMO").pattern("IOI")
                    .define('I', Tags.Items.INGOTS_IRON)
                    .define('O', Items.OBSIDIAN)
                    .define('M', ModItems.MACHINE_CASING.get())
                    .unlockedBy("has_machine_casing", has(ModItems.MACHINE_CASING.get()))
                    .save(output);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LASER_LENS.get())
                    .pattern(" G ").pattern("GDG").pattern(" S ")
                    .define('G', Tags.Items.GLASS)
                    .define('D', Tags.Items.GEMS_DIAMOND)
                    .define('S', ModItems.SILICON_WAFER.get())
                    .unlockedBy("has_silicon_wafer", has(ModItems.SILICON_WAFER.get()))
                    .save(output);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ORBITAL_LASER_DRILL.get())
                    .pattern("CLC").pattern("ERE").pattern("CEC")
                    .define('C', ModItems.ADVANCED_CIRCUIT.get())
                    .define('L', ModItems.LASER_LENS.get())
                    .define('E', ModItems.ENDGAME_CASING.get())
                    .define('R', Tags.Items.STORAGE_BLOCKS_REDSTONE)
                    .unlockedBy("has_laser_lens", has(ModItems.LASER_LENS.get()))
                    .save(output);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.LASER_TARGET.get())
                    .pattern("IGI").pattern("RBR").pattern("III")
                    .define('I', Tags.Items.INGOTS_IRON)
                    .define('G', Tags.Items.GLASS)
                    .define('R', Tags.Items.DUSTS_REDSTONE)
                    .define('B', ModItems.BASIC_CIRCUIT.get())
                    .unlockedBy("has_basic_circuit", has(ModItems.BASIC_CIRCUIT.get()))
                    .save(output);
        }
    }

    /** The complete v1.7 copies of the two vanilla tool tags; they supersede the v1.6 copies. */
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
                    ModBlocks.WARP_CORE.get(),
                    ModBlocks.SATELLITE_BUILDER.get(),
                    ModBlocks.MICROWAVE_RECEIVER.get(),
                    ModBlocks.ENDGAME_CASING.get(),
                    ModBlocks.ORBITAL_LASER_DRILL.get(),
                    ModBlocks.LASER_TARGET.get()
            };
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(blocks);
            tag(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL).add(blocks);
            // ADR-054 section 9.1: endpoints resist withers and the dragon and opt out of common block movers
            // (shipped with replace false, harmless without those mods).
            Block[] endpoints = {ModBlocks.LASER_TARGET.get()};
            tag(net.minecraft.tags.BlockTags.WITHER_IMMUNE).add(endpoints);
            tag(net.minecraft.tags.BlockTags.DRAGON_IMMUNE).add(endpoints);
            tag(foreign("create", "non_movable")).add(endpoints);
            tag(foreign("carryon", "block_blacklist")).add(endpoints);
            tag(ModBlockTags.LASER_DRILL_IMMUNE).addTag(net.minecraft.tags.BlockTags.WITHER_IMMUNE);
        }

        private static net.minecraft.tags.TagKey<Block> foreign(String namespace, String path) {
            return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    java.util.Objects.requireNonNull(net.minecraft.resources.ResourceLocation.tryBuild(namespace, path)));
        }
    }
}
