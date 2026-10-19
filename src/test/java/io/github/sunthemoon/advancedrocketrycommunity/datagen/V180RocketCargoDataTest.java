package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180RocketCargoDataTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void bothShapedRecipesUseTheEmptyTankIngredientAndDistinctOrientation() {
        var files = V180RocketCargoData.serverFiles();
        assertEquals(6, files.size());
        for (var role : RocketCargoRole.values()) {
            var recipe = files.get("data/advancedrocketrycommunity/recipes/" + role.id() + ".json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals(" H ", recipe.getAsJsonArray("pattern").get(role == RocketCargoRole.LOAD ? 0 : 1).getAsString());
            assertEquals("LMT", recipe.getAsJsonArray("pattern").get(role == RocketCargoRole.LOAD ? 1 : 0).getAsString());
            assertEquals(V180FluidFuelLoaderData.EMPTY_TANK, recipe.getAsJsonObject("key").getAsJsonObject("T").get("type").getAsString());
            assertEquals("advancedrocketrycommunity:" + role.id(), recipe.getAsJsonObject("result").get("item").getAsString());
        }
        assertEquals(6, V180RocketCargoData.clientFiles().size());
    }

    @Test void bilingualNamesAndEveryStatusArePresent() {
        for (boolean chinese : new boolean[] {false, true}) {
            var entries = V180RocketCargoData.language(chinese);
            assertEquals(36, entries.size());
            for (var role : RocketCargoRole.values()) { assertFalse(entries.get("block.advancedrocketrycommunity." + role.id()).isBlank()); }
            for (var status : RocketCargoStatus.values()) { assertFalse(entries.get(status.translationKey()).isBlank()); }
        }
    }

    @Test void originalPortArrowsDifferAndPngOutputIsReproducible() throws Exception {
        assertFalse(Arrays.equals(V180RocketCargoArt.grid(false), V180RocketCargoArt.grid(true)));
        var textures = V180RocketCargoArt.textures();
        assertEquals(2, textures.size());
        for (var entry : textures.entrySet()) {
            var image = ImageIO.read(new ByteArrayInputStream(entry.getValue()));
            assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
            assertArrayEquals(entry.getValue(), V180RocketCargoArt.textures().get(entry.getKey()));
        }
    }

    @Test void committedResourcesMatchGenerators() throws Exception {
        Path root = Path.of("src/generated/v1.8/resources");
        var jsons = new java.util.LinkedHashMap<>(V180RocketCargoData.clientFiles());
        jsons.putAll(V180RocketCargoData.serverFiles());
        for (var entry : jsons.entrySet()) {
            assertEquals(entry.getValue(), com.google.gson.JsonParser.parseString(Files.readString(root.resolve(entry.getKey()))));
        }
        for (var entry : V180RocketCargoArt.textures().entrySet()) {
            assertArrayEquals(entry.getValue(), Files.readAllBytes(root.resolve(entry.getKey())));
        }
        for (String locale : new String[] {"en_us", "zh_cn"}) {
            var language = com.google.gson.JsonParser.parseString(Files.readString(root.resolve(
                    "assets/advancedrocketrycommunity_v180/lang/" + locale + ".json"))).getAsJsonObject();
            V180RocketCargoData.language(locale.equals("zh_cn")).forEach((key, value) -> assertEquals(value, language.get(key).getAsString()));
        }
    }
}
