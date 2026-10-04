package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/** NEW community pixel design: offset winding slots, shaft end and mounting feet. */
public final class MotorArt {
    public static final List<String> FACES = List.of("side", "top", "bottom");
    private MotorArt() { }

    public static int[] pixels(MotorDefinition tier, String face) {
        if (tier == null || !FACES.contains(face)) { throw new IllegalArgumentException("Unknown motor texture"); }
        int[] pixels = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = (x * 3 + y * 5) % 7 == 0 ? 0xFF4D6571 : 0xFF425863;
                if (x < 2 || x > 13 || y < 2 || y > 13) { color = 0xFF243540; }
                if (face.equals("side")) {
                    boolean winding = x >= 3 && x <= 11 && y >= 4 && y <= 10;
                    if (winding) { color = (x + y) % 3 == 0 ? tier.accent() : 0xFF192A36; }
                    if (y == 12 && x >= 4 && x <= 10) { color = 0xFF9CB0BA; }
                    if (x == 12 && y >= 3 && y <= 3 + tier.ordinal()) { color = tier.accent(); }
                } else if (face.equals("top")) {
                    int dx = x - 7;
                    int dy = y - 8;
                    int radius = dx * dx + dy * dy;
                    if (radius >= 13 && radius <= 30) { color = tier.accent(); }
                    if (radius < 13) { color = 0xFF1C2E39; }
                    if (x >= 6 && x <= 8 && y >= 7 && y <= 9) { color = 0xFFB2C0C7; }
                    if (x == 8 && y == 7) { color = 0xFF718B9B; }
                    if (x == 12 && y >= 3 && y <= 3 + tier.ordinal()) { color = tier.accent(); }
                } else {
                    if (x >= 3 && x <= 11 && y >= 4 && y <= 11) { color = 0xFF1A2C38; }
                    if ((x == 4 || x == 10) && y >= 3 && y <= 12) { color = 0xFF8CA2AC; }
                    if (x >= 6 && x <= 8 && y == 9 - tier.ordinal()) { color = tier.accent(); }
                    if (x == 11 && y == 11) { color = tier.accent(); }
                }
                if ((x == 2 && y == 3) || (x == 13 && y == 12)) { color = 0xFF829CAA; }
                pixels[y * 16 + x] = color;
            }
        }
        return pixels;
    }

    public static byte[] png(MotorDefinition tier, String face) {
        int[] pixels = pixels(tier, face);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 16, 16, pixels, 0, 16);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("PNG encoder unavailable"); }
            return bytes.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("Cannot encode community motor texture", exception); }
    }
}
