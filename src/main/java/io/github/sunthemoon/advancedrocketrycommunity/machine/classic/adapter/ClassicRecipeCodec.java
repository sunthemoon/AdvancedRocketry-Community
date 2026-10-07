package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeJsonSignature;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Private strict recipe reader, with no serializer, registry or catalog publication. */
final class ClassicRecipeCodec {
    private static final Set<String> FIELDS = Set.of("type", "schema_version", "item_inputs", "item_outputs",
            "fluid_inputs", "fluid_outputs", "processing_time", "energy_per_tick");

    static ClassicValidatedRecipe decode(ResourceLocation id, String text) {
        ClassicValueChecks.id(id);
        JsonObject root = object(parse(text));
        fields(root, FIELDS);
        ClassicValueChecks.require(string(root, "type").equals("advancedrocketrycommunity:lathe")
                && integer(root, "schema_version", 1, 1) == 1, "Recipe type/schema");
        var inputs = new ArrayList<ClassicItemInput>();
        for (JsonElement value : array(root, "item_inputs", 4)) {
            JsonObject row = object(value); fields(row, Set.of("ingredient", "count"));
            inputs.add(new ClassicItemInput(RecipeJsonSignature.canonical(row.get("ingredient")),
                    integer(row, "count", 1, 64)));
        }
        var outputs = new ArrayList<ClassicItemOutput>();
        for (JsonElement value : array(root, "item_outputs", 4)) {
            JsonObject row = object(value); fields(row, Set.of("item", "count"));
            outputs.add(new ClassicItemOutput(ClassicValueChecks.id(string(row, "item")), integer(row, "count", 1, 64)));
        }
        String canonical = RecipeJsonSignature.canonical(root);
        return new ClassicValidatedRecipe(id, canonical, List.copyOf(inputs), List.copyOf(outputs),
                fluids(root, "fluid_inputs"), fluids(root, "fluid_outputs"),
                integer(root, "processing_time", 1, 72_000), integer(root, "energy_per_tick", 1, 10_000));
    }

    private static List<ClassicFluidRow> fluids(JsonObject root, String key) {
        var result = new ArrayList<ClassicFluidRow>();
        for (JsonElement value : array(root, key, 2)) {
            JsonObject row = object(value); fields(row, Set.of("fluid", "amount"));
            result.add(new ClassicFluidRow(ClassicValueChecks.id(string(row, "fluid")), integer(row, "amount", 1, 16_000)));
        }
        return List.copyOf(result);
    }

    static JsonElement parse(String text) {
        ClassicValueChecks.require(text != null && text.length() <= RecipeJsonSignature.MAX_BYTES
                && text.getBytes(StandardCharsets.UTF_8).length <= RecipeJsonSignature.MAX_BYTES, "Recipe text bound");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement result = read(reader, 0, new int[1]);
            ClassicValueChecks.require(reader.peek() == JsonToken.END_DOCUMENT, "Trailing recipe data");
            // Shared canonicalization owns the established integer and text budgets.
            RecipeJsonSignature.canonical(result);
            return result;
        } catch (IOException | IllegalStateException failure) {
            throw new IllegalArgumentException("Invalid bounded recipe JSON", failure);
        }
    }

    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException {
        ClassicValueChecks.require(depth <= RecipeJsonSignature.MAX_DEPTH
                && ++nodes[0] <= RecipeJsonSignature.MAX_NODES, "Recipe structural bound");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject result = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    ClassicValueChecks.require(!result.has(key), "Duplicate recipe field");
                    result.add(key, read(reader, depth + 1, nodes));
                }
                reader.endObject(); yield result;
            }
            case BEGIN_ARRAY -> {
                JsonArray result = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) { result.add(read(reader, depth + 1, nodes)); }
                reader.endArray(); yield result;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> {
                String raw = reader.nextString(); ClassicValueChecks.require(raw.length() <= 64, "Recipe number bound");
                try { yield new JsonPrimitive(new BigDecimal(raw).intValueExact()); }
                catch (ArithmeticException | NumberFormatException invalid) {
                    throw new IllegalArgumentException("Recipe number is not an exact int", invalid);
                }
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            default -> throw new IllegalArgumentException("Null or unexpected recipe token");
        };
    }

    private static JsonObject object(JsonElement value) {
        ClassicValueChecks.require(value != null && value.isJsonObject(), "Recipe object required");
        return value.getAsJsonObject();
    }

    private static void fields(JsonObject root, Set<String> fields) {
        ClassicValueChecks.require(root.keySet().equals(fields), "Recipe missing/unknown field");
    }

    private static JsonArray array(JsonObject root, String key, int maximum) {
        JsonElement value = root.get(key);
        ClassicValueChecks.require(value != null && value.isJsonArray() && value.getAsJsonArray().size() <= maximum,
                "Recipe row type/count");
        return value.getAsJsonArray();
    }

    private static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        ClassicValueChecks.require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
                "Recipe string required");
        return value.getAsString();
    }

    private static int integer(JsonObject root, String key, int minimum, int maximum) {
        int value = RecipeJsonSignature.requireInt(root, key);
        ClassicValueChecks.require(value >= minimum && value <= maximum, "Recipe number range");
        return value;
    }

    private ClassicRecipeCodec() { }
}
