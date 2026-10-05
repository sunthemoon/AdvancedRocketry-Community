package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class V180SolarDataTest {
    @Test void exactSixClientAndSixServerOutputsUseOnlySelectedIds() {
        var client = V180SolarData.clientFiles(); var server = V180SolarData.serverFiles();
        assertEquals(6, client.size()); assertEquals(6, server.size());
        assertEquals(Set.of("assets/advancedrocketrycommunity/blockstates/solar_generator.json",
                "assets/advancedrocketrycommunity/blockstates/solar_panel.json",
                "assets/advancedrocketrycommunity/models/block/solar_generator.json",
                "assets/advancedrocketrycommunity/models/block/solar_panel.json",
                "assets/advancedrocketrycommunity/models/item/solar_generator.json",
                "assets/advancedrocketrycommunity/models/item/solar_panel.json"), client.keySet());
        client.values().iterator().next().addProperty("mutated", true);
        assertTrue(V180SolarData.clientFiles().values().stream().noneMatch(value -> value.has("mutated")));
        assertEquals("minecraft:block/cube", V180SolarData.clientFiles().get(
                "assets/advancedrocketrycommunity/models/block/solar_generator.json").get("parent").getAsString());
        assertEquals("minecraft:block/cube_all", V180SolarData.clientFiles().get(
                "assets/advancedrocketrycommunity/models/block/solar_panel.json").get("parent").getAsString());
    }
    @Test void recipesMatchSelectedNineSlotGridsAndOneSelfResult() {
        var files = V180SolarData.serverFiles();
        var panel = files.get("data/advancedrocketrycommunity/recipes/solar_panel.json");
        var generator = files.get("data/advancedrocketrycommunity/recipes/solar_generator.json");
        assertEquals("[\"GGG\",\"SSS\",\"CCC\"]", panel.getAsJsonArray("pattern").toString());
        assertEquals("[\"PPP\",\"CGC\",\"CCC\"]", generator.getAsJsonArray("pattern").toString());
        assertEquals("forge:glass", panel.getAsJsonObject("key").getAsJsonObject("G").get("tag").getAsString());
        assertEquals("advancedrocketrycommunity:silicon_wafer", panel.getAsJsonObject("key").getAsJsonObject("S").get("item").getAsString());
        assertEquals("forge:ingots/copper", generator.getAsJsonObject("key").getAsJsonObject("C").get("tag").getAsString());
        for (String id : new String[]{"solar_generator", "solar_panel"}) {
            var result = files.get("data/advancedrocketrycommunity/recipes/" + id + ".json").getAsJsonObject("result");
            assertEquals("advancedrocketrycommunity:" + id, result.get("item").getAsString());
            assertEquals(1, result.get("count").getAsInt());
            var loot = files.get("data/advancedrocketrycommunity/loot_tables/blocks/" + id + ".json");
            assertFalse(loot.toString().contains("copy_nbt")); assertFalse(loot.toString().contains("energy"));
            assertTrue(loot.toString().contains("minecraft:explosion_decay"));
        }
    }
    @Test void sameIdRecipeUnlockUsesOrAndOneRecipeReward() {
        for (String id : new String[]{"solar_generator", "solar_panel"}) {
            var unlock = V180SolarData.serverFiles().get("data/advancedrocketrycommunity/advancements/recipes/building_blocks/" + id + ".json");
            assertEquals("[[\"has_ingredient\",\"has_the_recipe\"]]", unlock.get("requirements").toString());
            assertEquals("[\"advancedrocketrycommunity:" + id + "\"]", unlock.getAsJsonObject("rewards").get("recipes").toString());
            assertEquals("minecraft:recipe_unlocked", unlock.getAsJsonObject("criteria").getAsJsonObject("has_the_recipe").get("trigger").getAsString());
        }
    }
    @Test void fourOriginalOpaqueGridsEncodeDeterministicallyWithValidPngCrc() throws Exception {
        assertEquals(4, V180SolarData.grids().size());
        for (var entry : V180SolarData.grids().entrySet()) {
            var grid = entry.getValue(); assertEquals(16, grid.length);
            for (String row : grid) { assertTrue(row.matches("[0-9]{16}")); }
            for (int pixel : V180MaterialArt.pixels(grid, V180SolarData.tint(entry.getKey()))) { assertEquals(255, pixel >>> 24); }
            byte[] png = V180SolarData.textures().get("assets/advancedrocketrycommunity/textures/block/" + entry.getKey() + ".png");
            assertArrayEquals(png, V180SolarData.textures().get("assets/advancedrocketrycommunity/textures/block/" + entry.getKey() + ".png"));
            DataInputStream stream = new DataInputStream(new ByteArrayInputStream(png));
            assertEquals(0x89504e470d0a1a0aL, stream.readLong());
            for (String type : new String[]{"IHDR", "IDAT", "IEND"}) {
                int size = stream.readInt(); byte[] name = stream.readNBytes(4), body = stream.readNBytes(size);
                assertEquals(type, new String(name, StandardCharsets.US_ASCII));
                CRC32 crc = new CRC32(); crc.update(name); crc.update(body);
                assertEquals((int) crc.getValue(), stream.readInt());
                if (type.equals("IHDR")) { DataInputStream header = new DataInputStream(new ByteArrayInputStream(body));
                    assertEquals(16, header.readInt()); assertEquals(16, header.readInt()); assertEquals(8, header.readByte()); assertEquals(6, header.readByte()); }
            }
            assertEquals(-1, stream.read());
        }
    }
    @Test void bilingualKeySetCoversAllFixedReasonAndContextCodes() {
        var english = V180SolarLanguage.entries(false); var chinese = V180SolarLanguage.entries(true);
        assertEquals(english.keySet(), chinese.keySet()); assertEquals(21, english.size());
        assertEquals("Solar Generator", english.get("block.advancedrocketrycommunity.solar_generator"));
        assertEquals("太阳能板", chinese.get("block.advancedrocketrycommunity.solar_panel"));
        for (var reason : io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneration.Reason.values()) {
            assertTrue(english.containsKey("screen.advancedrocketrycommunity.solar.reason." + reason.name().toLowerCase(java.util.Locale.ROOT)));
        }
        for (int context = 0; context <= 3; context++) { assertTrue(english.containsKey("screen.advancedrocketrycommunity.solar.context." + context)); }
    }
}
