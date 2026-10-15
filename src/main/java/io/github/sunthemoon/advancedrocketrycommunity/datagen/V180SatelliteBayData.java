package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * ADR-065 C17c (W27b) rocket satellite bay: original pixel geometry drawn here, a plain cube model, a loot table that
 * drops the bay item and the extracted package once, a community recipe, the bay's rocket tag and bilingual text. No
 * upstream, LibVulpes or game art is read or copied (the upstream bay texture is still under authorship review).
 */
public final class V180SatelliteBayData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private static final String BAY = "rocket_satellite_bay";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;

    public V180SatelliteBayData(PackOutput output, boolean client, boolean server) {
        this.output = output;
        this.client = client;
        this.server = server;
    }

    public static Map<String, JsonObject> clientFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        files.put(ASSETS + "blockstates/" + BAY + ".json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/"
                + BAY + "\"}}}"));
        files.put(ASSETS + "models/block/" + BAY + ".json", json("{\"parent\":\"minecraft:block/cube_bottom_top\","
                + "\"textures\":{\"top\":\"" + NS + ":block/" + BAY + "_top\",\"bottom\":\"" + NS + ":block/" + BAY
                + "_bottom\",\"side\":\"" + NS + ":block/" + BAY + "_side\"}}"));
        files.put(ASSETS + "models/item/" + BAY + ".json", json("{\"parent\":\"" + NS + ":block/" + BAY + "\"}"));
        return files;
    }

    public static Map<String, JsonObject> serverFiles() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        // No survives_explosion condition: a normal break or an explosion drops the bay and its package exactly once.
        files.put(DATA + "loot_tables/blocks/" + BAY + ".json", json("{\"type\":\"minecraft:block\",\"pools\":["
                + "{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":" + BAY + "\"}]},"
                + "{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:dynamic\",\"name\":\""
                + NS + ":" + BAY + "_package\"}]}]}"));
        files.put(DATA + "recipes/" + BAY + ".json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\","
                + "\"pattern\":[\"p p\",\"pcp\",\"pbp\"],\"key\":{\"p\":{\"tag\":\"forge:plates/aluminum\"},"
                + "\"c\":{\"item\":\"minecraft:chest\"},\"b\":{\"item\":\"" + NS + ":basic_circuit\"}},"
                + "\"result\":{\"item\":\"" + NS + ":" + BAY + "\",\"count\":1}}"));
        files.put(DATA + "advancements/recipes/misc/" + BAY + ".json", json("{\"parent\":\"minecraft:recipes/root\","
                + "\"criteria\":{\"has_part\":{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":["
                + "{\"items\":[\"" + NS + ":basic_circuit\"]}]}},\"has_the_recipe\":{\"trigger\":"
                + "\"minecraft:recipe_unlocked\",\"conditions\":{\"recipe\":\"" + NS + ":" + BAY + "\"}}},"
                + "\"requirements\":[[\"has_part\",\"has_the_recipe\"]],\"rewards\":{\"recipes\":[\"" + NS + ":" + BAY
                + "\"]}}"));
        files.put(DATA + "tags/blocks/rocket_satellite_bays.json", json("{\"replace\":false,\"values\":[\"" + NS + ":"
                + BAY + "\"]}"));
        return files;
    }

    public static Map<String, String> language(boolean chinese) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("block." + NS + "." + BAY, chinese ? "火箭卫星舱" : "Rocket Satellite Bay");
        String screen = "screen." + NS + "." + BAY + ".";
        entries.put(screen + "phase", chinese ? "部署阶段：%s" : "Deployment phase: %s");
        entries.put(SatelliteBayStatus.Phase.NONE.translationKey(), chinese ? "无" : "None");
        entries.put(SatelliteBayStatus.Reason.DEPLOYMENT_UNAVAILABLE.translationKey(), chinese
                ? "本版本尚不能部署卫星。" : "Satellite deployment is not available in this version.");
        String message = "message." + NS + "." + BAY + ".";
        entries.put(message + "unauthorized", chinese
                ? "只有卫星舱的所有者或管理员可以在 8 格内打开它。"
                : "Only the bay's owner or an operator can open it, from within 8 blocks.");
        String[][] guards = {
                {"QUARANTINED", "This bay's data is unreadable and kept unchanged; ask an administrator.",
                        "这个卫星舱的数据无法读取，已原样保留；请联系管理员。"},
                {"UNSUPPORTED_ROOT", "This bay was saved by a newer version and is kept unchanged.",
                        "这个卫星舱由更新的版本保存，已原样保留。"},
                {"UNSUPPORTED_DATA", "This bay's package was made by a newer version and is kept unchanged.",
                        "这个卫星舱中的卫星包裹由更新的版本制作，已原样保留。"},
                {"PACKAGE_UNAVAILABLE", "This bay's package item is not installed; the package is kept unchanged.",
                        "这个卫星舱中的包裹物品未安装，包裹已原样保留。"},
                {"INVALID_PACKAGE", "This bay holds an invalid package, kept unchanged; ask an administrator.",
                        "这个卫星舱中的包裹无效，已原样保留；请联系管理员。"},
                {"CLAIMED", "A rocket assembly or disassembly holds this bay; wait for it to finish.",
                        "火箭组装或拆解正在占用这个卫星舱；请等待其完成。"}};
        for (String[] guard : guards) {
            entries.put(SatelliteBayStatus.Guard.valueOf(guard[0]).translationKey(), guard[chinese ? 2 : 1]);
        }
        entries.put("jei." + NS + "." + BAY, chinese
                ? "放入一个属于卫星舱所有者的卫星包裹。漏斗和投掷器可以放入和取出包裹；火箭组装或拆解期间不可存取。"
                        + "卫星舱随火箭飞行时包裹被锁定。本版本尚不能在轨部署卫星。"
                        + "正常破坏或爆炸时掉落卫星舱和其中的包裹各一次。"
                : "Holds one satellite package owned by the bay's owner. Hoppers and droppers can insert and extract it, "
                        + "except while a rocket assembly or disassembly holds the bay. In a flying rocket the package is "
                        + "locked. Deployment in orbit is not available in this version. Breaking or blowing up the bay "
                        + "drops the bay and its package once each.");
        return Map.copyOf(entries);
    }

    /** The bay's three faces, drawn on the 16 x 16 grid encoder. */
    public static String[] grid(String face) {
        char[][] pixels = new char[16][16];
        for (char[] row : pixels) {
            Arrays.fill(row, '4');
        }
        rect(pixels, 0, 0, 15, 0, '6');
        rect(pixels, 0, 0, 0, 15, '6');
        rect(pixels, 0, 15, 15, 15, '2');
        rect(pixels, 15, 0, 15, 15, '2');
        switch (face) {
            case "top" -> {
                // Two hatch doors over a dark recess, with handles and four corner bolts.
                rect(pixels, 2, 2, 13, 13, '1');
                rect(pixels, 3, 3, 7, 12, '5');
                rect(pixels, 8, 3, 12, 12, '5');
                rect(pixels, 7, 3, 7, 12, '2');
                rect(pixels, 8, 3, 8, 12, '7');
                rect(pixels, 5, 7, 6, 8, '8');
                rect(pixels, 9, 7, 10, 8, '8');
                for (int[] bolt : new int[][] {{1, 1}, {14, 1}, {1, 14}, {14, 14}}) {
                    pixels[bolt[1]][bolt[0]] = '9';
                }
            }
            case "side" -> {
                // A recessed panel crossed by a striped band, rivets and a small status lamp.
                rect(pixels, 2, 3, 13, 12, '3');
                for (int x = 2; x <= 13; x++) {
                    for (int y = 6; y <= 7; y++) {
                        pixels[y][x] = (x + y) % 4 < 2 ? '7' : '2';
                    }
                }
                for (int[] rivet : new int[][] {{2, 3}, {13, 3}, {2, 12}, {13, 12}}) {
                    pixels[rivet[1]][rivet[0]] = '8';
                }
                rect(pixels, 10, 9, 11, 10, '9');
            }
            case "bottom" -> {
                rect(pixels, 2, 2, 13, 13, '3');
                for (int i = 2; i <= 13; i++) {
                    pixels[i][i] = '5';
                    pixels[i][15 - i] = '5';
                }
            }
            default -> throw new IllegalArgumentException("Unknown satellite bay face " + face);
        }
        return Arrays.stream(pixels).map(String::new).toArray(String[]::new);
    }

    private static void rect(char[][] pixels, int x1, int y1, int x2, int y2, char colour) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                pixels[y][x] = colour;
            }
        }
    }

    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(ASSETS + "textures/block/" + BAY + "_top.png", V180MaterialArt.png(grid("top"), 0xE2C79A));
        files.put(ASSETS + "textures/block/" + BAY + "_side.png", V180MaterialArt.png(grid("side"), 0xC8D0D8));
        files.put(ASSETS + "textures/block/" + BAY + "_bottom.png", V180MaterialArt.png(grid("bottom"), 0xA9B3BD));
        return files;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            textures().forEach((path, png) -> writes.add(CompletableFuture.runAsync(() -> {
                try {
                    cache.writeIfNeeded(output.getOutputFolder().resolve(path), png, Hashing.sha1().hashBytes(png));
                } catch (IOException failed) {
                    throw new UncheckedIOException(failed);
                }
            })));
        }
        if (server) {
            serverFiles().forEach((path, value) -> writes.add(
                    DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Advanced Rocketry v1.8 rocket satellite bay";
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
