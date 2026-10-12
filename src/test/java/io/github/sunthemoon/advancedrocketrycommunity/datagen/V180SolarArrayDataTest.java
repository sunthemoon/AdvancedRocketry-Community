package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Locale;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayRules;
import org.junit.jupiter.api.Test;

class V180SolarArrayDataTest {
    @Test void exactFilesCoverModelsRotationsLootRecipesAndCellTag() {
        var client = V180SolarArrayData.clientFiles(); var server = V180SolarArrayData.serverFiles();
        assertEquals(9, client.size()); assertEquals(10, server.size());
        assertEquals(4, client.get("assets/advancedrocketrycommunity/blockstates/solar_array.json").getAsJsonObject("variants").size());
        for (String id : V180SolarArrayData.IDS) {
            assertTrue(client.containsKey("assets/advancedrocketrycommunity/models/item/" + id + ".json"));
            var loot = server.get("data/advancedrocketrycommunity/loot_tables/blocks/" + id + ".json").toString();
            assertFalse(loot.contains("copy_nbt")); assertFalse(loot.contains("energy"));
            assertEquals("advancedrocketrycommunity:" + id,
                    server.get("data/advancedrocketrycommunity/recipes/" + id + ".json").getAsJsonObject("result").get("item").getAsString());
        }
        assertEquals("[\"GGG\",\"SSS\",\"CCC\"]", server.get("data/advancedrocketrycommunity/recipes/solar_array_panel.json").get("pattern").toString());
        assertEquals("[\"PGP\",\"CMC\",\"III\"]", server.get("data/advancedrocketrycommunity/recipes/solar_array.json").get("pattern").toString());
        assertEquals(2, server.get("data/advancedrocketrycommunity/recipes/power_output_plug.json").getAsJsonObject("result").get("count").getAsInt());
        assertEquals(2, server.get("data/advancedrocketrycommunity/tags/blocks/solar_array_cells.json").getAsJsonArray("values").size());
    }
    @Test void fiveOriginalGridsAreOpaqueAndDeterministic() {
        assertEquals(5, V180SolarArrayData.grids().size());
        for (var entry : V180SolarArrayData.grids().entrySet()) {
            assertEquals(16, entry.getValue().length);
            for (String row : entry.getValue()) { assertTrue(row.matches("[0-9]{16}")); }
            for (int pixel : V180MaterialArt.pixels(entry.getValue(), V180SolarArrayData.tint(entry.getKey()))) { assertEquals(255, pixel >>> 24); }
            String path = "assets/advancedrocketrycommunity/textures/block/" + entry.getKey() + ".png";
            assertArrayEquals(V180SolarArrayData.textures().get(path), V180SolarArrayData.textures().get(path));
        }
    }
    @Test void bilingualStructureReasonsAndJeiInformationAreComplete() {
        var english = V180SolarArrayLanguage.entries(false); var chinese = V180SolarArrayLanguage.entries(true);
        assertEquals(english.keySet(), chinese.keySet()); assertEquals(12, english.size());
        for (var state : SolarArrayRules.Structure.values()) {
            assertTrue(english.containsKey("screen.advancedrocketrycommunity.solar_array.structure." + state.name().toLowerCase(Locale.ROOT)));
        }
        assertTrue(english.get("jei.advancedrocketrycommunity.solar_array").contains("breaking destroys stored FE"));
    }
}
