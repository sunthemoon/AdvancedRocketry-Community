package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-063 sections 1, 3 and 9 (A0): the drawn art is valid, deterministic and matches the committed output. */
class V180MaterialArtTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static final Path TEXTURES = Path.of("src", "generated", "v1.8", "resources", "assets",
            "advancedrocketrycommunity", "textures");

    @Test
    void everyTemplateEncodesTheSamePngTwiceAndMatchesTheCommittedFile() throws IOException {
        Map<String, String[]> textures = V180MaterialArt.textures();
        assertEquals(19, textures.size());
        for (Map.Entry<String, String[]> texture : textures.entrySet()) {
            int tint = texture.getKey().startsWith("block/small_plate_press") ? 0xB4BECC : 0xFFFFFF;
            byte[] first = V180MaterialArt.png(texture.getValue(), tint);
            assertArrayEquals(first, V180MaterialArt.png(texture.getValue(), tint), texture.getKey());
            assertArrayEquals(first, Files.readAllBytes(TEXTURES.resolve(texture.getKey() + ".png")),
                    texture.getKey() + " differs from the committed DataGen output");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(first));
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            int[] expected = V180MaterialArt.pixels(texture.getValue(), tint);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int argb = image.getRGB(x, y);
                    int want = expected[y * 16 + x];
                    if ((want >>> 24) == 0) {
                        assertEquals(0, argb >>> 24, texture.getKey() + " pixel " + x + "," + y);
                    } else {
                        assertEquals(want, argb, texture.getKey() + " pixel " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void fullBlocksAreOpaqueAndItemsAndOverlaysAreCutOut() {
        for (Map.Entry<String, String[]> texture : V180MaterialArt.textures().entrySet()) {
            String name = texture.getKey();
            boolean transparent = String.join("", texture.getValue()).contains(".");
            boolean overlay = name.endsWith("ore_overlay");
            if (name.startsWith("block/") && !overlay) {
                assertTrue(!transparent, name + " is a full block face and must be opaque");
            } else {
                assertTrue(transparent, name + " should have a transparent outline");
            }
        }
    }

    @Test
    void malformedGridsAreRefused() {
        String[] shortGrid = new String[15];
        java.util.Arrays.fill(shortGrid, "0123456789012345");
        assertThrows(IllegalArgumentException.class, () -> V180MaterialArt.pixels(shortGrid, 0xFFFFFF));
        String[] badCharacter = new String[16];
        java.util.Arrays.fill(badCharacter, "0123456789012345");
        badCharacter[3] = "012345678901234x";
        assertThrows(IllegalArgumentException.class, () -> V180MaterialArt.pixels(badCharacter, 0xFFFFFF));
    }
}
