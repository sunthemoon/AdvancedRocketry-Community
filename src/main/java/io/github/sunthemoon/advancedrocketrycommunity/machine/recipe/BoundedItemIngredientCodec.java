package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/** Shared decoder for the bounded vanilla item/tag ingredient subset. */
public final class BoundedItemIngredientCodec {
    public static final int MAX_INGREDIENT_JSON_CHARS = 4_096;
    private static final Set<String> ITEM_FIELDS = Set.of("item");
    private static final Set<String> TAG_FIELDS = Set.of("tag");

    private BoundedItemIngredientCodec() {
    }

    public static Ingredient decode(JsonElement raw) {
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

    public static void validateOnly(JsonElement raw) {
        validate(raw);
    }

    public static boolean hasTags(JsonElement raw) {
        return validate(raw).stream().anyMatch(entry -> entry.has("tag"));
    }

    /** Enumerates at most 33 distinct entries; does not allocate an unbounded vanilla tag expansion. */
    public static List<String> resolveAlternatives(JsonElement raw) {
        TreeSet<String> alternatives = new TreeSet<>();
        for (JsonObject entry : validate(raw)) {
            if (entry.has("item")) {
                Item item = requireItem(entry.get("item").getAsString());
                alternatives.add(BuiltInRegistries.ITEM.getKey(item).toString());
            } else {
                ResourceLocation id = ResourceLocation.tryParse(entry.get("tag").getAsString());
                var holders = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id));
                if (holders.isPresent()) {
                    for (Holder<Item> holder : holders.orElseThrow()) {
                        Item item = holder.value();
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                        if (item.getDefaultInstance().isEmpty() || itemId == null
                                || itemId.toString().length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS) {
                            throw new IllegalArgumentException("ingredient tag contains an invalid item");
                        }
                        alternatives.add(itemId.toString());
                        requireVariantBound(alternatives.size());
                    }
                }
            }
            requireVariantBound(alternatives.size());
        }
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("machine ingredient has no bound alternatives");
        }
        return List.copyOf(alternatives);
    }

    private static void requireVariantBound(int count) {
        if (count > ProcessInput.MAX_VARIANTS) {
            throw new IllegalArgumentException("machine ingredient must resolve to 1..32 variants");
        }
    }

    /** Explicit item-only subset for consumers that must not depend on tag binding. */
    public static Ingredient decodeItemsOnly(JsonElement raw) {
        requireItemsOnly(raw);
        return decode(raw);
    }

    /** Validates the bounded subset and refuses any tag entry (see {@link #decodeItemsOnly}). */
    public static void requireItemsOnly(JsonElement raw) {
        for (JsonObject entry : validate(raw)) {
            if (!entry.has("item")) {
                throw new IllegalArgumentException("machine recipes name items; tag ingredients are not supported yet");
            }
        }
    }

    public static List<String> resolveAlternatives(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            throw new IllegalArgumentException("machine ingredient cannot be empty");
        }
        ItemStack[] variants = ingredient.getItems();
        if (variants.length < 1 || variants.length > ProcessInput.MAX_VARIANTS) {
            throw new IllegalArgumentException("machine ingredient must resolve to 1..32 variants");
        }
        TreeSet<String> alternatives = new TreeSet<>();
        for (ItemStack variant : variants) {
            if (variant.isEmpty() || variant.hasTag()) {
                throw new IllegalArgumentException("machine ingredients cannot be empty or carry NBT");
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(variant.getItem());
            if (id == null || id.toString().length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS) {
                throw new IllegalArgumentException("machine ingredient has an invalid resource id");
            }
            alternatives.add(id.toString());
        }
        if (alternatives.size() != variants.length) {
            throw new IllegalArgumentException("machine ingredient variants cannot overlap");
        }
        return List.copyOf(alternatives);
    }

    private static List<JsonObject> validate(JsonElement raw) {
        String encoded = RecipeJsonSignature.canonical(raw);
        if (encoded.length() > MAX_INGREDIENT_JSON_CHARS) {
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
            JsonElement value = entry.get(item ? "item" : "tag");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("ingredient resource must be a string");
            }
            String resource = value.getAsString();
            ResourceLocation parsed = ResourceLocation.tryParse(resource);
            if (parsed == null || resource.length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS) {
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
        if (rawId.length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                || item == null
                || item.getDefaultInstance().isEmpty()) {
            throw new IllegalArgumentException("ingredient item is unknown or empty");
        }
        return item;
    }
}
