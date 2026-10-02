package io.github.sunthemoon.advancedrocketrycommunity.material.press;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.BoundedItemIngredientCodec;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A small plate press recipe (ADR-063 section 3): one block, matched through its item form, becomes one output stack.
 * Bounded like the kernel recipes: exact fields, one ingredient of at most 32 item or tag entries, an output of at
 * most 64. Tag entries are resolved only when the press acts, because recipes load before tags are bound.
 */
public final class SmallPlatePressRecipe implements Recipe<SimpleContainer> {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RECIPE_BYTES = 4_096;
    public static final int MAX_RESULT_COUNT = 64;
    public static final int MAX_RESOURCE_ID_CHARS = 256;
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("type", "schema_version", "ingredient", "result");
    private static final Set<String> RESULT_FIELDS = Set.of("item", "count");

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final ItemStack result;

    public SmallPlatePressRecipe(ResourceLocation id, Ingredient ingredient, ItemStack result) {
        this.id = Objects.requireNonNull(id, "id");
        this.ingredient = Objects.requireNonNull(ingredient, "ingredient");
        this.result = Objects.requireNonNull(result, "result").copy();
        if (this.result.isEmpty() || this.result.hasTag() || this.result.getCount() > MAX_RESULT_COUNT
                || this.result.getCount() > this.result.getMaxStackSize()) {
            throw new IllegalArgumentException("Small plate press result must be a bounded item without NBT");
        }
        BoundedItemIngredientCodec.validateOnly(ingredient.toJson());
    }

    @Override
    public boolean matches(SimpleContainer container, Level level) {
        return ingredient.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(SimpleContainer container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredient);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MaterialContent.SMALL_PLATE_PRESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return MaterialContent.SMALL_PLATE_PRESS_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    public ItemStack result() {
        return result.copy();
    }

    public static final class Serializer implements RecipeSerializer<SmallPlatePressRecipe> {
        @Override
        public SmallPlatePressRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                if (json.toString().getBytes(StandardCharsets.UTF_8).length > MAX_RECIPE_BYTES) {
                    throw new IllegalArgumentException("recipe JSON exceeds " + MAX_RECIPE_BYTES + " bytes");
                }
                if (!TOP_LEVEL_FIELDS.equals(json.keySet())) {
                    throw new IllegalArgumentException("recipe has missing or unexpected top-level fields");
                }
                if (requireInt(json, "schema_version") != SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported recipe schema");
                }
                Ingredient ingredient = BoundedItemIngredientCodec.decode(GsonHelper.getNonNull(json, "ingredient"));
                JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
                if (!RESULT_FIELDS.equals(resultJson.keySet())) {
                    throw new IllegalArgumentException("result has missing or unexpected fields");
                }
                return new SmallPlatePressRecipe(id, ingredient, new ItemStack(
                        requireItem(GsonHelper.getAsString(resultJson, "item")), requireInt(resultJson, "count")));
            } catch (RuntimeException exception) {
                throw new JsonSyntaxException("Invalid small plate press recipe " + id + ": " + exception.getMessage(),
                        exception);
            }
        }

        @Override
        public SmallPlatePressRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            try {
                if (buffer.readVarInt() != SCHEMA_VERSION) {
                    throw new IllegalArgumentException("unsupported recipe schema");
                }
                JsonElement ingredientJson = JsonParser.parseString(
                        buffer.readUtf(BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS));
                Ingredient ingredient = BoundedItemIngredientCodec.decode(ingredientJson);
                Item item = requireItem(buffer.readUtf(MAX_RESOURCE_ID_CHARS));
                return new SmallPlatePressRecipe(id, ingredient, new ItemStack(item, buffer.readVarInt()));
            } catch (RuntimeException exception) {
                throw new JsonParseException("Invalid network small plate press recipe " + id, exception);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SmallPlatePressRecipe recipe) {
            String ingredientJson = recipe.ingredient.toJson().toString();
            BoundedItemIngredientCodec.validateOnly(JsonParser.parseString(ingredientJson));
            buffer.writeVarInt(SCHEMA_VERSION);
            buffer.writeUtf(ingredientJson, BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS);
            buffer.writeUtf(Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(recipe.result.getItem())).toString(),
                    MAX_RESOURCE_ID_CHARS);
            buffer.writeVarInt(recipe.result.getCount());
        }

        private static int requireInt(JsonObject parent, String field) {
            JsonElement element = parent.get(field);
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(field + " must be an integer");
            }
            try {
                return new BigDecimal(element.getAsString()).intValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new IllegalArgumentException(field + " must be a 32-bit integer", exception);
            }
        }

        private static Item requireItem(String rawId) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (rawId.length() > MAX_RESOURCE_ID_CHARS || item == null || item.getDefaultInstance().isEmpty()) {
                throw new IllegalArgumentException("result item is unknown or empty");
            }
            return item;
        }
    }
}
