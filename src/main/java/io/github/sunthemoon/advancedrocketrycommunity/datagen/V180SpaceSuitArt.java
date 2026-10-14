package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** NEW MIT coordinated pressure suit, including the merged fixed overlays. No bitmap input. */
public final class V180SpaceSuitArt implements DataProvider {
    public static final List<String> TEXTURES = List.of("item/space_suit_helmet", "item/space_suit_chestplate",
            "item/space_suit_leggings", "item/space_suit_boots",
            "models/armor/space_suit_layer_1", "models/armor/space_suit_layer_2");
    private static final int SHELL = 0xFFD9D5C5;
    private static final int SHADE = 0xFFB4BEAF;
    private static final int SEAL = 0xFF344957;
    private static final int TEAL = 0xFF4DC3BB;
    private static final int GLASS = 0xFF234F67;
    private static final int GOLD = 0xFFF0B55E;
    private final PackOutput.PathProvider output;

    public V180SpaceSuitArt(PackOutput output) {
        this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    public static int width(String texture) { check(texture); return texture.startsWith("item/") ? 16 : 64; }
    public static int height(String texture) { check(texture); return texture.startsWith("item/") ? 16 : 32; }

    private static void check(String texture) {
        if (!TEXTURES.contains(texture)) { throw new IllegalArgumentException("Unknown suit texture: " + texture); }
    }

    public static int[] pixels(String texture) {
        check(texture);
        if (texture.startsWith("models/")) { return armor(texture.endsWith("_2")); }
        int[] p = new int[256];
        switch (texture) {
            case "item/space_suit_helmet" -> {
                box(p, 16, 4, 2, 8, 2, SHELL);
                box(p, 16, 3, 4, 10, 7, SHELL);
                box(p, 16, 4, 11, 8, 2, SEAL);
                box(p, 16, 4, 5, 7, 4, GLASS);
                box(p, 16, 5, 5, 4, 1, TEAL);
                box(p, 16, 11, 8, 3, 3, SEAL);
                box(p, 16, 12, 9, 2, 1, GOLD);
                box(p, 16, 5, 10, 4, 1, SHADE);
            }
            case "item/space_suit_chestplate" -> {
                box(p, 16, 5, 3, 7, 11, SHELL);
                box(p, 16, 2, 4, 3, 7, SHADE);
                box(p, 16, 12, 5, 2, 8, SHADE);
                box(p, 16, 6, 3, 4, 2, SEAL);
                box(p, 16, 2, 9, 3, 2, SEAL);
                box(p, 16, 12, 11, 2, 2, SEAL);
                for (int y = 5; y < 12; y++) { box(p, 16, 5 + (y - 5) / 2, y, 1, 1, TEAL); }
                box(p, 16, 9, 7, 2, 3, SEAL);
                box(p, 16, 9, 8, 1, 1, GOLD);
                box(p, 16, 5, 13, 7, 1, SEAL);
            }
            case "item/space_suit_leggings" -> {
                box(p, 16, 3, 2, 10, 3, SEAL);
                box(p, 16, 4, 5, 4, 8, SHELL);
                box(p, 16, 9, 5, 3, 9, SHADE);
                box(p, 16, 4, 8, 3, 2, TEAL);
                box(p, 16, 9, 9, 3, 2, TEAL);
                box(p, 16, 4, 12, 4, 2, SEAL);
                box(p, 16, 9, 13, 3, 1, SEAL);
                box(p, 16, 10, 3, 2, 1, GOLD);
            }
            case "item/space_suit_boots" -> {
                box(p, 16, 3, 3, 4, 7, SHELL);
                box(p, 16, 10, 4, 3, 7, SHADE);
                box(p, 16, 2, 10, 5, 2, SHELL);
                box(p, 16, 9, 11, 5, 2, SHELL);
                box(p, 16, 1, 12, 6, 2, SEAL);
                box(p, 16, 9, 13, 6, 2, SEAL);
                box(p, 16, 3, 5, 4, 1, TEAL);
                box(p, 16, 10, 6, 3, 1, TEAL);
                box(p, 16, 3, 9, 2, 1, GOLD);
            }
            default -> throw new IllegalArgumentException(texture);
        }
        return p;
    }

    private static int[] armor(boolean inner) {
        int[] p = new int[64 * 32];
        if (!inner) {
            cube(p, 0, 0, 8, 8, 8, SHELL); // head UV (including top and bottom)
            cube(p, 16, 16, 8, 12, 4, SHELL); // torso UV
            cube(p, 40, 16, 4, 12, 4, SHADE); // both arms use the mirrored right-arm island
            box(p, 64, 8, 10, 8, 4, GLASS); // front viewport
            box(p, 64, 9, 10, 5, 1, TEAL);
            box(p, 64, 8, 15, 8, 1, SEAL); // helmet seal
            for (int y = 21; y < 29; y++) { box(p, 64, 20 + (y - 21) / 2, y, 1, 1, TEAL); }
            box(p, 64, 25, 23, 3, 4, SEAL); // chest gauge
            box(p, 64, 26, 24, 1, 2, GOLD);
            box(p, 64, 40, 29, 16, 2, SEAL); // wrist cuffs
            box(p, 64, 45, 22, 2, 3, TEAL); // shoulder patches
            box(p, 64, 0, 28, 16, 4, SEAL); // boots: only bottom four rows of leg side UVs
            box(p, 64, 4, 28, 4, 1, TEAL);
            box(p, 64, 8, 16, 4, 4, SEAL); // bottom cap of the leg cube, not the top cap at the hip
        } else {
            cube(p, 0, 16, 4, 12, 4, SHADE); // trouser legs
            box(p, 64, 16, 29, 24, 3, SEAL); // waist, leaving the rest of the torso transparent
            box(p, 64, 28, 16, 8, 4, SEAL); // torso bottom cap
            box(p, 64, 4, 24, 4, 3, TEAL); // knee reinforcement
            box(p, 64, 12, 24, 4, 3, SEAL);
            box(p, 64, 0, 30, 16, 2, SEAL); // ankle seals
            box(p, 64, 25, 29, 2, 2, GOLD);
        }
        return p;
    }

    /** Standard box UV geometry only; no native texture pixels or art patterns. */
    private static void cube(int[] p, int u, int v, int width, int height, int depth, int color) {
        box(p, 64, u + depth, v, width * 2, depth, color);
        box(p, 64, u, v + depth, 2 * (width + depth), height, color);
        // Independently drawn vertical seams distinguish face turns.
        for (int x : new int[] {u, u + depth, u + depth + width, u + 2 * depth + width}) {
            box(p, 64, x, v + depth, 1, height, SEAL);
        }
    }

    private static void box(int[] p, int stride, int x, int y, int width, int height, int color) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) { p[row * stride + column] = color; }
        }
    }

    public static byte[] png(String texture) {
        return V180ClosureArt.encode(width(texture), height(texture), pixels(texture));
    }

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

    @Override public String getName() { return "v1.8 NEW space suit icons and armor layers"; }
}
