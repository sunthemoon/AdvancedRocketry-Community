package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** NEW MIT community art: ceramic cups and distinct instrument panels drawn from geometry. */
public final class V180C16cMachineArt implements DataProvider {
    private final PackOutput.PathProvider output;
    public V180C16cMachineArt(PackOutput output) { this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures"); }
    public static int[] pixels(String id) {
        if (!V180C16cMachineData.BLOCKS.contains(id) && !id.equals("quartz_crucible_shell")) { throw new IllegalArgumentException("Unknown W10 art"); }
        var pixels = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = x < 2 || x > 13 || y < 2 || y > 13 ? 0xFF30484F : 0xFF849D9C;
                if (id.startsWith("quartz_crucible")) {
                    color = id.endsWith("shell") ? 0 : 0xFFE7E1D1;
                    if (x >= 2 && x <= 13 && y >= 3 && y <= 13 && (x >= 4 && x <= 11 || y <= 5)) {
                        color = x == 4 || x == 11 || y == 13 ? 0xFFB8B4A9 : 0xFFF6F1E4;
                        if (y >= 4 && y <= 5 && x >= 4 && x <= 11) { color = 0xFF635E56; }
                    }
                } else if (x >= 3 && x <= 12 && y >= 3 && y <= 12) {
                    color = 0xFF182C35;
                    int dx = 2 * x - 15, dy = 2 * y - 15;
                    if (id.equals("crystallizer") && Math.abs(dx) + Math.abs(dy) <= 10) { color = (x + y) % 3 == 0 ? 0xFFEFDDCA : 0xFF67CBBF; }
                    if (id.equals("chemical_reactor") && (x == 5 || x == 10 || y == 9 && x >= 5 && x <= 10)) { color = 0xFFF4B561; }
                    if (id.equals("precision_laser_etcher") && (y == 7 || x == 9 && y >= 5 && y <= 10)) { color = x == 9 ? 0xFFF2DC68 : 0xFFDB7564; }
                    if (id.equals("centrifuge") && (dx * dx + dy * dy <= 80 && dx * dx + dy * dy >= 36 || Math.abs(dx) <= 1 || Math.abs(dy) <= 1)) { color = 0xFF86C3DA; }
                }
                pixels[y * 16 + x] = color;
            }
        }
        return pixels;
    }
    public static byte[] png(String id) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); image.setRGB(0, 0, 16, 16, pixels(id), 0, 16);
        try (var bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("PNG encoder unavailable"); }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("W10 art encoding failed", failure); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>(); var ids = new ArrayList<>(V180C16cMachineData.BLOCKS); ids.add("quartz_crucible_shell");
        for (String id : ids) {
            byte[] bytes = png(id); var path = output.file(ModIdentity.id((id.endsWith("shell") ? "item/" : "block/") + id), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failure) { throw new IllegalStateException("W10 art write failed", failure); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original C16c machine art"; }
}
