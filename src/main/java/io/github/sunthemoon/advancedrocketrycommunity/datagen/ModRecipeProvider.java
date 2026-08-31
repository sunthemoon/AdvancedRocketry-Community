package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipeSpec;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import javax.annotation.Nullable;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.Tags;

public final class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ELECTROLYZER.get())
                .pattern("IGI")
                .pattern("CMC")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('G', Tags.Items.GLASS)
                .define('C', ModItems.BASIC_CIRCUIT.get())
                .define('M', ModBlocks.MACHINE_CASING.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_machine_casing", has(ModBlocks.MACHINE_CASING.get()))
                .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.EMPTY_CANISTER.get(), 4)
                .pattern(" G ")
                .pattern("G G")
                .pattern(" G ")
                .define('G', Tags.Items.GLASS)
                .unlockedBy("has_glass", has(Tags.Items.GLASS))
                .save(output);

        output.accept(new ElectrolyzingFinishedRecipe());
    }

    private static final class ElectrolyzingFinishedRecipe implements FinishedRecipe {
        private static final ElectrolyzerRecipeSpec SPEC = ElectrolyzerRecipeSpec.fixedRecipe();
        private static final ResourceLocation ID = ModIdentity.id("electrolyzer_water");

        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("schema_version", SPEC.schemaVersion());
            json.add("ingredient", Ingredient.of(ModItems.EMPTY_CANISTER.get()).toJson());
            json.addProperty("input_count", SPEC.inputCount());

            JsonObject fluid = new JsonObject();
            fluid.addProperty("fluid", BuiltInRegistries.FLUID.getKey(Fluids.WATER).toString());
            fluid.addProperty("amount", SPEC.waterAmount());
            json.add("fluid", fluid);

            json.addProperty("processing_time", SPEC.processingTicks());
            json.addProperty("energy_per_tick", SPEC.energyPerTick());
            json.add("hydrogen_result", result(ModItems.HYDROGEN_CANISTER.get().getDefaultInstance()));
            json.add("oxygen_result", result(ModItems.OXYGEN_CANISTER.get().getDefaultInstance()));
        }

        private static JsonObject result(ItemStack stack) {
            JsonObject result = new JsonObject();
            result.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (stack.getCount() != 1) {
                result.addProperty("count", stack.getCount());
            }
            return result;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return ModRecipes.ELECTROLYZING_SERIALIZER.get();
        }

        @Nullable
        @Override
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Nullable
        @Override
        public ResourceLocation getAdvancementId() {
            return null;
        }
    }
}
