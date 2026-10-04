package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180TankArt;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TankDataTest {
    @Test void generatedBlueprintsAreDeterministicAndReferencesUseOriginalFaces() {
        assertEquals(TankDataFiles.client(), TankDataFiles.client());
        assertEquals(TankDataFiles.server(), TankDataFiles.server());
        var model = TankDataFiles.client().get("assets/advancedrocketrycommunity/models/block/pressurized_tank.json");
        assertEquals("minecraft:block/cube_bottom_top", model.get("parent").getAsString());
        for (String face : V180TankArt.FACES) {
            assertEquals("advancedrocketrycommunity:block/pressurized_tank_" + face,
                    model.getAsJsonObject("textures").get(face).getAsString());
        }
        var recipe = TankDataFiles.server().get("data/advancedrocketrycommunity/recipes/pressurized_tank.json");
        assertEquals(TankDataFiles.ID, recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals("forge:plates/steel", recipe.getAsJsonObject("key").getAsJsonObject("P").get("tag").getAsString());
        assertEquals("minecraft:bucket", recipe.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString());
        assertTrue(Files.exists(Path.of("src/generated/v1.8/resources/data/forge/tags/items/plates/steel.json")));
    }
    @Test void originalFacesHaveBoundedOpaqueGeometryAndDeterministicDistinctPngBytes() throws Exception {
        var hashes = new HashSet<String>();
        for (String face : V180TankArt.FACES) {
            byte[] png = V180TankArt.png(face); assertArrayEquals(png, V180TankArt.png(face));
            assertTrue(hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png))));
            var image = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
            int[] pixels = V180TankArt.pixels(face);
            assertArrayEquals(pixels, image.getRGB(0, 0, 16, 16, null, 0, 16));
            for (int pixel : pixels) { assertEquals(255, pixel >>> 24); }
        }
        assertThrows(IllegalArgumentException.class, () -> V180TankArt.png("front"));
    }
}
