package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180LifeSupportData;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LifeSupportDataTest {
    @Test void everyDeviceHasModelsLootRecipesOriginalDeterministicArtAndTwoLanguages() {
        var client = V180LifeSupportData.clientFiles(); var server = V180LifeSupportData.serverFiles(); var textures = V180LifeSupportData.textures();
        assertEquals(10, client.size()); assertEquals(11, server.size()); assertEquals(4, textures.size());
        assertEquals(V180LifeSupportData.language(false).keySet(), V180LifeSupportData.language(true).keySet());
        for (String id : V180LifeSupportData.BLOCKS) {
            assertTrue(client.containsKey("assets/advancedrocketrycommunity/blockstates/" + id + ".json"));
            assertTrue(server.containsKey("data/advancedrocketrycommunity/loot_tables/blocks/" + id + ".json"));
            assertTrue(server.containsKey("data/advancedrocketrycommunity/recipes/" + id + ".json"));
        }
        textures.forEach((path, bytes) -> {
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10}, Arrays.copyOf(bytes, 8));
            assertArrayEquals(bytes, V180LifeSupportData.textures().get(path));
        });
        var recipe = server.get("data/advancedrocketrycommunity/recipes/carbon_scrubber_cartridge.json");
        assertFalse(recipe.getAsJsonObject("key").toString().contains("carbon_scrubber_cartridge"));
        assertTrue(V180LifeSupportData.language(true).values().stream().noneMatch(String::isBlank));
    }
}
