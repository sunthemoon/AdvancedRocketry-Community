package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItemTags;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

public final class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.SILICON_WAFER.get(), 2)
                .requires(Tags.Items.GEMS_QUARTZ)
                .requires(Items.REDSTONE)
                .unlockedBy("has_quartz", has(Tags.Items.GEMS_QUARTZ))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.BASIC_CIRCUIT.get(), 2)
                .requires(ModItemTags.SILICON_WAFERS)
                .requires(Tags.Items.INGOTS_COPPER)
                .requires(Items.REDSTONE)
                .unlockedBy("has_silicon_wafer", has(ModItemTags.SILICON_WAFERS))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ADVANCED_CIRCUIT.get())
                .requires(ModItemTags.BASIC_CIRCUITS)
                .requires(Tags.Items.INGOTS_GOLD)
                .requires(Tags.Items.GEMS_DIAMOND)
                .unlockedBy("has_basic_circuit", has(ModItemTags.BASIC_CIRCUITS))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DATA_STORAGE_UNIT.get())
                .requires(ModItemTags.ADVANCED_CIRCUITS)
                .requires(Items.COMPARATOR)
                .requires(Items.REDSTONE)
                .unlockedBy("has_advanced_circuit", has(ModItemTags.ADVANCED_CIRCUITS))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.MACHINE_CASING.get(), 2)
                .pattern("III")
                .pattern("ICI")
                .pattern("III")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', ModItemTags.BASIC_CIRCUITS)
                .unlockedBy("has_basic_circuit", has(ModItemTags.BASIC_CIRCUITS))
                .save(output);
    }
}
