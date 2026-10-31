package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/** Original pad geometry/markings and community recipe; no imported art. */
public final class V180GasChargePadData implements DataProvider {
    private static final String NS = "advancedrocketrycommunity";
    private static final String ASSETS = "assets/" + NS + "/";
    private static final String DATA = "data/" + NS + "/";
    private final PackOutput output;
    private final boolean client;
    private final boolean server;
    public V180GasChargePadData(PackOutput output, boolean client, boolean server) { this.output = output; this.client = client; this.server = server; }
    public static Map<String, JsonObject> clientFiles() {
        return Map.of(ASSETS + "blockstates/gas_charge_pad.json", json("{\"variants\":{\"\":{\"model\":\"" + NS + ":block/gas_charge_pad\"}}}"),
                ASSETS + "models/item/gas_charge_pad.json", json("{\"parent\":\"" + NS + ":block/gas_charge_pad\"}"),
                ASSETS + "models/block/gas_charge_pad.json", json("{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{"
                        + "\"top\":\"" + NS + ":block/gas_charge_pad_top\",\"bottom\":\"" + NS + ":block/gas_charge_pad_side\","
                        + "\"side\":\"" + NS + ":block/gas_charge_pad_side\"}}"));
    }
    public static Map<String, JsonObject> serverFiles() {
        return Map.of(DATA + "recipes/gas_charge_pad.json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\","
                        + "\"pattern\":[\"ppp\",\"cic\",\"ppp\"],\"key\":{\"p\":{\"tag\":\"forge:plates/steel\"},"
                        + "\"c\":{\"item\":\"" + NS + ":empty_canister\"},\"i\":{\"item\":\"" + NS + ":user_interface\"}},"
                        + "\"result\":{\"item\":\"" + NS + ":gas_charge_pad\"}}"),
                DATA + "loot_tables/blocks/gas_charge_pad.json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                        + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + NS + ":gas_charge_pad\"}],"
                        + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
    }
    public static Map<String, String> language(boolean zh) {
        String key = "message." + NS + ".gas_charge_pad.";
        return Map.of("block." + NS + ".gas_charge_pad", zh ? "气体充装台" : "Gas Charge Pad",
                key + "refilled", zh ? "已补充 1,000 单位气体，并返还一个空罐。" : "Refilled 1,000 gas units; one empty canister returned.",
                key + "refused", zh ? "无法补充：检查压力罐、气体类型、容量和空罐存放位置。" : "Refill refused: check tank, gas, capacity and empty-canister space.",
                key + "instructions", zh ? "放入气体罐供气、空罐充装；空手领取成品，潜行空手取回输入罐。不会自动为宇航服充气。"
                        : "Insert gas to supply or an empty canister to fill. Empty hand: output; sneak: input. No automatic suit charging.");
    }
    public static Map<String, byte[]> textures() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String side : List.of("top", "side")) {
            char[][] grid = new char[16][16]; for (char[] row : grid) { Arrays.fill(row, '3'); }
            for (int y = 1; y < 15; y++) { for (int x = 1; x < 15; x++) {
                if (x == 1 || x == 14 || y == 1 || y == 14) { grid[y][x] = '8'; }
                if (side.equals("top") && Math.abs(x - 7) + Math.abs(y - 7) <= 5) { grid[y][x] = '5'; }
                if (side.equals("side") && y >= 5 && y <= 10 && (x >= 3 && x <= 5 || x >= 10 && x <= 12)) { grid[y][x] = '9'; }
            } }
            files.put(ASSETS + "textures/block/gas_charge_pad_" + side + ".png",
                    V180MaterialArt.png(Arrays.stream(grid).map(String::new).toArray(String[]::new), 0x85C8B5));
        }
        return files;
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            textures().forEach((path, bytes) -> writes.add(CompletableFuture.runAsync(() -> {
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(path), bytes, Hashing.sha1().hashBytes(bytes)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            })));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 finite gas charge pad"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
