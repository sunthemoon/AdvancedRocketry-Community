package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.InflaterInputStream;
import org.junit.jupiter.api.Test;

class V180AtmosphereAnalyzerDataTest {
    @Test void newShapelessRecipeHasExactAdoptedMultisetAndSingletonOutput() {
        var files = V180AtmosphereAnalyzerData.serverFiles();
        assertEquals(2, files.size());
        var recipe = files.get("data/advancedrocketrycommunity/recipes/atmosphere_analyzer.json");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        var ingredients = recipe.getAsJsonArray("ingredients");
        assertEquals(List.of("advancedrocketrycommunity:basic_circuit", "minecraft:glass_pane", "minecraft:iron_ingot"),
                java.util.stream.StreamSupport.stream(ingredients.spliterator(), false)
                        .map(value -> value.getAsJsonObject().get("item").getAsString()).toList());
        assertEquals(V180AtmosphereAnalyzerData.ID, recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(2, recipe.getAsJsonObject("result").size());
    }

    @Test void normalInputAndRecipeUnlockedCriteriaUseVanillaOrUnlockSemantics() {
        var unlock = V180AtmosphereAnalyzerData.serverFiles().get("data/advancedrocketrycommunity/advancements/recipes/misc/atmosphere_analyzer.json");
        assertEquals("minecraft:recipes/root", unlock.get("parent").getAsString());
        var criteria = unlock.getAsJsonObject("criteria");
        assertEquals("minecraft:inventory_changed", criteria.getAsJsonObject("has_input").get("trigger").getAsString());
        assertEquals("advancedrocketrycommunity:basic_circuit", criteria.getAsJsonObject("has_input").getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsString());
        assertEquals("minecraft:recipe_unlocked", criteria.getAsJsonObject("has_the_recipe").get("trigger").getAsString());
        assertEquals(1, unlock.getAsJsonArray("requirements").size());
        assertEquals(2, unlock.getAsJsonArray("requirements").get(0).getAsJsonArray().size());
        assertEquals(V180AtmosphereAnalyzerData.ID, unlock.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
    }

    @Test void modelAndProviderPathsAreAdditiveAndFreshPerCall() {
        var files = V180AtmosphereAnalyzerData.clientFiles();
        assertEquals(1, files.size());
        var model = files.get("assets/advancedrocketrycommunity/models/item/atmosphere_analyzer.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("advancedrocketrycommunity:item/atmosphere_analyzer", model.getAsJsonObject("textures").get("layer0").getAsString());
        model.addProperty("mutated", true);
        assertFalse(V180AtmosphereAnalyzerData.clientFiles().values().iterator().next().has("mutated"));
        assertEquals("assets/advancedrocketrycommunity/textures/item/atmosphere_analyzer.png", V180AtmosphereAnalyzerData.TEXTURE);
    }

    @Test void originalPngIsDeterministic16By16RgbaWithTransparentSilhouette() throws Exception {
        byte[] png = V180AtmosphereAnalyzerData.texture();
        assertArrayEquals(png, V180AtmosphereAnalyzerData.texture());
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, java.util.Arrays.copyOf(png, 8));
        ByteBuffer bytes = ByteBuffer.wrap(png);
        assertEquals(16, bytes.getInt(16)); assertEquals(16, bytes.getInt(20));
        assertEquals(8, png[24]); assertEquals(6, png[25]);
        int length = bytes.getInt(33);
        assertEquals("IDAT", new String(png, 37, 4, java.nio.charset.StandardCharsets.US_ASCII));
        byte[] pixels = new InflaterInputStream(new ByteArrayInputStream(png, 41, length)).readAllBytes();
        assertEquals(16 * 65, pixels.length);
        assertEquals(0, pixels[4]); // Transparent first pixel alpha after filter byte.
        assertTrue(java.util.stream.IntStream.range(0, 16).allMatch(y -> pixels[y * 65] == 0));
        assertTrue(png.length < 4096);
        var grid = V180AtmosphereAnalyzerData.grid(); grid[0] = "0000000000000000";
        assertEquals("................", V180AtmosphereAnalyzerData.grid()[0]);
    }
}
