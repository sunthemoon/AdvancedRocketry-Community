package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** NEW MIT community art: integer geometry only; no source images. W275 registers this before authoring. */
public final class V180StandInArt {
    private static final int EDGE = 0xFF2C4348;
    private static final int DARK = 0xFF162F38;
    private static final int STEEL = 0xFF95A6A0;
    private static final int SHADE = 0xFF657E83;
    private static final int LIGHT = 0xFFC6D4CE;
    private static final int ITEM = 0xFFD3A65E;
    private static final int FLUID = 0xFF4DB5C6;
    private static final int ENERGY = 0xFFE7D265;
    public static final List<String> CONTAINERS = List.of("empty_canister", "oxygen_canister", "hydrogen_canister",
            "low_pressure_tank", "pressure_tank", "high_pressure_tank", "super_high_pressure_tank");

    private V180StandInArt() { }

    public static List<String> textures() {
        List<String> names = new ArrayList<>(List.of("block/machine_panel_side", "block/rolling_machine_front",
                "block/machine_item_input_port", "block/machine_item_output_port",
                "block/machine_fluid_input_port", "block/machine_energy_input_port"));
        CONTAINERS.forEach(id -> names.add("item/" + id + "_cylinder"));
        return List.copyOf(names);
    }

    public static int[] pixels(String name) {
        if (!textures().contains(name)) { throw new IllegalArgumentException("Unknown W275 texture: " + name); }
        int[] pixels = new int[256];
        if (name.startsWith("item/")) {
            cylinder(pixels, name.substring(5, name.length() - "_cylinder".length()));
        } else {
            panel(pixels);
            if (name.endsWith("rolling_machine_front")) { rollers(pixels); }
            else if (name.endsWith("_port")) { port(pixels, name); }
            else {
                // Three offset service plates break up the centre without adding a drawer handle.
                rect(pixels, 4, 4, 11, 11, DARK);
                rect(pixels, 4, 4, 6, 11, SHADE);
                rect(pixels, 7, 4, 11, 7, 0xFF7B9190);
                pixels[10 * 16 + 10] = LIGHT;
            }
        }
        return pixels;
    }

    private static void panel(int[] p) {
        // Same steel/teal palette and inset bolts as the v1.8 hatches, without drawer hardware.
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                p[y * 16 + x] = x < 2 || x > 13 || y < 2 || y > 13 ? EDGE : STEEL;
            }
        }
        // Staggered panel joints, rather than concentric bright borders around a light centre.
        rect(p, 4, 3, 11, 3, LIGHT);
        rect(p, 3, 4, 3, 11, SHADE);
        rect(p, 4, 8, 9, 8, SHADE);
        rect(p, 9, 9, 9, 13, SHADE);
        rect(p, 10, 9, 13, 13, 0xFF7B9190);
        p[11 * 16 + 11] = LIGHT;
        for (int x : new int[] {2, 13}) {
            for (int y : new int[] {2, 13}) { p[y * 16 + x] = LIGHT; }
        }
    }

    private static void rollers(int[] p) {
        rect(p, 3, 4, 12, 11, DARK);
        // Two horizontal rollers, with dark bearing ends and a sheet through their nip.
        for (int y : new int[] {4, 9}) {
            rect(p, 4, y, 11, y + 2, SHADE);
            rect(p, 4, y, 11, y, LIGHT);
            rect(p, 5, y + 1, 10, y + 1, STEEL);
            p[(y + 1) * 16 + 3] = ITEM;
            p[(y + 1) * 16 + 12] = ITEM;
        }
        rect(p, 2, 7, 13, 8, LIGHT);
        rect(p, 3, 8, 12, 8, 0xFFABB9C5);
        rect(p, 5, 7, 10, 7, 0xFFE4EBE3);
        rect(p, 6, 12, 9, 12, DARK);
    }

    private static void port(int[] p, String name) {
        rect(p, 4, 4, 11, 11, DARK);
        if (name.contains("item_")) {
            boolean output = name.contains("output");
            // Match the hatches: input points right, output points left, with a shared amber accent.
            for (int y = 5; y <= 9; y++) {
                for (int x = 5; x <= 10; x++) {
                    int a = output ? 15 - x : x;
                    if (y == 7 || a == 9 && (y == 6 || y == 8) || a == 8 && (y == 5 || y == 9)) {
                        p[y * 16 + x] = ITEM;
                    }
                }
            }
        } else if (name.contains("fluid")) {
            p[4 * 16 + 8] = FLUID;
            rect(p, 7, 5, 8, 6, FLUID);
            rect(p, 6, 7, 9, 9, FLUID);
            rect(p, 7, 10, 8, 10, FLUID);
            p[7 * 16 + 7] = LIGHT;
        } else {
            rect(p, 8, 4, 9, 5, ENERGY);
            rect(p, 7, 6, 8, 6, ENERGY);
            rect(p, 6, 7, 10, 7, ENERGY);
            rect(p, 8, 8, 9, 8, ENERGY);
            rect(p, 7, 9, 8, 10, ENERGY);
            p[11 * 16 + 7] = ENERGY;
        }
    }

    private static void cylinder(int[] p, String id) {
        // Broad rounded shoulders and a narrow stem with a transverse brass valve, not a bottle neck.
        rect(p, 6, 1, 10, 1, ITEM);
        rect(p, 7, 2, 8, 3, SHADE);
        p[2 * 16 + 10] = LIGHT;
        rect(p, 6, 4, 9, 4, LIGHT);
        rect(p, 5, 5, 10, 5, STEEL);
        for (int y = 6; y <= 12; y++) {
            rect(p, 4, y, 11, y, SHADE);
            rect(p, 5, y, 5, y, LIGHT);
            rect(p, 6, y, 8, y, STEEL);
            p[y * 16 + 11] = EDGE;
        }
        rect(p, 5, 13, 10, 13, SHADE);
        rect(p, 6, 14, 9, 14, EDGE);
        int bands = switch (id) {
            case "empty_canister" -> 0;
            case "high_pressure_tank" -> 3;
            case "super_high_pressure_tank" -> 4;
            case "pressure_tank" -> 2;
            default -> 1;
        };
        int accent = switch (id) {
            case "oxygen_canister" -> FLUID;
            case "hydrogen_canister" -> 0xFFE08662;
            case "low_pressure_tank" -> 0xFFADC6BF;
            case "pressure_tank" -> 0xFF72B5A4;
            case "high_pressure_tank" -> ITEM;
            case "super_high_pressure_tank" -> 0xFFE08662;
            default -> STEEL;
        };
        for (int band = 0; band < bands; band++) { rect(p, 5, 6 + band * 2, 10, 6 + band * 2, accent); }
        // A small off-centre stamped plate leaves the cylinder shading visible.
        rect(p, 8, 9, 9, 11, EDGE);
        p[10 * 16 + 8] = LIGHT;
    }

    private static void rect(int[] p, int x1, int y1, int x2, int y2, int color) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) { p[y * 16 + x] = color; }
        }
    }

    public static byte[] png(String name) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 16, 16, pixels(name), 0, 16);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("No PNG writer"); }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("W275 texture encoding failed", failure); }
    }
}
