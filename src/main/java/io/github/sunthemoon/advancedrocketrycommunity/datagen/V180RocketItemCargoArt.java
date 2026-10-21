package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Original item cargo faces: a bevelled frame, a four-cell parcel hatch for the buffer's four slots and a roller band
 * whose chevron points into the hatch for a loader and out of it for an unloader. No bitmap inputs.
 */
public final class V180RocketItemCargoArt {
    private V180RocketItemCargoArt() { }

    public static String[] grid(boolean unload) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) { Arrays.fill(row, '4'); }
        // A bevel: light top and left edges, dark bottom and right edges, and four corner rivets.
        rect(pixels, 0, 0, 15, 0, '7');
        rect(pixels, 0, 1, 0, 15, '6');
        rect(pixels, 15, 1, 15, 15, '1');
        rect(pixels, 1, 15, 14, 15, '0');
        for (int[] rivet : new int[][] {{2, 2}, {13, 2}, {2, 12}, {13, 12}}) { pixels[rivet[1]][rivet[0]] = '9'; }
        // The parcel hatch: a dark rim around four raised cells, one per buffer slot.
        rect(pixels, 4, 2, 11, 9, '0');
        rect(pixels, 5, 3, 10, 8, '2');
        for (int[] cell : new int[][] {{5, 3}, {8, 3}, {5, 6}, {8, 6}}) {
            rect(pixels, cell[0], cell[1], cell[0] + 1, cell[1] + 1, '8');
            pixels[cell[1] + 1][cell[0] + 1] = '5';
        }
        // The roller band below the hatch, with alternating rollers.
        rect(pixels, 2, 11, 13, 13, '1');
        for (int x = 3; x <= 12; x += 2) { pixels[12][x] = '5'; }
        // The port chevron on the band: up (into the hatch) loads, down (out of it) unloads.
        int point = unload ? 13 : 11;
        int tail = unload ? 11 : 13;
        pixels[point][7] = '9'; pixels[point][8] = '9';
        pixels[12][6] = '9'; pixels[12][9] = '9';
        pixels[tail][5] = '9'; pixels[tail][10] = '9';
        // A side lamp: lit high for a loader, low for an unloader.
        pixels[unload ? 8 : 4][13] = '9';
        pixels[unload ? 4 : 8][13] = '3';
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }

    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String prefix = "assets/advancedrocketrycommunity/textures/block/rocket_item_";
        files.put(prefix + "loader.png", V180MaterialArt.png(grid(false), 0xB9CC8E));
        files.put(prefix + "unloader.png", V180MaterialArt.png(grid(true), 0xD7A0B6));
        return files;
    }

    private static void rect(char[][] pixels, int x1, int y1, int x2, int y2, char colour) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) { pixels[y][x] = colour; }
        }
    }
}
