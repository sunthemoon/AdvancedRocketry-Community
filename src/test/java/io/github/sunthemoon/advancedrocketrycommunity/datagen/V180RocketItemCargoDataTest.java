package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180RocketItemCargoDataTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void bothShapedRecipesUseTheItemBoardsAndDistinctOrientation() {
        var files = V180RocketItemCargoData.serverFiles();
        assertEquals(6, files.size());
        for (var role : RocketCargoRole.values()) {
            String id = RocketItemCargoBlock.id(role);
            var recipe = files.get("data/advancedrocketrycommunity/recipes/" + id + ".json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals(" H ", recipe.getAsJsonArray("pattern").get(role == RocketCargoRole.LOAD ? 0 : 1).getAsString());
            assertEquals("IMC", recipe.getAsJsonArray("pattern").get(role == RocketCargoRole.LOAD ? 1 : 0).getAsString());
            var key = recipe.getAsJsonObject("key");
            assertEquals("minecraft:hopper", key.getAsJsonObject("H").get("item").getAsString());
            assertEquals("advancedrocketrycommunity:item_io_board", key.getAsJsonObject("I").get("item").getAsString());
            assertEquals("advancedrocketrycommunity:machine_casing", key.getAsJsonObject("M").get("item").getAsString());
            assertEquals("advancedrocketrycommunity:control_board", key.getAsJsonObject("C").get("item").getAsString());
            assertEquals(4, key.size());
            assertEquals("advancedrocketrycommunity:" + id, recipe.getAsJsonObject("result").get("item").getAsString());
            var loot = files.get("data/advancedrocketrycommunity/loot_tables/blocks/" + id + ".json");
            assertEquals("advancedrocketrycommunity:" + id, loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        }
        assertEquals(6, V180RocketItemCargoData.clientFiles().size());
    }

    @Test void bilingualNamesAndScreenTextAreNewAndTheSharedStatusesAreNotRepeated() {
        for (boolean chinese : new boolean[] {false, true}) {
            var entries = V180RocketItemCargoData.language(chinese);
            assertEquals(7, entries.size());
            for (var role : RocketCargoRole.values()) {
                assertFalse(entries.get("block.advancedrocketrycommunity." + RocketItemCargoBlock.id(role)).isBlank());
            }
            for (var value : entries.values()) { assertFalse(value.isBlank()); }
            var fluid = V180RocketCargoData.language(chinese);
            for (var key : entries.keySet()) { assertFalse(fluid.containsKey(key), key); }
            // Every status the item devices show comes from W109's shared keys.
            for (var status : RocketCargoStatus.values()) { assertTrue(fluid.containsKey(status.translationKey())); }
        }
        assertEquals("Rocket Item Loader", V180RocketItemCargoData.language(false).get("block.advancedrocketrycommunity.rocket_item_loader"));
        assertEquals("火箭物品卸载器", V180RocketItemCargoData.language(true).get("block.advancedrocketrycommunity.rocket_item_unloader"));
    }

    @Test void originalPortMarksDifferFromEachOtherAndFromTheFluidDevices() throws Exception {
        var load = V180RocketItemCargoArt.grid(false);
        var unload = V180RocketItemCargoArt.grid(true);
        assertFalse(Arrays.equals(load, unload));
        for (var fluid : new String[][] {V180RocketCargoArt.grid(false), V180RocketCargoArt.grid(true)}) {
            assertFalse(Arrays.equals(load, fluid));
            assertFalse(Arrays.equals(unload, fluid));
        }
        var textures = V180RocketItemCargoArt.textures();
        assertEquals(2, textures.size());
        for (var entry : textures.entrySet()) {
            var image = ImageIO.read(new ByteArrayInputStream(entry.getValue()));
            assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
            assertArrayEquals(entry.getValue(), V180RocketItemCargoArt.textures().get(entry.getKey()));
            for (var fluid : V180RocketCargoArt.textures().values()) { assertFalse(Arrays.equals(entry.getValue(), fluid)); }
        }
    }

    @Test void committedResourcesMatchGenerators() throws Exception {
        Path root = Path.of("src/generated/v1.8/resources");
        var jsons = new java.util.LinkedHashMap<>(V180RocketItemCargoData.clientFiles());
        jsons.putAll(V180RocketItemCargoData.serverFiles());
        for (var entry : jsons.entrySet()) {
            assertEquals(entry.getValue(), com.google.gson.JsonParser.parseString(Files.readString(root.resolve(entry.getKey()))));
        }
        for (var entry : V180RocketItemCargoArt.textures().entrySet()) {
            assertArrayEquals(entry.getValue(), Files.readAllBytes(root.resolve(entry.getKey())));
        }
        for (String locale : new String[] {"en_us", "zh_cn"}) {
            var language = com.google.gson.JsonParser.parseString(Files.readString(root.resolve(
                    "assets/advancedrocketrycommunity_v180/lang/" + locale + ".json"))).getAsJsonObject();
            V180RocketItemCargoData.language(locale.equals("zh_cn")).forEach((key, value) -> assertEquals(value, language.get(key).getAsString()));
        }
    }
}
