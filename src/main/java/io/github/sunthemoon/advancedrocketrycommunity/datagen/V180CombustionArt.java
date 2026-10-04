package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
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

/** NEW community art: riveted steel, copper vents and a square combustion chamber. No bitmap inputs. */
public final class V180CombustionArt implements DataProvider {
    public static final List<String> FACES = List.of("side", "top", "front", "front_lit");
    private final PackOutput.PathProvider output;

    public V180CombustionArt(PackOutput output) {
        this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    public static byte[] png(String face) {
        if (!FACES.contains(face)) {
            throw new IllegalArgumentException("Unknown combustion texture");
        }
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = ((x + y) % 3 == 0) ? 0xFF46585D : 0xFF3C4C52;
                if (x == 0 || y == 0 || x == 15 || y == 15) {
                    color = 0xFF202C31;
                }
                if ((x == 2 || x == 13) && (y == 2 || y == 13)) {
                    color = 0xFFC79861;
                }
                if (face.equals("side") && x >= 4 && x <= 11 && y >= 4 && y <= 11) {
                    color = y % 3 == 1 ? 0xFF0E171C : 0xFF97714E;
                }
                if (face.equals("top") && x >= 5 && x <= 10 && y >= 3 && y <= 12) {
                    color = x % 2 == 0 ? 0xFF121E24 : 0xFF8A9A9C;
                }
                if (face.startsWith("front") && x >= 3 && x <= 12 && y >= 5 && y <= 12) {
                    boolean frame = x == 3 || x == 12 || y == 5 || y == 12;
                    color = frame ? 0xFFAF7A4B : 0xFF101B20;
                    if (!frame && face.equals("front_lit")) {
                        // A narrow asymmetric flame, not an evenly luminous square face.
                        boolean flame = (y == 7 && x == 7) || (y == 8 && (x == 6 || x == 7))
                                || (y == 9 && x >= 6 && x <= 9) || (y >= 10 && x >= 5 && x <= 10);
                        if (flame) {
                            color = x == 7 && y >= 9 ? 0xFFFFD46A : ((x + y) % 2 == 0 ? 0xFFD77B31 : 0xFFAE4828);
                        }
                    }
                }
                if (face.startsWith("front") && y == 3 && x >= 6 && x <= 9) {
                    color = face.equals("front_lit") ? 0xFFFFDC80 : 0xFF637574;
                }
                image.setRGB(x, y, color);
            }
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) {
                throw new IllegalStateException("PNG encoder unavailable");
            }
            return bytes.toByteArray();
        } catch (IOException failed) {
            throw new IllegalStateException("Cannot encode community combustion art", failed);
        }
    }

    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (String face : FACES) {
            byte[] bytes = png(face);
            Path path = output.file(ModIdentity.id("block/combustion_" + face), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try {
                    cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes));
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot write combustion texture " + path, failure);
                }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 combustion generator art"; }
}
