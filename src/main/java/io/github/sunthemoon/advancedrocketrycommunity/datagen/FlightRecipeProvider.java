package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
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

public final class FlightRecipeProvider extends RecipeProvider {
    public FlightRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.FUEL_LOADER.get())
                .pattern("IHI")
                .pattern("CMC")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('H', Items.HOPPER)
                .define('C', ModItems.BASIC_CIRCUIT.get())
                .define('M', ModBlocks.MACHINE_CASING.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_rocket_fuel_tank", has(ModBlocks.ROCKET_FUEL_TANK.get()))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.ROCKET_FUEL_CELL.get())
                .requires(ModItems.HYDROGEN_CANISTER.get())
                .requires(Items.BLAZE_POWDER)
                .requires(Items.REDSTONE)
                .unlockedBy("has_hydrogen_canister", has(ModItems.HYDROGEN_CANISTER.get()))
                .save(output);
    }
}
