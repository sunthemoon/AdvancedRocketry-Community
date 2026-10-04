package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** NEW MIT community art, recorded in the tank task's ART-ORIGIN before generation. No bitmap inputs. */
public final class V180TankArt implements DataProvider {
    public static final List<String> FACES = List.of("side", "top", "bottom");
    private final PackOutput.PathProvider output;
    public V180TankArt(PackOutput output) { this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures"); }

    public static int[] pixels(String face) {
        if (!FACES.contains(face)) { throw new IllegalArgumentException("Unknown tank face"); }
        int[] pixels = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = x % 4 == 0 ? 0xFFADBCC0 : 0xFF83989E;
                if (x == 0 || x == 15 || y == 0 || y == 15) { color = 0xFF253840; }
                if (face.equals("side")) {
                    if (y == 3 || y == 12) { color = 0xFF3E535C; }
                    if ((x == 2 || x == 13) && (y == 3 || y == 12)) { color = 0xFFD2AA71; }
                    if (x >= 10 && x <= 12 && y >= 5 && y <= 10) {
                        color = x == 10 || x == 12 ? 0xFF344F58 : (y < 7 ? 0xFF162A34 : 0xFF62C2D4);
                    }
                } else {
                    if (x >= 3 && x <= 12 && y >= 3 && y <= 12) { color = 0xFF506A75; }
                    if (x >= 6 && x <= 9 && y >= 6 && y <= 9) { color = 0xFFC0925C; }
                    if (x >= 7 && x <= 8 && y >= 7 && y <= 8) { color = 0xFF172B32; }
                    if (face.equals("bottom") && (y == 4 || y == 11) && x >= 4 && x <= 11) { color = 0xFFD2AA71; }
                    if (face.equals("top") && x == 4 && y >= 4 && y <= 11) { color = 0xFF9ACED0; }
                }
                pixels[y * 16 + x] = color;
            }
        }
        return pixels;
    }

    public static byte[] png(String face) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 16, 16, pixels(face), 0, 16);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("PNG encoder unavailable"); }
            return bytes.toByteArray();
        } catch (IOException failed) { throw new IllegalStateException("Cannot encode original tank art", failed); }
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        for (String face : FACES) {
            byte[] bytes = png(face);
            var path = output.file(ModIdentity.id("block/pressurized_tank_" + face), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failed) { throw new IllegalStateException("Cannot write tank art", failed); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original pressurized tank art"; }
}
