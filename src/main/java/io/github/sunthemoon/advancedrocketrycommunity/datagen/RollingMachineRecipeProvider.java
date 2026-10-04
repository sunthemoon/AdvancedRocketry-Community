package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.Tags;

public final class RollingMachineRecipeProvider extends RecipeProvider {
    public RollingMachineRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ROLLING_MACHINE.get())
                .pattern("IPI")
                .pattern("CMC")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('P', Items.PISTON)
                .define('C', ModItems.ADVANCED_CIRCUIT.get())
                .define('M', ModBlocks.MACHINE_CASING.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_advanced_circuit", has(ModItems.ADVANCED_CIRCUIT.get()))
                .save(output);

        portRecipe(
                output,
                ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                Items.HOPPER,
                "has_hopper"
        );
        portRecipe(
                output,
                ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                Items.BUCKET,
                "has_bucket"
        );
        portRecipe(
                output,
                ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                Items.REDSTONE_BLOCK,
                "has_redstone_block"
        );
        portRecipe(
                output,
                ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get(),
                Items.DROPPER,
                "has_dropper"
        );
        precisionAssemblerCraftingRecipes(output);
        addProcessRecipes(output);
    }

    /** Current versions may supersede process payloads without regenerating historical crafting recipes. */
    static void addProcessRecipes(Consumer<FinishedRecipe> output) {
        output.accept(new RollingFinishedRecipe());
        PrecisionAssemblerRecipeProvider.addRecipes(output);
    }

    private static void precisionAssemblerCraftingRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.PRECISION_ASSEMBLER.get())
                .pattern("ICI")
                .pattern("PMP")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', ModItems.ADVANCED_CIRCUIT.get())
                .define('P', Items.PISTON)
                .define('M', ModBlocks.MACHINE_CASING.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_advanced_circuit", has(ModItems.ADVANCED_CIRCUIT.get()))
                .save(output);
        precisionPortRecipe(output, ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get(),
                Items.HOPPER, "has_hopper");
        precisionPortRecipe(output, ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get(),
                Items.DROPPER, "has_dropper");
        precisionPortRecipe(output, ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get(),
                Items.REDSTONE_BLOCK, "has_redstone_block");
    }

    private static void precisionPortRecipe(
            Consumer<FinishedRecipe> output,
            net.minecraft.world.level.block.Block result,
            net.minecraft.world.level.ItemLike component,
            String criterion
    ) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("I")
                .pattern("C")
                .pattern("M")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', component)
                .define('M', ModBlocks.MACHINE_CASING.get())
                .unlockedBy(criterion, has(component))
                .save(output);
    }

    private static void portRecipe(
            Consumer<FinishedRecipe> output,
            net.minecraft.world.level.block.Block result,
            net.minecraft.world.level.ItemLike component,
            String criterion
    ) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("C")
                .pattern("M")
                .define('C', component)
                .define('M', ModBlocks.MACHINE_CASING.get())
                .unlockedBy(criterion, has(component))
                .save(output);
    }

    private static final class RollingFinishedRecipe implements FinishedRecipe {
        private static final ResourceLocation ID = ModIdentity.id("rolling_iron_bars");

        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("schema_version", ProcessDefinition.SCHEMA_VERSION);
            json.add("ingredient", Ingredient.of(Tags.Items.INGOTS_IRON).toJson());
            json.addProperty("input_count", 2);

            JsonObject fluid = new JsonObject();
            fluid.addProperty("fluid", BuiltInRegistries.FLUID.getKey(Fluids.WATER).toString());
            fluid.addProperty("amount", 100);
            json.add("fluid", fluid);

            json.add("result", result(new ItemStack(Items.IRON_BARS, 8)));
            json.addProperty("processing_time", 100);
            json.addProperty("energy_per_tick", 20);
        }

        private static JsonObject result(ItemStack stack) {
            JsonObject result = new JsonObject();
            result.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            result.addProperty("count", stack.getCount());
            return result;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return ModRecipes.ROLLING_SERIALIZER.get();
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
