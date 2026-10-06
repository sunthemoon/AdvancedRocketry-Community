package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.BoundedItemIngredientCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeJsonSignature;
import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;

/** Immutable recipe data; construction never resolves tags or accesses an Item. */
public record ClassicItemInput(String canonicalIngredientJson, int count) {
    public ClassicItemInput {
        Objects.requireNonNull(canonicalIngredientJson, "canonicalIngredientJson");
        ClassicValueChecks.require(count >= 1 && count <= 64, "Recipe Item input count");
        ClassicValueChecks.require(canonicalIngredientJson.length()
                <= BoundedItemIngredientCodec.MAX_INGREDIENT_JSON_CHARS, "Recipe ingredient text limit");
        JsonElement ingredient = parseShallow(canonicalIngredientJson);
        BoundedItemIngredientCodec.validateOnly(ingredient);
        ClassicValueChecks.require(RecipeJsonSignature.canonical(ingredient).equals(canonicalIngredientJson),
                "Recipe ingredient must be canonical JSON");
    }

    private static JsonElement parseShallow(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement result;
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    ClassicValueChecks.require(array.size() < ProcessInput.MAX_VARIANTS,
                            "Recipe ingredient alternative limit");
                    array.add(readEntry(reader));
                }
                reader.endArray();
                result = array;
            } else {
                result = readEntry(reader);
            }
            ClassicValueChecks.require(reader.peek() == JsonToken.END_DOCUMENT,
                    "Trailing recipe ingredient data");
            return result;
        } catch (IOException | IllegalStateException invalid) {
            throw new IllegalArgumentException("Invalid recipe ingredient JSON", invalid);
        }
    }

    private static JsonObject readEntry(JsonReader reader) throws IOException {
        ClassicValueChecks.require(reader.peek() == JsonToken.BEGIN_OBJECT,
                "Recipe ingredient entry must be an object");
        reader.beginObject();
        ClassicValueChecks.require(reader.hasNext(), "Empty recipe ingredient entry");
        String key = reader.nextName();
        ClassicValueChecks.require(key.equals("item") || key.equals("tag"), "Recipe ingredient field");
        ClassicValueChecks.require(reader.peek() == JsonToken.STRING, "Recipe ingredient ID must be a string");
        JsonObject entry = new JsonObject();
        entry.addProperty(key, reader.nextString());
        ClassicValueChecks.require(!reader.hasNext(), "Recipe ingredient requires exactly one field");
        reader.endObject();
        return entry;
    }
}
