package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180PumpLanguage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PumpResourcesTest {
    @Test void newRecipeHasExactMultisetAndUsesBasicMotorOnly() {
        var files = PumpDataFiles.server();
        var recipe = files.get("data/advancedrocketrycommunity/recipes/pump.json");
        Map<String, Integer> actual = new HashMap<>();
        for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
            var object = ingredient.getAsJsonObject(); String key = object.has("item") ? "item" : "tag";
            actual.merge(key + ":" + object.get(key).getAsString(), 1, Integer::sum);
        }
        assertEquals(Map.of("item:advancedrocketrycommunity:machine_casing", 1, "item:advancedrocketrycommunity:motor", 1,
                "item:minecraft:bucket", 1, "tag:forge:plates/steel", 2, "tag:forge:rods/iron", 2, "item:minecraft:redstone", 1), actual);
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(3, files.size()); assertEquals(files, PumpDataFiles.server());
        assertEquals(3, PumpDataFiles.client().size());
        assertTrue(files.keySet().stream().noneMatch(path -> path.contains("tags/blocks/mineable")));
    }
    @Test void originalTexturesAreOpaqueDistinctAndDeterministic() throws Exception {
        Map<String, byte[]> bytes = new HashMap<>();
        for (String face : PumpArt.FACES) {
            assertArrayEquals(PumpArt.png(face), PumpArt.png(face));
            bytes.put(face, PumpArt.png(face));
            var image = ImageIO.read(new ByteArrayInputStream(bytes.get(face)));
            assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
            for (int pixel : PumpArt.pixels(face)) { assertEquals(255, pixel >>> 24); }
        }
        assertFalse(java.util.Arrays.equals(bytes.get("side"), bytes.get("top")));
        assertFalse(java.util.Arrays.equals(bytes.get("bottom"), bytes.get("top")));
        assertThrows(IllegalArgumentException.class, () -> PumpArt.png("unknown"));
    }
    @Test void everyStableStatusHasBothLabelsWithMatchingKeySet() {
        var english = V180PumpLanguage.translations(false);
        var chinese = V180PumpLanguage.translations(true);
        assertEquals(english.keySet(), chinese.keySet()); assertEquals(PumpCode.values().length + 2, english.size());
        for (String key : english.keySet()) { assertFalse(english.get(key).isBlank()); assertFalse(chinese.get(key).isBlank()); }
    }
}
