package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;

public final class SatelliteRecipeProvider extends RecipeProvider {
    public SatelliteRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SATELLITE_TERMINAL.get())
                .pattern("DAD")
                .pattern("CMC")
                .pattern("IRI")
                .define('D', ModItems.DATA_STORAGE_UNIT.get())
                .define('A', ModItems.ADVANCED_CIRCUIT.get())
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('M', ModItems.MACHINE_CASING.get())
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Items.REDSTONE_BLOCK)
                .unlockedBy("has_advanced_circuit", has(ModItems.ADVANCED_CIRCUIT.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SATELLITE_CHASSIS.get())
                .pattern("I I")
                .pattern("CMC")
                .pattern("III")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('M', ModItems.MACHINE_CASING.get())
                .unlockedBy("has_machine_casing", has(ModItems.MACHINE_CASING.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SATELLITE_SOLAR_MODULE.get())
                .pattern("GGG")
                .pattern("CDC")
                .pattern("CRC")
                .define('G', Tags.Items.GLASS_PANES)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('D', Items.DAYLIGHT_DETECTOR)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_daylight_detector", has(Items.DAYLIGHT_DETECTOR))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SATELLITE_CONTROL_CHIP.get())
                .pattern("GRG")
                .pattern("RAR")
                .pattern("GRG")
                .define('G', Tags.Items.INGOTS_GOLD)
                .define('R', Items.REDSTONE)
                .define('A', ModItems.ADVANCED_CIRCUIT.get())
                .unlockedBy("has_advanced_circuit", has(ModItems.ADVANCED_CIRCUIT.get()))
                .save(output);
    }
}
