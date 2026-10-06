package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import org.junit.jupiter.api.Test;

class V180SealDetectorDataTest {
    @Test void recipeHasExactVanillaMultisetAndSingletonStableOutput() {
        var files = V180SealDetectorData.serverFiles();
        assertEquals(2, files.size());
        var recipe = files.get("data/advancedrocketrycommunity/recipes/seal_detector.json");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals(List.of("minecraft:iron_ingot", "minecraft:redstone", "minecraft:glass_pane"),
                java.util.stream.StreamSupport.stream(recipe.getAsJsonArray("ingredients").spliterator(), false)
                        .map(v -> v.getAsJsonObject().get("item").getAsString()).toList());
        assertEquals(V180SealDetectorData.ID, recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test void unlockIsRedstoneOrRecipeUnlockedAndRewardsOnlyThisRecipe() {
        var unlock = V180SealDetectorData.serverFiles().get("data/advancedrocketrycommunity/advancements/recipes/misc/seal_detector.json");
        assertEquals("minecraft:recipes/root", unlock.get("parent").getAsString());
        var criteria = unlock.getAsJsonObject("criteria");
        assertEquals(Set.of("has_input", "has_the_recipe"), criteria.keySet());
        assertEquals("minecraft:inventory_changed", criteria.getAsJsonObject("has_input").get("trigger").getAsString());
        assertEquals("minecraft:redstone", criteria.getAsJsonObject("has_input").getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsString());
        assertEquals("minecraft:recipe_unlocked", criteria.getAsJsonObject("has_the_recipe").get("trigger").getAsString());
        assertEquals(V180SealDetectorData.ID, criteria.getAsJsonObject("has_the_recipe").getAsJsonObject("conditions").get("recipe").getAsString());
        assertEquals(1, unlock.getAsJsonArray("requirements").size());
        assertEquals(List.of("has_input", "has_the_recipe"), java.util.stream.StreamSupport.stream(
                unlock.getAsJsonArray("requirements").get(0).getAsJsonArray().spliterator(), false).map(v -> v.getAsString()).toList());
        assertEquals(1, unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").size());
        assertEquals(V180SealDetectorData.ID, unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
    }

    @Test void modelAndJsonMapsAreAdditiveAndFresh() {
        var model = V180SealDetectorData.clientFiles().get("assets/advancedrocketrycommunity/models/item/seal_detector.json");
        assertEquals(1, V180SealDetectorData.clientFiles().size());
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("advancedrocketrycommunity:item/seal_detector", model.getAsJsonObject("textures").get("layer0").getAsString());
        model.addProperty("mutation", true);
        assertFalse(V180SealDetectorData.clientFiles().values().iterator().next().has("mutation"));
        assertEquals("assets/advancedrocketrycommunity/textures/item/seal_detector.png", V180SealDetectorData.TEXTURE);
    }

    @Test void taskGridAndTintGenerateDeterministicBoundedRgbaPng() throws Exception {
        var grid = V180SealDetectorData.grid();
        assertEquals(16, grid.length);
        assertTrue(java.util.Arrays.stream(grid).allMatch(r -> r.length() == 16 && r.matches("[.234679]+")));
        assertArrayEquals(V180MaterialArt.png(grid, 0xE0BD72), V180SealDetectorData.texture());
        byte[] png = V180SealDetectorData.texture();
        assertArrayEquals(png, V180SealDetectorData.texture());
        assertTrue(png.length < 4096);
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, java.util.Arrays.copyOf(png, 8));
        ByteBuffer bytes = ByteBuffer.wrap(png);
        assertEquals(16, bytes.getInt(16)); assertEquals(16, bytes.getInt(20));
        assertEquals(8, png[24]); assertEquals(6, png[25]);
        int length = bytes.getInt(33);
        assertEquals("IDAT", new String(png, 37, 4, java.nio.charset.StandardCharsets.US_ASCII));
        byte[] raw = new InflaterInputStream(new ByteArrayInputStream(png, 41, length)).readAllBytes();
        assertEquals(1040, raw.length);
        for (int y = 0; y < 16; y++) { for (int x = 0; x < 16; x++) {
            int offset = y * 65 + 1 + x * 4;
            assertEquals(0, raw[y * 65]);
            assertEquals(grid[y].charAt(x) == '.' ? 0 : 255, Byte.toUnsignedInt(raw[offset + 3]));
        } }
        grid[0] = "0000000000000000";
        assertEquals("................", V180SealDetectorData.grid()[0]);
    }

    @Test void bilingualKeysAreExactlyTwelveImmutableAndNeverCertifyRoomOrOxygenAbsence() {
        var en = V180SealDetectorLanguage.translations(false);
        var zh = V180SealDetectorLanguage.translations(true);
        assertEquals(12, en.size()); assertEquals(en.keySet(), zh.keySet());
        assertThrows(UnsupportedOperationException.class, () -> en.put("extra", "value"));
        assertEquals("Seal detector | Boundary: %s | Adjacent supply: %s", en.get("message.advancedrocketrycommunity.seal_detector.reading"));
        assertEquals("密封检测器 | 边界：%s | 相邻供气：%s", zh.get("message.advancedrocketrycommunity.seal_detector.reading"));
        assertTrue(en.get("tooltip.advancedrocketrycommunity.seal_detector.measurement_only").contains("does not certify"));
        assertEquals("NOT KNOWN SUPPLIED", en.get("message.advancedrocketrycommunity.seal_detector.supply.not_known_supplied"));
        assertFalse(String.join(" ", en.values()).contains("no oxygen"));
    }
}
