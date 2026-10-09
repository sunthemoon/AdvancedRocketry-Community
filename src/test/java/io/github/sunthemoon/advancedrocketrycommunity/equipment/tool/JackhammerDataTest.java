package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180JackhammerData;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180JackhammerLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180MaterialArt;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import java.util.zip.InflaterInputStream;
import org.junit.jupiter.api.Test;

class JackhammerDataTest {
    @Test void recipePreservesReviewedShapeCountsAndOnlyExistingModernTags() {
        var recipe = V180JackhammerData.serverFiles().get("data/advancedrocketrycommunity/recipes/jackhammer.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("equipment", recipe.get("category").getAsString());
        var rows = StreamSupport.stream(recipe.getAsJsonArray("pattern").spliterator(), false).map(v -> v.getAsString()).toList();
        assertEquals(List.of(" pt", "imp", "di "), rows);
        var counts = new java.util.HashMap<Character, Integer>();
        rows.forEach(row -> row.chars().filter(c -> c != ' ').forEach(c -> counts.merge((char) c, 1, Integer::sum)));
        assertEquals(Map.of('d', 1, 't', 1, 'i', 2, 'p', 2, 'm', 1), counts);
        var key = recipe.getAsJsonObject("key");
        assertEquals(Set.of("d", "t", "i", "p", "m"), key.keySet());
        Map.of("d", "forge:gems/diamond", "t", "forge:rods/titanium", "i", "forge:rods/iron",
                "p", "forge:plates/aluminum", "m", "advancedrocketrycommunity:motors").forEach((symbol, tag) -> {
                    assertEquals(Set.of("tag"), key.getAsJsonObject(symbol).keySet());
                    assertEquals(tag, key.getAsJsonObject(symbol).get("tag").getAsString());
                });
        assertEquals(V180JackhammerData.ID, recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test void unlockUsesAnyMotorOrPreviouslyUnlockedRecipeOnly() {
        var unlock = V180JackhammerData.serverFiles().get("data/advancedrocketrycommunity/advancements/recipes/tools/jackhammer.json");
        assertEquals("minecraft:recipes/root", unlock.get("parent").getAsString());
        var criteria = unlock.getAsJsonObject("criteria");
        assertEquals(Set.of("has_motor", "has_the_recipe"), criteria.keySet());
        assertEquals("minecraft:inventory_changed", criteria.getAsJsonObject("has_motor").get("trigger").getAsString());
        assertEquals("advancedrocketrycommunity:motors", criteria.getAsJsonObject("has_motor").getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("minecraft:recipe_unlocked", criteria.getAsJsonObject("has_the_recipe").get("trigger").getAsString());
        assertEquals(V180JackhammerData.ID, criteria.getAsJsonObject("has_the_recipe").getAsJsonObject("conditions").get("recipe").getAsString());
        assertEquals("[[\"has_motor\",\"has_the_recipe\"]]", unlock.get("requirements").toString());
        assertEquals("[\"advancedrocketrycommunity:jackhammer\"]", unlock.getAsJsonObject("rewards").get("recipes").toString());
    }

    @Test void modelUsesHandheldReferenceAndTagIsAdditive() {
        var model = V180JackhammerData.clientFiles().get("assets/advancedrocketrycommunity/models/item/jackhammer.json");
        assertEquals(1, V180JackhammerData.clientFiles().size());
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
        assertEquals("advancedrocketrycommunity:item/jackhammer", model.getAsJsonObject("textures").get("layer0").getAsString());
        var tag = V180JackhammerData.serverFiles().get("data/minecraft/tags/items/pickaxes.json");
        assertFalse(tag.get("replace").getAsBoolean());
        assertEquals("[\"advancedrocketrycommunity:jackhammer\"]", tag.get("values").toString());
        assertEquals(3, V180JackhammerData.serverFiles().size());
    }

    @Test void proposedGridProducesDeterministicSixteenPixelRgbaAnd82OpaquePixels() throws Exception {
        String[] grid = V180JackhammerData.grid();
        assertEquals(16, grid.length);
        assertTrue(java.util.Arrays.stream(grid).allMatch(row -> row.length() == 16 && row.matches("[.256789]+")));
        assertEquals(82, java.util.Arrays.stream(grid).flatMapToInt(String::chars).filter(c -> c != '.').count());
        byte[] png = V180JackhammerData.texture();
        assertArrayEquals(V180MaterialArt.png(grid, 0xBDD0DE), png);
        assertArrayEquals(png, V180JackhammerData.texture());
        assertTrue(png.length < 4096);
        ByteBuffer bytes = ByteBuffer.wrap(png);
        assertEquals(16, bytes.getInt(16)); assertEquals(16, bytes.getInt(20));
        assertEquals(8, png[24]); assertEquals(6, png[25]);
        int size = bytes.getInt(33);
        byte[] raw = new InflaterInputStream(new ByteArrayInputStream(png, 41, size)).readAllBytes();
        assertEquals(1040, raw.length);
        for (int y = 0; y < 16; y++) {
            assertEquals(0, raw[y * 65]);
            for (int x = 0; x < 16; x++) {
                assertEquals(grid[y].charAt(x) == '.' ? 0 : 255, Byte.toUnsignedInt(raw[y * 65 + 1 + x * 4 + 3]));
            }
        }
    }

    @Test void generatedMapsAndGridDoNotShareMutableOutput() {
        var files = V180JackhammerData.serverFiles(); files.values().iterator().next().addProperty("changed", true);
        assertTrue(V180JackhammerData.serverFiles().values().stream().noneMatch(file -> file.has("changed")));
        String[] grid = V180JackhammerData.grid(); grid[0] = "0000000000000000";
        assertEquals("................", V180JackhammerData.grid()[0]);
    }

    @Test void bilingualNamesAndPlayerDescriptionsAreCompleteAndImmutable() {
        var en = V180JackhammerLanguage.translations(false); var zh = V180JackhammerLanguage.translations(true);
        assertEquals(4, en.size()); assertEquals(en.keySet(), zh.keySet());
        assertEquals("Jackhammer", en.get("item.advancedrocketrycommunity.jackhammer"));
        assertEquals("钻锤", zh.get("item.advancedrocketrycommunity.jackhammer"));
        assertTrue(en.values().stream().allMatch(value -> !value.isBlank()));
        assertTrue(zh.values().stream().allMatch(value -> !value.isBlank()));
        assertThrows(UnsupportedOperationException.class, () -> en.put("extra", "bad"));
    }
}
