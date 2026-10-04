package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.common.Tags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** First community-authored two-output process recipe for the registered machine. */
public final class PrecisionAssemblerRecipeProvider {
    private PrecisionAssemblerRecipeProvider() {
    }

    static void addRecipes(Consumer<FinishedRecipe> output) {
        output.accept(new CircuitAssembly());
        output.accept(new GuidanceAssembly());
    }

    private static final class CircuitAssembly implements FinishedRecipe {
        private static final ResourceLocation ID = ModIdentity.id("precision_control_circuit");

        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("schema_version", ProcessDefinition.SCHEMA_VERSION);
            JsonArray inputs = new JsonArray();
            inputs.add(input(Tags.Items.INGOTS_IRON, 2));
            inputs.add(input(Tags.Items.DUSTS_REDSTONE, 2));
            json.add("inputs", inputs);
            JsonArray outputs = new JsonArray();
            outputs.add(result(ModItems.ADVANCED_CIRCUIT.get(), 1));
            outputs.add(result(Items.REDSTONE_TORCH, 2));
            json.add("outputs", outputs);
            json.addProperty("processing_time", 20);
            json.addProperty("energy_per_tick", 40);
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return ModRecipes.PRECISION_ASSEMBLING_SERIALIZER.get();
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

    private static final class GuidanceAssembly implements FinishedRecipe {
        private static final ResourceLocation ID = ModIdentity.id("precision_guidance_module");

        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("schema_version", ProcessDefinition.SCHEMA_VERSION);
            JsonArray inputs = new JsonArray();
            inputs.add(input(Tags.Items.INGOTS_IRON, 2));
            inputs.add(input(Tags.Items.DUSTS_REDSTONE, 2));
            inputs.add(input(Tags.Items.INGOTS_GOLD, 1));
            inputs.add(input(Tags.Items.GEMS_QUARTZ, 1));
            inputs.add(input(Tags.Items.INGOTS_COPPER, 1));
            json.add("inputs", inputs);
            JsonArray outputs = new JsonArray();
            outputs.add(result(Items.COMPARATOR, 1));
            json.add("outputs", outputs);
            json.addProperty("processing_time", 30);
            json.addProperty("energy_per_tick", 50);
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return ModRecipes.PRECISION_ASSEMBLING_SERIALIZER.get();
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

    private static JsonObject input(TagKey<Item> item, int count) {
        JsonObject input = new JsonObject();
        input.add("ingredient", Ingredient.of(item).toJson());
        input.addProperty("count", count);
        return input;
    }

    private static JsonObject result(Item item, int count) {
        JsonObject result = new JsonObject();
        result.addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString());
        result.addProperty("count", count);
        return result;
    }
}
