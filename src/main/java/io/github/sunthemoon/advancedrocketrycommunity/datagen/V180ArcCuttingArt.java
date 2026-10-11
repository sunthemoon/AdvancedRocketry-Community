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

/** NEW MIT community art drawn from geometry, without imported textures or models. */
public final class V180ArcCuttingArt implements DataProvider {
    private final PackOutput.PathProvider output;
    public V180ArcCuttingArt(PackOutput output) { this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures"); }
    public static int[] pixels(String id) {
        if (!V180ArcCuttingData.BLOCKS.contains(id) && !id.equals("iron_saw_blade")) { throw new IllegalArgumentException("Unknown W05 art"); }
        int[] pixels = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = 0xFF879A98;
                if (id.equals("blast_brick") || id.equals("arc_furnace")) {
                    int row = y / 4, seam = (x + (row % 2) * 4) % 8;
                    color = y % 4 == 0 || seam == 0 ? 0xFF403C35 : ((x * 3 + y) % 7 == 0 ? 0xFF807461 : 0xFF9B8B70);
                    if (id.equals("arc_furnace") && x >= 3 && x <= 12 && y >= 3 && y <= 12) {
                        color = x == 3 || x == 12 || y == 3 || y == 12 ? 0xFF24383E : 0xFF12232B;
                        if ((x == 5 || x == 10) && y >= 5 && y <= 10) { color = 0xFFF6C559; }
                        if (y == 8 && x >= 6 && x <= 9) { color = 0xFFE88C46; }
                    }
                } else {
                    if (x < 2 || x > 13 || y < 2 || y > 13) { color = 0xFF30484F; }
                    int dx = x * 2 - 15, dy = y * 2 - 15, radius = dx * dx + dy * dy;
                    if (id.equals("iron_saw_blade")) { color = 0; }
                    if (id.equals("saw_blade") || id.equals("iron_saw_blade")) {
                        if (radius <= 120 || radius <= 185 && (x + y) % 3 == 0) { color = (x + y) % 4 == 0 ? 0xFFA7BBC1 : 0xFFD5DEDA; }
                        if (radius <= 14) { color = id.equals("iron_saw_blade") ? 0 : 0xFF30484F; }
                    } else {
                        if (x >= 3 && x <= 12 && y >= 4 && y <= 11) { color = 0xFF162C34; }
                        if (y == 8 && x >= 4 && x <= 11) { color = 0xFF70CFB7; }
                        if (x == 8 && y >= 5 && y <= 10) { color = 0xFFD5DEDA; }
                    }
                }
                pixels[y * 16 + x] = color;
            }
        }
        return pixels;
    }
    public static byte[] png(String id) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); image.setRGB(0, 0, 16, 16, pixels(id), 0, 16);
        try (var bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("PNG encoder unavailable"); } return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("W05 art encoding failed", failure); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>(); var ids = new ArrayList<>(V180ArcCuttingData.BLOCKS); ids.add("iron_saw_blade");
        for (String id : ids) {
            byte[] bytes = png(id); var path = output.file(ModIdentity.id((id.equals("iron_saw_blade") ? "item/" : "block/") + id), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failure) { throw new IllegalStateException("W05 art write failed", failure); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original arc furnace and saw art"; }
}
