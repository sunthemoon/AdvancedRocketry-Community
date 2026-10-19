package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original cargo couplings: offset sight glass, striped hose and opposite port arrows. No bitmap inputs. */
public final class V180RocketCargoArt {
    private V180RocketCargoArt() { }

    public static String[] grid(boolean unload) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) { Arrays.fill(row, '3'); }
        // Asymmetric enclosure and mounting feet, not a vanilla machine face.
        rect(pixels, 0, 0, 15, 1, '7');
        rect(pixels, 0, 2, 1, 13, '6');
        rect(pixels, 14, 2, 15, 15, '1');
        rect(pixels, 2, 14, 6, 15, '0');
        rect(pixels, 10, 14, 13, 15, '0');
        rect(pixels, 3, 3, 5, 11, '1');
        rect(pixels, 4, 4, 4, 6, '8');
        rect(pixels, 4, 7, 4, 10, '5');
        for (int y = 4; y <= 10; y += 3) { pixels[y][6] = '9'; }
        // A squared coupling collar and dark throat, with a clear direction arrow.
        rect(pixels, 8, 4, 12, 11, '6');
        rect(pixels, 9, 5, 11, 10, '0');
        rect(pixels, 10, 6, 10, 9, '9');
        int tip = unload ? 6 : 9;
        pixels[unload ? tip + 1 : tip - 1][9] = '9';
        pixels[unload ? tip + 1 : tip - 1][11] = '9';
        // The hose exits below the coupling; two pale diagonal stripes identify the cargo family.
        rect(pixels, 7, 12, 12, 13, '2');
        pixels[12][8] = '8'; pixels[13][9] = '8';
        pixels[12][11] = '8'; pixels[13][12] = '8';
        pixels[2][12] = unload ? '5' : '9';
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }

    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        String prefix = "assets/advancedrocketrycommunity/textures/block/rocket_fluid_";
        files.put(prefix + "loader.png", V180MaterialArt.png(grid(false), 0x9BD5C7));
        files.put(prefix + "unloader.png", V180MaterialArt.png(grid(true), 0xE5AD78));
        return files;
    }

    private static void rect(char[][] pixels, int x1, int y1, int x2, int y2, char colour) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) { pixels[y][x] = colour; }
        }
    }
}
