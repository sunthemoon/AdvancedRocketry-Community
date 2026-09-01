package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

public final class StationRecipeProvider extends RecipeProvider {
    public StationRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.STATION_DEPLOYMENT_KIT.get())
                .pattern("EGE")
                .pattern("ACA")
                .pattern("III")
                .define('E', Items.ENDER_EYE)
                .define('G', ModItems.GUIDANCE_COMPUTER.get())
                .define('A', ModItems.ADVANCED_CIRCUIT.get())
                .define('C', ModItems.MACHINE_CASING.get())
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_guidance_computer", has(ModItems.GUIDANCE_COMPUTER.get()))
                .save(output);
    }
}

