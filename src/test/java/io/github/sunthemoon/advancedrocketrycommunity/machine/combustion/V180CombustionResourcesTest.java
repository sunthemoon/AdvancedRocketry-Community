package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180CombustionArt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class V180CombustionResourcesTest {
    private static final Path ROOT = Path.of("src/generated/v1.8/resources");
    private static final String NS = "advancedrocketrycommunity";
    private static JsonObject json(String path) throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }

    @Test void allEightFacingAndLitVariantsUseCommunityTextures() throws IOException {
        JsonObject variants = json("assets/" + NS + "/blockstates/combustion_generator.json").getAsJsonObject("variants");
        assertEquals(8, variants.size());
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            for (boolean lit : new boolean[]{false, true}) {
                assertEquals(NS + ":block/combustion_generator" + (lit ? "_lit" : ""),
                        variants.getAsJsonObject("facing=" + facing + ",lit=" + lit).get("model").getAsString());
            }
        }
        for (String suffix : new String[]{"", "_lit"}) {
            var model = json("assets/" + NS + "/models/block/combustion_generator" + suffix + ".json");
            model.getAsJsonObject("textures").entrySet().forEach(entry ->
                    assertTrue(entry.getValue().getAsString().startsWith(NS + ":block/combustion_")));
        }
    }

    @Test void fuelGeneratorIsCraftableWithoutAnyPoweredMachine() throws IOException {
        var recipe = json("data/" + NS + "/recipes/combustion_generator.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(NS + ":combustion_generator", recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals("forge:ingots/iron", recipe.getAsJsonObject("key").getAsJsonObject("I").get("tag").getAsString());
        assertEquals(NS + ":copper_coil", recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        assertEquals("minecraft:crafting_shaped", json("data/" + NS + "/recipes/copper_coil.json").get("type").getAsString());
        assertEquals(NS + ":combustion_generator", json("data/" + NS + "/loot_tables/blocks/combustion_generator.json")
                .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("name").getAsString());
        for (String tag : new String[]{"mineable/pickaxe", "needs_stone_tool"}) {
            assertTrue(json("data/minecraft/tags/blocks/" + tag + ".json").getAsJsonArray("values").asList().stream()
                    .anyMatch(value -> value.getAsString().equals(NS + ":combustion_generator")));
        }
    }

    @Test void generatedArtIsDeterministicAndLanguageCoversEveryRefusal() throws IOException {
        for (String face : V180CombustionArt.FACES) {
            byte[] generated = Files.readAllBytes(ROOT.resolve("assets/" + NS + "/textures/block/combustion_" + face + ".png"));
            assertArrayEquals(V180CombustionArt.png(face), generated);
            var image = ImageIO.read(new ByteArrayInputStream(generated));
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
        }
        for (String language : new String[]{"en_us", "zh_cn"}) {
            var lang = json("assets/" + NS + "_v180/lang/" + language + ".json");
            assertTrue(lang.has("block." + NS + ".combustion_generator"));
            for (var status : CombustionBurn.Status.values()) {
                assertTrue(lang.has("screen." + NS + ".combustion.status."
                        + status.name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
    }
}
