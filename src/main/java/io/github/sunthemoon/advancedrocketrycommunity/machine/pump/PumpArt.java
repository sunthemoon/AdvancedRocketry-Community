package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/** NEW opaque pixel art: cyan pressure gauge, pipe band and downward intake grille. */
public final class PumpArt {
    public static final List<String> FACES = List.of("side", "top", "bottom");
    public static int[] pixels(String face) {
        if (!FACES.contains(face)) { throw new IllegalArgumentException("Unknown pump face"); }
        int[] pixels = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = (x + y * 2) % 5 == 0 ? 0xFF405C63 : 0xFF354D55;
                if (x < 2 || x > 13 || y < 2 || y > 13) { color = 0xFF1C303A; }
                if (face.equals("side")) {
                    if (y >= 10 && y <= 12) { color = y == 10 ? 0xFFADB8B1 : 0xFF66847F; }
                    int dx = x - 8, dy = y - 6;
                    int r = dx * dx + dy * dy;
                    if (r <= 10) { color = r >= 5 ? 0xFF42CFC2 : 0xFF0E2832; }
                    if (x == 8 && y >= 4 && y <= 6) { color = 0xFFE6DEC1; }
                } else if (face.equals("top")) {
                    if (x >= 4 && x <= 11 && y >= 4 && y <= 11) { color = 0xFF162D37; }
                    if (x >= 5 && x <= 10 && y >= 5 && y <= 10) { color = (x + y) % 2 == 0 ? 0xFF62AEA7 : 0xFF446F71; }
                    if (y == 12 && x >= 4 && x <= 11) { color = 0xFFBAC3B9; }
                } else {
                    if (x >= 3 && x <= 12 && y >= 3 && y <= 12) { color = 0xFF0D232D; }
                    if (x >= 3 && x <= 12 && y >= 3 && y <= 12 && (x % 3 == 0 || y % 3 == 0)) { color = 0xFF97B9AD; }
                }
                if ((x == 2 || x == 13) && (y == 2 || y == 13)) { color = 0xFFBCD0C6; }
                pixels[y * 16 + x] = color;
            }
        }
        return pixels;
    }
    public static byte[] png(String face) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 16, 16, pixels(face), 0, 16);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("PNG writer missing"); }
            return bytes.toByteArray();
        } catch (IOException failed) { throw new IllegalStateException("Cannot encode pump art", failed); }
    }
    private PumpArt() { }
}
