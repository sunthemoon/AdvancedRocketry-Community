package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180ClosureArtTest {
    private static final Path TEXTURES = Path.of("src/generated/v1.8/resources/assets/advancedrocketrycommunity/textures");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void allSixteenImagesAreDeterministicAndMatchCommittedBytesAndPixels() throws IOException {
        List<String> textures = textures();
        assertEquals(16, textures.size());
        HashSet<String> distinct = new HashSet<>();
        for (String texture : textures) {
            byte[] png = png(texture);
            assertArrayEquals(png, png(texture), texture);
            assertArrayEquals(png, Files.readAllBytes(TEXTURES.resolve(texture + ".png")), texture);
            assertTrue(distinct.add(HexFormat.of().formatHex(png)), "duplicate image: " + texture);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            int width = suit(texture) ? V180SpaceSuitArt.width(texture) : 16;
            int height = suit(texture) ? V180SpaceSuitArt.height(texture) : 16;
            assertEquals(width, image.getWidth(), texture);
            assertEquals(height, image.getHeight(), texture);
            int[] expected = suit(texture) ? V180SpaceSuitArt.pixels(texture) : V180ClosureArt.pixels(texture);
            assertArrayEquals(expected, image.getRGB(0, 0, width, height, null, 0, width), texture);
            assertTrue(png.length < 8192, texture);
            assertFalse(Files.exists(TEXTURES.resolve(texture + ".png.mcmeta")), "static texture: " + texture);
        }
    }

    @Test void blockFacesAreOpaqueAndItemAndArmorImagesHaveRealCutouts() {
        for (String texture : textures()) {
            int[] pixels = suit(texture) ? V180SpaceSuitArt.pixels(texture) : V180ClosureArt.pixels(texture);
            boolean clear = false;
            boolean opaque = false;
            for (int pixel : pixels) {
                int alpha = pixel >>> 24;
                assertTrue(alpha == 0 || alpha == 255, texture);
                clear |= alpha == 0;
                opaque |= alpha == 255;
            }
            assertTrue(opaque, texture);
            assertEquals(!texture.startsWith("block/"), clear, texture);
        }
    }

    @Test void armorUsesTheHeadTorsoArmBootAndLegUvIslandsAndLeavesUnusedAreasClear() {
        int[] outer = V180SpaceSuitArt.pixels("models/armor/space_suit_layer_1");
        int[] inner = V180SpaceSuitArt.pixels("models/armor/space_suit_layer_2");
        assertEquals(0xFF234F67, outer[12 * 64 + 12]); // viewport on head front
        assertEquals(0xFFF0B55E, outer[24 * 64 + 26]); // torso oxygen gauge
        assertEquals(0xFF4DC3BB, outer[22 * 64 + 45]); // sleeve patch
        assertEquals(0xFF344957, outer[31 * 64 + 5]); // boot sole edge
        assertEquals(0xFF344957, outer[18 * 64 + 9]); // boot bottom cap
        assertEquals(0, outer[18 * 64 + 5]); // no floating cap at the hip when only boots are worn
        assertEquals(0, outer[24 * 64 + 5]); // no layer-1 trousers painted above the boot
        assertEquals(0xFF4DC3BB, inner[25 * 64 + 5]); // trouser knee
        assertEquals(0, inner[12 * 64 + 12]); // no inner helmet
        assertEquals(0, inner[24 * 64 + 23]); // no full torso painted on the trouser layer
        assertEquals(0, outer[31 * 64 + 63]);
        assertEquals(0, inner[31 * 64 + 63]);
    }

    @Test void unknownTextureNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> V180ClosureArt.png("block/unknown"));
        assertThrows(IllegalArgumentException.class, () -> V180SpaceSuitArt.png("item/unknown"));
        assertThrows(IllegalArgumentException.class, () -> V180SpaceSuitArt.width("../unknown"));
    }

    static List<String> textures() {
        return Stream.concat(V180ClosureArt.TEXTURES.stream(), V180SpaceSuitArt.TEXTURES.stream()).toList();
    }

    private static boolean suit(String texture) { return V180SpaceSuitArt.TEXTURES.contains(texture); }
    static byte[] png(String texture) { return suit(texture) ? V180SpaceSuitArt.png(texture) : V180ClosureArt.png(texture); }
}
