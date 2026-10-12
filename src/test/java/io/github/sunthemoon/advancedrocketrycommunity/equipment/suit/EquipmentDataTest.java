package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180EquipmentData;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EquipmentDataTest {
    @Test void allElevenModulesHaveModelsOriginalPngsRecipesAndBothNames() {
        var client = V180EquipmentData.clientFiles(); var server = V180EquipmentData.serverFiles();
        var textures = V180EquipmentData.textures(); var en = V180EquipmentData.language(false); var zh = V180EquipmentData.language(true);
        assertEquals(11, V180EquipmentData.moduleIds().size()); assertEquals(14, textures.size()); assertEquals(en.keySet(), zh.keySet());
        for (String id : V180EquipmentData.moduleIds()) {
            assertTrue(client.containsKey("assets/advancedrocketrycommunity/models/item/" + id + ".json"));
            var recipe = server.get("data/advancedrocketrycommunity/recipes/" + id + ".json");
            assertEquals("advancedrocketrycommunity:" + id, recipe.getAsJsonObject("result").get("item").getAsString());
            assertTrue(en.containsKey("item.advancedrocketrycommunity." + id)); assertFalse(zh.get("item.advancedrocketrycommunity." + id).isBlank());
            byte[] png = textures.get("assets/advancedrocketrycommunity/textures/item/" + id + ".png");
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10}, Arrays.copyOf(png, 8));
            assertArrayEquals(png, V180EquipmentData.textures().get("assets/advancedrocketrycommunity/textures/item/" + id + ".png"));
        }
        assertTrue(server.containsKey("data/advancedrocketrycommunity/loot_tables/blocks/suit_workstation.json"));
        assertTrue(server.keySet().stream().noneMatch(path -> path.contains("space_breathing") || path.contains("beacon_finder")));
    }
    @Test void tankRecipesNeverDiscardAnExistingTankOrItsGas() {
        V180EquipmentData.serverFiles().forEach((path, recipe) -> {
            if (path.startsWith("data/advancedrocketrycommunity/recipes/") && path.endsWith("pressure_tank.json")) {
                assertFalse(recipe.getAsJsonObject("key").toString().contains("pressure_tank"));
            }
        });
    }
}
