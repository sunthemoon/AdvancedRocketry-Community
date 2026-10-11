package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.*;

/** Original MIT pixel art, generated entirely from geometry/colors without bitmap inputs. */
public final class V180PropulsionArt implements DataProvider {
    private final PackOutput.PathProvider output;
    public V180PropulsionArt(PackOutput output) { this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures"); }
    public static byte[] png(String id, String face) {
        if (!V180PropulsionData.IDS.contains(id) || !java.util.List.of("side", "top", "bottom").contains(face)) {
            throw new IllegalArgumentException("Unknown propulsion art");
        }
        int accent = id.contains("nuclear") ? 0xFFDDB253 : id.contains("oxidizer") ? 0xFF7ECAB9
                : id.contains("bipropellant") ? 0xFFE78857 : 0xFF79AAC9;
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = 0xFF80919D;
                if (x % 4 == 0) { color = 0xFFB5C4CB; }
                if (x == 0 || x == 15 || y == 0 || y == 15) { color = 0xFF273844; }
                if (face.equals("side")) {
                    if (y == 3 || y == 12) { color = accent; }
                    if (x >= 6 && x <= 9 && y >= 5 && y <= 10) { color = 0xFF1A2B38; }
                    if (x == 7 && y >= 6 && y <= 9) { color = accent; }
                    if (id.contains("advanced") && (y == 5 || y == 10) && x >= 3 && x <= 4) { color = accent; }
                } else {
                    int r = (x - 7) * (x - 7) + (y - 7) * (y - 7);
                    if (r < 40) { color = accent; }
                    if (r < 25) { color = 0xFF314551; }
                    if (r < 10 && face.equals("bottom") && id.contains("motor")) { color = 0xFF111E27; }
                }
                image.setRGB(x, y, color);
            }
        }
        try (var bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("No PNG encoder"); }
            return bytes.toByteArray();
        } catch (IOException failed) { throw new IllegalStateException(failed); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        for (String id : V180PropulsionData.IDS) {
            for (String face : java.util.List.of("side", "top", "bottom")) {
                byte[] bytes = png(id, face); var path = output.file(ModIdentity.id("block/" + id + "_" + face), "png");
                writes.add(CompletableFuture.runAsync(() -> {
                    try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                    catch (IOException failed) { throw new IllegalStateException(failed); }
                }));
            }
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original propulsion art"; }
}
