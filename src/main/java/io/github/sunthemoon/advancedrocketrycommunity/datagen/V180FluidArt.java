package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluidDefinition;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original fluid ripples, reinforced pails and a nitrogen capsule; no bitmap inputs. */
public final class V180FluidArt implements DataProvider {
    private final PackOutput.PathProvider output;

    public V180FluidArt(PackOutput output) {
        this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    public static List<String> textures() {
        List<String> names = new ArrayList<>();
        for (var fluid : ClassicFluidDefinition.values()) {
            names.add("block/fluid/" + fluid.id() + "_still");
            names.add("block/fluid/" + fluid.id() + "_flow");
        }
        names.add("item/rocket_fuel_bucket");
        names.add("item/enriched_lava_bucket");
        names.add("item/nitrogen_canister");
        return List.copyOf(names);
    }

    public static byte[] png(String name) {
        if (!textures().contains(name)) { throw new IllegalArgumentException("Unknown fluid texture: " + name); }
        boolean flow = name.endsWith("_flow");
        int size = flow ? 32 : 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        boolean lava = name.contains("enriched_lava");
        boolean item = name.startsWith("item/");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int color;
                if (!item) {
                    // Neutral ripples accept the audited gas/fuel tint; lava has emissive mineral seams.
                    int wave = Math.floorMod(x * 3 + y * 2 + (y / 3) * 5, 19);
                    color = lava ? (wave < 3 ? 0xFFFFCE6A : wave < 8 ? 0xFFCC572C : 0xFF823A2F)
                            : wave < 4 ? 0xFFE8ECE5 : wave < 9 ? 0xFFB6C4BC : 0xFF839D91;
                } else if (name.endsWith("nitrogen_canister")) {
                    boolean body = x >= 4 && x <= 11 && y >= 4 && y <= 13;
                    boolean valve = x >= 6 && x <= 9 && y >= 1 && y <= 3;
                    color = body ? (x == 4 || x == 11 || y == 13 ? 0xFF354951 : 0xFFBDC6D9)
                            : valve ? 0xFF6C8189 : 0;
                    if (body && y >= 7 && y <= 9) { color = 0xFF526B92; }
                    if (x == 8 && (y == 7 || y == 9) || x == 7 && y == 8) { color = 0xFFEAF0FF; }
                } else {
                    boolean rim = y == 4 && x >= 2 && x <= 13;
                    boolean body = y >= 5 && y <= 13 && x >= 3 + (y - 5) / 4 && x <= 12 - (y - 5) / 4;
                    boolean handle = y == 1 && x >= 5 && x <= 10 || y >= 2 && y <= 3 && (x == 4 || x == 11);
                    color = rim ? 0xFFB6BEB4 : body ? ((x + y) % 3 == 0 ? 0xFF72898C : 0xFF4C656F)
                            : handle ? 0xFFCBD2C7 : 0;
                    if (body && y == 6 && x >= 5 && x <= 10) { color = lava ? 0xFFEC8E37 : 0xFFE5D884; }
                    if (body && y >= 9 && y <= 10 && x >= 6 && x <= 9) { color = lava ? 0xFFD26336 : 0xFFBBAE55; }
                }
                image.setRGB(x, y, color);
            }
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("PNG encoder unavailable"); }
            return bytes.toByteArray();
        } catch (IOException failed) { throw new IllegalStateException("Cannot encode fluid texture", failed); }
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (String name : textures()) {
            byte[] bytes = png(name);
            Path path = output.file(ModIdentity.id(name), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failed) { throw new IllegalStateException("Cannot write " + path, failed); }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 original fluid art"; }
}
