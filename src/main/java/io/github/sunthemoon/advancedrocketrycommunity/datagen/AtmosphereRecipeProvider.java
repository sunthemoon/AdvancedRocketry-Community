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

public final class AtmosphereRecipeProvider extends RecipeProvider {
    public AtmosphereRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.OXYGEN_VENT.get())
                .pattern("IRI")
                .pattern("CMC")
                .pattern("IGI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Items.REDSTONE)
                .define('C', ModItems.BASIC_CIRCUIT.get())
                .define('M', ModBlocks.MACHINE_CASING.get())
                .define('G', Tags.Items.GLASS)
                .unlockedBy("has_oxygen_canister", has(ModItems.OXYGEN_CANISTER.get()))
                .save(output);

        suit(output, ModItems.SPACE_SUIT_HELMET.get(), "IGI", "I I", " C ");
        suit(output, ModItems.SPACE_SUIT_CHESTPLATE.get(), "I I", "ICI", "III");
        suit(output, ModItems.SPACE_SUIT_LEGGINGS.get(), "III", "ICI", "I I");
        suit(output, ModItems.SPACE_SUIT_BOOTS.get(), "I I", "ICI");
    }

    private static void suit(
            Consumer<FinishedRecipe> output,
            net.minecraft.world.level.ItemLike result,
            String... rows
    ) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, result);
        for (String row : rows) {
            builder.pattern(row);
        }
        builder.define('I', Tags.Items.INGOTS_IRON)
                .define('C', ModItems.BASIC_CIRCUIT.get());
        if (java.util.Arrays.stream(rows).anyMatch(row -> row.indexOf('G') >= 0)) {
            builder.define('G', Tags.Items.GLASS);
        }
        builder.unlockedBy("has_basic_circuit", has(ModItems.BASIC_CIRCUIT.get()))
                .save(output);
    }
}
