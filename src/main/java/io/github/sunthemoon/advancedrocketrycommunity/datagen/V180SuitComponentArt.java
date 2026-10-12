package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * NEW MIT community art for the worn suit components (ADR-066 section 7.1), registered in
 * {@code docs/provenance/v1.8.0-c18d-suit-components-preauthoring.md} before authoring. One 32 x 32 sheet drawn pixel
 * by pixel, no bitmap input. The regions follow the box UV layout of {@code SuitComponentLayer}; metal and glass are
 * neutral so the renderer can tint tanks by gas and the visor by type.
 */
public final class V180SuitComponentArt implements DataProvider {
    public static final int SIZE = 32;
    private static final int CLEAR = 0x00000000;
    private final PackOutput.PathProvider output;

    public V180SuitComponentArt(PackOutput output) {
        this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    public static int[] pixels() {
        int[] pixels = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                pixels[y * SIZE + x] = pixel(x, y);
            }
        }
        return pixels;
    }

    private static int pixel(int x, int y) {
        if (x < 20 && y < 12) {
            return frame(x, y); // jetpack frame, an 8 x 10 x 2 box at (0, 0)
        }
        if (x < 12 && y >= 12 && y < 24) {
            return metal(x, y - 12, 3); // jetpack tank, a 3 x 9 x 3 box at (0, 12)
        }
        if (x >= 12 && x < 20 && y >= 12 && y < 16) {
            return (x + y) % 3 == 0 ? 0xFF4A525A : 0xFF23282D; // nozzle, a 2 x 2 x 2 box at (12, 12)
        }
        if (x >= 20 && y < 11) {
            return metal(x - 20, y, 3); // module tank, a 3 x 8 x 3 box at (20, 0)
        }
        if (x < 18 && y >= 24 && y < 28) {
            return y == 24 || y == 25 && x % 5 == 1 ? 0xFFF4FAFF : 0xFFC2DCEE; // visor glass, 8 x 3 x 1 at (0, 24)
        }
        return CLEAR;
    }

    private static int frame(int x, int y) {
        boolean edge = y == 2 || y == 11 || x == 0 || x == 19 || x == 2 || x == 10 || x == 12;
        boolean rivet = y >= 3 && (y - 3) % 4 == 0 && (x == 4 || x == 8 || x == 14 || x == 18);
        if (rivet) {
            return 0xFF9AA8B4;
        }
        return edge ? 0xFF262E36 : (y < 2 ? 0xFF46525E : 0xFF38434E);
    }

    /** Light brushed metal with a highlight on the front face and two dark straps. */
    private static int metal(int x, int y, int depth) {
        if (y < depth) {
            return (x + y) % 2 == 0 ? 0xFFB4BEC6 : 0xFFA6B0B8; // caps
        }
        int row = y - depth;
        if (row == 1 || row == 6) {
            return 0xFF5A646C; // straps
        }
        int column = x % (depth * 2);
        if (column == depth || column == depth + 1) {
            return 0xFFF2F6F8; // highlight
        }
        return (row + x) % 4 == 0 ? 0xFFC4CCD2 : 0xFFD6DDE2;
    }

    public static byte[] png() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, SIZE, SIZE, pixels(), 0, SIZE);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) {
                throw new IllegalStateException("PNG encoder unavailable");
            }
            return bytes.toByteArray();
        } catch (IOException failed) {
            throw new IllegalStateException("Cannot encode original suit component art", failed);
        }
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        byte[] bytes = png();
        return CompletableFuture.runAsync(() -> {
            try {
                cache.writeIfNeeded(output.file(ModIdentity.id("entity/suit_components"), "png"), bytes,
                        Hashing.sha1().hashBytes(bytes));
            } catch (IOException failed) {
                throw new IllegalStateException("Cannot write suit component art", failed);
            }
        });
    }

    @Override
    public String getName() {
        return "Advanced Rocketry Community v1.8 suit component art";
    }
}
