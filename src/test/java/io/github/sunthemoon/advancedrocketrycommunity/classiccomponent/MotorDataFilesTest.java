package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MotorDataFilesTest {
    private static final String DATA = "data/advancedrocketrycommunity/";
    private static final String ASSETS = "assets/advancedrocketrycommunity/";

    @Test
    void bothTagsContainExactlyTheFourTierIdsWithoutReplacingExtensionEntries() {
        var files = MotorDataFiles.server();
        for (String kind : List.of("blocks", "items")) {
            JsonObject tag = files.get(DATA + "tags/" + kind + "/motors.json");
            assertEquals(Set.of("replace", "values"), tag.keySet());
            assertFalse(tag.get("replace").getAsBoolean());
            assertEquals(Arrays.stream(MotorDefinition.values()).map(MotorDefinition::fullId).toList(),
                    tag.getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList());
        }
    }

    @Test
    void shapelessJsonRepeatsQuantitiesAndOnlyOutputsOneMotor() {
        var files = MotorDataFiles.server();
        for (MotorDefinition tier : MotorDefinition.values()) {
            JsonObject recipe = files.get(DATA + "recipes/" + tier.id() + ".json");
            assertEquals(Set.of("type", "category", "ingredients", "result"), recipe.keySet());
            assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
            Map<String, Integer> actual = new HashMap<>();
            for (var input : recipe.getAsJsonArray("ingredients")) {
                var ingredient = input.getAsJsonObject();
                assertEquals(1, ingredient.size());
                var key = ingredient.keySet().iterator().next();
                assertTrue(key.equals("tag") || key.equals("item"));
                actual.merge(key + ":" + ingredient.get(key).getAsString(), 1, Integer::sum);
            }
            Map<String, Integer> expected = new HashMap<>();
            for (var input : tier.ingredients()) { expected.put((input.tag() ? "tag:" : "item:") + input.id(), input.count()); }
            assertEquals(expected, actual);
            assertEquals(tier.fullId(), recipe.getAsJsonObject("result").get("item").getAsString());
            assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        }
    }

    @Test
    void resourcesAreFreshAndStableWithoutWritingExistingCasingsOrToolTags() {
        var first = MotorDataFiles.server();
        var second = MotorDataFiles.server();
        assertEquals(14, first.size());
        assertEquals(first, second);
        first.get(DATA + "tags/blocks/motors.json").getAsJsonArray("values").add("not-a-runtime-output");
        assertEquals(4, second.get(DATA + "tags/blocks/motors.json").getAsJsonArray("values").size());
        assertTrue(first.keySet().stream().noneMatch(path -> path.contains("endgame_casing")
                || path.contains("mineable") || path.contains("lang")));
    }

    @Test
    void eachMotorDropsOnlyItsOwnSingleItem() {
        var files = MotorDataFiles.server();
        for (MotorDefinition tier : MotorDefinition.values()) {
            JsonObject loot = files.get(DATA + "loot_tables/blocks/" + tier.id() + ".json");
            assertEquals("minecraft:block", loot.get("type").getAsString());
            assertEquals(1, loot.getAsJsonArray("pools").size());
            var pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt());
            assertEquals(1, pool.getAsJsonArray("entries").size());
            assertEquals(tier.fullId(), pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("minecraft:survives_explosion",
                    pool.getAsJsonArray("conditions").get(0).getAsJsonObject().get("condition").getAsString());
        }
    }

    @Test
    void modelAndBlockstateReferencesUseEveryOriginalFace() {
        var files = MotorDataFiles.client();
        assertEquals(12, files.size());
        for (MotorDefinition tier : MotorDefinition.values()) {
            var model = files.get(ASSETS + "models/block/" + tier.id() + ".json");
            assertEquals("minecraft:block/cube_bottom_top", model.get("parent").getAsString());
            assertEquals(Set.of("side", "top", "bottom"), model.getAsJsonObject("textures").keySet());
            for (String face : MotorArt.FACES) {
                assertEquals(MotorDataFiles.texture(tier, face), model.getAsJsonObject("textures").get(face).getAsString());
            }
            var item = files.get(ASSETS + "models/item/" + tier.id() + ".json");
            assertEquals("advancedrocketrycommunity:block/" + tier.id(), item.get("parent").getAsString());
            var state = files.get(ASSETS + "blockstates/" + tier.id() + ".json");
            assertEquals(Set.of(""), state.getAsJsonObject("variants").keySet());
        }
    }

    @Test
    void allRecipeAdvancementsUnlockOnlyTheirOwnStableRecipe() {
        var files = MotorDataFiles.server();
        for (MotorDefinition tier : MotorDefinition.values()) {
            var advancement = files.get(DATA + "advancements/recipes/misc/" + tier.id() + ".json");
            assertEquals(tier.fullId(), advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
            assertEquals(1, advancement.getAsJsonArray("requirements").size());
            assertEquals(2, advancement.getAsJsonArray("requirements").get(0).getAsJsonArray().size());
            assertEquals("minecraft:inventory_changed",
                    advancement.getAsJsonObject("criteria").getAsJsonObject("has_input").get("trigger").getAsString());
        }
    }
}
