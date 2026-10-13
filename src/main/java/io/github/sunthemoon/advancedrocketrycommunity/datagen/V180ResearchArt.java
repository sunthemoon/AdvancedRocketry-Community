package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.*;

/** Original MIT community pixel art: brass telescope reticle and teal data tracks. */
public final class V180ResearchArt implements DataProvider {
    private final PackOutput.PathProvider output;
    public V180ResearchArt(PackOutput output) { this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures"); }
    public static byte[] png(String id) {
        if (!V180ResearchData.IDS.contains(id)) { throw new IllegalArgumentException("Research art identity"); }
        int[] pixels = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int dx = 2 * x - 15, dy = 2 * y - 15;
                int color = x < 2 || x > 13 || y < 2 || y > 13 ? 0xFF344A50 : 0xFF152E36;
                if (id.equals("observatory")) {
                    int radius = dx * dx + dy * dy;
                    if (radius >= 50 && radius <= 110 || (Math.abs(dx) <= 1 || Math.abs(dy) <= 1) && radius < 110) { color = 0xFFE7BE73; }
                    if (x == 11 && y == 4 || x == 4 && y == 10) { color = 0xFFECEBDC; }
                } else {
                    if ((y == 4 || y == 7 || y == 10) && x >= 4 && x <= 11) { color = 0xFF78C8BC; }
                    if ((x == 4 || x == 11) && y >= 3 && y <= 12) { color = 0xFFDBB375; }
                }
                pixels[y * 16 + x] = color;
            }
        }
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); image.setRGB(0, 0, 16, 16, pixels, 0, 16);
        try (var bytes = new ByteArrayOutputStream()) { ImageIO.write(image, "PNG", bytes); return bytes.toByteArray(); }
        catch (IOException failure) { throw new IllegalStateException("Research art encoding", failure); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        for (String id : V180ResearchData.IDS) {
            byte[] bytes = png(id); var path = output.file(ModIdentity.id("block/" + id), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failure) { throw new IllegalStateException("Research art write", failure); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original ground research art"; }
}
