package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class V180SealedRocketDataTest {
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String ID = "advancedrocketrycommunity:sealed_rocket_block";

    @Test void oneOpaqueDeterministicTextureAndOneModelCannotRevealTheOriginalMember() throws Exception {
        var files = files("clientFiles");
        assertEquals(Set.of(ASSETS + "blockstates/sealed_rocket_block.json", ASSETS + "models/block/sealed_rocket_block.json"), files.keySet());
        var model = files.get(ASSETS + "models/block/sealed_rocket_block.json");
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());
        assertEquals(Set.of("all"), model.getAsJsonObject("textures").keySet());
        assertEquals("advancedrocketrycommunity:block/sealed_rocket_block", model.getAsJsonObject("textures").get("all").getAsString());
        var bytes = (byte[]) type().getMethod("texture").invoke(null);
        assertArrayEquals(bytes, (byte[]) type().getMethod("texture").invoke(null));
        var png = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(16, png.getWidth()); assertEquals(16, png.getHeight());
        for (int y = 0; y < 16; y++) { for (int x = 0; x < 16; x++) { assertEquals(255, png.getRGB(x, y) >>> 24); } }
    }

    @Test void additiveImmunityTagsPreserveEveryEarlierEntryWithoutAnItemRecipeOrLoot() throws Exception {
        var files = files("serverFiles");
        assertEquals(Set.of("data/minecraft/tags/blocks/wither_immune.json", "data/minecraft/tags/blocks/dragon_immune.json",
                "data/advancedrocketrycommunity/tags/blocks/rocket_forbidden.json"), files.keySet());
        for (var entry : files.entrySet()) {
            String earlier = entry.getKey().contains("rocket_forbidden") ? "v0.5" : "v1.7";
            var old = JsonParser.parseString(Files.readString(Path.of("src/generated", earlier, "resources", entry.getKey())))
                    .getAsJsonObject().getAsJsonArray("values");
            var values = entry.getValue().getAsJsonArray("values");
            assertEquals(old.size() + 1, values.size());
            for (int i = 0; i < old.size(); i++) { assertEquals(old.get(i), values.get(i)); }
            assertEquals(ID, values.get(old.size()).getAsString());
            assertFalse(entry.getValue().get("replace").getAsBoolean());
        }
    }

    @Test void bothLocalesNameTheSameSealedBlockWithoutAnItemTranslation() throws Exception {
        var method = type().getMethod("language", boolean.class);
        assertEquals(Map.of("block.advancedrocketrycommunity.sealed_rocket_block", "Sealed Rocket Block"), method.invoke(null, false));
        assertEquals(Map.of("block.advancedrocketrycommunity.sealed_rocket_block", "火箭封存方块"), method.invoke(null, true));
    }

    private static Class<?> type() throws Exception { return Class.forName(V180SealedRocketDataTest.class.getPackageName() + ".V180SealedRocketData"); }
    @SuppressWarnings("unchecked") private static Map<String, JsonObject> files(String method) throws Exception {
        return (Map<String, JsonObject>) type().getMethod(method).invoke(null);
    }
}
