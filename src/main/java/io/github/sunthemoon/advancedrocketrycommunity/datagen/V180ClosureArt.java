package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** NEW MIT N2 art: integer geometry, fixed seed, no bitmap input. See the W49 pre-authoring record. */
public final class V180ClosureArt implements DataProvider {
    public static final List<String> TEXTURES = List.of(
            "block/orbital_laser_bottom", "block/orbital_laser_front", "block/electrolyzer_front",
            "block/electrolyzer_front_active", "block/precision_assembler_front", "block/railgun_front",
            "block/satellite_builder_front", "block/warp_core_side",
            "item/satellite_solar_module", "item/advanced_solar_panel");
    private static final int DARK = 0xFF182F3B;
    private static final int EDGE = 0xFF344D5A;
    private static final int LIGHT = 0xFFE5DBC2;
    private static final int TEAL = 0xFF4DC3BB;
    private static final int GOLD = 0xFFF0B55E;
    private final PackOutput.PathProvider output;

    public V180ClosureArt(PackOutput output) {
        this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    public static int[] pixels(String texture) {
        if (!TEXTURES.contains(texture)) {
            throw new IllegalArgumentException("Unknown N2 texture: " + texture);
        }
        int[] pixels = new int[256];
        if (texture.startsWith("item/")) {
            solar(pixels, texture.endsWith("advanced_solar_panel"));
            return pixels;
        }
        SplittableRandom grain = new SplittableRandom(0x49C18D02L ^ texture.hashCode());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // Broad staggered plates, not a sampled casing or a native machine silhouette.
                int color = grain.nextInt(4) == 0 ? 0xFF738C91 : 0xFF809A9B;
                if (x == 0 || y == 0 || x == 15 || y == 15) { color = EDGE; }
                if (x == 1 && y > 1 && y < 14 || y == 13 && x > 11) { color = LIGHT; }
                pixels[y * 16 + x] = color;
            }
        }
        switch (texture) {
            case "block/orbital_laser_bottom" -> {
                for (int y = 2; y < 14; y++) {
                    for (int x = 2; x < 14; x++) {
                        int radius = StrictMath.abs(2 * x - 15) + StrictMath.abs(2 * y - 15);
                        pixels[y * 16 + x] = radius < 8 ? DARK : radius < 13 ? TEAL : EDGE;
                    }
                }
                rect(pixels, 7, 6, 2, 4, GOLD);
                rect(pixels, 3, 3, 3, 1, LIGHT);
                rect(pixels, 11, 11, 2, 2, GOLD);
            }
            case "block/orbital_laser_front" -> {
                rect(pixels, 3, 3, 8, 7, DARK);
                rect(pixels, 4, 4, 2, 5, TEAL);
                rect(pixels, 7, 6, 3, 2, GOLD);
                for (int y = 3; y < 12; y += 2) { rect(pixels, 12, y, 2, 1, DARK); }
                rect(pixels, 4, 12, 6, 1, LIGHT);
            }
            case "block/electrolyzer_front", "block/electrolyzer_front_active" -> {
                boolean active = texture.endsWith("_active");
                rect(pixels, 3, 3, 4, 9, DARK);
                rect(pixels, 9, 2, 4, 10, DARK);
                rect(pixels, 4, 5, 2, 6, active ? TEAL : 0xFF426F78);
                rect(pixels, 10, 4, 2, 7, active ? GOLD : 0xFF8F8064);
                rect(pixels, 6, 12, 5, 1, LIGHT);
                rect(pixels, 7, 10, 2, 4, EDGE);
                rect(pixels, 12, 13, 2, 1, active ? TEAL : DARK);
                if (active) {
                    rect(pixels, 4, 6, 1, 1, LIGHT);
                    rect(pixels, 11, 8, 1, 1, LIGHT);
                }
            }
            case "block/precision_assembler_front" -> {
                rect(pixels, 3, 3, 10, 9, DARK);
                rect(pixels, 4, 4, 8, 1, LIGHT);
                rect(pixels, 5, 5, 1, 4, TEAL);
                rect(pixels, 10, 5, 1, 3, TEAL);
                rect(pixels, 6, 8, 2, 1, LIGHT);
                rect(pixels, 9, 7, 2, 1, LIGHT);
                rect(pixels, 7, 9, 3, 2, GOLD);
                rect(pixels, 3, 12, 10, 1, EDGE);
            }
            case "block/railgun_front" -> {
                rect(pixels, 6, 4, 5, 8, DARK);
                for (int y = 3; y < 13; y++) {
                    int x = 2 + (y - 3) / 3;
                    rect(pixels, x, y, 1, 1, TEAL);
                    rect(pixels, 13 - (y - 3) / 3, y, 1, 1, GOLD);
                }
                rect(pixels, 7, 9, 3, 2, LIGHT);
                rect(pixels, 7, 12, 2, 1, GOLD);
            }
            case "block/satellite_builder_front" -> {
                rect(pixels, 3, 3, 10, 10, DARK);
                rect(pixels, 7, 4, 3, 7, LIGHT);
                rect(pixels, 4, 6, 2, 4, TEAL);
                rect(pixels, 11, 5, 1, 5, TEAL);
                rect(pixels, 6, 7, 5, 1, GOLD);
                rect(pixels, 8, 11, 1, 2, GOLD);
            }
            case "block/warp_core_side" -> {
                for (int y = 2; y < 13; y++) {
                    for (int x = 2; x < 13; x++) {
                        int distance = StrictMath.abs(x - 7) + StrictMath.abs(y - 7);
                        if (distance <= 5) { pixels[y * 16 + x] = distance >= 4 ? TEAL : DARK; }
                    }
                }
                rect(pixels, 7, 6, 2, 3, GOLD);
                rect(pixels, 11, 12, 3, 1, LIGHT);
                rect(pixels, 13, 10, 1, 3, GOLD);
            }
            default -> throw new IllegalArgumentException(texture);
        }
        return pixels;
    }

    private static void solar(int[] pixels, boolean advanced) {
        rect(pixels, advanced ? 7 : 10, 4, 2, 9, LIGHT);
        rect(pixels, advanced ? 7 : 10, 12, 3, 2, GOLD);
        for (int y = 2; y < 12; y++) {
            for (int x = 1; x < (advanced ? 7 : 10); x++) {
                boolean edge = x == 1 || y == 2 || y == 11 || x == (advanced ? 6 : 9);
                pixels[y * 16 + x] = edge ? EDGE : (x + 2 * y) % 5 == 0 ? TEAL : 0xFF285B87;
            }
        }
        if (advanced) {
            for (int y = 4; y < 14; y++) {
                for (int x = 10; x < 15; x++) {
                    boolean edge = x == 10 || x == 14 || y == 4 || y == 13;
                    pixels[y * 16 + x] = edge ? EDGE : (2 * x + y) % 4 == 0 ? GOLD : 0xFF366FA0;
                }
            }
        }
    }

    private static void rect(int[] pixels, int x, int y, int width, int height, int color) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) { pixels[row * 16 + column] = color; }
        }
    }

    static byte[] encode(int width, int height, int[] pixels) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("No PNG writer"); }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    public static byte[] png(String texture) { return encode(16, 16, pixels(texture)); }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (String texture : TEXTURES) {
            byte[] bytes = png(texture);
            var path = output.file(ModIdentity.id(texture), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 NEW closure machine and solar art"; }
}
