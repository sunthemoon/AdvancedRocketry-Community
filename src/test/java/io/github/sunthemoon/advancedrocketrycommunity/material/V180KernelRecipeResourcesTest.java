package io.github.sunthemoon.advancedrocketrycommunity.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

/** ADR-064 section 2.4: current tag recipes supersede, but never edit, historical item recipes. */
class V180KernelRecipeResourcesTest {
    private static final String PREFIX = "data/advancedrocketrycommunity/recipes/";
    private static final Map<String, List<String>> TAGS = Map.of(
            "rolling_iron_bars", List.of("forge:ingots/iron"),
            "precision_control_circuit", List.of("forge:ingots/iron", "forge:dusts/redstone"),
            "precision_guidance_module", List.of("forge:ingots/iron", "forge:dusts/redstone",
                    "forge:ingots/gold", "forge:gems/quartz", "forge:ingots/copper"));
    private static final Map<String, List<String>> HISTORICAL_ITEMS = Map.of(
            "rolling_iron_bars", List.of("minecraft:iron_ingot"),
            "precision_control_circuit", List.of("minecraft:iron_ingot", "minecraft:redstone"),
            "precision_guidance_module", List.of("minecraft:iron_ingot", "minecraft:redstone",
                    "minecraft:gold_ingot", "minecraft:quartz", "minecraft:copper_ingot"));

    @Test
    void currentPayloadsChangeOnlySelectorsAndKeepHistoricalInputs() throws IOException {
        for (String id : TAGS.keySet()) {
            JsonObject historical = json(Path.of("src/generated/v1.2/resources", PREFIX + id + ".json"));
            JsonObject expected = historical.deepCopy();
            List<JsonObject> oldIngredients = ingredients(historical);
            List<JsonObject> newIngredients = ingredients(expected);
            assertEquals(HISTORICAL_ITEMS.get(id).size(), oldIngredients.size(), id);
            for (int i = 0; i < oldIngredients.size(); i++) {
                assertEquals(Map.of("item", HISTORICAL_ITEMS.get(id).get(i)), selector(oldIngredients.get(i)), id);
                newIngredients.get(i).remove("item");
                newIngredients.get(i).addProperty("tag", TAGS.get(id).get(i));
            }
            assertEquals(expected, current(id), "only ingredient selectors change: " + id);
        }
    }

    @Test
    void processedResourcesSelectTheCurrentPayloads() throws IOException {
        for (String id : TAGS.keySet()) {
            assertEquals(current(id), json(Path.of("build/resources/main", PREFIX + id + ".json")), id);
        }
    }

    @Test
    void packagedRuntimeContainsExactlyOneCurrentCopyOfEveryStableId() throws IOException {
        try (ZipFile jar = new ZipFile(System.getProperty("arce.runtimeJar"))) {
            for (String id : TAGS.keySet()) {
                String entry = PREFIX + id + ".json";
                assertEquals(1L, jar.stream().filter(value -> value.getName().equals(entry)).count(), id);
                assertNotNull(jar.getEntry(entry), id);
                try (var input = jar.getInputStream(jar.getEntry(entry))) {
                    assertEquals(current(id), JsonParser.parseString(new String(input.readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(), id);
                }
            }
        }
    }

    private static List<JsonObject> ingredients(JsonObject recipe) {
        if (recipe.has("ingredient")) {
            return List.of(recipe.getAsJsonObject("ingredient"));
        }
        return recipe.getAsJsonArray("inputs").asList().stream()
                .map(value -> value.getAsJsonObject().getAsJsonObject("ingredient")).toList();
    }

    private static Map<String, String> selector(JsonObject ingredient) {
        return ingredient.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, value -> value.getValue().getAsString()));
    }

    private static JsonObject current(String id) throws IOException {
        return json(Path.of("src/generated/v1.8/resources", PREFIX + id + ".json"));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
