package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Decoder for the deliberately limited vanilla item/tag ingredient subset. */
final class RollingMachineIngredientCodec {
    private static final Set<String> ITEM_FIELDS = Set.of("item");
    private static final Set<String> TAG_FIELDS = Set.of("tag");

    private RollingMachineIngredientCodec() {
    }

    static Ingredient decode(JsonElement raw) {
        List<JsonObject> entries = validate(raw);
        List<Ingredient.Value> values = new ArrayList<>(entries.size());
        for (JsonObject entry : entries) {
            if (entry.has("item")) {
                values.add(new Ingredient.ItemValue(new ItemStack(
                        requireItem(GsonHelper.getAsString(entry, "item"))
                )));
            } else {
                ResourceLocation tagId = ResourceLocation.tryParse(GsonHelper.getAsString(entry, "tag"));
                if (tagId == null) {
                    throw new IllegalArgumentException("ingredient tag is invalid");
                }
                values.add(new Ingredient.TagValue(TagKey.create(Registries.ITEM, tagId)));
            }
        }
        return Ingredient.fromValues(values.stream());
    }

    static void validateOnly(JsonElement raw) {
        validate(raw);
    }

    private static List<JsonObject> validate(JsonElement raw) {
        String encoded = raw.toString();
        if (encoded.length() > RollingMachineRecipe.MAX_INGREDIENT_JSON_CHARS) {
            throw new IllegalArgumentException("ingredient JSON exceeds the character limit");
        }
        List<JsonObject> entries = new ArrayList<>();
        if (raw.isJsonArray()) {
            JsonArray array = raw.getAsJsonArray();
            if (array.size() < 1 || array.size() > ProcessInput.MAX_VARIANTS) {
                throw new IllegalArgumentException("ingredient array must contain 1..32 entries");
            }
            array.forEach(value -> entries.add(requireObject(value)));
        } else {
            entries.add(requireObject(raw));
        }
        for (JsonObject entry : entries) {
            boolean item = ITEM_FIELDS.equals(entry.keySet());
            boolean tag = TAG_FIELDS.equals(entry.keySet());
            if (!item && !tag) {
                throw new IllegalArgumentException("ingredient entries must contain exactly item or tag");
            }
            String resource = GsonHelper.getAsString(entry, item ? "item" : "tag");
            ResourceLocation parsed = ResourceLocation.tryParse(resource);
            if (parsed == null || resource.length() > RollingMachineRecipe.MAX_RESOURCE_ID_CHARS) {
                throw new IllegalArgumentException("ingredient resource id is invalid");
            }
        }
        return entries;
    }

    private static JsonObject requireObject(JsonElement value) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("ingredient entries must be objects");
        }
        return value.getAsJsonObject();
    }

    private static Item requireItem(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (rawId.length() > RollingMachineRecipe.MAX_RESOURCE_ID_CHARS
                || item == null
                || item.getDefaultInstance().isEmpty()) {
            throw new IllegalArgumentException("ingredient item is unknown or empty");
        }
        return item;
    }
}
