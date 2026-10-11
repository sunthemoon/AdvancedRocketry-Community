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

/** NEW MIT community art, drawn from integer geometry without any source bitmaps. */
public final class V180ClassicMachineArt implements DataProvider {
    private final PackOutput.PathProvider output;
    public V180ClassicMachineArt(PackOutput output) { this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures"); }
    public static int[] pixels(String block) {
        if (!V180ClassicMachineData.BLOCKS.contains(block)) { throw new IllegalArgumentException("Unknown classic texture"); }
        int[] pixels = new int[256];
        int accent = block.startsWith("fluid") ? 0xFF4DB5C6 : block.startsWith("item") ? 0xFFD3A65E
                : block.equals("lathe") ? 0xFF5CBA94 : 0xFFE7D265;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = ((x + y) % 5 == 0) ? 0xFF7B9190 : 0xFF95A6A0;
                if (x < 2 || x > 13 || y < 2 || y > 13) { color = 0xFF2C4348; }
                if ((x == 2 || x == 13) && (y == 2 || y == 13)) { color = 0xFFC6D4CE; }
                if (x >= 4 && x <= 11 && y >= 4 && y <= 11) { color = 0xFF162F38; }
                int arrowX = block.contains("output") ? 15 - x : x;
                if (y == 7 && arrowX >= 5 && arrowX <= 10 || arrowX == 9 && (y == 6 || y == 8)
                        || arrowX == 8 && (y == 5 || y == 9)) { color = accent; }
                if (block.equals("lathe") && x >= 5 && x <= 10 && (y == 5 || y == 10)) { color = accent; }
                if (block.equals("power_input_plug") && x == 7 && y >= 4 && y <= 11) { color = accent; }
                pixels[y * 16 + x] = color;
            }
        }
        return pixels;
    }
    public static byte[] png(String block) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); image.setRGB(0, 0, 16, 16, pixels(block), 0, 16);
        try (var bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("No PNG writer"); } return bytes.toByteArray();
        } catch (IOException failed) { throw new IllegalStateException("Classic art encoding failed", failed); }
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        for (String block : V180ClassicMachineData.BLOCKS) {
            byte[] bytes = png(block); var path = output.file(ModIdentity.id("block/" + block), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failed) { throw new IllegalStateException("Classic art write failed", failed); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original hatch and lathe art"; }
}
