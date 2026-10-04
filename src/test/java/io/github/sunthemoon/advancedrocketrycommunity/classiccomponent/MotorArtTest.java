package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MotorArtTest {
    @Test
    void everyOriginalFaceIsOpaqueAndExactlySixteenPixelsSquare() throws Exception {
        for (MotorDefinition tier : MotorDefinition.values()) {
            for (String face : MotorArt.FACES) {
                int[] pixels = MotorArt.pixels(tier, face);
                assertEquals(256, pixels.length);
                for (int pixel : pixels) { assertEquals(0xFF, pixel >>> 24); }
                var image = ImageIO.read(new ByteArrayInputStream(MotorArt.png(tier, face)));
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
                assertArrayEquals(pixels, image.getRGB(0, 0, 16, 16, null, 0, 16));
            }
        }
    }

    @Test
    void twelveFaceFilesAreDistinctAndByteDeterministic() throws Exception {
        var hashes = new HashSet<String>();
        for (MotorDefinition tier : MotorDefinition.values()) {
            for (String face : MotorArt.FACES) {
                byte[] png = MotorArt.png(tier, face);
                assertArrayEquals(png, MotorArt.png(tier, face));
                assertTrue(hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png))));
            }
        }
        assertEquals(12, hashes.size());
    }

    @Test
    void pixelQueriesReturnNewArraysAndTierAccentsAppearOnAllFaces() {
        int[] original = MotorArt.pixels(MotorDefinition.MOTOR, "side");
        original[0] = 0;
        assertEquals(0xFF243540, MotorArt.pixels(MotorDefinition.MOTOR, "side")[0]);
        for (MotorDefinition tier : MotorDefinition.values()) {
            for (String face : MotorArt.FACES) {
                boolean found = false;
                for (int pixel : MotorArt.pixels(tier, face)) { found |= pixel == tier.accent(); }
                assertTrue(found, tier.id() + "/" + face);
            }
        }
    }

    @Test
    void unknownFacesAndMissingTiersCannotGenerateAnAsset() {
        assertThrows(IllegalArgumentException.class, () -> MotorArt.png(MotorDefinition.MOTOR, "front"));
        assertThrows(IllegalArgumentException.class, () -> MotorArt.png(null, "side"));
    }
}
