package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

public final class RocketRecipeProvider extends RecipeProvider {
    public RocketRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ROCKET_ASSEMBLER.get())
                .pattern("IPI")
                .pattern("CMC")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('P', Items.PISTON)
                .define('C', ModItems.ADVANCED_CIRCUIT.get())
                .define('M', ModBlocks.MACHINE_CASING.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_machine_casing", has(ModBlocks.MACHINE_CASING.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ROCKET_MOTOR.get())
                .pattern(" I ")
                .pattern("ICI")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', ModItems.BASIC_CIRCUIT.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_basic_circuit", has(ModItems.BASIC_CIRCUIT.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ROCKET_FUEL_TANK.get())
                .pattern("III")
                .pattern("G G")
                .pattern("III")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('G', Tags.Items.GLASS)
                .unlockedBy("has_iron_ingot", has(Tags.Items.INGOTS_IRON))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ROCKET_SEAT.get())
                .pattern("W W")
                .pattern("WWW")
                .pattern("I I")
                .define('W', ItemTags.WOOL)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_wool", has(ItemTags.WOOL))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.GUIDANCE_COMPUTER.get())
                .pattern("GCG")
                .pattern("RMR")
                .pattern("III")
                .define('G', Tags.Items.GLASS)
                .define('C', ModItems.ADVANCED_CIRCUIT.get())
                .define('R', Items.REDSTONE)
                .define('M', ModBlocks.MACHINE_CASING.get())
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_advanced_circuit", has(ModItems.ADVANCED_CIRCUIT.get()))
                .save(output);
    }
}
